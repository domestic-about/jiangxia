/**
 * D3 / r1 / L2 —— 本片自写的共享 helper（lib 文件，不是断言证据）。
 * 纪律：不读图、不 mock、只连真后端(8081)+真库(5433)+真 seed；小程序侧 = miniapp H5(9200) + Playwright。
 */
import { createRequire } from 'node:module'
import { mkdirSync } from 'node:fs'
import { execFileSync } from 'node:child_process'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

export const HERE = path.dirname(fileURLToPath(import.meta.url))
export const WS = path.resolve(HERE, '../../../..')
const require = createRequire(path.join(WS, 'code/miniapp/package.json'))
export const { chromium } = require('playwright')

export const MP = process.env.LQG_H5_BASE || 'http://127.0.0.1:9200'
export const WEB = process.env.LQG_WEB_BASE || 'http://127.0.0.1:8082'
export const API = 'http://127.0.0.1:8081'

export function shotsDir(name) {
  const p = path.join(HERE, `L2-shots/${name}`)
  mkdirSync(p, { recursive: true })
  return p
}

export function makeChecker(tag) {
  const results = []
  const check = (name, ok, detail) => {
    results.push({ name, ok: !!ok, detail: detail === undefined ? '' : String(detail) })
    console.log(`${ok ? 'PASS' : 'FAIL'}  ${name}${detail !== undefined ? ` — ${detail}` : ''}`)
  }
  const finish = () => {
    const bad = results.filter(r => !r.ok)
    console.log(`\n== ${tag} ${results.length - bad.length}/${results.length} 通过 ==`)
    for (const b of bad) console.log(`  RED: ${b.name} — ${b.detail}`)
    return bad.length
  }
  return { results, check, finish }
}

export function dbOne(sql) {
  try {
    return execFileSync('psql', ['-h', '127.0.0.1', '-p', '5433', '-U', 'lqg', '-d', 'lqg_dev', '-Atc', sql],
      { env: { ...process.env, PGPASSWORD: 'lqg_dev_pwd' }, encoding: 'utf8' }).trim()
  }
  catch (e) { return '__ERR__' + e.message.slice(0, 120) }
}

export function dbRows(sql) {
  const out = dbOne(sql)
  return out === '' ? [] : out.split('\n')
}

/** 任意 seed 身份的 mp token（走真后端 mock 登录，不 mock 业务） */
export function mpToken(mockKey, phone) {
  const client = '22b2aecd0710671691ec1c07f2542b9d'
  const body = JSON.stringify({ clientId: client, grantType: 'xcx', tenantId: '000000', xcxCode: `mock:${mockKey}`, phoneCode: `mock:${phone}` })
  const out = execFileSync('curl', ['-sS', '-m', '15', '-X', 'POST', `${API}/auth/login`,
    '-H', 'Content-Type: application/json', '-H', `clientid: ${client}`, '-d', body], { encoding: 'utf8' })
  const j = JSON.parse(out)
  if (j.code !== 200) throw new Error(`mpToken(${mockKey}) 登录失败：${out.slice(0, 200)}`)
  return j.data.access_token
}

/** api.sh 的通用调用（真后端；--bizcode 时返回 "HTTPCODE body"） */
export function api(args) {
  return execFileSync('bash', ['doc/verify/api.sh', ...args], { cwd: WS, encoding: 'utf8' })
}

export const SEED_LABEL = {
  staff: '内部人员 · 李工',
  extA: '外部人员 · 王医生（已核验）',
}

/** 清 storage + 硬 reload 复位（hash 路由下 goto 不重载、勾选状态会残留） */
export async function resetToLogin(page) {
  await page.goto(`${MP}/#/pages/login/index`, { waitUntil: 'domcontentloaded' })
  await page.evaluate(() => { localStorage.clear(); sessionStorage.clear() })
  await page.reload({ waitUntil: 'domcontentloaded' })
  await page.waitForSelector('.login__agree', { timeout: 30000 })
  await page.waitForTimeout(400)
}

