package org.dromara.lqg.doc.publish;

import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.web.core.BaseController;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/*
 * 实现备注（给维护的人看，不进接口文档 / Swagger）：
 *
 * 「完成并同步 / 撤回」两个端点（doc/api-contract.md 的 QC 一节，ticket §2）。
 *
 * <pre>
 *   POST /lqg/qc/{sampleId}/{docType}/publish     完成并同步（draft → published）
 *   POST /lqg/qc/{sampleId}/{docType}/unpublish   手动撤回（published → draft）
 * </pre>
 *
 * <p>★ {@code docType} ∈ {@code sample-qc | organoid-qc | score}（路径里用连字符，
 * 与 {@code QcDocController} 的其它写接口同一套路径段）。
 *
 * <p>★ 权限 {@code lqg:qc:publish}（菜单 5503，QC-MODEL-001 的迁移里已授给 101 / 102
 * —— 那张票没落端点，端点归本票）。
 *
 * <p>★ 把两个端点**单独放在 doc.publish 包**而不是塞进 {@code QcDocController}：
 * 状态机（含「完成即触发渲染」与「改内容自动回草稿」）是一整块口径，
 * 混进 CRUD 控制器里迟早会被后来的写接口绕过去。
 *
 * @author DOC-PUBLISH-001
 */
/**
 * 质控文档的「完成并同步」与「撤回」。
 *
 * <pre>
 *   POST /lqg/qc/{sampleId}/{docType}/publish     完成并同步（草稿 → 已完成，并生成内部版、外部版与合并件）
 *   POST /lqg/qc/{sampleId}/{docType}/unpublish   撤回（已完成 → 草稿，送检方立即看不到）
 * </pre>
 *
 * <p>docType 取 sample-qc / organoid-qc / score。已完成的再点完成、对草稿点撤回都返回 400。
 */
@RequiredArgsConstructor
@RestController
@RequestMapping("/lqg/qc")
public class DocPublishController extends BaseController {

    private final DocPublishService docPublishService;

    /**
     * 完成并同步给送检方：置 published + 记完成人与时间 + 触发内部版 / 外部版 / 合并件渲染。
     */
    @SaCheckPermission("lqg:qc:publish")
    @PostMapping("/{sampleId}/{docType}/publish")
    public R<Void> publish(@PathVariable Long sampleId, @PathVariable String docType) {
        docPublishService.publish(sampleId, docType);
        return R.ok();
    }

    /**
     * 手动撤回：published → draft、清完成时间；对草稿调用一律 400。
     */
    @SaCheckPermission("lqg:qc:publish")
    @PostMapping("/{sampleId}/{docType}/unpublish")
    public R<Void> unpublish(@PathVariable Long sampleId, @PathVariable String docType) {
        docPublishService.unpublish(sampleId, docType);
        return R.ok();
    }

}
