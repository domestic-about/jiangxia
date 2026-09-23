#!/usr/bin/env bash
# SYS-PROD-001 · 远端：装两条 cron（healthcheck 每 5 分钟 / cert 续期由 acme.sh 自己装）
#
# 由 deploy.sh 分离执行（bash deploy.sh cron），输出落 logs/cron.log。
set -euo pipefail

cd "$(dirname "$0")/.."
DATA_DIR="$(pwd)"
set -a; . "${DATA_DIR}/.env"; set +a
: "${LQG_DATA_DIR:=${DATA_DIR}}"

HEALTH="${LQG_DATA_DIR}/healthcheck.sh"
[ -x "${HEALTH}" ] || chmod +x "${HEALTH}"

echo "=== [cron] 1/2 生成 healthcheck 的配置（从 .env 抽，不猜默认值）==="
# ★ healthcheck.sh 的配置单独一份文件：它要能在 cron 的极简环境里独立跑，
#   不依赖调用者的 shell（cron 的 PATH 很短、也没有 set -a 的 .env）。
HC_ENV="${LQG_DATA_DIR}/healthcheck.env"
umask 077
cat > "${HC_ENV}" <<EOF
# 由 remote/05-cron.sh 从 ${LQG_DATA_DIR}/.env 生成（600）。改告警地址改这里或重跑 deploy.sh cron。
LQG_DATA_DIR=${LQG_DATA_DIR}
LQG_PROD_DOMAIN=${LQG_PROD_DOMAIN}
LQG_ALERT_WEBHOOK=${LQG_ALERT_WEBHOOK:-}
LQG_HEALTH_DISK_PCT=${LQG_HEALTH_DISK_PCT:-80}
LQG_HEALTH_CERT_DAYS=${LQG_HEALTH_CERT_DAYS:-15}
EOF
chmod 600 "${HC_ENV}"
echo "  ✓ ${HC_ENV}"

echo "=== [cron] 2/2 装 crontab ==="
# 幂等：先删掉本项目的旧行（带 LQG-PROD-001 标记的那条），再追加。
CRON_LINE="*/5 * * * * ${HEALTH} >> ${LQG_DATA_DIR}/logs/healthcheck.log 2>&1 # LQG-PROD-001-healthcheck"
(crontab -l 2>/dev/null | grep -v 'LQG-PROD-001-healthcheck' || true; echo "${CRON_LINE}") | crontab -
echo "  当前 crontab："
crontab -l | sed 's/^/    /'

echo "=== [cron] 立刻跑一次 healthcheck（确认脚本本身能跑，不是等 5 分钟才知道）==="
bash "${HEALTH}" && echo "  ✓ healthcheck 退出码 0" || echo "  ✗ healthcheck 非零退出（看上面输出；告警已尝试发出）"
echo "[cron] ✅ 完成"
