package org.dromara.lqg.embed.mp;

import cn.dev33.satoken.annotation.SaCheckRole;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.lqg.embed.domain.bo.EmbedSubmitBo;
import org.dromara.lqg.sample.service.PatchBodyReader;
import org.dromara.lqg.embed.domain.vo.EmbedVo;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 小程序<b>内部人员</b>侧「石蜡包埋送样记录」的四个端点
 * （doc/api-contract.md 第 63 行；EMBED-MP-001）。
 *
 * <pre>
 * GET  /mp/int/embed/list   内部管理表格页（全表 + 搜索/核验状态/染色筛选）与
 *                           「历史编辑记录」（sort=recent，可选 mine=true）
 * GET  /mp/int/embed/{id}   详情（带 updateByName / updateTime / handlerName / mine / editable）
 * POST /mp/int/embed        首页点表新增一条（内部录入直接 valid、石蜡块编号必填）
 * PUT  /mp/int/embed        补填 / 修改（谁录的都能改；待核验 / 无效 → 400）
 * </pre>
 *
 * <p>★ <b>类级 {@code @SaCheckRole("lqg_internal")}</b>（契约「通用」一节的表：{@code /mp/int/**}
 * 只给小程序内部人员）：外部角色（{@code lqg_external}）打这一组一律 403 ——
 * AUTH-EXT-002 的报告 WARN-5 与探针 P5 记的「外部打 {@code /mp/int/embed/list} 现在是 404
 * No endpoint」在本类落地的这一刻收口成 403。管理员同时带 internal 角色，所以
 * {@code --as staff}（lqg_internal）与管理员都进得来。
 *
 * <p>★ <b>本类不查库、不碰 ADR-0004 的咽喉</b>：{@code /mp/int/**} 是内部侧，读写全在
 * {@link MpEmbedService} → {@code embed} 包的 service；{@code /mp/ext/**}（外部侧）仍然只由
 * {@code org.dromara.lqg.ext} 包提供。
 *
 * <p>★ 校验只做「非空」，业务规则（编号必填与全库唯一、只能挂已核验有效样本、染色与 marker
 * 字典、状态闸）全在 service：两处各写一套判据等于给自己埋一个「小程序能过、工作台不能过」的分叉。
 *
 * @author EMBED-MP-001
 */
@RequiredArgsConstructor
@RestController
@RequestMapping("/mp/int/embed")
@SaCheckRole("lqg_internal")
public class MpEmbedController {

    private final MpEmbedService mpEmbedService;
    /** 补丁要知道「哪些键出现过」（没带 = 不改、带了空值 = 清空，FIX V28 / V33） */
    private final PatchBodyReader patchBodyReader;

    /**
     * 列表：内部管理表格页（搜索 + 核验状态 + 染色）与历史编辑记录（{@code sort=recent}）共用。
     */
    @GetMapping("/list")
    public TableDataInfo<EmbedVo> list(MpEmbedQueryBo query) {
        return mpEmbedService.list(query);
    }

    /**
     * 详情：只读页与修改页都靠它渲染（可写性以后端的 {@code editable} 为准）。
     */
    @GetMapping("/{id}")
    public R<EmbedVo> detail(@PathVariable Long id) {
        return R.ok(mpEmbedService.detail(id));
    }

    /**
     * 新增（首页宫格点进来；内部录入直接有效）。
     */
    @PostMapping
    public R<Long> create(@Valid @RequestBody EmbedSubmitBo bo) {
        return R.ok(mpEmbedService.create(bo));
    }

    /**
     * 补填 / 修改（历史编辑记录点一条进来，或内部管理只读页右上角切修改模式）——
     * 补丁语义（FIX V33）：没带的键不动；带了空值 = 清空（工序时间填错了能清掉）；清必填项 → 400。
     */
    @PutMapping
    public R<Void> update(@io.swagger.v3.oas.annotations.parameters.RequestBody(
        content = @Content(schema = @Schema(implementation = EmbedSubmitBo.class))) @RequestBody JsonNode body) {
        mpEmbedService.update(patchBodyReader.read(body, EmbedSubmitBo.class));
        return R.ok();
    }

}
