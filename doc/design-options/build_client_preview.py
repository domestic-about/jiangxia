#!/usr/bin/env python3
"""从 gallery.html 抽出已选定的帧，生成给甲方看的单文件界面设计稿。

为什么是生成、不是另画一份：gallery.html 里已选定的帧是视觉基准（design-authority.md §A）。
甲方版如果手抄一份，两边迟早对不上。这里只取「帧的标记」和「产品界面的 CSS」，
外壳和文案按甲方读者另写：不出现方案对比、内部锚点、ticket、决策人这些内部信息。

产物约束（发给甲方、多半在微信里打开）：
  - 单个文件，不引用任何外部资源（Google Fonts 在国内打不开，会把页面卡住）
  - 不用 JavaScript（iOS 微信的文件预览不保证执行脚本）
  - 手机宽度能看；电脑样子的三张图在窄屏上横向滑动

用法（cwd = 工作区根）：
  python3 doc/design-options/build_client_preview.py           # 生成
  python3 doc/design-options/build_client_preview.py --check   # 只比对：磁盘上的稿子过期则退出 1
"""
import html as htmllib
import pathlib
import re
import sys

HERE = pathlib.Path(__file__).resolve().parent
SRC = HERE / "gallery.html"
VERSION = "3"
EXPECTED_FIGS = 19          # 9-18 新增 5 张：表格页只读详情 / 修改模式、开关打开的样本详情、工作台取用登记、参数设置
DATE_CN = "2026 年 9 月 18 日"
DATE_ISO = "2026-09-18"
OUT = HERE.parent / "confirmation" / f"类器官送检与样本管理系统-界面设计稿-v{VERSION}.html"

CLIENT = "湖北江夏实验室类器官研究中心"
VENDOR = "武汉市添达信息技术服务有限公司"

