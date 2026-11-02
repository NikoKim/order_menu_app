import assert from 'node:assert/strict'
import { chromium } from 'playwright-core'
import { mkdir } from 'node:fs/promises'
import path from 'node:path'

const base = process.env.QA_BASE_URL || 'http://127.0.0.1:5174'
const output = process.env.QA_OUTPUT_DIR || path.resolve('../.agents/naval-qa')
await mkdir(output, { recursive: true })
const browser = await chromium.launch({ executablePath: process.env.QA_BROWSER || 'C:/Program Files/Google/Chrome/Application/chrome.exe', headless: true })
const errors = []
try {
  const page = await browser.newPage({ viewport: { width: 900, height: 800 } })
  page.on('pageerror', error => errors.push(error.message))
  await page.goto(base + '/', { waitUntil: 'domcontentloaded' })
  await page.locator('#app').waitFor()
  await page.evaluate(async () => {
    const url = '/src/views/entertainment/NavalBoard.vue', compiled = await (await fetch(url)).text()
    const vueUrl = compiled.match(/from\s+["']([^"']*\/vue\.js[^"']*)["']/)?.[1]
    if (!vueUrl) throw new Error('Cannot locate shared Vue runtime')
    const { createApp, h, reactive } = await import(vueUrl)
    const { default: Board } = await import(url)
    document.querySelector('#app').__vue_app__?.unmount()
    document.body.replaceChildren()
    const host = document.createElement('div'); host.style.cssText = 'width:624px;height:624px;margin:24px;'; document.body.append(host)
    window.fixtureReady = false; window.fixtureErrors = []; window.animationChanges = []; window.cellCommands = []
    window.fixture = reactive({ player: { ships: [], shots: [], mines: [], aa: [] }, hidden: true, defenseSide: 'left', label: '像素舰队测试', event: null, onCell: point => cellCommands.push(point), onAnimation: busy => animationChanges.push({ busy, at: performance.now() }), onReady: () => { window.fixtureReady = true }, onError: message => window.fixtureErrors.push(message) })
    window.fixtureApp = createApp({ render: () => h(Board, window.fixture) }); fixtureApp.mount(host)
    window.pixels = () => {
      const canvas = host.querySelector('canvas'), values = canvas.getContext('2d').getImageData(0, 0, 624, 624).data
      let dark = 0, warm = 0, saturated = 0, hash = 0
      for (let i = 0; i < values.length; i += 4) {
        const r = values[i], g = values[i + 1], b = values[i + 2]
        if (r < 110 && g < 130 && b < 130) dark++
        if (r > 170 && g < 190 && b < 155) warm++
        if (Math.max(r, g, b) - Math.min(r, g, b) > 60) saturated++
        if (i % 64 === 0) hash = (Math.imul(hash, 31) + r * 3 + g * 5 + b * 7) >>> 0
      }
      return { dark, warm, saturated, hash, renderer: canvas.dataset.renderer }
    }
    window.regionPixel = (x, y) => Array.from(host.querySelector('canvas').getContext('2d').getImageData(x, y, 1, 1).data)
    window.axisPixels = () => {
      const ctx = host.querySelector('canvas').getContext('2d'), counts = []
      const count = (x, y, w, h) => { const data = ctx.getImageData(x, y, w, h).data; let n = 0; for (let i = 0; i < data.length; i += 4) if (Math.abs(data[i] - 96) < 5 && Math.abs(data[i + 1] - 125) < 5 && Math.abs(data[i + 2] - 135) < 5) n++; return n }
      for (let i = 0; i < 12; i++) { counts.push(count(24 + i * 48 + 13, 4, 22, 16)); counts.push(count(0, 24 + i * 48 + 16, 24, 16)) }
      return counts
    }
  })
  await page.waitForFunction(() => window.fixtureReady || window.fixtureErrors.length)
  assert.deepEqual(await page.evaluate(() => fixtureErrors), [])
  await page.waitForTimeout(800)
  const hidden = await page.evaluate(() => pixels())
  assert.equal(hidden.renderer, 'phaser-3.90', 'Board must actually be rendered by Phaser')
  assert((await page.evaluate(() => axisPixels())).every(count => count >= 20), 'Every axis glyph must render without clipping')
  await page.evaluate(() => { fixture.player = { ships: [{ id: 'cruiser', x: 2, y: 2, w: 1, h: 3, hits: [] }], shots: [], mines: [{ x: 1, y: 1 }], aa: [7] } })
  await page.waitForTimeout(120)
  assert.deepEqual(await page.evaluate(() => pixels()), hidden, 'Enemy hidden assets must not affect pixels')
  await page.evaluate(() => { fixture.hidden = false })
  await page.waitForTimeout(150)
  const revealed = await page.evaluate(() => pixels())
  assert(revealed.dark > hidden.dark + 500, 'Generated fleet texture must be visible')
  await page.screenshot({ path: path.join(output, 'phaser-pixel-fleet.png') })
  await page.evaluate(() => { fixture.player.ships[0].hits = ['2,2']; fixture.player.shots = [{ x: 2, y: 2, result: 'hit' }] })
  await page.waitForTimeout(120)
  const gray = await page.evaluate(() => regionPixel(24 + 2 * 48 + 8, 24 + 2 * 48 + 8))
  assert(Math.max(...gray.slice(0, 3)) - Math.min(...gray.slice(0, 3)) < 22, `Hit grid must be gray, got ${gray}`)

  await page.evaluate(() => { fixture.hidden = true; fixture.player = { ships: [], shots: [], mines: [], aa: [] } })
  const animationChecks = []
  for (const weapon of ['shot', 'fighter', 'bomber', 'torpedo', 'radar', 'intercept', 'mine']) {
    await page.evaluate(weapon => { fixture.event = { id: `test-${weapon}`, weapon: weapon === 'intercept' ? 'fighter' : weapon, x: 4, y: 4, row: 4, cells: ['radar', 'intercept'].includes(weapon) ? [] : [{ x: 5, y: 4, result: 'hit' }, { x: 6, y: 4, result: 'miss' }], text: weapon === 'intercept' ? 'intercepted' : '' } }, weapon)
    if (weapon === 'mine') await page.evaluate(() => { fixture.event = { id: 'test-mine-shot', weapon: 'shot', x: 5, y: 4, cells: [{ x: 5, y: 4, result: 'mine' }] } })
    await page.waitForTimeout(weapon === 'intercept' ? 850 : weapon === 'mine' ? 900 : weapon === 'shot' ? 500 : weapon === 'radar' ? 600 : 1000)
    const active = await page.evaluate(() => pixels())
    assert.notEqual(active.hash, hidden.hash, `${weapon} animation must render pixels`)
    assert.equal(await page.locator('canvas').getAttribute('data-animating'), 'true', `${weapon} must remain readable for longer than a flash`)
    animationChecks.push({ weapon, visible: true, warm: active.warm })
    if (weapon === 'torpedo') {
      assert.equal(await page.locator('canvas').getAttribute('data-torpedo-asset'), 'torpedo-projectile', 'Torpedo must render its own bitmap projectile, not a primitive or an aircraft')
      await page.screenshot({ path: path.join(output, 'phaser-torpedo-projectile.png') })
    }
    await page.waitForFunction(() => document.querySelector('canvas').dataset.animating === 'false', null, { timeout: 12000 })
  }

  await page.evaluate(() => { fixture.event = { id: 'aa-row-nine', weapon: 'fighter', x: 2, y: 8, row: 9, cells: [], text: '防空炮击落了来袭飞机' } })
  await page.waitForFunction(() => document.querySelector('canvas').dataset.effectPhase === 'aa-aim')
  assert.equal(await page.locator('canvas').getAttribute('data-effect-row'), '9')
  assert.equal(await page.locator('canvas').getAttribute('data-effect-gun-x'), '44', 'Own board AA must emerge on its left edge')
  assert.equal(await page.locator('canvas').getAttribute('data-incoming-direction'), 'right-to-left', 'Incoming enemy aircraft must approach own board from the right')
  await page.screenshot({ path: path.join(output, 'phaser-aa-row-popup.png') })
  await page.waitForFunction(() => document.querySelector('canvas').dataset.effectPhase === 'aa-tracer')
  await page.waitForTimeout(120)
  await page.screenshot({ path: path.join(output, 'phaser-aa-tracer.png') })
  await page.waitForFunction(() => document.querySelector('canvas').dataset.effectPhase === 'plane-crash')
  await page.waitForTimeout(280)
  await page.screenshot({ path: path.join(output, 'phaser-aa-plane-crash.png') })
  await page.waitForFunction(() => document.querySelector('canvas').dataset.animating === 'false', null, { timeout: 10000 })
  await page.evaluate(() => { fixture.defenseSide = 'right'; fixture.event = { id: 'aa-right-board', weapon: 'bomber', x: 5, y: 2, row: 2, cells: [], text: 'intercepted' } })
  await page.waitForFunction(() => document.querySelector('canvas').dataset.effectEvent === 'aa-right-board' && document.querySelector('canvas').dataset.effectPhase === 'aa-aim')
  assert.equal(await page.locator('canvas').getAttribute('data-effect-gun-x'), '580', 'Enemy board AA must emerge on its right edge')
  assert.equal(await page.locator('canvas').getAttribute('data-incoming-direction'), 'left-to-right')
  await page.screenshot({ path: path.join(output, 'phaser-aa-right-board.png') })
  await page.waitForFunction(() => document.querySelector('canvas').dataset.animating === 'false', null, { timeout: 10000 })
  await page.waitForTimeout(450)
  await page.evaluate(() => { fixture.defenseSide = 'left'; fixture.radar = [{ x: 4, y: 4, occupied: true }, { x: 5, y: 4, occupied: false }] })
  await page.waitForTimeout(100)
  const beforeRadar = await page.evaluate(() => pixels())
  assert.equal(beforeRadar.hash, hidden.hash, 'Stored radar results may not draw persistent marks')
  await page.evaluate(() => { fixture.event = { id: 'transient-radar', weapon: 'radar', x: 4, y: 4, cells: [] } })
  await page.waitForTimeout(550)
  assert.notEqual((await page.evaluate(() => pixels())).hash, beforeRadar.hash, 'Radar results flash during the scan')
  await page.screenshot({ path: path.join(output, 'phaser-radar-transient.png') })
  await page.waitForTimeout(1100)
  assert.equal((await page.evaluate(() => pixels())).hash, beforeRadar.hash, 'Radar must leave no residual outlines or scan marks')

  await page.evaluate(() => { fixture.interactive = true; fixture.event = { id: 'rapid-followup', weapon: 'shot', cells: [{ x: 5, y: 5, result: 'hit' }] } })
  await page.waitForTimeout(180)
  assert.equal(await page.locator('canvas').getAttribute('data-animating'), 'true')
  await page.locator('canvas').click({ position: { x: 130, y: 130 } })
  assert.equal(await page.evaluate(() => cellCommands.length), 1, 'A confirmed followup attack must not be blocked by cosmetic animation')
  await page.evaluate(() => { fixture.interactive = false })
  await page.waitForFunction(() => document.querySelector('canvas').dataset.animating === 'false')
  await page.evaluate(() => { fixture.event = { id: 'mine-sequence', weapon: 'shot', x: 6, y: 7, cells: [{ x: 6, y: 7, result: 'mine' }] } })
  await page.waitForFunction(() => document.querySelector('canvas').dataset.effectPhase === 'mine-rise')
  assert.equal(await page.locator('canvas').getAttribute('data-mine-size'), '28', 'Mine keeps its normal grid size throughout its detonation')
  await page.screenshot({ path: path.join(output, 'phaser-mine-rise.png') })
  await page.waitForFunction(() => document.querySelector('canvas').dataset.effectPhase === 'mine-detonate')
  await page.waitForTimeout(350)
  await page.screenshot({ path: path.join(output, 'phaser-mine-explosion.png') })
  await page.waitForFunction(() => document.querySelector('canvas').dataset.effectPhase === 'mine-ripple')
  await page.waitForFunction(() => document.querySelector('canvas').dataset.animating === 'false', null, { timeout: 10000 })

  await page.evaluate(() => {
    fixture.events = [
      { id: 'queued-first', weapon: 'shot', cells: [{ x: 1, y: 2, result: 'miss' }] },
      { id: 'queued-second', weapon: 'shot', cells: [{ x: 3, y: 4, result: 'miss' }] }
    ]
  })
  await page.waitForFunction(() => JSON.parse(document.querySelector('canvas').dataset.playedEventIds||'[]').includes('queued-first'))
  await page.waitForFunction(() => document.querySelector('canvas').dataset.effectEvent === 'queued-second', null, { timeout: 10000 })
  await page.waitForFunction(() => document.querySelector('canvas').dataset.animating === 'false', null, { timeout: 10000 })
  assert((await page.evaluate(() => animationChanges.filter(change => change.busy).length)) >= 10, 'Every queued event must report its cosmetic playback status without dropping actions')
  await page.evaluate(() => {
    fixture.player = { ships: [{ id: 'new-sunk-enemy', x: 4, y: 3, w: 1, h: 3, hits: ['4,3', '4,4', '4,5'] }], shots: [3,4,5].map(y => ({ x: 4, y, result: 'hit' })), mines: [], aa: [] }
    fixture.event = { id: 'final-kill', weapon: 'shot', cells: [{ x: 4, y: 5, result: 'hit' }] }
    fixture.events.push(fixture.event)
  })
  await page.waitForTimeout(950)
  const sinking = await page.evaluate(() => pixels())
  await page.screenshot({ path: path.join(output, 'phaser-ship-destruction.png') })
  await page.waitForFunction(() => document.querySelector('canvas').dataset.animating === 'false', null, { timeout: 10000 })
  await page.waitForTimeout(2800)
  const afterSink = await page.evaluate(() => pixels())
  assert.notEqual(sinking.hash, afterSink.hash, 'Newly revealed sunk enemy must animate, not just show a static tint')
  const remainsGray = await page.evaluate(() => regionPixel(24 + 4 * 48 + 8, 24 + 3 * 48 + 8))
  assert(Math.max(...remainsGray.slice(0, 3)) - Math.min(...remainsGray.slice(0, 3)) < 24, 'Destroyed ship cells remain gray')

  const audio = await page.evaluate(async () => {
    const module = await import('/src/games/navalAudio.js')
    await module.preloadNavalAudio()
    const ctx = new OfflineAudioContext(2, 44100, 44100), results = []
    for (const name of ['cannon', 'explosion', 'splash', 'fighter', 'bomber', 'torpedo', 'sonar', 'intercept', 'sink', 'deploy', 'ready', 'transition', 'win', 'lose', 'mine', 'tick']) {
      const response = await fetch(`/entertainment/naval/v2/audio/${name}.wav`)
      if (!response.ok) throw new Error(`Missing WAV ${name}`)
      const buffer = await ctx.decodeAudioData(await response.arrayBuffer()), samples = buffer.getChannelData(0)
      let power = 0, peak = 0
      for (const value of samples) { power += value * value; peak = Math.max(peak, Math.abs(value)) }
      const rms = Math.sqrt(power / samples.length)
      if (rms < .005 || peak > .95) throw new Error(`Invalid rendered ${name} audio: ${rms}/${peak}`)
      results.push({ name, channels: buffer.numberOfChannels, duration: Number(buffer.duration.toFixed(2)), rms: Number(rms.toFixed(3)) })
    }
    return results
  })
  assert(audio.every(sample => sample.channels === 2), 'Dedicated WAV samples must have stereo reflections')
  assert(audio.find(sample => sample.name === 'tick')?.duration <= .25, 'Countdown tick must be short and unobtrusive')
  assert.deepEqual(errors, [])
  await page.evaluate(() => fixtureApp.unmount())
  await page.waitForTimeout(150)
  assert.equal(await page.locator('canvas').count(), 0, 'Phaser renderer must clean up on unmount')
  console.log(JSON.stringify({ renderer: 'Phaser 3.90', generatedSprites: true, hiddenData: 'passed', grayDamage: 'passed', destruction: 'animated', animationChecks, audio, output }))
} finally { await browser.close() }
