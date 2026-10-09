package org.dromara.lqg.sample.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.encrypt.properties.EncryptorProperties;
import org.dromara.lqg.auth.group.domain.SourceUnit;
import org.dromara.lqg.auth.group.mapper.SourceUnitMapper;
import org.dromara.lqg.auth.group.service.UnitQueryService;
import org.dromara.lqg.auth.service.ExtProfileQueryService;
import org.dromara.lqg.ext.domain.bo.ExtOrganoidSubmitBo;
import org.dromara.lqg.ext.service.ExtScopeService;
import org.dromara.lqg.sample.domain.Sample;
import org.dromara.lqg.sample.guard.SampleChildrenCheckers;
import org.dromara.lqg.sample.mapper.SampleMapper;
import org.dromara.lqg.sample.verify.SampleVerifyService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 外部填类器官收样记录带「代数」（CR-20260924-10：甲方 2026-09-24 测试问题记录表第 18 行）的契约测试。
 *
 * <p>钉三件事（不起 Spring，假 mapper 记下每一次 INSERT / UPDATE）：
 * <ol>
 *   <li>外部<b>提交</b>（{@code POST /mp/ext/organoid}）：代数落库，且与工作台同一份归一化（{@code p3 → P3}）；</li>
 *   <li>代数格式不对 → 400、一条都不写（与其它送检段字段同一份 {@code SubmitSegmentRules}）；</li>
 *   <li>外部<b>改自己待核验 / 无效的记录后重提</b>（{@code PUT /mp/ext/organoid/{id}}）也能改代数 ——
 *       入参 → 送检段是逐字段手工拷的，漏拷一个字段就是「外部改了、库里没变」。</li>
 * </ol>
 *
 * @author CR-20260924-10
 */
class ExtOrganoidPassageContractTest {

    private static final long EXT_USER = 9000000111L;
    private static final long PENDING_ORGANOID = 9000001012L;

