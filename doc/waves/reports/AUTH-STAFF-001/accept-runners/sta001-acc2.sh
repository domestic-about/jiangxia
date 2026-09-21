#!/usr/bin/env bash
# AUTH-STAFF-001 · accept 2 原文逐字（唯一改动：去掉 `--fresh-module ruoyi-lqg`，沙箱 ps 被禁）
# 整体退出码判定，理由同 sta001-acc1.sh（`set -e` 管不住非末尾的 && 失败）。
set -e
acc2() {
bash doc/verify/reseed.sh --yes >/dev/null &&
PC="$(sed -n 's/^LQG_CLIENT_PC=//p' doc/verify/verify.env)" && BASE="$(sed -n 's/^LQG_API_BASE=//p' doc/verify/verify.env)" &&
login() { curl -s -X POST "${BASE}/auth/login" -H 'Content-Type: application/json' -H "clientid: ${PC}" -d "$(jq -nc --arg c "${PC}" --arg u "$1" '{clientId:$c,grantType:"password",tenantId:"000000",username:$u,password:"admin123"}')"; } &&
login lqg_13800000001 | jq -e '.code==200 and (.data.access_token|length>10)' &&
python3 doc/verify/db.py --sql "SELECT count(*) FROM sys_user WHERE user_name='wx_13800000012' AND length(password) = 60" --eq 1 &&
login wx_13800000012 | jq -e '.code!=200 and ((.data.access_token // "") == "") and (.msg|test("无权"))' &&
bash doc/verify/api.sh --as admin --bizcode PUT /lqg/auth/staff/9000000111/reset-pwd '{"password":"Lqg@test123"}' | grep -qE '^(400|403|500)' &&
python3 doc/verify/db.py --sql "SELECT length(password) FROM sys_user WHERE user_id=9000000111" --eq 0
}
if acc2; then echo "ACCEPT-2 EXIT=0"; else rc=$?; echo "ACCEPT-2 FAILED rc=$rc"; exit 1; fi
