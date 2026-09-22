package org.dromara.lqg.qc.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

import java.io.Serial;

/**
 * 质控文档的图片位 t_lqg_doc_image（REQ-QC-003 / 006，FIELD:t_lqg_doc_image.*）。
 *
 * <p>★ <b>一条记录 = 一个图片位里的一张图</b>：{@code doc_type} + {@code doc_id} 指向三份
 * 文档里的哪一份，{@code slot} 指向该文档的哪个图片位（取值见字典 {@code lqg_image_slot}），
 * {@code sort} 是同一个位内的顺序。归属与每位至多 3 张的规则在
 * {@code QcDocRules}（Java），不在这张表上。
 *
 * <p>★ {@code previewOssId} 与 {@code ossId} 分开存：非 jpg / png（TIFF、BMP…）或长边
 * &gt; 2000px 的图另存一份长边 ≤ 2000px 的 JPEG，进 Word 的也是它；其余情况两者相等。
 * 判定归 {@code QcImagePreviewResolver}。
 *
 * <p>★ 软删用 {@code @TableLogic}（DELETE 接口不是物理删）：accept 3 的库里断言带着
 * {@code del_flag='0'}。
 *
 * @author QC-MODEL-001
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_lqg_doc_image")
public class DocImage extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id")
    private Long id;

    /**
     * sample_qc / organoid_qc（评分表没有图片位），字典 lqg_doc_type
     */
    private String docType;

    /**
     * 对应质控文档表的主键（t_lqg_qc_sample.id / t_lqg_qc_organoid.id）
     */
    private Long docId;

    /**
     * orig / observe / pretreat（样本质控表）｜organoid_observe（类器官质控表），字典 lqg_image_slot
     */
    private String slot;

    /**
     * 原图 FK→sys_oss.oss_id（预览页点开看的就是它）
     */
    private Long ossId;

    /**
     * 预览图 FK→sys_oss.oss_id（长边 ≤ 2000px 的 JPEG）；无需转换时等于 {@code ossId}
     */
    private Long previewOssId;

    /**
     * 同一图片位内的顺序
     */
    private Integer sort;

    /**
     * 软删标志（{@code @TableLogic}）
     */
    @TableLogic
    private String delFlag;

}
