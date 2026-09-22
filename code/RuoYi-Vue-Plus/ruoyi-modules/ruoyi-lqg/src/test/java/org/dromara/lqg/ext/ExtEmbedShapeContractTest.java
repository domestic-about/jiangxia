package org.dromara.lqg.ext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.dev33.satoken.annotation.SaCheckRole;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;
import org.dromara.lqg.ext.controller.ExtEmbedController;
import org.dromara.lqg.ext.domain.bo.ExtEmbedQueryBo;
import org.dromara.lqg.ext.domain.bo.ExtEmbedSubmitBo;
import org.dromara.lqg.ext.domain.vo.ExtEmbedMarkerVo;
import org.dromara.lqg.ext.domain.vo.ExtEmbedVo;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AUTH-EXT-002 的形状契约：把「外部能拿到哪些键」从<b>运行期差集断言</b>再往前钉一层到<b>声明期</b>。
 *
 * <p>为什么还要这一层：ticket accept 段 1 的差集断言是
 * {@code ([.data.embeds[]|keys[]]|unique) - [24 键] == []} —— 它是<b>单向</b>的，
 * 只能抓「多出来的键」，抓不到「少了某个键」；而且只在真起了后端、真有数据时才跑得动。
 * 本类用反射扫<b>声明</b>的实例字段，两边都抓：
 * <ul>
 *   <li>{@code ExtEmbedVo} 的字段集合必须<b>恰好等于</b>那 24 键（多一个 / 少一个都红）；</li>
 *   <li>{@code ExtEmbedSubmitBo} 只有 {@code sampleId / sampleType / organoidSourceType}
 *       —— 夹带石蜡块编号 / 状态 / 包埋人 / 染色「落库」的头号形态就是它<br>
 *       （复用内部 {@code EmbedSubmitBo}）；</li>
 *   <li>{@code ExtEmbedQueryBo} 只多出 {@code onlyMine / verifyStatus} 两个筛选；</li>
 *   <li>controller 的类级角色注解与路径（ADR-0004 的 I1 配套）。</li>
 * </ul>
 *
 * <p>★ 不测 {@code sectioned} 的取值（那是运行期派生），也不重测 I3/I4 ——
 * 那两条由需求层的 {@code ExtChokepointContractTest} 扫整个 ext 包守着（本类不复述）。
 *
 * @author AUTH-EXT-002
 */
class ExtEmbedShapeContractTest {

    /**
     * {@code ExtEmbedVo} 的 24 键白名单 —— 与 ticket accept 段 1 的数组<b>逐字对应</b>。
     */
    private static final Set<String> EXT_EMBED_VO_KEYS = Set.of(
        "id", "sampleId", "submitNo", "paraffinBlockNo", "sampleType", "organoidSourceType",
        "tissueReceiveTime", "tissueProcessTime", "agaroseEmbedTime", "dehydrateTime", "agaroseSendTime",
        "paraffinEmbedTime", "sectionTime", "sectioned", "stainTypes", "stainOther", "markers",
        "verifyStatus", "invalidReason", "submitterName", "mine", "editable", "embedBy", "operatorName");

    /**
     * 对外**永远**不许出现的键（无论开关）。{@code internalNo} 不在本表里：它不是永远禁，
     * 而是由 {@code lqg.ext.show-internal-no} 决定只出现在<b>样本详情</b>上
     * （{@code ExtEmbedVo} 里连这个字段都不该有 —— 外部包埋列表 / 卡片不显示内部编号）。
     */
    private static final Set<String> NEVER_EXTERNAL_KEYS = Set.of(
        "internalNo", "remark", "verifyBy", "verifyTime", "submitSource", "submitterId",
        "sampleVerifyStatus", "sourceUnitName", "createBy", "updateBy", "createDept", "phone", "phonenumber");

