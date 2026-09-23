#!/usr/bin/env bash
# SYS-PROD-001 · 生产部署（本地跑；产物送上机 + 在服务器上原生构建镜像 + 起容器 + 签证书 + 自检）
#
#   cd code/deploy/prod
#   cp .env.example .env && chmod 600 .env      # 填真域名 / 主机 / AK；.env 不进仓库
#
#   bash deploy.sh all            # 全部：产物 → 上传 → 起容器 → 宿主准备 → 证书 → OSS 初始化 → 自检
#   bash deploy.sh preflight      # 只做前置自检（.env 完整性 / 脚本语法 / compose 校验 / 端口纪律）
#   bash deploy.sh artifacts      # 只在本机构建产物（后端 jar + plus-ui dist production）
#   bash deploy.sh upload         # 只上传（rsync）
#   bash deploy.sh up             # 只在服务器上：建镜像 + 起容器 + 等健康
#   bash deploy.sh cert           # 只在服务器上：acme.sh 签证书 + 续期 cron（含 reload nginx）
#   bash deploy.sh oss-init       # 只在服务器上：执行 oss-init.sql（写 sys_oss_config 一行）
#   bash deploy.sh cron           # 只在服务器上：装 healthcheck 的每 5 分钟 cron
#   bash deploy.sh verify         # 从本机打 https://<域名>/lqg/sys/ping 与工作台首页 / 证书天数
#   bash deploy.sh rollback <sha> # 回滚到指定提交号的镜像（镜像 tag = 提交号）
#   bash deploy.sh status         # 生产现状（只读：容器 / 磁盘 / 证书 / 端口）
#   bash deploy.sh down           # docker compose down（**保留数据卷**）
#
# ── 三条硬规矩（与测试环境同源，都是踩出来的）────────────────────────────────────
# 1. **镜像在服务器上原生构建**：本机 docker 是 linux/aarch64、生产机是 x86_64，
#    本地镜像上去就是 `exec format error`。所以只把 **jar**（与架构无关）rsync 上去，
#    在服务器 `docker compose build`。多阶段那份 Dockerfile 留给 amd64 CI。
# 2. **长操作分离执行 + 短命令轮询**：docker build / 首次 Flyway / 证书签发都是长操作，
#    本机 SSH 长连接会被透明代理掐断。远端脚本一律 `nohup` 跑、输出落 log、
#    用 `.done` 文件带退出码，本机只做短命令 tail。
# 3. **生产禁飞区**：本脚本只写 ${LQG_DATA_DIR}（默认 /opt/lqg）、只碰 lqg-prod-* 容器，
#    绝不 down/kill 别人的东西，也**绝不**去动测试机（118.178.109.11 上是 test 环境）。
#
# 依赖：本机 mvn / pnpm / rsync / ssh / jq / git；生产机 docker + compose + openssl + curl。
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "${HERE}/../../.." && pwd)"
ENV_FILE="${HERE}/.env"
ENV_EXAMPLE="${HERE}/.env.example"

say()  { printf '\033[36m[deploy]\033[0m %s\n' "$*"; }
warn() { printf '\033[33m[warn]\033[0m %s\n' "$*" >&2; }
die()  { printf '\033[31m[error]\033[0m %s\n' "$*" >&2; exit 1; }

# ── 0. 前置：.env / 域名口径 / 禁飞区 ───────────────────────────────────────────
ensure_env_file() {
  [ -f "${ENV_FILE}" ] || die "没有 ${ENV_FILE}，先：cp .env.example .env && chmod 600 .env（真口令见部署手册 §3）"
  chmod 600 "${ENV_FILE}"
}

