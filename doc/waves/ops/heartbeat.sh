#!/usr/bin/env bash
# 会话存活心跳：由 zhixing 主会话起成一个**受管后台作业**，每 30 秒把时间戳写进 heartbeat。
#
#   起：  nohup bash doc/waves/ops/heartbeat.sh > /dev/null 2>&1 &
#   看：  date -r doc/waves/ops/heartbeat
#
# 语义：**这个文件新鲜 ⟺ 驱动 zhixing 的那个 DSH 进程还活着**。
#   - 会话/turn 正常跑（包括在等一个 40 分钟的 subagent）→ 心跳照跳，watchdog 不动手；
#   - DSH 进程死了 / 机器睡了 / GUI 关了 → 后台作业随之消失，heartbeat 停止更新；
#   - 于是 watchdog.sh 在 STALE_SECS（默认 600 秒 = 10 分钟）之后接手。
#
# 为什么用「进程存活」而不是「turn 存活」当信号：等 subagent 时主会话本来就没有动静，
# 拿 turn 当信号会把正常的长任务误判成断线。turn 级的网络抖动由 dsh-llm-retry 兜（见 README）。
set -u
WS="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
HB="${WS}/doc/waves/ops/heartbeat"
mkdir -p "$(dirname "${HB}")"
while :; do
  date +%s > "${HB}.tmp" && mv -f "${HB}.tmp" "${HB}"
  sleep 30
done
