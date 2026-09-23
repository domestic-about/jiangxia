package org.dromara.lqg.doc.render;

import org.dromara.common.core.exception.ServiceException;

/**
 * 文档版本（字典 {@code lqg_doc_audience}，FIELD:t_lqg_doc_file.audience）。
 *
 * <p>★★ <b>两份独立缓存的产物，不是在下载时临时抹</b>（ticket §0 口径复述 2）：
 * 内部版有内部编号，外部版那一格<b>一律留空</b>。{@code audience} 进内容指纹、进对象键路径，
 * 所以「谁后渲染谁覆盖」在结构上不可能发生 —— 外部永远取不到带内部编号的那一份。
 *
 * <p>★ CR-20260918-07 的系统参数 {@code lqg.ext.show-internal-no} 只管**页面数据**，
 * 不管这里的预渲染产物：开关切换不会重出历史文档，外部版文档里那一格仍然是空的
 * （范围已在 CR 里写死；要放开另记变更）。
 *
 * @author DOC-RENDER-001
 */
public final class DocAudiences {

    private DocAudiences() {
    }

    /** 内部版：含内部编号。 */
    public static final String INTERNAL = "internal";
    /** 外部版：内部编号一格留空。 */
    public static final String EXTERNAL = "external";

    /**
     * 两个版本的顺序（DOC-PUBLISH-001「完成并同步」要一次把内部版与外部版都排进渲染，
     * 顺序固定便于日志与探针复读）。
     */
    public static final java.util.List<String> ALL = java.util.List.of(INTERNAL, EXTERNAL);

    /**
     * 校验；不认识的一律 400。
     */
    public static String require(String audience) {
        if (!INTERNAL.equals(audience) && !EXTERNAL.equals(audience)) {
            throw new ServiceException("文档版本只能是 internal / external，收到：" + audience, 400);
        }
        return audience;
    }

    public static boolean isInternal(String audience) {
        return INTERNAL.equals(audience);
    }
}
