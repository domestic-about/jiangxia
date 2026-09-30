# 接口约定（ticket 与 accept 断言共用的那一份）

> 这不是蓝图三件套之一，是拆 ticket 时为了让 40 多张 ticket、三个端对同一批接口说同一种话而定的**路径与形状约定**。
> accept 断言里的路径、字段名全部取自这里；实现时要改路径或字段名 → 先改这里，再改引用它的 ticket 的 accept（`grep -rn '<旧路径>' doc/tickets`）。
> 字段名 = `authority/field-ssot.yaml` 列名转 camelCase；字典值 = SSOT `dicts` 里的 value。

## 通用

- 响应体：单个 `{code, msg, data}`；分页 `{code, msg, rows, total}`（若依 `R` / `TableDataInfo`）。**HTTP 状态码几乎恒为 200，成败看 `code`。**
- 业务错误码：参数 / 业务校验不过 `code=400` 类（以 msg 说明）；无权限 `code=403`；外部访问不可见的样本一律 `code=404` 且 `data` 为空（不泄露存在性）。
- 写接口先校验再写库：违规回 `code=400`，msg 一次列全违规项；不会把数据库报错（SQL、表名列名、整行数据、密文、内部 id）回给调用方（CR-20260923-09）。
- 上传超限：单个文件 50MB、一次请求合计 60MB，超了回 `code=413` 与中文提示（HTTP 仍是 200，与若依惯例一致；nginx 侧统一 `client_max_body_size 60m`）。
- 数据库异常与未知异常：只回通用提示加 8 位「错误编号」（如「服务器处理出错，请稍后重试；如一直出现请联系管理员（错误编号 xxxxxxxx）」），原始 message 与堆栈只进服务端日志、带同一个编号。
- 请求参数格式错误（请求体不是合法 JSON、字段类型不对、枚举值不认识）：`code=400`「请求参数格式不正确」，不回 Jackson / Spring 原文；个别补丁接口会写明是哪个字段（如「请求参数格式错误：「receiveDate」的值不合法」）。
- id 一律雪花 Long；断言里用 `tostring` 比较（小 id 会被序列化成数字，大 id 是字符串）。
- 时间：`yyyy-MM-dd HH:mm:ss`；日期：`yyyy-MM-dd`。
- 请求头：`Authorization: Bearer <token>` + `clientid: <clientId>`（缺 clientid → token 无效）。
- 三组前缀、三种身份：

| 前缀 | 谁能调 | 说明 |
|---|---|---|
| `/lqg/**` | 工作台：`lqg_admin`、`lqg_internal`；小程序内部人员（`lqg_internal`，mp client token）也调其中几组：首页待办 `GET /lqg/home/todo`、核验 `PUT /lqg/sample/{id}/verify` 与 `PUT /lqg/embed/{id}/verify`、冻存登记 `/lqg/cryo/batch/{id}/flow`、`…/flow/{flowId}`、`…/to-ln2`、`…/flows`（CR-20260924-10） | 后端用 `@SaCheckPermission("lqg:<域>:<资源>:<动作>")`，与菜单 perms 逐字一致；权限串挂在角色上，不区分工作台还是小程序调用；外部角色一律 403 |
| `/mp/int/**` | 小程序 · 内部人员 | `@SaCheckRole("lqg_internal")`（管理员同时带 internal 角色） |
| `/mp/ext/**` | 小程序 · 外部人员 | **唯一**对 `lqg_external` 开放的业务接口组；全部经 `ExtScopeService`，只返回 `Ext*Vo`（ADR-0004） |
| `/mp/me`、`/mp/ocr/**`、`/mp/dict/**` | 小程序 · 内外部都能调 | 不带任何样本数据 |

## SYS

| 方法 路径 | 说明 |
|---|---|
| `GET /lqg/sys/ping` | **仅内部角色**（`lqg_admin` / `lqg_internal` / `superadmin`，任一即可，`SaMode.OR`），外部账号 `code=403`（CR-20260923-09：它回吐 profile、mock 开关、加密开关、数据库版本）。`data:{module:"ruoyi-lqg", db:"PostgreSQL", dbVersion, tenantEnabled:false, encryptEnabled:true\|false, mockLogin:true\|false, profile, buildCommit}`（`buildCommit` = 打包时注入的 git 提交号，远程环境靠它反 stale）——用来证明业务模块真的挂进了后端、库真的是 PostgreSQL |

## AUTH

