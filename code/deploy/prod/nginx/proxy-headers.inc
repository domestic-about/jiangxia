# ── SYS-PROD-001 · 生产 nginx 的反代共用头 ────────────────────────────────────────
# 落盘位置：code/deploy/prod/nginx/proxy-headers.inc
#   → compose 挂进容器 /etc/nginx/conf.d/lqg-proxy-headers.inc
# 与测试环境那份同源（code/deploy/test/nginx/proxy-headers.inc），差别只有一处：
#   生产在上游（容器）直接终止 TLS，X-Forwarded-Proto 必须是 https —— $scheme 在 443 的
#   server 块里天然就是 https，所以这里照旧写 $scheme 即可，不要硬编码。
proxy_set_header Host              $host;
proxy_set_header X-Real-IP         $remote_addr;
proxy_set_header X-Forwarded-For   $proxy_add_x_forwarded_for;
proxy_set_header X-Forwarded-Proto $scheme;
proxy_set_header X-Forwarded-Host  $host;
proxy_set_header X-Forwarded-Port  $server_port;
proxy_http_version 1.1;
# 后端有 SSE（plus-ui 的 VITE_APP_SSE=true），长连接别被默认 60s 掐掉
proxy_set_header Connection        "";
proxy_read_timeout 300s;
proxy_send_timeout 300s;
proxy_connect_timeout 15s;
proxy_buffering off;
# 大文件上传 / 文档下载：与 server 层的 client_max_body_size 60m 对齐
proxy_request_buffering off;
