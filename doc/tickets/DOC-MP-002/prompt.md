---
ticket: DOC-MP-002
track: DOC
phase: D7
size: M
req_refs:
  - REQ-DOC-003
  - REQ-DOC-005
  - REQ-DOC-006
  - REQ-DOC-007
  - REQ-DOC-008
depends_on:
  - DOC-MP-001
touches:
  - code/miniapp/src/pages/doc/preview.vue
  - code/miniapp/src/pages/doc/download.ts
  - code/miniapp/src/pages/doc/download.fixture.spec.ts
  - code/miniapp/src/components/lqg/PageImageViewer.vue
  - code/miniapp/src/components/lqg/ThumbStrip.vue
  - code/miniapp/src/components/lqg/AttachmentList.vue
  - code/miniapp/src/components/lqg/DownloadBar.vue
  - code/miniapp/src/components/lqg/DownloadSheet.vue
  - code/miniapp/src/components/lqg/DocTabs.vue
  - code/miniapp/src/components/lqg/DocGroupCard.vue
  - code/RuoYi-Vue-Plus/ruoyi-modules/ruoyi-lqg/src/main/java/org/dromara/lqg/doc/mp/**
adr_refs:
  - ADR-0005
blueprint_refs:
  - UI:mp.doc.preview
  - FLOW:F-DOC-02.step2
  - FLOW:F-DOC-02.step3
  - FLOW:F-DOC-02.step4
accept:
  - name: "构建是本次产物；预览页用到了两层放大、打开、发送到微信四个平台能力；**点缩略图看的是原图（真打开层 src == 原图 url 且 ≠ previewUrl）**、**`showMenu: true` 在真代码里（剥注释后仍命中）**；组件直接路径导入；失败态不泄露内部错误；列表上的下载弹层与预览页顶部三份切换已接上"
    form: API
    run: |-
      cd code/miniapp && rm -rf dist/build/mp-weixin && pnpm build:mp-weixin >/dev/null && test -f dist/build/mp-weixin/pages/doc/preview.js &&
      grep -q 'previewImage' src/components/lqg/PageImageViewer.vue &&
      grep -q 'shareFileMessage' src/utils/fileHandoff.ts &&
      grep -c "@/components/lqg/.*\.vue" src/pages/doc/preview.vue | awk '{exit !($1 >= 5)}' &&
      grep -q "@/components/lqg/DownloadSheet.vue" src/components/lqg/DocGroupCard.vue && grep -q "@/components/lqg/DownloadBar.vue" src/components/lqg/DownloadSheet.vue && grep -q 'groupDocs' src/components/lqg/DocTabs.vue &&
      ! grep -nE 'errorMsg|error_msg' src/pages/doc/preview.vue &&
      pnpm vitest run src/pages/doc/download.fixture.spec.ts --reporter=json --outputFile=/tmp/lqg-dl.json >/dev/null && jq -e '.numFailedTests == 0 and .numPassedTests >= 4' /tmp/lqg-dl.json &&
      cd ../.. && bash doc/waves/regression/D7/mutation-assert.sh --verify-only --hotspot H3a,H3b
    counterfeit: |-
      「文档中的图片」点开用的还是预览图地址 → 甲方要的「看得更清楚一点」落空；要求 ThumbStrip 用 url（原图）而缩略用 previewUrl，完工报告贴代码片段。
      openDocument 没带 showMenu → 用户打开了文件却没有任何保存 / 转发入口，等于没法「下载」，第 3 段红。
      把后端的 error_msg 原样显示给外部 → 第 5 段红。
      列表上的「下载」另写了一套 downloadFile + openDocument、没复用 DownloadBar → DownloadSheet 里找不到 DownloadBar 红：两处下载迟早一处带 showMenu、一处不带。
      ★ **H3a / H3b 两条不再由源码 grep 判**（旧写法 `grep -qE 'showMenu:[[:space:]]*true' fileHandoff.ts` 与 `grep -q previewImage ThumbStrip.vue`；
      D7 r1 L2 证伪 F1/F2：把真代码那行 showMenu 删掉只留注释 → 仍绿；把「看原图」换成看 `previewUrl` → 仍绿）。现在由 `mutation-assert.sh --hotspot H3a,H3b` 判：
      · H3b（行为型）：真浏览器打开 1001 的样本质控表预览页 → 真 DOM 点「文档中的图片」缩略图 → 读 H5 打开层（对应真机 `wx.previewImage`）拿到的 src，
        必须 **== pages 接口给的原图 `url`** 且 **≠ `previewUrl`**（夹具先上传 2400×1600 真 PNG，后端另存 .jpg 预览图，两者才可区分）；
        已定义变异 = 把 `urls` 换成 `previewUrl` → 必须变红。
      · H3a（本票唯一允许读源码的例外，`showMenu` 是平台参数、H5 上无可施加的行为变异）：把注释（行/块）剥掉后 `showMenu: true` 仍须在真代码里，
        且位于 `uni.openDocument({filePath, …, showMenu: true})` 的参数位置；已定义变异 = **删掉真代码那一行、只在注释保留字面量** → 必须变红（这就是「与注释无关」的证明）。
      两条变异后必须变红、还原必须复绿；任一条「改坏了还绿」→ 脚本 exit 1。
  - name: "内部的页面图片接口可用且只给内部；页数与 PDF 一致；外部身份打内部接口被拒"
    form: DATA
    run: |-
      bash doc/verify/reseed.sh --yes >/dev/null && rm -f /tmp/lqg-mp.pdf &&
      bash doc/verify/api.sh --as staff --fresh-module ruoyi-lqg POST '/lqg/doc/9000001006/organoid_score/render?audience=internal' >/dev/null && sleep 5 &&
      curl -sSf -o /tmp/lqg-mp.pdf "$(bash doc/verify/api.sh --as staff GET '/mp/int/doc/9000001006/organoid_score/download?format=pdf' | jq -r '.data.url')" &&
      bash doc/verify/api.sh --as staff GET /mp/int/doc/9000001006/organoid_score/pages | jq -e --argjson n "$(pdfinfo /tmp/lqg-mp.pdf | awk '/^Pages:/{print $2}')" '.data.status=="done" and (.data.pages|length)==$n' &&
      bash doc/verify/api.sh --as extD --bizcode GET /mp/int/doc/9000001006/organoid_score/pages | grep -qE '^403'
    counterfeit: |-
      页面图片与 PDF 不同源 → 页数对不上红。两侧不同源：一侧是 pages 接口，一侧是真的下载下来的 PDF 用 pdfinfo 数的页。
      内部接口没加角色注解 → extD 拿到了内部版（带内部编号）的页面图，最后一段红。
---

# DOC-MP-002 · 小程序 · 文档预览与下载：顶部三份切换、逐页图片可放大、看原图、附件；列表与预览页都能下 Word / PDF（打开或发送到微信）

## §0 状态自检（实施前必过，不过就 STOP 报 Kevin）

- [ ] 当前分支符合调度器注入的期望（`track/DOC` worktree）——不写死 `feature/dayN`
- [ ] `depends_on` 全部 done：**DOC-MP-001**
- [ ] 扫 `doc/change-log.md`：涉及本 ticket 的 CR **以 CR 为准**（CR 覆盖本文）
- [ ] 逐条取权威（别全文读蓝图）：`python3 ~/claude-config/skills/xuqiu/scripts/authority_lint.py show <锚>`，锚见上方 `blueprint_refs`；接口形状见 `doc/api-contract.md`
- [ ] 必读：
  - **视觉按方向 A**（CR-20260921-08）：`doc/design-options/direction-a/落地规范.md` §5 与 §6 本页那一行；样式类用 SYS-MP-001 已落的 `src/style/components.scss`（`.lqg-*`），零颜色字面量。下面提到的图廊帧画于方向 A 之前，**只取内容块与排布，不取它的无阴影小圆角外观**
  - 视觉基准 = **方案 A**（应用内预览页），Kevin 2026-09-17 已选定（CR-20260917-03）；看图 `doc/design-options/gallery.html#mp-preview-a`。方案 B（直接弹操作菜单）已否决
  - 平台限制（要写进确认单告诉甲方）：小程序**没有**「存到手机文件夹」的接口。下载落地为「打开」（微信内置查看器，右上角菜单可保存 / 用其他应用打开）和「发送到微信聊天」
  - **ADR-0005**（全文在 `doc/_adr/`，`authority_lint.py show ADR-0005` 取结构化口径）
- [ ] 口径复述（本张最容易做反的）：
  1. 放大看图有两层：点**页面图片** → `wx.previewImage` 全屏双指缩放；点**文档中的图片**缩略图 → 看**原图**（显微照片的细节靠这个，页面图只有 150 DPI）。
  2. `wx.downloadFile` 的域名必须在小程序后台的 downloadFile 合法域名里——OSS 的签名链接域名要么配进去，要么让后端域名反代。先定好走哪条，写进报告（SYS-RELEASE-001 要用）。
  3. 下载有两处入口、同一套实现：预览页底部的 `DownloadBar`，和文档列表每份 / 每组上的「下载」「合并下载」（点开 `DownloadSheet` 弹层，里面就是 `DownloadBar`）——甲方 2026-09-17 看设计稿后要求列表上直接能下（CR-20260917-04）。
  4. 顶部 `DocTabs` 取这个样本已完成的几份（`…/doc/list?sampleId=`），复用 DOC-MP-001 的 `groupDocs` 决定顺序与要不要「合并」，别另写排序；外部走外部接口，拿到的也是三份。
  5. 文件名 = 文档名 + 编号：外部用送检单号、内部用内部编号；合并文件叫「质控文档（合并）」。

## 1 背景与口径

会上 L139：图片要可以放大或者缩小；L121：想看得更清楚一点。L136：预览之后觉得没问题再下载。L109：Word、PDF 两种都要。

## 2 实现要点

- 后端：`GET /mp/int/doc/{sampleId}/{docKind}/pages`、`…/download`（内部，audience 固定 internal）。外部用 AUTH-EXT-003 的。
- `pages/doc/preview`：路由参数 `sampleId, docKind`（含 `merged`）；按身份选内部 / 外部接口。
  - `DocTabs`：顶部切换条（该样本已完成的几份 + ≥2 份时的「合并」），切换时重新取 pages。
  - `PageImageViewer`：逐页 `image`（`mode="widthFix"`，懒加载）；点任一页 → `wx.previewImage({urls: 全部页, current})`。
  - `ThumbStrip`：`images` 的 `previewUrl` 作缩略图，点开 `wx.previewImage` 用 `url`（原图）。原图是 TIFF 等小程序打不开的格式时退回预览图并提示。
  - `AttachmentList`：点开 → `wx.downloadFile` → `wx.openDocument`（pdf / doc / xls 等）或 `wx.previewImage`（图片）。
  - `DownloadBar`：格式切换（PDF / Word）+「打开」（`wx.openDocument({showMenu: true})`）+「发送到微信」（`wx.shareFileMessage`）。
- `DownloadSheet`（`wd-popup`）：包一层 `DownloadBar`，把 DOC-MP-001 在 `DocGroupCard` 上放的「下载」「合并下载」点亮。
  - 状态：生成中（轮询 pages，最多 60 秒）/ 失败（「文档暂时无法预览，请稍后再试」，不显示内部错误）。
- `download.ts`：`downloadFileName(docKind, no, format)` 纯函数 + fixture 单测（用例写在 spec 里即可，规则见上）。

## 3 边界（明确不做）

- 不做小程序内的 Word 在线渲染
- 不做保存到相册（文档不是图片）
- 不做批量下载多个样本
- 不做水印

## 4 完工报告要求

1. 真机录屏：列表上直接「下载」PDF → 打开；「合并下载」Word → 发送到微信；列表 → 预览 → 顶部切换三份 → 放大页面 → 看原图
2. downloadFile 走的是哪个域名
3. **改了哪些文件**（含 Flyway 文件名与取号依据、新增的类 / 页面 / 接口清单）
4. **accept 逐条 ✅ / ❌ + 关键输出**（贴命令输出，不贴「已通过」三个字）
5. **遗留与 raise**：越出 `touches` 的改动、与 `doc/api-contract.md` 不一致的地方、没把握的口径
6. 验证用的后端 / 前端长进程已关，或明示留给谁
