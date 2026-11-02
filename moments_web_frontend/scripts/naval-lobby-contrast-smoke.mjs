import assert from 'node:assert/strict'
import { mkdir } from 'node:fs/promises'
import { chromium } from 'playwright-core'

const base=process.env.QA_BASE_URL||'http://127.0.0.1:5173',out='../.agents/naval-lobby-contrast'
await mkdir(out,{recursive:true})
const browser=await chromium.launch({executablePath:'C:/Program Files/Google/Chrome/Application/chrome.exe',headless:true})
const luminance=rgb=>rgb.map(v=>{v/=255;return v<=.04045?v/12.92:((v+.055)/1.055)**2.4}).reduce((n,v,i)=>n+v*[.2126,.7152,.0722][i],0)
const contrast=(a,b)=>(Math.max(luminance(a),luminance(b))+.05)/(Math.min(luminance(a),luminance(b))+.05)
const errors=[]
try{
  for(const size of [{width:844,height:390},{width:667,height:375}]){
    const page=await browser.newPage({viewport:size,isMobile:true,hasTouch:true});page.on('pageerror',e=>errors.push(e.message))
    await page.goto(base+'/customer/entertainment/naval')
    for(const mode of ['经典模式','军事模式']){
      await page.getByRole('button',{name:mode,exact:true}).click()
      await page.locator('.lobby-sea img').evaluate(img=>img.decode())
      const styles=await page.evaluate(()=>{
        const pick=selector=>{const e=document.querySelector(selector),s=getComputedStyle(e);return {color:s.color,bg:s.backgroundColor}}
        return {icon:pick('.header-actions .community-actions button'),header:pick('.naval-header'),tabs:pick('.naval-rule-switch'),heading:pick('.lobby-heading'),copy:pick('.guest-storage'),selected:pick('.naval-rule-switch [aria-pressed=true]'),backdrop:getComputedStyle(document.querySelector('.naval-page'),'::before').backgroundImage}
      })
      const rgb=color=>color.match(/[\d.]+/g).slice(0,3).map(Number)
      // One continuous image glaze, rather than independent white strips, protects the entire surface.
      const glazes=[...styles.backdrop.matchAll(/rgba\(([^)]+)\)/g)].map(m=>m[1].split(',').map(Number))
      assert(glazes.length>=2)
      const worst=glazes.sort((a,b)=>a[3]-b[3])[0].slice(0,3).map(v=>v*Math.min(...glazes.map(c=>c[3])))
      for(const foreground of ['icon','copy','selected']){
        assert(contrast(rgb(styles[foreground].color),worst)>=4.5,`${size.width} ${mode} ${foreground} contrast`)
      }
      for(const area of ['header','tabs','heading'])assert.equal(styles[area].bg,'rgba(0, 0, 0, 0)','no separate opaque section strips')
      assert(await page.locator('.naval-title').isVisible(),'title anchors the toolbar instead of leaving an empty strip')
      const switchBox=await page.locator('.naval-rule-switch').boundingBox(),tutorialBox=await page.locator('.naval-tutorial-entry').boundingBox()
      assert(Math.abs(switchBox.y-tutorialBox.y)<5,'mode and tutorial share one row')
      const bounds=await page.locator('.header-actions button').evaluateAll(es=>es.map(e=>{const r=e.getBoundingClientRect();return {left:r.left,right:r.right,top:r.top,bottom:r.bottom}}))
      assert(bounds.every(b=>b.left>=0&&b.right<=size.width&&b.top>=0&&b.bottom<=size.height),'all header controls stay visible')
      const commands=await page.locator('.mode-entry>button').evaluateAll(es=>es.map(e=>{const r=e.getBoundingClientRect();return {bottom:r.bottom,height:r.height}}))
      assert(commands.every(b=>b.bottom<=size.height&&b.height>=30),'all launch commands fit without clipping')
      assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth+1),false)
      await page.screenshot({path:`${out}/${size.width}-${mode==='军事模式'?'military':'classic'}.png`})
    }
    await page.getByRole('button',{name:'全屏',exact:true}).click()
    await page.waitForFunction(()=>Boolean(document.fullscreenElement))
    const root=await page.locator('.naval-page').boundingBox()
    assert(Math.abs(root.height-size.height)<2,'native fullscreen lobby fills its viewport')
    await page.screenshot({path:`${out}/${size.width}-fullscreen.png`})
    await page.close()
  }
  for(const loggedIn of [false,true]){
    let desktopCopy
    for(const mobile of [false,true]){
      const page=await browser.newPage({viewport:mobile?{width:667,height:375}:{width:1440,height:950},isMobile:mobile,hasTouch:mobile});page.on('pageerror',e=>errors.push(e.message))
      if(loggedIn){
        await page.addInitScript(()=>{localStorage.setItem('token','qa-display-only');localStorage.setItem('user',JSON.stringify({id:1,nickname:'测试指挥官',role:'CUSTOMER'}))})
        await page.route(url=>url.pathname.startsWith('/api/'),route=>{const url=route.request().url();return route.fulfill({json:{code:0,data:url.includes('/profile')?{formations:[],history:[]}:url.includes('/messages')?[]:{id:1,nickname:'测试指挥官',role:'CUSTOMER'}}})})
      }
      await page.goto(base+'/customer/entertainment/naval')
      const profile=page.locator('.lobby-profile');await profile.waitFor()
      const copy=(await profile.innerText()).replace(/\s+/g,' ').trim()
      if(!mobile)desktopCopy=copy
      else{
        assert.equal(copy,desktopCopy,'mobile preserves the complete desktop copy for both account states')
        if(loggedIn){assert(copy.includes('账号档案 · 每局 300 金币'));assert(await profile.locator('.naval-avatar').isVisible());assert.equal(await profile.locator('.guest-storage').count(),0)}
        else{assert(copy.includes('选择出航方式'));assert(copy.includes('当前对局会临时同步用于观战'));assert(await profile.getByRole('link',{name:'登录',exact:true}).isVisible());assert(await profile.getByRole('link',{name:'注册',exact:true}).isVisible());assert(await profile.getByRole('button',{name:'查看排行榜',exact:true}).isVisible())}
        const commands=await page.locator('.mode-entry>button').evaluateAll(es=>es.map(e=>e.getBoundingClientRect().bottom))
        assert(commands.every(bottom=>bottom<=375),'complete account hint does not clip launch controls')
        await page.screenshot({path:`${out}/667-${loggedIn?'account':'guest'}-hint.png`})
      }
      await page.close()
    }
  }
  assert.deepEqual(errors,[])
  console.log('Both mobile sizes and rule modes: toolbar/text AA contrast, visible header controls and no horizontal overflow passed.')
}finally{await browser.close()}
