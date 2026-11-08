export const NAVAL_RANK_SOUND_SECONDS = {normal:4.8, exam:8.2}

// The live cue and the demo's offline render share this exact score.
export function scheduleNavalRankFanfare(context, destination, kind = 'normal', start = context.currentTime) {
  const ceremonial = kind === 'exam', nodes = [], voices = []
  const output = context.createGain()
  output.gain.value = .52
  output.connect(destination)
  const keep = node => { nodes.push(node); return node }
  function tone(frequency, offset, duration, volume = .12, timbre = 'brass') {
    const gain = keep(context.createGain()), filter = keep(context.createBiquadFilter())
    const at = start + offset
    filter.type = 'lowpass'; filter.frequency.value = timbre === 'bell' ? 5200 : 1750
    gain.gain.setValueAtTime(0, at)
    gain.gain.linearRampToValueAtTime(volume, at + .045)
    gain.gain.exponentialRampToValueAtTime(.0001, at + duration)
    filter.connect(gain); gain.connect(output)
    for (const [multiple, strength] of timbre === 'bell' ? [[1, 1], [2.01, .26], [3.98, .08]] : [[1, 1], [2, .16]]) {
      const oscillator = keep(context.createOscillator()), level = keep(context.createGain())
      oscillator.type = timbre === 'bell' ? 'sine' : 'triangle'
      oscillator.frequency.value = frequency * multiple
      level.gain.value = strength
      oscillator.connect(level); level.connect(filter)
      oscillator.start(at); oscillator.stop(at + duration + .02); voices.push(oscillator)
    }
  }
  const noise = context.createBuffer(1, Math.ceil(context.sampleRate * .3), context.sampleRate)
  const samples = noise.getChannelData(0)
  let seed = 71593
  for (let i = 0; i < samples.length; i++) { seed = (Math.imul(seed, 1664525) + 1013904223) | 0; samples[i] = seed / 2147483648 }
  function drum(offset, volume = .15, duration = .12) {
    const source = keep(context.createBufferSource()), gain = keep(context.createGain()), filter = keep(context.createBiquadFilter())
    const at = start + offset
    source.buffer = noise; filter.type = 'bandpass'; filter.frequency.value = 2100; filter.Q.value = .65
    gain.gain.setValueAtTime(volume, at); gain.gain.exponentialRampToValueAtTime(.0001, at + duration)
    source.connect(filter); filter.connect(gain); gain.connect(output)
    source.start(at); source.stop(at + duration); voices.push(source)
  }
  if (ceremonial) {
    for (let i = 0; i < 18; i++) drum(.22 + i * .11, .055 + i * .005, .09)
    tone(65.406, .15, 2.1, .17)
    for (const [note, at, length] of [[196, .6, .65], [261.626, 1.35, .6], [329.628, 2.08, .7], [391.995, 2.9, .65]]) tone(note, at, length, .17)
    for (let i = 0; i < 8; i++) drum(2.65 + i * .14, .1, .12)
    for (const frequency of [130.813, 261.626, 329.628, 391.995, 523.251]) tone(frequency, 3.78, 2.75, .12)
    tone(1046.502, 3.84, 3.8, .14, 'bell')
    tone(783.991, 4.12, 2.8, .08, 'bell')
    drum(3.78, .25, .24)
    for (const frequency of [261.626, 391.995, 523.251]) tone(frequency, 6.12, 1.7, .06)
  } else {
    for (const [frequency, at] of [[261.626, .3], [329.628, .65], [391.995, 1], [523.251, 1.38]]) tone(frequency, at, .65, .14)
    for (const frequency of [261.626, 329.628, 391.995]) tone(frequency, 2.12, 1.65, .09)
    tone(1046.502, 2.15, 2.35, .12, 'bell')
    drum(2.12, .12, .16)
  }
  let stopped = false
  return () => {
    if (stopped) return
    stopped = true
    for (const voice of voices) { try { voice.stop() } catch { } }
    for (const node of nodes) { try { node.disconnect() } catch { } }
    output.disconnect()
  }
}
