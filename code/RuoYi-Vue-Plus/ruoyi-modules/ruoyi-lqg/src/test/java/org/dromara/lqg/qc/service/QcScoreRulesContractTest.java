package org.dromara.lqg.qc.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.dromara.common.core.exception.ServiceException;
import org.dromara.lqg.qc.domain.bo.QcScoreSaveBo;
import org.dromara.lqg.qc.domain.vo.QcScoreDictRow;
import org.dromara.lqg.qc.mapper.QcScoreDictMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/**
 * 评分回填的契约测试（QC-MODEL-001 accept 2 的三条 counterfeit，纯函数、不碰库不碰 Spring）。
 *
 * <p>钉四件事：
 * <ol>
 *   <li><b>前端传来的分值不生效</b> —— {@code QcScoreSaveBo} 上<b>一个 {@code *Score} 字段都没有</b>
 *       （反射断言）；</li>
 *   <li><b>0 分档 = 选了</b>：{@code cultureDaysLevel="gt14"} → {@code cultureDaysScore=0}
 *       且合计算进去（{@code if (score)} 那种写法在这里就红）；</li>
 *   <li><b>任一项没选 → 该项分值 null、合计 null</b>（不是 0）；</li>
 *   <li><b>分值只认字典 {@code remark}</b>：同一份 stub 字典里改一个数字，结论跟着变
 *       —— 写死在 Java 枚举里的实现过不了这条。</li>
 * </ol>
 *
 * @author QC-MODEL-001
 */
class QcScoreRulesContractTest {

    /**
     * stub 字典：与 {@code V202609210810__SYS-BASE-001-lqg-dicts.sql} 的现状一致
     * （分值在 remark 列）。
     */
    private static QcScoreDictionary dictionaryOf() {
        List<QcScoreDictRow> rows = new ArrayList<>();
        // 培养前样本评分
        rows.add(row(QcDocRules.DICT_PRE_CULTURE, "lt40", 8));
        rows.add(row(QcDocRules.DICT_PRE_CULTURE, "40to80", 16));
        rows.add(row(QcDocRules.DICT_PRE_CULTURE, "gt80", 20));
        // 培养天数：>14d 就是 0 分（不是「没选」）
        rows.add(row(QcDocRules.DICT_CULTURE_DAYS, "gt14", 0));
        rows.add(row(QcDocRules.DICT_CULTURE_DAYS, "le14", 10));
        // 类器官数量：<100 也是 0 分
        rows.add(row(QcDocRules.DICT_ORGANOID_COUNT, "lt100", 0));
        rows.add(row(QcDocRules.DICT_ORGANOID_COUNT, "100to1500", 10));
        rows.add(row(QcDocRules.DICT_ORGANOID_COUNT, "1500to4000", 25));
        rows.add(row(QcDocRules.DICT_ORGANOID_COUNT, "gt4000", 40));
        // 类器官直径
        rows.add(row(QcDocRules.DICT_DIAMETER, "lt30", 10));
        rows.add(row(QcDocRules.DICT_DIAMETER, "30to100", 20));
        rows.add(row(QcDocRules.DICT_DIAMETER, "gt100", 30));
        QcScoreDictMapper mapper = () -> rows;
        return new QcScoreDictionary(mapper);
    }

    /** 只改某一个档位的 remark，验证「分值跟着字典走」。 */
    private static QcScoreDictionary dictionaryWithRemark(String dictType, String dictValue, Object remark) {
        List<QcScoreDictRow> rows = new ArrayList<>();
        rows.add(row(QcDocRules.DICT_PRE_CULTURE, "gt80", 20));
        rows.add(row(QcDocRules.DICT_CULTURE_DAYS, "gt14", 0));
        rows.add(row(QcDocRules.DICT_CULTURE_DAYS, "le14", 10));
        rows.add(row(QcDocRules.DICT_ORGANOID_COUNT, "lt100", 0));
        rows.add(row(QcDocRules.DICT_DIAMETER, "gt100", 30));
        if (dictType != null) {
            rows.add(row(dictType, dictValue, remark));
        }
        return new QcScoreDictionary(() -> rows);
    }

    private static QcScoreDictRow row(String dictType, String dictValue, Object remark) {
        QcScoreDictRow r = new QcScoreDictRow();
        r.setDictType(dictType);
        r.setDictValue(dictValue);
        r.setRemark(remark == null ? null : String.valueOf(remark));
        return r;
    }

