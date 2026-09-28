package org.dromara.lqg.cryo.flow.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.annotation.TableLogic;
import org.apache.ibatis.annotations.Select;
import org.dromara.lqg.cryo.batch.domain.CryoFlow;
import org.dromara.lqg.cryo.batch.mapper.CryoBatchMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 冻存流水五个端点的契约测试（CRYO-FLOW-001）。
 *
 * <p>钉四件事：
 * <ol>
 *   <li><b>路由形状</b>：五个端点都在 {@code /lqg/cryo/batch/{id}} 下（工作台），
 *       {@code POST /flow}、{@code PUT|DELETE /flow/{flowId}}、{@code PUT /to-ln2}、
 *       {@code GET /flows} —— 少一个或路径写歪，accept 会 404；</li>
 *   <li><b>权限串逐字</b>：三个写流水口用 {@code lqg:cryo:flow}（{@code sys_menu} 5407，
 *       上游 CRYO-MODEL-001 落的权限行）、{@code to-ln2} 用 {@code lqg:cryo:edit}（5404）、
 *       读流水用 {@code lqg:cryo:query}（5402）—— 写错串是 <b>403（不是 500）</b>；</li>
 *   <li><b>软删</b>：{@code CryoFlow.delFlag} 上有 {@code @TableLogic}（删 = 软删，
 *       追溯不断）；</li>
 *   <li><b>行锁</b>：{@code CryoBatchMapper.selectByIdForUpdate} 的 SQL 里有 {@code FOR UPDATE}
 *       （写 / 改 / 删流水前都要先锁批次行 —— accept 3 的并发用例断的就是它）。</li>
 * </ol>
 *
 * <p>★ <b>本票不新增权限行、不新增迁移</b>（{@code touches} 里本来就没有迁移）：
 * {@code lqg:cryo:flow} 由上游落好；本测试只负责证明代码里用的串与上游落的那一串一致。
 *
 * @author CRYO-FLOW-001
 */
class CryoFlowEndpointContractTest {

    @Test
    @DisplayName("① 五个端点的 HTTP 方法 + 路径 + 权限串逐字")
    void endpoints() {
        RequestMapping base = CryoFlowController.class.getAnnotation(RequestMapping.class);
        assertNotNull(base, "控制器必须有 @RequestMapping");
        assertEquals("/lqg/cryo/batch/{id}", base.value()[0]);

        List<String> actual = new ArrayList<>();
        for (Method method : CryoFlowController.class.getDeclaredMethods()) {
            String http = httpOf(method);
            if (http == null) {
                continue;
            }
            SaCheckPermission permission = method.getAnnotation(SaCheckPermission.class);
            assertNotNull(permission, method.getName() + " 必须有 @SaCheckPermission（缺了就是裸奔）");
            actual.add(http + " " + base.value()[0] + pathOf(method) + " -> " + permission.value()[0]);
        }
        actual.sort(String::compareTo);
        assertEquals(List.of(
            "DELETE /lqg/cryo/batch/{id}/flow/{flowId} -> lqg:cryo:flow",
            "GET /lqg/cryo/batch/{id}/flows -> lqg:cryo:query",
            "POST /lqg/cryo/batch/{id}/flow -> lqg:cryo:flow",
            "PUT /lqg/cryo/batch/{id}/flow/{flowId} -> lqg:cryo:flow",
            "PUT /lqg/cryo/batch/{id}/to-ln2 -> lqg:cryo:edit"
        ), actual, "★ 路由 / 权限串与 doc/api-contract.md 的 CRYO 一节必须逐字一致");
    }

    @Test
    @DisplayName("② 流水是软删：@TableLogic 在 delFlag 上；批次行锁 SQL 里有 FOR UPDATE")
    void softDeleteAndRowLock() throws Exception {
        Field delFlag = CryoFlow.class.getDeclaredField("delFlag");
        assertNotNull(delFlag.getAnnotation(TableLogic.class),
            "★ 删一笔登记必须是软删（@TableLogic）—— 物理删了「谁取走了几支」就查不到了");

        Method lock = CryoBatchMapper.class.getMethod("selectByIdForUpdate", Long.class);
        Select select = lock.getAnnotation(Select.class);
        assertNotNull(select, "selectByIdForUpdate 必须是自定义 @Select");
        String sql = String.join(" ", select.value()).toUpperCase();
        assertTrue(sql.contains("FOR UPDATE"), "★ 写 / 改 / 删流水前必须先对批次行加行锁：" + sql);
        assertTrue(sql.contains("DEL_FLAG = '0'"),
            "★ 自定义 @Select 不吃 @TableLogic，del_flag='0' 必须手写：" + sql);
    }

    @Test
    @DisplayName("③ 控制器上没有任何 /mp/** 路由：写接口只在工作台（CR-20260917-05）")
    void noMiniProgramWriteRoutes() {
        RequestMapping base = CryoFlowController.class.getAnnotation(RequestMapping.class);
        assertNotNull(base);
        assertTrue(base.value()[0].startsWith("/lqg/cryo/"),
            "★ 写流水的接口只在 /lqg/cryo/**（工作台）；小程序 /mp/int/cryo/** 只读 …/flows");
        assertNotNull(CryoFlowController.class.getAnnotation(RestController.class));
    }

    private static String httpOf(Method method) {
        if (method.getAnnotation(PostMapping.class) != null) {
            return "POST";
        }
        if (method.getAnnotation(PutMapping.class) != null) {
            return "PUT";
        }
        if (method.getAnnotation(DeleteMapping.class) != null) {
            return "DELETE";
        }
        if (method.getAnnotation(GetMapping.class) != null) {
            return "GET";
        }
        return null;
    }

    private static String pathOf(Method method) {
        PostMapping post = method.getAnnotation(PostMapping.class);
        if (post != null) {
            return post.value()[0];
        }
        PutMapping put = method.getAnnotation(PutMapping.class);
        if (put != null) {
            return put.value()[0];
        }
        DeleteMapping delete = method.getAnnotation(DeleteMapping.class);
        if (delete != null) {
            return delete.value()[0];
        }
        GetMapping get = method.getAnnotation(GetMapping.class);
        if (get != null) {
            return get.value()[0];
        }
        return "";
    }

}
