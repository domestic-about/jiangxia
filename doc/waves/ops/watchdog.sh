#!/usr/bin/env bash
# zhixing 断线重连看门狗 —— 一轮（建议由 launchd 每 60 秒调一次）。
#
#   bash doc/waves/ops/watchdog.sh --dry-run     # 只判断并打印它会做什么（默认行为）
#   bash doc/waves/ops/watchdog.sh --run         # 真去 resume
#
# 判断链（任何一步命中就退出，不会继续往下）：
#   1. state.json 不存在                     → 还没 init，不自动跑
#   2. PAUSE 哨兵存在                        → 主会话明确停在等人（exception/blocked/
#                                              blueprint_changed/qa_gate 带 S0-S1），不许自动接手
#   3. 全部任务 qa_passed/accepted           → all_done，收工
#   4. 需要人（有 ticket escalated，或某任务 qa_failed 且轮次 ≥2）→ 停手等人
#   5. heartbeat 比 STALE_SECS 新            → 有 live 会话在驱动，不抢
#   6. 拿不到 resume 锁                      → 已有看门狗/headless 在跑
#   7. 最近一小时 RESUME 次数 ≥ MAX_PER_HOUR → 限频，防止无人看管时烧 token
#   8. 否则：cd 到工作区根，跑
#        dsh --profile headless "$(cat resume-prompt.txt)"
#      （headless = 一次性跑完打印结果就退出，自带 subagent/jobs/bash，能跑完整的 zhixing 循环；
#        续跑靠 state.json，所以不需要恢复上一个会话的上下文）
#
# ★ 绝不 push、不合 main（那是 Kevin 的活）；resume 只推进 ②执行 的循环。
set -u

WS="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
OPS="${WS}/doc/waves/ops"
HB="${OPS}/heartbeat"
LOCK="${OPS}/.resume-lock"
LOG="${OPS}/watchdog.log"
PAUSE="${OPS}/PAUSE"
STATE="${WS}/doc/waves/state.json"
PROMPT="${OPS}/resume-prompt.txt"

STALE_SECS="${STALE_SECS:-600}"          # 心跳多久没更新算断线（默认 10 分钟）
MAX_PER_HOUR="${MAX_PER_HOUR:-6}"        # 一小时内最多自动 resume 几次
LOCK_STALE_SECS="${LOCK_STALE_SECS:-3600}"
MODE="dry-run"
[ "${1:-}" = "--run" ] && MODE="run"

# launchd 的 PATH 很短，显式补齐；dsh 在 npx 缓存里，路径可能随缓存刷新而变，做多路兜底
export PATH="/opt/homebrew/bin:/usr/local/bin:/usr/bin:/bin:/usr/sbin:/sbin:${HOME}/.nvm/versions/node/v24.11.1/bin:${PATH:-}"
PY=/usr/bin/python3
[ -x "$PY" ] || PY=python3

mkdir -p "$OPS"
log() { printf '%s [%s] %s\n' "$(date '+%Y-%m-%d %H:%M:%S')" "$$" "$*" >> "$LOG"; }

# ── 1. state.json ────────────────────────────────────────────────────────────
[ -f "$STATE" ] || { log "SKIP 没有 state.json（还没 init），不自动跑"; exit 0; }

# ── 2. PAUSE 哨兵 ────────────────────────────────────────────────────────────
if [ -f "$PAUSE" ]; then
  log "SKIP PAUSE 哨兵存在：$(head -1 "$PAUSE" 2>/dev/null) —— 主会话停在等人，不自动接手"
  exit 0
fi

# ── 3/4. 从 state.json 读进度 ────────────────────────────────────────────────
PROBE_OUT="$(STATE="$STATE" "$PY" - <<'PY' 2>/dev/null || echo "unknown parse-failed"
import json, os
try:
    d = json.load(open(os.environ["STATE"], encoding="utf-8"))
except Exception as e:
    print("unknown", "state.json 读不了：%s" % e); raise SystemExit(0)
phases = d.get("phases") or []
tickets = d.get("tickets") or {}
done_states = {"qa_passed", "accepted"}
esc = sorted(t for t, v in tickets.items() if (v or {}).get("status") == "escalated")
failed_hard = [p.get("id") for p in phases
               if p.get("status") == "qa_failed" and int(p.get("qa_rounds") or 0) >= 2]
if esc or failed_hard:
    print("needs_human", "escalated=%s qa_failed>=2=%s" % (",".join(esc) or "-", ",".join(failed_hard) or "-"))
elif phases and all(p.get("status") in done_states for p in phases):
    print("all_done", "phases=%d" % len(phases))
