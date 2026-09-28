#!/usr/bin/env bash
# SYS-PROD-001 · 生产健康检查与告警（cron 每 5 分钟跑一次；在**生产宿主机**上跑）
#
#   安装：bash deploy.sh cron      # 生成 healthcheck.env + 装 crontab（幂等）
#   手工：bash /opt/lqg/healthcheck.sh
#
# 检查四件事（票面 §2）：
#   1. 容器不健康 / 反复重启（5 个 lqg-prod-* 容器都有 healthcheck，见 docker-compose.yml）
#   2. 数据盘使用率 > LQG_HEALTH_DISK_PCT（默认 80）
#   3. TLS 证书剩余 < LQG_HEALTH_CERT_DAYS（默认 15）——用 `openssl x509 -checkend`，不做日期解析
#   4. 容器频繁重启（RestartCount 增长）—— 只看当下状态，"反复重启"用 Restarting 状态 + 计数近似
#
# 任一告警 → 往 LQG_ALERT_WEBHOOK（飞书自定义机器人）发一条，脚本非零退出（cron 会记 log）。
# 备份失败的告警**不在这里**（SYS-BACKUP-001 的 backup.sh 自己发，避免两条告警互相顶）。
#
# ★ 配置来源：${LQG_DATA_DIR}/healthcheck.env（600，由 05-cron.sh 从 .env 生成）。
#   之所以不直接读 .env：cron 环境极简，且 healthcheck 只需要其中 4 个值；
#   直接 source .env 会把生产 AK 等无关密钥带进一个每 5 分钟就跑的脚本的环境里。
set -uo pipefail

DATA_DIR="${LQG_DATA_DIR:-/opt/lqg}"
HC_ENV="${DATA_DIR}/healthcheck.env"
if [ -f "${HC_ENV}" ]; then
  set -a; # shellcheck disable=SC1090
  . "${HC_ENV}"; set +a
fi

LQG_ALERT_WEBHOOK="${LQG_ALERT_WEBHOOK:-}"
DISK_PCT="${LQG_HEALTH_DISK_PCT:-80}"
CERT_DAYS="${LQG_HEALTH_CERT_DAYS:-15}"
DOMAIN="${LQG_PROD_DOMAIN:-}"
CERT="${DATA_DIR}/certs/${DOMAIN}/fullchain.pem"

ALERTS=()
add() { ALERTS+=("$1"); }

# ── 1. 容器健康 ────────────────────────────────────────────────────────────────
if command -v docker >/dev/null 2>&1; then
  # 只看本项目的容器（lqg-prod-*）：这台机器上不该有别的项目，但也不去惊动别人的容器。
  # ★ 解析用 `{{.Status}}` 的文本（docker compose / docker ps 各版本的 format 字段名不一样，
  #   `{{.Health}}` 在 `docker ps` 下是空的）——Status 形如：
  #     "Up 3 minutes (healthy)" / "Up 1 minute (health: starting)" / "Restarting (1) 5 seconds ago"
  while IFS=$'\t' read -r name status; do
    [ -n "${name}" ] || continue
    case "${status}" in
      *"(unhealthy)"*)                     add "容器 ${name} 不健康：${status}" ;;
      *"(health: starting)"*)              : ;;   # 启动中：start_period 内不算故障
      *Restarting*)                        add "容器 ${name} 正在反复重启：${status}" ;;
      *Exited*|*Dead*)                     add "容器 ${name} 已退出：${status}" ;;
      *"(healthy)"*)                       : ;;
      *)                                   add "容器 ${name} 状态异常：${status}" ;;
    esac
  done < <(docker ps -a --filter 'name=lqg-prod-' --format '{{.Names}}\t{{.Status}}')
  # 期望的 5 个容器都必须在
  for c in postgres redis gotenberg backend nginx; do
    docker ps --format '{{.Names}}' | grep -qx "lqg-prod-${c}" || add "容器 lqg-prod-${c} 不在运行（compose up 过吗？）"
  done
else
  add "这台机器上没有 docker 命令 —— healthcheck 无法工作"
fi

# ── 2. 数据盘使用率 ────────────────────────────────────────────────────────────
if [ -d "${DATA_DIR}" ]; then
  USED="$(df -P "${DATA_DIR}" | awk 'NR==2 {gsub(/%/,"",$5); print $5}')"
  if [ -n "${USED}" ] && [ "${USED}" -gt "${DISK_PCT}" ] 2>/dev/null; then
    add "磁盘使用率 ${USED}% > ${DISK_PCT}%（${DATA_DIR}）"
  fi
