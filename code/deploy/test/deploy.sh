#!/usr/bin/env bash
# SYS-STAGING-001 · 测试环境一键部署（本地跑；产物送上机 + 在服务器上原生构建镜像 + 起容器 + 灌 seed）
#
#   cd code/deploy/test
#   bash deploy.sh all          # 日常部署：产物 → 上传 → 起容器（Flyway 迁移）→ 宿主 nginx/证书 → 自检
#                               #   ★ 2026-09-30 起**不再 reseed、不再端口取证**（见下方 main 里的说明）
#   bash deploy.sh bootstrap    # 新机器 / 要把测试数据恢复成初始样子时：all + reseed + 端口取证
#   bash deploy.sh artifacts    # 只在本机构建产物（后端 jar + plus-ui dist）
#   bash deploy.sh upload       # 只上传（rsync）
#   bash deploy.sh up           # 只在服务器上：建镜像 + 起容器 + 等健康（分离执行）
#   bash deploy.sh nginx        # 只在服务器上：宿主宝塔 nginx 站点 + Let's Encrypt 证书 + 反代
#   bash deploy.sh prove-ports  # accept[2] 等价取证：发夹探针+安全组+ss -lntp（本机 nc 不可用）
#   bash deploy.sh reseed       # 只在服务器上：把 doc/verify/seed 灌进 lqg_test
#                               #   ★ 会 TRUNCATE 全部业务表、删掉微信绑定的用户 —— 甲方在测试站录的数据全没
#   bash deploy.sh miniapp      # 小程序体验版：本机构建 → 同步 → 在服务器上传（固定 IP）
#                               #   ★ 不在 all 里：它依赖微信侧 IP 白名单（外部前提），失败不该带崩部署
#   bash deploy.sh verify       # 从本机打 https://<域名>/lqg/sys/ping 与工作台首页
#   bash deploy.sh status       # 测试机现状（只读）
#   bash deploy.sh down         # docker compose down（保留数据卷；不会碰别人的容器）
#
# ── 三条硬规矩（都是踩出来的，别绕）──────────────────────────────────────────
# 1. **镜像在服务器上原生构建**：本机 docker 是 linux/aarch64、测试机是 x86_64，
#    本地镜像上去就是 `exec format error`。所以这里只把 **jar**（与架构无关）rsync 上去，
#    在服务器 `docker compose build`（ops §4.5 第 1 条）。多阶段那份 Dockerfile 保留给 amd64 CI。
# 2. **长操作分离执行 + 短命令轮询**：docker build / 首次 Flyway / 证书签发都是长操作，
#    本机 SSH 长连接会被透明代理掐断（ops §6.4）。所以远端脚本一律 `nohup` 跑、输出落 log、
#    用 `.done` 文件带退出码，本机只做短命令 tail。
# 3. **只碰自己的东西**：这台机器上还有 guzi 与 tianda 两个别的项目（容器、宝塔站点、证书目录、
#    安全组）。本脚本只写 /opt/lqg-test/、自己的域名 vhost 与自己的证书目录，绝不 down/kill 别人的进程。
#
# 依赖：本机 mvn / pnpm / rsync / ssh / jq / git；测试机 docker + compose（已就位）。
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "${HERE}/../../.." && pwd)"
ENV_FILE="${HERE}/.env"
ENV_EXAMPLE="${HERE}/.env.example"
REMOTE_DIR_DEFAULT=/opt/lqg-test

say()  { printf '\033[36m[deploy]\033[0m %s\n' "$*"; }
warn() { printf '\033[33m[warn]\033[0m %s\n' "$*" >&2; }
die()  { printf '\033[31m[error]\033[0m %s\n' "$*" >&2; exit 1; }

