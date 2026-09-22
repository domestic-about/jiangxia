#!/usr/bin/env python3
"""模式 B（2026-09-22 Kevin 决定，D5 起生效）：把**票面的 accept 断言**当 L1 证据重放。

为什么改（实测账，D1–D4）：
  · 分片 audit 里 10 条拦门 S0/S1 —— L2 占 9 条、L1 占 1 条、**L0 与 L3 各 0 条**；
  · 而每轮 L0+L1 都要派一个全新 agent，把同样的确定性对账重新推导一遍（还各自
    重建一次环境：mvn + 两个前端 build + reseed + 起进程）。
  · 断言本来就是 ① 侧票面资产（front-matter 的 accept.run），实现方只是把它落成
    runner。直接从票面重放 = 确定性、零上下文、可逐轮 diff，比 agent 每次重推更钉得住。

归一化（只有两条，且逐条印出来，**不做静默弱化**）：
  NF1  去掉 `--fresh-module <模块>`。本沙箱 `/bin/ps` 是 "Operation not permitted"，
       守卫落到 `date -d`（macOS 没有）→ api.sh exit 2，把工具故障伪装成断言红
       （issue #1/#13/#82，第 12 次命中）。新鲜度改由 gate.sh 的 L0.0 前置检查承担
       （源码不得新于 jar + 进程必须持有该 jar）。
       实测：43 张票 101 条 accept 里 51 条含该 token；除它之外 **0 条**需要在
       `--bizcode` 或判码写法上做手脚（逐语句核过 `api.sh … grep -qE '^<码>'` 的组合）。
  NF2  给票面的 `mvn` 补上本机必需的三个参数 `-s .mvn-settings.xml
       -Dmaven.repo.local=<ws>/.m2repo -Duser.home=<ws>/.buildhome`（issue #6）。
       缺它们时 maven 会去写 `~/.m2` 并被沙箱拒 → exit 1，**把工具故障伪装成断言红**
       （D4 验证轮实测：CRYO-REMIND-001 acc1 就是这样假红的）。实测 8 条 accept 命中。

用法：
  python3 doc/waves/tools/accept-run.py --phase D5 --meta
  python3 doc/waves/tools/accept-run.py --phase D5 --emit .tmp/gate/D5/accepts
  python3 doc/waves/tools/accept-run.py --phase D5 --run --json .tmp/gate/D5/accept.json [--only T_CODE]
  python3 doc/waves/tools/accept-run.py --ticket CRYO-FLOW-001 --run

退出码：0 = 全部 accept 成立 ｜ 1 = 有 accept 不成立 ｜ 2 = 用法 / 环境 / 读票失败
"""

import argparse
import glob
import json
import os
import re
import subprocess
import sys
import time

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", "..", ".."))
TICKETS_DIR = os.path.join(ROOT, "doc", "tickets")

try:
    import yaml
except ImportError:  # pragma: no cover
    sys.stderr.write("[error] 需要 pyyaml（python3 -c 'import yaml' 失败）\n")
    raise SystemExit(2)

# NF2：本机 maven 必需参数（绝对路径，与 cwd 无关）
MVN_FLAGS = ("-s {r}/.mvn-settings.xml -Dmaven.repo.local={r}/.m2repo "
             "-Duser.home={r}/.buildhome")


def _inject_mvn(repl_root, text):
    """给每个缺 -Dmaven.repo.local 的 mvn / ./mvnw 调用补上三个必需参数。"""
    out, pos, changed = [], 0, False
    for m in re.finditer(r"(?<![\w./-])(\./mvnw|mvn)(?=\s)", text):
        seg_end = len(text)
        for sep in re.finditer(r"&&|\|\||[;|\n]", text[m.end():]):
            seg_end = m.end() + sep.start()
            break
        seg = text[m.end():seg_end]
        out.append(text[pos:m.end()])
        if "-Dmaven.repo.local" in seg:
            out.append(text[m.end():m.end()])
        else:
            out.append(" " + MVN_FLAGS.format(r=repl_root))
            changed = True
        pos = m.end()
    out.append(text[pos:])
    return "".join(out), changed


