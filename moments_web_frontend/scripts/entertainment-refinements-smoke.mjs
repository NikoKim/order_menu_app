import assert from 'node:assert/strict'
import { mkdir } from 'node:fs/promises'
import path from 'node:path'
import { chromium } from 'playwright-core'
import { createTutorialSession, transitionTutorial } from '../src/games/guessStockTutorial.js'

const baseUrl = process.env.QA_BASE_URL || 'http://127.0.0.1:5174'
const output = path.resolve(process.env.QA_OUTPUT_DIR || 'test-results/entertainment-refinements')
await mkdir(output, { recursive: true })
const browser = await chromium.launch({ executablePath: 'C:/Program Files/Google/Chrome/Application/chrome.exe', headless: true })
const copy = value => JSON.parse(JSON.stringify(value))
const game = { id: 23, stockCode: 'HK001', title: '长兴存储', description: '存储芯片与市场周期', versionLabel: 'v1', visibility: 'PUBLIC', owner: true, creator: { nickname: '测试创建者' } }
const config = { ...createTutorialSession().config, stockCode: 'HK001', aiEnabled: true, aiCount: 3,
  dailyChallengeEnabled: true, multiplayerEnabled: true, maxHumanPlayers: 8,
  events: [{ name: 'QA-hidden-event', description: 'QA-hidden-event-details', enabled: true }], liquidity: 'MEDIUM' }
const hosted = { configured: true, baseUrl: 'https://example.com/v1', model: 'test-model' }

async function checkLayout(page, label) {
  const issues = await page.evaluate(() => {
    const result = []
    if (document.documentElement.scrollWidth > innerWidth + 1) result.push('horizontal overflow')
    for (const element of document.querySelectorAll('.primary-action button,.primary-action a,.check-command')) {
      if (element.getClientRects().length && element.scrollWidth > element.clientWidth + 2) result.push('control text clipped')
    }
    for (const element of document.querySelectorAll('.modal-mask .ai-dialog')) {
      const rect = element.getBoundingClientRect()
      if (rect.top < 0 || rect.bottom > innerHeight) result.push('AI dialog outside viewport')
    }
    return result
  })
  await page.screenshot({ path: path.join(output, label + '.png'), fullPage: true })
  assert.deepEqual(issues, [], label + ': ' + issues.join(', '))
}

