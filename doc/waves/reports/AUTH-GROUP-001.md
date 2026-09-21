# AUTH-GROUP-001 · 完工报告

- **ticket**：AUTH-GROUP-001（track AUTH / phase D1 / size M）——D1 最后一张
- **status**：**done**
- **accept**：**3/3 绿**（三条 `run` 原文逐条实跑，见 §4；唯一改动 = 去掉本 agent 沙箱跑不了的 `--fresh-module ruoyi-lqg`，等价证据见 §4.0）
- **分支**：`task/D1`（已切好，未 push / 未 merge / 未切分支）
- **验收对象**：jar `ruoyi-admin.jar` @ `2026-09-21 16:20:07`，后端进程 **PID 45987**（8081，dev profile + `--api-decrypt.enabled=false`）
- **只读区未动**：`doc/waves/state.json`（进来就是 modified，不是我改的）、`_manifest.json`、`doc/requirements.yaml`、`doc/authority/*`、`doc/change-log.md`、`doc/verify/seed/`、`gen_seed.py`、`doc/verify/api.sh`、`doc/verify/reseed.sh`

---

## §0 状态自检（3 级）

| 检查项 | 结果 | 证据 |
|---|---|---|
| 当前分支符合调度器注入的期望 | ✅ PASS | `git branch --show-current` → `task/D1` |
| `depends_on` 全部 done | ✅ PASS | AUTH-LOGIN-001 / AUTH-STAFF-001 / SYS-MP-001 三份报告 `status: done`；产物都在盘上（`/mp/me`、`/lqg/auth/staff/**`、`code/miniapp` 全树） |
| 扫 `doc/change-log.md` 顶部 CR | ✅ PASS | 顶部四条：**CR-20260921-08**（视觉方向 A）、**CR-20260918-07**（`lqg.ext.show-internal-no` / `lqg.cryo.overdue-days` 两个系统参数、外部可见操作人与包埋人）、CR-20260917-06、CR-20260917-05。本票**不受这四条影响**：本票不碰外部样本 VO（那是 AUTH-EXT-002）、不碰小程序视觉 token（SYS-MP-001 已落）、`UI:mp.me` 的底部小字由 SYS-MP-001 落。**CR-20260918-07 里唯一相关的一条**是「外部 VO 禁含 operatorName / embedBy / internalNo / 冻存信息」——本票新增的两个外部 VO（`ExtUnitVo`/`ExtUnitGroupVo`）**一条都不带**，且经 `ExtChokepointContractTest` 扫过（见 §4.4） |
| 逐条取权威锚 | ✅ PASS | 9 个 `blueprint_refs` 全部 `authority_lint.py show` 过，逐条对照见 §1 |
| ADR-0003 / ADR-0004 | ✅ PASS | `authority_lint.py show ADR-0003`：内外部只由角色决定、外部单位组别存 `t_lqg_ext_profile`；ADR-0004 的咽喉不变量由 D2 的 fixture 测试守住（本票主动对齐，见 §4.4） |
| 动手前代码是绿的 | ✅ PASS | `mvn -pl ruoyi-modules/ruoyi-lqg test` → `Tests run: 25, Failures: 0, Errors: 0`（改动前是 21） |

**STOP 判定：无。** 没有出现「上游产物缺失」「与权威冲突」「环境不可用」三类硬阻塞。

## §1 口径复述（本张最容易做反的三点，逐条对 accept 核）

| # | 口径 | 落点 | 机器证据 |
|---|---|---|---|
| 1 | **只有 `verified` 才参与同组互看** | `ExtBindStateMachine.participatesInGroupSharing` + `verified` 是唯一写入 `bind_status` 的终态之一 | 契约测试 `onlyVerifiedParticipatesInGroupSharing`（`pending`/`rejected`/`unbound`/`null` 全 false） |
| 2 | **改单位或组别 → 立刻回 `pending`**（不是等核验人驳回才失效） | `ExtProfileUpdateService.apply` 无条件 `setBindStatus(afterProfileSave())` + 显式清 `verified_by / verified_time / reject_reason` | accept 2 第 7 段：`pending\|9000009102\|-`（第 3 格就是 `verified_by` 已清空） |
| 3 | 自填的单位 / 组别核验通过时必须二选一「新建」或「归并」 | `ExtBindStateMachine.needsSelfInputDecision` 是闸门，`requireUnitAndGroupMatch` 兜底（`unit_id`/`group_id` 都非空且组别属于该单位） | accept 2 第 2/6 段；契约测试 3 个用例；探针 P5/P6 |
| 4 | 合法转移只有五条，拒绝时**库里一字不变** | `ExtBindStateMachine.LEGAL`；`verify()` 先校验后写库（唯一一次 UPDATE 在最后） | accept 2 第 2/4 段（拒 + 库内断言紧跟）；探针 P1/P2/P3/P4 |
| 5 | `GET /mp/ext/units` 只返回 active、只有 id 与名称、不带任何人数 | `ExtCatalogQueryService`（不持有 mapper）+ `ExtUnitVo`/`ExtUnitGroupVo`（**不复用**带 `verifiedCount` 的 `UnitGroupVo`） | accept 3 第 1 段：`([.data[]|..|objects|keys[]]\|map(select(test("count\|Count")))\|length == 0)` → `true` |
| 6 | 单位 / 组别不物理删；停用后已绑定的人不受影响 | `SourceUnitService/UnitGroupService.toggleStatus`；没有 DELETE 端点 | 探针 P17（`405 Request method 'DELETE' is not supported` + 行还在）、P20′（停用后 `bind_status/unit_id/group_id` 一字未动） |
| 7 | 同单位内组别名唯一、不同单位可重名、单位名全库唯一、软删后可重建 | 部分唯一索引 + service 层归一化查重（给人话报错） | accept 1 第 1/3 段；探针 P7/P8/P18/P19 |
| 8 | 两个菜单落 5100 段、可达、**内部人员也看得到** | `V202609210930` 的 5120/5130 + F 按钮，`sys_role_menu` **同时授 101 与 102**（父目录 5100 一起授） | accept 3 第 2/3 段（`--as staff` 取路由命中 2 条） |

**与 `touches` 的已知偏差（照调度器交办办）**：ticket 的 `touches` 写 `code/miniapp/src/pages/me/profile/**`，但 SYS-MP-001 实际建的占位页是 `code/miniapp/src/pages/me/unit-group.vue`，且「我的」页的「单位与组别」行已指向它（`ME_TARGET.unitGroup`）。**本票在 `pages/me/unit-group.vue` 上实现**，没有另建 `pages/me/profile/`（避免两个页抢同一个入口）。这条记在 §5 遗留里。

## §2 改了哪些文件（ticket §4.3）

### 2.1 迁移（Flyway）

`code/RuoYi-Vue-Plus/ruoyi-admin/src/main/resources/db/migration/V202609210930__AUTH-GROUP-001-unit-group.sql`（新增，11323 B）

