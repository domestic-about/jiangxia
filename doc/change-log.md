# 变更记录（CR）—— 决策终局的留痕层

> 需求正文（`requirements.yaml` 的 text、合同、方案 v6）= 只读。任何需求 / 口径 / 字段 / 范围变更走 CR，**按时间倒序 append-only**。
> 实现方 §0 自检必扫本文件：涉及当前 ticket 的 CR **以 CR 为准**。
> 「影响 ticket」一栏不手数——粘 `authority_lint.py impact` 的输出。

## CR 模板（复制整段填空，贴到「CR 列表」最上方）

```
## CR-YYYYMMDD-NN: <一句话变更标题>

- **提出人**：<Kevin / 甲方 / Reviewer / AI 评审>
- **范围归属**：合同内 / 合同外          ← 「合同外」= 二次开发，另行报价（合同第六条第 5 款）；攒起来就是下次报价的证据
- **影响 ticket**：<粘 authority_lint.py impact 的输出；无则写「无新增，仅确认现状」>
- **背景**：<为什么要改>
- **变更前**：<引用原口径>
- **变更后**：<新口径，能落地的具体值>
- **影响下游**：<连带改的表 / 字段 / 文档>
- **决策**：✅ 接受 / ❌ 拒绝 / ⏸️ 待定
- **签字**：<谁> @ YYYY-MM-DD（<依据一句话>）
```

> 铁律：before / after 都要有；必须有人签字（AI 不签）；甲方原始材料与推断冲突时甲方原始材料优先；改完权威必跑 `authority_lint.py diff → impact`，再 `snapshot`。

## CR 列表

## CR-20260918-07: 甲方看设计图后的 9 条——表格页加修改入口、历史记录看全中心、外部可见操作人与包埋人、内部编号开关、冻存超期口径

- **提出人**：甲方（2026-09-18 在《测试问题记录表》「小程序」子表填的 9 条，逐字落在 `_input/feedback/2026-09-18-甲方看设计图的9条意见.md`，截图存 `2026-09-18-shots/`）
- **范围归属**：合同内。5 处都是已有页面上的口径、开关与过滤条件调整，不新增页面、不新增表；估合计约 1.3 人日（⑩ 修改入口约 0.5、内部编号开关约 0.3、历史记录范围约 0.2、冻存阈值参数化约 0.2、外部两个字段可见约 0.1），排进 D2 / D3 / D4 / D7，不动 D8 与交付日期
- **影响 ticket**（`authority_lint.py impact` 的输出：权威变更涉及 11 个锚，波及 11 个 ticket）：
  - AUTH-EXT-002 ← UI:mp.sample.detail.ext（外部样本详情：包埋卡片加操作人、包埋人；内部编号按 `lqg.ext.show-internal-no` 开关显示）
  - CRYO-MP-001 ← UI:mp.cryo.list, UI:mp.history, UI:mp.ledger（冻存工作表：详情弹层加「修改」入口；历史页签改为全中心；超期随转液氮消失）
  - CRYO-REMIND-001 ← FLOW:F-CRYO-01.step2（超期阈值改读系统参数 `lqg.cryo.overdue-days`，默认 14；转液氮 / 取空后退出清单）
  - EMBED-MP-001 ← UI:mp.embed.list, UI:mp.history, UI:mp.ledger（石蜡包埋工作表：只读详情 +「修改」；历史页签全中心）
  - SAMPLE-MP-001 ← UI:mp.history, UI:mp.sample.detail.ext（历史编辑记录内部默认全中心 +「只看我提交的」开关 + 经手人列；外部详情两个字段）
  - SAMPLE-MP-002 ← UI:mp.ledger, UI:mp.sample.list（表格页点一行进只读详情，详情右上角「修改」切修改模式）
  - SYS-EXPORT-001 ← UI:mp.ledger（页底小字去掉「修改」二字，导出本身不变）
  - SYS-MP-001 ← UI:mp.me（内部管理板块底部小字改为「核验、冻存取用请到网页工作台」）
  - AUTH-EXT-001 ← ADR-0004, FLOW:F-EXT-01.step3（开关从 application.yml 配置项改为 sys_config 系统参数；外部样本 VO 仍不含操作人）
  - AUTH-EXT-003 ← ADR-0004, FLOW:F-EXT-01.step3（逐条核过：文档 VO 的断言仍成立，无需改动）
  - DOC-RENDER-001 ← ADR-0004（逐条核过：外部版文档里内部编号一格仍留空，**开关不作用于预渲染文档**，已在任务书与确认单 05 写明）
  - 连带改的基线：`doc/verify/fixtures/java/ExtChokepointContractTest.java` 的 I3 禁用字段表——operatorName / embedBy 仍在禁用表里，只对 `ExtEmbedVo` 精确豁免（不是整张摘掉，免得样本 VO 顺着继承漏出操作人）；`doc/api-contract.md`、`doc/verify/README.md`、三份 form fixture 的 `_doc` 一并对齐
