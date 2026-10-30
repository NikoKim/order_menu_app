import assert from 'node:assert/strict'
import { mkdir } from 'node:fs/promises'
import path from 'node:path'
import { chromium } from 'playwright-core'

const baseUrl = process.env.QA_BASE_URL || 'http://127.0.0.1:5174'
const endpoint = `${baseUrl}/api/entertainment/ai-connection/test`
const response = await fetch(endpoint, { method: 'POST', headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ source: 'UNKNOWN' }), signal: AbortSignal.timeout(10000) })
assert.equal(response.status, 400, 'The running backend must expose the probe route; rebuild/restart it if this returns 500/404')
console.log('Live frontend proxy -> backend: probe route registered and request validation returns 400')
const missing = await fetch(`${baseUrl}/api/entertainment/nonexistent-connection-probe`, { signal: AbortSignal.timeout(10000) })
assert.equal(missing.status, 404, 'A missing API route must not become a generic server error')
console.log('Live backend: missing routes return 404 instead of a misleading 500')

// Opt in to an upstream request using an intentionally invalid key, never a saved/user key.
if (process.argv.includes('--provider')) {
  const output = path.resolve(process.env.QA_OUTPUT_DIR || 'test-results/ai-connection-live')
  await mkdir(output, { recursive: true })
  const browser = await chromium.launch({ executablePath: 'C:/Program Files/Google/Chrome/Application/chrome.exe', headless: true })
  try {
    const page = await browser.newPage({ viewport: { width: 1440, height: 1000 } })
    const errors = []
    const writes = []
    page.on('pageerror', error => errors.push(error.message))
    page.on('request', request => {
      const pathname = new URL(request.url()).pathname
      if (request.method() !== 'GET' && pathname.startsWith('/api/entertainment/')) writes.push(pathname)
    })
    await page.goto(`${baseUrl}/customer/entertainment/games/${process.env.QA_GAME_ID || 8}`, { waitUntil: 'networkidle' })
    await page.getByRole('button', { name: '开始游戏', exact: true }).click()
    await page.getByRole('button', { name: '使用本局自己的 AI Key', exact: true }).click()
    const modal = page.locator('.ai-dialog')
    await modal.locator('.custom-select-trigger').click()
    await page.locator('.custom-select-menu').getByRole('button', { name: 'DeepSeek', exact: true }).click()
    const model = modal.locator('label').filter({ has: page.locator('span', { hasText: /^模型$/ }) }).locator('input')
    assert.equal(await model.inputValue(), 'deepseek-flash')
    await modal.locator('input[type="password"]').fill('sk-local-connection-probe-not-a-valid-key')
    const resultResponse = page.waitForResponse(response => response.url() === endpoint && response.request().method() === 'POST')
    await page.getByRole('button', { name: '验证 API 连接', exact: true }).click()
    const actualResponse = await resultResponse
    assert.equal(actualResponse.status(), 200)
    const result = await actualResponse.json()
    assert.equal(result.code, 0)
    assert.equal(result.data.connected, false)
    assert.equal(result.data.message, 'API Key 无效或已过期', 'The deliberately invalid key should be rejected by DeepSeek, not by a missing local route')
    await page.getByText('API Key 无效或已过期', { exact: true }).waitFor()
    assert.deepEqual(errors, [])
    assert.ok(writes.every(pathname => ['/api/entertainment/guests', '/api/entertainment/ai-connection/test'].includes(pathname)),
      'The test must not save credentials, start a game, or change any account configuration')
    await page.screenshot({ path: path.join(output, 'desktop-real-deepseek-response.png') })
    await page.setViewportSize({ width: 390, height: 844 })
    await page.screenshot({ path: path.join(output, 'mobile-real-deepseek-response.png') })
    console.log('Unmocked browser -> frontend proxy -> backend -> DeepSeek: invalid test key correctly rejected and displayed')
    console.log('No user/saved key used; no game started and no AI configuration saved')
  } finally { await browser.close() }
}
