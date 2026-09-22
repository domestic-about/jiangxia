package org.dromara.lqg.doc.mp;

import cn.dev33.satoken.annotation.SaCheckRole;
import lombok.RequiredArgsConstructor;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 小程序<b>内部人员</b>侧「文档」页签的清单端点（FLOW:F-DOC-02.step1、UI:mp.doc.list）。
 *
 * <pre>
 * GET /mp/int/doc/list?sampleId=&amp;docKind=&amp;publishedBegin=&amp;publishedEnd=&amp;pageNum=&amp;pageSize=
 * </pre>
 *
 * <p>★ <b>类级 {@code @SaCheckRole("lqg_internal")}</b>（契约第 19 行 / ADR-0004 的 I1）：
 * 外部角色（{@code lqg_external}）打这一组一律 403 —— 内部清单带内部编号，
 * 它不是「少几个字段」的问题，而是整条路不许进。外部那份走 {@code /mp/ext/doc/list}
 * （AUTH-EXT-003）。
 *
 * <p>★ <b>本类不查库、不碰 ADR-0004 的咽喉</b>：{@code /mp/int/**} 是内部侧，
 * 取数与拼装全在 {@link MpDocService}；{@code /mp/ext/**} 那一组（外部侧）仍然只由
 * {@code org.dromara.lqg.ext} 包提供。
 *
 * <p>★ 与 {@code DocRenderController} 的分工：那边是「生成 / 预览 / 下载某一版」，
 * 这边只回答「有哪些已完成的文档」（按样本分组的清单）。
 *
 * @author DOC-MP-001
 */
@RequiredArgsConstructor
@RestController
@RequestMapping("/mp/int/doc")
@SaCheckRole("lqg_internal")
public class MpDocController {

    private final MpDocService mpDocService;

    /**
     * 内部文档清单：<b>全部样本</b>里「已完成且内部版渲染成功」的文档，
     * 同一个样本的行相邻，组的先后 = 组内最新完成时间倒序。
     */
    @GetMapping("/list")
    public TableDataInfo<MpDocVo> list(MpDocQueryBo query) {
        return mpDocService.list(query);
    }

}
