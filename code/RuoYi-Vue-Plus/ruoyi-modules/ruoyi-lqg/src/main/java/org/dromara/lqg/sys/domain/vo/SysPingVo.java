package org.dromara.lqg.sys.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 健康探针视图对象 —— GET /lqg/sys/ping 的 data 形状。
 *
 * <p>形状权威：doc/api-contract.md 的「SYS」一节。db / dbVersion / tenantEnabled / encryptEnabled
 * 四项**一律取自运行期**（真实连接的 DatabaseMetaData、运行中的配置），不许写死成常量——
 * 写死的话「库真的是 PostgreSQL 吗」「多租户真关了吗」这两条断言就永远是绿的。
 *
 * @author SYS-BASE-001
 */
@Data
@Schema(description = "后端健康探针")
public class SysPingVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 业务模块 artifactId：ruoyi-lqg（证明这个模块真的挂进了后端）
     */
    @Schema(description = "业务模块名")
    private String module;

    /**
     * 数据库产品名，取自 DatabaseMetaData.getDatabaseProductName()，如 PostgreSQL
     */
    @Schema(description = "数据库产品名（运行期取值）")
    private String db;

    /**
     * 数据库大版本号，取自 DatabaseMetaData.getDatabaseMajorVersion()，如 16
     */
    @Schema(description = "数据库大版本（运行期取值）")
    private Integer dbVersion;

    /**
     * 多租户开关（ADR-0001 定死为 false）
     */
    @Schema(description = "多租户是否开启")
    private Boolean tenantEnabled;

    /**
     * 字段加密开关（dev / test 必须是 true，否则 seed 里的密文读出来是乱码）
     */
    @Schema(description = "字段加密是否开启")
    private Boolean encryptEnabled;

    /**
     * 小程序 mock 登录开关（ADR-0008：只在 dev / test 存在）。
     * SYS-BASE-001 阶段还没有 mock 登录实现，所以这个字段暂时不返回；
     * AUTH-LOGIN-001 落地 mock 登录时把它接上（同一个 /lqg/sys/ping 形状，不用改接口）。
     */
    @Schema(description = "mock 登录是否可用（AUTH-LOGIN-001 起生效）")
    private Boolean mockLogin;

    /**
     * 当前激活的 Spring profile
     */
    @Schema(description = "激活的 profile")
    private String profile;

    /**
     * 打包时注入的 git 提交号；未注入时为 unknown（远程环境靠它反 stale）
     */
    @Schema(description = "构建提交号")
    private String buildCommit;

}
