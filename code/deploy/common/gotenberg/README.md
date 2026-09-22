# Gotenberg（docx → PDF 的转换容器）

DOC-PDF-001 引入。这一层的唯一目的是**把中文开源字体装进官方 Gotenberg 镜像**：

```bash
docker build -t lqg-gotenberg:8 code/deploy/common/gotenberg
```

dev / test 的 compose 都是从 `code/deploy/common/gotenberg` 这个目录 build 的
（`docker compose -f code/deploy/dev/docker-compose.yml up -d gotenberg`）。

## 字体

| 文件 | 字体 | 许可 | 替代谁 |
|---|---|---|---|
| `fonts/NotoSerifSC-Regular.otf` / `-Bold.otf` | Noto Serif SC（思源宋体 Google 发行版） | `fonts/OFL-NotoSerifSC.txt`（SIL OFL 1.1） | 模板里的 eastAsia「宋体」 |
| `fonts/Tinos-Regular.ttf` / `-Bold.ttf` | Tinos（Times New Roman 度量兼容） | `fonts/OFL-Tinos.txt`（SIL OFL 1.1） | 模板里的 ascii/hAnsi/cs「Times New Roman」 |

来源：`https://github.com/google/fonts`（`ofl/notoserifsc`、`ofl/tinos`）与
`https://github.com/googlefonts/tinos`。两份都是 OFL-1.1，允许随产品分发。
**不用**宋体 / 微软雅黑 / 黑体等商业字体（ticket §3）。

## 端口与网络

- 容器内固定 `3000`（Gotenberg 默认）。
- dev：宿主只绑 `127.0.0.1:${LQG_GOTENBERG_PORT:-3001}` —— 后端跑在**宿主**上（qa-up 起的 JVM），
  必须能连到容器；绑在环回上等于「只在内网暴露」，同机别的项目/局域网进不来。
- test：**不发布宿主端口**，backend 容器与它在同一个 compose 网络里，用服务名
  `http://gotenberg:3000`（见 `code/deploy/test/docker-compose.yml` 的 `LQG_GOTENBERG_URL`）。
  测试机上 3000 端口是**别的项目**的 next-server（SYS-STAGING-001 的端口纪律），所以一个字节都不占。

## 超时

`--api-timeout=60s`（与 ticket §2 的「超时 60 秒」一致）。客户端侧
（`PdfConvertService`）自己还有一层排队 + 超时预算，两边都失败得**可见**：
超时/连不上都会让该 `(docKind, audience)` 整体 `failed` 并把原因写进
`t_lqg_doc_file.error_msg`（工作台可见、可重试）。

## 并发

Gotenberg 的 LibreOffice 模块本身就是一次转一份（转几百 MB 的 Word 要吃内存）。
后端侧 `PdfConvertService` 再串一层**单线程执行器**（ticket §0 口径复述 2），
同一台机器上永远只有一份文档在转。
