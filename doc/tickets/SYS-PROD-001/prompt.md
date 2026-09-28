---
ticket: SYS-PROD-001
track: SYS
phase: D8
size: L
req_refs:
  - REQ-SYS-004
  - REQ-SYS-005
  - REQ-SYS-009
depends_on:
  - SYS-STAGING-001
  - DOC-PUBLISH-001
  - SYS-HOME-001
touches:
  - code/deploy/prod/**
  - code/deploy/common/**
  - code/RuoYi-Vue-Plus/ruoyi-admin/src/main/resources/application-prod.yml
  - code/plus-ui/.env.production
  - code/plus-ui/vite.config.ts
  - doc/verify/verify.prod.env.example
  - doc/ops/**
adr_refs:
  - ADR-0002
  - ADR-0008
  - ADR-0006
blueprint_refs:
  - FLOW:F-OPS-01.step1
  - FLOW:F-OPS-01.step5
  - FLOW:F-OPS-02.step4
accept:
  - name: "生产跑的是当前代码、prod 配置、PostgreSQL、字段加密开着、mock 登录关着；mock 登录请求在生产被拒；全站 HTTPS 且证书有效期充足"
    form: API
    run: |-
      export LQG_VERIFY_ENV_FILE=doc/verify/verify.prod.env && BASE="$(sed -n 's/^LQG_API_BASE=//p' doc/verify/verify.prod.env)" && case "${BASE}" in https://*) true ;; *) false ;; esac &&
      TOK="$(sed -n 's/^LQG_ADMIN_TOKEN=//p' doc/verify/verify.prod.env)" && test -n "${TOK}" &&
      curl -s "${BASE}/lqg/sys/ping" -H "Authorization: Bearer ${TOK}" -H "clientid: $(sed -n 's/^LQG_CLIENT_PC=//p' doc/verify/verify.prod.env)" | jq -e --arg c "$(git -C code rev-parse --short=7 HEAD 2>/dev/null || git rev-parse --short=7 HEAD)" '.code==200 and .data.profile=="prod" and .data.db=="PostgreSQL" and .data.encryptEnabled==true and .data.mockLogin==false and .data.tenantEnabled==false and (.data.buildCommit|startswith($c))' &&
      test "$(curl -s -X POST "${BASE}/auth/login" -H 'Content-Type: application/json' -H "clientid: $(sed -n 's/^LQG_CLIENT_MP=//p' doc/verify/verify.prod.env)" -d "$(jq -nc --arg c "$(sed -n 's/^LQG_CLIENT_MP=//p' doc/verify/verify.prod.env)" '{clientId:$c,grantType:"xcx",tenantId:"000000",xcxCode:"mock:extA",phoneCode:"mock:13800000011"}')" | jq -r '.data.access_token // "rejected"')" = "rejected" &&
      HOST="$(printf '%s' "${BASE}" | sed -E 's#https://([^/:]+).*#\1#')" &&
      END="$(echo | openssl s_client -servername "${HOST}" -connect "${HOST}:443" 2>/dev/null | openssl x509 -noout -enddate | cut -d= -f2)" &&
      python3 -c "import sys,email.utils,time; d=(email.utils.mktime_tz(email.utils.parsedate_tz(sys.argv[1]))-time.time())/86400; sys.exit(0 if d>20 else 1)" "${END}" &&
      curl -sI "http://${HOST}/" | grep -qiE '^location: https://'
    counterfeit: |-
      生产误用了 test 配置（mock 登录是开的）→ ping 的 profile / mockLogin 红；第 3 段 mock 登录真的换到了 token 红——那等于任何人知道一个手机号就能登录。
      ★ 2026-09-27 修 #311（我的独立验收发现）：原来这一段用 `doc/verify/api.sh --as admin` 取 ping —— 而
      api.sh 是**裸 curl**（不带 `encrypt-key` 头、不做 RSA/AES、不带验证码 code/uuid），偏偏生产
      `api-decrypt.enabled` 与 `captcha.enable` 都吃缺省 `true` → **生产登录必然失败**，等于这段断言
      即便资源到位也跑不通。现在改成用**生产侧签发的管理员 token**（`verify.prod.env` 的 `LQG_ADMIN_TOKEN`，
      在服务器上用超管登录工作台后从 localStorage 的 `Admin-Token` 取一次）直接打 ping。取 token 这一步
      写进部署手册 §11；token 过期就重取。
      部署的是旧镜像 → buildCommit 对不上红。
      证书是手工装的、快过期了 → 剩余天数 ≤ 20 红。http 没跳 https → 最后一段红（小程序要求 https，工作台也不该明文传口令）。
      生产上没有 seed 账号：这里的 admin 是 verify.prod.env 里配的真实管理员，口令不进仓库。
  - name: "数据库、缓存、文档转换的端口在公网上连不通；OSS 对象匿名读被拒；仓库里没有任何生产密钥；生产配置文件里没有三个危险开关"
    form: STATE
    run: |-
      HOST="$(sed -n 's#^LQG_API_BASE=https://\([^/:]*\).*#\1#p' doc/verify/verify.prod.env)" && test -n "${HOST}" &&
      ! nc -z -w 3 "${HOST}" 5432 && ! nc -z -w 3 "${HOST}" 6379 && ! nc -z -w 3 "${HOST}" 3000 && ! nc -z -w 3 "${HOST}" 8080 &&
      ! grep -nE '^[[:space:]]*-[[:space:]]*"?(0\.0\.0\.0:)?(5432|6379|3000|8080):' code/deploy/prod/docker-compose.yml &&
      URL="$(bash -c 'LQG_VERIFY_ENV_FILE=doc/verify/verify.prod.env bash doc/verify/api.sh --as admin GET /lqg/home/recent' >/dev/null; sed -n 's/^LQG_OSS_PROBE_URL=//p' doc/verify/verify.prod.env)" && test -n "${URL}" &&
      test "$(curl -s -o /dev/null -w '%{http_code}' "${URL}")" = 403 &&
      ! git ls-files | grep -E '(^|/)\.env$|verify\.prod\.env$' | grep -vx 'code/miniapp/env/\.env' | grep -q . &&
      ! git grep -nE 'LTAI[0-9A-Za-z]{12,}|AKID[0-9A-Za-z]{12,}' -- . ':!doc/tickets' | grep -q . &&
      ! grep -nE 'mock-login|paid-enabled|provider:[[:space:]]*stub' code/RuoYi-Vue-Plus/ruoyi-admin/src/main/resources/application-prod.yml
    counterfeit: |-
      为了方便远程连库，compose 里写了 ports: "5432:5432" → 公网可达，nc 连得上红。不允许的连接被拒才算对。
      OSS bucket 建成了公共读 → 拿一个对象的裸地址（不带签名，填在 verify.prod.env 的 LQG_OSS_PROBE_URL）匿名 GET 得到 200 红：任何拿到链接的人都能看到供体的质控文档。
      把 .env 或带 AK 的配置提交进了 git → 红。唯一放过的是 `code/miniapp/env/.env`：它是小程序的 vite 模板（全是 `VITE_*`、没有密钥），改名会破坏构建（#313 的收窄，只排除这一个精确路径）。
      在 prod 配置里显式写了 mock-login: false → 红：这个键出现在 prod 文件里，就离被改成 true 只差一次手滑。
---

# SYS-PROD-001 · 生产部署：代购的云服务器上 compose 起全套、HTTPS、OSS 私有桶、健康检查与告警、生产护栏

## §0 状态自检（实施前必过，不过就 STOP 报 Kevin）

- [ ] 当前分支符合调度器注入的期望（`track/SYS` worktree）——不写死 `feature/dayN`
- [ ] `depends_on` 全部 done：**SYS-STAGING-001**、**DOC-PUBLISH-001**、**SYS-HOME-001**
- [ ] 扫 `doc/change-log.md`：涉及本 ticket 的 CR **以 CR 为准**（CR 覆盖本文）
- [ ] 逐条取权威（别全文读蓝图）：`python3 ~/claude-config/skills/xuqiu/scripts/authority_lint.py show <锚>`，锚见上方 `blueprint_refs`；接口形状见 `doc/api-contract.md`
- [ ] 必读：
  - 合同第二条第 3 款：云资源以**乙方名义**购买和备案；2000 元/年含服务器、OSS、域名、SSL、ICP 备案、日常运维——**不含 RDS**
  - **非开发前置（Kevin 办）**：买服务器与 OSS、注册域名、域名 ICP 备案（按周计，第一周就要启动）。这几样没到位，本张 blocked
  - `~/claude-config/skills/aliyun-deploy` 与 dongjiaoshan 的部署清单是按 MySQL / RDS 写的，**不能照抄**：这里数据库是同机 Docker 里的 PostgreSQL
  - **ADR-0002**（全文在 `doc/_adr/`，`authority_lint.py show ADR-0002` 取结构化口径）
  - **ADR-0008**（全文在 `doc/_adr/`，`authority_lint.py show ADR-0008` 取结构化口径）
  - **ADR-0006**（全文在 `doc/_adr/`，`authority_lint.py show ADR-0006` 取结构化口径）
- [ ] 口径复述（本张最容易做反的）：
  1. **生产上 mock 登录必须是关的、识别 provider 不能是 stub**——两个护栏在启动时就拒绝（AUTH-LOGIN-001 / OCR-IMPL-001 已做），本张负责验证它们在真环境里生效。
  2. **数据库、Redis、Gotenberg、MinIO（生产不用）端口一律不映射到公网**。要连库走 SSH 隧道。
  3. **密钥不进仓库**：数据库口令、字段加密口令、OSS AK、微信 secret 全在服务器上的 `.env`（600 权限）。字段加密口令另存一份给 Kevin——丢了数据就读不出来。
  4. 生产的字段加密口令**不是**测试那个 `LqgTestAesKey#01`。

## 1 背景与口径

合同附件第 14 行：在乙方代为购买的云服务器上以 Docker 部署后端、PostgreSQL、网页工作台。第二条第 3 款：域名、SSL、OSS、运行监控、安全更新。

## 2 实现要点

- `code/deploy/prod/docker-compose.yml`：`postgres:16-alpine`（数据卷落数据盘；`shm_size`、`max_connections` 按 2 核 4G 调）、`redis`、`gotenberg`（自建镜像，内存上限 1G）、`backend`（`prod` 配置，JVM `-Xmx1g`）、`nginx`（HTTPS、工作台 dist、`/prod-api` 反代、上传体积上限 60MB、安全响应头）。
  全部服务 `restart: unless-stopped` + `healthcheck`；只有 nginx 暴露 80 / 443。
- 证书：acme.sh / certbot 自动续期（续期后 reload nginx），写进 crontab。
- OSS：一个**私有读写**的 bucket，前缀 `lqg/`；后端用 RAM 子账号的 AK（只授这个 bucket）。上游 OSS 配置存在 `sys_oss_config` 表里——用一支 prod 专用的初始化 SQL（不进 Flyway，手工执行一次）写入，AK 不进仓库。
- `application-prod.yml`：数据库 / Redis 走 compose 内网主机名；字段加密口令、微信 appid / secret 取环境变量；不出现 `mock-login`、`paid-enabled`、`provider: stub`。
- 生产密钥改注入（CR-20260923-09）：JWT 签名密钥、接口加解密的两对 RSA、字段加密口令都由环境变量注入且**没有缺省值**，缺失、等于若依默认值或长度不合规（字段加密口令须 16 / 24 / 32 位）就拒绝启动；`code/deploy/prod/gen-secrets.sh` 一次生成写进服务器上的 `.env`（幂等，不覆盖已有真值）。
  工作台 `pnpm build:prod` 从 `.env` 读 `VITE_APP_RSA_*`，检测到默认 RSA 直接构建失败（`code/plus-ui/vite.config.ts`、`code/plus-ui/.env.production`）。
- actuator（CR-20260923-09）：生产只开 health 与 info，未配口令时随机生成；nginx 对 `/actuator`、`/prod-api/actuator` 一律回 404。上传超 60MB 由 nginx 回 413 与中文 JSON（与后端单文件 50MB、一次请求 60MB 同口径）。
- 告警 `code/deploy/prod/healthcheck.sh`（cron 每 5 分钟）：容器不健康 / 磁盘 > 80% / 证书剩余 < 15 天 → 往 `LQG_ALERT_WEBHOOK`（飞书机器人）发一条。备份失败的告警在 SYS-BACKUP-001。
- `doc/ops/部署手册.md`：从一台空服务器到上线的每一步、`.env` 每个键的含义、怎么回滚到上一个镜像、怎么走 SSH 隧道连库。
- 首个管理员：上线后用超管登录 → 在「内部人员授权」里按甲方管理员的手机号授权 → 停用上游默认的 admin 账号口令（改成随机强口令并记入交接单）。

## 3 边界（明确不做）

- 不上 RDS、不做主从、不做多机
- 不做蓝绿 / 灰度发布
- 不接第三方 APM
- 备份与导出在 SYS-BACKUP-001；小程序发布在 SYS-RELEASE-001

## 4 完工报告要求

1. `/lqg/sys/ping` 在生产的输出
2. `docker compose ps` 的输出（全部 healthy）
3. 字段加密口令已交 Kevin 保管的确认
4. **改了哪些文件**（含 Flyway 文件名与取号依据、新增的类 / 页面 / 接口清单）
5. **accept 逐条 ✅ / ❌ + 关键输出**（贴命令输出，不贴「已通过」三个字）
6. **遗留与 raise**：越出 `touches` 的改动、与 `doc/api-contract.md` 不一致的地方、没把握的口径
7. 验证用的后端 / 前端长进程已关，或明示留给谁

## 5 票面更新

- 2026-09-23 按 CR-20260923-09 更新：touches 补 `code/plus-ui/.env.production`、`code/plus-ui/vite.config.ts`（`gen-secrets.sh` 已在 `code/deploy/prod/**` 里）；§2 补密钥注入、actuator 与上传上限；accept 2 的 `.env` 检查按 #313 收窄，只放过 `code/miniapp/env/.env` 这个 vite 模板；#311 与三段待补断言记在下面，生产未到位，run 先不动。
  - #311：生产开着接口加解密与验证码，`doc/verify/api.sh` 是裸 curl，口令登录必然失败。accept 1 改用在服务器上签发的一次性 token，不走口令登录；run 等生产到位再改。
  - 生产到位后补进 run 的三段（写法供参考）：
    ① actuator 不对外：`test "$(curl -s -o /dev/null -w '%{http_code}' "${BASE}/actuator/health")" = 404 && test "$(curl -s -o /dev/null -w '%{http_code}' "${BASE}/prod-api/actuator/env")" = 404`
    ② 上传超 60MB 回 413：`head -c 62914561 /dev/zero > "${TMPDIR:-/tmp}"/lqg-61m.bin && test "$(curl -s -o "${TMPDIR:-/tmp}"/lqg-413.json -w '%{http_code}' -F "file=@${TMPDIR:-/tmp}/lqg-61m.bin" "${BASE}/prod-api/resource/oss/upload")" = 413 && jq -e '.code==413' "${TMPDIR:-/tmp}"/lqg-413.json`
    ③ 缺 `LQG_JWT_SECRET` 时 compose 起不来：在生产机的部署目录里，完整 `.env` 下 `docker compose config -q` 成功，去掉 `LQG_JWT_SECRET` 那一行（且 shell 里也没有这个变量）后 `docker compose --env-file <去掉那一行的副本> config -q` 必须失败。
- 2026-09-24 按 CR-20260924-11 更新：核对两条 accept 没有写死 Kevin 本机的 dev 容器、3010 或 `/tmp/lqg-*`（都经 `verify.prod.env` 连生产机，远程段语义不变，run 不动）；上面待补断言 ② 的两个临时文件改放 `${TMPDIR:-/tmp}`（`-F` 改用双引号才会展开），并入 run 时照此写。
