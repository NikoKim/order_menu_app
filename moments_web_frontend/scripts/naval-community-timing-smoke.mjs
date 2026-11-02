import assert from 'node:assert/strict'
import { mkdir } from 'node:fs/promises'
import path from 'node:path'
import { chromium } from 'playwright-core'
import { createLocalGame, localBattleResult, shipCells } from '../src/games/navalEngine.js'
const base=process.env.QA_BASE_URL || 'http://127.0.0.1:5174', output=path.resolve('../.agents/naval-qa')
await mkdir(output,{recursive:true})
const browser=await chromium.launch({executablePath:'C:/Program Files/Google/Chrome/Application/chrome.exe',headless:true})
const errors=[]
async function api(url,data,token) {
  const response=await fetch(base+'/api/entertainment'+url,{method:data===undefined?'GET':'POST',headers:{'Content-Type':'application/json',...(token?{'X-Guest-Token':token}:{})},...(data===undefined?{}:{body:JSON.stringify(data)})})
  const payload=await response.json(); assert(response.ok && payload.code===0,`${url}: ${JSON.stringify(payload)}`); return payload.data
}
function observe(page) {page.on('pageerror',error=>errors.push(error.message))}
async function seeded(room,mobile=false) {
  const context=await browser.newContext({viewport:mobile?{width:844,height:390}:{width:1366,height:900},isMobile:mobile,hasTouch:mobile})
  await context.addInitScript(room=>{room.deadline=Date.now()+180000;localStorage.setItem('naval-guest-session',JSON.stringify(room));localStorage.setItem('naval-guest-resume-kind','LOCAL')},room)
  const page=await context.newPage();observe(page);await page.goto(base+'/customer/entertainment/naval');await page.getByRole('button',{name:'继续对局',exact:true}).click()
  await page.waitForFunction(()=>document.querySelector('.own-panel canvas')?.dataset.renderer && !document.querySelector('.launch-overlay'))
  return {context,page}
}
async function idle(page) {await page.waitForFunction(()=>!document.querySelector('.phase-announcement,.launch-overlay') && [...document.querySelectorAll('.naval-board canvas')].every(c=>c.dataset.animating!=='true') && !JSON.parse(localStorage.getItem('naval-guest-session') || 'null')?.presentationUntil,null,{timeout:20000})}
async function cell(page,x,y) {const b=await page.locator('.enemy-panel canvas').boundingBox();await page.mouse.click(b.x+(48+48*x)/624*b.width,b.y+(48+48*y)/624*b.height)}
try {
  for(const mode of ['AI','MULTIPLAYER']) for(const metric of ['wins','rate','economy']) assert(Array.isArray(await api(`/naval/leaderboard?mode=${mode}&metric=${metric}`)))
  const guest=await api('/guests',{});await api('/naval/lobby/messages',{text:'QA 海战大厅 😀'},guest.token)
  assert((await api('/naval/lobby/messages')).some(message=>message.text==='QA 海战大厅 😀'))
  for(const mobile of [false,true]) {
    const context=await browser.newContext({viewport:mobile?{width:390,height:844}:{width:1440,height:1000},isMobile:mobile,hasTouch:mobile})
    const historyRoom=createLocalGame(),detail=localBattleResult(historyRoom);detail.battleStarted=true
    await context.addInitScript(detail=>localStorage.setItem('naval-guest-history',JSON.stringify([{id:detail.gameId,gameId:detail.gameId,local:true,detail,mode:'AI',won:true,at:Date.now()}])),detail)
    const page=await context.newPage();observe(page);await page.goto(base+'/customer/entertainment/naval')
    assert.equal(await page.locator('.naval-community').count(),0,'community starts closed')
    await page.getByRole('button',{name:'排行榜',exact:true}).click();await page.getByRole('tab',{name:'获胜场数'}).waitFor()
    assert.equal(await page.locator('.naval-community .custom-select').count(),0)
    await page.getByRole('tab',{name:'人人对战'}).click();await page.getByRole('tab',{name:'胜率',exact:true}).click();await page.waitForTimeout(400)
    assert.equal(await page.locator('.community-error').count(),0)
    const body=await page.locator('.naval-primary-content').boundingBox(),drawer=await page.locator('.naval-community').boundingBox()
    if(!mobile) assert(body.x+body.width<=drawer.x,'desktop main and drawer must be side by side')
    else {assert(drawer.x+drawer.width<=390 && drawer.x>=0);assert.equal(await page.getByRole('link',{name:'返回娱乐',exact:true}).count(),0)}
    await page.screenshot({path:path.join(output,`community-ranking-${mobile?'mobile':'desktop'}.png`),fullPage:true})
    await page.getByRole('button',{name:'聊天大厅',exact:true}).click();await page.getByRole('heading',{name:'聊天大厅',exact:true}).waitFor()
    await page.getByRole('button',{name:'添加表情 👍'}).click();assert.equal(await page.getByRole('textbox',{name:'大厅消息'}).inputValue(),'👍')
    await page.screenshot({path:path.join(output,`community-chat-${mobile?'mobile':'desktop'}.png`)})
    await page.getByRole('button',{name:'关闭社区面板'}).click();await page.locator('.naval-community').waitFor({state:'detached'})
    await page.getByRole('button',{name:'历史战绩',exact:true}).click();await page.locator('.history-entry').first().click();await page.locator('.history-detail').waitFor()
    await page.getByRole('button',{name:'返回战绩列表'}).click();await page.getByRole('button',{name:'关闭社区面板'}).click()
    await page.getByRole('button',{name:'玩家点评',exact:true}).click();await page.locator('.review-gate').waitFor();assert.equal(await page.locator('.reviews-error').count(),0)
    await context.close()
  }
  const room=createLocalGame();room.phase='BATTLE';room.turn=0;room.players.forEach(p=>{p.stage='READY';p.ready=true;p.aa=[];p.mines=[]});room.players[0].inventory.fighter=1
  const {context,page}=await seeded(room)
  await idle(page);assert.equal(await page.getByRole('button',{name:'战局聊天',exact:true}).count(),0,'AI has no match chat')
  const occupied=new Set(room.players[1].ships.flatMap(shipCells).map(c=>`${c.x},${c.y}`));let aim
  for(let y=0;y<12&&!aim;y++)for(let x=0;x<12&&!aim;x++)if(!occupied.has(`${x},${y}`))aim={x,y}
  await page.evaluate(()=>{window.__overlap=[];window.__watch=setInterval(()=>{const r=JSON.parse(localStorage.getItem('naval-guest-session'));if(r.events.some(e=>e.player===1) && document.querySelector('.enemy-panel canvas')?.dataset.animating==='true')window.__overlap.push(Date.now())},20)})
  await cell(page,aim.x,aim.y)
  await page.waitForFunction(()=>document.querySelector('.enemy-panel canvas')?.dataset.animating==='true')
  const during=await page.evaluate(()=>JSON.parse(localStorage.getItem('naval-guest-session')))
  assert(during.presentationUntil);assert(!during.events.some(e=>e.player===1))
  await page.waitForFunction(()=>JSON.parse(localStorage.getItem('naval-guest-session')).events.some(e=>e.player===1),null,{timeout:20000})
  await page.evaluate(()=>clearInterval(window.__watch));assert.deepEqual(await page.evaluate(()=>window.__overlap),[])
  await context.close()
  const sub=createLocalGame();sub.phase='BATTLE';sub.turn=0;sub.players.forEach(p=>{p.stage='READY';p.ready=true;p.aa=[];p.mines=[]});sub.players[0].inventory.submarine=1;sub.players[1].mines=[{x:3,y:5}];sub.players[1].inventory.mine=1
  const fixture=await seeded(sub);await idle(fixture.page);await fixture.page.getByRole('button',{name:'潜艇',exact:true}).click();await cell(fixture.page,7,5)
  await fixture.page.waitForFunction(()=>document.querySelector('.enemy-panel canvas')?.dataset.effectWeapon==='submarine')
  await fixture.page.waitForTimeout(450);await fixture.page.screenshot({path:path.join(output,'submarine-underwater.png')})
  await fixture.page.waitForFunction(()=>document.querySelector('.enemy-panel canvas')?.dataset.submarineDestroyed==='true')
  await fixture.page.waitForTimeout(1450)
  const outcome=await fixture.page.evaluate(()=>JSON.parse(localStorage.getItem('naval-guest-session')).events.findLast(e=>e.weapon==='submarine'))
  assert(outcome.destroyed);assert.equal(outcome.cells.length,3);assert.equal(outcome.torpedoes.length,0)
  await fixture.page.screenshot({path:path.join(output,'submarine-resurface-explosion.png')});await fixture.context.close()
  assert.deepEqual(errors,[]);console.log('Community API, desktop/mobile panels, subtabs, emoji, local history, review gate, animation timing and submarine mine checks passed')
} finally {await browser.close()}
