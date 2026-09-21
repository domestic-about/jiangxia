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
      bash doc/verify/api.sh --as admin GET /lqg/sys/ping | jq -e --arg c "$(git -C code rev-parse --short=7 HEAD 2>/dev/null || git rev-parse --short=7 HEAD)" '.code==200 and .data.profile=="prod" and .data.db=="PostgreSQL" and .data.encryptEnabled==true and .data.mockLogin==false and .data.tenantEnabled==false and (.data.buildCommit|startswith($c))' &&
      test "$(curl -s -X POST "${BASE}/auth/login" -H 'Content-Type: application/json' -H "clientid: $(sed -n 's/^LQG_CLIENT_MP=//p' doc/verify/verify.prod.env)" -d "$(jq -nc --arg c "$(sed -n 's/^LQG_CLIENT_MP=//p' doc/verify/verify.prod.env)" '{clientId:$c,grantType:"xcx",tenantId:"000000",xcxCode:"mock:extA",phoneCode:"mock:13800000011"}')" | jq -r '.data.access_token // "rejected"')" = "rejected" &&
      HOST="$(printf '%s' "${BASE}" | sed -E 's#https://([^/:]+).*#\1#')" &&
      END="$(echo | openssl s_client -servername "${HOST}" -connect "${HOST}:443" 2>/dev/null | openssl x509 -noout -enddate | cut -d= -f2)" &&
      python3 -c "import sys,email.utils,time; d=(email.utils.mktime_tz(email.utils.parsedate_tz(sys.argv[1]))-time.time())/86400; sys.exit(0 if d>20 else 1)" "${END}" &&
      curl -sI "http://${HOST}/" | grep -qiE '^location: https://'
    counterfeit: |-
      生产误用了 test 配置（mock 登录是开的）→ ping 的 profile / mockLogin 红；第 3 段 mock 登录真的换到了 token 红——那等于任何人知道一个手机号就能登录。
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
      ! git ls-files | grep -E '(^|/)\.env$|verify\.prod\.env$' | grep -q . &&
      ! git grep -nE 'LTAI[0-9A-Za-z]{12,}|AKID[0-9A-Za-z]{12,}' -- . ':!doc/tickets' | grep -q . &&
      ! grep -nE 'mock-login|paid-enabled|provider:[[:space:]]*stub' code/RuoYi-Vue-Plus/ruoyi-admin/src/main/resources/application-prod.yml
    counterfeit: |-
      为了方便远程连库，compose 里写了 ports: "5432:5432" → 公网可达，nc 连得上红。不允许的连接被拒才算对。
      OSS bucket 建成了公共读 → 拿一个对象的裸地址（不带签名，填在 verify.prod.env 的 LQG_OSS_PROBE_URL）匿名 GET 得到 200 红：任何拿到链接的人都能看到供体的质控文档。
      把 .env 或带 AK 的配置提交进了 git → 红。
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
