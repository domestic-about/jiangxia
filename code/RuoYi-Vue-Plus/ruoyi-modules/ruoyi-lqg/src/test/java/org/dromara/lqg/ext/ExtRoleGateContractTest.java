package org.dromara.lqg.ext;

import cn.dev33.satoken.annotation.SaCheckRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 外部接口组的<b>角色闸</b>契约测试（FIX V21）。
 *
 * <p>契约第 20 行：{@code /mp/ext/**} 是唯一对 {@code lqg_external} 开放的业务接口组。
 * ext 包里每个 controller 都必须带类级 {@code @SaCheckRole("lqg_external")}，
 * 例外只有下面这张表里<b>按类名写死</b>的几个、并写明理由（别扩大）。
 *
 * <p>病灶（独立验收 V21，活体已证）：{@code ExtProfileController} 漏了这道闸，内部账号打
 * {@code PUT /mp/ext/profile} 会在 {@code t_lqg_ext_profile} 里给自己补建一行外部档案。
 * 需求层的 {@code ExtChokepointContractTest}（逐字节钉住、不许改）不查角色注解，所以单列本类。
 *
 * @author FIX-V21
 */
class ExtRoleGateContractTest {

    /** 刻意不设外部角色闸的 ext controller → 理由。 */
    private static final Map<String, String> EXEMPT = Map.of(
        "ExtUnitController", "GET /mp/ext/units 是单位—组别选择器数据：内部人员的类器官表单也要拉（只有 id 与名称，不带样本数据）");

    @Test
    @DisplayName("ext 包每个 controller 都带类级 @SaCheckRole(\"lqg_external\")（例外按类名写死）")
    void everyExtControllerIsGatedByTheExternalRole() throws Exception {
        ClassPathScanningCandidateComponentProvider p = new ClassPathScanningCandidateComponentProvider(false);
        p.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
        List<String> gated = new ArrayList<>();
        for (BeanDefinition bd : p.findCandidateComponents("org.dromara.lqg.ext")) {
            Class<?> c = Class.forName(bd.getBeanClassName());
            if (EXEMPT.containsKey(c.getSimpleName())) {
                continue;
            }
            SaCheckRole role = AnnotatedElementUtils.findMergedAnnotation(c, SaCheckRole.class);
            assertNotNull(role, c.getSimpleName() + " 缺类级 @SaCheckRole(\"lqg_external\")：内部账号就能打这个外部接口");
            assertEquals(List.of("lqg_external"), Arrays.asList(role.value()), c.getSimpleName() + " 的角色闸不是 lqg_external");
            gated.add(c.getSimpleName());
        }
        assertTrue(gated.contains("ExtProfileController"), "★ V21 的病灶：PUT /mp/ext/profile 必须在闸内：" + gated);
        assertTrue(gated.size() >= 5, "ext 包里被闸住的 controller 太少，扫描可能在空转：" + gated);
    }

}
