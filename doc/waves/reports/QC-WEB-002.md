# QC-WEB-002 · 工作台 · 质控文档编辑页（二）：类器官质控表页签、类器官质量评分表页签

- **票**：`doc/tickets/QC-WEB-002/prompt.md`（track QC / phase D6 / size M）
- **分支**：`task/D6`（未切分支、未 push、未合分支、未动 `doc/waves/state.json` 与 `_manifest.json`）
- **状态**：**done** —— `accept 2/2` 全绿（连跑 3 次一致）；另加 25 项人眼验收（真浏览器 + 真字典 + 真库）
  与 6 项 counterfeit 探针全绿
- **完成时间**：2026-09-22
- **实现者**：impl subagent（QC-WEB-002）

---

## 0. 状态自检（3 级）

- ✅ **PASS** 分支 = `task/D6`（调度器注入，未动）
- ✅ **PASS** `depends_on` 全部 done：**QC-WEB-001**；其「给下游的坑」12 条逐条读过并对齐
  （第 1 条路由 `/qc-console/qc-editor?sampleId=…`、第 2 条组件可直接复用且 `images` 是 map、
  第 3 条三个 tab 骨架 `TABS.docKind` 已就位、第 6 条保存是补丁语义整份发、第 8 条只解绑不删 OSS）
- ✅ **PASS** 扫 `doc/change-log.md`：**无一条 CR 涉及 QC 域页面口径**（最新 CR-20260921-08 只动小程序
  视觉，明说「网页工作台不受影响」）
- ✅ **PASS** 逐条取权威（垫片 `python3 doc/authority/authority_lint.py show <锚>`，5 条全 `status: active`）：
  - `UI:admin.qc.editor`（左编辑右预览、页头摘要条、三页签带徽标、**底部附件区**、四组单选 + 自动显示各项分值与合计）
  - `FLOW:F-QC-01.step3`（类器官质控表：样本观察情况图片位 1-3 张 + 五栏文本）、`.step4`（评分表：四个变量各选一档；
    **后端按字典 remark 回填分值，前端传的分值忽略；任一项未选则合计为空**；`produces` 明写「不产出质量等级结论」）
  - `FIELD:t_lqg_qc_score.total_score`（★ 合计 = 四项之和，任一项未选为空；**不出「偏差/中等/良好」结论**）、
    `FIELD:t_lqg_qc_organoid.planned_drug_screen`（纯文本，不延伸成药敏模块）
  - **无「正文与蓝图冲突」项**（唯一的偏差是蓝图比正文**多**了「底部附件区」，照蓝图做了，见 §5 WARN-3）
- ✅ **PASS** 环境可用：后端 8094 / 工作台 8093 起得来；`lqg-dev-gotenberg` 全程 running（未停）
- ✅ **PASS** 上游口径照用：`PUT …/score` 只收四个 `*Level`、`*_score` 由后端按 `sys_dict_data.remark`
  回填、四项齐才给 `total_score`、`0` 分档 = 「选了 0 分」（全链路 `Integer`，null=没选）；
  本票**没碰** `doc_status`（口径悬案 issue #228 留给 DOC-PUBLISH-001）

**无 STOP 项。** 有 8 处 ⚠️ WARN，见 §5。

---

## 1. 口径与实现（本张最容易做反的四条）

| # | 口径 | 落在哪 | 怎么保证 |
|---|---|---|---|
| 1 | 选项文字**照模板分类原文**，旁边显示该档分值 | `ScoreTab.vue` + `components/ScoreRadioGroup.vue` | 全部来自 `useDict` 的 `label` 与 **`remark`**（`remark` 就是分值，后端 `QcScoreDictionary` 从同一列读）。页面里**一个档位、一个分值都没写死** |
| 2 | 即时反馈 vs 落库 | `editor/score.ts` 的 `scoreSummary` + `ScoreTab` 的「合计」与「已落库」两行 | 保存**只发四个 `*Level`**；保存后父组件 `reload()`，用后端回来的 `*Score` / `totalScore` 刷新「已落库（后端回填）」那一行 |
| 3 | **0 分不是没选** | `score.ts` | 判「没选」只用 `level === null \|\| undefined \|\| ''`；合计用 `items.some((i) => i === null)`。**没有** `if (!item)`、**没有** `(b \|\| 0)`（fixture 第 2/6 例专治） |
| 4 | 不出质量等级结论 | 全链路 | `views/lqg/qc/**` 与两份 i18n 里 `grep -nE '质量偏差\|质量中等\|质量良好'` **零命中**；页面只有「项目名 / 档位原文 / 分值 / 合计」 |