- **背景**：甲方 9 月 18 日在测试问题记录表里提了 9 条。其中 5 条是改动，4 条是提问（首页顶部数字是什么、「不渲染」什么意思、预览排版是不是最终版式、文档预览那条「没有解决」），答复回填在记录表「解决方式」一栏，并把图廊上对应的说法改成人话
- **变更前 → 变更后**：
  1. ⑩ 内部管理表格页：只读，改记录只能走「我的 → 历史编辑记录」或网页工作台 → **点一行进只读详情，详情右上角「修改」进该表填写页的修改模式**；内部人员可改任何人录的记录（外部送来未核验的仍只读）。表格本身仍无行内编辑、无新增、无核验
  2. ⑪ 历史编辑记录（内部）：本人新增或最后一次由本人修改的记录 → **中心全部内部人员的记录，默认全列，顶部「只看我提交的」开关（默认关）**，每行显示经手人
  3. ⑦ 外部样本详情：看不到操作人、包埋人和冻存信息 → **看得到操作人与包埋人**，仍看不到冻存信息
  4. ⑦ 内部编号：一律不对外显示 → 默认不显示，**系统参数 `lqg.ext.show-internal-no`（默认 false）打开后外部可见**，只有内部人员能在工作台系统管理里改（作用范围：外部接口返回的页面数据。外部版质控文档里的内部编号一格**仍然留空**——文档是预渲染缓存的产物，开关切换不会重出历史文档；要一并放开另记变更，已写进确认单 05）
  5. ⑧ 冻存超期：满 14 天进提醒清单（阈值写死） → 阈值改读系统参数 **`lqg.cryo.overdue-days`（默认 14）**；并写明**登记转液氮或支数取空后立即退出超期清单、提醒消失**
- **影响下游**：`authority/ui-index.yaml`（UI:mp.ledger / mp.history / mp.me / mp.sample.list / mp.embed.list / mp.cryo.list / mp.sample.detail.ext）、`authority/flows.yaml`（F-CRYO-01.step2）、`design-options/gallery.html` 对应帧与口径、`design-options/README.md`、`design-authority.md`、8 张 ticket；新增两个系统参数 `lqg.ext.show-internal-no`、`lqg.cryo.overdue-days`（若依 sys_config 参数项，工作台「系统管理 → 参数设置」里改，不建表；REQ-AUTH-012 原先写的是应用配置，本 CR 起改为运行时可改的系统参数）
- **另一件事：内部图廊外泄**。甲方 9 条截图全部来自内部图廊 `gallery.html`（已发布为 Artifact），不是给甲方的设计稿 v2；甲方因此读到「Kevin 定稿」「合同里我方的建议」「约 1 人天，另记变更」等内部话术，其中 2 条问题正由这些话术引起。处置（Kevin 2026-09-18 定）：**把图廊里的内部话术清干净，继续用同一个链接**——人名、人日、报价、「我方的建议」、CR 编号、lint 命令、README 指向全部移除，「甲方」改称「贵方」，「不渲染」等黑话改成人话。内部决策记录仍留在 `README.md`、本文件与 `design-authority.md`（这三份不对外）
- **补画（同日晚，Kevin：「UI 上没有看到表格页详情的修改按钮」「加个开关似乎没有看到」「很多改变都没有在图上显示，尽量把相关页面显示完全，甲方看图更清晰」）**：
  这一轮的改动原先只写在说明文字里，图上看不见。补了 5 帧——`#mp-ledger-view`（点一行的只读详情，右上角「修改」）、`#mp-ledger-edit`（点修改之后的填写页）、
  `#mp-detail-ext-on`（开关打开后的外部详情，与 `#mp-detail-ext` 并排对比）、`#admin-cryo`（工作台取用登记与转液氮，图上标出转液氮后超期页签 2→1）、`#admin-config`（系统管理 · 参数设置，两个开关都在图上）。
  新增权威 `UI:admin.config`（related_reqs: REQ-AUTH-012 / REQ-CRYO-003），由 **SYS-WEB-001** 认领：菜单授权 + 两行 sys_config 的 accept 断言已加。
  给甲方的设计稿 14 → 19 张图；顺手把文案里的硬编码图号改成 `{fig:<帧 id>}` 占位符、渲染时自动编号（插图导致图号错位，这次已经发生过一次）。
- **决策**：✅ 接受
- **签字**：Kevin @ 2026-09-18（三条拍板：⑩ 走「只读 + 修改入口」折中；历史编辑记录内部看全实验室；图廊内部话术清干净、链接不换）

## CR-20260917-06: 设计图廊删掉未采用的设计；「我的」页重绘

- **提出人**：Kevin（2026-09-17 夜：「1. 去掉未采用的设计；2. 我的页面用 claude design 重新设计一下」）
- **范围归属**：合同内（只动设计稿与视觉基准，不动需求、字段、接口）
- **影响 ticket**（`authority_lint.py impact` 的输出：权威变更涉及 1 个锚，波及 0 个 ticket）：
  - UI:mp.sample.mine：只改了 prototype 一栏的文字（原先指向的外部视角帧已删）；这条记录早已被 UI:mp.history 取代，没有 ticket 引用
  - SYS-MP-001：impact 反查不到，但它照 `#mp-me-int` / `#mp-me-ext` 两帧做「我的」页；锚点没变、内容块没变，任务书不用改，实现时照新帧即可
- **背景**：设计定稿后，图廊里置灰留档的未采用方案容易被当成可选项；「我的」页原来是一张色块身份条加几行列表，Kevin 要重新设计
- **变更前**：
  - `gallery.html` 里留着 11 帧未采用或作废的设计（首页方案 A 与上午选定的 B、②~⑥ 的方案 B、⑩ 的两种挂法），以及 7 张 A / B 对比表和 AI 推荐说明
  - 「我的」页：渐变色块身份条；历史编辑记录一行；内部管理 = 四行列表；协议与退出登录挤在一张卡片里
