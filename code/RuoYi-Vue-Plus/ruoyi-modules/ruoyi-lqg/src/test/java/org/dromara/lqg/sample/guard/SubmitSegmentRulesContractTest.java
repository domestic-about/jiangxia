package org.dromara.lqg.sample.guard;

import org.dromara.common.core.exception.ServiceException;
import org.dromara.lqg.sample.domain.bo.SampleSubmitSegmentBo;
import org.dromara.lqg.sample.guard.SubmitSegmentRules.UnitRef;
import org.dromara.lqg.sample.guard.SubmitSegmentRules.Writer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 样本写入口的必填与格式（{@link SubmitSegmentRules}）的契约测试 —— FIX V03（issue #88 / #111）
 * 与 FIX V01（issue #112）的纯函数部分。
 *
 * <p>钉的是「进库之前就拒」：每一条违规都必须在这里被认出来并给出<b>字段级</b>的人话，
 * 业务码 400；认不出来的那一条就会一路走到 PostgreSQL 的约束上，被全局异常处理把 SQL 回吐给外部。
 *
 * @author FIX-V03
 */
class SubmitSegmentRulesContractTest {

    private static SampleSubmitSegmentBo tissue(String unitName, String donor, String tissueType) {
        SampleSubmitSegmentBo s = new SampleSubmitSegmentBo();
        s.setSourceUnitName(unitName);
        // 种属两类都必填（CR-20261009-18）：这里给齐，各条用例只看自己要钉的那一项；种属本身见 ⑯
        s.setSpecies("人");
        s.setDonorName(donor);
        s.setTissueType(tissueType);
        return s;
    }

    private static String repeat(String s, int n) {
        return s.repeat(n);
    }

    // ── 必填 ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("① 外部组织样本：来源单位 / 供体姓名 / 组织类型 缺一个报一个，一次报全")
    void externalTissueRequiredFields() {
        List<String> v = SubmitSegmentRules.submitViolations("tissue", tissue(null, " ", ""), Writer.EXTERNAL);
        assertEquals(3, v.size(), "三个必填都缺 → 三条：" + v);
        assertTrue(v.get(0).startsWith("来源单位不能为空"), v.toString());
        assertTrue(v.get(0).contains("我的 → 单位与组别"), "★ #111：新外部用户第一次提交要被告知去哪绑定单位：" + v);
        assertTrue(v.contains("组织类型不能为空"), v.toString());
        assertTrue(v.contains("供体姓名不能为空"), v.toString());

        assertTrue(SubmitSegmentRules.submitViolations("tissue", tissue("A 医院", "张三", "肝组织"), Writer.EXTERNAL).isEmpty(),
            "三项都给了 → 通过");
    }

    @Test
    @DisplayName("② 选了单位（id）就不要求单位名；内部录入不要求供体姓名（工作台口径不变）")
    void unitIdSatisfiesUnitAndInternalDoesNotRequireDonor() {
        SampleSubmitSegmentBo s = tissue(null, null, "肝组织");
        s.setSourceUnitId(9000009001L);
        assertTrue(SubmitSegmentRules.submitViolations("tissue", s, Writer.INTERNAL).isEmpty(),
            "内部：有单位 id + 组织类型就够（供体姓名在工作台不是必填）");
        assertEquals(List.of("供体姓名不能为空"), SubmitSegmentRules.submitViolations("tissue", s, Writer.EXTERNAL));
    }

    @Test
    @DisplayName("③ 类器官只看来源单位 + 类器官类型；组织样本的字段不参与（类器官收样记录没有那几列）")
    void organoidOnlyChecksItsOwnFields() {
        SampleSubmitSegmentBo s = new SampleSubmitSegmentBo();
        s.setSourceUnitName("B 大学");
        s.setSpecies("鼠兔");
        s.setGender("bogus");          // 组织样本字段：类器官路径根本不落库，不报
        s.setHasPathology("yes");
        assertEquals(List.of("类器官类型不能为空"), SubmitSegmentRules.submitViolations("organoid", s, Writer.EXTERNAL));
        s.setOrganoidType("肝类器官");
        assertTrue(SubmitSegmentRules.submitViolations("organoid", s, Writer.EXTERNAL).isEmpty());
    }

