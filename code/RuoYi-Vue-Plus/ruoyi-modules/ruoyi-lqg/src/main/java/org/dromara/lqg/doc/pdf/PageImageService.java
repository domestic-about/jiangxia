package org.dromara.lqg.doc.pdf;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.lqg.doc.render.domain.DocFile;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 页面图片的落地（FLOW:F-DOC-01.step4）：PDF 每页 150 DPI 出一张 PNG、存 OSS、落
 * {@code file_format='png'} 的行，{@code page_no} **从 1 起**；页数变少时把多余的旧页行**软删**。
 *
 * <p>★★ <b>和 PDF/docx 同一个指纹</b>：这里写的每一行都带 {@code contentHash}
 * （= 那个 {@code DocRenderModel} 算出来的指纹）。于是「docx 重出了、页面图还是旧的」在缓存层
 * 就没法发生：取图接口只认 {@code content_hash = 当前指纹} 的行（见
 * {@link DocArtifactRows#pngPages}）。这正是本票 accept 2 第 5/6 段要防的事故形态。
 *
 * <p>★ 这一层**不吞异常**：出图失败要往上传，让整个 {@code (docKind, audience)} 变 failed。
 *
 * @author DOC-PDF-001
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PageImageService {

    /** ticket §2 / F-DOC-01.step4：150 DPI。 */
    public static final int DPI = PdfPageRasterizer.DEFAULT_DPI;

    private final DocArtifactRows rows;
    private final DocArtifactStore store;

    /**
     * 出图 + 落库（由 {@code DocRenderService} 的流水线调用，失败即整份 failed）。
     *
     * @return 出了几页
     */
    public int publish(Long sampleId, String docKind, String audience,
                       String contentHash, String templateVersion, byte[] pdf) {
        List<byte[]> pages = PdfPageRasterizer.toPng(pdf, DPI);
        for (int index = 0; index < pages.size(); index++) {
            int pageNo = index + 1;
            byte[] png = pages.get(index);
            DocFile row = rows.upsertPending(sampleId, docKind, audience,
                DocArtifactRows.FORMAT_PNG, pageNo, contentHash, templateVersion);
            Long ossId = store.uploadPng(sampleId, docKind, audience, contentHash, pageNo, png);
            rows.markDone(row.getId(), ossId, contentHash, templateVersion);
        }
        // 页数变少 → 多出来的旧页行软删（旧产物保留在 OSS 里，但行不再被当成最新）
        int pruned = rows.prunePagesAbove(sampleId, docKind, audience, pages.size());
        if (pruned > 0) {
            log.info("页面图片变少：sampleId={} docKind={} audience={} 现在 {} 页，软删 {} 行旧页",
                sampleId, docKind, audience, pages.size(), pruned);
        }
        log.info("页面图片完成：sampleId={} docKind={} audience={} {} 页 @ {} DPI",
            sampleId, docKind, audience, pages.size(), DPI);
        return pages.size();
    }
}
