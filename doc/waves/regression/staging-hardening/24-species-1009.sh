#!/usr/bin/env bash
# CR-20261009-18「种属」回归（本机，对 doc/verify/verify.env 指向的后端）。
#   bash doc/verify/reseed.sh --yes && bash doc/waves/regression/staging-hardening/24-species-1009.sh
# S0 / S0b 的期望值钉在模拟数据（seed）的种属分配上，所以要先 reseed。
# 会建一条外部组织样本、一条内部组织样本 + 一个石蜡块 + 一个冻存批次（编号前缀 R24-），结束时全部删掉。
set -uo pipefail
cd "$(dirname "$0")/../../../.."
API="bash doc/verify/api.sh"
PASS=0; FAIL=0
ok()   { PASS=$((PASS+1)); echo "  ✓ $1"; }
bad()  { FAIL=$((FAIL+1)); echo "  ✗ $1"; echo "    → $2" | cut -c1-400; }
check(){ local name="$1" cond="$2" body="$3"; if echo "$body" | jq -e "$cond" >/dev/null 2>&1; then ok "$name"; else bad "$name" "$body"; fi; }
TAG="R24-$(date +%H%M%S)"
TMP=$(mktemp -d)

# ── S0 模拟数据逐表按种属筛（2026-10-10 加：甲方要验「选了种属只出相应的记录、导出的 Excel 只有这个种属」）──
#    期望值来自 doc/verify/gen_seed.py 的种属分配（与迁移 V202610101000 同值）；跑之前先 reseed。
#    urlencode：人 %E4%BA%BA ｜ 鼠兔 %E9%BC%A0%E5%85%94 ｜ 移植猪 %E7%A7%BB%E6%A4%8D%E7%8C%AA ｜ 鸡 %E9%B8%A1
REN=%E4%BA%BA; SHUTU=%E9%BC%A0%E5%85%94; ZHU=%E7%A7%BB%E6%A4%8D%E7%8C%AA; JI=%E9%B8%A1
echo "S0 模拟数据：五张表逐个按种属筛，只出这个种属的、而且一条不漏"
ids() { $API GET "$1&pageNum=1&pageSize=200" | jq -c "[.rows[]|$2]|sort"; }
eq() { if [ "$2" = "$3" ]; then ok "$1"; else bad "$1" "期望 $3 实际 $2"; fi; }
eq "样本记录信息表 · 人 = 有效 / 待核验 / 无效三条"        "$(ids "/lqg/sample/list?sampleKind=tissue&species=$REN" .submitNo)"    '["SJ90000001","SJ90000002","SJ90000003"]'
eq "样本记录信息表 · 移植猪 = T-hli02、T-hli05"             "$(ids "/lqg/sample/list?sampleKind=tissue&species=$ZHU" .internalNo)" '["T-hli02","T-hli05"]'
eq "样本记录信息表 · 鼠兔 = T-hga03"                        "$(ids "/lqg/sample/list?sampleKind=tissue&species=$SHUTU" .internalNo)" '["T-hga03"]'
eq "样本记录信息表 · 鸡 = T-hco04（已删除的那条鸡不出来）"   "$(ids "/lqg/sample/list?sampleKind=tissue&species=$JI" .internalNo)"  '["T-hco04"]'
eq "样本记录信息表 · 未填 = 待核验的 SJ90000007"            "$(ids "/lqg/sample/list?sampleKind=tissue&species=__none__" .submitNo)" '["SJ90000007"]'
eq "类器官收样记录 · 鼠兔 = T-oco01"                        "$(ids "/lqg/sample/list?sampleKind=organoid&species=$SHUTU" .internalNo)" '["T-oco01"]'
eq "类器官收样记录 · 人 = 没有"                             "$(ids "/lqg/sample/list?sampleKind=organoid&species=$REN" .internalNo)" '[]'
eq "石蜡包埋 · 人 = T-E01-1、T-E01-2 + 一条待核验的送样"    "$(ids "/lqg/embed/list?species=$REN" '(.paraffinBlockNo // "待核验")')" '["T-E01-1","T-E01-2","待核验"]'
eq "石蜡包埋 · 移植猪 = T-E02-1（T-hli05 那块已删除的不出来）" "$(ids "/lqg/embed/list?species=$ZHU" .paraffinBlockNo)" '["T-E02-1"]'
eq "石蜡包埋 · 鸡 = T-E04-1"                                "$(ids "/lqg/embed/list?species=$JI" .paraffinBlockNo)" '["T-E04-1"]'
eq "石蜡包埋 · 鼠兔 = 没有"                                 "$(ids "/lqg/embed/list?species=$SHUTU" .paraffinBlockNo)" '[]'
eq "-80 冻存 · 人 = T-hli01 的两批"                          "$(ids "/lqg/cryo/batch/list?species=$REN" .cryoName)" '["T-hli01-GZ-N-P2-EM2-2e5","T-hli01-GZ-N-P5-EM2-1e5"]'
eq "-80 冻存 · 移植猪 = T-hli02 一批 + T-hli05 两批（已删除的那批不出来）" "$(ids "/lqg/cryo/batch/list?species=$ZHU" .cryoName)" '["T-hli02-GZ-N-P2-EM2-2e5","T-hli05-GZ-N-P7-EM2-2e5","T-hli05-GZ-N-P8-EM2-2e5"]'
eq "-80 冻存 · 鼠兔 = T-oco01 的两批"                        "$(ids "/lqg/cryo/batch/list?species=$SHUTU" .cryoName)" '["T-oco01-JC-T-P3-EM1-5e5","T-oco01-JC-T-P4-EM1-5e5"]'
eq "-80 冻存 · 鸡 = 没有"                                    "$(ids "/lqg/cryo/batch/list?species=$JI" .cryoName)" '[]'
eq "质控文档 · 人 = T-hli01"                                 "$(ids "/lqg/qc/list?species=$REN" .internalNo)" '["T-hli01"]'
eq "质控文档 · 移植猪 = T-hli02、T-hli05（甲方的例子：选猪，猪的都出来）" "$(ids "/lqg/qc/list?species=$ZHU" .internalNo)" '["T-hli02","T-hli05"]'
eq "质控文档 · 鼠兔 = T-hga03、T-oco01"                      "$(ids "/lqg/qc/list?species=$SHUTU" .internalNo)" '["T-hga03","T-oco01"]'
eq "质控文档 · 鸡 = T-hco04"                                 "$(ids "/lqg/qc/list?species=$JI" .internalNo)" '["T-hco04"]'
# 不筛 = 全部：各表按种属分出来的加起来正好是整张表（没有漏掉、也没有重复算）
eq "不筛种属：质控文档 6 条、冻存 7 批、石蜡包埋 5 条" "$($API GET '/lqg/qc/list?pageNum=1&pageSize=200' | jq .total)/$($API GET '/lqg/cryo/batch/list?pageNum=1&pageSize=200' | jq .total)/$($API GET '/lqg/embed/list?pageNum=1&pageSize=200' | jq .total)" '6/7/5'