def front_matter(path):
    """读 ticket 的 YAML front-matter（与 dag_lint.py 同一份票面资产）。"""
    with open(path, encoding="utf-8") as f:
        text = f.read()
    m = re.match(r"^---\n(.*?)\n---\n", text, re.S)
    if not m:
        raise ValueError(f"{path} 没有 front-matter")
    return yaml.safe_load(m.group(1)) or {}


def load_tickets(phase=None, ticket=None):
    out = []
    for p in sorted(glob.glob(os.path.join(TICKETS_DIR, "*", "prompt.md"))):
        try:
            fm = front_matter(p)
        except Exception as e:
            raise SystemExit(f"[error] 读不了 {p}: {e}")
        if ticket and fm.get("ticket") != ticket:
            continue
        if phase and fm.get("phase") != phase:
            continue
        fm["_path"] = p
        out.append(fm)
    if not out:
        raise SystemExit(f"[error] 没找到票（phase={phase} ticket={ticket}）")
    return out


def normalize(run):
    """返回 (归一化后的 run, 施加的规则列表)。只有 NF1 / NF2，且逐条记下来。"""
    applied = []
    new = run
    if re.search(r"--fresh-module\s+\S+", new):
        new = re.sub(r"\s*--fresh-module\s+\S+", "", new)
        applied.append("NF1")
    new, mc = _inject_mvn(ROOT, new)
    if mc:
        applied.append("NF2")
    return new, applied


def ddl_tables(fm):
    ts = []
    for a in fm.get("accept") or []:
        for m in re.finditer(r"--table\s+([A-Za-z_][A-Za-z0-9_]*)", a.get("run") or ""):
            if m.group(1) not in ts:
                ts.append(m.group(1))
    return ts


def meta(tickets):
    touches = [t for fm in tickets for t in (fm.get("touches") or [])]
    tables = []
    for fm in tickets:
        for t in ddl_tables(fm):
            if t not in tables:
                tables.append(t)
    n_acc = sum(len(fm.get("accept") or []) for fm in tickets)
    return {
        "tickets": [fm["ticket"] for fm in tickets],
        "accept_count": n_acc,
        "needs_plusui": any("code/plus-ui" in t for t in touches),
        "needs_miniapp": any("code/miniapp" in t for t in touches),
        "needs_backend_java": any("ruoyi-lqg" in t or "ruoyi-admin" in t for t in touches),
        "ddl_tables": tables,
    }


def scripts(tickets):
    """展开成 [(ticket, index, name, form, 归一化 run, nf)]，index 从 1 起。"""
    out = []
    for fm in tickets:
        for i, a in enumerate(fm.get("accept") or [], start=1):
            if not isinstance(a, dict) or not a.get("run"):
                raise SystemExit(f"[error] {fm['ticket']} accept[{i}] 缺 run")
            run, nf = normalize(a["run"])
            out.append({
                "ticket": fm["ticket"], "index": i,
                "name": (a.get("name") or "")[:120], "form": a.get("form") or "",
                "run": run, "nf": nf,
            })
    return out


def emit(items, outdir):
    os.makedirs(outdir, exist_ok=True)
    paths = []
    for it in items:
        p = os.path.join(outdir, f"{it['ticket']}-acc{it['index']}.sh")
        with open(p, "w", encoding="utf-8") as f:
            f.write("#!/usr/bin/env bash\n")
            f.write(f"# 票面 accept（逐字重放）：{it['ticket']} accept[{it['index']}] form={it['form']}\n")
            f.write(f"# {it['name']}\n")
            if it["nf"]:
                f.write(f"# 归一化：{','.join(it['nf'])}\n")
            f.write("set -euo pipefail\n")
            f.write(it["run"].rstrip() + "\n")
        os.chmod(p, 0o755)
        it["path"] = p
        paths.append(p)
    return paths


