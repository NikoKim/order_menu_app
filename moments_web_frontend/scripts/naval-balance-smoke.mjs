import assert from 'node:assert/strict'
import { chromium } from 'playwright-core'
import { mkdir } from 'node:fs/promises'
import { createLocalGame, localAction } from '../src/games/navalEngine.js'
const base=process.env.QA_BASE_URL || 'http://127.0.0.1:5175', output='../.agents/naval-qa'
await mkdir(output,{recursive:true})
const browser=await chromium.launch({executablePath:'C:/Program Files/Google/Chrome/Application/chrome.exe',headless:true})
const errors=[]
try {
  const page=await browser.newPage({viewport:{width:800,height:700}})
  page.on('pageerror',error=>errors.push(error.message))
  await page.goto(base)
  await page.evaluate(async()=>{
    const url='/src/views/entertainment/NavalBoard.vue',compiled=await(await fetch(url)).text()
    const vueUrl=compiled.match(/from\s+["']([^"']*\/vue\.js[^"']*)["']/)[1]
    const{createApp,h,reactive}=await import(vueUrl),{default:Board}=await import(url)
    document.querySelector('#app').__vue_app__?.unmount(); document.body.replaceChildren()
    const host=document.createElement('div');host.style.cssText='width:624px;height:624px;margin:20px';document.body.append(host)
    window.fixture=reactive({player:{ships:[],shots:[],mines:[],aa:[]},defenseSide:'left',events:[],onReady:()=>{window.boardReady=true}})
    window.boardApp=createApp({render:()=>h(Board,fixture)});boardApp.mount(host)
  })
  await page.waitForFunction(()=>window.boardReady)
  await page.waitForTimeout(700)
  await page.evaluate(()=>fixture.events.push({id:1,player:1,weapon:'torpedo',x:2,y:3,cells:Array.from({length:10},(_,i)=>({x:i+2,y:3,result:'miss'}))}))
  await page.waitForFunction(()=>Boolean(document.querySelector('canvas')?.dataset.torpedoX))
  const position=()=>page.locator('canvas').evaluate(canvas=>({plane:Number(canvas.dataset.aircraftX),torpedo:Number(canvas.dataset.torpedoX)}))
  const first=await position();await page.waitForTimeout(200);const second=await position()
  const plane=first.plane-second.plane,torpedo=first.torpedo-second.torpedo
  assert(plane>torpedo&&torpedo>0,'both move right to left, with torpedo slower than aircraft')
  assert(plane/torpedo>1.1&&plane/torpedo<1.3,'relative speed stays only slightly slower')
  assert(await page.locator('canvas').evaluate(canvas=>canvas.getContext('2d').getImageData(100,100,1,1).data[3]>0))
  await page.screenshot({path:output+'/balance-torpedo-speed.png'})
  await page.waitForFunction(()=>document.querySelector('canvas').dataset.animating==='false')
  const room=createLocalGame();room.phase='BATTLE';room.turn=0;room.players[0].inventory.submarine=1;room.players[1].ships=[];room.players[1].mines=[{x:1,y:5}];room.players[1].inventory.mine=1
  const attack=localAction(room,{type:'ATTACK',weapon:'submarine',targets:[{x:4,y:5},{x:5,y:5},{x:6,y:5}]}).events.findLast(event=>event.weapon==='submarine')
  assert.equal(attack.cells.length,2)
  await page.evaluate(event=>{fixture.player.shots=event.cells;fixture.events.push({...event,id:2})},attack)
  await page.waitForFunction(()=>document.querySelector('canvas').dataset.submarineDestroyed==='true' && document.querySelector('canvas').dataset.effectPhase==='mine-rise')
  assert.equal(await page.locator('canvas').getAttribute('data-visible-shots'),'2')
  await page.screenshot({path:output+'/balance-partial-submarine.png'})
  await page.waitForFunction(()=>document.querySelector('canvas').dataset.animating==='false')
  await page.evaluate(()=>fixture.events.push({id:3,player:1,weapon:'fighter',x:2,y:8,row:9,cells:[],text:'防空炮击落飞机'}))
  await page.waitForFunction(()=>document.querySelector('canvas').dataset.effectPhase==='aa-tracer')
  assert.equal(await page.locator('canvas').getAttribute('data-effect-row'),'9')
  await page.screenshot({path:output+'/balance-cross-row-intercept.png'})
  await page.evaluate(()=>boardApp.unmount())
  assert.deepEqual(errors,[])
  console.log('PASS: moving mirrored torpedo is slightly slower than plane, partial submarine explosion marks only two cells, cross-row anti-air animation renders')
} finally {await browser.close()}
