package org.dromara.lqg.doc.pdf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.dromara.lqg.doc.pdf.domain.vo.DocPagesAttachmentVo;
import org.dromara.lqg.doc.pdf.domain.vo.DocPagesImageVo;
import org.dromara.lqg.doc.pdf.domain.vo.DocPagesVo;
import org.dromara.lqg.doc.render.DocAudiences;
import org.dromara.lqg.doc.render.DocKinds;
import org.dromara.lqg.doc.render.DocRenderModelFactory;
import org.dromara.lqg.doc.render.domain.DocFile;
import org.dromara.lqg.doc.render.service.DocRenderService;
import org.dromara.lqg.qc.domain.DocAttachment;
import org.dromara.lqg.qc.domain.DocImage;
import org.dromara.lqg.qc.domain.QcOrganoidDoc;
import org.dromara.lqg.qc.domain.QcSampleDoc;
import org.dromara.lqg.qc.domain.QcScoreDoc;
import org.dromara.lqg.qc.mapper.DocAttachmentMapper;
import org.dromara.lqg.qc.mapper.DocImageMapper;
import org.dromara.lqg.sample.domain.Sample;
import org.dromara.system.domain.SysOss;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 预览接口的「图片与附件」口径（独立验收 V24 / G21）—— 真的 {@link DocPagesService}，产物表、OSS、质控表是替身。
 *
 * <ol>
 *   <li><b>外部咽喉</b>：外部版只签本样本、已完成成员的对象；图片 / 附件若指向渲染产物目录，只许是本样本的外部版
 *       —— 误挂的内部版产物、别的样本的产物一律不签发；页面图对象键不是本样本外部版的也不给；</li>
 *   <li><b>合并件只带已完成成员</b>：草稿那份的图与附件不混进来（内外部都一样）；</li>
 *   <li><b>活率附件并进附件列表</b>（G21）：排在最前，与通用附件同一个对象时只列一次。</li>
 *   <li><b>内部编号开关那一道</b>（G 批 C 组）：外部版印了内部编号而开关已关 → 按「生成中」报、一页不给；
 *       按旧设置出的一版照给页面图并带 {@code outdated=true}。</li>
 * </ol>
 *
 * @author 独立验收 V24 / G21 修复 · G 批 C 组
 */
class DocPagesServiceTest {

    private static final long S = 9000001005L;
    private static final long SAMPLE_DOC = 51L;
    private static final long ORGANOID_DOC = 52L;

    /** ossId → 对象键 */
    private final Map<Long, String> objects = new HashMap<>();
    private final Map<String, List<DocImage>> imagesByType = new HashMap<>();
    private final Map<String, List<DocAttachment>> attachmentsByType = new HashMap<>();
    private final Map<String, String> status = new HashMap<>();
    /** 渲染编排替身的「发不发得出去」答案（真实判据在 DocRenderIntegrityTest 里钉）。 */
    private DocRenderService.Delivery delivery = DocRenderService.Delivery.OK;
    /** true = 这一份从没生成过（header 行不存在） */
    private boolean neverRendered;
    private DocPagesService service;

