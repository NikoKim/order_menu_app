import assert from 'node:assert/strict'
import { chromium } from 'playwright-core'
import { mkdir } from 'node:fs/promises'
import { createLocalGame, localBattleResult } from '../src/games/navalEngine.js'

const base=process.env.QA_BASE_URL || 'http://127.0.0.1:5175'
const output='../.agents/naval-qa'
await mkdir(output,{recursive:true})
const browser=await chromium.launch({executablePath:'C:/Program Files/Google/Chrome/Application/chrome.exe',headless:true})
const errors=[]
function record() {
  const room=createLocalGame(); const result=localBattleResult(room)
  result.gameId=`local-qa-${Date.now()}:1`; result.battleStarted=true; result.winner=0
  result.events=[
    {id:1,player:1,weapon:'torpedo',x:1,y:2,cells:[1,2,3,4,5].map(x=>({x,y:2,result:'miss'})),text:'鱼雷镜像'},
    {id:2,player:1,weapon:'submarine',x:4,y:4,targets:[4,5,6].map(x=>({x,y:4})),route:[{x:0,y:4},{x:4,y:4}],torpedoes:[[{x:5,y:4},{x:5,y:3},{x:5,y:2}],[{x:6,y:4},{x:6,y:5}]],cells:[{x:5,y:3,result:'miss'},{x:5,y:2,result:'miss'},{x:6,y:5,result:'miss'}],text:'潜艇镜像'},
    {id:3,player:0,weapon:'shot',x:3,y:8,cells:[{x:3,y:8,result:'mine'}],backfire:[{x:3,y:8,result:'miss'}],text:'水雷同坐标反击'}
  ]
  return result
}
async function openRecord(page) {
  await page.goto(base+'/customer/entertainment/naval')
  await page.getByRole('button',{name:'历史战绩',exact:true}).click()
  await page.locator('.history-entry').first().click()
  await page.getByRole('button',{name:'对局回放',exact:true}).waitFor()
}
try {
  const detail=record(), desktop=await browser.newContext({viewport:{width:1280,height:800}})
  await desktop.addInitScript(detail=>localStorage.setItem('naval-guest-history',JSON.stringify([{id:detail.gameId,gameId:detail.gameId,detail,local:true,mode:'AI',won:true,at:Date.now()}])),detail)
  const page=await desktop.newPage(); page.on('pageerror',e=>errors.push(e.message))
  await openRecord(page)
  await page.getByRole('button',{name:'分享战绩到大厅',exact:true}).click()
  await page.getByRole('button',{name:'已分享到大厅',exact:true}).waitFor()
  await page.getByRole('button',{name:'聊天大厅',exact:true}).click()
  await page.locator('.battle-result-card').first().waitFor()
  await page.locator('.battle-result-card').last().click()
  assert.equal(await page.getByRole('button',{name:'分享战绩到大厅',exact:true}).count(),0,'shared cards cannot impersonate ownership')
  await page.getByRole('button',{name:'对局回放',exact:true}).click()
  await page.waitForFunction(()=>document.querySelectorAll('.replay-boards canvas[data-renderer]').length===2)
  const own=page.locator('.replay-boards section').first().locator('canvas')
  assert.equal(await own.evaluate(el=>getComputedStyle(el).transform),'matrix(-1, 0, 0, 1, 0, 0)')
  const next=page.getByRole('button',{name:'下一次攻击',exact:true})
  await next.click()
  await page.waitForFunction(()=>document.querySelector('.replay-boards canvas')?.dataset.torpedoDirection==='right-to-left')
  await page.waitForFunction(()=>Boolean(document.querySelector('.replay-boards canvas')?.dataset.torpedoX))
  const torpedo=await own.evaluate(canvas=>({x:Number(canvas.dataset.torpedoX),pixel:canvas.getContext('2d').getImageData(100,100,1,1).data[3]}))
  assert(torpedo.pixel>0,'game canvas is not blank')
  await page.waitForTimeout(180)
  assert(Number(await own.getAttribute('data-torpedo-x'))<torpedo.x,'incoming torpedo travels right to left on the defending sea')
  await page.screenshot({path:output+'/naval-mirrored-torpedo.png'})
  await next.waitFor({state:'visible'}); await page.waitForFunction(()=>!document.querySelector('.replay-controls button[aria-label="下一次攻击"]')?.disabled)
  await next.click()
  await page.waitForFunction(()=>document.querySelector('.replay-boards canvas')?.dataset.submarineDirection==='right-to-left')
  await page.screenshot({path:output+'/naval-mirrored-submarine.png'})
  await page.waitForFunction(()=>!document.querySelector('.replay-controls button[aria-label="下一次攻击"]')?.disabled)
  await next.click()
  await page.waitForFunction(()=>document.querySelector('.replay-boards canvas')?.dataset.effectWeapon==='mine-rebound')
  await page.waitForTimeout(700)
  assert.equal(await own.getAttribute('data-visible-shots'),'8')
  await page.screenshot({path:output+'/naval-mine-rebound-replay.png'})
  await desktop.close()

  const mobile=await browser.newContext({viewport:{width:390,height:844},isMobile:true,hasTouch:true})
  await mobile.addInitScript(detail=>{
    localStorage.setItem('naval-guest-history',JSON.stringify([{id:detail.gameId,detail,local:true,mode:'AI',won:true,at:Date.now()}]))
    window.__orientationLocks=[]; screen.orientation.lock=async value=>{window.__orientationLocks.push(value)}
  },detail)
  const phone=await mobile.newPage(); phone.on('pageerror',e=>errors.push(e.message))
  await openRecord(phone); await phone.getByRole('button',{name:'对局回放',exact:true}).click()
  await phone.waitForFunction(()=>Boolean(document.fullscreenElement))
  assert((await phone.evaluate(()=>window.__orientationLocks)).includes('landscape'))
  if (await phone.evaluate(()=>innerWidth<innerHeight)) await phone.getByRole('dialog',{name:'横屏全屏限制'}).waitFor()
  await phone.setViewportSize({width:844,height:390})
  await phone.waitForFunction(()=>!document.querySelector('.device-gate'))
  await phone.waitForFunction(()=>document.querySelectorAll('.replay-boards canvas[data-renderer]').length===2)
  await phone.waitForFunction(()=>!document.querySelector('.replay-controls button[aria-label="下一次攻击"]')?.disabled)
  await phone.waitForTimeout(700)
  const box=await phone.locator('.naval-replay').boundingBox()
  assert(box.y>=0&&box.y+box.height<=390,'fullscreen replay fits the landscape viewport')
  await phone.screenshot({path:output+'/naval-replay-auto-fullscreen.png'})
  await phone.getByRole('button',{name:'聊天大厅',exact:true}).click()
  await phone.locator('.global-chat').waitFor()
  await phone.getByRole('button',{name:'关闭社区面板',exact:true}).click()
  await phone.getByRole('button',{name:'退出回放',exact:true}).click()
  await phone.waitForFunction(()=>!document.fullscreenElement)
  await mobile.close()
  assert.deepEqual(errors,[])
  console.log('PASS: local result sharing, public battle cards, mirrored torpedo/submarine/rebound replay, mobile fullscreen and orientation request, lobby access and exit cleanup')
} finally { await browser.close() }
