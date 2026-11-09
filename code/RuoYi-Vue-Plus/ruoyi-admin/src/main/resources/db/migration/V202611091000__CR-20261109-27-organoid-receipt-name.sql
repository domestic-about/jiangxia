-- 飞书小程序行27 / 网页工作台行23：两端统一使用「类器官收样记录」。
-- 仅改仍为旧标准名的菜单，不覆盖使用方自定义名称；历史迁移保持不变。
UPDATE sys_menu
   SET menu_name = '类器官收样记录', update_time = now()
 WHERE menu_id = 5220
   AND menu_name = '类器官送样记录';
