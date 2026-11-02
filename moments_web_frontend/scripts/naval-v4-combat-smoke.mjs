import assert from 'node:assert/strict'
import { mkdir } from 'node:fs/promises'
import path from 'node:path'
import { chromium } from 'playwright-core'
import { createLocalGame } from '../src/games/navalEngine.js'

const base = process.env.QA_BASE_URL || 'http://127.0.0.1:5174'
const output = process.env.QA_OUTPUT_DIR || path.resolve('../.agents/naval-qa')
const gamePath = '/customer/entertainment/naval'
const errors = [], findings = {}
await mkdir(output, { recursive: true })
const browser = await chromium.launch({ executablePath: process.env.QA_BROWSER || 'C:/Program Files/Google/Chrome/Application/chrome.exe', headless: true })

function battleFixture() {
  const room = createLocalGame()
  room.phase = 'BATTLE'; room.deadline = 0; room.turn = 0
  for (const player of room.players) { player.stage = 'READY'; player.ready = true; player.aa = []; player.mines = []; player.inventory.antiAir = 0; player.inventory.mine = 0 }
  room.players[0].inventory = { antiAir: 0, mine: 0, fighter: 3, bomber: 3, torpedo: 3, radar: 2 }
  return room
}
async function seedPage(context, deadlineSeconds = 180) {
  const page = await context.newPage()
  page.on('pageerror', error => errors.push(error.message))
  await page.addInitScript(({ room, deadlineSeconds }) => {
    room.deadline = Date.now() + deadlineSeconds * 1000
    localStorage.setItem('naval-guest-session', JSON.stringify(room))
    localStorage.setItem('naval-guest-resume-kind', 'LOCAL')
    localStorage.removeItem('naval_sound_muted')
    window.__audioStarts = []
    const start = AudioBufferSourceNode.prototype.start
    AudioBufferSourceNode.prototype.start = function (...args) {
      window.__audioStarts.push({ duration: this.buffer?.duration, at: Date.now(), perf: performance.now(), contextState: this.context.state })
      return start.apply(this, args)
    }
  }, { room: battleFixture(), deadlineSeconds })
  await page.goto(base + gamePath, { waitUntil: 'domcontentloaded' })
  await page.getByRole('button', { name: '继续对局', exact: true }).click()
  await page.locator('.weapons').waitFor({ state: 'attached' })
  await page.getByRole('status', { name: '舰队加载中' }).waitFor({ state: 'detached' })
  await page.waitForFunction(() => [...document.querySelectorAll('.naval-board canvas')].length === 2 && [...document.querySelectorAll('.naval-board canvas')].every(canvas => canvas.dataset.renderer === 'phaser-3.90'))
  return page
}
async function state(page) { return page.evaluate(() => JSON.parse(localStorage.getItem('naval-guest-session'))) }
async function boardCell(page, x, y) {
  const canvas = page.locator('.enemy-panel canvas'), box = await canvas.boundingBox()
  assert(box, 'Enemy board is visible')
  await canvas.click({ position: { x: (24 + x * 48 + 24) / 624 * box.width, y: (24 + y * 48 + 24) / 624 * box.height } })
}
async function selectedWeapon(page) { return page.locator('.weapons>button.selected').getAttribute('data-tutorial') }
async function turnState(page) {
  return page.evaluate(() => Object.fromEntries(['own', 'enemy'].map(side => {
    const element = document.querySelector(`.${side}-panel .board-turn`), style = getComputedStyle(element)
    return [side, { active: element.classList.contains('active'), background: style.backgroundColor, color: style.color, text: element.textContent, icons: element.querySelectorAll(':scope>svg').length, clocks: element.querySelectorAll(':scope>span').length }]
  })))
}
async function checkLayout(page, name) {
  const value = await page.evaluate(() => ({
    viewport: { width: innerWidth, height: innerHeight }, overflow: document.documentElement.scrollWidth > innerWidth,
    boards: [...document.querySelectorAll('.naval-board')].map(element => element.getBoundingClientRect().toJSON()),
    weapons: document.querySelector('.weapons').getBoundingClientRect().toJSON(),
    weaponButtons: [...document.querySelectorAll('.weapons>button')].map(element => element.getBoundingClientRect().toJSON()).filter(box => box.width > 0),
    colors: [...document.querySelectorAll('.naval-board canvas')].map(canvas => {
      const data = canvas.getContext('2d').getImageData(0, 0, canvas.width, canvas.height).data, colors = new Set()
      for (let i = 0; i < data.length; i += 64) colors.add(`${data[i]},${data[i + 1]},${data[i + 2]}`)
      return colors.size
    })
  }))
  await page.screenshot({ path: path.join(output, `${name}.png`), fullPage: true })
  assert(!value.overflow, `${name}: no horizontal overflow`)
  assert(value.boards[0].right <= value.boards[1].left, `${name}: boards do not overlap`)
  assert(value.colors.every(count => count > 6), `${name}: Phaser canvases are nonblank (${value.colors})`)
  assert(value.weaponButtons.every(button => button.bottom <= value.viewport.height), `${name}: weapon controls fit viewport (${value.weaponButtons.map(button => button.bottom)})`)
  return value
}

