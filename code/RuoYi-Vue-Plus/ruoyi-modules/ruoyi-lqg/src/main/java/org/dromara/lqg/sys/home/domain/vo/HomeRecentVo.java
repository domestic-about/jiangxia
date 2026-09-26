package org.dromara.lqg.sys.home.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/*
 * 实现备注（给维护的人看，不进接口文档 / Swagger）：
 *
 * 工作台首页「最近提交」一行（UI:admin.home）。
 *
 * <pre>
 * GET /lqg/home/recent → [{submitTime, submitNo, sampleKind, sourceUnitName, submitSource, verifyStatus}, …]（最多 10 行）
 * </pre>
 *
 * <p>★ {@code sampleKind} 是 CR-20260924-10 加的：工作台样本总表拆成「样本记录信息表」「类器官收样记录」
 * 两页后，首页点一行要按类别进对应的那一页。
 *
 * <p>★ 只列<b>未软删</b>的样本：软删行（seed 的 9000001010 / {@code SJ90000010}）不许出现
 * ——accept 1 最后一段就是断这一格。走 {@code SampleMapper} 的 {@code @TableLogic}，
 * 不手写 {@code del_flag='0'}（手写容易在 join / 子查询里漏）。
 *
 * @author SYS-HOME-001
 */
/**
 * 工作台首页「最近提交」一行（最多 10 行，不含已删除的样本）。
 */
@Data
public class HomeRecentVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 提交时间（{@code t_lqg_sample.create_time}） */
    private Date submitTime;

    /** 送检单号（{@code SJ} + 8 位序号） */
    private String submitNo;

    /** 样本类别（{@code tissue} / {@code organoid}）：点一行按它进「样本记录信息表」或「类器官收样记录」 */
    private String sampleKind;

    /** 来源单位名称（内部人员直接录的行是「本中心」，外部是提交时冻结的单位名） */
    private String sourceUnitName;

    /** 内外部（{@code internal} / {@code external}，字典 {@code lqg_submit_source}） */
    private String submitSource;

    /** 核验状态（{@code pending} / {@code valid} / {@code invalid}，字典 {@code lqg_verify_status}） */
    private String verifyStatus;

}
