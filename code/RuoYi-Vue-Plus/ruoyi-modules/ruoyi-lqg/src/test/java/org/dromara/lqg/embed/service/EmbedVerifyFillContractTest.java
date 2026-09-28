package org.dromara.lqg.embed.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.dromara.common.core.domain.dto.DictDataDTO;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.service.DictService;
import org.dromara.lqg.embed.domain.Embed;
import org.dromara.lqg.embed.domain.EmbedMarker;
import org.dromara.lqg.embed.domain.bo.EmbedFillBo;
import org.dromara.lqg.embed.domain.bo.EmbedSubmitBo;
import org.dromara.lqg.embed.domain.bo.EmbedVerifyBo;
import org.dromara.lqg.embed.guard.EmbedFillRules;
import org.dromara.lqg.embed.mapper.EmbedMapper;
import org.dromara.lqg.embed.mapper.EmbedMarkerMapper;
import org.dromara.lqg.sample.domain.Sample;
import org.dromara.lqg.sample.domain.bo.PatchBody;
import org.dromara.lqg.sample.guard.SubmitSegmentRules;
import org.dromara.lqg.sample.mapper.SampleMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 石蜡包埋核验时一并保存补填段（FIX V02b / issue #147）的契约测试。
 *
 * <p>病灶（活体已证，证据见交付报告）：工作台核验抽屉里工序时间、包埋人、染色、marker、操作人、备注、样本类型
 * 都显示成可填，点「判为有效并保存」请求体只有 {@code {action, paraffinBlockNo}}（{@code EmbedVerifyBo} 只收 3 个键），
 * 填的内容被<b>静默丢弃</b>，库里全是空、marker 0 行。
 *
 * <p>修法：核验请求可带 {@code fill}（补丁语义同 {@code PUT /lqg/embed}），与核验结论拼进<b>同一条 UPDATE</b>，
 * marker 在同一事务里整组替换；校验与落库列走 {@code EmbedFillWriter}（与普通保存同一份）。判为无效只收外部送样
 * 填的两项，实验室补填的 13 项带了明确 400。
 *
 * <p>钉法：假 mapper（JDK 动态代理）记下每一次 UPDATE / marker 的删与插；断言那一条 UPDATE 的 SET 子句里
 * <b>既有</b>核验结论<b>也有</b>补填段；被拒时<b>一次写都没有</b>。
 *
 * @author FIX-V02b
 */
class EmbedVerifyFillContractTest {

    /** 与容器里那个 ObjectMapper 同口径：未知键忽略（application.yml 的 fail_on_unknown_properties: false） */
    private static final ObjectMapper MAPPER = new ObjectMapper().findAndRegisterModules()
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    private static final long PENDING_EXT = 9000002006L;
    private static final long INVALID_EXT = 9000002007L;
    private static final long SAMPLE_VALID = 9000001001L;
    private static final long SAMPLE_PENDING = 9000001002L;
    private static final long OPERATOR = 9000000100L;

    /** 抽屉「判为有效并保存」发出去的那种整份补填段（15 项） */
    private static final String FULL_FILL = "{"
        + "\"sampleType\":\"组织（核验更正）\",\"organoidSourceType\":\"肝类器官\","
        + "\"tissueReceiveTime\":\"2026-09-17\",\"tissueProcessTime\":\"2026-09-17\",\"agaroseEmbedTime\":\"2026-09-18\","
        + "\"embedBy\":\" 核验员乙 \",\"dehydrateTime\":\"2026-09-19\",\"agaroseSendTime\":\"2026-09-19\","
        + "\"paraffinEmbedTime\":\"2026-09-20\",\"sectionTime\":\"2026-09-21\","
        + "\"stainTypes\":[\"IHC\",\"HE\"],\"stainOther\":\"不该落库（没选其他）\","
        + "\"markers\":[{\"markerName\":\"Ki67\",\"expression\":\"strong\"},{\"markerName\":\"\",\"expression\":\"negative\"}],"
        + "\"operatorName\":\"核验员乙\",\"remark\":\"核验时补的备注\"}";

