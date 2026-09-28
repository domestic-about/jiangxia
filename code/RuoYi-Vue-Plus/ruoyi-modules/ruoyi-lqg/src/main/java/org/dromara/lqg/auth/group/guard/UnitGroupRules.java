package org.dromara.lqg.auth.group.guard;

/**
 * 单位 / 组别维护的**纯规则**（无 Spring、无库，可被契约测试直接钉）。
 *
 * <p>为什么单独一个类：accept 里「同单位内组别名唯一、不同单位可重名」「单位名全库唯一」
 * 这两条既要 service 层给人话报错、又要唯一索引兜底，判据本身不该散在 SQL 里。
 * 真正的查重在 service 做（要读库），这里只放「什么算同一个名字」「什么状态是合法的」
 * 这类**不依赖库**的判断，好让契约测试在没有数据库的构建里也能跑。
 *
 * @author AUTH-GROUP-001
 */
public final class UnitGroupRules {

    /**
     * 启用
     */
    public static final String STATUS_ACTIVE = "active";

    /**
     * 待核验（外部自填，落库未核验）
     */
    public static final String STATUS_PENDING = "pending";

    /**
     * 停用
     */
    public static final String STATUS_DISABLED = "disabled";

    private UnitGroupRules() {
    }

    /**
     * 名字归一化后比较：去首尾空白、把连续空白折成一个空格、大小写不敏感。
     *
     * <p>唯一索引是**区分大小写**的（PG 的 text 比较），所以库里真可能出现「A 医院」与「a 医院」
     * 两个单位；service 层按这个归一化结果提前拦一道，把「看起来一样的名字」挡在写库之前
     * —— 人话报错比唯一索引的 23505 好懂得多。
     *
     * @param name 原始名字，可为 null
     * @return 归一化后的比较键；null → 空串
     */
    public static String normalizeName(String name) {
        if (name == null) {
            return "";
        }
        return name.trim().replaceAll("\\s+", " ").toLowerCase();
    }

    /**
     * 两个名字是否算「同一个」。
     */
    public static boolean sameName(String a, String b) {
        String na = normalizeName(a);
        return !na.isEmpty() && na.equals(normalizeName(b));
    }

    /**
     * 工作台启停只允许 active / disabled（{@code pending} 只由「外部自填待核验」这一条路产生，
     * 不允许人工置回 —— 那会让一个还没核验的名字混进选择器）。
     */
    public static boolean isToggleTarget(String status) {
        return STATUS_ACTIVE.equals(status) || STATUS_DISABLED.equals(status);
    }

    /**
     * 状态值是否在本表允许的取值集合里（字典 {@code lqg_unit_status}）。
     */
    public static boolean isKnownStatus(String status) {
        return STATUS_ACTIVE.equals(status) || STATUS_PENDING.equals(status) || STATUS_DISABLED.equals(status);
    }

}
