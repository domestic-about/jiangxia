package org.dromara.lqg.embed.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

import java.io.Serial;

/**
 * 石蜡块的 marker 表达 t_lqg_embed_marker（FIELD-ssot 的 t_lqg_embed_marker）。
 *
 * <p>★ <b>一个蜡块可测多个 marker，所以它单独一张表</b>：主表 t_lqg_embed 上**没有**
 * {@code marker_name} / {@code expression} 两列（accept 1 的 counterfeit 点名这一形态）。
 *
 * <p>★ {@code markerName} 可空（只记表达情况、不写名称也允许），{@code expression} 必填且落在字典
 * {@code lqg_marker_expr}（negative / weak / strong）。写侧语义是「<b>整组替换</b>」：
 * 先软删旧的、再插新的，同一事务（ticket §2）。
 *
 * @author EMBED-MODEL-001
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_lqg_embed_marker")
public class EmbedMarker extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id")
    private Long id;

    /**
     * FK→t_lqg_embed.id
     */
    private Long embedId;

    /**
     * marker 名称（可空：只记表达情况不写名称也允许）
     */
    private String markerName;

    /**
     * negative 阴性 / weak 弱表达 / strong 强表达（字典 lqg_marker_expr，按钮单选）
     */
    private String expression;

    /**
     * 显示顺序（请求里的数组下标）
     */
    private Integer sort;

    /**
     * 软删标志（整组替换时这里置 1）
     */
    @TableLogic
    private String delFlag;

}
