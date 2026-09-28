package org.dromara.lqg.doc.pdf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.dromara.lqg.doc.render.domain.DocFile;
import org.dromara.lqg.doc.render.mapper.DocFileMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * {@link DocArtifactRows} 的写库口径契约（独立验收 V04 / V23）—— 截下<b>真生成的 wrapper</b>断言，
 * 不读源码字符串。
 *
 * <ol>
 *   <li><b>失效 = 真失效</b>：{@link DocArtifactRows#markMergedStale} 把合并件 header / PDF 置回 pending，
 *       <b>不改写 content_hash</b>（旧版 markStale 改写指纹、状态仍 done，正是 V04 的病根）；</li>
 *   <li><b>在途渲染复活不了失效的一版</b>：{@link DocArtifactRows#markHeaderDone} 是条件更新 ——
 *       同指纹、仍是 pending、没有失效标记才落得上 done；</li>
 *   <li><b>产物齐不齐看三种产物的指纹</b>：{@link DocArtifactRows#completeSet} 对「header 被改过指纹、
 *       PDF 与页面图还是旧指纹」的撕裂一版给 false；</li>
 *   <li><b>缺图记账</b>：新一版开始时缺图数清零，落 done / failed 时带上缺图数与明细。</li>
 * </ol>
 *
 * @author 独立验收 V04 / V23 修复
 */
class DocArtifactRowsContractTest {

    private static final long SAMPLE = 9000001001L;

    @BeforeAll
    static void initLambdaCache() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), DocFile.class);
    }

    /** 记录 update / selectOne / selectList 的假 mapper。 */
    private static final class Capture {
        final List<Wrapper<DocFile>> updates = new ArrayList<>();
        DocFile selectOneResult;
        List<DocFile> selectListResult = List.of();
        int updateResult = 1;
    }

    @SuppressWarnings("unchecked")
    private static DocArtifactRows rows(Capture capture) {
        DocFileMapper mapper = (DocFileMapper) Proxy.newProxyInstance(
            DocArtifactRowsContractTest.class.getClassLoader(),
            new Class<?>[]{DocFileMapper.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "update" -> {
                    capture.updates.add((Wrapper<DocFile>) args[1]);
                    yield capture.updateResult;
                }
                case "selectOne" -> capture.selectOneResult;
                case "selectList" -> capture.selectListResult;
                case "toString" -> "stub-DocFileMapper";
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == args[0];
                default -> throw new UnsupportedOperationException("本测试没有预期这次调用：" + method.getName());
            });
        return new DocArtifactRows(mapper);
    }

    private static DocFile row(String format, int pageNo, String status, String hash, Long ossId) {
        DocFile row = new DocFile();
        row.setId((long) (format.hashCode() + pageNo));
        row.setSampleId(SAMPLE);
        row.setDocKind("merged");
        row.setAudience("internal");
        row.setFileFormat(format);
        row.setPageNo(pageNo);
        row.setRenderStatus(status);
        row.setContentHash(hash);
        row.setOssId(ossId);
        return row;
    }

    private static String set(Wrapper<DocFile> wrapper) {
        return ((LambdaUpdateWrapper<DocFile>) wrapper).getSqlSet();
    }

    private static String where(Wrapper<DocFile> wrapper) {
        return ((LambdaUpdateWrapper<DocFile>) wrapper).getTargetSql();
    }

    private static Map<String, Object> params(Wrapper<DocFile> wrapper) {
        return ((LambdaUpdateWrapper<DocFile>) wrapper).getParamNameValuePairs();
    }

    @Test
    @DisplayName("① markMergedStale：合并件 header / PDF 置回 pending + 失效标记，指纹一个字节都不改")
    void mergedStaleIsARealInvalidation() {
        Capture capture = new Capture();
        rows(capture).markMergedStale(SAMPLE);
        assertEquals(1, capture.updates.size());
        Wrapper<DocFile> w = capture.updates.get(0);
        String set = set(w);
        String where = where(w);
        assertTrue(set.contains("render_status"), "必须改状态：" + set);
        assertTrue(set.contains("error_msg"), "必须写失效标记：" + set);
        assertFalse(set.contains("content_hash"),
            "★ 不许改写指纹（V04 的病根：header 被写成新指纹、状态仍 done → 永远命中缓存）：" + set);
        assertFalse(set.contains("oss_id"), "旧产物行保留，不动 oss_id：" + set);
        assertTrue(where.contains("sample_id") && where.contains("doc_kind") && where.contains("file_format")
            && where.contains("page_no") && where.contains("render_status"), where);
        Map<String, Object> p = params(w);
        assertTrue(p.containsValue("merged"), "只动合并件：" + p);
        assertTrue(p.containsValue(DocArtifactRows.STATUS_PENDING), p.toString());
        assertTrue(p.containsValue(DocArtifactRows.STALE_MERGED_MSG), p.toString());
        assertTrue(p.containsValue(DocArtifactRows.FORMAT_DOCX) && p.containsValue(DocArtifactRows.FORMAT_PDF),
            "header（docx）与 PDF 行一起置回：" + p);
        assertFalse(p.containsValue(DocArtifactRows.STATUS_FAILED),
            "failed 的行不动（原因要留着给人看）：" + p);
    }

    @Test
    @DisplayName("② markHeaderDone 是条件更新：同指纹 + 仍 pending + 没有失效标记；改不到就回 false")
    void headerDoneIsConditional() {
        Capture capture = new Capture();
        DocArtifactRows rows = rows(capture);
        assertTrue(rows.markHeaderDone(1L, 11L, "h-new", "3", 2, "样本质控表·收样原始情况 第 1 张", true));
        Wrapper<DocFile> w = capture.updates.get(0);
        String where = where(w);
        assertTrue(where.contains("content_hash"), "必须以「还是这一版的指纹」为前提：" + where);
        assertTrue(where.contains("render_status"), "必须以「仍是 pending」为前提：" + where);
        assertTrue(where.contains("error_msg IS NULL"), "失效标记一落就不许再落 done：" + where);
        String set = set(w);
        assertTrue(set.contains("missing_image_count") && set.contains("missing_images"), "done 时带缺图记账：" + set);
        assertTrue(params(w).containsValue(2), "缺图数原样落库：" + params(w));
        assertTrue(set.contains("show_internal_no") && params(w).containsValue(DocArtifactRows.FLAG_YES),
            "「内部编号」一格印没印随这一版一起落下（发出去之前按它核开关）：" + set + " " + params(w));

        capture.updateResult = 0;
        assertFalse(rows.markHeaderDone(1L, 11L, "h-old", "3", 0, null, false),
            "条件不成立（渲染期间被判过期）→ 回 false，调用方按新成员重出");
    }

    @Test
    @DisplayName("③ 新一版开始（upsertPending 已有行）：状态 pending、清失败原因、缺图数清零")
    void upsertPendingResetsMissing() {
        Capture capture = new Capture();
        capture.selectOneResult = row("docx", 0, DocArtifactRows.STATUS_DONE, "h-old", 10L);
        DocFile pending = rows(capture).upsertPending(SAMPLE, "merged", "internal", "docx", 0, "h-new", "3");
        assertEquals(DocArtifactRows.STATUS_PENDING, pending.getRenderStatus());
        assertEquals(0, pending.getMissingImageCount());
        String set = set(capture.updates.get(0));
        assertTrue(set.contains("missing_image_count") && set.contains("missing_images") && set.contains("error_msg"), set);
    }

    @Test
    @DisplayName("④ completeSet：三种产物同指纹才算齐；「header 改了指纹、PDF 与页面图还是旧的」是撕裂，不算")
    void completeSetLooksAtTheArtifacts() {
        DocFile header = row("docx", 0, DocArtifactRows.STATUS_DONE, "h2", 10L);

        Capture ok = new Capture();
        ok.selectOneResult = row("pdf", 0, DocArtifactRows.STATUS_DONE, "h2", 11L);
        ok.selectListResult = List.of(row("png", 1, DocArtifactRows.STATUS_DONE, "h2", 12L));
        assertTrue(rows(ok).completeSet(SAMPLE, "merged", "internal", header));

        // V04 的撕裂形态：header 指纹被改写成 h2，PDF 还是 h3
        Capture torn = new Capture();
        torn.selectOneResult = row("pdf", 0, DocArtifactRows.STATUS_DONE, "h3", 11L);
        torn.selectListResult = List.of(row("png", 1, DocArtifactRows.STATUS_DONE, "h2", 12L));
        assertFalse(rows(torn).completeSet(SAMPLE, "merged", "internal", header), "PDF 与 header 不同指纹 = 撕裂");

        // 同指纹的页面图一张都没有（「已完成却 0 页」）
        Capture noPages = new Capture();
        noPages.selectOneResult = row("pdf", 0, DocArtifactRows.STATUS_DONE, "h2", 11L);
        noPages.selectListResult = List.of();
        assertFalse(rows(noPages).completeSet(SAMPLE, "merged", "internal", header), "0 页不算齐");

        // header 失效（pending）
        Capture pending = new Capture();
        pending.selectOneResult = row("pdf", 0, DocArtifactRows.STATUS_DONE, "h2", 11L);
        pending.selectListResult = List.of(row("png", 1, DocArtifactRows.STATUS_DONE, "h2", 12L));
        DocFile stale = row("docx", 0, DocArtifactRows.STATUS_PENDING, "h2", 10L);
        assertFalse(rows(pending).completeSet(SAMPLE, "merged", "internal", stale), "失效的一版不给");
        assertFalse(rows(pending).completeSet(SAMPLE, "merged", "internal", null));
    }

    @Test
    @DisplayName("⑤ header 失败时连同缺图一起记（工作台清单要显示缺了哪几张）")
    void failedHeaderKeepsMissingDetail() {
        Capture capture = new Capture();
        rows(capture).markFailed(1L, "外部版有 1 张图取不到", 1, "样本质控表·收样原始情况 第 1 张（存储里读不到这个文件）");
        Wrapper<DocFile> w = capture.updates.get(0);
        String set = set(w);
        assertTrue(set.contains("render_status") && set.contains("error_msg")
            && set.contains("missing_image_count") && set.contains("missing_images"), set);
        assertTrue(params(w).containsValue(DocArtifactRows.STATUS_FAILED));
        assertNotNull(params(w).values().stream().filter(v -> String.valueOf(v).contains("收样原始情况")).findAny()
            .orElse(null), "缺图明细原样落库：" + params(w));
    }
}
