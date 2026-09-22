package org.dromara.lqg.embed.mapper;

import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.lqg.embed.domain.Embed;
import org.dromara.lqg.embed.domain.vo.EmbedVo;

/**
 * 石蜡包埋送样记录 mapper（FIELD-ssot 的 t_lqg_embed）。
 *
 * <p>软删由 {@code @TableLogic} 在 {@link Embed#getDelFlag()} 上兜住：本接口不写任何自定义
 * DELETE / 绕过逻辑删的 SQL，列表与详情因此天然永不返回 {@code del_flag='1'} 的行
 * （accept 3 第 2 段的 id 集合里没有软删的 2005 就是断这一格）。
 *
 * <p>★ 没有自定义 SQL：连「按内部编号筛」也是「先用 {@code SampleMapper} 查样本 id、再
 * {@code in(sample_id)}」，不手写 join —— 免得绕开 {@code @TableLogic}。
 *
 * @author EMBED-MODEL-001
 */
public interface EmbedMapper extends BaseMapperPlus<Embed, EmbedVo> {
}
