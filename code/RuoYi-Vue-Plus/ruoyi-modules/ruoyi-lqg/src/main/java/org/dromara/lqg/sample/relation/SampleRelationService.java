package org.dromara.lqg.sample.relation;

import lombok.RequiredArgsConstructor;
import org.dromara.lqg.sample.relation.mapper.SampleRelationMapper;
import org.dromara.lqg.sample.relation.vo.SampleRelationRow;
import org.dromara.lqg.sample.relation.vo.SampleRelationVo;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 样本行的「石蜡包埋 / 冻存」关联数（工作台样本两页的关联列，Kevin 2026-09-24 本机验收）。
 *
 * <p>★ 一页只发一次查询：收整页的 id 集合（与 {@code SampleHintService.hintsOf} 同一个做法），
 * 每个请求的 id 都先铺零值 —— 调用方不需要、也不许再判 null。
 */
@Service
@RequiredArgsConstructor
public class SampleRelationService {

    private final SampleRelationMapper sampleRelationMapper;

    /**
     * 批量算关联数。
     *
     * @param sampleIds 本页的样本 id（可含 null / 重复 / 空集，本方法自己洗）
     * @return 每个去重后的非空 id 都有一条（没有关联记录的是零值），顺序按入参首次出现的顺序
     */
    public Map<Long, SampleRelationVo> countsOf(Collection<Long> sampleIds) {
        Map<Long, SampleRelationVo> counts = new LinkedHashMap<>();
        if (sampleIds == null || sampleIds.isEmpty()) {
            return counts;
        }
        List<Long> ids = sampleIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return counts;
        }
        for (Long id : ids) {
            counts.put(id, SampleRelationVo.empty());
        }
        List<SampleRelationRow> rows = sampleRelationMapper.selectCounts(ids);
        if (rows == null) {
            return counts;
        }
        for (SampleRelationRow row : rows) {
            if (row == null || row.getSampleId() == null || !counts.containsKey(row.getSampleId())) {
                continue;
            }
            counts.put(row.getSampleId(), new SampleRelationVo(
                row.getPendingEmbedCount() == null ? 0 : row.getPendingEmbedCount(),
                row.getCryoBatchCount() == null ? 0 : row.getCryoBatchCount()));
        }
        return counts;
    }

}