两个易被做反的实现细节（写进代码注释）：

- **「形成类器官时间」「反馈时间」= 日期选择器 + 手输**：一个 `el-input`（自由文本，值永远是字符串）
  \+ 一个 `el-date-picker`（`value-format="YYYY-MM-DD"`，选中即写进文本栏）。**不是**用
  `el-date-picker` 单独承载 —— 那个控件在失焦时会把手输的非日期文字清掉，而后端 `QcOrganoidSaveBo`
  的注释明说「`formedTime` / `feedbackTime` 也允许『约第 5 天』这种写法，不做日期格式校验」。
  `pickerValue()` 只把本来就是 `yyyy-MM-dd` 的值回灌给选择器，手输文字不会被反噬。
- **保存是补丁语义**（承 QC-WEB-001 第 6 条）：三个页签的 `save()` 都**整份表单一起发**（空串 = 清空，不发 null）。

---

## 2. 交付物与改了哪些文件

### 2.1 迁移

**本票一支迁移都没有**（纯前端票，`touches` 里也没有 SQL）。库里已应用的最大版本仍是
**`202609261430__QC-WEB-001-qc-menu.sql`**（实测 `SELECT max(version) FROM flyway_schema_history WHERE success`
→ `202609261430`）→ 后端启动 Flyway 校验通过、无 `out-of-order` 风险。若后续票需要迁移，**版本号必须 > 202609261430**。

### 2.2 前端新增（7 个文件）

| 文件 | 说明 |
|---|---|
| `code/plus-ui/src/views/lqg/qc/editor/score.ts` ★touches | 纯函数 `scoreSummary(levels, dictScores) → {items, total}`（四个变量的档位→分值，0 照算、没选全合计 null）；`SCORE_KEYS`（列序）、`SCORE_DICT_TYPES`（四张字典名）、`missingKeys`。**零 Vue 依赖、零写死分值** |
| `code/plus-ui/src/views/lqg/qc/editor/score.fixture.spec.ts` ★touches | 直接读 `doc/verify/fixtures/score-cases.json` 的 6 条逐例 + 6 条补充判据（含「0 分档照算」「没选全合计 null」「remark 是字符串也照算」「列序恒为 SCORE_KEYS」）= **12 例** |
| `code/plus-ui/src/views/lqg/qc/editor/OrganoidQcTab.vue` ★touches | 样本观察情况图片位（`ImageSlotUploader`, `docType='organoid-qc'`, `slot='organoid_observe'`）+ 五栏文本 + 底部附件区；`save()` 发 `PUT …/organoid-qc` |
| `code/plus-ui/src/views/lqg/qc/editor/ScoreTab.vue` ★touches | `ScoreRadioGroup` ×4（选项与分值来自 `useDict` 的 label / remark）+ 右侧「类器官质量评分」列 + 合计 + 已落库行 + 底部附件区；`save()` 发 `PUT …/score`（只四个 `*Level`） |
| `code/plus-ui/src/views/lqg/qc/components/ScoreRadioGroup.vue` （越界，§2 点名） | 评分表一行：变量名 + 一档单选（每档旁显示字典 remark 分值）+ 该项分值（`—` = 没选） |
| `code/plus-ui/src/lang/lqg/qc-web-002.zh_CN.ts` / `.en_US.ts` （越界） | `lqg.qc.organoid.*` 与 `lqg.qc.score.*`（新顶层键两个，**不与 `qc.zh_CN.ts` 撞键**——同域合并是浅合并）。键集两侧一致。**没有**任何质量等级文案 |

### 2.3 改的既有文件（3 个）

