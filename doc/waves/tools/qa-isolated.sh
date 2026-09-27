#!/usr/bin/env bash
# 隔离副本验收（2026-09-27）—— 把某个 revision 检出到**工作区之外**的临时目录，在那里起环境跑验收。
#
#   bash doc/waves/tools/qa-isolated.sh up   [--ref <rev>] [--dir <path>]
#                                            [--backend-port 8101] [--web-port 8102] [--mp-port 9210]
#   bash doc/waves/tools/qa-isolated.sh down [--dir <path>] [--backend-port 8101] [--web-port 8102] [--mp-port 9210]
#   bash doc/waves/tools/qa-isolated.sh status [--dir <path>]
#
# 为什么有它（独立验收报告 §5「脚本写死工作区路径」+ 交接报告 §10.3）：
#   模式 B 的验收原本在工作区里跑 —— 会往工作区写 .tmp / 生成物（pages.json、.eslintrc-auto-import.json）、
#   变异脚本还会在工作区里 git checkout。谁在副本里重放都不该碰主工作区。这个脚本用 `git worktree`
#   把 revision 检出到 ${TMPDIR}，重资产（node_modules / .m2repo / .pnpm-store）**软链**到主工作区避免重复下载，
#   环境与日志全部落在副本里。
#
# ★ 隔离边界（必须知道，别当成全隔离）：
#   · **代码/构建/日志/生成物**：完全隔离在副本目录里，主工作区零写入。
#   · **数据库 / MinIO / Gotenberg**：仍然共用本机 dev 容器（5433 / 9002 / 3010）—— 所以
#     **同一时刻只许一个验收在跑**，且副本里的 reseed 会重置这套共用库。
#   · git 层面只在主仓 .git/worktrees 下登记一条 worktree（down 时移除），不改任何分支或历史。
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
cd "${ROOT}"

CMD="${1:-}"; shift || true
REF="HEAD"; DIR=""; BPORT=8101; WPORT=8102; MPORT=9210
while [ $# -gt 0 ]; do
  case "$1" in
    --ref) REF="$2"; shift 2 ;;
    --dir) DIR="$2"; shift 2 ;;
    --backend-port) BPORT="$2"; shift 2 ;;
    --web-port) WPORT="$2"; shift 2 ;;
    --mp-port) MPORT="$2"; shift 2 ;;
    *) echo "[error] 未知参数 $1" >&2; exit 2 ;;
  esac
done

# 记住上一次 up 用的目录，down/status 不必再传 --dir
# ★ ${TMPDIR} 末尾自带斜杠（macOS 的 /var/folders/.../T/）—— 直接拼会得到 `T//lqg-verify-...`，
#   而 `git rev-parse --show-toplevel` 返回单斜杠路径 → mutation-assert.sh 的「是不是仓库根」字符串比较失败
#   （实测踩过：副本里跑 SYS-ACCEPT-001 直接报「ROOT 推导错了…不是 git 仓库根」）。先归一化。
#   ★ 还要解析符号链接：macOS 上 TMPDIR=/var/folders/... 而 /var → /private/var，
#     git 会返回规范化路径，两边拼写不一致会让「是不是仓库根」的比较失败。用 pwd -P 落到真实路径。
STAGE_BASE="$( cd "${TMPDIR:-/tmp}" && pwd -P )"
STAMP="${STAGE_BASE}/lqg-verify-last-dir"
if [ -z "${DIR}" ] && [ -f "${STAMP}" ]; then DIR="$(cat "${STAMP}")"; fi

