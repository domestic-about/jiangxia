package org.dromara.lqg.qc.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.lqg.qc.domain.bo.QcScoreSaveBo;
import org.dromara.lqg.qc.domain.vo.QcScoreDictRow;
import org.dromara.lqg.qc.mapper.QcScoreDictMapper;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 评分的**档位 → 分值**换算（ADR 口径：分值的唯一来源是 {@code sys_dict_data.remark}）。
 *
 * <pre>
 *   SELECT dict_type, dict_value, remark FROM sys_dict_data
 *    WHERE dict_type IN (四个 lqg_score_*)
 * </pre>
 *
 * <p>★★ <b>为什么必须有这个类、不许把分值写在 Java 里</b>（accept 2 的 counterfeit 第 3 条）：
 * 写死枚举此刻也和字典相等，但 accept 2 第 4 段是拿<b>库里落下的分值</b>去和字典表 JOIN
 * 对账 —— 将来有人只改了一边（改库里的分值快照、或改字典 remark），那条断言就红。
 * 反过来「只改字典 remark、代码一个字不动」是这个设计要买到的东西。
 *
 * <p>★ <b>不缓存</b>：工作台「系统管理 → 字典管理」改了 remark，下一次保存立刻生效。
 * 四张字典一共 12 个档位，一次查询的成本可以忽略。
 *
 * <p>★ <b>非法档位一律 400</b>（accept 2 第 6 段：{@code preCultureLevel:"gt999"} → 400/500），
 * <b>不静默当成「没选」</b> —— 那样前端把值拼错时合计会静默变空。
 *
 * @author QC-MODEL-001
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class QcScoreDictionary {

    private final QcScoreDictMapper qcScoreDictMapper;

    /**
     * 把入参的四个档位换算成落库的分值快照。
     *
     * @param bo 只含四个 {@code *Level} 的入参（{@code null} / 空串 = 没选）
     * @return 四个分值 + 合计（任一项没选 → 合计 null）
     */
    public QcScoreSnapshot resolve(QcScoreSaveBo bo) {
        Map<String, Map<String, Integer>> dict = loadScoreDict();
        Integer preCulture = pick(dict, QcDocRules.DICT_PRE_CULTURE,
            bo == null ? null : bo.getPreCultureLevel());
        Integer cultureDays = pick(dict, QcDocRules.DICT_CULTURE_DAYS,
            bo == null ? null : bo.getCultureDaysLevel());
        Integer organoidCount = pick(dict, QcDocRules.DICT_ORGANOID_COUNT,
            bo == null ? null : bo.getOrganoidCountLevel());
        Integer diameter = pick(dict, QcDocRules.DICT_DIAMETER,
            bo == null ? null : bo.getDiameterLevel());
        return QcScoreSnapshot.of(preCulture, cultureDays, organoidCount, diameter);
    }

    /**
     * 读四张评分字典的当前值：{@code dictType → (dictValue → 分值)}。
     */
    private Map<String, Map<String, Integer>> loadScoreDict() {
        List<QcScoreDictRow> rows = qcScoreDictMapper.selectScoreDicts();
        Map<String, Map<String, Integer>> dict = new HashMap<>();
        if (rows == null) {
            return dict;
        }
        for (QcScoreDictRow row : rows) {
            if (row == null || StringUtils.isBlank(row.getDictType()) || StringUtils.isBlank(row.getDictValue())) {
                continue;
            }
            dict.computeIfAbsent(row.getDictType(), k -> new HashMap<>())
                .put(row.getDictValue(), parseScore(row));
        }
        return dict;
    }

    /**
     * {@code remark} 就是分值。空 / 非数字 = 字典没配好 —— 这是**配置错**（500），
     * 不是用户输入错（400）：静默当 0 分会让总分悄悄错掉。
     */
    private static Integer parseScore(QcScoreDictRow row) {
        String remark = row.getRemark();
        if (StringUtils.isBlank(remark)) {
            throw new ServiceException("字典 " + row.getDictType() + " 的档位 " + row.getDictValue()
                + " 没有配分值（sys_dict_data.remark 为空）——分值只认字典 remark，不在代码里写死", 500);
        }
        try {
            return Integer.valueOf(remark.trim());
        } catch (NumberFormatException e) {
            throw new ServiceException("字典 " + row.getDictType() + " 的档位 " + row.getDictValue()
                + " 的分值不是整数（remark=" + remark + "）", 500);
        }
    }

    /**
     * 取一个档位的分值；没选返回 null；档位不在字典里 → 400。
     */
    private static Integer pick(Map<String, Map<String, Integer>> dict, String dictType, String level) {
        if (StringUtils.isBlank(level)) {
            // ★ 没选：返回 null（不是 0）。0 分档必须走下面那一支、拿到真的 0。
            return null;
        }
        Map<String, Integer> values = dict.get(dictType);
        Integer score = values == null ? null : values.get(level.trim());
        if (score == null) {
            throw new ServiceException("「" + QcDocRules.SCORE_DICT_LABELS.getOrDefault(dictType, dictType)
                + "」没有档位 " + level + "（合法档位："
                + (values == null ? "字典 " + dictType + " 未配置" : String.join(" / ", values.keySet()))
                + "）", 400);
        }
        return score;
    }

}