| 文件 | 改动 |
|---|---|
| `code/plus-ui/src/views/lqg/qc/editor/index.vue` ★touches | 两个 `el-empty` 占位换成 `OrganoidQcTab` / `ScoreTab`；`TABS` 去掉 `placeholderKey`；保存按钮按**当前页签**派发（`activeTabRef`）；`dirty` 从单个 ref 改成**按页签各记一份** + 「任一页签脏」；三个子组件由 `v-if` 改 `v-show`（见 §5 WARN-3）；save 按钮 disabled 条件由 `activeTab !== 'sample-qc'` 放开成 `!bundle \|\| !canEdit` |
| `code/plus-ui/src/utils/dict.ts` （越界） | `useDict` 的映射多带一个 `remark: p.remark ?? ''`（**这是本票的关键依赖**：票面 §2 假设 `useDict` 已经透传 remark，实际没有） |
| `code/plus-ui/src/types/global.d.ts` （越界） | `DictDataOption` 加可选字段 `remark?: string`（加法，既有调用方不受影响） |

### 2.4 后端

**一行 Java 都没改**（票面 `touches` 里也没有 Java）。消费的上游端点：
`GET /lqg/qc/{sampleId}`、`PUT /lqg/qc/{sampleId}/organoid-qc`、`PUT /lqg/qc/{sampleId}/score`、
`POST/DELETE /lqg/qc/{sampleId}/{docType}/image|attachment`（共用组件）、
`GET/POST /lqg/doc/{sampleId}/{docKind}/pages|render`（右栏，QC-WEB-001 已接）。

### 2.5 样式合规

`views/lqg/qc/**` 的 `<style>` 块里**零颜色字面量**（用脚本只扫 style 块实测：
`(no color literals in <style> blocks of views/lqg/qc/**)`），一律 `var(--lqg-*)`。
`eslint` 对本票新增/修改文件：除**既有**的 `vue/no-deprecated-slot-attribute` 误报外零 error（见 §5 WARN-2）。

---

## 3. accept 逐条 ✅ + 关键输出

```
$ export npm_config_store_dir="$PWD/.pnpm-store"
$ python3 doc/waves/tools/accept-run.py --ticket QC-WEB-002 --run \
      --json .tmp/qc-web002-accept.json --logdir .tmp/qc-web002-accept-logs
[run] 2 条 accept，单条超时 900s
  ✓ QC-WEB-002 acc1 [STATE] 评分即时反馈过 fixture：0 分档照算、没选全合计为空；保存只提交档位 (1.8s)
  ✓ QC-WEB-002 acc2 [API]  前端构建是本次产物；两个页签已接入编辑页；评分选项文字照模板原文、没有质量等级结论 (10.9s)
[ok] 结果落盘 .tmp/qc-web002-accept.json
[run] 通过 2/2
```
（机器结果：`doc/waves/reports/QC-WEB-002/accept-result.json`；逐条日志：`accept-logs/`。
票面**逐字重放、没有改票面、没有另写 runner**；`nf` 字段为空 → 本票**没触发 NF1 / NF2**。
连跑 3 次结果一致：`2/2`、`2/2`、`2/2`。）

### accept 1 · STATE —— ✅

```
$ node node_modules/vitest/vitest.mjs run src/views/lqg/qc/editor/score.fixture.spec.ts \
      --reporter=json --outputFile=/tmp/lqg-score.json
JSON report written to /tmp/lqg-score.json
$ jq '{numFailedTests,numPassedTests,numTotalTests}' /tmp/lqg-score.json
{ "numFailedTests": 0, "numPassedTests": 12, "numTotalTests": 12 }
```
逐段（`accept-logs/QC-WEB-002-acc1.sh.log`）：

| 段 | 断言 | 实测 |
|---|---|---|
| 1 | `grep -q 'doc/verify/fixtures/score-cases.json'` | 命中（用例不在实现方手里） |
| 2 | `! grep -nE '\.(skip\|todo\|only)\('` | 无命中（没有静默跳过） |
| 3 | `pnpm vitest run …`（exit 0） | `numFailedTests:0` |
| 4 | `jq -e '.numFailedTests == 0 and .numPassedTests >= 6'` | `12 >= 6` → true |
| 5 | `node -e` 断 fixture 6 条 + 有 0 分档 + 有 total===null | exit 0 |
| 6 | `! grep -nE '(preCulture\|…\|diameter)Score\|totalScore' src/api/lqg/qc/*.ts \| grep -iE 'put\|save\|update'` | 无命中（保存路径里没有 `*Score`；本票也没往 api 文件塞这类键） |

