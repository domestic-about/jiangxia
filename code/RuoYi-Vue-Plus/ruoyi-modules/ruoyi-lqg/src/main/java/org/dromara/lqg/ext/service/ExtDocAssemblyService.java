package org.dromara.lqg.ext.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.lqg.doc.pdf.domain.vo.DocPageItemVo;
import org.dromara.lqg.doc.pdf.domain.vo.DocPagesVo;
import org.dromara.lqg.doc.render.DocKinds;
import org.dromara.lqg.doc.render.domain.vo.DocDownloadVo;
import org.dromara.lqg.doc.service.DocExternalQueryService;
import org.dromara.lqg.doc.service.DocExternalQueryService.DocExternalRow;
import org.dromara.lqg.ext.domain.bo.ExtDocQueryBo;
import org.dromara.lqg.ext.domain.vo.ExtDocDownloadVo;
import org.dromara.lqg.ext.domain.vo.ExtDocPageItemVo;
import org.dromara.lqg.ext.domain.vo.ExtDocPagesVo;
import org.dromara.lqg.ext.domain.vo.ExtDocVo;
import org.dromara.lqg.ext.guard.MaskRules;
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
import java.util.Set;

/**
 * 外部质控文档的<b>拼装</b>（{@code GET /mp/ext/doc/**} 与样本详情里的 {@code docs}）。
 *
 * <p>★ 本类<b>不持有任何 {@code *Mapper}</b>（ADR-0004 的 I4，
 * {@code ExtChokepointContractTest#i4_onlyExtScopeServiceImplTouchesMappers} 扫整个 ext 包）：
 * 可见范围经 {@link ExtScopeService}（ext 包里唯一允许碰 mapper 的实现类），
 * 文档行经 doc 域的读口 {@link DocExternalQueryService}（AUTH-EXT-003 新增）。
 * 本类只做「可见范围断言 → 拼装成对外 VO」这一件事。
 *
 * <p>★★ <b>三条口径，逐条对着 accept 1 抄</b>：
 *
 * <ol>
 *   <li><b>可见样本集合先算</b>：{@code sampleId} / {@code docKind} / 时间范围三个筛选
 *       都只是在它之上再收窄 —— 没有任何入参能把范围放大。异组用户带
 *       {@code sampleId=别人的样本} 拿到的是<b>空列表</b>（不是 404，也不是那一份文档）。</li>
 *   <li><b>「不可用」按「不存在」回 404</b>（FLOW:F-EXT-01.step2）：
 *       不可见样本、草稿、外部版没渲染成功、合并件过期 —— 四种原因对外长得一模一样。</li>
 *   <li><b>audience 连参数都没有</b>：预览与下载两个方法签名里没有 audience，
 *       doc 域读口也把它写死成 external。请求里带 {@code audience=internal} 只是被 Spring
 *       忽略掉的一个陌生查询参数（accept 1 拿它断「链接里只许出现 /external/」）。</li>
 * </ol>
 *
 * <p>★ <b>分页是内存里切的</b>：一行文档要跨「三张质控表 + 一张产物表」才凑得出来，
 * 没有一条 SQL 能既分页又给出 {@code docStatus} / 指纹，而单个外部账号的可见样本数
 * 本就不大（同组已核验者的样本）。所以先把可见样本的全部可见文档取出来、排序、再切页。
 * 这个取舍记在完工报告里（给 DOC-MP-001 的坑）。
 *
 * @author AUTH-EXT-003
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExtDocAssemblyService {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ExtScopeService extScopeService;
    private final DocExternalQueryService docExternalQueryService;

    // ══════════════════════════════════════════════════════════════════════
    // GET /mp/ext/doc/list
    // ══════════════════════════════════════════════════════════════════════

    /**
     * 外部文档清单（「按样本分组展示已完成的文档」，FLOW:F-DOC-02.step1）。
     */
    public TableDataInfo<ExtDocVo> list(Long userId, ExtDocQueryBo query) {
        ExtDocQueryBo q = query == null ? new ExtDocQueryBo() : query;
        Set<Long> visible = extScopeService.visibleSampleIds(userId);
        Set<Long> targets = targetsOf(visible, q.getSampleId());
        if (targets.isEmpty()) {
            // 可见集合为空 / sampleId 不在可见集合里 → 不查库，直接给空页
            return TableDataInfo.build(new ArrayList<>());
        }
        String kind = trimToNull(q.getDocKind());
        List<ExtDocVo> all = new ArrayList<>();
        for (DocExternalRow row : docExternalQueryService.rowsOfSamples(targets)) {
            if (kind != null && !kind.equals(row.docKind())) {
                continue;
            }
            if (!inRange(row.publishedTime(), q.getPublishedBegin(), q.getPublishedEnd())) {
                continue;
            }
            all.add(toVo(row));
        }
        all.sort(bySampleGroupThenKind(all));
        return slice(all, q);
    }

    // ══════════════════════════════════════════════════════════════════════
    // ExtSampleDetailVo.docs
    // ══════════════════════════════════════════════════════════════════════

    /**
     * 一个样本对外的文档（样本详情里的 {@code docs}）。
     *
     * <p>调用方（{@code ExtSampleAssemblyService.detail}）已经过 {@code assertVisible}，
     * 所以这里不再断言可见性（同一个样本不两处判，与 {@code ExtEmbedAssemblyService#embedsOfSample} 同款）。
     * <b>用的是与清单同一份查询</b>——详情与列表不可能给出两套答案。
     */
    public List<ExtDocVo> docsOfSample(Long sampleId) {
        if (sampleId == null) {
            return List.of();
        }
        return docExternalQueryService.rowsOfSample(sampleId).stream().map(this::toVo).toList();
    }

    // ══════════════════════════════════════════════════════════════════════
    // GET /mp/ext/doc/{sampleId}/{docKind}/pages | /download
    // ══════════════════════════════════════════════════════════════════════

    /**
     * 预览：页面图（10 分钟签名链接）。
     *
     * <p>★ 先 {@code assertVisible} 再判「有没有这一份」：异组用户猜 id → 404，
     * 看得见但那份是草稿 / 没渲染成功 → 也是 404。
     */
    public ExtDocPagesVo pages(Long userId, Long sampleId, String docKind) {
        extScopeService.assertVisible(userId, sampleId);
        DocPagesVo src = docExternalQueryService.pages(sampleId, docKind);
        ExtDocPagesVo vo = new ExtDocPagesVo();
        vo.setDocKind(src.getDocKind());
        vo.setStatus(src.getStatus());
        List<ExtDocPageItemVo> pages = new ArrayList<>();
        if (src.getPages() != null) {
            for (DocPageItemVo item : src.getPages()) {
                pages.add(new ExtDocPageItemVo(item.getPageNo(), item.getUrl()));
            }
        }
        vo.setPages(pages);
        return vo;
    }

    /**
     * 下载：10 分钟签名链接（{@code format=docx|pdf}，默认 docx）。
     *
     * <p>★ {@code audience} 不是入参 —— 请求里带 {@code audience=internal} 落不到任何地方，
     * 签出来的键里那一段永远是 {@code external}。
     */
    public ExtDocDownloadVo download(Long userId, Long sampleId, String docKind, String format) {
        extScopeService.assertVisible(userId, sampleId);
        DocDownloadVo src = docExternalQueryService.download(sampleId, docKind, format);
        ExtDocDownloadVo vo = new ExtDocDownloadVo();
        vo.setUrl(src.getUrl());
        vo.setFileName(src.getFileName());
        return vo;
    }

    // ── 私有 ─────────────────────────────────────────────────────────────────

    /**
     * 可见集合 ∩ 请求里的 {@code sampleId}。
     *
     * <p>★ 顺序是刻意的：先算可见集合再求交。反过来（先按 sampleId 查、再判可见）在这张票上
     * 就是「预览页切换条成了越权口子」——accept 1 用 extC 带 {@code sampleId=9000001001}
     * 断它必须拿到空列表。
     */
    private static Set<Long> targetsOf(Set<Long> visible, Long sampleId) {
        if (visible == null || visible.isEmpty()) {
            return Set.of();
        }
        if (sampleId == null) {
            return visible;
        }
        return visible.contains(sampleId) ? Set.of(sampleId) : Set.of();
    }

    /** 一行文档 → 对外 VO（供体姓名在这里打码，明文由 doc 域读口解密后给出）。 */
    private ExtDocVo toVo(DocExternalRow row) {
        ExtDocVo vo = new ExtDocVo();
        vo.setSampleId(row.sampleId());
        vo.setSubmitNo(row.submitNo());
        vo.setDonorNameMasked(MaskRules.maskDonorName(row.donorName()));
        vo.setDocKind(row.docKind());
        vo.setPublishedTime(row.publishedTime());
        // totalScore 只在评分表行上有（其余行保持 null → JSON 里连键都不出）
        vo.setTotalScore(row.totalScore());
        return vo;
    }

    /**
     * 排序：<b>同一个样本的几份文档必须相邻</b>（FLOW:F-DOC-02.step1 是「按样本分组展示」，
     * 分组是前端的活，但列表得先把同一组的行放在一起 —— 否则翻了页就会把一组拆到两页里）。
     *
     * <p>组的先后 = 该组里<b>最新完成</b>的那一份的时间倒序；组内按
     * 「样本质控表 → 类器官质控表 → 评分表 → 合并件」。
     */
    private static Comparator<ExtDocVo> bySampleGroupThenKind(List<ExtDocVo> rows) {
        Map<Long, Date> groupTime = new HashMap<>();
        for (ExtDocVo vo : rows) {
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
    private static TableDataInfo<ExtDocVo> slice(List<ExtDocVo> all, ExtDocQueryBo q) {
        long total = all.size();
        Page<ExtDocVo> requested = q.build();
        long pageNum = requested.getCurrent();
        long pageSize = Math.max(requested.getSize(), 0);
        long from = pageNum <= 1 ? 0 : (pageNum - 1) * pageSize;
        int fromIndex = from >= total ? (int) total : (int) from;
        long endExclusive = (long) fromIndex + pageSize;
        int toIndex = endExclusive >= total || endExclusive < 0 ? (int) total : (int) endExclusive;
        List<ExtDocVo> rows = fromIndex >= toIndex
            ? new ArrayList<>()
            : new ArrayList<>(all.subList(fromIndex, toIndex));
        return TableDataInfo.build(new Page<ExtDocVo>(pageNum, pageSize, total).setRecords(rows));
    }

    /**
     * 完成时间是否落在 {@code [begin, end]} 里（含端点）。
     *
     * <p>★ 只给日期（{@code yyyy-MM-dd}）时：{@code begin} 按当天 00:00:00、
     * {@code end} 按当天 23:59:59 —— 否则「截止到 9 月 15 日」会把 9 月 15 日当天的文档全漏掉。
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