# ── 0. 前置：.env / 账号硬闸 / 服务器可达 ────────────────────────────────────
ensure_env_file() {
  if [ ! -f "${ENV_FILE}" ]; then
    say "没有 ${ENV_FILE}，按 .env.example 生成一份（真口令用 openssl rand 现生成，只落本地与服务器）"
    cp "${ENV_EXAMPLE}" "${ENV_FILE}"
    # 把三个占位口令换成随机值（不含 $ # 等会被 compose 当元字符的字符）
    local db redis minio
    db="lqg_$(openssl rand -hex 8)"
    redis="lqg_$(openssl rand -hex 8)"
    minio="lqg_$(openssl rand -hex 8)"
    sed -i.bak -e "s|^LQG_DB_PASSWORD=.*|LQG_DB_PASSWORD=${db}|" \
               -e "s|^LQG_REDIS_PASSWORD=.*|LQG_REDIS_PASSWORD=${redis}|" \
               -e "s|^LQG_MINIO_PASSWORD=.*|LQG_MINIO_PASSWORD=${minio}|" "${ENV_FILE}"
    rm -f "${ENV_FILE}.bak"
    chmod 600 "${ENV_FILE}"
    say "  已生成（口令不打印；要看口令：ssh 上机 cat ${REMOTE_DIR_DEFAULT}/.env）"
  fi
  chmod 600 "${ENV_FILE}"
}