- **变更后**：
  - 图廊只留定稿帧；各节只保留一句「Kevin 选定 · 理由」。未采用方案只在 `design-options/README.md` 留文字，链接一栏标「已删」
  - 「我的」页重绘（`#mp-me-int` / `#mp-me-ext`，内容块与 UI:mp.me 一致）：身份区去掉色块，头像缺省显示姓的首字；白底圆角分组卡片带小标题，行首是图标方块；内部管理 = 2×2 表名方块 + 灰底「修改、核验、冻存取用请到网页工作台」；外部的单位与组别行带核验徽标；用户协议、隐私政策分两行；退出登录单独一张
  - **没有经过 Claude Design**：这里没有它的出图通道，把本机组件库同步进 Claude Design 的 `/design-sync` 只能 Kevin 自己运行。已写好可直接粘到 claude.ai/design 的设计说明 `design-options/claude-design-brief-我的.md`；那边出了稿，再走变更记录替换这两帧
- **影响下游**：`design-options/gallery.html`、`README.md`、`design-authority.md`（A 节、C 节的「我的」组件）、新增 `claude-design-brief-我的.md`；`authority/ui-index.yaml`（UI:mp.sample.mine 的 prototype 文字）；给甲方的 `界面设计稿-v2.html` 原地重出（图 5「我的」换成新帧，仍未发出）
- **决策**：✅ 接受
- **签字**：Kevin @ 2026-09-17（「去掉未采用的设计」「我的页面……重新设计一下」）

## CR-20260917-05: 小程序结构四条决定——首页内外部同一个样子并去掉数字、外部三张表、内部管理只读、所有人在「我的」看历史编辑记录

- **提出人**：Kevin（2026-09-17 晚两段话，逐字落在 `_input/feedback/2026-09-17-Kevin-小程序结构四条决定.md`：先是「我觉得首页进去都是一样的，无论外部还是内部……内部人员在我的页面，多一个内部管理板块」，AI 复述理解后再答「1. 外部三个表；2. 网页工作台要有最全面的功能，只是把部分查看和筛选以及导出等功能放到小程序里；3. 首页顶部数字去掉，不合理；4. 所有人员想看历史编辑记录，都可以在我的页面查看」）
- **范围归属**：合同内为主，有增有减，是否计费由 Kevin 定：
  - 减：小程序首页的三个数字与最近记录不做（`/mp/int/home`、`/mp/ext/home` 不建）；小程序冻存操作弹层（取走 / 补入 / 转液氮 / 改删登记）不做，**CRYO-MP-002 删除**；表格页不做新增与修改入口
  - 增：外部可提交类器官收样记录与石蜡包埋送样记录（REQ-AUTH-015）——石蜡包埋表加 6 个核验字段、核验状态机、工作台核验抽屉、工作台首页第五张卡片；「我的 → 历史编辑记录」（REQ-SYS-020）；「我的 → 内部管理」板块（REQ-SYS-019，复用原表格页）
  - 工作量估计：增约 3-3.5 人日（外部送样两种 + 石蜡包埋核验约 2 人日，历史编辑记录约 1-1.5 人日），减约 1.5-2 人日（冻存操作弹层约 1 人日，小程序首页数字与最近记录、表格页新增入口约 0.5-1 人日）；**净增约 1.5 人日**，排进 D2 / D3 / D4 / D7，不动 D8 与交付日期
  - 不在本 CR 里：逐次修改明细（审计日志）。「历史编辑记录」按记录清单做（`create_by` / `update_by` + 可见范围）；要看每一次改了什么约另需 1 人日，记 OQ-16 待 Kevin 定
