import assert from 'node:assert/strict'
import { chromium } from 'playwright-core'
import { mkdir } from 'node:fs/promises'
import { militaryTutorialStep } from '../src/games/navalMilitaryTutorial.js'
const base=process.env.QA_BASE_URL||'http://127.0.0.1:5173',output='../.agents/naval-military'
await mkdir(output,{recursive:true})
const browser=await chromium.launch({executablePath:'C:/Program Files/Google/Chrome/Application/chrome.exe',headless:true})
const errors=[]
async function cell(page,side,x,y){const c=page.locator(`.${side}-panel .naval-board`),b=await c.boundingBox();await c.click({position:{x:(48+(side==='own'?11-x:x)*48)/624*b.width,y:(48+y*48)/624*b.height}})}
async function idle(page){await page.waitForFunction(()=>!document.querySelector('.launch-overlay,.phase-announcement,.stage-scene-leave-active')&&[...document.querySelectorAll('.naval-board')].every(b=>!b.classList.contains('is-loading')));await page.waitForTimeout(450)}
async function geometry(page,name){await idle(page);assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth+1),false,'no overflow');const data=await page.locator('.naval-board canvas').evaluateAll(cs=>cs.map(c=>{const p=c.getContext('2d').getImageData(0,0,c.width,c.height).data,s=new Set();for(let i=0;i<p.length;i+=64)s.add(`${p[i]},${p[i+1]},${p[i+2]}`);return s.size}));assert(data.every(n=>n>6));await page.screenshot({path:`${output}/${name}.png`,fullPage:true})}
async function enter(page,tutorial=false){await page.goto(base+'/customer/entertainment/naval');await page.getByRole('button',{name:'军事模式',exact:true}).click();await page.getByRole('button',{name:tutorial?'开始教程':'开始对战',exact:true}).click();const full=page.getByRole('button',{name:'全屏并锁定横屏',exact:true});if(await full.isVisible())await full.click();await idle(page)}
try{
  const page=await browser.newPage({viewport:{width:1440,height:960}});page.on('pageerror',e=>errors.push(e.message))
  await enter(page)
  for(const name of ['航母（战斗机）','巡洋舰','雷达驱逐舰','防空驱逐舰']){for(let n=0;n<5&&!await page.getByRole('button',{name:`购买${name}`,exact:true}).count();n++)await page.getByRole('button',{name:'切换驱逐舰类型',exact:true}).click();await page.getByRole('button',{name:`购买${name}`,exact:true}).click()}
  await page.getByRole('button',{name:'旋转航母（战斗机）',exact:true}).click()
  await page.getByRole('button',{name:'随机阵型',exact:true}).click();await geometry(page,'shared-deployment-desktop')
  const saved=await page.evaluate(()=>JSON.parse(localStorage.getItem('naval-guest-session')))
  assert.equal(saved.ruleSet,'MILITARY');assert.equal(saved.players[0].ships[0].rotation,90);assert.equal(saved.players[0].coins,170)
  await page.getByRole('button',{name:'完成战备',exact:true}).click();await idle(page)
  await page.getByRole('button',{name:/雷达驱逐舰.*雷达/}).click();await cell(page,'enemy',0,0)
  await page.waitForFunction(()=>JSON.parse(localStorage.getItem('naval-guest-session')).turn===0);await page.waitForTimeout(4500)
  await page.getByRole('button',{name:/航母.*战斗机 2/}).click();await cell(page,'enemy',2,11);await page.waitForTimeout(1000);await geometry(page,'shared-flight-desktop')
  assert.equal(await page.locator('.military-page,.military-layout,.military-guide').count(),0,'no separate military UI')
  await page.reload();await page.getByRole('button',{name:'继续对局',exact:true}).click();await idle(page);await page.waitForFunction(()=>JSON.parse(localStorage.getItem('naval-guest-session')).turn===0);await page.waitForTimeout(4600);assert(await page.getByRole('button',{name:/巡洋舰.*主炮/}).isEnabled(),'resumed military battle is not blocked by classic presentation gate')
  const mobile=await browser.newPage({viewport:{width:844,height:390},isMobile:true,hasTouch:true});mobile.on('pageerror',e=>errors.push(e.message));await enter(mobile)
  assert(await mobile.evaluate(()=>Boolean(document.fullscreenElement)),'same native fullscreen flow')
  assert.equal(await mobile.locator('.naval-page.mobile-game.mobile-preparing').count(),1)
  await mobile.getByRole('button',{name:'购买航母（战斗机）',exact:true}).click();await mobile.getByRole('button',{name:'购买巡洋舰',exact:true}).click();await mobile.getByRole('button',{name:'舰队编成',exact:true}).click();await geometry(mobile,'shared-mobile-fleet')
  await mobile.getByRole('button',{name:'阵型工具',exact:true}).click();await mobile.getByRole('button',{name:'随机阵型',exact:true}).click();await mobile.getByRole('button',{name:'完成战备',exact:true}).click();await geometry(mobile,'shared-mobile-battle')
  const training=await browser.newPage({viewport:{width:844,height:390},isMobile:true,hasTouch:true});training.on('pageerror',e=>errors.push(e.message));await enter(training,true)
  await training.locator('.naval-tutorial-guide[data-device="mobile"]').waitFor();assert(await training.evaluate(()=>Boolean(document.fullscreenElement)))
  await training.waitForFunction(()=>document.querySelector('.naval-tutorial-highlight')?.dataset.moving==='false');await geometry(training,'shared-tutorial-mobile')
  for(let attempt=0;attempt<150;attempt++) {
    const session=await training.evaluate(()=>JSON.parse(localStorage.getItem('naval-military-tutorial-v6-mobile')))
    if(session.completed)break
    const step=militaryTutorialStep(session)
    console.log('Tutorial',step.id,step.target,JSON.stringify(step.cell))
    await training.waitForFunction(()=>document.querySelector('.naval-tutorial-highlight')?.dataset.moving==='false'&&![...document.querySelectorAll('.naval-board canvas')].some(c=>c.dataset.animating==='true'),null,{timeout:20000})
    if(step.mode!=='action') await training.getByRole('button',{name:step.continueLabel||'看懂了，继续',exact:true}).click()
    else if(['naval-enemy-board','naval-own-board'].includes(step.target)) {
      await training.waitForFunction(()=>JSON.parse(localStorage.getItem('naval-military-tutorial-v6-mobile')).room.turn===0)
      await training.waitForTimeout(1500);await training.screenshot({path:`${output}/tutorial-${step.id}.png`});await cell(training,step.target==='naval-own-board'?'own':'enemy',step.cell.x,step.cell.y)
      const confirm=training.getByRole('button',{name:'确认使用',exact:true});await training.waitForTimeout(350);if(await confirm.isVisible()){await training.waitForFunction(()=>document.querySelector('.naval-tutorial-highlight')?.dataset.moving==='false');await confirm.click()}
    } else {
      const target=training.locator(`[data-tutorial="${step.target}"]:visible`).first(), control=step.subTarget?target.locator(step.subTarget).first():target
      await control.click()
    }
    await training.waitForTimeout(400)
  }
  assert.equal(await training.evaluate(()=>JSON.parse(localStorage.getItem('naval-military-tutorial-v6-mobile')).completed),true,'complete shared fullscreen tutorial')
  const rank=await browser.newPage();const queries=[];await rank.route('**/api/entertainment/naval/leaderboard?**',route=>{queries.push(new URL(route.request().url()).searchParams.get('ruleSet'));return route.fulfill({json:{code:200,data:[]}})})
  await rank.goto(base+'/customer/entertainment/naval');await rank.getByRole('button',{name:'排行榜',exact:true}).click();await rank.getByRole('heading',{name:'经典模式排行榜',exact:true}).waitFor();await rank.getByRole('button',{name:'军事模式',exact:true}).click();await rank.getByRole('heading',{name:'军事模式排行榜',exact:true}).waitFor();await rank.waitForTimeout(400);assert.deepEqual(queries,['CLASSIC','MILITARY'])
  assert.deepEqual(errors,[]);console.log('Shared classic UI, military purchase/deploy/attack, mobile fullscreen, shared tutorial and rule-specific rankings passed.')
}finally{await browser.close()}
