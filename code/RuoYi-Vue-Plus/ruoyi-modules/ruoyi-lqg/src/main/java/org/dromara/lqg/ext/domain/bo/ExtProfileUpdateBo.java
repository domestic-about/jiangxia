package org.dromara.lqg.ext.domain.bo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 外部填 / 改姓名、单位、组别的入参 —— {@code PUT /mp/ext/profile}
 * （doc/api-contract.md，FLOW:F-AUTH-03.step2 / step4，UI:mp.me.profile）。
 *
 * <p>两套写法**互斥**：
 * <ul>
 *   <li>选了列表项 → {@code unitId + groupId}</li>
 *   <li>列表里没有 → {@code unitNameInput + groupNameInput}（「手动填写」那一项）</li>
 * </ul>
 * 选了列表项就清空自填项 —— 服务端**两套都不接受**（要么只给一套，要么以列表项为准并清自填），
 * 绝不留着「既有 unit_id 又同时显示一个自填名」的档案。
 *
 * @author AUTH-GROUP-001
 */
@Data
@Schema(description = "外部档案填写 / 修改入参")
public class ExtProfileUpdateBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 姓名（必填；外部唯一能自己改的身份信息）
     */
    @Size(max = 100, message = "姓名不能超过 100 字")
    private String realName;

    /**
     * 选中的单位 id（列表里选的）
     */
    private Long unitId;

    /**
     * 选中的组别 id（必须属于上面的单位）
     */
    private Long groupId;

    /**
     * 单位不在列表时自填的单位名
     */
    @Size(max = 100, message = "单位名称不能超过 100 字")
    private String unitNameInput;

    /**
     * 组别不在列表时自填的组别名
     */
    @Size(max = 100, message = "组别名称不能超过 100 字")
    private String groupNameInput;

}
