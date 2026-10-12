package org.dromara.lqg.auth.staff.directory;

import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.model.LoginUser;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.helper.DataPermissionHelper;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.lqg.auth.staff.domain.vo.StaffOptionVo;
import org.dromara.lqg.auth.staff.mapper.StaffAccountMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 内部人员名单（CR-20261012-22）：四张表上「操作人 / 包埋人 / 冻存人」从这里选，统计页也按它认人。
 *
 * <p>名单 = 带内部角色（实验室管理员 / 内部人员）、账号未停用的人，每次现查（人少，不缓存 ——
 * 管理员刚在「内部人员授权」里加的人要立刻选得到）。
 *
 * <p>★ SQL 都在 {@code DataPermissionHelper.ignore} 里跑：{@code sys_user} 带数据权限，
 * 普通内部人员的上下文里不 ignore 会被滤成只剩自己。
 *
 * @author CR-20261012-22
 */
@Service
@RequiredArgsConstructor
public class StaffDirectoryService {

    private final StaffAccountMapper staffAccountMapper;

    /**
     * 名单（按姓名排序，同一个账号只出一次）。
     */
    public List<StaffOptionVo> options() {
        Map<Long, StaffOptionVo> byUser = new LinkedHashMap<>();
        for (StaffOptionVo row : DataPermissionHelper.ignore(staffAccountMapper::selectDirectory)) {
            if (row.getUserId() != null && StringUtils.isNotBlank(row.getName())) {
                byUser.putIfAbsent(row.getUserId(), row);
            }
        }
        return new ArrayList<>(byUser.values());
    }

    /**
     * 请求里的一栏人名最后落成什么（规则见 {@link StaffRefRules#resolve}）。写库之前调用。
     *
     * @param label    字段中文名（报错用：操作人 / 包埋人 / 冻存人）
     * @param id       请求里的账号 id
     * @param name     请求里的姓名文字
     * @param existing 库里现在的值（新增 = null）
     */
    public StaffRef resolve(String label, Long id, String name, StaffRef existing) {
        if (id == null && StringUtils.isBlank(name)) {
            return StaffRef.EMPTY;
        }
        Map<Long, String> names = directory();
        StaffRefRules.Outcome outcome = StaffRefRules.resolve(id, name, existing, names, StaffRefRules.uniqueNames(names));
        if (outcome.rejected()) {
            throw new ServiceException("所选的" + label + "不在内部人员名单里，请重新选择", 400);
        }
        return outcome.ref();
    }

    /**
     * 新增时没填这一栏 → 默认当前登录人（在名单里就带上账号 id；不在名单里的，如上游超管，只落姓名）。
     */
    public StaffRef currentUser() {
        LoginUser loginUser;
        try {
            loginUser = LoginHelper.getLoginUser();
        } catch (Exception ignored) {
            // 没有登录上下文（后台任务 / 单测）：这一栏就空着
            return StaffRef.EMPTY;
        }
        if (loginUser == null) {
            return StaffRef.EMPTY;
        }
        String staffName = directory().get(loginUser.getUserId());
        return staffName != null
            ? StaffRef.of(loginUser.getUserId(), staffName)
            : StaffRef.of(null, loginUser.getNickname());
    }

    /**
     * 按账号 id 取现在的姓名（不看角色：撤销授权的人也查得到）。统计页显示用。
     */
    public Map<Long, String> namesOf(Collection<Long> ids) {
        Map<Long, String> out = new LinkedHashMap<>();
        List<Long> wanted = ids == null ? List.of() : ids.stream().filter(Objects::nonNull).distinct().toList();
        if (wanted.isEmpty()) {
            return out;
        }
        for (StaffOptionVo row : DataPermissionHelper.ignore(() -> staffAccountMapper.selectNamesByIds(wanted))) {
            if (StringUtils.isNotBlank(row.getName())) {
                out.put(row.getUserId(), row.getName().trim());
            }
        }
        return out;
    }

    private Map<Long, String> directory() {
        Map<Long, String> names = new LinkedHashMap<>();
        for (StaffOptionVo row : options()) {
            names.put(row.getUserId(), row.getName().trim());
        }
        return names;
    }

}
