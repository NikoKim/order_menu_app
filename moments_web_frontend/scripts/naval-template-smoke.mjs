import assert from 'node:assert/strict'
import {chromium} from 'playwright-core'
const browser=await chromium.launch({executablePath:'C:/Program Files/Google/Chrome/Application/chrome.exe',headless:true})
try{
 const p=await browser.newPage();let requests=0
 await p.route('**/api/entertainment/naval/military-template',route=>{requests++;return route.fulfill({json:{code:0,data:{ships:[{id:'template-0',kind:'cruiser',x:3,y:3,w:1,h:3,rotation:0,hits:[],lostPlanes:[],minesUsed:0}],actions:[{shipId:'template-0',weapon:'shot',plane:0,x:0,y:0}]}}})})
 await p.goto('http://127.0.0.1:5173/customer/entertainment/naval');await p.getByRole('button',{name:'军事模式',exact:true}).click();await p.getByRole('button',{name:'开始对战',exact:true}).click()
 await p.waitForFunction(()=>JSON.parse(localStorage.getItem('naval-guest-session'))?.aiTemplate?.length===1)
 const s=await p.evaluate(()=>JSON.parse(localStorage.getItem('naval-guest-session')));assert.equal(requests,1);assert.equal(s.players[1].ships.length,1);assert.equal(s.players[1].ships[0].id,'template-0');assert.equal(s.players[1].coins,270);assert.equal(s.aiTemplate[0].weapon,'shot');console.log('Guest AI loads the server winner template, keeps its fleet and attack flow, and records unspent coins.')
}finally{await browser.close()}
