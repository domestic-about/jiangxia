-- CR-20261005-16 · 工作台对抗性 UX 测试 WEB-08：核验状态的标签颜色。
--
-- 字典 lqg_verify_status 的 list_class 一直是空，工作台 DictTag 落到默认的 primary ——
-- 「待核验 / 有效 / 无效」三种状态都是同一个主色青绿，列表里一眼分不出来。
-- 照 lqg-tokens.scss §B 的语义（待核验 = warn、有效 = ok、无效 = danger）补上；
-- 只改「仍是空值」的行，甲方 / 我们在后台改过的不覆盖。
UPDATE sys_dict_data SET list_class = 'warning', update_time = now()
 WHERE dict_type = 'lqg_verify_status' AND dict_value = 'pending' AND coalesce(list_class, '') = '';
UPDATE sys_dict_data SET list_class = 'success', update_time = now()
 WHERE dict_type = 'lqg_verify_status' AND dict_value = 'valid' AND coalesce(list_class, '') = '';
UPDATE sys_dict_data SET list_class = 'danger', update_time = now()
 WHERE dict_type = 'lqg_verify_status' AND dict_value = 'invalid' AND coalesce(list_class, '') = '';
