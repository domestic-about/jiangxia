package org.dromara.lqg.sys.export;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.utils.file.FileUtils;
import org.dromara.lqg.cryo.batch.domain.bo.CryoQueryBo;
import org.dromara.lqg.cryo.export.CryoExportService;
import org.dromara.lqg.embed.domain.bo.EmbedQueryBo;
import org.dromara.lqg.embed.export.EmbedExportService;
import org.dromara.lqg.sample.domain.bo.SampleQueryBo;
import org.dromara.lqg.sample.export.SampleExportService;
import org.springframework.stereotype.Service;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Set;

/**
 * 小程序表格页「导出 Excel」的编排（FLOW:F-SAMPLE-02.step7 / REQ-SYS-017）。
 *
 * <pre>
 * GET /mp/int/export/tissue      → SampleExportService#exportTissue   （14 列）
 * GET /mp/int/export/organoid    → SampleExportService#exportOrganoid （7 列）
 * GET /mp/int/export/embed       → EmbedExportService#export          （16 列）
 * GET /mp/int/export/cryo        → CryoExportService#export           （9 + 2 列）
 * </pre>
 *
 * <p>★★ <b>本类只做「按 sheet 分派 + 出流 + 中文文件名」三件事</b>（ticket §2）：
 * 列序 / 表头 / 单元格格式化<b>一个字节都不在本包里</b>——四张表的导出能力上游都已具备，
 * 这里再写一份就是两份真相源（Accept 1 counterfeit 第一条）。
 *
 * <p>★ <b>筛选参数绑定到「对应工作表 list 的查询对象」</b>：
 * 由 controller 的四个 handler 各自收 {@link SampleQueryBo} / {@link EmbedQueryBo} /
 * {@link CryoQueryBo}（与 {@code GET /lqg/sample/list}、{@code GET /mp/int/sample/list} 同一个 BO），
 * 本类只把它原样交给上游 service —— 「用户筛了什么就导出什么」靠的是上游那条唯一的读路径
 * （{@code exportRows} 与列表同一份 wrapper），不是在导出里另拼 WHERE。
 *
 * <p>★ <b>响应 = xlsx 文件流</b>（ticket §3：<b>不</b>把导出文件存进 OSS，直接流式返回）。
 * 文件名的中文用 RFC 5987 落进 {@code Content-Disposition}：
 * <pre>
 * attachment; filename=export.xlsx;filename*=utf-8''%E6%A0%B7%E6%9C%AC...
 * </pre>
 * 取整时刻 {@code yyyyMMddHHmmss}（契约第 55 行的例子是 {@code 样本记录信息表-20260918.xlsx}，
 * 那一格是「哪一天导的」；本实现把时间也带上，用户一天里导两次不会互相覆盖文件名）。
 * <b>文件名不受 {@code ExcelUtil} 影响</b>：上游 {@code ExcelUtil.exportExcel} 内部会把
 * {@code Content-Disposition} 改写成随机 UUID 前缀的 ASCII 串，所以这里用一个
 * {@link ContentDispositionResponse} 拦住它的那一次改写（见该内部类的注释）。
 *
 * @author SYS-EXPORT-001
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysExportService {

    /**
     * 文件名里的时刻格式（本地时区；同一天多次导出不重名）。
     */
    static final String FILE_TIME_PATTERN = "yyyyMMddHHmmss";

    /**
     * 没有中文名时的 ASCII 兜底名（RFC 6266 的 {@code filename=} 只允许 ASCII）。
     */
    static final String ASCII_FALLBACK_NAME = "export.xlsx";

    /**
     * xlsx 的 MIME（与 {@code ExcelUtil.resetResponse} 写的逐字相同）。
     */
    static final String XLSX_CONTENT_TYPE =
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet;charset=UTF-8";

    /**
     * 上游 {@code ExcelUtil} 会写的响应头（本类用自己的文件名覆盖 Content-Disposition）。
     */
    static final Set<String> UPSTREAM_HEADERS = Set.of("Content-Disposition", "download-filename");

    private final SampleExportService sampleExportService;

    private final EmbedExportService embedExportService;

    private final CryoExportService cryoExportService;

    /**
     * 样本记录信息表（tissue 类）—— 与 {@code POST /lqg/sample/export/tissue} 同一份视图。
     */
    public void exportTissue(SampleQueryBo query, HttpServletResponse response) {
        sampleExportService.exportTissue(query, wrap(ExcelSheet.TISSUE, response));
    }

    /**
     * 类器官收样记录（organoid 类）—— 与 {@code POST /lqg/sample/export/organoid} 同一份视图。
     */
    public void exportOrganoid(SampleQueryBo query, HttpServletResponse response) {
        sampleExportService.exportOrganoid(query, wrap(ExcelSheet.ORGANOID, response));
    }

    /**
     * 石蜡包埋送样记录 —— 与 {@code POST /lqg/embed/export} 同一份视图。
     */
    public void exportEmbed(EmbedQueryBo query, HttpServletResponse response) {
        log.info("小程序导出石蜡包埋送样记录（筛选 internalNo={} verifyStatus={} stain={}）",
            query == null ? null : query.getInternalNo(),
            query == null ? null : query.getVerifyStatus(),
            query == null ? null : query.getStain());
        embedExportService.export(query, wrap(ExcelSheet.EMBED, response));
    }

    /**
     * -80 冻存 —— 与 {@code POST /lqg/cryo/batch/export} 同一份视图。
     */
    public void exportCryo(CryoQueryBo query, HttpServletResponse response) {
        log.info("小程序导出 -80 冻存（筛选 internalNo={} location={} overdueOnly={}）",
            query == null ? null : query.getInternalNo(),
            query == null ? null : query.getLocation(),
            query == null ? null : query.getOverdueOnly());
        cryoExportService.export(query, wrap(ExcelSheet.CRYO, response));
    }

    // ── 文件名（纯函数；一处判据，单测直接断）────────────────────────────────

    /**
     * 「下载下来的这个文件叫什么」：{@code <工作表名>-<yyyyMMddHHmmss>.xlsx}。
     *
     * @param sheet 工作表（决定主干，与上游 service 的 {@code SHEET_NAME} 同源）
     * @param at    冻结的时刻（单测传死值；生产传 {@code new Date()}）
     */
    public static String fileNameOf(ExcelSheet sheet, Date at) {
        String stamp = new SimpleDateFormat(FILE_TIME_PATTERN).format(at == null ? new Date() : at);
        return sheet.sheetName() + "-" + stamp + ".xlsx";
    }

    /**
     * RFC 5987 的 {@code Content-Disposition} 值。
     *
     * <p>中文名必须同时给两段：{@code filename=} 是 ASCII 兜底（老客户端），
     * {@code filename*=utf-8''<百分号编码>} 才是真正要用的那一段（ticket §2 明文要求 RFC 5987）。
     * 百分号编码复用若依既有的 {@link FileUtils#percentEncode(String)}（UTF-8；空格转 {@code %20}）。
     */
    public static String contentDispositionOf(String fileName) {
        String encoded = FileUtils.percentEncode(fileName);
        return "attachment; filename=" + ASCII_FALLBACK_NAME + ";filename*=utf-8''" + encoded;
    }

    /**
     * 把「本次导出自己的 Content-Disposition」钉在响应上，并拦住上游
     * {@code ExcelUtil.exportExcel} 对它的改写。
     */
    private static HttpServletResponse wrap(ExcelSheet sheet, HttpServletResponse response) {
        String disposition = contentDispositionOf(fileNameOf(sheet, new Date()));
        if (!response.isCommitted()) {
            response.setContentType(XLSX_CONTENT_TYPE);
            response.setHeader("Content-Disposition", disposition);
            response.setHeader("Access-Control-Expose-Headers", "Content-Disposition,download-filename");
        }
        return new ContentDispositionResponse(response, disposition);
    }

    /**
     * 只拦 {@code Content-Disposition} / {@code download-filename} 两个头的响应包装。
     *
     * <p>★★ <b>为什么必须有这一层</b>：{@code ExcelUtil.exportExcel(rows, sheetName, clazz, response)}
     * 内部第一步就是 {@code resetResponse} → {@code FileUtils.setAttachmentResponseHeader(response,
     * <随机 UUID>_<sheetName>.xlsx)}，它会 {@code setHeader("Content-disposition", …)} 覆盖掉本类
     * 写好的中文名（ticket §2 要求 {@code Content-Disposition} 用 RFC 5987 写中文文件名，
     * 如 {@code 样本记录信息表-20260918.xlsx}）。
     *
     * <p>★ <b>不改上游 service</b>（{@code touches} 里没有它们，也别在别域改口径）：
     * 上游的 SQL 走真实 response 的 {@code getOutputStream()}（委托给被包装的那个），
     * 只有那两个头被忽略 —— 于是「表头 / 列序 / 单元格」全部照旧来自上游导出视图，
     * 文件名由本包说了算。
     */
    static final class ContentDispositionResponse extends HttpServletResponseWrapper {

        private final String disposition;

        ContentDispositionResponse(HttpServletResponse response, String disposition) {
            super(response);
            this.disposition = disposition;
        }

        @Override
        public void setHeader(String name, String value) {
            if (!isUpstreamHeader(name)) {
                super.setHeader(name, value);
            }
        }

        @Override
        public void addHeader(String name, String value) {
            if (!isUpstreamHeader(name)) {
                super.addHeader(name, value);
            }
        }

        private static boolean isUpstreamHeader(String name) {
            if (name == null) {
                return false;
            }
            for (String blocked : UPSTREAM_HEADERS) {
                if (blocked.equalsIgnoreCase(name)) {
                    return true;
                }
            }
            return false;
        }
    }

}
