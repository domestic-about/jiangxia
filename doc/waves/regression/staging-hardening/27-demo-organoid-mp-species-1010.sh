#!/usr/bin/env bash
# CR-20261010-21 回归（本机）：① 类器官收样记录的演示数据（迁移 V202610101100）② 小程序按种属筛用到的接口。
#   bash doc/waves/regression/staging-hardening/27-demo-organoid-mp-species-1010.sh
# 自己会先 reseed、再把演示数据的 SQL 灌一遍（reseed 会清掉演示数据）；结束时再 reseed 一次，库回到验收基线。
set -uo pipefail
cd "$(dirname "$0")/../../../.."
API="bash doc/verify/api.sh"
PG="${LQG_DEV_PG_CONTAINER:-lqg-dev-postgres}"
DEMO=code/RuoYi-Vue-Plus/ruoyi-admin/src/main/resources/db/migration/V202610101100__CR-20261010-21-demo-organoid-samples.sql
PASS=0; FAIL=0
ok()   { PASS=$((PASS+1)); echo "  ✓ $1"; }
bad()  { FAIL=$((FAIL+1)); echo "  ✗ $1"; echo "    → $2" | cut -c1-400; }
eq()   { if [ "$2" = "$3" ]; then ok "$1"; else bad "$1" "期望 $3 实际 $2"; fi; }
check(){ local name="$1" cond="$2" body="$3"; if echo "$body" | jq -e "$cond" >/dev/null 2>&1; then ok "$name"; else bad "$name" "$body"; fi; }
ids()  { $API --as "${3:-admin}" GET "$1&pageNum=1&pageSize=200" | jq -c "[.rows[]|$2]|sort"; }
TMP=$(mktemp -d)
REN=%E4%BA%BA; SHUTU=%E9%BC%A0%E5%85%94; ZHU=%E7%A7%BB%E6%A4%8D%E7%8C%AA; JI=%E9%B8%A1

bash doc/verify/reseed.sh --yes >/dev/null 2>&1
# 灌演示数据之前先记下基线的超期批次数（日期相对今天，不写死）
OVERDUE0=$($API GET '/lqg/cryo/batch/list?pageNum=1&pageSize=200' | jq .tabCounts.overdue)
docker exec -i "$PG" psql -U lqg -d lqg_dev -q -v ON_ERROR_STOP=1 < "$DEMO" >/dev/null || { echo "[error] 演示数据没灌进去"; exit 2; }
docker exec -i "$PG" psql -U lqg -d lqg_dev -q -v ON_ERROR_STOP=1 < "$DEMO" >/dev/null   # 再灌一遍：可重复执行

echo "D1 演示数据：类器官收样记录按种属筛（工作台）"
eq "不筛 = 6 条（T-oco01 + 5 条演示数据，重复执行不会翻倍）" "$($API GET '/lqg/sample/list?sampleKind=organoid&pageNum=1&pageSize=200' | jq .total)" 6
eq "人 = T-oga12、T-oli11"       "$(ids "/lqg/sample/list?sampleKind=organoid&species=$REN" .internalNo)"   '["T-oga12","T-oli11"]'
eq "移植猪 = T-oli13"            "$(ids "/lqg/sample/list?sampleKind=organoid&species=$ZHU" .internalNo)"   '["T-oli13"]'
eq "鸡 = T-oco14（合作单位送的）" "$(ids "/lqg/sample/list?sampleKind=organoid&species=$JI" '(.internalNo + "/" + .submitSource)')" '["T-oco14/external"]'
eq "鼠兔 = T-oco01、T-opa15"     "$(ids "/lqg/sample/list?sampleKind=organoid&species=$SHUTU" .internalNo)" '["T-oco01","T-opa15"]'
eq "未填 = 没有"                  "$(ids "/lqg/sample/list?sampleKind=organoid&species=__none__" .internalNo)" '[]'
eq "样本记录信息表不受影响：移植猪仍是 T-hli02、T-hli05" "$(ids "/lqg/sample/list?sampleKind=tissue&species=$ZHU" .internalNo)" '["T-hli02","T-hli05"]'

