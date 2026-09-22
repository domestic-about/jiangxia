package org.dromara.lqg.sys.home;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.annotation.SaMode;
import org.dromara.lqg.doc.render.mapper.DocFileMapper;
import org.dromara.lqg.sys.home.controller.HomeController;
import org.dromara.lqg.sys.home.domain.vo.HomeRecentVo;
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
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 工作台首页计数的<b>形状与同源</b>契约测试（SYS-HOME-001）—— 不启 Spring，纯反射 + 源码扫描。
 *
 * <ol>
 *   <li><b>{@code /lqg/home/todo} 恰好五个键</b>：accept 1 第 1 段断的是对象逐字相等
 *       （{@code jq -e '.data == {…五键…}'}），多一个键就红 —— 靠人盯会回退；</li>
 *   <li><b>超期数只有一个真相源</b>：{@code HomeCounterService} 里<b>一个超期条件都没有</b>
 *       （没有 {@code in_minus80} / {@code to_ln2_time} / {@code freeze_time} / 也没有绕开唯一判定
 *       直连 {@code CryoOverdueMapper} 或 {@code CryoOverdueSqlProvider}）—— 它必须调
 *       {@code CryoOverdueService#countOverdue()}。这正是accept 的 counterfeit
 *       「工作台首页和冻存列表各写各的超期 where → 与超期清单长度不等红」；</li>
 *   <li><b>待核验样本不分组织 / 类器官</b>：条件里不许出现 {@code sample_kind}
 *       （只数组织正是 counterfeit 点名的形态）；</li>
 *   <li><b>渲染失败数是「组数」不是「行数」</b>：DOC 域的读口必须 {@code DISTINCT}
 *       样本 / 文档种类 / 受众三键，且手写 {@code del_flag='0'}（自定义 SQL 不吃
 *       {@code @TableLogic}）；</li>
 *   <li><b>两个端点是内部角色闸</b>：{@code @SaCheckRole(value = {"lqg_admin", "lqg_internal"},
 *       mode = SaMode.OR)}，<b>没有</b> {@link SaCheckPermission} ——「少一行菜单权限就 403」会让
 *       「没有待办」与「功能坏了」分不清。★ 必须是<b>角色闸</b>而不是 {@code @SaCheckLogin}：
 *       小程序 token 同样算「已登录」，只挂登录门时五个外部身份都读得到五个数与跨单位送检单号
 *       （D7 r1 L3 的 S1），而内外部隔离是 ADR-0004 由 {@code ExtChokepointContractTest}
 *       守着的不变量 —— 所以第 ⑥ 条从「登录即可调」改成「内部角色闸」。
 *       ★★ {@code mode} 必须是 {@code OR}：Sa-Token 默认 {@code AND}，漏了它 admin（只有 101）
 *       与 staff（只有 102）会<b>一起</b> 403 —— 实测踩过，见下。</li>
 * </ol>
 *
 * @author SYS-HOME-001
 */
class HomeCounterContractTest {

    /** doc/api-contract.md 第 88 行的五键（accept 1 第 1 段逐字断的就是它）。 */
    private static final Set<String> TODO_KEYS = Set.of(
        "pendingSamples", "pendingEmbeds", "cryoOverdue", "pendingExtUsers", "renderFailed");

    /** 「最近提交」一行的五格（ticket §2）。 */
    private static final Set<String> RECENT_KEYS = Set.of(
        "submitTime", "submitNo", "sourceUnitName", "submitSource", "verifyStatus");

    /** 超期判定属于 CRYO-REMIND-001：这些名字出现在本包源码里就说明有人重写了一遍。 */
    private static final Set<String> OVERDUE_INTERNALS = Set.of(
        "in_minus80", "to_ln2_time", "freeze_time", "t_lqg_cryo_batch",
        "CryoOverdueMapper", "CryoOverdueSqlProvider");

    // ── ①② 形状 ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("① /lqg/home/todo 的 data 恰好五个键（多一个键 accept 1 第 1 段就红）")
    void todoHasExactlyTheFiveContractKeys() {
        assertEquals(TODO_KEYS, declaredFieldNames(HomeTodoVo.class),
            "HomeTodoVo 的字段集必须与契约的五个键逐字相等");
    }

    @Test
    @DisplayName("② /lqg/home/recent 的每一行恰好五格：提交时间 / 送检单号 / 来源单位 / 内外部 / 核验状态")
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
    @DisplayName("④ 待核验样本数不按 sample_kind 收窄（外部也能交类器官收样，CR-20260917-05）")
    void pendingSamplesCountsBothKinds() {
        String code = codeOnly(source("sys/home/service/HomeCounterService.java"));
        assertTrue(code.contains(".eq(Sample::getVerifyStatus, VerifyTransitions.PENDING)"),
            "待核验样本 = verify_status='pending'，状态常量取自 VerifyTransitions（唯一真相源）");
        assertFalse(code.contains("getSampleKind") || code.contains("sample_kind"),
            "待核验样本条件里不许出现 sample_kind —— 只数组织正是 accept 的 counterfeit");
    }

    // ── ⑤ 渲染失败 = 组数 ────────────────────────────────────────────────────

    @Test
    @DisplayName("⑤ 渲染失败读口数的是 (样本, 文档种类, 受众) 组数，且自己写了 del_flag='0'")
    void renderFailedPortCountsGroupsNotRows() {
        String code = codeOnly(source("doc/render/mapper/DocFileMapper.java"));
        assertTrue(code.contains("DISTINCT sample_id, doc_kind, audience"),
            "一次失败会写 docx + pdf 两行，必须按三键去重后计数");
        assertTrue(code.contains("render_status = 'failed'"), "口径 = render_status='failed'");
        assertTrue(code.contains("del_flag = '0'"),
            "自定义 SQL 不吃 @TableLogic，软删要手写（否则软删掉的旧失败还会被算进来）");
        try {
            Method m = DocFileMapper.class.getMethod("countFailedGroups");
            assertEquals(long.class, m.getReturnType(), "计数回 long（不接 null 的 Long）");
        } catch (NoSuchMethodException e) {
            fail("DocFileMapper 必须暴露 countFailedGroups() 这个读口（SYS-HOME-001 的唯一失败数来源）");
        }
    }

    // ── ⑥ 内部角色闸（D7 r1 L3 的 S1 修复）────────────────────────────────────

    @Test
    @DisplayName("⑥ 两个端点挂内部角色闸 {lqg_admin|lqg_internal, OR}（不是 @SaCheckLogin），无权限串")
    void bothEndpointsRequireAnInternalRoleGate() {
        assertInternalRoleGate("todo", "/todo");
        assertInternalRoleGate("recent", "/recent");
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
        assertEquals(Set.of("lqg_admin", "lqg_internal"), Set.of(role.value()),
            methodName + " 的角色闸必须恰好放行 101 lqg_admin 与 102 lqg_internal"
                + "（只放 lqg_internal 会把工作台管理员首页打 403，那是本票的主场景）");
        assertEquals(SaMode.OR, role.mode(),
            methodName + " 的 @SaCheckRole 必须是 SaMode.OR —— Sa-Token 默认 AND 时"
                + "「同时具备 101 和 102」才放行，而 seed 里 lqgadmin 只有 101、内部人员只有 102，"
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
