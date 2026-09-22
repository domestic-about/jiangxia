package org.dromara.lqg.doc.render;

import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 三份 docx 模板的资源契约（ticket §0 口径复述 1 + §2）。
 *
 * <p>这份测试盯的是**模板文件本身**（不是渲染结果）：占位符齐不齐、两句印死的注是否逐字、
 * 甲方原件里的示例文字有没有清干净。渲染结果由 accept 1 的 {@code docx_check.py} 断。
 *
 * <p>★ 用 POI 打开模板顺带证明一件事：**生成出来的 docx 是 POI 打得开的合法文件**
 * （模板是拿原件做 XML 手术改出来的，见
 * {@code doc/waves/reports/DOC-RENDER-001/make-doc-templates.py}）。
 *
 * <p>★ 比较前去掉全部空白，与 {@code doc/verify/docx_check.py} 同一判据 ——
 * 两句注在原件里被拆在多个 run 上（{@code ＜1x10} + {@code 4} + {@code 。}），
 * 只有拼起来再比才有意义。
 *
 * @author DOC-RENDER-001
 */
class DocTemplateContractTest {

    /** 样本质控表的注（REQ-QC-004，一个字都不许动）。 */
    private static final String SAMPLE_QC_NOTE =
        "注：合格，活率≥70%；基本合格，50%~70%；不合格，＜50%或活细胞＜1x104。";

    /** 类器官质量评分表的注（REQ-QC-008）。 */
    private static final String SCORE_NOTE =
        "注：类器官质量评分≤50表示类器官质量偏差，药敏实验失败风险较大；"
            + "50~75表示类器官质量中等；≥75表示类器官质量良好。";

    private static String flatten(String text) {
        return text.replaceAll("\\s+", "");
    }

    private static String textOf(String docKind) throws Exception {
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(DocTemplate.bytes(docKind)));
             XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
            return flatten(extractor.getText());
        }
    }

    @Test
    @DisplayName("模板版本号文件存在且非空")
    void versionExists() {
        assertTrue(DocTemplate.version().matches("\\d+"), "模板版本号应该是数字，当前：" + DocTemplate.version());
    }

    @Test
    @DisplayName("三个种类都有模板，且模板里没有残留的甲方示例文字")
    void templatesExistAndAreClean() throws Exception {
        for (String kind : DocKinds.TEMPLATED) {
            assertTrue(DocTemplate.bytes(kind).length > 0, kind + " 模板为空");
            String text = textOf(kind);
            assertFalse(text.contains("要求图片可以放大"),
                kind + " 模板里还留着「要求图片可以放大」（应换成图片占位符）");
        }
    }

    @Test
    @DisplayName("样本质控表：所有占位符都在，两句注逐字保留")
    void sampleQcTemplate() throws Exception {
        String text = textOf(DocKinds.SAMPLE_QC);
        for (String tag : List.of("patient_no", "source_unit_name", "donor_name", "sampling_site",
            "sampling_method", "gender", "clinical_diagnosis", "receive_desc", "receive_date",
            "process_time", "operator_name", "internal_no", "viability_file_name",
            "orig_desc", "observe_desc", "pretreat_desc",
            "@orig_img1", "@orig_img2", "@orig_img3",
            "@observe_img1", "@observe_img2", "@observe_img3",
            "@pretreat_img1", "@pretreat_img2", "@pretreat_img3")) {
            assertTrue(text.contains("{{" + tag + "}}"), "样本质控表模板缺占位符 {{" + tag + "}}");
        }
        assertTrue(text.contains(flatten(SAMPLE_QC_NOTE)), "样本质控表的注不是逐字原文");
    }

    @Test
    @DisplayName("类器官质控表：五个字段 + 一个图片位，占位符都在")
    void organoidQcTemplate() throws Exception {
        String text = textOf(DocKinds.ORGANOID_QC);
        for (String tag : List.of("formed_time", "growth_state", "growth_desc",
            "planned_drug_screen", "feedback_time",
            "@organoid_observe_img1", "@organoid_observe_img2", "@organoid_observe_img3")) {
            assertTrue(text.contains("{{" + tag + "}}"), "类器官质控表模板缺占位符 {{" + tag + "}}");
        }
    }

    @Test
    @DisplayName("评分表：四个纵合并的分值格 + 合计行，注逐字保留")
    void scoreTemplate() throws Exception {
        String text = textOf(DocKinds.ORGANOID_SCORE);
        for (String tag : List.of("sc_pre_culture", "sc_culture_days", "sc_count", "sc_diameter", "sc_total")) {
            assertTrue(text.contains("{{" + tag + "}}"), "评分表模板缺占位符 {{" + tag + "}}");
        }
        assertTrue(text.contains("合计"), "评分表模板表尾缺合计行");
        assertTrue(text.contains(flatten(SCORE_NOTE)), "评分表的注不是逐字原文");
        // 原件的 12 个档位文字必须原样在（选中档是靠分值格区分，不是把没选的档删掉）
        for (String option : List.of("<40", "40~80", ">80", ">14d", "≤14d",
            "<100", "100~1500", "1500~4000", ">4000", "<30μm", "30~100μm", ">100μm")) {
            assertTrue(text.contains(option), "评分表模板缺档位文字 " + option);
        }
    }

    @Test
    @DisplayName("模板里的占位符语法是 poi-tl 的 {{…}}（docx_check 的 --no-placeholder 也认这个）")
    void placeholderSyntax() {
        assertTrue(Pattern.compile("\\{\\{[^}]+}}").matcher("{{patient_no}}").find());
    }
}
