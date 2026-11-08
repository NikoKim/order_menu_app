export function unitCounterStep(currentCents, targetCents) {
  const current = Math.round(currentCents), target = Math.round(targetCents)
  return current + Math.sign(target - current) * Math.min(100, Math.abs(target - current))
}

export function unitCounterPlan(fromCents, toCents, {duration = 1600, minInterval = 4, maxInterval = 24} = {}) {
  const steps = Math.ceil(Math.abs(Math.round(toCents) - Math.round(fromCents)) / 100)
  const interval = Math.max(minInterval, Math.min(maxInterval, steps ? Math.ceil(duration / steps) : maxInterval))
  return {steps, interval, duration:steps * interval}
}

export function unitCounterValueAt(fromCents, toCents, elapsed, options) {
  const plan = unitCounterPlan(fromCents, toCents, options)
  const distance = Math.abs(Math.round(toCents) - Math.round(fromCents))
  const moved = Math.min(distance, Math.max(0, Math.floor(elapsed / plan.interval)) * 100)
  return Math.round(fromCents) + Math.sign(toCents - fromCents) * moved
}

// Each task changes the displayed amount by at most one coin, even after a delayed frame.
export function createNavalUnitCounter({fromCents = 0, toCents = 0, onChange = () => {}, onComplete = () => {}, setTimer = setTimeout, clearTimer = clearTimeout, ...timing} = {}) {
  const plan = unitCounterPlan(fromCents, toCents, timing)
  let value = Math.round(fromCents), timer, running = false, stopped = false, complete = false
  function done() { complete = true; running = false; onComplete(value) }
  function tick() {
    timer = undefined
    if (!running || stopped || complete) return
    value = unitCounterStep(value, toCents)
    onChange(value)
    if (value === Math.round(toCents)) done()
    else timer = setTimer(tick, plan.interval)
  }
  function resume() {
    if (running || stopped || complete) return
    if (value === Math.round(toCents)) { onChange(value); done(); return }
    running = true
    timer = setTimer(tick, plan.interval)
  }
  return {
    ...plan, start:resume, resume,
    pause() { running = false; clearTimer(timer); timer = undefined },
    stop() { stopped = true; running = false; clearTimer(timer); timer = undefined },
    finish() {
      if (stopped || complete) return
      clearTimer(timer); value = Math.round(toCents); onChange(value); done()
    },
    get value() { return value }, get complete() { return complete }
  }
}
