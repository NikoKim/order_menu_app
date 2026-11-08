const TAU = Math.PI * 2
const clamp = (value, min, max) => Math.max(min, Math.min(max, value))
const smooth = value => value * value * (3 - 2 * value)

export function shortestFleetTurn(from, to) {
  let delta = ((to - from) % TAU + TAU) % TAU
  if (delta > Math.PI) delta -= TAU
  return delta
}

export function canAnimateFleetPlacement(previous, next, state) {
  return Boolean(previous && next && !state.initial && !state.entering && !state.hidden && !state.reducedMotion &&
    state.placing && state.wasPlacing && state.selected === next.id && previous.id === next.id &&
    previous.kind === next.kind && previous.x >= 0 && previous.y >= 0 && next.x >= 0 && next.y >= 0 &&
    previous.w * previous.h === next.w * next.h && !(previous.hits?.length || next.hits?.length))
}

export function fleetPlacementMotion(from, to) {
  const dx = to.x - from.x, dy = to.y - from.y, distance = Math.hypot(dx, dy)
  const turn = to.rotation - from.rotation
  const lean = distance > .01
    ? clamp((-dx + dy * .65) / distance, -1, 1) * clamp(.035 + distance / 1800, .035, .11)
    : Math.sign(turn) * .055
  return {
    from:{...from}, to:{...to}, dx, dy, lean,
    overshoot:distance ? Math.min(9, distance * .065) / distance : 0,
    duration:clamp(510 + distance * .32 + Math.abs(turn) * 26, 510, 760)
  }
}

function interpolate(stops, progress) {
  for (let index = 1; index < stops.length; index++) {
    const [end, value] = stops[index], [start, previous] = stops[index - 1]
    if (progress <= end) return previous + (value - previous) * smooth((progress - start) / (end - start))
  }
  return stops.at(-1)[1]
}

// The hull brakes through its destination, then its smaller counter-swings settle.
export function sampleFleetPlacement(motion, progress) {
  const p = clamp(progress, 0, 1), extra = motion.overshoot
  if (p === 0) return {...motion.from}
  if (p === 1) return {...motion.to}
  const travel = interpolate([[0, 0], [.58, 1], [.72, 1 + extra], [.84, 1 - extra * .42], [.94, 1 + extra * .14], [1, 1]], p)
  const bank = interpolate([[0, 0], [.18, motion.lean], [.60, -motion.lean * .85], [.78, motion.lean * .44], [.90, -motion.lean * .18], [1, 0]], p)
  const turn = smooth(clamp(p / .70, 0, 1))
  return {
    x:motion.from.x + motion.dx * travel,
    y:motion.from.y + motion.dy * travel,
    rotation:motion.from.rotation + (motion.to.rotation - motion.from.rotation) * turn + bank
  }
}
