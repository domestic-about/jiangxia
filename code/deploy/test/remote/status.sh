#!/usr/bin/env bash
# SYS-STAGING-001 · 测试机现状一屏（只读，幂等，随时可跑）
# 由 deploy.sh status 上传并执行；也用于完工报告 §7 的「留下的容器/端口清单」。
set -uo pipefail
cd /opt/lqg-test 2>/dev/null || true
set -a; [ -f /opt/lqg-test/.env ] && . /opt/lqg-test/.env; set +a

echo "=== 磁盘 / 内存 ==="
df -h / | tail -1
free -m | awk 'NR==1||NR==2'

echo
echo "=== 本项目的容器（lqg-test-*）==="
docker ps --filter 'name=lqg-test-' --format '  {{.Names}}  {{.Status}}  {{.Ports}}'
echo "=== 别人的容器（只读列出，未碰）==="
docker ps --format '  {{.Names}}  {{.Status}}' | grep -v 'lqg-test-' || true

echo
echo "=== 宿主监听端口（全量；本项目的应全是 127.0.0.1）==="
ss -lntp | awk 'NR==1 || /LISTEN/'

echo
echo "=== 本项目的 compose 状态 ==="
docker compose -f /opt/lqg-test/docker-compose.yml --env-file /opt/lqg-test/.env ps 2>/dev/null || echo "  (compose 文件不在或未起)"

echo
echo "=== 后端最近日志（20 行）==="
docker logs --tail 20 lqg-test-backend 2>&1 | sed 's/^/  /' || echo "  (没有 lqg-test-backend)"

echo
echo "=== 宿主 nginx 站点文件（本项目的）==="
ls -l "/www/server/panel/vhost/nginx/${LQG_TEST_DOMAIN:-songjian.tianda.studio}.conf" 2>/dev/null || echo "  (还没落 vhost)"
ls -l "/www/server/panel/vhost/cert/${LQG_TEST_DOMAIN:-songjian.tianda.studio}/" 2>/dev/null || echo "  (还没证书)"
