---
ticket: SYS-RELEASE-001
track: SYS
phase: D8
size: M
req_refs:
  - REQ-SYS-009
  - REQ-AUTH-001
depends_on:
  - SYS-PROD-001
  - DOC-MP-002
  - OCR-MP-001
touches:
  - code/miniapp/src/pages/legal/**
  - code/miniapp/src/manifest.json
  - code/miniapp/src/env/**
  - code/miniapp/project.config.json
  - doc/ops/小程序发布清单.md
adr_refs: []
blueprint_refs:
  - FLOW:F-OPS-02.step1
  - FLOW:F-OPS-02.step2
  - FLOW:F-OPS-02.step3
  - UI:mp.login
accept:
  - name: "生产构建：指向生产域名、没有调试登录、两份协议正文不是占位、隐私相关接口的使用与声明对得上"
    form: API
    run: |-
      cd code/miniapp && rm -rf dist/build/mp-weixin && pnpm build:mp-weixin --mode production >/dev/null &&
      ! grep -rq 'mock:ext' dist/build/mp-weixin && ! grep -rqE '127\.0\.0\.1|localhost|192\.168\.' dist/build/mp-weixin/common dist/build/mp-weixin/pages 2>/dev/null &&
      grep -rq "$(sed -n 's#^LQG_API_BASE=https://\([^/]*\).*#\1#p' ../../doc/verify/verify.prod.env)" dist/build/mp-weixin &&
      test "$(wc -m < src/pages/legal/privacy.vue)" -ge 800 && grep -q '住院号' src/pages/legal/privacy.vue && grep -q '识别' src/pages/legal/privacy.vue && grep -q '删除' src/pages/legal/privacy.vue &&
      ! grep -rnE 'TODO|占位|lorem' src/pages/legal &&
      jq -e '(.["mp-weixin"].__usePrivacyCheck__ == true)' src/manifest.json &&
      ! grep -rnE 'getLocation|getClipboardData|chooseAddress|getWeRunData' src --include=*.vue --include=*.ts | grep -q .
    counterfeit: |-
      生产包里还指着测试环境的域名 → 第 4 段红（线上用户的数据会写进测试库）。
      隐私政策是从别的项目抄的模板、没提住院号和拍照识别 → grep 红；审核和甲方都会问。
      代码里偷偷用了位置 / 剪贴板接口却没声明 → 最后一段红。
  - name: "发布清单逐项闭环：认证、类目、隐私指引、四类合法域名、体验版回归、提审、发布、线上冒烟；线上小程序能走通一次真实登录"
    form: STATE
    run: |-
      L=doc/ops/小程序发布清单.md && test -f "${L}" &&
      test "$(grep -cE '^- \[x\] ' "${L}")" -ge 10 && ! grep -qE '^- \[ \] ' "${L}" &&
      grep -q 'request' "${L}" && grep -q 'uploadFile' "${L}" && grep -q 'downloadFile' "${L}" &&
      grep -qE '线上版本[:：][[:space:]]*[0-9]+\.[0-9]+\.[0-9]+' "${L}" &&
      export LQG_VERIFY_ENV_FILE=doc/verify/verify.prod.env &&
      bash doc/verify/api.sh --as admin GET '/lqg/auth/ext-user/list?pageSize=5' | jq -e '.code==200 and .total>=1'
    counterfeit: |-
      清单里还有没打勾的项就宣布上线 → 红。
      只发了体验版、没提审发布 → 没有线上版本号红。
      「线上冒烟」没人真的用微信登录过 → 生产库里一个外部用户都没有，最后一段 total < 1 红。这条断的是真实登录链路（真实的 code2session 与手机号接口）在生产跑通过，mock 在生产是关的，造不了假。
---

# SYS-RELEASE-001 · 小程序提审上线：隐私保护指引、用户协议与隐私政策正文、合法域名、体验版回归、提审发布

## §0 状态自检（实施前必过，不过就 STOP 报 Kevin）

- [ ] 当前分支符合调度器注入的期望（`track/SYS` worktree）——不写死 `feature/dayN`
- [ ] `depends_on` 全部 done：**SYS-PROD-001**、**DOC-MP-002**、**OCR-MP-001**
- [ ] 扫 `doc/change-log.md`：涉及本 ticket 的 CR **以 CR 为准**（CR 覆盖本文）
- [ ] 逐条取权威（别全文读蓝图）：`python3 ~/claude-config/skills/xuqiu/scripts/authority_lint.py show <锚>`，锚见上方 `blueprint_refs`；接口形状见 `doc/api-contract.md`
- [ ] 必读：
  - 合同第四条 1(3)：**甲方**以其主体办理小程序的注册、认证与备案（300 元/年认证费甲方直付）。**非开发前置**：甲方给出 appid 并把乙方加为开发者——没到位本张 blocked
  - `doc/_oq.md` OQ-5：手机号快速验证组件需要已认证的非个人主体，微信按次计费（有免费额度）；OQ-6：小程序叫什么名由甲方注册时定
  - 栈包 gotchas §6.5：上传只走本地构建 + miniprogram-ci
- [ ] 口径复述（本张最容易做反的）：
  1. 隐私保护指引要如实声明三样：手机号（登录与身份判定）、相机与相册（拍照识别样本信息）、剪贴板 / 位置等**没用到的不要勾**。代码里用到而没声明 → 审核打回或接口直接不可用。
  2. 表单里有供体姓名、住院号，审核可能追问资质。备一段说明：本小程序是实验室与合作单位之间的 B 端送检工具，不面向患者，不提供诊疗服务。类目选工具类 / 办公类，别选医疗。
  3. 合法域名四类都要配：request、uploadFile（识别传图）、downloadFile（文档下载——DOC-MP-002 报告里写了走哪个域名）。
  4. 生产构建里不能有调试登录入口（SYS-MP-001 已断言过，换了 appid 之后再断一次）。

## 1 背景与口径

合同第一条第 2 款：部署上线。小程序上线 = 以甲方主体审核通过并发布。

## 2 实现要点

- `pages/legal/agreement`、`pages/legal/privacy`：两份正文（大白话，各一屏多）。隐私政策写清：收集什么（手机号、送检信息里的供体姓名与住院号、拍照识别的图片）、为什么、存在哪（境内云服务器，加密存储）、谁能看（实验室内部人员；同组合作单位成员）、图片识别完即删、怎么联系实验室删除数据。
  主体名称、联系方式留成配置（甲方给）。登录页的勾选框链接到这两页。
- `env/.env.production`：生产 API 域名、`VITE_MOCK_LOGIN=0`；appid 取环境变量注入 `project.config.json` / `manifest.json`，不写死。
- `doc/ops/小程序发布清单.md`（逐项打勾并留证）：主体认证完成 → 类目 → 隐私保护指引三项 → 四类合法域名 → 体验版回归（外部送检一条龙 / 内部四张表 / 文档预览与下载 / 识别失败不挡手填）→ 提审说明文字 → 提审 → 审核结果 → 发布 → 线上冒烟。
- 审核被打回：按意见改，改动与回复记在清单里。

## 3 边界（明确不做）

- 不代办主体认证与备案（甲方办，乙方给材料清单并协助）
- 不做小程序订阅消息模板申请（没有推送功能）
- 不做小程序码物料设计

## 4 完工报告要求

1. 发布清单全文（每项带截图或链接）
2. 线上版本号与发布时间
3. **改了哪些文件**（含 Flyway 文件名与取号依据、新增的类 / 页面 / 接口清单）
4. **accept 逐条 ✅ / ❌ + 关键输出**（贴命令输出，不贴「已通过」三个字）
5. **遗留与 raise**：越出 `touches` 的改动、与 `doc/api-contract.md` 不一致的地方、没把握的口径
6. 验证用的后端 / 前端长进程已关，或明示留给谁
