package org.dromara.lqg.sys.home.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 工作台首页待办（UI:admin.home，FLOW:F-CRYO-01.step3 / F-SAMPLE-01.step2 / F-DOC-01.step6 / F-EMBED-01.step7）。
 *
 * <pre>
 * GET /lqg/home/todo → {pendingSamples, pendingEmbeds, cryoOverdue, pendingExtUsers, renderFailed}
 * </pre>
 *
 * <p>★★ <b>恰好五个键，一个不多</b>：accept 1 第 1 段用 {@code jq -e '.data == {…五键…}'}
 * 断的是**对象逐字相等** —— 多一个键（哪怕是「已超天数」这种看着有用的）就会红。
 *
 * <p>★ 五个数全部<b>读时计算</b>（ticket §0 口径 1）：本 VO 不是一个持久化对象，
 * 每次请求都由 {@code HomeCounterService} 现算，库里没有任何计数字段、没有缓存。
 *
 * @author SYS-HOME-001
 */
@Data
public class HomeTodoVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 待核验样本数（组织与类器官<b>都算</b>，ticket §0 口径 3）。
     */
    private Long pendingSamples;

    /**
     * 待核验石蜡包埋送样数（CR-20260917-05 之后外部也能交石蜡包埋送样）。
     */
    private Long pendingEmbeds;

    /**
     * -80 冻存超期批次数 —— ★ 出自 CRYO-REMIND-001 的<b>唯一判定</b>
     * （{@code CryoOverdueService#countOverdue()}），本票不重写「14 天 + in_minus80 + 未转液氮 + 剩余>0」。
     */
    private Long cryoOverdue;

    /**
     * 待核验的外部用户档案数（{@code t_lqg_ext_profile.bind_status='pending'}，
     * <b>{@code unbound} 不算</b>：没有档案 / 还没提交单位的不是「待核验」）。
     */
    private Long pendingExtUsers;

    /**
     * 文档渲染失败数 —— {@code t_lqg_doc_file} 里 {@code render_status='failed'} 的
     * (样本, 文档种类, 受众) <b>组数</b>，不是行数（一次失败会写 docx + pdf 两行）。
     */
    private Long renderFailed;

}
