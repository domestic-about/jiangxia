# QC-WEB-001 · 工作台 · 质控文档编辑页（一）：页面骨架、样本质控表页签、图片位上传、细胞活率附件与通用附件

- **票**：`doc/tickets/QC-WEB-001/prompt.md`（track QC / phase D6 / size L）
- **分支**：`task/D6`（未切分支、未 push、未合分支、未动 `doc/waves/state.json` 与 `_manifest.json`）
- **状态**：**done** —— `accept 2/2` 全绿；另加一轮 27 项人眼验收（真浏览器 + 真上传 + 真库）全绿
- **完成时间**：2026-09-22
- **实现者**：impl subagent（QC-WEB-001）

---

## 0. 状态自检（3 级）

- ✅ **PASS** 分支 = `task/D6`（调度器注入，未动）
- ✅ **PASS** `depends_on` 全部 done：**QC-MODEL-001**、**SAMPLE-WEB-001**
  - QC-MODEL-001 的「给下游的坑」10 条逐条读过并对齐（尤其第 2 条 `images` 是 map、第 4 条 `patientNo` 明文、第 9 条 `fileSize` 可为 0、第 10 条「C 页面要 QC-WEB-* 自己落 5510」）
  - DOC-PDF-001 的「给下游的坑」8 条读过（第 4 条专门写给本票：hidden 断言问题 + 可复用 `@/api/lqg/doc`；第 5 条 `DocPagesVO.errorMsg`）
- ✅ **PASS** 扫 `doc/change-log.md` 顶部 8 条 CR：**无一条改动 QC 域页面口径**；CR-20260921-08 只动小程序视觉、CR-20260918-07 明确「网页工作台不受影响」「工作台沿用 plus-ui，主色与语义色值没变」
- ✅ **PASS** 逐条取权威（垫片 `python3 doc/authority/authority_lint.py show <锚>`，5 条全 `status: active`，无 WARN）：
  - `UI:admin.qc.editor`（方案 A：独立整页、左编辑右预览、页头摘要条、三页签带徽标、底部附件、页脚三按钮）
  - `FLOW:F-QC-01.step1`（总表进入 + 七个只读字段）、`FLOW:F-QC-01.step2`（样本质控表字段 + 图片位 + 活率附件 + 通用附件）
  - `FIELD:t_lqg_qc_sample.viability_oss_id`（文档里印文件名，不做 OLE 嵌入）、`FIELD:t_lqg_doc_image.oss_id`（预览页点开看原图）
- ✅ **PASS** 环境可用：后端 8094 / 工作台 8093 起得来；`lqg-dev-gotenberg` 容器未动

**无 STOP 项。** 有 2 处 ⚠️ WARN（取号、5511-5513），见 §5。

---

## 1. 方案选择：隐藏菜单用 **方案 a**（隐藏 M 目录）

### 1.1 问题（issue #221 / #227）

若依 `SysMenuServiceImpl.buildMenus()` 对 `parent_id=0 且 menu_type='C' 且 is_frame='1'` 走 `isMenuFrame()` 专用分支：外层包一个 `Layout` 外壳（外壳 `hidden=true`、`meta=null`、`name=路由名+menuId`），真正的页面变成**子路由且不带 `hidden`**。用户可见行为是对的（外壳 hidden → 侧边栏不渲染），但 **accept 1 第 2 段断的是「`component == "lqg/qc/editor/index"` 的那条路由自身 `hidden == true`」**，打在子路由上必红。

### 1.2 选 a 的理由

1. **合框架惯例**：`sys_menu` 里已有的隐藏 C 页面（116 / 130 / 131 / 132 / 133）**全部**挂在目录菜单下（`parent_id≠0`）→ 走 else 分支 → 路由自身拿到 `hidden=true`。方案 a 是把本票接进这条既有惯例，不是为绕断言打补丁。
2. **不依赖特殊分支**：方案 b（保持 `parent_id=0`、改断言成「外壳 hidden」）要**改票面 accept**（明令禁止），而且让页面继续依赖 `isMenuFrame()` 这条「外壳 + 子路由 meta=null」的隐式行为——DOC-PDF-001 的 5520 实测就是这种形态，外壳 `meta=null` 会让面包屑/页签标题丢信息。
3. **整条链一致**：把 DOC-PDF-001 的 5520 也一起挂进隐藏目录，两条隐藏路由同一形态，侧边栏什么都不出现（实测侧边栏 = `首页|系统管理|样本总表|石蜡包埋|人员与单位|冻存管理`，无「质控文档」「文档渲染状态」）。

