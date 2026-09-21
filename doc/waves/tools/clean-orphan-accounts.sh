#!/usr/bin/env bash
# 清掉「并发 reseed 窗口里自动建出的孤儿账号」，把库拉回确定性 seed 状态。
#
# 为什么需要它（D1 实测，issue #18）：
#   reseed.sh 先 TRUNCATE t_lqg_* 、再 DELETE sys_user(9000000000-9000009999)、最后逐段灌 seed。
#   两步之间有一个窗口：窗口内进来的 mock 登录按手机号查 sys_user 查不到 → 走
#   WxAccountBindService#createExternalUser 建了一个 snowflake id 的新账号。
#   而 reseed.sh 只删 9000000000-9000009999 段，**snowflake id 的孤儿号永远留在库里**：
#     - 同手机号两行 → 之后 selectOne(eq(phonenumber)) 抛 TooManyResults，--as extA 直接挂；
#     - 下游 MP ticket 的 bindStatus=verified 断言全红。
#
#   ★ 根因是调度：同一时刻只许一个 ticket 碰 PG 5433（DB 是容量 1 的共享资源）。
#     这个脚本是把已经污染的库拉回干净状态的补救，不是让并行变合法的手段。
#
#   bash doc/waves/tools/clean-orphan-accounts.sh --yes
set -euo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "${HERE}/../../.." && pwd)"
ENV_FILE="${LQG_VERIFY_ENV_FILE:-${ROOT}/doc/verify/verify.env}"
[ -f "${ENV_FILE}" ] && set -a && . "${ENV_FILE}" && set +a
: "${LQG_DB_HOST:?缺 LQG_DB_HOST}" "${LQG_DB_NAME:?缺 LQG_DB_NAME}" "${LQG_DB_USER:?缺 LQG_DB_USER}"
case "${LQG_DB_NAME}" in *dev*|*test*) ;; *) echo "[拒绝] 库名 ${LQG_DB_NAME} 不含 dev / test——本脚本只许对开发、测试库执行" >&2; exit 2 ;; esac
[ "${1:-}" = "--yes" ] || { echo "将删除 ${LQG_DB_HOST}/${LQG_DB_NAME} 里所有非 seed 段的 app_user 账号及其角色/绑定/档案行。确认请加 --yes" >&2; exit 2; }
export PGPASSWORD="${LQG_DB_PASSWORD:-}"
PSQL=(psql -v ON_ERROR_STOP=1 -q -h "${LQG_DB_HOST}" -p "${LQG_DB_PORT:-5432}" -U "${LQG_DB_USER}" -d "${LQG_DB_NAME}")

"${PSQL[@]}" <<'SQL'
BEGIN;
-- 孤儿 = 不在 seed 段（9000000000-9000009999）的 app_user 账号（都是登录链路自动建出来的）
CREATE TEMP TABLE _lqg_orphan ON COMMIT DROP AS
SELECT user_id, user_name, phonenumber
FROM sys_user
WHERE user_id NOT BETWEEN 9000000000 AND 9000009999
  AND user_type = 'app_user';

DELETE FROM sys_user_role     WHERE user_id IN (SELECT user_id FROM _lqg_orphan);
DELETE FROM t_lqg_ext_profile WHERE user_id IN (SELECT user_id FROM _lqg_orphan);
DELETE FROM t_lqg_wx_bind     WHERE user_id IN (SELECT user_id FROM _lqg_orphan);
DELETE FROM sys_user          WHERE user_id IN (SELECT user_id FROM _lqg_orphan);

SELECT '清掉孤儿账号 ' || count(*) || ' 个：' || COALESCE(string_agg(user_name || '(' || phonenumber || ')', ', '), '（无）') AS r
FROM _lqg_orphan;
COMMIT;
SQL

echo "── 复核：同手机号多行（应为空；空串手机号是上游自带账号，不算）──"
"${PSQL[@]}" -c "SELECT phonenumber, count(*) FROM sys_user WHERE del_flag='0' AND phonenumber IS NOT NULL AND phonenumber <> '' GROUP BY phonenumber HAVING count(*) > 1;"
echo "── 复核：非 seed 段 app_user（应为空）──"
"${PSQL[@]}" -c "SELECT user_id, user_name FROM sys_user WHERE user_id NOT BETWEEN 9000000000 AND 9000009999 AND user_type='app_user';"
