import assert from 'node:assert/strict'
import { mkdir } from 'node:fs/promises'
import path from 'node:path'
import { chromium } from 'playwright-core'
import { createLocalGame } from '../src/games/navalEngine.js'

const base = process.env.QA_BASE_URL || 'http://127.0.0.1:5174'
const route = '/customer/entertainment/naval'
const output = path.resolve('../.agents/naval-qa')
await mkdir(output, { recursive: true })
const browser = await chromium.launch({ executablePath: 'C:/Program Files/Google/Chrome/Application/chrome.exe', headless: true })
const errors = []
async function api(url, token, data) {
  const response = await fetch(`${base}/api/entertainment${url}`, { method: data === undefined ? 'GET' : 'POST', headers: { 'Content-Type': 'application/json', ...(token ? { 'X-Guest-Token': token } : {}) }, ...(data === undefined ? {} : { body: JSON.stringify(data) }) })
  const payload = await response.json()
  assert(response.ok && payload.code === 0, JSON.stringify(payload))
  return payload.data
}
async function ready(page) {
  await page.waitForFunction(() => document.querySelector('canvas[data-renderer]') && !document.querySelector('.launch-overlay,.match-presentation,.phase-announcement'), null, { timeout: 30000 })
}
try {
  const local = await browser.newContext({ viewport: { width: 1280, height: 800 } })
  const page = await local.newPage(); page.on('pageerror', e => errors.push(e.message))
  await page.goto(base + route)
  await page.getByRole('button', { name: '开始对战', exact: true }).click(); await ready(page)
  const id = await page.evaluate(() => JSON.parse(localStorage.getItem('naval-guest-session')).id)
  await page.reload()
  await page.getByRole('button', { name: '开始对战', exact: true }).click()
  await page.getByRole('dialog', { name: '已有未结束的对局' }).waitFor()
  assert.equal(await page.locator('.in-game').count(), 0)
  await page.getByRole('button', { name: '继续旧对局', exact: true }).click(); await ready(page)
  assert.equal(await page.evaluate(() => JSON.parse(localStorage.getItem('naval-guest-session')).id), id)
  await page.reload()
  await page.getByRole('button', { name: '退出之前的对局', exact: true }).click()
  await page.getByRole('dialog', { name: '已有未结束的对局' }).waitFor()
  await page.getByRole('button', { name: '退出对局', exact: true }).click()
  await page.waitForFunction(() => !document.querySelector('.resume-dialog,.resume-line'))
  assert.deepEqual(await page.evaluate(() => JSON.parse(localStorage.getItem('naval-guest-history') || '[]')), [])
  await page.getByRole('button', { name: '开始对战', exact: true }).click(); await ready(page)
  await page.reload(); await page.getByRole('button', { name: '开始对战', exact: true }).click()
  await page.getByRole('button', { name: '退出并开始新局', exact: true }).click(); await ready(page)
  assert.deepEqual(await page.evaluate(() => JSON.parse(localStorage.getItem('naval-guest-history') || '[]')), [])
  await local.close()

  for (const viewport of [{ width: 568, height: 320 }, { width: 844, height: 390 }]) {
    const context = await browser.newContext({ viewport, isMobile: true, hasTouch: true, userAgent: 'Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X) AppleWebKit/605.1.15 Version/18.0 Mobile Safari/604.1' })
    const p = await context.newPage(); await p.goto(base + route)
    await p.locator('.lobby-modes').waitFor()
    const bottoms = await p.locator('.lobby-modes>.mode-entry>button').evaluateAll(buttons => buttons.map(button => button.getBoundingClientRect().bottom))
    assert.equal(bottoms.length, 3); assert(Math.max(...bottoms) - Math.min(...bottoms) < 2, JSON.stringify(bottoms))
    await p.screenshot({ path: path.join(output, `naval-v10-buttons-${viewport.width}.png`) })
    await p.evaluate(game => { localStorage.setItem('naval-guest-session', JSON.stringify(game)); localStorage.setItem('naval-guest-resume-kind', 'LOCAL') }, createLocalGame())
    await p.reload(); await p.getByRole('button', { name: '退出之前的对局', exact: true }).click()
    await p.waitForTimeout(320)
    const dialog = await p.locator('.resume-dialog').boundingBox()
    assert(dialog.x >= 0 && dialog.y >= 0 && dialog.x + dialog.width <= viewport.width + 1 && dialog.y + dialog.height <= viewport.height + 1)
    await p.screenshot({ path: path.join(output, `naval-v10-resume-${viewport.width}.png`) })
    await context.close()
  }

  const host = (await api('/guests', null, {})).token, peer = (await api('/guests', null, {})).token
  const waiting = await api('/naval/rooms', host, { mode: 'MULTIPLAYER', autoMatch: false })
  const online = await browser.newContext({ viewport: { width: 1280, height: 800 } })
  const p = await online.newPage(); p.on('pageerror', e => errors.push(e.message))
  await p.goto(base + '/customer/entertainment')
  await p.evaluate(({ host, id }) => { localStorage.setItem('entertainment_guest_token', host); localStorage.setItem('naval-guest-room', id); localStorage.setItem('naval-guest-resume-kind', 'ONLINE') }, { host, id: waiting.id })
  await p.goto(base + route); await p.getByRole('button', { name: '继续对局', exact: true }).click()
  await p.waitForFunction(() => document.querySelector('.waiting-toolbar') && !document.querySelector('.launch-overlay'))
  await p.goto(base + '/customer/entertainment')
  await p.waitForTimeout(500)
  const closed = await api(`/naval/rooms/${waiting.id}`, host)
  assert.equal(closed.phase, 'FINISHED'); assert.equal(closed.winner, null); assert.equal(closed.finishReason, 'CANCELLED')

  const room = await api('/naval/rooms', host, { mode: 'MULTIPLAYER', autoMatch: false })
  await api('/naval/rooms/join', peer, { inviteCode: room.inviteCode })
  await p.evaluate(id => { localStorage.setItem('naval-guest-room', id); localStorage.setItem('naval-guest-resume-kind', 'ONLINE') }, room.id)
  await p.goto(base + route); await p.getByRole('button', { name: '继续对局', exact: true }).click(); await ready(p)
  await p.goto(base + '/customer/entertainment'); await p.waitForTimeout(700)
  assert.equal((await api(`/naval/rooms/${room.id}`, peer)).phase, 'PLACEMENT')
  await p.goto(base + route); await p.getByRole('button', { name: '继续对局', exact: true }).click(); await ready(p)
  assert.equal(await p.evaluate(() => localStorage.getItem('naval-guest-room')), room.id)
  await p.goto(base + '/customer/entertainment')
  const until = Date.now() + 31_500
  let outcome
  while (Date.now() < until) { await new Promise(resolve => setTimeout(resolve, 1000)); outcome = await api(`/naval/rooms/${room.id}`, peer) }
  assert.equal(outcome.phase, 'FINISHED'); assert.equal(outcome.winner, 1); assert.equal(outcome.finishReason, 'DISCONNECTED')
  await online.close()

  const render = await browser.newContext({ viewport: { width: 900, height: 720 } })
  const canvasPage = await render.newPage(); canvasPage.on('pageerror', e => errors.push(e.message))
  await canvasPage.goto(base + '/customer/entertainment')
  await canvasPage.evaluate(async () => {
    const { mountNavalGame } = await import('/src/games/navalScene.js')
    const host = document.createElement('div'); host.style.cssText = 'position:fixed;inset:0;width:624px;height:624px;z-index:9999'
    document.body.append(host)
    window.__naval = mountNavalGame(host, { player: { ships: [], shots: [], mines: [], aa: [] }, defenseSide: 'left' }, {})
  })
  await canvasPage.waitForFunction(() => window.__naval.game.canvas.dataset.renderer)
  for (const side of ['left', 'right']) {
    await canvasPage.evaluate(side => { const scene = window.__naval.game.scene.getScene('naval-board'); scene.snapshot.defenseSide = side; scene.torpedoWake(side === 'left' ? 0 : 11, 4) }, side)
    await canvasPage.waitForTimeout(100)
    const first = await canvasPage.evaluate(() => { const s = window.__naval.game.scene.getScene('naval-board'); const t = s.effects.list.find(item => item.active && item.getData('effect') === 'torpedo-projectile'); return { x: t.x, rotation: t.rotation, direction: s.game.canvas.dataset.torpedoDirection } })
    await canvasPage.waitForTimeout(200)
    const second = await canvasPage.evaluate(() => window.__naval.game.scene.getScene('naval-board').effects.list.find(item => item.active && item.getData('effect') === 'torpedo-projectile').x)
    assert.equal(first.direction, side === 'left' ? 'right-to-left' : 'left-to-right')
    assert(side === 'left' ? second < first.x : second > first.x)
    await canvasPage.screenshot({ path: path.join(output, `naval-v10-torpedo-${side}.png`) })
    await canvasPage.waitForTimeout(800)
  }
  await render.close(); assert.deepEqual(errors, [])
  console.log('PASS: old-game choices, preparation not counted, bottom-aligned buttons, waiting-room cancellation, reconnect within 30s, disconnect defeat after 30s, mirrored moving torpedo assets')
} finally { await browser.close() }
