package org.dromara.lqg.stat.domain.vo;

import lombok.Data;

/**
 * 聚合查询的一行（{@code OperatorStatMapper}）：一个「人」在一张表里的数。不出接口，只在服务层装配时用。
 *
 * @author CR-20261012-22
 */
@Data
public class PersonCountRow {

    /** 账号 id（关联了内部人员的） */
    private Long userId;

    /** 没关联账号时的姓名文字；{@code userId} 与它都为空 = 这一栏没填 */
    private String text;

    /** 记录上的姓名快照（账号查不到姓名时兜底显示） */
    private String snapshot;

    /** 记录数 */
    private Long count;

    /** 数量之和（只有冻存用：冻存数量 / 支） */
    private Long qty;

}
