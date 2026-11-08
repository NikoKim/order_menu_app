export function scheduleNavalExperienceCue(context, destination, start = context.currentTime, offset = 0) {
  const nodes = [], sources = [], bus = context.createGain()
  bus.gain.value = .42; bus.connect(destination)
  for (let index = 0; index < 9; index++) {
    const at = .15 + index * .125
    if (at < offset) continue
    const source = context.createOscillator(), gain = context.createGain(), begins = start + at - offset
    source.type = 'sine'; source.frequency.value = 440 * Math.pow(2, index / 12)
    gain.gain.setValueAtTime(.0001, begins); gain.gain.linearRampToValueAtTime(.14, begins + .012)
    gain.gain.exponentialRampToValueAtTime(.0001, begins + .19)
    source.connect(gain); gain.connect(bus); source.start(begins); source.stop(begins + .21)
    nodes.push(source, gain); sources.push(source)
  }
  return () => { sources.forEach(source => { try { source.stop() } catch { } }); nodes.forEach(node => node.disconnect()); bus.disconnect() }
}