- **与甲方原话的冲突**：甲方 9-17 下午说外部「填的就是样本记录信息表」（REQ-AUTH-014，本 CR 标 dropped）。Kevin 定外部三张表，依据是会上 L295 他举例「石蜡包埋送样本外部也可以填」、甲方答「可以可以」。外部类器官收样三项、石蜡包埋送样两项的字段划分是 AI 默认口径（REQ-AUTH-015 note），写进确认单 02 / 03 与设计稿 v2 请甲方过目（OQ-15）
- **影响 ticket**（`authority_lint.py impact` 的输出：权威变更涉及 58 个锚，波及 24 个 ticket；另删除 CRYO-MP-002）：
  - SYS-MP-001 ← FLOW:F-AUTH-01.step4, UI:mp.home, UI:mp.home.entries, UI:mp.me（**重写**：首页内外部同一个样子、无数字、点表就是填写；「我的」里历史编辑记录与内部管理板块；fixture 改 cases / targetCases，加 meCases）
  - SAMPLE-MP-001 ← FLOW:F-SAMPLE-01.step1, step4, FLOW:F-SAMPLE-02.step1, UI:mp.sample.detail.ext, UI:mp.sample.form（**重写**：填写页新增 / 修改 / 只读三种模式；历史编辑记录页框架与样本页签取代「我的送检」；`/mp/int/sample/list?mine=true`；fixture 加入口模式三例）
  - SAMPLE-MP-002 ← FLOW:F-SAMPLE-02.step2, step6, UI:mp.ledger, UI:mp.organoid.form, UI:mp.sample.list（**重写**：表格页只读、挂内部管理；类器官收样内外部两种布局 + 新 fixture；首页计数迁出到 SYS-HOME-001）
  - AUTH-EXT-001 ← FLOW:F-EXT-01.step1 ~ step4, FLOW:F-SAMPLE-01.step1, step4（外部类器官收样入口与写保护；列表加 `sampleKind` / `onlyMine`；删 `/mp/ext/home`）
  - AUTH-EXT-002 ← FIELD:t_lqg_embed.paraffin_block_no, FLOW:F-EMBED-01.step4, step6, FLOW:F-EXT-01.step3, step4, UI:mp.sample.detail.ext（**重写**：外部石蜡包埋送样的提交、重提、列表与详情；详情含待核验的送样）
  - EMBED-MODEL-001 ← FIELD:t_lqg_embed.paraffin_block_no, FLOW:F-EMBED-01.step1 ~ step3, step6, step7（核验字段、核验状态机与 `PUT /lqg/embed/{id}/verify`、外部送样 service；新增第 4 条 accept）
  - EMBED-WEB-001 ← FLOW:F-EMBED-01.step5, step7, FLOW:F-SAMPLE-02.step5, UI:admin.embed.list（核验抽屉、状态徽标列、verify 按钮权限；导出含待核验送样）
  - EMBED-MP-001 ← FLOW:F-EMBED-01.step1 ~ step3, step6, UI:mp.embed.form, UI:mp.embed.list, UI:mp.ledger（**重写**：外部送样填写页、只读工作表、历史页签；新 fixture）
  - CRYO-MP-001 ← FLOW:F-CRYO-01.step1, step3, FLOW:F-CRYO-02.step5, UI:mp.cryo.flow, UI:mp.cryo.form, UI:mp.cryo.list, UI:mp.ledger（**重写**：只读工作表与批次详情、历史页签；`/mp/int/cryo` 上没有写流水的接口）
  - **CRYO-MP-002（删除）**：小程序冻存操作弹层整张不做，`doc/tickets/CRYO-MP-002/` 已删；它认领的 REQ-CRYO-004 / 005 / 006 仍由 CRYO-FLOW-001、CRYO-WEB-001 覆盖，REQ-CRYO-008 改由 CRYO-MP-001 一并认领
  - CRYO-REMIND-001 ← FLOW:F-CRYO-01.step2, step3（去掉 `/mp/int/home` 接入，改为给工作台首页的 `countOverdue()`；不再依赖 SAMPLE-MP-002）
  - CRYO-FLOW-001、CRYO-WEB-001 ← FLOW:F-CRYO-01 / F-CRYO-02 各步（措辞：写流水的接口只在工作台）
  - SYS-HOME-001 ← FLOW:F-CRYO-01.step3, FLOW:F-EMBED-01.step7, REQ:REQ-SYS-901, UI:admin.home（**重写**：`HomeCounterService` 迁到本张；五个数；石蜡包埋菜单角标）
  - SAMPLE-HINT-001 ← FLOW:F-SAMPLE-02.step4（只数已核验有效的石蜡块；accept 库侧计数加 `verify_status='valid'`）
  - SAMPLE-VERIFY-001 ← FLOW:F-SAMPLE-01.step4（状态机不分样本种类；转移表抽成 `VerifyTransitions` 给石蜡包埋复用）
  - SAMPLE-WEB-001 ← FLOW:F-SAMPLE-02.step3, UI:admin.sample.edit, UI:admin.sample.list（核验抽屉按样本种类切字段）
  - SYS-EXPORT-001 ← FLOW:F-SAMPLE-02.step7, UI:mp.ledger（依赖改为 CRYO-MP-001；措辞）
  - SYS-MANUAL-001 ← FLOW:F-CRYO-02.step1, step5, FLOW:F-SAMPLE-01.step1, FLOW:F-SAMPLE-02.step7（两份说明按新结构写；关键词断言换成「历史编辑记录」「内部管理」）
  - SYS-STAGING-001：impact 反查不到，但 accept 里调了 `/mp/int/home`，换成内部历史编辑记录的集合与超期页签计数
  - 逐张核对、无需改动：AUTH-LOGIN-001（FLOW:F-AUTH-01.step4 只改了产出描述）、AUTH-EXT-003、SAMPLE-MODEL-001、SAMPLE-EXPORT-001（待核验样本本来就导出）、CRYO-MODEL-001、SYS-WEB-001
- **背景**：CR-20260917-04 按甲方意见把内部表格页挂在首页、在小程序里能新增能改、冻存能在弹层里取用，首页保留了数字。Kevin 看过后重定了小程序的整体结构：首页只管填，找回和修改放到「我的」，网页工作台功能最全
- **变更前**：
  - 首页：内部人员顶部三个数字 + 四张表点进去是表格页 + 最近记录；外部只有「样本记录信息表」一个入口 +「我的送检」
  - 外部只能填样本记录信息表（REQ-AUTH-014）
  - 表格页可新增、点一行修改；冻存弹层可取走 / 补入 / 转液氮 / 改删登记（CRYO-MP-002）
  - 找回自己填过的记录：外部靠「我的送检」，内部靠首页最近记录
- **变更后**：
  - 首页内外部同一个样子，没有数字和最近记录；内部四张、外部三张（没有 -80 冻存记录），点哪张都是填写页新增一条（REQ-SYS-018、REQ-AUTH-015，`FLOW:F-MP-01.step1`）
  - 外部可提交类器官收样记录（来源单位、类器官类型、备注）与石蜡包埋送样记录（选本人未判无效的样本 + 样本类型、类器官来源类型），都先待核验；石蜡包埋送样判有效必须给石蜡块编号，且所挂样本已核验有效（`FLOW:F-EMBED-01.step6 / step7`）
  - 「我的 → 历史编辑记录」所有人都有：内部 = 本人新增或最后一次由本人修改的记录；外部 = 本人与同组已核验同事的记录，可「只看我提交的」；点进去修改（外部仅本人待核验 / 无效的）；列的是记录，不是逐次修改明细（REQ-SYS-020，`UI:mp.history`）
  - 「我的 → 内部管理」只给内部：四张表只读查看、筛选、导出；新增走首页，改自己录的走历史编辑记录，核验、冻存取用、改别人录的都在工作台（REQ-SYS-019，`UI:mp.ledger`）；小程序冻存批次详情只读
  - 工作台首页五张卡片（加「待核验石蜡包埋送样」）、石蜡包埋菜单角标；切片染色提示只数已核验有效的石蜡块
