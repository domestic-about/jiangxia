#!/usr/bin/env bash
# D1 QA r2（L0+L1 片）新增的独立检查 —— 与 doc/waves/regression/D1/verify.sh 互补，不动那个文件。
# 跑在 reseed 后的确定性快照上；会建两个临时手机号账号，请用
#   bash doc/verify/reseed.sh --yes && bash doc/waves/tools/clean-orphan-accounts.sh --yes
# 收尾。退出码 0 = 新增检查全绿 / 1 = 有断言不成立 / 2 = 环境或工具错。
#
#   bash doc/waves/qa/D1-r2-L01-extra-checks.sh
#
# 相对回归包补的狠处：
#   E1 字典不只点名：26 个 lqg_ 字典的**值/标签/评分 remark** 与 doc/authority/field-ssot.yaml 的 dicts 段逐项对账（权威侧，另一数据源）。
#   E2 角色不只 101-103：全库 lqg_* 角色集合必须精确等于 {101:lqg_admin,102:lqg_internal,103:lqg_external}（防多一个 104 蒙混）。
#   E3 「同一手机号 1 行」按**全表**（含软删行）判，并证明三条路径拿到的是**同一个 user_id**（回归包只数活跃行）。
#   E4 种子段账号恰 8 个；clean-orphan 复核后全库无重号、无越界运行时账号。
#   E5 反 stale 覆盖整棵后端树：fat jar 内的 ruoyi-lqg 字节码与当前源码重新编译的 .class 逐文件 md5 一致。
set -uo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
cd "${ROOT}"
set -a; . "${ROOT}/doc/verify/verify.env"; set +a

FAILED=(); ENV_BROKEN=0
ok()  { printf '  \033[32m✓\033[0m %s\n' "$1"; }
bad() { printf '  \033[31m✗\033[0m %s\n' "$1"; FAILED+=("$1"); }
env() { printf '  \033[33m!\033[0m %s\n' "$1"; ENV_BROKEN=1; FAILED+=("ENV: $1"); }
head1(){ printf '\n\033[1m%s\033[0m\n' "$1"; }
clear_tokens() { rm -f "${TMPDIR:-/tmp}"/lqg-verify-token-* 2>/dev/null || true; }
q() { python3 doc/verify/db.py --sql "$1"; }

clear_tokens
head1 "E1/E2 字典 vs 权威 field-ssot.yaml；lqg_ 角色集合精确"
python3 - <<'PY'
import sys, yaml, psycopg2
kv={}
for line in open('doc/verify/verify.env',encoding='utf-8'):
    line=line.strip()
    if line and not line.startswith('#') and '=' in line:
        k,v=line.split('=',1); kv[k.strip()]=v.strip()
auth=yaml.safe_load(open('doc/authority/field-ssot.yaml',encoding='utf-8'))['dicts']
conn=psycopg2.connect(host=kv['LQG_DB_HOST'],port=int(kv['LQG_DB_PORT']),dbname=kv['LQG_DB_NAME'],
                      user=kv['LQG_DB_USER'],password=kv.get('LQG_DB_PASSWORD',''))
conn.set_session(readonly=True,autocommit=True); cur=conn.cursor()
cur.execute("SELECT dict_type, dict_value, dict_label, remark FROM sys_dict_data WHERE dict_type LIKE 'lqg\\_%'")
db={}
for t,v,l,r in cur.fetchall(): db.setdefault(t,{})[v]=(l,r)
cur.execute("SELECT dict_type FROM sys_dict_type WHERE dict_type LIKE 'lqg\\_%'")
types={r[0] for r in cur.fetchall()}
cur.execute("SELECT role_id, role_key FROM sys_role WHERE role_key LIKE 'lqg\\_%'")
roles={int(i):k for i,k in cur.fetchall()}
bad=[]
if set(auth)!=types: bad.append(f"字典清单不等：权威多 {sorted(set(auth)-types)} 库多 {sorted(types-set(auth))}")
for t,spec in auth.items():
    want={str(k):v for k,v in spec['values'].items()}
    if set(want)!=set(db.get(t,{})): bad.append(f"{t}: 值集合 权威={sorted(want)} 库={sorted(db.get(t,{}))}")
    for v,label in want.items():
        g=db.get(t,{}).get(v)
        if g and str(g[0])!=str(label): bad.append(f"{t}.{v}: 标签 权威={label!r} 库={g[0]!r}")
    for v,score in (spec.get('scores') or {}).items():
        g=db.get(t,{}).get(v)
        if not g: bad.append(f"{t}.{v}: 库里缺失（分值 {score} 无处可对）")
        elif str(g[1] if g[1] is not None else '')!=str(score): bad.append(f"{t}.{v}: remark 权威={score!r} 库={g[1]!r}")