**取号依据 `doc/lint-profile.yaml`**：日期段按任务取、后做的任务版本号一定更大（`out-of-order=false`）→ D1 = `20260921`；HHmm 按域分段 → AUTH = `09xx`；**0930 未被占用，且大于当前最高版本 0920**（AUTH-STAFF-001）→ 不会触发 `FlywayValidateException`（SYS-WEB-001 踩过跨域补号的坑）。菜单号依据同文件：AUTH 域 `5100-5199`。

内容两段：

1. **第一段** = `python3 doc/tools/gen_ddl_pg.py --migration V202609210930__AUTH-GROUP-001-unit-group.sql` 的**逐字节输出**（`t_lqg_source_unit` + `t_lqg_unit_group`：公共 6 字段、部分唯一索引 `WHERE del_flag='0'`、无 `tenant_id` / `del_unique`）。
2. **第二段**（手写，非生成器输出）：菜单 5120「来源单位与组别」（`path='unit'`、`component='lqg/auth/unit/index'`、perms `lqg:auth:unit:list`）、5130「外部用户」（`path='extuser'`、`component='lqg/auth/extuser/index'`、perms `lqg:auth:extuser:list`）+ 11 个 F 按钮；`sys_role_menu` **同时授给 101 与 102**（`(VALUES (101),(102)) CROSS JOIN` + `ON CONFLICT DO NOTHING`），父目录 5100 一起授。

> 菜单实测（accept 3 第 2 段）：
> ```
> $ python3 doc/verify/db.py --sql "SELECT menu_id || ':' || path || ':' || component FROM sys_menu WHERE menu_id IN (5120, 5130)" --col-set "5120:unit:lqg/auth/unit/index,5130:extuser:lqg/auth/extuser/index"
> 5120:unit:lqg/auth/unit/index
> 5130:extuser:lqg/auth/extuser/index
> ```
> 空库端到端：`- Migrating schema "public" to version "202609210930 - AUTH-GROUP-001-unit-group"` → `Successfully applied 1 migration … now at version v202609210930 (execution time 00:00.044s)`。

**按钮权限串清单**（`lqg:auth:unit:{list,add,edit,toggle}` / `lqg:auth:group:{list,add,edit,toggle}` / `lqg:auth:extuser:{list,verify,regroup}`）。

### 2.2 后端（`ruoyi-lqg`，包 `org.dromara.lqg.auth.group` + `org.dromara.lqg.ext`）

| 文件 | 职责 |
|---|---|
| `auth/group/domain/SourceUnit.java` · `UnitGroup.java` | 两张表的实体（`@TableLogic del_flag`，公共字段走 `BaseEntity`） |
| `auth/group/domain/vo/SourceUnitVo.java` · `UnitGroupVo.java` · `ExtUserVo.java` | 工作台三行（组别行带读时 `verifiedCount`、外部用户行带 `selfInput/sampleCount/lastLoginTime`） |
| `auth/group/domain/bo/SourceUnitBo.java` · `UnitGroupBo.java` · `StatusBo.java` · `ExtVerifyBo.java` | 入参（形状与 `doc/api-contract.md` 逐字对齐） |
| `auth/group/guard/UnitGroupRules.java` | 纯规则：名字归一化比较（去空白 + 折连续空白 + 大小写不敏感）、启停合法值、状态取值集合 |
| `auth/group/guard/ExtBindStateMachine.java` | **状态机纯函数**：五条合法边、`targetOf`、`needsSelfInputDecision`、`afterProfileSave`、`participatesInGroupSharing` |
| `auth/group/mapper/SourceUnitMapper.java` · `UnitGroupMapper.java` | 两张表的 mapper |
| `auth/group/service/UnitQueryService.java` | 单位/组别读侧（`groupCount`、`verifiedCount` 读时算）+ 核验用的最小读法（findUnit/findGroup/findUnitByName/findGroupByName） |
| `auth/group/service/SourceUnitService.java` · `UnitGroupService.java` | 单位 / 组别增改 + 启停（不物理删；组别不许挪到别的单位下） |
| `auth/group/service/ExtUserQueryService.java` | 外部用户列表（`sampleCount` 用 `to_regclass` 探针，样本表还没建就回 0） |
| `auth/group/service/ExtUserVerifyService.java` | **核验写侧**：状态机校验 → 自填二选一 → 归口解析 → 单条 UPDATE 落 verified |
| `auth/group/service/ExtProfileUpdateService.java` | `PUT /mp/ext/profile` 的写侧：两套写法互斥、保存即回 pending |
| `auth/group/controller/SourceUnitController.java` · `UnitGroupController.java` · `ExtUserController.java` | `/lqg/auth/unit`、`/lqg/auth/group`、`/lqg/auth/ext-user` 三组端点 |
| `ext/domain/vo/ExtUnitVo.java` · `ExtUnitGroupVo.java` | **对外**选择器的两个 VO（**只有 id 与名称**） |
| `ext/domain/bo/ExtProfileUpdateBo.java` | 对外档案入参 |
| `ext/service/ExtCatalogQueryService.java` | 对外选择器的拼装（**不持有任何 mapper**，见 §4.4） |
| `ext/controller/ExtUnitController.java` · `ExtProfileController.java` | `GET /mp/ext/units`、`PUT /mp/ext/profile` |
| `src/test/java/.../auth/group/guard/ExtBindStateMachineContractTest.java` | **11 个用例**钉住状态机五条边 + 自填二选一 + 命名规则（`Tests run: 11, Failures: 0`） |
| `src/test/java/.../ext/ExtChokepointContractTest.java` | 需求层 fixture 的**逐字节拷贝**（`cmp` 已核，sha256 `6b1da29a…7e812`）——D2 的 L0 门，本票**主动先跑通**，见 §4.4 |

**修改**：

| 文件 | 改动 |
|---|---|
| `ruoyi-lqg/pom.xml` | 测试作用域加 `spring-test`（`ExtChokepointContractTest` 要 `spring-context`/`spring-web` 的扫描与注解类型）。**只加 spring-test，不重复声明 spring-web**：重复声明会把版本降下来把整个模块编挂（实测踩过，见 §7 坑 4） |

**接口清单（本票新增）**：

```
GET  /lqg/auth/unit                      单位列表（含停用，带 groupCount）      lqg:auth:unit:list
POST /lqg/auth/unit                      新增单位                              lqg:auth:unit:add
PUT  /lqg/auth/unit/{id}                 改名 / 改备注                          lqg:auth:unit:edit
PUT  /lqg/auth/unit/{id}/status          启用 / 停用（不物理删）                 lqg:auth:unit:toggle
GET  /lqg/auth/group?unitId=             单位下的组别（带读时 verifiedCount）    lqg:auth:group:list
POST /lqg/auth/group                     新增组别                              lqg:auth:group:add
PUT  /lqg/auth/group/{id}                改名 / 改备注                          lqg:auth:group:edit
PUT  /lqg/auth/group/{id}/status         启用 / 停用（不物理删）                 lqg:auth:group:toggle
GET  /lqg/auth/ext-user/list             外部用户列表（状态 / 单位可筛）         lqg:auth:extuser:list
PUT  /lqg/auth/ext-user/{userId}/verify  核验（approve / reject + 归口决定）     lqg:auth:extuser:verify
GET  /mp/ext/units                       对外单位—组别选择器（只含 active）      登录即可（内外部都行）
PUT  /mp/ext/profile                     外部填 / 改姓名 + 单位 / 组别           登录即可（外部用）
```

