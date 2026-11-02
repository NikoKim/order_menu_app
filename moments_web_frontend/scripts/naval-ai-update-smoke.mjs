import assert from 'node:assert/strict'
import {mkdir} from 'node:fs/promises'
import {chromium} from 'playwright-core'
import {createLocalGame} from '../src/games/navalEngine.js'
import {createMilitaryGame,MILITARY_SHIPS as M} from '../src/games/navalMilitary.js'
const base=process.env.QA_BASE_URL||'http://127.0.0.1:5173',out='../.agents/naval-ai-update';await mkdir(out,{recursive:true})
const browser=await chromium.launch({executablePath:'C:/Program Files/Google/Chrome/Application/chrome.exe',headless:true}),errors=[]
const ship=(kind,id,x,y,r=0)=>({kind,id,x,y,w:r%180?M[kind].h:1,h:r%180?1:M[kind].h,rotation:r,hits:[],lostPlanes:[],minesUsed:0})
async function idle(p){await p.waitForFunction(()=>!document.querySelector('.launch-overlay,.phase-announcement')&&[...document.querySelectorAll('.naval-board canvas')].every(c=>c.dataset.animating!=='true'))}
async function cell(p,side,x,y){const c=p.locator(`.${side}-panel canvas`),b=await c.boundingBox();await c.click({position:{x:(48+(side==='own'?11-x:x)*48)/624*b.width,y:(48+y*48)/624*b.height}})}
try{
 for(const military of [false,true]){
  const p=await browser.newPage({viewport:{width:1440,height:950}});p.on('pageerror',e=>errors.push(e.message));let s=military?createMilitaryGame():createLocalGame();s.phase='BATTLE';s.battleStarted=true;s.deadline=Date.now()+300000;s.players[0].ready=true;s.players[0].stage='READY';s.players[1].ready=true;s.players[1].stage='READY';s.spectators=[{id:'observer',name:'观战测试'}]
  if(military){s.players[0].ships=[ship('battleship','b',0,0,90),ship('cruiser','c',4,0),ship('mineLayer','m',7,0)];s.players[1].ships=[ship('cruiser','e',9,9)];s.players[1].shots=[{x:3,y:5,result:'hit'}]}
  await p.addInitScript(r=>localStorage.setItem('naval-guest-session',JSON.stringify(r)),s);await p.goto(base+'/customer/entertainment/naval');await p.getByRole('button',{name:'继续对局',exact:true}).click();await idle(p)
  await p.getByRole('button',{name:'展开观众席',exact:true}).click();await p.locator('[data-mobile-panel="audience"]').waitFor();await p.waitForTimeout(350);await p.screenshot({path:`${out}/audience-debug.png`});const panelBox=await p.locator('[data-mobile-panel="audience"]').boundingBox(),boardBox=await p.locator('.boards').boundingBox();assert(panelBox.x>=boardBox.x+boardBox.width,'audience is beside the boards, not floating over them');assert.equal(await p.locator('.mobile-panel-veil').count(),0);assert(await p.locator('[data-mobile-panel="audience"] .naval-audience').isVisible());await p.screenshot({path:`${out}/${military?'military':'classic'}-pc-audience.png`});await p.locator('[data-mobile-panel="audience"]').getByRole('button',{name:'收起面板',exact:true}).click();await p.locator('[data-mobile-panel="audience"]').waitFor({state:'detached'})
  if(military){await p.locator('[data-tutorial="naval-weapon-shot-battleship"]').click();const c=p.locator('.enemy-panel canvas'),b=await c.boundingBox();await c.hover({position:{x:(48+2*48)/624*b.width,y:(48+5*48)/624*b.height}});await p.waitForTimeout(200);await p.screenshot({path:`${out}/salvo-probability.png`});await p.locator('[data-tutorial="naval-weapon-mine-mineLayer"]').click();await cell(p,'own',8,8);await cell(p,'own',9,8);assert.match(await p.locator('.own-panel .submarine-count').innerText(),/2\/2/);assert.equal(await p.locator('.enemy-panel .target-confirmation').count(),0);await p.getByRole('button',{name:'确认使用',exact:true}).click();await p.waitForFunction(()=>JSON.parse(localStorage.getItem('naval-guest-session')).players[0].mines.length===2)}
  await p.close()
 }
 assert.deepEqual(errors,[]);console.log('PC audience panels in both rules, military salvo preview, two-cell mine selection and atomic placement passed.')
}finally{await browser.close()}
