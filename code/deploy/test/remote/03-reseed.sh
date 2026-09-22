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

log "3) reseed 完成"
