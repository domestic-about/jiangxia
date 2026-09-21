import { createRequire } from 'node:module'
const require = createRequire('/Users/wkui/Project/profile/project/freelance/projects/jiangxia-organoid/code/miniapp/package.json')
const { chromium } = require('playwright')
const MP='http://127.0.0.1:9200'
const b=await chromium.launch({headless:true}); const ctx=await b.newContext({viewport:{width:390,height:844},deviceScaleFactor:2}); const p=await ctx.newPage()
const errs=[]; p.on('console',m=>{if(m.type()==='error')errs.push(m.text().slice(0,120))})
async function login(label){await p.goto(MP+'/#/pages/login/index',{waitUntil:'domcontentloaded'});await p.evaluate(()=>{localStorage.clear();sessionStorage.clear()});await p.reload({waitUntil:'domcontentloaded'});await p.waitForSelector('.login__agree',{timeout:25000});await p.waitForTimeout(400);const on=await p.$eval('.login__box',el=>el.className.includes('login__box--on')).catch(()=>false);if(!on)await p.click('.login__box');await p.click(`.login__mock-btn:has-text("${label}")`);await p.waitForSelector('.lqg-tile',{timeout:25000});await p.waitForTimeout(600)}
await login('内部人员 · 李工')
// readonly page via ledger row (mode=view) — value rendering
await p.goto(MP+'/#/pages/sample/form?id=9000001008&mode=view',{waitUntil:'domcontentloaded'}); await p.waitForTimeout(2500)
console.log('VIEW TEXT:',JSON.stringify((await p.evaluate(()=>document.body.innerText)).replace(/\n/g,'|').slice(0,300)))
console.log('errs after view:',JSON.stringify(errs.slice(0,3)))
errs.length=0
// seg button tap on new form
await p.goto(MP+'/#/pages/sample/form?mode=new',{waitUntil:'domcontentloaded'}); await p.waitForTimeout(2500)
const segBefore=await p.$$eval('.lqg-seg__item--on',els=>els.map(e=>e.innerText.trim()))
await p.locator('.lqg-seg__item:has-text("男")').first().click(); await p.waitForTimeout(800)
const segAfter=await p.$$eval('.lqg-seg__item--on',els=>els.map(e=>e.innerText.trim()))
console.log('seg on before:',JSON.stringify(segBefore),'after:',JSON.stringify(segAfter))
console.log('errs after seg tap:',JSON.stringify(errs.slice(0,3)))
await b.close()
