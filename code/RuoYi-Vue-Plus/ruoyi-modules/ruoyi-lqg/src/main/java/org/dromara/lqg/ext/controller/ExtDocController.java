package org.dromara.lqg.ext.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.lqg.ext.domain.bo.ExtDocQueryBo;
import org.dromara.lqg.ext.domain.vo.ExtDocDownloadVo;
import org.dromara.lqg.ext.domain.vo.ExtDocPagesVo;
import org.dromara.lqg.ext.domain.vo.ExtDocVo;
import org.dromara.lqg.ext.service.ExtDocAssemblyService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/*
 * 实现备注（给维护的人看，不进接口文档 / Swagger）：
 *
 * 外部「质控文档」的三个端点（doc/api-contract.md 的 QC / DOC 一节，
 * FLOW:F-DOC-02.step1 / step3）：
 *
 * <pre>
 * GET /mp/ext/doc/list                          可见样本名下已完成、且**外部版渲染成功**的文档
 * GET /mp/ext/doc/{sampleId}/{docKind}/pages    页面图（10 分钟签名链接）
 * GET /mp/ext/doc/{sampleId}/{docKind}/download 10 分钟签名下载链接（format=docx|pdf）
 * </pre>
 *
 * <p>★★ <b>{@code docKind} ∈ {sample_qc, organoid_qc, organoid_score, merged}</b>
 * （与内部 {@code /lqg/doc/**} 同一套下划线取值；质控草稿侧那套连字符
 * {@code sample-qc} 不在对外链路上）。
 *
 * <p>★★ <b>audience 不是入参</b>（ticket §0 口径 2）：这两个 handler 的签名里根本没有
 * {@code audience}，请求里带 {@code audience=internal} 只是被 Spring 忽略的一个陌生查询参数。
 * 真正的 external 是在 doc 域读口 {@code DocExternalQueryService} 里写死的 ——
 * 不是「读进来再覆盖」，是外部这条路拿不到第二个取值。内部版里那一格是内部编号，
 * 外部版里那一格随系统参数 {@code lqg.ext.show-internal-no}（默认留空），两份产物的对象键与指纹都不同（ADR-0005）。
 *
 * <p>★ <b>类级 {@code @SaCheckRole("lqg_external")}</b>（ADR-0004 的 I1）：
 * 内部账号打这里天然 403；外部账号打 {@code /lqg/doc/**} 也天然 403
 * （那条路要 {@code lqg:doc:query} 权限，外部没有）—— accept 2 的最后一段断的就是后者。
 *
 * <p>★ <b>本类只做转发、不查库</b>：可见范围在 {@code ExtScopeService}（ext 包里唯一允许
 * 持有 {@code *Mapper} 的实现类），文档行与产物在 {@code DocExternalQueryService}（doc 域），
 * 返回值只能是 ext 包里的 {@code Ext*} 类型（ADR-0004 的 I2 / I4）。
 *
 * @author AUTH-EXT-003
 */
/**
 * 外部人员的「质控文档」：清单、预览（页面图、文档中的图片、附件）、下载（10 分钟签名链接）。
 *
 * <p>只给外部角色；只含可见样本里已完成、且外部版生成成功的文档。拿不到的一律按「不存在」返回 404。
 */
@RequiredArgsConstructor
@RestController
@RequestMapping("/mp/ext/doc")
@SaCheckRole("lqg_external")
public class ExtDocController {

    private final ExtDocAssemblyService extDocAssemblyService;

    /**
     * 清单：可见样本里已完成、且外部版生成成功的文档（可按样本、文档种类、完成时间筛）。
     */
    @GetMapping("/list")
    public TableDataInfo<ExtDocVo> list(ExtDocQueryBo query) {
        return extDocAssemblyService.list(currentUserId(), query);
    }

    /**
     * 预览：页面图 + 文档中的图片（缩略与原图）+ 附件。看不到 / 没有这一份一律 404。
     */
    @GetMapping("/{sampleId}/{docKind}/pages")
    public R<ExtDocPagesVo> pages(@PathVariable Long sampleId, @PathVariable String docKind) {
        return R.ok(extDocAssemblyService.pages(currentUserId(), sampleId, docKind));
    }

    /**
     * 下载（{@code format=docx|pdf}，默认 docx）：10 分钟签名链接 + 文件名（文档名-送检单号）。
     */
    @GetMapping("/{sampleId}/{docKind}/download")
    public R<ExtDocDownloadVo> download(@PathVariable Long sampleId,
                                        @PathVariable String docKind,
                                        @RequestParam(required = false, defaultValue = "docx") String format) {
        return R.ok(extDocAssemblyService.download(currentUserId(), sampleId, docKind, format));
    }

    private Long currentUserId() {
        Long userId = LoginHelper.getUserId();
        if (userId == null) {
            throw new ServiceException("未登录", 401);
        }
        return userId;
    }

}