    // ── 字典与长度 ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("④ 字典外的值（台账 #88 的复现：hasPathology='yes'）在进库之前就被认出来")
    void dictionaryValues() {
        SampleSubmitSegmentBo s = tissue("A 医院", "张三", "肝组织");
        s.setHasPathology("yes");
        s.setGender("M");
        List<String> v = SubmitSegmentRules.submitViolations("tissue", s, Writer.EXTERNAL);
        assertTrue(v.contains("有无病理只能是 Y（有）或 N（无）"), v.toString());
        assertTrue(v.contains("性别只能是 male（男）/ female（女）/ unknown（未知）"), v.toString());

        s.setHasPathology("Y");
        s.setGender("unknown");
        assertTrue(SubmitSegmentRules.submitViolations("tissue", s, Writer.EXTERNAL).isEmpty());
    }

    @Test
    @DisplayName("⑤ 长度按 DDL；两个加密列按「密文放得进 VARCHAR(255)」封顶（50 字且 UTF-8 ≤ 175 字节）")
    void lengths() {
        SampleSubmitSegmentBo s = tissue(repeat("单", 101), repeat("名", 51), repeat("型", 101));
        s.setAge(repeat("1", 21));
        s.setHospitalNo(repeat("Z", 51));
        s.setRemark(repeat("备", 501));
        List<String> v = SubmitSegmentRules.submitViolations("tissue", s, Writer.EXTERNAL);
        assertTrue(v.contains("来源单位名称不能超过 100 字"), v.toString());
        assertTrue(v.contains("供体姓名不能超过 50 字"), v.toString());
        assertTrue(v.contains("组织类型不能超过 100 字"), v.toString());
        assertTrue(v.contains("年龄不能超过 20 字"), v.toString());
        assertTrue(v.contains("住院号不能超过 50 字"), v.toString());
        assertTrue(v.contains("备注不能超过 500 字"), v.toString());

        // 恰好在上限上：通过（中文按字数算，不按字节）
        SampleSubmitSegmentBo edge = tissue(repeat("单", 100), repeat("名", 50), repeat("型", 100));
        edge.setRemark(repeat("备", 500));
        assertTrue(SubmitSegmentRules.submitViolations("tissue", edge, Writer.EXTERNAL).isEmpty(),
            "上限本身必须放行：" + SubmitSegmentRules.submitViolations("tissue", edge, Writer.EXTERNAL));

        // 50 个 4 字节字符 = 200 字节 → AES 之后密文 280 个字符，放不进 VARCHAR(255)：必须拒
        SampleSubmitSegmentBo emoji = tissue("A 医院", repeat("😀", 50), "肝组织");
        assertTrue(SubmitSegmentRules.submitViolations("tissue", emoji, Writer.EXTERNAL).contains("供体姓名不能超过 50 字"));
    }

    @Test
    @DisplayName("⑥ 收样段：三个两态按钮只收 Y / N，操作人 ≤ 50 字，内部编号 ≤ 64 字；内部必填 = 内部编号 + 收样日期")
    void receiveSegment() {
        List<String> v = SubmitSegmentRules.receiveViolations(repeat("T", 65), "有", "Y", "x", repeat("操", 51));
        assertEquals(List.of("内部编号不能超过 64 字", "有无固定只能是 Y（有）或 N（无）",
            "细胞活率报告只能是 Y（有）或 N（无）", "操作人不能超过 50 字"), v);
        assertEquals(List.of("内部编号不能为空", "收样日期不能为空"), SubmitSegmentRules.receiveRequiredViolations(" ", null));
        assertTrue(SubmitSegmentRules.receiveRequiredViolations("T-1", java.time.LocalDate.now()).isEmpty());
    }

    @Test
    @DisplayName("⑦ 外部石蜡包埋送样：POST 必须给所挂样本；样本类型 ≤ 50、类器官来源类型 ≤ 100")
    void externalEmbed() {
        assertEquals(List.of("请选择要送样的样本（sampleId 不能为空）"),
            SubmitSegmentRules.externalEmbedViolations(null, true, "组织", null));
        assertTrue(SubmitSegmentRules.externalEmbedViolations(null, false, "组织", null).isEmpty(),
            "PUT 不给 sampleId = 不改所挂样本，不是错误");
        assertEquals(List.of("样本类型不能超过 50 字", "类器官来源类型不能超过 100 字"),
            SubmitSegmentRules.externalEmbedViolations(1L, true, repeat("样", 51), repeat("源", 101)));
    }

