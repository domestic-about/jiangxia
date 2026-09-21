package org.dromara.lqg.ext.service;

import org.dromara.lqg.ext.domain.vo.ExtSampleDetailVo;
import org.dromara.lqg.ext.domain.vo.ExtSampleVo;
import org.dromara.lqg.ext.domain.bo.ExtSampleQueryBo;
import org.dromara.common.mybatis.core.page.TableDataInfo;

import java.util.Map;
import java.util.Set;

/**
 * 外部隔离咽喉（ADR-0004）：<b>可见范围只有这一处算</b>。
 *
 * <p>★ 所有 {@code /mp/ext/**} 的查询都必须经本接口：
 * <ul>
 *   <li>列表 → {@link #visibleSampleIds(Long)} 拿到可见样本 id 集合，再交给业务 service 按 id 查；</li>
 *   <li>单条 → {@link #assertVisible(Long, Long)} 先断言，不可见<b>按「不存在」返回</b>
 *       （业务码 404、响应体不带任何样本字段）—— 403 等于告诉对方「这个 id 存在」；</li>
 *   <li>写 → {@link #isMine(Long, Long)} + {@link #editable(Long, Long)}
 *       （本人 且 状态 ∈ {pending, invalid}；同组的可看不可改）。</li>
 * </ul>
 *
 * <p>★ <b>本接口的 ext 包实现是 ext 包里唯一允许持有 {@code *Mapper} 的类</b>
 * （ADR-0004 的 I4，{@code ExtChokepointContractTest#i4} 按类名精确放行
 * {@code ExtScopeServiceImpl}）。别的 ext 类只做拼装与转发。
 *
 * @author AUTH-EXT-001
 */
public interface ExtScopeService {

    /**
     * 可见样本 id 集合（FLOW:F-EXT-01.step1）：
     * 本人提交的 ∪（本人 {@code verified} 时）同 {@code group_id} 且 {@code verified} 的其他外部用户提交的。
     *
     * <p>只含 {@code del_flag='0'} 的样本；<b>不按来源单位名称匹配</b>，只按提交人。
     * 「同单位不同组」与「同组但未核验」都不互看。
     *
     * @param userId 当前登录人
     * @return 可见样本 id 集合（可能为空，**不是 null**）
     */
    Set<Long> visibleSampleIds(Long userId);

    /**
     * 单条可见性断言；不可见抛 {@code ServiceException("样本不存在", 404)}。
     */
    void assertVisible(Long userId, Long sampleId);

    /**
     * 是不是本人提交的（同组别人的 → false；不可见的也 → false）。
     */
    boolean isMine(Long userId, Long sampleId);

    /**
     * 本人提交<b>且</b>状态是 {@code pending} / {@code invalid} 才可改（同组别人的、已核验有效的都 false）。
     */
    boolean editable(Long userId, Long sampleId);

    /**
     * {@code mine} 与 {@code editable} 一次算出来（明细 / 列表逐行都要这两个标记）。
     *
     * <p>为什么要有这个批量方法：{@link #isMine} 与 {@link #editable} 各自都要先算一次可见集合，
     * 逐行调就是 2N 次范围解析；这个方法在一趟里把两个标记都算出来，
     * 且<b>判据与两个单数方法逐字相同</b>（同一处代码，不会哪天改了一边忘了另一边）。
     *
     * @param userId     当前登录人
     * @param sampleIds  要算的样本 id（null / 空 → 空 Map）
     * @return {@code sampleId → (mine, editable)}；不可见的 id 不出现（调用方按「不可见」处理）
     */
    Map<Long, ViewFlags> viewFlags(Long userId, Set<Long> sampleIds);

    /**
     * 单个样本的行内标记（{@code mine} = 本人提交；{@code editable} = 本人 且 pending/invalid）。
     *
     * @param mine     是否本人提交
     * @param editable 是否可改
     */
    record ViewFlags(boolean mine, boolean editable) {
    }

    /**
     * 提交人姓名（外部档案 {@code real_name}）批量解析，给列表拼装用（避免 N+1）。
     *
     * @param userIds 提交人 id 集合（null / 空 → 空 Map）
     * @return {@code userId → real_name}；查不到档案的人不出现在 Map 里
     */
    Map<Long, String> submitterNames(Set<Long> userIds);

}
