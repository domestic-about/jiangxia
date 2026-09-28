package org.dromara.lqg.qc.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 图片位里的一张图（{@code t_lqg_doc_image} 的一行 + 读时带出的两个 URL）。
 *
 * <p>★ {@code url} = 原图（预览页点开看的就是它）；{@code previewUrl} = 进文档的预览图
 * （长边 ≤ 2000px 的 JPEG）。两者相同时（无需转换）指向同一个 oss。
 *
 * <p>★ 大 id 一律走 Jackson 的数字类型；小程序侧比较时记得 {@code tostring}
 * （doc/verify/README.md 的坑表第 5 条）。
 *
 * @author QC-MODEL-001
 */
@Data
@Schema(description = "质控文档图片位里的一张图")
public class DocImageVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "主键")
    private Long id;

    @Schema(description = "orig / observe / pretreat / organoid_observe")
    private String slot;

    @Schema(description = "原图 oss_id")
    private Long ossId;

    @Schema(description = "预览图 oss_id（无需转换时等于 ossId）")
    private Long previewOssId;

    @Schema(description = "同一图片位内的顺序")
    private Integer sort;

    @Schema(description = "原图 URL（读时从 sys_oss 带出）")
    private String url;

    @Schema(description = "预览图 URL（读时从 sys_oss 带出）")
    private String previewUrl;

}
