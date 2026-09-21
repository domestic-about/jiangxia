---
ticket: SYS-BACKUP-001
track: SYS
phase: D8
size: M
req_refs:
  - REQ-SYS-004
  - REQ-SYS-006
depends_on:
  - SYS-PROD-001
touches:
  - code/deploy/prod/backup/**
  - doc/ops/**
adr_refs:
  - ADR-0002
  - ADR-0006
blueprint_refs:
  - FLOW:F-OPS-01.step2
  - FLOW:F-OPS-01.step3
  - FLOW:F-OPS-01.step4
accept:
  - name: "最新备份在 26 小时内且非空；恢复演练真的做过且逐表行数与生产一致；备份前缀配了 30 天过期"
    form: DATA
    run: |-
      ssh "$(sed -n 's/^LQG_PROD_SSH=//p' doc/verify/verify.prod.env)" 'cd /opt/lqg/backup && ./backup.sh --verify-latest' | tee /tmp/lqg-backup-latest.txt &&
      awk '/size_bytes/{s=$2} /age_hours/{a=$2} END{exit !(s>10240 && a<26)}' /tmp/lqg-backup-latest.txt &&
      R="$(ls doc/ops/restore-drill-*.md | sort | tail -1)" && test -n "${R}" &&
      test "$(grep -cE '^\| t_lqg_[a-z_]+ \| [0-9]+ \| [0-9]+ \| 一致 \|' "${R}")" -ge 15 &&
      ! grep -qE '\| 不一致 \|' "${R}" && grep -q '结论：备份可恢复' "${R}" &&
      ssh "$(sed -n 's/^LQG_PROD_SSH=//p' doc/verify/verify.prod.env)" 'ossutil lifecycle --method get oss://$LQG_OSS_BUCKET 2>/dev/null' | grep -A6 'backup/' | grep -qE '<Days>30</Days>|"Days": *30'
    counterfeit: |-
      cron 写了但脚本因为路径问题每天都在失败、没人知道 → 最新备份超过 26 小时红。
      备份了个空文件（pg_dump 连不上库时也会产出一个几十字节的文件）→ 大小断言红。
      「恢复演练」只是看了一眼备份文件在不在 → 演练报告里没有逐表两列数字，第 4 段红。两侧不同源：一侧是恢复出来的临时库，一侧是生产库。
      业务表 15 张，少核了几张 → 计数不足红。
  - name: "全量导出脚本可用：清单里的行数与库一致、加密列导出的是明文、文件校验和对得上；导出包没有被留在服务器或仓库里"
    form: STATE
    run: |-
      M="$(ls doc/ops/export-manifest-*.txt | sort | tail -1)" && test -n "${M}" &&
      grep -qE '^t_lqg_sample[[:space:]]+[0-9]+' "${M}" && grep -qE '^oss_files[[:space:]]+[0-9]+' "${M}" && grep -q 'donor_name: plaintext-verified' "${M}" &&
      grep -q 'export package removed: yes' "${M}" &&
      ! git ls-files | grep -E '\.dump(\.gz)?$|export-.*\.(zip|tar\.gz)$' | grep -q . &&
      ssh "$(sed -n 's/^LQG_PROD_SSH=//p' doc/verify/verify.prod.env)" 'ls /opt/lqg/export 2>/dev/null | wc -l' | grep -qx '0' &&
      test -f doc/ops/运维手册.md && grep -q '恢复' doc/ops/运维手册.md && grep -q '全量导出' doc/ops/运维手册.md
    counterfeit: |-
      导出直接 COPY 表到 CSV → 供体姓名一列是 Base64 密文，甲方拿到也读不了；清单里没有 plaintext-verified 红。
      导出包演示完忘了删、留在服务器上 → 倒数第 2 段不为 0 红：那是一份解了密的全量供体数据。
      只导了库、没导 OSS 文件 → 清单里没有 oss_files 红。
---

# SYS-BACKUP-001 · 每日备份到 OSS、一次真实的恢复演练、全量数据导出脚本

## §0 状态自检（实施前必过，不过就 STOP 报 Kevin）

- [ ] 当前分支符合调度器注入的期望（`track/SYS` worktree）——不写死 `feature/dayN`
- [ ] `depends_on` 全部 done：**SYS-PROD-001**
- [ ] 扫 `doc/change-log.md`：涉及本 ticket 的 CR **以 CR 为准**（CR 覆盖本文）
- [ ] 逐条取权威（别全文读蓝图）：`python3 ~/claude-config/skills/xuqiu/scripts/authority_lint.py show <锚>`，锚见上方 `blueprint_refs`；接口形状见 `doc/api-contract.md`
- [ ] 必读：
  - **ADR-0002 的后果**：数据库和应用同机，服务器一坏两样一起坏——所以备份 + **做过一次真实恢复**是上线前的硬要求，不是可选项
  - 合同第二条第 3 款：甲方可随时要求全量导出数据；合作终止时协助导出与迁移
  - **ADR-0006**（全文在 `doc/_adr/`，`authority_lint.py show ADR-0006` 取结构化口径）
- [ ] 口径复述（本张最容易做反的）：
  1. **没恢复过的备份不算备份**。演练必须真的把最新备份恢复到一个空库，逐表核对行数。
  2. 备份文件放 OSS 私有桶的 `backup/` 前缀，保留 30 天（生命周期规则），**和业务文件不同前缀**。
  3. 全量导出里的加密列要**解密后**导出（给甲方的数据必须是读得懂的）——所以导出包本身是敏感物，脚本只产出到本地指定目录、不自动上传、用完即删。
  4. OSS 里的文件（照片、附件、渲染产物）也是数据的一部分：导出包要含文件清单与打包。

## 1 背景与口径

合同附件第 14 行：数据库每日自动备份。方案 v6 L133：数据库每日自动备份到数据存储，文件与图片同步留存。

## 2 实现要点

- `code/deploy/prod/backup/backup.sh`（cron 每天 03:00）：`docker exec` 里 `pg_dump -Fc` → gzip → `ossutil cp` 到 `backup/lqg-YYYYMMDD-HHMM.dump.gz` → 校验对象存在且大小 > 0 → 写一行日志；任何一步失败 → 往 `LQG_ALERT_WEBHOOK` 告警并非零退出。
  `backup.sh --verify-latest`：打印最新备份的对象名、大小、距今小时数；超过 26 小时 → 非零退出。
- OSS 生命周期：`backup/` 前缀 30 天过期（用 ossutil 配，命令写进部署手册）。
- `restore-drill.sh`：拉最新备份 → 起一个临时 postgres 容器（不占生产端口）→ `pg_restore` → 对每张 `t_lqg_*` 与 `sys_user / sys_oss` 比对「演练库行数 vs 生产库行数」→ 输出 `doc/ops/restore-drill-YYYYMMDD.md`（逐表两列数字 + 结论）→ 删临时容器。
- `export-all.sh <输出目录>`：① 数据库 dump；② 每张业务表一个 CSV（通过后端一个仅超管可调、仅本机可达的导出端点，或一个小 Java 命令行读库解密——加密列输出明文）；③ OSS `lqg/` 前缀全量同步到本地；④ `MANIFEST.txt`（各表行数、文件数、每个文件的 sha256）。
- `doc/ops/运维手册.md`：备份在哪、怎么恢复、怎么导出、告警长什么样、每月该看一眼什么。

## 3 边界（明确不做）

- 不做增量备份 / PITR
- 不做异地多副本（OSS 本身的冗余够用）
- 不给甲方做自助导出界面（合同写的是「要求乙方导出」）

## 4 完工报告要求

1. 最近三天的备份对象清单
2. 恢复演练报告全文
3. 一次全量导出的 MANIFEST.txt（导出包本身演示完即删，报告里写明已删）
4. **改了哪些文件**（含 Flyway 文件名与取号依据、新增的类 / 页面 / 接口清单）
5. **accept 逐条 ✅ / ❌ + 关键输出**（贴命令输出，不贴「已通过」三个字）
6. **遗留与 raise**：越出 `touches` 的改动、与 `doc/api-contract.md` 不一致的地方、没把握的口径
7. 验证用的后端 / 前端长进程已关，或明示留给谁
