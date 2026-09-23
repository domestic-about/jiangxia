#!/usr/bin/env bash
# SYS-PROD-001 · 远端：签证书 + 装续期 cron（在**生产服务器**上跑）
#
# 由 deploy.sh 分离执行（bash deploy.sh cert），输出落 logs/cert.log。
#
# 路线：acme.sh + webroot 校验（80 端口由 compose 的 nginx 提供 /.well-known/acme-challenge/）。
# 为什么不用 standalone：standalone 要停掉占 80 的 nginx，等于每次续期都得停站。
# 为什么不用 DNS 校验：要 API 密钥（甲方域名在阿里云，密钥又是另一件 owner 待提供的事）。
set -euo pipefail

cd "$(dirname "$0")/.."
DATA_DIR="$(pwd)"
set -a; . "${DATA_DIR}/.env"; set +a
: "${LQG_PROD_DOMAIN:?}" "${LQG_CERT_EMAIL:?}" "${LQG_DATA_DIR:=${DATA_DIR}}"
: "${LQG_CERT_CA:=letsencrypt}"

ACME_HOME=/root/.acme.sh              # ★ 保持 acme.sh 默认位置：它装的续期 cron 直接引用这里的路径
WEBROOT="${LQG_DATA_DIR}/acme-webroot"
CERTDIR="${LQG_DATA_DIR}/certs/${LQG_PROD_DOMAIN}"

echo "=== [cert] 域名 ${LQG_PROD_DOMAIN} / CA ${LQG_CERT_CA} ==="

# ── 1. 装 acme.sh（幂等）────────────────────────────────────────────────────────
if [ ! -x "${ACME_HOME}/acme.sh" ]; then
  echo "  安装 acme.sh …"
  curl -fsSL https://get.acme.sh | sh -s -- "email=${LQG_CERT_EMAIL}"
else
  echo "  acme.sh 已装：$("${ACME_HOME}/acme.sh" --version)"
fi

export LE_WORKING_DIR="${ACME_HOME}"
ACME="${ACME_HOME}/acme.sh"

# ── 2. 续期后必须 reload nginx 的钩子 ──────────────────────────────────────────
# ★ 关键：nginx **只在启动 / reload 时**读证书文件。acme.sh 默认只把新证书换到自己的
#   --cert-file/--key-file 路径下，不 reload 的话新证书要等到下次重启才生效 ——
#   于是「证书明明续了，浏览器还是旧的 / 到期前一天告警」。
#   这个脚本由 acme.sh 的 --reloadcmd 调用（**每条续期命令都要带，包括 cron 里那条**）：
#     · docker cp 不支持改属主，容器里 nginx 以 root 跑、读的是同一个文件 → 用 cp 进 bind mount
#       （acme.sh 把证书写到 ${LQG_DATA_DIR}/cert-origin/，compose 只挂 certs/ 那半边，
#        所以不能在原地写，必须 cp 过去）
#     · reload 用 `nginx -s reload`（平滑，不断连接），不是 restart
cat > "${LQG_DATA_DIR}/remote/06-cert-install.sh" <<'SH'
#!/usr/bin/env bash
# 由 acme.sh 的 --reloadcmd 调用（参数：域名 私钥 全链 链 完整链）。
# 幂等：把证书从 cert-origin 搬到 compose 挂载的 certs 目录，然后平滑 reload nginx。
set -euo pipefail
DOMAIN="$1"; KEY="$2"; FULLCHAIN="$3"
cd "$(dirname "$0")/.."
DATA_DIR="$(pwd)"
. "${DATA_DIR}/.env" 2>/dev/null || true
: "${LQG_DATA_DIR:=${DATA_DIR}}"
DEST="${LQG_DATA_DIR}/certs/${DOMAIN}"
install -d -m 755 "${DEST}"
# 先写临时文件再 mv：避免 reload 撞上「证书写了一半」的窗口
cp "${FULLCHAIN}" "${DEST}/fullchain.pem.tmp" && mv "${DEST}/fullchain.pem.tmp" "${DEST}/fullchain.pem"
cp "${KEY}"       "${DEST}/privkey.pem.tmp"   && mv "${DEST}/privkey.pem.tmp"   "${DEST}/privkey.pem"
chmod 644 "${DEST}/fullchain.pem"; chmod 600 "${DEST}/privkey.pem"
if docker ps --format '{{.Names}}' | grep -qx lqg-prod-nginx; then
  docker exec lqg-prod-nginx nginx -t
  docker exec lqg-prod-nginx nginx -s reload
  echo "$(date '+%F %T') cert-install ok ${DOMAIN} (reload nginx)"
else
  echo "$(date '+%F %T') cert-install ok ${DOMAIN} (nginx 容器不在，跳过 reload)"
fi
SH
chmod +x "${LQG_DATA_DIR}/remote/06-cert-install.sh"

# ── 3. 签发（首次 --issue；已有则 --renew 走一次）───────────────────────────────
if [ -d "${ACME_HOME}/${LQG_PROD_DOMAIN}_ecc" ] || [ -d "${ACME_HOME}/${LQG_PROD_DOMAIN}" ]; then
  echo "  已有证书，直接续期一次"
  "${ACME}" --renew -d "${LQG_PROD_DOMAIN}" --ecc || true
else
  echo "  首次签发（webroot=${WEBROOT}）"
  "${ACME}" --set-default-ca --server "${LQG_CERT_CA}"
  "${ACME}" --issue -d "${LQG_PROD_DOMAIN}" --webroot "${WEBROOT}" --keylength ec-256
fi

# ── 4. 安装到宿主的 cert-origin（再交给 06-cert-install.sh 搬进 certs/）─────────
ORIGIN="${LQG_DATA_DIR}/cert-origin/${LQG_PROD_DOMAIN}"
install -d -m 755 "${ORIGIN}"
"${ACME}" --install-cert -d "${LQG_PROD_DOMAIN}" --ecc \
  --key-file       "${ORIGIN}/privkey.pem" \
  --fullchain-file "${ORIGIN}/fullchain.pem" \
  --reloadcmd      "bash ${LQG_DATA_DIR}/remote/06-cert-install.sh ${LQG_PROD_DOMAIN} ${ORIGIN}/privkey.pem ${ORIGIN}/fullchain.pem"

# ── 5. 续期 cron（acme.sh 自己会装一条；这里打印出来让人核对）─────────────────
echo "=== [cert] 续期 cron ==="
"${ACME_HOME}/acme.sh" --install-cronjob >/dev/null 2>&1 || true
crontab -l 2>/dev/null | grep -n acme || echo "  （没看到 acme 的 cron —— 手工加：0 0 * * * ${ACME_HOME}/acme.sh --cron --home ${ACME_HOME} > /dev/null）"

echo "=== [cert] 结果 ==="
openssl x509 -in "${CERTDIR}/fullchain.pem" -noout -subject -issuer -dates
openssl x509 -in "${CERTDIR}/fullchain.pem" -noout -checkend 1296000 \
  && echo "  ✓ 剩余 > 15 天" || echo "  ✗ 剩余 ≤ 15 天"
docker exec lqg-prod-nginx nginx -t
echo "[cert] ✅ 完成"