if roles!={101:'lqg_admin',102:'lqg_internal',103:'lqg_external'}:
    bad.append(f"lqg_ 角色集合不等：{roles}")
if bad:
    print("  \033[31m✗\033[0m E1/E2 对账失败："); [print('     -',b) for b in bad]; sys.exit(1)
print(f"  \033[32m✓\033[0m E1 26 个 lqg_ 字典（含 {sum(len(s.get('scores') or {}) for s in auth.values())} 个评分档位的 remark）与权威逐项一致")
print("  \033[32m✓\033[0m E2 lqg_ 角色集合精确 = {101:lqg_admin,102:lqg_internal,103:lqg_external}")
PY
[ $? -ne 0 ] && bad "E1/E2 见上"

head1 "E3 同一手机号恒 1 行（全表，含软删）+ 三条路径同一 user_id"
PH=13800000095
api_jq_uid() { bash doc/verify/api.sh --as "$1" GET /mp/me | jq -r 'select(.code==200) | .data.userId // empty'; }
rm -f "${TMPDIR:-/tmp}"/lqg-verify-token-*
U1="$(api_jq_uid "phone:xA:${PH}")"
[ -n "${U1}" ] && ok "E3a 首登自动建：uid=${U1}" || bad "E3a 首登没拿到 uid"
rm -f "${TMPDIR:-/tmp}"/lqg-verify-token-*
U2="$(api_jq_uid "phone:xB:${PH}")"
[ -n "${U2}" ] && ok "E3b 换微信号再登：uid=${U2}" || bad "E3b 换微信号再登没拿到 uid"
rm -f "${TMPDIR:-/tmp}"/lqg-verify-token-*
UP="$(bash doc/verify/api.sh --as admin POST /lqg/auth/staff "{\"phone\":\"${PH}\",\"name\":\"QA-R2-extra\",\"roleKey\":\"lqg_internal\",\"password\":\"Lqg@r2x123\"}" | jq -r 'select(.code==200) | (.data.upgraded|tostring) + "|" + (.data.userId|tostring)')"
ok "E3c 按手机号授权升级：upgraded|uid=${UP}"
[ "${U1}" = "${U2}" ] && [ "${U1}" = "${UP#*|}" ] \
  && ok "E3 三条路径拿到同一个 user_id（${U1}）——不是建了新账号" \
  || bad "E3 user_id 不一致：${U1} / ${U2} / ${UP#*|}"
ROW="$(q "SELECT count(*)||'|'||count(DISTINCT user_id) FROM sys_user WHERE phonenumber='${PH}'")"
[ "${ROW}" = "1|1" ] && ok "E3 该手机号全表（含软删）恰 1 行 1 个 user_id" || bad "E3 该手机号行数/去重用户 = ${ROW}（期望 1|1）"
DUP="$(q "SELECT count(*) FROM (SELECT phonenumber FROM sys_user WHERE phonenumber IS NOT NULL AND phonenumber<>'' GROUP BY phonenumber HAVING count(*)>1) t")"
[ "${DUP}" = "0" ] && ok "E3 全库无任何重号（含软删行）" || bad "E3 全库有 ${DUP} 个重号"

head1 "E4 种子段账号数 / 越界运行时账号"
N="$(q "SELECT count(*) FROM sys_user WHERE user_id BETWEEN 9000000000 AND 9000009999 AND del_flag='0'")"
[ "${N}" = "8" ] && ok "E4 种子段活跃账号恰 8 个" || bad "E4 种子段活跃账号 = ${N}（期望 8）"
OUT="$(q "SELECT count(*) FROM sys_user WHERE user_id NOT BETWEEN 9000000000 AND 9000009999 AND (user_name LIKE 'wx\_%' OR user_name LIKE 'lqg\_%')")"
echo "  · 非种子段 wx_/lqg_ 账号（本脚本自己建的，收尾靠 clean-orphan 清）：${OUT}"

