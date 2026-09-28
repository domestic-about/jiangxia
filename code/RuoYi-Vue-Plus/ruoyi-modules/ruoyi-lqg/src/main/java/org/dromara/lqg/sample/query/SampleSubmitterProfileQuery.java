package org.dromara.lqg.sample.query;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.mybatis.helper.DataPermissionHelper;
import org.dromara.lqg.sample.domain.vo.SampleVo;
import org.dromara.lqg.sample.query.mapper.SampleSubmitterProfileMapper;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 「提交人的外部档案」读侧（SAMPLE-WEB-001）—— 工作台总表的<b>组别筛选</b>与两个显示列。
 *
 * <p>★ 为什么单独一个类、而不是往 {@code SampleQueryService} 里再塞：
 * <ul>
 *   <li><b>组别不住在样本行上</b>（ticket §0 口径复述 2）：筛选只能「先查档案拿 id 集合，
 *       再 {@code in(submitter_id)}」。把这一段独立出来，样本侧那条读路径（老筛选 + 排序）
 *       一行原生 SQL 都不用沾，{@code @TableLogic} 的不变量继续由实体兜住；</li>
 *   <li>它是**读侧**：只 select，不写库，也不做任何状态判断（核验状态不影响筛选口径）。</li>
 * </ul>
 *
 * <p>★ <b>来源单位不在这里</b>（issue #96 修）：来源单位在样本行上就有 {@code source_unit_id} 快照，
 * 直接按样本行筛；绕外部档案会让内部人员录的行永远筛不出来。所以本类<b>只</b>服务组别与两个显示列。
 *
 * <p>★ <b>档案的核验状态不参与筛选</b>：内部人员按组别找样本，未核验的组也要出
 * （accept 1 的 {@code groupId=9000009101} 期望里含 extE 送来的 1007）。
 *
 * <p>读侧一律包 {@link DataPermissionHelper#ignore}：与 {@code UnitQueryService} 同一条口径 ——
 * 档案表属 AUTH 域，将来给它加 {@code @DataPermission} 时不该把工作台的筛选静默滤空。
 *
 * @author SAMPLE-WEB-001
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SampleSubmitterProfileQuery {

    private final SampleSubmitterProfileMapper profileMapper;

    /**
     * 组别筛选 → 提交人 id 集合。
     *
     * <p>★ <b>只服务组别</b>（issue #96）：来源单位在样本行上就有 {@code source_unit_id} 快照，
     * 工作台那个筛选直接打在样本行上，<b>不再</b>绕提交人的外部档案 —— 否则内部人员录的行
     * （没有外部档案）永远筛不出来。本方法因此不再接 {@code unitId}。
     *
     * @param groupId 组别 id；null = 不按组别筛
     * @return {@code groupId} 为 null 时 <b>null</b>（= 不加这个条件）；否则是 id 集合（可能为空集，
     *         调用方据此回空页而不是退化成全表）
     */
    public List<Long> submitterIds(Long groupId) {
        if (groupId == null) {
            return null;
        }
        return DataPermissionHelper.ignore(() -> profileMapper.selectSubmitterIds(groupId));
    }

    /**
     * 给一页样本行补「提交人姓名 / 组别名」。
     *
     * <p>一次 {@code IN} 批量取档案（不是每行一次查询）；内部人员提交的行在档案表里没有行，
     * 两个字段保持 null。
     *
     * @param rows 当前页的样本行（就地填；null / 空页直接返回）
     */
    public void fill(List<SampleVo> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        Set<Long> submitterIds = new LinkedHashSet<>();
        for (SampleVo row : rows) {
            if (row.getSubmitterId() != null) {
                submitterIds.add(row.getSubmitterId());
            }
        }
        if (submitterIds.isEmpty()) {
            return;
        }
        List<SampleSubmitterProfileVo> profiles = DataPermissionHelper.ignore(
            () -> profileMapper.selectProfiles(submitterIds));
        if (profiles == null || profiles.isEmpty()) {
            return;
        }
        Map<Long, SampleSubmitterProfileVo> byUserId = new HashMap<>();
        for (SampleSubmitterProfileVo profile : profiles) {
            byUserId.put(profile.getUserId(), profile);
        }
        for (SampleVo row : rows) {
            SampleSubmitterProfileVo profile = byUserId.get(row.getSubmitterId());
            if (profile != null) {
                row.setSubmitterName(profile.getSubmitterName());
                row.setGroupId(profile.getGroupId());
                row.setGroupName(profile.getGroupName());
            }
        }
    }

    /**
     * 单条样本的档案（详情用；给 {@code GET /lqg/sample/{id}} 补同样两个键）。
     *
     * @param submitterId 提交人 user_id
     * @return 档案片段；内部人员 / 没有档案时 null
     */
    public SampleSubmitterProfileVo profileOf(Long submitterId) {
        if (submitterId == null) {
            return null;
        }
        List<SampleSubmitterProfileVo> profiles = DataPermissionHelper.ignore(
            () -> profileMapper.selectProfiles(List.of(submitterId)));
        return profiles == null || profiles.isEmpty() ? null : profiles.get(0);
    }

}
