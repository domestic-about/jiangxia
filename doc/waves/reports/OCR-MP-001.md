# OCR-MP-001 · 完工报告

- **ticket**：OCR-MP-001（track OCR / phase D5 / size M）—— 小程序 · 拍照识别条：拍照或选图、识别、只往空项里预填并标「请核对」、识别失败不挡手填
- **status**：**done**
- **accept**：**2/2 绿**（票面 `run` 逐字重放，`accept-run.py --ticket OCR-MP-001 --run`，**0 条归一化**：NF1 未命中、NF2 未命中；逐条见 §4）
- **分支**：`task/D5`（未切分支 / 未 push / 未 merge / **未动** `doc/waves/state.json`、`_manifest.json`）
- **验收对象**：小程序 H5 dev server（:9204，H5 代理 → 后端 8094）+ 后端 `ruoyi-admin.jar`（dev profile + `--api-decrypt.enabled=false` + `lqg.ocr.provider=stub`）+ 工作台 dev（:8095）。两次 `qa-up` 的进程号见 §7.1（第一次 4160/1327/3343，**改造后复验**那一次 12459/11421/12196），都已按 PID 关停
- **单测**：`prefill.fixture.spec.ts` **19/19**；小程序全量 **147/147 通过**（改前 146 → 净增 1 条回归用例；10 个 spec 文件全绿）
- **`pnpm type-check`**：`src/pages/sample/ocr/**`、`src/api/ocr.ts`、`src/components/lqg/OcrBar.vue`、`src/components/lqg/FieldRow.vue`、`src/pages/sample/form.vue` **零报错**（仓库里既有的 wot-design-uni / unit-group / history 报错与本票无关，未动）
- **生产包**：`pnpm build:mp-weixin` 重打，`dist/build/mp-weixin/` 是本次产物（`api/ocr.js` 里 `/mp/ocr/recognize` 已编进去、`FieldRow.wxml` 里 `slot="suffix"` 小标已编进去）
- **只读区未动**：`_input/`、`doc/requirements.yaml` 的 text、`doc/authority/*.yaml`、`doc/change-log.md`、`doc/api-contract.md`、`doc/verify/seed/**`、`doc/verify/gen_seed.py`、`doc/verify/api.sh`、`doc/verify/fixtures/**`、`doc/waves/state.json`、`_manifest.json`
- **端口纪律**：只用 8094（后端）/ 8095（工作台）/ 9204（H5）/ 6380（Redis）/ 5433（PG）；**没碰** 8080 / 5432 / 6379；进程一律按 PID 关（**从没用过 `pkill -f 'ruoyi-admin.jar'`**）
- **收尾**：三个进程已 `qa-up.sh --down` 按 PID 关停，8094 / 8095 / 9204 已释放（见 §7.3 的 `--status` 输出）；`code/miniapp/src/pages.json` 未被 dev server 改写（`git status` 里没有它）

---

## §0 状态自检

| 检查项 | 结果 | 证据 |
|---|---|---|
| 分支符合调度器注入的期望 | ✅ PASS | `git branch --show-current` → `task/D5`；全程未切 / 未 push / 未 merge |
| `depends_on` 全部 done | ✅ PASS | **OCR-IMPL-001**（后端 `/mp/ocr/recognize` + 夹具 + 限流）与 **SAMPLE-MP-001**（`formLayout` 的 `showOcr`）产物都在盘，本票直接复用：`accept 2` 第 1 段 `pnpm build:mp-weixin` 过，说明依赖的页面/组件都在 |
| 扫 `doc/change-log.md` 涉及本票的 CR | ✅ PASS | **CR-20260921-08**（视觉方向 A）→ 本票直接照 `direction-a/落地规范.md` §5.7 + `components.scss` 的 `.lqg-ocr*` 写，零色值字面量；**CR-20260918-07** 不动本票的接口形状与页面入口。两条 CR 都与实现无冲突 |
| 4 个 `blueprint_refs` 逐条 `authority_lint.py show` | ✅ PASS | `UI:mp.sample.form.ocr`（四段状态：空闲 / 识别中进度 / 成功标记可展开原文 / 失败一句「没识别出来，请手动填写」不挡填表，仅新增时显示）· `FLOW:F-OCR-01.step1`（chooseMedia 单张 → 压缩长边 ≤ 2000px → 上传）· `step4`（只填空项、不覆盖手填、加标记、改动摘标、可看原文）· `step5`（提交以表单当前值为准）。四条都 `active`，实现逐条对齐 |
| 口径复述 4 条 | ✅ PASS | 见 §1 |
| 环境可用 | ✅ PASS | `qa-up.sh --backend-port 8094 --web-port 8095 --mp-port 9204` 一次起齐；H5 `curl` → 200；后端 `/lqg/sys/ping` → 200 |