else:
    pend = [p.get("id") for p in phases if p.get("status") not in done_states]
    print("running", "未完成任务=%s" % ",".join(pend))
PY
)"
VERDICT="${PROBE_OUT%% *}"
DETAIL="${PROBE_OUT#* }"

case "$VERDICT" in
  all_done)    log "SKIP all_done（$DETAIL），收工"; exit 0 ;;
  needs_human) log "SKIP 需要人（$DETAIL）—— 按 zhixing 规矩不许自动接手"; exit 0 ;;
  unknown)     log "SKIP state.json 状态未知（$DETAIL）"; exit 0 ;;
  running)     : ;;
  *)           log "SKIP 判据异常（VERDICT=$VERDICT）"; exit 0 ;;
esac

# ── 5. 心跳新鲜度 ────────────────────────────────────────────────────────────
if [ -f "$HB" ]; then
  NOW=$(date +%s); MT=$(date -r "$HB" +%s 2>/dev/null || stat -f %m "$HB")
  AGE=$((NOW - MT))
  if [ "$AGE" -lt "$STALE_SECS" ]; then
    exit 0            # 有人活着，正常情况走这里，不写日志免得刷屏
  fi
else
  AGE=-1
fi

# ── 6. resume 锁（macOS 没有 flock，用 mkdir 的原子性）────────────────────────
if ! mkdir "$LOCK" 2>/dev/null; then
  LMT=$(date -r "$LOCK" +%s 2>/dev/null || echo 0)
  if [ $(( $(date +%s) - LMT )) -gt "$LOCK_STALE_SECS" ]; then
    log "WARN 锁目录超过 ${LOCK_STALE_SECS}s，视为陈旧锁，清掉重试"
    rmdir "$LOCK" 2>/dev/null && mkdir "$LOCK" 2>/dev/null || { log "SKIP 抢锁失败"; exit 0; }
  else
    log "SKIP 已有 resume 在跑（锁 $(basename "$LOCK")）"
    exit 0
  fi
fi
trap 'rmdir "$LOCK" 2>/dev/null' EXIT

# ── 7. 限频 ──────────────────────────────────────────────────────────────────
RECENT=0
[ -f "$LOG" ] && RECENT=$(grep -c 'RESUME' <(tail -n 400 "$LOG") 2>/dev/null || echo 0)
# 只数最近一小时：日志行首就是时间戳，用 awk 比时间
RECENT=$("$PY" - "$LOG" <<'PY' 2>/dev/null || echo 0
import sys, time
from datetime import datetime, timedelta
cut = datetime.now() - timedelta(hours=1)
n = 0
try:
    for line in open(sys.argv[1], encoding="utf-8", errors="replace"):
        if " RESUME " not in line:
            continue
        try:
            if datetime.strptime(line[:19], "%Y-%m-%d %H:%M:%S") >= cut:
                n += 1
        except Exception:
            pass
except OSError:
    pass
print(n)
PY
)
if [ "${RECENT:-0}" -ge "$MAX_PER_HOUR" ]; then
  log "SKIP 最近一小时已 resume ${RECENT} 次（上限 ${MAX_PER_HOUR}）—— 多半不是断线，是别的问题，停下来等人看"
  exit 0
fi

# ── 8/9. 真的动手 ────────────────────────────────────────────────────────────
DSH="$(command -v dsh || true)"
[ -n "$DSH" ] || DSH="$(ls -1 "${HOME}"/.npm/_npx/*/node_modules/.bin/dsh 2>/dev/null | head -1)"
[ -n "$DSH" ] || { log "ERROR 找不到 dsh 可执行文件，放弃"; exit 0; }
[ -f "$PROMPT" ] || { log "ERROR 缺少 resume-prompt.txt，放弃"; exit 0; }

if [ "$MODE" != "run" ]; then
  # ★ 全角标点紧跟 $VAR 会被吃进变量名（README 坑 #6），一律 ${VAR}
  log "DRYRUN 会 resume：心跳已停 ${AGE}s，${DETAIL}；dsh=${DSH}"
  echo "dry-run：会 resume（心跳停 ${AGE}s，${DETAIL}）"
  exit 0
fi

log "RESUME 心跳已停 ${AGE}s（${DETAIL}）→ ${DSH} --profile headless <resume-prompt.txt>"
echo "$$" > "${OPS}/.driver"
cd "$WS" || exit 0
"$DSH" --profile headless "$(cat "$PROMPT")" >> "$LOG" 2>&1
RC=$?
log "RESUME-END 退出码 ${RC}（下一轮 watchdog 会按心跳与限频重新判断）"
exit 0
