# zhixing 断线重连（三层）

> 2026-09-21 起，因会话经常被网络/服务中断打断而加。目标：**断了之后 10 分钟内自动接着跑**，
> 且不许出现「两个调度器同时驱动同一个 state.json」。

## 三层各管什么（别指望单层解决全部）

| 层 | 场景 | 机制 | 现状 |
|---|---|---|---|
| **L1 turn 级** | 单次模型请求被限流/超时/传输中断 | `@deepseek-ai/dsh-llm-retry`（已在 `dsh-base` 里挂载）。默认 normal mode：`EMPTY_RESPONSE / RATE_LIMIT / SERVER / TIMEOUT / TRANSPORT` 最多重试 5 次，退避 0.5→10s、10% 抖动。**在同一轮内重跑失败的那一步**，不会结束 turn | ✅ 已生效 |
| **L2 会话级** | 会话还活着但某个 turn 异常结束；想「10 分钟后自动再推一次」 | `@deepseek-ai/dsh-schedule` 的持久提醒：`schedule_create` 一个 10 分钟后的提醒，到期作为**普通 follow-up 消息**回到同一会话。提醒落在会话事件日志里，**重启后仍在** | ⚠️ 需启用 overlay（见下），且它只在**会话 live** 时交付；会话已关闭则保持逾期直到被恢复 |
| **L3 进程级** | DSH 进程死了 / 机器睡了 / GUI 关了 | `doc/waves/ops/watchdog.sh` + launchd 每 60s 判一次；心跳停了 ≥10 分钟就 `dsh --profile headless "<resume-prompt>"` 起一个**一次性 headless 会话**接着跑（headless 自带 subagent/jobs/bash，能跑完整 zhixing 循环；续跑靠 `state.json`，不需要上一个会话的上下文） | ✅ 脚本已落盘并验证四个分支；**待 Kevin 装 launchd** |

**诚实说清楚的三点**：
1. **会话自己睡着了叫不醒自己。** L3 必须由会话之外的进程（launchd）来叫，所以「10 分钟自动重试」这件事本质上是 L3 在兑现。
2. 心跳信号用的是**进程存活**，不是 turn 存活——等一个 40 分钟的 subagent 时主会话本来就没动静，拿 turn 当信号会把正常长任务误判成断线。turn 级抖动交给 L1。
3. 三层都拦不住「蓝图和实现一起错」「审美体感」「真机硬件长尾」，那是模型天花板，与断线无关。

## 一、怎么装（Kevin 跑，主会话沙箱写不了 `~/Library/LaunchAgents`）

```bash
cd /Users/wkui/Project/profile/project/freelance/projects/jiangxia-organoid

# ① 先手动冒烟一次 headless（这一步只有你能跑：dsh 要写 ~/.dsh/profiles/headless/）
dsh --profile headless "回答两个字：可以"
#    能打印结果就说明 headless 通道通；若这里就失败，先修它，别装看门狗

# ② 装 launchd（plist 默认 --dry-run，只判断不接手，安全）
cp doc/waves/ops/com.kevin.lqg-zhixing-watchdog.plist ~/Library/LaunchAgents/
launchctl bootstrap gui/$(id -u) ~/Library/LaunchAgents/com.kevin.lqg-zhixing-watchdog.plist

# ③ 观察判据对不对（会写出它「本来打算 resume」的日志，但不动手）
bash doc/waves/ops/watchdog.sh --dry-run
tail -5 doc/waves/ops/watchdog.log

# ④ 确认无误后，把 plist 里的 --dry-run 改成 --run 并重载，自动重连才真正生效
sed -i '' 's|<string>--dry-run</string>|<string>--run</string>|' ~/Library/LaunchAgents/com.kevin.lqg-zhixing-watchdog.plist
launchctl bootout gui/$(id -u)/com.kevin.lqg-zhixing-watchdog 2>/dev/null
launchctl bootstrap gui/$(id -u) ~/Library/LaunchAgents/com.kevin.lqg-zhixing-watchdog.plist
```

运维：

```bash
launchctl bootout gui/$(id -u)/com.kevin.lqg-zhixing-watchdog   # 卸载
touch doc/waves/ops/PAUSE                                       # 临时停（看门狗见哨兵即不接手）
rm doc/waves/ops/PAUSE                                          # 恢复
tail -f doc/waves/ops/watchdog.log                              # 看它做了什么
```

