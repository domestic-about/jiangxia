# SAMPLE-MODEL-001 · 完工报告

- **ticket**：SAMPLE-MODEL-001（track SAMPLE / phase D2 / size M）——D2 第一张
- **status**：**done**
- **accept**：**3/3 绿**（三条 `run` 逐条实跑；唯一改动 = 去掉本 agent 沙箱跑不了的 `--fresh-module ruoyi-lqg`，等价证据见 §4.0）
- **分支**：`task/D2`（未切分支 / 未 push / 未 merge / 未动 `main`、`integration`）
- **验收对象**：jar `ruoyi-admin.jar` @ `2026-09-21 22:42:12`，后端进程 **PID 21550**（8081，dev profile + `--api-decrypt.enabled=false`）
- **只读区未动**：`doc/waves/state.json`、`_manifest.json`、`doc/requirements.yaml`、`doc/authority/*`、`doc/change-log.md`、`doc/verify/seed/`、`gen_seed.py`、`doc/verify/api.sh`、`doc/verify/reseed.sh`、`doc/verify/db.py`、`ddl_vs_ssot.py`

---

## §0 状态自检（3 级）

| 检查项 | 结果 | 证据 |
|---|---|---|
| 当前分支符合调度器注入的期望 | ✅ PASS | `git branch --show-current` → `task/D2` |
| `depends_on` 全部 done | ✅ PASS | `SYS-BASE-001` / `AUTH-LOGIN-001` 两份报告 `status: done`；产物在盘（`/lqg/sys/ping`、`/mp/me`、`rooyi-lqg` 模块、7 支 D1 迁移） |
| 扫 `doc/change-log.md` 顶部四条 CR | ✅ PASS | 顶部四条：**CR-20260921-08**（小程序视觉方向 A——只动小程序 token 与图廊，本票不做前端）、**CR-20260918-07**（9 条里的 ④「内部编号开关」与 ⑦「外部可见操作人/包埋人」**都作用在 Ext\*Vo 与工作台页面**，本票只做内部接口、不产出任何 Ext\*Vo、不返回包埋人 → 不受影响；①「表格页加修改入口」是 SAMPLE-WEB-001/Mp 的活，本票的 `PUT /lqg/sample` 是它要调的那个写入口）、**CR-20260917-06** / **CR-20260917-05**（小程序结构与图廊，同上）。`authority_lint.py diff` 口径：本票不动 flows / field-ssot / ui-index |
| 逐条取权威锚 | ✅ PASS | 9 个 `blueprint_refs` 全部 `authority_lint.py show` 过，逐条对照见 §1 |
| ADR-0010 / ADR-0006 / ADR-0009 | ✅ PASS | 三条都 `show` 过：一张表两种收样记录、加密列只精确查、部分唯一索引 + 6 公共字段 + 无 `tenant_id`/`del_unique`；落地差异见 §5 |
| 动手前代码是绿的 | ✅ PASS | `mvn -pl ruoyi-modules/ruoyi-lqg test` → `Tests run: 31, Failures: 0, Errors: 0`（改动前 25，本票 +6） |

**STOP 判定：无。** 没有出现「上游产物缺失」「与权威冲突且无法判断」「环境不可用」三类硬阻塞。

## §1 口径复述（本张最容易做反的四点，逐条对 accept 核）

| # | 口径（ticket §0 / §2） | 落点 | 机器证据 |
|---|---|---|---|
| 1 | **别照两份模板建两张表**：`sample_kind` 区分 tissue / organoid，「类器官类型」只是 organoid 类才填的一列 | 一支迁移只建 `t_lqg_sample` 一张表（`t_lqg_organoid_receive` 不存在） | `pg_tables` 里 `t_lqg_*` 恰 5 张（4 张 D1 + 本票 1 张），`t_lqg_organoid_receive` 计数 0；`ddl_vs_ssot.py --table t_lqg_sample` 逐列相符 |
| 2 | **内部编号手填、不自动生成**；唯一性校验 + **软删后可重用** | `SampleKindRules` 里只有「空就拒」没有生成；`uk_sample_internal_no ... WHERE del_flag='0'`；查重走 `@TableLogic` 过滤后的 `selectCount` | 索引定义带 `WHERE (del_flag = '0'::bpchar)`；seed 的 1002/1003/1007 有 NULL 内部编号能查出来（列可空）；`T-del99` 软删后重录 → 200 且 `del_flag='0'` 恰 1 行 |
| 3 | **送检单号走序列** `seq_lqg_submit_no`，不许 `max()+1` | `SampleMapper.nextSubmitNoSeq()` = `SELECT nextval('seq_lqg_submit_no')`；`SampleKindRules.formatSubmitNo` = `"SJ" + %08d` | accept 1 第 4 段断序列存在；P13 连开 5 条 5 个不同 `submit_no`，全匹配 `^SJ[0-9]{8}$` |
| 4 | **联想词走字典不走历史值** | `HintDictionaryService` 只调 `DictService.getDictData('lqg_hint_*')`，代码里没有任何 `t_lqg_sample` 的读法 | 往样本表塞 `某某单位私有组织` 后，`/mp/dict/hints?type=tissue` 仍返回字典那 7 个、不含私有值（P7）；接口 data 与 `sys_dict_data` 的 label 序列逐字相同 |

**与 accept 第 3 条 `--as extA` 调 `/mp/dict/hints` 的边界**（调度器点名要核的那条）：见 §6 的 **WARN-1**——
`doc/api-contract.md` 第 21 行明确把 `/mp/dict/**` 列进「内外部都能调」的三组，**按 accept + 契约实现**；
ADR-0004 字面的「外部只能调 `/mp/ext/**`」有一处张力，已记 WARN，未改任何权威。

## §2 改了哪些文件（ticket §4.2）

### 2.1 迁移（Flyway）

**`code/RuoYi-Vue-Plus/ruoyi-admin/src/main/resources/db/migration/V202609221000__SAMPLE-MODEL-001-sample.sql`**（143 行，新增）

