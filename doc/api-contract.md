# 接口约定（ticket 与 accept 断言共用的那一份）

> 这不是蓝图三件套之一，是拆 ticket 时为了让 40 多张 ticket、三个端对同一批接口说同一种话而定的**路径与形状约定**。
> accept 断言里的路径、字段名全部取自这里；实现时要改路径或字段名 → 先改这里，再改引用它的 ticket 的 accept（`grep -rn '<旧路径>' doc/tickets`）。
> 字段名 = `authority/field-ssot.yaml` 列名转 camelCase；字典值 = SSOT `dicts` 里的 value。

## 通用

- 响应体：单个 `{code, msg, data}`；分页 `{code, msg, rows, total}`（若依 `R` / `TableDataInfo`）。**HTTP 状态码几乎恒为 200，成败看 `code`。**
- 业务错误码：参数 / 业务校验不过 `code=400` 类（以 msg 说明）；无权限 `code=403`；外部访问不可见的样本一律 `code=404` 且 `data` 为空（不泄露存在性）。
- id 一律雪花 Long；断言里用 `tostring` 比较（小 id 会被序列化成数字，大 id 是字符串）。
- 时间：`yyyy-MM-dd HH:mm:ss`；日期：`yyyy-MM-dd`。
- 请求头：`Authorization: Bearer <token>` + `clientid: <clientId>`（缺 clientid → token 无效）。
- 三组前缀、三种身份：

| 前缀 | 谁能调 | 说明 |
|---|---|---|
| `/lqg/**` | 工作台：`lqg_admin`、`lqg_internal` | 后端用 `@SaCheckPermission("lqg:<域>:<资源>:<动作>")`，与菜单 perms 逐字一致 |
| `/mp/int/**` | 小程序 · 内部人员 | `@SaCheckRole("lqg_internal")`（管理员同时带 internal 角色） |
| `/mp/ext/**` | 小程序 · 外部人员 | **唯一**对 `lqg_external` 开放的业务接口组；全部经 `ExtScopeService`，只返回 `Ext*Vo`（ADR-0004） |
| `/mp/me`、`/mp/ocr/**`、`/mp/dict/**` | 小程序 · 内外部都能调 | 不带任何样本数据 |

## SYS

| 方法 路径 | 说明 |
|---|---|
| `GET /lqg/sys/ping` | 登录即可调。`data:{module:"ruoyi-lqg", db:"PostgreSQL", dbVersion, tenantEnabled:false, encryptEnabled:true\|false, mockLogin:true\|false, profile, buildCommit}`（`buildCommit` = 打包时注入的 git 提交号，远程环境靠它反 stale）——用来证明业务模块真的挂进了后端、库真的是 PostgreSQL |

## AUTH

| 方法 路径 | 说明 | 关键形状 |
|---|---|---|
| `POST /auth/login` | 小程序登录：`{clientId, grantType:"xcx", tenantId:"000000", xcxCode, phoneCode}` | `data.access_token`。dev / test 下 `xcxCode="mock:<key>"`、`phoneCode="mock:<手机号>"` 走 mock（ADR-0008） |
| `GET /mp/me` | 当前身份 | `data:{userId, name, phoneMasked, identity:"internal"\|"external", ext:{unitId, unitName, groupId, groupName, unitNameInput, groupNameInput, bindStatus, rejectReason}\|null}` |
| `GET /mp/ext/units` | 单位—组别选择器数据 | `data:[{unitId, unitName, groups:[{groupId, groupName}]}]`，只含 active |
| `PUT /mp/ext/profile` | 外部填 / 改姓名、单位、组别 | `{realName, unitId?, groupId?, unitNameInput?, groupNameInput?}` → `bindStatus=pending` |
| `GET/POST /lqg/auth/staff`、`PUT /lqg/auth/staff/{userId}/role`、`PUT …/{userId}/reset-pwd`、`DELETE …/{userId}` | 内部人员授权 / 改角色 / 重置密码 / 撤销 | POST `{phone, name, roleKey:"lqg_internal"\|"lqg_admin", password}` → `data:{userId, upgraded:true\|false}` |
| `GET/POST/PUT/DELETE /lqg/auth/unit`、`/lqg/auth/group` | 单位、组别维护 | 组别列表带 `verifiedCount` |
| `GET /lqg/auth/ext-user/list`、`PUT /lqg/auth/ext-user/{userId}/verify` | 外部用户与组别核验 | verify：`{action:"approve"\|"reject", unitId?, groupId?, createUnit?, createGroup?, reason?}` |

