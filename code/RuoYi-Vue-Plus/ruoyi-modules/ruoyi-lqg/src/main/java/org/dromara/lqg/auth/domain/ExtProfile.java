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
 * 外部用户档案 t_lqg_ext_profile。
 *
 * <p>一行 = 「这个 sys_user 是外部人员，单位 / 组别与核验状态在这里」。内部人员没有这张表的行
 * （`/mp/me` 的 ext 因此为 null）。`bind_status` 初始 {@code unbound}：外部不设准入，
 * 登录即建行，填单位与核验在 AUTH-EXT-001 / AUTH-GROUP-001。
 *
 * <p>列定义权威：doc/authority/field-ssot.yaml。
 *
 * @author AUTH-LOGIN-001
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_lqg_ext_profile")
public class ExtProfile extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id")
    private Long id;

    /**
     * FK→sys_user.user_id，一人一行（部分唯一索引 uk_ext_profile_user WHERE del_flag='0'）
     */
    private Long userId;

    /**
     * 姓名（外部自填；本票不填，AUTH-EXT-001 才收）
     */
    private String realName;

    /**
     * FK→t_lqg_source_unit.id（自选或核验时归入；该表属 AUTH-GROUP-001）
     */
    private Long unitId;

    /**
     * FK→t_lqg_unit_group.id（该表属 AUTH-GROUP-001）
     */
    private Long groupId;

    /**
     * 单位不在列表时外部自填的单位名
     */
    private String unitNameInput;

    /**
     * 组别不在列表时外部自填的组别名
     */
    private String groupNameInput;

    /**
     * ★ unbound / pending / verified / rejected；只有 verified 才参与「同组互看」
     */
    private String bindStatus;

    /**
     * 核验人 user_id
     */
    private Long verifiedBy;

    /**
     * 核验时间
     */
    private Date verifiedTime;

    /**
     * 驳回原因
     */
    private String rejectReason;

    /**
     * 软删标志
     */
    @TableLogic
    private String delFlag;

}