- **影响下游**：
  - `_input/feedback/2026-09-17-Kevin-小程序结构四条决定.md`（新，Kevin 原话）
  - `requirements.yaml`：新增 REQ-SYS-018 / 019 / 020、REQ-AUTH-015；REQ-AUTH-014、REQ-SYS-902 标 dropped；改 REQ-SYS-901 的 text；改 note：REQ-SYS-001 / 016 / 017、REQ-AUTH-004 / 009、REQ-SAMPLE-007 / 014 / 015 / 016、REQ-EMBED-001、REQ-CRYO-003 / 008
  - `authority/field-ssot.yaml`：`t_lqg_embed` 加 submit_source / submitter_id / verify_status / verify_by / verify_time / invalid_reason，石蜡块编号改可空（部分唯一索引不约束空值），加两个索引
  - `authority/flows.yaml`：新增 FLOW:F-MP-01（三步）、F-EMBED-01.step6 / step7；改 F-AUTH-01.step4（related_reqs 换成 REQ-AUTH-015）、F-EXT-01、F-SAMPLE-01.step1 / step4、F-SAMPLE-02、F-CRYO-01、F-CRYO-02
  - `authority/ui-index.yaml`：新增 UI:mp.history；UI:mp.sample.mine 标 superseded；改 mp.home、home.entries、me、ledger、sample.form / list / detail.ext、organoid.form、embed.form / list、cryo.form / list / flow、admin.home、admin.embed.list、admin.sample.list / edit
  - `api-contract.md`：删 `/mp/int/home`、`/mp/ext/home`；加 `mine`、`onlyMine`、`/mp/ext/organoid`、`/mp/ext/embed`、`/lqg/embed/{id}/verify`；`/mp/int/cryo` 只读；`/lqg/home/todo` 五个键。`_adr/0004`、`0010` 各补一段后果
  - `design-options/`：gallery.html（① 定稿两帧、② 外部送样帧、⑩ 只读定稿帧、新增 ⑪ 我的与历史编辑记录四帧、⑦⑧⑨ 改；上午的首页 B 与 ⑩ 的 A / B 置灰作废）、README、design-authority（A / C / D-1 / D-2 / D-18 / D-19 / E-6 / E-9 ~ E-12 / F）、`build_client_preview.py`
  - **给甲方的 `界面设计稿-v2.html` 原地重出**（下午那份 v2 没有发出去，版本号不加）：14 张图，开头「和第 1 版相比改了 7 处」，结尾三件事请甲方确认；v1 不动
  - `confirmation/`：README（第 6、7 件事改，加第 8、9 件）、01 / 02 / 03 / 04；`_oq.md`：OQ-13 关闭，新增 OQ-15、OQ-16
  - `phase-plan.yaml`：D1 / D2 / D3 / D4 / D7 的目标与 QA 范围；演练剧本 1 / 4 / 5 改，加剧本 7
  - `verify/`：`home-entries-cases.json` 重写（外部三张、点进去一律是填写页、加 meCases）；`sample-form-cases.json` 加入口模式（13 例）；新增 `organoid-form-cases.json`、`embed-form-cases.json`；`gen_seed.py`：石蜡包埋行补核验字段、加一条外部待核验送样 2006、2001 / 2003 / 2005 与 3001 / 3003 / 3008 的创建人改成李工（给历史编辑记录的断言用）；`README.md` 的 seed 速查同步。临时 PostgreSQL 上 `check_accept_sql.py` 125 条 SQL 全部可解析，新 seed 的期望值逐条对过
- **决策**：✅ 接受（净增约 1.5 人日是否计费、历史编辑记录要不要逐次明细（OQ-16）待 Kevin 定；外部三张表的字段划分待甲方过目（OQ-15））
- **签字**：Kevin @ 2026-09-17（「1. 外部三个表；2. 网页工作台要有最全面的功能……；3. 首页顶部数字去掉，不合理；4. 所有人员想看历史编辑记录，都可以在我的页面查看」）

## CR-20260917-04: 甲方看界面设计稿 v1 的 5 条意见——内部表格页、提交后可修改、小程序导出 Excel、文档列表直接下载、三份 Word 对外可见

- **提出人**：甲方（2026-09-17 下午微信，看的是 `doc/confirmation/类器官送检与样本管理系统-界面设计稿-v1.html`；原话逐条落在 `_input/feedback/2026-09-17-甲方看界面设计稿v1的意见.md`）；Kevin 在同一段对话里答应「明白了，我调整一下，内部人员加一个板块可能比较好」，并让 AI「提取并修改」
- **范围归属**：大部分**合同内**（口径调整与展示）；两处**超出合同附件原口径**，由 Kevin 决定是否计入二次开发：
  - 合同内：提交后可修改（合同只写「在线填写」「出入库登记」，没写不可改；原来的「只增不改」是 AI 为追溯定的默认值）；文档列表直接单份 / 合并下载、三份 Word 对外可见（合同附件第 4、6 行本来就有，这次是展示改清楚）
  - 超出原口径：① 小程序里内部人员的表格页（合同附件第 1 行是「首页四张表的在线填写」，原设计是卡片列表 + 填写页）；② 小程序里导出 Excel（合同附件第 8 行写的是工作台导出；REQ-SAMPLE-013 note 原写「小程序不做 Excel 导出」）
  - 工作量估计：表格页约 1.5-2 人日（一个共用表格组件 + 四个工作表注册），小程序导出约 1 人日（后端复用工作台三个导出 service），冻存登记可改可删约 1 人日；合计约 3.5-4 人日，排进 D2 / D3 / D4 / D7，不动 D8 与交付日期
