---
ticket: QC-MODEL-001
track: QC
phase: D6
size: M
req_refs:
  - REQ-QC-001
  - REQ-QC-005
  - REQ-QC-007
  - REQ-QC-003
  - REQ-QC-006
  - REQ-QC-010
depends_on:
  - SAMPLE-VERIFY-001
touches:
  - code/RuoYi-Vue-Plus/ruoyi-modules/ruoyi-lqg/src/main/java/org/dromara/lqg/qc/**
  - code/RuoYi-Vue-Plus/ruoyi-modules/ruoyi-lqg/src/test/java/org/dromara/lqg/qc/**
  - code/RuoYi-Vue-Plus/ruoyi-admin/src/main/resources/db/migration/V20260926130*__QC-MODEL-001-*.sql
  - code/RuoYi-Vue-Plus/ruoyi-admin/src/main/resources/db/migration/V20260926131*__QC-MODEL-001-*.sql
adr_refs:
  - ADR-0010
  - ADR-0006
blueprint_refs:
  - FLOW:F-QC-01.step1
  - FLOW:F-QC-01.step2
  - FLOW:F-QC-01.step3
  - FLOW:F-QC-01.step4
  - FIELD:t_lqg_qc_sample.sample_id
  - FIELD:t_lqg_qc_sample.patient_no
  - FIELD:t_lqg_qc_score.total_score
  - FIELD:t_lqg_doc_image.slot
  - FIELD:t_lqg_doc_image.oss_id
  - FIELD:t_lqg_doc_image.preview_oss_id
  - FIELD:t_lqg_doc_attachment.oss_id
accept:
  - name: "五张表与 SSOT 逐列相符、出自本票 Flyway；每个样本每种文档至多一份（部分唯一索引）"
    form: DDL
    run: |-
      python3 doc/verify/ddl_vs_ssot.py --table t_lqg_qc_sample --table t_lqg_qc_organoid --table t_lqg_qc_score --table t_lqg_doc_image --table t_lqg_doc_attachment --require-public create_dept,create_by,create_time,update_by,update_time,del_flag &&
      python3 doc/verify/db.py --sql "SELECT count(*) FROM flyway_schema_history WHERE success AND script LIKE 'V2026092613%__QC-MODEL-001-%'" --eq 2 &&
      python3 doc/verify/db.py --sql "SELECT data_type FROM information_schema.columns WHERE table_name='t_lqg_qc_organoid' AND column_name IN ('formed_time','feedback_time')" --col-set "character varying"
    counterfeit: |-
      把来源单位、患者姓名、内部编号也建进了质控表 → 「库里有、SSOT 没有」红；样本主档改了名字，文档上还是旧的。
      「形成类器官时间」建成 DATE → 第 3 段红：实验员要写「约第 5 天」就写不进去。
      sample_id 上没有部分唯一索引 → ddl_vs_ssot 红；并发首次打开会建出两份草稿。
  - name: "评分由后端按字典回填：前端夹带的假分值不生效、0 分档不被当成未选、没选全合计为空；落库的分值与字典表两侧对得上"
    form: DATA
    run: |-
      bash doc/verify/reseed.sh --yes >/dev/null &&
      bash doc/verify/api.sh --as staff --fresh-module ruoyi-lqg PUT /lqg/qc/9000001005/score '{"preCultureLevel":"gt80","cultureDaysLevel":"gt14","organoidCountLevel":"lt100","diameterLevel":"gt100","preCultureScore":99,"cultureDaysScore":99,"totalScore":100}' | jq -e '.code==200' &&
      python3 doc/verify/db.py --sql "SELECT pre_culture_score || '|' || culture_days_score || '|' || organoid_count_score || '|' || diameter_score || '|' || total_score FROM t_lqg_qc_score WHERE sample_id=9000001005 AND del_flag='0'" --eq "20|0|0|30|50" &&
      python3 doc/verify/db.py --sql "SELECT s.sample_id FROM t_lqg_qc_score s WHERE s.del_flag='0' AND s.total_score IS NOT NULL AND s.total_score <> (SELECT sum(d.remark::int) FROM sys_dict_data d WHERE (d.dict_type, d.dict_value) IN (('lqg_score_pre_culture', s.pre_culture_level), ('lqg_score_culture_days', s.culture_days_level), ('lqg_score_count', s.organoid_count_level), ('lqg_score_diameter', s.diameter_level)))" --empty &&
      python3 doc/verify/db.py --sql "SELECT count(*) FROM t_lqg_qc_score WHERE del_flag='0' AND total_score IS NOT NULL" --eq 3 &&
      bash doc/verify/api.sh --as staff PUT /lqg/qc/9000001005/score '{"preCultureLevel":"gt80","cultureDaysLevel":null,"organoidCountLevel":"lt100","diameterLevel":"gt100"}' | jq -e '.code==200' &&
      python3 doc/verify/db.py --sql "SELECT COALESCE(total_score::text,'NULL') || '|' || COALESCE(culture_days_score::text,'NULL') FROM t_lqg_qc_score WHERE sample_id=9000001005 AND del_flag='0'" --eq "NULL|NULL" &&
      bash doc/verify/api.sh --as staff --bizcode PUT /lqg/qc/9000001005/score '{"preCultureLevel":"gt999"}' | grep -qE '^(400|500)' &&
      bash doc/verify/reseed.sh --yes >/dev/null
    counterfeit: |-
      直接存前端传来的分值 → 第 3 段是 99|99|… 红。
      用 `if (score)` 之类判断「有没有选」→ 0 分档被当成未选，合计变 NULL 红。「培养天数 >14d = 0 分」就是为这个挑的。
      分值写死在 Java 枚举里、和字典 remark 不同源 → 此刻相等，但第 4 段的对账是拿库里落下的分值去和字典表 JOIN：将来有人只改了一边，这条会红。前面第 5 段先断「确实有 3 行参与对账」，--empty 不在零行上空转。
      没选全时合计按 0 补 → 倒数第 3 段红。
  - name: "首次打开幂等建三份草稿且默认文字逐字照模板；图片位规则（归属、每位至多三张）被拒时库里不变；患者编号落库是密文"
    form: STATE
    run: |-
      bash doc/verify/reseed.sh --yes >/dev/null &&
      bash doc/verify/api.sh --as staff --fresh-module ruoyi-lqg GET /lqg/qc/9000001006 | jq -e '.code==200 and .data.sample.internalNo=="T-hco04" and .data.sampleQc.docStatus=="draft" and .data.sampleQc.receiveDesc=="样本按质控要求，保持2-8℃低温环境运输至实验室。" and .data.sampleQc.observeDesc=="样本外观呈黄白色。" and .data.sampleQc.pretreatDesc=="样本经剪切等预处理，显微镜下观察组织漏出细胞量适中，细胞活性中等；培养3d照片如左图所示。" and .data.score.totalScore==18' &&
      bash doc/verify/api.sh --as staff GET /lqg/qc/9000001006 >/dev/null &&
      python3 doc/verify/db.py --sql "SELECT (SELECT count(*) FROM t_lqg_qc_sample WHERE sample_id=9000001006) || '|' || (SELECT count(*) FROM t_lqg_qc_organoid WHERE sample_id=9000001006) || '|' || (SELECT count(*) FROM t_lqg_qc_score WHERE sample_id=9000001006)" --eq "1|1|1" &&
      bash doc/verify/api.sh --as staff --bizcode GET /lqg/qc/9000001002 | grep -qE '^(400|500)' &&
      bash doc/verify/api.sh --as staff POST /lqg/qc/9000001001/sample-qc/image '{"slot":"orig","ossId":9000004005}' | jq -e '.code==200' &&
      bash doc/verify/api.sh --as staff --bizcode POST /lqg/qc/9000001001/sample-qc/image '{"slot":"orig","ossId":9000004006}' | grep -qE '^(400|500)' &&
      bash doc/verify/api.sh --as staff --bizcode POST /lqg/qc/9000001001/sample-qc/image '{"slot":"organoid_observe","ossId":9000004006}' | grep -qE '^(400|500)' &&
      bash doc/verify/api.sh --as staff --bizcode POST /lqg/qc/9000001001/score/image '{"slot":"orig","ossId":9000004006}' | grep -qE '^(400|404|500)' &&
      python3 doc/verify/db.py --sql "SELECT slot || ':' || count(*) FROM t_lqg_doc_image WHERE doc_type='sample_qc' AND doc_id=9000005001 AND del_flag='0' GROUP BY slot" --col-set "orig:3,observe:1" &&
      bash doc/verify/api.sh --as staff PUT /lqg/qc/9000001006/sample-qc '{"patientNo":"P-PROBE","samplingSite":"结肠"}' | jq -e '.code==200' &&
      python3 doc/verify/db.py --sql "SELECT patient_no FROM t_lqg_qc_sample WHERE sample_id=9000001006" --eq "$(printf '%s' 'P-PROBE' | openssl enc -aes-128-ecb -K 4c7167546573744165734b6579233031 -nosalt -base64 -A)" &&
      bash doc/verify/reseed.sh --yes >/dev/null
    counterfeit: |-
      默认文字是凭印象打的（「2~8℃」写成了波浪号、少了句号）→ 第 1 段逐字比对红。甲方拿系统出的文档和他们的旧文档并排看，差一个标点都会问。
      每次 GET 都新建草稿 → 第 3 段计数 2|2|2 红（或者唯一索引报错 500）。
      待核验样本也能开质控文档 → 第 4 段红。
      图片位不校验归属 → organoid_observe 挂到了样本质控表上，集合里多出一行红；不限张数 → orig:4 红。
      患者编号忘了加密注解 → 库里是明文 P-PROBE，与 openssl 独立算出的密文不等红。
  - name: "私有桶下编辑页拿到的图片地址打得开：GET /lqg/qc/{sampleId} 里图片的 url 是带签名的短时链接、直接 GET 为 200，而同一对象去掉签名的原样地址是 403（证明环境确实是私有桶）；公有桶原样给、签不出来照旧给原值由单测钉住（CR-20260924-11）"
    form: API
    run: |-
      bash doc/verify/reseed.sh --yes >/dev/null &&
      bash doc/verify/api.sh --as staff --fresh-module ruoyi-lqg GET /lqg/qc/9000001005 | jq -e '.code==200' &&
      OID="$(bash doc/verify/api.sh --as staff --form 'file=@doc/verify/fixtures/ocr-sample.png' POST /resource/oss/upload | jq -r '.data.ossId')" && test -n "${OID}" && test "${OID}" != null &&
      bash doc/verify/api.sh --as staff POST /lqg/qc/9000001005/sample-qc/image "{\"slot\":\"orig\",\"ossId\":${OID}}" | jq -e '.code==200' &&
      U="$(bash doc/verify/api.sh --as staff GET /lqg/qc/9000001005 | jq -r --arg o "${OID}" '[.data.sampleQc.images.orig[] | select((.ossId|tostring)==$o) | .url][0] // empty')" && test -n "${U}" &&
      case "${U}" in *X-Amz-Signature=*) true ;; *) false ;; esac &&
      test "$(curl -s -o /dev/null -w '%{http_code}' "${U}")" = 200 &&
      test "$(curl -s -o /dev/null -w '%{http_code}' "${U%%\?*}")" = 403 &&
      (cd code/RuoYi-Vue-Plus && mvn -q -pl ruoyi-modules/ruoyi-lqg -am test -Dtest='QcOssUrlsTest' -Dsurefire.failIfNoSpecifiedTests=true) &&
      bash doc/verify/reseed.sh --yes >/dev/null
    counterfeit: |-
      编辑页照旧原样给 `sys_oss.url`（CR-20260924-11 之前的形态）→ 地址里没有 X-Amz-Signature、直接 GET 是 403，两段都红：Kevin 本机验收「网页工作台」第 3 行，缩略图、放大图、附件全部「图片加载失败」，而下载的 Word 里图是好的（渲染走后端读字节，不受影响）。
      不看桶策略、一律签名 → 本条仍绿，但公有桶的行为变了：由 `QcOssUrlsTest`（公有桶原样给、私有桶签名、签不出来给原值不抛、同一个 service 只取一次客户端）钉住。
      环境其实是公有桶（原样地址也能打开）→ 最后那段 403 红：这条要证明的是「私有桶下能看」，公有桶上测不出这个缺陷，先把存储改回私有桶再跑。
      签名时效拉到几个小时、几天（「省得过期」）→ 这里测不到，由 api-contract 与 ADR-0005 的「只发 10 分钟短时签名链接」约束；过期由前端在取回满 5 分钟后重取一次。
---

# QC-MODEL-001 · 三份质控文档的数据模型：每个样本各一份、图片位与附件、评分由后端按字典回填、内部读写接口

## §0 状态自检（实施前必过，不过就 STOP 报 Kevin）

- [ ] 当前分支符合调度器注入的期望（`track/QC` worktree）——不写死 `feature/dayN`
- [ ] `depends_on` 全部 done：**SAMPLE-VERIFY-001**
- [ ] 扫 `doc/change-log.md`：涉及本 ticket 的 CR **以 CR 为准**（CR 覆盖本文）
- [ ] 逐条取权威（别全文读蓝图）：`python3 ~/claude-config/skills/xuqiu/scripts/authority_lint.py show <锚>`，锚见上方 `blueprint_refs`；接口形状见 `doc/api-contract.md`
- [ ] 必读：
  - 甲方三份 Word 模板原件（`_input/templates/*.docx`）与 `_input/01-模板内容整理.md` L41-L84：每份文档有哪些栏、哪些是印死的文字
  - `00-最终想法.md`「明确不做」：**不做录入活率自动判级、不做按总分自动出质量结论**——那两句「注」是文档上的固定文字
  - **ADR-0010**（全文在 `doc/_adr/`，`authority_lint.py show ADR-0010` 取结构化口径）
  - **ADR-0006**（全文在 `doc/_adr/`，`authority_lint.py show ADR-0006` 取结构化口径）
- [ ] 口径复述（本张最容易做反的）：
  1. **来源单位、患者姓名、性别、收样时间、处理时间、操作人、内部编号不在质控表里重复存**，从样本主档带出（只读）。存两份 = 两个真相源。
  2. **评分的分值由后端按字典 remark 回填**，前端传来的分值一律忽略；任一项没选 → 合计为空（不是 0）。注意「培养天数 >14d」是 **0 分**——别把 0 当成没选。
  3. 类器官质控表的五栏都是自由文本（模板没给选项）：`formed_time`、`feedback_time` 是文本列，不是日期列。
  4. 模板里印着的三段示例文字做成新建草稿时的默认值，**逐字**照模板。

## 1 背景与口径

三份 Word：样本质控表（REQ-QC-001，含三个图片位 + 细胞活率测定附件）、类器官质控表（REQ-QC-005，一个图片位 + 五栏文本）、
类器官质量评分表（REQ-QC-007，四个变量 12 档）。图片「要求可以放大」（REQ-QC-003 / 006），还可以添加附件（REQ-QC-010）。
本张只做模型与内部接口；页面在 QC-WEB-001 / 002，渲染在 DOC-RENDER-001，发布状态机在 DOC-PUBLISH-001。

## 2 实现要点

- DDL 两支迁移：`V202609261300__QC-MODEL-001-qc-docs.sql`（三张文档表）、`V202609261310__QC-MODEL-001-doc-assets.sql`（图片、附件）——都用 `gen_ddl_pg.py --migration …` 生成。
- 包 `org.dromara.lqg.qc`，权限 `lqg:qc:{query,edit,publish}`。`patientNo` 加 `@EncryptField`。
- `GET /lqg/qc/{sampleId}`：样本须为有效；三份文档缺哪份建哪份空草稿（幂等，并发下靠 `sample_id` 的部分唯一索引兜底）。新建样本质控表时预填三段模板原文：
  `receive_desc`=「样本按质控要求，保持2-8℃低温环境运输至实验室。」`observe_desc`=「样本外观呈黄白色。」
  `pretreat_desc`=「样本经剪切等预处理，显微镜下观察组织漏出细胞量适中，细胞活性中等；培养3d照片如左图所示。」
  返回体：`sample`（从主档带出的只读字段）+ `sampleQc` / `organoidQc` / `score` + 各自的 `images`（按 slot 分组）与 `attachments`。
  图片的 `url / previewUrl`、附件的 `url` 按这一行 `sys_oss.service` 对应的存储配置给（CR-20260924-11，`qc/service/QcOssUrls`，口径同若依 `SysOssServiceImpl#matchingUrl`）：私有桶给 10 分钟签名链接（有效期与渲染产物同一个 `DocArtifactStore.SIGNED_URL_TTL`），公有桶原样给 `sys_oss.url`，签不出来（seed 的 `service='seed'` 没有配置）照旧给原值、记 WARN、不抛。
- `PUT …/sample-qc`、`…/organoid-qc`：保存字段。`PUT …/score`：只收四个 `*Level`；按字典 remark 回填四个 `*_score`；四项齐 → `total_score` = 和，否则 NULL。
- 图片：`POST …/{docType}/image {slot, ossId}`、`DELETE …/image/{id}`、`PUT …/image/sort`。规则：slot 必须属于该文档类型（`orig / observe / pretreat` ↔ 样本质控表；`organoid_observe` ↔ 类器官质控表；评分表没有图片位）；每个 slot ≤ 3 张。
  预览图：非 jpg / png（TIFF、BMP…）或长边 > 2000px → 生成长边 ≤ 2000px 的 JPEG 另存 OSS，填 `preview_oss_id`；否则 `preview_oss_id = oss_id`。
- 附件：`POST / DELETE …/{docType}/attachment`，单个 ≤ 50MB；`viability_oss_id` 是样本质控表上单独的一栏，不走通用附件。
- 注册 `SampleChildrenChecker`：样本名下有任何一份 `published` 的文档 → true（自动建出来的空草稿不算）。

## 3 边界（明确不做）

- 不做活率判级、不做质量等级结论
- 不做「完成并同步」与撤回（DOC-PUBLISH-001）——本张里 `doc_status` 只会是 draft（seed 里已有的 published 除外）
- 不做渲染与预览
- 不做文档的多版本 / 修订历史
- 一个样本一份：不做同一样本多份类器官质控表

## 4 完工报告要求

1. **改了哪些文件**（含 Flyway 文件名与取号依据、新增的类 / 页面 / 接口清单）
2. **accept 逐条 ✅ / ❌ + 关键输出**（贴命令输出，不贴「已通过」三个字）
3. **遗留与 raise**：越出 `touches` 的改动、与 `doc/api-contract.md` 不一致的地方、没把握的口径
4. 验证用的后端 / 前端长进程已关，或明示留给谁

- 2026-09-24 按 CR-20260924-11 更新：新增 accept 4——私有桶下 `GET /lqg/qc/{sampleId}` 给出的图片地址是签名链接、直接 GET 为 200，同一对象去掉签名为 403，单测 `QcOssUrlsTest` 钉公有桶原样、签不出来不抛；§2 补地址口径，blueprint_refs 补 FIELD:t_lqg_doc_image.oss_id。
