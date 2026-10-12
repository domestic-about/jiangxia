package org.dromara.lqg.auth.staff.directory;

import org.dromara.lqg.auth.staff.directory.StaffRefRules.Outcome;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 「这一栏人名最后落成什么」（{@link StaffRefRules}，CR-20261012-22）。
 * 名单：101 李工、102 刘佳、103 张伟、104 张伟（重名）。
 *
 * @author CR-20261012-22
 */
class StaffRefRulesTest {

    private static final Map<Long, String> NAMES = new LinkedHashMap<>();

    static {
        NAMES.put(101L, "李工");
        NAMES.put(102L, "刘佳");
        NAMES.put(103L, "张伟");
        NAMES.put(104L, "张伟");
    }

    private static final Map<String, Long> UNIQUE = StaffRefRules.uniqueNames(NAMES);

    private static Outcome resolve(Long id, String name, StaffRef existing) {
        return StaffRefRules.resolve(id, name, existing, NAMES, UNIQUE);
    }

    @Test
    @DisplayName("① 选了名单里的人：落账号 id，姓名取名单里的（不信请求里的名字）")
    void pickedFromDirectory() {
        Outcome o = resolve(102L, "随便写的", null);
        assertFalse(o.rejected());
        assertEquals(new StaffRef(102L, "刘佳"), o.ref());
        assertEquals(new StaffRef(102L, "刘佳"), resolve(102L, null, new StaffRef(101L, "李工")).ref(), "换人");
    }

    @Test
    @DisplayName("② ★ 选了名单外的账号 → 拒；但与库里现在的一样（已撤销授权的人）→ 原样留着")
    void unknownIdRejectedUnlessUnchanged() {
        assertTrue(resolve(999L, "外人", null).rejected());
        assertTrue(resolve(999L, null, new StaffRef(101L, "李工")).rejected());
        StaffRef revoked = new StaffRef(999L, "离职的人");
        Outcome o = resolve(999L, "离职的人", revoked);
        assertFalse(o.rejected(), "改这条记录别的字段时，不能因为操作人不在名单里被拦住");
        assertEquals(revoked, o.ref());
    }

    @Test
    @DisplayName("③ 没带 id、名字为空 → 清空（两样都没有）")
    void blankClears() {
        assertEquals(StaffRef.EMPTY, resolve(null, "  ", new StaffRef(101L, "李工")).ref());
        assertEquals(StaffRef.EMPTY, resolve(null, null, null).ref());
    }

    @Test
    @DisplayName("④ ★ 旧版客户端只带名字：没改 → 已有的关联不洗掉；对上唯一一个人 → 自动关联")
    void nameOnlyFromOldClients() {
        StaffRef linked = new StaffRef(101L, "李工");
        assertEquals(linked, resolve(null, " 李工 ", linked).ref(), "整份回传时只带了名字、没改");
        assertEquals(new StaffRef(102L, "刘佳"), resolve(null, "刘佳", linked).ref(), "改成另一个名单里的人");
        assertEquals(new StaffRef(101L, "李工"), resolve(null, "李工", null).ref(), "新增时默认带的是当前登录人的姓名");
    }

    @Test
    @DisplayName("⑤ 对不上账号的名字、重名的名字：只存文字，不替用户猜是谁")
    void unknownOrDuplicatedNameStaysText() {
        Outcome unknown = resolve(null, "袁琴琴", null);
        assertFalse(unknown.rejected());
        assertNull(unknown.ref().id());
        assertEquals("袁琴琴", unknown.ref().name());
        assertNull(resolve(null, "张伟", null).ref().id(), "两个张伟：按名字关联不了");
        assertEquals(new StaffRef(104L, "张伟"), resolve(104L, null, null).ref(), "带了 id 就不存在重名的问题");
    }

    @Test
    @DisplayName("⑥ 不重名表：重名的人都不在里面，空名字不进表")
    void uniqueNamesDropsDuplicates() {
        assertEquals(Map.of("李工", 101L, "刘佳", 102L), UNIQUE);
        Map<Long, String> withBlank = new LinkedHashMap<>(NAMES);
        withBlank.put(105L, " ");
        assertEquals(UNIQUE, StaffRefRules.uniqueNames(withBlank));
    }

}
