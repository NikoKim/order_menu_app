import assert from 'node:assert/strict'
import { mkdir } from 'node:fs/promises'
import path from 'node:path'
import { chromium } from 'playwright-core'
import { generateFleet } from '../src/games/navalEngine.js'

const base = process.env.QA_BASE_URL || 'http://127.0.0.1:5174'
const output = process.env.QA_OUTPUT_DIR || path.resolve('../.agents/naval-qa')
const gamePath = '/customer/entertainment/naval'
const errors = []
const checks = []
await mkdir(output, { recursive: true })

async function waitForBackend() {
  const until = Date.now() + 120_000
  let lastError
  while (Date.now() < until) {
    try {
      const response = await fetch(`${base}/api/entertainment/modes`, { signal: AbortSignal.timeout(2500) })
      if (response.ok && (await response.json()).code === 0) return
      lastError = `HTTP ${response.status}`
    } catch (error) { lastError = error.message }
    await new Promise(resolve => setTimeout(resolve, 1000))
  }
  throw new Error(`Backend did not become ready: ${lastError}`)
}

function snapshot(page) {
  return page.evaluate(() => JSON.parse(localStorage.getItem('naval-guest-last-online') || 'null'))
}

async function waitState(page, predicate, timeout = 20_000) {
  await page.bringToFront()
  const until = Date.now() + timeout
  let latest
  while (Date.now() < until) {
    latest = await snapshot(page)
    if (latest && predicate(latest)) return latest
    await page.waitForTimeout(150)
  }
  throw new Error(`Timed out waiting for room state: ${JSON.stringify(latest)}`)
}

async function settled(page) {
  await page.bringToFront()
  await page.locator('.match-presentation').waitFor({ state: 'detached' })
  await page.locator('.launch-overlay').waitFor({ state: 'detached' })
}

async function boardCell(page, x, y, own = false) {
  await settled(page)
  if (!own) await page.waitForFunction(() => {
    const shot = document.querySelector('.weapons > button')
    return shot && !shot.disabled
  })
  const canvas = page.locator(`${own ? '.own-panel' : '.enemy-panel'} canvas`)
  const box = await canvas.boundingBox()
  assert(box, 'enemy board must be visible')
  await canvas.click({ position: { x: (48 + x * 48) / 624 * box.width, y: (48 + y * 48) / 624 * box.height } })
}

async function watchMatching(page) {
  await page.evaluate(() => {
    window.__navalMatchTrace = []
    window.__navalMatchObserver?.disconnect()
    const record = () => {
      const overlay = document.querySelector('.match-presentation')
      const stage = !overlay ? 'NONE' : overlay.classList.contains('match-presentation-leave-active') ? 'LEAVING' : overlay.dataset.stage
      const trace = window.__navalMatchTrace
      if (trace.at(-1)?.stage !== stage) trace.push({ stage, at: performance.now() })
    }
    window.__navalMatchObserver = new MutationObserver(record)
    window.__navalMatchObserver.observe(document.body, { subtree: true, childList: true, attributes: true, attributeFilter: ['data-stage', 'class'] })
    record()
  })
}

async function assertMatching(page, paired = true) {
  await settled(page)
  const trace = await page.evaluate(() => window.__navalMatchTrace)
  const search = trace.find(event => event.stage === 'SEARCHING')
  const found = trace.find(event => event.stage === 'FOUND')
  const leaving = trace.find(event => event.stage === 'LEAVING' && event.at > (search?.at || Infinity))
  assert(search && leaving && (paired ? found : !found), `matching must present the appropriate bounded stages: ${JSON.stringify(trace)}`)
  assert((found || leaving).at - search.at >= 1350, `search presentation must last at least 1.4s: ${JSON.stringify(trace)}`)
  if (paired) assert(leaving.at - found.at >= 1450, `found presentation must last at least 1.5s: ${JSON.stringify(trace)}`)
  return { searchingMs: Math.round((found || leaving).at - search.at), foundMs: found ? Math.round(leaving.at - found.at) : 0 }
}

