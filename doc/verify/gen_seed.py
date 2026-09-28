#!/usr/bin/env python3
"""gen_seed.py — 生成确定性的测试数据 doc/verify/seed/*.sql（REQ-SYS-010）。

为什么由需求层写 seed，而不是让实现方自己造：accept 的 DATA / API 断言必须打在
**实现方没写过的数据**上（自造数据查回来自证是假绿的头号来源）。这份 seed 先于实现存在，
断言里的期望值（谁能看到哪几条、哪几批超期、剩余几支、合计几分）全部从它推出来，见 README 的「seed 速查」。

特点：
  · 列名对着 doc/authority/field-ssot.yaml 校验——seed 写了 SSOT 里没有的列直接报错；
    反过来实现方建的表和 SSOT 对不上，seed 就灌不进去（这本身就是一道对账）。
  · 三个加密列（供体姓名 / 住院号 / 患者编号）按若依 @EncryptField 的 AES 口径预先算好密文：
    AES/ECB/PKCS5Padding，key = 口令的 UTF-8 字节，Base64 输出。**测试环境口令固定为下面的 TEST_KEY**，
    生产口令走环境变量、与此无关。
  · 每类数据都**故意埋了病灶**（软删的样本 / 石蜡块 / 冻存批次 / 流水，恰好第 14 天的批次，取空的批次，
    同组但未核验的人）——判据再狠，样本里没病，判据就是摆设。
  · 日期一律相对 CURRENT_DATE，超期类断言任何一天重灌都成立。
  · id 全部落在 9_000_000_000 段，送检单号 SJ9 开头，不与运行期雪花 id / 序列撞。

用法：python3 doc/verify/gen_seed.py            # 重写 doc/verify/seed/*.sql（按建表的 ticket 分段）
"""
from __future__ import annotations

import argparse
import base64
import sys
from pathlib import Path

import yaml
from cryptography.hazmat.primitives import padding
from cryptography.hazmat.primitives.ciphers import Cipher, algorithms, modes

# 只加 --help：放在生成动作之前，--help 时打印帮助并退出 0；
# 不带参数时直接往下走，生成行为与改动前完全一致。
if __name__ == "__main__":
    argparse.ArgumentParser(
        description=__doc__,
        formatter_class=argparse.RawDescriptionHelpFormatter,
    ).parse_args()

ROOT = Path(__file__).resolve().parents[2]
SSOT = ROOT / "doc" / "authority" / "field-ssot.yaml"
OUT_DIR = Path(__file__).resolve().parent / "seed"
TEST_KEY = "LqgTestAesKey#01"          # 16 位；只用于 dev / test（mybatis-encryptor.password）
ENCRYPTED = {("t_lqg_sample", "donor_name"), ("t_lqg_sample", "hospital_no"), ("t_lqg_qc_sample", "patient_no")}
# 若依自带 admin123 的 BCrypt（上游 seed 里公开的那一串），只用于测试管理员
ADMIN123 = "$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2"


def enc(plain: str) -> str:
    padder = padding.PKCS7(128).padder()
    data = padder.update(plain.encode("utf-8")) + padder.finalize()
    c = Cipher(algorithms.AES(TEST_KEY.encode("utf-8")), modes.ECB()).encryptor()
    return base64.b64encode(c.update(data) + c.finalize()).decode()


class Raw(str):
    """原样进 SQL 的表达式（CURRENT_DATE - 20 之类）。"""


# seed 行的创建时间一律放到过去。为什么：好几条 accept 用「create_time > now() - 5 分钟」去找**本次断言刚建出来的那一行**，
# seed 如果也是 now()，reseed 之后的五分钟里它们全都会混进来，断言对着正确的实现也会红。
PAST = Raw("(now() - interval '30 days')")


def lit(v) -> str:
    if v is None:
        return "NULL"
    if isinstance(v, Raw):
        return str(v)
    if isinstance(v, (int,)):
        return str(v)
    return "'" + str(v).replace("'", "''") + "'"


