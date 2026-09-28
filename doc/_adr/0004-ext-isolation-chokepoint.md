---
id: ADR-0004
status: proposed
implementation_status: not-yet-implemented
supersedes: []
superseded_by: []
amends: []
amended_by: []
verified: 2026-09-17
anchor: "doc/authority/flows.yaml"
decision:
  key: ext.isolation
  value: "外部用户能调的业务接口只有 /mp/ext/** 一组（包 org.dromara.lqg.ext），全部经 ExtScopeService 解析可见样本集合（本人提交的，加上同单位同组且组别已核验者提交的），只返回 Ext 前缀的专用 VO；专用 VO 不含冻存信息与核验人；石蜡包埋记录上的操作人与包埋人 CR-20260918-07 起对外可见；lqg_external 角色调其余业务接口一律被拒；是否对外显示内部编号由系统参数 lqg.ext.show-internal-no 控制（默认 false，CR-20260918-07 起是若依 sys_config，甲方在工作台「系统管理 → 参数设置」里自己改，不用发版）；2026-09-24 起同一个开关也管外部版质控文档里的「内部编号」一格（CR-20260924-10）"
  rejected_values:
    - "在每个业务接口上各自按角色裁字段"
    - "只靠行级数据权限注解过滤"
---
# ADR-0004: 外部隔离做成一个咽喉

**决策者**: 待 Kevin 过目（AI 给出；售前内部简报里已写过同一思路）
**关联**: REQ-AUTH-005 / 006 / 007 / 009 / 012、REQ-DOC-009、FLOW:F-EXT-01、包 org.dromara.lqg.ext

## 背景

合同验收里最不能出事的一条：外部人员只能看到本人和同组的样本，且（默认）看不到真实内部编号。
这类要求如果散在每个接口里各自判断，就变成一个「要证明所有入口都堵住了」的命题——入口是开放集合，永远枚举不完。

## 决策

把它改成一个可以验的不变量：

1. **入口只有一组**：外部角色的权限串只覆盖 `/mp/ext/**`，其余接口在鉴权层就被拒。
2. **范围只有一处算**：`ExtScopeService.visibleSampleIds(userId)`，所有外部查询都从它拿样本集合；单条详情、文档、预览图、下载链接一律先过 `assertVisible(sampleId)`。
3. **字段只有一种出口**：ext 包的 controller 返回值只能是 `Ext*Vo`。这些 VO 里**没有**内部编号字段——不是前端不显示，是接口里根本没有。
4. **三条结构性断言守住它**（写进 AUTH-EXT-001 的 accept 与单测）：ext 包 controller 的返回类型全是 Ext*Vo；ext 包不直接注入业务 Mapper 而只经 ExtScopeService；Ext*Vo 的字段集合是白名单。

「在每个业务接口上各自按角色裁字段」不采纳：漏一个就是事故，而且验收时没法证明没漏。
「只靠行级数据权限注解过滤」不采纳：它只管看哪几行，管不了看哪几列，也管不了 OSS 文件链接。

## 后果

- 新增任何给外部看的内容，都只能加在 ext 包里，并且回来改这条 ADR 的白名单。
- 2026-09-17 晚 Kevin 定外部可填三张表（CR-20260917-05）：组织样本、类器官收样、石蜡包埋送样。写入口按同一个模式各加一组——
  `ExtOrganoidController`（`/mp/ext/organoid`）、`ExtEmbedController`（`/mp/ext/embed`），各自一个只含送检段字段的 `Ext*SubmitBo`，
  都先过范围解析器：只能写本人提交的、状态是待核验或无效的记录；石蜡包埋送样只能挂本人送检过、没被判无效的样本。四条不变量与契约测试不用改，新类自然被扫到。
- 文档文件在 OSS 私有桶，外部拿到的是后端按权限签发的短时签名链接（REQ-DOC-009）。
- 甲方 2026-09-18 要了这个开关（REQ-AUTH-012 / OQ-2 已关）：打开系统参数 `lqg.ext.show-internal-no`，Ext*Vo 里的 `internalNo` 才会被填值；不用改表、不用发版改代码。装配时读参数，关着连键都不出。
- **2026-09-24 起开关也管外部版文档**（CR-20260924-10，甲方第 23 行「质控文档里的「内部编号」内部人员能看到吗？要求内部人员能看到，最好是能选择是否可以让外部人员看到」）。
  此前（CR-20260918-07）开关只作用于接口返回的页面数据，外部版质控文档里的内部编号一格一律留空。现在：开着时外部版（单份、合并、页面图、Word、PDF）印内部编号，关着时留空；内部版一直印。
  实现没有破这条 ADR 的四条不变量：开关的读口仍只有 ext 包的 `ExtInternalNoSwitch`（读不到按关），doc 域直接用它；「这一版印没印」随内容指纹与 `t_lqg_doc_file.show_internal_no` 落库，
  判据在 doc 域 `DocRenderService#delivery`——外部版记着「印了」而开关此刻关着 → 清单、预览图、下载一律不给（外部 `/mp/ext/doc/**` 按「不存在」404），后台按新设置重出；
  ext 包仍不持业务 Mapper、仍只返回 `Ext*Vo`，`ExtDocPagesVo` 逐键拷贝，不转出工作台用的 `internalNoShown` / `outdated`。切换后不用重新「完成并同步」。
  残余风险与撤回文档同口径：已经签出去的 10 分钟下载链接在关开关后到期前仍可用。
