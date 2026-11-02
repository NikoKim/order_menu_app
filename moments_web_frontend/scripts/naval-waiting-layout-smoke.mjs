import assert from 'node:assert/strict'
import { chromium } from 'playwright-core'
const base = process.env.QA_BASE_URL || 'http://127.0.0.1:5174'
const browser = await chromium.launch({ executablePath: 'C:/Program Files/Google/Chrome/Application/chrome.exe', headless: true })
try {
  for (const mobile of [true, false]) {
  const viewport = mobile ? { width: 568, height: 320 } : { width: 1366, height: 900 }
  const context = await browser.newContext({ viewport, isMobile: mobile, hasTouch: mobile })
  const page = await context.newPage(), errors = []
  page.on('pageerror', error => errors.push(error.message))
  await page.goto(base+'/customer/entertainment/naval')
  await page.getByRole('switch').uncheck()
  await page.getByRole('button', { name: '创建邀请房间', exact: true }).click()
  await page.locator('.waiting-dock').waitFor()
  await page.locator('.launch-overlay,.match-presentation').waitFor({ state: 'hidden' })
  await page.waitForTimeout(450)
  const header = await page.locator('.naval-header').boundingBox(), dock = await page.locator('.waiting-dock').boundingBox()
  assert(header.height >= 35 && header.height <= 46 && (!mobile || header.y <= 5), 'waiting page reserves a compact top operation row')
  assert.deepEqual(await page.locator('.naval-header button').evaluateAll(buttons => buttons.map(button => button.getAttribute('aria-label'))), ['战局聊天', '对局菜单', '展开观众席'], 'only chat, more and audience appear in the toolbar')
  assert.equal(await page.locator('.naval-header .naval-title,.naval-header .match-status,.mobile-waiting-actions').count(), 0, 'no title, waiting status or duplicate floating actions')
  assert.equal(await page.locator('.naval-header').evaluate(element => getComputedStyle(element).borderBottomWidth), '0px', 'toolbar must not divide the image with a line')
  assert(dock.y >= header.y+header.height && dock.y+dock.height <= viewport.height, 'waiting scene starts below its controls and fits the screen')
  await page.screenshot({ path: `../.agents/naval-qa/waiting-operation-row${mobile ? '' : '-desktop'}.png` })
  await page.getByRole('button', { name: '战局聊天', exact: true }).click(); await page.getByLabel('战局消息', { exact: true }).waitFor()
  await page.getByRole('button', { name: '收起聊天', exact: true }).click()
  await page.getByRole('button', { name: '展开观众席', exact: true }).click(); await page.getByRole('dialog', { name: '观众席', exact: true }).waitFor()
  await page.getByRole('dialog', { name: '观众席', exact: true }).getByRole('button', { name: '收起面板', exact: true }).click()
  await page.getByRole('button', { name: '对局菜单', exact: true }).click()
  for (const name of ['分享房间', '关闭音效', '对局规则', '全屏', '离开对局']) assert.equal(await page.getByRole('button', { name, exact: true }).count(), 1, 'more menu exposes each action once')
  await page.getByRole('button', { name: '离开对局', exact: true }).click()
  await page.getByRole('button', { name: '确认离开', exact: true }).click()
  await page.getByRole('heading', { name: '选择出航方式', exact: true }).waitFor()
  assert.deepEqual(errors, [])
  await context.close()
  }
  console.log(JSON.stringify({ passed: true, compactWaitingHeader: true, reservedOperationRow: true }))
} finally { await browser.close() }
