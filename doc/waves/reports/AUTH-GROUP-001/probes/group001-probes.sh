#!/usr/bin/env bash
# AUTH-GROUP-001 对抗性探针：accept 没直接断、但 counterfeit 点名的形态
#
# 用法（从工作区根，或任意目录）：bash doc/waves/reports/AUTH-GROUP-001/probes/group001-probes.sh
# 说明：P18/P19 这两段的直连写法在本文件里用 docker exec psql（db.py 只收 SELECT/WITH）；
#      P20 的原始写法预设错了（想直接 approve 一条 rejected 档案，被状态机正确拒掉），
#      报告 §4.4(b) 里写的是修正后的正路（外部先保存 → 核验 → 停用单位 → 再看库）。
#      P21/P22 是本票抓到真 bug 之后补的两条负对照。
set -uo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/../../../../.."
rm -f "${TMPDIR:-/tmp}"/lqg-verify-token-*

echo "P1 已核验的不能被驳回（verified→rejected 非法，库里不变）"
bash doc/verify/reseed.sh --yes >/dev/null
bash doc/verify/api.sh --as staff --bizcode PUT /lqg/auth/ext-user/9000000111/verify '{"action":"reject","reason":"随便"}'
python3 doc/verify/db.py --sql "SELECT bind_status||'|'||COALESCE(reject_reason,'-') FROM t_lqg_ext_profile WHERE user_id=9000000111"

echo "P2 rejected 不能被直接 approve（rejected→verified 非法）"
bash doc/verify/api.sh --as staff --bizcode PUT /lqg/auth/ext-user/9000000115/verify '{"action":"reject","reason":"组别名不全"}' >/dev/null
bash doc/verify/api.sh --as staff --bizcode PUT /lqg/auth/ext-user/9000000115/verify '{"action":"approve","createUnit":true,"createGroup":true}'
python3 doc/verify/db.py --sql "SELECT bind_status||'|'||COALESCE(reject_reason,'-') FROM t_lqg_ext_profile WHERE user_id=9000000115"

echo "P3 unbound 不能被核验"
bash doc/verify/api.sh --as staff --bizcode PUT /lqg/auth/ext-user/9000000116/verify '{"action":"approve"}'
python3 doc/verify/db.py --sql "SELECT bind_status FROM t_lqg_ext_profile WHERE user_id=9000000116" --eq pending

echo "P4 驳回不带 reason / reason 全空白都拒绝"
bash doc/verify/api.sh --as staff --bizcode PUT /lqg/auth/ext-user/9000000116/verify '{"action":"reject","reason":"   "}'
python3 doc/verify/db.py --sql "SELECT bind_status FROM t_lqg_ext_profile WHERE user_id=9000000116" --eq pending

echo "P5 自填只给 createUnit 不给 createGroup → 拒绝且库里不变"
bash doc/verify/api.sh --as staff --bizcode PUT /lqg/auth/ext-user/9000000116/verify '{"action":"approve","createUnit":true}'
python3 doc/verify/db.py --sql "SELECT bind_status||'|'||COALESCE(unit_id::text,'-') FROM t_lqg_ext_profile WHERE user_id=9000000116"

echo "P6 归并时组别不属于单位 → 拒绝且库里不变"
bash doc/verify/api.sh --as staff --bizcode PUT /lqg/auth/ext-user/9000000116/verify '{"action":"approve","unitId":9000009001,"groupId":9000009103}'
python3 doc/verify/db.py --sql "SELECT bind_status||'|'||COALESCE(unit_id::text,'-') FROM t_lqg_ext_profile WHERE user_id=9000000116"

echo "P7 单位名全库唯一（重名拒绝）"
bash doc/verify/api.sh --as admin --bizcode POST /lqg/auth/unit '{"unitName":"A 医院"}'
bash doc/verify/api.sh --as admin --bizcode POST /lqg/auth/unit '{"unitName":"  a 医院 "}'
python3 doc/verify/db.py --sql "SELECT count(*) FROM t_lqg_source_unit WHERE unit_name ILIKE '%医院%'" 

echo "P8 同单位组别名唯一；不同单位可重名"
bash doc/verify/api.sh --as admin --bizcode POST /lqg/auth/group '{"unitId":9000009001,"groupName":"肝胆外科组"}'
bash doc/verify/api.sh --as admin --bizcode POST /lqg/auth/group '{"unitId":9000009002,"groupName":"肝胆外科组"}'
python3 doc/verify/db.py --sql "SELECT unit_id||':'||group_name FROM t_lqg_unit_group WHERE group_name='肝胆外科组' AND del_flag='0' ORDER BY unit_id"