| 方法 路径 | 说明 | 关键形状 |
|---|---|---|
| `POST /auth/login` | 小程序登录：`{clientId, grantType:"xcx", tenantId:"000000", xcxCode, phoneCode}` | `data.access_token`。dev / test 下 `xcxCode="mock:<key>"`、`phoneCode="mock:<手机号>"` 走 mock（ADR-0008） |
| `GET /mp/me` | 当前身份 | `data:{userId, name, phoneMasked, identity:"internal"\|"external", ext:{unitId, unitName, groupId, groupName, unitNameInput, groupNameInput, bindStatus, rejectReason}\|null}` |
| `GET /mp/ext/units` | 单位—组别选择器数据 | `data:[{unitId, unitName, groups:[{groupId, groupName}]}]`，只含 active |
| `PUT /mp/ext/profile` | 外部填 / 改姓名、单位、组别；**仅限外部角色**（`lqg_external`），内部账号 `code=403`（CR-20260923-09） | `{realName, unitId?, groupId?, unitNameInput?, groupNameInput?}` → `bindStatus=pending` |
| `GET/POST /lqg/auth/staff`、`PUT /lqg/auth/staff/{userId}/role`、`PUT …/{userId}/reset-pwd`、`DELETE …/{userId}` | 内部人员授权 / 改角色 / 重置密码 / 撤销 | POST `{phone, name, roleKey:"lqg_internal"\|"lqg_admin", password}` → `data:{userId, upgraded:true\|false}`。**改角色后该账号全部 token 失效**（旧 token 回 401），重新登录后按新角色生效；按手机号授权（外部账号原地升级）不踢下线，`/mp/me` 下次请求即显示内部身份，但访问内部接口要重新登录一次（CR-20260923-09） |
| `GET/POST/PUT/DELETE /lqg/auth/unit`、`/lqg/auth/group` | 单位、组别维护 | 组别列表带 `verifiedCount` |
| `GET /lqg/auth/ext-user/list`、`PUT /lqg/auth/ext-user/{userId}/verify` | 外部用户与组别核验 | verify：`{action:"approve"\|"reject", unitId?, groupId?, createUnit?, createGroup?, reason?}` |

## SAMPLE

