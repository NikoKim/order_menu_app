import assert from 'node:assert/strict'
import { mkdir } from 'node:fs/promises'
import path from 'node:path'
import { chromium } from 'playwright-core'
import { createLocalGame } from '../src/games/navalEngine.js'
import { getNavalTutorialStep, NAVAL_TUTORIAL_STORAGE_KEY, NAVAL_MOBILE_TUTORIAL_STORAGE_KEY, NAVAL_TUTORIAL_LESSONS } from '../src/games/navalTutorial.js'

const base = process.env.QA_BASE_URL || 'http://127.0.0.1:5174'
const output = process.env.QA_OUTPUT_DIR || path.resolve('../.agents/naval-qa')
const browser = await chromium.launch({ executablePath: process.env.QA_BROWSER || 'C:/Program Files/Google/Chrome/Application/chrome.exe', headless: true })
await mkdir(output, { recursive: true })
const errors = []
const pathName = '/customer/entertainment/naval'
const read = (page, key = NAVAL_TUTORIAL_STORAGE_KEY) => page.evaluate(key => JSON.parse(localStorage.getItem(key) || 'null'), key)

async function idle(page) {
  await page.waitForFunction(() => {
    const highlight = document.querySelector('.naval-tutorial-highlight')
    return highlight?.dataset.moving === 'false' && [...document.querySelectorAll('.naval-board canvas')].every(canvas => canvas.dataset.animating !== 'true')
  }, null, { timeout: 20000 })
}

async function cell(page, selector, point) {
  const canvas = page.locator(selector), box = await canvas.boundingBox()
  assert(box)
  const mirrored = await canvas.evaluate(el=>Boolean(el.closest('.naval-board.mirrored')))
  await canvas.click({ position: { x: (24 + (mirrored ? 11-point.x : point.x) * 48 + 24) / 624 * box.width, y: (24 + point.y * 48 + 24) / 624 * box.height } })
}

async function layoutCheck(page) {
  const result = await page.evaluate(() => {
    const guide = document.querySelector('.naval-tutorial-guide').getBoundingClientRect()
    const hole = document.querySelector('.naval-tutorial-highlight').getBoundingClientRect()
    return { width: innerWidth, height: innerHeight, guide: guide.toJSON(), hole: hole.toJSON(), shades: document.querySelectorAll('.shade-panel').length }
  })
  assert.equal(result.shades, 4)
  assert(result.guide.left >= 0 && result.guide.right <= result.width && result.guide.top >= 0 && result.guide.bottom <= result.height, 'guide fits viewport')
  assert(result.hole.width > 5 && result.hole.height > 5, 'spotlight target is visible')
  assert(result.guide.right <= result.hole.left || result.guide.left >= result.hole.right || result.guide.bottom <= result.hole.top || result.guide.top >= result.hole.bottom, 'tutorial guide does not cover its target')
}