### 1.3 代价（如实记）

- **路由 URL 变了**：`5510` 的 `path` 仍是票面要求的 `qc-editor`，但父目录 `5500` 的 path `qc-console` 会做前缀 → 真实路由是 **`/qc-console/qc-editor?sampleId=…`**（DOC-PDF-001 的 5520 同样从 `/doc-console` 变成 `/qc-console/doc-console`）。票面 §2 写的 `/qc-editor` 是按「顶级 C 菜单」推的，方案 a 下不再成立；`views/lqg/sample/index.vue` 里的入口按**实际路由**写，注释里写清了为什么。
- **改了 DOC-PDF-001 的一行**：`UPDATE sys_menu SET parent_id=5500 WHERE menu_id=5520`（**新开迁移**，没碰已应用的 `V202609261420`）。§4 越界清单已记。
- **回归验证**：`/qc-console/doc-console` 在浏览器里照常渲染（见 §3 第 27 项），`getRouters` 里 5520 的 `hidden=true`、`component=lqg/doc/index` 都在。

---

## 2. 交付物与改了哪些文件

### 2.1 Flyway 迁移（1 支，新增）

| 文件 | 内容 |
|---|---|
| `code/RuoYi-Vue-Plus/ruoyi-admin/src/main/resources/db/migration/V202609261430__QC-WEB-001-qc-menu.sql` | 隐藏目录 **5500**（`M`, `visible='1'`, `path='qc-console'`）+ C 页面 **5510**（`qc-editor` → `lqg/qc/editor/index`, `visible='1'`）+ F 按钮 **5511-5513**（三个 perm）+ 把 5520 改挂 5500 + 授权 101/102 |

**★ 取号依据（issue #222：票面写的 `V20260926132*` 已经不能用了）**

```
票面 touches 原写 V20260926132*__QC-WEB-001-*.sql。
但库里**已应用的最大版本 = 202609261420**（DOC-PDF-001 的 V202609261420__DOC-PDF-001-doc-console.sql），
本项目 spring.flyway.out-of-order=false（框架默认）→ 版本号小于已应用最大值时**后端启动即
FlywayValidateException**（本项目因此重建过 4 次开发库）。
1320 < 1420 → 1320 及 13xx 段内所有 ≤1420 的号都不可用。
按 lint-profile「已应用的迁移终身不改名不改内容」+「保证后做的任务版本号一定更大」，
取**下一个未用的分钟 1430**（1430 > 1420）→ 安全；本票在 D6 内、DOC-PDF-001 之后落地，
不占任何后续 ticket 的号。迁移头注释里逐字写了这段依据。
```
实测：qa-up 重建 jar 后后端正常启动、Flyway 校验通过、5510 行落库（见 §3 acc1 第 1 段）。

### 2.2 前端（新增 7 个文件）

