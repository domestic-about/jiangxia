package org.dromara.lqg.sample.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.domain.model.LoginUser;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.helper.DataPermissionHelper;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.lqg.auth.group.service.UnitQueryService;
import org.dromara.lqg.auth.group.domain.SourceUnit;
import org.dromara.lqg.sample.domain.Sample;
import org.dromara.lqg.sample.domain.bo.SampleSubmitBo;
import org.dromara.lqg.sample.guard.SampleKindRules;
import org.dromara.lqg.sample.mapper.SampleMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 样本写侧：内部新增 / 修改 / 软删（ticket §2.2）。
 *
 * <p>★ 四条口径：
 * <ol>
 *   <li><b>内部新增直接有效</b>：{@code submit_source='internal'}、{@code verify_status='valid'}、
 *       {@code submitter_id = verify_by = 当前用户}（外部那条 pending 的路在 AUTH-EXT-001）；</li>
 *   <li><b>送检单号走序列</b>（{@link SampleSubmitNoGenerator}），不是 {@code max()+1}；</li>
 *   <li><b>内部编号手填、全库唯一、软删后可重用</b>：唯一性用 {@code @TableLogic} 过滤后的
 *       {@code selectCount} 查（软删行查不到 → 同一个编号可重录），部分唯一索引兜底；</li>
 *   <li><b>所有校验在写库之前抛</b>：accept 的每条「被拒」后面都跟一句库内断言，
 *       「先落盘再报错」是它要抓的假绿形态。</li>
 * </ol>
 *
 * @author SAMPLE-MODEL-001
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SampleService {

    private final SampleMapper sampleMapper;
    private final SampleQueryService sampleQueryService;
    private final SampleFieldCipher fieldCipher;
    private final SampleSubmitNoGenerator submitNoGenerator;
    private final UnitQueryService unitQueryService;

    /**
     * 内部新增（{@code POST /lqg/sample}）。
     */
    @Transactional(rollbackFor = Exception.class)
    public Long create(SampleSubmitBo bo) {
        if (bo == null) {
            throw new ServiceException("请求体不能为空");
        }
        String kind = SampleKindRules.normalize(bo.getSampleKind());
        if (!SampleKindRules.isKnownKind(kind)) {
            throw new ServiceException("样本类别只能是 tissue（组织样本）或 organoid（类器官）");
        }
        requireRequired(kind, bo);
        requireInternalNoUnique(bo.getInternalNo(), null);

        LoginUser loginUser = LoginHelper.getLoginUser();
        Long userId = loginUser == null ? null : loginUser.getUserId();
        if (userId == null) {
            throw new ServiceException("取不到当前登录用户，无法落提交人");
        }
        return DataPermissionHelper.ignore(() -> {
            Sample entity = new Sample();
            entity.setSubmitNo(submitNoGenerator.next());
            entity.setSampleKind(kind);
            // 内部录入：来源 internal、状态直接 valid、提交人 = 核验人 = 当前用户
            entity.setSubmitSource("internal");
            entity.setSubmitterId(userId);
            entity.setVerifyStatus("valid");
            entity.setVerifyBy(userId);
            entity.setVerifyTime(new java.util.Date());
            applyFields(entity, kind, bo);
            sampleMapper.insert(entity);
            log.info("内部新增样本：id={} submitNo={} kind={} internalNo={}",
                entity.getId(), entity.getSubmitNo(), kind, entity.getInternalNo());
            return entity.getId();
        });
    }

    /**
     * 内部修改（{@code PUT /lqg/sample}）。
     *
     * <p>{@code submitNo / submitSource / submitterId} **不可改**（契约）：它们根本不在
     * {@link SampleSubmitBo} 里，这里也不碰。
     *
     * <p>用 {@link LambdaUpdateWrapper} 而不是 {@code updateById}：本方法需要把「改成空」的字段
     * （清住院号 / 清备注 / 换类别时清掉另一类的类型列）真的写成 NULL，而 {@code updateById}
     * 默认忽略 null（{@code FieldStrategy.NOT_NULL}）—— AUTH-GROUP-001 WARN-2 的形态。
     */
    @Transactional(rollbackFor = Exception.class)
    public void update(SampleSubmitBo bo) {
        if (bo == null || bo.getId() == null) {
            throw new ServiceException("缺少样本 id");
        }
        DataPermissionHelper.ignore(() -> {
            Sample exists = sampleMapper.selectById(bo.getId());
            if (exists == null) {
                // 已软删的行 selectById 也查不到（@TableLogic）→ 与「不存在」同样处理
                throw new ServiceException("样本不存在（或已删除）");
            }
            String kind = StringUtils.isNotBlank(bo.getSampleKind())
                ? SampleKindRules.normalize(bo.getSampleKind())
                : exists.getSampleKind();
            if (!SampleKindRules.isKnownKind(kind)) {
                throw new ServiceException("样本类别只能是 tissue（组织样本）或 organoid（类器官）");
            }
            // 合并视角校验必填：没传的字段沿用库里的现值（否则「只改备注」会被必填规则误拒）
            requireRequired(kind,
                firstNonNull(bo.getTissueType(), exists.getTissueType()),
                firstNonNull(bo.getOrganoidType(), exists.getOrganoidType()),
                firstNonNull(bo.getInternalNo(), exists.getInternalNo()),
                bo.getReceiveDate() != null ? bo.getReceiveDate() : exists.getReceiveDate());
            if (StringUtils.isNotBlank(bo.getInternalNo())) {
                requireInternalNoUnique(bo.getInternalNo(), exists.getId());
            }
            LambdaUpdateWrapper<Sample> patch = new LambdaUpdateWrapper<Sample>()
                .eq(Sample::getId, exists.getId())
                // 显式补 update_by / update_time：用 `update(null, wrapper)` 时 MyBatis-Plus 的
                // updateFill 拿不到 BaseEntity（参数对象是 wrapper 的参数 Map），不会自动填充 update_by；
                // 而 REQ-SAMPLE-016 / CR-20260917-04 的「最后修改：某某 · 时间」正是读 update_by。
                .set(Sample::getUpdateBy, currentUserId())
                .set(Sample::getUpdateTime, new java.util.Date())
                .set(Sample::getSampleKind, kind)
                .set(Sample::getTissueType, SampleKindRules.KIND_TISSUE.equals(kind) ? bo.getTissueType() : null)
                .set(Sample::getOrganoidType, SampleKindRules.KIND_ORGANOID.equals(kind) ? bo.getOrganoidType() : null)
                .set(Sample::getInternalNo, trimToNull(bo.getInternalNo()))
                .set(Sample::getReceiveDate, bo.getReceiveDate())
                .set(Sample::getSourceUnitId, bo.getSourceUnitId())
                .set(Sample::getSourceUnitName, resolveUnitName(bo))
                .set(Sample::getDonorName, fieldCipher.encrypt(bo.getDonorName()))
                .set(Sample::getGender, trimToNull(bo.getGender()))
                .set(Sample::getAge, trimToNull(bo.getAge()))
                .set(Sample::getHospitalNo, fieldCipher.encrypt(bo.getHospitalNo()))
                .set(Sample::getHasPathology, trimToNull(bo.getHasPathology()))
                .set(Sample::getIsFixed, trimToNull(bo.getIsFixed()))
                .set(Sample::getProcessTime, bo.getProcessTime())
                .set(Sample::getHasQcSheet, trimToNull(bo.getHasQcSheet()))
                .set(Sample::getHasViabilityReport, trimToNull(bo.getHasViabilityReport()))
                .set(Sample::getOperatorName, trimToNull(bo.getOperatorName()))
                .set(Sample::getRemark, trimToNull(bo.getRemark()));
            sampleMapper.update(null, patch);
            log.info("修改样本：id={} kind={} internalNo={}", exists.getId(), kind, bo.getInternalNo());
            return null;
        });
    }

    /**
     * 软删（{@code DELETE /lqg/sample/{ids}}）：{@code @TableLogic} 把 {@code del_flag} 置 1。
     *
     * <p>★ 软删之后同一个 {@code internalNo} 可以重新录（部分唯一索引
     * {@code uk_sample_internal_no WHERE del_flag='0'}）——这是甲方口径，accept 第 3 条断它。
     *
     * @param ids 逗号分隔的 id 串（契约形状）
     * @return 实际删掉的行数
     */
    @Transactional(rollbackFor = Exception.class)
    public int remove(String ids) {
        List<Long> idList = parseIds(ids);
        if (idList.isEmpty()) {
            throw new ServiceException("缺少样本 id");
        }
        return DataPermissionHelper.ignore(() -> {
            int affected = sampleMapper.deleteByIds(idList);
            log.info("软删样本：ids={} 影响行数={}", idList, affected);
            return affected;
        });
    }

    // ── 私有 ─────────────────────────────────────────────────────────────────

    private Long currentUserId() {
        LoginUser loginUser = LoginHelper.getLoginUser();
        return loginUser == null ? null : loginUser.getUserId();
    }

    /**
     * 新增路径的必填校验（按类别分化）。
     */
    private void requireRequired(String kind, SampleSubmitBo bo) {
        requireRequired(kind, bo.getTissueType(), bo.getOrganoidType(), bo.getInternalNo(), bo.getReceiveDate());
    }

    private void requireRequired(String kind, String tissueType, String organoidType, String internalNo, Object receiveDate) {
        List<String> missing = SampleKindRules.missingRequiredFields(kind, tissueType, organoidType, internalNo, receiveDate);
        if (!missing.isEmpty()) {
            throw new ServiceException(String.format("「%s」类样本缺少必填项：%s",
                SampleKindRules.KIND_ORGANOID.equals(kind) ? "类器官" : "组织", String.join("、", missing)));
        }
    }

    /**
     * 内部编号全库唯一，但**软删后可重用**：查重走 mapper（{@code @TableLogic} 自动只查
     * {@code del_flag='0'}），因此被软删的旧行不会挡住新录入。
     */
    private void requireInternalNoUnique(String internalNo, Long excludeId) {
        String value = SampleKindRules.normalize(internalNo);
        if (StringUtils.isBlank(value)) {
            return;
        }
        LambdaQueryWrapper<Sample> wrapper = new LambdaQueryWrapper<Sample>()
            .eq(Sample::getInternalNo, value)
            .ne(excludeId != null, Sample::getId, excludeId);
        if (sampleMapper.selectCount(wrapper) > 0) {
            throw new ServiceException("内部编号「" + value + "」已存在，请换一个");
        }
    }

    /**
     * 把入参与「单位名称快照」合并进实体（新增路径）。
     */
    private void applyFields(Sample entity, String kind, SampleSubmitBo bo) {
        entity.setSourceUnitId(bo.getSourceUnitId());
        entity.setSourceUnitName(resolveUnitName(bo));
        entity.setDonorName(fieldCipher.encrypt(bo.getDonorName()));
        entity.setGender(trimToNull(bo.getGender()));
        entity.setAge(trimToNull(bo.getAge()));
        entity.setHospitalNo(fieldCipher.encrypt(bo.getHospitalNo()));
        entity.setTissueType(SampleKindRules.KIND_TISSUE.equals(kind) ? trimToNull(bo.getTissueType()) : null);
        entity.setOrganoidType(SampleKindRules.KIND_ORGANOID.equals(kind) ? trimToNull(bo.getOrganoidType()) : null);
        entity.setHasPathology(trimToNull(bo.getHasPathology()));
        entity.setReceiveDate(bo.getReceiveDate());
        entity.setInternalNo(SampleKindRules.normalize(bo.getInternalNo()));
        entity.setIsFixed(trimToNull(bo.getIsFixed()));
        entity.setProcessTime(bo.getProcessTime());
        entity.setHasQcSheet(trimToNull(bo.getHasQcSheet()));
        entity.setHasViabilityReport(trimToNull(bo.getHasViabilityReport()));
        entity.setOperatorName(StringUtils.isNotBlank(bo.getOperatorName())
            ? bo.getOperatorName().trim()
            : currentNickname());
        entity.setRemark(trimToNull(bo.getRemark()));
    }

    /**
     * 来源单位名称快照：选了单位就取单位表的当前名称，否则用请求里的名称（ticket §2.2）。
     */
    private String resolveUnitName(SampleSubmitBo bo) {
        if (bo.getSourceUnitId() != null) {
            SourceUnit unit = unitQueryService.findUnit(bo.getSourceUnitId());
            if (unit == null) {
                throw new ServiceException("来源单位不存在");
            }
            return unit.getUnitName();
        }
        return trimToNull(bo.getSourceUnitName());
    }

    private String currentNickname() {
        LoginUser loginUser = LoginHelper.getLoginUser();
        return loginUser == null ? null : loginUser.getNickname();
    }

    private static String trimToNull(String value) {
        return StringUtils.isBlank(value) ? null : value.trim();
    }

    private static String firstNonNull(String candidate, String fallback) {
        return StringUtils.isNotBlank(candidate) ? candidate : fallback;
    }

    /**
     * {@code "1,2,3"} → {@code [1,2,3]}；非法片段直接报错（不静默忽略）。
     */
    static List<Long> parseIds(String ids) {
        if (StringUtils.isBlank(ids)) {
            return List.of();
        }
        List<Long> out = new ArrayList<>();
        for (String piece : Arrays.asList(ids.split(","))) {
            String value = piece.trim();
            if (value.isEmpty()) {
                continue;
            }
            try {
                out.add(Long.valueOf(value));
            } catch (NumberFormatException e) {
                throw new ServiceException("样本 id 不是数字：" + value);
            }
        }
        return out;
    }

}