**STOP 判定：无。**

★ 与蓝图**无冲突**。两处**措辞差**记在 §6 WARN-1 / WARN-4（都不是冲突，是实现按更严 / 更贴实际的写法落地）。

---

## §1 口径复述（逐条对 accept 与端侧证据核）

| # | 口径（ticket §0 复述 + 权威锚） | 落点 | 机器证据 |
|---|---|---|---|
| 1 | **只往空着的项里填**，已手填的不覆盖 | `prefill.ts` 的 `mergeOcrPrefill`（trim 后为空才算空） | accept 1 fixture **B 例**；端侧 case 04：先手填「手填姓名 / HAND0001」再识别 → 两格原样保留、只有空项被填（`dom-network-transcript.txt`） |
| 2 | 识别失败 / 超时 / 被限流 → 一句「没识别出来，请手动填写」，**不阻塞填表** | `OcrBar.apply()`（一项都没填出来即落失败态）+ `OCR_FAIL_TEXT` | 端侧 case 05（夹具 03 只有噪声）提示逐字 `没识别出来，请手动填写`、失败后仍能手填、提交按钮仍在；**case 09 限流**：第 7 次被限流 → 同一句提示、0 小标、仍能手填（`rate-limit-live.txt`） |
| 3 | 只调自己后端的 `/mp/ocr/recognize`，**不直连任何第三方识别服务** | `api/ocr.ts` 只有一个路径常量；`OcrBar.vue` 不出现任何服务域名 | accept 2 第 4 段（`src` + `dist/build/mp-weixin` 里的域名 / key 词表零命中，见 `build-package-grep.txt`）；端侧 case 07 断言「非本机 / 非代理的识别请求 = 0」 |
| 4 | 识别条只在**新增**时显示 | 页面按 `layout.showOcr` 挂 `OcrBar`（SAMPLE-MP-001 的 fixture 已规定编辑态 `showOcr=false`） | accept 2 第 2 段（`form.vue` 里 `showOcr`）；端侧 case 06：编辑态 / 只读态 `.lqg-ocr` 计数都是 0（`07-ocr-edit-hidden.png` / `08-ocr-view-hidden.png`） |
| 5 | 识别**不落库、不存图**（上游口径 5，D5 QA 门 L1 的断言） | `OcrBar` 用完即弃，不写任何表、不碰 OSS | 端侧脚本在**同一段 flow 前后**读 `t_lqg_sample` / `sys_oss`：`10 → 10`、`0 → 0`（`dom-network-transcript.txt`、`dom-network.json`） |

---

## §2 改了哪些文件

### 2.1 新增（全部落在 `touches` 的 `code/miniapp/src/pages/sample/ocr/**`、`components/lqg/OcrBar.vue`、`api/ocr.ts` 内）

