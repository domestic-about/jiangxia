package org.dromara.lqg.sample.query.mapper;

import org.apache.ibatis.annotations.Param;
import org.dromara.lqg.sample.query.SampleSubmitterProfileVo;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
import java.util.List;

/**
 * 样本提交人的外部档案（{@code t_lqg_ext_profile} 及组别名）读侧 —— SAMPLE-WEB-001。
 *
 * <p>★ 为什么不复用 AUTH 域的 {@code ExtProfileMapper}：那个 mapper 的实体是 {@code ExtProfile}
 * （AUTH-LOGIN-001 的写侧实体），本票只要两列读视图；而组别名在 {@code t_lqg_unit_group}
 * （AUTH-GROUP-001 的表）上，需要一条 join。两个 mapper 各管各的写 / 读形状，互不牵动。
 *
 * <p>★ <b>只用一条 {@code @Select} + 参数化 where，不拼 SQL 文本</b>：单位 / 组别是工作台筛选项，
 * 值来自查询参数。全部走 {@code #{}} 占位符，没有字符串拼接。
 *
 * <p>★ <b>不看档案的核验状态</b>（{@code bind_status}）：内部人员按组别找样本，未核验的组
 * （seed 的 extE 送来的 1007）也要能筛出来 —— 核验状态是「这个人的组别认不认」，
 * 不是「这条样本算不算这个组的」。软删的档案行仍然排除（{@code del_flag='0'}）。
 *
 * <p>★ 为什么放在 {@code sample.query.mapper} 而不是 {@code sample.mapper}：
 * 若依的 mapper 扫描路径是 {@code org.dromara.**.mapper}（application.yml 的
 * {@code mybatis-plus.mapperPackage}），所以包名必须以 {@code .mapper} 结尾才会被扫到 ——
 * 只写 {@code org.dromara.lqg.sample.query} 起不来（
 * {@code Parameter 0 of constructor in SampleSubmitterProfileQuery required a bean of type
 * '...SampleSubmitterProfileMapper' that could not be found}，本票实测踩过）。
 * 放在 {@code sample/query/} 包内是为了不往 SAMPLE-MODEL-001 的 {@code sample.mapper} 里塞东西。
 *
 * @author SAMPLE-WEB-001
 */
public interface SampleSubmitterProfileMapper {

    /**
     * 按<b>外部档案</b>里的单位 / 组别筛出提交人 user_id。
     *
     * <p>两个条件都可为 null：都为 null 时返回全部有档案的外部用户
     * （调用方只有在 {@code sourceUnitId} / {@code groupId} 至少给了一个时才调本方法）。
     *
     * @param unitId  来源单位 id（对应 {@code t_lqg_ext_profile.unit_id}）
     * @param groupId 组别 id（对应 {@code t_lqg_ext_profile.group_id}）
     * @return 提交人 user_id 列表（可能为空集 → 调用方回空页）
     */
    @Select("""
        <script>
        SELECT p.user_id
        FROM t_lqg_ext_profile p
        WHERE p.del_flag = '0'
        <if test="unitId != null"> AND p.unit_id = #{unitId} </if>
        <if test="groupId != null"> AND p.group_id = #{groupId} </if>
        ORDER BY p.user_id
        </script>
        """)
    List<Long> selectSubmitterIds(@Param("unitId") Long unitId, @Param("groupId") Long groupId);

    /**
     * 按 user_id 批量取「提交人姓名 + 组别名」。
     *
     * <p>组别名走 {@code LEFT JOIN t_lqg_unit_group}：自填组名（{@code group_id} 为空）与档案里
     * 组别 id 指向已不存在的组时都是 null，不报错。
     *
     * @param userIds 提交人 user_id 集合（调用方保证非空）
     * @return 每个有档案的 user_id 一行
     */
    @Select("""
        <script>
        SELECT p.user_id       AS userId,
               p.real_name     AS submitterName,
               p.group_id      AS groupId,
               g.group_name    AS groupName
        FROM t_lqg_ext_profile p
        LEFT JOIN t_lqg_unit_group g ON g.id = p.group_id AND g.del_flag = '0'
        WHERE p.del_flag = '0'
          AND p.user_id IN
        <foreach collection="userIds" item="uid" open="(" separator="," close=")">#{uid}</foreach>
        </script>
        """)
    List<SampleSubmitterProfileVo> selectProfiles(@Param("userIds") Collection<Long> userIds);

}
