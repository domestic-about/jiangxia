package org.dromara.lqg.ext.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.mybatis.helper.DataPermissionHelper;
import org.dromara.lqg.auth.domain.ExtProfile;
import org.dromara.lqg.auth.group.guard.ExtBindStateMachine;
import org.dromara.lqg.auth.mapper.ExtProfileMapper;
import org.dromara.lqg.embed.domain.Embed;
import org.dromara.lqg.embed.mapper.EmbedMapper;
import org.dromara.lqg.sample.domain.Sample;
import org.dromara.lqg.sample.guard.SampleKindRules;
import org.dromara.lqg.sample.mapper.SampleMapper;
import org.dromara.lqg.sample.verify.VerifyTransitions;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 可见范围解析器的实现 —— <b>ext 包里唯一持有 {@code *Mapper} 的类</b>
 * （ADR-0004 的 I4：{@code ExtChokepointContractTest#i4_onlyExtScopeServiceImplTouchesMappers}
 * 按类名放行本类，扫到别的 ext 类持 mapper 就红）。
 *
 * <p>★ 为什么不把这段查询写在 {@code ExtSampleQueryService}（ext 包）里：
 * 那条查询就绕过了咽喉 —— 于是「外部只能看到本人与同组已核验者的样本」这条口径会散在每个接口各自
 * 实现里，变成「要证明所有入口都堵住了」的开放集合（ADR-0004 的 rejected_values）。
 *
 * <p>★ 口径（FLOW:F-EXT-01.step1，逐字）：
 * <pre>
 *   visibleSampleIds(userId)
 *     = 本人 submitter_id 的样本
 *     ∪ （本人 bind_status='verified' 时）同 group_id 且 bind_status='verified' 的其他外部用户提交的样本
 *   只含 del_flag='0' 的样本；不按来源单位名称匹配，只按提交人。
 * </pre>
 * 三个最容易做反的点：<b>同单位不同组不互看</b>（seed 的 extC 是病灶）、
 * <b>同组但未核验不互看</b>（seed 的 extE 是病灶，且两个方向都要挡住）、
 * <b>软删样本任何接口都不出现</b>（seed 的 1010 是病灶）。
 *
 * <p>★ 所有查询包 {@link DataPermissionHelper#ignore}：行级数据范围会把外部不该看的行滤掉，
 * 也会把<b>该看的</b>行滤掉（SAMPLE-MODEL-001 坑）；可见性只由本类算，不由数据范围算。
 *
 * @author AUTH-EXT-001
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExtScopeServiceImpl implements ExtScopeService {

    private final SampleMapper sampleMapper;
    private final ExtProfileMapper extProfileMapper;
    /**
     * FIX V17：石蜡包埋记录的可见性也收口在这里（记录 → 所挂样本 → 可见集合），
     * embed 包的外部写侧不再自己 {@code selectById} 判「有没有 / 是不是别人的」。
     */
    private final EmbedMapper embedMapper;

    /**
     * {@inheritDoc}
     *
     * <p>返回 {@link LinkedHashSet}（顺序稳定，便于排查）；空集时<b>不查样本表</b>，
     * 直接用 {@code in ()} 那样的空集合会让 MyBatis-Plus 生成恒假的 SQL 甚至语法错。
     */
    @Override
    public Set<Long> visibleSampleIds(Long userId) {
        if (userId == null) {
            return Set.of();
        }
        return DataPermissionHelper.ignore(() -> {
            Set<Long> submitters = new LinkedHashSet<>();
            submitters.add(userId);

            ExtProfile me = findProfile(userId);
            if (me != null && ExtBindStateMachine.participatesInGroupSharing(me.getBindStatus())
                && me.getGroupId() != null) {
                // 同组**且都 verified** 的人：组别相等只是必要条件，双方 bind_status 都必须是 verified
                List<ExtProfile> peers = extProfileMapper.selectList(new LambdaQueryWrapper<ExtProfile>()
                    .eq(ExtProfile::getGroupId, me.getGroupId())
                    .eq(ExtProfile::getBindStatus, ExtBindStateMachine.VERIFIED));
                peers.stream()
                    .filter(p -> ExtBindStateMachine.participatesInGroupSharing(p.getBindStatus()))
                    .map(ExtProfile::getUserId)
                    .filter(id -> id != null)
                    .forEach(submitters::add);
            }

            List<Sample> samples = sampleMapper.selectList(new LambdaQueryWrapper<Sample>()
                .select(Sample::getId)
                .in(Sample::getSubmitterId, submitters));
            return samples.stream()
                .map(Sample::getId)
                .filter(id -> id != null)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        });
    }

    /**
     * {@inheritDoc}
     *
     * <p>★ 不可见一律按「不存在」：业务码 404、{@code data} 为空。
     * <b>不用 403</b> —— 403 等于承认「这个 id 存在」，外部就能拿它探库
     * （FLOW:F-EXT-01.step2 的 {@code produces} 逐字这么写）。
     */
    @Override
    public void assertVisible(Long userId, Long sampleId) {
        if (sampleId == null || !visibleSampleIds(userId).contains(sampleId)) {
            throw new ServiceException(SAMPLE_NOT_FOUND, 404);
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>★ 先按 id 取记录（{@code @TableLogic}：软删的取不到），再看它挂的样本在不在可见集合里 ——
     * 任何一步不成立都抛<b>同一个</b>异常，调用方拿不到「有没有这条记录」的任何信号。
     */
    @Override
    public Long assertEmbedVisible(Long userId, Long embedId) {
        if (userId == null || embedId == null) {
            throw new ServiceException(EMBED_NOT_FOUND, 404);
        }
        Embed embed = DataPermissionHelper.ignore(() -> embedMapper.selectById(embedId));
        if (embed == null || embed.getSampleId() == null || !visibleSampleIds(userId).contains(embed.getSampleId())) {
            throw new ServiceException(EMBED_NOT_FOUND, 404);
        }
        return embed.getSampleId();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void assertUsableForEmbed(Long userId, Long sampleId) {
        // ① 可见（不可见 / 不存在 / 软删 → 同一个 404）
        assertVisible(userId, sampleId);
        Sample sample = DataPermissionHelper.ignore(() -> sampleMapper.selectById(sampleId));
        if (sample == null) {
            throw new ServiceException(SAMPLE_NOT_FOUND, 404);
        }
        // ② 本人（可见才走到这里：同组的样本看得到，但不能替他送样）
        if (!userId.equals(sample.getSubmitterId())) {
            throw new ServiceException("只能挂本人送检过的样本（同组的样本可以看，但不能替他送样）", 400);
        }
        // ③ 没被判无效（待核验的可以挂）
        if (VerifyTransitions.INVALID.equals(sample.getVerifyStatus())) {
            throw new ServiceException("这条样本已判无效，不能提交石蜡包埋送样", 400);
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>「本人」按 {@code submitter_id} 判，<b>不是</b> {@code create_by}：同组的人替别人建的记录
     * 不算「他提交的」（ticket §0 口径 5 的同一句话管着列表与写）。
     */
    @Override
    public boolean isMine(Long userId, Long sampleId) {
        return flagsOf(loadSample(userId, sampleId), userId).mine();
    }

    /**
     * {@inheritDoc}
     *
     * <p>★ 两个条件<b>都要</b>：本人（同组可看不可改）+ 状态 ∈ {pending, invalid}
     * （已核验有效的自己也不能改 —— 那是实验室的结论，不是外部能推翻的）。
     */
    @Override
    public boolean editable(Long userId, Long sampleId) {
        return flagsOf(loadSample(userId, sampleId), userId).editable();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Map<Long, String> submitterNames(Set<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        return DataPermissionHelper.ignore(() -> extProfileMapper.selectList(new LambdaQueryWrapper<ExtProfile>()
                .in(ExtProfile::getUserId, userIds))
            .stream()
            .filter(p -> p.getUserId() != null)
            .collect(Collectors.toMap(ExtProfile::getUserId,
                p -> p.getRealName() == null ? "" : p.getRealName(),
                (a, b) -> a)));
    }

    /**
     * {@inheritDoc}
     *
     * <p>判据与 {@link #isMine} / {@link #editable} 同源（都走 {@link #flagsOf}），
     * 不会出现「列表点了可改、详情说不可改」那种两处口径不一致。
     */
    @Override
    public Map<Long, ViewFlags> viewFlags(Long userId, Set<Long> sampleIds) {
        if (userId == null || sampleIds == null || sampleIds.isEmpty()) {
            return Map.of();
        }
        Set<Long> visible = visibleSampleIds(userId);
        Set<Long> wanted = new LinkedHashSet<>(sampleIds);
        wanted.retainAll(visible);
        if (wanted.isEmpty()) {
            return Map.of();
        }
        return DataPermissionHelper.ignore(() -> {
            Map<Long, ViewFlags> out = new LinkedHashMap<>();
            for (Sample sample : sampleMapper.selectList(new LambdaQueryWrapper<Sample>()
                .in(Sample::getId, wanted)
                .select(Sample::getId, Sample::getSubmitterId, Sample::getVerifyStatus))) {
                out.put(sample.getId(), flagsOf(sample, userId));
            }
            return out;
        });
    }

    // ── 私有 ─────────────────────────────────────────────────────────────────

    /**
     * 行内标记的唯一判据：{@code mine} = 本人提交（按 {@code submitter_id}，**不是** {@code create_by}）；
     * {@code editable} = 本人 且 状态 ∈ {pending, invalid}。
     */
    private static ViewFlags flagsOf(Sample sample, Long userId) {
        boolean mine = sample != null && userId != null && userId.equals(sample.getSubmitterId());
        String status = sample == null ? null : sample.getVerifyStatus();
        boolean editable = mine
            && (VerifyTransitions.PENDING.equals(status) || VerifyTransitions.INVALID.equals(status));
        return new ViewFlags(mine, editable);
    }

    /**
     * 取档案行（{@code del_flag='0'} 由 {@code @TableLogic} 兜住）。
     */
    private ExtProfile findProfile(Long userId) {
        return extProfileMapper.selectOne(new LambdaQueryWrapper<ExtProfile>()
            .eq(ExtProfile::getUserId, userId)
            .last("limit 1"));
    }

    /**
     * 载入样本行；<b>不可见的按不存在返回 null</b>（调用方据此判 false，不泄露存在性）。
     *
     * <p>软删行 {@code selectById} 也查不到（{@code @TableLogic}）→ 与「不存在」同样处理。
     */
    private Sample loadSample(Long userId, Long sampleId) {
        if (userId == null || sampleId == null || !visibleSampleIds(userId).contains(sampleId)) {
            return null;
        }
        return DataPermissionHelper.ignore(() -> sampleMapper.selectById(sampleId));
    }

    /**
     * 给 {@code editable} 用的人话报错（写侧被拒时抛，便于排查）。
     */
    public static String rejectionMessage(Sample sample, Long userId) {
        if (sample == null) {
            return SAMPLE_NOT_FOUND;
        }
        if (!userId.equals(sample.getSubmitterId())) {
            return "只能修改重提本人提交的样本（同组的样本可以看，但不能改）";
        }
        return "样本当前是「" + sample.getVerifyStatus() + "」，外部只能修改待核验或无效的样本";
    }

    /**
     * 类别的人话名（报错文案用）。
     */
    public static String kindName(String kind) {
        return SampleKindRules.KIND_ORGANOID.equals(SampleKindRules.normalize(kind)) ? "类器官" : "组织";
    }

}
