package org.dromara.lqg.doc.render.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

import java.io.Serial;
import java.util.Date;

/**
 * 渲染产物缓存 {@code t_lqg_doc_file}（FLOW:F-DOC-01.step1/step2，ADR-0005）。
 *
 * <p>★★ <b>一行 = 一份产物</b>，由
 * {@code (sample_id, doc_kind, audience, file_format, page_no)} 上的部分唯一索引
 * {@code uk_doc_file WHERE del_flag='0'} 钉住：
 *
 * <ul>
 *   <li>{@code audience} 在键里 → 内部版与外部版<b>各一行、互不覆盖</b>
 *       （accept 2 断的就是「行数恰好 2」）；</li>
 *   <li>{@code file_format} + {@code page_no} 在键里 → DOC-PDF-001 的 pdf / 每页 png
 *       与这里的 docx 并存，不抢同一行（docx 恒 {@code page_no=0}）。</li>
 * </ul>
 *
 * <p>★ 本票只写 {@code file_format='docx'} 的行；pdf / png 是 DOC-PDF-001 的事。
 *
 * @author DOC-RENDER-001
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_lqg_doc_file")
public class DocFile extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id")
    private Long id;

    /** FK→t_lqg_sample.id */
    private Long sampleId;

    /** sample_qc / organoid_qc / organoid_score / merged，字典 lqg_doc_kind */
    private String docKind;

    /** internal / external，字典 lqg_doc_audience */
    private String audience;

    /** docx / pdf / png，字典 lqg_file_format */
    private String fileFormat;

    /** png 的页码（从 1 起）；docx / pdf 恒为 0 */
    private Integer pageNo;

    /** 产物 FK→sys_oss.oss_id（私有桶；对外只发短时签名链接） */
    private Long ossId;

    /** 算这份产物时的内容指纹；与当下算出来的不一致 = 缓存过期 */
    private String contentHash;

    /** 渲染所用模板版本号 */
    private String templateVersion;

    /** pending / done / failed，字典 lqg_render_status */
    private String renderStatus;

    /** 失败原因（工作台可见、可重试） */
    private String errorMsg;

    /** 生成完成时间 */
    private Date renderedTime;

    /** 软删标志（{@code @TableLogic}） */
    @TableLogic
    private String delFlag;
}
