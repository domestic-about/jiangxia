---
ticket: OCR-MP-001
track: OCR
phase: D5
size: M
req_refs:
  - REQ-OCR-001
  - REQ-OCR-002
  - REQ-OCR-003
depends_on:
  - OCR-IMPL-001
  - SAMPLE-MP-001
touches:
  - code/miniapp/src/pages/sample/ocr/**
  - code/miniapp/src/components/lqg/OcrBar.vue
  - code/miniapp/src/api/ocr.ts
  - code/miniapp/src/pages/sample/form.vue
adr_refs:
  - ADR-0007
blueprint_refs:
  - UI:mp.sample.form.ocr
  - FLOW:F-OCR-01.step1
  - FLOW:F-OCR-01.step4
  - FLOW:F-OCR-01.step5
accept:
  - name: "预填合并规则过 fixture：只填空项、已手填的不覆盖、空值不填不标、表单里没有的键忽略"
    form: STATE
    run: |-
      cd code/miniapp &&
      grep -q 'doc/verify/fixtures/prefill-cases.json' src/pages/sample/ocr/prefill.fixture.spec.ts &&
      ! grep -nE '\.(skip|todo|only)\(' src/pages/sample/ocr/prefill.fixture.spec.ts &&
      pnpm vitest run src/pages/sample/ocr/prefill.fixture.spec.ts --reporter=json --outputFile=/tmp/lqg-prefill.json >/dev/null &&
      jq -e '.numFailedTests == 0 and .numPassedTests >= 5' /tmp/lqg-prefill.json &&
      node -e "const c=require('../../doc/verify/fixtures/prefill-cases.json').cases; if(c.length!==5||!c.some(x=>x.case.startsWith('B-'))||!c.some(x=>x.case.startsWith('E-'))) process.exit(1)"
    counterfeit: |-
      合并写成 Object.assign(form, ocrFields) → B 例里手填的「手填姓名」被「识别姓名」覆盖红；E 例里 internalNo、verifyStatus 被写进表单红——后者等于让识别结果往提交体里夹带内部字段。
      空串也当成识别结果填进去并打标 → C 例红。
      删掉 fixture 里的 B、E 两条病灶用例来过关 → 最后一段红。
  - name: "构建是本次产物；识别条只在新增时出现；小程序包里没有任何第三方识别服务的域名或密钥"
    form: API
    run: |-
      cd code/miniapp && rm -rf dist/build/mp-weixin && pnpm build:mp-weixin >/dev/null &&
      grep -q "@/components/lqg/OcrBar.vue" src/pages/sample/form.vue && grep -q 'showOcr' src/pages/sample/form.vue &&
      grep -q 'chooseMedia' src/components/lqg/OcrBar.vue && grep -q '/mp/ocr/recognize' src/api/ocr.ts &&
      ! grep -rnEi 'aliyuncs\.com|dashscope|api\.weixin\.qq\.com/cv|baidubce|tencentcloudapi|secret[_-]?key|api[_-]?key' src dist/build/mp-weixin
    counterfeit: |-
      为了快，小程序直接调了云厂商的识别接口 → 最后一段在源码或产物里搜到域名 / key 红。
      识别条无条件渲染 → 要求它受 formLayout 的 showOcr 控制（SAMPLE-MP-001 的 fixture 已经规定了编辑态 showOcr=false）。
---

# OCR-MP-001 · 小程序 · 拍照识别条：拍照或选图、识别、只往空项里预填并标「请核对」、识别失败不挡手填

## §0 状态自检（实施前必过，不过就 STOP 报 Kevin）

- [ ] 当前分支符合调度器注入的期望（`track/OCR` worktree）——不写死 `feature/dayN`
- [ ] `depends_on` 全部 done：**OCR-IMPL-001**、**SAMPLE-MP-001**
- [ ] 扫 `doc/change-log.md`：涉及本 ticket 的 CR **以 CR 为准**（CR 覆盖本文）
- [ ] 逐条取权威（别全文读蓝图）：`python3 ~/claude-config/skills/xuqiu/scripts/authority_lint.py show <锚>`，锚见上方 `blueprint_refs`；接口形状见 `doc/api-contract.md`
- [ ] 必读：
  - 填写页视觉基准 = **方案 A**（识别是表单顶部的可选动作），Kevin 2026-09-17 已选定（CR-20260917-03）；看图 `doc/design-options/gallery.html#mp-form-a`。方案 B（三步向导）已否决
  - `doc/verify/fixtures/prefill-cases.json`：预填合并规则的验收用例
  - **ADR-0007**（全文在 `doc/_adr/`，`authority_lint.py show ADR-0007` 取结构化口径）
- [ ] 口径复述（本张最容易做反的）：
  1. **只往空着的项里填**，已经手填的不覆盖。用户先填了两项再拍照，回来发现被冲掉——这是最伤人的一种 bug。
  2. 识别失败 / 超时 / 被限流 → 一句「没识别出来，请手动填写」，**不阻塞填表**。
  3. 小程序只调自己后端的 `/mp/ocr/recognize`，**不直连任何第三方识别服务**（密钥会进小程序包）。
  4. 识别条只在**新增**时显示，编辑已提交样本时不显示。

## 1 背景与口径

模板批注 N6：可支持拍照、截图或者类似顺丰快递拍照识别相关信息。方案 v6：识别出的内容自动填进表单，由填写人核对修改后再提交。

## 2 实现要点

- `OcrBar.vue`（`@/components/lqg/OcrBar.vue`，直接路径导入）：左「拍照识别」右「从相册选」→ `wx.chooseMedia({count:1, mediaType:['image'], sourceType})` → 压缩到长边 ≤ 2000px → `wx.uploadFile` 到 `/mp/ocr/recognize`。
  状态：空闲 / 识别中（禁用按钮 + 进度）/ 成功（「已识别 N 项」+ 可展开的原文）/ 失败（提示语）。
- 纯函数 `mergeOcrPrefill(form, ocrFields)`（`src/pages/sample/ocr/prefill.ts`）→ `{form, marks}`；`prefill.fixture.spec.ts` 从 `doc/verify/fixtures/prefill-cases.json` 读用例。
- 表单页：被预填的项右侧出「识别 · 请核对」小标（`marks` 驱动）；用户改动该项 → 从 `marks` 里移除。提交的永远是表单当前值。
- 隐私：首次使用相机 / 相册前，走小程序隐私授权弹窗（`wx.requirePrivacyAuthorize`）；用途说明写进隐私保护指引（SYS-RELEASE-001 汇总）。

## 3 边界（明确不做）

- 不做语音识别、不做「粘贴并识别」（顺丰截图里有，但甲方只点了拍照和截图）
- 不做一次多张、不做裁剪框
- 不在类器官收样、石蜡包埋、冻存三张表上加识别（甲方只对样本记录信息表提了）

## 4 完工报告要求

1. 真机上识别成功与识别失败各一段录屏或截图序列
2. **改了哪些文件**（含 Flyway 文件名与取号依据、新增的类 / 页面 / 接口清单）
3. **accept 逐条 ✅ / ❌ + 关键输出**（贴命令输出，不贴「已通过」三个字）
4. **遗留与 raise**：越出 `touches` 的改动、与 `doc/api-contract.md` 不一致的地方、没把握的口径
5. 验证用的后端 / 前端长进程已关，或明示留给谁