| 文件 | 说明 |
|---|---|
| `code/plus-ui/src/api/lqg/qc/index.ts` | QC 域客户端与类型：`getQcBundle / saveSampleQc / saveOrganoidQc / saveScore / addDocImage / removeDocImage / sortDocImages / addDocAttachment / removeDocAttachment` + `OSS_UPLOAD_URL`；文件头把上游 5 个「最容易做反」的形状逐条写死（`images` 是 map、`sort` 传全量 ids #210、`fileSize` 可选 #209、`viabilityOssId=0` 摘掉 #212、保存是补丁语义 null=不动） |
| `code/plus-ui/src/views/lqg/qc/editor/index.vue` | 编辑页骨架：页头样本摘要条（七个只读字段）+ 三个页签（各带草稿/已完成徽标）+ 左编辑右预览 + 页脚三按钮（后两个置灰）+ 未保存改动拦截 + 右栏渲染失败「重新生成」 |
| `code/plus-ui/src/views/lqg/qc/editor/SampleQcTab.vue` | 样本质控表页签：模板顺序的字段 + 细胞活率测定（单文件，传/替换/移除）+ 三个图片位（各带情况描述）+ 通用附件 |
| `code/plus-ui/src/views/lqg/qc/components/ImageSlotUploader.vue` | 图片位：OSS 上传 → 绑定；满 3 张上传入口消失；HTML5 拖拽排序（`PUT image/sort {ids:[全量]}`）；删除（只解绑）；点图放大（`el-image` + `preview-src-list`，失败的图也能点开） |
| `code/plus-ui/src/views/lqg/qc/components/AttachmentList.vue` | 通用附件：多挂、下载、删除；`fileSize` 缺失/为 0 显示「大小未知」而不是 0 B |
| `code/plus-ui/src/lang/lqg/qc.zh_CN.ts` / `qc.en_US.ts` | `lqg.qc.*` 全部文案（键集两侧一致） |

**样式合规**：三个 `.vue` 的 `<style>` 里**零颜色字面量**（`grep -nE '#[0-9a-fA-F]{3,8}|rgba?\('` 在 `views/lqg/qc/**` 无命中），一律 `var(--lqg-*)`（`--lqg-ink / -ink-2 / -ink-3 / -card / -bg / -line / -primary / -primary-soft / -font-mono`），沿用 plus-ui 既有视觉（SYS-WEB-001 的 token 文件 `assets/styles/lqg-tokens.scss`，未改）。文案零硬编码中文（全走 `lqg.qc.*`；唯一例外是置灰入口的 title，进了 `lqg.sample.rowAction.qcDocInvalid`）。

### 2.3 改的既有文件（3 个）

| 文件 | 改动 |
|---|---|
| `code/plus-ui/src/views/lqg/sample/index.vue` | 「质控文档」行操作从 `<el-button disabled>` 点亮：`v-hasPermi="['lqg:qc:query']"`、`verifyStatus !== 'valid'` 时置灰、点击 `router.push('/qc-console/qc-editor?sampleId=…')`（新增 `handleQcDoc`） |
| `code/plus-ui/src/lang/lqg/sample.zh_CN.ts` / `sample.en_US.ts` | 各加 1 个 key `rowAction.qcDocInvalid`（置灰时的 title）。**越出 `touches`**，见 §4 |

### 2.4 后端

**一行 Java 都没改**（票面 `touches` 里也没有 Java）。GET/PUT/POST/DELETE `/lqg/qc/**` 与 `@/api/lqg/doc` 的 render/pages 全部是上游既有端点，本票只消费。

---

## 3. accept 逐条 ✅ + 关键输出

```
$ export LQG_VERIFY_ENV_FILE="$PWD/.tmp/qa-env/8094/verify.env"
$ python3 doc/waves/tools/accept-run.py --ticket QC-WEB-001 --run \
      --json .tmp/qc-web-accept.json --logdir .tmp/qc-web-accept-logs
[run] 2 条 accept，单条超时 900s
  ✓ QC-WEB-001 acc1 [MENU] 隐藏菜单可路由：path 与 component 非空、visible 为隐藏、路由下发里存在且标了 hidden；三个权限串下 (0.2s)
  ✓ QC-WEB-001 acc2 [API]  前端构建是本次产物；编辑页用了图片位上传与附件组件；带出的只读字段没有进表单；样本总表的「质控文档」入口已点亮 (10.9s)
[ok] 结果落盘 .tmp/qc-web-accept.json
[run] 通过 2/2
```
（原始日志：`doc/waves/reports/QC-WEB-001/accept-logs/`、机器结果 `accept-result.json`）

### accept 1 · MENU —— ✅

逐段命令与输出（`accept-logs/QC-WEB-001-acc1.sh.log` 原样）：