async function refreshPresence(page, success = true) {
  await page.bringToFront()
  const pending = page.waitForResponse(response => response.url().endsWith('/entertainment/naval/presence') && response.request().method() === 'POST')
  await page.evaluate(() => document.dispatchEvent(new Event('visibilitychange')))
  const response = await pending
  const payload = await response.json()
  if (!success) {
    assert.equal(response.status(), 503)
    await page.locator('.online-count').getByText('在线人数暂不可用', { exact: true }).waitFor()
    return null
  }
  assert.equal(response.status(), 200)
  assert.equal(payload.code, 0)
  assert(Number.isInteger(payload.data.onlineCount))
  await page.locator('.online-count').getByText(`${payload.data.onlineCount} 人在线`, { exact: true }).waitFor()
  return { count: payload.data.onlineCount, token: response.request().postDataJSON().pageToken }
}

async function api(page, url, method = 'GET', data, authenticated = true) {
  return page.evaluate(async ({ url, method, data, authenticated }) => {
    const token = localStorage.getItem('entertainment_guest_token')
    const response = await fetch(`/api/entertainment/naval${url}`, {
      method,
      headers: { 'Content-Type': 'application/json', ...(authenticated ? { 'X-Guest-Token': token } : {}) },
      ...(data === undefined ? {} : { body: JSON.stringify(data) })
    })
    return { status: response.status, payload: await response.json() }
  }, { url, method, data, authenticated })
}

function checkPrivateView(room) {
  assert.equal(room.players.length, 2)
  const self = room.players[room.you]
  const enemy = room.players[1 - room.you]
  assert.equal(self.ships.length, 10, 'own fleet must remain available')
  assert.equal(self.ships.reduce((sum, ship) => sum + ship.w * ship.h, 0), 20)
  assert.equal(room.rulesVersion, 2)
  assert(enemy.ships.every(ship => ship.hits.length === ship.w * ship.h), 'only sunk opponent ships may be returned')
  assert.deepEqual(enemy.mines, [], 'opponent mines must not leak')
  assert.deepEqual(enemy.aa, [], 'opponent AA must not leak')
  assert(Object.values(enemy.inventory).every(count => count === 0), 'opponent inventory must not leak')
  assert(room.players.every(player => !('key' in player)), 'private member identity keys must not leak')
}

async function ready(page) {
  await page.getByRole('button', { name: '完成战备', exact: true }).click()
}

async function lockLayout(page) {
  await settled(page)
  await page.getByRole('button', { name: '下一步：战备补给', exact: true }).click()
  await waitState(page, room => room.players[room.you].stage === 'SUPPLY')
  assert.equal(await page.locator('.enemy-panel').count(), 0, 'enemy board must remain hidden until battle')
}

async function leave(page) {
  await settled(page)
  await page.getByRole('button', { name: '离开对局', exact: true }).click()
  await page.getByRole('button', { name: '确认离开', exact: true }).click()
  await page.getByRole('heading', { name: '选择出航方式' }).waitFor()
}

const browser = await chromium.launch({ executablePath: process.env.QA_CHROME_PATH || 'C:/Program Files/Google/Chrome/Application/chrome.exe', headless: true })
const contexts = []
async function guest() {
  const context = await browser.newContext({ viewport: { width: 1440, height: 900 } })
  contexts.push(context)
  const page = await context.newPage()
  page.setDefaultTimeout(20_000)
  page.on('pageerror', error => errors.push(error.message))
  await page.goto(base + gamePath)
  await page.getByRole('heading', { name: '选择出航方式' }).waitFor()
  await page.locator('.online-count').filter({ hasText: /^\d+ 人在线$/ }).waitFor()
  return page
}

