import { tutorialPricing } from './guessStockTutorialPricing.js'
import { tutorialStep } from './guessStockTutorialLessons.js'
export { tutorialStep, TUTORIAL_LESSONS } from './guessStockTutorialLessons.js'

export const TUTORIAL_STORAGE_KEY = 'guess_stock_tutorial_v2'
const BANK = 'TUTORIAL_BANK'
const copy = value => JSON.parse(JSON.stringify(value))
const rounded = value => Math.round(value * 10000) / 10000

function makeRound(roundNo, previousClosePrice = 100) {
  const scenario = [null,
    { openBase: .5, openEvent: 1.5, closeBase: .2, closeEvent: 3 },
    { openBase: .3, openEvent: 1, closeBase: -.2, closeEvent: .5 },
    { openBase: -.3, openEvent: -5, closeBase: 0, closeEvent: -2 }][roundNo]
  return {
    roundNo, previousClosePrice,
    openPrice: null, closePrice: null, pricingEngineVersion: 'V1',
    openingEventOccurred: true, closingEventOccurred: true,
    openingEventEffective: true, closingEventEffective: true,
    openingEventTier: 'NORMAL', closingEventTier: 'NORMAL',
    openingBaseChangePercent: scenario.openBase, baseChangePercent: scenario.closeBase,
    openingEventChangePercent: scenario.openEvent, closingEventChangePercent: scenario.closeEvent,
    openingEventReason: '教学事件冲击已预设；实际价格还会计入本次买盘。正式游戏的事件存在不确定性。',
    closingEventReason: '实际收盘同时计入基础变化、事件、均值回归和卖盘，不由单一因素决定。',
    eventStage: 'OPENING', eventType: 'DEMAND', eventRelevance: 'HIGH',
    eventCredibility: 'CONFIRMED', eventSurprise: 'NORMAL', eventStrength: 'MEDIUM',
    eventName: roundNo === 3 ? '下游客户削减订单' : roundNo === 1 ? '启航科技获得新订单' : '订单温和增长',
    eventDescription: roundNo === 3
      ? '客户下调采购计划，需求走弱。即使不再买入，已有持仓也会受到价格变化影响。'
      : '公司公布新增订单，市场需求改善。先阅读消息，再观察它与订单流怎样共同影响价格。'
  }
}

export function createTutorialSession() {
  const round = makeRound(1)
  return revalue({
    id: 'tutorial', gameId: 'tutorial', status: 'ACTIVE', phase: 'ROUND_START', currentRound: 1,
    game: { stockCode: 'LEARN', title: '启航科技 · 新手教学股' },
    config: {
      stockCode: 'LEARN', stockType: '教学股', initialCash: 10000, initialPrice: 100,
      roundCount: 3, maxBuyQuantity: 100, progressionMode: 'MANUAL', tradingMode: 'CLASSIC',
      baseVolatilityPercent: 3, marketLiquidityShares: 200, maxOrderImpactPercent: 5,
      pricingEngineVersion: 'V1', aiEnabled: false, aiCount: 0, loanEnabled: true,
      fundamentalEnabled: true, researchPoints: 3, creditInteractionMode: 'PHASED',
      creditPhaseTimed: false, repaymentPhaseTimed: false, feePercent: 0, slippagePercent: 0,
      creditProducts: [{ code: BANK, name: '教学银行', enabled: true, maturityRounds: 1, maxExposureRatio: 150 }]
    },
    participants: [{ id: 1, type: 'PLAYER', currentPlayer: true, displayName: '我', initialCash: 10000, cash: 10000, quantity: 0, reservedCash: 0 }],
    rounds: [round], currentRoundState: round, currentOrder: null,
    recentTrades: [], aiDecisions: [], autoAdvance: false, playbackSpeed: 1,
    phaseEndsAt: null,
    credit: {
      enabled: true, riskState: 'NORMAL', effectiveDebt: 0, totalInterestCharged: 0,
      totalForcedLiquidationShares: 0,
      banks: [{ productCode: BANK, productName: '教学银行', principal: 0, accruedInterest: 0,
        contracts: [], recentLedger: [], warningLtv: 40, blockLtv: 50, liquidationLtv: 60 }]
    },
    tutorial: { version: 2, borrowed: false, researched: false, cancelled: false,
      firstOrderPlaced: false, reordered: false, remainingPoints: 3, researchResult: null,
      lessons: [], creditInput: 0, repayInput: 0, bankClosed: false, bankReopened: false }
  })
}