    @BeforeAll
    static void initLambdaCache() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, Embed.class);
        TableInfoHelper.initTableInfo(assistant, EmbedMarker.class);
    }

    // ── 判为有效并保存 ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("① ★ 判为有效并保存：核验结论 + 15 项补填段在同一条 UPDATE；marker 同一事务整组替换")
    void validWithFillWritesVerdictAndFillInOneUpdate() throws Exception {
        Fake fake = new Fake(SAMPLE_VALID);
        fake.service().verifyAs(PENDING_EXT, validBo("T-E06-1"), fill(FULL_FILL), true, OPERATOR);

        assertEquals(1, fake.updates.size(), "★ 原子：核验结论与补填段必须是同一条 UPDATE");
        Map<String, Object> set = setValues(fake.updates.get(0));
        assertEquals("valid", set.get("verify_status"));
        assertEquals("T-E06-1", set.get("paraffin_block_no"));
        assertEquals(OPERATOR, set.get("verify_by"));
        assertTrue(set.containsKey("invalid_reason") && set.get("invalid_reason") == null, "改判有效清掉旧原因");
        // ★ 以前这些全被静默丢弃
        assertEquals("组织（核验更正）", set.get("sample_type"));
        assertEquals("肝类器官", set.get("organoid_source_type"));
        assertEquals(LocalDate.of(2026, 9, 17), set.get("tissue_receive_time"));
        assertEquals(LocalDate.of(2026, 9, 17), set.get("tissue_process_time"));
        assertEquals(LocalDate.of(2026, 9, 18), set.get("agarose_embed_time"));
        assertEquals(LocalDate.of(2026, 9, 19), set.get("dehydrate_time"));
        assertEquals(LocalDate.of(2026, 9, 19), set.get("agarose_send_time"));
        assertEquals(LocalDate.of(2026, 9, 20), set.get("paraffin_embed_time"));
        assertEquals(LocalDate.of(2026, 9, 21), set.get("section_time"));
        assertEquals("核验员乙", set.get("embed_by"), "去首尾空白（与普通保存同一份写法）");
        assertEquals("HE,IHC", set.get("stain_types"), "染色按固定顺序落库（与普通保存同一份规则）");
        assertTrue(set.containsKey("stain_other") && set.get("stain_other") == null, "没选「其他」→ 其他名称置空");
        assertEquals("核验员乙", set.get("operator_name"));
        assertEquals("核验时补的备注", set.get("remark"));
        // marker：先软删旧的，再按请求顺序插两条（名称空串落 null）
        assertEquals(1, fake.markerDeletes, "marker 整组替换：先删一次");
        assertEquals(List.of("Ki67:strong:0", "null:negative:1"), fake.markerInserts);
    }

    @Test
    @DisplayName("② 补丁语义：只带做完的那一步 → 只写那一列；没带的补填列一个都不碰")
    void validWithPartialFillOnlyTouchesSentKeys() throws Exception {
        Fake fake = new Fake(SAMPLE_VALID);
        fake.service().verifyAs(PENDING_EXT, validBo("T-E06-1"), fill("{\"sectionTime\":\"2026-09-21\",\"embedBy\":null}"),
            true, OPERATOR);
        Map<String, Object> set = setValues(fake.updates.get(0));
        assertEquals(LocalDate.of(2026, 9, 21), set.get("section_time"));
        assertTrue(set.containsKey("embed_by") && set.get("embed_by") == null, "带了 null = 清空");
        for (String untouched : List.of("sample_type", "organoid_source_type", "dehydrate_time", "stain_types",
            "stain_other", "operator_name", "remark", "tissue_receive_time")) {
            assertFalse(set.containsKey(untouched), "没带的键不许写：" + untouched + " ∈ " + set.keySet());
        }
        assertEquals(0, fake.markerDeletes, "没带 markers → marker 不动");
    }

    @Test
    @DisplayName("③ 不带 fill（老调用方 / accept 脚本）：UPDATE 与修复前逐列相同，补填段一个字都不动")
    void withoutFillBehavesExactlyAsBefore() {
        Fake fake = new Fake(SAMPLE_VALID);
        fake.service().verifyAs(PENDING_EXT, validBo("T-E06-1"), null, true, OPERATOR);
        assertEquals(1, fake.updates.size());
        assertEquals(Set.of("update_by", "update_time", "verify_status", "verify_by", "verify_time", "invalid_reason",
            "paraffin_block_no"), setValues(fake.updates.get(0)).keySet(), "修复前的判有效就只写这 7 列");
        assertEquals(0, fake.markerDeletes);
        assertEquals(0, fake.markerInserts.size());

        Fake inv = new Fake(SAMPLE_PENDING);
        inv.service().verifyAs(PENDING_EXT, invalidBo("信息不符"), null, true, OPERATOR);
        assertEquals(Set.of("update_by", "update_time", "verify_status", "invalid_reason", "verify_by", "verify_time"),
            setValues(inv.updates.get(0)).keySet(), "修复前的判无效就只写这 6 列");
    }

    // ── 被拒：库里一个字都不变 ─────────────────────────────────────────────────

    @Test
    @DisplayName("④ 补填段不合规（无染色与 HE 并存 / marker 表达字典外 / 超长）→ 拒，且一次写都没有")
    void invalidFillRejectedBeforeAnyWrite() {
        Fake a = new Fake(SAMPLE_VALID);
        assertThrows(ServiceException.class, () -> a.service().verifyAs(PENDING_EXT, validBo("T-E06-1"),
            fill("{\"stainTypes\":[\"NONE\",\"HE\"],\"sectionTime\":\"2026-09-21\"}"), true, OPERATOR));
        a.assertNoWrites();

        Fake b = new Fake(SAMPLE_VALID);
        assertThrows(ServiceException.class, () -> b.service().verifyAs(PENDING_EXT, validBo("T-E06-1"),
            fill("{\"markers\":[{\"markerName\":\"Ki67\",\"expression\":\"high\"}]}"), true, OPERATOR));
        b.assertNoWrites();

        Fake c = new Fake(SAMPLE_VALID);
        ServiceException tooLong = assertThrows(ServiceException.class, () -> c.service().verifyAs(PENDING_EXT,
            validBo("T-E06-1"), fill("{\"remark\":\"" + "长".repeat(501) + "\",\"embedBy\":\"" + "人".repeat(51) + "\"}"),
            true, OPERATOR));
        assertEquals(Integer.valueOf(400), tooLong.getCode());
        assertTrue(tooLong.getMessage().contains("备注不能超过 500 字") && tooLong.getMessage().contains("包埋人不能超过 50 字"),
            tooLong.getMessage());
        assertFalse(tooLong.getMessage().contains("SQL"), "不再把 SQL 回吐出去");
        c.assertNoWrites();
    }

    @Test
    @DisplayName("⑤ 判有效的老规矩照旧先判：所挂样本未核验有效 / 缺编号 / 撞号 → 拒，补填段也不落")
    void verdictRulesStillComeFirst() {
        Fake pending = new Fake(SAMPLE_PENDING);
        ServiceException e1 = assertThrows(ServiceException.class,
            () -> pending.service().verifyAs(PENDING_EXT, validBo("T-E06-1"), fill(FULL_FILL), true, OPERATOR));
        assertTrue(e1.getMessage().contains("所挂样本还未核验有效"), e1.getMessage());
        pending.assertNoWrites();

        Fake noNo = new Fake(SAMPLE_VALID);
        assertThrows(ServiceException.class,
            () -> noNo.service().verifyAs(PENDING_EXT, validBo("  "), fill(FULL_FILL), true, OPERATOR));
        noNo.assertNoWrites();

        Fake taken = new Fake(SAMPLE_VALID);
        taken.blockNoTaken = true;
        ServiceException e3 = assertThrows(ServiceException.class,
            () -> taken.service().verifyAs(PENDING_EXT, validBo("T-E01-1"), fill(FULL_FILL), true, OPERATOR));
        assertTrue(e3.getMessage().contains("已存在"), e3.getMessage());
        taken.assertNoWrites();
    }

    @Test
    @DisplayName("⑥ fill 里夹带所挂样本 / 石蜡块编号 → 400（核验时不能换样本；编号只认顶层），不静默丢")
    void fillCannotCarrySampleOrBlockNo() {
        Fake a = new Fake(SAMPLE_VALID);
        ServiceException e = assertThrows(ServiceException.class, () -> a.service().verifyAs(PENDING_EXT,
            validBo("T-E06-1"), fill("{\"sampleId\":9000001005,\"sectionTime\":\"2026-09-21\"}"), true, OPERATOR));
        assertEquals(Integer.valueOf(400), e.getCode());
        assertTrue(e.getMessage().contains("所挂样本"), e.getMessage());
        a.assertNoWrites();

        Fake b = new Fake(SAMPLE_VALID);
        ServiceException e2 = assertThrows(ServiceException.class, () -> b.service().verifyAs(PENDING_EXT,
            validBo("T-E06-1"), fill("{\"paraffinBlockNo\":\"T-OTHER\"}"), true, OPERATOR));
        assertEquals(Integer.valueOf(400), e2.getCode());
        b.assertNoWrites();
    }

    // ── 判为无效 ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("⑦ 判为无效：样本类型、类器官来源类型的更正随原因同一条 UPDATE 落库；编号不落")
    void invalidSavesSubmitKeysWithReason() throws Exception {
        Fake fake = new Fake(SAMPLE_PENDING);
        EmbedVerifyBo bo = invalidBo("所挂样本信息不符，请核对后重提");
        bo.setParaffinBlockNo("T-should-not-land");
        fake.service().verifyAs(PENDING_EXT, bo,
            fill("{\"sampleType\":\"组织（核验更正）\",\"organoidSourceType\":null}"), true, OPERATOR);
        assertEquals(1, fake.updates.size());
        Map<String, Object> set = setValues(fake.updates.get(0));
        assertEquals("invalid", set.get("verify_status"));
        assertEquals("所挂样本信息不符，请核对后重提", set.get("invalid_reason"));
        assertEquals("组织（核验更正）", set.get("sample_type"));
        assertTrue(set.containsKey("organoid_source_type") && set.get("organoid_source_type") == null);
        assertFalse(set.containsKey("paraffin_block_no"), "判无效不写石蜡块编号");
        assertEquals(0, fake.markerDeletes);
    }

    @Test
    @DisplayName("⑧ ★ 判为无效带了实验室补填的键（工序 / 染色 / marker / 备注）→ 400 写明哪几项，一次写都没有")
    void invalidRejectsLabKeys() {
        Fake fake = new Fake(SAMPLE_PENDING);
        ServiceException e = assertThrows(ServiceException.class, () -> fake.service().verifyAs(PENDING_EXT,
            invalidBo("信息不符"), fill("{\"sampleType\":\"组织\",\"dehydrateTime\":\"2026-09-19\",\"stainTypes\":[\"HE\"],"
                + "\"markers\":[{\"markerName\":\"Ki67\",\"expression\":\"strong\"}],\"remark\":\"x\"}"), true, OPERATOR));
        assertEquals(Integer.valueOf(400), e.getCode());
        for (String label : List.of("脱水时间", "染色", "marker 表达", "备注")) {
            assertTrue(e.getMessage().contains(label), "报错要写明「" + label + "」：" + e.getMessage());
        }
        assertFalse(e.getMessage().contains("样本类型、类器官来源类型是核验有效后"), "样本类型不在被拒之列：" + e.getMessage());
        fake.assertNoWrites();
    }

    @Test
    @DisplayName("⑨ 无效的送样内部直接改判有效（invalid→valid）：同样一并保存补填段")
    void invalidToValidAlsoSavesFill() throws Exception {
        Fake fake = new Fake(SAMPLE_VALID);
        fake.service().verifyAs(INVALID_EXT, validBo("T-E07-1"), fill("{\"paraffinEmbedTime\":\"2026-09-20\"}"), true, OPERATOR);
        Map<String, Object> set = setValues(fake.updates.get(0));
        assertEquals("valid", set.get("verify_status"));
        assertEquals(LocalDate.of(2026, 9, 20), set.get("paraffin_embed_time"));
        assertTrue(set.containsKey("invalid_reason") && set.get("invalid_reason") == null);
    }

    // ── 普通保存与核验时补填是同一份规则 ─────────────────────────────────────

    @Test
    @DisplayName("⑩ 同一份规则：普通保存 PUT /lqg/embed 超长也是同一句 400、一次写都没有")
    void plainUpdateSharesTheSameRules() throws Exception {
        Fake fake = new Fake(SAMPLE_VALID);
        fake.status = "valid";
        PatchBody<EmbedSubmitBo> body = PatchBody.parse(MAPPER,
            MAPPER.readTree("{\"id\":9000002006,\"remark\":\"" + "长".repeat(501) + "\"}"), EmbedSubmitBo.class);
        ServiceException e = assertThrows(ServiceException.class, () -> fake.embedService().update(body));
        assertEquals(Integer.valueOf(400), e.getCode());
        assertTrue(e.getMessage().contains("备注不能超过 500 字"), e.getMessage());
        fake.assertNoWrites();
    }

    @Test
    @DisplayName("⑪ 形状：补填段 15 项 = EmbedFillBo 的字段 = 外部两项 + 实验室 13 项；EmbedSubmitBo = 补填段 + id / 所挂样本 / 石蜡块编号")
    void fillShapeIsPinned() {
        Set<String> fillFields = fields(EmbedFillBo.class);
        assertEquals(new LinkedHashSet<>(EmbedFillRules.ALL_KEYS), fillFields);
        assertEquals(15, EmbedFillRules.ALL_KEYS.size());
        assertTrue(EmbedFillRules.SUBMIT_KEYS.stream().noneMatch(EmbedFillRules.LAB_KEYS::contains), "两组不重叠");
        Set<String> submit = new LinkedHashSet<>();
        for (Class<?> k = EmbedSubmitBo.class; k != null && k != Object.class; k = k.getSuperclass()) {
            submit.addAll(fields(k));
        }
        Set<String> expected = new LinkedHashSet<>(fillFields);
        expected.addAll(List.of("id", "sampleId", "paraffinBlockNo"));
        assertEquals(expected, submit, "普通保存与核验补填是同一个形状");
        // 外部送样两项的长度与外部提交接口（FIX V03）同一个数
        assertEquals(SubmitSegmentRules.MAX_EMBED_SAMPLE_TYPE, EmbedFillRules.MAX_SAMPLE_TYPE);
        assertEquals(SubmitSegmentRules.MAX_EMBED_ORGANOID_SOURCE_TYPE, EmbedFillRules.MAX_ORGANOID_SOURCE_TYPE);
    }

    // ── 夹具 ─────────────────────────────────────────────────────────────────

    private static EmbedVerifyBo validBo(String blockNo) {
        EmbedVerifyBo bo = new EmbedVerifyBo();
        bo.setAction("valid");
        bo.setParaffinBlockNo(blockNo);
        return bo;
    }

    private static EmbedVerifyBo invalidBo(String reason) {
        EmbedVerifyBo bo = new EmbedVerifyBo();
        bo.setAction("invalid");
        bo.setReason(reason);
        return bo;
    }

    /** 与 controller 同一种读法：fill 这一段按补丁解析（记下出现过的键） */
    private static PatchBody<EmbedFillBo> fill(String json) throws Exception {
        return PatchBody.parse(MAPPER, MAPPER.readTree(json), EmbedFillBo.class);
    }

    private static Set<String> fields(Class<?> type) {
        return Arrays.stream(type.getDeclaredFields())
            .filter(f -> !Modifier.isStatic(f.getModifiers()))
            .map(Field::getName)
            .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /**
     * 把一条 UPDATE 的 SET 子句还原成「列 → 值」（MyBatis-Plus 把值放在 paramNameValuePairs 里）。
     */
    static Map<String, Object> setValues(LambdaUpdateWrapper<?> w) {
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

    /** 假库：一条外部送样（2006 待核验 / 2007 无效）+ 所挂样本；记下每一次 UPDATE 与 marker 的删、插。 */
    private static final class Fake {

        final List<LambdaUpdateWrapper<?>> updates = new ArrayList<>();
        final List<String> markerInserts = new ArrayList<>();
        final Embed stored = new Embed();
        final long sampleId;
        int markerDeletes;
        boolean blockNoTaken;
        /** 库里这条送样的核验状态；不指定 = 2006 待核验、2007 无效 */
        String status;

        Fake(long sampleId) {
            this.sampleId = sampleId;
            stored.setSampleId(sampleId);
            stored.setSubmitSource("external");
            stored.setSubmitterId(9000000111L);
            stored.setSampleType("组织");
        }

        EmbedMapper embedMapper() {
            return (EmbedMapper) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{EmbedMapper.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "selectById" -> {
                        long id = (Long) args[0];
                        if (id != PENDING_EXT && id != INVALID_EXT) {
                            yield null;
                        }
                        stored.setId(id);
                        stored.setVerifyStatus(status != null ? status : id == PENDING_EXT ? "pending" : "invalid");
                        yield stored;
                    }
                    case "selectCount" -> blockNoTaken ? 1L : 0L;
                    case "update" -> {
                        updates.add((LambdaUpdateWrapper<?>) args[1]);
                        yield 1;
                    }
                    case "toString" -> "FakeEmbedMapper";
                    case "hashCode" -> 1;
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                });
        }

        SampleMapper sampleMapper() {
            return (SampleMapper) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{SampleMapper.class}, (proxy, method, args) -> {
                    if ("selectById".equals(method.getName())) {
                        Sample s = new Sample();
                        s.setId(sampleId);
                        s.setVerifyStatus(sampleId == SAMPLE_VALID ? "valid" : "pending");
                        return s;
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
        }

        EmbedFillWriter writer() {
            EmbedMarkerMapper markerMapper = (EmbedMarkerMapper) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{EmbedMarkerMapper.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "delete" -> {
                        markerDeletes++;
                        yield 1;
                    }
                    case "insert" -> {
                        EmbedMarker m = (EmbedMarker) args[0];
                        markerInserts.add(m.getMarkerName() + ":" + m.getExpression() + ":" + m.getSort());
                        yield 1;
                    }
                    default -> throw new UnsupportedOperationException(method.getName());
                });
            // 字典服务：给出与库里一致的两本字典（染色五个值、marker 表达三个值）
            DictService dict = (DictService) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{DictService.class}, (proxy, method, args) -> {
                    if ("getDictData".equals(method.getName())) {
                        List<String> values = EmbedDictService.DICT_STAIN.equals(args[0])
                            ? List.of("HE", "IF", "IHC", "OTHER", "NONE") : List.of("negative", "weak", "strong");
                        List<DictDataDTO> out = new ArrayList<>();
                        for (String v : values) {
                            DictDataDTO d = new DictDataDTO();
                            d.setDictValue(v);
                            d.setDictLabel(v);
                            out.add(d);
                        }
                        return out;
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
            return new EmbedFillWriter(markerMapper, new EmbedDictService(dict));
        }

        EmbedVerifyService service() {
            EmbedMapper mapper = embedMapper();
            return new EmbedVerifyService(mapper, sampleMapper(), new EmbedBlockNoGuard(mapper), writer());
        }

        EmbedService embedService() {
            EmbedMapper mapper = embedMapper();
            return new EmbedService(mapper, null, sampleMapper(), new EmbedBlockNoGuard(mapper), writer());
        }

        void assertNoWrites() {
            assertEquals(0, updates.size(), "★ 被拒之后一条 UPDATE 都没有：" + updates.size());
            assertEquals(0, markerDeletes, "★ 被拒之后 marker 一条都没删");
            assertEquals(0, markerInserts.size(), "★ 被拒之后 marker 一条都没插");
        }
    }

}
