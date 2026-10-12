#!/usr/bin/env bash
# CR-20261012-22 回归（本机）：操作人 / 包埋人 / 冻存人从内部人员名单里选 + 操作人统计。
#   bash doc/waves/regression/staging-hardening/29-operator-stat-1012.sh
# 自己会先 reseed；过程中建两个测试用的内部人员（手机号 13900002201 / 13900002202），结束时删掉并再 reseed，库回到验收基线。
set -uo pipefail
cd "$(dirname "$0")/../../../.."
API="bash doc/verify/api.sh"
PG="${LQG_DEV_PG_CONTAINER:-lqg-dev-postgres}"
MIG=code/RuoYi-Vue-Plus/ruoyi-admin/src/main/resources/db/migration/V202610121000__CR-20261012-22-operator-user.sql
PASS=0; FAIL=0
ok()   { PASS=$((PASS+1)); echo "  ✓ $1"; }
bad()  { FAIL=$((FAIL+1)); echo "  ✗ $1"; echo "    → $2" | cut -c1-500; }
eq()   { if [ "$2" = "$3" ]; then ok "$1"; else bad "$1" "期望 $3 实际 $2"; fi; }
check(){ local name="$1" cond="$2" body="$3"; if echo "$body" | jq -e "$cond" >/dev/null 2>&1; then ok "$name"; else bad "$name" "$body"; fi; }
sql()  { docker exec -i "$PG" psql -U lqg -d lqg_dev -tA -v ON_ERROR_STOP=1 -c "$1"; }
day()  { python3 -c "import datetime;print(datetime.date.today()-datetime.timedelta(days=$1))"; }
drop_test_staff() {
  sql "DELETE FROM sys_user_role WHERE user_id IN (SELECT user_id FROM sys_user WHERE phonenumber IN ('13900002201','13900002202'));
       DELETE FROM t_lqg_ext_profile WHERE user_id IN (SELECT user_id FROM sys_user WHERE phonenumber IN ('13900002201','13900002202'));
       DELETE FROM sys_user WHERE phonenumber IN ('13900002201','13900002202');" >/dev/null
}
LI=9000000101          # seed 内部人员李工
EXT_A=9000000111       # seed 外部 王医生（不是内部人员）
TAG="T-op$(date +%H%M%S)"
TODAY=$(day 0); D10=$(day 10); D11=$(day 11)

drop_test_staff
bash doc/verify/reseed.sh --yes >/dev/null 2>&1

echo "S1 选人名单：每个内部人员都能调，外部一律 403"
R=$($API --as staff GET /mp/int/staff/options)
check "小程序（李工）：名单 = 李工 + 测试管理员，只有账号 id 与姓名" '.code==200 and ([.data[]|.name]|sort)==["李工","测试管理员"] and ([.data[]|keys]|unique)==[["name","userId"]]' "$R"
check "工作台（管理员）：同一份名单" '([.data[]|.name]|sort)==["李工","测试管理员"]' "$($API GET /lqg/staff/options)"
eq "外部账号调小程序那条 → 403" "$($API --as extA --bizcode GET /mp/int/staff/options | cut -f1)" 403
eq "外部账号调工作台那条 → 403" "$($API --as extA --bizcode GET /lqg/staff/options | cut -f1)" 403
eq "没登录 → 401" "$($API --as anon --bizcode GET /lqg/staff/options | cut -f1)" 401
R=$($API POST /lqg/auth/staff '{"phone":"13900002201","name":"刘佳","roleKey":"lqg_internal","password":"Test@12345"}')
LIU=$(echo "$R" | jq -r '.data.userId')
check "管理员在「内部人员授权」里加了刘佳 → 名单里马上有她" "[.data[]|select((.userId|tostring)==\"$LIU\")|.name]==[\"刘佳\"]" "$($API --as staff GET /mp/int/staff/options)"

echo "S2 操作人统计：seed 基线（全部时间）"
R=$($API --as staff GET /mp/int/stat/operator)
check "四张表、顺序固定" '[.data.sheets[]|.sheet]==["tissue","organoid","embed","cryo"]' "$R"
check "样本记录信息表：5 条都是李工（只算已核验有效的；待核验的 1002 / 1007、判无效的 1003、软删的 1010 不算）" \
  '.data.sheets[0]|(.totals.count==5 and ([.rows[]|[.key,.name,.linked,.values.count]]==[["u:9000000101","李工",true,5]]))' "$R"
