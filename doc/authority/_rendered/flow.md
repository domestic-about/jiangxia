# 业务流权威图

> 本文件由 `authority_lint.py render` 从 `doc/authority/*.yaml` 自动生成。**别手改**——改权威改 YAML，手改这里下次 render 就被覆盖。

## 小程序登录与内外部身份判定

**锚 id**：`FLOW:F-AUTH-01`（ticket 的 blueprint_refs 写这个）
**对应需求**：REQ-AUTH-001, REQ-AUTH-003, REQ-AUTH-004, REQ-AUTH-008, REQ-AUTH-015, REQ-SYS-001

只有一个登录入口：微信登录拿 openid + 手机号快速验证组件拿手机号。后端只认微信返回的手机号：
该手机号对应的账号带 lqg_internal 角色 → 内部；否则 → 外部（没有账号就自动建一个外部账号，不设准入）。
身份由后端判定后随 token 一起返回，前端据此渲染首页入口；前端传来的任何「我是内部」字段一律不认。

| 步 | 谁 | 做什么 | 写什么 | 产出 |
|---|---|---|---|---|
| `FLOW:F-AUTH-01.step1` | 任何人(mp) | wx.login 拿 code → 后端 code2session 换 openid（请求必须带 clientId + grantType=xcx）；未勾选用户协议与隐私政策不能继续 |  | openid（此时还没有账号概念） |
| `FLOW:F-AUTH-01.step2` | 任何人(mp) | 点「手机号快速验证」按钮 → 拿到动态 code → 后端向微信换取真实手机号；用户拒绝授权则停在登录页，不进系统 |  | 微信背书的手机号 |
| `FLOW:F-AUTH-01.step3` | 系统 | 按手机号查 sys_user：查到 → 把 openid 绑到该账号（绑定表没有这个 openid 才插）；查不到 → 新建外部账号（角色 lqg_external）+ 空的外部档案（bind_status=unbound）。 同一手机号永远只对应一个账号。 | FIELD:t_lqg_wx_bind.openid, FIELD:t_lqg_wx_bind.unionid, FIELD:t_lqg_wx_bind.phone, FIELD:t_lqg_wx_bind.user_id, FIELD:t_lqg_ext_profile.bind_status | 账号 + 绑定行；新外部用户另有一行外部档案 |
| `FLOW:F-AUTH-01.step4` | 系统 | 签发 token，返回 identity（internal / external）、姓名、手机号掩码、（外部）单位组别与核验状态；首页按 identity 渲染入口 | FIELD:t_lqg_wx_bind.last_login_time | 内部见四张表入口；外部见三张（没有 -80 冻存记录，Kevin 2026-09-17 晚定，CR-20260917-05） |

## 内部人员按手机号授权与撤销

**锚 id**：`FLOW:F-AUTH-02`（ticket 的 blueprint_refs 写这个）
**对应需求**：REQ-AUTH-002, REQ-AUTH-003, REQ-AUTH-008, REQ-AUTH-010

实验室管理员在工作台按手机号给人授权。关键是「升级而不是新建」：这个手机号如果已经以外部身份登录过，
就把原账号的角色改成内部；没登录过才预建账号，等他首次小程序登录时按手机号自动对上。
撤销授权 = 角色改回外部 + 踢下线；他以前以内部身份录的样本仍是内部样本。

| 步 | 谁 | 做什么 | 写什么 | 产出 |
|---|---|---|---|---|
| `FLOW:F-AUTH-02.step1` | 实验室管理员(admin) | 在「内部人员授权」页填手机号、姓名、角色（内部人员 / 实验室管理员）、工作台初始密码 |  | 一次授权请求 |
| `FLOW:F-AUTH-02.step2` | 系统 | 按手机号查 sys_user：已存在（多半是外部账号）→ 原地改角色为内部、设置工作台密码；不存在 → 新建内部账号（暂无 openid 绑定） |  | 同一手机号仍然只有一个账号；/mp/me 下次请求即显示内部身份，访问内部接口要重新登录一次（角色在登录时写入会话，CR-20260923-09） |
| `FLOW:F-AUTH-02.step3` | 实验室管理员(admin) | 撤销 → 角色改回 lqg_external、强制该账号全部 token 失效；若没有外部档案则补建一行 unbound | FIELD:t_lqg_ext_profile.bind_status | 此人再登录即为外部；工作台登录被拒 |
| `FLOW:F-AUTH-02.step4` | 系统 | 工作台账号密码登录时校验角色：不含 lqg_internal / lqg_admin 的账号登录失败（后端拒绝，不是登录成功后没菜单） |  | 外部账号即使知道口令也进不了工作台 |

## 单位组别维护与外部组别核验

**锚 id**：`FLOW:F-AUTH-03`（ticket 的 blueprint_refs 写这个）
**对应需求**：REQ-AUTH-007, REQ-AUTH-011, REQ-SYS-002

「同组互看」靠这条流成立。实验室在工作台维护「来源单位 → 组别」；外部用户自己选单位和组别（列表里没有可以自填），
选完是待核验；实验室核验通过后才参与同组互看。核验前、被驳回、或改了单位组别之后，都只能看到本人的样本。
⚠️ 本流按合同附件二第 1 条的乙方建议写，甲方尚未最终确认（REQ-AUTH-011 clarify）。

| 步 | 谁 | 做什么 | 写什么 | 产出 |
|---|---|---|---|---|
| `FLOW:F-AUTH-03.step1` | 内部人员(admin) | 增删改来源单位、单位下的组别；停用的单位 / 组别不再出现在小程序选择器里，但已绑定的人不受影响 | FIELD:t_lqg_source_unit.unit_name, FIELD:t_lqg_source_unit.unit_status, FIELD:t_lqg_source_unit.remark, FIELD:t_lqg_unit_group.group_name, FIELD:t_lqg_unit_group.group_status, FIELD:t_lqg_unit_group.remark | 启用中的单位—组别树 |
| `FLOW:F-AUTH-03.step2` | 外部人员(mp) | 首次提交样本前（或在「我的」里）填姓名、选单位与组别；列表里没有就自填单位名 / 组别名 | FIELD:t_lqg_ext_profile.real_name, FIELD:t_lqg_ext_profile.unit_id, FIELD:t_lqg_ext_profile.group_id, FIELD:t_lqg_ext_profile.unit_name_input, FIELD:t_lqg_ext_profile.group_name_input, FIELD:t_lqg_ext_profile.bind_status | 外部档案 bind_status=pending；此时只看得到本人的样本 |
| `FLOW:F-AUTH-03.step3` | 内部人员(admin) | 在「外部用户」页核验：通过（自填的单位 / 组别此时选择「新建」或「归并到已有」）或驳回（写原因） | FIELD:t_lqg_ext_profile.bind_status, FIELD:t_lqg_ext_profile.unit_id, FIELD:t_lqg_ext_profile.group_id, FIELD:t_lqg_ext_profile.verified_by, FIELD:t_lqg_ext_profile.verified_time, FIELD:t_lqg_ext_profile.reject_reason | verified → 开始与同组互看；rejected → 仍只看本人的 |
| `FLOW:F-AUTH-03.step4` | 外部人员(mp) | 在「我的 → 单位与组别」修改 → 回到 pending，同组互看立即失效，等重新核验 | FIELD:t_lqg_ext_profile.bind_status | bind_status=pending |

## 冻存建批、超期提醒与转液氮

**锚 id**：`FLOW:F-CRYO-01`（ticket 的 blueprint_refs 写这个）
**对应需求**：REQ-CRYO-001, REQ-CRYO-002, REQ-CRYO-003, REQ-CRYO-007, REQ-CRYO-008, REQ-CRYO-901, REQ-SAMPLE-008, REQ-SAMPLE-013

-80 冻存模板的一行 = 一个冻存批次，必须挂到一个样本上并记代数。暂存在 -80 的批次满 14 天还没转液氮就进提醒清单，
登记了转液氮时间和位置就出清单。提醒只在系统内（工作台首页待办、冻存列表置顶高亮——工作台与小程序「我的 → 内部管理」的冻存表格页）。
小程序首页顶部的「-80 超期」数字 2026-09-17 晚随首页数字一起去掉（CR-20260917-05）；2026-09-24 甲方要求内部人员的小程序首页也提醒（第 17 行），
内部首页「待处理」里重新有「-80 超期未转液氮」一项，与工作台首页同一个数（CR-20260924-10）。
-80 是暂存、液氮是长期存放，一般从液氮取走；转液氮时间与液氮储存位置在各端的列表、详情、填写页都看得见（甲方 2026-09-24 第 22 行）。

