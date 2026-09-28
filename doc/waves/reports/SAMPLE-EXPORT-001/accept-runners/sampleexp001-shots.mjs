// SAMPLE-EXPORT-001 · 工作台样本总表「两个导出按钮」的 DOM / 网络证据（不读 PNG，只读 DOM 文本与请求 URL）
//
// 用法：cp 到有 puppeteer-core 的目录再跑，例如
//   cp doc/waves/reports/SAMPLE-EXPORT-001/accept-runners/sampleexp001-shots.mjs /tmp/shots/ && node /tmp/shots/sampleexp001-shots.mjs
// 产物：doc/waves/reports/SAMPLE-EXPORT-001/*.png + probe-*.json（截图只落盘，不读进上下文）
import fs from 'node:fs';
import puppeteer from 'puppeteer-core';

const BASE = 'http://127.0.0.1:8082';
const OUT = '/Users/wkui/Project/profile/project/freelance/projects/jiangxia-organoid/doc/waves/reports/SAMPLE-EXPORT-001';
const CHROME = '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome';
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
fs.mkdirSync(OUT, { recursive: true });

const browser = await puppeteer.launch({ executablePath: CHROME, headless: true, args: ['--no-sandbox'] });
const page = await browser.newPage();
await page.setViewport({ width: 1920, height: 1100 });

const log = (...a) => console.log('[sampleexp001]', ...a);
const dump = (name, obj) => fs.writeFileSync(`${OUT}/${name}`, JSON.stringify(obj, null, 2));
const shot = async (name) => { await page.screenshot({ path: `${OUT}/${name}`, fullPage: false }); };

// ── 抓导出请求（证明按钮真的打到 /lqg/sample/export/{tissue,organoid}，且带着当前筛选） ──
const requests = [];
const responses = [];
page.on('request', (r) => {
  if (r.url().includes('/lqg/sample/export/')) requests.push({ method: r.method(), url: r.url() });
});
page.on('response', async (r) => {
  if (!r.url().includes('/lqg/sample/export/')) return;
  let len = -1;
  try { len = (await r.buffer()).length; } catch { /* 忽略 */ }
  responses.push({ status: r.status(), contentType: r.headers()['content-type'], bytes: len, url: r.url() });
});

// ── 登录（seed 的 lqgadmin / admin123） ──────────────────────────────────────
await page.goto(`${BASE}/`, { waitUntil: 'networkidle2' });
await page.waitForSelector('.login-form');
await page.type('.login-form input[type="text"]', 'lqgadmin');
await page.type('.login-form input[type="password"]', 'admin123');
await Promise.all([
  page.waitForFunction(() => !!document.querySelector('.sidebar-container')),
  page.click('.login-form .el-button--primary')
]);
await sleep(2500);

// ── 进样本总表（菜单 5210 的 path='sample'） ─────────────────────────────────
await page.goto(`${BASE}/sample`, { waitUntil: 'networkidle2' });
await page.waitForSelector('.lqg-sample .el-table__row', { timeout: 20000 });
await sleep(2000);

const readToolbar = () =>
  page.evaluate(() => {
    const bar = document.querySelector('.lqg-sample .mb8');
    const buttons = [...(bar?.querySelectorAll('button') ?? [])].map((b) => b.innerText.replace(/\s+/g, ' ').trim());
    const exportTissue = [...(bar?.querySelectorAll('button') ?? [])].find((b) => b.innerText.includes('导出样本记录信息表'));
    const exportOrganoid = [...(bar?.querySelectorAll('button') ?? [])].find((b) => b.innerText.includes('导出类器官收样记录'));
    return {
      url: location.pathname,
      title: document.querySelector('.lqg-sample__title')?.textContent?.trim(),
      toolbarButtons: buttons,
      hasExportTissue: !!exportTissue,
      hasExportOrganoid: !!exportOrganoid,
      exportTissueDisabled: exportTissue ? exportTissue.disabled : null,
      exportOrganoidDisabled: exportOrganoid ? exportOrganoid.disabled : null,
      switchCount: document.querySelectorAll('.lqg-sample .el-switch').length
    };
  });

const toolbar = await readToolbar();
log('toolbar', JSON.stringify(toolbar));
dump('probe-toolbar.json', toolbar);
await shot('01-sample-list-with-export-buttons.png');

// ── 按表单标签填两个筛选（内部编号 + 操作人），点「搜索」，再点两个导出 ────────
const fillByLabel = async (label, value) => {
  const handle = await page.evaluateHandle((text) => {
    const items = [...document.querySelectorAll('.lqg-sample__filter .el-form-item')];
    const item = items.find((i) => i.innerText.replace(/\s+/g, ' ').trim().startsWith(text));
    return item?.querySelector('input') ?? null;
  }, label);
  const el = handle.asElement();
  if (!el) throw new Error(`找不到筛选「${label}」`);
  await el.click({ clickCount: 3 });
  await el.type(value);
};

await fillByLabel('内部编号', 'T-hli01');
await fillByLabel('操作人', '李');
await page.evaluate(() => {
  const btn = [...document.querySelectorAll('.lqg-sample__filter button')].find((b) => b.innerText.includes('搜索'));
  btn?.click();
});
await sleep(2500);

const filtered = await page.evaluate(() => ({
  rowCount: document.querySelectorAll('.lqg-sample .el-table__body tr.el-table__row').length,
  firstInternalNo: document.querySelector('.lqg-sample .el-table__body tr.el-table__row td')?.innerText?.trim(),
  total: document.querySelector('.lqg-sample .el-pagination__total')?.innerText?.trim()
}));
log('filtered', JSON.stringify(filtered));
dump('probe-filtered-list.json', filtered);
await shot('02-sample-list-filtered.png');

const clickExport = async (text) => {
  await page.evaluate((label) => {
    const bar = document.querySelector('.lqg-sample .mb8');
    const btn = [...(bar?.querySelectorAll('button') ?? [])].find((b) => b.innerText.includes(label));
    btn?.click();
  }, text);
  await sleep(3000);
};

await clickExport('导出样本记录信息表');
await clickExport('导出类器官收样记录');

const evidence = { requests, responses };
log('export requests', JSON.stringify(requests));
log('export responses', JSON.stringify(responses));
dump('probe-export-requests.json', evidence);

// 断言（硬判据，跑完打印结论）
const tissue = requests.find((r) => r.url.includes('/lqg/sample/export/tissue'));
const organoid = requests.find((r) => r.url.includes('/lqg/sample/export/organoid'));
const check = (cond, label) => log(`${cond ? '✓' : '✗'} ${label}`);
check(toolbar.hasExportTissue && toolbar.hasExportOrganoid, '页面上两个导出按钮都在');
check(!!tissue && tissue.method === 'POST', '点「导出样本记录信息表」发出 POST /lqg/sample/export/tissue');
check(!!organoid && organoid.method === 'POST', '点「导出类器官收样记录」发出 POST /lqg/sample/export/organoid');
check(!!tissue && tissue.url.includes('internalNo=T-hli01'), 'tissue 导出带着当前筛选 internalNo=T-hli01');
check(!!tissue && tissue.url.includes('operatorName=%E6%9D%8E'), 'tissue 导出带着当前筛选 operatorName=李');
check(!!organoid && organoid.url.includes('internalNo=T-hli01'), 'organoid 导出带着当前筛选 internalNo=T-hli01');
check(responses.filter((r) => r.status === 200 && r.bytes > 0).length === 2, '两个导出各拿到一个非空 xlsx（HTTP 200）');

await browser.close();