ssot = yaml.safe_load(SSOT.read_text(encoding="utf-8"))
COLS = {t["table"]: {f["name"] for f in t["fields"]} | {"create_by", "create_time", "del_flag"} for t in ssot["tables"]}
lines: list[str] = []
FILES: list[tuple[str, list[str], list[str]]] = []   # (文件名, 依赖的表, SQL 行)


def begin(fname: str, *requires: str):
    """开一个新分段。reseed.sh 只在 requires 里的表全部已建时才灌这一段——
    D1 还没有样本表，照样能灌账号与单位；表建到哪，seed 灌到哪。"""
    global lines
    lines = []
    FILES.append((fname, list(requires), lines))



def ins(table: str, **kv):
    if table in COLS:
        unknown = set(kv) - COLS[table]
        if unknown:
            sys.exit(f"seed 写了 SSOT 里没有的列：{table}.{sorted(unknown)}")
        for col in list(kv):
            if (table, col) in ENCRYPTED and kv[col] is not None:
                kv[col] = enc(kv[col])
        kv.setdefault("create_by", 9000000100)
        kv.setdefault("create_time", PAST)
    cols = ", ".join(kv)
    vals = ", ".join(lit(v) for v in kv.values())
    lines.append(f"INSERT INTO {table} ({cols}) VALUES ({vals});")


def d(n: int) -> Raw:
    return Raw(f"CURRENT_DATE - {n}")


def ts(n: int, hm: str = "10:00") -> Raw:
    return Raw(f"(CURRENT_DATE - {n} + TIME '{hm}')")


def section(title: str):
    lines.append(f"\n-- ── {title}")


# ═══════════ 角色与账号 ═══════════
begin("01-accounts.sql")
section("角色（正式环境由 AUTH-STAFF-001 的迁移建；这里 ON CONFLICT 兜底，方便单独灌 seed）")
for rid, name, key, sort in ((101, "实验室管理员", "lqg_admin", 11), (102, "内部人员", "lqg_internal", 12), (103, "外部人员", "lqg_external", 13)):
    lines.append("INSERT INTO sys_role (role_id, role_name, role_key, role_sort, data_scope, status, del_flag, create_time) "
                 f"VALUES ({rid}, '{name}', '{key}', {sort}, '1', '0', '0', now()) ON CONFLICT (role_id) DO NOTHING;")

USERS = [  # id, user_name, nick_name, user_type, phone, role, password
    (9000000100, "lqgadmin", "测试管理员", "sys_user", "13800000000", 101, ADMIN123),
    (9000000101, "lqg_13800000001", "李工", "sys_user", "13800000001", 102, ADMIN123),
    (9000000111, "wx_13800000011", "王医生", "app_user", "13800000011", 103, ""),
    (9000000112, "wx_13800000012", "陈医生", "app_user", "13800000012", 103, ADMIN123),   # 病灶：外部账号带着可用口令，也不许登上工作台
    (9000000113, "wx_13800000013", "赵医生", "app_user", "13800000013", 103, ""),
    (9000000114, "wx_13800000014", "孙老师", "app_user", "13800000014", 103, ""),
    (9000000115, "wx_13800000015", "周医生", "app_user", "13800000015", 103, ""),
    (9000000116, "wx_13800000016", "吴同学", "app_user", "13800000016", 103, ""),
]
CODE = {9000000101: "staff", 9000000111: "extA", 9000000112: "extB", 9000000113: "extC",
        9000000114: "extD", 9000000115: "extE", 9000000116: "extF"}
section("测试账号：1 管理员 + 1 内部 + 6 外部（手机号 138000000xx 是号段里的测试号，不是真人）")
for uid, uname, nick, utype, phone, role, pwd in USERS:
    ins("sys_user", user_id=uid, user_name=uname, nick_name=nick, user_type=utype, phonenumber=phone,
        password=pwd, status="0", del_flag="0", create_time=PAST)
    ins("sys_user_role", user_id=uid, role_id=role)
