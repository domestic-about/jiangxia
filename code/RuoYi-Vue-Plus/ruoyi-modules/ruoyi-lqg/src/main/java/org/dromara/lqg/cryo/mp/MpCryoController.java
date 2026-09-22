package org.dromara.lqg.cryo.mp;

import cn.dev33.satoken.annotation.SaCheckRole;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.lqg.cryo.batch.domain.bo.CryoBatchSubmitBo;
import org.dromara.lqg.cryo.batch.domain.vo.CryoBatchPageVo;
import org.dromara.lqg.cryo.batch.domain.vo.CryoBatchVo;
import org.dromara.lqg.cryo.flow.domain.vo.CryoFlowRecordVo;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 小程序<b>内部人员</b>侧「-80 冻存记录」的端点
 * （doc/api-contract.md 第 73 行；CRYO-MP-001）。
 *
 * <pre>
 * GET  /mp/int/cryo/batch/list      内部管理「-80 冻存」工作表（全部 / 超期 / 液氮三个页签）
 *                                   与「历史编辑记录」（sort=recent，可选 mine=true）
 * GET  /mp/int/cryo/batch/{id}      详情（带 updateByName / updateTime / handlerName / mine）
 * POST /mp/int/cryo/batch           首页点表新增一条（挂已核验有效样本）
 * PUT  /mp/int/cryo/batch           修改（含冻存数量 = 初始支数；逐笔校验，改负被拒）
 * GET  /mp/int/cryo/batch/{id}/flows 这一批的取用登记（<b>只读</b>，时间倒序，带操作后剩余）
 * </pre>
 *
 * <p>★ <b>类级 {@code @SaCheckRole("lqg_internal")}</b>（契约「通用」一节的表：{@code /mp/int/**}
 * 只给小程序内部人员）：外部角色（{@code lqg_external}）打这一组一律 403 ——
 * 「冻存信息明确不对外」（accept 1 最后一段 / accept 2 最后一段各有一段）。
 *
 * <p>★★ <b>这里没有取走 / 补入 / 转液氮 / 改删登记的端点</b>（CR-20260917-05、ticket §2 §3）：
 * 那五个写口只在 {@code /lqg/cryo/batch/{id}/**}（工作台，CRYO-FLOW-001）。本类<b>不转发</b>它们，
 * 也不建 {@code POST|PUT|DELETE …/{id}/flow} / {@code PUT …/{id}/to-ln2} / 删批次 ——
 * 不是「前端不放按钮」：accept 2 会真调这些路径要求 404 且库里不变。
 *
 * <p>★ 超期判定<b>不在本类</b>：{@code overdue / overdueDays / tabCounts / overdueOnly}
 * 全部由 {@code CryoQueryService.list}（CRYO-REMIND-001 的唯一判定）算好，本类只转发。
 * 页签数字取接口给的 {@code tabCounts}，前端不自己数 rows。
 *
 * @author CRYO-MP-001
 */
@RequiredArgsConstructor
@RestController
@RequestMapping("/mp/int/cryo/batch")
@SaCheckRole("lqg_internal")
public class MpCryoController {

    private final MpCryoService mpCryoService;

    /**
     * 列表：工作表（三个页签）与历史编辑记录（{@code sort=recent}）共用。
     *
     * <p>响应体是 {@link CryoBatchPageVo}（{@code TableDataInfo} 的子类）：在 {@code total / rows}
     * 之上多带 {@code tabCounts:{all, overdue, ln2}}。返回类型必须写成子类，
     * Jackson 才会把这个键序列化出来（CRYO-REMIND-001 坑）。
     */
    @GetMapping("/list")
    public CryoBatchPageVo list(MpCryoQueryBo query) {
        return mpCryoService.list(query);
    }

    /**
     * 详情：只读页与修改模式（顶部「最后修改：某某 · 时间」）都靠它渲染。
     */
    @GetMapping("/{id}")
    public R<CryoBatchVo> detail(@PathVariable Long id) {
        return R.ok(mpCryoService.detail(id));
    }

    /**
     * 新增（首页宫格点进来）。
     */
    @PostMapping
    public R<Long> create(@Valid @RequestBody CryoBatchSubmitBo bo) {
        return R.ok(mpCryoService.create(bo));
    }

    /**
     * 修改 / 改冻存数量（历史编辑记录点一条进来，或内部管理批次详情右上角「修改」）。
     */
    @PutMapping
    public R<Void> update(@Valid @RequestBody CryoBatchSubmitBo bo) {
        mpCryoService.update(bo);
        return R.ok();
    }

    /**
     * 这一批的取用登记（只读）：时间倒序，每行带操作后剩余与「改过没有」。
     *
     * <p>没有配套的 POST / PUT / DELETE —— 登记本身只能在网页工作台改。
     */
    @GetMapping("/{id}/flows")
    public R<List<CryoFlowRecordVo>> flows(@PathVariable Long id) {
        return R.ok(mpCryoService.flows(id));
    }

}