| 文件 | 行数 | 职责 |
|---|---|---|
| `code/miniapp/src/pages/sample/ocr/prefill.ts` | 151 | ★★ 纯函数：`mergeOcrPrefill(form, ocrFields) → {form, marks}`（只填空项 / 空值不填不标 / 表单里没有的键忽略 / 原地改同一个对象）、`readOcrResponse`（响应收口，**两层的入参都收**）、`PREFILLABLE_KEYS`、`recognizedCount`、`OCR_FAIL_TEXT` |
| `code/miniapp/src/pages/sample/ocr/image.ts` | 195 | 选图 + 压缩：`pickImage('camera' \| 'album')`（`uni.chooseMedia`，H5 回落 `uni.chooseImage`）、`compressToMaxEdge`（长边 ≤ 2000，原生 `compressImage` → canvas 两跳，量不出尺寸就原样返回）、取消与错误的可判定包装 |
| `code/miniapp/src/pages/sample/ocr/prefill.fixture.spec.ts` | 197 | accept 1 的 runner：fixture 驱动，19 条断言（结构守卫 + 逐例 + 跨例口径 + 响应收口回归） |
| `code/miniapp/src/api/ocr.ts` | 143 | `startOcrUpload(filePath, {stubCase, onProgress}) → {task, done}`：`uni.uploadFile` → `/mp/ocr/recognize`，根地址与鉴权头**与 `utils/request.ts` 同源** |
| `code/miniapp/src/components/lqg/OcrBar.vue` | 276 | 识别条四态（idle / recognizing / done / failed）+ 原文折叠 + 隐私授权（`uni.requirePrivacyAuthorize` 特性检测）+ 上传 abort |
| `code/miniapp/scripts/shots-ocr-mp001.mjs` | 396 | 端侧取证主脚本（H5 + Playwright 真 DOM，9 个 case，见 §5） |
| `code/miniapp/scripts/shots-ocr-mp001-ratelimit.mjs` | 186 | 限流路径端侧取证（清桶 → 烧 6 次 → 第 7 次走浏览器） |

### 2.2 修改

| 文件 | 改了什么 | 为什么 |
|---|---|---|
| `code/miniapp/src/pages/sample/form.vue` | 占位识别条换成真组件；`ocrMarks` 状态 + `dropOcrMark` + `onPrefilled` + `onBeforeRecognize`；两个 `FieldRow` 传 `:ocr-mark`；`?stubCase=` 透传 | 本票的主接线 |
| `code/miniapp/src/components/lqg/FieldRow.vue` | 新增 `ocrMark` prop；输入行用 `wd-input` 的 `#suffix` 槽、seg / 只读行用 `.fr__val` 包一层 | 「识别 · 请核对」小标要落在值右侧（§5.7） |
| `code/miniapp/src/utils/request.ts` | `resolveBaseUrl` 由私有改为**导出**（+ 注释说明为什么只能有一个根地址来源） | `uploadFile` 是另一个 API、不走 `request()`；两处各拼一次就是「H5 代理开着、上传却直连 8081」 |
| `code/miniapp/src/types/components.d.ts` | 自动生成：新增一行 `OcrBar: typeof import('./../components/lqg/OcrBar.vue')['default']` | 新组件被 unplugin 自动登记（生成物，随组件走） |

**没有**新增 Flyway / 表 / 接口 / 类 —— 本票是纯前端票；识别接口是上游 OCR-IMPL-001 的产物，本票只消费。

### 2.3 越出 `touches` 的改动（2 处，都已在任务书里预告会记 WARN）

1. **`code/miniapp/src/utils/request.ts`**（新增导出 `resolveBaseUrl`）：`uni.uploadFile` 不走 `request()`，根地址必须同源，否则 H5 代理被绕过。**不导出的话两条路会各拼一次根地址**（见 §2.2）。
2. **`code/miniapp/src/components/lqg/FieldRow.vue`**（新增 `ocrMark` prop）：小标落在每个被预填的 cell 内，只能改通用的行组件。改法是**纯增量**（默认 `false`，老调用方零影响，SAMPLE-MP-001 的 22 条 fixture 与 147 条全量单测都仍绿）。

**没有**碰任何被显式列为只读的文件，也**没有**新建 `OcrMark.vue`（落地规范 §5.7 的 OcrMark 就是 `.lqg-tag--ocr` 那一块，已按 `CryoBatchSheet.vue` 的既有写法内联在 FieldRow 里）。

---

## §3 视觉与红线

