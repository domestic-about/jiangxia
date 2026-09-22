package org.dromara.lqg.sys.home.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.mybatis.helper.DataPermissionHelper;
import org.dromara.lqg.auth.domain.ExtProfile;
import org.dromara.lqg.auth.group.guard.ExtBindStateMachine;
import org.dromara.lqg.auth.mapper.ExtProfileMapper;
import org.dromara.lqg.cryo.remind.service.CryoOverdueService;
import org.dromara.lqg.doc.render.domain.DocFile;
import org.dromara.lqg.doc.render.mapper.DocFileMapper;
import org.dromara.lqg.embed.domain.Embed;
import org.dromara.lqg.embed.mapper.EmbedMapper;
import org.dromara.lqg.sample.domain.Sample;
import org.dromara.lqg.sample.mapper.SampleMapper;
import org.dromara.lqg.sample.verify.VerifyTransitions;
import org.dromara.lqg.sys.home.domain.vo.HomeRecentVo;
import org.dromara.lqg.sys.home.domain.vo.HomeTodoVo;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 工作台首页的<b>计数唯一来源</b>（UI:admin.home，REQ-SYS-901）—— ticket §0 口径 2：
 *
 * <pre>
 * GET /lqg/home/todo    五个待办数（卡片与侧边菜单角标取同一次调用的结果）
 * GET /lqg/home/recent  最近提交 10 条
 * </pre>
 *
 * <p>★★ <b>五个数全部读时计算</b>（ticket §0 口径 1）：库里没有计数字段、没有缓存、没有定时刷新，
 * 每一次请求都现查现算 —— 外部新交一条送样、有人核验掉一条、渲染由 failed 变 done，
 * 下一次请求的数字就跟着变（accept 1 会真造数据来验这一点）。
 *
 * <p>★★ <b>每个数只从它自己域的口径取，本类不重写判定</b>（ticket §0.1 硬要求 ①）：
 *
 * <table border="1">
 *   <tr><th>数</th><th>取自</th><th>为什么不在这里写条件</th></tr>
 *   <tr>
 *     <td>{@code pendingSamples}</td>
 *     <td>{@link SampleMapper} + {@link VerifyTransitions#PENDING}</td>
 *     <td>核验状态的取值只有一个真相源（{@code VerifyTransitions}）；组织与类器官<b>都算</b>，
 *         所以条件里<b>没有</b> {@code sample_kind}（只数组织正是 accept 的 counterfeit）</td>
 *   </tr>
 *   <tr>
 *     <td>{@code pendingEmbeds}</td>
 *     <td>{@link EmbedMapper} + {@link VerifyTransitions#PENDING}</td>
 *     <td>与样本核验<b>同一张转移表</b>（EMBED-MODEL-001 复用 {@code VerifyTransitions}）</td>
 *   </tr>
 *   <tr>
 *     <td>{@code cryoOverdue}</td>
 *     <td>★ {@link CryoOverdueService#countOverdue()}</td>
 *     <td>「超期只有一个真相源」（CRYO-REMIND-001 的唯一判定函数 + 唯一一段 where）。
 *         <b>本类一个字节的超期条件都没有</b> —— 否则首页数字与超期清单会长出两个口径，
 *         accept 1 第 3 段（首页数 == 清单长度）就是钉这一格的</td>
 *   </tr>
 *   <tr>
 *     <td>{@code pendingExtUsers}</td>
 *     <td>{@link ExtProfileMapper} + {@link ExtBindStateMachine#PENDING}</td>
 *     <td>★ <b>只数 {@code pending}，不数 {@code unbound}</b>：{@code unbound} 是「还没提交单位」，
 *         不是「等人核验」（把 unbound 也算进去正是 accept 的 counterfeit）</td>
 *   </tr>
 *   <tr>
 *     <td>{@code renderFailed}</td>
 *     <td>★ {@link DocFileMapper#countFailedGroups()}</td>
 *     <td>DOC 域的读口（写在被调方）：一次渲染失败会写 <b>两行</b>（docx header + pdf），
 *         按行数数会翻倍 —— 口径是 (样本, 文档种类, 受众) <b>组数</b></td>
 *   </tr>
 * </table>
 *
 * <p>★ 读侧一律包 {@link DataPermissionHelper#ignore}：这五个数是<b>全中心</b>的待办
 * （不是「我负责的那些」），带数据范围的上下文会把它们静默滤小，
 * 于是「首页显示 1 条、点进去列表里有 5 条」——AUTH-LOGIN-001 报告坑 1 踩过。
 *
 * <p>★ 权限：登录即可调（101 / 102 / 外部都行，见 {@code HomeController}）。数字不带任何
 * 患者信息，外部角色看到的是同样的五个数，前端不给他渲染入口。
 *
 * @author SYS-HOME-001
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HomeCounterService {

    /**
     * 「最近提交」最多几行（UI:admin.home 写死 10 条）。
     */
    public static final int RECENT_LIMIT = 10;

    private final SampleMapper sampleMapper;
    private final EmbedMapper embedMapper;
    private final ExtProfileMapper extProfileMapper;
    private final DocFileMapper docFileMapper;
    private final CryoOverdueService cryoOverdueService;

    // ══════════════════════════════════════════════════════════════════════
    // GET /lqg/home/todo
    // ══════════════════════════════════════════════════════════════════════

    /**
     * 五个待办数（每次调用现算）。
     */
    public HomeTodoVo todo() {
        return DataPermissionHelper.ignore(() -> {
            HomeTodoVo vo = new HomeTodoVo();
            vo.setPendingSamples(pendingSamples());
            vo.setPendingEmbeds(pendingEmbeds());
            vo.setCryoOverdue(cryoOverdue());
            vo.setPendingExtUsers(pendingExtUsers());
            vo.setRenderFailed(renderFailed());
            return vo;
        });
    }

    /**
     * 待核验样本数：{@code t_lqg_sample} 里 {@code verify_status='pending'} 的<b>全部</b>行。
     *
     * <p>★ 组织（{@code sample_kind='tissue'}）与类器官（{@code 'organoid'}）都算 ——
     * 2026-09-17 晚起外部也能交类器官收样记录（CR-20260917-05），所以<b>没有</b> kind 条件。
     * <p>★ {@code del_flag='0'} 由 {@code @TableLogic} 兜住，本方法不手写。
     */
    public Long pendingSamples() {
        return sampleMapper.selectCount(new LambdaQueryWrapper<Sample>()
            .eq(Sample::getVerifyStatus, VerifyTransitions.PENDING));
    }

    /**
     * 待核验石蜡包埋送样数（第五张卡片之外的第二张卡片，CR-20260917-05）。
     */
    public Long pendingEmbeds() {
        return embedMapper.selectCount(new LambdaQueryWrapper<Embed>()
            .eq(Embed::getVerifyStatus, VerifyTransitions.PENDING));
    }

    /**
     * -80 超期批次数 —— <b>转发</b>给 CRYO-REMIND-001 的唯一判定（本类不写任何超期条件）。
     *
     * <p>它读的是系统参数 {@code lqg.cryo.overdue-days}（每次现读、不缓存），
     * 所以「阈值改了两边一起变」，不需要重启也不需要同步。
     */
    public Long cryoOverdue() {
        return cryoOverdueService.countOverdue();
    }

    /**
     * 待核验的外部用户档案数（{@code t_lqg_ext_profile.bind_status='pending'}）。
     *
     * <p>★ 不数 {@code unbound}：那是「登录了但还没提交单位」，没有可核验的东西；
     * 也不数 {@code rejected}（已驳回，等人重提）。
     */
    public Long pendingExtUsers() {
        return extProfileMapper.selectCount(new LambdaQueryWrapper<ExtProfile>()
            .eq(ExtProfile::getBindStatus, ExtBindStateMachine.PENDING));
    }

    /**
     * 文档渲染失败数 —— DOC 域的读口 {@link DocFileMapper#countFailedGroups()}：
     * {@code render_status='failed'} 的 (样本, 文档种类, 受众) <b>组数</b>。
     */
    public Long renderFailed() {
        return docFileMapper.countFailedGroups();
    }

    // ══════════════════════════════════════════════════════════════════════
    // GET /lqg/home/recent
    // ══════════════════════════════════════════════════════════════════════

    /**
     * 最近提交（送检时间倒序，最多 {@value #RECENT_LIMIT} 条）。
     *
     * <p>★ 只列<b>未软删</b>的样本（{@code @TableLogic}）——seed 里那条软删的
     * {@code SJ90000010} 不许出现在首页（accept 1 最后一段）。
     * <p>★ 排序键带 {@code id} 兜底：同一秒提交的两条（seed 的 1002 / 1007 都在今天）
     * 否则每次请求可能换顺序。
     */
    public List<HomeRecentVo> recent() {
        return DataPermissionHelper.ignore(() -> {
            List<Sample> rows = sampleMapper.selectList(new LambdaQueryWrapper<Sample>()
                .orderByDesc(Sample::getCreateTime)
                .orderByDesc(Sample::getId)
                .last("LIMIT " + RECENT_LIMIT));
            List<HomeRecentVo> out = new ArrayList<>(rows == null ? 0 : rows.size());
            if (rows != null) {
                for (Sample row : rows) {
                    if (row == null) {
                        continue;
                    }
                    out.add(toRecent(row));
                }
            }
            return out;
        });
    }

    private static HomeRecentVo toRecent(Sample sample) {
        HomeRecentVo vo = new HomeRecentVo();
        vo.setSubmitTime(sample.getCreateTime());
        vo.setSubmitNo(sample.getSubmitNo());
        vo.setSourceUnitName(sample.getSourceUnitName());
        vo.setSubmitSource(sample.getSubmitSource());
        vo.setVerifyStatus(sample.getVerifyStatus());
        return vo;
    }

}
