# QC-MODEL-001 · 完工报告

- **ticket**：QC-MODEL-001（track QC / phase D6 / size M）—— D6 第一张票
- **status**：**done**
- **accept**：**3/3 绿**（`accept-run.py --ticket QC-MODEL-001 --run`，票面 front-matter 逐字重放；NF1 自动去掉 `--fresh-module`，本票没有 mvn 语句 → NF2 未触发）
- **分支**：`task/D6`（**未切分支、未 push、未合分支**；`doc/waves/state.json`、`_manifest.json`、`doc/requirements.yaml`、`doc/authority/*`、`doc/change-log.md`、`doc/verify/seed/`、`gen_seed.py`、`doc/verify/api.sh`、`_input/` 一个字节没动）
- **环境**：后端 `ruoyi-admin.jar`（含本票两支迁移）@ 8094，pid 73477（**收工已按 PID 关停**，见 §6）
- **验收对象**：`code/RuoYi-Vue-Plus` 工作树 + dev 库 `lqg_dev`（PG 5433）

---

## 0. 状态自检（3 级）

| 项 | 结果 |
|---|---|
| 分支符合调度器注入的期望 | ✅ `task/D6`（不是写死的 `feature/dayN`） |
| `depends_on` SAMPLE-VERIFY-001 已完成 | ✅ 上游 5 张表/接口都在；`t_lqg_sample` 的最近迁移 202609221000 已应用 |
| 扫 `doc/change-log.md` 涉及本票的 CR | ✅ **无** CR 直接改 QC-MODEL-001 的表/字段/接口（最近的两条 CR-20260921-08 小程序视觉、CR-20260918-07 表格页/历史记录/内部编号开关都不动 QC 模型；CR-20260917-04 只提「三份 Word 对外可见」，属渲染票）。**prompt 字面与蓝图无冲突** |
| 环境可用 | ✅ `qa-up.sh --backend-port 8094 --no-web --no-mp` 一条命令起齐（含 reseed 7 段） |
| 权威锚逐条取过 | ✅ 全部 10 个 `blueprint_refs` 用 `authority_lint.py show` 取过（**路径与票面/任务书写的不同**，见 WARN-11） |

**没有 STOP**。

---

## 1. 改了哪些文件

全部是**新增**（`git status --porcelain` 里没有一条 `M`/`D`）。`doc/waves/reports/QC-MODEL-001/` 是本报告与取证目录。

### 1.1 Flyway 迁移（2 支，与票面 touches 的号段逐字一致）

| 文件 | 内容 | 取号依据 |
|---|---|---|
| `.../db/migration/V202609261300__QC-MODEL-001-qc-docs.sql` | `t_lqg_qc_sample` / `t_lqg_qc_organoid` / `t_lqg_qc_score` 三张表 + 菜单 5501-5503 | `doc/lint-profile.yaml`：D6 = `20260926`、QC 域 `13xx`；`field-ssot.yaml` 里三张表的 `migration` 字段就写着这一支 → 取 `1300`。版本号 > 库里已应用最大值 `202609241210` → `out-of-order=false` 不会 ValidateException |
| `.../db/migration/V202609261310__QC-MODEL-001-doc-assets.sql` | `t_lqg_doc_image` / `t_lqg_doc_attachment` 两张表 | 同 SSOT 的 `migration` 字段 → 取 `1310`（1300 之后的下一个分钟）。**不含菜单** |

- 建表段 = `python3 doc/tools/gen_ddl_pg.py --migration <文件名>` 的**逐字节输出**（先跑了 `--check`：`field-ssot OK：15 张表 · 165 个业务字段 · 26 个字典`）。
- ★ **accept 1 第 2 段断「本票 Flyway 恰 2 行」**（`script LIKE 'V2026092613%__QC-MODEL-001-%'`），所以菜单**并进了第一支**（CRYO-MODEL-001 的先例），**没有第三支迁移**。
- 表上**没有**来源单位 / 患者姓名 / 性别 / 收样时间 / 处理时间 / 操作人 / 内部编号（ticket §0 口径复述 1）；`formed_time` / `feedback_time` 是 `VARCHAR(100)` 不是 DATE；三张文档表的 `sample_id` 上是**部分唯一索引** `... WHERE del_flag='0'`。