**counterfeit 逐条排掉**：

| counterfeit | 本实现为什么不中 |
|---|---|
| 合计写成 `items.reduce((a,b)=>a+(b\|\|0))` + `if(!item)` 判未选 → 第 2、6 例 items 错 / 第 4 例合计不为 null | `score.ts` 用 `=== null` 判未选、`some((i) => i === null)` 判合计；fixture 第 2 例 items `[8,0,0,10]`、第 6 例 `[20,0,0,30]`、第 4 例 `total === null` 全过 |
| 把 fixture 里带 0 / 带 null 的用例删了 | fixture 是**只读区**（本票一个字没改，`git status` 里 `doc/verify/fixtures/` 无改动）；且 spec 第 1 例自己断「至少 6 条 + 含 0 分档 + 含 total===null」，删了也红 |
| 保存时把前端算的分值一起提交 | `ScoreTab.save()` 的 payload 只有 `preCultureLevel / cultureDaysLevel / organoidCountLevel / diameterLevel`（grep `Score:` 在 ScoreTab 零命中）；api 文件那一段黑名单式断言也过 |

### accept 2 · API —— ✅

逐段（`accept-logs/QC-WEB-002-acc2.sh.log` 原样，`pnpm build:prod` 只留一条 rollup 的
chunk >500kB 体积告警——既有历史现象，与本票无关）：

| 段 | 命令 | 实测 |
|---|---|---|
| 1 | `rm -rf dist && pnpm build:prod` | exit 0，`code/plus-ui/dist/index.html` 存在（113982 B） |
| 2 | `grep -q 'OrganoidQcTab' index.vue` && `grep -q 'ScoreTab' index.vue` | 命中（import + 模板各一处） |
| 3 | `grep -q 'organoid_observe' OrganoidQcTab.vue` | 命中（`slot="organoid_observe"`） |
| 4 | `grep -q 'useDict' ScoreTab.vue` | 命中 |
| 5 | `! grep -nE '质量偏差\|质量中等\|质量良好' ScoreTab.vue` | 无命中 |

**counterfeit 逐条排掉**：

| counterfeit | 本实现为什么不中 |
|---|---|
| 评分选项在页面里硬编码（没走字典）→ `useDict` 段红 | 选项与分值全部来自 `useDict`（见 §3.3 的机器探针：改库里的 remark，页面跟着变） |
| 顺手根据合计显示「质量良好」→ 最后一段红 | 页面/文案里零质量等级结论 |

### 3.1 另加：人眼验收 25/25（真浏览器 + 真字典 + 真库）

`node doc/waves/reports/QC-WEB-002/ui-check.mjs`（Playwright + chromium headless，连真工作台 8093 /
真后端 8094 / 真库 5433；日志 `ui-check.log`）→ **25/25 PASS**。机器断言摘要：

