package org.dromara.lqg.sample.hint.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 一条 GROUP BY 的原始结果行（SAMPLE-HINT-001）：一个样本一行。
 *
 * <p>刻意与 {@link SampleHintVo} 分开：这里是 <b>SQL 的列形状</b>（染色还是一个逗号串，
 * 由 {@code STRING_AGG} 拼出来），{@link SampleHintVo} 是 <b>对外形状</b>（染色是数组）。
 * 合并成一个类就得在 VO 上挂一个「只给 mapper 用」的 {@code stainCsv} 字段，对外会多一个键。
 *
 * @author SAMPLE-HINT-001
 */
@Data
public class SampleHintRow implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 样本 id（GROUP BY 的键） */
    private Long sampleId;

    /** 这个样本名下<b>已核验有效、未软删</b>的石蜡块数 */
    private Integer blockCount;

    /** 其中有没有一块切片时间非空 */
    private Boolean sectioned;

    /** 各块 {@code stain_types} 用逗号拼起来的串（整列都空时是 null） */
    private String stainCsv;

}
