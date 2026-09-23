# SYS-ACCEPT-001 · 把已放跑过缺陷的四条 accept 从「源码 grep」改成「行为判据」（并用变异验证）

- **分支**：`task/D7`（`git branch --show-current` 确认；未切分支、未 push、未合分支）
- **HEAD**：`73186df`（起始也是 `73186df`）
- **性质**：owner 决策中途插入的**收敛票**。只改**断言 + 回归资产**，**没动产品代码**。
- **环境**：`bash doc/waves/tools/qa-up.sh --backend-port 8094 --web-port 8093 --mp-port 9204`
  （后端 pid 66507 / 工作台 67476 / 小程序 H5 67716；JVM 的 `-Dhttp.nonProxyHosts=…` 未删；
  `lqg-dev-gotenberg` 全程 running，本票**没有**停它）
- **结论**：两条 accept **2/2 全绿**（acc1 235.2s / acc2 68.4s）；六个热点**逐个施加已定义变异 → 判据真变红 → 还原 → 真复绿**。

---

## 1. 一句话：为什么这是「止损」而不是「镀金」

D7 门 r1 放过一条真 S1：`SYS-EXPORT-001` acc2 的「下载带鉴权头」写的是
`grep -E 'Authorization|clientid' export.ts fileHandoff.ts` —— 它只问**两个文件里有没有这两个词**。
QA 实测（`doc/waves/qa/D7-r1-L2.json` issues[1]，证伪 F3）：**把导出的调用点改成不传 header，整条 acc2 仍全绿**（含两条 vitest 10/0、17/0）。
于是「鉴权头已经验过」的假信心成立，而**「哪个调用方该朝哪个 URL 带哪个头」从来没被验过**；
同一处错配在文档下载链路上把 API 鉴权头塞到了 OSS 预签名直链 → MinIO `400 multiple authentication types` → H5 单份/合并下载 **0/4 全失败**
（`doc/waves/reports/D7-rework-r1-issue-S1.md`，已修）。

本票把 D7 r1 L2 **已经证明有效**的那类判据（真请求头 / 真 DOM 读数 / 真 URL 对照）固化成 accept，
并给每个热点配一个**可施加的变异**：判据不只要「现在绿」，还要**改坏了必须红**。

---

## 2. 六个热点：旧判据 → 新判据（+ 已定义变异）

| 热点 | 归属 | 旧判据（源码字面量） | 新判据（判据输入来自运行中的系统） | 已定义变异 |
|---|---|---|---|---|
| **H1a** | `SYS-EXPORT-001` acc2 | `grep -qE 'Authorization\|clientid' src/pages/ledger/export.ts src/utils/fileHandoff.ts`（只问词存在，**调用点零覆盖**） | **真点一次「导出 Excel」**，`page.route` 抓这一发的**真请求头**：必须 `Authorization: Bearer …` + `clientid`，响应 200 且响应体是 **ZIP 魔数 `PK` 的真 xlsx**；URL 带 `verifyStatus=pending` | 去掉 `pages/ledger/index.vue` 调用点的 `header: authHeader(), requireAuth: true` |
| **H1b** | `SYS-EXPORT-001` acc2 | 同上（这正是 S1 的盲区） | **真点一次单份「下载」**：后端签发 OSS 预签名直链后，浏览器对该直链的**真请求头不含 `Authorization`**，且 OSS 响应 **200** | 给 `DownloadBar.vue` 的 `downloadToTemp` 传回 `authHeader()`（= S1 缺陷本体） |
| **H2** | `SYS-HOME-001` acc2 | `grep 'home/todo'` + `grep -c TodoCard >= 5`（只问「卡片接了接口」） | 工作台首页**真 DOM** 读五张卡片的数字（`.lqg-todo-card__num`）**== 同一次 `GET /lqg/home/todo` 的返回值** | 把卡片 `:value="todo.pendingSamples"` → 写死 `7`、`:value="todo.pendingEmbeds"` → 写死 `9`（真值 2 / 1） |
| **H3a** | `DOC-MP-002` acc1 | `grep -qE 'showMenu:[[:space:]]*true' src/utils/fileHandoff.ts`（**注释里的字面量也算命中**） | **剥掉注释后** `showMenu: true` 仍须在**真代码**里，且位于 `uni.openDocument({filePath, …, showMenu: true})` 的**参数位置**（同一对象字面量里有 `filePath`） | 删掉真代码那一行、只在注释里保留 `// showMenu: true` |
| **H3b** | `DOC-MP-002` acc1 | `grep -q 'previewImage' src/components/lqg/ThumbStrip.vue`（只看组件里出现过这个词） | 真浏览器点「文档中的图片」缩略图 → 读 H5 打开层（对应真机 `wx.previewImage`）拿到的 **src == pages 接口给的原图 `url`**，且 **≠ `previewUrl`** | 把 `ThumbStrip.vue#open` 的 `urls` 从原图 `url` 换成 `previewUrl` |
| **H4** | `DOC-PUBLISH-001` acc2 | `grep -cE "format.*(docx\|pdf)\|'docx'\|'pdf'" >= 2`（只钉住文里有 docx 与 pdf 两个字面量） | 预览面板**真 DOM** 渲染出的下载入口**恰好 4 个**、文案恰好 [下载 Word / PDF / 合并 Word / 合并 PDF]，并**逐个真点一次**抓真请求断言 `format=docx\|pdf` 与 `/merged/` 位都正确 | 删掉「下载合并 PDF」整个 `el-button` |