```
$ python3 doc/verify/db.py --sql "SELECT menu_id || ':' || path || ':' || component || ':' || visible FROM sys_menu WHERE menu_id = 5510" --eq "5510:qc-editor:lqg/qc/editor/index:1"
5510:qc-editor:lqg/qc/editor/index:1                                   ← 第 1 段 成立（exit 0）

$ bash doc/verify/api.sh --as staff GET /system/menu/getRouters | jq -e '…'
true                                                                   ← 第 2 段 成立：恰 1 条 component=lqg/qc/editor/index 且 hidden==true

$ bash doc/verify/api.sh --as staff GET /system/user/getInfo | jq -e '…'
true                                                                   ← 第 3 段 成立：lqg:qc:{query,edit,publish} 三串齐全
```
（第 4 段 `test -f …/editor/index.vue` 随文件存在成立。）

路由下发的真实形状（`--as staff`，节选自 `getRouters`）：

```json
{ "name": "Qc-console5500", "path": "/qc-console", "hidden": true, "component": "Layout", "alwaysShow": true,
  "meta": { "title": "质控文档", "icon": "documentation" },
  "children": [
    { "name": "Qc-editor5510", "path": "qc-editor",  "hidden": true, "component": "lqg/qc/editor/index", "meta": { "title": "质控文档编辑", "icon": "form" } },
    { "name": "Doc-console5520","path": "doc-console","hidden": true, "component": "lqg/doc/index",        "meta": { "title": "文档渲染状态", "icon": "documentation" } } ] }
```
→ 外壳与两个子路由**都** `hidden=true`（方案 a 的收益），页面不依赖 `isMenuFrame()` 外壳分支。

### accept 2 · API —— ✅

逐段：`rm -rf dist && pnpm build:prod`（exit 0，`dist/index.html` 存在）→ `grep -c 'ImageSlotUploader' SampleQcTab.vue = 4 (≥3)` → `AttachmentList` 有 → `preview-src-list` 有 → 七个只读字段**没有**任何 `v-model="form.xxx"` → `sample/index.vue` 含 `qc-editor`。build 日志（`accept-logs/QC-WEB-001-acc2.sh.log`）只有一条 rollup 的 chunk >500kB 体积告警（既有历史现象，与本票无关）。

### 3.1 另加：人眼验收 27/27（真浏览器 + 真上传 + 真库）

`node doc/waves/reports/QC-WEB-001/ui-check.mjs`（Playwright + chromium headless，连真工作台 8093 / 真后端 8094 / 真库 5433 / 真 MinIO）→ **27/27 PASS**，日志 `doc/waves/reports/QC-WEB-001/ui-check.log`。覆盖票面 §4 要的三张截图之外的机器断言：

| # | 断言 | 证据 |
|---|---|---|
| 1-4 | 页头七个只读字段齐（T-hli01 / 测试供体甲 / A 医院 / 李工…）、三页签带「已完成」徽标、3 个图片位 + 1 个附件区 | DOM innerText |
| 5 | 三个图片位各传一张后库里 `observe:2 / orig:3 / pretreat:1` | psql |
| 6-7 | **TIFF 的 `preview_oss_id ≠ oss_id`，且原图 `.tif` / 预览图 `.jpg`**（真 TIFF 上传 → 后端另存 JPEG 预览） | psql `sys_oss.file_suffix` |
| 8-10 | 页面 6 张缩略图；orig 满 3 张后**上传入口消失**；observe 未满仍有入口 | DOM |
| 11-13 | 点图放大浮层真打开；TIFF 点开放大的**不是 `.tif` 地址**；真上传的 PNG 点开是原图签名地址（不是 seed 假地址） | DOM `src` |
| 14 | **拖拽排序真落库**：orig 三个 id 顺序变了、集合没变（`9000005301,9000005302,2102345788…` → `9000005302,2102345788…,9000005301`） | psql |
| 15 | 有未保存改动点「返回样本总表」→ 弹「有未保存的改动…留下继续编辑 / 离开」 | DOM |
| 16-17 | 「保存草稿」落库（患者编号密文变 `8pRfE0…` → `6gqF+R…`）；doc_status 不动（上游口径，见 §5 WARN-4） | psql |
| 18-20 | 细胞活率附件：选中显示文件名 → 保存落库 `2102345997714767874/viability.txt` → **「移除」后保存 = 库里两列都 NULL**（issue #212） | psql |
| 21-22 | 通用附件落库（`attachment.txt/63`，seed 的 `活率报告.pdf/319488` 仍在）；附件区列出刚挂的文件 | psql + DOM |
| 23-24 | 样本总表「质控文档」入口：有效样本（T-hli01/T-hli02/T-hga03/T-hco04）**可点**，待核验样本（`—`）**置灰** | DOM |
| 25 | **侧边栏没有「质控文档」「文档渲染状态」**：`首页\|系统管理\|样本总表\|石蜡包埋\|人员与单位\|冻存管理` | DOM |
| 26 | 回归：`/qc-console/doc-console` 改挂隐藏目录后**仍可路由**、页面渲染出来 | DOM |
| 27 | 无未捕获 JS 异常（忽略 seed 假地址图必报的资源加载失败） | pageerror |

