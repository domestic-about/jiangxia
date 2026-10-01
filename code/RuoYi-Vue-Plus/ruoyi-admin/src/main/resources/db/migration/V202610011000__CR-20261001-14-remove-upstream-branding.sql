-- CR-20261001-14 · 飞书《测试问题记录表》「网页工作台」row20：去掉后台所有 RuoYi-Vue-Plus 相关的标识，用「类器官样本」替代。
--
-- 这里只处理**数据库里**带上游框架印记的种子数据（代码 / 配置里的欢迎语、接口文档标题、favicon 在同一 CR 里改）：
--   1. 超级管理员 admin 的昵称「疯狂的狮子Li」、上游作者的邮箱和占位手机号 → 换成本系统的称呼、清空联系方式
--   2. 顶级菜单 4「PLUS官网」（外链 gitee.com/dromara/RuoYi-Vue-Plus）→ 删除（连同角色授权）
--   3. 通知公告 1、2（上游演示：「新版本发布啦」「系统凌晨维护」）→ 删除
--   4. 默认租户 000000 的「XXX有限公司 / 多租户通用后台管理管理系统」、根部门「XXX科技」→ 换成本系统名称
--
-- ★ 每条都带「仍是上游原值」的条件：已经被人改过的（例如有人手动改了 admin 昵称）不覆盖。可重复执行。

-- 1. admin 账号
UPDATE sys_user
   SET nick_name = '系统管理员', email = '', phonenumber = ''
 WHERE user_id = 1
   AND nick_name = '疯狂的狮子Li';

-- 2. 「PLUS官网」外链菜单
DELETE FROM sys_role_menu WHERE menu_id = 4
   AND EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 4 AND path LIKE '%RuoYi-Vue-Plus%');
DELETE FROM sys_menu WHERE menu_id = 4 AND path LIKE '%RuoYi-Vue-Plus%';

-- 3. 上游演示公告
DELETE FROM sys_notice
 WHERE (notice_id = 1 AND notice_title LIKE '温馨提醒：2018-07-01%')
    OR (notice_id = 2 AND notice_title LIKE '维护通知：2018-07-01%');

-- 4. 默认租户与根部门
UPDATE sys_tenant
   SET company_name = '类器官样本管理', intro = '类器官样本管理系统'
 WHERE tenant_id = '000000'
   AND company_name = 'XXX有限公司';

UPDATE sys_dept
   SET dept_name = '类器官样本管理'
 WHERE dept_id = 100
   AND dept_name = 'XXX科技';