### 1.2 后端 Java（全部在 `org.dromara.lqg.qc`，共 24 个新类）

```
qc/controller/QcDocController                 9 个端点（见 §2）
qc/domain/       QcSampleDoc QcOrganoidDoc QcScoreDoc DocImage DocAttachment
qc/domain/bo/    QcSampleSaveBo QcOrganoidSaveBo QcScoreSaveBo
                 DocImageBo DocImageSortBo DocAttachmentBo
qc/domain/vo/    QcDocBundleVo QcSampleRefVo QcSampleDocVo QcOrganoidDocVo
                 QcScoreDocVo DocImageVo DocAttachmentVo QcScoreDictRow
qc/mapper/       QcSampleDocMapper QcOrganoidDocMapper QcScoreDocMapper
                 DocImageMapper DocAttachmentMapper QcScoreDictMapper
qc/service/      QcDocRules            ← 纯规则：文档类型 / 图片位归属 / 张数上限 / 三段模板原文 / 评分字典
                 QcDocService          ← 读 + 写 + 草稿幂等建立（不加 @Transactional，见 §5 坑 2）
                 QcScoreDictionary     ← 档位 → 字典 remark 分值（不缓存）
                 QcScoreSnapshot       ← 四个分值 + 合计（Integer，null = 没选）
                 QcImagePreviewResolver← 非 jpg/png 或长边 >2000px → 另存 JPEG 预览
qc/guard/        QcChildrenChecker     ← 注册给 SampleChildrenChecker：published 文档才算
```

### 1.3 测试（4 个契约测试类 / 20 个用例，全绿）

```
qc/service/QcDocRulesContractTest        5 例  模板原文逐字 / 图片位归属 / 上限 / 路径段映射
qc/service/QcScoreRulesContractTest      6 例  无 *Score 字段(反射) / 0 分档算选了 / 缺项合计 NULL / 非法档位 400 / 分值跟字典 remark / remark 空 = 500
qc/guard/QcChildrenCheckerContractTest   5 例  真 wrapper 里只有 published / 三张表都查 / null 不查库 / @TableLogic
qc/domain/QcShapeContractTest            4 例  质控表上无样本七项 / accept 点名的键都在 / 聚合体四键 / patientNo 是 String
```

全套模块测试：`mvn -pl ruoyi-modules/ruoyi-lqg test …` → **`Tests run: 217, Failures: 0, Errors: 0, Skipped: 0` + BUILD SUCCESS**（含 D1–D5 全部既有契约测试，0 回归）。

### 1.4 取证目录 `doc/waves/reports/QC-MODEL-001/`

`accept.json`（accept-run 的机器结果）· 3 份 `QC-MODEL-001-accN.sh.log`（逐条输出）· `SAMPLE-VERIFY-001-acc*.log` + `EMBED-MODEL-001-acc*.log`（**上游回归**）· `evidence.db.txt`（迁移/列/索引/菜单/seed 快照）· `extra-probes.txt`（accept 没覆盖的端点：附件增删、图片排序、软删、50MB、400 分支）。

---

## 2. 接口清单与口径（给 QC-WEB-* / DOC-RENDER-001 的权威形状）