begin("02-wx-bind-ext-profile.sql", "t_lqg_wx_bind", "t_lqg_ext_profile")
section("微信绑定：mock 登录 xcxCode=mock:<身份> ↔ openid=mock-openid-<身份>")
for i, (uid, code) in enumerate(CODE.items()):
    phone = next(u[4] for u in USERS if u[0] == uid)
    ins("t_lqg_wx_bind", id=9000000200 + i, user_id=uid, openid=f"mock-openid-{code}", phone=phone, last_login_time=ts(1))

section("外部档案：A、B 同组已核验｜C 同单位异组｜D 异单位｜E 与 A 同组但【未核验】（病灶）｜F 自填单位待核验")
P = "t_lqg_ext_profile"
ins(P, id=9000000311, user_id=9000000111, real_name="王医生", unit_id=9000009001, group_id=9000009101, bind_status="verified", verified_by=9000000100, verified_time=ts(40))
ins(P, id=9000000312, user_id=9000000112, real_name="陈医生", unit_id=9000009001, group_id=9000009101, bind_status="verified", verified_by=9000000100, verified_time=ts(40))
ins(P, id=9000000313, user_id=9000000113, real_name="赵医生", unit_id=9000009001, group_id=9000009102, bind_status="verified", verified_by=9000000100, verified_time=ts(40))
ins(P, id=9000000314, user_id=9000000114, real_name="孙老师", unit_id=9000009002, group_id=9000009103, bind_status="verified", verified_by=9000000100, verified_time=ts(40))
ins(P, id=9000000315, user_id=9000000115, real_name="周医生", unit_id=9000009001, group_id=9000009101, bind_status="pending")
ins(P, id=9000000316, user_id=9000000116, real_name="吴同学", unit_name_input="C 研究所", group_name_input="肿瘤组", bind_status="pending")


# ═══════════ 单位、组别、外部档案 ═══════════
begin("03-unit-group.sql", "t_lqg_source_unit", "t_lqg_unit_group")
section("来源单位与组别")
ins("t_lqg_source_unit", id=9000009001, unit_name="A 医院", unit_status="active")
ins("t_lqg_source_unit", id=9000009002, unit_name="B 大学", unit_status="active")
ins("t_lqg_source_unit", id=9000009003, unit_name="已停用单位", unit_status="disabled")
ins("t_lqg_unit_group", id=9000009101, unit_id=9000009001, group_name="肝胆外科组", group_status="active")
ins("t_lqg_unit_group", id=9000009102, unit_id=9000009001, group_name="消化内科组", group_status="active")
ins("t_lqg_unit_group", id=9000009103, unit_id=9000009002, group_name="类器官课题组", group_status="active")
# ═══════════ 样本 ═══════════
begin("04-sample.sql", "t_lqg_sample")
section("样本：A1/A2/A3（extA：有效 / 待核验 / 无效）｜B1（同组）｜C1（异组）｜D1（异单位）｜E1（未核验同组）｜I1/I2（内部 组织 / 类器官）｜X1（软删，病灶）。今天创建的 = A2、E1 + 软删的 X1 → 今日新增期望 2")
S = "t_lqg_sample"


def today(sec: int) -> Raw:
    """今天零点过 sec 秒。用零点而不是 now()：既算「今日新增」，又不会落进「刚刚创建」的五分钟窗。"""
    return Raw(f"(CURRENT_DATE + TIME '00:00:{sec:02d}')")


def sample(sid, no, kind, source, submitter, status, unit_id, unit_name, created, **kw):
    ins(S, id=sid, submit_no=no, sample_kind=kind, submit_source=source, submitter_id=submitter,
        verify_status=status, source_unit_id=unit_id, source_unit_name=unit_name, create_by=submitter, create_time=created, **kw)