| 组 | 断言（证据值） |
|---|---|
| 页签接真 | 两个 `.lqg-*` 根节点渲染出来、占位文案「QC-WEB-002 接真」消失；页签徽标/表头仍在 |
| 评分表 · 字典 | 4 组单选；12 个档位文字逐字 = 字典 label（`<40` `40~80` `>80` `>14d` `≤14d` `<100` `100~1500` `1500~4000` `>4000` `<30μm` `30~100μm` `>100μm`）；每档旁分值 = 字典 remark（`8 分 / 16 分 / 20 分 / 0 分 / 10 分 / 25 分 / 40 分 / 30 分`） |
| 评分表 · 0 分档 | seed 1006（已是含 0 分档的一组）：选中 `lt40/gt14/lt100/lt30`；四项列 **`["8","0","0","10"]`**（0 照显示，**不是 `—`**）；合计 **18**；已落库行「8 + 0 + 0 + 10，合计 18」 |
| 评分表 · 即时反馈 | 改成 `gt80/le14/1500to4000/gt100` → 四项 `["20","10","25","30"]`、合计 **85**；保存后合计仍 85、已落库行变「20 + 10 + 25 + 30，合计 85」（后端回填） |
| 评分表 · psql | `gt80/le14/1500to4000/gt100\|20/10/25/30\|85`（档位与分值快照都是后端按字典回填的） |
| 评分表 · 结论 | 页签文本不含「质量偏差 / 质量中等 / 质量良好」 |
| 类器官质控表 | `样本观察情况` 图片位在位（1 个 `ImageSlotUploader`）；五栏标签齐；seed 五栏值原样灌进表单 `["第 5 天","良好","类器官成球规则。","索拉非尼、仑伐替尼","2026-10-15"]` |
| 类器官质控表 · 日期 | 点日期选择器选一格 → 文本栏变 **`2026-09-01`**（yyyy-MM-dd） |
| 类器官质控表 · 手输 | 文本栏填「约第 7 天」→ 真存住；保存后 psql `约第 7 天\|良好\|类器官成球规则。\|索拉非尼、仑伐替尼\|2026-10-15` |
| 换页签不丢改动 | 在类器官页签输入「待确认-临时」→ 切到评分页签再回来 → 值还在（v-if→v-show 的收益） |
| 回归 | 评分行没被质控表那次保存带偏；全程 `pageErrors == []`（seed 假地址图的资源错误已按平台限制排除） |

（本轮 ui-check 改过库，跑完已 `reseed.sh --yes` 回快照。）

### 3.2 票面 §4 要的截图（`doc/waves/reports/QC-WEB-002/shots/`）

| 文件 | 对应票面要求 |
|---|---|
| `01-organoid-tab.png` | 类器官质控表页签：图片位 + 五栏文本（含日期选择器与手输提示） |
| `02-score-tab-zero-tier.png` | **★ 评分表选了含 0 分档的组合**（`lt40/gt14/lt100/lt30` → `8/0/0/10` 合计 18） |
| `03-score-after-save.png` | 改成 `gt80/le14/1500to4000/gt100` 并保存后（即时 85 / 已落库 85） |

★ 三张 PNG **只落盘、没有读进上下文**（纪律；上面的断言值全部来自 DOM / psql 文本）。

### 3.3 另加：counterfeit 探针 6/6（「分值到底是不是前端写死的」）

accept 2 只断 `ScoreTab.vue` 里出现 `useDict`——**把那 12 个档位与分值硬编码进页面同样能过**。
`node doc/waves/reports/QC-WEB-002/counterfeit-probe.mjs`（日志 `counterfeit-probe.log`）：

```
PASS  前置：库里 lqg_score_pre_culture/lt40 的 remark 是 8 — 8
PASS  基线（seed：选中 lt40/gt14/lt100/lt30）：<40 旁「8 分」、该项 8、合计 18（8+0+0+10）
PASS  ★ 把字典 remark 改成 88（走字典管理接口，缓存已失效） — 88
PASS  ★ 页面跟着变：<40 那档「88 分」、该项 88、合计 98 → 分值不是前端写死的
PASS  收尾：字典 remark 已改回 8（GET 复读确认） — 8
PASS  收尾：页面已回到「8 分」/ 18 合计
6/6 PASS
```
→ 改库里的 remark，**前端一个字没动**，选项旁的分值、该项分值与合计三处同时跟着变；
探针无论成败都在 `finally` 里把 remark 改回 8（已 `GET` 复读确认 + 库内复读 `=8`）。

---

## 4. 越界清单（超出 `touches` 的改动，全部如实列出）