    @BeforeAll
    static void initLambdaCache() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), Sample.class);
    }

    @Test
    @DisplayName("① 外部提交带代数 p3 → 落库 P3（待核验、外部来源），与工作台同一份归一化")
    void submitStoresThePassage() {
        Fake fake = new Fake();
        ExtOrganoidSubmitBo bo = new ExtOrganoidSubmitBo();
        bo.setSourceUnitName("A 医院");
        bo.setSpecies("鼠兔");
        bo.setOrganoidType("肝类器官");
        bo.setPassage(" p3 ");
        bo.setRemark("外部填的备注");

        fake.service().submitOrganoid(EXT_USER, bo);

        assertEquals(1, fake.inserts.size());
        Sample row = fake.inserts.get(0);
        assertEquals("organoid", row.getSampleKind());
        assertEquals("pending", row.getVerifyStatus());
        assertEquals("external", row.getSubmitSource());
        assertEquals("P3", row.getPassage(), "★ 外部填的代数要落库（p→P、去空白）");
        assertEquals("肝类器官", row.getOrganoidType());
        assertEquals("鼠兔", row.getSpecies(), "种属随外部提交落库（CR-20261009-18）");
        assertEquals(9000009001L, row.getSourceUnitId(), "单位名与本人绑定的单位同名 → 挂上 id（FIX V01 口径不变）");
    }

    @Test
    @DisplayName("② 外部提交代数写成 3 → 400「代数请填 P 加数字，如 P3」，一行都不写；不填代数照样能提交（选填）")
    void badPassageIsRejectedAndBlankIsFine() {
        Fake fake = new Fake();
        ExtOrganoidSubmitBo bad = new ExtOrganoidSubmitBo();
        bad.setSourceUnitName("A 医院");
        bad.setSpecies("鼠兔");
        bad.setOrganoidType("肝类器官");
        bad.setPassage("3");
        ServiceException e = assertThrows(ServiceException.class, () -> fake.service().submitOrganoid(EXT_USER, bad));
        assertEquals(Integer.valueOf(400), e.getCode());
        assertTrue(e.getMessage().contains("代数请填 P 加数字，如 P3"), e.getMessage());
        assertEquals(0, fake.inserts.size(), "被拒之后一行都不写");

        ExtOrganoidSubmitBo blank = new ExtOrganoidSubmitBo();
        blank.setSourceUnitName("A 医院");
        blank.setSpecies("鼠兔");
        blank.setOrganoidType("肝类器官");
        fake.service().submitOrganoid(EXT_USER, blank);
        assertNull(fake.inserts.get(0).getPassage(), "没填代数 = NULL（选填）");
    }

    @Test
    @DisplayName("③ 外部改自己待核验的类器官收样后重提：代数改成 P5 真的落库（不是被入参拷贝漏掉）")
    void resubmitCanChangeThePassage() {
        Fake fake = new Fake();
        ExtOrganoidSubmitBo bo = new ExtOrganoidSubmitBo();
        bo.setSourceUnitName("A 医院");
        bo.setSpecies("鼠兔");
        bo.setOrganoidType("肝类器官");
        bo.setPassage("P5");

        fake.service().resubmitOrganoid(EXT_USER, PENDING_ORGANOID, bo);

        assertEquals(1, fake.updates.size());
        Map<String, Object> set = setValues(fake.updates.get(0));
        assertEquals("P5", set.get("passage"));
        assertEquals("肝类器官", set.get("organoid_type"));
        assertEquals("鼠兔", set.get("species"), "★ 重提的手工拷贝带上了种属（漏拷 = 改了库里没变）");
    }

    @Test
    @DisplayName("④ 入参 → 送检段的手工拷贝带上了代数（外部类器官四项：来源单位、类器官类型、代数、备注）")
    void segmentCopyCarriesThePassage() {
        ExtOrganoidSubmitBo bo = new ExtOrganoidSubmitBo();
        bo.setSourceUnitName("A 医院");
        bo.setSpecies("鼠兔");
        bo.setOrganoidType("肝类器官");
        bo.setPassage("P2");
        bo.setRemark("r");
        var seg = ExtSampleSubmitService.segmentOf(bo);
        assertEquals("P2", seg.getPassage());
        assertEquals("鼠兔", seg.getSpecies());
        assertEquals("肝类器官", seg.getOrganoidType());
        assertEquals("r", seg.getRemark());
        assertNull(seg.getDonorName(), "类器官入参里根本没有组织样本字段");
    }

    // ── 夹具 ─────────────────────────────────────────────────────────────────

    /** UPDATE 的 SET 子句 → 「列 → 值」（与 SampleVerifySegmentContractTest 同一写法）。 */
    private static Map<String, Object> setValues(LambdaUpdateWrapper<?> w) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (String part : w.getSqlSet().split(",")) {
            int eq = part.indexOf('=');
            String col = part.substring(0, eq).trim();
            String expr = part.substring(eq + 1).trim();
            String key = expr.substring(expr.lastIndexOf('.') + 1, expr.length() - 1);
            out.put(col, w.getParamNameValuePairs().get(key));
        }
        return out;
    }

    private static SampleFieldCipher cipher() {
        EncryptorProperties props = new EncryptorProperties();
        props.setPassword("LqgTestAesKey#01");
        return new SampleFieldCipher(props);
    }

    /** 假库：一条本人的待核验类器官收样 + 一个单位（A 医院，本人绑定的）；记下 INSERT / UPDATE。 */
    private static final class Fake {

        final List<Sample> inserts = new ArrayList<>();
        final List<LambdaUpdateWrapper<?>> updates = new ArrayList<>();

        ExtSampleSubmitService service() {
            SampleMapper sampleMapper = (SampleMapper) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{SampleMapper.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "selectById" -> pendingOrganoid((Long) args[0]);
                    case "nextSubmitNoSeq" -> 99L;
                    case "insert" -> {
                        Sample s = (Sample) args[0];
                        s.setId(9000009999L);
                        inserts.add(s);
                        yield 1;
                    }
                    case "update" -> {
                        updates.add((LambdaUpdateWrapper<?>) args[1]);
                        yield 1;
                    }
                    case "toString" -> "FakeSampleMapper";
                    case "hashCode" -> 1;
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                });
            SourceUnitMapper unitMapper = (SourceUnitMapper) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{SourceUnitMapper.class}, (proxy, method, args) -> {
                    if ("selectById".equals(method.getName())) {
                        return Long.valueOf(9000009001L).equals(args[0]) ? unitA() : null;
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
            SampleSubmitSegmentWriter writer = new SampleSubmitSegmentWriter(cipher(),
                new UnitQueryService(unitMapper, null, null));
            ExtScopeService scope = (ExtScopeService) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{ExtScopeService.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "assertVisible" -> null;
                    case "isMine", "editable" -> true;
                    default -> throw new UnsupportedOperationException(method.getName());
                });
            ExtProfileQueryService profiles = new ExtProfileQueryService(null, null, null) {
                @Override
                public SourceUnit boundUnitOf(Long userId) {
                    return unitA();
                }
            };
            SampleVerifyService verifyService = new SampleVerifyService(sampleMapper,
                new SampleChildrenCheckers(List.of()), writer);
            return new ExtSampleSubmitService(sampleMapper, new SampleSubmitNoGenerator(sampleMapper),
                verifyService, scope, profiles, writer);
        }

        private static SourceUnit unitA() {
            SourceUnit unit = new SourceUnit();
            unit.setId(9000009001L);
            unit.setUnitName("A 医院");
            return unit;
        }

        private static Sample pendingOrganoid(Long id) {
            if (id == null || id != PENDING_ORGANOID) {
                return null;
            }
            Sample s = new Sample();
            s.setId(id);
            s.setSampleKind("organoid");
            s.setVerifyStatus("pending");
            s.setSubmitSource("external");
            s.setSubmitterId(EXT_USER);
            s.setSourceUnitId(9000009001L);
            s.setSourceUnitName("A 医院");
            s.setSpecies("鼠兔");
            s.setOrganoidType("肝类器官");
            s.setPassage("P3");
            return s;
        }
    }

}
