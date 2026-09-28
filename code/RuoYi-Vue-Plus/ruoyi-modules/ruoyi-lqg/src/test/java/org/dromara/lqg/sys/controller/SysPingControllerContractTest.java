package org.dromara.lqg.sys.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.annotation.SaIgnore;
import cn.dev33.satoken.annotation.SaMode;
import org.dromara.lqg.auth.staff.guard.StaffGrantRules;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;

import java.lang.reflect.Method;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * {@code GET /lqg/sys/ping} 只给内部角色（独立验收 V20）。
 *
 * <p>口径与首页两个端点同源（{@code HomeCounterContractTest} ⑥）：角色集<b>恰等于</b>
 * {@link StaffGrantRules#INTERNAL_ROLE_KEYS}，且必须是 {@link SaMode#OR}（缺省 AND 会让管理员与内部人员一起 403）。
 * 票面里用 admin（lqg_admin）/ staff（lqg_internal）调 ping 的 accept 因此照常成立，外部账号 403。
 */
class SysPingControllerContractTest {

    @Test
    @DisplayName("ping 挂内部角色闸 = INTERNAL_ROLE_KEYS（OR），不是只登录、也不匿名")
    void pingIsInternalOnly() throws Exception {
        Method ping = SysPingController.class.getMethod("ping");
        SaCheckRole role = AnnotatedElementUtils.findMergedAnnotation(ping, SaCheckRole.class);
        assertNotNull(role, "ping 必须挂 @SaCheckRole —— 只登录即可调时外部账号也读得到环境指纹");
        assertEquals(Set.copyOf(StaffGrantRules.INTERNAL_ROLE_KEYS), Set.of(role.value()),
            "角色闸必须恰等于 StaffGrantRules.INTERNAL_ROLE_KEYS = " + StaffGrantRules.INTERNAL_ROLE_KEYS);
        assertEquals(SaMode.OR, role.mode(), "必须是 SaMode.OR（Sa-Token 缺省 AND）");
        assertFalse(role.value().length == 0);

        assertFalse(AnnotatedElementUtils.hasAnnotation(ping, SaIgnore.class), "不许匿名（accept 要匿名 401）");
        assertFalse(AnnotatedElementUtils.hasAnnotation(SysPingController.class, SaIgnore.class));
        assertFalse(AnnotatedElementUtils.hasAnnotation(ping, SaCheckPermission.class),
            "不挂权限串：探针不该因为少一行菜单授权而 403");
        assertFalse(AnnotatedElementUtils.hasAnnotation(ping, SaCheckLogin.class)
                && !AnnotatedElementUtils.hasAnnotation(ping, SaCheckRole.class),
            "只挂 @SaCheckLogin 等于没闸");
    }

}
