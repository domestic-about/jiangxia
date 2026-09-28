package org.dromara.lqg.sys.home.domain.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/*
 * 实现备注（给维护的人看，不进接口文档 / Swagger）：
 *
 * 首页「渲染失败与缺图」清单的一行（UI:admin.home 的渲染失败卡片点开，独立验收 V29）。
 *
 * <pre>
 * GET /lqg/home/render-issues → [{sampleId, internalNo, submitNo, sourceUnitName, docKind, audience,
 *                                 issue, errorMsg, missingImageCount, missingImages, time}, …]
 * </pre>
 *
 * <p>★ 一行 = 一组 (样本, 文档种类, 受众)，与首页 {@code renderFailed} 同一个口径
 * （{@code DocFileMapper.RENDER_ISSUE_WHERE}）：卡片上的数 == 这张清单的行数。
 * {@code issue} = {@code failed}（渲染失败，含外部版缺图）/ {@code missing_images}（内部版照出但缺图）。
 *
 * <p>只给内部人员（与首页同一道内部角色闸）；外部接口不会返回这里的任何字段。
 *
 * @author 独立验收 V29 修复
 */
/**
 * 首页「渲染失败与缺图」清单的一行：一份文档的一个版本（内部版 / 外部版），带失败原因或缺了哪几张图。
 *
 * <p>issue = failed（渲染失败，含外部版缺图）/ missing_images（内部版照出但缺图）；与首页 renderFailed 同一个口径。
 */
@Data
public class HomeRenderIssueVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 样本 id（点行进该样本的质控页用） */
    private Long sampleId;

    /** 内部编号 */
    private String internalNo;

    /** 送检单号 */
    private String submitNo;

    /** 来源单位 */
    private String sourceUnitName;

    /** sample_qc / organoid_qc / organoid_score / merged */
    private String docKind;

    /** internal / external */
    private String audience;

    /** failed（渲染失败）/ missing_images（内部版照出但缺图） */
    private String issue;

    /** 失败原因（failed 时） */
    private String errorMsg;

    /** 缺图张数 */
    private Integer missingImageCount;

    /** 缺了哪几张 */
    private String missingImages;

    /** 出问题的时间（这一版最后一次写入） */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date time;

}
