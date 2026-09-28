#!/usr/bin/env bash
# D3 r2 · L3 独立邪路探针（自写。独立重跑 qa_scope L3 五条 + #145 后端侧，不引用 r1 结论）
# 用法: bash doc/waves/regression/D3/L3-r2-probes.sh <g1|g2|g3|g4|g5|g6>
# 说明：每条「被拒」= 业务码白名单 + msg 关键词；随后必跟一条库内不变断言（--eq 精确值）。
set -uo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../../.." && pwd)"
cd "${ROOT}"
API="bash doc/verify/api.sh"
DB="python3 doc/verify/db.py"
TMPL='_input/templates/样本记录信息表模板.xlsx'
PASS=0; FAIL=0
ok(){ echo "  [PASS] $1"; PASS=$((PASS+1)); }
bad(){ echo "  [FAIL] $1"; FAIL=$((FAIL+1)); }
val(){ ${DB} --quiet --sql "$1" 2>/dev/null | head -1; }
eqv(){ local desc="$1" sql="$2" want="$3"
  if ${DB} --sql "$sql" --eq "$want" >/tmp/l3r2.out 2>&1; then ok "${desc} [=${want}]"
  else bad "${desc} 库里不是 [${want}]"; sed 's/^/      | /' /tmp/l3r2.out | head -4; fi; }
rows(){ local desc="$1" sql="$2" want="$3"
  if ${DB} --sql "$sql" --rows "$want" >/tmp/l3r2.out 2>&1; then ok "${desc} [rows=${want}]"
  else bad "${desc} 行数不是 ${want}"; sed 's/^/      | /' /tmp/l3r2.out | head -4; fi; }
# rej <desc> <as> <method> <path> <want-code> <msg-keyword> [body]
rej(){ local desc="$1" as="$2" method="$3" path="$4" want="$5" kw="$6" body="${7:-}"
  local out code msg
  out=$(${API} --as "$as" --bizcode "$method" "$path" ${body:+"$body"} 2>&1)
  code="$(printf '%s' "$out" | head -1 | cut -f1)"
  msg="$(printf '%s' "$out" | head -1 | cut -f2-)"
  if [ "$code" = "$want" ] && printf '%s' "$msg" | grep -qF "$kw"; then ok "${desc} -> code=${code} msg=${msg}"
  else bad "${desc} -> 拿到 code=${code} msg=${msg}（期望 code=${want} 且含「${kw}」）"; fi; }
# ok200 <desc> <as> <method> <path> [body]
ok200(){ local desc="$1" as="$2" method="$3" path="$4" body="${5:-}"
  local out code
  out=$(${API} --as "$as" --bizcode "$method" "$path" ${body:+"$body"} 2>&1)
  code="$(printf '%s' "$out" | head -1 | cut -f1)"
  if [ "$code" = "200" ]; then ok "${desc} -> code=200"; else bad "${desc} -> code=${code} $(printf '%s' "$out"|head -1|cut -f2-)"; fi; }
reseed(){ bash doc/verify/reseed.sh --yes >/dev/null 2>&1 || { echo "reseed 失败"; exit 2; }
  rm -f "${TMPDIR:-/tmp}"/lqg-verify-token-* 2>/dev/null || true; }
# jq 断言：命令失败即红
ja(){ local desc="$1" json="$2"; shift 2
  if printf '%s' "$json" | jq -e "$@" >/dev/null 2>&1; then ok "$desc"; else bad "$desc"; printf '%s' "$json" | jq -c '.' 2>/dev/null | head -c 300 | sed 's/^/      | /'; echo; fi; }

case "${1:-}" in

