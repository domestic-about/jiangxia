package org.dromara.lqg.auth.group.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

import java.io.Serial;

/**
 * 组别 t_lqg_unit_group（FLOW:F-AUTH-03.step1，UI:admin.auth.unit 右栏）。
 *
 * <p>挂在来源单位下；同一单位内组别名唯一（部分唯一索引 uk_unit_group (unit_id, group_name)
 * WHERE del_flag='0'），**不同单位可以重名**（A 医院有「肝胆外科组」不影响 B 大学也建一个）。
 * 同单位同组的外部用户互看样本，所以这张表是可见范围的输入之一（可见范围本身在 AUTH-EXT-001 算）。
 *
 * <p><b>不物理删</b>：只启用 / 停用。已有人绑定的组别停用后，已绑定的人不受影响
 * （核验状态与 {@code unit_id}/{@code group_id} 都不会因为停用被动过）。
 *
 * <p>列定义权威：doc/authority/field-ssot.yaml。
 *
 * @author AUTH-GROUP-001
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_lqg_unit_group")
public class UnitGroup extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id")
    private Long id;

    /**
     * FK→t_lqg_source_unit.id
     */
    private Long unitId;

    /**
     * 组别名称（同一单位内唯一）
     */
    private String groupName;

    /**
     * active 启用 / pending 待核验 / disabled 停用
     */
    private String groupStatus;

    /**
     * 备注
     */
    private String remark;

    /**
     * 软删标志
     */
    @TableLogic
    private String delFlag;

}
