#!/usr/bin/env bash
# SYS-PROD-001 · 生产机宿主 nginx（宝塔）站点 + Let's Encrypt 证书 + 反代到 compose 的 nginx
#
# 2026-10-21：生产机上装了宝塔（Kevin 的运维工具，**不停**），公网 80/443 归宝塔 nginx。
# 所以生产也走测试环境的分层（本脚本由 code/deploy/test/remote/02-host-nginx.sh 改来）：
#   公网 80/443 ──> 宿主宝塔 nginx（本脚本落的 vhost，终止 TLS）──> 127.0.0.1:${LQG_LOCAL_WEB_PORT}
#   ──> compose 的 nginx 容器 8080 入口 ──> 静态 plus-ui dist / /prod-api 与根路径接口 ──> backend
# ★ 前提：域名 ICP 备案已通过。未备案时阿里云会掐掉带本域名的 80/443 请求，HTTP-01 签不出证书。
# ★ 只写**自己域名**的文件：/www/server/panel/vhost/nginx/<域名>.conf、/www/wwwroot/<域名>/。
#
# 用法（服务器上，root）：bash /opt/lqg/remote/02-host-nginx.sh
#   或本机：bash code/deploy/prod/deploy.sh host-nginx
set -euo pipefail

[ -f /opt/lqg/.env ] && { set -a; . /opt/lqg/.env; set +a; }
DOMAIN="${LQG_PROD_DOMAIN:?LQG_PROD_DOMAIN 未设}"
WEB_PORT="${LQG_LOCAL_WEB_PORT:-18080}"
SITE_ROOT="/www/wwwroot/${DOMAIN}"
SITE_CERT_DIR="/www/server/panel/vhost/cert/${DOMAIN}"
ACME_ORDER_DIR="/www/server/panel/vhost/letsencrypt/${DOMAIN}"
VHOST="/www/server/panel/vhost/nginx/${DOMAIN}.conf"
BT_ACME="/www/server/panel/class/acme_v2.py"

log() { echo "[$(date '+%F %T')] $*"; }

[ "$(id -u)" = "0" ] || { echo "必须是 root" >&2; exit 2; }
install -d "${SITE_ROOT}"

# 证书在哪个目录：站点证书目录 > ACME 订单目录
resolve_cert_dir() {
  if [ -s "${SITE_CERT_DIR}/fullchain.pem" ] && [ -s "${SITE_CERT_DIR}/privkey.pem" ]; then
    echo "${SITE_CERT_DIR}"; return 0
  fi
  if [ -s "${ACME_ORDER_DIR}/fullchain.pem" ] && [ -s "${ACME_ORDER_DIR}/privkey.pem" ]; then
    echo "${ACME_ORDER_DIR}"; return 0
  fi
  return 1
}

# ── 阶段 A：先只监听 80，且只为 ACME 服务 ────────────────────────────────────
# HTTP-01 要求 http://<域名>/.well-known/acme-challenge/<token> 能从公网取到，
# 而这时还没有证书 → 不能让 443/ssl 块存在（nginx -t 会因证书文件缺失直接失败）。
if ! CERT_DIR="$(resolve_cert_dir)"; then
  log "阶段 A：写 80 端口的最简 vhost（只为证书验证服务）"
  cat > "${VHOST}" <<EOS
# SYS-PROD-001 阶段 A（证书签发中）：只在 80 上服务 ACME challenge。
# 证书签出来后本文件会被覆盖成完整的 80+443 版本。
server {
    listen 80;
    server_name ${DOMAIN};
    root ${SITE_ROOT};

    location ^~ /.well-known/acme-challenge/ {
        allow all;
        try_files \$uri =404;
    }

    location / {
        default_type text/plain;
        return 503 "cert issuance in progress\n";
    }

    access_log /www/wwwlogs/${DOMAIN}.log;
    error_log  /www/wwwlogs/${DOMAIN}.error.log;
}
EOS
  nginx -t
  nginx -s reload
  log "阶段 A 完成，nginx 已 reload"

  log "阶段 B：宝塔 ACME 签发 Let's Encrypt 证书（HTTP-01，长操作）"
  cd /www/server/panel
  # 与面板「SSL → Let's Encrypt → 文件验证」同一条路径；auth_to = 站点根目录
  if btpython "${BT_ACME}" --domain "${DOMAIN}" --type http --path "${SITE_ROOT}" 2>&1 | tail -30; then
    log "acme_v2 CLI 正常退出"
  else
    log "acme_v2 CLI 非零退出（下面继续按证书文件是否落盘判定）"
  fi

  CERT_DIR="$(resolve_cert_dir)" || {
    echo "FAIL: 证书没签出来：${SITE_CERT_DIR}/fullchain.pem 与 ${ACME_ORDER_DIR}/fullchain.pem 都不存在"
    echo "  排查：① 域名是否解析到本机（getent hosts ${DOMAIN}）；② 安全组是否放行 80 入方向；"
    echo "        ③ 宝塔面板 → 网站 → 该域名的 SSL 页手动签一次；④ /www/server/panel/logs/letsencrypt.log"
    exit 1
  }
else
  log "证书已存在（${CERT_DIR}），跳过签发阶段"
fi
log "使用证书目录：${CERT_DIR}"

