import assert from 'node:assert/strict'
import {mkdir} from 'node:fs/promises'
import {chromium} from 'playwright-core'
import {createMilitaryGame,MILITARY_SHIPS as M} from '../src/games/navalMilitary.js'
const base=process.env.QA_BASE_URL||'http://127.0.0.1:5173',out='../.agents/naval-carrier-revision';await mkdir(out,{recursive:true})
const browser=await chromium.launch({executablePath:'C:/Program Files/Google/Chrome/Application/chrome.exe',headless:true}),errors=[]
async function idle(p){await p.waitForFunction(()=>!document.querySelector('.launch-overlay,.phase-announcement')&&[...document.querySelectorAll('.naval-board canvas')].every(c=>c.dataset.animating!=='true'))}
async function full(p){const b=p.getByRole('button',{name:'全屏并锁定横屏',exact:true});if(await b.isVisible())await b.click({timeout:1500}).catch(async e=>{if(!await p.evaluate(()=>!!document.fullscreenElement))throw e})}
async function cell(p,side,x,y){const c=p.locator(`.${side}-panel canvas`),b=await c.boundingBox();await c.click({position:{x:(48+(side==='own'?11-x:x)*48)/624*b.width,y:(48+y*48)/624*b.height}})}
const ship=(kind,id,x,y)=>({kind,id,x,y,w:M[kind].w,h:M[kind].h,rotation:0,hits:[],lostPlanes:[],minesUsed:0})
try{
 const p=await browser.newPage({viewport:{width:844,height:390},isMobile:true,hasTouch:true});p.on('pageerror',e=>errors.push(e.message));await p.goto(base+'/customer/entertainment/naval');await p.getByRole('button',{name:'军事模式',exact:true}).click();await p.getByRole('button',{name:'开始对战',exact:true}).click();await full(p);await idle(p)
 assert.match(await p.locator('.prep-clock').innerText(),/7:5\d|8:00/)
 for(let i=0;i<2;i++)await p.getByRole('button',{name:'切换航母类型',exact:true}).click()
 await p.getByRole('button',{name:'购买航母（鱼雷机）',exact:true}).click();assert.equal(await p.locator('.prep-coins').innerText(),'240');assert.equal(await p.getByRole('button',{name:'购买鱼雷驱逐舰',exact:true}).count(),0)
 await p.getByRole('button',{name:'舰队编成',exact:true}).click();await p.locator('.prep-ship>button').first().click();await cell(p,'own',2,2)
 const confirm=p.locator('.own-panel .target-confirmation');await confirm.waitFor();assert.equal(await confirm.locator('button').count(),3)
 await confirm.getByRole('button',{name:'旋转预览舰船',exact:true}).click();assert.equal(await confirm.locator('button').count(),3)
 await p.screenshot({path:`${out}/rotate-before-confirm.png`});await confirm.getByRole('button',{name:'确认使用',exact:true}).click();assert.equal(await p.evaluate(()=>JSON.parse(localStorage.getItem('naval-guest-session')).players[0].ships[0].rotation),90)
 const battle=await browser.newPage({viewport:{width:844,height:390},isMobile:true,hasTouch:true});battle.on('pageerror',e=>errors.push(e.message));const room=createMilitaryGame();room.phase='BATTLE';room.battleStarted=true;room.players[0].ships=[ship('mineLayer','m',0,0),ship('cruiser','c',3,0),ship('torpedoCarrier','t',6,0)];room.players[0].ready=true;room.players[0].stage='READY';room.players[1].ships=[ship('cruiser','e',9,9)];room.deadline=Date.now()+30000
 await battle.addInitScript(r=>localStorage.setItem('naval-guest-session',JSON.stringify(r)),room);await battle.goto(base+'/customer/entertainment/naval');await battle.getByRole('button',{name:'继续对局',exact:true}).click();await full(battle);await idle(battle);assert.equal(await battle.locator('.dock-chevron').count(),0)
 await battle.getByRole('button',{name:'展开攻击方式',exact:true}).click();await battle.locator('[data-tutorial="naval-weapon-mine-mineLayer"]').click()
 for(let n=0;n<2;n++){await cell(battle,'own',8+n,8);assert.equal(await battle.locator('.target-confirmation').count(),1);await battle.getByRole('button',{name:'确认使用',exact:true}).click();await battle.waitForFunction(n=>JSON.parse(localStorage.getItem('naval-guest-session')).players[0].mines.length===n,n+1);const state=await battle.evaluate(()=>JSON.parse(localStorage.getItem('naval-guest-session')));assert.equal(state.turn,n===0?0:1);assert.equal(state.mineActions,n===0?1:0);if(n===0){await idle(battle);await battle.screenshot({path:`${out}/first-mine-still-your-turn.png`})}}
 assert.deepEqual(errors,[])
 console.log('Torpedo carrier purchase, 8-minute clock, preview rotate/check/cancel and arrow removal checked.')
}finally{await browser.close()}
