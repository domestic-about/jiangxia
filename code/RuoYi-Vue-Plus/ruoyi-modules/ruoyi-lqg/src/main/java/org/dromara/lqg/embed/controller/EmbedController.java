package org.dromara.lqg.embed.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.lqg.embed.domain.bo.EmbedQueryBo;
import org.dromara.lqg.embed.domain.bo.EmbedSubmitBo;
import org.dromara.lqg.embed.domain.bo.EmbedVerifyBo;
import org.dromara.lqg.embed.domain.vo.EmbedVo;
import org.dromara.lqg.embed.service.EmbedQueryService;
import org.dromara.lqg.embed.service.EmbedService;
import org.dromara.lqg.embed.service.EmbedVerifyService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 石蜡包埋送样记录的内部接口（doc/api-contract.md 的 EMBED 一节）。
 *
 * <pre>
 * GET    /lqg/embed/list        列表（筛选见 {@link EmbedQueryBo}；待核验置顶，其余按创建时间倒序）
 * GET    /lqg/embed/{id}        详情（行内带 internalNo / submitNo / sampleVerifyStatus / markers / stainTypes 数组）
 * POST   /lqg/embed             内部录入（石蜡块编号必填、直接 valid）
 * PUT    /lqg/embed             修改 / 补填（patch 语义；待核验 / 无效的记录 400）
 * DELETE /lqg/embed/{ids}       软删
 * PUT    /lqg/embed/{id}/verify 核验 / 改判（action=valid|invalid）
 * </pre>
 *
 * <p>权限串 {@code lqg:embed:{list,query,add,edit,remove,verify}}，与 Flyway 迁移
 * {@code V202609231100__EMBED-MODEL-001-embed.sql} 里菜单 5301-5307 的 perms 逐字一致，
 * 授给 101（lqg_admin）与 102（lqg_internal）。★ 缺这几行 {@code --as staff} 恒 403
 * （不是 500）—— 新票加 {@code @SaCheckPermission("lqg:xxx:yyy")} 前先查 {@code sys_menu.perms}。
 *
 * <p>★ {@code POST /lqg/embed/export}（导出）**不在本票**（ticket §3：三张 Excel 导出在
 * SAMPLE-EXPORT-001 / EMBED-WEB-001），所以 {@code lqg:embed:export} 只有权限行、没有端点。
 *
 * <p>★ 对外接口（{@code /mp/ext/embed/**}，AUTH-EXT-002）、小程序内部接口（{@code /mp/int/embed/**}，
 * EMBED-MP-001）都不在这里；它们复用本包的 service（{@code EmbedExternalService} /
 * {@code EmbedQueryService} / {@code EmbedService}）。
 *
 * @author EMBED-MODEL-001
 */
@RequiredArgsConstructor
@RestController
@RequestMapping("/lqg/embed")
public class EmbedController {

    private final EmbedService embedService;
    private final EmbedQueryService embedQueryService;
    private final EmbedVerifyService embedVerifyService;

    /**
     * 列表（分页）。
     */
    @SaCheckPermission("lqg:embed:list")
    @GetMapping("/list")
    public TableDataInfo<EmbedVo> list(EmbedQueryBo query) {
        return embedQueryService.list(query);
    }

    /**
     * 详情。已软删 / 不存在一律按「不存在」处理（不泄露存在性）。
     */
    @SaCheckPermission("lqg:embed:query")
    @GetMapping("/{id}")
    public R<EmbedVo> detail(@PathVariable Long id) {
        EmbedVo vo = embedQueryService.detail(id);
        if (vo == null) {
            throw new ServiceException("石蜡包埋记录不存在");
        }
        return R.ok(vo);
    }

    /**
     * 内部录入。
     */
    @SaCheckPermission("lqg:embed:add")
    @PostMapping
    public R<Long> add(@Valid @RequestBody EmbedSubmitBo bo) {
        return R.ok(embedService.create(bo));
    }

    /**
     * 修改 / 补填。
     */
    @SaCheckPermission("lqg:embed:edit")
    @PutMapping
    public R<Void> edit(@Valid @RequestBody EmbedSubmitBo bo) {
        embedService.update(bo);
        return R.ok();
    }

    /**
     * 软删（契约是 {@code /{ids}} 逗号分隔，与若依上游删法一致）。
     */
    @SaCheckPermission("lqg:embed:remove")
    @DeleteMapping("/{ids}")
    public R<Void> remove(@PathVariable String ids) {
        embedService.remove(ids);
        return R.ok();
    }

    /**
     * 核验 / 改判（外部送样的 pending → valid / invalid，内部改判 invalid → valid）。
     */
    @SaCheckPermission("lqg:embed:verify")
    @PutMapping("/{id}/verify")
    public R<Void> verify(@PathVariable Long id, @Valid @RequestBody EmbedVerifyBo bo) {
        embedVerifyService.verify(id, bo);
        return R.ok();
    }

}
