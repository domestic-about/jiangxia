// EMBED-WEB-001 · 工作台「石蜡包埋」页截图 + DOM 证据（不读 PNG，只读 DOM 文本 / getComputedStyle）
//
// 用法：cd /tmp/shots && node <这个文件>
// 产物：doc/waves/reports/EMBED-WEB-001/*.png + probe-*.json
import fs from 'node:fs';
import puppeteer from 'puppeteer-core';

const BASE = 'http://127.0.0.1:8082';
const OUT = '/Users/wkui/Project/profile/project/freelance/projects/jiangxia-organoid/doc/waves/reports/EMBED-WEB-001';
const CHROME = '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome';
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
fs.mkdirSync(OUT, { recursive: true });

const browser = await puppeteer.launch({ executablePath: CHROME, headless: true, args: ['--no-sandbox'] });
const page = await browser.newPage();
await page.setViewport({ width: 1920, height: 1100 });

const log = (...a) => console.log('[embedweb001]', ...a);
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

// ── 侧边菜单「石蜡包埋」（menu_id=5310 的 path='embed' 真的挂在侧栏上） ──────
const sidebar = await page.evaluate(() =>
  [...document.querySelectorAll('.sidebar-container a, .sidebar-container .el-menu-item')].map((el) => ({
    text: el.innerText.replace(/\s+/g, ' ').trim(),
    href: el.getAttribute('href')
  }))
);
log('sidebar', JSON.stringify(sidebar));
dump('probe-sidebar.json', sidebar);

// ── 进石蜡包埋页（也可以从侧边菜单点，这里直接走路由 = 菜单 5310 的 component） ──
await page.goto(`${BASE}/embed`, { waitUntil: 'networkidle2' });
await page.waitForSelector('.lqg-embed .el-table__row', { timeout: 20000 });
await sleep(2500);

const readTable = () =>
  page.evaluate(() => {
    const rows = [...document.querySelectorAll('.lqg-embed .el-table__body tr.el-table__row')];
    const headers = [...document.querySelectorAll('.lqg-embed .el-table__header th')].map((th) => th.innerText.replace(/\s+/g, ' ').trim());
    const cellText = (tr, i) => (tr.querySelectorAll('td')[i]?.innerText ?? '').replace(/\s+/g, ' ').trim();
    return {
      url: location.pathname,
      title: document.querySelector('.lqg-embed__title')?.textContent?.trim(),
      subtitle: document.querySelector('.lqg-embed__subtitle')?.textContent?.trim(),
      headers,
      headerCount: headers.length,
      rowCount: rows.length,
      rows: rows.map((tr) => ({
        cells: [...tr.querySelectorAll('td')].map((td) => td.innerText.replace(/\s+/g, ' ').trim()),
        source: cellText(tr, 0),
        verifyStatus: cellText(tr, 1),
        blockNo: cellText(tr, 2),
        internalNo: cellText(tr, 3),
        stain: cellText(tr, 14),
        markers: cellText(tr, 15),
        lastModified: cellText(tr, 19),
        rowBg: getComputedStyle(tr).backgroundColor,
        rowClass: tr.className
      }))
    };
  });

const table = await readTable();
log('table', JSON.stringify({
  url: table.url, headers: table.headerCount, rowCount: table.rowCount,
  firstBlockNo: table.rows[0]?.blockNo, lastBlockNo: table.rows[table.rowCount - 1]?.blockNo,
  bg: table.rows.map((r) => r.rowBg)
}));
log('pending row', JSON.stringify(table.rows.filter((r) => r.verifyStatus === '待核验').map((r) => ({ blockNo: r.blockNo, bg: r.rowBg }))));
log('valid row bg', JSON.stringify(table.rows.filter((r) => r.verifyStatus === '有效').slice(0, 1).map((r) => r.rowBg)));
dump('probe-table.json', table);
await shot('01-embed-list.png');

// 主色证据 + 表格列名（模板 16 列 + 两个徽标列 + 最后修改 + 操作）
const primary = await page.evaluate(() => {
  const btns = [...document.querySelectorAll('.lqg-embed button')];
  const search = btns.find((b) => b.innerText.includes('搜索'));
  const add = btns.find((b) => b.innerText.includes('新增'));
  const style = (el) => (el ? getComputedStyle(el).backgroundColor : null);
  return { searchBg: style(search), addBg: style(add), searchText: search?.innerText?.trim(), addText: add?.innerText?.trim() };
});
log('primary', JSON.stringify(primary));
dump('probe-primary.json', primary);

