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
  python3 doc/waves/tools/accept-run.py --phase D5 --run --known-red doc/waves/regression/D5/known-red.txt
  python3 doc/waves/tools/accept-run.py --ticket CRYO-FLOW-001 --run

退出码：0 = 全部 accept 成立 ｜ 1 = 有 accept 不成立 ｜ 2 = 用法 / 环境 / 读票失败

known-red（2026-09-23 按 CR-20260923-09 收紧，独立验收查实「豁免按整条 accept 生效、断在第一段也算已登记」）：
  · 每条登记必须写明**豁免哪一段**：`TICKET|accN@段号|#issue|理由`，段号 = 该 accept 的 run 按**顶层 `&&`**
    切开后的序号（从 1 起），可写 `3`、`2-5`、`2,4`。`python3 doc/waves/tools/accept-run.py --ticket T --segments`
    打印切段结果供登记时对号。旧格式（不带 @段号）一律拒绝（exit 2）。
  · 登记的 issue 号必须在 doc/waves/state.json 的 open_issues 里存在，且状态是 open / decided（exit 2）。
  · 带登记的 accept 按段插桩执行：登记段红了 → 记下、**接着跑后面的段**（后面的段照样要绿）；
    非登记段红了 → 立即按原退出码失败，照「未登记的红」处理。run 不是单纯的顶层 `&&` 链（出现顶层 `;`、换行分隔、
    `||`、`&`、heredoc）时切不了段 → 原样执行、**豁免不生效**，并打印原因。
  · 没有登记的 accept 一律原样执行（逐字重放，不插桩）。
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
STATE_FILE = os.path.join(ROOT, "doc", "waves", "state.json")

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


def escalated_tickets():
    """状态里标 escalated 的 ticket —— 它们**没实现**，重放它们的 accept 只会得到
    「表/接口不存在」的红，那不是产品缺陷。默认跳过（并在输出里明说跳了哪些），
    要显式重放加 --include-escalated。
    ★ 别把它们塞进 known-red：known-red 的语义是「红的原因不是产品行为不符」，
      而 escalated 的语义是「根本没做」——混在一起就把「没做」伪装成了「已登记」。"""
    try:
        with open(STATE_FILE, encoding="utf-8") as f:
            d = json.load(f)
        return {k for k, v in (d.get("tickets") or {}).items()
                if isinstance(v, dict) and v.get("status") == "escalated"}
    except Exception:
        return set()


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


def emit(items, outdir, known_red=None):
    os.makedirs(outdir, exist_ok=True)
    paths = []
    for it in items:
        p = os.path.join(outdir, f"{it['ticket']}-acc{it['index']}.sh")
        body = it["run"].rstrip()
        kr = (known_red or {}).get((it["ticket"], it["index"]))
        it.pop("kr_instrumented", None); it.pop("kr_unsplittable", None)
        if kr:
            inst, why = instrument(it["run"], kr["segments"])
            if inst is None:
                it["kr_unsplittable"] = why
                print(f"  [known-red] {it['ticket']} acc{it['index']} 登记了豁免段 {kr['segments']}，但 run 切不了段"
                      f"（{why}）→ 原样执行，**豁免不生效**")
            else:
                body = inst
                it["kr_instrumented"] = True
        with open(p, "w", encoding="utf-8") as f:
            f.write("#!/usr/bin/env bash\n")
            f.write(f"# 票面 accept（{'按段插桩重放' if it.get('kr_instrumented') else '逐字重放'}）："
                    f"{it['ticket']} accept[{it['index']}] form={it['form']}\n")
            f.write(f"# {it['name']}\n")
            if it["nf"]:
                f.write(f"# 归一化：{','.join(it['nf'])}\n")
            f.write("set -euo pipefail\n")
            f.write(body + "\n")
        os.chmod(p, 0o755)
        it["path"] = p
        paths.append(p)
    return paths


def open_issue_ids():
    """state.json 的 open_issues 里状态为 open / decided 的 issue 号集合；读不了 → None（调用方按错误处理）。"""
    try:
        with open(STATE_FILE, encoding="utf-8") as f:
            d = json.load(f)
    except Exception:
        return None
    out = {}
    for x in d.get("open_issues") or []:
        if isinstance(x, dict) and x.get("id") is not None:
            out[str(x["id"]).lstrip("#")] = x.get("status") or ""
    return out


