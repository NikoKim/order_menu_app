import assert from 'node:assert/strict'
import { mkdir } from 'node:fs/promises'
import { chromium } from 'playwright-core'
import { createMilitaryGame, defaultMilitaryFleet, militaryInside } from '../src/games/navalMilitary.js'

const base=process.env.QA_BASE_URL||'http://127.0.0.1:5173',out='../.agents/naval-military-feedback'
await mkdir(out,{recursive:true})
const browser=await chromium.launch({executablePath:'C:/Program Files/Google/Chrome/Application/chrome.exe',headless:true})
const errors=[]
async function idle(page){await page.waitForFunction(()=>!document.querySelector('.launch-overlay,.phase-announcement')&&[...document.querySelectorAll('.naval-board canvas')].every(c=>c.dataset.animating!=='true'))}
async function cell(page,side,x,y){const canvas=page.locator(`.${side}-panel canvas`),b=await canvas.boundingBox();await canvas.click({position:{x:(48+(side==='own'?11-x:x)*48)/624*b.width,y:(48+y*48)/624*b.height}})}
async function full(page){const b=page.getByRole('button',{name:'全屏并锁定横屏',exact:true});if(await b.isVisible())await b.click({timeout:1500}).catch(async e=>{if(!await page.evaluate(()=>!!document.fullscreenElement))throw e})}
async function resume(page,room){await page.addInitScript(r=>{
  localStorage.setItem('naval-guest-session',JSON.stringify(r));localStorage.removeItem('naval_sound_muted')
  window.soundStarts=[];window.qaTimeOffset=0;const now=Date.now;Date.now=()=>now()+window.qaTimeOffset
  const start=AudioBufferSourceNode.prototype.start;AudioBufferSourceNode.prototype.start=function(...a){window.soundStarts.push(this.buffer?.duration);return start.apply(this,a)}
},room);await page.goto(base+'/customer/entertainment/naval');await page.getByRole('button',{name:'继续对局',exact:true}).click();await full(page);await idle(page)}
try{
  const phone=await browser.newPage({viewport:{width:844,height:390},isMobile:true,hasTouch:true});phone.on('pageerror',e=>errors.push(e.message))
  const room=createMilitaryGame();room.phase='BATTLE';room.battleStarted=true;room.players[0].ships=defaultMilitaryFleet();room.players[0].coins=0;room.players[0].ready=true;room.players[0].stage='READY';room.players[1].ships=[{kind:'cruiser',id:'enemy',x:9,y:7,w:1,h:3,rotation:0,hits:[],lostPlanes:[]}];room.players[1].coins=265;room.deadline=Date.now()+30000
  await resume(phone,room)
  assert.equal(await phone.locator('.status-coins').innerText(),'0')
  for(const [kind,weapon] of [['battleship','shot'],['carrier','fighter'],['torpedo','torpedo'],['radar','radar'],['submarine','submarine'],['mineLayer','mine']]){
    await phone.getByRole('button',{name:'展开攻击方式',exact:true}).click()
    await phone.locator(`[data-tutorial="naval-weapon-${weapon}-${kind}"]`).first().click()
    const old=await phone.evaluate(()=>JSON.parse(localStorage.getItem('naval-guest-session')).events.length)
    const side=weapon==='mine'?'own':'enemy'
    const point=weapon==='mine'?Array.from({length:144},(_,i)=>({x:i%12,y:Math.floor(i/12)})).find(c=>militaryInside(c.x,c.y)&&!room.players[0].ships.some(s=>c.x>=s.x&&c.x<s.x+s.w&&c.y>=s.y&&c.y<s.y+s.h)):{x:1,y:8}
    await cell(phone,side,point.x,point.y)
    await phone.locator(`.${side}-panel .target-confirmation`).waitFor()
    assert.equal(await phone.locator('.target-confirmation').count(),1,kind+' has exactly one preview')
    assert.equal(await phone.locator(`.${side==='own'?'enemy':'own'}-panel .target-confirmation`).count(),0)
    assert.equal(await phone.evaluate(()=>JSON.parse(localStorage.getItem('naval-guest-session')).events.length),old,'preview does not fire')
    if(weapon==='mine')await phone.screenshot({path:`${out}/mobile-own-mine-confirm.png`})
    await phone.getByRole('button',{name:'取消预览',exact:true}).click()
    assert.equal(await phone.locator('.target-confirmation').count(),0)
  }
  await phone.getByRole('button',{name:'展开攻击方式',exact:true}).click();await phone.locator('[data-tutorial="naval-weapon-shot-cruiser"]').click();await cell(phone,'enemy',11,11)
  assert.equal(await phone.locator('.target-confirmation').count(),0,'cruiser fires immediately')
  await phone.waitForFunction(()=>JSON.parse(localStorage.getItem('naval-guest-session')).events.some(e=>e.weapon==='shot'))

  const timer=await browser.newPage({viewport:{width:844,height:390},isMobile:true,hasTouch:true});timer.on('pageerror',e=>errors.push(e.message));await resume(timer,room)
  await timer.getByRole('button',{name:'对局菜单',exact:true}).click()
  await timer.evaluate(()=>{window.soundStarts=[];window.qaTimeOffset=JSON.parse(localStorage.getItem('naval-guest-session')).deadline-Date.now()-5500})
  await timer.waitForTimeout(5600)
  const ticks=await timer.evaluate(()=>window.soundStarts.filter(d=>Math.abs(d-.17)<.01).length)
  assert(ticks>=5,`last five seconds play actual WAVs even while menu open, got ${ticks}`)

  const shop=await browser.newPage({viewport:{width:844,height:390},isMobile:true,hasTouch:true});shop.on('pageerror',e=>errors.push(e.message));await shop.goto(base+'/customer/entertainment/naval')
  const classic=await shop.locator('.lobby-sea img').getAttribute('src');await shop.getByRole('button',{name:'军事模式',exact:true}).click();const military=await shop.locator('.lobby-sea img').getAttribute('src');assert.notEqual(classic,military)
  const bg=await shop.locator('.naval-page').evaluate(e=>getComputedStyle(e,'::before').backgroundImage);assert(bg.includes('harbor-v2.png')&&bg.includes('linear-gradient'),'compact landscape keeps fading image backdrop')
  await shop.screenshot({path:`${out}/mobile-lobby-background.png`})
  await shop.getByRole('button',{name:'开始对战',exact:true}).click();await full(shop);await idle(shop)
  assert.equal(await shop.locator('.prep-coins').innerText(),'300');await shop.getByRole('button',{name:'购买航母',exact:true}).click();assert.equal(await shop.locator('.prep-coins').innerText(),'250')
  const images=await shop.locator('.military-card .prep-item-name img').evaluateAll(es=>es.map(e=>({src:e.src,w:e.naturalWidth,h:e.naturalHeight})))
  assert(images.every(i=>i.src.startsWith('data:image/png')&&i.w===40&&i.h>=80),'all purchase icons are real hull models')
  await shop.screenshot({path:`${out}/mobile-coins-and-models.png`});assert.equal(await shop.evaluate(()=>document.documentElement.scrollWidth>innerWidth+1),false)
  const directory=await browser.newPage({viewport:{width:1440,height:950}});directory.on('pageerror',e=>errors.push(e.message))
  await directory.route('**/api/entertainment/naval/rooms',route=>route.fulfill({json:{code:0,data:['CLASSIC','MILITARY'].map((ruleSet,i)=>({id:`fixture-${i}`,ruleSet,mode:'MULTIPLAYER',phase:'WAITING',players:[{name:`Commander ${i}`}],spectators:0,allowSpectators:true,requiresInvite:false}))}}))
  await directory.addInitScript(()=>localStorage.setItem('naval-guest-history',JSON.stringify([{id:'c',mode:'AI',ruleSet:'CLASSIC',won:true,at:Date.now()},{id:'m',mode:'AI',ruleSet:'MILITARY',won:false,at:Date.now()}])))
  await directory.goto(base+'/customer/entertainment/naval');await directory.getByRole('button',{name:'游戏房间',exact:true}).click();await directory.locator('.directory-room').first().waitFor()
  assert.equal(await directory.locator('.directory-room').count(),2)
  assert.match(await directory.locator('.room-results').innerText(),/经典模式/);assert.match(await directory.locator('.room-results').innerText(),/军事模式/)
  await directory.getByRole('button',{name:'军事模式',exact:true}).click();assert.equal(await directory.locator('.directory-room').count(),2,'switching rules never filters the room directory')
  await directory.screenshot({path:`${out}/all-room-modes.png`})
  await directory.getByRole('button',{name:'历史战绩',exact:true}).click();assert.equal(await directory.locator('.history-entry').count(),1)
  assert.match(await directory.locator('.history-entry').innerText(),/军事模式/)
  await directory.getByRole('button',{name:'经典模式',exact:true}).click();assert.equal(await directory.locator('.history-entry').count(),1);assert.match(await directory.locator('.history-entry').innerText(),/经典模式/)
  assert.deepEqual(errors,[])
  console.log('Mobile gold, all non-cruiser confirmations, own-only mine preview, five real tick WAVs, fading mode backdrop and hull thumbnails passed.')
}finally{await browser.close()}