**取号依据 `doc/lint-profile.yaml`**：D2 = `20260922`（本票 phase D2）；HHmm 按域分段，SAMPLE = `10xx`；
`1000` 未被占用，且**大于全部 D1 迁移**（最大 `V202609210930`）→ `out-of-order=false` 下不会触发
`FlywayValidateException`（SYS-WEB-001 踩过跨域补号的坑）。菜单号同文件：SAMPLE 域 `5200-5299`。

内容两段：

1. **第一段** = `python3 doc/tools/gen_ddl_pg.py --migration V202609221000__SAMPLE-MODEL-001-sample.sql`
   的**逐字节输出**（已用 `diff` 核过，`GEN-BYTE-IDENTICAL`）：`t_lqg_sample` 26 业务列 + 6 公共字段、
   两个部分唯一索引、3 个普通索引、**`CREATE SEQUENCE IF NOT EXISTS seq_lqg_submit_no START 1`**。
2. **第二段**（手写，非生成器输出）：菜单 `5200`「样本总表」（`path='sample'`、
   `component='lqg/sample/index'`、perms `lqg:sample:list`，父目录 `0` = 工作台一级菜单）
   + `5201-5205` 五个 F 按钮（`lqg:sample:{list,query,add,edit,remove}`）；
   `sys_role_menu` **同时授给 101（lqg_admin）与 102（lqg_internal）**（内部人员是录样本的主力；
   只授管理员会让 `--as staff` 的 accept 全 403）。

```
$ python3 doc/verify/db.py --sql "SELECT menu_id||':'||menu_name||':'||menu_type||':'||perms||':'||COALESCE(path,'-') FROM sys_menu WHERE menu_id BETWEEN 5200 AND 5205 ORDER BY menu_id"
5200:样本总表:C:lqg:sample:list:sample
5201:样本查询:F:lqg:sample:list:
5202:样本详情:F:lqg:sample:query:
5203:样本新增:F:lqg:sample:add:
5204:样本修改:F:lqg:sample:edit:
5205:样本删除:F:lqg:sample:remove:
$ python3 doc/verify/db.py --sql "SELECT role_id||':'||string_agg(menu_id::text,',' ORDER BY menu_id) FROM sys_role_menu WHERE menu_id BETWEEN 5200 AND 5205 GROUP BY role_id ORDER BY role_id"
101:5200,5201,5202,5203,5204,5205
102:5200,5201,5202,5203,5204,5205
```

> ★ **SAMPLE-WEB-001 要的 C 页面 `lqg/sample/index` 本票不产出**（§3 边界），菜单先建好是为了让
> `@SaCheckPermission("lqg:sample:*")` 对 102 真的通得过；页面由 SAMPLE-WEB-001 落。
> ★ 也**没有**按 CR-20260917-05 的 `UI:admin.sample.list` 做「导出 / 核验」菜单项——那是
> SAMPLE-EXPORT-001 / SAMPLE-VERIFY-001 的权限串。

### 2.2 后端（`ruoyi-lqg`，包 `org.dromara.lqg.sample`）

| 文件 | 职责 |
|---|---|
| `domain/Sample.java` | `t_lqg_sample` 实体，逐列照 SSOT；`@TableLogic delFlag`；公共字段走 `BaseEntity` |
| `domain/vo/SampleVo.java` | 工作台行 / 详情（明文 `donorName`/`hospitalNo` + `updateByName`/`updateTime`） |
| `domain/bo/SampleSubmitBo.java` | 新增 / 修改入参；**刻意不声明** `submitNo`/`submitSource`/`submitterId`（不可改 = 接口里根本没有） |
| `domain/bo/SampleQueryBo.java` | 列表筛选（本票五个条件，同一条 `PageQuery` 只给 `pageNum/pageSize`） |
| `mapper/SampleMapper.java` | 唯一的自写 SQL：`SELECT nextval('seq_lqg_submit_no')` |
| `guard/SampleKindRules.java` | **纯函数**：类别必填集（tissue/organoid 分化）、归一化、`SJ%08d` 格式化 |
| `guard/SampleChildrenChecker.java` · `guard/SampleChildrenCheckers.java` | **扩展点**：接口 + 聚合器；本票注册 **0 个实现**（`registeredCount()==0`） |
| `service/SampleService.java` | 写侧：新增（internal + valid + 提交人=核验人）、修改（UpdateWrapper 显式写 NULL 与 `update_by`）、软删、编号唯一/可重用 |
| `service/SampleQueryService.java` | 读侧：五条件筛选、**加密列精确查**（查询值先加密）、读出即解密、软删天然过滤 |
| `service/SampleFieldCipher.java` | AES 加解密（ADR-0006），**手工**走 `EncryptUtils`，见 §5.1 的取舍 |
| `service/SampleSubmitNoGenerator.java` | 取号（`nextval` + 格式化） |
| `service/SampleNameResolver.java` | `update_by` → 昵称（`DataPermissionHelper.ignore` 包住，AUTH-LOGIN-001 坑 1） |
| `service/HintDictionaryService.java` | 三个 `lqg_hint_*` 字典 → label 数组（走 `DictService`，不碰样本表） |
| `controller/SampleController.java` | `GET /lqg/sample/list`、`GET /lqg/sample/{id}`、`POST /lqg/sample`、`PUT /lqg/sample`、`DELETE /lqg/sample/{ids}` |
| `controller/HintController.java` | `GET /mp/dict/hints?type=…` |
| `src/test/java/.../sample/guard/SampleKindRulesContractTest.java` | **6 个用例**钉住类别必填分化 + 送检单号形状（`Tests run: 6, Failures: 0`） |

**没有改任何上游文件、没有改 `ruoyi-lqg/pom.xml`**（本票不需要新依赖：加密走已有的
`ruoyi-common-encrypt`，字典走 `ruoyi-system` 的 `DictService`，单位快照走 AUTH-GROUP-001 的
`UnitQueryService`）。

