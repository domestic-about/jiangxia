package org.dromara.lqg.sample.mp;

import cn.dev33.satoken.annotation.SaCheckRole;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.lqg.sample.domain.bo.SampleSubmitBo;
import org.dromara.lqg.sample.domain.vo.SampleVo;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 小程序<b>内部人员</b>侧「样本记录信息表」的四个端点
 * （doc/api-contract.md 第 49 行；SAMPLE-MP-001）。
 *
 * <pre>
 * GET  /mp/int/sample/list   内部管理表格页（全表）与「历史编辑记录」（sort=recent，可选 mine=true）
 * GET  /mp/int/sample/{id}   详情（带 updateByName / updateTime / handlerName / mine / editable）
 * POST /mp/int/sample        首页点表新增一条（内部录入直接 valid）
 * PUT  /mp/int/sample        修改（valid 谁录的都能改；待核验 / 无效 → 400）
 * </pre>
 *
 * <p>★ <b>类级 {@code @SaCheckRole("lqg_internal")}</b>（契约第 19 行）：外部角色
 * （{@code lqg_external}）打这一组一律 403 —— AUTH-EXT-001 的 accept 里那段
 * 「{@code --as extA GET /mp/int/sample/list} 期望 403」就是等本票把端点注册出来才能收口。
 * 管理员同时带 internal 角色，所以 {@code --as staff}（lqg_internal）与管理员都进得来。
 *
 * <p>★ <b>本类不查库、不碰 ADR-0004 的咽喉</b>：{@code /mp/int/**} 是内部侧，
 * 读写全在 {@code sample} 包的 service 里（{@link MpSampleService}）；
 * {@code /mp/ext/**} 那一组（外部侧）仍然只由 {@code org.dromara.lqg.ext} 包提供。
 *
 * <p>★ 校验只做「非空」，业务规则（必填、编号唯一、状态闸）全在 service：
 * 两处各写一套判据等于给自己埋一个「小程序能过、工作台不能过」的分叉。
 *
 * @author SAMPLE-MP-001
 */
@RequiredArgsConstructor
@RestController
@RequestMapping("/mp/int/sample")
@SaCheckRole("lqg_internal")
public class MpSampleController {

    private final MpSampleService mpSampleService;

    /**
     * 列表：默认全表（内部管理表格页）；带 {@code sort=recent} 时是「历史编辑记录」
     * 那一档（中心内部人员经手过的 + 按最后修改时间倒序），开关打开再带 {@code mine=true}。
     */
    @GetMapping("/list")
    public TableDataInfo<SampleVo> list(MpSampleQueryBo query) {
        return mpSampleService.list(query);
    }

    /**
     * 详情：带「最后修改 / 经手人 / 可写性」，修改模式靠它渲染。
     */
    @GetMapping("/{id}")
    public R<SampleVo> detail(@PathVariable Long id) {
        return R.ok(mpSampleService.detail(id));
    }

    /**
     * 新增（首页宫格点进来）。
     */
    @PostMapping
    public R<Long> create(@Valid @RequestBody SampleSubmitBo bo) {
        return R.ok(mpSampleService.create(bo));
    }

    /**
     * 修改（历史编辑记录 / 内部管理只读页切修改模式）。
     */
    @PutMapping
    public R<Void> update(@RequestBody SampleSubmitBo bo) {
        mpSampleService.update(bo);
        return R.ok();
    }

}
