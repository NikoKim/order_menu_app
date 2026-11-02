import assert from 'node:assert/strict'
import { mkdir } from 'node:fs/promises'
import path from 'node:path'
import { chromium } from 'playwright-core'
const base = process.env.QA_BASE_URL || 'http://127.0.0.1:5174'
const output = path.resolve('../.agents/naval-qa')
const browser = await chromium.launch({ executablePath: 'C:/Program Files/Google/Chrome/Application/chrome.exe', headless: true })
const errors = []
await mkdir(output, { recursive: true })
try {
  for (const viewport of [{ width: 1920, height: 900 }, { width: 390, height: 844 }]) {
    const context = await browser.newContext({ viewport, hasTouch: viewport.width < 600, isMobile: viewport.width < 600 })
    const page = await context.newPage(); page.on('pageerror', error => errors.push(error.message))
    await page.goto(base + '/customer/entertainment')
    await page.locator('.ent-navigation').waitFor()
    assert.equal(await page.locator('.desktop-rail:visible,.mobile-primary-nav:visible').count(), 0)
    assert(await page.locator('.game-tabs a.active').filter({ hasText: '股票' }).count())
    const header = await page.locator('.ent-navigation').boundingBox()
    assert(header.y === 0 && header.height <= 53 && header.width <= viewport.width)
    assert.equal(await page.locator('.back-links a').count(), 2)
    const links = await page.locator('.ent-navigation a').evaluateAll(links => links.map(link => link.getBoundingClientRect().toJSON()))
    assert(links.every(box => box.left >= 0 && box.right <= viewport.width && box.bottom <= header.height + 1))
    await page.screenshot({ path: path.join(output, `entertainment-stock-${viewport.width}.png`) })
    await page.getByRole('navigation', { name: '娱乐类别' }).getByRole('link', { name: '海战棋', exact: true }).click()
    await page.getByRole('heading', { name: '选择出航方式', exact: true }).waitFor()
    assert(await page.locator('.game-tabs a.active').filter({ hasText: '海战棋' }).count())
    assert.equal(await page.locator('.ent-navigation').count(), 1)
    await page.screenshot({ path: path.join(output, `entertainment-naval-${viewport.width}.png`) })
    await page.locator('.back-links a').filter({ hasText: '动态' }).click()
    await page.waitForURL('**/customer/moments')
    assert.equal(await page.locator('.ent-navigation').count(), 0)
    await context.close()
  }
  assert.deepEqual(errors, [])
  console.log(JSON.stringify({ passed: true, checks: ['shared stock/naval tabs', 'active state', 'desktop/mobile header fit', 'no bottom entertainment navigation', 'return-to-moments arrow'], output }))
} finally { await browser.close() }
