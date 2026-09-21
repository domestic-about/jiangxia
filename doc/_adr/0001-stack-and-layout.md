---
id: ADR-0001
status: accepted
implementation_status: not-yet-implemented
supersedes: []
superseded_by: []
amends: []
amended_by: []
verified: 2026-09-17
anchor: "doc/lint-profile.yaml"
decision:
  key: stack.layout
  value: "RuoYi-Vue-Plus 5.5（后端）+ plus-ui（网页工作台）+ unibest / uni-app + wot-design-uni（小程序）；从干净上游起项目，dongjiaoshan 只搬底座做法（部署清单、OSS 配置、小程序登录与请求层、CI），ruoyi-djs-* 业务代码一行不拷；全部业务代码进一个模块 ruoyi-modules/ruoyi-lqg（包 org.dromara.lqg.<域>）；多租户关闭；一套生产 + 一套测试环境"
  rejected_values:
    - "照搬 dongjiaoshan 的业务模块再删改"
    - "低代码平台（简道云 / 飞书多维表格）"
    - "开启多租户，把来源单位当租户"
  decided_by: kevin
  decided_at: 2026-09-16
---
# ADR-0001: 技术栈与工程布局——沿用 dongjiaoshan 那套，只搬底座不搬业务

**决策者**: Kevin（2026-09-16「我初步判定还是使用 dongjiaoshan 的那一套技术选型」；方案 v6 第 4 章、合同附件技术架构一行同口径）
**关联**: REQ-SYS-003、doc/lint-profile.yaml（号段与前缀）、全部 ticket 的 touches

## 背景

需求里最硬的三条——内外部权限、外部按组隔离、Excel 台账导出——若依现成能力正好压中（小程序认证策略、字段加密、OSS、EasyExcel、定时任务），
而且 /xuqiu → /zhixing 流水线、aliyun-deploy、unibest 小程序模板都是照这套栈调出来的。

## 决策

栈按 frontmatter。工程布局：

```
jiangxia-organoid/
  code/
    RuoYi-Vue-Plus/                              后端（上游 5.5.x，底座源码只读）
      ruoyi-modules/ruoyi-lqg/                   ← 全部业务代码，包 org.dromara.lqg.{auth,sample,embed,cryo,qc,doc,ocr,ext}
      ruoyi-admin/src/main/resources/db/migration/   ← Flyway 迁移
    plus-ui/                                     网页工作台：src/views/lqg/<域>/**、src/api/lqg/<域>/**
    miniapp/                                     小程序（unibest）：src/pages/<域>/**、src/api/<域>.ts
    deploy/                                      docker-compose、nginx、备份与导出脚本
  doc/                                           需求与蓝图
```

- 业务只建一个 Maven 模块：体量小（十几张表），拆多模块只会多出跨模块依赖的麻烦；域之间用包隔开，ticket 的 touches 精确到包。
- 「照搬 dongjiaoshan 的业务模块再删改」不采纳：那边是农场业务，拷过来删不干净，还会把它的字典、菜单号段、迁移历史一起带进来。
- 「低代码平台」不采纳：卡在外部按行按列双重隔离、冻存像库存一样扣数可追溯、质控表按原 Word 版式带图导出这三条，且供体信息会放到第三方 SaaS 上。
- 「开启多租户」不采纳：来源单位、组别是业务数据不是租户；别的实验室要用就单独部署一套（REQ-SYS-015）。

## 后果

- 新开一个 RuoYi 项目该过的坑手册照常过：`~/claude-config/stacks/ruoyi-vue-plus/gotchas.md`（其中 MySQL 专属的 del_unique 一节被 ADR-0009 取代）。
- 不上 staging / prod 双环境流水线：测试环境就是一台小机器 + 小程序体验版（REQ-SYS-010）。
