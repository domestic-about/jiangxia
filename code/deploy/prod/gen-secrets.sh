#!/usr/bin/env bash
# 生产密钥一次生成（独立验收 V13 / V14 / V15，F4）：把 .env 里**空着或还是占位值**的密钥现生成、写回 .env。
#
#   cd code/deploy/prod
#   cp .env.example .env && chmod 600 .env
#   bash gen-secrets.sh            # 缺省改 ./.env；也可以 bash gen-secrets.sh /path/to/.env
#
# 生成哪些（值只写进 .env，终端上只打印变量名，不打印值）：
#   LQG_DB_PASSWORD / LQG_REDIS_PASSWORD      32 位十六进制随机串
#   LQG_JWT_SECRET                            Sa-Token 的 JWT 签名密钥，64 位十六进制（≥ 32 字符，护栏要求）
#   LQG_ENCRYPT_PASSWORD                      字段加密（AES）口令，32 个字母数字（AES 要求 16/24/32 位）
#   LQG_API_REQUEST_PRIVATE_KEY + VITE_APP_RSA_PUBLIC_KEY   接口加密「请求」那一对 RSA-2048
#   LQG_API_RESPONSE_PUBLIC_KEY + VITE_APP_RSA_PRIVATE_KEY  接口加密「响应」那一对 RSA-2048
#     · 后端两个（LQG_API_*）由 compose 注入 backend 容器；
#     · 前端两个（VITE_APP_RSA_*）在 deploy.sh artifacts 阶段 `pnpm build:prod` 时从环境里读
#       （deploy.sh 会先 source .env；Vite 不覆盖已经存在的进程环境变量）。
#     · 格式：私钥 = PKCS#8 DER 的 Base64，公钥 = X.509（SubjectPublicKeyInfo）DER 的 Base64，都不带 PEM 头尾。
#
# ★ 幂等、**绝不覆盖已有的真值**：只处理「没有这一行 / 值为空 / 值以 change-me 开头」的键。
#   尤其是 LQG_ENCRYPT_PASSWORD —— 上线后再换它，已经加密落库的三列就再也读不出来了。
# ★ LQG_ENCRYPT_PASSWORD 生成后**另存一份给 Kevin**（部署手册 §8）；丢了 = 供体姓名 / 住院号 / 患者编号永久不可读。
set -euo pipefail

ENV_FILE="${1:-$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/.env}"
[ -f "${ENV_FILE}" ] || { echo "[error] 没有 ${ENV_FILE}：先 cp .env.example .env && chmod 600 .env" >&2; exit 2; }
command -v openssl >/dev/null || { echo "[error] 需要 openssl" >&2; exit 2; }
chmod 600 "${ENV_FILE}"
umask 077

current() { sed -n "s/^$1=//p" "${ENV_FILE}" | tail -1; }

needs_value() {
  local v
  v="$(current "$1")"
  [ -z "${v}" ] || case "${v}" in change-me*) true ;; *) false ;; esac
}

set_value() { # set_value KEY VALUE —— 有这一行就原地替换，没有就追加（值里只有 [A-Za-z0-9+/=]，sed 安全）
  local key="$1" value="$2" tmp
  tmp="$(mktemp "${ENV_FILE}.XXXXXX")"
  if grep -q "^${key}=" "${ENV_FILE}"; then
    awk -v k="${key}" -v v="${value}" 'BEGIN{FS=OFS="="} $1==k {print k "=" v; next} {print}' "${ENV_FILE}" > "${tmp}"
  else
    cat "${ENV_FILE}" > "${tmp}"
    printf '%s=%s\n' "${key}" "${value}" >> "${tmp}"
  fi
  mv "${tmp}" "${ENV_FILE}"
  chmod 600 "${ENV_FILE}"
  echo "  ✓ 已生成 ${key}"
}

alnum() { # alnum N：N 个字母数字
  local out=""
  while [ "${#out}" -lt "$1" ]; do
    out="${out}$(openssl rand -base64 48 | tr -dc 'A-Za-z0-9')"
  done
  printf '%s' "${out:0:$1}"
}

rsa_pair() { # rsa_pair PRIV_VAR PUB_VAR：生成一对 RSA-2048，私钥 PKCS#8 / 公钥 X.509，Base64 单行
  local dir
  dir="$(mktemp -d)"
  openssl genrsa -out "${dir}/key.pem" 2048 2>/dev/null
  openssl pkcs8 -topk8 -nocrypt -in "${dir}/key.pem" -outform DER -out "${dir}/key.p8.der"
  openssl rsa -in "${dir}/key.pem" -pubout -outform DER -out "${dir}/pub.der" 2>/dev/null
  printf -v "$1" '%s' "$(base64 < "${dir}/key.p8.der" | tr -d '\n')"
  printf -v "$2" '%s' "$(base64 < "${dir}/pub.der" | tr -d '\n')"
  rm -rf "${dir}"
}

echo "── gen-secrets → ${ENV_FILE}"
needs_value LQG_DB_PASSWORD      && set_value LQG_DB_PASSWORD "$(openssl rand -hex 16)"
needs_value LQG_REDIS_PASSWORD   && set_value LQG_REDIS_PASSWORD "$(openssl rand -hex 16)"
needs_value LQG_JWT_SECRET       && set_value LQG_JWT_SECRET "$(openssl rand -hex 32)"
if needs_value LQG_ENCRYPT_PASSWORD; then
  set_value LQG_ENCRYPT_PASSWORD "$(alnum 32)"
  echo "  ⚠️ LQG_ENCRYPT_PASSWORD 是新生成的：现在就另存一份给 Kevin（丢了三列加密数据永久不可读）"
fi

# 接口加解密：两对密钥必须成对生成（半对缺失时整对重来，避免前后端对不上）
if needs_value LQG_API_REQUEST_PRIVATE_KEY || needs_value VITE_APP_RSA_PUBLIC_KEY; then
  rsa_pair REQ_PRIV REQ_PUB
  set_value LQG_API_REQUEST_PRIVATE_KEY "${REQ_PRIV}"
  set_value VITE_APP_RSA_PUBLIC_KEY "${REQ_PUB}"
fi
if needs_value LQG_API_RESPONSE_PUBLIC_KEY || needs_value VITE_APP_RSA_PRIVATE_KEY; then
  rsa_pair RESP_PRIV RESP_PUB
  set_value LQG_API_RESPONSE_PUBLIC_KEY "${RESP_PUB}"
  set_value VITE_APP_RSA_PRIVATE_KEY "${RESP_PRIV}"
fi
echo "完成。已有的真值一个没动；deploy.sh preflight 会再核一遍两对 RSA 是否成对。"
