import { chromium } from 'playwright-core'
import { mkdir } from 'node:fs/promises'
import path from 'node:path'

const baseUrl = process.env.QA_BASE_URL || 'http://127.0.0.1:5174'
const outputDir = process.env.QA_OUTPUT_DIR || path.resolve('test-results/entertainment')
await mkdir(outputDir, { recursive: true })
const browser = await chromium.launch({ executablePath: 'C:/Program Files/Google/Chrome/Application/chrome.exe', headless: true })

async function setupGuest(page) {
  await page.goto(`${baseUrl}/customer/entertainment`, { waitUntil: 'networkidle' })
  return page.evaluate(async () => {
    let token = localStorage.getItem('entertainment_guest_token')
    if (!token) {
      const result = await fetch('/api/entertainment/guests', { method: 'POST' }).then(item => item.json())
      token = result.data.token
      localStorage.setItem('entertainment_guest_token', token)
      localStorage.setItem('entertainment_guest_name', result.data.displayName)
    }
    return token
  })
}

async function api(page, url, method = 'GET', body) {
  return page.evaluate(async ({ url, method, body }) => {
    const token = localStorage.getItem('entertainment_guest_token')
    const response = await fetch(url, {
      method,
      headers: { 'Content-Type': 'application/json', 'X-Guest-Token': token },
      body: body == null ? undefined : JSON.stringify(body)
    }).then(item => item.json())
    if (response.code !== 0) throw new Error(response.message)
    return response.data
  }, { url, method, body })
}

try {
  const hostContext = await browser.newContext({ viewport: { width: 1280, height: 900 } })
  const guestContext = await browser.newContext({ viewport: { width: 900, height: 800 } })
  const host = await hostContext.newPage()
  const guest = await guestContext.newPage()
  await setupGuest(host)
  await setupGuest(guest)

  const games = await api(host, '/api/entertainment/games?page=1&pageSize=50')
  let game = null
  for (const candidate of games.records) {
    const detail = await api(host, `/api/entertainment/games/${candidate.id}`)
    if (detail.config.multiplayerEnabled) { game = candidate; break }
  }
  if (!game) throw new Error('No public multiplayer game available')
  const room = await api(host, `/api/entertainment/games/${game.id}/rooms`, 'POST')

  await host.goto(`${baseUrl}/customer/entertainment/games/${game.id}`, { waitUntil: 'networkidle' })
  await host.getByRole('button', { name: '开始游戏', exact: true }).waitFor()
  await host.getByRole('button', { name: '返回多人房间', exact: true }).waitFor()

  const solo = await api(host, `/api/entertainment/games/${game.id}/sessions`, 'POST', { useAi: false })
  if (solo.room != null) throw new Error('Solo session unexpectedly attached to room')
  const coexist = await api(host, `/api/entertainment/games/${game.id}`)
  if (!coexist.hasResumableSession || coexist.resumableSessionId !== solo.id || coexist.activeRoomId !== room.id) {
    throw new Error(`Solo and multiplayer state are not isolated: ${JSON.stringify(coexist)}`)
  }

  await host.goto(`${baseUrl}/customer/entertainment/rooms/${room.id}`, { waitUntil: 'networkidle' })
  await host.getByText('玩家 1 /', { exact: false }).waitFor()
  await host.getByRole('button', { name: '关闭房间', exact: true }).waitFor()
  if (await host.getByRole('button', { name: '开始游戏', exact: true }).isEnabled()) throw new Error('One-player room must remain open but cannot start')
  await host.screenshot({ path: path.join(outputDir, 'room-one-player.png'), fullPage: true })

  await api(guest, '/api/entertainment/rooms/join', 'POST', { inviteCode: room.inviteCode })
  const multiplayer = await api(host, `/api/entertainment/rooms/${room.id}/start`, 'POST', { useAi: false, useDefaultAi: false })
  await host.goto(`${baseUrl}/customer/entertainment/sessions/${multiplayer.id}/play`, { waitUntil: 'networkidle' })
  const acknowledge = host.getByRole('button', { name: /我已知晓|等待其他玩家/ })
  if (await acknowledge.waitFor({ state: 'visible', timeout: 2500 }).then(() => true).catch(() => false)) await acknowledge.click()
  await host.getByTitle('规则').click()
  await host.getByRole('heading', { name: '快速开始' }).waitFor()
  const closeTrigger = host.getByTitle('关闭多人房间')
  const triggerZ = await closeTrigger.evaluate(element => Number(getComputedStyle(element).zIndex))
  if (triggerZ < 900) throw new Error('Close-room trigger is below modal backdrop')
  await closeTrigger.click()
  await host.getByRole('heading', { name: '关闭多人房间？' }).waitFor()
  await host.screenshot({ path: path.join(outputDir, 'room-close-over-modal.png') })
  await host.getByRole('button', { name: '确认关闭房间' }).click()
  await host.waitForURL(new RegExp(`/customer/entertainment/games/${game.id}$`))

  const closedSession = await api(guest, `/api/entertainment/sessions/${multiplayer.id}`)
  if (closedSession.status !== 'ABANDONED') throw new Error('Closing room did not end multiplayer session')
  const afterClose = await api(host, `/api/entertainment/games/${game.id}`)
  if (afterClose.activeRoomId != null || afterClose.resumableSessionId !== solo.id) {
    throw new Error('Closing multiplayer room changed solo progress')
  }
  await api(host, `/api/entertainment/sessions/${solo.id}`, 'DELETE')
  console.log(JSON.stringify({ gameId: game.id, roomId: room.id, soloSessionId: solo.id, multiplayerSessionId: multiplayer.id, outputDir }))
} finally {
  await browser.close()
}
