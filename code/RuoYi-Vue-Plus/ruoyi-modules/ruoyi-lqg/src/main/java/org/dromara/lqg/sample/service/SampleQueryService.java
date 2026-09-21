package org.dromara.lqg.sample.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.common.mybatis.helper.DataPermissionHelper;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.lqg.sample.domain.Sample;
import org.dromara.lqg.sample.domain.bo.SampleQueryBo;
import org.dromara.lqg.sample.domain.vo.SampleVo;
import org.dromara.lqg.sample.mapper.SampleMapper;
import org.dromara.lqg.sample.query.SampleSubmitterProfileQuery;
import org.dromara.lqg.sample.query.SampleSubmitterProfileVo;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 样本读侧（doc/api-contract.md 的 {@code GET /lqg/sample/list} / {@code GET /lqg/sample/{id}}）。
 *
 * <p>★ 四条口径都落在这一层：
 * <ol>
 *   <li><b>加密列只支持精确查询</b>（ADR-0006）：{@code donorName} / {@code hospitalNo} 的查询值
 *       先经 {@link SampleFieldCipher#encrypt} 再 {@code eq}。**不做 LIKE** —— 加密列 LIKE 等于
 *       全表解密后在内存里过滤，而且会把别的单位填过的内容联想出去（ticket §2.2 口径 4）；</li>
 *   <li><b>软删行永不返回</b>：软删由实体上的 {@code @TableLogic} 兜住，本类不写任何原生 SQL、
 *       不绕过 MyBatis-Plus —— accept 第 2 条倒数第 2 段断的就是 seed 的 1010（{@code del_flag='1'}）；</li>
 *   <li><b>读出即解密</b>：内部人员要对着全名核样本，VO 里是明文（ADR-0006 的 rejected_value
 *       「内部列表也打码显示」不采纳）；</li>
 *   <li><b>组别走提交人的外部档案，来源单位走样本行自己的列</b>（SAMPLE-WEB-001；issue #96 修）：
 *       样本行上<b>没有</b> {@code group_id}，所以组别只能先由本包
 *       {@link SampleSubmitterProfileQuery#submitterIds} 把档案查成 id 集合、再 {@code in(submitter_id)}；
 *       而<b>来源单位在样本行上本来就有</b> {@code source_unit_id} 快照，直接 {@code eq} 样本行
 *       —— 两条口径<b>各管各的</b>，混在一起会让 <b>内部人员录的行</b>（没有外部档案）永远筛不出来
 *       （这正是 issue #96 的病灶）。这样<b>样本侧一条原生 SQL 都不用写</b>，
 *       {@code @TableLogic} 那条不变量继续由实体兜住。</li>
 * </ol>
 *
 * @author SAMPLE-MODEL-001（SAMPLE-WEB-001 补五个工作台筛选与「待核验置顶」）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SampleQueryService {

    private final SampleMapper sampleMapper;
    private final SampleFieldCipher fieldCipher;
    private final SampleNameResolver nameResolver;
    private final SampleSubmitterProfileQuery submitterProfileQuery;

    /**
     * 样本列表。
     *
     * <p>筛选：{@code sampleKind / verifyStatus / internalNo / donorName（精确）/ hospitalNo（精确）
     * / sourceUnitId / groupId / submitSource / receiveDateBegin / receiveDateEnd / tissueType（模糊）
     * / operatorName（模糊）}，多条件一律 <b>AND</b>（不是 OR）。
     *
     * <p>★ <b>两个「看起来像同一类」的筛选，口径是分开的</b>（issue #96）：
     * <ul>
     *   <li>{@code sourceUnitId} —— 按<b>样本行自己的 {@code source_unit_id} 快照</b>筛。
     *       内部人员录的行（{@code submit_source='internal'}、提交人没有外部档案）与外部送的行
     *       <b>一视同仁</b>：界面「来源单位」列显示的就是这一列，筛它必须能筛出来。
     *       自填单位名（{@code source_unit_id} 为空、只有 {@code source_unit_name}）的行
     *       <b>不落进按 id 的筛选</b>；同名不同 id 时<b>以 id 为准</b>，按名字找走
     *       {@code keyword} 的 LIKE 那一支。</li>
     *   <li>{@code groupId} —— 样本行上<b>没有</b> {@code group_id} 列，只能走提交人的外部档案
     *       （{@code t_lqg_ext_profile.group_id}）取 id 集合、再 {@code in(submitter_id)}。
     *       内部人员没有外部档案 → 带组别筛时他们录的行不在结果里（他们没有组别，这是对的）。</li>
     * </ul>
     *
     * <p>★ 两个小程序专属参数（SAMPLE-MP-001 / CR-20260918-07，见 {@link SampleQueryBo}）：
     * <ul>
     *   <li>{@code sort=recent} ——「历史编辑记录」的取数口：先把「没人经手过的」挡在外面
     *       （{@code create_by} 与 {@code update_by} 都不是内部账号的行不进这张清单），
     *       再按 {@code COALESCE(update_time, create_time)} 倒序；</li>
     *   <li>{@code mine=true} —— 默认 <b>不是</b>本人，是<b>中心全部内部人员</b>；
     *       带了这个参数才在上面那个范围里再收窄到 {@code create_by = 我 OR update_by = 我}。</li>
     * </ul>
     * 两个参数都不带 = 「内部管理」表格页的全表。
     *
     * <p>★ <b>不带 {@code sort=recent} 时「待核验置顶」</b>（UI:admin.sample.list / accept 1 末段）：
     * {@code ORDER BY (verify_status = 'pending') DESC, create_time DESC, id DESC}
     * —— 布尔表达式在 PostgreSQL 里 {@code true} 排在 {@code false} 前，所以待核验在最上；
     * 其余仍按创建时间倒序。带 {@code sort=recent} 时按最后修改时间倒序（小程序那一档的口径不变）。
     */
    public TableDataInfo<SampleVo> list(SampleQueryBo query) {
        SampleQueryBo q = query == null ? new SampleQueryBo() : query;
        return DataPermissionHelper.ignore(() -> {
            Page<Sample> page = q.build();
            // 组别：样本行上没有 group_id → 先查提交人的外部档案拿 id 集合（来源单位不走这条路，见下）
            List<Long> submitterIds = submitterProfileQuery.submitterIds(q.getGroupId());
            if (submitterIds != null && submitterIds.isEmpty()) {
                // 该组别下没有任何外部档案 → 空集，直接回空页（别退化成「不过滤 = 全表」）
                return TableDataInfo.build(new Page<SampleVo>(page.getCurrent(), page.getSize(), 0L).setRecords(List.of()));
            }
            LambdaQueryWrapper<Sample> wrapper = new LambdaQueryWrapper<Sample>()
                .eq(StringUtils.isNotBlank(q.getSampleKind()), Sample::getSampleKind, trim(q.getSampleKind()))
                .eq(StringUtils.isNotBlank(q.getVerifyStatus()), Sample::getVerifyStatus, trim(q.getVerifyStatus()))
                .eq(StringUtils.isNotBlank(q.getInternalNo()), Sample::getInternalNo, trim(q.getInternalNo()))
                // ★ 提交来源钉在样本行已落库的列上（提交当时的快照），不按提交人当前角色现算
                .eq(StringUtils.isNotBlank(q.getSubmitSource()), Sample::getSubmitSource, trim(q.getSubmitSource()))
                // 加密列：先把查询值加密再 eq（明文 eq 查不到任何东西）
                .eq(StringUtils.isNotBlank(q.getDonorName()), Sample::getDonorName, fieldCipher.encrypt(q.getDonorName()))
                .eq(StringUtils.isNotBlank(q.getHospitalNo()), Sample::getHospitalNo, fieldCipher.encrypt(q.getHospitalNo()))
                // ★ 来源单位钉在**样本行自己的** source_unit_id 快照上（issue #96）：
                //   内部人员录的行（submit_source='internal'、提交人没有外部档案）也要能被这个筛选筛出来
                //   —— 工作台「来源单位」列显示的就是这一列（UI:admin.sample.list / REQ-SAMPLE-011）。
                //   自填单位名（source_unit_id 为空、只有 source_unit_name）的行不落进按 id 的筛选；
                //   同名不同 id 时以 id 为准，按名字找走 keyword 的 LIKE 那一支（见 SampleQueryBo）。
                .eq(q.getSourceUnitId() != null, Sample::getSourceUnitId, q.getSourceUnitId())
                // 组别：样本行上没有这一列 → 外部档案的 id 集合（不看档案核验状态）
                .in(submitterIds != null, Sample::getSubmitterId, submitterIds == null ? List.of() : submitterIds)
                // 收样日期区间：两端都含（begin <= receive_date <= end）
                .ge(q.getReceiveDateBegin() != null, Sample::getReceiveDate, q.getReceiveDateBegin())
                .le(q.getReceiveDateEnd() != null, Sample::getReceiveDate, q.getReceiveDateEnd())
                // 自由文本两项走模糊（不是加密列，没有精确匹配的约束）
                .like(StringUtils.isNotBlank(q.getTissueType()), Sample::getTissueType, trim(q.getTissueType()))
                .like(StringUtils.isNotBlank(q.getOperatorName()), Sample::getOperatorName, trim(q.getOperatorName()))
                // ★ 表格页搜索框（SAMPLE-MP-002 / 契约第 49 行）：内部编号等值 OR 来源单位模糊。
                //   单独一个括号（`w -&gt;` 那层会加括号），不把别的筛选卷进 OR 里。
                .and(StringUtils.isNotBlank(q.getKeyword()), w -> w
                    .eq(Sample::getInternalNo, trim(q.getKeyword()))
                    .or()
                    .like(Sample::getSourceUnitName, trim(q.getKeyword())));

            if (SampleQueryBo.isRecentSort(q.getSort())) {
                // 「经手过」= create_by 或 update_by 是内部账号（sys_user.user_type='sys_user'）。
                // 外部登录建的账号一律是 app_user（AUTH-LOGIN-001），所以外部送来没人动过的
                // （待核验 / 无效）与外部自己改过的都不进「历史编辑记录」。
                String internalUsers = "SELECT user_id FROM sys_user WHERE user_type = 'sys_user' AND del_flag = '0'";
                Long me = currentUserId();
                wrapper.inSql(Sample::getCreateBy, internalUsers)
                    .or()
                    .inSql(Sample::getUpdateBy, internalUsers);
                if (Boolean.TRUE.equals(q.getMine()) && me != null) {
                    // 「只看我提交的」开关打开：在上面那个范围里再按经手人收窄（create_by OR update_by）
                    wrapper.and(w -> w.eq(Sample::getCreateBy, me).or().eq(Sample::getUpdateBy, me));
                }
                // ★ 表达式排序只能走 last()：MyBatis-Plus 3.5.16 的 Func 接口只留了 SFunction 重载
                //（orderByDesc(R, R...)），没有接受列名字符串的重载 —— COALESCE(...) 不是列引用，
                // 编译期就报 no suitable method found。分页插件的 LIMIT 接在这段 ORDER BY 之后。
                wrapper.last("ORDER BY COALESCE(update_time, create_time) DESC, id DESC");
            } else {
                // ★ 待核验置顶（工作台总表）：布尔表达式 true 在前；再按创建时间倒序。
                // 与 accept 1 末段「pageSize=2 的前两行 verifyStatus 都是 pending」同源。
                wrapper.last("ORDER BY (verify_status = 'pending') DESC, create_time DESC, id DESC");
            }

            Page<Sample> result = sampleMapper.selectPage(page, wrapper);
            List<SampleVo> rows = result.getRecords().stream().map(this::toVo).toList();
            // 每行带出提交人姓名 / 组别名（读时 join 外部档案；内部人员与自填单位的行是 null）
            submitterProfileQuery.fill(rows);
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
     *
     * <p>SAMPLE-WEB-001：与列表同形状，补 {@code submitterName / groupId / groupName}
     * （提交人的外部档案；内部人员提交的行是 null）—— 抽屉的「最后修改」「提交人」都读它。
     */
    public SampleVo detail(Long id) {
        if (id == null) {
            return null;
        }
        return DataPermissionHelper.ignore(() -> {
            Sample sample = sampleMapper.selectById(id);
            if (sample == null) {
                return null;
            }
            SampleVo vo = toVo(sample);
            SampleSubmitterProfileVo profile = submitterProfileQuery.profileOf(sample.getSubmitterId());
            if (profile != null) {
                vo.setSubmitterName(profile.getSubmitterName());
                vo.setGroupId(profile.getGroupId());
                vo.setGroupName(profile.getGroupName());
            }
            return vo;
        });
    }

    /**
     * 单条<b>实体</b>（读出即解密）：给「先读现状、再合并补丁」的写路径用
     * （SAMPLE-MP-001 的 {@code PUT /mp/int/sample} 收的是<b>部分字段</b>的补丁，
     * 没传的字段要沿用库里现值，不是整表覆盖）。
     *
     * <p>不存在 / 已软删 → null。软删由 {@code @TableLogic} 自动过滤。
     */
    public Sample entity(Long id) {
        if (id == null) {
            return null;
        }
        return DataPermissionHelper.ignore(() -> {
            Sample sample = sampleMapper.selectById(id);
            if (sample != null) {
                // 与 toVo 同一条口径：实体层面的两个加密列在离开本层前一律解成明文
                sample.setDonorName(fieldCipher.decrypt(sample.getDonorName()));
                sample.setHospitalNo(fieldCipher.decrypt(sample.getHospitalNo()));
            }
            return sample;
        });
    }

    /**
     * 实体 → VO：解密两个加密列、解析经手人（最后修改人，没改过就是创建人）与「是不是我经手的」。
     *
     * <p>★ {@code updateTime} 保持列里的原值：{@code update_by} 为空的行（从没被改过）
     * 在库里 {@code update_time} 也是空 —— 小程序「历史编辑记录」就是靠它空不空显示
     * 「新增 / 修改」（SAMPLE-MP-001 / CR-20260918-07）。本方法不在这里做时间兜底，
     * 兜底只在 {@code SampleNameResolver.fill} 里、且只影响从没改过的行的显示。
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
        // 「经手人」= 最后修改人，没改过就是创建人 —— 与 updateByName 同一个值，
        // 但 handlerName 是契约第 49 行给小程序历史编辑记录定的键名（两个都留着）。
        vo.setHandlerName(vo.getUpdateByName());
        vo.setMine(isHandledBy(sample, currentUserId()));
        vo.setEditable(isEditable(sample));
        return vo;
    }

    /**
     * 这一行在<b>小程序内部接口</b>上能不能改（SAMPLE-MP-001 / CR-20260918-07）：
     * {@code verify_status = 'valid'} 才可改。
     *
     * <p>★ 为什么判据只看状态、<b>不看是谁录的</b>：9-18 起内部人员对中心的任何一条<b>有效</b>
     * 记录都能改（甲方「我们内部人员也有多个哦，江夏实验室所有的工作人员」），加一条
     * 「只能改本人录的」反而会把别人录的有效样本挡掉。外部送来<b>待核验 / 无效</b>的
     * 一律只读 —— 核验是带必填项的状态转移，不能被一次普通保存绕过去。
     *
     * <p>★ 内部录入的记录一律是 {@code valid}（{@code submit_source='internal'} 时
     * {@code verify_status} 直接写死 valid，见 {@code SampleService.create}），所以
     * 这里不需要再叠一个「来源是内部」的条件。
     */
    public static boolean isEditable(Sample sample) {
        return sample != null && "valid".equals(sample.getVerifyStatus());
    }

    /**
     * 这一行是不是这个用户经手的：{@code create_by = 我 OR update_by = 我}
     * —— 与 {@code mine=true} 的收窄口径同一个判据（CR-20260918-07）。
     *
     * <p>取不到当前登录人（匿名 / 定时任务）时返回 {@code false}：不猜、不默认本人。
     */
    public static boolean isHandledBy(Sample sample, Long userId) {
        if (sample == null || userId == null) {
            return false;
        }
        return userId.equals(sample.getCreateBy()) || userId.equals(sample.getUpdateBy());
    }

    /**
     * 当前登录人 id；取不到时 null（{@code LoginHelper} 是读 Sa-Token 上下文，不查库）。
     */
    public static Long currentUserId() {
        try {
            return LoginHelper.getUserId();
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }

}
