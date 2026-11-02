import assert from 'node:assert/strict'
import { mkdir } from 'node:fs/promises'
import path from 'node:path'
import { chromium } from 'playwright-core'

const base = process.env.QA_BASE_URL || 'http://127.0.0.1:5174'
const gamePath = '/customer/entertainment/naval'
const output = process.env.QA_OUTPUT_DIR || path.resolve('../.agents/naval-qa')
await mkdir(output, { recursive: true })
const browser = await chromium.launch({ executablePath: process.env.QA_CHROME_PATH || 'C:/Program Files/Google/Chrome/Application/chrome.exe', headless: true })
const contexts = [], errors = [], checks = []
let activeRoom

const snapshot = page => page.evaluate(() => JSON.parse(localStorage.getItem('naval-guest-last-online') || 'null'))
const history = page => page.evaluate(() => JSON.parse(localStorage.getItem('naval-guest-history') || '[]'))
async function state(page, predicate, timeout = 20_000) {
  const until = Date.now() + timeout
  let latest
  while (Date.now() < until) {
    await page.bringToFront()
    latest = await snapshot(page)
    if (latest && predicate(latest)) return latest
    await page.waitForTimeout(120)
  }
  throw new Error(`Timed out waiting for room state: ${JSON.stringify(latest)}`)
}
async function settled(page) {
  await page.bringToFront()
  await page.locator('.match-presentation').waitFor({ state: 'detached' })
  await page.locator('.launch-overlay').waitFor({ state: 'detached' })
  await page.locator('.stage-scene-leave-active').waitFor({ state: 'detached' })
}
async function api(page, url, data) {
  return page.evaluate(async ({ url, data }) => {
    const response = await fetch(`/api/entertainment/naval${url}`, { method: data === undefined ? 'GET' : 'POST', headers: { 'Content-Type': 'application/json', 'X-Guest-Token': localStorage.getItem('entertainment_guest_token') }, ...(data === undefined ? {} : { body: JSON.stringify(data) }) })
    return { status: response.status, payload: await response.json() }
  }, { url, data })
}
async function guest(invite, mobile = false) {
  const context = await browser.newContext({ viewport: mobile ? { width: 844, height: 390 } : { width: 1440, height: 900 }, ...(mobile ? { isMobile: true, hasTouch: true, deviceScaleFactor: 1 } : {}), permissions: ['clipboard-read', 'clipboard-write'] })
  contexts.push(context)
  const page = await context.newPage()
  page.setDefaultTimeout(20_000)
  page.on('pageerror', error => errors.push(error.message))
  await page.addInitScript(() => {
    window.__fullRoomTrace = []
    let wasFull = false
    new MutationObserver(() => {
      const full = Boolean(document.querySelector('[aria-label="房间已满"]'))
      if (full !== wasFull) { window.__fullRoomTrace.push({ full, at: performance.now() }); wasFull = full }
    }).observe(document, { subtree: true, childList: true })
  })
  await page.goto(base + gamePath + (invite ? `?invite=${invite}` : ''))
  if (!invite) await page.getByRole('heading', { name: '选择出航方式' }).waitFor()
  return page
}
async function view(page, mode) {
  await settled(page)
  await page.locator('.spectator-toolbar .custom-select-trigger').click()
  const menu = page.locator('.custom-select-menu')
  await menu.getByRole('button').nth({ BOTH: 0, PLAYER_0: 1, PLAYER_1: 2 }[mode]).click()
  const value = await state(page, room => room.viewMode === mode)
  await settled(page)
  await page.locator('.boards canvas').nth(1).waitFor()
  return value
}
async function pixels(page) {
  await settled(page)
  await page.waitForFunction(() => document.querySelectorAll('.boards canvas').length === 2 && !document.querySelector('.boards .naval-board.is-loading'))
  await page.waitForTimeout(800)
  const result = await page.locator('.boards canvas').evaluateAll(canvases => canvases.map(canvas => {
    const pixels = canvas.getContext('2d').getImageData(0, 0, canvas.width, canvas.height).data
    const colors = new Set(); let opaque = 0, dark = 0
    for (let i = 0; i < pixels.length; i += 4) { if (pixels[i + 3]) opaque++; if (pixels[i] < 110 && pixels[i + 1] < 130 && pixels[i + 2] < 140) dark++; colors.add(`${pixels[i] >> 4},${pixels[i + 1] >> 4},${pixels[i + 2] >> 4}`) }
    const box = canvas.getBoundingClientRect()
    return { width: canvas.width, height: canvas.height, opaque, dark, colors: colors.size, renderer: canvas.dataset.renderer, left: box.left, right: box.right, top: box.top, bottom: box.bottom }
  }))
  assert.equal(result.length, 2, 'Spectators must get two actual game canvases')
  for (const canvas of result) { assert.equal(canvas.renderer, 'phaser-3.90'); assert(canvas.opaque > 50_000 && canvas.colors >= 5 && canvas.dark > 50, `Game canvas must contain rendered sea/axis pixels: ${JSON.stringify(canvas)}`) }
  assert(result[0].colors > 20, 'Visible own fleet must render detailed bitmap colors, not only flat sea tiles')
  return result
}
async function cell(page, x, y) {
  await settled(page)
  await page.waitForFunction(() => document.querySelector('.weapons>button') && !document.querySelector('.weapons>button').disabled)
  const canvas = page.locator('.enemy-panel canvas'), box = await canvas.boundingBox()
  await canvas.click({ position: { x: (48 + x * 48) / 624 * box.width, y: (48 + y * 48) / 624 * box.height } })
}
async function leave(page) {
  await settled(page)
  await page.getByRole('button', { name: '离开对局', exact: true }).click()
  await page.getByRole('button', { name: '确认离开', exact: true }).click()
  await page.getByRole('heading', { name: '选择出航方式' }).waitFor()
}