else
  add "数据盘目录 ${DATA_DIR} 不存在"
fi

# ── 3. 证书剩余天数 ────────────────────────────────────────────────────────────
# ★ 不用 `openssl x509 -enddate` + 日期解析（跨平台的 date -d / date -j 差异是坑）：
#   `-checkend N` 直接问「N 秒后是否还有效」，退出码 0=还有效 / 1=已过期或将在 N 秒内过期 / 2=读不到文件。
if [ -n "${DOMAIN}" ] && [ -s "${CERT}" ]; then
  if ! openssl x509 -in "${CERT}" -noout -checkend $(( CERT_DAYS * 86400 )) >/dev/null 2>&1; then
    END="$(openssl x509 -in "${CERT}" -noout -enddate 2>/dev/null | cut -d= -f2)"
    add "证书剩余不足 ${CERT_DAYS} 天（或已过期）：${DOMAIN} notAfter=${END:-未知}"
  fi
else
  add "证书文件缺失或为空：${CERT}（域名 ${DOMAIN:-未配置}）"
fi

# ── 4. 证书与线上实际提供的是否一致（续期没 reload 的经典症状）──────────────────
# acme.sh 续了、但没 reload → 文件是新的、443 上服务的还是旧的。这条能把它抓出来。
if [ -n "${DOMAIN}" ] && [ -s "${CERT}" ] && command -v openssl >/dev/null 2>&1; then
  LIVE="$(echo | timeout 8 openssl s_client -servername "${DOMAIN}" -connect "${DOMAIN}:443" 2>/dev/null \
          | openssl x509 -noout -fingerprint -sha256 2>/dev/null | cut -d= -f2)"
  FILE="$(openssl x509 -in "${CERT}" -noout -fingerprint -sha256 2>/dev/null | cut -d= -f2)"
  if [ -n "${LIVE}" ] && [ -n "${FILE}" ] && [ "${LIVE}" != "${FILE}" ]; then
    add "线上提供的证书与磁盘上的不是同一张（${DOMAIN}）—— 多半是续期后没 reload nginx：docker exec lqg-prod-nginx nginx -s reload"
  fi
fi

# ── 汇总与告警 ─────────────────────────────────────────────────────────────────
STAMP="$(date '+%F %T')"
HOST="$(hostname)"
if [ "${#ALERTS[@]}" -eq 0 ]; then
  echo "[${STAMP}] healthcheck OK (${HOST})"
  exit 0
fi

TEXT="【lqg 生产告警】${HOST} ${STAMP}
$(printf -- '- %s\n' "${ALERTS[@]}")
处置见 doc/ops/运维手册.md §3；看容器：cd ${DATA_DIR} && docker compose ps"

# 打印到 stdout（cron 的 log 里有全文，webhook 挂了也留痕）
printf '%s\n' "${TEXT}"

if [ -n "${LQG_ALERT_WEBHOOK}" ]; then
  # 飞书自定义机器人：{"msg_type":"text","content":{"text":"..."}}
  # 用 jq 拼 body 而不是手写 JSON 字符串（告警文本里有换行 / 括号 / 中文，手拼必坏）。
  if command -v jq >/dev/null 2>&1; then
    BODY="$(jq -nc --arg t "${TEXT}" '{msg_type:"text", content:{text:$t}}')"
  else
    # 没 jq 时的兜底：只做最小转义（引号 / 反斜杠 / 换行）。
    ESC="$(printf '%s' "${TEXT}" | sed -e 's/\\/\\\\/g' -e 's/"/\\"/g' | awk '{printf "%s\\n", $0}')"
    BODY="{\"msg_type\":\"text\",\"content\":{\"text\":\"${ESC}\"}}"
  fi
  CODE="$(curl -s -o /dev/null -w '%{http_code}' --max-time 10 -X POST \
          -H 'Content-Type: application/json' -d "${BODY}" "${LQG_ALERT_WEBHOOK}" || echo 000)"
  case "${CODE}" in
    200) echo "[${STAMP}] 告警已发出（webhook 200）" ;;
    *)   echo "[${STAMP}] ⚠️ 告警发送失败（HTTP ${CODE}）—— 告警本身也留在这条 stdout 里" >&2 ;;
  esac
else
  echo "[${STAMP}] ⚠️ LQG_ALERT_WEBHOOK 未配置，只打日志（owner 待提供，见 doc/handover/d8-blockers.json）" >&2
fi

exit 1
