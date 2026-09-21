// AUTH-GROUP-001 截图脚本（临时工具，不入库）
// 打工作台：来源单位与组别（左单位右组别）、外部用户列表 + 核验弹窗（新建 / 归并 / 改归组 / 驳回）
//
// ★ 导航方式：登录后直接 `page.goto(BASE + 路由路径)` 整页加载。
//   原因见完工报告的坑：这套 headless 环境里点侧边栏（或 router.push）之后 `.app-main`
//   会停在 `<!---->`（layout 的 router-view 不换），而整页加载到 `/auth/unit` 是好的。
import puppeteer from 'puppeteer-core';
import fs from 'node:fs';

const OUT = '/Users/wkui/Project/profile/project/freelance/projects/jiangxia-organoid/doc/waves/reports/AUTH-GROUP-001';
const BASE = process.env.LQG_WEB_BASE || 'http://127.0.0.1:8082';
const CHROME = '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome';

fs.mkdirSync(OUT, { recursive: true });

const browser = await puppeteer.launch({
  executablePath: CHROME,
  headless: true,
  args: ['--no-sandbox', '--disable-dev-shm-usage', '--force-device-scale-factor=2']
});
const page = await browser.newPage();
await page.setViewport({ width: 1560, height: 1000, deviceScaleFactor: 2 });
const log = (...a) => console.log('[group001-shots]', ...a);
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

async function shot(name) {
  await page.screenshot({ path: `${OUT}/${name}`, fullPage: false });
  log('saved', name);
}

async function openRoute(path) {
  await page.goto(`${BASE}${path}`, { waitUntil: 'networkidle2', timeout: 60000 });
  await sleep(3200);
}

// ── 登录 ─────────────────────────────────────────────────────────────────────
await page.goto(`${BASE}/`, { waitUntil: 'networkidle2', timeout: 60000 });
await page.waitForSelector('.login-form', { timeout: 30000 });
await sleep(800);
await page.type('.login-form input[type="text"]', 'lqgadmin');
await page.type('.login-form input[type="password"]', 'admin123');
await Promise.all([
  page.waitForFunction(() => !location.hash.includes('/login') && !!document.querySelector('.sidebar-container'), { timeout: 60000 }),
  page.click('.login-form .el-button--primary')
]);
await sleep(2500);

// 侧边栏（父目录「人员与单位」下本票两个菜单）
await page.evaluate(() => {
  const sub = Array.from(document.querySelectorAll('.sidebar-container .el-sub-menu__title')).find((e) => e.textContent.includes('人员与单位'));
  sub?.click();
});
await sleep(900);
const sidebarProbe = await page.evaluate(() => ({
  topMenus: Array.from(document.querySelectorAll('.sidebar-container .el-sub-menu__title, .sidebar-container .el-menu-item')).map((e) => e.textContent.trim())
}));
fs.writeFileSync(`${OUT}/probe-sidebar.json`, JSON.stringify(sidebarProbe, null, 2));
log('sidebar', JSON.stringify(sidebarProbe));

// ── 01/02/03：来源单位与组别 ────────────────────────────────────────────────
await openRoute('/auth/unit');
await shot('01-unit-group.png');

const unitProbe = await page.evaluate(() => ({
  route: document.querySelector('#app').__vue_app__.config.globalProperties.$router.currentRoute.value.fullPath,
  panelTitles: Array.from(document.querySelectorAll('.lqg-unit .lqg-unit__panel-title')).map((e) => e.textContent.trim()),
  unitHeaders: Array.from(document.querySelectorAll('.lqg-unit .el-col:first-child .el-table__header th')).map((e) => e.textContent.trim()),
  units: Array.from(document.querySelectorAll('.lqg-unit .el-col:first-child .el-table__body tr')).map((tr) =>
    Array.from(tr.querySelectorAll('td')).map((td) => td.textContent.trim())),
  groupHeaders: Array.from(document.querySelectorAll('.lqg-unit .el-col:nth-child(2) .el-table__header th')).map((e) => e.textContent.trim()),
  groups: Array.from(document.querySelectorAll('.lqg-unit .el-col:nth-child(2) .el-table__body tr')).map((tr) =>
    Array.from(tr.querySelectorAll('td')).map((td) => td.textContent.trim())),
  rawI18nKeysLeaked: /lqg\.auth\./.test(document.querySelector('.lqg-unit')?.textContent || '')
}));
fs.writeFileSync(`${OUT}/probe-unit-group.json`, JSON.stringify(unitProbe, null, 2));
log('unit probe', JSON.stringify(unitProbe));

