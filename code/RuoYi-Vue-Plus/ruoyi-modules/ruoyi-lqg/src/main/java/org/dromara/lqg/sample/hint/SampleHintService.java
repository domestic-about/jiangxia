package org.dromara.lqg.sample.hint;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.lqg.sample.hint.mapper.SampleHintMapper;
import org.dromara.lqg.sample.hint.vo.SampleHintRow;
import org.dromara.lqg.sample.hint.vo.SampleHintVo;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 切片染色提示（REQ-SAMPLE-010 / UI:admin.sample.list.hint / UI:mp.ledger）。
 *
 * <p>★ <b>读时计算</b>，不落库（ticket §0 口径复述 1）：在样本表上加 {@code has_section} 之类
 * 的列 = 第二个真相源，迟早和包埋记录对不上（石蜡块软删、外部送样判无效之后没人回头改它）。
 *
 * <p>★ <b>一页只发一次聚合查询</b>（ticket §0 口径复述 3）：{@link #hintsOf} 收的是整页的
 * 样本 id 集合，一条 {@code GROUP BY} 出结果。写侧一行一个 {@code selectCount} 是最自然也
 * 最错的实现 —— 一页 20 行就是 20 次往返。
 *
 * <p>★ <b>每个请求进来的 id 都保证有值</b>：没有（有效）石蜡块的样本补
 * {@link SampleHintVo#empty()}，所以调用方不需要、也不许再判 null。
 *
 * @author SAMPLE-HINT-001
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SampleHintService {

    private final SampleHintMapper sampleHintMapper;

    /**
     * 批量算提示。
     *
     * @param sampleIds 本页的样本 id（可含 null / 重复 / 空集，本方法自己洗）
     * @return <b>每个去重后的非空 id 都有一条</b>（没有有效石蜡块的是零值）——
     *         顺序按入参首次出现的顺序
     */
    public Map<Long, SampleHintVo> hintsOf(Collection<Long> sampleIds) {
        Map<Long, SampleHintVo> hints = new LinkedHashMap<>();
        if (sampleIds == null || sampleIds.isEmpty()) {
            return hints;
        }
        // 去重 + 去空：一条 IN 里重复的 id 没有意义，null 会让 IN 变成 `IN (null)`（永远不匹配）
        List<Long> ids = sampleIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return hints;
        }
        // ★ 先给每个 id 铺零值：没有包埋记录的行也必须带 hint（不许 null —— 前端会 undefined.blockCount）
        for (Long id : ids) {
            hints.put(id, SampleHintVo.empty());
        }
        // ★ 一条 GROUP BY 查完整页
        List<SampleHintRow> rows = sampleHintMapper.selectHints(ids);
        if (rows == null) {
            return hints;
        }
        for (SampleHintRow row : rows) {
            if (row == null || row.getSampleId() == null) {
                continue;
            }
            hints.put(row.getSampleId(), new SampleHintVo(
                row.getBlockCount() == null ? 0 : row.getBlockCount(),
                Boolean.TRUE.equals(row.getSectioned()),
                StainHintRules.union(row.getStainCsv())));
        }
        return hints;
    }

    /**
     * 单行查询（详情页用；列表页**不要**用它 —— 那就是逐行查）。
     *
     * <p>等价于 {@code hintsOf(List.of(id)).get(id)}，但取不到时也回零值而不是 null。
     */
    public SampleHintVo hintOf(Long sampleId) {
        if (sampleId == null) {
            return SampleHintVo.empty();
        }
        return hintsOf(List.of(sampleId)).getOrDefault(sampleId, SampleHintVo.empty());
    }

}
