# code/deploy/common —— 各环境复用的部署资产

| 文件 | 用途 | 谁在用 |
|---|---|---|
| `Dockerfile.backend` | 后端镜像：**多阶段**（maven 构建 → JRE 21 运行），构建参数 `BUILD_COMMIT` 注入 `/lqg/sys/ping` 的 `buildCommit` | **与运行机同架构**的场合：amd64 CI runner、x86 服务器直接建。上下文 = `code/RuoYi-Vue-Plus` |
| `wait-for` | 等 TCP 端口 / HTTP 地址就绪的纯 bash 小脚本（不依赖 nc / curl） | `code/deploy/test/remote/01-up.sh` 等远端阶段脚本；其它环境可复用 |
| `nginx/workspace-site.conf.template` | 工作台站点的 server 块（静态 dist + `/prod-api` 反代 + 小程序/验收直连的根路径接口反代 + SPA 回退） | 渲染成 `code/deploy/test/nginx/workspace.conf`（compose 里的 nginx 容器） |
| `nginx/baota-site.conf.template` | 宿主 nginx（宝塔）vhost：TLS 终止 + 全量反代到 compose 的 nginx | 与 `code/deploy/test/remote/02-host-nginx.sh` 生成的 vhost 同构（那边是内联 heredoc，这份是给别的环境抄的模板） |
| `nginx/proxy-headers.inc.template` | 反代共用头（含 SSE 长连接、`X-Forwarded-*`） | 渲染成 `code/deploy/test/nginx/proxy-headers.inc` |

## ★ 后端的「两份 Dockerfile」——为什么（SYS-STAGING-001 与票面 §2 的偏差）

票面 §2 的字面要求是「后端 Dockerfile（多阶段：maven 构建 → JRE 运行）」。仓库里确实有这一份
（`Dockerfile.backend`），但**测试机的部署不走它**，走的是
`code/RuoYi-Vue-Plus/ruoyi-admin/Dockerfile`（单阶段 `COPY` 已打好的 jar）。

原因（实测，见 `doc/waves/ops/tianda-test-env.md` §4.5）：

| | 架构 |
|---|---|
| 开发机（Mac / Apple Silicon） | `linux/aarch64` |
| 测试服务器（阿里云 ECS） | `x86_64` |

* 在开发机上用多阶段 Dockerfile 构建 → 产出 **arm64** 镜像，推到测试机是 `exec format error`；
* 加 `--platform linux/amd64` 走 qemu 模拟 maven 全量构建 → 慢到不可接受；
* 采用路线：**jar 与 CPU 架构无关** → 本地 `mvn package` → rsync jar 上机 →
  服务器**原生** `docker build`（amd64，只 COPY jar，秒级）。
  基础镜像 `eclipse-temurin:21-jre-jammy` 已在测试机就位（宿主只有 JDK 17，后端必须跑容器）。

两条路线产出的运行镜像等价（同一个 jar、同一个 JRE 21、同一个 `BUILD_COMMIT` → `ENV`）。
`Dockerfile.backend`（多阶段）留给 amd64 CI 用，不删。

`BUILD_COMMIT` 的两条注入链路都要对：
* 多阶段：`ARG BUILD_COMMIT` → `ENV BUILD_COMMIT`（运行阶段）；
* 单阶段：`ARG BUILD_COMMIT` → `ENV BUILD_COMMIT`，另外 compose 的 `environment` 再给一次。

`SysPingController` 用 `@Value("${BUILD_COMMIT:${lqg.build-commit:unknown}}")` 读它，
于是 `GET /lqg/sys/ping` 的 `buildCommit` 就是打包时的提交号——远程环境看不到 jar 的 mtime，
这是唯一的反 stale 手段（SYS-STAGING-001 accept 第 1 条）。
