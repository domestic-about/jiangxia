package org.dromara.lqg.sample.guard;

/**
 * 「这个样本名下有没有下游记录」的扩展点（ticket §2.2 末条）。
 *
 * <p>EMBED / CRYO / QC 建模时各自注册一个实现（石蜡块 / 冻存批次 / 质控文档），
 * SAMPLE-VERIFY-001 的「有下游记录不许改判无效」靠它。本票（SAMPLE-MODEL-001）注册零个实现。
 *
 * <p>实现方约定：**只读**，不抛业务异常，查不到就 false。
 *
 * @author SAMPLE-MODEL-001
 */
@FunctionalInterface
public interface SampleChildrenChecker {

    /**
     * @param sampleId 样本 id
     * @return 有下游记录 → true
     */
    boolean hasChildren(Long sampleId);
}
