package org.dromara.lqg.sample.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.common.mybatis.helper.DataPermissionHelper;
import org.dromara.lqg.sample.domain.Sample;
import org.dromara.lqg.sample.domain.bo.SampleQueryBo;
import org.dromara.lqg.sample.domain.vo.SampleVo;
import org.dromara.lqg.sample.mapper.SampleMapper;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 样本读侧（doc/api-contract.md 的 {@code GET /lqg/sample/list} / {@code GET /lqg/sample/{id}}）。
 *
 * <p>★ 三条口径都落在这一层：
 * <ol>
 *   <li><b>加密列只支持精确查询</b>（ADR-0006）：{@code donorName} / {@code hospitalNo} 的查询值
 *       先经 {@link SampleFieldCipher#encrypt} 再 {@code eq}。**不做 LIKE** —— 加密列 LIKE 等于
 *       全表解密后在内存里过滤，而且会把别的单位填过的内容联想出去（ticket §2.2 口径 4）；</li>
 *   <li><b>软删行永不返回</b>：软删由实体上的 {@code @TableLogic} 兜住，本类不写任何原生 SQL、
 *       不绕过 MyBatis-Plus —— accept 第 2 条倒数第 2 段断的就是 seed 的 1010（{@code del_flag='1'}）；</li>
 *   <li><b>读出即解密</b>：内部人员要对着全名核样本，VO 里是明文（ADR-0006 的 rejected_value
 *       「内部列表也打码显示」不采纳）。</li>
 * </ol>
 *
 * @author SAMPLE-MODEL-001
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SampleQueryService {

    private final SampleMapper sampleMapper;
    private final SampleFieldCipher fieldCipher;
    private final SampleNameResolver nameResolver;

    /**
     * 样本列表（本票支持五个条件：sampleKind / verifyStatus / internalNo / donorName / hospitalNo）。
     */
    public TableDataInfo<SampleVo> list(SampleQueryBo query) {
        SampleQueryBo q = query == null ? new SampleQueryBo() : query;
        return DataPermissionHelper.ignore(() -> {
            Page<Sample> page = q.build();
            LambdaQueryWrapper<Sample> wrapper = new LambdaQueryWrapper<Sample>()
                .eq(StringUtils.isNotBlank(q.getSampleKind()), Sample::getSampleKind, trim(q.getSampleKind()))
                .eq(StringUtils.isNotBlank(q.getVerifyStatus()), Sample::getVerifyStatus, trim(q.getVerifyStatus()))
                .eq(StringUtils.isNotBlank(q.getInternalNo()), Sample::getInternalNo, trim(q.getInternalNo()))
                // 加密列：先把查询值加密再 eq（明文 eq 查不到任何东西）
                .eq(StringUtils.isNotBlank(q.getDonorName()), Sample::getDonorName, fieldCipher.encrypt(q.getDonorName()))
                .eq(StringUtils.isNotBlank(q.getHospitalNo()), Sample::getHospitalNo, fieldCipher.encrypt(q.getHospitalNo()))
                .orderByDesc(Sample::getCreateTime)
                .orderByDesc(Sample::getId);
            Page<Sample> result = sampleMapper.selectPage(page, wrapper);
            List<SampleVo> rows = result.getRecords().stream().map(this::toVo).toList();
            return TableDataInfo.build(new Page<SampleVo>(result.getCurrent(), result.getSize(), result.getTotal())
                .setRecords(rows));
        });
    }

    /**
     * 按<b>调用方给定的</b> wrapper 分页查样本行 —— 给外部接口（AUTH-EXT-001）用。
     *
     * <p>★ 为什么外部接口不自己注入 {@code SampleMapper}：ADR-0004 的不变量 I4 规定 ext 包里除
     * {@code ExtScopeServiceImpl} 外任何类都不得持有 {@code *Mapper} 字段
     * （{@code ExtChokepointContractTest} 扫整个 ext 包）。所以「按可见 id 集合查一页」这个读操作
     * 放在 sample 包，ext 包只把算好的 wrapper 传进来拼装。
     *
     * <p>调用方负责把可见性条件写进 wrapper（外部那侧是 {@code in(Sample::getId, visibleSampleIds)}）；
     * 本方法只执行查询、不额外加任何过滤 —— 免得两处过滤口径打架。
     *
     * @param page    分页对象（页号 / 页大小 / 排序）
     * @param wrapper 查询条件（含排序）
     * @return 一页实体
     */
    public Page<Sample> selectExtPage(Page<Sample> page, LambdaQueryWrapper<Sample> wrapper) {
        return DataPermissionHelper.ignore(() -> sampleMapper.selectPage(page, wrapper));
    }

    /**
     * 单条详情；不存在或已软删 → null（调用方回 404 语义，不泄露存在性）。
     */
    public SampleVo detail(Long id) {
        if (id == null) {
            return null;
        }
        return DataPermissionHelper.ignore(() -> {
            Sample sample = sampleMapper.selectById(id);
            return sample == null ? null : toVo(sample);
        });
    }

    /**
     * 实体 → VO：解密两个加密列、解析最后修改人。
     */
    public SampleVo toVo(Sample sample) {
        SampleVo vo = new SampleVo();
        vo.setId(sample.getId());
        vo.setSubmitNo(sample.getSubmitNo());
        vo.setSampleKind(sample.getSampleKind());
        vo.setSubmitSource(sample.getSubmitSource());
        vo.setSubmitterId(sample.getSubmitterId());
        vo.setVerifyStatus(sample.getVerifyStatus());
        vo.setVerifyBy(sample.getVerifyBy());
        vo.setVerifyTime(sample.getVerifyTime());
        vo.setInvalidReason(sample.getInvalidReason());
        vo.setSourceUnitId(sample.getSourceUnitId());
        vo.setSourceUnitName(sample.getSourceUnitName());
        vo.setDonorName(fieldCipher.decrypt(sample.getDonorName()));
        vo.setGender(sample.getGender());
        vo.setAge(sample.getAge());
        vo.setHospitalNo(fieldCipher.decrypt(sample.getHospitalNo()));
        vo.setTissueType(sample.getTissueType());
        vo.setOrganoidType(sample.getOrganoidType());
        vo.setHasPathology(sample.getHasPathology());
        vo.setReceiveDate(sample.getReceiveDate());
        vo.setInternalNo(sample.getInternalNo());
        vo.setIsFixed(sample.getIsFixed());
        vo.setProcessTime(sample.getProcessTime());
        vo.setHasQcSheet(sample.getHasQcSheet());
        vo.setHasViabilityReport(sample.getHasViabilityReport());
        vo.setOperatorName(sample.getOperatorName());
        vo.setRemark(sample.getRemark());
        vo.setCreateTime(sample.getCreateTime());
        vo.setUpdateTime(sample.getUpdateTime());
        nameResolver.fill(sample, vo);
        return vo;
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }

}
