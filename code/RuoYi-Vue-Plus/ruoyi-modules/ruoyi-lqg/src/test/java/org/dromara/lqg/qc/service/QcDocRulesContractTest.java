package org.dromara.lqg.qc.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.dromara.common.core.exception.ServiceException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * 质控文档规则表的契约测试（QC-MODEL-001 accept 1/3 的口径，纯函数、不碰库不碰 Spring）。
 *
 * <p>钉四件事：
 * <ol>
 *   <li><b>三段模板默认文字逐字</b>（accept 3 第 1 段就是逐字比对，差一个标点都红）；</li>
 *   <li><b>图片位归属</b>：{@code orig / observe / pretreat} 属样本质控表、
 *       {@code organoid_observe} 属类器官质控表、<b>评分表一个位都没有</b>；</li>
 *   <li><b>每位至多 3 张、单个附件至多 50MB</b>；</li>
 *   <li><b>路径段 → 字典取值</b>的映射（连字符 vs 下划线，contract 第 90 行）。</li>
 * </ol>
 *
 * @author QC-MODEL-001
 */
class QcDocRulesContractTest {

    @Test
    @DisplayName("① 三段模板默认文字逐字（差一个标点就红）")
    void templateDefaultsAreVerbatim() {
        assertEquals("样本按质控要求，保持2-8℃低温环境运输至实验室。", QcDocRules.RECEIVE_DESC_DEFAULT);
        assertEquals("样本外观呈黄白色。", QcDocRules.OBSERVE_DESC_DEFAULT);
        assertEquals("样本经剪切等预处理，显微镜下观察组织漏出细胞量适中，细胞活性中等；培养3d照片如左图所示。",
            QcDocRules.PRETREAT_DESC_DEFAULT);
    }

    @Test
    @DisplayName("② 图片位归属：两个位集互不相交，评分表没有图片位")
    void slotsBelongToTheirDocType() {
        assertEquals(List.of("orig", "observe", "pretreat"),
            QcDocRules.slotsOf(QcDocRules.DOC_TYPE_SAMPLE_QC));
        assertEquals(List.of("organoid_observe"),
            QcDocRules.slotsOf(QcDocRules.DOC_TYPE_ORGANOID_QC));
        assertTrue(QcDocRules.slotsOf(QcDocRules.DOC_TYPE_ORGANOID_SCORE).isEmpty(),
            "评分表没有图片位");

        // 该给的给
        assertEquals("orig", QcDocRules.requireSlotOf(QcDocRules.DOC_TYPE_SAMPLE_QC, "orig"));
        assertEquals("organoid_observe",
            QcDocRules.requireSlotOf(QcDocRules.DOC_TYPE_ORGANOID_QC, "organoid_observe"));

        // ★ accept 3 第 6 段：organoid_observe 挂到样本质控表上 → 400
        ServiceException crossSlot = assertThrows(ServiceException.class,
            () -> QcDocRules.requireSlotOf(QcDocRules.DOC_TYPE_SAMPLE_QC, "organoid_observe"));
        assertEquals(400, crossSlot.getCode());

        // ★ accept 3 第 7 段：往评分表挂图片位 → 400/500
        ServiceException scoreSlot = assertThrows(ServiceException.class,
            () -> QcDocRules.requireSlotOf(QcDocRules.DOC_TYPE_ORGANOID_SCORE, "orig"));
        assertEquals(400, scoreSlot.getCode());

        // 空 / null 图片位也不许过
        assertEquals(400, assertThrows(ServiceException.class,
            () -> QcDocRules.requireSlotOf(QcDocRules.DOC_TYPE_SAMPLE_QC, null)).getCode());
    }

    @Test
    @DisplayName("③ 每位至多 3 张、单个附件至多 50MB")
    void limits() {
        assertEquals(3, QcDocRules.MAX_IMAGES_PER_SLOT);
        assertEquals(50L * 1024 * 1024, QcDocRules.MAX_ATTACHMENT_BYTES);
    }

    @Test
    @DisplayName("④ 路径段 → 字典取值（连字符 ↔ 下划线）；不认识的一律 400")
    void docTypePathMapping() {
        assertEquals(QcDocRules.DOC_TYPE_SAMPLE_QC, QcDocRules.requireDocType("sample-qc"));
        assertEquals(QcDocRules.DOC_TYPE_ORGANOID_QC, QcDocRules.requireDocType("organoid-qc"));
        // ★ 评分表的路径段是 score，字典取值是 organoid_score（不是 score）
        assertEquals(QcDocRules.DOC_TYPE_ORGANOID_SCORE, QcDocRules.requireDocType("score"));
        assertEquals(400, assertThrows(ServiceException.class,
            () -> QcDocRules.requireDocType("sample_qc")).getCode());
        assertEquals(400, assertThrows(ServiceException.class,
            () -> QcDocRules.requireDocType(null)).getCode());
    }

    @Test
    @DisplayName("⑤ 文档状态常量与字典 lqg_doc_status 的取值一致")
    void docStatus() {
        assertEquals("draft", QcDocRules.STATUS_DRAFT);
        assertEquals("published", QcDocRules.STATUS_PUBLISHED);
        assertEquals(4, QcDocRules.SCORE_DICT_TYPES.size(), "四个评分字典");
    }

}
