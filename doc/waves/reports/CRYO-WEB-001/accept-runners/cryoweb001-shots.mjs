// CRYO-WEB-001 · 工作台「-80 冻存管理」页截图 + DOM 证据
// （★ 全程只读 DOM 文本 / getComputedStyle / 网络请求，绝不把 PNG 读进上下文）
//
// 用法：cp 到有 puppeteer-core 的目录再跑（例如 /tmp/shots），
//   node cryoweb001-shots.mjs
// 产物：doc/waves/reports/CRYO-WEB-001/*.png + probe-*.json
import fs from 'node:fs';
import puppeteer from 'puppeteer-core';

const BASE = 'http://127.0.0.1:8082';
const API = 'http://127.0.0.1:8081';
const OUT = '/Users/wkui/Project/profile/project/freelance/projects/jiangxia-organoid/doc/waves/reports/CRYO-WEB-001';
const CHROME = '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome';
const CLIENT_PC = 'e5cd7e4891bf95d1d19206ce24a7b32e';
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
fs.mkdirSync(OUT, { recursive: true });

const browser = await puppeteer.launch({
  executablePath: CHROME,
  headless: true,
  args: ['--no-sandbox', '--disable-dev-shm-usage']
});
const page = await browser.newPage();
await page.setViewport({ width: 1920, height: 1100 });

const errors = [];
page.on('pageerror', (e) => errors.push(String(e).slice(0, 300)));
page.on('response', (r) => { if (r.status() >= 400) errors.push(`HTTP ${r.status()} ${r.url()}`); });

const log = (...a) => console.log('[cryoweb001]', ...a);
const dump = (name, obj) => fs.writeFileSync(`${OUT}/${name}`, JSON.stringify(obj, null, 2));
const shot = async (name) => { await page.screenshot({ path: `${OUT}/${name}`, fullPage: false }); };

// 记录发往 /lqg/cryo/** 的请求（证明抽屉真的接了「改」「删」两个接口）
let calls = [];
page.on('request', (req) => {
  const url = req.url();
  if (url.includes('/lqg/cryo/')) {
    calls.push({ method: req.method(), url: url.replace(BASE, '').replace('/api', '') });
  }
});
const takeCalls = () => { const c = [...calls]; calls = []; return c; };

/** 关掉当前打开的弹窗：点「取 消 / 取消」（比 Escape 可靠，弹窗叠抽屉时 Escape 会被吃掉） */
const closeDialog = async () => {
  const closed = await page.evaluate(() => {
    const dlg = document.querySelector('.el-dialog');
    if (!dlg) return true;
    const btn = [...dlg.querySelectorAll('button')].find((b) => b.innerText.replace(/\s+/g, '').includes('取消'));
    btn?.click();
    return !btn;
  });
  if (closed) return;
  await page.waitForFunction(() => !document.querySelector('.el-dialog'), { timeout: 10000 }).catch(() => {});
  await sleep(600);
};

// ── 登录（seed 的 lqgadmin / admin123）─────────────────────────────────────
await page.goto(`${BASE}/`, { waitUntil: 'networkidle2' });
await page.waitForSelector('.login-form');
await page.type('.login-form input[type="text"]', 'lqgadmin');
await page.type('.login-form input[type="password"]', 'admin123');
await Promise.all([
  page.waitForFunction(() => !!document.querySelector('.sidebar-container')),
  page.click('.login-form .el-button--primary')
]);
await sleep(2500);

// ── 侧栏菜单 5410 真的挂上了 ─────────────────────────────────────────────────
const sidebar = await page.evaluate(() =>
  [...document.querySelectorAll('.sidebar-container a, .sidebar-container .el-menu-item')].map((el) => ({
    text: el.innerText.replace(/\s+/g, ' ').trim(),
    href: el.getAttribute('href')
  }))
);
log('sidebar', JSON.stringify(sidebar.filter((s) => s.text.includes('冻存') || s.href === '/cryo')));
dump('probe-sidebar.json', sidebar);