- **影响 ticket**（`authority_lint.py impact` 的输出：权威变更涉及 35 个锚，波及 20 个 ticket；含本 CR 新增的 CRYO-MP-002、SYS-EXPORT-001）：
  - SYS-MP-001 ← UI:mp.home, UI:mp.home.entries（首页入口按身份跳转：内部进表格页、外部直接填写；fixture 加 targetCases）
  - SAMPLE-MP-001 ← FLOW:F-SAMPLE-02.step1, UI:mp.home, UI:mp.sample.detail.ext, UI:mp.sample.form（内部修改模式；待核验 / 无效外部样本只读；fixture 加两例）
  - SAMPLE-MP-002 ← FLOW:F-SAMPLE-02.step2, FLOW:F-SAMPLE-02.step6, UI:mp.home, UI:mp.organoid.form, UI:mp.sample.list（**重写**：表格页、LedgerTable、四张表的列 fixture 与模板原件对账、类器官修改模式）
  - EMBED-MP-001 ← UI:mp.embed.list（石蜡包埋改成表格页的工作表）
  - CRYO-MODEL-001 ← FIELD:t_lqg_cryo_batch.init_qty, FLOW:F-CRYO-01.step1, FLOW:F-CRYO-02.step4, FLOW:F-CRYO-02.step5（初始支数可改 + CryoBalanceChecker；accept 由「改被拒」翻成「改得动、改负被拒」）
  - CRYO-FLOW-001 ← FLOW:F-CRYO-02.step1 ~ step5（登记可改可删、逐笔剩余不得为负；accept 由「PUT / DELETE 打不动」翻成改删的正反用例，并发与转液氮拆成第 3 条）
  - CRYO-WEB-001 ← FLOW:F-CRYO-02.step3, FLOW:F-CRYO-02.step5, UI:admin.cryo.list（流水抽屉可改可删、初始支数可改）
  - CRYO-MP-001 ← FLOW:F-CRYO-01.step1, FLOW:F-CRYO-02.step5, UI:mp.cryo.form, UI:mp.cryo.list, UI:mp.home（**拆成两张**：本张 = 冻存工作表 + 填写修改 + 首页直达）
  - **CRYO-MP-002（新）** ← FLOW:F-CRYO-02.step1, step2, step5, UI:mp.cryo.flow（操作弹层：取走 / 补入 / 转液氮、改删登记）
  - DOC-MP-001 ← FLOW:F-DOC-02.step1, UI:mp.doc.list（每份「下载」、组底「合并预览」「合并下载」）
  - DOC-MP-002 ← FLOW:F-DOC-02.step2 ~ step4, UI:mp.doc.preview（DownloadSheet 点亮列表下载；DocTabs 顶部三份切换）
  - AUTH-EXT-003 ← FLOW:F-DOC-02.step1, step3（外部文档清单加 sampleId 过滤，先过可见范围；三种文档都给）
  - **SYS-EXPORT-001（新）** ← FLOW:F-DOC-02.step4, FLOW:F-SAMPLE-02.step7（小程序导出四张 Excel，与工作台同一个导出视图）
  - SAMPLE-EXPORT-001、EMBED-WEB-001 ← FLOW:F-SAMPLE-02.step5（导出视图放 service 层供小程序复用；EMBED-WEB-001 抽屉显示最后修改）
  - SAMPLE-HINT-001 ← FLOW:F-SAMPLE-02.step4（小程序的切片染色提示改成表格页一列）
  - SAMPLE-MODEL-001 ← FLOW:F-SAMPLE-02.step1, step2（详情带最后修改人与时间）
  - SAMPLE-WEB-001 ← FLOW:F-SAMPLE-02.step3, UI:admin.sample.edit（抽屉显示最后修改）
  - SYS-MANUAL-001 ← FLOW:F-CRYO-02.step1, step5, FLOW:F-DOC-02.step4, FLOW:F-SAMPLE-02.step7（内部版说明补表格页与改登记）
  - AUTH-EXT-002 ← UI:mp.sample.detail.ext：逐张核对，这张管的是外部详情里的石蜡包埋段，本次只改了质控文档段的措辞，**无需改动**
- **背景**：Kevin 把 v1 设计稿发给甲方过目，甲方逐图提了意见。其中「提交后不能修改」「列表页不放下载按钮」「小程序不做 Excel 导出」三条原口径都是 AI 定的默认值，甲方不同意
- **变更前**：
  - 首页四张表点进去是「卡片列表 + 右下角新增」；小程序不做 Excel 导出
  - 冻存流水只增不改不删，填错了再登记一条反向的；初始支数建后不可改（改数走盘点调整）
  - 文档列表页不放下载按钮，下载只在预览页底部；设计稿文字没写明合作单位看得到哪几份文档
