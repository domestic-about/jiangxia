-- ════════════════════════════════════════════════════════════════════════════════
-- AUTH-EXT-002 · 系统参数 lqg.ext.show-internal-no 的幂等兜底（CR-20260918-07）
-- ════════════════════════════════════════════════════════════════════════════════
-- 本票（外部石蜡包埋送样：提交 / 改后重提 / 可见范围内的列表与详情）要读这个参数决定
-- 「外部样本详情给不给内部编号那一行」。参数本体由 SYS-WEB-001 的 V202609210830 插入
-- （config_id=5001，默认 false，UI:admin.config 的「参数设置」页改它），本票只读它。
--
-- 这一支是**幂等兜底**：单独把本票的迁移打到新库 / 有人手工删过这行时也能读到，
-- 已经存在就跳过 —— **绝不覆盖已有值**（甲方可能已经在工作台里把开关改成 true 了，
-- 覆盖回去等于把人家打开的开关悄悄关掉）。
--
-- 取号依据 doc/lint-profile.yaml：D3 = 20260923；AUTH 域 = 09xx；0902 在本域未被占用。
-- ★ 0902 < 已应用的 202609231100（EMBED-MODEL-001），而 spring.flyway.out-of-order=false
--   → 直接启动会 FlywayValidateException。处置（issue #12 的既知形态，SYS-WEB-001 先例）：
--   重建 dev 库让 Flyway 按版本号顺序全量重跑，**不改 out-of-order 配置**（prod 语义要保持）。
-- ════════════════════════════════════════════════════════════════════════════════

INSERT INTO sys_config (config_id, tenant_id, config_name, config_key, config_value, config_type,
                        create_dept, create_by, create_time, remark)
SELECT 5001, '000000', '外部页面显示内部编号', 'lqg.ext.show-internal-no', 'false', 'N',
       103, 1, now(),
       'CR-20260918-07：false = 外部接口里连这个键都不出；true = 外部样本详情多显示一行内部编号（AUTH-EXT-002 运行时读它）'
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'lqg.ext.show-internal-no');

COMMIT;