# ── 文案：读者是研究中心的老师，不是软件工程师。用他们自己的叫法（合同与模板里的原词）。
PARTS = [
    dict(
        id="mp", title="一、微信小程序",
        intro="所有人从同一个入口用微信手机号登录，系统按手机号判断是内部人员还是合作单位的人。底部三个页签：首页、文档、我的。首页用来填表，「我的」里找回填过的记录；内部人员的「我的」里还能看四张表的全部记录。",
        figs=[
            dict(src="mp-home-final", title="首页", toc="首页（内部人员）", who="内部人员看到的",
                 purpose="内部人员登录后的第一页，用来填表。",
                 points=[
                     "四个入口对应您的四个 Excel：样本记录信息表、类器官收样记录、石蜡包埋送样记录、-80 冻存记录。<b>点哪张表，就是新增一条这张表的记录</b>（{fig:mp-form-a}）。",
                     "首页不放数字和提醒，页面保持简单。待核验、冻存超期这些需要处理的事，在网页工作台首页集中提醒（{fig:admin-home}）。",
                     "填过的记录在「我的 → 历史编辑记录」里找回和修改（{fig:mp-history}）；四张表的全部记录在「我的 → 内部管理」里看（{fig:mp-ledger}）。",
                 ]),
            dict(src="mp-home-final-ext", title="首页", toc="首页（合作单位）", who="合作单位的人看到的",
                 purpose="合作单位的人登录后看到的首页，和内部人员是同一个样子。",
                 points=[
                     "<b>三张表</b>：样本记录信息表、类器官收样记录、石蜡包埋送样记录。-80 冻存记录只有中心内部人员能看到。",
                     "点哪张表就是填写这张表，提交后由中心核验。",
                     "样本用系统自动生成的「送检单号」指代，全程不出现内部编号。",
                     "自己和同组同事送过的记录，在「我的 → 历史编辑记录」里查看（{fig:mp-history}）。",
                 ]),
            dict(src="mp-form-a", title="填写与修改样本记录信息表", toc="填写与修改样本记录信息表", who="合作单位和内部人员都用",
                 purpose="送检时填的表，也是拍照识别用的地方。从「历史编辑记录」点一条进来，就是修改这一条。",
                 points=[
                     "拍照或从相册选图，系统识别出姓名、性别、年龄、住院号等内容并预先填好。",
                     "识别出来的项带黄色「请核对」标记，修改后标记消失；没识别出来的项留空，手填即可。识别结果只是预填，核对后才提交。",
                     "合作单位只填送检信息；内部人员在下方多一组收样信息。合作单位提交后是「待核验」，中心核验通过后变为「有效」。",
                     "<b>提交后可以修改</b>：内部人员随时可以改，包括已核验有效的样本，页面上会显示最后是谁、什么时候改的。",
                     "合作单位在「待核验」或「无效」时可以改自己送的样本；已核验有效的如果有错，请中心内部人员修改。",
                     "类器官收样记录、-80 冻存记录的填写页和这一页样式相同，字段按您给的模板来。合作单位填类器官收样记录只有三项：来源单位、类器官类型、备注。",
                 ]),
            dict(src="mp-embed-ext", title="合作单位填石蜡包埋送样记录", toc="合作单位填石蜡包埋送样", who="合作单位的人用",
                 purpose="合作单位把自己送检过的样本交给中心做石蜡包埋时填的表。这一页是这一版新加的。",
                 points=[
                     "先选样本：只列出本人送检过、没有被判为无效的样本，用送检单号和姓氏标出。",
                     "只填两项：样本类型、类器官来源类型。",
                     "提交后由中心核验。核验有效时中心给出石蜡块编号，再陆续补上包埋、切片、染色情况，合作单位在样本详情里都看得到（{fig:mp-detail-ext}）。",
                     "石蜡块编号、包埋人、染色这些由中心填写，合作单位这一页不出现。",
                 ]),
            dict(src="mp-me-int", title="我的", toc="我的（内部人员）", who="内部人员看到的",
                 purpose="底部「我的」页签。内部人员比合作单位多一个「内部管理」板块。这一页是这一版新加的。",
                 points=[
                     "<b>历史编辑记录</b>：每个人都有，找回自己填过的记录并修改（{fig:mp-history}）。",
                     "<b>内部管理</b>：只有内部人员有。四个入口进入四张表，像 Excel 一样看全部记录、筛选、导出（{fig:mp-ledger}、{fig:mp-cryo}）。",
                     "小程序里不做核验、不做冻存取用登记，这些在网页工作台做，那里的功能最全。",
                     "合作单位的「我的」里没有内部管理，多一行「单位与组别」，显示核验状态，可以修改。",
                 ]),
            dict(src="mp-history", title="历史编辑记录", toc="历史编辑记录", who="合作单位和内部人员都用",
                 purpose="从「我的」进入，找回自己填过的记录。图上是合作单位看到的样子。这一页是这一版新加的。",
                 points=[
                     "按表分页签：合作单位三张，内部人员四张。最近填的、最近改过的排在前面。",
                     "合作单位看得到自己和同组同事提交的记录，可以打开「只看我提交的」只看自己的。",
                     "<b>点进去可以修改</b>：合作单位自己的记录在「待核验」或「无效」时可以修改后重新提交，无效的会写明原因；同组同事的只能看。",
                     "内部人员看到的是<b>中心所有内部人员</b>填过、改过的记录，每条写明是谁经手的，也可以打开「只看我提交的」只看自己的；点进去就能改，别人录的也能改。",
                     "这里列的是记录本身，不是每一次修改的明细；每条记录会显示最后是谁、什么时候改的。",
                 ]),
            dict(src="mp-ledger", title="内部管理 · 四张表", toc="内部管理 · 四张表", who="内部人员用",
                 purpose="内部人员从「我的 → 内部管理」进来，像 Excel 一样看全部记录。这一页是这一版新加的。",
                 points=[
                     "四张表在顶上切换，像 Excel 底部的工作表：样本记录、类器官收样、石蜡包埋、-80 冻存。",
                     "列名和顺序与您给的模板一样。第一列（内部编号、石蜡块编号或冻存样品）固定不动，其余列左右滑动查看。",
                     "可以搜索、按核验状态筛选。合作单位新送来、还没核验的记录用浅黄色标出。",
                     "底部「导出 Excel」：按当前筛选导出，格式和网页工作台导出的一样，导出后可以打开或发送到微信。",
                     "<b>点一行看这一条的全部内容，右上角「修改」就能改</b>：中心任何人填的记录都能改（外部送来还没核验的除外，那些在工作台核验）。新增在首页；核验、冻存取用在网页工作台。",
                 ]),
            dict(src="mp-ledger-view", title="内部管理 · 点一行看详情", toc="内部管理 · 看详情", who="内部人员用",
                 purpose="在四张表里点一行，打开的就是这一条的全部内容。这一页是这一版新加的。",
                 points=[
                     "全部内容只读，没有输入框，不会手滑改坏数据。",
                     "<b>右上角「修改」</b>：点它就进入修改（下一张图）。这是 9 月 18 日您提「还要加一条可以编辑」加的。",
                     "中心内部人员之间不分你我：自己录的、同事录的都能改。合作单位送来还没核验的样本这里不给「修改」，请先在网页工作台核验。",
                     "页面底部写着最后是谁、什么时候改的。",
                 ]),
            dict(src="mp-ledger-edit", title="内部管理 · 点「修改」之后", toc="内部管理 · 修改", who="内部人员用",
                 purpose="点了「修改」之后的样子。这一页是这一版新加的。",
                 points=[
                     "进来的就是这张表的填写页，和首页新增用的是同一页，只是带着这一条的内容。",
                     "改完点底部「保存」。系统记下是谁、什么时候改的。",
                     "这条记录随后也会出现在改动人的「历史编辑记录」里（{fig:mp-history}），随时能找回来再改。",
                 ]),
            dict(src="mp-cryo", title="内部管理 · -80 冻存与取用登记", toc="内部管理 · -80 冻存", who="内部人员用",
                 purpose="内部管理里的「-80 冻存」：放了多久、还剩几支、谁取走了。图上是点了一行之后弹出的批次详情。",
                 points=[
                     "在 -80℃ 放满两周还没转液氮的批次排在最前面，浅红色标出，并写明超了几天。<b>在工作台登记「转液氮」之后，这一条就不再提示</b>；支数取空的也不再提示。两周这个天数写在系统参数里，想改成别的天数随时能改。提醒只在系统里显示，不发微信消息或短信。",
                     "点一行弹出这一批的详情：还剩几支、放在哪、全部取用登记；每一笔都写明操作后还剩几支，改过的会标出来。",
                     "<b>取走、补入、转液氮和修改登记在网页工作台做</b>：登记填错了可以直接改或删，冻存数量本身也能改。改完系统按时间先后重新算剩余，出现「当时只剩 2 支却取走了 3 支」时不让保存，并指出是哪一笔。",
                     "新的冻存记录从首页填；自己录的冻存记录（包括冻存数量）可以在历史编辑记录里改，校验规则一样。",
                 ]),
            dict(src="mp-detail-ext", title="样本详情", toc="样本详情（合作单位）", who="合作单位的人看到的",
                 purpose="合作单位点开自己或同组同事的样本，能看到这个样本后续的处理情况。",
                 points=[
                     "<b>看得到</b>：送检信息、核验结果、石蜡包埋进度（包埋和切片时间、染色类型、指标表达情况、操作人与包埋人）。",
                     "自己交的石蜡包埋送样在核验前也列在这里，标「待核验」；被判无效会写明原因。",
                     "<b>三份质控文档都看得到</b>：样本质控表、类器官质控表、类器官质量评分表（评分表显示合计分）。内部人员在网页工作台「完成并同步」之后才出现，点进去可以预览和下载（{fig:mp-preview-a}）。",
                     "<b>看不到</b>：冻存信息。内部编号默认也不显示；需要让合作单位看到时，系统管理里有一个开关可以打开，默认是关的。",
                     "同组同事送的样本只能看，不能改。",
                 ]),
            dict(src="mp-detail-ext-on", title="样本详情 · 打开内部编号开关之后", toc="样本详情（开关打开）", who="合作单位的人看到的",
                 purpose="和上一张是同一个页面，区别只有顶上多出来的那行内部编号。这一页是这一版新加的。",
                 points=[
                     "9 月 18 日您说「加个开关就能决定让不让外部人员看到我们的内部编号」——开关做好了，在网页工作台的「系统管理 → 参数设置」里（{fig:admin-config}）。",
                     "<b>默认是关的</b>：关着的时候，合作单位连这个字段都拿不到，不是页面上藏起来。",
                     "您需要时自己打开，合作单位的样本详情里就多显示一行内部编号；随时可以再关掉。",
                     "质控文档里的「内部编号」一格不受这个开关影响，始终留空（文档是提前生成好存起来的）。",
                 ]),
            dict(src="mp-doc-a", title="文档列表", toc="文档列表", who="合作单位和内部人员都用",
                 purpose="找质控文档的地方，对应小程序底部的「文档」页签。图上是点了「下载」之后弹出的样子。",
                 points=[
                     "按样本分组，一个样本一张卡片，三份文档放在一起：样本质控表、类器官质控表、类器官质量评分表。",
                     "<b>每份文档旁边都有「下载」</b>，可以选 PDF 或 Word；一个样本有两份以上时，还有「合并预览」「合并下载」。不用先点进预览。",
                     "小程序不能把文件直接存进手机文件夹，所以下载是「打开」（打开后点右上角可以保存或转发）或「发送到微信」。网页工作台上是正常的下载。",
                     "可以按时间范围和文档类型筛选，最近更新的排在前面。",
                     "合作单位看得到自己和同组同事的样本的这三份文档。",
                 ]),
            dict(src="mp-preview-a", title="文档预览与下载", toc="文档预览与下载", who="合作单位和内部人员都用",
                 purpose="点开一份文档后看到的页面。",
                 points=[
                     "顶上可以在这个样本的几份文档之间切换，两份以上时还能看合并版。",
                     "预览的版式和 Word 原件一致，可以翻页、全屏、双指放大。图上的样张是照您给的模板排的示意，<b>最终版式以您确认的模板样张为准</b>。",
                     "文档里的显微照片单独列在下方，点开看原图，细节看得清。附件（比如细胞活率报告）可以直接点开。",
                     "底部选 PDF 或 Word，再选「打开」或「发送到微信」。",
                     "合作单位看到的版本里，「内部编号」一格留空。",
                 ]),
        ]),
    dict(
        id="web", title="二、网页工作台",
        intro="内部人员在电脑浏览器里用账号密码登录。核验、批量筛选、导出 Excel、冻存取用登记、编辑三份质控文档都在这里做，功能最全。",
        figs=[
            dict(src="admin-home", title="工作台首页", who="内部人员用",
                 purpose="打开工作台先看到的页面，只放需要处理的事。",
                 points=[
                     "五张待办卡片：待核验样本、待核验石蜡包埋送样、-80℃ 超两周未转液氮、待核验的外部用户组别、文档生成失败。点卡片直接进入对应列表。",
                     "左侧菜单上的红色数字和卡片是同一份数。小程序首页不放数字，需要处理的事都在这里提醒。",
                     "下方是最近提交的样本，合作单位新送来的会标出「待核验」。",
                 ]),
            dict(src="admin-sample-a", title="样本总表与核验", who="内部人员用",
                 purpose="所有样本都在这一张表里，是内部人员在电脑上的主要工作页面。",
                 points=[
                     "组织样本和类器官样本同在一张表，可按来源单位、组别、类别、内部或外部、核验状态、收样日期等条件筛选。",
                     "四张表都能导出 Excel，格式与您的模板一致：这里导出样本记录信息表和类器官收样记录，石蜡包埋、冻存管理页各自导出；小程序「我的 → 内部管理」也能导出同样的文件。",
                     "合作单位提交的样本（包括类器官收样记录）用浅黄色标出。点「核验」，右侧滑出核验面板（图上正打开着），补上收样日期、内部编号等信息，判为有效或无效。石蜡包埋页用同样的方式核验合作单位交来的送样，判有效时给出石蜡块编号。",
                     "核验面板盖住了表格右边的几列：收样日期、有无固定、切片染色和操作。关掉面板就能看到。",
                     "「切片染色」一列提示这个样本有几个石蜡块、是否已切片、做过哪些染色；「操作」一列可以直接进入该样本的质控文档、石蜡包埋和冻存。",
                     "所有样本提交后都能修改，包括已核验有效的；面板顶部显示最后是谁、什么时候改的。",
                 ]),
            dict(src="admin-qc-a", title="质控文档编辑", who="内部人员用",
                 purpose="在网页上填写三份质控文档，确认无误后同步给送检方。",
                 points=[
                     "三份文档用页签切换，每份都有「草稿」和「已完成」两种状态。",
                     "左边填写内容、上传显微照片和附件；右边是预览，和最终下载的 Word、PDF 是同一份内容。",
                     "预览可以在「内部版」和「外部版」之间切换，提前看到送检方会看到什么。",
                     "点「完成并同步给送检方」之后，送检方才能在小程序里看到这份文档。",
                     "支持单份下载，也支持三份合并下载，Word 和 PDF 都有。",
                 ]),
            dict(src="admin-cryo", title="冻存管理 · 取用登记与转液氮", who="内部人员用",
                 purpose="冻存的取走、补入、转液氮、改登记都在这一页做。这一页是这一版新加的。",
                 points=[
                     "三个页签：全部、-80 超期、液氮。超期的排最前、浅红色，写明超了几天。",
                     "<b>点「转液氮」登记转移时间和液氮位置之后，这一批当场从「-80 超期」里消失</b>，页签数字和工作台首页的待办数跟着减一——这就是您问的「转移后还会有提示吗」：不会再提示了。支数取空的也不再提示。",
                     "「取走」「补入」按实际支数登记，剩余支数自动重算；登记填错了可以改、可以删。",
                     "小程序上这些只能看，不能改（{fig:mp-cryo}）。",
                 ]),
            dict(src="admin-config", title="系统管理 · 参数设置", who="内部人员用",
                 purpose="您 9 月 18 日提到的两个设置都在这一页，自己改，改完立刻生效。这一页是这一版新加的。",
                 points=[
                     "<b>合作单位可见内部编号</b>：默认关。开了合作单位就能在样本详情里看到内部编号（{fig:mp-detail-ext-on}），随时能再关。",
                     "<b>-80 冻存超期提醒天数</b>：默认 14 天。改成 10 天或 21 天都行，下次打开页面就按新的天数算。",
                     "改这两项不用我们改程序、不用重新发版；谁改的、什么时候改的都有记录。",
                     "合作单位登不上网页工作台，也看不到这一页。",
                 ]),
        ]),
]

