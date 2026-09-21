#!/usr/bin/env python3
"""把全部 ticket 的 accept 里写的 SQL 拎出来，在一个**已有全量表结构**的库上逐条 EXPLAIN。

为什么要有：40 多张 ticket 里有一百多条 `db.py --sql "…"`，是在实现之前写的。表名、列名、类型转换写错一个字，
到了 ②zhixing 才发现，那条 accept 会以 exit 2（SQL 错）卡住实现方——而实现方多半会去「修」断言。这里提前把笔误筛掉。
只查「这条 SQL 在 SSOT 生成的表结构上语法与引用是否成立」，不执行、不判断结果对不对。

用法（cwd = 项目根；库需先灌：上游若依 postgres 脚本 + `gen_ddl_pg.py` 全量 DDL + 一张空的 flyway_schema_history）：
  LQG_VERIFY_ENV_FILE=<env 文件> python3 doc/tools/check_accept_sql.py
退出码：0 全部可解析 / 1 有 SQL 不成立。
"""
import glob
import os
import re
import sys

import yaml

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "verify"))
from db import load_env  # noqa: E402

import psycopg2  # noqa: E402

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..")
SQL_RE = re.compile(r'--sql\s+"((?:[^"\\]|\\.)*)"', re.S)


def main():
    kv = load_env()
    conn = psycopg2.connect(host=kv["LQG_DB_HOST"], port=int(kv.get("LQG_DB_PORT") or 5432), dbname=kv["LQG_DB_NAME"],
                            user=kv["LQG_DB_USER"], password=kv.get("LQG_DB_PASSWORD", ""))
    conn.autocommit = True
    total, bad = 0, []
    for md in sorted(glob.glob(os.path.join(ROOT, "doc", "tickets", "*", "prompt.md"))):
        tid = os.path.basename(os.path.dirname(md))
        text = open(md, encoding="utf-8").read()
        fm = yaml.safe_load(text.split("\n---\n", 1)[0].split("---\n", 1)[1])
        for i, a in enumerate(fm["accept"]):
            for m in SQL_RE.finditer(a["run"]):
                sql = m.group(1).replace('\\"', '"').replace("\\$", "$")
                # accept 里个别 SQL 带 shell 函数的位置参数 / 变量：换成一个合法的样例值再解析
                sql = sql.replace("FROM $1 WHERE sample_id=$2", "FROM t_lqg_qc_sample WHERE sample_id=9000001006")
                sql = re.sub(r"user_id=\$1\b", "user_id=9000000111", sql)
                sql = re.sub(r"submitter_id=\$1\b", "submitter_id=9000000111", sql)
                if re.search(r"\$\{?\w", sql):
                    bad.append((tid, i, "SQL 里还有没替换的 shell 变量，静态检查不了：" + sql[:80]))
                    continue
                total += 1
                try:
                    with conn.cursor() as cur:
                        cur.execute("EXPLAIN " + sql)
                except Exception as e:  # noqa: BLE001
                    bad.append((tid, i, str(e).strip().splitlines()[0] + "  ← " + sql[:90]))
    conn.close()
    for tid, i, msg in bad:
        print(f"✗ [{tid}] accept[{i}]: {msg}")
    print(f"\n静态检查 {total} 条 SQL，不成立 {len(bad)} 条")
    return 1 if bad else 0


if __name__ == "__main__":
    sys.exit(main())