// 左栏选 B 大学 → 右栏联动成它的组别
await page.evaluate(() => {
  const rows = Array.from(document.querySelectorAll('.lqg-unit .el-col:first-child .el-table__body tr'));
  const target = rows.find((tr) => tr.textContent.includes('B 大学'));
  target?.querySelector('td')?.click();
});
await sleep(1800);
await shot('02-unit-group-switch.png');
const switchProbe = await page.evaluate(() => ({
  panelTitle: Array.from(document.querySelectorAll('.lqg-unit .lqg-unit__panel-title')).map((e) => e.textContent.trim())[1],
  groups: Array.from(document.querySelectorAll('.lqg-unit .el-col:nth-child(2) .el-table__body tr')).map((tr) =>
    Array.from(tr.querySelectorAll('td')).map((td) => td.textContent.trim()))
}));
fs.writeFileSync(`${OUT}/probe-unit-group-switch.json`, JSON.stringify(switchProbe, null, 2));
log('switch probe', JSON.stringify(switchProbe));

// 停用单位二次确认（不物理删，只启用 / 停用）
await page.evaluate(() => {
  const rows = Array.from(document.querySelectorAll('.lqg-unit .el-col:first-child .el-table__body tr'));
  const target = rows.find((tr) => tr.textContent.includes('已停用单位'));
  const buttons = Array.from(target?.querySelectorAll('button') ?? []);
  buttons[buttons.length - 1]?.click();
});
await page.waitForSelector('.el-message-box', { timeout: 20000 });
await sleep(800);
await shot('03-unit-toggle-confirm.png');
const confirmProbe = await page.evaluate(() => ({
  title: document.querySelector('.el-message-box__title')?.textContent?.trim(),
  content: document.querySelector('.el-message-box__message')?.textContent?.trim(),
  buttons: Array.from(document.querySelectorAll('.el-message-box__btns button')).map((b) => b.textContent.trim())
}));
fs.writeFileSync(`${OUT}/probe-unit-toggle-confirm.json`, JSON.stringify(confirmProbe, null, 2));
log('confirm probe', JSON.stringify(confirmProbe));
await page.evaluate(() => {
  const btn = Array.from(document.querySelectorAll('.el-message-box__btns button')).find((b) => b.textContent.includes('取'));
  btn?.click();
});
await sleep(700);

// 新增组别弹窗
await page.evaluate(() => {
  const btn = Array.from(document.querySelectorAll('.lqg-unit button')).find((b) => b.textContent.includes('新增组别'));
  btn?.click();
});
await page.waitForSelector('.el-dialog__body', { timeout: 20000 });
await sleep(900);
await shot('04-add-group-dialog.png');
await page.evaluate(() => {
  const btn = Array.from(document.querySelectorAll('.el-dialog__footer button')).find((b) => b.textContent.includes('取'));
  btn?.click();
});
await sleep(700);

// ── 05/06/07/08/09：外部用户列表 + 核验弹窗 ───────────────────────────────
await openRoute('/auth/extuser');
await shot('05-extuser-list.png');

const listProbe = await page.evaluate(() => ({
  route: document.querySelector('#app').__vue_app__.config.globalProperties.$router.currentRoute.value.fullPath,
  headers: Array.from(document.querySelectorAll('.lqg-extuser .el-table__header th')).map((e) => e.textContent.trim()),
  rows: Array.from(document.querySelectorAll('.lqg-extuser .el-table__body tr')).map((tr) =>
    Array.from(tr.querySelectorAll('td')).map((td) => td.textContent.trim())),
  selfItalic: Array.from(document.querySelectorAll('.lqg-extuser .lqg-extuser__self')).map((e) => ({ text: e.textContent.trim(), style: getComputedStyle(e).fontStyle })),
  rawI18nKeysLeaked: /lqg\.auth\./.test(document.querySelector('.lqg-extuser')?.textContent || '')
}));
fs.writeFileSync(`${OUT}/probe-extuser-list.json`, JSON.stringify(listProbe, null, 2));
log('list probe', JSON.stringify(listProbe));

async function openVerifyDialog(phone, buttonText) {
  await page.evaluate((p, bt) => {
    const rows = Array.from(document.querySelectorAll('.lqg-extuser .el-table__body tr'));
    const target = rows.find((tr) => tr.textContent.includes(p));
    const btn = Array.from(target?.querySelectorAll('button') ?? []).find((b) => b.textContent.includes(bt));
    btn?.click();
  }, phone, buttonText);
  await page.waitForSelector('.el-dialog__body', { timeout: 20000 });
  await sleep(1200);
}

