package org.dromara.lqg.embed.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.dromara.lqg.embed.domain.bo.EmbedQueryBo;
import org.dromara.lqg.embed.domain.bo.EmbedSubmitBo;
import org.dromara.lqg.embed.domain.bo.EmbedVerifyBo;
import org.dromara.lqg.embed.domain.vo.EmbedVo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 入参 / 实体的<b>形状</b>契约测试（EMBED-MODEL-001）—— 钉住几条「靠人盯会回退」的口径。
 *
 * <ol>
 *   <li><b>入参里没有 {@code verifyStatus}</b>（ticket §2 第 6 条）：状态只能经核验接口改，
 *       请求里夹带它不生效（accept 4 最后一段）；{@code submitSource / submitterId / verifyBy /
 *       invalidReason} 同样不在外部可写面里；</li>
 *   <li><b>七个工序时间一个不少</b>（ticket §0 口径复述 2：模板里有 7 个时间列，
 *       「原先权威里写的六个是笔误」）—— 少一个就会少一列可补填；</li>
 *   <li><b>主表上没有 marker 的两列、也没有「样本编号」那一列</b>（accept 1 的两个 counterfeit）：
 *       {@code marker_name / expression} 在 {@link EmbedMarker} 上，{@code internalNo} 读时从样本带出。</li>
 * </ol>
 *
 * @author EMBED-MODEL-001
 */
class EmbedShapeContractTest {

    /**
     * 模板里的七个工序时间（FLOW:F-EMBED-01.step1 / step2）。
     */
    private static final Set<String> PROCESS_TIMES = Set.of(
        "tissueReceiveTime", "tissueProcessTime", "agaroseEmbedTime", "dehydrateTime",
        "agaroseSendTime", "paraffinEmbedTime", "sectionTime");

    private static Set<String> declaredFieldNames(Class<?> type) {
        return Arrays.stream(type.getDeclaredFields())
            .filter(f -> !Modifier.isStatic(f.getModifiers()))
            .map(Field::getName)
            .collect(Collectors.toSet());
    }

    @Test
    @DisplayName("① 普通保存入参没有 verifyStatus（也没有核验段 / 来源段）")
    void submitBoCannotCarryVerifyStatus() {
        Set<String> names = declaredFieldNames(EmbedSubmitBo.class);
        for (String forbidden : List.of("verifyStatus", "verifyBy", "verifyTime", "invalidReason",
            "submitSource", "submitterId", "submitNo", "internalNo")) {
            assertFalse(names.contains(forbidden), "EmbedSubmitBo 不该有 " + forbidden + "：" + names);
        }
        assertFalse(Arrays.stream(EmbedSubmitBo.class.getMethods())
            .anyMatch(m -> m.getName().equals("setVerifyStatus")), "不该有 setVerifyStatus");
    }

    @Test
    @DisplayName("①b 核验入参只有 action / paraffinBlockNo / reason")
    void verifyBoShape() {
        assertEquals(Set.of("action", "paraffinBlockNo", "reason"), declaredFieldNames(EmbedVerifyBo.class));
    }

    @Test
    @DisplayName("② 七个工序时间一个不少（可空、保存不要求填完）")
    void sevenProcessTimes() {
        Set<String> submit = declaredFieldNames(EmbedSubmitBo.class);
        Set<String> entity = declaredFieldNames(Embed.class);
        Set<String> vo = declaredFieldNames(EmbedVo.class);
        for (String field : PROCESS_TIMES) {
            assertTrue(submit.contains(field), "EmbedSubmitBo 少了工序时间 " + field);
            assertTrue(entity.contains(field), "Embed 少了工序时间 " + field);
            assertTrue(vo.contains(field), "EmbedVo 少了工序时间 " + field);
        }
        assertEquals(7, PROCESS_TIMES.size());
    }

    @Test
    @DisplayName("③ 主表没有 marker_name / expression，也没有样本编号那一列")
    void mainTableHasNoMarkerColumns() {
        Set<String> entity = declaredFieldNames(Embed.class);
        assertFalse(entity.contains("markerName"), "marker 必须单独一张表：" + entity);
        assertFalse(entity.contains("expression"), "marker 必须单独一张表：" + entity);
        assertFalse(entity.contains("sampleNo"), "「样本编号」读时带出、不落库：" + entity);
        assertFalse(entity.contains("internalNo"), "「样本编号」读时带出、不落库：" + entity);
        Set<String> marker = declaredFieldNames(EmbedMarker.class);
        assertTrue(marker.containsAll(Set.of("embedId", "markerName", "expression", "sort")));
    }

    @Test
    @DisplayName("④ 染色：库里是逗号串（String），对外是数组（List<String>）")
    void stainTypesShapes() {
        assertEquals(String.class, fieldType(Embed.class, "stainTypes"));
        assertEquals(String.class, fieldType(EmbedMarker.class, "expression"));
        assertEquals(List.class, fieldType(EmbedVo.class, "stainTypes"));
        assertEquals(List.class, fieldType(EmbedVo.class, "markers"));
    }

    @Test
    @DisplayName("⑤ 列表筛选 BO 带 EMBED-MP-001 预置的 sort / mine，且有无参构造（PageQuery 5.5.3）")
    void queryBoShape() throws Exception {
        Set<String> names = declaredFieldNames(EmbedQueryBo.class);
        assertTrue(names.containsAll(Set.of("paraffinBlockNo", "internalNo", "sampleId", "stain",
            "sectionTimeBegin", "sectionTimeEnd", "verifyStatus", "submitSource", "sort", "mine")), names.toString());
        assertTrue(Arrays.stream(EmbedQueryBo.class.getConstructors())
            .anyMatch(c -> c.getParameterCount() == 0), "必须有显式无参构造（PageQuery 只有两参构造器）");
    }

    private static Class<?> fieldType(Class<?> type, String name) {
        try {
            return type.getDeclaredField(name).getType();
        } catch (NoSuchFieldException e) {
            throw new AssertionError(type.getSimpleName() + " 缺字段 " + name, e);
        }
    }

}
