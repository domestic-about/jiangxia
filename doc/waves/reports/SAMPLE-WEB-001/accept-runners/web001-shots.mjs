// SAMPLE-WEB-001 · 工作台样本总表截图 + DOM 证据（不读图，只读 DOM 文本 / getComputedStyle）
//
// 用法：cd /tmp/shots && node /tmp/shots/web001-shots.mjs
// 产物：doc/waves/reports/SAMPLE-WEB-001/*.png + *.json
import fs from 'node:fs';
import puppeteer from 'puppeteer-core';

const BASE = 'http://127.0.0.1:8082';
const OUT = '/Users/wkui/Project/profile/project/freelance/projects/jiangxia-organoid/doc/waves/reports/SAMPLE-WEB-001';
const CHROME = '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome';
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
fs.mkdirSync(OUT, { recursive: true });

const browser = await puppeteer.launch({ executablePath: CHROME, headless: true, args: ['--no-sandbox'] });
const page = await browser.newPage();
await page.setViewport({ width: 1920, height: 1100 });

const log = (...a) => console.log('[web001]', ...a);
const dump = (name, obj) => fs.writeFileSync(`${OUT}/${name}`, JSON.stringify(obj, null, 2));
const shot = async (name) => { await page.screenshot({ path: `${OUT}/${name}`, fullPage: false }); };

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

// ── 进样本总表 ──────────────────────────────────────────────────────────────
await page.goto(`${BASE}/sample`, { waitUntil: 'networkidle2' });
await page.waitForSelector('.lqg-sample .el-table__row', { timeout: 20000 });
await sleep(2500);

const readTable = () =>
  page.evaluate(() => {
    const rows = [...document.querySelectorAll('.lqg-sample .el-table__body tr.el-table__row')];
    const cellText = (tr, i) => (tr.querySelectorAll('td')[i]?.innerText ?? '').replace(/\s+/g, ' ').trim();
    const headers = [...document.querySelectorAll('.lqg-sample .el-table__header th')].map((th) => th.innerText.replace(/\s+/g, ' ').trim());
    return {
      title: document.querySelector('.lqg-sample__title')?.textContent?.trim(),
      subtitle: document.querySelector('.lqg-sample__subtitle')?.textContent?.trim(),
      headers,
      rowCount: rows.length,
      rows: rows.map((tr) => ({
        cells: [...tr.querySelectorAll('td')].map((td) => td.innerText.replace(/\s+/g, ' ').trim()),
        internalNo: cellText(tr, 0),
        submitNo: cellText(tr, 1),
        verifyStatus: cellText(tr, 5),
        donor: cellText(tr, 6),
        lastModified: cellText(tr, 20),
        rowBg: getComputedStyle(tr).backgroundColor,
        rowClass: tr.className
      }))
    };
  });

const table = await readTable();
log('table', JSON.stringify({ headers: table.headers.length, rowCount: table.rowCount, first: table.rows[0]?.submitNo, last: table.rows[table.rowCount - 1]?.submitNo }));
log('pending rows', JSON.stringify(table.rows.filter((r) => r.verifyStatus === '待核验').map((r) => ({ no: r.submitNo, bg: r.rowBg }))));
log('valid row bg', JSON.stringify(table.rows.filter((r) => r.verifyStatus === '有效').slice(0, 1).map((r) => r.rowBg)));
log('never-modified cells', JSON.stringify(table.rows.map((r) => r.lastModified)));
dump('probe-table.json', table);
await shot('01-sample-list.png');

// 主色证据（搜索按钮 = --el-color-primary 映射）
const primary = await page.evaluate(() => {
  const btns = [...document.querySelectorAll('.lqg-sample button')];
  const search = btns.find((b) => b.innerText.includes('搜索'));
  const add = btns.find((b) => b.innerText.includes('新增') || b.innerText.includes('类器官'));
  const style = (el) => (el ? getComputedStyle(el).backgroundColor : null);
  return { searchBg: style(search), addBg: style(add), searchText: search?.innerText?.trim(), addText: add?.innerText?.trim() };
});
log('primary', JSON.stringify(primary));

// 筛选区字段（供体姓名 / 住院号旁的「精确匹配」小字）
const filters = await page.evaluate(() => ({
  labels: [...document.querySelectorAll('.lqg-sample .el-form-item__label')].map((l) => l.innerText.replace(/\s+/g, ' ').trim()),
  exactMarks: [...document.querySelectorAll('.lqg-sample__exact')].map((s) => s.innerText.trim()),
  unitOptions: null
}));
log('filters', JSON.stringify(filters));
dump('probe-filters.json', filters);