try {
  const context = await browser.newContext({ viewport: { width: 1440, height: 1000 } })
  const formal = JSON.stringify(createLocalGame()), history = JSON.stringify([{ id: 'formal-test', mode: 'AI', won: true, at: 1000 }])
  await context.addInitScript(({ formal, history }) => { if (!sessionStorage.getItem('tutorial-qa-seeded')) { localStorage.setItem('naval-guest-session', formal); localStorage.setItem('naval-guest-history', history); sessionStorage.setItem('tutorial-qa-seeded', '1') } }, { formal, history })
  const page = await context.newPage()
  page.on('pageerror', error => errors.push(error.message))
  await page.goto(base + pathName)
  await page.getByRole('button', { name: '开始教程', exact: true }).click()
  await page.locator('.naval-tutorial-guide[data-step="welcome"]').waitFor()
  await idle(page)
  assert.equal(await page.locator('.phase-announcement').count(), 0, 'tutorial spotlight is not interrupted by a phase banner')
  await layoutCheck(page)
  await page.screenshot({ path: path.join(output, 'tutorial-welcome-desktop.png'), fullPage: true })
  await page.getByRole('button', { name: '重新开始海战教程', exact: true }).click()
  const restartDialog = page.getByRole('dialog', { name: '重新开始教程', exact: true })
  await restartDialog.waitFor()
  assert.equal(await page.locator('.naval-tutorial-guide').count(), 0, 'spotlight guard yields to the animated confirmation')
  await restartDialog.getByRole('button', { name: '取消', exact: true }).click()
  await page.locator('.naval-tutorial-guide[data-step="welcome"]').waitFor()
  await idle(page)
  await page.getByRole('button', { name: '重新开始海战教程', exact: true }).click()
  await restartDialog.getByRole('button', { name: '确认', exact: true }).click()
  await page.locator('.naval-tutorial-guide[data-step="welcome"]').waitFor()
  await idle(page)
  const visited = [], initialFormationCount = await page.evaluate(() => localStorage.getItem('naval-guest-formations'))
  let resumed = false
  for (let i = 0; i < 60; i++) {
    await idle(page)
    const session = await read(page), step = getNavalTutorialStep(session)
    await page.locator(`.naval-tutorial-guide[data-step="${step.id}"]`).waitFor()
    await layoutCheck(page)
    if (visited.at(-1) !== step.id) visited.push(step.id)
    if (step.id === 'buy-aa' && !resumed) {
      await page.getByRole('button', { name: '退出教程，保留进度', exact: true }).click()
      await page.getByRole('heading', { name: '选择出航方式' }).waitFor()
      assert.equal((await read(page)).step, session.step)
      await page.getByRole('button', { name: '继续演习', exact: true }).click()
      await page.locator(`.naval-tutorial-guide[data-step="${step.id}"]`).waitFor()
      await idle(page); resumed = true
    }
    if (step.mode === 'read') {
      await page.locator('.naval-tutorial-guide .guide-primary').click()
      if (step.id === 'complete') break
    } else if (step.id === 'move') {
      await page.locator('[data-tutorial="naval-fleet-roster"] button').first().click()
      await idle(page)
      await cell(page, '.own-panel canvas', getNavalTutorialStep(await read(page)).cell)
    } else if (step.id === 'clear') {
      await page.locator('[data-tutorial="naval-clear-layout"]').click()
      assert.equal((await read(page)).room.players[0].ships.length, 0)
    } else if (step.id === 'randomize') await page.locator('[data-tutorial="naval-random-layout"]').click()
    else if (step.id === 'rotate') await page.locator('[data-tutorial="naval-rotate-ship"]').first().click()
    else if (step.id === 'layout') await page.locator('[data-tutorial="naval-layout-next"]').click()
    else if (step.id.startsWith('buy-')) await page.locator(`[data-tutorial="${step.target}"]`).click()
    else if (['place-mine','place-aa','coat'].includes(step.id)) await cell(page, '.own-panel canvas', step.cell)
    else if (step.id === 'ready') await page.locator('[data-tutorial="naval-supply-ready"]').click()
    else {
      if (step.target !== 'naval-enemy-board') {
        await page.locator(`[data-tutorial="${step.target}"]`).click()
        await idle(page)
      }
      const positions = step.weapon === 'submarine' ? [session.targets[step.id][0]] : [getNavalTutorialStep(await read(page)).cell]
      for (const point of positions) { await idle(page); await cell(page, '.enemy-panel canvas', point) }
      if (step.id === 'mine') {
        await page.waitForFunction(() => document.querySelector('.enemy-panel canvas')?.dataset.effectPhase === 'mine-rise')
        await page.screenshot({ path: path.join(output, 'tutorial-water-mine.png'), fullPage: true })
      }
      if (step.id === 'fighter') {
        await page.waitForFunction(() => document.querySelector('.enemy-panel canvas')?.dataset.effectPhase === 'aa-aim')
        await page.screenshot({ path: path.join(output, 'tutorial-air-defense.png'), fullPage: true })
      }
      if (step.id === 'fighter-strafe') {
        const attack=(await read(page)).room.events.findLast(event=>event.weapon==='fighter')
        assert.equal(attack.cells.length,8); assert.equal(new Set(attack.cells.map(cell=>cell.y)).size,1)
        await page.waitForTimeout(900)
        await page.screenshot({ path:path.join(output,'tutorial-fighter-single-row.png'),fullPage:true })
      }
      if (step.id === 'submarine-mine') {
        const attack=(await read(page)).room.events.findLast(event=>event.weapon==='submarine')
        assert(attack.destroyed); assert.equal(attack.cells.length,2)
        await page.waitForFunction(()=>document.querySelector('.enemy-panel canvas')?.dataset.submarineDestroyed==='true' && document.querySelector('.enemy-panel canvas')?.dataset.effectPhase==='mine-rise')
        await page.screenshot({path:path.join(output,'tutorial-submarine-two-cell-impact.png'),fullPage:true})
      }
    }
  }
  await idle(page)
  assert.equal((await read(page)).completed, true)
  assert.equal(visited.length, NAVAL_TUTORIAL_LESSONS.length)
  await page.screenshot({ path: path.join(output, 'tutorial-complete-desktop.png'), fullPage: true })
  await page.getByRole('button', { name: '返回港口', exact: true }).click()
  await page.getByRole('heading', { name: '选择出航方式' }).waitFor()
  assert.deepEqual(await page.evaluate(() => ({ formal: localStorage.getItem('naval-guest-session'), history: localStorage.getItem('naval-guest-history'), formations: localStorage.getItem('naval-guest-formations') })), { formal, history, formations: initialFormationCount })

  const mobile = await browser.newContext({ viewport: { width: 844, height: 390 }, isMobile: true, hasTouch: true })
  const phone = await mobile.newPage()
  phone.on('pageerror', error => errors.push(error.message))
  await phone.goto(base + pathName)
  await phone.getByRole('button', { name: '开始教程', exact: true }).click()
  const fullscreen = phone.getByRole('button', { name: '全屏并锁定横屏', exact: true })
  if (await fullscreen.count()) await fullscreen.click()
  await phone.locator('.naval-tutorial-guide[data-step="welcome"]').waitFor()
  await idle(phone)
  await layoutCheck(phone)
  assert(await phone.evaluate(() => Boolean(document.fullscreenElement)), 'mobile tutorial retains fullscreen requirement')
  await phone.screenshot({ path: path.join(output, 'tutorial-welcome-mobile.png'), fullPage: true })
  await phone.setViewportSize({ width: 667, height: 375 })
  await phone.waitForTimeout(450)
  await idle(phone)
  await layoutCheck(phone)
  assert(await phone.evaluate(() => document.documentElement.scrollWidth <= innerWidth), 'narrow landscape welcome has no horizontal overflow')
  await phone.screenshot({ path: path.join(output, 'tutorial-welcome-mobile-narrow.png'), fullPage: true })
  await phone.locator('.naval-tutorial-guide .guide-primary').click()
  await idle(phone)
  await phone.locator('.naval-tutorial-guide .guide-primary').click()
  await idle(phone)
  await phone.locator('[data-tutorial="naval-fleet-roster"] button').first().click()
  await idle(phone)
  await layoutCheck(phone)
  await phone.screenshot({ path: path.join(output, 'tutorial-cell-mobile.png'), fullPage: true })
  await phone.setViewportSize({ width: 667, height: 375 })
  await phone.waitForTimeout(450)
  await idle(phone)
  await layoutCheck(phone)
  assert(await phone.evaluate(() => document.documentElement.scrollWidth <= innerWidth), 'narrow landscape tutorial has no horizontal overflow')
  await phone.screenshot({ path: path.join(output, 'tutorial-cell-mobile-narrow.png'), fullPage: true })
  const progressBeforeGateExit = await read(phone, NAVAL_MOBILE_TUTORIAL_STORAGE_KEY)
  assert.equal(await phone.evaluate(() => localStorage.getItem('naval-guest-session')), null, 'tutorial never starts a formal session')
  await phone.evaluate(() => document.exitFullscreen())
  const gate = phone.getByRole('dialog', { name: '横屏全屏限制', exact: true })
  await gate.waitFor()
  assert.equal(await phone.locator('.naval-tutorial-guide').count(), 0, 'fullscreen gate takes over from the tutorial spotlight')
  await gate.getByRole('button', { name: '返回港口', exact: true }).click()
  await phone.getByRole('heading', { name: '选择出航方式' }).waitFor()
  assert.equal(await phone.locator('.naval-tutorial-guide').count(), 0)
  assert.deepEqual(await read(phone, NAVAL_MOBILE_TUTORIAL_STORAGE_KEY), progressBeforeGateExit, 'leaving through the device gate preserves tutorial progress')
  assert.equal(await phone.evaluate(() => localStorage.getItem('naval-guest-session')), null)
  assert.equal(await phone.evaluate(() => localStorage.getItem('naval-guest-history')), null)
  await phone.getByRole('button', { name: '开始对战', exact: true }).click()
  if (await fullscreen.count()) await fullscreen.click()
  await phone.getByRole('button', { name: '下一步：战备补给', exact: true }).waitFor()
  await phone.getByRole('status', { name: '舰队加载中' }).waitFor({ state: 'detached' })
  assert.equal(await phone.locator('.naval-tutorial-guide').count(), 0, 'formal AI game is no longer in tutorial mode')
  const normal = await phone.evaluate(() => JSON.parse(localStorage.getItem('naval-guest-session')))
  assert.equal(normal.mode, 'AI')
  assert.notEqual(normal.id, 'naval-tutorial')
  assert.equal(normal.players[0].ships.length, 10)
  assert.deepEqual(await read(phone, NAVAL_MOBILE_TUTORIAL_STORAGE_KEY), progressBeforeGateExit, 'formal AI actions do not overwrite tutorial progress')
  await phone.evaluate(() => document.exitFullscreen())
  await gate.waitFor()
  assert(await phone.getByRole('button', { name: '下一步：战备补给', exact: true }).isDisabled(), 'normal AI commands remain blocked outside fullscreen')
  await phone.screenshot({ path: path.join(output, 'tutorial-exit-to-formal-mobile-gate.png'), fullPage: true })
  assert.deepEqual(errors, [])
  console.log(JSON.stringify({ passed: true, lessons: visited, resume: true, isolatedStorage: true, mobileSpotlight: true, narrowLandscape: true, fullscreenExitThenNormalGame: true, output }))
} catch (error) {
  for (const [index, context] of browser.contexts().entries()) for (const page of context.pages()) {
    await page.screenshot({ path: path.join(output, `tutorial-failure-${index}.png`), fullPage: true }).catch(() => {})
    console.error(JSON.stringify(await page.evaluate(() => ({ step: document.querySelector('.naval-tutorial-guide')?.dataset.step, text: document.querySelector('.naval-tutorial-guide')?.textContent, error: document.querySelector('.naval-notice')?.textContent })).catch(() => null)))
  }
  throw error
} finally { await browser.close() }
