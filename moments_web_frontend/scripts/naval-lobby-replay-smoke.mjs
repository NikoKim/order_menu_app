import assert from 'node:assert/strict'
import { mkdir } from 'node:fs/promises'
import path from 'node:path'
import { chromium } from 'playwright-core'
import { createLocalGame, generateFleet, localBattleResult } from '../src/games/navalEngine.js'
const base=process.env.QA_BASE_URL || 'http://127.0.0.1:5174', output=path.resolve('../.agents/naval-qa')
await mkdir(output,{recursive:true})
const browser=await chromium.launch({executablePath:'C:/Program Files/Google/Chrome/Application/chrome.exe',headless:true})
const errors=[]
const delay=ms=>new Promise(resolve=>setTimeout(resolve,ms))
async function api(url,data,token) { const response=await fetch(base+'/api/entertainment'+url,{method:data===undefined?'GET':'POST',headers:{'Content-Type':'application/json',...(token?{'X-Guest-Token':token}:{})},...(data===undefined?{}:{body:JSON.stringify(data)})});const payload=await response.json();assert(response.ok && payload.code===0,`${url}: ${JSON.stringify(payload)}`);return payload.data }
function historyFixture() {
  const room=createLocalGame(),ship=room.players[1].ships[0],own=room.players[0].ships[0]
  const hit={x:ship.x,y:ship.y,result:'hit'},otherHit={x:own.x,y:own.y,result:'hit'}
  room.phase='FINISHED';room.winner=0;room.events=[{id:1,player:0,weapon:'shot',x:hit.x,y:hit.y,cells:[hit],text:'舰炮命中'}, {id:2,player:1,weapon:'bomber',x:otherHit.x,y:otherHit.y,cells:[otherHit],text:'轰炸机投弹'}, {id:3,player:0,weapon:'radar',x:3,y:3,cells:[],text:'雷达扫描'}]
  ship.hits=[`${hit.x},${hit.y}`];own.hits=[`${otherHit.x},${otherHit.y}`];room.players[1].shots=[hit];room.players[0].shots=[otherHit]
  const detail=localBattleResult(room);detail.battleStarted=true
  return [{id:detail.gameId,gameId:detail.gameId,local:true,detail,mode:'AI',won:true,at:Date.now()}]
}
async function pageFor(viewport,mobile=false) {
  const context=await browser.newContext({viewport,isMobile:mobile,hasTouch:mobile,...(mobile?{userAgent:'Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X) AppleWebKit/605.1.15 Version/18.0 Mobile Safari/604.1'}:{})})
  await context.addInitScript(history=>localStorage.setItem('naval-guest-history',JSON.stringify(history)),historyFixture())
  const page=await context.newPage();page.on('pageerror',e=>errors.push(e.message));await page.goto(base+'/customer/entertainment/naval');await page.getByRole('heading',{name:'选择出航方式'}).waitFor();return{context,page}
}
async function inside(page,selector,viewport) { const box=await page.locator(selector).boundingBox();assert(box,selector);assert(box.x>=-1 && box.y>=-1 && box.x+box.width<=viewport.width+1 && box.y+box.height<=viewport.height+1,`${selector} ${JSON.stringify(box)}`);return box }
try {
  const guest=await api('/guests',{}), room=await api('/naval/rooms',{mode:'MULTIPLAYER',autoMatch:false},guest.token)
  try {
    const sent=await api('/naval/lobby/messages',{roomId:room.id},guest.token)
    assert.equal(sent.at(-1).room.status,'WAITING');assert.equal(sent.at(-1).text,'')
    await delay(1550)
    const stickers=await api('/naval/lobby/messages',{stickerId:'applause'},guest.token)
    assert.equal(stickers.at(-1).stickerId,'applause');assert.equal(stickers.at(-1).text,'')
    const peer=await api('/guests',{});await api('/naval/rooms/join',{inviteCode:room.inviteCode},peer.token)
    assert.equal((await api('/naval/lobby/messages')).find(m=>m.roomId===room.id).room.status,'STARTED')
    await api(`/naval/rooms/${room.id}/actions`,{type:'LEAVE'},peer.token)
    assert.equal((await api('/naval/lobby/messages')).find(m=>m.roomId===room.id).room.status,'FINISHED')
  } finally {await api(`/naval/rooms/${room.id}/actions`,{type:'LEAVE'},guest.token)}
  for(const viewport of [{width:320,height:740},{width:390,height:844},{width:568,height:320},{width:1440,height:900}]) {
    const mobile=viewport.width<1000,{context,page}=await pageFor(viewport,mobile)
    assert.match(await page.locator('.guest-storage').innerText(),/更换设备或浏览器将无法同步数据/)
    assert.match(await page.getByRole('link',{name:'登录',exact:true}).getAttribute('href'),/customer\/login/)
    assert.match(await page.getByRole('link',{name:'注册',exact:true}).getAttribute('href'),/customer\/register/)
    await inside(page,'.header-actions',viewport)
    if(mobile && viewport.height>viewport.width) assert(await page.evaluate(()=>document.documentElement.scrollHeight>innerHeight),'mobile homepage may scroll')
    await page.screenshot({path:path.join(output,`naval-v9-home-${viewport.width}.png`),fullPage:true})
    await page.getByRole('button',{name:'聊天大厅',exact:true}).click();await page.waitForTimeout(450)
    const drawer=await inside(page,'.naval-community',viewport)
    if(mobile) {assert(drawer.width>=viewport.width-1);assert(Math.abs(drawer.y+drawer.height-viewport.height)<2)}
    assert.equal(await page.locator('.sticker-picker').count(),0)
    await page.getByRole('button',{name:'展开舰队表情包'}).click();await page.waitForTimeout(220)
    await inside(page,'.sticker-picker',viewport);assert.equal(await page.locator('.sticker-grid>button').count(),8)
    await page.screenshot({path:path.join(output,`naval-v9-stickers-${viewport.width}.png`)})
    await page.getByRole('button',{name:'发送表情包：等得困了',exact:true}).click()
    await page.waitForFunction(()=>!document.querySelector('.sticker-picker') && [...document.querySelectorAll('.lobby-chat-log img')].some(img=>img.alt==='等得困了'))
    assert.equal(await page.locator('.community-error').count(),0)
    await page.getByRole('button',{name:'关闭社区面板'}).click();await page.waitForTimeout(320)
    await page.getByRole('button',{name:'历史战绩',exact:true}).click();await page.locator('.history-entry').first().click()
    await page.getByRole('button',{name:'对局回放',exact:true}).waitFor();assert.equal(await page.locator('.history-overlay,[aria-modal="true"]').count(),0,'history stays inline')
    await page.getByRole('button',{name:'对局回放',exact:true}).click()
    await page.waitForFunction(()=>document.querySelectorAll('.naval-replay canvas[data-renderer]').length===2)
    await page.getByRole('button',{name:'下一次攻击',exact:true}).click()
    await page.waitForFunction(()=>document.querySelector('.replay-controls progress')?.value===1 && [...document.querySelectorAll('.naval-replay canvas')].every(c=>c.dataset.animating!=='true'))
    await page.getByRole('button',{name:'自动播放',exact:true}).click()
    await page.waitForFunction(()=>document.querySelector('.replay-controls progress')?.value===3,null,{timeout:25000})
    await page.getByRole('button',{name:'聊天大厅',exact:true}).click();await page.waitForTimeout(350)
    await inside(page,'.naval-community',viewport)
    await page.screenshot({path:path.join(output,`naval-v9-replay-chat-${viewport.width}.png`)})
    await context.close()
  }
  const waiting=await pageFor({width:844,height:390},true)
  let hostToken,hostRoom,peerToken
  try {
    await waiting.page.locator('.match-toggle input').uncheck()
    await waiting.page.getByRole('button',{name:'创建邀请房间',exact:true}).click()
    await waiting.page.waitForFunction(()=>document.querySelector('.waiting-toolbar') && !document.querySelector('.launch-overlay,.match-presentation'),null,{timeout:25000})
    hostToken=await waiting.page.evaluate(()=>localStorage.getItem('entertainment_guest_token'))
    const id=await waiting.page.evaluate(()=>localStorage.getItem('naval-guest-room'))
    hostRoom=await api(`/naval/rooms/${id}`,undefined,hostToken)
    await waiting.page.getByRole('button',{name:'聊天大厅',exact:true}).click()
    await waiting.page.getByRole('button',{name:'分享当前房间到大厅',exact:true}).click()
    await waiting.page.waitForFunction(()=>[...document.querySelectorAll('.lobby-room-card')].some(card=>card.textContent.includes('等待加入')))
    await waiting.page.getByRole('button',{name:'关闭社区面板'}).click()
    await waiting.page.getByRole('button',{name:'对局菜单',exact:true}).click()
    assert.equal(await waiting.page.locator('.mobile-menu-actions .community-actions').count(),0)
    await waiting.page.screenshot({path:path.join(output,'naval-v9-match-menu.png')})
    await waiting.page.locator('.mobile-utility-panel').getByRole('button',{name:'收起面板',exact:true}).click()
    await waiting.page.getByRole('button',{name:'聊天大厅',exact:true}).click()
    peerToken=(await api('/guests',{})).token
    await api('/naval/rooms/join',{inviteCode:hostRoom.inviteCode},peerToken)
    await waiting.page.waitForFunction(()=>!document.querySelector('.naval-community'),null,{timeout:10000})
    assert.equal(await waiting.page.getByRole('button',{name:'聊天大厅',exact:true}).count(),0,'lobby is hidden during placement')
    await api(`/naval/rooms/${id}/actions`,{type:'LAYOUT_READY',ships:generateFleet()},hostToken)
    await api(`/naval/rooms/${id}/actions`,{type:'LAYOUT_READY',ships:generateFleet()},peerToken)
    await api(`/naval/rooms/${id}/actions`,{type:'READY'},hostToken)
    await api(`/naval/rooms/${id}/actions`,{type:'READY'},peerToken)
    await waiting.page.waitForFunction(()=>document.querySelector('.mobile-player-status') && !document.querySelector('.phase-announcement,.launch-overlay,.match-presentation'),null,{timeout:20000})
    await waiting.page.getByRole('button',{name:'战局聊天',exact:true}).click()
    await waiting.page.getByRole('button',{name:'展开舰队表情包'}).click()
    await waiting.page.getByRole('button',{name:'发送表情包：放马过来',exact:true}).click()
    await waiting.page.waitForFunction(()=>document.querySelector('.status-chat-bubble.sticker-pop img')?.alt==='放马过来')
    await waiting.page.screenshot({path:path.join(output,'naval-v9-battle-sticker.png')})
  } finally {if(hostRoom) {if(peerToken) await api(`/naval/rooms/${hostRoom.id}/actions`,{type:'LEAVE'},peerToken);await api(`/naval/rooms/${hostRoom.id}/actions`,{type:'LEAVE'},hostToken)} await waiting.context.close()}
  const context=await browser.newContext({viewport:{width:1280,height:800}}),page=await context.newPage(),requests=[]
  page.on('request',request=>{if(/navalScene|phaser/i.test(request.url()))requests.push(request.url())})
  await page.goto(base+'/customer/entertainment');await page.getByRole('link',{name:'海战棋',exact:true}).click();await page.getByRole('heading',{name:'选择出航方式'}).waitFor();assert.deepEqual(requests,[],'naval tab must not wait for Phaser')
  await context.close();assert.deepEqual(errors,[])
  console.log('PASS: standalone sticker/card posts, live room status, responsive drawers, scrollable mobile home, inline history, manual/automatic replay, lobby during replay, deferred Phaser')
} finally {await browser.close()}
