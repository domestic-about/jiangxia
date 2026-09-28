package org.dromara.lqg.sample.verify;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.domain.model.LoginUser;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.helper.DataPermissionHelper;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.lqg.auth.staff.guard.StaffGrantRules;
import org.dromara.lqg.sample.domain.Sample;
import org.dromara.lqg.sample.domain.bo.SampleSubmitSegmentBo;
import org.dromara.lqg.sample.guard.SampleChildrenCheckers;
import org.dromara.lqg.sample.guard.SampleKindRules;
import org.dromara.lqg.sample.guard.SubmitSegmentRules;
import org.dromara.lqg.sample.guard.SubmitSegmentRules.UnitRef;
import org.dromara.lqg.sample.guard.SubmitSegmentRules.Writer;
import org.dromara.lqg.sample.mapper.SampleMapper;
import org.dromara.lqg.sample.service.SampleSubmitSegmentWriter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 样本核验状态机（FLOW:F-SAMPLE-01.step3 / step4 / step5，ticket §2）。
 *
 * <p>★ controller（本票的 {@code PUT /lqg/sample/{id}/verify}）与外部接口（AUTH-EXT-001）**都只能经本类**
 * 改 {@code verify_status}：状态的唯一出口在这里，判据的唯一来源是纯函数
 * {@link VerifyTransitions#check(String, String, boolean)}。
 *
 * <p>★ 本张最容易做反的四点，逐条对着 accept 抄：
 * <ol>
 *   <li><b>判有效必须同时给 {@code receiveDate} 与 {@code internalNo}</b>（且内部编号全库唯一）；
 *       <b>判无效必须给 {@code reason}</b>。缺了就拒，且<b>库里什么都不变</b> —— 全部校验都在
 *       任何写操作之前，整段一个事务（accept 第 1 条第 5 段断的就是「三次被拒后仍是 pending|-|-」）；</li>
 *   <li><b>{@code submit_source} 是提交当时的快照</b>：本类**从不重算**它 —— 核验、改判、重提都不碰
 *       这一列，也不按提交人当前角色回填。外部升级成内部之后，他以前送的样本仍是 {@code external}
 *       （accept 第 2 条）；</li>
 *   <li><b>状态只能由内部改</b>：外部不能把自己的样本改成 {@code valid}；外部重提只会回到
 *       {@code pending}（{@link #resubmitByExternal}）；{@code invalid → valid} 这条内部改判路径
 *       **放开**，且改判有效时<b>必须清掉旧的 {@code invalid_reason}</b>；</li>
 *   <li><b>状态机不分 {@code sample_kind}</b>：类器官走同一张转移表（CR-20260917-05）——
 *       本类里没有任何 {@code if (organoid)} 分支决定状态，只有「送检段该写哪几列」按类别分化。</li>
 * </ol>
 *
 * <p>★ FIX V02（issue #147）：核验请求可以带 {@code submitSegment}（核验抽屉里改过的送检段），
 * 与核验结论<b>拼进同一条 UPDATE</b>：先校验送检段（{@code SubmitSegmentRules}，与工作台修改同一份）、
 * 再判转移与必填，全部通过才写库 —— 被拒时库里一个字都不变。送检段的列由
 * {@link SampleSubmitSegmentWriter} 写（与工作台修改、外部重提同一组列）。
 *
 * <p>★ FIX V03：本类的业务拒绝一律 {@code code=400}（样本不存在 404），不再是默认的 500。
 *
 * @author SAMPLE-VERIFY-001
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SampleVerifyService {

    private final SampleMapper sampleMapper;
    private final SampleChildrenCheckers childrenCheckers;
    private final SampleSubmitSegmentWriter segmentWriter;

    /**
     * 核验（{@code PUT /lqg/sample/{id}/verify}）：pending→valid / pending→invalid /
     * invalid→valid / valid→invalid 四条**内部**转移都走这里。
     *
     * @param id 样本 id
     * @param bo action=valid|invalid 与核验段字段
     */
    @Transactional(rollbackFor = Exception.class)
    public void verify(Long id, SampleVerifyBo bo) {
        if (id == null) {
            throw new ServiceException("缺少样本 id", 400);
        }
        if (bo == null) {
            throw new ServiceException("请求体不能为空", 400);
        }
        String action = StringUtils.trim(bo.getAction());
        if (!VerifyTransitions.isKnownAction(action)) {
            throw new ServiceException("核验动作只能是 valid（判有效）或 invalid（判无效）", 400);
        }
        verifyAs(id, bo, currentUserIsInternal(), LoginHelper.getUserId());
    }

    /**
     * {@link #verify} 的主体（包内可见：契约测试不起 Sa-Token 上下文，直接给定操作者身份调它）。
     *
     * @param actorIsInternal 操作者是不是内部人员（只用于判转移表）
     * @param operatorId      操作者 user_id（写 verify_by / update_by）
     */
    void verifyAs(Long id, SampleVerifyBo bo, boolean actorIsInternal, Long operatorId) {
        if (id == null) {
            throw new ServiceException("缺少样本 id", 400);
        }
        if (bo == null) {
            throw new ServiceException("请求体不能为空", 400);
        }
        String action = StringUtils.trim(bo.getAction());
        if (!VerifyTransitions.isKnownAction(action)) {
            throw new ServiceException("核验动作只能是 valid（判有效）或 invalid（判无效）", 400);
        }
        // 目标状态只由 action 经转移表推出来：请求体里夹带 verifyStatus 不生效
        String target = VerifyTransitions.targetOf(action);

        DataPermissionHelper.ignore(() -> {
            Sample sample = sampleMapper.selectById(id);
            if (sample == null) {
                // 已软删的行 selectById 也查不到（@TableLogic）→ 与「不存在」同样处理
                throw new ServiceException("样本不存在（或已删除）", 404);
            }
            String from = sample.getVerifyStatus();
            // ① 先判转移表（不合法就到此为止 —— 库里一个字都不动）
            if (!VerifyTransitions.check(from, target, actorIsInternal)) {
                throw new ServiceException(VerifyTransitions.rejectionMessage(from, target, actorIsInternal), 400);
            }
            // ② 送检段（核验抽屉一并保存，FIX V02）：校验 + 解析单位名快照，都在任何写库之前
            SegmentWrite segment = prepareSegment(sample, bo.getSubmitSegment());
            if (VerifyTransitions.VALID.equals(target)) {
                applyValid(sample, bo, operatorId, segment);
            } else {
                applyInvalid(sample, bo, operatorId, segment);
            }
            return null;
        });
    }

    /**
     * 外部「修改送检段后重提」（FLOW:F-SAMPLE-01.step4，ticket §2）——
     * <b>只写 {@link SampleResubmitBo}</b>，写不到核验段、也写不到 {@code verify_status} 的终态。
     *
     * <p>三条规则：
     * <ul>
     *   <li>只能重提<b>本人</b>提交的样本（{@code submitter_id == userId}）；</li>
     *   <li>只有 {@code pending} / {@code invalid} 能重提 —— {@code valid} 的样本外部只读，
     *       {@code invalid → pending} 经 {@link VerifyTransitions#check} 判（外部重提那条边）；</li>
     *   <li>{@code pending} 的改完仍是 {@code pending}（不是状态转移，只是内容变了）；
     *       {@code invalid} 的回到 {@code pending}，并清空 {@code invalid_reason / verify_by / verify_time}。</li>
     * </ul>
     *
     * <p>★ {@code submit_source} / {@code submitter_id} / {@code submit_no} / {@code internal_no} /
     * {@code receive_date} 一律不写：外部改不动它们，也不按当前角色重算来源。
     *
     * <p>★ 语义 = 「送检段整体替换」（与 AUTH-GROUP-001 的 {@code PUT /mp/ext/profile} 同款）：
     * 没传的送检段字段按清空处理，外部填写页是整份提交的。本类只写**本类别**的送检段列
     * （tissue：来源单位、供体姓名、性别、年龄、住院号、组织类型、有无病理、备注；
     * organoid：来源单位、类器官类型、代数、备注）。
     *
     * @param sampleId 样本 id
     * @param userId   提交人 user_id（外部 controller 从 token 取，不从请求体取）
     * @param bo       送检段字段
     */
    @Transactional(rollbackFor = Exception.class)
    public void resubmitByExternal(Long sampleId, Long userId, SampleResubmitBo bo) {
        if (sampleId == null) {
            throw new ServiceException("缺少样本 id", 400);
        }
        if (userId == null) {
            throw new ServiceException("取不到当前登录用户，无法重提");
        }
        SampleResubmitBo fields = bo == null ? new SampleResubmitBo() : bo;
        DataPermissionHelper.ignore(() -> {
            Sample sample = sampleMapper.selectById(sampleId);
            if (sample == null) {
                throw new ServiceException("样本不存在", 404);
            }
            if (!userId.equals(sample.getSubmitterId())) {
                throw new ServiceException("只能修改重提本人提交的样本", 400);
            }
            String from = sample.getVerifyStatus();
            boolean backToPending = VerifyTransitions.INVALID.equals(from);
            if (!backToPending && !VerifyTransitions.PENDING.equals(from)) {
                // valid（或任何其它值）的样本外部只读：这里不给「重提」留后门
                throw new ServiceException("样本当前是「" + from + "」，外部不能修改重提（只有待核验、无效的样本可以）", 400);
            }
            if (backToPending && !VerifyTransitions.check(from, VerifyTransitions.PENDING, false)) {
                // 转移表是唯一判据：这一句在表变动时立刻生效，不是硬编码的假设
                throw new ServiceException(
                    VerifyTransitions.rejectionMessage(from, VerifyTransitions.PENDING, false), 400);
            }
            String kind = SampleKindRules.normalize(sample.getSampleKind());
            // 送检段的必填与格式（外部口径：组织样本多一个供体姓名必填），任何写库之前
            SubmitSegmentRules.throwIfAny(SubmitSegmentRules.submitViolations(kind, fields, Writer.EXTERNAL));
            // 单位归属已由调用方（ExtSampleSubmitService）按「外部只能挂自己可用的单位」定好；
            // 这里只把 id 换成单位表的当前名称快照（同一份写法）
            UnitRef unit = segmentWriter.resolveInternalUnit(fields.getSourceUnitId(), fields.getSourceUnitName());
            LambdaUpdateWrapper<Sample> patch = new LambdaUpdateWrapper<Sample>()
                .eq(Sample::getId, sampleId)
                // 用 `update(null, wrapper)` 时必须显式补 update_by / update_time：MP 的 updateFill
                // 只在参数对象是 BaseEntity 时才填 update_by（SAMPLE-MODEL-001 坑 1）。
                .set(Sample::getUpdateBy, userId)
                .set(Sample::getUpdateTime, new Date());
            // 送检段：与工作台修改、核验抽屉同一组列（SampleSubmitSegmentWriter）
            segmentWriter.applyTo(patch, kind, fields, unit);
            if (backToPending) {
                patch.set(Sample::getVerifyStatus, VerifyTransitions.PENDING)
                    .set(Sample::getInvalidReason, null)
                    .set(Sample::getVerifyBy, null)
                    .set(Sample::getVerifyTime, null);
            }
            sampleMapper.update(null, patch);
            log.info("外部重提样本：id={} userId={} kind={} status {} → {}",
                sampleId, userId, kind, from, backToPending ? VerifyTransitions.PENDING : from);
            return null;
        });
    }

    // ── 私有 ─────────────────────────────────────────────────────────────────

    /**
     * 核验时一并保存的送检段（FIX V02）：校验过、单位名快照已解析好，等着拼进核验那条 UPDATE。
     *
     * @param kind 样本自己的类别（送检段按它写本类别的列）
     * @param seg  请求里的送检段
     * @param unit 解析好的来源单位
     */
    private record SegmentWrite(String kind, SampleSubmitSegmentBo seg, UnitRef unit) {
    }

    /**
     * 请求带了送检段 → 按工作台修改同一份规则校验（内部口径），并解析来源单位；没带 → null（送检段不动）。
     */
    private SegmentWrite prepareSegment(Sample sample, SampleSubmitSegmentBo seg) {
        if (seg == null) {
            return null;
        }
        String kind = SampleKindRules.normalize(sample.getSampleKind());
        SubmitSegmentRules.throwIfAny(SubmitSegmentRules.submitViolations(kind, seg, Writer.INTERNAL));
        UnitRef unit = segmentWriter.resolveInternalUnit(seg.getSourceUnitId(), seg.getSourceUnitName());
        return new SegmentWrite(kind, seg, unit);
    }

    /**
     * 判有效：收样日期 + 内部编号**两个都必填**、内部编号全库唯一（软删后可重用），
     * 通过后一并落核验段字段，并清掉旧的无效原因；带了送检段就拼进同一条 UPDATE。
     */
    private void applyValid(Sample sample, SampleVerifyBo bo, Long operatorId, SegmentWrite segment) {
        List<String> missing = new ArrayList<>();
        if (bo.getReceiveDate() == null) {
            missing.add("收样日期");
        }
        if (StringUtils.isBlank(bo.getInternalNo())) {
            missing.add("内部编号");
        }
        if (!missing.isEmpty()) {
            throw new ServiceException("判有效必须同时给收样日期与内部编号，缺少：" + String.join("、", missing), 400);
        }
        SubmitSegmentRules.throwIfAny(SubmitSegmentRules.receiveViolations(bo.getInternalNo(), bo.getIsFixed(),
            bo.getHasQcSheet(), bo.getHasViabilityReport(), bo.getOperatorName()));
        String internalNo = SampleKindRules.normalize(bo.getInternalNo());
        requireInternalNoUnique(internalNo, sample.getId());
        Date now = new Date();
        LambdaUpdateWrapper<Sample> patch = new LambdaUpdateWrapper<Sample>()
            .eq(Sample::getId, sample.getId())
            .set(Sample::getUpdateBy, operatorId)
            .set(Sample::getUpdateTime, now)
            .set(Sample::getVerifyStatus, VerifyTransitions.VALID)
            .set(Sample::getVerifyBy, operatorId)
            .set(Sample::getVerifyTime, now)
            // ★ 改判有效必须清掉旧的 invalid_reason（invalid→valid 是「内部直接改判」这条路径）
            .set(Sample::getInvalidReason, null)
            .set(Sample::getReceiveDate, bo.getReceiveDate())
            .set(Sample::getInternalNo, internalNo)
            // 核验段其余字段：给了就落（没给的保持原值 —— 判有效是个动作，不是整份表单替换）
            .set(bo.getIsFixed() != null, Sample::getIsFixed, trimToNull(bo.getIsFixed()))
            .set(bo.getProcessTime() != null, Sample::getProcessTime, bo.getProcessTime())
            .set(bo.getHasQcSheet() != null, Sample::getHasQcSheet, trimToNull(bo.getHasQcSheet()))
            .set(bo.getHasViabilityReport() != null, Sample::getHasViabilityReport,
                trimToNull(bo.getHasViabilityReport()))
            .set(bo.getOperatorName() != null, Sample::getOperatorName, trimToNull(bo.getOperatorName()));
        applySegment(patch, segment);
        sampleMapper.update(null, patch);
        log.info("核验判有效：id={} internalNo={} receiveDate={} verifyBy={} 一并保存送检段={}",
            sample.getId(), internalNo, bo.getReceiveDate(), operatorId, segment != null);
    }

    /**
     * 判无效：原因必填；{@code valid → invalid} 是「误判纠正」，名下已有下游记录时不许改判。
     * 带了送检段就拼进同一条 UPDATE（收样段不落：判无效的样本不补收样信息）。
     */
    private void applyInvalid(Sample sample, SampleVerifyBo bo, Long operatorId, SegmentWrite segment) {
        String reason = trimToNull(bo.getReason());
        if (reason == null) {
            throw new ServiceException("判无效必须写原因", 400);
        }
        if (reason.codePointCount(0, reason.length()) > SubmitSegmentRules.MAX_INVALID_REASON) {
            throw new ServiceException("无效原因不能超过 " + SubmitSegmentRules.MAX_INVALID_REASON + " 字", 400);
        }
        // FLOW:F-SAMPLE-01.step5：valid→invalid 只在「该样本名下没有包埋 / 冻存 / 质控文档」时允许。
        // 下游三张表分别属于 EMBED / CRYO / QC 的票，实现由它们各自注册（本票预期注册数 = 0）。
        if (VerifyTransitions.VALID.equals(sample.getVerifyStatus()) && childrenCheckers.hasChildren(sample.getId())) {
            throw new ServiceException("该样本名下已有包埋 / 冻存 / 质控文档，不能改判无效", 400);
        }
        Date now = new Date();
        LambdaUpdateWrapper<Sample> patch = new LambdaUpdateWrapper<Sample>()
            .eq(Sample::getId, sample.getId())
            .set(Sample::getUpdateBy, operatorId)
            .set(Sample::getUpdateTime, now)
            .set(Sample::getVerifyStatus, VerifyTransitions.INVALID)
            .set(Sample::getInvalidReason, reason)
            .set(Sample::getVerifyBy, operatorId)
            .set(Sample::getVerifyTime, now);
        applySegment(patch, segment);
        sampleMapper.update(null, patch);
        log.info("核验判无效：id={} reason={} verifyBy={} 一并保存送检段={}",
            sample.getId(), reason, operatorId, segment != null);
    }

    private void applySegment(LambdaUpdateWrapper<Sample> patch, SegmentWrite segment) {
        if (segment != null) {
            segmentWriter.applyTo(patch, segment.kind(), segment.seg(), segment.unit());
        }
    }

    /**
     * 内部编号全库唯一，但**软删后可重用**：查重走 mapper（{@code @TableLogic} 只查
     * {@code del_flag='0'}），被软删的旧行不挡新编号，部分唯一索引兜底。
     *
     * <p>★ accept 第 1 条第 3 段用的是 seed 里真实存在的 {@code T-hli01}（挂在 1001 上）——
     * 「先 UPDATE 再校验」的实现在那里立刻红。
     */
    private void requireInternalNoUnique(String internalNo, Long excludeId) {
        if (StringUtils.isBlank(internalNo)) {
            return;
        }
        LambdaQueryWrapper<Sample> wrapper = new LambdaQueryWrapper<Sample>()
            .eq(Sample::getInternalNo, internalNo)
            .ne(excludeId != null, Sample::getId, excludeId);
        if (sampleMapper.selectCount(wrapper) > 0) {
            throw new ServiceException("内部编号「" + internalNo + "」已存在，请换一个", 400);
        }
    }

    /**
     * 当前操作者是不是内部人员。
     *
     * <p>判据 = token 里的**角色键**（ADR-0003：内外部只由角色决定，不看 {@code user_type}），
     * 复用 AUTH-STAFF-001 的纯函数 {@link StaffGrantRules#isInternal(java.util.Set)}。
     *
     * <p>★ 这里算出来的身份**只用于判转移表**，绝不写回 {@code submit_source}：那一列是提交当时的快照。
     */
    private boolean currentUserIsInternal() {
        LoginUser loginUser = LoginHelper.getLoginUser();
        return loginUser != null && StaffGrantRules.isInternal(loginUser.getRolePermission());
    }

    private static String trimToNull(String value) {
        return StringUtils.isBlank(value) ? null : value.trim();
    }

}