echo "S0b 模拟数据：带着种属筛选导出 —— Excel 里只有这个种属、而且都在同一张表里"
$API --out "$TMP/s-t.xlsx" POST "/lqg/sample/export/tissue?species=$ZHU"
$API --out "$TMP/s-o.xlsx" POST "/lqg/sample/export/organoid?species=$SHUTU"
$API --out "$TMP/s-e.xlsx" POST "/lqg/embed/export?species=$REN"
$API --out "$TMP/s-c.xlsx" POST "/lqg/cryo/batch/export?species=$ZHU"
$API --out "$TMP/s-all.xlsx" POST "/lqg/sample/export/tissue"
R=$(python3 - "$TMP" <<'PY'
import sys,json,warnings
warnings.filterwarnings('ignore')
import openpyxl
d=sys.argv[1]; out={}
for k in ('s-t','s-o','s-e','s-c','s-all'):
    ws=openpyxl.load_workbook(f'{d}/{k}.xlsx').active
    rows=[[('' if c is None else str(c)) for c in r] for r in ws.iter_rows(values_only=True)]
    head=rows[0]; i=head.index('种属')
    out[k]={'n':len(rows)-1,'species':sorted(set(r[i] for r in rows[1:])),'rows':[dict(zip(head,r)) for r in rows[1:]]}
print(json.dumps(out,ensure_ascii=False))
PY
)
check "样本记录信息表导出（筛移植猪）：2 行，全是移植猪，T-hli02、T-hli05 都在" '.["s-t"].n==2 and .["s-t"].species==["移植猪"] and ([.["s-t"].rows[]|.["内部编号"]]|sort)==["T-hli02","T-hli05"]' "$R"
check "类器官收样记录导出（筛鼠兔）：1 行 T-oco01" '.["s-o"].n==1 and .["s-o"].species==["鼠兔"] and .["s-o"].rows[0]["内部编号"]=="T-oco01"' "$R"
check "石蜡包埋导出（筛人）：全是人，T-E01-1、T-E01-2 都在" '.["s-e"].species==["人"] and (([.["s-e"].rows[]|.["石蜡块编号"]]|map(select(.!=""))|sort)==["T-E01-1","T-E01-2"])' "$R"
check "-80 冻存导出（筛移植猪）：3 行，全是移植猪" '.["s-c"].n==3 and .["s-c"].species==["移植猪"]' "$R"
check "不筛种属导出：各种属都在同一张表里（人 / 移植猪 / 鼠兔 / 鸡 + 一条未填）" '.["s-all"].species==["","人","移植猪","鸡","鼠兔"]' "$R"

