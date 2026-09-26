---
ticket: QC-WEB-001
track: QC
phase: D6
size: L
req_refs:
  - REQ-QC-001
  - REQ-QC-002
  - REQ-QC-003
  - REQ-QC-009
  - REQ-QC-010
depends_on:
  - QC-MODEL-001
  - SAMPLE-WEB-001
touches:
  - code/plus-ui/src/views/lqg/qc/editor/index.vue
  - code/plus-ui/src/views/lqg/qc/editor/SampleQcTab.vue
  - code/plus-ui/src/views/lqg/qc/editor/session.ts
  - code/plus-ui/src/views/lqg/qc/editor/session.spec.ts
  - code/plus-ui/src/lang/lqg/qc-session.*.ts
  - code/plus-ui/src/views/lqg/qc/components/**
  - code/plus-ui/src/api/lqg/qc/**
  - code/plus-ui/src/lang/lqg/qc.*.ts
  - code/RuoYi-Vue-Plus/ruoyi-admin/src/main/resources/db/migration/V20260926132*__QC-WEB-001-*.sql
  - code/plus-ui/src/views/lqg/sample/index.vue
adr_refs: []
blueprint_refs:
  - UI:admin.qc.editor
  - FLOW:F-QC-01.step1
  - FLOW:F-QC-01.step2
  - FIELD:t_lqg_qc_sample.viability_oss_id
  - FIELD:t_lqg_doc_image.oss_id
accept:
  - name: "隐藏菜单可路由：path 与 component 非空、visible 为隐藏、路由下发里存在且标了 hidden；三个权限串下发到内部人员"
    form: MENU
    run: |-
      python3 doc/verify/db.py --sql "SELECT menu_id || ':' || path || ':' || component || ':' || visible FROM sys_menu WHERE menu_id = 5510" --eq "5510:qc-editor:lqg/qc/editor/index:1" &&
      bash doc/verify/api.sh --as staff GET /system/menu/getRouters | jq -e '[.data | .. | objects | select(.component? == "lqg/qc/editor/index")] | length == 1 and .[0].hidden == true' &&
      bash doc/verify/api.sh --as staff GET /system/user/getInfo | jq -e '[.data.permissions[] | select(startswith("lqg:qc:"))] | sort == ["lqg:qc:edit","lqg:qc:publish","lqg:qc:query"]' &&
      test -f code/plus-ui/src/views/lqg/qc/editor/index.vue
    counterfeit: |-
      隐藏菜单的 path 留空（「反正不显示」）→ 第 1 段红：空 path 会让整个 vue-router 崩，所有页面白屏。
      没 seed 这个路由、直接在前端静态路由表里写死 → getRouters 里找不到红；换个没授权的角色也能进。
      visible 写成 '0' → 侧边栏多出一个点进去缺参数就报错的菜单，第 1 段红。
  - name: "前端构建是本次产物；编辑页用了图片位上传与附件组件；带出的只读字段没有进表单；样本表（CR-20260924-10 起是两页、同一个列表组件）的「质控文档」入口已点亮；换样本整页按新样本重载、有改动先问、没带样本给引导、签名地址老了先重取的判据过单测，切页签只由预览面板读一次状态（CR-20260924-11）"
    form: API
    run: |-
      cd code/plus-ui && rm -rf dist && pnpm build:prod >/dev/null && test -f dist/index.html &&
      grep -c 'ImageSlotUploader' src/views/lqg/qc/editor/SampleQcTab.vue | awk '{exit !($1 >= 3)}' &&
      grep -q 'AttachmentList' src/views/lqg/qc/editor/SampleQcTab.vue && grep -q 'preview-src-list\|previewSrcList' src/views/lqg/qc/components/ImageSlotUploader.vue &&
      ! grep -nE 'v-model="form\.(internalNo|donorName|sourceUnitName|receiveDate|processTime|operatorName|gender)"' src/views/lqg/qc/editor/*.vue &&
      grep -q 'qc-editor' src/views/lqg/sample/index.vue &&
      ! grep -nE '\.(skip|todo|only)\(' src/views/lqg/qc/editor/session.spec.ts &&
      pnpm vitest run src/views/lqg/qc/editor/session.spec.ts --reporter=json --outputFile="${TMPDIR:-/tmp}"/lqg-qc-session.json >/dev/null && jq -e '.numFailedTests == 0 and .numPassedTests >= 11' "${TMPDIR:-/tmp}"/lqg-qc-session.json &&
      ! grep -nE '@tab-change=' src/views/lqg/qc/editor/index.vue
    counterfeit: |-
      三个图片位只接了一个 → 计数不足红。
      图片只是缩略显示、点了没反应 → 没用 el-image 的预览能力，第 3 段红（「要求图片可以放大」是模板上写了三遍的话）。
      把内部编号、患者姓名做成了可编辑输入框 → 第 4 段红。
      换样本还显示第一次打开的那个（标签页缓存按路由复用、sampleId 只在 setup 里读一次；CR-20260924-11 之前的形态，Kevin 本机验收「网页工作台」第 8 行）、有没保存的改动时换样本不问、离开时已确认过还再问一遍、没带样本显示上一次的内容、签名地址刚取回就重取（失败 → 重取 → 失败死循环）→ session.spec 对应用例红（共 11 例，skip 掉就不够数）。
      编辑页 `@tab-change` 里再调一次 `loadPages`，加上预览面板自己 watch 页签又读一次 → 一次切换两个请求、两条提示（第 6 行截图里同时弹了两条）→ 最后一段 grep 红。
---

# QC-WEB-001 · 工作台 · 质控文档编辑页（一）：页面骨架、样本质控表页签、图片位上传、细胞活率附件与通用附件

## §0 状态自检（实施前必过，不过就 STOP 报 Kevin）

- [ ] 当前分支符合调度器注入的期望（`track/QC` worktree）——不写死 `feature/dayN`
- [ ] `depends_on` 全部 done：**QC-MODEL-001**、**SAMPLE-WEB-001**
- [ ] 扫 `doc/change-log.md`：涉及本 ticket 的 CR **以 CR 为准**（CR 覆盖本文）
- [ ] 逐条取权威（别全文读蓝图）：`python3 ~/claude-config/skills/xuqiu/scripts/authority_lint.py show <锚>`，锚见上方 `blueprint_refs`；接口形状见 `doc/api-contract.md`
- [ ] 必读：
  - 视觉基准 = **方案 A**（独立整页，左编辑右预览），Kevin 2026-09-17 已选定（CR-20260917-03）。方案 B（抽屉内编辑）已否决
  - `doc/design-options/gallery.html#admin-qc-a`
- [ ] 口径复述（本张最容易做反的）：
  1. 这是工作台里**唯一不用抽屉的录入页**（内容多、带多图）。别因为「若依规约是抽屉」硬塞进抽屉——方案 B 就是那样，已经比较过了。
  2. 从样本主档带出的七个字段在页头摘要条里**只读**展示，不放进表单。要改去样本表改（CR-20260924-10 起分「样本记录信息表」「类器官收样记录」两页；编辑页的「返回」按样本类别回到对应那一页，页头小字同样按类别写，路径与名字取 `views/lqg/sample/pages.ts`）。
  3. 图片位：1-3 张、可拖拽排序、可删、**点图放大**（`el-image` 的 preview）；TIFF 显示后端给的预览图。
  4. 右栏预览面板本张先放占位（「保存后点预览」），DOC-PUBLISH-001 接真的。

## 1 背景与口径

会上 L364-L367：质控表是内部人员在网页上编辑。L109：要添加附件，图片要可以放大缩小。模板：细胞活率测定一格里嵌了一个文件附件。

## 2 实现要点

- 路由：隐藏菜单 5510（C，`visible='1'`，`path='qc-editor'`，`component='lqg/qc/editor/index'`，授 101、102）+ 按钮 5511-5513（`lqg:qc:query / edit / publish`）；
  迁移 `V202609261320__QC-WEB-001-menu.sql`。进入方式：样本表行操作「质控文档」→ `/qc-editor?sampleId=…`（点亮 SAMPLE-WEB-001 里置灰的入口；只对有效样本可点；两页共用 `views/lqg/sample/index.vue` 这个列表，CR-20260924-10）。
- `editor/index.vue`：页头样本摘要条 + 三个页签（各带草稿 / 已完成徽标）+ 左右分栏 + 页脚按钮（保存草稿 / 预览 / 完成并同步——后两个本张置灰）。离开页面有未保存改动时提示。
  一个标签装一个样本（CR-20260924-11，`UI:admin.qc.editor`）：`onActivated` 与 `onBeforeRouteUpdate` 都按地址判——同一个样本不动，换了样本就作废在途请求、清掉表单、预览面板按「样本 | 版本」重建、重新取数，标签标题改成「质控文档 · 内部编号」；当前样本有未保存改动时先问（离开编辑页时已确认过的不再问）；地址里没带样本显示引导（去两张样本表），不显示任何样本。判据是纯函数 `editor/session.ts`（`session.spec.ts` 钉）。换页签只由预览面板 watch 读一次状态，编辑页不再另调 `loadPages`。
  图片与附件的地址是短时签名链接：编辑页 `provide` 一个保鲜函数（`components/freshUrls.ts`），取回满 5 分钟后在缩略图加载失败、点图放大、打开附件之前重取一次 bundle，刚取回的打不开不再重取；重取时有未保存改动不覆盖表单。
- `SampleQcTab.vue`：按模板顺序——患者编号、取样部位、取样方式、临床诊断/既往治疗、收样描述；细胞活率测定（单文件上传，显示文件名，可替换 / 移除）；
  三行「图片位 + 情况描述」。
- `components/ImageSlotUploader.vue`：上传走上游 OSS 上传接口拿 `ossId` → 调 `POST …/image` 绑定；超过 3 张时上传按钮消失；`components/AttachmentList.vue`：通用附件。
- 文案走 `lqg.qc.*`；颜色走 token。

## 3 边界（明确不做）

- 不做类器官质控表与评分表两个页签（QC-WEB-002，本张放占位）
- 不做预览、下载、完成并同步（DOC-PUBLISH-001）
- 不做小程序端编辑（三份文档只在网页编辑）
- 不做图片裁剪、标注

## 4 完工报告要求

1. 编辑页截图（三个图片位各传了图）、点图放大的截图、TIFF 图片显示为预览图的截图
2. **改了哪些文件**（含 Flyway 文件名与取号依据、新增的类 / 页面 / 接口清单）
3. **accept 逐条 ✅ / ❌ + 关键输出**（贴命令输出，不贴「已通过」三个字）
4. **遗留与 raise**：越出 `touches` 的改动、与 `doc/api-contract.md` 不一致的地方、没把握的口径
5. 验证用的后端 / 前端长进程已关，或明示留给谁

- 2026-09-24 按 CR-20260924-10 更新：「样本总表」改称样本表两页，§0 口径 2 补编辑页「返回」按样本类别回对应页（FLOW:F-QC-01.step1）；accept 2 名字同步，`qc-editor` 入口仍在 `views/lqg/sample/index.vue`，逐条核过不用改断言。
- 2026-09-24 按 CR-20260924-11 更新：accept 2 补三段——`session.spec.ts` 不许 skip、全过且 ≥ 11 例（换样本重载、有改动先问、已确认不再问、没带样本给引导、签名地址保鲜判据），编辑页没有 `@tab-change=`（切页签只由预览面板读一次状态，修掉连弹两条「还没生成」）；touches 补 `editor/session*.ts` 与 `lang/lqg/qc-session.*.ts`；§2 补一个标签一个样本与地址保鲜的做法。