### 3.2 票面 §4 要的三张截图（`doc/waves/reports/QC-WEB-001/shots/`）

| 文件 | 对应票面要求 |
|---|---|
| `02-editor-three-slots.png` | 编辑页截图，**三个图片位各传了图**（orig 3 / observe 2 / pretreat 1，含一张真 TIFF） |
| `04-png-click-zoom.png` | 点图放大（真 PNG，浮层里是原图的 MinIO 签名地址） |
| `03-tiff-click-zoom.png` + `03-tiff-thumb-preview.png` | **TIFF 显示为后端预览图**：特写看缩略图 + 点开看放大后的 JPEG 预览（`.tif` 原图不在浮层里） |
| 其余 | `01-editor-before`（进入态）/ `05-after-sort`（拖拽后）/ `06-unsaved-dirty` / `07-unsaved-guard`（离开拦截）/ `08-after-save` / `09-sample-entry`（总表入口，有效可点、待核验置灰）/ `10-attachments` / `11-doc-console-regression` |

---

## 4. 越界清单（超出 `touches` 的改动，全部如实列出）

| # | 文件 | 为什么动 | 越界程度 |
|---|---|---|---|
| 1 | `code/plus-ui/src/lang/lqg/sample.zh_CN.ts`、`sample.en_US.ts` | 给置灰的「质控文档」入口加 title 文案 `rowAction.qcDocInvalid`（各 1 行）。i18n 约定要求文案进 `lqg.<域>.*`，没有别的落点；域文件里只 append，不动任何既有 key | 轻（touches 只写了 `sample/index.vue`） |
| 2 | 迁移里 `UPDATE sys_menu SET parent_id=5500 WHERE menu_id=5520` | 方案 a 要把 DOC-PDF-001 的隐藏页一起挂进隐藏目录（见 §1）。**用新迁移改，没碰已应用的 `V202609261420`** | 中：改了别的票创建的菜单行的 `parent_id`/`order_num`（不改它的 path/component/perms/授权）。已回归验证其路由仍通（§3 第 26 项） |
| 3 | `doc/waves/reports/QC-WEB-001/**` | 本票取证目录（截图 / 脚本 / accept 日志） | 无（报告目录本来就归本票） |

**没动的**：`code/RuoYi-Vue-Plus/**` 的 Java 与资源（除新迁移）、`code/miniapp/**`、`doc/authority/*.yaml`、`doc/requirements.yaml`、`doc/change-log.md`、`doc/verify/seed/**`、`doc/verify/gen_seed.py`、`doc/verify/api.sh`、`doc/waves/state.json`、`_manifest.json`。`code/plus-ui/src/api/lqg/qc/.gitkeep` 与 `views/lqg/qc/.gitkeep` 原样留着。

---

## 5. WARN 清单（请逐条入账）

