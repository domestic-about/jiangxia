package org.dromara.lqg.sample.mp;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.lqg.sample.domain.Sample;
import org.dromara.lqg.sample.domain.bo.PatchBody;
import org.dromara.lqg.sample.domain.bo.SampleSubmitBo;
import org.dromara.lqg.sample.mapper.SampleMapper;
import org.dromara.lqg.sample.service.SampleService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.time.LocalDate;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 小程序内部改样本的<b>补丁语义</b>（FIX V28）契约测试：键没出现 = 不改；键出现、值为空 = 清空；
 * 清必填项 = 400 写明哪一项；给本类别没有的字段传值 = 400 —— 任何一种都<b>不许</b>「提示已保存、库里没变」。
 *
 * <p>病灶（独立验收 V28，活体复现）：{@code PUT /mp/int/sample {"id":…,"remark":""}} 回 200「操作成功」，
 * 库里备注还是旧值（旧 {@code mergePatch} 把空串当「没传」）。
 *
 * @author FIX-V28
 */
class MpSamplePatchContractTest {

    private static final ObjectMapper MAPPER = new ObjectMapper().findAndRegisterModules();

    private static Sample tissueRow() {
        Sample s = new Sample();
        s.setId(9000001001L);
        s.setSampleKind("tissue");
        s.setVerifyStatus("valid");
        s.setSourceUnitId(9000009001L);
        s.setSourceUnitName("A 医院");
        s.setDonorName("测试供体甲");
        s.setHospitalNo("ZY0000001");
        s.setAge("56");
        s.setTissueType("肝组织");
        s.setInternalNo("T-hli01");
        s.setReceiveDate(LocalDate.of(2026, 8, 24));
        s.setRemark("原备注");
        return s;
    }

    private static PatchBody<SampleSubmitBo> parse(String json) throws Exception {
        return PatchBody.parse(MAPPER, MAPPER.readTree(json), SampleSubmitBo.class);
    }

    // ── 解析：知道「哪些键出现过」 ─────────────────────────────────────────────

    @Test
    @DisplayName("① PatchBody 区分「没带」与「带了 null」；类型不对 → 400 只报字段名、不回吐 Java 类名")
    void parseKnowsWhichKeysWereSent() throws Exception {
        PatchBody<SampleSubmitBo> p = parse("{\"id\":9000001001,\"remark\":null,\"age\":\"\",\"receiveDate\":\"2026-09-20\"}");
        assertTrue(p.has("remark") && p.has("age") && p.has("receiveDate"));
        assertFalse(p.has("donorName"), "没带的键不能算出现过");
        assertEquals(LocalDate.of(2026, 9, 20), p.value().getReceiveDate(), "@JsonFormat 日期照常解析");

        ServiceException bad = assertThrows(ServiceException.class, () -> parse("{\"id\":1,\"receiveDate\":\"2026-13-45\"}"));
        assertEquals(Integer.valueOf(400), bad.getCode());
        assertTrue(bad.getMessage().contains("receiveDate"), bad.getMessage());
        assertFalse(bad.getMessage().contains("org.dromara"), "不回吐 Java 类名：" + bad.getMessage());
        assertEquals(Integer.valueOf(400), assertThrows(ServiceException.class, () -> parse("[1,2]")).getCode());
    }

    // ── 合并：没带不改 / 带了空值清空 ──────────────────────────────────────────

    @Test
    @DisplayName("② ★ 带了空串 / null = 清空（以前当成「没传」，提示已保存而库里没变）；没带 = 沿用")
    void explicitEmptyClearsAbsentKeeps() throws Exception {
        SampleSubmitBo merged = MpSampleService.mergePatch(tissueRow(),
            parse("{\"id\":9000001001,\"remark\":\"\",\"hospitalNo\":null,\"age\":\"  \"}"));
        assertNull(merged.getRemark(), "★ 清空备注必须真的清空");
        assertNull(merged.getHospitalNo(), "★ 清空住院号必须真的清空");
        assertNull(merged.getAge());
        assertEquals("测试供体甲", merged.getDonorName(), "没带的键沿用现值（不能顺手清掉）");
        assertEquals("肝组织", merged.getTissueType());
        assertEquals("T-hli01", merged.getInternalNo());
        assertEquals("tissue", merged.getSampleKind(), "类目身份永远取库里的");
    }