check "类器官送样记录：1 条" '.data.sheets[1]|(.totals.count==1 and .rows[0].name=="李工")' "$R"
check "石蜡包埋：包埋人、操作人两列；李工各 1 块，另外 3 块这两栏没填 →「未填」一行排在最后（软删的 T-E05-X、待核验的 2006 不算）" \
  '.data.sheets[2]|([.metrics[]|.key]==["embedBy","operator"] and .totals=={"embedBy":4,"operator":4} and ([.rows[]|[.key,.values.embedBy,.values.operator]]==[["u:9000000101",1,1],["none",3,3]]))' "$R"
check "-80 冻存：7 批 33 支都是李工（软删的 3008 不算；支数 = 冻存数量之和，不是当前剩余）" \
  '.data.sheets[3]|(.totals=={"batches":7,"tubes":33} and (.rows|length)==1)' "$R"
check "当前登录人那一行标了「我」" '[.data.sheets[]|.rows[]|select(.key=="u:9000000101")|.me]|unique==[true]' "$R"
check "管理员看：同样的数，李工那一行不是「我」" '(.data.sheets[0].totals.count==5) and ([.data.sheets[]|.rows[]|.me]|unique==[false])' "$($API GET /lqg/stat/operator)"
eq "外部账号 → 403（工作台那条）" "$($API --as extA --bizcode GET /lqg/stat/operator | cut -f1)" 403
eq "外部账号 → 403（小程序那条）" "$($API --as extA --bizcode GET /mp/int/stat/operator | cut -f1)" 403

echo "S3 时间段：两头都含；只给一头 = 这一天及之前 / 及之后"
R=$($API GET "/lqg/stat/operator?begin=$D10&end=$TODAY")
check "最近 10 天冻存：只有 5 天前那一批（4 支）" '.data.sheets[3].totals=={"batches":1,"tubes":4} and .data.begin!=null and .data.end!=null' "$R"
check "只给结束日期（11 天前及之前）：其余 6 批 29 支" '.data.sheets[3].totals=={"batches":6,"tubes":29}' "$($API GET "/lqg/stat/operator?end=$D11")"
check "只给开始日期（10 天前及之后）：1 批" '.data.sheets[3].totals.batches==1' "$($API GET "/lqg/stat/operator?begin=$D10")"
check "没有记录的时间段：合计是 0、没有行（不是报错）" '[.data.sheets[]|.rows|length]==[0,0,0,0] and .data.sheets[0].totals.count==0' "$($API GET '/lqg/stat/operator?begin=2001-01-01&end=2001-12-31')"
check "开始晚于结束 → 400" '.code==400' "$($API GET "/lqg/stat/operator?begin=$TODAY&end=$D10")"

echo "S4 新增：选了谁落谁；没填默认当前登录人；旧版客户端只带名字也能对上账号"
mk() { $API "$@" | jq -r '.data'; }
BODY="\"sampleKind\":\"tissue\",\"sourceUnitName\":\"A 医院\",\"species\":\"人\",\"tissueType\":\"肝组织\",\"receiveDate\":\"$TODAY\""
S_LIU=$(mk --as staff POST /mp/int/sample "{$BODY,\"internalNo\":\"$TAG-1\",\"operatorId\":\"$LIU\",\"operatorName\":\"乱写的名字\"}")
R=$($API GET "/lqg/sample/$S_LIU")
check "选了刘佳：账号 id 是她，姓名取名单里的（不信请求里的名字）" "(.data.operatorId|tostring)==\"$LIU\" and .data.operatorName==\"刘佳\"" "$R"
S_DEF=$(mk --as staff POST /mp/int/sample "{$BODY,\"internalNo\":\"$TAG-2\"}")
check "没填：默认当前登录人李工（带账号 id）" ".data.operatorId==$LI and .data.operatorName==\"李工\"" "$($API GET "/lqg/sample/$S_DEF")"
S_OLD=$(mk POST /lqg/sample "{$BODY,\"internalNo\":\"$TAG-3\",\"operatorName\":\" 刘佳 \"}")
check "旧版客户端只带名字「刘佳」：对上唯一一个人 → 自动关联" "(.data.operatorId|tostring)==\"$LIU\" and .data.operatorName==\"刘佳\"" "$($API GET "/lqg/sample/$S_OLD")"
S_TXT=$(mk POST /lqg/sample "{$BODY,\"internalNo\":\"$TAG-4\",\"operatorName\":\"袁琴琴\"}")
check "名字对不上账号（袁琴琴）：只存文字，不关联" '.data.operatorId==null and .data.operatorName=="袁琴琴"' "$($API GET "/lqg/sample/$S_TXT")"
N0=$(sql "select count(*) from t_lqg_sample")
R=$($API POST /lqg/sample "{$BODY,\"internalNo\":\"$TAG-5\",\"operatorId\":$EXT_A}")
check "★ 选了一个不是内部人员的账号 → 400，写明是操作人" '.code==400 and (.msg|test("操作人不在内部人员名单"))' "$R"
eq "被拒的那一条没落库" "$(sql "select count(*) from t_lqg_sample")" "$N0"