try {
  const host = await guest()
  await host.getByRole('switch').uncheck()
  await host.getByRole('button', { name: '创建邀请房间', exact: true }).click()
  const waiting = await state(host, room => room.phase === 'WAITING')
  activeRoom = waiting.id
  await settled(host)
  await host.getByRole('button', { name: '分享房间', exact: true }).click()
  const share = host.getByRole('dialog', { name: '分享海战房间' })
  await share.waitFor()
  assert((await share.locator('h3').textContent()).includes(waiting.players[0].name))
  const shareUrl = await share.getByLabel('海战棋分享链接').inputValue()
  const shareResponse = await host.request.get(shareUrl)
  assert.equal(shareResponse.status(), 200)
  assert((await shareResponse.text()).includes(`invite=${waiting.inviteCode}`))
  await share.getByRole('button', { name: '复制链接' }).click()
  await share.getByRole('status').filter({ hasText: '已复制' }).waitFor()
  await share.getByRole('button', { name: '关闭分享' }).click()
  await share.waitFor({ state: 'detached' })
  checks.push('root share dialog, actual room title, functional copy, live crawler card')

  const peer = await guest(waiting.inviteCode)
  const paired = await state(peer, room => room.phase === 'PLACEMENT')
  assert.equal(paired.id, waiting.id, 'Invitation query must autojoin without a second button click')
  await state(host, room => room.phase === 'PLACEMENT')
  await settled(peer); await settled(host)
  checks.push('shared invitation URL autojoins the correct room')

  const observer = await guest()
  await observer.getByRole('textbox', { name: '海战棋邀请码' }).fill(waiting.inviteCode)
  await observer.getByRole('button', { name: '加入房间', exact: true }).click()
  await observer.getByRole('dialog', { name: '房间已满' }).waitFor()
  await observer.waitForTimeout(1800)
  assert(await observer.getByRole('dialog', { name: '房间已满' }).isVisible(), 'Full room warning must not disappear immediately')
  const audience = await state(observer, room => room.role === 'SPECTATOR')
  await settled(observer)
  const fullTrace = await observer.evaluate(() => window.__fullRoomTrace)
  const shown = fullTrace.find(item => item.full), hidden = fullTrace.find(item => !item.full && item.at > shown.at)
  assert(shown && hidden && hidden.at - shown.at >= 2850, `Full room warning must wait three seconds: ${JSON.stringify(fullTrace)}`)
  assert.equal(audience.viewMode, 'BOTH'); assert.equal(audience.you, -1)
  assert(audience.players.every(player => player.ships.length === 10))
  const observerName = audience.spectators.find(person => person.id === audience.ownSpectatorId).name
  for (const player of [host, peer]) {
    await state(player, room => room.spectators.some(person => person.name === observerName))
    await player.locator('.naval-audience .audience-person').filter({ hasText: observerName }).waitFor()
  }
  await observer.getByRole('button', { name: '战局聊天', exact: true }).click()
  await observer.locator('.battle-chat').waitFor()
  assert.equal(await observer.getByRole('textbox', { name: '战局消息' }).count(), 0)
  assert.equal((await api(observer, `/rooms/${waiting.id}/actions`, { type: 'CHAT', text: 'spectator must not send' })).status, 403)
  await observer.getByRole('button', { name: '收起聊天', exact: true }).click()
  for (const index of [0, 1]) {
    const selected = await view(observer, `PLAYER_${index}`)
    assert.equal(selected.viewAs, index)
    assert.equal(selected.players[index].ships.length, 10)
    assert.deepEqual(selected.players[1 - index].ships, [])
    assert.deepEqual(selected.players[1 - index].mines, [])
    assert.equal(selected.players[1 - index].coins, 0)
    assert((await observer.locator('.own-panel>header h2').textContent()).includes(selected.players[index].name), 'True player POV must put that player on the left')
    await pixels(observer)
  }
  const roomEndpoint = `**/api/entertainment/naval/rooms/${waiting.id}`
  await observer.route(roomEndpoint, route => route.fulfill({ status: 403, contentType: 'application/json', body: JSON.stringify({ code: 403, message: '观众席已过期', data: null }) }), { times: 1 })
  const expiredPoll = observer.waitForResponse(response => response.url().endsWith(`/entertainment/naval/rooms/${waiting.id}`) && response.request().method() === 'GET' && response.status() === 403)
  const reenterAudience = observer.waitForResponse(response => response.url().endsWith('/entertainment/naval/rooms/spectate') && response.request().method() === 'POST')
  await observer.bringToFront()
  await observer.evaluate(() => document.dispatchEvent(new Event('visibilitychange')))
  await expiredPoll
  const recoveredResponse = await reenterAudience, recoveredPayload = await recoveredResponse.json()
  assert.equal(recoveredResponse.status(), 200)
  assert.equal(recoveredResponse.request().postDataJSON().viewMode, 'PLAYER_1', 'Expired spectator membership must reenter with the selected POV, not silently reset to BOTH')
  const recovered = await state(observer, room => room.role === 'SPECTATOR' && room.viewMode === 'PLAYER_1' && room.serverTime >= recoveredPayload.data.serverTime)
  assert.equal(recovered.id, waiting.id)
  assert.equal(recovered.players[0].ships.length, 0)
  assert.equal(recovered.players[1].ships.length, 10)
  const healthyPoll = await observer.waitForResponse(response => response.url().endsWith(`/entertainment/naval/rooms/${waiting.id}`) && response.request().method() === 'GET' && response.status() === 200)
  assert.equal((await healthyPoll.json()).data.viewMode, 'PLAYER_1')
  assert.equal(await observer.locator('.connection-warning').count(), 0, 'Recovered spectator must not remain stuck in an endless connection warning')
  checks.push('one expired spectator GET403 auto-rejoins through real backend and preserves selected POV without a connection loop')
  await view(observer, 'BOTH')
  checks.push(`full room waits ${Math.round(hidden.at - shown.at)}ms then auto-spectates`, 'players see spectators', 'three component-select views enforce server redaction and actual player POV', 'spectators cannot send chat')

  const phone = await guest(waiting.inviteCode, true)
  await phone.getByRole('dialog', { name: '横屏全屏限制' }).waitFor()
  assert.equal(await snapshot(phone), null, 'Mobile invitation must wait for an actual fullscreen gesture')
  await phone.getByRole('button', { name: '全屏并锁定横屏' }).click()
  await state(phone, room => room.role === 'SPECTATOR')
  await settled(phone)
  assert(await phone.evaluate(() => Boolean(document.fullscreenElement)), 'Mobile spectator must actually enter fullscreen')
  checks.push('mobile shared link respects fullscreen gate and enters real fullscreen spectator mode')

  for (const player of [host, peer]) {
    await settled(player)
    await player.getByRole('button', { name: '下一步：战备补给', exact: true }).click()
    await state(player, room => room.players[room.you].stage === 'SUPPLY')
  }
  await host.getByRole('button', { name: '购买战斗机', exact: true }).click()
  await host.getByRole('button', { name: '购买雷达', exact: true }).click()
  await state(host, room => room.players[room.you].coins === 230)
  for (const player of [host, peer]) {
    await player.getByRole('button', { name: '完成战备', exact: true }).click()
    await state(player, room => room.players[room.you].ready)
  }
  let hostBattle = await state(host, room => room.phase === 'BATTLE')
  const peerBattle = await state(peer, room => room.phase === 'BATTLE')
  await state(observer, room => room.phase === 'BATTLE')
  await state(phone, room => room.phase === 'BATTLE')
  await observer.bringToFront()
  await observer.locator('.own-panel .board-turn').waitFor()
  await observer.waitForFunction(() => !document.querySelector('.phase-announcement'))
  const desktopPixels = await pixels(observer)
  await observer.screenshot({ path: path.join(output, 'v4-spectator-desktop.png'), fullPage: true })
  await phone.bringToFront()
  await phone.locator('.mobile-player-status.is-left').waitFor()
  await phone.waitForFunction(() => !document.querySelector('.phase-announcement'))
  const phonePixels = await pixels(phone)
  assert(phonePixels.every(canvas => canvas.left >= 0 && canvas.right <= 845 && canvas.bottom <= 391), `Mobile spectator boards must fit 844x390: ${JSON.stringify(phonePixels)}`)
  await phone.screenshot({ path: path.join(output, 'v4-spectator-mobile-landscape.png') })
  checks.push({ desktopSpectatorCanvas: desktopPixels, mobileSpectatorCanvas: phonePixels })

  await host.bringToFront()
  await host.getByRole('button', { name: '战局聊天', exact: true }).click()
  const chatText = '舰队集合，准备出击'
  await host.getByRole('textbox', { name: '战局消息' }).fill(chatText)
  await host.getByRole('button', { name: '发送消息', exact: true }).click()
  await host.locator('.own-panel .player-chat-bubble').filter({ hasText: chatText }).waitFor()
  await state(peer, room => room.messages.some(message => message.text === chatText))
  await peer.locator('.enemy-panel .player-chat-bubble').filter({ hasText: chatText }).waitFor()
  await state(observer, room => room.messages.some(message => message.text === chatText))
  await observer.locator('.own-panel .player-chat-bubble').filter({ hasText: chatText }).waitFor()
  await host.getByRole('button', { name: '收起聊天', exact: true }).click()
  checks.push('chat appears beside the correct player name on sender, opponent, and spectator pages')
  const departedChatText = '右翼舰队收到，保持队形'
  await peer.bringToFront()
  await peer.getByRole('button', { name: '战局聊天', exact: true }).click()
  await peer.getByRole('textbox', { name: '战局消息' }).fill(departedChatText)
  await peer.getByRole('button', { name: '发送消息', exact: true }).click()
  const peerWithChat = await state(peer, room => room.messages.some(message => message.text === departedChatText))
  const departedMessage = peerWithChat.messages.find(message => message.text === departedChatText)
  assert.equal(departedMessage.name, peerBattle.players[peerBattle.you].name, 'Messages must snapshot the actual sender name')
  assert.equal(departedMessage.senderId, peerBattle.players[peerBattle.you].id, 'Messages must snapshot sender identity independently of the seat number')
  await peer.getByRole('button', { name: '收起聊天', exact: true }).click()

  hostBattle = await state(host, room => room.phase === 'BATTLE')
  if (hostBattle.turn !== hostBattle.you) {
    const own = hostBattle.players[hostBattle.you]
    const water = Array.from({ length: 144 }, (_, i) => ({ x: i % 12, y: Math.floor(i / 12) })).find(cell => !own.ships.some(ship => cell.x >= ship.x && cell.x < ship.x + ship.w && cell.y >= ship.y && cell.y < ship.y + ship.h))
    await cell(peer, water.x, water.y)
    hostBattle = await state(host, room => room.turn === room.you)
  }
  await host.bringToFront(); await host.keyboard.press('2')
  assert(await host.locator('[data-tutorial="naval-weapon-fighter"]').evaluate(button => button.classList.contains('selected')))
  await host.getByRole('button', { name: '战局聊天', exact: true }).click()
  await host.getByRole('textbox', { name: '战局消息' }).focus(); await host.keyboard.press('1')
  assert(await host.locator('[data-tutorial="naval-weapon-fighter"]').evaluate(button => button.classList.contains('selected')), 'Typing digits into chat must not switch weapons')
  await host.getByRole('button', { name: '收起聊天', exact: true }).click()
  await host.keyboard.press('1')
  const target = peerBattle.players[peerBattle.you].ships.find(ship => ship.w * ship.h > 1)
  await cell(host, target.x, target.y)
  await state(host, room => room.players[1 - room.you].shots.some(shot => shot.x === target.x && shot.y === target.y && shot.result === 'hit'))
  assert(await host.locator('.weapons>button').first().isEnabled(), 'Readable hit animation must not block the next attack')
  checks.push('number hotkeys exclude chat typing', 'hit animation does not block the next attack')

  const leftAt = Date.now()
  await leave(peer)
  const finished = await state(host, room => room.phase === 'FINISHED')
  await host.getByRole('dialog', { name: '战局结算' }).waitFor()
  assert(Date.now() - leftAt < 5000, 'Opponent departure must immediately settle rather than wait for a turn timeout')
  assert.equal(finished.finishReason, 'PLAYER_LEFT'); assert.equal(finished.winner, finished.you)
  await state(observer, room => room.phase === 'FINISHED')
  await observer.getByRole('dialog', { name: '战局结算' }).waitFor()
  assert.equal((await history(observer)).length, 0, 'Watching a result must not create a personal win/loss')
  assert.equal((await history(phone)).length, 0)
  const oldNames = await observer.locator('.settlement-players .player-heading>strong').allTextContents()
  await observer.locator('.settlement-screen .seat-choice').nth(1).click()
  const claimed = await state(observer, room => room.role === 'PLAYER' && room.you === 1)
  assert.equal(claimed.round, 1); assert.equal(claimed.phase, 'FINISHED')
  assert.equal(claimed.resultYou, -1, 'Seat replacement must not inherit the departed player historical result')
  assert.equal((await history(observer)).length, 0)
  assert.deepEqual(await observer.locator('.settlement-players .player-heading>strong').allTextContents(), oldNames, 'Old settlement must retain original participants after a spectator takes a seat')
  const oldMessageAfterClaim = claimed.messages.find(message => message.id === departedMessage.id)
  assert.equal(oldMessageAfterClaim.name, departedMessage.name)
  assert.equal(oldMessageAfterClaim.senderId, departedMessage.senderId)
  await observer.locator('.settlement-screen>div>header button').click()
  await observer.getByRole('dialog', { name: '战局结算' }).waitFor({ state: 'detached' })
  await observer.getByRole('button', { name: '战局聊天', exact: true }).click()
  const oldChatRow = observer.locator('.chat-message').filter({ has: observer.getByText(departedChatText, { exact: true }) })
  await oldChatRow.waitFor()
  assert.equal(await oldChatRow.locator('span').textContent(), departedMessage.name, 'Replacing a seat must not rewrite an old message sender to the new player name')
  assert.equal(await oldChatRow.evaluate(row => row.classList.contains('mine')), false, 'Replacement player must not inherit departed player chat attribution')
  await observer.getByRole('button', { name: '收起聊天', exact: true }).click()
  await observer.getByRole('button', { name: '战局结算', exact: true }).click()
  await observer.getByRole('dialog', { name: '战局结算' }).waitFor()
  checks.push('old chat keeps immutable departed sender name and identity after a spectator takes that seat')
  checks.push('opponent leave immediately opens result page', 'spectator can take vacated seat without inheriting old result or old statistics')

  await observer.getByRole('button', { name: '取消准备', exact: true }).click()
  await state(observer, room => !room.players[room.you].continueRequested)
  await host.getByRole('button', { name: '继续下一局', exact: true }).click()
  await state(host, room => room.players[room.you].continueRequested)
  await observer.waitForTimeout(1500)
  assert.equal((await snapshot(observer)).phase, 'FINISHED', 'One consent is not enough to start the next round')
  await observer.getByRole('button', { name: '继续下一局', exact: true }).click()
  const secondRound = await state(observer, room => room.round === 2 && room.phase === 'PLACEMENT')
  await state(host, room => room.round === 2 && room.phase === 'PLACEMENT')
  const globalSecond = await state(phone, room => room.round === 2 && room.phase === 'PLACEMENT')
  assert.equal(secondRound.id, waiting.id); assert.equal(secondRound.inviteCode, waiting.inviteCode)
  assert.notEqual(secondRound.gameId, finished.gameId)
  for (const player of globalSecond.players) {
    assert.equal(player.coins, 300); assert.equal(player.ships.length, 10); assert.equal(player.stage, 'PLACEMENT')
    assert(player.ships.every(ship => !ship.hits.length)); assert.deepEqual(player.shots, []); assert.deepEqual(player.mines, []); assert.deepEqual(player.aa, []); assert.deepEqual(player.radar, [])
    assert(Object.values(player.inventory).every(count => count === 0)); assert.equal(player.ready, false)
  }
  assert.deepEqual(globalSecond.messages, [])
  assert.equal((await history(observer)).length, 0)
  assert.equal((await history(host)).length, 1)
  await settled(observer); await observer.locator('.fleet-roster').waitFor()
  assert.equal(await observer.getByRole('dialog', { name: '战局结算' }).count(), 0)
  checks.push('both-player continue consent gates rematch', 'same room and invite, new round identity, fresh ten-ship fleets and 300 coins')

  await leave(observer)
  await state(host, room => room.round === 2 && room.phase === 'FINISHED')
  await state(phone, room => room.round === 2 && room.phase === 'FINISHED')
  const replacementHistory = await history(observer), hostHistory = await history(host)
  assert.equal(replacementHistory.length, 1)
  assert.equal(replacementHistory[0].id, secondRound.gameId, 'Replacement player should only receive their actual second-round result')
  assert.equal(hostHistory.length, 2); assert.equal(new Set(hostHistory.map(item => item.id)).size, 2)
  assert.equal((await history(phone)).length, 0)
  checks.push('round-aware local history excludes observer-only round and preserves both actual player rounds')
  assert.deepEqual(errors, [])
  console.log(JSON.stringify({ passed: true, output, checks, errors }, null, 2))
} catch (error) {
  for (const [index, context] of contexts.entries()) {
    for (const page of context.pages()) { try { await page.screenshot({ path: path.join(output, `v4-failure-${index}.png`), fullPage: true }) } catch {} }
  }
  throw error
} finally {
  if (activeRoom) for (const context of contexts) {
    const page = context.pages()[0]
    if (page && !page.isClosed()) { try { await api(page, `/rooms/${activeRoom}/actions`, { type: 'LEAVE' }) } catch {} }
  }
  for (const context of contexts) await context.close()
  await browser.close()
}