| 方法 路径 | 权限 | 说明 |
|---|---|---|
| `GET /lqg/qc/{sampleId}` | `lqg:qc:query` | 三份文档 + 图片 + 附件 + 样本只读字段；**缺哪份建哪份空草稿** |
| `PUT /lqg/qc/{sampleId}/sample-qc` | `lqg:qc:edit` | 补丁语义（`null`=不动、空串=清空、`viabilityOssId=0`=摘掉活率附件） |
| `PUT /lqg/qc/{sampleId}/organoid-qc` | `lqg:qc:edit` | 五栏自由文本，不做日期校验 |
| `PUT /lqg/qc/{sampleId}/score` | `lqg:qc:edit` | **只收四个 `*Level`**；分值后端按字典 `remark` 回填 |
| `POST /lqg/qc/{sampleId}/{docType}/image` | `lqg:qc:edit` | `{slot, ossId}`；归属校验 + 每位 ≤3 |
| `DELETE /lqg/qc/{sampleId}/{docType}/image/{id}` | `lqg:qc:edit` | 软删 |
| `PUT /lqg/qc/{sampleId}/{docType}/image/sort` | `lqg:qc:edit` | `{ids:[…]}` 该位**全部** id，按目标顺序（contract 没定形状 → WARN-3） |
| `POST /lqg/qc/{sampleId}/{docType}/attachment` | `lqg:qc:edit` | `{ossId, fileName[, fileSize]}`；三份文档都能挂；单个 ≤50MB |
| `DELETE /lqg/qc/{sampleId}/{docType}/attachment/{id}` | `lqg:qc:edit` | 软删 |

`docType` ∈ `sample-qc | organoid-qc | score`（连字符）↔ 字典 `sample_qc | organoid_qc | organoid_score`（下划线）。
响应体：`data.{sample, sampleQc, organoidQc, score}`，三份文档**恒不为 null**（首次 GET 就建齐）；
文档对象里 `images` 是 **slot → 列表** 的 map（该文档类型的**每个位都在**，没图也是空列表），**评分表的 `images` 恒为空 map**；
`attachments` 按 `sort` 升序；细胞活率测定附件是 `t_lqg_qc_sample` 上**单独一栏**（`viabilityOssId/viabilityFileName`），
**不在** `attachments` 里。

**权限**：Flyway 落了菜单 `5501 lqg:qc:query` / `5502 lqg:qc:edit` / `5503 lqg:qc:publish`，授给 101（lqg_admin）与 102（lqg_internal）。
**只落 F 按钮行、不建 C 页面菜单**（页面菜单 5510 留给 QC-WEB-001/002，CRYO-MODEL-001 先例）。

### 2.1 给下游票的坑（DOC-RENDER-001 / QC-WEB-001 / QC-WEB-002）

1. **三段模板默认文字只有一个真相源**：`QcDocRules.RECEIVE_DESC_DEFAULT / OBSERVE_DESC_DEFAULT / PRETREAT_DESC_DEFAULT`。渲染模板要引用/对齐它们，**不要重新手打一遍** —— 差一个标点（如「2~8℃」写成波浪号）甲方并排看就会发现，accept 3 第 1 段也是逐字比对。
2. **`images` 是 map 不是数组**：`{"orig":[…],"observe":[…],"pretreat":[…]}`（类器官质控表只有 `organoid_observe`），**每个位都在、没图是 `[]`**；**评分表的 `images` 恒为 `{}`**（它没有图片位，但可以挂 `attachments`）。
3. **进 Word 的图用 `previewOssId`（对应的 `previewUrl`）**，预览页点开看原图用 `ossId`（`url`）。两者常常相等（无需转换）。seed 行的 URL 是 `https://seed.invalid/...` 的假地址，只用于形状断言，别拿它去下载。
4. **`patientNo` 在库里是裸 Base64 密文**（如 seed 的 1001 解出来是 `P-0001`）。直接读表渲染会渲染出密文 —— 要么走 `GET /lqg/qc/{sampleId}` 的 VO（已解密），要么自己用 `SampleFieldCipher.decrypt`。同域的 `donorName` 同理。
5. **`doc_status` 与「对外可见」同源**：只有 `doc_status='published'` 才算已完成（`QcChildrenChecker` 用的就是这个判据）。draft 是「自动建出来的空草稿」，**不算下游记录、也不该给外部看**。本票不写这一列 —— 状态机在 DOC-PUBLISH-001。
6. **`GET /lqg/qc/{sampleId}` 是「会写库」的读**（首次访问建三份草稿），且样本不是 `valid` 时 400。渲染/预渲染链路不要拿它当只读探活；DOC-RENDER-001 直接读三张表更合适。
7. **`formedTime` / `feedbackTime` 是 `VARCHAR(100)`**：可能是「约第 5 天」，渲染不要按日期格式化。
8. **一个样本一份**：三张表都用部分唯一索引（`WHERE del_flag='0'`）钉住，同一样本不可能有两份同类文档；不要写「取最新一份」的兜底逻辑。
9. **附件的 `fileSize` 可能为 0**（契约没带、`sys_oss.ext1` 也没记录时）—— 页面别把 0 当「空文件」拒绝。
10. **权限**：`5501 lqg:qc:query` / `5502 lqg:qc:edit` / `5503 lqg:qc:publish` 已存在并授给 101/102；**C 页面菜单要 QC-WEB-* 自己落 5510**（别复用 5501-5503，`menu_type` 不同、`component` 一撞就出现两条同名路由 —— EMBED-WEB-001 被逼搬过一次号）。

