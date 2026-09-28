# tianda 测试环境（`songjian.tianda.studio`）—— 侦察事实与部署约束

> 2026-09-22 实测（只读侦察）。**非机密**：只记资源 ID / IP / 端口 / 约定；
> 密钥一律在仓库外（`~/.aliyun/config.json`、`~/.tianda-secrets/`），**绝不进 git、绝不回显**。
> 权威来源：用户级 skill `~/.claude/skills/aliyun-deploy/`（阶段 0.5 选账号 → 08-accounts → 04-ecs-baota-nginx）
> 与 `~/.aliyun-accounts.md`。**改环境先读本文件与那份 skill，别信记忆。**

## 1. 账号与硬闸

- 账号 **tianda**（Kevin 个人）= AccountId **1826406494972500**，region `cn-hangzhou`。
- admin profile：**`tianda-admin`**（RAM `power-application-user`：ECS/RDS/VPC/DNS/OSS/计费全通，**仅无 RAM 管理**）。
  → 按 `reference_aliyun_accounts_registry` 的「个人项目简化」路线：**直接用 `tianda-admin` 当 deployer**，
  跳过「建 scoped `<project>-deployer`」（tianda 无 `ram:CreateUser`，会 NoPermission）。
- 🔴 **防串号硬闸（每次建资源前必跑）**：
  `aliyun sts GetCallerIdentity --profile tianda-admin` → `AccountId` 必须是 `1826406494972500`。
  **2026-09-22 已验通过**（Arn `acs:ram::1826406494972500:user/power-application-user`）。
- ⚠️ 本机 `aliyun configure list` 的 active profile 是 `tianda-admin`（带 `*`），而 dongjiaoshan 的
  `djs-prod` 也在本机 → **每条命令都显式 `--profile tianda-admin`**，别裸跑。
- ⚠️ **本机 VPN 透明代理会劫持 aliyun/oss API**：每条云调用前 `export no_proxy='*' NO_PROXY='*'`。
  不加会 `context deadline exceeded` / EOF（有的命令侥幸能过、建资源的必挂）。

## 2. 服务器

| 项 | 值 |
|---|---|
| ECS | `i-bp14wbcfphboybx9idug`（`iZbp14wbcfphboybx9idugZ`），**Running** |
| 公网 / 内网 | **118.178.109.11** / 172.23.196.130 |
| 规格 | `ecs.u2i-c1m2.xlarge` = **4 vCPU / 7.4 GB**（可用内存 ~2.9 GB，load 0.04） |
| 系统 | Alibaba Cloud Linux 3（kernel 5.10.134），PrePaid 到 2027-03-25 |
| 磁盘 | `/` 59G，**已用 43G（76%），剩 14G** |
| 安全组 | `sg-bp17g616h67b936e6b1b` |
| 软件 | docker 26.1.3 + compose v2.27.0（active）、nginx 1.28.1、**宝塔面板**（23127）、git 2.43.7、**JDK 17**（不是 21！） |
| docker 镜像加速 | `/etc/docker/daemon.json` → `registry-mirrors: ["https://docker.1ms.run"]` |
| 上机方式 | **SSH `root@118.178.109.11` 已配置免密可用**（BatchMode 实测通过）；skill 默认走 ECS RunCommand，文件传输只能 SSH/rsync |
| 仓库远端 | **本仓库没有 git remote** → 代码上机必须 rsync/scp（不能让 box 自己 clone） |

### 已占用的端口（别碰）

`80/443/888/8881` = 宿主机 nginx（宝塔）；`23127` = 宝塔面板；`8080` = `gz-ruoyi-admin-staging`
（guzi 项目后端，**公网开放**）；`3306` = `gz-mysql-staging`（**公网开放**）；`3307`、`6380` = `tianda-mysql`/`tianda-redis`
（127.0.0.1）；`8081` = 某个 java（127.0.0.1）；`3000` = next-server；`22` sshd；`25` smtp。