// ── 后端 tabCounts（两侧不同源：页面读 DOM，这里直接打接口）─────────────────
const token = await page.evaluate(() => {
  let raw = localStorage.getItem('Admin-Token');
  try { raw = JSON.parse(raw); } catch { /* 有的版本存的是裸串 */ }
  return raw || '';
});
const api = async (method, path) => {
  const res = await fetch(`${API}${path}`, { method, headers: { Authorization: 'Bearer ' + token, clientid: CLIENT_PC, 'Content-Type': 'application/json' } });
  return res.json();
};
const listAll = await api('GET', '/lqg/cryo/batch/list?pageSize=100');
log('api tabCounts', JSON.stringify(listAll.tabCounts));
dump('probe-api-tabcounts.json', { tabCounts: listAll.tabCounts, total: listAll.total, ids: listAll.rows.map((r) => r.id) });

// ── ① 列表页 ────────────────────────────────────────────────────────────────
await page.goto(`${BASE}/cryo`, { waitUntil: 'networkidle2' });
await page.waitForSelector('.lqg-cryo .el-table__row', { timeout: 20000 });
await sleep(2500);

const readTable = () =>
  page.evaluate(() => {
    const rows = [...document.querySelectorAll('.lqg-cryo .el-table__body tr.el-table__row')];
    const headers = [...document.querySelectorAll('.lqg-cryo .el-table__header th')].map((th) => th.innerText.replace(/\s+/g, ' ').trim());
    const cellText = (tr, i) => (tr.querySelectorAll('td')[i]?.innerText ?? '').replace(/\s+/g, ' ').trim();
    return {
      url: location.pathname + location.search,
      title: document.querySelector('.lqg-cryo__title')?.textContent?.trim(),
      tabs: [...document.querySelectorAll('.lqg-cryo__tabs .el-tabs__item')].map((it) => it.innerText.replace(/\s+/g, ' ').trim()),
      headers,
      headerCount: headers.length,
      rowCount: rows.length,
      rows: rows.map((tr) => ({
        freezeTime: cellText(tr, 0),
        cryoName: cellText(tr, 1),
        initQty: cellText(tr, 2),
        inMinus80: cellText(tr, 4),
        toLn2Time: cellText(tr, 6),
        internalNo: cellText(tr, 9),
        passage: cellText(tr, 10),
        remainingQty: cellText(tr, 11),
        location: cellText(tr, 12),
        rowBg: getComputedStyle(tr).backgroundColor,
        rowClass: tr.className,
        actions: [...tr.querySelectorAll('td:last-child button')].map((b) => b.innerText.replace(/\s+/g, ' ').trim())
      }))
    };
  });

const table = await readTable();
log('table', JSON.stringify({
  url: table.url, tabs: table.tabs, headers: table.headerCount, rowCount: table.rowCount,
  firstRow: table.rows[0], bgs: table.rows.map((r) => r.rowBg)
}));
dump('probe-table.json', table);
await shot('01-cryo-list.png');

// 超期行样式（整行浅红 = --lqg-danger-soft）+ 「已超 N 天」徽标
const overdueStyle = await page.evaluate(() => {
  const rows = [...document.querySelectorAll('.lqg-cryo .el-table__body tr.el-table__row')];
  const overdue = rows.filter((tr) => tr.className.includes('lqg-cryo__row-overdue'));
  const badges = [...document.querySelectorAll('.lqg-cryo .lqg-cryo__badge')].map((b) => b.innerText.trim());
  return {
    overdueRowCount: overdue.length,
    overdueBg: overdue.map((tr) => getComputedStyle(tr).backgroundColor),
    nonOverdueBg: rows.filter((tr) => !tr.className.includes('lqg-cryo__row-overdue')).slice(0, 1).map((tr) => getComputedStyle(tr).backgroundColor),
    badges,
    pinnedFirstTwo: rows.slice(0, 2).every((tr) => tr.className.includes('lqg-cryo__row-overdue')),
    switchCount: document.querySelectorAll('.lqg-cryo .el-switch').length
  };
});
log('overdueStyle', JSON.stringify(overdueStyle));
dump('probe-overdue-style.json', overdueStyle);

const filters = await page.evaluate(() => ({
  labels: [...document.querySelectorAll('.lqg-cryo .el-form-item__label')].map((l) => l.innerText.replace(/\s+/g, ' ').trim()),
  buttons: [...document.querySelectorAll('.lqg-cryo .mb8 button')].map((b) => b.innerText.replace(/\s+/g, ' ').trim()),
  checkboxCount: document.querySelectorAll('.lqg-cryo .el-checkbox').length
}));
log('filters', JSON.stringify(filters));
dump('probe-filters.json', filters);