echo "S5 修改 / 核验：这一对键都没带 = 不动；换人两列一起变"
$API --as staff PUT /mp/int/sample "{\"id\":\"$S_LIU\",\"remark\":\"只改备注\"}" >/dev/null
check "小程序补丁只改备注：操作人原样" "(.data.operatorId|tostring)==\"$LIU\" and .data.remark==\"只改备注\"" "$($API GET "/lqg/sample/$S_LIU")"
$API --as staff PUT /mp/int/sample "{\"id\":\"$S_LIU\",\"operatorId\":$LI}" >/dev/null
check "小程序补丁只带账号 id（换成李工）：姓名跟着换" ".data.operatorId==$LI and .data.operatorName==\"李工\"" "$($API GET "/lqg/sample/$S_LIU")"
$API --as staff PUT /mp/int/sample "{\"id\":\"$S_TXT\",\"operatorId\":\"$LIU\",\"operatorName\":\"刘佳\"}" >/dev/null
check "历史手填的「袁琴琴」重新选成刘佳：补上了关联" "(.data.operatorId|tostring)==\"$LIU\"" "$($API GET "/lqg/sample/$S_TXT")"
$API --as staff PUT /mp/int/sample "{\"id\":\"$S_TXT\",\"operatorName\":\"袁琴琴\"}" >/dev/null   # 改回手填文字，后面统计里要看「未关联」那一行
R=$($API PUT /lqg/sample/9000001002/verify "{\"action\":\"valid\",\"receiveDate\":\"$TODAY\",\"internalNo\":\"$TAG-V\",\"operatorId\":\"$LIU\",\"operatorName\":\"刘佳\"}")
check "核验合作单位提交的 1002：操作人选刘佳" '.code==200' "$R"
check "核验后操作人是刘佳（做实验的人，不是点核验的管理员）" "(.data.operatorId|tostring)==\"$LIU\" and .data.verifyStatus==\"valid\"" "$($API GET /lqg/sample/9000001002)"

echo "S6 石蜡包埋（包埋人、操作人两栏）与冻存（冻存人）"
E1=$(mk POST /lqg/embed "{\"sampleId\":\"$S_LIU\",\"paraffinBlockNo\":\"$TAG-E1\",\"sampleType\":\"组织\",\"paraffinEmbedTime\":\"$TODAY\",\"embedById\":\"$LIU\"}")
R=$($API GET "/lqg/embed/$E1")
check "新增石蜡块：包埋人选刘佳；操作人没填 → 默认当前登录人（管理员）" "(.data.embedById|tostring)==\"$LIU\" and .data.embedBy==\"刘佳\" and .data.operatorName==\"测试管理员\" and .data.operatorId==9000000100" "$R"
$API PUT /lqg/embed "{\"id\":\"$E1\",\"sectionTime\":\"$TODAY\"}" >/dev/null
check "补填切片时间：两栏人名都不动" "(.data.embedById|tostring)==\"$LIU\" and .data.operatorId==9000000100 and .data.sectionTime==\"$TODAY\"" "$($API GET "/lqg/embed/$E1")"
$API --as staff PUT /mp/int/embed "{\"id\":\"$E1\",\"operatorId\":$LI,\"operatorName\":\"李工\",\"embedBy\":\"袁琴琴\"}" >/dev/null
check "操作人换成李工；包埋人只带了名字「袁琴琴」（对不上账号）→ 只存文字、关联清掉" ".data.operatorId==$LI and .data.embedById==null and .data.embedBy==\"袁琴琴\"" "$($API GET "/lqg/embed/$E1")"
R=$($API PUT /lqg/embed "{\"id\":\"$E1\",\"embedById\":$EXT_A}")
check "包埋人选了名单外的账号 → 400，写明是包埋人" '.code==400 and (.msg|test("包埋人不在内部人员名单"))' "$R"
C1=$(mk --as staff POST /mp/int/cryo/batch "{\"sampleId\":\"$S_LIU\",\"cryoName\":\"$TAG-C1\",\"passage\":\"P2\",\"freezeTime\":\"$TODAY\",\"initQty\":3,\"inMinus80\":\"N\",\"ln2Location\":\"回归罐\",\"frozenById\":\"$LIU\"}")
check "新增冻存批次：冻存人选刘佳" "(.data.frozenById|tostring)==\"$LIU\" and .data.frozenBy==\"刘佳\"" "$($API GET "/lqg/cryo/batch/$C1")"
$API PUT /lqg/cryo/batch "{\"id\":\"$C1\",\"frozenBy\":\"李工\"}" >/dev/null
check "旧版客户端只带名字「李工」改冻存人：自动关联到李工的账号" ".data.frozenById==$LI and .data.frozenBy==\"李工\"" "$($API GET "/lqg/cryo/batch/$C1")"
$API PUT /lqg/cryo/batch "{\"id\":\"$C1\",\"frozenById\":null,\"frozenBy\":\"\"}" >/dev/null
check "清空冻存人：两列一起清" '.data.frozenById==null and .data.frozenBy==null' "$($API GET "/lqg/cryo/batch/$C1")"

