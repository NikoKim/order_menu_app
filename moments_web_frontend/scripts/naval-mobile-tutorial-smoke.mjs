import assert from 'node:assert/strict'
import { mkdir } from 'node:fs/promises'
import path from 'node:path'
import { chromium } from 'playwright-core'
import { createLocalGame } from '../src/games/navalEngine.js'
import { createNavalTutorial, getNavalTutorialStep, NAVAL_TUTORIAL_STORAGE_KEY, NAVAL_MOBILE_TUTORIAL_STORAGE_KEY, NAVAL_TUTORIAL_LESSONS, NAVAL_MOBILE_TUTORIAL_LESSONS } from '../src/games/navalTutorial.js'

const base = process.env.QA_BASE_URL || 'http://127.0.0.1:5174'
const output = process.env.QA_OUTPUT_DIR || path.resolve('../.agents/naval-qa')
const browser = await chromium.launch({ executablePath: process.env.QA_BROWSER || 'C:/Program Files/Google/Chrome/Application/chrome.exe', headless: true })
await mkdir(output, { recursive: true })
const errors = [], checks = []
const gamePath = '/customer/entertainment/naval'
const read = page => page.evaluate(key => JSON.parse(localStorage.getItem(key) || 'null'), NAVAL_MOBILE_TUTORIAL_STORAGE_KEY)
const target = (page, name) => page.locator(`[data-tutorial="${name}"]:visible`).first()

async function idle(page) {
  await page.waitForFunction(() => {
    const hole = document.querySelector('.naval-tutorial-highlight')
    return hole?.dataset.moving === 'false' && [...document.querySelectorAll('.naval-board canvas')].every(canvas => canvas.dataset.animating !== 'true') && !document.querySelector('.launch-overlay,.stage-scene-leave-active,.mobile-sheet-enter-active,.mobile-sheet-leave-active')
  }, null, { timeout: 20000 })
  await page.waitForTimeout(90)
}

async function geometry(page, name) {
  await idle(page)
  await page.waitForTimeout(350)
  const bounds = await page.evaluate(() => {
    const guide = document.querySelector('.naval-tutorial-guide'), hole = document.querySelector('.naval-tutorial-highlight'), root = document.querySelector('.naval-page')
    return { width: innerWidth, height: innerHeight, x: scrollX, y: scrollY, root: root.getBoundingClientRect().toJSON(), guide: guide.getBoundingClientRect().toJSON(), hole: hole.getBoundingClientRect().toJSON(), target: hole.dataset.target, enemy: document.querySelector('.enemy-panel .naval-board')?.getBoundingClientRect().toJSON(), guideScroll: guide.scrollHeight > guide.clientHeight + 1, documentOverflow: document.documentElement.scrollWidth > innerWidth + 1, device: guide.dataset.device }
  })
  assert.equal(bounds.device, 'mobile', `${name}: compact phone-specific tutorial`)
  assert(!bounds.documentOverflow, `${name}: no horizontal document overflow`)
  assert(bounds.root.top >= -1 && bounds.root.bottom <= bounds.height + 1, `${name}: fixed game screen fits viewport`)
  assert(bounds.guide.left >= 0 && bounds.guide.right <= bounds.width + 1 && bounds.guide.top >= 0 && bounds.guide.bottom <= bounds.height + 1, `${name}: guide fits viewport ${JSON.stringify(bounds)}`)
  assert(!bounds.guideScroll, `${name}: tutorial copy needs no vertical scrolling`)
  if (bounds.enemy && ['naval-enemy-board', 'naval-confirm-target'].includes(bounds.target)) assert(bounds.guide.right <= bounds.enemy.left, `${name}: attacking guide stays in the left area, not over the enemy board`)
  assert(bounds.hole.width > 5 && bounds.hole.height > 5, `${name}: spotlight is visible`)
  assert(bounds.guide.right <= bounds.hole.left + 1 || bounds.guide.left >= bounds.hole.right - 1 || bounds.guide.bottom <= bounds.hole.top + 1 || bounds.guide.top >= bounds.hole.bottom - 1, `${name}: guide does not cover spotlight ${JSON.stringify(bounds)}`)
  await page.mouse.move(2, 2)
  await page.mouse.wheel(0, 250)
  await page.waitForTimeout(50)
  assert.deepEqual(await page.evaluate(() => ({ x: scrollX, y: scrollY })), { x: bounds.x, y: bounds.y }, `${name}: no page scrolling`)
  return bounds
}

async function cell(page, name, point) {
  const board = target(page, name), box = await board.boundingBox()
  assert(box)
  const mirrored=await board.evaluate(el=>el.classList.contains('mirrored'))
  await board.click({ position: { x: (48 + (mirrored ? 11-point.x : point.x) * 48) / 624 * box.width, y: (48 + point.y * 48) / 624 * box.height } })
  if (await page.locator('.target-confirmation').count()) {
    await idle(page); await geometry(page, 'confirm-target')
    await page.getByRole('button', { name: '确认使用', exact: true }).click()
  }
}

