"""Prepare markdown for pandoc → docx. Idempotent, edits files in place.

1. Paired straight double quotes on a line become “ ” (pandoc's smart quotes
   guess the direction from surrounding spaces, which fails inside CJK text).
   Lines with an odd number of quotes are left alone and reported.
2. Pipe-table separator rows are re-weighted by content width so pandoc gives
   wide columns to wide content (pandoc uses the dash counts as relative widths
   whenever a table line exceeds --columns).

Usage: python3 md_prep.py <file.md> [...]
"""
import re
import sys
import unicodedata
from pathlib import Path

SEP_RE = re.compile(r"^\s*\|?\s*:?-{2,}:?\s*(\|\s*:?-{2,}:?\s*)*\|?\s*$")
ROW_RE = re.compile(r"^\s*\|.*\|\s*$")
MAX_W = 40


def cells(line: str):
    s = line.strip()
    if s.startswith("|"):
        s = s[1:]
    if s.endswith("|"):
        s = s[:-1]
    return [c.strip() for c in s.split("|")]


def width(text: str) -> int:
    return sum(2 if unicodedata.east_asian_width(ch) in ("W", "F") else 1 for ch in text)


def fix_quotes(line: str):
    if line.count('"') % 2:
        return line, True
    out, opening = [], True
    for ch in line:
        if ch == '"':
            out.append("“" if opening else "”")
            opening = not opening
        else:
            out.append(ch)
    return "".join(out), False


def reweight_tables(lines):
    i, n = 0, len(lines)
    while i < n:
        if SEP_RE.match(lines[i]) and i > 0 and ROW_RE.match(lines[i - 1]):
            header, seps = cells(lines[i - 1]), cells(lines[i])
            ncol = len(seps)
            maxw = [width(h) for h in header[:ncol]] + [0] * max(0, ncol - len(header))
            j = i + 1
            while j < n and ROW_RE.match(lines[j]):
                for k, c in enumerate(cells(lines[j])[:ncol]):
                    maxw[k] = max(maxw[k], width(c))
                j += 1
            new = []
            for s, w in zip(seps, maxw):
                # Floors keep short columns from wrapping mid-word: a 1–2 character
                # column ("#", "二期") needs less than an ID-length one ("AUTH-001").
                floor = 8 if w <= 4 else 14
                w = min(max(w, floor), MAX_W) + 4
                left, right = s.startswith(":"), s.endswith(":")
                dashes = "-" * max(w - int(left) - int(right), 3)
                new.append((":" if left else "") + dashes + (":" if right else ""))
            lines[i] = "|" + "|".join(new) + "|"
            i = j
        else:
            i += 1
    return lines


def main() -> None:
    for arg in sys.argv[1:]:
        path = Path(arg)
        text = path.read_text(encoding="utf-8")
        lines = text.split("\n")
        in_front_matter = False
        odd = []
        for idx, line in enumerate(lines):
            if idx == 0 and line.strip() == "---":
                in_front_matter = True
                continue
            if in_front_matter:
                if line.strip() == "---":
                    in_front_matter = False
                continue
            fixed, is_odd = fix_quotes(line)
            lines[idx] = fixed
            if is_odd and '"' in line:
                odd.append(idx + 1)
        lines = reweight_tables(lines)
        new_text = "\n".join(lines)
        if new_text != text:
            path.write_text(new_text, encoding="utf-8")
        status = f"odd-quote lines: {odd}" if odd else "ok"
        print(f"{path.name}: {status}")


if __name__ == "__main__":
    main()
