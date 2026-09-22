#!/usr/bin/env bash
# SYS-ACCEPT-001 · 行为判据的**确定性夹具**（H1b / H3b / H4 需要真渲染产物才能点下载 / 看原图）。
#
# 为什么需要它：`qa-up.sh` 的 reseed **不灌 `t_lqg_doc_file`**（issue #217 那族），
# 而 seed 的图片位是假地址（`https://seed.invalid` → `signedUrl` 返回 null → 前端跳过）。
# 所以「文档下载」「点缩略图看原图」「四个下载入口」这三条判据要先把产物造成：
#   ① 上传两张 **2400×1600 真 PNG**（长边 > 2000 → 后端另存 .jpg 预览图，
#      于是 `url`(.png) 与 `previewUrl`(.jpg) **不同**，H3b 才有区分对可断）；
#   ② 挂到 9000001001/sample-qc 的 `pretreat` 图片位；
#   ③ 对 1001 的三份文档 unpublish + publish 触发渲染，等 `t_lqg_doc_file` 全 done。
#
# 幂等：每次先 `reseed.sh --yes` 回到确定性快照，再重新造 —— 反复跑结果一致。
# ★ 只用环境自身的后端（`LQG_VERIFY_ENV_FILE` / 默认 8094），不碰 8080/8081。
set -uo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../../../.." && pwd)"
cd "${ROOT}"
export LQG_VERIFY_ENV_FILE="${LQG_VERIFY_ENV_FILE:-${ROOT}/.tmp/qa-env/8094/verify.env}"
# ★ 不要往 PATH 前面塞 /opt/homebrew/bin：那会把 pyenv 的 python3 顶掉，而 `doc/verify/db.py`
#   依赖它那份 psycopg2（顶掉后 db.py 只往 stderr 打一行「需要 psycopg2」→ 快照读成空串 → 假红，实测踩过）。

DIR="${ROOT}/.tmp/sys-accept-001"
mkdir -p "${DIR}"

echo "── fixture ①：reseed 到确定性快照"
bash doc/verify/reseed.sh --yes >/dev/null 2>&1 || { echo "[error] reseed 失败" >&2; exit 2; }
# ★ reseed 会把服务端会话作废，但 `api.sh` 的 token 缓存在 `$TMPDIR/lqg-verify-token-*`（**不是 /tmp**，
#   macOS 的 TMPDIR 是 /var/folders/…），`reseed.sh` 自己不清它 —— 不清就会拿着上一个环境的死 token
#   一路 401（实测：所有 `--as staff` 调用 401「认证失败，无法访问系统资源」，判据假红）。
#   `qa-up.sh` 第 190 行就是这么做的，这里照抄。
rm -f "${TMPDIR:-/tmp}"/lqg-verify-token-* 2>/dev/null || true

echo "── fixture ②：造两张 2400×1600 真 PNG（长边>2000 → previewUrl 另存 .jpg）"
python3 - "${DIR}" <<'PY'
import os, struct, sys, zlib
d = sys.argv[1]
def png(path, w, h, rgb):
    row = b'\x00' + bytes(rgb) * w
    def chunk(t, x):
        return struct.pack('>I', len(x)) + t + x + struct.pack('>I', zlib.crc32(t + x) & 0xffffffff)
    data = (b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', w, h, 8, 2, 0, 0, 0))
            + chunk(b'IDAT', zlib.compress(row * h, 6)) + chunk(b'IEND', b''))
    open(path, 'wb').write(data)
for tag, rgb in (('orig-A', (220, 40, 40)), ('orig-B', (40, 90, 220))):
    p = os.path.join(d, f'{tag}.png')
    if not os.path.exists(p):
        png(p, 2400, 1600, rgb)
PY