    @Test
    @DisplayName("⑧ 出口：违规 → ServiceException 业务码 400，msg = 前缀 + 逐条（没有 SQL、没有表名）")
    void throwIfAnyIs400WithFieldMessages() {
        ServiceException e = assertThrows(ServiceException.class,
            () -> SubmitSegmentRules.throwIfAny(List.of("供体姓名不能为空", "组织类型不能为空")));
        assertEquals(Integer.valueOf(400), e.getCode());
        assertEquals("提交的内容不符合要求：供体姓名不能为空；组织类型不能为空", e.getMessage());
        SubmitSegmentRules.throwIfAny(List.of());   // 空 = 不抛
    }

    // ── 外部提交的来源单位归属（V01 / issue #112） ───────────────────────────

    private static final UnitRef A = new UnitRef(9000009001L, "A 医院");
    private static final UnitRef B = new UnitRef(9000009002L, "B 大学");

    @Test
    @DisplayName("⑨ ★ 前端不发 id：单位名与本人绑定的单位同名 → 挂上这个单位的 id（去空白、大小写不敏感）")
    void nameMatchingTheBoundUnitGetsItsId() {
        assertSame(A, SubmitSegmentRules.attributeExternalUnit(A, null, "A 医院", null),
            "★ 小程序组织样本表单只发单位名 —— 这正是 #112 的病灶形态");
        assertSame(A, SubmitSegmentRules.attributeExternalUnit(A, null, "  a   医院 ", null), "空白与大小写不敏感");
    }

    @Test
    @DisplayName("⑩ ★ 不能把样本挂到别的单位：发来的 id 不是本人绑定的单位 → 400；单位名是别的单位 → 只存名称")
    void cannotAttachToAnotherUnit() {
        ServiceException e = assertThrows(ServiceException.class,
            () -> SubmitSegmentRules.attributeExternalUnit(A, B.id(), "B 大学", null));
        assertEquals(Integer.valueOf(400), e.getCode());
        assertTrue(e.getMessage().contains("只能选您在「我的 → 单位与组别」里绑定的单位"), e.getMessage());

        UnitRef typed = SubmitSegmentRules.attributeExternalUnit(A, null, "B 大学", null);
        assertNull(typed.id(), "★ 名字写的是别的单位 → 不按名字去单位表里找 id（那样外部就能挂到任意单位）");
        assertEquals("B 大学", typed.name());

        // 没有可用单位（unbound / rejected / 只自填了单位名）：带任何 id 都拒
        assertThrows(ServiceException.class, () -> SubmitSegmentRules.attributeExternalUnit(null, A.id(), "A 医院", null));
        UnitRef unbound = SubmitSegmentRules.attributeExternalUnit(null, null, "A 医院", null);
        assertNull(unbound.id(), "没有可用单位 → 只存名称快照，由实验室核验时归口");
        assertSame(A, SubmitSegmentRules.attributeExternalUnit(A, A.id(), "随便写的名字", null),
            "带的就是本人绑定的单位 → 用它（名称取单位表的）");
    }

    @Test
    @DisplayName("⑪ #111：没有 id、单位名为空 → 400 且告诉他去「我的 → 单位与组别」绑定")
    void blankUnitWithoutIdIs400WithHint() {
        ServiceException e = assertThrows(ServiceException.class,
            () -> SubmitSegmentRules.attributeExternalUnit(null, null, "  ", null));
        assertEquals(Integer.valueOf(400), e.getCode());
        assertTrue(e.getMessage().contains("来源单位不能为空") && e.getMessage().contains("我的 → 单位与组别"), e.getMessage());
    }

    @Test
    @DisplayName("⑫ 重提：单位名没改 → 沿用这条样本已挂的单位（实验室核验时挂的也不丢）；带回已挂的 id 也认")
    void resubmitKeepsTheAlreadyAttachedUnit() {
        UnitRef existing = new UnitRef(9000009002L, "B 大学");
        assertSame(existing, SubmitSegmentRules.attributeExternalUnit(A, null, "B 大学", existing),
            "名字没改：外部重提不许把实验室挂好的单位弄丢");
        assertSame(existing, SubmitSegmentRules.attributeExternalUnit(null, 9000009002L, "B 大学", existing));
        UnitRef changed = SubmitSegmentRules.attributeExternalUnit(null, null, "C 研究所", existing);
        assertNull(changed.id(), "名字改成了别的 → 只存新名称");
        assertFalse(changed.name().isEmpty());
    }

    // ── 代数（CR-20260924-10：甲方 2026-09-24 第 18 行，类器官收样记录加「代数」） ────────

    private static SampleSubmitSegmentBo organoid(String passage) {
        SampleSubmitSegmentBo s = new SampleSubmitSegmentBo();
        s.setSourceUnitName("B 大学");
        s.setSpecies("人");
        s.setOrganoidType("肝类器官");
        s.setPassage(passage);
        return s;
    }

