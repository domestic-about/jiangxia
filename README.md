# jiangxia-organoid · 类器官送检与样本管理系统 V1.0

甲方：湖北江夏实验室类器官研究中心　乙方：武汉市添达信息技术服务有限公司　合同编号：ZH-2026-LQG-SJ
来源：chores 任务 T007；售前工作区 `freelance/proposals/leiqiguanshiyanshi-songjianyuyangbengua/`（方案 v6、合同底稿、内部简报）。

**现在在哪一步**：①需求拆解（/xuqiu）已完成，闸门全绿（2026-09-21 重跑）。设计口径以 `doc/change-log.md` 顶部为准：CR-20260917-05 定小程序结构，CR-20260918-07 落甲方 9 条意见，CR-20260921-08 定小程序视觉方向 A「清爽卡片」（规范在 `doc/design-options/direction-a/`）。下一步进 ②执行（/zhixing D1）；给甲方的稿子与确认单是否发出、计费等仍见 `_manifest.json` 的 `needs_human`。

## 目录

```
_input/                      需求素材（只读）：7 份模板原件、模板逐字整理、微信答复、会议逐字稿、方案 v6、合同、售前内部稿
_manifest.json               给管家看的一页状态
doc/
  00-最终想法.md             一页：要做什么 / 明确不做 / 什么算成功 / 已知不确定
  requirements.yaml          需求点清单 94 条（87 在范围内 · 5 deferred · 2 dropped · 其中 8 条 clarify 带默认口径）
  authority/                 权威蓝图（结构化、稳定锚 id）：flows.yaml 16 条流 · field-ssot.yaml 15 表 26 字典 · ui-index.yaml 31 页（1 页已被取代）
    _rendered/               给人看的 markdown（authority_lint render 单向生成，别手改）
    _snapshot.json           基线快照
  _adr/                      10 份决策记录（0001-0002 Kevin 已拍；0003-0010 proposed 待过目）
  _oq.md                     16 条问题（2 条已关），每条都有先行口径
  change-log.md              变更记录（CR）
  api-contract.md            接口路径与形状约定（ticket 与验收断言共用）
  design-options/            七个关键页面各两案（9-17 晚定稿：首页内外部同一个样子、⑩ 表格页挂「我的 → 内部管理」且只读；其余五页 A）：README.md（选型表）· gallery.html（看图）· design-authority.md（设计权威抽取）· build_client_preview.py（生成甲方版设计稿）
  tickets/<ID>/prompt.md     43 张 ticket（机器可读头 + 任务书正文）
  phase-plan.yaml            8 个任务 D1-D8：顺序、目标、QA 范围、最终验收演练剧本
  confirmation/              给甲方过目的确认单：README（4 件要拍板 + 9 件要提前知道）+ 7 个板块 + 界面设计稿-v2.html（单文件 14 张图，可直接发微信；v1 是 9-17 已发出的那版）
  verify/                    验收执行器 + 确定性测试数据（见 verify/README.md）
  tools/                     gen_ddl_pg.py（SSOT → PostgreSQL DDL 与字典 seed）· 三个自检脚本
  lint-profile.yaml          公共字段集、菜单号段、Flyway 号段
code/                        代码（D1 的 SYS-BASE-001 起建）：RuoYi-Vue-Plus / plus-ui / miniapp / deploy
```

## 本机启动（dev）

前提：dev 容器在跑（`docker compose -f code/deploy/dev/docker-compose.yml up -d`，库 5433 / Redis 6380 / MinIO 9002 / Gotenberg 3010），`code/deploy/dev/.env` 存在（gitignored）。

```bash
# 后端 :8081（在 code/RuoYi-Vue-Plus 下）
cd code/RuoYi-Vue-Plus
mvn install -DskipTests -q            # 第一次、以及改了 ruoyi-lqg / ruoyi-common 等非 admin 模块之后
mvn spring-boot:run -pl ruoyi-admin   # 自动用 dev profile、自动读 code/deploy/dev/.env、自带 JVM 代理例外

# 工作台 :8082（代理到 8081）
cd code/plus-ui && pnpm dev
# 小程序 H5 :9100（直连 8081，mock 登录）
cd code/miniapp && pnpm dev:h5
```

- Maven 用本项目自己的本地仓库 `.m2repo`（`code/RuoYi-Vue-Plus/.mvn/maven.config` 指定）：本项目与其它若依项目都用 `org.dromara:ruoyi-*:5.5.3` 坐标，共用 `~/.m2` 会互相覆盖。IDE 里跑的话，把 Maven 的 Local repository 也指到 `.m2repo`。
- 一条命令起三个进程：`bash .tmp/local-env/local.sh up`（`down` / `status` / `reseed` 同理）。

## 常用命令（cwd = 本目录）

```bash
S=~/claude-config/skills
python3 $S/xuqiu/scripts/coverage_lint.py --write-back     # 需求覆盖：漏拆 / 镀金
python3 $S/xuqiu/scripts/authority_lint.py check           # 锚点悬空
python3 $S/xuqiu/scripts/adr_check.py                      # ADR / OQ 台账
python3 $S/zhixing/scripts/dag_lint.py                     # ticket 结构与断言形态
python3 $S/xuqiu/scripts/authority_lint.py show FLOW:F-EXT-01.step1   # 精确取一条权威

python3 doc/tools/check_req_sources.py        # REQ 的 text 是不是 source 那一行的原文
python3 doc/tools/check_authority_xref.py     # 蓝图内部互相对账（流程写的字段在不在、REQ 有没有被蓝图接住…）
python3 doc/tools/check_accept_sql.py         # 全部 accept 里的 SQL 在真实表结构上能不能解析（需要一个灌好表结构的库）
python3 doc/tools/gen_ddl_pg.py --table t_lqg_sample       # 从 SSOT 生成建表 DDL；--dicts 出字典 seed

# 改了权威（flows / field-ssot / ui-index / ADR 的 decision）之后：
python3 $S/xuqiu/scripts/authority_lint.py diff && python3 $S/xuqiu/scripts/authority_lint.py impact
# 处理完受影响的 ticket 再：authority_lint.py snapshot --force
```

## 项目约定（权威在 `doc/lint-profile.yaml` 与 ADR）

- 表 `t_lqg_<实体>` · 字典 `lqg_<名>` · 权限串 `lqg:<域>:<资源>:<动作>` · 包 `org.dromara.lqg.<域>` · 全部业务代码在 `ruoyi-modules/ruoyi-lqg`
- PostgreSQL：业务表 6 个公共字段，**不带 tenant_id、不带 del_unique**；唯一性一律部分唯一索引（`WHERE del_flag = '0'`）
- 外部人员只能调 `/mp/ext/**`，全部过 `ExtScopeService`，只返回 `Ext*Vo`（ADR-0004；契约测试在 `doc/verify/fixtures/java/`）
- 验收断言打在 `doc/verify/seed/` 的确定性数据上；**别改 seed 迁就实现**
