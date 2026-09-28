---
ticket: SYS-STAGING-001
track: SYS
phase: D5
size: M
req_refs:
  - REQ-SYS-010
depends_on:
  - SAMPLE-MP-002
  - SAMPLE-WEB-001
  - CRYO-MP-001
  - EMBED-MP-001
  - OCR-MP-001
touches:
  - code/deploy/test/**
  - code/deploy/common/**
  - doc/verify/verify.test.env.example
  - code/RuoYi-Vue-Plus/ruoyi-admin/src/main/resources/application-test.yml
  - code/RuoYi-Vue-Plus/ruoyi-admin/Dockerfile
  - code/RuoYi-Vue-Plus/pom.xml
adr_refs:
  - ADR-0002
  - ADR-0008
blueprint_refs:
  - FLOW:F-OPS-01.step1
  - FLOW:F-OPS-02.step2
accept:
  - name: "测试环境跑的就是当前这份代码（提交号对得上）、库是 PostgreSQL、走 test 配置、测试数据已灌，且 **mock 只认预置演示身份**（体验版的登录页快捷入口靠它，但任意身份必被拒）"
    form: API
    run: |-
      export LQG_VERIFY_ENV_FILE=doc/verify/verify.test.env &&
      PING="$(bash doc/verify/api.sh --as admin GET /lqg/sys/ping)" &&
      printf '%s' "${PING}" | jq -e --arg c "$(git -C code rev-parse --short=12 HEAD 2>/dev/null || git rev-parse --short=12 HEAD)" '.code==200 and .data.db=="PostgreSQL" and .data.profile=="test" and .data.encryptEnabled==true and .data.mockLogin==true and (.data.buildCommit|startswith($c[0:7]))' &&
      bash doc/verify/api.sh --as admin GET '/lqg/sample/list?pageSize=200' | jq -e '([.rows[].id|tostring]|sort)==["9000001001","9000001002","9000001003","9000001004","9000001005","9000001006","9000001007","9000001008","9000001009"]' &&
      bash doc/verify/api.sh --as admin GET /lqg/home/todo | jq -e '.data.pendingSamples==2 and .data.pendingTissue==2 and .data.pendingOrganoid==0 and .data.pendingEmbeds==1 and .data.cryoOverdue==2 and .data.pendingExtUsers==2 and .data.renderFailed==0' &&
      bash doc/verify/api.sh --as staff GET /mp/me | jq -e '.code==200 and .data.identity=="internal"' &&
      EVIL="$(bash doc/verify/api.sh --as phone:evilprobe:13900000000 GET /mp/me 2>&1 || true)" && printf '%s' "${EVIL}" | grep -q '不在允许清单里' &&
      case "$(sed -n 's/^LQG_API_BASE=//p' doc/verify/verify.test.env)" in https://*) true ;; *) false ;; esac
    counterfeit: |-
      部署脚本推的是上周构建的旧镜像 → buildCommit 与本地 HEAD 对不上红。远程环境没法看 jar 的 mtime，只能靠这个。
      测试环境误用了 dev 或 prod 配置 → profile 不是 test 红。
      加密口令和 seed 不一致 → 管理面还能登（账号密码不走加密列），但**首页待办计数会不对**（密文读成乱码，外部档案/来源单位判定跟着错）。
      测试环境灌的不是 seed（或 reseed 没跑完）→ 样本 id 集合与首页待办计数对不上红。
      测试环境走的是 http → 最后一段红：小程序体验版的请求域名必须是 https。
      ★ mock 这一段是 **2026-09-28 二次修订**，两次都写明白：
        · 第一版（拿到 appid 后）要求 `mockLogin==false` —— 把 mock 整个关掉，堵住「公网可用 mock 冒充内部身份并读写业务数据」（台账 #346）。
        · 第二版（Kevin 要求体验版保留登录页快捷入口后）改成 **`mockLogin==true` 且「预置身份可用 + 任意身份必被拒」**：
          关掉 mock 会让甲方对接人登不进演示数据（seed 里绑定的 openid 换不出来），所以改成**收窄**而不是关闭 ——
          后端 `WxIdentityResolver` 现在只认 `lqg.auth.mock-identities` 清单里的 key，且**手机号由服务端从清单取**，
          不再采信调用方传的 phoneCode 值（原来正是这条路让「报出种子里的内部人员号码」就能拿到 internal 身份）。
          两段断言合起来才是完整口径：`--as staff` 必须**能**登且拿到 `identity=="internal"`（证明快捷入口真的可用，
          不是「全拒了」也算过），而自造身份 `phone:evilprobe:13900000000` 必须**被拒**并回「不在允许清单里」。
          也就是说：**谁把清单挪掉/改成不校验，或者把 mock 关掉，这条都会红。**
       ★ 关于原本那句「外部隔离在远程环境同样成立」：它用的 `--as extA / extC` 是 mock 身份，第一版改写到 admin 账号的
        种子指纹时就已去掉；「外部隔离」这条产品性质由本地环境的 AUTH-EXT-002 acc1/acc2 等票承担（那里有完整的
        可见集合 / 白名单 / 写保护断言）。这里只承担「环境本身对不对 + mock 的门是不是只开给预置身份」。
  - name: "测试机的数据库与缓存端口不对公网开放；compose 与部署脚本在仓库里、密码不在"
    form: STATE
    run: |-
      HOST="$(sed -n 's#^LQG_API_BASE=http[s]*://\([^/:]*\).*#\1#p' doc/verify/verify.test.env)" && test -n "${HOST}" &&
      SSH_HOST="$(sed -n 's/^LQG_TEST_HOST=//p' code/deploy/test/.env 2>/dev/null || true)" &&
      { [ -n "${SSH_HOST}" ] || SSH_HOST="${HOST}"; } &&
      PROOF="$(ssh -o BatchMode=yes -o ConnectTimeout=10 "root@${SSH_HOST}" "LQG_TEST_PUB_IP='${SSH_HOST}' bash /opt/lqg-test/remote/port-proof.sh")" &&
      state_of() { printf '%s' "${PROOF}" | awk -v k="$1" -v want="$2" 'index($0,k){seen=1; if ($NF==want) ok=1} END{exit !(seen&&ok)}'; } &&
      state_of "${SSH_HOST}:15432" unreachable && state_of "${SSH_HOST}:16379" unreachable &&
      state_of "${SSH_HOST}:19000" unreachable && state_of "${SSH_HOST}:19001" unreachable &&
      state_of "${SSH_HOST}:8082"  unreachable && state_of "${SSH_HOST}:8083"  unreachable &&
      state_of 127.0.0.1:15432 reachable && state_of 127.0.0.1:16379 reachable && state_of 127.0.0.1:8083 reachable &&
      state_of "${SSH_HOST}:443" reachable && state_of "${SSH_HOST}:80" reachable && state_of "${SSH_HOST}:22" reachable &&
      test -f code/deploy/test/docker-compose.yml && test -f code/deploy/test/deploy.sh && test -f code/deploy/test/.env.example &&
      ! git ls-files --error-unmatch code/deploy/test/.env >/dev/null 2>&1 &&
      ! grep -nE '^[[:space:]]*-[[:space:]]*"?(0\.0\.0\.0:)?(5432|6379):' code/deploy/test/docker-compose.yml
    counterfeit: |-
      compose 里顺手写了 ports: "5432:5432" 方便自己连库 → 公网可达，探针连得上红 / grep 红。不允许的连接被拒才算对。
      ★ 2026-09-28 重写（本例此前**从未真正通过**，两次都是环境/平台假红）：
        ① 原写作 `https\?://`，而 **macOS 自带 BSD sed 不支持 BRE 的 `\?`** → HOST 恒为空 → `test -n` 直接红；
        ② 改成 `nc -z "${HOST}" 5432` 之后仍然不可信：**本机 DNS 被本地代理劫持成 fake-IP（198.18.1.218）**，
           该代理接受**任意** TCP 连接（实测端口 1、12345 都「连上」）→ nc 断言在本机恒绿或恒红，都没有判别力。
        现在改成：**在测试机上**换发夹探（`ssh` 跑 port-proof.sh，它自带阳性对照 80/443/22、阴性对照 1.1.1.1:59999），
        断言 ①本栈 6 个发布端口的公网 IP 一律 unreachable ②环回同端口 reachable（服务确实在跑）③阳性对照 reachable
        （证明探针在区分，不是全都 unreachable）④仓库/密码那三条不变。
        变异验证：把 `U 15432` 改成 `P 15432` → 红；把阳性对照 `P 443` 改成 `U 443` → 红（两次都实测过）。
      把带密码的 .env 提交进了 git → 红。
---

# SYS-STAGING-001 · 测试环境与可试用版：一台测试机上 compose 起全套、灌测试数据、上传小程序体验版

## §0 状态自检（实施前必过，不过就 STOP 报 Kevin）

- [ ] 当前分支符合调度器注入的期望（`track/SYS` worktree）——不写死 `feature/dayN`
- [ ] `depends_on` 全部 done：**SAMPLE-MP-002**、**SAMPLE-WEB-001**、**CRYO-MP-001**、**EMBED-MP-001**、**OCR-MP-001**
- [ ] 扫 `doc/change-log.md`：涉及本 ticket 的 CR **以 CR 为准**（CR 覆盖本文）
- [ ] 逐条取权威（别全文读蓝图）：`python3 ~/claude-config/skills/xuqiu/scripts/authority_lint.py show <锚>`，锚见上方 `blueprint_refs`；接口形状见 `doc/api-contract.md`
- [ ] 必读：
  - 合同第三条：收到首期款后 **3 周内**提供小程序送检与登录的可试用版本——本张就是那个交付点
  - 合同第四条 2(3)：测试阶段使用乙方测试服务器，**仅使用测试数据**
  - 栈包 gotchas §6.5：小程序上传**只走本地构建 + miniprogram-ci**，不要用 CI 机器构建（不同 OS 产物不同，体验版真机上组件渲染为空）
  - **ADR-0002**（全文在 `doc/_adr/`，`authority_lint.py show ADR-0002` 取结构化口径）
  - **ADR-0008**（全文在 `doc/_adr/`，`authority_lint.py show ADR-0008` 取结构化口径）
- [ ] 口径复述（本张最容易做反的）：
  1. 测试环境的 mock 登录是开的（ADR-0008 允许 test），但**测试机不放任何真实数据**；甲方试用时填进来的东西到期清掉。
  2. 数据库、Redis 端口不映射到公网。
  3. `buildCommit` 要真的注入（打包时传 git 提交号），远程环境只能靠它反 stale。
  4. 小程序此刻多半还没有甲方主体的 appid → 先用乙方测试号 / 测试 appid 发体验版；appid 走环境变量，别写死。

## 1 背景与口径

合同约定的第一个交付点：小程序送检与登录的可试用版本。它同时是后面几个任务 QA 门里「真机 / 体验版」那一档的落脚处。

## 2 实现要点

- `code/deploy/common/`：后端 Dockerfile（多阶段：maven 构建 → JRE 运行，构建参数 `BUILD_COMMIT` 注入 `/lqg/sys/ping` 的 `buildCommit`）、nginx 配置模板、`wait-for` 小脚本。
- `code/deploy/test/docker-compose.yml`：`postgres:16-alpine`（库 `lqg_test`）、`redis`、`minio`、`backend`（`--spring.profiles.active=test`）、`nginx`（工作台 dist + `/prod-api` 反代 + HTTPS）。
  `.env` 放密码与域名，**不进 git**，给一份 `.env.example`。
- 上传上限统一（CR-20260923-09）：后端单个文件 50MB、一次请求合计 60MB，超了回 `code=413` 与中文提示；nginx 一律 `client_max_body_size 60m`（compose 里的 nginx 与宿主机那层都是），别在某一层留小的缺省值。
- actuator 不对外（CR-20260923-09）：nginx 对 `/actuator` 与 `/prod-api/actuator` 两条路一律回 404。
- `application-test.yml`：同 dev 的加密口令（seed 的密文按它算的）、`lqg.auth.mock-login=true`、`lqg.ocr.provider=stub`、api 加解密关。
- `code/deploy/test/deploy.sh`：本地构建镜像 → 推到测试机 → `docker compose up -d` → 等健康 → 远程执行 reseed（把 `doc/verify/seed/` 与 `reseed.sh` 一并同步过去，库名 `lqg_test` 满足护栏）。
- `doc/verify/verify.test.env.example`：指向测试环境的 API 地址（DB 不对外，DB 类断言只在本地跑）。
- 小程序：`pnpm upload:mp --mode=test`（本地构建 + miniprogram-ci），设为体验版，把甲方对接人加为体验成员；生成一页「试用说明」（怎么进、测试账号是谁、哪些是假数据）。

## 3 边界（明确不做）

- 不上生产、不买服务器域名（SYS-PROD-001）
- 不配 Gotenberg 与文档渲染（D6 之后再把它加进 test compose）
- 不做 CI / CD 流水线
- 不在测试机上放任何真实供体数据

## 4 完工报告要求

1. 测试环境的工作台地址、体验版二维码（发 Kevin，不进仓库）
2. `/lqg/sys/ping` 在测试环境的完整输出
3. 给甲方的试用说明一页
4. **改了哪些文件**（含 Flyway 文件名与取号依据、新增的类 / 页面 / 接口清单）
5. **accept 逐条 ✅ / ❌ + 关键输出**（贴命令输出，不贴「已通过」三个字）
6. **遗留与 raise**：越出 `touches` 的改动、与 `doc/api-contract.md` 不一致的地方、没把握的口径
7. 验证用的后端 / 前端长进程已关，或明示留给谁

## 5 票面更新

- 2026-09-23 按 CR-20260923-09 更新：§2 补上传上限统一 60m、nginx 挡掉 actuator；说明 accept 1 里 `mine=true` 那段原文断言已成立、重新部署后要补的两段 actuator 断言（见下）。两条 accept 的 run 本轮不改：都要连公网测试机，而 CR 定了测试机现阶段不动，本轮不跑。
  - accept 1 里 `mine=true` 那一段（`/mp/int/sample/list?pageSize=100&mine=true`，按顶层 `&&` 切是第 6 段）：#191 修掉之后 `mine` 与 `sort` 无关，原文不补 `&sort=recent` 也返回 `["9000001008","9000001009"]`（本机活体已绿）；测试机重新部署到当前代码后这一段即绿。
  - 测试机重新部署后，在 accept 1 末尾补两段（写法供参考，现在不进 run）：
    `B="$(sed -n 's/^LQG_API_BASE=//p' doc/verify/verify.test.env)" && test "$(curl -s -o /dev/null -w '%{http_code}' "${B}/actuator/health")" = 404 && test "$(curl -s -o /dev/null -w '%{http_code}' "${B}/prod-api/actuator/env")" = 404`
- 2026-09-24 按 CR-20260924-11 更新：核对两条 accept 没有写死 Kevin 本机的 dev 容器、3010 或 `/tmp/lqg-*`——accept 1 经 `LQG_VERIFY_ENV_FILE=doc/verify/verify.test.env` 连公网测试机，accept 2 只 `nc` 测试机端口并读仓库里的 compose，本机不起停任何容器；远程段语义不变，run 不动。在隔离环境重放时照样只连测试机，本机要设的变量见 doc/verify/README.md「隔离环境重放」。