try {
  const desktop = await browser.newContext({ viewport: { width: 1440, height: 900 } })
  const page = await seedPage(desktop)
  await page.locator('.phase-announcement').waitFor({ state: 'detached' })
  await page.waitForTimeout(350)
  const banners = await turnState(page)
  assert(banners.own.active && !banners.enemy.active, 'Own turn highlights only the left banner')
  assert.equal(banners.own.background, 'rgb(230, 242, 238)', 'Own active banner settles into its green state')
  assert.equal(banners.enemy.background, 'rgb(237, 242, 244)', 'Inactive enemy banner settles into its neutral state')
  assert.equal(banners.own.icons, 1); assert.equal(banners.enemy.icons, 1)
  assert.equal(banners.own.clocks, 1); assert.equal(banners.enemy.clocks, 0)
  assert.equal(await page.locator('.own-panel canvas').getAttribute('data-defense-side'), 'left')
  assert.equal(await page.locator('.enemy-panel canvas').getAttribute('data-defense-side'), 'right')
  const headers = await page.evaluate(() => Object.fromEntries(['own', 'enemy'].map(side => {
    const header = document.querySelector(`.${side}-panel>header`)
    return [side, { name: header.querySelector('h2').getBoundingClientRect().toJSON(), count: header.querySelector(':scope>span').getBoundingClientRect().toJSON() }]
  })))
  assert(headers.enemy.count.right < headers.enemy.name.left, 'Enemy ship count is on the left and name on the right')
  assert(headers.own.name.right < headers.own.count.left, 'Own name/count form the mirrored header')
  findings.banners = banners

  const weapons = ['shot', 'fighter', 'bomber', 'torpedo', 'radar']
  for (const [index, weapon] of weapons.entries()) {
    await page.keyboard.press(String(index + 1))
    assert.equal(await selectedWeapon(page), `naval-weapon-${weapon}`, `Digit ${index + 1} selects ${weapon}`)
  }
  await page.keyboard.press('4')
  await page.getByRole('button', { name: '战局聊天', exact: true }).click()
  await page.waitForFunction(() => document.querySelector('.chat-drawer')?.classList.contains('chat-slide-enter-active'))
  await page.waitForTimeout(40)
  const entering = await page.locator('.chat-drawer').evaluate(element => ({ transform: getComputedStyle(element).transform, opacity: Number(getComputedStyle(element).opacity), duration: getComputedStyle(element).transitionDuration }))
  assert.notEqual(entering.transform, 'none', 'Chat drawer slides into the screen')
  assert(entering.opacity < 1 && entering.opacity > 0, 'Chat drawer fades in rather than appearing instantly')
  await page.waitForTimeout(400)
  const input = page.getByRole('textbox', { name: '战局消息' })
  await input.fill('联合作战 ')
  await input.pressSequentially('12345')
  assert.equal(await input.inputValue(), '联合作战 12345')
  assert.equal(await selectedWeapon(page), 'naval-weapon-torpedo', 'Typing digits in chat must not select weapons')
  await page.getByRole('button', { name: '发送消息', exact: true }).click()
  await page.locator('.own-panel .player-chat-bubble').filter({ hasText: '联合作战 12345' }).waitFor()
  assert.equal((await state(page)).messages.at(-1).text, '联合作战 12345')
  await page.waitForTimeout(280)
  const chatGeometry = await page.evaluate(() => ({ bubble: document.querySelector('.own-panel .player-chat-bubble').getBoundingClientRect().toJSON(), turn: document.querySelector('.own-panel .board-turn').getBoundingClientRect().toJSON() }))
  assert(chatGeometry.bubble.top >= chatGeometry.turn.bottom, 'Player message bubble must not overlap the prominent turn banner')
  await page.screenshot({ path: path.join(output, 'v4-chat-bubble-desktop.png'), fullPage: true })
  await page.getByRole('button', { name: '收起聊天', exact: true }).click()
  await page.waitForFunction(() => document.querySelector('.chat-drawer')?.classList.contains('chat-slide-leave-active'))
  await page.waitForTimeout(40)
  const leaving = await page.locator('.chat-drawer').evaluate(element => ({ transform: getComputedStyle(element).transform, opacity: Number(getComputedStyle(element).opacity) }))
  assert.notEqual(leaving.transform, 'none', 'Closing chat slides the drawer out')
  assert(leaving.opacity < 1, 'Closing chat fades out')
  await page.locator('.chat-drawer').waitFor({ state: 'detached' })
  findings.chat = { entering, leaving, bubble: true, typingGuard: true }

  await page.locator('.naval-title').click()
  await page.keyboard.press('1')
  assert.equal(await selectedWeapon(page), 'naval-weapon-shot')
  const before = await state(page), targets = before.players[1].ships.filter(ship => ship.w * ship.h === 1).slice(0, 2)
  assert.equal(targets.length, 2)
  const start = await page.evaluate(() => performance.now())
  await boardCell(page, targets[0].x, targets[0].y)
  await page.waitForFunction(id => JSON.parse(localStorage.getItem('naval-guest-session')).players[1].ships.find(ship => ship.id === id).hits.length === 1, targets[0].id)
  await page.waitForTimeout(370)
  assert.equal(await page.locator('.enemy-panel canvas').getAttribute('data-animating'), 'true', 'First impact/sinking is still being presented')
  assert(await page.locator('[data-tutorial="naval-weapon-shot"]').isEnabled(), 'Cosmetic animations do not disable attack controls')
  await boardCell(page, targets[1].x, targets[1].y)
  await page.waitForFunction(id => JSON.parse(localStorage.getItem('naval-guest-session')).players[1].ships.find(ship => ship.id === id).hits.length === 1, targets[1].id)
  const elapsed = await page.evaluate(() => performance.now()) - start
  assert(elapsed < 1000, `Two hit attacks must be accepted in under 1 s, got ${elapsed} ms`)
  const after = await state(page)
  assert.equal(after.turn, 0, 'Hits retain the player turn')
  assert.equal(after.events.filter(event => event.weapon === 'shot' && event.cells.some(cell => cell.result === 'hit')).length, 2)
  findings.rapidHitsMs = Math.round(elapsed)
  await checkLayout(page, 'v4-rapid-hits-desktop')

  const miss = Array.from({ length: 144 }, (_, i) => ({ x: i % 12, y: Math.floor(i / 12) })).find(point => !after.players[1].ships.some(ship => point.x >= ship.x && point.x < ship.x + ship.w && point.y >= ship.y && point.y < ship.y + ship.h))
  await boardCell(page, miss.x, miss.y)
  await page.waitForFunction(() => JSON.parse(localStorage.getItem('naval-guest-session')).turn === 1)
  await page.waitForTimeout(380)
  const enemyTurn = await turnState(page)
  assert(!enemyTurn.own.active && enemyTurn.enemy.active, 'Miss transfers the prominent turn marker to the right board')
  assert.equal(enemyTurn.enemy.background, 'rgb(252, 240, 232)', 'Enemy turn settles into its warm highlight')
  assert.equal(enemyTurn.own.clocks, 0); assert.equal(enemyTurn.enemy.clocks, 1)
  findings.enemyTurn = enemyTurn
  await page.screenshot({ path: path.join(output, 'v4-enemy-turn-desktop.png'), fullPage: true })
  await desktop.close()

  const countdown = await browser.newContext({ viewport: { width: 1280, height: 800 } })
  const tickPage = await seedPage(countdown, 9)
  const initialClock = await tickPage.locator('.own-panel .board-turn>span').innerText()
  await tickPage.waitForFunction(() => window.__audioStarts.some(sample => sample.duration > .16 && sample.duration < .18), null, { timeout: 10000 })
  await tickPage.waitForFunction(() => window.__audioStarts.filter(sample => sample.duration > .16 && sample.duration < .18).length >= 5, null, { timeout: 8000 })
  const ticks = await tickPage.evaluate(() => window.__audioStarts.filter(sample => sample.duration > .16 && sample.duration < .18))
  assert.equal(ticks.length, 5, 'Final five seconds play exactly one tick per second')
  assert(ticks.every(sample => sample.contextState === 'running'), 'Countdown ticks really start on an unlocked audio device')
  assert(ticks.slice(1).every((sample, index) => sample.at - ticks[index].at >= 650 && sample.at - ticks[index].at <= 1350), 'Countdown ticks follow one-second clock boundaries')
  findings.countdown = { initialClock, ticks: ticks.map(sample => ({ duration: sample.duration, at: sample.at })) }
  await tickPage.screenshot({ path: path.join(output, 'v4-countdown-desktop.png'), fullPage: true })
  await countdown.close()

  const mobile = await browser.newContext({ viewport: { width: 844, height: 390 }, isMobile: true, hasTouch: true })
  const phone = await seedPage(mobile)
  await phone.locator('.phase-announcement').waitFor({ state: 'detached' })
  assert(await phone.evaluate(() => Boolean(document.fullscreenElement)), 'Mobile combat really runs in fullscreen')
  assert.equal(await phone.getByRole('dialog', { name: '横屏全屏限制' }).count(), 0)
  assert.equal(await phone.locator('.weapons').isVisible(), false, 'mobile keeps attacks in a collapsible palette')
  await phone.getByRole('button', { name: '展开攻击方式', exact: true }).waitFor()
  const mobileLayout = await checkLayout(phone, 'v4-battle-mobile-landscape')
  findings.mobile = mobileLayout
  assert(mobileLayout.boards.every(board => board.bottom <= 390), 'Both mobile boards fit the landscape viewport')
  assert(mobileLayout.weaponButtons.every(button => button.bottom <= 390), 'All mobile weapon controls fit without scrolling')
  await phone.setViewportSize({ width: 568, height: 320 })
  await phone.waitForTimeout(500)
  const smallLayout = await checkLayout(phone, 'v4-battle-mobile-small-landscape')
  assert(smallLayout.boards.every(board => board.bottom <= 320), 'Both boards fit small landscape phones')
  findings.smallMobile = smallLayout
  await mobile.close()
  assert.deepEqual(errors, [])
  console.log(JSON.stringify({ passed: true, findings, output }))
} catch (error) {
  console.error(JSON.stringify({ passed: false, findings, output }))
  throw error
} finally { await browser.close() }
