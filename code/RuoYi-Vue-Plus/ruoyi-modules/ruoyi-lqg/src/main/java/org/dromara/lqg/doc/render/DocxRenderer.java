package org.dromara.lqg.doc.render;

import com.deepoove.poi.XWPFTemplate;
import com.deepoove.poi.config.Configure;
import com.deepoove.poi.data.PictureRenderData;
import com.deepoove.poi.data.Pictures;
import com.deepoove.poi.xwpf.NiceXWPFDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.usermodel.BreakType;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.dromara.common.core.exception.ServiceException;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * poi-tl 渲染器：拿 {@link DocRenderModel} 出 docx 字节（FLOW:F-DOC-01.step2）。
 *
 * <p>★ <b>文本</b>：{@code {{tag}}} 直接填；模板里没有数据的标签被 poi-tl 的
 * {@code ClearHandler} 清成空（所以**不会**残留占位符，accept 1 的 {@code --no-placeholder}
 * 断的就是这条）。所以模型里每个标签都必须有值，缺一个就是空格子而不是显式报错。
 *
 * <p>★ <b>图片</b>：每个图片位在模板里预留 <b>3 个独立 run</b>
 * （{@code {{@orig_img1}}} … {@code {{@orig_img3}}}，每位上限见
 * {@code QcDocRules.MAX_IMAGES_PER_SLOT}）。多张并排时**等分单元格宽度**：
 * 单元格宽度从模板 {@code tblGrid} 量出来（样本质控表图片位跨 3 列 = 4148 twips ≈ 276px，
 * 类器官质控表 = 4261 twips ≈ 284px），每张取 {@code 宽度 / 张数}。
 *
 * <p>★ 取不到字节的图 → 该标签留 {@code null} → poi-tl 清掉这个 run（见
 * {@link DocOssBytes} 的口径说明）。
 *
 * @author DOC-RENDER-001
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DocxRenderer {

    /** 每个图片位在模板里预留的 run 数（= {@code QcDocRules.MAX_IMAGES_PER_SLOT}）。 */
    static final int IMAGE_TAGS_PER_SLOT = 3;

    /**
     * 图片位所在单元格的宽度（px）。来源：三份模板的 {@code tblGrid}（twips ÷ 15 = 96dpi 像素）——
     * 样本质控表图片位 {@code gridSpan=3}：1626+1400+1122 = 4148 twips ≈ 276px；
     * 类器官质控表：4261 twips ≈ 284px。
     */
    private static final Map<String, Integer> SLOT_CELL_WIDTH_PX = Map.of(
        "orig", 276,
        "observe", 276,
        "pretreat", 276,
        "organoid_observe", 284
    );

    private static final int MIN_IMAGE_WIDTH_PX = 40;
    private static final int MAX_IMAGE_HEIGHT_PX = 420;

    private final DocOssBytes ossBytes;

    /**
     * 渲染一份文档（不含合并件）。
     */
    public byte[] render(DocRenderModel model) {
        Map<String, Object> data = new HashMap<>();
        data.putAll(model.texts());
        for (Map.Entry<String, List<Long>> slot : model.images().entrySet()) {
            List<Long> ossIds = slot.getValue();
            for (int i = 1; i <= IMAGE_TAGS_PER_SLOT; i++) {
                String tag = slot.getKey() + "_img" + i;
                data.put(tag, i <= ossIds.size()
                    ? picture(slot.getKey(), i, ossIds.get(i - 1), ossIds.size())
                    : null);
            }
        }
        try (var in = new ByteArrayInputStream(DocTemplate.bytes(model.getDocKind()))) {
            XWPFTemplate template = XWPFTemplate.compile(in, Configure.builder().build());
            try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                template.render(data);
                template.write(out);
                return out.toByteArray();
            } finally {
                template.close();
            }
        } catch (Exception e) {
            throw new ServiceException("渲染 " + model.getDocKind() + " 失败：" + e.getMessage(), 500);
        }
    }

    /**
     * 合并件：先把每份渲染成 docx（每份末尾补一个分页符），再按 poi-tl 的
     * {@code NiceXWPFDocument.merge} 拼起来 —— 它会把各份的图片/样式/命名空间一起搬过去，
     * 这是「合并 = 先拼 docx 再整体转 PDF」（ADR-0005）里「拼 docx」那一步。
     *
     * @param docs 至少一份，顺序即拼接顺序
     */
    public byte[] merge(List<byte[]> docs) {
        if (docs == null || docs.isEmpty()) {
            throw new ServiceException("没有可合并的文档（三份都还没完成）", 400);
        }
        try {
            if (docs.size() == 1) {
                return docs.get(0);
            }
            List<NiceXWPFDocument> rest = new ArrayList<>();
            for (int i = 1; i < docs.size(); i++) {
                rest.add(new NiceXWPFDocument(new ByteArrayInputStream(withTrailingPageBreak(docs.get(i)))));
            }
            NiceXWPFDocument base = new NiceXWPFDocument(new ByteArrayInputStream(docs.get(0)));
            NiceXWPFDocument merged = base.merge(rest, base.createParagraph().createRun());
            try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                merged.write(out);
                return out.toByteArray();
            } finally {
                merged.close();
            }
        } catch (Exception e) {
            throw new ServiceException("合并文档失败：" + e.getMessage(), 500);
        }
    }

    /**
     * 一份文档末尾补一个分页符（下一份另起一页）。
     *
     * <p>★ 用 run 里的 {@code <w:br w:type="page"/>} 而**不是**段落属性
     * {@code w:pageBreakBefore}：后者是「这一段自己从新页开始」，被拼到上一份末尾会先空出
     * 一整页；run 级的分页符是在当前位置断页，上一份的最后一行留在原页、下一份从新页开头。
     */
    private static byte[] withTrailingPageBreak(byte[] docx) throws Exception {
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(docx));
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            XWPFParagraph paragraph = document.createParagraph();
            paragraph.createRun().addBreak(BreakType.PAGE);
            document.write(out);
            return out.toByteArray();
        }
    }

    /**
     * 一张图 → poi-tl 的图片数据；取不到字节返回 {@code null}（标签会被清掉）。
     */
    private PictureRenderData picture(String slot, int index, Long ossId, int total) {
        byte[] raw = ossBytes.read(ossId);
        if (raw == null) {
            log.warn("图片位 {} 第 {} 张（ossId={}）取不到字节，本次渲染跳过这张图", slot, index, ossId);
            return null;
        }
        byte[] bytes = DocImageBytes.distinct(raw, slot + "-" + index + "-" + ossId);
        int cellWidth = SLOT_CELL_WIDTH_PX.getOrDefault(slot, 260);
        int width = Math.max(MIN_IMAGE_WIDTH_PX, cellWidth / Math.max(1, total));
        return Pictures.ofBytes(bytes).size(width, heightFor(bytes, width)).create();
    }

    /**
     * 按原图长宽比算高度；读不出尺寸（TIFF 之类本机 ImageIO 不认的）就退回正方形，
     * 不让一张图的元数据把整份文档打挂。
     */
    private static int heightFor(byte[] bytes, int width) {
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
            if (image != null && image.getWidth() > 0) {
                int height = (int) Math.round(width * (double) image.getHeight() / image.getWidth());
                return Math.min(MAX_IMAGE_HEIGHT_PX, Math.max(MIN_IMAGE_WIDTH_PX, height));
            }
        } catch (Exception e) {
            log.warn("读图片尺寸失败，按正方形排版：{}", e.toString());
        }
        return width;
    }
}
