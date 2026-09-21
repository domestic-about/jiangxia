---
id: ADR-0006
status: proposed
implementation_status: not-yet-implemented
supersedes: []
superseded_by: []
amends: []
amended_by: []
verified: 2026-09-17
anchor: "t_lqg_sample"
decision:
  key: privacy.donor_fields
  value: "样本主档的供体姓名、住院号，样本质控表的患者编号，共三列用框架 @EncryptField 加密落库（AES，密钥走环境变量不进仓库）；工作台内部人员看明文；按这几列查询只支持精确匹配；其余字段不加密"
  rejected_values:
    - "全部明文存储"
    - "内部列表也打码显示"
---
# ADR-0006: 供体姓名、住院号、患者编号加密存储

**决策者**: 待 Kevin 过目（涉及隐私承诺，属于要人拍板的那一类；AI 只给默认口径）
**关联**: REQ-SYS-008、REQ-SAMPLE-011、t_lqg_sample.donor_name / hospital_no、t_lqg_qc_sample.patient_no

## 背景

合同第四条 2(5) 写了乙方对供体个人信息的保密义务，但没写技术手段。数据库和备份文件都在我方代管的服务器和 OSS 上，
最现实的泄露面是「备份文件或数据库被整个拿走」。

## 决策

按 frontmatter。「全部明文存储」不采纳：备份文件一旦外泄就是姓名 + 住院号的明文清单。
「内部列表也打码显示」不采纳：内部人员干活要对着全名和住院号核样本，打码只会逼他们点开每一行。

## 后果

- **代价**：这几列不能模糊搜索。工作台筛选里供体姓名、住院号是「精确匹配」输入框（REQ-SAMPLE-011 的 note 已写）。
- 导出 Excel 是明文（内部人员导出，合同允许）。
- 密钥丢了数据就读不出来：密钥随部署文档单独交给 Kevin 保管，全量导出（REQ-SYS-006）导的是解密后的数据。
- Kevin 若决定不加密：去掉两个注解 + 一次解密迁移即可，不影响表结构。