判据实现落在 `doc/waves/regression/D7/accept-strengthened/`：
`probe.mjs`（H1a/H1b/H2/H3b/H4，Playwright 真浏览器；require 锚点 = `code/miniapp/package.json`，issue #229）、
`probe-h3a.py`（H3a，剥注释）、`setup-fixture.sh`（确定性夹具）、`mutate.py`（变异施加/还原）、
`merge-evidence.py`（合成 evidence）。总入口 `doc/waves/regression/D7/mutation-assert.sh`。

---

## 3. 变异前后**实际观测值**（真请求头 / 真 DOM 数 / 真 URL）

> 全部从 `accept-strengthened/evidence.json` 的 `hotspots[*].observed`（未改坏）与
> `previous_mutation_run.mutations[*].observed_when_mutated`（改坏后）原样取出。

### H1a · 导出请求真发一次

| | 真请求头 | 真响应 |
|---|---|---|
| **未改坏** | `authorization: Bearer eyJhbGciOiJIUzI…`、`clientid: 22b2aecd0710671691ec1c07f2542b9d`（全部 header key：`accept / authorization / clientid`） | **200**，`4117` B，魔数 **`PK`**（真 xlsx） |
| **改坏后**（去掉调用点 `header: authHeader()`） | `authorization: (无)`、`clientid: (无)`（只有 `accept`） | HTTP 200 但只有 **72 B**、魔数 `{"`（若依把 401 包进响应体，HTTP 恒 200）→ **判据红** |

URL（两次相同）：`http://127.0.0.1:9204/lqg-api/mp/int/export/tissue?verifyStatus=pending`

> ★ 这次对照正好演示了票面 §0 那句「HTTP 恒 200，判成败必须断业务码/副作用」：
> 光看 HTTP 状态码这条变异会**漏判**；是「真请求头 + ZIP 魔数」两件副作用一起把它钉住的。

### H1b · 文档下载真点一次（这条直接复现了 S1）

| | 后端签发的直链 | 发往 OSS 的**真请求头** | OSS 真响应 |
|---|---|---|---|
| **未改坏** | `…/doc/9000001001/sample_qc/internal/013bee40bb69.docx?X-Amz-…` | `sec-ch-ua-platform / referer / user-agent / sec-ch-ua / sec-ch-ua-mobile` —— **无 `authorization`、无 `clientid`** | **200**，`application/vnd.openxmlformats-officedocument.wordprocessingml.document` |
| **改坏后**（给 `DownloadBar` 传回 `authHeader()`） | `…/…/d4fc309b194e.docx?X-Amz-…` | **`authorization: (有！)`** + **`clientid: 22b2aecd…`** | **400**，`application/xml`（MinIO：request has multiple authentication types）→ **判据红** |