try {
  for (const viewport of [{ width: 1440, height: 1000 }, { width: 390, height: 844 }, { width: 360, height: 640 }]) {
    const page = await browser.newPage({ viewport })
    const errors = []
    const probes = []
    let connected = true
    let missingVerificationRoute = false
    let resumable = false
    page.on('pageerror', error => errors.push(error.message))
    await page.addInitScript(() => localStorage.setItem('entertainment_guest_token', 'detail-fixture'))
    await page.route('**/api/**', async route => {
      const pathname = new URL(route.request().url()).pathname
      if (!pathname.startsWith('/api/')) return route.continue()
      let data = {}
      if (pathname === '/api/entertainment/games/23') data = { game, config, hasResumableSession: resumable,
        resumableSessionId: resumable ? 502 : null, activeRoomId: 90, activeRoomStatus: 'PLAYING' }
      else if (pathname.endsWith('/ai-credential')) data = hosted
      else if (pathname.endsWith('/leaderboard')) data = { entries: [], versionLabel: 'v1' }
      else if (pathname.endsWith('/reviews')) data = { records: [], total: 0, averageRating: 0, canReview: false }
      else if (pathname.endsWith('/ai-connection/test')) {
        probes.push(route.request().postDataJSON())
        if (missingVerificationRoute) return route.fulfill({ status: 404, contentType: 'application/json',
          body: JSON.stringify({ code: 404, message: '请求的接口或资源不存在', data: null }) })
        data = { connected, message: connected ? '连接有效，模型已响应' : 'API Key 无效或已过期', model: 'test-model', latencyMs: 123 }
      }
      return route.fulfill({ contentType: 'application/json', body: JSON.stringify({ code: 0, data }) })
    })
    await page.goto(`${baseUrl}/customer/entertainment/games/23`, { waitUntil: 'networkidle' })
    await page.getByRole('heading', { name: '长兴存储', exact: true }).waitFor()
    assert.equal(await page.getByRole('heading', { name: '事件池', exact: true }).count(), 0)
    assert.equal(await page.getByText('QA-hidden-event', { exact: true }).count(), 0)
    const background = await page.locator('.stock-overview').evaluate(element => {
      const style = getComputedStyle(element, '::before')
      return { image: style.backgroundImage, mask: style.maskImage }
    })
    assert.ok(background.image.includes('stock-market-detail.png'))
    assert.ok(background.mask.includes('linear-gradient'))
    assert.ok(await page.evaluate(async () => { const image = new Image(); image.src = '/entertainment/stock-market-detail.png'; await image.decode(); return image.naturalWidth > 1000 }))
    await checkLayout(page, `${viewport.width}-detail`)
    await page.getByRole('button', { name: '更多操作', exact: true }).click()
    await page.getByRole('menuitem', { name: '玩法说明', exact: true }).waitFor()
    await page.getByRole('heading', { name: '长兴存储', exact: true }).click()
    await page.getByRole('button', { name: '开始游戏', exact: true }).click()
    await page.getByRole('button', { name: '验证 API 连接', exact: true }).click()
    await page.getByText('连接有效，模型已响应', { exact: true }).waitFor()
    assert.equal(probes.at(-1).source, 'HOSTED')
    assert.equal(probes.at(-1).aiCredential, undefined)
    await checkLayout(page, `${viewport.width}-api-connected`)
    await page.getByRole('button', { name: '使用本局自己的 AI Key', exact: true }).click()
    assert.equal(await page.locator('.check-result').count(), 0, 'changing source clears a stale verification')
    const modal = page.locator('.ai-dialog')
    const model = modal.locator('label').filter({ has: page.locator('span', { hasText: /^模型$/ }) }).locator('input')
    await model.fill('my-model')
    await modal.locator('input[type="password"]').fill('test-private-key')
    connected = false
    await page.getByRole('button', { name: '验证 API 连接', exact: true }).click()
    await page.getByText('API Key 无效或已过期', { exact: true }).waitFor()
    assert.equal(probes.at(-1).source, 'OWN')
    assert.equal(probes.at(-1).aiCredential.apiKey, 'test-private-key')
    assert.ok(!(await page.locator('.check-result').innerText()).includes('test-private-key'))
    await checkLayout(page, `${viewport.width}-api-failed`)
    await model.fill('new-model')
    assert.equal(await page.locator('.check-result').count(), 0)
    missingVerificationRoute = true
    await page.getByRole('button', { name: '验证 API 连接', exact: true }).click()
    await page.getByText('当前后端尚未提供 API 验证接口，请更新并重启后端后再试', { exact: true }).waitFor()
    await checkLayout(page, `${viewport.width}-api-route-missing`)
    missingVerificationRoute = false
    await modal.getByTitle('关闭', { exact: true }).click()
    resumable = true
    await page.reload({ waitUntil: 'networkidle' })
    await page.getByRole('button', { name: '更多操作', exact: true }).click()
    await page.getByRole('menuitem', { name: '重新开始', exact: true }).waitFor()
    assert.deepEqual(errors, [])
    await page.close()
    console.log(`${viewport.width}px detail hierarchy, background and API verification states passed; requests mocked`)
  }

  for (const playerIndex of [0, 1]) {
    const viewport = playerIndex ? { width: 390, height: 844 } : { width: 1440, height: 1000 }
    const context = await browser.newContext({ viewport })
    const page = await context.newPage()
    let session = transitionTutorial(transitionTutorial(createTutorialSession(), 'READY_NEXT_ROUND'), 'ACK_EVENT')
    session.id = 501; session.gameId = 23; session.game = game
    session.config.creditProducts[0].maturityRounds = 3
    session.participants.push({ ...copy(session.participants[0]), id: 2, displayName: '玩家二' })
    session.participants.forEach((participant, index) => { participant.currentPlayer = index === playerIndex })
    session.room = { id: 90, host: playerIndex === 0, status: 'PLAYING', members: [0, 1].map(index => ({ id: index + 10,
      displayName: index ? '玩家二' : '房主', currentPlayer: index === playerIndex, readyStage: null })) }
    const calls = []
    const errors = []
    page.on('pageerror', error => errors.push(error.message))
    await page.addInitScript(index => localStorage.setItem('entertainment_guest_token', 'room-player-' + index), playerIndex)
    await page.route('**/api/**', route => {
      if (!new URL(route.request().url()).pathname.startsWith('/api/')) return route.continue()
      const pathname = new URL(route.request().url()).pathname
      calls.push(pathname)
      assert.equal(route.request().headers()['x-guest-token'], 'room-player-' + playerIndex)
      const bank = session.credit.banks[0]
      const me = session.participants[playerIndex]
      if (pathname.endsWith('/credit/borrow')) {
        const amount = route.request().postDataJSON().amount
        bank.principal = amount; bank.effectiveDebt = amount; bank.availableCredit = 0
        session.credit.effectiveDebt = amount; me.cash += amount
      }
      if (pathname.endsWith('/credit/repay')) {
        me.cash -= session.credit.effectiveDebt
        bank.principal = 0; bank.accruedInterest = 0; bank.effectiveDebt = 0
        session.credit.effectiveDebt = 0
      }
      if (pathname.endsWith('/actions')) session.room.members[playerIndex].readyStage = session.phase
      return route.fulfill({ contentType: 'application/json', body: JSON.stringify({ code: 0, data: session }) })
    })
    await page.goto(`${baseUrl}/customer/entertainment/sessions/501/play`, { waitUntil: 'networkidle' })
    assert.equal(await page.locator('.bank-access-bar').count(), 0, 'the redundant bank access bar must not render')
    await page.locator('.credit-dialog').waitFor({ state: 'visible' })
    await page.locator('[data-tutorial="credit-close"]').click()
    await page.locator('.credit-dialog').waitFor({ state: 'hidden' })
    await checkLayout(page, `${viewport.width}-multiplayer-without-bank-bar`)
    await page.locator('[data-tutorial="bank-trigger"]').click()
    await page.locator('.credit-amount input').fill('1000')
    await page.getByRole('button', { name: '确认借款', exact: true }).click()
    await page.getByRole('button', { name: '完成融资，进入开盘前', exact: true }).click()
    await page.locator('[data-tutorial="credit-input"]').fill('100')
    assert.equal(await page.getByRole('button', { name: '确认借款', exact: true }).isDisabled(), true)
    session.phase = 'OPENING_TRADING'
    session.config.creditInteractionMode = 'INLINE'
    session.room.members.forEach(member => { member.readyStage = null })
    await page.locator('.credit-dialog').waitFor({ state: 'hidden' })
    await page.locator('[data-tutorial="bank-trigger"]').click()
    await page.getByRole('button', { name: '还款', exact: true }).waitFor()
    await page.locator('[data-tutorial="credit-close"]').click()
    session.phase = 'CREDIT_REPAYMENT'
    session.credit.effectiveDebt = 1010
    session.credit.banks[0].effectiveDebt = 1010
    session.credit.banks[0].accruedInterest = 10
    await page.locator('.credit-dialog').waitFor({ state: 'visible' })
    await checkLayout(page, `${viewport.width}-multiplayer-repayment`)
    await page.locator('[data-tutorial="credit-close"]').click()
    await page.locator('[data-tutorial="bank-trigger"]').click()
    await page.locator('.credit-quick').getByRole('button', { name: '100%', exact: true }).click()
    await page.getByRole('button', { name: '确认还款', exact: true }).click()
    assert.ok(calls.some(call => call.endsWith('/credit/borrow')))
    assert.ok(calls.some(call => call.endsWith('/credit/repay')))
    assert.equal(session.credit.effectiveDebt, 0)
    assert.deepEqual(errors, [])
    await context.close()
    console.log(`Multiplayer ${playerIndex ? 'member' : 'host'}: bank reopening, borrowing, readiness and repayment passed; requests mocked`)
  }

  const roomPage = await browser.newPage({ viewport: { width: 390, height: 844 } })
  let roomProbe
  await roomPage.addInitScript(() => localStorage.setItem('entertainment_guest_token', 'host-fixture'))
  await roomPage.route('**/api/**', route => {
    const pathname = new URL(route.request().url()).pathname
    if (!pathname.startsWith('/api/')) return route.continue()
    let data = { id: 90, gameId: 23, game, status: 'LOBBY', inviteCode: 'ABC123', maxPlayers: 8,
      host: true, aiEnabled: true, hostedAiConfigured: false, members: [{ id: 1, currentPlayer: true, role: 'HOST', displayName: '房主', credentialReady: true }, { id: 2, role: 'PLAYER', displayName: '玩家二' }] }
    if (pathname.endsWith('/ai-connection/test')) {
      roomProbe = route.request().postDataJSON()
      data = { connected: true, message: '连接有效，模型已响应', model: 'room-model', latencyMs: 99 }
    }
    return route.fulfill({ contentType: 'application/json', body: JSON.stringify({ code: 0, data }) })
  })
  await roomPage.goto(`${baseUrl}/customer/entertainment/rooms/90`, { waitUntil: 'networkidle' })
  await roomPage.locator('.ai-choice input').check()
  await roomPage.getByRole('button', { name: '验证 API 连接', exact: true }).click()
  await roomPage.getByText('连接有效，模型已响应', { exact: true }).waitFor()
  assert.equal(roomProbe.source, 'ROOM')
  assert.equal(roomProbe.roomId, 90)
  assert.equal(roomProbe.aiCredential, undefined)
  await checkLayout(roomPage, '390-room-api-check')
  await roomPage.close()
  console.log('Stored room API verification passed; no real model calls made')
} catch (error) {
  const active = browser.contexts().at(-1)?.pages().at(-1)
  if (active) await active.screenshot({ path: path.join(output, 'failure.png'), fullPage: true }).catch(() => {})
  throw error
} finally { await browser.close() }
