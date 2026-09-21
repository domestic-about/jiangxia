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
  - name: "前端构建是本次产物；编辑页用了图片位上传与附件组件；带出的只读字段没有进表单；样本总表的「质控文档」入口已点亮"
    form: API
    run: |-
      cd code/plus-ui && rm -rf dist && pnpm build:prod >/dev/null && test -f dist/index.html &&
      grep -c 'ImageSlotUploader' src/views/lqg/qc/editor/SampleQcTab.vue | awk '{exit !($1 >= 3)}' &&
      grep -q 'AttachmentList' src/views/lqg/qc/editor/SampleQcTab.vue && grep -q 'preview-src-list\|previewSrcList' src/views/lqg/qc/components/ImageSlotUploader.vue &&
      ! grep -nE 'v-model="form\.(internalNo|donorName|sourceUnitName|receiveDate|processTime|operatorName|gender)"' src/views/lqg/qc/editor/*.vue &&
      grep -q 'qc-editor' src/views/lqg/sample/index.vue
    counterfeit: |-
      三个图片位只接了一个 → 计数不足红。
      图片只是缩略显示、点了没反应 → 没用 el-image 的预览能力，第 3 段红（「要求图片可以放大」是模板上写了三遍的话）。
      把内部编号、患者姓名做成了可编辑输入框 → 第 4 段红。
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
  2. 从样本主档带出的七个字段在页头摘要条里**只读**展示，不放进表单。要改去样本总表改。
  3. 图片位：1-3 张、可拖拽排序、可删、**点图放大**（`el-image` 的 preview）；TIFF 显示后端给的预览图。
  4. 右栏预览面板本张先放占位（「保存后点预览」），DOC-PUBLISH-001 接真的。

## 1 背景与口径

会上 L364-L367：质控表是内部人员在网页上编辑。L109：要添加附件，图片要可以放大缩小。模板：细胞活率测定一格里嵌了一个文件附件。

## 2 实现要点

- 路由：隐藏菜单 5510（C，`visible='1'`，`path='qc-editor'`，`component='lqg/qc/editor/index'`，授 101、102）+ 按钮 5511-5513（`lqg:qc:query / edit / publish`）；
  迁移 `V202609261320__QC-WEB-001-menu.sql`。进入方式：样本总表行操作「质控文档」→ `/qc-editor?sampleId=…`（点亮 SAMPLE-WEB-001 里置灰的入口；只对有效样本可点）。
- `editor/index.vue`：页头样本摘要条 + 三个页签（各带草稿 / 已完成徽标）+ 左右分栏 + 页脚按钮（保存草稿 / 预览 / 完成并同步——后两个本张置灰）。离开页面有未保存改动时提示。
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
