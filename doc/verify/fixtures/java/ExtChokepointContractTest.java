package org.dromara.lqg.ext;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.core.type.filter.RegexPatternTypeFilter;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 契约测试（由需求层提供；AUTH-EXT-001 / AUTH-EXT-002 / AUTH-EXT-003 的 accept 用 cmp 校验本文件**逐字节未改**后再跑）。
 * 守的是 ADR-0004「外部隔离做成一个咽喉」的四条结构性不变量——它们让「外部看不到不该看的」从
 * 「要证明所有入口都堵住了」（开放集合，永远枚举不完）变成「只需要验这一处」：
 *
 *   I1 入口只有一组：org.dromara.lqg.ext 包里每个 @RestController 的路径都以 /mp/ext 开头；
 *      该包之外的任何 controller 都不得映射 /mp/ext 开头的路径。
 *   I2 字段只有一种出口：ext 包 controller 的每个处理方法，返回类型（拆掉 R / TableDataInfo / 集合等容器后）
 *      只能是 void、基本包装类型、String，或 ext 包里名字以 Ext 开头的类。
 *   I3 专用 VO 是白名单：ext 包里每个 Ext*Vo 都不得声明这些字段——操作人、包埋人、冻存人、核验人、创建 / 更新人、手机号；
 *      唯一例外是 ExtEmbedVo 上的操作人与包埋人（CR-20260918-07 甲方要求对外可见），例外按「VO 名 + 字段名」精确豁免，别扩大。
 *   I4 范围只有一处算：ext 包里除 ExtScopeServiceImpl 外，任何类都不得持有 *Mapper 类型的字段
 *      （外部查询必须先经 ExtScopeService 拿到可见样本集合，再调业务 service，而不是自己写 SQL）。
 */
class ExtChokepointContractTest {

    private static final String EXT_PKG = "org.dromara.lqg.ext";
    private static final String ROOT_PKG = "org.dromara.lqg";
    private static final Set<String> BANNED_VO_FIELDS = Set.of(
        "operatorName", "embedBy", "frozenBy", "verifyBy", "verifiedBy", "publishedBy",
        "createBy", "updateBy", "createDept", "phone", "phonenumber", "openid");
    // CR-20260918-07：甲方要外部看得到石蜡包埋的操作人与包埋人，**只在石蜡包埋记录这一个 VO 上**放开。
    // 其余 Ext*Vo（样本、文档…）照旧禁——否则哪天有人让 ExtSampleDetailVo 继承 SampleVo，操作人就顺着继承漏出去了。
    // internalNo 不进这张表：它由系统参数 lqg.ext.show-internal-no 在装配时决定给不给，见 FLOW:F-EXT-01.step3。
    private static final Map<String, Set<String>> BANNED_EXEMPT = Map.of(
        "ExtEmbedVo", Set.of("operatorName", "embedBy"));
    private static final Set<String> ALLOWED_LEAF = Set.of(
        "void", "java.lang.Void", "java.lang.String", "java.lang.Boolean", "java.lang.Long", "java.lang.Integer");

    private static List<Class<?>> scan(String basePackage, boolean controllersOnly) throws ClassNotFoundException {
        ClassPathScanningCandidateComponentProvider p = new ClassPathScanningCandidateComponentProvider(false);
        if (controllersOnly) {
            p.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
        } else {
            p.addIncludeFilter(new RegexPatternTypeFilter(java.util.regex.Pattern.compile(".*")));
        }
        List<Class<?>> out = new ArrayList<>();
        for (BeanDefinition bd : p.findCandidateComponents(basePackage)) {
            out.add(Class.forName(bd.getBeanClassName()));
        }
        return out;
    }

    private static List<String> classPaths(Class<?> c) {
        RequestMapping rm = AnnotatedElementUtils.findMergedAnnotation(c, RequestMapping.class);
        return rm == null ? List.of() : Arrays.asList(rm.path());
    }

    private static boolean isHandler(Method m) {
        return Modifier.isPublic(m.getModifiers())
            && AnnotatedElementUtils.hasAnnotation(m, RequestMapping.class);
    }

