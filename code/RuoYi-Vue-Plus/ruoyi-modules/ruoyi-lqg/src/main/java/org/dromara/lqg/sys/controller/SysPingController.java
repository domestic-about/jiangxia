package org.dromara.lqg.sys.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.annotation.SaMode;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.lqg.sys.domain.vo.SysPingVo;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;

/**
 * 后端健康探针：GET /lqg/sys/ping（<b>只给内部角色</b>，doc/api-contract.md「SYS」一节）。
 *
 * <p>这个接口存在的唯一目的，是让验收执行器能证明三件事、而且证明的是**运行期事实**：
 * <ol>
 *   <li>ruoyi-lqg 真的挂进了 ruoyi-admin（否则 404）；</li>
 *   <li>连的库真的是 PostgreSQL 16 —— db / dbVersion 直接问 JDBC 的
 *       {@link DatabaseMetaData}，不写死常量；</li>
 *   <li>多租户真的关了、字段加密真的开着 —— 取自运行中的配置。</li>
 * </ol>
 *
 * <p>不加 {@code @SaIgnore}：匿名访问落在若依的 Sa-Token 全局拦截器上（401），
 * 这也是 accept 第 3 段要的。接口组 {@code /lqg/**} 的鉴权口径见 api-contract.md。
 *
 * <p>★ <b>内部角色闸</b>（独立验收 V20，2026-09-23）：原先登录即可调，外部账号（小程序 mp token）
 * 也能读到 profile、mock 登录开关、加密开关、数据库版本 —— 这些是给验收与运维看的环境指纹，
 * 不该给送检方。现在与首页同一个闸：{@code lqg_admin / lqg_internal / superadmin} 任一即可，
 * 外部 403。<b>{@code mode = SaMode.OR} 不能省</b>（Sa-Token 缺省 AND：要求三个角色同时具备，
 * 管理员与内部人员会一起 403，见 {@code HomeController} 的注释）。角色集合必须与
 * {@code StaffGrantRules.INTERNAL_ROLE_KEYS} 逐字一致，由 {@code SysPingControllerContractTest} 钉住。
 *
 * @author SYS-BASE-001
 */
@RequiredArgsConstructor
@RestController
@RequestMapping("/lqg/sys")
public class SysPingController {

    /**
     * 业务模块名。用常量而不是读 pom：这就是「模块挂没挂进后端」的判据本身。
     */
    private static final String MODULE = "ruoyi-lqg";

    private final DataSource dataSource;
    private final Environment environment;

    /**
     * 打包时注入的 git 提交号。解析顺序：JVM/环境变量 BUILD_COMMIT（部署时注入，推荐走这个）
     * → lqg.build-commit 配置项 → unknown。SYS-STAGING-001 / SYS-PROD-001 打包时注入真值，
     * 本地直接 run 是 unknown —— 反 stale 守卫是看 jar 与源码时间，不看这个值。
     */
    @Value("${BUILD_COMMIT:${lqg.build-commit:unknown}}")
    private String buildCommit;

    /**
     * 小程序 mock 登录开关（ADR-0008）。与 {@code MockLoginGuard} 读同一个键、同一个缺省 false：
     * 探针报的必须是**运行期真值**而不是常量，否则「prod 下 mock 真的关着吗」读不出来。
     */
    @Value("${lqg.auth.mock-login:false}")
    private boolean mockLogin;

    @SaCheckRole(value = {"lqg_admin", "lqg_internal", "superadmin"}, mode = SaMode.OR)
    @GetMapping("/ping")
    public R<SysPingVo> ping() {
        SysPingVo vo = new SysPingVo();
        vo.setModule(MODULE);
        fillDatabase(vo);
        vo.setTenantEnabled(envBoolean("tenant.enable", false));
        vo.setEncryptEnabled(envBoolean("mybatis-encryptor.enable", false));
        vo.setMockLogin(mockLogin);
        vo.setProfile(String.join(",", environment.getActiveProfiles()));
        vo.setBuildCommit(buildCommit);
        // 成功 / 失败都回 R：连不上库不该让验收脚本看到一个没有响应体的 500
        return R.ok(vo);
    }

    /**
     * db / dbVersion 取自真实连接的 DatabaseMetaData（不许写死）。
     * 连不上库时留空——ping 仍然返回 200，让断言在 db 字段上红，而不是整个接口 500。
     */
    private void fillDatabase(SysPingVo vo) {
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData meta = connection.getMetaData();
            vo.setDb(meta.getDatabaseProductName());
            vo.setDbVersion(meta.getDatabaseMajorVersion());
        } catch (Exception e) {
            vo.setDb("unavailable");
            vo.setDbVersion(null);
        }
    }

    /**
     * 读运行期配置的布尔开关；配了非布尔值时按「不成立」处理（探针不抛异常）。
     */
    private boolean envBoolean(String key, boolean defaultValue) {
        return environment.getProperty(key, Boolean.class, defaultValue);
    }

}
