package org.dromara.lqg.ext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

import cn.dev33.satoken.annotation.SaCheckRole;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.dromara.lqg.ext.controller.ExtDocController;
import org.dromara.lqg.ext.domain.bo.ExtDocQueryBo;
import org.dromara.lqg.ext.domain.vo.ExtDocAttachmentVo;
import org.dromara.lqg.ext.domain.vo.ExtDocDownloadVo;
import org.dromara.lqg.ext.domain.vo.ExtDocImageVo;
import org.dromara.lqg.ext.domain.vo.ExtDocPageItemVo;
import org.dromara.lqg.ext.domain.vo.ExtDocPagesVo;
import org.dromara.lqg.ext.domain.vo.ExtDocVo;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AUTH-EXT-003 的形状契约：把「外部能拿到哪些文档键」与「audience 不是入参」钉在<b>声明期</b>。
 *
 * <p>为什么还要这一层（accept 抓不到的两件事）：
 *
 * <ul>
 *   <li>accept 1 的 keys 断言是<b>单向</b>的（只抓多出来的键），抓不到「少了某个键」
 *       —— 本类用反射把四个对外 VO 的字段集合钉成<b>恰好等于</b>白名单；</li>
 *   <li>accept 2 的 {@code ! grep} 只打 {@code ExtDocVo} / {@code ExtDocPagesVo} 两个文件；
 *       本类把四个 VO 一起扫，且把「audience 不许出现在 ext 侧的任何一行代码里」
 *       （只许出现在注释里）做成一条可复跑的断言 —— 这是 ticket §0 口径 2
 *       「audience 写死 external、请求里带 internal 不能生效」的声明期守卫。</li>
 * </ul>
 *
 * <p>★ 不重测 I1~I4：那四条由需求层的 {@code ExtChokepointContractTest} 扫整个 ext 包守着
 * （本类不复述，也不改那份 fixture —— accept 2 会 {@code cmp} 它）。
 *
 * @author AUTH-EXT-003
 */
class ExtDocShapeContractTest {

    /**
     * {@code ExtDocVo} 的 6 键白名单 —— 与 {@code doc/api-contract.md} 的
     * {@code ExtDocVo{sampleId, submitNo, donorNameMasked, docKind, publishedTime, totalScore?}}
     * 逐字对应（{@code totalScore} 只出现在评分表那一行上）。
     */
    private static final Set<String> EXT_DOC_VO_KEYS = Set.of(
        "sampleId", "submitNo", "donorNameMasked", "docKind", "publishedTime", "totalScore");

    /** 独立验收 V24：外部预览补上原图与附件（UI:mp.doc.preview 要求），键仍是白名单。 */
    private static final Set<String> EXT_DOC_PAGES_VO_KEYS = Set.of("docKind", "status", "pages", "images", "attachments");

    private static final Set<String> EXT_DOC_PAGE_ITEM_VO_KEYS = Set.of("pageNo", "url");

    private static final Set<String> EXT_DOC_IMAGE_VO_KEYS = Set.of("url", "previewUrl");

    private static final Set<String> EXT_DOC_ATTACHMENT_VO_KEYS = Set.of("fileName", "fileSize", "url");

    private static final Set<String> EXT_DOC_DOWNLOAD_VO_KEYS = Set.of("url", "fileName");

    /**
     * 对外<b>永远</b>不许出现的键。{@code internalNo} 在这张票上是「一律不给」——
     * 它只按 {@code lqg.ext.show-internal-no} 出现在<b>样本详情</b>上（{@code ExtSampleDetailVo}），
     * 文档这条链路一个都不给（CR-20260918-07：开关不作用于预渲染文档）。
     * {@code contentHash / templateVersion / errorMsg} 是产物缓存的内部键与失败原因，
     * ticket §3 明确「不能看渲染失败的原因」。
     */
    private static final Set<String> NEVER_EXTERNAL_KEYS = Set.of(
        "internalNo", "remark", "verifyBy", "verifiedBy", "publishedBy", "createBy", "updateBy",
        "createDept", "operatorName", "embedBy", "frozenBy", "phone", "phonenumber", "openid",
        "contentHash", "templateVersion", "errorMsg", "ossId", "audience");

    /** ext 侧「不属于外部文档接口」的 controller —— 本类只管自己这三个端点。 */
    private static final String DOC_CONTROLLER_PATH = "/mp/ext/doc";

