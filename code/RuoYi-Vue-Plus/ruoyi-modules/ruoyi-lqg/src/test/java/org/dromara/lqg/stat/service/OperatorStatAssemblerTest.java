package org.dromara.lqg.stat.service;

import org.dromara.lqg.stat.domain.vo.OperatorStatVo.Metric;
import org.dromara.lqg.stat.domain.vo.OperatorStatVo.Row;
import org.dromara.lqg.stat.domain.vo.OperatorStatVo.Sheet;
import org.dromara.lqg.stat.service.OperatorStatAssembler.Cell;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 操作人统计一张表的装配（{@link OperatorStatAssembler}，CR-20261012-22）。
 *
 * @author CR-20261012-22
 */
class OperatorStatAssemblerTest {

    private static final Map<Long, String> NAMES = Map.of(101L, "李工", 102L, "刘佳");

    private static final List<Metric> EMBED = List.of(new Metric("embedBy", "包埋人", "块"), new Metric("operator", "操作人", "块"));

    @Test
    @DisplayName("① 同一个人的两个数并到一行；某一列没有他的记录 = 0；合计 = 各行之和")
    void mergesMetricsPerPerson() {
        Sheet sheet = OperatorStatAssembler.sheet("embed", "石蜡包埋送样记录", "石蜡包埋时间", EMBED, List.of(
            new Cell("embedBy", 101L, null, "李工", 3),
            new Cell("embedBy", 102L, null, "刘佳", 1),
            new Cell("operator", 101L, null, "李工", 4)), NAMES, 102L);
        assertEquals(2, sheet.getRows().size());
        Row li = sheet.getRows().get(0);
        assertEquals("u:101", li.getKey());
        assertEquals(Map.of("embedBy", 3L, "operator", 4L), li.getValues());
        Row liu = sheet.getRows().get(1);
        assertEquals(Map.of("embedBy", 1L, "operator", 0L), liu.getValues());
        assertEquals(Map.of("embedBy", 4L, "operator", 4L), sheet.getTotals());
        assertTrue(liu.getMe(), "当前登录人那一行要标出来");
        assertFalse(li.getMe());
    }

    @Test
    @DisplayName("② ★ 关联了账号的、没关联的同名文字、没填的，是三行（不合并）；「未填」永远在最后")
    void linkedTextAndNoneAreSeparateRows() {
        Sheet sheet = OperatorStatAssembler.sheet("tissue", "样本记录信息表", "收样日期",
            List.of(new Metric("count", "操作人", "条")), List.of(
                new Cell("count", null, null, null, 9),
                new Cell("count", null, "李工", "李工", 2),
                new Cell("count", 101L, null, "李工", 5)), NAMES, null);
        assertEquals(List.of("u:101", "n:李工", "none"), sheet.getRows().stream().map(Row::getKey).toList());
        assertEquals(List.of(true, false, false), sheet.getRows().stream().map(Row::getLinked).toList());
        assertEquals("未填", sheet.getRows().get(2).getName());
        assertEquals(16L, sheet.getTotals().get("count"));
    }

    @Test
    @DisplayName("③ 按第一个数从多到少排；姓名取账号现在的名字，账号查不到时用记录上的快照")
    void sortedByFirstMetricAndNamedByAccount() {
        Sheet sheet = OperatorStatAssembler.sheet("cryo", "-80 冻存记录", "冻存时间",
            List.of(new Metric("batches", "批次", "批"), new Metric("tubes", "支数", "支")), List.of(
                new Cell("batches", 101L, null, "李工（旧名）", 1), new Cell("tubes", 101L, null, "李工（旧名）", 30),
                new Cell("batches", 300L, null, "已删除的人", 4), new Cell("tubes", 300L, null, "已删除的人", 8)), NAMES, null);
        assertEquals(List.of("已删除的人", "李工"), sheet.getRows().stream().map(Row::getName).toList());
        assertEquals(Map.of("batches", 5L, "tubes", 38L), sheet.getTotals());
    }

    @Test
    @DisplayName("④ 一条记录都没有：合计是 0、没有行（不是 null）")
    void emptySheet() {
        Sheet sheet = OperatorStatAssembler.sheet("embed", "石蜡包埋送样记录", "石蜡包埋时间", EMBED, List.of(), NAMES, null);
        assertTrue(sheet.getRows().isEmpty());
        assertEquals(Map.of("embedBy", 0L, "operator", 0L), sheet.getTotals());
    }

}
