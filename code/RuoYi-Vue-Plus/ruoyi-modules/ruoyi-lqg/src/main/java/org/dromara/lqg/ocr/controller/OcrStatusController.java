package org.dromara.lqg.ocr.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.lqg.ocr.domain.vo.OcrStatusVo;
import org.dromara.lqg.ocr.service.OcrRecognizeService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 识别通道状态（工作台）—— {@code GET /lqg/ocr/status}（doc/api-contract.md「OCR」一节）。
 *
 * <p>★ 鉴权口径：复用 {@code lqg:sample:list}，<b>不新造 {@code lqg:ocr:*} 权限串</b>。
 * 两条依据：
 * <ul>
 *   <li>{@code doc/lint-profile.yaml} 写死「OCR <b>无菜单</b>（只有小程序入口）」，
 *       而 api-contract.md 的硬口径是「{@code /lqg/**} 的 {@code @SaCheckPermission} 与菜单
 *       {@code perms} 逐字一致」—— 本票不建菜单，就不该造一条没人授权的串（那会让接口对所有人 403）；</li>
 *   <li>这个页就是「样本录入」表单上那个「拍照识别」按钮的可不可以用，能看样本列表的人
 *       （101 {@code lqg_admin} / 102 {@code lqg_internal}，见 V202609221010 的 5211）
 *       本来就该能读它。外部角色没这串 → 403（识别接口 {@code /mp/ocr/recognize} 才对内外部都开放）。</li>
 * </ul>
 * ★ 为什么不用 {@code @SaCheckRole}：实测工作台 token 的 {@code rolePermission} 里没有
 * {@code lqg_admin}（Sa-Token 的 {@code getRoleList} 只认 {@code LoginUser} 里带的那份），
 * 用角色判会把 admin 也挡在 403；权限串走 {@code getPermissionList}，admin 的
 * {@code *:*:*} / menu perms 都在，与 {@code /lqg/cryo/**} 同一形态（实测可调）。
 *
 * @author OCR-IMPL-001
 */
@RequiredArgsConstructor
@RestController
@RequestMapping("/lqg/ocr")
public class OcrStatusController {

    private final OcrRecognizeService ocrRecognizeService;

    /**
     * {@code {provider, paidEnabled}}。
     */
    @SaCheckPermission("lqg:sample:list")
    @GetMapping("/status")
    public R<OcrStatusVo> status() {
        return R.ok(ocrRecognizeService.status());
    }

}