load_env() {
  set -a
  # shellcheck disable=SC1090
  . "${ENV_FILE}"
  set +a
  : "${LQG_TEST_HOST:?}" "${LQG_TEST_DOMAIN:?}" "${LQG_DEPLOY_DIR:=${REMOTE_DIR_DEFAULT}}"
  BUILD_COMMIT="$(git -C "${ROOT}" rev-parse --short=12 HEAD)"
  : "${LQG_API_PORT:=8082}" "${LQG_WEB_PORT:=8083}" "${LQG_DB_PORT:=15432}"
  SSH=(ssh -o BatchMode=yes -o ConnectTimeout=15 "root@${LQG_TEST_HOST}")
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

gate_account() {
  # 账号硬闸：这台机器/这个 profile 必须是 tianda（AccountId 1826406494972500）。
  # 本机 active profile 恰好就是它，但另一个项目是 dongjiaoshan 的 djs-prod —— 别裸跑。
  #
  # ★ 2026-09-28 加显式跳过开关（CI 用）：GitHub runner 上没有 tianda-admin profile，
  #   而这一步会取到空 AccountId 直接 STOP（首次跑 CI 就卡在这）。CI 的护栏不一样：
  #   目标机器来自 repo secret、而且只走 SSH、不碰任何云资源 —— 所以按调用方**显式**声明跳过是合理的。
  #   注意是显式：不设这个变量时行为与以前完全一致（没有 CLI 就告警放行，有 CLI 就必须对上账号）。
  if [ "${LQG_SKIP_ACCOUNT_GATE:-}" = "1" ]; then
    warn "按 LQG_SKIP_ACCOUNT_GATE=1 跳过账号硬闸（CI 环境：没有 tianda-admin profile，且本次只走 SSH、不动云资源）"
    return 0
  fi
  command -v aliyun >/dev/null 2>&1 || { warn "本机没有 aliyun CLI，跳过账号硬闸（本次部署不动云资源，只走 SSH）"; return 0; }
  local acct
  acct="$(no_proxy='*' NO_PROXY='*' aliyun sts GetCallerIdentity --profile tianda-admin 2>/dev/null | jq -r '.AccountId // empty')" || true
  [ "${acct}" = "1826406494972500" ] || die "账号硬闸不过：GetCallerIdentity 得到 '${acct}'，期望 1826406494972500。STOP。"
  say "账号硬闸 ✅ AccountId=1826406494972500（profile tianda-admin）"
}

ssh_ok() {
  local hostname
  hostname="$("${SSH[@]}" 'hostname' 2>/dev/null)" || die "SSH 连不上 root@${LQG_TEST_HOST}"
  say "SSH ✅ ${LQG_TEST_HOST} ($hostname)"
}

# ── 远端长操作三件套：上传脚本 → nohup 分离跑 → 短命令轮询 .done ────────────────
REMOTE_LOGDIR="${LQG_DEPLOY_DIR:-$REMOTE_DIR_DEFAULT}/logs"

run_remote_phase() {
  local name="$1" script="$2" timeout="${3:-1800}"
  say "▶ 远端分离执行 ${name}（日志 ${REMOTE_LOGDIR}/${name}.log）"
  "${SSH[@]}" "install -d '${REMOTE_LOGDIR}'; rm -f '${REMOTE_LOGDIR}/${name}.done'; chmod +x '${LQG_DEPLOY_DIR}/remote/${script}'"
  "${SSH[@]}" "nohup bash -c 'bash ${LQG_DEPLOY_DIR}/remote/${script}; echo \$? > ${REMOTE_LOGDIR}/${name}.done' > ${REMOTE_LOGDIR}/${name}.log 2>&1 & echo started"
  local waited=0
  while [ "${waited}" -lt "${timeout}" ]; do
    sleep 10; waited=$((waited + 10))
    local done_rc
    done_rc="$("${SSH[@]}" "cat ${REMOTE_LOGDIR}/${name}.done 2>/dev/null" || true)"
    if [ -n "${done_rc}" ]; then
      "${SSH[@]}" "tail -25 ${REMOTE_LOGDIR}/${name}.log" || true
      if [ "${done_rc}" = "0" ]; then
        say "✅ ${name} 完成（${waited}s）"
        return 0
      fi
      die "${name} 失败（远端退出码 ${done_rc}）——完整日志：ssh root@${LQG_TEST_HOST} 'tail -100 ${REMOTE_LOGDIR}/${name}.log'"
    fi
    # 短命令轮询：打印远端日志尾部，让长操作可见
    printf '  … %ss：%s\n' "${waited}" "$("${SSH[@]}" "tail -1 ${REMOTE_LOGDIR}/${name}.log 2>/dev/null" || true)"
  done
  die "${name} 超过 ${timeout}s 未完成"
}

# ── 各阶段 ──────────────────────────────────────────────────────────────────
phase_artifacts() {
  sync_build_commit
  say "① 后端 jar（本地 mvn package；BUILD_COMMIT 由镜像阶段的 build-arg 注入，jar 里不落）"
  # ★ 2026-09-28：maven settings 可覆盖。本机用仓库里的 .mvn-settings.xml（阿里云镜像，快）；
  #   CI 里那个镜像**缺件**（实测 spring-boot-starter-data-redis:3.5.10 / spring-data-redis:3.5.8 拉不到），
  #   而 GitHub runner 直连 Maven Central 又快又全 → 用 LQG_MVN_SETTINGS=none 显式声明「不要 -s」。
  #   不设这个变量时行为与以前完全一致。
  local mvn_settings=()
  case "${LQG_MVN_SETTINGS:-}" in
    none|"")  [ "${LQG_MVN_SETTINGS:-}" = "none" ] && warn "按 LQG_MVN_SETTINGS=none 使用 Maven 默认仓库（CI：阿里云镜像缺件）" ;;
    *)        mvn_settings=(-s "${LQG_MVN_SETTINGS}") ;;
  esac
  [ "${LQG_MVN_SETTINGS:-}" = "" ] && mvn_settings=(-s "${ROOT}/.mvn-settings.xml")
  ( cd "${ROOT}/code/RuoYi-Vue-Plus" && \
    mvn -q "${mvn_settings[@]}" -Dmaven.repo.local="${ROOT}/.m2repo" \
        -Dmaven.wagon.http.retryHandler.count=3 -Dmaven.wagon.httpconnectionManager.ttlSeconds=60 \
        -Duser.home="${ROOT}/.buildhome" -DskipTests -pl ruoyi-admin -am package ) \
    || die "mvn package 失败"
  [ -f "${ROOT}/code/RuoYi-Vue-Plus/ruoyi-admin/target/ruoyi-admin.jar" ] || die "没有产出 ruoyi-admin.jar"
  say "  ✓ ruoyi-admin.jar ($(du -h "${ROOT}/code/RuoYi-Vue-Plus/ruoyi-admin/target/ruoyi-admin.jar" | cut -f1))"

  say "② 网页工作台 dist（test 模式：VITE_APP_ENCRYPT=false + VITE_APP_BASE_API=/prod-api）"
  ( cd "${ROOT}/code/plus-ui" && rm -rf dist && pnpm build:test ) || die "pnpm build:test 失败"
  [ -f "${ROOT}/code/plus-ui/dist/index.html" ] || die "没有产出 plus-ui/dist/index.html"
  # 同一次 deploy.sh 进程里刚按 test 模式建好的 → phase_upload 不必再建一遍（原来每次部署前端建两遍，约 60s）
  WEB_DIST_FRESH=1
  say "  ✓ plus-ui dist ($(du -sh "${ROOT}/code/plus-ui/dist" | cut -f1))"
}