case "${CMD}" in
  up)
    if [ -z "${DIR}" ]; then
      DIR="${STAGE_BASE}/lqg-verify-$(date +%Y%m%d-%H%M%S)"
    fi
    [ -e "${DIR}" ] && { echo "[error] ${DIR} 已存在，先 down 或换 --dir" >&2; exit 2; }

    echo "── 检出 ${REF} 到隔离副本 ${DIR}"
    git worktree add --detach "${DIR}" "${REF}" >/dev/null || { echo "[error] git worktree add 失败" >&2; exit 2; }

    echo "── 软链重资产（不重复下载）"
    for p in code/miniapp/node_modules code/plus-ui/node_modules .m2repo .pnpm-store; do
      if [ -e "${ROOT}/${p}" ] && [ ! -e "${DIR}/${p}" ]; then
        mkdir -p "$(dirname "${DIR}/${p}")"
        ln -s "${ROOT}/${p}" "${DIR}/${p}"
        echo "   → ${p}"
      fi
    done

    echo "── 拷必需的非版本化配置"
    # ★ code/deploy/*/.env 是 gitignored 的**运行时必须文件**：qa-up.sh 会 `set -a; . code/deploy/dev/.env`
    #   取库 / Redis / MinIO 的口令与端口。第一次做这个工具时漏了它 → 副本里后端连 Redis 报 WRONGPASS 起不来。
    for f in .mvn-settings.xml doc/verify/verify.env doc/verify/verify.test.env \
             code/deploy/dev/.env code/deploy/test/.env code/deploy/prod/.env; do
      if [ -e "${ROOT}/${f}" ]; then
        mkdir -p "$(dirname "${DIR}/${f}")"
        cp "${ROOT}/${f}" "${DIR}/${f}"
        chmod 600 "${DIR}/${f}" 2>/dev/null || true
        echo "   → ${f}"
      elif [ "${f%/.env}" != "${f}" ]; then
        echo "   ! 缺 ${f}（本机没有；dev 环境起不来的话按 doc/verify/README.md 建一个）"
      fi
    done

    echo "── 在副本里起环境（后端 ${BPORT} / 工作台 ${WPORT} / H5 ${MPORT}）"
    ( cd "${DIR}" && bash doc/waves/tools/qa-up.sh --backend-port "${BPORT}" --web-port "${WPORT}" --mp-port "${MPORT}" ) \
      || { echo "[error] 副本里 qa-up 失败，日志在 ${DIR}/.tmp/" >&2; exit 2; }

    printf '%s\n' "${DIR}" > "${STAMP}"
    echo
    echo "════ 隔离副本就绪 ════"
    echo "  目录      ${DIR}"
    echo "  验收 env  export LQG_VERIFY_ENV_FILE=\"${DIR}/.tmp/qa-env/${BPORT}/verify.env\""
    echo "  收工      bash doc/waves/tools/qa-isolated.sh down --dir ${DIR} --backend-port ${BPORT} --web-port ${WPORT} --mp-port ${MPORT}"
    echo
    echo "  主工作区是否被写过（应为空）："
    git -C "${ROOT}" status --porcelain | sed 's/^/    /' || true
    echo "  （空 = 隔离成立）"
    echo
    echo "  ★ 共用资源提醒：库/桶/Gotenberg 仍是本机 dev 那套 → 同一时刻只跑一个验收"
    ;;
  down)
    [ -n "${DIR}" ] && [ -d "${DIR}" ] || { echo "[error] 找不到副本目录（--dir 或先 up）" >&2; exit 2; }
    echo "── 停副本里的环境"
    ( cd "${DIR}" && bash doc/waves/tools/qa-up.sh --down --backend-port "${BPORT}" --web-port "${WPORT}" --mp-port "${MPORT}" ) || true
    echo "── 移除 worktree"
    git -C "${ROOT}" worktree remove --force "${DIR}" || { echo "[warn] worktree remove 失败，手动删 ${DIR}" >&2; }
    rm -f "${STAMP}"
    echo "── 主工作区状态（应为空）"; git -C "${ROOT}" status --porcelain | sed 's/^/    /'; echo "  （空 = 干净）"
    ;;
  status)
    echo "── worktree 列表"; git -C "${ROOT}" worktree list | sed 's/^/  /'
    if [ -n "${DIR}" ] && [ -f "${DIR}/.tmp/qa-env/${BPORT}/verify.env" ]; then
      echo "── 副本环境"; echo "  ${DIR}"
      ( cd "${DIR}" && bash doc/waves/tools/qa-up.sh --status --backend-port "${BPORT}" ) || true
    fi
    ;;
  *)
    sed -n '2,12p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'
    exit 2
    ;;
esac