// ── ①b 页签切换：超期 / 液氮 都要真的收窄（而数字不变 = 整表口径）───────────
const clickTab = async (label) => {
  await page.evaluate((text) => {
    const items = [...document.querySelectorAll('.lqg-cryo__tabs .el-tabs__item')];
    items.find((it) => it.innerText.includes(text))?.click();
  }, label);
  await sleep(1800);
  return readTable();
};
const tabOverdue = await clickTab('-80 超期');
log('tab overdue', JSON.stringify({ rowCount: tabOverdue.rowCount, tabs: tabOverdue.tabs, names: tabOverdue.rows.map((r) => r.cryoName) }));
await shot('01b-cryo-tab-overdue.png');
const tabLn2 = await clickTab('液氮');
log('tab ln2', JSON.stringify({ rowCount: tabLn2.rowCount, tabs: tabLn2.tabs, names: tabLn2.rows.map((r) => r.cryoName) }));
dump('probe-tabs.json', { overdue: tabOverdue, ln2: tabLn2, all: table });
await clickTab('全部');

// ── ② 取走弹窗（上限 = 当前剩余；显示当前剩余）─────────────────────────────
const openTake = await page.evaluate(() => {
  const rows = [...document.querySelectorAll('.lqg-cryo .el-table__body tr.el-table__row')];
  const row = rows.find((tr) => !tr.className.includes('lqg-cryo__row-overdue'));
  const btn = [...row.querySelectorAll('button')].find((b) => b.innerText.trim() === '取走');
  btn?.click();
  return { name: row.querySelectorAll('td')[1]?.innerText?.trim(), remaining: row.querySelectorAll('td')[11]?.innerText?.trim() };
});
await page.waitForSelector('.el-dialog', { timeout: 15000 });
await sleep(1500);
const takeDialog = await page.evaluate(() => {
  const dlg = document.querySelector('.el-dialog');
  return {
    title: dlg?.querySelector('.el-dialog__title')?.textContent?.trim(),
    labels: [...dlg.querySelectorAll('.el-form-item__label')].map((l) => l.innerText.trim()),
    bodyText: dlg?.innerText.replace(/\s+/g, ' ').trim().slice(0, 260),
    switchCount: dlg.querySelectorAll('.el-switch').length
  };
});
log('takeDialog', JSON.stringify(openTake), JSON.stringify(takeDialog));
dump('probe-take-dialog.json', { row: openTake, dialog: takeDialog });
await shot('02-take-dialog.png');
await closeDialog();

// ── ③ 流水抽屉（3003 有三笔：balanceAfter 4 / 7 / 5，时间倒序）─────────────
const openFlowDrawer = async (name) => {
  await page.evaluate((n) => {
    const rows = [...document.querySelectorAll('.lqg-cryo .el-table__body tr.el-table__row')];
    const row = rows.find((tr) => (tr.querySelectorAll('td')[1]?.innerText || '').includes(n));
    [...row.querySelectorAll('button')].find((b) => b.innerText.trim() === '流水')?.click();
  }, name);
  await page.waitForSelector('.el-drawer.open', { timeout: 15000 });
  await page.waitForSelector('.el-drawer.open .el-timeline-item', { timeout: 15000 }).catch(() => {});
  await sleep(1200);
};
const readFlowDrawer = () =>
  page.evaluate(() => {
    const root = document.querySelector('.el-drawer.open');
    const items = [...root.querySelectorAll('.el-timeline-item')];
    return {
      title: root?.querySelector('.el-drawer__title')?.textContent?.trim(),
      head: root?.querySelector('.lqg-cryo-flow__head')?.innerText.replace(/\s+/g, ' ').trim(),
      count: items.length,
      rows: items.map((it) => it.innerText.replace(/\s+/g, ' ').trim()),
      editButtons: [...root.querySelectorAll('button')].filter((b) => b.innerText.trim() === '修改').length,
      removeButtons: [...root.querySelectorAll('button')].filter((b) => b.innerText.trim() === '删除').length,
      bodyText: root?.querySelector('.el-drawer__body')?.innerText.replace(/\s+/g, ' ').trim().slice(0, 500)
    };
  });

