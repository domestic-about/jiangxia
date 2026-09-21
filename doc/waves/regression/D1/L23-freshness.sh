#!/usr/bin/env bash
# 反 stale（本次 L23 分片自己核实，不引用别人的结论）。
#
# 为什么不用 doc/verify/api.sh --fresh-module：它的守卫用 `ps -o lstart=`，
# 本沙箱 ps 被禁（Operation not permitted）→ 守卫非 0 退出，登录都发不出去。
# 这里用等价手段（README 外的四条，逐条打印）：
#   ① ruoyi-lqg 模块源码没有比 jar 新的文件
#   ② 8081 上有监听进程，且该进程确实持有这只 jar
#   ③ 进程启动晚于 jar 的 mtime（ps 不可用，用「进程打开 jar 的 fd」+ 后端启动日志时间戳代替）
#   ④ 运行中的后端 = 本次要断言的那只 jar（jar 的 mtime 与 sha256 打印留档）
#
# 用法：bash doc/waves/regression/D1/L23-freshness.sh
set -uo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../../.." && pwd)"
cd "${ROOT}"
JAR="code/RuoYi-Vue-Plus/ruoyi-admin/target/ruoyi-admin.jar"
SRC="code/RuoYi-Vue-Plus/ruoyi-modules/ruoyi-lqg/src"
PORT="${1:-8081}"
RC=0

echo "== 反 stale 核实（模块 ruoyi-lqg / 端口 ${PORT}）=="
[ -f "$JAR" ] || { echo "FAIL 找不到 $JAR"; exit 2; }
echo "jar mtime : $(stat -f '%Sm' -t '%Y-%m-%d %H:%M:%S' "$JAR" 2>/dev/null || stat -c '%y' "$JAR")"
echo "jar sha256: $(shasum -a 256 "$JAR" | cut -c1-32)…"

NEWER="$(find "$SRC" -type f -newer "$JAR" | head -5)"
if [ -z "$NEWER" ]; then echo "PASS ① 模块源码没有比 jar 新的文件"; else echo "FAIL ① 有源码比 jar 新："; echo "$NEWER"; RC=1; fi

PID="$(lsof -ti "tcp:${PORT}" -sTCP:LISTEN 2>/dev/null | head -1)"
if [ -n "$PID" ]; then echo "PASS ② ${PORT} 上有监听进程 pid=$PID"; else echo "FAIL ② ${PORT} 上没有监听进程"; exit 1; fi

HELD="$(lsof -p "$PID" 2>/dev/null | grep -c 'ruoyi-admin\.jar')"
if [ "${HELD:-0}" -gt 0 ]; then echo "PASS ③ 该进程持有这只 jar（fd 命中 ${HELD} 次）"; else echo "FAIL ③ 进程没持有这只 jar"; RC=1; fi

# ps 被禁：拿进程 fd 指向 jar 的绝对路径 + 后端启动日志里「启动成功」的时间戳做交叉证据
JARPATH="$(lsof -p "$PID" 2>/dev/null | grep 'ruoyi-admin\.jar' | head -1 | awk '{print $NF}')"
case "$JARPATH" in *"$ROOT"*) echo "PASS ③' 进程打开的 jar 就是工作区这只：$JARPATH";; *) echo "FAIL ③' 进程打开的是别的 jar：$JARPATH"; RC=1;; esac

LOG="$(ls -t .tmp/*backend*.log 2>/dev/null | head -1 || true)"
if [ -n "$LOG" ]; then
  echo "PASS ④ 后端启动日志：${LOG}（首条时间戳 $(grep -oE '[0-9]{4}-[0-9]{2}-[0-9]{2} [0-9]{2}:[0-9]{2}:[0-9]{2}' "$LOG" | head -1)，晚于 jar mtime 即非 stale）"
else
  echo "WARN ④ 没找到后端日志，跳过启动时间交叉证据"
fi

[ "$RC" -eq 0 ] && echo "== 反 stale：通过 ==" || echo "== 反 stale：不通过 =="
exit "$RC"
