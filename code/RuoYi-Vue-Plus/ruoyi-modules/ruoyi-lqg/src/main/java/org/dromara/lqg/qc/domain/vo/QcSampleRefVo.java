package org.dromara.lqg.qc.domain.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;
import java.util.Date;

/**
 * 质控文档编辑页上<b>从样本主档带出的只读字段</b>（FLOW:F-QC-01.step1）。
 *
 * <p>★ <b>这七项不在质控表里重复存</b>（ticket §0 口径复述 1）：
 * 来源单位、患者姓名、性别、收样时间、处理时间、操作人、内部编号 —— 一律读样本主档，
 * 编辑页只读展示。{@code GET /lqg/qc/{sampleId}} 的 {@code data.sample} 就是本对象
 * （accept 3 第 1 段断 {@code .data.sample.internalNo}）。
 *
 * <p>★ {@code donorName} 在这里是<b>明文</b>（ADR-0006：工作台内部人员看明文）——
 * 库里的密文由 service 用 {@code SampleFieldCipher} 解出来。
 *
 * <p>★ 只带「编辑页要显示的那几项」，不是整个 {@code SampleVo}：
 * 质控文档不需要样本的提交人 / 审核 / 备注等列。
 *
 * @author QC-MODEL-001
 */
@Data
@Schema(description = "质控文档页的样本主档只读字段")
public class QcSampleRefVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "样本 id")
    private Long id;

    @Schema(description = "送检单号")
    private String submitNo;

    @Schema(description = "tissue / organoid")
    private String sampleKind;

    @Schema(description = "内部编号（只读带出）")
    private String internalNo;

    @Schema(description = "来源单位名称（只读带出）")
    private String sourceUnitName;

    @Schema(description = "患者姓名（明文，只读带出）")
    private String donorName;

    @Schema(description = "性别（只读带出）")
    private String gender;

    @Schema(description = "收样时间（只读带出）")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate receiveDate;

    @Schema(description = "处理时间（只读带出）")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date processTime;

    @Schema(description = "操作人（只读带出）")
    private String operatorName;

}
