package org.dromara.lqg.doc.render;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * 渲染时取不到字节的一张图（#217 的「缺图」）。
 *
 * <p>★ 只在渲染链路里产生（{@link DocxRenderer#render(DocRenderModel, List)} 往里收），
 * 由 {@code DocRenderService} 落到 header 行的 {@code missing_image_count / missing_images}：
 * 内部版照出并记缺图；外部版有缺图直接 failed。
 *
 * @param docKind 哪一份文档（sample_qc / organoid_qc；合并件里记的是成员自己的种类）
 * @param slot    图片位（字典 lqg_image_slot：orig / observe / pretreat / organoid_observe）
 * @param index   该位里的第几张（从 1 起）
 * @param ossId   进 Word 的那张（预览图）的 oss_id
 * @param reason  为什么取不到（给人看的短句，见 {@link DocOssBytes#fetch}）
 * @author 独立验收 V23 修复
 */
public record MissingImage(String docKind, String slot, int index, Long ossId, String reason) {

    /** 描述串的上限（列宽 500，留余量）。 */
    static final int SUMMARY_MAX = 480;

    /** 字典 lqg_image_slot 的标签（与 V202609210810 的字典行逐字一致）。 */
    private static final Map<String, String> SLOT_LABELS = Map.of(
        "orig", "收样原始情况",
        "observe", "样本观察情况",
        "pretreat", "样本预处理情况",
        "organoid_observe", "类器官样本观察情况"
    );

    /** 图片位在文档里的先后（字典 dict_sort 的顺序：收样原始 → 样本观察 → 样本预处理 → 类器官观察）。 */
    private static final List<String> SLOT_ORDER = List.of("orig", "observe", "pretreat", "organoid_observe");

    /** 按「文档先后 → 图片位先后 → 第几张」排，明细读起来与文档里的顺序一致。 */
    public static final Comparator<MissingImage> DOC_ORDER = Comparator
        .comparingInt((MissingImage m) -> rank(DocKinds.MERGED_ORDER, m.docKind()))
        .thenComparingInt(m -> rank(SLOT_ORDER, m.slot()))
        .thenComparingInt(MissingImage::index);

    private static int rank(List<String> order, String value) {
        int i = order.indexOf(value);
        return i < 0 ? Integer.MAX_VALUE : i;
    }

    /** 「样本质控表·收样原始情况 第 2 张（存储里读不到这个文件）」 */
    public String label() {
        String slotLabel = SLOT_LABELS.getOrDefault(slot, slot);
        String text = DocKinds.label(docKind) + "·" + slotLabel + " 第 " + index + " 张";
        return reason == null || reason.isBlank() ? text : text + "（" + reason + "）";
    }

    /**
     * 多张缺图 → 一句话（「；」分隔），超长截断并注明总数。空列表 → {@code null}。
     */
    public static String summary(List<MissingImage> missing) {
        return summary(missing, SUMMARY_MAX);
    }

    /**
     * 同上，限定最长 {@code max} 个字符（拼进更长的一句话时用，保证后半句不被列宽截掉）。
     */
    public static String summary(List<MissingImage> missing, int max) {
        if (missing == null || missing.isEmpty()) {
            return null;
        }
        List<MissingImage> sorted = missing.stream().sorted(DOC_ORDER).toList();
        String tail = "；…共 " + sorted.size() + " 张";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < sorted.size(); i++) {
            String next = (i == 0 ? "" : "；") + sorted.get(i).label();
            boolean last = i == sorted.size() - 1;
            if (sb.length() + next.length() > (last ? max : max - tail.length())) {
                if (sb.length() + tail.length() <= max) {
                    sb.append(tail);
                }
                return sb.length() > max ? sb.substring(0, max) : sb.toString();
            }
            sb.append(next);
        }
        return sb.toString();
    }
}
