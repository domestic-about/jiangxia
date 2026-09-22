package org.dromara.lqg.doc.render;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 内容指纹的敏感度（accept 2 第 5 段点名的 {@code DocFingerprintTest}）。
 *
 * <p>★★ <b>为什么用「遍历每一个输入翻一个值」而不是手写五六条</b>：accept 2 的 counterfeit
 * 写得很清楚 ——「缓存了但指纹只算了文档自己的字段 → 改了内容之后 S3 仍等于 S1」。
 * 手写几条只能证明那几条；遍历 {@link DocRenderModel#texts()} / {@link DocRenderModel#images()}
 * 的**全部键**，以后有人往模型里加字段却忘了加进指纹，这里立刻红。
 *
 * <p>★ 纯 JUnit、不起 Spring、不连库：指纹是 {@link DocRenderModel#canonical()} 的纯函数。
 *
 * @author DOC-RENDER-001
 */
class DocFingerprintTest {

    /** 一份「什么都填满」的样本质控表模型（内部版）。 */
    private static DocRenderModel base(String audience) {
        DocRenderModel model = new DocRenderModel(DocKinds.SAMPLE_QC, audience, "1");
        model.text("patient_no", "P-0001")
            .text("source_unit_name", "A 医院")          // ← 从样本主档带出
            .text("donor_name", "测试供体甲")
            .text("sampling_site", "肝右叶")
            .text("sampling_method", "手术切除")
            .text("gender", "男")
            .text("clinical_diagnosis", "测试诊断")
            .text("receive_desc", "样本按质控要求，保持2-8℃低温环境运输至实验室。")
            .text("receive_date", "2026-09-01")
            .text("process_time", "2026-09-01 14:20:00")
            .text("operator_name", "李工")
            .text("internal_no", DocAudiences.isInternal(audience) ? "T-hli01" : "")
            .text("viability_file_name", "活率报告.pdf")
            .text("orig_desc", "组织块约 0.8cm。")
            .text("observe_desc", "样本外观呈黄白色。")
            .text("pretreat_desc", "样本经剪切等预处理。")
            .imageSlot("orig", List.of(9000004001L, 9000004002L))
            .imageSlot("observe", List.of(9000004003L))
            .imageSlot("pretreat", List.of())
            .attachments(List.of(9000004011L))
            .part("viability_oss=9000004011")
            .part("doc_status=published");
        return model;
    }

    private static DocRenderModel base() {
        return base(DocAudiences.INTERNAL);
    }

    @Test
    @DisplayName("同样的输入 → 同样的指纹（缓存能命中的前提）")
    void stable() {
        assertEquals(base().contentHash(), base().contentHash());
        assertTrue(base().contentHash().matches("[0-9a-f]{64}"), "指纹必须是 64 位小写十六进制");
    }

    @Test
    @DisplayName("文档自己的每一个字段改了 → 指纹变（逐项翻一个值）")
    void everyTextFieldIsCovered() {
        DocRenderModel reference = base();
        for (Map.Entry<String, String> entry : new LinkedHashMap<>(reference.texts()).entrySet()) {
            DocRenderModel changed = base();
            changed.text(entry.getKey(), entry.getValue() + "-改");
            assertNotEquals(reference.contentHash(), changed.contentHash(),
                "文本标签「" + entry.getKey() + "」改了，指纹却没变 —— 这条输入没进指纹");
        }
    }

    @Test
    @DisplayName("只改样本主档带出的来源单位 → 指纹变（accept 2 点名的一条）")
    void sampleMasterFieldIsCovered() {
        DocRenderModel reference = base();
        DocRenderModel changed = base().text("source_unit_name", "B 大学");
        assertNotEquals(reference.contentHash(), changed.contentHash());
    }

    @Test
    @DisplayName("只换一张图 → 指纹变；图没变 → 指纹不变")
    void imageOssIdIsCovered() {
        DocRenderModel reference = base();
        DocRenderModel changed = base().imageSlot("observe", List.of(9000004999L));
        assertNotEquals(reference.contentHash(), changed.contentHash());

        DocRenderModel same = base().imageSlot("observe", List.of(9000004003L));
        assertEquals(reference.contentHash(), same.contentHash());
    }

    @Test
    @DisplayName("同一个位里换顺序 → 指纹变（甲方看到的图换了位置）")
    void imageOrderMatters() {
        DocRenderModel reference = base();
        DocRenderModel changed = base().imageSlot("orig", List.of(9000004002L, 9000004001L));
        assertNotEquals(reference.contentHash(), changed.contentHash());
    }

    @Test
    @DisplayName("加一张图 / 去掉一张图 → 指纹变")
    void imageCountMatters() {
        DocRenderModel reference = base();
        assertNotEquals(reference.contentHash(),
            base().imageSlot("pretreat", List.of(9000004004L)).contentHash());
        assertNotEquals(reference.contentHash(),
            base().imageSlot("orig", List.of(9000004001L)).contentHash());
    }

    @Test
    @DisplayName("附件集变了 → 指纹变（附件不进正文，但预览页那一栏是它）")
    void attachmentsAreCovered() {
        DocRenderModel reference = base();
        List<Long> more = new ArrayList<>(reference.attachmentOssIds());
        more.add(9000004999L);
        assertNotEquals(reference.contentHash(), base().attachments(more).contentHash());
        assertNotEquals(reference.contentHash(), base().attachments(List.of()).contentHash());
    }

    @Test
    @DisplayName("只升模板版本 → 指纹变（accept 2 点名的一条）")
    void templateVersionIsCovered() {
        DocRenderModel reference = base();
        DocRenderModel changed = new DocRenderModel(DocKinds.SAMPLE_QC, DocAudiences.INTERNAL, "2");
        changed.texts().putAll(reference.texts());
        assertNotEquals(reference.contentHash(), changed.contentHash());
    }

    @Test
    @DisplayName("同一个样本的内外部版指纹不同（audience 进指纹，两份产物互不覆盖）")
    void audienceIsCovered() {
        // 外部版的 internal_no 本来就是空的 —— 即便字段完全一样，audience 也必须让指纹分叉
        DocRenderModel internal = base(DocAudiences.INTERNAL);
        DocRenderModel external = base(DocAudiences.EXTERNAL);
        assertNotEquals("T-hli01", external.texts().get("internal_no"), "外部版的内部编号必须是空的");
        assertNotEquals(internal.contentHash(), external.contentHash());

        DocRenderModel sameFieldsDifferentAudience = new DocRenderModel(
            DocKinds.ORGANOID_QC, DocAudiences.EXTERNAL, "1").text("growth_state", "良好");
        DocRenderModel internalTwin = new DocRenderModel(
            DocKinds.ORGANOID_QC, DocAudiences.INTERNAL, "1").text("growth_state", "良好");
        assertNotEquals(internalTwin.contentHash(), sameFieldsDifferentAudience.contentHash());
    }

    @Test
    @DisplayName("文档种类进指纹（换种类必重出）")
    void docKindIsCovered() {
        DocRenderModel score = new DocRenderModel(DocKinds.ORGANOID_SCORE, DocAudiences.INTERNAL, "1")
            .text("sc_total", "85");
        DocRenderModel organoid = new DocRenderModel(DocKinds.ORGANOID_QC, DocAudiences.INTERNAL, "1")
            .text("sc_total", "85");
        assertNotEquals(score.contentHash(), organoid.contentHash());
    }

    @Test
    @DisplayName("评分表：四个分值 + 合计，任一改了都要重出")
    void scoreFieldsAreCovered() {
        DocRenderModel reference = score();
        for (String tag : List.of("sc_pre_culture", "sc_culture_days", "sc_count", "sc_diameter", "sc_total")) {
            DocRenderModel changed = score();
            changed.text(tag, "99");
            assertNotEquals(reference.contentHash(), changed.contentHash(), "评分字段 " + tag + " 没进指纹");
        }
    }

    private static DocRenderModel score() {
        return new DocRenderModel(DocKinds.ORGANOID_SCORE, DocAudiences.INTERNAL, "1")
            .text("sc_pre_culture", "20")
            .text("sc_culture_days", "10")
            .text("sc_count", "25")
            .text("sc_diameter", "30")
            .text("sc_total", "85")
            .part("level:pre_culture=gt80")
            .part("level:culture_days=le14")
            .part("level:count=1500to4000")
            .part("level:diameter=gt100")
            .part("doc_status=published");
    }

    @Test
    @DisplayName("合并件：任一成员的内容变了 → 合并件重出")
    void mergedFollowsMembers() {
        DocRenderModel qc = base();
        DocRenderModel organoid = new DocRenderModel(DocKinds.ORGANOID_QC, DocAudiences.INTERNAL, "1")
            .text("growth_state", "良好");
        DocRenderModel score = score();

        DocRenderModel reference = DocRenderModel.merged(DocAudiences.INTERNAL, "1", List.of(qc, organoid, score));
        DocRenderModel sameAgain = DocRenderModel.merged(DocAudiences.INTERNAL, "1",
            List.of(base(), organoid, score));
        assertEquals(reference.contentHash(), sameAgain.contentHash());

        DocRenderModel memberChanged = DocRenderModel.merged(DocAudiences.INTERNAL, "1",
            List.of(base().text("source_unit_name", "B 大学"), organoid, score));
        assertNotEquals(reference.contentHash(), memberChanged.contentHash(), "成员变了合并件却没变");

        DocRenderModel memberRemoved = DocRenderModel.merged(DocAudiences.INTERNAL, "1", List.of(qc, organoid));
        assertNotEquals(reference.contentHash(), memberRemoved.contentHash(), "少了一份成员却没变");

        DocRenderModel reordered = DocRenderModel.merged(DocAudiences.INTERNAL, "1", List.of(organoid, qc, score));
        assertNotEquals(reference.contentHash(), reordered.contentHash(), "顺序变了却没变");
    }
}
