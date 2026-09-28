#!/usr/bin/env bash
# D3 r1 L3 独立邪路探针（自起，不引用 D2/D3 regression 里已有脚本的结论）
# 用法: bash doc/waves/regression/D3/L3-r1-probes.sh <g1|g2|g3|g4|g5>
set -uo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../../.." && pwd)"
cd "${ROOT}"
API="bash doc/verify/api.sh"
DB="python3 doc/verify/db.py"
PASS=0; FAIL=0
ok(){ echo "  [PASS] $1"; PASS=$((PASS+1)); }
bad(){ echo "  [FAIL] $1"; FAIL=$((FAIL+1)); }
# 断言：命令退出 0 才算过
chk(){ local desc="$1"; shift; if "$@" >/tmp/l3.out 2>&1; then ok "$desc"; else bad "$desc"; sed 's/^/      | /' /tmp/l3.out | head -4; fi; }
# 业务码属 400/403/404/500（“被拒”）才算过；同时打印实际码
rej(){ local desc="$1" as="$2" method="$3" path="$4" body="${5:-}"
  local out; out=$(${API} --as "$as" --bizcode "$method" "$path" ${body:+"$body"} 2>&1)
  local code; code="$(printf '%s' "$out" | head -1 | cut -f1)"
  case "$code" in 400|403|404|500) ok "${desc} -> code=${code}"; printf '      msg=%s\n' "$(printf '%s' "$out" | head -1 | cut -f2-)" ;;
    *) bad "${desc} -> 拿到 code=${code}（期望被拒）"; printf '      resp=%s\n' "$out" | head -3 ;; esac; }
eq(){ local desc="$1" sql="$2" want="$3"; if ${DB} --sql "$sql" --eq "$want" >/tmp/l3.out 2>&1; then ok "${desc} [=${want}]"; else bad "${desc} 库里不是 [${want}]"; sed 's/^/      | /' /tmp/l3.out|head -4; fi; }
val(){ ${DB} --quiet --sql "$1" 2>/dev/null | head -1; }

case "${1:-}" in
g1) # ── 染色与挂靠规则：4 条被拒 + 库内不变 ──────────────────────────
  echo "== G1 染色与挂靠规则 =="
  bash doc/verify/reseed.sh --yes >/dev/null || { echo "reseed 失败"; exit 2; }
  ${API} --as staff GET /lqg/sys/ping >/dev/null
  BASE="$(val "SELECT count(*) FROM t_lqg_embed")"; echo "  baseline t_lqg_embed=$BASE"
  rej "无染色+HE 同时提交被拒" staff POST /lqg/embed '{"sampleId":9000001005,"paraffinBlockNo":"T-X1","stainTypes":["NONE","HE"]}'
  eq  "  拒后未多行" "SELECT count(*) FROM t_lqg_embed" "$BASE"
  rej "选其他不写名称被拒" staff POST /lqg/embed '{"sampleId":9000001005,"paraffinBlockNo":"T-X2","stainTypes":["OTHER"]}'
  eq  "  拒后未多行" "SELECT count(*) FROM t_lqg_embed" "$BASE"
  rej "字典外染色值(PAS)被拒" staff POST /lqg/embed '{"sampleId":9000001005,"paraffinBlockNo":"T-X3","stainTypes":["PAS"]}'
  eq  "  拒后未多行" "SELECT count(*) FROM t_lqg_embed" "$BASE"
  rej "挂到待核验样本(1002)被拒" staff POST /lqg/embed '{"sampleId":9000001002,"paraffinBlockNo":"T-X4"}'
  eq  "  拒后未多行" "SELECT count(*) FROM t_lqg_embed" "$BASE"
  echo "  最终 T-X% 行数=$(val "SELECT count(*) FROM t_lqg_embed WHERE paraffin_block_no LIKE 'T-X%'")"
  echo "RESULT g1 pass=${PASS} fail=${FAIL}"
  ;;