phase_upload() {
  local d="${LQG_DEPLOY_DIR}"
  say "③ rsync 产物与编排文件 → ${LQG_TEST_HOST}:${d}"
  "${SSH[@]}" "install -d '${d}/ruoyi-admin/target' '${d}/www' '${d}/nginx' '${d}/remote' '${d}/verify/seed' '${d}/bin' '${d}/logs/backend' '${d}/logs/nginx'"

  # 后端 jar → ./ruoyi-admin/target/ruoyi-admin.jar（compose 的构建上下文就是这个目录）
  # 注：本机 rsync 是 macOS 自带的 openrsync（2.6.9 兼容），别用 --info=progress2 / --copy-links 之类新选项
  rsync -az -e "ssh -o BatchMode=yes" \
    "${ROOT}/code/RuoYi-Vue-Plus/ruoyi-admin/target/ruoyi-admin.jar" \
    "root@${LQG_TEST_HOST}:${d}/ruoyi-admin/target/ruoyi-admin.jar"
  rsync -az -e "ssh -o BatchMode=yes" \
    "${ROOT}/code/RuoYi-Vue-Plus/ruoyi-admin/Dockerfile" \
    "root@${LQG_TEST_HOST}:${d}/ruoyi-admin/Dockerfile"

  # ★ 2026-09-28 修（本次部署实测踩到）：测试环境的前端产物必须是 **test 模式**。
  #   `.env.test` 与 `.env.production` 只差一处：VITE_APP_ENCRYPT=false。test profile 的后端
  #   **不解密**（api-decrypt 关着），若把 prod 模式的产物发上去，登录体是 AES 密文，后端拿它
  #   当 JSON 解析 → `MismatchedInputException` → 登录回 code=500（错误编号可查 sys-error.log）。
  #   而 `dist/` 是**共享目录**：QC-WEB-002 acc2 这类 accept 要求「前端构建是本次产物」，会在重放时
  #   `rm -rf dist && pnpm build:prod` 把它覆盖成 prod 模式 —— 之后谁再跑一次 deploy.sh，就会把
  #   这份 prod 产物发上测试机，登录当场坏掉（且现象是后端 500，很难一眼看出是前端模式问题）。
  #   所以这里**自己重建**，不信任 dist/ 的现状：部署产物与部署动作绑定，谁都改不歪。
  #   ★ 2026-09-30：`all` 里 phase_artifacts 刚在**同一个进程**里按 test 模式建过（WEB_DIST_FRESH=1），
  #     那一份就是本次产物，不再重建；单独跑 `deploy.sh upload` 时照旧重建。
  if [ "${WEB_DIST_FRESH:-0}" = "1" ]; then
    say "③ 工作台产物是本次 artifacts 阶段刚按 test 模式建的 → 直接上传"
  else
    say "③ 重建工作台产物（test 模式：VITE_APP_ENCRYPT=false，与 test profile 的后端匹配）"
    ( cd "${ROOT}/code/plus-ui" && rm -rf dist && pnpm build:test >/dev/null 2>&1 ) \
      || die "工作台 build:test 失败——先手动跑：cd code/plus-ui && pnpm build:test"
  fi
  [ -f "${ROOT}/code/plus-ui/dist/index.html" ] || die "build:test 跑完却没有 dist/index.html"

  # 工作台静态产物（--delete：旧 chunk 不许留在站根）
  rsync -az --delete -e "ssh -o BatchMode=yes" \
    "${ROOT}/code/plus-ui/dist/" "root@${LQG_TEST_HOST}:${d}/www/"

  # 编排与 nginx 配置
  rsync -az -e "ssh -o BatchMode=yes" \
    "${HERE}/docker-compose.yml" "root@${LQG_TEST_HOST}:${d}/docker-compose.yml"
  rsync -az -e "ssh -o BatchMode=yes" \
    "${HERE}/nginx/workspace.conf" "${HERE}/nginx/proxy-headers.inc" \
    "root@${LQG_TEST_HOST}:${d}/nginx/"

  # ★ 2026-09-28 修（本次部署实测）：compose 里 gotenberg 用 `build.context: ../common/gotenberg`
  #   （相对 compose 文件 → /opt/common/gotenberg）。原来这里**没上传**它，于是服务器上报
  #   「unable to prepare context: path /opt/common/gotenberg not found」→ up 阶段整体失败（远端退出码 17）。
  #   现在把仓库里的 code/deploy/common/gotenberg（Dockerfile + 三套开源字体）一起传上去。
  COMMON_DIR="$(dirname "${d}")/common"
  "${SSH[@]}" "install -d '${COMMON_DIR}'"
  rsync -az --delete -e "ssh -o BatchMode=yes" \
    "${ROOT}/code/deploy/common/gotenberg" "root@${LQG_TEST_HOST}:${COMMON_DIR}/"

  # 远端阶段脚本 + wait-for
  rsync -az -e "ssh -o BatchMode=yes" "${HERE}/remote/" "root@${LQG_TEST_HOST}:${d}/remote/"
  rsync -az -e "ssh -o BatchMode=yes" "${ROOT}/code/deploy/common/wait-for" "root@${LQG_TEST_HOST}:${d}/bin/wait-for"
  "${SSH[@]}" "chmod +x '${d}/remote/'*.sh '${d}/bin/wait-for'"

  # seed 与 reseed.sh（**原样**同步，不改仓库里那份）
  # ★ openrsync（macOS 自带）在「一个文件 + 一个带尾斜杠的目录」混在同一条命令里时，
  #   会把目录的**内容**摊平到目标目录、并留下一个空的同名子目录（实测踩过一次）。
  #   所以拆成两条，且目录源**不带尾斜杠**。
  "${SSH[@]}" "rm -rf '${d}/verify/seed'; install -d '${d}/verify/seed'"
  rsync -az -e "ssh -o BatchMode=yes" \
    "${ROOT}/doc/verify/reseed.sh" "root@${LQG_TEST_HOST}:${d}/verify/"
  rsync -az --delete -e "ssh -o BatchMode=yes" \
    "${ROOT}/doc/verify/seed" "root@${LQG_TEST_HOST}:${d}/verify/"
  "${SSH[@]}" "ls '${d}/verify/seed/' | wc -l | sed 's/^/  seed 段数：/'"

  # .env（口令；600，不进 git）
  rsync -az -e "ssh -o BatchMode=yes" "${ENV_FILE}" "root@${LQG_TEST_HOST}:${d}/.env"
  # openrsync（macOS 自带）不做 uid/gid 名字映射，传上去会是 501:games —— 统一收回 root，
  # 免得后面 docker/git/bash 撞上莫名其妙的属主问题
  "${SSH[@]}" "chown -R root:root '${d}'; chmod 600 '${d}/.env'; ls -l '${d}/.env'"
  say "  ✓ 上传完成"
}

