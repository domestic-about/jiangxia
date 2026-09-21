---
ticket: SAMPLE-MODEL-001
track: SAMPLE
phase: D2
size: M
req_refs:
  - REQ-SAMPLE-001
  - REQ-SAMPLE-007
  - REQ-SAMPLE-008
  - REQ-SAMPLE-005
  - REQ-SAMPLE-901
  - REQ-SYS-008
depends_on:
  - SYS-BASE-001
  - AUTH-LOGIN-001
touches:
  - code/RuoYi-Vue-Plus/ruoyi-modules/ruoyi-lqg/src/main/java/org/dromara/lqg/sample/**
  - code/RuoYi-Vue-Plus/ruoyi-modules/ruoyi-lqg/src/test/java/org/dromara/lqg/sample/**
  - code/RuoYi-Vue-Plus/ruoyi-admin/src/main/resources/db/migration/V20260922100*__SAMPLE-MODEL-001-*.sql
adr_refs:
  - ADR-0010
  - ADR-0006
  - ADR-0009
blueprint_refs:
  - FLOW:F-SAMPLE-02.step1
  - FLOW:F-SAMPLE-02.step2
  - FIELD:t_lqg_sample.internal_no
  - FIELD:t_lqg_sample.submit_no
  - FIELD:t_lqg_sample.sample_kind
  - FIELD:t_lqg_sample.submit_source
  - FIELD:t_lqg_sample.donor_name
  - FIELD:t_lqg_sample.hospital_no
  - FIELD:t_lqg_sample.tissue_type
accept:
  - name: "样本表与 SSOT 逐列相符（含公共字段、内部编号与送检单号两个部分唯一索引）、出自本票 Flyway、送检单号序列已建"
    form: DDL
    run: |-
      python3 doc/verify/ddl_vs_ssot.py --table t_lqg_sample --require-public create_dept,create_by,create_time,update_by,update_time,del_flag &&
      python3 doc/verify/db.py --sql "SELECT count(*) FROM flyway_schema_history WHERE success AND script LIKE 'V20260922100%__SAMPLE-MODEL-001-%'" --eq 1 &&
      grep -qi 'CREATE TABLE t_lqg_sample' code/RuoYi-Vue-Plus/ruoyi-admin/src/main/resources/db/migration/V20260922100*__SAMPLE-MODEL-001-*.sql &&
      python3 doc/verify/db.py --sql "SELECT count(*) FROM pg_sequences WHERE schemaname='public' AND sequencename='seq_lqg_submit_no'" --eq 1
    counterfeit: |-
      照两份模板建了 t_lqg_sample 和 t_lqg_organoid_receive 两张表 → 第一张少 organoid_type 列，ddl_vs_ssot 红。
      内部编号建成 NOT NULL → 红：外部刚提交、还没核验的样本没有内部编号，INSERT 直接炸。
      内部编号唯一索引不带 WHERE del_flag='0' → 红：误删重录同一个编号会永远报重复。
      送检单号用 max()+1 取号、没建序列 → 第 4 段红；两个人同时提交会撞号。
  - name: "写入时真的加了密、读出时真的解了密（库里的密文与 openssl 独立算出的一致）；加密列只能精确查；软删的样本任何条件都查不到"
    form: DATA
    run: |-
      bash doc/verify/reseed.sh --yes >/dev/null &&
      NEW="$(bash doc/verify/api.sh --as staff --fresh-module ruoyi-lqg POST /lqg/sample '{"sampleKind":"tissue","sourceUnitName":"本中心","donorName":"加密探针","gender":"male","age":"50","hospitalNo":"ZYPROBE01","tissueType":"肝组织","receiveDate":"2026-09-17","internalNo":"T-probe01"}')" &&
      printf '%s' "${NEW}" | jq -e '.code==200' &&
      WANT="$(printf '%s' '加密探针' | openssl enc -aes-128-ecb -K 4c7167546573744165734b6579233031 -nosalt -base64 -A)" &&
      python3 doc/verify/db.py --sql "SELECT donor_name FROM t_lqg_sample WHERE internal_no='T-probe01'" --eq "${WANT}" &&
      bash doc/verify/api.sh --as staff GET '/lqg/sample/list?internalNo=T-probe01' | jq -e '.rows|length==1 and .[0].donorName=="加密探针" and .[0].hospitalNo=="ZYPROBE01" and .[0].submitSource=="internal" and .[0].verifyStatus=="valid" and (.[0].submitNo|test("^SJ[0-9]{8}$"))' &&
      bash doc/verify/api.sh --as staff GET '/lqg/sample/list?donorName=%E6%B5%8B%E8%AF%95%E4%BE%9B%E4%BD%93%E7%94%B2' | jq -e '[.rows[].id|tostring] == ["9000001001"]' &&
      bash doc/verify/api.sh --as staff GET '/lqg/sample/list?donorName=%E6%B5%8B%E8%AF%95%E4%BE%9B%E4%BD%93' | jq -e '.rows|length==0' &&
      bash doc/verify/api.sh --as staff GET '/lqg/sample/list?pageSize=100' | jq -e '([.rows[].id|tostring] | index("9000001010")) == null and (.rows|length) == 10' &&
      bash doc/verify/reseed.sh --yes >/dev/null
    counterfeit: |-
      实体上漏了 @EncryptField → 库里存的是明文「加密探针」，与 openssl 算出的密文不等红。两侧不同源：一侧是应用写进库的值，一侧是命令行工具独立算的。
      加了注解但 dev 没开 mybatis-encryptor → 同上红。
      精确查询没先加密查询值 → 按「测试供体甲」查不到 1001 红。
      为了「好用」给加密列做了 LIKE（在内存里解密后过滤）→ 按「测试供体」能查出一堆，倒数第 3 段红——口径是只支持精确匹配，做了模糊等于全表解密。
      列表没过滤软删（手写 SQL 绕过了 @TableLogic）→ 1010 出现红；行数 10 = seed 的 9 条有效 + 刚建的 1 条。
  - name: "内部编号全库唯一但软删后可重用、重复时拒绝且不落库；内部新增必填项缺失被拒；联想词来自字典"
    form: STATE
    run: |-
      bash doc/verify/reseed.sh --yes >/dev/null &&
      bash doc/verify/api.sh --as staff --fresh-module ruoyi-lqg --bizcode POST /lqg/sample '{"sampleKind":"tissue","sourceUnitName":"本中心","tissueType":"肝组织","receiveDate":"2026-09-17","internalNo":"T-hli01"}' | grep -qE '^(400|500)' &&
      python3 doc/verify/db.py --sql "SELECT count(*) FROM t_lqg_sample WHERE internal_no='T-hli01'" --eq 1 &&
      bash doc/verify/api.sh --as staff POST /lqg/sample '{"sampleKind":"tissue","sourceUnitName":"本中心","tissueType":"肝组织","receiveDate":"2026-09-17","internalNo":"T-del99"}' | jq -e '.code==200' &&
      python3 doc/verify/db.py --sql "SELECT del_flag FROM t_lqg_sample WHERE internal_no='T-del99'" --col-set 0,1 &&
      bash doc/verify/api.sh --as staff --bizcode POST /lqg/sample '{"sampleKind":"organoid","sourceUnitName":"本中心","receiveDate":"2026-09-17","internalNo":"T-oco77"}' | grep -qE '^(400|500)' &&
      python3 doc/verify/db.py --sql "SELECT count(*) FROM t_lqg_sample WHERE internal_no='T-oco77'" --eq 0 &&
      bash doc/verify/api.sh --as extA GET '/mp/dict/hints?type=tissue' | jq -e '.code==200 and (.data|index("肝组织")!=null) and (.data|index("测试供体甲")==null)' &&
      bash doc/verify/reseed.sh --yes >/dev/null
    counterfeit: |-
      唯一性只靠应用层先查后插、查询时没带 del_flag='0' → 重用软删编号 T-del99 被误拒，第 4 段红。
      重复编号时抛了异常但事务没回滚（取了号、插了半条）→ 第 3 段计数 2 红。每个「被拒」后面都跟库内断言：只看业务码的话，先落盘再报错也是绿的。
      类器官类缺「类器官类型」也放行 → 倒数第 3、4 段红。
      联想词偷懒用 SELECT DISTINCT tissue_type FROM t_lqg_sample → 外部能看到别的单位填过的东西；这里断的是来源于字典（「肝组织」在字典 seed 里）且不混入任何样本数据。
---

# SAMPLE-MODEL-001 · 样本主档：一张表承载两种收样记录、送检单号序列、三个联想词接口、供体字段加密、内部增删改查接口

## §0 状态自检（实施前必过，不过就 STOP 报 Kevin）

- [ ] 当前分支符合调度器注入的期望（`track/SAMPLE` worktree）——不写死 `feature/dayN`
- [ ] `depends_on` 全部 done：**SYS-BASE-001**、**AUTH-LOGIN-001**
- [ ] 扫 `doc/change-log.md`：涉及本 ticket 的 CR **以 CR 为准**（CR 覆盖本文）
- [ ] 逐条取权威（别全文读蓝图）：`python3 ~/claude-config/skills/xuqiu/scripts/authority_lint.py show <锚>`，锚见上方 `blueprint_refs`；接口形状见 `doc/api-contract.md`
- [ ] 必读：
  - **ADR-0010**：样本主档一张表同时承载「样本记录信息表」与「类器官收样记录」；内部编号只存在这张表上，全库唯一，待核验的外部样本可为空
  - **ADR-0006（proposed）**：供体姓名、住院号用 `@EncryptField` 加密落库；只支持精确查询。Kevin 若否决，去掉两个注解即可，表结构不变
  - 栈包 gotchas §9.1：VO 上只写 `@AutoMapper(target = 实体.class)`，**不加 `reverseConvertGenerate = false`**（会静默关掉实体→VO，接口通了但返回体是空的）
  - **ADR-0009**（全文在 `doc/_adr/`，`authority_lint.py show ADR-0009` 取结构化口径）
- [ ] 口径复述（本张最容易做反的）：
  1. **别照两份模板建两张表**。`sample_kind` 区分 tissue / organoid；「类器官类型」只是 organoid 类才填的一列。
  2. **内部编号手填，不自动生成**（编号规则是甲方自己的，合同约定由甲方提供）。系统只做：唯一性校验、软删后可重用。
  3. **加密列查询要自己加密查询值**：`@EncryptField` 只管实体读写，`LambdaQueryWrapper.eq(donorName, 明文)` 查不到任何东西——要先 `EncryptUtils.encryptByAes(明文, key)` 再 eq。
  4. 联想词**走字典不走历史值**：外部用户会从历史值联想里看到别的单位填过的内容。

## 1 背景与口径

模板 A：样本记录信息表 14 列（REQ-SAMPLE-001）；模板 B：类器官收样记录 7 列（REQ-SAMPLE-007）。甲方同时说「内部编号贯穿到底」（REQ-SAMPLE-008）、
「外部人员填写的相关信息实时同步到该表」（REQ-SAMPLE-005）、「所有样本一张表」——只有「一个样本一行、外部提交直接写这一行」时三句话同时成立。
本张只做模型与**内部**接口；外部接口在 AUTH-EXT-001，核验状态机在 SAMPLE-VERIFY-001。

## 2 实现要点

### 2.1 DDL（`V202609221000__SAMPLE-MODEL-001-sample.sql`）
- `python3 doc/tools/gen_ddl_pg.py --migration V202609221000__SAMPLE-MODEL-001-sample.sql` 的输出（含序列 `seq_lqg_submit_no`）。
### 2.2 后端（包 `org.dromara.lqg.sample`，权限串 `lqg:sample:{list,query,add,edit,remove}`）
- 实体字段逐列照 SSOT；`donorName`、`hospitalNo` 加 `@EncryptField(algorithm = AlgorithmType.AES)`。
- `submit_no` = `'SJ' || lpad(nextval('seq_lqg_submit_no')::text, 8, '0')`，在 service 层取号（一条原生 SQL 取 nextval），**不用** `SELECT max()+1`。
- `POST /lqg/sample`：内部新增 → `submit_source='internal'`、`verify_status='valid'`、`submitter_id=当前用户`、`verify_by=当前用户`。
  `sample_kind=tissue`：`tissue_type`、`internal_no`、`receive_date` 必填；`organoid`：`organoid_type`、`internal_no`、`receive_date` 必填。`source_unit_id` 有值时 `source_unit_name` 取单位表的名称快照，否则用请求里的名称。
- `PUT /lqg/sample`：内部改任意字段（`submit_source`、`submitter_id`、`submit_no` 不可改）。`DELETE`：软删。
  详情 VO 带 `updateByName`（`update_by` 对应的用户姓名）与 `updateTime`：两端的修改页都显示「最后修改：某某 · 时间」（REQ-SAMPLE-016，CR-20260917-04）。
- `GET /lqg/sample/{id}`、`GET /lqg/sample/list`：本张先支持 `sampleKind / verifyStatus / internalNo / donorName（精确）/ hospitalNo（精确）` 五个条件，其余筛选在 SAMPLE-WEB-001 补；`del_flag='1'` 的永不返回（`@TableLogic` 自带）。
- `GET /mp/dict/hints?type=tissue|organoid|sample`：返回对应 `lqg_hint_*` 字典的 label 数组；内外部都能调。
- 给后续域留一个扩展点：`SampleChildrenChecker` 接口（`boolean hasChildren(Long sampleId)`），本张里注册零个实现；EMBED / CRYO / QC 建模时各注册一个——SAMPLE-VERIFY-001 的「有下游记录不许改判无效」靠它。

## 3 边界（明确不做）

- 不做外部提交接口（AUTH-EXT-001）、不做核验（SAMPLE-VERIFY-001）、不做工作台页面（SAMPLE-WEB-001）、不做导出（SAMPLE-EXPORT-001）
- 不自动生成内部编号、不解析内部编号的含义
- 「质控表」「细胞活率报告」两个按钮字段就是手点的 Y / N，不按有没有文档自动推导
- 不给历史 Excel 导入预留任何字段（REQ-SYS-012 deferred）

## 4 完工报告要求

1. `SELECT id, donor_name, hospital_no FROM t_lqg_sample WHERE id = <新建的那条>` 的输出（应是密文）与接口返回（应是明文）并排贴
2. **改了哪些文件**（含 Flyway 文件名与取号依据、新增的类 / 页面 / 接口清单）
3. **accept 逐条 ✅ / ❌ + 关键输出**（贴命令输出，不贴「已通过」三个字）
4. **遗留与 raise**：越出 `touches` 的改动、与 `doc/api-contract.md` 不一致的地方、没把握的口径
5. 验证用的后端 / 前端长进程已关，或明示留给谁
