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
  - name: "PDF 里的中文是真字不是豆腐块（能抽出文字、字体已嵌入且不是回退字体）；下载的 Word 保留甲方原件字体（宋体 / Times New Roman，CR-20260924-10）；页面图片页数与 PDF 页数一致且图不是空白；合并文件按固定顺序含三份"
    form: DATA
    run: |-
      bash doc/verify/reseed.sh --yes >/dev/null && rm -f "${TMPDIR:-/tmp}"/lqg-a.pdf "${TMPDIR:-/tmp}"/lqg-a.docx "${TMPDIR:-/tmp}"/lqg-m.pdf "${TMPDIR:-/tmp}"/lqg-p1.png &&
      bash doc/verify/api.sh --as staff --fresh-module ruoyi-lqg POST '/lqg/doc/9000001001/organoid_score/render?audience=internal' | jq -e '.data.status=="done"' &&
      curl -sSf -o "${TMPDIR:-/tmp}"/lqg-a.pdf "$(bash doc/verify/api.sh --as staff GET '/lqg/doc/9000001001/organoid_score/download?format=pdf&audience=internal' | jq -r '.data.url')" &&
      pdftotext -layout "${TMPDIR:-/tmp}"/lqg-a.pdf - | tr -d ' \n' | grep -q '类器官质量评分表' && pdftotext "${TMPDIR:-/tmp}"/lqg-a.pdf - | tr -d ' \n' | grep -q '药敏实验失败风险较大' &&
      pdffonts "${TMPDIR:-/tmp}"/lqg-a.pdf | tail -n +3 | awk '{print $(NF-4)}' | grep -qv no && pdffonts "${TMPDIR:-/tmp}"/lqg-a.pdf | grep -qiE 'Noto|SourceHan|Source Han|思源' &&
      ! pdffonts "${TMPDIR:-/tmp}"/lqg-a.pdf | grep -qiE 'DejaVu|Liberation' &&
      curl -sSf -o "${TMPDIR:-/tmp}"/lqg-a.docx "$(bash doc/verify/api.sh --as staff GET '/lqg/doc/9000001001/organoid_score/download?format=docx&audience=internal' | jq -r '.data.url')" &&
      python3 -c "import zipfile,sys; z=zipfile.ZipFile('${TMPDIR:-/tmp}/lqg-a.docx'); x=''.join(z.read(n).decode('utf-8','ignore') for n in z.namelist() if n.startswith('word/') and n.endswith('.xml')); sys.exit(0 if ('宋体' in x and 'Times New Roman' in x and 'Noto' not in x and 'Tinos' not in x) else 1)" &&
      PAGES="$(pdfinfo "${TMPDIR:-/tmp}"/lqg-a.pdf | awk '/^Pages:/{print $2}')" &&
      bash doc/verify/api.sh --as staff GET '/lqg/doc/9000001001/organoid_score/pages?audience=internal' | jq -e --argjson n "${PAGES}" '.data.status=="done" and (.data.pages|length)==$n and ([.data.pages[].pageNo]==[range(1;$n+1)])' &&
      curl -sSf -o "${TMPDIR:-/tmp}"/lqg-p1.png "$(bash doc/verify/api.sh --as staff GET '/lqg/doc/9000001001/organoid_score/pages?audience=internal' | jq -r '.data.pages[0].url')" &&
      python3 -c "from PIL import Image; im=Image.open('${TMPDIR:-/tmp}/lqg-p1.png').convert('L'); w,h=im.size; assert w>=1000 and h>=1400,(w,h); lo,hi=im.getextrema(); assert lo<80 and hi>240,(lo,hi)" &&
      bash doc/verify/api.sh --as staff POST '/lqg/doc/9000001001/merged/render?audience=internal' | jq -e '.data.status=="done"' &&
      curl -sSf -o "${TMPDIR:-/tmp}"/lqg-m.pdf "$(bash doc/verify/api.sh --as staff GET '/lqg/doc/9000001001/merged/download?format=pdf&audience=internal' | jq -r '.data.url')" &&
      pdftotext "${TMPDIR:-/tmp}"/lqg-m.pdf - | tr -d ' \n' | python3 -c "import sys; t=sys.stdin.read(); a,b,c=t.find('样本质控表'),t.find('类器官质控表'),t.find('类器官质量评分表'); sys.exit(0 if 0<=a<b<c else 1)" &&
      test "$(pdfinfo "${TMPDIR:-/tmp}"/lqg-m.pdf | awk '/^Pages:/{print $2}')" -ge 3
    counterfeit: |-
      容器里没装中文字体 → PDF 里全是方框，pdftotext 抽不出「类器官质量评分表」红；或者回退到了 DejaVu → 第 5 段红。
      字体没嵌入（emb 列为 no）→ 甲方电脑上打开又是另一个样子，第 4 段红。
      为了 PDF 好看直接把模板里的字体改成 Noto Serif SC / Tinos（CR-20260924-10 之前的做法）→ 下载的 Word 在甲方电脑上没有这两个字体、被 Word / WPS 随便顶替，docx 字体那段红：甲方第 24 行要的是「下载下来是我发给你的模板样子」。字体只许在转 PDF 的副本上换。
      页面图片是拿 docx 另外渲染的、页数和 PDF 对不上 → pages 长度断言红。图是全白的（渲染时机不对）→ 像素极值断言红。
      合并是把三份 PDF 硬拼、顺序按完成时间 → 顺序断言红；合并文件里少了某一份 → 页数或 find 为 -1 红。
      seed 里 1001 的图片是假地址：渲染图片位时取不到图 → 本条只用没有图片位的评分表和合并文件里的文字来断；带图的渲染由 DOC-RENDER-001 用真上传的图断过了。取图失败按 CR-20260923-09：外部版取图失败整份 failed、不对外；内部版照出并记缺图，计入首页异常；完工报告贴 1001 样本质控表内外两版的 render_status / error_msg / missing_image_count。
  - name: "失败看得见、能重试：转换服务不可用时状态为 failed 且带原因、旧产物不再被当成最新返回；恢复后重新生成成功"
    form: STATE
    run: |-
      RC=0 ; { bash doc/verify/reseed.sh --yes >/dev/null &&
      bash doc/verify/api.sh --as staff --fresh-module ruoyi-lqg POST '/lqg/doc/9000001006/organoid_score/render?audience=internal' | jq -e '.data.status=="done"' &&
      docker stop "${LQG_GOTENBERG_CONTAINER:-lqg-dev-gotenberg}" >/dev/null 2>&1 &&
      bash doc/verify/api.sh --as staff PUT /lqg/qc/9000001006/score '{"preCultureLevel":"gt80","cultureDaysLevel":"le14","organoidCountLevel":"gt4000","diameterLevel":"gt100"}' | jq -e '.code==200' &&
      bash doc/verify/api.sh --as staff POST '/lqg/doc/9000001006/organoid_score/render?audience=internal' | jq -e '.data.status=="failed" and (.data.errorMsg|length>0)' ; } || RC=$? ;
      docker start "${LQG_GOTENBERG_CONTAINER:-lqg-dev-gotenberg}" >/dev/null 2>&1 ;
      for i in $(seq 1 30); do curl -sf "${LQG_GOTENBERG_URL:-http://127.0.0.1:3010}/health" >/dev/null 2>&1 && break; sleep 1; done ;
      test "${RC}" = 0 &&
      bash doc/verify/api.sh --as staff GET '/lqg/doc/9000001006/organoid_score/pages?audience=internal' | jq -e '.data.status=="failed" and (.data.pages|length)==0' &&
      sleep 8 && bash doc/verify/api.sh --as staff POST '/lqg/doc/9000001006/organoid_score/render?audience=internal' | jq -e '.data.status=="done"' &&
      bash doc/verify/reseed.sh --yes >/dev/null
    counterfeit: |-
      转换失败被吞掉、状态仍是 done、pages 返回的是改分之前的旧图 → 「渲染应失败」那段与其后 pages 那段红。这是「Word 是新的、预览图是旧的」的事故形态：送检方看到的分数和下载到的不一样。
      失败后不允许重试（要重启后端才行）→ 最后一段红。
      不管断言成败都会先把 gotenberg 启回来、等它的 /health 通过再判结果：从第一段 reseed 到「渲染应失败」整段包在 `RC=0 ; { …; } || RC=$?` 里，其中任何一段失败（包括该失败的没失败）都把失败码存进 RC——既不会被 set -e 在 start 之前提前退出，也不会被 `;` 静默吞掉；最后由 `test "${RC}" = 0 && …` 这条链决定退出码。不把一个停掉的容器留给后面的任务。
      停 / 启的容器与健康检查地址读 `LQG_GOTENBERG_CONTAINER` / `LQG_GOTENBERG_URL`（缺省 = Kevin 本机的 lqg-dev-gotenberg 与 http://127.0.0.1:3010，CR-20260924-11）；在隔离环境重放必须设成自己那一套，否则停掉的是 Kevin 的转换服务（见 doc/verify/README.md「隔离环境重放」）。
  - name: "从没生成过的文档是正常的初始态：页面图接口回 status=none、一页不给、不顺手排队渲染，不再回 400（工作台首次进入、切页签不再连弹「这份文档还没生成」）；只有点下载而确实没有产物时才回 400 说明还没生成（CR-20260924-11）"
    form: API
    run: |-
      bash doc/verify/reseed.sh --yes >/dev/null &&
      bash doc/verify/api.sh --as staff --fresh-module ruoyi-lqg GET '/lqg/doc/9000001005/organoid_score/pages?audience=internal' | jq -e '.code==200 and .data.status=="none" and (.data.pages|length)==0 and (.data.images|length)==0' &&
      for K in sample_qc organoid_qc merged; do bash doc/verify/api.sh --as staff GET "/lqg/doc/9000001005/${K}/pages?audience=internal" | jq -e '.code==200 and .data.status=="none" and (.data.pages|length)==0' >/dev/null || exit 1; done &&
      bash doc/verify/api.sh --as staff GET '/lqg/doc/9000001005/sample_qc/pages?audience=external' | jq -e '.code==200 and .data.status=="none" and (.data.pages|length)==0' &&
      python3 doc/verify/db.py --sql "SELECT count(*) FROM t_lqg_doc_file WHERE sample_id=9000001005" --eq 0 &&
      bash doc/verify/api.sh --as staff --bizcode GET '/lqg/doc/9000001005/organoid_score/download?format=docx&audience=internal' | grep -qE '^400[[:space:]]+这份文档还没生成' &&
      (cd code/RuoYi-Vue-Plus && mvn -q -pl ruoyi-modules/ruoyi-lqg -am test -Dtest='DocPagesServiceTest' -Dsurefire.failIfNoSpecifiedTests=true)
    counterfeit: |-
      「从没生成过」照旧当错误回 400（CR-20260924-11 之前的形态）→ 第 2 段起的 `.code==200 and .data.status=="none"` 红：工作台请求层对任何非 200 业务码都弹红色通知，编辑页首次进入、每切一次页签就弹「这份文档还没生成」（Kevin 本机验收「网页工作台」第 6 行，截图里同时弹了两条）。
      只改了单份、合并件或外部版还是 400 → 循环里 merged 那一格或 external 那段红。
      把 none 做成「看一眼就顺手排队渲染」→ doc_file 计数那段红：没人点预览，后台就在渲染草稿、白占转换服务。
      为了不弹提示把下载也改成静默成功或给空链接 → 最后一段接口红：用户主动点下载而确实没有产物时要说清楚是「还没生成」。单测 `DocPagesServiceTest#neverRenderedIsNoneNotError` 钉住服务层同一条口径。
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
  1. **字体**：Gotenberg 容器挂开源中文字体（思源宋体 / Noto Serif CJK SC 与 Tinos）。**模板保留甲方原件字体（宋体 / Times New Roman）**，下载的 Word 在甲方电脑上就是原件字体；转 PDF 前由 `PdfFonts` 在「转换副本」上把字体换成容器字体（宋体、SimSun、等线一类 → 思源宋体，其余 → Tinos），PDF 与页面图里只有这两种字体（CR-20260924-10，甲方第 24 行；此前「把三份模板里的宋体统一换成思源宋体、模板版本号加一」的做法作废）。**样张要在这个容器里出**，别在装了宋体的开发机上出。
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
- `GET /lqg/doc/{sampleId}/{docKind}/pages?audience=`：`{status, errorMsg, pages:[{pageNo,url}], images:[{url, previewUrl}], attachments:[{fileName, fileSize, url}], missingImageCount, missingImages}`，url 都是短时签名链接；只有这一版产物齐全才是 `done`，在途或待重出为 `pending`（CR-20260923-09）；这一版从没生成过回 `status=none`、一页不给（正常的初始态，不是 400、不触发渲染，CR-20260924-11），download 在这种情况下仍回 400「这份文档还没生成，请先在质控文档页点「预览」」。
- `…/download?format=pdf`；`docKind=merged`（docx 与 pdf）。只有一份已完成时，合并文件 = 那一份。合并件每份自成一节（分节符 = 下一页，保留各自纸张：评分表原件是 Letter、另两份 A4），不在第 2、3 份末尾补分页符（CR-20260924-10：旧做法少断一页、末尾多一张空白页）。
- 工作台：质控文档页上出现 `failed` 时显示原因与「重新生成」按钮（调 `render?force=true`，一定重出）；内部版照出但缺图时提示缺了哪几张（CR-20260923-09）。

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

