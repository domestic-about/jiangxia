package org.dromara.lqg.qc.guard;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.dromara.common.mybatis.helper.DataPermissionHelper;
import org.dromara.lqg.qc.domain.QcOrganoidDoc;
import org.dromara.lqg.qc.domain.QcSampleDoc;
import org.dromara.lqg.qc.domain.QcScoreDoc;
import org.dromara.lqg.qc.mapper.QcOrganoidDocMapper;
import org.dromara.lqg.qc.mapper.QcSampleDocMapper;
import org.dromara.lqg.qc.mapper.QcScoreDocMapper;
import org.dromara.lqg.qc.service.QcDocRules;
import org.dromara.lqg.sample.guard.SampleChildrenChecker;
import org.springframework.stereotype.Component;

/**
 * 「这个样本名下有没有质控文档」—— 注册给 SAMPLE-VERIFY-001 的
 * {@link SampleChildrenChecker} 扩展点（QC-MODEL-001 的实现）。
 *
 * <p>★ <b>它一注册，{@code valid → invalid} 这条边就活了</b>：样本名下只要有一份
 * <b>已完成（{@code published}）</b>的质控文档，内部就不能把它改判无效。
 *
 * <p>★★ <b>判据（全仓只有这一处，必须写清楚）</b>：
 * <pre>
 * SELECT count(*) FROM t_lqg_qc_sample   WHERE sample_id = ? AND del_flag = '0' AND doc_status = 'published'
 *   OR ...
 * SELECT count(*) FROM t_lqg_qc_organoid WHERE sample_id = ? AND del_flag = '0' AND doc_status = 'published'
 *   OR ...
 * SELECT count(*) FROM t_lqg_qc_score    WHERE sample_id = ? AND del_flag = '0' AND doc_status = 'published'
 * </pre>
 * 三张表任意一张有 → true。{@code del_flag='0'} 由实体上的 {@code @TableLogic} 在一个
 * {@code selectCount} 里兜住；{@code doc_status='published'} 是显式条件。
 *
 * <p>★★ <b>为什么「自动建出来的空草稿」不算 children</b>（ticket §2 末条）：
 * {@code GET /lqg/qc/{sampleId}} 一打开就给三份文档各建一份空草稿 —— 那是<b>看一眼</b>
 * 的副作用，不是「有下游记录」。若把 draft 也算上，任何人在工作台上点开过一次质控文档，
 * 这个样本就永远不能改判无效了（SAMPLE-VERIFY-001 的 {@code valid → invalid} 会莫名其妙地红）。
 * <b>这不是「只靠 del_flag 就够」的简化</b>：EMBED 的判据叠了 {@code verify_status='valid'}、
 * CRYO 只有软删一个维度，QC 叠的是 {@code doc_status='published'} —— 逐表照该表的「生效」定义写。
 *
 * <p>★ <b>为什么这不会打红 D2/D3/D4 的既有 accept</b>：
 * SAMPLE-VERIFY-001 把 <b>1002</b> 判无效、EMBED-MODEL-001 对 <b>1004</b> 断「改判被拒」——
 * 1002 名下没有任何质控文档；1004 名下只有一份 <b>published</b> 的样本质控表（seed 的
 * 9000005002），它本来就已经被冻存批次 3005 挡住，判据再叠一层不改变结论。
 * 而 seed 里 <b>1005</b>（只有 draft）本判据恒 false —— 那一行就是「草稿不算」的活证据。
 *
 * @author QC-MODEL-001
 */
@Component
@RequiredArgsConstructor
public class QcChildrenChecker implements SampleChildrenChecker {

    private final QcSampleDocMapper sampleDocMapper;
    private final QcOrganoidDocMapper organoidDocMapper;
    private final QcScoreDocMapper scoreDocMapper;

    /**
     * 该样本名下有没有<b>未删且已完成</b>的质控文档。
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
            if (sampleDocMapper.selectCount(new LambdaQueryWrapper<QcSampleDoc>()
                .eq(QcSampleDoc::getSampleId, sampleId)
                .eq(QcSampleDoc::getDocStatus, QcDocRules.STATUS_PUBLISHED)) > 0) {
                return true;
            }
            if (organoidDocMapper.selectCount(new LambdaQueryWrapper<QcOrganoidDoc>()
                .eq(QcOrganoidDoc::getSampleId, sampleId)
                .eq(QcOrganoidDoc::getDocStatus, QcDocRules.STATUS_PUBLISHED)) > 0) {
                return true;
            }
            return scoreDocMapper.selectCount(new LambdaQueryWrapper<QcScoreDoc>()
                .eq(QcScoreDoc::getSampleId, sampleId)
                .eq(QcScoreDoc::getDocStatus, QcDocRules.STATUS_PUBLISHED)) > 0;
        });
    }

}