g1) # ── L3-1 染色与挂靠规则：4 条被拒 + 库内不变 ─────────────────────────
  echo "== G1 染色与挂靠规则 =="
  reseed
  BASE="$(val "SELECT count(*) FROM t_lqg_embed")"; echo "  baseline t_lqg_embed=${BASE}"
  [ "$BASE" -gt 0 ] && ok "基线非空（t_lqg_embed=${BASE}）" || bad "基线为 0，后续「不多行」断言会恒绿"
  rej "1a 无染色+HE 同时提交被拒" staff POST /lqg/embed 500 "「无染色」与其余染色互斥" '{"sampleId":9000001005,"paraffinBlockNo":"T-X1","stainTypes":["NONE","HE"]}'
  eqv "1a-库 拒后未多行" "SELECT count(*) FROM t_lqg_embed" "${BASE}"
  rej "1b 选「其他」不写名称被拒" staff POST /lqg/embed 500 "选了「其他」必须写具体染色名称" '{"sampleId":9000001005,"paraffinBlockNo":"T-X2","stainTypes":["OTHER"]}'
  eqv "1b-库 拒后未多行" "SELECT count(*) FROM t_lqg_embed" "${BASE}"
  rej "1c 字典外染色值(PAS)被拒" staff POST /lqg/embed 500 "不在字典 lqg_stain_type 里" '{"sampleId":9000001005,"paraffinBlockNo":"T-X3","stainTypes":["PAS"]}'
  eqv "1c-库 拒后未多行" "SELECT count(*) FROM t_lqg_embed" "${BASE}"
  eqv "1d-前置 1002 仍是 pending（否则 1d 恒绿）" "SELECT verify_status FROM t_lqg_sample WHERE id=9000001002" "pending"
  rej "1d 挂到待核验样本(1002)被拒" staff POST /lqg/embed 500 "只能挂到已核验有效的样本" '{"sampleId":9000001002,"paraffinBlockNo":"T-X4"}'
  eqv "1d-库 拒后未多行" "SELECT count(*) FROM t_lqg_embed" "${BASE}"
  eqv "1-终态 无 T-X% 行" "SELECT count(*) FROM t_lqg_embed WHERE paraffin_block_no LIKE 'T-X%'" "0"
  echo "RESULT g1 pass=${PASS} fail=${FAIL}"
  ;;

g2) # ── L3-2 有已核验石蜡块的样本改判无效被拒 ───────────────────────────
  echo "== G2 valid→invalid 闸 =="
  reseed
  B="$(val "SELECT count(*) FROM t_lqg_embed WHERE sample_id=9000001004 AND verify_status='valid' AND del_flag='0'")"
  echo "  1004 名下已核验石蜡块=${B}"
  [ "${B:-0}" -ge 1 ] && ok "前置：1004 确有 ≥1 个已核验石蜡块" || bad "前置不成立（1004 无已核验块），断言会恒绿"
  eqv "2-前置 1004 现为 valid" "SELECT verify_status FROM t_lqg_sample WHERE id=9000001004" "valid"
  rej "2 有已核验石蜡块的样本改判无效被拒" staff PUT /lqg/sample/9000001004/verify 500 "不能改判无效" '{"action":"invalid","reason":"想改判"}'
  eqv "2-库 仍 valid" "SELECT verify_status FROM t_lqg_sample WHERE id=9000001004" "valid"
  eqv "2-库 invalid_reason 仍为空" "SELECT count(*) FROM t_lqg_sample WHERE id=9000001004 AND invalid_reason IS NULL" "1"
  eqv "2-库 1004 石蜡块数未变" "SELECT count(*) FROM t_lqg_embed WHERE sample_id=9000001004 AND verify_status='valid' AND del_flag='0'" "${B}"
  echo "RESULT g2 pass=${PASS} fail=${FAIL}"
  ;;