def parse_segs(spec):
    """'3' / '2-5' / '2,4' → {3} / {2,3,4,5} / {2,4}；非法 → None。"""
    segs = set()
    for part in spec.split(","):
        part = part.strip()
        m = re.match(r"^(\d+)(?:-(\d+))?$", part)
        if not m:
            return None
        a = int(m.group(1)); b = int(m.group(2) or a)
        if a < 1 or b < a:
            return None
        segs.update(range(a, b + 1))
    return segs or None


def _kr_die(msg):
    """known-red 清单本身有问题 = 用法 / 配置错（exit 2），不是「有 accept 不成立」（exit 1）。"""
    sys.stderr.write(msg + "\n")
    raise SystemExit(2)


def load_known_red(path):
    """已登记的「非产品缺陷造成的红」：TICKET|accN@段号|issue|理由
    格式（`#` 开头是注释）：
      SYS-STAGING-001|1@3|#198|buildCommit 戳落后本地 HEAD
      SYS-STAGING-001|2@2|#192|BSD sed 不支持 BRE \\?
      SYS-STAGING-001|2@3-5|#181|本机 nc 被代理劫持
    同一条 accept 可以写多行（不同段挂不同 issue），同一段不许登记两次。
    ★ 每条都必须挂 **state.json 的 open_issues 里真实存在**（open / decided）的 issue 号 —— 不许无号登记、
      不许挂不存在或已关的号，那是把真缺陷藏起来的入口。
    ★ 豁免只作用于登记的那几段（顶层 && 切段的序号），不是整条 accept。
      命中的红**仍然照原样打印与落盘**（不伪装成绿）。"""
    kr = {}
    if not path:
        return kr
    if not os.path.exists(path):
        _kr_die(f"[error] --known-red 指的文件不存在：{path}")
    known_ids = open_issue_ids()
    if known_ids is None:
        _kr_die(f"[error] 读不了 {STATE_FILE}，无法核对 known-red 的 issue 号 —— 拒绝使用 known-red")
    with open(path, encoding="utf-8") as f:
        for lineno, line in enumerate(f, 1):
            line = line.strip()
            if not line or line.startswith("#"):
                continue
            parts = line.split("|")
            if len(parts) < 4:
                _kr_die(f"[error] {path}:{lineno} 格式应为 TICKET|accN@段号|issue|理由")
            tk, idx, iss, why = parts[0].strip(), parts[1].strip(), parts[2].strip(), "|".join(parts[3:]).strip()
            m = re.match(r"^(\d+)@(.+)$", idx)
            if not m:
                _kr_die(f"[error] {path}:{lineno} 没写豁免哪一段（accN@段号，如 1@3、2@2-5）——"
                                 f"豁免只作用于登记的那一段，不许整条 accept 豁免")
            segs = parse_segs(m.group(2))
            if segs is None:
                _kr_die(f"[error] {path}:{lineno} 段号 {m.group(2)!r} 不合法（写 3、2-5、2,4）")
            if not re.match(r"^#?[0-9]+(,#?[0-9]+)*$", iss):
                _kr_die(f"[error] {path}:{lineno} issue 必须是 #号 列表（不许无号登记）")
            for one in iss.split(","):
                num = one.strip().lstrip("#")
                st = known_ids.get(num)
                if st is None:
                    _kr_die(f"[error] {path}:{lineno} issue #{num} 不在 doc/waves/state.json 的 open_issues 里"
                                     f"（不许挂不存在的号）")
                if st not in ("open", "decided"):
                    _kr_die(f"[error] {path}:{lineno} issue #{num} 状态是 {st!r}，不是 open / decided"
                                     f"（已关的号不能再为红兜底）")
            key = (tk, int(m.group(1)))
            ent = kr.setdefault(key, {"segments": [], "by_segment": {}})
            dup = sorted(set(ent["segments"]) & segs)
            if dup:
                _kr_die(f"[error] {path}:{lineno} {tk} acc{m.group(1)} 的第 {dup} 段重复登记")
            ent["segments"] = sorted(set(ent["segments"]) | segs)
            for s in segs:
                ent["by_segment"][s] = {"issue": iss, "reason": why}
    for ent in kr.values():   # 汇总成老字段（gate-audit / 打印用）：issue 与理由按段去重拼起来
        uniq = []
        for s in ent["segments"]:
            b = ent["by_segment"][s]
            if b not in uniq:
                uniq.append(b)
        ent["issue"] = ",".join(dict.fromkeys(x for b in uniq for x in b["issue"].split(",")))
        ent["reason"] = "；".join(b["reason"] for b in uniq)
    return kr


