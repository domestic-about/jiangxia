package org.dromara.lqg.sys.export;

import cn.dev33.satoken.annotation.SaCheckRole;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.dromara.lqg.cryo.batch.domain.bo.CryoQueryBo;
import org.dromara.lqg.embed.domain.bo.EmbedQueryBo;
import org.dromara.lqg.sample.domain.bo.SampleQueryBo;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 小程序表格页「导出 Excel」的四张工作表
 * （doc/api-contract.md 第 55 行；UI:mp.ledger ④ / FLOW:F-SAMPLE-02.step7）。
 *
 * <pre>
 * GET /mp/int/export/tissue     按当前筛选导出「样本记录信息表」xlsx（14 列）
 * GET /mp/int/export/organoid   按当前筛选导出「类器官收样记录」xlsx（7 列）
 * GET /mp/int/export/embed      按当前筛选导出「石蜡包埋送样记录」xlsx（16 列）
 * GET /mp/int/export/cryo       按当前筛选导出「-80 冻存」xlsx（9 + 2 列）
 *      · 查询参数 = 对应工作表 list 的筛选参数（与 /mp/int/{sample,embed}/list、
 *        /mp/int/cryo/batch/list 同一组字段名）
 *      · 响应 = xlsx 文件流；Content-Disposition 用 RFC 5987 写中文名
 *      · 未知 sheet 路径 → 404 No endpoint（不匹配任何 handler）；外部角色 → 403
 * </pre>
 *
 * <p>★ <b>四张表用四个 handler 而不是一个 {@code {sheet}} 通配</b>：
 * 每张表的筛选参数是**不同的查询对象**（{@link SampleQueryBo} / {@link EmbedQueryBo} /
 * {@link CryoQueryBo}，与各自 list 端点逐字段同名）。用 Spring 的参数绑定收它们，
 * 「筛选参数绑到对应工作表的查询对象」这件事就落在**同一套绑定语义**上
 * （工作台那四个导出端点也是这么收的）；换成通配路径 + 手工拼参数，
 * 就等于在导出里重造一套绑定 —— 与 list 迟早分叉。
 *
 * <p>★★ <b>类级 {@code @SaCheckRole("lqg_internal")}</b>（契约「通用」一节：{@code /mp/int/**}
 * 只给小程序内部人员）：外部角色（{@code lqg_external}）打这四个端点一律 403
 * —— ticket §3「不给外部任何导出」（Accept 1 最后一段真调 {@code --as extA} 断 403）。
 * 管理员同时带 internal 角色，所以 {@code --as staff} 与管理员都进得来。
 * ★ <b>不是</b> {@code @SaCheckPermission("lqg:sample:export")}（工作台那个按钮权限）：
 * 小程序内部人员是角色维度（{@code /mp/int/**} 的一贯口径），绑到工作台按钮权限会让
 * 「菜单没 seed」把小程序整条导出静默打成 403。
 *
 * <p>★ <b>本类不查库、不出流</b>：四张表各自的导出能力全在上游 service
 * （{@link SysExportService} 只做分派），所以「工作台导出的」与「小程序导出的」是同一份文件。
 *
 * @author SYS-EXPORT-001
 */
@RequiredArgsConstructor
@RestController
@RequestMapping(MpExportController.BASE_PATH)
@SaCheckRole("lqg_internal")
public class MpExportController {

    /**
     * 四个导出端点的公共前缀（契约第 55 行的 {@code /mp/int/export/{sheet}}）。
     */
    public static final String BASE_PATH = "/mp/int/export";

    private final SysExportService sysExportService;

    /**
     * 样本记录信息表（tissue 类）：筛选与 {@code GET /mp/int/sample/list?sampleKind=tissue} 同源。
     */
    @GetMapping("/tissue")
    public void tissue(SampleQueryBo query, HttpServletResponse response) {
        sysExportService.exportTissue(query, response);
    }

    /**
     * 类器官收样记录（organoid 类）。
     */
    @GetMapping("/organoid")
    public void organoid(SampleQueryBo query, HttpServletResponse response) {
        sysExportService.exportOrganoid(query, response);
    }

    /**
     * 石蜡包埋送样记录：筛选与 {@code GET /mp/int/embed/list} 同源（含 {@code stain}）。
     */
    @GetMapping("/embed")
    public void embed(EmbedQueryBo query, HttpServletResponse response) {
        sysExportService.exportEmbed(query, response);
    }

    /**
     * -80 冻存：筛选与 {@code GET /mp/int/cryo/batch/list} 同源
     * （{@code overdueOnly} / {@code location} 就是表格页那三个页签）。
     */
    @GetMapping("/cryo")
    public void cryo(CryoQueryBo query, HttpServletResponse response) {
        sysExportService.exportCryo(query, response);
    }

}
