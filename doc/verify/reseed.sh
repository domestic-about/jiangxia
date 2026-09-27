#!/usr/bin/env bash
# 把 dev / test 库的业务数据重置成确定性快照：清空全部 t_lqg_* 表与 seed 账号 → 逐段灌 doc/verify/seed/*.sql。
# 分段按「哪张 ticket 建的表」切：某段依赖的表还没建就跳过——表建到哪，seed 灌到哪。
#   bash doc/verify/reseed.sh --yes
# 护栏：库名不含 dev 或 test 直接拒绝（这条脚本会 TRUNCATE，绝不许碰生产）。
set -euo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ENV_FILE="${LQG_VERIFY_ENV_FILE:-${HERE}/verify.env}"
[ -f "${ENV_FILE}" ] && set -a && . "${ENV_FILE}" && set +a
: "${LQG_DB_HOST:?缺 LQG_DB_HOST（建 doc/verify/verify.env）}" "${LQG_DB_NAME:?缺 LQG_DB_NAME}" "${LQG_DB_USER:?缺 LQG_DB_USER}"
case "${LQG_DB_NAME}" in *dev*|*test*) ;; *) echo "[拒绝] 库名 ${LQG_DB_NAME} 不含 dev / test——reseed 只许对开发、测试库执行" >&2; exit 2 ;; esac
[ "${1:-}" = "--yes" ] || { echo "将清空 ${LQG_DB_HOST}/${LQG_DB_NAME} 的全部 t_lqg_* 表并重灌测试数据。确认请加 --yes" >&2; exit 2; }
export PGPASSWORD="${LQG_DB_PASSWORD:-}"
PSQL=(psql -v ON_ERROR_STOP=1 -q -h "${LQG_DB_HOST}" -p "${LQG_DB_PORT:-5432}" -U "${LQG_DB_USER}" -d "${LQG_DB_NAME}")
"${PSQL[@]}" <<'SQL'
DO $$
DECLARE t text;
BEGIN
  FOR t IN SELECT tablename FROM pg_tables WHERE schemaname = 'public' AND tablename LIKE 't\_lqg\_%' LOOP
    EXECUTE format('TRUNCATE TABLE %I', t);
  END LOOP;
END $$;
DELETE FROM sys_user_role WHERE user_id BETWEEN 9000000000 AND 9000009999;
DELETE FROM sys_user      WHERE user_id BETWEEN 9000000000 AND 9000009999;
DELETE FROM sys_oss       WHERE oss_id  BETWEEN 9000000000 AND 9000009999;
-- ★ 基线维护（2026-09-27）：验收登录会按手机号建**雪花 id** 的用户（user_name 形如 wx_13800000099），
--   这些行不在种子段里，下面这一步不清理它们的话：t_lqg_ext_profile / t_lqg_wx_bind 已被上面的
--   TRUNCATE 清空，而 sys_user 还在 → 下次登录走「账号已存在」分支、不再补建 ext_profile
--   → /mp/me 的 .data.ext 为 null，AUTH-LOGIN-001 acc2 的 `ext.bindStatus=="unbound"` 必红。
--   表现就是「同一条 accept 第二次跑必红」（不幂等），CI 上尤其致命。
--   只删「wx_ 开头且不在种子段」的用户：种子用户（含 wx_13800000012 这类）落在种子段内，不受影响。
DELETE FROM sys_user_role WHERE user_id IN (SELECT user_id FROM sys_user WHERE user_name LIKE 'wx\_%' AND user_id NOT BETWEEN 9000000000 AND 9000009999);
DELETE FROM sys_user      WHERE user_name LIKE 'wx\_%' AND user_id NOT BETWEEN 9000000000 AND 9000009999;
SQL
for f in "${HERE}"/seed/*.sql; do
  need="$(sed -n 's/^-- requires: *//p' "${f}" | head -1)"
  missing=""
  for tb in ${need}; do
    [ "$("${PSQL[@]}" -At -c "SELECT to_regclass('public.${tb}') IS NOT NULL")" = "t" ] || missing="${missing} ${tb}"
  done
  if [ -n "${missing}" ]; then
    echo "  跳过 $(basename "${f}")：表还没建 →${missing}"
  else
    "${PSQL[@]}" -f "${f}"
    echo "  已灌 $(basename "${f}")"
  fi
done
echo "reseed 完成"
