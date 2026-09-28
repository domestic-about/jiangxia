package org.dromara.lqg.doc.render.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.dromara.common.core.exception.ServiceException;
import org.dromara.lqg.doc.pdf.DocArtifactRows;
import org.dromara.lqg.doc.pdf.DocArtifactStore;
import org.dromara.lqg.doc.pdf.PageImageService;
import org.dromara.lqg.doc.pdf.PdfConvertService;
import org.dromara.lqg.doc.render.DocAudiences;
import org.dromara.lqg.doc.render.DocKinds;
import org.dromara.lqg.doc.render.DocOleEmbedder;
import org.dromara.lqg.doc.render.DocRenderModel;
import org.dromara.lqg.doc.render.DocRenderModelFactory;
import org.dromara.lqg.doc.render.DocTemplate;
import org.dromara.lqg.doc.render.DocxRenderer;
import org.dromara.lqg.doc.render.MissingImage;
import org.dromara.lqg.doc.render.domain.DocFile;
import org.dromara.lqg.doc.render.domain.vo.DocDownloadVo;
import org.dromara.lqg.doc.render.domain.vo.DocRenderVo;
import org.dromara.lqg.sample.domain.Sample;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 渲染编排的<b>完整性</b>行为测试（独立验收 V04 / V23）—— 不连库：产物表、OSS、转换服务都是内存替身，
 * 但 {@link DocRenderService} 与 {@link DocArtifactRows#completeSet} 用的是真实现。
 *
 * <p>钉住的事故形态（V04 活体证实过的四条，外加 #217 的口径）：
 * <ol>
 *   <li>撤回合并件里的一份之后，旧合并件（含已撤回文档）<b>一个字节都不再下发</b>，下载明确提示在重新生成；</li>
 *   <li>重出之后 Word / PDF / 页面图是<b>同一版</b>，内容不含已撤回的那份；</li>
 *   <li>「重新生成」真的重出（{@code force=true}），撕裂的历史一版不算缓存命中；</li>
 *   <li>渲染期间成员又变了 → 那一版落不了 done，按新成员重出；</li>
 *   <li>外部版有缺图 → failed（不对外）；内部版照出并把缺图数与明细记在渲染记录上。</li>
 * </ol>
 *
 * <p>G 批 C 组加的一节（甲方 2026-09-24 意见第 23 行：内部编号开关也管外部版文档）：
 * 开关开 / 关两种状态下外部版印什么、指纹随开关变、<b>开关关着时不会命中也不会发出「印了编号」的那一份</b>
 * （Word / PDF 下载、合并件、渲染途中切换的竞态）、打开开关时不断档、切换后后台逐份重出、模板升级后自愈。
 *
 * @author 独立验收 V04 / V23 修复 · G 批 C 组
 */
class DocRenderIntegrityTest {

    private static final long S = 9000001001L;

    private FakeFactory factory;
    private FakeRenderer renderer;
    private FakeRows rows;
    private FakeStore store;
    private FakePdf pdf;
    private DocRenderService service;
    private final List<Runnable> kicked = new ArrayList<>();
    /** 后台「按新设置重出」队列（单线程那条）排上的活。 */
    private final List<Runnable> refreshed = new ArrayList<>();

    @BeforeEach
    void setUp() {
        factory = new FakeFactory();
        renderer = new FakeRenderer();
        rows = new FakeRows();
        store = new FakeStore();
        pdf = new FakePdf();
        service = new DocRenderService(factory, renderer, rows, store, pdf, new FakePages(rows, store));
        // 后台补渲染排进队列、不自己跑（测试按需手动执行），保证每一步可复现
        service.setAsyncExecutor(kicked::add);
        service.setRefreshExecutor(refreshed::add);
        factory.published.addAll(DocKinds.MERGED_ORDER);
    }

    // ══════════════════════════════════════════════════════════════════════
    // V04
    // ══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("V04 ① 撤回一份后：旧合并件不再下发（Word / PDF 都 400 且说清在重新生成），重出后不含撤回的那份")
    void withdrawnMemberIsNeverDeliveredAgain() {
        DocRenderVo first = service.render(S, DocKinds.MERGED, DocAudiences.INTERNAL);
        assertEquals(DocArtifactRows.STATUS_DONE, first.getStatus());
        assertTrue(downloadText("docx").contains("organoid_score"), "三份都完成时合并件里有评分表");

        // 撤回评分表：DocPublishService 在同一个请求里做的就是这两步
        factory.published.remove(DocKinds.ORGANOID_SCORE);
        service.markMergedStale(S);

        ServiceException docx = assertThrows(ServiceException.class,
            () -> service.download(S, DocKinds.MERGED, "docx", DocAudiences.INTERNAL));
        assertEquals(400, docx.getCode());
        assertTrue(docx.getMessage().contains("重新生成"), docx.getMessage());
        ServiceException pdfEx = assertThrows(ServiceException.class,
            () -> service.download(S, DocKinds.MERGED, "pdf", DocAudiences.INTERNAL));
        assertEquals(400, pdfEx.getCode());
        assertFalse(kicked.isEmpty(), "读到失效的一版时顺手排了一次后台重出（不让页面干等）");

        // 后台重出（invalidateMerged 同一条路）
        service.invalidateMerged(S);
        DocFile header = rows.header(S, DocKinds.MERGED, DocAudiences.INTERNAL);
        assertEquals(DocArtifactRows.STATUS_DONE, header.getRenderStatus());
        assertTrue(rows.completeSet(S, DocKinds.MERGED, DocAudiences.INTERNAL, header), "Word / PDF / 页面图同一版");
        String word = downloadText("docx");
        String pdfText = downloadText("pdf");
        assertFalse(word.contains("organoid_score"), "重出的 Word 不含已撤回的评分表：" + word);
        assertFalse(pdfText.contains("organoid_score"), "重出的 PDF 不含已撤回的评分表");
        assertTrue(word.contains("sample_qc") && word.contains("organoid_qc"));
    }

    @Test
    @DisplayName("V04 ② 失效后再调 render：不命中缓存、真的重出（旧版的「cached:true 空转」不再出现）")
    void renderAfterInvalidationReallyRerenders() {
        service.render(S, DocKinds.MERGED, DocAudiences.INTERNAL);
        factory.published.remove(DocKinds.ORGANOID_SCORE);
        service.markMergedStale(S);
        DocRenderVo again = service.render(S, DocKinds.MERGED, DocAudiences.INTERNAL);
        assertFalse(again.isCached(), "成员变了还命中缓存 = V04 的空转");
        assertEquals(DocArtifactRows.STATUS_DONE, again.getStatus());
        assertFalse(downloadText("docx").contains("organoid_score"));
    }

    @Test
    @DisplayName("V04 ③ 撕裂的历史一版（header 被改写成新指纹、PDF / 页面图还是旧的）不算命中；force=true 一定重出")
    void tornRowIsNotACacheHitAndForceRerenders() {
        service.render(S, DocKinds.MERGED, DocAudiences.INTERNAL);
        // 模拟旧版 markStale 留下的数据：只把 header 的指纹改成「此刻该有的」，状态仍 done
        factory.published.remove(DocKinds.ORGANOID_SCORE);
        String expected = DocRenderModel.merged(DocAudiences.INTERNAL, DocTemplate.version(),
            factory.mergedMembers(S, DocAudiences.INTERNAL)).contentHash();
        rows.header(S, DocKinds.MERGED, DocAudiences.INTERNAL);
        rows.rewriteHeaderHash(S, DocKinds.MERGED, DocAudiences.INTERNAL, expected);

        assertThrows(ServiceException.class, () -> service.download(S, DocKinds.MERGED, "docx", DocAudiences.INTERNAL),
            "撕裂的一版不许下发（旧版这里会给出含评分表的旧 Word）");
        DocRenderVo vo = service.render(S, DocKinds.MERGED, DocAudiences.INTERNAL);
        assertFalse(vo.isCached(), "撕裂的一版不算命中");
        assertFalse(downloadText("docx").contains("organoid_score"));

        // 什么都没变时 force=true 也要真的重出（「重新生成」不空转）
        Long before = rows.header(S, DocKinds.MERGED, DocAudiences.INTERNAL).getOssId();
        DocRenderVo cached = service.render(S, DocKinds.MERGED, DocAudiences.INTERNAL);
        assertTrue(cached.isCached(), "没 force、内容没变：命中缓存");
        DocRenderVo forced = service.render(S, DocKinds.MERGED, DocAudiences.INTERNAL, true);
        assertFalse(forced.isCached());
        assertNotEquals(before, rows.header(S, DocKinds.MERGED, DocAudiences.INTERNAL).getOssId(), "force 出了新的一份");
    }

    @Test
    @DisplayName("V04 ④ 渲染期间有人撤回：那一版落不了 done，按撤回后的成员重出")
    void withdrawDuringRenderIsNotResurrected() {
        renderer.duringMerge = () -> {
            renderer.duringMerge = null;
            factory.published.remove(DocKinds.ORGANOID_SCORE);
            service.markMergedStale(S);
        };
        DocRenderVo vo = service.render(S, DocKinds.MERGED, DocAudiences.INTERNAL);
        assertEquals(DocArtifactRows.STATUS_DONE, vo.getStatus());
        assertFalse(downloadText("docx").contains("organoid_score"),
            "渲染期间撤回的那份不许借这次在途渲染复活");
    }

    @Test
    @DisplayName("V04 ⑤ 一份成员都不剩：合并件撤下（下载明确说「还没有生成」），不留一个永远「生成中」的空壳")
    void lastMemberWithdrawnRemovesMerged() {
        service.render(S, DocKinds.MERGED, DocAudiences.INTERNAL);
        factory.published.clear();
        service.markMergedStale(S);
        service.invalidateMerged(S);
        assertNull(rows.header(S, DocKinds.MERGED, DocAudiences.INTERNAL));
        ServiceException e = assertThrows(ServiceException.class,
            () -> service.download(S, DocKinds.MERGED, "docx", DocAudiences.INTERNAL));
        assertTrue(e.getMessage().contains("还没有生成"), e.getMessage());
    }

    @Test
    @DisplayName("V04 ⑥ 改的是草稿（成员没变）：后台失效检查什么都不写（内容没变不重出）")
    void draftEditDoesNotTouchMerged() {
        service.render(S, DocKinds.MERGED, DocAudiences.INTERNAL);
        int writes = rows.writes;
        service.invalidateMerged(S);
        assertEquals(writes, rows.writes, "成员与内容都没变 → 零写库");
    }

    // ══════════════════════════════════════════════════════════════════════
    // V23（#217）
    // ══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("V23 ① 外部版有缺图 → failed（原因说清缺了哪张），产物不对外；内部版照出并记缺图")
    void missingImageFailsExternalButInternalIsRecorded() {
        renderer.missingOssIds.add(9000004002L);
        factory.images.put(DocKinds.SAMPLE_QC, List.of(9000004001L, 9000004002L));

        DocRenderVo ext = service.render(S, DocKinds.SAMPLE_QC, DocAudiences.EXTERNAL);
        assertEquals(DocArtifactRows.STATUS_FAILED, ext.getStatus());
        assertTrue(ext.getErrorMsg().contains("外部版有 1 张图取不到"), ext.getErrorMsg());
        assertTrue(ext.getErrorMsg().contains("收样原始情况 第 2 张"), ext.getErrorMsg());
        assertEquals(1, ext.getMissingImageCount());
        assertFalse(rows.completeSet(S, DocKinds.SAMPLE_QC, DocAudiences.EXTERNAL,
            rows.header(S, DocKinds.SAMPLE_QC, DocAudiences.EXTERNAL)), "外部版不可用");
        assertThrows(ServiceException.class,
            () -> service.download(S, DocKinds.SAMPLE_QC, "pdf", DocAudiences.EXTERNAL));
        assertEquals(0, pdf.calls, "外部版缺图直接失败，不去转 PDF");

        DocRenderVo in = service.render(S, DocKinds.SAMPLE_QC, DocAudiences.INTERNAL);
        assertEquals(DocArtifactRows.STATUS_DONE, in.getStatus(), "内部版照出");
        assertEquals(1, in.getMissingImageCount(), "缺图数持久化在渲染记录上");
        assertTrue(in.getMissingImages().contains("样本质控表·收样原始情况 第 2 张"), in.getMissingImages());
        DocFile header = rows.header(S, DocKinds.SAMPLE_QC, DocAudiences.INTERNAL);
        assertEquals(1, header.getMissingImageCount());
        assertNotNull(service.download(S, DocKinds.SAMPLE_QC, "docx", DocAudiences.INTERNAL).getUrl());
    }

    @Test
    @DisplayName("V23 ② 图补上后重新生成：外部版恢复、内部版缺图清零")
    void recoversAfterImageIsBack() {
        renderer.missingOssIds.add(9000004002L);
        factory.images.put(DocKinds.SAMPLE_QC, List.of(9000004001L, 9000004002L));
        service.render(S, DocKinds.SAMPLE_QC, DocAudiences.EXTERNAL);
        service.render(S, DocKinds.SAMPLE_QC, DocAudiences.INTERNAL);

        renderer.missingOssIds.clear();
        DocRenderVo ext = service.render(S, DocKinds.SAMPLE_QC, DocAudiences.EXTERNAL);
        assertEquals(DocArtifactRows.STATUS_DONE, ext.getStatus(), "failed 不是缓存命中：一调就重试");
        assertEquals(0, ext.getMissingImageCount());
        DocRenderVo in = service.render(S, DocKinds.SAMPLE_QC, DocAudiences.INTERNAL, true);
        assertEquals(0, in.getMissingImageCount(), "「重新生成」之后缺图清零");
        assertNull(in.getMissingImages());
    }

    @Test
    @DisplayName("V23 ③ 缺图明细是人话：图片位中文名 + 第几张 + 原因；很多张时截断并注明总数")
    void missingSummaryIsReadable() {
        List<MissingImage> many = new ArrayList<>();
        for (int i = 1; i <= 40; i++) {
            many.add(new MissingImage(DocKinds.SAMPLE_QC, "pretreat", i, 9000000000L + i, "存储里读不到这个文件"));
        }
        String summary = MissingImage.summary(many);
        assertTrue(summary.length() <= 480, "不超过列宽：" + summary.length());
        assertTrue(summary.startsWith("样本质控表·样本预处理情况 第 1 张（存储里读不到这个文件）"), summary);
        assertTrue(summary.endsWith("共 40 张"), summary);
        assertNull(MissingImage.summary(List.of()));
    }

    // ══════════════════════════════════════════════════════════════════════
    // 内部编号开关作用到外部版文档（甲方 2026-09-24 意见第 23 行，G 批 C 组）
    // ══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("开关 ① 关 → 外部版那一格空；开 → 印出来且不命中关着时的缓存；再关 → 不命中开着时的缓存，回到原指纹")
    void switchDrivesExternalContentAndCache() {
        service.render(S, DocKinds.SAMPLE_QC, DocAudiences.INTERNAL);
        assertTrue(text(DocKinds.SAMPLE_QC, DocAudiences.INTERNAL, "docx").contains("内部编号=T-hli01"), "内部版一直印");

        factory.switchOn = false;
        DocRenderVo off = service.render(S, DocKinds.SAMPLE_QC, DocAudiences.EXTERNAL);
        assertEquals(DocArtifactRows.STATUS_DONE, off.getStatus());
        assertTrue(text(DocKinds.SAMPLE_QC, DocAudiences.EXTERNAL, "docx").contains("内部编号=}"), "关着：外部版那一格空");
        assertEquals("N", rows.header(S, DocKinds.SAMPLE_QC, DocAudiences.EXTERNAL).getShowInternalNo());

        factory.switchOn = true;
        DocRenderVo on = service.render(S, DocKinds.SAMPLE_QC, DocAudiences.EXTERNAL);
        assertFalse(on.isCached(), "开关打开：不许命中关着时出的那一份");
        assertNotEquals(off.getContentHash(), on.getContentHash(), "指纹随开关变");
        assertTrue(text(DocKinds.SAMPLE_QC, DocAudiences.EXTERNAL, "docx").contains("内部编号=T-hli01"));
        assertTrue(text(DocKinds.SAMPLE_QC, DocAudiences.EXTERNAL, "pdf").contains("内部编号=T-hli01"), "PDF 与 Word 同一版");
        assertEquals("Y", rows.header(S, DocKinds.SAMPLE_QC, DocAudiences.EXTERNAL).getShowInternalNo());

        factory.switchOn = false;
        DocRenderVo offAgain = service.render(S, DocKinds.SAMPLE_QC, DocAudiences.EXTERNAL);
        assertFalse(offAgain.isCached(), "★ 开关关回去：绝不许命中开着时印了编号的那一份");
        assertEquals(off.getContentHash(), offAgain.getContentHash(), "内容没变：与第一次关着时同一个指纹");
        assertFalse(text(DocKinds.SAMPLE_QC, DocAudiences.EXTERNAL, "docx").contains("T-hli01"));
        assertFalse(text(DocKinds.SAMPLE_QC, DocAudiences.EXTERNAL, "pdf").contains("T-hli01"));
    }

    @Test
    @DisplayName("开关 ② 开着出的外部版（单份 + 合并件）开关一关：Word / PDF 下载、可用性判据一律不给；后台按新设置重出后恢复且那一格为空")
    void printedExternalIsNeverDeliveredOnceSwitchIsOff() {
        factory.switchOn = true;
        service.render(S, DocKinds.SAMPLE_QC, DocAudiences.EXTERNAL);
        service.render(S, DocKinds.MERGED, DocAudiences.EXTERNAL);
        service.render(S, DocKinds.SAMPLE_QC, DocAudiences.INTERNAL);
        assertTrue(text(DocKinds.MERGED, DocAudiences.EXTERNAL, "docx").contains("内部编号=T-hli01"), "合并件里的样本质控表也印了");
        assertEquals("Y", rows.header(S, DocKinds.MERGED, DocAudiences.EXTERNAL).getShowInternalNo(), "合并件记「印了」");

        factory.switchOn = false;   // 关开关，还没来得及重出
        for (String kind : List.of(DocKinds.SAMPLE_QC, DocKinds.MERGED)) {
            DocFile header = rows.header(S, kind, DocAudiences.EXTERNAL);
            assertTrue(rows.completeSet(S, kind, DocAudiences.EXTERNAL, header), "产物本身是齐的 —— 挡住它的只有开关这一道");
            assertEquals(DocRenderService.Delivery.BLOCKED, service.delivery(S, kind, DocAudiences.EXTERNAL, header, false));
            for (String format : List.of("docx", "pdf")) {
                ServiceException e = assertThrows(ServiceException.class,
                    () -> service.download(S, kind, format, DocAudiences.EXTERNAL), kind + " " + format + " 不许下发");
                assertEquals(400, e.getCode());
                assertTrue(e.getMessage().contains("按新设置重新生成"), e.getMessage());
            }
        }
        assertEquals(DocRenderService.Delivery.OK, service.delivery(S, DocKinds.SAMPLE_QC, DocAudiences.INTERNAL,
            rows.header(S, DocKinds.SAMPLE_QC, DocAudiences.INTERNAL), true), "内部版不受开关影响");
        assertFalse(kicked.isEmpty() && refreshed.isEmpty(), "读到时已在后台排了重出");

        drain();
        for (String kind : List.of(DocKinds.SAMPLE_QC, DocKinds.MERGED)) {
            DocFile header = rows.header(S, kind, DocAudiences.EXTERNAL);
            assertEquals("N", header.getShowInternalNo(), kind + " 按新设置重出");
            assertEquals(DocRenderService.Delivery.OK, service.delivery(S, kind, DocAudiences.EXTERNAL, header, false));
            assertFalse(text(kind, DocAudiences.EXTERNAL, "docx").contains("T-hli01"), kind + " Word 里不再有内部编号");
            assertFalse(text(kind, DocAudiences.EXTERNAL, "pdf").contains("T-hli01"), kind + " PDF 里不再有内部编号");
        }
    }

    @Test
    @DisplayName("开关 ③ 竞态：模型按「开」组装好、渲染途中有人关了开关 → 落下的那一版记「印了」，照样发不出去，读到时按新设置重出")
    void switchTurnedOffDuringRenderIsStillBlocked() {
        factory.switchOn = true;
        renderer.duringRender = () -> factory.switchOn = false;
        DocRenderVo vo = service.render(S, DocKinds.SAMPLE_QC, DocAudiences.EXTERNAL);
        assertEquals(DocArtifactRows.STATUS_DONE, vo.getStatus());
        DocFile header = rows.header(S, DocKinds.SAMPLE_QC, DocAudiences.EXTERNAL);
        assertEquals("Y", header.getShowInternalNo(), "这一版确实是按开着出的");
        assertThrows(ServiceException.class, () -> service.download(S, DocKinds.SAMPLE_QC, "docx", DocAudiences.EXTERNAL),
            "开关已关：印了编号的那一版不许发出去");

        drain();
        assertFalse(text(DocKinds.SAMPLE_QC, DocAudiences.EXTERNAL, "docx").contains("T-hli01"));
        assertNotNull(service.download(S, DocKinds.SAMPLE_QC, "docx", DocAudiences.EXTERNAL).getUrl());
    }

    @Test
    @DisplayName("开关 ④ 打开：关着时出的外部版（留空）照给不断档、标「按旧设置」；参数一改就扫一遍外部版，逐份按新设置重出，没有这一格的文档不动")
    void switchOnRefreshesWithoutHidingDocs() {
        factory.switchOn = false;
        for (String kind : List.of(DocKinds.SAMPLE_QC, DocKinds.ORGANOID_QC, DocKinds.MERGED)) {
            service.render(S, kind, DocAudiences.EXTERNAL);
        }
        Long organoidOss = rows.header(S, DocKinds.ORGANOID_QC, DocAudiences.EXTERNAL).getOssId();

        factory.switchOn = true;
        DocFile header = rows.header(S, DocKinds.SAMPLE_QC, DocAudiences.EXTERNAL);
        assertEquals(DocRenderService.Delivery.OUTDATED, service.delivery(S, DocKinds.SAMPLE_QC, DocAudiences.EXTERNAL, header, false),
            "留空的那一版给出去无妨：照给，但它是按旧设置出的");
        assertNotNull(service.download(S, DocKinds.SAMPLE_QC, "docx", DocAudiences.EXTERNAL).getUrl(), "重出前不断档");
        assertEquals(DocRenderService.Delivery.OK, service.delivery(S, DocKinds.ORGANOID_QC, DocAudiences.EXTERNAL,
            rows.header(S, DocKinds.ORGANOID_QC, DocAudiences.EXTERNAL), false), "类器官质控表没有这一格：不算过期");

        service.requestExternalRefresh(true);   // 参数设置页一改（DocInternalNoSwitchAspect）
        drain();
        assertTrue(text(DocKinds.SAMPLE_QC, DocAudiences.EXTERNAL, "docx").contains("内部编号=T-hli01"));
        assertTrue(text(DocKinds.MERGED, DocAudiences.EXTERNAL, "docx").contains("内部编号=T-hli01"), "合并件也按新设置重出");
        assertEquals(organoidOss, rows.header(S, DocKinds.ORGANOID_QC, DocAudiences.EXTERNAL).getOssId(), "类器官质控表没重出");
        assertEquals(DocRenderService.Delivery.OK, service.delivery(S, DocKinds.MERGED, DocAudiences.EXTERNAL,
            rows.header(S, DocKinds.MERGED, DocAudiences.EXTERNAL), false));
    }

    @Test
    @DisplayName("开关 ⑤ 扫描只挑按旧设置出的已完成文档：草稿不替它重出；同一份只排一次")
    void scanQueuesOnlyPublishedOutdatedOnesOnce() {
        factory.switchOn = false;
        service.render(S, DocKinds.SAMPLE_QC, DocAudiences.EXTERNAL);
        service.render(S, DocKinds.ORGANOID_QC, DocAudiences.EXTERNAL);
        factory.switchOn = true;
        assertEquals(1, service.scanExternal(), "只有样本质控表按旧设置出（类器官质控表没有这一格）");
        assertEquals(0, service.scanExternal(), "已经在队里：不重复排");
        drain();
        assertEquals(0, service.scanExternal(), "重出之后没有要排的了");

        factory.published.remove(DocKinds.SAMPLE_QC);   // 改回草稿
        factory.switchOn = false;
        assertEquals(0, service.scanExternal(), "草稿不对外，不替它重出（下次完成并同步按当时的设置出）");
    }

    @Test
    @DisplayName("模板升级：已完成文档照给旧版、同时在后台按新模板重出（不用人工逐份「重新生成」）")
    void templateUpgradeRefreshesInBackground() {
        service.render(S, DocKinds.SAMPLE_QC, DocAudiences.INTERNAL);
        rows.simulateOldTemplate(S, DocKinds.SAMPLE_QC, DocAudiences.INTERNAL);
        DocFile header = rows.header(S, DocKinds.SAMPLE_QC, DocAudiences.INTERNAL);
        assertTrue(rows.completeSet(S, DocKinds.SAMPLE_QC, DocAudiences.INTERNAL, header));
        assertEquals(DocRenderService.Delivery.OUTDATED,
            service.delivery(S, DocKinds.SAMPLE_QC, DocAudiences.INTERNAL, header, false), "旧模板出的：照给但过期");
        assertNotNull(service.download(S, DocKinds.SAMPLE_QC, "docx", DocAudiences.INTERNAL).getUrl(), "重出前不断档");
        drain();
        header = rows.header(S, DocKinds.SAMPLE_QC, DocAudiences.INTERNAL);
        assertEquals(DocTemplate.version(), header.getTemplateVersion(), "按新模板重出了");
        assertEquals(DocRenderService.Delivery.OK, service.delivery(S, DocKinds.SAMPLE_QC, DocAudiences.INTERNAL, header, false));
    }

    // ══════════════════════════════════════════════════════════════════════
    // 工具
    // ══════════════════════════════════════════════════════════════════════

    private String downloadText(String format) {
        return text(DocKinds.MERGED, DocAudiences.INTERNAL, format);
    }

    private String text(String kind, String audience, String format) {
        DocDownloadVo vo = service.download(S, kind, format, audience);
        Long ossId = Long.valueOf(vo.getUrl().substring("oss://".length()));
        return new String(store.objects.get(ossId), StandardCharsets.UTF_8);
    }

    /** 把后台排上的活（补渲染 + 按新设置重出的队列）跑到空为止（跑的过程中可能再排新的）。 */
    private void drain() {
        for (int guard = 0; guard < 100 && !(kicked.isEmpty() && refreshed.isEmpty()); guard++) {
            List<Runnable> batch = new ArrayList<>(kicked);
            batch.addAll(refreshed);
            kicked.clear();
            refreshed.clear();
            batch.forEach(Runnable::run);
        }
    }

    /**
     * 成员与内容的替身：published 里有谁，合并件就拼谁；{@code switchOn} = 系统参数 lqg.ext.show-internal-no。
     * 样本质控表的正文带上「内部编号」一格印出来的字（{@code 内部编号=T-hli01} 或空），下载的字节里看得见。
     */
    static final class FakeFactory extends DocRenderModelFactory {
        final Set<String> published = new LinkedHashSet<>();
        final Map<String, List<Long>> images = new HashMap<>();
        boolean switchOn;
        /** 读了几次开关（钉「合并件的三个成员只读一次」）。 */
        int switchReads;

        FakeFactory() {
            super(null, null, null, null, null, null, null, null, null);
        }

        @Override
        public Sample requireSample(Long sampleId) {
            Sample sample = new Sample();
            sample.setId(sampleId);
            return sample;
        }

        @Override
        public boolean showsInternalNo(String audience) {
            switchReads++;
            return DocAudiences.isInternal(audience) || switchOn;
        }

        @Override
        public boolean isPublished(Long sampleId, String docKind) {
            return published.contains(docKind);
        }

        @Override
        public DocRenderModel single(Long sampleId, String docKind, String audience, boolean showInternalNo) {
            boolean shown = showInternalNo && DocKinds.hasInternalNoCell(docKind);
            DocRenderModel model = new DocRenderModel(docKind, audience, DocTemplate.version())
                .internalNoShown(shown)
                .text("body", docKind + (DocKinds.hasInternalNoCell(docKind) ? "{内部编号=" + (shown ? "T-hli01" : "") + "}" : ""))
                .part("doc_status=" + (published.contains(docKind) ? "published" : "draft"));
            if (DocKinds.SAMPLE_QC.equals(docKind)) {
                model.imageSlot("orig", images.getOrDefault(docKind, List.of()));
            }
            return model;
        }

        @Override
        public List<DocRenderModel> mergedMembers(Long sampleId, String audience) {
            boolean show = showsInternalNo(audience);
            List<DocRenderModel> members = new ArrayList<>();
            for (String kind : DocKinds.MERGED_ORDER) {
                if (published.contains(kind)) {
                    members.add(single(sampleId, kind, audience, show));
                }
            }
            if (members.isEmpty()) {
                throw new ServiceException("这个样本还没有已完成的质控文档，合并件无从拼起", 400);
            }
            return members;
        }

        @Override
        public String displayName(Long sampleId, String docKind, String audience) {
            return DocKinds.label(docKind);
        }
    }

    /**
     * docx = 各成员的 docKind 拼起来；missingOssIds 里的图记为缺图。
     * 替身换的是「草稿」这一步（单份 render 与合并件 renderMerged 都经过它；没有嵌入附件，最后一步原样返回）。
     */
    static final class FakeRenderer extends DocxRenderer {
        final Set<Long> missingOssIds = new HashSet<>();
        Runnable duringMerge;

        FakeRenderer() {
            super(null);
        }

        /** 单份渲染时调它一次（模拟「渲染要几秒，期间有人切了开关」）。 */
        Runnable duringRender;

        @Override
        protected byte[] draft(DocRenderModel model, List<MissingImage> missing, List<DocOleEmbedder.Attachment> embeds) {
            if (duringRender != null) {
                Runnable r = duringRender;
                duringRender = null;
                r.run();
            }
            for (Map.Entry<String, List<Long>> slot : model.images().entrySet()) {
                for (int i = 0; i < slot.getValue().size(); i++) {
                    Long ossId = slot.getValue().get(i);
                    if (missingOssIds.contains(ossId)) {
                        missing.add(new MissingImage(model.getDocKind(), slot.getKey(), i + 1, ossId, "存储里读不到这个文件"));
                    }
                }
            }
            return ("[" + model.texts().getOrDefault("body", model.getDocKind()) + "]").getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public byte[] merge(List<byte[]> docs) {
            if (duringMerge != null) {
                duringMerge.run();
            }
            StringBuilder sb = new StringBuilder();
            for (byte[] doc : docs) {
                sb.append(new String(doc, StandardCharsets.UTF_8));
            }
            return sb.toString().getBytes(StandardCharsets.UTF_8);
        }
    }

    static final class FakePdf extends PdfConvertService {
        int calls;

        FakePdf() {
            super("http://fake-gotenberg", 60);
        }

        @Override
        public byte[] toPdf(byte[] docx, String fileName) {
            calls++;
            return ("%PDF-" + new String(docx, StandardCharsets.UTF_8)).getBytes(StandardCharsets.UTF_8);
        }
    }

    static final class FakeStore extends DocArtifactStore {
        final Map<Long, byte[]> objects = new HashMap<>();
        long seq = 100;

        FakeStore() {
            super(null);
        }

        private Long put(byte[] bytes) {
            long id = ++seq;
            objects.put(id, bytes);
            return id;
        }

        @Override
        public Long uploadDocx(Long sampleId, String docKind, String audience, String contentHash, byte[] bytes) {
            return put(bytes);
        }

        @Override
        public Long uploadPdf(Long sampleId, String docKind, String audience, String contentHash, byte[] bytes) {
            return put(bytes);
        }

        @Override
        public Long uploadPng(Long sampleId, String docKind, String audience, String contentHash, int pageNo, byte[] bytes) {
            return put(bytes);
        }

        @Override
        public String signedUrlOrFail(Long ossId) {
            return "oss://" + ossId;
        }
    }

    static final class FakePages extends PageImageService {
        private final DocArtifactRows rows;
        private final DocArtifactStore store;

        FakePages(DocArtifactRows rows, DocArtifactStore store) {
            super(rows, store);
            this.rows = rows;
            this.store = store;
        }

        @Override
        public int publish(Long sampleId, String docKind, String audience, String contentHash, String templateVersion, byte[] pdf) {
            DocFile row = rows.upsertPending(sampleId, docKind, audience, DocArtifactRows.FORMAT_PNG, 1, contentHash, templateVersion);
            Long ossId = store.uploadPng(sampleId, docKind, audience, contentHash, 1, pdf);
            rows.markDone(row.getId(), ossId, contentHash, templateVersion);
            return 1;
        }
    }

    /** t_lqg_doc_file 的内存替身（条件更新按 SQL 的语义实现）。 */
    static final class FakeRows extends DocArtifactRows {
        final Map<String, DocFile> table = new LinkedHashMap<>();
        long seq = 1;
        int writes;

        FakeRows() {
            super(null);
        }

        private static String key(Long s, String k, String a, String f, int p) {
            return s + "/" + k + "/" + a + "/" + f + "/" + p;
        }

        private DocFile byId(Long id) {
            return table.values().stream().filter(r -> r.getId().equals(id)).findFirst().orElse(null);
        }

        private static DocFile copy(DocFile r) {
            if (r == null) {
                return null;
            }
            DocFile c = new DocFile();
            c.setId(r.getId());
            c.setSampleId(r.getSampleId());
            c.setDocKind(r.getDocKind());
            c.setAudience(r.getAudience());
            c.setFileFormat(r.getFileFormat());
            c.setPageNo(r.getPageNo());
            c.setOssId(r.getOssId());
            c.setContentHash(r.getContentHash());
            c.setTemplateVersion(r.getTemplateVersion());
            c.setRenderStatus(r.getRenderStatus());
            c.setErrorMsg(r.getErrorMsg());
            c.setMissingImageCount(r.getMissingImageCount());
            c.setMissingImages(r.getMissingImages());
            c.setShowInternalNo(r.getShowInternalNo());
            return c;
        }

        void rewriteHeaderHash(Long s, String k, String a, String hash) {
            table.get(key(s, k, a, FORMAT_DOCX, 0)).setContentHash(hash);
        }

        @Override
        public DocFile find(Long s, String k, String a, String f, int p) {
            return copy(table.get(key(s, k, a, f, p)));
        }

        @Override
        public List<DocFile> pngPages(Long s, String k, String a, String hash) {
            return table.values().stream()
                .filter(r -> r.getSampleId().equals(s) && r.getDocKind().equals(k) && r.getAudience().equals(a)
                    && FORMAT_PNG.equals(r.getFileFormat()) && hash.equals(r.getContentHash())
                    && STATUS_DONE.equals(r.getRenderStatus()))
                .sorted(Comparator.comparing(DocFile::getPageNo))
                .map(FakeRows::copy)
                .toList();
        }

        @Override
        public DocFile upsertPending(Long s, String k, String a, String f, int p, String hash, String version) {
            writes++;
            DocFile row = table.get(key(s, k, a, f, p));
            if (row == null) {
                row = new DocFile();
                row.setId(seq++);
                row.setSampleId(s);
                row.setDocKind(k);
                row.setAudience(a);
                row.setFileFormat(f);
                row.setPageNo(p);
                table.put(key(s, k, a, f, p), row);
            }
            row.setContentHash(hash);
            row.setTemplateVersion(version);
            row.setRenderStatus(STATUS_PENDING);
            row.setErrorMsg(null);
            row.setMissingImageCount(0);
            row.setMissingImages(null);
            return copy(row);
        }

        @Override
        public void markDone(Long id, Long ossId, String hash, String version) {
            writes++;
            DocFile row = byId(id);
            row.setOssId(ossId);
            row.setContentHash(hash);
            row.setRenderStatus(STATUS_DONE);
            row.setErrorMsg(null);
        }

        @Override
        public boolean markHeaderDone(Long id, Long ossId, String hash, String version, int missingCount, String summary,
                                      boolean internalNoShown) {
            writes++;
            DocFile row = byId(id);
            if (row == null || !hash.equals(row.getContentHash()) || !STATUS_PENDING.equals(row.getRenderStatus())
                || row.getErrorMsg() != null) {
                return false;
            }
            row.setOssId(ossId);
            row.setRenderStatus(STATUS_DONE);
            row.setMissingImageCount(missingCount);
            row.setMissingImages(summary);
            row.setShowInternalNo(internalNoShown ? FLAG_YES : FLAG_NO);
            return true;
        }

        @Override
        public List<DocFile> externalDoneHeaders() {
            return table.values().stream()
                .filter(r -> DocAudiences.EXTERNAL.equals(r.getAudience()) && FORMAT_DOCX.equals(r.getFileFormat())
                    && r.getPageNo() == 0 && STATUS_DONE.equals(r.getRenderStatus()))
                .map(FakeRows::copy)
                .toList();
        }

        /** 模拟「升模板之前」落下的一版：这一份的全部产物行换成旧指纹 + 旧版本号（产物仍然齐全）。 */
        void simulateOldTemplate(Long s, String k, String a) {
            for (DocFile r : table.values()) {
                if (r.getSampleId().equals(s) && r.getDocKind().equals(k) && r.getAudience().equals(a)) {
                    r.setContentHash("old-" + r.getContentHash());
                    r.setTemplateVersion("0");
                }
            }
        }

        @Override
        public void markFailed(Long id, String reason) {
            writes++;
            DocFile row = byId(id);
            row.setRenderStatus(STATUS_FAILED);
            row.setErrorMsg(reason);
        }

        @Override
        public void markFailed(Long id, String reason, int missingCount, String summary) {
            markFailed(id, reason);
            DocFile row = byId(id);
            row.setMissingImageCount(missingCount);
            row.setMissingImages(summary);
        }

        @Override
        public int markMergedStale(Long sampleId) {
            writes++;
            int n = 0;
            for (DocFile row : table.values()) {
                if (row.getSampleId().equals(sampleId) && DocKinds.MERGED.equals(row.getDocKind())
                    && row.getPageNo() == 0
                    && (FORMAT_DOCX.equals(row.getFileFormat()) || FORMAT_PDF.equals(row.getFileFormat()))
                    && (STATUS_PENDING.equals(row.getRenderStatus()) || STATUS_DONE.equals(row.getRenderStatus()))) {
                    row.setRenderStatus(STATUS_PENDING);
                    row.setErrorMsg(STALE_MERGED_MSG);
                    n++;
                }
            }
            return n;
        }

        @Override
        public int softDeleteArtifacts(Long s, String k, String a) {
            writes++;
            List<String> keys = new ArrayList<>();
            for (Map.Entry<String, DocFile> e : table.entrySet()) {
                DocFile r = e.getValue();
                if (r.getSampleId().equals(s) && r.getDocKind().equals(k) && r.getAudience().equals(a)) {
                    keys.add(e.getKey());
                }
            }
            keys.forEach(table::remove);
            return keys.size();
        }

        @Override
        public int prunePagesAbove(Long s, String k, String a, int keep) {
            return 0;
        }
    }
}