const filters = await page.evaluate(() => ({
  labels: [...document.querySelectorAll('.lqg-embed .el-form-item__label')].map((l) => l.innerText.replace(/\s+/g, ' ').trim())
}));
log('filters', JSON.stringify(filters));
dump('probe-filters.json', filters);

// ── ① 待核验外部送样（核验抽屉：所挂样本没核验 → 「判为有效并保存」置灰） ────
const pendingIndex = table.rows.findIndex((r) => r.verifyStatus === '待核验');
const openedVerify = await page.evaluate((idx) => {
  const rows = [...document.querySelectorAll('.lqg-embed .el-table__body tr.el-table__row')];
  const btn = [...rows[idx].querySelectorAll('button')].find((b) => b.innerText.includes('核验') && !b.disabled);
  btn?.click();
  return btn?.innerText?.trim();
}, pendingIndex);
log('opened verify', openedVerify, 'pendingIndex', pendingIndex);
await page.waitForSelector('.el-drawer.open', { timeout: 15000 });
await sleep(2000);

const verifyDrawer = await page.evaluate(() => {
  const root = document.querySelector('.el-drawer.open');
  const footer = [...root.querySelectorAll('.lqg-embed-drawer__footer button')];
  const validBtn = footer.find((b) => b.innerText.includes('判为有效'));
  const invalidBtn = footer.find((b) => b.innerText.includes('判为无效'));
  return {
    title: root?.querySelector('.el-drawer__title')?.textContent?.trim(),
    sections: [...root.querySelectorAll('.lqg-embed-drawer__section')].map((s) => s.innerText.trim()),
    alertText: [...root.querySelectorAll('.el-alert__content')].map((a) => a.innerText.replace(/\s+/g, ' ').trim()),
    probeHint: root.querySelector('.lqg-embed-drawer__hint')?.innerText?.trim(),
    footerButtons: footer.map((b) => b.innerText.trim()),
    validDisabled: validBtn?.disabled,
    validClass: validBtn?.className,
    invalidDisabled: invalidBtn?.disabled,
    stainButtons: [...root.querySelectorAll('.lqg-embed-drawer .el-checkbox-button')].map((b) => b.innerText.trim()),
    blockNoValue: root.querySelector('.lqg-embed-drawer__mono-input input')?.value,
    switchCount: root.querySelectorAll('.el-switch').length,
    bodyText: root.innerText.replace(/\s+/g, ' ').trim().slice(0, 500)
  };
});
log('verifyDrawer', JSON.stringify(verifyDrawer));
dump('probe-verify-drawer.json', verifyDrawer);
await shot('02-verify-drawer.png');

// 关抽屉（点蒙层）
await page.mouse.click(40, 600);
await sleep(1200);
const closedByOverlay = await page.evaluate(() => !document.querySelector('.el-drawer.open'));
log('closedByOverlay', closedByOverlay);
dump('probe-overlay-close.json', { closedByOverlay });

