import assert from 'node:assert/strict'
import { mkdir } from 'node:fs/promises'
import path from 'node:path'
import { chromium } from 'playwright-core'

const base = process.env.QA_BASE_URL || 'http://127.0.0.1:5174'
const output = process.env.QA_OUTPUT_DIR || path.resolve('../.agents/naval-qa')
await mkdir(output, { recursive: true })
const browser = await chromium.launch({ executablePath: process.env.QA_CHROME_PATH || 'C:/Program Files/Google/Chrome/Application/chrome.exe', headless: true })
const errors = [], checks = []
try {
  const page = await browser.newPage({ viewport: { width: 844, height: 390 } })
  page.on('pageerror', error => errors.push(error.message))
  await page.goto(base + '/customer/entertainment/naval')
  await page.evaluate(async () => {
    const componentUrl = '/src/views/entertainment/NavalSettlement.vue'
    const compiled = await (await fetch(componentUrl)).text()
    const vueUrl = compiled.match(/from\s+["']([^"']*\/vue\.js[^"']*)["']/)?.[1]
    const { createApp, h, reactive } = await import(vueUrl)
    const { default: Settlement } = await import(componentUrl)
    const { default: Share } = await import('/src/views/entertainment/NavalShareDialog.vue')
    document.querySelector('#app').__vue_app__?.unmount()
    document.body.replaceChildren()
    const host = document.createElement('div'); document.body.append(host)
    window.panelEvents = []
    Object.defineProperty(navigator, 'clipboard', { configurable: true, value: { writeText: async value => { window.copiedShare = value } } })
    window.mountPanel = type => {
      window.panelApp?.unmount()
      const players = [0, 1].map(index => ({ id: `player-${index}`, name: index ? '远洋舰队指挥官' : '北海舰队指挥官', occupied: true, registered: false, avatarUrl: null, left: index === 1, continueRequested: false, shots: [], ships: [] }))
      const room = reactive({ id: 'panel-fixture', inviteCode: 'ABC234', phase: 'FINISHED', mode: type === 'ai' ? 'AI' : 'MULTIPLAYER', role: type === 'spectator' ? 'SPECTATOR' : 'PLAYER', you: type === 'spectator' ? -1 : 0, resultYou: type === 'spectator' ? -1 : 0, round: 2, winner: 0, players,
        spectators: Array.from({ length: 18 }, (_, index) => ({ id: `viewer-${index}`, name: `观众指挥官${index}`, registered: false })), viewMode: 'BOTH', ownSpectatorId: 'viewer-0',
        lastResult: { round: 2, winner: 0, finishReason: 'PLAYER_LEFT', players: players.map((player, index) => ({ ...player, hits: 20 - index * 4, shots: 38 + index * 4, shipsRemaining: 7 - index * 7 })) } })
      window.panelRoom = room
      const component = type === 'share' ? Share : Settlement
      window.panelApp = createApp({ render: () => h(component, { room, onClose: () => panelEvents.push('close'), onExit: () => panelEvents.push('exit'), onContinue: value => { panelEvents.push(['continue', value]); room.players[0].continueRequested = value }, onClaim: seat => panelEvents.push(['claim', seat]) }) })
      panelApp.mount(host)
    }
  })
  for (const viewport of [{ width: 844, height: 390 }, { width: 667, height: 375 }, { width: 568, height: 320 }]) {
    await page.setViewportSize(viewport)
    for (const type of ['player', 'spectator', 'ai', 'share']) {
      await page.evaluate(value => mountPanel(value), type)
      await page.waitForTimeout(150)
      if (type === 'share') {
        await page.getByRole('button', { name: '复制链接', exact: true }).click()
        await page.getByRole('status').filter({ hasText: '房间链接已复制' }).waitFor()
      }
      const fit = await page.getByRole('dialog').evaluate(element => {
        const box = element.getBoundingClientRect()
        const controls = [...element.querySelectorAll('button')].filter(control => !control.disabled && control.getClientRects().length).map(control => control.getBoundingClientRect().toJSON())
        const overflow = [...element.querySelectorAll('.settlement-inner,.naval-share-dialog,.room-share-preview,.next-round-seats')].filter(item => item.scrollHeight > item.clientHeight + 1).map(item => item.className)
        return { box: box.toJSON(), controls, overflow, scroll: { root: element.scrollHeight > element.clientHeight + 1, document: document.documentElement.scrollHeight > innerHeight + 1, horizontal: document.documentElement.scrollWidth > innerWidth + 1 } }
      })
      assert(fit.box.top >= 0 && fit.box.bottom <= viewport.height && fit.box.left >= 0 && fit.box.right <= viewport.width, `${type} dialog fits ${viewport.width}x${viewport.height}`)
      assert.deepEqual(fit.overflow, [], `${type}: important panel content must not scroll vertically`)
      assert.deepEqual(fit.scroll, { root: false, document: false, horizontal: false }, `${type}: no screen or dialog scrolling`)
      assert(fit.controls.every(box => box.top >= 0 && box.bottom <= viewport.height && box.left >= 0 && box.right <= viewport.width), `${type}: controls must fit viewport`)
      if (type === 'spectator') {
        const audience = await page.locator('.audience-list').evaluate(element => ({ avatars: [...element.querySelectorAll('.naval-avatar')].filter(avatar => avatar.getBoundingClientRect().width > 0).length, scrollsHorizontally: element.scrollWidth > element.clientWidth, verticalOverflow: element.scrollHeight > element.clientHeight + 1 }))
        assert.equal(audience.avatars, 18, 'all spectator avatars remain available in the horizontal strip')
        assert(audience.scrollsHorizontally && !audience.verticalOverflow, 'audience uses horizontal rather than vertical scrolling')
        await page.getByRole('button', { name: /二号席位.*加入此席位/ }).click()
        assert.deepEqual((await page.evaluate(() => panelEvents)).at(-1), ['claim', 1])
      } else if (type === 'player') {
        await page.getByRole('button', { name: '继续下一局', exact: true }).click()
        await page.getByRole('button', { name: '取消准备', exact: true }).click()
        assert.deepEqual((await page.evaluate(() => panelEvents)).slice(-2), [['continue', true], ['continue', false]])
      }
      await page.screenshot({ path: path.join(output, `mobile-panel-${type}-${viewport.width}x${viewport.height}.png`) })
      checks.push({ type, viewport, buttons: fit.controls.length, fit: true })
    }
  }
  assert.deepEqual(errors, [])
  console.log(JSON.stringify({ passed: true, checks, output }))
} finally { await browser.close() }
