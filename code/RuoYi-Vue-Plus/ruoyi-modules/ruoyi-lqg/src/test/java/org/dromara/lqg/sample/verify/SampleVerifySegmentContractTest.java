package org.dromara.lqg.sample.verify;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.encrypt.properties.EncryptorProperties;
import org.dromara.lqg.auth.group.domain.SourceUnit;
import org.dromara.lqg.auth.group.mapper.SourceUnitMapper;
import org.dromara.lqg.auth.group.service.UnitQueryService;
import org.dromara.lqg.sample.domain.Sample;
import org.dromara.lqg.sample.domain.bo.SampleSubmitSegmentBo;
import org.dromara.lqg.sample.guard.SampleChildrenCheckers;
import org.dromara.lqg.sample.mapper.SampleMapper;
import org.dromara.lqg.sample.service.SampleFieldCipher;
import org.dromara.lqg.sample.service.SampleSubmitSegmentWriter;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 核验时一并保存送检段（FIX V02 / issue #147）的契约测试。
 *
 * <p>病灶（活体已证）：工作台核验抽屉的送检段显示成可编辑，点「判为有效并保存」只发了收样段，
 * 送检段的修改被<b>静默丢弃</b>。修法：核验请求可带 {@code submitSegment}，与核验结论拼进<b>同一条 UPDATE</b>
 * （原子），规则与工作台修改 {@code PUT /lqg/sample} 同一份。
 *
 * <p>钉法：假 {@link SampleMapper}（JDK 动态代理）记下每一次 {@code update(null, wrapper)}，
 * 断言那一条 UPDATE 的 SET 子句里<b>既有</b>核验结论<b>也有</b>送检段；被拒时<b>一次 UPDATE 都没有</b>。
 *
 * @author FIX-V02
 */
class SampleVerifySegmentContractTest {

    private static final long PENDING_TISSUE = 9000001002L;
    private static final long PENDING_ORGANOID = 9000001012L;
    private static final long OPERATOR = 9000000101L;