## SAMPLE

| 方法 路径 | 说明 |
|---|---|
| `GET /lqg/sample/list` | 总表。参数：`sourceUnitId, groupId, sampleKind, submitSource, verifyStatus, receiveDateBegin/End, tissueType, internalNo, operatorName, donorName（精确）, hospitalNo（精确）, pageNum, pageSize`。行内带 `hint:{blockCount, sectioned, stains:[…]}`（SAMPLE-HINT-001 之后） |
| `GET /lqg/sample/{id}`、`POST /lqg/sample`、`PUT /lqg/sample`、`DELETE /lqg/sample/{ids}` | 内部增删改查（内部新增直接 `valid`） |
| `PUT /lqg/sample/{id}/verify` | `{action:"valid"\|"invalid", receiveDate, internalNo, isFixed, processTime, hasQcSheet, hasViabilityReport, operatorName, reason}` |
| `POST /lqg/sample/export/tissue`、`POST /lqg/sample/export/organoid` | 按当前筛选导出 xlsx（表单参数同 list） |
| `GET /mp/int/sample/list`、`GET/POST/PUT /mp/int/sample` | 小程序内部（tissue 与 organoid 共用，`sampleKind` 区分）：list 给「内部管理」表格页（只读）与「历史编辑记录」用，POST 给首页新增、PUT 给历史编辑记录里的修改。list 参数：`keyword`（内部编号 / 来源单位）、`donorName`（精确）、`verifyStatus`、`sampleKind`、`sort=recent`（给「历史编辑记录」用：有改动按最后修改时间、否则按创建时间倒序；**默认是中心全部内部人员的记录**，CR-20260918-07）、`mine=true`（「只看我提交的」开关打开时才带：`create_by = 当前用户 OR update_by = 当前用户`）。list 的行 = 该工作表的全部模板列 + `hint`，历史编辑记录场景下另带 `handlerName`（经手人）与 `mine`（是否本人，前端显示「我」）；详情多两个键 `updateByName`、`updateTime`（「最后修改」）。**PUT**：有效样本全部字段可改（`submitNo / submitSource / submitterId` 不可改）；待核验、无效的外部样本 → 400（核验与改判只走 `/lqg/sample/{id}/verify`） |
| `GET /mp/ext/sample/list` | 「历史编辑记录」的样本两个页签用。参数 `sampleKind?`、`verifyStatus?`、`onlyMine?`；行 = `ExtSampleVo{id, submitNo, sampleKind, donorNameMasked, tissueType, organoidType, verifyStatus, invalidReason, submitterName, mine, editable, createTime, updateTime}`（`invalidReason` 在 D2 补：外部必须能看到本人无效样本的原因才能改后重提） |
| `GET /mp/ext/sample/{id}` | `ExtSampleDetailVo{id, submitNo, sourceUnitName, donorName, gender, age, hospitalNo, tissueType, hasPathology, remark, verifyStatus, invalidReason, submitterName, mine, editable, createTime, embeds:[ExtEmbedVo], docs:[ExtDocVo]}`。**没有** `operatorName / verifyBy / receiveDate…` 这些键；`internalNo` 默认也没有，只有系统参数 `lqg.ext.show-internal-no` 打开时才带上（CR-20260918-07，默认 false） |
| `POST /mp/ext/sample`、`PUT /mp/ext/sample/{id}` | 组织样本：只收送检段字段；PUT 仅本人的 pending / invalid，成功后回到 pending |
| `POST /mp/ext/organoid`、`PUT /mp/ext/organoid/{id}` | 外部填类器官收样记录（CR-20260917-05）：`ExtOrganoidSubmitBo{sourceUnitId?, sourceUnitName, organoidType, remark?}` → `sample_kind='organoid'`、`submit_source='external'`、`verify_status='pending'`；PUT 规则同组织样本。入参里夹带的 `internalNo / receiveDate / verifyStatus` 等不生效 |
| `GET /mp/dict/hints?type=tissue\|organoid\|sample` | 联想词（三个 `lqg_hint_*` 字典） |
| `GET /mp/int/export/{sheet}` | 小程序表格页「导出 Excel」（REQ-SYS-017）。`sheet` ∈ `tissue \| organoid \| embed \| cryo`；查询参数 = 对应工作表 list 的筛选参数；响应 = xlsx 文件流（`Content-Disposition` 带中文文件名，如 `样本记录信息表-20260918.xlsx`）。表头列序与 `/lqg/sample/export/*`、`/lqg/embed/export`、`/lqg/cryo/batch/export` **同一个导出视图**。小程序用 `wx.downloadFile({url, header})` 带上 `Authorization` 与 `clientid` 取临时文件，再 `openDocument` / `shareFileMessage`。外部角色 403 |

