# code/deploy/prod —— SYS-PROD-001 · 生产部署资产

> **状态：资产已写完，但从未在真生产上跑过。** 生产服务器 / 域名 / ICP 备案 / OSS 私有桶与 RAM AK
> 都没到位（owner 未提供，见 `doc/handover/d8-blockers.json` 的 B1 / B2）。
> 本目录里的一切都是**代码与文档**，没有任何一条生产证据 —— 别把它当「已上线」。

## 文件清单

| 文件 | 用途 |
|---|---|
| `docker-compose.yml` | 全套服务：postgres 16 / redis 7 / gotenberg / backend / nginx。**只有 nginx 映射宿主 80/443**，其余一律不映射（要连库走 SSH 隧道，见 `doc/ops/部署手册.md` §7） |
| `.env.example` | 生产环境变量模板（域名 / 主机 / 口令 / OSS AK / webhook）。**真文件 `.env` 不进仓库**（600，服务器上也留一份） |
| `deploy.sh` | 一键部署 + `preflight` / `artifacts` / `upload` / `up` / `cert` / `oss-init` / `cron` / `verify` / `rollback` / `status` / `down` |
| `remote/00-host.sh` | 买完机器第一次跑：时区 / swap / 数据盘核对 / docker 日志轮转 / 防火墙 |
| `remote/01-up.sh` | 远端阶段：目录权限 → nginx 域名渲染 → 建镜像 → 起容器 → 等健康 → 端口纪律复核 |
| `remote/03-cert.sh` | acme.sh（webroot）+ **续期后 reload nginx** 的 `--reloadcmd` |
| `remote/04-oss-init.sh` | 在 postgres 容器里执行 `oss-init.sql`（AK 从服务器 `.env` 取） |
| `remote/05-cron.sh` | 生成 `healthcheck.env` + 装 healthcheck 的每 5 分钟 crontab（幂等） |
| `remote/06-cert-install.sh` | 由 acme.sh 的 `--reloadcmd` 调用：把证书从 `cert-origin/` 搬到 `certs/` 并 `nginx -s reload` |
| `healthcheck.sh` | 容器不健康 / 磁盘 > 80% / 证书剩余 < 15 天 / 线上证书与磁盘不一致 → 飞书告警 |
| `oss-init.sql` | **手工执行一次**的生产 OSS 配置（`sys_oss_config` 一行，私有桶 + 前缀 `lqg/`，**不进 Flyway**） |
| `nginx/workspace.conf` | 生产站点：80 跳转 + ACME webroot + 443 TLS + 工作台 dist + `/prod-api` 反代 + 60MB 上传上限 |
| `nginx/proxy-headers.inc` | 反代共用头（SSE 长连接、`X-Forwarded-*`） |
| `nginx/security-headers.inc` | 安全响应头（HSTS / nosniff / X-Frame-Options / Referrer-Policy / Permissions-Policy） |

## 与 `code/deploy/test/` 的四处关键差异（别互相抄）

| | test（`songjian.tianda.studio`） | prod（本目录） |
|---|---|---|
| TLS 在哪终止 | **宿主宝塔 nginx**（那台机器上还有别的项目） | **nginx 容器**（生产机器上只有我们） |
| 宿主端口 | 全部只绑 `127.0.0.1`，宝塔反代进来 | 只有 nginx 用 80/443；其余**一个都不映射** |
| 应用 profile | `test`（mock 登录**开着**，ADR-0008 允许） | `prod`（`application-prod.yml` 里连那个键都不出现；被打开则拒绝启动） |
| 数据落哪 | docker named volume | postgres 的 `pgdata` **bind mount 到数据盘**（`LQG_DATA_DIR`） |

## 顺序（首次上线）

```bash
cd code/deploy/prod
cp .env.example .env && chmod 600 .env      # 填域名 / 主机 / AK；口令可让 deploy.sh 生成
bash deploy.sh preflight                    # 不联网也能跑的六道自检
ssh <生产机> 'cd /opt/lqg && bash remote/00-host.sh'   # 系统层准备（要 sudo）
bash deploy.sh all                          # 产物 → 上传 → 起容器 → 证书 → OSS → cron → 自检
```

逐段的解释、以及**每一步失败怎么排查**，见 `doc/ops/部署手册.md`；
日常巡检 / 告警处置 / 备份与导出的指针，见 `doc/ops/运维手册.md`。
