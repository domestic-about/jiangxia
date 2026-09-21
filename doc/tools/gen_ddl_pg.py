#!/usr/bin/env python3
"""gen_ddl_pg.py — 从 doc/authority/field-ssot.yaml 生成 PostgreSQL 建表 DDL（ADR-0009）。

为什么不用 skill 自带的 ssot_gen.py + 栈包 renderer：那份 renderer 是 MySQL / Flyway 方言
（反引号、ENGINE=InnoDB、del_unique 收尾的 UNIQUE KEY），而本项目是 PostgreSQL。
这里是同一份契约的 PG 实现：**单向**（只 SSOT → DDL）、公共字段自动附加、唯一性一律
生成部分唯一索引（… WHERE del_flag = '0'）。

用法（cwd = 项目根）：
  python3 doc/tools/gen_ddl_pg.py                         # 全部表 → stdout
  python3 doc/tools/gen_ddl_pg.py --table t_lqg_sample    # 只出一张（可重复传）
  python3 doc/tools/gen_ddl_pg.py --migration V202609221000__SAMPLE-MODEL-001-sample.sql
                                                          # 出某个迁移文件该含的全部表
  python3 doc/tools/gen_ddl_pg.py --dicts                 # 出全部 lqg_* 字典的 seed（sys_dict_type / sys_dict_data）
  python3 doc/tools/gen_ddl_pg.py --check                 # 只校验 SSOT，不输出 DDL
退出码：0 成功 / 1 SSOT 校验不过（消息进 stderr）。

迁移文件铁律：已应用的迁移终身不改。SSOT 改了字段 → 新写一支 ALTER 迁移，
不要拿本脚本的输出去覆盖旧的 CREATE 迁移。
"""
from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path

import yaml

ROOT = Path(__file__).resolve().parents[2]
SSOT = ROOT / "doc" / "authority" / "field-ssot.yaml"
NEEDS_LENGTH = {"str", "dict"}
IDENT = re.compile(r"^[a-z][a-z0-9_]{0,62}$")


def q(s: str) -> str:
    return "'" + str(s).replace("'", "''") + "'"


def validate(data: dict) -> list[str]:
    errs: list[str] = []
    tmap, blocks, dicts = data.get("type_map") or {}, data.get("common_field_blocks") or {}, data.get("dicts") or {}
    seen_tables: set[str] = set()
    for t in data.get("tables") or []:
        name = t.get("table", "")
        if not IDENT.match(name):
            errs.append(f"表名不合法: {name!r}")
        if name in seen_tables:
            errs.append(f"表重复: {name}")
        seen_tables.add(name)
        for b in t.get("common_fields") or []:
            if b not in blocks:
                errs.append(f"{name}: common_fields 引用了不存在的块 {b}")
        cols: set[str] = set()
        common = {f["name"] for b in t.get("common_fields") or [] for f in blocks.get(b, [])}
        for f in t.get("fields") or []:
            fn, ft = f.get("name", ""), f.get("type", "")
            where = f"{name}.{fn}"
            if not IDENT.match(fn):
                errs.append(f"{where}: 列名不合法")
            if fn in cols or fn in common:
                errs.append(f"{where}: 列重复（或与公共字段撞名）")
            cols.add(fn)
            if ft not in tmap:
                errs.append(f"{where}: type={ft!r} 不在 type_map 里")
            if ft in NEEDS_LENGTH and not f.get("length"):
                errs.append(f"{where}: type={ft} 必须有 length")
            if ft == "dict":
                if not f.get("dict"):
                    errs.append(f"{where}: type=dict 必须有 dict")
                elif f["dict"] not in dicts:
                    errs.append(f"{where}: 字典 {f['dict']} 不在 dicts 权威清单里")
                elif f.get("default") is not None and str(f["default"]) not in {
                        str(k) for k in dicts[f["dict"]]["values"]}:
                    errs.append(f"{where}: default={f['default']!r} 不是字典 {f['dict']} 的合法值")
            if not f.get("comment"):
                errs.append(f"{where}: 缺 comment")
        if "id" not in cols:
            errs.append(f"{name}: 缺主键列 id")
        for ix in t.get("indexes") or []:
            for c in ix.get("cols") or []:
                if c not in cols and c not in common:
                    errs.append(f"{name}: 索引 {ix.get('name')} 引用了不存在的列 {c}")
    return errs


def col_sql(f: dict, tmap: dict) -> str:
    base = tmap[f["type"]]["ddl"]
    if f["type"] in NEEDS_LENGTH:
        base = f"{base}({f['length']})"
    parts = [f"    {f['name']:<24}{base}"]
    if not f.get("nullable", True):
        parts.append("NOT NULL")
    if f.get("default") is not None:
        d = str(f["default"])
        numeric = f["type"] == "int"
        parts.append(f"DEFAULT {d}" if numeric else f"DEFAULT {q(d)}")
    return " ".join(parts)