### 2.3 前端工作台（`code/plus-ui`）

| 文件 | 说明 |
|---|---|
| `src/views/lqg/auth/unit/index.vue` | **UI:admin.auth.unit**：左单位（名称 / 组别数 / 状态 / 编辑 + 启停）、右该单位的组别（名称 / 已核验人数 / 状态 / 编辑 + 启停）+ 两个弹窗；主从联动（点左栏行 → 右栏换列表） |
| `src/views/lqg/auth/extuser/index.vue` | **UI:admin.auth.extuser**：列表（姓名 / 手机号 / 单位 / 组别 / 核验状态 / 提交样本数 / 最近登录）+ 状态与单位筛选 + 核验弹窗（通过 / 驳回；自填的出「新建单位与组别 / 归并到已有」二选一；已核验的进来是「改归组」） |
| `src/api/lqg/auth/group.ts` | 12 个接口的 TS 封装与类型（含 `unitNameInput`/`groupNameInput`） |
| `src/lang/lqg/auth-group.zh_CN.ts` / `.en_US.ts` | 域内 i18n（键路径 `lqg.auth.unit.*` / `lqg.auth.extuser.*`），落进 `lqg.auth` 与 `auth-staff.*` 合并（**没有改 `src/lang/index.ts`**，AUTH-STAFF-001 的 `split('-')` 解析已就位） |

**构建 + 运行期证据**（不是 grep 出来的）：

```
$ cd code/plus-ui && pnpm build:prod
✓ built in 7.52s
$ grep -c '来源单位与组别' dist/assets/index-JXuQrJZ4.js
1
```

页面截图与机器可读探针见 §3，图里的 `rawI18nKeysLeaked:false` 证明 i18n 合并真的生效（没漏原始 key）。

### 2.4 小程序（`code/miniapp`）

| 文件 | 说明 |
|---|---|
| `src/pages/me/unit-group.vue` | **UI:mp.me.profile**（替换 SYS-MP-001 的占位页）：顶部说明「核验通过后，可与同组同事互看样本」+ 当前状态徽标（含被驳回时的原因）+ 姓名输入 + `UnitGroupPicker` + 保存 / 返回首页 |
| `src/components/biz/UnitGroupPicker.vue` | 单位 → 组别**联动**选择器，末项「列表里没有，手动填写」→ 出两个输入框；两套写法互斥 |
| `src/api/unit-group.ts` | `fetchUnits()` / `saveProfile()` |
| `scripts/shots-unit-group.mjs` · `scripts/probe-unit-group.mjs` | H5 产物 + Playwright 的截图脚本与**功能探针**（16 条断言，`PROBE UNIT-GROUP ALL PASS`） |
| `src/types/components.d.ts` | 构建生成物（upd：登记新的 `UnitGroupPicker` 与 `WdPopup`）。这个文件在 SYS-MP-001 就被跟踪提交过，所以按同一约定提交 |

**生产产物验证**：`pnpm build:mp-weixin` → `DONE Build complete.`；`dist/build/mp-weixin/pages/me/unit-group.json` 的 `usingComponents` 里有 `unit-group-picker`；`app.json` 的 `pages[]` 含 `pages/me/unit-group`；现有 25 个 vitest 用例仍全绿（`Test Files 1 passed / Tests 25 passed`）。

**新写法 i18n / 契约一致性**：`GET /mp/ext/units` 的 data 形状与 `doc/api-contract.md` 第 35 行逐字一致；`PUT /mp/ext/profile` 见 §5.2。

## §3 截图与机器可读证据（`doc/waves/reports/AUTH-GROUP-001/`）

### 3.1 工作台（`doc/waves/reports/AUTH-GROUP-001/probes/group001-admin-shots.mjs`，puppeteer-core + 本机 Chrome）

| 文件 | 内容 |
|---|---|
| `01-unit-group.png` | 侧边栏「人员与单位 › 来源单位与组别 / 外部用户」+ 左单位右组别（A 医院 2 组、B 大学 1 组、已停用单位 0 组、C 研究所 1 组）+ 面包屑 |
| `02-unit-group-switch.png` | 点左栏「B 大学」→ 右栏标题变「该单位的组别（B 大学）」且只剩「类器官课题组」（联动） |
| `03-unit-toggle-confirm.png` | 启停二次确认：「确认把单位「已停用单位」改为启用？停用后它不再出现在小程序的选择器里，已绑定的人不受影响。」 |
| `04-add-group-dialog.png` | 新增组别弹窗（所属单位只读显示当前单位） |
| `05-extuser-list.png` | 外部用户列表 8 行：seed 的 extA/extB/extC/extD/extE/extF + 未填写的新号 + 自填的「郑老师」；「自填」标签 + 斜体单位/组别可见 |
| `06-verify-dialog-create.png` | 自填档案的核验弹窗：黄色提示「通过前必须二选一」+ 新建路径（「把「D 研究院」「免疫组」建成新的启用单位与组别」） |
| `07-verify-dialog-merge.png` | 切到「归并到已有」→ 出单位 / 组别两个下拉 |
| `08-verify-dialog-regroup.png` | 已核验的人进来 = 「改归组」（通过 / 驳回 + 当前单位组别预选） |
| `09-verify-dialog-reject.png` | 驳回路径：原因必填（占位「请填写驳回原因（必填，外部人员能看到）」） |

机器可读探针：`probe-sidebar.json`、`probe-unit-group.json`、`probe-unit-group-switch.json`、`probe-unit-toggle-confirm.json`、`probe-extuser-list.json`、`probe-verify-dialog-{create,merge,regroup,reject}.json`。

**ticket §4.1 要求的「四个外部身份在「外部用户」页的截图」**：`05-extuser-list.png` 一屏里有 extA 王医生（待核验）、extE 周医生（已驳回，因我为了让驳回态可见先驳回了一次）、extF 吴同学（已核验）、新号 `wx_13800000099`（未填写）；extB/extC/extD 同屏可见。探针里的逐行文字是同源证据：

