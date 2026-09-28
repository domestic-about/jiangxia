package org.dromara.lqg.doc.render;

import org.dromara.common.core.exception.ServiceException;

/**
 * 文档版本（字典 {@code lqg_doc_audience}，FIELD:t_lqg_doc_file.audience）。
 *
 * <p>★★ <b>两份独立缓存的产物，不是在下载时临时抹</b>（ticket §0 口径复述 2）：
 * 内部版一直印内部编号；外部版那一格<b>按系统参数 {@code lqg.ext.show-internal-no} 决定，默认留空</b>。
 * {@code audience} 进内容指纹、进对象键路径，所以「谁后渲染谁覆盖」在结构上不可能发生。
 *
 * <p>★ 甲方 2026-09-24 意见第 23 行起，这个开关<b>也管外部版文档</b>（此前 CR-20260918-07 只管页面数据）：
 * 「外部版这一格印什么」进内容指纹（{@code DocRenderModel#isInternalNoShown}），切换后已完成的文档
 * 在后台按新设置重出；开关关着时，印了内部编号的外部版在任何路径上都不给出去
 * （{@code t_lqg_doc_file.show_internal_no} + {@code DocRenderService#deliverable}）。
 *
 * @author DOC-RENDER-001 · G 批 C 组（内部编号开关作用到外部版文档）
 */
public final class DocAudiences {

    private DocAudiences() {
    }

    /** 内部版：含内部编号。 */
    public static final String INTERNAL = "internal";
    /** 外部版：内部编号一格按系统参数 {@code lqg.ext.show-internal-no}（默认关 = 留空）。 */
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