g3) # ── L3-3 外部送样写保护 ─────────────────────────────────────────────
  echo "== G3 外部送样 =="
  reseed
  ${API} --as extA GET /lqg/sys/ping >/dev/null 2>&1 || true
  BE="$(val "SELECT count(*) FROM t_lqg_embed WHERE submit_source='external' AND del_flag='0'")"
  BA="$(val "SELECT count(*) FROM t_lqg_embed")"; BM="$(val "SELECT count(*) FROM t_lqg_embed_marker")"
  echo "  baseline ext=${BE} all=${BA} marker=${BM}"
  [ "${BE:-0}" -ge 1 ] && ok "基线非空（external 行=${BE}）" || bad "基线 external=0，断言会恒绿"
  rej "3a extB 替同组 extA 的 1001 送样被拒" extB POST /mp/ext/embed 400 "只能挂本人送检过的样本" '{"sampleId":9000001001,"sampleType":"组织"}'
  eqv "3a-库 外部送样数不变" "SELECT count(*) FROM t_lqg_embed WHERE submit_source='external' AND del_flag='0'" "${BE}"
  eqv "3b-前置 1003 是 invalid" "SELECT verify_status FROM t_lqg_sample WHERE id=9000001003" "invalid"
  rej "3b extA 挂自己无效样本 1003 被拒" extA POST /mp/ext/embed 400 "已判无效" '{"sampleId":9000001003,"sampleType":"组织"}'
  eqv "3b-库 总数不变" "SELECT count(*) FROM t_lqg_embed" "${BA}"
  # 3c 夹带：接口成功但夹带字段全不生效
  ok200 "3c extA 正常送样（夹带 6 字段）仍 200" extA POST /mp/ext/embed '{"sampleId":9000001001,"sampleType":"类器官","organoidSourceType":"肝类器官","paraffinBlockNo":"T-hack9","verifyStatus":"valid","embedBy":"外部","stainTypes":["HE"],"operatorName":"外部","submitSource":"internal"}'
  eqv "3c-库 夹带字段全不生效" "SELECT e.submit_source||'|'||e.verify_status||'|'||COALESCE(e.paraffin_block_no,'-')||'|'||COALESCE(e.embed_by,'-')||'|'||COALESCE(e.stain_types,'-')||'|'||COALESCE(e.operator_name,'-') FROM t_lqg_embed e WHERE e.sample_id=9000001001 AND e.submit_source='external' AND e.paraffin_block_no IS NULL" "external|pending|-|-|-|-"
  eqv "3c-库 submitter 仍是王医生" "SELECT p.real_name FROM t_lqg_embed e JOIN t_lqg_ext_profile p ON p.user_id=e.submitter_id WHERE e.sample_id=9000001001 AND e.submit_source='external' AND e.paraffin_block_no IS NULL" "王医生"
  eqv "3c-库 只多 1 行（无额外副作用）" "SELECT count(*) FROM t_lqg_embed" "$((BA+1))"
  eqv "3c-库 marker 未被夹带写入" "SELECT count(*) FROM t_lqg_embed_marker" "${BM}"
  eqv "3c-库 无 T-hack9" "SELECT count(*) FROM t_lqg_embed WHERE paraffin_block_no='T-hack9'" "0"
  # 3d 所挂样本未核验时判有效被拒（2006 挂 pending 的 1002）
  eqv "3d-前置 2006 pending|所挂1002 pending" "SELECT e.verify_status||'|'||s.verify_status FROM t_lqg_embed e JOIN t_lqg_sample s ON s.id=e.sample_id WHERE e.id=9000002006" "pending|pending"
  rej "3d 所挂样本未核验时判有效被拒" staff PUT /lqg/embed/9000002006/verify 500 "所挂样本还未核验有效" '{"action":"valid","paraffinBlockNo":"T-E06-1"}'
  eqv "3d-库 2006 仍 pending|-" "SELECT verify_status||'|'||COALESCE(paraffin_block_no,'-') FROM t_lqg_embed WHERE id=9000002006" "pending|-"
  # 3e 普通保存改不动待核验送样
  rej "3e 普通保存改待核验送样被拒" staff PUT /lqg/embed 400 "不能通过普通保存修改" '{"id":9000002006,"sampleId":9000001002,"paraffinBlockNo":"T-E06-9","sampleType":"被普通保存改掉"}'
  eqv "3e-库 2006 行不变" "SELECT verify_status||'|'||COALESCE(paraffin_block_no,'-')||'|'||COALESCE(verify_by::text,'-')||'|'||sample_type FROM t_lqg_embed WHERE id=9000002006" "pending|-|-|组织"
  # 3f 外部改不了实验室录的石蜡块 / 同组别人的
  rej "3f extA 改实验室录的 2001 被拒" extA PUT /mp/ext/embed/9000002001 400 "只能修改重提本人提交的送样" '{"sampleId":9000001001,"sampleType":"改实验室的块"}'
  rej "3g extB 改同组 extA 的 2006 被拒" extB PUT /mp/ext/embed/9000002006 400 "只能修改重提本人提交的送样" '{"sampleId":9000001002,"sampleType":"被同组人改"}'
  eqv "3f-库 无被改行" "SELECT count(*) FROM t_lqg_embed WHERE sample_type IN ('被同组人改','改实验室的块')" "0"
  eqv "3f-库 2001 行不变" "SELECT verify_status||'|'||paraffin_block_no||'|'||sample_type FROM t_lqg_embed WHERE id=9000002001" "valid|T-E01-1|组织"
  echo "RESULT g3 pass=${PASS} fail=${FAIL}"
  ;;

