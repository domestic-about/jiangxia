package org.dromara.lqg.doc.render;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 一份待渲染文档的**全部输入**（ticket §0 口径复述 3 的唯一落点是这个类）。
 *
 * <p>★★ <b>指纹必须覆盖「所有输入」，而不是只有文档自己的字段</b>：
 *
 * <pre>
 *   content_hash = sha256( 该文档全部字段（texts）
 *                        + 从样本主档带出的字段（也在 texts 里）
 *                        + 图片的 oss_id 序列（images，逐位、有序）
 *                        + 附件集的 oss_id 序列（attachmentOssIds）
 *                        + 模板版本（templateVersion）
 *                        + audience )                       ← ADR-0005 / FLOW:F-DOC-01.step1
 * </pre>
 *
 * <p>★ <b>指纹与渲染数据是同一份对象</b>：{@link #canonical()} 算指纹、{@link #texts()} /
 * {@link #images()} 喂 poi-tl。两者同一个来源，就不可能出现「页面上改了、指纹没跟上」
 * 或者反过来「指纹变了、文档没变」——{@code DocFingerprintTest} 逐类输入翻一个值来钉这条。
 *
 * <p>★ <b>null 与空串归一成 ""</b>：文档上两者都是空格子，指纹不该因此分叉（否则
 * 「清空一栏」会重出一份字节完全一样的文件）。反之**值的顺序**有意义：同一个图片位里
 * 换顺序 = 甲方看到的图换了位置 = 必须重出，所以 {@code images} 是有序列表不是集合。
 *
 * <p>★ 纯 POJO、无 Spring 无库：{@code DocFingerprintTest} 直接 new 出来改一个字段就能跑。
 *
 * @author DOC-RENDER-001
 */
public class DocRenderModel {

    private final String docKind;
    private final String audience;
    private final String templateVersion;

    /** poi-tl 文本标签 → 值（TreeMap：指纹与渲染都按标签名字典序，跨进程可复现）。 */
    private final Map<String, String> texts = new TreeMap<>();

    /** 图片位 → 该位里按 sort 升序的 oss_id 列表（进 Word 用的是 **preview_oss_id**）。 */
    private final Map<String, List<Long>> images = new TreeMap<>();

    /** 通用附件的 oss_id 序列（不进 Word 正文，但改了必须重出 —— 预览页下方那一栏是它）。 */
    private final List<Long> attachmentOssIds = new ArrayList<>();

    /** 额外参与指纹的行（合并件用它装三个成员的指纹）。 */
    private final List<String> parts = new ArrayList<>();

    public DocRenderModel(String docKind, String audience, String templateVersion) {
        this.docKind = docKind;
        this.audience = audience;
        this.templateVersion = templateVersion;
    }

    /** 加一条文本标签（值 null → ""）。 */
    public DocRenderModel text(String tag, String value) {
        texts.put(tag, value == null ? "" : value);
        return this;
    }

    /** 记一个图片位（顺序即 sort 顺序，**换了顺序指纹必须变**）。 */
    public DocRenderModel imageSlot(String slot, List<Long> ossIds) {
        images.put(slot, ossIds == null ? List.of() : List.copyOf(ossIds));
        return this;
    }

    public DocRenderModel attachments(List<Long> ossIds) {
        attachmentOssIds.clear();
        if (ossIds != null) {
            attachmentOssIds.addAll(ossIds);
        }
        return this;
    }

    /** 追加一条额外的指纹行（只影响指纹，不影响渲染数据）。 */
    public DocRenderModel part(String line) {
        parts.add(line);
        return this;
    }

    public String getDocKind() {
        return docKind;
    }

    public String getAudience() {
        return audience;
    }

    public String getTemplateVersion() {
        return templateVersion;
    }

    public Map<String, String> texts() {
        return texts;
    }

    public Map<String, List<Long>> images() {
        return images;
    }

    public List<Long> attachmentOssIds() {
        return List.copyOf(attachmentOssIds);
    }

    /**
     * 指纹的**规范文本**（人可读，出问题时能直接 diff 两份算出来的字符串）。
     */
    public String canonical() {
        StringBuilder sb = new StringBuilder();
        sb.append("kind=").append(docKind).append('\n');
        sb.append("audience=").append(audience).append('\n');
        sb.append("template=").append(templateVersion).append('\n');
        for (Map.Entry<String, String> e : texts.entrySet()) {
            sb.append("text:").append(e.getKey()).append('=').append(e.getValue()).append('\n');
        }
        for (Map.Entry<String, List<Long>> e : images.entrySet()) {
            sb.append("image:").append(e.getKey()).append('=');
            for (int i = 0; i < e.getValue().size(); i++) {
                if (i > 0) {
                    sb.append(',');
                }
                sb.append(e.getValue().get(i));
            }
            sb.append('\n');
        }
        for (Long ossId : attachmentOssIds) {
            sb.append("attach:").append(ossId).append('\n');
        }
        for (String line : parts) {
            sb.append("part:").append(line).append('\n');
        }
        return sb.toString();
    }

    /** {@code content_hash}。 */
    public String contentHash() {
        return DocContentHash.sha256Hex(canonical());
    }

    /**
     * 合并件：没有自己的字段，指纹 = 三个成员各自的指纹（按拼接顺序）+ 版本 + audience。
     *
     * <p>任何一份成员的内容变了 → 成员的指纹变 → 合并件的指纹跟着变 → 合并件重出。
     * 这就是「docx 是新的、合并件还是旧的」在缓存层被堵死的地方。
     */
    public static DocRenderModel merged(String audience, String templateVersion, List<DocRenderModel> members) {
        DocRenderModel model = new DocRenderModel(DocKinds.MERGED, audience, templateVersion);
        for (DocRenderModel member : members) {
            model.part(member.getDocKind() + ":" + member.contentHash());
        }
        return model;
    }
}
