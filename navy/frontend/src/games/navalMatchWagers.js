import { unitCounterPlan } from './navalUnitCounter.js'

export function matchWagerPlan(people = []) {
  const amounts = people.slice(0, 2).map(person => { const value = Number(person?.wager); return Number.isFinite(value) ? Math.max(0, Math.round(value)) : 0 })
  const total = amounts.reduce((sum, value) => sum + value, 0)
  const counter = unitCounterPlan(0, total * 100), counterStart = 560
  const coins = []
  amounts.forEach((amount, side) => {
    if (!amount) return
    const count = Math.min(7, Math.max(3, Math.ceil(Math.sqrt(amount))))
    for (let index = 0; index < count; index++) {
      const startsAt = 130 + side * 260 + index / Math.max(1, count - 1) * Math.max((count - 1) * 47, counter.duration - 650)
      coins.push({ id: `${side}-${index}`, side, index, startsAt, arrivesAt: startsAt + 430, value: Math.round(amount * (index + 1) / count) - Math.round(amount * index / count) })
    }
  })
  return { amounts, total, coins, counter, counterStart, duration:Math.max(1800, counterStart + counter.duration + 250) }
}
