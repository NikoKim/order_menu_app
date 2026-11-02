import assert from 'node:assert/strict'
import { chromium } from 'playwright-core'
import { mkdir } from 'node:fs/promises'
import path from 'node:path'

const base = process.env.QA_BASE_URL || 'http://127.0.0.1:5174'
const backend = process.env.QA_API_URL || 'http://127.0.0.1:8082'
const output = path.resolve('../.agents/naval-qa'); await mkdir(output, { recursive: true })
const browser = await chromium.launch({ executablePath: 'C:/Program Files/Google/Chrome/Application/chrome.exe', headless: true })
const contexts = [], cleanup = [], errors = []
let keepHost
async function api(url, data, token, method = data ? 'POST' : 'GET') {
  const response = await fetch(backend + '/api' + url, { method, headers: { 'Content-Type': 'application/json', ...(token ? { 'X-Guest-Token': token } : {}) }, ...(data ? { body: JSON.stringify(data) } : {}) })
  const body = await response.json()
  if (!response.ok || ![0, 200].includes(body.code)) throw Object.assign(new Error(body.message), { code: body.code })
  return body.data
}
async function pageFor(viewport, token) {
  const context = await browser.newContext({ viewport, isMobile: viewport.width < 700, hasTouch: viewport.width < 700 }); contexts.push(context)
  await context.route('**/api/**', async route => {
    const url = new URL(route.request().url())
    if (!url.pathname.startsWith('/api/')) return route.continue()
    try { const headers = { ...route.request().headers() }; delete headers.origin; const response = await route.fetch({ url: backend + url.pathname + url.search, headers }); if (!response.ok()) console.log(url.pathname, response.status(), (await response.text()).slice(0, 200)); await route.fulfill({ response }) } catch (e) { console.log('API routing:', e.message.split('\n')[0]); await route.abort() }
  })
  if (token) await context.addInitScript(token => localStorage.setItem('entertainment_guest_token', token), token)
  const page = await context.newPage(); page.on('pageerror', e => errors.push(e.message))
  await page.goto(base + '/customer/entertainment/naval')
  try { await page.waitForFunction(() => /\d+ 人在线/.test(document.querySelector('.online-count')?.textContent || ''), null, { timeout: 12000 }) }
  catch (e) { console.log('Page errors:', errors); console.log((await page.locator('body').innerText()).slice(0, 1800)); await page.screenshot({ path: path.join(output, 'naval-rooms-failure.png') }); throw e }
  return page
}
try {
  const host = await api('/entertainment/guests', {}, null)
  const waiting = await api('/entertainment/naval/rooms', { mode: 'MULTIPLAYER', autoMatch: false }, host.token)
  cleanup.push({ id: waiting.id, token: host.token })
  keepHost = setInterval(() => api(`/entertainment/naval/rooms/${waiting.id}`, null, host.token).catch(() => {}), 4000)
  await api('/entertainment/naval/lobby/messages', { roomId: waiting.id }, host.token)
  await assert.rejects(api('/entertainment/naval/lobby/messages', { roomId: waiting.id }, host.token), /已经分享到大厅/)
  const directory = await api('/entertainment/naval/rooms')
  assert(directory.some(r => r.id === waiting.id && r.requiresInvite))
  assert(!JSON.stringify(directory).includes('inviteCode'))
  for (const viewport of [{ width: 1280, height: 800 }, { width: 320, height: 740 }, { width: 568, height: 320 }]) {
    const viewer = await api('/entertainment/guests', {}, null), page = await pageFor(viewport, viewer.token)
    const centers = await page.locator('.naval-header .header-actions').evaluate(el => [...el.querySelectorAll('button,a')].map(button => button.getBoundingClientRect()).filter(box => box.width > 0).map(box => box.x + box.width / 2))
    const distances = centers.slice(1).map((center, i) => center - centers[i])
    assert(Math.max(...distances) - Math.min(...distances) < .5, 'toolbar centers are evenly spaced across control groups')
    await page.getByRole('button', { name: '查看排行榜', exact: true }).click()
    await page.locator('.community-heading h2').getByText('排行榜', { exact: true }).waitFor()
    if (viewport.height < 450) {
      await page.getByRole('button', { name: '关闭社区面板', exact: true }).click()
      await page.waitForTimeout(320)
      const start = await page.getByRole('button', { name: '开始对战', exact: true }).boundingBox()
      assert(start.y + start.height <= viewport.height + 1, 'extra guest ranking copy does not hide start controls')
    }
    const roomButton = page.getByRole('button', { name: '游戏房间', exact: true })
    await roomButton.click()
    const card = page.locator('.directory-room').filter({ hasText: host.displayName })
    await card.waitFor()
    await page.waitForTimeout(350)
    await card.getByText('需要邀请码', { exact: true }).waitFor()
    assert(await card.getByRole('button', { name: '加入房间', exact: true }).isDisabled())
    const box = await page.locator('.naval-community').boundingBox()
    assert(box.x >= 0 && box.x + box.width <= viewport.width + 1)
    await page.screenshot({ path: path.join(output, `naval-rooms-${viewport.width}.png`) })
    await page.getByRole('button', { name: '聊天大厅', exact: true }).click()
    const shared = page.locator('.lobby-room-card').filter({ hasText: host.displayName })
    await shared.waitFor(); assert((await shared.innerText()).includes('需要邀请码'))
    await shared.click(); await page.getByRole('textbox', { name: '分享房间邀请码', exact: true }).waitFor()
    await page.getByRole('button', { name: '取消加入', exact: true }).click()
    await page.getByRole('button', { name: '展开舰队表情包', exact: true }).click()
    const picker = page.locator('.sticker-picker'), input = page.getByRole('textbox', { name: '大厅消息', exact: true })
    await picker.waitFor(); await page.waitForTimeout(250)
    assert.equal(await picker.evaluate(el => getComputedStyle(el).position), 'relative')
    assert(await picker.evaluate(el => Boolean(el.closest('.sticker-tray'))))
    const pickerBox = await picker.boundingBox(), inputBox = await input.boundingBox()
    assert(pickerBox.y >= inputBox.y + inputBox.height, 'stickers expand below the input')
    const tile = picker.locator('.sticker-grid button').first(), img = await tile.locator('img').boundingBox(), label = await tile.locator('small').boundingBox()
    assert(label.y - img.y - img.height >= 5, 'image and label have stable spacing')
    if (viewport.width === 320) {
      const lastLabel = await picker.locator('.sticker-grid button small').last().boundingBox(), tray = await page.locator('.sticker-tray').boundingBox()
      assert(lastLabel.y + lastLabel.height <= tray.y + tray.height + 1, `all mobile portrait sticker labels fit the tray: ${JSON.stringify({ lastLabel, tray, picker: await picker.boundingBox() })}`)
    }
    await page.screenshot({ path: path.join(output, `naval-sticker-tray-${viewport.width}.png`) })
    await tile.click()
    await page.locator('.lobby-chat-log .naval-message-sticker').last().waitFor()
  }
  const owner = await pageFor({ width: 1280, height: 800 })
  await owner.getByRole('button', { name: '开始对战', exact: true }).click()
  await owner.waitForFunction(() => document.querySelector('.own-panel canvas') && !document.querySelector('.launch-overlay'))
  const id = await owner.evaluate(() => JSON.parse(localStorage.getItem('naval-guest-session')).id)
  const ownerToken = await owner.evaluate(() => localStorage.getItem('entertainment_guest_token'))
  cleanup.push({ id, token: ownerToken })
  const spectator = await api('/entertainment/guests', {}, null)
  await owner.waitForTimeout(500)
  assert((await api('/entertainment/naval/rooms')).some(r => r.id === id && r.mode === 'AI'))
  const watched = await api(`/entertainment/naval/rooms/${id}/enter`, {}, spectator.token)
  assert.equal(watched.role, 'SPECTATOR'); assert.equal(watched.players[1].ships.length, 10)
  await owner.getByRole('switch', { name: '允许观战', exact: true }).uncheck()
  await owner.waitForTimeout(600)
  assert((await api('/entertainment/naval/rooms')).some(r => r.id === id && r.allowSpectators === false))
  await assert.rejects(api(`/entertainment/naval/rooms/${id}`, null, spectator.token), /关闭观战/)
  await owner.getByRole('switch', { name: '允许观战', exact: true }).check()
  await owner.waitForTimeout(600)
  const again = await api(`/entertainment/naval/rooms/${id}/enter`, {}, spectator.token)
  assert.equal(again.role, 'SPECTATOR')
  // A directory join cannot use the room ID to bypass its invitation.
  await assert.rejects(api(`/entertainment/naval/rooms/${waiting.id}/enter`, {}, spectator.token), /邀请码/)
  await assert.rejects(api(`/entertainment/naval/rooms/${waiting.id}/enter`, { inviteCode: 'BADBAD' }, spectator.token), /邀请码/)
  const joined = await api(`/entertainment/naval/rooms/${waiting.id}/enter`, { inviteCode: waiting.inviteCode }, spectator.token)
  assert.equal(joined.phase, 'PLACEMENT')
  cleanup.push({ id: joined.id, token: spectator.token })
  await api(`/entertainment/naval/rooms/${waiting.id}/actions`, { type: 'SPECTATORS', value: false }, host.token)
  const cards = await api('/entertainment/naval/lobby/messages', null, spectator.token)
  const updated = cards.find(m => m.roomId === waiting.id).room
  assert.equal(updated.status, 'STARTED'); assert.equal(updated.allowSpectators, false)
  assert.deepEqual(errors, [])
  console.log('PASS: room directory and private access, AI guest live mirror and privacy toggle, one invite per round, responsive inline sticker trays and sending')
} finally {
  clearInterval(keepHost)
  for (const room of cleanup) { try { await api(`/entertainment/naval/rooms/${room.id}/actions`, { type: 'LEAVE' }, room.token) } catch {} }
  for (const context of contexts) await context.close()
  await browser.close()
}
