import test from 'node:test'
import assert from 'node:assert/strict'
import { createTutorialSession, loadTutorialSession, saveTutorialSession, transitionTutorial, tutorialStep, TUTORIAL_LESSONS, TUTORIAL_STORAGE_KEY } from '../src/games/guessStockTutorial.js'
import { tutorialFlowImpact, tutorialPricing } from '../src/games/guessStockTutorialPricing.js'

function practice() {
  let session = createTutorialSession()
  return { get session() { return session }, act(action, payload) { session = transitionTutorial(session, action, payload); return session } }
}
function reachTrading(game) {
  game.act('READY_NEXT_ROUND'); game.act('ACK_EVENT'); game.act('BORROW', { amount: 1000 }); game.act('READY_NEXT_ROUND')
}
function buy(game, quantity = 25, allIn = false) {
  game.act('RESEARCH', { actionType: 'VERIFY_EVENT' })
  game.act('PLACE_ORDER', { side: 'BUY', quantity, allIn }); game.act('CANCEL_ORDER')
  game.act('PLACE_ORDER', { side: 'BUY', quantity, allIn }); game.act('READY_NEXT_ROUND')
}
function sell(game, quantity) {
  game.act('READY_NEXT_ROUND'); game.act('ACK_EVENT')
  game.act('PLACE_ORDER', { side: 'SELL', quantity }); game.act('READY_NEXT_ROUND'); game.act('READY_NEXT_ROUND')
}

test('V1 price decomposition matches the backend formula, including log returns and bounded pressure', () => {
  const session = createTutorialSession()
  assert.equal(tutorialFlowImpact(25, 200), .6218)
  assert.equal(tutorialFlowImpact(0, 200), 0)
  assert.equal(tutorialFlowImpact(100000, 200), 5)
  assert.ok(tutorialFlowImpact(75, 200) > tutorialFlowImpact(25, 200))
  assert.ok(tutorialFlowImpact(75, 200) > tutorialFlowImpact(75, 2000))
  assert.ok(tutorialFlowImpact(75, 200, 2) < tutorialFlowImpact(75, 200, 1))
  const price = tutorialPricing(session, 'OPENING', 25)
  assert.equal(price.flow, .6218)
  assert.equal(price.total, 2.6218)
  assert.equal(price.price, 102.6565)
  assert.notEqual(price.total, price.changePercent)
  session.currentRoundState.openPrice = price.price
  const close = tutorialPricing(session, 'CLOSING', 25)
  const noSell = tutorialPricing(session, 'CLOSING', 0)
  assert.equal(close.flow, -.6218)
  assert.ok(close.reversion < 0)
  assert.ok(close.price < noSell.price)
  assert.ok(close.price > price.price, 'positive events can outweigh selling pressure')
})

test('three rounds include financing, partial selling, carry-over holdings and a loss shock', () => {
  const game = practice()
  reachTrading(game)
  assert.equal(game.session.participants[0].cash, 11000)
  assert.equal(game.session.participants[0].asset, 10000)
  buy(game)
  assert.equal(game.session.participants[0].quantity, 25)
  assert.equal(game.session.recentTrades[0].executionPrice, 102.6565)
  sell(game, 25)
  assert.equal(game.session.credit.effectiveDebt, 1010)
  game.act('REPAY', { amount: 1010 }); game.act('READY_NEXT_ROUND')
  game.act('ACK_EVENT'); game.act('READY_NEXT_ROUND')
  game.act('PLACE_ORDER', { side: 'BUY', quantity: 47 }); game.act('READY_NEXT_ROUND')
  assert.ok(game.session.currentRoundState.openingOrderFlowChangePercent > game.session.rounds[0].openingOrderFlowChangePercent)
  sell(game, 23)
  assert.equal(game.session.participants[0].quantity, 24)
  const assetBeforeShock = game.session.participants[0].asset
  const cashBeforeShock = game.session.participants[0].cash
  game.act('READY_NEXT_ROUND'); game.act('ACK_EVENT'); game.act('READY_NEXT_ROUND')
  assert.throws(() => game.act('PLACE_ORDER', { side: 'BUY', quantity: 1 }), /不新增买单/)
  game.act('READY_NEXT_ROUND')
  assert.equal(game.session.currentRoundState.openingOrderFlowChangePercent, 0)
  assert.equal(game.session.participants[0].cash, cashBeforeShock)
  assert.ok(game.session.participants[0].asset < assetBeforeShock)
  sell(game, 24); game.act('READY_NEXT_ROUND')
  assert.equal(game.session.status, 'COMPLETED')
  assert.equal(game.session.credit.effectiveDebt, 0)
  assert.equal(game.session.credit.totalInterestCharged, 10)
  assert.equal(game.session.participants[0].quantity, 0)
  assert.equal(game.session.rounds.length, 3)
  assert.equal(game.session.recentTrades.length, 5)
  const tradeCash = game.session.recentTrades.reduce((total, trade) => total + trade.amount * (trade.side === 'BUY' ? -1 : 1), 0)
  assert.ok(Math.abs(game.session.finalAsset - (10000 + tradeCash - 10)) < .0001)
  assert.throws(() => game.act('READY_NEXT_ROUND'), /已完成/)
})

