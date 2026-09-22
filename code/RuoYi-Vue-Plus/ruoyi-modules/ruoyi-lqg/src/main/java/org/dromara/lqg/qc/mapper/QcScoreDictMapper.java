package org.dromara.lqg.qc.mapper;

import org.apache.ibatis.annotations.Select;
import org.dromara.lqg.qc.domain.vo.QcScoreDictRow;

import java.util.List;

/**
 * 评分字典的只读取值（ADR 口径：分值的唯一来源是 {@code sys_dict_data.remark}）。
 *
 * <p>★ <b>为什么在 qc 里自己写一条 SQL、不引 ruoyi-system 的字典 service</b>：
 * 这里只要四行 {@code (dict_type, dict_value, remark)}，用工作台的字典管理 service
 * 要拉整棵字典树、还带租户与缓存语义。四张字典一共 12 个档位，每次保存读一遍
 * <b>不缓存</b>——「改分值改字典 remark，不改代码」这句话要成立，读的就必须是<b>当下</b>
 * 的字典表（accept 2 第 4 段就是拿库里落下的分值去和字典表 JOIN 对账）。
 *
 * <p>★ {@code sys_dict_data} 没有 {@code del_flag} 列（若依基线），所以这里没有软删条件。
 *
 * @author QC-MODEL-001
 */
public interface QcScoreDictMapper {

    /**
     * 四个评分字典的全部档位与分值。
     */
    @Select("""
        SELECT dict_type AS dictType, dict_value AS dictValue, remark
          FROM sys_dict_data
         WHERE dict_type IN ('lqg_score_pre_culture', 'lqg_score_culture_days',
                             'lqg_score_count', 'lqg_score_diameter')
        """)
    List<QcScoreDictRow> selectScoreDicts();

}
