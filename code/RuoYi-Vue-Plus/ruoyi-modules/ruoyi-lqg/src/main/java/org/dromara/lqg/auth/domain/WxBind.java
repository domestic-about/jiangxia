package org.dromara.lqg.auth.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

import java.io.Serial;
import java.util.Date;

/**
 * 微信身份绑定 t_lqg_wx_bind（ADR-0003：微信身份存这里，不动 sys_user 表结构）。
 *
 * <p>一行 = 「某个 openid 属于某个 sys_user，当时微信返回的手机号是 phone」。
 * 同一手机号换了微信号登录 → 同一个 user_id 多一行 openid（`uk_wx_openid` 只约束 openid 不重复）。
 *
 * <p>列定义权威：doc/authority/field-ssot.yaml（本类只是它的 Java 影子）。
 *
 * @author AUTH-LOGIN-001
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_lqg_wx_bind")
public class WxBind extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id")
    private Long id;

    /**
     * FK→sys_user.user_id
     */
    private Long userId;

    /**
     * 小程序 openid（部分唯一索引 uk_wx_openid WHERE del_flag='0'）
     */
    private String openid;

    /**
     * unionid（有就存，没有不强求）
     */
    private String unionid;

    /**
     * ★ 登录当时微信返回的手机号；内外部判定只认它，不认前端传的任何身份字段
     */
    private String phone;

    /**
     * 最近登录时间
     */
    private Date lastLoginTime;

    /**
     * 软删标志（SSOT 公共字段块的 del_flag；本项目不带 tenant_id / del_unique，见 ADR-0009）
     */
    @TableLogic
    private String delFlag;

}