- **方向 A 落地规范 §5.7**：`.lqg-ocr` 一张卡（左实底主按钮 + 右 soft 按钮、高 44、圆角 12）、下方 12 弱字说明、末尾青绿文字链「查看识别到的原文」、识别中两按钮置灰 + 细进度条、失败**只换说明文字**（不弹窗、不挡表单）。样式类全部复用 `src/style/components.scss` 的 `.lqg-ocr*` / `.lqg-tag--ocr`，`OcrBar.vue` 只补了 5 个类（置灰 / 进度条 / 底部行 / 计数 / 原文区）。
- **零色值字面量**：`grep -rnE '#[0-9a-fA-F]{3,8}' src/components/lqg/OcrBar.vue src/pages/sample/ocr/ src/api/ocr.ts` → 无命中（SYS-MP-001 的 accept 同向）。
- **不裸写控件**：按钮用 `.lqg-ocr__btn`（既有范式类），没有引入新组件库。
- 失败态截图可肉眼核：`06-ocr-failed-manual-ok.png`（提示变琥珀色，表单照常可填）。

---

## §4 accept 逐条 ✅ / ❌ + 关键输出

命令（票面逐字重放，唯一附加参数是执行器要求的 `--logdir`）：

```bash
export LQG_VERIFY_ENV_FILE="$PWD/.tmp/qa-env/8094/verify.env"
python3 doc/waves/tools/accept-run.py --ticket OCR-MP-001 --run \
  --json .tmp/ocr-mp-accept.json --logdir .tmp/ocr-mp-accept-logs
```

```
[run] 2 条 accept，单条超时 900s
  ✓ OCR-MP-001 acc1 [STATE] 预填合并规则过 fixture：只填空项、已手填的不覆盖、空值不填不标、表单里没有的键忽略 (0.9s)
  ✓ OCR-MP-001 acc2 [API] 构建是本次产物；识别条只在新增时出现；小程序包里没有任何第三方识别服务的域名或密钥 (4.5s)
[ok] 结果落盘 .tmp/ocr-mp-accept.json
[run] 通过 2/2
```

**归一化：两条 accept 都是 `nf: []`**（`accept-result.json` 里逐条写着）——NF1（去 `--fresh-module`）与 NF2（补 mvn 三参数）都**没命中**，票面 `run` 原样跑通。落盘的逐字脚本：`accept-acc1-replayed.sh` / `accept-acc2-replayed.sh`。

### acc1 · STATE ✅

重放脚本（`accept-acc1-replayed.sh`）：

```bash
cd code/miniapp &&
grep -q 'doc/verify/fixtures/prefill-cases.json' src/pages/sample/ocr/prefill.fixture.spec.ts &&
! grep -nE '\.(skip|todo|only)\(' src/pages/sample/ocr/prefill.fixture.spec.ts &&
pnpm vitest run src/pages/sample/ocr/prefill.fixture.spec.ts --reporter=json --outputFile=/tmp/lqg-prefill.json >/dev/null &&
jq -e '.numFailedTests == 0 and .numPassedTests >= 5' /tmp/lqg-prefill.json &&
node -e "const c=require('../../doc/verify/fixtures/prefill-cases.json').cases; if(c.length!==5||!c.some(x=>x.case.startsWith('B-'))||!c.some(x=>x.case.startsWith('E-'))) process.exit(1)"
```

关键输出（日志 `accept-acc1.sh.log`，末行 `true` 就是 `jq -e` 的那一关）：

```
true
```

单测实况（另跑一遍给人看）：

```
 ✓ src/pages/sample/ocr/prefill.fixture.spec.ts (19 tests) 3ms
 Test Files  1 passed (1)
      Tests  19 passed (19)
```

**反例自检**（确认这 19 条不是「怎么改都绿」）：
- 把 `mergeOcrPrefill` 换成 `Object.assign(form, ocrFields)` → B 例（手填被覆盖）与 E 例（`internalNo` 落进表单）同时红；
- 把空串也填进去并打标 → C 例（`expectMarks` 多一个 `donorName`）红；
- 删掉 fixture 里的 B、E 两条 → 最后一段 `node -e` 红（这就是它存在的原因）。

### acc2 · API ✅

重放脚本（`accept-acc2-replayed.sh`）：

```bash
cd code/miniapp && rm -rf dist/build/mp-weixin && pnpm build:mp-weixin >/dev/null &&
grep -q "@/components/lqg/OcrBar.vue" src/pages/sample/form.vue && grep -q 'showOcr' src/pages/sample/form.vue &&
grep -q 'chooseMedia' src/components/lqg/OcrBar.vue && grep -q '/mp/ocr/recognize' src/api/ocr.ts &&
! grep -rnEi 'aliyuncs\.com|dashscope|api\.weixin\.qq\.com/cv|baidubce|tencentcloudapi|secret[_-]?key|api[_-]?key' src dist/build/mp-weixin
```

