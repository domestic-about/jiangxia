// SYS-ACCEPT-001 · 行为判据探针的公共段。
//
// ★ 本目录是「把 D7 r1 L2 已经证明有效的判据固化成 accept」的落点：六个热点的判据输入
//   全部来自**运行中的系统**（真请求头 / 真 DOM 读数 / 真 URL / 真响应码），不是源码字面量。
//   H3a 是唯一允许读源码的例外（先剥注释再断言，证明与注释无关）。
//
// ★ Playwright 的 require 锚点 = `code/miniapp/package.json`（用 plus-ui 会 MODULE_NOT_FOUND，issue #229）。
// ★ 截图一律不落盘 —— 本目录只落 JSON 证据，PNG 不读进上下文。
import { createRequire } from 'node:module'
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

// ★ 2026-09-23 按 CR-20260923-09 更新：ROOT 不再写死工作区（副本里重放会连到工作区的 node_modules、把证据写回工作区）
//   → 从本文件位置推导（accept-strengthened → D7 → regression → waves → doc → 仓库根）；
//   端口与 OSS 直链前缀从环境变量读，缺省沿用原值（8093 / 9204 / 127.0.0.1:9000）。
export const OUT = path.dirname(fileURLToPath(import.meta.url))
export const ROOT = path.resolve(OUT, '..', '..', '..', '..', '..')
if (!fs.existsSync(path.join(ROOT, 'code/miniapp/src/pages.json'))) {
  console.error(`[error] ROOT 推导错了：${ROOT} 下没有 code/miniapp/src/pages.json`)
  process.exit(2)
}
export const OBS = path.join(OUT, 'observations')
export const WEB = `http://127.0.0.1:${process.env.LQG_ACCEPT_WEB_PORT || 8093}`
export const MP = `http://127.0.0.1:${process.env.LQG_ACCEPT_MP_PORT || 9204}`
// OSS 直链前缀：sys_oss_config 指向哪台 MinIO 就是哪台（2026-09-23 起 dev 库改指 jiangxia 自己的 MinIO）
export const OSS = (process.env.LQG_ACCEPT_OSS_BASE || 'http://127.0.0.1:9000').replace(/\/?$/, '/')

export function playwright() {
  const require = createRequire(`${ROOT}/code/miniapp/package.json`)
  return require('playwright')
}

/** 判据收集器：rec(name, ok, detail) + observed 键值（真观测值，给 evidence.json 用） */
export function collector(hotspot) {
  const results = []
  const observed = {}
  const rec = (name, ok, detail = '') => {
    results.push({ name, ok: !!ok, detail: String(detail).slice(0, 1200) })
    console.log(`${ok ? 'PASS' : 'FAIL'}  ${name}${detail ? '  :: ' + String(detail).slice(0, 420) : ''}`)
    return !!ok
  }
  const note = (k, v) => { observed[k] = v; return v }
  const finish = (meta = {}) => {
    const failed = results.filter(r => !r.ok)
    const payload = {
      hotspot,
      green: failed.length === 0,
      ran_at: new Date().toISOString(),
      results,
      observed,
      ...meta,
    }
    fs.mkdirSync(OBS, { recursive: true })
    fs.writeFileSync(path.join(OBS, `${hotspot}.json`), JSON.stringify(payload, null, 1))
    console.log(`\n==== ${hotspot}: ${results.length - failed.length}/${results.length} PASS → ${payload.green ? 'GREEN（判据成立）' : 'RED（判据被违反）'} ====`)
    if (failed.length) console.log('FAILED:', failed.map(f => f.name).join(' | '))
    return payload.green
  }
  return { rec, note, finish, results, observed }
}

/** 统一收尾：关浏览器、按判据结果 exit（0=绿 / 1=红） */
export async function done(browser, green) {
  await browser.close()
  process.exit(green ? 0 : 1)
}

export function headerOf(headers, name) {
  const k = Object.keys(headers || {}).find(x => x.toLowerCase() === name.toLowerCase())
  return k ? headers[k] : ''
}

/** 工作台（8093）登录：dev 下 captcha.enable=false */
export async function loginWeb(page) {
  await page.goto(`${WEB}/login`, { waitUntil: 'domcontentloaded' })
  await page.waitForSelector('input[type="text"]', { timeout: 30000 })
  await page.fill('input[type="text"]', 'lqgadmin')
  await page.fill('input[type="password"]', 'admin123')
  await page.getByRole('button', { name: /登\s*录/ }).first().click()
  await page.waitForURL(u => !u.pathname.includes('/login'), { timeout: 30000 })
}

/** 小程序 H5（9204）内部人员 mock 登录（第 1 个调试按钮 = 内部人员·李工） */
export async function loginH5Internal(page) {
  await page.goto(`${MP}/#/pages/login/index`, { waitUntil: 'networkidle' })
  await page.waitForSelector('.login__mock-btn', { timeout: 40000 })
  await page.locator('.login__box').click()          // 点勾选框本体：点整行会命中《用户协议》链接
  await page.waitForTimeout(200)
  await page.locator('.login__mock-btn').nth(0).click()
  await page.waitForTimeout(4000)
  return !page.url().includes('login')
}