    private static void collectLeaves(Type t, List<String> leaves) {
        if (t instanceof ParameterizedType pt) {
            for (Type arg : pt.getActualTypeArguments()) {
                collectLeaves(arg, leaves);
            }
        } else if (t instanceof WildcardType wt) {
            for (Type ub : wt.getUpperBounds()) {
                collectLeaves(ub, leaves);
            }
        } else if (t instanceof Class<?> c) {
            if (c.isArray()) {
                collectLeaves(c.getComponentType(), leaves);
            } else {
                leaves.add(c.getName());
            }
        } else {
            leaves.add(t.getTypeName());
        }
    }

    @Test
    void i1_extPackageControllersAllLiveUnderMpExt_andNobodyElseDoes() throws Exception {
        List<Class<?>> ext = scan(EXT_PKG, true);
        assertFalse(ext.isEmpty(), "ext 包里一个 controller 都没扫到——包名不对，或这条测试在空集合上空转");
        for (Class<?> c : ext) {
            List<String> paths = classPaths(c);
            assertFalse(paths.isEmpty(), c.getName() + " 缺类级 @RequestMapping");
            for (String path : paths) {
                assertTrue(path.startsWith("/mp/ext"), c.getName() + " 的路径 " + path + " 不在 /mp/ext 下");
            }
        }
        for (Class<?> c : scan(ROOT_PKG, true)) {
            if (c.getName().startsWith(EXT_PKG + ".")) {
                continue;
            }
            for (String path : classPaths(c)) {
                assertFalse(path.startsWith("/mp/ext"), c.getName() + " 不在 ext 包里却映射了 " + path);
            }
        }
    }

    @Test
    void i2_extControllersOnlyReturnExtVos() throws Exception {
        int handlers = 0;
        for (Class<?> c : scan(EXT_PKG, true)) {
            for (Method m : c.getDeclaredMethods()) {
                if (!isHandler(m)) {
                    continue;
                }
                handlers++;
                List<String> leaves = new ArrayList<>();
                collectLeaves(m.getGenericReturnType(), leaves);
                for (String leaf : leaves) {
                    String simple = leaf.substring(leaf.lastIndexOf('.') + 1);
                    boolean ok = ALLOWED_LEAF.contains(leaf)
                        || (leaf.startsWith(EXT_PKG + ".") && simple.startsWith("Ext"));
                    assertTrue(ok, c.getSimpleName() + "#" + m.getName() + " 返回了 " + leaf + "——外部接口只能返回 ext 包里的 Ext* 类型");
                }
            }
        }
        assertTrue(handlers > 0, "ext 包里一个处理方法都没扫到");
    }

    @Test
    void i3_extVosNeverCarryInternalOnlyFields() throws Exception {
        int vos = 0;
        for (Class<?> c : scan(EXT_PKG, false)) {
            String simple = c.getSimpleName();
            if (!simple.startsWith("Ext") || !simple.endsWith("Vo")) {
                continue;
            }
            vos++;
            for (Class<?> k = c; k != null && k != Object.class; k = k.getSuperclass()) {
                Set<String> exempt = BANNED_EXEMPT.getOrDefault(simple, Set.of());
                for (Field f : k.getDeclaredFields()) {
                    if (exempt.contains(f.getName())) {
                        continue;   // CR-20260918-07：这一个 VO 上这一个字段是甲方要的
                    }
                    assertFalse(BANNED_VO_FIELDS.contains(f.getName()),
                        simple + " 声明了 " + f.getName() + "——这个字段不许出现在给外部的 VO 里（含继承来的）");
                }
            }
        }
        assertTrue(vos > 0, "ext 包里一个 Ext*Vo 都没扫到");
    }

    @Test
    void i4_onlyExtScopeServiceImplTouchesMappers() throws Exception {
        for (Class<?> c : scan(EXT_PKG, false)) {
            if (c.getSimpleName().equals("ExtScopeServiceImpl")) {
                continue;
            }
            for (Field f : c.getDeclaredFields()) {
                assertFalse(f.getType().getSimpleName().endsWith("Mapper"),
                    c.getSimpleName() + " 直接持有 " + f.getType().getSimpleName() + "——外部查询必须经 ExtScopeService，不许自己查库");
            }
        }
    }
}
