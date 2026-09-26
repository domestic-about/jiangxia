package org.dromara.lqg.sample.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.lqg.auth.group.domain.SourceUnit;
import org.dromara.lqg.auth.group.service.UnitQueryService;
import org.dromara.lqg.sample.domain.Sample;
import org.dromara.lqg.sample.domain.bo.SampleSubmitSegmentBo;
import org.dromara.lqg.sample.guard.SampleKindRules;
import org.dromara.lqg.sample.guard.SubmitSegmentRules;
import org.dromara.lqg.sample.guard.SubmitSegmentRules.UnitRef;
import org.springframework.stereotype.Component;

/**
 * 送检段的<b>唯一落库口径</b>（FIX V02：避免两套规则）。
 *
 * <p>以前三条写路径各写一份送检段的列：工作台修改（{@code SampleService.update}）、
 * 外部重提（{@code SampleVerifyService.resubmitByExternal}），而工作台核验抽屉根本没写
 * （issue #147 / V02：送检段显示成可编辑，点「判为有效并保存」却被静默丢弃）。
 * 现在三条路 + 新建样本都经本类：同一组列、同一个加密、同一个单位名快照。
 *
 * <p>★ 按样本<b>自己的</b>类别写（{@code SampleKindRules}）：
 * <ul>
 *   <li>两类都写：来源单位 id + 名称快照、备注；</li>
 *   <li>组织样本：供体姓名（加密）、性别、年龄、住院号（加密）、组织类型、有无病理；类器官类型与代数置空
 *       （组织样本没有代数：传了也写 NULL，不报错 —— 与「另一类的类型列传了不生效」同口径）；</li>
 *   <li>类器官：类器官类型、代数（CR-20260924-10，归一化成大写 P 开头）；组织类型置空。
 *       供体姓名等组织样本字段<b>不碰</b>（类器官收样记录没有这些列，REQ-SAMPLE-007）。</li>
 * </ul>
 * 语义是<b>整段替换</b>：送检段里没给的字段写成 NULL —— 所以调用方给的必须是整份送检段
 * （工作台表单、核验抽屉、外部填写页都是整份提交）。「补丁」语义（只改传了的）在
 * {@code MpSampleService.mergePatch} 那一层先合并成整份再进来。
 *
 * @author FIX-V02
 */
@Component
@RequiredArgsConstructor
public class SampleSubmitSegmentWriter {

    private final SampleFieldCipher fieldCipher;
    private final UnitQueryService unitQueryService;

    /**
     * 内部口径的来源单位（工作台 / 小程序内部人员 / 核验人）：给了 id → 单位必须存在，
     * 名称取单位表的<b>当前</b>名称作快照；没给 id → 用请求里的名称（去首尾空白）。
     *
     * @throws ServiceException 400：给的单位 id 不存在（或已删除）
     */
    public UnitRef resolveInternalUnit(Long unitId, String unitName) {
        if (unitId != null) {
            SourceUnit unit = unitQueryService.findUnit(unitId);
            if (unit == null) {
                throw new ServiceException(SubmitSegmentRules.MSG_PREFIX + "来源单位不存在（或已删除），请重新选择", 400);
            }
            return new UnitRef(unit.getId(), unit.getUnitName());
        }
        return new UnitRef(null, trimToNull(unitName));
    }

    /**
     * id 有值时把名称刷新成单位表的当前名称（单位行没了就沿用手里的快照）。
     */
    public UnitRef refresh(UnitRef ref) {
        if (ref == null || ref.id() == null) {
            return ref;
        }
        SourceUnit unit = unitQueryService.findUnit(ref.id());
        return unit == null ? ref : new UnitRef(unit.getId(), unit.getUnitName());
    }

    /**
     * 送检段写进一条 {@code UPDATE}（整段替换，按样本自己的类别）。
     */
    public void applyTo(LambdaUpdateWrapper<Sample> patch, String kind, SampleSubmitSegmentBo seg, UnitRef unit) {
        SampleSubmitSegmentBo s = seg == null ? new SampleSubmitSegmentBo() : seg;
        patch.set(Sample::getSourceUnitId, unit == null ? null : unit.id())
            .set(Sample::getSourceUnitName, unit == null ? null : unit.name())
            .set(Sample::getRemark, trimToNull(s.getRemark()));
        if (isOrganoid(kind)) {
            patch.set(Sample::getOrganoidType, trimToNull(s.getOrganoidType()))
                .set(Sample::getPassage, SubmitSegmentRules.normalizePassage(s.getPassage()))
                .set(Sample::getTissueType, null);
        } else {
            patch.set(Sample::getTissueType, trimToNull(s.getTissueType()))
                .set(Sample::getOrganoidType, null)
                .set(Sample::getPassage, null)
                .set(Sample::getDonorName, fieldCipher.encrypt(trimToNull(s.getDonorName())))
                .set(Sample::getGender, trimToNull(s.getGender()))
                .set(Sample::getAge, trimToNull(s.getAge()))
                .set(Sample::getHospitalNo, fieldCipher.encrypt(trimToNull(s.getHospitalNo())))
                .set(Sample::getHasPathology, trimToNull(s.getHasPathology()));
        }
    }

    /**
     * 送检段写进一条<b>新建</b>的实体（与 {@link #applyTo(LambdaUpdateWrapper, String, SampleSubmitSegmentBo, UnitRef)}
     * 同一组列）。
     */
    public void applyTo(Sample entity, String kind, SampleSubmitSegmentBo seg, UnitRef unit) {
        SampleSubmitSegmentBo s = seg == null ? new SampleSubmitSegmentBo() : seg;
        entity.setSourceUnitId(unit == null ? null : unit.id());
        entity.setSourceUnitName(unit == null ? null : unit.name());
        entity.setRemark(trimToNull(s.getRemark()));
        if (isOrganoid(kind)) {
            entity.setOrganoidType(trimToNull(s.getOrganoidType()));
            entity.setPassage(SubmitSegmentRules.normalizePassage(s.getPassage()));
            entity.setTissueType(null);
        } else {
            entity.setTissueType(trimToNull(s.getTissueType()));
            entity.setOrganoidType(null);
            entity.setPassage(null);
            entity.setDonorName(fieldCipher.encrypt(trimToNull(s.getDonorName())));
            entity.setGender(trimToNull(s.getGender()));
            entity.setAge(trimToNull(s.getAge()));
            entity.setHospitalNo(fieldCipher.encrypt(trimToNull(s.getHospitalNo())));
            entity.setHasPathology(trimToNull(s.getHasPathology()));
        }
    }

    private static boolean isOrganoid(String kind) {
        return SampleKindRules.KIND_ORGANOID.equals(SampleKindRules.normalize(kind));
    }

    private static String trimToNull(String value) {
        return StringUtils.isBlank(value) ? null : value.trim();
    }

}
