package org.dromara.lqg.sample.export;

import org.dromara.lqg.sys.export.LqgExcel;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.lqg.sample.domain.bo.SampleQueryBo;
import org.dromara.lqg.sample.domain.vo.SampleVo;
import org.dromara.lqg.sample.service.SampleQueryService;
import org.springframework.stereotype.Service;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 样本两张 Excel 的导出（FLOW:F-SAMPLE-02.step5）：
 * {@code POST /lqg/sample/export/tissue} 与 {@code POST /lqg/sample/export/organoid}。
 *
 * <p>★★ <b>导出视图放在这一层</b>（ticket §2：「导出逻辑放 {@code SampleExportService}
 * （VO + 查询），别写死在 controller 里」）：小程序表格页的 {@code GET /mp/int/export/{sheet}}
 * （sheet=tissue|organoid，SYS-EXPORT-001）直接调 {@link #tissueRowsOf} / {@link #organoidRowsOf}
 * —— 两处导出的文件<b>逐列一致</b>；各写一份拼装迟早会漂。
 *
 * <p>★ <b>数据源 = {@link SampleQueryService#exportRows}</b>：与 {@code GET /lqg/sample/list}
 * <b>同一份 wrapper、同一份装配</b>（同一批筛选：来源单位 / 组别 / 类别 / 提交来源 / 核验状态 /
 * 收样日期区间 / 组织类型 / 内部编号 / 操作人 / 供体姓名（精确）/ 住院号（精确）/ keyword）。
 * 「带筛选导出只出筛选结果」「导出行数与列表 total 一致」这两条口径只能靠它，
 * <b>在导出里另写一遍 WHERE 就是 accept 1 counterfeit 点名的形态</b>。
 *
 * <p>★ <b>范围</b>：{@code del_flag='0'}（实体 {@code @TableLogic} 兜住，软删的 1010 不出现）
 * 且 {@code sample_kind} = 本端点那一类。<b>待核验 / 无效的样本也导</b>
 * （内部编号、有无固定等还没填的格子留空）—— ticket §2。
 *
 * <p>★ <b>两张表是同一张 {@code t_lqg_sample} 的两个视图</b>（ADR-0010）：tissue 14 列、
 * organoid 8 列（模板 7 列 + 插入的「代数」，CR-20260924-10），列集不同（见两个导出 VO）。
 *
 * <p>★ <b>格式钉死</b>（accept 1 逐格断的）：
 * <ul>
 *   <li>三个「有无」按钮列 + 细胞活率报告：{@code Y→有 / N→无}，没选（null）留空
 *       —— <b>不是 Y / N</b>（字典 {@code lqg_has_none}）；</li>
 *   <li>性别：{@code male→男 / female→女 / unknown→未知} —— <b>不是 male / female</b>
 *       （字典 {@code lqg_gender}）；</li>
 *   <li>★ 供体姓名 / 住院号：<b>明文</b>（ADR-0006：工作台内部人员看明文）。
 *       库里是裸 Base64 密文（{@code SampleFieldCipher}），本类<b>只消费
 *       {@link SampleQueryService#exportRows} 已经解过密的 {@code SampleVo}</b>
 *       —— 绝不自己查 Map / 原生 SQL，那样导出的是密文；</li>
 *   <li>收样日期 {@code yyyy-MM-dd}、处理时间 {@code yyyy-MM-dd HH:mm:ss}
 *       （与 {@code SampleVo} 的 {@code @JsonFormat} 同格式）；空 → 空格子。</li>
 * </ul>
 * ★ 绝不用 {@code ExcelDictConvert} 在表头层做转换：那样「有无固定」会变成 {@code Y}、
 * 性别会变成 {@code male}，而且导出视图就没法被小程序复用了。
 *
 * @author SAMPLE-EXPORT-001
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SampleExportService {

    /**
     * tissue 工作表 / 下载文件名。
     */
    public static final String TISSUE_SHEET_NAME = "样本记录信息表";

    /**
     * organoid 工作表 / 下载文件名。
     */
    public static final String ORGANOID_SHEET_NAME = "类器官送样记录";

    /**
     * 样本类别（{@code t_lqg_sample.sample_kind}，字典 {@code lqg_sample_kind}）。
     */
    public static final String KIND_TISSUE = "tissue";

    /**
     * 类器官（同上）。
     */
    public static final String KIND_ORGANOID = "organoid";

    /**
     * 日期格格式（与 {@code SampleVo.receiveDate} 的 {@code @JsonFormat} 一致）。
     */
    static final String DATE_PATTERN = "yyyy-MM-dd";

    /**
     * 时间格格式（与 {@code SampleVo.processTime} / {@code application.yml} 的
     * {@code spring.jackson.date-format} 一致）。
     */
    static final String DATE_TIME_PATTERN = "yyyy-MM-dd HH:mm:ss";

    /**
     * 「有无」按钮列的字典（{@code lqg_has_none}：{@code Y→有 / N→无}）。
     *
     * <p>取 {@code doc/authority/field-ssot.yaml} 的字典权威值，不查库：这三个格子是
     * accept 逐字钉死的输出，写死在这里才能被单测在不起 Spring 的情况下钉住。
     */
    private static final Map<String, String> FLAG_LABELS = Map.of("Y", "有", "N", "无");

    /**
     * 性别的字典（{@code lqg_gender}：{@code male→男 / female→女 / unknown→未知}）。
     */
    private static final Map<String, String> GENDER_LABELS = Map.of("male", "男", "female", "女", "unknown", "未知");

    /**
     * 「类器官收样记录」的<b>插入列</b>：列名 → 插在哪一列后面（CR-20260924-10）。
     *
     * <p>★ 列名、列序的唯一来源是甲方模板原件第 1 行；「代数」是甲方 2026-09-24 测试问题记录表第 18 行
     * <b>自己要求加的</b>、模板原件里没有 —— 所以不去改「模板列」，而是单独记一条插入规则，
     * 表头 = 模板列在指定列后插入这些列（与 {@code doc/verify/fixtures/ledger-columns-cases.json}
     * 的 {@code inserted}、{@code doc/verify/xlsx_header.py --insert} 同一条规则）。
     */
    public static final Map<String, String> ORGANOID_INSERTED_AFTER = Map.of("代数", "类器官类型");

    private final SampleQueryService sampleQueryService;

    /**
     * 按当前筛选导出「样本记录信息表」xlsx（{@code POST /lqg/sample/export/tissue}）。
     *
     * @param query    与 {@code GET /lqg/sample/list} 同一组筛选参数（表单 / query 参数绑定）
     * @param response 响应体（文件流）
     */
    public void exportTissue(SampleQueryBo query, HttpServletResponse response) {
        List<SampleTissueExportVo> rows = tissueRowsOf(query);
        log.info("导出样本记录信息表：{} 行（sample_kind=tissue，筛选 sourceUnitId={} verifyStatus={}）",
            rows.size(), query == null ? null : query.getSourceUnitId(),
            query == null ? null : query.getVerifyStatus());
        LqgExcel.export(rows, TISSUE_SHEET_NAME, SampleTissueExportVo.class, response);
    }

    /**
     * 按当前筛选导出「类器官收样记录」xlsx（{@code POST /lqg/sample/export/organoid}）。
     */
    public void exportOrganoid(SampleQueryBo query, HttpServletResponse response) {
        List<SampleOrganoidExportVo> rows = organoidRowsOf(query);
        log.info("导出类器官送样记录：{} 行（sample_kind=organoid，筛选 sourceUnitId={} verifyStatus={}）",
            rows.size(), query == null ? null : query.getSourceUnitId(),
            query == null ? null : query.getVerifyStatus());
        LqgExcel.export(rows, ORGANOID_SHEET_NAME, SampleOrganoidExportVo.class, response);
    }

    /**
     * 「样本记录信息表」的导出数据（纯数据，不碰 {@code HttpServletResponse}）——
     * 给复跑 / 单测直接断格式，也给 {@code /mp/int/export/tissue}（SYS-EXPORT-001）复用。
     */
    public List<SampleTissueExportVo> tissueRowsOf(SampleQueryBo query) {
        List<SampleVo> source = sampleQueryService.exportRows(forceKind(query, KIND_TISSUE));
        List<SampleTissueExportVo> out = new ArrayList<>(source.size());
        for (SampleVo row : source) {
            SampleTissueExportVo vo = new SampleTissueExportVo();
            vo.setSourceUnitName(row.getSourceUnitName());
            vo.setDonorName(row.getDonorName());
            vo.setGender(genderText(row.getGender()));
            vo.setAge(row.getAge());
            vo.setHospitalNo(row.getHospitalNo());
            vo.setTissueType(row.getTissueType());
            vo.setReceiveDate(dateText(row.getReceiveDate()));
            vo.setInternalNo(row.getInternalNo());
            vo.setIsFixed(flagText(row.getIsFixed()));
            vo.setProcessTime(dateTimeText(row.getProcessTime()));
            vo.setHasQcSheet(flagText(row.getHasQcSheet()));
            vo.setHasViabilityReport(flagText(row.getHasViabilityReport()));
            vo.setOperatorName(row.getOperatorName());
            vo.setRemark(row.getRemark());
            out.add(vo);
        }
        return out;
    }

    /**
     * 「类器官收样记录」的导出数据（同上）。
     */
    public List<SampleOrganoidExportVo> organoidRowsOf(SampleQueryBo query) {
        List<SampleVo> source = sampleQueryService.exportRows(forceKind(query, KIND_ORGANOID));
        List<SampleOrganoidExportVo> out = new ArrayList<>(source.size());
        for (SampleVo row : source) {
            SampleOrganoidExportVo vo = new SampleOrganoidExportVo();
            vo.setSourceUnitName(row.getSourceUnitName());
            vo.setOrganoidType(row.getOrganoidType());
            vo.setPassage(row.getPassage());
            vo.setReceiveDate(dateText(row.getReceiveDate()));
            vo.setInternalNo(row.getInternalNo());
            vo.setProcessTime(dateTimeText(row.getProcessTime()));
            vo.setHasViabilityReport(flagText(row.getHasViabilityReport()));
            vo.setOperatorName(row.getOperatorName());
            out.add(vo);
        }
        return out;
    }

    /**
     * ★ <b>类别由端点决定，不由调用方决定</b>：{@code /export/tissue} 恒导 tissue 类、
     * {@code /export/organoid} 恒导 organoid 类 —— 调用方传的 {@code sampleKind} 一律覆盖。
     *
     * <p>否则「两列视图同一张表」这件事就守不住：{@code /export/organoid?sampleKind=tissue}
     * 会用 8 列的表头导组织样本。<b>也保证不带参数时行数 = 该类的未删总数</b>
     * （accept 1：tissue 全量 8 行 = {@code del_flag='0' AND sample_kind='tissue'}）。
     */
    private static SampleQueryBo forceKind(SampleQueryBo query, String kind) {
        SampleQueryBo q = query == null ? new SampleQueryBo() : query;
        q.setSampleKind(kind);
        return q;
    }

    // ── 单元格格式（纯函数；一个格子一个判据，全在这个文件里） ────────────────

    /**
     * 「有无」按钮列一格：{@code Y→有 / N→无}。
     *
     * <pre>
     * "Y" → 有
     * "N" → 无
     * null / "" / "  " → null（空格子，不是 "null" 也不是 "—"）
     * 字典外的历史脏值 → 原样带出（读侧不做业务校验）
     * </pre>
     *
     * <p>没选（新增时三个按钮都没点）是有意义的状态，必须留空 —— 见
     * {@code SegButtons} 的「再点一次 = 取消选择」。
     */
    static String flagText(String value) {
        if (StringUtils.isBlank(value)) {
            return null;
        }
        String trimmed = value.trim();
        return FLAG_LABELS.getOrDefault(trimmed, trimmed);
    }

    /**
     * 性别一格：{@code male→男 / female→女 / unknown→未知}（字典 {@code lqg_gender}）。
     *
     * <p>字典外的历史值原样带出（读侧不做业务校验，与 {@code EmbedExportService} 同一口径）；
     * 空 → 空格子。
     */
    static String genderText(String value) {
        if (StringUtils.isBlank(value)) {
            return null;
        }
        String trimmed = value.trim();
        return GENDER_LABELS.getOrDefault(trimmed, trimmed);
    }

    /**
     * 日期 → {@code yyyy-MM-dd}；空 → {@code null}（空格子）。
     */
    static String dateText(Object date) {
        if (date == null) {
            return null;
        }
        if (date instanceof java.time.LocalDate localDate) {
            return localDate.toString();
        }
        if (date instanceof Date value) {
            return new SimpleDateFormat(DATE_PATTERN).format(value);
        }
        return date.toString();
    }

    /**
     * 时间 → {@code yyyy-MM-dd HH:mm:ss}；空 → {@code null}（空格子）。
     *
     * <p>{@code process_time} 落库是 {@code timestamp}（值里有时分秒，小程序 / 抽屉都用
     * {@code type="datetime"} 填），所以这里不能只导日期。
     */
    static String dateTimeText(Object time) {
        if (time == null) {
            return null;
        }
        if (time instanceof Date value) {
            return new SimpleDateFormat(DATE_TIME_PATTERN).format(value);
        }
        return time.toString();
    }

    /**
     * 「样本记录信息表」的列名清单（{@code 14} 列，与模板逐字同序）—— 给验收脚本 / 单测对表头用。
     */
    public static Map<String, Integer> tissueHeaderIndex() {
        Map<String, Integer> out = new LinkedHashMap<>();
        out.put("来源单位", 0);
        out.put("供体姓名", 1);
        out.put("性别", 2);
        out.put("年龄", 3);
        out.put("住院号", 4);
        out.put("组织类型", 5);
        out.put("收样日期", 6);
        out.put("内部编号", 7);
        out.put("有无固定", 8);
        out.put("处理时间", 9);
        out.put("质控表", 10);
        out.put("细胞活率报告", 11);
        out.put("操作人", 12);
        out.put("备注", 13);
        return out;
    }

    /**
     * 「类器官收样记录」的列名清单（{@code 8} 列 = 模板 7 列逐字同序，「类器官类型」后插入「代数」）。
     */
    public static Map<String, Integer> organoidHeaderIndex() {
        Map<String, Integer> out = new LinkedHashMap<>();
        out.put("来源单位", 0);
        out.put("类器官类型", 1);
        out.put("代数", 2);
        out.put("收样日期", 3);
        out.put("内部编号", 4);
        out.put("处理时间", 5);
        out.put("细胞活率报告", 6);
        out.put("操作人", 7);
        return out;
    }

}