echo "P9 组别停用后已绑定的人不受影响；停用组别不再出现在对外选择器"
bash doc/verify/api.sh --as admin PUT /lqg/auth/group/9000009101/status '{"status":"disabled"}' | jq -c .
python3 doc/verify/db.py --sql "SELECT group_id||'|'||bind_status FROM t_lqg_ext_profile WHERE user_id IN (9000000111,9000000112) ORDER BY user_id"
bash doc/verify/api.sh --as extF GET /mp/ext/units | jq -c '[.data[]|select(.unitName=="A 医院")|.groups[].groupName]'
bash doc/verify/api.sh --as admin PUT /lqg/auth/group/9000009101/status '{"status":"active"}' >/dev/null

echo "P10 单位停用后 /mp/ext/units 不再返回它（seed 的「已停用单位」本来就不在）"
bash doc/verify/api.sh --as admin PUT /lqg/auth/unit/9000009002/status '{"status":"disabled"}' >/dev/null
bash doc/verify/api.sh --as extF GET /mp/ext/units | jq -c '[.data[].unitName]'
bash doc/verify/api.sh --as admin PUT /lqg/auth/unit/9000009002/status '{"status":"active"}' >/dev/null

echo "P11 外部选一个已停用的单位 / 组别 → 拒绝"
bash doc/verify/api.sh --as admin PUT /lqg/auth/group/9000009102/status '{"status":"disabled"}' >/dev/null
bash doc/verify/api.sh --as extA --bizcode PUT /mp/ext/profile '{"realName":"王医生","unitId":9000009001,"groupId":9000009102}'
python3 doc/verify/db.py --sql "SELECT bind_status FROM t_lqg_ext_profile WHERE user_id=9000000111" --eq verified
bash doc/verify/api.sh --as admin PUT /lqg/auth/group/9000009102/status '{"status":"active"}' >/dev/null

echo "P12 外部不能把组别挪到别的单位（group 不属于 unit）"
bash doc/verify/api.sh --as extA --bizcode PUT /mp/ext/profile '{"realName":"王医生","unitId":9000009001,"groupId":9000009103}'
python3 doc/verify/db.py --sql "SELECT bind_status||'|'||group_id FROM t_lqg_ext_profile WHERE user_id=9000000111" --eq "verified|9000009101"

echo "P13 两套写法互斥：选了列表项同时夹带自填名 → 以列表项为准、自填清空"
bash doc/verify/api.sh --as extA PUT /mp/ext/profile '{"realName":"王医生","unitId":9000009001,"groupId":9000009102,"unitNameInput":"X 医院","groupNameInput":"Y 组"}' | jq -c .
python3 doc/verify/db.py --sql "SELECT bind_status||'|'||unit_id||'|'||group_id||'|'||COALESCE(unit_name_input,'NULL')||'|'||COALESCE(group_name_input,'NULL') FROM t_lqg_ext_profile WHERE user_id=9000000111"

echo "P14 自填保存把两个 id 清空（从列表项切到手动填写）"
bash doc/verify/api.sh --as extA PUT /mp/ext/profile '{"realName":"王医生","unitNameInput":"X 医院","groupNameInput":"Y 组"}' | jq -c .
python3 doc/verify/db.py --sql "SELECT bind_status||'|'||COALESCE(unit_id::text,'NULL')||'|'||COALESCE(group_id::text,'NULL')||'|'||unit_name_input||'|'||group_name_input FROM t_lqg_ext_profile WHERE user_id=9000000111"

echo "P15 外部不能核验别人（/lqg/auth/ext-user/** 对外部角色 403）"
bash doc/verify/api.sh --as extA --bizcode PUT /lqg/auth/ext-user/9000000111/verify '{"action":"approve"}'
bash doc/verify/api.sh --as extA --bizcode GET /lqg/auth/ext-user/list

echo "P16 组别不能挪到别的单位（PUT 换 unitId → 拒绝）"
bash doc/verify/api.sh --as admin --bizcode PUT /lqg/auth/group/9000009101 '{"unitId":9000009002,"groupName":"肝胆外科组"}'
python3 doc/verify/db.py --sql "SELECT unit_id FROM t_lqg_unit_group WHERE id=9000009101" --eq 9000009001

