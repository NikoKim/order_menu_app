import assert from 'node:assert/strict'
import { chromium } from 'playwright-core'
import { createLocalGame } from '../src/games/navalEngine.js'
import { createNavalTutorial } from '../src/games/navalTutorial.js'

const browser = await chromium.launch({ executablePath: 'C:/Program Files/Google/Chrome/Application/chrome.exe', headless: true })
const errors = []
const base = process.env.QA_BASE_URL || 'http://127.0.0.1:5174'
const read = page => page.evaluate(() => JSON.parse(localStorage.getItem('naval-guest-session')).players[0])
async function clickCell(page, x, y) {
  const b = await page.locator('.own-panel canvas').boundingBox()
  const point = { x: b.x+(48+(11-x)*48)/624*b.width, y: b.y+(48+y*48)/624*b.height }
  if (await page.evaluate(() => navigator.maxTouchPoints > 0)) await page.touchscreen.tap(point.x, point.y)
  else await page.mouse.click(point.x, point.y)
}
async function confirm(page) { const button = page.getByRole('button', { name: '确认使用', exact: true }); if (await button.count()) await button.click() }
async function controlsClear(page, point, shape = { w: 1, h: 1 }, selector = '.target-confirmation') {
  const board = await page.locator('.own-panel canvas').boundingBox(), controls = await page.locator(selector).boundingBox()
  const top = board.y+(24+point.y*48)/624*board.height, bottom = top+shape.h*48/624*board.height
  assert(controls.x >= board.x && controls.x+controls.width <= board.x+board.width && controls.y >= board.y && controls.y+controls.height <= board.y+board.height, 'controls must fit inside the board')
  assert(controls.y+controls.height <= top || controls.y >= bottom, 'controls must not cover the asset footprint')
}
try {
  for (const mobile of [true, false]) {
    const context = await browser.newContext({ viewport: mobile ? { width: 568, height: 320 } : { width: 1366, height: 900 }, hasTouch: mobile, isMobile: mobile })
    const room = createLocalGame(); room.phase = 'SUPPLY'; room.players[0].stage = 'SUPPLY'
    room.players[0].ships = createNavalTutorial().room.players[0].ships
    room.players[0].ships[0].stealth = true
    room.players[0].mines = [{ x: 0, y: 0 }]; room.players[0].aa = [7]
    Object.assign(room.players[0].inventory, { mine: 1, antiAir: 1, stealth: 1 })
    await context.addInitScript(room => { room.deadline = Date.now()+180000; localStorage.setItem('naval-guest-session', JSON.stringify(room)); localStorage.setItem('naval-guest-resume-kind', 'LOCAL') }, room)
    const page = await context.newPage(); page.on('pageerror', error => errors.push(error.message))
    await page.goto(base+'/customer/entertainment/naval'); await page.getByRole('button', { name: '继续对局', exact: true }).click()
    await page.waitForFunction(() => document.querySelector('.own-panel canvas')?.dataset.renderer && !document.querySelector('.launch-overlay,.phase-announcement'))
    await page.waitForTimeout(350)
    await clickCell(page, 0, 0)
    assert.deepEqual((await read(page)).mines, [{ x: 0, y: 0 }], 'selecting deployed mine must not delete it')
    await clickCell(page, 11, 0)
    if (mobile) {
      await controlsClear(page, { x: 11, y: 0 }); await page.getByRole('button', { name: '取消预览', exact: true }).click()
      assert.deepEqual((await read(page)).mines, [{ x: 0, y: 0 }], 'cancel must keep source deployment')
      await clickCell(page, 0, 0); await clickCell(page, 11, 0)
    }
    await confirm(page); assert.deepEqual((await read(page)).mines, [{ x: 11, y: 0 }])
    // Select each tool through its existing component before moving another defense.
    if (mobile) await page.getByRole('button', { name: '部署防空炮', exact: true }).click()
    else await page.locator('.supply-name').filter({ hasText: '防空炮' }).click()
    await clickCell(page, 0, 7); assert.deepEqual((await read(page)).aa, [7])
    await clickCell(page, 0, 10); await confirm(page); assert.deepEqual((await read(page)).aa, [10])
    if (mobile) await page.getByRole('button', { name: '部署隐身图层', exact: true }).click()
    else await page.locator('.supply-name').filter({ hasText: '隐身' }).click()
    const [source, destination] = (await read(page)).ships
    await clickCell(page, source.x, source.y); assert((await read(page)).ships[0].stealth)
    await clickCell(page, destination.x, destination.y); await confirm(page)
    const coated = (await read(page)).ships.filter(ship => ship.stealth)
    assert.equal(coated.length, 1); assert.equal(coated[0].id, destination.id)
    if (mobile) {
      await page.getByRole('button', { name: '部署水雷', exact: true }).click()
      const b = await page.locator('.own-panel canvas').boundingBox(), pos = q => ({ x: b.x+(48+(11-q.x)*48)/624*b.width, y: b.y+(48+q.y*48)/624*b.height })
      const from = pos({ x: 11, y: 0 }), to = pos({ x: 0, y: 11 }), cdp = await context.newCDPSession(page)
      await cdp.send('Input.dispatchTouchEvent', { type: 'touchStart', touchPoints: [from] })
      for (let i = 1; i <= 12; i++) {
        await cdp.send('Input.dispatchTouchEvent', { type: 'touchMove', touchPoints: [{ x: from.x+(to.x-from.x)*i/12, y: from.y+(to.y-from.y)*i/12 }] })
        await page.waitForTimeout(35)
        if (i === 6) { assert.equal(await page.locator('.own-panel canvas').getAttribute('data-preview-sprite'), 'mine'); await page.screenshot({ path: '../.agents/naval-qa/defense-drag-follow.png' }) }
      }
      await cdp.send('Input.dispatchTouchEvent', { type: 'touchEnd', touchPoints: [] })
      await controlsClear(page, { x: 0, y: 11 }); await confirm(page)
      assert.deepEqual((await read(page)).mines, [{ x: 0, y: 11 }])
      await page.getByRole('button', { name: '对局菜单', exact: true }).click(); await page.getByRole('button', { name: '离开对局', exact: true }).click(); await page.waitForTimeout(350)
      const dialog = await page.locator('.naval-dialog').boundingBox()
      assert(Math.abs(dialog.x+dialog.width/2-284)<2, 'landscape leave dialog must be centered')
      await page.screenshot({ path: '../.agents/naval-qa/defense-leave-centered.png' })
    }
    await context.close()
  }
  assert.deepEqual(errors, [])
  console.log(JSON.stringify({ passed: true, checks: ['desktop and mobile defense relocation without removal', 'coating transfer preserves purchase count', 'corner confirmation avoids footprint', 'live drag bitmap', 'centered leave dialog'] }))
} finally { await browser.close() }