await openFlowDrawer('T-hli05-GZ-N-P7-EM2-2e5');
const flowDrawer = await readFlowDrawer();
log('flowDrawer', JSON.stringify({ count: flowDrawer.count, head: flowDrawer.head, editButtons: flowDrawer.editButtons, removeButtons: flowDrawer.removeButtons }));
log('flowDrawer body', flowDrawer.bodyText);
dump('probe-flow-drawer.json', flowDrawer);
await shot('03-flow-drawer.png');

// ── ③b 「修改」：打开同款弹窗（类型不可改；支数是绝对值不是负 delta）─────────
takeCalls();
await page.evaluate(() => {
  const root = document.querySelector('.el-drawer.open');
  [...root.querySelectorAll('button')].find((b) => b.innerText.trim() === '修改')?.click();
});
await page.waitForSelector('.el-dialog', { timeout: 15000 });
await sleep(1500);
const editFlowDialog = await page.evaluate(() => {
  const dlg = document.querySelector('.el-dialog');
  return {
    title: dlg?.querySelector('.el-dialog__title')?.textContent?.trim(),
    bodyText: dlg?.innerText.replace(/\s+/g, ' ').trim().slice(0, 320),
    qtyValue: dlg.querySelector('.el-input-number input')?.value,
    typeText: dlg?.querySelector('.lqg-cryo-flow-dialog__type')?.textContent?.trim(),
    switchCount: dlg.querySelectorAll('.el-switch').length
  };
});
log('editFlowDialog', JSON.stringify(editFlowDialog));
dump('probe-flow-edit-dialog.json', editFlowDialog);
await shot('04-flow-edit-dialog.png');

// 改支数 3 → 2 并保存（后端记修改人；行上出现「已改 · 某某 时间」）
await page.evaluate(() => {
  const input = document.querySelector('.el-dialog .el-input-number input');
  const setter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value').set;
  setter.call(input, '2');
  input.dispatchEvent(new Event('input', { bubbles: true }));
  input.dispatchEvent(new Event('change', { bubbles: true }));
});
await sleep(600);
await page.evaluate(() => {
  const dlg = document.querySelector('.el-dialog');
  [...dlg.querySelectorAll('button')].find((b) => b.innerText.trim() === '保存')?.click();
});
await page.waitForFunction(() => !document.querySelector('.el-dialog'), { timeout: 15000 }).catch(() => {});
await sleep(2500);
const updateCalls = takeCalls().filter((c) => c.method !== 'GET');
const afterEdit = await readFlowDrawer();
log('after edit', JSON.stringify({ count: afterEdit.count, body: afterEdit.bodyText }));
log('after edit calls', JSON.stringify(updateCalls));
dump('probe-flow-after-edit.json', { drawer: afterEdit, writeCalls: updateCalls });

// ── ③c 「删除」：二次确认 → 软删（走 DELETE 接口）────────────────────────────
takeCalls();
await page.evaluate(() => {
  const root = document.querySelector('.el-drawer.open');
  [...root.querySelectorAll('button')].filter((b) => b.innerText.trim() === '删除').pop()?.click();
});
await page.waitForSelector('.el-message-box', { timeout: 15000 });
await sleep(900);
const confirmText = await page.evaluate(() => document.querySelector('.el-message-box')?.innerText.replace(/\s+/g, ' ').trim());
log('delete confirm', confirmText);
await page.evaluate(() => {
  const box = document.querySelector('.el-message-box');
  [...box.querySelectorAll('button')].find((b) => b.innerText.trim() === '确定')?.click();
});
await sleep(3000);
const deleteCalls = takeCalls().filter((c) => c.method !== 'GET');
const afterDelete = await readFlowDrawer();
log('after delete', JSON.stringify({ count: afterDelete.count, body: afterDelete.bodyText }));
log('after delete calls', JSON.stringify(deleteCalls));
dump('probe-flow-after-delete.json', { drawer: afterDelete, writeCalls: deleteCalls, confirmText });

// 收尾：把抽屉关掉（点蒙层）
await page.mouse.click(40, 600);
await sleep(1200);

