package org.dromara.lqg.cryo.mp;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.lqg.cryo.batch.domain.bo.CryoBatchSubmitBo;
import org.dromara.lqg.cryo.batch.domain.vo.CryoBatchPageVo;
import org.dromara.lqg.cryo.batch.domain.vo.CryoBatchVo;
import org.dromara.lqg.cryo.batch.service.CryoBatchService;
import org.dromara.lqg.cryo.batch.service.CryoQueryService;
import org.dromara.lqg.cryo.flow.domain.vo.CryoFlowRecordVo;
import org.dromara.lqg.cryo.flow.service.CryoFlowService;
import org.dromara.lqg.sample.domain.bo.PatchBody;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 小程序<b>内部人员</b>侧「-80 冻存记录」的读写
 * （doc/api-contract.md 第 73 行：{@code GET /mp/int/cryo/batch/list}、{@code GET …/batch/{id}}、
 * {@code POST|PUT …/batch}、{@code GET …/batch/{id}/flows}）。
 *
 * <p>★ 本类<b>不自己查库、不自己写库、不自己判超期</b>：
 * <ul>
 *   <li>读走 {@link CryoQueryService#list} —— 与工作台列表<b>同一个方法</b>，所以
 *       {@code tabCounts:{all,overdue,ln2,emptied}}、每行的 {@code overdue / overdueDays / remainingQty /
 *       location / emptied / handlerName / mine} 全部是现成的、且与超期清单同源（CRYO-REMIND-001 §3.3
 *       明写「MP 端点只需转发」）；</li>
 *   <li>写走 {@link CryoBatchService} —— {@code initQty} 改了同样跑
 *       {@code CryoBalanceChecker} 的逐笔校验（CR-20260917-04 / FLOW:F-CRYO-02.step5），
 *       小程序这边<b>不另写一份「剩余不为负」的判据</b>；内部人员改谁录的都行
 *       （CR-20260918-07），本类<b>不校验「是不是本人录的」</b>；</li>
 *   <li>取用登记的读走 {@link CryoFlowService#list}。</li>
 * </ul>
 *
 * <p>★★ <b>本类没有任何写流水的口</b>：没有 {@code create/update/delete} 流水、没有 {@code toLn2}、
 * 没有删批次。2026-09-24 起小程序内部人员也能在批次详情弹层里取走 / 补入 / 转液氮 / 改删登记
 * （甲方「小程序和工作台界面都能操作」），但走的是 {@code /lqg/cryo/batch/{id}/**} 那一份写口
 * （{@code CryoFlowController}），不在这里转发 —— 这些路径在 {@code /mp/int/cryo/**} 上仍然不存在。
 *
 * @author CRYO-MP-001
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MpCryoService {

    private final CryoQueryService cryoQueryService;
    private final CryoBatchService cryoBatchService;
    private final CryoFlowService cryoFlowService;

    /**
     * 列表：内部管理「-80 冻存」工作表（三个页签）与「历史编辑记录」共用。
     *
     * <p>带 {@code sort=recent} 时是历史那一档（默认中心全员，开关打开才带 {@code mine=true}）；
     * 不带时是工作表那一档（默认超期置顶 + {@code tabCounts}）。
     */
    public CryoBatchPageVo list(MpCryoQueryBo query) {
        return cryoQueryService.list(query);
    }

    /**
     * 详情（只读页 / 修改模式顶部「最后修改」都靠它渲染）：不存在 / 已软删 → 业务码 404。
     */
    public CryoBatchVo detail(Long id) {
        if (id == null) {
            throw new ServiceException("缺少冻存批次 id", 400);
        }
        CryoBatchVo vo = cryoQueryService.detail(id);
        if (vo == null) {
            throw new ServiceException("冻存批次不存在", 404);
        }
        return vo;
    }

    /**
     * 新增（首页点「-80 冻存记录」进来）：挂已核验有效样本、代数 {@code ^P\d{1,3}$}、
     * 初始支数为正整数、选「否」（直接进液氮）必须填液氮位置 —— 校验全在
     * {@link CryoBatchService#create}。
     */
    public Long create(CryoBatchSubmitBo bo) {
        return cryoBatchService.create(bo);
    }

    /**
     * 修改（历史编辑记录点一行，或内部管理只读详情右上角「修改」）。
     *
     * <p>★ <b>全部可改、含冻存数量（= 初始支数）</b>；改小到让某一步剩余为负时由
     * {@link CryoBatchService#update} 拒绝（400，消息里指出是哪一笔），前端把提示原样显示。
     * 谁录的都能改（CR-20260918-07）。
     */
    public void update(PatchBody<CryoBatchSubmitBo> body) {
        cryoBatchService.update(body);
        CryoBatchSubmitBo bo = body == null ? null : body.value();
        log.info("小程序内部修改冻存批次：id={} initQty={}", bo == null ? null : bo.getId(),
            bo == null ? null : bo.getInitQty());
    }

    /**
     * 这一批的取用登记（读口）：时间倒序，每行带操作后剩余 {@code balanceAfter} 与
     * 「改过没有」{@code edited} / {@code updateByName}（CRYO-FLOW-001 已装配好）。
     *
     * <p>批次不存在 / 已软删 → 400「冻存批次不存在」（与详情口同语义，不泄露存在性）。
     */
    public List<CryoFlowRecordVo> flows(Long batchId) {
        if (batchId == null) {
            throw new ServiceException("缺少冻存批次 id", 400);
        }
        if (cryoQueryService.entity(batchId) == null) {
            throw new ServiceException("冻存批次不存在", 400);
        }
        return cryoFlowService.list(batchId);
    }

}
