package org.dromara.lqg.sys.home.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/*
 * 实现备注（给维护的人看，不进接口文档 / Swagger）：
 *
 * 工作台首页待办（UI:admin.home，FLOW:F-CRYO-01.step3 / F-SAMPLE-01.step2 / F-DOC-01.step6 / F-EMBED-01.step7）。
 *
 * <pre>
 * GET /lqg/home/todo → {pendingSamples, pendingTissue, pendingOrganoid, pendingEmbeds, cryoOverdue,
 *                       pendingExtUsers, renderFailed}
 * </pre>
 *
 * <p>★★ <b>恰好七个键，一个不多</b>：原来是五个；CR-20260924-10（甲方 2026-09-24 第 25 行：组织样本与
 * 类器官样本分成两张表）把工作台样本总表拆成两页，首页「待核验样本」一张卡也拆成两张，于是加了
 * {@code pendingTissue} / {@code pendingOrganoid}。{@code pendingSamples} <b>保留</b>且恒等于两者之和
 * （小程序首页用它）。契约测试断字段集逐字相等 —— 再多一个键（哪怕看着有用）也会红。
 *
 * <p>★ 五个数全部<b>读时计算</b>（ticket §0 口径 1）：本 VO 不是一个持久化对象，
 * 每次请求都由 {@code HomeCounterService} 现算，库里没有任何计数字段、没有缓存。
 *
 * @author SYS-HOME-001
 */
/**
 * 工作台首页待办：{pendingSamples, pendingTissue, pendingOrganoid, pendingEmbeds, cryoOverdue, pendingExtUsers,
 * renderFailed}，每次请求现算；pendingSamples = pendingTissue + pendingOrganoid。
 */
@Data
public class HomeTodoVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 待核验样本数（组织与类器官都算）= {@link #pendingTissue} + {@link #pendingOrganoid}。
     * 工作台首页已拆成两张卡，这个数留给小程序首页用（别的端依赖它，不能删）。
     */
    private Long pendingSamples;

    /**
     * 待核验的样本记录信息表（组织样本，{@code sample_kind='tissue'}）—— 工作台首页一张卡、
     * 侧边菜单「样本记录信息表」的角标。
     */
    private Long pendingTissue;

    /**
     * 待核验的类器官收样记录（{@code sample_kind='organoid'}）—— 工作台首页一张卡、
     * 侧边菜单「类器官收样记录」的角标。
     */
    private Long pendingOrganoid;

    /**
     * 待核验石蜡包埋送样数（外部也能交石蜡包埋送样）。
     */
    private Long pendingEmbeds;

    /**
     * -80 冻存超期批次数（与冻存管理的超期清单同一个判定）。
     */
    private Long cryoOverdue;

    /**
     * 待核验的外部用户档案数（{@code t_lqg_ext_profile.bind_status='pending'}，
     * <b>{@code unbound} 不算</b>：没有档案 / 还没提交单位的不是「待核验」）。
     */
    private Long pendingExtUsers;

    /**
     * 文档渲染异常数：渲染失败 + 内部版照出但缺图，按（样本, 文档种类, 版本）组数计。
     */
    private Long renderFailed;

}