### H2 · 工作台首页真 DOM vs 同一次接口

| | `/lqg/home/todo` 返回值 | 真 DOM `.lqg-todo-card__num` |
|---|---|---|
| **未改坏** | `{pendingSamples:2, pendingEmbeds:1, cryoOverdue:2, pendingExtUsers:2, renderFailed:0}` | 待核验样本 `2` / 石蜡包埋送样 `1` / -80 超期批次 `2` / 待核验外部用户 `2` / 文档渲染失败 `0` |
| **改坏后**（写死 7 / 9） | 同上（接口没变） | 待核验样本 **`7`**、石蜡包埋送样 **`9`**、其余 2 / 2 / 0 → **判据红** |

URL：`http://127.0.0.1:8093/dev-api/lqg/home/todo`（status 200）

### H3a · 剥注释后的真代码（本票唯一允许读源码的一条）

| | 原始文件命中数 | **剥注释后**命中数 | 位置 |
|---|---|---|---|
| **未改坏** | 1 | **1** | `fileHandoff.ts:160` → `showMenu: true,`，所在对象字面量含 `filePath`（= `openDocument` 的参数位） |
| **改坏后**（真代码那行删掉、只留注释） | 1（注释里的） | **0** | `null` → **判据红** |

### H3b · 点缩略图后打开层的 src

| | pages 接口给的原图 `url` | 同一条的 `previewUrl` | 打开层真拿到的 `src` |
|---|---|---|---|
| **未改坏** | `…/6cbd182eb5394c468f73a0c7bc031d51.png` | `…/3a1320d4b4e041aba8cdb7688af0c619.jpg` | **`…6cbd182e….png`**（== 原图 url，≠ previewUrl） |
| **改坏后**（`urls` 换成 `previewUrl`） | `…/2d14a02f0f344211aa6f783d99a2c1ed.png` | `…/bdf9e739933540d79081348768064148.jpg` | **`…bdf9e739….jpg`**（== previewUrl）→ **判据红** |

> 夹具先上传 **2400×1600 真 PNG**（`setup-fixture.sh`），后端 `QcImagePreviewResolver` 因长边 > 2000 另存 `.jpg` 预览图
> → `url` 与 `previewUrl` 才是两个不同地址，这一条才有区分对可断。（seed 的图片位是假地址 `https://seed.invalid`，按 issue #217 裁定①前端跳过。）

### H4 · 四个下载入口

| | 真 DOM 的下载按钮 | 逐个真点后的真请求 |
|---|---|---|
| **未改坏** | `["下载 Word", "下载 PDF", "下载合并 Word", "下载合并 PDF"]`（4 个） | `…/sample_qc/download?format=docx&audience=internal` 200 ／ `…format=pdf…` 200 ／ `…/merged/download?format=docx…` 200 ／ `…/merged/download?format=pdf…` 200 |
| **改坏后**（删掉「下载合并 PDF」） | `["下载 Word", "下载 PDF", "下载合并 Word"]`（3 个） | 第 4 个 `found:false`（按钮不存在）→ **判据红** |

---

## 4. 两个 accept 的实际输出

```bash
export LQG_VERIFY_ENV_FILE="$PWD/.tmp/qa-env/8094/verify.env"
python3 doc/waves/tools/accept-run.py --ticket SYS-ACCEPT-001 --run \
        --json .tmp/sys-accept.json --logdir .tmp/sys-accept-logs
```

```
[run] 2 条 accept，单条超时 900s
  ✓ SYS-ACCEPT-001 acc1 [STATE] 四条高危断言改成行为判据后，**改坏必须变红**… (235.2s)
  ✓ SYS-ACCEPT-001 acc2 [DATA] 强化后的四条断言在未改坏的树上全绿，且每条都留下了真实运行证据… (68.4s)
[ok] 结果落盘 .tmp/sys-accept.json
[run] 通过 2/2
ACCEPT_EXIT=0
```

