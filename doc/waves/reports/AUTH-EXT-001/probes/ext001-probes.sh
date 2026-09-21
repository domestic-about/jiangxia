#!/usr/bin/env bash
# AUTH-EXT-001 · 对抗性探针（accept 三条没直接断的形态，逐条带库内断言）。
#
# 跑法（cwd 任意）：bash doc/waves/reports/AUTH-EXT-001/probes/ext001-probes.sh
#
# 探针清单（每条 = 「形态 → 期望拒绝/生效 → 库内真实值」）：
#   P1  列表排序 = COALESCE(update_time, create_time) 倒序（改过的排前面）
#   P2  lqg.ext.show-internal-no 关着时详情**没有** internalNo 键；打开后才出现（运行时可切）
#   P3  POST 组织样本：夹带 internalNo/verifyStatus/submitSource/receiveDate/operatorName/
#       hasViabilityReport/isFixed/hasQcSheet 一律不落库（外部 BO 里根本没有这些键）
#   P4  POST 类器官：夹带内部字段 + 组织样本字段一律不落库；收样段全空
#   P5  PUT 无效样本 → 回 pending 且清 invalid_reason/verify_by（夹带的内部字段仍不生效）
#   P6  组织样本 PUT 不许改类器官样本（400，且库里一字不变）
#   P7  外单位（extD）改 extC 的样本 → 404「样本不存在」（不是 403：不泄露存在性），库里不变
#   P8  同组但对方未核验（extB 改 extE 的 1007）→ 404，库里不变
#   P9  自己的 valid 样本也改不了（400），库里不变
#   P10 软删样本（1010）详情 404 且不在列表里
#   P11 内部账号（admin/staff）打外部写端点 → 403（角色闸两个方向）
#   P12 外部账号打内部 /lqg/** → 403
set -uo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/../../../../.."   # → 项目根
set -a; . doc/verify/verify.env; set +a
export PGPASSWORD="${LQG_DB_PASSWORD}"
PSQL=(psql -h "${LQG_DB_HOST}" -p "${LQG_DB_PORT}" -U "${LQG_DB_USER}" -d "${LQG_DB_NAME}" -At -q)

sql() { "${PSQL[@]}" -c "$1"; }
api() { bash doc/verify/api.sh "$@"; }
PASS=0; FAIL=0
chk() { # chk <name> <expected> <actual>
  if [ "$2" = "$3" ]; then PASS=$((PASS+1)); printf 'PASS %s — %s\n' "$1" "$3"
  else FAIL=$((FAIL+1)); printf 'FAIL %s — 期望 [%s] 实得 [%s]\n' "$1" "$2" "$3"; fi
}

rm -f "${TMPDIR:-/tmp}"/lqg-verify-token-*
bash doc/verify/reseed.sh --yes >/dev/null

# ── P1 排序 ────────────────────────────────────────────────────────────────
chk P1-列表排序-COALESCE倒序 '[9000001002,9000001003,9000001004,9000001001]' \
  "$(api --as extA GET '/mp/ext/sample/list?pageSize=100' | jq -c '[.rows[].id]')"

# ── P2 内部编号开关（默认 false）──────────────────────────────────────────
chk P2a-开关关-详情无internalNo键 'false' \
  "$(api --as extA GET /mp/ext/sample/9000001001 | jq -c '.data|has("internalNo")')"
sql "UPDATE sys_config SET config_value='true' WHERE config_key='lqg.ext.show-internal-no'" >/dev/null
api --as admin DELETE /system/config/refreshCache >/dev/null
chk P2b-开关开-详情有internalNo键 'true|T-hli01' \
  "$(api --as extA GET /mp/ext/sample/9000001001 | jq -r '[(.data|has("internalNo")), .data.internalNo]|join("|")')"
sql "UPDATE sys_config SET config_value='false' WHERE config_key='lqg.ext.show-internal-no'" >/dev/null
api --as admin DELETE /system/config/refreshCache >/dev/null
chk P2c-开关还原-详情又无该键 'false' \
  "$(api --as extA GET /mp/ext/sample/9000001001 | jq -c '.data|has("internalNo")')"

