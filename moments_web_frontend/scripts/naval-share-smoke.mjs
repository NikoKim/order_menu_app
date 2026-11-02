import assert from 'node:assert/strict'
import { mkdir } from 'node:fs/promises'
import path from 'node:path'
import { chromium } from 'playwright-core'

const base = process.env.QA_BASE_URL || 'http://127.0.0.1:5174'
const output = process.env.QA_OUTPUT_DIR || path.resolve('../.agents/naval-qa')
await mkdir(output, { recursive: true })
const browser = await chromium.launch({ executablePath: process.env.QA_BROWSER || 'C:/Program Files/Google/Chrome/Application/chrome.exe', headless: true })
const errors = []
try {
  const api = async (url, data, token) => {
    const response = await fetch(base + url, { method: 'POST', headers: { 'Content-Type': 'application/json', ...(token ? { 'X-Guest-Token': token } : {}) }, body: data == null ? undefined : JSON.stringify(data) })
    const payload = await response.json()
    assert(response.ok && payload.code === 0, `Live share fixture API failed: ${JSON.stringify(payload)}`)
    return payload.data
  }
  const guest = await api('/api/entertainment/guests')
  const room = await api('/api/entertainment/naval/rooms', { mode: 'MULTIPLAYER', autoMatch: false }, guest.token)
  let liveCard
  try {
    const response = await fetch(`${base}/api/entertainment/naval/share/${room.inviteCode}?frontendOrigin=${encodeURIComponent(base)}`)
    const html = await response.text()
    assert.equal(response.status, 200)
    assert.match(response.headers.get('content-type'), /^text\/html/)
    assert(html.includes('property="og:title"') && html.includes('property="og:description"') && html.includes('property="og:image"'), 'Public room endpoint must serve crawler metadata before any JavaScript runs')
    assert(html.includes(`${base}/customer/entertainment/naval?invite=${room.inviteCode}`), 'Live card must open this frontend room instead of hardcoded localhost/production')
    assert(html.includes('allowfullscreen') && !html.includes('state_json') && !html.includes('"ships"') && !html.includes('"mines"'), 'Card must preserve fullscreen and exclude private board state')
    const invalid = await fetch(`${base}/api/entertainment/naval/share/invalid?frontendOrigin=${encodeURIComponent(base)}`)
    assert.equal(invalid.status, 404)
    assert((await invalid.text()).includes('前往海战大厅'), 'Expired/invalid share cards must remain useful')
    liveCard = { status: response.status, metadata: true, localOrigin: true, privateStateExcluded: true, unavailableCard: true }
  } finally {
    await api(`/api/entertainment/naval/rooms/${room.id}/actions`, { type: 'LEAVE' }, guest.token)
  }
  const page = await browser.newPage({ viewport: { width: 1280, height: 800 } })
  page.on('pageerror', error => errors.push(error.message))
  await page.goto(base + '/', { waitUntil: 'domcontentloaded' })
  await page.locator('#app').waitFor()
  const checks = await page.evaluate(async () => {
    const share = await import('/src/services/navalShare.js')
    const room = { inviteCode: 'abc234', phase: 'BATTLE', players: [{ name: '甲舰长' }, { name: '乙舰长' }], ships: 'PRIVATE_FLEET' }
    const assert = (condition, message) => { if (!condition) throw new Error(message) }
    const direct = new URL(share.buildNavalRoomUrl(room)), card = new URL(share.buildNavalShareUrl(room))
    assert(direct.origin === location.origin && direct.pathname === '/customer/entertainment/naval' && direct.searchParams.get('invite') === 'ABC234', 'Invitation must enter this website with the room query')
    assert(card.pathname === '/api/entertainment/naval/share/ABC234' && card.searchParams.get('frontendOrigin') === location.origin, 'Crawler URL must retain actual frontend origin')
    assert(!card.href.includes('PRIVATE_FLEET'), 'Invitation must not encode private fleet state')
    const details = share.navalShareDetails(room)
    assert(details.title.includes('甲舰长') && details.description.includes('乙舰长'), 'Share preview needs real room title and player names')
    assert(details.image.endsWith('/entertainment/naval/v2/harbor.png'), 'Share preview needs the actual pixel harbor bitmap')
    let invalidRejected = false
    try { share.buildNavalRoomUrl({ inviteCode: '<bad>' }) } catch { invalidRejected = true }
    assert(invalidRejected, 'Malformed invitation codes must be rejected')

    const copied = [], native = [], popups = []
    Object.defineProperty(navigator, 'clipboard', { configurable: true, value: { writeText: async text => copied.push(text) } })
    Object.defineProperty(navigator, 'share', { configurable: true, value: async data => { native.push(data) } })
    const wechat = await share.shareNavalRoom(room, { platform: 'wechat' })
    assert(wechat.status === 'copied' && wechat.message.includes('粘贴发送') && native.length === 0 && copied[0] === details.url, 'Explicit WeChat must provide a pasteable invitation instead of an unverifiable OS MIME handoff')
    copied.length = 0
    assert((await share.shareNavalRoom(room)).status === 'shared', 'System share should be used when available')
    assert(native[0].title === details.title && native[0].text.includes(details.description) && native[0].url === details.url, 'Native share must include title, content, and actual card URL')
    Object.defineProperty(navigator, 'share', { configurable: true, value: async () => { throw new DOMException('cancelled', 'AbortError') } })
    assert((await share.shareNavalRoom(room)).status === 'cancelled' && copied.length === 0, 'Cancelling native share must not copy or open another target')
    Object.defineProperty(navigator, 'share', { configurable: true, value: async () => { throw new DOMException('unavailable', 'NotAllowedError') } })
    const fallback = await share.shareNavalRoom(room, { platform: 'wechat' })
    assert(fallback.status === 'copied' && fallback.message.includes('微信聊天中粘贴') && copied[0] === details.url, 'Unavailable WeChat share needs an honest functional clipboard fallback')
    Object.defineProperty(navigator, 'share', { configurable: true, value: undefined })
    window.open = (url, target, features) => { popups.push({ url, target, features }); return {} }
    assert((await share.shareNavalRoom(room, { platform: 'qq' })).status === 'opened', 'QQ must open its real official sharing widget, not a fake completion')
    const qq = new URL(popups[0].url)
    assert(qq.origin === 'https://connect.qq.com' && qq.searchParams.get('url') === details.url && qq.searchParams.get('title') === details.title && qq.searchParams.get('summary') === details.description, 'QQ widget must receive the same room title/content/link')
    assert(popups[0].features.includes('noopener'), 'External QQ window must not retain an opener')

    const fullscreenHost = document.createElement('div'); document.body.append(fullscreenHost)
    Object.defineProperty(document, 'fullscreenElement', { configurable: true, value: fullscreenHost })
    Object.defineProperty(navigator, 'clipboard', { configurable: true, value: undefined })
    let selectionWasInFullscreen = false
    document.execCommand = command => { selectionWasInFullscreen = command === 'copy' && fullscreenHost.contains(document.activeElement) && document.activeElement.value === details.url; return true }
    await share.copyNavalShareLink(room)
    assert(selectionWasInFullscreen && !fullscreenHost.childElementCount, 'Insecure/LAN fallback must copy within fullscreen and remove its temporary input')
    document.execCommand = () => false
    let copyRejected = false
    try { await share.copyNavalShareLink(room) } catch { copyRejected = true }
    assert(copyRejected && !fullscreenHost.childElementCount, 'Failed copying must report failure and still clean up')
    delete document.fullscreenElement; fullscreenHost.remove()
    Object.defineProperty(navigator, 'clipboard', { configurable: true, value: { writeText: async text => copied.push(text) } })

    const url = '/src/views/entertainment/NavalShareDialog.vue', compiled = await (await fetch(url)).text()
    const vueUrl = compiled.match(/from\s+["']([^"']*\/vue\.js[^"']*)["']/)?.[1]
    assert(vueUrl, 'Cannot locate shared Vue runtime')
    const { createApp, h } = await import(vueUrl)
    const { default: Dialog } = await import(url)
    document.querySelector('#app').__vue_app__?.unmount()
    document.body.replaceChildren()
    const host = document.createElement('div'); document.body.append(host)
    window.shareClosed = 0
    window.shareFixture = createApp({ render: () => h(Dialog, { room, onClose: () => { window.shareClosed++ } }) })
    shareFixture.mount(host)
    return { directUrl: direct.href, shareUrl: card.href, title: details.title, native: true, qq: true, clipboard: true, fullscreenFallback: true, cancellation: true }
  })
  await page.locator('.room-share-preview img').evaluate(img => img.decode())
  assert.equal(await page.getByRole('dialog').count(), 1)
  assert.equal(await page.getByRole('button', { name: '关闭分享' }).evaluate(element => element === document.activeElement), true)
  await page.keyboard.press('Shift+Tab')
  assert.equal(await page.getByLabel('海战棋分享链接').evaluate(element => element === document.activeElement), true, 'Dialog must trap keyboard focus')
  await page.keyboard.press('Tab')
  assert.equal(await page.getByRole('button', { name: '关闭分享' }).evaluate(element => element === document.activeElement), true)
  await page.getByRole('button', { name: '复制链接' }).click()
  await page.getByRole('status').filter({ hasText: '房间链接已复制' }).waitFor()
  assert.equal(await page.locator('.room-share-preview strong').evaluate(element => getComputedStyle(element).fontFamily === getComputedStyle(element.parentElement).fontFamily), true, 'Invitation code must use the website typeface')
  await page.screenshot({ path: path.join(output, 'naval-share-desktop.png') })
  await page.setViewportSize({ width: 844, height: 390 })
  await page.screenshot({ path: path.join(output, 'naval-share-mobile-landscape.png') })
  for (const viewport of [{ width: 844, height: 390 }, { width: 375, height: 667 }]) {
    await page.setViewportSize(viewport)
    const fit = await page.getByRole('dialog').evaluate(element => { const box = element.getBoundingClientRect(); return box.left >= 0 && box.right <= innerWidth && box.top >= 0 && box.bottom <= innerHeight && document.body.scrollWidth <= innerWidth })
    assert(fit, `Share dialog must fit ${viewport.width}x${viewport.height}`)
    const image = await page.locator('.room-share-preview img').evaluate(img => ({ complete: img.complete, width: img.naturalWidth }))
    assert(image.complete && image.width > 100, 'Pixel harbor cover must actually load')
  }
  await page.keyboard.press('Escape')
  assert.equal(await page.evaluate(() => window.shareClosed), 1)
  assert.deepEqual(errors, [])
  console.log(JSON.stringify({ ...checks, liveCard, responsiveDialog: true, focusTrap: true, errors, output }, null, 2))
} finally {
  await browser.close()
}
