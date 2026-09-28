package org.dromara.lqg.cryo.batch.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

import java.io.Serial;
import java.time.LocalDateTime;

/**
 * 冻存出入库流水 t_lqg_cryo_flow（FLOW:F-CRYO-02，FIELD-ssot 的 t_lqg_cryo_flow）。
 *
 * <p>★ <b>追溯全靠这张表</b>：批次上只存初始支数，取走 / 补入 / 盘点调整各记一笔带符号的
 * {@code delta}，剩余永远 = 初始 + Σ(未删 delta)。删一笔登记是<b>软删</b>（{@code del_flag='1'}），
 * 所以「谁在什么时候从哪拿了几支」删掉之后仍然查得到。
 *
 * <p>★ 本票（CRYO-MODEL-001）只<b>建表 + 读</b>：写流水、改删登记、转液氮的接口在
 * CRYO-FLOW-001（ticket §3 边界）。本票的读用途只有一个 —— 算剩余（{@code CryoQueryService}）
 * 与改初始支数前的逐笔校验（{@code CryoBatchService.update}）。
 *
 * <p>★ {@code delta} 的符号就是语义（{@code take} 恒负 / {@code add} 恒正 / {@code adjust} 可正可负），
 * 不靠 {@code flow_type} 现算加减 —— 否则任何写报表的地方 {@code SUM(delta)} 就全错了
 * （CRYO-FLOW-001 accept 1 的 counterfeit 点名这一形态）。
 *
 * @author CRYO-MODEL-001
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_lqg_cryo_flow")
public class CryoFlow extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id")
    private Long id;

    /**
     * FK→t_lqg_cryo_batch.id
     */
    private Long batchId;

    /**
     * take 取走 / add 补入 / adjust 盘点调整（字典 {@code lqg_cryo_flow_type}）
     */
    private String flowType;

    /**
     * 带符号变化量：take 恒为负、add 恒为正、adjust 可正可负且不为 0
     */
    private Integer delta;

    /**
     * minus80 / ln2（字典 {@code lqg_cryo_location}）：由批次当时所在位置自动带出，不让人选
     */
    private String fromLocation;

    /**
     * 经手人（默认当前登录人）
     */
    private String operatorName;

    /**
     * 发生时间（默认当前；逐笔校验的排序键，同一时刻按 id）
     */
    private LocalDateTime flowTime;

    /**
     * 用途 / 原因（{@code adjust} 必填）
     */
    private String purpose;

    /**
     * 软删标志（{@code @TableLogic}：软删的登记不计入剩余，也查不到）
     */
    @TableLogic
    private String delFlag;

}