// ── 打开一条待核验样本（核验抽屉） ────────────────────────────────────────
const pendingIndex = table.rows.findIndex((r) => r.verifyStatus === '待核验');
const opened = await page.evaluate((idx) => {
  const rows = [...document.querySelectorAll('.lqg-sample .el-table__body tr.el-table__row')];
  const btn = [...rows[idx].querySelectorAll('button')].find((b) => b.innerText.includes('核验') || b.innerText.includes('编辑'));
  btn?.click();
  return btn?.innerText?.trim();
}, pendingIndex);
log('opened row action', opened);
await page.waitForSelector('.el-drawer__body', { timeout: 15000 });
await sleep(2000);

const drawer = await page.evaluate(() => {
  const root = document.querySelector('.el-drawer.open') || document.querySelector('.el-drawer');
  const text = root?.innerText?.replace(/\s+/g, ' ').trim() ?? '';
  const groups = [...root.querySelectorAll('.el-radio-group')].map((g) => ({
    buttons: [...g.querySelectorAll('.el-radio-button')].map((b) => b.innerText.trim()),
    hasSwitch: !!g.querySelector('.el-switch')
  }));
  const switches = root.querySelectorAll('.el-switch').length;
  const selects = [...root.querySelectorAll('.el-select')].map((s) => s.innerText.replace(/\s+/g, ' ').trim());
  const footerButtons = [...root.querySelectorAll('.lqg-sample-drawer__actions button')].map((b) => b.innerText.trim());
  const moreButtons = [...root.querySelectorAll('.lqg-sample-drawer__more button')].map((b) => b.innerText.trim());
  const dateInputs = [...root.querySelectorAll('.el-date-editor')].map((d) => d.className.includes('el-range-editor') ? 'range' : 'date');
  return {
    title: root?.querySelector('.el-drawer__title')?.textContent?.trim(),
    sections: [...root.querySelectorAll('.lqg-sample-drawer__section')].map((s) => s.innerText.trim()),
    hint: root.querySelector('.el-alert__content')?.innerText?.replace(/\s+/g, ' ').trim(),
    radioGroups: groups,
    switchCount: switches,
    selectCount: selects.length,
    selects,
    dateInputs,
    footerButtons,
    moreButtons,
    hasDrawerText: text.slice(0, 400)
  };
});
log('drawer', JSON.stringify(drawer));
dump('probe-verify-drawer.json', drawer);
await shot('02-verify-drawer.png');

// ── 关抽屉（点蒙层；不写 :close-on-click-modal="false" 的证据） ─────────────
await page.mouse.click(40, 600);
await sleep(1200);
const closedByOverlay = await page.evaluate(() => !document.querySelector('.el-drawer.open'));
log('closedByOverlay', closedByOverlay);
dump('probe-overlay-close.json', { closedByOverlay });

// ── 打开一条有效样本（编辑抽屉：底部「保存」+ 顶部「最后修改」） ────────────
const validIndex = table.rows.findIndex((r) => r.verifyStatus === '有效');
const openedEdit = await page.evaluate((idx) => {
  const rows = [...document.querySelectorAll('.lqg-sample .el-table__body tr.el-table__row')];
  const btn = [...rows[idx].querySelectorAll('button')].find((b) => b.innerText.includes('编辑'));
  btn?.click();
  return btn?.innerText?.trim();
}, validIndex);
log('opened edit', openedEdit, 'validIndex', validIndex, 'validNo', table.rows[validIndex]?.submitNo);
await page.waitForSelector('.el-drawer__body', { timeout: 15000 });
await sleep(1800);
const editDrawer = await page.evaluate(() => {
  const root = [...document.querySelectorAll('.el-drawer')].find((d) => d.className.includes('open')) || document.querySelector('.el-drawer');
  const flagGroups = [...root.querySelectorAll('.el-radio-group')].map((g) => {
    const label = g.closest('.el-form-item')?.querySelector('.el-form-item__label')?.innerText?.replace(/\s+/g, ' ').trim();
    const checked = [...g.querySelectorAll('.el-radio-button')].filter((b) => b.className.includes('is-active')).map((b) => b.innerText.trim());
    return { label, checked };
  });
  return {
    title: root?.querySelector('.el-drawer__title')?.textContent?.trim(),
    lastMod: root?.querySelector('.lqg-sample-drawer__lastmod')?.innerText?.replace(/\s+/g, ' ').trim(),
    footerButtons: [...root.querySelectorAll('.lqg-sample-drawer__actions button')].map((b) => b.innerText.trim()),
    moreButtons: [...root.querySelectorAll('.lqg-sample-drawer__more button')].map((b) => b.innerText.trim()),
    flagGroupState: flagGroups,
    switchCount: root.querySelectorAll('.el-switch').length,
    invalidReasonRow: [...root.querySelectorAll('.el-form-item')]
      .filter((f) => f.querySelector('.el-form-item__label')?.innerText.includes('无效原因'))
      .map((f) => f.innerText.replace(/\s+/g, ' ').trim())
  };
});
log('editDrawer', JSON.stringify(editDrawer));
dump('probe-edit-drawer.json', editDrawer);
await shot('03-edit-drawer.png');

await browser.close();
log('done');
