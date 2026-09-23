# doc/manual · 操作说明（SYS-MANUAL-001）

两份交付材料：

| 文件 | 是什么 | 读者 |
| --- | --- | --- |
| `内部人员操作说明.md` / `pdf/内部人员操作说明.pdf` | 按一天的工作顺序写的工作台 + 小程序内部功能说明，**文末带合同《系统功能清单》14 行对照表** | 实验室内部人员、管理员（培训讲义） |
| `外部用户操作说明.md` / `pdf/外部用户操作说明.pdf` | **一页**图文，讲清登录 / 填表（拍照识别 + 核对）/ 找回修改 / 看结果与下载 | 合作单位人员（发给甲方转发） |

- `shots/` —— 配图（测试环境 + 种子假数据；`shots/_page-text.txt` 是每张图上的文字，用来核对图里不是错误态，**不用打开 PNG**）
- `tools/shots.mjs` —— 配图与缩略图的生成脚本（Playwright，锚点用 `code/miniapp/package.json` 解析依赖）
- `style.css` —— 两份说明共用的排版样式（导出 PDF 时套用，保证可复现）

## 重新导出 PDF（三步，先定稿 md 再导）

```bash
cd <repo-root>
# 1) 起模式 B 环境（要重拍配图时才需要；只重导 PDF 可跳过）
bash doc/waves/tools/qa-up.sh --backend-port 8092 --web-port 8093 --mp-port 9202
# reseed 会把已渲染的文档产物清掉，而「文档列表 / 预览 / 渲染状态」三张配图要有已渲染的文档；
# 先补渲染两条 seed 里的「已完成」文档，再截图（只动本地测试库，重跑 reseed 即可复位）：
export LQG_VERIFY_ENV_FILE="$PWD/.tmp/qa-env/8092/verify.env"
for S in 9000001001 9000001004; do for A in internal external; do
  bash doc/verify/api.sh --as admin POST "/lqg/doc/$S/sample_qc/render?audience=$A"; done; done
bash doc/verify/api.sh --as admin POST "/lqg/doc/9000001005/sample_qc/render?audience=internal"
node doc/manual/tools/shots.mjs          # 配图 → doc/manual/shots/（34 张：工作台 14 + 小程序 8 + 登录页 1 + 外部 6 + 一页窄图 5）

# 2) md → HTML（implicit_figures 才会把图注渲染成 figcaption）
pandoc "doc/manual/内部人员操作说明.md" -f gfm+implicit_figures -t html5 -s -o .tmp/manual/内部人员操作说明.html
pandoc "doc/manual/外部用户操作说明.md" -f gfm+implicit_figures -t html5 -s -o .tmp/manual/外部用户操作说明.html

# 3) HTML → PDF（-u 让 `shots/xxx.png` 这类相对路径能解析；-s 套 style.css）
weasyprint -u "$PWD/doc/manual/" -s doc/manual/style.css .tmp/manual/内部人员操作说明.html doc/manual/pdf/内部人员操作说明.pdf
weasyprint -u "$PWD/doc/manual/" -s doc/manual/style.css .tmp/manual/外部用户操作说明.html doc/manual/pdf/外部用户操作说明.pdf

# 自检：页数 + 中文能抽出来（抽不出「送检」= 字体没嵌对）
pdfinfo doc/manual/pdf/外部用户操作说明.pdf | grep '^Pages:'
pdftotext doc/manual/pdf/外部用户操作说明.pdf - | tr -d ' \n' | grep -c 送检
```

## 两个坑（别踩回去）

1. **中文 PDF 必须走 weasyprint + Noto Serif SC**（`style.css` 的 `font-family` 已写死
   `"Noto Serif SC", "Songti SC", serif`）。用 LibreOffice 走 md→pdf 会出方框，
   `pdftotext` 抽不出中文，accept 直接红。
2. **`md` 改了必须重导 PDF**：accept 用 `test <pdf> -nt <md>` 断「PDF 比 md 新」。
   顺序永远是 **先定稿 md → 再导 PDF**；`shots/` 里的图改了也要重导。
3. 外部版要**一页**：`style.css` 里 `.ext` 那一组是专门压排版的（字号 8.5pt、行高 1.32、
   `@page ext` 窄边距）；正文删一行就可能从 2 页回到 1 页，改完务必用 `pdfinfo` 实测页数。
   外部版用的配图是 `shots/c-ext-*.png`（`tools/shots.mjs` 末尾按 290px 高裁出来的窄图），
   直接引用手机整屏长图会溢出到第 2 页。
