import assert from 'node:assert/strict'
import { chromium } from 'playwright-core'

const base = process.env.QA_BASE_URL || 'http://127.0.0.1:5174'
const browser = await chromium.launch({ executablePath: 'C:/Program Files/Google/Chrome/Application/chrome.exe', headless: true })
const errors = [], contexts = [], cleanup = []
async function guestPage(expired = false) {
  const context = await browser.newContext({ viewport: { width: 1280, height: 800 } }); contexts.push(context)
  if (expired) await context.addInitScript(() => {
    localStorage.setItem('entertainment_guest_token', 'expired-guest-token')
    localStorage.setItem('naval-guest-history', '[]')
    localStorage.setItem('naval-guest-formations', '[]')
    localStorage.setItem('naval-guest-local-marker', 'preserved')
  })
  let creations = 0
  await context.route('**/api/entertainment/guests', async route => {
    creations++
    await new Promise(resolve => setTimeout(resolve, 250))
    await route.continue()
  })
  const page = await context.newPage(); page.on('pageerror', error => errors.push(error.message))
  await page.goto(base + '/customer/entertainment/naval')
  await page.waitForFunction(() => /\d+ 人在线/.test(document.querySelector('.online-count')?.textContent || ''))
  return { page, creations: () => creations }
}
async function direct(page, code, data) {
  return page.evaluate(async ({ code, data }) => {
    const api = await import('/src/api/naval.js')
    if (code === 'create') return api.createNavalRoom(data)
    if (code === 'get') return api.getNavalRoom(data)
    return api.navalAction(data, { type: 'LEAVE' })
  }, { code, data })
}
try {
  const host = await guestPage(true)
  assert.equal(host.creations(), 1, 'invalid cached guest is replaced once')
  assert.equal(await host.page.evaluate(() => localStorage.getItem('naval-guest-local-marker')), 'preserved')
  assert.notEqual(await host.page.evaluate(() => localStorage.getItem('entertainment_guest_token')), 'expired-guest-token')
  await host.page.getByRole('button', { name: '匹配并创建房间', exact: true }).click()
  await host.page.waitForFunction(() => document.querySelector('.waiting-toolbar') && !document.querySelector('.match-presentation'))
  const roomId = await host.page.evaluate(() => localStorage.getItem('naval-guest-room'))
  cleanup.push({ id: roomId, token: await host.page.evaluate(() => localStorage.getItem('entertainment_guest_token')) })
  const peer = await guestPage()
  const matched = await direct(peer.page, 'create', { mode: 'MULTIPLAYER', autoMatch: true })
  cleanup.push({ id: matched.id, token: await peer.page.evaluate(() => localStorage.getItem('entertainment_guest_token')) })
  assert.equal(matched.id, roomId); assert.equal(matched.phase, 'PLACEMENT')
  assert.equal((await direct(host.page, 'get', roomId)).phase, 'PLACEMENT')

  const concurrent = await guestPage()
  const before = concurrent.creations()
  const result = await concurrent.page.evaluate(async () => {
    const api = await import('/src/api/naval.js')
    localStorage.setItem('entertainment_guest_token', 'revoked-during-page-session')
    const [presence, room, reviews] = await Promise.all([
      api.heartbeatNavalPresence(crypto.randomUUID()),
      api.createNavalRoom({ mode: 'MULTIPLAYER', autoMatch: false }),
      api.getNavalReviews(null, { page: 1 })
    ])
    return { presence, room, reviews }
  })
  cleanup.push({ id: result.room.id, token: await concurrent.page.evaluate(() => localStorage.getItem('entertainment_guest_token')) })
  assert(result.presence.onlineCount >= 1); assert.equal(concurrent.creations() - before, 1, 'concurrent 401s share one replacement')
  // Its existing presence row belongs to the previous identity; the UI must rotate
  // the page token rather than repeatedly failing with a forbidden heartbeat.
  await concurrent.page.waitForTimeout(8500)
  await concurrent.page.waitForFunction(() => /\d+ 人在线/.test(document.querySelector('.online-count')?.textContent || ''))

  const bounded = await concurrent.page.evaluate(async () => {
    const { withEntertainmentGuest } = await import('/src/api/entertainment.js')
    let attempts = 0
    try { await withEntertainmentGuest(() => { attempts++; const error = new Error('invalid'); error.code = 401; return Promise.reject(error) }) } catch (error) { return { attempts, code: error.code } }
  })
  assert.deepEqual(bounded, { attempts: 2, code: 401 }, 'recovery retries only once')
  const beforeLogin = concurrent.creations()
  const account = await concurrent.page.evaluate(async () => {
    const { withEntertainmentGuest } = await import('/src/api/entertainment.js')
    localStorage.setItem('token', 'account-test-only')
    let attempts = 0
    try { await withEntertainmentGuest(() => { attempts++; const error = new Error('expired account'); error.code = 401; return Promise.reject(error) }) } catch (error) { return { attempts, code: error.code } } finally { localStorage.removeItem('token') }
  })
  assert.deepEqual(account, { attempts: 1, code: 401 }); assert.equal(concurrent.creations(), beforeLogin, 'account failures never become anonymous')
  assert.deepEqual(errors, [])
  console.log('PASS: expired guest recovery, online count, real two-guest matching, concurrent renewal, presence re-keying, local data preservation and bounded retries')
} finally {
  for (const { token, id } of cleanup) {
    try { await fetch(`${base}/api/entertainment/naval/rooms/${id}/actions`, { method: 'POST', headers: { 'Content-Type': 'application/json', 'X-Guest-Token': token }, body: JSON.stringify({ type: 'LEAVE' }) }) } catch { /* Best-effort cleanup if the backend stopped. */ }
  }
  for (const context of contexts) await context.close()
  await browser.close()
}