| 步 | 谁 | 做什么 | 写什么 | 产出 |
|---|---|---|---|---|
| `FLOW:F-CRYO-01.step1` | 内部人员(mp/admin) | 选样本 → 冻存样品名称（用「内部编号-」预填，手改）、代数（P+数字）、冻存时间、数量、密度、是否暂存 -80、冻存人；选「否」时必须填液氮储存位置 | FIELD:t_lqg_cryo_batch.sample_id, FIELD:t_lqg_cryo_batch.cryo_name, FIELD:t_lqg_cryo_batch.passage, FIELD:t_lqg_cryo_batch.freeze_time, FIELD:t_lqg_cryo_batch.init_qty, FIELD:t_lqg_cryo_batch.density, FIELD:t_lqg_cryo_batch.in_minus80, FIELD:t_lqg_cryo_batch.frozen_by, FIELD:t_lqg_cryo_batch.ln2_location, FIELD:t_lqg_cryo_batch.remark | 批次建立，剩余 = 初始支数；同一样本可有多条不同代数的批次。之后批次的每一项都可以改（含初始支数，校验见 FLOW:F-CRYO-02.step5） |
| `FLOW:F-CRYO-01.step2` | 系统 | 超期 = in_minus80='Y' 且 to_ln2_time 为空 且 剩余支数 > 0 且 当前日期 − freeze_time ≥ 阈值天数（默认 14，第 14 天当天即算）。读时计算，不落标志位； 所以登记转液氮（to_ln2_time 落值）、或支数被取空之后，这一条立刻退出超期清单、提醒消失（CR-20260918-07 甲方问「转移后还会有提示吗」）。 阈值天数存在系统参数 `lqg.cryo.overdue-days`（默认 14），内部人员在工作台系统管理里改，改完下一次读时即生效（CR-20260918-07 甲方问「这儿是有什么设置吗」）； 每天 08:00 的定时任务只负责把当日超期数写日志 / 供首页缓存，不是判定依据。 |  | 超期批次清单（含已超天数） |
| `FLOW:F-CRYO-01.step3` | 系统 | 工作台首页待办卡片与内部人员小程序首页「待处理」显示超期批次数并可直达（小程序直达 ?sheet=cryo&tab=overdue）； 冻存列表超期行置顶并标红「已超 N 天」（工作台，以及小程序「我的 → 内部管理」冻存表格页的超期页签）。 另有「已取空」提示（CR-20260924-10，甲方第 22 行「支数取空的要提示」）：剩余 ≤ 0 的批次在两端列表与批次详情标「已取空」， 两端冻存列表多一个「已取空 N」页签（数字来自后端 tabCounts.emptied，筛选 emptiedOnly=true）；取空的批次永不算超期 |  | 内部人员一进系统就看到哪些该转液氮、哪些已经取空 |
| `FLOW:F-CRYO-01.step4` | 内部人员(mp/admin) | 填转移至液氮时间（不得早于冻存时间）与液氮储存位置（必填）→ 保存。工作台在冻存列表行操作里做；2026-09-24 起小程序也能做（CR-20260924-10）： 内部管理冻存表点一批的详情弹层，还在 -80、没转过的才有「转液氮」，调同一个 PUT /lqg/cryo/batch/{id}/to-ln2 | FIELD:t_lqg_cryo_batch.to_ln2_time, FIELD:t_lqg_cryo_batch.ln2_location | 该批次当前位置 = 液氮，出提醒清单 |
| `FLOW:F-CRYO-01.step5` | 内部人员(admin) | 导出「-80 冻存」9 列，表头与列序和模板逐字一致；其后追加两列「代数」「当前剩余/支」 |  | xlsx 文件 |

## 冻存出入库流水与剩余支数

**锚 id**：`FLOW:F-CRYO-02`（ticket 的 blueprint_refs 写这个）
**对应需求**：REQ-CRYO-004, REQ-CRYO-005, REQ-CRYO-006, REQ-CRYO-008