g2) # ── 有已核验石蜡块的样本改判无效被拒 ────────────────────────────
  echo "== G2 valid->invalid 闸 =="
  bash doc/verify/reseed.sh --yes >/dev/null || { echo "reseed 失败"; exit 2; }
  ${API} --as staff GET /lqg/sys/ping >/dev/null
  echo "  1004 现有已核验石蜡块数=$(val "SELECT count(*) FROM t_lqg_embed WHERE sample_id=9000001004 AND verify_status='valid' AND del_flag='0'")"
  rej "有已核验石蜡块的样本 1004 改判无效被拒" staff PUT /lqg/sample/9000001004/verify '{"action":"invalid","reason":"想改判"}'
  eq  "  拒后样本仍 valid" "SELECT verify_status FROM t_lqg_sample WHERE id=9000001004" "valid"
  eq  "  拒后无效原因仍为空" "SELECT count(*) FROM t_lqg_sample WHERE id=9000001004 AND invalid_reason IS NULL" "1"
  echo "RESULT g2 pass=${PASS} fail=${FAIL}"
  ;;
g3) # ── 外部送样写保护 ──────────────────────────────────────────────
  echo "== G3 外部送样 =="
  bash doc/verify/reseed.sh --yes >/dev/null || { echo "reseed 失败"; exit 2; }
  ${API} --as extA GET /mp/ext/sample/9000001001 >/dev/null
  BASE_EXT="$(val "SELECT count(*) FROM t_lqg_embed WHERE submit_source='external' AND del_flag='0'")"
  BASE_ALL="$(val "SELECT count(*) FROM t_lqg_embed")"; echo "  baseline ext=$BASE_EXT all=$BASE_ALL"
  rej "extB 替同组 extA 的 1001 送样被拒" extB POST /mp/ext/embed '{"sampleId":9000001001,"sampleType":"组织"}'
  eq  "  拒后外部送样数不变" "SELECT count(*) FROM t_lqg_embed WHERE submit_source='external' AND del_flag='0'" "$BASE_EXT"
  rej "extA 挂到自己无效样本 1003 被拒" extA POST /mp/ext/embed '{"sampleId":9000001003,"sampleType":"组织"}'
  eq  "  拒后总数不变" "SELECT count(*) FROM t_lqg_embed" "$BASE_ALL"
  # 夹带石蜡块编号与染色：接口 200 但字段不生效
  echo "  -- 夹带 paraffinBlockNo/stainTypes/verifyStatus/operatorName/embedBy/submitSource --"
  ${API} --as extA --bizcode POST /mp/ext/embed '{"sampleId":9000001001,"sampleType":"类器官","organoidSourceType":"肝类器官","paraffinBlockNo":"T-hack9","verifyStatus":"valid","embedBy":"外部","stainTypes":["HE"],"operatorName":"外部","submitSource":"internal"}' | head -1
  eq  "  夹带字段全部未生效(落 external|pending|-|-|-|-)" \
      "SELECT e.submit_source || '|' || e.verify_status || '|' || COALESCE(e.paraffin_block_no,'-') || '|' || COALESCE(e.embed_by,'-') || '|' || COALESCE(e.stain_types,'-') || '|' || COALESCE(e.operator_name,'-') FROM t_lqg_embed e WHERE e.sample_id=9000001001 AND e.submit_source='external'" \
      "external|pending|-|-|-|-"
  eq  "  submitter 仍是王医生" "SELECT p.real_name FROM t_lqg_embed e JOIN t_lqg_ext_profile p ON p.user_id=e.submitter_id WHERE e.sample_id=9000001001 AND e.submit_source='external'" "王医生"
  NEWID="$(val "SELECT id FROM t_lqg_embed WHERE sample_id=9000001001 AND submit_source='external' AND del_flag='0'")"
  echo "  新建送样 id=$NEWID"
  # 所挂样本未核验时判有效被拒 —— seed 的 2006 挂在待核验 1002
  rej "所挂样本未核验(1002)时判有效被拒" staff PUT /lqg/embed/9000002006/verify '{"action":"valid","paraffinBlockNo":"T-E06-1"}'
  eq  "  拒后 2006 仍 pending|-" "SELECT verify_status || '|' || COALESCE(paraffin_block_no,'-') FROM t_lqg_embed WHERE id=9000002006" "pending|-"
  # 普通保存改不动待核验送样
  rej "普通保存改待核验送样被拒" staff PUT /lqg/embed '{"id":9000002006,"sampleId":9000001002,"paraffinBlockNo":"T-E06-9","sampleType":"被普通保存改掉"}'
  eq  "  拒后 2006 行不变" "SELECT verify_status || '|' || COALESCE(paraffin_block_no,'-') || '|' || COALESCE(verify_by::text,'-') || '|' || sample_type FROM t_lqg_embed WHERE id=9000002006" "pending|-|-|组织"
  # 外部改不了实验室录的石蜡块
  rej "extA 改实验室录的 2001 被拒" extA PUT /mp/ext/embed/9000002001 '{"sampleId":9000001001,"sampleType":"改实验室的块"}'
  rej "extB 改 extA 的 2006 被拒" extB PUT /mp/ext/embed/9000002006 '{"sampleId":9000001002,"sampleType":"被同组人改"}'
  eq  "  拒后无被改行" "SELECT count(*) FROM t_lqg_embed WHERE sample_type IN ('被同组人改','改实验室的块')" "0"
  echo "  最终 all=$(val "SELECT count(*) FROM t_lqg_embed") ext=$(val "SELECT count(*) FROM t_lqg_embed WHERE submit_source='external' AND del_flag='0'")"
  echo "RESULT g3 pass=${PASS} fail=${FAIL}"
  ;;