**接口清单（本票）**

```
GET    /lqg/sample/list?sampleKind&verifyStatus&internalNo&donorName&hospitalNo&pageNum&pageSize   lqg:sample:list
GET    /lqg/sample/{id}                                                                            lqg:sample:query
POST   /lqg/sample                                                                                 lqg:sample:add
PUT    /lqg/sample                                                                                 lqg:sample:edit
DELETE /lqg/sample/{ids}                                                                           lqg:sample:remove
GET    /mp/dict/hints?type=tissue|organoid|sample                                                  登录即可（内外部都行）
```

## §3 对照 ticket `touches`

`touches` 列的三处（`sample/**` Java 主/测试、`V20260922100*` 迁移）**覆盖了本票全部改动**，
无越界改动。本票**没碰**前端（`code/plus-ui` / `code/miniapp` 一个字节）、没碰根 pom、
没碰 `ruoyi-common-*` / `ruoyi-system`。

## §4 accept 逐条 ✅ / ❌ + 关键输出

### 4.0 关于 `--fresh-module`（本 agent 沙箱限制，与 D1 六张票同源）

`doc/verify/api.sh` 第 71 行用 `ps -o lstart=`，在本 subagent 沙箱里是 `/bin/ps: Operation not permitted`
→ `exit 2`。按规矩**没有改 `api.sh`**，用等价证据覆盖那道守卫的**两个半边**（源码不得比 jar 新 +
进程不得早于 jar）：

```
jar mtime                : 2026-09-21 22:42:12
lqg src newer than jar   : []          ← 没有比 jar 新的 ruoyi-lqg 源码
ruoyi-admin src newer    : []          ← 同上
8081 PID                 : 21550（`lsof -ti tcp:8081 -sTCP:LISTEN`）
该 PID 打开的文件含       : ruoyi-admin/target/ruoyi-admin.jar（`lsof -p 21550 | grep -c` = 2）
后端日志 mtime            : 2026-09-21 22:42:34（进程起于打包之后 = 没 stale）
嵌套 jar 复核             : `BOOT-INF/lib/ruoyi-lqg-5.5.3.jar` 内含
                            `org/dromara/lqg/sample/{controller,service,guard}/` 下 20+ 条目
                            （HintController / SampleController / SampleQueryService / SampleService …）
```

**复跑脚本随报告落盘**：`doc/waves/reports/SAMPLE-MODEL-001/accept-runners/{sample001-acc1,acc2,acc3}.sh`
（与本次实跑的 `.tmp` 版本逐字相同，从工作区根执行即可；唯一差异是 acc2 去掉 `--fresh-module`）。
三条脚本都**把长链包进函数判整条 rc**（不丢给 `set -e`：非末尾位置失败会静默短路 + exit 0，
AUTH-STAFF-001 坑 3），并且开头 `rm -f $TMPDIR/lqg-verify-token-*`（api.sh 的 token 缓存只按 mtime
判新鲜，改库后会假 401 / 假 200）。

### 4.1 accept 1 · DDL —— ✅

原样跑（四条 `&&` 链，无 `--fresh-module`）：

```
$ bash doc/waves/reports/SAMPLE-MODEL-001/accept-runners/sample001-acc1.sh
✓ 1 张表与 SSOT 逐列相符（含公共字段 6 个、部分唯一索引、普通索引）
1
1
ACCEPT-1 EXIT=0
```

四段分别是：`ddl_vs_ssot.py --table t_lqg_sample --require-public create_dept,create_by,create_time,update_by,update_time,del_flag`
逐列相符；`flyway_schema_history` 里 `V20260922100%__SAMPLE-MODEL-001-%` 恰 1 行且 success；
迁移文件含 `CREATE TABLE t_lqg_sample`；`pg_sequences` 里 `seq_lqg_submit_no` 恰 1 个。

**counterfeit 点名的形态逐条排掉**：

```
$ python3 doc/verify/db.py --sql "SELECT indexname||' => '||indexdef FROM pg_indexes WHERE tablename='t_lqg_sample' ORDER BY indexname"
idx_sample_status_date => CREATE INDEX idx_sample_status_date ON public.t_lqg_sample USING btree (verify_status, receive_date)
idx_sample_submitter => CREATE INDEX idx_sample_submitter ON public.t_lqg_sample USING btree (submitter_id)
idx_sample_unit => CREATE INDEX idx_sample_unit ON public.t_lqg_sample USING btree (source_unit_id)
pk_lqg_sample => CREATE UNIQUE INDEX pk_lqg_sample ON public.t_lqg_sample USING btree (id)
uk_sample_internal_no => CREATE UNIQUE INDEX uk_sample_internal_no ON public.t_lqg_sample USING btree (internal_no) WHERE (del_flag = '0'::bpchar)
uk_sample_submit_no => CREATE UNIQUE INDEX uk_sample_submit_no ON public.t_lqg_sample USING btree (submit_no) WHERE (del_flag = '0'::bpchar)

$ # 照两份模板建两张表 → t_lqg_organoid_receive 不存在，第一张少 organoid_type 列会红
$ python3 doc/verify/db.py --sql "SELECT tablename FROM pg_tables WHERE schemaname='public' AND tablename LIKE 't_lqg_%' ORDER BY tablename"
t_lqg_ext_profile
t_lqg_sample
t_lqg_source_unit
t_lqg_unit_group
t_lqg_wx_bind
$ python3 doc/verify/db.py --sql "SELECT count(*) FROM pg_tables WHERE schemaname='public' AND tablename='t_lqg_organoid_receive'" --eq 0
0

$ # 内部编号建成 NOT NULL → seed 的 1002/1003/1007（NULL 内部编号）灌不进去；实际是 nullable
$ python3 doc/verify/db.py --sql "SELECT is_nullable FROM information_schema.columns WHERE table_name='t_lqg_sample' AND column_name='internal_no'" --eq YES
YES

$ # 送检单号用 max()+1 → 没有序列，第 4 段红；实际有序列
$ python3 doc/verify/db.py --sql "SELECT sequencename||'|start='||start_value||'|inc='||increment_by FROM pg_sequences WHERE schemaname='public' AND sequencename='seq_lqg_submit_no'"
seq_lqg_submit_no|start=1|inc=1
```

