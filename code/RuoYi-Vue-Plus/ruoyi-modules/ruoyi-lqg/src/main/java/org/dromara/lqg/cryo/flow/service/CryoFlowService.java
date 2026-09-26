package org.dromara.lqg.cryo.flow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.domain.model.LoginUser;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.lqg.cryo.batch.CryoBalanceChecker;
import org.dromara.lqg.cryo.batch.domain.CryoBatch;
import org.dromara.lqg.cryo.batch.domain.CryoFlow;
import org.dromara.lqg.cryo.batch.mapper.CryoBatchMapper;
import org.dromara.lqg.cryo.batch.mapper.CryoFlowMapper;
import org.dromara.lqg.cryo.flow.domain.bo.CryoFlowEditBo;
import org.dromara.lqg.cryo.flow.domain.bo.CryoFlowSubmitBo;
import org.dromara.lqg.cryo.flow.domain.bo.CryoToLn2Bo;
import org.dromara.lqg.cryo.flow.domain.vo.CryoFlowRecordVo;
import org.dromara.lqg.sample.service.SampleNameResolver;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 冻存出入库流水的写侧与流水读侧（CRYO-FLOW-001）。
 *
 * <p>四个动作与三个写操作，全部照 ticket §2 与 CR-20260917-04 / CR-20260917-05：
 *
 * <pre>
 * POST   /lqg/cryo/batch/{id}/flow            取走 take / 补入 add / 盘点调整 adjust
 * PUT    /lqg/cryo/batch/{id}/flow/{flowId}   改一笔登记（记修改人；类型不可改）
 * DELETE /lqg/cryo/batch/{id}/flow/{flowId}   软删一笔登记
 * PUT    /lqg/cryo/batch/{id}/to-ln2          登记转液氮（批次位置当场变液氮）
 * GET    /lqg/cryo/batch/{id}/flows           未删流水，时间倒序，每行带操作后剩余
 * </pre>
 *
 * <p>★★ <b>每一个写操作都走同一条骨架</b>（FLOW:F-CRYO-02.step4 / step5）：
 * <ol>
 *   <li>{@code CryoBatchMapper.selectByIdForUpdate} —— <b>先对批次行加行锁</b>
 *       （{@code SELECT … FOR UPDATE}）；</li>
 *   <li>读<b>未删</b>流水，把「这一笔改后的形态」拼进去；</li>
 *   <li>{@link CryoBalanceChecker#requireNonNegative(int, java.util.Collection)} ——
 *       从<b>初始支数</b>出发按 {@code flow_time} 正序（同一时刻按 id）<b>逐笔</b>累加，
 *       任何一步 &lt; 0 就拒；</li>
 *   <li>通过才写库。</li>
 * </ol>
 * ★ 没有第 1 步的锁，「两个人同时取最后两支」会双双通过校验
 * （accept 3 第 1 段：5 个并发请求抢 2 支，只能成功 1 个、剩余恰好 0）。<br>
 * ★ 第 3 步<b>不是</b>「只看最终剩余」：删掉一笔补入、或把一笔取走挪到更早，
 * 都可能让序列中途为负而最终为正（accept 2 的 counterfeit 点名这两个形态）。
 *
 * <p>★ <b>借位为负也能被正确拒绝</b>：{@code requiredInitQty} = 逐笔累加过程中最大的透支额，
 * 也就是「让整条序列合法的最小初始支数」。取走时 {@code requiredInitQty(旧序列 + 新的一笔) > init}
 * ⟺ 这一笔取走超过了当时的剩余（accept 1 第 4 段「超取 9 支」就是这一条）。
 *
 * <p>★ <b>被拒时库里必须什么都不变</b>（每条 accept 的「被拒」后面都跟库内断言）：
 * 所有校验都在第一条写操作之前，且整个方法一个事务；抛 {@link ServiceException} 即回滚。
 *
 * <p>★ <b>写接口只在 {@code /lqg/cryo/**}</b>：工作台与（2026-09-24 起）小程序内部人员的批次详情弹层
 * 都调这一套（甲方「小程序和工作台界面都能操作」），本类不向 {@code /mp/int/cryo/**} 暴露任何写方法 ——
 * 两端共用同一把锁、同一套逐笔校验，被拒时的中文消息两端原样显示。
 *
 * @author CRYO-FLOW-001
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CryoFlowService {

    /** 取走。 */
    public static final String TYPE_TAKE = "take";

    /** 补入。 */
    public static final String TYPE_ADD = "add";

    /** 盘点调整。 */
    public static final String TYPE_ADJUST = "adjust";

    /** 入参时间格式（verify/README 坑 4：contract 与 accept 都用空格分隔，不用 ISO 的 {@code T}）。 */
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 被拒提示里指那一笔用的短时间（{@code MM-dd HH:mm}，与两端列表上的写法一致）。 */
    private static final DateTimeFormatter SHORT_TIME = DateTimeFormatter.ofPattern("MM-dd HH:mm");

    /** 纯日期（{@code to-ln2} 的 {@code toLn2Time}）。 */
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final CryoBatchMapper cryoBatchMapper;
    private final CryoFlowMapper cryoFlowMapper;
    private final SampleNameResolver nameResolver;

    // ── 登记一笔（取走 / 补入 / 盘点调整） ──────────────────────────────────────

    /**
     * 登记一笔流水（{@code POST /lqg/cryo/batch/{id}/flow}）。
     *
     * @param batchId 批次 id
     * @param bo      入参
     * @return 新流水的 id
     */
    @Transactional(rollbackFor = Exception.class)
    public Long create(Long batchId, CryoFlowSubmitBo bo) {
        if (batchId == null) {
            throw new ServiceException("缺少冻存批次 id", 400);
        }
        if (bo == null) {
            throw new ServiceException("请求体不能为空", 400);
        }
        String flowType = requireFlowType(bo.getFlowType());
        int delta = deltaOf(flowType, bo.getQty());
        // ★ adjust 必须写原因（FLOW:F-CRYO-02.step3：可正可负、不为 0、原因必填）
        String purpose = purposeOf(flowType, bo.getPurpose());
        // ★ 只解析一次：校验用的时间与落库的时间必须是同一个值
        LocalDateTime flowTime = flowTimeOf(bo.getFlowTime());

        // ① 锁批次行（锁在任何一次读流水 / 写库之前）
        CryoBatch batch = lockBatch(batchId);
        int initQty = initQtyOf(batch);
        // ②③ 读未删流水 → 判「这一笔取走是不是超过了当时的剩余」；通过才落库
        requireInsertable(initQty, undeletedFlows(batchId), delta);

        Long userId = currentUserId();
        CryoFlow entity = new CryoFlow();
        entity.setBatchId(batchId);
        entity.setFlowType(flowType);
        entity.setDelta(delta);
        // ★ 取自位置由批次「当时」所在位置自动带出，不让人选（FLOW:F-CRYO-02.step1）
        entity.setFromLocation(locationOf(batch));
        entity.setOperatorName(operatorNameOf(bo.getOperatorName()));
        entity.setFlowTime(flowTime);
        entity.setPurpose(purpose);
        cryoFlowMapper.insert(entity);
        log.info("登记录入流水：batchId={} flowId={} type={} delta={} from={} operator={}",
            batchId, entity.getId(), flowType, delta, entity.getFromLocation(), userId);
        return entity.getId();
    }

    // ── 改一笔登记 ────────────────────────────────────────────────────────────

    /**
     * 改一笔登记（{@code PUT /lqg/cryo/batch/{id}/flow/{flowId}}）。
     *
     * <p>★ 登记类型改不了：传了 {@code flowType} 且与原来不同 → 400（要换类型就删掉重登）。
     * ★ {@code fromLocation} 保持登记时的值，不按批次当前位置重算。
     * ★ 记下修改人：{@code update(null, wrapper)} 不填 {@code update_by}（SAMPLE-MODEL-001 坑 1），
     * 这里显式 {@code set}（accept 2 第 2 段断的就是 {@code yes}）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void update(Long batchId, Long flowId, CryoFlowEditBo bo) {
        if (batchId == null || flowId == null) {
            throw new ServiceException("缺少冻存批次 id 或流水 id", 400);
        }
        if (bo == null) {
            throw new ServiceException("请求体不能为空", 400);
        }
        // ① flowId 必须属于这个批次且未删（不属于 → 404，别让「用 A 批次的路径改 B 批次的登记」得逞）
        CryoFlow exists = requireFlow(batchId, flowId);
        // ② 类型不可改
        if (StringUtils.isNotBlank(bo.getFlowType())
            && !exists.getFlowType().equals(bo.getFlowType().trim())) {
            throw new ServiceException("登记类型不能改（要换类型请删掉重新登记）", 400);
        }
        String flowType = exists.getFlowType();
        // ③ qty 按原类型解释（不传 = 不动）
        int delta = bo.getQty() == null ? deltaOf(exists) : deltaOf(flowType, bo.getQty());
        // ④ purpose：原类型是 adjust 时必须非空（不传则保留原值）
        String purpose = purposeOf(flowType,
            bo.getPurpose() != null ? bo.getPurpose() : exists.getPurpose());
        // ⑤ 发生时间（不传 = 不动）
        LocalDateTime flowTime = bo.getFlowTime() == null
            ? exists.getFlowTime() : flowTimeOf(bo.getFlowTime());

        // ⑥ 锁批次行 → 用「改后的流水集合」逐笔重算
        CryoBatch batch = lockBatch(batchId);
        requireNonNegative(initQtyOf(batch),
            merge(undeletedFlows(batchId), new CryoBalanceChecker.Flow(flowId, flowTime, delta)), "这样改");

        Long userId = currentUserId();
        CryoFlow patch = buildEditPatch(exists, delta, purpose, flowTime, bo.getOperatorName());
        // ★ 走实体路径（updateById）而不是 update(null, wrapper)：
        //   ① 实体路径下 MP 的 updateFill 会自动填 update_by / update_time ——
        //      不依赖「wrapper 里手动补」这一点（SAMPLE-MODEL-001 坑 1 的根因就是它没补）；
        //   ② 非 null 的字段才进 SET，所以 operatorName 不传时不会被清空；
        //   ③ @TableLogic 会给 UPDATE 补 del_flag='0'，改不到已软删的行。
        cryoFlowMapper.updateById(patch);
        // ★ FIX V33：用途被清空（取走 / 补入可以不写用途）—— updateById 跳过 null 列，
        //   于是「清空用途、点保存」提示已保存而库里还是旧值。这一列单独显式写 NULL（同一事务）。
        //   盘点调整清空用途在上面 purposeOf 里就被拒（400「盘点调整必须写原因」），走不到这里。
        if (bo.getPurpose() != null && purpose == null && exists.getPurpose() != null) {
            cryoFlowMapper.update(null, new LambdaUpdateWrapper<CryoFlow>()
                .eq(CryoFlow::getId, exists.getId())
                .set(CryoFlow::getPurpose, null));
        }
        log.info("修改冻存登记：batchId={} flowId={} type={} delta={} operator={}",
            batchId, flowId, flowType, delta, userId);
    }

    /**
     * 改登记要落库的<b>补丁实体</b>（非 null 的列才进 SET）。
     *
     * <p>★ <b>刻意不设的三列</b>：{@code flow_type}（类型改不了）、
     * {@code from_location}（保持登记时的值，不按批次当前位置重算）、
     * {@code batch_id} / {@code create_by} / {@code create_time}。
     * ★ {@code update_by / update_time} 交给 MP 的 {@code updateFill}（实体路径会自动填）。
     *
     * @param exists       原行
     * @param delta        改后的带符号变化量
     * @param purpose      改后的用途 / 原因
     * @param flowTime     改后的发生时间
     * @param operatorName 入参经手人（{@code null} = 不动）
     * @return patch 实体（只有 {@code id} + 要改的列非 null）
     */
    static CryoFlow buildEditPatch(CryoFlow exists, int delta, String purpose, LocalDateTime flowTime,
                                   String operatorName) {
        CryoFlow patch = new CryoFlow();
        patch.setId(exists == null ? null : exists.getId());
        patch.setDelta(delta);
        patch.setPurpose(purpose);
        patch.setFlowTime(flowTime);
        if (operatorName != null) {
            patch.setOperatorName(operatorNameOf(operatorName));
        }
        return patch;
    }

    // ── 删一笔登记（软删） ─────────────────────────────────────────────────────

    /**
     * 软删一笔登记（{@code DELETE /lqg/cryo/batch/{id}/flow/{flowId}}）。
     *
     * <p>★ 软删不是物理删（CR-20260917-04 的追溯要求）：删掉之后 {@code del_flag='1'}，
     * 剩余不再计入它，但「谁在什么时候从哪拿了几支」这条记录仍在库里
     * （accept 2 第 8 段查的就是 {@code del_flag=1}）。
     *
     * <p>★ 删一笔<b>补入</b>可能让后面的取走不够（accept 2 的 counterfeit 点名 3006）——
     * 所以删之前也要把剩余序列重算一遍。
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long batchId, Long flowId) {
        if (batchId == null || flowId == null) {
            throw new ServiceException("缺少冻存批次 id 或流水 id", 400);
        }
        CryoFlow exists = requireFlow(batchId, flowId);
        // 锁批次行 → 去掉这一笔之后的序列逐笔重算
        CryoBatch batch = lockBatch(batchId);
        requireNonNegative(initQtyOf(batch), exclude(undeletedFlows(batchId), flowId), "删掉这一笔");
        cryoFlowMapper.deleteById(flowId);
        log.info("软删冻存登记：batchId={} flowId={} type={} delta={} operator={}",
            batchId, flowId, exists.getFlowType(), exists.getDelta(), currentUserId());
    }

    // ── 登记转液氮 ────────────────────────────────────────────────────────────

    /**
     * 登记转液氮（{@code PUT /lqg/cryo/batch/{id}/to-ln2}，FLOW:F-CRYO-01.step4）。
     *
     * <p>★ {@code toLn2Time} 不得早于 {@code freezeTime}（accept 3 第 4 段：2020-01-01 → 400）；
     * ★ {@code ln2Location} 必填（已转过的可以改位置，不可以清空）；
     * ★ 保存后该批次当前位置 = 液氮（accept 3 第 6 段断 {@code .data.location=="ln2"}）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void toLn2(Long batchId, CryoToLn2Bo bo) {
        if (batchId == null) {
            throw new ServiceException("缺少冻存批次 id", 400);
        }
        if (bo == null) {
            throw new ServiceException("请求体不能为空", 400);
        }
        LocalDate toLn2Time = parseDate(bo.getToLn2Time(), "转移至液氮时间");
        String ln2Location = trimToNull(bo.getLn2Location());
        if (ln2Location == null) {
            throw new ServiceException("转液氮必须填液氮储存位置", 400);
        }
        // 锁批次行：与写流水的并发路径串行化（同一把行锁）
        CryoBatch batch = lockBatch(batchId);
        // ★ 不早于冻存时间（复用上游的标量判据，别另写一份）
        CryoBalanceChecker.requireLn2NotBeforeFreeze(batch.getFreezeTime(), toLn2Time);

        Long userId = currentUserId();
        cryoBatchMapper.update(null, new LambdaUpdateWrapper<CryoBatch>()
            .eq(CryoBatch::getId, batchId)
            .set(CryoBatch::getInMinus80, "Y")
            .set(CryoBatch::getToLn2Time, toLn2Time)
            .set(CryoBatch::getLn2Location, ln2Location)
            .set(CryoBatch::getUpdateBy, userId)
            .set(CryoBatch::getUpdateTime, new Date()));
        log.info("登记转液氮：batchId={} toLn2Time={} ln2Location={} operator={}",
            batchId, toLn2Time, ln2Location, userId);
    }

    // ── 读：流水列表（工作台 + 小程序共用） ────────────────────────────────────

    /**
     * 某批次的流水（{@code GET /lqg/cryo/batch/{id}/flows}）。
     *
     * <p>★ <b>时间倒序</b>返回（契约原文），但 {@code balanceAfter} 必须按<b>时间正序</b>累加算出来
     * —— 所以先按正序算完再翻过来，别在倒序列表上就地累加。
     *
     * <p>★ {@code balanceAfter} <b>不落库</b>（ADR-0010：批次上连剩余列都没有，更不会给流水存快照）。
     *
     * <p>★ 给「最新一笔」也填上批次当前剩余（{@code remainingQty}），
     * 让调用方不必再发一次详情请求；历史行留 null。
     */
    public List<CryoFlowRecordVo> list(Long batchId) {
        if (batchId == null) {
            return List.of();
        }
        CryoBatch batch = cryoBatchMapper.selectById(batchId);
        if (batch == null) {
            // 不存在 / 已软删：返回空列表（与上游 listFlows 的语义一致，不泄露存在性）
            return List.of();
        }
        List<CryoFlow> rows = cryoFlowMapper.selectList(new LambdaQueryWrapper<CryoFlow>()
            .eq(CryoFlow::getBatchId, batchId));
        int initQty = initQtyOf(batch);
        // ★ 按 flow_time 正序、同一时刻按 id（复用上游纯函数，别另写一份排序）
        List<CryoBalanceChecker.Flow> ordered = CryoBalanceChecker.ordered(toCheckerFlows(rows));
        Map<Long, Integer> balanceAfter = new HashMap<>();
        int balance = initQty;
        for (CryoBalanceChecker.Flow flow : ordered) {
            balance += flow.delta();
            balanceAfter.put(flow.id(), balance);
        }
        List<CryoFlow> desc = new ArrayList<>(rows);
        desc.sort((a, b) -> {
            int byTime = compareDesc(a.getFlowTime(), b.getFlowTime());
            return byTime != 0 ? byTime : compareDesc(a.getId(), b.getId());
        });
        List<CryoFlowRecordVo> out = new ArrayList<>(desc.size());
        for (CryoFlow row : desc) {
            out.add(toVo(row, balanceAfter.get(row.getId())));
        }
        if (!out.isEmpty()) {
            // 最新一笔的操作后剩余 = 批次当前剩余（同一把账，两个口）
            out.get(0).setRemainingQty(balance);
        }
        return out;
    }

    // ── 内部：锁 / 流水集合 / 校验 ─────────────────────────────────────────────

    /**
     * 取批次行并<b>锁住它</b>（{@code SELECT … FOR UPDATE}，FLOW:F-CRYO-02.step4）。
     *
     * @throws ServiceException 批次不存在 / 已软删
     */
    CryoBatch lockBatch(Long batchId) {
        CryoBatch batch = cryoBatchMapper.selectByIdForUpdate(batchId);
        if (batch == null) {
            throw new ServiceException("冻存批次不存在（或已删除）", 400);
        }
        return batch;
    }

    /**
     * 取一笔流水，且必须<b>属于这个批次</b>、<b>未删</b>（{@code @TableLogic} 自动排除软删）。
     *
     * @throws ServiceException 不属于这个批次 / 已删 / 不存在 → 404
     */
    CryoFlow requireFlow(Long batchId, Long flowId) {
        CryoFlow flow = cryoFlowMapper.selectById(flowId);
        if (flow == null || !batchId.equals(flow.getBatchId())) {
            throw new ServiceException("冻存出入库登记不存在", 404);
        }
        return flow;
    }

    /**
     * 某批次的<b>未删</b>流水（{@code @TableLogic} 自动补 {@code del_flag='0'}；
     * 排序交给 {@link CryoBalanceChecker}）。
     */
    List<CryoBalanceChecker.Flow> undeletedFlows(Long batchId) {
        return toCheckerFlows(cryoFlowMapper.selectList(new LambdaQueryWrapper<CryoFlow>()
            .eq(CryoFlow::getBatchId, batchId)));
    }

    /**
     * 逐笔校验：从初始支数出发，任一步 &lt; 0 → 400（复用上游唯一判据，别另写一套）。
     */
    static void requireNonNegative(int initQty, List<CryoBalanceChecker.Flow> flows, String action) {
        try {
            CryoBalanceChecker.requireNonNegative(initQty, flows);
        } catch (ServiceException e) {
            // ★ 判定仍是上游那一个；这里只把提示换成「改 / 删登记」的话并指出是哪一笔 ——
            //   上游那句「已取走 N 支，冻存数量不能少于 N」是给改初始支数的，改一笔取走时看到它会以为要去改冻存数量
            //   （2026-09-24 起小程序也能改删登记，两端都原样显示这句话）
            throw new ServiceException(overdraftMessage(initQty, flows, action), 400);
        }
    }

    /**
     * 改 / 删一笔被拒时的提示：按时间正序逐笔累加，<b>第一次</b>变负的那一笔是谁、变成了多少。
     *
     * <p>例：「这样改会让 09-24 10:27 那一笔（-3 支）之后的剩余变成 -1 支，没有保存」。
     *
     * @param action 「这样改」/「删掉这一笔」
     */
    static String overdraftMessage(int initQty, List<CryoBalanceChecker.Flow> flows, String action) {
        int balance = initQty;
        for (CryoBalanceChecker.Flow flow : CryoBalanceChecker.ordered(flows)) {
            balance += flow.delta();
            if (balance < 0) {
                String when = flow.flowTime() == null ? "" : flow.flowTime().format(SHORT_TIME) + " ";
                String delta = flow.delta() > 0 ? "+" + flow.delta() : String.valueOf(flow.delta());
                return action + "会让 " + when + "那一笔（" + delta + " 支）之后的剩余变成 " + balance + " 支，没有保存";
            }
        }
        return action + "会让某一步的剩余变成负数，没有保存";
    }

    /**
     * ★ <b>登记一笔时（{@code POST}）的判据：这一笔不能超过它那一刻的剩余。</b>
     *
     * <p>为什么不直接把「已有流水 + 这一笔」丢给
     * {@link CryoBalanceChecker#requireNonNegative}：那个判据的 N 是
     * <b>「让整条序列合法的<b>最小初始支数</b>」= 逐笔累加过程中最大的透支额</b>，
     * 于是「初始 5 + 补入 2（第 1 步）→ 取走 7（第 2 步）」会被判成
     * {@code requiredInitQty = 7 > 5} 而拒 —— 可那一刻账面明明有 7 支，
     * 用户看到的是「一次合法的『全部取用』被系统挡了」（CRYO-FLOW-001 accept 2 第 12 段
     * 就是这个形态，实跑红过）。
     *
     * <p>正确判据是把「已有流水」按时间正序走一遍（同一个函数
     * {@link CryoBalanceChecker#ordered}，同一套排序、同一套标量判据，没有另写一套算法），
     * 拿到这一笔之前的余额 {@code balance}，再要 {@code balance + delta ≥ 0}：
     * <ul>
     *   <li>透支时失败消息里的 N = {@code -delta} —— 就是「当时只剩几支却取走了几支」里的后者
     *       （accept 1 第 4 段：剩 3 取 9 → <b>9</b>）；</li>
     *   <li>补入 / 正向调整恒过（{@code delta > 0}）；</li>
     *   <li>一串同刻取走时，新的一笔按 id 排在最后，等价于「余额 − qty ≥ 0」
     *       —— 并发用例（10 线程各取 1 支、剩余 3）就是它挡住的。</li>
     * </ul>
     *
     * @param initQty 初始支数
     * @param flows   已有的<b>未删</b>流水
     * @param delta   这一笔的带符号变化量
     * @throws ServiceException 取走数超过当时的剩余
     */
    static void requireInsertable(int initQty, List<CryoBalanceChecker.Flow> flows, int delta) {
        int balance = initQty;
        for (CryoBalanceChecker.Flow flow : CryoBalanceChecker.ordered(flows)) {
            balance += flow.delta();
        }
        if (balance + delta < 0) {
            throw new ServiceException("取走支数超过当前剩余（当前剩余 " + balance
                + " 支，本次要 " + -delta + " 支）", 400);
        }
    }

    /**
     * 把「改后的那一笔」并进未删流水集合（同 id 的旧形态被替换；新登记时 id 用
     * {@link #UNSAVED_ID} 之类的值，靠 {@code flow_time} 排序、必要时排在最后）。
     */
    static List<CryoBalanceChecker.Flow> merge(List<CryoBalanceChecker.Flow> flows,
                                               CryoBalanceChecker.Flow changed) {
        List<CryoBalanceChecker.Flow> out = new ArrayList<>();
        if (flows != null) {
            for (CryoBalanceChecker.Flow flow : flows) {
                if (flow != null && !Objects.equals(flow.id(), changed.id())) {
                    out.add(flow);
                }
            }
        }
        out.add(changed);
        return out;
    }

    /** 去掉被删的那一笔之后的集合（软删的流水不再计入剩余）。 */
    static List<CryoBalanceChecker.Flow> exclude(List<CryoBalanceChecker.Flow> flows, Long flowId) {
        List<CryoBalanceChecker.Flow> out = new ArrayList<>();
        if (flows != null) {
            for (CryoBalanceChecker.Flow flow : flows) {
                if (flow != null && !Objects.equals(flow.id(), flowId)) {
                    out.add(flow);
                }
            }
        }
        return out;
    }

    /** 未落库的新流水在排序里的占位 id：同一时刻它应当排在已有流水之后。 */
    static final long UNSAVED_ID = Long.MAX_VALUE;

    private static int deltaOf(CryoFlow flow) {
        return flow.getDelta() == null ? 0 : flow.getDelta();
    }

    private static String locationOf(CryoBatch batch) {
        return CryoBalanceChecker.locationOf(batch.getInMinus80(), batch.getToLn2Time());
    }

    private static int initQtyOf(CryoBatch batch) {
        return batch.getInitQty() == null ? 0 : batch.getInitQty();
    }

    private static List<CryoBalanceChecker.Flow> toCheckerFlows(List<CryoFlow> rows) {
        List<CryoBalanceChecker.Flow> out = new ArrayList<>();
        if (rows != null) {
            for (CryoFlow row : rows) {
                if (row != null) {
                    out.add(new CryoBalanceChecker.Flow(row.getId(), row.getFlowTime(), deltaOf(row)));
                }
            }
        }
        return out;
    }

    private static int compareDesc(LocalDateTime a, LocalDateTime b) {
        if (a == null && b == null) {
            return 0;
        }
        if (a == null) {
            return 1;
        }
        if (b == null) {
            return -1;
        }
        return b.compareTo(a);
    }

    private static int compareDesc(Long a, Long b) {
        if (a == null && b == null) {
            return 0;
        }
        if (a == null) {
            return 1;
        }
        if (b == null) {
            return -1;
        }
        return b.compareTo(a);
    }

    // ── 内部：入参判据 ────────────────────────────────────────────────────────

    private static String requireFlowType(String flowType) {
        String value = flowType == null ? null : flowType.trim();
        if (!TYPE_TAKE.equals(value) && !TYPE_ADD.equals(value) && !TYPE_ADJUST.equals(value)) {
            throw new ServiceException("登记类型只能是 take（取走）/ add（补入）/ adjust（盘点调整）：" + flowType, 400);
        }
        return value;
    }

    /**
     * {@code qty} → 带符号 {@code delta}（符号就是语义，别靠 {@code flow_type} 现算加减）。
     *
     * <p>★ take / add 的 {@code qty} 必须是<b>正整数</b>（0 与负数都拒，accept 1 第 7 段）；
     * ★ adjust 带符号、<b>不为 0</b>（accept 1 第 5 段 {@code qty=-1} 无原因 → 400；
     * 第 6 段 {@code qty=-9} 调成负数 → 400）。
     */
    static int deltaOf(String flowType, Integer qty) {
        if (qty == null) {
            throw new ServiceException("支数不能为空", 400);
        }
        return switch (flowType) {
            case TYPE_TAKE -> {
                if (qty <= 0) {
                    throw new ServiceException("取走支数必须是正整数（当前：" + qty + "）", 400);
                }
                yield -qty;
            }
            case TYPE_ADD -> {
                if (qty <= 0) {
                    throw new ServiceException("补入支数必须是正整数（当前：" + qty + "）", 400);
                }
                yield qty;
            }
            case TYPE_ADJUST -> {
                if (qty == 0) {
                    throw new ServiceException("盘点调整量不能为 0", 400);
                }
                yield qty;
            }
            default -> throw new ServiceException("登记类型只能是 take / add / adjust：" + flowType, 400);
        };
    }

    /**
     * 用途 / 原因：{@code adjust} 必填（FLOW:F-CRYO-02.step3），其余可空。
     *
     * @throws ServiceException adjust 缺原因
     */
    static String purposeOf(String flowType, String purpose) {
        String value = trimToNull(purpose);
        if (TYPE_ADJUST.equals(flowType) && value == null) {
            throw new ServiceException("盘点调整必须写原因", 400);
        }
        return value;
    }

    private static String operatorNameOf(String operatorName) {
        String value = trimToNull(operatorName);
        return value != null ? value : currentNickname();
    }

    static LocalDateTime flowTimeOf(String flowTime) {
        String value = trimToNull(flowTime);
        if (value == null) {
            return LocalDateTime.now();
        }
        try {
            return LocalDateTime.parse(value, DATE_TIME);
        } catch (DateTimeParseException e) {
            // 只给日期时补零点（小程序的日期选择器会这么传，别让「实现对了却 400」）
            try {
                return LocalDate.parse(value, DATE).atStartOfDay();
            } catch (DateTimeParseException ignored) {
                throw new ServiceException("发生时间格式不对，应为 yyyy-MM-dd HH:mm:ss：" + flowTime, 400);
            }
        }
    }

    /**
     * 纯日期入参：接受 {@code yyyy-MM-dd}，也接受带时间的 {@code yyyy-MM-dd HH:mm:ss}
     * （取日期部分）。
     */
    private static LocalDate parseDate(String value, String label) {
        String text = trimToNull(value);
        if (text == null) {
            throw new ServiceException(label + "不能为空", 400);
        }
        try {
            return LocalDate.parse(text, DATE);
        } catch (DateTimeParseException ignored) {
            // 继续试带时间的形式
        }
        try {
            return LocalDateTime.parse(text, DATE_TIME).toLocalDate();
        } catch (DateTimeParseException ignored) {
            throw new ServiceException(label + "格式不对，应为 yyyy-MM-dd：" + value, 400);
        }
    }

    private static String trimToNull(String value) {
        return StringUtils.isBlank(value) ? null : value.trim();
    }

    // ── 内部：VO 装配 ─────────────────────────────────────────────────────────

    private CryoFlowRecordVo toVo(CryoFlow row, Integer balanceAfter) {
        CryoFlowRecordVo vo = new CryoFlowRecordVo();
        vo.setId(row.getId());
        vo.setBatchId(row.getBatchId());
        vo.setFlowType(row.getFlowType());
        vo.setDelta(row.getDelta());
        vo.setFromLocation(row.getFromLocation());
        vo.setOperatorName(row.getOperatorName());
        vo.setFlowTime(row.getFlowTime());
        vo.setPurpose(row.getPurpose());
        vo.setCreateBy(row.getCreateBy());
        vo.setUpdateBy(row.getUpdateBy());
        vo.setCreateTime(row.getCreateTime());
        vo.setUpdateTime(row.getUpdateTime());
        vo.setBalanceAfter(balanceAfter);
        vo.setEdited(isEdited(row));
        // 最后修改人姓名：改过取 update_by 的，没改过取 create_by 的（与 SAMPLE / EMBED 域同一条口径）
        Long modifierId = row.getUpdateBy() != null ? row.getUpdateBy() : row.getCreateBy();
        vo.setUpdateByName(nameResolver == null ? null : nameResolver.nameOf(modifierId));
        return vo;
    }

    /**
     * 这一笔是否被改过：{@code update_by} 换过人就一定是；同一个人改的则看
     * {@code update_time} 是否晚于 {@code create_time}（数据库时间精度到秒）。
     *
     * <p>★ 不用「{@code update_time} 非空」当判据：{@code update(null, wrapper)} 在本实现里
     * 总会填 {@code update_time}，但 INSERT 走的 {@code insertFill} 也会填它 —— 两者靠时间先后区分。
     */
    static boolean isEdited(CryoFlow row) {
        if (row == null) {
            return false;
        }
        if (row.getUpdateBy() != null && !row.getUpdateBy().equals(row.getCreateBy())) {
            return true;
        }
        Date created = row.getCreateTime();
        Date updated = row.getUpdateTime();
        return created != null && updated != null && updated.after(created);
    }

    private static Long currentUserId() {
        try {
            LoginUser loginUser = LoginHelper.getLoginUser();
            return loginUser == null ? null : loginUser.getUserId();
        } catch (Exception ignored) {
            // 无 Sa-Token 上下文（单测 / 后台任务）：不给 update_by 赋值，别把调用整个炸掉
            return null;
        }
    }

    private static String currentNickname() {
        try {
            LoginUser loginUser = LoginHelper.getLoginUser();
            return loginUser == null ? null : loginUser.getNickname();
        } catch (Exception ignored) {
            return null;
        }
    }

}