`acc1` 日志（`mutation-assert.sh` 默认模式）关键行 —— **归一化：无**（`nf: []`）：

```
[tree] 起点 git diff -- code/ = [] sha=e3b0c44298fc（期望只有 pages.json 的 2 行平台注释）
── ① 未改坏的树：scope 内判据全绿（预期全 GREEN）
   ✓ H1a GREEN   ✓ H1b GREEN   ✓ H2 GREEN   ✓ H3a GREEN   ✓ H3b GREEN   ✓ H4 GREEN
── ② 变异验证（每个热点：施加变异 → 判据必须变红 → 还原 → 必须复绿）
   ✓ H1a:改坏之后判据变红（符合预期）      ✓ H1a:还原之后判据复绿
   ✓ H1b:改坏之后判据变红（符合预期）      ✓ H1b:还原之后判据复绿
   ✓ H2:改坏之后判据变红（符合预期）       ✓ H2:还原之后判据复绿
   ✓ H3a:改坏之后判据变红（符合预期）      ✓ H3a:还原之后判据复绿
   ✓ H3b:改坏之后判据变红（符合预期）      ✓ H3b:还原之后判据复绿
   ✓ H4:改坏之后判据变红（符合预期）       ✓ H4:还原之后判据复绿
[tree] 收尾 ✅ git diff -- code/ = [] sha=e3b0c44298fc（起点 e3b0c44298fc）; git stash list=空
[ok] evidence.json 落盘：6 个热点，六个齐；变异档案 evidence-mutation.json 已更新
════ 结果：scope=[H1a,H1b,H2,H3a,H3b,H4] 六个热点全部「变异真变红 + 还原真复绿」 → exit 0 ════
```

`acc2` 日志末段（evidence 的六个键逐个打印）：

```
H1a ok
H1b ok
H2 ok
H3a ok
H3b ok
H4 ok
```

### 四张被改票的 accept（改了就必须证明它还能跑）

把四张票的 accept 用 `accept-run.py --phase D7 --emit` **落成脚本后逐字重放**（只跑我改的那一条，避免动 `SYS-HOME-001` acc1 那段
会短暂停/起 gotenberg 的既有步骤）：

| 票 | 重放的 accept | exit | 备注 |
|---|---|---|---|
| `SYS-EXPORT-001` | acc2 | **0** | 含 `pnpm build:mp-weixin` + 两条 vitest + `--hotspot H1a,H1b` |
| `DOC-MP-002` | acc1 | **0** | 含 `pnpm build:mp-weixin` + vitest + `--hotspot H3a,H3b` |
| `DOC-PUBLISH-001` | acc2 | **0** | 含 `pnpm build:prod` + `--hotspot H4` |
| `SYS-HOME-001` | acc2 | **0** | 含 `pnpm build:prod` + `--hotspot H2` |

> ★ **没有**跑这四张票 acc1（DATA/STATE）的整条 —— 其中 `SYS-HOME-001` acc1 会**短暂停 gotenberg** 造真渲染失败，
> 派单纪律写着「别停」。四张票的 acc1 与本次改动**无交集**（本票没碰它们），D7 门复跑时连同 acc2 一起跑即可。

---

## 5. `evidence.json` 摘要

`doc/waves/regression/D7/accept-strengthened/evidence.json`（27.7 KB）：

```
top keys   : ticket, generated_at, mode, scope, all_six_present, missing, tree_green,
             hotspots, mutations, tree_after, env, previous_mutation_run
hotspots   : H1a, H1b, H2, H3a, H3b, H4   （all_six_present=true, tree_green=true, missing=[]）
mode       : verify-only（accept 2 最后写的这一份）；变异观测值挂在 previous_mutation_run
```

- 每个热点带 `criterion` / `old_criterion` / `mutation` / `behavioral` / `result` / `observed` / `assertions[]`
  —— `observed` 里是**真值**（真请求头、真 DOM 读数、真 URL、真响应码），不是「字段存在」。