# 第 2 版和第 1 版相比改了什么：左边是要解决的问题（多数是您 9 月 17 日下午提的），右边是这一版的改法。
CHANGES = [
    ("首页四张表点进去是什么？能不能像 Excel 表？",
     "首页点哪张表，<b>就是新增一条这张表的记录</b>，内部人员和合作单位一样。<b>像 Excel 一样看整张表</b>，在内部人员的「我的 → 内部管理」里。首页顶部的数字去掉了，需要处理的事在网页工作台首页集中提醒。", "{fig:mp-home-final}、{fig:mp-ledger}、{fig:admin-home}"),
    ("合作单位能填哪几张表",
     "合作单位首页有<b>三张表</b>：样本记录信息表、类器官收样记录、石蜡包埋送样记录，提交后都由中心核验；-80 冻存记录只有内部人员能看到。", "{fig:mp-home-final-ext}、{fig:mp-embed-ext}"),
    ("填过的记录去哪找、怎么改",
     "「我的」里加了<b>历史编辑记录</b>：每个人都能找回自己填过的记录，点进去修改；合作单位还能看到同组同事送的。", "{fig:mp-me-int}、{fig:mp-history}"),
    ("四张表内部人员都要能下载，导出为 Excel",
     "内部管理的四张表底部都有「导出 Excel」，按当前筛选导出；网页工作台的四张表原本就能导出，两边格式一样。", "{fig:mp-ledger}、{fig:mp-cryo}、{fig:admin-sample-a}"),
    ("-80 冻存以及其他所有表，提交后都要能修改",
     "都能改：自己填的在历史编辑记录里改；网页工作台能改全部，包括已核验有效的样本；冻存的取走、补入登记填错了在工作台直接改或删，剩余支数自动重算。", "{fig:mp-form-a}、{fig:mp-history}、{fig:mp-cryo}"),
    ("文档要能单份下载 PDF 或 Word，也能合并下载",
     "文档列表上每份文档旁边加了「下载」，每个样本加了「合并下载」，不用先点进预览。", "{fig:mp-doc-a}、{fig:mp-preview-a}"),
    ("合作单位要能看到三份 Word",
     "三份都看得到：样本质控表、类器官质控表、类器官质量评分表；在图上逐份写明了。", "{fig:mp-detail-ext}、{fig:mp-doc-a}、{fig:mp-preview-a}"),
    ("内部管理的四张表，看和导出之外还要能编辑（9 月 18 日）",
     "表格里<b>点一行进去，右上角「修改」就能改</b>，中心任何人填的记录都能改；表格本身仍然只用来看、筛、导出，避免在小格子里误改。", "{fig:mp-ledger}、{fig:mp-ledger-view}、{fig:mp-ledger-edit}"),
    ("中心内部人员有多位，历史编辑记录不该只看自己的（9 月 18 日）",
     "内部人员的历史编辑记录改成<b>中心所有人的记录</b>，每条写明谁经手，也能切换「只看我提交的」。", "{fig:mp-history}"),
    ("合作单位应该看得到操作人和包埋人（9 月 18 日）",
     "样本详情里<b>加上了操作人与包埋人</b>；冻存信息仍然不对合作单位显示。", "{fig:mp-detail-ext}"),
    ("内部编号想给一个开关，决定要不要让合作单位看到（9 月 18 日）",
     "系统管理里加了这个开关，<b>默认关着</b>（合作单位看到的是送检单号和石蜡块编号）；需要时打开，合作单位页面上就会显示内部编号。", "{fig:mp-detail-ext-on}、{fig:admin-config}"),
    ("转液氮之后还会不会一直提示，两周这个天数能不能设（9 月 18 日）",
     "登记转液氮之后<b>立刻不再提示</b>，支数取空的也不再提示；两周是默认值，写在系统参数里，随时可以改成别的天数。", "{fig:admin-cryo}、{fig:admin-config}"),
]

