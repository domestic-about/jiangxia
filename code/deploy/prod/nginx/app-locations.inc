# ── SYS-PROD-001 · 工作台站点的应用层 location（443 与 8080 两个入口共用）────────────────
# 2026-10-21 起生产机上有宝塔（Kevin 的运维工具，不停）：公网 80/443 归宿主宝塔 nginx，
# 它终止 TLS 后反代到本容器的 8080（compose 只发布到 127.0.0.1:18080）。443 块保留，供直连场景。
root  /usr/share/nginx/html;
index index.html;

# 上传体积上限 60MB（票面 §2 硬要求；后端 spring.servlet.multipart 与小程序上传同一口径）。
# 超限时 nginx 直接回 413，不会打到后端。
client_max_body_size 60m;
client_body_timeout  300s;

# 上传体积超限（V05）：回与后端同口径的 JSON（{code,msg}），不回 nginx 默认的 413 HTML ——
# 工作台 / 小程序按响应体的 code、msg 给提示。数值与后端 spring.servlet.multipart 同口径。
error_page 413 = @lqg_413;
location @lqg_413 {
    default_type "application/json; charset=utf-8";
    return 413 '{"code":413,"msg":"上传的文件太大：单个文件不能超过 50MB，一次上传合计不能超过 60MB","data":null}';
}

# 安全响应头统一在 security-headers.inc 里（含 HSTS）；放在 server 层，
# 各个 location 里不再重复 add_header（nginx 的 add_header 不继承，重复写只会互相覆盖）。
include /etc/nginx/conf.d/lqg-security-headers.inc;

# ── 0) actuator 一律不对外（V18）：根路径与 /prod-api 前缀两条路都挡，统一 404 ─────────
#    上游 SecurityConfig 用 Basic 认证护 /actuator，而本项目从没配过那对口令 —— 期望值是字面量
#    「null:null」，dev / test 下全量暴露（env、heapdump 都在）。这里在入口直接挡掉；
#    `^~` 前缀比下面的 `^~ /prod-api/` 长、又压过正则，/prod-api/actuator/… 也进不去。
location ^~ /actuator {
    return 404;
}
location ^~ /prod-api/actuator {
    return 404;
}

# ── 1) 网页工作台的 API：plus-ui 的 VITE_APP_BASE_API=/prod-api ─────────────────
#    带 `^~` 前缀，避免被下面的正则 location 抢走。proxy_pass 末尾的 `/` 会**剥掉** /prod-api。
location ^~ /prod-api/ {
    proxy_pass http://backend:8080/;
    include /etc/nginx/conf.d/lqg-proxy-headers.inc;
}

# ── 2) 小程序 / doc/verify/api.sh 直连的根路径接口（**不剥前缀**）──────────────
#    小程序 env/.env.production 的 VITE_SERVER_BASEURL=https://<域名>，请求 /auth/login、/mp/...；
#    api.sh 请求 /auth/login、/lqg/sys/ping —— 都不带 /prod-api。
location ~ ^/(auth|lqg|mp|system|monitor|resource|tool|workflow)/ {
    proxy_pass http://backend:8080;
    include /etc/nginx/conf.d/lqg-proxy-headers.inc;
}

# ── 3) 工作台静态资源 + SPA history 回退 ────────────────────────────────────────
#    缺 try_files 这条，深链直接 404（首页 200 会骗人，栈坑手册 §7）
location / {
    try_files $uri $uri/ /index.html;
}

# 静态资源缓存（plus-ui 的产物带 hash，长缓存安全）
location ~* \.(?:js|css|png|jpg|jpeg|gif|svg|woff2?|ttf|eot|ico)$ {
    expires 30d;
    access_log off;
    try_files $uri =404;
}

# acme 的 webroot 在生产不对外服务（HTTPS 上的挑战走 80），显式挡掉免得被当静态目录扫
location ^~ /.well-known/acme-challenge/ {
    return 404;
}

