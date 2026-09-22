# D3 r1 · L2（端侧 UI）回归资产

本目录是 **D3 第 1 轮 QA 的 L2 分片**自写资产（独享：后端 8081 / PG 5433 / Redis 6380 / MinIO 9002-9003 / plus-ui 8082 / miniapp H5 9200）。
不使用 mock、只连真后端 + 真库 + 真 seed；**不读任何图片**（截图只作落盘证据）。

## 前置

```bash
bash .tmp/run-backend.sh                     # 8081（dev + --api-decrypt.enabled=false）
cd code/plus-ui && npm_config_store_dir=<ws>/.pnpm-store pnpm dev            # 8082
cd code/miniapp && VITE_MOCK_LOGIN=1 npm_config_store_dir=<ws>/.pnpm-store pnpm dev:h5 --port 9200
bash doc/verify/reseed.sh --yes
```

## 跑法（每条自身会先 reseed，可重复跑）

```bash
node doc/waves/regression/D3/L2-g1-web.mjs        # 组1 工作台（24 检查）
node doc/waves/regression/D3/L2-g2-mp-int.mjs     # 组2 小程序内部（32 检查）
node doc/waves/regression/D3/L2-g3-mp-ext.mjs     # 组3 小程序外部 + 工作台核验闭环（21 检查）
```

定点复现（可选，只打日志）：

```bash
node doc/waves/regression/D3/L2-probe-dom.mjs            # 小程序 DOM 探针（不进断言）
node doc/waves/regression/D3/L2-probe-verify-drawer.mjs  # 核验抽屉缺陷取证（请求/响应/异常全抓）
```

截图落在 `L2-shots/<组名>/`；导出的 xlsx 落在 `L2-shots/g1-web/*.xlsx`。

## 结果（D3 r1）

| 组 | 结果 | 说明 |
|---|---|---|
| 组1 工作台 | 24/24 pass | 列表/抽屉/核验抽屉置灰+说明/提示列悬停+跳转/三个导出真下文件 |
| 组2 小程序内部 | 32/32 pass | 首建 → 补填脱水时间 → 工序圆点 +1 → 样本页带样本进填写页 |
| 组3 小程序外部+核验 | 19/21 | 2 红 = 同一条 **S0**：核验抽屉「判为有效并保存」点不动（详见 `doc/waves/qa/D3-r1-L2.json`） |

未覆盖：微信开发者工具 / 真机（沙箱 EPERM + 需扫码）→ 小程序侧一律 H5 + Playwright 等价覆盖。
