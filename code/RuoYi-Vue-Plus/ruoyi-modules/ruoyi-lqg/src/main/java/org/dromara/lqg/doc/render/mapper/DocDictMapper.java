package org.dromara.lqg.doc.render.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 渲染期要用的字典标签取值（目前只有性别 {@code lqg_gender}）。
 *
 * <p>★ 为什么自己在 doc 域写一条 SQL、不走 {@code SysDictTypeServiceImpl}：那份实现是
 * <b>Redis 缓存</b>驱动的（{@code CacheNames.SYS_DICT}）。渲染链路上「Redis 挂了 → 文档出不来」
 * 是不可接受的耦合，而这里只要一个标签。与 {@code QcScoreDictMapper} 同一取舍。
 *
 * <p>★ {@code sys_dict_data} 在若依基线上没有 {@code del_flag} 列，所以没有软删条件。
 *
 * @author DOC-RENDER-001
 */
public interface DocDictMapper {

    /**
     * @return 字典标签；查不到返回 {@code null}（调用方回落到原值）
     */
    @Select("""
        SELECT dict_label FROM sys_dict_data
         WHERE dict_type = #{dictType} AND dict_value = #{dictValue}
         ORDER BY dict_sort
         LIMIT 1
        """)
    String selectLabel(@Param("dictType") String dictType, @Param("dictValue") String dictValue);
}
