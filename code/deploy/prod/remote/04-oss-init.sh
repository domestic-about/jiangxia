#!/usr/bin/env bash
# SYS-PROD-001 · 远端：写入生产 OSS 配置（sys_oss_config 一行）+ 建**私有**桶
#
# 由 deploy.sh 分离执行（bash deploy.sh oss-init），输出落 logs/oss.log。
#
# ★ 为什么是一支**手工执行一次**的 SQL，不进 Flyway：
#   Flyway 的迁移脚本进仓库、每台环境都会跑一次；而这一行里带着**生产 AK**，
#   且只应该在生产执行一次。放进 Flyway = 把 AK 写进仓库（accept 2 第 6 段必红）
#   且让 dev / test 也去指向生产桶（dev 有 MinIO，test 也有自己的桶）。
#
# ★ AK 的来源：**服务器上的 ${LQG_DATA_DIR}/.env**（600），不是仓库、不是命令行历史。
#   psql 用 `-v` 传参 + `:'var'` 引用（libpq 会按字面量转义，不拼字符串）。
set -euo pipefail

cd "$(dirname "$0")/.."
DATA_DIR="$(pwd)"
set -a; . "${DATA_DIR}/.env"; set +a
: "${LQG_DATA_DIR:=${DATA_DIR}}" "${LQG_DB_NAME:?}" "${LQG_DB_USER:?}" "${LQG_DB_PASSWORD:?}"

if [ -z "${LQG_OSS_BUCKET:-}" ] || [ -z "${LQG_OSS_ACCESS_KEY_ID:-}" ]; then
  echo "[oss] ⚠️ LQG_OSS_BUCKET / LQG_OSS_ACCESS_KEY_ID 未填 —— 跳过（这是 owner 待提供的 B1）"
  echo "[oss]    本步骤不做，接受验收「OSS 对象匿名读被拒」挂 blocked。填好后重跑：bash deploy.sh oss-init"
  exit 0
fi
for k in LQG_OSS_ENDPOINT LQG_OSS_REGION LQG_OSS_ACCESS_KEY_SECRET; do
  eval "v=\${$k:-}"; [ -n "${v}" ] || { echo "[oss] ❌ ${k} 未填"; exit 1; }
done

SQL="${LQG_DATA_DIR}/oss/oss-init.sql"
[ -f "${SQL}" ] || { echo "[oss] ❌ 找不到 ${SQL}"; exit 1; }

echo "=== [oss] 桶 ${LQG_OSS_BUCKET} @ ${LQG_OSS_ENDPOINT}（前缀 ${LQG_OSS_PREFIX:-lqg/}）==="

# ── 1. 先在 OSS 侧把桶的 ACL 确认成**私有** ─────────────────────────────────────
# 这一步用 ossutil（阿里云官方 CLI）。没装就只给提示，不阻塞 —— SQL 那一步才是后端要的。
if command -v ossutil >/dev/null 2>&1; then
  echo "  ossutil 已装，核对桶 ACL（期望 private）"
  ossutil -e "${LQG_OSS_ENDPOINT}" -i "${LQG_OSS_ACCESS_KEY_ID}" -k "${LQG_OSS_ACCESS_KEY_SECRET}" \
    bucket-acl "oss://${LQG_OSS_BUCKET}" 2>&1 | sed 's/^/    /' || {
      echo "  ⚠️ 读 ACL 失败（多数是 RAM 子账号没授 oss:GetBucketAcl）—— 不影响后端，人工在控制台确认「私有」"
    }
  echo "  ★ 桶必须是**私有读写**：公共读 = 任何拿到链接的人都能看到供体的质控文档（accept 2 第 4 段）。"
  echo "    控制台路径：OSS → Bucket → 权限管理 → 读写权限 → 私有。"
else
  echo "  （没装 ossutil；桶 ACL 请在控制台人工确认「私有读写」，验收探针是 LQG_OSS_PROBE_URL 匿名 403）"
fi

# ── 2. 写 sys_oss_config（在 postgres 容器里执行，不落宿主的 psql 客户端）──────────
echo "=== [oss] 执行 oss-init.sql（docker exec 进 postgres，不经宿主的 5432）==="
docker exec -i lqg-prod-postgres \
  psql -v ON_ERROR_STOP=1 -U "${LQG_DB_USER}" -d "${LQG_DB_NAME}" \
       -v bucket="${LQG_OSS_BUCKET}" \
       -v endpoint="${LQG_OSS_ENDPOINT}" \
       -v region="${LQG_OSS_REGION}" \
       -v ak="${LQG_OSS_ACCESS_KEY_ID}" \
       -v sk="${LQG_OSS_ACCESS_KEY_SECRET}" \
       -v prefix="${LQG_OSS_PREFIX:-lqg/}" \
  < "${SQL}"

# ── 3. 回读确认（不打印 AK/SK 原值）────────────────────────────────────────────
echo "=== [oss] 回读 sys_oss_config（只打印桶 / 前缀 / 权限 / 是否默认）==="
docker exec -i lqg-prod-postgres \
  psql -U "${LQG_DB_USER}" -d "${LQG_DB_NAME}" -c \
  "select oss_config_id, config_key, bucket_name, prefix, access_policy as policy_0私有, status as status_0默认,
          case when access_key = '' then 'EMPTY' else 'SET(len=' || length(access_key) || ')' end as ak
     from sys_oss_config order by oss_config_id;"

echo "[oss] ✅ 完成（★ 桶 ACL 若是 public-read，这里一步都不会报错 —— 必须人工/探针复核）"