- **变更后**：
  - 内部人员点首页四张表进「表格页」：四张表顶上切换、列名列序照模板、首列冻结、左右滑，底部「导出 Excel」「＋ 新增一行」，点一行进填写页修改（REQ-SYS-016，`UI:mp.ledger`）。外部点唯一入口直接进填写页。表格页挂在哪 Kevin 待选（OQ-13），先按 A
  - 小程序表格页导出四张 Excel，`GET /mp/int/export/{sheet}`，与工作台同一个导出视图，打开或发送到微信（REQ-SYS-017）
  - 四张表记录提交后内部人员随时可改（含已核验有效的样本），显示最后修改人与时间；待核验、无效的外部样本在小程序里只读（核验与改判仍在工作台）；合作单位改已核验样本仍不行（OQ-14，写进确认单让甲方过目）（REQ-SAMPLE-016）
  - 冻存登记可改可删（软删）、初始支数可改；改删后按 flow_time 逐笔算剩余，任何一步为负就拒绝；登记类型不可改；小程序不能改删盘点调整类登记（REQ-CRYO-008，`FLOW:F-CRYO-02.step5`，ADR-0010 后果补一段）
  - 文档列表每份「下载」、每组「合并预览」「合并下载」；预览页顶部三份切换；合作单位看得到三份质控文档（REQ-DOC-006 / 008、REQ-AUTH-006 的 note）
- **影响下游**：
  - `requirements.yaml`：新增 REQ-SYS-016 / 017、REQ-SAMPLE-016、REQ-CRYO-008（source 指向 `_input/feedback/`）；改 note：REQ-SAMPLE-013 / 014 / 015、REQ-CRYO-004、REQ-DOC-006 / 008、REQ-AUTH-006
  - `authority/ui-index.yaml`：新增 UI:mp.ledger；改 13 条（mp.home、home.entries、sample.form、sample.detail.ext、sample.list、organoid.form、embed.list、cryo.list、cryo.form、cryo.flow、doc.list、doc.preview、admin.sample.edit、admin.cryo.list）
  - `authority/flows.yaml`：F-SAMPLE-02（新增 step7）、F-CRYO-01.step1、F-CRYO-02（新增 step5）、F-DOC-02；`field-ssot.yaml`：init_qty 与流水表的注释；`api-contract.md`；`_adr/0010`；`_oq.md` OQ-13 / 14
  - `design-options/`：gallery.html（③④⑦⑧ 改帧、新增 ⑩ 两案）、README、design-authority（A / C / D-14~17 / E-7~9 / F）、`build_client_preview.py` 出 v2
  - `confirmation/`：README（第 7 件事、指向 v2）、02 / 03 / 04 / 05；**给甲方的 `界面设计稿-v2.html`**（v1 留着不动）
  - `phase-plan.yaml`：D2 / D3 / D4 / D7 的目标与 QA 范围、最终演练剧本 4 / 5
  - `verify/`：新 fixture `ledger-columns-cases.json`（表头由脚本从甲方 xlsx 原件读出）；`home-entries-cases.json` 加 targetCases；`sample-form-cases.json` 加两例；`xlsx_header.py` 加 `--print-header`
  - 顺带修的两处断言隐患（不属于甲方意见）：① 期望值以负号开头的 `db.py --eq "-2|0"` 会被参数解析当成选项、以用法错误退出——CRYO-FLOW-001、CRYO-MP-001 原来就有，改成 `--eq="-2|0"`；② SAMPLE-MP-002 的断言用 `HOME` 当变量名会覆盖家目录，改名 `H`。两条都记进了 `verify/README.md` 的坑表
- **决策**：✅ 接受（⑩ 表格页挂在哪待 Kevin 选，先按 A；两处超出原口径的是否计费待 Kevin 定）
- **签字**：Kevin @ 2026-09-17（微信里答应甲方调整，并交代「提取并修改」）

## CR-20260917-03: 六个关键页面的设计方案选定——首页 B，其余五页 A

- **提出人**：Kevin（2026-09-17「1 b, 2 a, 3 a, 4 a, 5 a, 6 a, 这是设计决策，请调整」）
- **范围归属**：合同内
- **影响 ticket**（`authority_lint.py impact` 的输出：权威变更涉及 10 个锚，波及 13 个 ticket）：
  - SYS-MP-001 ← UI:mp.home, UI:mp.home.entries
  - SAMPLE-MP-001 ← REQ:REQ-SYS-902, UI:mp.home, UI:mp.sample.form, UI:mp.sample.mine
  - SAMPLE-MP-002 ← REQ:REQ-SYS-902, UI:mp.home
  - CRYO-MP-001 ← UI:mp.home
  - CRYO-REMIND-001、CRYO-WEB-001、SYS-HOME-001 ← FLOW:F-CRYO-01.step3
  - DOC-MP-001 ← UI:mp.doc.list；DOC-MP-002 ← UI:mp.doc.preview
  - QC-WEB-001、QC-WEB-002 ← UI:admin.qc.editor
  - SAMPLE-WEB-001、SAMPLE-EXPORT-001 ← UI:admin.sample.list
  - impact 反查不到、但正文里写着「目前按方案 A，Kevin 若选 B 先停下确认」的另外两张也一并改了措辞：OCR-MP-001、DOC-PDF-001
- **背景**：/xuqiu 第 3 步，视觉拍板归 Kevin。拆 ticket 时六页都按 AI 推荐的方案 A 写；Kevin 选定后只有首页与推荐不同
- **变更前**：首页 = 方案 A（一行一个大卡片入口，待办数字做成入口上的角标，外部与内部同一套版式）；其余五页标的是「方案 A（推荐）」、ticket 里留着「Kevin 若选 B 先停下确认」的闸
- **变更后**：
  - 首页 = **方案 B**：问候行 → 三个数字摘要（待核验样本 / -80 超期 / 今日新增，**仅内部人员**，为 0 照样显示，点数字直达）→ 2×2 宫格入口（入口上不放角标；外部只有一个入口时那一格横向占满整行）→ 最近记录
  - 由稿带进来一个新数字「今日新增」= 当天创建且未删除的样本数（`design-authority.md` D-2、E-6）；`/mp/int/home` 多一个键 `todayNew`；`/mp/int/sample/list` 多一个筛选参数 `createDate`
  - 其余五页锁定方案 A，ticket 里的「先停下确认」全部拿掉；落选的六帧在 `design-authority.md` §A 标「已否决」
