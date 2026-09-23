package org.dromara.lqg.doc.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.mybatis.helper.DataPermissionHelper;
import org.dromara.lqg.doc.pdf.DocArtifactRows;
import org.dromara.lqg.doc.pdf.DocArtifactStore;
import org.dromara.lqg.doc.pdf.DocPagesService;
import org.dromara.lqg.doc.pdf.domain.vo.DocPagesVo;
import org.dromara.lqg.doc.render.DocAudiences;
import org.dromara.lqg.doc.render.DocKinds;
import org.dromara.lqg.doc.render.DocRenderModelFactory;
import org.dromara.lqg.doc.render.domain.DocFile;
import org.dromara.lqg.doc.render.domain.vo.DocDownloadVo;
import org.dromara.lqg.doc.render.service.DocRenderService;
import org.dromara.lqg.sample.domain.Sample;
import org.dromara.lqg.sample.service.SampleFieldCipher;
import org.dromara.system.domain.SysOss;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;

/**
 * <b>对外文档的读口</b>（AUTH-EXT-003）—— {@code /mp/ext/doc/**} 的取数全部落在这里。
 *
 * <p>★★ <b>为什么这个类在 doc 域、不在 ext 包</b>（ADR-0004 的 I4）：
 * ext 包里只有 {@code ExtScopeServiceImpl} 允许持 {@code *Mapper}，连读一行
 * {@code t_lqg_doc_file} 都不行。于是「外部能拿到哪些文档」这条口径的**取数**放在被调方
 * （本类），ext 包只做可见范围断言 + 拼装成 {@code Ext*Vo}。
 *
 * <p>★★ <b>对外可见 = 三个条件同时成立</b>（ticket §0 口径 1、FLOW:F-DOC-02.step1 的 produces）：
 *
 * <ol>
 *   <li>{@code doc_status='published'}（三张质控表各自的列）——
 *       {@code draft} 不给（DOC-PUBLISH-001 的「撤回 / 改内容回草稿」都会写回 draft）；</li>
 *   <li>对应 {@code audience='external'} 的 header 行（{@code file_format='docx'} / {@code page_no=0}）
 *       {@code render_status='done'} 且有产物 —— 「完成并同步」的渲染是异步排队的，
 *       只看状态会在渲染完成前的一小段时间里把看不见的文档列出来（DOC-PUBLISH-001 的 WARN-5）；</li>
 *   <li>对 {@code merged} 还要加一条：<b>这一版产物必须完整</b> —— 合并件没有自己的
 *       {@code doc_status}，成员集合变了（某一份被撤回 / 新完成一份）时，header 行的
 *       {@code content_hash} 会被 {@code invalidateMerged} <b>改写成新的期望值</b>，
 *       而桶里的 docx 还是旧成员拼的。只比「header 指纹 vs 此刻指纹」当场就成立
 *       （<b>实测无效</b>，见 {@link #artifactComplete}），必须锚在**产物**上：
 *       当前指纹下有页图 + PDF 与 header 同版。少了这一条就会把「含已撤回文档的旧合并件」
 *       发给送检方，而且预览页是空的（ticket accept 1 的 counterfeit 点名的正是
 *       「点进去一片空白」）。</li>
 * </ol>
 *
 * <p>★ <b>单份文档为什么不查「此刻指纹」</b>：模板版本号（{@code lqg/doc-templates/template-version.txt}）
 * 进指纹，一次模板升级会让**所有**历史产物的指纹都对不上。单份文档的「内容改了」由
 * {@code doc_status} 兜住（改内容 → 回草稿），所以再拿指纹当门槛只会在模板升级后
 * 把全部历史文档对送检方隐藏 —— 那是比「产物略旧」更糟的事故（内部页面照旧显示同一份，
 * 对外却空了，两边不一致）。所以：<b>单份 = 状态 + 产物完整；合并件 = 产物完整</b>
 * （它没有自己的状态，成员状态一变就走产物完整性那一条）。
 *
 * <p>★ <b>audience 写死 external</b>（ticket §0 口径 2）：本类所有方法都<b>没有</b>
 * {@code audience} 入参 —— 不是「传进来再覆盖」，是调用方根本传不进来。
 * 「能不能给出去」这道判据本身在 {@link DocAvailabilityService}（DOC-MP-001 把它抽成
 * 按 audience 参数化的共享类，内部清单用的是同一份判据）；本类只负责
 * 「可见范围之外不查库 + 拼外部的行 + 签发前核对象键」。
 *
 * <p>★ <b>链接签发前再核一遍对象键</b>（ticket §0 口径 3）：
 * 对象键约定 {@code lqg/doc/<sampleId>/<docKind>/<audience>/<指纹前 12 位>}
 * （{@code DocOssBytes.objectKey}），签发前要求 {@code sys_oss.file_name} 里确有
 * {@code /external/} 这一段。这是「内外部产物各一行、键里带 audience」的结构性守卫
 * —— 万一哪天有一行的元数据指向了内部版，这里宁可 404 也不把带内部编号的那一份发出去。
 *
 * @author AUTH-EXT-003
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocExternalQueryService {

    private final DocRenderModelFactory modelFactory;
    private final DocArtifactRows rows;
    private final DocArtifactStore artifactStore;
    private final DocPagesService pagesService;
    private final DocRenderService renderService;
    private final SampleFieldCipher fieldCipher;
    private final DocAvailabilityService availability;

    /**
     * 一行「外部可见的文档」。
     *
     * <p>只带拼装 {@code ExtDocVo} 需要的字段，<b>不带</b>内部编号 / 渲染人 / 内部路径。
     * {@code donorName} 是<b>解密后</b>的明文（{@code t_lqg_sample.donor_name} 是密文，
     * ADR-0006 的裸 Base64 口径）—— 打码是 ext 包的事（{@code MaskRules}），
     * 本类只负责把密文变成可打码的明文。
     */
    public record DocExternalRow(Long sampleId, String submitNo, String donorName,
                                 String docKind, Date publishedTime, Integer totalScore) {
    }

    // ══════════════════════════════════════════════════════════════════════
    // 清单
    // ══════════════════════════════════════════════════════════════════════

    /**
     * 一批样本对外可见的文档行（顺序 = 该样本内的 {@link DocKinds#MERGED_ORDER}，合并件在最后）。
     *
     * <p>入参是**调用方已经算好的可见样本集合**（ext 包的 {@code ExtScopeService} 算，
     * 本类不重算、也不可能把范围放大）。空集合不查库。
     */
    public List<DocExternalRow> rowsOfSamples(Collection<Long> sampleIds) {
        if (sampleIds == null || sampleIds.isEmpty()) {
            return List.of();
        }
        List<DocExternalRow> out = new ArrayList<>();
        for (Long sampleId : sampleIds) {
            out.addAll(rowsOfSample(sampleId));
        }
        return out;
    }

    /**
     * 一个样本对外可见的全部文档行。
     *
     * <p>样本不存在 / 查不动 → 空列表（调用方那条「不可见按不存在」的闸在更外面，
     * 本类不替它决定 404 还是空集）。
     */
    public List<DocExternalRow> rowsOfSample(Long sampleId) {
        if (sampleId == null) {
            return List.of();
        }
        return DataPermissionHelper.ignore(() -> {
            Sample sample;
            try {
                sample = modelFactory.requireSample(sampleId);
            } catch (Exception e) {
                return List.<DocExternalRow>of();
            }
            String submitNo = sample.getSubmitNo();
            String donorName = fieldCipher.decrypt(sample.getDonorName());
            List<DocExternalRow> out = new ArrayList<>(DocKinds.MERGED_ORDER.size() + 1);
            // 合并件的完成时间 = 成员里最新那一份的完成时间（它自己没有完成时间）
            Date mergedTime = null;
            for (String kind : DocKinds.MERGED_ORDER) {
                if (!available(sampleId, kind)) {
                    continue;
                }
                Date time = publishedTimeOf(sampleId, kind);
                out.add(new DocExternalRow(sampleId, submitNo, donorName, kind, time, totalScoreOf(sampleId, kind)));
                if (time != null && (mergedTime == null || time.after(mergedTime))) {
                    mergedTime = time;
                }
            }
            if (available(sampleId, DocKinds.MERGED)) {
                out.add(new DocExternalRow(sampleId, submitNo, donorName, DocKinds.MERGED, mergedTime, null));
            }
            return out;
        });
    }

    // ══════════════════════════════════════════════════════════════════════
    // 可用性（清单 / 预览 / 下载共用同一处判据）
    // ══════════════════════════════════════════════════════════════════════

    /**
     * 这一份文档现在能不能给外部看 —— <b>清单、预览、下载三处必须是同一个判据</b>，
     * 否则会出现「列表里有、点进去 404」或「列表里没有、却能直接预览」。
     *
     * <p>四个条件（header 存在 / {@code done} 且有产物 / 产物完整 / 单份还要 published）
     * 逐条在 {@link DocAvailabilityService#available}（按 audience 参数化的共享判据）里，
     * 本方法只是把 audience 写死成 {@code external}。★
     * 「合并件成员被撤回后不再露出去」靠的是那里的 {@code artifactComplete}
     * （**不是**比 header 的 {@code content_hash} 与此刻指纹 —— 那条实测无效）。
     */
    public boolean available(Long sampleId, String docKind) {
        return availability.available(sampleId, DocKinds.require(docKind), DocAudiences.EXTERNAL);
    }

    // ══════════════════════════════════════════════════════════════════════
    // 预览页 / 下载
    // ══════════════════════════════════════════════════════════════════════

    /**
     * 页面图（10 分钟签名链接）。不可用按「不存在」回 404 ——
     * 草稿 / 外部版没渲染成功 / 渲染在途，一律与「没这份文档」同一个响应。
     */
    public DocPagesVo pages(Long sampleId, String docKind) {
        String kind = DocKinds.require(docKind);
        requireAvailable(sampleId, kind);
        return pagesService.pages(sampleId, kind, DocAudiences.EXTERNAL);
    }

    /**
     * 短时签名下载链接（{@code format=docx|pdf}，默认 docx）。不可用回 404。
     *
     * <p>顺序是刻意的：<b>先核对象键、再签发</b>（ticket §0 口径 3）。
     */
    public DocDownloadVo download(Long sampleId, String docKind, String format) {
        String kind = DocKinds.require(docKind);
        String fmt = DocAvailabilityService.requireFormat(format);
        requireAvailable(sampleId, kind);
        requireExternalObjectKey(sampleId, kind, fmt);
        return renderService.download(sampleId, kind, fmt, DocAudiences.EXTERNAL);
    }

    // ── 私有 ─────────────────────────────────────────────────────────────────

    /**
     * 不可用一律 404（不泄露存在性：草稿 / 外部版没渲染成功 / 合并件过期，对外长得跟
     * 「没这份文档」一样）。<b>不区分原因</b>是刻意的 —— 说「还是草稿」等于告诉对方
     * 内部已经写了这份文档。
     */
    private void requireAvailable(Long sampleId, String docKind) {
        if (!available(sampleId, docKind)) {
            throw DocAvailabilityService.notFound();
        }
    }

    /**
     * 签发前再核一遍对象键里的 audience 段（ticket §0 口径 3）。
     *
     * <p>对象键的中间一段就是 audience（{@code DocOssBytes.objectKey}）；内部版与外部版
     * 是两行独立产物，键里带的段不同。这里的检查**理论上永远成立**，它是
     * 「产物行 ↔ 桶里的对象」这条链的结构性守卫：不成立就宁可 404，绝不签发。
     */
    private void requireExternalObjectKey(Long sampleId, String docKind, String format) {
        DocFile row = rows.find(sampleId, docKind, DocAudiences.EXTERNAL, format, 0);
        SysOss oss = row == null ? null : artifactStore.oss(row.getOssId());
        String key = oss == null ? null : oss.getFileName();
        if (key == null || !key.contains("/" + DocAudiences.EXTERNAL + "/")) {
            log.error("外部文档的对象键里没有 external 段，拒绝签发下载链接：sampleId={} docKind={} format={} key={}",
                sampleId, docKind, format, key);
            throw DocAvailabilityService.notFound();
        }
    }

    // ── 下面四个只是把「按 audience 参数化的共享判据/取数」钉在 external 这一版上 ──────

    /** 完成时间（评分表行的 {@code totalScore} 是外部唯一能看到的「分数」）。 */
    private Date publishedTimeOf(Long sampleId, String docKind) {
        return availability.publishedTimeOf(sampleId, docKind);
    }

    /** 合计分只在评分表那一行上有（FLOW:F-EXT-01.step3 的白名单：「含已完成评分表的合计分」）。 */
    private Integer totalScoreOf(Long sampleId, String docKind) {
        return availability.totalScoreOf(sampleId, docKind);
    }
}
