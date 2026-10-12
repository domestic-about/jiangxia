package org.dromara.lqg.stat.domain.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 操作人统计（CR-20261012-22，{@code GET /lqg/stat/operator}、{@code GET /mp/int/stat/operator}）。
 *
 * <p>四张表各一块（{@link Sheet}），<b>块与块之间不加总</b>（甲方：样本记录信息表是从原代开始养的，
 * 不能和别的表里的操作人叠在一起算）；质控表不在里面（它的操作人是从样本带出来的）。
 *
 * @author CR-20261012-22
 */
@Data
@Schema(description = "操作人统计")
public class OperatorStatVo {

    @Schema(description = "时间段起（含）；空 = 不限")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate begin;

    @Schema(description = "时间段止（含）；空 = 不限")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate end;

    @Schema(description = "四张表：tissue / organoid / embed / cryo（顺序固定）")
    private List<Sheet> sheets;

    /**
     * 一张表。
     */
    @Data
    public static class Sheet {

        @Schema(description = "tissue / organoid / embed / cryo")
        private String sheet;

        @Schema(description = "表名")
        private String title;

        @Schema(description = "时间段按这张表的哪一栏算")
        private String dateLabel;

        @Schema(description = "这张表统计的几个数（列）")
        private List<Metric> metrics;

        @Schema(description = "合计：数的 key → 全部行之和")
        private Map<String, Long> totals;

        @Schema(description = "每人一行：有记录的才出现；没填这一栏的记录归在最后一行「未填」")
        private List<Row> rows;
    }

    /**
     * 一个数（列）。
     */
    @Data
    public static class Metric {

        @Schema(description = "count / embedBy / operator / batches / tubes")
        private String key;

        @Schema(description = "列名")
        private String label;

        @Schema(description = "单位")
        private String unit;

        public Metric(String key, String label, String unit) {
            this.key = key;
            this.label = label;
            this.unit = unit;
        }
    }

    /**
     * 一个人一行。
     */
    @Data
    public static class Row {

        @Schema(description = "按人筛的参数值：u:<账号 id> / n:<姓名> / none —— 原样带给列表的 operatorKey / embedByKey / frozenByKey")
        private String key;

        @Schema(description = "账号 id（关联了内部人员的才有）")
        private Long userId;

        @Schema(description = "姓名（关联了账号的取账号现在的姓名；没填的是「未填」）")
        private String name;

        @Schema(description = "true = 关联了内部人员账号；false = 历史手填的姓名（没对上账号）或没填")
        private Boolean linked;

        @Schema(description = "true = 就是当前登录人")
        private Boolean me;

        @Schema(description = "数的 key → 值（这个人在这一列没有记录 = 0）")
        private Map<String, Long> values;
    }

}
