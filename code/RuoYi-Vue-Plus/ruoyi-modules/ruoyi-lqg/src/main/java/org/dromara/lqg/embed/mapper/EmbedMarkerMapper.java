package org.dromara.lqg.embed.mapper;

import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.lqg.embed.domain.EmbedMarker;

/**
 * marker 表达 mapper（FIELD-ssot 的 t_lqg_embed_marker）。
 *
 * <p>「整组替换」的旧组软删也走这里（{@code delete(wrapper)} 会被 {@code @TableLogic}
 * 改写成 {@code UPDATE … SET del_flag='1'}），所以历史行不会消失、也不会被任何查询读到。
 *
 * @author EMBED-MODEL-001
 */
public interface EmbedMarkerMapper extends BaseMapperPlus<EmbedMarker, EmbedMarker> {
}
