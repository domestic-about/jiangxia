package org.dromara.lqg.sys.home;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.dromara.lqg.doc.render.domain.DocFile;
import org.dromara.lqg.doc.render.mapper.DocFileMapper;
import org.dromara.lqg.sample.domain.Sample;
import org.dromara.lqg.sample.mapper.SampleMapper;
import org.dromara.lqg.sys.home.domain.vo.HomeRenderIssueVo;
import org.dromara.lqg.sys.home.service.HomeCounterService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.Date;
import java.util.List;

/**
 * 首页「渲染失败与缺图」清单的组装（独立验收 V29）：一行一份文档的一个版本，
 * failed → 渲染失败（带原因）、done + 缺图 → 内部版缺图（带明细）；样本已删的不列。
 *
 * @author 独立验收 V29 修复
 */
class HomeRenderIssuesTest {

    private static DocFile row(long sampleId, String kind, String aud, String status, int missing, String err, String detail) {
        DocFile f = new DocFile();
        f.setSampleId(sampleId);
        f.setDocKind(kind);
        f.setAudience(aud);
        f.setFileFormat("docx");
        f.setPageNo(0);
        f.setRenderStatus(status);
        f.setMissingImageCount(missing);
        f.setErrorMsg(err);
        f.setMissingImages(detail);
        f.setUpdateTime(new Date(1_800_000_000_000L));
        return f;
    }

    @Test
    @DisplayName("失败 / 缺图两类问题分清；样本主档查不到（已删）的行不列；限 200 行")
    void mapsIssuesAndSkipsDeletedSamples() {
        List<DocFile> rows = List.of(
            row(1L, "sample_qc", "external", "failed", 3, "外部版有 3 张图取不到", "…"),
            row(1L, "sample_qc", "internal", "done", 3, null, "样本质控表·收样原始情况 第 1 张"),
            row(2L, "merged", "internal", "failed", 0, "转换服务不可用", null));
        int[] limit = {0};
        DocFileMapper docFileMapper = (DocFileMapper) Proxy.newProxyInstance(getClass().getClassLoader(),
            new Class<?>[]{DocFileMapper.class}, (proxy, method, args) -> switch (method.getName()) {
                case "selectRenderIssues" -> {
                    limit[0] = (Integer) args[0];
                    yield rows;
                }
                case "toString" -> "stub";
                case "hashCode" -> 1;
                case "equals" -> false;
                default -> throw new UnsupportedOperationException(method.getName());
            });
        SampleMapper sampleMapper = (SampleMapper) Proxy.newProxyInstance(getClass().getClassLoader(),
            new Class<?>[]{SampleMapper.class}, (proxy, method, args) -> switch (method.getName()) {
                case "selectById" -> {
                    if (Long.valueOf(2L).equals(args[0])) {
                        yield null;  // 样本已软删：@TableLogic 查不到
                    }
                    Sample s = new Sample();
                    s.setId(1L);
                    s.setInternalNo("T-hli01");
                    s.setSubmitNo("SJ90000001");
                    s.setSourceUnitName("A 医院");
                    yield s;
                }
                case "toString" -> "stub";
                case "hashCode" -> 2;
                case "equals" -> false;
                default -> throw new UnsupportedOperationException(method.getName());
            });
        HomeCounterService service = new HomeCounterService(sampleMapper, null, null, docFileMapper, null);
        List<HomeRenderIssueVo> out = service.renderIssues();
        assertEquals(HomeCounterService.RENDER_ISSUE_LIMIT, limit[0]);
        assertEquals(2, out.size(), "已删样本的那一行不列");
        assertEquals("failed", out.get(0).getIssue());
        assertEquals("外部版有 3 张图取不到", out.get(0).getErrorMsg());
        assertEquals("missing_images", out.get(1).getIssue());
        assertEquals(3, out.get(1).getMissingImageCount());
        assertEquals("T-hli01", out.get(1).getInternalNo());
        assertEquals("internal", out.get(1).getAudience());
    }
}