phase_up()      { run_remote_phase "01-up" "01-up.sh" 1800; }
phase_nginx()   { run_remote_phase "02-host-nginx" "02-host-nginx.sh" 600; }
phase_reseed()  { run_remote_phase "03-reseed" "03-reseed.sh" 900; }

phase_verify() {
  local base="https://${LQG_TEST_DOMAIN}"
  say "⑦ 从**本机**打测试环境的真实地址（走公网 443，与甲方/小程序同一条路）"
  echo "--- ${base}/lqg/sys/ping（未带 token；若依把 401 包在响应体里，HTTP 恒 200）---"
  curl -sS -o /tmp/lqg-ping.out -w 'HTTP %{http_code}\n' --max-time 20 "${base}/lqg/sys/ping" || true
  head -c 400 /tmp/lqg-ping.out 2>/dev/null; echo
  echo "--- ${base}/（工作台首页，期望 200 且是 HTML）---"
  curl -sS -o /tmp/lqg-home.out -w 'HTTP %{http_code}  content-type=%{content_type}  size=%{size_download}\n' --max-time 20 "${base}/" || true
  echo "--- ${base}/lqg/sys/ping（带 admin 登录，走 accept 同一条路）---"
  LQG_VERIFY_ENV_FILE="${ROOT}/doc/verify/verify.test.env" bash "${ROOT}/doc/verify/api.sh" --as admin GET /lqg/sys/ping || true
}