## EMBED / CRYO

| 方法 路径 | 说明 |
|---|---|
| `GET /lqg/embed/list`、`GET/POST/PUT/DELETE /lqg/embed`、`POST /lqg/embed/export` | 行内带 `internalNo`（读时带出）、`markers:[{markerName, expression}]`、`submitSource`、`verifyStatus`、`submitNo`（所挂样本的送检单号）；`stainTypes` 是数组。list 加筛选 `verifyStatus`、`submitSource`，默认待核验置顶。内部新增直接 `valid` 且石蜡块编号必填 |
| `PUT /lqg/embed/{id}/verify` | 核验外部提交的石蜡包埋送样（CR-20260917-05）：`{action:"valid"\|"invalid", paraffinBlockNo, reason}`；判有效：石蜡块编号必填且唯一、所挂样本必须已 `valid`；判无效：`reason` 必填。合法转移同样本主档 |
| `GET /mp/int/embed/list`、`GET/POST/PUT /mp/int/embed` | 同形状；list 给「内部管理」表格页与「历史编辑记录」（`sort=recent`，默认全中心；`mine=true` 只在「只看我提交的」打开时带，行带 `handlerName` / `mine`，CR-20260918-07）用 |
| `GET /mp/ext/embed/list`、`GET /mp/ext/embed/{id}`、`POST /mp/ext/embed`、`PUT /mp/ext/embed/{id}` | 外部填石蜡包埋送样记录（CR-20260917-05）。list 参数 `onlyMine?`、`verifyStatus?`，行 = 可见样本下的全部石蜡包埋记录（含外部提交还没核验的）；`ExtEmbedSubmitBo{sampleId, sampleType?, organoidSourceType?}`：`sampleId` 必须是**本人**提交、未判无效的样本；PUT 仅本人的 pending / invalid，成功后回到 pending |
| `ExtEmbedVo` | `{id, sampleId, submitNo, paraffinBlockNo, sampleType, organoidSourceType, tissueReceiveTime, tissueProcessTime, agaroseEmbedTime, dehydrateTime, agaroseSendTime, paraffinEmbedTime, sectionTime, sectioned, stainTypes, stainOther, markers, verifyStatus, invalidReason, submitterName, mine, editable, embedBy, operatorName}`——`embedBy`（包埋人）与 `operatorName`（操作人）2026-09-18 起对外可见（CR-20260918-07，甲方「应该是可以看得到操作人、包埋人，看不到冻存信息」）；**仍然没有** `remark / verifyBy / internalNo`（内部编号按 `lqg.ext.show-internal-no` 开关） |
| `GET /lqg/cryo/batch/list` | 参数：`internalNo, cryoName, location(minus80\|ln2), overdueOnly, freezeTimeBegin/End`。行内带 `remainingQty, location, overdue, overdueDays, internalNo`；响应另带 `data.tabCounts` 或顶层 `tabCounts:{all, overdue, ln2}` |
| `GET/POST/PUT/DELETE /lqg/cryo/batch`、`POST /lqg/cryo/batch/export` | `initQty` 可改：锁批次行后从新的初始支数出发按 `flow_time` 正序逐笔累加未删流水，**任一步 < 0 → 400** 且库里不变（CR-20260917-04）；详情带 `updateByName`、`updateTime` |
| `POST /lqg/cryo/batch/{id}/flow` | `{flowType:"take"\|"add"\|"adjust", qty, purpose, operatorName?, flowTime?}`；take / add 的 `qty` 是正整数，adjust 的 `qty` 带符号 |
| `PUT /lqg/cryo/batch/{id}/flow/{flowId}`、`DELETE /lqg/cryo/batch/{id}/flow/{flowId}` | 改 / 删一笔登记（REQ-CRYO-008）。PUT `{qty, purpose, operatorName, flowTime}`：`flowType` 不可改（传了不同的 → 400），`qty` 按原类型解释（take / add 正整数，adjust 带符号不为 0），`fromLocation` 保持登记时的值。DELETE = 软删。两者都锁批次行后逐笔重算，**任一步剩余 < 0 → 400** 且库里不变；`flowId` 不属于这个批次 → 404 |
| `PUT /lqg/cryo/batch/{id}/to-ln2` | `{toLn2Time, ln2Location}` |
| `GET /lqg/cryo/batch/{id}/flows` | 未删流水，时间倒序；每行带 `balanceAfter`（按时间正序累计算出的操作后剩余，不落库）、`edited`（改过为 true）、`updateByName`、`updateTime` |
| `GET /lqg/cryo/overdue` | 超期批次清单（同一判定函数） |
| `/mp/int/cryo/**` | 2026-09-17 晚起（CR-20260917-05）只有：`GET …/batch/list`（历史编辑记录用 `sort=recent`，默认全中心；`mine=true` 只在「只看我提交的」打开时带，行带 `handlerName` / `mine`，CR-20260918-07）、`GET/POST/PUT …/batch`（首页新增、历史编辑记录里修改，改 `initQty` 同样逐笔校验）、`GET …/batch/{id}/flows`（只读）。**没有**取走 / 补入 / 转液氮 / 改删登记的接口（都在 `/lqg/cryo/**`），没有 export（导出走 `/mp/int/export/cryo`），没有删除批次 |