async function enter(page) {
  await page.getByRole('button', { name: /^(开始教程|继续演习)$/ }).click()
  const fullscreen = page.getByRole('button', { name: '全屏并锁定横屏', exact: true })
  if (await fullscreen.isVisible()) await fullscreen.click({timeout:1500}).catch(async error=>{ if(!await page.evaluate(()=>Boolean(document.fullscreenElement))) throw error })
  await page.locator('.naval-tutorial-guide[data-device="mobile"]').waitFor()
  await idle(page)
  assert(await page.evaluate(() => Boolean(document.fullscreenElement)), 'actual mobile fullscreen is active')
}

try {
  const desktopProgress = createNavalTutorial(1000); desktopProgress.step = NAVAL_TUTORIAL_LESSONS.findIndex(step => step.id === 'layout')
  const formal = JSON.stringify(createLocalGame()), history = JSON.stringify([{ id: 'prior-real-game', won: true }])
  const desktopSaved = JSON.stringify(desktopProgress)
  const context = await browser.newContext({ viewport: { width: 844, height: 390 }, isMobile: true, hasTouch: true, deviceScaleFactor: 1 })
  await context.addInitScript(({ formal, history, desktopSaved, desktopKey }) => {
    if (sessionStorage.getItem('mobile-training-seeded')) return
    localStorage.setItem('naval-guest-session', formal); localStorage.setItem('naval-guest-history', history); localStorage.setItem(desktopKey, desktopSaved)
    sessionStorage.setItem('mobile-training-seeded', '1')
  }, { formal, history, desktopSaved, desktopKey: NAVAL_TUTORIAL_STORAGE_KEY })
  const page = await context.newPage()
  page.setDefaultTimeout(20000); page.on('pageerror', error => errors.push(error.message))
  await page.goto(base + gamePath)
  await page.getByRole('heading', { name: '海战棋手机教程', exact: true }).waitFor()
  await enter(page)
  assert.equal((await read(page)).step, 0, 'a progressed desktop tutorial does not skip mobile welcome')
  await geometry(page, 'welcome-844')
  await page.screenshot({ path: path.join(output, 'mobile-tutorial-welcome-844.png') })
  await page.setViewportSize({ width: 568, height: 320 })
  await geometry(page, 'welcome-568')
  await page.screenshot({ path: path.join(output, 'mobile-tutorial-welcome-568.png') })
  const visited = [], toggles = [], seen = new Set()
  let resumed = false
  const resumedPanels = new Set()
  for (let guard = 0; guard < 100; guard++) {
    await idle(page)
    let session = await read(page), step = getNavalTutorialStep(session)
    if (visited.at(-1) !== step.id) visited.push(step.id)
    await geometry(page, `${step.id}-${step.target}`)
    if (!seen.has(step.id) && ['move', 'select-shot', 'radar', 'fighter'].includes(step.id)) {
      seen.add(step.id)
      await page.screenshot({ path: path.join(output, `mobile-tutorial-${step.id}-568.png`) })
      await page.setViewportSize({ width: 844, height: 390 })
      await geometry(page, `${step.id}-844`)
      await page.screenshot({ path: path.join(output, `mobile-tutorial-${step.id}-844.png`) })
      await page.setViewportSize({ width: 568, height: 320 }); await idle(page)
    }
    const resumePanel = step.id === 'clear' && session.flags.openPanel === 'tools' && !resumedPanels.has('tools') ? 'tools' : step.id === 'select-shot' && session.flags.openPanel === 'weapons' && !resumedPanels.has('weapons') ? 'weapons' : null
    if (resumePanel) {
      await page.getByRole('button', { name: '退出教程，保留进度', exact: true }).click()
      await page.getByRole('heading', { name: '选择出航方式', exact: true }).waitFor()
      assert.equal((await read(page)).flags.openPanel, resumePanel)
      await enter(page)
      session = await read(page); step = getNavalTutorialStep(session)
      assert.equal(session.flags.openPanel, resumePanel, 'initialization must not overwrite saved tools or combat weapons')
      await target(page, step.target).waitFor()
      resumedPanels.add(resumePanel)
    }
    if (step.id === 'buy-radar' && session.flags.openPanel === 'supply' && !resumed) {
      await page.getByRole('button', { name: '退出教程，保留进度', exact: true }).click()
      await page.getByRole('heading', { name: '选择出航方式', exact: true }).waitFor()
      const saved = await read(page)
      assert.equal(saved.flags.openPanel, 'supply')
      await enter(page)
      session = await read(page); step = getNavalTutorialStep(session)
      assert.equal(step.id, 'buy-radar')
      assert.equal(session.flags.openPanel, 'supply')
      await target(page, 'naval-buy-radar').waitFor()
      resumed = true
    }
    if (session.completed) break
    const previous = JSON.stringify(session)
    if (step.target.startsWith('naval-mobile-')) { toggles.push(step.target); await target(page, step.target).click() }
    else if (step.mode === 'read') await page.locator('.naval-tutorial-guide .guide-primary').click()
    else if (step.id === 'move' && !session.flags.shipSelected) {
      await target(page, 'naval-fleet-roster').locator('button').first().click()
      assert.equal((await read(page)).flags.openPanel, 'fleet', 'the fixed roster remains visible after selection')
    } else if (step.cell && ['naval-own-board', 'naval-enemy-board'].includes(step.target)) await cell(page, step.target, step.cell)
    else {
      await target(page, step.target).click()
      if (step.target.startsWith('naval-weapon-')) assert.equal((await read(page)).flags.openPanel, null, 'combat weapon selection returns both seas to view')
      if (['naval-buy-mine', 'naval-buy-antiAir'].includes(step.target)) assert.equal((await read(page)).flags.openPanel, 'supply', 'the right-side shop stays visible beside the board')
    }
    await page.waitForFunction(({ key, previous }) => JSON.stringify(JSON.parse(localStorage.getItem(key))) !== previous, { key: NAVAL_MOBILE_TUTORIAL_STORAGE_KEY, previous })
  }
  await idle(page)
  const complete = await read(page)
  assert.equal(complete.completed, true)
  assert.deepEqual(visited, NAVAL_MOBILE_TUTORIAL_LESSONS.map(step => step.id))
  assert(resumed, 'mobile open-panel progress can resume')
  assert.deepEqual([...resumedPanels].sort(), ['tools', 'weapons'], 'tool tab and combat weapons resume without losing their open state')
  for (const key of ['tools', 'weapons']) assert(toggles.includes(`naval-mobile-${key}-toggle`))
  assert(!toggles.includes('naval-mobile-supply-toggle'), 'the persistent shop needs no pointless opening step')
  assert(complete.room.events.some(event => event.cells.some(cell => cell.result === 'hit')))
  assert(complete.room.events.some(event => event.cells.some(cell => cell.result === 'mine')))
  assert(complete.room.events.some(event => event.weapon === 'radar'))
  assert(complete.room.events.some(event => event.weapon === 'fighter' && /击落/.test(event.text)))
  await page.screenshot({ path: path.join(output, 'mobile-tutorial-complete-568.png') })
  assert.deepEqual(await page.evaluate(key => ({ formal: localStorage.getItem('naval-guest-session'), history: localStorage.getItem('naval-guest-history'), desktop: localStorage.getItem(key) }), NAVAL_TUTORIAL_STORAGE_KEY), { formal, history, desktop: desktopSaved })
  checks.push(`all ${NAVAL_MOBILE_TUTORIAL_LESSONS.length} phone lessons`, 'persistent right-side fleet and shop with tools tab guidance', 'collapsed combat weapons open and close', '568x320 and 844x390 guide fit', 'no guide-target overlap or document scrolling', 'persistent shop resume', 'isolated real sessions and desktop progress')

  const desktop = await browser.newContext({ viewport: { width: 1440, height: 900 } })
  const mobileSaved = JSON.stringify(complete)
  await desktop.addInitScript(({ desktopKey, desktopSaved, mobileKey, mobileSaved }) => { localStorage.setItem(desktopKey, desktopSaved); localStorage.setItem(mobileKey, mobileSaved) }, { desktopKey: NAVAL_TUTORIAL_STORAGE_KEY, desktopSaved, mobileKey: NAVAL_MOBILE_TUTORIAL_STORAGE_KEY, mobileSaved })
  const pc = await desktop.newPage(); pc.on('pageerror', error => errors.push(error.message))
  await pc.goto(base + gamePath)
  await pc.getByRole('heading', { name: '海战棋电脑教程', exact: true }).waitFor()
  await pc.getByRole('button', { name: '继续演习', exact: true }).click()
  await pc.locator('.naval-tutorial-guide[data-step="layout"][data-device="desktop"]').waitFor()
  assert.equal(await pc.locator('.naval-tutorial-guide .guide-meta>span').textContent(), `6 / ${NAVAL_TUTORIAL_LESSONS.length}`)
  assert.equal(await pc.evaluate(key => localStorage.getItem(key), NAVAL_MOBILE_TUTORIAL_STORAGE_KEY), mobileSaved)
  assert.deepEqual(errors, [])
  checks.push(`desktop retains its independent ${NAVAL_TUTORIAL_LESSONS.length}-lesson path`)
  console.log(JSON.stringify({ passed: true, visited, checks, output }))
} catch (error) {
  for (const [index, context] of browser.contexts().entries()) for (const page of context.pages()) {
    await page.screenshot({ path: path.join(output, `mobile-tutorial-failure-${index}.png`) }).catch(() => {})
    console.error(JSON.stringify(await page.evaluate(() => ({ step: document.querySelector('.naval-tutorial-guide')?.dataset, guide: document.querySelector('.naval-tutorial-guide')?.textContent, error: document.querySelector('.naval-notice')?.textContent, panel: document.querySelector('.naval-page')?.className })).catch(() => null)))
  }
  throw error
} finally { await browser.close() }
