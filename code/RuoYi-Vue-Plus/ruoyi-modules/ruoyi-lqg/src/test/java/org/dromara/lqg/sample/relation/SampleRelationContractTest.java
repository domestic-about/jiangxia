package org.dromara.lqg.sample.relation;

import org.apache.ibatis.annotations.Select;
import org.dromara.lqg.sample.domain.vo.SampleVo;
import org.dromara.lqg.sample.relation.mapper.SampleRelationMapper;
import org.dromara.lqg.sample.relation.vo.SampleRelationRow;
import org.dromara.lqg.sample.relation.vo.SampleRelationVo;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * 样本行「石蜡包埋 / 冻存」关联数的契约测试（Kevin 2026-09-24 本机验收：四张表之间的关系）。
 *
 * <p>不启 Spring，钉四件事：
 * <ol>
 *   <li>SQL 的两个判据对准目标页：待核验送样 = 未删 + {@code verify_status='pending'}；
 *       冻存批次 = 未删（与 {@code CryoChildrenChecker} 同源，冻存没有核验状态）；</li>
 *   <li>一页只发一次查询（逐行查会数出 20）；</li>
 *   <li>没有关联记录的行也有零值，不是 null；</li>
 *   <li>装配点在列表读路径上、整页一次；{@code SampleVo.relation} 的类型对。</li>
 * </ol>
 */
class SampleRelationContractTest {

    @Test
    void countSqlUsesTheSameGuardsAsTheTargetPages() throws Exception {
        Method method = SampleRelationMapper.class.getMethod("selectCounts", Collection.class);
        Select select = method.getAnnotation(Select.class);
        assertNotNull(select, "关联数必须有一条显式 SQL");
        String flat = String.join(" ", select.value()).replaceAll("\\s+", " ").toLowerCase();

        assertTrue(flat.contains("from t_lqg_embed e"), "待核验送样数在 t_lqg_embed 上");
        assertTrue(flat.contains("e.del_flag = '0'"), "软删的送样不算");
        assertTrue(flat.contains("e.verify_status = 'pending'"),
            "★ 只数待核验的：点「待核验 N」过去是石蜡包埋页按样本 + 待核验筛的条数");
        assertTrue(flat.contains("from t_lqg_cryo_batch c"), "冻存批次数在 t_lqg_cryo_batch 上");
        assertTrue(flat.contains("c.del_flag = '0'"), "★ 软删的批次不算（与 CryoChildrenChecker 同一判据）");
        assertFalse(flat.contains("c.verify_status"), "冻存批次没有核验状态列");
        assertTrue(flat.contains("foreach") && flat.contains(" in"), "★ 一条 IN 吃下整页 id");
        assertFalse(flat.contains("${"), "只许 #{} 占位符");
        assertFalse(flat.contains("limit"), "整页都要");
    }

    @Test
    void twentySampleIdsTriggerExactlyOneQuery() {
        CountingMapper mapper = new CountingMapper(List.of());
        SampleRelationService service = new SampleRelationService(mapper);
        List<Long> page = IntStream.rangeClosed(1, 20).mapToObj(i -> 9000001000L + i).toList();

        Map<Long, SampleRelationVo> counts = service.countsOf(page);

        assertEquals(1, mapper.calls, "★ 一页 20 行只许发 1 次查询");
        assertEquals(page, mapper.asked, "那一次查询吃下整页 id，顺序不动");
        assertEquals(20, counts.size(), "每个请求的 id 都要有值");
    }

    @Test
    void emptyNullAndDuplicateIdsAreWashed() {
        CountingMapper mapper = new CountingMapper(List.of());
        SampleRelationService service = new SampleRelationService(mapper);

        assertTrue(service.countsOf(null).isEmpty());
        assertTrue(service.countsOf(List.of()).isEmpty());
        assertTrue(service.countsOf(Arrays.asList(null, null)).isEmpty());
        assertEquals(0, mapper.calls, "没有 id 就不该发查询");

        service.countsOf(Arrays.asList(1L, null, 1L, 2L));
        assertEquals(List.of(1L, 2L), mapper.asked, "去空、去重后再查");
    }

    /**
     * seed 口径：1001 有 2 批冻存、没有待核验送样；1002 有 1 条待核验送样（2006）、没有冻存；
     * 1005 什么都没有（SQL 仍会回一行 0/0，这里模拟「查不到」也要铺零值）。
     */
    @Test
    void samplesWithoutRelationsStillGetAZeroValue() {
        SampleRelationRow r1001 = row(9000001001L, 0, 2);
        SampleRelationRow r1002 = row(9000001002L, 1, 0);
        SampleRelationRow stranger = row(42L, 9, 9);
        SampleRelationService service = new SampleRelationService(new CountingMapper(List.of(r1001, r1002, stranger)));

        Map<Long, SampleRelationVo> counts = service.countsOf(List.of(9000001001L, 9000001002L, 9000001005L));

        assertEquals(3, counts.size(), "只回请求的 id（查询回来多余的行不混进来）");
        assertEquals(2, counts.get(9000001001L).getCryoBatchCount());
        assertEquals(0, counts.get(9000001001L).getPendingEmbedCount());
        assertEquals(1, counts.get(9000001002L).getPendingEmbedCount());
        SampleRelationVo zero = counts.get(9000001005L);
        assertNotNull(zero, "★ 没有关联记录的样本也必须有对象，不许 null");
        assertEquals(0, zero.getPendingEmbedCount());
        assertEquals(0, zero.getCryoBatchCount());
    }

    @Test
    void sampleRowCarriesTheRelationObjectAssembledOncePerPage() throws Exception {
        Field relation = SampleVo.class.getDeclaredField("relation");
        assertEquals(SampleRelationVo.class, relation.getType());

        String source = readSource("service/SampleQueryService.java");
        assertTrue(source.contains("fillRelations(rows)"), "列表要把整页交给关联数装配");
        assertEquals(1, source.split("sampleRelationService\\.countsOf\\(", -1).length - 1,
            "读路径里装配关联数只许有一处");
        assertTrue(source.contains("SampleRelationVo.empty()"), "装配时第二道保险：取不到也是零值");
    }

    // ── 测试替身 ─────────────────────────────────────────────────────────────

    private static SampleRelationRow row(Long sampleId, int pending, int cryo) {
        SampleRelationRow row = new SampleRelationRow();
        row.setSampleId(sampleId);
        row.setPendingEmbedCount(pending);
        row.setCryoBatchCount(cryo);
        return row;
    }

    /** 数调用次数的 mapper 替身（本模块测试类路径只有 JUnit）。 */
    static final class CountingMapper implements SampleRelationMapper {

        private final List<SampleRelationRow> rows;
        private int calls;
        private List<Long> asked = List.of();

        CountingMapper(List<SampleRelationRow> rows) {
            this.rows = rows;
        }

        @Override
        public List<SampleRelationRow> selectCounts(Collection<Long> sampleIds) {
            this.calls++;
            this.asked = new ArrayList<>(sampleIds);
            return rows;
        }
    }

    private static String readSource(String relative) throws Exception {
        Path path = Path.of("src/main/java/org/dromara/lqg/sample", relative);
        if (!Files.exists(path)) {
            path = Path.of("ruoyi-modules/ruoyi-lqg/src/main/java/org/dromara/lqg/sample", relative);
        }
        if (!Files.exists(path)) {
            return fail("找不到 " + relative + "（cwd=" + Path.of(".").toAbsolutePath() + "）");
        }
        return Files.readString(path);
    }

}