    @Test
    @DisplayName("③ 来源单位：同名沿用 id；改名 = 自填单位（id 置空）；空名 = 清空（交给必填校验报错）；带 id 以 id 为准")
    void sourceUnitPair() throws Exception {
        SampleSubmitBo same = MpSampleService.mergePatch(tissueRow(), parse("{\"id\":1,\"sourceUnitName\":\"a  医院\"}"));
        assertEquals(9000009001L, same.getSourceUnitId(), "★ 小程序只发单位名、名字没改 → 单位关联不能丢");
        SampleSubmitBo renamed = MpSampleService.mergePatch(tissueRow(), parse("{\"id\":1,\"sourceUnitName\":\"C 研究所\"}"));
        assertNull(renamed.getSourceUnitId(), "★ 改成别的名字 → 以前被静默忽略（连名称一起沿用），现在按自填单位名落");
        assertEquals("C 研究所", renamed.getSourceUnitName());
        SampleSubmitBo cleared = MpSampleService.mergePatch(tissueRow(), parse("{\"id\":1,\"sourceUnitName\":\"\"}"));
        assertNull(cleared.getSourceUnitId());
        assertNull(cleared.getSourceUnitName(), "清空来源单位 → 由 SampleService 的必填校验报 400");
        SampleSubmitBo byId = MpSampleService.mergePatch(tissueRow(),
            parse("{\"id\":1,\"sourceUnitId\":9000009002,\"sourceUnitName\":\"随便\"}"));
        assertEquals(9000009002L, byId.getSourceUnitId());
        SampleSubmitBo detach = MpSampleService.mergePatch(tissueRow(), parse("{\"id\":1,\"sourceUnitId\":null}"));
        assertNull(detach.getSourceUnitId());
        assertEquals("A 医院", detach.getSourceUnitName());
        SampleSubmitBo untouched = MpSampleService.mergePatch(tissueRow(), parse("{\"id\":1,\"remark\":\"x\"}"));
        assertEquals(9000009001L, untouched.getSourceUnitId());
        assertEquals("A 医院", untouched.getSourceUnitName());
    }

    @Test
    @DisplayName("④ 给本类别没有的字段传值 → 400（以前被静默忽略）；传空值不算")
    void foreignFieldsAreRejected() throws Exception {
        Sample organoid = tissueRow();
        organoid.setSampleKind("organoid");
        ServiceException e = assertThrows(ServiceException.class,
            () -> MpSampleService.assertNoForeignFields(organoid, parse("{\"id\":1,\"donorName\":\"x\",\"gender\":\"male\"}")));
        assertEquals(Integer.valueOf(400), e.getCode());
        assertTrue(e.getMessage().contains("供体姓名") && e.getMessage().contains("性别"), e.getMessage());
        MpSampleService.assertNoForeignFields(organoid, parse("{\"id\":1,\"donorName\":\"\",\"organoidType\":\"肝类器官\"}"));
        assertThrows(ServiceException.class,
            () -> MpSampleService.assertNoForeignFields(tissueRow(), parse("{\"id\":1,\"organoidType\":\"肝类器官\"}")));
    }