**空闲可用**：`8082 8083 8090 9000 9001 15432 16379 19000`（本项目的 test compose 建议
postgres/redis/minio 一律 **只绑 127.0.0.1** 且用非默认宿主端口，避免与 guzi/tianda 的容器撞）。

### 🔴 侦察发现（要 Kevin 决断，不是本项目的改动）

1. **安全组把 `5432 / 3306 / 8881 / 8080` 对 `0.0.0.0/0` 开着**；实测 **3306 从公网真的连得上**
   （guzi 的 mysql）。这是既有暴露面，与本项目无关但值得收（建议关掉 3306/5432/8080，只留 80/443/22/宝塔端口）。
   **本项目的 test compose 不能依赖那条 5432 规则** —— 库只许绑 127.0.0.1。
2. **磁盘空间的真实情况（2026-09-22 已 prune 悬空镜像，Kevin 授权）**：
   prune 前 `docker system df` = Images 58 个 / 10.77GB（可回收 9.36GB）、**Build Cache 11.67GB（可回收 11.67GB）**。
   跑 `docker image prune -f` 后 Images 降到 **5 个 / 1.851GB（可回收 0）**，但 **`df` 前后都是 43G 已用 / 14G 可用** ——
   那 9.36GB 的悬空层同时被 docker 记在 build cache 名下，而 **Build Cache 涨到 20.46GB（可回收 20.46GB，active 0）**。
   → **真正能腾空间的是 `docker builder prune -f`（≈20GB），不是 image prune**。本次未做（Kevin 只授权了悬空镜像；
   且那是与 guzi 共用的构建缓存，清了只影响下次构建速度）。构建若撑爆磁盘再单独问。
   在跑的容器全部未受影响（guzi ×3 + tianda ×2 状态与时长不变）。
3. **JDK 17 而非 21** → 后端**必须在容器里跑**，基础镜像用 JRE **21**（`eclipse-temurin:21-jre` 之类），
   不要用宿主机 java。

## 3. 域名与备案

- DNS 在阿里云云解析（`dns9/dns10.hichina.com`），zone = `tianda.studio`。
- **2026-09-22 已新增**：`songjian` A → `118.178.109.11`，TTL 600，状态 ENABLE
  （RecordId `2102294096156393472`）。此前 zone 里只有 `@ / www / api / admin / guzi / guzi-admin / dongke`。
- **备案已通**：`guzi.tianda.studio`、`admin.tianda.studio` 在 80 端口实测 HTTP 200 → 该域名已备案可服务。
- ⚠️ 本机 `dig` 返回 `198.18.x.x` 是**代理的 fake-IP**（198.18.0.0/15 保留段），**不可信**；
  要么用 `aliyun alidns DescribeDomainRecords`，要么在 box 上查。

## 4. 🔴 本机 `nc` 完全不可用（会打红 accept，必须知道）

本机有 7 个 utun + `all_proxy=socks5://…`，**透明代理把所有 TCP 连接都「接」下来**：

| 目标 | 本机 `nc -z` 结果 |
|---|---|
| `118.178.109.11:5432 / 6379 / 9000` | 「可达」（假） |
| `1.1.1.1:59999`（绝不可能开） | **「可达」（假）← 对照组** |
| `118.178.109.11:1` / `:34567` | 「可达」（假） |

而**从 box 侧做发夹探针（打自己的公网 IP）是可判别的**：

| 端口 | box→自身公网 IP | 说明 |
|---|---|---|
| 5432 / 6379 / 9000 | **不可达** | 没绑公网 ✅（正是 accept 要的） |
| 3306 / 80 / 443 / 8080 | **可达** | 阳性对照，证明探针真的在区分 |

