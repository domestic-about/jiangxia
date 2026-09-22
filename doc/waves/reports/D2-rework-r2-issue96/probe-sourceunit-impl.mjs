/**
 * impl 侧探针（issue #96 / S1）—— 能红能绿，退出码即判据。
 *
 * QA 的 `doc/waves/regression/D2/L2r2-probe-sourceunit.mjs` 只给 CONFIRMED_BUG / NOT_REPRODUCED 两个词，
 * 修完不会说 FIXED；本探针是等价的**接口级**探针，修前 BROKEN、修后 FIXED：
 *   ① 核心：`sourceUnitId=9000009002`（B 大学）必须 == 库内 `source_unit_id=9000009002` 的集合
 *      （seed = {SJ90000006 外部, SJ90000009 内部}），修前只回 SJ90000006；
 *   ② 同类清扫：`SampleQueryBo` 的每个筛选参数对「内部录的行」都要成立，期望值一律**库内现算**，
 *      不抄 accept 里的常数；
 *   ③ 回归护栏：`sourceUnitId=9000009001` 仍 6 条（A 医院下没有内部样本）、`groupId=9000009101` 仍 5 条
 *      （组别口径不变，仍走提交人的外部档案）。
 *
 * 跑法（需 8081 dev 后端 + reseed 后的库）：node doc/waves/reports/D2-rework-r2-issue96/probe-sourceunit-impl.mjs
 * 退出码：0 = 全部通过（FIXED）；1 = 有红（BROKEN）。
 */
import { execFileSync } from 'node:child_process'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const HERE = path.dirname(fileURLToPath(import.meta.url))
const WS = path.resolve(HERE, '../../../..')
const VIS = "del_flag='0'"

const psql = sql => execFileSync('psql',
  ['-h', '127.0.0.1', '-p', '5433', '-U', 'lqg', '-d', 'lqg_dev', '-Atc', sql],
  { env: { ...process.env, PGPASSWORD: 'lqg_dev_pwd' }, encoding: 'utf8' }).trim()
const dbSet = sql => {
  const s = psql(sql)
  return s === '' ? [] : s.split('\n').map(x => x.trim()).filter(Boolean)
}
const api = qs => {
  const body = execFileSync('bash', ['doc/verify/api.sh', '--as', 'staff', 'GET', `/lqg/sample/list?pageSize=100&${qs}`],
    { cwd: WS, encoding: 'utf8' })
  try {
    return JSON.parse(body)
  } catch (e) {
    throw new Error(`api 返回不是 JSON（qs=${qs}）：${body.slice(0, 200)}`)
  }
}
const sortJoin = a => [...a].sort().join(',')

let bad = 0
const out = []
const check = (id, name, got, expect, extra = '') => {
  const ok = sortJoin(got) === sortJoin(expect)
  if (!ok) bad++
  out.push(`${ok ? 'PASS' : 'FAIL'} ${id} ${name} got=[${sortJoin(got)}] expect=[${sortJoin(expect)}]${extra ? ' ' + extra : ''}`)
  return ok
}
// 期望集合的后端口径：API 的 submitNo ↔ 库内 submit_no
const dbCodes = sql => dbSet(sql).map(n => n)

const UNIT_B = 9000009002
const UNIT_A = 9000009001
const GROUP_A1 = 9000009101

// ── ① 核心：来源单位按样本行自己的 source_unit_id ─────────────────────────────
const rUnitB = api(`sourceUnitId=${UNIT_B}`)
const expUnitB = dbCodes(`SELECT submit_no FROM t_lqg_sample WHERE ${VIS} AND source_unit_id=${UNIT_B} ORDER BY submit_no`)
check('C1', 'sourceUnitId=9000009002(B 大学) == 库内样本行 source_unit_id 集合',
  rUnitB.rows.map(r => r.submitNo), expUnitB, `total=${rUnitB.total}`)
const c1ok = sortJoin(rUnitB.rows.map(r => r.submitNo)) === sortJoin(expUnitB)
if (c1ok && rUnitB.total !== expUnitB.length) { bad++; out.push(`FAIL C1b total 与集合大小不一致 total=${rUnitB.total} set=${expUnitB.length}`) }
else out.push(`INFO C1b total=${rUnitB.total} set=${expUnitB.length}`)

// 内部录的那条确实被这一条件带出来（内部行没有任何外部档案）
const rUnitBInternal = api(`sourceUnitId=${UNIT_B}&submitSource=internal`)
check('C2', 'sourceUnitId=9000009002 + submitSource=internal == 库内集合',
  rUnitBInternal.rows.map(r => r.submitNo),
  dbCodes(`SELECT submit_no FROM t_lqg_sample WHERE ${VIS} AND source_unit_id=${UNIT_B} AND submit_source='internal' ORDER BY submit_no`))
const internalRow = rUnitB.rows.find(r => r.submitSource === 'internal')
out.push(`${internalRow ? 'PASS' : 'FAIL'} C3 该条件下返回了 submit_source=internal 的行（无外部档案也能筛出） submitNo=${internalRow ? internalRow.submitNo : 'NONE'}`)
if (!internalRow) bad++

// 组合：单位(样本行) + 组别(提交人档案) 两条口径各管各的，AND 起来
check('C4', 'sourceUnitId=9000009002 + groupId=9000009103 == 库内 join 现算',
  api(`sourceUnitId=${UNIT_B}&groupId=9000009103`).rows.map(r => r.submitNo),
  dbCodes(`SELECT s.submit_no FROM t_lqg_sample s JOIN t_lqg_ext_profile p ON p.user_id=s.submitter_id
           WHERE s.del_flag='0' AND s.source_unit_id=${UNIT_B} AND p.group_id=9000009103 AND p.del_flag='0' ORDER BY s.submit_no`))

