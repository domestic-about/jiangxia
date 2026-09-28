#!/usr/bin/env python3
"""数一个导出 xlsx 的数据行数（不含表头）。"""
import sys

import openpyxl

ws = openpyxl.load_workbook(sys.argv[1], read_only=True, data_only=True).worksheets[0]
rows = list(ws.iter_rows(values_only=True))
print(len([r for r in rows[1:] if any(c not in (None, "") for c in r)]))
