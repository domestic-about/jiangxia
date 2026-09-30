package org.dromara.lqg.qc.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.common.mybatis.helper.DataPermissionHelper;
import org.dromara.lqg.qc.domain.QcOrganoidDoc;
import org.dromara.lqg.qc.domain.QcSampleDoc;
import org.dromara.lqg.qc.domain.QcScoreDoc;
import org.dromara.lqg.qc.domain.bo.QcDocListQueryBo;
import org.dromara.lqg.qc.domain.vo.QcDocListVo;
import org.dromara.lqg.qc.mapper.QcOrganoidDocMapper;
import org.dromara.lqg.qc.mapper.QcSampleDocMapper;
import org.dromara.lqg.qc.mapper.QcScoreDocMapper;
import org.dromara.lqg.sample.domain.Sample;
import org.dromara.lqg.sample.mapper.SampleMapper;
import org.dromara.lqg.sample.verify.VerifyTransitions;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 工作台「质控文档」板块的列表（{@code GET /lqg/qc/list}）。
 *
 * <p>一行 = 一个<b>已核验有效</b>的样本（组织、类器官都算；只有有效样本能开质控文档，
 * 与样本表「质控文档」按钮同一个口径）+ 它三份质控表各自的状态。
 *
 * <p>取数：先按样本主档的条件查出候选样本，再一次性把三张质控表里这批样本的行取回来
 * （每张表一条 {@code IN} 查询，不在循环里查），拼好状态后再按「进度」过滤、在内存里切页。
 * 进度是由三张表算出来的，没法下推到样本表的 SQL 里；实验室样本量是千级，内存切页够用
 * （与 {@code MpDocService} 同一个取舍）。
 *
 * <p>★ 查询包 {@link DataPermissionHelper#ignore}：行级数据范围会把该看的样本滤掉
 * （SAMPLE-MODEL-001 坑）；接口本身由 {@code lqg:qc:query} 把门，只有内部角色有。
 */
@Service
@RequiredArgsConstructor
public class QcDocListService {

    public static final String PROGRESS_NONE = "none";
    public static final String PROGRESS_DOING = "doing";
    public static final String PROGRESS_DONE = "done";

    private final SampleMapper sampleMapper;
    private final QcSampleDocMapper sampleDocMapper;
    private final QcOrganoidDocMapper organoidDocMapper;
    private final QcScoreDocMapper scoreDocMapper;

    public TableDataInfo<QcDocListVo> list(QcDocListQueryBo query) {
        QcDocListQueryBo q = query == null ? new QcDocListQueryBo() : query;
        String progress = StringUtils.isBlank(q.getProgress()) ? null : q.getProgress().trim();
        List<QcDocListVo> rows = DataPermissionHelper.ignore(() -> {
            List<Sample> samples = sampleMapper.selectList(sampleWrapper(q));
            if (samples.isEmpty()) {
                return new ArrayList<QcDocListVo>();
            }
            List<Long> ids = samples.stream().map(Sample::getId).toList();
            Map<Long, QcSampleDoc> sampleDocs = bySample(sampleDocMapper.selectList(
                new LambdaQueryWrapper<QcSampleDoc>().in(QcSampleDoc::getSampleId, ids)), QcSampleDoc::getSampleId);
            Map<Long, QcOrganoidDoc> organoidDocs = bySample(organoidDocMapper.selectList(
                new LambdaQueryWrapper<QcOrganoidDoc>().in(QcOrganoidDoc::getSampleId, ids)), QcOrganoidDoc::getSampleId);
            Map<Long, QcScoreDoc> scoreDocs = bySample(scoreDocMapper.selectList(
                new LambdaQueryWrapper<QcScoreDoc>().in(QcScoreDoc::getSampleId, ids)), QcScoreDoc::getSampleId);
            List<QcDocListVo> out = new ArrayList<>();
            for (Sample sample : samples) {
                QcDocListVo vo = toVo(sample, sampleDocs.get(sample.getId()),
                    organoidDocs.get(sample.getId()), scoreDocs.get(sample.getId()));
                if (progress == null || progress.equals(vo.getProgress())) {
                    out.add(vo);
                }
            }
            return out;
        });
        return slice(rows, q);
    }

    private static LambdaQueryWrapper<Sample> sampleWrapper(QcDocListQueryBo q) {
        LambdaQueryWrapper<Sample> w = new LambdaQueryWrapper<Sample>()
            .eq(Sample::getVerifyStatus, VerifyTransitions.VALID)
            .eq(StringUtils.isNotBlank(q.getSampleKind()), Sample::getSampleKind, trim(q.getSampleKind()))
            .ge(StringUtils.isNotBlank(q.getReceiveBegin()), Sample::getReceiveDate, parseDate(q.getReceiveBegin()))
            .le(StringUtils.isNotBlank(q.getReceiveEnd()), Sample::getReceiveDate, parseDate(q.getReceiveEnd()));
        String keyword = trim(q.getKeyword());
        if (keyword != null) {
            w.and(k -> k.like(Sample::getInternalNo, keyword)
                .or().like(Sample::getSourceUnitName, keyword)
                .or().like(Sample::getSubmitNo, keyword));
        }
        // 收样日期新的在前；同一天按 id 倒序（后录的在前），翻页顺序稳定
        return w.orderByDesc(Sample::getReceiveDate).orderByDesc(Sample::getId);
    }

    static QcDocListVo toVo(Sample sample, QcSampleDoc sampleDoc, QcOrganoidDoc organoidDoc, QcScoreDoc scoreDoc) {
        QcDocListVo vo = new QcDocListVo();
        vo.setSampleId(sample.getId());
        vo.setInternalNo(sample.getInternalNo());
        vo.setSampleKind(sample.getSampleKind());
        vo.setSourceUnitName(sample.getSourceUnitName());
        vo.setTypeName("organoid".equals(sample.getSampleKind()) ? sample.getOrganoidType() : sample.getTissueType());
        vo.setReceiveDate(sample.getReceiveDate());
        vo.setSampleQcStatus(sampleDoc == null ? null : sampleDoc.getDocStatus());
        vo.setOrganoidQcStatus(organoidDoc == null ? null : organoidDoc.getDocStatus());
        vo.setScoreStatus(scoreDoc == null ? null : scoreDoc.getDocStatus());
        vo.setTotalScore(scoreDoc == null ? null : scoreDoc.getTotalScore());
        // 没改过的行 update_time 是 null（编辑页首次打开时建的空草稿）→ 用建行时间
        vo.setLastUpdateTime(latest(
            sampleDoc == null ? null : touchedAt(sampleDoc.getUpdateTime(), sampleDoc.getCreateTime()),
            organoidDoc == null ? null : touchedAt(organoidDoc.getUpdateTime(), organoidDoc.getCreateTime()),
            scoreDoc == null ? null : touchedAt(scoreDoc.getUpdateTime(), scoreDoc.getCreateTime())));

        String[] statuses = {vo.getSampleQcStatus(), vo.getOrganoidQcStatus(), vo.getScoreStatus()};
        int published = 0;
        boolean touched = false;
        for (String status : statuses) {
            if (QcDocRules.STATUS_PUBLISHED.equals(status)) {
                published++;
            }
            if (status != null) {
                touched = true;
            }
        }
        vo.setPublishedCount(published);
        vo.setProgress(published == statuses.length ? PROGRESS_DONE : touched ? PROGRESS_DOING : PROGRESS_NONE);
        return vo;
    }

    private static <T> Map<Long, T> bySample(List<T> rows, Function<T, Long> key) {
        // 部分唯一索引保证一个样本每张表最多一行未删的；万一有重复，留第一行，不因脏数据整页 500
        return rows.stream().collect(Collectors.toMap(key, Function.identity(), (a, b) -> a));
    }

    private static Date touchedAt(Date updateTime, Date createTime) {
        return updateTime != null ? updateTime : createTime;
    }

    private static Date latest(Date... dates) {
        Date out = null;
        for (Date d : dates) {
            if (d != null && (out == null || d.after(out))) {
                out = d;
            }
        }
        return out;
    }

    private static String trim(String value) {
        return StringUtils.isBlank(value) ? null : value.trim();
    }

    private static LocalDate parseDate(String text) {
        if (StringUtils.isBlank(text)) {
            return null;
        }
        try {
            return LocalDate.parse(text.trim());
        } catch (DateTimeParseException e) {
            throw new ServiceException("收样日期只能是 yyyy-MM-dd，收到：" + text, 400);
        }
    }

    /**
     * 内存里切一页（{@code long} 算起点：默认 pageSize 是 {@code Integer.MAX_VALUE}，int 会溢出，
     * 同 {@code MpDocService#slice}）。
     */
    private static TableDataInfo<QcDocListVo> slice(List<QcDocListVo> all, QcDocListQueryBo q) {
        long total = all.size();
        Page<QcDocListVo> requested = q.build();
        long pageNum = requested.getCurrent();
        long pageSize = Math.max(requested.getSize(), 0);
        long from = pageNum <= 1 ? 0 : (pageNum - 1) * pageSize;
        int fromIndex = from >= total ? (int) total : (int) from;
        long endExclusive = (long) fromIndex + pageSize;
        int toIndex = endExclusive >= total || endExclusive < 0 ? (int) total : (int) endExclusive;
        List<QcDocListVo> rows = fromIndex >= toIndex ? new ArrayList<>() : new ArrayList<>(all.subList(fromIndex, toIndex));
        return TableDataInfo.build(new Page<QcDocListVo>(pageNum, pageSize, total).setRecords(rows));
    }
}
