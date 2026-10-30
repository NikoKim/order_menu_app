import { chromium } from 'playwright-core'
import { mkdir } from 'node:fs/promises'
import path from 'node:path'

const baseUrl = process.env.QA_BASE_URL || 'http://127.0.0.1:5184'
const outputDir = process.env.QA_OUTPUT_DIR || path.resolve('test-results/ai-cost')
await mkdir(outputDir, { recursive: true })
const browser = await chromium.launch({ executablePath: 'C:/Program Files/Google/Chrome/Application/chrome.exe', headless: true })
const round = { roundNo: 180, previousClosePrice: 104, openPrice: 104.5, closePrice: 105, eventName: null,
  openingBuyQuantity: 30, closingSellQuantity: 15, clearedAt: '2026-10-22T12:00:00' }
const participants = Array.from({ length: 9 }, (_, i) => ({ id: i + 1, type: i ? 'AI' : 'PLAYER',
  displayName: i ? `AI ${i}` : '测试玩家', currentPlayer: i === 0, initialCash: 10000,
  cash: 10500 - i * 30, asset: 10500 - i * 30, quantity: 0, returnPercent: 5 - i * .3, finalRank: i + 1 }))
const fixture = { id: 951, gameId: 951, status: 'COMPLETED', phase: 'COMPLETED', currentRound: 180,
  game: { stockCode: 'SIM', title: '股票对局' },
  config: { roundCount: 180, initialCash: 10000, initialPrice: 100, progressionMode: 'AUTO',
    tradingMode: 'TARGET_AUCTION', loanEnabled: false, creditProducts: [], fundamentalEnabled: false },
  participants, currentRoundState: round, rounds: [round], recentTrades: [], playerTrades: [], aiDecisions: [],
  credit: { enabled: false, banks: [] }, room: null, finalAsset: 10500, profitAmount: 500, returnPercent: 5,
  serverTime: '2026-10-22T12:00:00',
  aiReviews: participants.slice(1).map(item => ({ participantId: item.id, participantName: item.displayName,
    finalRank: item.finalRank, returnPercent: item.returnPercent, bestDecision: null, biggestMistake: null,
    thesisReplacementCount: 8, reverseTradeCount: 12, modelCallCount: 8, fallbackCount: 0,
    promptTokens: 20800, promptCacheHitTokens: 14400, completionTokens: 800, estimatedCostYuan: .08352,
    totalInterestCost: 0, forcedLiquidationShares: 0 })) }

try {
  for (const viewport of [{ width: 1440, height: 1000 }, { width: 390, height: 844 }]) {
    let currentFixture = fixture
    const page = await browser.newPage({ viewport })
    const errors = []
    page.on('pageerror', error => errors.push(error.message))
    await page.addInitScript(() => localStorage.setItem('entertainment_guest_token', 'fixture-only'))
    // Intercept every API request: this QA never creates games or calls a paid AI provider.
    await page.route('**/api/**', route => {
      if (!new URL(route.request().url()).pathname.startsWith('/api/')) return route.continue()
      return route.fulfill({ status: 200, contentType: 'application/json',
        body: JSON.stringify({ code: 0, data: route.request().url().includes('/entertainment/sessions/') ? currentFixture : {} }) })
    })
    await page.goto(`${baseUrl}/customer/entertainment/sessions/951/play`, { waitUntil: 'networkidle' })
    try { await page.getByRole('heading', { name: 'AI 决策复盘' }).waitFor({ timeout: 8000 }) }
    catch (error) {
      await page.screenshot({ path: path.join(outputDir, 'failure.png'), fullPage: true })
      throw new Error(JSON.stringify({ errors, body: (await page.locator('body').innerText()).slice(0, 1500), cause: error.message }))
    }
    if (await page.locator('.ai-review-stats').count() !== 8) throw new Error('Missing AI usage rows')
    if (!(await page.locator('.ai-review').innerText()).includes('0.6682 元')) throw new Error('Incorrect total estimate')
    const overflow = await page.evaluate(() => ({ horizontal: document.documentElement.scrollWidth > innerWidth + 1,
      clipped: [...document.querySelectorAll('.ai-review-stats span')].filter(element => element.scrollWidth > element.clientWidth + 1).length }))
    if (overflow.horizontal || overflow.clipped || errors.length) throw new Error(JSON.stringify({ overflow, errors }))
    await page.screenshot({ path: path.join(outputDir, `ai-cost-${viewport.width}.png`), fullPage: true })
    currentFixture = { ...fixture, aiReviews: fixture.aiReviews.map((review, index) => index ? review : { ...review, estimatedCostYuan: null }) }
    await page.reload({ waitUntil: 'networkidle' })
    await page.getByText('历史模型费用未统计', { exact: true }).waitFor()
    await page.close()
  }
  console.log('AI cost UI: desktop/mobile passed; all API traffic mocked.')
} finally { await browser.close() }
