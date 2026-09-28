package org.dromara.lqg.sample.hint;

import org.apache.ibatis.annotations.Select;
import org.dromara.lqg.sample.domain.Sample;
import org.dromara.lqg.sample.domain.vo.SampleVo;
import org.dromara.lqg.sample.hint.mapper.SampleHintMapper;
import org.dromara.lqg.sample.hint.vo.SampleHintRow;
import org.dromara.lqg.sample.hint.vo.SampleHintVo;
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
 * 切片染色提示的契约测试（SAMPLE-HINT-001）。
 *
 * <p>不启 Spring 容器 —— 钉的是<b>票面最容易做反</b>而那两条端到端 accept 要跑一遍后端
 * 才撞得到的四件事：
 *
 * <ol>
 *   <li><b>计数口径与 {@code EmbedChildrenChecker} 同源</b>：聚合 SQL 里必须同时有
 *       {@code e.del_flag='0'}、{@code e.verify_status='valid'}、所挂样本 {@code s.del_flag='0'}。
 *       少任何一条，seed 里的两个病灶用例就红（1008 名下只有软删的 2005；
 *       1002 名下只有待核验的 2006）；</li>
 *   <li><b>{@code NONE} 不是一种染色</b>：{@link StainHintRules#union(String)} 是纯函数，
 *       逐条钉去 {@code NONE} / 并集 / 去重 / 字典序（1004 必须是 {@code [1,true,[]]}）；</li>
 *   <li><b>一页 20 个 id 只触发 1 次查询</b>（accept 2 的 counterfeit 逐字要求）：
 *       用一个数调用次数的 {@link SampleHintMapper} 替身当 spy，断言 {@code calls == 1}
 *       且那一次拿到的是<b>全部 20 个 id</b>；</li>
 *   <li><b>没有包埋记录的行也有 hint（零值，不是 null）</b>：{@code hintsOf} 对每个请求的
 *       id 都铺一条；另外钉住读路径只有<b>一处</b> {@code hintsOf} 的调用点、
 *       且入参是整页集合（{@code rows.stream()}），不是单行。</li>
 * </ol>
 *
 * @author SAMPLE-HINT-001
 */
class SampleHintContractTest {

    // ── 1) 聚合 SQL 的口径 ────────────────────────────────────────────────────

    /**
     * 一条 GROUP BY、一个 IN、三道守卫。
     *
     * <p>★ 这是本票第一条 accept 的判据在代码侧的镜像：accept 用「接口逐行块数求和 ==
     * 直连库的独立 count」两侧对账，那条独立 SQL 是
     * {@code e.del_flag='0' AND e.verify_status='valid'} 再 join {@code s.del_flag='0'}。
     */
    @Test
    void hintSqlIsOneGroupByWithTheSameGuardsAsTheChildrenChecker() throws Exception {
        Method method = SampleHintMapper.class.getMethod("selectHints", Collection.class);
        Select select = method.getAnnotation(Select.class);
        assertNotNull(select, "提示的聚合查询必须有一条显式 SQL");

        String sql = String.join(" ", select.value());
        String flat = sql.replaceAll("\\s+", " ").toLowerCase();

        assertTrue(flat.contains("t_lqg_embed"), "块住在 t_lqg_embed 上");
        assertTrue(flat.contains("join t_lqg_sample"), "要 join 所挂样本（软删的样本不算）");
        assertTrue(flat.contains("s.del_flag = '0'"),
            "★ accept 1 的独立计数 SQL 里有 `JOIN t_lqg_sample s ON s.id=e.sample_id AND s.del_flag='0'`，"
                + "本 SQL 必须逐字同源");
        assertTrue(flat.contains("e.del_flag = '0'"),
            "★ 软删的石蜡块不算一块（seed 的 2005 挂 1008 就是为这条埋的）");
        assertTrue(flat.contains("e.verify_status = 'valid'"),
            "★ 只数已核验有效的块：外部提交还没核验 / 判了无效的送样还不是一块（CR-20260917-05；"
                + "seed 的 2006 挂 1002）");
        assertTrue(flat.contains("group by"), "★ 一条 GROUP BY 出整页，不是逐行查");
        assertTrue(flat.contains("bool_or"), "「有没有切片」= 任一有效块 section_time 非空");
        assertTrue(flat.contains("string_agg"), "各块的 stain_types 先拼成一个串，拆开交给纯函数");
        assertTrue(flat.contains("foreach") && flat.contains("in"),
            "★ 一条 IN 吃下整页的样本 id（accept 2 要「传 20 个样本 id 只触发 1 次查询」）");
        assertFalse(flat.contains("${"), "只许 #{} 占位符，不许字符串拼接（id 来自查询结果）");
        assertFalse(flat.contains("limit 1"), "整页都要，不是只算一行");
    }

    // ── 2) stains 纯函数 ─────────────────────────────────────────────────────

    /**
     * 逐条对着 seed 的五个期望值：
     * <pre>
     * 1001 → HE,IHC                  → ["HE","IHC"]
     * 1004 → NONE                    → []          ★ NONE 不是一种染色
     * 1006 → OTHER                   → ["OTHER"]
     * 1008 → 整行软删（SQL 层就查不到）→ []
     * 1002 → 待核验（SQL 层就查不到）  → []
     * </pre>
     */
    @Test
    void stainsDropNoneDedupeAndSortLexicographically() {
        assertEquals(List.of("HE", "IHC"), StainHintRules.union("HE,IHC"), "seed 1001");
        assertEquals(List.of(), StainHintRules.union("NONE"),
            "★ seed 1004：一个已切片、无染色的块 —— NONE 不是一种染色，必须回空列表");
        assertEquals(List.of("OTHER"), StainHintRules.union("OTHER"), "seed 1006");
        assertEquals(List.of(), StainHintRules.union(null), "整列都空时 STRING_AGG 回 null");
        assertEquals(List.of(), StainHintRules.union(""));
        assertEquals(List.of(), StainHintRules.union("   "));
        // 多块并集：跨块的顺序在库里没有意义 → 去重 + 字典序（HE < IF < IHC < OTHER）
        assertEquals(List.of("HE", "IHC"), StainHintRules.union("IHC,HE"), "并集要按字典序，不是落库顺序");
        assertEquals(List.of("HE", "IF", "IHC", "OTHER"),
            StainHintRules.union("HE,IF,IHC,OTHER,HE,NONE"), "去重 + 去 NONE + 字典序");
        assertEquals(List.of("HE"), StainHintRules.union("NONE,HE"), "NONE 与别的混在一起时只去掉 NONE");
        assertEquals(List.of("HE"), StainHintRules.union(" HE , ,NONE,"), "空白与空元素都不算一种染色");
        // 字典序是稳定的：与输入顺序无关
        assertEquals(StainHintRules.union("OTHER,IHC,IF,HE"), StainHintRules.union("HE,IF,IHC,OTHER"));
        // 结果必须不可变、且不是 null（前端会 .map）
        assertEquals(List.of(), StainHintRules.union("NONE"));
    }

    // ── 3) 一页只发一次查询 ───────────────────────────────────────────────────

    /**
     * ★ accept 2 的 counterfeit 逐字要求：<b>传 20 个样本 id 只触发 1 次查询</b>。
     *
     * <p>逐行查的实现（{@code for (id : ids) mapper.selectHints(List.of(id))}）在这里
     * 会数出 20，直接红。这一条不碰数据库、也不碰 Spring —— 纯粹数调用次数。
     */
    @Test
    void twentySampleIdsTriggerExactlyOneQuery() {
        CountingMapper mapper = new CountingMapper(List.of());
        SampleHintService service = new SampleHintService(mapper);
        List<Long> page = IntStream.rangeClosed(1, 20).mapToObj(i -> 9000001000L + i).toList();

        Map<Long, SampleHintVo> hints = service.hintsOf(page);

        assertEquals(1, mapper.calls, "★ 一页 20 行只许发 1 次聚合查询（逐行查会数出 20）");
        assertEquals(20, mapper.asked.size(), "那一次查询必须一次吃下整页 id（IN 的 20 个参数）");
        assertEquals(page, mapper.asked, "入参就是本页的 id 集合，顺序不动");
        assertEquals(20, hints.size(), "每个请求的 id 都要有值");
    }

    /** 空集 / 全 null 不许白跑一趟库。 */
    @Test
    void emptyOrNullIdSetDoesNotHitTheDatabase() {
        CountingMapper mapper = new CountingMapper(List.of());
        SampleHintService service = new SampleHintService(mapper);

        assertTrue(service.hintsOf(null).isEmpty());
        assertTrue(service.hintsOf(List.of()).isEmpty());
        assertEquals(0, mapper.calls, "没有 id 就不该发查询");
    }

    // ── 4) 没有包埋记录的行也有零值 ───────────────────────────────────────────

    /**
     * ★ 1002（只有待核验的 2006）与 1008（只有软删的 2005）在 SQL 层一行都查不到，
     * 也必须拿到 {@code {0,false,[]}} —— 不是 null。
     *
     * <p>同时钉住「多块样本」的行被覆盖成真值。
     */
    @Test
    void samplesWithoutValidBlocksStillGetAZeroValueNot() {
        SampleHintRow blocks = new SampleHintRow();
        blocks.setSampleId(9000001001L);
        blocks.setBlockCount(2);
        blocks.setSectioned(true);
        blocks.setStainCsv("HE,IHC");
        // 1004：一块、NONE —— SQL 会回一行，但染色是空
        SampleHintRow noneOnly = new SampleHintRow();
        noneOnly.setSampleId(9000001004L);
        noneOnly.setBlockCount(1);
        noneOnly.setSectioned(true);
        noneOnly.setStainCsv("NONE");

        SampleHintService service = new SampleHintService(new CountingMapper(List.of(blocks, noneOnly)));
        Map<Long, SampleHintVo> hints = service.hintsOf(
            List.of(9000001001L, 9000001002L, 9000001004L, 9000001008L));

        assertEquals(4, hints.size(), "四个 id 都要有值（含查不到的 1002 / 1008）");
        assertEquals(2, hints.get(9000001001L).getBlockCount().intValue());
        assertEquals(Boolean.TRUE, hints.get(9000001001L).getSectioned());
        assertEquals(List.of("HE", "IHC"), hints.get(9000001001L).getStains());
        assertEquals(List.of(), hints.get(9000001004L).getStains(), "★ NONE 不带出来（accept 1 的 [1,true,[]]）");
        assertEquals(1, hints.get(9000001004L).getBlockCount().intValue());

        for (Long emptyId : List.of(9000001002L, 9000001008L)) {
            SampleHintVo zero = hints.get(emptyId);
            assertNotNull(zero, "★ 没有有效石蜡块的样本必须有 hint 对象，不许 null（前端会 undefined.blockCount）");
            assertEquals(0, zero.getBlockCount().intValue(), "id=" + emptyId);
            assertEquals(Boolean.FALSE, zero.getSectioned(), "id=" + emptyId);
            assertEquals(List.of(), zero.getStains(), "id=" + emptyId);
        }
    }

    /** 零值工厂与详情用的一行口。 */
    @Test
    void emptyFactoryAndSingleRowLookupReturnZeroValues() {
        SampleHintVo zero = SampleHintVo.empty();
        assertEquals(0, zero.getBlockCount().intValue());
        assertEquals(Boolean.FALSE, zero.getSectioned());
        assertEquals(List.of(), zero.getStains());

        SampleHintService service = new SampleHintService(new CountingMapper(List.of()));
        assertEquals(0, service.hintOf(9000001002L).getBlockCount().intValue());
        assertEquals(0, service.hintOf(null).getBlockCount().intValue(), "null id 不许 NPE");
    }

    // ── 5) 装配点与「不落库」 ─────────────────────────────────────────────────

    /** 列表的行要带 hint：{@code SampleVo.hint} 的类型必须是 {@code SampleHintVo}。 */
    @Test
    void sampleRowCarriesTheHintObject() throws Exception {
        Field hint = SampleVo.class.getDeclaredField("hint");
        assertEquals(SampleHintVo.class, hint.getType());
    }

    /**
     * 装配点唯一、且在<b>整页</b>上：读 {@code SampleQueryService} 的源码。
     *
     * <p>钉两件事：① 入参是 {@code rows.stream()}（整页集合，不是单行）；
     * ② 读路径里 {@code hintsOf} 只出现一次（写死一处才不会有第二条口径）。
     */
    @Test
    void hintIsAssembledOncePerPageInTheSampleReadPath() throws Exception {
        String source = readSource("service/SampleQueryService.java");
        assertTrue(source.contains("fillHints(rows)"), "列表要把整页交给提示装配");
        assertTrue(source.contains("sampleHintService.hintsOf("), "提示必须由 SampleHintService 算（不在这里另写 SQL）");
        assertTrue(source.contains("rows.stream().map(SampleVo::getId)"),
            "★ 入参是本页全部样本 id（一页一次查询），不是单行");
        assertFalse(source.contains("hintOf("),
            "★ 列表路径不许用单行口（那就是逐行查）；hintOf 只给详情");
        assertEquals(1, countOccurrences(source, "hintsOf("),
            "读路径里装配提示只许有一处");
    }

    /**
     * <b>不落库</b>：样本实体上不许出现 {@code block_count} / {@code has_section} 之类的冗余列
     * （accept 2 用 {@code ddl_vs_ssot} 卡死；这里从实体侧再钉一次）。
     */
    @Test
    void sampleEntityCarriesNoRedundantHintColumns() {
        List<String> names = Arrays.stream(Sample.class.getDeclaredFields()).map(Field::getName).toList();
        for (String forbidden : List.of("blockCount", "hasSection", "sectioned", "stainTypes", "stainHint")) {
            assertFalse(names.contains(forbidden),
                "★ 读时计算：样本表上不许有 " + forbidden + " 这类冗余列（会变成第二个真相源）");
        }
    }

    // ── 测试替身 ─────────────────────────────────────────────────────────────

    /** 数调用次数的 mapper spy（不引 Mockito：本模块的测试类路径只有 JUnit）。 */
    static final class CountingMapper implements SampleHintMapper {

        private final List<SampleHintRow> rows;
        private int calls;
        private List<Long> asked = List.of();

        CountingMapper(List<SampleHintRow> rows) {
            this.rows = rows;
        }

        @Override
        public List<SampleHintRow> selectHints(Collection<Long> sampleIds) {
            this.calls++;
            this.asked = new ArrayList<>(sampleIds);
            return rows;
        }
    }

    private static int countOccurrences(String source, String needle) {
        int count = 0;
        int from = 0;
        while (true) {
            int at = source.indexOf(needle, from);
            if (at < 0) {
                return count;
            }
            count++;
            from = at + needle.length();
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