关键输出（日志 `accept-acc2.sh.log`；exit 0 即全绿）：

```
# 构建（本次产物）
DONE  Build complete.
# 四段 grep 全过；最后一段词表在 src 与 dist/build/mp-weixin 里零命中
```

产物自查留档在 `build-package-grep.txt`：

```
$ grep -rnEi 'aliyuncs\.com|dashscope|api\.weixin\.qq\.com/cv|baidubce|tencentcloudapi|secret[_-]?key|api[_-]?key' src dist/build/mp-weixin
(无命中 —— 绿)

$ grep -rn 'mp/ocr/recognize' dist/build/mp-weixin | head
dist/build/mp-weixin/api/ocr.js:1:"use strict";…C="/mp/ocr/recognize";function p(t,e={}){…r=l.index.uploadFile({url:`${d.resolveBaseUrl()}${C}`,…

$ grep -rn '识别 · 请核对' dist/build/mp-weixin | head -3
dist/build/mp-weixin/components/lqg/FieldRow.wxml:1:…<text wx:if="{{m}}" class="lqg-tag lqg-tag--ocr fr__mark data-v-49e36e1b" slot="suffix">识别 · 请核对</text>…
```

---

## §5 端侧取证（H5 + Playwright 真 DOM）—— 以及**真机档未覆盖的如实说明**

### 5.1 ★ 真机 / 体验版录屏：**未覆盖**

票面 §4.1 要「真机上识别成功与识别失败各一段录屏或截图序列」。**本票没有做真机 / 体验版**，原因不是省事，而是**测试环境还不存在**：真机与体验版要扫一台能访问后端的测试机，
那是下一张票 **SYS-STAGING-001** 的交付物（阿里云测试机与域名 `songjian.tianda.studio` 已拿到，但机器上的部署还没做）。本 agent 的沙箱里微信开发者工具的 CLI 也跑不通（要写
`~/Library/Application Support/微信开发者工具/**` → EPERM，且首次要扫码），这条上游 SAMPLE-MP-001 已经踩过并留档。

**本票的等价覆盖**：H5 dev server（真渲染）+ Playwright 真 DOM + 真后端 + 真夹具，断言到**具体 DOM 文本 / 输入值 / 网络响应**，截图落盘。**未覆盖的部分明说**：
相机 `wx.chooseMedia` 的原生采集、真机的压缩与上传链路、体验版包体表现。这三项在 SYS-STAGING-001 部署完成后补一段真机录屏（同一份脚本的 case 清单可直接照抄）。

### 5.2 主脚本覆盖的 9 条路径（`shots-ocr-mp001.mjs`，`exitCode = 0`；**34 条 `[assert] … OK`、0 条 FAIL**）

| case | 覆盖的口径 | 关键断言值（截自 `dom-network-transcript.txt`） |
|---|---|---|
| 01 | 识别成功（夹具 01-印刷标签） | 供体姓名=`测试供体甲`、性别=`男`、年龄=`56`、住院号=`ZY0000001`、组织类型=`肝组织`；**五格都挂「识别 · 请核对」**；`.ocr__count` = `已识别 5 项` 且 DOM 小标数 = 5 |
| 01b | 来源单位（**档案默认值**，不是识别读出来的） | 值 = `A 医院`、**不挂小标**（识别也认出了 `sourceUnitName`，但那格的值是登录人档案带的 → 见 §6 WARN-3） |
| 02 | 原文可展开 | 点「查看识别到的原文」后 5 行原文逐字出现（含 `姓名：测试供体甲`） |
| 03 | 用户改动 → 该格摘标 | 供体姓名改成 `医生改过的名字` → 该格小标 false；住院号小标仍 true |
| 04 | **手填优先**（新开一页，先手填姓名 + 住院号再识别） | `手填姓名` / `HAND0001` 原样保留、两格**无**小标；空项年龄被填 `56` 且有标 |
| 05 | 识别失败（夹具 03-只有噪声） | 提示逐字 `没识别出来，请手动填写`；六格里五格仍空、0 小标；失败后能继续手填、提交按钮仍在；失败态原文可见（3 行噪声） |
| 06 | 识别条只在新增时出现 | 编辑态 / 只读态 `.lqg-ocr` 计数**都是 0** |
| 07 | 「从相册选」这条入口 | 走 `uni.chooseImage` 回落路径，夹具 04 → 住院号=`ZY0000002`、性别=`女`；只标 2 格（`已识别 2 项` = DOM 小标数） |
| 08 | 识别中态（进度条 + 按钮置灰） | 用 route 把上传挂住 → `.ocr__bar` = 1、两个按钮都 `disabled`；放行后转成功态 |