test('every lesson is reachable in order through the complete guided interaction path', () => {
  const game = practice()
  const ui = { researchOpen: false, creditAmount: 0 }
  const visited = []
  for (let iteration = 0; iteration < 120; iteration++) {
    const lesson = tutorialStep(game.session, ui)
    visited.push(lesson.id)
    if (lesson.id === 'complete') break
    if (lesson.mode !== 'action') {
      if (lesson.id === 'loan-input') ui.creditAmount = 1000
      game.act('CONTINUE_LESSON', { lessonId: lesson.id, ui })
      continue
    }
    switch (lesson.target) {
      case 'research-trigger': ui.researchOpen = true; break
      case 'research-verify': game.act('RESEARCH', { actionType: 'VERIFY_EVENT' }); break
      case 'research-close': ui.researchOpen = false; break
      case 'credit-close': game.act('CLOSE_BANK'); break
      case 'bank-trigger': game.act('REOPEN_BANK'); break
      case 'event-confirm': game.act('ACK_EVENT'); break
      case 'credit-borrow-confirm': game.act('BORROW', { amount: ui.creditAmount }); ui.creditAmount = 0; break
      case 'credit-full': ui.creditAmount = game.session.credit.effectiveDebt; game.act('PREPARE_REPAY', { amount: ui.creditAmount }); break
      case 'credit-repay-confirm': game.act('REPAY', { amount: ui.creditAmount }); ui.creditAmount = 0; break
      case 'order-quarter': game.act('PLACE_ORDER', { side: 'BUY', quantity: 25 }); break
      case 'order-half': {
        const opening = game.session.phase === 'OPENING_TRADING'
        const max = opening ? Math.min(100, Math.floor(game.session.participants[0].cash / game.session.currentRoundState.previousClosePrice)) : game.session.participants[0].quantity
        game.act('PLACE_ORDER', { side: opening ? 'BUY' : 'SELL', quantity: Math.floor(max * .5) }); break
      }
      case 'order-all': game.act('PLACE_ORDER', { side: 'SELL', quantity: game.session.participants[0].quantity }); break
      case 'order-cancel': game.act('CANCEL_ORDER'); break
      default: game.act('READY_NEXT_ROUND')
    }
  }
  assert.deepEqual(visited, TUTORIAL_LESSONS.map(lesson => lesson.id))
  assert.ok(visited.length >= 60)
  assert.equal(game.session.status, 'COMPLETED')
})

test('only the current valid lesson can be acknowledged; inputs do not jump during typing', () => {
  const game = practice()
  assert.throws(() => game.act('CONTINUE_LESSON', { lessonId: 'intro-cash' }), /聚焦/)
  for (const id of ['intro-price', 'intro-cash', 'intro-position', 'intro-asset', 'intro-chart']) game.act('CONTINUE_LESSON', { lessonId: id })
  game.act('READY_NEXT_ROUND')
  game.act('CONTINUE_LESSON', { lessonId: 'r1-event' }); game.act('ACK_EVENT')
  game.act('CONTINUE_LESSON', { lessonId: 'r1-credit-limit' })
  assert.equal(tutorialStep(game.session, { creditAmount: 1000 }).id, 'loan-input')
  assert.throws(() => game.act('CONTINUE_LESSON', { lessonId: 'loan-input', ui: { creditAmount: 100 } }), /聚焦/)
  game.act('CONTINUE_LESSON', { lessonId: 'loan-input', ui: { creditAmount: 1000 } })
  assert.equal(game.session.tutorial.creditInput, 1000)
  assert.equal(tutorialStep(game.session).target, 'credit-borrow-confirm')
})