# ── 阶段 C：完整的 80 + 443 ────────────────────────────────────────────────
log "阶段 C：写完整的 80（ACME + 301）+ 443（TLS + 反代 127.0.0.1:${WEB_PORT}）"
cat > "${VHOST}" <<EOS
# SYS-PROD-001 · ${DOMAIN} 的宿主 nginx 配置（宝塔 nginx）
#   由 code/deploy/prod/remote/02-host-nginx.sh 生成；重跑部署会原地覆盖。
#   分层：本 vhost 终止 TLS → 全量反代到 compose 的 nginx（127.0.0.1:${WEB_PORT}）
#   → 那里做静态 plus-ui dist + /prod-api 与根路径接口反代 → backend 容器。
#   证书由宝塔 ACME（Let's Encrypt, HTTP-01）签发在订单目录，宝塔的续签任务会原地重写，
#   所以这里直接引用它（不做拷贝，免得续签后副本过期）。
# ★ 不要把这个文件改回「面板 AddSite 生成的样子」：面板生成的 conf 里有
#   location ~ .*\.(js|css)?\$ 这类**正则** location，会抢在 location / 前面把请求当静态文件处理，
#   而本机站根是空的 → 工作台的 JS/CSS 全 404。这里用一句 location ^~ / 整体反代，简单且不会踩。
server {
    listen 80;
    listen [::]:80;
    server_name ${DOMAIN};

    # 证书续期（HTTP-01）必须一直可服务；这条 ^~ 前缀比下面的 location / 更长，优先命中
    location ^~ /.well-known/acme-challenge/ {
        root ${SITE_ROOT};
        allow all;
        try_files \$uri =404;
    }

    location / {
        return 301 https://\$host\$request_uri;
    }

    access_log /www/wwwlogs/${DOMAIN}.log;
    error_log  /www/wwwlogs/${DOMAIN}.error.log;
}

server {
    listen 443 ssl;
    listen [::]:443 ssl;
    http2 on;
    server_name ${DOMAIN};

    ssl_certificate     ${CERT_DIR}/fullchain.pem;
    ssl_certificate_key ${CERT_DIR}/privkey.pem;
    ssl_protocols TLSv1.2 TLSv1.3;
    ssl_ciphers ECDHE-ECDSA-AES128-GCM-SHA256:ECDHE-RSA-AES128-GCM-SHA256:ECDHE-ECDSA-AES256-GCM-SHA384:ECDHE-RSA-AES256-GCM-SHA384:ECDHE-ECDSA-CHACHA20-POLY1305:ECDHE-RSA-CHACHA20-POLY1305;
    ssl_prefer_server_ciphers off;
    ssl_session_cache shared:SSL:10m;
    ssl_session_timeout 10m;
    ssl_session_tickets off;
    add_header Strict-Transport-Security "max-age=31536000" always;

    # 证书验证目录（续期用）在 443 上同样可达
    location ^~ /.well-known/acme-challenge/ {
        root ${SITE_ROOT};
        allow all;
        try_files \$uri =404;
    }

    # 其余全部交给 compose 的 nginx（静态 + /prod-api + 根路径接口）
    location ^~ / {
        proxy_pass http://127.0.0.1:${WEB_PORT};
        proxy_set_header Host              \$host;
        proxy_set_header X-Real-IP         \$remote_addr;
        proxy_set_header X-Forwarded-For   \$proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto \$scheme;
        proxy_set_header X-Forwarded-Port  443;
        proxy_http_version 1.1;
        # 后端有 SSE：清掉默认的 Connection: close，别让长连接 60s 断
        proxy_set_header Connection        "";
        proxy_read_timeout 300s;
        proxy_send_timeout 300s;
        proxy_connect_timeout 15s;
        # 上传/导出：60m = 后端 multipart 的 max-request-size；超限回 JSON（见下面的 @lqg_413，V05）
        client_max_body_size 60m;
        error_page 413 = @lqg_413;
        proxy_buffering off;
    }

    # 上传体积超限：回与后端同口径的 JSON（{code,msg}），不回 nginx 默认的 413 HTML
    location @lqg_413 {
        default_type "application/json; charset=utf-8";
        return 413 '{"code":413,"msg":"上传的文件太大：单个文件不能超过 50MB，一次上传合计不能超过 60MB","data":null}';
    }

    access_log /www/wwwlogs/${DOMAIN}.log;
    error_log  /www/wwwlogs/${DOMAIN}.error.log;
}
EOS

nginx -t
nginx -s reload
log "阶段 C 完成：https://${DOMAIN}/ 已生效（→ 127.0.0.1:${WEB_PORT}）"

# ── 自检（在 box 本机打自己的域名 + 反代后的接口）─────────────────────────────
log "自检：curl --resolve ${DOMAIN}:443:127.0.0.1 https://${DOMAIN}/lqg/sys/ping"
curl -sS -o /dev/null -w '  /lqg/sys/ping -> HTTP %{http_code}\n' --max-time 20 \
  --resolve "${DOMAIN}:443:127.0.0.1" "https://${DOMAIN}/lqg/sys/ping" || true
curl -sS -o /dev/null -w '  /            -> HTTP %{http_code}\n' --max-time 20 \
  --resolve "${DOMAIN}:443:127.0.0.1" "https://${DOMAIN}/" || true
log "证书到期：$(openssl x509 -in "${CERT_DIR}/fullchain.pem" -noout -enddate 2>/dev/null || echo unknown)"