    @BeforeAll
    static void initLambdaCache() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), Sample.class);
    }

    // ── 用例 ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("① 判为有效并保存：送检段 7 项 + 收样段在同一条 UPDATE 里落库（供体姓名 / 住院号仍加密）")
    void validWithSegmentWritesBothInOneUpdate() {
        Fake fake = new Fake();
        SampleVerifyBo bo = validBo();
        bo.setSubmitSegment(tissueSegment());

        fake.service().verifyAs(PENDING_TISSUE, bo, true, OPERATOR);

        assertEquals(1, fake.updates.size(), "★ 原子：核验结论与送检段必须是同一条 UPDATE");
        Map<String, Object> set = setValues(fake.updates.get(0));
        assertEquals("valid", set.get("verify_status"));
        assertEquals("T-v02-01", set.get("internal_no"));
        assertEquals(LocalDate.of(2026, 9, 20), set.get("receive_date"));
        assertEquals("胆管组织（核验更正）", set.get("tissue_type"), "★ 送检段必须真的落库（以前被静默丢弃）");
        assertEquals("49", set.get("age"));
        assertEquals("male", set.get("gender"));
        assertEquals("Y", set.get("has_pathology"));
        assertEquals("核验时补的备注", set.get("remark"));
        assertEquals(9000009001L, set.get("source_unit_id"));
        assertEquals("A 医院", set.get("source_unit_name"), "选了单位 → 名称取单位表的快照");
        assertTrue(set.containsKey("organoid_type") && set.get("organoid_type") == null, "组织样本清掉类器官类型列");
        SampleFieldCipher cipher = cipher();
        assertEquals("测试供体乙（核验更正）", cipher.decrypt((String) set.get("donor_name")), "供体姓名加密落库");
        assertFalse("测试供体乙（核验更正）".equals(set.get("donor_name")), "★ 不许明文落库（ADR-0006）");
        assertEquals("ZY0000002X", cipher.decrypt((String) set.get("hospital_no")));
    }

    @Test
    @DisplayName("② 不带 submitSegment（老调用方 / accept 脚本）：送检段一个字都不动")
    void withoutSegmentLeavesSubmitColumnsAlone() {
        Fake fake = new Fake();
        fake.service().verifyAs(PENDING_TISSUE, validBo(), true, OPERATOR);
        Map<String, Object> set = setValues(fake.updates.get(0));
        for (String col : List.of("tissue_type", "donor_name", "gender", "age", "hospital_no", "has_pathology",
            "remark", "source_unit_id", "source_unit_name", "organoid_type", "passage")) {
            assertFalse(set.containsKey(col), "没带送检段却写了 " + col + "：" + set.keySet());
        }
        assertEquals("valid", set.get("verify_status"));
    }

    @Test
    @DisplayName("③ 送检段不合规（组织类型清空、性别字典外）→ 400，且一条 UPDATE 都没有（库里不变）")
    void invalidSegmentRejectedBeforeAnyWrite() {
        Fake fake = new Fake();
        SampleVerifyBo bo = validBo();
        SampleSubmitSegmentBo seg = tissueSegment();
        seg.setTissueType("  ");
        seg.setGender("M");
        bo.setSubmitSegment(seg);

        ServiceException e = assertThrows(ServiceException.class,
            () -> fake.service().verifyAs(PENDING_TISSUE, bo, true, OPERATOR));
        assertEquals(Integer.valueOf(400), e.getCode());
        assertTrue(e.getMessage().contains("组织类型不能为空") && e.getMessage().contains("性别只能是"), e.getMessage());
        assertEquals(0, fake.updates.size(), "★ 被拒之后库里一个字都不变");
    }

    @Test
    @DisplayName("④ 选的单位不存在 → 400（不再 500）；判有效缺内部编号 → 400，且都不写库")
    void unknownUnitAndMissingInternalNoAre400() {
        Fake fake = new Fake();
        SampleVerifyBo bo = validBo();
        SampleSubmitSegmentBo seg = tissueSegment();
        seg.setSourceUnitId(123L);
        bo.setSubmitSegment(seg);
        ServiceException e1 = assertThrows(ServiceException.class,
            () -> fake.service().verifyAs(PENDING_TISSUE, bo, true, OPERATOR));
        assertEquals(Integer.valueOf(400), e1.getCode());
        assertTrue(e1.getMessage().contains("来源单位不存在"), e1.getMessage());

        SampleVerifyBo noNo = validBo();
        noNo.setInternalNo(null);
        noNo.setSubmitSegment(tissueSegment());
        ServiceException e2 = assertThrows(ServiceException.class,
            () -> fake.service().verifyAs(PENDING_TISSUE, noNo, true, OPERATOR));
        assertEquals(Integer.valueOf(400), e2.getCode());
        assertEquals(0, fake.updates.size());
    }

    @Test
    @DisplayName("⑤ 判为无效：送检段随原因一起保存；收样段不落（判无效的样本不补收样信息）")
    void invalidWithSegment() {
        Fake fake = new Fake();
        SampleVerifyBo bo = new SampleVerifyBo();
        bo.setAction("invalid");
        bo.setReason("住院号写错了，请核对后重提");
        bo.setInternalNo("T-should-not-land");
        bo.setSubmitSegment(tissueSegment());

        fake.service().verifyAs(PENDING_TISSUE, bo, true, OPERATOR);
        Map<String, Object> set = setValues(fake.updates.get(0));
        assertEquals("invalid", set.get("verify_status"));
        assertEquals("住院号写错了，请核对后重提", set.get("invalid_reason"));
        assertEquals("胆管组织（核验更正）", set.get("tissue_type"));
        assertFalse(set.containsKey("internal_no"), "判无效不写内部编号");
        assertFalse(set.containsKey("receive_date"));
    }

    @Test
    @DisplayName("⑥ 类器官样本：只写来源单位、类器官类型、代数、备注；组织样本那几列不碰")
    void organoidWritesOnlyItsOwnColumns() {
        Fake fake = new Fake();
        SampleVerifyBo bo = validBo();
        SampleSubmitSegmentBo seg = new SampleSubmitSegmentBo();
        seg.setSourceUnitName("  自填单位  ");
        seg.setOrganoidType("胃类器官（更正）");
        seg.setRemark("备注");
        seg.setDonorName("类器官没有这一列");
        bo.setSubmitSegment(seg);

        fake.service().verifyAs(PENDING_ORGANOID, bo, true, OPERATOR);
        Map<String, Object> set = setValues(fake.updates.get(0));
        assertEquals("胃类器官（更正）", set.get("organoid_type"));
        assertNull(set.get("source_unit_id"));
        assertEquals("自填单位", set.get("source_unit_name"), "没选单位 → 名称去首尾空白");
        assertTrue(set.containsKey("tissue_type") && set.get("tissue_type") == null);
        assertFalse(set.containsKey("donor_name"), "类器官收样记录没有供体姓名这一列（REQ-SAMPLE-007）");
        assertTrue(set.containsKey("passage") && set.get("passage") == null, "送检段整段替换：没给代数 = 清空");
    }

    @Test
    @DisplayName("⑦ 核验时改代数（CR-20260924-10）：外部填的 P3 被实验室改成 p4 → 归一化成 P4，与核验结论同一条 UPDATE")
    void verifyCanCorrectThePassage() {
        Fake fake = new Fake();
        SampleVerifyBo bo = validBo();
        SampleSubmitSegmentBo seg = new SampleSubmitSegmentBo();
        seg.setSourceUnitName("B 大学");
        seg.setOrganoidType("肝类器官");
        seg.setPassage(" p4 ");
        bo.setSubmitSegment(seg);

        fake.service().verifyAs(PENDING_ORGANOID, bo, true, OPERATOR);
        assertEquals(1, fake.updates.size());
        Map<String, Object> set = setValues(fake.updates.get(0));
        assertEquals("valid", set.get("verify_status"));
        assertEquals("P4", set.get("passage"), "核验抽屉里改的代数要真的落库（小写 p 转大写、去空白）");
    }

    @Test
    @DisplayName("⑧ 代数格式不对（4）→ 400「代数请填 P 加数字」，核验结论也不落（一条 UPDATE 都没有）")
    void badPassageRejectsTheWholeVerify() {
        Fake fake = new Fake();
        SampleVerifyBo bo = validBo();
        SampleSubmitSegmentBo seg = new SampleSubmitSegmentBo();
        seg.setSourceUnitName("B 大学");
        seg.setOrganoidType("肝类器官");
        seg.setPassage("4");
        bo.setSubmitSegment(seg);

        ServiceException e = assertThrows(ServiceException.class,
            () -> fake.service().verifyAs(PENDING_ORGANOID, bo, true, OPERATOR));
        assertEquals(Integer.valueOf(400), e.getCode());
        assertTrue(e.getMessage().contains("代数请填 P 加数字，如 P3"), e.getMessage());
        assertEquals(0, fake.updates.size());
    }

    @Test
    @DisplayName("⑨ 组织样本不落代数：送检段里夹带 P3 → 那一列写 NULL（不报错、不落值）")
    void tissueNeverStoresAPassage() {
        Fake fake = new Fake();
        SampleVerifyBo bo = validBo();
        SampleSubmitSegmentBo seg = tissueSegment();
        seg.setPassage("P3");
        bo.setSubmitSegment(seg);

        fake.service().verifyAs(PENDING_TISSUE, bo, true, OPERATOR);
        Map<String, Object> set = setValues(fake.updates.get(0));
        assertTrue(set.containsKey("passage"), "组织样本也要显式写这一列（清掉可能的历史脏值）");
        assertNull(set.get("passage"), "★ tissue 类一律为空");
    }

    // ── 夹具 ─────────────────────────────────────────────────────────────────

    private static SampleVerifyBo validBo() {
        SampleVerifyBo bo = new SampleVerifyBo();
        bo.setAction("valid");
        bo.setReceiveDate(LocalDate.of(2026, 9, 20));
        bo.setInternalNo("T-v02-01");
        bo.setIsFixed("Y");
        bo.setOperatorName("核验员甲");
        return bo;
    }

    private static SampleSubmitSegmentBo tissueSegment() {
        SampleSubmitSegmentBo seg = new SampleSubmitSegmentBo();
        seg.setSourceUnitId(9000009001L);
        seg.setSourceUnitName("前端带回来的旧名字");
        seg.setDonorName("测试供体乙（核验更正）");
        seg.setGender("male");
        seg.setAge("49");
        seg.setHospitalNo("ZY0000002X");
        seg.setTissueType("胆管组织（核验更正）");
        seg.setHasPathology("Y");
        seg.setRemark("核验时补的备注");
        return seg;
    }

    private static SampleFieldCipher cipher() {
        EncryptorProperties props = new EncryptorProperties();
        props.setPassword("LqgTestAesKey#01");
        return new SampleFieldCipher(props);
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

    /** 假库：两条待核验样本（组织 / 类器官）+ 一个单位；记下每一次 UPDATE。 */
    private static final class Fake {

        final List<LambdaUpdateWrapper<?>> updates = new ArrayList<>();

        SampleVerifyService service() {
            SampleMapper sampleMapper = (SampleMapper) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{SampleMapper.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "selectById" -> sample((Long) args[0]);
                    case "selectCount" -> 0L;
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
                        if (Long.valueOf(9000009001L).equals(args[0])) {
                            SourceUnit unit = new SourceUnit();
                            unit.setId(9000009001L);
                            unit.setUnitName("A 医院");
                            return unit;
                        }
                        return null;
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
            SampleSubmitSegmentWriter writer = new SampleSubmitSegmentWriter(cipher(),
                new UnitQueryService(unitMapper, null, null));
            return new SampleVerifyService(sampleMapper, new SampleChildrenCheckers(List.of()), writer);
        }

        private static Sample sample(Long id) {
            Sample s = new Sample();
            s.setId(id);
            s.setVerifyStatus("pending");
            s.setSubmitSource("external");
            s.setSubmitterId(9000000111L);
            s.setSourceUnitName("A 医院");
            if (id == PENDING_TISSUE) {
                s.setSampleKind("tissue");
                return s;
            }
            if (id == PENDING_ORGANOID) {
                s.setSampleKind("organoid");
                return s;
            }
            return null;
        }
    }

}