test('closing and reopening the bank preserve cash and debt', () => {
  const game = practice()
  game.act('READY_NEXT_ROUND'); game.act('ACK_EVENT'); game.act('BORROW', { amount: 1000 })
  const before = JSON.stringify({ order: game.session.currentOrder, player: game.session.participants[0], credit: game.session.credit })
  game.act('CLOSE_BANK'); game.act('REOPEN_BANK')
  const after = JSON.stringify({ order: game.session.currentOrder, player: game.session.participants[0], credit: game.session.credit })
  assert.equal(after, before)
  assert.equal(game.session.tutorial.bankClosed, true)
  assert.equal(game.session.tutorial.bankReopened, true)
  assert.throws(() => game.act('SET_LAB', { quantity: 75 }))
})

test('invalid trades are atomic, and partial repayment must finish before proceeding', () => {
  const game = practice()
  game.act('READY_NEXT_ROUND'); game.act('ACK_EVENT')
  const before = JSON.stringify(game.session)
  assert.throws(() => game.act('READY_NEXT_ROUND'), /借款/)
  for (const amount of [0, -1, NaN, Infinity, 1001]) assert.throws(() => game.act('BORROW', { amount }))
  assert.equal(JSON.stringify(game.session), before)
  game.act('BORROW', { amount: 1000 }); game.act('READY_NEXT_ROUND')
  assert.throws(() => game.act('PLACE_ORDER', { side: 'BUY', quantity: 25 }), /市场研究/)
  buy(game); sell(game, 25)
  game.act('REPAY', { amount: 500 })
  assert.equal(game.session.credit.effectiveDebt, 510)
  assert.throws(() => game.act('READY_NEXT_ROUND'), /还清/)
  game.act('REPAY', { amount: 999999 }); game.act('READY_NEXT_ROUND')
  assert.throws(() => game.act('BORROW', { amount: 1000 }))
})

test('all-in iterates price and affordability without negative cash', () => {
  const game = practice()
  reachTrading(game); buy(game, 110, true)
  const player = game.session.participants[0]
  assert.ok(player.cash >= 0)
  assert.equal(game.session.currentRoundState.openPrice, tutorialPricing(game.session, 'OPENING', player.quantity).price)
  assert.ok(player.quantity <= Math.floor(11000 / game.session.currentRoundState.openPrice))
})

test('new saves are isolated from both formal records and the retained older tutorial', () => {
  const game = practice()
  reachTrading(game)
  game.act('RESEARCH', { actionType: 'VERIFY_EVENT' })
  game.act('PLACE_ORDER', { side: 'BUY', quantity: 25 })
  const values = new Map([['formal_session', 'untouched'], ['guess_stock_tutorial_v1', 'old-practice']])
  const storage = { getItem: key => values.get(key), setItem: (key, value) => values.set(key, value) }
  saveTutorialSession(game.session, storage)
  const loaded = loadTutorialSession(storage)
  assert.equal(loaded.currentOrder.quantity, 25)
  assert.equal(loaded.tutorial.remainingPoints, 2)
  assert.equal(loaded.config.roundCount, 3)
  assert.equal(values.get('formal_session'), 'untouched')
  assert.equal(values.get('guess_stock_tutorial_v1'), 'old-practice')
  assert.ok(values.has(TUTORIAL_STORAGE_KEY))
})

test('missing, corrupt and inaccessible storage permit a fresh practice', () => {
  const unavailable = { getItem() { throw new Error('blocked') }, setItem() { throw new Error('full') } }
  for (const storage of [{ getItem: () => '{broken' }, { getItem: () => null }, unavailable]) assert.equal(loadTutorialSession(storage).phase, 'ROUND_START')
  assert.doesNotThrow(() => saveTutorialSession(createTutorialSession(), unavailable))
  for (const patch of [{ phase: 'UNKNOWN' }, { config: null }, { recentTrades: null }, { participants: [{ cash: null }] }]) {
    assert.equal(loadTutorialSession({ getItem: () => JSON.stringify({ ...createTutorialSession(), ...patch }) }).phase, 'ROUND_START')
  }
})
