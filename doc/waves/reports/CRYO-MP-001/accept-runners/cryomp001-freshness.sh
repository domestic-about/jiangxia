#!/usr/bin/env bash
# CRYO-MP-001 · `--fresh-module ruoyi-lqg` 的等价反 stale 证据（八项）。
#
# 为什么需要它：`doc/verify/api.sh` 第 71 行用 `ps -o lstart=`，本 subagent 沙箱里
# `/bin/ps: Operation not permitted` → 原样带 `--fresh-module` 恒 exit 2（既有 WARN，issue #151）。
# 按规矩**没有改 `api.sh``，改成在这里逐项取证。
set -uo pipefail
WS="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../../../.." && pwd)"
cd "$WS"
JAR="code/RuoYi-Vue-Plus/ruoyi-admin/target/ruoyi-admin.jar"
PID="$(lsof -ti tcp:8081 -sTCP:LISTEN 2>/dev/null | head -1)"

echo "① jar：$(stat -f '%Sm' -t '%Y-%m-%d %H:%M:%S' "$JAR") $(stat -f %z "$JAR")"
echo "② 源码比 jar 新的文件（必须为空，两处：ruoyi-lqg 与 ruoyi-admin）："
find code/RuoYi-Vue-Plus/ruoyi-modules/ruoyi-lqg/src code/RuoYi-Vue-Plus/ruoyi-admin/src -type f -newer "$JAR" | head -5
echo "② 命中行数 = $(find code/RuoYi-Vue-Plus/ruoyi-modules/ruoyi-lqg/src code/RuoYi-Vue-Plus/ruoyi-admin/src -type f -newer "$JAR" | wc -l | tr -d ' ')"
echo "③ 8081 监听 PID = ${PID:-（无）}"
JAR_EPOCH="$(stat -f %m "$JAR")"
START_EPOCH="$(python3 - "$PID" <<'PY'
import ctypes, ctypes.util, sys
pid = int(sys.argv[1])
libc = ctypes.CDLL(ctypes.util.find_library('c'))
buf = ctypes.create_string_buffer(4096)
# proc_pidinfo(PROC_PIDTBSDINFO=3) 里 pbi_start_tvsec 的偏移在 macOS 上是 120（struct proc_bsdinfo，sizeof=136）
if libc.proc_pidinfo(pid, 3, 0, buf, 4096) <= 0:
    print(0)
else:
    print(int.from_bytes(buf.raw[120:128], 'little'))
PY
)"
echo "   jar mtime = $(date -r "$JAR_EPOCH" '+%Y-%m-%d %H:%M:%S') ($JAR_EPOCH)"
echo "   进程启动  = $(date -r "$START_EPOCH" '+%Y-%m-%d %H:%M:%S') ($START_EPOCH)"
if [ "$START_EPOCH" -ge "$JAR_EPOCH" ]; then echo "   ✓ 进程不早于 jar"; else echo "   ✗ 进程早于 jar（打了包没重启）"; fi
echo "④ 进程持有 jar：$(lsof -p "$PID" 2>/dev/null | grep -c 'ruoyi-admin/target/ruoyi-admin.jar')"
echo "⑤ 嵌套 jar 内含本票新 class（cryo/mp）："
unzip -l "$JAR" 2>/dev/null | grep -c 'ruoyi-lqg' >/dev/null
python3 - "$JAR" <<'PY'
import io, sys, zipfile
jar = sys.argv[1]
with zipfile.ZipFile(jar) as z:
    inner = [n for n in z.namelist() if n.endswith('ruoyi-lqg-5.5.3.jar')]
    data = z.read(inner[0])
with zipfile.ZipFile(io.BytesIO(data)) as lqg:
    hits = [n for n in lqg.namelist() if '/cryo/mp/' in n]
print('   ' + '\n   '.join(hits) if hits else '   （没找到 cryo/mp）')
print(f"   命中 {len(hits)} 个条目")
PY
echo "⑥ flyway 里最新的三支（本票不新增迁移）："
python3 doc/verify/db.py --sql "SELECT version || '|' || description FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 3"
echo "⑦ GET /lqg/sys/ping（--as staff）："
bash doc/verify/api.sh --as staff --bizcode GET /lqg/sys/ping
echo "⑧ GET /mp/int/cryo/batch/list（--as staff，本票新端点）："
bash doc/verify/api.sh --as staff GET '/mp/int/cryo/batch/list?pageSize=1' | jq -c '{code,total,tabCounts}'
