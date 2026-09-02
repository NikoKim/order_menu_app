import { chromium } from 'playwright-core'
import { mkdir } from 'node:fs/promises'
import path from 'node:path'

const baseUrl = process.env.QA_BASE_URL || 'http://127.0.0.1:5174'
const outputDir = process.env.QA_OUTPUT_DIR || path.resolve('test-results/entertainment-notice')
await mkdir(outputDir, { recursive: true })

const browser = await chromium.launch({
  executablePath: 'C:/Program Files/Google/Chrome/Application/chrome.exe',
  headless: true
})

try {
  const page = await browser.newPage({ viewport: { width: 1280, height: 800 } })
  await page.route('**/api/__notice_qa__', route => route.fulfill({
    status: 500,
    contentType: 'application/json',
    body: JSON.stringify({ code: 500, message: 'Internal Server Error: SQLSyntaxError' })
  }))

  await page.goto(`${baseUrl}/customer/entertainment`, { waitUntil: 'networkidle' })
  await page.getByRole('button', { name: '玩法说明' }).click()
  await page.getByRole('heading', { name: '快速开始' }).waitFor()
  const manualChecks = [
    ['创建游戏', '股票信息与发布'],
    ['银行融资', '开盘内融资'],
    ['AI 策略', 'AI 难度'],
    ['定价参数', '推荐组合']
  ]
  for (const [tab, text] of manualChecks) {
    await page.getByRole('button', { name: tab, exact: true }).click()
    await page.getByText(text, { exact: true }).first().waitFor()
  }
  await page.getByRole('button', { name: '快速开始', exact: true }).click()

  await page.evaluate(async () => {
    const [{ default: client }, { notify }] = await Promise.all([
      import('/src/api/client.js'),
      import('/src/services/notice.js')
    ])
    try {
      await client.get('/__notice_qa__')
    } catch (error) {
      notify(error.message, 'error', 10000)
    }
  })

  const toast = page.locator('.notice-toast').last()
  await toast.waitFor()
  await page.waitForTimeout(350)
  const message = (await toast.textContent())?.trim() || ''
  if (message !== '服务暂时无法完成该操作，请稍后重试') {
    throw new Error(`Unexpected public error message: ${message}`)
  }
  if (/500|status code|internal server|sql/i.test(message)) {
    throw new Error(`Technical error details leaked: ${message}`)
  }

  const layers = await page.evaluate(() => {
    const toastElement = document.querySelector('.notice-stack')
    const toastItem = document.querySelector('.notice-toast:last-child')
    const manualElement = document.querySelector('.manual-mask')
    return {
      toast: Number(getComputedStyle(toastElement).zIndex),
      toastOpacity: Number(getComputedStyle(toastItem).opacity),
      toastRect: toastItem.getBoundingClientRect().toJSON(),
      manual: Number(getComputedStyle(manualElement).zIndex),
      pageWidth: document.documentElement.scrollWidth,
      viewportWidth: innerWidth
    }
  })
  if (!(layers.toast > layers.manual)) throw new Error(`Toast is below the manual: ${JSON.stringify(layers)}`)
  if (layers.toastOpacity !== 1 || layers.toastRect.height < 40) throw new Error(`Toast is not visibly laid out: ${JSON.stringify(layers)}`)
  if (layers.pageWidth > layers.viewportWidth) throw new Error(`Notice causes horizontal overflow: ${JSON.stringify(layers)}`)

  await page.screenshot({ path: path.join(outputDir, 'notice-over-manual.png') })
  console.log(JSON.stringify({ message, layers, outputDir }))
} finally {
  await browser.close()
}