echo "── fixture ③：上传 + 挂到 9000001001/sample-qc 的 pretreat 位"
OSS_IDS=()
for f in orig-A orig-B; do
  RESP="$(bash doc/verify/api.sh --as admin --form "file=@${DIR}/${f}.png" POST /resource/oss/upload)"
  OID="$(printf '%s' "${RESP}" | python3 -c 'import json,sys;print(json.load(sys.stdin)["data"]["ossId"])')" || { echo "[error] 上传 ${f} 失败：${RESP}" >&2; exit 2; }
  OSS_IDS+=("${OID}")
  bash doc/verify/api.sh --as staff POST /lqg/qc/9000001001/sample-qc/image "{\"slot\":\"pretreat\",\"ossId\":${OID}}" >/dev/null \
    || { echo "[error] 挂图 ${f}(ossId=${OID}) 失败" >&2; exit 2; }
done
echo "     ossIds=${OSS_IDS[*]}"

echo "── fixture ④：触发三份文档 + 合并件的渲染"
# ① publish 那条路（端到端，等价 L2 上一轮的做法）：业务码**不吞**，逐条打出来
for dt in sample-qc organoid-qc score; do
  U="$(bash doc/verify/api.sh --as staff --bizcode POST "/lqg/qc/9000001001/${dt}/unpublish" 2>&1 || echo '(api 调用失败)')"
  P="$(bash doc/verify/api.sh --as staff --bizcode POST "/lqg/qc/9000001001/${dt}/publish" 2>&1 || echo '(api 调用失败)')"
  echo "     ${dt}: unpublish → ${U:-（空）} ｜ publish → ${P:-（空）}"
done
# ② 显式 render 兜底（不依赖 publish 的异步排队）—— 实测遇到过一次 publish 的请求没落库、
#    t_lqg_doc_file 迟迟为 0；夹具必须自己保证产物存在，否则判据会假红（工具红不是产品红）。
for kind in sample_qc organoid_qc organoid_score merged; do
  for aud in internal external; do
    R="$(bash doc/verify/api.sh --as staff --bizcode POST "/lqg/doc/9000001001/${kind}/render?audience=${aud}" 2>&1 || echo '(api 调用失败)')"
    echo "     render ${kind}/${aud} → ${R:-（空）}"
  done
done

echo "── fixture ⑤：等 t_lqg_doc_file 全 done"
OK=0
for i in $(seq 1 60); do
  SNAP="$(python3 doc/verify/db.py --quiet --sql "SELECT count(*) FILTER (WHERE render_status='done') || '/' || count(*) || '/' || count(*) FILTER (WHERE render_status='failed') FROM t_lqg_doc_file WHERE sample_id=9000001001 AND del_flag='0'" 2>/dev/null | head -1)"
  done_n="${SNAP%%/*}"; rest="${SNAP#*/}"; tot_n="${rest%%/*}"; fail_n="${rest##*/}"
  if [ "${tot_n}" -ge 36 ] && [ "${done_n}" = "${tot_n}" ] && [ "${fail_n}" = "0" ]; then OK=1; echo "     ${SNAP}（done/total/failed）"; break; fi
  sleep 3
done
[ "${OK}" = 1 ] || { echo "[error] 渲染产物没能在 180s 内全部 done（最后一次 ${SNAP:-?}）" >&2; exit 2; }

python3 - "${DIR}/fixture.json" "${OSS_IDS[0]}" "${OSS_IDS[1]}" "${SNAP}" <<'PY'
import json, sys
out, a, b, snap = sys.argv[1:5]
json.dump({"sample_id": 9000001001, "doc_kind": "sample_qc", "slot": "pretreat",
           "uploaded_oss_ids": [a, b], "doc_file_done_total_failed": snap,
           "image_urls": {"orig-A": "2400x1600 PNG（原图 .png）", "orig-B": "2400x1600 PNG（原图 .png）"},
           "preview": "后端另存 2000px 内 JPEG（.jpg）→ url 与 previewUrl 不同"},
          open(out, "w", encoding="utf-8"), ensure_ascii=False, indent=1)
PY
echo "── fixture 就绪（${DIR}/fixture.json）"