    private static Set<String> instanceFields(Class<?> type) {
        return Arrays.stream(type.getDeclaredFields())
            .filter(f -> !Modifier.isStatic(f.getModifiers()))
            .map(Field::getName)
            .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    @Test
    void extEmbedVoKeysAreExactlyTheWhitelist() {
        assertEquals(EXT_EMBED_VO_KEYS, instanceFields(ExtEmbedVo.class),
            "ExtEmbedVo 的字段集合必须恰是 accept 段 1 那 24 键：多了就是对外多给数据，少了前端就没得渲染");
    }

    @Test
    void extEmbedVoNeverDeclaresInternalOnlyKeys() {
        Set<String> declared = instanceFields(ExtEmbedVo.class);
        for (String banned : NEVER_EXTERNAL_KEYS) {
            assertFalse(declared.contains(banned),
                "ExtEmbedVo 不许声明 " + banned + " —— 内部编号 / 备注 / 核验人 / 提交来源 / 冻存类字段都只能留在内部 VO");
        }
    }

    @Test
    void extEmbedVoKeepsOperatorAndEmbedByVisibleToExternal() {
        Set<String> declared = instanceFields(ExtEmbedVo.class);
        // CR-20260918-07：甲方原话「这儿应该是可以看得到操作人、包埋人，看不到冻存信息」
        assertTrue(declared.contains("operatorName"), "CR-20260918-07 起操作人必须对外可见");
        assertTrue(declared.contains("embedBy"), "CR-20260918-07 起包埋人必须对外可见");
        // 这两个字段是 I3 禁用表里**唯一**的例外（按「VO 名 + 字段名」精确豁免）；
        // 禁止的字段一个都不能有 —— 别把这个 VO 当成「整张禁用表可以放宽」的先例
        assertTrue(java.util.Collections.disjoint(declared, NEVER_EXTERNAL_KEYS),
            "只有 operatorName / embedBy 两个例外，别的内部专用字段一个都不许有");
    }

    @Test
    void markerVoOnlyCarriesNameAndExpression() {
        assertEquals(Set.of("markerName", "expression"), instanceFields(ExtEmbedMarkerVo.class),
            "对外 marker 只有名称与表达；内部 VO 的 sort 不对外");
    }

    @Test
    void submitBoOnlyCarriesTheThreeExternalFields() {
        assertEquals(Set.of("sampleId", "sampleType", "organoidSourceType"),
            instanceFields(ExtEmbedSubmitBo.class),
            "外部送样入参只有这三个键 —— 多一个键（石蜡块编号 / 状态 / 包埋人 / 染色）就是一条夹带通道");
    }

    @Test
    void queryBoOnlyAddsTheTwoExternalFilters() {
        assertEquals(Set.of("onlyMine", "verifyStatus"), instanceFields(ExtEmbedQueryBo.class),
            "外部列表只有 onlyMine / verifyStatus 两个筛选（分页参数在父类 PageQuery 上）");
    }

    @Test
    void controllerIsRoleGuardedAndUnderMpExt() {
        assertTrue(AnnotatedElementUtils.hasAnnotation(ExtEmbedController.class, RestController.class),
            "ExtEmbedController 要是 @RestController");
        SaCheckRole role = AnnotatedElementUtils.findMergedAnnotation(ExtEmbedController.class, SaCheckRole.class);
        assertNotNull(role, "缺类级 @SaCheckRole —— 内部账号就能打外部接口了");
        assertTrue(Arrays.asList(role.value()).contains("lqg_external"),
            "类级角色注解必须是 lqg_external，实际 " + Arrays.toString(role.value()));
        RequestMapping mapping = AnnotatedElementUtils.findMergedAnnotation(ExtEmbedController.class, RequestMapping.class);
        assertNotNull(mapping, "缺类级 @RequestMapping");
        assertTrue(Arrays.asList(mapping.path()).contains("/mp/ext/embed"),
            "路径必须是 /mp/ext/embed（ADR-0004 的 I1），实际 " + Arrays.toString(mapping.path()));
    }

}
