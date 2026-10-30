import assert from 'node:assert/strict'
import { mkdir } from 'node:fs/promises'
import path from 'node:path'
import { chromium } from 'playwright-core'
import { createTutorialSession, transitionTutorial, TUTORIAL_STORAGE_KEY } from '../src/games/guessStockTutorial.js'

const baseUrl = process.env.QA_BASE_URL || 'http://127.0.0.1:5174'
const output = path.resolve(process.env.QA_OUTPUT_DIR || 'test-results/tutorial-restart')
await mkdir(output, { recursive: true })
const snapshots = []
let session = createTutorialSession()
const capture = () => snapshots.push(structuredClone(session))
const act = (action, payload) => { session = transitionTutorial(session, action, payload) }
capture()
act('READY_NEXT_ROUND'); capture()
act('ACK_EVENT'); capture()
act('BORROW', { amount: 1000 }); act('READY_NEXT_ROUND'); capture()
act('RESEARCH', { actionType: 'VERIFY_EVENT' })
act('PLACE_ORDER', { side: 'BUY', quantity: 25 }); act('CANCEL_ORDER')
act('PLACE_ORDER', { side: 'BUY', quantity: 25 }); act('READY_NEXT_ROUND'); capture()
act('READY_NEXT_ROUND'); capture()
act('ACK_EVENT'); capture()
act('PLACE_ORDER', { side: 'SELL', quantity: 25 }); act('READY_NEXT_ROUND'); capture()
act('READY_NEXT_ROUND'); capture()

const browser = await chromium.launch({ executablePath: 'C:/Program Files/Google/Chrome/Application/chrome.exe', headless: true })
async function settled(page, step) {
  await page.waitForFunction(expected => {
    const guide = document.querySelector('.tutorial-guide')
    return guide && (!expected || guide.dataset.step === expected)
      && document.querySelector('.tutorial-highlight')?.dataset.moving === 'false'
      && document.querySelector('[data-tutorial-active="true"]')?.getClientRects().length > 0
  }, step, { timeout: 10000 })
}
async function restartDialog(page) {
  await page.getByRole('heading', { name: '确认重新开始？', exact: true }).waitFor({ state: 'visible', timeout: 3000 })
  const button = page.getByRole('button', { name: '确认重新开始', exact: true })
  await button.click({ trial: true, timeout: 3000 })
  assert.equal(await page.locator('.tutorial-guide').count(), 0)
  const bounds = await button.boundingBox()
  assert.ok(bounds.y >= 0 && bounds.y + bounds.height <= page.viewportSize().height, 'restart command must be within viewport')
}
try {
  for (const viewport of [{ width: 1440, height: 1000 }, { width: 360, height: 640 }]) {
    const context = await browser.newContext({ viewport, reducedMotion: 'reduce' })
    const page = await context.newPage()
    const errors = []
    const calls = []
    page.on('pageerror', error => errors.push(error.message))
    await page.route('**/api/entertainment/**', route => {
      calls.push(route.request().url())
      return route.abort()
    })
    await page.goto(`${baseUrl}/customer/entertainment/tutorial`, { waitUntil: 'networkidle' })
    await page.evaluate(() => localStorage.setItem('formal_session_sentinel', 'untouched'))
    for (const snapshot of snapshots) {
      await page.evaluate(({ key, value }) => localStorage.setItem(key, JSON.stringify(value)), { key: TUTORIAL_STORAGE_KEY, value: snapshot })
      await page.reload({ waitUntil: 'networkidle' })
      await settled(page)
      if (snapshot.phase === 'OPENING_TRADING') {
        await page.locator('[data-tutorial="research-trigger"]').click()
        await settled(page, 'research-verify')
      }
      const step = await page.locator('.tutorial-guide').getAttribute('data-step')
      const guideRestart = page.getByRole('button', { name: '重新开始教程', exact: true })
      await guideRestart.click()
      await restartDialog(page)
      if (snapshot.phase === 'OPENING_RESULT_ACK') await page.screenshot({ path: path.join(output, `${viewport.width}-restart-confirm.png`) })
      await page.getByRole('button', { name: '取消', exact: true }).click()
      await settled(page, step)
      assert.equal(JSON.parse(await page.evaluate(key => localStorage.getItem(key), TUTORIAL_STORAGE_KEY)).phase, snapshot.phase)
      await guideRestart.click()
      await restartDialog(page)
      await page.getByRole('button', { name: '确认重新开始', exact: true }).click()
      await settled(page, 'intro-price')
      const saved = await page.evaluate(key => JSON.parse(localStorage.getItem(key)), TUTORIAL_STORAGE_KEY)
      assert.equal(saved.phase, 'ROUND_START')
      assert.equal(saved.participants[0].cash, 10000)
      assert.equal(saved.credit.effectiveDebt, 0)
      assert.deepEqual(saved.tutorial.lessons, [])
      await page.locator('.guide-primary').click()
      await settled(page, 'intro-cash')
      console.log(`${viewport.width}px ${snapshot.phase}: restart, cancel, reset and resumed interaction passed`)
    }
    assert.equal(await page.evaluate(() => localStorage.getItem('formal_session_sentinel')), 'untouched')
    assert.deepEqual(calls, [])
    assert.deepEqual(errors, [])
    await context.close()
  }
} finally { await browser.close() }
