---
ticket: DOC-PDF-001
track: DOC
phase: D6
size: M
req_refs:
  - REQ-DOC-003
  - REQ-DOC-004
  - REQ-DOC-006
  - REQ-DOC-007
depends_on:
  - DOC-RENDER-001
touches:
  - code/RuoYi-Vue-Plus/ruoyi-modules/ruoyi-lqg/src/main/java/org/dromara/lqg/doc/pdf/**
  - code/RuoYi-Vue-Plus/ruoyi-modules/ruoyi-lqg/src/test/java/org/dromara/lqg/doc/pdf/**
  - code/deploy/common/gotenberg/**
  - code/deploy/dev/docker-compose.yml
  - code/deploy/test/docker-compose.yml
  - code/RuoYi-Vue-Plus/ruoyi-modules/ruoyi-lqg/src/main/resources/lqg/doc-templates/**
  - code/RuoYi-Vue-Plus/ruoyi-admin/src/main/resources/application*.yml
adr_refs:
  - ADR-0005
blueprint_refs:
  - FLOW:F-DOC-01.step3
  - FLOW:F-DOC-01.step4
  - FLOW:F-DOC-01.step5
  - FLOW:F-DOC-01.step6
  - FIELD:t_lqg_doc_file.page_no
  - FIELD:t_lqg_doc_file.error_msg
accept:
  - name: "PDF 里的中文是真字不是豆腐块（能抽出文字、字体已嵌入且不是回退字体）；页面图片页数与 PDF 页数一致且图不是空白；合并文件按固定顺序含三份"
    form: DATA
    run: |-
      bash doc/verify/reseed.sh --yes >/dev/null && rm -f /tmp/lqg-a.pdf /tmp/lqg-m.pdf /tmp/lqg-p1.png &&
      bash doc/verify/api.sh --as staff --fresh-module ruoyi-lqg POST '/lqg/doc/9000001001/organoid_score/render?audience=internal' | jq -e '.data.status=="done"' &&
      curl -sSf -o /tmp/lqg-a.pdf "$(bash doc/verify/api.sh --as staff GET '/lqg/doc/9000001001/organoid_score/download?format=pdf&audience=internal' | jq -r '.data.url')" &&
      pdftotext -layout /tmp/lqg-a.pdf - | tr -d ' \n' | grep -q '类器官质量评分表' && pdftotext /tmp/lqg-a.pdf - | tr -d ' \n' | grep -q '药敏实验失败风险较大' &&
      pdffonts /tmp/lqg-a.pdf | tail -n +3 | awk '{print $(NF-4)}' | grep -qv no && pdffonts /tmp/lqg-a.pdf | grep -qiE 'Noto|SourceHan|Source Han|思源' &&
      ! pdffonts /tmp/lqg-a.pdf | grep -qiE 'DejaVu|Liberation' &&
      PAGES="$(pdfinfo /tmp/lqg-a.pdf | awk '/^Pages:/{print $2}')" &&
      bash doc/verify/api.sh --as staff GET '/lqg/doc/9000001001/organoid_score/pages?audience=internal' | jq -e --argjson n "${PAGES}" '.data.status=="done" and (.data.pages|length)==$n and ([.data.pages[].pageNo]==[range(1;$n+1)])' &&
      curl -sSf -o /tmp/lqg-p1.png "$(bash doc/verify/api.sh --as staff GET '/lqg/doc/9000001001/organoid_score/pages?audience=internal' | jq -r '.data.pages[0].url')" &&
      python3 -c "from PIL import Image; im=Image.open('/tmp/lqg-p1.png').convert('L'); w,h=im.size; assert w>=1000 and h>=1400,(w,h); lo,hi=im.getextrema(); assert lo<80 and hi>240,(lo,hi)" &&
      bash doc/verify/api.sh --as staff POST '/lqg/doc/9000001001/merged/render?audience=internal' | jq -e '.data.status=="done"' &&
      curl -sSf -o /tmp/lqg-m.pdf "$(bash doc/verify/api.sh --as staff GET '/lqg/doc/9000001001/merged/download?format=pdf&audience=internal' | jq -r '.data.url')" &&
      pdftotext /tmp/lqg-m.pdf - | tr -d ' \n' | python3 -c "import sys; t=sys.stdin.read(); a,b,c=t.find('样本质控表'),t.find('类器官质控表'),t.find('类器官质量评分表'); sys.exit(0 if 0<=a<b<c else 1)" &&
      test "$(pdfinfo /tmp/lqg-m.pdf | awk '/^Pages:/{print $2}')" -ge 3
    counterfeit: |-
      容器里没装中文字体 → PDF 里全是方框，pdftotext 抽不出「类器官质量评分表」红；或者回退到了 DejaVu → 第 5 段红。
      字体没嵌入（emb 列为 no）→ 甲方电脑上打开又是另一个样子，第 4 段红。
      页面图片是拿 docx 另外渲染的、页数和 PDF 对不上 → pages 长度断言红。图是全白的（渲染时机不对）→ 像素极值断言红。
      合并是把三份 PDF 硬拼、顺序按完成时间 → 顺序断言红；合并文件里少了某一份 → 页数或 find 为 -1 红。
      seed 里 1001 的图片是假地址：渲染图片位时取不到图 → 本条只用没有图片位的评分表和合并文件里的文字来断；带图的渲染由 DOC-RENDER-001 用真上传的图断过了。取图失败时该文档应整体 failed 而不是静默出一份缺图的文件——完工报告里要贴 1001 样本质控表此刻的渲染状态与 error_msg。
  - name: "失败看得见、能重试：转换服务不可用时状态为 failed 且带原因、旧产物不再被当成最新返回；恢复后重新生成成功"
    form: STATE
    run: |-
      bash doc/verify/reseed.sh --yes >/dev/null &&
      bash doc/verify/api.sh --as staff --fresh-module ruoyi-lqg POST '/lqg/doc/9000001006/organoid_score/render?audience=internal' | jq -e '.data.status=="done"' &&
      docker compose -f code/deploy/dev/docker-compose.yml stop gotenberg >/dev/null 2>&1 &&
      bash doc/verify/api.sh --as staff PUT /lqg/qc/9000001006/score '{"preCultureLevel":"gt80","cultureDaysLevel":"le14","organoidCountLevel":"gt4000","diameterLevel":"gt100"}' | jq -e '.code==200' &&
      bash doc/verify/api.sh --as staff POST '/lqg/doc/9000001006/organoid_score/render?audience=internal' | jq -e '.data.status=="failed" and (.data.errorMsg|length>0)' ;
      RC=$? ; docker compose -f code/deploy/dev/docker-compose.yml start gotenberg >/dev/null 2>&1 ; test "${RC}" = 0 &&
      bash doc/verify/api.sh --as staff GET '/lqg/doc/9000001006/organoid_score/pages?audience=internal' | jq -e '.data.status=="failed" and (.data.pages|length)==0' &&
      sleep 8 && bash doc/verify/api.sh --as staff POST '/lqg/doc/9000001006/organoid_score/render?audience=internal' | jq -e '.data.status=="done"' &&
      bash doc/verify/reseed.sh --yes >/dev/null
    counterfeit: |-
      转换失败被吞掉、状态仍是 done、pages 返回的是改分之前的旧图 → 第 5、6 段红。这是「Word 是新的、预览图是旧的」的事故形态：送检方看到的分数和下载到的不一样。
      失败后不允许重试（要重启后端才行）→ 最后一段红。
      不管断言成败都会把 gotenberg 启回来（RC 先存起来），不把一个停掉的容器留给后面的任务。
---

# DOC-PDF-001 · PDF 与页面图片：Gotenberg 容器转 PDF、中文字体、每页出 PNG、合并文件、失败可见可重试

## §0 状态自检（实施前必过，不过就 STOP 报 Kevin）

- [ ] 当前分支符合调度器注入的期望（`track/DOC` worktree）——不写死 `feature/dayN`
- [ ] `depends_on` 全部 done：**DOC-RENDER-001**
- [ ] 扫 `doc/change-log.md`：涉及本 ticket 的 CR **以 CR 为准**（CR 覆盖本文）
- [ ] 逐条取权威（别全文读蓝图）：`python3 ~/claude-config/skills/xuqiu/scripts/authority_lint.py show <锚>`，锚见上方 `blueprint_refs`；接口形状见 `doc/api-contract.md`
- [ ] 必读：
  - 小程序预览 = **方案 A**（应用内预览页，需要页面图片），Kevin 2026-09-17 已选定（CR-20260917-03）——所以「出页面 PNG」这一步要做。方案 B（直接用微信查看器）已否决
  - 甲方三份模板的中文字体是**宋体**——Linux 容器里没有。版式走样的头号原因就是字体回退
  - **ADR-0005**（全文在 `doc/_adr/`，`authority_lint.py show ADR-0005` 取结构化口径）
- [ ] 口径复述（本张最容易做反的）：
  1. **字体**：Gotenberg 容器挂开源中文字体（思源宋体 / Noto Serif CJK SC），并把三份模板里的「宋体」统一换成它、模板版本号加一。**样张要在这个容器里出**，别在装了宋体的开发机上出。
  2. 转换**串行**（并发 1，排队）：LibreOffice 转一份几百 MB 内存，2 核 4G 的机器经不起并发。
  3. PDF 与 PNG 也进指纹缓存；docx 重出了，它俩必须跟着重出——别出现「Word 是新的、预览图是旧的」。
  4. 合并 = 先拼 docx 再整体转 PDF（不是拼 PDF）：这样合并版的 Word 与 PDF 也同源。

## 1 背景与口径

会上 L109：下载方式 Word、PDF 都要；三个 Word 可以分开或合并在一起下载。L130-L151：小程序里要能预览，和 Word 一模一样。小程序渲染不了 docx，所以预览看的是页面图片。

## 2 实现要点

- `code/deploy/common/gotenberg/`：基于官方 `gotenberg/gotenberg:8` 的 Dockerfile，`COPY fonts/` 进 `/usr/local/share/fonts` 并 `fc-cache`；字体文件放仓库里（开源字体，许可文件一并放）。dev / test 两份 compose 各加一个 `gotenberg` 服务（只在内网暴露）。
- `PdfConvertService`：`POST {gotenberg}/forms/libreoffice/convert`，超时 60 秒；单线程执行器排队；失败记 `error_msg`。
- `PageImageService`：PDFBox 150 DPI 每页出 PNG，`page_no` 从 1 起；页数变少时把多余的旧页行软删。
- `DocRenderService` 的流水线扩成 docx → pdf → png，三种产物同一个指纹；任何一步失败 → 该 (docKind, audience) 整体 `failed`，已有的旧产物保留但不再被返回。
- `GET /lqg/doc/{sampleId}/{docKind}/pages?audience=`：`{status, pages:[{pageNo,url}], images:[{url, previewUrl}], attachments:[{fileName, fileSize, url}]}`，url 都是短时签名链接。
- `…/download?format=pdf`；`docKind=merged`（docx 与 pdf）。只有一份已完成时，合并文件 = 那一份。
- 工作台：质控文档页上出现 `failed` 时显示原因与「重新生成」按钮（调 render）。

## 3 边界（明确不做）

- 不做小程序预览页（DOC-MP-002）
- 不做水印、不做电子签章
- 不做 PDF 的文字可搜索之外的任何加工
- 不引入商业字体

## 4 完工报告要求

1. 三份 PDF + 合并 PDF 附在报告里；`pdffonts` 的输出；与甲方 Word 原件并排的截图（标出字体替换带来的差异，给 DOC-PROOF-001 用）
2. **改了哪些文件**（含 Flyway 文件名与取号依据、新增的类 / 页面 / 接口清单）
3. **accept 逐条 ✅ / ❌ + 关键输出**（贴命令输出，不贴「已通过」三个字）
4. **遗留与 raise**：越出 `touches` 的改动、与 `doc/api-contract.md` 不一致的地方、没把握的口径
5. 验证用的后端 / 前端长进程已关，或明示留给谁
