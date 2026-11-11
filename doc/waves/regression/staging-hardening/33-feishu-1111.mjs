// 飞书 revision368 行27–32。运行：node doc/waves/regression/staging-hardening/33-feishu-1111.mjs
// LQG_API_BASE / LQG_WEB_BASE 可换本机端口，LQG_TEST_OUTPUT 指定临时证据目录。
import assert from 'node:assert/strict';
import { createRequire } from 'node:module';
import fs from 'node:fs';
import path from 'node:path';
const require = createRequire(path.resolve('code/miniapp/package.json'));
const { chromium } = require('playwright');
const API = process.env.LQG_API_BASE || 'http://127.0.0.1:18081';
const WEB = process.env.LQG_WEB_BASE || 'http://127.0.0.1:18082';
for (const url of [API, WEB]) assert(['localhost', '127.0.0.1'].includes(new URL(url).hostname), '仅允许本机回归');
const OUT = process.env.LQG_TEST_OUTPUT || path.join(process.env.TMPDIR || '/tmp', 'jiangxia-acceptance-20261111');
fs.mkdirSync(OUT, { recursive: true });
const client = fs.readFileSync('code/plus-ui/.env.development', 'utf8').match(/VITE_APP_CLIENT_ID\s*=\s*['"]([^'"]+)/)[1];
const password = process.env.LQG_TEST_PASSWORD || 'admin123';
const tag = `feishu1111-${Date.now()}`;
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
// revision 368，网页工作台27–32。仅本机临时记录，finally 删除。
try {
  token = (await api('POST', '/auth/login', { username:'lqgadmin', password, clientId:client, tenantId:'000000', grantType:'password' })).data.access_token;
  for (const kind of ['tissue','organoid']) for (const day of ['2026-11-10','2026-11-11','2026-11-12']) {
    const sampleId=String((await api('POST','/lqg/sample',{sampleKind:kind,sourceUnitName:tag,species:'人',internalNo:`${tag}-${created.length}`,receiveDate:day,[kind==='tissue'?'tissueType':'organoidType']:'肝'})).data);
    created.push({route:'/lqg/sample',id:sampleId});
    if(kind==='organoid') {
      const embedId=String((await api('POST','/lqg/embed',{sampleId,paraffinBlockNo:`${tag}-${day}`,paraffinEmbedTime:day,sectionTime:day})).data);
      created.push({route:'/lqg/embed',id:embedId});
      const cryoId=String((await api('POST','/lqg/cryo/batch',{sampleId,cryoName:tag,passage:'P3',freezeTime:day,initQty:5,inMinus80:'N',ln2Location:'QA-TEMP',toLn2Time:day})).data);
      created.push({route:'/lqg/cryo/batch',id:cryoId});
    }
  }
  const cases=[
    {path:'sample',route:'/lqg/sample/list',fields:[['receiveDateBegin','receiveDateEnd','receiveDate']],kind:'tissue'},
    {path:'sample-organoid',route:'/lqg/sample/list',fields:[['receiveDateBegin','receiveDateEnd','receiveDate']],kind:'organoid'},
    {path:'qc-docs',route:'/lqg/qc/list',fields:[['receiveBegin','receiveEnd','receiveDate']]},
    {path:'embed',route:'/lqg/embed/list',fields:[['sectionTimeBegin','sectionTimeEnd','sectionTime'],['paraffinEmbedTimeBegin','paraffinEmbedTimeEnd','paraffinEmbedTime']]},
    {path:'cryo',route:'/lqg/cryo/batch/list',fields:[['freezeTimeBegin','freezeTimeEnd','freezeTime']]}
  ];
  for(const c of cases) for(const [begin,end,field] of c.fields) {
    const q=new URLSearchParams({keyword:tag,pageSize:'100',[begin]:'2026-11-11',[end]:'2026-11-11',...(c.kind?{sampleKind:c.kind}:{})});
    const rows=(await api('GET',c.route+'?'+q)).rows;
    const ours=rows.filter(r=>JSON.stringify(r).includes(tag));
    check(`${c.path} ${field} 单日包含当天并排除前后一天`,ours.length===(c.path==='qc-docs'?2:1)&&ours.every(r=>r[field]==='2026-11-11'));
  }
  browser=await chromium.launch().catch(()=>chromium.launch({channel:'chrome'}));
  const page=await browser.newPage({viewport:{width:1440,height:1000}});page.setDefaultTimeout(15000);
  await page.goto(WEB+'/login'); await page.locator('.login-form input').nth(0).fill('lqgadmin'); await page.locator('.login-form input').nth(1).fill(password); await page.locator('.login-form button').click();await page.waitForURL(u=>!u.pathname.includes('login'));
  for(const c of cases) {
    await page.goto(WEB+'/'+c.path);await page.locator('.lqg-single-date input').first().waitFor();
    check(`${c.path} 仅单日输入，无起止日期框`,await page.locator('.lqg-single-date input').count()===c.fields.length&&await page.locator('.el-range-editor').count()===0);
    for(let i=0;i<c.fields.length;i++) {
      const [begin,end]=c.fields[i]; const input=page.locator('.lqg-single-date input').nth(i);
      await input.fill('2026-11-11');await input.press('Tab');
      const response=page.waitForResponse(r=>new URL(r.url()).pathname.endsWith(c.route)&&new URL(r.url()).searchParams.get(begin)==='2026-11-11');
      await page.getByRole('button',{name:/^搜\s*索$/}).click();const req=new URL((await response).url());await page.keyboard.press('Escape');await page.locator('.el-card__header').first().click();
      check(`${c.path} 第${i+1}个日期框提交同一天上下界`,req.searchParams.get(end)==='2026-11-11');
    }
    check(`${c.path} 操作列不覆盖数据列`,await page.locator('.is-fixed-column--right').count()===0);
    for(const width of [1280,1903]) {
      await page.setViewportSize({width,height:1000});
      const wrap=page.locator('.el-table__body-wrapper .el-scrollbar__wrap');
      await page.waitForTimeout(350);await wrap.evaluate(el=>{el.scrollLeft=el.scrollWidth});await page.waitForTimeout(350);
      await page.screenshot({path:`${OUT}/${c.path}-${width}.png`,fullPage:true});
      check(`${c.path} ${width}px 右侧操作表头完整可见`,await page.locator('.el-table__header-wrapper th').filter({hasText:/^操作$/}).last().evaluate(el=>{const r=el.getBoundingClientRect();return el.contains(document.elementFromPoint(r.right-5,r.top+r.height/2));}));
      await page.screenshot({path:`${OUT}/${c.path}-${width}.png`,fullPage:true});
    }
    await page.getByRole('button',{name:/^重\s*置$/}).click();
    check(`${c.path} 重置清空日期`,await page.locator('.lqg-single-date input').evaluateAll(els=>els.every(e=>e.value==='')));
  }
  for(const c of cases.filter(c=>c.path!=='qc-docs')) {
    await page.goto(WEB+'/'+c.path+'?timeBegin=2026-11-10&timeEnd=2026-11-12&operatorKey=u%3A9000000101&frozenByKey=u%3A9000000101');
    const tag=page.locator('.lqg-single-date__period');await tag.waitFor();
    check(`${c.path} 统计时段显式保留`,(await tag.innerText()).includes('2026-11-10')&&(await tag.innerText()).includes('2026-11-12'));
    await tag.locator('.el-tag__close').click();await tag.waitFor({state:'detached'});check(`${c.path} 可清除统计日期条件`,await tag.count()===0);
  }
} finally {
  if(browser) await browser.close();
  for(const {route,id} of [...created].reverse()) await api('DELETE',`${route}/${id}`);
  if(created.length) check('临时样本与下游记录已清理',(await api('GET',`/lqg/qc/list?keyword=${tag}`)).rows.length===0);
}
