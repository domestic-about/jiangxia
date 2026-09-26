package org.dromara.lqg.qc.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

import java.io.Serial;

/**
 * 质控文档的通用附件 t_lqg_doc_attachment（REQ-QC-010，FIELD:t_lqg_doc_attachment.*）。
 *
 * <p>★ <b>通用附件不进 Word 正文</b>，只在预览页下方列出（类注释照 SSOT）；
 * 样本质控表上另有一栏「细胞活率测定」是<b>单独一列</b>
 * （{@code t_lqg_qc_sample.viability_oss_id} + {@code viability_file_name}），
 * <b>不走本表</b> —— 那一格在 Word 里嵌入附件本身（图标 + 文件名，双击打开，{@code DocOleEmbedder}）。
 *
 * <p>★ 三份文档都能挂附件（{@code doc_type} 含 {@code organoid_score}）；
 * <b>图片位</b>只有两份（{@code organoid_score} 没有 slot）。
 *
 * @author QC-MODEL-001
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_lqg_doc_attachment")
public class DocAttachment extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id")
    private Long id;

    /**
     * sample_qc / organoid_qc / organoid_score，字典 lqg_doc_type
     */
    private String docType;

    /**
     * 对应质控文档表的主键
     */
    private Long docId;

    /**
     * FK→sys_oss.oss_id
     */
    private Long ossId;

    /**
     * 原始文件名
     */
    private String fileName;

    /**
     * 字节数（单个 ≤ 50MB；SSOT 的 int → PG integer，故用 Integer）
     */
    private Integer fileSize;

    /**
     * 显示顺序
     */
    private Integer sort;

    /**
     * 软删标志（{@code @TableLogic}）
     */
    @TableLogic
    private String delFlag;

}
