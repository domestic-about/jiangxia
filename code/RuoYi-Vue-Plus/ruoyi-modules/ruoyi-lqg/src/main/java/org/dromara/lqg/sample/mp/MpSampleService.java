package org.dromara.lqg.sample.mp;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.lqg.auth.group.guard.UnitGroupRules;
import org.dromara.lqg.sample.domain.Sample;
import org.dromara.lqg.sample.domain.bo.PatchBody;
import org.dromara.lqg.sample.domain.bo.SampleSubmitBo;
import org.dromara.lqg.sample.domain.vo.SampleVo;
import org.dromara.lqg.sample.guard.SampleKindRules;
import org.dromara.lqg.sample.service.SampleQueryService;
import org.dromara.lqg.sample.service.SampleService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
 *   <li><b>★ FIX V28：显式空值 = 清空</b>。键<b>没出现</b> = 不改；键出现、值是 {@code null} 或空串 = 清空；
 *       清空必填项（组织类型 / 类器官类型、内部编号、收样日期、来源单位）→ 400 并写明是哪一项
 *       （由 {@code SampleService.update} 的同一份校验报出），<b>不再假装保存成功</b>。
 *       以前是「空值 = 不改」：用户清空备注点保存，提示「已保存」、库里还是旧值。
 *       给本类别<b>没有</b>的字段传了值（例如给类器官样本传供体姓名）→ 400，同样不假装成功。</li>
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
    public void update(PatchBody<SampleSubmitBo> patch) {
        SampleSubmitBo bo = patch == null ? null : patch.value();
        if (bo == null || bo.getId() == null) {
            throw new ServiceException("缺少样本 id", 400);
        }
        Sample exists = sampleQueryService.entity(bo.getId());
        if (exists == null) {
            throw new ServiceException("样本不存在", 404);
        }
        if (!SampleQueryService.isEditable(exists)) {
            throw new ServiceException("待核验与无效的样本只能在网页工作台核验或改判，小程序里不能改", 400);
        }
        assertKindUnchanged(exists, bo);
        assertNoForeignFields(exists, patch);
        sampleService.update(mergePatch(exists, patch));
        log.info("小程序内部修改样本：id={} 改动键={}", bo.getId(), patch.keys());
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
     * 本类别<b>没有</b>的字段 → 键名（给错误提示用）。组织样本没有类器官类型与代数（代数是
     * CR-20260924-10 给类器官收样记录加的）；类器官收样记录只有来源单位、类器官类型、代数与收样段
     * （REQ-SAMPLE-007），没有供体姓名等组织样本字段。
     */
    private static final Map<String, String> TISSUE_ONLY = new LinkedHashMap<>();
    private static final Map<String, String> ORGANOID_ONLY = new LinkedHashMap<>();

    static {
        TISSUE_ONLY.put("donorName", "供体姓名");
        TISSUE_ONLY.put("gender", "性别");
        TISSUE_ONLY.put("age", "年龄");
        TISSUE_ONLY.put("hospitalNo", "住院号");
        TISSUE_ONLY.put("tissueType", "组织类型");
        TISSUE_ONLY.put("hasPathology", "有无病理");
        ORGANOID_ONLY.put("organoidType", "类器官类型");
        ORGANOID_ONLY.put("passage", "代数");
    }

    /**
     * 给本类别没有的字段传了<b>非空</b>值 → 400（FIX V28：以前被静默忽略、照样提示已保存）。
     * 传空值不算（前端整份表单带着空的另一类字段是常见形状，本来就是空的，不必报错）。
     */
    static void assertNoForeignFields(Sample exists, PatchBody<SampleSubmitBo> patch) {
        boolean organoid = SampleKindRules.KIND_ORGANOID.equals(SampleKindRules.normalize(exists.getSampleKind()));
        Map<String, String> foreign = organoid ? TISSUE_ONLY : ORGANOID_ONLY;
        SampleSubmitBo p = patch.value();
        List<String> hit = new ArrayList<>();
        for (Map.Entry<String, String> e : foreign.entrySet()) {
            if (patch.has(e.getKey()) && !isBlank(valueOf(p, e.getKey()))) {
                hit.add(e.getValue());
            }
        }
        if (!hit.isEmpty()) {
            throw new ServiceException((organoid ? "类器官送样记录" : "组织样本") + "没有这些字段，不能修改："
                + String.join("、", hit), 400);
        }
    }

    /**
     * 把「补丁」合并成 {@link SampleService#update} 要的完整入参（FIX V28 的语义）：
     * <ul>
     *   <li>键<b>没出现</b> → 沿用库里现值；</li>
     *   <li>键出现、值为 {@code null} / 空串 → <b>清空</b>（写 NULL）；清的是必填项时由
     *       {@code SampleService.update} 的同一份校验报 400「××不能为空」，不会静默成功；</li>
     *   <li>键出现、有值 → 用它。</li>
     * </ul>
     *
     * <p>★ 来源单位的 id 与名称是一对（这条路上「单位名」与「单位 id」必须同源）：
     * <ul>
     *   <li>带了非空 {@code sourceUnitId} → 以它为准（名称快照由 {@code SampleService} 从单位表取）；</li>
     *   <li>只带名称：与现有名称同名（去空白、大小写不敏感）→ 连 id 一起沿用；改成别的名字 → 自填单位名（id 置空）；
     *       名称为空 → 清空来源单位（必填，{@code SampleService} 报 400）；</li>
     *   <li>只带 {@code sourceUnitId: null} → 解除与单位表的关联，名称沿用；</li>
     *   <li>都没带 → 都沿用。</li>
     * </ul>
     * 以前「没带 id 就连名称一起沿用」—— 小程序组织样本表单只发名称，于是改了单位名也不生效（V28 同一病灶）。
     *
     * <p>合并基准用 {@link SampleQueryService#entity} 读出来的实体 —— 它在 sample 包内
     * 已完成两个加密列的解密，所以这里拿到的是明文（再经 service 层加密落库，不会二次加密）。
     *
     * <p>刻意<b>不搬</b> {@code submitNo / submitSource / submitterId / verifyBy / verifyTime /
     * verifyStatus / createBy / createTime / updateBy / updateTime / delFlag}：前三个是契约写死的
     * 「不可改」，状态与核验人是核验路径的地盘，审计时间戳由写路径自己填
     * （{@code SampleSubmitBo} 里也根本没有这些字段）。{@code sampleKind} 是类目身份，永远取库里的。
     */
    static SampleSubmitBo mergePatch(Sample exists, PatchBody<SampleSubmitBo> patch) {
        SampleSubmitBo p = patch.value();
        SampleSubmitBo merged = new SampleSubmitBo();
        merged.setId(exists.getId());
        merged.setSampleKind(exists.getSampleKind());
        mergeUnit(exists, patch, merged);
        merged.setSpecies(pick(patch, "species", p.getSpecies(), exists.getSpecies()));
        merged.setDonorName(pick(patch, "donorName", p.getDonorName(), exists.getDonorName()));
        merged.setGender(pick(patch, "gender", p.getGender(), exists.getGender()));
        merged.setAge(pick(patch, "age", p.getAge(), exists.getAge()));
        merged.setHospitalNo(pick(patch, "hospitalNo", p.getHospitalNo(), exists.getHospitalNo()));
        merged.setTissueType(pick(patch, "tissueType", p.getTissueType(), exists.getTissueType()));
        merged.setOrganoidType(pick(patch, "organoidType", p.getOrganoidType(), exists.getOrganoidType()));
        merged.setPassage(pick(patch, "passage", p.getPassage(), exists.getPassage()));
        merged.setHasPathology(pick(patch, "hasPathology", p.getHasPathology(), exists.getHasPathology()));
        merged.setReceiveDate(patch.has("receiveDate") ? p.getReceiveDate() : exists.getReceiveDate());
        merged.setInternalNo(pick(patch, "internalNo", p.getInternalNo(), exists.getInternalNo()));
        merged.setIsFixed(pick(patch, "isFixed", p.getIsFixed(), exists.getIsFixed()));
        merged.setProcessTime(patch.has("processTime") ? p.getProcessTime() : exists.getProcessTime());
        merged.setHasQcSheet(pick(patch, "hasQcSheet", p.getHasQcSheet(), exists.getHasQcSheet()));
        merged.setHasViabilityReport(pick(patch, "hasViabilityReport", p.getHasViabilityReport(),
            exists.getHasViabilityReport()));
        merged.setOperatorName(pick(patch, "operatorName", p.getOperatorName(), exists.getOperatorName()));
        merged.setRemark(pick(patch, "remark", p.getRemark(), exists.getRemark()));
        return merged;
    }

    private static void mergeUnit(Sample exists, PatchBody<SampleSubmitBo> patch, SampleSubmitBo merged) {
        SampleSubmitBo p = patch.value();
        if (patch.has("sourceUnitId") && p.getSourceUnitId() != null) {
            merged.setSourceUnitId(p.getSourceUnitId());
            merged.setSourceUnitName(p.getSourceUnitName());
            return;
        }
        if (patch.has("sourceUnitName")) {
            String name = blankToNull(p.getSourceUnitName());
            if (name == null) {
                // 清空来源单位：必填项，SampleService 的同一份校验报 400「来源单位不能为空」
                merged.setSourceUnitId(null);
                merged.setSourceUnitName(null);
            } else if (exists.getSourceUnitId() != null && UnitGroupRules.sameName(name, exists.getSourceUnitName())) {
                // 名字没改：连单位 id 一起沿用（别因为前端只发名称就把单位关联弄丢）
                merged.setSourceUnitId(exists.getSourceUnitId());
                merged.setSourceUnitName(exists.getSourceUnitName());
            } else {
                merged.setSourceUnitId(null);
                merged.setSourceUnitName(name);
            }
            return;
        }
        if (patch.has("sourceUnitId")) {
            // 显式 sourceUnitId:null、没给名称：解除与单位表的关联，名称沿用
            merged.setSourceUnitId(null);
            merged.setSourceUnitName(exists.getSourceUnitName());
            return;
        }
        merged.setSourceUnitId(exists.getSourceUnitId());
        merged.setSourceUnitName(exists.getSourceUnitName());
    }

    /** 键出现 → 用请求里的值（空白 = 清空）；没出现 → 沿用现值。 */
    private static String pick(PatchBody<?> patch, String key, String incoming, String current) {
        return patch.has(key) ? blankToNull(incoming) : current;
    }

    private static String valueOf(SampleSubmitBo p, String key) {
        return switch (key) {
            case "donorName" -> p.getDonorName();
            case "gender" -> p.getGender();
            case "age" -> p.getAge();
            case "hospitalNo" -> p.getHospitalNo();
            case "tissueType" -> p.getTissueType();
            case "hasPathology" -> p.getHasPathology();
            case "organoidType" -> p.getOrganoidType();
            case "passage" -> p.getPassage();
            default -> null;
        };
    }

    private static String blankToNull(String value) {
        return isBlank(value) ? null : value.trim();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /** 与 {@code SampleQueryService.trim} 同口径：查询 / 补丁值一律 trim 后比较 */
    private static String trim(String value) {
        return value == null ? null : value.trim();
    }

}