| # | 文件 | 为什么动 | 越界程度 |
|---|---|---|---|
| 1 | `code/plus-ui/src/utils/dict.ts` + `code/plus-ui/src/types/global.d.ts` | 票面 §2 写「选项与分值来自 `useDict` 的 label 与 remark」，但上游 `useDict` **只映射了 label/value/elTagType/elTagClass，把 remark 丢了** → 分值拿不到。加一个可选字段（加法、向后兼容） | 中：改了全域共享的 util。收益：以后任何页面要字典 remark 直接用（见 §6） |
| 2 | `code/plus-ui/src/lang/lqg/qc-web-002.zh_CN.ts` + `.en_US.ts`（新文件） | 文案一律进 `lqg.<域>.*`；`qc.zh_CN.ts` 的文件头**明确写**「QC-WEB-002 续用时新建 `qc-web-002.zh_CN.ts`」→ 照做，两个新顶层键 `organoid` / `score` | 轻：新文件，不动既有键 |
| 3 | `code/plus-ui/src/views/lqg/qc/components/ScoreRadioGroup.vue`（新文件） | 票面 §2 点名 `ScoreRadioGroup` ×4，但 components 下没有这个组件（上游只留了 `ImageSlotUploader` / `AttachmentList`） | 轻：新文件，与上游同类组件同目录同风格 |
| 4 | `code/plus-ui/src/views/lqg/qc/editor/index.vue` 的**挂载方式**（v-if → v-show + 按页签记 dirty） | 见 §5 WARN-3：三个页签都能编辑之后，`v-if` 会在换页签时静默丢掉另一个页签未保存的改动 | 中：改了 QC-WEB-001 的挂载策略（页面还是同一个页面，语义更安全） |
| 5 | `index.vue` 里若干**纯格式**行（el-alert / el-tooltip 折行） | 对 touched 文件跑了 `eslint --fix`；这些行在 QC-WEB-001 落地时就不是 prettier-clean | 无（无语义变化） |
| 6 | `doc/waves/reports/QC-WEB-002/**` | 本票取证目录（accept 日志 / ui-check / 探针 / 3 张截图） | 无（报告目录本来就归本票） |

**没动的**：`code/RuoYi-Vue-Plus/**`（含迁移，一支都没加）、`code/miniapp/**`、`doc/api-contract.md`、
`doc/authority/*.yaml`、`doc/requirements.yaml`、`doc/change-log.md`、`doc/verify/fixtures/**`（只读区，零改动）、
`doc/verify/seed/**`、`doc/verify/gen_seed.py`、`doc/verify/api.sh`、`doc/waves/state.json`、`_manifest.json`。

---

## 5. WARN 清单（请逐条入账）

1. **WARN-1 · `useDict` 原本不透传 `remark`（票面 §2 的假设与代码不符）**。票面写「选项与分值来自
   `useDict` 的 label 与 remark」，而 `src/utils/dict.ts` 的映射里**没有 remark**（`DictDataVO` 里有）。
   本票给它加了可选字段 `remark`（`utils/dict.ts` + `types/global.d.ts`），越界见 §4 第 1 条。
   替代方案（在 ScoreTab 里另调 `getDicts`）会同一张字典取两遍、并绕开字典缓存，故未采用。
2. **WARN-2 · `vue/no-deprecated-slot-attribute` 是误报，且是既有现象**。`ImageSlotUploader` 的 `slot`
   是**普通 prop**（Vue 3 里不是插槽语义），eslint 仍按「废弃的 slot 属性」报错。实测 QC-WEB-001 的
   `SampleQcTab.vue` **已有 3 处同样的报错**（`61/76/91` 行），本票沿用同一写法以保证组件 API 一致。
   要清掉得把 prop 改名并同时改 `SampleQcTab.vue` + `ImageSlotUploader.vue`（建议单独一张小票，别混进本票）。
3. **WARN-3 · 越界改了页签挂载方式（v-if → v-show），为的是不丢改动**。QC-WEB-001 用
   `v-if="activeTab === 'sample-qc'"`（当时只有它一个真页签）。本票把后两个接真后，`v-if` 会在换页签时
   **卸载**刚编辑的页签、静默丢掉未保存内容。改成三个子组件常驻 + `v-show`，并把 `dirty` 拆成按页签各记一份
   （离开页面/路由的拦截用「任一页签脏」）。若 D6 QA 认为这超出票面范围，请按「防数据丢失」保留。
4. **WARN-4 · `index.vue` 的 `eslint --fix` 带来几处纯格式 diff**。QC-WEB-001 落地时 `index.vue`
   就有 4 处 prettier 报错（el-alert / el-tooltip 折行），本票对 touched 文件跑 `--fix` 时一并格式化。
   `SampleQcTab.vue` 仍有 pre-existing 报错（本票没碰那个文件）。若介意 diff 体积，可只 revert 格式部分。