```
rows: [["王医生","13800000011","A 医院","消化内科组","待核验",…],
       ["陈医生","13800000012","A 医院","肝胆外科组","已核验",…],
       ["赵医生","13800000013","A 医院","消化内科组","已核验",…],
       ["孙老师","13800000014","B 大学","类器官课题组","已核验",…],
       ["周医生","13800000015","A 医院","肝胆外科组","已驳回",…],
       ["吴同学","13800000016","C 研究所","肿瘤组","已核验",…],
       ["wx_13800000099","13800000099","未填写","未填写自填","未填写",…],
       ["郑老师","13800000098","D 研究院","免疫组自填","待核验",…]]
```

### 3.2 小程序（`pnpm build:h5` + Playwright，`code/miniapp/scripts/shots-unit-group.mjs`）

| 文件 | 内容 |
|---|---|
| `10-mp-profile-verified.png` | 外部已核验进来：说明条 + 「已核验」徽标 + 姓名「王医生」+ 单位「A 医院」/ 组别「肝胆外科组」 |
| `11-mp-unit-sheet.png` | 单位选择面板：A 医院（当前项高亮）/ B 大学 / **列表里没有，手动填写** |
| `12-mp-group-sheet-linked.png` | 选 B 大学后组别面板只剩「类器官课题组」+ 手动填写（联动） |
| `13-mp-manual-input.png` / `13b-mp-manual-filled.png` | 末项「手动填写」→ 出「单位名 / 组别名」两个输入框（占位「列表里没有，请填写单位全称」「请填写组别名」）→ 填入 C 研究所 / 肿瘤组 |
| `14-mp-after-save-me-pending.png` | 保存后回「我的」，单位与组别行的徽标 = **待核验**（琥珀） |
| `15-mp-profile-rejected.png` | 被驳回：徽标「已驳回」+「驳回原因：组别名请写全称，如「肝胆外科组」」+ 自填名回填进两个输入框 |
| `16-mp-home-unbound-hint.png` | 首页提示条「补充单位与组别，可与同组同事互看样本」 |
| `17-mp-hint-to-unit-group.png` | 点提示条 → 落到 `/pages/me/unit-group`（`hasProfile:true`） |

**功能探针**（`node scripts/probe-unit-group.mjs`，16 条断言，比截图更强）：

```
PASS 姓名框预填 = 王医生 — 王医生
PASS 单位面板 = 两个启用单位 + 手动填写 — ["A 医院","B 大学","列表里没有，手动填写"]
PASS 选完单位后面板关闭
PASS 换单位后组别被清空
PASS 组别面板只剩 B 大学的组别（联动） — ["类器官课题组","列表里没有，手动填写"]
PASS 列表项路径请求体 = {realName, unitId, groupId} — {"realName":"王医生","unitId":9000009002,"groupId":9000009103}
PASS 保存后跳回「我的」
PASS 「我的」显示待核验
PASS 两个输入框有占位文案 — ["列表里没有，请填写单位全称","请填写组别名"]
PASS 手动填写路径请求体 = {realName, unitNameInput, groupNameInput} — {"realName":"王医生","unitNameInput":"C 研究所","groupNameInput":"肿瘤组"}
PASS 被驳回显示徽标「已驳回」
PASS 被驳回显示原因
PASS 被驳回时自填名回填到输入框 — ["C 研究所","肿瘤组"]

PROBE UNIT-GROUP ALL PASS
```

> 微信开发者工具在 subagent 沙箱里跑不通（SYS-MP-001 WARN-1：要写 `~/Library/Application Support/微信开发者工具/**` + 扫码登录），所以小程序侧的证据是 **H5 产物 + Playwright**，与 SYS-MP-001 同一手法。

## §4 accept 逐条 ✅ + 关键输出

### 4.0 关于 `--fresh-module`（本 agent 沙箱限制）

`doc/verify/api.sh` 第 71 行用 `ps -o lstart=`，在本 subagent 沙箱里是 `/bin/ps: Operation not permitted` → `exit 2`。按规矩**没有改 `api.sh`**，用等价证据覆盖那道守卫的**两个半边**（源码不得比 jar 新 + 进程不得早于 jar）：

```
jar mtime                : 2026-09-21 16:20:07（epoch 1789978807）
lqg src newer than jar   : [空]        ← 没有比 jar 新的 ruoyi-lqg 源码（src/main）
ruoyi-admin src newer    : [空]        ← 同上
8081 PID                 : 45987
该 PID 打开的文件含       : ruoyi-admin/target/ruoyi-admin.jar   ← 就是上面那个 jar
后端日志 mtime            : 2026-09-21 16:30:23（epoch 1789979423 > jar 的 1789978807 = 进程起于打包之后）
嵌套 jar 复核             : BOOT-INF/lib/ruoyi-lqg-5.5.3.jar 内含 org/dromara/lqg/{auth/group,ext}/ 下 77 个条目
                            （含 ExtBindStateMachine / UnitQueryService / ExtCatalogQueryService）
```

**复跑脚本随报告落盘**：`doc/waves/reports/AUTH-GROUP-001/accept-runners/`（`group001-acc1/2/3.sh` + `README.md`）。三条脚本与本次实跑的 `.tmp` 版本逐字相同，从工作区根执行即可；差异只有「去掉 `--fresh-module ruoyi-lqg`」这一处（acc2）。

### 4.1 accept 1 · DDL —— ✅

```
$ bash doc/waves/reports/AUTH-GROUP-001/accept-runners/group001-acc1.sh
✓ 2 张表与 SSOT 逐列相符（含公共字段 6 个、部分唯一索引、普通索引）
1
True
ACCEPT-1 EXIT=0
```

三段分别是：`ddl_vs_ssot.py` 的两张表逐列相符（含公共字段 6 个、部分唯一索引、普通索引）、`flyway_schema_history` 里 `V20260921093%__AUTH-GROUP-001-%` 恰好 1 行且 success、`uk_unit_group` 的 `indexdef` 同时含 `(unit_id, group_name)` 与 `WHERE (del_flag = '0'`。

**counterfeit 点名的形态逐条排掉**：

```
$ python3 doc/verify/db.py --sql "SELECT indexdef FROM pg_indexes WHERE indexname='uk_unit_group'"
CREATE UNIQUE INDEX uk_unit_group ON public.t_lqg_unit_group USING btree (unit_id, group_name) WHERE (del_flag = '0'::bpchar)
$ # 组别唯一索引漏了 unit_id → A 医院有了「肝胆外科组」B 大学就建不了（反证：不同单位可重名）
$ bash doc/verify/api.sh --as admin POST /lqg/auth/group '{"unitId":9000009001,"groupName":"肝胆外科组"}'
500	该单位下已有组别「肝胆外科组」
$ bash doc/verify/api.sh --as admin POST /lqg/auth/group '{"unitId":9000009002,"groupName":"肝胆外科组"}'
200	操作成功
```

另外两条与软删相关的（accept 1 的 DDL 只断索引定义，语义用直连 SQL 补）：

