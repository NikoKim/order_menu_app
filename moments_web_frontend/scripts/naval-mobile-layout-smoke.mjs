import assert from 'node:assert/strict'
import { mkdir } from 'node:fs/promises'
import path from 'node:path'
import { chromium } from 'playwright-core'
import { createLocalGame, FLEET, legalPlacement, shipCells, validateFleet } from '../src/games/navalEngine.js'

const base = process.env.QA_BASE_URL || 'http://127.0.0.1:5174'
const output = process.env.QA_OUTPUT_DIR || path.resolve('../.agents/naval-qa')
const gamePath = '/customer/entertainment/naval'
const browser = await chromium.launch({ executablePath: process.env.QA_BROWSER || 'C:/Program Files/Google/Chrome/Application/chrome.exe', headless: true })
const errors = [], findings = { stages: [], viewports: [], tutorial: [] }, online = []
const heartbeat = setInterval(() => { for (const { page, id } of online) if (!page.isClosed()) api(page, `/rooms/${id}`).catch(() => {}) }, 5000)
await mkdir(output, { recursive: true })

function fleet() {
  const positions = [[11, 11], [11, 0], [0, 11], [5, 5], [0, 0], [3, 0], [6, 0], [0, 4], [3, 4], [7, 7]]
  const ships = FLEET.map((ship, index) => ({ ...ship, x: positions[index][0], y: positions[index][1], hits: [] }))
  assert.equal(validateFleet(ships), '')
  return ships
}
function fixture(phase = 'PLACEMENT') {
  const room = createLocalGame()
  room.players[1].ships = fleet(); room.players[1].mines = []; room.players[1].aa = []
  room.players[1].inventory = Object.fromEntries(Object.keys(room.players[1].inventory).map(key => [key, 0]))
  if (phase === 'BATTLE') {
    room.phase = phase; room.turn = 0
    room.players[0].ready = true; room.players[0].stage = 'READY'
    room.players[0].inventory.fighter = 1; room.players[0].inventory.radar = 1
  }
  return room
}
const local = page => page.evaluate(() => JSON.parse(localStorage.getItem('naval-guest-session') || 'null'))
const remote = page => page.evaluate(() => JSON.parse(localStorage.getItem('naval-guest-last-online') || 'null'))
async function until(page, read, predicate, timeout = 20_000) {
  const end = Date.now() + timeout
  let value
  while (Date.now() < end) { await page.bringToFront(); value = await read(page); if (value && predicate(value)) return value; await page.waitForTimeout(100) }
  throw new Error(`State timeout: ${JSON.stringify(value)}`)
}
const hook = (page, name) => page.locator(`[data-tutorial="${name}"]:visible`)
const panel = (page, name) => page.locator(`[data-mobile-panel="${name}"]:visible`)
async function settled(page) {
  await page.bringToFront()
  for (const selector of ['.launch-overlay', '.match-presentation', '.stage-scene-leave-active', '.phase-announcement']) await page.waitForFunction(selector => !document.querySelector(selector), selector)
  await page.waitForFunction(() => !document.querySelector('.naval-board.is-loading') && [...document.querySelectorAll('.naval-board canvas')].every(canvas => canvas.dataset.renderer === 'phaser-3.90'))
  await page.waitForFunction(() => { const room=JSON.parse(localStorage.getItem('naval-guest-session') || 'null'); return room?.phase !== 'BATTLE' || !room.presentationUntil })
  await page.waitForTimeout(450)
}
async function tapCell(page, side, point) {
  if (side === 'enemy') await page.waitForFunction(() => { const room=JSON.parse(localStorage.getItem('naval-guest-session') || 'null'); return room?.phase !== 'BATTLE' || (!room.presentationUntil && [...document.querySelectorAll('.naval-board canvas')].every(canvas => canvas.dataset.animating !== 'true')) })
  await page.locator('.mobile-panel-veil').waitFor({ state: 'hidden' })
  const box = await page.locator(`.${side}-panel canvas`).boundingBox()
  assert(box)
  await page.touchscreen.tap(box.x + (48 + point.x * 48) / 624 * box.width, box.y + (48 + point.y * 48) / 624 * box.height)
  const confirmation=page.getByRole('button',{name:'确认使用',exact:true})
  if (await confirmation.isVisible()) await confirmation.click()
}
async function closePanel(page, name) {
  const current = panel(page, name)
  await current.locator('button[aria-label^="收起"]').first().click()
  await current.waitFor({ state: 'hidden' })
  await page.locator('.mobile-panel-veil').waitFor({ state: 'hidden' })
  await page.locator('.chat-slide-leave-active, .mobile-sheet-leave-active').waitFor({ state: 'hidden' })
}
async function panelFits(page, name) {
  const current = panel(page, name)
  await page.waitForTimeout(400); await current.waitFor()
  const result = await current.evaluate(element => {
    const box = element.getBoundingClientRect()
    const containers = []
    for (let current = element; current && !current.classList.contains('naval-page'); current = current.parentElement) {
      if (current.getClientRects().length) containers.push({ tag: current.tagName, className: current.className, scrollHeight: current.scrollHeight, clientHeight: current.clientHeight })
    }
    return { box: box.toJSON(), width: innerWidth, height: innerHeight, scrollHeight: element.scrollHeight, clientHeight: element.clientHeight, containers,
      items: [...element.querySelectorAll('.prep-item, .prep-item-name, .prep-fleet>button, .prep-tools>button')].map(item => ({ className: item.className, height: item.clientHeight, scrollHeight: item.scrollHeight })),
      controls: [...element.querySelectorAll('button,input')].filter(control => control.getClientRects().length).map(control => control.getBoundingClientRect().toJSON()) }
  })
  assert(result.box.top >= -1 && result.box.left >= -1 && result.box.right <= result.width + 1 && result.box.bottom <= result.height + 1, `${name} panel must fit viewport: ${JSON.stringify(result)}`)
  assert(result.controls.every(box => box.top >= -1 && box.bottom <= result.height + 1 && box.left >= -1 && box.right <= result.width + 1), `${name} controls fit the viewport`)
  if (['fleet', 'supply', 'tools', 'weapons'].includes(name)) {
    assert(result.scrollHeight <= result.clientHeight + 1, `${name} panel requires no internal vertical scrolling (${result.scrollHeight}/${result.clientHeight})`)
    assert(result.containers.every(container => container.scrollHeight <= container.clientHeight + 1), `${name} panel parent containers must not scroll: ${JSON.stringify(result.containers)}`)
    assert(result.items.every(item => item.scrollHeight <= item.height + 1), `${name} preparation item labels and controls fit their rows: ${JSON.stringify(result.items)}`)
    const sidebar = page.locator('.battle-sidebar:visible')
    if (await sidebar.count()) assert(await sidebar.evaluate(element => element.scrollHeight <= element.clientHeight + 1), `${name}: actual .battle-sidebar has no vertical scroll`)
  }
  return result
}
async function layout(page, name, expectedBoards, preparation = false) {
  await settled(page)
  const result = await page.evaluate(() => {
    const root = document.querySelector('.naval-page'), doc = document.scrollingElement
    const room = JSON.parse(localStorage.getItem('naval-guest-last-online') || localStorage.getItem('naval-guest-session') || 'null')
    const you = room?.role === 'SPECTATOR' ? room.viewAs || 0 : room?.you || 0
    return { viewport: { width: innerWidth, height: innerHeight }, root: root.getBoundingClientRect().toJSON(), rootHeight: root.clientHeight, rootScrollHeight: root.scrollHeight,
      docHeight: doc.clientHeight, docScrollHeight: doc.scrollHeight, docWidth: doc.clientWidth, docScrollWidth: doc.scrollWidth,
      boards: [...document.querySelectorAll('.boards .naval-board')].map(element => element.getBoundingClientRect().toJSON()),
      header: document.querySelector('.naval-header')?.getBoundingClientRect().toJSON(),
      boardHeaders: [...document.querySelectorAll('.board-panel>header:not(.mobile-player-status)')].map(element => element.getBoundingClientRect().toJSON()),
      operations: document.querySelector('.mobile-prep-operations')?.getBoundingClientRect().toJSON(),
      operationsControls: [...(document.querySelector('.mobile-prep-operations')?.querySelectorAll('button,input') || [])].filter(element => element.getClientRects().length).map(element => element.getBoundingClientRect().toJSON()),
      participantNames: [room?.players?.[you]?.name, room?.players?.[1 - you]?.name],
      statuses: [...document.querySelectorAll('.mobile-player-status')].filter(element => element.getClientRects().length).map(element => ({ side: element.dataset.side || (element.classList.contains('is-left') ? 'left' : 'right'), text: element.textContent, box: element.getBoundingClientRect().toJSON(), clocks: [...element.querySelectorAll('.rail-clock')].filter(clock => clock.getClientRects().length).map(clock => ({ text: clock.textContent, box: clock.getBoundingClientRect().toJSON() })), controls: [...element.querySelectorAll('button')].filter(button => button.getClientRects().length).map(button => button.getBoundingClientRect().toJSON()) })),
      colors: [...document.querySelectorAll('.boards canvas')].map(canvas => {
        const data = canvas.getContext('2d').getImageData(0, 0, canvas.width, canvas.height).data, colors = new Set()
        for (let index = 0; index < data.length; index += 16) colors.add(`${data[index] >> 4},${data[index + 1] >> 4},${data[index + 2] >> 4}`)
        return colors.size
      }) }
  })
  assert(await page.evaluate(() => Boolean(document.fullscreenElement)), `${name} actually runs fullscreen`)
  assert(result.root.top >= -1 && result.root.bottom <= result.viewport.height + 1, `${name}: root fits screen`)
  assert(result.rootScrollHeight <= result.rootHeight + 1, `${name}: game has no vertical scroll (${result.rootScrollHeight}/${result.rootHeight})`)
  assert(result.docScrollHeight <= result.docHeight + 1, `${name}: document has no vertical scroll (${result.docScrollHeight}/${result.docHeight})`)
  assert(result.docScrollWidth <= result.docWidth + 1, `${name}: document has no horizontal scroll`)
  if (expectedBoards) assert(!result.header || result.header.height === 0, `${name}: mobile reserves no top title/status header`)
  else assert(!result.header || result.header.height <= 44, `${name}: waiting room reserves only a compact operations toolbar`)
  assert(result.boardHeaders.every(box => box.height === 0), `${name}: obsolete desktop board headers stay hidden`)
  assert.equal(result.boards.length, expectedBoards)
  const minimum = preparation ? result.viewport.height - 24 : Math.min((result.viewport.width - 16) / 2, result.viewport.height - 34) - 4
  assert(result.boards.every(box => box.width >= minimum && box.height >= minimum), `${name}: boards should use the available viewport, min=${minimum}: ${JSON.stringify(result.boards)}`)
  assert(result.boards.every(box => box.left >= 0 && box.right <= result.viewport.width + 1 && box.top >= 0 && box.bottom <= result.viewport.height + 1), `${name}: boards fully visible`)
  if (expectedBoards === 2) assert(result.boards[0].right <= result.boards[1].left, `${name}: boards do not overlap`)
  if (preparation && expectedBoards) {
    assert(result.operations, `${name}: preparation operations are permanently on the right`)
    assert(result.boards[0].right <= result.operations.left + 1, `${name}: own 144-cell sea is left of, not underneath, its controls`)
    assert(result.operations.right >= result.viewport.width - 16 && result.operations.width >= 210, `${name}: operations fill the remaining right side`)
    assert(result.boards[0].top <= 16 && result.boards[0].bottom >= result.viewport.height - 16, `${name}: own board uses full viewport height`)
    assert(result.operationsControls.every(box => box.top >= -1 && box.bottom <= result.viewport.height + 1 && box.left >= result.operations.left - 1 && box.right <= result.viewport.width + 1), `${name}: every persistent preparation control fits its right-hand pane`)
  }
  if (expectedBoards === 2) {
    const left = result.statuses.find(status => status.side === 'left'), right = result.statuses.find(status => status.side === 'right')
    assert(left && right, `${name}: each participant has its own compact information bar`)
    for (const [index, status] of [left, right].entries()) {
      const board = result.boards[index]
      assert(status.text.includes(result.participantNames[index]), `${name}: each information bar identifies the player who owns that board`)
      assert(status.box.left >= board.left - 1 && status.box.right <= board.right + 1 && status.box.bottom <= board.top + 1, `${name}: player information is above its own sea, not in an outer rail or global header`)
      assert(status.box.top >= -1 && status.box.height <= 28, `${name}: per-board status stays compact`)
      assert(status.controls.every(box => box.left >= status.box.left - 1 && box.right <= status.box.right + 1 && box.top >= status.box.top - 1 && box.bottom <= status.box.bottom + 1), `${name}: information-bar controls fit without overlapping the board`)
    }
    if (!name.includes('finished')) assert(result.statuses.some(status => status.clocks.some(clock => /\d+:\d{2}/.test(clock.text))), `${name}: live countdown is visible above the active player's board`)
  }
  assert(result.colors.every(count => count >= 5), `${name}: real Phaser sea pixels are rendered`)
  if (expectedBoards) assert(result.colors[0] > 20, `${name}: detailed own bitmap ships render`)
  const scrollBefore = await page.evaluate(() => scrollY)
  await page.mouse.wheel(0, 500)
  await page.waitForTimeout(80)
  assert.equal(await page.evaluate(() => scrollY), scrollBefore, `${name}: wheel cannot move the game`)
  await page.screenshot({ path: path.join(output, `${name}.png`) })
  findings.stages.push({ name, ...result })
  return result
}
async function newPhone(viewport, room) {
  const context = await browser.newContext({ viewport, isMobile: true, hasTouch: true, deviceScaleFactor: 1 })
  const page = await context.newPage()
  page.on('pageerror', error => errors.push(error.message)); page.setDefaultTimeout(20_000)
  if (room) await page.addInitScript(room => { room.deadline = Date.now() + 180_000; localStorage.setItem('naval-guest-session', JSON.stringify(room)); localStorage.setItem('naval-guest-resume-kind', 'LOCAL') }, room)
  await page.goto(base + gamePath)
  if (room) {
    await page.getByRole('button', { name: '继续对局', exact: true }).click()
    const fullscreen = page.getByRole('button', { name: '全屏并锁定横屏', exact: true })
    if (await fullscreen.isVisible()) await fullscreen.click()
    await page.locator('.naval-page.mobile-game').waitFor()
    await page.waitForFunction(() => Boolean(document.fullscreenElement))
    await settled(page)
  }
  return { context, page }
}
async function chooseWeapon(page, weapon) {
  await hook(page, 'naval-mobile-weapons-toggle').click()
  await panelFits(page, 'weapons')
  await hook(page, `naval-weapon-${weapon}`).click()
  await panel(page, 'weapons').waitFor({ state: 'hidden' })
}
async function corners(page) {
  await hook(page, 'naval-mobile-weapons-toggle').click()
  await panelFits(page, 'weapons')
  await hook(page, 'naval-mobile-weapons-toggle').click({ timeout: 3500 })
  await panel(page, 'weapons').waitFor({ state: 'hidden' })
  await page.locator('.mobile-panel-veil').waitFor({ state: 'hidden' })
  await chooseWeapon(page, 'fighter'); await chooseWeapon(page, 'shot')
  for (const target of [{ x: 11, y: 11 }, { x: 11, y: 0 }, { x: 0, y: 11 }]) {
    await tapCell(page, 'enemy', target)
    await until(page, local, room => room.players[1].shots.some(shot => shot.x === target.x && shot.y === target.y && shot.result === 'hit'))
  }
}
async function api(page, url, data) {
  return page.evaluate(async ({ url, data }) => {
    const response = await fetch(`/api/entertainment/naval${url}`, { method: data === undefined ? 'GET' : 'POST', headers: { 'Content-Type': 'application/json', 'X-Guest-Token': localStorage.getItem('entertainment_guest_token') }, ...(data === undefined ? {} : { body: JSON.stringify(data) }) })
    const payload = await response.json()
    if (!response.ok || payload.code !== 0) throw new Error(`${response.status}: ${payload.message}`)
    return payload.data
  }, { url, data })
}