echo "D2 演示数据补上了原来空着的两格；冻存不进超期提醒"
eq "石蜡包埋 · 鼠兔 = T-E15-1"              "$(ids "/lqg/embed/list?species=$SHUTU" .paraffinBlockNo)" '["T-E15-1"]'
eq "石蜡包埋 · 移植猪 = T-E02-1、T-E13-1"    "$(ids "/lqg/embed/list?species=$ZHU" .paraffinBlockNo)"   '["T-E02-1","T-E13-1"]'
eq "-80 冻存 · 鸡 = T-oco14 那一批"          "$(ids "/lqg/cryo/batch/list?species=$JI" .cryoName)"       '["T-oco14-JC-T-P1-EM1-5e5"]'
R=$($API GET '/lqg/cryo/batch/list?pageNum=1&pageSize=200')
check "两批演示冻存都在液氮、不算超期" '([.rows[]|select(.cryoName|test("^T-o(li11|co14)-"))|[.location,.overdue]]|unique)==[["ln2",false]]' "$R"
eq "超期页签的数字和灌演示数据之前一样（不给测试站凭空多出待办）" "$(echo "$R" | jq .tabCounts.overdue)" "$OVERDUE0"
eq "质控文档 · 鸡 = T-hco04、T-oco14"        "$(ids "/lqg/qc/list?species=$JI" .internalNo)"            '["T-hco04","T-oco14"]'
R=$($API GET "/lqg/sample/list?sampleKind=organoid&species=$REN&pageNum=1&pageSize=200")
check "演示数据在备注里标明了「演示数据」" '[.rows[]|.remark]|unique==["演示数据"]' "$R"

echo "M1 小程序 · 内部管理四张表按种属筛（内部人员李工）"
eq "类器官收样记录 · 人"   "$(ids "/mp/int/sample/list?sampleKind=organoid&species=$REN" .internalNo staff)" '["T-oga12","T-oli11"]'
eq "样本记录信息表 · 移植猪" "$(ids "/mp/int/sample/list?sampleKind=tissue&species=$ZHU" .internalNo staff)" '["T-hli02","T-hli05"]'
eq "样本记录信息表 · 未填"  "$(ids "/mp/int/sample/list?sampleKind=tissue&species=__none__" .submitNo staff)" '["SJ90000007"]'
eq "石蜡包埋 · 人 = 3 条"   "$($API --as staff GET "/mp/int/embed/list?species=$REN&pageNum=1&pageSize=200" | jq .total)" 3
eq "石蜡包埋 · 人 + 染色 HE（两个筛选相与）" "$(ids "/mp/int/embed/list?species=$REN&stain=HE" .paraffinBlockNo staff)" '["T-E01-1"]'
eq "-80 冻存 · 移植猪 = 3 批" "$(ids "/mp/int/cryo/batch/list?species=$ZHU" .cryoName staff)" '["T-hli02-GZ-N-P2-EM2-2e5","T-hli05-GZ-N-P7-EM2-2e5","T-hli05-GZ-N-P8-EM2-2e5"]'
eq "-80 冻存 · 鼠兔 + 液氮页签（两个筛选相与）= 没有" "$(ids "/mp/int/cryo/batch/list?species=$SHUTU&location=ln2" .cryoName staff)" "$(ids "/lqg/cryo/batch/list?species=$SHUTU&location=ln2" .cryoName)"

echo "M2 小程序 · 导出跟着种属筛选走"
$API --as staff --out "$TMP/o.xlsx" GET "/mp/int/export/organoid?species=$REN"
$API --as staff --out "$TMP/c.xlsx" GET "/mp/int/export/cryo?species=$JI"
$API --as staff --out "$TMP/t.xlsx" GET "/mp/int/export/tissue?species=__none__"
R=$(python3 - "$TMP" <<'PY'
import sys,json,warnings
warnings.filterwarnings('ignore')
import openpyxl
out={}
for k in ('o','c','t'):
    rows=[[('' if c is None else str(c)) for c in r] for r in openpyxl.load_workbook(f'{sys.argv[1]}/{k}.xlsx').active.iter_rows(values_only=True)]
    i=rows[0].index('种属'); out[k]={'n':len(rows)-1,'species':sorted(set(r[i] for r in rows[1:]))}
print(json.dumps(out,ensure_ascii=False))
PY
)
check "类器官收样记录导出（筛人）：2 行全是人" '.o.n==2 and .o.species==["人"]' "$R"
check "-80 冻存导出（筛鸡）：1 行" '.c.n==1 and .c.species==["鸡"]' "$R"
check "样本记录信息表导出（筛未填）：1 行、种属那一格是空的" '.t.n==1 and .t.species==[""]' "$R"

