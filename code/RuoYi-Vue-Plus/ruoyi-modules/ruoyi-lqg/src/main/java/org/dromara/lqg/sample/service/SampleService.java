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
import org.dromara.lqg.sample.domain.Sample;
import org.dromara.lqg.sample.domain.bo.SampleSubmitBo;
import org.dromara.lqg.sample.domain.bo.SampleSubmitSegmentBo;
import org.dromara.lqg.sample.guard.SampleKindRules;
import org.dromara.lqg.sample.guard.SubmitSegmentRules;
import org.dromara.lqg.sample.guard.SubmitSegmentRules.UnitRef;
import org.dromara.lqg.sample.guard.SubmitSegmentRules.Writer;
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
 * <p>★ FIX V02 / V03：送检段的必填与格式走 {@link SubmitSegmentRules}（违规 {@code code=400}，
 * 不再一路走到数据库约束上报 500 回吐 SQL），送检段的落库列走 {@link SampleSubmitSegmentWriter}
 * —— 与核验抽屉一并保存送检段、外部重提是<b>同一份</b>规则与列。
 *
 * @author SAMPLE-MODEL-001
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SampleService {

    private final SampleMapper sampleMapper;
    private final SampleQueryService sampleQueryService;
    private final SampleSubmitNoGenerator submitNoGenerator;
    private final SampleSubmitSegmentWriter segmentWriter;

    /**
     * 内部新增（{@code POST /lqg/sample}）。
     */
    @Transactional(rollbackFor = Exception.class)
    public Long create(SampleSubmitBo bo) {
        if (bo == null) {
            throw new ServiceException("请求体不能为空", 400);
        }
        String kind = SampleKindRules.normalize(bo.getSampleKind());
        if (!SampleKindRules.isKnownKind(kind)) {
            throw new ServiceException("样本类别只能是 tissue（组织样本）或 organoid（类器官）", 400);
        }
        requireValid(kind, bo);
        requireInternalNoUnique(bo.getInternalNo(), null);
        UnitRef unit = segmentWriter.resolveInternalUnit(bo.getSourceUnitId(), bo.getSourceUnitName());

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
            applyFields(entity, kind, bo, unit);
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
     *
     * <p>★ 语义是<b>整份替换</b>（工作台表单整份提交；小程序的补丁在 {@code MpSampleService.mergePatch}
     * 先合并成整份）。所以必填与格式校验的对象就是<b>这一次真正要写进去的值</b>，不再拿库里现值去凑
     * （FIX V33：以前「合并视角校验、整份写入」—— 只传一个备注时校验照过，写库却把组织类型、
     * 内部编号、收样日期一并写成 NULL）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void update(SampleSubmitBo bo) {
        if (bo == null || bo.getId() == null) {
            throw new ServiceException("缺少样本 id", 400);
        }
        DataPermissionHelper.ignore(() -> {
            Sample exists = sampleMapper.selectById(bo.getId());
            if (exists == null) {
                // 已软删的行 selectById 也查不到（@TableLogic）→ 与「不存在」同样处理
                throw new ServiceException("样本不存在（或已删除）", 404);
            }
            String kind = StringUtils.isNotBlank(bo.getSampleKind())
                ? SampleKindRules.normalize(bo.getSampleKind())
                : exists.getSampleKind();
            if (!SampleKindRules.isKnownKind(kind)) {
                throw new ServiceException("样本类别只能是 tissue（组织样本）或 organoid（类器官）", 400);
            }
            requireValid(kind, bo);
            if (StringUtils.isNotBlank(bo.getInternalNo())) {
                requireInternalNoUnique(bo.getInternalNo(), exists.getId());
            }
            UnitRef unit = segmentWriter.resolveInternalUnit(bo.getSourceUnitId(), bo.getSourceUnitName());
            LambdaUpdateWrapper<Sample> patch = new LambdaUpdateWrapper<Sample>()
                .eq(Sample::getId, exists.getId())
                // 显式补 update_by / update_time：用 `update(null, wrapper)` 时 MyBatis-Plus 的
                // updateFill 拿不到 BaseEntity（参数对象是 wrapper 的参数 Map），不会自动填充 update_by；
                // 而 REQ-SAMPLE-016 / CR-20260917-04 的「最后修改：某某 · 时间」正是读 update_by。
                .set(Sample::getUpdateBy, currentUserId())
                .set(Sample::getUpdateTime, new java.util.Date())
                .set(Sample::getSampleKind, kind)
                .set(Sample::getInternalNo, trimToNull(bo.getInternalNo()))
                .set(Sample::getReceiveDate, bo.getReceiveDate())
                .set(Sample::getIsFixed, trimToNull(bo.getIsFixed()))
                .set(Sample::getProcessTime, bo.getProcessTime())
                .set(Sample::getHasQcSheet, trimToNull(bo.getHasQcSheet()))
                .set(Sample::getHasViabilityReport, trimToNull(bo.getHasViabilityReport()))
                .set(Sample::getOperatorName, trimToNull(bo.getOperatorName()));
            // 送检段：与核验抽屉、外部重提同一组列（SampleSubmitSegmentWriter）
            segmentWriter.applyTo(patch, kind, segmentOf(bo), unit);
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
            throw new ServiceException("缺少样本 id", 400);
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
     * 内部录入 / 修改的必填与格式（全部在写库之前；违规一次报全，{@code code=400}）：
     * 送检段走 {@link SubmitSegmentRules#submitViolations}（与核验抽屉、外部重提同一份），
     * 收样段另要求内部编号与收样日期（内部录的记录直接有效，FLOW:F-SAMPLE-02.step1 / step2）。
     */
    private void requireValid(String kind, SampleSubmitBo bo) {
        List<String> violations = new ArrayList<>(
            SubmitSegmentRules.submitViolations(kind, segmentOf(bo), Writer.INTERNAL));
        violations.addAll(SubmitSegmentRules.receiveRequiredViolations(bo.getInternalNo(), bo.getReceiveDate()));
        violations.addAll(SubmitSegmentRules.receiveViolations(bo.getInternalNo(), bo.getIsFixed(),
            bo.getHasQcSheet(), bo.getHasViabilityReport(), bo.getOperatorName()));
        SubmitSegmentRules.throwIfAny(violations);
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
            throw new ServiceException("内部编号「" + value + "」已存在，请换一个", 400);
        }
    }

    /**
     * 把入参与「单位名称快照」合并进实体（新增路径）：送检段走 {@link SampleSubmitSegmentWriter}，
     * 收样段在这里。
     */
    private void applyFields(Sample entity, String kind, SampleSubmitBo bo, UnitRef unit) {
        segmentWriter.applyTo(entity, kind, segmentOf(bo), unit);
        entity.setReceiveDate(bo.getReceiveDate());
        entity.setInternalNo(SampleKindRules.normalize(bo.getInternalNo()));
        entity.setIsFixed(trimToNull(bo.getIsFixed()));
        entity.setProcessTime(bo.getProcessTime());
        entity.setHasQcSheet(trimToNull(bo.getHasQcSheet()));
        entity.setHasViabilityReport(trimToNull(bo.getHasViabilityReport()));
        entity.setOperatorName(StringUtils.isNotBlank(bo.getOperatorName())
            ? bo.getOperatorName().trim()
            : currentNickname());
    }

    /**
     * 内部入参里的送检段（逐字段手工拷，不用 BeanUtils：哪天两边多出同名字段也不会被静默带上）。
     */
    static SampleSubmitSegmentBo segmentOf(SampleSubmitBo bo) {
        SampleSubmitSegmentBo seg = new SampleSubmitSegmentBo();
        seg.setSourceUnitId(bo.getSourceUnitId());
        seg.setSourceUnitName(bo.getSourceUnitName());
        seg.setSpecies(bo.getSpecies());
        seg.setDonorName(bo.getDonorName());
        seg.setGender(bo.getGender());
        seg.setAge(bo.getAge());
        seg.setHospitalNo(bo.getHospitalNo());
        seg.setTissueType(bo.getTissueType());
        seg.setOrganoidType(bo.getOrganoidType());
        seg.setPassage(bo.getPassage());
        seg.setHasPathology(bo.getHasPathology());
        seg.setRemark(bo.getRemark());
        return seg;
    }

    private String currentNickname() {
        LoginUser loginUser = LoginHelper.getLoginUser();
        return loginUser == null ? null : loginUser.getNickname();
    }

    private static String trimToNull(String value) {
        return StringUtils.isBlank(value) ? null : value.trim();
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
                throw new ServiceException("样本 id 不是数字：" + value, 400);
            }
        }
        return out;
    }

}
