package org.dromara.lqg.auth.staff.directory;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.lqg.cryo.batch.domain.CryoBatch;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 「按人筛」的参数值（{@link PersonKey}，CR-20261012-22）：统计页一行的 key 与列表筛选是同一个口径。
 *
 * @author CR-20261012-22
 */
class PersonKeyTest {

    @BeforeAll
    static void initLambdaCache() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), CryoBatch.class);
    }

    private static LambdaQueryWrapper<CryoBatch> apply(String key) {
        return PersonKey.apply(new LambdaQueryWrapper<CryoBatch>(), CryoBatch::getFrozenById, CryoBatch::getFrozenBy, key);
    }

    @Test
    @DisplayName("① 库里一行 → key：有账号 id 用 id；没有就用姓名；两样都没有 = none")
    void keyOfRow() {
        assertEquals("u:101", PersonKey.of(101L, "李工"));
        assertEquals("n:袁琴琴", PersonKey.of(null, " 袁琴琴 "));
        assertEquals("none", PersonKey.of(null, "  "));
        assertEquals("none", PersonKey.of(null, null));
    }

    @Test
    @DisplayName("② 空 = 不筛")
    void blankMeansNoFilter() {
        assertTrue(apply(null).getSqlSegment().isBlank());
        assertTrue(apply("  ").getSqlSegment().isBlank());
    }

    @Test
    @DisplayName("③ u:<id> 按账号；n:<姓名> 只要没关联账号、文字是这个名字的；none 只要这一栏没填的")
    void threeForms() {
        LambdaQueryWrapper<CryoBatch> user = apply("u:101");
        assertTrue(user.getSqlSegment().contains("frozen_by_id ="), user.getSqlSegment());
        assertFalse(user.getSqlSegment().contains("frozen_by ="), "按账号筛不看文字：" + user.getSqlSegment());
        assertTrue(user.getParamNameValuePairs().containsValue(101L));

        LambdaQueryWrapper<CryoBatch> name = apply("n:袁琴琴");
        assertTrue(name.getSqlSegment().contains("frozen_by_id IS NULL"), name.getSqlSegment());
        assertTrue(name.getParamNameValuePairs().containsValue("袁琴琴"));

        LambdaQueryWrapper<CryoBatch> none = apply("none");
        assertTrue(none.getSqlSegment().contains("frozen_by_id IS NULL"), none.getSqlSegment());
        assertTrue(none.getSqlSegment().contains("frozen_by IS NULL"), none.getSqlSegment());
    }

    @Test
    @DisplayName("④ 认不出来的值 → 400（不静默当成不筛，否则统计页点过去看到的是全表）")
    void unknownFormIs400() {
        for (String bad : List.of("101", "u:abc", "n:", "x:1")) {
            ServiceException e = assertThrows(ServiceException.class, () -> apply(bad), bad);
            assertEquals(Integer.valueOf(400), e.getCode(), bad);
        }
    }

}
