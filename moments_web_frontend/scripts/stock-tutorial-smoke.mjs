import assert from 'node:assert/strict'
import { mkdir } from 'node:fs/promises'
import path from 'node:path'
import { chromium } from 'playwright-core'
import { createTutorialSession, transitionTutorial, TUTORIAL_LESSONS, TUTORIAL_STORAGE_KEY } from '../src/games/guessStockTutorial.js'

const baseUrl = process.env.QA_BASE_URL || 'http://127.0.0.1:5174'
const outputDir = path.resolve(process.env.QA_OUTPUT_DIR || 'test-results/stock-tutorial')
await mkdir(outputDir, { recursive: true })
const browser = await chromium.launch({ executablePath: process.env.QA_CHROME_PATH || 'C:/Program Files/Google/Chrome/Application/chrome.exe', headless: true })
const screenshots = new Set(['intro-price', 'loan-input', 'r1-borrow-confirm', 'r1-bank-reopen', 'r1-buy-first', 'r1-price-components', 'r1-open-formula', 'r1-close-flow', 'r1-close-formula', 'r2-hold', 'r3-open', 'complete'])

async function settled(page, id) {
  await page.waitForFunction(expected => {
    const guide = document.querySelector('.tutorial-guide')
    const frame = document.querySelector('.tutorial-highlight')
    return guide?.dataset.step === expected && frame?.dataset.moving === 'false'
      && document.querySelector('[data-tutorial-active="true"]')?.dataset.tutorial === frame.dataset.target
  }, id, { timeout: 10000 })
}

async function layout(page, id) {
  const problems = await page.evaluate(() => {
    const issues = []
    const guide = document.querySelector('.tutorial-guide').getBoundingClientRect()
    const frame = document.querySelector('.tutorial-highlight').getBoundingClientRect()
    const target = document.querySelector('[data-tutorial-active="true"]').getBoundingClientRect()
    if (document.documentElement.scrollWidth > innerWidth + 1) issues.push('horizontal overflow')
    if (guide.bottom > innerHeight + 1 || guide.top < 0) issues.push('guide outside viewport')
    if (frame.width < 4 || frame.height < 4 || frame.bottom > guide.top + 2) issues.push('focus frame missing or overlapping guide')
    if (Math.abs((frame.left + frame.right) / 2 - (target.left + target.right) / 2) > 8) issues.push('focus frame not aligned horizontally')
    if (Math.abs((frame.top + frame.bottom) / 2 - (target.top + target.bottom) / 2) > 15) issues.push('focused target clipped vertically')
    for (const dialog of document.querySelectorAll('.overlay .dialog')) {
      if (!dialog.getClientRects().length) continue
      const rect = dialog.getBoundingClientRect()
      if (rect.bottom > guide.top + 1 || rect.top < 0) issues.push('dialog overlaps guide or viewport')
    }
    for (const text of document.querySelectorAll('.tutorial-guide button,.tutorial-pricing dd,.final-result > div')) {
      if (text.scrollWidth > text.clientWidth + 2) issues.push('clipped control or result text')
    }
    return issues
  })
  if (problems.length) {
    await page.screenshot({ path: path.join(outputDir, `${id}-failure.png`) })
    const geometry = await page.evaluate(() => Object.fromEntries(['.tutorial-guide', '.tutorial-highlight', '[data-tutorial-active="true"]'].map(selector => [selector, document.querySelector(selector).getBoundingClientRect().toJSON()])))
    throw new Error(`${id}: ${problems.join(', ')} ${JSON.stringify(geometry)}`)
  }
}

async function assertMovingFrame(page, before, label) {
  await page.waitForFunction(() => document.querySelector('.tutorial-highlight').getAnimations()
    .some(animation => animation.playState === 'running' && ['left', 'top', 'width', 'height'].includes(animation.transitionProperty)))
  await page.waitForTimeout(90)
  const middle = await page.locator('.tutorial-highlight').boundingBox()
  assert.ok(Math.abs(middle.x - before.x) + Math.abs(middle.y - before.y) + Math.abs(middle.width - before.width) > .5, `${label}: frame must move, not be replaced`)
  assert.equal(await page.evaluate(() => window.testFocusFrame === document.querySelector('.tutorial-highlight')), true)
  await page.screenshot({ path: path.join(outputDir, label + '-moving.png') })
}