echo "S1 字典：/mp/dict/hints?type=species = 人 / 鼠兔 / 移植猪 / 鸡"
R=$($API --as extA GET '/mp/dict/hints?type=species')
check "四个常用值、按字典顺序" '.code==200 and .data==["人","鼠兔","移植猪","鸡"]' "$R"

echo "S2 外部提交组织样本：不带种属 → 400；带字典外的「食蟹猴」→ 收下，详情 / 列表都带种属"
R=$($API --as extA POST /mp/ext/sample '{"sourceUnitName":"A 医院","donorName":"回归供体","tissueType":"肝组织"}')
check "没种属 → 400「种属不能为空」" '.code==400 and (.msg|test("种属不能为空"))' "$R"
R=$($API --as extA POST /mp/ext/sample '{"sourceUnitName":"A 医院","species":" 食蟹猴 ","donorName":"回归供体","tissueType":"肝组织"}')
check "带种属 → 成功" '.code==200' "$R"
EXT_ID=$(echo "$R" | jq -r '.data')
R=$($API --as extA GET "/mp/ext/sample/$EXT_ID")
check "外部详情 species=食蟹猴（去了首尾空白）" '.data.species=="食蟹猴"' "$R"
R=$($API --as extA GET '/mp/ext/sample/list?pageNum=1&pageSize=50')
check "外部列表行带 species" "[.rows[]?|select(.id==\"$EXT_ID\" or .id==$EXT_ID)|.species]==[\"食蟹猴\"]" "$R"

echo "S3 外部改后重提：不带种属 → 400（整段替换不许把种属洗空）"
R=$($API --as extA PUT "/mp/ext/sample/$EXT_ID" '{"sourceUnitName":"A 医院","donorName":"回归供体","tissueType":"肝组织"}')
check "重提缺种属 → 400" '.code==400 and (.msg|test("种属"))' "$R"

echo "S4 工作台内部新增组织样本（种属 = 鼠兔），按种属筛"
R=$($API POST /lqg/sample "{\"sampleKind\":\"tissue\",\"sourceUnitName\":\"A 医院\",\"species\":\"鼠兔\",\"tissueType\":\"肝组织\",\"internalNo\":\"$TAG\",\"receiveDate\":\"2026-10-09\"}")
check "新增成功" '.code==200' "$R"
SID=$(echo "$R" | jq -r '.data')
R=$($API GET "/lqg/sample/list?sampleKind=tissue&species=%E9%BC%A0%E5%85%94&pageNum=1&pageSize=200")
check "species=鼠兔 筛得到它，且行行都是鼠兔" "([.rows[]|.internalNo]|index(\"$TAG\"))!=null and ([.rows[]|.species]|unique==[\"鼠兔\"])" "$R"
R=$($API GET "/lqg/sample/list?sampleKind=tissue&species=__none__&pageNum=1&pageSize=200")
check "species=__none__ 只出没填的老记录，不含它" "([.rows[]|.internalNo]|index(\"$TAG\"))==null and ([.rows[]|.species]|unique==[null])" "$R"
R=$($API PUT /lqg/sample "{\"id\":\"$SID\",\"sampleKind\":\"tissue\",\"sourceUnitName\":\"A 医院\",\"species\":\"\",\"tissueType\":\"肝组织\",\"internalNo\":\"$TAG\",\"receiveDate\":\"2026-10-09\"}")
check "工作台修改把种属清空 → 400" '.code==400 and (.msg|test("种属不能为空"))' "$R"