→ **`SYS-STAGING-001` accept 第 2 条的 `! nc -z -w 3 "${HOST}" 5432` 在本机必然假红**
（不是实现问题）。替代证据（三件一起，可判别）：
① box 侧发夹探针 + 阳性对照；② `aliyun ecs DescribeSecurityGroupAttribute` 入方向清单；
③ box 上 `ss -lntp` 看 bind 地址是 `127.0.0.1` 还是 `0.0.0.0`。
同类断言受害者还有 `SYS-PROD-001`（也用 `nc`）。已记 issue（`type: harness`）。
**不要**为了让 accept 变绿去改 ① 侧票面。

## 4.5 🔴🔴 架构不匹配：本机是 arm64，服务器是 x86_64（部署方案的决定性约束）

| | 架构 | docker |
|---|---|---|
| 本开发机（Mac） | **linux/aarch64**（Apple Silicon） | 24.0.2 |
| 测试服务器 | **x86_64**（`uname -m` 实测） | 26.1.3 + compose v2.27 |

→ **本地 `docker build` 出来的镜像是 arm64，在服务器上跑不起来**（`exec format error`）。
→ 也**不能**用 `docker save | ssh | docker load` 那条「本地构建→推到测试机」的捷径（除非 `--platform linux/amd64`，
   而多阶段 maven 构建走 qemu 模拟会慢到不可接受）。

**可行的三条路，按推荐排序：**

1. **★ 推荐：jar 本地构建（jar 与架构无关）+ 服务器原生建镜像。**
   本机 maven 打 jar（已是既有流程）→ rsync jar + `Dockerfile`（多阶段那份保留给 CI/amd64）→
   在服务器 `docker build`（原生 amd64，只 COPY jar，秒级）。基础镜像 `eclipse-temurin:21-jre-jammy`
   **已在服务器就位**（286MB）。
2. **源码上机 + 服务器原生多阶段构建**：rsync 源码 → 服务器 `docker build`（多阶段 maven）。
   最贴票面字面（票面 §2 要求「多阶段：maven 构建 → JRE 运行」），但要在服务器拉
   `maven:3.9-eclipse-temurin-21`（~500MB）并下全量 maven 依赖（首次 5–20 min）。
   `.mvn-settings.xml` 已指向 `https://maven.aliyun.com/repository/public`（国内可达）。
3. **本地跨架构构建**：`docker buildx build --platform linux/amd64`（qemu）——慢，仅在前两条都不通时用。

**已在服务器预拉就位的基础镜像**（2026-09-22）：`eclipse-temurin:21-jre-jammy` 286MB、
`postgres:16-alpine` 294MB、`redis:7-alpine` 39MB、`minio/minio:latest` 175MB。

> MinIO 镜像的坑：`docker.1ms.run` 这个加速源**拉不到 `minio/minio`**（会回落到 `registry-1.docker.io` 然后超时）。
> 实测可用的是 `docker.1panel.live/minio/minio:latest` → 拉下来后 `docker tag` 回 `minio/minio:latest`。
> 备选源：`docker.m.daocloud.io` / `dockerpull.org` 都失败。**别再用镜像站的 library 前缀规律去猜**。

## 5. 本项目在测试机上的约定（SYS-STAGING-001 落地时遵守）

- 后端**只在容器里跑**（JRE 21，宿主机只有 JDK 17）；宿主 8080/8081 已被别人占，别用。
- **镜像必须在服务器上构建**（宿主 arch = x86_64，本机 = arm64，见 §4.5）。
- 宿主 nginx（宝塔）终止 TLS：`songjian.tianda.studio` → 反代到 compose 里那个 nginx 或后端的
  **127.0.0.1 端口**。宿主 80/443 不能给容器抢占。
- postgres（库 `lqg_test`）/ redis / minio 一律**只发布到 127.0.0.1**，且宿主端口避开
  5432/6379/9000 的「已占」邻居（用 15432/16379/19000 之类更稳）。
