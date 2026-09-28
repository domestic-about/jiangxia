#!/usr/bin/env bash
# SYS-STAGING-001 · 在测试机上把小程序体验版上传到微信（固定 IP，绕开开发机的代理出口）
#
# 为什么上传不在开发机做：微信要求上传来源 IP 在「开发管理 → 开发设置 → 小程序代码上传 → IP 白名单」里，
# 而开发机常年跑在代理后面（TUN 模式，出口 IP 是代理节点、还会变）。测试机是固定公网 IP，
# 一次加进白名单就长期可用 —— 这正是把它做成自动化一步的前提。
#
# ★ 构建**不在**这台机器上做（项目纪律 gotchas §6.5：不同 OS 产物不同，体验版真机上会渲染空）。
#   本机（macOS）`pnpm upload:mp --mode=test --build-only` 构建并过两道守卫 → deploy.sh 把产物同步过来
#   → 这里只 `--skip-build` 上传（该脚本的配置/产物守卫照样再跑一遍）。
#
# 由 code/deploy/test/deploy.sh 上传后执行（分离：日志 logs/04-miniapp-upload.log，标记 .done）。
set -euo pipefail
cd /opt/lqg-test

set -a; . /opt/lqg-test/.env; set +a
MP_DIR=/opt/lqg-test/miniapp
log() { echo "[$(date '+%F %T')] $*"; }

log "===== 1) 检查物料 ====="
[ -f "${MP_DIR}/scripts/upload-mp.mjs" ] || { log "  ✗ 缺 ${MP_DIR}/scripts/upload-mp.mjs（deploy.sh 的上传阶段没跑？）"; exit 1; }
[ -d "${MP_DIR}/dist/build/mp-weixin-test" ] || { log "  ✗ 缺构建产物 ${MP_DIR}/dist/build/mp-weixin-test"; exit 1; }
[ -f "${MP_DIR}/private.key" ] || { log "  ✗ 缺上传密钥 ${MP_DIR}/private.key"; exit 1; }
chmod 600 "${MP_DIR}/private.key"
log "  产物文件数：$(find "${MP_DIR}/dist/build/mp-weixin-test" -type f | wc -l | tr -d ' ')"
log "  密钥权限：$(stat -c '%a' "${MP_DIR}/private.key")"

log "===== 2) miniprogram-ci 依赖（装在独立目录，只装一次）====="
# ★ 为什么不在 ${MP_DIR} 里直接 npm install：那里的 package.json 是**小程序自己的**（整棵 uni-app /
#   vue 依赖树），npm 会试图把整棵树按 node 24 重新解析 → ERESOLVE 直接失败（2026-09-28 实测）。
#   这里只需要一个包，所以另起一个干净的依赖目录，再把它的 node_modules **软链**进上传目录 ——
#   ESM 解析 bare specifier 是沿「导入文件所在目录」向上找 node_modules，软链照样命中。
CI_DEPS=/opt/lqg-test/mp-ci-deps
if [ -d "${MP_DIR}/node_modules/miniprogram-ci" ]; then
  log "  已就位：$(node -e "console.log(require('${MP_DIR}/node_modules/miniprogram-ci/package.json').version)" 2>/dev/null || echo '?')"
else
  install -d "${CI_DEPS}"
  [ -f "${CI_DEPS}/package.json" ] || echo '{"name":"lqg-mp-ci-deps","private":true}' > "${CI_DEPS}/package.json"
  log "  安装中（走服务器上的 npm 镜像 ${npm_config_registry:-默认}）…"
  ( cd "${CI_DEPS}" && npm install --no-save --no-audit --no-fund miniprogram-ci@2.1.31 2>&1 | tail -4 )
  [ -d "${CI_DEPS}/node_modules/miniprogram-ci" ] || { log "  ✗ miniprogram-ci 装不上（看上面 npm 输出）"; exit 1; }
  ln -sfn "${CI_DEPS}/node_modules" "${MP_DIR}/node_modules"
  log "  已装并软链：$(node -e "console.log(require('${MP_DIR}/node_modules/miniprogram-ci/package.json').version)")"
fi

log "===== 3) 上传（node scripts/upload-mp.mjs --mode=test --skip-build）====="
cd "${MP_DIR}"
# appid 从 code/miniapp/env/.env 同步过来的那份读（deploy.sh 也允许用 LQG_WX_APPID 覆盖）
export LQG_WX_APPID="${LQG_WX_APPID:-}"
export LQG_WX_PRIVATE_KEY="${MP_DIR}/private.key"
# 版本号：测试版按 .env 里的固定值（Kevin 2026-09-28 要求写死 1.1.2）；没配就走脚本自带的
# <version>.<mode>.<commit> 串。带了 --version 时脚本不再拼后缀。
VER_ARGS=()
[ -n "${LQG_MINIPROGRAM_VERSION:-}" ] && VER_ARGS=(--version="${LQG_MINIPROGRAM_VERSION}")
log "  版本号：${LQG_MINIPROGRAM_VERSION:-（未配，用脚本缺省的 <version>.<mode>.<commit>）}"
set +e
node scripts/upload-mp.mjs --mode=test --skip-build "${VER_ARGS[@]+"${VER_ARGS[@]}"}" 2>&1 | tee /tmp/lqg-mp-upload.out | grep -vE '^\s*\[object Object\]' | tail -30
rc="${PIPESTATUS[0]}"
set -e

log "===== 4) 结果 ====="
if [ "${rc}" = "0" ]; then
  qr="$(ls -t "${MP_DIR}"/dist/体验版二维码-*.png 2>/dev/null | head -1 || true)"
  log "  ✓ 上传成功${qr:+；体验版二维码：${qr}}"
  log "  ⚠ 上传成功后还要在「微信公众平台 → 版本管理」把该版本**设为体验版**，并把体验成员加进去"
else
  log "  ✗ 上传失败（退出码 ${rc}）——完整输出在 /tmp/lqg-mp-upload.out"
  # 最常见的两类，直接把该做什么打出来，省得每次翻文档
  if grep -q 'invalid ip' /tmp/lqg-mp-upload.out; then
    ip="$(grep -o 'invalid ip: [0-9.]*' /tmp/lqg-mp-upload.out | head -1 | awk '{print $3}')"
    log "  → 这是**微信侧 IP 白名单**未放行：把 ${ip:-<上面的 IP>} 加进"
    log "     「微信公众平台 → 开发管理 → 开发设置 → 小程序代码上传 → IP 白名单」，然后重跑本阶段"
  fi
  if grep -q 'errCode":-10008' /tmp/lqg-mp-upload.out && ! grep -q 'invalid ip' /tmp/lqg-mp-upload.out; then
    log "  → -10008 通常是密钥与 appid 不匹配，或密钥被重置过：核对 private.<appid>.key"
  fi
  exit "${rc}"
fi
