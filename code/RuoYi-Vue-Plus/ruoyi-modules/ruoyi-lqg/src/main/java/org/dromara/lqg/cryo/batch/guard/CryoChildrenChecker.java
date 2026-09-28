package org.dromara.lqg.cryo.batch.guard;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.dromara.common.mybatis.helper.DataPermissionHelper;
import org.dromara.lqg.cryo.batch.domain.CryoBatch;
import org.dromara.lqg.cryo.batch.mapper.CryoBatchMapper;
import org.dromara.lqg.sample.guard.SampleChildrenChecker;
import org.springframework.stereotype.Component;

/**
 * 「这个样本名下有没有冻存批次」—— 注册给 SAMPLE-VERIFY-001 的
 * {@link SampleChildrenChecker} 扩展点（CRYO-MODEL-001 的实现）。
 *
 * <p>★ <b>它一注册，{@code valid → invalid} 这条边就活了</b>：样本名下只要有一个
 * <b>未删（= 已生效）</b>的冻存批次，内部就不能把它改判无效
 * （D4 的 L3「有冻存批次的样本改判无效被拒」、本票 accept 2 之外的那条口径）。
 *
 * <p>★★ <b>判据（全仓只有这一处）</b>：
 * <pre>
 * SELECT count(*) FROM t_lqg_cryo_batch
 *  WHERE sample_id = ?
 *    AND del_flag = '0'          -- 软删的批次不算（seed 的 3008 是软删的，挂在 1008 名下）
 * </pre>
 *
 * <p>★ <b>为什么冻存的判据里没有 {@code verify_status}</b>（与 {@link
 * org.dromara.lqg.embed.guard.EmbedChildrenChecker} 的<b>唯一差异</b>，必须写清楚）：
 * {@code t_lqg_cryo_batch} <b>没有核验状态列</b> —— 冻存批次是内部人员建的，建出来即生效，
 * 没有「待核验」这一档（外部提交冻存是合同外的，不存在）。所以这张表的「已生效」就等价于
 * 「{@code del_flag='0'}」，由实体上的 {@code @TableLogic} 在一个 {@code selectCount} 里兜住。
 * <b>不是</b>「只靠 del_flag 就够」的简化：{@code SampleChildrenChecker} 的三张下游表里，
 * EMBED 需要显式叠 {@code verify_status='valid'}、QC 未建、CRYO 只有软删这一个维度
 * —— 判据必须逐表照该表的「生效」定义写。
 *
 * <p>★ <b>为什么这不会打红 D2</b>：{@code SAMPLE-VERIFY-001} accept 1 对 <b>1002</b> 做
 * {@code valid → invalid}，而 1002 名下<b>没有任何冻存批次</b>（3001/3004 挂 1001、
 * 3005 挂 1004、3002/3007 挂 1009、3003/3006/3008 挂 1008）→ 本判据恒 false，仍然放行。
 * 1004（名下有 3005）会因此不能被改判无效 —— 那是 D4 预期的行为，不是回归。
 *
 * <p>★ <b>与超期提醒 / 小程序计数同源</b>：「这个样本冻了几批」只看未删批次
 * （CRYO-REMIND-001 的计数另加「剩余 &gt; 0」「没转液氮」等条件，那是<b>别的</b>问题，
 * 不要混进来 —— 取空的批次仍是一条有效的下游记录，仍要能查到它的流水）。
 *
 * @author CRYO-MODEL-001
 */
@Component
@RequiredArgsConstructor
public class CryoChildrenChecker implements SampleChildrenChecker {

    private final CryoBatchMapper cryoBatchMapper;

    /**
     * 该样本名下有没有<b>未删</b>的冻存批次。
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
            LambdaQueryWrapper<CryoBatch> wrapper = new LambdaQueryWrapper<CryoBatch>()
                .eq(CryoBatch::getSampleId, sampleId);
            // @TableLogic 自动补 del_flag='0'：软删的批次不进这个 count
            // （t_lqg_cryo_batch 没有核验状态列 —— 冻存批次建出来即生效，见类注释）
            return cryoBatchMapper.selectCount(wrapper) > 0;
        });
    }

}
