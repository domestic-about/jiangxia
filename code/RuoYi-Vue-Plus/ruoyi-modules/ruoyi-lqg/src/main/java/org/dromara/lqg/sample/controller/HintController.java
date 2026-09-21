package org.dromara.lqg.sample.controller;

import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.lqg.sample.service.HintDictionaryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 样本表单的联想词（doc/api-contract.md 的 {@code GET /mp/dict/hints?type=tissue|organoid|sample}，
 * 挂在本模块**非 ext** 的包下）。
 *
 * <p>★ <b>内外部都能调</b>（契约「通用」一节的表：{@code /mp/me}、{@code /mp/ocr/**}、
 * {@code /mp/dict/**} 是小程序内外部共用的三组；契约 SAMPLE 一节也写明这个端点）。所以这里
 * <b>不加</b> {@code @SaCheckPermission} / {@code @SaCheckRole} —— 与 ADR-0004 字面「外部用户能调的
 * 业务接口只有 {@code /mp/ext/**}」有一处边界张力，见本票完工报告的 WARN 清单：
 * ADR-0004 守的是「**业务数据**只有一条出口」，本端点返回的是**字典**（系统级配置，零样本数据、
 * 零单位信息），因此按 accept 与契约实现；ADR-0004 的咽喉不变量由
 * {@code ExtChokepointContractTest} 继续守住（本类路径不是 {@code /mp/ext} 开头，
 * 也不在 ext 包内，四条不变量都不受影响）。
 *
 * @author SAMPLE-MODEL-001
 */
@RequiredArgsConstructor
@RestController
@RequestMapping("/mp/dict")
public class HintController {

    private final HintDictionaryService hintDictionaryService;

    /**
     * 联想词：{@code type=tissue} 组织类型 / {@code organoid} 类器官类型 / {@code sample} 样本类型。
     */
    @GetMapping("/hints")
    public R<List<String>> hints(@RequestParam(required = false) String type) {
        return R.ok(hintDictionaryService.hints(type));
    }

}