/** 先勾协议再点调试登录（幂等） */
export async function mockLogin(page, key) {
  const label = SEED_LABEL[key] || key
  const on = await page.$eval('.login__box', el => el.className.includes('login__box--on')).catch(() => false)
  if (!on) await page.click('.login__box')
  await page.click(`.login__mock-btn:has-text("${label}")`)
  await page.waitForSelector('.lqg-tile, .me__sec, .his__switch, .ledger', { timeout: 30000 })
  await page.waitForTimeout(900)
}

export async function injectToken(page, token) {
  await page.evaluate(t => {
    localStorage.clear()
    localStorage.setItem('lqg_mp_token', t)
  }, token)
}

export async function gotoPage(page, url, waitMs = 2600) {
  await page.goto(`${MP}/#/${url}`, { waitUntil: 'domcontentloaded' })
  await page.reload({ waitUntil: 'domcontentloaded' })
  await page.waitForTimeout(waitMs)
}

export async function bodyText(page) {
  return page.evaluate(() => document.body.innerText)
}

const ROW_SEL = '.wd-input.is-cell, .wd-cell'
export async function rowIndex(page, label) {
  return page.evaluate(([sel, lb]) => {
    const rows = [...document.querySelectorAll(sel)]
    return rows.findIndex(e => {
      const t = e.querySelector('.wd-input__label-inner, .wd-cell__title')
      return t && t.innerText.trim() === lb
    })
  }, [ROW_SEL, label])
}

export async function fillByLabel(page, label, value) {
  const i = await rowIndex(page, label)
  if (i < 0) throw new Error(`找不到字段「${label}」的行`)
  const row = page.locator(ROW_SEL).nth(i)
  await row.waitFor({ state: 'visible', timeout: 12000 })
  const input = row.locator('input,textarea').first()
  if (await input.count() === 0) throw new Error(`字段「${label}」没有可输入控件（只读？）`)
  await input.click()
  await input.fill('')
  await input.type(value, { delay: 25 })
  return true
}

export async function rowText(page, label) {
  const i = await rowIndex(page, label)
  if (i < 0) return '__NO_ROW__'
  return page.evaluate(([sel, idx]) => {
    const r = [...document.querySelectorAll(sel)][idx]
    const inp = r.querySelector('input,textarea')
    const val = inp ? inp.value : ''
    const seg = r.querySelector('.lqg-seg__item--on')
    return val || (seg ? seg.innerText.trim() : '') || (r.querySelector('.wd-cell__value') || {}).innerText || r.innerText.replace(/\n/g, '|')
  }, [ROW_SEL, i])
}

export async function segPick(page, label, option) {
  const i = await rowIndex(page, label)
  if (i < 0) throw new Error(`找不到字段「${label}」的行`)
  const row = page.locator(ROW_SEL).nth(i)
  await row.waitFor({ state: 'visible', timeout: 12000 })
  await row.locator('.lqg-seg__item', { hasText: option }).first().click()
  return true
}

export async function segValue(page, label) {
  const i = await rowIndex(page, label)
  if (i < 0) return '__NO_ROW__'
  return page.evaluate(([sel, idx]) => {
    const r = [...document.querySelectorAll(sel)][idx]
    const on = r.querySelector('.lqg-seg__item--on')
    return on ? on.innerText.trim() : '__NONE_ON__'
  }, [ROW_SEL, i])
}

export async function historyRows(page) {
  return page.evaluate(() => [...document.querySelectorAll('.his__item')].map(e => {
    const code = e.querySelector('.scard__code')
    const st = e.querySelector('.lqg-tag')
    return {
      code: code ? code.innerText.trim() : '',
      statusText: st ? st.innerText.replace(/\n/g, '|').trim() : '',
      text: e.innerText.replace(/\n/g, '|'),
    }
  }))
}

export async function gotoHistory(page, tabText) {
  await gotoPage(page, 'pages/history/index')
  await page.waitForSelector('.his__switch', { timeout: 25000 })
  await page.waitForTimeout(1200)
  if (tabText) {
    await page.locator('.lqg-sheets__item', { hasText: tabText }).first().click()
    await page.waitForTimeout(2000)
  }
}

export function sleep(ms) { return new Promise(r => setTimeout(r, ms)) }
