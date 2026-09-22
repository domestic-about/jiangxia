package org.dromara.lqg.cryo.batch.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.lqg.cryo.batch.domain.CryoBatch;
import org.dromara.lqg.cryo.batch.domain.vo.CryoBatchVo;

/**
 * 冻存批次 mapper（FIELD-ssot 的 t_lqg_cryo_batch）。
 *
 * <p>软删由 {@code @TableLogic} 在 {@link CryoBatch#getDelFlag()} 上兜住：本接口不写任何自定义
 * DELETE，列表与详情因此天然永不返回 {@code del_flag='1'} 的行（seed 里软删的 3008 不出现）。
 *
 * <p>★ <b>本接口只有一条自定义 SQL：{@link #selectByIdForUpdate(Long)}</b>。
 * 「改初始支数 / 写改删流水前先对批次行加行锁再重算」是 FLOW:F-CRYO-02.step4 的硬要求
 * （CRYO-FLOW-001 accept 3 的并发用例就是断它）—— {@code @TableLogic} 的条件必须
 * <b>手写进这条 SQL</b>：MP 不会给自定义 {@code @Select} 补 {@code del_flag='0'}。
 *
 * <p>包名以 {@code .mapper} 结尾（若依的 mapper 扫描路径是 {@code org.dromara.**.mapper}，
 * 见 {@code common-mybatis.yml}）；写错一层就起不来（SAMPLE-WEB-001 踩过同型坑）。
 *
 * @author CRYO-MODEL-001
 */
public interface CryoBatchMapper extends BaseMapperPlus<CryoBatch, CryoBatchVo> {

    /**
     * 按 id 取批次并<b>锁住这一行</b>（{@code SELECT … FOR UPDATE}）。
     *
     * <p>★ 必须在事务内调用（调用方 {@code CryoBatchService} 上有
     * {@code @Transactional(rollbackFor = Exception.class)}）。没有这一把锁，
     * 「先读未删流水算剩余、再 UPDATE」会让两个人同时通过校验（库存类功能最经典的事故）。
     *
     * <p>★ {@code del_flag = '0'} 是手写的：{@code @Select} 是自定义 SQL，MP 的逻辑删<b>不会</b>
     * 自动补上（与 {@code selectById} 不同）。
     *
     * @param id 批次 id
     * @return 未删的批次；不存在 / 已软删 → null
     */
    @Select("SELECT * FROM t_lqg_cryo_batch WHERE id = #{id} AND del_flag = '0' FOR UPDATE")
    CryoBatch selectByIdForUpdate(@Param("id") Long id);

}
