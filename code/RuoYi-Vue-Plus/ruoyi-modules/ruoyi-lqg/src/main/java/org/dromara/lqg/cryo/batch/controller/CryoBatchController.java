package org.dromara.lqg.cryo.batch.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.lqg.cryo.batch.domain.bo.CryoBatchSubmitBo;
import org.dromara.lqg.cryo.batch.domain.bo.CryoQueryBo;
import org.dromara.lqg.cryo.batch.domain.vo.CryoBatchPageVo;
import org.dromara.lqg.cryo.batch.domain.vo.CryoBatchVo;
import org.dromara.lqg.cryo.batch.service.CryoBatchService;
import org.dromara.lqg.cryo.batch.service.CryoQueryService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 冻存批次的内部接口（doc/api-contract.md 的 CRYO 一节，ticket §2）。
 *
 * <pre>
 * GET    /lqg/cryo/batch/list   列表（筛选见 {@link CryoQueryBo}；行带 remainingQty / location /
 *                               internalNo / sourceUnitName，全是读时算 / 读时带出）
 * GET    /lqg/cryo/batch/{id}   详情（同样的读模型）
 * POST   /lqg/cryo/batch        新建批次（挂已核验有效样本；代数 ^P\d{1,3}$；直接进液氮必须填位置）
 * PUT    /lqg/cryo/batch        修改（初始支数可改：锁批次行 → 逐笔算剩余，任一步 < 0 → 400）
 * DELETE /lqg/cryo/batch/{ids}  软删（有未删流水的不许删）
 * </pre>
 *
 * <p>权限串 {@code lqg:cryo:{list,query,add,edit,remove}}，与 Flyway 迁移
 * {@code V202609241200__CRYO-MODEL-001-cryo.sql} 里菜单 5401-5407 的 perms 逐字一致，
 * 授给 101（lqg_admin）与 102（lqg_internal）。★ 缺这几行 {@code --as staff} 恒 403
 * （不是 500）—— 新票加 {@code @SaCheckPermission("lqg:xxx:yyy")} 前先查 {@code sys_menu.perms}。
 *
 * <p>★ <b>本票不做</b>（ticket §3 边界，逐条核过）：
 * <ul>
 *   <li>写流水 / 转液氮（{@code POST|PUT|DELETE …/{id}/flow}、{@code PUT …/{id}/to-ln2}）→ CRYO-FLOW-001；</li>
 *   <li><b>超期判定</b>与 {@code GET /lqg/cryo/overdue} → CRYO-REMIND-001（列表上的
 *       {@code overdue / overdueDays / tabCounts / overdueOnly} 与「默认排序超期置顶」
 *       由它在本控制器的响应体 {@link CryoBatchPageVo} 上补齐）；</li>
 *   <li>导出（{@code POST /lqg/cryo/batch/export}）→ CRYO-WEB-001（本票只落了权限行
 *       {@code lqg:cryo:export}；{@code lqg:cryo:flow} 同理是给 CRYO-FLOW-001 落的权限行）；</li>
 *   <li>页面（工作台 / 小程序）→ CRYO-WEB-001 / CRYO-MP-001。</li>
 * </ul>
 *
 * @author CRYO-MODEL-001
 */
@RequiredArgsConstructor
@RestController
@RequestMapping("/lqg/cryo/batch")
public class CryoBatchController {

    private final CryoBatchService cryoBatchService;
    private final CryoQueryService cryoQueryService;

    /**
     * 列表（分页）。
     *
     * <p>★ 响应体是 {@link CryoBatchPageVo}（{@code TableDataInfo} 的子类）：在
     * {@code total / rows} 之上多带 {@code tabCounts:{all, overdue, ln2}}（CRYO-REMIND-001）。
     * 返回类型必须写成子类，Jackson 才会把这个键序列化出来。
     */
    @SaCheckPermission("lqg:cryo:list")
    @GetMapping("/list")
    public CryoBatchPageVo list(CryoQueryBo query) {
        return cryoQueryService.list(query);
    }

    /**
     * 详情。已软删 / 不存在一律按「不存在」处理（不泄露存在性）。
     */
    @SaCheckPermission("lqg:cryo:query")
    @GetMapping("/{id}")
    public R<CryoBatchVo> detail(@PathVariable Long id) {
        CryoBatchVo vo = cryoQueryService.detail(id);
        if (vo == null) {
            throw new ServiceException("冻存批次不存在", 400);
        }
        return R.ok(vo);
    }

    /**
     * 新建批次。
     */
    @SaCheckPermission("lqg:cryo:add")
    @PostMapping
    public R<Long> add(@Valid @RequestBody CryoBatchSubmitBo bo) {
        return R.ok(cryoBatchService.create(bo));
    }

    /**
     * 修改批次（patch 语义；{@code initQty} 改了就逐笔校验）。
     */
    @SaCheckPermission("lqg:cryo:edit")
    @PutMapping
    public R<Void> edit(@Valid @RequestBody CryoBatchSubmitBo bo) {
        cryoBatchService.update(bo);
        return R.ok();
    }

    /**
     * 软删（契约是 {@code /{ids}} 逗号分隔，与若依上游删法一致）。
     */
    @SaCheckPermission("lqg:cryo:remove")
    @DeleteMapping("/{ids}")
    public R<Void> remove(@PathVariable String ids) {
        cryoBatchService.remove(ids);
        return R.ok();
    }

}
