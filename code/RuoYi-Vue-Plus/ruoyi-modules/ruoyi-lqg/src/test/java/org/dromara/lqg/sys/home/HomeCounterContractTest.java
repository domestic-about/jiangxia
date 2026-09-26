package org.dromara.lqg.sys.home;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.annotation.SaMode;
import org.dromara.lqg.auth.staff.guard.StaffGrantRules;
import org.dromara.lqg.doc.render.mapper.DocFileMapper;
import org.dromara.lqg.sys.home.controller.HomeController;
import org.dromara.lqg.sys.home.domain.vo.HomeRecentVo;
import org.dromara.lqg.sys.home.domain.vo.HomeRenderIssueVo;
import org.dromara.lqg.sys.home.domain.vo.HomeTodoVo;
import org.dromara.lqg.sys.home.service.HomeCounterService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.bind.annotation.GetMapping;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 工作台首页计数的<b>形状与同源</b>契约测试（SYS-HOME-001）—— 不启 Spring，纯反射 + 源码扫描。
 *
 * <ol>
 *   <li><b>{@code /lqg/home/todo} 恰好七个键</b>：原五键 + CR-20260924-10 拆页后加的
 *       {@code pendingTissue} / {@code pendingOrganoid}；字段集逐字相等，多一个键就红 —— 靠人盯会回退；</li>
 *   <li><b>超期数只有一个真相源</b>：{@code HomeCounterService} 里<b>一个超期条件都没有</b>
 *       （没有 {@code in_minus80} / {@code to_ln2_time} / {@code freeze_time} / 也没有绕开唯一判定
 *       直连 {@code CryoOverdueMapper} 或 {@code CryoOverdueSqlProvider}）—— 它必须调
 *       {@code CryoOverdueService#countOverdue()}。这正是accept 的 counterfeit
 *       「工作台首页和冻存列表各写各的超期 where → 与超期清单长度不等红」；</li>
 *   <li><b>待核验样本总数 = 组织 + 类器官</b>（只数组织正是 counterfeit 点名的形态）：CR-20260924-10 起
 *       按类别各数一次（两张卡、两个菜单角标），总数 {@code pendingSamples} 就是两者相加、不另查 ——
 *       同一次响应里三个数必然对得上；</li>
 *   <li><b>渲染失败数是「组数」不是「行数」</b>：DOC 域的读口必须 {@code DISTINCT}
 *       样本 / 文档种类 / 受众三键，且手写 {@code del_flag='0'}（自定义 SQL 不吃
 *       {@code @TableLogic}）；独立验收 V23 起口径含「内部版照出但缺图」（#217）；</li>
 *   <li><b>两个端点是内部角色闸，且角色集与 {@code StaffGrantRules.INTERNAL_ROLE_KEYS} 一致</b>：
 *       {@code @SaCheckRole(value = {"lqg_admin", "lqg_internal", "superadmin"},
 *       mode = SaMode.OR)}，<b>没有</b> {@link SaCheckPermission} ——「少一行菜单权限就 403」会让
 *       「没有待办」与「功能坏了」分不清。★ 必须是<b>角色闸</b>而不是 {@code @SaCheckLogin}：
 *       小程序 token 同样算「已登录」，只挂登录门时五个外部身份都读得到五个数与跨单位送检单号
 *       （D7 r1 L3 的 S1），而内外部隔离是 ADR-0004 由 {@code ExtChokepointContractTest}
 *       守着的不变量 —— 所以第 ⑥ 条从「登录即可调」改成「内部角色闸」。
 *       ★★ {@code mode} 必须是 {@code OR}：Sa-Token 默认 {@code AND}，漏了它 admin（只有 101）
 *       与 staff（只有 102）会<b>一起</b> 403 —— 实测踩过，见下。
 *       ★★ 角色集<b>不逐字钉字面量</b>，而是断言「恰等于 {@link StaffGrantRules#INTERNAL_ROLE_KEYS}」：
 *       注解不能引用 {@code List.of(...)} 常量（不是编译期常量），一致性只能靠这条测试守
 *       —— 谁改了那条常量（例如再加一个内部角色），这里立刻红。</li>
 * </ol>
 *
 * @author SYS-HOME-001
 */
class HomeCounterContractTest {

    /**
     * 待办七键：doc/api-contract.md 第 88 行的五键 + CR-20260924-10 的 pendingTissue / pendingOrganoid
     * （工作台样本总表拆成两页、首页卡片拆成两张；pendingSamples 保留给小程序首页）。
     */
    private static final Set<String> TODO_KEYS = Set.of(
        "pendingSamples", "pendingTissue", "pendingOrganoid", "pendingEmbeds", "cryoOverdue", "pendingExtUsers",
        "renderFailed");

    /** 「最近提交」一行的六格（ticket §2 的五格 + CR-20260924-10 的 sampleKind：点一行按类别进对应页）。 */
    private static final Set<String> RECENT_KEYS = Set.of(
        "submitTime", "submitNo", "sampleKind", "sourceUnitName", "submitSource", "verifyStatus");

    /** 超期判定属于 CRYO-REMIND-001：这些名字出现在本包源码里就说明有人重写了一遍。 */
    private static final Set<String> OVERDUE_INTERNALS = Set.of(
        "in_minus80", "to_ln2_time", "freeze_time", "t_lqg_cryo_batch",
        "CryoOverdueMapper", "CryoOverdueSqlProvider");

    // ── ①② 形状 ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("① /lqg/home/todo 的 data 恰好七个键（再多一个键就红）")
    void todoHasExactlyTheContractKeys() {
        assertEquals(TODO_KEYS, declaredFieldNames(HomeTodoVo.class),
            "HomeTodoVo 的字段集必须与契约的七个键逐字相等");
    }

    @Test
    @DisplayName("② /lqg/home/recent 的每一行恰好六格：提交时间 / 送检单号 / 类别 / 来源单位 / 内外部 / 核验状态")
    void recentHasExactlyTheFiveDocumentedFields() {
        assertEquals(RECENT_KEYS, declaredFieldNames(HomeRecentVo.class));
    }

    // ── ③ 同源：超期不重写 ────────────────────────────────────────────────────

    @Test
    @DisplayName("③ 超期数转发 CryoOverdueService#countOverdue()，本包一个超期条件都没写")
    void cryoOverdueComesFromTheSingleJudgement() {
        String code = codeOnly(source("sys/home/service/HomeCounterService.java"));
        assertTrue(code.contains("cryoOverdueService.countOverdue()"),
            "超期数必须调 CRYO-REMIND-001 的唯一判定，不许在本包重算");
        for (String forbidden : OVERDUE_INTERNALS) {
            assertFalse(code.contains(forbidden),
                "HomeCounterService 里出现了超期内部细节「" + forbidden + "」——超期只有一个真相源");
        }
    }

    // ── ④ 待核验样本 = 组织 + 类器官 ─────────────────────────────────────────

    @Test
    @DisplayName("④ 待核验样本按类别各数一次；总数 = 组织 + 类器官（外部也能交类器官收样，CR-20260917-05），不另查")
    void pendingSamplesCountsBothKinds() {
        String code = codeOnly(source("sys/home/service/HomeCounterService.java"));
        assertTrue(code.contains(".eq(Sample::getVerifyStatus, VerifyTransitions.PENDING)"),
            "待核验样本 = verify_status='pending'，状态常量取自 VerifyTransitions（唯一真相源）");
        assertTrue(code.contains("pendingSamplesOf(SampleKindRules.KIND_TISSUE)")
                && code.contains("pendingSamplesOf(SampleKindRules.KIND_ORGANOID)"),
            "两类的取值来自 SampleKindRules（不手写 'tissue' / 'organoid' 字面量）");
        assertTrue(code.contains("vo.setPendingSamples(tissue + organoid)"),
            "★ 总数就是同一次取到的两个分类数相加 —— 另查一遍会出现「总数 ≠ 两张卡之和」");
        assertFalse(code.contains("\"sample_kind\""), "不手写列名，走 Sample::getSampleKind");

        // 行为：假 mapper 按 kind 回不同的数，总数必须是两者之和（只数组织 = 3，红）
        Map<String, Long> byKind = Map.of("tissue", 3L, "organoid", 2L);
        HomeTodoVo vo = counterWith(byKind).todo();
        assertEquals(3L, vo.getPendingTissue());
        assertEquals(2L, vo.getPendingOrganoid());
        assertEquals(5L, vo.getPendingSamples(), "pendingSamples 必须仍等于两者之和（小程序首页用它）");
        assertEquals(5L, counterWith(byKind).pendingSamples(), "单取总数的读口与 todo() 同一个口径");
    }

    /**
     * 只有样本计数是真的 HomeCounterService：假 SampleMapper 从 wrapper 的参数里读出 sample_kind，
     * 按 {@code byKind} 回数；其余四个数的来源一律回 0。
     */
    private static HomeCounterService counterWith(Map<String, Long> byKind) {
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
            new org.apache.ibatis.builder.MapperBuilderAssistant(
                new com.baomidou.mybatisplus.core.MybatisConfiguration(), ""),
            org.dromara.lqg.sample.domain.Sample.class);
        org.dromara.lqg.sample.mapper.SampleMapper sampleMapper = proxy(org.dromara.lqg.sample.mapper.SampleMapper.class,
            (method, args) -> {
                if (!"selectCount".equals(method.getName())) {
                    throw new UnsupportedOperationException(method.getName());
                }
                var wrapper = (com.baomidou.mybatisplus.core.conditions.AbstractWrapper<?, ?, ?>) args[0];
                wrapper.getSqlSegment(); // 生成一次，参数表才齐
                Map<String, Object> params = wrapper.getParamNameValuePairs();
                assertTrue(params.containsValue("pending"), "每一次样本计数都只数待核验");
                return byKind.entrySet().stream()
                    .filter(e -> params.containsValue(e.getKey()))
                    .map(Map.Entry::getValue)
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("样本计数没带 sample_kind 条件：" + params));
            });
        org.dromara.lqg.embed.mapper.EmbedMapper embedMapper =
            proxy(org.dromara.lqg.embed.mapper.EmbedMapper.class, (method, args) -> 0L);
        org.dromara.lqg.auth.mapper.ExtProfileMapper extProfileMapper =
            proxy(org.dromara.lqg.auth.mapper.ExtProfileMapper.class, (method, args) -> 0L);
        DocFileMapper docFileMapper = proxy(DocFileMapper.class, (method, args) -> 0L);
        org.dromara.lqg.cryo.remind.service.CryoOverdueService cryo =
            new org.dromara.lqg.cryo.remind.service.CryoOverdueService(null, null, null, null) {
                @Override
                public long countOverdue() {
                    return 0L;
                }
            };
        return new HomeCounterService(sampleMapper, embedMapper, extProfileMapper, docFileMapper, cryo);
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, java.util.function.BiFunction<Method, Object[], Object> body) {
        return (T) java.lang.reflect.Proxy.newProxyInstance(HomeCounterContractTest.class.getClassLoader(),
            new Class<?>[]{type}, (p, method, args) -> switch (method.getName()) {
                case "toString" -> type.getSimpleName();
                case "hashCode" -> 1;
                case "equals" -> p == args[0];
                default -> body.apply(method, args);
            });
    }

    // ── ⑤ 渲染失败 = 组数 ────────────────────────────────────────────────────

    @Test
    @DisplayName("⑤ 渲染异常读口数的是 (样本, 文档种类, 受众) 组数：failed + 内部版缺图（#217），且自己写了 del_flag='0'")
    void renderFailedPortCountsGroupsNotRows() {
        String code = codeOnly(source("doc/render/mapper/DocFileMapper.java"));
        assertTrue(code.contains("DISTINCT sample_id, doc_kind, audience"),
            "一次失败会写 docx + pdf 两行，必须按三键去重后计数");
        assertTrue(code.contains("render_status = 'failed'"), "口径含 render_status='failed'");
        // ★ #217（2026-09-23 定）：内部版照出但缺图，也计入首页的渲染异常数
        assertTrue(code.contains("audience = 'internal' AND render_status = 'done' AND missing_image_count > 0"),
            "口径还要含「内部版照出但缺图」—— 否则缺图的文档在首页上一个数都不显示");
        assertTrue(code.contains("del_flag = '0'"),
            "自定义 SQL 不吃 @TableLogic，软删要手写（否则软删掉的旧失败还会被算进来）");
        // 首页计数与「渲染失败与缺图」清单必须同一段 WHERE（两处各写一份迟早漂）
        assertTrue(code.contains("RENDER_ISSUE_WHERE"), "计数与清单共用 DocFileMapper.RENDER_ISSUE_WHERE");
        try {
            Method m = DocFileMapper.class.getMethod("countRenderIssueGroups");
            assertEquals(long.class, m.getReturnType(), "计数回 long（不接 null 的 Long）");
        } catch (NoSuchMethodException e) {
            fail("DocFileMapper 必须暴露 countRenderIssueGroups() 这个读口（SYS-HOME-001 的唯一异常数来源）");
        }
        String service = codeOnly(source("sys/home/service/HomeCounterService.java"));
        assertTrue(service.contains("docFileMapper.countRenderIssueGroups()"),
            "renderFailed 必须转发 DOC 域的读口，不在本包重写判定");
    }

    // ── ⑥ 内部角色闸（D7 r1 L3 的 S1 修复）────────────────────────────────────

    @Test
    @DisplayName("⑥ 两个端点挂内部角色闸 = StaffGrantRules.INTERNAL_ROLE_KEYS（OR，不是 @SaCheckLogin），无权限串")
    void bothEndpointsRequireAnInternalRoleGate() {
        assertInternalRoleGate("todo", "/todo");
        assertInternalRoleGate("recent", "/recent");
        // 独立验收 V29：渲染失败与缺图清单同一道内部角色闸（里面有跨单位送检单号与失败原因）
        assertInternalRoleGate("renderIssues", "/render-issues");
    }

    @Test
    @DisplayName("⑦ 渲染失败与缺图清单与卡片上的数同一段 WHERE（卡片数 == 清单行数），清单行 = 一组一行")
    void renderIssueListSharesTheCountPredicate() {
        String mapper = codeOnly(source("doc/render/mapper/DocFileMapper.java"));
        assertTrue(mapper.contains("\" FROM t_lqg_doc_file WHERE \" + RENDER_ISSUE_WHERE"),
            "清单必须拼的是 RENDER_ISSUE_WHERE（与 countRenderIssueGroups 同一段）");
        assertTrue(mapper.contains("file_format = 'docx' AND page_no = 0"),
            "判定只看 header 行（一组一行），计数与清单才能逐一对上");
        String service = codeOnly(source("sys/home/service/HomeCounterService.java"));
        assertTrue(service.contains("docFileMapper.selectRenderIssues("), "清单转发 DOC 域读口");
        assertEquals(Set.of("sampleId", "internalNo", "submitNo", "sourceUnitName", "docKind", "audience", "issue",
                "errorMsg", "missingImageCount", "missingImages", "time"),
            declaredFieldNames(HomeRenderIssueVo.class));
    }

    private static void assertInternalRoleGate(String methodName, String expectedPath) {
        Method method = null;
        try {
            method = HomeController.class.getMethod(methodName);
        } catch (NoSuchMethodException e) {
            fail("HomeController 缺少端点 " + methodName + "()");
        }
        SaCheckRole role = AnnotatedElementUtils.findMergedAnnotation(method, SaCheckRole.class);
        assertTrue(role != null,
            methodName + " 必须挂 @SaCheckRole —— 只挂 @SaCheckLogin 时小程序 token 也能调，外部就读得到");
        // ★ 不逐字钉字面量：注解引用不了 List.of(...) 常量，一致性只能由这条断言守。
        //   改 StaffGrantRules.INTERNAL_ROLE_KEYS（例如新增一个内部角色）时必须同步改注解，否则这里红。
        assertEquals(Set.copyOf(StaffGrantRules.INTERNAL_ROLE_KEYS), Set.of(role.value()),
            methodName + " 的角色闸必须恰等于 StaffGrantRules.INTERNAL_ROLE_KEYS = "
                + StaffGrantRules.INTERNAL_ROLE_KEYS + "（工作台准入的唯一口径来源，"
                + "见 WorkbenchLoginGuardAspect）—— 少了 superadmin 会出现「超管能登工作台、"
                + "却打不开首页」；少了 lqg_admin / lqg_internal 会打红本票主场景");
        assertEquals(SaMode.OR, role.mode(),
            methodName + " 的 @SaCheckRole 必须是 SaMode.OR —— Sa-Token 默认 AND 时"
                + "「同时具备全部角色」才放行，而 seed 里 lqgadmin 只有 101、内部人员只有 102，"
                + "结果 admin 与 staff 会一起 403（D7 r1 返工实测踩过）");
        for (String allowed : role.value()) {
            assertFalse(allowed.contains("external"),
                methodName + " 不许放行外部角色「" + allowed + "」—— 工作台拒外部（AUTH-STAFF-001 §2.2）");
        }
        assertFalse(AnnotatedElementUtils.hasAnnotation(method, SaCheckPermission.class),
            methodName + " 不许挂权限串 —— 首页是登录后的第一屏，不该因为少一行 sys_menu 就 403");
        GetMapping mapping = AnnotatedElementUtils.findMergedAnnotation(method, GetMapping.class);
        assertTrue(mapping != null && Arrays.asList(mapping.value()).contains(expectedPath),
            methodName + " 的 GET 路径必须是 " + expectedPath);
    }

    // ── 工具 ─────────────────────────────────────────────────────────────────

    private static Set<String> declaredFieldNames(Class<?> type) {
        return Arrays.stream(type.getDeclaredFields())
            .filter(f -> !Modifier.isStatic(f.getModifiers()))
            .map(Field::getName)
            .collect(Collectors.toSet());
    }

    /**
     * 读模块内的源码（cwd 是模块目录；reactor 从仓库根跑时用带前缀的路径回落）。
     */
    private static String source(String relative) {
        Path path = Path.of("src/main/java/org/dromara/lqg", relative);
        if (!Files.exists(path)) {
            path = Path.of("ruoyi-modules/ruoyi-lqg/src/main/java/org/dromara/lqg", relative);
        }
        if (!Files.exists(path)) {
            return fail("找不到 " + relative + "（cwd=" + Path.of(".").toAbsolutePath() + "）");
        }
        try {
            return Files.readString(path);
        } catch (IOException e) {
            return fail("读不了 " + path + "：" + e.getMessage());
        }
    }

    /** 去掉注释行之后的代码文本（注释里当然可以讲超期口径 —— 讲的就是它）。 */
    private static String codeOnly(String text) {
        return Arrays.stream(text.split("\n"))
            .filter(line -> {
                String t = line.strip();
                return !t.startsWith("*") && !t.startsWith("/*") && !t.startsWith("//");
            })
            .collect(Collectors.joining("\n"));
    }

}
