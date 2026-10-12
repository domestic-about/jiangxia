package org.dromara.lqg.stat.service;

import org.dromara.lqg.auth.staff.directory.PersonKey;
import org.dromara.lqg.stat.domain.vo.OperatorStatVo;
import org.dromara.lqg.stat.domain.vo.OperatorStatVo.Metric;
import org.dromara.lqg.stat.domain.vo.OperatorStatVo.Row;
import org.dromara.lqg.stat.domain.vo.OperatorStatVo.Sheet;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 把聚合查询的结果装成一张表的统计块（CR-20261012-22）—— <b>纯函数</b>，不碰库，单测直接钉。
 *
 * <p>一张表可以有几个数（石蜡包埋：包埋人一列、操作人一列；冻存：批次、支数），同一个人的几个数并到一行；
 * 某一列里没有他的记录就是 0。
 *
 * <p>排序：按第一个数从多到少，一样多的按姓名；<b>「未填」永远在最后</b>。
 *
 * @author CR-20261012-22
 */
public final class OperatorStatAssembler {

    /** 这一栏没填的那一行显示的名字 */
    public static final String NONE_NAME = "未填";

    private OperatorStatAssembler() {
    }

    /**
     * 一个格子：某个数（列）里、某个人的值。
     *
     * @param metric   数的 key
     * @param userId   账号 id（关联了的）
     * @param text     没关联账号时的姓名文字
     * @param snapshot 记录上的姓名快照（账号查不到姓名时兜底）
     * @param value    值
     */
    public record Cell(String metric, Long userId, String text, String snapshot, long value) {
    }

    /**
     * @param names 账号 id → 现在的姓名
     * @param me    当前登录人（取不到 = null）
     */
    public static Sheet sheet(String key, String title, String dateLabel, List<Metric> metrics, List<Cell> cells,
                              Map<Long, String> names, Long me) {
        Map<String, Row> rows = new LinkedHashMap<>();
        Map<String, Long> totals = new LinkedHashMap<>();
        for (Metric metric : metrics) {
            totals.put(metric.getKey(), 0L);
        }
        for (Cell cell : cells) {
            if (!totals.containsKey(cell.metric())) {
                continue;
            }
            String rowKey = PersonKey.of(cell.userId(), cell.text());
            Row row = rows.computeIfAbsent(rowKey, k -> newRow(k, cell, metrics, names, me));
            row.getValues().merge(cell.metric(), cell.value(), Long::sum);
            totals.merge(cell.metric(), cell.value(), Long::sum);
        }
        String first = metrics.isEmpty() ? null : metrics.get(0).getKey();
        List<Row> sorted = new ArrayList<>(rows.values());
        sorted.sort(Comparator
            .comparing((Row r) -> PersonKey.NONE.equals(r.getKey()))
            .thenComparing(r -> first == null ? 0L : -r.getValues().getOrDefault(first, 0L))
            .thenComparing(Row::getName));
        Sheet sheet = new Sheet();
        sheet.setSheet(key);
        sheet.setTitle(title);
        sheet.setDateLabel(dateLabel);
        sheet.setMetrics(metrics);
        sheet.setTotals(totals);
        sheet.setRows(sorted);
        return sheet;
    }

    private static Row newRow(String rowKey, Cell cell, List<Metric> metrics, Map<Long, String> names, Long me) {
        Row row = new Row();
        row.setKey(rowKey);
        row.setUserId(cell.userId());
        row.setLinked(cell.userId() != null);
        row.setMe(cell.userId() != null && cell.userId().equals(me));
        if (cell.userId() != null) {
            String name = names.get(cell.userId());
            row.setName(name != null ? name : (cell.snapshot() != null ? cell.snapshot() : "账号 " + cell.userId()));
        } else {
            row.setName(PersonKey.NONE.equals(rowKey) ? NONE_NAME : cell.text().trim());
        }
        Map<String, Long> values = new LinkedHashMap<>();
        for (Metric metric : metrics) {
            values.put(metric.getKey(), 0L);
        }
        row.setValues(values);
        return row;
    }

    /**
     * 四张表的块合在一起（顺序固定）。
     */
    public static OperatorStatVo of(java.time.LocalDate begin, java.time.LocalDate end, List<Sheet> sheets) {
        OperatorStatVo vo = new OperatorStatVo();
        vo.setBegin(begin);
        vo.setEnd(end);
        vo.setSheets(sheets);
        return vo;
    }

}
