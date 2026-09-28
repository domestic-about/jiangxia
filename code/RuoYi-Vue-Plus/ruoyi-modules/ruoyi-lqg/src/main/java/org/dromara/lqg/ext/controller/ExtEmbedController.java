package org.dromara.lqg.ext.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.lqg.embed.service.EmbedExternalService;
import org.dromara.lqg.ext.domain.bo.ExtEmbedQueryBo;
import org.dromara.lqg.ext.domain.bo.ExtEmbedSubmitBo;
import org.dromara.lqg.ext.domain.vo.ExtEmbedVo;
import org.dromara.lqg.ext.service.ExtEmbedAssemblyService;
import org.dromara.lqg.ext.service.ExtScopeService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 外部「石蜡包埋送样记录」的四个端点（doc/api-contract.md 第 63-64 行）：
 *
 * <pre>
 * GET  /mp/ext/embed/list   可见样本名下的全部未删石蜡包埋记录（含外部提交还没核验的送样）
 * GET  /mp/ext/embed/{id}   单条（先按记录取 sampleId，再 assertVisible；不可见按「不存在」回 404）
 * POST /mp/ext/embed        外部提交送样 → external / pending / 石蜡块编号为空
 * PUT  /mp/ext/embed/{id}   本人待核验 / 无效的送样改后重提（回到 pending）
 * </pre>
 *
 * <p>★ <b>类级 {@code @SaCheckRole("lqg_external")}</b>（ADR-0004 的 I1 配套）：
 * 内部账号打这里天然 403（角色 103 之外的人不满足），外部账号打 {@code /lqg/**} /
 * {@code /mp/int/**} 也天然 403。
 *
 * <p>★ <b>本类只做转发、不查库</b>：可见范围在 {@code ExtScopeService}（ext 包里唯一允许持有
 * {@code *Mapper} 的实现类），记录装配在 {@link ExtEmbedAssemblyService}（无 mapper），
 * 写侧在 {@code org.dromara.lqg.embed.service.EmbedExternalService}（embed 包）——
 * ADR-0004 的 I4 就是这么要求的（AUTH-GROUP-001 的返工教训）。
 *
 * <p>★ <b>入参只有 {@link ExtEmbedSubmitBo} 那三个键</b>：石蜡块编号 / 工序时间 / 染色 / marker /
 * 核验状态 / 包埋人 / 操作人由实验室在核验与补填时给，外部夹带连反序列化的落点都没有
 * （accept 段 2 用它们断「夹带不生效」）。
 *
 * @author AUTH-EXT-002
 */
@RequiredArgsConstructor
@RestController
@RequestMapping("/mp/ext/embed")
@SaCheckRole("lqg_external")
public class ExtEmbedController {

    private final ExtEmbedAssemblyService extEmbedAssemblyService;
    private final EmbedExternalService embedExternalService;

    /**
     * 列表：可见样本集合 → 按 {@code verifyStatus / onlyMine} 收窄 → 装配。
     */
    @GetMapping("/list")
    public TableDataInfo<ExtEmbedVo> list(ExtEmbedQueryBo query) {
        return extEmbedAssemblyService.list(currentUserId(), query);
    }

    /**
     * 单条：不可见 / 不存在 / 已软删一律同一个 404、同一句 {@link ExtScopeService#EMBED_NOT_FOUND}
     * （不泄露存在性；判据在 {@code ExtScopeService.assertEmbedVisible}，与写口同一处）。
     */
    @GetMapping("/{id}")
    public R<ExtEmbedVo> detail(@PathVariable Long id) {
        ExtEmbedVo vo = extEmbedAssemblyService.detail(currentUserId(), id);
        if (vo == null) {
            throw new ServiceException(ExtScopeService.EMBED_NOT_FOUND, 404);
        }
        return R.ok(vo);
    }

    /**
     * 外部提交石蜡包埋送样（只能挂本人送检过、没被判无效的样本）。
     *
     * <p>落库 {@code external / pending / 编号空}；能挂哪些样本由
     * {@code EmbedExternalService.submit} 一处判（可见 ≠ 能替他送样）。
     */
    @PostMapping
    public R<Long> submit(@Valid @RequestBody ExtEmbedSubmitBo bo) {
        return R.ok(embedExternalService.submit(currentUserId(), bo.getSampleId(),
            bo.getSampleType(), bo.getOrganoidSourceType()));
    }

    /**
     * 改后重提（本人 + pending / invalid；实验室录入的块外部永远只读）。
     */
    @PutMapping("/{id}")
    public R<Void> resubmit(@PathVariable Long id, @RequestBody ExtEmbedSubmitBo bo) {
        embedExternalService.resubmit(currentUserId(), id, bo.getSampleId(),
            bo.getSampleType(), bo.getOrganoidSourceType());
        return R.ok();
    }

    private Long currentUserId() {
        Long userId = LoginHelper.getUserId();
        if (userId == null) {
            throw new ServiceException("未登录", 401);
        }
        return userId;
    }

}
