package org.dromara.lqg.embed.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.helper.DataPermissionHelper;
import org.dromara.lqg.embed.domain.Embed;
import org.dromara.lqg.embed.mapper.EmbedMapper;
import org.dromara.lqg.ext.service.ExtScopeService;
import org.dromara.lqg.sample.guard.SubmitSegmentRules;
import org.dromara.lqg.sample.verify.VerifyTransitions;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;

/**
 * 外部提交 / 重提石蜡包埋送样的<b>写侧 service</b>（FLOW:F-EMBED-01.step6）——
 * 外部 controller 在 AUTH-EXT-002（{@code /mp/ext/embed/**}），本类只出业务方法
 * （ticket §2：外部送样「只写 service」）。
 *
 * <p>★ <b>只收两个字段</b>（样本类型 / 类器官来源类型）：{@link #submit} 的签名就是这两个，
 * 石蜡块编号、工序时间、染色、marker、包埋人、操作人、状态一律<b>不在签名里</b> ——
 * 请求里夹带它们不会生效（不是「读到了再丢掉」，是没有入口）。
 *
 * <p>★ <b>能挂的样本</b>：<b>本人</b>送检过、且<b>没被判无效</b>的样本 ——
 * <b>待核验的样本可以挂</b>（ticket §2 第 4 条；核验有效后这条送样才允许被判有效）。
 * 落库 {@code submit_source='external'}、{@code verify_status='pending'}、
 * {@code paraffin_block_no} 为空、{@code submitter_id=本人}。
 *
 * <p>★ <b>重提</b>：只有<b>本人提交</b>且状态 ∈ {pending, invalid} 的记录能改；
 * 改完回到 {@code pending} 并清 {@code invalid_reason / verify_by / verify_time}。
 * 状态回退经 {@link VerifyTransitions}（外部那条边），不另写一份判据。
 *
 * <p>★ FIX V17（issue #299）：<b>可见性一律经 {@link ExtScopeService}</b>。以前本类直接
 * {@code selectById} 记录与样本，「别人的」回 400、「不存在的」回 404 —— 两者之差就是存在性预言机。
 * 现在：记录先过 {@link ExtScopeService#assertEmbedVisible}、所挂样本先过
 * {@link ExtScopeService#assertUsableForEmbed}，不可见与不存在是同一个 404、同一句话；
 * 只有<b>看得见</b>的记录 / 样本才会走到「不是本人的 → 400」。本类因此不再持有样本的 mapper
 * （{@code EmbedExternalScopeContractTest} 钉住）。
 *
 * <p>★ FIX V03：必填与长度先过 {@link SubmitSegmentRules#externalEmbedViolations}（缺所挂样本、
 * 样本类型超过 50 字等 → 400），不再走到数据库约束上把 SQL 回吐给外部。
 *
 * @author EMBED-MODEL-001
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmbedExternalService {

    private final EmbedMapper embedMapper;
    private final ExtScopeService extScopeService;

    /**
     * 外部提交石蜡包埋送样（{@code POST /mp/ext/embed}）。
     *
     * @param userId             当前登录的外部人员（controller 从 token 取）
     * @param sampleId           所挂样本 id（必须是本人送检、且没被判无效的样本）
     * @param sampleType         样本类型（自由文本）
     * @param organoidSourceType 类器官来源类型（自由文本）
     * @return 新建记录 id
     */
    @Transactional(rollbackFor = Exception.class)
    public Long submit(Long userId, Long sampleId, String sampleType, String organoidSourceType) {
        requireLogin(userId);
        SubmitSegmentRules.throwIfAny(
            SubmitSegmentRules.externalEmbedViolations(sampleId, true, sampleType, organoidSourceType));
        // 能挂哪个样本只在范围解析器里判：不可见 / 不存在 → 同一个 404；可见但不是本人的 / 已无效 → 400
        extScopeService.assertUsableForEmbed(userId, sampleId);
        return DataPermissionHelper.ignore(() -> {
            Embed entity = new Embed();
            entity.setSampleId(sampleId);
            entity.setSubmitSource("external");
            entity.setSubmitterId(userId);
            entity.setVerifyStatus(VerifyTransitions.PENDING);
            entity.setSampleType(trimToNull(sampleType));
            entity.setOrganoidSourceType(trimToNull(organoidSourceType));
            embedMapper.insert(entity);
            log.info("外部提交石蜡包埋送样：id={} sampleId={} submitter={}",
                entity.getId(), sampleId, userId);
            return entity.getId();
        });
    }

    /**
     * 外部修改后重提（{@code PUT /mp/ext/embed/{id}}）—— 不改所挂样本。
     */
    @Transactional(rollbackFor = Exception.class)
    public void resubmit(Long userId, Long embedId, String sampleType, String organoidSourceType) {
        resubmit(userId, embedId, null, sampleType, organoidSourceType);
    }

    /**
     * 外部修改后重提（{@code PUT /mp/ext/embed/{id}}），可一并改所挂样本。
     *
     * <p>★ 校验顺序：可见（不可见 / 不存在 / 软删 → 同一个 404）→ 本人 → 状态 → 字段 → 新挂的样本。
     *
     * @param sampleId 新的样本 id；{@code null} 或与现值相同 = 不动
     */
    @Transactional(rollbackFor = Exception.class)
    public void resubmit(Long userId, Long embedId, Long sampleId, String sampleType, String organoidSourceType) {
        requireLogin(userId);
        // ① 可见性：记录 → 所挂样本 → 可见集合（FIX V17：与读接口同一个判据、同一句 404）
        extScopeService.assertEmbedVisible(userId, embedId);
        DataPermissionHelper.ignore(() -> {
            Embed embed = embedMapper.selectById(embedId);
            if (embed == null) {
                // 与上一步之间被删掉的极端情况：仍按「不存在」回同一句
                throw new ServiceException(ExtScopeService.EMBED_NOT_FOUND, 404);
            }
            // ② 本人（看得见才走到这里：同组的记录可以看、不能改）
            if (!userId.equals(embed.getSubmitterId())) {
                throw new ServiceException("只能修改重提本人提交的送样（同组的可以看，但不能改）", 400);
            }
            // ③ 状态
            String from = embed.getVerifyStatus();
            boolean backToPending = VerifyTransitions.INVALID.equals(from);
            if (!backToPending && !VerifyTransitions.PENDING.equals(from)) {
                throw new ServiceException("送样当前是「" + from + "」，外部不能修改重提（只有待核验、无效的可以）", 400);
            }
            if (backToPending && !VerifyTransitions.check(from, VerifyTransitions.PENDING, false)) {
                // 转移表是唯一判据：这一句在表变动时立刻生效，不是硬编码的假设
                throw new ServiceException(
                    VerifyTransitions.rejectionMessage(from, VerifyTransitions.PENDING, false), 400);
            }
            // ④ 字段（FIX V03）
            SubmitSegmentRules.throwIfAny(
                SubmitSegmentRules.externalEmbedViolations(sampleId, false, sampleType, organoidSourceType));
            LambdaUpdateWrapper<Embed> patch = new LambdaUpdateWrapper<Embed>()
                .eq(Embed::getId, embedId)
                // 显式补 update_by / update_time（wrapper 不自动填 update_by）
                .set(Embed::getUpdateBy, userId)
                .set(Embed::getUpdateTime, new Date())
                .set(Embed::getSampleType, trimToNull(sampleType))
                .set(Embed::getOrganoidSourceType, trimToNull(organoidSourceType));
            // ⑤ 换挂样本：新样本同样只经范围解析器判（不可见 / 不存在 → 同一个 404）
            if (sampleId != null && !sampleId.equals(embed.getSampleId())) {
                extScopeService.assertUsableForEmbed(userId, sampleId);
                patch.set(Embed::getSampleId, sampleId);
            }
            if (backToPending) {
                patch.set(Embed::getVerifyStatus, VerifyTransitions.PENDING)
                    .set(Embed::getInvalidReason, null)
                    .set(Embed::getVerifyBy, null)
                    .set(Embed::getVerifyTime, null);
            }
            embedMapper.update(null, patch);
            log.info("外部重提石蜡包埋送样：id={} userId={} 状态 {} → {}",
                embedId, userId, from, backToPending ? VerifyTransitions.PENDING : from);
            return null;
        });
    }

    // ── 私有 ─────────────────────────────────────────────────────────────────

    private static void requireLogin(Long userId) {
        if (userId == null) {
            throw new ServiceException("未登录", 401);
        }
    }

    private static String trimToNull(String value) {
        return StringUtils.isBlank(value) ? null : value.trim();
    }

}