recv = dict(is_fixed="Y", has_qc_sheet="Y", has_viability_report="Y", operator_name="李工", verify_by=9000000101)
sample(9000001001, "SJ90000001", "tissue", "external", 9000000111, "valid", 9000009001, "A 医院", ts(31), donor_name="测试供体甲", gender="male", age="56",
       hospital_no="ZY0000001", tissue_type="肝组织", has_pathology="Y", receive_date=d(30), internal_no="T-hli01", process_time=ts(30, "14:20"), verify_time=ts(30), **recv)
sample(9000001002, "SJ90000002", "tissue", "external", 9000000111, "pending", 9000009001, "A 医院", today(1), donor_name="测试供体乙", gender="female", age="48",
       hospital_no="ZY0000002", tissue_type="胆管组织", has_pathology="N")
sample(9000001003, "SJ90000003", "tissue", "external", 9000000111, "invalid", 9000009001, "A 医院", ts(9), donor_name="测试供体丙", gender="unknown",
       tissue_type="肝组织", invalid_reason="信息不全：缺住院号", verify_by=9000000101, verify_time=ts(8))
sample(9000001004, "SJ90000004", "tissue", "external", 9000000112, "valid", 9000009001, "A 医院", ts(26), donor_name="测试供体丁", gender="male", age="61",
       hospital_no="ZY0000004", tissue_type="肝组织", receive_date=d(25), internal_no="T-hli02", process_time=ts(25, "09:40"), verify_time=ts(25), **recv)
sample(9000001005, "SJ90000005", "tissue", "external", 9000000113, "valid", 9000009001, "A 医院", ts(21), donor_name="测试供体戊", gender="female", age="39",
       hospital_no="ZY0000005", tissue_type="胃组织", receive_date=d(20), internal_no="T-hga03", process_time=ts(20), verify_time=ts(20), **recv)
sample(9000001006, "SJ90000006", "tissue", "external", 9000000114, "valid", 9000009002, "B 大学", ts(19), donor_name="测试供体己", gender="male", age="70",
       hospital_no="ZY0000006", tissue_type="结直肠组织", receive_date=d(18), internal_no="T-hco04", process_time=ts(18), verify_time=ts(18), **recv)
sample(9000001007, "SJ90000007", "tissue", "external", 9000000115, "pending", 9000009001, "A 医院", today(2), donor_name="测试供体庚", gender="male", age="52",
       hospital_no="ZY0000007", tissue_type="肝组织")
sample(9000001008, "SJ90000008", "tissue", "internal", 9000000101, "valid", None, "本中心", ts(45), donor_name="测试供体辛", gender="female", age="45",
       hospital_no="ZY0000008", tissue_type="肝组织", receive_date=d(45), internal_no="T-hli05", process_time=ts(45), verify_time=ts(45), **recv)
# 代数 passage（CR-20260924-10，类器官收样记录在「类器官类型」后加的一项）：唯一的类器官样本带上 P3，H5 / 工作台 / 导出里 T-oco01 都能看到带值的代数
sample(9000001009, "SJ90000009", "organoid", "internal", 9000000101, "valid", 9000009002, "B 大学", ts(60), organoid_type="结直肠类器官", passage="P3",
       receive_date=d(60), internal_no="T-oco01", process_time=ts(60), has_viability_report="Y", operator_name="李工", verify_by=9000000101, verify_time=ts(60))
sample(9000001010, "SJ90000010", "tissue", "external", 9000000111, "valid", 9000009001, "A 医院", today(3), donor_name="已删除供体", gender="male",
       tissue_type="肝组织", receive_date=d(10), internal_no="T-del99", del_flag="1")

# ═══════════ 石蜡包埋 ═══════════
begin("05-embed.sql", "t_lqg_sample", "t_lqg_embed", "t_lqg_embed_marker")
section("石蜡包埋：A1 两块（一块已切片 HE+IHC、一块未切片）｜B1 无染色｜D1 其他染色｜I1 一块【软删】（病灶：不得计入切片染色提示）"
        "｜A2 extA 提交的送样【待核验、没有石蜡块编号，所挂样本也待核验】（病灶：判有效必须被拒；不算一块石蜡；外部看得到）。"
        "李工（staff）建的 = 2001、2003 + 软删的 2005 → 内部历史编辑记录期望 {2001, 2003}")