网络侧同时断：（a）每次请求都打到**自己后端的** `http://127.0.0.1:9204/lqg-api/mp/ocr/recognize`（H5 代理），**非本机 / 非代理的识别请求 = 0**；（b）`clientid` 与 `Bearer` 都在；（c）响应体里 `fields` 只出现后端白名单的键，**没有** `internalNo` / `verifyStatus` 被夹带。

### 5.3 限流路径（`shots-ocr-mp001-ratelimit.mjs`，`rate-limit-live.txt`）

```
[rate] 删除 redis 桶 1 个，等 62s 让 JVM 里的旧桶状态过期
[rate] extC 前 6 次：200,200,200,200,200,200（应全是 200）
[rate] 第 7 次（浏览器点击）提示 = "没识别出来，请手动填写"
[rate] 失败态 DOM 小标数 = 0（应 0）
[rate] 第 7 次响应体 = {"code":500,"msg":"识别太频繁了，请稍后再试（每分钟最多 6 次）","data":null}
[assert] 限流 → 同一句失败提示 OK
[rate] 限流后仍能手填 = "限流后手填"，提交按钮 = 1
[assert] 限流不阻塞填表 OK
```

★ 这一步**必须**先清桶再等 >60s（上游 OCR-IMPL-001 的硬坑 1：Redisson 桶状态 24h 存活，删了 redis key 同一个 JVM 实例仍持旧状态）。脚本把「清桶 + 等 62s」内建了，**谁重跑都不用记这条**。

### 5.4 落库断言（同一段 flow 前后）

```
[db] 识别前后 t_lqg_sample = 10 → 10  sys_oss = 0 → 0
[assert] 识别不落库、不存图 OK（行数识别前后不变）
```

### 5.5 取证目录清单（`doc/waves/reports/OCR-MP-001/`）

```
01-ocr-idle.png                       空闲态：两个按钮 + 「识别结果仅作预填，请核对后提交」
02-ocr-success-marks.png              识别成功：五格「识别 · 请核对」+「已识别 5 项」+ 原文展开
03-ocr-mark-cleared-after-edit.png    改过的那格小标消失，别的格还在
04-ocr-manual-first.png               先手填两项（识别前）
05-ocr-prefill-only-blank.png         识别后：手填的没被覆盖，只有空项被填
06-ocr-failed-manual-ok.png           失败态：一句「没识别出来，请手动填写」+ 表单仍可填 + 原文可见
07-ocr-edit-hidden.png                编辑态没有识别条
08-ocr-view-hidden.png                只读态没有识别条
09-ocr-album-entry.png                「从相册选」入口（夹具 04）
10-ocr-recognizing.png                识别中：按钮置灰 + 细进度条
11-ocr-rate-limited-manual-ok.png     被限流后：同一句提示 + 仍能手填
dom-network-transcript.txt            上述 9 个 case 的逐条断言输出（含 [db] 前后行数）
dom-network.json                      每次识别的请求头 / 响应体 / 最终各格的值
rate-limit-live.txt                   限流路径全过程
rate-limit-calls.json                 烧额度 6 次 + 浏览器第 7 次的响应体
accept-result.json / accept-acc1.sh.log / accept-acc2.sh.log / accept-acc{1,2}-replayed.sh
build-package-grep.txt                生产包词表零命中 + /mp/ocr/recognize 已编入 + 小标已编入
```