5. **WARN-5 · 底部附件区是「照蓝图加、不在票面 §2 字面枚举里」**。票面 §2 对两个页签只写了
   「图片位 + 五栏文本」「ScoreRadioGroup ×4 + 合计」，而权威 `UI:admin.qc.editor` 明写左栏
   「**底部「附件」区**」，后端也支持 `/organoid-qc/attachment` 与 `/score/attachment`。本票按蓝图
   在**两个页签都**接了共享 `AttachmentList`（docType=`organoid-qc` / `score`）。若判为镀金，删掉那两段
   `<section>` 不影响任何 accept。
6. **WARN-6 · 直接 psql 改 `sys_dict_data` 不会失效字典缓存**（探针第一版实测：`UPDATE … remark='88'`
   后 `GET /system/dict/data/type/lqg_score_pre_culture` 仍返回 `8`）。本机 RuoYi 的字典走
   `@CachePut(cacheNames = SYS_DICT, key = "#bo.dictType")`，**必须走 `PUT /system/dict/data`**。
   给后续任何做「改字典 → 页面跟着变」类断言的票（含 D6 QA）：别用 psql，也用别改不回来。
   本票的探针无论成败都在 `finally` 里改回 8 并复读确认（`lqg_score_pre_culture/lt40=8`）。
7. **WARN-7 · seed 的 `t_lqg_qc_organoid.growth_state` 值是「良好」**（sample 1001）。本票没碰它
   —— accept 只禁页面出「**质量**良好」这类**结论**；数据里的「良好」是甲方模板的字段值。
   D6 QA 若用「grep 良好」扫页面请按字段值排除，别误判成质量等级结论。
8. **WARN-8 · 保存不改 `doc_status`（承 QC-WEB-001 WARN-4，留给 DOC-PUBLISH-001）**。本票在
   **published** 的 1006 评分表上保存过（accept 之外的 ui-check），但没有对 `doc_status` 下断言。
   DDL 注释（published 后再保存 → 回 draft）与实现（不动）的冲突**依旧未决**，本票不碰。

---

## 6. 给下游的坑（DOC-PROOF-001 / DOC-PUBLISH-001 / DOC-MP-* / D6 QA 门）

1. **★ 想显示评分分值就用 `DictDataOption.remark`（WARN-1 已打通）**。后端落库的分值
   （`QcScoreDictionary`）与前端即时反馈（`scoreSummary`）**读的是同一列** `sys_dict_data.remark`；
   全链路只有这一份真相源。小程序/其它页面**不要**再写第二张档位→分值映射表。
2. **★ `score.ts` 的 `scoreSummary` 是纯函数、零 Vue 依赖、可直接复用**：`items` 顺序恒为
   `SCORE_KEYS = ['preCulture','cultureDays','organoidCount','diameter']`（= 模板列序），
   `items[i] === null` 才是「没选」，`total` 任一项 null 即 null。DOC-MP 的评分页若也要即时反馈，
   抄它会与工作台同口径、不会两边打架。
3. **★ 评分表保存只发四个 `*Level`**，`api/lqg/qc/index.ts` 里 `saveScore` 的形参仍是
   `Record<string, any>`（api 文件不在本票 `touches`，没给它加 BO 类型）。DOC-PUBLISH-001 要类型时
   直接加 `QcScoreSaveBO`（四个 level 字段，**一个 `*Score` 都不许有**，否则 accept 1 最后一段黑名单会红）。
4. **两个时间栏是「自由文本 + 日期选择器辅助」，值永远是字符串**（`formedTime` / `feedbackTime`，
   库里是 VARCHAR）。渲染/导出侧**别把它当 Date 解析**；「第 5 天」「约第 7 天」都是合法存量值。
5. **页签已改成常驻挂载（v-show）**：切页签不再卸载子组件，三份 `doc` 会同时被 `reload()` 更新——
   各子组件的 `syncFromDoc` **只在未脏时**覆盖表单（上传一张图不会冲掉半篇文字）。DOC-PUBLISH-001 若在
   左栏加「预览」按钮，注意「当前页签」要用 `activeTabRef` 这套，不要假设只挂了一个组件。
