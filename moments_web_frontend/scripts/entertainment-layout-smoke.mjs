import { chromium } from 'playwright-core'
import { mkdir } from 'node:fs/promises'
import path from 'node:path'

const token = process.env.QA_CUSTOMER_TOKEN
const user = JSON.parse(process.env.QA_CUSTOMER_USER || 'null')
if (!token || !user) throw new Error('QA_CUSTOMER_TOKEN and QA_CUSTOMER_USER are required')

const baseUrl = process.env.QA_BASE_URL || 'http://127.0.0.1:5174'
const gameId = process.env.QA_GAME_ID || '8'
const outputDir = process.env.QA_OUTPUT_DIR || path.resolve('test-results/entertainment-layout')
await mkdir(outputDir, { recursive: true })

const browser = await chromium.launch({
  executablePath: 'C:/Program Files/Google/Chrome/Application/chrome.exe',
  headless: true
})

try {
  const page = await browser.newPage({ viewport: { width: 1440, height: 1000 } })
  await page.addInitScript(({ savedToken, savedUser }) => {
    localStorage.setItem('token', savedToken)
    localStorage.setItem('user', JSON.stringify(savedUser))
  }, { savedToken: token, savedUser: user })

  await page.goto(`${baseUrl}/customer/entertainment/games/${gameId}/edit`, { waitUntil: 'networkidle' })
  await page.getByRole('heading', { name: '编辑股票游戏' }).waitFor()

  const priceEngine = page.locator('.form-group').filter({ hasText: '价格引擎' }).locator('.custom-select-trigger')
  await priceEngine.click()
  await page.getByRole('button', { name: '市场趋势 V2', exact: true }).click()
  if (await page.getByText('公司基本面与财报').count()) throw new Error('V2 still shows financial report settings')
  await page.screenshot({ path: path.join(outputDir, 'editor-v2-desktop.png'), fullPage: true })

  await page.getByRole('button', { name: '银行融资', exact: true }).click()
  const creditMode = page.locator('.form-group').filter({ hasText: '贷款交互' }).locator('.custom-select-trigger')
  await creditMode.click()
  await page.getByRole('button', { name: '开盘内融资', exact: true }).click()
  await page.getByText('与开盘交易共用倒计时').waitFor()
  if (await page.getByText('贷款阶段倒计时', { exact: true }).count()) throw new Error('Inline credit still shows a separate loan timer')
  if (!await page.getByText('还贷阶段倒计时', { exact: true }).count()) throw new Error('Repayment timer disappeared in inline mode')
  await page.screenshot({ path: path.join(outputDir, 'editor-inline-credit.png'), fullPage: true })

  await creditMode.click()
  await page.getByRole('button', { name: '独立银行阶段', exact: true }).click()
  await page.getByText('贷款阶段倒计时', { exact: true }).waitFor()
  await page.getByText('贷款阶段倒计时', { exact: true }).locator('..').locator('input[type=checkbox]').check()
  await page.getByLabel('贷款阶段秒数').waitFor()

  await page.setViewportSize({ width: 390, height: 844 })
  await page.reload({ waitUntil: 'networkidle' })
  if (await page.evaluate(() => document.documentElement.scrollWidth > innerWidth)) throw new Error('Mobile editor overflows horizontally')
  await page.screenshot({ path: path.join(outputDir, 'editor-mobile.png'), fullPage: true })

  await page.setViewportSize({ width: 1440, height: 1000 })
  await page.goto(`${baseUrl}/customer/entertainment/games/${gameId}`, { waitUntil: 'networkidle' })
  await page.getByRole('heading', { name: '长兴存储' }).waitFor()
  if (await page.evaluate(() => document.documentElement.scrollWidth > innerWidth)) throw new Error('Desktop detail overflows horizontally')
  await page.screenshot({ path: path.join(outputDir, 'detail-desktop.png'), fullPage: true })

  await page.setViewportSize({ width: 390, height: 844 })
  await page.reload({ waitUntil: 'networkidle' })
  const mobileOverflow = await page.evaluate(() => ({
    pageWidth: document.documentElement.scrollWidth,
    viewportWidth: innerWidth,
    offenders: [...document.querySelectorAll('body *')]
      .map(element => ({ element, rect: element.getBoundingClientRect() }))
      .filter(({ rect }) => rect.right > innerWidth + 1 || rect.left < -1)
      .slice(0, 8)
      .map(({ element, rect }) => ({ tag: element.tagName, className: element.className, left: rect.left, right: rect.right, width: rect.width }))
  }))
  if (mobileOverflow.pageWidth > mobileOverflow.viewportWidth) throw new Error(`Mobile detail overflows horizontally: ${JSON.stringify(mobileOverflow)}`)
  const actionBox = await page.locator('.primary-action').boundingBox()
  if (!actionBox || actionBox.x < 0 || actionBox.x + actionBox.width > 390) throw new Error('Mobile actions do not fit viewport')
  await page.screenshot({ path: path.join(outputDir, 'detail-mobile.png'), fullPage: true })

  console.log(JSON.stringify({ gameId, outputDir }))
} finally {
  await browser.close()
}
