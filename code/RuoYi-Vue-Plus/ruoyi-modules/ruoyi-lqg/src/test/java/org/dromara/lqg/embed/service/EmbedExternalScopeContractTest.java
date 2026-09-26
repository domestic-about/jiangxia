package org.dromara.lqg.embed.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.lqg.embed.domain.Embed;
import org.dromara.lqg.embed.mapper.EmbedMapper;
import org.dromara.lqg.ext.service.ExtEmbedAssemblyService;
import org.dromara.lqg.ext.service.ExtScopeService;
import org.dromara.lqg.ext.service.ExtScopeServiceImpl;
import org.dromara.lqg.sample.domain.Sample;
import org.dromara.lqg.sample.mapper.SampleMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * embed 包外部写侧的<b>外部隔离咽喉</b>契约测试（FIX V17 / issue #299）。
 *
 * <p>需求层给的 {@code ExtChokepointContractTest}（ADR-0004 的 I1–I4，accept 用 {@code cmp} 钉住逐字节不许改）
 * 只扫 {@code org.dromara.lqg.ext} 包；外部石蜡包埋的写侧 {@code EmbedExternalService} 住在 embed 包，
 * 于是它直接 {@code selectById} 记录与样本、用 400 / 404 之差暴露「别人的记录是否存在」，那四条扫描一条也扫不到。
 * 本类把同一条不变量延伸到 ext controller 在 ext 包<b>之外</b>的依赖上，并用假库把「不可见 = 不存在」钉成行为：
 *
 * <ol>
 *   <li><b>I5（结构）</b>：ext 包 controller 注入的、住在 ext 包之外的类，只要持有样本域的 {@code *Mapper}
 *       （样本 / 石蜡包埋 / 冻存 / 质控 / 文档……，每人一行的外部档案与微信绑定除外），就必须同时持有
 *       {@link ExtScopeService} —— 可见范围只有那一处算；{@code EmbedExternalService} 不得再持有样本的 mapper。</li>
 *   <li><b>写口无预言机</b>：{@code PUT /mp/ext/embed/{id}} 打「存在但不可见」「已软删」「不存在」三种 id，
 *       {@code POST /mp/ext/embed} 的 {@code sampleId} 打「存在但不可见」「不存在」两种 —— 业务码与提示<b>逐字相同</b>，
 *       且库里没有任何写入。</li>
 *   <li><b>读口同一句</b>：{@code GET /mp/ext/embed/{id}} 的不可见与不存在同一个 404、同一句话。</li>
 *   <li><b>看得见才会走到 400</b>：同组可见但不是本人的记录 / 样本、本人已判无效的样本 → 400（对看得见的东西说「不能改」
 *       不泄露任何存在性）；本人待核验的样本照样能挂。</li>
 * </ol>
 *
 * @author FIX-V17
 */
class EmbedExternalScopeContractTest {

    private static final long EXT_A = 9000000111L;
    private static final long EXT_B = 9000000112L;
    private static final long EXT_C = 9000000113L;
    private static final long LAB = 9000000101L;

    private static final long S1001 = 9000001001L;
    private static final long S1002 = 9000001002L;
    private static final long S1003 = 9000001003L;
    private static final long S1004 = 9000001004L;
    private static final long S1005 = 9000001005L;

    private static final long E2001 = 9000002001L;
    private static final long E2005_SOFT_DELETED = 9000002005L;
    private static final long E2006 = 9000002006L;
    private static final long NOWHERE = 999999999L;