// 自填的（新建路径）→ 用真正自填过单位 / 组别的「郑老师」
await openVerifyDialog('13800000098', '核验');
await shot('06-verify-dialog-create.png');
const createProbe = await page.evaluate(() => {
  const dlg = Array.from(document.querySelectorAll('.el-dialog')).find((d) => d.style.display !== 'none');
  return {
    title: dlg?.querySelector('.el-dialog__title')?.textContent?.trim(),
    text: dlg?.textContent?.replace(/\s+/g, ' ').trim().slice(0, 700),
    radios: Array.from(dlg?.querySelectorAll('.el-radio') ?? []).map((e) => e.textContent.trim()),
    alerts: Array.from(dlg?.querySelectorAll('.el-alert__title') ?? []).map((e) => e.textContent.trim())
  };
});
fs.writeFileSync(`${OUT}/probe-verify-dialog-create.json`, JSON.stringify(createProbe, null, 2));
log('create dialog probe', JSON.stringify(createProbe));

// 归并路径：重新打开一条自填档案，切到「归并到已有」→ 出单位 / 组别下拉
await page.evaluate(() => {
  const btn = Array.from(document.querySelectorAll('.el-dialog__footer button')).find((b) => b.textContent.includes('取'));
  btn?.click();
});
await sleep(800);
await openVerifyDialog('13800000099', '核验');
await page.evaluate(() => {
  const radios = Array.from(document.querySelectorAll('.el-dialog .el-radio'));
  const merge = radios.find((r) => r.textContent.includes('归并'));
  merge?.click();
});
await sleep(900);
await shot('07-verify-dialog-merge.png');
const mergeProbe = await page.evaluate(() => {
  const dlg = Array.from(document.querySelectorAll('.el-dialog')).find((d) => d.style.display !== 'none');
  return { selects: dlg?.querySelectorAll('.el-select').length, labels: Array.from(dlg?.querySelectorAll('.el-form-item__label') ?? []).map((e) => e.textContent.trim()) };
});
fs.writeFileSync(`${OUT}/probe-verify-dialog-merge.json`, JSON.stringify(mergeProbe, null, 2));
log('merge dialog probe', JSON.stringify(mergeProbe));

await page.evaluate(() => {
  const btn = Array.from(document.querySelectorAll('.el-dialog__footer button')).find((b) => b.textContent.includes('取'));
  btn?.click();
});
await sleep(800);

// 已核验的进来 = 改归组（verified→verified）
await openVerifyDialog('13800000012', '改归组');
await shot('08-verify-dialog-regroup.png');
const regroupProbe = await page.evaluate(() => {
  const dlg = Array.from(document.querySelectorAll('.el-dialog')).find((d) => d.style.display !== 'none');
  return {
    title: dlg?.querySelector('.el-dialog__title')?.textContent?.trim(),
    text: dlg?.textContent?.replace(/\s+/g, ' ').trim().slice(0, 400),
    radioButtons: Array.from(dlg?.querySelectorAll('.el-radio-button') ?? []).map((e) => e.textContent.trim())
  };
});
fs.writeFileSync(`${OUT}/probe-verify-dialog-regroup.json`, JSON.stringify(regroupProbe, null, 2));
log('regroup probe', JSON.stringify(regroupProbe));

// 驳回路径：切到「驳回」→ 原因必填
await page.evaluate(() => {
  const radios = Array.from(document.querySelectorAll('.el-dialog .el-radio-button'));
  const reject = radios.find((r) => r.textContent.includes('驳回'));
  reject?.click();
});
await sleep(900);
await shot('09-verify-dialog-reject.png');
const rejectProbe = await page.evaluate(() => {
  const dlg = Array.from(document.querySelectorAll('.el-dialog')).find((d) => d.style.display !== 'none');
  return { textareaPlaceholder: dlg?.querySelector('textarea')?.getAttribute('placeholder'), labels: Array.from(dlg?.querySelectorAll('.el-form-item__label') ?? []).map((e) => e.textContent.trim()) };
});
fs.writeFileSync(`${OUT}/probe-verify-dialog-reject.json`, JSON.stringify(rejectProbe, null, 2));
log('reject dialog probe', JSON.stringify(rejectProbe));

await browser.close();
log('done');