### 4.2 accept 2 · DATA —— ✅

```
$ bash doc/waves/reports/SAMPLE-MODEL-001/accept-runners/sample001-acc2.sh
true
Jfied4P73vkz5XkxDihNQQ==
true
true
true
true
ACCEPT-2 EXIT=0
```

逐段对应（原文 9 段 `&&` 链）：

| 段 | 命令 | 输出 | 说明 |
|---|---|---|---|
| 2 | `--as staff POST /lqg/sample {...}` | `{"code":200,"msg":"操作成功","data":"2102045467171979265"}` | 内部新增成功（雪球 id） |
| 4 | `db.py … WHERE internal_no='T-probe01'` `--eq ${WANT}` | `Jfied4P73vkz5XkxDihNQQ==` | **库里的密文 == openssl 独立算出的值** |
| 5 | `list?internalNo=T-probe01` 的 jq | `true` | 明文 + `submitSource=internal` + `verifyStatus=valid` + `submitNo` 匹配 `^SJ[0-9]{8}$` |
| 6 | 精确查「测试供体甲」 | `true` | 恰 seed 1001 —— 加密列精确查询生效 |
| 7 | 前缀查「测试供体」 | `true` | 0 行 —— **没做 LIKE**（做了就是全表解密） |
| 8 | `list?pageSize=100` | `true` | 无 1010（软删被 `@TableLogic` 过滤）且恰 10 行（seed 9 有效 + 刚建 1） |

**★ 「两侧同源」的完整对照**（一侧是应用写进库的值，一侧是命令行工具独立算的）：

```
$ bash doc/verify/api.sh --as staff POST /lqg/sample '{"sampleKind":"tissue","sourceUnitName":"本中心","donorName":"加密探针","gender":"male","age":"50","hospitalNo":"ZYPROBE01","tissueType":"肝组织","receiveDate":"2026-09-17","internalNo":"T-probe01"}'
{"code":200,"msg":"操作成功","data":"2102045467171979265"}

$ python3 doc/verify/db.py --sql "SELECT id, donor_name, hospital_no FROM t_lqg_sample WHERE internal_no='T-probe01'"
2102045467171979265|Jfied4P73vkz5XkxDihNQQ==|fGn9eNFIUbJMOF0WbGZbtA==

$ printf '%s' '加密探针' | openssl enc -aes-128-ecb -K 4c7167546573744165734b6579233031 -nosalt -base64 -A
Jfied4P73vkz5XkxDihNQQ==
$ printf '%s' 'ZYPROBE01' | openssl enc -aes-128-ecb -K 4c7167546573744165734b6579233031 -nosalt -base64 -A
fGn9eNFIUbJMOF0WbGZbtA==

$ bash doc/verify/api.sh --as staff GET '/lqg/sample/list?internalNo=T-probe01' | jq -c '.rows[0]|{id,submitNo,sampleKind,submitSource,verifyStatus,donorName,hospitalNo,internalNo,updateByName}'
{"id":"2102045467171979265","submitNo":"SJ00000003","sampleKind":"tissue","submitSource":"internal","verifyStatus":"valid","donorName":"加密探针","hospitalNo":"ZYPROBE01","internalNo":"T-probe01","updateByName":"李工"}
```

**counterfeit 逐条排掉**：

```
$ # 漏了加密 → 库里是明文「加密探针」，与 openssl 的密文不等红
$ # 加了注解但 dev 没开 mybatis-encryptor → 同上红（dev 的 mybatis-encryptor.enable=true 已在配置里）
$ # 精确查询没先加密查询值 → 按「测试供体甲」查不到 1001 红
$   python3 doc/verify/db.py --sql "SELECT count(*) FROM t_lqg_sample WHERE donor_name='测试供体甲'" --eq 0   → 0
$   bash doc/verify/api.sh --as staff GET '/lqg/sample/list?donorName=%E6%B5%8B%E8%AF%95%E4%BE%9B%E4%BD%93%E7%94%B2' | jq -c '[.rows[].id|tostring]'  → ["9000001001"]
$ # 为了「好用」给加密列做 LIKE → 按「测试供体」能查出一堆；实际 0 行
$   bash doc/verify/api.sh --as staff GET '/lqg/sample/list?donorName=%E6%B5%8B%E8%AF%95%E4%BE%9B%E4%BD%93' | jq -c '.rows|length'  → 0
$ # 列表没过滤软删（手写 SQL 绕过 @TableLogic）→ 1010 出现红；实际
$   bash doc/verify/api.sh --as staff GET '/lqg/sample/list?pageSize=100' | jq -c '{total,n:(.rows|length),ids:[.rows[].id|tostring]}'
$   {"total":9,"n":9,"ids":["9000001007","9000001002","9000001003","9000001006","9000001005","9000001004","9000001001","9000001008","9000001009"]}
$   bash doc/verify/api.sh --as staff GET '/lqg/sample/list?internalNo=T-del99' | jq -c '{total,n:(.rows|length)}'  → {"total":0,"n":0}
$   bash doc/verify/api.sh --as staff --bizcode GET /lqg/sample/9000001010                                             → 500	样本不存在
```

### 4.3 accept 3 · STATE —— ✅

```
$ bash doc/waves/reports/SAMPLE-MODEL-001/accept-runners/sample001-acc3.sh
1
true
1
0
0
true
ACCEPT-3 EXIT=0
```

逐段对应（原文 9 段 `&&` 链）：

