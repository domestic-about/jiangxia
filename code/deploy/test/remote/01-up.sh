#!/usr/bin/env bash
# SYS-STAGING-001 · 测试机上起全套（数据面 → 后端镜像原生构建 → 起容器 → 等健康）
#
# 由 code/deploy/test/deploy.sh 上传到 /opt/lqg-test/remote/ 后 `nohup` **分离执行**
# （docker build / 首次 Flyway 迁移都是长操作，本机 SSH 长连接会被代理掐断 —— ops §6.4）。
# 日志：/opt/lqg-test/logs/01-up.log   完成标记：/opt/lqg-test/logs/01-up.done（内容是退出码）
set -euo pipefail
cd /opt/lqg-test

set -a; . /opt/lqg-test/.env; set +a
COMPOSE=(docker compose -f /opt/lqg-test/docker-compose.yml --env-file /opt/lqg-test/.env)
export PATH="/opt/lqg-test/bin:${PATH}"

log() { echo "[$(date '+%F %T')] $*"; }

log "===== 0) 前提检查 ====="
docker version --format '{{.Server.Version}}' | sed 's/^/  docker server: /'
log "  磁盘：$(df -h / | tail -1)"
log "  内存：$(free -m | awk 'NR==2{print $2"MB total, "$7"MB available"}')"
for img in postgres:16-alpine redis:7-alpine minio/minio:latest eclipse-temurin:21-jre-jammy; do
  docker image inspect "$img" >/dev/null 2>&1 && log "  镜像就位：$img" || log "  ⚠ 镜像缺失：$img"
done
if ! docker image inspect nginx:1.27-alpine >/dev/null 2>&1; then
  log "  拉 nginx:1.27-alpine（首次；走 daemon.json 里的镜像加速）"
  docker pull nginx:1.27-alpine
fi

log "===== 1) 数据面：postgres / redis / minio ====="
"${COMPOSE[@]}" up -d postgres redis minio
# --wait 会等 healthcheck 变 healthy（compose v2.27 支持）
"${COMPOSE[@]}" up -d --wait postgres redis minio

log "===== 2) 后端镜像：**在本机原生构建**（amd64，只 COPY jar，秒级）====="
# 本机是 x86_64，本地开发机是 arm64 —— 镜像必须在服务器上建（ops §4.5）
log "  构建参数 BUILD_COMMIT=${LQG_BUILD_COMMIT}（= 本地 git rev-parse --short=12 HEAD）"
"${COMPOSE[@]}" build --pull=false backend

log "===== 3) 起后端（Flyway 首次迁移在这里跑）====="
"${COMPOSE[@]}" up -d backend

log "===== 4) 等后端在宿主环回端口上应答 /lqg/sys/ping ====="
wait-for -t 420 -i 5 "http://127.0.0.1:${LQG_API_PORT}/lqg/sys/ping"
CODE="$(curl -sS -o /dev/null -w '%{http_code}' --max-time 10 "http://127.0.0.1:${LQG_API_PORT}/lqg/sys/ping")"
log "  /lqg/sys/ping -> HTTP ${CODE}；响应体前 200 字节：$(curl -sS --max-time 10 "http://127.0.0.1:${LQG_API_PORT}/lqg/sys/ping" | head -c 200)"
# 注意：若依把 401 / 404 / 500 全都包进响应体，**HTTP 状态码恒为 200**（doc/verify/README.md 坑 #1）。
# 所以这里只证明「应用在监听并且能应答」；鉴权与业务判据由 deploy.sh verify 与票面 accept 用 api.sh + jq 断。

log "===== 5) 起 compose 的 nginx（静态 + 反代）====="
"${COMPOSE[@]}" up -d nginx
wait-for -t 60 "http://127.0.0.1:${LQG_WEB_PORT}/" || log "  ⚠ compose nginx 的自检没通（工作台首页可能还没 rsync 上去）"

log "===== 6) 现状 ====="
"${COMPOSE[@]}" ps
log "  宿主环回端口占用（本项目应只有 127.0.0.1 的 15432/16379/19000/19001/${LQG_API_PORT}/${LQG_WEB_PORT}）"
ss -lntp | grep -E ":(15432|16379|19000|19001|${LQG_API_PORT}|${LQG_WEB_PORT})\b" || true

log "===== 后端启动日志尾部（Flyway 迁移结果）====="
docker logs --tail 40 lqg-test-backend 2>&1 | sed 's/^/  /' || true

log "===== 5) OSS 配置对齐 compose 里的 MinIO（上传转圈 / 图片打不开的根因）====="
# 2026-09-28：测试环境「工作台上传图片一直转圈」的根因有两个，都在这里一次性对齐：
#   ① `sys_oss_config` 里启用的那条（config_key=minio）endpoint 是 RuoYi 的默认值 `127.0.0.1:9000`
#      —— 那在**后端容器里**是容器自己，请求永远等不到响应（转圈到超时）。
#   ② MinIO 里**根本没有这个桶**：RuoYi 不会替你建桶（实测 mc ls 为空）。
# 另外两个必须守住的点（都是既有验收断言的性质）：
#   · endpoint **不带 scheme**：RuoYi 的 OssClient#getEndpoint() 会自己补 `http://`，
#     写 `http://minio:9000` 会变成 `http://http://minio:9000` → 建连卡死。
#   · access_policy 必须是 **0（私有）**：验收里断言「OSS 裸地址匿名访问 403」，
#     设成公开会破坏这条已验证的安全性质。
OSSW="minio:9000|${LQG_MINIO_BUCKET}|0|N"
CUR="$(docker exec lqg-test-postgres psql -U lqg -d lqg_test -At -c \
  "SELECT coalesce(endpoint,'')||'|'||coalesce(bucket_name,'')||'|'||coalesce(access_policy,'')||'|'||coalesce(is_https,'') FROM sys_oss_config WHERE config_key='minio'" 2>/dev/null || true)"
# 桶先建（私有；已存在则跳过）。mc 在 minio 服务端镜像里自带。
if docker exec lqg-test-minio sh -c "mc alias set l http://127.0.0.1:9000 '${LQG_MINIO_USER}' '${LQG_MINIO_PASSWORD}' >/dev/null 2>&1 && mc mb --ignore-existing l/${LQG_MINIO_BUCKET}" >/dev/null 2>&1; then
  log "  ✓ 桶 ${LQG_MINIO_BUCKET} 就位（私有）"
else
  log "  ⚠ 建桶失败（MinIO 没起来？）——上传会失败，继续按现状跑"
fi
if [ "${CUR}" = "${OSSW}" ]; then
  log "  OSS 配置已是目标值（${OSSW}）→ 不动"
else
  log "  当前 ${CUR:-（读不到）} ≠ 目标 ${OSSW} → 改写并重启 backend（init() 才会重写 Redis 缓存）"
  docker exec -i lqg-test-postgres psql -U lqg -d lqg_test -v ON_ERROR_STOP=1 <<SQL
UPDATE sys_oss_config SET endpoint='minio:9000', bucket_name='${LQG_MINIO_BUCKET}',
       access_key='${LQG_MINIO_USER}', secret_key='${LQG_MINIO_PASSWORD}',
       access_policy='0', is_https='N', status='0' WHERE config_key='minio';
UPDATE sys_oss_config SET status='1' WHERE config_key<>'minio';
SQL
  "${COMPOSE[@]}" restart backend
  wait-for -t 420 -i 5 "http://127.0.0.1:${LQG_API_PORT}/lqg/sys/ping" && log "  ✓ backend 已重启并就绪"
fi

log "1) up 完成"
