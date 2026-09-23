# ── SYS-PROD-001 · 生产 nginx 的安全响应头 ────────────────────────────────────────
# 落盘位置：code/deploy/prod/nginx/security-headers.inc
#   → compose 挂进容器 /etc/nginx/conf.d/lqg-security-headers.inc
#   由 workspace.conf 的 443 server 块 `include` 一次（nginx 的 add_header **不继承**：
#   在 server 层写一次、location 里不再写，否则内层的会覆盖外层的）。
#
# ★ 不写 Content-Security-Policy：plus-ui 是 Vite 产出的 SPA，dist 里有内联的
#   module preload / 动态 style，收紧 CSP 要先把产物扫一遍再逐条放行。在没有真环境
#   可验证的前提下写一条「看起来安全」的 CSP，等于给上线埋一个白屏 —— 留给上线演练
#   （SYS-PROD-001 的 online_phase_backlog）按真实产物定，不在本票里猜。

# 强制 HTTPS：一年。★ 只有 443 的 server 块 include 本文件，80 块不 include ——
# 明文端口上发 HSTS 没有意义，而且会让证书出问题时的排查更麻烦。
add_header Strict-Transport-Security "max-age=31536000" always;
# 禁止浏览器按内容嗅探 MIME（上传目录如果哪天被当静态服务，这条能挡住 html 被当脚本执行）
add_header X-Content-Type-Options    "nosniff" always;
# 禁止被别的站点 iframe 嵌入（工作台里全是内部数据，不需要被嵌）
add_header X-Frame-Options           "SAMEORIGIN" always;
# 跨站跳转时不泄漏完整 URL（URL 里可能带样本编号）
add_header Referrer-Policy           "strict-origin-when-cross-origin" always;
# 关掉用不上的浏览器能力（相机走小程序，不在工作台里）
add_header Permissions-Policy        "camera=(), microphone=(), geolocation=()" always;