echo "S7 统计里：关联了账号的、没关联的手填姓名、没填的，各是各的一行"
R=$($API GET /lqg/stat/operator)
check "样本记录信息表：李工 7（原 5 + 两条新的）、刘佳 2、袁琴琴（未关联）1，合计 10" \
  "(.data.sheets[0].totals.count==10) and ([.data.sheets[0].rows[]|[.name,.linked,.values.count]]|sort)==([[\"李工\",true,7],[\"刘佳\",true,2],[\"袁琴琴\",false,1]]|sort)" "$R"
check "没关联的那一行 key 是 n:<姓名>" '[.data.sheets[0].rows[]|select(.linked==false)|.key]==["n:袁琴琴"]' "$R"
check "石蜡包埋：包埋人一列有「袁琴琴（未关联）」1 块，操作人一列李工 2 块" \
  '.data.sheets[2]|(([.rows[]|select(.key=="n:袁琴琴")|.values]==[{"embedBy":1,"operator":0}]) and ([.rows[]|select(.key=="u:9000000101")|.values.operator]==[2]))' "$R"
check "-80 冻存：清空了冻存人的那一批归到「未填」，排在最后" '.data.sheets[3]|(.rows[-1].key=="none" and .rows[-1].values=={"batches":1,"tubes":3} and .totals=={"batches":8,"tubes":36})' "$R"