// ── ①b 反向对照：把所挂样本核验有效后，同一个核验抽屉的按钮应该**可点** ────────
//    （不做这一步的话，「恒置灰」也能让上面那条断言绿）
const sampleIdOfPending = await page.evaluate(() => {
  const rows = [...document.querySelectorAll('.lqg-embed .el-table__body tr.el-table__row')];
  return rows[0]?.querySelectorAll('td')[3]?.innerText?.trim();
});
log('pending embed internalNo', sampleIdOfPending);
// 样本 1002 是待核验的，用工作台核验它：先登出再以 staff 走 API 更快 —— 这里直接用页面表单
await page.goto(`${BASE}/sample`, { waitUntil: 'networkidle2' });
await page.waitForSelector('.lqg-sample .el-table__row', { timeout: 20000 });
await sleep(1500);
const verified = await page.evaluate(async () => {
  let raw = localStorage.getItem('Admin-Token');
  try { raw = JSON.parse(raw); } catch { /* 有的版本存的就是裸串 */ }
  const token = raw || '';
  // 工作台的 token 在 Cookie/localStorage 里，直接调后端接口把样本 9000001002 核验有效
  const res = await fetch('/dev-api/lqg/sample/9000001002/verify', {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json', Authorization: 'Bearer ' + token, clientid: 'e5cd7e4891bf95d1d19206ce24a7b32e' },
    body: JSON.stringify({ action: 'valid', receiveDate: '2026-09-18', internalNo: 'T-probe-web01' })
  });
  return await res.json();
});
log('verify sample 1002', JSON.stringify(verified));
await page.goto(`${BASE}/embed`, { waitUntil: 'networkidle2' });
await page.waitForSelector('.lqg-embed .el-table__row', { timeout: 20000 });
await sleep(2000);
const reopened = await page.evaluate(() => {
  const rows = [...document.querySelectorAll('.lqg-embed .el-table__body tr.el-table__row')];
  const btn = [...rows[0].querySelectorAll('button')].find((b) => b.innerText.includes('核验') && !b.disabled);
  btn?.click();
  return btn?.innerText?.trim();
});
await page.waitForSelector('.el-drawer.open', { timeout: 15000 });
await sleep(2000);
const verifyDrawerUnlocked = await page.evaluate(() => {
  const root = document.querySelector('.el-drawer.open');
  const footer = [...root.querySelectorAll('.lqg-embed-drawer__footer button')];
  const validBtn = footer.find((b) => b.innerText.includes('判为有效'));
  return {
    alertText: [...root.querySelectorAll('.el-alert__content')].map((a) => a.innerText.replace(/\s+/g, ' ').trim()),
    validDisabled: validBtn?.disabled,
    validClass: validBtn?.className,
    footerButtons: footer.map((b) => b.innerText.trim())
  };
});
log('verifyDrawerUnlocked', JSON.stringify(verifyDrawerUnlocked), 'reopened', reopened);
dump('probe-verify-drawer-unlocked.json', verifyDrawerUnlocked);
await shot('02b-verify-drawer-sample-valid.png');
await page.mouse.click(40, 600);
await sleep(1200);

// ── ② 编辑抽屉（有效记录：顶部「最后修改」+ 底部保存；染色按钮组 + marker 多行） ──
const validIndex = table.rows.findIndex((r) => r.verifyStatus === '有效');
const openedEdit = await page.evaluate((idx) => {
  const rows = [...document.querySelectorAll('.lqg-embed .el-table__body tr.el-table__row')];
  const btn = [...rows[idx].querySelectorAll('button')].find((b) => b.innerText.includes('编辑') && !b.disabled);
  btn?.click();
  return btn?.innerText?.trim();
}, validIndex);
log('opened edit', openedEdit, 'validIndex', validIndex, 'blockNo', table.rows[validIndex]?.blockNo);
await page.waitForSelector('.el-drawer.open', { timeout: 15000 });
await sleep(2000);

const editDrawer = await page.evaluate(() => {
  const root = document.querySelector('.el-drawer.open');
  const stainGroup = [...root.querySelectorAll('.el-checkbox-group')].map((g) => ({
    buttons: [...g.querySelectorAll('.el-checkbox-button')].map((b) => b.innerText.trim()),
    checked: [...g.querySelectorAll('.el-checkbox-button')].filter((b) => b.className.includes('is-checked')).map((b) => b.innerText.trim())
  }));
  return {
    title: root?.querySelector('.el-drawer__title')?.textContent?.trim(),
    lastMod: root?.querySelector('.lqg-embed-drawer__lastmod')?.innerText?.replace(/\s+/g, ' ').trim(),
    sections: [...root.querySelectorAll('.lqg-embed-drawer__section')].map((s) => s.innerText.trim()),
    stainGroups: stainGroup,
    markerRows: [...root.querySelectorAll('.lqg-embed-drawer__marker-row')].map((r) => r.innerText.replace(/\s+/g, ' ').trim()),
    dateInputs: [...root.querySelectorAll('.el-date-editor')].map((d) => (d.className.includes('el-range-editor') ? 'range' : 'date')),
    footerButtons: [...root.querySelectorAll('.lqg-embed-drawer__footer button')].map((b) => b.innerText.trim()),
    switchCount: root.querySelectorAll('.el-switch').length,
    blockNoValue: root.querySelector('.lqg-embed-drawer__mono-input input')?.value
  };
});
log('editDrawer', JSON.stringify(editDrawer));
dump('probe-edit-drawer.json', editDrawer);
await shot('03-edit-drawer.png');
await page.mouse.click(40, 600);
await sleep(1000);