g4) # ── 外部详情 embeds 键集合（CR-20260918-07 精确豁免）────────────
  echo "== G4 外部详情 embeds 键集合 =="
  bash doc/verify/reseed.sh --yes >/dev/null || { echo "reseed 失败"; exit 2; }
  EXTKEYS='["id","sampleId","submitNo","paraffinBlockNo","sampleType","organoidSourceType","tissueReceiveTime","tissueProcessTime","agaroseEmbedTime","dehydrateTime","agaroseSendTime","paraffinEmbedTime","sectionTime","sectioned","stainTypes","stainOther","markers","verifyStatus","invalidReason","submitterName","mine","editable","embedBy","operatorName"]'
  echo "  -- extB 取 1001 详情 --"
  OUT="$(${API} --as extB GET /mp/ext/sample/9000001001)"
  printf '%s' "$OUT" | jq -e '([.data.embeds[].paraffinBlockNo])==["T-E01-1","T-E01-2"] and .data.embeds[0].sectioned==true and .data.embeds[0].stainTypes==["HE","IHC"] and .data.embeds[0].embedBy=="李工" and .data.embeds[0].operatorName=="李工" and (.data.embeds[1].embedBy // null)==null' >/dev/null 2>&1 && ok "embeds 形状 + embedBy/operatorName 均为「李工」对外可见" || bad "embeds 形状或 embedBy/operatorName 不符"
  DIFF="$(printf '%s' "$OUT" | jq -c --argjson allow "$EXTKEYS" '[.data.embeds[]|keys[]]|unique-($allow)')"
  [ "$DIFF" = "[]" ] && ok "键集合 ⊂ 白名单（无 internalNo/verifyBy/remark/冻存键）" || bad "出现白名单外的键: $DIFF"
  printf '%s' "$OUT" | jq -e '(.data|has("internalNo"))==false' >/dev/null 2>&1 && ok "开关默认关：详情无 internalNo 键" || bad "详情出现了 internalNo 键"
  printf '%s' "$OUT" | jq -er '[.data.embeds[]|keys[]]|unique[]' 2>/dev/null | grep -qE '^(verifyBy|verifiedBy|remark|frozenBy|frozen|createBy|updateBy|phone|phonenumber)$' && bad "embeds 出现禁键" || ok "embeds 无 verifyBy/remark/冻存/创建人 键"
  echo "  实到键: $(printf '%s' "$OUT" | jq -c '[.data.embeds[]|keys[]]|unique')"
  echo "  -- extC 取 1001 详情应 404 --"
  rej "extC 取 1001 详情 404" extC GET /mp/ext/sample/9000001001
  echo "  -- 开关打开后 internalNo 才出现 --"
  CID="$(${API} --as admin GET '/system/config/list?configKey=lqg.ext.show-internal-no' | jq -r '.rows[0].configId')"
  ${API} --as admin PUT /system/config "{\"configId\":${CID},\"configName\":\"外部页面显示内部编号\",\"configKey\":\"lqg.ext.show-internal-no\",\"configValue\":\"true\",\"configType\":\"N\"}" | jq -e '.code==200' >/dev/null && ok "开关置 true" || bad "开关置 true 失败"
  ${API} --as extB GET /mp/ext/sample/9000001001 | jq -e '.data.internalNo=="T-hli01"' >/dev/null 2>&1 && ok "开关打开后 1001 详情 internalNo=T-hli01" || bad "开关打开后详情无 internalNo"
  ${API} --as admin PUT /system/config "{\"configId\":${CID},\"configName\":\"外部页面显示内部编号\",\"configKey\":\"lqg.ext.show-internal-no\",\"configValue\":\"false\",\"configType\":\"N\"}" >/dev/null
  eq  "  开关已还原 false" "SELECT config_value FROM sys_config WHERE config_key='lqg.ext.show-internal-no'" "false"
  echo "RESULT g4 pass=${PASS} fail=${FAIL}"
  ;;