echo "S8 ★ 统计的每一个数 = 对应列表按「这个人 + 这段时间」筛出来的条数（工作台点数字跳过去看到的就是这些）"
recon() {  # $1 = 说明  $2 = 统计的时间段参数  $3..$5 = 三张列表各自的时间段参数
  local stat line sheet metric key value url got n=0 miss=0
  stat=$($API GET "/lqg/stat/operator?$2")
  while IFS=$'\t' read -r sheet metric key value; do
    case "$sheet/$metric" in
      tissue/count|organoid/count) url="/lqg/sample/list?sampleKind=$sheet&verifyStatus=valid&operatorKey=$key&$3" ;;
      embed/embedBy)  url="/lqg/embed/list?verifyStatus=valid&embedByKey=$key&$4" ;;
      embed/operator) url="/lqg/embed/list?verifyStatus=valid&operatorKey=$key&$4" ;;
      cryo/*)         url="/lqg/cryo/batch/list?frozenByKey=$key&$5" ;;
    esac
    if [ "$metric" = "tubes" ]; then
      got=$($API GET "$url&pageNum=1&pageSize=200" | jq '[.rows[]|.initQty]|add // 0')
    else
      got=$($API GET "$url&pageNum=1&pageSize=200" | jq '.total')
    fi
    n=$((n+1))
    if [ "$got" != "$value" ]; then miss=$((miss+1)); echo "    × $sheet/$metric $key：统计 $value，列表 $got"; fi
  done < <(echo "$stat" | jq -r '.data.sheets[] | .sheet as $s | .rows[] | (.key|@uri) as $k | .values | to_entries[] | [$s, .key, $k, (.value|tostring)] | @tsv')
  if [ "$miss" -eq 0 ] && [ "$n" -gt 0 ]; then ok "$1：$n 个数逐个对上"; else bad "$1" "对了 $((n-miss)) / $n"; fi
}
recon "全部时间" "" "" "" ""
recon "最近 10 天" "begin=$D10&end=$TODAY" "receiveDateBegin=$D10&receiveDateEnd=$TODAY" "paraffinEmbedTimeBegin=$D10&paraffinEmbedTimeEnd=$TODAY" "freezeTimeBegin=$D10&freezeTimeEnd=$TODAY"
recon "11 天前及之前（只给结束日期；列表那头补一个够远的开始日期）" "end=$D11" "receiveDateBegin=2000-01-01&receiveDateEnd=$D11" "paraffinEmbedTimeBegin=2000-01-01&paraffinEmbedTimeEnd=$D11" "freezeTimeBegin=2000-01-01&freezeTimeEnd=$D11"
check "按人筛的参数乱写 → 400（不静默当成不筛）" '.code==400' "$($API GET '/lqg/sample/list?operatorKey=abc&pageNum=1&pageSize=10')"
eq "小程序的四张表与导出也认同一个参数" "$($API --as staff GET "/mp/int/sample/list?sampleKind=tissue&operatorKey=u:$LIU&pageNum=1&pageSize=200" | jq .total)" 2

echo "S9 撤销授权之后：名单里没有她了，她做过的记录、统计里的名字都还在"
check "撤销刘佳的内部授权" '.code==200' "$($API DELETE "/lqg/auth/staff/$LIU")"
check "名单里没有刘佳了" '[.data[]|.name]|index("刘佳")==null' "$($API GET /lqg/staff/options)"
R=$($API GET /lqg/stat/operator)
check "统计里刘佳那一行还在，还是关联着账号的" '[.data.sheets[0].rows[]|select(.name=="刘佳")|[.linked,.values.count]]==[[true,2]]' "$R"
R=$($API PUT /lqg/sample "{\"id\":\"$S_OLD\",$BODY,\"internalNo\":\"$TAG-3\",\"operatorId\":\"$LIU\",\"operatorName\":\"刘佳\",\"remark\":\"撤销后改备注\"}")
check "★ 改她名下那条记录的别的字段（操作人原样带回来）：不因为「不在名单里」被拦住" '.code==200' "$R"
check "改完操作人还是刘佳" "(.data.operatorId|tostring)==\"$LIU\" and .data.remark==\"撤销后改备注\"" "$($API GET "/lqg/sample/$S_OLD")"
R=$($API PUT /lqg/sample "{\"id\":\"$S_DEF\",$BODY,\"internalNo\":\"$TAG-2\",\"operatorId\":\"$LIU\"}")
check "但不能再把她选给别的记录 → 400" '.code==400 and (.msg|test("操作人不在内部人员名单"))' "$R"

echo "S10 重名：两个内部人员叫同一个名字时，只带名字的不替用户猜是谁"
$API POST /lqg/auth/staff '{"phone":"13900002202","name":"李工","roleKey":"lqg_internal","password":"Test@12345"}' >/dev/null
S_DUP=$(mk POST /lqg/sample "{$BODY,\"internalNo\":\"$TAG-6\",\"operatorName\":\"李工\"}")
check "只带名字「李工」（现在有两个李工）：只存文字，不关联" '.data.operatorId==null and .data.operatorName=="李工"' "$($API GET "/lqg/sample/$S_DUP")"
$API PUT /lqg/sample "{\"id\":\"$S_DUP\",$BODY,\"internalNo\":\"$TAG-6\",\"operatorId\":$LI}" >/dev/null
check "带了账号 id 就没有重名的问题" ".data.operatorId==$LI" "$($API GET "/lqg/sample/$S_DUP")"

echo "S11 历史数据回填（迁移 V202610121000 再执行一遍）：姓名恰好对上一个人的才关联，不动最后修改时间"
sql "UPDATE t_lqg_sample SET operator_id = NULL WHERE id IN (9000001001, 9000001004);
     UPDATE t_lqg_cryo_batch SET frozen_by_id = NULL WHERE id = 9000003001;
     UPDATE t_lqg_embed SET embed_by_id = NULL, operator_id = NULL WHERE id = 9000002001;" >/dev/null
docker exec -i "$PG" psql -U lqg -d lqg_dev -q -v ON_ERROR_STOP=1 < "$MIG" >/dev/null 2>&1
eq "有两个李工的时候：一条都不关联（不猜）" "$(sql "select count(*) from t_lqg_sample where id in (9000001001,9000001004) and operator_id is not null")" 0
drop_test_staff
sql "UPDATE t_lqg_sample SET operator_name = '袁琴琴' WHERE id = 9000001004" >/dev/null
docker exec -i "$PG" psql -U lqg -d lqg_dev -q -v ON_ERROR_STOP=1 < "$MIG" >/dev/null 2>&1 || bad "迁移重复执行" "psql 报错"
eq "只剩一个李工：样本 1001 关联上了" "$(sql "select operator_id from t_lqg_sample where id=9000001001")" "$LI"
eq "手填成「袁琴琴」的 1004：对不上账号，id 留空、文字原样" "$(sql "select coalesce(operator_id::text,'null') || '/' || operator_name from t_lqg_sample where id=9000001004")" "null/袁琴琴"
eq "冻存人、包埋人、石蜡包埋的操作人一样回填" "$(sql "select (select frozen_by_id from t_lqg_cryo_batch where id=9000003001) || '/' || embed_by_id || '/' || operator_id from t_lqg_embed where id=9000002001")" "$LI/$LI/$LI"
eq "没动过的 seed 行的最后修改时间仍是空的（回填不算一次修改）" "$(sql "select count(*) from t_lqg_sample where id=9000001001 and update_time is null")" 1

echo "S12 不该变的：导出的列、对合作单位的字段"
TMP=$(mktemp -d)
$API --as staff --out "$TMP/t.xlsx" GET /mp/int/export/tissue
$API --as staff --out "$TMP/e.xlsx" GET /mp/int/export/embed
$API --as staff --out "$TMP/c.xlsx" GET /mp/int/export/cryo
R=$(python3 - "$TMP" <<'PY'
import sys,json,warnings
warnings.filterwarnings('ignore')
import openpyxl
out={}
for k in ('t','e','c'):
    head=[('' if c is None else str(c)) for c in next(openpyxl.load_workbook(f'{sys.argv[1]}/{k}.xlsx').active.iter_rows(values_only=True))]
    out[k]={'n':len(head),'people':[h for h in head if h in ('操作人','包埋人','冻存人')],'ids':[h for h in head if 'id' in h.lower() or '账号' in h]}
print(json.dumps(out,ensure_ascii=False))
PY
)
check "三张导出的表头列数没变（15 / 17 / 12），人名还是那几列，没有多出账号 id 列" \
  '.t.n==15 and .e.n==17 and .c.n==12 and .t.people==["操作人"] and .e.people==["包埋人","操作人"] and .c.people==["冻存人"] and ([.t.ids,.e.ids,.c.ids]|add)==[]' "$R"
rm -rf "$TMP"
R=$($API --as extA GET /mp/ext/sample/9000001001)
check "合作单位看到的样本详情、石蜡包埋卡片里没有账号 id 这些键" \
  '(.data|has("operatorId")|not) and ([.data.embeds[]|has("embedById") or has("operatorId")]|any|not) and (.data.embeds|length)>0' "$R"

echo "S13 工作台菜单"
R=$($API --as staff GET /system/menu/getRouters)
check "内部人员的菜单里有「操作人统计」（全员可见），排在冻存管理后面、人员与单位前面" \
  '[.data[]|(.meta.title // (.children[0].meta.title))] as $t | ($t|index("操作人统计"))!=null and ($t|index("操作人统计")) > ($t|index("冻存管理")) and ($t|index("操作人统计")) < ($t|index("人员与单位"))' "$R"

drop_test_staff
bash doc/verify/reseed.sh --yes >/dev/null 2>&1
eq "收尾：reseed 之后回到验收基线（样本 10 条、测试用的内部人员已删）" "$(sql "select (select count(*) from t_lqg_sample) || '/' || (select count(*) from sys_user where phonenumber in ('13900002201','13900002202'))")" "10/0"
echo "结果：通过 $PASS，失败 $FAIL"
[ "$FAIL" -eq 0 ]