---

## 3. accept 逐条 ✅/❌ 与关键输出

```
$ export LQG_VERIFY_ENV_FILE="$PWD/.tmp/qa-env/8094/verify.env"
$ python3 doc/waves/tools/accept-run.py --ticket QC-MODEL-001 --run \
      --json .tmp/qc-model-accept-final.json --logdir .tmp/qc-model-accept-logs-final
[run] 3 条 accept，单条超时 900s
  ✓ QC-MODEL-001 acc1 [DDL]   五张表与 SSOT 逐列相符、出自本票 Flyway；每个样本每种文档至多一份（部分唯一索引） (0.4s)
  ✓ QC-MODEL-001 acc2 [DATA]  评分由后端按字典回填：前端夹带的假分值不生效、0 分档不被当成未选、没选全合计为空；落库的分值与字典表两侧对得上 (2.0s)
  ✓ QC-MODEL-001 acc3 [STATE] 首次打开幂等建三份草稿且默认文字逐字照模板；图片位规则（归属、每位至多三张）被拒时库里不变；患者编号落库是密文 (1.9s)
[ok] 结果落盘 .tmp/qc-model-accept-final.json
[run] 通过 3/3
```

### accept 1 · DDL —— ✅（`doc/waves/reports/QC-MODEL-001/QC-MODEL-001-acc1.sh.log`）

```
✓ 5 张表与 SSOT 逐列相符（含公共字段 6 个、部分唯一索引、普通索引）   ← ddl_vs_ssot.py，exit 0
2                                                                ← V2026092613%__QC-MODEL-001-% 恰 2 行
character varying                                                ← t_lqg_qc_organoid.formed_time
character varying                                                ← t_lqg_qc_organoid.feedback_time
```

补充（`evidence.db.txt`）：五张表列数 `21 / 16 / 20 / 13 / 13`；三个部分唯一索引带 `WHERE (del_flag = '0'::bpchar)`：

```
uk_qc_sample_sample    CREATE UNIQUE INDEX … (sample_id) WHERE (del_flag = '0'::bpchar)
uk_qc_organoid_sample  CREATE UNIQUE INDEX … (sample_id) WHERE (del_flag = '0'::bpchar)
uk_qc_score_sample     CREATE UNIQUE INDEX … (sample_id) WHERE (del_flag = '0'::bpchar)
```

### accept 2 · DATA —— ✅（`QC-MODEL-001-acc2.sh.log`，含 NF1）