- 变异档案另存 `accept-strengthened/evidence-mutation.json`（mutation 模式写，不会被 `--verify-only` 覆盖）；
  `evidence.json.previous_mutation_run` 指向它 —— 这样「verify-only 全绿」不会把变异证据抹掉。
- 逐热点断言明细另存 `accept-strengthened/observations/<HOT>.json`（六个）。

---

## 6. 改了什么（只碰断言与回归资产，**零产品代码**）

**新增（全部在 `touches` 内）**

| 文件 | 作用 |
|---|---|
| `doc/waves/regression/D7/mutation-assert.sh` | 总入口：默认 = 施加变异→断言红→还原→断言复绿（任一条「改坏了还绿」exit 1）；`--verify-only`；`--hotspot H1a,H1b` |
| `doc/waves/regression/D7/accept-strengthened/probe.mjs` | H1a/H1b/H2/H3b/H4 的真浏览器行为判据 |
| `doc/waves/regression/D7/accept-strengthened/probe-h3a.py` | H3a 的剥注释判据 |
| `doc/waves/regression/D7/accept-strengthened/setup-fixture.sh` | 确定性夹具（reseed + 2 张 2400×1600 真 PNG + publish/render + 显式 render 兜底） |
| `doc/waves/regression/D7/accept-strengthened/mutate.py` | 六个已定义变异的精确施加/还原（还原后核对 sha256 与 `git diff` 干净） |
| `doc/waves/regression/D7/accept-strengthened/merge-evidence.py` | 合成 `evidence.json` / `evidence-mutation.json` |
| `doc/waves/regression/D7/accept-strengthened/evidence.json`、`evidence-mutation.json`、`observations/*.json` | 机器证据（回归资产） |

**修改（四张票的 accept）**

| 票 | accept | 改动 |
|---|---|---|
| `SYS-EXPORT-001` | acc2 | **删** `grep -qE 'Authorization\|clientid' export.ts fileHandoff.ts`；**加** `cd ../.. && bash doc/waves/regression/D7/mutation-assert.sh --verify-only --hotspot H1a,H1b`；name/counterfeit 改成行为判据口径 |
| `SYS-HOME-001` | acc2 | **加** `--hotspot H2`；name/counterfeit 说明「真 DOM 五个数 == 同一次接口」 |
| `DOC-MP-002` | acc1 | **删** `grep -q 'previewImage' ThumbStrip.vue` 与 `grep -qE 'showMenu:…' fileHandoff.ts`；**加** `--hotspot H3a,H3b`；name/counterfeit 重写 |
| `DOC-PUBLISH-001` | acc2 | **删** `grep -cE "format.*(docx\|pdf)…"`；**加** `--hotspot H4`；name/counterfeit 重写 |

> 四张票的 `touches`、其他断言、`fileHandoff`、`doc/api-contract.md` **一律没动**。
> 本票**没动** `doc/waves/state.json` 与 `_manifest.json`。

---

## 7. 如实说明：哪几条仍做不到行为化

1. **H3a（`showMenu: true`）是唯一一条**。`showMenu` 是 `wx.openDocument` 的**平台参数**：
   owner 已把真机验收面替换成 H5 + mock 登录（缺 appid / 合法域名），而 H5 上 `uni.openDocument` 不存在、
   代码如实退化成 `window.open`（`fileHandoff.ts` 的平台差异注释），**在 H5 上没有可施加的行为变异**。
   按 ticket §2 的显式例外，它用「**剥注释后的真代码位置**」判，并以「删真代码只留注释」这个变异**证明它与注释无关**。
   要把它变成纯行为判据，需要 appid + 一次真机（微信）跑「下载 PDF → 右上角有保存/转发」。
2. **H1b / H3b / H4 的行为面是 H5**（owner 指定的验收面），**不是真机/体验版**。
   H1b 的机理性结论（MinIO/S3 只看请求头，与服务端无关）在真机分支同样成立——小程序分支把同一个 `header` 交给
   `uni.downloadFile`——但**未在微信里覆盖**；要真机确认需 appid 到位后跑一次「下载 PDF」。
   `uni.shareFileMessage` 的转发本身同样只有代码路径（H5 上 `typeof undefined`）。
