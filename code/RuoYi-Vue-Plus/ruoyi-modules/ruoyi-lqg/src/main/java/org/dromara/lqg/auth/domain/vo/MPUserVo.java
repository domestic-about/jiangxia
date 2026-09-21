package org.dromara.lqg.auth.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 当前身份视图 —— {@code GET /mp/me} 的 data（形状权威：doc/api-contract.md「AUTH」一节）。
 *
 * @author AUTH-LOGIN-001
 */
@Data
@Schema(description = "小程序当前身份")
public class MPUserVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "用户 id")
    private Long userId;

    @Schema(description = "姓名")
    private String name;

    @Schema(description = "手机号掩码（中间四位 ****）")
    private String phoneMasked;

    /**
     * internal / external。判定只认**账号角色**（带 lqg_internal 或 lqg_admin → internal），
     * 不认 user_type、更不认请求体里传的任何字段（ADR-0003）。
     */
    @Schema(description = "身份：internal / external")
    private String identity;

    /**
     * 外部档案；内部人员为 null。
     */
    @Schema(description = "外部档案（仅外部）")
    private ExtProfileVo ext;

}
