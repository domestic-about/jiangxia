package org.dromara.lqg.cryo.batch.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.dromara.lqg.cryo.batch.domain.bo.CryoBatchSubmitBo;
import org.dromara.lqg.cryo.batch.domain.bo.CryoQueryBo;
import org.dromara.lqg.cryo.batch.domain.vo.CryoBatchVo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 实体 / 入参 / VO 的<b>形状</b>契约测试（CRYO-MODEL-001）—— 钉住几条「靠人盯会回退」的口径。
 *
 * <ol>
 *   <li><b>表上没有任何「剩余」类的列</b>（ADR-0010 / accept 1 第 3 段）：实体字段集与
 *       {@code field-ssot.yaml} 逐列相等，且没有一个字段名沾 {@code remain / current / stock}；</li>
 *   <li><b>入参里也没有</b>：请求夹带 {@code remainingQty} 不生效（BO 里根本没有这个字段）；</li>
 *   <li><b>VO 有</b>：{@code remainingQty} / {@code location} 是读模型的两格（accept 3 逐格断）；</li>
 *   <li><b>列表筛选 BO 不声明 {@code overdueOnly}</b>：超期判定是 CRYO-REMIND-001 的活，
 *       先声明一个没人实现的筛选 = 静默全表（空转断言的温床，SAMPLE-VERIFY-001 WARN-2）。</li>
 * </ol>
 *
 * @author CRYO-MODEL-001
 */
class CryoShapeContractTest {

    /** field-ssot.yaml 的 t_lqg_cryo_batch 业务列 + {@code @TableLogic} 的 del_flag（其余公共字段在 BaseEntity 上）。 */
    private static final Set<String> BATCH_FIELDS = Set.of(
        "id", "sampleId", "cryoName", "passage", "freezeTime", "initQty", "density",
        "inMinus80", "frozenBy", "toLn2Time", "ln2Location", "remark", "delFlag");

    /** field-ssot.yaml 的 t_lqg_cryo_flow 业务列 + del_flag。 */
    private static final Set<String> FLOW_FIELDS = Set.of(
        "id", "batchId", "flowType", "delta", "fromLocation", "operatorName", "flowTime",
        "purpose", "delFlag");

    /** 入参白名单（POST / PUT 的全部可写字段）。 */
    private static final Set<String> SUBMIT_FIELDS = Set.of(
        "id", "sampleId", "cryoName", "passage", "freezeTime", "initQty", "density",
        "inMinus80", "frozenBy", "toLn2Time", "ln2Location", "remark");

    private static Set<String> declaredFieldNames(Class<?> type) {
        return Arrays.stream(type.getDeclaredFields())
            .filter(f -> !Modifier.isStatic(f.getModifiers()))
            .map(Field::getName)
            .collect(Collectors.toSet());
    }

    @Test
    @DisplayName("① CryoBatch 的列与 SSOT 逐列相等，且没有任何「剩余 / 当前 / 库存」类的字段")
    void batchColumnsMatchSsotAndHaveNoRemainingColumn() {
        Set<String> names = declaredFieldNames(CryoBatch.class);
        assertEquals(BATCH_FIELDS, names, "t_lqg_cryo_batch 的列必须与 SSOT 逐列相等");
        assertNoRemainingLikeName(names, "CryoBatch");
    }

    @Test
    @DisplayName("② CryoFlow 的列与 SSOT 逐列相等（delta 是 Integer，符号即语义）")
    void flowColumnsMatchSsot() {
        assertEquals(FLOW_FIELDS, declaredFieldNames(CryoFlow.class));
        assertEquals(Integer.class, fieldType(CryoFlow.class, "delta"));
        assertEquals(LocalDateTime.class, fieldType(CryoFlow.class, "flowTime"));
        assertEquals(Long.class, fieldType(CryoFlow.class, "batchId"));
    }

    @Test
    @DisplayName("③ 新增 / 修改入参：白名单逐字相等，夹带 remainingQty 不生效")
    void submitBoShape() {
        Set<String> names = declaredFieldNames(CryoBatchSubmitBo.class);
        assertEquals(SUBMIT_FIELDS, names);
        assertNoRemainingLikeName(names, "CryoBatchSubmitBo");
        assertFalse(Arrays.stream(CryoBatchSubmitBo.class.getMethods())
            .anyMatch(m -> m.getName().equals("setRemainingQty")), "不该有 setRemainingQty");
        // 初始支数可改 —— 它是入参的一部分（CR-20260917-04）
        assertTrue(names.contains("initQty"));
    }

    @Test
    @DisplayName("④ VO 带读时的两格：remainingQty（Integer）与 location（String），另有四个读时带出的键")
    void voCarriesReadTimeFields() {
        Set<String> names = declaredFieldNames(CryoBatchVo.class);
        assertTrue(names.containsAll(Set.of("remainingQty", "location", "internalNo", "sourceUnitName",
            "submitNo", "sampleVerifyStatus", "handlerName", "updateByName", "mine")), names.toString());
        assertEquals(Integer.class, fieldType(CryoBatchVo.class, "remainingQty"));
        assertEquals(String.class, fieldType(CryoBatchVo.class, "location"));
        assertEquals(LocalDate.class, fieldType(CryoBatchVo.class, "freezeTime"));
        assertEquals(LocalDate.class, fieldType(CryoBatchVo.class, "toLn2Time"));
        assertEquals(LocalDateTime.class, fieldType(org.dromara.lqg.cryo.batch.domain.vo.CryoFlowVo.class, "flowTime"));
    }

    @Test
    @DisplayName("⑤ 列表筛选 BO：五个本票筛选 + 预置的 sort/mine；不声明 overdueOnly（CRYO-REMIND-001 的活）")
    void queryBoShape() {
        Set<String> names = declaredFieldNames(CryoQueryBo.class);
        assertTrue(names.containsAll(Set.of("internalNo", "cryoName", "sampleId", "location",
            "freezeTimeBegin", "freezeTimeEnd", "sort", "mine")), names.toString());
        assertFalse(names.contains("overdueOnly"),
            "超期筛选取自 CRYO-REMIND-001：本票声明一个没人实现的筛选 = 静默全表");
        assertFalse(names.contains("overdue"), names.toString());
        assertTrue(Arrays.stream(CryoQueryBo.class.getConstructors())
            .anyMatch(c -> c.getParameterCount() == 0), "必须有显式无参构造（PageQuery 只有两参构造器）");
    }

    private static void assertNoRemainingLikeName(Set<String> names, String what) {
        for (String name : names) {
            String lower = name.toLowerCase();
            assertFalse(lower.contains("remain") || lower.contains("current") || lower.contains("stock"),
                what + " 不该有「剩余」类的字段 " + name
                    + "（剩余 = init_qty + SUM(未删流水 delta)，读时算，ADR-0010）");
        }
    }

    private static Class<?> fieldType(Class<?> type, String name) {
        try {
            return type.getDeclaredField(name).getType();
        } catch (NoSuchFieldException e) {
            throw new AssertionError(type.getSimpleName() + " 缺字段 " + name, e);
        }
    }

}