# accept 第 2 条的**等价取证**（本机 nc 被代理劫持 → 恒报可达，见 ops §4/§6.5）。
# 三件套：① box 侧发夹探针 + 阳性/阴性对照 ② 安全组入方向清单 ③ box 上 ss -lntp 的 bind 地址。
phase_prove_ports() {
  local outdir="${ROOT}/doc/waves/reports/SYS-STAGING-001"
  install -d "${outdir}"
  say "accept[2] 等价取证 → ${outdir}/"
  rsync -az -e "ssh -o BatchMode=yes" "${HERE}/remote/port-proof.sh" \
    "root@${LQG_TEST_HOST}:${LQG_DEPLOY_DIR}/remote/port-proof.sh"
  "${SSH[@]}" "chmod +x '${LQG_DEPLOY_DIR}/remote/port-proof.sh'"
  {
    echo "# accept[2] 等价取证 ① ② ③ —— 在测试机 \`${LQG_TEST_HOST}\` 上执行"
    echo "# 命令：ssh root@${LQG_TEST_HOST} 'LQG_TEST_PUB_IP=${LQG_TEST_HOST} bash ${LQG_DEPLOY_DIR}/remote/port-proof.sh'"
    echo "# 生成时间：$(date '+%F %T %z')"
    echo
    "${SSH[@]}" "LQG_TEST_PUB_IP='${LQG_TEST_HOST}' bash '${LQG_DEPLOY_DIR}/remote/port-proof.sh'"
  } > "${outdir}/acc2-port-proof.txt" 2>&1 || warn "port-proof 远端执行有非零退出（看输出）"
  say "  ✓ ${outdir}/acc2-port-proof.txt"

  # ★ 2026-09-28：CI 上这条会挂 —— runner 镜像里**有** aliyun 命令但没有 tianda-admin profile，
  #   于是 `aliyun ... GetCallerIdentity` 非零退出，把整个 all 链带成 exit 3（部署本身其实已经成功）。
  #   安全组清单只是「等价取证」的第三份材料，缺云凭据时降级跳过并说清楚即可。
  if command -v aliyun >/dev/null 2>&1 && [ "${LQG_SKIP_ACCOUNT_GATE:-}" != "1" ]; then
    # 安全组 ID 从实例上现查（ops 文档里那份写成 sg-bp17g616h67b936e6b1b6，末尾多了一个 6；
    # 真值 = sg-bp17g616h67b936e6b1b，见报告 §遗留与 raise 的「ops 文档勘误」）
    local ecs_id="${LQG_ECS_ID:-i-bp14wbcfphboybx9idug}" sg
    sg="$(no_proxy='*' NO_PROXY='*' aliyun ecs DescribeInstances --profile tianda-admin \
          --RegionId cn-hangzhou --InstanceIds "[\"${ecs_id}\"]" 2>/dev/null \
          | jq -r '.Instances.Instance[0].SecurityGroupIds.SecurityGroupId[0] // empty')"
    [ -n "${sg}" ] || sg="sg-bp17g616h67b936e6b1b"
    {
      echo "# accept[2] 等价取证 ③ 安全组入方向清单（只读；Kevin 说「只报告，不动」）"
      echo "# 实例：${ecs_id} → 安全组：${sg}（从实例现查，不写死）"
      echo "# 命令：aliyun ecs DescribeSecurityGroupAttribute --profile tianda-admin --RegionId cn-hangzhou --SecurityGroupId ${sg}"
      echo "# 生成时间：$(date '+%F %T %z')"
      echo
      no_proxy='*' NO_PROXY='*' aliyun ecs DescribeSecurityGroupAttribute --profile tianda-admin \
        --RegionId cn-hangzhou --SecurityGroupId "${sg}" 2>&1 \
        | jq -r '.Permissions.Permission[] | "\(.IpProtocol)\t\(.PortRange)\t\(.SourceCidrIp // "-")\t\(.Direction)\t\(.Description // "")"' \
        | sort
      echo
      echo "# 读法：下面若有 5432/6379/9000 且 SourceCidrIp=0.0.0.0/0 才是「对公网开放」。"
      echo "#      本项目 compose 只把这三个发布到 127.0.0.1（见 acc2-port-proof.txt ①③），"
      echo "#      所以即使安全组开着也连不上 —— 本票不改安全组（Kevin 明确只报告不动）。"
    } > "${outdir}/acc2-security-group.txt" 2>&1
    say "  ✓ ${outdir}/acc2-security-group.txt"
  else
    warn "本机没有 aliyun CLI，跳过分组清单"
  fi
}

