package org.dromara.lqg.sample.mp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.dev33.satoken.annotation.SaCheckRole;
import java.lang.reflect.Method;
import java.util.Arrays;
import org.dromara.lqg.sample.domain.Sample;
import org.dromara.lqg.sample.domain.bo.SampleQueryBo;
import org.dromara.lqg.sample.domain.bo.SampleSubmitBo;
import org.dromara.lqg.sample.service.SampleQueryService;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 小程序内部样本接口（{@code /mp/int/sample/**}）的契约测试（SAMPLE-MP-001）。
 *
 * <p>本类不启 Spring 容器，钉的是<b>结构</b>与<b>纯判据</b>两件事 ——
 * 它们恰好是票面最容易做反、而端到端 accept 又最费时去撞的部分：
 *
 * <ol>
 *   <li><b>角色闸的位置</b>：类级 {@code @SaCheckRole("lqg_internal")}。外部角色
 *       （{@code lqg_external}）打这一组必须 403；把注解挪到某个方法上、或整个漏掉，
 *       外部就拿到了全部样本连同内部编号。AUTH-EXT-001 的 accept 有一段
 *       「{@code --as extA GET /mp/int/sample/list} 期望 403」就是等本票注册端点才收口。</li>
 *   <li><b>{@code PUT} 的入口形状</b>：契约写的是 {@code PUT /mp/int/sample}（没有路径变量），
 *       方法上不能有 {@code @PathVariable} 参数 —— 形状错了前端永远 404 / 405。</li>
 *   <li><b>「有效才可改」的判据</b>：{@code isEditable} 只看 {@code verify_status='valid'}，
 *       <b>不看是谁录的</b>（CR-20260918-07 起内部人员对中心任何一条有效记录都能改），
 *       待核验 / 无效一律只读（核验是带必填项的状态转移，不能被一次普通保存绕过去）。</li>
 *   <li><b>{@code sort=recent} 的字面量</b>：只认 {@code recent}（大小写不敏感、忽略首尾空白），
 *       别的值 / 空值都走「内部管理表格页的全表」那一支，不会被误当排序键拼进 SQL。</li>
 *   <li><b>{@code mine} 默认不是本人</b>：契约写的是「开关打开时才带」，默认 false
 *       = 中心全部内部人员（甲方 9-18「江夏实验室所有的工作人员」）。</li>
 * </ol>
 *
 * @author SAMPLE-MP-001
 */
class MpSampleContractTest {

    @Test
    void controllerIsRestMappedUnderMpIntSampleWithClassLevelInternalRole() {
        Class<MpSampleController> type = MpSampleController.class;
        assertNotNull(AnnotatedElementUtils.findMergedAnnotation(type, RestController.class),
            "MpSampleController 必须是一个 @RestController");
        RequestMapping mapping = AnnotatedElementUtils.findMergedAnnotation(type, RequestMapping.class);
        assertNotNull(mapping, "MpSampleController 缺类级 @RequestMapping");
        assertEquals(java.util.List.of("/mp/int/sample"), Arrays.asList(mapping.path()),
            "类级路径必须是 /mp/int/sample（契约第 49 行）");

        SaCheckRole role = AnnotatedElementUtils.findMergedAnnotation(type, SaCheckRole.class);
        assertNotNull(role, "★ 类级 @SaCheckRole 不许漏：漏了外部角色就能读到全部样本连同内部编号");
        assertEquals(java.util.List.of("lqg_internal"), Arrays.asList(role.value()),
            "类级角色必须是 lqg_internal（契约第 19 行）");
    }

    @Test
    void updateIsAFlatPutWithoutPathVariable() {
        // 契约：PUT /mp/int/sample（body 里带 id），不是 PUT /mp/int/sample/{id}
        Method update = Arrays.stream(MpSampleController.class.getDeclaredMethods())
            .filter(m -> AnnotatedElementUtils.hasAnnotation(m, PutMapping.class))
            .findFirst()
            .orElseThrow(() -> new AssertionError("MpSampleController 没有 PUT 处理方法"));
        assertEquals(1, update.getParameterCount(), "PUT /mp/int/sample 只收请求体，不该有路径变量");
        assertEquals(SampleSubmitBo.class, update.getParameterTypes()[0], "PUT 的入参类型应是 SampleSubmitBo");
        assertEquals(0, update.getAnnotationsByType(org.springframework.web.bind.annotation.PathVariable.class).length);
    }

    @Test
    void createIsAPostOnTheSameFlatPath() {
        Method create = Arrays.stream(MpSampleController.class.getDeclaredMethods())
            .filter(m -> AnnotatedElementUtils.hasAnnotation(m, PostMapping.class))
            .findFirst()
            .orElseThrow(() -> new AssertionError("MpSampleController 没有 POST 处理方法"));
        assertEquals(1, create.getParameterCount(), "POST /mp/int/sample 只收请求体");
        assertEquals(SampleSubmitBo.class, create.getParameterTypes()[0]);
    }

    @Test
    void editableOnlyWhenVerifyStatusIsValid_noMatterWhoRecordedIt() {
        // 内部录入的有效样本：可改（哪怕不是当前用户录的 —— 9-18 起的口径）
        assertTrue(SampleQueryService.isEditable(sample("valid")), "有效样本必须可改");
        // 外部送来待核验 / 无效：只有读（核验与改判在工作台）
        assertFalse(SampleQueryService.isEditable(sample("pending")), "待核验的样本在小程序里必须只读");
        assertFalse(SampleQueryService.isEditable(sample("invalid")), "无效的样本在小程序里必须只读");
        // 状态缺失（脏数据）也按只读 —— 不猜
        assertFalse(SampleQueryService.isEditable(sample(null)), "状态缺失按只读，不按可改");
        assertFalse(SampleQueryService.isEditable(null), "没有行 = 不可改");
    }

    @Test
    void mineIsCreateByOrUpdateBy_ofTheCurrentUser() {
        Sample created = sample("valid");
        created.setCreateBy(9000000101L);
        created.setUpdateBy(null);
        assertTrue(SampleQueryService.isHandledBy(created, 9000000101L), "创建人算经手");
        assertFalse(SampleQueryService.isHandledBy(created, 9000000100L), "别人不算经手");
        assertFalse(SampleQueryService.isHandledBy(created, null), "取不到登录人不猜成本人");

        Sample modifiedByOther = sample("valid");
        modifiedByOther.setCreateBy(9000000111L);
        modifiedByOther.setUpdateBy(9000000101L);
        assertTrue(SampleQueryService.isHandledBy(modifiedByOther, 9000000101L), "最后修改人算经手");
        assertTrue(SampleQueryService.isHandledBy(modifiedByOther, 9000000111L),
            "创建人（原提交人）也仍算经手 —— mine 的判据是 create_by OR update_by，两个都算");
    }

    @Test
    void recentSortOnlyRecognisesTheRecentLiteral() {
        assertTrue(SampleQueryBo.isRecentSort("recent"));
        assertTrue(SampleQueryBo.isRecentSort("  RECENT "), "大小写与首尾空白不敏感");
        assertFalse(SampleQueryBo.isRecentSort(null), "不带 sort = 内部管理表格页的全表");
        assertFalse(SampleQueryBo.isRecentSort(""), "空串按不带处理");
        assertFalse(SampleQueryBo.isRecentSort("create_time"), "别把任意列名当排序键收下（SQL 注入面）");
    }

    @Test
    void mineDefaultsToNullMeaningWholeCentre() {
        MpSampleQueryBo bo = new MpSampleQueryBo();
        assertEquals(null, bo.getMine(), "mine 不带 = 中心全部内部人员（默认不是本人）");
        bo.setMine(Boolean.TRUE);
        assertTrue(bo.getMine(), "开关打开才带 mine=true");
    }

    @Test
    void putPatchKeepsFieldsTheBodyDidNotCarry() {
        // accept 的请求体就是 {"id":9000001001,"tissueType":"肝组织（更正）"} 这种部分字段
        Sample exists = sample("valid");
        exists.setSampleKind("tissue");
        exists.setSourceUnitName("A 医院");
        exists.setDonorName("测试供体甲");
        exists.setHospitalNo("ZY0000001");
        exists.setTissueType("肝组织");

        SampleSubmitBo patch = new SampleSubmitBo();
        patch.setId(9000001001L);
        patch.setTissueType("肝组织（更正）");

        SampleSubmitBo merged = MpSampleService.mergePatch(exists, patch);
        assertEquals(9000001001L, merged.getId());
        assertEquals("肝组织（更正）", merged.getTissueType(), "传了的字段要被覆盖");
        assertEquals("tissue", merged.getSampleKind(), "没传的字段沿用库里现值");
        assertEquals("测试供体甲", merged.getDonorName(), "★ 没传的加密列不能被清成空");
        assertEquals("ZY0000001", merged.getHospitalNo(), "★ 没传的加密列不能被清成空");
        assertEquals("A 医院", merged.getSourceUnitName(), "没传单位时名称快照与库里一致");
    }

    private static Sample sample(String verifyStatus) {
        Sample s = new Sample();
        s.setId(9000001001L);
        s.setVerifyStatus(verifyStatus);
        return s;
    }

}
