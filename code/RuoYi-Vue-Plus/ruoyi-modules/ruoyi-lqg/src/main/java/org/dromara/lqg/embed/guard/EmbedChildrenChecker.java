package org.dromara.lqg.embed.guard;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.dromara.common.mybatis.helper.DataPermissionHelper;
import org.dromara.lqg.embed.domain.Embed;
import org.dromara.lqg.embed.mapper.EmbedMapper;
import org.dromara.lqg.sample.guard.SampleChildrenChecker;
import org.dromara.lqg.sample.verify.VerifyTransitions;
import org.springframework.stereotype.Component;

/**
 * 「这个样本名下有没有石蜡块」—— 注册给 SAMPLE-VERIFY-001 的
 * {@link SampleChildrenChecker} 扩展点（EMBED-MODEL-001 的第一个实现）。
 *
 * <p>★ <b>它一注册，{@code valid → invalid} 这条边就活了</b>：样本名下只要有<b>一块已生效的石蜡块</b>，
 * 内部就不能把它改判无效（D3 qa_scope L3「有已核验石蜡块的样本改判无效被拒」、本票 accept 2 倒数第 2 段）。
 *
 * <p>★★ <b>判据（这是本票背的那颗定时炸弹的解药，全仓只有这一处）</b>：
 * <pre>
 * SELECT count(*) FROM t_lqg_embed
 *  WHERE sample_id = ?
 *    AND del_flag = '0'          -- 软删的不算（seed 的 2005 是软删的，挂在 1008 名下）
 *    AND verify_status = 'valid' -- ★ 待核验的外部送样不算（seed 的 2006 是 pending，挂在 1002 名下）
 * </pre>
 * 前两行由 {@code @TableLogic} 与显式 {@code eq(verifyStatus, VALID)} 落在同一个
 * {@code selectCount} 里（见 {@link #hasChildren(Long)}），没有手写 SQL。
 *
 * <p>★ <b>为什么「待核验」不能算 children</b>（ticket 的硬口径）：它还没生效，随时可能被判无效 /
 * 被外部自己改后重提，把它算成「下游记录」会让一条<b>没有任何正式下游记录</b>的样本永远不能改判。
 * 具体后果：seed 里 1002 名下有一条 extA 提交的待核验送样 2006，若这里把 pending 也算 children，
 * D2 的 {@code SAMPLE-VERIFY-001} accept 1（对 1002 的 {@code valid → invalid}）会立刻变红
 * —— 那是本票最容易打红别人的地方（SAMPLE-VERIFY-001 完工报告 WARN-4 / §7 坑 4 就点了这句，
 * 要求本票把判据限定成「已生效」）。
 *
 * <p>★ 与 SAMPLE-HINT-001 的切片染色提示<b>同一份计数口径</b>（它那边的 SQL 是
 * {@code e.del_flag='0' AND e.verify_status='valid'} 再 join 样本 {@code s.del_flag='0'}）：
 * 「有效的块才算一块石蜡」。区别只有一处 —— 本类按 sample_id 单条查、不做样本侧的 join，
 * 因为调用方（核验）拿到的就是一条未软删的样本。
 *
 * @author EMBED-MODEL-001
 */
@Component
@RequiredArgsConstructor
public class EmbedChildrenChecker implements SampleChildrenChecker {

    private final EmbedMapper embedMapper;

    /**
     * 该样本名下有没有<b>未删且已核验有效</b>的石蜡块。
     *
     * @param sampleId 样本 id
     * @return 有 → true（样本不许再改判无效）；查不到 / 参数为空 → false（可改判）
     */
    @Override
    public boolean hasChildren(Long sampleId) {
        if (sampleId == null) {
            return false;
        }
        return DataPermissionHelper.ignore(() -> {
            LambdaQueryWrapper<Embed> wrapper = new LambdaQueryWrapper<Embed>()
                .eq(Embed::getSampleId, sampleId)
                // ★ 只数「已生效」的块：待核验（pending）与无效（invalid）都不算
                .eq(Embed::getVerifyStatus, VerifyTransitions.VALID);
            // @TableLogic 自动补 del_flag='0'：软删的块不进这个 count
            return embedMapper.selectCount(wrapper) > 0;
        });
    }

}