1. **WARN-1 · 迁移取号：票面的 `V20260926132*` 不可用**（issue #222）。已应用最大版本 202609261420 + `out-of-order=false` → 1320 会让后端启动即 `FlywayValidateException`。本票取 **`V202609261430__QC-WEB-001-qc-menu.sql`**，取号依据写进迁移头注释与 §2.1。建议把 13xx 段「已过期号」写进 lint-profile，免得下一张 QC 票再踩。
2. **WARN-2 · 菜单 5511-5513 与 5501-5503 是同一组 perm 的第二份菜单行**。票面 §2 字面要求落 5511-5513；而 QC-MODEL-001 已经落了 `5501 lqg:qc:query / 5502 lqg:qc:edit / 5503 lqg:qc:publish` 并授给 101/102（其「给下游的坑」第 10 条说 perms 已齐、只缺 C 页面）。本票按票面字面落，`SysMenuMapper.selectMenuPermsByUserId` 返回 `HashSet` → **权限集合自动去重，accept 1 第 3 段不受影响**；代价只是「系统管理 → 菜单管理」树里多出三个同名按钮。若后续要清理，删 5511-5513 不影响任何 accept。
3. **WARN-3 · 隐藏菜单改用「隐藏 M 目录」后路由 URL 变了**：5510 = `/qc-console/qc-editor`（票面 §2 写的 `/qc-editor` 不再成立）、5520 = `/qc-console/doc-console`（原 `/doc-console`）。理由见 §1.3。文档/后续票若引用这两个 URL，请按新值。
4. **WARN-4 · 「published 文档再保存回到 draft」在 QC-MODEL-001 里**没有**实现**：`QcDocService.saveSampleQc` 的 `doc_status` 一个字都不动（其类注释明说「本票里它仍然是 published」，状态机在 DOC-PUBLISH-001）。但 `V202609261300` 的 DDL 注释写的是「published 后再保存内容 → 回到 draft」。**两处口径不一致，本票按实现（不动）如实记录并留给 DOC-PUBLISH-001 定夺**。人眼验收因此断言「保存不改 doc_status」而不是「回到 draft」。
5. **WARN-5 · 图片位里 seed 的假图点开是空浮层**。seed 的 4 张图是 `service='seed'` / `https://seed.invalid/...`（QC-MODEL-001 的既定测试数据），浏览器取不到字节 → `el-image` 的 `#error` 槽。上游 `image.mjs` 只把 click 处理器挂在它自己渲染的 `<img>` 上，加载失败时 img 被 `#error` 槽替换 → **那种图点了本该没反应**。本票额外按 id 存了组件引用、点整块都走 `expose` 出来的 `showPreview()`，保证「点了有反应」；但浮层里仍然是取不到的假地址（平台限制，不是本页缺陷）。
6. **WARN-6 · 任务书里给的 Playwright 取址不对**：`createRequire(path.join(WS,'code/plus-ui/package.json'))` 本机 `MODULE_NOT_FOUND` —— `playwright` 装在小程序前端 `code/miniapp/package.json`（与 D3 的 `L2-lib.mjs` 一致）。本票脚本改用 `code/miniapp/package.json`，并在脚本头注释里写明。浏览器用 `~/Library/Caches/ms-playwright` 缓存（未下载）。
7. **WARN-7 · `pnpm build:prod` 留下 `code/plus-ui/dist/`**（accept 2 自己 `rm -rf dist` 后重建）。该目录被 gitignore，不进提交。
8. **WARN-8 · 置灰入口的 title 文案改了 sample 域 i18n 文件**（§4 第 1 条）。若 SAMPLE-WEB-001 后续重排 `rowAction` 键序，注意别把 `qcDocInvalid` 冲掉。

---

## 6. 给下游的坑（QC-WEB-002 / DOC-PROOF-001 / DOC-PUBLISH-001 / DOC-MP-*）