# ── 顶层 && 切段（known-red 按段豁免用）────────────────────────────────────────
_KW_OPEN = {"for": "loop", "while": "loop", "until": "loop", "select": "loop", "if": "if", "case": "case"}
_KW_CLOSE = {"done": "loop", "fi": "if", "esac": "case"}
_KW_KEEP_CMD = {"do", "then", "else", "elif", "!", "time"}


def split_top_and(text):
    """把一段 run 按**顶层** `&&` 切段 → [段文本, …]；切不了返回 (None, 原因)。

    只认「一整条顶层 && 链」：顶层出现 `;`、换行分隔（不是跟在 && / | 之后的换行）、`||`、单个 `&`、
    heredoc，或引号 / 括号 / 关键字块不平衡 → 切不了。引号、`$( )`、`( )`、`{ }`、`${ }`、`$(( ))`、
    反引号、`for/while/until … done`、`if … fi`、`case … esac`、`[[ … ]]` 里面的 && 不切。"""
    n = len(text)
    i = 0
    stack = []          # 'dq' 'sq-none' 'cmdsub' 'paren' 'brace' 'param' 'arith' 'bq' 'loop' 'if' 'case' 'dbrack'
    segs, start = [], 0
    cmd_start = True
    pending_cont = False   # 顶层刚出现过 && 或 |，后面的换行是续行
    while i < n:
        c = text[i]
        top = stack[-1] if stack else None
        # ── 双引号里 ──
        if top == "dq":
            if c == "\\":
                i += 2; continue
            if c == '"':
                stack.pop(); i += 1; continue
            if text.startswith("$((", i):
                stack.append("arith"); i += 3; continue
            if text.startswith("$(", i):
                stack.append("cmdsub"); i += 2; cmd_start = True; continue
            if text.startswith("${", i):
                stack.append("param"); i += 2; continue
            if c == "`":
                stack.append("bq"); i += 1; cmd_start = True; continue
            i += 1; continue
        # ── 算术 $(( )) ──
        if top == "arith":
            if text.startswith("))", i):
                stack.pop(); i += 2; continue
            if c == "(":
                stack.append("arith-paren"); i += 1; continue
            i += 1; continue
        if top == "arith-paren":
            if c == ")":
                stack.pop()
            elif c == "(":
                stack.append("arith-paren")
            i += 1; continue
        # ── ${ … } ──
        if top == "param":
            if c == "}":
                stack.pop(); i += 1; continue
            if c == "'":
                j = text.find("'", i + 1)
                if j < 0:
                    return None, "单引号没闭合"
                i = j + 1; continue
            if c == '"':
                stack.append("dq"); i += 1; continue
            if text.startswith("$(", i):
                stack.append("cmdsub"); i += 2; cmd_start = True; continue
            if text.startswith("${", i):
                stack.append("param"); i += 2; continue
            i += 1; continue
        # ── 命令语境（顶层 / $( ) / ( ) / { } / 关键字块 / 反引号）──
        if c in " \t":
            i += 1; continue
        if c == "\\":
            if i + 1 < n and text[i + 1] == "\n":
                i += 2; continue          # 续行
            i += 2; cmd_start = False; continue
        if c == "\n":
            if not stack:
                if pending_cont:
                    i += 1; continue
                if text[i:].strip():
                    return None, "顶层有换行分隔的多条命令（不是单纯的 && 链）"
                i += 1; continue
            i += 1; cmd_start = True; continue
        if c == "#" and (i == 0 or text[i - 1] in " \t\n;&|()"):
            j = text.find("\n", i)
            i = n if j < 0 else j
            continue
        if c == "'":
            j = text.find("'", i + 1)
            if j < 0:
                return None, "单引号没闭合"
            i = j + 1; cmd_start = False; pending_cont = False; continue
        if c == '"':
            stack.append("dq"); i += 1; cmd_start = False; pending_cont = False; continue
        if c == "`":
            if top == "bq":
                stack.pop(); i += 1; cmd_start = False; continue
            stack.append("bq"); i += 1; cmd_start = True; pending_cont = False; continue
        if text.startswith("$((", i):
            stack.append("arith"); i += 3; cmd_start = False; pending_cont = False; continue
        if text.startswith("$(", i):
            stack.append("cmdsub"); i += 2; cmd_start = True; pending_cont = False; continue
        if text.startswith("${", i):
            stack.append("param"); i += 2; cmd_start = False; pending_cont = False; continue
        if c == "(":
            stack.append("paren"); i += 1; cmd_start = True; pending_cont = False; continue
        if c == ")":
            if top in ("paren", "cmdsub"):
                # `name() { …; }` 函数定义：空括号之后是函数体，{ 仍在命令起点
                fdef = top == "paren" and i > 0 and text[i - 1] == "("
                stack.pop(); i += 1; cmd_start = fdef; continue
            if top == "case":
                i += 1; cmd_start = True; continue   # case 的模式结束符
            return None, "右括号多了"
        if text.startswith("&&", i):
            if not stack:
                seg = text[start:i].strip()
                if not seg:
                    return None, "空段"
                segs.append(seg)
                start = i + 2
                pending_cont = True
            i += 2; cmd_start = True; continue
        if text.startswith("||", i):
            if not stack:
                return None, "顶层有 ||（&& 链的语义被改写）"
            i += 2; cmd_start = True; continue
        if text.startswith(";;", i):
            if not stack:
                return None, "顶层有 ;;"
            i += 2; cmd_start = True; continue
        if c == ";":
            if not stack:
                return None, "顶层有 ; 分隔的多条命令"
            i += 1; cmd_start = True; continue
        if c == "|":
            if not stack:
                pending_cont = True
            i += 1; cmd_start = True; continue
        if c == "&":
            if text.startswith("&>", i):
                i += 2; continue
            if i > 0 and text[i - 1] in "<>":
                i += 1; continue           # 2>&1 之类
            if not stack:
                return None, "顶层有 & 后台命令"
            i += 1; cmd_start = True; continue
        if text.startswith("<<", i) and not text.startswith("<<<", i):
            return None, "有 heredoc"
        # ── 普通字 ──
        m = re.match(r"[^\s;&|()<>'\"`$\\]+", text[i:])
        word = m.group(0) if m else text[i]
        if cmd_start:
            if word in _KW_OPEN:
                stack.append(_KW_OPEN[word]); i += len(word); cmd_start = True if word != "case" else False
                pending_cont = False
                continue
            if word in _KW_CLOSE:
                if top != _KW_CLOSE[word]:
                    return None, f"{word} 与块不匹配"
                stack.pop(); i += len(word); cmd_start = False; continue
            if word == "{":
                stack.append("brace"); i += 1; cmd_start = True; pending_cont = False; continue
            if word == "}":
                if top != "brace":
                    return None, "} 与 { 不匹配"
                stack.pop(); i += 1; cmd_start = False; continue
            if word == "[[":
                stack.append("dbrack"); i += 2; cmd_start = False; pending_cont = False; continue
            if word in _KW_KEEP_CMD:
                i += len(word); continue
        if top == "dbrack" and word == "]]":
            stack.pop(); i += 2; cmd_start = False; continue
        if top == "case" and word == "in":
            i += 2; cmd_start = True; continue
        i += max(1, len(word))
        # 赋值（X=… cmd）之后仍在命令起点
        cmd_start = bool(re.match(r"^[A-Za-z_][A-Za-z0-9_]*=", word)) and cmd_start
        pending_cont = False
    if stack:
        return None, f"块没闭合（{stack[-1]}）"
    seg = text[start:].strip()
    if not seg:
        return None, "结尾是 &&"
    segs.append(seg)
    return segs, ""


