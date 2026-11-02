import assert from 'node:assert/strict'
import {mkdir} from 'node:fs/promises'
import {chromium} from 'playwright-core'
import {createMilitaryGame,MILITARY_SHIPS as M} from '../src/games/navalMilitary.js'
const base=process.env.QA_BASE_URL||'http://127.0.0.1:5173',out='../.agents/naval-ai-safety';await mkdir(out,{recursive:true})
const browser=await chromium.launch({executablePath:'C:/Program Files/Google/Chrome/Application/chrome.exe',headless:true}),errors=[]
const ship=(kind,id,x,y,r=0)=>({kind,id,x,y,w:r%180?M[kind].h:1,h:r%180?1:M[kind].h,rotation:r,hits:[],lostPlanes:[],minesUsed:0})
async function idle(p){await p.waitForFunction(()=>!document.querySelector('.launch-overlay,.phase-announcement')&&[...document.querySelectorAll('.naval-board canvas')].every(c=>c.dataset.animating!=='true'))}
async function full(p){const b=p.getByRole('button',{name:'全屏并锁定横屏',exact:true});if(await b.isVisible())await b.click({timeout:1500}).catch(async e=>{if(!await p.evaluate(()=>!!document.fullscreenElement))throw e})}
try{
 const purchase=await browser.newPage({viewport:{width:1440,height:950}});purchase.on('pageerror',e=>errors.push(e.message));await purchase.goto(base+'/customer/entertainment/naval');await purchase.getByRole('button',{name:'军事模式',exact:true}).click();await purchase.getByRole('button',{name:'开始对战',exact:true}).click();await idle(purchase)
 const carrier=purchase.getByRole('button',{name:'购买航母（战斗机）',exact:true});await carrier.click();await carrier.click();assert(await carrier.isDisabled())
 const aa=purchase.getByRole('button',{name:'购买防空驱逐舰',exact:true});for(let i=0;i<6;i++)await aa.click();assert(await aa.isDisabled());assert.equal(await purchase.evaluate(()=>JSON.parse(localStorage.getItem('naval-guest-session')).players[0].ships.length),8)
 for(const [name,viewport,mobile] of [['desktop',{width:1440,height:950},false],['phone',{width:844,height:390},true],['small-phone',{width:667,height:375},true]]){
  const p=await browser.newPage({viewport,isMobile:mobile,hasTouch:mobile});p.on('pageerror',e=>errors.push(e.message));const s=createMilitaryGame();s.phase='BATTLE';s.battleStarted=true;s.deadline=Date.now()+300000;s.players[0].ships=[ship('battleship','horizontal',0,0,90),ship('battleship','vertical',5,0),ship('carrier','c',8,0)];s.players[0].ready=true;s.players[0].stage='READY';s.players[1].ships=[ship('cruiser','e',9,9)]
  await p.addInitScript(r=>localStorage.setItem('naval-guest-session',JSON.stringify(r)),s);await p.goto(base+'/customer/entertainment/naval');await p.getByRole('button',{name:'继续对局',exact:true}).click();if(mobile)await full(p);await idle(p);if(mobile)await p.getByRole('button',{name:'展开攻击方式',exact:true}).click()
  const salvo=p.locator('.salvo-weapon');assert.equal(await salvo.count(),2);assert.match(await salvo.nth(0).innerText(),/1×3/);assert.match(await salvo.nth(1).innerText(),/3×1/)
  for(const b of await salvo.all())assert(await b.evaluate(el=>{const s=el.querySelector('span'),a=el.getBoundingClientRect(),r=s.getBoundingClientRect();return s.scrollWidth<=s.clientWidth+1&&r.right<=a.right+1&&r.bottom<=a.bottom+1}),'salvo label fits its button')
  assert(await p.locator('.naval-board canvas').first().evaluate(c=>{const ctx=c.getContext('2d');if(!ctx)return c.width>100&&c.height>100;const data=ctx.getImageData(0,0,c.width,c.height).data;return data.some((n,i)=>i%4===3&&n>0)}))
  await p.waitForTimeout(350);await p.screenshot({path:`${out}/${name}-salvo.png`});await p.close()
 }
 assert.deepEqual(errors,[]);console.log('Expanded shared hull caps and 1x3/3x1 salvo labels fit desktop and both phone sizes.')
}finally{await browser.close()}
