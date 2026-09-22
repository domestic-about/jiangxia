package org.dromara.lqg.embed.mp;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.lqg.embed.domain.bo.EmbedSubmitBo;
import org.dromara.lqg.embed.domain.vo.EmbedVo;
import org.dromara.lqg.embed.service.EmbedQueryService;
import org.dromara.lqg.embed.service.EmbedService;
import org.springframework.stereotype.Service;

/**
 * 小程序<b>内部人员</b>侧「石蜡包埋送样记录」的读写
 * （doc/api-contract.md 第 63 行：{@code GET /mp/int/embed/list}、{@code GET /mp/int/embed/{id}}、
 * {@code POST /mp/int/embed}、{@code PUT /mp/int/embed}）。
 *
 * <p>★ 本类<b>不自己查库、不自己写库</b>：读走 {@link EmbedQueryService}（同一份筛选、
 * 同一份「经手人 / 是不是我经手的 / 能不能改」判据），写走 {@link EmbedService}
 * （同一套编号必填与全库唯一、染色与 marker 字典校验、{@code update_by} 显式回填）。
 * 小程序与工作台对同一张表说话时用的是同一条读写路径 —— 两条路径各写一套判据迟早会漂移。
 *
 * <p>★ 三条口径（ticket §2 与 CR-20260918-07，逐条对 accept 2 核）：
 * <ol>
 *   <li><b>补填就是修改</b>：{@code PUT} 只改传了的工序时间（patch），不新增行
 *       —— 这是 {@link EmbedService#update} 本来的语义，本类<b>不加</b>任何一层「先查再拼」，
 *       免得凭空多出一份会漂移的合并逻辑。</li>
 *   <li><b>内部人员改得动中心里任何人录的记录</b>（CR-20260918-07）：本类
 *       <b>不校验「是不是本人录的」</b>（accept 2 第 1 段：2002 是管理员建的，李工照样能改）。</li>
 *   <li><b>待核验 / 无效的外部送样在小程序里改不动</b>：闸在
 *       {@link EmbedService#update}（非 {@code valid} → 400），本类<b>不重复实现、也不绕过</b>
 *       —— accept 2 最后一段断的就是「被拒之后库里一字不变」。</li>
 * </ol>
 *
 * <p>★ 详情带上 {@code updateByName} / {@code updateTime} / {@code handlerName} / {@code mine} /
 * {@code editable}（{@code EmbedQueryService.toVo} 已经装配）：修改模式顶部要显示
 * 「最后修改：某某 · 时间」，只读页右上角的「修改」与它同源。
 *
 * @author EMBED-MP-001
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MpEmbedService {

    private final EmbedQueryService embedQueryService;
    private final EmbedService embedService;

    /**
     * 列表：默认全表（内部管理表格页）；带 {@code sort=recent} 时是「历史编辑记录」
     * 那一档（中心内部人员经手过的 + 按最后修改时间倒序），开关打开再带 {@code mine=true}。
     */
    public TableDataInfo<EmbedVo> list(MpEmbedQueryBo query) {
        return embedQueryService.list(query);
    }

    /**
     * 详情：不存在 / 已软删 → 业务码 404（不泄露存在性，与工作台那条一致）。
     */
    public EmbedVo detail(Long id) {
        if (id == null) {
            throw new ServiceException("缺少石蜡包埋记录 id");
        }
        EmbedVo vo = embedQueryService.detail(id);
        if (vo == null) {
            throw new ServiceException("石蜡包埋记录不存在", 404);
        }
        return vo;
    }

    /**
     * 新增（首页点「石蜡包埋送样记录」进来）：内部录入直接 {@code valid}、石蜡块编号必填，
     * 校验全在 {@link EmbedService#create}。
     */
    public Long create(EmbedSubmitBo bo) {
        return embedService.create(bo);
    }

    /**
     * 修改 / 补填（「历史编辑记录」点一条进来，或「内部管理」只读页右上角切修改模式）。
     *
     * <p>★ 谁录的都能改（CR-20260918-07）；待核验 / 无效 → 400（核验与改判只走工作台）。
     */
    public void update(EmbedSubmitBo bo) {
        embedService.update(bo);
        log.info("小程序内部修改石蜡包埋：id={}", bo == null ? null : bo.getId());
    }

}
