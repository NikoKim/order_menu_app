import assert from 'node:assert/strict'
import { chromium } from 'playwright-core'
import { createLocalGame, legalPlacement, FLEET } from '../src/games/navalEngine.js'
const browser=await chromium.launch({executablePath:'C:/Program Files/Google/Chrome/Application/chrome.exe',headless:true})
const errors=[]
const root='http://127.0.0.1:5174/customer/entertainment/naval'
const read=p=>p.evaluate(()=>JSON.parse(localStorage.getItem('naval-guest-session')))
async function idle(p) { await p.waitForFunction(()=>!document.querySelector('.launch-overlay,.phase-announcement,.stage-scene-leave-active,.mobile-sheet-leave-active')&&document.querySelector('.own-panel canvas')?.dataset.renderer); await p.waitForTimeout(250) }
async function cell(p,side,x,y) { const b=await p.locator(`.${side}-panel canvas`).boundingBox(); await p.touchscreen.tap(b.x+(48+x*48)/624*b.width,b.y+(48+y*48)/624*b.height) }
async function start(battle=false) {
 const c=await browser.newContext({viewport:{width:568,height:320},isMobile:true,hasTouch:true})
 const r=createLocalGame(); if(battle) { r.phase='BATTLE'; r.turn=0; r.players[0].stage='READY'; r.players[0].ready=true; r.players[0].inventory.fighter=2; r.players[0].inventory.submarine=2; r.players[1].aa=[];r.players[1].mines=[] }
 if(battle) { const positions=[[11,11],[11,0],[0,11],[5,5],[0,3],[3,3],[6,3],[0,6],[3,6],[7,7]];r.players[1].ships=FLEET.map((ship,i)=>({...ship,x:positions[i][0],y:positions[i][1],hits:[]})) }
 await c.addInitScript(r=>{r.deadline=Date.now()+300000;localStorage.setItem('naval-guest-session',JSON.stringify(r));localStorage.setItem('naval-guest-resume-kind','LOCAL')},r)
 const p=await c.newPage();p.on('pageerror',e=>errors.push(e.message)); await p.goto(root);await p.getByRole('button',{name:'继续对局',exact:true}).click();await idle(p);return {c,p}
}
try {
 const {c,p}=await start(); const r=await read(p),ship=r.players[0].ships[0]
 const point=Array.from({length:144},(_,i)=>({x:i%12,y:Math.floor(i/12)})).find(q=>(q.x!==ship.x||q.y!==ship.y)&&legalPlacement(r.players[0].ships,{...ship,...q}))
 const b=await p.locator('.own-panel canvas').boundingBox(),pos=q=>({x:b.x+(48+q.x*48)/624*b.width,y:b.y+(48+q.y*48)/624*b.height})
 const from=pos(ship),to=pos(point),cdp=await c.newCDPSession(p)
 await cdp.send('Input.dispatchTouchEvent',{type:'touchStart',touchPoints:[from]})
 for(let i=1;i<=12;i++) {await cdp.send('Input.dispatchTouchEvent',{type:'touchMove',touchPoints:[{x:from.x+(to.x-from.x)*i/12,y:from.y+(to.y-from.y)*i/12}]});await p.waitForTimeout(25)}
 await cdp.send('Input.dispatchTouchEvent',{type:'touchEnd',touchPoints:[]})
 await p.locator('.target-confirmation').waitFor();assert.deepEqual((await read(p)).players[0].ships,r.players[0].ships)
 await p.getByRole('button',{name:'确认使用',exact:true}).click();assert((await read(p)).players[0].ships.some(s=>s.id===ship.id&&s.x===point.x&&s.y===point.y))
 await p.locator('[data-tutorial="naval-layout-next"]').click();await idle(p)
 const fit=await p.locator('.prep-item').evaluateAll(items=>items.every(item=>{const a=item.getBoundingClientRect();return [...item.querySelectorAll('button')].every(button=>{const b=button.getBoundingClientRect();return b.top>=a.top&&b.bottom<=a.bottom&&b.left>=a.left&&b.right<=a.right})}))
 await p.screenshot({path:'../.agents/naval-qa/refinements-supply.png'})
 if(!fit) console.log(await p.locator('.prep-item').evaluateAll(items=>items.map(item=>({item:item.getBoundingClientRect().toJSON(),buttons:[...item.querySelectorAll('button')].map(b=>b.getBoundingClientRect().toJSON())}))))
 assert(fit,'all eight shop steppers remain inside their items')
 await p.getByRole('button',{name:'购买水雷',exact:true}).click();await p.locator('[data-tutorial="naval-supply-ready"]').click()
 await p.locator('.naval-notice').waitFor();const initial=await p.locator('.notice-countdown').boundingBox();await p.waitForTimeout(1200);const shorter=await p.locator('.notice-countdown').boundingBox();assert(shorter.width<initial.width);await p.locator('.naval-notice').waitFor({state:'hidden'})
 await p.getByRole('button',{name:'对局菜单',exact:true}).click();await p.getByRole('button',{name:'离开对局',exact:true}).click()
 await p.setViewportSize({width:390,height:844});await p.waitForTimeout(450)
 const dialog=await p.locator('.naval-dialog').boundingBox();assert(dialog.width>=300&&dialog.x>=0&&dialog.x+dialog.width<=390)
 await p.screenshot({path:'../.agents/naval-qa/refinements-portrait.png'});await c.close()
 const combat=await start(true),page=combat.p
 await page.locator('[data-tutorial="naval-mobile-weapons-toggle"]').click();await page.locator('[data-tutorial="naval-weapon-submarine"]').click();await idle(page)
 await cell(page,'enemy',4,5);await page.locator('.target-confirmation').waitFor();assert.equal((await read(page)).players[0].inventory.submarine,2)
 await page.screenshot({path:'../.agents/naval-qa/refinements-submarine-ghost.png'});await page.getByRole('button',{name:'取消预览',exact:true}).click()
 await page.locator('[data-tutorial="naval-mobile-weapons-toggle"]').click();await page.locator('[data-tutorial="naval-weapon-fighter"]').click();await idle(page)
 await cell(page,'enemy',0,0);await page.getByRole('button',{name:'确认使用',exact:true}).click()
 await page.waitForFunction(()=>+document.querySelector('.enemy-panel canvas').dataset.unrevealedCells>0)
 await page.waitForFunction(()=>{const c=document.querySelector('.enemy-panel canvas');return +c.dataset.visibleShots>0&&+c.dataset.unrevealedCells>0})
 await page.screenshot({path:'../.agents/naval-qa/refinements-progressive.png'})
 await page.waitForFunction(()=>document.querySelector('.enemy-panel canvas').dataset.unrevealedCells==='0')
 assert.equal((await read(page)).players[0].inventory.fighter,1)
 assert((await read(page)).players[1].shots.every(cell=>cell.result==='miss'),'all-miss aircraft still renders water impacts, never false AA interception')
 await combat.c.close();assert.deepEqual(errors,[])
 console.log(JSON.stringify({passed:true,checks:['touch drag and confirmation','eight shop steppers contained','portrait leave dialog fit','progressive aircraft trajectory reveal']}))
} catch(e) { console.log(JSON.stringify({errors,canvases:await Promise.all(browser.contexts().flatMap(c=>c.pages()).map(p=>p.locator('canvas').evaluateAll(cs=>cs.map(c=>({...c.dataset})))))})); throw e } finally {await browser.close()}