echo "S5 石蜡块、冻存批次：读所挂样本的种属，可按种属筛"
R=$($API POST /lqg/embed "{\"sampleId\":\"$SID\",\"paraffinBlockNo\":\"$TAG-E1\",\"sampleType\":\"组织\"}")
check "石蜡块新增" '.code==200' "$R"
EID=$(echo "$R" | jq -r '.data')
R=$($API GET "/lqg/embed/list?species=%E9%BC%A0%E5%85%94&pageNum=1&pageSize=200")
check "石蜡包埋 species=鼠兔 筛得到它，行上带种属" "[.rows[]|select(.paraffinBlockNo==\"$TAG-E1\")|.species]==[\"鼠兔\"]" "$R"
R=$($API POST /lqg/cryo/batch "{\"sampleId\":\"$SID\",\"cryoName\":\"$TAG-C1\",\"passage\":\"P2\",\"freezeTime\":\"2026-10-09\",\"initQty\":3,\"inMinus80\":\"Y\"}")
check "冻存批次新增" '.code==200' "$R"
CID=$(echo "$R" | jq -r '.data')
R=$($API GET "/lqg/cryo/batch/list?species=%E9%BC%A0%E5%85%94&pageNum=1&pageSize=200")
check "冻存 species=鼠兔 筛得到它，行上带种属" "[.rows[]|select(.cryoName==\"$TAG-C1\")|.species]==[\"鼠兔\"]" "$R"
R=$($API GET "/lqg/cryo/batch/list?species=%E4%BA%BA&pageNum=1&pageSize=200")
check "冻存 species=人 筛不到它" "[.rows[]|select(.cryoName==\"$TAG-C1\")]==[]" "$R"

echo "S6 四张导出：表头带「种属」（样本两张紧跟来源单位、石蜡紧跟样本编号、冻存在最后），值对"
$API --out "$TMP/t.xlsx" POST "/lqg/sample/export/tissue?internalNo=$TAG"
$API --out "$TMP/e.xlsx" POST "/lqg/embed/export?internalNo=$TAG"
$API --out "$TMP/c.xlsx" POST "/lqg/cryo/batch/export?internalNo=$TAG"
$API --out "$TMP/o.xlsx" POST "/lqg/sample/export/organoid"
R=$(python3 - "$TMP" <<'PY'
import sys,json,zipfile,re
def rows(p):
    z=zipfile.ZipFile(p); ss=[]
    if 'xl/sharedStrings.xml' in z.namelist():
        ss=[ ''.join(re.findall(r'<t[^>]*>([^<]*)</t>',si)) for si in re.findall(r'<si>(.*?)</si>',z.read('xl/sharedStrings.xml').decode(),re.S)]
    out=[]
    for r in re.findall(r'<row[^>]*>(.*?)</row>',z.read('xl/worksheets/sheet1.xml').decode(),re.S):
        cells=[]
        for c in re.findall(r'<c ([^>]*?)(?:/>|>(.*?)</c>)',r,re.S):
            attrs,body=c; v=re.search(r'<v>(.*?)</v>',body or ''); t=re.search(r't="(\w+)"',attrs)
            txt=re.search(r'<t[^>]*>(.*?)</t>',body or '')
            cells.append(ss[int(v.group(1))] if (t and t.group(1)=='s' and v) else (txt.group(1) if txt else (v.group(1) if v else '')))
        out.append(cells)
    return out
d=sys.argv[1]; res={}
for k in 'tecо'.replace('о','o'):
    r=rows(f'{d}/{k}.xlsx'); res[k]={'head':r[0],'row':r[1] if len(r)>1 else []}
print(json.dumps(res,ensure_ascii=False))
PY
)
check "样本记录信息表：第 2 列「种属」= 鼠兔" '.t.head[1]=="种属" and .t.head[0]=="来源单位" and .t.row[1]=="鼠兔" and (.t.head|length)==15' "$R"
check "类器官收样记录：第 2 列「种属」、「代数」仍紧跟类器官类型" '.o.head[0:4]==["来源单位","种属","类器官类型","代数"]' "$R"
check "石蜡包埋：「种属」紧跟「样本编号」= 鼠兔" '.e.head[1:3]==["样本编号","种属"] and .e.row[2]=="鼠兔" and (.e.head|length)==17' "$R"
check "冻存：「种属」追加在最后 = 鼠兔" '.c.head[-1]=="种属" and .c.head[9:11]==["代数","当前剩余/支"] and .c.row[-1]=="鼠兔"' "$R"

