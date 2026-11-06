-- CR-20261106-25 · 验收问题：超管默认口令改成 csradmin123；小程序登录态保留 30 天
--
-- 1) 只把「仍是上游默认 admin123」的超管口令改成 csradmin123。
--    已经人工改过强口令的环境不会被这条迁移覆盖（避免一次发版把生产强口令打回测试口令）。
--    admin123 的 BCrypt 值来自 V202609210800 基线的 admin 行；csradmin123 的哈希由
--    Spring Security BCryptPasswordEncoder（cost 10）生成，与其它账号同一套校验。
UPDATE sys_user
   SET password    = '$2a$10$S6Aj6OlaDPaEl8bipOpW6e5MgmSVDy7SxqOojEgkLhyHA.iYAfI9W',
       update_by   = 1,
       update_time = now()
 WHERE user_name = 'admin'
   AND del_flag  = '0'
   AND password  = '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2';

-- 2) 小程序登录态：原来 active_timeout=1800 秒（30 分钟不操作就失效），
--    验收反馈「隔一天进入就要重新登录」。小程序是长期使用的工具，改成 30 天不操作才失效；
--    固定 timeout 一并拉到 30 天，避免第 7 天被绝对超时踢掉。工作台 pc 客户端不动。
UPDATE sys_client
   SET active_timeout = 2592000,
       timeout        = 2592000,
       update_by      = 1,
       update_time    = now()
 WHERE client_key = 'mp'
   AND del_flag   = '0';