head1 "E5 反 stale：fat jar 内 lqg 字节码 == 当前源码重编译"
JAR="code/RuoYi-Vue-Plus/ruoyi-admin/target/ruoyi-admin.jar"
T="$(mktemp -d)"
unzip -o -q "${JAR}" 'BOOT-INF/lib/ruoyi-lqg*.jar' -d "${T}" && mkdir -p "${T}/n" && (cd "${T}/n" && unzip -q -o "${T}"/BOOT-INF/lib/ruoyi-lqg*.jar)
HA="$( (cd "${T}/n" && find . -name '*.class' | sort | xargs md5 -q) | md5 -q)"
HB="$( (cd code/RuoYi-Vue-Plus/ruoyi-modules/ruoyi-lqg/target/classes && find . -name '*.class' | sort | xargs md5 -q) | md5 -q)"
rm -rf "${T}"
[ -n "${HA}" ] && [ "${HA}" = "${HB}" ] && ok "E5 jar 内 $(find code/RuoYi-Vue-Plus/ruoyi-modules/ruoyi-lqg/target/classes -name '*.class' | wc -l | tr -d ' ') 个 .class 与当前源码编译结果逐文件一致（${HA}）" \
  || bad "E5 字节码不一致：jar=${HA} 源码编译=${HB}"
# 整棵树的反 stale：*.java 一律不许比 jar 新；资源文件（Maven 会做 @占位符@ 过滤，
# application.yml 进 jar 后被替换成 dev / 版本号，不能拿字节比）逐文件按「占位符换成通配」
# 的正则与 jar 内 BOOT-INF/classes 的副本做全文匹配。
python3 - "${JAR}" <<'PY'
import os, re, subprocess, sys, zipfile
jar = sys.argv[1]
root = 'code/RuoYi-Vue-Plus'
old = int(os.stat(jar).st_mtime)
newer = []
for dp, dn, fn in os.walk(root):
    dn[:] = [d for d in dn if d not in ('target', 'logs', '.git')]
    for f in fn:
        p = os.path.join(dp, f)
        if os.stat(p).st_mtime > old: newer.append(p)
z = zipfile.ZipFile(jar)
bad = []
for p in newer:
    if p.endswith('.java'):
        bad.append(f'{p}: 源码比 jar 新（打了包没重编）'); continue
    rel = p.split('/src/main/resources/', 1)
    if len(rel) < 2 or not rel[1].startswith('application'):
        bad.append(f'{p}: 比 jar 新且无法与 jar 内副本比对'); continue
    entry = 'BOOT-INF/classes/' + rel[1]
    if entry not in z.namelist():
        bad.append(f'{p}: jar 内没有 {entry}'); continue
    src = open(p, encoding='utf-8').read()
    # Maven 两种过滤/属性占位符（@key@ 与 ${key}）都当通配，其余必须逐字一致
    norm = re.sub(r'\$\{[A-Za-z0-9_.\-]+\}', '\x00', re.sub(r'@[A-Za-z0-9_.\-]+@', '\x00', src))
    pat = re.escape(norm).replace(re.escape('\x00'), '.+?')
    if not re.fullmatch(pat, z.read(entry).decode('utf-8'), re.S):
        bad.append(f'{p}: 与 jar 内 {entry} 不一致（真 stale，不只是 Maven 占位符）')
if bad:
    print("  \033[31m✗\033[0m E5 整树反 stale 失败："); [print('     -', b) for b in bad]; sys.exit(1)
print(f"  \033[32m✓\033[0m E5 整棵后端树（除 target/logs）无 .java 比 jar 新；{len(newer)} 个更新的资源文件与 jar 内副本逐字一致（Maven 占位符已归一）")
PY
[ $? -ne 0 ] && bad "E5 整树反 stale 见上"

printf '\n═══ 汇总 ═══\n'
if [ "${#FAILED[@]}" -eq 0 ]; then echo "D1 r2 新增检查全绿"; exit 0; fi
printf '\033[31m失败 %d 条：\033[0m\n' "${#FAILED[@]}"; for f in "${FAILED[@]}"; do printf '  - %s\n' "$f"; done
[ "${ENV_BROKEN}" = 1 ] && exit 2; exit 1
