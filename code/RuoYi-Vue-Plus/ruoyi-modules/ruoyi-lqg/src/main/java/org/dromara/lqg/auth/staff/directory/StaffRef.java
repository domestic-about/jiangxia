package org.dromara.lqg.auth.staff.directory;

import org.dromara.common.core.utils.StringUtils;

/**
 * 一栏人名（操作人 / 包埋人 / 冻存人）落库的那一对值（CR-20261012-22）：账号 id + 姓名快照。
 *
 * <p>三种形态：
 * <ul>
 *   <li>{@code id} 有值 —— 从内部人员名单里选的；{@code name} 是选的那一刻的姓名；</li>
 *   <li>{@code id} 为空、{@code name} 有值 —— 历史手填的文字，或旧版客户端传来的、对不上账号的名字；</li>
 *   <li>都为空 —— 没填。</li>
 * </ul>
 *
 * @param id   内部人员账号 id
 * @param name 姓名（已去首尾空白；空白 = null）
 * @author CR-20261012-22
 */
public record StaffRef(Long id, String name) {

    /** 没填 */
    public static final StaffRef EMPTY = new StaffRef(null, null);

    public StaffRef {
        name = StringUtils.isBlank(name) ? null : name.trim();
    }

    public static StaffRef of(Long id, String name) {
        return id == null && StringUtils.isBlank(name) ? EMPTY : new StaffRef(id, name);
    }

}
