package org.dromara.lqg.embed.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.domain.model.LoginUser;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.helper.DataPermissionHelper;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.lqg.auth.staff.guard.StaffGrantRules;
import org.dromara.lqg.embed.domain.Embed;
import org.dromara.lqg.embed.domain.bo.EmbedFillBo;
import org.dromara.lqg.embed.domain.bo.EmbedVerifyBo;
import org.dromara.lqg.embed.guard.EmbedFillRules;
import org.dromara.lqg.embed.mapper.EmbedMapper;
import org.dromara.lqg.sample.domain.Sample;
import org.dromara.lqg.sample.domain.bo.PatchBody;
import org.dromara.lqg.sample.mapper.SampleMapper;
import org.dromara.lqg.sample.verify.VerifyTransitions;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;

/**
 * 石蜡包埋送样的核验状态机（FLOW:F-EMBED-01.step7，doc/api-contract.md 的
 * {@code PUT /lqg/embed/{id}/verify}）。
 *
 * <p>★★ <b>复用 {@link VerifyTransitions}，不另写一份</b>（ticket §0 口径 4 / §2）：
 * 合法转移与样本主档<b>同一张表</b>（pending→valid、pending→invalid、invalid→pending（外部重提）、
 * invalid→valid；内部录入的直接 valid），判据只有 {@code check(from, to, actorIsInternal)} 一处。
 * 「自己写了一份少了某条转移」正是 ticket 的 counterfeit 点名形态之一。
 *
 * <p>★ <b>判有效要同时满足两条</b>（ticket §2 第 5 条 / accept 4）：
 * <ol>
 *   <li>石蜡块编号<b>非空</b>且<b>全库唯一</b>（撞号 → 拒，accept 4 中间那两段）；</li>
 *   <li>所挂样本<b>已核验有效</b>（样本还是 pending → 拒：一块石蜡挂在一个还没有内部编号的样本上，
 *       导出的「样本编号」是空的）。</li>
 * </ol>
 * <b>判无效必须写原因</b>。任何一条不满足都拒，且<b>库里什么都不变</b> ——
 * 全部校验在任何写操作之前，整段一个事务（accept 4 断的 {@code pending|-} 就是这个）。
 *
 * <p>★ <b>入参里没有 {@code verifyStatus}</b>：目标状态只能由 {@code action} 经转移表推出来。
 *
 * <p>★ FIX V02b（issue #147）：核验请求可以带 {@code fill}（核验抽屉里补填 / 改过的内容），与核验结论
 * <b>拼进同一条 UPDATE</b>，marker 在同一事务里整组替换。校验与落库列都走 {@link EmbedFillWriter}
 * （与普通保存 {@code PUT /lqg/embed} 同一份），并且在任何写库之前：被拒时库里一个字都不变。
 * <ul>
 *   <li>判为有效并保存：补填段 15 项都收（{@code UI:admin.embed.list}「判为有效并保存」）；</li>
 *   <li>判为无效：只收外部送样填的两项（样本类型、类器官来源类型）的更正；实验室补填的 13 项
 *       （{@link EmbedFillRules#LAB_KEYS}）是「实验室核验有效后填」的（{@code UI:mp.embed.form}），
 *       带了 → 400 并写明哪几项，不静默丢 —— 同样本核验「判无效的样本不补收样信息」；</li>
 *   <li>不带 {@code fill} = 补填段一个字都不动（老调用方、accept 脚本行为不变）。</li>
 * </ul>
 *
 * @author EMBED-MODEL-001
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmbedVerifyService {

    private final EmbedMapper embedMapper;
    private final SampleMapper sampleMapper;
    private final EmbedBlockNoGuard blockNoGuard;
    /** 补填段唯一的校验与落库列（与普通保存同一份，FIX V02b） */
    private final EmbedFillWriter fillWriter;

    /**
     * 核验 / 改判（{@code PUT /lqg/embed/{id}/verify}）。
     *
     * @param id   石蜡包埋记录 id
     * @param bo   action=valid|invalid 与核验段字段
     * @param fill 核验抽屉里一并保存的补填段（补丁：出现过哪些键 + 值）；{@code null} = 补填段不动
     */
    @Transactional(rollbackFor = Exception.class)
    public void verify(Long id, EmbedVerifyBo bo, PatchBody<EmbedFillBo> fill) {
        checkRequest(id, bo);
        verifyAs(id, bo, fill, currentUserIsInternal(), LoginHelper.getUserId());
    }

    /**
     * {@link #verify} 的主体（包内可见：契约测试不起 Sa-Token 上下文，直接给定操作者身份调它）。
     *
     * @param actorIsInternal 操作者是不是内部人员（只用于判转移表）
     * @param operatorId      操作者 user_id（写 verify_by / update_by）
     */
    void verifyAs(Long id, EmbedVerifyBo bo, PatchBody<EmbedFillBo> fill, boolean actorIsInternal, Long operatorId) {
        checkRequest(id, bo);
        // 目标状态只由 action 经转移表推出来：请求体里夹带 verifyStatus 不生效
        String target = VerifyTransitions.targetOf(StringUtils.trim(bo.getAction()));

        DataPermissionHelper.ignore(() -> {
            Embed embed = embedMapper.selectById(id);
            if (embed == null) {
                // 已软删的行 selectById 也查不到（@TableLogic）→ 与「不存在」同样处理
                throw new ServiceException("石蜡包埋记录不存在（或已删除）");
            }
            String from = embed.getVerifyStatus();
            // ① 先判转移表（不合法就到此为止 —— 库里一个字都不动）
            if (!VerifyTransitions.check(from, target, actorIsInternal)) {
                throw new ServiceException(VerifyTransitions.rejectionMessage(from, target, actorIsInternal));
            }
            if (VerifyTransitions.VALID.equals(target)) {
                applyValid(embed, bo, fill, operatorId);
            } else {
                applyInvalid(embed, bo, fill, operatorId);
            }
            return null;
        });
    }

    // ── 私有 ─────────────────────────────────────────────────────────────────

    /**
     * 与原先 {@code @Valid} + 动作白名单同样的前置判断、同样的报错（老调用方看到的一个字都不变）。
     */
    private static void checkRequest(Long id, EmbedVerifyBo bo) {
        if (id == null) {
            throw new ServiceException("缺少石蜡包埋记录 id");
        }
        if (bo == null) {
            throw new ServiceException("请求体不能为空");
        }
        String action = StringUtils.trim(bo.getAction());
        if (StringUtils.isBlank(action)) {
            throw new ServiceException("核验动作不能为空");
        }
        if (!VerifyTransitions.isKnownAction(action)) {
            throw new ServiceException("核验动作只能是 valid（判有效）或 invalid（判无效）");
        }
    }

    /**
     * 判有效：石蜡块编号必填 + 全库唯一、所挂样本必须已核验有效；带了补填段就一起校验。
     * 全部通过后落编号与核验人、清无效原因，补填段拼进<b>同一条</b> UPDATE，marker 同一事务整组替换。
     */
    private void applyValid(Embed embed, EmbedVerifyBo bo, PatchBody<EmbedFillBo> fill, Long operatorId) {
        String blockNo = EmbedBlockNoGuard.normalized(bo.getParaffinBlockNo());
        if (blockNo == null) {
            throw new ServiceException("判有效必须填石蜡块编号");
        }
        // 所挂样本必须已核验有效（否则导出的「样本编号」是空的 —— accept 4 第 1 段）
        Sample sample = sampleMapper.selectById(embed.getSampleId());
        if (sample == null) {
            throw new ServiceException("所挂样本不存在（或已删除）");
        }
        if (!VerifyTransitions.VALID.equals(sample.getVerifyStatus())) {
            throw new ServiceException("所挂样本还未核验有效（当前状态：" + sample.getVerifyStatus()
                + "），不能判为有效");
        }
        blockNoGuard.requireUnique(blockNo, embed.getId());
        // 补填段（FIX V02b）：与普通保存同一份规则，校验完才写库
        EmbedFillWriter.Prepared prepared = prepareFill(fill, VerifyTransitions.VALID);
        Date now = new Date();
        LambdaUpdateWrapper<Embed> patch = new LambdaUpdateWrapper<Embed>()
            .eq(Embed::getId, embed.getId())
            .set(Embed::getUpdateBy, operatorId)
            .set(Embed::getUpdateTime, now)
            .set(Embed::getVerifyStatus, VerifyTransitions.VALID)
            .set(Embed::getVerifyBy, operatorId)
            .set(Embed::getVerifyTime, now)
            // 改判有效必须清掉旧的 invalid_reason
            .set(Embed::getInvalidReason, null)
            .set(Embed::getParaffinBlockNo, blockNo);
        fillWriter.applyTo(patch, prepared);
        embedMapper.update(null, patch);
        fillWriter.replaceMarkers(embed.getId(), prepared);
        log.info("石蜡包埋判有效：id={} blockNo={} verifyBy={} 一并保存的补填键={}", embed.getId(), blockNo, operatorId,
            fill == null ? "[]" : fill.keys());
    }

    /**
     * 判无效：原因必填；带了补填段只收样本类型、类器官来源类型（外部送样填的两项），拼进同一条 UPDATE。
     */
    private void applyInvalid(Embed embed, EmbedVerifyBo bo, PatchBody<EmbedFillBo> fill, Long operatorId) {
        String reason = StringUtils.isBlank(bo.getReason()) ? null : bo.getReason().trim();
        if (reason == null) {
            throw new ServiceException("判无效必须写原因");
        }
        EmbedFillWriter.Prepared prepared = prepareFill(fill, VerifyTransitions.INVALID);
        Date now = new Date();
        LambdaUpdateWrapper<Embed> patch = new LambdaUpdateWrapper<Embed>()
            .eq(Embed::getId, embed.getId())
            .set(Embed::getUpdateBy, operatorId)
            .set(Embed::getUpdateTime, now)
            .set(Embed::getVerifyStatus, VerifyTransitions.INVALID)
            .set(Embed::getInvalidReason, reason)
            .set(Embed::getVerifyBy, operatorId)
            .set(Embed::getVerifyTime, now);
        fillWriter.applyTo(patch, prepared);
        embedMapper.update(null, patch);
        fillWriter.replaceMarkers(embed.getId(), prepared);
        log.info("石蜡包埋判无效：id={} reason={} verifyBy={} 一并保存的补填键={}", embed.getId(), reason, operatorId,
            fill == null ? "[]" : fill.keys());
    }

    /**
     * 核验时一并保存的补填段：先按核验结论判「收哪些键」，再走与普通保存同一份的校验（{@link EmbedFillWriter#prepare}）。
     * 都在任何写库之前。
     *
     * @return 校验过的补丁；没带补填段 → {@code null}（补填段不动）
     */
    private EmbedFillWriter.Prepared prepareFill(PatchBody<EmbedFillBo> fill, String target) {
        if (fill == null) {
            return null;
        }
        List<String> banned = EmbedFillRules.notAllowedInFill(fill.keys());
        if (!banned.isEmpty()) {
            throw new ServiceException(EmbedFillRules.MSG_PREFIX + "核验时不能改" + EmbedFillRules.labelsOf(banned)
                + "（所挂样本核验时不能更换；石蜡块编号用请求顶层的 paraffinBlockNo）", 400);
        }
        if (VerifyTransitions.INVALID.equals(target)) {
            List<String> lab = EmbedFillRules.labKeysIn(fill.keys());
            if (!lab.isEmpty()) {
                throw new ServiceException("判为无效只保存原因，以及样本类型、类器官来源类型的更正；"
                    + EmbedFillRules.labelsOf(lab) + "是核验有效后才补填的，这次不会保存——"
                    + "请撤销这些改动（关掉抽屉重新打开即可），或改为「判为有效并保存」", 400);
            }
        }
        return fillWriter.prepare(fill);
    }

    /**
     * 当前操作者是不是内部人员（ADR-0003：内外部只由角色决定），复用 AUTH-STAFF-001 的纯函数。
     * 它只用于判转移表，绝不写回 {@code submit_source}（那一列是提交当时的快照）。
     */
    private boolean currentUserIsInternal() {
        LoginUser loginUser = LoginHelper.getLoginUser();
        return loginUser != null && StaffGrantRules.isInternal(loginUser.getRolePermission());
    }

}
