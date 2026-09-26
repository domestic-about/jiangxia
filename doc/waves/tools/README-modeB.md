# 模式 B：把 L0+L1 变成脚本，把 agent 留给真正会抓到问题的地方

> 2026-09-22 由 Kevin 拍板，**D5 起生效**。背景是 D1–D4 的实测账：验证太慢、太多环节在
> 重复劳动。这份文档是给后续会话 / 分片 agent 的操作手册，也是这次改法的依据。

## 1. 为什么改（D1–D4 的实测数据，不是感觉）

把四个任务的全部 QA 分片 audit 拉出来按级归因（`doc/waves/qa/D*-r*-L*.json`）：

| 级 | 拦门 S0/S1 | 非拦门 S2/S3 | 备注 |
|---|---|---|---|
| **L2**（端侧 UI） | **9** | 14 | 全部 9 条都是「老师按说明书走一遍」撞出来的 |
| L1（跨表对账） | 1（#105 顶层 `.or()` 串味） | 12 | 唯一一条拦门的 L1 —— 会被下面的脚本钉死 |
| L3（邪路） | **0** | 18 | 有价值，但主要是「没出事」的证明 |
| L0（编译/单测/build） | **0** | 15 | 最贵（mvn + 两个前端 build），却一次没拦过门 |

同时：**每轮的 L0+L1 都要派一个全新 agent，把同一套确定性对账重新推导一遍**，还各自
重建一次环境（mvn + 两个 build + reseed + 起三个进程）。台账 172 条里 163 条（94.8%）
不拦门，其中 55 条（32%）是 `harness` —— 长在检查工具自己身上。

结论：**L0+L1 该是脚本，L2 该是「一条端到端剧本」，L3 该只给有钱/库存/并发/权限的域做。**

## 2. 谁产出什么

| 产物 | 谁产 | 落在哪 |
|---|---|---|
| L0、L1 的审计 | `doc/waves/tools/gate.sh` + `gate-audit.py`（**机械产出**） | `doc/waves/qa/<D>-r<n>-L01.json` |
| L2 的审计 | 一个全新上下文的 QA agent（端到端剧本） | `doc/waves/qa/<D>-r<n>-L2.json` |
| L3 的审计 | 一个全新上下文的 QA agent（邪路） | `doc/waves/qa/<D>-r<n>-L3.json` |
| 整轮 verdict | `task_state.py qa merge --phase <D>`（按严重度机械算） | `doc/waves/qa/<D>-r<n>.json` |

`gate-audit.py` 写出的审计里带 `generated_by` 字段，标明「本片由脚本产出、不是 LLM 审计」。
它不冒充 agent —— 但它比 agent **更可复现**：同一 commit 重跑逐字相同。

`qa merge` 硬要求四级都有 `pass/fail` 且有 evidence（缺级一律拒合，挡的是「没跑当跑过」），
所以降级**不是**「这级不跑」，而是「这级跑得更窄、但真跑」。

## 3. 一轮的完整操作（照抄）

```bash
# 0) 起环境（后端 + 两端 dev，一条命令；只按 PID 收尾）
bash doc/waves/tools/qa-up.sh --backend-port 8092 --web-port 8093 --mp-port 9202

# 1) L0+L1：一条命令，零 LLM 上下文（约 8–20 分钟）
export LQG_VERIFY_ENV_FILE="$PWD/.tmp/qa-env/8092/verify.env"
bash doc/waves/tools/gate.sh --phase D5 --backend-port 8092
#    → .tmp/gate/D5/gate.json + doc/waves/qa/D5-r1-L01.json
#    ★ 只有 gate exit 0 且 L0、L1 都 pass 才写正式审计；否则只写草稿 .tmp/gate/D5/D5-r1-L01.draft.json
#      （auditor=draft-not-for-merge，qa merge 不收；2026-09-23 按 CR-20260923-09）

# 2) L2 / L3：派两个全新上下文的 QA agent（各只认领自己那级）
#    L2 跑「一条端到端剧本」；L3 只打库存/并发/权限
#    （派单模板见 §5；两者串行 —— 都要 reseed，同一时刻只许一个碰 PG 5433）

# 3) 合并 + 判红绿
python3 ~/claude-config/skills/zhixing/scripts/task_state.py qa merge --phase D5
#    再按 merge 的说法逐条 issue add，然后 set --phase D5 --status qa_passed

# 4) 收尾
bash doc/waves/tools/qa-up.sh --down --backend-port 8092 --web-port 8093 --mp-port 9202
```