load_env() {
  set -a
  # shellcheck disable=SC1090
  . "${ENV_FILE}"
  set +a
  : "${LQG_PROD_HOST:?LQG_PROD_HOST 未填（owner 待提供，见 doc/handover/d8-blockers.json 的 B2）}"
  : "${LQG_PROD_SSH:?LQG_PROD_SSH 未填（形如 root@<生产主机>）}"
  : "${LQG_PROD_DOMAIN:?LQG_PROD_DOMAIN 未填（已 ICP 备案的生产域名）}"
  : "${LQG_DATA_DIR:=/opt/lqg}"
  : "${LQG_PROD_SSH_PORT:=22}"
  : "${LQG_DB_NAME:=lqg}"

  # ★ 禁飞区：生产库名不许含 test（reseed.sh 的护栏靠这个字符串）；
  #   生产域名不许是测试域名；生产主机不许是测试机 IP（B2 的核心口径）。
  case "${LQG_DB_NAME}" in *test*) die "LQG_DB_NAME='${LQG_DB_NAME}' 含 test —— 生产库名不许含 test（reseed.sh 的护栏就是查这个）";; esac
  case "${LQG_PROD_DOMAIN}" in *tianda.studio*) die "LQG_PROD_DOMAIN='${LQG_PROD_DOMAIN}' 是测试域名 —— 生产要自己的已备案域名（B2）";; esac
  case "${LQG_PROD_HOST}" in 118.178.109.11) die "LQG_PROD_HOST 是测试机 IP（上面跑的是 test 环境，profile=test）—— 不许当生产用（B2）";; esac
  case "${LQG_ENCRYPT_PASSWORD:-}" in ""|change-me-encrypt) die "LQG_ENCRYPT_PASSWORD 还是占位值 —— 生产字段加密口令必须是真的（且 ≠ LqgTestAesKey#01）";; esac
  case "${LQG_ENCRYPT_PASSWORD}" in LqgTestAesKey#01) die "LQG_ENCRYPT_PASSWORD 是**测试口令** —— 生产必须换一个（ADR-0006）";; esac

  BUILD_COMMIT="$(git -C "${ROOT}" rev-parse --short=7 HEAD)"
  SSH=(ssh -o BatchMode=yes -o ConnectTimeout=15 -p "${LQG_PROD_SSH_PORT}" "${LQG_PROD_SSH}")
  RSYNC_RSH="ssh -o BatchMode=yes -p ${LQG_PROD_SSH_PORT}"
}

# 把 LQG_BUILD_COMMIT 写回 .env（compose 的镜像 tag / build arg 都读它）
sync_build_commit() {
  local cur
  cur="$(sed -n 's/^LQG_BUILD_COMMIT=//p' "${ENV_FILE}" | head -1)"
  if [ "${cur}" != "${BUILD_COMMIT}" ]; then
    sed -i.bak "s|^LQG_BUILD_COMMIT=.*|LQG_BUILD_COMMIT=${BUILD_COMMIT}|" "${ENV_FILE}" && rm -f "${ENV_FILE}.bak"
    say "LQG_BUILD_COMMIT ← ${BUILD_COMMIT}（写入 .env）"
  fi
  export LQG_BUILD_COMMIT="${BUILD_COMMIT}"
}

