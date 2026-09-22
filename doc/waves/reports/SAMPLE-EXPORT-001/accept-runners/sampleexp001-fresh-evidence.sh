#!/usr/bin/env bash
# SAMPLE-EXPORT-001 · `api.sh --fresh-module` 在本 subagent 沙箱恒挂，这是**等价反 stale 取证**
# （不修改只读的 api.sh；同一手段 EMBED-WEB-001 / SAMPLE-WEB-001 / AUTH-EXT-002 用过）。
#
#   bash doc/waves/reports/SAMPLE-EXPORT-001/accept-runners/sampleexp001-fresh-evidence.sh
set -uo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../../../.." && pwd)"
JAR="${ROOT}/code/RuoYi-Vue-Plus/ruoyi-admin/target/ruoyi-admin.jar"
LOG="${ROOT}/.tmp/sample-export-backend.log"

echo "jar                     : ${JAR}"
echo "jar mtime               : $(stat -f '%Sm' -t '%Y-%m-%d %H:%M:%S' "${JAR}")"
echo "jar epoch               : $(stat -f %m "${JAR}")"
echo "lqg src newer than jar  : $(find "${ROOT}/code/RuoYi-Vue-Plus/ruoyi-modules/ruoyi-lqg/src" -type f -newer "${JAR}" | wc -l | tr -d ' ') 个文件"
echo "admin src newer than jar: $(find "${ROOT}/code/RuoYi-Vue-Plus/ruoyi-admin/src" -type f -newer "${JAR}" | wc -l | tr -d ' ') 个文件"
PID="$(lsof -ti tcp:8081 -sTCP:LISTEN 2>/dev/null | head -1 || true)"
echo "8081 PID                : ${PID:-（空）}"
if [ -n "${PID}" ]; then
  LSOF_OUT="$(mktemp)"; lsof -p "${PID}" > "${LSOF_OUT}" 2>/dev/null || true
  echo "该 PID 持有 jar 的行数  : $(grep -c 'ruoyi-admin.jar' "${LSOF_OUT}")"
  rm -f "${LSOF_OUT}"
  START_EPOCH="$(python3 - "${PID}" <<'PY' 2>/dev/null
import ctypes, ctypes.util, sys
pid = int(sys.argv[1])
try:
    import psutil
    print(int(psutil.Process(pid).create_time())); raise SystemExit(0)
except SystemExit:
    raise
except Exception:
    pass
lib = ctypes.CDLL(ctypes.util.find_library('proc') or '/usr/lib/libproc.dylib')
class PBI(ctypes.Structure):
    _fields_ = [("pbi_flags", ctypes.c_uint32), ("pbi_status", ctypes.c_uint32), ("pbi_xstatus", ctypes.c_uint32),
                ("pbi_pid", ctypes.c_uint32), ("pbi_ppid", ctypes.c_uint32), ("pbi_uid", ctypes.c_uint32),
                ("pbi_gid", ctypes.c_uint32), ("pbi_ruid", ctypes.c_uint32), ("pbi_rgid", ctypes.c_uint32),
                ("pbi_svuid", ctypes.c_uint32), ("pbi_svgid", ctypes.c_uint32), ("rfu_1", ctypes.c_uint32),
                ("pbi_comm", ctypes.c_char * 16), ("pbi_name", ctypes.c_char * 32),
                ("pbi_nfiles", ctypes.c_uint32), ("pbi_pgid", ctypes.c_uint32), ("pbi_pjobc", ctypes.c_uint32),
                ("e_tdev", ctypes.c_uint32), ("e_tpgid", ctypes.c_uint32), ("pbi_nice", ctypes.c_int32),
                ("pbi_start_tvsec", ctypes.c_uint64), ("pbi_start_tvusec", ctypes.c_uint64)]
b = PBI()
if lib.proc_pidinfo(pid, 3, 0, ctypes.byref(b), ctypes.sizeof(b)) <= 0:
    raise SystemExit(1)
print(b.pbi_start_tvsec)
PY
)"
  JAR_EPOCH="$(stat -f %m "${JAR}" 2>/dev/null || stat -c %Y "${JAR}")"
  echo "进程启动 epoch          : ${START_EPOCH:-取不到}"
  if [ -n "${START_EPOCH}" ]; then
    echo "    启动时间            : $(date -r "${START_EPOCH}" '+%Y-%m-%d %H:%M:%S')"
    if [ "${START_EPOCH}" -ge "${JAR_EPOCH}" ]; then echo "进程启动 ≥ jar          : True"; else echo "进程启动 ≥ jar          : False ← stale"; fi
  fi
fi
echo "后端日志 mtime          : $(stat -f '%Sm' -t '%Y-%m-%d %H:%M:%S' "${LOG}")"
echo "--- 日志关键行 ---"
grep -aE "Successfully applied|Successfully validated|Started DromaraApplication" "${LOG}" | sed -E 's/\x1b\[[0-9;]*m//g' | tail -5
echo "--- 嵌套 jar 里本票的 class ---"
unzip -l "${JAR}" 2>/dev/null | grep -E "ruoyi-lqg|sample/export" | head -5
unzip -p "${JAR}" BOOT-INF/lib/ruoyi-lqg-5.5.3.jar > /tmp/lqg-nested.jar 2>/dev/null \
  && unzip -l /tmp/lqg-nested.jar | grep -cE 'org/dromara/lqg/sample/export/' | sed 's/^/嵌套 jar 里 sample\/export\/ 的条目数: /'
