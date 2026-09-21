package org.dromara.lqg.sample.mp;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.lqg.sample.domain.Sample;
import org.dromara.lqg.sample.domain.bo.SampleSubmitBo;
import org.dromara.lqg.sample.domain.vo.SampleVo;
import org.dromara.lqg.sample.service.SampleQueryService;
import org.dromara.lqg.sample.service.SampleService;
import org.springframework.stereotype.Service;

/**
 * 小程序<b>内部人员</b>侧「样本记录信息表」的读写
 * （doc/api-contract.md 第 49 行：{@code GET /mp/int/sample/list}、{@code GET /mp/int/sample/{id}}、
 * {@code POST /mp/int/sample}、{@code PUT /mp/int/sample}）。
 *
 * <p>★ 本类<b>不自己查库、不自己写库</b>：读走 {@link SampleQueryService}（同一份筛选与
 * 「经手人 / 是不是我经手的」判据），写走 {@link SampleService}（同一套必填、唯一性、
 * {@code update_by} 显式回填）。小程序与工作台对同一张表说话时用的是同一条读写路径 ——
 * 两条路径各写一套判据迟早会漂移。
 *
 * <p>★ 四条口径（ticket §0 的复述 3 / 4 与 CR-20260918-07）：
 * <ol>
 *   <li><b>有效样本谁录的都能改</b>：不校验「是不是本人录的」。内部人员对中心任何一条
 *       {@code verify_status='valid'} 的记录都能改，改完记下 {@code update_by}（「经手人」的来源）。</li>
 *   <li><b>待核验 / 无效只读</b>：{@code PUT} 直接 400。核验是带必填项的状态转移
 *       （{@code PUT /lqg/sample/{id}/verify}），不能被一次普通保存绕过去 —— 否则核验人看到的
 *       已经不是外部提交的原样。前端另外会按详情里的 {@code editable} 渲染成只读，
 *       但**挡在服务端这一层**才是真的挡。</li>
 *   <li><b>{@code POST} 走内部新增</b>：{@code submit_source='internal'}、
 *       {@code verify_status='valid'}、提交人 = 核验人 = 当前用户（与工作台新增同一条路）。</li>
 *   <li><b>{@code PUT} 收的是补丁</b>（见 {@link #mergePatch}）：票面与 accept 的请求体只有
 *       {@code {"id":…,"tissueType":…}} 这种<b>部分字段</b>，没传的字段要沿用库里现值，
 *       不能整表置空 —— 否则「小程序改一个字段顺手清掉住院号」是最容易出的数据事故。</li>
 * </ol>
 *
 * <p>★ 详情带上 {@code updateByName} / {@code updateTime} / {@code handlerName} / {@code mine} /
 * {@code editable}：修改模式顶部要显示「最后修改：某某 · 时间」，可写性以后端这个 {@code editable}
 * 为准（前端不自己按状态重新判断）。
 *
 * @author SAMPLE-MP-001
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MpSampleService {

    private final SampleQueryService sampleQueryService;
    private final SampleService sampleService;

    /**
     * 列表。{@code sort=recent} 与 {@code mine=true} 的语义全在
     * {@link SampleQueryService#list} 与 {@link MpSampleQueryBo}。
     */
    public TableDataInfo<SampleVo> list(MpSampleQueryBo query) {
        return sampleQueryService.list(query);
    }

    /**
     * 详情。不存在 / 已软删 → 业务码 404（不泄露存在性，与工作台那条一致）。
     */
    public SampleVo detail(Long id) {
        if (id == null) {
            throw new ServiceException("缺少样本 id");
        }
        SampleVo vo = sampleQueryService.detail(id);
        if (vo == null) {
            throw new ServiceException("样本不存在", 404);
        }
        return vo;
    }

    /**
     * 新增（首页点「样本记录信息表」进来，内外部一样都是新增一条；内部这条直接有效）。
     */
    public Long create(SampleSubmitBo bo) {
        return sampleService.create(bo);
    }

    /**
     * 修改（「历史编辑记录」点一条进来，或「内部管理」只读页右上角切修改模式）。
     *
     * <p>★ 先读现状（同时拿到状态与待合并的字段），待核验 / 无效 → 400（见类注释口径 2）。
     * 把补丁合并成完整入参之后再交给 {@link SampleService#update}：那边是「整体替换」语义
     * （工作台的修改页会带全字段），所以补齐这件事必须在这里做。
     *
     * <p>★ <b>类目身份不可越类改</b>（issue #105 的第二半，防再次发生）：补丁里的
     * {@code sampleKind} 与库里不一致 → 400，见 {@link #assertKindUnchanged}。
     */
    public void update(SampleSubmitBo bo) {
        if (bo == null || bo.getId() == null) {
            throw new ServiceException("缺少样本 id");
        }
        Sample exists = sampleQueryService.entity(bo.getId());
        if (exists == null) {
            throw new ServiceException("样本不存在", 404);
        }
        if (!SampleQueryService.isEditable(exists)) {
            throw new ServiceException("待核验与无效的样本只能在网页工作台核验或改判，小程序里不能改");
        }
        assertKindUnchanged(exists, bo);
        sampleService.update(mergePatch(exists, bo));
        log.info("小程序内部修改样本：id={} internalNo={}", bo.getId(), bo.getInternalNo());
    }

    /**
     * <b>样本类别（{@code sample_kind}）是这条记录的类目身份，不是可改字段</b>（issue #105）。
     *
     * <p>口径来源：
     * <ul>
     *   <li>{@code doc/authority/} 的 {@code FIELD:t_lqg_sample.sample_kind}
     *       —— 「tissue 组织样本（样本记录信息表）/ organoid 类器官（类器官收样记录）」：
     *       它决定这条记录属于哪张工作表、哪套必填集（{@code SampleKindRules}），
     *       是<b>创建当时</b>由入口定下的身份
     *       （{@code FLOW:F-SAMPLE-01.step1} / {@code FLOW:F-SAMPLE-02.step1|step2}
     *       把 {@code sample_kind} 写在 {@code writes} 里，修改路径的 {@code writes} 里没有它）；</li>
     *   <li>{@code doc/api-contract.md} 第 49 行：{@code PUT} 的不可改字段原先只列了
     *       {@code submitNo / submitSource / submitterId}，本票把 {@code sampleKind} 一并列进去
     *       （同一条 issue #105 的返工，报告里有记录）。</li>
     * </ul>
     *
     * <p>★ 为什么选「拒绝（400）」而不是「静默忽略」：这条路径的病灶正是
     * <b>点错行 + 前端把 {@code sampleKind} 当可改字段发出去</b>（小程序类器官表单保存时固定发
     * {@code updateIntSample({sampleKind:'organoid'})}），一条组织样本保存后就被静默改判成类器官
     * —— 静默忽略只会把「改判」换成「字段写进了别类记录」，两种都不可见。拒绝会让前端与 QA
     * 当场看到 400，而不是在库里留下一条类别错乱的记录。
     *
     * <p>不传 / 传空 = 沿用库里现值（{@link #mergePatch} 的补丁语义），不算不一致。
     */
    static void assertKindUnchanged(Sample exists, SampleSubmitBo patch) {
        String requested = patch == null ? null : trim(patch.getSampleKind());
        if (isBlank(requested)) {
            return;
        }
        String current = trim(exists.getSampleKind());
        if (!requested.equals(current)) {
            throw new ServiceException(
                "样本类别不可修改（当前 " + current + "，请求 " + requested + "）：类别是这条记录的身份，"
                    + "要换类别请在工作台按对应工作表新增", 400);
        }
    }

    /**
     * 把「部分字段的补丁」合并成 {@link SampleService#update} 要的完整入参：
     * <b>没传的字段沿用库里现值</b>，传了就用传的（不是「传空串 = 清空」：契约与票面都没有
     * 「小程序清字段」这条口径，而误清一个字段的代价远大于清不掉一个字段）。
     *
     * <p>合并基准用 {@link SampleQueryService#entity} 读出来的实体 —— 它在 sample 包内
     * 已完成两个加密列的解密，所以这里拿到的是明文（再经 service 层加密落库，不会二次加密）。
     *
     * <p>刻意<b>不搬</b> {@code submitNo / submitSource / submitterId / verifyBy / verifyTime /
     * verifyStatus / createBy / createTime / updateBy / updateTime / delFlag}：前三个是契约写死的
     * 「不可改」，状态与核验人是核验路径的地盘，审计时间戳由写路径自己填
     * （{@code SampleSubmitBo} 里也根本没有这些字段）。
     */
    static SampleSubmitBo mergePatch(Sample exists, SampleSubmitBo patch) {
        SampleSubmitBo merged = new SampleSubmitBo();
        merged.setId(exists.getId());
        merged.setSampleKind(isBlank(patch.getSampleKind()) ? exists.getSampleKind() : patch.getSampleKind());
        if (patch.getSourceUnitId() == null) {
            // 没换单位：连名称快照一起沿用（这条路上「单位名」与「单位 id」必须同源）
            merged.setSourceUnitId(exists.getSourceUnitId());
            merged.setSourceUnitName(exists.getSourceUnitName());
        } else {
            merged.setSourceUnitId(patch.getSourceUnitId());
            merged.setSourceUnitName(patch.getSourceUnitName());
        }
        merged.setDonorName(isBlank(patch.getDonorName()) ? exists.getDonorName() : patch.getDonorName());
        merged.setGender(isBlank(patch.getGender()) ? exists.getGender() : patch.getGender());
        merged.setAge(isBlank(patch.getAge()) ? exists.getAge() : patch.getAge());
        merged.setHospitalNo(isBlank(patch.getHospitalNo()) ? exists.getHospitalNo() : patch.getHospitalNo());
        merged.setTissueType(isBlank(patch.getTissueType()) ? exists.getTissueType() : patch.getTissueType());
        merged.setOrganoidType(isBlank(patch.getOrganoidType()) ? exists.getOrganoidType() : patch.getOrganoidType());
        merged.setHasPathology(isBlank(patch.getHasPathology()) ? exists.getHasPathology() : patch.getHasPathology());
        merged.setReceiveDate(patch.getReceiveDate() != null ? patch.getReceiveDate() : exists.getReceiveDate());
        merged.setInternalNo(isBlank(patch.getInternalNo()) ? exists.getInternalNo() : patch.getInternalNo());
        merged.setIsFixed(isBlank(patch.getIsFixed()) ? exists.getIsFixed() : patch.getIsFixed());
        merged.setProcessTime(patch.getProcessTime() != null ? patch.getProcessTime() : exists.getProcessTime());
        merged.setHasQcSheet(isBlank(patch.getHasQcSheet()) ? exists.getHasQcSheet() : patch.getHasQcSheet());
        merged.setHasViabilityReport(isBlank(patch.getHasViabilityReport())
            ? exists.getHasViabilityReport() : patch.getHasViabilityReport());
        merged.setOperatorName(isBlank(patch.getOperatorName()) ? exists.getOperatorName() : patch.getOperatorName());
        merged.setRemark(isBlank(patch.getRemark()) ? exists.getRemark() : patch.getRemark());
        return merged;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /** 与 {@code SampleQueryService.trim} 同口径：查询 / 补丁值一律 trim 后比较 */
    private static String trim(String value) {
        return value == null ? null : value.trim();
    }

}