```
(第 1 段) PUT /lqg/qc/9000001005/score，请求里夹带 preCultureScore:99 / cultureDaysScore:99 / totalScore:100 → .code==200 → true
(第 2 段) 落库分值              20|0|0|30|50     ← 前端假分值与 totalScore:100 一个字没生效；gt14/lt100 的 0 分档落成 0（不是 NULL）
(第 3 段) 与字典表 JOIN 对账    3                ← 参与对账的行数（1001=85 / 1005=50 / 1006=18），--empty 不在零行上空转
(第 4 段) 分值≠字典 remark 求和  (空)             ← 三次对账全部相等
(第 5 段) cultureDaysLevel:null 后  NULL|NULL      ← 该项分值回落 NULL、合计回落 NULL（不是补 0）
(第 6 段) preCultureLevel:"gt999" → 400	…没有档位 gt999（合法档位：…）
```

### accept 3 · STATE —— ✅（`QC-MODEL-001-acc3.sh.log`，含 NF1）

```
(第 1 段) GET /lqg/qc/9000001006 → true   （sample.internalNo=T-hco04、sampleQc.docStatus=draft、
                                          三段默认文字逐字相等、score.totalScore=18）
(第 2 段) 再 GET 一次 → 三张表 1|1|1      ← 幂等，没建出第二份（部分唯一索引也没被撞到）
(第 3 段) GET /lqg/qc/9000001002 → 400	质控文档只能对已核验有效的样本打开（当前状态：pending）
(第 4 段) POST 9000001001/sample-qc/image {orig,9000004005} → 200
(第 5 段) 再 POST 一张 orig → 400	图片位 orig 已有 3 张，最多 3 张
(第 6 段) POST organoid_observe 到样本质控表 → 400	图片位 organoid_observe 不属于 sample_qc（它只有：orig / observe / pretreat）
(第 7 段) POST /score/image → 400	图片位 orig 不属于 organoid_score（它只有：没有图片位）
(第 8 段) 库里 slot 分组       observe:1 / orig:3     ← 三次被拒库里都没多行
(第 9 段) PUT sample-qc {patientNo:P-PROBE} → 200
(第 10 段) patient_no = WGk4sblY1e3HTyYUeNdt2g==  ← 与 openssl 独立算出的密文逐字相等（裸 Base64，无 ENC_ 前缀）
```

### 上游回归（本票注册 `QcChildrenChecker` 的直接风险面）—— ✅

```
$ python3 doc/waves/tools/accept-run.py --ticket SAMPLE-VERIFY-001 --run   → 通过 2/2
$ python3 doc/waves/tools/accept-run.py --ticket EMBED-MODEL-001  --run   → 通过 4/4
```

（SAMPLE-VERIFY-001 accept 1 对 **1002** 的 `pending→valid→invalid` 全绿 —— 1002 名下没有质控文档；
EMBED-MODEL-001 对 **1004** 的「改判被拒」仍 400 —— 1004 名下那份 **published** 的样本质控表本来就没让它通过过。）

### accept 没覆盖、本票另跑的探针 —— ✅（`extra-probes.txt`）

```
GET /lqg/qc/9000001001 → sample.internalNo=T-hli01 · donorName 明文「测试供体甲」· sampleQc.patientNo 明文「P-0001」
                         imageSlots [{orig:2},{observe:1},{pretreat:0}] · organoidSlots ["organoid_observe"]
                         scoreImages [] · scoreTotal 85 · attachments ["活率报告.pdf"]
                         origFirst {ossId:9000004001, previewOssId:9000004001, url:…, previewUrl:…}
POST 附件（319488 字节）→ 200 · GET 带出 {fileName,fileSize,url} · POST 60MB → 400「单个附件不能超过 50MB」
DELETE 附件 → 200 · 库里 del_flag='0' 计数 0
PUT image/sort {ids:[9000005302,9000005301]} → 200 · 库里 9000005302:1 / 9000005301:2
PUT image/sort 少给一张 → 400「排序要给出该图片位里全部 2 张图的 id」
DELETE image/9000005301 → 200 · 库里 orig:1 / observe:1
DELETE 别的文档的图（9000005303 属 organoid_qc）→ 400 · 该行 del_flag 仍 '0'（库里不变）
preview：seed 的假 OSS → 9000004003|9000004003（退回原图）
GET 1003(invalid) / 1007(pending) / 1010(软删) → 400 400 400
PUT /lqg/sample/9000001001/verify invalid → 500「该样本名下已有包埋 / 冻存 / 质控文档，不能改判无效」
PUT /lqg/sample/9000001002/verify invalid → 200（没有质控文档，仍可改判）
```

