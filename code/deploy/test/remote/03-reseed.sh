#!/usr/bin/env bash
# SYS-STAGING-001 · 在测试机上把 doc/verify/seed/ 灌进 lqg_test
#
# 由 code/deploy/test/deploy.sh 上传后执行（分离：日志 /opt/lqg-test/logs/03-reseed.log，
# 标记 /opt/lqg-test/logs/03-reseed.done）。
#
# ★ **不改 doc/verify/reseed.sh 本身**（只读区）。它靠宿主 `psql` 连库，而测试机上没有
#   postgres 客户端 —— 这里用两条路之一（票面给的①/②）：
#     ②（本脚本默认）在 /opt/lqg-test/bin/psql 放一个 shim，把 psql 调用转发进
#        compose 的 postgres 容器（`docker exec -i lqg-test-postgres psql`），
#        宿主不装任何包、不占磁盘；shim 只对**本次路径**生效（PATH 前置，不改系统）。
#     ① 若测试机上本来就有 psql（`command -v psql`），直接用真的，不插 shim。
#   两条路都只影响 reseed 这一次调用，reseed.sh 一个字节都没动。
set -euo pipefail
cd /opt/lqg-test

set -a; . /opt/lqg-test/.env; set +a
COMPOSE=(docker compose -f /opt/lqg-test/docker-compose.yml --env-file /opt/lqg-test/.env)

log() { echo "[$(date '+%F %T')] $*"; }

log "===== 1) 准备 psql 入口 ====="
install -d /opt/lqg-test/bin
if command -v psql >/dev/null 2>&1 && [ ! -e /opt/lqg-test/bin/psql ]; then
  log "  宿主已有 psql：$(command -v psql) → 不插 shim"
else
  log "  宿主没有 psql → 放 shim /opt/lqg-test/bin/psql（转发进 postgres 容器）"
  cat > /opt/lqg-test/bin/psql <<'EOS'
#!/usr/bin/env bash
# SYS-STAGING-001 · psql shim：宿主没有 postgres 客户端，把调用转发进 compose 的 postgres 容器。
# reseed.sh 传的是**宿主环回**地址（-h 127.0.0.1 -p 15432）——那两个值在容器里不成立，丢掉；
# 其余参数（-v ON_ERROR_STOP=1 -q -U lqg -d lqg_test -At -c … / -f …）原样转发。
# ★ `-f <宿主路径>` 要改成 **stdin 喂进去**：seed 的 .sql 在宿主上，容器里看不到那个路径，
#   直接转发 -f 会报 "No such file or directory"（实测踩过）。psql 不带 -f 时就是从 stdin 读。
# 容器内官方 postgres 镜像的 pg_hba 对 unix socket 是 trust，所以 -U/-d 就够。
set -euo pipefail
ARGS=()
INPUT=""
while [ $# -gt 0 ]; do
  case "$1" in
    -h|--host|-p|--port) shift 2 ;;
    -h*|-p*)             shift   ;;
    -f|--file)           INPUT="$2"; shift 2 ;;
    -f*)                 INPUT="${1#-f}"; shift ;;
    *)                   ARGS+=("$1"); shift ;;
  esac
done
if [ -n "${INPUT}" ]; then
  [ -r "${INPUT}" ] || { echo "psql shim: 打不开 ${INPUT}" >&2; exit 1; }
  exec docker exec -i -e PGPASSWORD="${PGPASSWORD:-}" lqg-test-postgres psql "${ARGS[@]}" < "${INPUT}"
fi
exec docker exec -i -e PGPASSWORD="${PGPASSWORD:-}" lqg-test-postgres psql "${ARGS[@]}"
EOS
  chmod +x /opt/lqg-test/bin/psql
fi
export PATH="/opt/lqg-test/bin:${PATH}"
log "  psql -> $(command -v psql)"
psql --version 2>&1 | sed 's/^/  /' || true