`gate.sh` 的退出码：`0` 全绿 ｜ `1` 有断言不成立（实现问题）｜ `2` 环境/工具坏。
**1 和 2 必须分开** —— 工具故障伪装成红，会让整轮返工白烧（D1–D4 踩过多次）。

## 4. 工具清单与各自解决的老问题

| 工具 | 解决什么 |
|---|---|
| `gate.sh` | L0+L1 一条命令跑完；带 L0.0 新鲜度前置（**不用 `--fresh-module`**，它在沙箱恒 exit 2） |
| `accept-run.py` | **逐字重放票面的 accept 断言**（断言是 ① 侧票面资产，不是实现方写的 runner）；归一化只有 NF1 一条，且逐条打印。known-red 按段登记（`TICKET|accN@段号|#issue|理由`，issue 必须在 state.json 的 open_issues 里）：只豁免登记的那几段、后面的段照跑必须绿；`--segments` 打印切段 |
| `gate-audit.py` | 把机器结果转成合规模的审计；产品断言不成立 = **S1 拦门**，环境坏 = **S2 + harness**，但**该级记 blocked、不是 pass**（一级一步没跑也是 blocked）；门退出码不为 0 或任一级不是 pass → 不写正式审计，只写草稿 |
| `qa-up.sh` / `--down` / `--status` | 一条命令起/停/查环境；整个门的三阶段**共用一台后端**，不再三片各建一次 |
| `detach.sh` | macOS 没有 `setsid`；fork + `os.setsid()` 起真脱离的长进程，并打印**真实 PID**（收尾只按 PID kill） |
| `classify-parallel.py` | 判「纯前端票」（DB-free）能不能与要 DB 的票并行 |
| `sync-manifest.py` | 从 `state.json` 单向刷新 `_manifest.json` |
| `clean-orphan-accounts.sh` | 清并发 reseed 窗口留下的非 seed 段 `wx_*`/`lqg_*` 孤儿账号 |

### NF1 归一化（唯一一条，别再加）

票面 accept 里的 `--fresh-module <模块>` 会被去掉。原因：本沙箱 `/bin/ps` 是
`Operation not permitted` → 守卫落到 `date -d`（macOS 无）→ `api.sh` exit 2，把工具故障
伪装成断言红（issue #1/#13/#82，第 12 次命中）。**新鲜度由 `gate.sh` 的 L0.0 承担**
（源码不得新于 jar + 监听进程必须持有该 jar）。

实测：43 张票 101 条 accept 里 51 条含该 token；**除此之外 0 条**需要在 `--bizcode` 或
判码写法上做手脚（逐语句核过 `api.sh … grep -qE '^<码>'` 的组合）。要再加归一化规则，
必须先在 `accept-run.py` 里写明「是哪条 issue、为什么不能改票面」，并逐条打印出来 ——
**归一化是最容易变成静默弱化的地方**。

## 5. 分片派单模板（L2 / L3）

派单必须包含（其余照 `~/claude-config/skills/zhixing/templates/qa-subagent-prompt.md`）：

- **只有一级**：分片名 `L2` 或 `L3`，`levels` 只写自己那级，**不写 `verdict`**，`auditor: "independent"`。
- **禁读**：`doc/waves/reports/**`（实现方报告）与**同伴分片的 audit**。
- **绝对不要把 PNG 读进上下文**（截图只落盘、报告里写路径；历史上多任 agent 因此整轮报废）。
- ★ **`code/miniapp/src/pages.json` 的还原规则要分情况**（它是 uni-pages 生成物、且 merge 是 **old 优先**，tabBar 顺序由上次生成结果决定）：
  · **改了 tabBar / 页面注册的票**：**不要** `git checkout` 它——那会把 tabBar 退回旧页面（实测：还原后 tabBar 从 `[index,doc,me]` 退回 `[index,me,doc]`，含旧页 `pages/docs/index`，相关 accept 必红）。这类票**有意保留**重生成的版本，并在报告里写明「不是漏还原」。
  · **没改 tabBar 的票**：仍照旧 `git checkout -- code/miniapp/src/pages.json` 还原 dev server 的噪音改写。
  · **QA / 后续票要重生成时**：先 `rm code/miniapp/src/pages.json` 再 build，**不要**用 `git checkout`。
  （见 issue：DOC-MP-001 的 pages.json 顺序陷阱。）
