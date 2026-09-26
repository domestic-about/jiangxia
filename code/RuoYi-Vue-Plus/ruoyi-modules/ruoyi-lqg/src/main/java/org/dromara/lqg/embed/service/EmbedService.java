package org.dromara.lqg.embed.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.domain.model.LoginUser;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.helper.DataPermissionHelper;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.lqg.embed.domain.Embed;
import org.dromara.lqg.embed.domain.EmbedMarker;
import org.dromara.lqg.embed.domain.bo.EmbedSubmitBo;
import org.dromara.lqg.embed.guard.EmbedFillRules;
import org.dromara.lqg.embed.guard.StainRules;
import org.dromara.lqg.embed.mapper.EmbedMapper;
import org.dromara.lqg.embed.mapper.EmbedMarkerMapper;
import org.dromara.lqg.sample.domain.Sample;
import org.dromara.lqg.sample.domain.bo.PatchBody;
import org.dromara.lqg.sample.mapper.SampleMapper;
import org.dromara.lqg.sample.verify.VerifyTransitions;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * 石蜡包埋送样记录的写侧：内部新增 / 修改（补填）/ 软删（ticket §2）。
 *
 * <p>★ <b>内部新增</b>（{@code POST /lqg/embed}，FLOW:F-EMBED-01.step1）：
 * <ul>
 *   <li>只能挂到<b>已核验有效</b>的样本（{@code verify_status='valid'}）—— 挂到待核验样本会让
 *       「样本编号」这一格为空（accept 2 第 4 段点名这一形态）；</li>
 *   <li><b>石蜡块编号必填</b>且全库唯一（accept 2 第 6 段：内部建的块没有编号 → 红）；</li>
 *   <li>落库 {@code submit_source='internal'}、{@code verify_status='valid'}、
 *       {@code submitter_id = verify_by = 当前用户}（accept 2 第 7 段断的就是这四列）；</li>
 *   <li>{@code tissue_receive_time} 未传则带样本的收样日期、{@code tissue_process_time}
 *       未传则带样本处理时间的<b>日期部分</b>（ticket §2）。</li>
 * </ul>
 *
 * <p>★ <b>修改 = 补填</b>（{@code PUT /lqg/embed}）：这张表会被反复打开补填，七个工序时间全部可空，
 * 所以 PUT 是<b>只改传了的字段</b>（没传的保持现值）。唯一例外是 {@code markers}：
 * <b>传了就整组替换</b>（先软删旧的再插新的，同一事务）。
 *
 * <p>★★ <b>待核验 / 无效的送样不能被普通保存改掉</b>（ticket §2 第 6 条 / accept 4 第 3 段）：
 * PUT 对这两种状态<b>直接 400</b>，且入参 BO 里根本没有 {@code verifyStatus} ——
 * 状态只经核验接口改，一次普通保存绕不过去。
 *
 * <p>★ <b>所有校验在任何写操作之前</b>：accept 的每条「被拒」后面都跟一句库内断言
 * （「先落盘再报错」是它要抓的假绿形态）。
 *
 * <p>★ FIX V02b（issue #147）：补填段（{@code EmbedFillBo} 那 15 项：样本类型、类器官来源类型、七个工序时间、包埋人、
 * 染色、marker、操作人、备注）的校验与落库列收口到 {@link EmbedFillWriter} —— 本类的新增 / 修改与核验抽屉
 * 「判为有效并保存」时一并保存的 {@code fill}（{@code EmbedVerifyService}）走<b>同一份规则</b>。
 *
 * @author EMBED-MODEL-001
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmbedService {

    private final EmbedMapper embedMapper;
    private final EmbedMarkerMapper embedMarkerMapper;
    private final SampleMapper sampleMapper;
    private final EmbedBlockNoGuard blockNoGuard;
    /** 补填段唯一的校验与落库列（与核验抽屉一并保存的 fill 同一份，FIX V02b） */
    private final EmbedFillWriter fillWriter;

    /**
     * 内部新增（{@code POST /lqg/embed}）。
     *
     * @return 新建记录 id
     */
    @Transactional(rollbackFor = Exception.class)
    public Long create(EmbedSubmitBo bo) {
        if (bo == null) {
            throw new ServiceException("请求体不能为空");
        }
        if (bo.getSampleId() == null) {
            throw new ServiceException("缺少所挂样本 id");
        }
        // ① 样本必须已核验有效（内部录入）
        Sample sample = requireValidSample(bo.getSampleId());
        // ② 石蜡块编号必填 + 全库唯一
        String blockNo = EmbedBlockNoGuard.normalized(bo.getParaffinBlockNo());
        if (blockNo == null) {
            throw new ServiceException("内部录入石蜡包埋必须填石蜡块编号");
        }
        blockNoGuard.requireUnique(blockNo, null);
        // ③ 补填段：长度、染色组合 + marker 表达（字典外的值一律拒）—— 与修改、核验时补填同一份规则（EmbedFillWriter）
        EmbedFillWriter.Prepared fill = fillWriter.prepare(PatchBody.of(bo, new LinkedHashSet<>(EmbedFillRules.ALL_KEYS)));
        List<String> stains = fill.stains();
        Long userId = currentUserId();
        if (userId == null) {
            throw new ServiceException("取不到当前登录用户，无法落提交人");
        }
        return DataPermissionHelper.ignore(() -> {
            Date now = new Date();
            Embed entity = new Embed();
            entity.setSampleId(bo.getSampleId());
            // 内部录入：来源 internal、状态直接 valid、提交人 = 核验人 = 当前用户
            entity.setSubmitSource("internal");
            entity.setSubmitterId(userId);
            entity.setVerifyStatus(VerifyTransitions.VALID);
            entity.setVerifyBy(userId);
            entity.setVerifyTime(now);
            entity.setParaffinBlockNo(blockNo);
            entity.setSampleType(trimToNull(bo.getSampleType()));
            entity.setOrganoidSourceType(trimToNull(bo.getOrganoidSourceType()));
            // 前两个工序时间从样本带出、可改（FLOW:F-EMBED-01.step1）
            entity.setTissueReceiveTime(bo.getTissueReceiveTime() != null
                ? bo.getTissueReceiveTime() : sample.getReceiveDate());
            entity.setTissueProcessTime(bo.getTissueProcessTime() != null
                ? bo.getTissueProcessTime() : toLocalDate(sample.getProcessTime()));
            entity.setAgaroseEmbedTime(bo.getAgaroseEmbedTime());
            entity.setEmbedBy(trimToNull(bo.getEmbedBy()));
            entity.setDehydrateTime(bo.getDehydrateTime());
            entity.setAgaroseSendTime(bo.getAgaroseSendTime());
            entity.setParaffinEmbedTime(bo.getParaffinEmbedTime());
            entity.setSectionTime(bo.getSectionTime());
            entity.setStainTypes(StainRules.toCsv(stains));
            entity.setStainOther(StainRules.hasOther(stains) ? trimToNull(bo.getStainOther()) : null);
            entity.setOperatorName(StringUtils.isNotBlank(bo.getOperatorName())
                ? bo.getOperatorName().trim() : currentNickname());
            entity.setRemark(trimToNull(bo.getRemark()));
            embedMapper.insert(entity);
            fillWriter.insertMarkers(entity.getId(), fill.markers());
            log.info("内部新增石蜡包埋：id={} sampleId={} blockNo={} stains={}",
                entity.getId(), entity.getSampleId(), blockNo, entity.getStainTypes());
            return entity.getId();
        });
    }

    /**
     * 内部修改 / 补填（{@code PUT /lqg/embed}、{@code PUT /mp/int/embed}）—— <b>补丁语义</b>（FIX V33）：
     * <ul>
     *   <li>键<b>没出现</b> → 不动（补填：只传做完的那一步工序时间）；</li>
     *   <li>键出现、值为 {@code null} / 空串 → <b>清空</b>（工序时间填错了能清掉 —— 以前「null = 不动」，
     *       工作台抽屉里清掉切片时间点保存，提示已保存而库里还在，切片染色提示一直显示「已切片」）；</li>
     *   <li>清必填项（所挂样本、石蜡块编号）→ 400，不假装成功。</li>
     * </ul>
     *
     * <p>★ 待核验 / 无效的记录直接 400（ticket §2 第 6 条）：核验是带必填项的状态转移，
     * 不能被一次普通保存绕过去。
     */
    @Transactional(rollbackFor = Exception.class)
    public void update(PatchBody<EmbedSubmitBo> body) {
        EmbedSubmitBo bo = body == null ? null : body.value();
        if (bo == null || bo.getId() == null) {
            throw new ServiceException("缺少石蜡包埋记录 id", 400);
        }
        Long userId = currentUserId();
        DataPermissionHelper.ignore(() -> {
            Embed exists = embedMapper.selectById(bo.getId());
            if (exists == null) {
                // 已软删的行 selectById 也查不到（@TableLogic）→ 与「不存在」同样处理
                throw new ServiceException("石蜡包埋记录不存在（或已删除）", 404);
            }
            if (!VerifyTransitions.VALID.equals(exists.getVerifyStatus())) {
                throw new ServiceException("待核验 / 无效的送样不能通过普通保存修改"
                    + "（核验与改判只走 PUT /lqg/embed/{id}/verify）", 400);
            }
            LambdaUpdateWrapper<Embed> patch = new LambdaUpdateWrapper<Embed>()
                .eq(Embed::getId, exists.getId())
                // 用 `update(null, wrapper)` 时必须显式补 update_by / update_time：MP 的 updateFill
                // 只在参数对象是 BaseEntity 时才填 update_by（SAMPLE-MODEL-001 坑 1）。
                // EMBED-MP-001 的「经手人 = 最后动手的人」与「改完排到历史最前」都读它。
                .set(Embed::getUpdateBy, userId)
                .set(Embed::getUpdateTime, new Date());

            // 所挂样本：必填 —— 传了空值 → 400；传了且换了才校验（必须仍是已核验有效的样本）
            if (body.has("sampleId")) {
                if (bo.getSampleId() == null) {
                    throw new ServiceException("所挂样本不能为空", 400);
                }
                if (!bo.getSampleId().equals(exists.getSampleId())) {
                    requireValidSample(bo.getSampleId());
                    patch.set(Embed::getSampleId, bo.getSampleId());
                }
            }
            // 石蜡块编号：必填、全库唯一（排除自己）—— 传了空值 → 400
            if (body.has("paraffinBlockNo")) {
                String blockNo = EmbedBlockNoGuard.normalized(bo.getParaffinBlockNo());
                if (blockNo == null) {
                    throw new ServiceException("石蜡块编号不能为空", 400);
                }
                blockNoGuard.requireUnique(blockNo, exists.getId());
                patch.set(Embed::getParaffinBlockNo, blockNo);
            }
            // 补填段（15 项）：长度、染色（传了就整组替换，含 stainOther 的置空 / 必填）、marker（传了就整组替换）
            // —— 校验全部在写库之前，与核验抽屉一并保存的 fill 同一份（EmbedFillWriter，FIX V02b）
            EmbedFillWriter.Prepared fill = fillWriter.prepare(body);
            // 补填段的列拼进同一条 UPDATE：没传 = 不动；传了空值 = 清空（FIX V33：以前 null = 不动，日期清不掉）
            fillWriter.applyTo(patch, fill);
            embedMapper.update(null, patch);
            // marker：传了就整组替换（先软删旧的、再插新的，同一事务；传 null / [] = 清空）
            fillWriter.replaceMarkers(exists.getId(), fill);
            log.info("修改石蜡包埋：id={} 改动键={}", exists.getId(), body.keys());
            return null;
        });
    }

    /**
     * 软删（{@code DELETE /lqg/embed/{ids}}）：{@code @TableLogic} 把 {@code del_flag} 置 1；
     * 同时把名下 marker 一并软删（免得留下永远读不到的孤儿行）。
     *
     * @param ids 逗号分隔的 id 串（契约形状）
     * @return 实际删掉的行数
     */
    @Transactional(rollbackFor = Exception.class)
    public int remove(String ids) {
        List<Long> idList = parseIds(ids);
        if (idList.isEmpty()) {
            throw new ServiceException("缺少石蜡包埋记录 id");
        }
        return DataPermissionHelper.ignore(() -> {
            embedMarkerMapper.delete(new LambdaQueryWrapper<EmbedMarker>()
                .in(EmbedMarker::getEmbedId, idList));
            int affected = embedMapper.deleteByIds(idList);
            log.info("软删石蜡包埋：ids={} 影响行数={}", idList, affected);
            return affected;
        });
    }

    // ── 校验 / 装配 ───────────────────────────────────────────────────────────

    /**
     * 内部录入只能挂到<b>已核验有效</b>的样本（ticket §2 第 3 条）。
     *
     * <p>软删 / 不存在的样本由 {@code @TableLogic} + {@code selectById} 一并挡在外面。
     */
    Sample requireValidSample(Long sampleId) {
        Sample sample = DataPermissionHelper.ignore(() -> sampleMapper.selectById(sampleId));
        if (sample == null) {
            throw new ServiceException("所挂样本不存在（或已删除）");
        }
        if (!VerifyTransitions.VALID.equals(sample.getVerifyStatus())) {
            throw new ServiceException("内部录入只能挂到已核验有效的样本（当前状态："
                + sample.getVerifyStatus() + "）");
        }
        return sample;
    }

    // ── 小工具 ───────────────────────────────────────────────────────────────

    private static String trimToNull(String value) {
        return StringUtils.isBlank(value) ? null : value.trim();
    }

    /**
     * {@code Date} → {@code LocalDate}（样本的 {@code process_time} 是时间戳，
     * 而工序时间列是日期，口径是「取日期部分」）。
     */
    static LocalDate toLocalDate(Date date) {
        return date == null ? null : date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
    }

    /**
     * 当前登录人 id；取不到（没有请求上下文，例如单测、定时任务）时 null —— 与 {@code SampleQueryService.currentUserId} 同口径。
     */
    private static Long currentUserId() {
        try {
            LoginUser loginUser = LoginHelper.getLoginUser();
            return loginUser == null ? null : loginUser.getUserId();
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String currentNickname() {
        LoginUser loginUser = LoginHelper.getLoginUser();
        return loginUser == null ? null : loginUser.getNickname();
    }

    /**
     * {@code "1,2,3"} → {@code [1,2,3]}；非法片段直接报错（不静默忽略）。
     */
    static List<Long> parseIds(String ids) {
        if (StringUtils.isBlank(ids)) {
            return List.of();
        }
        List<Long> out = new ArrayList<>();
        for (String piece : Arrays.asList(ids.split(","))) {
            String value = piece.trim();
            if (value.isEmpty()) {
                continue;
            }
            try {
                out.add(Long.valueOf(value));
            } catch (NumberFormatException e) {
                throw new ServiceException("石蜡包埋记录 id 不是数字：" + value);
            }
        }
        return out;
    }

}
