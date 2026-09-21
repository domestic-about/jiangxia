#!/usr/bin/env python3
"""accept / 回归断言的 DB 执行器 —— PostgreSQL，**只读会话**。

用法（accept 的 run 直接调它，SQL 写在命令行里，dag_lint C5 才看得见 SQL 内容）：

  python3 doc/verify/db.py --sql "SELECT count(*) FROM t_lqg_sample" --eq 9
  python3 doc/verify/db.py --sql "..." --empty            # 必须查不到行（对账差异为 0）
  python3 doc/verify/db.py --sql "..." --nonempty
  python3 doc/verify/db.py --sql "..." --rows 6
  python3 doc/verify/db.py --sql "..." --col-set a,b,c    # 首列值集合必须**精确等于**（顺序无关）
  python3 doc/verify/db.py --sql "..."                    # 只打印，人看

退出码：0 = 断言成立；1 = **断言不成立**（实现有问题）；2 = **用法 / 连接 / SQL 错**（表不存在、列名写错）。
1 和 2 必须分开：否则「表还没建」和「实现做错了」在流水线里长得一模一样。

连接参数：doc/verify/verify.env（照 verify.env.example 建；不接受命令行传密码）。
会话一律 `SET default_transaction_read_only = on`——断言脚本手滑也写不了库。
多列结果用 | 拼成一行再比（--eq "a|b|c"）。
"""
import argparse
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ENV_FILE = os.environ.get("LQG_VERIFY_ENV_FILE") or os.path.join(HERE, "verify.env")


def load_env():
    kv = {}
    if os.path.exists(ENV_FILE):
        with open(ENV_FILE, encoding="utf-8") as f:
            for line in f:
                line = line.strip()
                if line and not line.startswith("#") and "=" in line:
                    k, v = line.split("=", 1)
                    kv[k.strip()] = v.strip()
    for k in ("LQG_DB_HOST", "LQG_DB_PORT", "LQG_DB_NAME", "LQG_DB_USER", "LQG_DB_PASSWORD"):
        if os.environ.get(k):
            kv[k] = os.environ[k]
    missing = [k for k in ("LQG_DB_HOST", "LQG_DB_NAME", "LQG_DB_USER") if not kv.get(k)]
    if missing:
        sys.stderr.write(f"[error] 缺连接参数 {missing}：建 {ENV_FILE}（照 verify.env.example）\n")
        sys.exit(2)
    return kv


def run_sql(sql):
    try:
        import psycopg2  # noqa: PLC0415
    except ImportError:
        sys.stderr.write("[error] 需要 psycopg2：pip install psycopg2-binary\n")
        sys.exit(2)
    kv = load_env()
    try:
        conn = psycopg2.connect(host=kv["LQG_DB_HOST"], port=int(kv.get("LQG_DB_PORT") or 5432),
                                dbname=kv["LQG_DB_NAME"], user=kv["LQG_DB_USER"],
                                password=kv.get("LQG_DB_PASSWORD", ""), connect_timeout=5)
    except Exception as e:  # noqa: BLE001
        sys.stderr.write(f"[error] 连不上数据库：{e}\n")
        sys.exit(2)
    try:
        conn.set_session(readonly=True, autocommit=True)
        with conn.cursor() as cur:
            cur.execute(sql)
            return cur.fetchall() if cur.description else []
    except Exception as e:  # noqa: BLE001
        sys.stderr.write(f"[error] SQL 执行失败（不是断言不成立，是断言本身有问题）：{e}\n")
        sys.exit(2)
    finally:
        conn.close()


def main():
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("--sql", required=True)
    p.add_argument("--eq", help="首行（多列用 | 拼接）必须等于此值")
    p.add_argument("--rows", type=int, help="结果行数必须等于此值")
    p.add_argument("--empty", action="store_true", help="必须零行（对账类：差异集为空）")
    p.add_argument("--nonempty", action="store_true")
    p.add_argument("--col-set", help="首列的值集合必须精确等于这个逗号分隔集合（顺序无关）")
    p.add_argument("--quiet", action="store_true")
    a = p.parse_args()

    if not re.match(r"\s*(SELECT|WITH)\b", a.sql, re.I):
        sys.stderr.write("[error] 只接受 SELECT / WITH 开头的查询——断言不改库\n")
        return 2
    asserts = [x for x in (a.eq is not None, a.rows is not None, a.empty, a.nonempty, a.col_set is not None) if x]
    if len(asserts) > 1:
        sys.stderr.write("[error] 一次只能带一种断言\n")
        return 2

    rows = run_sql(a.sql)
    lines = ["|".join("" if c is None else str(c) for c in r) for r in rows]
    if a.quiet:
        # --quiet 的语义是「只打印值」——给 `X="$(db.py --quiet --sql …)"` 插值用：
        # 每行取**第一列**，不加表头、不做 | 拼接、不截断、不加「共 N 行」提示。
        # 2026-09-22 修（Kevin 拍板）：原实现是「什么都不打印」，而 doc/tickets/ 里
        # 10 张票都写成 OID="$(db.py --quiet --sql "SELECT id …")" → 插值恒为空，
        # AUTH-EXT-001 的 accept 3 因此假绿过（空 id 让请求变成 `.../organoid/`，
        # 404 正好落进断言正则 ^(400|403|404)）。断言类用途请用下面那组标志，别用 --quiet。
        for r in rows:
            print("" if r[0] is None else str(r[0]))
    else:
        for ln in lines[:50]:
            print(ln)
        if len(lines) > 50:
            print(f"…（共 {len(lines)} 行）")

    def fail(msg):
        sys.stderr.write(f"[FAIL] {msg}\n")
        return 1

    if a.eq is not None:
        if not lines:
            return fail(f"期望首行 = {a.eq!r}，实际零行")
        return 0 if lines[0] == a.eq else fail(f"期望 {a.eq!r}，实际 {lines[0]!r}")
    if a.rows is not None:
        return 0 if len(lines) == a.rows else fail(f"期望 {a.rows} 行，实际 {len(lines)} 行")
    if a.empty:
        return 0 if not lines else fail(f"期望零行，实际 {len(lines)} 行（上面打印的就是差异）")
    if a.nonempty:
        return 0 if lines else fail("期望至少一行，实际零行")
    if a.col_set is not None:
        want = {x.strip() for x in a.col_set.split(",") if x.strip()}
        got = {("" if r[0] is None else str(r[0])) for r in rows}
        if want == got:
            return 0
        return fail(f"集合不相等：多出 {sorted(got - want)}，缺少 {sorted(want - got)}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