echo "M3 小程序 · 质控文档（已完成文档清单、填写列表）按种属筛"
# reseed 之后文档产物是空的，「已完成文档清单」只列内部版渲染成功的 → 先让 seed 里已完成的那几份出内部版
for d in 9000001001/sample_qc 9000001001/organoid_qc 9000001001/organoid_score 9000001004/sample_qc 9000001006/organoid_score; do
  $API --as staff POST "/lqg/doc/$d/render?audience=internal" >/dev/null
done
ALL=$($API --as staff GET '/mp/int/doc/list?pageSize=100' | jq '.rows|length')
if [ "$ALL" -gt 0 ]; then ok "不筛：已完成文档清单有 $ALL 份"; else bad "不筛：已完成文档清单不该是空的" "$ALL"; fi
A=$($API --as staff GET "/mp/int/doc/list?pageSize=100&species=$REN")
B=$($API --as staff GET "/mp/int/doc/list?pageSize=100&species=$ZHU")
C=$($API --as staff GET "/mp/int/doc/list?pageSize=100&species=$JI")
D=$($API --as staff GET "/mp/int/doc/list?pageSize=100&species=$SHUTU")
N=$($API --as staff GET "/mp/int/doc/list?pageSize=100&species=__none__")
check "已完成文档 · 人：全是 T-hli01 的" '(.rows|length)>0 and ([.rows[]|.internalNo]|unique)==["T-hli01"]' "$A"
check "已完成文档 · 移植猪：全是 T-hli02 的" '(.rows|length)>0 and ([.rows[]|.internalNo]|unique)==["T-hli02"]' "$B"
check "已完成文档 · 鸡：全是 T-hco04 的" '(.rows|length)>0 and ([.rows[]|.internalNo]|unique)==["T-hco04"]' "$C"
check "已完成文档 · 鼠兔 / 未填：没有（T-hga03 只有草稿）" '.rows==[]' "$D"
check "已完成文档 · 未填：没有" '.rows==[]' "$N"
SUM=$(( $(echo "$A" | jq '.rows|length') + $(echo "$B" | jq '.rows|length') + $(echo "$C" | jq '.rows|length') ))
eq "各种属加起来 = 不筛的总数（没有漏、没有重）" "$SUM" "$ALL"
eq "填写列表 · 移植猪 = T-hli02、T-hli05、T-oli13" "$(ids "/lqg/qc/list?species=$ZHU" .internalNo staff)" '["T-hli02","T-hli05","T-oli13"]'
R=$($API --as staff GET "/lqg/qc/list?species=$ZHU&pageNum=1&pageSize=200")
check "填写列表的行上带种属" '[.rows[]|.species]|unique==["移植猪"]' "$R"

echo "M4 合作单位的文档清单不受种属参数影响（对外清单没有这个筛选）"
eq "extA 带不带 species 结果一样" "$($API --as extA GET "/mp/ext/doc/list?pageSize=100&species=$JI" | jq -c '[.rows[]|.docKind]|sort')" "$($API --as extA GET '/mp/ext/doc/list?pageSize=100' | jq -c '[.rows[]|.docKind]|sort')"

rm -rf "$TMP"
bash doc/verify/reseed.sh --yes >/dev/null 2>&1
eq "收尾：reseed 之后演示数据不在了（库回到验收基线）" "$($API GET '/lqg/sample/list?sampleKind=organoid&pageNum=1&pageSize=200' | jq .total)" 1
echo "结果：通过 $PASS，失败 $FAIL"
[ "$FAIL" -eq 0 ]
