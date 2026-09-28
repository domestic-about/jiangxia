package org.dromara.lqg.auth.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.dromara.common.core.exception.ServiceException;
import org.junit.jupiter.api.Test;

/**
 * mock 登录的**预置身份清单**口径（2026-09-28 修台账 #346 时加）。
 *
 * <p>被测：{@link WxIdentityResolver#mockPhoneFor(String, String)}（纯函数）。
 *
 * <p>为什么要有这条：mock 登录原来**采信调用方传的手机号**，而 {@code WxAccountBindService} 是按手机号
 * 复用已有账号的 —— 于是「报出种子里内部人员的号码（13800000001）」就能直接拿到 {@code internal} 身份；
 * 又因为工作台鉴权是角色制（不是 client 制），同一个 token 还能读写工作台业务数据。
 * 测试环境公网可达，这等于一个开放的身份入口。
 *
 * <p>本测试钉死三件事：
 * <ol>
 *   <li>清单里的 key → 返回**清单里的**手机号；调用方传什么号码都不影响（这条是本次修复的核心）；</li>
 *   <li>清单外的 key → 拒绝，且报错里带 key；</li>
 *   <li>清单为空 → 拒绝（fail-closed），**不是**退化成「不校验」。</li>
 * </ol>
 */
class WxIdentityResolverMockIdentitiesTest {

    /** 与 application-dev.yml / application-test.yml 里那份逐字一致 */
    private static final String LIST =
        "admin:13800000000,staff:13800000001,extA:13800000011,extB:13800000012,extC:13800000013,"
            + "extD:13800000014,extE:13800000015,extF:13800000016,newbie1:13800000099,newbie2:13800000099";

    @Test
    void listedKeyReturnsPhoneFromList() {
        assertEquals("13800000001", WxIdentityResolver.mockPhoneFor(LIST, "staff"));
        assertEquals("13800000011", WxIdentityResolver.mockPhoneFor(LIST, "extA"));
        assertEquals("13800000099", WxIdentityResolver.mockPhoneFor(LIST, "newbie1"));
        // newbie2 与 newbie1 **同一个手机号、不同 key**：这是「不同 openid 绑到同一手机号 → 复用已有账号」
        // 那条路径（AUTH-LOGIN-001 acc2 靠它），必须继续可用。
        assertEquals("13800000099", WxIdentityResolver.mockPhoneFor(LIST, "newbie2"));
    }

    @Test
    void callerSuppliedPhoneIsNotTrusted() {
        // ★ 核心：接口只接受 (清单, key) 两个入参，**根本没有**把调用方的手机号传进来的口子。
        //   换句话说，即便请求体里写 mock:13900000000，解析出来的也只会是清单里的号码。
        //   这条断言的存在方式就是「签名里没有 phone 参数」——用返回值再确认一次。
        assertEquals("13800000001", WxIdentityResolver.mockPhoneFor(LIST, "staff"));
    }

    @Test
    void unlistedKeyIsRejected() {
        ServiceException e = assertThrows(ServiceException.class,
            () -> WxIdentityResolver.mockPhoneFor(LIST, "boss"));
        assertTrue(e.getMessage().contains("boss"), "报错应带上被拒的 key，便于排查：" + e.getMessage());

        // 用别人的手机号 + 自造 key 也不行：key 不在清单里就拒
        assertThrows(ServiceException.class, () -> WxIdentityResolver.mockPhoneFor(LIST, ""));
        assertThrows(ServiceException.class, () -> WxIdentityResolver.mockPhoneFor(LIST, "STAFF"));
    }

    @Test
    void emptyListFailsClosed() {
        // 没配清单时必须拒绝，绝不能退化成「不校验」——那正是要修的洞
        assertThrows(ServiceException.class, () -> WxIdentityResolver.mockPhoneFor("", "staff"));
        assertThrows(ServiceException.class, () -> WxIdentityResolver.mockPhoneFor(null, "staff"));
        assertThrows(ServiceException.class, () -> WxIdentityResolver.mockPhoneFor("   ", "staff"));
    }

    @Test
    void malformedPairsAreSkippedNotFatal() {
        // 配置里混了坏项（没有冒号 / 冒号在开头 / 号码为空）时，坏项跳过、好项照用
        String messy = "::,noColon,staff:,extA:13800000011,,  extB : 13800000012  ";
        assertEquals("13800000011", WxIdentityResolver.mockPhoneFor(messy, "extA"));
        assertEquals("13800000012", WxIdentityResolver.mockPhoneFor(messy, "extB"));
        // 坏项不产生身份
        assertThrows(ServiceException.class, () -> WxIdentityResolver.mockPhoneFor(messy, "noColon"));
        assertThrows(ServiceException.class, () -> WxIdentityResolver.mockPhoneFor(messy, "staff"));
    }
}
