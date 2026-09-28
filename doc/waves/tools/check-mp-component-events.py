#!/usr/bin/env python3
"""小程序编译产物的**自定义组件事件**自检（2026-09-28 加）。

起因（Kevin 报的 bug）：小程序里「首页四个板块」和「我的页每一行」都点不动，而 H5 上正常。
根因：源码里写 `<EntryTile @click="…" />` / `<MeRow @click="…" />` —— **在自定义组件上写 `@click`**。
  · H5：Vue 3 把它当原生事件挂到组件根 DOM 上（attrs 兜底）→ 能点；
  · 小程序：编译成自定义组件标签的 `bindclick`，只有当**子组件自己 triggerEvent('click')** 时才触发。
子组件既没声明 emits 也不 emit → 小程序里点了毫无反应，且**编译不报错、H5 也看不出来**。

这条检查直接看**编译产物**（权威），把这类错变成机器可判定：
  1. 对每个 .wxml，读同目录同名 .json 的 usingComponents（tag → 组件路径）——这是官方的标签映射；
  2. 找出 wxml 里「标签属于 usingComponents」且带 `bind<事件>=` 的地方；
  3. 断言对应组件的 .js 里含 `triggerEvent("<事件>"`（uni 把 emit('x') 编译成 triggerEvent('x'）。

用法：
    python3 doc/waves/tools/check-mp-component-events.py [产物目录]
默认产物目录 code/miniapp/dist/build/mp-weixin-test。

退出码：0 = 全过；1 = 有「绑了但子组件不触发」的事件（就是 Kevin 那个 bug）。
"""
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
ART = Path(sys.argv[1]) if len(sys.argv) > 1 else ROOT / "code/miniapp/dist/build/mp-weixin-test"
# ★ macOS 上 /tmp → /private/tmp 是软链，后面用 resolve() 解析组件路径，两边都要规范化，否则 relative_to 会炸
ART = ART.resolve()

if not ART.is_dir():
    print(f"[error] 产物目录不存在：{ART}\n        先构建：cd code/miniapp && pnpm build:mp-weixin")
    sys.exit(2)

# 三方组件库（wot-design-uni 自己会 emit）也要查 —— 万一把事件绑到它不 emit 的名字上，同样是死键。
violations = []
checked = 0


def _rel(p):
    """尽量给相对路径（跨软链时退回绝对路径，别让报告本身炸掉）。"""
    try:
        return str(Path(p).resolve().relative_to(ART))
    except Exception:
        return str(p)

wxmls = sorted(ART.rglob("*.wxml"))
for wxml in wxmls:
    rel = str(wxml.relative_to(ART))
    if rel.startswith("node-modules/"):
        continue          # 第三方组件库（wot-design-uni）自己那一套事件机制不在本判据职责内
    cfg = wxml.with_suffix(".json")
    if not cfg.is_file():
        continue
    try:
        using = json.loads(cfg.read_text(encoding="utf-8")).get("usingComponents") or {}
    except Exception:
        continue
    if not using:
        continue
    text = wxml.read_text(encoding="utf-8", errors="ignore")
    # 逐个自定义标签找 bind<事件>
    for tag, target in using.items():
        for m in re.finditer(rf"<{re.escape(tag)}\b([^>]*)>", text):
            attrs = m.group(1)
            for ev in re.findall(r"\bbind:?([a-zA-Z][a-zA-Z0-9_-]*)\s*=", attrs):
                if ev in ("__l",):          # uni 内部生命周期，不是业务事件
                    continue
                checked += 1
                # 解析组件 JS：usingComponents 的值是相对该 wxml 目录的路径
                if "node-modules" in target or "wot-design-uni" in target:
                    continue      # 第三方组件库不做本判据的对象（它有自己的事件实现，且我改不了它）
                js = (wxml.parent / target).resolve()
                if js.suffix != ".js":
                    js = js.with_suffix(".js")
                if not js.is_file():
                    violations.append((_rel(wxml), tag, ev, f"找不到组件 JS：{target}"))
                    continue
                src = js.read_text(encoding="utf-8", errors="ignore")
                # uni/vue 把 emit('x') 编成本地调用，形如 `c("x", payload)`（不是 triggerEvent ——
                # 那是运行时的事，全在 common/vendor.js 里）。所以判据是：**该组件自己的 .js 里
                # 有没有以这个事件名调用的 emit**。v-model 的事件名带冒号（update:modelValue），
                # 而属性和触发点写法不同，两边都去掉冒号和小写后再比。
                # 事件名的三种写法都要当成同一个：`click` / `update:modelValue`（v-model）/
                # `row-tap`（kebab，父组件写 @row-tap 会编成 bindrowTap）→ 统一去掉 : 和 - 再比。
                def _norm(x):
                    return x.replace(":", "").replace("-", "").lower()
                called = {_norm(n)
                          for n in re.findall(r"""[A-Za-z_$][\w$]*\(\s*["']([^"']+)["']""", src)}
                if _norm(ev) not in called:
                    violations.append((_rel(wxml), tag, ev, _rel(js)))

print(f"产物：{ART}")
print(f"扫描 {len(wxmls)} 个 wxml，检查 {checked} 处「自定义组件 + 事件绑定」")
if not violations:
    print("结果：PASS —— 每一处绑定的事件，子组件都会 triggerEvent")
    sys.exit(0)

print(f"\n✗ 发现 {len(violations)} 处死键（绑了事件，但子组件从不 triggerEvent）—— 小程序里点了没反应：")
for wxml, tag, ev, js in violations:
    print(f"  · {wxml}：<{tag}> 绑了 bind{ev}，但 {js} 里没有 triggerEvent(\"{ev}\")")
print("\n修法：在子组件里 `const emit = defineEmits<{ (e: 'click', ev: unknown): void }>()`，")
print("      并给根节点加 `@click=\"emit('click', $event)\"`（声明 emits 后 Vue 不再挂原生兜底，H5 也只触发一次）。")
sys.exit(1)
