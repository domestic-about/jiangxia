#!/usr/bin/env bash
# CRYO-REMIND-001 · `--fresh-module ruoyi-lqg` 的**等价反 stale 证据**（本 agent 沙箱里
# `api.sh --fresh-module` 恒非 0 退出：`ps -o lstart=` 被禁 + macOS 没有 `date -d`；
# 见 issue #151 / CRYO-FLOW-001 WARN-1 + WARN-6）。逐项打印，人工/QA 可直接复跑。
set -uo pipefail
cd "$(cd "$(dirname "${BASH_SOURCE[0]}")/../../../../.." && pwd)"
WS="$(pwd)"
SRC="code/RuoYi-Vue-Plus/ruoyi-modules/ruoyi-lqg/src"
JAR="code/RuoYi-Vue-Plus/ruoyi-admin/target/ruoyi-admin.jar"
PORT=8081

echo "① jar：$(ls -l "${JAR}" | awk '{print $6, $7, $8, $5}')"
echo "② 源码比 jar 新的文件（必须为空，两处：src 与 本票新包）："
find "${SRC}" -type f -newer "${JAR}" | head -5 | sed 's/^/     /'
find "${SRC}/main/java/org/dromara/lqg/cryo/remind" -type f -newer "${JAR}" | head -5 | sed 's/^/     /'
echo "② 命中行数 = $(find "${SRC}" -type f -newer "${JAR}" | wc -l | tr -d ' ')"

PID="$(lsof -ti tcp:${PORT} -sTCP:LISTEN 2>/dev/null | head -1)"
echo "③ 8081 监听 PID = ${PID:-（空）}"
[ -n "${PID}" ] || { echo "   ✗ 没有后端进程"; exit 2; }

# 进程启动秒：沙箱里 `ps -o lstart=` 被禁，用 libproc（macOS 专属；CRYO-FLOW-001 §8-5 同款）
python3 - "$PID" "$JAR" <<'PY'
import ctypes, ctypes.util, os, sys, time
pid, jar = int(sys.argv[1]), sys.argv[2]
lib = ctypes.CDLL(ctypes.util.find_library('proc'))
buf = ctypes.create_string_buffer(136)
if lib.proc_pidinfo(pid, 3, 0, buf, 136) <= 0:
    print("   ✗ proc_pidinfo 取不到启动时间"); sys.exit(1)
start = int.from_bytes(buf.raw[120:128], 'little')
jar_m = int(os.stat(jar).st_mtime)
print(f"   jar mtime = {time.strftime('%F %T', time.localtime(jar_m))} ({jar_m})")
print(f"   进程启动  = {time.strftime('%F %T', time.localtime(start))} ({start})")
print(f"   {'✓ 进程不早于 jar' if start >= jar_m else '✗ 进程早于 jar（打了包没重启）'}")
sys.exit(0 if start >= jar_m else 1)
PY
echo "④ 进程持有 jar：$(lsof -p "${PID}" 2>/dev/null | grep -c 'ruoyi-admin.jar') 处"
echo "⑤ 嵌套 jar 内含本票新 class：$(unzip -p "${JAR}" BOOT-INF/lib/ruoyi-lqg-*.jar 2>/dev/null > /tmp/rm001-lqg.jar; unzip -l /tmp/rm001-lqg.jar 2>/dev/null | grep -cE 'cryo/remind/(service/CryoOverdueService|sql/CryoOverdueSqlProvider|mapper/CryoOverdueMapper)\.class') 个"
echo "⑥ flyway 里本票那一行：$(python3 doc/verify/db.py --sql "SELECT script FROM flyway_schema_history WHERE script LIKE 'V20260924120%__CRYO-REMIND-001-%'")"
echo "⑦ GET /lqg/sys/ping（--as staff）：$(bash doc/verify/api.sh --as staff --bizcode GET /lqg/sys/ping)"