**★ 全程没有把任何 PNG 读进上下文**：脚本只写盘，断言读的是 DOM 文本 / 输入值 / 响应体。

---

## §6 WARN 清单

| # | 级别 | 内容 | 处置 |
|---|---|---|---|
| WARN-1 | 记 | 票面 §2 写「状态：成功（「已识别 N 项」+ 可展开的原文）」、而 `UI:mp.sample.form.ocr` 写「条下方可展开『查看识别到的原文』」——两处**措辞不同但不冲突**（都要求能看原文）。实现按蓝图 + 方向 A §5.7 的措辞：空闲 / 成功 / 失败**都能展开**，成功态额外出「已识别 N 项」。 | 已按蓝图；无需改票 |
| WARN-2 | 记 | 票面 §2 的识别条说明小字写「按纸质表拍照可自动填表；识别结果请核对后再提交」，而 `UI:mp.sample.form.ocr` 的权威文案是「识别结果仅作预填，请核对后提交」。**蓝图优先** → 实现用权威那句（端侧 case 01 断言逐字读过）。 | 已按蓝图；记在这里免得下一张票以为漏了 |
| WARN-3 | 记（口径判断，不是冲突） | 「识别 · 请核对」小标按 `UI:mp.sample.form.ocr`/§5.7 只说明「这一格的值是识别写的」。识别**也会**认出 `sourceUnitName`，但外部新增时那格在识别前已经带了登录人档案里的单位名（`load()` 的既有逻辑）→ 那格的值不是识别写的，**不挂标**。为把这事做实，`OcrBar` 新增一个 `recognizing` 事件：页面在识别开始那一刻给表单拍快照，回来只给「原本为空、这次被填」的格挂标。副作用：`.ocr__count` 数的是**真的填了几项**（夹具 01 → 5 项，不是接口回了 6 个键）。 | 实现即结论；上游 OCR-IMPL-001 的「整行全等」单位名口径不受影响 |
| WARN-4 | 记 | 票面 §2 要的压缩是「长边 ≤ 2000px」。H5 的 `uni.compressImage` 不存在（uni-h5 没实现），所以 `image.ts` 做三跳：量尺寸（`getImageInfo` / `Image`）→ 原生 `compressImage` → canvas；**量不出尺寸就原样上传**（宁可上传大图，也不因量不出尺寸把用户选的图丢掉）。真机 / 开发者工具上走的是原生那一跳，**未在真机验证**（同 §5.1）。 | 记；SYS-STAGING-001 补真机时一并验 |
| WARN-5 | 越界 | 见 §2.3 的两处（`utils/request.ts` 新增导出、`FieldRow.vue` 新增 prop）。 | 已列出，等上级裁 |
| WARN-6 | 纪律 | 起环境时工作台的 dev server 会把 **`code/plus-ui/.eslintrc-auto-import.json`**（自动生成物）改脏（少 4 个 `ElMessage*` 键）。这不是本票改的代码，已 `git checkout --` 还原；**后续谁起 8095 不要把它算成本票的改动**。 | 已还原；见 §7.2 的 `git status` |

---

## §7 收尾

### 7.1 长进程

三个进程全部按 PID 关停（`bash doc/waves/tools/qa-up.sh --down --backend-port 8094 --web-port 8095 --mp-port 9204`），**没有用 `pkill -f 'ruoyi-admin.jar'`**（8080 上是 Kevin 的本机服务，issue #46）：

```
── qa-up --down（后端 8094）
  ✓ 已按 PID 关停 工作台 dev(8095)（pid 12196）
  ✓ 已按 PID 关停 小程序 H5(9204)（pid 12459）
  ✓ 已按 PID 关停 后端(8094)（pid 11421）
  ✓ 8094 已释放
  ✓ 8095 已释放
  ✓ 9204 已释放
```

**未留给任何人**：本票不需要常驻进程（上游 OCR-IMPL-001 的后端已按 PID 收过）。

### 7.2 `git status --porcelain`（收工实况）

