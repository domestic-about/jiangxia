# DOC-PDF-001 · Gotenberg + 开源中文字体（docx → PDF 的那一步）
#
# 为什么必须自己建一层：
#   甲方三份模板里的中文字体是**宋体**，官方 gotenberg/gotenberg:8（Debian）里没有宋体、
#   也没有任何 CJK 字体。不装字体的话 LibreOffice 会回退到 DejaVu Sans —— 中文全成方框
#   （pdftotext 抽不出字），这正是本票 accept 1 前两段与 counterfeit 第 1 条要防的事故。
#
# 装的是什么（都是开源字体，文件与许可**一并进了仓库** code/deploy/common/gotenberg/fonts/）：
#   · NotoSerifSC-{Regular,Bold}.otf  ← 思源宋体的 Google 发行版（Noto Serif SC），OFL-1.1
#                                        替换模板里的「宋体」（eastAsia）
#   · Tinos-{Regular,Bold}.ttf        ← Times New Roman 的**度量兼容**开源替代，OFL-1.1
#                                        替换模板里的 Times New Roman（ascii/hAnsi/cs）
#   ★ 为什么 Latin 也要换：容器里没有 Times New Roman，LibreOffice 默认回退到
#     **Liberation Serif**，而 accept 1 明写 `! pdffonts | grep -qiE 'DejaVu|Liberation'`：
#     留着 Times New Roman 不用，就有 Liberation 出现在 PDF 里 → 断言红。
#     Tinos 与 Times New Roman 同度量，换它之后版式不漂、甲方原件上的拉丁字形差异最小。
#   ★ 不许用宋体 / 微软雅黑等有版权的字体（ticket §3：不引入商业字体）。
#
# 字体装到系统字体目录（不是用户目录）：LibreOffice 以 gotenberg 用户跑 soffice，
# 系统目录一定在 fontconfig 的搜索路径里。
FROM gotenberg/gotenberg:8

USER root

COPY fonts/ /usr/local/share/fonts/lqg/
# fc-cache 让 fontconfig 立刻认到新字体；a+r 保证非 root 的 soffice 进程读得到
RUN fc-cache -fv && chmod -R a+r /usr/local/share/fonts/lqg && fc-list | grep -iE 'Noto Serif SC|Tinos' | sort

# 官方镜像默认就是非 root 的 gotenberg 用户；显式切回去，不把容器跑成 root
USER gotenberg