g4) # ── L3-4 外部详情 embeds 键集合（按 CR-20260918-07 精确豁免）─────────
  echo "== G4 外部详情 embeds 键集合 =="
  reseed
  ALLOW='["id","sampleId","submitNo","paraffinBlockNo","sampleType","organoidSourceType","tissueReceiveTime","tissueProcessTime","agaroseEmbedTime","dehydrateTime","agaroseSendTime","paraffinEmbedTime","sectionTime","sectioned","stainTypes","stainOther","markers","verifyStatus","invalidReason","submitterName","mine","editable","embedBy","operatorName"]'
  OUT="$(${API} --as extB GET /mp/ext/sample/9000001001)"
  ja "4a embeds 形状（2 块 T-E01-1/T-E01-2，已切片、HE+IHC）" "$OUT" '([.data.embeds[].paraffinBlockNo]==["T-E01-1","T-E01-2"]) and .data.embeds[0].sectioned==true and .data.embeds[0].stainTypes==["HE","IHC"]'
  ja "4b embedBy=李工 / operatorName=李工 对外可见（CR 精确豁免）" "$OUT" '.data.embeds[0].embedBy=="李工" and .data.embeds[0].operatorName=="李工" and (.data.embeds[1].embedBy//null)==null'
  ja "4c embeds 键集合 ⊆ CR 白名单（24 键，无越界）" "$OUT" --argjson allow "$ALLOW" '([.data.embeds[]|keys[]]|unique)-$allow==[]'
  ja "4d embedBy 与 operatorName 确在键集合内（不是被漏掉而恒绿）" "$OUT" '([.data.embeds[]|keys[]]|unique) as $k | ($k|index("embedBy"))!=null and ($k|index("operatorName"))!=null'
  ja "4e 默认开关关：详情与 embeds 均无 internalNo" "$OUT" '(([.data] + [.data.embeds[]])|map(has("internalNo"))|any)==false'
  ja "4f embeds 无 verifyBy/verifiedBy/remark/冻存/创建更新人/手机号" "$OUT" '([.data.embeds[]|keys[]]|unique|map(select(test("^(verifyBy|verifiedBy|remark|frozenBy|frozen|createBy|updateBy|createDept|phone|phonenumber|openid)$")))|length)==0'
  ja "4g 样本详情级无 operatorName/embedBy/verifyBy/冻存人" "$OUT" '([.data|keys[]]|unique|map(select(test("^(operatorName|embedBy|verifyBy|verifiedBy|frozenBy|frozen)$")))|length)==0'
  echo "  实到 embeds 键: $(printf '%s' "$OUT" | jq -c '[.data.embeds[]|keys[]]|unique')"
  rej "4h extC 取 1001 详情 404" extC GET /mp/ext/sample/9000001001 404 "样本不存在"
  rej "4i extE（同组未核验）取 1001 详情 404" extE GET /mp/ext/sample/9000001001 404 "样本不存在"
  # 开关打开后 internalNo 才出现，测完还原
  eqv "4j-前置 开关默认 false" "SELECT config_value FROM sys_config WHERE config_key='lqg.ext.show-internal-no'" "false"
  CID="$(${API} --as admin GET '/system/config/list?configKey=lqg.ext.show-internal-no' | jq -r '.rows[0].configId')"
  ok200 "4k admin 把开关置 true" admin PUT /system/config "{\"configId\":${CID},\"configName\":\"外部页面显示内部编号\",\"configKey\":\"lqg.ext.show-internal-no\",\"configValue\":\"true\",\"configType\":\"N\"}"
  O2="$(${API} --as extB GET /mp/ext/sample/9000001001)"
  ja "4l 开关开：详情 internalNo=T-hli01" "$O2" '.data.internalNo=="T-hli01"'
  ${API} --as admin PUT /system/config "{\"configId\":${CID},\"configName\":\"外部页面显示内部编号\",\"configKey\":\"lqg.ext.show-internal-no\",\"configValue\":\"false\",\"configType\":\"N\"}" >/dev/null 2>&1
  eqv "4m-库 开关已还原 false" "SELECT config_value FROM sys_config WHERE config_key='lqg.ext.show-internal-no'" "false"
  echo "RESULT g4 pass=${PASS} fail=${FAIL}"
  ;;