HOW = [
    ("图里的数据都是示意", "姓名、编号、单位都是编的，只为了让页面看起来像真的在用。"),
    ("手机样子的是小程序，电脑样子的是网页工作台", "小程序合作单位和内部人员都用；网页工作台只有内部人员用。"),
    ("颜色、图标、文字细节还会微调", "功能范围与合同附件一致，这份稿子是把它画出来给您看。"),
    ("没有单独画出来的页面", "类器官收样记录、-80 冻存记录的填写页，样式和{fig:mp-form-a} 相同，字段按您给的模板来；合作单位的「我的」、内部人员的历史编辑记录、人员管理页也没单独画，区别写在了文字里。"),
]

ASK = [
    "合作单位能填的三张表、各填哪几项是否合适：样本记录信息表填送检信息；类器官收样记录填来源单位、类器官类型、备注；石蜡包埋送样记录选自己送过的样本，填样本类型和类器官来源类型（{fig:mp-home-final-ext}、{fig:mp-form-a}、{fig:mp-embed-ext}）。",
    "历史编辑记录的范围是否合适：合作单位看到自己和同组同事的；内部人员看到中心所有人的，都可以切换「只看我提交的」。它列的是记录本身，不是每一次修改的明细（{fig:mp-history}）。",
    "内部管理里四张表的列名和顺序，和您平时用的 Excel 是否一致（{fig:mp-ledger}、{fig:mp-cryo}）。",
]