- **Playwright 的 require 锚点用 `code/miniapp/package.json`**（`createRequire(path.join(WS,'code/miniapp/package.json'))` 再 `require('playwright')`）。
  用 `code/plus-ui/package.json` 作锚点会 `MODULE_NOT_FOUND`（plus-ui 的 package.json 里没声明 playwright 依赖；
  QC-WEB-001 实测踩到并改用小程序那份，见 issue #228）。浏览器已在 `~/Library/Caches/ms-playwright`，**别下载**。
- **环境已起好**：给它 `export LQG_VERIFY_ENV_FILE="$PWD/.tmp/qa-env/8092/verify.env"`、
  后端端口、两端 dev 端口；告诉它**不要重建环境**，只在需要时 `reseed.sh --yes`。
- **串行**：明确告知「同一时刻只有你一个在碰 PG 5433」，不要并发 reseed。
- 第 1 轮要做**证伪抽样 8–10 条**（改坏 → 看断言会不会绿 → `git checkout` 还原）；
  **第 2 轮起不重做**（断言的强弱在第 1 轮就问完了）。
- 收尾：`git status --porcelain` 干净、按 PID 关掉自己起的进程、reseed 回基线。

### 降级策略（D7/D8）

D7/D8 多为配置/发布/文档票，且相当一部分要等外部输入。这两级的**宽度**降级（一条冒烟
剧本 + 权限 403 + 一两个边界），但**仍然真跑**，合并在一个分片里，例如 `-L23.json`
同时认领 L2 与 L3（`qa merge` 认得，只要每级都有真 evidence）。这样门是完整的，
不需要人记名认下。

## 6. 已知的老坑（别重挖）

- **若依把 404/403/500 全包进响应体，HTTP 状态码几乎恒 200** → 判成败必须断业务码或 `jq -e`，
  再断**副作用没发生**（库里行数/全列 diff 不变）。只断 HTTP 码 = 假绿。
- `/mp/int/**` 用 `--as staff`；`--as admin` 恒 403（设计如此）。
- MyBatis-Plus 分页 count SQL 带绑定参数在 PG 上会 500（#159，`page.setOptimizeCountSql(false)`）。
- `@Scheduled` 在本项目原本永远不跑（#160）→ 超期/时效类正确性靠**读时判定**。
- 跑过 accept 再跑回归会因运行时账号残留假红（#157）→ 回归前先 `reseed` + `clean-orphan`。
- 改流水/登记类断言必须 dump **全表全列 md5** 做改前改后对比，只看响应码会被「先写库再返 400」骗过。
- **单测可能抓不住丢事务**（#173：`CryoFlowConcurrencyTest` 用自己的锁把线程串行化了）→
  并发正确性只有**真库并发**能验。
- 关进程**只按 PID**。`pkill -f 'ruoyi-admin.jar'` 会误杀 8080 上 Kevin 的本机服务（#46）。
- **JVM 必须显式给 `-Dhttp.nonProxyHosts` 含 `127.0.0.1`**（qa-up.sh 已内置）：macOS 的系统代理会被
  JDK 灌成 `http.proxyHost`，而 JDK 自带的 `nonProxyHosts` **只含 localhost、不含 127.0.0.1** →
  AWS SDK(Netty) 把发往 `127.0.0.1:9000`(MinIO) 的请求丢给代理 → 框架自带的
  `POST /resource/oss/upload` 直接 500（阻塞 120s 才报错）。**curl 不受影响，只有 JVM 踩**，
  所以从外部极难看出是代理问题（DOC-RENDER-001 花了很久才定位，见 issue #215）。

## 7. 成本预期

| 项目 | 改前（D1–D4 实测） | 改后（模式 B 预期） |
|---|---|---|
| L0+L1 | 1 个 agent / 轮，各建一次环境 | 1 条命令 / 轮，零 LLM 上下文 |
| 每轮分片数 | 3（D5/D6 计划 3；D7/D8 计划 3） | 2（D5/D6）／1（D7/D8 合并片） |
| 环境重建 | 每片各一次 | 整门共用一台后端 |
| 每任务墙钟 | 1.5–3 h（D2 因 4 轮更长） | 目标 40–70 min |
