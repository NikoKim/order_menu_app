import { unitCounterPlan, unitCounterValueAt } from './navalUnitCounter.js'

const presentations = new Map()
const money = value => Number.isFinite(Number(value)) && Number(value) >= 0 ? Math.round(Number(value) * 100) : null

export function walletSettlementPlan(balance, pot = 0) {
  const balanceCents = balance == null ? null : money(balance)
  return {kind:'idle',amountCents:0,potCents:money(pot) ?? 0,balanceCents,fromCents:balanceCents,coins:[],counterStart:0,duration:0}
}

export function wagerSettlementPlan(wager, seat, balance) {
  if (wager?.status !== 'PAID' || ![0, 1].includes(seat) || ![0, 1].includes(wager.winner)) return null
  const own = wager.players?.find(player => player.seat === seat)
  if (!own) return null
  const stake = money(own.stake), payout = money(own.payout)
  if (stake == null || payout == null) return null
  const kind = wager.winner === seat ? 'win' : 'loss'
  const opponent = wager.players.find(player => player.seat === 1 - seat)
  const opponentStake = opponent ? money(opponent.stake) : null
  // Unmatched escrow is already refunded. Animate only the portion both players risked.
  const matched = opponentStake == null ? null : Math.min(stake, opponentStake)
  const reward = !opponent && stake === 0 && payout > 0
  if (!reward && (matched == null || matched === 0)) return null
  const amount = reward ? payout : kind === 'win' ? matched * 2 : matched
  if (!amount) return null
  const balanceCents = balance == null ? null : money(balance)
  // This reconstructs a display baseline, never another account credit or debit.
  const fromCents = balanceCents == null ? null : kind === 'win'
    ? balanceCents >= amount ? balanceCents - amount : balanceCents
    : balanceCents + amount
  const counter = unitCounterPlan(0, amount), counterStart = 800
  const count = Math.min(9, Math.max(4, Math.ceil(Math.sqrt(amount / 100))))
  const quantum = amount % 100 === 0 ? 100 : 1, units = amount / quantum
  const coins = Array.from({length:count}, (_, index) => ({
    id:index, startsAt:180 + index / Math.max(1, count - 1) * Math.max((count - 1) * 115, counter.duration - 400), travel:620,
    cents:(Math.round(units * (index + 1) / count) - Math.round(units * index / count)) * quantum
  }))
  return {kind, stake:stake / 100, payout:payout / 100, net:(payout - stake) / 100,
    amountCents:amount, potCents:reward ? payout : kind === 'win' ? matched * 2 : matched,
    balanceCents, fromCents, coins, counter, counterStart,
    duration:Math.max(2500, counterStart + counter.duration + 250, coins.at(-1).startsAt + 850)}
}

export function wagerPresentationFrame(plan, elapsed) {
  if (!plan || plan.balanceCents == null) return null
  const arrived = unitCounterValueAt(0, plan.amountCents, elapsed - plan.counterStart)
  return wagerTransferFrame(plan, arrived)
}

export function wagerTransferFrame(plan, transferred) {
  if (!plan || plan.balanceCents == null) return null
  const arrived = Math.max(0, Math.min(plan.amountCents, Number(transferred) || 0))
  const difference = plan.balanceCents - plan.fromCents
  const balance = plan.fromCents + Math.sign(difference) * Math.min(Math.abs(difference), arrived)
  return {balanceCents:balance, potCents:plan.kind === 'win' ? Math.max(0, plan.potCents - arrived) : plan.kind === 'loss' ? plan.potCents + arrived : plan.potCents}
}

export function hasWagerPresentation(account, gameKey) { return Boolean(account && gameKey && presentations.has(account + '|' + gameKey)) }

export function claimWagerPresentation(account, gameKey) {
  if (!account || !gameKey) return false
  const key = account + '|' + gameKey
  if (presentations.has(key)) return false
  presentations.set(key, true)
  if (presentations.size > 256) presentations.delete(presentations.keys().next().value)
  return true
}
