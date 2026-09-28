# D2 r4 L2 notes（边跑边写）

## 环境/前置
- 分支 `task/D2`；jar mtime `2026-09-22 07:03:37`；`find ruoyi-lqg/src -newer jar` = 空（非 stale）；
  后端 PID 60476 从 07:33 起（晚于 jar）、`lsof -p 60476` 里持有 `ruoyi-admin.jar`（2 条 fd）。
- 容器 5433/6380/9002/9003 在跑；8081 / 8082 / 9200 全部本片自起。
- r1 返工确实改过 D2 目录里的探针期望值（`L2-mp-h5.mjs` 的 G3-20 与 `L2-web-plusui.mjs` 的 WEB-L2-06b）
  → 本片**不引用**该目录既有脚本的任何结论，三组全部自写脚本独立重跑。

## 本片资产（全部自写，放在 doc/waves/regression/D2/）
| 文件 | 覆盖 | 结果 |
|---|---|---|
| `L2r4-lib.mjs` / `L2r4-state.mjs` | 共享 helper（不产断言）/ 组1 内部传 id | — |
| `L2r4-g1a-ext-submit.mjs` | 组1：外部 extA 首页三格无数 → 点表新增（mode=new）→ 落库 pending → 历史编辑记录出现待核验 | 9/9 |
| `L2r4-g2-web-table.mjs` | 组2：九种筛选 UI vs 本轮 API 集合、待核验浅黄置顶、核验抽屉两出口（判有效打 1007 / 判无效打本片新交的那条） | 19/19 |
| `L2r4-g1b-ext-resubmit.mjs` | 组1：外部看到原因 → 改后重提回 pending → 工作台核验有效 → 详情只读；extB 看得到 + 「只看我提交的」收窄 | 13/13 |
| `L2r4-g3a-mp-internal.mjs` | 组3：内部首页四格 → 填写页；历史编辑记录改有效样本并排最前；★两页签不串台+点行进对应表单；内部管理表格页（模板列序/冻结/横滑/无新增保存/只读） | 23/23 |
| `L2r4-g3b-organoid.mjs` | 组3：类器官七列可新增（来源单位真走底部弹层）、可从历史编辑记录修改、extC 三项 → organoid/external/pending | 14/14 |
| `L2r4-recon*.mjs` | 侦察（DOM/选择器），不产断言 | — |
| `L2r4-run-g1chain.sh` | 组1 全链驱动（reseed→g1a→g2→g1b） | — |

**最终整链（一次干净 seed 连跑五段）：78/78 绿、0 FAIL。**
```
== D2-r4-L2-g1a 9/9 通过 ==
== D2-r4-L2-g2 19/19 通过 ==
== D2-r4-L2-g1b 13/13 通过 ==
== D2-r4-L2-g3a 23/23 通过 ==
== D2-r4-L2-g3b 14/14 通过 ==
(0 FAIL)
```
截图目录：`shots/L2r4-mp-g1a`、`shots/L2r4-web-g2`、`shots/L2r4-mp-g1b`、`shots/L2r4-mp-g3a`、`shots/L2r4-mp-g3b`。

## 逐条要点
- 组1：外部首页三格 = 样本/类器官/石蜡包埋，正文数字 0 个；点「样本记录信息表」→ URL 带 `mode=new`、有提交按钮；
  新增页不渲染收样段；提交后 `pending|external|tissue`，`donor_name` 库里是密文、接口读回明文（加密落库真的在）。
  工作台判无效写的原因原样出现在外部历史的「无效原因：」行；外部改住院号重提 → 库里回 pending 且原因清空。
  核验有效后外部详情 `inputs=0 / save=0`，且不出现收样段/冻存/核验人字段。
  ★ extB：默认看得到 extA 这条；打开「只看我提交的」后只剩自己的 SJ90000004。**开关必须点 `.wd-switch` 本体**
  （点父容器 `.his__switch` 不切换，是本片第一次误红的原因，产品无问题）。
- 组2：九种筛选逐项**UI 驱动**，对照值不是抄来的常量 —— 每条同一时刻用 `api.sh --as staff` 独立取集合再比；
  软删 1010 任何筛选都不出现。待核验置顶 = 库里 pending 按 `(verify_status='pending') DESC, create_time DESC, id DESC` 的排序，
  行 class `lqg-sample__row-pending`、计算底色 `rgb(252, 241, 218)`（#fcf1da）。抽屉两出口各走一遍并截了图，
  库里副作用（valid+T-l2r4a01 / invalid+原话原因）都核过。
- 组3：★硬断言过了 —— 样本页签逐行 `sample_kind=tissue`、类器官页签逐行 `organoid`、两页签行集合不相交、
  点类器官行 → `pages/organoid/form`、点样本行 → `pages/sample/form`（不是 organoid）。
  表格页表头**逐字对 `_input/templates/*.xlsx` 原件**（openpyxl 真读模板第 1 行）：样本记录 15 列 / 类器官 8 列，
  列名列序 = 模板（冻结列提到最前）+ 追加「切片染色」；首列 `position:sticky left:0px`；
  横滑：H5 下真正滚动的是内层 `.uni-scroll-view`（外层 `uni-scroll-view` 是 `overflow:hidden`），滚到最右 1368/1798；
  页面上只有「导出 Excel」，没有新增/保存；点一行进 `mode=view` 且 0 输入控件 0 保存按钮。
  类器官：新增走底部弹层真选单位（A 医院）→ `organoid|internal|valid`；改类型保存成功、update_by 有值、排最前；
  extC 三项提交 → `organoid|external|pending`。

## 本轮判红的取舍
- **0×S0 / 0×S1**。三组主流程全绿。
- 唯一记账项（S2）：小程序外部新增样本时**不落 `source_unit_id`**（接口拿到 `sourceUnitName` 但
  `source_unit_id` 为空，实测 extA 新交的行 `source_unit_id=null`），所以工作台「来源单位=A 医院 + 组别」
  组合筛选带不出这条（单条件「组别」带得出）。spec 没定外部填表要不要带 id；它只造成「组合筛选少一行」
  的窄场景，不改判 pass/fail，记账给下游。

## 未覆盖项
- 微信开发者工具 / 真机：本沙箱跑不通（EPERM + 需扫码）→ 小程序侧全程用 **H5(9200) + Playwright 等价覆盖**，
  **开发者工具与真机未覆盖**。
- 组1 里「工作台再核验为有效」这一步走的是**同一个后端核验端点**（`PUT /lqg/sample/{id}/verify`），不是网页抽屉的
  第二次手点 —— 网页抽屉两个出口已在组2 各手点一遍并截图，不重复。
