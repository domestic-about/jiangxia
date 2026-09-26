// 内部管理表格页「导出 Excel」的**纯函数层**（SYS-EXPORT-001 · UI:mp.ledger ④ · REQ-SYS-017）。
//
// ★ 这一层只做两件事，**不 import 网络层、不 import `uni`**（`export.spec.ts` 在 node 环境
//   直接 import 它跑单测）：
//   ① `exportUrl(sheet, filters)` —— 把「当前工作表 + 当前筛选」拼成导出端点地址；
//   ② `authHeader()` —— **导出**要带的头（导出端点是后端鉴权接口；文档下载是 OSS 签名链接，不带头）。
//
// ★★ **同一个导出视图**（ticket §0 口径 1）：地址指向 `/mp/int/export/{sheet}`，后端把参数
//   交给**工作台那三个域已有的导出 service**。所以「列序 / 表头」在这里**一个字都不写** ——
//   前端的表格列名只在 `pages/ledger/columns.ts`（表格显示用），导出的表头在服务端。
//
// ★ **筛选参数名与表格页各表 list 逐字相同**（ticket §0 口径 2：用户筛了什么就导出什么）：
//   · 样本两张表：`keyword` / `verifyStatus`（= `GET /mp/int/sample/list` 的那两个）
//   · 石蜡包埋：`keyword` / `verifyStatus` / `stain`
//   · -80 冻存：`overdueOnly`（-80 超期页签）/ `location='ln2'`（液氮页签）/ `emptiedOnly`（已取空页签）
//   · 空值一律**不带这个参数**（带了空串会被后端当成「筛空串」，行数就不是全部）
//
// ★ 为什么要拼**相对**地址：根地址的判据只有一处（`utils/baseUrl.ts`）。本文件保持纯函数，
//   根地址在真正发起请求的那一层（`fileHandoff.downloadToTemp` 的调用方）拼。
import { getToken } from '@/utils/auth'
import type { LedgerFilters } from '@/api/ledger'

/** 四张工作表的键（与 `pages/ledger/sheets.ts` 的注册表、后端 `ExcelSheet` 逐字一致） */
export type ExportSheet = 'tissue' | 'organoid' | 'embed' | 'cryo'

/** 导出端点的前缀（契约第 55 行：`GET /mp/int/export/{sheet}`） */
export const EXPORT_PATH_PREFIX = '/mp/int/export/'

/** 认不认识这个工作表（`?sheet=` 传进来的是未知串时，页面上导不出东西） */
export function isExportSheet(value: unknown): value is ExportSheet {
  return value === 'tissue' || value === 'organoid' || value === 'embed' || value === 'cryo'
}

/** 只保留「真的有值」的键：空串 / null / undefined 都不进查询串 */
function push(pairs: string[], key: string, value: unknown) {
  if (value === undefined || value === null) {
    return
  }
  const text = String(value)
  if (text === '') {
    return
  }
  pairs.push(`${key}=${encodeURIComponent(text)}`)
}

/**
 * 一张工作表的筛选参数（顺序固定：测试断的是**整串**，顺序漂移要能立刻看出来）。
 *
 * ★ 只有该表真的有的参数才带：给冻存带 `verifyStatus`、给样本带 `stain` 都会被后端当成
 *   一个不存在的条件（样本表没有染色列；冻存表没有核验状态）→ 空页。
 */
function queryOf(sheet: ExportSheet, filters?: Partial<LedgerFilters> | null): string {
  const f = filters ?? {}
  const pairs: string[] = []
  switch (sheet) {
    case 'tissue':
    case 'organoid':
      push(pairs, 'keyword', f.keyword)
      push(pairs, 'verifyStatus', f.verifyStatus)
      break
    case 'embed':
      push(pairs, 'keyword', f.keyword)
      push(pairs, 'verifyStatus', f.verifyStatus)
      push(pairs, 'stain', f.stain)
      break
    case 'cryo':
      // 四个页签：全部（都不带）/ -80 超期（overdueOnly=true）/ 液氮（location=ln2）/ 已取空（emptiedOnly=true）
      push(pairs, 'overdueOnly', f.cryoView === 'overdue' ? 'true' : '')
      push(pairs, 'location', f.cryoView === 'ln2' ? 'ln2' : '')
      push(pairs, 'emptiedOnly', f.cryoView === 'emptied' ? 'true' : '')
      break
  }
  return pairs.length > 0 ? `?${pairs.join('&')}` : ''
}

/**
 * 「按当前筛选导出」的地址（相对地址；根地址由发起请求的那一层拼）。
 *
 * ```ts
 * exportUrl('tissue', { verifyStatus: 'pending' })  // '/mp/int/export/tissue?verifyStatus=pending'
 * exportUrl('cryo', { cryoView: 'overdue' })        // '/mp/int/export/cryo?overdueOnly=true'
 * exportUrl('embed', { stain: '', keyword: '' })     // '/mp/int/export/embed'（空值不带）
 * ```
 */
export function exportUrl(sheet: ExportSheet, filters?: Partial<LedgerFilters> | null): string {
  return `${EXPORT_PATH_PREFIX}${sheet}${queryOf(sheet, filters)}`
}

/**
 * **导出**要带的请求头：**必须带鉴权**（Accept 2 第 5 段）。
 *
 * ★ 导出端点是鉴权接口（`/mp/int/**` 是 `lqg_internal` 角色面），不像**文档下载**那样拿 OSS
 *   签名链接 —— 所以 `wx.downloadFile` 不带这个头，真机上拿到的会是一段 401 的 JSON，
 *   当成 xlsx 打开就报「文件已损坏」。
 *   ★ 反过来说：这个头**只跟导出走**。文档下载（OSS 预签名直链）一个头都不带 —— 带了会被
 *     对象存储判「多重认证」400（D7 返工单 r1-S1）。调用点用 `downloadToTemp` 的
 *     `requireAuth: true` 显式声明「这条链路要鉴权」。
 *
 * ★ `clientid` 也要带：后端按它取租户 / 客户端上下文（`utils/request.ts` 的普通请求
 *   也是这两个头）。
 */
export function authHeader(): Record<string, string> {
  const header: Record<string, string> = {
    'clientid': (import.meta.env.VITE_APP_CLIENT_ID as string) || '',
  }
  const token = getToken()
  if (token) {
    header.Authorization = `Bearer ${token}`
  }
  return header
}

/**
 * 导出文件的名字（服务端 `Content-Disposition` 里给的那个名；取不到时的本地兜底）。
 *
 * ★ 真正的名字由**服务端**给（`<工作表名>-<yyyyMMddHHmmss>.xlsx`）——
 *   前端不另立一套规则，只在拿不到响应头时兜一句人能看懂的。
 */
export function exportFileName(sheet: ExportSheet, at: Date = new Date()): string {
  const base: Record<ExportSheet, string> = {
    tissue: '样本记录信息表',
    organoid: '类器官收样记录',
    embed: '石蜡包埋送样记录',
    cryo: '-80冻存',
  }
  const pad = (n: number) => String(n).padStart(2, '0')
  const stamp = `${at.getFullYear()}${pad(at.getMonth() + 1)}${pad(at.getDate())}`
    + `${pad(at.getHours())}${pad(at.getMinutes())}${pad(at.getSeconds())}`
  return `${base[sheet]}-${stamp}.xlsx`
}
