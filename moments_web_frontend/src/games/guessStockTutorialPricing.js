const round4 = value => Math.round(value * 10000) / 10000

// Mirrors GuessStockRules.quantityImpact and stagePrice for the offline V1 lesson.
export function tutorialFlowImpact(quantity, liquidity, participantCount = 1, cap = 5) {
  const depth = Math.max(1, liquidity) * Math.max(1, participantCount)
  return round4(cap * Math.tanh(Math.max(0, quantity) / depth))
}

export function tutorialPricing(session, stage, quantity, liquidity = session.config.marketLiquidityShares) {
  const round = session.currentRoundState
  const opening = stage === 'OPENING'
  const reference = opening ? round.previousClosePrice : round.openPrice
  const base = Number(opening ? round.openingBaseChangePercent : round.baseChangePercent)
  const event = Number(opening ? round.openingEventChangePercent : round.closingEventChangePercent)
  const limit = session.config.baseVolatilityPercent * .5
  const reversion = opening ? 0 : round4(Math.max(-limit, Math.min(limit,
    -Math.log(reference / round.previousClosePrice) * 100 * .18)))
  const flow = tutorialFlowImpact(quantity, liquidity, session.participants.length, session.config.maxOrderImpactPercent)
    * (opening ? 1 : -1)
  const total = round4(base + event + reversion + flow)
  const price = round4(Math.max(.01, reference * Math.exp(total / 100)))
  return { reference, base, event, reversion, flow, total, price,
    changePercent: round4((price / reference - 1) * 100), quantity, liquidity }
}