    @BeforeAll
    static void initLambdaCache() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), Embed.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), Sample.class);
    }

    // ── I5：结构 ──────────────────────────────────────────────────────────────

    /** 每人一行、只写本人那一行的表（不是「可见范围」那条路）：持有它们不需要经范围解析器。 */
    private static final Set<String> PER_USER_MAPPERS = Set.of("ExtProfileMapper", "WxBindMapper");

    @Test
    @DisplayName("I5 ext controller 在 ext 包之外的依赖：持有样本域 mapper 的，必须同时持有 ExtScopeService")
    void i5_extFacingServicesOutsideExtPackageGoThroughTheScope() throws Exception {
        ClassPathScanningCandidateComponentProvider p = new ClassPathScanningCandidateComponentProvider(false);
        p.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
        List<String> checked = new ArrayList<>();
        for (BeanDefinition bd : p.findCandidateComponents("org.dromara.lqg.ext")) {
            Class<?> controller = Class.forName(bd.getBeanClassName());
            for (Field f : controller.getDeclaredFields()) {
                Class<?> dep = f.getType();
                if (!dep.getName().startsWith("org.dromara.lqg.") || dep.getName().startsWith("org.dromara.lqg.ext.")) {
                    continue;
                }
                List<String> scopedMappers = Arrays.stream(dep.getDeclaredFields())
                    .map(x -> x.getType().getSimpleName())
                    .filter(n -> n.endsWith("Mapper") && !PER_USER_MAPPERS.contains(n))
                    .toList();
                boolean hasScope = Arrays.stream(dep.getDeclaredFields())
                    .anyMatch(x -> ExtScopeService.class.equals(x.getType()));
                if (!scopedMappers.isEmpty()) {
                    checked.add(dep.getSimpleName());
                    assertTrue(hasScope, controller.getSimpleName() + " → " + dep.getSimpleName() + " 持有 " + scopedMappers
                        + " 却不经 ExtScopeService：外部能看到 / 改到什么必须只由范围解析器算");
                }
            }
        }
        assertTrue(checked.contains("EmbedExternalService"), "★ 扫描必须覆盖到 embed 包的外部写侧：" + checked);
        assertTrue(checked.contains("ExtSampleSubmitService"), "样本的外部写侧同一条不变量：" + checked);

        List<String> embedFields = Arrays.stream(EmbedExternalService.class.getDeclaredFields())
            .map(f -> f.getType().getSimpleName()).toList();
        assertFalse(embedFields.contains("SampleMapper"),
            "★ EmbedExternalService 不许自己查样本（能挂哪个样本只在 ExtScopeService.assertUsableForEmbed 判）：" + embedFields);
    }

    // ── 行为：写口 / 读口没有存在性预言机 ─────────────────────────────────────

    @Test
    @DisplayName("PUT /mp/ext/embed/{id}：存在但不可见 / 已软删 / 不存在 → 同一个 404、同一句话，库里不动")
    void putHasNoExistenceOracle() {
        World w = new World();
        ServiceException invisible = expect(() -> w.service().resubmit(EXT_C, E2001, null, "x", null));
        ServiceException softDeleted = expect(() -> w.service().resubmit(EXT_C, E2005_SOFT_DELETED, null, "x", null));
        ServiceException missing = expect(() -> w.service().resubmit(EXT_C, NOWHERE, null, "x", null));

        assertEquals(Integer.valueOf(404), invisible.getCode());
        assertEquals(ExtScopeService.EMBED_NOT_FOUND, invisible.getMessage());
        assertSame(invisible, softDeleted);
        assertSame(invisible, missing);
        assertEquals(0, w.writes, "★ 被拒之后库里一个字都不变");
    }

    @Test
    @DisplayName("POST /mp/ext/embed：sampleId 存在但不可见 / 不存在 → 同一个 404、同一句话，不插行")
    void postHasNoExistenceOracle() {
        World w = new World();
        ServiceException invisible = expect(() -> w.service().submit(EXT_C, S1001, "组织", null));
        ServiceException missing = expect(() -> w.service().submit(EXT_C, NOWHERE, "组织", null));
        assertEquals(Integer.valueOf(404), invisible.getCode());
        assertEquals(ExtScopeService.SAMPLE_NOT_FOUND, invisible.getMessage());
        assertSame(invisible, missing);

        // 改挂到一个不可见 / 不存在的样本：同样不许借 PUT 探库
        ServiceException moveInvisible = expect(() -> w.service().resubmit(EXT_A, E2006, S1005, "x", null));
        ServiceException moveMissing = expect(() -> w.service().resubmit(EXT_A, E2006, NOWHERE, "x", null));
        assertSame(invisible, moveInvisible);
        assertSame(invisible, moveMissing);
        assertEquals(0, w.writes);
    }

    @Test
    @DisplayName("GET /mp/ext/embed/{id}：不可见与不存在同一个 404、同一句话（读写口同一个判据）")
    void getHasTheSameMessageForInvisibleAndMissing() {
        World w = new World();
        ExtEmbedAssemblyService assembly = new ExtEmbedAssemblyService(w.scope(), null);
        ServiceException invisible = expect(() -> assembly.detail(EXT_C, E2001));
        ServiceException missing = expect(() -> assembly.detail(EXT_C, NOWHERE));
        ServiceException softDeleted = expect(() -> assembly.detail(EXT_A, E2005_SOFT_DELETED));
        assertEquals(Integer.valueOf(404), invisible.getCode());
        assertEquals(ExtScopeService.EMBED_NOT_FOUND, invisible.getMessage());
        assertSame(invisible, missing);
        assertSame(invisible, softDeleted);
    }

    @Test
    @DisplayName("看得见才会走到 400：同组别人的记录 / 样本、本人已无效的样本；本人待核验的样本照样能挂")
    void visibleButNotYoursIs400() {
        World w = new World();
        ServiceException labBlock = expect(() -> w.service().resubmit(EXT_A, E2001, null, "改实验室的块", null));
        assertEquals(Integer.valueOf(400), labBlock.getCode());
        assertTrue(labBlock.getMessage().contains("只能修改重提本人提交的送样"), labBlock.getMessage());

        ServiceException peers = expect(() -> w.service().submit(EXT_A, S1004, "组织", null));
        assertEquals(Integer.valueOf(400), peers.getCode(), "1004 是同组 extB 的：看得见、不能替他送样");
        ServiceException invalid = expect(() -> w.service().submit(EXT_A, S1003, "组织", null));
        assertEquals(Integer.valueOf(400), invalid.getCode());
        assertTrue(invalid.getMessage().contains("已判无效"), invalid.getMessage());
        assertEquals(0, w.writes);

        Long id = w.service().submit(EXT_A, S1002, "组织", "肝类器官");
        assertEquals(1, w.inserted.size());
        Embed row = w.inserted.get(0);
        assertEquals(id, row.getId());
        assertEquals("external|pending|" + EXT_A, row.getSubmitSource() + "|" + row.getVerifyStatus() + "|" + row.getSubmitterId());
    }

    @Test
    @DisplayName("缺所挂样本 / 样本类型超长 → 400 字段级提示（FIX V03），且在任何可见性查询与写库之前")
    void missingSampleIdIs400() {
        World w = new World();
        ServiceException e = expect(() -> w.service().submit(EXT_A, null, "组织", null));
        assertEquals(Integer.valueOf(400), e.getCode());
        assertTrue(e.getMessage().contains("sampleId 不能为空"), e.getMessage());
        ServiceException tooLong = expect(() -> w.service().submit(EXT_A, S1002, "样".repeat(51), null));
        assertEquals(Integer.valueOf(400), tooLong.getCode());
        assertEquals(0, w.writes);
    }

    // ── 夹具 ─────────────────────────────────────────────────────────────────

    private static ServiceException expect(Executable call) {
        return assertThrows(ServiceException.class, call);
    }

    /** 两个异常对外完全一样：业务码与 msg 逐字相同（外部能拿到的只有这两样）。 */
    private static void assertSame(ServiceException expected, ServiceException actual) {
        assertEquals(expected.getCode(), actual.getCode(), "业务码不同 = 存在性预言机");
        assertEquals(expected.getMessage(), actual.getMessage(), "提示不同 = 存在性预言机");
    }

    /**
     * 一个与 seed 同构的小世界：extA / extB 同组互看 {1001–1004}，extC 只看 {1005}；
     * 2001 挂在 1001 上（实验室录的），2006 挂在 1002 上（extA 送的、待核验），2005 已软删（{@code selectById} 取不到）。
     */
    private static final class World {

        final Map<Long, Sample> samples = new HashMap<>();
        final Map<Long, Embed> embeds = new HashMap<>();
        final List<Embed> inserted = new ArrayList<>();
        int writes;

        World() {
            sample(S1001, EXT_A, "valid");
            sample(S1002, EXT_A, "pending");
            sample(S1003, EXT_A, "invalid");
            sample(S1004, EXT_B, "valid");
            sample(S1005, EXT_C, "valid");
            embed(E2001, S1001, LAB, "valid");
            embed(E2006, S1002, EXT_A, "pending");
        }

        private void sample(long id, long submitter, String status) {
            Sample s = new Sample();
            s.setId(id);
            s.setSubmitterId(submitter);
            s.setVerifyStatus(status);
            samples.put(id, s);
        }

        private void embed(long id, long sampleId, long submitter, String status) {
            Embed e = new Embed();
            e.setId(id);
            e.setSampleId(sampleId);
            e.setSubmitterId(submitter);
            e.setVerifyStatus(status);
            embeds.put(id, e);
        }

        ExtScopeService scope() {
            SampleMapper sampleMapper = (SampleMapper) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{SampleMapper.class}, (proxy, method, args) -> {
                    if ("selectById".equals(method.getName())) {
                        return samples.get((Long) args[0]);
                    }
                    throw new UnsupportedOperationException("假样本表不支持 " + method.getName());
                });
            Map<Long, Set<Long>> visible = Map.of(
                EXT_A, Set.of(S1001, S1002, S1003, S1004),
                EXT_B, Set.of(S1001, S1002, S1003, S1004),
                EXT_C, Set.of(S1005));
            return new ExtScopeServiceImpl(sampleMapper, null, embedMapper()) {
                @Override
                public Set<Long> visibleSampleIds(Long userId) {
                    // 可见集合本身的判据（同组 + 都已核验）由 AUTH-EXT-001 的 accept 钉；这里只给定结果
                    return visible.getOrDefault(userId, Set.of());
                }
            };
        }

        EmbedMapper embedMapper() {
            return (EmbedMapper) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{EmbedMapper.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "selectById" -> embeds.get((Long) args[0]);
                    case "insert" -> {
                        Embed row = (Embed) args[0];
                        row.setId(9000002100L + inserted.size());
                        inserted.add(row);
                        writes++;
                        yield 1;
                    }
                    case "update" -> {
                        writes++;
                        yield 1;
                    }
                    default -> throw new UnsupportedOperationException("假石蜡包埋表不支持 " + method.getName());
                });
        }

        EmbedExternalService service() {
            return new EmbedExternalService(embedMapper(), scope());
        }
    }

}