# ── 1. 前置自检（不联网也能跑）───────────────────────────────────────────────────
phase_preflight() {
  say "① .env 硬口径"
  ensure_env_file
  load_env
  say "  ✓ 域名=${LQG_PROD_DOMAIN} 主机=${LQG_PROD_HOST} 数据盘=${LQG_DATA_DIR}"
  say "  ✓ 库名=${LQG_DB_NAME}（不含 test）"
  case "${LQG_OSS_BUCKET:-}" in "") warn "LQG_OSS_BUCKET 空 —— oss-init 阶段会跳过（OSS 断言 blocked，B1）";; *) say "  ✓ OSS 桶=${LQG_OSS_BUCKET} 前缀=${LQG_OSS_PREFIX:-lqg/}";; esac
  case "${LQG_ALERT_WEBHOOK:-}" in "") warn "LQG_ALERT_WEBHOOK 空 —— healthcheck 告警会退化成打日志";; *) say "  ✓ 告警 webhook 已配";; esac

  say "② compose 校验（用真实 .env 展开；任一必填缺失会在这里红）"
  ( cd "${HERE}" && docker compose config -q ) || die "compose 校验失败"
  say "  ✓ docker-compose.yml 可展开"

  say "③ 端口纪律：只有 nginx 映射宿主端口"
  local bad
  bad="$(grep -nE '^[[:space:]]*-[[:space:]]*"?(0\.0\.0\.0:)?(5432|6379|3000|8080):' "${HERE}/docker-compose.yml" || true)"
  [ -z "${bad}" ] || die "docker-compose.yml 里有宿主端口映射命中接纳黑名单：\n${bad}"
  # 找 `    ports:`（**行首恰好 4 个空格** —— 缩进更深的是 postgres command 数组里的端口值，
  # 不是映射）。再用 awk 往上回溯最近一个「恰好 2 空格 + 冒号」的服务名，得到「谁在映射端口」。
  # ★ 别用 `grep -B` 按固定行数回溯：服务段落行数不等，回溯窗口一错就误判（实测踩过）。
  local ports_in
  ports_in="$(awk '
      /^  [a-zA-Z0-9_-]+:[[:space:]]*$/ { svc = $1; sub(/:$/, "", svc) }
      /^    ports:[[:space:]]*$/        { print svc }
    ' "${HERE}/docker-compose.yml" | tr '\n' ' ')"
  [ "${ports_in}" = "nginx " ] || die "有 ports: 的服务是「${ports_in}」，期望只有 nginx（accept 2 第 1/2 段）"
  say "  ✓ ports: 只出现在 nginx 段（${ports_in}）"

  say "④ 仓库里没有生产密钥 / 没有 .env 被跟踪"
  ( cd "${ROOT}" && ! git grep -nE 'LTAI[0-9A-Za-z]{12,}|AKID[0-9A-Za-z]{12,}' -- . ':!doc/tickets' >/dev/null ) \
    || die "仓库里出现了 AK 形态的字符串（accept 2 第 6 段）"
  say "  ✓ 无 AK 形态字符串"

  say "⑤ 生产配置不出现三个危险开关"
  grep -nE 'mock-login|paid-enabled|provider:[[:space:]]*stub' \
    "${ROOT}/code/RuoYi-Vue-Plus/ruoyi-admin/src/main/resources/application-prod.yml" >/dev/null \
    && die "application-prod.yml 命中三个危险开关（accept 2 第 7 段）" || true
  say "  ✓ application-prod.yml 干净"

  say "⑥ 远端脚本语法"
  for f in "${HERE}"/remote/*.sh "${HERE}"/healthcheck.sh; do
    bash -n "${f}" || die "语法错误：${f}"
  done
  say "  ✓ 远端脚本 bash -n 通过"
  say "preflight ✅"
}

ssh_ok() {
  local hostname
  hostname="$("${SSH[@]}" 'hostname' 2>/dev/null)" || die "SSH 连不上 ${LQG_PROD_SSH}（端口 ${LQG_PROD_SSH_PORT}）"
  say "SSH ✅ ${LQG_PROD_SSH} ($hostname)"
}

# ── 2. 本地构建产物 ─────────────────────────────────────────────────────────────
phase_artifacts() {
  say "① 后端 jar（本地 mvn package；BUILD_COMMIT 由镜像阶段的 build-arg 注入，jar 里不落）"
  ( cd "${ROOT}/code/RuoYi-Vue-Plus" \
      && mvn -q -s "${ROOT}/.mvn-settings.xml" -Dmaven.repo.local="${ROOT}/.m2repo" \
           -pl ruoyi-admin -am -DskipTests package ) \
    || die "mvn package 失败"
  [ -f "${ROOT}/code/RuoYi-Vue-Plus/ruoyi-admin/target/ruoyi-admin.jar" ] || die "没有产出 ruoyi-admin.jar"
  say "  ✓ ruoyi-admin.jar"

  say "② 网页工作台 dist（**production 模式**：VITE_APP_ENCRYPT=true + VITE_APP_BASE_API=/prod-api）"
  say "   ★ 生产要用 build:prod（.env.production）；test 的 build:test 会把接口加密关掉，"
  say "     而后端 prod 的 api-decrypt.enabled 是 true → 两者不一致会让工作台登录一律失败。"
  ( cd "${ROOT}/code/plus-ui" && pnpm build:prod ) || die "pnpm build:prod 失败"
  [ -f "${ROOT}/code/plus-ui/dist/index.html" ] || die "没有产出 plus-ui/dist/index.html"
  say "  ✓ plus-ui dist ($(du -sh "${ROOT}/code/plus-ui/dist" | cut -f1))"
}

# ── 3. 上传 ─────────────────────────────────────────────────────────────────────
phase_upload() {
  local d="${LQG_DATA_DIR}"
  local R=(-e "${RSYNC_RSH}")
  say "上传到 ${LQG_PROD_SSH}:${d}"
  "${SSH[@]}" "install -d -m 755 '${d}' '${d}/logs' '${d}/logs/backend' '${d}/logs/nginx' '${d}/tmp' '${d}/cert-origin' '${d}/acme-webroot' '${d}/certs' '${d}/remote' '${d}/oss'"
  "${SSH[@]}" "chmod 1777 '${d}/tmp'"

  # 后端 jar（构建上下文 = ruoyi-admin：Dockerfile + target/ruoyi-admin.jar）
  say "  ↑ backend 构建上下文（Dockerfile + jar）"
  "${SSH[@]}" "install -d -m 755 '${d}/ruoyi-admin/target'"
  rsync "${R[@]}" -a --delete \
    "${ROOT}/code/RuoYi-Vue-Plus/ruoyi-admin/Dockerfile" \
    "${LQG_PROD_SSH}:${d}/ruoyi-admin/"
  rsync "${R[@]}" -a \
    "${ROOT}/code/RuoYi-Vue-Plus/ruoyi-admin/target/ruoyi-admin.jar" \
    "${LQG_PROD_SSH}:${d}/ruoyi-admin/target/"

  # gotenberg 构建上下文（Dockerfile + 字体）
  say "  ↑ gotenberg 构建上下文（Dockerfile + 中文字体）"
  rsync "${R[@]}" -a --delete --exclude 'fonts/*.otf' --exclude 'fonts/*.ttf' \
    "${ROOT}/code/deploy/common/gotenberg/" "${LQG_PROD_SSH}:${d}/gotenberg/"
  rsync "${R[@]}" -a "${ROOT}/code/deploy/common/gotenberg/fonts/" "${LQG_PROD_SSH}:${d}/gotenberg/fonts/"

  # compose + nginx 配置 + 远端脚本 + oss-init.sql + healthcheck
  say "  ↑ compose / nginx / remote / oss-init.sql / healthcheck"
  rsync "${R[@]}" -a "${HERE}/docker-compose.yml" "${LQG_PROD_SSH}:${d}/"
  "${SSH[@]}" "install -d -m 755 '${d}/nginx'"
  rsync "${R[@]}" -a "${HERE}/nginx/" "${LQG_PROD_SSH}:${d}/nginx/"
  rsync "${R[@]}" -a "${HERE}/remote/" "${LQG_PROD_SSH}:${d}/remote/"
  rsync "${R[@]}" -a "${HERE}/healthcheck.sh" "${LQG_PROD_SSH}:${d}/"
  rsync "${R[@]}" -a "${HERE}/oss-init.sql" "${LQG_PROD_SSH}:${d}/oss/"

  # 工作台静态产物
  say "  ↑ plus-ui dist → ${d}/www"
  rsync "${R[@]}" -a --delete "${ROOT}/code/plus-ui/dist/" "${LQG_PROD_SSH}:${d}/www/"

  # ★ .env 单独送、单独 chmod：里面有全部生产口令
  say "  ↑ .env（600；scp 后立刻 chmod）"
  scp -q -P "${LQG_PROD_SSH_PORT}" -o BatchMode=yes "${ENV_FILE}" "${LQG_PROD_SSH}:${d}/.env"
  "${SSH[@]}" "chmod 600 '${d}/.env'"

  # nginx 的 __LQG_DOMAIN__ 渲染（在服务器上做，域名只在 .env 里）
  say "  ↑ 渲染 nginx 配置的域名占位"
  "${SSH[@]}" "cd '${d}' && sed -i \"s/__LQG_DOMAIN__/${LQG_PROD_DOMAIN}/g\" nginx/workspace.conf && grep -c '${LQG_PROD_DOMAIN}' nginx/workspace.conf"
  say "upload ✅"
}

# 渲染本地那份 nginx 配置（给 preflight 看；真渲染在服务器上做）
phase_render_nginx() {
  local out="${HERE}/.rendered-workspace.conf"
  sed "s/__LQG_DOMAIN__/${LQG_PROD_DOMAIN}/g" "${HERE}/nginx/workspace.conf" > "${out}"
  say "渲染后的 nginx 配置：${out}（已 gitignore 之外 → 用后即删；真文件在服务器上）"
  rm -f "${out}"
}

# ── 4. 远端长操作三件套 ─────────────────────────────────────────────────────────
run_remote_phase() {
  local name="$1" script="$2" timeout="${3:-1800}"
  local LOGDIR="${LQG_DATA_DIR}/logs"
  say "▶ 远端分离执行 ${name}（日志 ${LOGDIR}/${name}.log）"
  "${SSH[@]}" "install -d '${LOGDIR}'; rm -f '${LOGDIR}/${name}.done'; chmod +x '${LQG_DATA_DIR}/remote/${script}'"
  "${SSH[@]}" "cd '${LQG_DATA_DIR}' && nohup bash -c 'bash ${LQG_DATA_DIR}/remote/${script}; echo \$? > ${LOGDIR}/${name}.done' > ${LOGDIR}/${name}.log 2>&1 & echo started"
  local waited=0
  while [ "${waited}" -lt "${timeout}" ]; do
    sleep 10; waited=$((waited + 10))
    local done_rc
    done_rc="$("${SSH[@]}" "cat ${LOGDIR}/${name}.done 2>/dev/null" || true)"
    if [ -n "${done_rc}" ]; then
      "${SSH[@]}" "tail -25 ${LOGDIR}/${name}.log" || true
      if [ "${done_rc}" = "0" ]; then say "✅ ${name} 完成（${waited}s）"; return 0; fi
      die "${name} 失败（远端退出码 ${done_rc}）——完整日志：ssh ${LQG_PROD_SSH} 'tail -100 ${LOGDIR}/${name}.log'"
    fi
    printf '  … %ss：%s\n' "${waited}" "$("${SSH[@]}" "tail -1 ${LOGDIR}/${name}.log 2>/dev/null" || true)"
  done
  die "${name} 超时（${timeout}s）——ssh ${LQG_PROD_SSH} 'tail -100 ${LOGDIR}/${name}.log'"
}

phase_up()    { run_remote_phase up   01-up.sh 2400; }
phase_cert()  {
  [ -n "${LQG_CERT_EMAIL:-}" ] || die "LQG_CERT_EMAIL 未填 —— acme.sh 注册要用邮箱"
  run_remote_phase cert 03-cert.sh 900
}
phase_oss()   { run_remote_phase oss  04-oss-init.sh 300; }
phase_cron()  { run_remote_phase cron 05-cron.sh 120; }

# ── 5. 从本机验收 ───────────────────────────────────────────────────────────────
phase_verify() {
  local base="https://${LQG_PROD_DOMAIN}"
  say "① 工作台首页（期望 200）"
  curl -s -o /dev/null -w '  HTTP %{http_code}\n' "${base}/"
  say "② http 必须跳 https（期望 30x + Location: https://）"
  curl -sI "http://${LQG_PROD_DOMAIN}/" | grep -iE '^(HTTP/|location:)' | sed 's/^/  /' || true
  say "③ /lqg/sys/ping 匿名访问（期望 401：接口不 @SaIgnore，这也是 accept 1 第 3 段的口径）"
  curl -s -o /dev/null -w '  HTTP %{http_code}\n' "${base}/lqg/sys/ping"
  say "④ 证书（期望剩余天数 > 20；accept 1 第 5 段）"
  local END days
  END="$(echo | openssl s_client -servername "${LQG_PROD_DOMAIN}" -connect "${LQG_PROD_DOMAIN}:443" 2>/dev/null \
        | openssl x509 -noout -enddate | cut -d= -f2)"
  days="$(python3 -c "import sys,email.utils,time;print(round((email.utils.mktime_tz(email.utils.parsedate_tz(sys.argv[1]))-time.time())/86400,1))" "${END}")"
  printf '  notAfter=%s  剩余 %s 天\n' "${END}" "${days}"
  python3 -c "import sys; sys.exit(0 if float(sys.argv[1])>20 else 1)" "${days}" \
    && say "  ✓ 剩余天数 > 20" || warn "  ✗ 剩余天数 ≤ 20 —— accept 1 第 5 段会红，先修证书再验"
  say "⑤ 公网不可达：5432 / 6379 / 3000 / 8080（accept 2 第 1 段）"
  local p
  for p in 5432 6379 3000 8080; do
    if nc -z -w 3 "${LQG_PROD_DOMAIN}" "${p}" 2>/dev/null; then warn "  ✗ ${p} 公网可达 —— 不该"; else say "  ✓ ${p} 不可达"; fi
  done
  say "⑥ 容器健康"
  "${SSH[@]}" "cd '${LQG_DATA_DIR}' && docker compose ps --format '{{.Name}}\t{{.Status}}'" || true
  say "verify 完成 —— 需要登录态的断言（ping 的 profile/encrypt/mockLogin、mock 登录被拒、"
  say "OSS 匿名 403）跑 doc/verify/verify.prod.env + 线上冒烟剧本，见部署手册 §11。"
}

phase_rollback() {
  local sha="${1:-}"
  [ -n "${sha}" ] || die "用法：bash deploy.sh rollback <7 位提交号>（docker images | grep lqg-backend 看有哪些）"
  say "回滚到 lqg-backend:${sha}"
  "${SSH[@]}" "docker image inspect 'lqg-backend:${sha}' >/dev/null 2>&1" \
    || die "服务器上没有 lqg-backend:${sha} —— 先用 git checkout 到那个提交重跑 deploy.sh up 重建，或从别处导入"
  sed -i.bak "s|^LQG_BUILD_COMMIT=.*|LQG_BUILD_COMMIT=${sha}|" "${ENV_FILE}" && rm -f "${ENV_FILE}.bak"
  LQG_BUILD_COMMIT="${sha}"
  scp -q -P "${LQG_PROD_SSH_PORT}" -o BatchMode=yes "${ENV_FILE}" "${LQG_PROD_SSH}:${LQG_DATA_DIR}/.env"
  "${SSH[@]}" "chmod 600 '${LQG_DATA_DIR}/.env'"
  "${SSH[@]}" "cd '${LQG_DATA_DIR}' && docker compose up -d backend && sleep 5 && docker compose ps backend"
  say "回滚完成（DB schema 不回滚 —— Flyway 是单向的，见部署手册 §6 的注意）"
}

phase_status() {
  say "容器"
  "${SSH[@]}" "cd '${LQG_DATA_DIR}' && docker compose ps --format 'table {{.Name}}\t{{.Image}}\t{{.Status}}'" || true
  say "磁盘"
  "${SSH[@]}" "df -h '${LQG_DATA_DIR}' | tail -2" || true
  say "证书"
  "${SSH[@]}" "openssl x509 -in '${LQG_DATA_DIR}/certs/${LQG_PROD_DOMAIN}/fullchain.pem' -noout -subject -enddate 2>/dev/null || echo '（还没有证书）'" || true
  say "监听端口（期望除 22 / 80 / 443 外没有本项目的）"
  "${SSH[@]}" "ss -lntp | grep -E ':(80|443|5432|6379|3000|8080)\b' || echo '（无）'" || true
}

phase_down() {
  warn "down 只停容器，**保留数据卷与 ${LQG_DATA_DIR} 下的一切**"
  "${SSH[@]}" "cd '${LQG_DATA_DIR}' && docker compose down"
}

# ── main ────────────────────────────────────────────────────────────────────────
cmd="${1:-all}"
case "${cmd}" in
  all)
    phase_preflight
    ssh_ok
    phase_artifacts
    phase_upload
    phase_up
    phase_cert
    phase_oss
    phase_cron
    phase_verify
    ;;
  preflight) phase_preflight ;;
  artifacts) ensure_env_file; load_env; sync_build_commit; phase_artifacts ;;
  upload)    ensure_env_file; load_env; sync_build_commit; ssh_ok; phase_upload ;;
  up)        ensure_env_file; load_env; sync_build_commit; ssh_ok; phase_up ;;
  cert)      ensure_env_file; load_env; ssh_ok; phase_cert ;;
  oss-init)  ensure_env_file; load_env; ssh_ok; phase_oss ;;
  cron|healthcheck) ensure_env_file; load_env; ssh_ok; phase_cron ;;
  verify)    ensure_env_file; load_env; phase_verify ;;
  status)    ensure_env_file; load_env; ssh_ok; phase_status ;;
  rollback)  ensure_env_file; load_env; ssh_ok; shift || true; phase_rollback "${1:-}" ;;
  down)      ensure_env_file; load_env; ssh_ok; phase_down ;;
  -h|--help|help) sed -n '2,30p' "${BASH_SOURCE[0]}" ;;
  *) die "未知子命令：${cmd}（bash deploy.sh help 看用法）" ;;
esac
