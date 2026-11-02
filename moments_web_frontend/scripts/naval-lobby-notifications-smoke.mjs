import assert from 'node:assert/strict'
import { chromium } from 'playwright-core'
import path from 'node:path'
import { mkdir } from 'node:fs/promises'
import { createLocalGame, localBattleResult } from '../src/games/navalEngine.js'

const base = process.env.QA_BASE_URL || 'http://127.0.0.1:5174'
const output = path.resolve('../.agents/naval-qa'); await mkdir(output, { recursive: true })
const browser = await chromium.launch({ executablePath: 'C:/Program Files/Google/Chrome/Application/chrome.exe', headless: true })
const errors = []
try {
  for (const viewport of [{ width: 1280, height: 800 }, { width: 320, height: 740 }]) {
    const context = await browser.newContext({ viewport, isMobile: viewport.width < 700, hasTouch: viewport.width < 700 })
    const messages = [{ id: 1, name: '其他指挥官', text: '以前的消息', at: Date.now(), mine: false }]
    let requests = 0
    await context.route('**/api/entertainment/naval/lobby/messages', route => {
      requests++
      return route.fulfill({ json: { code: 0, message: 'ok', data: messages } })
    })
    await context.addInitScript(() => {
      localStorage.setItem('naval_sound_muted', '0')
      window.__sounds = []
      const start = AudioBufferSourceNode.prototype.start
      AudioBufferSourceNode.prototype.start = function (...args) { window.__sounds.push(this.buffer?.duration); return start.apply(this, args) }
    })
    const detail = localBattleResult(createLocalGame()); detail.battleStarted = true
    await context.addInitScript(detail => localStorage.setItem('naval-guest-history', JSON.stringify([{ id: detail.gameId, gameId: detail.gameId, detail, local: true, mode: 'AI', won: true, at: Date.now() }])), detail)
    const page = await context.newPage(); page.on('pageerror', e => errors.push(e.message))
    await page.goto(base + '/customer/entertainment/naval')
    await page.waitForFunction(() => /\d+ 人在线/.test(document.querySelector('.online-count')?.textContent || ''))
    await page.waitForTimeout(300)
    const lobby = page.getByRole('button', { name: '聊天大厅', exact: true })
    assert.equal(await lobby.locator('.lobby-unread-dot').count(), 0, 'old history is not unread')
    await page.getByRole('button', { name: '排行榜', exact: true }).click()
    messages.push({ id: 2, name: '其他指挥官', text: '新消息', at: Date.now(), mine: false })
    await lobby.locator('.lobby-unread-dot').waitFor()
    await page.waitForFunction(() => window.__sounds.some(duration => Math.abs(duration - .65) < .01))
    assert.equal(await page.evaluate(() => window.__sounds.filter(d => Math.abs(d - .65) < .01).length), 1)
    const buttonBox = await lobby.boundingBox(), dotBox = await lobby.locator('.lobby-unread-dot').boundingBox()
    assert(dotBox.x >= buttonBox.x && dotBox.x + dotBox.width <= buttonBox.x + buttonBox.width)
    assert(buttonBox.x + buttonBox.width <= viewport.width)
    await page.screenshot({ path: path.join(output, `naval-lobby-unread-${viewport.width}.png`) })
    await page.waitForTimeout(2800)
    assert.equal(await page.evaluate(() => window.__sounds.filter(d => Math.abs(d - .65) < .01).length), 1, 'repeated polls are silent')
    await lobby.click()
    await page.locator('.lobby-chat-log').getByText('新消息', { exact: true }).waitFor()
    assert.equal(await lobby.locator('.lobby-unread-dot').count(), 0, 'opening lobby marks read')
    messages.push({ id: 3, name: '其他指挥官', text: '正在查看时的消息', at: Date.now(), mine: false })
    await page.locator('.lobby-chat-log').getByText('正在查看时的消息', { exact: true }).waitFor()
    assert.equal(await page.evaluate(() => window.__sounds.filter(d => Math.abs(d - .65) < .01).length), 1)
    await page.getByRole('button', { name: '关闭社区面板', exact: true }).click()
    messages.push({ id: 4, name: '自己', text: '同账号其他页面发出的消息', at: Date.now(), mine: true })
    await page.waitForTimeout(3000)
    assert.equal(await lobby.locator('.lobby-unread-dot').count(), 0, 'own messages never trigger unread')
    assert.equal(await page.evaluate(() => window.__sounds.filter(d => Math.abs(d - .65) < .01).length), 1, 'own messages stay silent')
    await page.getByRole('button', { name: '关闭音效', exact: true }).click()
    messages.push({ id: 5, name: '其他指挥官', stickerId: 'salute', text: '', at: Date.now(), mine: false })
    await lobby.locator('.lobby-unread-dot').waitFor()
    assert.equal(await page.evaluate(() => window.__sounds.filter(d => Math.abs(d - .65) < .01).length), 1, 'mute keeps the dot but suppresses sound')
    assert(requests >= 5)
    const logic = await page.evaluate(async () => {
      const { createNavalLobbyUnread } = await import('/src/composables/useNavalLobbyNotifications.js')
      const state = createNavalLobbyUnread(), result = []
      result.push(state.receive([{ id: '9007199254740993', mine: false }]))
      result.push(state.receive([{ id: '9007199254740994', mine: false }, { id: '9007199254740995', mine: false }]))
      result.push(state.receive([{ id: '9007199254740993', mine: false }]))
      result.push(state.receive([{ id: '9007199254740995', mine: false }], true))
      result.push(state.unread)
      return result
    })
    assert.deepEqual(logic, [false, true, false, false, false], 'large IDs, batches and stale responses are deduplicated')
    await page.getByRole('button', { name: '历史战绩', exact: true }).click()
    await page.locator('.history-entry').first().click()
    await page.getByRole('button', { name: '对局回放', exact: true }).click()
    await page.locator('.replay-toolbar .lobby-unread-dot').waitFor()
    await page.getByRole('button', { name: '聊天大厅', exact: true }).click()
    await page.locator('.lobby-chat-log').waitFor()
    assert.equal(await page.locator('.replay-toolbar .lobby-unread-dot').count(), 0)
    await page.getByRole('button', { name: '关闭社区面板', exact: true }).click()
    await page.getByRole('button', { name: '退出回放', exact: true }).click()
    if (viewport.width < 700) {
      await page.setViewportSize({ width: 740, height: 320 })
      await page.waitForTimeout(300)
    }
    await page.getByRole('button', { name: '开始对战', exact: true }).click()
    await page.waitForFunction(() => document.querySelector('.naval-page.in-game'))
    await page.waitForTimeout(400)
    const duringGame = requests
    messages.push({ id: 6, name: '其他指挥官', text: '游戏过程中不打扰', at: Date.now(), mine: false })
    await page.waitForTimeout(3000)
    assert.equal(requests, duringGame, 'gameplay pauses lobby polling')
    assert.equal(await page.getByRole('button', { name: '聊天大厅', exact: true }).count(), 0)
    await context.close()
  }
  assert.deepEqual(errors, [])
  console.log('PASS: desktop/mobile lobby dot, dedicated notification sound, old-history baseline, read clearing, deduplication, own-message suppression and mute')
} finally { await browser.close() }