3. 其余三条（H1a / H2 / H3b）+ H4 已是纯行为判据（真请求头 / 真 DOM / 真 URL）。

---

## 8. 越界与 WARN

1. **【WARN·本票没修的真问题】无新增**。本次实现过程中**没有**发现产品侧的新缺陷；
   唯一与产品相关的观察是 H1b 变异**成功复现了已修的 S1**（OSS 400 / multiple authentication types），
   说明这条判据确实打在 S1 的维度上——那是**判据有效的证据**，不是新缺陷。
2. **【WARN·夹具的一次假红，已消除】`reseed.sh` 不清 `api.sh` 的 token 缓存**。
   `api.sh` 把 token 缓存在 `$TMPDIR/lqg-verify-token-*`，而 macOS 的 `TMPDIR` 是
   `/var/folders/…/T`（**不是 `/tmp`**）；`reseed.sh` 只清服务端会话，`qa-up.sh:190` 才清客户端缓存。
   我的夹具一开始直接调 `reseed.sh` → 拿着上一个环境的死 token → 所有 `--as staff` 调用 **401
   认证失败，无法访问系统资源** → 夹具假红（表现为「渲染产物没能在 180s 内全部 done」，
   第一次全量变异跑就撞上）。已在 `setup-fixture.sh` / `mutation-assert.sh` 的 reseed 之后补一行
   `rm -f "${TMPDIR:-/tmp}"/lqg-verify-token-*`（与 `qa-up.sh` 同款），并把 unpublish/publish/render 的**业务码逐条打出来**不再吞。
   ★ 这是**工具红不是产品红**，记在这里以免下游误判。
3. **【WARN·夹具不依赖 publish 的异步触发】** 有一次 `publish` 的请求没有落库、`t_lqg_doc_file` 迟迟为 0。
   夹具现在除了 `unpublish + publish`，还会对 `sample_qc / organoid_qc / organoid_score / merged` ×
   `internal / external` **显式调用 `POST /lqg/doc/{sampleId}/{kind}/render?audience=`** 兜底，
   保证产物存在（否则 H1b/H3b/H4 会假红）。
4. **【WARN·`python3` 不许被 homebrew 顶掉】** 夹具一开始往 `PATH` 前面塞了 `/opt/homebrew/bin`，
   把 pyenv 的 `python3` 顶掉，`doc/verify/db.py` 因缺 `psycopg2` 只往 stderr 打一行 → 快照读成空串 → 假红。
   已删掉那行并写进注释。
5. **【WARN·`bash` 是 3.2】** 本机 `/bin/bash` 是 3.2.57，`declare -A` 不可用（脚本里已去掉关联数组）。
6. **【WARN·已知 S3，与本票无关】** `DownloadSheet` 反复开合时 `DownloadBar` 的 Word/PDF 选择会**跨弹层留存**
   （D7 r1 L2 的 S3）。H1b 探针每次都**显式点一次 Word**，以免把「上一次选的是 PDF」误当成 H1b 的产品红。
7. **【有意保留】`code/miniapp/src/pages.json`**：本票**没有** `git checkout` 它。
   当前 `git status` 里它**没有差异**——H5 dev server 把它重生成了与 HEAD 一致的形式（tabBar 仍是 `[index, doc, me]`，
   `grep pagePath` 三行 = `pages/index/index`、`pages/doc/index`、`pages/me/index`）。语义状态未变。
8. **【树守卫的口径】** `mutation-assert.sh` 起点/收尾都断言 `git diff -- code/` 里**只允许** `pages.json` 且它只动 2 行平台注释；
   收尾另断言六个变异目标文件全部干净、`git stash list` 为空（本票**不使用** `git stash`）。
   本次两次全量跑的收尾都是 `git diff -- code/ = []`（dev server 已把它重生回 HEAD 形式）。

---

