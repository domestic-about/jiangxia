package org.dromara.lqg.embed.mp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.dev33.satoken.annotation.SaCheckRole;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import org.dromara.lqg.embed.domain.bo.EmbedQueryBo;
import org.dromara.lqg.embed.domain.bo.EmbedSubmitBo;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 小程序内部石蜡包埋接口（{@code /mp/int/embed/**}）的契约测试（EMBED-MP-001）。
 *
 * <p>本类不启 Spring 容器，钉的是<b>结构</b>与<b>纯判据</b>两件事 ——
 * 它们恰好是票面最容易做反、而端到端 accept 又最费时去撞的部分：
 *
 * <ol>
 *   <li><b>角色闸的位置</b>：类级 {@code @SaCheckRole("lqg_internal")}。外部角色
 *       （{@code lqg_external}）打这一组必须 403；把注解挪到某个方法上、或整个漏掉，
 *       外部就拿到了全部石蜡块连同包埋人 / 操作人。EMBED-WEB-001 / AUTH-EXT-002 的报告里
 *       都点了「{@code --as extA GET /mp/int/embed/list} 期望 403」，accept 1 有一段就是它。</li>
 *   <li><b>{@code PUT} / {@code POST} 的入口形状</b>：契约第 63 行是 {@code PUT /mp/int/embed}
 *       与 {@code POST /mp/int/embed}（body 里带 id），方法上不能有 {@code @PathVariable} ——
 *       形状错了前端永远 404 / 405（而 405 上角色切面根本不跑，SAMPLE-MP-001 坑 7）。</li>
 *   <li><b>★ 只有一条读写路径</b>：本包<b>不注入任何 {@code *Mapper}</b>（与 ADR-0004 的 I4
 *       同理的纪律，只是这条走的是内部侧）：读走 {@code EmbedQueryService.list/detail}、
 *       写走 {@code EmbedService.create/update}。自己拼一份 wrapper / update 就等于
 *       「小程序能过、工作台不能过」的分叉。</li>
 *   <li><b>{@code sort=recent} / {@code mine} 两个字面量</b>：只认 {@code recent}
 *       （大小写不敏感、忽略首尾空白），别的值走「内部管理表格页的全表」那一支；
 *       {@code mine} 默认 {@code null} = 中心全员（CR-20260918-07 的默认口径）。</li>
 *   <li><b>入参里没有状态</b>：{@code EmbedSubmitBo} 里没有 {@code verifyStatus} ——
 *       「待核验 / 无效改不动」这条口径不是「读到了再丢掉」，是接口里没有这个键。</li>
 * </ol>
 *
 * @author EMBED-MP-001
 */
class MpEmbedContractTest {

    @Test
    void controllerIsRestMappedUnderMpIntEmbedWithClassLevelInternalRole() {
        Class<MpEmbedController> type = MpEmbedController.class;
        assertNotNull(AnnotatedElementUtils.findMergedAnnotation(type, RestController.class),
            "MpEmbedController 必须是一个 @RestController");
        RequestMapping mapping = AnnotatedElementUtils.findMergedAnnotation(type, RequestMapping.class);
        assertNotNull(mapping, "MpEmbedController 缺类级 @RequestMapping");
        assertEquals(java.util.List.of("/mp/int/embed"), Arrays.asList(mapping.path()),
            "类级路径必须是 /mp/int/embed（契约第 63 行）");

        SaCheckRole role = AnnotatedElementUtils.findMergedAnnotation(type, SaCheckRole.class);
        assertNotNull(role, "★ 类级 @SaCheckRole 不许漏：漏了外部角色就能读到全部石蜡块连同包埋人");
        assertEquals(java.util.List.of("lqg_internal"), Arrays.asList(role.value()),
            "类级角色必须是 lqg_internal（契约「通用」一节的表）");
        // 方法级不许自带另一套角色（会盖掉类级这一份、出现两个判据）
        for (Method method : MpEmbedController.class.getDeclaredMethods()) {
            assertNull(AnnotatedElementUtils.findMergedAnnotation(method, SaCheckRole.class),
                "角色闸只在类上写一次：" + method.getName());
        }
    }

    @Test
    void updateIsAFlatPutWithoutPathVariable() {
        // 契约：PUT /mp/int/embed（body 里带 id），不是 PUT /mp/int/embed/{id}
        Method update = Arrays.stream(MpEmbedController.class.getDeclaredMethods())
            .filter(m -> AnnotatedElementUtils.hasAnnotation(m, PutMapping.class))
            .findFirst()
            .orElseThrow(() -> new AssertionError("MpEmbedController 没有 PUT 处理方法"));
        assertEquals(1, update.getParameterCount(), "PUT /mp/int/embed 只收请求体，不该有路径变量");
        // FIX V33：收原始 JSON（没带 = 不动、带了空值 = 清空），形状仍是 EmbedSubmitBo（接口文档里标着）
        assertEquals(com.fasterxml.jackson.databind.JsonNode.class, update.getParameterTypes()[0],
            "PUT 收原始 JSON 再按 EmbedSubmitBo 解析（PatchBody）");
        assertEquals(EmbedSubmitBo.class, update.getParameters()[0]
            .getAnnotation(io.swagger.v3.oas.annotations.parameters.RequestBody.class).content()[0].schema().implementation());
        assertEquals(0, update.getAnnotationsByType(PathVariable.class).length);
    }

    @Test
    void createIsAPostOnTheSameFlatPath() {
        Method create = Arrays.stream(MpEmbedController.class.getDeclaredMethods())
            .filter(m -> AnnotatedElementUtils.hasAnnotation(m, PostMapping.class))
            .findFirst()
            .orElseThrow(() -> new AssertionError("MpEmbedController 没有 POST 处理方法"));
        assertEquals(1, create.getParameterCount(), "POST /mp/int/embed 只收请求体");
        assertEquals(EmbedSubmitBo.class, create.getParameterTypes()[0]);
    }

    /**
     * ★ 单一路径守卫：mp 包里不许出现 {@code *Mapper} 字段。
     *
     * <p>「补填就是修改」的正确性依赖 {@code EmbedService.update} 的 patch 语义与状态闸；
     * 本包自己注入 mapper 直写，就会绕过编号唯一性、染色字典与状态闸三处判据
     * （accept 2 的三段「被拒之后库里不变」正是靠这三处）。
     */
    @Test
    void mpPackageNeverHoldsAMapper() {
        for (Class<?> type : new Class<?>[]{MpEmbedService.class, MpEmbedQueryBo.class, MpEmbedController.class}) {
            for (Field field : type.getDeclaredFields()) {
                assertFalse(field.getType().getSimpleName().endsWith("Mapper"),
                    type.getSimpleName() + " 不该注入 " + field.getType().getSimpleName()
                        + "：读写必须走 embed 包的 service（只有一条判据）");
            }
        }
    }

    @Test
    void queryBoCarriesTheMpOnlySearchBoxAndDefaultsToTheWholeCentre() {
        MpEmbedQueryBo bo = new MpEmbedQueryBo();
        assertNull(bo.getMine(), "mine 不带 = 中心全部内部人员（默认不是本人）");
        assertNull(bo.getSort(), "sort 不带 = 内部管理表格页的全表（待核验置顶）");
        assertNull(bo.getKeyword(), "keyword 不带 = 不搜索");
        bo.setMine(Boolean.TRUE);
        assertTrue(bo.getMine(), "开关打开才带 mine=true");
        bo.setSort("recent");
        assertTrue(EmbedQueryBo.isRecentSort(bo.getSort()), "历史编辑记录那一档的排序键");
        bo.setKeyword("T-E01");
        assertEquals("T-E01", bo.getKeyword(), "搜索框（石蜡块编号 / 内部编号）落在父 BO 的同一条读路径上");
    }

    @Test
    void recentSortOnlyRecognisesTheRecentLiteral() {
        assertTrue(EmbedQueryBo.isRecentSort("recent"));
        assertTrue(EmbedQueryBo.isRecentSort("  RECENT "), "大小写与首尾空白不敏感");
        assertFalse(EmbedQueryBo.isRecentSort(null), "不带 sort = 内部管理表格页的全表");
        assertFalse(EmbedQueryBo.isRecentSort(""), "空串按不带处理");
        assertFalse(EmbedQueryBo.isRecentSort("create_time"), "别把任意列名当排序键收下（SQL 注入面）");
    }

    @Test
    void submitBoHasNoVerifyStatusSoSavingCannotBypassVerification() {
        // 连同父类（FIX V02b 起补填段上移到 EmbedFillBo）一起看：继承来的也不许有
        java.util.List<Field> fields = new java.util.ArrayList<>();
        for (Class<?> k = EmbedSubmitBo.class; k != null && k != Object.class; k = k.getSuperclass()) {
            fields.addAll(Arrays.asList(k.getDeclaredFields()));
        }
        assertFalse(fields.stream()
                .anyMatch(f -> "verifyStatus".equals(f.getName())),
            "★ 入参里不许有 verifyStatus：状态只经 PUT /lqg/embed/{id}/verify 改，"
                + "一次普通保存绕不过核验（accept 2 最后一段断的就是它）");
    }

}