| 方法 路径 | 说明 |
|---|---|
| `GET /lqg/sample/list` | 工作台两张样本表共用（「样本记录信息表」/sample、「类器官收样记录」/sample-organoid，CR-20260924-10 拆页）。参数：`sourceUnitId, groupId, sampleKind, submitSource, verifyStatus, receiveDateBegin/End, tissueType, organoidType（模糊，CR-20260924-10）, internalNo, operatorName, donorName（精确）, hospitalNo（精确）, pageNum, pageSize`。**不带 `sampleKind` = 两类都查**；工作台两页的列表与导出请求都显式带 `sampleKind`（重置筛选不清它）。行内带 `hint:{blockCount, sectioned, stains:[…]}`（SAMPLE-HINT-001 之后）；行内另带 `relation:{pendingEmbedCount, cryoBatchCount}`（CR-20260924-11，工作台两页的「石蜡包埋 / 冻存」一列：`pendingEmbedCount` = 名下未删且待核验的石蜡包埋送样数，`cryoBatchCount` = 名下未删的冻存批次数；蜡块数不在这里，沿用 `hint.blockCount`；读时算、整页一次查询，每行都有、没有记录为 0 不是 null；只挂列表，详情与导出不带；外部 VO 没有它）；行与详情带 `passage`（代数，类器官才有值） |
| `GET /lqg/sample/{id}`、`POST /lqg/sample`、`PUT /lqg/sample`、`DELETE /lqg/sample/{ids}` | 内部增删改查（内部新增直接 `valid`）。`PUT /lqg/sample` = 工作台抽屉的**整份替换**（没传的列按清空）；必填（来源单位 = `sourceUnitId` 或 `sourceUnitName`、组织类型或类器官类型、内部编号、收样日期）缺了 → `code=400`，msg 以「提交的内容不符合要求：」开头并列全缺项，库里不变（CR-20260923-09）。入参另有 `passage`（代数，CR-20260924-10）：类器官选填，去首尾空白、开头小写 `p` 转大写后须形如 `P3`（`^P\d{1,3}$`，与冻存批次代数同一规则），不对 → `code=400`「提交的内容不符合要求：代数请填 P 加数字，如 P3」；组织样本传了不报错、库里一律写空 |
| `PUT /lqg/sample/{id}/verify` | 调用方：工作台核验抽屉，**以及小程序内部人员的核验页**（CR-20260924-10；mp client token、`lqg_internal`，权限串 `lqg:sample:verify` 同一个；外部 403）。`{action:"valid"\|"invalid", receiveDate, internalNo, isFixed, processTime, hasQcSheet, hasViabilityReport, operatorName, reason, submitSegment?}`。`submitSegment` = 送检段整段 `{sourceUnitId?, sourceUnitName, donorName, gender, age, hospitalNo, tissueType\|organoidType, passage?, hasPathology, remark}`（`passage` 只对类器官有意义，规则同 `PUT /lqg/sample`，CR-20260924-10）：带了就与核验结论**同一事务**保存，规则同 `PUT /lqg/sample`（按样本自己的类别写本类别那几列，没传的按清空；来源单位可改选正式单位归口）；不带 = 送检段不动（老调用方不受影响）；判无效时送检段照存、收样段不落库；校验不过 `code=400`，库里不变（CR-20260923-09）。★ 整段替换意味着**只带改过的键会把其余键清空**：调用方要么不带 `submitSegment`，要么带整段（小程序核验页送检信息一项没改就不带，改了就带整段、没改的项带原值） |
| `POST /lqg/sample/export/tissue`、`POST /lqg/sample/export/organoid` | 按当前筛选导出 xlsx（表单参数同 list）。`/export/tissue` 表头 = 甲方模板 14 列；`/export/organoid` 表头 = 甲方模板 7 列并在「类器官类型」后插入「代数」（8 列，CR-20260924-10，甲方 2026-09-24 第 18 行要求插入） |
| `GET /mp/int/sample/list`、`GET/POST/PUT /mp/int/sample` | 小程序内部（tissue 与 organoid 共用，`sampleKind` 区分）：list 给「内部管理」表格页、「历史编辑记录」以及小程序「待核验」列表（`verifyStatus=pending&sampleKind=…`，CR-20260924-10）用，POST 给首页新增、PUT 给历史编辑记录与表格页详情里的修改。行、详情、POST / PUT 入参都带 `passage`（代数，CR-20260924-10）。list 参数：`keyword`（内部编号 / 来源单位）、`donorName`（精确）、`verifyStatus`、`sampleKind`、`sort=recent`（给「历史编辑记录」用：有改动按最后修改时间、否则按创建时间倒序；**默认是中心全部内部人员的记录**，CR-20260918-07）、`mine=true`（「只看我提交的」开关打开时才带：`create_by = 当前用户 OR update_by = 当前用户`，**与 `sort` 无关**，带不带 `sort=recent` 都生效，#191 / CR-20260923-09）。list 的行 = 该工作表的全部模板列 + `hint`（走同一个列表查询，行上也带 `relation`，小程序不读它，CR-20260924-11），历史编辑记录场景下另带 `handlerName`（经手人）与 `mine`（是否本人，前端显示「我」）；详情多两个键 `updateByName`、`updateTime`（「最后修改」）。**PUT**：有效样本除身份字段外全部可改（`submitNo / submitSource / submitterId / sampleKind` 不可改；`sampleKind` 与库里不一致 → 400，issue #105 —— `sample_kind` 是这条记录的类目身份，由创建入口定下）；待核验、无效的外部样本 → 400（核验与改判只走 `/lqg/sample/{id}/verify`）。**补丁语义**（CR-20260923-09）：请求体里没出现的键不改；出现且值为 `null` 或空串 = 清空；清必填项 → `code=400` 并写明是哪一项；给样本本类别没有的字段传非空值 → `code=400`（如「组织样本没有这些字段，不能修改：类器官类型」；给组织样本传非空 `passage` 同样 400「……：代数」）。必填 = 来源单位、组织类型（组织样本）或类器官类型（类器官）、内部编号、收样日期 |
| `GET /mp/ext/sample/list` | 「历史编辑记录」的样本两个页签用。参数 `sampleKind?`、`verifyStatus?`、`onlyMine?`；行 = `ExtSampleVo{id, submitNo, sampleKind, donorNameMasked, tissueType, organoidType, verifyStatus, invalidReason, submitterName, mine, editable, createTime, updateTime}`（`invalidReason` 在 D2 补：外部必须能看到本人无效样本的原因才能改后重提） |
| `GET /mp/ext/sample/{id}` | `ExtSampleDetailVo{id, submitNo, sampleKind, sourceUnitName, donorName, gender, age, hospitalNo, tissueType, organoidType, passage, hasPathology, remark, verifyStatus, invalidReason, submitterName, mine, editable, createTime, embeds:[ExtEmbedVo], docs:[ExtDocVo]}`。`passage`（代数）是外部自己填的送检段字段、不是内部字段（CR-20260924-10；外部重提是整段替换，详情不带它的话改一次备注就会把代数洗空），组织样本上为 null。**没有** `operatorName / verifyBy / receiveDate…` 这些键；`internalNo` 默认也没有，只有系统参数 `lqg.ext.show-internal-no` 打开时才带上（CR-20260918-07，默认 false） |
| `POST /mp/ext/sample`、`PUT /mp/ext/sample/{id}` | 组织样本：只收送检段字段；PUT 仅本人的 pending / invalid，成功后回到 pending。必填 = 来源单位（`sourceUnitId` 或 `sourceUnitName`）、供体姓名、组织类型；违规 `code=400`，msg 以「提交的内容不符合要求：」开头、一次列全。`sourceUnitId` 只能是提交人档案里绑定的单位（pending 或 verified），否则 400；不带 id 且单位名与绑定单位同名时，后端自动挂上该单位 id；重提时单位名没改就沿用已挂的单位（CR-20260923-09） |
| `POST /mp/ext/organoid`、`PUT /mp/ext/organoid/{id}` | 外部填类器官收样记录（CR-20260917-05）：`ExtOrganoidSubmitBo{sourceUnitId?, sourceUnitName, organoidType, passage?, remark?}` → `sample_kind='organoid'`、`submit_source='external'`、`verify_status='pending'`；PUT 规则同组织样本（整段替换，`passage` 不带 = 清空）。入参里夹带的 `internalNo / receiveDate / verifyStatus` 等不生效。必填 = 来源单位（`sourceUnitId` 或 `sourceUnitName`）、类器官类型；`passage`（代数，CR-20260924-10）选填，规则同 `PUT /lqg/sample`，格式错 `code=400`「提交的内容不符合要求：代数请填 P 加数字，如 P3」且库里不写；违规 `code=400`，msg 以「提交的内容不符合要求：」开头；来源单位 id 的规则同组织样本（CR-20260923-09） |
| `GET /mp/dict/hints?type=tissue\|organoid\|sample` | 联想词（三个 `lqg_hint_*` 字典） |
| `GET /mp/int/export/{sheet}` | 小程序表格页「导出 Excel」（REQ-SYS-017）。`sheet` ∈ `tissue \| organoid \| embed \| cryo`；查询参数 = 对应工作表 list 的筛选参数；响应 = xlsx 文件流（`Content-Disposition` 带中文文件名，如 `样本记录信息表-20260918.xlsx`）。表头列序与 `/lqg/sample/export/*`、`/lqg/embed/export`、`/lqg/cryo/batch/export` **同一个导出视图**（`organoid` 同样是模板 7 列 + 「类器官类型」后插入「代数」；`cryo` 同样收 `emptiedOnly`，CR-20260924-10）。小程序用 `wx.downloadFile({url, header})` 带上 `Authorization` 与 `clientid` 取临时文件，再 `openDocument` / `shareFileMessage`。外部角色 403 |

