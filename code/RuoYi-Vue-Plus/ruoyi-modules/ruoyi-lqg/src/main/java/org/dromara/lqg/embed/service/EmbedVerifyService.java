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
import org.dromara.lqg.embed.domain.bo.EmbedVerifyBo;
import org.dromara.lqg.embed.mapper.EmbedMapper;
import org.dromara.lqg.sample.domain.Sample;
import org.dromara.lqg.sample.mapper.SampleMapper;
import org.dromara.lqg.sample.verify.VerifyTransitions;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;

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
 * @author EMBED-MODEL-001
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmbedVerifyService {

    private final EmbedMapper embedMapper;
    private final SampleMapper sampleMapper;
    private final EmbedBlockNoGuard blockNoGuard;

    /**
     * 核验 / 改判（{@code PUT /lqg/embed/{id}/verify}）。
     *
     * @param id 石蜡包埋记录 id
     * @param bo action=valid|invalid 与核验段字段
     */
    @Transactional(rollbackFor = Exception.class)
    public void verify(Long id, EmbedVerifyBo bo) {
        if (id == null) {
            throw new ServiceException("缺少石蜡包埋记录 id");
        }
        if (bo == null) {
            throw new ServiceException("请求体不能为空");
        }
        String action = StringUtils.trim(bo.getAction());
        if (!VerifyTransitions.isKnownAction(action)) {
            throw new ServiceException("核验动作只能是 valid（判有效）或 invalid（判无效）");
        }
        // 目标状态只由 action 经转移表推出来：请求体里夹带 verifyStatus 不生效
        String target = VerifyTransitions.targetOf(action);
        boolean actorIsInternal = currentUserIsInternal();
        Long operatorId = LoginHelper.getUserId();

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
                applyValid(embed, bo, operatorId);
            } else {
                applyInvalid(embed, bo, operatorId);
            }
            return null;
        });
    }

    // ── 私有 ─────────────────────────────────────────────────────────────────

    /**
     * 判有效：石蜡块编号必填 + 全库唯一、所挂样本必须已核验有效；通过后落编号与核验人、清无效原因。
     */
    private void applyValid(Embed embed, EmbedVerifyBo bo, Long operatorId) {
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
        Date now = new Date();
        embedMapper.update(null, new LambdaUpdateWrapper<Embed>()
            .eq(Embed::getId, embed.getId())
            .set(Embed::getUpdateBy, operatorId)
            .set(Embed::getUpdateTime, now)
            .set(Embed::getVerifyStatus, VerifyTransitions.VALID)
            .set(Embed::getVerifyBy, operatorId)
            .set(Embed::getVerifyTime, now)
            // 改判有效必须清掉旧的 invalid_reason
            .set(Embed::getInvalidReason, null)
            .set(Embed::getParaffinBlockNo, blockNo));
        log.info("石蜡包埋判有效：id={} blockNo={} verifyBy={}", embed.getId(), blockNo, operatorId);
    }

    /**
     * 判无效：原因必填。
     */
    private void applyInvalid(Embed embed, EmbedVerifyBo bo, Long operatorId) {
        String reason = StringUtils.isBlank(bo.getReason()) ? null : bo.getReason().trim();
        if (reason == null) {
            throw new ServiceException("判无效必须写原因");
        }
        Date now = new Date();
        embedMapper.update(null, new LambdaUpdateWrapper<Embed>()
            .eq(Embed::getId, embed.getId())
            .set(Embed::getUpdateBy, operatorId)
            .set(Embed::getUpdateTime, now)
            .set(Embed::getVerifyStatus, VerifyTransitions.INVALID)
            .set(Embed::getInvalidReason, reason)
            .set(Embed::getVerifyBy, operatorId)
            .set(Embed::getVerifyTime, now));
        log.info("石蜡包埋判无效：id={} reason={} verifyBy={}", embed.getId(), reason, operatorId);
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
