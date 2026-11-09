// 飞书 revision 337：小程序26–27，网页工作台22–25。本机 API + 实际工作台回归。
// 运行：node doc/waves/regression/staging-hardening/32-feishu-1109.mjs
// 临时新建三条样本，finally 通过业务 API 删除；不改已有业务记录、不 reseed。
import assert from 'node:assert/strict';
import { createRequire } from 'node:module';
import fs from 'node:fs';
import path from 'node:path';
const require = createRequire(path.resolve('code/miniapp/package.json'));
const { chromium } = require('playwright');
const API = process.env.LQG_API_BASE || 'http://127.0.0.1:18081';
const WEB = process.env.LQG_WEB_BASE || 'http://127.0.0.1:18082';
for (const url of [API, WEB]) assert(['localhost', '127.0.0.1'].includes(new URL(url).hostname), '仅允许本机回归');
const OUT = '/tmp/jiangxia-acceptance-20261109';
fs.mkdirSync(OUT, { recursive: true });
const client = fs.readFileSync('code/plus-ui/.env.development', 'utf8').match(/VITE_APP_CLIENT_ID\s*=\s*['"]([^'"]+)/)[1];
const password = process.env.LQG_TEST_PASSWORD || 'admin123';
const tag = `feishu1109-${Date.now()}`;
let token, browser;
const created = [];
const check = (name, value) => { assert(value, name); console.log(`PASS ${name}`); };
async function api(method, route, body) {
  const response = await fetch(API + route, { method, signal: AbortSignal.timeout(20000), headers: {
    'Content-Type': 'application/json', clientid: client, ...(token ? { Authorization: `Bearer ${token}` } : {})
  }, ...(body === undefined ? {} : { body: JSON.stringify(body) }) });
  const result = await response.json();
  assert.equal(result.code, 200, `${route}: ${result.msg}`);
  return result;
}
try {
  token = (await api('POST', '/auth/login', { username: 'lqgadmin', password, clientId: client, tenantId: '000000', grantType: 'password' })).data.access_token;
  for (const [kind, category] of [['tissue', '肝'], ['tissue', '肾'], ['organoid', '胆管']]) {
    const body = { sampleKind: kind, sourceUnitName: tag, species: '人', internalNo: `${tag}-${created.length}`,
      receiveDate: '2026-11-09', processTime: '2026-11-09 09:30:00',
      [kind === 'tissue' ? 'tissueType' : 'organoidType']: category };
    const result = await api('POST', '/lqg/sample', body);
    created.push(String(result.data));
  }
  check('组织类别沿用样本手填值，三条样本保存并回读', (await api('GET', `/lqg/qc/list?keyword=${tag}`)).rows.length === 3);
  for (const category of ['肝', '肾', '胆管']) {
    const rows = (await api('GET', `/lqg/qc/list?keyword=${tag}&typeName=${encodeURIComponent(` ${category} `)}`)).rows;
    check(`组织类别筛选「${category}」只返回对应样本`, rows.length === 1 && rows[0].typeName === category);
  }
  check('组织类别筛选与样本身份筛选同时生效', (await api('GET', `/lqg/qc/list?keyword=${tag}&sampleKind=tissue&typeName=${encodeURIComponent('胆管')}`)).rows.length === 0);
  check('空组织类别筛选回到全部', (await api('GET', `/lqg/qc/list?keyword=${tag}&typeName=%20%20`)).rows.length === 3);

  browser = await chromium.launch().catch(() => chromium.launch({ channel: 'chrome' }));
  const page = await browser.newPage({ viewport: { width: 1903, height: 1000 } });
  page.setDefaultTimeout(20000);
  await page.goto(`${WEB}/login`);
  await page.locator('.login-form input').nth(0).fill('lqgadmin');
  await page.locator('.login-form input').nth(1).fill(password);
  await page.locator('.login-form button').click();
  await page.waitForURL(url => !url.pathname.includes('login'));
  await page.goto(`${WEB}/qc-docs`);
  const keyword = page.getByPlaceholder('内部编号 / 来源单位');
  await keyword.fill(tag);
  await keyword.press('Enter');
  await page.locator('tr.el-table__row').filter({ hasText: tag }).first().waitFor();
  const category = page.getByPlaceholder('肝 / 胆管 / 肾等');
  await category.fill('肝');
  await category.press('Enter');
  await page.waitForFunction(() => document.querySelectorAll('tr.el-table__row').length === 1);
  check('质控列表组织类别是文本输入，显示「肝」而非表名', await page.locator('tr.el-table__row').first().innerText().then(t => t.includes('肝') && !t.includes('样本记录信息表')));
  await page.screenshot({ path: `${OUT}/web-qc-category.png`, fullPage: true });
  await page.getByRole('button', { name: /^重\s*置$/ }).click();
  check('重置清空组织类别', await category.inputValue() === '');

  for (const [kind, sampleId] of [['tissue', created[0]], ['organoid', created[2]]]) {
    await page.goto(`${WEB}/${kind === 'tissue' ? 'sample' : 'sample-organoid'}`);
    await page.locator('tr.el-table__row').first().waitFor();
    const headers = page.locator('.el-table__header-wrapper th');
    check(`${kind} 表拆出石蜡包埋、冻存独立列`, await headers.filter({ hasText: /^石蜡包埋$/ }).count() === 1 && await headers.filter({ hasText: /^冻存$/ }).count() === 1);
    const process = headers.filter({ hasText: /^处理时间$/ });
    if (kind === 'tissue') {
      await page.locator('.el-table__body-wrapper .el-scrollbar__wrap').evaluate(el => { el.scrollLeft = 900; });
      await page.waitForTimeout(250);
    }
    await page.screenshot({ path: `${OUT}/web-${kind}-header.png`, fullPage: true });
    // 表头在截图反馈的桌面宽度下完整露出；固定列不能盖住它。
    check(`${kind} 处理时间表头未被固定列覆盖`, await process.evaluate(el => {
      const r = el.getBoundingClientRect();
      const top = document.elementFromPoint(r.right - 4, r.top + r.height / 2);
      return el.contains(top) && el.clientWidth >= 100;
    }));
    if (kind === 'organoid') {
      check('工作台菜单与页面统一收样名称', await page.getByText('类器官收样记录', { exact: true }).count() >= 1);

    }
    await page.locator('.el-table__body-wrapper .el-scrollbar__wrap').evaluate(el => { el.scrollLeft = el.scrollWidth; });
    await page.waitForTimeout(250);
    await page.screenshot({ path: `${OUT}/web-${kind}-split.png`, fullPage: true });
    // 带 sampleId 的关联入口仍可点击，不把两列都连到同一张表。
    await page.getByPlaceholder('内部编号（完整）').fill(`${tag}-${kind === 'tissue' ? 0 : 2}`);
    await page.getByRole('button', { name: /^搜\s*索$/ }).click();
    const row = page.locator('tr.el-table__row').filter({ hasText: tag });
    await row.first().waitFor();
    const add = row.getByText('新增', { exact: true });
    await add.nth(0).click();
    await page.waitForURL(url => url.pathname === '/embed' && url.searchParams.get('sampleId') === sampleId);
    check(`${kind} 石蜡包埋入口携带正确样本`, true);
    // 不填写就取消，不新增下游记录。
    await page.locator('.el-drawer:visible').getByRole('button', { name: /^取\s*消$/ }).click();
    await page.goBack();
    await page.locator('tr.el-table__row').filter({ hasText: tag }).getByText('新增', { exact: true }).nth(1).click();
    await page.waitForURL(url => url.pathname === '/cryo' && url.searchParams.get('sampleId') === sampleId);
    check(`${kind} 冻存入口携带正确样本`, true);
    await page.locator('.el-drawer:visible').getByRole('button', { name: /^取\s*消$/ }).click();
  }
} finally {
  if (browser) await browser.close();
  for (const id of created) await api('DELETE', `/lqg/sample/${id}`);
  if (created.length) check('临时样本已清理，列表无活动测试记录', (await api('GET', `/lqg/qc/list?keyword=${tag}`)).rows.length === 0);
}
