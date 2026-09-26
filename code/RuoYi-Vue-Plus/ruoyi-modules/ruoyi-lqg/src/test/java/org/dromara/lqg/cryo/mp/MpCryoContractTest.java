package org.dromara.lqg.cryo.mp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.dev33.satoken.annotation.SaCheckRole;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.dromara.lqg.cryo.batch.domain.bo.CryoBatchSubmitBo;
import org.dromara.lqg.cryo.batch.domain.bo.CryoQueryBo;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 小程序内部冻存接口（{@code /mp/int/cryo/**}）的契约测试（CRYO-MP-001）。
 *
 * <p>本类不启 Spring 容器，钉的是<b>结构</b>与<b>纯判据</b>两件事 ——
 * 它们恰好是票面最容易做反、而端到端 accept 又最费时去撞的部分：
 *
 * <ol>
 *   <li><b>角色闸的位置</b>：类级 {@code @SaCheckRole("lqg_internal")}。外部角色
 *       （{@code lqg_external}）打这一组必须 403（accept 1 / accept 2 的最后一段各有一段），
 *       把注解挪到某个方法上、或整个漏掉，外部就拿到了冻存位置与剩余支数。</li>
 *   <li><b>{@code PUT} / {@code POST} 的入口形状</b>：契约第 73 行是 {@code POST|PUT
 *       /mp/int/cryo/batch}（body 里带 id），方法上不能有 {@code @PathVariable} ——
 *       形状错了前端永远 404 / 405（而 405 上角色切面根本不跑，SAMPLE-MP-001 坑 7）。</li>
 *   <li>★★ <b>没有写流水的口</b>：本控制器声明的映射<b>精确等于</b>五条
 *       （list / detail / create / update / flows），类上<b>没有</b> {@code @DeleteMapping}，
 *       也没有任何 {@code flow} / {@code to-ln2} 的 POST / PUT / DELETE。
 *       accept 2 会真调那四条路径要求「没有这个接口」且库里不变。</li>
 *   <li><b>只有一条读写路径</b>：本包<b>不注入任何 {@code *Mapper}</b>（与 ADR-0004 的 I4
 *       同理的纪律，只是这条走的是内部侧）：读走 {@code CryoQueryService}（同一个
 *       {@code list}，自带 {@code tabCounts / overdue / overdueDays / overdueOnly}）、
 *       写走 {@code CryoBatchService}（同一套 {@code CryoBalanceChecker} 逐笔校验）、
 *       流水读走 {@code CryoFlowService.list}。自己拼一份 wrapper / update 就等于
 *       「小程序能过、工作台不能过」的分叉。</li>
 *   <li><b>{@code mine} 不兼职当「历史模式」</b>：{@code sort=recent} 与 {@code mine=true}
 *       是两个独立参数，默认都不带（= 中心全部内部人员，CR-20260918-07）。</li>
 *   <li><b>冻存数量可改</b>：{@code CryoBatchSubmitBo} 里有 {@code initQty}，且<b>没有</b>
 *       {@code remainingQty} —— 剩余永远是读时算的（ADR-0010）。</li>
 * </ol>
 *
 * @author CRYO-MP-001
 */
class MpCryoContractTest {

    @Test
    void controllerIsRestMappedUnderMpIntCryoBatchWithClassLevelInternalRole() {
        Class<MpCryoController> type = MpCryoController.class;
        assertNotNull(AnnotatedElementUtils.findMergedAnnotation(type, RestController.class),
            "MpCryoController 必须是一个 @RestController");
        RequestMapping mapping = AnnotatedElementUtils.findMergedAnnotation(type, RequestMapping.class);
        assertNotNull(mapping, "MpCryoController 缺类级 @RequestMapping");
        assertEquals(List.of("/mp/int/cryo/batch"), Arrays.asList(mapping.path()),
            "类级路径必须是 /mp/int/cryo/batch（契约第 73 行）");

        SaCheckRole role = AnnotatedElementUtils.findMergedAnnotation(type, SaCheckRole.class);
        assertNotNull(role, "★ 类级 @SaCheckRole 不许漏：漏了外部角色就能读到冻存位置与剩余支数");
        assertEquals(List.of("lqg_internal"), Arrays.asList(role.value()),
            "类级角色必须是 lqg_internal（契约「通用」一节的表）");
        // 方法级不许自带另一套角色（会盖掉类级这一份、出现两个判据）
        for (Method method : MpCryoController.class.getDeclaredMethods()) {
            assertNull(AnnotatedElementUtils.findMergedAnnotation(method, SaCheckRole.class),
                "角色闸只在类上写一次：" + method.getName());
        }
    }

    @Test
    void createIsAPostOnTheFlatBatchPathAndUpdateIsAFlatPut() {
        Method create = Arrays.stream(MpCryoController.class.getDeclaredMethods())
            .filter(m -> AnnotatedElementUtils.hasAnnotation(m, PostMapping.class))
            .findFirst()
            .orElseThrow(() -> new AssertionError("MpCryoController 没有 POST 处理方法"));
        assertEquals(1, create.getParameterCount(), "POST /mp/int/cryo/batch 只收请求体");
        assertEquals(CryoBatchSubmitBo.class, create.getParameterTypes()[0], "POST 的入参类型应是 CryoBatchSubmitBo");

        // 契约：PUT /mp/int/cryo/batch（body 里带 id），不是 PUT /mp/int/cryo/batch/{id}
        Method update = Arrays.stream(MpCryoController.class.getDeclaredMethods())
            .filter(m -> AnnotatedElementUtils.hasAnnotation(m, PutMapping.class))
            .findFirst()
            .orElseThrow(() -> new AssertionError("MpCryoController 没有 PUT 处理方法"));
        assertEquals(1, update.getParameterCount(), "PUT /mp/int/cryo/batch 只收请求体，不该有路径变量");
        // FIX V33：收原始 JSON（没带 = 不动、带了空值 = 清空），形状仍是 CryoBatchSubmitBo（接口文档里标着）
        assertEquals(com.fasterxml.jackson.databind.JsonNode.class, update.getParameterTypes()[0],
            "PUT 收原始 JSON 再按 CryoBatchSubmitBo 解析（PatchBody）");
        assertEquals(CryoBatchSubmitBo.class, update.getParameters()[0]
            .getAnnotation(io.swagger.v3.oas.annotations.parameters.RequestBody.class).content()[0].schema().implementation());
        assertEquals(0, update.getAnnotationsByType(PathVariable.class).length);
    }

    /**
     * ★★ 本类最容易做反的一条：{@code /mp/int/cryo/**} 上<b>根本没有</b>写流水的口。
     *
     * <p>{@code POST} 只允许是扁平的 {@code /mp/int/cryo/batch}；{@code PUT} 只允许是扁平的
     * {@code /mp/int/cryo/batch}；整个控制器<b>不许有</b> {@code @DeleteMapping}。
     * 任何一条 {@code flow} / {@code to-ln2} 的写映射都会让 accept 2 的四段
     * 「期望 404|405」变成 200 或 400。
     */
    @Test
    void thereIsNoWriteEndpointForFlowsOrToLn2OrDelete() {
        Set<String> writes = new LinkedHashSet<>();
        for (Method method : MpCryoController.class.getDeclaredMethods()) {
            PostMapping post = AnnotatedElementUtils.findMergedAnnotation(method, PostMapping.class);
            PutMapping put = AnnotatedElementUtils.findMergedAnnotation(method, PutMapping.class);
            DeleteMapping delete = AnnotatedElementUtils.findMergedAnnotation(method, DeleteMapping.class);
            if (post != null) {
                writes.add("POST " + Arrays.toString(post.path()));
            }
            if (put != null) {
                writes.add("PUT " + Arrays.toString(put.path()));
            }
            assertNull(delete, "★ /mp/int/cryo 不许有 DELETE（删批次 / 删登记都只在工作台）：" + method.getName());
        }
        assertEquals(Set.of("POST []", "PUT []"), writes,
            "★ 写口只有扁平的 POST / PUT /mp/int/cryo/batch 两条；"
                + "取走 / 补入 / 转液氮 / 改删登记只在 /lqg/cryo/**（CR-20260917-05）");
    }

    /**
     * 五条路由与业务语义一一对应：list / detail / create / update / flows。
     *
     * <p>特别钉住 {@code GET …/{id}/flows} —— 只读的取用登记（accept 2 第 1 段读的就是它）。
     */
    @Test
    void theOnlyFiveRoutesAreTheReadOnlyOnesAndTheFlowQuery() {
        Set<String> gets = new LinkedHashSet<>();
        for (Method method : MpCryoController.class.getDeclaredMethods()) {
            GetMapping get = AnnotatedElementUtils.findMergedAnnotation(method, GetMapping.class);
            if (get != null) {
                gets.add(Arrays.toString(get.path()));
            }
        }
        assertEquals(Set.of("[/list]", "[/{id}]", "[/{id}/flows]"), gets,
            "GET 只有三条：列表 / 详情 / 取用登记（只读）");
    }

    /**
     * ★ 单一路径守卫：mp 包里不许出现 {@code *Mapper} 字段。
     *
     * <p>「改冻存数量走同一套逐笔校验」的正确性依赖 {@code CryoBatchService.update}；
     * 本包自己注入 mapper 直写，就会绕过 {@code CryoBalanceChecker}
     * （accept 1 里「把 3004 的冻存数量改成 2 要拿到 400/500」那一段正是靠它）。
     */
    @Test
    void mpPackageNeverHoldsAMapper() {
        for (Class<?> type : new Class<?>[]{MpCryoService.class, MpCryoQueryBo.class, MpCryoController.class}) {
            for (Field field : type.getDeclaredFields()) {
                assertFalse(field.getType().getSimpleName().endsWith("Mapper"),
                    type.getSimpleName() + " 不该注入 " + field.getType().getSimpleName()
                        + "：读写必须走 cryo 包的 service（只有一条判据）");
            }
        }
    }

    /**
     * {@code sort=recent} 与 {@code mine} 是两个独立参数，默认都不带（中心全员）。
     */
    @Test
    void queryBoDefaultsToTheWholeCentreAndKeepsMineSeparateFromRecentSort() {
        MpCryoQueryBo bo = new MpCryoQueryBo();
        assertNull(bo.getMine(), "mine 不带 = 中心全部内部人员（默认不是本人）");
        assertNull(bo.getSort(), "sort 不带 = 内部管理冻存工作表那一档（超期置顶）");
        assertNull(bo.getOverdueOnly(), "overdueOnly 不带 = 「全部」页签");
        assertNull(bo.getLocation(), "location 不带 = 不按位置筛");
        bo.setSort("recent");
        assertNull(bo.getMine(), "★ 带了 sort=recent 不等于带了 mine —— 历史页签默认仍是全中心");
        assertTrue(CryoQueryBo.isRecentSort(bo.getSort()), "历史编辑记录那一档的排序键");
        bo.setMine(Boolean.TRUE);
        assertTrue(bo.getMine(), "「只看我提交的」打开才带 mine=true");
        assertFalse(CryoQueryBo.isRecentSort("create_time"), "别把任意列名当排序键收下（SQL 注入面）");
        assertFalse(CryoQueryBo.isRecentSort(""), "空串按不带处理");
    }

    /**
     * ★ 冻存数量可改，剩余不可填（ADR-0010：剩余永远是读时算的）。
     */
    @Test
    void submitBoCarriesInitQtyAndNeverRemainingQty() {
        assertTrue(Arrays.stream(CryoBatchSubmitBo.class.getDeclaredFields())
                .anyMatch(f -> "initQty".equals(f.getName())),
            "★ 入参里必须有 initQty：修改冻存记录时冻存数量（= 初始支数）全部可改（CR-20260917-04）");
        assertFalse(Arrays.stream(CryoBatchSubmitBo.class.getDeclaredFields())
                .anyMatch(f -> "remainingQty".equals(f.getName())),
            "★ 入参里不许有 remainingQty：剩余 = 初始 + 未删流水，读时算（ADR-0010）");
    }

}