## 9. 给 D7 门复跑的说明

```bash
# 1) 环境（gate 的 L0.0/L1.0 本来就要求后端在 8094 + jar 新鲜；下面这条会把两端 dev 也起好）
bash doc/waves/tools/qa-up.sh --backend-port 8094 --web-port 8093 --mp-port 9204
export LQG_VERIFY_ENV_FILE="$PWD/.tmp/qa-env/8094/verify.env"   # ★ 不导出 → DATA 段落 8081 → 假「连不上后端」

# 2) 本票的两条 accept（acc1 ≈ 4 分钟、acc2 ≈ 70 秒）
python3 doc/waves/tools/accept-run.py --ticket SYS-ACCEPT-001 --run \
        --json .tmp/sys-accept.json --logdir .tmp/sys-accept-logs

# 3) 单跑某个热点（排障/分片用）
bash doc/waves/regression/D7/mutation-assert.sh --verify-only --hotspot H1a,H1b
bash doc/waves/regression/D7/mutation-assert.sh --hotspot H2      # 变异模式
```

复跑要点：

1. **环境自足**：`mutation-assert.sh` 自己检查 8094/8093/9204，缺哪个只补起哪个（`detach.sh`，**只按 PID 关**）；
   它自己起的进程在退出时按 PID 关掉；已经在跑的**一律复用、收尾不动**（不会误杀 8080 上 Kevin 的服务）。
2. **夹具自己造产物**：`setup-fixture.sh` 先 reseed，再上传两张 2400×1600 真 PNG 挂到 9000001001/sample-qc 的 `pretreat` 位，
   再 `unpublish+publish` + **显式 render 兜底**，等 `t_lqg_doc_file` 全 done（约 4~5 秒，36/36/0）。**不需要人工先灌数据。**
3. **票面按票分片**：四张票的 acc2/acc1 各带 `--hotspot`，跑的是**自己那一两个热点**；
   `--hotspot` 模式**不写** `evidence.json`（只写 `observations/<HOT>.json`），所以四张票的 accept 不会互相覆盖全量证据。
4. **耗时**：`acc1`（六个热点 × 3 次跑的等价物：1 次 preflight 全绿 + 12 次变异/还原）= **≈ 4 分钟**；
   `acc2`（preflight + evidence）= **≈ 70 秒**。都在单条 900s 超时内。
5. **口径替换照旧**：真机 → H5(9204) + mock 登录 + Playwright 真 DOM + 真后端 8094 + 真库 5433；
   Playwright require 锚点 `code/miniapp/package.json`（issue #229）；**PNG 一律不落盘、不进上下文**。
6. **收尾**：`bash doc/waves/tools/qa-up.sh --down --backend-port 8094 --web-port 8093 --mp-port 9204`
   （按 PID 关；`lqg-dev-gotenberg` 全程 running，本票没停）。

---

## 10. 落盘产物索引

| 文件 | 内容 |
|---|---|
| `doc/waves/regression/D7/mutation-assert.sh` | 本票总入口（变异验证 + `--verify-only` + `--hotspot`） |
| `doc/waves/regression/D7/accept-strengthened/` | 探针 / 夹具 / 变异器 / 证据（见 §6） |
| `doc/waves/regression/D7/accept-strengthened/evidence.json` | 六个热点的判据 + 真观测值（27.7 KB） |
| `doc/waves/regression/D7/accept-strengthened/evidence-mutation.json` | 变异前后对照的耐久档案 |
| `doc/waves/regression/D7/accept-strengthened/observations/H*.json` | 逐热点断言明细 |
| `.tmp/sys-accept.json` + `.tmp/sys-accept-logs/` | 本票两条 accept 的结果与日志 |
| `.tmp/sys-accept-001/mutation-full.log`（旧）/ `logs/`（accept 内） | 变异验证的完整 stdout |
| `.tmp/sys-accept-001/emit-*.log` | 四张被改票 acc2/acc1 的逐字重放日志 |
| `doc/waves/reports/SYS-ACCEPT-001.md` | 本报告 |
