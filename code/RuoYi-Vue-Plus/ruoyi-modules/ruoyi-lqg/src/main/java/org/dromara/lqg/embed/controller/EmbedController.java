package org.dromara.lqg.embed.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.lqg.embed.domain.bo.EmbedFillBo;
import org.dromara.lqg.embed.domain.bo.EmbedQueryBo;
import org.dromara.lqg.embed.domain.bo.EmbedSubmitBo;
import org.dromara.lqg.embed.domain.bo.EmbedVerifyBo;
import org.dromara.lqg.embed.domain.vo.EmbedVo;
import org.dromara.lqg.embed.export.EmbedExportService;
import org.dromara.lqg.embed.service.EmbedQueryService;
import org.dromara.lqg.embed.service.EmbedService;
import org.dromara.lqg.embed.service.EmbedVerifyService;
import org.dromara.lqg.sample.domain.bo.PatchBody;
import org.dromara.lqg.sample.service.PatchBodyReader;
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
 * PUT    /lqg/embed/{id}/verify 核验 / 改判（action=valid|invalid；可带 fill 与核验结论同一事务补填，FIX V02b）
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
    private final EmbedExportService embedExportService;
    /** 补丁要知道「哪些键出现过」（没带 = 不改、带了空值 = 清空，FIX V28 / V33） */
    private final PatchBodyReader patchBodyReader;

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
     * 修改 / 补填 —— 补丁语义（FIX V33）：没带的键不动，带了空值 = 清空（工序时间填错了能清掉），
     * 清必填项（所挂样本、石蜡块编号）→ 400。收原始 JSON 是为了分清「没带」与「带了 null」，
     * 形状仍是 {@link EmbedSubmitBo}。
     */
    @SaCheckPermission("lqg:embed:edit")
    @PutMapping
    public R<Void> edit(@io.swagger.v3.oas.annotations.parameters.RequestBody(
        content = @Content(schema = @Schema(implementation = EmbedSubmitBo.class))) @RequestBody JsonNode body) {
        embedService.update(patchBodyReader.read(body, EmbedSubmitBo.class));
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
     *
     * <p>★ FIX V02b（issue #147）：可带 {@code fill}（核验抽屉里补填 / 改过的内容），与核验结论同一事务保存，
     * 补丁语义同 {@code PUT /lqg/embed}。收原始 JSON 是为了分清 {@code fill} 里「没带」与「带了 null」；
     * 顶层形状仍是 {@link EmbedVerifyBo}。原先 {@code @Valid} 管的那一条（{@code action} 不能为空）改由
     * {@code EmbedVerifyService} 在任何读写之前判，同一句话、同一个响应码。不带 {@code fill} = 与原先完全一样。
     */
    @SaCheckPermission("lqg:embed:verify")
    @PutMapping("/{id}/verify")
    public R<Void> verify(@PathVariable Long id, @io.swagger.v3.oas.annotations.parameters.RequestBody(
        content = @Content(schema = @Schema(implementation = EmbedVerifyBo.class))) @RequestBody JsonNode body) {
        EmbedVerifyBo bo = patchBodyReader.read(body, EmbedVerifyBo.class).value();
        embedVerifyService.verify(id, bo, readFill(body));
        return R.ok();
    }

    /**
     * 请求体里的 {@code fill}：没带 / {@code null} → {@code null}（补填段不动）；带了必须是 JSON 对象。
     */
    private PatchBody<EmbedFillBo> readFill(JsonNode body) {
        JsonNode fill = body.get("fill");
        if (fill == null || fill.isNull()) {
            return null;
        }
        if (!fill.isObject()) {
            throw new ServiceException("fill 必须是 JSON 对象（核验抽屉里补填 / 改过的内容）", 400);
        }
        return patchBodyReader.read(fill, EmbedFillBo.class);
    }

    /**
     * 按当前筛选导出「石蜡包埋送样记录」xlsx（FLOW:F-EMBED-01.step5，EMBED-WEB-001）。
     *
     * <p>参数与 {@code GET /lqg/embed/list} 同一组（同一个 {@link EmbedQueryBo} 绑定）；
     * 文件流由 {@code EmbedExportService} 出 —— 表头 16 列与甲方模板
     * {@code _input/templates/石蜡包埋送样记录模板.xlsx} 逐字同序。
     *
     * <p>★ 排障：筛选走 <b>query 参数</b>（不是 JSON body），
     * {@code POST /lqg/embed/export?internalNo=T-hli01} —— 与 {@code api.sh} 的形状一致。
     * ★ 小程序表格页的 {@code GET /mp/int/export/embed}（SYS-EXPORT-001）复用同一个
     * {@code EmbedExportService}，两处文件逐列一致。
     */
    @SaCheckPermission("lqg:embed:export")
    @PostMapping("/export")
    public void export(EmbedQueryBo query, HttpServletResponse response) {
        embedExportService.export(query, response);
    }

}
