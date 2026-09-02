import { chromium } from 'playwright-core'
import { mkdir } from 'node:fs/promises'
import path from 'node:path'

const baseUrl = process.env.QA_BASE_URL || 'http://127.0.0.1:5174'
const outputDir = path.resolve('test-results/multi-bank')
await mkdir(outputDir, { recursive: true })
const browser = await chromium.launch({ executablePath: 'C:/Program Files/Google/Chrome/Application/chrome.exe', headless: true })
let sessionId
let page

try {
  page = await browser.newPage({ viewport: { width: 1280, height: 900 } })
  await page.goto(`${baseUrl}/customer/entertainment`, { waitUntil: 'networkidle' })
  const setup = await page.evaluate(async () => {
    const token = localStorage.getItem('entertainment_guest_token')
    const headers = { 'Content-Type': 'application/json', 'X-Guest-Token': token }
    const list = await fetch('/api/entertainment/games?page=1&pageSize=50', { headers }).then(response => response.json())
    let chosen
    for (const game of list.data.records) {
      const detail = await fetch(`/api/entertainment/games/${game.id}`, { headers }).then(response => response.json())
      if (detail.data?.config?.loanEnabled && detail.data.config.creditProducts?.filter(item => item.enabled).length >= 2) {
        chosen = detail.data
        break
      }
    }
    if (!chosen) throw new Error('No public multi-bank game found')
    if (chosen.resumableSessionId) await fetch(`/api/entertainment/sessions/${chosen.resumableSessionId}`, { method: 'DELETE', headers })
    const started = await fetch(`/api/entertainment/games/${chosen.game.id}/sessions`, {
      method: 'POST', headers, body: JSON.stringify({ useAi: false })
    }).then(response => response.json())
    if (started.code !== 0) throw new Error(`${started.message}: ${JSON.stringify({ gameId: chosen.game.id, aiEnabled: chosen.game.aiEnabled, token: Boolean(token) })}`)
    return { sessionId: started.data.id, gameId: chosen.game.id, token,
      bankNames: chosen.config.creditProducts.filter(item => item.enabled).map(item => item.name) }
  })
  sessionId = setup.sessionId
  await page.goto(`${baseUrl}/customer/entertainment/sessions/${sessionId}/play`, { waitUntil: 'networkidle' })
  await page.waitForFunction(() => !document.querySelector('.round-curtain'), null, { timeout: 5000 })
  const ack = page.getByRole('button', { name: /我已知晓/ })
  if (await ack.isVisible().catch(() => false)) await ack.click()
  await page.locator('.credit-dialog').waitFor({ state: 'visible', timeout: 8000 })
  await page.locator('.credit-quick button').nth(0).click()
  await page.getByRole('button', { name: '确认借款' }).click()
  await page.locator('.bank-select .custom-select-trigger').click()
  await page.getByRole('button', { name: setup.bankNames[1], exact: true }).click()
  await page.locator('.custom-select-menu').waitFor({ state: 'hidden' })
  await page.locator('.credit-amount input').fill('21875.6')
  await page.getByRole('button', { name: '确认借款' }).click()
  await page.waitForFunction(() => !document.querySelector('.bank-select small')?.textContent.includes('本行债务 ¥0.00'))
  await page.screenshot({ path: path.join(outputDir, 'multi-bank-desktop.png') })
  await page.getByRole('button', { name: '本行贷款', exact: true }).click()
  await page.locator('.loan-extension .custom-select-trigger').click()
  if (await page.locator('.custom-select-menu button').count() !== 10) throw new Error('Extension range did not match 10-round loan term')
  await page.getByRole('button', { name: '延期 10 回合', exact: true }).click()
  await page.getByRole('button', { name: '延期', exact: true }).click()
  await page.getByText('第 20 回合到期', { exact: true }).waitFor()
  await page.getByRole('button', { name: '还款', exact: true }).click()
  await page.locator('.credit-amount input').fill('100')
  await page.getByRole('button', { name: '确认还款' }).click()
  await page.waitForFunction(() => document.querySelector('.bank-select small')?.textContent.includes('¥21,775.60'))
  await page.locator('.credit-quick button').nth(2).click()
  if (Number(await page.locator('.credit-amount input').inputValue()) !== 21775.6) throw new Error('100% repayment lost decimal debt')
  await page.getByRole('button', { name: '确认还款' }).click()
  await page.waitForFunction(() => document.querySelector('.bank-select small')?.textContent.includes('本行债务 ¥0.00'))
  await page.getByRole('button', { name: '本行贷款', exact: true }).click()
  const settlement = page.locator('.loan-settlement')
  await settlement.waitFor()
  const settlementText = await settlement.textContent()
  if (!settlementText.includes('借款') || !settlementText.includes('还款（本金）')) throw new Error(`Closed loan summary missing: ${settlementText}`)
  await page.screenshot({ path: path.join(outputDir, 'closed-loan-desktop.png') })

  const result = await page.evaluate(async id => {
    const token = localStorage.getItem('entertainment_guest_token')
    const snapshot = await fetch(`/api/entertainment/sessions/${id}`, { headers: { 'X-Guest-Token': token } })
      .then(response => response.json())
    return snapshot.data.credit.banks.map(bank => ({ code: bank.productCode, debt: bank.effectiveDebt,
      contracts: bank.contracts.length, closed: bank.contracts.filter(contract => contract.status === 'CLOSED').length }))
  }, sessionId)
  if (result.filter(bank => bank.contracts > 0).length < 2) throw new Error(`Multi-bank debt was not isolated: ${JSON.stringify(result)}`)
  if (Number(result.find(bank => bank.code === 'CONSERVATIVE_BANK')?.debt) !== 12500
      || Number(result.find(bank => bank.code === 'GROWTH_BANK')?.debt) !== 0
      || result.find(bank => bank.code === 'GROWTH_BANK')?.closed !== 1) {
    throw new Error(`Repayment affected the wrong bank: ${JSON.stringify(result)}`)
  }
  console.log(JSON.stringify(result))
  await page.evaluate(async id => {
    const token = localStorage.getItem('entertainment_guest_token')
    await fetch(`/api/entertainment/sessions/${id}`, { method: 'DELETE', headers: { 'X-Guest-Token': token } })
  }, sessionId)
  sessionId = undefined
  const skipResult = await page.evaluate(async gameId => {
    const token = localStorage.getItem('entertainment_guest_token')
    const headers = { 'Content-Type': 'application/json', 'X-Guest-Token': token }
    const started = await fetch(`/api/entertainment/games/${gameId}/sessions`, {
      method: 'POST', headers, body: JSON.stringify({ useAi: false, playbackSpeed: 5, autoAdvance: false })
    }).then(response => response.json())
    if (started.code !== 0) throw new Error(started.message)
    let snapshot = started.data
    const action = async actionType => {
      const response = await fetch(`/api/entertainment/sessions/${snapshot.id}/actions`, {
        method: 'POST', headers, body: JSON.stringify({ clientRequestId: `${actionType}-${Date.now()}-${Math.random()}`, actionType })
      }).then(item => item.json())
      if (response.code !== 0) throw new Error(response.message)
      snapshot = response.data
    }
    const deadline = Date.now() + 20000
    while (Date.now() < deadline) {
      if (snapshot.phase === 'OPENING_EVENT_ACK' || snapshot.phase === 'CLOSING_EVENT_ACK') await action('ACK_EVENT')
      else if (snapshot.phase === 'CREDIT_TRADING' || snapshot.phase === 'OPENING_RESULT_ACK') await action('READY_NEXT_ROUND')
      else if (snapshot.phase === 'ROUND_RESULT_ACK') {
        await action('READY_NEXT_ROUND')
        return { sessionId: snapshot.id, phase: snapshot.phase }
      } else {
        await new Promise(resolve => setTimeout(resolve, 250))
        const response = await fetch(`/api/entertainment/sessions/${snapshot.id}`, { headers }).then(item => item.json())
        if (response.code !== 0) throw new Error(response.message)
        snapshot = response.data
      }
    }
    throw new Error(`No-debt round did not finish: ${snapshot.phase}`)
  }, setup.gameId)
  sessionId = skipResult.sessionId
  if (skipResult.phase === 'CREDIT_REPAYMENT') throw new Error('No-debt session entered repayment phase')
  if (skipResult.phase !== 'ROUND_START' && skipResult.phase !== 'COMPLETED') throw new Error(`Unexpected phase after skipped repayment: ${skipResult.phase}`)
  console.log(JSON.stringify({ skippedRepaymentPhase: skipResult.phase }))
} finally {
  if (sessionId && page) {
    await page.evaluate(async id => {
      const token = localStorage.getItem('entertainment_guest_token')
      await fetch(`/api/entertainment/sessions/${id}`, { method: 'DELETE', headers: { 'X-Guest-Token': token } })
    }, sessionId).catch(() => {})
  }
  await browser.close()
}