E = "t_lqg_embed"
INT_BY_STAFF = dict(submit_source="internal", submitter_id=9000000101, verify_status="valid", verify_by=9000000101, create_by=9000000101)
INT_BY_ADMIN = dict(submit_source="internal", submitter_id=9000000100, verify_status="valid", verify_by=9000000100)
ins(E, id=9000002001, sample_id=9000001001, paraffin_block_no="T-E01-1", sample_type="组织", tissue_receive_time=d(30), tissue_process_time=d(30),
    agarose_embed_time=d(29), embed_by="李工", dehydrate_time=d(28), agarose_send_time=d(28), paraffin_embed_time=d(27), section_time=d(26),
    stain_types="HE,IHC", operator_name="李工", **INT_BY_STAFF)
ins(E, id=9000002002, sample_id=9000001001, paraffin_block_no="T-E01-2", sample_type="类器官", organoid_source_type="肝类器官", agarose_embed_time=d(12), **INT_BY_ADMIN)
ins(E, id=9000002003, sample_id=9000001004, paraffin_block_no="T-E02-1", sample_type="组织", paraffin_embed_time=d(20), section_time=d(19), stain_types="NONE", **INT_BY_STAFF)
ins(E, id=9000002004, sample_id=9000001006, paraffin_block_no="T-E04-1", sample_type="组织", section_time=d(10), stain_types="OTHER", stain_other="Masson", **INT_BY_ADMIN)
ins(E, id=9000002005, sample_id=9000001008, paraffin_block_no="T-E05-X", sample_type="组织", section_time=d(40), stain_types="HE,IF", del_flag="1", **INT_BY_STAFF)
ins(E, id=9000002006, sample_id=9000001002, paraffin_block_no=None, sample_type="组织", submit_source="external", submitter_id=9000000111,
    verify_status="pending", create_by=9000000111, create_time=today(4))
M = "t_lqg_embed_marker"
ins(M, id=9000002101, embed_id=9000002001, marker_name="Ki67", expression="strong", sort=1)
ins(M, id=9000002102, embed_id=9000002001, marker_name="CK19", expression="negative", sort=2)
ins(M, id=9000002103, embed_id=9000002004, marker_name=None, expression="weak", sort=1)

# ═══════════ 冻存 ═══════════
begin("06-cryo.sql", "t_lqg_sample", "t_lqg_cryo_batch", "t_lqg_cryo_flow")
section("冻存批次。期望：超期 = {3001, 3005}；剩余 3001→6 3002→4 3003→4 3004→0 3005→2 3006→5 3007→5；李工（staff）建的 = 3001、3003 + 软删的 3008 → 内部历史编辑记录期望 {3001, 3003}")
B = "t_lqg_cryo_batch"
ins(B, id=9000003001, sample_id=9000001001, cryo_name="T-hli01-GZ-N-P2-EM2-2e5", passage="P2", freeze_time=d(20), init_qty=8, density="2e5", in_minus80="Y", frozen_by="李工",
    create_by=9000000101)
ins(B, id=9000003002, sample_id=9000001009, cryo_name="T-oco01-JC-T-P3-EM1-5e5", passage="P3", freeze_time=d(5), init_qty=4, density="5e5", in_minus80="Y", frozen_by="李工")
ins(B, id=9000003003, sample_id=9000001008, cryo_name="T-hli05-GZ-N-P7-EM2-2e5", passage="P7", freeze_time=d(40), init_qty=6, density="2e5", in_minus80="Y", frozen_by="李工",
    to_ln2_time=d(30), ln2_location="2号罐-3架-B5", create_by=9000000101)