def instrument(run, exempt):
    """把切好的段逐段包起来：登记段红了 → 记账后继续；其它段红了 → 按原退出码立即退出。"""
    segs, why = split_top_and(run)
    if segs is None:
        return None, why
    ex = "," + ",".join(str(s) for s in sorted(exempt)) + ","
    out = [
        "# known-red 按段插桩（accept-run.py）：登记段 " + ex.strip(",") + "；其余段红了立即失败",
        f'__KR_SEGS="{ex}"',
        '__kr_fail() { local rc="$1" seg="$2"',
        '  if [[ "${__KR_SEGS}" == *",${seg},"* ]]; then',
        '    printf \'%s %s\\n\' "${seg}" "${rc}" >> "${ACC_KR_LOG:-/dev/null}"',
        '    echo "[known-red] 第 ${seg} 段红（exit ${rc}），在登记的豁免段里 → 接着跑后面的段" >&2',
        '    return 0',
        '  fi',
        '  printf \'%s\\n\' "${seg}" > "${ACC_KR_FAILSEG:-/dev/null}"',
        '  echo "[known-red] 第 ${seg} 段红（exit ${rc}），不在登记的豁免段里" >&2',
        '  exit "${rc}"',
        '}',
    ]
    for k, s in enumerate(segs, start=1):
        out.append(f"# ── 第 {k} 段")
        out.append("{ " + s + "\n} || __kr_fail $? " + str(k))
    return "\n".join(out), ""


