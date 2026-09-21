// dev 构建下的登录页截图：证明「调试入口只在 dev 存在」（对比生产产物里没有）
import { chromium } from 'playwright'

const OUT = '/Users/wkui/Project/profile/project/freelance/projects/jiangxia-organoid/doc/waves/reports/SYS-MP-001'
const browser = await chromium.launch()
const ctx = await browser.newContext({ viewport: { width: 390, height: 844 }, deviceScaleFactor: 2 })
const page = await ctx.newPage()
await page.goto('http://127.0.0.1:9201/#/pages/login/index', { waitUntil: 'networkidle' })
await page.waitForTimeout(2500)
const mock = await page.locator('.login__mock').count()
const seeds = await page.locator('.login__mock-btn').allInnerTexts()
console.log('dev login page: .login__mock count =', mock, 'seeds =', JSON.stringify(seeds))
await page.screenshot({ path: `${OUT}/01b-login-dev-mock.png`, fullPage: true })
await ctx.close()
await browser.close()
