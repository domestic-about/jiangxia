package org.dromara.lqg.embed.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.helper.DataPermissionHelper;
import org.dromara.lqg.embed.domain.Embed;
import org.dromara.lqg.embed.mapper.EmbedMapper;
import org.springframework.stereotype.Service;

/**
 * 石蜡块编号的唯一性（FIELD:t_lqg_embed.paraffin_block_no 的 {@code uk_embed_block_no}）。
 *
 * <p>★ <b>三处调用、一份判据</b>：内部新增（必填 + 唯一）、内部修改（给了就唯一）、
 * 判有效（必填 + 唯一）。三处都走本类，免得「哪一条路忘了查重」——accept 2 第 5 段与
 * accept 4 中间两段分别打的就是「内部新增撞号」与「核验判有效撞号」。
 *
 * <p>★ 查重走 mapper（{@code @TableLogic} 只查 {@code del_flag='0'}）：<b>软删后可重用</b>同一个编号，
 * 部分唯一索引 {@code uk_embed_block_no WHERE del_flag='0'} 兜底。给用户的错误必须是人话
 * （「石蜡块编号「X」已存在，请换一个」），别把 PG 的 {@code duplicate key} 直接抛出去。
 *
 * <p>★ <b>不看状态</b>：pending 的记录占住一个编号（若哪天有历史数据如此）也算占用 ——
 * 「全库唯一」是编号这一列的字面口径，不按核验状态分化。
 *
 * @author EMBED-MODEL-001
 */
@Service
@RequiredArgsConstructor
public class EmbedBlockNoGuard {

    private final EmbedMapper embedMapper;

    /**
     * 修剪；空白 → {@code null}（「没填」）。
     */
    public static String normalized(String raw) {
        return StringUtils.isBlank(raw) ? null : raw.trim();
    }

    /**
     * 这个编号能不能用（全库唯一，排除自己那条）。
     *
     * @param blockNo   已修剪的编号（调用方保证非空；空白时本方法直接返回，不查）
     * @param excludeId 修改 / 核验时排除自己的 id（新增传 {@code null}）
     * @throws ServiceException 已被别的记录占用
     */
    public void requireUnique(String blockNo, Long excludeId) {
        if (StringUtils.isBlank(blockNo)) {
            return;
        }
        LambdaQueryWrapper<Embed> wrapper = new LambdaQueryWrapper<Embed>()
            .eq(Embed::getParaffinBlockNo, blockNo)
            .ne(excludeId != null, Embed::getId, excludeId);
        boolean taken = DataPermissionHelper.ignore(() -> embedMapper.selectCount(wrapper) > 0);
        if (taken) {
            throw new ServiceException("石蜡块编号「" + blockNo + "」已存在，请换一个");
        }
    }

}
