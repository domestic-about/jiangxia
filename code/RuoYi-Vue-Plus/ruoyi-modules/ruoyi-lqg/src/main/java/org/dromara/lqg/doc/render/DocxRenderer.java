package org.dromara.lqg.doc.render;

import com.deepoove.poi.XWPFTemplate;
import com.deepoove.poi.config.Configure;
import com.deepoove.poi.data.PictureRenderData;
import com.deepoove.poi.data.Pictures;
import com.deepoove.poi.xwpf.NiceXWPFDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.dromara.common.core.exception.ServiceException;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTParaRPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTRPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSectPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSpacing;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STLineSpacingRule;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
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
 * {@code QcDocRules.MAX_IMAGES_PER_SLOT}）。排法见 {@link DocImageLayout}（甲方 2026-09-24 意见第 26 行
 * 「不太美观」之后重做）：1~3 张<b>等比</b>塞进这一格「能放图的框」（{@link #SLOT_BOX_PX}），
 * 按图的比例挑一行放几张（放不下自动折行），多张时每张包一圈白边当缝 —— 不变形、不撑破格子、不挤成一排小图。
 *
 * <p>★★ <b>取不到字节的图不再静默</b>（#217，独立验收 V23）：该标签仍留 {@code null}
 * （poi-tl 的 ClearHandler 会清掉这个 run，版式不乱），但<b>每一张都记进调用方给的缺图清单</b>
 * （{@link #render(DocRenderModel, List)} 的第二个参数）。怎么处置由 {@code DocRenderService} 定：
 * 外部版有缺图 → 整份 failed、不对外；内部版照出，缺图数与明细落在渲染记录上。
 *
 * <p>★ <b>合并件</b>（{@link #merge}）：每份文档自成一节（分节符 = 下一页），各自保留原件的纸张与页边距
 * —— 评分表原件是 Letter 纸，另两份是 A4，合并后也一样；每份都从新的一页开始、最后一页后面没有空白页。
 *
 * <p>★ <b>格子定高、长文字缩小、值格居中</b>（H 批，Kevin 本机验收「网页工作台」第 4、7 行）：poi-tl 渲染之前由
 * {@link DocCellLayout} 给表格里每一段定版式 —— 行距固定（Word、WPS 与转 PDF 的 LibreOffice 排出来一样高）、
 * 填值的格子按 {@link DocCellFit} 挑放得下的最大字号、水平垂直居中。
 *
 * <p>★ <b>细胞活率附件嵌进 Word</b>（H 批，同上第 5 行）：模型里带了嵌入附件（{@link DocRenderModel#embeds()}）的那一格，
 * 渲染时先放一个记号，单份渲染完 / 合并件拼好之后由 {@link DocOleEmbedder#embed} 换成「图标 + 文件名」的 OLE 对象，
 * Word / WPS 里双击打开原文件。取不到文件就只印文件名（记日志）；超过 {@link DocOleEmbedder#MAX_EMBED_BYTES}
 * 只印文件名并注明去附件里看。
 *
 * @author DOC-RENDER-001 · 独立验收 V23（缺图记账）· G 批 C 组（图片区排版、合并件分节）· H 批 H4 组（定高缩字、居中、嵌附件）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DocxRenderer {

    /** 每个图片位在模板里预留的 run 数（= {@code QcDocRules.MAX_IMAGES_PER_SLOT}）。 */
    static final int IMAGE_TAGS_PER_SLOT = 3;

    /**
     * 图片位所在单元格里<b>能放图的框</b>（px，96dpi）：{宽, 高}。
     *
     * <ul>
     *   <li><b>宽</b>：模板 {@code tblGrid}（twips ÷ 15）扣掉左右单元格边距（{@code tblCellMar} 各 108 twips），
     *       再留 ~4% 余量 —— 样本质控表图片位 {@code gridSpan=3}：1626+1400+1122 = 4148 twips，扣边距 3932 twips ≈ 262px；
     *       但 LibreOffice 排自动列宽的表格时实际只给了 ≈ 259.6px（G 批 C 组量过 PDF），260px 的图会顶到右边框、
     *       两张 130px 的图并排放不下而折成三行撑破格子。所以取 250。类器官质控表：4261 − 216 = 4045 twips ≈ 269px → 258。</li>
     *   <li><b>高</b>：模板这一行的最小行高（{@code trHeight atLeast}）再留上下各 ~8px —— 收样原始 / 样本观察 3634 twips ≈ 242px → 226；
     *       样本预处理 4248 twips ≈ 283px → 266；类器官样本观察 5769 twips ≈ 385px → 368。图排完不超过它，
     *       表格就还是模板的样子（样本质控表两页、类器官质控表一页），不会被图撑长。</li>
     * </ul>
     *
     * <p>★ 独立验收 V11 核对截图时发现过：旧值直接用了格宽（276 / 284px），一张图时右边被格子裁掉十几个像素。
     * {@code DocTemplateContractTest#imageBoxFitsInsideTheCell} 从模板量格宽与行高来钉这张表。
     */
    static final Map<String, int[]> SLOT_BOX_PX = Map.of(
        "orig", new int[] {250, 226},
        "observe", new int[] {250, 226},
        "pretreat", new int[] {250, 266},
        "organoid_observe", new int[] {258, 368}
    );

    /** 不认识的图片位（模板以后加的）用的框：取最小的那个，宁小勿撑。 */
    private static final int[] DEFAULT_BOX_PX = {250, 226};

    /** 多张时每张图四周的白边（px）：相邻两张之间 6px 的缝，贴格子边留 3px。 */
    static final int IMAGE_PAD_PX = 3;

    private final DocOssBytes ossBytes;

    /**
     * 渲染一份文档（不含合并件）；取不到的图照旧清掉图位，但<b>不</b>记账 —— 只给不关心缺图的调用方
     * （单测等）用。渲染链路一律走 {@link #render(DocRenderModel, List)}。
     */
    public byte[] render(DocRenderModel model) {
        return render(model, new ArrayList<>());
    }

    /**
     * 渲染一份文档（不含合并件），取不到字节的图逐张记进 {@code missing}。
     *
     * @param missing 缺图清单（调用方给的收集器；按 图片位 → 第几张 的顺序追加）
     */
    public byte[] render(DocRenderModel model, List<MissingImage> missing) {
        List<DocOleEmbedder.Attachment> embeds = new ArrayList<>();
        return DocOleEmbedder.embed(draft(model, missing, embeds), embeds);
    }

    /**
     * 合并件：各成员先出「草稿」（嵌入附件的地方还是记号），按 {@link #merge} 拼好之后再一次性把附件嵌进去。
     *
     * @param members 至少一份，顺序即拼接顺序
     */
    public byte[] renderMerged(List<DocRenderModel> members, List<MissingImage> missing) {
        List<DocOleEmbedder.Attachment> embeds = new ArrayList<>();
        List<byte[]> parts = new ArrayList<>();
        for (DocRenderModel member : members) {
            parts.add(draft(member, missing, embeds));
        }
        return DocOleEmbedder.embed(merge(parts), embeds);
    }

    /**
     * 出一份文档的「草稿」：版式、文字、图片都已到位，要嵌的附件在那一格里还是记号（追加进 {@code embeds}，
     * 由调用方在最后一步嵌进去）。
     */
    protected byte[] draft(DocRenderModel model, List<MissingImage> missing, List<DocOleEmbedder.Attachment> embeds) {
        Map<String, String> texts = new HashMap<>(model.texts());
        Map<String, DocCellLayout.Prefix> prefixes = new HashMap<>();
        Map<String, DocOleEmbedder.Attachment> pending = new LinkedHashMap<>();
        for (Map.Entry<String, DocRenderModel.Embed> embed : model.embeds().entrySet()) {
            String tag = embed.getKey();
            DocOleEmbedder.Attachment attachment = attachment(model.getDocKind(), tag, embed.getValue(), texts);
            if (attachment != null) {
                pending.put(tag, attachment);
                prefixes.put(tag, new DocCellLayout.Prefix(DocOleEmbedder.ICON_ADVANCE_TWIPS, DocOleEmbedder.ICON_LINE_TWIPS));
            }
        }
        Map<String, Object> data = new HashMap<>(texts);
        for (Map.Entry<String, List<Long>> slot : model.images().entrySet()) {
            List<PictureRenderData> pictures = pictures(model.getDocKind(), slot.getKey(), slot.getValue(), missing);
            for (int i = 1; i <= IMAGE_TAGS_PER_SLOT; i++) {
                data.put(slot.getKey() + "_img" + i, i <= pictures.size() ? pictures.get(i - 1) : null);
            }
        }
        try (var in = new ByteArrayInputStream(DocTemplate.bytes(model.getDocKind()))) {
            XWPFTemplate template = XWPFTemplate.compile(in, Configure.builder().build());
            try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                // 占位符还在的时候定版式（分得清哪格填值、哪格是印死的标签），再填值
                DocCellLayout.Result layout = DocCellLayout.apply(template.getXWPFDocument(), texts, prefixes);
                template.render(data);
                for (Map.Entry<String, DocOleEmbedder.Attachment> e : pending.entrySet()) {
                    XWPFParagraph paragraph = layout.paragraphs().get(e.getKey());
                    if (paragraph == null) {
                        log.warn("模板里没有「{}」这一格，附件 {} 没地方嵌", e.getKey(), e.getValue().fileName());
                        continue;
                    }
                    insertMarker(paragraph, e.getValue().marker());
                    embeds.add(e.getValue());
                }
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
     * 取要嵌的附件字节；嵌不了（取不到 / 太大）就返回 {@code null}，这一格只印文字（太大时文字后面注明去附件里看）。
     */
    private DocOleEmbedder.Attachment attachment(String docKind, String tag, DocRenderModel.Embed embed,
                                                 Map<String, String> texts) {
        DocOssBytes.Fetched fetched = ossBytes.fetch(embed.ossId());
        String name = embed.fileName();
        if (!fetched.ok()) {
            log.warn("「{}」的附件（ossId={}，{}）取不到字节：{} —— 这一格只印文件名", tag, embed.ossId(), name, fetched.reason());
            return null;
        }
        if (fetched.bytes().length > DocOleEmbedder.MAX_EMBED_BYTES) {
            log.info("「{}」的附件 {} 有 {} 字节，超过嵌入上限，只印文件名", tag, name, fetched.bytes().length);
            texts.put(tag, name + "（大于 " + DocOleEmbedder.MAX_EMBED_BYTES / 1024 / 1024 + "MB，未嵌入，请在附件中查看）");
            return null;
        }
        return new DocOleEmbedder.Attachment(DocOleEmbedder.marker(docKind, tag), DocOleEmbedder.safeName(name), fetched.bytes());
    }

    /** 在这一段最前面插一个记号 run 和一个空格（字号等随这一段的文字），嵌对象时记号换成图标。 */
    private static void insertMarker(XWPFParagraph paragraph, String marker) {
        CTRPr rPr = null;
        if (!paragraph.getRuns().isEmpty() && paragraph.getRuns().get(0).getCTR().isSetRPr()) {
            rPr = paragraph.getRuns().get(0).getCTR().getRPr();
        }
        XWPFRun gap = paragraph.insertNewRun(0);
        XWPFRun head = paragraph.insertNewRun(0);
        if (rPr != null) {
            head.getCTR().setRPr((CTRPr) rPr.copy());
            gap.getCTR().setRPr((CTRPr) rPr.copy());
        }
        head.setText(marker);
        gap.setText(" ");
    }

    /**
     * 合并件：先把每份渲染成 docx，再按 poi-tl 的 {@code NiceXWPFDocument.merge} 拼起来 ——
     * 它会把各份的图片/样式/命名空间一起搬过去，这是「合并 = 先拼 docx 再整体转 PDF」（ADR-0005）里「拼 docx」那一步。
     *
     * <p>★ <b>每份自成一节</b>（G 批 C 组修）：除最后一份外，每份末尾加一个带本份 {@code sectPr} 的分节段落
     * （分节符默认「下一页」）；拼好后整篇的最后一节换成最后一份自己的 {@code sectPr}。于是：
     * <ul>
     *   <li>每份都从新的一页开始（旧做法把分页符补在第 2..n 份的末尾：第 1、2 份之间没断页，
     *       类器官质控表接在样本质控表第 2 页的注下面、被拦腰截成两页；最后一份后面又多出一张空白页）；</li>
     *   <li>每份保留原件自己的纸张与页边距（评分表原件是 Letter，另两份是 A4）。</li>
     * </ul>
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
            CTSectPr lastSection = sectionOf(docs.get(docs.size() - 1));
            List<NiceXWPFDocument> rest = new ArrayList<>();
            for (int i = 1; i < docs.size(); i++) {
                byte[] part = i < docs.size() - 1 ? withSectionBreak(docs.get(i)) : docs.get(i);
                rest.add(new NiceXWPFDocument(new ByteArrayInputStream(part)));
            }
            NiceXWPFDocument base = new NiceXWPFDocument(new ByteArrayInputStream(withSectionBreak(docs.get(0))));
            NiceXWPFDocument merged = base.merge(rest, base.createParagraph().createRun());
            if (lastSection != null) {
                merged.getDocument().getBody().setSectPr(lastSection);
            }
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

    /** 一份文档的节属性（纸张、页边距…）副本；没有就 {@code null}。 */
    private static CTSectPr sectionOf(byte[] docx) throws Exception {
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(docx))) {
            CTSectPr sect = document.getDocument().getBody().getSectPr();
            return sect == null ? null : (CTSectPr) sect.copy();
        }
    }

    /**
     * 一份文档末尾加一个<b>分节段落</b>（段落属性里带本份的 {@code sectPr}，分节符默认「下一页」）：
     * 下一份从新的一页开始，并且本份保留自己的纸张与页边距。
     *
     * <p>★ 分节段落本身压到 1 磅高（字号 1、行距固定 20 twips、段前段后 0）：它占在本份最后一页的末尾，
     * 不会因为多出一行把那一页挤出一张空白页。
     */
    private static byte[] withSectionBreak(byte[] docx) throws Exception {
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(docx));
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            CTSectPr body = document.getDocument().getBody().getSectPr();
            XWPFParagraph paragraph = document.createParagraph();
            CTPPr ppr = paragraph.getCTP().isSetPPr() ? paragraph.getCTP().getPPr() : paragraph.getCTP().addNewPPr();
            CTSpacing spacing = ppr.addNewSpacing();
            spacing.setBefore(BigInteger.ZERO);
            spacing.setAfter(BigInteger.ZERO);
            spacing.setLine(BigInteger.valueOf(20));
            spacing.setLineRule(STLineSpacingRule.EXACT);
            CTParaRPr mark = ppr.addNewRPr();
            mark.addNewSz().setVal(BigInteger.valueOf(2));
            if (body != null) {
                ppr.setSectPr((CTSectPr) body.copy());
            }
            document.write(out);
            return out.toByteArray();
        }
    }

    /**
     * 一个图片位里的图 → poi-tl 的图片数据（与 {@code ossIds} 一一对应；取不到字节的那张是 {@code null}，
     * 标签会被清掉，并记进 {@code missing}）。取到的几张按 {@link DocImageLayout} 一起排。
     */
    private List<PictureRenderData> pictures(String docKind, String slot, List<Long> ossIds, List<MissingImage> missing) {
        List<byte[]> fetched = new ArrayList<>(ossIds.size());
        List<DocImageLayout.Size> sizes = new ArrayList<>();
        for (int i = 0; i < ossIds.size(); i++) {
            Long ossId = ossIds.get(i);
            DocOssBytes.Fetched one = ossBytes.fetch(ossId);
            if (!one.ok()) {
                log.warn("图片位 {} 第 {} 张（ossId={}）取不到字节：{} —— 记为缺图", slot, i + 1, ossId, one.reason());
                missing.add(new MissingImage(docKind, slot, i + 1, ossId, one.reason()));
                fetched.add(null);
                continue;
            }
            fetched.add(one.bytes());
            sizes.add(sizeOf(one.bytes()));
        }
        int[] box = SLOT_BOX_PX.getOrDefault(slot, DEFAULT_BOX_PX);
        List<DocImageLayout.Placement> placements = DocImageLayout.fit(sizes, box[0], box[1], IMAGE_PAD_PX);
        List<PictureRenderData> pictures = new ArrayList<>(ossIds.size());
        int k = 0;
        for (int i = 0; i < ossIds.size(); i++) {
            byte[] raw = fetched.get(i);
            if (raw == null) {
                pictures.add(null);
                continue;
            }
            DocImageLayout.Size source = sizes.get(k);
            DocImageLayout.Placement place = placements.get(k++);
            pictures.add(picture(raw, source, place, slot + "-" + (i + 1) + "-" + ossIds.get(i)));
        }
        return pictures;
    }

    /**
     * 一张图：多张时先包一圈白边（{@link DocImageLayout.Placement#pad} 换算成原图像素）再定显示尺寸；
     * 白边加不上（本机 ImageIO 读不了这种图）就不加，显示尺寸同步扣掉白边 —— 宁可没缝，也不把图拉变形。
     */
    private static PictureRenderData picture(byte[] raw, DocImageLayout.Size source, DocImageLayout.Placement place,
                                             String marker) {
        int pad = place.pad();
        int width = place.display().width();
        int height = place.display().height();
        byte[] bytes = raw;
        if (pad > 0) {
            int contentWidth = Math.max(1, width - 2 * pad);
            int border = Math.max(1, (int) Math.round(pad * (double) source.width() / contentWidth));
            byte[] padded = DocImageBytes.withWhiteBorder(raw, border);
            if (padded != null) {
                bytes = padded;
            } else {
                width -= 2 * pad;
                height -= 2 * pad;
            }
        }
        bytes = DocImageBytes.distinct(bytes, marker);
        return Pictures.ofBytes(bytes).size(width, height).create();
    }

    /**
     * 原图宽高（只读图头）；读不出尺寸（TIFF 之类本机 ImageIO 不认的）就按正方形排，
     * 不让一张图的元数据把整份文档打挂。
     */
    private static DocImageLayout.Size sizeOf(byte[] bytes) {
        DocImageLayout.Size size = DocImageBytes.dimensions(bytes);
        if (size == null) {
            log.warn("读图片尺寸失败，按正方形排版");
            return new DocImageLayout.Size(1000, 1000);
        }
        return size;
    }
}