def run_all(items, timeout, json_path=None, logdir=None):
    env = dict(os.environ)
    results = []
    ok_n = 0
    if logdir:
        os.makedirs(logdir, exist_ok=True)
    for it in items:
        assert "path" in it, "先 emit 再 run"
        log = os.path.join(logdir, os.path.basename(it["path"]) + ".log") if logdir else None
        t0 = time.time()
        with open(log, "w", encoding="utf-8") as lf:
            lf.write(f"# {it['ticket']} accept[{it['index']}] {it['name']}\n")
            if it["nf"]:
                lf.write(f"# 归一化 {','.join(it['nf'])}\n")
            lf.write(f"# LQG_VERIFY_ENV_FILE={env.get('LQG_VERIFY_ENV_FILE', '(未设)')}\n\n")
            lf.flush()
            try:
                cp = subprocess.run(["bash", it["path"]], cwd=ROOT, env=env,
                                    stdout=lf, stderr=subprocess.STDOUT, timeout=timeout)
                rc = cp.returncode
            except subprocess.TimeoutExpired:
                rc = 124
                lf.write(f"\n[gate] 超时 {timeout}s\n")
        dt = round(time.time() - t0, 1)
        rec = {"ticket": it["ticket"], "index": it["index"], "form": it["form"],
               "name": it["name"], "exit": rc, "seconds": dt,
               "nf": it["nf"], "path": it["path"], "log": log}
        results.append(rec)
        if rc == 0:
            ok_n += 1
            print(f"  \033[32m✓\033[0m {it['ticket']} acc{it['index']} [{it['form']}] {it['name'][:64]} ({dt}s)")
        else:
            print(f"  \033[31m✗\033[0m {it['ticket']} acc{it['index']} [{it['form']}] {it['name'][:64]} "
                  f"→ exit {rc} ({dt}s) 日志 {log}")
    out = {"total": len(results), "passed": ok_n,
           "failed": [r for r in results if r["exit"] != 0], "results": results}
    if json_path:
        os.makedirs(os.path.dirname(os.path.abspath(json_path)), exist_ok=True)
        with open(json_path, "w", encoding="utf-8") as f:
            json.dump(out, f, ensure_ascii=False, indent=1)
        print(f"[ok] 结果落盘 {json_path}")
    return out


def main():
    ap = argparse.ArgumentParser(description="票面 accept 重放器（模式 B）")
    g = ap.add_mutually_exclusive_group(required=True)
    g.add_argument("--phase", help="任务号，如 D5")
    g.add_argument("--ticket", help="单票，如 CRYO-FLOW-001")
    ap.add_argument("--meta", action="store_true", help="只打印该任务的票/前端/DDL 表清单（JSON）")
    ap.add_argument("--emit", metavar="DIR", help="把 accept 落成 shell 脚本到 DIR")
    ap.add_argument("--run", action="store_true", help="执行（必须先 --emit，或用 --json 触发落盘）")
    ap.add_argument("--json", help="结果 JSON 路径（给了就顺带落盘）")
    ap.add_argument("--logdir", help="每条 accept 的 stdout 日志目录")
    ap.add_argument("--timeout", type=int, default=900, help="单条 accept 超时秒数（默认 900）")
    ap.add_argument("--only", help="只跑某个 ticket（ticket 号）")
    a = ap.parse_args()

    tickets = load_tickets(a.phase, a.ticket)
    if a.meta:
        print(json.dumps(meta(tickets), ensure_ascii=False, indent=1))
        return 0

    items = scripts(tickets)
    if a.only:
        items = [it for it in items if it["ticket"] == a.only]
        if not items:
            raise SystemExit(f"[error] --only {a.only} 没有匹配的 accept")

    if a.emit:
        paths = emit(items, a.emit)
        print(f"[ok] 落 {len(paths)} 条 accept 脚本 → {a.emit}")
        for p in paths:
            print(f"     {os.path.relpath(p, ROOT)}")
    if a.run:
        if not a.emit:
            d = (a.json and os.path.dirname(os.path.abspath(a.json))) or os.path.join(ROOT, ".tmp", "gate", "accepts")
            emit(items, d)
        print(f"[run] {len(items)} 条 accept，单条超时 {a.timeout}s")
        out = run_all(items, a.timeout, a.json, a.logdir)
        print(f"[run] 通过 {out['passed']}/{out['total']}")
        return 0 if out["passed"] == out["total"] else 1
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except KeyboardInterrupt:
        sys.exit(130)