ins(B, id=9000003004, sample_id=9000001001, cryo_name="T-hli01-GZ-N-P5-EM2-1e5", passage="P5", freeze_time=d(14), init_qty=3, density="1e5", in_minus80="Y", frozen_by="李工",
    remark="病灶：满 14 天但已取空 → 不算超期")
ins(B, id=9000003005, sample_id=9000001004, cryo_name="T-hli02-GZ-N-P2-EM2-2e5", passage="P2", freeze_time=d(14), init_qty=2, density="2e5", in_minus80="Y", frozen_by="李工",
    remark="病灶：恰好第 14 天 → 算超期（已超 0 天）")
ins(B, id=9000003006, sample_id=9000001008, cryo_name="T-hli05-GZ-N-P8-EM2-2e5", passage="P8", freeze_time=d(13), init_qty=5, density="2e5", in_minus80="Y", frozen_by="李工",
    remark="病灶：第 13 天 → 不算超期")
ins(B, id=9000003007, sample_id=9000001009, cryo_name="T-oco01-JC-T-P4-EM1-5e5", passage="P4", freeze_time=d(60), init_qty=5, density="5e5", in_minus80="N", frozen_by="李工",
    ln2_location="1号罐-1架-A2", remark="病灶：直接进液氮 → 永不超期")
ins(B, id=9000003008, sample_id=9000001008, cryo_name="T-hli05-DEL", passage="P1", freeze_time=d(50), init_qty=9, in_minus80="Y", del_flag="1", create_by=9000000101)
F = "t_lqg_cryo_flow"
ins(F, id=9000003101, batch_id=9000003001, flow_type="take", delta=-2, from_location="minus80", operator_name="李工", flow_time=ts(6), purpose="复苏培养")
ins(F, id=9000003102, batch_id=9000003003, flow_type="take", delta=-1, from_location="minus80", operator_name="李工", flow_time=ts(35), purpose="复苏培养")
ins(F, id=9000003103, batch_id=9000003003, flow_type="add", delta=2, from_location="ln2", operator_name="李工", flow_time=ts(20), purpose="同批补冻")
ins(F, id=9000003104, batch_id=9000003003, flow_type="take", delta=-3, from_location="ln2", operator_name="李工", flow_time=ts(10), purpose="药敏实验")
ins(F, id=9000003105, batch_id=9000003004, flow_type="take", delta=-3, from_location="minus80", operator_name="李工", flow_time=ts(3), purpose="全部取用")
ins(F, id=9000003106, batch_id=9000003002, flow_type="take", delta=-1, from_location="minus80", operator_name="李工", flow_time=ts(2), purpose="病灶：软删流水，不得计入", del_flag="1")

# ═══════════ 质控文档 ═══════════
begin("07-qc-docs.sql", "t_lqg_sample", "t_lqg_qc_sample", "t_lqg_qc_organoid", "t_lqg_qc_score", "t_lqg_doc_image", "t_lqg_doc_attachment")
section("质控文档。A1 三份已完成（评分 20+10+25+30=85）｜B1 质控表已完成 + 类器官质控表【草稿】（病灶：草稿不得对外）｜C1 只有草稿｜D1 评分已完成 8+0+0+10=18")
for i in range(1, 7):
    ins("sys_oss", oss_id=9000004000 + i, file_name=f"seed/lqg/img-{i}.jpg", original_name=f"测试图片{i}.jpg", file_suffix=".jpg",
        url=f"https://seed.invalid/lqg/img-{i}.jpg", service="seed", create_time=PAST)
ins("sys_oss", oss_id=9000004011, file_name="seed/lqg/viability.pdf", original_name="活率报告.pdf", file_suffix=".pdf",
    url="https://seed.invalid/lqg/viability.pdf", service="seed", create_time=PAST)
