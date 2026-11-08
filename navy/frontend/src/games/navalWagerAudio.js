export const NAVAL_WAGER_SOUND_SECONDS = 2.5

export function scheduleNavalWagerCue(context, destination, kind, start = context.currentTime, offset = 0) {
  const nodes = [], voices = [], bus = context.createGain()
  bus.gain.value = .62; bus.connect(destination)
  const keep = node => { nodes.push(node); return node }
  function chime(frequency, at, duration, volume) {
    if (at < offset) return
    const begins = start + at - offset
    for (const [multiple, strength] of [[1, 1], [2.76, .2]]) {
      const source = keep(context.createOscillator()), gain = keep(context.createGain())
      source.type = 'sine'; source.frequency.setValueAtTime(frequency * multiple, begins)
      source.frequency.exponentialRampToValueAtTime(frequency * multiple * .94, begins + duration)
      gain.gain.setValueAtTime(.0001, begins)
      gain.gain.linearRampToValueAtTime(volume * strength, begins + .005)
      gain.gain.exponentialRampToValueAtTime(.0001, begins + duration)
      source.connect(gain); gain.connect(bus)
      source.start(begins); source.stop(begins + duration + .01); voices.push(source)
    }
  }
  const win = kind === 'win'
  for (let index = 0; index < 9; index++) {
    chime(win ? 1350 + index * 78 : 1100 - index * 73, .18 + index * .115, .14, .12)
  }
  if (win) {
    chime(1046.5, 1.68, .5, .15); chime(1567.98, 1.82, .62, .14)
  } else {
    chime(392, 1.6, .38, .12); chime(261.63, 1.83, .55, .11)
  }
  let stopped = false
  return () => {
    if (stopped) return
    stopped = true
    for (const voice of voices) { try { voice.stop() } catch { } }
    for (const node of nodes) { try { node.disconnect() } catch { } }
    bus.disconnect()
  }
}