# 甲方版里不该出现的内部用语与外部依赖。帧里若混进来，同样拦下。
FORBIDDEN = ["Kevin", "AI 推荐", "推荐", "方案 A", "方案 B", "未采用", "选定", "UI:", "FLOW:", "REQ-", "CR-",
             "ADR", "ticket", "authority", "lint", "甲方", "乙方", "人天", "成本", "外包", "毛利",
             "http://", "https://", "<script", "<link", "@import", "url("]

# ── 外壳样式：类名一律 d- 前缀。帧的 CSS 里全是 .c .h .t .nav 这类短类名，不加前缀必撞。
SHELL_CSS = """
:root{
  color-scheme:light;
  --ground:#EDF2F2; --surface:#FFFFFF; --ink:#15232A; --ink-2:#44565F; --ink-3:#6E7F87;
  --line:#D3DEDF; --accent:#0E7C7B; --accent-soft:#D6ECEA;
  --shadow:0 1px 2px rgba(21,35,42,.06),0 8px 24px rgba(21,35,42,.08);
  --sans:-apple-system,BlinkMacSystemFont,"PingFang SC","Hiragino Sans GB","Microsoft YaHei","Noto Sans CJK SC","Source Han Sans SC",sans-serif;
  --monof:ui-monospace,"SF Mono",Menlo,Consolas,"Liberation Mono",monospace;
}
*{box-sizing:border-box}
html{-webkit-text-size-adjust:100%;text-size-adjust:100%}
body{margin:0;background:var(--ground);color:var(--ink);font-family:var(--sans);font-size:15px;line-height:1.75}
.d-wrap{max-width:1020px;margin:0 auto;padding-inline:20px;padding-block:40px 72px;display:flex;flex-direction:column;gap:48px}
.d-eyebrow{margin:0;font-size:13px;font-weight:700;letter-spacing:.06em;color:var(--accent)}
.d-top h1{margin:6px 0 0;font-size:30px;line-height:1.3;font-weight:700;text-wrap:balance}
.d-sub{display:block;margin-top:2px;font-size:20px;font-weight:500;color:var(--ink-2)}
.d-meta{margin:10px 0 0;font-size:13px;color:var(--ink-3);font-variant-numeric:tabular-nums}
.d-lead{margin:18px 0 0;max-width:40em;color:var(--ink-2)}
.d-chg{margin:26px 0 0;padding:18px 20px;background:var(--surface);border:1px solid var(--line);border-left:4px solid var(--accent);border-radius:12px}
.d-chg h2{margin:0 0 10px;font-size:17px;line-height:1.4}
.d-chg ol{margin:0;padding-left:1.4em;display:flex;flex-direction:column;gap:12px}
.d-chg li{padding-left:2px}
.d-chg li::marker{color:var(--accent);font-weight:700}
.d-chg .q{display:block;font-weight:700;color:var(--ink)}
.d-chg .a{display:block;color:var(--ink-2);font-size:14px;line-height:1.7}
.d-chg .f{display:inline-block;margin-top:3px;font-size:12px;color:var(--accent);font-weight:700}
.d-how{list-style:none;margin:22px 0 0;padding:16px 20px;background:var(--surface);border:1px solid var(--line);border-radius:12px;
  display:grid;grid-template-columns:repeat(auto-fit,minmax(340px,1fr));gap:12px 36px;font-size:13.5px;line-height:1.65;color:var(--ink-2)}
.d-how b{display:block;color:var(--ink)}
.d-toc{display:flex;flex-direction:column;gap:10px;margin-top:22px;font-size:13.5px}
.d-toc-row{display:flex;flex-wrap:wrap;align-items:baseline;gap:6px 8px}
.d-toc-row>span{color:var(--ink-3);min-width:6em}
.d-toc a{color:var(--ink-2);text-decoration:none;padding:2px 11px;border:1px solid var(--line);border-radius:999px;background:var(--surface)}
.d-toc a:hover,.d-toc a:focus-visible{border-color:var(--accent);color:var(--accent);outline:none}
.d-part{display:flex;flex-direction:column;gap:44px;scroll-margin-top:12px}
.d-part-head{padding-bottom:14px;border-bottom:2px solid var(--ink)}
.d-part-head h2{margin:0;font-size:22px;line-height:1.35}
.d-part-head p{margin:6px 0 0;max-width:40em;color:var(--ink-2)}
.d-fig{display:grid;grid-template-columns:auto minmax(0,1fr);gap:14px 40px;align-items:start;scroll-margin-top:12px}
.d-fig.d-wide{grid-template-columns:minmax(0,1fr)}
.d-cap{grid-column:1/-1;display:flex;flex-wrap:wrap;align-items:baseline;gap:6px 12px}
.d-no{font-family:var(--monof);font-size:12.5px;font-weight:700;color:var(--accent);background:var(--accent-soft);border-radius:6px;padding:1px 8px;font-variant-numeric:tabular-nums}
.d-cap h3{margin:0;font-size:17px;line-height:1.4}
.d-who{font-size:12.5px;color:var(--ink-3)}
.d-shot{min-width:0}
.d-swipe{display:none;margin:6px 0 0;font-size:12.5px;color:var(--ink-3)}
.d-say{min-width:0;max-width:36em;padding-top:2px}
.d-say p{margin:0 0 10px}
.d-say ul{margin:0;padding-left:1.25em;display:flex;flex-direction:column;gap:7px;color:var(--ink-2);font-size:14px;line-height:1.7}
.d-say li::marker{color:var(--accent)}
.d-say b{color:var(--ink)}
.d-fig.d-wide .d-say{max-width:none}
.d-fig.d-wide .d-say p{max-width:40em}
.d-fig.d-wide .d-say ul{display:block;columns:2 340px;column-gap:44px}
.d-fig.d-wide .d-say li{break-inside:avoid;margin-bottom:7px}
.d-ask{padding-top:18px;border-top:2px solid var(--ink)}
.d-ask h2{margin:0 0 10px;font-size:20px;line-height:1.35}
.d-ask ol{margin:0;padding-left:1.4em;max-width:40em;display:flex;flex-direction:column;gap:6px}
.d-ask p{margin:14px 0 0;max-width:40em;color:var(--ink-2)}
.d-foot{margin:0;padding-top:14px;border-top:1px solid var(--line);font-size:12.5px;color:var(--ink-3)}
/* 帧里的等宽字与「Word 标题」不再依赖网络字体 */
.mono{font-family:var(--monof)}
.pg h6{font-family:"Songti SC","STSong","SimSun",serif}
@media (max-width:980px){.d-swipe{display:block}}
@media (max-width:760px){
  .d-wrap{padding-block:26px 56px;gap:38px}
  .d-top h1{font-size:25px}
  .d-sub{font-size:18px}
  .d-fig{grid-template-columns:minmax(0,1fr)}
  .d-shot{justify-self:center}
  .d-fig.d-wide .d-shot{justify-self:stretch}
}
@media (prefers-reduced-motion:no-preference){html{scroll-behavior:smooth}}
@media print{
  *{-webkit-print-color-adjust:exact;print-color-adjust:exact}
  body{background:#fff}
  .d-wrap{max-width:none;padding:0}
  .d-toc,.d-swipe{display:none}
  .d-fig{break-inside:avoid}
  .br{zoom:.72}
}
"""


