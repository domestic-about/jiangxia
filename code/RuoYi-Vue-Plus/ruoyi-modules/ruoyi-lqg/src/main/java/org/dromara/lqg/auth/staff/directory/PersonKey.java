package org.dromara.lqg.auth.staff.directory;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;

/**
 * 「按人筛」的参数值（CR-20261012-22）—— 统计页每一行的 {@code key}，也是三张列表上
 * {@code operatorKey / embedByKey / frozenByKey} 的取值。统计页点数字跳到列表时原样带过去，
 * 所以统计出来的数与列表筛出来的条数必须是<b>同一个口径</b>，两边都只认这里。
 *
 * <ul>
 *   <li>{@code u:<账号 id>} —— 关联了内部人员的记录；</li>
 *   <li>{@code n:<姓名>} —— <b>没关联账号</b>、文字是这个名字的记录（历史手填）；</li>
 *   <li>{@code none} —— 这一栏没填的记录。</li>
 * </ul>
 *
 * @author CR-20261012-22
 */
public final class PersonKey {

    /** 这一栏没填 */
    public static final String NONE = "none";

    private static final String USER_PREFIX = "u:";
    private static final String NAME_PREFIX = "n:";

    private PersonKey() {
    }

    public static String ofUser(Long userId) {
        return USER_PREFIX + userId;
    }

    public static String ofName(String name) {
        return NAME_PREFIX + name;
    }

    /**
     * 库里一行的 {@code (id, 姓名)} → key。
     */
    public static String of(Long userId, String name) {
        if (userId != null) {
            return ofUser(userId);
        }
        return StringUtils.isBlank(name) ? NONE : ofName(name.trim());
    }

    /**
     * 给查询加上「这一栏是这个人」。{@code key} 为空 = 不筛；认不出来的值 → 400（不静默当成不筛）。
     *
     * @param idColumn   账号 id 列
     * @param nameColumn 姓名文字列
     */
    public static <T> LambdaQueryWrapper<T> apply(LambdaQueryWrapper<T> wrapper, SFunction<T, ?> idColumn,
                                                  SFunction<T, ?> nameColumn, String key) {
        String value = StringUtils.isBlank(key) ? null : key.trim();
        if (value == null) {
            return wrapper;
        }
        if (NONE.equals(value)) {
            return wrapper.isNull(idColumn)
                .and(w -> w.isNull(nameColumn).or().eq(nameColumn, ""));
        }
        if (value.startsWith(USER_PREFIX)) {
            try {
                return wrapper.eq(idColumn, Long.valueOf(value.substring(USER_PREFIX.length())));
            } catch (NumberFormatException e) {
                throw new ServiceException("按人筛选的参数不对：" + value, 400);
            }
        }
        if (value.startsWith(NAME_PREFIX) && value.length() > NAME_PREFIX.length()) {
            return wrapper.isNull(idColumn).eq(nameColumn, value.substring(NAME_PREFIX.length()));
        }
        throw new ServiceException("按人筛选的参数不对：" + value, 400);
    }

}