try {
  await waitForBackend()
  const first = await guest()
  const initialPresence = await refreshPresence(first)
  const second = await guest()
  const secondPresence = await refreshPresence(second)
  assert.equal(secondPresence.count, initialPresence.count + 1, 'a distinct browser identity must add one naval-page player')
  assert.notEqual(secondPresence.token, initialPresence.token, 'each page must have its own UUID')
  const sibling = await first.context().newPage()
  await sibling.goto(base + gamePath)
  await sibling.getByRole('heading', { name: '选择出航方式' }).waitFor()
  await sibling.locator('.online-count').filter({ hasText: /^\d+ 人在线$/ }).waitFor()
  const siblingPresence = await refreshPresence(sibling)
  assert.equal(siblingPresence.count, secondPresence.count, 'two tabs with the same guest identity count once')
  assert.notEqual(siblingPresence.token, initialPresence.token, 'deduplicated identity still uses separate tab tokens')
  const sameGuest = await sibling.evaluate(() => localStorage.getItem('entertainment_guest_token'))
  assert.equal(sameGuest, await first.evaluate(() => localStorage.getItem('entertainment_guest_token')))
  await sibling.close()
  assert.equal((await refreshPresence(first)).count, secondPresence.count, 'closing a sibling tab must not remove the remaining player')
  const leftPresence = second.waitForResponse(response => response.url().includes('/entertainment/naval/presence/') && response.request().method() === 'DELETE')
  await second.getByRole('link', { name: '返回娱乐', exact: true }).click()
  assert.equal((await leftPresence).status(), 200)
  assert.equal((await refreshPresence(first)).count, initialPresence.count, 'leaving the naval page must remove its distinct identity')
  await second.goto(base + gamePath)
  await second.getByRole('heading', { name: '选择出航方式' }).waitFor()
  await second.locator('.online-count').filter({ hasText: /^\d+ 人在线$/ }).waitFor()
  assert.equal((await refreshPresence(second)).count, secondPresence.count, 'returning to the page must restore presence')
  const presenceRoute = '**/api/entertainment/naval/presence'
  await first.route(presenceRoute, route => route.fulfill({ status: 503, contentType: 'application/json', body: JSON.stringify({ code: 503, message: 'QA unavailable', data: null }) }))
  await refreshPresence(first, false)
  await first.unroute(presenceRoute)
  assert.equal((await refreshPresence(first)).count, secondPresence.count, 'failed heartbeat must recover without a fabricated fallback count')
  checks.push('database-backed page presence and distinct-context count', 'same-identity tab deduplication and tab-specific UUIDs', 'page-leave presence removal', 'unavailable presence state and recovery without fake counts')

  await first.getByRole('button', { name: '匹配并创建房间', exact: true }).click()
  const waiting = await waitState(first, room => room.phase === 'WAITING')
  assert.equal(waiting.autoMatch, true)
  assert.match(waiting.inviteCode, /^[A-Z2-9]{6}$/)
  assert.equal(waiting.players.filter(player => player.occupied !== false).length, 1)
  await settled(first)
  await first.reload()
  await first.getByRole('heading', { name: '选择出航方式' }).waitFor()
  await first.getByRole('textbox', { name: '海战棋邀请码' }).fill(waiting.inviteCode)
  await watchMatching(first)
  await first.getByRole('button', { name: '加入房间', exact: true }).click()
  await first.locator('.waiting-dock').waitFor()
  await assertMatching(first, false)
  assert.equal((await snapshot(first)).phase, 'WAITING', 'joining the current waiting room must settle without a permanent matching overlay')
  checks.push('own waiting-room invite rejoins with bounded search presentation')
  await watchMatching(second)
  await second.getByRole('button', { name: '匹配并创建房间', exact: true }).click()
  const pairedSecond = await waitState(second, room => room.phase === 'PLACEMENT')
  const pairedFirst = await waitState(first, room => room.phase === 'PLACEMENT')
  assert.equal(pairedFirst.id, waiting.id)
  assert.equal(pairedSecond.id, waiting.id)
  assert.equal(pairedFirst.inviteCode, pairedSecond.inviteCode)
  assert.equal(pairedFirst.you, 0)
  assert.equal(pairedSecond.you, 1)
  assert(pairedFirst.deadline - Date.now() > 270_000, 'deployment initially allows five minutes')
  checkPrivateView(pairedFirst)
  checkPrivateView(pairedSecond)
  const matchTimings = await assertMatching(second)
  await settled(first)
  checks.push('real two-context automatic matching', 'same invite code', 'opponent redaction')
  checks.push(`instant-match search ${matchTimings.searchingMs}ms and found ${matchTimings.foundMs}ms presentation`)

  assert.equal(await first.locator('.enemy-panel').count(), 0, 'layout is an own-board-only scene')
  assert.equal(await first.getByRole('button', { name: '购买战斗机', exact: true }).count(), 0, 'the shop cannot appear during placement')
  const earlyPurchase = await api(first, `/rooms/${waiting.id}/actions`, 'POST', { type: 'DEPLOY', ships: pairedFirst.players[pairedFirst.you].ships, inventory: { fighter: 1 } })
  assert.equal(earlyPurchase.status, 400, 'the backend must reject shopping during layout')

  await first.getByRole('button', { name: '清空舰队布局', exact: true }).click()
  await waitState(first, room => room.players[room.you].ships.length === 0)
  const cleared = await api(first, `/rooms/${waiting.id}`)
  assert.equal(cleared.payload.data.players[cleared.payload.data.you].ships.length, 0, 'clearing must persist an empty placement on the server')
  assert.equal(await first.locator('.fleet-roster .unplaced').count(), 10)
  assert(await first.getByRole('button', { name: '下一步：战备补给', exact: true }).isDisabled(), 'an empty fleet cannot advance')
  await first.reload()
  await first.getByRole('button', { name: '继续对局', exact: true }).click()
  await first.locator('.own-panel canvas').waitFor()
  await settled(first)
  const resumed = await waitState(first, room => room.id === waiting.id && room.players[room.you].ships.length === 0)
  assert.equal(resumed.players[resumed.you].stage, 'PLACEMENT')
  assert.equal(await first.locator('.fleet-roster .unplaced').count(), 10, 'reloaded empty placement must retain the complete unplaced roster')
  await first.locator('.fleet-roster .unplaced').first().click()
  await boardCell(first, 2, 2, true)
  const manual = await waitState(first, room => room.players[room.you].ships.length === 1)
  assert.equal(manual.players[manual.you].ships[0].x, 2)
  assert.equal(manual.players[manual.you].ships[0].y, 2)
  await first.getByTitle('随机阵型', { exact: true }).click()
  await waitState(first, room => room.players[room.you].ships.length === 10)
  checks.push('clear all placements persists to server', 'reload resumes an empty fleet with ten unplaced roster ships', 'manual roster placement and randomize back to complete fleet')

  await first.getByRole('button', { name: '战局聊天', exact: true }).click()
  await first.getByRole('textbox', { name: '战局消息' }).fill('自动匹配：准备交战')
  await first.getByRole('button', { name: '发送消息', exact: true }).click()
  await waitState(second, room => room.messages.some(message => message.text === '自动匹配：准备交战'))
  await second.getByRole('button', { name: '战局聊天', exact: true }).click()
  await second.locator('.chat-log').getByText('自动匹配：准备交战', { exact: true }).waitFor()
  await first.getByRole('button', { name: '收起聊天', exact: true }).click()
  await second.getByRole('button', { name: '收起聊天', exact: true }).click()
  await lockLayout(first)
  const firstSupply = await waitState(first, room => room.players[room.you].stage === 'SUPPLY')
  assert.equal(firstSupply.phase, 'PLACEMENT', 'one player may shop while the other is still arranging')
  const altered = generateFleet()
  const illegalMove = await api(first, `/rooms/${waiting.id}/actions`, 'POST', { type: 'DEPLOY', ships: altered, inventory: {} })
  assert.equal(illegalMove.status, 400, 'the backend must reject changing locked geometry during supply')
  assert.match(illegalMove.payload.message, /阵型已锁定/)
  await first.screenshot({ path: path.join(output, 'online-supply.png'), fullPage: true })
  await first.reload()
  await first.getByRole('heading', { name: '选择出航方式' }).waitFor()
  await watchMatching(first)
  await first.getByRole('button', { name: '匹配并创建房间', exact: true }).click()
  await first.locator('.supply-list').waitFor()
  await assertMatching(first)
  const restoredSupply = await snapshot(first)
  assert.equal(restoredSupply.id, waiting.id)
  assert.equal(restoredSupply.players[restoredSupply.you].stage, 'SUPPLY')
  checks.push('match-create restores an existing supply room with bounded search and found presentation')
  await ready(first)
  const firstReady = await waitState(first, room => room.players[room.you].ready)
  assert.equal(firstReady.phase, 'PLACEMENT', 'one ready player must wait for the second')
  await lockLayout(second)
  const sharedSupply = await waitState(second, room => room.phase === 'SUPPLY')
  assert(sharedSupply.deadline - Date.now() > 150_000, 'both layouts complete opens a fresh three-minute supply window')
  await ready(second)
  const fightingFirst = await waitState(first, room => room.phase === 'BATTLE')
  const fightingSecond = await waitState(second, room => room.phase === 'BATTLE')
  assert(fightingFirst.players.every(player => player.ready))
  assert(fightingFirst.deadline - Date.now() <= 30_000 && fightingFirst.deadline - Date.now() > 15_000)
  checkPrivateView(fightingFirst)
  checkPrivateView(fightingSecond)
  await first.reload()
  await first.getByRole('heading', { name: '选择出航方式' }).waitFor()
  await watchMatching(first)
  await first.getByRole('button', { name: '匹配并创建房间', exact: true }).click()
  await first.locator('.enemy-panel canvas').waitFor()
  await assertMatching(first)
  const restoredBattle = await snapshot(first)
  assert.equal(restoredBattle.id, waiting.id)
  assert.equal(restoredBattle.phase, 'BATTLE')
  checks.push('match-create restores an existing battle room with bounded search and found presentation')
  checks.push('battle chat delivery', 'own-board-only layout and supply', 'server enforces layout then supply', 'locked supply formation', 'both-ready early start', '30-second battle timer')

  const attackerPage = fightingFirst.turn === fightingFirst.you ? first : second
  const defenderPage = attackerPage === first ? second : first
  const before = attackerPage === first ? fightingFirst : fightingSecond
  const defender = defenderPage === first ? fightingFirst : fightingSecond
  const target = defender.players[defender.you].ships.find(ship => ship.w * ship.h > 1)
  await attackerPage.bringToFront()
  await boardCell(attackerPage, target.x, target.y)
  const hit = await waitState(attackerPage, room => room.players[1 - room.you].shots.some(shot => shot.x === target.x && shot.y === target.y && shot.result === 'hit'))
  assert.equal(hit.turn, hit.you, 'hitting a ship must grant an extra attack')
  assert(hit.deadline >= before.deadline, 'hitting a ship must reset the timer')
  assert.deepEqual(hit.players[1 - hit.you].ships, [], 'one hit must not reveal the unsunk ship')
  const defended = await waitState(defenderPage, room => room.players[room.you].shots.some(shot => shot.x === target.x && shot.y === target.y && shot.result === 'hit'))
  assert(defended.players[defended.you].ships.some(ship => ship.hits.includes(`${target.x},${target.y}`)))
  await attackerPage.screenshot({ path: path.join(output, 'online-automatic-battle.png'), fullPage: true })
  const defenderFleet = defender.players[defender.you]
  const water = Array.from({ length: 144 }, (_, i) => ({ x: i % 12, y: Math.floor(i / 12) })).find(cell =>
    !defenderFleet.ships.some(ship => cell.x >= ship.x && cell.x < ship.x + ship.w && cell.y >= ship.y && cell.y < ship.y + ship.h) &&
    !defenderFleet.mines.some(mine => mine.x === cell.x && mine.y === cell.y))
  await attackerPage.bringToFront()
  await boardCell(attackerPage, water.x, water.y)
  const missed = await waitState(attackerPage, room => room.turn !== room.you)
  assert(missed.players[1 - missed.you].shots.some(shot => shot.x === water.x && shot.y === water.y && shot.result === 'miss'))
  await waitState(defenderPage, room => room.turn === room.you)
  checks.push('UI hit and defender update', 'hit bonus with timer reset', 'miss turn handoff')

  const attackerFleet = before.players[before.you]
  const returnWater = Array.from({ length: 144 }, (_, i) => ({ x: i % 12, y: Math.floor(i / 12) })).find(cell =>
    !attackerFleet.ships.some(ship => cell.x >= ship.x && cell.x < ship.x + ship.w && cell.y >= ship.y && cell.y < ship.y + ship.h))
  const returnMiss = await api(defenderPage, `/rooms/${waiting.id}/actions`, 'POST', { type: 'ATTACK', weapon: 'shot', ...returnWater })
  assert.equal(returnMiss.status, 200)
  await waitState(attackerPage, room => room.turn === room.you)
  for (let y = target.y; y < target.y + target.h; y++) for (let x = target.x; x < target.x + target.w; x++) {
    if (x === target.x && y === target.y) continue
    await boardCell(attackerPage, x, y)
    await waitState(attackerPage, room => room.players[1 - room.you].shots.some(shot => shot.x === x && shot.y === y && shot.result === 'hit'))
  }
  const sunk = await waitState(attackerPage, room => room.players[1 - room.you].ships.some(ship => ship.id === target.id))
  assert.equal(sunk.players[1 - sunk.you].ships.length, 1, 'only the destroyed opponent ship is revealed')
  await attackerPage.waitForTimeout(1600)
  await attackerPage.screenshot({ path: path.join(output, 'online-ship-destroyed.png'), fullPage: true })
  checks.push('complete ship destruction and public wreck reveal')

  await leave(first)
  await first.getByText('战败', { exact: true }).waitFor()
  const finished = await waitState(second, room => room.phase === 'FINISHED')
  assert.equal(finished.winner, finished.you)
  await second.getByRole('heading', { name: '海域大捷', exact: true }).waitFor()
  for (const [page, won] of [[first, false], [second, true]]) {
    const history = await page.evaluate(() => JSON.parse(localStorage.getItem('naval-guest-history') || '[]'))
    assert(history.some(entry => entry.id === `${waiting.id}:1` && entry.won === won), 'guest round results must be saved locally')
    assert.equal(await page.evaluate(() => localStorage.getItem('naval-guest-room')), won ? waiting.id : null, 'remaining player keeps the room pointer for rematch')
  }
  checks.push('leave adjudicates winner', 'guest local history on both clients')

  await second.getByRole('button', { name: '返回港口', exact: true }).click()
  await first.getByRole('button', { name: '匹配并创建房间', exact: true }).click()
  const publicInvite = await waitState(first, room => room.phase === 'WAITING')
  await second.getByRole('textbox', { name: '海战棋邀请码' }).fill(publicInvite.inviteCode)
  await second.getByRole('button', { name: '加入房间', exact: true }).click()
  const publicJoined = await waitState(second, room => room.phase === 'PLACEMENT')
  assert.equal(publicJoined.id, publicInvite.id)
  assert.equal(publicJoined.autoMatch, true, 'invite codes must also work while automatic matching is enabled')
  await waitState(first, room => room.phase === 'PLACEMENT')
  await leave(first)
  await waitState(second, room => room.phase === 'FINISHED')
  checks.push('invite join remains available during automatic matching')

  const host = await guest()
  const invited = await guest()
  const outsider = await guest()
  await host.getByRole('switch').uncheck()
  await host.getByRole('button', { name: '创建邀请房间', exact: true }).click()
  const privateWaiting = await waitState(host, room => room.phase === 'WAITING')
  assert.equal(privateWaiting.autoMatch, false)
  await invited.getByRole('textbox', { name: '海战棋邀请码' }).fill(privateWaiting.inviteCode)
  await invited.getByRole('button', { name: '加入房间', exact: true }).click()
  const privateSecond = await waitState(invited, room => room.phase === 'PLACEMENT')
  assert.equal(privateSecond.id, privateWaiting.id)
  await waitState(host, room => room.phase === 'PLACEMENT')
  await outsider.getByRole('textbox', { name: '海战棋邀请码' }).fill(privateWaiting.inviteCode)
  await outsider.getByRole('button', { name: '加入房间', exact: true }).click()
  await outsider.getByRole('dialog', { name: '房间已满', exact: true }).waitFor()
  await outsider.getByRole('button', { name: '取消', exact: true }).click()
  assert.equal(await snapshot(outsider), null, 'third player must not enter a full room')
  const rejectedGet = await api(outsider, `/rooms/${privateWaiting.id}`)
  assert.equal(rejectedGet.status, 403)
  assert.equal(rejectedGet.payload.code, 403)
  assert.equal(rejectedGet.payload.data, null, 'outsider must receive no room state')
  const rejectedChat = await api(outsider, `/rooms/${privateWaiting.id}/actions`, 'POST', { type: 'CHAT', text: 'unauthorized' })
  assert.equal(rejectedChat.status, 403)
  const rejectedAttack = await api(outsider, `/rooms/${privateWaiting.id}/actions`, 'POST', { type: 'ATTACK', weapon: 'shot', x: 0, y: 0 })
  assert.equal(rejectedAttack.status, 403)
  const anonymous = await api(outsider, `/rooms/${privateWaiting.id}`, 'GET', undefined, false)
  assert.equal(anonymous.status, 401)
  const privateView = await api(invited, `/rooms/${privateWaiting.id}`)
  assert.equal(privateView.status, 200)
  assert.equal(privateView.payload.data.players.length, 2)
  checkPrivateView(privateView.payload.data)
  assert(!privateView.payload.data.messages.some(message => message.text === 'unauthorized'))
  const guestProfile = await api(outsider, '/profile')
  assert.equal(guestProfile.status, 401, 'guest profiles must remain browser-local')
  await host.screenshot({ path: path.join(output, 'online-private-placement.png'), fullPage: true })
  await leave(host)
  await waitState(invited, room => room.phase === 'FINISHED')
  checks.push('private invite UI join', 'two-player capacity enforcement', 'member-only reads and actions', 'anonymous access denied', 'guest database profile denied')
  assert.deepEqual(errors, [], 'browser runtime must remain error-free')
  console.log(JSON.stringify({ passed: true, output, checks }))
} catch (error) {
  for (let i = 0; i < contexts.length; i++) {
    for (const page of contexts[i].pages()) await page.screenshot({ path: path.join(output, `online-failure-${i}.png`), fullPage: true }).catch(() => {})
  }
  throw error
} finally {
  for (const context of contexts) for (const page of context.pages()) {
    const room = await snapshot(page).catch(() => null)
    if (room && room.phase !== 'FINISHED') await api(page, `/rooms/${room.id}/actions`, 'POST', { type: 'LEAVE' }).catch(() => {})
  }
  await browser.close()
}
