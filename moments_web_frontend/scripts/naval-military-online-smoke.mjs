import assert from 'node:assert/strict'
import { chromium } from 'playwright-core'
const base=process.env.QA_BASE_URL||'http://127.0.0.1:5173'
const browser=await chromium.launch({executablePath:'C:/Program Files/Google/Chrome/Application/chrome.exe',headless:true})
async function cell(p,x,y,side='own'){const c=p.locator(`.${side}-panel .naval-board`),b=await c.boundingBox();await c.click({position:{x:(48+(side==='own'?11-x:x)*48)/624*b.width,y:(48+y*48)/624*b.height}})}
async function state(p,predicate){await p.bringToFront();for(let i=0;i<150;i++){const s=await p.evaluate(()=>JSON.parse(localStorage.getItem('naval-guest-last-online')));if(s&&predicate(s))return s;await p.waitForTimeout(180)}throw new Error('Room did not reach expected state: '+(await p.locator('body').innerText()).slice(-1400))}
async function idle(p){await p.bringToFront();await p.waitForFunction(()=>!document.querySelector('.launch-overlay,.phase-announcement,.match-presentation,.stage-scene-leave-active'));await p.waitForTimeout(300)}
try{
  const a=await browser.newContext(),b=await browser.newContext(),host=await a.newPage(),peer=await b.newPage()
  for(const p of [host,peer]){p.on('pageerror',e=>{throw e});await p.goto(base+'/customer/entertainment/naval');await p.getByRole('button',{name:'军事模式',exact:true}).click()}
  await host.bringToFront();await host.getByRole('switch').uncheck();await host.getByRole('button',{name:'创建邀请房间',exact:true}).click()
  const waiting=await state(host,s=>s.phase==='WAITING');assert.equal(waiting.ruleSet,'MILITARY')
  await peer.bringToFront();await peer.getByRole('textbox',{name:'海战棋邀请码',exact:true}).fill(waiting.inviteCode);await peer.getByRole('button',{name:'加入房间',exact:true}).click()
  for(const [i,p] of [host,peer].entries()){
    await state(p,s=>s.phase==='PLACEMENT');await idle(p);await p.getByRole('button',{name:'购买巡洋舰',exact:true}).click()
    if(i===0)await p.getByRole('button',{name:'旋转巡洋舰',exact:true}).click()
    await cell(p,i===0?0:5,i===0?0:5);await p.getByRole('button',{name:'完成战备',exact:true}).click()
  }
  const battle=await state(host,s=>s.phase==='BATTLE');const hostTurn=battle.turn===battle.you,attacker=hostTurn?host:peer
  await state(attacker,s=>s.phase==='BATTLE'&&s.turn===s.you&&!s.presentationUntil);await idle(attacker);await attacker.getByRole('button',{name:/巡洋舰.*主炮/}).waitFor();await cell(attacker,hostTurn?5:2,hostTurn?5:0,'enemy')
  const won=await state(attacker,s=>s.phase==='FINISHED');assert.equal(won.winner,won.you);assert.equal(won.ruleSet,'MILITARY');assert.equal(won.settlement.ruleSet,'MILITARY')
  const loser=hostTurn?peer:host;const lost=await state(loser,s=>s.phase==='FINISHED');assert.notEqual(lost.winner,lost.you)
  console.log('Shared UI multiplayer: military invite, purchase, 90-degree deployment, authoritative attack, functional defeat and mode-specific archive passed.')
}finally{await browser.close()}