```
$ # 软删后可重建同名（部分唯一索引不带 del_flag 就会红）
$ psql … "INSERT …(9000009099,'临时单位X',…'0')" ; "UPDATE … SET del_flag='1' WHERE id=9000009099" ; "INSERT …(9000009098,'临时单位X',…'0')"
2                       ← 同名两行共存（一行 del_flag='1'）
$ psql … 组别同理（9000009199 软删 → 9000009198 同名可建）
2
```

### 4.2 accept 2 · STATE —— ✅

```
$ bash doc/waves/reports/AUTH-GROUP-001/accept-runners/group001-acc2.sh
pending
true
verified|C 研究所|active|肿瘤组
true
pending|9000009102|-
ACCEPT-2 EXIT=0
```

逐段对应（原文 9 段，`&&` 链）：

| 段 | 命令 | 输出 | 说明 |
|---|---|---|---|
| 2 | `--as staff --bizcode PUT …/9000000116/verify '{"action":"approve"}'` | `500 该档案的单位 / 组别是外部自填的，核验通过前必须二选一…` | extF 是自填档案，approve 不带归口 → 拒 |
| 3 | `db.py … user_id=9000000116` `--eq pending` | `pending` | **拒了之后库里一字未变** |
| 4 | `--bizcode PUT …/9000000115/verify '{"action":"reject"}'` | `500 驳回必须填写原因` | reject 不带 reason → 拒 |
| 6 | `PUT …/9000000116/verify '{"action":"approve","createUnit":true,"createGroup":true}' \| jq -e '.code==200'` | `true` | 「新建」路径 |
| 7 | JOIN 单位 + 组别断言 `--eq "verified\|C 研究所\|active\|肿瘤组"` | `verified\|C 研究所\|active\|肿瘤组` | 新单位 / 新组别都是 `active`，档案 `verified` |
| 8 | `--as extA PUT /mp/ext/profile '{…groupId:9000009102}' \| jq -e '.code==200'` | `true` | 外部改自己的组别（extA 本来是 verified / 9000009101） |
| 9 | `… SELECT bind_status\|\|'\|\|'group_id\|\|'\|\|'COALESCE(verified_by::text,'-') … user_id=9000000111 --eq "pending\|9000009102\|-"` | `pending\|9000009102\|-` | **改单位或组别立刻回 pending，且 `verified_by` 清空** |
| 10 | `--as extA --bizcode PUT /mp/ext/profile '{…groupId:9000009103}'` | `500 组别「类器官课题组」不属于单位「A 医院」` | 单位 / 组别不匹配 → 拒（且上面第 9 段的库内状态没被这次拒动过） |
| 11 | `reseed.sh --yes` | （静默） | 收尾 |

**counterfeit 逐条排掉**：

```
# 「approve 不校验自填档案」→ 第 2 段拿到 200 红 ✓ 已绿
# 「reject 不要求原因」→ 第 4 段拿到 200 红 ✓ 已绿
# 「新建只建了单位没建组别 / 组别挂错单位」→ 第 7 段 JOIN 不出行红 ✓ 已绿（JOIN 条件带 g.unit_id=u.id）
# 「外部改组别后状态仍是 verified」→ 第 9 段红 ✓ 已绿
# 「组别属于 B 大学却和 A 医院一起提交」→ 第 10 段必须拒绝 ✓ 已绿
```

**「只看业务码会漏」的那条也排掉了**：每段「被拒」后面都紧跟库内断言（第 3、9 段），第 2 段拒的是 `pending` 原值、第 9 段是换组后的新值 —— 「先落盘再返回 400」会在第 3 段红。另外用 20 条对抗性探针补了 accept 没覆盖的边（§4.4 的 P1-P22）。

### 4.3 accept 3 · MENU —— ✅

```
$ bash doc/waves/reports/AUTH-GROUP-001/accept-runners/group001-acc3.sh
true
5120:unit:lqg/auth/unit/index
5130:extuser:lqg/auth/extuser/index
true
ACCEPT-3 EXIT=0
```

第 1 段是整条 jq（`.code==200` + 单位集合恰为 `["A 医院","B 大学"]` + A 医院的组别恰为 `["消化内科组","肝胆外科组"]` + 响应体里**任何对象的键名都不含 `count`/`Count`**）：

```
$ bash doc/verify/api.sh --as extF GET /mp/ext/units | jq -c .
{"code":200,"msg":"操作成功","data":[
  {"unitId":9000009001,"unitName":"A 医院","groups":[{"groupId":9000009101,"groupName":"肝胆外科组"},{"groupId":9000009102,"groupName":"消化内科组"}]},
  {"unitId":9000009002,"unitName":"B 大学","groups":[{"groupId":9000009103,"groupName":"类器官课题组"}]}]}
```

**seed 的「已停用单位」（9000009003）不在里面** —— 这正是第 1 段「单位集合不等就红」的病灶被排掉的证据。

第 3 段用 `--as staff`（= 102 lqg_internal）取路由，命中 2 条 —— **菜单同时授 101/102** 的口径被机器断住了。

### 4.4 追加证据（accept 之外的机器证据）

**(a) Java 契约测试 —— 25 个用例全绿，含 D2 的 L0 门**

```
$ (cd code/RuoYi-Vue-Plus && mvn -pl ruoyi-modules/ruoyi-lqg test -s <ws>/.mvn-settings.xml -Dmaven.repo.local=<ws>/.m2repo -Duser.home=<ws>/.buildhome)
[INFO] Tests run: 11, Failures: 0, Errors: 0, Skipped: 0 -- ExtBindStateMachineContractTest     ← 本票
[INFO] Tests run:  6, Failures: 0, Errors: 0, Skipped: 0 -- StaffGrantRulesContractTest
[INFO] Tests run:  4, Failures: 0, Errors: 0, Skipped: 0 -- MockLoginGuardContractTest
[INFO] Tests run:  4, Failures: 0, Errors: 0, Skipped: 0 -- ExtChokepointContractTest          ← D2 的 L0 门，本票先跑通
[INFO] Tests run: 25, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS

$ cmp doc/verify/fixtures/java/ExtChokepointContractTest.java \
      code/RuoYi-Vue-Plus/ruoyi-modules/ruoyi-lqg/src/test/java/org/dromara/lqg/ext/ExtChokepointContractTest.java
（无输出；exit 0）  sha256=6b1da29a7c12744798910c115be8981745253335a8db4c8b68a992a88567e812
```

> ★ **这不是「顺手」**：ADR-0004 的四个不变量里 i4 规定 **ext 包里除 `ExtScopeServiceImpl` 外任何类都不得持有 `*Mapper` 字段**。我第一版把「选择器 / 档案保存」两个 service 写在 `org.dromara.lqg.ext.service` 里（各持一个 mapper），跑这个 fixture **直接红**；改成「读写在 `auth.group` 包，ext 包只拼装 + 转发」后全绿。**下游 EXT 域各票写 `/mp/ext/**` 时先跑这个 fixture，别等 D2 返工。** 另：i1（路径都以 `/mp/ext` 开头）也是靠它兜住的 —— 本票两个 controller 都声明 `@RequestMapping("/mp/ext")`。