echo "S7 样本质控表：编辑页头带种属；渲染出的 Word 里「性别」行下面是「种属 | 鼠兔」"
R=$($API GET "/lqg/qc/$SID")
check "质控 bundle 的 sample.species=鼠兔" '.data.sample.species=="鼠兔"' "$R"
R=$($API GET "/lqg/qc/list?species=%E9%BC%A0%E5%85%94&pageNum=1&pageSize=200")
check "质控文档列表按种属筛得到它" "[.rows[]|select(.internalNo==\"$TAG\")|.species]==[\"鼠兔\"]" "$R"
R=$($API POST "/lqg/doc/$SID/sample_qc/render?audience=internal&force=true")
for i in $(seq 1 60); do
  P=$($API GET "/lqg/doc/$SID/sample_qc/pages?audience=internal")
  st=$(echo "$P" | jq -r '.data.status'); [ "$st" = "done" ] || [ "$st" = "failed" ] && break; sleep 1
done
check "渲染完成" '.data.status=="done"' "$P"
echo "    页数：$(echo "$P" | jq '.data.pages|length')"
U=$($API GET "/lqg/doc/$SID/sample_qc/download?format=docx&audience=internal" | jq -r '.data.url')
curl -s -o "$TMP/qc.docx" "$U"
R=$(python3 - "$TMP/qc.docx" <<'PY'
import sys,re,zipfile,json
x=zipfile.ZipFile(sys.argv[1]).read('word/document.xml').decode()
rows=[ [''.join(re.findall(r'<w:t[^>]*>([^<]*)</w:t>',c)) for c in re.findall(r'<w:tc>.*?</w:tc>',r,re.S)] for r in re.findall(r'<w:tr[ >].*?</w:tr>',x,re.S)]
print(json.dumps(rows,ensure_ascii=False))
PY
)
check "Word：「性别」行之后是 [种属, 鼠兔]" '(map(.[0])|index("性别")) as $g | .[$g+1]==["种属","鼠兔"]' "$R"
U=$($API GET "/lqg/doc/$SID/sample_qc/download?format=pdf&audience=internal" | jq -r '.data.url')
curl -s -o "$TMP/qc.pdf" "$U"
echo "    PDF：$(python3 -c "import re,sys;d=open('$TMP/qc.pdf','rb').read();print(len(re.findall(rb'/Type\s*/Page[^s]',d)),'页')")"

echo "清理"
$API DELETE "/lqg/cryo/batch/$CID" >/dev/null
$API DELETE "/lqg/embed/$EID" >/dev/null
R=$($API DELETE "/lqg/sample/$SID"); check "删内部样本" '.code==200' "$R"
R=$($API DELETE "/lqg/sample/$EXT_ID"); check "删外部样本" '.code==200' "$R"
cp "$TMP/qc.pdf" "${QC_PDF_OUT:-/dev/null}" 2>/dev/null || true
rm -rf "$TMP"
echo "结果：通过 $PASS，失败 $FAIL"
[ "$FAIL" -eq 0 ]
