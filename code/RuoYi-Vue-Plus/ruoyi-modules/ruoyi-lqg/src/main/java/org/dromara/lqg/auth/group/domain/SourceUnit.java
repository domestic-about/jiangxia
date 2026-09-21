package org.dromara.lqg.auth.group.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

import java.io.Serial;

/**
 * 来源单位 t_lqg_source_unit（FLOW:F-AUTH-03.step1，UI:admin.auth.unit 左栏）。
 *
 * <p>实验室在工作台维护；外部自填的新单位在核验通过、核验人点「新建」时才落成 {@code active}。
 * <b>不物理删</b>：只 {@code active ↔ disabled} 切换（{@code unit_status}），
 * 已停用的单位不再出现在小程序选择器里，但已绑定的人不受影响。
 *
 * <p>列定义权威：doc/authority/field-ssot.yaml。
 *
 * @author AUTH-GROUP-001
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_lqg_source_unit")
public class SourceUnit extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id")
    private Long id;

    /**
     * 单位名称（全库唯一：部分唯一索引 uk_unit_name WHERE del_flag='0'）
     */
    private String unitName;

    /**
     * active 启用 / pending 待核验（外部自填）/ disabled 停用
     */
    private String unitStatus;

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
