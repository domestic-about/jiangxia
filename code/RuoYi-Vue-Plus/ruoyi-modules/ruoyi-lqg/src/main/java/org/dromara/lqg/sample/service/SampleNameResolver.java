package org.dromara.lqg.sample.service;

import lombok.RequiredArgsConstructor;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.helper.DataPermissionHelper;
import org.dromara.lqg.sample.domain.Sample;
import org.dromara.lqg.sample.domain.vo.SampleVo;
import org.dromara.system.domain.vo.SysUserVo;
import org.dromara.system.mapper.SysUserMapper;
import org.springframework.stereotype.Service;

/**
 * 样本行的「最后修改人姓名」解析（REQ-SAMPLE-016 / CR-20260917-04）。
 *
 * <p>修改页显示「最后修改：某某 · 时间」；{@code update_by} 只有 id，姓名要回 {@code sys_user} 取昵称。
 * 与 AUTH-LOGIN-001 的坑 1 同源：读 {@code sys_user} 一律包 {@link DataPermissionHelper#ignore}，
 * 否则部门数据范围会把行滤掉 —— 现象是接口 200 但 {@code updateByName} 恒 null。
 *
 * @author SAMPLE-MODEL-001
 */
@Service
@RequiredArgsConstructor
public class SampleNameResolver {

    private final SysUserMapper sysUserMapper;

    /**
     * {@code update_by}（没有取 {@code create_by}）对应的账号昵称。
     *
     * @param sample 样本行
     * @return 姓名；没有可解析的人 → null
     */
    public String resolveLastModifierName(Sample sample) {
        Long userId = sample == null ? null : sample.getUpdateBy();
        if (userId == null) {
            userId = sample == null ? null : sample.getCreateBy();
        }
        return nameOf(userId);
    }

    /**
     * user_id → 昵称。
     */
    public String nameOf(Long userId) {
        if (userId == null) {
            return null;
        }
        return DataPermissionHelper.ignore(() -> {
            SysUserVo user = sysUserMapper.selectVoById(userId);
            return user == null ? null : user.getNickName();
        });
    }

    /**
     * 把「最后修改人姓名」填进行 VO（详情与列表共用）。
     */
    public void fill(Sample sample, SampleVo vo) {
        vo.setUpdateByName(resolveLastModifierName(sample));
        if (vo.getUpdateTime() == null && !StringUtils.isBlank(vo.getUpdateByName())) {
            // updateTime 列由 BaseEntity 的 INSERT_UPDATE 填充，正常情况下非空；这里只保证
            // 「显示最后修改」这件事在从没改过的行上也有个时间可显示（回落到创建时间）。
            vo.setUpdateTime(sample.getCreateTime());
        }
    }

}
