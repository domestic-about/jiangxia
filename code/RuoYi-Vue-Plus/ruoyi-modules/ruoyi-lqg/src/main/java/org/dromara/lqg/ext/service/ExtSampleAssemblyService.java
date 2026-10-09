package org.dromara.lqg.ext.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.common.mybatis.helper.DataPermissionHelper;
import org.dromara.lqg.ext.domain.bo.ExtSampleQueryBo;
import org.dromara.lqg.ext.domain.vo.ExtSampleDetailVo;
import org.dromara.lqg.ext.domain.vo.ExtSampleVo;
import org.dromara.lqg.ext.guard.MaskRules;
import org.dromara.lqg.sample.domain.Sample;
import org.dromara.lqg.sample.domain.vo.SampleVo;
import org.dromara.lqg.sample.service.SampleFieldCipher;
import org.dromara.lqg.sample.service.SampleQueryService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 外部样本的<b>拼装</b>（{@code GET /mp/ext/sample/list} 与 {@code GET /mp/ext/sample/{id}}）。
 *
 * <p>★ 本类<b>不持有任何 {@code *Mapper}</b>（ADR-0004 的 I4）：读库在两侧 ——
 * 可见集合经 {@link ExtScopeService}（ext 包里唯一能碰 mapper 的实现类），
 * 样本行经 {@code org.dromara.lqg.sample.service.SampleQueryService}（sample 包）。
 * 本类只做「把内部行装成对外 VO」这一件事。
 *
 * <p>★ 三条拼装口径，逐条对着 accept 抄：
 * <ol>
 *   <li><b>列表给掩码、详情给全名</b>：{@code donorNameMasked} = 首字 + {@code "**"}；
 *       密文解密在 {@code SampleQueryService} 读出时就做了（ADR-0006 的裸 Base64 口径）。</li>
 *   <li><b>{@code mine} / {@code editable} 由范围解析器算</b>，不在本类里另写一套判据
 *       （{@code isMine} 按 {@code submitter_id}，{@code editable} = 本人 且 状态 ∈ {pending, invalid}）。</li>
 *   <li><b>排序按 {@code COALESCE(update_time, create_time)} 倒序</b>（改过的排前面），
 *       同刻用 id 倒序兜稳定。</li>
 * </ol>
 *
 * @author AUTH-EXT-001
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExtSampleAssemblyService {

    private final ExtScopeService extScopeService;
    private final ExtInternalNoSwitch internalNoSwitch;
    private final ExtEmbedAssemblyService extEmbedAssemblyService;
    private final ExtDocAssemblyService extDocAssemblyService;
    private final SampleQueryService sampleQueryService;
    private final SampleFieldCipher fieldCipher;

    /**
     * 外部样本列表（「历史编辑记录」的样本两个页签）。
     *
     * <p>★ 可见集合**先**算：没有任何筛选能把可见集合放大；{@code sampleKind} / {@code verifyStatus}
     * / {@code onlyMine} 都只是在它之上再收窄。
     */
    public TableDataInfo<ExtSampleVo> list(Long userId, ExtSampleQueryBo query) {
        ExtSampleQueryBo q = query == null ? new ExtSampleQueryBo() : query;
        Set<Long> visible = extScopeService.visibleSampleIds(userId);
        if (visible.isEmpty()) {
            // 可见集合为空 → 不查库，直接给空页（也避免 `in ()` 那种空集合 SQL）
            return TableDataInfo.build(new ArrayList<>());
        }
        String kind = trimToNull(q.getSampleKind());
        String status = trimToNull(q.getVerifyStatus());
        boolean onlyMine = Boolean.TRUE.equals(q.getOnlyMine());

        return DataPermissionHelper.ignore(() -> {
            Page<Sample> page = q.build();
            Page<Sample> result = samplePage(page, userId, visible, kind, status, onlyMine);
            List<ExtSampleVo> rows = new ArrayList<>(result.getRecords().size());
            if (!result.getRecords().isEmpty()) {
                // 提交人姓名一次性批量取（N+1 会随页大小线性放大）
                Set<Long> submitters = new HashSet<>();
                Set<Long> ids = new HashSet<>();
                result.getRecords().forEach(s -> {
                    ids.add(s.getId());
                    if (s.getSubmitterId() != null) {
                        submitters.add(s.getSubmitterId());
                    }
                });
                Map<Long, String> names = extScopeService.submitterNames(submitters);
                // mine / editable 也批量算（逐行调 isMine+editable 会 2N 次范围解析）
                Map<Long, ExtScopeService.ViewFlags> flags = extScopeService.viewFlags(userId, ids);
                for (Sample sample : result.getRecords()) {
                    rows.add(toRow(sample, names.get(sample.getSubmitterId()),
                        flags.getOrDefault(sample.getId(), new ExtScopeService.ViewFlags(false, false))));
                }
            }
            return TableDataInfo.build(new Page<ExtSampleVo>(result.getCurrent(), result.getSize(), result.getTotal())
                .setRecords(rows));
        });
    }

    /**
     * 外部样本详情。**调用方必须先 {@code assertVisible}**（不可见按不存在回 404，不泄露存在性）。
     *
     * <p>{@code embeds} / {@code docs} 本票恒为空数组（AUTH-EXT-002 / AUTH-EXT-003 接）。
     */
    public ExtSampleDetailVo detail(Long userId, Long sampleId) {
        SampleVo sample = sampleQueryService.detail(sampleId);
        if (sample == null) {
            // 可见集合里有、行却读不到（并发软删）→ 同样按「不存在」
            return null;
        }
        ExtSampleDetailVo vo = new ExtSampleDetailVo();
        vo.setId(sample.getId());
        vo.setSubmitNo(sample.getSubmitNo());
        vo.setSampleKind(sample.getSampleKind());
        vo.setSourceUnitName(sample.getSourceUnitName());
        vo.setSpecies(sample.getSpecies());
        vo.setDonorName(sample.getDonorName());
        vo.setGender(sample.getGender());
        vo.setAge(sample.getAge());
        vo.setHospitalNo(sample.getHospitalNo());
        vo.setTissueType(sample.getTissueType());
        vo.setOrganoidType(sample.getOrganoidType());
        vo.setPassage(sample.getPassage());
        vo.setHasPathology(sample.getHasPathology());
        vo.setRemark(sample.getRemark());
        vo.setVerifyStatus(sample.getVerifyStatus());
        vo.setInvalidReason(sample.getInvalidReason());
        // ★ 只有开关打开时才填：关着时保持 null，@JsonInclude(NON_NULL) 连键都不出
        if (internalNoSwitch.enabled()) {
            vo.setInternalNo(sample.getInternalNo());
        }
        vo.setSubmitterName(extScopeService.submitterNames(Set.of(sample.getSubmitterId()))
            .get(sample.getSubmitterId()));
        ExtScopeService.ViewFlags flags = extScopeService.viewFlags(userId, Set.of(sampleId))
            .getOrDefault(sampleId, new ExtScopeService.ViewFlags(false, false));
        vo.setMine(flags.mine());
        vo.setEditable(flags.editable());
        vo.setCreateTime(sample.getCreateTime());
        // ★ 第②段：该样本名下未删的全部石蜡包埋记录 —— **含外部提交还没核验的送样**
        //   （paraffinBlockNo 空、带状态与无效原因），外部要能看到自己送的样走到哪一步
        //   （FLOW:F-EMBED-01.step4 的 produces）。装配在 embed 域 + ext 拼装层，本类不碰 mapper。
        vo.setEmbeds(extEmbedAssemblyService.embedsOfSample(userId, sampleId));
        // ★ 质控文档：与 {@code GET /mp/ext/doc/list} **同一份查询**（可见样本 ∩ 已完成 ∩
        //   外部版渲染成功），详情与列表不可能给出两套答案（AUTH-EXT-003）。
        vo.setDocs(extDocAssemblyService.docsOfSample(sampleId));
        return vo;
    }

    /**
     * 样本行 → 对外列表行。
     *
     * <p>★ {@code donorName} 在库里是<b>密文</b>（ADR-0006 的裸 Base64 口径，不走框架
     * {@code @EncryptField}）：必须经 {@link SampleFieldCipher#decrypt} 解出来再打码 ——
     * 直接对密文打码会得到 {@code "T**"} 这种垃圾（本票实测踩过，见完工报告坑清单）。
     */
    private ExtSampleVo toRow(Sample sample, String submitterName, ExtScopeService.ViewFlags flags) {
        ExtSampleVo vo = new ExtSampleVo();
        vo.setId(sample.getId());
        vo.setSubmitNo(sample.getSubmitNo());
        vo.setSampleKind(sample.getSampleKind());
        vo.setDonorNameMasked(MaskRules.maskDonorName(fieldCipher.decrypt(sample.getDonorName())));
        vo.setSpecies(sample.getSpecies());
        vo.setTissueType(sample.getTissueType());
        vo.setOrganoidType(sample.getOrganoidType());
        vo.setVerifyStatus(sample.getVerifyStatus());
        // ★ 无效原因也带上（D2 r1 L2 S1-2）：外部要在「历史编辑记录」里看到原因才能照它改后重提
        vo.setInvalidReason(sample.getInvalidReason());
        vo.setSubmitterName(submitterName);
        vo.setMine(flags.mine());
        vo.setEditable(flags.editable());
        vo.setCreateTime(sample.getCreateTime());
        vo.setUpdateTime(sample.getUpdateTime());
        return vo;
    }

    /**
     * 按可见集合查页。
     *
     * <p>★ 排序走 {@code last(ORDER BY …)} 而不是 {@code orderByDesc}：MyBatis-Plus 3.5.16 的
     * {@code Func} 接口<b>只留了 {@code SFunction} 重载</b>（{@code orderByDesc(R, R...)}，
     * {@code R = SFunction<Sample,?>}），没有接受列名字符串的重载 —— 实测编译期就红：
     * {@code no suitable method found for orderByDesc(String)}。而票面要求按
     * {@code COALESCE(update_time, create_time)} 倒序（改过的排前面），那是表达式不是列引用。
     * 分页插件的 {@code LIMIT} 加在这段 {@code ORDER BY} 之后，两者不冲突。
     */
    private Page<Sample> samplePage(Page<Sample> page, Long userId, Set<Long> visible,
                                    String kind, String status, boolean onlyMine) {
        LambdaQueryWrapper<Sample> wrapper = new LambdaQueryWrapper<Sample>()
            .in(Sample::getId, visible)
            .eq(kind != null, Sample::getSampleKind, kind)
            .eq(status != null, Sample::getVerifyStatus, status)
            // 「只看我提交的」= 在可见集合里按 submitter_id 收窄，不是另起一套范围
            .eq(onlyMine && userId != null, Sample::getSubmitterId, userId)
            // 时间相同（seed 的「今天零点过几秒」那几条）时用 id 倒序兜稳定
            .last("ORDER BY COALESCE(update_time, create_time) DESC, id DESC");
        return sampleQueryService.selectExtPage(page, wrapper);
    }

    private static String trimToNull(String value) {
        return StringUtils.isBlank(value) ? null : value.trim();
    }
}