g5) # ── L3-5 导出边界 ──────────────────────────────────────────────────
  echo "== G5 导出边界 =="
  reseed
  rm -f /tmp/l3r2-all.xlsx /tmp/l3r2-f.xlsx /tmp/l3r2-e.xlsx
  NALL="$(${API} --as staff GET '/lqg/sample/list?sampleKind=tissue&pageSize=200' | jq -r '.total')"
  ${API} --as staff --out /tmp/l3r2-all.xlsx POST /lqg/sample/export/tissue >/dev/null 2>&1; ALLX=$?
  [ "$ALLX" = "0" ] && ok "5a-前置 全量导出接口未报错" || bad "5a 全量导出 exit=${ALLX}"
  if python3 doc/verify/xlsx_header.py --file /tmp/l3r2-all.xlsx --template "$TMPL" --rows "$NALL" >/tmp/l3r2.out 2>&1; then
    ok "5a-前置 全量导出表头逐字一致且行数=${NALL}（=同条件列表 total）"; else bad "5a 全量导出对不上模板/行数（列表 total=${NALL}）"; sed 's/^/      | /' /tmp/l3r2.out|tail -3; fi
  [ "${NALL:-0}" -gt 1 ] && ok "5a-前置 全量行数>1（下面「只出筛选结果」才有意义）" || bad "5a 全量行数=${NALL} 不足以证明筛选收窄"
  rej "5b 外部角色(extA)调导出 403" extA POST /lqg/sample/export/tissue 403 "没有访问权限"
  # 带筛选：B 大学 9000009002 → 只有 extD 的 1006
  TOT="$(${API} --as staff GET '/lqg/sample/list?sourceUnitId=9000009002&sampleKind=tissue&pageSize=200' | jq -r '.total')"
  IDS="$(${API} --as staff GET '/lqg/sample/list?sourceUnitId=9000009002&sampleKind=tissue&pageSize=200' | jq -rc '[.rows[].id]')"
  echo "  同条件列表 total=${TOT} ids=${IDS}"
  ${API} --as staff --out /tmp/l3r2-f.xlsx POST '/lqg/sample/export/tissue?sourceUnitId=9000009002' >/dev/null 2>&1; FX=$?
  [ "$FX" = "0" ] && ok "5c 带筛选导出接口未报错" || bad "5c 带筛选导出 exit=${FX}"
  if python3 doc/verify/xlsx_header.py --file /tmp/l3r2-f.xlsx --template "$TMPL" --rows "$TOT" >/tmp/l3r2.out 2>&1; then
    ok "5c-库/件 带筛选导出只出筛选结果（行数=${TOT}=列表 total）"; else bad "5c 带筛选导出行数≠列表 total=${TOT}"; sed 's/^/      | /' /tmp/l3r2.out|tail -3; fi
  python3 doc/verify/xlsx_header.py --file /tmp/l3r2-f.xlsx --template "$TMPL" --find "内部编号=T-hco04" >/dev/null 2>&1 && ok "5c-件 筛选结果含筛选内的 1006/T-hco04" || bad "5c 筛选结果缺 1006/T-hco04"
  if python3 doc/verify/xlsx_header.py --file /tmp/l3r2-f.xlsx --template "$TMPL" --find "内部编号=T-hli01" >/dev/null 2>&1; then
    bad "5c-件 筛选结果混入筛选外的 T-hli01"; else ok "5c-件 筛选结果未混入筛选外的 T-hli01"; fi
  if python3 doc/verify/xlsx_header.py --file /tmp/l3r2-f.xlsx --template "$TMPL" --rows 0 >/dev/null 2>&1; then
    bad "5c-件 带筛选导出竟然 0 行（与列表 total=${TOT} 矛盾）"; else ok "5c-件 带筛选导出非空（≠0 行的空态）"; fi
  # 空结果
  ${API} --as staff --out /tmp/l3r2-e.xlsx POST '/lqg/sample/export/tissue?sourceUnitId=9999999999' >/dev/null 2>&1; EX=$?
  [ "$EX" = "0" ] && ok "5d 空结果导出接口未报错（exit=0）" || bad "5d 空结果导出 exit=${EX}"
  if python3 doc/verify/xlsx_header.py --file /tmp/l3r2-e.xlsx --template "$TMPL" --rows 0 >/tmp/l3r2.out 2>&1; then
    ok "5d-件 空结果导出只有表头、0 数据行"; else bad "5d 空结果导出不符合「只有表头」"; sed 's/^/      | /' /tmp/l3r2.out|tail -3; fi
  [ -s /tmp/l3r2-e.xlsx ] && ok "5d-件 空结果仍产出非空 xlsx 文件（$(stat -f%z /tmp/l3r2-e.xlsx 2>/dev/null || stat -c%s /tmp/l3r2-e.xlsx) bytes）" || bad "5d 空结果文件缺失/0 字节"
  echo "RESULT g5 pass=${PASS} fail=${FAIL}"
  ;;

