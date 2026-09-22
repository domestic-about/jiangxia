package org.dromara.lqg.qc.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.lqg.qc.domain.bo.DocAttachmentBo;
import org.dromara.lqg.qc.domain.bo.DocImageBo;
import org.dromara.lqg.qc.domain.bo.DocImageSortBo;
import org.dromara.lqg.qc.domain.bo.QcOrganoidSaveBo;
import org.dromara.lqg.qc.domain.bo.QcSampleSaveBo;
import org.dromara.lqg.qc.domain.bo.QcScoreSaveBo;
import org.dromara.lqg.qc.domain.vo.QcDocBundleVo;
import org.dromara.lqg.qc.service.QcDocService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 三份质控文档的内部读写（doc/api-contract.md 的 QC / DOC 一节）。
 *
 * <pre>
 * GET    /lqg/qc/{sampleId}                             三份文档 + 图片 + 附件 + 样本只读字段（首次访问建三份空草稿）
 * PUT    /lqg/qc/{sampleId}/sample-qc                   保存样本质控表
 * PUT    /lqg/qc/{sampleId}/organoid-qc                 保存类器官质控表
 * PUT    /lqg/qc/{sampleId}/score                       保存评分表（只收四个 level，分值后端按字典回填）
 * POST   /lqg/qc/{sampleId}/{docType}/image             {slot, ossId}
 * DELETE /lqg/qc/{sampleId}/{docType}/image/{id}        软删
 * PUT    /lqg/qc/{sampleId}/{docType}/image/sort        {ids:[…]} 该图片位全部 id，按目标顺序
 * POST   /lqg/qc/{sampleId}/{docType}/attachment        {ossId, fileName[, fileSize]}
 * DELETE /lqg/qc/{sampleId}/{docType}/attachment/{id}   软删
 * </pre>
 *
 * <p>★ {@code docType} ∈ {@code sample-qc | organoid-qc | score}（路径里用连字符，
 * 对应字典 {@code sample_qc | organoid_qc | organoid_score}，contract 第 90 行）。
 * <b>评分表没有图片位</b>：{@code /score/image} 一定被拒（400）；挂附件则可以。
 *
 * <p>★ 权限串 {@code lqg:qc:{query,edit,publish}}，与 Flyway 迁移 V202609261300 里
 * 5501-5503 的菜单 perms 逐字一致；101（lqg_admin）与 102（lqg_internal）都授了。
 * {@code lqg:qc:publish} 本票不落端点（完成并同步 / 撤回在 DOC-PUBLISH-001）。
 *
 * <p>★ 本票只做**内部**接口：工作台页面在 QC-WEB-001 / 002、渲染在 DOC-RENDER-001、
 * 发布状态机在 DOC-PUBLISH-001。
 *
 * @author QC-MODEL-001
 */
@RequiredArgsConstructor
@RestController
@RequestMapping("/lqg/qc")
public class QcDocController {

    private final QcDocService qcDocService;

    /**
     * 三份文档（含图片位与附件）+ 样本主档只读字段；首次访问就地建三份空草稿。
     */
    @SaCheckPermission("lqg:qc:query")
    @GetMapping("/{sampleId}")
    public R<QcDocBundleVo> bundle(@PathVariable Long sampleId) {
        return R.ok(qcDocService.bundle(sampleId));
    }

    /**
     * 保存样本质控表（补丁语义）。
     */
    @SaCheckPermission("lqg:qc:edit")
    @PutMapping("/{sampleId}/sample-qc")
    public R<Void> saveSampleQc(@PathVariable Long sampleId, @RequestBody QcSampleSaveBo bo) {
        qcDocService.saveSampleQc(sampleId, bo);
        return R.ok();
    }

    /**
     * 保存类器官质控表（补丁语义）。
     */
    @SaCheckPermission("lqg:qc:edit")
    @PutMapping("/{sampleId}/organoid-qc")
    public R<Void> saveOrganoidQc(@PathVariable Long sampleId, @RequestBody QcOrganoidSaveBo bo) {
        qcDocService.saveOrganoidQc(sampleId, bo);
        return R.ok();
    }

    /**
     * 保存类器官质量评分表（只收四个档位；分值后端按字典 remark 回填）。
     */
    @SaCheckPermission("lqg:qc:edit")
    @PutMapping("/{sampleId}/score")
    public R<Void> saveScore(@PathVariable Long sampleId, @RequestBody QcScoreSaveBo bo) {
        qcDocService.saveScore(sampleId, bo);
        return R.ok();
    }

    /**
     * 往图片位加一张图。
     */
    @SaCheckPermission("lqg:qc:edit")
    @PostMapping("/{sampleId}/{docType}/image")
    public R<Long> addImage(@PathVariable Long sampleId, @PathVariable String docType,
                            @RequestBody DocImageBo bo) {
        return R.ok(qcDocService.addImage(sampleId, docType, bo));
    }

    /**
     * 软删一张图。
     */
    @SaCheckPermission("lqg:qc:edit")
    @DeleteMapping("/{sampleId}/{docType}/image/{id}")
    public R<Void> removeImage(@PathVariable Long sampleId, @PathVariable String docType,
                               @PathVariable Long id) {
        qcDocService.removeImage(sampleId, docType, id);
        return R.ok();
    }

    /**
     * 重排一个图片位内的顺序。
     */
    @SaCheckPermission("lqg:qc:edit")
    @PutMapping("/{sampleId}/{docType}/image/sort")
    public R<Void> sortImages(@PathVariable Long sampleId, @PathVariable String docType,
                              @RequestBody DocImageSortBo bo) {
        qcDocService.sortImages(sampleId, docType, bo);
        return R.ok();
    }

    /**
     * 挂一个通用附件（单个 ≤ 50MB）。
     */
    @SaCheckPermission("lqg:qc:edit")
    @PostMapping("/{sampleId}/{docType}/attachment")
    public R<Long> addAttachment(@PathVariable Long sampleId, @PathVariable String docType,
                                 @RequestBody DocAttachmentBo bo) {
        return R.ok(qcDocService.addAttachment(sampleId, docType, bo));
    }

    /**
     * 软删一个通用附件。
     */
    @SaCheckPermission("lqg:qc:edit")
    @DeleteMapping("/{sampleId}/{docType}/attachment/{id}")
    public R<Void> removeAttachment(@PathVariable Long sampleId, @PathVariable String docType,
                                    @PathVariable Long id) {
        qcDocService.removeAttachment(sampleId, docType, id);
        return R.ok();
    }

}
