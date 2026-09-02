import { chromium } from 'playwright-core'
import { mkdir } from 'node:fs/promises'
import path from 'node:path'

const baseUrl = process.env.QA_BASE_URL || 'http://127.0.0.1:5174'
const outputDir = process.env.QA_OUTPUT_DIR || path.resolve('test-results/entertainment')
await mkdir(outputDir, { recursive: true })
const browser = await chromium.launch({ executablePath: 'C:/Program Files/Google/Chrome/Application/chrome.exe', headless: true })

try {
  const page = await browser.newPage({ viewport: { width: 1440, height: 1000 } })
  const consoleErrors = []
  let expectingBadRequest = false
  page.on('console', message => { if (message.type() === 'error' && !(expectingBadRequest && message.text().includes('400'))) consoleErrors.push(message.text()) })
  await page.goto(`${baseUrl}/customer/entertainment`, { waitUntil: 'networkidle' })
  await page.getByRole('heading', { name: '娱乐', exact: true }).waitFor()
  await page.getByRole('button', { name: '玩法说明' }).click()
  await page.getByRole('heading', { name: '快速开始' }).waitFor()
  await page.getByRole('button', { name: 'AI 策略' }).click()
  await page.getByRole('heading', { name: 'AI 配置与策略' }).waitFor()
  await page.screenshot({ path: path.join(outputDir, 'game-manual.png') })
  await page.getByTitle('关闭说明书').click()
  await page.screenshot({ path: path.join(outputDir, 'hub-desktop.png'), fullPage: true })

  const setup = await page.evaluate(async () => {
    const list = await fetch('/api/entertainment/games?page=1&pageSize=50').then(response => response.json())
    const game = list.data.records.find(item => !item.aiEnabled) || list.data.records[0]
    const aiGame = list.data.records.find(item => item.aiEnabled)
    if (!game) throw new Error('No public game available')
    return { gameId: game.id, gameAiEnabled: game.aiEnabled, aiGameId: aiGame?.id }
  })

  if (setup.aiGameId) {
    const optionalPage = await browser.newPage({ viewport: { width: 720, height: 800 } })
    await optionalPage.goto(`${baseUrl}/customer/entertainment/games/${setup.aiGameId}`, { waitUntil: 'networkidle' })
    const cleanedExistingSession = await optionalPage.evaluate(async gameId => {
      const token = localStorage.getItem('entertainment_guest_token')
      const headers = token ? { 'X-Guest-Token': token } : {}
      const detail = await fetch(`/api/entertainment/games/${gameId}`, { headers }).then(response => response.json())
      if (!detail.data?.resumableSessionId) return false
      await fetch(`/api/entertainment/sessions/${detail.data.resumableSessionId}`, { method: 'DELETE', headers })
      return true
    }, setup.aiGameId)
    if (cleanedExistingSession) await optionalPage.reload({ waitUntil: 'networkidle' })
    await optionalPage.getByRole('button', { name: '开始游戏', exact: true }).click()
    const optionalAi = optionalPage.getByText(/加入 \d+ 个 AI 对手/)
    await optionalAi.waitFor()
    await optionalAi.click()
    await optionalPage.getByRole('button', { name: '不使用 AI，开始本局' }).waitFor()
    await optionalPage.screenshot({ path: path.join(outputDir, 'optional-ai-modal.png') })
    const autonomousAi = await optionalPage.evaluate(async gameId => {
      const token = localStorage.getItem('entertainment_guest_token')
      const headers = { 'Content-Type': 'application/json', 'X-Guest-Token': token }
      const started = await fetch(`/api/entertainment/games/${gameId}/sessions`, {
        method: 'POST', headers, body: JSON.stringify({ useAi: true, playbackSpeed: 5, autoAdvance: false })
      }).then(response => response.json())
      if (started.code !== 0) throw new Error(started.message)
      const sessionId = started.data.id
      let snapshot = started.data
      const deadline = Date.now() + 25000
      while (Date.now() < deadline) {
        if (snapshot.aiDecisions?.some(item => item.status === 'EXECUTED')) break
        await new Promise(resolve => setTimeout(resolve, 300))
        snapshot = await fetch(`/api/entertainment/sessions/${sessionId}`, {
          headers: { 'X-Guest-Token': token }
        }).then(response => response.json()).then(result => result.data)
      }
      const executed = snapshot.aiDecisions?.find(item => item.status === 'EXECUTED')
      await fetch(`/api/entertainment/sessions/${sessionId}`, { method: 'DELETE', headers })
      return { aiCount: snapshot.participants?.filter(item => item.type === 'AI').length || 0, executed }
    }, setup.aiGameId)
    if (autonomousAi.aiCount < 1 || !autonomousAi.executed) throw new Error(`Autonomous AI task did not execute: ${JSON.stringify(autonomousAi)}`)
    if (!autonomousAi.executed.action || autonomousAi.executed.targetPositionPercent == null || !autonomousAi.executed.marketRegime) {
      throw new Error(`Autonomous AI decision is not structured: ${JSON.stringify(autonomousAi.executed)}`)
    }
    await optionalPage.close()
  }

  setup.sessionId = await page.evaluate(async ({ gameId, gameAiEnabled }) => {
    const token = localStorage.getItem('entertainment_guest_token')
    const headers = { 'Content-Type': 'application/json', 'X-Guest-Token': token }
    const detail = await fetch(`/api/entertainment/games/${gameId}`, { headers }).then(response => response.json())
    if (detail.data?.resumableSessionId) {
      await fetch(`/api/entertainment/sessions/${detail.data.resumableSessionId}`, { method: 'DELETE', headers })
    }
    const body = gameAiEnabled ? { useAi: false } : {}
    const result = await fetch(`/api/entertainment/games/${gameId}/sessions`, { method: 'POST', headers, body: JSON.stringify(body) }).then(response => response.json())
    if (result.code !== 0) throw new Error(result.message)
    return result.data.id
  }, setup)

  await page.goto(`${baseUrl}/customer/entertainment/sessions/${setup.sessionId}/play`, { waitUntil: 'networkidle' })
  const roundCurtain = page.locator('.round-curtain')
  await roundCurtain.waitFor({ state: 'visible' })
  await page.screenshot({ path: path.join(outputDir, 'round-start.png') })
  await roundCurtain.waitFor({ state: 'hidden', timeout: 4000 })
  const acknowledge = page.getByRole('button', { name: /我已知晓|等待其他玩家/ })
  async function acknowledgeEvent(capture = false) {
    const appeared = await acknowledge.waitFor({ state: 'visible', timeout: 2500 }).then(() => true).catch(() => false)
    if (!appeared) return
    if (capture) await page.screenshot({ path: path.join(outputDir, 'event-modal.png') })
    await acknowledge.click()
    await acknowledge.waitFor({ state: 'hidden' })
  }
  async function finishCreditStage() {
    const finish = page.getByRole('button', { name: '完成融资，进入开盘前', exact: true })
    const appeared = await finish.waitFor({ state: 'visible', timeout: 2500 }).then(() => true).catch(() => false)
    if (!appeared) return
    await page.screenshot({ path: path.join(outputDir, 'credit-stage.png') })
    await finish.click()
    await finish.waitFor({ state: 'hidden' })
  }
  async function finishRepaymentStage() {
    const finish = page.getByRole('button', { name: '完成还贷', exact: true })
    const appeared = await finish.waitFor({ state: 'visible', timeout: 2500 }).then(() => true).catch(() => false)
    if (!appeared) return
    await page.screenshot({ path: path.join(outputDir, 'repayment-stage.png') })
    await finish.click()
    await finish.waitFor({ state: 'hidden' })
  }
  await acknowledgeEvent(true)
  await finishCreditStage()
  await page.getByRole('heading', { name: /当前操作：(买入|卖出)/ }).waitFor()
  await page.getByRole('heading', { name: '当前操作：买入' }).waitFor()
  if (!await page.locator('.order-panel.buy-mode').count()) throw new Error('Opening order panel must clearly use buy mode')
  await page.getByRole('button', { name: '100%', exact: true }).waitFor()
  await page.getByRole('button', { name: '全部', exact: true }).waitFor()
  const configuredRounds = await page.evaluate(() => Number(document.querySelector('.round-meter span')?.textContent.match(/\/\s*(\d+)/)?.[1] || 0))
  if (configuredRounds > 1) {
    await page.getByRole('button', { name: '1回合', exact: true }).waitFor()
    await page.getByRole('button', { name: '2回合', exact: true }).waitFor()
  }
  if (configuredRounds >= 3) {
    await page.getByRole('button', { name: '3回合', exact: true }).waitFor()
  }
  if (configuredRounds >= 5) {
    await page.getByRole('button', { name: '5回合', exact: true }).waitFor()
    if (configuredRounds >= 10) await page.getByRole('button', { name: '10回合', exact: true }).waitFor()
  }
  const mineCount = await page.locator('.rank-row.mine').count()
  if (mineCount !== 1) {
    const participantDebug = await page.evaluate(async sessionId => {
      const token = localStorage.getItem('entertainment_guest_token')
      const response = await fetch(`/api/entertainment/sessions/${sessionId}`, { headers: { 'X-Guest-Token': token } }).then(item => item.json())
      return response.data?.participants?.map(item => ({ id: item.id, type: item.type, name: item.displayName, mine: item.currentPlayer }))
    }, setup.sessionId)
    throw new Error(`Only the current human player may be marked as the current player: ${mineCount} ${JSON.stringify(participantDebug)}`)
  }
  const resume = page.getByRole('button', { name: /继续游戏/ })
  if (await resume.isVisible().catch(() => false)) await resume.click()

  const openingInput = page.locator('.quantity-field input[type=number]')
  if (await openingInput.isEnabled()) {
    await page.getByRole('button', { name: '全部', exact: true }).click()
    await page.getByText('按实际开盘价全仓').waitFor()
    await page.getByText(/已保存 全部资金买入/).waitFor()
    const allInSaved = await page.evaluate(async sessionId => {
      const token = localStorage.getItem('entertainment_guest_token')
      const snapshot = await fetch(`/api/entertainment/sessions/${sessionId}`, { headers: { 'X-Guest-Token': token } }).then(response => response.json())
      return snapshot.data.currentOrder
    }, setup.sessionId)
    if (!allInSaved?.allIn || allInSaved.quantity <= 0) throw new Error(`All-in order was not persisted: ${JSON.stringify(allInSaved)}`)
    await page.getByRole('button', { name: '100%', exact: true }).click()
    expectingBadRequest = true
    const buyLimit = await page.evaluate(async sessionId => {
      const token = localStorage.getItem('entertainment_guest_token')
      const headers = { 'Content-Type': 'application/json', 'X-Guest-Token': token }
      const snapshot = await fetch(`/api/entertainment/sessions/${sessionId}`, { headers }).then(response => response.json())
      const player = snapshot.data.participants.find(item => item.currentPlayer)
      const editableReservation = snapshot.data.currentOrder?.stage === 'OPENING' ? Number(snapshot.data.currentOrder.reservedAmount || 0) : 0
      const expected = Math.max(0, Math.min(Number(snapshot.data.config.maxBuyQuantity), Math.floor((Number(player.cash) - Number(player.reservedCash) + editableReservation) / Number(snapshot.data.currentRoundState.previousClosePrice))))
      const adjusted = await fetch(`/api/entertainment/sessions/${sessionId}/actions`, { method: 'POST', headers, body: JSON.stringify({ clientRequestId: `qa-over-limit-${Date.now()}`, actionType: 'PLACE_ORDER', side: 'BUY', quantity: expected + 50 }) }).then(response => response.json())
      return { expected, adjusted: adjusted.data?.currentOrder?.quantity }
    }, setup.sessionId)
    await page.waitForTimeout(100)
    expectingBadRequest = false
    const inputMax = Number(await openingInput.getAttribute('max'))
    if (inputMax !== buyLimit.expected || Number(buyLimit.adjusted) !== buyLimit.expected) throw new Error(`Cash and configured buy limits must clamp to the nearest valid quantity: ${JSON.stringify({ inputMax, ...buyLimit })}`)
    const tradeQuantity = Math.min(150, buyLimit.expected)
    await openingInput.fill(String(tradeQuantity))
    await page.getByText(`已保存 买入 ${tradeQuantity} 股`).waitFor()
  }

  await page.getByRole('button', { name: '事件', exact: true }).click()
  await page.locator('.event-dialog h2').waitFor()
  if (await page.locator('.event-dialog .event-result').count()) throw new Error('Event result was revealed before the stage price was published')
  await page.locator('.event-dialog .dialog-close').click()
  await page.getByRole('heading', { name: '收益率排名' }).waitFor()
  await page.getByRole('button', { name: 'x5', exact: true }).waitFor()

  await page.getByTitle('规则').click()
  await page.getByRole('heading', { name: '快速开始' }).waitFor()
  await page.getByRole('button', { name: '定价参数' }).click()
  await page.getByRole('heading', { name: '价格策略与参数' }).waitFor()
  await page.getByText(/参考价 × exp/).waitFor()
  await page.screenshot({ path: path.join(outputDir, 'rules-modal.png') })
  await page.getByTitle('关闭说明书').click()

  const finishOpening = page.getByRole('button', { name: /完成开盘前操作/ })
  if (await finishOpening.isVisible().catch(() => false)) await finishOpening.click()
  await page.getByText(/回合已开盘/).waitFor({ timeout: 45000 })
  const openingBars = await page.locator('.round-candle').count()
  if (openingBars !== 0) throw new Error('Current round must not be drawn before closing calculation')
  await page.screenshot({ path: path.join(outputDir, 'opening-result.png') })
  const hadOpeningEvent = await page.evaluate(() => Boolean(document.querySelector('.result-dialog .event-result:not(.empty)')))
  await page.keyboard.press('Enter')
  if (hadOpeningEvent) {
    await page.waitForTimeout(500)
    const phaseAfterOpeningEvent = await page.evaluate(async sessionId => {
      const token = localStorage.getItem('entertainment_guest_token')
      const snapshot = await fetch(`/api/entertainment/sessions/${sessionId}`, { headers: { 'X-Guest-Token': token } }).then(response => response.json())
      return snapshot.data?.phase
    }, setup.sessionId)
    if (phaseAfterOpeningEvent === 'CLOSING_EVENT_ACK') throw new Error('An opening event must not display a second event dialog before closing')
  }

  await acknowledgeEvent()
  await page.getByRole('heading', { name: '当前操作：卖出' }).waitFor()
  await page.getByRole('heading', { name: '当前操作：卖出' }).waitFor()
  if (!await page.locator('.order-panel.sell-mode').count()) throw new Error('Closing order panel must clearly use sell mode')
  const sellInput = page.locator('.quantity-field input[type=number]')
  if (await sellInput.isEnabled()) {
    const sellQuantity = Number(await sellInput.getAttribute('max'))
    await page.getByRole('button', { name: '全部', exact: true }).click()
    await page.getByText(`已保存 卖出 ${sellQuantity} 股`).waitFor()
    const savedSellQuantity = await page.evaluate(async sessionId => {
      const token = localStorage.getItem('entertainment_guest_token')
      const snapshot = await fetch(`/api/entertainment/sessions/${sessionId}`, { headers: { 'X-Guest-Token': token } }).then(response => response.json())
      return Number(snapshot.data?.currentOrder?.quantity || 0)
    }, setup.sessionId)
    if (savedSellQuantity !== sellQuantity) throw new Error(`Sell all did not submit the full position: ${savedSellQuantity}/${sellQuantity}`)
  }
  const finishClosing = page.getByRole('button', { name: /完成收盘前操作/ })
  if (await finishClosing.isVisible().catch(() => false)) await finishClosing.click()
  await page.getByText(/回合已收盘/).waitFor({ timeout: 45000 })
  if (await page.locator('.round-candle').count() < 1) throw new Error('Completed round candle was not drawn')
  await page.locator('.result-dialog .event-result').waitFor()
  await page.screenshot({ path: path.join(outputDir, 'closing-result.png') })
  await page.keyboard.press('Enter')
  await page.waitForTimeout(1400)
  await finishRepaymentStage()
  await acknowledgeEvent()

  await page.getByRole('heading', { name: '当前操作：买入' }).waitFor()
  if (await openingInput.isEnabled()) { await openingInput.fill('0'); await page.waitForTimeout(500) }
  if (await finishOpening.isVisible().catch(() => false)) await finishOpening.click()
  await page.getByText(/回合已开盘/).waitFor({ timeout: 45000 })
  await page.getByRole('button', { name: /进入收盘前|等待其他参与者/ }).click()
  await page.waitForTimeout(1000)
  await acknowledgeEvent()
  await page.getByRole('heading', { name: '当前操作：卖出' }).waitFor()
  if (await sellInput.isEnabled()) { await sellInput.fill('0'); await page.waitForTimeout(500) }
  if (await finishClosing.isVisible().catch(() => false)) await finishClosing.click()
  await page.getByText(/回合已收盘/).waitFor({ timeout: 45000 })
  await page.getByRole('button', { name: /进入下一回合|查看最终收益|进入银行还贷期/ }).click()
  await page.waitForTimeout(1400)
  await finishRepaymentStage()
  await acknowledgeEvent()
  if (await page.locator('.round-candle').count() < 2 || await page.locator('.kline-connector').count() < 2) throw new Error('Round candles and close-to-open connectors were not drawn')
  if (await page.locator('.trade-marker.buy').count() !== 1 || await page.locator('.trade-marker.sell').count() !== 1) throw new Error('Trade markers must only match the player trades in each completed round')
  if (configuredRounds >= 5) {
    await page.getByRole('button', { name: '5回合', exact: true }).click()
    if (await page.locator('.round-candle').count() !== 1) throw new Error('5-round view must merge completed rounds 1-5 into one candle')
    await page.getByRole('button', { name: '1回合', exact: true }).click()
    if (await page.locator('.round-candle').count() < 2) throw new Error('1-round view must restore one candle per completed round')
  }
  const connectorGeometryValid = await page.locator('.price-chart').evaluate(svg => {
    const bars = [...svg.querySelectorAll('.round-candle')]
    const connectors = [...svg.querySelectorAll('.kline-connector')]
    return bars.every((bar, index) => {
      const centerX = Number(bar.getAttribute('x')) + Number(bar.getAttribute('width')) / 2
      const centerY = Number(bar.getAttribute('y')) + Number(bar.getAttribute('height')) / 2
      const connector = connectors[index]
      if (!connector || Math.abs(Number(connector.getAttribute('x2')) - centerX) > .01 || Math.abs(Number(connector.getAttribute('y2')) - centerY) > .01) return false
      if (index > 0) {
        const previous = bars[index - 1]
        const previousX = Number(previous.getAttribute('x')) + Number(previous.getAttribute('width')) / 2
        const previousY = Number(previous.getAttribute('y')) + Number(previous.getAttribute('height')) / 2
        if (Math.abs(Number(connector.getAttribute('x1')) - previousX) > .01 || Math.abs(Number(connector.getAttribute('y1')) - previousY) > .01) return false
      }
      return Boolean(connector.compareDocumentPosition(bar) & Node.DOCUMENT_POSITION_FOLLOWING)
    })
  })
  if (!connectorGeometryValid) throw new Error('Connectors must join candle centers and render below candle bodies')

  await page.locator('.chart-wrap').hover({ position: { x: 180, y: 120 } })
  await page.locator('.chart-tooltip').waitFor()
  const candleTransform = await page.locator('.round-bar.hovered .round-candle').evaluate(element => getComputedStyle(element).transform)
  if (!candleTransform || candleTransform === 'none') throw new Error('Hovered round candle must animate larger')
  const candleAnimation = await page.locator('.round-candle').last().evaluate(element => getComputedStyle(element).animationName)
  if (!candleAnimation.includes('candle-grow')) throw new Error('Round candle animation missing')
  await page.screenshot({ path: path.join(outputDir, 'play-desktop.png'), fullPage: true })

  await page.setViewportSize({ width: 390, height: 844 })
  await page.reload({ waitUntil: 'networkidle' })
  if (await resume.isVisible().catch(() => false)) await resume.click()
  if (await page.evaluate(() => document.documentElement.scrollWidth > innerWidth)) throw new Error('Mobile play overflow')
  const chartBox = await page.locator('.price-chart').boundingBox()
  if (!chartBox || chartBox.height < 240) throw new Error('Mobile chart is vertically squashed')
  const chartBottom = await page.locator('.chart-panel').evaluate(element => element.getBoundingClientRect().bottom + scrollY)
  const orderTop = await page.locator('.order-panel').evaluate(element => element.getBoundingClientRect().top + scrollY)
  if (orderTop < chartBottom || orderTop - chartBottom > 16) throw new Error('Mobile order panel must follow chart')
  await page.screenshot({ path: path.join(outputDir, 'play-mobile.png'), fullPage: true })

  await page.getByTitle('重新开始').click()
  await page.getByRole('heading', { name: '确认重新开始？' }).waitFor()
  await page.getByRole('button', { name: '确认重新开始' }).click()
  await page.waitForURL(new RegExp(`/customer/entertainment/games/${setup.gameId}$`))
  if (consoleErrors.length) throw new Error(`Console errors: ${consoleErrors.join(' | ')}`)
  console.log(JSON.stringify({ ...setup, consoleErrors: 0, outputDir }))
} finally {
  await browser.close()
}
