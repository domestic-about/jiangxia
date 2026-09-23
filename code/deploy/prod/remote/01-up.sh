#!/usr/bin/env bash
# SYS-PROD-001 · 远端：建镜像 + 起容器 + 等健康 + 端口纪律复核（在**生产服务器**上跑）
#
# 由 deploy.sh 用 `nohup bash -c '... ; echo $? > logs/up.done'` 分离执行，输出落 logs/up.log。
# cwd = ${LQG_DATA_DIR}（deploy.sh 会 cd 进去）。
set -euo pipefail

cd "$(dirname "$0")/.."
DATA_DIR="$(pwd)"
ENV_FILE="${DATA_DIR}/.env"
[ -f "${ENV_FILE}" ] || { echo "[error] 没有 ${ENV_FILE}"; exit 1; }
set -a; # shellcheck disable=SC1090
. "${ENV_FILE}"; set +a
: "${LQG_BUILD_COMMIT:?}" "${LQG_PROD_DOMAIN:?}" "${LQG_DATA_DIR:=${DATA_DIR}}"

echo "=== [up] 提交号 ${LQG_BUILD_COMMIT} / 域名 ${LQG_PROD_DOMAIN} / 数据盘 ${LQG_DATA_DIR} ==="

# ── 0. 依赖自检（生产机上没装的话，这里给一句人话而不是 compose 的报错）────────────
for bin in docker openssl curl; do
  command -v "${bin}" >/dev/null 2>&1 || { echo "[error] 生产机缺 ${bin}"; exit 1; }
done
docker compose version >/dev/null 2>&1 || { echo "[error] 缺 docker compose 插件（v2）"; exit 1; }

# ★ 禁飞区复核：库名不含 test、域名不是测试域名、主机不是测试机
case "${LQG_DB_NAME:-lqg}" in *test*) echo "[error] LQG_DB_NAME 含 test"; exit 1;; esac
case "${LQG_PROD_DOMAIN}" in *tianda.studio*) echo "[error] 生产域名是测试域名"; exit 1;; esac

echo "=== [up] 1/6 目录与权限 ==="
install -d -m 755 "${LQG_DATA_DIR}/logs/backend" "${LQG_DATA_DIR}/logs/nginx" \
                   "${LQG_DATA_DIR}/pgdata" "${LQG_DATA_DIR}/tmp" \
                   "${LQG_DATA_DIR}/certs" "${LQG_DATA_DIR}/acme-webroot"
chmod 1777 "${LQG_DATA_DIR}/tmp"
# ★ postgres 官方镜像以 uid 70 跑；bind mount 的目录 root 独占会让 initdb 直接失败
chown -R 70:70 "${LQG_DATA_DIR}/pgdata"
chmod 700 "${LQG_DATA_DIR}/pgdata"

echo "=== [up] 2/6 nginx 配置渲染（__LQG_DOMAIN__ → 真实域名）==="
sed -i.bak "s/__LQG_DOMAIN__/${LQG_PROD_DOMAIN}/g" "${LQG_DATA_DIR}/nginx/workspace.conf"
rm -f "${LQG_DATA_DIR}/nginx/workspace.conf.bak"
grep -q '__LQG_DOMAIN__' "${LQG_DATA_DIR}/nginx/workspace.conf" \
  && { echo "[error] nginx/workspace.conf 里仍有未替换的 __LQG_DOMAIN__"; exit 1; } || true

# ★ 首次部署时证书还不存在；nginx 配置引用了证书文件，`nginx -t` 会失败。
#   所以：证书缺失 → 先生成一个**自签占位**放到位，让 nginx 能起来提供 80（acme 的 webroot 挑战），
#   紧接着 03-cert.sh 用真证书覆盖它并 reload。占位证书只用于「让 nginx 起得来」。
if [ ! -s "${LQG_DATA_DIR}/certs/${LQG_PROD_DOMAIN}/fullchain.pem" ]; then
  echo "  … 证书不存在，签发自签占位（仅用于让 nginx 起来提供 80 端口的 acme 挑战）"
  install -d -m 755 "${LQG_DATA_DIR}/certs/${LQG_PROD_DOMAIN}"
  openssl req -x509 -newkey rsa:2048 -nodes -days 3 \
    -keyout "${LQG_DATA_DIR}/certs/${LQG_PROD_DOMAIN}/privkey.pem" \
    -out    "${LQG_DATA_DIR}/certs/${LQG_PROD_DOMAIN}/fullchain.pem" \
    -subj "/CN=${LQG_PROD_DOMAIN}" >/dev/null 2>&1
  chmod 600 "${LQG_DATA_DIR}/certs/${LQG_PROD_DOMAIN}/privkey.pem"
fi

echo "=== [up] 3/6 建镜像（生产机原生 amd64）==="
docker compose build backend gotenberg

echo "=== [up] 4/6 拉镜像 + 起容器 ==="
docker compose up -d

echo "=== [up] 5/6 等健康（最多 8 分钟）==="
deadline=$(( $(date +%s) + 480 ))
while :; do
  ps="$(docker compose ps --format '{{.Name}} {{.State}} {{.Health}}' || true)"
  echo "--- $(date '+%H:%M:%S') ---"
  echo "${ps}"
  unhealthy="$(printf '%s\n' "${ps}" | grep -cE '(starting|unhealthy|exited)' || true)"
  [ "${unhealthy}" = "0" ] && break
  [ "$(date +%s)" -lt "${deadline}" ] || { echo "[error] 等健康超时"; docker compose logs --tail=60; exit 1; }
  sleep 10
done
docker compose ps

echo "=== [up] 6/6 端口纪律复核（本机监听面）==="
# 只允许 80 / 443（+ ssh）。5432/6379/3000/8080 一个都不许在宿主上监听。
if ss -lntp 2>/dev/null | grep -E ':(5432|6379|3000|8080)\b'; then
  echo "[error] 宿主上有不该监听的端口（accept 2 第 1 段）"; exit 1
fi
ss -lntp 2>/dev/null | grep -E ':(80|443)\b' || echo "  … 没看到 80/443 监听？检查 docker-proxy"
echo "[up] ✅ 完成"