phase_status() {
  "${SSH[@]}" "bash '${LQG_DEPLOY_DIR}/remote/status.sh'" || "${SSH[@]}" "bash -s" < "${HERE}/remote/status.sh"
}

# ── 小程序体验版上传（2026-09-28 加）─────────────────────────────────────────
# 为什么要有这一阶段、以及为什么上传在服务器上做：
#   · 微信要求上传来源 IP 在「开发管理 → 开发设置 → 小程序代码上传 → IP 白名单」里；
#     开发机常年跑在代理后面（TUN 模式，`curl --noproxy '*'` 与直连拿到的是同一个代理出口 IP），
#     那个 IP 不稳定、也不该长期占据白名单 → 上传挪到固定公网 IP 的测试机上。
#   · 但**构建必须在 macOS 上**（gotchas §6.5：不同 OS 产物不同，体验版真机上会渲染空）——
#     本机，或 GitHub 的 macOS runner（2026-09-30 起 push staging 由 .github/workflows/miniapp-staging.yml 自动跑本阶段）。
#   · 所以流程是：本机 `--build-only`（构建 + 配置守卫 + 产物守卫，两道都过）→ 同步物料 →
#     服务器上 `--skip-build`（守卫再跑一遍）→ miniprogram-ci 上传 + 生成体验版二维码。
# 前置：.env 里配 LQG_WX_APPID 与 LQG_MINIPROGRAM_KEY（本机绝对路径；密钥绝不进仓库）。
phase_miniapp() {
  local mode="test" mpd="${LQG_DEPLOY_DIR}/miniapp"
  [ -n "${LQG_WX_APPID:-}" ] || die "缺 LQG_WX_APPID（写进 code/deploy/test/.env）"
  [ -n "${LQG_MINIPROGRAM_KEY:-}" ] || die "缺 LQG_MINIPROGRAM_KEY=<private.<appid>.key 的绝对路径>（写进 .env）"
  [ -f "${LQG_MINIPROGRAM_KEY}" ] || die "上传密钥文件不存在：${LQG_MINIPROGRAM_KEY}"

  say "⑧ 小程序体验版：本机构建（mode=${mode}）"
  ( cd "${ROOT}/code/miniapp" && LQG_WX_APPID="${LQG_WX_APPID}" pnpm upload:mp --mode="${mode}" --build-only ) \
    || die "本机构建或发布守卫没过（原因见上）"

  # ★ 2026-09-28 加：产物「自定义组件死键」自检（Kevin 报过小程序点不动、H5 正常，见台账 #361）。
  #   判据查编译产物：父组件对自定义组件绑了 bindX，子组件必须真的 emit('X')。
  #   放在这里 = **带死键的包发不出去**（比事后再查便宜得多）。
  python3 "${ROOT}/doc/waves/tools/check-mp-component-events.py" \
    "${ROOT}/code/miniapp/dist/build/mp-weixin-${mode}" \
    || die "小程序产物里有『自定义组件死键』—— 小程序里点了没反应（H5 反而正常）；修法见上面的清单"

  say "⑧ 同步物料 → ${LQG_TEST_HOST}:${mpd}"
  "${SSH[@]}" "install -d '${mpd}/dist/build'"
  # openrsync（macOS 自带）在「文件 + 带尾斜杠目录」混在一条命令里会摊平，所以逐条来
  rsync -az -e "ssh -o BatchMode=yes" "${ROOT}/code/miniapp/scripts" "root@${LQG_TEST_HOST}:${mpd}/"
  rsync -az -e "ssh -o BatchMode=yes" "${ROOT}/code/miniapp/package.json" "root@${LQG_TEST_HOST}:${mpd}/"
  rsync -az --delete -e "ssh -o BatchMode=yes" "${ROOT}/code/miniapp/env" "root@${LQG_TEST_HOST}:${mpd}/"
  rsync -az --delete -e "ssh -o BatchMode=yes" \
    "${ROOT}/code/miniapp/dist/build/mp-weixin-${mode}" "root@${LQG_TEST_HOST}:${mpd}/dist/build/"
  # 上传密钥：只往服务器放，600；仓库里永远没有它
  rsync -az -e "ssh -o BatchMode=yes" "${LQG_MINIPROGRAM_KEY}" "root@${LQG_TEST_HOST}:${mpd}/private.key"
  "${SSH[@]}" "chmod 600 '${mpd}/private.key'"
  # 本阶段可能被单独调用（没有先跑 upload），远端脚本自己送一份
  rsync -az -e "ssh -o BatchMode=yes" "${HERE}/remote/04-miniapp-upload.sh" "root@${LQG_TEST_HOST}:${LQG_DEPLOY_DIR}/remote/"

  run_remote_phase "04-miniapp-upload" "04-miniapp-upload.sh" 900

  # 把体验版二维码取回来（在服务器上生成的，落到本机 dist/，该目录已被 gitignore）
  if "${SSH[@]}" "ls '${mpd}'/dist/体验版二维码-*.png >/dev/null 2>&1"; then
    rsync -az -e "ssh -o BatchMode=yes" "root@${LQG_TEST_HOST}:${mpd}/dist/体验版二维码-*.png" \
      "${ROOT}/code/miniapp/dist/" 2>/dev/null \
      && say "  ✓ 体验版二维码已取回：code/miniapp/dist/（**不要提交进仓库**）" \
      || warn "  二维码回传失败（不影响上传结果，可在服务器 ${mpd}/dist/ 取）"
  fi
  say "  ⚠ 上传成功后还要在「微信公众平台 → 版本管理」把该版本**设为体验版**并添加体验成员"
}

