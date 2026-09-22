package org.dromara.lqg.doc.mp;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.common.mybatis.helper.DataPermissionHelper;
import org.dromara.lqg.doc.pdf.DocArtifactRows;
import org.dromara.lqg.doc.pdf.DocPagesService;
import org.dromara.lqg.doc.pdf.domain.vo.DocPagesVo;
import org.dromara.lqg.doc.render.DocAudiences;
import org.dromara.lqg.doc.render.DocKinds;
import org.dromara.lqg.doc.render.DocRenderModelFactory;
import org.dromara.lqg.doc.render.domain.DocFile;
import org.dromara.lqg.doc.render.domain.vo.DocDownloadVo;
import org.dromara.lqg.doc.render.mapper.DocFileMapper;
import org.dromara.lqg.doc.render.service.DocRenderService;
import org.dromara.lqg.doc.service.DocAvailabilityService;
import org.dromara.lqg.sample.domain.Sample;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 内部文档清单的取数与拼装（{@code GET /mp/int/doc/list}，ticket §2 / FLOW:F-DOC-02.step1）。
 *
 * <p>★★ <b>「能不能给出去」不在这里判</b>：判据是共享的
 * {@link DocAvailabilityService#available}（header {@code done} + 产物完整 + 单份要
 * {@code doc_status='published'}），外部清单用的是同一份 ——
 * ★ <b>不要</b>在这里再写一遍，尤其不要写成「header 的 {@code content_hash} 与此刻算出来的
 * 指纹比」（那条实测无效，理由见 {@code DocAvailabilityService#artifactComplete}）。
 * 本类只做三件事：<b>取候选行 → 过滤/排序 → 切页</b>。
 *
 * <p>★ <b>候选行从产物表驱动</b>（{@code t_lqg_doc_file} 里 {@code audience='internal'} 的
 * docx header）：只有「内部版真的渲染过」的样本才会进候选，不必扫全表样本。
 * 代价是每个候选样本还要再查三张质控表（完成状态 / 完成时间 / 合计分）——
 * 与外部清单（{@code ExtDocAssemblyService}）是同一个取舍，记在完工报告的 WARN 里。
 *
 * <p>★ <b>软删的样本不进清单</b>：{@code modelFactory.requireSample} 走 {@code @TableLogic}，
 * 软删行（seed 的 1010）当场查不到 → 这一行被跳过。
 * （AUTH-EXT-003 §7.9 点名的既有病灶，本类在入口就绕开了。）
 *
 * <p>★ <b>title / subtitle 由后端按身份给</b>（ticket §0 口径 1）：内部 = 内部编号 / 来源单位，
 * 前端不自己拼内部编号（accept 2 的禁字 grep）。
 *
 * <p>★ DOC-MP-002 在本类加了 {@link #pages} / {@link #download} 两个方法：内部侧的
 * 预览与下载入口（{@code audience} 写死 internal、先过同一道可用性闸）。
 *
 * @author DOC-MP-001（清单） / DOC-MP-002（预览 + 下载）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MpDocService {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final DocFileMapper docFileMapper;
    private final DocRenderModelFactory modelFactory;
    private final DocAvailabilityService availability;
    private final DocPagesService pagesService;
    private final DocRenderService renderService;

    // ══════════════════════════════════════════════════════════════════════
    // GET /mp/int/doc/{sampleId}/{docKind}/pages|download（DOC-MP-002）
    // ══════════════════════════════════════════════════════════════════════

    /**
     * 预览（页面图 + 图片位 + 附件），{@code audience} 写死 {@code internal}。
     *
     * <p>★ 先过共享判据 {@code availability.requireAvailable(..., INTERNAL)} 再取数 ——
     * 与清单**同一个**判据，所以不会出现「列表里有、点进去 404」或反过来。
     * 不过这一道的话，{@code DocPagesService} 对「没渲染过」的行会抛 400（带一句
     * 「先 POST render」的实现提示）—— 那是工作台的口径，小程序侧一律按 404
     * 「没有这份文档」处理，不把实现细节漏给页面。
     */
    public DocPagesVo pages(Long sampleId, String docKind) {
        String kind = DocKinds.require(docKind);
        availability.requireAvailable(sampleId, kind, DocAudiences.INTERNAL);
        return pagesService.pages(sampleId, kind, DocAudiences.INTERNAL);
    }

    /**
     * 10 分钟签名下载链接 + 文件名，{@code audience} 写死 {@code internal}。
     *
     * <p>顺序与外部那条一致：<b>先归一化 format → 再判可用性 → 再签发</b>。
     * {@code format} 非法一律 400（{@code DocAvailabilityService.requireFormat}），
     * 不会悄悄给一份别的格式。
     */
    public DocDownloadVo download(Long sampleId, String docKind, String format) {
        String kind = DocKinds.require(docKind);
        String fmt = DocAvailabilityService.requireFormat(format);
        availability.requireAvailable(sampleId, kind, DocAudiences.INTERNAL);
        return renderService.download(sampleId, kind, fmt, DocAudiences.INTERNAL);
    }

    // ══════════════════════════════════════════════════════════════════════
    // GET /mp/int/doc/list
    // ══════════════════════════════════════════════════════════════════════

    /**
     * 内部文档清单：全部样本里已完成且内部版渲染成功的文档。
     */
    public TableDataInfo<MpDocVo> list(MpDocQueryBo query) {
        MpDocQueryBo q = query == null ? new MpDocQueryBo() : query;
        String kindFilter = trimToNull(q.getDocKind());
        Long sampleFilter = q.getSampleId();
        List<MpDocVo> all = DataPermissionHelper.ignore(() -> {
            List<MpDocVo> out = new ArrayList<>();
            for (DocFile header : internalHeaders()) {
                String kind = header.getDocKind();
                Long sampleId = header.getSampleId();
                if (kind == null || sampleId == null) {
                    continue;
                }
                if (kindFilter != null && !kindFilter.equals(kind)) {
                    continue;
                }
                if (sampleFilter != null && !sampleFilter.equals(sampleId)) {
                    continue;
                }
                Sample sample;
                try {
                    // 软删 / 不存在的样本 → 跳过（@TableLogic 兜住，seed 的 1010 走这条路）
                    sample = modelFactory.requireSample(sampleId);
                } catch (Exception e) {
                    continue;
                }
                // ★ 同一个判据，换成内部版这个 audience；前端不许自己再判一次
                if (!availability.available(sampleId, kind, DocAudiences.INTERNAL)) {
                    continue;
                }
                Date time = publishedTimeOf(sampleId, kind, internalTimes(sampleId));
                if (!inRange(time, q.getPublishedBegin(), q.getPublishedEnd())) {
                    continue;
                }
                out.add(toVo(sample, sampleId, kind, time));
            }
            return out;
        });
        all.sort(bySampleGroupThenKind(all));
        return slice(all, q);
    }

    // ── 取候选行 ──────────────────────────────────────────────────────────────

    /**
     * 内部版的 docx header 行（{@code audience='internal'} / {@code docx} / {@code page_no=0}）。
     *
     * <p>这是清单的<b>驱动集合</b>：外面再按「这一版产物完整」+「单份已发布」过滤，
     * 所以这里不过滤 {@code render_status}（{@code failed} / {@code pending} 的行也要取出来，
     * 否则「渲染失败之后这一份从清单里消失」这件事就没有独立的证据）。
     */
    private List<DocFile> internalHeaders() {
        return docFileMapper.selectList(new LambdaQueryWrapper<DocFile>()
            .eq(DocFile::getAudience, DocAudiences.INTERNAL)
            .eq(DocFile::getFileFormat, DocArtifactRows.FORMAT_DOCX)
            .eq(DocFile::getPageNo, 0));
    }

    // ── 组装 ─────────────────────────────────────────────────────────────────

    private MpDocVo toVo(Sample sample, Long sampleId, String docKind, Date time) {
        String internalNo = sample.getInternalNo();
        MpDocVo vo = new MpDocVo();
        vo.setSampleId(sampleId);
        // 内部 = 内部编号 / 来源单位（ticket §0 口径 1）。内部编号为空的老数据回落送检单号
        // —— 组标题空着比「露一个送检单号」更糟（pending / 无效样本本就不该有已发布文档）。
        vo.setTitle(StringUtils.isBlank(internalNo) ? sample.getSubmitNo() : internalNo);
        vo.setSubtitle(sample.getSourceUnitName());
        vo.setInternalNo(internalNo);
        vo.setSourceUnitName(sample.getSourceUnitName());
        vo.setDocKind(docKind);
        vo.setPublishedTime(time);
        // totalScore 只在评分表行上有（其余行保持 null → JSON 里连键都不出）
        vo.setTotalScore(availability.totalScoreOf(sampleId, docKind));
        return vo;
    }

    /**
     * 完成时间：单份文档读它自己的 {@code published_time}；
     * 合并件没有自己的完成时间，取<b>成员里最新那一份</b>的（与外部清单同一个口径）。
     */
    private Date publishedTimeOf(Long sampleId, String docKind, Map<String, Date> memberTimes) {
        if (!DocKinds.isMerged(docKind)) {
            return availability.publishedTimeOf(sampleId, docKind);
        }
        Date latest = null;
        for (Date time : memberTimes.values()) {
            if (time != null && (latest == null || time.after(latest))) {
                latest = time;
            }
        }
        return latest;
    }

    /** 这个样本三份单文档的完成时间（算合并件的完成时间用；一次性取好，别在循环里查）。 */
    private Map<String, Date> internalTimes(Long sampleId) {
        Map<String, Date> times = new HashMap<>();
        for (String kind : DocKinds.MERGED_ORDER) {
            if (availability.isPublished(sampleId, kind)) {
                times.put(kind, availability.publishedTimeOf(sampleId, kind));
            }
        }
        return times;
    }

    // ── 排序 / 切页（与外部清单逐字同款）────────────────────────────────────────

    /**
     * 排序：<b>同一个样本的几份文档必须相邻</b>（FLOW:F-DOC-02.step1 是「按样本分组展示」，
     * 分组是前端的活，但列表得先把同一组的行放在一起 —— 否则翻了页就会把一组拆到两页里）。
     *
     * <p>组的先后 = 该组里<b>最新完成</b>的那一份的时间倒序；组内按
     * 「样本质控表 → 类器官质控表 → 评分表 → 合并件」。
     */
    private static Comparator<MpDocVo> bySampleGroupThenKind(List<MpDocVo> rows) {
        Map<Long, Date> groupTime = new HashMap<>();
        for (MpDocVo vo : rows) {
            Date time = vo.getPublishedTime();
            if (time == null) {
                continue;
            }
            Date current = groupTime.get(vo.getSampleId());
            if (current == null || time.after(current)) {
                groupTime.put(vo.getSampleId(), time);
            }
        }
        return (a, b) -> {
            int c = compareDesc(groupTime.get(a.getSampleId()), groupTime.get(b.getSampleId()));
            if (c != 0) {
                return c;
            }
            c = compareAsc(a.getSampleId(), b.getSampleId());
            if (c != 0) {
                return c;
            }
            return Integer.compare(kindRank(a.getDocKind()), kindRank(b.getDocKind()));
        };
    }

    private static int kindRank(String docKind) {
        if (docKind == null) {
            return Integer.MAX_VALUE;
        }
        return switch (docKind) {
            case DocKinds.SAMPLE_QC -> 0;
            case DocKinds.ORGANOID_QC -> 1;
            case DocKinds.ORGANOID_SCORE -> 2;
            case DocKinds.MERGED -> 3;
            default -> Integer.MAX_VALUE - 1;
        };
    }

    /** 完成时间为 null 的行排最后（有完成时间的才是正常情况）。 */
    private static int compareDesc(Date a, Date b) {
        if (a == null && b == null) {
            return 0;
        }
        if (a == null) {
            return 1;
        }
        if (b == null) {
            return -1;
        }
        return b.compareTo(a);
    }

    private static int compareAsc(Long a, Long b) {
        if (a == null && b == null) {
            return 0;
        }
        if (a == null) {
            return 1;
        }
        if (b == null) {
            return -1;
        }
        return a.compareTo(b);
    }

    /**
     * 内存里切一页。
     *
     * <p>★ 用 {@code long} 算起点：{@code PageQuery.DEFAULT_PAGE_SIZE} 是
     * {@code Integer.MAX_VALUE}（「默认查全部」），{@code (pageNum-1)*pageSize} 用 int 会溢出，
     * 溢出成负数就会把 {@code subList} 打崩（或更糟：静默给错页）。
     */
    private static TableDataInfo<MpDocVo> slice(List<MpDocVo> all, MpDocQueryBo q) {
        long total = all.size();
        Page<MpDocVo> requested = q.build();
        long pageNum = requested.getCurrent();
        long pageSize = Math.max(requested.getSize(), 0);
        long from = pageNum <= 1 ? 0 : (pageNum - 1) * pageSize;
        int fromIndex = from >= total ? (int) total : (int) from;
        long endExclusive = (long) fromIndex + pageSize;
        int toIndex = endExclusive >= total || endExclusive < 0 ? (int) total : (int) endExclusive;
        List<MpDocVo> rows = fromIndex >= toIndex
            ? new ArrayList<>()
            : new ArrayList<>(all.subList(fromIndex, toIndex));
        return TableDataInfo.build(new Page<MpDocVo>(pageNum, pageSize, total).setRecords(rows));
    }

    /**
     * 完成时间是否落在 {@code [begin, end]} 里（含端点）。
     *
     * <p>★ 只给日期（{@code yyyy-MM-dd}）时：{@code begin} 按当天 00:00:00、
     * {@code end} 按当天 23:59:59 —— 否则「截止到 9 月 15 日」会把 9 月 15 日当天的文档全漏掉
     * （AUTH-EXT-003 探针 P33/P34 钉的就是这条，两边必须一致）。
     */
    private static boolean inRange(Date time, String begin, String end) {
        if (StringUtils.isBlank(begin) && StringUtils.isBlank(end)) {
            return true;
        }
        if (time == null) {
            return false;
        }
        LocalDateTime t = LocalDateTime.ofInstant(time.toInstant(), ZoneId.systemDefault());
        if (!StringUtils.isBlank(begin) && t.isBefore(parseBound(begin.trim(), false))) {
            return false;
        }
        return StringUtils.isBlank(end) || !t.isAfter(parseBound(end.trim(), true));
    }

    private static LocalDateTime parseBound(String text, boolean endOfDay) {
        try {
            if (text.length() <= 10) {
                LocalDate date = LocalDate.parse(text);
                return endOfDay ? date.atTime(23, 59, 59) : date.atStartOfDay();
            }
            return LocalDateTime.parse(text.replace('T', ' '), DATE_TIME);
        } catch (DateTimeParseException e) {
            throw new ServiceException("时间范围只能是 yyyy-MM-dd 或 yyyy-MM-dd HH:mm:ss，收到：" + text, 400);
        }
    }

    private static String trimToNull(String value) {
        return StringUtils.isBlank(value) ? null : value.trim();
    }
}
