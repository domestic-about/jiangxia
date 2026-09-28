# ── SYS-STAGING-001 · 工作台/接口反代共用头 ──────────────────────────────────
# 测试机上路径：/opt/lqg-test/nginx/proxy-headers.inc → 容器 /etc/nginx/conf.d/lqg-proxy-headers.inc
# 模板：code/deploy/common/nginx/proxy-headers.inc.template
proxy_set_header Host              $host;
proxy_set_header X-Real-IP         $remote_addr;
proxy_set_header X-Forwarded-For   $proxy_add_x_forwarded_for;
proxy_set_header X-Forwarded-Proto $scheme;
proxy_http_version 1.1;
# 后端有 SSE（plus-ui 的 VITE_APP_SSE=true），长连接别被默认 60s 掐掉
proxy_set_header Connection        "";
proxy_read_timeout 300s;
proxy_send_timeout 300s;
proxy_connect_timeout 15s;
proxy_buffering off;