## EMBED / CRYO

| 方法 路径 | 说明 |
|---|---|
| `GET /lqg/embed/list`、`GET/POST/PUT/DELETE /lqg/embed`、`POST /lqg/embed/export` | 行内带 `internalNo`（读时带出）、`markers:[{markerName, expression}]`、`submitSource`、`verifyStatus`、`submitNo`（所挂样本的送检单号）、`sampleKind`（所挂样本的类别 `tissue`\|`organoid`，读时带出，工作台「样本编号」点回样本时据此决定回哪一页，CR-20260924-11；只进内部 VO，`ExtEmbedVo` 没有）；`stainTypes` 是数组。list 加筛选 `verifyStatus`、`submitSource`、`sampleId`（从样本页的「石蜡包埋 / 冻存」一列带过来），默认待核验置顶。内部新增直接 `valid` 且石蜡块编号必填。`PUT` 为**补丁语义**（CR-20260923-09）：请求体里没出现的键不改；出现且值为 `null` 或空串 = 清空；清必填项 → `code=400` 并写明是哪一项；石蜡包埋的必填 = 所挂样本、石蜡块编号 |
| `PUT /lqg/embed/{id}/verify` | 核验外部提交的石蜡包埋送样（CR-20260917-05）。调用方：工作台核验抽屉，**以及小程序内部人员的核验页**（CR-20260924-10；权限串 `lqg:embed:verify` 同一个，外部 403；小程序的 `fill` 只带改过的键）：`{action:"valid"\|"invalid", paraffinBlockNo, reason, fill?}`；判有效：石蜡块编号必填且唯一、所挂样本必须已 `valid`；判无效：`reason` 必填。合法转移同样本主档。`fill` 可选（CR-20260923-09，V02b）：键为 `PUT /lqg/embed` 去掉 `id`、`sampleId`、`paraffinBlockNo` 后的 15 项（样本类型、类器官来源类型、七个工序时间、包埋人、染色、其他染色名称、操作人、备注、`markers`），补丁语义与校验同 `PUT /lqg/embed`，与核验结论**同一事务**保存；判有效 15 项都收；判无效只收 `sampleType`、`organoidSourceType`，带其余 13 项回 `code=400` 并写明是哪几项；`fill` 里带 `sampleId` 或 `paraffinBlockNo` 回 400（所挂样本核验时不能换，编号用顶层键）；任何一条被拒库里都不变；不带 `fill`（或 `fill:null`）= 补填段一个字不动 |
| `GET /mp/int/embed/list`、`GET/POST/PUT /mp/int/embed` | 同形状；list 给「内部管理」表格页、小程序「待核验」列表（`verifyStatus=pending`，CR-20260924-10）与「历史编辑记录」（`sort=recent`，默认全中心；`mine=true` 只在「只看我提交的」打开时带，= `create_by = 当前用户 OR update_by = 当前用户`，与 `sort` 无关，行带 `handlerName` / `mine`，CR-20260918-07 / CR-20260923-09）用。`PUT` 同 `/lqg/embed` 的补丁语义与必填 |
| `GET /mp/ext/embed/list`、`GET /mp/ext/embed/{id}`、`POST /mp/ext/embed`、`PUT /mp/ext/embed/{id}` | 外部填石蜡包埋送样记录（CR-20260917-05）。list 参数 `onlyMine?`、`verifyStatus?`，行 = 可见样本下的全部石蜡包埋记录（含外部提交还没核验的）；`ExtEmbedSubmitBo{sampleId, sampleType?, organoidSourceType?}`：`sampleId` 必须是**本人**提交、未判无效的样本；PUT 仅本人的 pending / invalid，成功后回到 pending。POST 的 `sampleId` 必填（缺了 400）。**不可见、不存在、已软删一律 `code=404`**，msg 固定为「石蜡包埋记录不存在」（按记录 id 访问）或「样本不存在」（按 `sampleId` 挂样），读口与写口同一句，不能靠 400 / 404 或提示语区分他人记录是否存在；可见但不是本人的、所挂样本已判无效的 → `code=400`（CR-20260923-09） |
| `ExtEmbedVo` | `{id, sampleId, submitNo, paraffinBlockNo, sampleType, organoidSourceType, tissueReceiveTime, tissueProcessTime, agaroseEmbedTime, dehydrateTime, agaroseSendTime, paraffinEmbedTime, sectionTime, sectioned, stainTypes, stainOther, markers, verifyStatus, invalidReason, submitterName, mine, editable, embedBy, operatorName}`——`embedBy`（包埋人）与 `operatorName`（操作人）2026-09-18 起对外可见（CR-20260918-07，甲方「应该是可以看得到操作人、包埋人，看不到冻存信息」）；**仍然没有** `remark / verifyBy / internalNo`（内部编号按 `lqg.ext.show-internal-no` 开关） |
| `GET /lqg/cryo/batch/list` | 参数：`internalNo, cryoName, sampleId, location(minus80\|ln2), overdueOnly, emptiedOnly, freezeTimeBegin/End`（`sampleId` = 从样本页的「石蜡包埋 / 冻存」一列带过来，只看这个样本的批次）。`emptiedOnly=true` = 只看已取空（剩余 ≤ 0，与超期判定第 ③ 条同一份剩余算式；与 `overdueOnly` 同带 = 空集，CR-20260924-10）。行内带 `remainingQty, location, overdue, overdueDays, emptied（剩余 ≤ 0）, frozenDays（冻存了几天，服务器日期算）, internalNo, sampleKind`（`sampleKind` = 所挂样本的类别，读时带出，CR-20260924-11）；响应另带 `data.tabCounts` 或顶层 `tabCounts:{all, overdue, ln2, emptied}`（键序固定，四个数都是整表口径、不随筛选收窄；`emptied` 2026-09-24 追加） |
| `GET/POST/PUT/DELETE /lqg/cryo/batch`、`POST /lqg/cryo/batch/export` | 导出同样收 `emptiedOnly`（与列表同一份筛选），表头不变（模板 9 列 + 代数、当前剩余/支）。`PUT` 为**补丁语义**（CR-20260923-09）：请求体里没出现的键不改；出现且值为 `null` 或空串 = 清空；清必填项 → `code=400` 并写明是哪一项；冻存的必填 = 所挂样本、样品名称、代数、冻存时间、冻存数量、是否暂存 -80；`toLn2Time:null` = 撤销登记错的转液氮（位置读时算，批次回到 -80；液氮位置要一并清掉就同时传 `ln2Location:null`）。`initQty` 可改：锁批次行后从新的初始支数出发按 `flow_time` 正序逐笔累加未删流水，**任一步 < 0 → 400** 且库里不变（CR-20260917-04）；详情带 `updateByName`、`updateTime` |
| `POST /lqg/cryo/batch/{id}/flow` | `{flowType:"take"\|"add"\|"adjust", qty, purpose, operatorName?, flowTime?}`；take / add 的 `qty` 是正整数，adjust 的 `qty` 带符号。调用方：工作台，以及小程序内部人员的批次详情弹层（只发 take / add，CR-20260924-10；权限串 `lqg:cryo:flow` 同一个，外部 403）。取走让剩余从有变成 0 时两端前端先确认，接口本身不拦 |
| `PUT /lqg/cryo/batch/{id}/flow/{flowId}`、`DELETE /lqg/cryo/batch/{id}/flow/{flowId}` | 改 / 删一笔登记（REQ-CRYO-008）。PUT `{qty, purpose, operatorName, flowTime}`（补丁语义同上）：`purpose` 传空串 = 清空用途（取走、补入）；盘点调整清空用途 → 400（调整必须写原因）；`flowType` 不可改（传了不同的 → 400），`qty` 按原类型解释（take / add 正整数，adjust 带符号不为 0），`fromLocation` 保持登记时的值。DELETE = 软删。两者都锁批次行后逐笔重算，**任一步剩余 < 0 → 400** 且库里不变，msg 指出是哪一笔：「这样改会让 MM-dd HH:mm 那一笔（-3 支）之后的剩余变成 -1 支，没有保存」/「删掉这一笔会让 …」（CR-20260924-10；改初始支数被拒仍是「已取走 N 支，冻存数量不能少于 N」）；`flowId` 不属于这个批次 → 404。小程序批次详情弹层对取走 / 补入两种登记调这两个口，盘点调整不给入口（后端不区分调用端） |
| `PUT /lqg/cryo/batch/{id}/to-ln2` | `{toLn2Time, ln2Location}`。工作台与小程序内部人员的批次详情弹层共用（CR-20260924-10） |
| `GET /lqg/cryo/batch/{id}/flows` | 未删流水，时间倒序；每行带 `balanceAfter`（按时间正序累计算出的操作后剩余，不落库）、`edited`（改过为 true）、`updateByName`、`updateTime` |
| `GET /lqg/cryo/overdue` | 超期批次清单（同一判定函数） |
| `/mp/int/cryo/**` | 2026-09-17 晚起（CR-20260917-05）只有：`GET …/batch/list`（历史编辑记录用 `sort=recent`，默认全中心；`mine=true` 只在「只看我提交的」打开时带，与 `sort` 无关，行带 `handlerName` / `mine`，CR-20260918-07）、`GET/POST/PUT …/batch`（首页新增、历史编辑记录里修改，改 `initQty` 同样逐笔校验；`PUT` 的补丁语义、必填与 `toLn2Time:null` 同 `/lqg/cryo/batch`）、`GET …/batch/{id}/flows`（只读）。**没有**取走 / 补入 / 转液氮 / 改删登记的接口（都在 `/lqg/cryo/**`），没有 export（导出走 `/mp/int/export/cryo`），没有删除批次。list 同样收 `emptiedOnly`、带 `tabCounts.emptied`（CR-20260924-10）。小程序内部人员做取走 / 补入 / 转液氮 / 改删登记时**直接调 `/lqg/cryo/batch/{id}/flow`、`…/flow/{flowId}`、`…/to-ln2`、`…/flows`**（`lqg_internal` 带这些权限串，外部 403）——写口仍只有 `/lqg/cryo/**` 一份，`/mp/int/cryo/**` 上这些路径仍然 404 |

