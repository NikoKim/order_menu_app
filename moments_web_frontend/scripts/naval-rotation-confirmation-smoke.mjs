import assert from 'node:assert/strict'
import { mkdir } from 'node:fs/promises'
import path from 'node:path'
import { chromium } from 'playwright-core'
import { createLocalGame, FLEET, legalPlacement, validateFleet } from '../src/games/navalEngine.js'

const base = process.env.QA_BASE_URL || 'http://127.0.0.1:5174'
const output = path.resolve('../.agents/naval-qa')
const browser = await chromium.launch({ executablePath: process.env.QA_BROWSER || 'C:/Program Files/Google/Chrome/Application/chrome.exe', headless: true })
const errors = [], checks = []
await mkdir(output, { recursive: true })
const read = page => page.evaluate(() => JSON.parse(localStorage.getItem('naval-guest-session')))
const hook = (page, name) => page.locator(`[data-tutorial="${name}"]:visible`).first()
function fixture(phase = 'PLACEMENT') {
  const room = createLocalGame()
  const positions = [[11, 11], [11, 0], [0, 11], [5, 5], [0, 0], [3, 0], [6, 0], [0, 4], [3, 4], [7, 7]]
  room.players[0].ships = FLEET.map((ship, i) => ({ ...ship, x: positions[i][0], y: positions[i][1], hits: [] }))
  assert.equal(validateFleet(room.players[0].ships), '')
  room.players[1].mines = []; room.players[1].aa = []
  if (phase === 'BATTLE') {
    room.phase = phase; room.turn = 0
    room.players[0].stage = 'READY'; room.players[0].ready = true
    room.players[0].inventory.radar = 2
  }
  return room
}
async function idle(page) {
  await page.waitForFunction(() => !document.querySelector('.launch-overlay,.stage-scene-leave-active,.phase-announcement,.mobile-sheet-leave-active') && document.querySelector('.own-panel canvas')?.dataset.renderer)
  await page.waitForTimeout(400)
}
async function start({ mobile = false, ios = false, rejectFullscreen = false, phase = 'PLACEMENT', width = 844, height = 390 } = {}) {
  const context = await browser.newContext({ viewport: mobile ? { width, height } : { width: 1440, height: 1000 }, isMobile: mobile, hasTouch: mobile,
    ...(ios ? { userAgent: 'Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X) AppleWebKit/605.1.15 Version/18.0 Mobile/15E148 Safari/604.1' } : {}) })
  await context.addInitScript(({ room, ios, rejectFullscreen }) => {
    room.deadline = Date.now() + 300000
    localStorage.setItem('naval-guest-session', JSON.stringify(room)); localStorage.setItem('naval-guest-resume-kind', 'LOCAL')
    if (rejectFullscreen) {
      Object.defineProperty(document, 'fullscreenEnabled', { configurable: true, get: () => true })
      HTMLElement.prototype.requestFullscreen = () => Promise.reject(new DOMException('Element fullscreen is unavailable', 'NotSupportedError'))
    } else if (ios) {
      for (const name of ['fullscreenEnabled', 'webkitFullscreenEnabled']) Object.defineProperty(document, name, { configurable: true, get: () => false })
    }
  }, { room: fixture(phase), ios, rejectFullscreen })
  const page = await context.newPage(); page.setDefaultTimeout(15000)
  page.mobileQA = mobile
  page.on('pageerror', error => errors.push(error.message))
  await page.goto(base + '/customer/entertainment/naval')
  await page.locator('.ent-navigation').waitFor()
  assert.equal(await page.locator('.ent-navigation').count(), 1, 'shared entertainment tabs are present')
  await page.getByRole('button', { name: '继续对局', exact: true }).click()
  if (mobile && !ios) {
    const gate = page.getByRole('button', { name: '全屏并锁定横屏', exact: true })
    if (await gate.isVisible()) await gate.click()
  }
  await idle(page)
  return { context, page }
}
async function tap(page, side, point) {
  await page.locator('.mobile-panel-veil').waitFor({ state: 'hidden' })
  await page.locator('.mobile-sheet-leave-active').waitFor({ state: 'hidden' })
  const box = await page.locator(`.${side}-panel canvas`).boundingBox()
  const x = box.x + (48 + point.x * 48) / 624 * box.width, y = box.y + (48 + point.y * 48) / 624 * box.height
  if (page.mobileQA) await page.touchscreen.tap(x, y)
  else await page.mouse.click(x, y)
  await page.waitForTimeout(120)
}
async function rendering(page, expected) {
  const result = await page.locator('.own-panel canvas').evaluate(canvas => {
    const pixels = canvas.getContext('2d').getImageData(0, 0, canvas.width, canvas.height).data, colors = new Set()
    for (let i = 0; i < pixels.length; i += 64) colors.add(`${pixels[i]},${pixels[i + 1]},${pixels[i + 2]}`)
    return { count: +canvas.dataset.shipCount, sprites: +canvas.dataset.liveShipSprites, ships: JSON.parse(canvas.dataset.shipFootprints), colors: colors.size }
  })
  assert.equal(result.count, expected); assert.equal(result.sprites, expected, 'exactly one live Phaser sprite per ship')
  assert.equal(new Set(result.ships.map(ship => ship.id)).size, expected)
  assert(result.colors > (expected ? 10 : 3), 'actual canvas pixels include rendered sea and raster assets')
  if (expected === 10) assert.equal(result.ships.reduce((sum, ship) => sum + ship.w * ship.h, 0), 20, 'fleet occupies exactly twenty cells')
  return result
}
try {
  const desktop = await start()
  const pc = desktop.page
  const before = (await read(pc)).players[0].ships.find(ship => ship.id === 'destroyer-1')
  await pc.locator('.fleet-command').filter({ has: pc.locator('[aria-label="旋转驱逐舰"]') }).first().locator('button').first().click()
  await pc.keyboard.press('r')
  await pc.waitForTimeout(160)
  let rotated = (await read(pc)).players[0].ships.find(ship => ship.id === before.id)
  assert.deepEqual([rotated.w, rotated.h], [before.h, before.w], 'R rotates the selected ship')
  await pc.getByRole('button', { name: '旋转驱逐舰', exact: true }).first().click()
  rotated = (await read(pc)).players[0].ships.find(ship => ship.id === before.id)
  assert.deepEqual([rotated.w, rotated.h], [before.w, before.h], 'individual roster rotation works')
  for (let i = 0; i < 8; i++) { await pc.keyboard.press('r'); await tap(pc, 'own', { x: 0, y: 0 }); await pc.waitForTimeout(80); await rendering(pc, 10) }
  assert.equal(validateFleet((await read(pc)).players[0].ships), '')
  await pc.screenshot({ path: path.join(output, 'rotation-desktop.png') })
  await desktop.context.close(); checks.push('desktop R and roster rotation; repeated moves leave exactly ten sprites and twenty occupied cells')

  const phone = await start({ mobile: true, width: 568, height: 320 })
  const page = phone.page
  await page.getByRole('button', { name: '旋转驱逐舰', exact: true }).first().click()
  await page.waitForTimeout(120)
  const controlBounds = await page.locator('.model-rotate-controls').boundingBox(), boardBounds = await page.locator('.own-panel canvas').boundingBox()
  const selected = (await read(page)).players[0].ships.find(ship => ship.id === 'destroyer-1')
  const assetTop = boardBounds.y+(24+selected.y*48)/624*boardBounds.height, assetBottom = assetTop+selected.h*48/624*boardBounds.height
  assert(controlBounds.y+controlBounds.height <= assetTop || controlBounds.y >= assetBottom, 'edge rotation must not cover its ship')
  assert(controlBounds.x >= boardBounds.x && controlBounds.x+controlBounds.width <= boardBounds.x+boardBounds.width && controlBounds.y >= boardBounds.y && controlBounds.y+controlBounds.height <= boardBounds.y+boardBounds.height, 'edge rotation must remain inside its board')
  await page.locator('.model-rotate').click()
  const original = await read(page), ship = original.players[0].ships.find(ship => ship.id === 'destroyer-1')
  const destination = Array.from({ length: 144 }, (_, i) => ({ x: i % 12, y: Math.floor(i / 12) })).find(point => (point.x !== ship.x || point.y !== ship.y) && legalPlacement(original.players[0].ships, { ...ship, ...point }))
  await tap(page, 'own', destination)
  await page.locator('.target-confirmation').waitFor()
  assert.deepEqual((await read(page)).players[0].ships, original.players[0].ships, 'preview does not deploy')
  await page.getByRole('button', { name: '取消预览', exact: true }).click()
  assert.deepEqual((await read(page)).players[0].ships, original.players[0].ships, 'cancel preserves all original placements')
  await tap(page, 'own', destination)
  await page.screenshot({ path: path.join(output, 'placement-mobile-confirm.png') })
  await page.getByRole('button', { name: '确认使用', exact: true }).click()
  assert((await read(page)).players[0].ships.some(s => s.id === ship.id && s.x === destination.x && s.y === destination.y))
  await rendering(page, 10)
  await hook(page, 'naval-mobile-tools-toggle').click()
  await hook(page, 'naval-clear-layout').click(); await rendering(page, 0)
  await hook(page, 'naval-mobile-fleet-toggle').click()
  await page.getByRole('button', { name: '旋转驱逐舰', exact: true }).first().click()
  await page.locator('.prep-ship').filter({ has: page.locator('[aria-label="旋转驱逐舰"]') }).first().locator('button').first().click()
  await tap(page, 'own', { x: 2, y: 2 }); await page.getByRole('button', { name: '确认使用', exact: true }).click()
  const placed = (await read(page)).players[0].ships[0]
  assert.deepEqual([placed.w, placed.h], [2, 1], 'unplaced roster rotation is retained when selected again')
  await hook(page, 'naval-mobile-tools-toggle').click(); await hook(page, 'naval-random-layout').click()
  await rendering(page, 10)
  await hook(page, 'naval-layout-next').click(); await idle(page)
  await hook(page, 'naval-supply-ready').click(); await idle(page)
  assert.equal((await read(page)).phase, 'BATTLE', 'zero purchases including zero stealth can start a battle')
  await phone.context.close(); checks.push('mobile model and roster rotation; placement preview/confirm/cancel; clear and rotated reserve ship; no-purchase readiness')

  const iphone = await start({ mobile: true, ios: true, phase: 'BATTLE' })
  const safari = iphone.page
  assert.equal(await safari.locator('.device-gate').count(), 0, 'unsupported iPhone native fullscreen does not block immersive play')
  assert.equal(await safari.evaluate(() => Boolean(document.fullscreenElement)), false, 'fallback does not claim native fullscreen')
  const geometry = await safari.locator('.mobile-player-status').first().boundingBox()
  assert(geometry.y <= 5 && geometry.height <= 26, 'compact information directly above the board without a blank top band')
  await hook(safari, 'naval-mobile-weapons-toggle').click(); await hook(safari, 'naval-weapon-radar').click()
  await tap(safari, 'enemy', { x: 4, y: 4 })
  assert.equal((await read(safari)).players[0].inventory.radar, 2, 'preview consumes no items')
  await safari.getByRole('button', { name: '取消预览', exact: true }).click()
  assert.equal((await read(safari)).players[0].inventory.radar, 2, 'cancel consumes no items')
  await tap(safari, 'enemy', { x: 4, y: 4 })
  await safari.screenshot({ path: path.join(output, 'radar-mobile-confirm.png') })
  await safari.getByRole('button', { name: '确认使用', exact: true }).click()
  assert.equal((await read(safari)).players[0].inventory.radar, 1)
  await hook(safari, 'naval-mobile-weapons-toggle').click()
  assert(await hook(safari, 'naval-weapon-shot').evaluate(button => button.classList.contains('selected')), 'defaults to cannon even with an item remaining')
  await safari.waitForTimeout(400)
  const weapons = await safari.locator('[data-mobile-panel="weapons"]').boundingBox()
  assert(weapons.x >= 420 && weapons.x + weapons.width <= 844, `mobile attack popup is anchored on the right: ${JSON.stringify(weapons)}`)
  await safari.screenshot({ path: path.join(output, 'iphone-immersive-battle.png') })
  await iphone.context.close(); checks.push('emulated iPhone unsupported-fullscreen fallback; compact top bar; right-side attack popup; item confirm/cancel and cannon reset')
  const rejected = await start({ mobile: true, ios: true, rejectFullscreen: true, phase: 'BATTLE' })
  assert.equal(await rejected.page.locator('.device-gate').count(), 0, 'iPhone rejecting an exposed fullscreen API still enters landscape fallback')
  await rejected.page.setViewportSize({ width: 390, height: 844 })
  await rejected.page.locator('.device-gate').waitFor()
  await rejected.page.setViewportSize({ width: 844, height: 390 })
  await rejected.page.locator('.device-gate').waitFor({ state: 'hidden' })
  await rejected.context.close(); checks.push('iPhone exposed-but-rejected fullscreen API fallback; portrait remains blocked')
  assert.deepEqual(errors, [])
  console.log(JSON.stringify({ passed: true, checks, output }))
} finally { await browser.close() }
