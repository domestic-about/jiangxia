package org.dromara.lqg.sample.guard;

import org.dromara.common.core.utils.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 样本字段规则（纯函数，无 Spring 无库 —— 契约测试可直接钉）。
 *
 * <p>★ 本张最容易做反的一条：<b>不是两份模板两张表</b>。{@code sample_kind} 区分
 * {@code tissue} / {@code organoid}；「类器官类型」只是 organoid 类才填的一列，
 * 所以必填集是<b>按类别分化</b>的：
 *
 * <ul>
 *   <li>{@code tissue}：{@code tissueType} + {@code internalNo} + {@code receiveDate} 必填；</li>
 *   <li>{@code organoid}：{@code organoidType} + {@code internalNo} + {@code receiveDate} 必填；</li>
 * </ul>
 *
 * <p>注意「另一类的类型字段**不是**必填」：给 organoid 传 {@code tissueType} 不该报错（历史数据 /
 * 预填可能带着），判据只看本类的那一列。反过来，缺本类的那一列必须拒（counterfeit 点名的形态）。
 *
 * <p>{@code internalNo} **手填、不自动生成**（编号规则是甲方自己的）：这里只归一化（去首尾空白）
 * 与查空，生成逻辑一行都没有。
 *
 * @author SAMPLE-MODEL-001
 */
public final class SampleKindRules {

    public static final String KIND_TISSUE = "tissue";
    public static final String KIND_ORGANOID = "organoid";

    private SampleKindRules() {
    }

    /**
     * 类别是否是已知值（未知值一律拒，不静默按 tissue 处理）。
     */
    public static boolean isKnownKind(String sampleKind) {
        return KIND_TISSUE.equals(normalize(sampleKind)) || KIND_ORGANOID.equals(normalize(sampleKind));
    }

    /**
     * 归一化：去首尾空白。
     */
    public static String normalize(String value) {
        return value == null ? null : value.trim();
    }

    /**
     * 该类别在「内部新增」时必须有的字段清单（给人话报错用）。
     *
     * @param sampleKind tissue / organoid
     * @return 缺失的字段名（中文名），全部具备时返回空列表
     */
    public static List<String> missingRequiredFields(String sampleKind,
                                                     String tissueType,
                                                     String organoidType,
                                                     String internalNo,
                                                     Object receiveDate) {
        List<String> missing = new ArrayList<>();
        String kind = normalize(sampleKind);
        if (KIND_TISSUE.equals(kind) && StringUtils.isBlank(tissueType)) {
            missing.add("组织类型");
        }
        if (KIND_ORGANOID.equals(kind) && StringUtils.isBlank(organoidType)) {
            missing.add("类器官类型");
        }
        if (StringUtils.isBlank(internalNo)) {
            missing.add("内部编号");
        }
        if (receiveDate == null || StringUtils.isBlank(String.valueOf(receiveDate))) {
            missing.add("收样日期");
        }
        return missing;
    }

    /**
     * 送检单号的形状：{@code 'SJ' + lpad(seq, 8, '0')}。
     */
    public static String formatSubmitNo(long seq) {
        return "SJ" + String.format("%08d", seq);
    }

}
