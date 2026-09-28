package org.dromara.lqg.sample.guard;

import org.springframework.stereotype.Component;

import java.util.Collection;

/**
 * 下游记录检查的扩展点（ticket §2.2 末条）。
 *
 * <p>「一个样本下面挂了下游记录（石蜡包埋 / 冻存批次 / 质控文档）之后，不许把它改判无效」——
 * 这条判断需要知道下游三张表，而它们分别是 EMBED / CRYO / QC 的票。所以这里只留
 * {@link SampleChildrenChecker} 接口 + 一个聚合器，由后续域在**自己的** ticket 里注册实现：
 * EMBED-MODEL-001、CRYO-MODEL-001、QC-MODEL-001 各注册一个 bean，
 * SAMPLE-VERIFY-001 的核验状态机依赖本类。
 *
 * <p>★ <b>本票注册的实现数 = 0</b>（ticket §2.2 明说）：下游表都还没建，不许在这里探
 * {@code to_regclass} 猜（AUTH-LOGIN-001 的坑 3：PG 会先规划整条语句，{@code CASE} 挡不住缺表）。
 * 没有任何实现时 {@link #hasChildren} 恒 false —— 下游表还不存在，自然没有下游记录。
 *
 * <p>新增实现时不用回来改本类：Spring 会把容器里全部 {@link SampleChildrenChecker} 注进来。
 *
 * @author SAMPLE-MODEL-001
 */
@Component
public class SampleChildrenCheckers {

    /**
     * 容器里注册的全部实现（本票为空）。
     */
    private final Collection<SampleChildrenChecker> checkers;

    public SampleChildrenCheckers(Collection<SampleChildrenChecker> checkers) {
        this.checkers = checkers == null ? java.util.List.of() : checkers;
    }

    /**
     * 该样本名下有没有下游记录：注册的实现里任意一个说「有」就是有。
     *
     * @param sampleId 样本 id
     * @return 有下游记录 → true（本票恒 false：没有实现）
     */
    public boolean hasChildren(Long sampleId) {
        if (sampleId == null) {
            return false;
        }
        for (SampleChildrenChecker checker : checkers) {
            if (checker.hasChildren(sampleId)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 当前注册的实现数（0 = 本票的预期值；给测试与报告取证用）。
     */
    public int registeredCount() {
        return checkers.size();
    }

}
