package org.dromara.lqg.cryo.batch.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.domain.model.LoginUser;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.helper.DataPermissionHelper;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.lqg.cryo.batch.CryoBalanceChecker;
import org.dromara.lqg.cryo.batch.domain.CryoBatch;
import org.dromara.lqg.cryo.batch.domain.CryoFlow;
import org.dromara.lqg.cryo.batch.domain.bo.CryoBatchSubmitBo;
import org.dromara.lqg.cryo.batch.mapper.CryoBatchMapper;
import org.dromara.lqg.cryo.batch.mapper.CryoFlowMapper;
import org.dromara.lqg.sample.domain.Sample;
import org.dromara.lqg.sample.mapper.SampleMapper;
import org.dromara.lqg.sample.verify.VerifyTransitions;
import org.dromara.lqg.sample.domain.bo.PatchBody;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

/**
 * 冻存批次的写侧：新建 / 修改（含<b>初始支数可改</b>）/ 软删（ticket §2）。
 *
 * <p>★ <b>新建</b>（{@code POST /lqg/cryo/batch}，FLOW:F-CRYO-01.step1）：
 * <ul>
 *   <li>只能挂到<b>已核验有效</b>的样本（accept 2 第 4 段：挂 1002 这个 pending 样本要被拒）；</li>
 *   <li>{@code passage} 必须匹配 {@code ^P\d{1,3}$}（accept 2 第 1 段：{@code "3"} 要被拒）；</li>
 *   <li>{@code initQty} 必须为正整数（accept 2 第 2 段：{@code 0} 要被拒）；</li>
 *   <li>{@code inMinus80='N'}（直接进液氮）时 {@code ln2Location} 必填
 *       （accept 2 第 3 段：东西进了液氮罐却没人知道在哪 → 拒）；</li>
 *   <li>{@code toLn2Time} 不得早于 {@code freezeTime}（accept 2 第 5 段）；</li>
 *   <li>冻存样品名称手填、系统不解析、不自动拼（ticket §0 口径复述 3）。</li>
 * </ul>
 *
 * <p>★★ <b>修改 = patch 语义 + 初始支数逐笔校验</b>（{@code PUT /lqg/cryo/batch}）：
 * <ol>
 *   <li>{@code SELECT … FOR UPDATE} <b>锁批次行</b>（FLOW:F-CRYO-02.step4：写、改、删流水和改
 *       初始支数，都先对批次行加行锁再重算、再校验，防两人同时操作导致负数）；</li>
 *   <li>读<b>未删</b>流水（{@code @TableLogic} 自动排除软删的）；</li>
 *   <li>{@link CryoBalanceChecker#requireNonNegative(int, java.util.Collection)}：从<b>新的</b>
 *       初始支数出发按 {@code flow_time} 正序（同一时刻按 id）逐笔累加，<b>任何一步 &lt; 0 就拒绝</b>；</li>
 *   <li>通过才 {@code update(null, wrapper)}，并显式补 {@code update_by / update_time}
 *       （MP 的 {@code updateFill} 只在参数对象是 {@code BaseEntity} 时才填 {@code update_by}，
 *       走 wrapper 时必须自己写 —— SAMPLE-MODEL-001 坑 1，accept 2 第 7 段查的就是这一列）。</li>
 * </ol>
 * ★ <b>不许静默忽略入参里的 {@code initQty}</b>：给了就必须落库（给了不合法就拒），
 * 否则用户以为改成功了、实际没改（accept 2 的 counterfeit 点名）。
 *
 * <p>★ <b>软删</b>（{@code DELETE /lqg/cryo/batch/{ids}}）：<b>有未删流水的批次不许删</b>
 * （accept 2 倒数第 2 段：删了批次，「谁取走了几支」就再也查不到了）。
 *
 * <p>★ <b>所有校验在任何写操作之前</b>：每条 accept 的「被拒」后面都跟一句库内断言
 * （「先落盘再报错」是它要抓的假绿形态）。
 *
 * @author CRYO-MODEL-001
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CryoBatchService {

    private final CryoBatchMapper cryoBatchMapper;
    private final CryoFlowMapper cryoFlowMapper;
    private final SampleMapper sampleMapper;

    /**
     * 新建冻存批次（{@code POST /lqg/cryo/batch}）。
     *
     * @return 新建批次 id
     */
    @Transactional(rollbackFor = Exception.class)
    public Long create(CryoBatchSubmitBo bo) {
        if (bo == null) {
            throw new ServiceException("请求体不能为空", 400);
        }
        if (bo.getSampleId() == null) {
            throw new ServiceException("缺少所挂样本 id", 400);
        }
        // ① 样本必须已核验有效（内部录入）
        requireValidSample(bo.getSampleId());
        // ② 冻存样品名称手填、必填（系统不解析内容）
        String cryoName = trimToNull(bo.getCryoName());
        if (cryoName == null) {
            throw new ServiceException("冻存样品名称不能为空", 400);
        }
        // ③ 代数格式 / 冻存时间 / 初始支数 / 是否暂存 -80
        String passage = CryoBalanceChecker.requirePassage(bo.getPassage());
        LocalDate freezeTime = CryoBalanceChecker.requireFreezeTime(bo.getFreezeTime());
        int initQty = CryoBalanceChecker.requirePositiveInitQty(bo.getInitQty());
        String inMinus80 = CryoBalanceChecker.requireInMinus80(bo.getInMinus80());
        // ④ 转液氮时间不早于冻存时间
        CryoBalanceChecker.requireLn2NotBeforeFreeze(freezeTime, bo.getToLn2Time());
        // ⑤ 直接进液氮（选「否」）必须有液氮储存位置
        String ln2Location = CryoBalanceChecker.requireLn2Location(inMinus80, bo.getToLn2Time(),
            trimToNull(bo.getLn2Location()));

        Long userId = currentUserId();
        return DataPermissionHelper.ignore(() -> {
            CryoBatch entity = new CryoBatch();
            entity.setSampleId(bo.getSampleId());
            entity.setCryoName(cryoName);
            entity.setPassage(passage);
            entity.setFreezeTime(freezeTime);
            entity.setInitQty(initQty);
            entity.setDensity(trimToNull(bo.getDensity()));
            entity.setInMinus80(inMinus80);
            entity.setFrozenBy(StringUtils.isNotBlank(bo.getFrozenBy())
                ? bo.getFrozenBy().trim() : currentNickname());
            entity.setToLn2Time(bo.getToLn2Time());
            entity.setLn2Location(ln2Location);
            entity.setRemark(trimToNull(bo.getRemark()));
            cryoBatchMapper.insert(entity);
            log.info("新建冻存批次：id={} sampleId={} cryoName={} passage={} initQty={} inMinus80={} operator={}",
                entity.getId(), entity.getSampleId(), cryoName, passage, initQty, inMinus80, userId);
            return entity.getId();
        });
    }

    /**
     * 修改批次（{@code PUT /lqg/cryo/batch}、{@code PUT /mp/int/cryo/batch}）—— <b>补丁语义</b>：
     * 键没出现 = 不动；键出现、值为空 = 清空（FIX V33，以前清不掉）；清必填项 → 400。
     * {@code initQty} 改了就<b>先锁行、再逐笔校验</b>。
     */
    @Transactional(rollbackFor = Exception.class)
    public void update(PatchBody<CryoBatchSubmitBo> body) {
        CryoBatchSubmitBo bo = body == null ? null : body.value();
        if (bo == null || bo.getId() == null) {
            throw new ServiceException("缺少冻存批次 id", 400);
        }
        Long userId = currentUserId();
        DataPermissionHelper.ignore(() -> {
            // ★ 先锁批次行（FLOW:F-CRYO-02.step4）：锁在第一条写操作之前，
            //   并发下「两个人同时改初始支数 / 同时取走」才会串行化。
            CryoBatch exists = cryoBatchMapper.selectByIdForUpdate(bo.getId());
            if (exists == null) {
                // 已软删的行也查不到（本条 SQL 手写了 del_flag='0'）→ 与「不存在」同样处理
                throw new ServiceException("冻存批次不存在（或已删除）", 400);
            }
            LambdaUpdateWrapper<CryoBatch> patch = new LambdaUpdateWrapper<CryoBatch>()
                .eq(CryoBatch::getId, exists.getId())
                // 用 `update(null, wrapper)` 时必须显式补 update_by / update_time：
                // MP 的 updateFill 只在参数对象是 BaseEntity 时才填 update_by（SAMPLE-MODEL-001 坑 1）。
                // accept 2 第 7 段查的就是 `init_qty='10' AND update_by IS NOT NULL`。
                .set(CryoBatch::getUpdateBy, userId)
                .set(CryoBatch::getUpdateTime, new Date());

            // ★ FIX V33（补丁语义）：键没出现 = 不动；键出现、值为空 = 清空；清必填项 → 400。
            //   以前一律「null = 不动」：工作台抽屉里清掉填错的「-80 转移至液氮时间」点保存，
            //   提示已保存而库里还在（这一批永远算「已转液氮」、退出超期提醒）。
            // ── 所挂样本：必填；传了且换了人才校验（必须仍是已核验有效的样本）
            if (body.has("sampleId")) {
                if (bo.getSampleId() == null) {
                    throw new ServiceException("所挂样本不能为空", 400);
                }
                if (!bo.getSampleId().equals(exists.getSampleId())) {
                    requireValidSample(bo.getSampleId());
                    patch.set(CryoBatch::getSampleId, bo.getSampleId());
                }
            }
            // ── 冻存样品名称：传了就不能为空（内容是手填的自由文本，不解析）
            if (body.has("cryoName")) {
                String cryoName = trimToNull(bo.getCryoName());
                if (cryoName == null) {
                    throw new ServiceException("冻存样品名称不能为空", 400);
                }
                patch.set(CryoBatch::getCryoName, cryoName);
            }
            // ── 代数：传了就必须是 ^P\d{1,3}$（空值同样被拒）
            if (body.has("passage")) {
                patch.set(CryoBatch::getPassage, CryoBalanceChecker.requirePassage(bo.getPassage()));
            }
            // ── 冻存时间：传了就不能为空，并重算「转液氮不得早于冻存」
            LocalDate freezeTime = exists.getFreezeTime();
            if (body.has("freezeTime")) {
                freezeTime = CryoBalanceChecker.requireFreezeTime(bo.getFreezeTime());
            }
            // ── 转液氮时间：传了就用传的（空值 = 撤销「已转液氮」，不在库里硬留一个填错的日期）
            LocalDate toLn2Time = body.has("toLn2Time") ? bo.getToLn2Time() : exists.getToLn2Time();
            CryoBalanceChecker.requireLn2NotBeforeFreeze(freezeTime, toLn2Time);
            if (body.has("freezeTime")) {
                patch.set(CryoBatch::getFreezeTime, freezeTime);
            }
            // ── 是否暂存 -80 / 液氮位置：两个字段互相牵制，合并成「改后的最终态」再判
            String inMinus80 = body.has("inMinus80")
                ? CryoBalanceChecker.requireInMinus80(bo.getInMinus80()) : exists.getInMinus80();
            String ln2Location = body.has("ln2Location")
                ? trimToNull(bo.getLn2Location()) : exists.getLn2Location();
            ln2Location = CryoBalanceChecker.requireLn2Location(inMinus80, toLn2Time, ln2Location);
            if (body.has("inMinus80")) {
                patch.set(CryoBatch::getInMinus80, inMinus80);
            }
            if (body.has("ln2Location")) {
                patch.set(CryoBatch::getLn2Location, ln2Location);
            }
            if (body.has("toLn2Time")) {
                patch.set(CryoBatch::getToLn2Time, toLn2Time);
            }
            // ── ★ 初始支数：可改（不能清空），但改完必须逐笔算下来每一步都不为负
            if (body.has("initQty")) {
                int initQty = CryoBalanceChecker.requirePositiveInitQty(bo.getInitQty());
                CryoBalanceChecker.requireNonNegative(initQty, undeletedFlows(exists.getId()));
                patch.set(CryoBatch::getInitQty, initQty);
            }
            // ── 其余标量：没传 = 不动；传了空值 = 清空
            patch.set(body.has("density"), CryoBatch::getDensity, trimToNull(bo.getDensity()))
                .set(body.has("frozenBy"), CryoBatch::getFrozenBy, trimToNull(bo.getFrozenBy()))
                .set(body.has("remark"), CryoBatch::getRemark, trimToNull(bo.getRemark()));
            cryoBatchMapper.update(null, patch);
            log.info("修改冻存批次：id={} 改动键={} operator={}", exists.getId(), body.keys(), userId);
            return null;
        });
    }

    /**
     * 软删批次（{@code DELETE /lqg/cryo/batch/{ids}}）：<b>有未删流水的批次一律拒</b>。
     *
     * @param ids 逗号分隔的 id 串（契约形状，与若依上游删法一致）
     * @return 实际删掉的行数
     */
    @Transactional(rollbackFor = Exception.class)
    public int remove(String ids) {
        List<Long> idList = parseIds(ids);
        if (idList.isEmpty()) {
            throw new ServiceException("缺少冻存批次 id", 400);
        }
        return DataPermissionHelper.ignore(() -> {
            for (Long id : idList) {
                CryoBatch batch = cryoBatchMapper.selectByIdForUpdate(id);
                if (batch == null) {
                    throw new ServiceException("冻存批次不存在（或已删除）：" + id, 400);
                }
                long flows = cryoFlowMapper.selectCount(new LambdaQueryWrapper<CryoFlow>()
                    .eq(CryoFlow::getBatchId, id));
                if (flows > 0) {
                    // 追溯断了就再也查不到「谁取走了几支」（accept 2 倒数第 2 段）
                    throw new ServiceException("该批次已有 " + flows + " 笔出入库登记，不能删除"
                        + "（删了「谁取走了几支」就查不到了）", 400);
                }
            }
            int affected = cryoBatchMapper.deleteByIds(idList);
            log.info("软删冻存批次：ids={} 影响行数={}", idList, affected);
            return affected;
        });
    }

    // ── 校验 / 小工具 ─────────────────────────────────────────────────────────

    /**
     * 内部录入只能挂到<b>已核验有效</b>的样本（ticket §2；accept 2 第 4 段）。
     *
     * <p>软删 / 不存在的样本由 {@code @TableLogic} + {@code selectById} 一并挡在外面。
     */
    Sample requireValidSample(Long sampleId) {
        Sample sample = DataPermissionHelper.ignore(() -> sampleMapper.selectById(sampleId));
        if (sample == null) {
            throw new ServiceException("所挂样本不存在（或已删除）", 400);
        }
        if (!VerifyTransitions.VALID.equals(sample.getVerifyStatus())) {
            throw new ServiceException("冻存只能挂到已核验有效的样本（当前状态："
                + sample.getVerifyStatus() + "）", 400);
        }
        return sample;
    }

    /**
     * 某批次的<b>未删</b>流水（{@code @TableLogic} 自动排除软删的行；排序交给
     * {@link CryoBalanceChecker}，它按 {@code flow_time} 正序、同一时刻按 id 排）。
     */
    List<CryoBalanceChecker.Flow> undeletedFlows(Long batchId) {
        List<CryoFlow> rows = cryoFlowMapper.selectList(new LambdaQueryWrapper<CryoFlow>()
            .eq(CryoFlow::getBatchId, batchId)
            .orderByAsc(CryoFlow::getFlowTime)
            .orderByAsc(CryoFlow::getId));
        List<CryoBalanceChecker.Flow> flows = new ArrayList<>();
        if (rows != null) {
            for (CryoFlow row : rows) {
                flows.add(new CryoBalanceChecker.Flow(row.getId(), row.getFlowTime(),
                    row.getDelta() == null ? 0 : row.getDelta()));
            }
        }
        return flows;
    }

    private static String trimToNull(String value) {
        return StringUtils.isBlank(value) ? null : value.trim();
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
                throw new ServiceException("冻存批次 id 不是数字：" + value, 400);
            }
        }
        return out;
    }

}