- 2026-09-23 按 CR-20260923-09 更新：accept 1 反例末句改为新的取图失败口径（外部版 failed 不对外、内部版照出记缺图、计入首页异常，完工报告贴 1001 样本质控表内外两版三个字段）；accept 2 修 set -e 缺陷——从 reseed 到「渲染应失败」包进 `RC=0 ; { …; } || RC=$?`，无论成败先启回 gotenberg 并等 /health，再由最后一条 && 链判结果；§2 补 pages 的缺图字段与「重新生成」= force。
- 2026-09-24 按 CR-20260924-10 更新：模板保留原件字体、只在转 PDF 的副本上换字体——accept 1 补「下载的 Word 里是宋体与 Times New Roman、没有 Noto / Tinos」一段（`pdffonts` 那几段不变）；§0 口径 1 字体做法改写，§2 补合并件分节与纸张。
- 2026-09-24 按 CR-20260924-11 更新：accept 2 停 / 启转换服务改为 `docker stop|start "${LQG_GOTENBERG_CONTAINER:-lqg-dev-gotenberg}"`、健康检查读 `${LQG_GOTENBERG_URL:-http://127.0.0.1:3010}`（不再用 dev compose，缺省值与原来相同，隔离环境重放必须设成自己的），accept 1 的临时文件改放 `${TMPDIR:-/tmp}`；新增 accept 3——从没生成过的文档 pages 回 `status=none`、不排队渲染，下载仍 400 说明还没生成；§2 pages 补 `none`。