phase_down() {
  warn "只 down 本项目（lqg-test）；别人的容器一个都不动"
  "${SSH[@]}" "cd '${LQG_DEPLOY_DIR}' && docker compose -f docker-compose.yml --env-file .env down"
}

main() {
  local cmd="${1:-all}"
  ensure_env_file
  load_env
  gate_account
  ssh_ok
  case "${cmd}" in
    all)
      # ★ 2026-09-30（Kevin：「每次提交 github action 部署都需要很久」）：日常部署去掉两步 ——
      #   · reseed：它 TRUNCATE 全部 t_lqg_* 表并删掉 wx_ 用户，等于**每推一次就把甲方在测试站录的数据清空**、
      #     合作单位还得重新绑定。表结构变更由后端启动时的 Flyway 迁移负责，与 reseed 无关。
      #     要恢复初始测试数据：`deploy.sh reseed`，或 GitHub 上手动跑工作流并勾选 reseed。
      #   · prove-ports：SYS-STAGING-001 的一次性验收取证（报告写在本机 doc/waves/reports/ 下，
      #     在 CI 的 runner 上写完就随 runner 销毁），每次部署重跑没有意义，约 60s。
      #   新机器首次部署用 `bootstrap`（= 以前的 all）。
      phase_artifacts
      phase_upload
      phase_up
      phase_nginx
      phase_verify
      ;;
    bootstrap)
      phase_artifacts
      phase_upload
      phase_up
      phase_nginx
      phase_reseed
      phase_verify
      phase_prove_ports
      # ★ 小程序上传**故意不放进 all**：它依赖一个微信侧的外部前提（上传来源 IP 在白名单里，
      #   见台账 #353）。放进来会让「一个只能由甲方/微信后台操作的前提」把整个测试环境部署搞失败。
      #   全自动化 = `deploy.sh all && deploy.sh miniapp`（后者失败不影响前者已部署好的环境）。
      ;;
    artifacts) phase_artifacts ;;
    upload)    sync_build_commit; phase_upload ;;
    up)        phase_up ;;
    nginx)     phase_nginx ;;
    reseed)    phase_reseed ;;
    verify)    phase_verify ;;
    miniapp)   phase_miniapp ;;
    prove-ports) phase_prove_ports ;;
    status)    phase_status ;;
    down)      phase_down ;;
    *) die "用法：bash deploy.sh {all|bootstrap|artifacts|upload|up|nginx|reseed|verify|miniapp|prove-ports|status|down}" ;;
  esac
  say "完成：${cmd}"
}

main "$@"