```
 M code/miniapp/src/components/lqg/FieldRow.vue
 M code/miniapp/src/pages/sample/form.vue
 M code/miniapp/src/types/components.d.ts
 M code/miniapp/src/utils/request.ts
?? code/miniapp/scripts/shots-ocr-mp001-ratelimit.mjs
?? code/miniapp/scripts/shots-ocr-mp001.mjs
?? code/miniapp/src/api/ocr.ts
?? code/miniapp/src/components/lqg/OcrBar.vue
?? code/miniapp/src/pages/sample/ocr/
?? doc/waves/reports/OCR-MP-001/
```

- **只剩本票改动**：4 个修改（全部在本票的 `touches` 或 §2.3 的越界清单里）+ 5 个新增。
- 自动生成物 `code/miniapp/src/types/components.d.ts` 里**唯一**的变化是新组件那一行，随本票走。
- `code/miniapp/src/pages.json` **没有**出现在 `git status` 里（H5 dev 这次没改写它）—— 无需 `git checkout`。
- 工作台的 `code/plus-ui/.eslintrc-auto-import.json` 已还原（WARN-6）。

### 7.3 端口

`bash doc/waves/tools/qa-up.sh --status --backend-port 8094 --web-port 8095 --mp-port 9204`（**三个端口都要显式带**，
否则它按默认的 8093 / 9202 去探、打印的是别人的端口）：

```
── qa-up --status（后端 8094）
  ! 后端 8094 空闲
  ! 工作台 8095 空闲
  ! 小程序H5 9204 空闲
```

`lsof -ti tcp:<port> -sTCP:LISTEN` 复核：8094 / 8095 / 9204 → 全 free。
Kevin 的 8080（pid 77245 在听）与 6379（pid 59180，compose 的 Redis）**全程未碰**，本票用的是 6380 / 5433。

---

## §8 给下游的坑（SYS-STAGING-001 / D5 QA / 下一张 MP 票）

1. **响应有两层，`readOcrResponse` 两层都收**（本票最大的坑，代价是一次假失败）：`api/ocr.ts` 的 `uploadFile` 直接 resolve **`body.data`**，而 `utils/request.ts` 那条路给的是**整个响应体**。只认一层时，接口把六个字段都回全了、页面照样说「没识别出来，请手动填写」，而**单测喂的是另一层 → 全绿**。已加回归用例（`readOcrResponse` 那两条）；谁再写「收口函数」，先想清楚调用方手里是哪一层。
2. **「识别 · 请核对」小标落在哪儿**：文本 / 数字行走 `wd-input` 的 `#suffix` 槽（不是 `wd-cell`，没有 `wd-cell__title`），seg / 日期 / 只读行走 `wd-cell` 的默认槽。**写 Playwright 脚本时别只按 `.wd-cell__title` 找行** —— 五个字段里只会命中一个（性别），其余全 null，看起来像实现红了，其实是选择器错了。脚本里的 `rowLocator()` 两种都覆盖。
3. **限流路径取证要「清桶 + 等 62s」**：Redisson 桶状态在 JVM 里 24h 存活，删 redis key 不够（上游硬坑 1）。脚本已内建；另外同一用户每分钟 6 次，**主脚本与限流脚本必须用不同身份 / 分两次跑**（主脚本用 extA，限流脚本用 extC）。
4. **别把「已识别 N 项」当接口字段数**：N 是**真的填进表单的项数**（WARN-3）。外部新增时来源单位那格在识别前已有档案默认值，所以夹具 01 的 6 个字段只填 5 格、只标 5 格。
5. **`uni.chooseMedia` 只在小程序 / App 有**：H5 必须回落 `uni.chooseImage`（`image.ts` 已做），否则 H5 上点按钮直接「当前环境不支持选图」。压缩同理：H5 没有 `uni.compressImage`，走 canvas 那一跳。
6. **`OcrBar` 的样式只补了 `.lqg-ocr` 覆盖不到的部分**（置灰 / 进度条 / 原文区 / 计数），主体仍用 `components.scss` 的既有类。后续谁做别的识别入口，**别再写一套 `.xxx-ocr`**，直接复用 `.lqg-ocr*` + `.lqg-tag--ocr`。
7. **`?stubCase=` 只是端侧取证的旁路**（透传给 `X-Ocr-Stub-Case`）。生产上 `StubOcrProvider` 不存在、这个参数没人读；**不要**把它接进任何业务分支。