- **影响下游**：`authority/ui-index.yaml`（7 条）、`authority/flows.yaml`（FLOW:F-CRYO-01 的总述与 step3）、`requirements.yaml`（REQ-SYS-902 的 text 与 note、REQ-CRYO-003 的 note）、`api-contract.md`、`design-options/`（README、gallery.html、design-authority.md）、`phase-plan.yaml`、确认单 02 / 04 / README、`verify/fixtures/home-entries-cases.json`（加 `statsCases`）、`_oq.md`（OQ-10 关闭）
  - 实际改了内容的 ticket：SYS-MP-001（首页按方案 B 重写 + 验收用例加「数字摘要只给内部」）、SAMPLE-MP-002（`todayNew` + 对账断言）、SAMPLE-MP-001、CRYO-MP-001、CRYO-REMIND-001、SYS-HOME-001、SAMPLE-WEB-001、QC-WEB-001、DOC-MP-001、DOC-MP-002、DOC-PDF-001、OCR-MP-001（后八张只改措辞）；CRYO-WEB-001、QC-WEB-002、SAMPLE-EXPORT-001 经核对无需改动
  - 收尾自查又扫出一处同源措辞：`FLOW:F-CRYO-01` 总述里的「小程序入口角标」→「小程序首页顶部的『-80 超期』数字」。再跑一次 impact，反查到 6 张引用该流各步骤的 ticket：CRYO-REMIND-001、CRYO-MP-001、SYS-HOME-001 已在本 CR 改过；CRYO-MODEL-001、CRYO-FLOW-001、CRYO-WEB-001 不涉及小程序首页的提醒呈现，逐张核对无需改动
  - 顺带修了一个验收数据的隐患（不属于设计决策）：seed 行的创建时间原来是灌库那一刻，会混进 accept 里「刚刚创建的那一行」的时间窗；现已全部挪到过去，只留三条样本是「今天」（两条有效 + 一条软删的病灶，供「今日新增」断言用）
- **决策**：✅ 接受
- **签字**：Kevin @ 2026-09-17（设计决策）

## CR-20260917-02: 设计权威抽取回流 4 条需求（内容块 → REQ）

- **提出人**：AI（/xuqiu §3 第 3b 步，见 `design-options/design-authority.md` §D）
- **范围归属**：合同内（都是已有功能上「会变的内容块」的落点，不新增功能）
- **影响 ticket**：回流发生在拆 ticket 之前，`authority_lint impact` = 波及 0 个 ticket；回流出的 REQ 已由 SYS-HOME-001、SAMPLE-MP-001、SAMPLE-MP-002、CRYO-REMIND-001、CRYO-MP-001、SAMPLE-MODEL-001 认领
- **背景**：设计稿上的角标数字、页签计数、联想词是需求来源之一，不回流就没人负责把它们变成接口
- **变更前**：需求清单 82 条（不含计数与联想词）
- **变更后**：新增 REQ-SYS-901（工作台首页待办计数）、REQ-SYS-902（小程序首页角标与最近记录）、REQ-CRYO-901（冻存页签计数与已超天数）、REQ-SAMPLE-901（联想词走字典）；Ext 白名单补「提交人姓名与是否本人」「已完成评分表的合计分」；`UI:admin.auth.staff` 删「授权时间」列；小程序列表页供体姓名掩码
- **影响下游**：`field-ssot.yaml` 加三个 `lqg_hint_*` 字典、改三个字段的 comment；`flows.yaml` 的 `FLOW:F-EXT-01.step3`；`ui-index.yaml` 五条
- **决策**：⏸️ 待定（AI 已按此拆解；Kevin 过目自审时一并点头或否决）
- **签字**：_待 Kevin_

## CR-20260917-01: 开工基线——合同敲定、方案 v6 确认、需求确定无误

- **提出人**：Kevin（2026-09-17「合同已敲定，需求也确定无误了，请帮我创建相关文件夹，并完成 /xuqiu 的拆解」）
- **范围归属**：合同内
- **影响 ticket**：初始基线，无存量 ticket
- **背景**：方案 v1→v6 六轮改版 + 09-17 甲方微信答复 + 当天视频会议之后，范围收敛；合同飞书版（revision 2）与本地底稿逐行比对一致，甲方未改动
- **变更前**：无
- **变更后**：
  - 范围 = 合同附件《系统功能清单》14 行；合同第一条第 3 款四项 + 方案第 7 章「协作单位试用」= deferred（REQ-SYS-011 ~ 015）
  - 技术栈：若依 RuoYi-Vue-Plus + plus-ui + uni-app，只搬 dongjiaoshan 的底座做法不搬业务（Kevin 2026-09-16，ADR-0001）
  - 数据库：PostgreSQL，Docker 装在服务器上，不用 RDS（Kevin 2026-09-17，ADR-0002）
  - 工期：收款后 3 周出「小程序送检与登录」可试用版，6 周完成开发 + 1 周联调测试（合同第三条）
  - 合同附件二 4 条待确认事项按乙方建议先行（`_oq.md` OQ-1 ~ OQ-4）
- **影响下游**：`requirements.yaml`（REQ-SYS-003、REQ-SYS-011 ~ 015 的 decided_* 四字段）、ADR-0001、ADR-0002
- **决策**：✅ 接受
- **签字**：Kevin @ 2026-09-17（合同敲定 + 方案 v6 经甲方确认）