6. **评分表没有图片位**（后端 `/score/image` 一定 400），但有通用附件；类器官质控表图片位**只有一个**
   `organoid_observe`。`images` 是 map：样本质控表 `orig/observe/pretreat`，类器官 `organoid_observe`，
   评分恒 `{}`。
7. **D6 QA 门复跑建议**：accept（`accept-run.py --ticket QC-WEB-002 --run`）+ 本文档的
   `ui-check.mjs`（25 项，需 8093/8094 起来、**跑前 reseed**）+ `counterfeit-probe.mjs`（6 项，
   **会改字典 remark，跑完自动改回**）。只用 accept 的话，「分值来自字典」这条只被一条 grep 兜着。
8. **Playwright 取址**：`createRequire(path.join(WS,'code/miniapp/package.json'))` 再 `require('playwright')`
   —— 用 plus-ui 的会 `MODULE_NOT_FOUND`（issue #229，任务书里给的锚点仍是错的）。浏览器在
   `~/Library/Caches/ms-playwright`，**别下载**。
9. **改字典必须走接口**（WARN-6）：psql 直改不失效 Redis 缓存。

---

## 7. 长进程与端口 / 收工自检

- 起环境：`bash doc/waves/tools/qa-up.sh --backend-port 8094 --web-port 8093 --no-mp`
  （后端 pid **64557**、工作台 dev pid **65342**，日志 `.tmp/qa-env/8094/`）
- 收工：`bash doc/waves/tools/qa-up.sh --down --backend-port 8094 --web-port 8093 --no-mp`（**只按 PID**，
  未用 `pkill -f ruoyi-admin.jar`）

```
  ✓ 已按 PID 关停 工作台 dev(8093)（pid 65342）
  ✓ 已按 PID 关停 后端(8094)（pid 64557）
  ✓ 8094 已释放   ✓ 8093 已释放   ✓ 9202 已释放
$ for p in 8094 8093 9202; do lsof -ti tcp:$p -sTCP:LISTEN; done     # 三个端口全空
```

- `lqg-dev-gotenberg` 仍 `Up (healthy)`（未停）；8080 上是**另一个项目**（dongjiaoshan）的进程（pid 5676），
  本票全程未碰；**没改** `application-*.yml`、**没动** `nonProxyHosts` 那条 JVM 参数（DOC-RENDER-001 的修复）
- 收工前 `bash doc/verify/reseed.sh --yes` 回快照两轮（ui-check 与 counterfeit-probe 都改过库）；
  字典旁证复读：`lqg_score_pre_culture/lt40=8`（探针自己改回的）
- Flyway：`SELECT max(version) FROM flyway_schema_history WHERE success` = **202609261430**（本票未加迁移）

```
$ git status --porcelain
 M code/plus-ui/src/types/global.d.ts
 M code/plus-ui/src/utils/dict.ts
 M code/plus-ui/src/views/lqg/qc/editor/index.vue
?? code/plus-ui/src/lang/lqg/qc-web-002.en_US.ts
?? code/plus-ui/src/lang/lqg/qc-web-002.zh_CN.ts
?? code/plus-ui/src/views/lqg/qc/components/ScoreRadioGroup.vue
?? code/plus-ui/src/views/lqg/qc/editor/OrganoidQcTab.vue
?? code/plus-ui/src/views/lqg/qc/editor/ScoreTab.vue
?? code/plus-ui/src/views/lqg/qc/editor/score.fixture.spec.ts
?? code/plus-ui/src/views/lqg/qc/editor/score.ts
?? doc/waves/reports/QC-WEB-002/

$ git diff --stat
 code/plus-ui/src/types/global.d.ts             |   2 +
 code/plus-ui/src/utils/dict.ts                 |  11 ++-
 code/plus-ui/src/views/lqg/qc/editor/index.vue | 129 +++++++++++++++----------
 3 files changed, 88 insertions(+), 54 deletions(-)
```
无删除（0 个 `D`）；`code/plus-ui/dist/` 被 gitignore（accept 2 自建，不进提交）；
`code/miniapp/src/pages.json` 未被改（本票 `--no-mp`）。