// ── ③ 新增抽屉（选样本下拉 + 空染色） ─────────────────────────────────────
await page.evaluate(() => {
  const btn = [...document.querySelectorAll('.lqg-embed button')].find((b) => b.innerText.includes('新增'));
  btn?.click();
});
await page.waitForSelector('.el-drawer.open', { timeout: 15000 });
await sleep(1500);
// 输入内部编号触发远程搜索（只列有效样本）
await page.type('.el-drawer.open .el-select input', 'T-hli01').catch(() => {});
await sleep(2000);
const addDrawer = await page.evaluate(() => {
  const root = document.querySelector('.el-drawer.open');
  return {
    title: root?.querySelector('.el-drawer__title')?.textContent?.trim(),
    footerButtons: [...root.querySelectorAll('.lqg-embed-drawer__footer button')].map((b) => b.innerText.trim()),
    stainButtons: [...root.querySelectorAll('.el-checkbox-button')].map((b) => b.innerText.trim()),
    stainChecked: [...root.querySelectorAll('.el-checkbox-button')].filter((b) => b.className.includes('is-checked')).map((b) => b.innerText.trim()),
    markerRows: root.querySelectorAll('.lqg-embed-drawer__marker-row').length
  };
});
const sampleOptions = await page.evaluate(() =>
  [...document.querySelectorAll('.el-select-dropdown__item')].map((i) => i.innerText.replace(/\s+/g, ' ').trim()).filter(Boolean)
);
log('addDrawer', JSON.stringify(addDrawer), 'sampleOptions', JSON.stringify(sampleOptions));
dump('probe-add-drawer.json', { ...addDrawer, sampleOptions });
await shot('04-add-drawer.png');

// ── ④ 样本总表「石蜡包埋」入口（点亮 + 带 sampleId 跳转） ─────────────────
await page.goto(`${BASE}/sample`, { waitUntil: 'networkidle2' });
await page.waitForSelector('.lqg-sample .el-table__row', { timeout: 20000 });
await sleep(2000);
const sampleRowAction = await page.evaluate(() => {
  const rows = [...document.querySelectorAll('.lqg-sample .el-table__body tr.el-table__row')];
  const pick = rows.find((r) => r.querySelectorAll('td')[0]?.innerText?.trim() === 'T-hli01') || rows[0];
  const btns = [...pick.querySelectorAll('button')].map((b) => ({ text: b.innerText.trim(), disabled: b.disabled }));
  return { firstRowButtons: btns, firstRowInternalNo: pick.querySelectorAll('td')[0]?.innerText?.trim() };
});
log('sampleRowAction', JSON.stringify(sampleRowAction));
await shot('05-sample-row-action.png');
// 点它 → 跳到 /embed?sampleId=…
await page.evaluate(() => {
  const rows = [...document.querySelectorAll('.lqg-sample .el-table__body tr.el-table__row')];
  const pick = rows.find((r) => r.querySelectorAll('td')[0]?.innerText?.trim() === 'T-hli01') || rows[0];
  const btn = [...pick.querySelectorAll('button')].find((b) => b.innerText.trim() === '石蜡包埋');
  btn?.click();
});
await sleep(3000);
const afterJump = await page.evaluate(() => ({
  url: location.pathname + location.search,
  rowCount: document.querySelectorAll('.lqg-embed .el-table__body tr.el-table__row').length,
  sampleTag: document.querySelector('.lqg-embed__sample-tag')?.innerText?.trim(),
  blockNos: [...document.querySelectorAll('.lqg-embed .el-table__body tr.el-table__row')].map((tr) => tr.querySelectorAll('td')[2]?.innerText?.trim())
}));
log('afterJump', JSON.stringify(afterJump));
dump('probe-sample-jump.json', { sampleRowAction, afterJump });
await shot('06-embed-filtered-by-sample.png');
// 清除样本筛选 → 回全量
await page.evaluate(() => document.querySelector('.lqg-embed__sample-tag .el-tag__close')?.dispatchEvent(new MouseEvent('click', { bubbles: true })));
await sleep(2000);
const afterClear = await page.evaluate(() => ({
  url: location.pathname + location.search,
  rowCount: document.querySelectorAll('.lqg-embed .el-table__body tr.el-table__row').length,
  sampleTag: document.querySelector('.lqg-embed__sample-tag')?.innerText?.trim() ?? null
}));
log('afterClear', JSON.stringify(afterClear));
dump('probe-sample-clear.json', afterClear);

await browser.close();
log('done');
