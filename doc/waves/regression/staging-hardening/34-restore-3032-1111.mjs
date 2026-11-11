// CR-20261111-29：只读本机页面回归，不新增或修改业务记录。
// 33为上一批发布时的冻结判据；其中30–32的布局期望被本CR覆盖。
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { createRequire } from 'node:module';
const require = createRequire(path.resolve('code/miniapp/package.json'));
const { chromium } = require('playwright');
const WEB = process.env.LQG_WEB_BASE || 'http://127.0.0.1:18082';
assert(['localhost','127.0.0.1'].includes(new URL(WEB).hostname));
const OUT = process.env.LQG_TEST_OUTPUT || path.join(process.env.TMPDIR || '/tmp', 'jiangxia-restore-3032');
fs.mkdirSync(OUT, {recursive:true});
const check = (name,value) => {assert(value,name);console.log('PASS '+name);};
const browser = await chromium.launch().catch(()=>chromium.launch({channel:'chrome'}));
try {
  const page = await browser.newPage({viewport:{width:1280,height:1000}});
  page.setDefaultTimeout(15000);
  await page.goto(WEB+'/login');
  await page.locator('.login-form input').nth(0).fill('lqgadmin');
  await page.locator('.login-form input').nth(1).fill(process.env.LQG_TEST_PASSWORD || 'admin123');
  await page.locator('.login-form button').click();
  await page.waitForURL(url=>!url.pathname.includes('login'));
  for (const route of ['sample','sample-organoid','qc-docs']) {
    await page.goto(WEB+'/'+route);
    await page.locator('tr.el-table__row').first().waitFor();
    check(route+' 保留单日日期框',await page.locator('.lqg-single-date input').count()===1 && await page.locator('.el-range-editor').count()===0);
    const head=page.locator('.el-table__header-wrapper th').filter({hasText:/^操作$/});
    await head.waitFor();
    const wrap=page.locator('.el-table__body-wrapper .el-scrollbar__wrap');
    const before=await head.boundingBox();
    await wrap.evaluate(el=>{el.scrollLeft=el.scrollWidth});
    await page.waitForTimeout(300);
    const after=await head.boundingBox();
    check(route+' 横滚时操作列保持右侧位置',Math.abs(before.x-after.x)<2);
    check(route+' 横向滚动仍可用',await wrap.evaluate(el=>el.scrollLeft>0));
    check(route+' 操作表头可见',await head.evaluate(el=>{const r=el.getBoundingClientRect();return el.contains(document.elementFromPoint(r.x+r.width/2,r.y+r.height/2));}));
    if(route==='qc-docs') {
      const docs=page.locator('.el-table__header-wrapper th').filter({hasText:/^(样本质控表|类器官质控表|类器官质量评分表)$/});
      check('三份质控文档恢复原135px列宽',await docs.count()===3 && await docs.evaluateAll(els=>els.every(el=>Math.abs(el.getBoundingClientRect().width-135)<2)));
    }
    await page.screenshot({path:`${OUT}/${route}-restored.png`,fullPage:true});
  }
  await page.goto(WEB+'/embed');await page.locator('.lqg-single-date input').first().waitFor();
  check('包埋与切片仍为两个独立单日日期框',await page.locator('.lqg-single-date input').count()===2);
  await page.goto(WEB+'/cryo');await page.locator('.lqg-single-date input').waitFor();
  check('冻存仍为一个单日日期框',await page.locator('.lqg-single-date input').count()===1);
  const transfer=page.locator('.el-table__header-wrapper th').filter({hasText:'转移时间'});
  check('第29条转液氮两行表头保留',await transfer.locator('br').count()===1 && (await transfer.innerText()).includes('−80℃ → 液氮'));
} finally { await browser.close(); }