def balanced_div(text, start):
    """start 指向一个 <div 的起点；返回与它配对的 </div> 之后的下标。"""
    assert text.startswith("<div", start), text[start:start + 30]
    depth = 0
    for m in re.finditer(r"<div\b|</div>", text[start:]):
        depth += -1 if m.group(0) == "</div>" else 1
        if depth == 0:
            return start + m.end()
    raise SystemExit(f"❌ gallery.html 里 div 不配对（从下标 {start} 起）")


def frame_of(gallery, opt_id):
    """取 id=opt_id 那个方案块里的设备帧（手机 .ph 或浏览器 .brwrap）。"""
    hits = [m.start() for m in re.finditer(re.escape(f'id="{opt_id}"'), gallery)]
    if len(hits) != 1:
        raise SystemExit(f"❌ gallery.html 里 id=\"{opt_id}\" 出现 {len(hits)} 次，应当恰好 1 次")
    s = gallery.rfind("<div", 0, hits[0])
    block = gallery[s:balanced_div(gallery, s)]
    m = re.search(r'<div class="(ph|brwrap)">', block)
    if not m:
        raise SystemExit(f"❌ {opt_id} 里没找到设备帧（.ph / .brwrap）")
    return block[m.start():balanced_div(block, m.start())], m.group(1)