- 证书：优先宝塔/Let's Encrypt 签 `songjian.tianda.studio`（80 需可达，已确认）。
- 测试数据：只灌 `doc/verify/seed/`（`reseed.sh` 拒绝非 dev/test 库名，所以库名必须含 `test`）。
- 不在测试机放任何真实数据；mock 登录开着（ADR-0008 允许 test）。
- 收尾：不留长进程；`docker compose down` 或明示留给谁；磁盘别撑爆（先 prune 悬空镜像）。

## 6. 部署方案（SYS-STAGING-001 照此落地；已核过的前提）

### 6.1 已就位的前提

| 前提 | 状态 |
|---|---|
| DNS `songjian.tianda.studio` → 118.178.109.11 | ✅ 已建，**从 box 查已生效** |
| 备案（`tianda.studio` 已备案，guzi/admin 在 80 实测 200） | ✅ |
| 宿主 nginx/宝塔可建站 + 签 LE 证书 | ✅ 面板端口 23127、入口路径见 `~/.tianda-secrets/bt-panel.env`；`/www/server/panel/vhost/cert/<域名>` 是既有证书目录 |
| 基础镜像 | ✅ temurin 21-jre / postgres 16-alpine / redis 7-alpine / minio（见 §4.5） |
| SSH 免密 | ✅ `root@118.178.109.11` |
| 仓库远端 | ❌ 没有 → 代码/产物只能 rsync/scp |

### 6.2 分层与端口（宿主 80/443 归宝塔，容器不许抢）

```
浏览器/小程序 ──HTTPS 443──> 宿主 nginx(宝塔, songjian.tianda.studio)
                                 ├── /            → 静态: plus-ui dist（SPA 回退 try_files → /index.html）
                                 └── /prod-api/   → 127.0.0.1:<BACKEND>  （后端只在容器里，不发布公网）
compose（全部只绑 127.0.0.1）:
  postgres:16-alpine  127.0.0.1:15432 → 5432   库名必须含 test（reseed.sh 护栏）
  redis:7-alpine      127.0.0.1:16379 → 6379
  minio/minio         127.0.0.1:19000 → 9000   控制台 19001 → 9001
  backend(JRE21)      127.0.0.1:<BACKEND> → 8080  --spring.profiles.active=test
```
`<BACKEND>` 取宿主空闲端口（`8082 / 8083 / 8090` 均空；**不要**用 8080/8081 —— 别人占着）。

### 6.3 部署目录与「不要在宝塔站根目录里跑 git」

- **compose/Dockerfile/.env 放 `/opt/lqg-test/`**（自建，root 所有）——别放 `/www/wwwroot/<域名>/`：
  宝塔 AddSite 会把站根 chown 成 `www`，root 在那里跑 git/docker 会撞 dirty-ownership（skill gotchas #7）。
- 站根只放 **plus-ui 的 dist**（`build:prod` 产物，rsync 上去即可）。
- `.env` 由本地 secrets 生成后 `scp`（chmod 600），**绝不进 git**；仓库里只留 `.env.example`。

### 6.4 长操作必须分离执行（本机 SSH 长连接会被掐）

本机走透明代理，**长 SSH 命令会被 "Connection closed by remote host" 掐断**（已踩两次：
`docker pull` 轮询、前面的 maven 拉取）。规矩：
```bash
# 在 box 上分离跑，输出落文件；然后用短命令轮询
ssh root@HOST 'nohup bash /opt/lqg-test/step.sh > /opt/lqg-test/step.log 2>&1 & echo started'
ssh root@HOST 'tail -5 /opt/lqg-test/step.log; ls /opt/lqg-test/step.done 2>/dev/null'
```
（`docker build` / `docker pull` / maven 下载 / 证书签发 都属长操作。）

### 6.5 accept 第 2 条的替代证据（本机 `nc` 不可用，见 §4）

按 §4 的三件套取证并**在报告里如实标注替换**：box 侧发夹探针 + 阳性对照、安全组入方向清单、`ss -lntp` bind 地址。
**不要**为了让断言变绿去改 ① 侧票面。