---

## 4. 越出 `touches` 的改动

**没有。** `git status --porcelain` 里新增的文件全部落在票面 4 条 touches 内：

- `ruoyi-lqg/src/main/java/org/dromara/lqg/qc/**`（含 controller/domain/bo/vo/mapper/service/guard）
- `ruoyi-lqg/src/test/java/org/dromara/lqg/qc/**`
- `ruoyi-admin/src/main/resources/db/migration/V202609261300__QC-MODEL-001-qc-docs.sql`
- `ruoyi-admin/src/main/resources/db/migration/V202609261310__QC-MODEL-001-doc-assets.sql`

外加票面 §4 要求的 `doc/waves/reports/QC-MODEL-001.md` 与取证目录 `doc/waves/reports/QC-MODEL-001/`（只新增，不改任何既有文件）。

---

## 5. WARN 清单（请逐条入账）

- **WARN-1 · `patientNo` 没有用框架 `@EncryptField`**。ADR-0006 的决策值字面是「用框架 `@EncryptField`」，ticket §2 也写「加 `@EncryptField`」；但 accept 3 末段要求**库里的值 = `openssl enc -aes-128-ecb … -nosalt -base64 -A` 的输出**（裸 Base64），而框架拦截器会写成 `ENC_` + Base64（`EncryptorManager.encrypt` 第 100 行）。所以本票与 SAMPLE-MODEL-001 的既有先例一致：**复用 `SampleFieldCipher`（`EncryptUtils` 手工加解密）**，实体上是密文、读时解密。这也是 ADR-0006 决策值与实现之间既有的偏差（D1 起就存在），不是本票新引入的口径冲突。
- **WARN-2 · 两张资源表的包位置**。`field-ssot.yaml` 的 `module` 字段把 `t_lqg_doc_image` / `t_lqg_doc_attachment` 划给 `ruoyi-lqg/doc`，但本票 `touches` 只允许 `org.dromara.lqg.qc/**`、§2 也写「包 `org.dromara.lqg.qc`」→ 实现放在 `qc` 包，**`org.dromara.lqg.doc` 的占位包保持空**。DOC-RENDER-001 若要按 `module` 分层，需自行决定是否把资源类搬过去。
- **WARN-3 · `PUT …/{docType}/image/sort` 的请求体形状 `doc/api-contract.md` 没定**。本票定为 `{ids:[…]}`（该图片位**全部** id，按目标顺序；少给 / 跨位 → 400）。
- **WARN-4 · 附件的 `fileSize`**。契约第 81 行的形状是 `{ossId, fileName}`，而 SSOT 的 `file_size` 是 NOT NULL → 本票把 `fileSize` 做成**可选**：给了校验 ≤50MB，没给回落到 `sys_oss.ext1.fileSize`（工作台上传侧写的），再没有落 0。**没给 size 且 ext1 没记录时无法强制「≤50MB」**。
- **WARN-5 · 预览图的「真转换」分支在确定性数据里跑不到**。判定逻辑按票面实现（非 jpg/png 或长边 >2000px → 缩放 + 编码 JPEG + 存 OSS + 落 `sys_oss` 行），但 seed 的 `sys_oss.service='seed'`、URL 是 `https://seed.invalid/...` 的假地址，取不到字节 → 一律**退回 `preview_oss_id = oss_id`** 并打 WARN。（真门 `minio` 才有字节。）另外**本机 JDK 自带 `ImageIO` 读不了 TIFF**，`read` 返回 null 也退回原图 —— 要真覆盖 TIFF 得加 `imageio-tiff` 之类插件，属后续票范围。**accept 不测这一条**，如实登记。
- **WARN-6 · 只落 F 权限行、不建 C 页面菜单**（5501-5503）。`lqg:qc:publish` 也一并落了（本票不实现 publish/unpublish，端点归 DOC-PUBLISH-001）—— 免得后继票再补一支迁移。
- **WARN-7 · `doc_status` 本票一个字不动**（§3 明确不做发布状态机）。因此对一份 **published** 的样本质控表 `PUT` 保存内容，它**仍然是 published**；SSOT 注释里那条「published 后再保存内容 → 回到 draft」归 DOC-PUBLISH-001。
- **WARN-8 · `viabilityOssId = 0` 是「摘掉细胞活率附件」的哨兵**（契约没定「清空一栏」的语义；用 0 与「不传 = 不动」区分）。文本栏的清空语义 = 传空串。
- **WARN-9 · 加图片时会尝试走一次 OSS/Redis**（`OssFactory.instance(service)`）判预览。seed 的 `service='seed'` 配置不存在 → 抛 `OssException` 被兜住（不阻断保存）；Redis 不可用同样只打 WARN。这是「预览图判定」的固有代价，已在类注释里写明。
- **WARN-10 · 评分字典每次保存都查库、不缓存**（故意的）：这样工作台改了字典 `remark`，下一次保存立刻生效（ADR 口径「改分值改字典 remark，不改代码」）。
- **WARN-11 · `authority_lint.py` 的实际路径与票面/任务书写的不一致**。票面 §0 与任务书写 `python3 doc/authority/authority_lint.py show <锚>`，但 `doc/authority/` 下只有 `field-ssot.yaml / flows.yaml / ui-index.yaml / _snapshot.json / _rendered/`；脚本实际在 `~/claude-config/skills/xuqiu/scripts/authority_lint.py`。用 `python3 ~/claude-config/skills/xuqiu/scripts/authority_lint.py --ws doc show <锚>` 跑通（10 个锚逐条取到）。**这是工具位置与文档不符，不是本票缺陷**，建议改票面模板。
- **WARN-12 · `QcChildrenChecker` 无法用 seed 数据做端到端单点验证**：seed 里**没有**「质控文档是唯一下游」的样本（1001 有包埋、1004 有包埋+冻存、1006 有包埋、1005 只有草稿）→ 「仅因 published 质控文档而不能改判」这条只能由 `QcChildrenCheckerContractTest`（真 wrapper 里只有 `doc_status='published'`）证明，E2E 上它总是被 EMBED/CRYO 先拦住。已按 5 条用例钉住判据。

---

## 6. 长进程与端口

- 后端 8094：`bash doc/waves/tools/qa-up.sh --down --backend-port 8094 --no-web --no-mp`（**按 PID**，未用 `pkill -f 'ruoyi-admin.jar'`；8080/5432/6379 全程没碰）。
- 本票**没起**工作台（8093）/ 小程序 H5（9202）—— 只动后端与迁移，`--no-web --no-mp`。
- 8094 关停后已释放（见 §7 收工输出）。

---

## 7. 收工自检

```
$ git status --porcelain            # 只有本票新增（见 §1），没有 M/D
$ git branch --show-current         # task/D6
$ git diff --stat HEAD              # 空（没有改任何既有文件）
$ bash doc/waves/tools/qa-up.sh --down --backend-port 8094 --no-web --no-mp
  ✓ 已按 PID 关停 后端(8094)（pid 73477）
  ✓ 8094 已释放 · ✓ 8093 已释放 · ✓ 9202 已释放
```

`doc/waves/state.json` / `_manifest.json` / `doc/requirements.yaml` / `doc/authority/*` / `doc/change-log.md` / `doc/verify/seed/` / `gen_seed.py` / `doc/verify/api.sh` / `_input/` —— **未改**。
