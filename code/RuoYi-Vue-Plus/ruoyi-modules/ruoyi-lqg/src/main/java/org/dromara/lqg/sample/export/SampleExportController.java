package org.dromara.lqg.sample.export;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.dromara.lqg.sample.domain.bo.SampleQueryBo;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 样本两张 Excel 的导出端点（doc/api-contract.md「SAMPLE」一节的
 * {@code POST /lqg/sample/export/tissue} 与 {@code POST /lqg/sample/export/organoid}）。
 *
 * <pre>
 * POST /lqg/sample/export/tissue      按当前筛选导出「样本记录信息表」（14 列）
 * POST /lqg/sample/export/organoid    按当前筛选导出「类器官收样记录」（7 列）
 * </pre>
 *
 * <p>★ <b>筛选走 query / 表单参数</b>（不是 JSON body）：参数与 {@code GET /lqg/sample/list}
 * 同一组（同一个 {@link SampleQueryBo} 绑定）—— 契约第 48 行「按当前筛选导出 xlsx（表单参数同 list）」，
 * 与 {@code api.sh} 的 {@code POST '/lqg/sample/export/tissue?sourceUnitId=…'} 同一形状。
 *
 * <p>★ <b>为什么单独一个 controller 而不是往 {@code SampleController} 里塞两个方法</b>：
 * ticket 的 {@code touches} 只给了 {@code sample/export/**}，把端点与导出视图放在同一个包里
 * 既满足「导出逻辑放 {@code SampleExportService}、别写死在 controller 里」，也不动
 * SAMPLE-MODEL-001 建的 {@code SampleController}（一个字节都没改）。
 *
 * <p>★ 权限串 {@code lqg:sample:export} —— 菜单按钮 <b>5217</b>（{@code parent_id=5210}），
 * 由本票迁移 {@code V202609231000__SAMPLE-EXPORT-001-sample-export-perm.sql} 建，
 * 并授给 101（lqg_admin）与 102（lqg_internal）。
 * <b>外部角色（lqg_external）没有这个权限串 → 403</b>（ticket §3：不给外部任何导出）。
 *
 * <p>★ 小程序表格页的 {@code GET /mp/int/export/tissue|organoid}（SYS-EXPORT-001）复用同一个
 * {@link SampleExportService}，两处文件逐列一致。
 *
 * @author SAMPLE-EXPORT-001
 */
@RequiredArgsConstructor
@RestController
@RequestMapping("/lqg/sample/export")
public class SampleExportController {

    private final SampleExportService sampleExportService;

    /**
     * 按当前筛选导出「样本记录信息表」xlsx（表头 14 列照甲方模板原件逐字同序）。
     */
    @SaCheckPermission("lqg:sample:export")
    @PostMapping("/tissue")
    public void exportTissue(SampleQueryBo query, HttpServletResponse response) {
        sampleExportService.exportTissue(query, response);
    }

    /**
     * 按当前筛选导出「类器官收样记录」xlsx（表头 7 列照甲方模板原件逐字同序）。
     */
    @SaCheckPermission("lqg:sample:export")
    @PostMapping("/organoid")
    public void exportOrganoid(SampleQueryBo query, HttpServletResponse response) {
        sampleExportService.exportOrganoid(query, response);
    }

}
