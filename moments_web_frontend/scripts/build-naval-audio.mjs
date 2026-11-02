import { mkdir, writeFile } from 'node:fs/promises'
import path from 'node:path'

// Offline sound design: layered transients, filtered textures, resonant metal and stereo tails.
// Runtime playback only decodes the resulting WAV samples; it does not synthesize oscillators.
const RATE = 44100
const output = path.resolve('public/entertainment/naval/v2/audio')
await mkdir(output, { recursive: true })
let seed = 0x4e415659
function random() { seed = (Math.imul(seed, 1664525) + 1013904223) >>> 0; return seed / 4294967296 }
function env(t, length, attack = .004, decay = 5) { return t < 0 || t > length ? 0 : Math.min(1, t / attack) * Math.exp(-decay * t / length) }
function noiseLayer(data, start, duration, gain, cutoff, decay = 5, highpass = false) {
  let low = 0, brown = 0
  const alpha = 1 - Math.exp(-2 * Math.PI * cutoff / RATE)
  const first = Math.floor(start * RATE), end = Math.min(data.length, Math.ceil((start + duration) * RATE))
  for (let i = first; i < end; i++) {
    const t = (i - first) / RATE, white = random() * 2 - 1
    brown = (brown + white * .035) / 1.015
    low += alpha * (white - low)
    const texture = highpass ? white - low : low * .78 + brown * .22
    data[i] += texture * gain * env(t, duration, .003, decay)
  }
}
function resonant(data, start, duration, gain, f0, f1 = f0, brightness = .2, decay = 6) {
  let phase = 0
  for (let i = Math.floor(start * RATE), end = Math.min(data.length, Math.ceil((start + duration) * RATE)); i < end; i++) {
    const t = i / RATE - start, freq = f0 * Math.pow(Math.max(1, f1) / f0, t / duration)
    phase += 2 * Math.PI * freq / RATE
    const fundamental = Math.sin(phase) + Math.sin(phase * 2.013) * brightness + Math.sin(phase * 3.92) * brightness * .23
    data[i] += fundamental * gain * env(t, duration, .002, decay)
  }
}
function engine(data, duration, gain, from, to, blades = 3) {
  let phase = 0, smooth = 0
  for (let i = 0; i < Math.min(data.length, duration * RATE); i++) {
    const t = i / RATE, p = t / duration, frequency = from + (to - from) * p
    phase += Math.PI * 2 * frequency / RATE
    smooth += .07 * (random() * 2 - 1 - smooth)
    const swell = Math.sin(Math.PI * p) ** 1.3
    const pulse = Math.sin(phase) * .42 + Math.sin(phase * blades) * .22 + Math.sin(phase * 2.005) * .18
    data[i] += gain * swell * (pulse + smooth * 1.8) * (.84 + .16 * Math.sin(t * 27))
  }
}
function cannon(data, offset = 0, strength = 1) {
  noiseLayer(data, offset, .075, .9 * strength, 5200, 9)
  noiseLayer(data, offset + .012, .65, 1.6 * strength, 260, 5)
  resonant(data, offset, .42, .8 * strength, 91, 29, .19, 6)
  resonant(data, offset + .035, .19, .14 * strength, 679, 204, .38, 9)
  noiseLayer(data, offset + .08, .44, .2 * strength, 1400, 6)
}
function explosion(data, offset = 0, strength = 1) {
  cannon(data, offset, strength * .7)
  noiseLayer(data, offset + .03, 1.4, 1.9 * strength, 170, 4)
  noiseLayer(data, offset + .055, .75, .4 * strength, 2100, 4)
  resonant(data, offset + .02, 1.3, .4 * strength, 63, 22, .2, 5)
  for (let n = 0; n < 8; n++) {
    const at = offset + .11 + random() * .82, power = (.07 + random() * .13) * strength
    noiseLayer(data, at, .02 + random() * .08, power, 6400, 8)
    resonant(data, at, .08 + random() * .15, power * .4, 430 + random() * 1900, 200 + random() * 260, .3, 8)
  }
}
function water(data, offset = 0, strength = 1) {
  noiseLayer(data, offset, .12, .6 * strength, 3900, 3)
  noiseLayer(data, offset + .06, .8, .9 * strength, 1500, 4)
  resonant(data, offset + .01, .19, .3 * strength, 151, 49, .1, 7)
  for (let n = 0; n < 18; n++) {
    const at = offset + .13 + random() * .65
    resonant(data, at, .025 + random() * .08, (.025 + random() * .035) * strength, 550 + random() * 2000, 300 + random() * 700, .05, 9)
  }
}
const designs = {
  cannon: [1.1, data => cannon(data)],
  explosion: [2.1, data => explosion(data)],
  splash: [1.3, data => water(data)],
  fighter: [3.2, data => { engine(data, 3.0, .55, 148, 78, 3); for (let n = 0; n < 12; n++) cannon(data, 1.05 + n * .1, .105) }],
  bomber: [3.4, data => { engine(data, 3.2, .6, 81, 57, 4); noiseLayer(data, 1.0, 2.0, .22, 670, 2) }],
  torpedo: [3.1, data => { engine(data, 2.9, .24, 180, 67, 6); noiseLayer(data, .05, 2.8, .5, 640, 1.5); water(data, 1.4, .28) }],
  submarine: [3.3, data => { engine(data, 2.8, .2, 44, 58, 5); noiseLayer(data, 0, 2.6, .2, 230, 1.8); resonant(data, .1, 1.4, .12, 311, 290, .05, 4); water(data, 1.24, .3); water(data, 1.42, .3) }],
  sonar: [1.7, data => { resonant(data, 0, .72, .26, 882, 878, .055, 4); resonant(data, .04, .85, .05, 1763, 1756, .02, 7); noiseLayer(data, .06, .6, .035, 370, 6) }],
  intercept: [1.5, data => { for (let n = 0; n < 3; n++) cannon(data, n * .2, .32); noiseLayer(data, .4, .7, .12, 1800, 5) }],
  sink: [3.3, data => { noiseLayer(data, 0, 2.1, .55, 210, 2.5); resonant(data, .08, 1.4, .19, 144, 51, .48, 2.7); resonant(data, .18, 1.8, .13, 229, 67, .4, 3); water(data, .6, .68); water(data, 1.6, .35) }],
  deploy: [.5, data => { noiseLayer(data, 0, .03, .2, 3200, 8); resonant(data, .018, .16, .12, 760, 340, .6, 8); resonant(data, .095, .15, .055, 1141, 800, .2, 8) }],
  ready: [.8, data => { noiseLayer(data, 0, .07, .13, 2200, 6); for (const [i, f] of [392, 523.25, 659.25].entries()) resonant(data, .04 + i * .09, .36, .09, f, f, .36, 5) }],
  transition: [1.15, data => { noiseLayer(data, 0, .85, .5, 1800, 1.4); resonant(data, .42, .55, .25, 92, 36, .2, 7); noiseLayer(data, .42, .08, .23, 4600, 8) }],
  win: [2.4, data => { for (const [i, f] of [261.63, 329.63, 392, 523.25].entries()) { resonant(data, i * .16, i === 3 ? 1.4 : .4, .1, f, f, .48, 2.8); resonant(data, i * .16, .85, .035, f * 2, f * 2, .2, 4) } }],
  lose: [2.2, data => { for (const [i, f] of [293.66, 261.63, 220, 146.83].entries()) resonant(data, i * .2, .95, .105, f, f * .993, .25, 3); noiseLayer(data, .5, 1.4, .07, 400, 3) }],
  mine: [2.9, data => { explosion(data, 0, .6); resonant(data, .025, 2.0, .64, 52, 19, .1, 3.8); water(data, .12, 1.1); water(data, 1.12, .45); noiseLayer(data, .25, 2.1, .4, 340, 2.5) }],
  tick: [.17, data => { noiseLayer(data, 0, .012, .17, 3400, 9); resonant(data, .002, .075, .12, 1280, 1110, .1, 8); resonant(data, .012, .09, .045, 740, 660, .15, 8) }],
  notification: [.65, data => { resonant(data, 0, .28, .2, 784, 784, .08, 5); resonant(data, .12, .4, .16, 1174.66, 1174.66, .07, 5) }]
}
function encode(data) {
  const left = new Float32Array(data.length), right = new Float32Array(data.length)
  const reflections = [[.061, .12], [.113, .08], [.197, .055], [.331, .032]]
  for (let i = 0; i < data.length; i++) {
    left[i] = data[i] * .87; right[i] = data[i] * .87
    for (const [seconds, gain] of reflections) {
      const l = i - Math.round(seconds * RATE), r = i - Math.round((seconds + .009) * RATE)
      if (l >= 0) left[i] += data[l] * gain
      if (r >= 0) right[i] += data[r] * gain
    }
  }
  let peak = 0
  for (let i = 0; i < data.length; i++) peak = Math.max(peak, Math.abs(left[i]), Math.abs(right[i]))
  const gain = Math.min(.9 / Math.max(peak, .001), 1.7)
  const pcm = Buffer.alloc(data.length * 4), head = Buffer.alloc(44)
  for (let i = 0; i < data.length; i++) { pcm.writeInt16LE(Math.round(Math.tanh(left[i] * gain) * 30000), i * 4); pcm.writeInt16LE(Math.round(Math.tanh(right[i] * gain) * 30000), i * 4 + 2) }
  head.write('RIFF', 0); head.writeUInt32LE(36 + pcm.length, 4); head.write('WAVEfmt ', 8); head.writeUInt32LE(16, 16); head.writeUInt16LE(1, 20); head.writeUInt16LE(2, 22); head.writeUInt32LE(RATE, 24); head.writeUInt32LE(RATE * 4, 28); head.writeUInt16LE(4, 32); head.writeUInt16LE(16, 34); head.write('data', 36); head.writeUInt32LE(pcm.length, 40)
  return Buffer.concat([head, pcm])
}
for (const [name, [duration, design]] of Object.entries(designs)) {
  const data = new Float32Array(Math.ceil(duration * RATE)); design(data)
  // Short fades prevent clicks at buffer boundaries and leave space for reflections.
  for (let i = 0; i < data.length; i++) data[i] *= Math.min(1, i / 80, (data.length - 1 - i) / 220)
  await writeFile(path.join(output, `${name}.wav`), encode(data))
}
await writeFile(path.join(output, 'README.md'), '# Naval sound library\n\nOriginal offline-designed stereo PCM WAV effects. No third-party samples or external licensing dependencies. Generated by `node scripts/build-naval-audio.mjs` from the frontend folder.\n\n44.1 kHz, stereo, 16-bit PCM. Layers include filtered impact textures, sub-bass transients, resonant metal, propeller passes, water droplets and decorrelated stereo reflections. Runtime uses decoded sample playback, not live oscillator synthesis.\n')
console.log(`Rendered ${Object.keys(designs).length} stereo game samples to ${output}`)