def run_all(items, timeout, json_path=None, logdir=None, known_red=None):
    env = dict(os.environ)
    results = []
    ok_n = 0
    if logdir:
        os.makedirs(logdir, exist_ok=True)
    for it in items:
        assert "path" in it, "先 emit 再 run"
        log = os.path.join(logdir, os.path.basename(it["path"]) + ".log") if logdir else None
        kr = (known_red or {}).get((it["ticket"], it["index"]))
        run_env = env
        kr_log = kr_failseg = None
        if kr and it.get("kr_instrumented"):
            base = (log or it["path"]) + ".kr"
            kr_log, kr_failseg = base + ".exempt", base + ".failseg"
            for fp in (kr_log, kr_failseg):
                try:
                    os.remove(fp)
                except FileNotFoundError:
                    pass
            run_env = dict(env, ACC_KR_LOG=kr_log, ACC_KR_FAILSEG=kr_failseg)
        t0 = time.time()
        with open(log, "w", encoding="utf-8") as lf:
            lf.write(f"# {it['ticket']} accept[{it['index']}] {it['name']}\n")
            if it["nf"]:
                lf.write(f"# 归一化 {','.join(it['nf'])}\n")
            lf.write(f"# LQG_VERIFY_ENV_FILE={env.get('LQG_VERIFY_ENV_FILE', '(未设)')}\n\n")
            lf.flush()
            try:
                cp = subprocess.run(["bash", it["path"]], cwd=ROOT, env=run_env,
                                    stdout=lf, stderr=subprocess.STDOUT, timeout=timeout)
                rc = cp.returncode
            except subprocess.TimeoutExpired:
                rc = 124
                lf.write(f"\n[gate] 超时 {timeout}s\n")
        dt = round(time.time() - t0, 1)
        rec = {"ticket": it["ticket"], "index": it["index"], "form": it["form"],
               "name": it["name"], "exit": rc, "seconds": dt,
               "nf": it["nf"], "path": it["path"], "log": log}
        exempt_hits, failseg = [], None
        if kr_log and os.path.exists(kr_log):
            with open(kr_log, encoding="utf-8") as ef:
                for ln in ef:
                    parts = ln.split()
                    if len(parts) == 2:
                        exempt_hits.append({"segment": int(parts[0]), "exit": int(parts[1])})
        if kr_failseg and os.path.exists(kr_failseg):
            with open(kr_failseg, encoding="utf-8") as ff:
                failseg = (ff.read().strip() or None)
        if failseg:
            rec["failed_segment"] = int(failseg)
        if kr and it.get("kr_unsplittable"):
            rec["known_red_not_applied"] = f"run 切不了段：{it['kr_unsplittable']}"
        if rc == 0 and exempt_hits:
            # 只有登记段红了、其余段全部照跑且全绿 → 已登记的非产品缺陷（issue / 理由只取真红了的那几段）
            hit = []
            for h in exempt_hits:
                b = kr["by_segment"].get(h["segment"]) or {"issue": kr["issue"], "reason": kr["reason"]}
                if b not in hit:
                    hit.append(b)
            rec["known_red"] = {
                "issue": ",".join(dict.fromkeys(x for b in hit for x in b["issue"].split(","))),
                "reason": "；".join(b["reason"] for b in hit),
                "segments": kr["segments"], "segments_failed": exempt_hits,
            }
        results.append(rec)
        if rc == 0 and not exempt_hits:
            ok_n += 1
            hint = (f"（登记的豁免段 {kr['segments']} 这次是绿的 → 可以撤掉 known-red）"
                    if kr and it.get("kr_instrumented") else "")
            print(f"  \033[32m✓\033[0m {it['ticket']} acc{it['index']} [{it['form']}] {it['name'][:64]} ({dt}s){hint}")
        elif rc == 0:
            segs_txt = "、".join(f"第 {h['segment']} 段(exit {h['exit']})" for h in exempt_hits)
            krr = rec["known_red"]
            print(f"  \033[33m▲\033[0m {it['ticket']} acc{it['index']} [{it['form']}] {it['name'][:64]} "
                  f"→ {segs_txt} 红，是**已登记的非产品缺陷**（issue {krr['issue']}，登记段 {kr['segments']}）："
                  f"{krr['reason']}；其余段已照跑且全绿｜日志 {log}")
        else:
            extra = ""
            if failseg:
                extra = f"，断在第 {failseg} 段" + (f"（不在登记的豁免段 {kr['segments']} 里）" if kr else "")
            elif kr and it.get("kr_unsplittable"):
                extra = f"（登记了 known-red 但 run 切不了段：{it['kr_unsplittable']} → 豁免不生效）"
            print(f"  \033[31m✗\033[0m {it['ticket']} acc{it['index']} [{it['form']}] {it['name'][:64]} "
                  f"→ exit {rc} ({dt}s){extra} 日志 {log}")
    known = [r for r in results if r.get("known_red")]
    unknown = [r for r in results if r["exit"] != 0]
    failed = [r for r in results if r["exit"] != 0 or r.get("known_red")]
    out = {"total": len(results), "passed": ok_n, "failed": failed,
           "known_red": known, "unknown_failed": unknown, "results": results}
    if json_path:
        os.makedirs(os.path.dirname(os.path.abspath(json_path)), exist_ok=True)
        with open(json_path, "w", encoding="utf-8") as f:
            json.dump(out, f, ensure_ascii=False, indent=1)
        print(f"[ok] 结果落盘 {json_path}")
    return out