def render_table(t: dict, data: dict) -> str:
    tmap, blocks = data["type_map"], data["common_field_blocks"]
    name = t["table"]
    fields = list(t["fields"])
    for b in t.get("common_fields") or []:
        fields += blocks[b]
    out = [f"-- ── {name}：{t.get('comment', '')}", f"CREATE TABLE {name} ("]
    out.append(",\n".join([col_sql(f, tmap) for f in fields] + [f"    CONSTRAINT pk_{name[2:]} PRIMARY KEY (id)"]))
    out.append(");")
    out.append(f"COMMENT ON TABLE {name} IS {q(t.get('comment', ''))};")
    for f in fields:
        out.append(f"COMMENT ON COLUMN {name}.{f['name']} IS {q(f['comment'])};")
    uniques: dict[str, list[str]] = {}
    for f in t["fields"]:
        if f.get("unique"):
            uniques.setdefault(f["unique"], []).append(f["name"])
    for uk, cols in uniques.items():
        # 部分唯一索引：只约束未删除的行 → 软删后同键可重建（ADR-0009）；NULL 互不冲突是 PG 默认语义
        out.append(f"CREATE UNIQUE INDEX {uk} ON {name} ({', '.join(cols)}) WHERE del_flag = '0';")
    for ix in t.get("indexes") or []:
        out.append(f"CREATE INDEX {ix['name']} ON {name} ({', '.join(ix['cols'])});")
    return "\n".join(out) + "\n"


def render_dicts(data: dict) -> str:
    """字典 seed：dict_id / dict_code 落 5_100_000 段（避开若依自带与运行期雪花 id）；幂等（ON CONFLICT DO NOTHING）。
    评分四个字典的分值写进 remark 列（ADR 口径：改分值改字典 remark，不改代码）。"""
    out = ["-- 由 doc/tools/gen_ddl_pg.py --dicts 从 field-ssot.yaml 的 dicts 生成；改字典改 SSOT，别手改本段\n"]
    for i, (dtype, spec) in enumerate(data["dicts"].items()):
        did = 5100000 + i
        out.append(f"INSERT INTO sys_dict_type (dict_id, dict_name, dict_type, create_by, create_time, remark) "
                   f"VALUES ({did}, {q(spec['label'])}, {q(dtype)}, 1, now(), '类器官送检系统') ON CONFLICT (dict_id) DO NOTHING;")
        scores = spec.get("scores") or {}
        for j, (val, label) in enumerate(spec["values"].items()):
            remark = q(scores[val]) if val in scores else "NULL"
            out.append(f"INSERT INTO sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, is_default, create_by, create_time, remark) "
                       f"VALUES ({did * 100 + j}, {j + 1}, {q(label)}, {q(val)}, {q(dtype)}, 'N', 1, now(), {remark}) ON CONFLICT (dict_code) DO NOTHING;")
        out.append("")
    return "\n".join(out)


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--table", action="append", default=[])
    ap.add_argument("--migration", help="只出 migration 字段等于此文件名的表")
    ap.add_argument("--check", action="store_true")
    ap.add_argument("--dicts", action="store_true", help="输出全部字典的 seed SQL")
    a = ap.parse_args()

    data = yaml.safe_load(SSOT.read_text(encoding="utf-8"))
    errs = validate(data)
    if errs:
        sys.stderr.write("field-ssot.yaml 校验不过：\n" + "\n".join(f"  - {e}" for e in errs) + "\n")
        return 1
    tables = data["tables"]
    if a.dicts:
        print(render_dicts(data))
        return 0
    if a.check:
        n = sum(len(t["fields"]) for t in tables)
        print(f"field-ssot OK：{len(tables)} 张表 · {n} 个业务字段 · {len(data.get('dicts') or {})} 个字典")
        return 0
    if a.table:
        unknown = set(a.table) - {t["table"] for t in tables}
        if unknown:
            sys.stderr.write(f"SSOT 里没有这些表: {sorted(unknown)}\n")
            return 1
        tables = [t for t in tables if t["table"] in a.table]
    if a.migration:
        tables = [t for t in tables if t.get("migration") == a.migration]
        if not tables:
            sys.stderr.write(f"没有表的 migration 是 {a.migration}\n")
            return 1
    print("-- 由 doc/tools/gen_ddl_pg.py 从 doc/authority/field-ssot.yaml 生成；改字段改 SSOT，别手改本段\n")
    for t in tables:
        print(render_table(t, data))
    if any(t["table"] == "t_lqg_sample" for t in tables):
        print("-- 送检单号序列（submit_no = 'SJ' || lpad(nextval::text, 8, '0')）")
        print("CREATE SEQUENCE IF NOT EXISTS seq_lqg_submit_no START 1;\n")
    return 0


if __name__ == "__main__":
    sys.exit(main())
