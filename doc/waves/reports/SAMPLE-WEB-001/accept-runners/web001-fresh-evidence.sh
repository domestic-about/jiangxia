#!/usr/bin/env bash
# SAMPLE-WEB-001 · 反 stale 等价证据（`--fresh-module ruoyi-lqg` 在本沙箱恒 exit 2：`ps` 被禁）
#
# 覆盖那道守卫的两个半边：
#   ① 源码不得比 jar 新；② 后端进程持有该 jar，且启动晚于 jar。
set -uo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/../../../../.." || exit 2
ROOT="$(pwd)"
JAR="${ROOT}/code/RuoYi-Vue-Plus/ruoyi-admin/target/ruoyi-admin.jar"

echo "jar mtime                  : $(stat -f '%Sm' -t '%Y-%m-%d %H:%M:%S' "${JAR}")"
echo "jar epoch                  : $(stat -f %m "${JAR}")"
echo "lqg src newer than jar     : [$(find "${ROOT}/code/RuoYi-Vue-Plus/ruoyi-modules/ruoyi-lqg/src" -type f -newer "${JAR}" | head -3)]"
echo "admin src newer than jar   : [$(find "${ROOT}/code/RuoYi-Vue-Plus/ruoyi-admin/src" -type f -newer "${JAR}" | head -3)]"
PID="$(lsof -ti tcp:8081 -sTCP:LISTEN | head -1)"
echo "8081 PID                   : ${PID}"
echo "该 PID 打开 jar 的次数      : $(lsof -p "${PID}" 2>/dev/null | grep -c 'ruoyi-admin.jar')"
echo "进程启动（日志行）          : $(grep -a 'Started DromaraApplication' "${ROOT}/.tmp/web001-backend.log" | tail -1 | sed 's/\x1b\[[0-9;]*m//g' | cut -c1-90)"
echo "日志 mtime                 : $(stat -f '%Sm' -t '%Y-%m-%d %H:%M:%S' "${ROOT}/.tmp/web001-backend.log")"
echo "嵌套 jar 内的新类           :"
unzip -p "${JAR}" BOOT-INF/lib/ruoyi-lqg-5.5.3.jar > /tmp/web001-lqg.jar 2>/dev/null \
  && unzip -l /tmp/web001-lqg.jar | grep -E 'sample/query|SampleSubmitterProfile' | sed 's/^/    /'