| 段 | 命令 | 输出 | 说明 |
|---|---|---|---|
| 2 | `--as staff --bizcode POST … internalNo=T-hli01` | `500	内部编号「T-hli01」已存在，请换一个` | 重复编号被拒（`^(400\|500)` 命中） |
| 3 | `SELECT count(*) WHERE internal_no='T-hli01'` `--eq 1` | `1` | **拒之后库里没多一行**（不是先落盘再报错） |
| 4 | `--as staff POST … internalNo=T-del99` 的 jq | `true` | 软删过的编号可重用（`code==200`） |
| 5 | `SELECT del_flag WHERE internal_no='T-del99'` `--col-set 0,1` | `1` 行 | 新行 del_flag 取值合法 |
| 6 | `--as staff --bizcode POST … organoid 不带 organoidType` | `500	「类器官」类样本缺少必填项：类器官类型` | 类器官类缺类型列被拒 |
| 7 | `SELECT count(*) WHERE internal_no='T-oco77'` `--eq 0` | `0` | **被拒的没落库** |
| 8 | `--as extA GET /mp/dict/hints?type=tissue` | `true` | `.code==200`、含「肝组织」、不含「测试供体甲」 |

```
$ # 「拒了之后库里一字不变」的完整链（P4/P5/P2 复现）
$ python3 doc/verify/db.py --sql "SELECT id||'|'||internal_no||'|'||del_flag FROM t_lqg_sample WHERE internal_no='T-del99'"
9000001010|T-del99|1
$ bash doc/verify/api.sh --as staff POST /lqg/sample '{"sampleKind":"tissue","sourceUnitName":"本中心","tissueType":"肝组织","receiveDate":"2026-09-17","internalNo":"T-del99"}' | jq -c '{code,id:.data}'
{"code":200,"id":"2102045501175201793"}
$ python3 doc/verify/db.py --sql "SELECT count(*) FROM t_lqg_sample WHERE internal_no='T-del99'" --eq 2                                    → 2
$ python3 doc/verify/db.py --sql "SELECT count(*) FROM t_lqg_sample WHERE internal_no='T-del99' AND del_flag='0'" --eq 1                   → 1
$ # 正向对照：类器官带上 organoidType 就该过（否则「一律拒绝」也能绿）
$ bash doc/verify/api.sh --as staff POST /lqg/sample '{"sampleKind":"organoid","sourceUnitName":"本中心","organoidType":"结直肠类器官","receiveDate":"2026-09-17","internalNo":"T-oco77"}' | jq -c '{code,id:.data}'
{"code":200,"id":"2102045504979435522"}
$ python3 doc/verify/db.py --sql "SELECT sample_kind||'|'||organoid_type||'|'||COALESCE(tissue_type,'-') FROM t_lqg_sample WHERE internal_no='T-oco77'" --eq "organoid|结直肠类器官|-"
```

**联想词的字典同源对照**（`sys_dict_data` vs 接口返回，逐字相同）：

```
$ python3 doc/verify/db.py --sql "SELECT dict_type||' => '||string_agg(dict_label,' / ' ORDER BY dict_sort) FROM sys_dict_data WHERE dict_type LIKE 'lqg\_hint\_%' GROUP BY dict_type ORDER BY dict_type"
lqg_hint_organoid_type => 肝类器官 / 胆管类器官 / 结直肠类器官 / 胃类器官 / 胰腺类器官
lqg_hint_sample_type => 组织 / 类器官
lqg_hint_tissue_type => 肝组织 / 胆管组织 / 结直肠组织 / 胃组织 / 胰腺组织 / 肺组织 / 乳腺组织

$ bash doc/verify/api.sh --as extA GET '/mp/dict/hints?type=tissue'
{"code":200,"msg":"操作成功","data":["肝组织","胆管组织","结直肠组织","胃组织","胰腺组织","肺组织","乳腺组织"]}
$ bash doc/verify/api.sh --as extF GET '/mp/dict/hints?type=organoid'
{"code":200,"msg":"操作成功","data":["肝类器官","胆管类器官","结直肠类器官","胃类器官","胰腺类器官"]}
$ bash doc/verify/api.sh --as staff GET '/mp/dict/hints?type=sample'
{"code":200,"msg":"操作成功","data":["组织","类器官"]}
$ bash doc/verify/api.sh --as admin GET '/mp/dict/hints?type=tissue'
{"code":200,"msg":"操作成功","data":["肝组织","胆管组织","结直肠组织","胃组织","胰腺组织","肺组织","乳腺组织"]}
$ bash doc/verify/api.sh --as extA --bizcode GET '/mp/dict/hints?type=nope'
500	联想词类型只能是 tissue / organoid / sample
```

**★ 硬核反证：联想词不是历史值**（构造一个只存在于样本表、字典里没有的值）：

```
$ docker exec lqg-dev-postgres psql -U lqg -d lqg_dev -q -c "UPDATE t_lqg_sample SET tissue_type='某某单位私有组织' WHERE id=9000001001"
$ python3 doc/verify/db.py --sql "SELECT count(*) FROM t_lqg_sample WHERE tissue_type='某某单位私有组织'" --eq 1     → 1
$ bash doc/verify/api.sh --as extA GET '/mp/dict/hints?type=tissue' | jq -e '(.data|index("某某单位私有组织")==null) and (.data|length==7)'
true
```

### 4.4 追加证据（accept 之外的机器证据）

**(a) Java 契约测试 —— 31 个用例全绿，含 D2 的 L0 门 `ExtChokepointContractTest`**

