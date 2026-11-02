import assert from 'node:assert/strict'
import {mkdir} from 'node:fs/promises'
import {chromium} from 'playwright-core'
import {createMilitaryGame} from '../src/games/navalMilitary.js'
const out='../.agents/naval-medals-ui';await mkdir(out,{recursive:true})
const browser=await chromium.launch({executablePath:'C:/Program Files/Google/Chrome/Application/chrome.exe',headless:true}),errors=[]
const medals=['classic-3','classic-5','classic-10','military-3','military-5','military-10']
async function idle(p){await p.waitForFunction(()=>!document.querySelector('.launch-overlay,.match-presentation,.phase-announcement'))}
async function full(p){const b=p.getByRole('button',{name:'全屏并锁定横屏',exact:true});if(await b.isVisible())await b.click({timeout:1500}).catch(async e=>{if(!await p.evaluate(()=>!!document.fullscreenElement))throw e})}
try{
 for(const mobile of [false,true]){
  const ctx=await browser.newContext({viewport:mobile?{width:844,height:390}:{width:1440,height:950},isMobile:mobile,hasTouch:mobile}),p=await ctx.newPage();p.on('pageerror',e=>errors.push(e.message));let wall={medalId:'fixture-own',unlocked:medals,equipped:'',classicCurrent:10,militaryCurrent:10,mine:true}
  await p.route('**/api/entertainment/naval/medals*',async r=>{if(r.request().method()==='PUT')wall={...wall,equipped:r.request().postDataJSON().medalId};await r.fulfill({json:{code:0,data:wall}})})
  await p.goto('http://127.0.0.1:5173/customer/entertainment/naval');await p.getByRole('button',{name:'勋章墙',exact:true}).click();const panel=p.locator('.naval-medal-wall');await panel.locator('.medal-row').last().waitFor();assert.equal(await panel.locator('.medal-row').count(),6)
  await panel.getByRole('button',{name:'佩戴',exact:true}).first().click();await panel.getByRole('button',{name:'卸下',exact:true}).waitFor();await p.waitForTimeout(350)
  if(!mobile){const a=await p.locator('.naval-primary-content').boundingBox(),b=await panel.boundingBox();assert(b.x>=a.x+a.width,'PC medal wall occupies the right column')}
  assert.equal(await p.locator('.naval-community .naval-medal-wall').count(),1,'lobby medals reuse the community panel')
  assert(await panel.locator('.medal-row>img').evaluateAll(imgs=>imgs.every(i=>i.complete&&i.naturalWidth===128)));await p.screenshot({path:`${out}/${mobile?'mobile':'pc'}-wall.png`});await p.getByRole('button',{name:'关闭社区面板',exact:true}).click()
  await p.getByRole('button',{name:'军事模式',exact:true}).click();await p.getByRole('button',{name:'开始对战',exact:true}).click();if(mobile)await full(p)
  await p.locator('.match-presentation[data-stage="FOUND"]').waitFor();assert.equal(await p.locator('.matched-fleets .naval-avatar').count(),2);assert(await p.locator('.matched-fleets .worn-medal').count());await p.screenshot({path:`${out}/${mobile?'mobile':'pc'}-match.png`});await idle(p)
  assert.equal(await p.getByRole('button',{name:'勋章墙',exact:true}).count(),0,'no medal icon during play')
  assert.equal(await p.getByRole('button',{name:'查看海军 AI的勋章墙',exact:true}).count(),0,'AI has no medal wall')
  await p.getByRole('button',{name:'查看我方舰队的勋章墙',exact:true}).filter({visible:true}).first().click();await panel.waitFor();await panel.getByRole('button',{name:'卸下',exact:true}).waitFor();await p.waitForTimeout(350)
  assert.equal(await p.locator(mobile?'.mobile-utility-panel[data-mobile-panel="medals"] .naval-medal-wall':'.naval-community .naval-medal-wall').count(),1,'avatar wall uses the existing right panel')
  if(mobile){const b=await panel.boundingBox();assert(b.x>=400,'in-game medals stay on the right')}
  await p.screenshot({path:`${out}/${mobile?'mobile':'pc'}-avatar-wall.png`});await p.getByRole('button',{name:mobile?'收起面板':'关闭社区面板',exact:true}).last().click()
  await ctx.close()
 }
 const ctx=await browser.newContext(),p=await ctx.newPage(),s=createMilitaryGame();s.phase='FINISHED';s.battleStarted=true;s.winner=0;s.players[0].ready=true;s.players[0].stage='READY';s.players[0].ships=[{id:'c',kind:'cruiser',x:0,y:0,w:1,h:3,rotation:0,hits:[],lostPlanes:[],minesUsed:0}];s.deadline=0
 await p.addInitScript(s=>localStorage.setItem('naval-guest-session',JSON.stringify(s)),s);p.on('pageerror',e=>errors.push(e.message));await p.goto('http://127.0.0.1:5173/customer/entertainment/naval');await p.getByRole('button',{name:'继续对局',exact:true}).click();await p.getByRole('button',{name:'再来一局',exact:true}).click();await p.locator('.match-presentation[data-stage="FOUND"]').waitFor();await p.screenshot({path:`${out}/rematch.png`})
 assert.deepEqual(errors,[]);console.log('Six medals, own equip, avatar walls, PC right column, mobile right sheet, matchup badges and rematch presentation passed.')
}finally{await browser.close()}