# ── P3 POST 组织样本：夹带内部字段不生效 ──────────────────────────────────
NID="$(api --as extF POST /mp/ext/sample '{"sourceUnitName":"C 研究所","donorName":"供体新","gender":"female","age":"33","hospitalNo":"ZYEXT01","tissueType":"肝组织","hasPathology":"Y","remark":"第一次","internalNo":"HACK1","verifyStatus":"valid","submitSource":"internal","receiveDate":"2026-01-01","operatorName":"黑客","hasViabilityReport":"Y","isFixed":"Y","hasQcSheet":"Y"}' | jq -r '.data')"
chk P3-POST组织-夹带内部字段不生效 'tissue|external|pending|9000000116|-|-|-|-|-|-|true' \
  "$(sql "SELECT sample_kind||'|'||submit_source||'|'||verify_status||'|'||submitter_id||'|'||COALESCE(internal_no,'-')||'|'||COALESCE(receive_date::text,'-')||'|'||COALESCE(operator_name,'-')||'|'||COALESCE(has_viability_report,'-')||'|'||COALESCE(is_fixed,'-')||'|'||COALESCE(has_qc_sheet,'-')||'|'||(submit_no ~ '^SJ[0-9]{8}\$') FROM t_lqg_sample WHERE id=${NID}")"

# ── P4 POST 类器官：夹带内部 + 组织样本字段都不生效 ────────────────────────
OID="$(api --as extF POST /mp/ext/organoid '{"sourceUnitName":"C 研究所","organoidType":"肝类器官","remark":"类器官一","internalNo":"HACK2","verifyStatus":"valid","receiveDate":"2026-01-02","hasViabilityReport":"Y","operatorName":"黑客","isFixed":"Y","hasQcSheet":"Y","tissueType":"肝组织","donorName":"不该有","hospitalNo":"不该有","gender":"male","age":"9"}' | jq -r '.data')"
chk P4-POST类器官-夹带全不生效 'organoid|external|pending|-|-|-|-|-|-|-|-|-|-|-' \
  "$(sql "SELECT sample_kind||'|'||submit_source||'|'||verify_status||'|'||COALESCE(internal_no,'-')||'|'||COALESCE(receive_date::text,'-')||'|'||COALESCE(has_viability_report,'-')||'|'||COALESCE(operator_name,'-')||'|'||COALESCE(is_fixed,'-')||'|'||COALESCE(has_qc_sheet,'-')||'|'||COALESCE(tissue_type,'-')||'|'||COALESCE(donor_name,'-')||'|'||COALESCE(hospital_no,'-')||'|'||COALESCE(gender,'-')||'|'||COALESCE(age,'-') FROM t_lqg_sample WHERE id=${OID}")"

# ── P5 无效 → 重提回 pending，清 invalid_reason/verify_by ──────────────────
api --as extA PUT /mp/ext/sample/9000001003 '{"sourceUnitName":"A 医院","donorName":"测试供体丙","gender":"male","hospitalNo":"ZY0000003","tissueType":"肝组织","remark":"重提了","internalNo":"HACK3","verifyStatus":"valid","submitSource":"internal","receiveDate":"2026-09-17","operatorName":"黑客","hasViabilityReport":"Y"}' >/dev/null
chk P5-重提-回pending且清痕迹且夹带不生效 'pending|-|-|-|-|-|-|external|重提了' \
  "$(sql "SELECT verify_status||'|'||COALESCE(invalid_reason,'-')||'|'||COALESCE(verify_by::text,'-')||'|'||COALESCE(internal_no,'-')||'|'||COALESCE(receive_date::text,'-')||'|'||COALESCE(operator_name,'-')||'|'||COALESCE(has_viability_report,'-')||'|'||submit_source||'|'||COALESCE(remark,'-') FROM t_lqg_sample WHERE id=9000001003")"

# ── P6 组织入口改类器官样本 → 400，库里不变 ────────────────────────────────
chk P6a-组织入口改类器官-业务码 '400' \
  "$(api --as extF --bizcode PUT "/mp/ext/sample/${OID}" '{"sourceUnitName":"C 研究所","donorName":"借口改","tissueType":"肝组织"}' | cut -f1)"
chk P6b-组织入口改类器官-库里不变 '-|-|肝类器官' \
  "$(sql "SELECT COALESCE(donor_name,'-')||'|'||COALESCE(tissue_type,'-')||'|'||organoid_type FROM t_lqg_sample WHERE id=${OID}")"

# ── P7 外单位改 → 404（不泄露存在性）─────────────────────────────────────
chk P7a-外单位改-404 '404' \
  "$(api --as extD --bizcode PUT /mp/ext/sample/9000001005 '{"sourceUnitName":"B 大学","donorName":"跨单位改","tissueType":"胃组织"}' | cut -f1)"
