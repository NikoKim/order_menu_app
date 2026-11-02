import assert from 'node:assert/strict'
import { mkdir } from 'node:fs/promises'
import path from 'node:path'
import { chromium } from 'playwright-core'
import { createLocalGame, localBattleResult, shipCells } from '../src/games/navalEngine.js'

const base = process.env.QA_BASE_URL || 'http://127.0.0.1:5174'
const output = path.resolve('../.agents/naval-qa')
await mkdir(output, { recursive: true })
const browser = await chromium.launch({ executablePath: 'C:/Program Files/Google/Chrome/Application/chrome.exe', headless: true })
const errors = []
function fixture() {
  const room = createLocalGame()
  room.phase = 'BATTLE'; room.turn = 0; room.battleStarted = true
  room.players.forEach(player => { player.stage = 'READY'; player.ready = true; player.aa = []; player.mines = [] })
  return room
}
async function cell(page, x, y) {
  const box = await page.locator('.enemy-panel canvas').boundingBox()
  await page.mouse.click(box.x + (48 + 48 * x) / 624 * box.width, box.y + (48 + 48 * y) / 624 * box.height)
}
async function seeded(room) {
  const context = await browser.newContext({ viewport: { width: 1366, height: 900 } })
  await context.addInitScript(room => { room.deadline = Date.now() + 30000; localStorage.setItem('naval-guest-session', JSON.stringify(room)); localStorage.setItem('naval-guest-resume-kind', 'LOCAL') }, room)
  const page = await context.newPage(); page.on('pageerror', e => errors.push(e.message))
  await page.goto(base + '/customer/entertainment/naval')
  await page.getByRole('button', { name: '继续对局', exact: true }).click()
  await page.waitForFunction(() => document.querySelector('.enemy-panel .naval-board.enabled') && !document.querySelector('.launch-overlay,.phase-announcement'))
  return { context, page }
}
try {
  const room = fixture(), target = room.players[1].ships.find(ship => ship.w * ship.h === 4), cells = shipCells(target)
  const { context, page } = await seeded(room)
  await cell(page, cells[0].x, cells[0].y)
  await page.waitForFunction(() => document.querySelector('.enemy-panel canvas')?.dataset.animating === 'true')
  assert(await page.locator('.enemy-panel .naval-board').evaluate(el => el.classList.contains('enabled')), 'hit allows immediate follow-up during animation')
  await cell(page, cells[1].x, cells[1].y)
  assert.equal(await page.evaluate(() => JSON.parse(localStorage.getItem('naval-guest-session')).events.filter(e => e.weapon === 'shot').length), 2)
  assert.equal(await page.locator('.enemy-panel canvas').getAttribute('data-effect-event'), await page.evaluate(() => String(JSON.parse(localStorage.getItem('naval-guest-session')).events.findLast(e => e.weapon === 'shot').id)), 'follow-up animation starts immediately rather than waiting in a queue')
  const occupied = new Set(room.players[1].ships.flatMap(shipCells).map(c => `${c.x},${c.y}`))
  const water = Array.from({ length: 144 }, (_, i) => ({ x: i % 12, y: Math.floor(i / 12) })).find(c => !occupied.has(`${c.x},${c.y}`))
  await cell(page, water.x, water.y)
  assert.equal(await page.locator('.board-turn.active').count(), 0, 'handoff does not highlight or count down before animations finish')
  await page.waitForFunction(() => document.querySelector('.enemy-panel .board-turn.active'), null, { timeout: 15000 })
  assert(!/0:31/.test(await page.locator('.boards').innerText()))
  await context.close()

  const torpedo = fixture(); torpedo.players[0].inventory.torpedo = 1
  const drop = torpedo.players[1].ships.find(ship => ship.x >= 2) || torpedo.players[1].ships[0]
  const launch = await seeded(torpedo)
  await launch.page.getByRole('button', { name: '鱼雷机', exact: true }).click()
  await cell(launch.page, drop.x, drop.y)
  const event = await launch.page.evaluate(() => JSON.parse(localStorage.getItem('naval-guest-session')).events.findLast(e => e.weapon === 'torpedo'))
  assert.equal(event.x, drop.x); assert.equal(event.cells[0].x, drop.x)
  await launch.context.close()

  const detail = localBattleResult(room)
  detail.battleStarted = true; detail.events = [{ id: 1, player: 0, weapon: 'shot', cells: [{ ...cells[0], result: 'hit' }], text: '舰炮命中' }]
  for (const viewport of [{ width: 844, height: 390 }, { width: 568, height: 320 }, { width: 390, height: 844 }]) {
    const mobile = await browser.newContext({ viewport, isMobile: true, hasTouch: true })
    await mobile.addInitScript(detail => localStorage.setItem('naval-guest-history', JSON.stringify([{ id: detail.gameId, gameId: detail.gameId, detail, local: true, mode: 'AI', won: true, at: Date.now() }])), detail)
    const replay = await mobile.newPage(); replay.on('pageerror', e => errors.push(e.message))
    await replay.goto(base + '/customer/entertainment/naval')
    await replay.getByRole('button', { name: '历史战绩', exact: true }).click()
    await replay.locator('.history-entry').first().click()
    await replay.getByRole('button', { name: '对局回放', exact: true }).click()
    await replay.waitForFunction(() => document.querySelectorAll('.naval-replay canvas[data-renderer]').length === 2)
    await replay.waitForTimeout(200)
    const caption = await replay.locator('.replay-caption').boundingBox(), board = await replay.locator('.replay-boards').boundingBox(), controls = await replay.locator('.replay-controls').boundingBox()
    assert(caption.y + caption.height <= board.y + 1, 'caption is above boards')
    await replay.screenshot({ path: path.join(output, `naval-turn-replay-${viewport.width}.png`) })
    assert(controls.y + controls.height <= viewport.height + 1, `controls fit viewport ${JSON.stringify({ viewport, caption, board, controls })}`)
    if (viewport.width > viewport.height) {
      const canvas = await replay.locator('.naval-replay canvas').first().boundingBox()
      assert(canvas.height >= viewport.height - 170, JSON.stringify(canvas))
      assert(viewport.height - controls.y - controls.height < 65, 'no large empty bottom band')
    }
    await replay.getByRole('button', { name: '下一次攻击', exact: true }).click()
    await replay.waitForTimeout(400)
    await replay.screenshot({ path: path.join(output, `naval-turn-replay-${viewport.width}.png`) })
    await replay.waitForTimeout(4200)
    assert.equal(await replay.locator('.replay-caption>span').count(), 0, 'caption fades out')
    await mobile.close()
  }
  // Check the scene's input lock independently of network and AI think time.
  const probe = await browser.newPage()
  await probe.goto(base + '/customer/entertainment/naval')
  const lead = await probe.evaluate(async () => {
    const { mountNavalGame } = await import('/src/games/navalScene.js')
    const host = document.createElement('div'); host.style.cssText = 'width:300px;height:300px'; document.body.append(host)
    const snapshot = { player: { ships: [], shots: [], aa: [], mines: [] }, events: [] }
    let mounted, started, unlocked
    return await new Promise((resolve, reject) => {
      mounted = mountNavalGame(host, snapshot, { error: reject, ready() { setTimeout(() => {
        const scene = mounted.game.scene.getScene('naval-board')
        scene.playAttack = () => 1000
        started = performance.now()
        mounted.update({ ...snapshot, events: [{ id: 1, weapon: 'shot', player: 0, cells: [] }] })
        setTimeout(() => { const result = { unlock: unlocked - started, playing: scene.playingEvent }; mounted.destroy(); host.remove(); resolve(result) }, 1080)
      }, 20) }, animation(value) { if (started && !value) unlocked = performance.now() } })
    })
  })
  assert(lead.unlock >= 850 && lead.unlock <= 970, JSON.stringify(lead))
  assert.equal(lead.playing, false)
  await probe.close()
  assert.deepEqual(errors, [])
  console.log('Immediate hit chaining, delayed turn indicators, selected torpedo drop, mobile replay sizing/caption fades and 100 ms early unlock passed')
} finally { await browser.close() }