def main():
    ap = argparse.ArgumentParser(description="票面 accept 重放器（模式 B）")
    g = ap.add_mutually_exclusive_group(required=False)
    g.add_argument("--phase", help="任务号，如 D5")
    g.add_argument("--ticket", help="单票，如 CRYO-FLOW-001")
    ap.add_argument("--meta", action="store_true", help="只打印该任务的票/前端/DDL 表清单（JSON）")
    ap.add_argument("--emit", metavar="DIR", help="把 accept 落成 shell 脚本到 DIR")
    ap.add_argument("--run", action="store_true", help="执行（必须先 --emit，或用 --json 触发落盘）")
    ap.add_argument("--json", help="结果 JSON 路径（给了就顺带落盘）")
    ap.add_argument("--logdir", help="每条 accept 的 stdout 日志目录")
    ap.add_argument("--timeout", type=int, default=900, help="单条 accept 超时秒数（默认 900）")
    ap.add_argument("--only", help="只跑某个 ticket（ticket 号）")
    ap.add_argument("--summary", metavar="ACCEPT_JSON",
                    help="只读一份已落的 accept.json，打印 `passed known unknown`（给 gate.sh 用，避免 shell 里嵌 python）")
    ap.add_argument("--known-list", metavar="ACCEPT_JSON",
                    help="只读一份已落的 accept.json，打印 known-red 的一行摘要")
    ap.add_argument("--include-escalated", action="store_true",
                    help="默认跳过状态为 escalated 的 ticket（没实现，重放只会得到假红）")
    ap.add_argument("--known-red", help="已登记的「非产品缺陷红」清单（TICKET|accN@段号|issue|理由）")
    ap.add_argument("--segments", action="store_true",
                    help="只打印每条 accept 按顶层 && 切出的段（登记 known-red 时对段号用）")
    a = ap.parse_args()

    if a.summary or a.known_list:
        try:
            d = json.load(open(a.summary or a.known_list, encoding="utf-8"))
        except Exception as e:
            print(f"[error] 读不了 {a.summary or a.known_list}: {e}", file=sys.stderr)
            return 2
        if a.summary:
            print(d.get("passed", 0), len(d.get("known_red") or []), len(d.get("unknown_failed") or []))
        else:
            print("; ".join(
                f'{r["ticket"]} acc{r["index"]}(issue {r["known_red"]["issue"]})'
                for r in (d.get("known_red") or [])))
        return 0

    if not a.phase and not a.ticket:
        raise SystemExit("[error] 要么给 --phase/--ticket，要么用 --summary/--known-list")
    tickets = load_tickets(a.phase, a.ticket)
    esc = escalated_tickets()
    if not a.include_escalated:
        skipped = [t["ticket"] for t in tickets if t["ticket"] in esc]
        tickets = [t for t in tickets if t["ticket"] not in esc]
        if skipped and not a.meta:
            print(f"[skip] {len(skipped)} 张 escalated 的票不重放（没实现，重放只会得到假红）："
                  f"{', '.join(skipped)}（要显式重放加 --include-escalated）")
    if a.meta:
        print(json.dumps(meta(tickets), ensure_ascii=False, indent=1))
        return 0

    items = scripts(tickets)
    if a.only:
        items = [it for it in items if it["ticket"] == a.only]
        if not items:
            raise SystemExit(f"[error] --only {a.only} 没有匹配的 accept")

    if a.segments:
        for it in items:
            segs, why = split_top_and(it["run"])
            print(f"── {it['ticket']} acc{it['index']}：" + (f"{len(segs)} 段" if segs else f"切不了段（{why}）"))
            for k, s in enumerate(segs or [], start=1):
                one = " ".join(s.split())
                print(f"   {k:>2}. {one[:160]}{'…' if len(one) > 160 else ''}")
        return 0

    kr = load_known_red(a.known_red)
    if a.emit:
        paths = emit(items, a.emit, kr)
        print(f"[ok] 落 {len(paths)} 条 accept 脚本 → {a.emit}")
        for p in paths:
            print(f"     {os.path.relpath(p, ROOT)}")
    if a.run:
        if not a.emit:
            d = (a.json and os.path.dirname(os.path.abspath(a.json))) or os.path.join(ROOT, ".tmp", "gate", "accepts")
            emit(items, d, kr)
        print(f"[run] {len(items)} 条 accept，单条超时 {a.timeout}s")
        out = run_all(items, a.timeout, a.json, a.logdir, kr)
        print(f"[run] 通过 {out['passed']}/{out['total']}"
              + (f"；另有 {len(out['known_red'])} 条已登记的**非产品缺陷红**"
                 f"（issue {'/'.join(r['known_red']['issue'] for r in out['known_red'])}）"
                 if out["known_red"] else ""))
        if out["unknown_failed"]:
            print(f"[run] ⛔ {len(out['unknown_failed'])} 条未登记的红（= 产品断言不成立）："
                  + "; ".join(f"{r['ticket']} acc{r['index']}" for r in out["unknown_failed"]))
            return 1
        return 0
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except KeyboardInterrupt:
        sys.exit(130)
