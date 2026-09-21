package org.dromara.lqg.sample.service;

import lombok.RequiredArgsConstructor;
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
     *
     * <p>★ <b>只填姓名，不动 {@code updateTime}</b>（SAMPLE-MP-001 改）：
     * 这里曾经在 {@code updateTime} 为空时把它兜底成 {@code createTime}，
     * 但那个兜底把「<b>从没被改过</b>」这个信息抹掉了 —— 小程序「历史编辑记录」正是靠
     * {@code updateTime} 空不空显示「新增 / 修改」（CR-20260918-07），而库里
     * {@code update_by} 为空的行 {@code update_time} 本来就是 NULL。
     * 修完就是：<b>{@code updateTime == null} ⇔ 这一行从没被改过</b>（新增），
     * 非空 ⇔ 被改过（修改）。「最后修改人是谁」仍由本方法给的姓名回答（没改过 = 创建人）。
     */
    public void fill(Sample sample, SampleVo vo) {
        vo.setUpdateByName(resolveLastModifierName(sample));
    }

}