调参用环境变量（plist 的 `EnvironmentVariables` 里改）：`STALE_SECS`（默认 600 = 10 分钟）、
`MAX_PER_HOUR`（默认 6，一小时内最多自动 resume 几次）、`LOCK_STALE_SECS`（默认 3600）。

## 二、看门狗的判据链（任何一步命中就退出，绝不往下走）

| # | 条件 | 动作 |
|---|---|---|
| 1 | 没有 `state.json` | 不跑（还没 init） |
| 2 | 有 `PAUSE` 哨兵 | 不接手 —— 主会话明确停在等人 |
| 3 | 所有任务 `qa_passed`/`accepted` | all_done，不跑 |
| 4 | 有 ticket `escalated`，或某任务 `qa_failed` 且轮次 ≥2 | 不接手 —— 按 zhixing 规矩这是人的决策点 |
| 5 | `heartbeat` 比 `STALE_SECS` 新 | 有人活着，不抢（正常情况都走这里，不写日志免得刷屏） |
| 6 | 抢不到 `.resume-lock`（`mkdir` 原子锁） | 已有 resume 在跑，不重复起 |
| 7 | 最近一小时 `RESUME` 次数 ≥ `MAX_PER_HOUR` | 停手等人 —— 多半不是断线，是别的问题 |
| 8 | 否则 | `cd <ws> && dsh --profile headless "$(cat resume-prompt.txt)"` |

**防「双驱动」**：心跳文件（活会话持有）+ `mkdir` 原子锁（headless 运行期间持有）+ `PAUSE` 哨兵，
三道互不依赖。锁目录超过 `LOCK_STALE_SECS` 视为陈旧锁自动清理，免得一次硬崩把闸门焊死。

**人在场时的注意**：如果看门狗已经起了一个 headless 在跑，而你又手动打开了 GUI 会话，
那就真有两个驱动了。看门狗自己的 headless 会把自己的 pid 写进 `doc/waves/ops/.driver`，
进场前先看一眼这个文件与 `watchdog.log` 尾部；确认有 headless 在跑就先等它或先 `launchctl bootout` 再动手。

## 三、L1 调参（把「网络抖动」这一层加厚）

`llm-retry` 已在，策略由 provider 适配器的 `retryPolicy` 决定，而它可以从
`~/.dsh/settings.yaml` 覆盖且**热重载、不用重启**。想让请求级几乎不放弃（含较长断网）：

```yaml
# ~/.dsh/settings.yaml
llm-deepseek:
  retryPolicy:
    mode: always                    # 无尝试上限，直到成功/取消/插件释放
    backoff:
      initialDelayMs: 2000
      maxDelayMs: 60000             # 单次最长退避 1 分钟，够覆盖短时断网
      jitterRatio: 0.2
```

⚠️ `always` 每次重试都是一次计费请求；无人看管时更要注意。只想加厚不无限：用
`mode: normal` + 自定义 `maxAttempts` 与 `backoff`。
改完用 `dsh web --dump-config | grep -A6 retryPolicy` 核一下真的生效了。

## 四、L2 可选：会话内持久提醒

`@deepseek-ai/dsh-schedule` 的手册要求**在会话开始前**把 overlay 挂上（已在运行的会话拿不到提醒工具）：

```sh
# 仓库里的示例路径（npm 包里没随附），实际形状请先 dump 确认，别照抄猜测
dsh web --patch apps/cli/config/examples/schedule/cordis.yml
dsh web --dump-config | grep -i schedule     # 确认 row 真的进来了
```

挂上之后，可以让模型「10 分钟后提醒我继续」；到期它作为 follow-up 回到同一会话，
**会话关着就一直逾期，直到被恢复**——所以 L2 是 L1/L3 的补充，不是替代。

## 文件清单

| 文件 | 干什么 |
|---|---|
| `heartbeat.sh` | 会话存活心跳（每 30s 写 `heartbeat`）。zhixing 主会话起成一个受管后台作业 |
| `watchdog.sh` | 一轮判断；`--dry-run`（默认）/ `--run` |
| `resume-prompt.txt` | headless 接手时用的续跑指令（含端口/进程/只读区/停手规矩） |
| `com.kevin.lqg-zhixing-watchdog.plist` | launchd 用户级 agent，`StartInterval=60` |
| `PAUSE` | 哨兵：存在即不接手（主会话停手等人前必须写） |
| `heartbeat` / `watchdog.log` / `.resume-lock` / `.driver` | 运行期产物（已 gitignore） |