## QC / DOC

| 方法 路径 | 说明 |
|---|---|
| `GET /lqg/qc/{sampleId}` | 三份文档 + 图片 + 附件 + 从样本主档带出的只读字段；首次访问建三份空草稿 |
| `PUT /lqg/qc/{sampleId}/sample-qc`、`/organoid-qc`、`/score` | 保存草稿；score 只收四个 level，分值由后端回填 |
| `POST/DELETE /lqg/qc/{sampleId}/{docType}/image`、`…/attachment` | `{slot, ossId}` / `{ossId, fileName}` |
| `POST /lqg/qc/{sampleId}/{docType}/publish`、`POST …/unpublish` | 完成并同步 / 手动撤回 |
| `POST /lqg/doc/{sampleId}/{docKind}/render?audience=` | 触发渲染（幂等：指纹未变直接返回 done） |
| `GET /lqg/doc/{sampleId}/{docKind}/pages?audience=` | `data:{status, pages:[{pageNo, url}], images:[{url, previewUrl}], attachments:[…]}` |
| `GET /lqg/doc/{sampleId}/{docKind}/download?format=docx\|pdf&audience=` | `data:{url, fileName}`（短时签名链接） |
| `GET /mp/int/doc/list`、`GET /mp/int/doc/{sampleId}/{docKind}/pages`、`…/download` | 内部：全部已完成文档，`audience` 固定 internal。list 可带 `sampleId`（预览页顶部切换条取这个样本的几份）。列表上的「下载」「合并下载」与预览页底部调的是同一个 download（CR-20260917-04） |
| `GET /mp/ext/doc/list`、`GET /mp/ext/doc/{sampleId}/{docKind}/pages`、`…/download` | 外部：过 ExtScope，`audience` 固定 external，只含已完成且外部版渲染成功的（三种 docKind 都给：sample_qc / organoid_qc / organoid_score，外加 merged）；`ExtDocVo{sampleId, submitNo, donorNameMasked, docKind, publishedTime, totalScore?}`；list 同样可带 `sampleId` |
| `GET /lqg/home/todo`、`GET /lqg/home/recent` | `data:{pendingSamples, pendingEmbeds, cryoOverdue, pendingExtUsers, renderFailed}`——`pendingEmbeds` = 待核验的石蜡包埋送样数（CR-20260917-05）。小程序首页不再有计数接口 |

`docType` ∈ `sample-qc | organoid-qc | score`（路径里用连字符，对应字典 `sample_qc | organoid_qc | organoid_score`）；`docKind` 另多一个 `merged`。

## OCR

| 方法 路径 | 说明 |
|---|---|
| `POST /mp/ocr/recognize` | multipart `file`；`data:{rawLines:[…], fields:{donorName?, gender?, age?, hospitalNo?, tissueType?, sourceUnitName?}}`——解析不出的键**不出现**。测试环境 `StubOcrProvider`：请求头 `X-Ocr-Stub-Case: <case 名前两位>` 决定返回 `doc/verify/fixtures/ocr-cases.json` 里哪一组 rawLines |
| `GET /lqg/ocr/status` | `data:{provider, paidEnabled}` |