    @Test
    @DisplayName("⑤ ★ 清空必填项（组织类型 / 内部编号 / 收样日期 / 来源单位）→ 400 写明哪几项，库里一个字都不写")
    void clearingRequiredFieldsIs400NotSilentSuccess() throws Exception {
        AtomicInteger updates = new AtomicInteger();
        SampleMapper mapper = (SampleMapper) Proxy.newProxyInstance(getClass().getClassLoader(),
            new Class<?>[]{SampleMapper.class}, (proxy, method, args) -> switch (method.getName()) {
                case "selectById" -> tissueRow();
                case "update" -> {
                    updates.incrementAndGet();
                    yield 1;
                }
                default -> throw new UnsupportedOperationException(method.getName());
            });
        SampleService sampleService = new SampleService(mapper, null, null, null);
        SampleSubmitBo merged = MpSampleService.mergePatch(tissueRow(),
            parse("{\"id\":9000001001,\"tissueType\":\"\",\"internalNo\":null,\"receiveDate\":null,\"sourceUnitName\":\"\"}"));
        ServiceException e = assertThrows(ServiceException.class, () -> sampleService.update(merged));
        assertEquals(Integer.valueOf(400), e.getCode());
        for (String field : Set.of("组织类型不能为空", "内部编号不能为空", "收样日期不能为空", "来源单位不能为空")) {
            assertTrue(e.getMessage().contains(field), "缺「" + field + "」：" + e.getMessage());
        }
        assertEquals(0, updates.get(), "★ 被拒之后一条 UPDATE 都没有");
    }

    @Test
    @DisplayName("⑥ 字典外的值（hasPathology=yes）经小程序补丁进来 → 同一份校验报 400（不再走到数据库约束上 500）")
    void dictionaryViolationsGoThroughTheSameRules() throws Exception {
        SampleMapper mapper = (SampleMapper) Proxy.newProxyInstance(getClass().getClassLoader(),
            new Class<?>[]{SampleMapper.class}, (proxy, method, args) -> {
                if ("selectById".equals(method.getName())) {
                    return tissueRow();
                }
                throw new UnsupportedOperationException(method.getName());
            });
        SampleSubmitBo merged = MpSampleService.mergePatch(tissueRow(), parse("{\"id\":9000001001,\"hasPathology\":\"yes\"}"));
        ServiceException e = assertThrows(ServiceException.class,
            () -> new SampleService(mapper, null, null, null).update(merged));
        assertEquals(Integer.valueOf(400), e.getCode());
        assertTrue(e.getMessage().contains("有无病理只能是"), e.getMessage());
    }

    // ── 代数（CR-20260924-10） ────────────────────────────────────────────────

    @Test
    @DisplayName("⑦ 代数走同一套补丁语义：带了 = 改、没带 = 沿用、空串 = 清空；组织样本传代数 → 400（该类别没有这一项）")
    void passageFollowsThePatchSemantics() throws Exception {
        Sample organoid = tissueRow();
        organoid.setSampleKind("organoid");
        organoid.setTissueType(null);
        organoid.setOrganoidType("肝类器官");
        organoid.setPassage("P3");

        assertEquals("p4", MpSampleService.mergePatch(organoid, parse("{\"id\":1,\"passage\":\"p4\"}")).getPassage(),
            "合并只搬值，归一化（p→P）在写路径统一做");
        assertEquals("P3", MpSampleService.mergePatch(organoid, parse("{\"id\":1,\"remark\":\"x\"}")).getPassage(),
            "没带 = 沿用库里的代数（小程序内部只改备注不该把代数洗掉）");
        assertNull(MpSampleService.mergePatch(organoid, parse("{\"id\":1,\"passage\":\"\"}")).getPassage(),
            "带了空串 = 清空（代数选填）");

        MpSampleService.assertNoForeignFields(organoid, parse("{\"id\":1,\"passage\":\"P5\"}"));
        ServiceException e = assertThrows(ServiceException.class,
            () -> MpSampleService.assertNoForeignFields(tissueRow(), parse("{\"id\":1,\"passage\":\"P5\"}")));
        assertEquals(Integer.valueOf(400), e.getCode());
        assertTrue(e.getMessage().contains("代数"), e.getMessage());
        // 前端整份表单带着空的代数是常见形状，不算越类
        MpSampleService.assertNoForeignFields(tissueRow(), parse("{\"id\":1,\"passage\":\"\"}"));
    }

}
