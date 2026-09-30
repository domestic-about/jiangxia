package org.dromara.lqg.cryo.export;

import org.dromara.lqg.sys.export.LqgExcel;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.lqg.cryo.batch.domain.bo.CryoQueryBo;
import org.dromara.lqg.cryo.batch.domain.vo.CryoBatchVo;
import org.dromara.lqg.cryo.batch.service.CryoQueryService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 「-80 冻存」导出（FLOW:F-CRYO-01.step5，{@code POST /lqg/cryo/batch/export}）。
 *
 * <p>★★ <b>导出视图放在 service 层</b>：小程序表格页的 {@code GET /mp/int/export/{sheet}}
 * （sheet=cryo，SYS-EXPORT-001）直接调本类，两处文件逐列一致
 * （{@code doc/api-contract.md} 第 55 行：「表头列序与 … {@code /lqg/cryo/batch/export}
 * 同一个导出视图」）—— 各写一份 Excel 拼装迟早会漂。
 *
 * <p>★ <b>数据源 = {@link CryoQueryService#exportRows}</b>：与列表<b>同一份 wrapper + 同一份装配</b>
 * （同一批筛选：内部编号、冻存样品、位置、只看超期、冻存时间区间、样本 id）
 * —— 「带筛选导出只出筛选结果」这一条靠它，而不是在导出里重写一遍 WHERE。
 *
 * <p>★ <b>行数</b> = 未删批次数 <b>且所挂样本未删</b>（后者由 {@code exportRows} 剔除）。
 *
 * <p>★ <b>格式钉死</b>（accept 1 逐格断的）：
 * <ul>
 *   <li>「冻存数量/支」= <b>{@code initQty}（初始支数）</b>，<b>不是</b>剩余
 *       —— 甲方拿导出去对纸质记录，对的是当初冻了几支（accept 1 counterfeit 第一条）；</li>
 *   <li>「暂存-80度超低温冰箱」= <b>是 / 否</b>（模板上这一格写的是「是 否（按钮）」，
 *       导 {@code Y}/{@code N} 就对不上）；</li>
 *   <li>「当前剩余/支」= {@code remainingQty}（读时算，与接口 / 库内独立汇总同源）；</li>
 *   <li>日期（冻存时间 / 转液氮时间）= {@code yyyy-MM-dd}，空 → 空格子。</li>
 * </ul>
 * ★ 列序 = 模板 9 列 + 追加「代数」「当前剩余/支」两列；追加列不插到中间（表头逐字按序比对）。
 *
 * @author CRYO-WEB-001
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CryoExportService {

    /**
     * 导出文件 / 工作表名（{@code Content-Disposition} 里的中文文件名）。
     */
    public static final String SHEET_NAME = "-80冻存";

    /**
     * 「暂存-80度超低温冰箱」这一格的「是」。
     */
    static final String YES = "是";

    /**
     * 「暂存-80度超低温冰箱」这一格的「否」。
     */
    static final String NO = "否";

    private final CryoQueryService cryoQueryService;

    /**
     * 按当前筛选导出 xlsx（{@code POST /lqg/cryo/batch/export}）。
     *
     * @param query    与 {@code GET /lqg/cryo/batch/list} 同一组筛选参数（走 query 参数，不是 JSON body）
     * @param response 响应体（文件流）
     */
    public void export(CryoQueryBo query, HttpServletResponse response) {
        List<CryoExportVo> rows = rowsOf(query);
        log.info("导出 -80 冻存：{} 行（筛选 internalNo={} cryoName={} location={} overdueOnly={}）",
            rows.size(), query == null ? null : query.getInternalNo(),
            query == null ? null : query.getCryoName(),
            query == null ? null : query.getLocation(),
            query == null ? null : query.getOverdueOnly());
        LqgExcel.export(rows, SHEET_NAME, CryoExportVo.class, response);
    }

    /**
     * 导出行（纯数据，给复跑 / 单测直接断格式用，不碰 {@code HttpServletResponse}）。
     */
    public List<CryoExportVo> rowsOf(CryoQueryBo query) {
        List<CryoBatchVo> source = cryoQueryService.exportRows(query);
        List<CryoExportVo> out = new ArrayList<>(source.size());
        for (CryoBatchVo row : source) {
            out.add(toExportVo(row));
        }
        return out;
    }

    /**
     * 一行读模型 → 一行导出视图（纯映射，不碰库；单测直接拿它断「初始 vs 剩余」那一格）。
     */
    static CryoExportVo toExportVo(CryoBatchVo row) {
        CryoExportVo vo = new CryoExportVo();
        vo.setFreezeTime(text(row.getFreezeTime()));
        vo.setCryoName(row.getCryoName());
        // ★ 初始支数，不是剩余（accept 1 counterfeit 第一条）
        vo.setInitQty(numberText(row.getInitQty()));
        vo.setDensity(row.getDensity());
        vo.setInMinus80(flagText(row.getInMinus80()));
        vo.setFrozenBy(row.getFrozenBy());
        vo.setToLn2Time(text(row.getToLn2Time()));
        vo.setLn2Location(row.getLn2Location());
        vo.setRemark(row.getRemark());
        vo.setPassage(row.getPassage());
        vo.setRemainingQty(numberText(row.getRemainingQty()));
        return vo;
    }

    // ── 单元格格式（纯函数；一个格子一个判据，全在这个文件里） ────────────────

    /**
     * {@code in_minus80} 的 Y / N → <b>是 / 否</b>（模板原件就是这么写的）。
     *
     * <p>其它值（空 / 历史脏值）原样带出，不做业务校验（读侧不造数据）。
     */
    static String flagText(String inMinus80) {
        if (inMinus80 == null) {
            return null;
        }
        String value = inMinus80.trim();
        if ("Y".equalsIgnoreCase(value)) {
            return YES;
        }
        if ("N".equalsIgnoreCase(value)) {
            return NO;
        }
        return value.isEmpty() ? null : value;
    }

    /**
     * 日期 → {@code yyyy-MM-dd}；空 → {@code null}（空格子，不是 {@code "null"} 也不是空串）。
     */
    static String text(Object date) {
        return date == null ? null : date.toString();
    }

    /**
     * 整数 → 文本格子（{@code 8} 而不是 {@code 8.0}）；空 → {@code null}（空格子）。
     *
     * <p>★ 导出视图的这两个计数列是 {@code String} 而不是 {@code Integer}：写成数值型时
     * FastExcel 落 {@code <v>8.0</v>}，对账脚本 / 甲方工具读回来都是 {@code 8.0}，
     * 而模板要的是 {@code 8}（见 {@link CryoExportVo#initQty}）。
     * ★ 取空那一档是 {@code "0"}、不是空格子 —— 「这一批取完了」与「没这一格」不是一回事。
     */
    static String numberText(Integer value) {
        return value == null ? null : String.valueOf(value.intValue());
    }

    /**
     * 导出的列名清单（11 列 = 模板 9 列 + 代数 + 当前剩余/支）—— 给验收脚本 / 单测对表头用。
     */
    public static Map<String, Integer> headerIndex() {
        Map<String, Integer> out = new LinkedHashMap<>();
        out.put("冻存时间", 0);
        out.put("冻存样品", 1);
        out.put("冻存数量/支", 2);
        out.put("冻存密度", 3);
        out.put("暂存-80度超低温冰箱", 4);
        out.put("冻存人", 5);
        out.put("-80度超低温冰箱转移至液氮时间", 6);
        out.put("液氮储存位置", 7);
        out.put("备注", 8);
        out.put("代数", 9);
        out.put("当前剩余/支", 10);
        return out;
    }

}