**(b) 对抗性探针 22 条**（`.tmp/group001-probe.sh`，accept 没直接断的形态）

```
P1  verified→rejected 非法            → 500 当前状态是「verified」，不能执行 reject… ；库内 verified|-（不变）
P2  rejected→verified 非法            → 500 当前状态是「rejected」… ；库内 rejected|组别名不全（不变）
P3  unbound 自填不能直接核验           → 500 必须二选一… ；库内 pending（不变）
P4  reject reason 全空白也拒绝         → 500 驳回必须填写原因 ；库内 pending（不变）
P5  只给 createUnit 不给 createGroup   → 500 必须二选一… ；库内 pending|-（不变）
P6  归并时组别不属于单位               → 500 组别「类器官课题组」不属于单位「A 医院」；库内 pending|-（不变）
P7  单位名全库唯一（含大小写 / 空白归一）→ 500 单位「A 医院」已存在… ；500 单位「a 医院」已存在… ；库里仍 1 行
P8  同单位组别名唯一 / 不同单位可重名    → 500 该单位下已有组别「肝胆外科组」；200 操作成功；库里 9000009001+9000009002 各一行
P9  组别停用：已绑定的人不受影响         → 两条档案仍 verified；对外选择器 A 医院只剩 ["消化内科组"]
P10 单位停用：选择器不再返回它           → ["A 医院"]
P11 外部选已停用的组别                   → 500 该组别已停用，不能选择 ；库内仍 verified（没被写坏）
P12 外部把组别挪到别的单位               → 500 组别「类器官课题组」不属于单位「A 医院」；库内 verified|9000009101（不变）
P13 两套写法互斥（列表项为准、自填清空）   → 200；库内 pending|9000009001|9000009102|NULL|NULL
P14 从列表项切到手动填写（两个 id 清空）   → 200；库内 pending|NULL|NULL|X 医院|Y 组
P15 外部不能核验别人 / 不能看外部用户列表  → 403 没有访问权限…（两条都 403）
P16 组别不能挪到别的单位（PUT 换 unitId） → 500 不能把组别挪到别的单位下…；库内 unit_id 仍 9000009001
P17 单位 / 组别没有 DELETE 端点（不物理删）→ 405 Request method 'DELETE' is not supported；行还在（del_flag='0'）
P18 软删后重建同名单位（部分唯一索引语义）→ 2（同名两行共存）
P19 软删后重建同名组别                  → 2
P20 （见下方「P20 的正路」：外部先保存 → 核验 → 停用单位 → 库内一字未动）
P21 归并到已有后自填名必须清空            → verified|NULL|NULL   ← 这一条**抓到了实现里的一个真 bug**，见 §7 坑 2
P22 改归组 verified→verified              → 200；库内 verified|9000009102|9000000101（状态不变、group_id 变了）
```

**探针脚本随报告落盘**：`doc/waves/reports/AUTH-GROUP-001/probes/group001-probes.sh`（22 条，从工作区根或任意目录执行均可）。P18/P19 的写操作走 `docker exec` 直连 psql（`db.py` 是只读执行器，遇到非 SELECT/WITH 会 `[error] 只接受 SELECT / WITH 开头的查询——断言不改库` —— 那是护栏不是失败）：

```
$ "${PSQL[@]}" -c "INSERT …(9000009099,'临时单位X',…'0')" -c "UPDATE … SET del_flag='1' WHERE id=9000009099" \
               -c "INSERT …(9000009098,'临时单位X',…'0')" -c "SELECT count(*) FROM t_lqg_source_unit WHERE unit_name='临时单位X'" …
2
$ （组别同理）2
```

**P20 的正路**（探针里最初那版预设错了：想直接 approve 一条 `rejected` 档案，被状态机正确拒掉 —— 那本身是 P2 的结论，不是 P20 要证的）：

```
$ --as extE PUT /mp/ext/profile '{"realName":"周医生","unitId":9000009002,"groupId":9000009103}' → 200
$ --as staff PUT …/9000000115/verify '{"action":"approve"}'                                     → 200
$ db: verified|9000009002|9000009103
$ --as admin PUT /lqg/auth/unit/9000009002/status '{"status":"disabled"}'                       → 200
$ db: verified|9000009002|9000009103      ← 停用后已核验的人一字未动
$ --as extF GET /mp/ext/units | jq -c '[.data[].unitName]'                                       → ["A 医院"]
```

**(c) 关键 bug 的负对照（能红的探针）**

| bug | 探针 | 修复前 | 修复后 |
|---|---|---|---|
| 清不掉 `verified_by`（`updateById` 忽略 null） | accept 2 第 9 段 | `pending\|9000009102\|9000000100`（红） | `pending\|9000009102\|-`（绿） |
| 归并后自填名不清 | P21 | `verified\|C 研究所\|肿瘤组`（红） | `verified\|NULL\|NULL`（绿） |
| 小程序面板不关 / 点不动保存 | `probe-unit-group.mjs` | `FAIL 选完单位后面板关闭` 等 3 条 | 16/16 PASS |

### 4.5 收尾状态

```
$ python3 doc/verify/db.py --sql "SELECT bind_status||':'||count(*) FROM t_lqg_ext_profile WHERE del_flag='0' GROUP BY bind_status"
pending:2
verified:4
$ python3 doc/verify/db.py --sql "SELECT count(*) FROM sys_user WHERE user_id > 2000000000000000000"
2
```

库里另有 **2 个雪花 id 的临时外部账号**（`13800000099` / `13800000098`，截图与探针用的测试号）。`reseed.sh` 只清 `9000000000-9000009999` 段，清不掉它们（AUTH-STAFF-001 WARN-7 已记过）。收尾清法：`bash doc/waves/tools/clean-orphan-accounts.sh --yes`。

## §5 遗留与 raise（ticket §4.5）

### 5.1 越出 `touches` 的改动（3 处，都请主会话确认边界）

| # | 文件 | 为什么必须改 |
|---|---|---|
| 1 | `code/RuoYi-Vue-Plus/ruoyi-modules/ruoyi-lqg/pom.xml` | `touches` 只列了 `.../auth/group/**`（Java 包），pom 不在里面。但需求层给的 `ExtChokepointContractTest`（§4.4）要 `spring-context` / `spring-web` 的扫描器与注解类型，**不加 `spring-test` 这个 fixture 编不过**。加了 `spring-test`（test scope，版本交给上游依赖管理） |
| 2 | `code/miniapp/src/types/components.d.ts` | 构建生成物（auto-import 插件写）。SYS-MP-001 已把它提交进仓库，本票的 `UnitGroupPicker` / `WdPopup` 注册必须跟着提交，否则下一个人 `pnpm type-check` 会报组件未声明 |
| 3 | `code/miniapp/scripts/{shots-unit-group,probe-unit-group}.mjs` | `touches` 只列到 `src/pages/me/profile/**` 与 `src/api/auth.ts`。截图 / 探针脚本按 SYS-MP-001 的先例放在 `scripts/` 下（那一票的 `scripts/shots-h5.mjs` 也提交了） |