try {
  for (const viewport of JSON.parse(process.env.QA_VIEWPORTS || '[{"width":1440,"height":1000},{"width":390,"height":844},{"width":360,"height":640}]')) {
    const context = await browser.newContext({ viewport })
    const page = await context.newPage()
    const errors = []
    const apiCalls = []
    page.on('pageerror', error => errors.push(error.message))
    page.on('request', request => { if (new URL(request.url()).pathname.startsWith('/api/entertainment')) apiCalls.push(request.url()) })
    await page.goto(`${baseUrl}/customer/entertainment/tutorial`, { waitUntil: 'networkidle' })
    await page.evaluate(() => { localStorage.setItem('formal_session_sentinel', 'unchanged'); localStorage.setItem('guess_stock_tutorial_v1', 'retained-old-lesson'); window.testFocusFrame = document.querySelector('.tutorial-highlight') })
    for (const lesson of TUTORIAL_LESSONS) {
      await settled(page, lesson.id)
      await layout(page, `${viewport.width}-${lesson.id}`)
      if (screenshots.has(lesson.id)) await page.screenshot({ path: path.join(outputDir, `${viewport.width}-${lesson.id}.png`) })
      const target = page.locator(`[data-tutorial="${lesson.target}"][data-tutorial-active="true"]`)
      if (lesson.id === 'complete') break
      if (lesson.id === 'intro-price') {
        await page.getByTitle('规则', { exact: true }).click()
        assert.equal(await page.locator('.tutorial-guide').getAttribute('data-step'), 'intro-price', 'unrelated controls cannot skip a focused lesson')
        assert.equal(await page.getByRole('heading', { name: '快速开始', exact: true }).count(), 0)
      }
      if (lesson.mode !== 'action') {
        if (lesson.id === 'loan-input') {
          await target.fill('1000')
          assert.equal(await page.locator('.tutorial-guide').getAttribute('data-step'), 'loan-input', 'typing must not change focus before confirmation')
        }
        const before = await page.locator('.tutorial-highlight').boundingBox()
        await page.locator('.guide-primary').click()
        if (lesson.id === 'intro-price' || lesson.id === 'loan-input') await assertMovingFrame(page, before, `${viewport.width}-${lesson.id}`)
      } else if (lesson.id === 'start') await page.getByRole('button', { name: '开始练习', exact: true }).click()
      else await target.click()
      if (lesson.id === 'r1-buy-first') {
        await settled(page, 'r1-pending')
        await page.reload({ waitUntil: 'networkidle' })
        await settled(page, 'r1-pending')
        assert.equal(await page.locator('.quantity-field input').inputValue(), '25')
        await page.evaluate(() => window.testFocusFrame = document.querySelector('.tutorial-highlight'))
      }
    }
    const result = await page.evaluate(key => ({ session: JSON.parse(localStorage.getItem(key)), sentinel: localStorage.getItem('formal_session_sentinel'), old: localStorage.getItem('guess_stock_tutorial_v1') }), TUTORIAL_STORAGE_KEY)
    assert.equal(result.session.status, 'COMPLETED')
    assert.equal(result.session.rounds.length, 3)
    assert.equal(result.session.credit.effectiveDebt, 0)
    assert.equal(result.session.recentTrades.length, 5)
    assert.ok(result.session.rounds[1].openingOrderFlowChangePercent > result.session.rounds[0].openingOrderFlowChangePercent)
    assert.equal(result.session.rounds[2].openingOrderFlowChangePercent, 0)
    assert.equal(result.sentinel, 'unchanged')
    assert.equal(result.old, 'retained-old-lesson')
    assert.equal(result.session.tutorial.bankClosed, true)
    assert.equal(result.session.tutorial.bankReopened, true)
    assert.equal(await page.locator('.tutorial-pricing').count(), 0)
    assert.deepEqual(apiCalls, [])
    assert.deepEqual(errors, [])
    await page.locator('.tutorial-guide').getByRole('button', { name: '重新开始教程', exact: true }).click()
    await page.getByRole('button', { name: '确认重新开始', exact: true }).click()
    await settled(page, 'intro-price')
    console.log(`${viewport.width}x${viewport.height}: ${TUTORIAL_LESSONS.length} steps, actual movement, focus alignment, pricing, reload, isolation and restart passed`)
    await context.close()
  }

  const reduced = await browser.newPage({ viewport: { width: 390, height: 844 }, reducedMotion: 'reduce' })
  await reduced.goto(`${baseUrl}/customer/entertainment/tutorial`, { waitUntil: 'networkidle' })
  await settled(reduced, 'intro-price')
  assert.ok(await reduced.locator('.tutorial-highlight').evaluate(element => getComputedStyle(element).transitionDuration.split(',').every(duration => parseFloat(duration) <= .001)))
  await reduced.locator('.guide-primary').click()
  await settled(reduced, 'intro-cash')
  await reduced.keyboard.press('Tab')
  assert.equal(await reduced.evaluate(() => Boolean(document.activeElement.closest('.tutorial-guide'))), true)
  await reduced.close()
  console.log('Reduced-motion preference and keyboard focus containment passed.')

  const hub = await browser.newPage({ viewport: { width: 390, height: 844 }, reducedMotion: 'reduce' })
  await hub.route('**/api/**', route => {
    const pathname = new URL(route.request().url()).pathname
    if (!pathname.startsWith('/api/')) return route.continue()
    return route.fulfill({ contentType: 'application/json', body: JSON.stringify(pathname.endsWith('/guests')
      ? { code: 0, data: { token: 'tutorial-test-only', displayName: '练习访客' } } : { code: 500, message: 'Unavailable' }) })
  })
  await hub.goto(`${baseUrl}/customer/entertainment`, { waitUntil: 'networkidle' })
  await hub.locator('.tutorial-entry').getByRole('link', { name: '从零开始练习' }).click()
  await settled(hub, 'intro-price')
  await hub.locator('.guide-primary').click()
  await settled(hub, 'intro-cash')
  await hub.getByRole('button', { name: '退出教程，保留进度', exact: true }).click()
  await hub.locator('.tutorial-entry').getByRole('link', { name: '继续新手教程' }).click()
  await settled(hub, 'intro-cash')
  await hub.close()
  console.log('Backend-unavailable entry and exit/resume passed; hub APIs mocked.')

  let live = transitionTutorial(transitionTutorial(createTutorialSession(), 'READY_NEXT_ROUND'), 'ACK_EVENT')
  live.id = 987; live.gameId = 987; live.game = { stockCode: 'SIM', title: '正式游戏接口回归' }
  live.tutorial.researched = true; live.tutorial.cancelled = true
  const liveCalls = []
  const livePage = await browser.newPage({ viewport: { width: 1440, height: 1000 } })
  await livePage.addInitScript(() => localStorage.setItem('entertainment_guest_token', 'live-regression-only'))
  await livePage.route('**/api/**', route => {
    const pathname = new URL(route.request().url()).pathname
    if (!pathname.startsWith('/api/')) return route.continue()
    liveCalls.push(pathname)
    if (pathname.endsWith('/credit/borrow')) live = transitionTutorial(live, 'BORROW', route.request().postDataJSON())
    if (pathname.endsWith('/credit/repay')) live = transitionTutorial(live, 'REPAY', route.request().postDataJSON())
    if (pathname.endsWith('/actions')) { const payload = route.request().postDataJSON(); live = transitionTutorial(live, payload.actionType, payload) }
    return route.fulfill({ contentType: 'application/json', body: JSON.stringify({ code: 0, data: live }) })
  })
  await livePage.goto(`${baseUrl}/customer/entertainment/sessions/987/play`, { waitUntil: 'networkidle' })
  assert.equal(await livePage.locator('.tutorial-guide').count(), 0)
  await livePage.locator('.credit-amount input').fill('1000')
  for (const name of ['确认借款', '完成融资，进入开盘前']) await livePage.getByRole('button', { name, exact: true }).click()
  await livePage.locator('.order-panel').getByRole('button', { name: '25%', exact: true }).click()
  await livePage.getByRole('button', { name: '撤销订单', exact: true }).waitFor()
  for (const name of ['完成开盘前操作', '进入收盘前', '我已知晓', '全部']) await livePage.getByRole('button', { name, exact: true }).click()
  await livePage.getByRole('button', { name: '撤销订单', exact: true }).waitFor()
  for (const name of ['完成收盘前操作', '进入银行还贷期']) await livePage.getByRole('button', { name, exact: true }).click()
  await livePage.locator('.credit-quick').getByRole('button', { name: '100%', exact: true }).click()
  await livePage.getByRole('button', { name: '确认还款', exact: true }).click()
  for (const endpoint of ['credit/borrow', 'credit/repay', 'actions']) assert.ok(liveCalls.includes(`/api/entertainment/sessions/987/${endpoint}`))
  assert.equal(await livePage.evaluate(key => localStorage.getItem(key), TUTORIAL_STORAGE_KEY), null)
  await livePage.close()
  console.log('Live-session controls still use live APIs; all regression traffic mocked.')
} finally { await browser.close() }
