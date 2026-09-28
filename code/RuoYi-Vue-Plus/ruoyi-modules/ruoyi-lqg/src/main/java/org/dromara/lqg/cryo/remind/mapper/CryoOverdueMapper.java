package org.dromara.lqg.cryo.remind.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.dromara.lqg.cryo.batch.domain.CryoBatch;
import org.dromara.lqg.cryo.remind.sql.CryoOverdueSqlProvider;

import java.util.List;

/**
 * 超期清单与超期计数（CRYO-REMIND-001，doc/api-contract.md 的 {@code GET /lqg/cryo/overdue}）。
 *
 * <p>★ <b>只有两条 SQL，拼的是同一段 where</b>（{@link CryoOverdueSqlProvider#WHERE}）：
 * {@link #selectOverdueList(int)} 与 {@link #selectOverdueCount(int)}。两个方法都只接一个
 * <b>阈值天数</b>（{@code @Param("days")}），片段里没有硬编码天数。
 *
 * <p>★ 不继承 {@code BaseMapper}：本接口没有任何「按 MP 的内置方法查」的需求，
 * 两条自定义 SQL 就是全部；包名以 {@code .mapper} 结尾（若依的 mapper 扫描路径是
 * {@code org.dromara.**.mapper}）。
 *
 * <p>★ 这两条是自定义 SQL，MyBatis-Plus 的 {@code @TableLogic} <b>不会</b>自动补
 * {@code del_flag='0'}，所以片段里是手写的（seed 里有软删的批次 3008 与软删的流水 3106）。
 *
 * @author CRYO-REMIND-001
 */
public interface CryoOverdueMapper {

    /**
     * 超期批次清单（按已超天数倒序）。
     *
     * @param days 阈值天数（来自 {@code lqg.cryo.overdue-days}，每次判定现取）
     * @return 未删、暂存 -80、未转液氮、剩余 &gt; 0、冻存满阈值天数的批次
     */
    @Select(CryoOverdueSqlProvider.SELECT_LIST)
    List<CryoBatch> selectOverdueList(@Param(CryoOverdueSqlProvider.DAYS_PARAM) int days);

    /**
     * 超期批次数（与清单同一段 where —— 清单长度与这个数必须恒等）。
     *
     * @param days 阈值天数
     * @return 超期批次条数
     */
    @Select(CryoOverdueSqlProvider.SELECT_COUNT)
    long selectOverdueCount(@Param(CryoOverdueSqlProvider.DAYS_PARAM) int days);

}