    @BeforeAll
    static void initLambdaCache() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, DocImage.class);
        TableInfoHelper.initTableInfo(assistant, DocAttachment.class);
    }

    @BeforeEach
    void setUp() {
        // 上传侧的普通对象
        objects.put(1L, "2026/09/23/orig.png");
        objects.put(2L, "2026/09/23/orig-preview.jpg");
        objects.put(3L, "2026/09/23/viability.pdf");
        objects.put(4L, "2026/09/23/general.pdf");
        objects.put(5L, "2026/09/23/draft-image.png");
        objects.put(6L, "2026/09/23/draft-att.pdf");
        // 渲染产物
        objects.put(100L, "lqg/doc/" + S + "/sample_qc/external/abc-p1.png");
        objects.put(101L, "lqg/doc/" + S + "/sample_qc/internal/abc.docx");
        objects.put(102L, "lqg/doc/9000001004/sample_qc/external/def.pdf");
        objects.put(103L, "lqg/doc/" + S + "/merged/external/ghi-p1.png");
        objects.put(104L, "lqg/doc/" + S + "/merged/internal/jkl-p1.png");

        imagesByType.put("sample_qc", List.of(image(11, "sample_qc", SAMPLE_DOC, "observe", 1L, 1L, 1),
            image(12, "sample_qc", SAMPLE_DOC, "orig", 1L, 2L, 1)));
        imagesByType.put("organoid_qc", List.of(image(13, "organoid_qc", ORGANOID_DOC, "organoid_observe", 5L, 5L, 1)));
        attachmentsByType.put("sample_qc", List.of(
            attachment(21, "sample_qc", SAMPLE_DOC, 3L, "活率报告（通用附件里也挂了一份）.pdf"),
            attachment(22, "sample_qc", SAMPLE_DOC, 4L, "补充说明.pdf"),
            attachment(23, "sample_qc", SAMPLE_DOC, 101L, "误挂的内部版.docx"),
            attachment(24, "sample_qc", SAMPLE_DOC, 102L, "误挂的别的样本.pdf")));
        attachmentsByType.put("organoid_qc", List.of(attachment(25, "organoid_qc", ORGANOID_DOC, 6L, "草稿附件.pdf")));
        status.put("sample_qc", "published");
        status.put("organoid_qc", "draft");
        status.put("organoid_score", null);

        delivery = DocRenderService.Delivery.OK;
        neverRendered = false;
        service = new DocPagesService(new FakeRows(), new FakeStore(), new FakeFactory(), imageMapper(), attachmentMapper(),
            new FakeRender());
    }

    @Test
    @DisplayName("内部编号开关：外部版印了编号而开关已关 → 「生成中」、一页不给；按旧设置出的 → 照给页面图并标 outdated")
    void internalNoSwitchGatesPages() {
        delivery = DocRenderService.Delivery.BLOCKED;
        DocPagesVo blocked = service.pages(S, DocKinds.SAMPLE_QC, DocAudiences.EXTERNAL);
        assertEquals("pending", blocked.getStatus());
        assertTrue(blocked.getPages().isEmpty(), "印了内部编号的那一版，一页都不给");
        assertTrue(blocked.isOutdated());

        delivery = DocRenderService.Delivery.OUTDATED;
        DocPagesVo outdated = service.pages(S, DocKinds.SAMPLE_QC, DocAudiences.EXTERNAL);
        assertEquals("done", outdated.getStatus());
        assertEquals(1, outdated.getPages().size(), "给出去无妨的旧一版照给，不断档");
        assertTrue(outdated.isOutdated(), "工作台据此提示「正在按新设置重新生成」");

        delivery = DocRenderService.Delivery.OK;
        DocPagesVo ok = service.pages(S, DocKinds.SAMPLE_QC, DocAudiences.EXTERNAL);
        assertFalse(ok.isOutdated());
        assertTrue(ok.isInternalNoShown(), "header 行记的「印了」如实带给工作台");
    }

    @Test
    @DisplayName("从没生成过：回 status=none、一页不给，不是 400（工作台切页签 / 首次进入不该弹「还没生成」，网页工作台第 6 行）")
    void neverRenderedIsNoneNotError() {
        neverRendered = true;
        for (String kind : List.of(DocKinds.SAMPLE_QC, DocKinds.ORGANOID_SCORE, DocKinds.MERGED)) {
            DocPagesVo vo = service.pages(S, kind, DocAudiences.INTERNAL);
            assertEquals(DocPagesService.STATUS_NONE, vo.getStatus(), kind);
            assertTrue(vo.getPages().isEmpty(), kind + " 没生成过却给了页面图");
            assertEquals(kind, vo.getDocKind());
            assertEquals(DocAudiences.INTERNAL, vo.getAudience());
            assertFalse(vo.isOutdated());
        }
    }

    @Test
    @DisplayName("外部版：只签本样本的普通对象与本样本外部版产物；误挂的内部版 / 别的样本产物一律不签发")
    void externalSignsOnlyOwnObjects() {
        DocPagesVo vo = service.pages(S, DocKinds.SAMPLE_QC, DocAudiences.EXTERNAL);
        assertEquals("done", vo.getStatus());
        assertEquals(1, vo.getPages().size(), "页面图：本样本外部版那一页");
        List<String> names = vo.getAttachments().stream().map(DocPagesAttachmentVo::getFileName).toList();
        assertEquals(List.of("细胞活率报告.pdf", "补充说明.pdf"), names,
            "活率附件在最前、通用附件里重复的那份只列一次；误挂的两个产物不签发：" + names);
        for (DocPagesAttachmentVo a : vo.getAttachments()) {
            assertFalse(a.getUrl().contains("/internal/"), "外部链接里不许出现内部版对象：" + a.getUrl());
            assertFalse(a.getUrl().contains("9000001004"), "外部链接里不许出现别的样本：" + a.getUrl());
        }
    }

    @Test
    @DisplayName("内部版：附件原样全给（含误挂的产物，工作台要看得见才能摘掉），活率附件并进列表且不重复")
    void internalListsEverythingOnce() {
        DocPagesVo vo = service.pages(S, DocKinds.SAMPLE_QC, DocAudiences.INTERNAL);
        List<String> names = vo.getAttachments().stream().map(DocPagesAttachmentVo::getFileName).toList();
        assertEquals(List.of("细胞活率报告.pdf", "补充说明.pdf", "误挂的内部版.docx", "误挂的别的样本.pdf"), names);
    }

    @Test
    @DisplayName("图片按文档里图片位的先后（收样原始 → 样本观察），原图 url 与预览 previewUrl 各是各的")
    void imagesFollowDocumentOrder() {
        DocPagesVo vo = service.pages(S, DocKinds.SAMPLE_QC, DocAudiences.EXTERNAL);
        List<DocPagesImageVo> images = vo.getImages();
        assertEquals(2, images.size());
        assertTrue(path(images.get(0).getUrl()).endsWith("orig.png") && path(images.get(0).getPreviewUrl()).endsWith("orig-preview.jpg"),
            "第一张是收样原始情况（原图 PNG + 另存的预览 JPEG）：" + images.get(0));
        assertEquals(path(images.get(1).getUrl()), path(images.get(1).getPreviewUrl()), "第二张是样本观察情况（预览图就是原图）");
    }

    private static String path(String url) {
        return url == null ? "" : url.split("\\?")[0];
    }

    @Test
    @DisplayName("合并件：只带已完成成员的图与附件（草稿的类器官质控表一个都不带），内外部同一口径")
    void mergedCarriesPublishedMembersOnly() {
        for (String aud : DocAudiences.ALL) {
            DocPagesVo vo = service.pages(S, DocKinds.MERGED, aud);
            List<String> names = vo.getAttachments().stream().map(DocPagesAttachmentVo::getFileName).toList();
            assertFalse(names.contains("草稿附件.pdf"), aud + " 合并件混进了草稿的附件：" + names);
            assertTrue(vo.getImages().stream().noneMatch(i -> String.valueOf(i.getUrl()).contains("draft-image")),
                aud + " 合并件混进了草稿的图");
        }
        DocPagesVo ext = service.pages(S, DocKinds.MERGED, DocAudiences.EXTERNAL);
        assertEquals(1, ext.getPages().size(), "外部合并件：只给本样本外部版的页");
        assertTrue(ext.getPages().get(0).getUrl().contains("/merged/external/"));
    }

    @Test
    @DisplayName("对象键按段比：本样本 + 这个版本才算自己的产物")
    void artifactKeyGuardComparesSegments() {
        assertTrue(DocPagesService.isOwnArtifactKey("lqg/doc/" + S + "/sample_qc/external/x-p1.png", S, "external"));
        assertFalse(DocPagesService.isOwnArtifactKey("lqg/doc/" + S + "/sample_qc/internal/x.docx", S, "external"));
        assertFalse(DocPagesService.isOwnArtifactKey("lqg/doc/9000001004/sample_qc/external/x.pdf", S, "external"));
        assertFalse(DocPagesService.isOwnArtifactKey("lqg/doc/" + S + "0/sample_qc/external/x.pdf", S, "external"),
            "样本 id 前缀相同的另一个样本不算");
        assertFalse(DocPagesService.isOwnArtifactKey("2026/09/23/x.png", S, "external"));
        assertFalse(DocPagesService.isOwnArtifactKey(null, S, "external"));
    }

    // ══════════════════════════════════════════════════════════════════════
    // 替身
    // ══════════════════════════════════════════════════════════════════════

    private static DocImage image(long id, String docType, long docId, String slot, Long ossId, Long previewOssId, int sort) {
        DocImage image = new DocImage();
        image.setId(id);
        image.setDocType(docType);
        image.setDocId(docId);
        image.setSlot(slot);
        image.setOssId(ossId);
        image.setPreviewOssId(previewOssId);
        image.setSort(sort);
        return image;
    }

    private static DocAttachment attachment(long id, String docType, long docId, Long ossId, String name) {
        DocAttachment a = new DocAttachment();
        a.setId(id);
        a.setDocType(docType);
        a.setDocId(docId);
        a.setOssId(ossId);
        a.setFileName(name);
        a.setFileSize(10);
        a.setSort((int) id);
        return a;
    }

    /** 从 wrapper 的参数里认出查的是哪一份（doc_type 的取值就在参数里）。 */
    private static String docTypeOf(Object wrapper) {
        LambdaQueryWrapper<?> query = (LambdaQueryWrapper<?>) wrapper;
        query.getSqlSegment();  // 参数表是渲染 SQL 时才填的（EmbedQueryContractTest 同一个坑）
        Map<String, Object> params = query.getParamNameValuePairs();
        for (Object v : params.values()) {
            if (v instanceof String s && (s.equals("sample_qc") || s.equals("organoid_qc") || s.equals("organoid_score"))) {
                return s;
            }
        }
        throw new IllegalStateException("查询里没有 doc_type：" + params);
    }

    private DocImageMapper imageMapper() {
        return (DocImageMapper) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{DocImageMapper.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "selectList" -> new ArrayList<>(imagesByType.getOrDefault(docTypeOf(args[0]), List.of()));
                case "toString" -> "stub-DocImageMapper";
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == args[0];
                default -> throw new UnsupportedOperationException(method.getName());
            });
    }

    private DocAttachmentMapper attachmentMapper() {
        return (DocAttachmentMapper) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{DocAttachmentMapper.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "selectList" -> new ArrayList<>(attachmentsByType.getOrDefault(docTypeOf(args[0]), List.of()));
                case "toString" -> "stub-DocAttachmentMapper";
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == args[0];
                default -> throw new UnsupportedOperationException(method.getName());
            });
    }

    private final class FakeFactory extends DocRenderModelFactory {
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
        public QcSampleDoc sampleDoc(Long sampleId) {
            QcSampleDoc doc = new QcSampleDoc();
            doc.setId(SAMPLE_DOC);
            doc.setSampleId(sampleId);
            doc.setDocStatus(status.get("sample_qc"));
            doc.setViabilityOssId(3L);
            doc.setViabilityFileName("细胞活率报告.pdf");
            return doc;
        }

        @Override
        public QcOrganoidDoc organoidDoc(Long sampleId) {
            QcOrganoidDoc doc = new QcOrganoidDoc();
            doc.setId(ORGANOID_DOC);
            doc.setSampleId(sampleId);
            doc.setDocStatus(status.get("organoid_qc"));
            return doc;
        }

        @Override
        public QcScoreDoc scoreDoc(Long sampleId) {
            return null;
        }
    }

    private final class FakeStore extends DocArtifactStore {
        FakeStore() {
            super(null);
        }

        @Override
        public SysOss oss(Long ossId) {
            String key = objects.get(ossId);
            if (key == null) {
                return null;
            }
            SysOss oss = new SysOss();
            oss.setOssId(ossId);
            oss.setFileName(key);
            oss.setOriginalName(key.substring(key.lastIndexOf('/') + 1));
            return oss;
        }

        @Override
        public String signedUrl(Long ossId) {
            String key = objects.get(ossId);
            return key == null ? null : "https://oss.test/lqg-f2/" + key + "?X-Amz-Signature=t";
        }
    }

    /** 渲染编排替身：只回答「发不发得出去」，不真的渲染。 */
    private final class FakeRender extends DocRenderService {
        FakeRender() {
            super(null, null, null, null, null, null);
        }

        @Override
        public Delivery delivery(Long sampleId, String kind, String aud, DocFile header, boolean urgent) {
            return delivery;
        }

        @Override
        public boolean requestRender(Long sampleId, String docKind, String audience) {
            return false;
        }
    }

    /** 每一份都是「这一版齐全」：页面图各给一页（外部版 / 内部版的对象键各是各的）。 */
    private final class FakeRows extends DocArtifactRows {
        FakeRows() {
            super(null);
        }

        @Override
        public DocFile header(Long sampleId, String docKind, String audience) {
            return neverRendered ? null : super.header(sampleId, docKind, audience);
        }

        @Override
        public DocFile find(Long sampleId, String docKind, String audience, String fileFormat, int pageNo) {
            DocFile row = new DocFile();
            row.setSampleId(sampleId);
            row.setDocKind(docKind);
            row.setAudience(audience);
            row.setFileFormat(fileFormat);
            row.setPageNo(pageNo);
            row.setRenderStatus(STATUS_DONE);
            row.setContentHash("h");
            row.setOssId(fileFormat.equals(FORMAT_DOCX) ? 900L : 901L);
            row.setMissingImageCount(0);
            row.setShowInternalNo(FLAG_YES);
            return row;
        }

        @Override
        public List<DocFile> pngPages(Long sampleId, String docKind, String audience, String contentHash) {
            DocFile page = find(sampleId, docKind, audience, FORMAT_PNG, 1);
            long ossId;
            if (DocKinds.isMerged(docKind)) {
                ossId = DocAudiences.EXTERNAL.equals(audience) ? 103L : 104L;
            } else {
                // 单份：外部版给的是本样本外部版那页；内部版这里借用一个内部产物键
                ossId = DocAudiences.EXTERNAL.equals(audience) ? 100L : 101L;
            }
            page.setOssId(ossId);
            return List.of(page);
        }
    }
}