g5) # ── 导出边界 ────────────────────────────────────────────────────
  echo "== G5 导出边界 =="
  bash doc/verify/reseed.sh --yes >/dev/null || { echo "reseed 失败"; exit 2; }
  ${API} --as staff GET /lqg/sys/ping >/dev/null
  rm -f /tmp/l3-all.xlsx /tmp/l3-f.xlsx /tmp/l3-empty.xlsx /tmp/l3-empty2.xlsx
  # 基线：全量
  ${API} --as staff --out /tmp/l3-all.xlsx POST /lqg/sample/export/tissue >/dev/null 2>&1
  echo "  全量导出: $(python3 doc/verify/xlsx_header.py --file /tmp/l3-all.xlsx --template '_input/templates/样本记录信息表模板.xlsx' 2>&1 | tail -1)"
  # 外部 403
  rej "外部角色(extA)调导出 403" extA POST /lqg/sample/export/tissue
  # 带筛选：sourceUnitId=9000009002（B 大学）应只出 extD 的样本
  ${API} --as staff --out /tmp/l3-f.xlsx POST '/lqg/sample/export/tissue?sourceUnitId=9000009002' >/dev/null 2>&1
  TOT="$(${API} --as staff GET '/lqg/sample/list?sourceUnitId=9000009002&sampleKind=tissue&pageSize=100' | jq '.total')"
  echo "  同条件列表 total=$TOT"
  python3 doc/verify/xlsx_header.py --file /tmp/l3-f.xlsx --template '_input/templates/样本记录信息表模板.xlsx' --rows "$TOT" >/dev/null 2>&1 && ok "带筛选导出只出筛选结果（行数=${TOT}）" || bad "带筛选导出行数 ≠ 列表 total=$TOT"
  python3 doc/verify/xlsx_header.py --file /tmp/l3-f.xlsx --template '_input/templates/样本记录信息表模板.xlsx' --find "内部编号=T-hco04" >/dev/null 2>&1 && ok "筛选结果含 1006/T-hco04" || bad "筛选结果缺 1006"
  python3 doc/verify/xlsx_header.py --file /tmp/l3-f.xlsx --template '_input/templates/样本记录信息表模板.xlsx' --find "内部编号=T-hli01" >/dev/null 2>&1 && bad "筛选结果混入 T-hli01（不该有）" || ok "筛选结果未混入 T-hli01"
  # 空结果：不存在的 sourceUnitId
  ${API} --as staff --out /tmp/l3-empty.xlsx POST '/lqg/sample/export/tissue?sourceUnitId=9999999999' >/dev/null 2>&1
  ECODE=$?
  [ "$ECODE" = 0 ] && ok "空结果导出接口未报错" || bad "空结果导出接口退出码=$ECODE"
  python3 doc/verify/xlsx_header.py --file /tmp/l3-empty.xlsx --template '_input/templates/样本记录信息表模板.xlsx' --rows 0 >/dev/null 2>&1 && ok "空结果导出只有表头、0 数据行" || bad "空结果导出不符合「只有表头」"
  echo "RESULT g5 pass=${PASS} fail=${FAIL}"
  ;;
*) echo "用法: $0 <g1|g2|g3|g4|g5>"; exit 2 ;;
esac