取走、补入、盘点调整都记流水。剩余支数不是一个能直接改的数字：剩余 = 初始支数 + 该批次全部**未删**流水的 delta 之和。
取走数大于剩余被拒。登记填错了可以改、可以删（2026-09-17 甲方看设计稿后要求「提交后可以修改」，CR-20260917-04），
改删后剩余当场重算，且按时间先后逐笔算下来每一步都不能为负。「谁在什么时候从哪拿了几支」仍然可追溯，改过的登记记着最后修改人与时间。
2026-09-17 晚 Kevin 定工作台功能最全、小程序只查看（CR-20260917-05）；2026-09-24 甲方要求「小程序和工作台界面都能操作」（第 20 行，CR-20260924-10）：
取走、补入、改删取走 / 补入登记与转液氮两端都能做，小程序在内部管理冻存表的批次详情弹层里做，调工作台同一组 /lqg/cryo/** 写接口、规则一字不差；盘点调整只在工作台。

| 步 | 谁 | 做什么 | 写什么 | 产出 |
|---|---|---|---|---|
| `FLOW:F-CRYO-02.step1` | 内部人员(mp/admin) | 选批次 → 填取走支数（正整数，≤ 剩余）、用途、经手人（默认当前登录人）、时间（默认现在）；from_location 由批次当前位置自动带出（已登记转液氮 = ln2，否则 minus80）， 界面上只读显示「从液氮取走」或「从 -80℃ 取走」。这一笔让剩余从有变成恰好 0 时，提交前两端都多问一句「登记后这一批就取空了（剩 0 支），确定吗？」（CR-20260924-10） | FIELD:t_lqg_cryo_flow.batch_id, FIELD:t_lqg_cryo_flow.flow_type, FIELD:t_lqg_cryo_flow.delta, FIELD:t_lqg_cryo_flow.from_location, FIELD:t_lqg_cryo_flow.operator_name, FIELD:t_lqg_cryo_flow.flow_time, FIELD:t_lqg_cryo_flow.purpose | 流水一行（delta 为负），剩余相应减少；超取被拒且不产生流水 |
| `FLOW:F-CRYO-02.step2` | 内部人员(mp/admin) | 填补入支数（正整数）、原因 | FIELD:t_lqg_cryo_flow.delta, FIELD:t_lqg_cryo_flow.flow_type | 流水一行（delta 为正），剩余相应增加 |
| `FLOW:F-CRYO-02.step3` | 内部人员(admin) | 实盘与账面不符时填调整量（可正可负、不为 0）与原因（必填）；调整后剩余不得为负 | FIELD:t_lqg_cryo_flow.delta, FIELD:t_lqg_cryo_flow.purpose | 流水一行，原因留痕 |
| `FLOW:F-CRYO-02.step4` | 系统 | 剩余 = init_qty + SUM(未删流水的 delta)；写、改、删流水和改初始支数，都先对批次行加行锁再重算、再校验，防两人同时操作导致负数 |  | 任意时刻 剩余 ≥ 0 且 = 初始 + 未删流水累计 |
| `FLOW:F-CRYO-02.step5` | 内部人员(mp/admin；小程序在批次详情弹层里改删取走 / 补入，冻存数量在冻存记录的修改模式里改，校验相同) | 改一笔取走 / 补入的支数、用途、经手人、时间，或删掉这一笔（软删，删前二次确认）；改冻存记录的初始支数。保存前从初始支数出发、按 flow_time 正序逐笔累加， **任何一步小于 0 都拒绝**（只看最终剩余不够：会改出「当时只剩 2 支却取走了 3 支」的账），改删一笔被拒时提示指出是哪一笔： 「这样改（或：删掉这一笔）会让 MM-dd HH:mm 那一笔（±N 支）之后的剩余变成 -M 支，没有保存」（改初始支数被拒仍是「已取走 N 支，冻存数量不能少于 N」）。 登记类型不能改，要换类型就删掉重登；from_location 保持登记时的值。盘点调整类登记只在工作台改删：小程序不提供入口（写接口是同一份，后端不区分调用端，CR-20260924-10） | FIELD:t_lqg_cryo_flow.delta, FIELD:t_lqg_cryo_flow.purpose, FIELD:t_lqg_cryo_flow.operator_name, FIELD:t_lqg_cryo_flow.flow_time, FIELD:t_lqg_cryo_batch.init_qty | 登记与剩余当场更新并记最后修改人、时间；被拒时库里不变 |

## 文档渲染（Word → PDF → 页面图片）

**锚 id**：`FLOW:F-DOC-01`（ticket 的 blueprint_refs 写这个）
**对应需求**：REQ-DOC-003, REQ-DOC-004, REQ-DOC-006, REQ-DOC-007, REQ-DOC-011, REQ-QC-002, REQ-QC-004, REQ-QC-008

一份 docx 模板是唯一的版式来源（ADR-0005）：poi-tl 渲染 Word → Gotenberg 转 PDF → PDFBox 出每页 PNG。
预览图、Word、PDF 同源，所以三者版式必然一致。每份文档出内部版和外部版；产物按内容指纹缓存，内容、模板或「合作单位可见内部编号」开关一变就重出。
渲染是异步排队的（并发 1），失败在工作台可见、可重试。
2026-09-24 甲方要求下载下来是他们模板的样子（第 24、26 行，CR-20260924-10）：三份模板逐格对照原件查缺补齐，模板版本升到 4，已完成的文档读到时自动按新模板重出。
同日 Kevin 本机验收（「网页工作台」第 4、5、7 行，CR-20260924-11）：格子行高按原件固定、长文字逐档缩字、填值格居中，细胞活率附件真嵌进 Word；
模板文件不变、版式规则变，模板版本升到 5，已完成的文档同样按「旧版照给、后台重出」自动更新。

| 步 | 谁 | 做什么 | 写什么 | 产出 |
|---|---|---|---|---|
| `FLOW:F-DOC-01.step1` | 系统 | content_hash = sha256(该文档全部字段 + 样本主档带出字段 + 图片与附件的 oss_id 序列（含嵌进 Word 的细胞活率附件，换附件必重出，CR-20260924-11） + 模板版本 + audience + 「内部编号」一格印没印)；与已有 done 记录一致则直接复用。 「印没印」：内部版恒印；外部版随系统参数 lqg.ext.show-internal-no（CR-20260924-10），只有样本质控表（及含它的合并件）有这一格，另两份的指纹不随开关变。 已有产物按旧设置或旧模板出的（给出去无妨：留空而开关已开、模板版本旧）照给，同时后台排队按新设置 / 新模板重出 | FIELD:t_lqg_doc_file.doc_kind, FIELD:t_lqg_doc_file.content_hash, FIELD:t_lqg_doc_file.template_version, FIELD:t_lqg_doc_file.render_status | 需要重出的置 pending 入队；不需要的零开销返回 |
| `FLOW:F-DOC-01.step2` | 系统 | poi-tl 按 docx 模板填充：文本、图片位（用预览图，等比放进格子，多张按图的比例排成一行或上下摞、之间留白缝；图片行不跨页断开）、评分表勾选与合计、页脚固定注释原样保留； 处理时间印到分钟；Word 保留模板原件字体（宋体 / Times New Roman）。 细胞活率测定一格嵌入附件本身（CR-20260924-11，按甲方模板原件的标注「嵌了一个文件附件，显示一个图标，双击就能打开」补齐，推翻此前「只印文件名、不做 OLE 嵌入」的默认口径）： Word 里是 OLE Package（文件图标 + 文件名），在 Word、WPS 里双击打开原文件；内部版、外部版、合并件都嵌，任意文件类型； PDF 与页面图里是图标 + 文件名（点不开，附件在预览页的附件栏里打开）；单个附件大于 20MB 不嵌，这一格印「文件名（大于 20MB，未嵌入，请在附件中查看）」； 存储里取不到附件时只印文件名并记日志，不计入缺图。 版式（CR-20260924-11）：行高按甲方原件固定；表格里的段落固定行距（字号 × 1.3）、不对齐文档网格（否则转 PDF 时一行字被吸成两格高）； 填值的格子放不下时从原字号起按 12 → 10.5 → 9 → 8 → 7.5 磅逐档缩小，取放得下的最大一档，缩到 7.5 磅仍放不下才让这一行长高，文字一律不截断； 所有填值的格子水平、垂直居中（标签格原本就居中）；图片位的排法不变。 「内部编号」一格：内部版一直印；外部版随系统参数 lqg.ext.show-internal-no——开着印、关着（默认）留空，其余与内部版相同（CR-20260924-10，甲方第 23 行；此前外部版一律留空）。 落 done 时与指纹同一次写下这一版印没印（show_internal_no）。开关切换后已完成的外部版在后台按新设置重出，不用重新「完成并同步」； 开关关着时印了内部编号的外部版（单份、合并、页面图、Word、PDF）在清单、预览、下载一律不给，重出完才恢复。 图片取不到时：外部版整份判 failed，error_msg 写明缺了哪几张，外部不可见，图补上后重新生成即恢复； 内部版照出，缺图的位置留空，缺图张数与明细记在 missing_image_count、missing_images，工作台质控页与首页可见（CR-20260923-09，裁定 #217） | FIELD:t_lqg_doc_file.oss_id, FIELD:t_lqg_doc_file.audience, FIELD:t_lqg_doc_file.file_format, FIELD:t_lqg_doc_file.render_status, FIELD:t_lqg_doc_file.error_msg, FIELD:t_lqg_doc_file.missing_image_count, FIELD:t_lqg_doc_file.missing_images, FIELD:t_lqg_doc_file.show_internal_no | docx 存 OSS 私有桶 |
| `FLOW:F-DOC-01.step3` | 系统 | 把 docx 的转换副本发给 Gotenberg 容器转 PDF：副本上把模板原件字体换成容器里的开源字体（宋体一类 → Noto Serif SC，其余 → Tinos；CR-20260924-10 起模板本身保留原件字体）， 并把嵌入的附件对象换成同一张图标图片（CR-20260924-11：转换服务不必解析 OLE、也不多传附件字节，PDF 里是图标 + 文件名）； 超时 60 秒；失败记 error_msg | FIELD:t_lqg_doc_file.render_status, FIELD:t_lqg_doc_file.error_msg | pdf 存 OSS；失败则该份标 failed |
| `FLOW:F-DOC-01.step4` | 系统 | PDFBox 按 150 DPI 把 PDF 每页渲染成 PNG，page_no 从 1 起 | FIELD:t_lqg_doc_file.page_no, FIELD:t_lqg_doc_file.rendered_time | 每页一张 PNG 存 OSS |
| `FLOW:F-DOC-01.step5` | 系统 | 该样本 published 的文档按「样本质控表 → 类器官质控表 → 类器官质量评分表」顺序拼接 docx（每份自成一节、分节符为下一页，保留各自纸张；CR-20260924-10 修掉了少断一页与末尾空白页）→ 整体再转 PDF； 只有一份 published 时合并文件 = 那一份；任何一份的状态或内容变化都让 merged 失效；合并件的成员用同一次读到的内部编号开关值。 失效的意思：完成、撤回、已完成改回草稿都在同一个请求里把合并件置回待渲染，旧产物不再下发；后台按新的成员重出，一份不剩则撤下合并件（CR-20260923-09） |  | doc_kind=merged 的 docx 与 pdf |
| `FLOW:F-DOC-01.step6` | 内部人员(admin) | 工作台首页待办显示渲染异常数（渲染失败加内部版缺图）；点开是异常清单，点一行进该样本的质控文档页；在清单或质控文档页点「重新生成」会强制重出（CR-20260923-09） |  | failed 或缺图 → pending → done |

## 小程序查看与下载质控文档

**锚 id**：`FLOW:F-DOC-02`（ticket 的 blueprint_refs 写这个）
**对应需求**：REQ-DOC-001, REQ-DOC-002, REQ-DOC-005, REQ-DOC-006, REQ-DOC-007, REQ-DOC-008, REQ-DOC-009, REQ-DOC-010, REQ-QC-011

小程序第二个页签。内部看全部已完成文档；外部只看本人与同组样本的（过 FLOW:F-EXT-01，且只取外部版）。
列表按完成时间倒序；每份文档旁可直接下载、每个样本可合并下载（2026-09-17 甲方要求，CR-20260917-04），
也可以点开预览（页面图片 + 原图 + 附件）再在底部选格式与单份 / 合并后下载。合作单位看得到三份：样本质控表、类器官质控表、类器官质量评分表。
小程序没有「存到手机文件夹」的接口：下载 = 打开文档（右上角菜单可保存或用其他应用打开）或发送到微信聊天。

| 步 | 谁 | 做什么 | 写什么 | 产出 |
|---|---|---|---|---|
| `FLOW:F-DOC-02.step1` | 内部/外部人员(mp) | 按样本分组展示已完成的文档（每组最多三份 + 完成时间，每份一行带「下载」）；组底「合并预览」「合并下载」（≥2 份时）； 点「下载」弹出格式（PDF / Word）+ 打开 / 发送到微信；可按时间范围、文档类型筛选；外部的组标题用送检单号 + 供体姓名，不出现内部编号 |  | 列表；外部只见可见样本集合内的、外部版已渲染成功的文档；外部版「内部编号」一格与开关此刻的值冲突（印了而开关已关）的暂不列出，按新设置重出后恢复（CR-20260924-10） |
| `FLOW:F-DOC-02.step2` | 内部/外部人员(mp) | 顶部切换条在该样本已完成的几份（+ 合并）之间切换；逐页显示页面 PNG；点任一页全屏双指缩放；页面下方列出文档里的样本图片缩略图（点开看原图）与附件（点开用微信内置查看器打开） |  | 与 Word 同版式的预览；显微照片可看原图细节 |
| `FLOW:F-DOC-02.step3` | 系统 | 请求参数 = 样本、单份或合并、docx 或 pdf；后端校验可见性后签发 OSS 短时签名链接（10 分钟）；外部只能拿 audience=external 的文件； 外部版印了内部编号而开关此刻已关的不签发，按「不存在」处理（CR-20260924-10） |  | 一次性可用的下载地址；猜 id 或转发过期链接都拿不到文件 |
| `FLOW:F-DOC-02.step4` | 内部/外部人员(mp) | wx.downloadFile 到临时目录 →「打开」走 wx.openDocument(showMenu=true)，「发送到微信」走 wx.shareFileMessage；文件名 = 文档名 + 送检单号 |  | 用户在微信里拿到 Word 或 PDF |

## 石蜡包埋送样记录

**锚 id**：`FLOW:F-EMBED-01`（ticket 的 blueprint_refs 写这个）
**对应需求**：REQ-EMBED-001, REQ-EMBED-002, REQ-EMBED-003, REQ-EMBED-004, REQ-AUTH-006, REQ-AUTH-013, REQ-AUTH-015, REQ-SAMPLE-013

一个样本可以有多个石蜡块。记录是陆续补填的：建块时只有石蜡块编号和样本，之后各工序做完一步填一步时间，
最后选染色和 marker 表达。石蜡块编号是实验室自己取的名字，也是对外展示包埋情况时用的标识。
2026-09-17 晚 Kevin 定外部也能填（CR-20260917-05）：合作单位挂自己送检过的样本提交送样，实验室核验有效时给石蜡块编号再往下补（step6、step7）。

| 步 | 谁 | 做什么 | 写什么 | 产出 |
|---|---|---|---|---|
| `FLOW:F-EMBED-01.step1` | 内部人员(mp/admin) | 从有效样本里选一个（按内部编号搜）→ 填石蜡块编号（全库唯一）；样本类型、类器官来源类型手填；组织收样时间、组织处理时间从样本带出可改 | FIELD:t_lqg_embed.sample_id, FIELD:t_lqg_embed.paraffin_block_no, FIELD:t_lqg_embed.sample_type, FIELD:t_lqg_embed.organoid_source_type, FIELD:t_lqg_embed.tissue_receive_time, FIELD:t_lqg_embed.tissue_process_time | 一条包埋记录，工序时间全空 |
| `FLOW:F-EMBED-01.step2` | 内部人员(mp/admin) | 琼脂糖包埋样本时间、包埋人、脱水时间、琼脂糖包埋样本送样时间、石蜡包埋时间、切片时间，做完一步填一步，全部允许留空 | FIELD:t_lqg_embed.agarose_embed_time, FIELD:t_lqg_embed.embed_by, FIELD:t_lqg_embed.dehydrate_time, FIELD:t_lqg_embed.agarose_send_time, FIELD:t_lqg_embed.paraffin_embed_time, FIELD:t_lqg_embed.section_time | 记录逐步完整 |
| `FLOW:F-EMBED-01.step3` | 内部人员(mp/admin) | 染色五个按钮多选（HE / IF / IHC / 其他 / 无染色），「无染色」与其余互斥，选「其他」必须写具体名称； marker 表达可加多行，每行 = 名称（可空）+ 三按钮单选（阴性 / 弱表达 / 强表达） | FIELD:t_lqg_embed.stain_types, FIELD:t_lqg_embed.stain_other, FIELD:t_lqg_embed_marker.marker_name, FIELD:t_lqg_embed_marker.expression, FIELD:t_lqg_embed.operator_name, FIELD:t_lqg_embed.remark | 染色与 marker 落库；非法组合（NONE 与别的并存、OTHER 无名称）被拒 |
| `FLOW:F-EMBED-01.step4` | 系统 | 外部在自己（或同组）样本的详情里看到该样本的石蜡包埋记录：石蜡块编号、各工序时间、染色、marker、操作人与包埋人（CR-20260918-07 按甲方意见放开）、核验状态（外部提交还没核验的显示「待核验」、无效的带原因）； 不含核验人、冻存信息；内部编号按系统参数 lqg.ext.show-internal-no 决定 |  | ExtEmbedVo 列表（过 FLOW:F-EXT-01） |
| `FLOW:F-EMBED-01.step5` | 内部人员(admin) | 导出「石蜡包埋送样记录」16 列，表头与列序和模板逐字一致；「样本编号」列 = 内部编号；染色导出为中文标签用顿号连接；marker 拼成「名称：表达」分号连接 |  | xlsx 文件 |
| `FLOW:F-EMBED-01.step6` | 外部人员(mp) | 首页点「石蜡包埋送样记录」进填写页：从本人送检过、没被判无效的样本里选一个（按送检单号），填样本类型、类器官来源类型后提交； 待核验或无效时本人可改后重提（回到待核验），核验有效后只读 | FIELD:t_lqg_embed.sample_id, FIELD:t_lqg_embed.submit_source, FIELD:t_lqg_embed.submitter_id, FIELD:t_lqg_embed.verify_status, FIELD:t_lqg_embed.sample_type, FIELD:t_lqg_embed.organoid_source_type | 一条 submit_source=external、verify_status=pending、石蜡块编号为空的记录 |
| `FLOW:F-EMBED-01.step7` | 内部人员(mp/admin) | 工作台「石蜡包埋」页待核验的行置顶浅黄 → 核验抽屉；2026-09-24 起小程序内部人员也能核验（CR-20260924-10，甲方第 20 行）： 首页「待处理」或内部管理表格点待核验的那一条进核验页，调同一个 PUT /lqg/embed/{id}/verify、同一份规则，fill 只带改过的键； 所挂样本还没核验有效时「判为有效并保存」置灰，并给「先去核验样本」入口。 判有效必须填石蜡块编号（全库唯一）且所挂样本已核验有效； 抽屉里补填的工序时间、包埋人、染色、marker、操作人、备注以及样本类型、类器官来源类型的更正，与核验结论同一事务保存，校验同石蜡包埋修改，被拒则库里不变。 判无效必须写原因，只保存原因与样本类型、类器官来源类型的更正；实验室补填项不保存，界面在原因弹窗里明说（CR-20260923-09）。 合法转移同样本主档：pending→valid、pending→invalid、invalid→pending（外部重提）、invalid→valid；内部录入的直接 valid | FIELD:t_lqg_embed.verify_status, FIELD:t_lqg_embed.verify_by, FIELD:t_lqg_embed.verify_time, FIELD:t_lqg_embed.invalid_reason, FIELD:t_lqg_embed.paraffin_block_no, FIELD:t_lqg_embed.sample_type, FIELD:t_lqg_embed.organoid_source_type, FIELD:t_lqg_embed.tissue_receive_time, FIELD:t_lqg_embed.tissue_process_time, FIELD:t_lqg_embed.agarose_embed_time, FIELD:t_lqg_embed.embed_by, FIELD:t_lqg_embed.dehydrate_time, FIELD:t_lqg_embed.agarose_send_time, FIELD:t_lqg_embed.paraffin_embed_time, FIELD:t_lqg_embed.section_time, FIELD:t_lqg_embed.stain_types, FIELD:t_lqg_embed.stain_other, FIELD:t_lqg_embed.operator_name, FIELD:t_lqg_embed.remark, FIELD:t_lqg_embed_marker.marker_name, FIELD:t_lqg_embed_marker.expression | valid（有石蜡块编号）或 invalid（有原因）；工作台首页与内部人员小程序首页「待核验石蜡包埋送样」数字随之变化 |

## 外部可见范围解析（隔离咽喉）

**锚 id**：`FLOW:F-EXT-01`（ticket 的 blueprint_refs 写这个）
**对应需求**：REQ-AUTH-005, REQ-AUTH-006, REQ-AUTH-007, REQ-AUTH-009, REQ-AUTH-012, REQ-AUTH-013, REQ-AUTH-015, REQ-DOC-009

外部人员的一切读写都过这一条（ADR-0004）。它回答三个问题：看哪些样本、看哪些字段、拿哪些文件。
入口只有 /mp/ext/** 一组；范围只在 ExtScopeService 一处算；字段只从 Ext*Vo 一种出口出去。
验收时只需要验这一处，而不是去证明「所有接口都没漏」。
2026-09-17 晚 Kevin 定外部可填三张表（CR-20260917-05）：样本记录信息表、类器官收样记录、石蜡包埋送样记录，写入口仍然只在这一组里。

| 步 | 谁 | 做什么 | 写什么 | 产出 |
|---|---|---|---|---|
| `FLOW:F-EXT-01.step1` | 系统 | visibleSampleIds(userId) = 本人提交的样本 ∪（本人 bind_status=verified 时）同 group_id 且 bind_status=verified 的其他外部用户提交的样本。 只含 del_flag='0' 的样本；不按来源单位名称匹配，只按提交人。 |  | 一个样本 id 集合（可能为空） |
| `FLOW:F-EXT-01.step2` | 系统 | 样本详情、包埋情况、文档列表、预览图、下载链接，凡带 sampleId 的请求先 assertVisible(sampleId)；不可见一律按「不存在」返回（不泄露存在性） |  | 越权请求得到 404 语义的业务码，响应体不含任何样本字段 |
| `FLOW:F-EXT-01.step3` | 系统 | 返回值只能是 Ext*Vo。白名单：送检单号、送检段字段、核验状态与无效原因、提交人姓名与「是否本人」标记、石蜡块编号与包埋各工序时间 / 染色 / marker、 石蜡包埋的操作人与包埋人（CR-20260918-07 起对外可见）、已完成文档的元信息（含已完成评分表的合计分）。 不含：冻存人、冻存的一切、核验人、其他提交人的手机号；内部编号默认也不含，只有系统参数 lqg.ext.show-internal-no=true 时才填（CR-20260918-07 起该参数是若依 sys_config，运行时可改）。 类器官样本的代数（passage）是外部自己填的送检段字段，属于白名单（CR-20260924-10）。 同一个开关也决定外部版质控文档里「内部编号」一格印不印（CR-20260924-10，甲方 2026-09-24 第 23 行；判据在 FLOW:F-DOC-01.step2、F-DOC-02.step3）。 |  | 外部拿到的 JSON 里没有上述字段的键 |
| `FLOW:F-EXT-01.step4` | 系统 | 外部的新增 / 修改只作用于 submitter_id = 本人 且 verify_status ∈ {pending, invalid} 的记录，且只能写送检段字段；同组的可看不可改。 三种记录：组织样本（样本记录信息表）、类器官样本（类器官收样记录：来源单位、类器官类型、代数、备注；代数 CR-20260924-10 加）、石蜡包埋送样（只能挂本人送检过、未判无效的样本； 样本类型、类器官来源类型）。每种各一个外部专用入参对象，只含上述字段 |  | 越界写入被拒且库里不产生任何变化 |

## 小程序：首页填写、历史编辑记录、内部管理

**锚 id**：`FLOW:F-MP-01`（ticket 的 blueprint_refs 写这个）
**对应需求**：REQ-SYS-001, REQ-SYS-016, REQ-SYS-017, REQ-SYS-018, REQ-SYS-019, REQ-SYS-020, REQ-AUTH-015, REQ-SAMPLE-016

Kevin 2026-09-17 晚定的小程序结构。首页点表就是填写；每个人在「我的 → 历史编辑记录」找回自己填过的记录；
内部人员另有「我的 → 内部管理」看全部记录、筛选、导出，点一行进详情可修改。网页工作台功能仍然最全。
2026-09-24 甲方要求「小程序和工作台界面都能操作」（CR-20260924-10，第 17、20 行）：内部人员首页多一块「待处理」，
小程序里也能核验（样本记录、类器官收样、石蜡包埋送样）和做冻存取用登记（取走、补入、转液氮、改删登记），规则与工作台同一份；改判仍只在工作台。

| 步 | 谁 | 做什么 | 写什么 | 产出 |
|---|---|---|---|---|
| `FLOW:F-MP-01.step1` | 内部/外部人员(mp) | 首页 = 问候行 +（仅内部）「待处理」+ 宫格。内部四张（样本记录信息表 / 类器官收样记录 / 石蜡包埋送样记录 / -80 冻存记录），外部三张（没有 -80 冻存记录）； 点哪张都进该表的填写页新增一条。外部首页没有任何数字、没有最近记录。 内部首页在宫格上方有「待处理」三项：待核验样本、待核验石蜡包埋送样、-80 超期未转液氮，数字与工作台首页同源（GET /lqg/home/todo 的 pendingSamples / pendingEmbeds / cryoOverdue）， 每次显示页面都重取；点前两项进「待核验」列表（FLOW:F-MP-01.step4），点第三项进内部管理冻存表的超期页签（CR-20260924-10，推翻 CR-20260917-05 的「首页不放数字」） |  | 新记录（外部的是待核验，内部的直接有效）；内部人员一进小程序就看到待办数 |
| `FLOW:F-MP-01.step2` | 内部/外部人员(mp) | 「我的 → 历史编辑记录」按表分页签（内部四张、外部三张），最近的在前。外部：本人提交的 + 同组同事提交的（组别已核验）， 有「只看我提交的」开关；点本人待核验 / 无效的 → 改后重提，其余只读查看。 内部：中心全部内部人员经手过的记录（取数 sort=recent，不带 mine；每行显示经手人，本人显示「我」），顶部「只看我提交的」开关（默认关，打开才带 mine=true， 收窄到 create_by = 本人 或 update_by = 本人）；点进去是该表填写页的修改模式，别人录的也能改（冻存记录的冻存数量改完逐笔校验）； 外部送来还没核验的由后端挡成只读（CR-20260918-07 起的口径，见 code/miniapp/src/pages/history/sources.ts 头注释） |  | 找回并修改记录；修改记最后修改人与时间 |
| `FLOW:F-MP-01.step3` | 内部人员(mp) | 「我的 → 内部管理」四个入口进表格页：四张表顶上切换、按表筛选、第一列冻结左右滑看全部列、底部「导出 Excel」；表格本身没有新增、没有行内编辑。 点一行：样本记录 / 类器官收样 / 石蜡包埋 → 该表填写页的只读详情，右上角「修改」切到修改模式（CR-20260918-07）； 合作单位送来、待核验的那一条 → 直接进核验页（FLOW:F-MP-01.step4）； -80 冻存 → 批次详情弹层，可取走、补入、转液氮、改删取走 / 补入登记（规则与工作台同一份，FLOW:F-CRYO-01.step4、F-CRYO-02.step1 / step2 / step5；盘点调整只在工作台）。 冻存表支持 ?sheet=cryo&tab=overdue|ln2|emptied|all 直达页签（CR-20260924-10） |  | 全部记录的表格视图、按筛选导出的 xlsx；从这里进入修改、核验与冻存登记 |
| `FLOW:F-MP-01.step4` | 内部人员(mp) | 2026-09-24 新增（CR-20260924-10，甲方第 20 行「要求小程序和工作台界面都能操作」）。入口：首页「待处理」、内部管理表格里点待核验的那一条。 「待核验」列表三个页签（样本记录 / 类器官收样 / 石蜡包埋），数据 = /mp/int/sample/list?verifyStatus=pending&sampleKind=… 与 /mp/int/embed/list?verifyStatus=pending， 每个页签的数取各自的 total；每行 = 送检单号、来源单位、关键字段摘要、提交人与提交时间；空态「没有待核验的记录」。 核验页调工作台同一个接口（PUT /lqg/sample/{id}/verify、PUT /lqg/embed/{id}/verify），必填与校验同 FLOW:F-SAMPLE-01.step3、F-EMBED-01.step7： 样本送检信息一项没改就不带 submitSegment，改了任意一项就带整段；石蜡包埋的 fill 只带改过的键；判无效要写原因（合作单位看得到）。 后端业务错误原样弹框提示、停在本页；成功后回列表刷新，首页数字随之变化。记录已不是待核验时只提示「已经核验过了；要改判请到网页工作台」，不给按钮 |  | 与工作台核验同样的结论落库；外部人员调这两个接口一律 403 |

## 拍照识别预填

**锚 id**：`FLOW:F-OCR-01`（ticket 的 blueprint_refs 写这个）
**对应需求**：REQ-OCR-001, REQ-OCR-002, REQ-OCR-003, REQ-OCR-004, REQ-OCR-005

样本记录信息表填写页顶部一个识别入口：拍管壁、袋子、送检单，或从相册选截图。识别只做预填——
结果填进空着的表单项并标「请核对」，填写人核对修改后照常提交；识别错了也进不了台账。
用哪家识别服务等实测后定（REQ-OCR-004），架构上是可插拔的适配层（ADR-0007）：按次收费的通道默认关。

| 步 | 谁 | 做什么 | 写什么 | 产出 |
|---|---|---|---|---|
| `FLOW:F-OCR-01.step1` | 内部/外部人员(mp) | wx.chooseMedia（相机 / 相册，单张）→ 前端压缩到长边 ≤ 2000px → 上传 |  | 一张待识别图片 |
| `FLOW:F-OCR-01.step2` | 系统 | 统一接口收图 → 限流（每人每分钟 ≤ 6 次）→ 交给当前配置的 OcrProvider → 得到原始文本行；图片只在内存 / 临时文件里，识别完即删，不进 OSS、不落库 |  | 原始文本行；provider 不可用或超时返回明确错误，前端提示手填 |
| `FLOW:F-OCR-01.step3` | 系统 | 规则解析出候选：供体姓名、性别、年龄、住院号、组织类型、来源单位（关键词 + 正则 + 单位名称表匹配）；每个候选带置信标记；解析不出的字段不返回 |  | 候选字段 + 原始文本（让用户能对照着手填） |
| `FLOW:F-OCR-01.step4` | 内部/外部人员(mp) | 只往空着的表单项里填，已手填的不覆盖；预填项加「识别 · 请核对」标记，用户改动后标记消失；原始文本可展开查看 |  | 表单被部分预填，未提交 |
| `FLOW:F-OCR-01.step5` | 内部/外部人员(mp) | 走正常提交（FLOW:F-SAMPLE-01.step1 / F-SAMPLE-02.step1），提交的内容以表单当前值为准 |  | 入库的是用户核对后的内容 |

## 部署、备份与全量导出

**锚 id**：`FLOW:F-OPS-01`（ticket 的 blueprint_refs 写这个）
**对应需求**：REQ-SYS-003, REQ-SYS-004, REQ-SYS-005, REQ-SYS-006, REQ-SYS-008, REQ-SYS-010

我方代购的一台阿里云服务器上用 docker compose 跑全部服务：PostgreSQL、Redis、后端、Nginx（工作台静态资源 + 反代 + HTTPS）、Gotenberg。
文件在 OSS 私有桶。数据库每日备份到 OSS 并做过真实恢复；甲方要数据时一条脚本出全量包。

| 步 | 谁 | 做什么 | 写什么 | 产出 |
|---|---|---|---|---|
| `FLOW:F-OPS-01.step1` | 乙方运维 | 一份 compose 文件 + 一份环境变量文件（密钥不进仓库）；数据库与 Redis 端口不映射到公网；Nginx 终结 HTTPS；后端以 prod 配置启动 |  | 工作台域名可访问；小程序合法域名（request / uploadFile / downloadFile）指向同一域名 |
| `FLOW:F-OPS-01.step2` | 系统 | 每天 03:00 pg_dump（自定义格式）→ 上传 OSS 私有桶 backup/ 前缀 → 保留 30 天；失败通知 Kevin |  | 每日一份可恢复的备份 |
| `FLOW:F-OPS-01.step3` | 乙方运维 | 上线前用最新备份在一个空库上做一次真实 pg_restore，核对各业务表行数与线上一致 |  | 备份被证明可恢复 |
| `FLOW:F-OPS-01.step4` | 乙方运维 | 脚本产出：数据库 dump + 业务表各导一份 CSV（加密列解密后）+ OSS 文件清单与打包；附校验清单（行数、文件数、sha256） |  | 可直接交给甲方的压缩包 |
| `FLOW:F-OPS-01.step5` | 系统 | 各容器 healthcheck + restart 策略；磁盘使用率 > 80%、备份失败、容器反复重启时通知 Kevin |  | 出问题时 Kevin 先于甲方知道 |

## 小程序提审上线

**锚 id**：`FLOW:F-OPS-02`（ticket 的 blueprint_refs 写这个）
**对应需求**：REQ-SYS-009, REQ-SYS-007, REQ-AUTH-001

小程序以甲方主体注册认证（甲方办，非开发前置）。我方备齐提审材料：服务类目、隐私保护指引（相机、相册、手机号）、
用户协议与隐私政策页；先发体验版给甲方试用，再提审发布。生产环境 mock 登录必须是关的。

| 步 | 谁 | 做什么 | 写什么 | 产出 |
|---|---|---|---|---|
| `FLOW:F-OPS-02.step1` | 乙方 | 配置合法域名；填隐私保护指引（收集手机号、使用相机与相册的目的）；小程序内置《用户协议》《隐私政策》页，登录前须勾选 |  | 满足微信审核要求的材料与页面 |
| `FLOW:F-OPS-02.step2` | 乙方 | 本地构建并上传（不走 CI 构建，见栈包 gotchas §6.5）→ 设为体验版 → 甲方对接人加为体验成员 |  | 合同第三条要求的「可试用版本」 |
| `FLOW:F-OPS-02.step3` | 乙方 | 提交审核 → 通过后发布；审核被打回按意见改后重提 |  | 小程序正式上线 |
| `FLOW:F-OPS-02.step4` | 系统 | prod 配置下 mock 登录开关为 false；若被误开，应用拒绝启动（ADR-0008） |  | 生产地址上 mock 登录请求被拒 |

## 质控文档编辑、预览、完成并同步

**锚 id**：`FLOW:F-QC-01`（ticket 的 blueprint_refs 写这个）
**对应需求**：REQ-QC-001, REQ-QC-002, REQ-QC-003, REQ-QC-005, REQ-QC-006, REQ-QC-007, REQ-QC-009, REQ-QC-010, REQ-QC-011

方案流程图的后半段：网页编辑质控文档 → [预览无误？] → 结果同步送检方；有误重新编辑。
三份文档（样本质控表、类器官质控表、类器官质量评分表）每个样本各一份，只在网页工作台编辑。
每份独立有「草稿 / 已完成」两态：完成才对外可见；完成后再改内容就回到草稿、对外撤下。

| 步 | 谁 | 做什么 | 写什么 | 产出 |
|---|---|---|---|---|
| `FLOW:F-QC-01.step1` | 内部人员(admin) | 在「样本记录信息表」或「类器官收样记录」页选一个有效样本 → 行操作「质控文档」→ 打开编辑页（三个页签）；编辑页的「返回」按样本类别回到对应那一页（CR-20260924-10 拆页）； 样本主档带出的字段（来源单位、患者姓名、性别、收样时间、处理时间、操作人、内部编号）只读展示。 编辑页是一个标签装一个样本（CR-20260924-11）：从任何入口带了另一个 sampleId 进来就整页按新样本重新加载，标签标题带内部编号； 当前样本有未保存的改动时换样本先提示；地址里没带样本显示去两张样本表的引导，不显示上一次的样本（见 UI:admin.qc.editor） |  | 首次进入时为该样本建三份空草稿（样本质控表的三段默认文字按模板原文预填） |
| `FLOW:F-QC-01.step2` | 内部人员(admin) | 填患者编号、取样部位、取样方式、临床诊断/既往治疗、收样描述、三行情况描述；三个图片位各传 1-3 张；上传细胞活率测定附件；可挂通用附件 | FIELD:t_lqg_qc_sample.patient_no, FIELD:t_lqg_qc_sample.sampling_site, FIELD:t_lqg_qc_sample.sampling_method, FIELD:t_lqg_qc_sample.clinical_diagnosis, FIELD:t_lqg_qc_sample.receive_desc, FIELD:t_lqg_qc_sample.viability_oss_id, FIELD:t_lqg_qc_sample.viability_file_name, FIELD:t_lqg_qc_sample.orig_desc, FIELD:t_lqg_qc_sample.observe_desc, FIELD:t_lqg_qc_sample.pretreat_desc, FIELD:t_lqg_doc_image.doc_type, FIELD:t_lqg_doc_image.doc_id, FIELD:t_lqg_doc_image.slot, FIELD:t_lqg_doc_image.oss_id, FIELD:t_lqg_doc_image.preview_oss_id, FIELD:t_lqg_doc_attachment.doc_type, FIELD:t_lqg_doc_attachment.doc_id, FIELD:t_lqg_doc_attachment.oss_id, FIELD:t_lqg_doc_attachment.file_name, FIELD:t_lqg_doc_attachment.file_size | 草稿保存；TIFF 等格式自动生成 JPEG 预览图 |
| `FLOW:F-QC-01.step3` | 内部人员(admin) | 样本观察情况图片位 1-3 张；形成类器官时间、生长状态、类器官生长情况、预计筛药、反馈时间五栏文本 | FIELD:t_lqg_qc_organoid.formed_time, FIELD:t_lqg_qc_organoid.growth_state, FIELD:t_lqg_qc_organoid.growth_desc, FIELD:t_lqg_qc_organoid.planned_drug_screen, FIELD:t_lqg_qc_organoid.feedback_time | 草稿保存 |
| `FLOW:F-QC-01.step4` | 内部人员(admin) | 四个变量各选一档；后端按字典 remark 回填各项分值快照并算合计（前端传来的分值忽略）；任一项未选则合计为空 | FIELD:t_lqg_qc_score.pre_culture_level, FIELD:t_lqg_qc_score.culture_days_level, FIELD:t_lqg_qc_score.organoid_count_level, FIELD:t_lqg_qc_score.diameter_level, FIELD:t_lqg_qc_score.pre_culture_score, FIELD:t_lqg_qc_score.culture_days_score, FIELD:t_lqg_qc_score.organoid_count_score, FIELD:t_lqg_qc_score.diameter_score, FIELD:t_lqg_qc_score.total_score | 合计 = 四项分值之和；不产出质量等级结论 |
| `FLOW:F-QC-01.step5` | 内部人员(admin) | 点「预览」→ 触发内部版渲染（FLOW:F-DOC-01）→ 弹窗显示页面图片，与将要下载的 Word / PDF 同源 |  | 内部确认版式与内容；有误关掉弹窗继续编辑 |
| `FLOW:F-QC-01.step6` | 内部人员(admin) | 点「完成并同步」→ 该份文档 doc_status=published、记完成人与时间 → 触发内部版与外部版渲染；外部版渲染成功后该文档才出现在送检方的小程序文档页 | FIELD:t_lqg_qc_sample.doc_status, FIELD:t_lqg_qc_sample.published_time, FIELD:t_lqg_qc_sample.published_by, FIELD:t_lqg_qc_organoid.doc_status, FIELD:t_lqg_qc_organoid.published_time, FIELD:t_lqg_qc_organoid.published_by, FIELD:t_lqg_qc_score.doc_status, FIELD:t_lqg_qc_score.published_time, FIELD:t_lqg_qc_score.published_by | 送检方（本人 + 同组）可见 |
| `FLOW:F-QC-01.step7` | 系统 | 对 published 文档保存任何内容改动（含增删图片、附件）→ doc_status 回到 draft、published_time 清空 → 对外立即不可见，旧渲染产物因指纹变化失效；合法转移只有 draft→published 与 published→draft |  | 送检方看不到半成品；重新「完成并同步」后再次可见 |

## 外部送检到实验室核验

**锚 id**：`FLOW:F-SAMPLE-01`（ticket 的 blueprint_refs 写这个）
**对应需求**：REQ-SAMPLE-001, REQ-SAMPLE-005, REQ-SAMPLE-006, REQ-SAMPLE-008, REQ-SAMPLE-014, REQ-SAMPLE-901, REQ-AUTH-004, REQ-AUTH-009, REQ-AUTH-015

方案流程图的前半段：合作单位小程序送检 → [送检信息有效？] → 内部编号收样；无效需重新提交。
外部提交直接写样本主档，工作台「样本记录信息表」「类器官收样记录」两页立刻能看到（没有「同步」动作；2026-09-24 起原「样本总表」按类别拆成这两页，数据仍是一张表，CR-20260924-10）。
实验室核验（工作台，或 2026-09-24 起小程序内部人员）：有效就补收样段并给内部编号，无效就写原因；外部看到无效后可以改了重提。一旦有效，外部只读。

| 步 | 谁 | 做什么 | 写什么 | 产出 |
|---|---|---|---|---|
| `FLOW:F-SAMPLE-01.step1` | 外部人员(mp) | 首页点「样本记录信息表」进填写页：填送检段（来源单位、供体姓名、性别、年龄、住院号、组织类型、有无病理、备注），可先拍照识别预填；提交。 2026-09-17 晚起外部也能点「类器官收样记录」：填来源单位、类器官类型、代数（选填，形如 P3；2026-09-24 加，CR-20260924-10）、备注后提交（sample_kind=organoid，同样待核验，CR-20260917-05） | FIELD:t_lqg_sample.submit_no, FIELD:t_lqg_sample.sample_kind, FIELD:t_lqg_sample.submit_source, FIELD:t_lqg_sample.submitter_id, FIELD:t_lqg_sample.source_unit_id, FIELD:t_lqg_sample.source_unit_name, FIELD:t_lqg_sample.species, FIELD:t_lqg_sample.donor_name, FIELD:t_lqg_sample.gender, FIELD:t_lqg_sample.age, FIELD:t_lqg_sample.hospital_no, FIELD:t_lqg_sample.tissue_type, FIELD:t_lqg_sample.organoid_type, FIELD:t_lqg_sample.passage, FIELD:t_lqg_sample.has_pathology, FIELD:t_lqg_sample.remark | 样本 verify_status=pending、submit_source=external、sample_kind=tissue 或 organoid，生成送检单号；收样段全空 |
| `FLOW:F-SAMPLE-01.step2` | 系统 | 无动作——工作台「样本记录信息表」（组织样本）与「类器官收样记录」（类器官）两页查的就是样本主档，按类别各取一类（CR-20260924-10 拆页）； 待核验的行有醒目标记；工作台首页对应的那张待核验卡（pendingTissue / pendingOrganoid）与侧边菜单红点 +1， 内部人员小程序首页「待处理」的待核验样本（pendingSamples = 两者之和）同一个数 +1 |  | 内部人员在对应那一页的「待核验」筛选下看到该行 |
| `FLOW:F-SAMPLE-01.step3` | 内部人员(mp/admin) | 判有效：必须同时填收样日期与内部编号（内部编号全库唯一），可一并填有无固定、处理时间、质控表、细胞活率报告、操作人。 判无效：必须写原因。 核验时可一并修改送检段（类器官的送检段含代数，CR-20260924-10），与核验结论同一次保存，校验规则与样本修改相同，校验不过则库里不变；判无效时送检段照存、收样段不落库； 来源单位可改选正式单位，把只填了单位名的外部样本归口（CR-20260923-09）。 2026-09-24 起小程序也能核验（CR-20260924-10，甲方第 20 行）：内部人员从首页「待处理」或内部管理表格点待核验的那一条进核验页， 调同一个 PUT /lqg/sample/{id}/verify、同一份规则；送检段是整段替换，所以小程序里送检信息一项没改就不带 submitSegment，改了就带整段。改判仍只在工作台。 | FIELD:t_lqg_sample.verify_status, FIELD:t_lqg_sample.verify_by, FIELD:t_lqg_sample.verify_time, FIELD:t_lqg_sample.invalid_reason, FIELD:t_lqg_sample.receive_date, FIELD:t_lqg_sample.internal_no, FIELD:t_lqg_sample.is_fixed, FIELD:t_lqg_sample.process_time, FIELD:t_lqg_sample.has_qc_sheet, FIELD:t_lqg_sample.has_viability_report, FIELD:t_lqg_sample.operator_name, FIELD:t_lqg_sample.source_unit_id, FIELD:t_lqg_sample.source_unit_name, FIELD:t_lqg_sample.species, FIELD:t_lqg_sample.donor_name, FIELD:t_lqg_sample.gender, FIELD:t_lqg_sample.age, FIELD:t_lqg_sample.hospital_no, FIELD:t_lqg_sample.tissue_type, FIELD:t_lqg_sample.organoid_type, FIELD:t_lqg_sample.passage, FIELD:t_lqg_sample.has_pathology, FIELD:t_lqg_sample.remark | verify_status=valid（带内部编号）或 invalid（带原因） |
| `FLOW:F-SAMPLE-01.step4` | 外部人员(mp) | 在「我的 → 历史编辑记录」看到无效及原因（2026-09-17 晚起取代「我的送检」，CR-20260917-05）→ 修改送检段（类器官含代数，CR-20260924-10）→ 重新提交 | FIELD:t_lqg_sample.verify_status, FIELD:t_lqg_sample.invalid_reason, FIELD:t_lqg_sample.passage | verify_status 回到 pending，无效原因清空 |
| `FLOW:F-SAMPLE-01.step5` | 系统 | 合法转移只有：pending→valid、pending→invalid、invalid→pending（外部重提）、invalid→valid（内部直接改判）、valid→invalid（内部误判纠正，须写原因，且该样本名下没有包埋 / 冻存 / 质控文档时才允许）。 外部对 valid 样本的任何写入被拒；外部不能自己把状态改成 valid。 |  | 非法转移返回业务错误且库内不变 |

## 内部收样录入、样本表查询与导出

**锚 id**：`FLOW:F-SAMPLE-02`（ticket 的 blueprint_refs 写这个）
**对应需求**：REQ-SAMPLE-001, REQ-SAMPLE-002, REQ-SAMPLE-003, REQ-SAMPLE-004, REQ-SAMPLE-007, REQ-SAMPLE-009, REQ-SAMPLE-010, REQ-SAMPLE-011, REQ-SAMPLE-012, REQ-SAMPLE-013, REQ-SAMPLE-015, REQ-SAMPLE-016, REQ-SYS-016, REQ-SYS-017, REQ-SYS-019

内部人员自己收的样（组织样本走「样本记录信息表」，直接收的类器官走「类器官收样记录」）在小程序或工作台录入，
直接是有效状态。样本数据仍是一张主档，不分来源、不分内外部；工作台按类别分两个菜单页——「样本记录信息表」（组织样本）与
「类器官收样记录」（类器官），各自的列、筛选、导出按甲方模板（2026-09-24 甲方第 25 行「应该是分开的表」，CR-20260924-10；
此前是组织与类器官同在一张「样本总表」）。四张 Excel 是按甲方模板列序导出的视图。四张表的记录提交后内部人员随时可改（含已核验有效的样本）。
小程序里内部人员在「我的 → 内部管理」看像 Excel 一样的表格页：查看、筛选、导出，点一行进详情可「修改」，待核验的那一条进核验页（CR-20260918-07、CR-20260924-10，见 FLOW:F-MP-01）。

| 步 | 谁 | 做什么 | 写什么 | 产出 |
|---|---|---|---|---|
| `FLOW:F-SAMPLE-02.step1` | 内部人员(mp/admin) | 填样本记录信息表全部 14 列 + 有无病理；内部编号必填且唯一 | FIELD:t_lqg_sample.internal_no, FIELD:t_lqg_sample.receive_date, FIELD:t_lqg_sample.is_fixed, FIELD:t_lqg_sample.process_time, FIELD:t_lqg_sample.has_qc_sheet, FIELD:t_lqg_sample.has_viability_report, FIELD:t_lqg_sample.operator_name | 样本 sample_kind=tissue、submit_source=internal、verify_status=valid |
| `FLOW:F-SAMPLE-02.step2` | 内部人员(mp/admin) | 填类器官收样记录 7 列 + 代数：来源单位、类器官类型、代数（选填，形如 P3，紧跟类器官类型；模板没有这一列，甲方 2026-09-24 第 18 行要加，CR-20260924-10）、 收样日期、内部编号、处理时间、细胞活率报告、操作人 | FIELD:t_lqg_sample.species, FIELD:t_lqg_sample.organoid_type, FIELD:t_lqg_sample.passage, FIELD:t_lqg_sample.sample_kind | 样本 sample_kind=organoid、submit_source=internal、verify_status=valid |
| `FLOW:F-SAMPLE-02.step3` | 内部人员(admin) | 两页共用一个列表接口，页面固定带 sampleKind（样本类别不再是筛选项，CR-20260924-10）。 样本记录信息表：按来源单位、组别、提交来源、核验状态、收样日期区间、组织类型、内部编号、操作人筛选；供体姓名与住院号精确匹配。 类器官收样记录：同上，但用「类器官类型」（模糊）代替组织类型，没有供体姓名、住院号。分页 |  | 每页看一类样本；每行带内外部标识、切片染色提示与「石蜡包埋 / 冻存」关联数（relation，见 step8，CR-20260924-11） |
| `FLOW:F-SAMPLE-02.step4` | 系统 | 读时计算：该样本名下石蜡块数、是否有切片时间非空的块、做过的染色种类并集（NONE 不计）。不落库 |  | 提示文案：小程序表格页最后一列如「石蜡块 2 · 已切片 · HE / IHC」；工作台两页的「切片染色」列只写「已切片 · HE / IHC」或「未切片」， 块数挪到「石蜡包埋 / 冻存」一列（step8，CR-20260924-11）。没有包埋记录显示「—」 |
| `FLOW:F-SAMPLE-02.step5` | 内部人员(admin) | 「样本记录信息表」页只导 tissue 类、「类器官收样记录」页只导 organoid 类；表头文字与列序和甲方模板逐字一致， 唯一的例外是类器官收样记录在「类器官类型」后插入「代数」一列（模板 7 列 + 代数 = 8 列，甲方 2026-09-24 第 18 行要求插入，CR-20260924-10）； 范围 = 当前筛选结果；按钮类字段导出为「有 / 无」 |  | xlsx 文件；供体姓名、住院号为明文 |
| `FLOW:F-SAMPLE-02.step6` | 内部人员(mp) | 「我的 → 内部管理」点「样本记录信息表」或「类器官收样记录」进表格页：按内部编号 / 来源单位 / 供体姓名（精确）/ 核验状态筛； 表格上不新增、不行内编辑。点一行进只读详情，右上角「修改」切到修改模式（CR-20260918-07）；合作单位送来、待核验的那一条直接进核验页（CR-20260924-10，FLOW:F-MP-01.step4）； 改判仍只在工作台 |  | 小程序内部表格页；从这里进入修改与核验 |
| `FLOW:F-SAMPLE-02.step7` | 内部人员(mp) | 在「我的 → 内部管理」表格页任一工作表点「导出 Excel」：按当前筛选导出，表头与列序和工作台导出同一个导出视图（照甲方模板）； 小程序拿到文件后「打开」（微信查看器右上角可保存 / 转发）或「发送到微信」；外部人员调导出一律 403 |  | 与工作台导出同格式的 xlsx，落到用户的微信里 |
| `FLOW:F-SAMPLE-02.step8` | 内部人员(admin) | 2026-09-24 Kevin 本机验收「四种表之间的关系看着有点乱」（CR-20260924-11）：样本两页的「操作」列只留编辑 / 核验、质控文档、删除； 新增「石蜡包埋 / 冻存」一列写「蜡块 N · 待核验 N · 冻存 N 批」——蜡块 = 已核验有效的石蜡块（沿用 hint.blockCount，与 step4 同一个数）、 待核验 = 合作单位送来还没核验的石蜡包埋送样（有才显示）、冻存 = 未删的冻存批次；数字可点，带 sampleId 过去；为 0 时有效样本给「新增」，过去直接打开新增抽屉、样本已选好。 石蜡包埋页、冻存页带 sampleId 进来时页顶显示「只看 ××（单位）的石蜡包埋记录 / 冻存批次 · 共 N 条 · 打开样本 · 看全部」； 两页行上的样本编号（内部编号，待核验的显示送检单号）能点回样本所在那一页（按所挂样本的类别分组织 / 类器官）并打开这条样本的抽屉。 关联数读时算、整页一次查询，样本表上不落冗余列 |  | 样本列表行带 relation{pendingEmbedCount, cryoBatchCount}（每行都有，没有记录为 0）；石蜡包埋、冻存列表行带所挂样本的 sampleKind；三张表之间来回可达 |