// ── ④ 编辑抽屉（暂存 -80 = 是/否 两个按钮；switchCount 必须 0）───────────────
const openedEdit = await page.evaluate(() => {
  const rows = [...document.querySelectorAll('.lqg-cryo .el-table__body tr.el-table__row')];
  const row = rows.find((tr) => (tr.querySelectorAll('td')[1]?.innerText || '').includes('T-hli01-GZ-N-P2-EM2-2e5'));
  [...row.querySelectorAll('button')].find((b) => b.innerText.trim() === '编辑')?.click();
  return row.querySelectorAll('td')[1]?.innerText?.trim();
});
await page.waitForSelector('.el-drawer.open .lqg-cryo-drawer__section', { timeout: 15000 });
await sleep(2000);
const editDrawer = await page.evaluate(() => {
  const root = document.querySelector('.el-drawer.open');
  return {
    title: root?.querySelector('.el-drawer__title')?.textContent?.trim(),
    lastMod: root?.querySelector('.lqg-cryo-drawer__lastmod')?.innerText.replace(/\s+/g, ' ').trim(),
    sections: [...root.querySelectorAll('.lqg-cryo-drawer__section')].map((s) => s.innerText.trim()),
    labels: [...root.querySelectorAll('.el-form-item__label')].map((l) => l.innerText.trim()),
    radioButtons: [...root.querySelectorAll('.el-radio-button')].map((b) => ({
      text: b.innerText.trim(), checked: b.className.includes('is-active')
    })),
    initQtyValue: root.querySelector('.el-input-number input')?.value,
    switchCount: root.querySelectorAll('.el-switch').length,
    bodyText: root.innerText.replace(/\s+/g, ' ').trim().slice(0, 460)
  };
});
editDrawer.openedFor = openedEdit;
log('editDrawer', JSON.stringify(editDrawer));
dump('probe-edit-drawer.json', editDrawer);
await shot('05-edit-drawer.png');
await page.mouse.click(40, 600);
await sleep(1200);

// ── ⑤ 样本总表的「冻存」入口已点亮 + 带 sampleId 跳入自动过滤 ────────────────
await page.goto(`${BASE}/sample`, { waitUntil: 'networkidle2' });
await page.waitForSelector('.lqg-sample .el-table__row', { timeout: 20000 });
await sleep(1800);
const sampleActions = await page.evaluate(() => {
  const rows = [...document.querySelectorAll('.lqg-sample .el-table__body tr.el-table__row')];
  const row = rows.find((tr) => [...tr.querySelectorAll('button')].some((b) => b.innerText.trim() === '冻存'));
  return {
    firstRowActions: [...rows[0].querySelectorAll('td:last-child button')].map((b) => ({
      text: b.innerText.replace(/\s+/g, ' ').trim(), disabled: b.disabled
    })),
    cryoButtonDisabled: row ? [...row.querySelectorAll('button')].find((b) => b.innerText.trim() === '冻存')?.disabled : null
  };
});
log('sampleActions', JSON.stringify(sampleActions));
dump('probe-sample-row-action.json', sampleActions);
await shot('06-sample-row-action.png');

const jumped = await page.evaluate(() => {
  const rows = [...document.querySelectorAll('.lqg-sample .el-table__body tr.el-table__row')];
  // ★ 按行文本找（样本总表的列序号与本票无关，写死列号会随列顺序变化而失手）
  const row = rows.find((tr) => tr.innerText.includes('T-hli01'));
  [...row.querySelectorAll('button')].find((b) => b.innerText.trim() === '冻存')?.click();
  return row.innerText.replace(/\s+/g, ' ').trim().slice(0, 60);
});
await page.waitForSelector('.lqg-cryo .el-table__row', { timeout: 20000 });
await sleep(2500);
const filtered = await readTable();
log('filtered by sample', jumped, JSON.stringify({ url: filtered.url, rowCount: filtered.rowCount, names: filtered.rows.map((r) => r.cryoName) }));
dump('probe-filtered-by-sample.json', { sample: jumped, table: filtered });
await shot('07-cryo-filtered-by-sample.png');

dump('probe-errors.json', errors);
log('errors', JSON.stringify(errors.slice(0, 10)), 'count', errors.length);
await browser.close();
console.log('[cryoweb001] DONE');