chk P7b-外单位改-库里不变 'valid|lGSKaQVYm8UmBZ296TFGDw==' \
  "$(sql "SELECT verify_status||'|'||COALESCE(donor_name,'-') FROM t_lqg_sample WHERE id=9000001005")"

# ── P8 同组但对方未核验 → 404 ─────────────────────────────────────────────
chk P8a-同组未核验改-404 '404' \
  "$(api --as extB --bizcode PUT /mp/ext/sample/9000001007 '{"sourceUnitName":"A 医院","donorName":"同组未核验","tissueType":"肝组织"}' | cut -f1)"
chk P8b-同组未核验改-库里不变 'pending|HXIVgGKK4X1fEZO12s896w==' \
  "$(sql "SELECT verify_status||'|'||COALESCE(donor_name,'-') FROM t_lqg_sample WHERE id=9000001007")"

# ── P9 自己的 valid 也改不了 ──────────────────────────────────────────────
chk P9a-自己的valid改-400 '400' \
  "$(api --as extC --bizcode PUT /mp/ext/sample/9000001005 '{"sourceUnitName":"A 医院","donorName":"测试供体戊改","tissueType":"胃组织"}' | cut -f1)"
chk P9b-自己的valid改-库里不变 'valid|lGSKaQVYm8UmBZ296TFGDw==' \
  "$(sql "SELECT verify_status||'|'||COALESCE(donor_name,'-') FROM t_lqg_sample WHERE id=9000001005")"

# ── P10 软删样本任何接口都不出现 ──────────────────────────────────────────
chk P10a-软删详情-404 '404' "$(api --as extA --bizcode GET /mp/ext/sample/9000001010 | cut -f1)"
chk P10b-软删不在列表 'false' \
  "$(api --as extA GET '/mp/ext/sample/list?pageSize=100' | jq -c '[.rows[].id]|index(9000001010)!=null')"

# ── P11 内部打外部写端点 → 403（角色闸一个方向）──────────────────────────
chk P11a-admin打POST-organoid-403 '403' "$(api --as admin --bizcode POST /mp/ext/organoid '{"sourceUnitName":"X","organoidType":"Y"}' | cut -f1)"
chk P11b-staff打POST-sample-403 '403' "$(api --as staff --bizcode POST /mp/ext/sample '{"tissueType":"肝组织"}' | cut -f1)"
chk P11c-staff打PUT-sample-403 '403' "$(api --as staff --bizcode PUT /mp/ext/sample/9000001001 '{}' | cut -f1)"
chk P11d-admin打GET-详情-403 '403' "$(api --as admin --bizcode GET /mp/ext/sample/9000001001 | cut -f1)"

# ── P12 外部打内部 → 403（角色闸另一个方向）──────────────────────────────
chk P12-外部打lqg-403 '403' "$(api --as extA --bizcode GET '/lqg/sample/list' | cut -f1)"
# /mp/int/** 的端点由 SAMPLE-MP-001 注册；本票只能证到「它现在 404、且注册后必带
# @SaCheckRole("lqg_internal")」——见完工报告 WARN-3。
chk P12b-外部打mpint-未越权 '404' "$(api --as extA --bizcode GET '/mp/int/sample/list' | cut -f1)"

# ── P13 必填与来源单位解析 ────────────────────────────────────────────────
chk P13a-POST组织缺组织类型-500 '500' "$(api --as extF --bizcode POST /mp/ext/sample '{"sourceUnitName":"C 研究所","donorName":"缺组织类型"}' | cut -f1)"
chk P13b-POST类器官缺类器官类型-500 '500' "$(api --as extF --bizcode POST /mp/ext/organoid '{"sourceUnitName":"C 研究所"}' | cut -f1)"
UID2="$(api --as extF POST /mp/ext/organoid '{"sourceUnitId":9000009002,"organoidType":"肠类器官"}' | jq -r '.data')"
chk P13c-选了单位-id-单位名取快照 '9000009002|B 大学' \
  "$(sql "SELECT source_unit_id||'|'||source_unit_name FROM t_lqg_sample WHERE id=${UID2}")"
chk P13d-来源单位不存在-500且不落库 '500|0' \
  "$(api --as extF --bizcode POST /mp/ext/organoid '{"sourceUnitId":123456789,"organoidType":"邪门"}' | cut -f1)|$(sql "SELECT count(*) FROM t_lqg_sample WHERE organoid_type='邪门'")"

bash doc/verify/reseed.sh --yes >/dev/null
echo
echo "PROBE EXT001 PASS=${PASS} FAIL=${FAIL}"
[ "${FAIL}" = 0 ]