function revalue(session) {
  const player = session.participants[0]
  const bank = session.credit.banks[0]
  const round = session.currentRoundState
  const mark = round.closePrice ?? round.openPrice ?? round.previousClosePrice
  bank.effectiveDebt = rounded(bank.principal + bank.accruedInterest)
  bank.availableCredit = session.currentRound === 1 && !session.tutorial.borrowed ? 1000 : 0
  session.credit.effectiveDebt = bank.effectiveDebt
  player.asset = rounded(player.cash + player.quantity * mark - bank.effectiveDebt)
  player.returnPercent = rounded((player.asset / player.initialCash - 1) * 100)
  player.exposurePercent = player.asset > 0 ? rounded(player.quantity * mark / player.asset * 100) : 0
  session.credit.netAsset = player.asset
  session.credit.exposurePercent = player.exposurePercent
  session.credit.ltvPercent = rounded(bank.effectiveDebt / Math.max(1, player.cash + player.quantity * mark) * 100)
  session.serverTime = new Date().toISOString()
  session.rounds[session.currentRound - 1] = round
  return session
}

function requireCondition(condition, message) {
  if (!condition) throw new Error(message)
}

function ledger(session, type, cashDelta, interestDelta = 0) {
  const rows = session.credit.banks[0].recentLedger
  rows.unshift({ id: rows.length + 1, roundNo: session.currentRound, type, cashDelta, interestDelta })
}

function executeOrder(session, side, executionPrice, filledQuantity = null) {
  const player = session.participants[0]
  const order = session.currentOrder
  if (!order) return
  const quantity = filledQuantity ?? (side === 'BUY'
    ? Math.min(order.allIn ? Infinity : order.quantity, Math.floor(player.cash / executionPrice))
    : Math.min(order.quantity, player.quantity))
  player.cash = rounded(player.cash + quantity * executionPrice * (side === 'BUY' ? -1 : 1))
  player.quantity += quantity * (side === 'BUY' ? 1 : -1)
  player.reservedCash = 0
  session.recentTrades.unshift({ id: session.recentTrades.length + 1, participantId: player.id,
    participantName: player.displayName, roundNo: session.currentRound, side, quantity,
    executionPrice, amount: rounded(quantity * executionPrice), feeAmount: 0, slippagePercent: 0 })
  session.currentOrder = null
  if (side === 'BUY') session.currentRoundState.openingBuyQuantity = quantity
  else session.currentRoundState.closingSellQuantity = quantity
}

