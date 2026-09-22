#!/usr/bin/env bash
# 票面 accept（逐字重放）：QC-MODEL-001 accept[1] form=DDL
# 五张表与 SSOT 逐列相符、出自本票 Flyway；每个样本每种文档至多一份（部分唯一索引）
set -euo pipefail
python3 doc/verify/ddl_vs_ssot.py --table t_lqg_qc_sample --table t_lqg_qc_organoid --table t_lqg_qc_score --table t_lqg_doc_image --table t_lqg_doc_attachment --require-public create_dept,create_by,create_time,update_by,update_time,del_flag &&
python3 doc/verify/db.py --sql "SELECT count(*) FROM flyway_schema_history WHERE success AND script LIKE 'V2026092613%__QC-MODEL-001-%'" --eq 2 &&
python3 doc/verify/db.py --sql "SELECT data_type FROM information_schema.columns WHERE table_name='t_lqg_qc_organoid' AND column_name IN ('formed_time','feedback_time')" --col-set "character varying"