g6) # ── #145 后端侧独立复验：verify 端点 vs 普通保存 ─────────────────────
  echo "== G6 #145 后端侧（verify 可判 / 普通保存仍拒，两条状态各带库内对照）=="
  reseed
  ${API} --as extA GET /lqg/sys/ping >/dev/null 2>&1 || true
  # E1: pending 外部送样（挂已核验有效的 1001）
  ok200 "6a-前置 extA 建一条 pending 送样（挂 1001）" extA POST /mp/ext/embed '{"sampleId":9000001001,"sampleType":"组织"}'
  E1="$(val "SELECT id FROM t_lqg_embed WHERE sample_id=9000001001 AND submit_source='external' AND del_flag='0' ORDER BY id DESC LIMIT 1")"
  echo "  E1=${E1}"
  eqv "6a-前置 E1 状态 pending|-" "SELECT verify_status||'|'||COALESCE(paraffin_block_no,'-') FROM t_lqg_embed WHERE id=${E1}" "pending|-"
  # pending 时普通保存仍拒
  rej "6b PUT /lqg/embed 对 pending 仍拒" staff PUT /lqg/embed 400 "不能通过普通保存修改" "{\"id\":${E1},\"sampleId\":9000001001,\"sampleType\":\"pending-被普通保存改\"}"
  eqv "6b-库 pending 行原样（status|block|sampleType）" "SELECT verify_status||'|'||COALESCE(paraffin_block_no,'-')||'|'||sample_type FROM t_lqg_embed WHERE id=${E1}" "pending|-|组织"
  # verify: pending → valid（带编号）
  ok200 "6c PUT /lqg/embed/{id}/verify pending→valid（带编号）" staff PUT "/lqg/embed/${E1}/verify" '{"action":"valid","paraffinBlockNo":"T-r2l3-A"}'
  eqv "6c-库 valid|T-r2l3-A|核验人有值" "SELECT verify_status||'|'||paraffin_block_no||'|'||CASE WHEN verify_by IS NULL THEN '-' ELSE 'set' END FROM t_lqg_embed WHERE id=${E1}" "valid|T-r2l3-A|set"
  # E2: 再建一条，判无效（带原因）
  ok200 "6d-前置 extA 再建一条 pending 送样" extA POST /mp/ext/embed '{"sampleId":9000001001,"sampleType":"组织"}'
  E2="$(val "SELECT id FROM t_lqg_embed WHERE sample_id=9000001001 AND submit_source='external' AND del_flag='0' AND verify_status='pending' ORDER BY id DESC LIMIT 1")"
  echo "  E2=${E2}"
  ok200 "6e PUT /lqg/embed/{id}/verify pending→invalid（带原因）" staff PUT "/lqg/embed/${E2}/verify" '{"action":"invalid","reason":"测-r2l3-无效原因"}'
  eqv "6e-库 invalid|原因" "SELECT verify_status||'|'||COALESCE(invalid_reason,'-') FROM t_lqg_embed WHERE id=${E2}" "invalid|测-r2l3-无效原因"
  # invalid 时普通保存仍拒
  rej "6f PUT /lqg/embed 对 invalid 仍拒" staff PUT /lqg/embed 400 "不能通过普通保存修改" "{\"id\":${E2},\"sampleId\":9000001001,\"sampleType\":\"invalid-被普通保存改\"}"
  eqv "6f-库 invalid 行原样（status|reason|sampleType）" "SELECT verify_status||'|'||COALESCE(invalid_reason,'-')||'|'||sample_type FROM t_lqg_embed WHERE id=${E2}" "invalid|测-r2l3-无效原因|组织"
  # 改判：invalid → valid（带编号，清旧原因）
  ok200 "6g PUT /lqg/embed/{id}/verify invalid→valid（走核验端点）" staff PUT "/lqg/embed/${E2}/verify" '{"action":"valid","paraffinBlockNo":"T-r2l3-B"}'
  eqv "6g-库 valid|T-r2l3-B|旧原因已清" "SELECT verify_status||'|'||paraffin_block_no||'|'||COALESCE(invalid_reason,'-') FROM t_lqg_embed WHERE id=${E2}" "valid|T-r2l3-B|-"
  # 缺编号 / 缺原因各自被拒且库不变（verify 端点的负向）
  ok200 "6h-前置 extA 再建一条 pending 送样(E3)" extA POST /mp/ext/embed '{"sampleId":9000001001,"sampleType":"组织"}'
  E3="$(val "SELECT id FROM t_lqg_embed WHERE sample_id=9000001001 AND submit_source='external' AND del_flag='0' AND verify_status='pending' ORDER BY id DESC LIMIT 1")"
  rej "6i verify 判有效缺编号被拒" staff PUT "/lqg/embed/${E3}/verify" 500 "必须填石蜡块编号" '{"action":"valid"}'
  rej "6j verify 判无效缺原因被拒" staff PUT "/lqg/embed/${E3}/verify" 500 "必须写原因" '{"action":"invalid"}'
  eqv "6i/j-库 E3 仍 pending|-|无原因" "SELECT verify_status||'|'||COALESCE(paraffin_block_no,'-')||'|'||COALESCE(invalid_reason,'-') FROM t_lqg_embed WHERE id=${E3}" "pending|-|-"
  echo "RESULT g6 pass=${PASS} fail=${FAIL}"
  ;;

*) echo "用法: $0 <g1|g2|g3|g4|g5|g6>"; exit 2 ;;
esac