Q = "t_lqg_qc_sample"
ins(Q, id=9000005001, sample_id=9000001001, patient_no="P-0001", sampling_site="肝右叶", sampling_method="手术切除", clinical_diagnosis="测试诊断",
    receive_desc="样本按质控要求，保持2-8℃低温环境运输至实验室。", viability_oss_id=9000004011, viability_file_name="活率报告.pdf",
    orig_desc="组织块约 0.8cm。", observe_desc="样本外观呈黄白色。", pretreat_desc="样本经剪切等预处理。", doc_status="published", published_time=ts(22), published_by=9000000101)
ins(Q, id=9000005002, sample_id=9000001004, patient_no="P-0004", sampling_site="肝左叶", doc_status="published", published_time=ts(15), published_by=9000000101)
ins(Q, id=9000005003, sample_id=9000001005, patient_no="P-0005", doc_status="draft")
O = "t_lqg_qc_organoid"
ins(O, id=9000005101, sample_id=9000001001, formed_time="第 5 天", growth_state="良好", growth_desc="类器官成球规则。", planned_drug_screen="索拉非尼、仑伐替尼",
    feedback_time="2026-10-15", doc_status="published", published_time=ts(12), published_by=9000000101)
ins(O, id=9000005102, sample_id=9000001004, growth_state="一般", doc_status="draft")
C = "t_lqg_qc_score"
ins(C, id=9000005201, sample_id=9000001001, pre_culture_level="gt80", culture_days_level="le14", organoid_count_level="1500to4000", diameter_level="gt100",
    pre_culture_score=20, culture_days_score=10, organoid_count_score=25, diameter_score=30, total_score=85, doc_status="published", published_time=ts(9), published_by=9000000101)
ins(C, id=9000005202, sample_id=9000001006, pre_culture_level="lt40", culture_days_level="gt14", organoid_count_level="lt100", diameter_level="lt30",
    pre_culture_score=8, culture_days_score=0, organoid_count_score=0, diameter_score=10, total_score=18, doc_status="published", published_time=ts(4), published_by=9000000101)
I = "t_lqg_doc_image"
ins(I, id=9000005301, doc_type="sample_qc", doc_id=9000005001, slot="orig", oss_id=9000004001, preview_oss_id=9000004001, sort=1)
ins(I, id=9000005302, doc_type="sample_qc", doc_id=9000005001, slot="orig", oss_id=9000004002, preview_oss_id=9000004002, sort=2)
ins(I, id=9000005303, doc_type="sample_qc", doc_id=9000005001, slot="observe", oss_id=9000004003, preview_oss_id=9000004003, sort=1)
ins(I, id=9000005304, doc_type="organoid_qc", doc_id=9000005101, slot="organoid_observe", oss_id=9000004004, preview_oss_id=9000004004, sort=1)
ins("t_lqg_doc_attachment", id=9000005401, doc_type="sample_qc", doc_id=9000005001, oss_id=9000004011, file_name="活率报告.pdf", file_size=319488, sort=1)

OUT_DIR.mkdir(exist_ok=True)
for old in OUT_DIR.glob("*.sql"):
    old.unlink()
total = 0
for fname, requires, body in FILES:
    head = (f"-- doc/verify/seed/{fname} —— 由 gen_seed.py 生成，别手改（改 gen_seed.py 重新生成）\n"
            f"-- requires: {' '.join(requires)}\n"
            f"-- 只许灌进 dev / test 库（reseed.sh 拒绝对名字不含 dev / test 的库动手）。\n"
            f"-- 加密列用测试口令 {TEST_KEY} 预先算好密文（后端 dev / test 的 mybatis-encryptor.password 必须是它）。\n"
            "BEGIN;\n")
    (OUT_DIR / fname).write_text(head + "\n".join(body) + "\n\nCOMMIT;\n", encoding="utf-8")
    total += sum(1 for x in body if x.startswith("INSERT"))
print(f"wrote {len(FILES)} 个分段到 {OUT_DIR.relative_to(ROOT)}/ · 共 {total} 条 INSERT")