// Only the external shocks are scripted. Order pressure follows the existing V1 rules.
export function transitionTutorial(current, action, payload = {}) {
  const session = copy(current)
  const player = session.participants[0]
  const bank = session.credit.banks[0]
  const round = session.currentRoundState
  const first = session.currentRound === 1
  requireCondition(session.status === 'ACTIVE', '本次练习已完成，可以重新开始教程。')

  if (action === 'CONTINUE_LESSON') {
    const lesson = tutorialStep(session, payload.ui)
    requireCondition(lesson.id === payload.lessonId && lesson.mode !== 'action' && lesson.canContinue, '请先完成当前聚焦的操作。')
    session.tutorial.lessons.push(lesson.id)
    if (lesson.id === 'loan-input') session.tutorial.creditInput = Number(payload.ui.creditAmount)
  } else if (action === 'CLOSE_BANK') {
    requireCondition(first && session.phase === 'CREDIT_TRADING' && session.tutorial.borrowed, '请先完成借款。')
    session.tutorial.bankClosed = true
  } else if (action === 'REOPEN_BANK') {
    requireCondition(session.tutorial.bankClosed, '请先关闭银行窗口。')
    session.tutorial.bankReopened = true
  } else if (action === 'PREPARE_REPAY') {
    requireCondition(session.phase === 'CREDIT_REPAYMENT', '请先进入还贷阶段。')
    requireCondition(Number.isFinite(payload.amount) && payload.amount >= 0, '请输入有效还款金额。')
    session.tutorial.repayInput = payload.amount
  } else if (action === 'BORROW') {
    const amount = Number(payload.amount)
    requireCondition(session.phase === 'CREDIT_TRADING' && first && !session.tutorial.borrowed, '本阶段不再新增借款，请继续教程。')
    requireCondition(Number.isFinite(amount) && amount > 0 && amount <= 1000, '请输入大于 0、不超过 1,000 的练习借款。')
    bank.principal = rounded(amount)
    player.cash = rounded(player.cash + amount)
    session.tutorial.borrowed = true
    bank.contracts = [{ id: 1, status: 'ACTIVE', principalOriginal: amount, principalOutstanding: amount,
      accruedInterest: 0, interestRate: 1, maturityRound: 1, originalMaturityRound: 1,
      remainingRounds: 1, extensionCount: 0, totalExtendedRounds: 0, canExtend: false }]
    ledger(session, 'BORROW', amount)
  } else if (action === 'REPAY') {
    const amount = Number(payload.amount)
    requireCondition(session.phase === 'CREDIT_REPAYMENT', '请在银行还贷阶段练习还款。')
    requireCondition(Number.isFinite(amount) && amount > 0, '请输入有效还款金额。')
    const payment = rounded(Math.min(amount, player.cash, bank.principal + bank.accruedInterest))
    const interest = Math.min(payment, bank.accruedInterest)
    bank.accruedInterest = rounded(bank.accruedInterest - interest)
    bank.principal = rounded(bank.principal - (payment - interest))
    player.cash = rounded(player.cash - payment)
    Object.assign(bank.contracts[0], { principalOutstanding: bank.principal, accruedInterest: bank.accruedInterest,
      status: bank.principal + bank.accruedInterest === 0 ? 'CLOSED' : 'ACTIVE' })
    ledger(session, 'MANUAL_REPAY', -payment)
  } else if (action === 'RESEARCH') {
    requireCondition(first && session.phase === 'OPENING_TRADING', '请在第一回合开盘前完成研究练习。')
    requireCondition(session.tutorial.remainingPoints > 0, '研究点已用完，请关闭窗口继续下单。')
    requireCondition(['VERIFY_EVENT', 'SURVEY_DEMAND', 'PREVIEW_REPORT', 'CHECK_LIQUIDITY'].includes(payload.actionType), '请选择一种研究方式。')
    session.tutorial.researched = true
    session.tutorial.remainingPoints--
    session.tutorial.researchResult = { direction: 'UP', rangeLow: 1, rangeHigh: 4, remainingPoints: session.tutorial.remainingPoints }
  } else if (action === 'PLACE_ORDER') {
    const opening = session.phase === 'OPENING_TRADING'
    requireCondition((opening || session.phase === 'CLOSING_TRADING') && !(session.currentRound === 3 && opening), '第三回合不新增买单，请直接完成开盘前操作。')
    requireCondition(session.tutorial.researched, '先点击顶部的市场研究，完成一次事件核验。')
    requireCondition(payload.side === (opening ? 'BUY' : 'SELL'), '当前阶段的订单方向不正确。')
    const quantity = Number(payload.quantity)
    const max = opening ? Math.min(100, Math.floor(player.cash / round.previousClosePrice)) : player.quantity
    requireCondition(Number.isSafeInteger(quantity) && quantity > 0 && (payload.allIn || quantity <= max), '请输入可用范围内的整数股数。')
    session.currentOrder = { side: payload.side, quantity: payload.allIn ? max : quantity,
      allIn: Boolean(payload.allIn && opening), stage: opening ? 'OPENING' : 'CLOSING',
      status: 'PENDING', reservedAmount: opening ? rounded(Math.min(player.cash, quantity * round.previousClosePrice)) : 0 }
    player.reservedCash = session.currentOrder.reservedAmount
    if (first && opening) {
      session.tutorial.firstOrderPlaced = true
      if (session.tutorial.cancelled) session.tutorial.reordered = true
    }
  } else if (action === 'CANCEL_ORDER') {
    requireCondition(['OPENING_TRADING', 'CLOSING_TRADING'].includes(session.phase), '当前阶段不能撤单。')
    if (session.currentOrder && session.phase === 'OPENING_TRADING') session.tutorial.cancelled = true
    session.currentOrder = null
    player.reservedCash = 0
  } else if (action === 'ACK_EVENT') {
    requireCondition(session.phase.endsWith('EVENT_ACK'), '请先完成当前阶段。')
    session.phase = session.phase === 'OPENING_EVENT_ACK' ? 'CREDIT_TRADING' : 'CLOSING_TRADING'
  } else if (action === 'READY_NEXT_ROUND') {
    switch (session.phase) {
      case 'ROUND_START': session.phase = 'OPENING_EVENT_ACK'; break
      case 'CREDIT_TRADING':
        requireCondition(!first || session.tutorial.borrowed, '先输入借款金额并确认借款，再完成融资。')
        session.phase = 'OPENING_TRADING'
        break
      case 'OPENING_TRADING':
        requireCondition(!first || (session.tutorial.researched && session.tutorial.cancelled && session.currentOrder), '先完成研究、下单、撤单，再重新保存一笔买入订单。')
        requireCondition(session.currentRound !== 2 || session.currentOrder, '第二回合请先保存一笔买单。')
        {
          const order = session.currentOrder
          let quantity = order ? (order.allIn ? Math.floor(player.cash / round.previousClosePrice) : order.quantity) : 0
          let pricing = tutorialPricing(session, 'OPENING', quantity)
          for (let iteration = 0; order && iteration < 24; iteration++) {
            const affordable = Math.floor(player.cash / pricing.price)
            const adjusted = order.allIn ? affordable : Math.min(quantity, affordable)
            if (adjusted === quantity) break
            quantity = adjusted
            pricing = tutorialPricing(session, 'OPENING', quantity)
          }
          quantity = Math.min(quantity, Math.floor(player.cash / pricing.price))
          pricing = tutorialPricing(session, 'OPENING', quantity)
          round.openPrice = pricing.price
          round.openingOrderFlowChangePercent = pricing.flow
          round.openingTotalChangePercent = pricing.total
          round.openingBuyQuantity = quantity
          executeOrder(session, 'BUY', round.openPrice, quantity)
        }
        session.phase = 'OPENING_RESULT_ACK'
        break
      case 'OPENING_RESULT_ACK':
        session.phase = 'CLOSING_EVENT_ACK'
        round.eventStage = 'CLOSING'
        round.eventName = session.currentRound === 3 ? '需求持续走弱' : first ? '订单交付顺利，市场信心提升' : '交付进展温和改善'
        round.eventDescription = session.currentRound === 3 ? '需求未见改善，价格可能继续波动。需要根据剩余持仓和风险决定操作。'
          : '公司确认订单交付进展。利好与卖盘可能互相抵消，最终收盘要看全部价格分项。'
        break
      case 'CLOSING_TRADING':
        requireCondition(session.currentRound === 2
          ? session.currentOrder?.quantity > 0 && session.currentOrder.quantity < player.quantity
          : session.currentOrder?.quantity === player.quantity, session.currentRound === 2
          ? '第二回合请保存部分卖出订单，留下一部分持仓。' : '点击“全部”保存卖出全部持仓的订单，再完成收盘前操作。')
        {
          const pricing = tutorialPricing(session, 'CLOSING', session.currentOrder?.quantity || 0)
          round.closePrice = pricing.price
          round.orderFlowChangePercent = pricing.flow
          round.meanReversionChangePercent = pricing.reversion
          round.closingTotalChangePercent = pricing.total
        }
        round.totalChangePercent = rounded((round.closePrice / round.previousClosePrice - 1) * 100)
        executeOrder(session, 'SELL', round.closePrice)
        if (bank.principal > 0) {
          const interest = rounded(bank.principal * .01)
          bank.accruedInterest = rounded(bank.accruedInterest + interest)
          bank.contracts[0].accruedInterest = bank.accruedInterest
          session.credit.totalInterestCharged = rounded(session.credit.totalInterestCharged + interest)
          ledger(session, 'INTEREST_ACCRUAL', 0, interest)
        }
        round.clearedAt = new Date().toISOString()
        session.phase = 'ROUND_RESULT_ACK'
        break
      case 'ROUND_RESULT_ACK': session.phase = 'CREDIT_REPAYMENT'; break
      case 'CREDIT_REPAYMENT':
        requireCondition(bank.principal + bank.accruedInterest === 0, '先还清练习借款和利息，再进入下一阶段。')
        if (session.currentRound < session.config.roundCount) {
          session.currentRound++
          session.currentRoundState = makeRound(session.currentRound, round.closePrice)
          session.tutorial.repayInput = 0
          session.phase = 'OPENING_EVENT_ACK'
        } else {
          session.phase = 'COMPLETED'
          session.status = 'COMPLETED'
          session.finalAsset = player.cash
          session.profitAmount = rounded(player.cash - player.initialCash)
          session.returnPercent = rounded(session.profitAmount / player.initialCash * 100)
        }
        break
      default: throw new Error('请先完成当前引导操作。')
    }
  } else throw new Error('教程不支持此操作。')
  return revalue(session)
}

