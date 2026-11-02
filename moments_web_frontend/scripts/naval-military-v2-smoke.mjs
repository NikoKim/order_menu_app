import assert from 'node:assert/strict'
import {mkdir} from 'node:fs/promises'
import {chromium} from 'playwright-core'
import {createMilitaryGame, MILITARY_SHIPS as M, arrangeMilitaryFleet} from '../src/games/navalMilitary.js'
const base=process.env.QA_BASE_URL||'http://127.0.0.1:5173',out='../.agents/naval-military-v2'
await mkdir(out,{recursive:true})
const browser=await chromium.launch({executablePath:'C:/Program Files/Google/Chrome/Application/chrome.exe',headless:true})
const make=(kind,i)=>({kind,id:`${kind}-${i}`,w:M[kind].w,h:M[kind].h,x:-1,y:-1,rotation:0,hits:[],lostPlanes:[]})
async function idle(p){await p.waitForFunction(()=>!document.querySelector('.launch-overlay,.phase-announcement')&&[...document.querySelectorAll('.naval-board canvas')].every(c=>c.dataset.animating!=='true'))}
async function cell(p,x,y){const c=p.locator('.enemy-panel canvas'),b=await c.boundingBox();await c.click({position:{x:(48+x*48)/624*b.width,y:(48+y*48)/624*b.height}});if(await p.locator('.target-confirmation').count()){await p.waitForTimeout(300);await p.getByRole('button',{name:'确认使用',exact:true}).click()}}
try{
 const p=await browser.newPage({viewport:{width:1440,height:950}}),errors=[];p.on('pageerror',e=>errors.push(e.message))
 await p.addInitScript(()=>{window.soundStarts=[];const start=AudioBufferSourceNode.prototype.start;AudioBufferSourceNode.prototype.start=function(...a){window.soundStarts.push(this.buffer?.duration);return start.apply(this,a)}})
 let room=createMilitaryGame();room.phase='BATTLE';room.battleStarted=true;room.presentationSync=true;room.deadline=Date.now()+300000;room.players[0].ships=arrangeMilitaryFleet(['bomberCarrier','cruiser','submarine','antiAir','torpedo','radar','mineLayer'].map(make));room.players[0].ready=true;room.players[0].stage='READY';room.players[1].ships=arrangeMilitaryFleet(['carrier','cruiser'].map(make));room.players[1].ready=true
 await p.addInitScript(r=>localStorage.setItem('naval-guest-session',JSON.stringify(r)),room)
 await p.goto(base+'/customer/entertainment/naval');await p.getByRole('button',{name:'继续对局',exact:true}).click();await idle(p)
 assert.equal(await p.locator('.naval-board canvas').first().getAttribute('data-renderer'),'phaser-3.90')
 await p.keyboard.press('2');assert.match(await p.locator('.weapons>button.selected').innerText(),/巡洋舰/);await cell(p,11,10);await p.waitForTimeout(500);assert((await p.evaluate(()=>soundStarts)).some(d=>Math.abs(d-1.1)<.01),'classic cannon WAV played')
 await idle(p)
 // Restore a fresh player turn through the persisted authoritative local state.
 let stored=await p.evaluate(()=>JSON.parse(localStorage.getItem('naval-guest-session')));stored.turn=0;stored.presentationUntil=0;stored.deadline=Date.now()+300000
 await p.evaluate(r=>localStorage.setItem('naval-guest-session',JSON.stringify(r)),stored);await p.reload();await p.getByRole('button',{name:'继续对局',exact:true}).click();await idle(p)
 await p.getByRole('button',{name:/航母（轰炸机）/}).click();await cell(p,2,6);await p.waitForTimeout(950)
 const canvas=p.locator('.enemy-panel canvas');const x1=Number(await canvas.getAttribute('data-aircraft-x'));await p.waitForTimeout(300);const x2=Number(await canvas.getAttribute('data-aircraft-x'));assert(x2>x1,'enemy aircraft approaches from left independent of ship orientation')
 assert.equal(await canvas.getAttribute('data-incoming-direction'),'left-to-right')
 await p.screenshot({path:`${out}/bomber-flight.png`,fullPage:true});await idle(p)
 assert((await p.evaluate(()=>soundStarts)).some(d=>Math.abs(d-3.4)<.01),'classic bomber WAV played')
 assert.deepEqual(errors,[])
 const shop=await browser.newPage();await shop.goto(base+'/customer/entertainment/naval');await shop.getByRole('button',{name:'军事模式',exact:true}).click();await shop.getByRole('button',{name:'开始对战',exact:true}).click();await idle(shop)
 assert.equal(await shop.locator('.military-card').count(),6,'six hull cards, variants not duplicated')
 await shop.getByRole('button',{name:'切换航母类型',exact:true}).click();await shop.getByRole('button',{name:'购买航母（轰炸机）',exact:true}).click();await shop.getByRole('button',{name:'切换航母类型',exact:true}).click();await shop.getByRole('button',{name:'切换航母类型',exact:true}).click();assert(await shop.getByRole('button',{name:'购买航母（战斗机）',exact:true}).isDisabled(),'carrier cap shared across variants')
 await shop.getByRole('button',{name:'切换驱逐舰类型',exact:true}).click();await shop.getByRole('button',{name:'购买雷达驱逐舰',exact:true}).click();const row=shop.locator('.military-card').filter({has:shop.getByRole('button',{name:'切换驱逐舰类型',exact:true})});const plus=await row.getByRole('button',{name:'购买雷达驱逐舰',exact:true}).boundingBox(),type=await row.getByRole('button',{name:'切换驱逐舰类型',exact:true}).boundingBox();assert(type.x>=plus.x+plus.width&&Math.abs(type.y-plus.y)<10,'type switch follows plus button in same row');await shop.screenshot({path:`${out}/catalog.png`,fullPage:true})
 const jammerPage=await browser.newPage({viewport:{width:1440,height:950}}),jamRoom=createMilitaryGame();jamRoom.players[0].ships=[{...make('jammer',0),x:8,y:6,w:2,h:1,rotation:270},{...make('antiAir',1),x:5,y:6},{...make('carrier',2),x:0,y:2}];await jammerPage.addInitScript(r=>localStorage.setItem('naval-guest-session',JSON.stringify(r)),jamRoom);await jammerPage.goto(base+'/customer/entertainment/naval');await jammerPage.getByRole('button',{name:'继续对局',exact:true}).click();await idle(jammerPage);await jammerPage.screenshot({path:`${out}/jammer-red-box-match.png`,fullPage:true})
 const phone=await browser.newPage({viewport:{width:844,height:390},isMobile:true,hasTouch:true});phone.on('pageerror',e=>errors.push(e.message));await phone.addInitScript(r=>localStorage.setItem('naval-guest-session',JSON.stringify(r)),room);await phone.goto(base+'/customer/entertainment/naval');await phone.getByRole('button',{name:'继续对局',exact:true}).click();const full=phone.getByRole('button',{name:'全屏并锁定横屏',exact:true});if(await full.isVisible())await full.click({timeout:1500}).catch(async e=>{if(!await phone.evaluate(()=>!!document.fullscreenElement))throw e});await idle(phone);assert(await phone.evaluate(()=>!!document.fullscreenElement));assert.equal(await phone.locator('.naval-board canvas').first().getAttribute('data-renderer'),'phaser-3.90');await phone.screenshot({path:`${out}/mobile-phaser.png`,fullPage:true});assert.equal(await phone.evaluate(()=>document.documentElement.scrollWidth>innerWidth+1),false);assert.deepEqual(errors,[])
 console.log('Phaser military rendering, classic cannon/bomber WAV, moving horizontal flight, six cards and shared variant caps passed.')
}finally{await browser.close()}
