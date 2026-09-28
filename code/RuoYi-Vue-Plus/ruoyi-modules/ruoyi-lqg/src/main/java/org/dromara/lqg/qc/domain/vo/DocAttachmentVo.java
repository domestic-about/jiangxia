package org.dromara.lqg.qc.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 质控文档的一条通用附件（{@code t_lqg_doc_attachment} 的一行 + 读时带出的 URL）。
 *
 * @author QC-MODEL-001
 */
@Data
@Schema(description = "质控文档的通用附件")
public class DocAttachmentVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "主键")
    private Long id;

    @Schema(description = "文件 oss_id")
    private Long ossId;

    @Schema(description = "原始文件名")
    private String fileName;

    @Schema(description = "字节数")
    private Integer fileSize;

    @Schema(description = "显示顺序")
    private Integer sort;

    @Schema(description = "下载 URL（读时从 sys_oss 带出）")
    private String url;

}