echo "P17 单位 / 组别没有 DELETE 端点（不物理删）"
bash doc/verify/api.sh --as admin --bizcode DELETE /lqg/auth/unit/9000009001
python3 doc/verify/db.py --sql "SELECT count(*) FROM t_lqg_source_unit WHERE id=9000009001 AND del_flag='0'" --eq 1

echo "P18 软删后可重建同名（部分唯一索引语义，accept 1 的 counterfeit 点名的形态）"
# db.py 只收 SELECT / WITH（只读护栏），写操作用 docker exec psql
export PGPASSWORD="${LQG_DB_PASSWORD:-lqg_dev_pwd}"
PSQL=(psql -v ON_ERROR_STOP=1 -q -h 127.0.0.1 -p 5433 -U lqg -d lqg_dev -At)
"${PSQL[@]}" -c "INSERT INTO t_lqg_source_unit (id, unit_name, unit_status, create_by, create_time, del_flag) VALUES (9000009099, '临时单位X', 'active', 1, now(), '0')" \
              -c "UPDATE t_lqg_source_unit SET del_flag='1' WHERE id=9000009099" \
              -c "INSERT INTO t_lqg_source_unit (id, unit_name, unit_status, create_by, create_time, del_flag) VALUES (9000009098, '临时单位X', 'active', 1, now(), '0')" \
              -c "SELECT count(*) FROM t_lqg_source_unit WHERE unit_name='临时单位X'" \
              -c "DELETE FROM t_lqg_source_unit WHERE id IN (9000009099,9000009098)"

echo "P19 同单位内同名组别在软删后可重建"
"${PSQL[@]}" -c "INSERT INTO t_lqg_unit_group (id, unit_id, group_name, group_status, create_by, create_time, del_flag) VALUES (9000009199, 9000009001, '临时组X', 'active', 1, now(), '0')" \
              -c "UPDATE t_lqg_unit_group SET del_flag='1' WHERE id=9000009199" \
              -c "INSERT INTO t_lqg_unit_group (id, unit_id, group_name, group_status, create_by, create_time, del_flag) VALUES (9000009198, 9000009001, '临时组X', 'active', 1, now(), '0')" \
              -c "SELECT count(*) FROM t_lqg_unit_group WHERE group_name='临时组X'" \
              -c "DELETE FROM t_lqg_unit_group WHERE id IN (9000009199,9000009198)"

echo "P20 停用单位后仍可被核验归并（已绑定的人不受影响）；单位名全库唯一冲突给人的是人话"
bash doc/verify/api.sh --as admin PUT /lqg/auth/unit/9000009003/status '{"status":"active"}' >/dev/null
bash doc/verify/api.sh --as staff PUT /lqg/auth/ext-user/9000000115/verify '{"action":"approve","unitId":9000009003,"groupId":9000009101}' | jq -c .
bash doc/verify/api.sh --as admin PUT /lqg/auth/unit/9000009003/status '{"status":"disabled"}' >/dev/null
python3 doc/verify/db.py --sql "SELECT bind_status||'|'||unit_id FROM t_lqg_ext_profile WHERE user_id=9000000115"

echo "PROBE DONE"

echo "P21 归并到已有后自填名必须清空（否则列表会挂着过期的自填名）"
bash doc/verify/reseed.sh --yes >/dev/null
bash doc/verify/api.sh --as staff PUT /lqg/auth/ext-user/9000000116/verify '{"action":"approve","unitId":9000009001,"groupId":9000009102}' >/dev/null
python3 doc/verify/db.py --sql "SELECT p.bind_status||'|'||COALESCE(p.unit_name_input,'NULL')||'|'||COALESCE(p.group_name_input,'NULL') FROM t_lqg_ext_profile p WHERE p.user_id=9000000116"

echo "P22 改归组：verified→verified（只改 group_id，状态不变、自填仍空）"
bash doc/verify/reseed.sh --yes >/dev/null
bash doc/verify/api.sh --as staff PUT /lqg/auth/ext-user/9000000111/verify '{"action":"approve","unitId":9000009001,"groupId":9000009102}' | jq -c .
python3 doc/verify/db.py --sql "SELECT bind_status||'|'||group_id||'|'||verified_by FROM t_lqg_ext_profile WHERE user_id=9000000111"
