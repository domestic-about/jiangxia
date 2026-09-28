package org.dromara.lqg.doc.publish;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.dev33.satoken.annotation.SaCheckPermission;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.lqg.doc.render.DocAudiences;
import org.dromara.lqg.doc.render.DocKinds;
import org.dromara.lqg.qc.service.QcDocRules;
import org.dromara.lqg.qc.service.QcDocService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

/**
 * 「完成并同步 / 撤回」状态机的契约测试（DOC-PUBLISH-001，纯反射、不碰库不碰 Spring）。
 *
 * <p>钉四件事 —— 都是「做反了 accept 会红、但不一定看得出来」的地方：
 *
 * <ol>
 *   <li><b>路径段 vs 字典值</b>：{@code sample-qc}（连字符，URL）↔ {@code sample_qc}（下划线，docKind）。
 *       本票的钩子第一版就把字典值传进了 {@code requireDocType}，表现是「保存草稿返回 400
 *       文档类型只能是 …」—— 由 ① 钉死。</li>
 *   <li><b>两个端点的形状与权限</b>：{@code POST …/{docType}/publish|unpublish}，
 *       权限 {@code lqg:qc:publish}（菜单 5503）。</li>
 *   <li><b>钩子接在 service 层</b>：{@code QcDocService} 必须持有 {@code DocPublishService}
 *       且是 setter 注入（构造器注入会与 {@code DocPublishService → 三张质控表} 成环）。</li>
 *   <li><b>两个 audience 都在</b>：完成并同步要把内部版与外部版都排进渲染（accept 1 第 6 段）。</li>
 * </ol>
 *
 * @author DOC-PUBLISH-001
 */
class DocPublishStateContractTest {

    @Test
    @DisplayName("① 路径段（连字符）→ docKind（下划线）三条映射；两种写法都认；真不认识才 400")
    void pathSegmentToDocKind() {
        assertEquals(DocKinds.SAMPLE_QC, DocPublishService.docKindOf(QcDocRules.DOC_TYPE_PATH_SAMPLE_QC));
        assertEquals(DocKinds.ORGANOID_QC, DocPublishService.docKindOf(QcDocRules.DOC_TYPE_PATH_ORGANOID_QC));
        assertEquals(DocKinds.ORGANOID_SCORE, DocPublishService.docKindOf(QcDocRules.DOC_TYPE_PATH_SCORE));

        // ★ 字典取值（下划线）也认 —— 调用链上（publish → scheduleRender、qc 包钩子）两种都会出现，
        //   不收口就会表现成「接口 400 但状态已经改了」（本票实现过程中真实踩到的那次红）
        assertEquals(DocKinds.SAMPLE_QC, DocPublishService.docKindOf(QcDocRules.DOC_TYPE_SAMPLE_QC));
        assertEquals(DocKinds.ORGANOID_QC, DocPublishService.docKindOf(QcDocRules.DOC_TYPE_ORGANOID_QC));
        assertEquals(DocKinds.ORGANOID_SCORE, DocPublishService.docKindOf(QcDocRules.DOC_TYPE_ORGANOID_SCORE));

        // 两套命名的字面值本身
        assertEquals("sample-qc", QcDocRules.DOC_TYPE_PATH_SAMPLE_QC);
        assertEquals("sample_qc", QcDocRules.DOC_TYPE_SAMPLE_QC);

        // 合并件不是质控文档类型（它的产物归 DocKinds.MERGED，不走状态机）
        assertEquals(400, assertThrows(ServiceException.class,
            () -> DocPublishService.docKindOf("merged")).getCode());
        assertEquals(400, assertThrows(ServiceException.class,
            () -> DocPublishService.docKindOf("bogus")).getCode());
    }

    @Test
    @DisplayName("② 两个端点的路径与权限：POST /lqg/qc/{sampleId}/{docType}/publish|unpublish + lqg:qc:publish")
    void endpointsAndPermission() throws Exception {
        String base = DocPublishController.class.getAnnotation(
            org.springframework.web.bind.annotation.RequestMapping.class).value()[0];
        assertEquals("/lqg/qc", base);

        assertEndpoint("publish", "/{sampleId}/{docType}/publish");
        assertEndpoint("unpublish", "/{sampleId}/{docType}/unpublish");
    }

    private void assertEndpoint(String methodName, String expectedPath) throws Exception {
        Method method = DocPublishController.class.getMethod(methodName, Long.class, String.class);
        PostMapping mapping = method.getAnnotation(PostMapping.class);
        assertNotNull(mapping, methodName + " 必须是 POST");
        assertTrue(Arrays.asList(mapping.value()).contains(expectedPath),
            methodName + " 的路径应是 " + expectedPath + "，实际 " + Arrays.toString(mapping.value()));
        SaCheckPermission perm = method.getAnnotation(SaCheckPermission.class);
        assertNotNull(perm, methodName + " 必须带权限注解");
        assertTrue(Arrays.asList(perm.value()).contains("lqg:qc:publish"),
            methodName + " 的权限应是 lqg:qc:publish，实际 " + Arrays.toString(perm.value()));
        // 返回 R<Void>：状态机端点没有响应体 payload
        assertEquals("org.dromara.common.core.domain.R", method.getReturnType().getName());
    }

    @Test
    @DisplayName("③ 钩子接在 qc 包 service 上：QcDocService 持有 DocPublishService（setter 注入，避免成环）")
    void hookWiredOnServiceNotController() throws Exception {
        Field field = QcDocService.class.getDeclaredField("docPublishService");
        assertEquals(DocPublishService.class, field.getType());
        assertNotNull(field.getAnnotation(org.springframework.beans.factory.annotation.Autowired.class),
            "必须是 setter/字段注入（构造器注入会与 DocPublishService 读三张质控表成环）");
        // 控制器不该自己记得调钩子：两个写控制器里没有 onContentChanged
        assertFalse(Arrays.stream(DocPublishController.class.getDeclaredMethods())
                .anyMatch(m -> m.getName().contains("onContentChanged")),
            "钩子不许落在 controller 里");
    }

    @Test
    @DisplayName("④ 两个 audience 都排进渲染（内部版 + 外部版；accept 1 第 6 段）")
    void bothAudiencesAreScheduled() {
        assertEquals(List.of("internal", "external"), DocAudiences.ALL);
        assertTrue(DocAudiences.ALL.contains(DocAudiences.INTERNAL));
        assertTrue(DocAudiences.ALL.contains(DocAudiences.EXTERNAL));
    }
}