// 空命中不许退化成全表（停用单位没有样本）
const rUnitEmpty = api(`sourceUnitId=9000009003`)
check('C5', 'sourceUnitId=9000009003(无样本) == 空集（不许退化成全表）',
  rUnitEmpty.rows.map(r => r.submitNo), [], `total=${rUnitEmpty.total}`)

// ── ② 回归护栏：这两条与改前必须一模一样 ────────────────────────────────────
const rUnitA = api(`sourceUnitId=${UNIT_A}`)
check('C6', 'sourceUnitId=9000009001(A 医院) 仍 == 库内 source_unit_id 集合（应 6 条，与改前一致）',
  rUnitA.rows.map(r => r.submitNo),
  dbCodes(`SELECT submit_no FROM t_lqg_sample WHERE ${VIS} AND source_unit_id=${UNIT_A} ORDER BY submit_no`))
const rGroup = api(`groupId=${GROUP_A1}`)
check('C7', 'groupId=9000009101 仍 == 库内「提交人档案」join 现算（应 5 条，口径不变）',
  rGroup.rows.map(r => r.submitNo),
  dbCodes(`SELECT s.submit_no FROM t_lqg_sample s JOIN t_lqg_ext_profile p ON p.user_id=s.submitter_id
           WHERE s.del_flag='0' AND p.del_flag='0' AND p.group_id=${GROUP_A1} ORDER BY s.submit_no`))

// ── ③ 同类清扫：SampleQueryBo 每个筛选对内部录的行都要成立（期望库内现算）────
const int = api('internalNo=T-oco01').rows[0]
// 加密列用一个真有值的内部行（seed 1008 = T-hli05）
const intEnc = api('internalNo=T-hli05').rows[0]
if (!int || !intEnc) { bad++; out.push('FAIL C8 取不到内部行（T-oco01 / T-hli05），后续清扫无法进行') }
else {
  const sweep = [
    ['C8', 'internalNo=T-oco01', 'internalNo=T-oco01', `SELECT submit_no FROM t_lqg_sample WHERE ${VIS} AND internal_no='T-oco01'`],
    ['C9', 'sampleKind=organoid', 'sampleKind=organoid', `SELECT submit_no FROM t_lqg_sample WHERE ${VIS} AND sample_kind='organoid'`],
    ['C10', 'submitSource=internal', 'submitSource=internal', `SELECT submit_no FROM t_lqg_sample WHERE ${VIS} AND submit_source='internal'`],
    ['C11', 'verifyStatus=valid + submitSource=internal', 'verifyStatus=valid&submitSource=internal',
      `SELECT submit_no FROM t_lqg_sample WHERE ${VIS} AND verify_status='valid' AND submit_source='internal'`],
    ['C12', `tissueType=${intEnc.tissueType}（模糊·内部行）`, `tissueType=${encodeURIComponent(intEnc.tissueType)}`,
      `SELECT submit_no FROM t_lqg_sample WHERE ${VIS} AND tissue_type LIKE '%${intEnc.tissueType}%'`],
    ['C13', `operatorName=${int.operatorName}（模糊）`, `operatorName=${encodeURIComponent(int.operatorName)}`,
      `SELECT submit_no FROM t_lqg_sample WHERE ${VIS} AND operator_name LIKE '%${int.operatorName}%'`],
    ['C14', `receiveDate 区间含该内部行 =${int.receiveDate}`, `receiveDateBegin=${int.receiveDate}&receiveDateEnd=${int.receiveDate}`,
      `SELECT submit_no FROM t_lqg_sample WHERE ${VIS} AND receive_date BETWEEN '${int.receiveDate}' AND '${int.receiveDate}'`],
    // 加密列：查询值取接口自己解出来的明文（库里是密文，无法现算），期望 = 该内部行本身
    ['C15', `donorName=${intEnc.donorName}（精确·加密列·内部行）`, `donorName=${encodeURIComponent(intEnc.donorName)}`, null],
    ['C16', `hospitalNo=${intEnc.hospitalNo}（精确·加密列·内部行）`, `hospitalNo=${encodeURIComponent(intEnc.hospitalNo)}`, null],
  ]
  for (const [id, name, qs, sql] of sweep) {
    if ((id === 'C15' && !intEnc.donorName) || (id === 'C16' && !intEnc.hospitalNo)) {
      out.push(`SKIP ${id} ${name}（该内部行此列为空）`)
      continue
    }
    const got = api(qs).rows.map(r => r.submitNo)
    const exp = sql ? dbCodes(sql) : [intEnc.submitNo]
    check(id, name, got, exp)
  }
}

// ── ④ 口径边界记账：按 id 筛，不按名字（同名不同 id 时以 id 为准）────────────
const nameMismatch = dbCodes(`SELECT submit_no FROM t_lqg_sample WHERE ${VIS} AND source_unit_name=(SELECT unit_name FROM t_lqg_source_unit WHERE id=${UNIT_B}) AND (source_unit_id IS NULL OR source_unit_id<>${UNIT_B})`)
out.push(`INFO C17 seed 中「名字同为 B 大学但 id 不同/为空」的行 = ${nameMismatch.length ? nameMismatch.join(',') : '无'}；sourceUnitId 以样本行 source_unit_id 为准，不按名字兜底`)

console.log(out.join('\n'))
console.log(`VERDICT ${bad === 0 ? 'FIXED' : 'BROKEN'} (bad=${bad})`)
process.exit(bad === 0 ? 0 : 1)