    private static Set<String> instanceFields(Class<?> type) {
        return Arrays.stream(type.getDeclaredFields())
            .filter(f -> !Modifier.isStatic(f.getModifiers()))
            .map(Field::getName)
            .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static List<Method> handlersOf(Class<?> controller) {
        return Arrays.stream(controller.getDeclaredMethods())
            .filter(m -> Modifier.isPublic(m.getModifiers()))
            .filter(m -> AnnotatedElementUtils.hasAnnotation(m, RequestMapping.class))
            .collect(Collectors.toList());
    }

    /**
     * 读模块内的源码文件（cwd 是模块目录；reactor 从仓库根跑时用带前缀的路径回落
     * —— 与 {@code SampleHintContractTest} 同一套写法）。
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

    /** 去掉注释行之后的代码文本（注释里当然可以提 audience —— 讲的就是它）。 */
    private static String codeOnly(String text) {
        return Arrays.stream(text.split("\n"))
            .filter(line -> {
                String t = line.strip();
                return !t.startsWith("*") && !t.startsWith("/*") && !t.startsWith("//");
            })
            .collect(Collectors.joining("\n"));
    }

    // ══════════════════════════════════════════════════════════════════════
    // 键集合
    // ══════════════════════════════════════════════════════════════════════

    @Test
    void docVoKeySetIsExactlyTheWhitelist() {
        assertEquals(EXT_DOC_VO_KEYS, instanceFields(ExtDocVo.class),
            "ExtDocVo 的字段集合必须恰好等于契约第 87 行的 6 键（多一个 = 漏内部字段，少一个 = 前端缺数据）");
    }

    @Test
    void docPagesShapesAreExactlyTheWhitelist() {
        assertEquals(EXT_DOC_PAGES_VO_KEYS, instanceFields(ExtDocPagesVo.class),
            "ExtDocPagesVo 只许有 docKind / status / pages / images / attachments（失败原因与产物指纹都不对外）");
        assertEquals(EXT_DOC_PAGE_ITEM_VO_KEYS, instanceFields(ExtDocPageItemVo.class),
            "ExtDocPageItemVo 只许有 pageNo / url");
        assertEquals(EXT_DOC_IMAGE_VO_KEYS, instanceFields(ExtDocImageVo.class),
            "ExtDocImageVo 只许有 url / previewUrl");
        assertEquals(EXT_DOC_ATTACHMENT_VO_KEYS, instanceFields(ExtDocAttachmentVo.class),
            "ExtDocAttachmentVo 只许有 fileName / fileSize / url（不带 ossId）");
        assertEquals(EXT_DOC_DOWNLOAD_VO_KEYS, instanceFields(ExtDocDownloadVo.class),
            "ExtDocDownloadVo 只许有 url / fileName");
    }

    @Test
    void noExtDocVoCarriesInternalOnlyKeys() {
        for (Class<?> type : List.of(ExtDocVo.class, ExtDocPagesVo.class,
            ExtDocPageItemVo.class, ExtDocDownloadVo.class, ExtDocImageVo.class, ExtDocAttachmentVo.class)) {
            for (String field : instanceFields(type)) {
                assertFalse(NEVER_EXTERNAL_KEYS.contains(field),
                    type.getSimpleName() + " 声明了 " + field + " —— 这个键不许出现在给外部的文档 VO 里");
            }
        }
    }

    @Test
    void docQueryBoOnlyNarrowsWithinTheVisibleSet() {
        assertEquals(Set.of("sampleId", "docKind", "publishedBegin", "publishedEnd"),
            instanceFields(ExtDocQueryBo.class),
            "ExtDocQueryBo 只许有这四个筛选 —— 任何一个能放大范围都是越权（sampleId 必须先与可见集合求交）");
        // PageQuery 的两参构造：没有无参构造的父类，缺了这行 Spring MVC 绑不出这个 BO
        assertNotNull(Arrays.stream(ExtDocQueryBo.class.getDeclaredConstructors())
            .filter(c -> c.getParameterCount() == 0)
            .findFirst().orElse(null), "ExtDocQueryBo 缺无参构造（PageQuery 5.5.3 没有无参构造）");
    }

    // ══════════════════════════════════════════════════════════════════════
    // 入口
    // ══════════════════════════════════════════════════════════════════════

    @Test
    void docControllerIsExternalOnlyAndUnderMpExt() {
        assertNotNull(AnnotatedElementUtils.findMergedAnnotation(ExtDocController.class, RestController.class),
            "ExtDocController 必须是 @RestController");
        SaCheckRole role = AnnotatedElementUtils.findMergedAnnotation(ExtDocController.class, SaCheckRole.class);
        assertNotNull(role, "ExtDocController 缺类级 @SaCheckRole（内部账号必须天然 403）");
        assertEquals(List.of("lqg_external"), List.of(role.value()),
            "ExtDocController 的类级角色必须是 lqg_external 且只有它");
        RequestMapping mapping = AnnotatedElementUtils.findMergedAnnotation(ExtDocController.class, RequestMapping.class);
        assertNotNull(mapping, "ExtDocController 缺类级 @RequestMapping");
        assertEquals(List.of(DOC_CONTROLLER_PATH), List.of(mapping.path()),
            "外部文档接口必须挂在 " + DOC_CONTROLLER_PATH + " 下（ADR-0004 的 I1）");

        Set<String> paths = handlersOf(ExtDocController.class).stream()
            .map(m -> {
                RequestMapping rm = AnnotatedElementUtils.findMergedAnnotation(m, RequestMapping.class);
                return rm == null || rm.path().length == 0 ? "" : rm.path()[0];
            })
            .collect(Collectors.toCollection(LinkedHashSet::new));
        assertEquals(Set.of("/list", "/{sampleId}/{docKind}/pages", "/{sampleId}/{docKind}/download"), paths,
            "三个端点的路径必须逐字是契约里那三个（多一个出口就是多一条要证明堵住的路）");
    }

    /**
     * ★ ticket §0 口径 2 的声明期守卫：<b>audience 只许出现在注释里</b>。
     *
     * <p>「写死 external」靠的是「外部这条路拿不到第二个取值」，而不是「读进来再覆盖」——
     * 所以这三份代码里不许出现任何一处把 {@code audience} 当值用的写法。
     * 运行期那半边由 accept 1 断（带 {@code audience=internal} 的下载链接里必须只有
     * {@code /external/}）。
     */
    @Test
    void audienceIsNeverABoundInputOnTheExternalDocPath() {
        for (String relative : List.of(
            "ext/controller/ExtDocController.java",
            "ext/service/ExtDocAssemblyService.java",
            "ext/domain/bo/ExtDocQueryBo.java")) {
            String code = codeOnly(source(relative));
            assertFalse(code.contains("audience"),
                relative + " 的代码里出现了 audience —— 外部文档链路上它必须是写死的常量，不是入参");
        }
    }

}
