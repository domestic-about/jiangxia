#!/usr/bin/env bash
# SYS-PROD-001 · 远端：宿主机器准备（swap / 时区 / 日志轮转 / 防火墙 / 数据盘核对）
#
# 由 deploy.sh 手动跑：`bash deploy.sh` 的 all 阶段不调用它（它要 sudo 改系统配置，
# 属于「买完机器第一次做的四件事」），部署手册 §1 会让运维先手工跑一次：
#   ssh <生产机> 'cd /opt/lqg && bash remote/00-host.sh'
set -euo pipefail

cd "$(dirname "$0")/.."
DATA_DIR="$(pwd)"
[ -f "${DATA_DIR}/.env" ] && { set -a; . "${DATA_DIR}/.env"; set +a; }
: "${LQG_DATA_DIR:=${DATA_DIR}}"

echo "=== [host] 1/5 时区 ==="
timedatectl set-timezone Asia/Shanghai 2>/dev/null || true
date

echo "=== [host] 2/5 swap（2 核 4G 上 gotenberg 会瞬间吃内存，留 2G swap 兜 OOM）==="
if swapon --show | grep -q .; then
  swapon --show
else
  echo "  没有 swap —— 建 2G（f=2G 的一次性文件，占系统盘）"
  fallocate -l 2G /swapfile && chmod 600 /swapfile && mkswap /swapfile && swapon /swapfile
  grep -q '^/swapfile' /etc/fstab || echo '/swapfile none swap sw 0 0' >> /etc/fstab
  sysctl -w vm.swappiness=10
  grep -q '^vm.swappiness' /etc/sysctl.conf || echo 'vm.swappiness=10' >> /etc/sysctl.conf
fi

echo "=== [host] 3/5 数据盘核对（postgres 的数据要落数据盘，不是系统盘）==="
df -h "${LQG_DATA_DIR}"
echo "  ★ 若 ${LQG_DATA_DIR} 与 / 在同一个设备上，说明数据盘还没挂 —— 先挂盘再起容器（部署手册 §1）"

echo "=== [host] 4/5 docker 日志轮转（免得 json-file 把系统盘写满）==="
if [ ! -f /etc/docker/daemon.json ]; then
  cat > /etc/docker/daemon.json <<'JSON'
{
  "log-driver": "json-file",
  "log-opts": { "max-size": "50m", "max-file": "5" },
  "live-restore": true
}
JSON
  systemctl restart docker
  echo "  已写入 /etc/docker/daemon.json 并重启 docker"
else
  echo "  /etc/docker/daemon.json 已存在，未改动（请人工确认 log-opts 里有 max-size）"
  cat /etc/docker/daemon.json
fi

echo "=== [host] 5/5 防火墙：只放 22 / 80 / 443 ==="
if command -v ufw >/dev/null 2>&1; then
  ufw allow 22/tcp; ufw allow 80/tcp; ufw allow 443/tcp
  echo "y" | ufw enable || true
  ufw status verbose
elif command -v firewall-cmd >/dev/null 2>&1; then
  firewall-cmd --permanent --add-service=ssh
  firewall-cmd --permanent --add-service=http
  firewall-cmd --permanent --add-service=https
  firewall-cmd --reload
  firewall-cmd --list-all
else
  echo "  没有 ufw / firewalld —— ★ 云厂商的**安全组**才是真正的门（阿里云控制台），"
  echo "    入方向只开 22 / 80 / 443；5432 / 6379 / 3000 / 8080 一条都不许有（accept 2 第 1 段）。"
fi
echo "[host] ✅ 完成（安全组仍需在云控制台人工确认，见部署手册 §1.4）"