    @Test
    @DisplayName("⑬ 代数选填；填了必须形如 P3：去首尾空白、小写 p 转大写后判，3 / 第3代 / P1234 / P 都拒，提示是人话")
    void passageFormat() {
        for (String ok : new String[] {null, "", "   ", "P3", " P12 ", "p3", "P999"}) {
            assertTrue(SubmitSegmentRules.submitViolations("organoid", organoid(ok), Writer.EXTERNAL).isEmpty(),
                "代数「" + ok + "」应当通过");
        }
        for (String bad : new String[] {"3", "第3代", "P1234", "P", "P3a", "PP3", "p 3"}) {
            assertEquals(List.of(SubmitSegmentRules.MSG_PASSAGE),
                SubmitSegmentRules.submitViolations("organoid", organoid(bad), Writer.INTERNAL),
                "代数「" + bad + "」必须被拒，且只报这一条");
        }
        assertEquals("代数请填 P 加数字，如 P3", SubmitSegmentRules.MSG_PASSAGE, "提示给人看的，别出现正则");

        // 落库值：与校验同一个归一化
        assertEquals("P3", SubmitSegmentRules.normalizePassage(" p3 "));
        assertEquals("P12", SubmitSegmentRules.normalizePassage("P12"));
        assertNull(SubmitSegmentRules.normalizePassage("  "), "选填：空白 = 没填 = NULL");
        assertNull(SubmitSegmentRules.normalizePassage(null));
    }

    @Test
    @DisplayName("⑭ 代数与冻存批次同一条规则（复用 CryoBalanceChecker.requirePassage），只多一步「小写 p 转大写」")
    void passageRuleIsTheCryoRule() {
        for (String v : new String[] {"P1", "P3", "P12", "P999", "P0", "P1234", "3", "P", "第3代", "PX", "P3 3"}) {
            boolean cryoAccepts;
            try {
                org.dromara.lqg.cryo.batch.CryoBalanceChecker.requirePassage(v);
                cryoAccepts = true;
            } catch (ServiceException e) {
                cryoAccepts = false;
            }
            assertEquals(cryoAccepts, SubmitSegmentRules.isValidPassage(v), "「" + v + "」两边判法必须一致");
        }
        // 冻存那边拒 p3；收样记录这边转成 P3 收下（外部手填，一眼能看懂的写法不让人重填）
        assertTrue(SubmitSegmentRules.isValidPassage("p3"));
    }

    @Test
    @DisplayName("⑮ 组织样本没有代数：传了什么都不报错（写路径一律写 NULL，与「另一类的类型列传了不生效」同口径）")
    void tissueIgnoresPassage() {
        SampleSubmitSegmentBo s = tissue("A 医院", "张三", "肝组织");
        s.setPassage("第3代");
        assertTrue(SubmitSegmentRules.submitViolations("tissue", s, Writer.EXTERNAL).isEmpty());
    }

    // ── 种属（CR-20261009-18：甲方 2026-10-09，样本记录信息表 / 类器官收样记录都加「种属」） ──────

    @Test
    @DisplayName("⑯ 种属：两类、内外部都必填；字典外的值照收（「还可以添加其他的」），只封顶 50 字")
    void speciesIsRequiredFreeTextUpTo50() {
        for (String kind : new String[] {"tissue", "organoid"}) {
            for (Writer writer : Writer.values()) {
                SampleSubmitSegmentBo s = kind.equals("tissue") ? tissue("A 医院", "张三", "肝组织") : organoid(null);
                s.setSpecies("  ");
                assertEquals(List.of("种属不能为空"), SubmitSegmentRules.submitViolations(kind, s, writer),
                    kind + " / " + writer + "：空白的种属 = 没填");
                for (String ok : new String[] {"人", "鼠兔", "移植猪", "鸡", "食蟹猴", repeat("种", 50)}) {
                    s.setSpecies(ok);
                    assertTrue(SubmitSegmentRules.submitViolations(kind, s, writer).isEmpty(),
                        kind + " / " + writer + "：种属「" + ok + "」应当通过（字典外的值也收）");
                }
                s.setSpecies(repeat("种", 51));
                assertEquals(List.of("种属不能超过 50 字"), SubmitSegmentRules.submitViolations(kind, s, writer));
            }
        }
        assertEquals(50, SubmitSegmentRules.MAX_SPECIES, "与 t_lqg_sample.species VARCHAR(50) 一致");
    }

}