log "===== 2) verify.env（只给 reseed.sh 用；库名必须含 test —— 它的护栏）====="
cat > /opt/lqg-test/verify.env <<EOS
# SYS-STAGING-001 · reseed 用的库连接（测试机 → compose 的 postgres 宿主环回端口）
# 这个文件含口令，属于测试机本地文件；仓库里没有它。
LQG_DB_HOST=127.0.0.1
LQG_DB_PORT=${LQG_DB_PORT}
LQG_DB_NAME=${LQG_DB_NAME}
LQG_DB_USER=${LQG_DB_USER}
LQG_DB_PASSWORD=${LQG_DB_PASSWORD}
EOS
chmod 600 /opt/lqg-test/verify.env

log "===== 3) 等 postgres 真正能收连接 ====="
wait-for -t 120 "127.0.0.1:${LQG_DB_PORT}"
for i in $(seq 1 30); do
  if psql -U "${LQG_DB_USER}" -d "${LQG_DB_NAME}" -At -c 'select 1' >/dev/null 2>&1; then
    log "  库 ${LQG_DB_NAME} 可连（第 ${i} 次）"; break
  fi
  [ "${i}" = 30 ] && { log "  ✗ 库连不上"; exit 1; }
  sleep 2
done

log "===== 4) Flyway 表是否已建（表建到哪，seed 灌到哪）====="
psql -U "${LQG_DB_USER}" -d "${LQG_DB_NAME}" -At \
  -c "select count(*) from pg_tables where schemaname='public' and tablename like 't\_lqg\_%'" \
  | sed 's/^/  t_lqg_* 表数：/'
psql -U "${LQG_DB_USER}" -d "${LQG_DB_NAME}" -At \
  -c "select max(version) from flyway_schema_history where success" | sed 's/^/  最新成功迁移：/'

log "===== 5) 跑 reseed（原样调用同步过来的 reseed.sh，未做任何改动）====="
export LQG_VERIFY_ENV_FILE=/opt/lqg-test/verify.env
bash /opt/lqg-test/verify/reseed.sh --yes

log "===== 6) 灌完复核（期望：样本 10 行 / extA 可见 4 / staff 历史 2）====="
psql -U "${LQG_DB_USER}" -d "${LQG_DB_NAME}" -At -c "select 'sample_rows='||count(*) from t_lqg_sample" | sed 's/^/  /'
psql -U "${LQG_DB_USER}" -d "${LQG_DB_NAME}" -At -c "select 'seed_users='||count(*) from sys_user where user_id between 9000000000 and 9000009999" | sed 's/^/  /'
psql -U "${LQG_DB_USER}" -d "${LQG_DB_NAME}" -At \
  -c "select 'donor_name_密文='||(donor_name <> '测试供体甲')||' len='||length(donor_name) from t_lqg_sample where id=9000001001" | sed 's/^/  /'

log "===== 7) 管理员口令加固（每次 reseed 后都强制成强口令；幂等的是**最终状态**）====="
# 为什么要有这一步：doc/verify/seed/01-accounts.sql 里 lqgadmin 的口令是 seed 默认值，
# 而**测试环境是公网可达的**（https://songjian.tianda.studio）。只改一次口令没用——
# 下一次 `deploy.sh reseed` 就把它退回默认值。这里每次灌完都强制成 .env 里的强口令。
# ★ 注意措辞：reseed 每次都先把账号段重灌 → 口令**每次都**回到默认值，所以本步**每次都会真的改**，
#   「无需改动」那条分支只是防御性兜底（例如账号段没重灌时）。不变的是**最终状态**：
#   跑 0 次、1 次、N 次 reseed，结束时的口令都是 LQG_ADMIN_PASSWORD。
#   · LQG_ADMIN_PASSWORD      目标强口令（不设则跳过，并明确告诉运维「仍是默认口令」）
#   · LQG_SEED_ADMIN_PASSWORD seed 里的默认口令（默认 admin123，仅用于「从默认改到强」这一步）
#   · LQG_CLIENT_PC           工作台账号登录的 client（漏配 → 本步无法登录，会明确告警并跳过）
ADMIN_USER="${LQG_ADMIN_USER:-lqgadmin}"
NEW_PW="${LQG_ADMIN_PASSWORD:-}"
SEED_PW="${LQG_SEED_ADMIN_PASSWORD:-admin123}"
API="http://127.0.0.1:${LQG_API_PORT:-8082}"
CLIENT="${LQG_CLIENT_PC:-}"

