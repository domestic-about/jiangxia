package org.dromara.lqg.auth.staff.directory;

import java.util.Map;
import java.util.Set;

/**
 * 「这一栏人名最后落成什么」的规则（CR-20261012-22）—— <b>纯函数</b>，不碰库，单测直接钉。
 *
 * <p>入参是请求里的 {@code id}（从名单里选的人）与 {@code name}（姓名文字），加上库里现在的值；
 * 名单由调用方读好传进来（{@code StaffDirectoryService}）。
 *
 * <p>★ 判定顺序（前一条命中就不看后面的）：
 * <ol>
 *   <li><b>带了 id、与库里现在的一样</b> → 原样留着。已经撤销授权的人不在名单里，
 *       但改这条记录的别的字段时不该因为「操作人不在名单里」被拦住。</li>
 *   <li><b>带了 id、是名单里的人</b> → 用它，姓名取名单里的（不信请求里的名字）。</li>
 *   <li><b>带了 id、不在名单里</b> → 拒（{@link Outcome#rejected}）：选了一个不是内部人员的账号。</li>
 *   <li><b>没带 id、名字为空</b> → 清空。</li>
 *   <li><b>没带 id、名字与库里现在的一样</b> → 原样留着（旧版客户端整份回传时只带名字，不能把已有的关联洗掉）。</li>
 *   <li><b>没带 id、名字恰好对上名单里唯一一个人</b> → 自动关联（旧版客户端默认带的是当前登录人的姓名）。</li>
 *   <li>其余（对不上、重名）→ 只存文字，不关联。统计页会把它单列为「未关联账号」。</li>
 * </ol>
 *
 * @author CR-20261012-22
 */
public final class StaffRefRules {

    private StaffRefRules() {
    }

    /**
     * 判定结果：{@code rejected = true} 时 {@code ref} 无意义。
     */
    public record Outcome(StaffRef ref, boolean rejected) {

        static Outcome of(StaffRef ref) {
            return new Outcome(ref, false);
        }

        static Outcome reject() {
            return new Outcome(StaffRef.EMPTY, true);
        }
    }

    /**
     * @param id       请求里的账号 id（没带 = null）
     * @param name     请求里的姓名文字（没带 = null）
     * @param existing 库里现在的值（新增 = null）
     * @param names    名单：账号 id → 姓名
     * @param unique   名单里<b>不重名</b>的姓名 → 账号 id（重名的人不在这张表里）
     */
    public static Outcome resolve(Long id, String name, StaffRef existing,
                                  Map<Long, String> names, Map<String, Long> unique) {
        StaffRef asked = StaffRef.of(id, name);
        StaffRef current = existing == null ? StaffRef.EMPTY : existing;
        if (asked.id() != null) {
            if (asked.id().equals(current.id())) {
                return Outcome.of(current);
            }
            String staffName = names.get(asked.id());
            return staffName == null ? Outcome.reject() : Outcome.of(StaffRef.of(asked.id(), staffName));
        }
        if (asked.name() == null) {
            return Outcome.of(StaffRef.EMPTY);
        }
        if (asked.name().equals(current.name())) {
            return Outcome.of(current);
        }
        return Outcome.of(StaffRef.of(unique.get(asked.name()), asked.name()));
    }

    /**
     * 名单里不重名的姓名 → 账号 id（同名的两个人都不进表：按名字关联时不替用户猜是哪一个）。
     */
    public static Map<String, Long> uniqueNames(Map<Long, String> names) {
        Map<String, Long> out = new java.util.HashMap<>();
        Set<String> duplicated = new java.util.HashSet<>();
        for (Map.Entry<Long, String> entry : names.entrySet()) {
            String name = entry.getValue() == null ? "" : entry.getValue().trim();
            if (name.isEmpty() || duplicated.contains(name)) {
                continue;
            }
            if (out.containsKey(name)) {
                out.remove(name);
                duplicated.add(name);
                continue;
            }
            out.put(name, entry.getKey());
        }
        return out;
    }

}