**没有动**：上游 `ruoyi-system` / `ruoyi-common-*` 一个字节、`src/lang/index.ts`（AUTH-STAFF-001 已就位）、`doc/verify/**` 的只读区。

### 5.2 与 `doc/api-contract.md` 的差异（2 处，都是契约侧的 doc-drift）

| # | 契约写的 | 实际实现 | 判断依据 |
|---|---|---|---|
| 1 | 第 38 行「`GET/POST/PUT/DELETE /lqg/auth/unit`、`/lqg/auth/group`」 | **没有 DELETE**；启停走 `PUT …/{id}/status`（`{"status":"active"\|"disabled"}`），权限串是 `lqg:auth:unit:toggle` / `lqg:auth:group:toggle` | ticket §2.2 与权威 `FLOW:F-AUTH-03.step1` 都写「启用 / 停用（不物理删）」；ticket §1 的口径是「已有人绑定的组别停用后已绑定的人不受影响」——物理删会让已绑定的档案指向不存在的行。契约是只读区，未改 |
| 2 | 第 38 行「组别列表带 `verifiedCount`」 | 工作台列表**带**；`GET /mp/ext/units` 用的是**另一个** VO（`ExtUnitGroupVo`，只有 id 与名称） | accept 3 第 1 段明确要求对外响应体里 `count|Count` 命中数为 0 |

### 5.3 口径上没把握 / 需要确认的两点

1. **「内部核验人能不能把一个 pending 档案归并到一个已停用的单位」**：我**允许**（`requireUnitAndGroupMatch` 只查存在性与归属，不查 `unit_status`）。理由：ticket §2.2 说停用是「不再出现在选择器里」，没说内部不许挂；而「已绑定的人不受影响」这条口径下，内部把待核验的人挂到停用单位，效果与「先挂上再停用」等价。若甲方要「只许归并到启用单位」，加一句状态校验即可（`P20` 那组探针就是为此准备的）。
2. **驳回后单位 / 组别要不要清空**：ticket §2.2 只写 `reject` 写 `reason` 并置 `rejected`，**没说要清 `unit_id`/`group_id`**。我**保留**了原值（seed 的 extE 驳回后仍带 `unit_id=9000009001/group_id=9000009101`），因为「驳回」是否定这一次的归口而不是抹掉他填过的东西，而且 AUTH-EXT-001 的可见范围只认 `verified`，留着不影响隔离。若甲方要「驳回即清空，逼他重选」，那是另一条口径。

### 5.4 明确没做（ticket §3 边界，逐条核对过）

不算可见范围（AUTH-EXT-001）；不做邀请码；不做单位管理员自助管理本单位成员；不做短信 / 订阅消息通知核验结果；单位不挂地址 / 联系人等字段。

### 5.5 ticket `touches` 偏差（调度器已知，照办）

`touches` 写 `code/miniapp/src/pages/me/profile/**`，实际在 `code/miniapp/src/pages/me/unit-group.vue` 上实现（SYS-MP-001 建的占位页 + 「我的」页 `ME_TARGET.unitGroup` 已指向它）。**没有另建 `pages/me/profile/`**，避免两个页抢同一个入口。默认不提交对 ticket 的修改（ticket 不明确禁止），此处只记录。

## §6 验证用的长进程（ticket §4.6）

- **后端 java（8081）：已关**（`pkill -9 -f 'ruoyi-admin.jar'`，收尾 `lsof -ti tcp:8081` 已空）。起停脚本 `.tmp/run-backend.sh`（gitignore）可复用。
- **前端 vite dev server（8082）：已关**（`lsof -ti tcp:8082` 已空）。
- **小程序 h5 / mp-weixin 构建产物**：`code/miniapp/dist/`（gitignore，不进仓库）。
- **没留下任何监听**：8080（Kevin 本机日常）/ 8083 / 8099 全程没碰；5433 / 6380 是 docker 容器（`lqg-dev-postgres` / `lqg-dev-redis`，D1 前就在跑，留着给后续 ticket）。
- **DB 收尾** = `reseed.sh --yes` 后的干净 seed + 2 个截图用临时外部账号（清法见 §4.5）。
- /tmp 的截图工具链依赖（`/tmp/shots/node_modules` 里的 puppeteer-core）不在仓库里；脚本本体随报告落盘：`doc/waves/reports/AUTH-GROUP-001/probes/group001-admin-shots.mjs`（工作台侧）、`code/miniapp/scripts/{shots-unit-group,probe-unit-group}.mjs`（小程序侧）。

## §7 坑与解法（3-5 行，给下游）

1. **MyBatis-Plus 的 `updateById` 默认忽略 null 字段（`FieldStrategy.NOT_NULL`）→ 「清空」根本清不掉**。实测：`bind_status` 已经回到 `pending`，`verified_by` 还是老的核验人，而 accept 2 第 9 段恰好断这一格（`COALESCE(verified_by::text,'-')` 要求 `-`）。**凡是要把某列写成 NULL（清核验痕迹 / 清自填名 / 清备注 / 清驳回原因），必须用 `LambdaUpdateWrapper` 显式 `.set(col, null)`**，`updateById` 只适合「只改非空值」的场景。本票三处写侧（档案保存 / 核验通过 / 驳回）都改成了 UpdateWrapper 并各带一条能红的探针（P21 + accept 2 第 9 段）。
2. **「归并到已有」也要清自填名**：第一版只在「新建」路径清 `unit_name_input` / `group_name_input`，结果归并成功后列表里还挂着过期的自填名（`C 研究所 / 肿瘤组`），前端继续按「自填」渲染斜体 + 标签。判据改成「档案里本来有自填名（不管走新建还是归并）就清」。**凡是有「临时名 → 正式名」两段式归口的地方，都要问自己：归口成功后那个临时名还在不在。**
3. **ADR-0004 的 i4 比看起来严**：`ExtChokepointContractTest` 会**扫整个 ext 包**，任何类（不只是 controller）持有 `*Mapper` 字段都红。所以「对外 VO 的拼装」和「读库」必须拆在两个包：读写在 `auth.group`（service 里持 mapper），ext 包只留「拼装 + 转发 + Ext*Vo」。**下游写 `/mp/ext/**` 的票：先把 fixture 拷进测试树跑一遍再接业务**，否则会在 D2/返工时才发现，而那时 ext 包已经被写满。
4. **在模块 pom 里「重复声明」一个已由别的模块传递进来的依赖会静默降版本**：我给 `ruoyi-lqg` 加 `spring-test` 时顺手也声明了 `spring-web`（想着「测试要注解类型」），Maven 就近选了根 pom 的 `spring-web 6.2.15`，而 `ruoyi-common-web` 带进来的是 6.2.x 更高版 → **整个模块的 `@RequestMapping` / `@RequestBody` 全部 cannot find symbol**（报错满天飞、看根因要翻到日志最上面）。只留 `spring-test`（不带版本）即好。**加 test 依赖前先 `mvn dependency:tree` 看一眼谁已经带进来了。**
5. **`api.sh` 的 token 缓存（`$TMPDIR/lqg-verify-token-*`）只按 mtime 判新鲜**：接手时被它误导过一次（陈旧 token → 假 401，看着像「实现坏了」）。三条 accept 复跑脚本开头都 `rm -f` 了。另外 **accept 的长链不能直接丢给 `set -e`**（非末尾位置失败会静默短路 + `exit 0`），必须包进函数判**整条链的 rc**（AUTH-STAFF-001 坑 3，本票三条脚本都这么写）。

