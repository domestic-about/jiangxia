---
id: ADR-0005
status: proposed
implementation_status: not-yet-implemented
supersedes: []
superseded_by: []
amends: []
amended_by: []
verified: 2026-09-17
anchor: "t_lqg_doc_file"
decision:
  key: doc.render_pipeline
  value: "poi-tl 按 docx 模板渲染 Word，再由 Gotenberg（LibreOffice）容器转 PDF，再由 PDFBox 出每页 PNG；三种产物存 OSS 私有桶，按『文档内容 + 模板版本』的指纹缓存，内容或模板一变即失效重出；每份文档出内部版与外部版两个版本（外部版里内部编号一格按 ADR-0004 的开关留空）；合并文件 = 已完成的几份 docx 依次拼接、每份另起一页后整体再转 PDF；小程序里的下载 = 打开文档 / 发送到微信聊天"
  rejected_values:
    - "小程序 web-view 在线渲染 docx"
    - "用 HTML 重画一套版式再转 PDF"
---
# ADR-0005: 文档渲染管线——一份 Word 出三种产物

**决策者**: 待 Kevin 过目（AI 给出；预览页形态以 design-options 里 Kevin 选定的方案为准，本文按推荐方案写）
**关联**: REQ-DOC-003 / 004 / 005 / 006 / 007、REQ-QC-011、REQ-DOC-011、t_lqg_doc_file、code/deploy/ 里的 gotenberg 服务与中文字体

## 背景

甲方要的是「预览和 Word 一模一样、图片能放大、Word 和 PDF 都能下、可以三份合并」。小程序本身渲染不了 docx。
售前估算里这一块是最容易超工期的（00-brief 表第 9 行）。

## 决策

按 frontmatter。要点：

- **只有一个版式来源**：docx 模板。PDF 和预览图都由渲染出的 Word 转出来，所以三者必然一致，「一模一样」不靠人眼逐项比。
- **字体是版式走样的头号原因**：Gotenberg 容器必须挂中文字体（模板用到的宋体 / 黑体对应的开源字体），模板文件里的字体同步改成这些字体；样张在**目标服务器的同一个容器镜像**上出，别在开发机上出。
- **显微照片要看原图**：PDF 里的图会被压缩，预览页另把文档用到的图片以缩略图列出，点开看原图（REQ-DOC-005）。
- 「小程序 web-view 在线渲染 docx」不采纳：个人主体以外虽可用 web-view，但版式还原差、业务域名还要单独配置校验。
- 「用 HTML 重画一套版式再转 PDF」不采纳：等于维护两套版式，甲方改一次模板要改两处。

## 后果

- 服务器多跑一个 Gotenberg 容器（转换时占几百 MB 内存）；2 核 4G 够用但要限并发为 1、排队转换。
- 渲染是异步的：点「完成并同步」后先出文件，出完才对外可见；失败要在工作台上看得见、能重试。
- 小程序没有「存到手机文件夹」的接口——这一点必须写进确认单让甲方提前知道（REQ-DOC-007）。
- 甲方改模板 = 换模板文件 + 模板版本号加一 → 旧缓存全部失效重出；定稿后改版式按合同属二次开发。
