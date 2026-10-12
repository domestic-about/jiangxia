package org.dromara.lqg.auth.staff.domain.vo;

import lombok.Data;

/**
 * 「选人」下拉 / 弹框的一项（CR-20261012-22）：只有账号 id 与姓名 —— 不带手机号、角色。
 *
 * @author CR-20261012-22
 */
@Data
public class StaffOptionVo {

    /**
     * 账号 id
     */
    private Long userId;

    /**
     * 姓名（sys_user.nick_name）
     */
    private String name;

}
