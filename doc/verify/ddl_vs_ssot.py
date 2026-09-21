#!/usr/bin/env python3
"""DDL 类 accept 的对账执行器：**库里的真实表** vs **doc/authority/field-ssot.yaml**，两侧不同源。

  python3 doc/verify/ddl_vs_ssot.py --table t_lqg_sample [--table ...] \
      --require-public create_dept,create_by,create_time,update_by,update_time,del_flag

逐表核对（任何一条不符 → exit 1 并打印差异）：
  · 列集合**精确相等**（SSOT 业务列 + 公共字段块；多一列少一列都红）
  · 每列类型、长度、可空性、默认值
  · 主键是 id
  · SSOT 里每个 unique 组 → 库里有同名**部分唯一索引**，列序一致，且带 WHERE del_flag = '0'
  · SSOT 里每个普通索引 → 库里有同名索引且列序一致
  · --require-public 列出的公共字段必须全在（与 lint-profile.yaml 的 public_fields 同集）
退出码：0 相符 / 1 不相符 / 2 用法、连接错或表不存在于 SSOT。
"""
import argparse
import os
import re
import sys

import yaml

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
from db import run_sql  # noqa: E402  复用同一份只读连接逻辑

SSOT = os.path.join(HERE, "..", "authority", "field-ssot.yaml")
PG_TYPE = {"BIGINT": "bigint", "VARCHAR": "character varying", "TEXT": "text", "INTEGER": "integer",
           "DATE": "date", "TIMESTAMP(0)": "timestamp without time zone", "CHAR(1)": "character"}


def expected_columns(t, data):
    cols = {}
    fields = list(t["fields"])
    for b in t.get("common_fields") or []:
        fields += data["common_field_blocks"][b]
    for f in fields:
        ddl = data["type_map"][f["type"]]["ddl"]
        length = f.get("length") if f["type"] in ("str", "dict") else (1 if ddl == "CHAR(1)" else None)
        cols[f["name"]] = {"type": PG_TYPE[ddl], "len": length, "nullable": bool(f.get("nullable", True)),
                           "default": None if f.get("default") is None else str(f["default"])}
    return cols


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--table", action="append", required=True)
    ap.add_argument("--require-public", default="")
    a = ap.parse_args()
    with open(SSOT, encoding="utf-8") as f:
        data = yaml.safe_load(f)
    by_name = {t["table"]: t for t in data["tables"]}
    public = [x.strip() for x in a.require_public.split(",") if x.strip()]
    diffs = []
    for name in a.table:
        if name not in by_name:
            sys.stderr.write(f"[error] SSOT 里没有表 {name}\n")
            return 2
        if not re.fullmatch(r"[a-z0-9_]+", name):
            return 2
        t = by_name[name]
        want = expected_columns(t, data)
        rows = run_sql("SELECT column_name, data_type, character_maximum_length, is_nullable, column_default "
                       f"FROM information_schema.columns WHERE table_schema='public' AND table_name='{name}'")
        if not rows:
            diffs.append(f"{name}: 库里没有这张表")
            continue
        got = {r[0]: {"type": r[1], "len": r[2], "nullable": r[3] == "YES", "default": r[4]} for r in rows}
        for c in sorted(set(want) - set(got)):
            diffs.append(f"{name}.{c}: SSOT 有、库里没有")
        for c in sorted(set(got) - set(want)):
            diffs.append(f"{name}.{c}: 库里有、SSOT 没有（加列要先改 SSOT）")
        for c in public:
            if c not in got:
                diffs.append(f"{name}.{c}: 公共字段缺失")
        for c in sorted(set(want) & set(got)):
            w, g = want[c], got[c]
            if w["type"] != g["type"]:
                diffs.append(f"{name}.{c}: 类型 期望 {w['type']} 实际 {g['type']}")
            if w["len"] is not None and w["len"] != g["len"]:
                diffs.append(f"{name}.{c}: 长度 期望 {w['len']} 实际 {g['len']}")
            if w["nullable"] != g["nullable"]:
                diffs.append(f"{name}.{c}: 可空 期望 {w['nullable']} 实际 {g['nullable']}")
            gd = None if g["default"] is None else re.sub(r"::[a-z ]+$", "", g["default"]).strip("'")
            if (w["default"] or None) != (gd or None):
                diffs.append(f"{name}.{c}: 默认值 期望 {w['default']!r} 实际 {g['default']!r}")
        pk = run_sql("SELECT a.attname FROM pg_index i JOIN pg_attribute a ON a.attrelid=i.indrelid AND a.attnum=ANY(i.indkey) "
                     f"WHERE i.indrelid='public.{name}'::regclass AND i.indisprimary")
        if [r[0] for r in pk] != ["id"]:
            diffs.append(f"{name}: 主键应为 (id)，实际 {[r[0] for r in pk]}")
        idx = {r[0]: r[1] for r in run_sql(f"SELECT indexname, indexdef FROM pg_indexes WHERE schemaname='public' AND tablename='{name}'")}
        uniques = {}
        for f in t["fields"]:
            if f.get("unique"):
                uniques.setdefault(f["unique"], []).append(f["name"])
        for uk, cols in uniques.items():
            d = idx.get(uk, "")
            if not d:
                diffs.append(f"{name}: 缺唯一索引 {uk}")
                continue
            if "UNIQUE" not in d or f"({', '.join(cols)})" not in d:
                diffs.append(f"{name}: {uk} 应为 UNIQUE ({', '.join(cols)})，实际 {d}")
            if "WHERE (del_flag = '0'" not in d:
                diffs.append(f"{name}: {uk} 不是部分唯一索引（缺 WHERE del_flag = '0'）——软删后同键将无法重建（ADR-0009）")
        for ix in t.get("indexes") or []:
            d = idx.get(ix["name"], "")
            if f"({', '.join(ix['cols'])})" not in d:
                diffs.append(f"{name}: 索引 {ix['name']} 应为 ({', '.join(ix['cols'])})，实际 {d or '不存在'}")
    if diffs:
        print("\n".join("✗ " + x for x in diffs))
        return 1
    print(f"✓ {len(a.table)} 张表与 SSOT 逐列相符（含公共字段 {len(public)} 个、部分唯一索引、普通索引）")
    return 0


if __name__ == "__main__":
    sys.exit(main())