# 去掉 JSON 里会破坏载荷的字符：口令只允许可打印且不含 " 和 \ 的字符
json_ok() { case "$1" in *'"'*|*'\'*) return 1 ;; *) return 0 ;; esac; }

try_login() {  # $1=口令 → 成功则打印 token，失败打印空
  local pw="$1"
  curl -sS --max-time 15 -X POST "${API}/auth/login" \
    -H 'Content-Type: application/json' -H "clientid: ${CLIENT}" \
    -d "$(printf '{"clientId":"%s","grantType":"password","tenantId":"000000","username":"%s","password":"%s"}' \
            "${CLIENT}" "${ADMIN_USER}" "${pw}")" 2>/dev/null \
    | sed -n 's/.*"access_token":"\([^"]*\)".*/\1/p' | head -1
}

if [ -z "${NEW_PW}" ]; then
  log "  ⚠ LQG_ADMIN_PASSWORD 未设置 → 跳过；**管理员口令仍是 seed 默认值**，"
  log "    公网可达期间请勿放真实数据（SYS-STAGING-001 风险条）"
elif [ -z "${CLIENT}" ]; then
  log "  ⚠ LQG_CLIENT_PC 未设置 → 无法登录改口令，跳过（口令仍是 seed 默认值）"
elif ! json_ok "${NEW_PW}"; then
  log "  ✗ LQG_ADMIN_PASSWORD 含 \" 或 \\，无法安全拼进 JSON → 跳过（请换一个口令）"
elif [ -n "$(try_login "${NEW_PW}")" ]; then
  log "  ✓ 已是强口令（新口令可直接登录）→ 无需改动"
else
  TOKEN="$(try_login "${SEED_PW}")"
  if [ -z "${TOKEN}" ]; then
    log "  ✗ 新口令与 seed 默认口令都登不上 → 口令状态未知，请人工处理："
    log "      ssh root@<host> \"curl -sS -X POST ${API}/auth/login -H 'Content-Type: application/json' -H 'clientid: ${CLIENT}' -d '{...}'\""
    exit 1
  fi
  RESP="$(curl -sS --max-time 15 -X PUT "${API}/system/user/profile/updatePwd" \
    -H 'Content-Type: application/json' -H "Authorization: Bearer ${TOKEN}" -H "clientid: ${CLIENT}" \
    -d "$(printf '{"oldPassword":"%s","newPassword":"%s"}' "${SEED_PW}" "${NEW_PW}")")"
  case "${RESP}" in
    *'"code":200'*)
      if [ -n "$(try_login "${NEW_PW}")" ]; then
        log "  ✓ 已把 ${ADMIN_USER} 的口令从 seed 默认值改成 .env 里的强口令，并复验可登录"
      else
        log "  ✗ 接口报成功但新口令登不上 → 请人工复核"; exit 1
      fi ;;
    *新密码不能与旧密码相同*)
      # 目标口令 == seed 默认口令（Kevin 要求测试环境用 admin123）→ 已经是目标状态，不算失败
      log "  ✓ 目标口令与当前口令相同（未改动）——已符合 .env 里的 LQG_ADMIN_PASSWORD" ;;
    *) log "  ✗ 改口令失败：${RESP}"; exit 1 ;;
  esac
fi

log "3) reseed 完成"
