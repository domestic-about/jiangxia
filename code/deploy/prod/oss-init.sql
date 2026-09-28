-- ── SYS-PROD-001 · 生产 OSS 配置初始化（sys_oss_config 一行）────────────────────────────
--
-- ★ 不进 Flyway，**手工执行一次**。为什么：
--   1. 这一行里带着生产 AK/SK —— 进 Flyway = 进仓库（accept 2 第 6 段的
--      `git grep 'LTAI[0-9A-Za-z]{12,}'` 必红）；
--   2. Flyway 在 dev / test / prod 都会跑 —— dev 有 MinIO、test 有自己的桶，
--      不该被生产桶覆盖。
--
-- ★ 怎么执行（**在数据库容器里**，宿主上没有 5432 在监听，也不许有）：
--     cd /opt/lqg && bash remote/04-oss-init.sh          # 由 deploy.sh oss-init 调起
--   或手工（AK 从 /opt/lqg/.env 取，不要打进 shell 历史）：
--     docker exec -i lqg-prod-postgres psql -v ON_ERROR_STOP=1 -U lqg -d lqg \
--       -v bucket="$LQG_OSS_BUCKET" -v endpoint="$LQG_OSS_ENDPOINT" -v region="$LQG_OSS_REGION" \
--       -v ak="$LQG_OSS_ACCESS_KEY_ID" -v sk="$LQG_OSS_ACCESS_KEY_SECRET" -v prefix="${LQG_OSS_PREFIX:-lqg/}" \
--       < oss/oss-init.sql
--
-- ★ 变量由 `-v` 传入；下面用 `:'var'`（**带冒号**）引用 —— libpq 会按字面量安全转义，
--   拼字符串会踩引号注入。缺变量时 psql 直接报错退出，不会静默写空值。
--
-- ★ 桶权限必须是**私有读写**：`access_policy = '0'`（0=private / 1=public / 2=custom，
--   见 baseline 里 sys_oss_config.access_policy 的注释）。
--   公共读 = 任何拿到链接的人都能看到供体的质控文档（accept 2 的 counterfeit 明写这一点）。
--   注意：这一列只是**让后端签私有 URL**；桶本身的 ACL 还得在 OSS 控制台是「私有」
--   （两道都要对：后端签了名，但桶若是公共读，裸地址依然匿名可读 → accept 2 第 4 段红）。
--
-- ★ status = '0' = **默认配置**（0=是 / 1=否）。上游 seed 里 minio 那行是 status='0'，
--   所以这里要先把别的行降级，再把 aliyun 这行提为默认 —— 否则后端取到的是别人的桶。
--
-- ★ prefix = 'lqg/'：业务对象前缀。备份走 `backup/`（SYS-BACKUP-001），两个前缀分开，
--   免得生命周期规则把业务文件一起过期掉。

BEGIN;

-- 1) 本环境只留一个默认配置：先全部降级
UPDATE sys_oss_config SET status = '1';

-- 2) aliyun 那行：存在就更新（幂等，重跑不炸），不存在就插入
--    ★ 更新时**只**在传入值非空时覆盖 AK/SK，避免「重跑一次忘了带 AK 就把生产配置清空」
INSERT INTO sys_oss_config (
    oss_config_id, tenant_id, config_key,
    access_key, secret_key, bucket_name, prefix,
    endpoint, domain, is_https, region, access_policy, status,
    create_dept, create_by, create_time, update_by, update_time, remark
) VALUES (
    3, '000000', 'aliyun',
    :'ak', :'sk', :'bucket', :'prefix',
    :'endpoint', '', 'Y', :'region', '0', '0',
    103, 1, now(), 1, now(),
    'SYS-PROD-001 生产私有桶；本行由 oss/oss-init.sql 手工写入，不进 Flyway'
)
ON CONFLICT (oss_config_id) DO UPDATE SET
    config_key    = EXCLUDED.config_key,
    access_key    = CASE WHEN EXCLUDED.access_key = '' THEN sys_oss_config.access_key ELSE EXCLUDED.access_key END,
    secret_key    = CASE WHEN EXCLUDED.secret_key = '' THEN sys_oss_config.secret_key ELSE EXCLUDED.secret_key END,
    bucket_name   = EXCLUDED.bucket_name,
    prefix        = EXCLUDED.prefix,
    endpoint      = EXCLUDED.endpoint,
    is_https      = 'Y',
    region        = EXCLUDED.region,
    access_policy = '0',
    status        = '0',
    update_by     = 1,
    update_time   = now(),
    remark        = EXCLUDED.remark;

COMMIT;

-- 3) 回读（人工核对：policy=0 私有、status=0 默认、桶名/前缀/endpoint 对得上）
--    AK 不打印原值，只看是否为空与长度。
SELECT oss_config_id,
       config_key,
       bucket_name,
       prefix,
       endpoint,
       region,
       access_policy AS policy,          -- 期望 0（私有）
       status        AS is_default,      -- 期望 0（默认）
       CASE WHEN access_key = '' THEN 'EMPTY' ELSE 'SET(len=' || length(access_key) || ')' END AS ak,
       CASE WHEN secret_key = '' THEN 'EMPTY' ELSE 'SET(len=' || length(secret_key) || ')' END AS sk
  FROM sys_oss_config
 ORDER BY oss_config_id;
