package org.dromara.lqg.embed.export;

import org.dromara.lqg.sys.export.LqgExcel;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.lqg.embed.domain.bo.EmbedQueryBo;
import org.dromara.lqg.embed.domain.vo.EmbedMarkerVo;
import org.dromara.lqg.embed.domain.vo.EmbedVo;
import org.dromara.lqg.embed.service.EmbedDictService;
import org.dromara.lqg.embed.service.EmbedQueryService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 「石蜡包埋送样记录」导出（FLOW:F-EMBED-01.step5，{@code POST /lqg/embed/export}）。
 *
 * <p>★★ <b>导出视图放在 service 层</b>（ticket §2）：小程序表格页的
 * {@code GET /mp/int/export/{sheet}}（sheet=embed，SYS-EXPORT-001）直接调本类，
 * 两处文件逐列一致 —— 各写一份 CSV/Excel 拼装迟早会漂。
 *
 * <p>★ <b>数据源 = {@link EmbedQueryService#exportRows}</b>：与列表<b>同一份 wrapper</b>
 * （同一批筛选：石蜡块编号、内部编号、染色、切片时间区间、核验状态、内 / 外部、样本 id）
 * —— 「带筛选导出只出筛选结果」这一条靠它，而不是在导出里重写一遍 WHERE。
 *
 * <p>★ <b>行数</b> = 未删的石蜡包埋送样记录数 <b>且所挂样本未删</b>（含待核验 / 无效的外部送样）：
 * 后者由 {@code exportRows} 剔除。待核验的外部送样也导（石蜡块编号、样本编号两格为空）——
 * ticket §0 口径 1，与 SAMPLE-EXPORT-001 同一口径。
 *
 * <p>★ <b>格式钉死</b>（accept 1 逐格断的）：
 * <ul>
 *   <li>染色：中文标签、顿号连接 → {@code HE染色、IHC染色}；{@code NONE} → {@code 无染色}；
 *       含 {@code OTHER} → {@code 其他（Masson）}（全角括号，具体名称取 {@code stainOther}）；</li>
 *   <li>mark 的表达情况：{@code 名称：表达}（<b>全角冒号</b>）用<b>中文分号</b>连接
 *       → {@code Ki67：强表达；CK19：阴性}；没名称的只写表达；</li>
 *   <li>日期：{@code yyyy-MM-dd}（值是 {@code LocalDate}，原样 toString）；空 → 空格子；</li>
 *   <li>样本编号列 = {@code EmbedVo.internalNo} = 所挂样本的内部编号（读时带出，本表没有这一列）。</li>
 * </ul>
 * ★ 绝不用 {@code ExcelDictConvert} 在表头层做转换：那样染色会变成整串 value
 * （{@code HE,IHC}）、「其他」也带不出具体名称，而且导出视图就没法被小程序复用了。
 *
 * @author EMBED-WEB-001
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmbedExportService {

    /**
     * 导出文件 / 工作表名（{@code Content-Disposition} 里的中文文件名）。
     */
    public static final String SHEET_NAME = "石蜡包埋送样记录";

    /**
     * 多值之间的顿号（染色）。
     */
    static final String STAIN_SEPARATOR = "、";

    /**
     * marker 名称与表达之间的全角冒号。
     */
    static final String MARKER_COLON = "：";

    /**
     * marker 之间的中文分号。
     */
    static final String MARKER_SEPARATOR = "；";

    private final EmbedQueryService embedQueryService;
    private final EmbedDictService embedDictService;

    /**
     * 按当前筛选导出 xlsx（{@code POST /lqg/embed/export}）。
     *
     * @param query    与 {@code GET /lqg/embed/list} 同一组筛选参数
     * @param response 响应体（文件流）
     */
    public void export(EmbedQueryBo query, HttpServletResponse response) {
        List<EmbedExportVo> rows = rowsOf(query);
        log.info("导出石蜡包埋送样记录：{} 行（筛选 internalNo={} verifyStatus={} stain={}）",
            rows.size(), query == null ? null : query.getInternalNo(),
            query == null ? null : query.getVerifyStatus(), query == null ? null : query.getStain());
        LqgExcel.export(rows, SHEET_NAME, EmbedExportVo.class, response);
    }

    /**
     * 导出行（纯数据，给复跑 / 单测直接断格式用，不碰 {@code HttpServletResponse}）。
     */
    public List<EmbedExportVo> rowsOf(EmbedQueryBo query) {
        List<EmbedVo> source = embedQueryService.exportRows(query);
        Map<String, String> stainLabels = embedDictService.labels(EmbedDictService.DICT_STAIN);
        Map<String, String> exprLabels = embedDictService.labels(EmbedDictService.DICT_MARKER_EXPR);
        List<EmbedExportVo> out = new ArrayList<>(source.size());
        for (EmbedVo row : source) {
            EmbedExportVo vo = new EmbedExportVo();
            vo.setParaffinBlockNo(row.getParaffinBlockNo());
            vo.setInternalNo(row.getInternalNo());
            vo.setSampleType(row.getSampleType());
            vo.setOrganoidSourceType(row.getOrganoidSourceType());
            vo.setTissueReceiveTime(text(row.getTissueReceiveTime()));
            vo.setTissueProcessTime(text(row.getTissueProcessTime()));
            vo.setAgaroseEmbedTime(text(row.getAgaroseEmbedTime()));
            vo.setEmbedBy(row.getEmbedBy());
            vo.setDehydrateTime(text(row.getDehydrateTime()));
            vo.setAgaroseSendTime(text(row.getAgaroseSendTime()));
            vo.setParaffinEmbedTime(text(row.getParaffinEmbedTime()));
            vo.setSectionTime(text(row.getSectionTime()));
            vo.setStain(stainText(row.getStainTypes(), row.getStainOther(), stainLabels));
            vo.setMarkerExpression(markerText(row.getMarkers(), exprLabels));
            vo.setOperatorName(row.getOperatorName());
            vo.setRemark(row.getRemark());
            out.add(vo);
        }
        return out;
    }

    // ── 单元格格式（纯函数；一个格子一个判据，全在这个文件里） ────────────────

    /**
     * 染色一格。
     *
     * <pre>
     * ["HE","IHC"]            → HE染色、IHC染色
     * ["OTHER"] + "Masson"    → 其他（Masson）
     * ["NONE"]                → 无染色
     * []（还没选）             → null（空格子）
     * </pre>
     *
     * @param stainTypes 染色 value 数组（{@code EmbedVo.stainTypes}，已按固定顺序）
     * @param stainOther 选了 OTHER 时的具体名称
     * @param labels     {@code lqg_stain_type} 的 value → label
     */
    static String stainText(List<String> stainTypes, String stainOther, Map<String, String> labels) {
        if (stainTypes == null || stainTypes.isEmpty()) {
            return null;
        }
        Map<String, String> dict = labels == null ? Map.of() : labels;
        List<String> parts = new ArrayList<>(stainTypes.size());
        for (String value : stainTypes) {
            if (StringUtils.isBlank(value)) {
                continue;
            }
            String trimmed = value.trim();
            if ("OTHER".equals(trimmed)) {
                // 「其他」要带出具体名称；没写名称（历史脏数据）就只落标签，不落成 null 把整格吃掉
                String label = dict.getOrDefault("OTHER", "其他");
                parts.add(StringUtils.isNotBlank(stainOther) ? label + "（" + stainOther.trim() + "）" : label);
            } else {
                // 字典里没有的历史值原样带出 value（读侧不做业务校验）
                parts.add(dict.getOrDefault(trimmed, trimmed));
            }
        }
        return parts.isEmpty() ? null : String.join(STAIN_SEPARATOR, parts);
    }

    /**
     * 「mark的表达情况」一格：{@code Ki67：强表达；CK19：阴性}。
     *
     * <p>没有名称的行只写表达（seed 的 2004 就是无名 marker，期望格 = {@code 弱表达}）；
     * 名称与表达都空的整行跳过；一行都没有 → null（空格子）。
     */
    static String markerText(List<EmbedMarkerVo> markers, Map<String, String> labels) {
        if (markers == null || markers.isEmpty()) {
            return null;
        }
        Map<String, String> dict = labels == null ? Map.of() : labels;
        List<String> parts = new ArrayList<>(markers.size());
        for (EmbedMarkerVo marker : markers) {
            if (marker == null) {
                continue;
            }
            String name = StringUtils.isBlank(marker.getMarkerName()) ? null : marker.getMarkerName().trim();
            String expression = expressionText(marker.getExpression(), dict);
            if (name == null && expression == null) {
                continue;
            }
            if (name == null) {
                parts.add(expression);
            } else if (expression == null) {
                parts.add(name);
            } else {
                parts.add(name + MARKER_COLON + expression);
            }
        }
        return parts.isEmpty() ? null : String.join(MARKER_SEPARATOR, parts);
    }

    /**
     * marker 表达的中文标签（{@code strong → 强表达}）；字典外的历史值原样带出。
     */
    static String expressionText(String expression, Map<String, String> labels) {
        if (StringUtils.isBlank(expression)) {
            return null;
        }
        String trimmed = expression.trim();
        return labels.getOrDefault(trimmed, trimmed);
    }

    /**
     * 日期 → {@code yyyy-MM-dd}；空 → {@code null}（空格子，不是 {@code "null"} 也不是空串）。
     *
     * <p>模板里的六个时间列在甲方原件里就是日期（没有时分秒），落库也是 {@code LocalDate}。
     */
    static String text(Object date) {
        return date == null ? null : date.toString();
    }

    /**
     * 导出的列名清单（{@code 16} 列，与模板逐字同序）—— 给验收脚本 / 单测对表头用。
     */
    public static Map<String, Integer> headerIndex() {
        Map<String, Integer> out = new LinkedHashMap<>();
        out.put("石蜡块编号", 0);
        out.put("样本编号", 1);
        out.put("样本类型", 2);
        out.put("类器官来源类型", 3);
        out.put("组织收样时间", 4);
        out.put("组织处理时间", 5);
        out.put("琼脂糖包埋样本时间", 6);
        out.put("包埋人", 7);
        out.put("脱水时间", 8);
        out.put("琼脂糖包埋样本送样时间", 9);
        out.put("石蜡包埋时间", 10);
        out.put("切片时间", 11);
        out.put("染色", 12);
        out.put("mark的表达情况", 13);
        out.put("操作人", 14);
        out.put("备注", 15);
        return out;
    }

}
