package org.dromara.lqg.qc.service;

import org.dromara.lqg.qc.domain.QcOrganoidDoc;
import org.dromara.lqg.qc.domain.QcSampleDoc;
import org.dromara.lqg.qc.domain.QcScoreDoc;
import org.dromara.lqg.qc.domain.vo.QcDocListVo;
import org.dromara.lqg.sample.domain.Sample;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 「质控文档」板块列表一行的拼装（飞书「网页工作台」第 17 行）：三份状态 → 进度。
 */
class QcDocListServiceTest {

    private static Sample sample(String kind) {
        Sample s = new Sample();
        s.setId(1L);
        s.setSampleKind(kind);
        s.setInternalNo("T-hli01");
        s.setTissueType("肝组织");
        s.setOrganoidType("肝类器官");
        return s;
    }

    private static QcSampleDoc sampleDoc(String status, long updatedAt) {
        QcSampleDoc d = new QcSampleDoc();
        d.setDocStatus(status);
        d.setUpdateTime(new Date(updatedAt));
        return d;
    }

    private static QcOrganoidDoc organoidDoc(String status) {
        QcOrganoidDoc d = new QcOrganoidDoc();
        d.setDocStatus(status);
        return d;
    }

    private static QcScoreDoc scoreDoc(String status, Integer total, long updatedAt) {
        QcScoreDoc d = new QcScoreDoc();
        d.setDocStatus(status);
        d.setTotalScore(total);
        d.setUpdateTime(new Date(updatedAt));
        return d;
    }

    @Test
    @DisplayName("① 三份都没打开过 → 未开始，状态都是 null")
    void none() {
        QcDocListVo vo = QcDocListService.toVo(sample("tissue"), null, null, null);
        assertEquals(QcDocListService.PROGRESS_NONE, vo.getProgress());
        assertEquals(0, vo.getPublishedCount());
        assertNull(vo.getSampleQcStatus());
        assertNull(vo.getLastUpdateTime());
        assertEquals("肝组织", vo.getTypeName());
    }

    @Test
    @DisplayName("② 打开过（空草稿）或完成了一部分 → 填写中；最近修改取三份里最新的")
    void doing() {
        QcDocListVo vo = QcDocListService.toVo(sample("organoid"),
            sampleDoc(QcDocRules.STATUS_PUBLISHED, 1000L), organoidDoc(QcDocRules.STATUS_DRAFT),
            scoreDoc(QcDocRules.STATUS_DRAFT, 7, 5000L));
        assertEquals(QcDocListService.PROGRESS_DOING, vo.getProgress());
        assertEquals(1, vo.getPublishedCount());
        assertEquals(7, vo.getTotalScore());
        assertEquals(5000L, vo.getLastUpdateTime().getTime());
        assertEquals("肝类器官", vo.getTypeName(), "类器官样本显示类器官类型");

        QcDocListVo draftsOnly = QcDocListService.toVo(sample("tissue"),
            sampleDoc(QcDocRules.STATUS_DRAFT, 1L), organoidDoc(QcDocRules.STATUS_DRAFT), scoreDoc(QcDocRules.STATUS_DRAFT, null, 1L));
        assertEquals(QcDocListService.PROGRESS_DOING, draftsOnly.getProgress(), "空草稿也算动过（编辑页打开时建的）");
    }

    @Test
    @DisplayName("③ 三份都已完成并同步 → 已全部完成")
    void done() {
        QcDocListVo vo = QcDocListService.toVo(sample("tissue"),
            sampleDoc(QcDocRules.STATUS_PUBLISHED, 1L), organoidDoc(QcDocRules.STATUS_PUBLISHED),
            scoreDoc(QcDocRules.STATUS_PUBLISHED, 9, 2L));
        assertEquals(QcDocListService.PROGRESS_DONE, vo.getProgress());
        assertEquals(3, vo.getPublishedCount());
    }

    @Test
    @DisplayName("⑦ 编辑页打开时建的空草稿、从没保存过（update_time = create_time）→ 仍算未填写，不计最近修改（UX WEB-02）")
    void openedButNeverSaved() {
        Date created = new Date(1_000_000L);
        QcSampleDoc s = new QcSampleDoc();
        s.setDocStatus("draft");
        s.setCreateTime(created);
        s.setUpdateTime(created);
        QcOrganoidDoc o = new QcOrganoidDoc();
        o.setDocStatus("draft");
        o.setCreateTime(created);
        o.setUpdateTime(new Date(created.getTime() + 300));
        QcScoreDoc sc = new QcScoreDoc();
        sc.setDocStatus("draft");
        sc.setCreateTime(created);
        sc.setUpdateTime(created);
        QcDocListVo vo = QcDocListService.toVo(sample("tissue"), s, o, sc);
        assertEquals(QcDocListService.PROGRESS_NONE, vo.getProgress());
        assertNull(vo.getSampleQcStatus());
        assertNull(vo.getOrganoidQcStatus());
        assertNull(vo.getScoreStatus());
        assertNull(vo.getLastUpdateTime());

        // 真保存过一次（晚于建行 1 秒以上）→ 草稿、进行中、最近修改 = 保存时间
        Date saved = new Date(created.getTime() + 60_000L);
        s.setUpdateTime(saved);
        QcDocListVo vo2 = QcDocListService.toVo(sample("tissue"), s, o, sc);
        assertEquals("draft", vo2.getSampleQcStatus());
        assertEquals(QcDocListService.PROGRESS_DOING, vo2.getProgress());
        assertEquals(saved, vo2.getLastUpdateTime());
    }
}