## §8 WARN 清单（交主会话落 `issue add`）

| # | severity | type | 标题 | 影响范围 / 方案 |
|---|---|---|---|---|
| WARN-1 | S2 | harness | `doc/verify/api.sh --fresh-module` 的 `ps -o lstart=` 在 subagent 沙箱被禁，accept 原样跑 `exit 2` | **已连续五张票命中**（SYS-BASE-001 / AUTH-LOGIN-001 / SYS-WEB-001 / AUTH-STAFF-001 / 本票）。只影响 subagent 沙箱，人工 / CI 正常。本票用「`find … -newer jar` 为空（lqg + admin 两侧）+ `lsof` 拿 PID + 进程持有该 jar + 日志 mtime 晚于 jar + 嵌套 jar 内含本票 class」五项等价替代。方案：把启动时间换成 `lsof` / 日志 mtime 的兼容写法，或给 `api.sh` 加「跳过启动时间守卫」的开关（改动属验收执行器，五张票都按规矩没动 `api.sh`）。 |
| WARN-2 | **S1** | counterfeit-risk | **MyBatis-Plus `updateById` 忽略 null** 会让状态机里的「清空」全部失效，而 accept 恰好断在被清的列上 | 影响**所有**「改状态要顺带清字段」的票（核验 / 撤销 / 驳回 / 退回 / 重填）：表现是「状态对了、痕迹还在」，而只看业务码的断言是绿的。本票用 `LambdaUpdateWrapper` 修掉并留了能红的探针（accept 2 第 9 段 / P21）。方案：① ticket 生成器加一条「凡断言 `COALESCE(col,'-')` 或空白字段的票，实现方必须用 UpdateWrapper 并写负对照」；② 或在模块里统一把 `FieldStrategy` 默认值调成 `IGNORED`（**别这么做**，那会让「只改非空值」的写法处处误清）。 |
| WARN-3 | S2 | doc-drift | `doc/api-contract.md` 第 38 行为单位 / 组别列了 `DELETE`，但 ticket §2.2 与权威 `FLOW:F-AUTH-03.step1` 都是「只启用 / 停用、不物理删」 | 影响照契约写实现的后续票（会实现出一个物理删端点，破坏「已绑定的人不受影响」）。本票按 ticket + 权威实现（`PUT …/{id}/status`）。方案：契约那一行改成 `GET/POST/PUT /lqg/auth/unit`、`PUT …/{id}/status`，并注明「不物理删」。 |
| WARN-4 | **S1** | counterfeit-risk | **ADR-0004 的 i4（ext 包里除 `ExtScopeServiceImpl` 外不得持有 `*Mapper`）比 ticket 正文更严**，而它只在 `doc/verify/fixtures/java/ExtChokepointContractTest.java` 里，D1 的 AUTH-GROUP-001 正文没有点名要求跑它 | 本票第一版把两个 service 放进 ext 包（各持一个 mapper）→ fixture 直接红。**D1 之后的 EXT 域票（AUTH-EXT-001/002/003、SAMPLE/EMBED/CRYO 的 `/mp/ext/**`）如果不先跑这个 fixture，会在 D2/返工时才发现**，而那时 ext 包已写满。本票主动把 fixture 拷进 `src/test/java/.../ext/`（`cmp` 逐字节）并跑绿。方案：把「EXT 域票的 accept 必须含 `ExtChokepointContractTest`」写进 ticket 生成器，或在 `touches` 里显式给 ext 包的测试目录。 |
| WARN-5 | S3 | doc-drift | 接收侧「归并到已有时要不要清自填名」ticket 正文没写，而实现上**必须清**（否则列表挂着过期自填名、前端继续按自填渲染） | 影响任何「临时值 → 正式值」两段式归口的票（SAMPLE/EMBED 的 `*_name` 快照同理）。本票按「归口成功即清」实现并加了断言。方案：把这条口径写进 `UI:admin.auth.extuser` 的 body 或 field-ssot 的注释。 |
| WARN-6 | S3 | clarify | **内部核验人可以把 pending 档案归并到一个已停用的单位**（本票刻意允许，见 §5.3） | 若甲方要「只许归并到启用单位」，加一句状态校验即可。方案：请主会话确认这一条口径；确认前保持「只查存在性与归属」。 |
| WARN-7 | S3 | harness | `doc/verify/reseed.sh` 清不掉雪花 id 的 `sys_user`（本票的截图 / 探针测试号会留 2 个） | 同 AUTH-STAFF-001 WARN-7（已记过一次），本票复核仍然成立。方案：`reseed.sh` 增加一段「删掉全部非 seed 段账号」，或把 `doc/waves/tools/clean-orphan-accounts.sh` 收进标准收尾步骤。 |
| WARN-8 | S3 | harness | 本 headless 环境里**点侧边栏 / `router.push` 之后 `.app-main` 会停在 `<!---->`**（layout 的 `router-view` 不换），而整页加载到目标路由是好的 | 只影响 subagent 的截图工具链（SYS-WEB-001 / AUTH-STAFF-001 的截图当时没遇到，可能与当次启动状态有关）。本票的截图脚本改成「登录后 `page.goto(BASE + 路由)` 整页加载」，耗时多几秒但稳。方案：给 subagent 的截图脚手架固化这条导航方式，别浪费轮次去 debug 框架行为。 |

> 已在上游验收报告里记过、本票**不重复计数**的既有 WARN：Maven 三参数（AUTH-LOGIN-001 WARN-2）、surefire `groups` 假绿（AUTH-LOGIN-001 WARN-3）、`cleanOnlineUser` 漏踢（AUTH-STAFF-001 WARN-2）、`doc/api-contract.md` 未列 `GET /lqg/auth/staff/check`（AUTH-STAFF-001 WARN-3）、`api.sh` token 缓存（AUTH-STAFF-001 WARN-4）、`--as extA` 的重复手机号账号（SYS-MP-001 WARN-2，本票实跑未复现 —— 库里只有一个 `13800000011`）、微信开发者工具跑不通（SYS-MP-001 WARN-1）。