1. **★ 路由与页面落点（QC-WEB-002 / DOC-PUBLISH-001）**：质控编辑页的真实 URL 是 **`/qc-console/qc-editor?sampleId=<id>`**（隐藏目录 5500 的 path `qc-console` 是前缀），不是 `/qc-editor`。菜单 **5510 已被占**（`C`, `visible='1'`, `component='lqg/qc/editor/index'`）；QC-WEB-002 要加第二个页面（或把两类器官页签拆页）请另取号，**不要复用 5510**（component 一撞就出现两条同名路由 —— EMBED-WEB-001 被逼搬过一次号）。若也要隐藏，**挂到 5500 这个隐藏目录下**（`parent_id=5500`），别再用顶级 C + `is_frame='1'`（会落进 `isMenuFrame()` 外壳分支、断言打不到子路由）。
2. **★ 组件可直接复用**：`views/lqg/qc/components/ImageSlotUploader.vue`（props `sampleId / docType / slot / images / disabled`，emit `changed`）与 `AttachmentList.vue` 是通用的。类器官质控表页签只要传 `docType='organoid-qc'`、`slot='organoid_observe'` 即可（`images` 是 map，**每个位都在**：样本质控表是 `orig/observe/pretreat`，类器官是 `organoid_observe`，评分表恒 `{}`）。
3. **★ 三个 tab 的骨架已就位、后两个是 `el-empty` 占位**：`editor/index.vue` 的 `TABS` 常量里已带 `docKind`（`sample_qc / organoid_qc / organoid_score`），右栏渲染状态、页签徽标、页脚按钮都已按 tab 联动 —— QC-WEB-002 只需把 `<el-empty>` 换成真页签组件，并在 `handleSaveDraft` 里接上 `saveOrganoidQc / saveScore`（api 客户端已按契约留好）。
4. **★ 状态机留给 DOC-PUBLISH-001**：`doc_status` 现在**不会**因保存而回 draft（见 WARN-4），页脚「预览」「完成并同步」是 disabled + tooltip。`lqg:qc:publish` 端点（`POST …/{docType}/publish|unpublish`）**尚未实现**。
5. **右栏预览的接口已经接好一半**：`editor/index.vue` 已经在切页签时调 `GET /lqg/doc/{sampleId}/{docKind}/pages?audience=internal`，**只用它的 `status / errorMsg / pages.length`**：`failed` 显示原因 + 「重新生成」（调 `POST …/render`，`@/api/lqg/doc` 的 `renderDoc`）。DOC-PUBLISH-001 接真预览时直接在同一个右栏里渲染 `res.data.pages[].url`（**10 分钟签名链接**，别缓存别存库）；「还没渲染过」时后端返回 **400 + 原因**（DOC-PDF-001 WARN-10），本页把它当 `status='none'` 处理，不是 failed。
6. **★ 保存是补丁语义（`null` = 不动、空串 = 清空）**：`SampleQcTab.save()` **整份表单一起发**（不是只发改动过的键）—— 只发改动键会让「把某个文本框清空」被后端当成「不动」而静默丢失。QC-WEB-002 的保存也要照做。
7. **★ 细胞活率附件「摘掉」的唯一约定是 `viabilityOssId=0`**（issue #212），传 `null` 是「不动」。本票实测：传 → 保存落库 → 点「移除」→ 保存后 `viability_oss_id` 与 `viability_file_name` 两列都 NULL。
8. **★ 只解绑，不删 OSS 对象**：101/102 有 `system:oss:upload / download / query`，**没有 `system:oss:remove`**。所以图片/附件的「删除」一律只调 `/lqg/qc/**` 的解绑端点；**不要**调 `@/api/system/oss` 的 `delOss`（会 403）。框架自带的 `components/FileUpload/index.vue` 默认会 `delOss`，**不要直接把那个组件搬过来**。
9. **★ TIFF 的路子是「后端另存 JPEG 预览」（DOC-PROOF-001 / DOC-MP-* 都受益）**：本机 JDK 自带的 ImageIO **能读 TIFF**，上传 `.tif` 后 `preview_oss_id ≠ oss_id`（实测 `sys_oss.file_suffix` = `.tif` / `.jpg`）。页面上显示的是 `previewUrl`；点图放大时若原图后缀是 `.tif/.tiff` 则退回 `previewUrl`（浏览器放不出 TIFF）。**进 Word 的图用 `previewUrl`、点开看原图用 `url`**（QC-MODEL-001 的 WARN-9）。
10. **DOC-PROOF-001**：本票没有产生任何给甲方的样张，也不需要；截图全是内部验收用的（`shots/`）。真正要出给甲方看的「三份 Word 与原件并排」仍走 `doc/waves/reports/DOC-PDF-001/rendered` + Gotenberg 容器（DOC-PDF-001 的坑 1）。
11. **DOC-MP-001/002**：本次没有改任何 `/mp/**` 端点，也没有改 `pages` 的形状。工作台新增的只是「谁能编辑」这一侧；小程序仍走 `/mp/int|ext/doc/**`（尚未实现）。
12. **所有人**：本票**没改** `application-*.yml`、没动 `nonProxyHosts` 那条 JVM 参数、没停 `lqg-dev-gotenberg`。DOC-RENDER-001 查出的「发往 127.0.0.1 的请求被系统代理吃掉」病灶不受本票影响。

