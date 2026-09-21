package org.dromara.lqg.sample.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.helper.DataPermissionHelper;
import org.dromara.lqg.auth.group.domain.SourceUnit;
import org.dromara.lqg.auth.group.service.UnitQueryService;
import org.dromara.lqg.ext.domain.bo.ExtOrganoidSubmitBo;
import org.dromara.lqg.ext.domain.bo.ExtSampleSubmitBo;
import org.dromara.lqg.ext.service.ExtScopeService;
import org.dromara.lqg.sample.domain.Sample;
import org.dromara.lqg.sample.guard.SampleKindRules;
import org.dromara.lqg.sample.mapper.SampleMapper;
import org.dromara.lqg.sample.verify.SampleResubmitBo;
import org.dromara.lqg.sample.verify.SampleVerifyService;
import org.dromara.lqg.sample.verify.VerifyTransitions;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


/**
 * 外部送检的写侧（{@code POST /mp/ext/sample}、{@code POST /mp/ext/organoid}、
 * {@code PUT /mp/ext/sample/{id}}、{@code PUT /mp/ext/organoid/{id}}）。
 *
 * <p>★ <b>为什么住在 sample 包而不是 ext 包</b>：ADR-0004 的不变量 I4 规定 ext 包里除
 * {@code ExtScopeServiceImpl} 外任何类都不得持有 {@code *Mapper} 字段
 * （{@code ExtChokepointContractTest} 扫整个 ext 包）—— AUTH-GROUP-001 已经在这条上栽过一次。
 * ext 包的 controller 只做转发。
 *
 * <p>★ 四条写口径，逐条对着 accept 第 3 条抄：
 * <ol>
 *   <li><b>入参对象是外部专用的两个</b>（{@code ExtSampleSubmitBo} / {@code ExtOrganoidSubmitBo}），
 *       <b>不复用</b>内部 {@code SampleSubmitBo}：夹带的 {@code internalNo / verifyStatus /
 *       submitSource / receiveDate / operatorName / hasViabilityReport} <b>根本没有对应的键</b>
 *       → 反序列化不到任何地方，不是「读到了再丢掉」；</li>
 *   <li><b>服务端写死来源与状态</b>：{@code submit_source='external'}、
 *       {@code verify_status='pending'}、{@code submitter_id=本人}；收样段（收样日期 / 内部编号 /
 *       固定 / 处理时间 / 质控表 / 活率报告 / 操作人）一律不写 → 出库时全空；</li>
 *   <li><b>改的是本人且状态 ∈ {pending, invalid} 的记录</b>（同组的可看不可改、已核验的自己也改不了），
 *       并且路径必须与样本的 {@code sample_kind} 一致（组织样本的 PUT 改不动类器官样本，反之亦然）；
 *       无效的重提后回 {@code pending} 且清 {@code invalid_reason}；</li>
 *   <li><b>被拒时库里一个字都不变</b>：全部校验在任何写操作之前，整段一个事务。</li>
 * </ol>
 *
 * @author AUTH-EXT-001
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExtSampleSubmitService {

    private final SampleMapper sampleMapper;
    private final SampleFieldCipher fieldCipher;
    private final SampleSubmitNoGenerator submitNoGenerator;
    private final SampleQueryService sampleQueryService;
    private final SampleVerifyService sampleVerifyService;
    private final UnitQueryService unitQueryService;
    private final ExtScopeService extScopeService;

    /**
     * 外部提交组织样本（{@code POST /mp/ext/sample}）。
     *
     * @param userId 当前登录的外部人员（controller 从 token 取）
     * @param bo     送检段字段
     * @return 新建样本 id
     */
    @Transactional(rollbackFor = Exception.class)
    public Long submitTissue(Long userId, ExtSampleSubmitBo bo) {
        ExtSampleSubmitBo fields = bo == null ? new ExtSampleSubmitBo() : bo;
        requireLogin(userId);
        if (StringUtils.isBlank(fields.getTissueType())) {
            throw new ServiceException("「组织」类样本缺少必填项：组织类型");
        }
        String unitName = resolveUnitName(fields.getSourceUnitId(), fields.getSourceUnitName());
        return insertExternalTissue(userId, fields, unitName);
    }

    /**
     * 外部提交类器官收样记录（{@code POST /mp/ext/organoid}，CR-20260917-05）。
     *
     * @param userId 当前登录的外部人员
     * @param bo     来源单位 + 类器官类型 + 备注
     * @return 新建样本 id
     */
    @Transactional(rollbackFor = Exception.class)
    public Long submitOrganoid(Long userId, ExtOrganoidSubmitBo bo) {
        ExtOrganoidSubmitBo fields = bo == null ? new ExtOrganoidSubmitBo() : bo;
        requireLogin(userId);
        if (StringUtils.isBlank(fields.getOrganoidType())) {
            throw new ServiceException("「类器官」类样本缺少必填项：类器官类型");
        }
        String unitName = resolveUnitName(fields.getSourceUnitId(), fields.getSourceUnitName());
        return insertExternalOrganoid(userId, fields, unitName);
    }

    /**
     * 外部修改送检段后重提（{@code PUT /mp/ext/sample/{id}} / {@code PUT /mp/ext/organoid/{id}}）。
     *
     * <p>★ 路径与类别必须一致：{@code /mp/ext/sample} 只改得到 {@code tissue}，
     * {@code /mp/ext/organoid} 只改得到 {@code organoid}。accept 第 3 条第 11 段断的就是
     * 「extC 借 {@code /mp/ext/sample} 给类器官样本写进了供体姓名」这个形态 —— 返回 400 且库里不变。
     *
     * <p>★ 校验顺序：范围（不可见 → 404）→ 归属与状态（不是本人的 / 已核验的 → 400）→ 类别
     * （不符 → 400）。全部走完才写库。
     *
     * @param userId         当前登录的外部人员
     * @param sampleId       样本 id
     * @param expectedKind   {@code tissue} 或 {@code organoid}（由端点决定）
     * @param resubmitFields 送检段字段（两种样本共用一个入参：另一类的类型列不生效）
     */
    @Transactional(rollbackFor = Exception.class)
    public void resubmitExternal(Long userId, Long sampleId, String expectedKind, SampleResubmitBo resubmitFields) {
        requireLogin(userId);
        // ① 可见性（不可见按「不存在」回 404，不泄露存在性）
        extScopeService.assertVisible(userId, sampleId);
        // ② 本人 + 状态（同组别人的 / 已核验有效的 → 拒）
        if (!extScopeService.isMine(userId, sampleId)) {
            throw new ServiceException("只能修改重提本人提交的样本（同组的样本可以看，但不能改）", 400);
        }
        if (!extScopeService.editable(userId, sampleId)) {
            throw new ServiceException("样本当前状态不允许外部修改重提（只有待核验、无效的样本可以）", 400);
        }
        // ③ 类别必须与路径一致（组织样本的 PUT 不许改类器官样本，反之亦然）
        Sample sample = DataPermissionHelper.ignore(() -> sampleMapper.selectById(sampleId));
        if (sample == null) {
            throw new ServiceException("样本不存在", 404);
        }
        String actualKind = SampleKindRules.normalize(sample.getSampleKind());
        if (!SampleKindRules.normalize(expectedKind).equals(actualKind)) {
            throw new ServiceException(String.format("这条记录是「%s」类样本，不能从「%s」的入口修改",
                kindName(actualKind), kindName(expectedKind)), 400);
        }
        // ④ 送检段写入 + 无效→待核验（清 invalid_reason）——状态机是唯一判据，本类不另写转移规则
        sampleVerifyService.resubmitByExternal(sampleId, userId, resubmitFields);
        log.info("外部重提：id={} userId={} kind={}", sampleId, userId, actualKind);
    }

    /**
     * 组织样本的修改重提（{@code PUT /mp/ext/sample/{id}}）—— 把外部入参手工换成 sample 包的
     * 「送检段」入参。
     *
     * <p>★ 逐字段手工拷，<b>不</b>用 {@code BeanUtils.copyProperties}：后者按名字拷贝，
     * 哪天有人在 {@code SampleResubmitBo} 上加一个外部不该写的字段（比如 {@code internalNo}），
     * 而外部 BO 恰好同名就会被静默带上 —— 手工拷贝让「外部能写什么」永远是显式的。
     */
    @Transactional(rollbackFor = Exception.class)
    public void resubmitTissue(Long userId, Long sampleId, ExtSampleSubmitBo bo) {
        ExtSampleSubmitBo src = bo == null ? new ExtSampleSubmitBo() : bo;
        SampleResubmitBo fields = new SampleResubmitBo();
        fields.setSourceUnitId(src.getSourceUnitId());
        fields.setSourceUnitName(src.getSourceUnitName());
        fields.setDonorName(src.getDonorName());
        fields.setGender(src.getGender());
        fields.setAge(src.getAge());
        fields.setHospitalNo(src.getHospitalNo());
        fields.setTissueType(src.getTissueType());
        fields.setHasPathology(src.getHasPathology());
        fields.setRemark(src.getRemark());
        resubmitExternal(userId, sampleId, SampleKindRules.KIND_TISSUE, fields);
    }

    /**
     * 类器官收样的修改重提（{@code PUT /mp/ext/organoid/{id}}）。
     *
     * <p>只拷「来源单位 + 类器官类型 + 备注」三项：类器官入参里根本没有别的键。
     */
    @Transactional(rollbackFor = Exception.class)
    public void resubmitOrganoid(Long userId, Long sampleId, ExtOrganoidSubmitBo bo) {
        ExtOrganoidSubmitBo src = bo == null ? new ExtOrganoidSubmitBo() : bo;
        SampleResubmitBo fields = new SampleResubmitBo();
        fields.setSourceUnitId(src.getSourceUnitId());
        fields.setSourceUnitName(src.getSourceUnitName());
        fields.setOrganoidType(src.getOrganoidType());
        fields.setRemark(src.getRemark());
        resubmitExternal(userId, sampleId, SampleKindRules.KIND_ORGANOID, fields);
    }

    // ── 私有 ─────────────────────────────────────────────────────────────────

    private void requireLogin(Long userId) {
        if (userId == null) {
            throw new ServiceException("未登录");
        }
    }

    /**
     * 新建组织样本：{@code sample_kind='tissue'}、{@code submit_source='external'}、
     * {@code verify_status='pending'}、{@code submitter_id=本人}，收样段全空。
     */
    private Long insertExternalTissue(Long userId, ExtSampleSubmitBo fields, String unitName) {
        return DataPermissionHelper.ignore(() -> {
            Sample entity = new Sample();
            entity.setSubmitNo(submitNoGenerator.next());
            entity.setSampleKind(SampleKindRules.KIND_TISSUE);
            entity.setSubmitSource("external");
            entity.setSubmitterId(userId);
            entity.setVerifyStatus(VerifyTransitions.PENDING);
            entity.setSourceUnitId(fields.getSourceUnitId());
            entity.setSourceUnitName(unitName);
            entity.setDonorName(fieldCipher.encrypt(fields.getDonorName()));
            entity.setGender(trimToNull(fields.getGender()));
            entity.setAge(trimToNull(fields.getAge()));
            entity.setHospitalNo(fieldCipher.encrypt(fields.getHospitalNo()));
            entity.setTissueType(trimToNull(fields.getTissueType()));
            entity.setHasPathology(trimToNull(fields.getHasPathology()));
            entity.setRemark(trimToNull(fields.getRemark()));
            sampleMapper.insert(entity);
            log.info("外部提交组织样本：id={} submitNo={} submitter={} 单位={}",
                entity.getId(), entity.getSubmitNo(), userId, unitName);
            return entity.getId();
        });
    }

    /**
     * 新建类器官收样记录：只写「来源单位 / 类器官类型 / 备注」，其余列（含供体姓名、收样段）全空。
     */
    private Long insertExternalOrganoid(Long userId, ExtOrganoidSubmitBo fields, String unitName) {
        return DataPermissionHelper.ignore(() -> {
            Sample entity = new Sample();
            entity.setSubmitNo(submitNoGenerator.next());
            entity.setSampleKind(SampleKindRules.KIND_ORGANOID);
            entity.setSubmitSource("external");
            entity.setSubmitterId(userId);
            entity.setVerifyStatus(VerifyTransitions.PENDING);
            entity.setSourceUnitId(fields.getSourceUnitId());
            entity.setSourceUnitName(unitName);
            entity.setOrganoidType(trimToNull(fields.getOrganoidType()));
            entity.setRemark(trimToNull(fields.getRemark()));
            sampleMapper.insert(entity);
            log.info("外部提交类器官收样：id={} submitNo={} submitter={} 单位={}",
                entity.getId(), entity.getSubmitNo(), userId, unitName);
            return entity.getId();
        });
    }

    /**
     * 来源单位名称快照：选了单位就取单位表的当前名称，否则用请求里的名称（同 SAMPLE-MODEL-001）。
     */
    private String resolveUnitName(Long sourceUnitId, String sourceUnitName) {
        if (sourceUnitId != null) {
            SourceUnit unit = unitQueryService.findUnit(sourceUnitId);
            if (unit == null) {
                throw new ServiceException("来源单位不存在");
            }
            return unit.getUnitName();
        }
        return trimToNull(sourceUnitName);
    }

    private static String kindName(String kind) {
        return SampleKindRules.KIND_ORGANOID.equals(kind) ? "类器官" : "组织";
    }

    private static String trimToNull(String value) {
        return StringUtils.isBlank(value) ? null : value.trim();
    }
}
