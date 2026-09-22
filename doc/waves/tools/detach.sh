#!/usr/bin/env bash
# 分离启动器（macOS 没有 setsid，这个补上）—— 起一个**脱离调用者进程组**的长进程。
#
#   bash doc/waves/tools/detach.sh <日志文件> <命令> [参数...]
#
# 为什么要它：
#   · macOS 自带没有 `setsid`（`setsid: command not found`），而 `nohup cmd &` 起的进程
#     仍留在调用者的进程组里 —— 调用方那层 shell 被收掉时，长进程会跟着吃 SIGTERM
#     （实测 exit 143），还常常占着端口不放（D1 起就踩过）。
#   · 做法：python fork 出子进程 → 子进程 `os.setsid()` 开新会话 → dup2 重定向到日志 →
#     execvp。父进程立刻打印**子进程真实 PID** 就退出，所以拿到的一定是能 kill 的那个 PID。
#
# 收尾纪律：关进程只按 PID（`lsof -ti tcp:<端口>` 或本脚本打印的 PID）。
#           严禁 `pkill -f 'ruoyi-admin.jar'` —— 8080 上跑着 Kevin 的本机服务（issue #46）。
set -euo pipefail
[ $# -ge 2 ] || { echo "[error] 用法：detach.sh <日志文件> <命令> [参数...]" >&2; exit 2; }
LOG="$1"; shift
mkdir -p "$(dirname "${LOG}")"
exec python3 - "${LOG}" "$@" <<'PY'
import os, sys
log, argv = sys.argv[1], sys.argv[2:]
pid = os.fork()
if pid == 0:
    os.setsid()                      # 新会话：脱离调用者进程组，SIGTERM 不会再顺藤摸瓜
    fd = os.open(log, os.O_WRONLY | os.O_CREAT | os.O_APPEND, 0o644)
    os.dup2(fd, 1); os.dup2(fd, 2)
    os.dup2(os.open(os.devnull, os.O_RDONLY), 0)
    try:
        os.execvp(argv[0], argv)
    except Exception as e:           # 起不来要留痕，别静默
        os.write(2, f"[detach] exec {argv[0]} 失败: {e}\n".encode())
        os._exit(127)
print(pid)                           # 真实子进程 PID —— 收尾就 kill 它
PY