---

## 7. 长进程与端口 / 收工自检

- 起环境的命令：`bash doc/waves/tools/qa-up.sh --backend-port 8094 --web-port 8093 --no-mp`（日志 `.tmp/qa-env/8094/`）
- 收工：`bash doc/waves/tools/qa-up.sh --down --backend-port 8094 --web-port 8093 --no-mp`（**只按 PID**；未用 `pkill -f ruoyi-admin.jar`）
- 8080（Kevin 的本机服务）/ 5432 / 6379 全程未碰；`lqg-dev-gotenberg` 容器全程 running
- 收工前 `bash doc/verify/reseed.sh --yes` 回快照（本票的人眼验收往 9000001001 名下加了图片/附件并改了患者编号，已还原）
- **收尾实测**：起的两条进程已**按 PID** 关停并确认端口释放。本票起过两轮环境（第二轮改完 CSS token 后复跑 accept + 人眼验收）：
  - 第一轮：工作台 dev 8093（pid **41079**）、后端 8094（pid **40316**）——已关停
  - 第二轮（最终树）：工作台 dev 8093（pid **25322**）、后端 8094（pid **24451**）——已关停

```
$ bash doc/waves/tools/qa-up.sh --down --backend-port 8094 --web-port 8093 --no-mp
  ✓ 已按 PID 关停 工作台 dev(8093)（pid 25322）
  ✓ 已按 PID 关停 后端(8094)（pid 24451）
  ✓ 8094 已释放   ✓ 8093 已释放   ✓ 9202 已释放
$ for p in 8094 8093 9202; do lsof -ti tcp:$p -sTCP:LISTEN; done     # 三个端口全空
```
- `lqg-dev-gotenberg` 仍 `Up (healthy)`（未停）；8080 上是**另一个项目**（dongjiaoshan）的 ruoyi-admin（pid 5676），本票全程未碰；`code/miniapp/src/pages.json` 未被改（本票 `--no-mp`）

```
$ git status --porcelain
 M code/plus-ui/src/lang/lqg/sample.en_US.ts
 M code/plus-ui/src/lang/lqg/sample.zh_CN.ts
 M code/plus-ui/src/views/lqg/sample/index.vue
?? code/RuoYi-Vue-Plus/ruoyi-admin/src/main/resources/db/migration/V202609261430__QC-WEB-001-qc-menu.sql
?? code/plus-ui/src/api/lqg/qc/index.ts
?? code/plus-ui/src/lang/lqg/qc.en_US.ts
?? code/plus-ui/src/lang/lqg/qc.zh_CN.ts
?? code/plus-ui/src/views/lqg/qc/components/
?? code/plus-ui/src/views/lqg/qc/editor/
?? doc/waves/reports/QC-WEB-001/

$ git diff --stat
 code/plus-ui/src/lang/lqg/sample.en_US.ts   |  1 +
 code/plus-ui/src/lang/lqg/sample.zh_CN.ts   |  1 +
 code/plus-ui/src/views/lqg/sample/index.vue | 28 +++++++++++++++++++++++++++-
 3 files changed, 29 insertions(+), 1 deletion(-)
```
无删除（0 个 `D`）；`code/miniapp/src/pages.json` 未被 dev server 改（本票 `--no-mp`，没起小程序）。