export function saveTutorialSession(session, storage) {
  try { (storage ?? globalThis.localStorage)?.setItem(TUTORIAL_STORAGE_KEY, JSON.stringify(session)) } catch { /* Practice still works without storage. */ }
}

export function loadTutorialSession(storage) {
  try {
    const session = JSON.parse((storage ?? globalThis.localStorage)?.getItem(TUTORIAL_STORAGE_KEY) || 'null')
    const phases = ['ROUND_START', 'OPENING_EVENT_ACK', 'CREDIT_TRADING', 'OPENING_TRADING', 'OPENING_RESULT_ACK',
      'CLOSING_EVENT_ACK', 'CLOSING_TRADING', 'ROUND_RESULT_ACK', 'CREDIT_REPAYMENT', 'COMPLETED']
    if (session?.id === 'tutorial' && session.tutorial?.version === 2
      && ['ACTIVE', 'COMPLETED'].includes(session.status) && [1, 2, 3].includes(session.currentRound)
      && phases.includes(session.phase) && session.config?.roundCount === 3 && session.game?.stockCode === 'LEARN'
      && Array.isArray(session.tutorial.lessons)
      && session.rounds?.length && Array.isArray(session.recentTrades) && Array.isArray(session.aiDecisions)
      && session.participants?.[0]?.type === 'PLAYER' && Number.isFinite(session.participants[0].cash)
      && Number.isSafeInteger(session.participants[0].quantity) && session.participants[0].quantity >= 0
      && Array.isArray(session.credit?.banks?.[0]?.contracts) && Array.isArray(session.credit.banks[0].recentLedger)
      && Number.isFinite(session.credit.banks[0].principal) && Number.isFinite(session.credit.banks[0].accruedInterest)
      && ['borrowed', 'researched', 'cancelled'].every(key => typeof session.tutorial[key] === 'boolean')
      && Number.isInteger(session.tutorial.remainingPoints) && session.tutorial.remainingPoints >= 0
      && session.currentRoundState?.roundNo === session.currentRound) {
      session.tutorial.bankClosed ??= false
      session.tutorial.bankReopened ??= false
      return revalue(session)
    }
  } catch { /* Ignore unavailable storage or an obsolete practice save. */ }
  return createTutorialSession()
}

