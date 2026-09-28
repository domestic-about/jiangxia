# ── 后端运行时镜像（SYS-STAGING-001）──────────────────────────────────────────
# 用法（构建上下文 = **本模块目录** code/RuoYi-Vue-Plus/ruoyi-admin，jar 在 ./target/）：
#   docker build --build-arg BUILD_COMMIT="$(git rev-parse --short=12 HEAD)" \
#     -t lqg-test-backend:"$(git rev-parse --short=12 HEAD)" code/RuoYi-Vue-Plus/ruoyi-admin
# 测试机上的实际调用见 code/deploy/test/docker-compose.yml 的 backend.build 与 deploy.sh。
#
# ★ 为什么是「单阶段 COPY 已打好的 jar」而不是票面 §2 字面的多阶段（偏差 + 理由）：
#   开发机是 linux/aarch64、测试机是 x86_64（实测，见 doc/waves/ops/tianda-test-env.md §4.5）。
#   多阶段里的 maven 构建在开发机上会产出 arm64 镜像 → 测试机 `exec format error`；
#   加 --platform linux/amd64 走 qemu 模拟全量 maven 构建慢到不可接受。
#   采用的路线：jar 与架构无关 → 本地 mvn package → rsync jar 上机 → **服务器原生 docker build**（秒级）。
#   多阶段那份**保留**在同目录树：code/deploy/common/Dockerfile.backend（留给同架构的 amd64 CI）。
#
# 相对上游模板的三处改动（原模板见 git 历史：bellsoft/liberica-openjdk-rocky:17）：
#   1. 基础镜像换 eclipse-temurin:21-jre-jammy —— 宿主 JDK 是 17，本项目要 21，且该镜像已在测试机预拉；
#   2. 新增 BUILD_COMMIT → ENV：SysPingController 的 @Value("${BUILD_COMMIT:...}") 读它，
#      出现在 GET /lqg/sys/ping 的 buildCommit 里（accept 第 1 条断它与本地 HEAD 对得上）；
#   3. 去掉 snail-job 端口暴露（本项目不跑 SnailJob，见 application-test.yml 的 snail-job.enabled=false）。
FROM eclipse-temurin:21-jre-jammy

LABEL maintainer="lqg"

ARG BUILD_COMMIT=unknown
ENV BUILD_COMMIT=${BUILD_COMMIT} \
    TZ=Asia/Shanghai \
    LANG=C.UTF-8 \
    LC_ALL=C.UTF-8 \
    # 测试机可用内存 ~2.8G（还要跑 postgres/redis/minio）：堆按容器内存比例给
    JAVA_TOOL_OPTIONS="-Xms256m -XX:MaxRAMPercentage=60 -XX:+ExitOnOutOfMemoryError"
# 监听端口：application.yml 是 server.port=${LQG_API_PORT:8081}，容器里由 compose 显式给
# LQG_API_PORT=8080（发布到宿主时另用 127.0.0.1:${LQG_API_PORT}，8080/8081 在测试机上已被别人占）。
ENV LQG_API_PORT=8080

# logback-plus.xml 的 log.path=./logs 是相对 WORKDIR 的；先建好目录，免得容器里 root 写不进去
RUN mkdir -p /app/logs
WORKDIR /app

# 上游模板就是 ADD ./target/ruoyi-admin.jar ./app.jar —— 构建上下文 = 本模块目录
ADD ./target/ruoyi-admin.jar ./app.jar

EXPOSE 8080

# profile 由运行侧追加为程序参数（compose 的 command: ["--spring.profiles.active=test"]）
ENTRYPOINT ["java", "-Djava.security.egd=file:/dev/./urandom", "-jar", "/app/app.jar"]