try {
  if (process.env.QA_ONLINE_ONLY !== '1') {
  const { context, page } = await newPhone({ width: 844, height: 390 }, fixture())
  await layout(page, 'mobile-v7-placement', 1, true)
  await panelFits(page, 'fleet')
  await hook(page, 'naval-mobile-fleet-toggle').click(); await panelFits(page, 'fleet')
  const before = await local(page), ship = before.players[0].ships.find(ship => ship.w * ship.h === 1)
  await panel(page, 'fleet').locator('button').first().click()
  assert(await panel(page, 'fleet').isVisible(), 'Selecting a ship keeps the right-hand fleet controls available')
  const move = Array.from({ length: 144 }, (_, index) => ({ x: index % 12, y: Math.floor(index / 12) })).find(point => (point.x !== ship.x || point.y !== ship.y) && legalPlacement(before.players[0].ships, { ...ship, ...point }))
  assert(move); await tapCell(page, 'own', move)
  await until(page, local, room => room.players[0].ships.some(value => value.id === ship.id && value.x === move.x && value.y === move.y))
  await hook(page, 'naval-mobile-tools-toggle').click(); await panelFits(page, 'tools')
  await hook(page, 'naval-clear-layout').click(); assert.equal((await local(page)).players[0].ships.length, 0)
  assert(await panel(page, 'tools').isVisible(), 'Tools remain in the persistent operations pane')
  await hook(page, 'naval-random-layout').click(); assert.equal((await local(page)).players[0].ships.length, 10)
  await hook(page, 'naval-layout-next').click()
  await until(page, local, room => room.phase === 'SUPPLY')
  await layout(page, 'mobile-v7-supply', 1, true)
  await panelFits(page, 'supply')
  await page.getByRole('button', { name: '购买战斗机', exact: true }).click()
  await page.getByRole('button', { name: '购买雷达', exact: true }).click()
  await page.getByRole('button', { name: '购买水雷', exact: true }).click()
  assert(await panel(page, 'supply').isVisible(), 'Defense purchases keep the shop on the right without covering the sea')
  const supplied = await local(page)
  const water = Array.from({ length: 144 }, (_, index) => ({ x: index % 12, y: Math.floor(index / 12) })).find(point => !supplied.players[0].ships.flatMap(shipCells).some(cell => cell.x === point.x && cell.y === point.y))
  await tapCell(page, 'own', water)
  await page.getByRole('button', { name: '购买防空炮', exact: true }).click()
  assert(await panel(page, 'supply').isVisible()); await tapCell(page, 'own', { x: 0, y: 11 })
  assert.equal((await local(page)).players[0].coins, 195)
  await hook(page, 'naval-supply-ready').click(); await until(page, local, room => room.phase === 'BATTLE')
  await layout(page, 'mobile-v7-battle-844', 2)
  assert.equal(await page.locator('.weapons:visible').count(), 0, 'Attack methods are collapsed by default')
  await corners(page)
  for (const target of fleet().flatMap(shipCells)) {
    if ((await local(page)).players[1].shots.some(shot => shot.x === target.x && shot.y === target.y)) continue
    await tapCell(page, 'enemy', target)
  }
  await page.getByRole('dialog', { name: '战局结算', exact: true }).waitFor()
  await page.waitForTimeout(450)
  const footer = await page.locator('.settlement-inner>footer button').evaluateAll(buttons => buttons.map(button => button.getBoundingClientRect().toJSON()))
  assert(footer.length > 0 && footer.every(box => box.top >= 0 && box.bottom <= 391), `Mobile settlement primary actions fit the same screen: ${JSON.stringify(footer)}`)
  await page.screenshot({ path: path.join(output, 'mobile-v7-settlement.png') })
  await context.close()

  for (const viewport of [{ width: 667, height: 375 }, { width: 568, height: 320 }]) {
    const { context, page } = await newPhone(viewport, fixture())
    await layout(page, `mobile-v7-placement-${viewport.width}`, 1, true)
    await panelFits(page, 'fleet')
    await hook(page, 'naval-mobile-fleet-toggle').click(); await panelFits(page, 'fleet')
    await hook(page, 'naval-mobile-tools-toggle').click(); await panelFits(page, 'tools')
    await hook(page, 'naval-layout-next').click(); await until(page, local, room => room.phase === 'SUPPLY')
    await layout(page, `mobile-v7-supply-${viewport.width}`, 1, true)
    await panelFits(page, 'supply')
    await page.screenshot({ path: path.join(output, `mobile-v7-shop-${viewport.width}.png`) })
    await page.getByRole('button', { name: '购买战斗机', exact: true }).click()
    await page.getByRole('button', { name: '购买雷达', exact: true }).click()
    await hook(page, 'naval-supply-ready').click()
    await until(page, local, room => room.phase === 'BATTLE')
    const result = await layout(page, `mobile-v7-battle-${viewport.width}`, 2)
    await corners(page); findings.viewports.push({ ...viewport, boards: result.boards })
    assert.equal(await page.getByRole('button', { name: '战局聊天', exact: true }).count(),0,'AI has no match chat')
    await page.getByRole('button',{name:'对局菜单',exact:true}).click()
    assert.equal(await page.getByRole('button',{name:'聊天大厅',exact:true}).count(),0,'active game menu has no lobby chat')
    assert.equal(await page.locator('.mobile-menu-actions .community-actions').count(),0,'community shortcuts do not nest inside match menu')
    await page.locator('.mobile-utility-panel').getByRole('button',{name:'收起面板',exact:true}).click()
    await context.close()
  }

  const folded = await newPhone({ width: 700, height: 500 }, fixture('BATTLE'))
  const foldedLayout = await layout(folded.page, 'mobile-v7-battle-folded-700', 2)
  await corners(folded.page)
  findings.viewports.push({ width: 700, height: 500, boards: foldedLayout.boards, centeredHeaderToggle: true })
  await folded.context.close()
  }

  const hostPhone = await newPhone({ width: 844, height: 390 }), host = hostPhone.page, peer = await browser.newPage()
  await host.getByRole('switch').uncheck()
  await host.getByRole('button', { name: '创建邀请房间', exact: true }).click()
  const hostGate = host.getByRole('button', { name: '全屏并锁定横屏', exact: true }); if (await hostGate.isVisible()) await hostGate.click()
  const waiting = await until(host, remote, room => room.phase === 'WAITING'); online.push({ page: host, id: waiting.id })
  await layout(host, 'mobile-v7-waiting', 0)
  await peer.goto(base + gamePath); await peer.waitForFunction(() => Boolean(localStorage.getItem('entertainment_guest_token')))
  const joined = await api(peer, '/rooms/join', { inviteCode: waiting.inviteCode }); online.push({ page: peer, id: waiting.id })
  const hostJoined = await api(host, `/rooms/${waiting.id}`)
  await api(host, `/rooms/${waiting.id}/actions`, { type: 'LAYOUT_READY', ships: hostJoined.players[hostJoined.you].ships })
  await until(host, remote, room => room.players[room.you].stage === 'SUPPLY'); await settled(host)
  await panelFits(host, 'supply')
  await api(host, `/rooms/${waiting.id}/actions`, { type: 'DEPLOY', ships: hostJoined.players[hostJoined.you].ships, inventory: {}, mines: [], aa: [], ready: true })
  await until(host, remote, room => room.players[room.you].stage === 'READY')
  await host.locator('.mobile-panel-veil').waitFor({ state: 'hidden' })
  await host.waitForFunction(() => [...document.querySelectorAll('[data-mobile-panel]')].every(element => getComputedStyle(element).display === 'none' || !element.getClientRects().length))
  assert.equal(await host.locator('[data-mobile-panel]:visible').count(), 0, 'A server-driven READY transition closes the supply panel and its veil')
  await layout(host, 'mobile-v7-ready', 1, true)
  await api(peer, `/rooms/${waiting.id}/actions`, { type: 'LAYOUT_READY', ships: joined.players[joined.you].ships })
  await api(peer, `/rooms/${waiting.id}/actions`, { type: 'DEPLOY', ships: joined.players[joined.you].ships, inventory: {}, mines: [], aa: [], ready: true })
  const observer = await newPhone({ width: 844, height: 390 })
  await observer.page.getByRole('textbox', { name: '海战棋邀请码', exact: true }).fill(waiting.inviteCode)
  await observer.page.getByRole('button', { name: '加入房间', exact: true }).click()
  const gate = observer.page.getByRole('button', { name: '全屏并锁定横屏', exact: true }); if (await gate.isVisible()) await gate.click()
  await until(observer.page, remote, room => room.role === 'SPECTATOR')
  for (const viewport of [{ width: 844, height: 390 }, { width: 667, height: 375 }, { width: 568, height: 320 }]) {
    await observer.page.setViewportSize(viewport)
    await layout(observer.page, `mobile-v7-spectator-${viewport.width}`, 2)
    await observer.page.getByRole('button', { name: '展开观众席', exact: true }).click(); await panelFits(observer.page, 'audience')
    assert(await panel(observer.page, 'audience').locator('.audience-person').count() >= 1)
    await closePanel(observer.page, 'audience')
  }
  assert.equal(await hook(observer.page, 'naval-mobile-weapons-toggle').count(), 0, 'Spectator has no attack menu')
  await observer.context.close()
  await until(host, remote, room => room.phase === 'BATTLE'); await settled(host)
  await hook(host, 'naval-mobile-weapons-toggle').click(); await panelFits(host, 'weapons')
  await api(peer, `/rooms/${waiting.id}/actions`, { type: 'LEAVE' })
  const finished = await until(host, remote, room => room.phase === 'FINISHED')
  await host.getByRole('dialog', { name: '战局结算', exact: true }).waitFor()
  await host.locator('.settlement-inner>footer').getByRole('button', { name: '查看棋盘', exact: true }).click()
  await host.getByRole('dialog', { name: '战局结算', exact: true }).waitFor({ state: 'hidden' })
  await host.locator('.mobile-panel-veil').waitFor({ state: 'hidden' })
  assert.equal(await host.locator('[data-mobile-panel]:visible').count(), 0, 'Opponent departure removes the open weapons panel, not just its visible content')
  assert.equal(await hook(host, 'naval-mobile-weapons-toggle').count(), 0, 'Finished games have no active attack dock')
  await layout(host, 'mobile-v7-finished-no-orphan-veil', 2)
  await tapCell(host, 'enemy', { x: 11, y: 11 }); await host.waitForTimeout(400)
  const afterFinishedTap = await remote(host)
  assert.equal(afterFinishedTap.events.length, finished.events.length, 'Read-only finished boards cannot dispatch attacks')
  findings.serverPanelTransitions = { readyClosesSupply: true, departureClosesWeapons: true, noOrphanVeil: true, finishedAttackBlocked: true }
  assert.deepEqual(errors, [], 'Mobile runtime stays error-free')
  console.log(JSON.stringify({ passed: true, findings, output }))
} catch (error) {
  for (const [index, context] of browser.contexts().entries()) for (const page of context.pages()) await page.screenshot({ path: path.join(output, `mobile-v7-failure-${index}.png`) }).catch(() => {})
  throw error
} finally {
  clearInterval(heartbeat)
  for (const { page, id } of online) if (!page.isClosed()) await api(page, `/rooms/${id}/actions`, { type: 'LEAVE' }).catch(() => {})
  await browser.close()
}
