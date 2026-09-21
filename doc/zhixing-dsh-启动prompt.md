# /zhixing 启动 prompt · DeepSeek Harness

> 用法：在 DSH 里新开一个会话，cwd 设为项目根，把下面两条分隔线之间的整段粘进去。
> 断了再续：新会话粘同一段即可，第 3 步会按 `doc/waves/state.json` 从断点接着跑，不会重新 init。

---

/zhixing

在这个项目上执行 ②执行：从 D1 开始，按任务串行跑到 all_done。

## 1. 位置

- 工作区根 `<ws>`：`/Users/wkui/Project/profile/project/freelance/projects/jiangxia-organoid`（标准布局，`doc/` 下是 requirements、authority、tickets、phase-plan）。所有命令在这一层跑。
- skill 实体：`~/claude-config/skills/zhixing`（DSH 从 `~/.agents/skills/zhixing` 软链加载，是同一份）。脚本一律写绝对路径 `~/claude-config/skills/zhixing/scripts/…`、`~/claude-config/skills/xuqiu/scripts/…`。

## 2. 开工前先读，按顺序

1. `~/claude-config/skills/zhixing/SKILL.md` 全文，`~/claude-config/skills/zhixing/templates/task-loop.md` 全文。主会话照 task-loop 跑，impl 与 QA 的 prompt 照 `templates/impl-subagent-prompt.md`、`templates/qa-subagent-prompt.md` 拼。
2. `README.md`（项目约定与常用命令）、`_manifest.json`。
3. `doc/change-log.md` 最上面四条：CR-20260921-08、CR-20260918-07、CR-20260917-06、CR-20260917-05。**当前口径由这四条定，CR 覆盖 ticket 正文。**
4. `doc/phase-plan.yaml` 顶部的「非开发前置」。

## 3. 开工检查，任一不过就停下报 Kevin，不要自己修需求层

```bash
cd /Users/wkui/Project/profile/project/freelance/projects/jiangxia-organoid
git status --short                     # 必须为空。不为空 = 还有没提交的拆解产物，worktree 与分支里看不到，停
python3 ~/claude-config/skills/zhixing/scripts/dag_lint.py            # 应为「无结构性错误」；CRYO-MP-001、CRYO-REMIND-001 两条 STATE 强度 WARNING 已知，不阻塞
python3 ~/claude-config/skills/xuqiu/scripts/coverage_lint.py --write-back   # 87/87
python3 ~/claude-config/skills/xuqiu/scripts/authority_lint.py check
python3 ~/claude-config/skills/xuqiu/scripts/authority_lint.py diff   # 应为「权威无变更」
python3 doc/tools/check_req_sources.py && python3 doc/tools/check_authority_xref.py
test -f doc/waves/state.json && echo RESUME || python3 ~/claude-config/skills/zhixing/scripts/task_state.py init
```

`state.json` 已存在就是续跑：不 init，直接 `task_state.py next`。

## 4. 分支与 git

- 从 `main` 切 `integration`，每个任务开 `task/D<N>`，QA 门绿了合进 `integration`。串行档，不开多轨 worktree。
- 可以在 `integration` 与 `task/D<N>` 上 commit。**不 push**（仓库没有远端），**不合进 `main`**，不删分支，不改写已有历史。all_done 收尾清单里的「合分支 push」只做到合进 integration，push 与部署留给 Kevin。

## 5. 项目特有的约束

- **栈**：后端 RuoYi-Vue-Plus（JDK 21、Maven）+ PostgreSQL + Flyway + Redis，业务代码全在 `ruoyi-modules/ruoyi-lqg`；工作台 plus-ui；小程序 unibest 模板（uni-app + Vue 3 + wot-design-uni 1.14 + pinia）。`code/` 现在是空的，D1 的 SYS-BASE-001 起建。
- **同栈参照**：`/Users/wkui/Project/profile/project/freelance/projects/dongjiaoshan/code/main/`（RuoYi-Vue-Plus、plus-ui、miniapp 同一套）。可以读它学写法与配置结构，**只读不改**，不拷它的业务代码、表、字典、密钥与环境配置。
- **小程序视觉 = 方向 A**（CR-20260921-08）：唯一规范是 `doc/design-options/direction-a/落地规范.md`；SYS-MP-001 把同目录的 `tokens.scss`、`components.scss` **原样**拷到 `code/miniapp/src/style/`。图廊 `doc/design-options/gallery.html` 各帧画于方向 A 之前，**只取内容块与排布，不取它的无阴影小圆角外观**。网页工作台沿用 plus-ui 默认浅色加主色覆盖，不套方向 A。
- **口径来源**：每页有什么内容块以 `doc/authority/ui-index.yaml` 为准；取权威用 `python3 ~/claude-config/skills/xuqiu/scripts/authority_lint.py show <锚>`，别全文读蓝图。接口形状以 `doc/api-contract.md` 为准，要改先改契约再改断言。
- **测试数据**：验收断言打在 `doc/verify/seed/` 的确定性数据上，**别改 seed 迁就实现**；身份表与执行器见 `doc/verify/README.md`。
- **只读**：`_input/`、`doc/requirements.yaml` 的 text、`doc/authority/*.yaml`、`doc/change-log.md`。实现中发现口径不对，走 `blueprint_changed` 或 issue，停下等 Kevin，不要自己改权威。
- **端口**：本机 8080、5432、6379 留给 Kevin 日常用。后端、数据库、Redis 另开端口（比如 8081 起、5433、6380）；QA 分片各自独占端口与运行根，隔不开就串行分片。起过的长进程用完关掉。
- **小程序端侧（L2）**：微信开发者工具在 `/Applications/wechatwebdevtools.app`，命令行 `/Applications/wechatwebdevtools.app/Contents/MacOS/cli`。开发者工具需要登录或人工扫码而跑不通时，先用 H5 构建加 Playwright 截图覆盖能覆盖的项，开发者工具与真机那几项记 blocked 集中上报，不算 QA 失败。
- **非开发前置**卡住的项照 phase-plan 顶部处理：D5 的 OCR-SPIKE-001 等甲方照片、SYS-STAGING-001 等测试服务器与域名，D8 的 SYS-PROD-001、SYS-RELEASE-001 等云资源、备案与甲方 appid。到了就记 blocked 或 escalated，继续跑其余 ticket，all_done 时集中列出，不要伪造环境去过验收。

## 6. 运行纪律

- 照 task-loop 的连续执行契约：处理完一个 action 立刻重跑 `next`，直到 all_done。不要问「要我继续吗」，不要做阶段性总结。
- 只在这些时候停下来输出给 Kevin：`exception`（QA 门连红 2 轮）、`blocked`、`blueprint_changed`、`inconsistent`、qa_gate 带 S0/S1 未决、all_done。`rearm` 只能 Kevin 跑，主会话不许自己调。
- 判红只看严重度：S0/S1 拦门，S2/S3 记台账推到 all_done，返工只修 S0/S1。
- QA subagent 必须是全新上下文，不给它看实现方的完工报告与解释。
- `_manifest.json` 按 zhixing 的格式维护（`skill: zhixing`）。
- 汇报用中文，结论先说。

---