def dedent_block(s):
    lines = s.split("\n")
    pad = min((len(l) - len(l.lstrip()) for l in lines[1:] if l.strip()), default=0)
    return "\n".join([lines[0]] + [l[pad:] for l in lines[1:]])


def build():
    gallery = SRC.read_text(encoding="utf-8")
    style = re.search(r"<style>(.*?)</style>", gallery, re.S).group(1)
    tokens = re.search(r"^\.scr\{.*?^\}", style, re.S | re.M)
    a, b = style.find("/* ── 手机框"), style.find("@media (max-width:860px)")
    if not tokens or a < 0 or b < 0 or a > b:
        raise SystemExit("❌ gallery.html 的 <style> 结构变了：找不到产品界面 token 或帧样式的起止标记")
    frame_css = tokens.group(0) + "\n" + style[a:b].rstrip()

    # 图号自动化：文案里写 {fig:<帧 id>}，这里按实际顺序换成「图 N」。
    # 插一张图就会让后面所有图号顺移，硬编码迟早对不上——2026-09-18 加 5 张图时就错过一次。
    fignum = {}
    _i = 0
    for _part in PARTS:
        for _f in _part["figs"]:
            _i += 1
            fignum[_f["src"]] = _i

    def resolve(text):
        def _sub(m):
            src = m.group(1)
            if src not in fignum:
                raise SystemExit(f"❌ 文案里引用了不存在的帧：{src}")
            return f"图 {fignum[src]}"
        return re.sub(r"\{fig:([a-z0-9-]+)\}", _sub, text)

    for _part in PARTS:
        _part["intro"] = resolve(_part["intro"])
        for _f in _part["figs"]:
            _f["purpose"] = resolve(_f["purpose"])
            _f["points"] = [resolve(x) for x in _f["points"]]

    n, toc, parts_html, used = 0, [], [], set()
    for part in PARTS:
        links, figs_html = [], []
        for f in part["figs"]:
            n += 1
            frame, kind = frame_of(gallery, f["src"])
            used |= {c for cls in re.findall(r'class="([^"]+)"', frame) for c in cls.split()}
            wide = kind == "brwrap"
            links.append(f'<a href="#fig-{n}">图 {n} {htmllib.escape(f.get("toc", f["title"]))}</a>')
            lis = "\n".join(f"        <li>{p}</li>" for p in f["points"])
            swipe = '\n      <p class="d-swipe">这张图比较宽，可以左右滑动着看；在电脑上打开更清楚。</p>' if wide else ""
            figs_html.append(f"""  <article class="d-fig{' d-wide' if wide else ''}" id="fig-{n}">
    <div class="d-cap"><span class="d-no">图 {n}</span><h3>{htmllib.escape(f["title"])}</h3><span class="d-who">{htmllib.escape(f["who"])}</span></div>
    <div class="d-shot">
      {dedent_block(frame)}{swipe}
    </div>
    <div class="d-say">
      <p>{f["purpose"]}</p>
      <ul>
{lis}
      </ul>
    </div>
  </article>""")
        toc.append(f'    <div class="d-toc-row"><span>{part["title"].split("、")[1]}</span>{"".join(links)}</div>')
        parts_html.append(f"""<section class="d-part" id="{part['id']}">
  <div class="d-part-head">
    <h2>{part["title"]}</h2>
    <p>{part["intro"]}</p>
  </div>
{chr(10).join(figs_html)}
</section>""")

    # 帧里用到的每个类，都得在抽出来的 CSS 里有规则；否则说明图廊把它定义在了外壳那一段，甲方版会裸奔
    css_all = frame_css + SHELL_CSS
    naked = sorted(c for c in used if not re.search(r"\." + re.escape(c) + r"(?![\w-])", css_all))
    if naked:
        raise SystemExit(f"❌ 帧里用到、但抽出的样式里没有规则的类：{naked}")

    counts = [len(p["figs"]) for p in PARTS]

    chg = "\n".join(f'    <li><span class="q">{resolve(q)}</span><span class="a">{resolve(a)}</span><span class="f">看{resolve(f)}</span></li>' for q, a, f in CHANGES)
    how = "\n".join(f"    <li><b>{t}</b>{resolve(d)}</li>" for t, d in HOW)
    ask = "\n".join(f"    <li>{resolve(x)}</li>" for x in ASK)
    page = f"""<!doctype html>
<html lang="zh-CN">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<meta name="color-scheme" content="light">
<title>类器官送检系统 界面设计稿</title>
<style>{SHELL_CSS}
/* ── 以下为产品界面（设备帧）的样式，由 build_client_preview.py 从 gallery.html 原样抽取 ── */
{frame_css}
</style>
</head>
<body>
<div class="d-wrap">
<header class="d-top">
  <p class="d-eyebrow">{CLIENT}</p>
  <h1>类器官送检与样本管理系统<span class="d-sub">界面设计稿</span></h1>
  <p class="d-meta">第 {VERSION} 版 · {DATE_CN} · 小程序 {counts[0]} 张图，网页工作台 {counts[1]} 张图</p>
  <p class="d-lead">这份稿子把系统的主要页面画了出来，方便您在开发之前先看一眼：页面上的内容、叫法和操作顺序，和您平时的做法是否对得上。第 1 版您看过之后提的意见、以及 9 月 18 日您在测试问题记录表里提的意见，这一版都改进去了，首页和「我的」也一起重新整理了。看完有想法，直接说图号告诉我们就行。</p>
  <section class="d-chg" aria-label="这一版改了什么">
    <h2>和第 1 版相比，这一版改了 {len(CHANGES)} 处</h2>
    <ol>
{chg}
    </ol>
  </section>
  <ul class="d-how">
{how}
  </ul>
  <nav class="d-toc" aria-label="图目录">
{chr(10).join(toc)}
  </nav>
</header>

{(chr(10) * 2).join(parts_html)}

<section class="d-ask">
  <h2>看图时请重点留意三件事</h2>
  <ol>
{ask}
  </ol>
  <p>有想法直接在微信里说图号就行，比如「图 9 少了一列」。之后想调整，也随时可以提。</p>
</section>

<p class="d-foot">{VENDOR} · 界面设计稿 第 {VERSION} 版 · {DATE_ISO} · 图中数据均为示意</p>
</div>
</body>
</html>
"""
    bad = [w for w in FORBIDDEN if w in page]
    if bad:
        raise SystemExit(f"❌ 甲方版里出现了不该有的内容：{bad}")
    if n != EXPECTED_FIGS:
        raise SystemExit(f"❌ 应当是 {EXPECTED_FIGS} 张图，实际 {n} 张")
    return page


def main():
    page = build()
    if "--check" in sys.argv:
        if not OUT.exists() or OUT.read_text(encoding="utf-8") != page:
            print(f"❌ 甲方版设计稿过期了（图廊或文案改过）：重新跑 build_client_preview.py → {OUT.name}")
            sys.exit(1)
        print(f"✅ 甲方版设计稿与图廊一致：{OUT.name}")
        return
    OUT.write_text(page, encoding="utf-8")
    print(f"[ok] {OUT.relative_to(HERE.parent.parent)} · {len(page.encode('utf-8')) // 1024} KB · {sum(len(p['figs']) for p in PARTS)} 张图 · 零外部依赖")


if __name__ == "__main__":
    main()