    private static QcScoreSaveBo bo(String pre, String days, String count, String diameter) {
        QcScoreSaveBo bo = new QcScoreSaveBo();
        bo.setPreCultureLevel(pre);
        bo.setCultureDaysLevel(days);
        bo.setOrganoidCountLevel(count);
        bo.setDiameterLevel(diameter);
        return bo;
    }

    @Test
    @DisplayName("① 入参没有 *_Score 字段：前端夹带的假分值连落脚的字段都没有")
    void scoreBoHasNoScoreFields() {
        java.util.List<String> names = java.util.Arrays.stream(QcScoreSaveBo.class.getDeclaredFields())
            .filter(f -> !java.lang.reflect.Modifier.isStatic(f.getModifiers()))
            .map(Field::getName).collect(java.util.stream.Collectors.toList());
        for (String name : names) {
            assertTrue(name.endsWith("Level"),
                "QcScoreSaveBo 只许有 *Level 字段，发现：" + name);
        }
        assertEquals(4, names.size(), "四个变量各一档，多一个都说明有人把分值塞进契约了");
    }

    @Test
    @DisplayName("② 0 分档是「选了」：gt14 / lt100 → 分值 0，且合计把 0 算进去")
    void zeroScoreLevelsCountAsChosen() {
        // 20 + 0 + 0 + 30 = 50（accept 2 第 1~3 段的那组值）
        QcScoreSnapshot snapshot = dictionaryOf().resolve(
            bo("gt80", "gt14", "lt100", "gt100"));
        assertEquals(20, snapshot.getPreCultureScore());
        assertEquals(0, snapshot.getCultureDaysScore());
        assertEquals(0, snapshot.getOrganoidCountScore());
        assertEquals(30, snapshot.getDiameterScore());
        assertEquals(50, snapshot.getTotalScore(), "0 分档必须参与合计，不能被当成没选");
    }

    @Test
    @DisplayName("③ 任一项没选 → 该项分值 null、合计 null（不是 0）")
    void missingLevelMakesTotalNull() {
        QcScoreSnapshot snapshot = dictionaryOf().resolve(
            bo("gt80", null, "lt100", "gt100"));
        assertNull(snapshot.getCultureDaysScore(), "没选是 null，不是 0");
        assertNull(snapshot.getTotalScore(), "少一项就不给合计");

        // 空串与 null 同义（前端清空下拉框）
        QcScoreSnapshot blank = dictionaryOf().resolve(bo("gt80", "  ", "lt100", "gt100"));
        assertNull(blank.getCultureDaysScore());
        assertNull(blank.getTotalScore());
    }

    @Test
    @DisplayName("④ 非法档位一律 400，不静默当成「没选」")
    void unknownLevelIsRejected() {
        ServiceException e = assertThrows(ServiceException.class,
            () -> dictionaryOf().resolve(bo("gt999", null, null, null)));
        assertEquals(400, e.getCode());
        assertTrue(e.getMessage().contains("gt999"), "错误信息要说清是哪个档位不合法：" + e.getMessage());
    }

    @Test
    @DisplayName("⑤ 分值只认字典 remark：改字典那一行，结论跟着变")
    void scoreComesFromDictionaryRemarkNotJava() {
        // gt80 在字典里被改成 99 → 后端落 99（写死 20 的实现这条红）
        QcScoreSnapshot snapshot = dictionaryWithRemark(
            QcDocRules.DICT_PRE_CULTURE, "gt80", 99).resolve(bo("gt80", "gt14", "lt100", "gt100"));
        assertEquals(99, snapshot.getPreCultureScore());
        assertEquals(99 + 0 + 0 + 30, snapshot.getTotalScore());
    }

    @Test
    @DisplayName("⑥ 字典没配分值（remark 为空）= 配置错 500，不静默当 0 分")
    void blankRemarkIsServerMisconfiguration() {
        ServiceException e = assertThrows(ServiceException.class,
            () -> dictionaryWithRemark(QcDocRules.DICT_PRE_CULTURE, "gt80", null)
                .resolve(bo("gt80", null, null, null)));
        assertEquals(500, e.getCode());
    }

}