## QC / DOC

| 方法 路径 | 说明 |
|---|---|
| `GET /lqg/qc/list` | 「质控文档」板块列表（CR-20260930-11）：已核验有效的样本 + 三份质控表各自的 `sampleQcStatus / organoidQcStatus / scoreStatus`（`null` = 没打开过 / `draft` / `published`）、`publishedCount`、`progress`（`none / doing / done`）、`totalScore`、`lastUpdateTime`。筛选 `keyword / sampleKind / progress / receiveBegin / receiveEnd`，分页 `pageNum / pageSize`；权限 `lqg:qc:query` |
| `GET /lqg/qc/{sampleId}` | 三份文档 + 图片 + 附件 + 从样本主档带出的只读字段；首次访问建三份空草稿。图片的 `url / previewUrl`、附件的 `url` 按这一行 `sys_oss.service` 对应的存储配置给：**私有桶是 10 分钟签名链接**，公有桶原样给 `sys_oss.url`；签不出来（配置缺失）照旧给原值并记日志（CR-20260924-11，修掉私有桶下原样给 `sys_oss.url`、缩略图 / 放大图 / 附件全部 403）。别缓存这些地址：工作台在取回满 5 分钟后，于缩略图加载失败、点图放大、打开附件之前重取一次 |
| `PUT /lqg/qc/{sampleId}/sample-qc`、`/organoid-qc`、`/score` | 保存草稿；score 只收四个 level，分值由后端回填 |
| `POST/DELETE /lqg/qc/{sampleId}/{docType}/image`、`…/attachment` | `{slot, ossId}` / `{ossId, fileName}` |
| `POST /lqg/qc/{sampleId}/{docType}/publish`、`POST …/unpublish` | 完成并同步 / 手动撤回。完成、撤回、已完成的改回草稿，都在**同一个请求里**让该样本的合并件失效（置回 pending，旧产物不再下发）；后台按新的成员重出，一份不剩则撤下合并件（CR-20260923-09） |
| `POST /lqg/doc/{sampleId}/{docKind}/render?audience=[&force=true]` | 触发渲染。内容指纹没变**且 docx / pdf / 页面图三种产物齐全**时直接返回 `cached=true`；`force=true` = 「重新生成」，一定重出（`cached=false`）。返回 `data:{sampleId, docKind, audience, status, errorMsg, cached, missingImageCount, missingImages, …}`：`missingImageCount` 缺图张数，`missingImages` 缺图明细（人话，逐张写明哪一栏第几张、为什么取不到）。取图失败：**外部版有任一张图取不到即 `status=failed`**，`errorMsg` 写明缺哪几张，外部不可见；内部版照出（`done`），缺图记进 `missingImageCount` / `missingImages`（CR-20260923-09） |
| `GET /lqg/doc/{sampleId}/{docKind}/pages?audience=` | `data:{status, errorMsg, pages:[{pageNo, url}], images:[{url, previewUrl}], attachments:[{fileName, fileSize, url}], missingImageCount, missingImages, internalNoShown, outdated}`。`status` ∈ `none \| pending \| done \| failed`：这一份（这一版）**从没生成过 → `code=200`、`status=none`、`pages` 为空**，是正常的初始态，不是 `code=400`，也不触发渲染（CR-20260924-11：原来回 400，工作台切页签连弹「这份文档还没生成」）。只有这一版产物齐全才是 `done`；在途或待重出为 `pending`（不给旧页面图）；`attachments` 含细胞活率附件；合并件只取已完成的成员（CR-20260923-09）。`internalNoShown`（boolean）= 这一版「内部编号」一格印没印；`outdated`（boolean）= 这一版按旧设置 / 旧模板出、后台正在重出（`done` 时照给旧页面图）；外部版印了内部编号而开关 `lqg.ext.show-internal-no` 已关时 `status=pending`、pages 为空（CR-20260924-10） |
| `GET /lqg/doc/{sampleId}/{docKind}/download?format=docx\|pdf&audience=` | `data:{url, fileName}`（短时签名链接）。只下发**这一版齐全**的产物：还没生成（msg「这份文档还没生成，请先在质控文档页点「预览」」；与 pages 的 `status=none` 不同，下载时没有产物要说清楚）、正在生成（含合并件正在按最新成员重出）、上次失败都回 `code=400`，msg 说明是哪一种（CR-20260923-09）；外部版印了内部编号而开关已关的同样不下发（CR-20260924-10）。外部版「内部编号」一格随开关：开印、关留空；内部版一直印；切换后已完成的文档在后台按新设置重出，文件名规则不变。Word 里「细胞活率测定」一格嵌着附件本身（OLE Package，内外部版、合并件都嵌），文件大小随附件增加（单个附件大于 20MB 不嵌，只印文件名）；PDF 里是图标 + 文件名（CR-20260924-11） |
| `GET /mp/int/doc/list`、`GET /mp/int/doc/{sampleId}/{docKind}/pages`、`…/download` | 内部：全部已完成文档，`audience` 固定 internal；pages 的形状同 `GET /lqg/doc/{sampleId}/{docKind}/pages`，download 的规则同上；两者都先过可用性判断（未完成或不可用 `code=404`），所以这里不会出现 `status=none`（CR-20260924-11）。list 可带 `sampleId`（预览页顶部切换条取这个样本的几份）。列表上的「下载」「合并下载」与预览页底部调的是同一个 download（CR-20260917-04） |
| `GET /mp/ext/doc/list`、`GET /mp/ext/doc/{sampleId}/{docKind}/pages`、`…/download` | 外部：过 ExtScope，`audience` 固定 external，只含已完成且外部版渲染成功的（有任一张图取不到即渲染失败，不对外；三种 docKind 都给：sample_qc / organoid_qc / organoid_score，外加 merged）；`ExtDocVo{sampleId, submitNo, donorNameMasked, docKind, publishedTime, totalScore?}`；list 同样可带 `sampleId`。pages = `ExtDocPagesVo{docKind, status, pages:[{pageNo, url}], images:[{url, previewUrl}], attachments:[{fileName, fileSize, url}]}`：按 `UI:mp.doc.preview` 给原图与附件（活率附件排最前），走同一个隔离咽喉，签发链接前逐个核对对象只属于本样本的外部版（误挂的内部产物不下发）；不带 `errorMsg` 等内部字段（CR-20260923-09），也不带工作台用的 `internalNoShown` / `outdated`。download 给的外部版 Word 里同样嵌着细胞活率附件（图标 + 文件名，大于 20MB 不嵌），PDF 与页面图里是图标 + 文件名（CR-20260924-11）。外部版「内部编号」一格随系统参数 `lqg.ext.show-internal-no`：开着印、关着留空；**印了内部编号而开关此刻已关的那一版在 list / pages / download 一律按不存在处理**（list 不列、pages / download `code=404`），后台按新设置重出后恢复（CR-20260924-10） |
| `GET /lqg/home/todo`、`GET /lqg/home/recent` | todo：`data:{pendingSamples, pendingTissue, pendingOrganoid, pendingEmbeds, cryoOverdue, pendingExtUsers, renderFailed}`（恰好七个键）——`pendingTissue` / `pendingOrganoid` = 待核验的组织样本 / 类器官样本数（工作台首页两张卡与两个菜单红点，CR-20260924-10 拆页时加），`pendingSamples` 保留且恒等于两者之和；`pendingEmbeds` = 待核验的石蜡包埋送样数（CR-20260917-05）；`renderFailed` = 渲染异常数 = 渲染失败加内部版缺图，按（样本, docKind, audience）计（CR-20260923-09）。**小程序内部首页「待处理」也读它**（只读 `pendingSamples / pendingEmbeds / cryoOverdue` 三个键，CR-20260924-10），外部 403 不变；小程序没有另外的计数接口。recent：`data:[{submitTime, submitNo, sampleKind, sourceUnitName, submitSource, verifyStatus}]`（最多 10 行、不含软删；`sampleKind` 用来按类别进对应那一页，CR-20260924-10） |
| `GET /lqg/home/render-issues` | 首页「渲染失败」卡片点开的异常清单，与 `renderFailed` 同一口径：`data:[{sampleId, internalNo, submitNo, sourceUnitName, docKind, audience, issue:"failed"\|"missing_images", errorMsg, missingImageCount, missingImages, time}]`；点一行进该样本质控页，可一键重新生成（`force=true`）。走内部角色闸（`lqg_admin` / `lqg_internal` / `superadmin`，OR），外部 `code=403`（CR-20260923-09） |

`docType` ∈ `sample-qc | organoid-qc | score`（路径里用连字符，对应字典 `sample_qc | organoid_qc | organoid_score`）；`docKind` 另多一个 `merged`。

## OCR

| 方法 路径 | 说明 |
|---|---|
| `POST /mp/ocr/recognize` | multipart `file`；`data:{rawLines:[…], fields:{donorName?, gender?, age?, hospitalNo?, tissueType?, sourceUnitName?}}`——解析不出的键**不出现**。测试环境 `StubOcrProvider`：请求头 `X-Ocr-Stub-Case: <case 名前两位>` 决定返回 `doc/verify/fixtures/ocr-cases.json` 里哪一组 rawLines；**缺请求头时返回 01 号样例**（仅 dev / test；prod 下桩不存在，配成 stub 拒绝启动）。桩读的是模块内 `ruoyi-lqg/src/main/resources/ocr-cases.json`，与 doc 原件逐字节一致（`FixtureCopiesSyncTest` 比对）（CR-20260923-09） |
| `GET /lqg/ocr/status` | `data:{provider, paidEnabled}` |