```
$ (cd code/RuoYi-Vue-Plus && mvn -pl ruoyi-modules/ruoyi-lqg test -s <ws>/.mvn-settings.xml \
      -Dmaven.repo.local=<ws>/.m2repo -Duser.home=<ws>/.buildhome)
[INFO] Running org.dromara.lqg.auth.group.guard.ExtBindStateMachineContractTest   Tests run: 11, Failures: 0, Errors: 0, Skipped: 0
[INFO] Running org.dromara.lqg.auth.staff.guard.StaffGrantRulesContractTest      Tests run:  6, Failures: 0, Errors: 0, Skipped: 0
[INFO] Running org.dromara.lqg.auth.guard.MockLoginGuardContractTest             Tests run:  4, Failures: 0, Errors: 0, Skipped: 0
[INFO] Running org.dromara.lqg.ext.ExtChokepointContractTest                     Tests run:  4, Failures: 0, Errors: 0, Skipped: 0
[INFO] Running org.dromara.lqg.sample.guard.SampleKindRulesContractTest          Tests run:  6, Failures: 0, Errors: 0, Skipped: 0   ← 本票
[INFO] Tests run: 31, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

> **L0 门是本票主动跑绿的**：ADR-0004 的 i1 会扫**整个 `org.dromara.lqg`**，断言「ext 包之外的
> controller 都不得映射 `/mp/ext` 开头的路径」。本票新增的 `HintController` 映射 `/mp/dict`，
> 不在 ext 包内、也不碰 `/mp/ext` → 四条不变量全绿（这是「按 accept 实现 `/mp/dict/**` 而不破
> ADR-0004 咽喉」的机器证据）。

**(b) 对抗性探针 14 组**（`doc/waves/reports/SAMPLE-MODEL-001/accept-runners/sample001-probes.sh`）

```
$ bash doc/waves/reports/SAMPLE-MODEL-001/accept-runners/sample001-probes.sh
P0  干净 seed 起点                                9 行有效
P1  tissue 缺 tissueType                          → 500 「组织」类样本缺少必填项：组织类型 ；库内 0 行
P2  organoid 缺 internalNo                        → 500 「类器官」类样本缺少必填项：内部编号 ；库内 0 行
P3  未知 sampleKind                               → 500 样本类别只能是 tissue… ；库内 0 行
P4  重复内部编号（T-hli02 已在 seed）              → 500 内部编号「T-hli02」已存在，请换一个 ；库内仍 1 行
P5  软删后同编号重录                               → 200 ；库内 2 行（1 行 del_flag=1）
P6  加密列只支持精确（明文 eq 0 / LIKE 0 / 密文 eq 1；接口精确 1001 / 前缀 0）
P7  联想词来自字典（构造私有组织类型，接口不带出；非法 type 500）
P8  软删行任何条件都查不到（列表 total=9 无 1010；internalNo 查 0；详情 500）
P9  外部身份进不了 /lqg/sample/**                   extA list 403 ／ extF detail 403
P10 PUT 改不动 submitNo/submitSource/submitterId    → 库内 SJ90000001|external|9000000111
P11 PUT 写回 update_by                             → staff 改后 updateByName=李工；admin 改后=测试管理员
P12 DELETE 走软删 + 编号可重用                     → del_flag=1、行还在、T-hga03 可重录
P13 送检单号走序列                                  → 连开 5 条 5 个不同号，全匹配 ^SJ[0-9]{8}$
P14 来源单位快照                                    → sourceUnitId=9000009001 + 请求里塞「假的单位名」→ 库里存「A 医院」；
                                                      单位不存在 → 500 且库内 0 行
PROBE SAMPLE-MODEL-001 ALL PASS
```

**(c) 关键 bug 的负对照（能红的探针）**

| bug | 探针 | 修复前 | 修复后 |
|---|---|---|---|
| `update(null, wrapper)` 不会自动填 `update_by`（MP 的 `updateFill` 只认 `BaseEntity` 参数对象）→ 详情「最后修改」显示的是**创建人** | §4.4(b) P11 | `updateByName=王医生`（创建人，红） | `updateByName=李工` / `测试管理员`（绿） |
| `PageQuery` 在 5.5.3 只有 `(pageSize, pageNum)` 构造器、没有无参构造 → BO 编不过 | `mvn compile` | `constructor PageQuery cannot be applied to given types` | BO 显式 `super(DEFAULT_PAGE_SIZE, DEFAULT_PAGE_NUM)` |

## §5 遗留与 raise（ticket §4.4）

### 5.1 与 ticket §2.2 字面的一处取舍：加密没有用框架 `@EncryptField` 注解

ticket §2.2 写「`donorName`、`hospitalNo` 加 `@EncryptField(algorithm = AlgorithmType.AES)`」，
**本实现没有加这个注解**，而是在 service 层用 `EncryptUtils.encryptByAes/decryptByAes` 手工读写
（`SampleFieldCipher`）。实测依据：

```
$ # 框架的 EncryptorManager.encrypt 会在密文前加 "ENC_" 前缀（Constants.ENCRYPT_HEADER）
$ sed -n '/public String encrypt/,/^    }/p' ruoyi-common/ruoyi-common-encrypt/src/main/java/org/dromara/common/encrypt/core/EncryptorManager.java
public String encrypt(String value, EncryptContext encryptContext) {
    if (StringUtils.startsWith(value, Constants.ENCRYPT_HEADER)) { return value; }
    IEncryptor encryptor = this.registAndGetEncryptor(encryptContext);
    String encrypt = encryptor.encrypt(value, encryptContext.getEncode());
    return Constants.ENCRYPT_HEADER + encrypt;      // ← "ENC_" + Base64
}
$ # 而 seed 与 ADR-0006 的既有密文是裸 Base64（gen_seed.py 的 enc() 不带前缀）
$ grep -n 'def enc' -A5 doc/verify/gen_seed.py
    return base64.b64encode(c.update(data) + c.finalize()).decode()
$ python3 doc/verify/db.py --sql "SELECT donor_name FROM t_lqg_sample WHERE id=9000001001"     → TcVJXrqPUYwPPs4JJt6NRg==   （无 ENC_）
```

两套混用的后果：seed 的 9 行读出来**还是密文**（decrypt 见不到 `ENC_` 就原样返回），
accept 第 2 条「接口返回明文」直接红。而且 accept 第 2 条的 `openssl` 对照断的是
**库里的值 == 裸 Base64**，加前缀也会红。所以本实现选「同一套 AES/ECB/PKCS5 + 同一个口令，
但由 service 层控制编码形态」——语义与 ADR-0006 完全一致（加密落库、只精确查、内部看明文），
只是载体从注解换成 `SampleFieldCipher`。**请主会话确认这个取舍**（若坚持用注解，正确做法是
同时写一支迁移把 9 行 seed 密文补上 `ENC_` 前缀，但那会改 seed 语义，属越界）。

### 5.2 与 `doc/api-contract.md` 的差异

| # | 契约写的 | 实际实现 | 判断依据 |
|---|---|---|---|
| 1 | `GET /lqg/sample/list` 参数里列了 `sourceUnitId, groupId, submitSource, receiveDateBegin/End, tissueType, operatorName` | 本票**只落五个**（`sampleKind / verifyStatus / internalNo / donorName / hospitalNo`） | ticket §2.2 明说「本张先支持五个条件，其余筛选在 SAMPLE-WEB-001 补」。BO 里**刻意不声明**未落的条件，免得写出半截筛选 |
| 2 | `DELETE /lqg/sample/{ids}` | 一致（逗号分隔） | — |
| 3 | 契约没写 `/mp/dict/hints` 的返回形状 | `data` = 字符串数组（label） | ticket §2.2 与 accept 第 3 条都断 `.data\|index("肝组织")` |

### 5.3 口径上没把握 / 需要确认

1. **`PUT /lqg/sample` 的必填校验口径**：我按「合并视角」校验——没传的字段沿用库里现值，
   只对**合并后**仍然缺的必填项报错。理由：否则「只改备注」会被必填规则误拒。
   若甲方要「PUT 也必须带全必填项」（更严、前端本来就带全），改 `SampleService.update` 的两行即可。
2. **`PUT` 把没传的字段写成 NULL**：本实现是「PUT = 整体替换」（没传的就是要清空），
   与若依上游 `updateById` 的「只改非空值」不同。依据是契约里 PUT 的入参形状与 POST 同构
   （表单整份提交），且 SAMPLE-WEB-001 的修改页会带全字段。若真要 PUT 也走 patch 语义，
   需要额外一套「字段是否出现在请求体里」的判据（`JsonNode`），属 SAMPLE-WEB-001 的口径。
3. **`SampleChildrenCheckers` 的注册方式**：本票用「Spring 注入 `Collection<SampleChildrenChecker>`」，
   下游 ticket 只要声明一个 `@Component`/`@Bean` 实现就自动生效，**不用回来改本类**。
   `registeredCount()` 现在恒 0（ticket §2.2 要求）。

### 5.4 明确没做（ticket §3 边界，逐条核对过）

不做外部提交接口（AUTH-EXT-001）、不做核验与 `/lqg/sample/{id}/verify`（SAMPLE-VERIFY-001）、
不做工作台页面（SAMPLE-WEB-001，菜单占位已留）、不做导出（SAMPLE-EXPORT-001）、
不自动生成内部编号、不解析内部编号含义、「质控表」「细胞活率报告」就是手点的 Y/N（不按文档推导）、
不给历史 Excel 导入预留任何字段（REQ-SYS-012 deferred）。

## §6 WARN 清单（交主会话落 `issue add`）

| # | severity | type | 标题 | 影响范围 / 方案 |
|---|---|---|---|---|
| WARN-1 | S3 | doc-drift | **ADR-0004 字面「外部用户能调的业务接口只有 `/mp/ext/**`」与 `/mp/dict/**`、`/mp/me`、`/mp/ocr/**` 冲突**；accept 第 3 条明确要 `--as extA` 调 `GET /mp/dict/hints?type=tissue` | 本票按 accept + `doc/api-contract.md` 第 21 行（把 `/mp/dict/**` 列进「内外部都能调」的三组）实现。ADR-0004 守的是「**业务数据**只有一条出口」，`/mp/dict/hints` 返回的是字典（系统级配置，零样本数据、零单位信息），不破咽喉——`ExtChokepointContractTest` 四条不变量全绿（本票已跑，见 §4.4(a)）。方案：把 ADR-0004 的 decision 那句补成「业务**数据**接口只有 `/mp/ext/**`；`/mp/me`、`/mp/ocr/**`、`/mp/dict/**` 是内外部共用的非数据组」，或在 `doc/api-contract.md` 注明该例外。**本票不改权威**。 |
| WARN-2 | S2 | counterfeit-risk | **框架 `@EncryptField` 的 `ENC_` 前缀与 `doc/verify/seed/`、ADR-0006 的裸 Base64 密文不是一套编码**，ticket §2.2 的字面写法（加注解）会让 seed 行读出来还是密文 | 影响**所有**要给已有 seed 行加 `@EncryptField` 的票（QC-MODEL-001 的 `t_lqg_qc_sample.patient_no` 是下一张！）。本票用 service 层手工加解密绕开（见 §5.1），并已在迁移与类的注释里写明因果。方案二选一：① 统一成「裸 Base64」并把 ticket/ADR 的措辞从「加 `@EncryptField`」改成「用 `EncryptUtils` 加密落库」；② 统一成「`ENC_` + Base64」并给 seed 密文补前缀（要改 `gen_seed.py` → 但那是只读区）。**请主会话定**，QC-MODEL-001 会立刻再撞一次。 |
| WARN-3 | S3 | clarify | `PUT /lqg/sample` 的语义（整体替换 vs 只改非空）ticket 没写 | 影响 SAMPLE-WEB-001 的修改页：本票按「表单整份提交 → 整体替换、没传的写 NULL」实现。方案：在契约那一行补一句 PUT 的字段语义。 |
| WARN-4 | S3 | debt | 本票的迁移把菜单 C 页面 `lqg/sample/index` 一起建了，而该页面文件由 SAMPLE-WEB-001 产出 | 现状影响：`lqg_admin` / `lqg_internal` 的侧边栏会出现「样本总表」一项，点进去 404（前端组件不存在）。做法理由：`@SaCheckPermission("lqg:sample:*")` 需要菜单行存在才通得过。方案：SAMPLE-WEB-001 落页面即闭环；若不想看到 404 入口，可把该 C 菜单的 `visible` 暂设 `'1'`（隐藏），页面做好再改回——本票没这么做是因为 SAMPLE-WEB-001 的 accept 可能直接取路由。 |
| WARN-5 | S2 | harness | `doc/verify/api.sh --fresh-module` 的 `ps -o lstart=` 在 subagent 沙箱被禁，accept 原样跑 `exit 2` | **已连续六张票命中**（SYS-BASE-001 / AUTH-LOGIN-001 / SYS-WEB-001 / AUTH-STAFF-001 / AUTH-GROUP-001 / 本票）。只影响 subagent 沙箱，人工 / CI 正常。本票用「`find … -newer jar` 为空（lqg + admin 两侧）+ `lsof` 拿 PID + 进程持有该 jar + 日志 mtime 晚于 jar + 嵌套 jar 内含本票 class」五项等价替代。方案：把启动时间换成 `lsof` / 日志 mtime 的兼容写法，或给 `api.sh` 加「跳过启动时间守卫」的开关（改动属验收执行器，六张票都按规矩没动 `api.sh`）。 |
| WARN-6 | S3 | harness | `doc/verify/reseed.sh` 清不掉运行时按手机号建的雪花 id 账号 | 同 AUTH-STAFF-001 WARN-7 / AUTH-GROUP-001 WARN-7（已记过两次）。本票收尾跑 `clean-orphan-accounts.sh --yes`。方案：把该脚本收进标准收尾步骤，或让 `reseed.sh` 自己清非 seed 段账号。 |

> 已在上游验收报告里记过、本票**不重复计数**的既有 WARN：Maven 三参数（AUTH-LOGIN-001 WARN-2）、
> surefire `groups` 假绿（AUTH-LOGIN-001 WARN-3）、`api.sh` token 缓存（AUTH-STAFF-001 WARN-4）、
> `updateById` 忽略 null（AUTH-GROUP-001 WARN-2）、跨域补号的 Flyway 坑（SYS-WEB-001 WARN-2）、
> `PageQuery` 只有两参构造（本票踩过、已修，见 §4.4(c)）。

## §7 坑与解法（给下游）

1. **`update(null, LambdaUpdateWrapper)` 不会自动填 `update_by`**：MP 的 `updateFill(MetaObject)` 只在
   参数对象是 `BaseEntity` 时写 `update_by`；走 wrapper 时参数对象是 wrapper 的参数 Map，落到
   `strictUpdateFill` 那一支，只填 `updateTime`。实测：`staff` 改完 seed 1001，
   `update_time` 是当下（对的）、`update_by` 还是 seed 的创建人 → 详情「最后修改：**王医生**」。
   **解法：在 wrapper 里显式 `.set(Sample::getUpdateBy, currentUserId()).set(Sample::getUpdateTime, new Date())`。**
   凡是要「记录是谁改的」的表，都要么用 `updateById`（丢掉 NULL 语义）、要么像这里显式补。
2. **再记一次 `updateById` 忽略 null**（AUTH-GROUP-001 WARN-2 的同源形态）：`PUT` 要把「住院号」清成 NULL、
   要「换类别时把另一类的类型列清成 NULL」，都必须用 `LambdaUpdateWrapper.set(col, null)`。
   本票 `SampleService.update` 整段都是 wrapper 写法。
3. **`@EncryptField` 的 `ENC_` 前缀**见 §5.1 / WARN-2：**下一张 QC-MODEL-001 会立刻再撞一次**
   （`t_lqg_qc_sample.patient_no` 是加密列，seed 里的密文同样是裸 Base64）。
4. **`PageQuery` 在 5.5.3 只有 `(pageSize, pageNum)` 构造器**：继承它的筛选 BO 必须自己写无参构造
   （`super(DEFAULT_PAGE_SIZE, DEFAULT_PAGE_NUM)`），否则编译期就报
   `constructor PageQuery cannot be applied to given types`。别指望 Lombok 的 `@NoArgsConstructor`
   （它会调 `super()`，同样编不过）。
5. **PG 的部分唯一索引是「软删后可重用」的全部依据，但应用层的查重也必须走 `@TableLogic` 过滤后的
   查询**：本票用 `sampleMapper.selectCount(LambdaQueryWrapper.eq(internalNo, v))` —— 软删行查不到，
   所以 `T-del99` 能重录；如果哪天有人为了「查重更严」改成原生 SQL（`WHERE internal_no=?` 不带
   `del_flag`），accept 第 3 条第 4 段立刻红。**结论错误信息也要给人话**（「内部编号「X」已存在，请换一个」），
   别把 PG 的 `duplicate key` 直接抛给用户。

## §8 验证用长进程（ticket §4.5）

- **后端 java（8081）：收尾时已关**（`lsof -ti tcp:8081` 拿 PID → `kill <PID>`；**没用 `pkill -f ruoyi-admin.jar`**，
  本机 8080 上有 Kevin 的另一个 java 服务）。起停脚本 `.tmp/run-backend.sh`（gitignore）可复用。
- **docker 容器：留着**（`lqg-dev-postgres` 5433 / `lqg-dev-redis` 6380 / `lqg-dev-minio` 9002+9003），
  起停 `docker compose -f code/deploy/dev/docker-compose.yml up -d|down`。
- **未占 8080 / 5432 / 6379 / 9000 / 9001**（Kevin 本机日常服务）。
- **DB 收尾** = `reseed.sh --yes` 后的干净 seed + `doc/waves/tools/clean-orphan-accounts.sh --yes`
  （输出：清掉孤儿账号 0 个；同手机号多行 0；非 seed 段账号 0）。库内 `t_lqg_sample` 复核 = 9 行
  `del_flag='0'` + 1 行 `del_flag='1'`（seed 原样）。
- 前端**一个字节没动**，本票没有起过任何前端进程。
