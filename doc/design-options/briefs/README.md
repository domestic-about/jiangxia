# Claude Design 粘贴稿 · 索引

一页一个文件。每份文件里「---」之间的整段是可以直接粘进 claude.ai/design 的内容，
「---」之外的是内部说明（不要粘、也不要让它流到图廊或甲方稿上）。

出稿之后放进 `doc/design-options/_from-design/`，再逐帧替换 `gallery.html` 里对应的 `<div class="opt …" id="…">`。
**帧 id 一律保持不变**（`doc/tools/check_authority_xref.py` 的 X5 只认 `gallery.html#<id>` 这种锚）；
新增的三帧要在权威里补上 `prototype` 指向。

| 文件 | UI 权威 | 对应图廊帧 |
|---|---|---|
| `mp-login.md` | `UI:mp.login` | **新增** `#mp-login`（至今没有草案） |
| `mp-home.md` | `UI:mp.home` / `UI:mp.home.entries` | `#mp-home-final`、`#mp-home-final-ext` |
| `../claude-design-brief-我的.md` | `UI:mp.me` | `#mp-me-int`、`#mp-me-ext`（9-17 夜已重绘，稿子就是这份，没有挪位置） |
| `mp-history.md` | `UI:mp.history` | `#mp-history`、`#mp-history-int` |
| `mp-me-profile.md` | `UI:mp.me.profile` | **新增** `#mp-profile`（至今没有草案） |
| `mp-sample-form.md` | `UI:mp.sample.form` / `UI:mp.sample.form.ocr` | `#mp-form-a` |
| `mp-embed-form.md` | `UI:mp.embed.form`（内部 + 外部两种模式） | `#mp-embed-ext` + **新增** `#mp-embed-int`（内部模式至今没有草案） |
| `mp-ledger.md` | `UI:mp.ledger` / `UI:mp.sample.list` / `UI:mp.embed.list` | `#mp-ledger`、`#mp-ledger-view`、`#mp-ledger-edit` |
| `mp-cryo.md` | `UI:mp.cryo.list` / `UI:mp.cryo.flow` | `#mp-cryo` |
| `mp-detail-ext.md` | `UI:mp.sample.detail.ext` | `#mp-detail-ext`、`#mp-detail-ext-on` |
| `mp-doc-list.md` | `UI:mp.doc.list` | `#mp-doc-a` |
| `mp-doc-preview.md` | `UI:mp.doc.preview` | `#mp-preview-a` |

12 份说明覆盖图廊现有 16 帧 + 3 帧新增 = 19 帧。网页工作台的 5 帧（`#admin-*`）不做设计，沿用若依 plus-ui 默认浅色加主色覆盖，本目录不涉及。

## 每份说明里都写死的几条

- 手机竖屏，宽 375，**只做浅色一套**（需求里没有深色）。
- token 值逐条写进「视觉语言」一节，组件里不许出现颜色字面量。
- 视觉方向 A「清爽卡片」（CR-20260921-08）：柔和阴影分层、卡片不描边；圆角卡片 14、主按钮与控件 12、按钮组 9、徽标 6、弹层 16。完整规则见 `../direction-a/落地规范.md`。
- 编号类内容一律等宽：内部编号、送检单号、石蜡块编号、冻存样品名称、住院号、手机号。
- 每一帧在 390 宽下不横向溢出、手机框不被撑破；表格页那一处是有意的横向滑动，滑动发生在被裁掉宽度的容器里，页面本身不滚。
- **空态、加载态、失败态都要画**——原稿只画了正常态，每份说明末尾都列了这一页该补的状态帧。
- 粘贴段里不出现人名、人日、报价、变更编号、命令、内部话术；合作单位一律称「合作单位」，甲方称「贵方」。

## 供体姓名的规矩

列表页掩码（保留姓，「刘**」），详情页全名（「刘某某」）。每份说明里都按这条写了示例数据，出稿时别混。
