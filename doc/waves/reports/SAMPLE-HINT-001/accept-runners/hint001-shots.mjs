// SAMPLE-HINT-001 · 工作台样本总表「切片染色」列的截图 + DOM 证据
// （**不读 PNG**：只读 DOM 文本 / getComputedStyle / 接口返回值）
//
// 用法：cd <有 puppeteer-core 的目录> && node hint001-shots.mjs
//   （例如 cp 到 code/plus-ui/ 下跑；EMBED-WEB-001 的先例）
// 产物：doc/waves/reports/SAMPLE-HINT-001/*.png + probe-*.json
import fs from 'node:fs';
import puppeteer from 'puppeteer-core';

const BASE = 'http://127.0.0.1:8082';
const OUT = '/Users/wkui/Project/profile/project/freelance/projects/jiangxia-organoid/doc/waves/reports/SAMPLE-HINT-001';
const CHROME = '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome';
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
fs.mkdirSync(OUT, { recursive: true });

const browser = await puppeteer.launch({ executablePath: CHROME, headless: true, args: ['--no-sandbox'] });
const page = await browser.newPage();
await page.setViewport({ width: 1920, height: 1100 });

const log = (...a) => console.log('[hint001]', ...a);
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

// ── 样本总表 ────────────────────────────────────────────────────────────────
await page.goto(`${BASE}/sample`, { waitUntil: 'networkidle2' });
await page.waitForSelector('.lqg-sample .el-table__row');
await sleep(1200);

const table = await page.evaluate(() => {
  const headers = [...document.querySelectorAll('.lqg-sample .el-table__header th')]
    .map((th) => th.innerText.replace(/\s+/g, ' ').trim());
  const hintIndex = headers.findIndex((h) => h === '切片染色');
  const rows = [...document.querySelectorAll('.lqg-sample .el-table__body-wrapper tbody tr')].map((tr) => {
    const tds = [...tr.querySelectorAll('td')];
    const cell = hintIndex >= 0 ? tds[hintIndex] : null;
    const badges = cell ? [...cell.querySelectorAll('.lqg-hint__badge')].map((b) => b.innerText.replace(/\s+/g, ' ').trim()) : [];
    const badgeBg = cell?.querySelector('.lqg-hint__badge--sectioned')
      ? getComputedStyle(cell.querySelector('.lqg-hint__badge--sectioned')).backgroundColor : null;
    return {
      internalNo: tds[0]?.innerText.replace(/\s+/g, ' ').trim(),
      hintText: cell?.innerText.replace(/\s+/g, ' ').trim(),
      badges,
      sectionedBadgeBg: badgeBg,
    };
  });
  return { headerCount: headers.length, hintIndex, hintHeader: headers[hintIndex], rows };
});
log('table', JSON.stringify({ headerCount: table.headerCount, hintIndex: table.hintIndex, hintHeader: table.hintHeader }));
log('hint rows', JSON.stringify(table.rows));
dump('probe-hint-table.json', table);
await shot('01-sample-list-hint-column.png');

const byNo = Object.fromEntries(table.rows.map((r) => [r.internalNo, r.hintText]));
const checks = {
  headerCount: table.headerCount,
  hintHeaderIsLastBeforeRemark: table.hintHeader === '切片染色',
  '1001_T-hli01': byNo['T-hli01'],
  '1004_T-hli02': byNo['T-hli02'],
  '1006_T-hco04': byNo['T-hco04'],
  '1008_T-hli05': byNo['T-hli05'],
};
log('checks', JSON.stringify(checks));
dump('probe-hint-checks.json', checks);

// ── 悬停 1001 那一行 → 浮层列出各石蜡块编号与切片时间（悬停时再查） ───────────
// ★ 宽表要横向滚动才看得到最后一列，用 elementHandle.hover()（它自己滚进视口），
//   不要用 page.mouse.move(x,y) —— 表格宽 2600px+，坐标会落在视口外（第一版就这么假红过）。
const handles = await page.$$('.lqg-sample .el-table__body-wrapper tbody tr');
let targetHandle = null;
for (const tr of handles) {
  const frozen = await tr.evaluate((el) => el.querySelector('td')?.innerText.trim());
  if (frozen === 'T-hli01') {
    targetHandle = await tr.$('.lqg-hint');
    break;
  }
}
if (!targetHandle) { throw new Error('找不到 T-hli01 那一行的提示组件'); }
await targetHandle.hover();
// ★ 每一行各有一个 el-popover，全都被 teleport 到 body：`querySelector` 会拿到**第一个**
//   （别人的、隐藏的）浮层。要按「可见」挑，别按文档顺序挑（第一版就这么假红过）。
await page.waitForSelector('.lqg-hint__popper .lqg-hint__detail-row', { visible: true, timeout: 8000 });
await sleep(600);
const pop = await page.evaluate(() => {
  const popper = [...document.querySelectorAll('.lqg-hint__popper')].find((p) => p.offsetParent !== null);
  if (!popper) return { found: false };
  return {
    found: true,
    header: [...popper.querySelectorAll('.lqg-hint__detail-head span')].map((s) => s.innerText.trim()),
    rows: [...popper.querySelectorAll('.lqg-hint__detail-row')].map((r) => r.innerText.replace(/\s+/g, ' ').trim()),
    visible: getComputedStyle(popper).display !== 'none',
  };
});
log('popover', JSON.stringify(pop));
dump('probe-hint-popover.json', pop);
await shot('02-hint-hover-blocks.png');

// ── 点徽标 → 跳石蜡包埋页并按该样本过滤 ─────────────────────────────────────
await targetHandle.click();
await sleep(2500);
const nav = await page.evaluate(() => ({
  url: location.href,
  hash: location.hash,
  hasEmbedPage: !!document.querySelector('.lqg-embed'),
  filterTag: [...document.querySelectorAll('.lqg-embed .el-tag')].map((e) => e.innerText.trim()).filter(Boolean).slice(0, 6),
  rowCount: document.querySelectorAll('.lqg-embed .el-table__body-wrapper tbody tr').length,
}));
log('navigate', JSON.stringify(nav));
dump('probe-hint-click.json', nav);
await shot('03-hint-click-to-embed.png');

await browser.close();
log('DONE');
