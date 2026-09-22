package org.dromara.lqg.ocr.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * 识别结果 —— {@code POST /mp/ocr/recognize} 的 data（形状权威：doc/api-contract.md「OCR」一节）。
 *
 * <p>★ {@code fields} 里<b>解析不出的键不出现</b>（不是空串）：前端只按 present 的键预填，
 * 「有键但值为空」会被当成识别出一个空值照填（accept 1 的 counterfeit 第 4 条）。
 *
 * <p>★ 识别结果<b>只回给前端预填</b>，不入库、不建识别记录表（ADR-0007、ticket §3）。
 *
 * @author OCR-IMPL-001
 */
@Data
@Schema(description = "拍照识别结果")
public class OcrRecognizeVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 识别服务返回的原始文本行（连同字段一起给前端，让填写人能对照着手填）。
     */
    @Schema(description = "原始文本行（识别服务原样返回，供人工核对）")
    private List<String> rawLines;

    /**
     * 解析出的预填字段。可能出现的键：{@code donorName / gender / age / hospitalNo / tissueType / sourceUnitName}；
     * 认不出的键不出现。
     */
    @Schema(description = "解析出的字段（认不出的键不出现）")
    private Map<String, String> fields;

}
