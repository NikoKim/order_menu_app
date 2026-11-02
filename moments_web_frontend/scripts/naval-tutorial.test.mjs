import assert from 'node:assert/strict'
import test from 'node:test'
import { createNavalTutorial, loadNavalTutorial, saveNavalTutorial, getNavalTutorialStep, advanceNavalTutorial, prepareTutorialAction, navalTutorialStorageKey, NAVAL_TUTORIAL_STORAGE_KEY, NAVAL_MOBILE_TUTORIAL_STORAGE_KEY, NAVAL_TUTORIAL_LESSONS, NAVAL_MOBILE_TUTORIAL_LESSONS } from '../src/games/navalTutorial.js'
import { generateFleet, localAction, validateFleet } from '../src/games/navalEngine.js'

const copy = value => JSON.parse(JSON.stringify(value))
const storage = () => {
  const data = new Map([['naval-guest-session', 'formal-session'], ['naval-guest-history', 'formal-history']])
  return { data, getItem: key => data.get(key) || null, setItem: (key, value) => data.set(key, value) }
}

test('training uses deterministic legal fleets, private storage and no autonomous AI timer', () => {
  const first = createNavalTutorial(1000), second = createNavalTutorial(1000)
  assert.equal(first.room.id, 'naval-tutorial')
  assert.equal(first.room.tutorial, true)
  assert.equal(first.room.mode, 'AI')
  assert.deepEqual(first.room.players, second.room.players)
  assert.equal(validateFleet(first.room.players[0].ships), '')
  assert.equal(validateFleet(first.room.players[1].ships), '')
  assert.equal(first.room._aiNextAt, null)
  assert.deepEqual(first.room.players[1].mines, [first.targets.mine, { x: 1, y: 11 }])
  assert.equal(first.room.players[0].ships.reduce((sum, ship) => sum + ship.w * ship.h, 0), 20)
})

test('save and resume never overwrite formal guest sessions or history', () => {
  const fake = storage(), value = advanceNavalTutorial(createNavalTutorial(), 'CONTINUE')
  assert.equal(saveNavalTutorial(value, fake), true)
  assert.deepEqual(loadNavalTutorial(fake), value)
  assert.equal(fake.data.get('naval-guest-session'), 'formal-session')
  assert.equal(fake.data.get('naval-guest-history'), 'formal-history')
  assert(fake.data.has(NAVAL_TUTORIAL_STORAGE_KEY))
})

test('corrupt, unsupported or blocked storage starts a fresh isolated lesson', () => {
  assert.equal(loadNavalTutorial({ getItem: () => '{bad' }).step, 0)
  assert.equal(loadNavalTutorial({ getItem: () => JSON.stringify({ version: 999 }) }).step, 0)
  assert.equal(loadNavalTutorial({ getItem: () => { throw new Error('blocked') } }).step, 0)
  assert.equal(saveNavalTutorial(createNavalTutorial(), { setItem: () => { throw new Error('full') } }), false)
  assert.equal(saveNavalTutorial({ version: 0 }, storage()), false)
})

test('only the matching action advances a lesson and choosing a weapon only retargets the spotlight', () => {
  const initial = createNavalTutorial()
  assert.equal(advanceNavalTutorial(initial, { type: 'PURCHASE', key: 'fighter' }), initial)
  let value = advanceNavalTutorial(initial, 'CONTINUE')
  assert.equal(getNavalTutorialStep(value).target, 'naval-fleet-roster')
  assert.equal(advanceNavalTutorial(value, { type: 'MOVE_SHIP' }), value)
  assert.equal(advanceNavalTutorial(value, { type: 'SELECT_SHIP', shipId: 'missing' }), value)
  value = advanceNavalTutorial(value, { type: 'SELECT_SHIP', shipId: value.room.players[0].ships[0].id })
  assert.equal(value.step, 1)
  assert.equal(getNavalTutorialStep(value).target, 'naval-own-board')
  assert(getNavalTutorialStep(value).cell)
  const radar = { ...value, step: NAVAL_TUTORIAL_LESSONS.findIndex(step => step.id === 'radar') }
  assert.equal(getNavalTutorialStep(radar).target, 'naval-weapon-radar')
  const selected = advanceNavalTutorial(radar, { type: 'WEAPON', weapon: 'radar' })
  assert.equal(selected.step, radar.step)
  assert.equal(getNavalTutorialStep(selected).target, 'naval-enemy-board')
  assert.deepEqual(getNavalTutorialStep(selected).cell, selected.targets.radar)
})

test('prepared actions refresh stale timers and reject attacks outside the taught target', () => {
  const value = createNavalTutorial(0)
  assert.throws(() => prepareTutorialAction(value, { type: 'ATTACK', weapon: 'shot', x: 0, y: 0 }), /教程/)
  const ready = { ...value, step: NAVAL_TUTORIAL_LESSONS.findIndex(step => step.id === 'hit'), room: { ...value.room, deadline: 0, turn: 1 } }
  const prepared = prepareTutorialAction(ready, { type: 'ATTACK', weapon: 'shot', ...ready.targets.hit }, 5000)
  assert.equal(prepared.room.turn, 0)
  assert(prepared.room.deadline > 5000)
  assert.equal(prepared.room._aiNextAt, null)
  assert.equal(ready.room.turn, 1, 'preparation never mutates the saved session')
})

test('all desktop lessons are reachable using real deployment, attacks, mine explosion, radar and AA interception', () => {
  let value = createNavalTutorial(1000), now = 5000
  const visited = []
  function apply(action) {
    now += 1000
    const prepared = prepareTutorialAction(value, action, now)
    value.room = localAction(prepared.room, prepared.action, now)
    value = advanceNavalTutorial(value, { type: 'ACTION', action })
  }
  function deploy() { apply({ type: 'DEPLOY', ships: value.room.players[0].ships, inventory: value.room.players[0].inventory, mines: value.room.players[0].mines, aa: value.room.players[0].aa, coatedShips: value.room.players[0].ships.filter(ship => ship.stealth).map(ship => ship.id) }) }
  for (let guard = 0; !value.completed && guard < 50; guard++) {
    const step = getNavalTutorialStep(value)
    if (visited.at(-1) !== step.id) visited.push(step.id)
    if (step.mode === 'read') { value = advanceNavalTutorial(value, 'CONTINUE'); continue }
    const player = value.room.players[0]
    if (step.id === 'move') {
      const ship = player.ships[0]
      value = advanceNavalTutorial(value, { type: 'SELECT_SHIP', shipId: ship.id })
      const { x, y } = getNavalTutorialStep(value).cell
      value.room.players[0].ships = value.room.players[0].ships.map(item => item.id === ship.id ? { ...item, x, y } : item)
      deploy(); value = advanceNavalTutorial(value, { type: 'MOVE_SHIP' })
    } else if (step.id === 'clear') {
      value.room.players[0].ships = []
      value = advanceNavalTutorial(value, { type: 'CLEAR_FLEET' })
    } else if (step.id === 'randomize') {
      value.room.players[0].ships = generateFleet()
      deploy(); value = advanceNavalTutorial(value, { type: 'RANDOMIZE' })
    } else if (step.id === 'rotate') value = advanceNavalTutorial(value, { type: 'ROTATE_SHIP' })
    else if (step.id === 'layout') apply({ type: 'LAYOUT_READY', ships: player.ships })
    else if (step.id.startsWith('buy-')) {
      const key = step.id === 'buy-aa' ? 'antiAir' : step.id.slice(4)
      value.room.players[0].inventory[key]++
      deploy(); value = advanceNavalTutorial(value, { type: 'PURCHASE', key })
    } else if (step.id === 'place-mine') {
      const { x, y } = step.cell
      value.room.players[0].mines = [{ x, y }]
      deploy(); value = advanceNavalTutorial(value, { type: 'PLACE_MINE' })
    } else if (step.id === 'place-aa') {
      value.room.players[0].aa = [step.cell.y]
      deploy(); value = advanceNavalTutorial(value, { type: 'PLACE_AA' })
    } else if (step.id === 'coat') {
      value.room.players[0].ships[0].stealth = true; deploy(); value = advanceNavalTutorial(value, { type: 'COAT_SHIP' })
    } else if (step.id === 'ready') apply({ type: 'READY' })
    else {
      value = advanceNavalTutorial(value, { type: 'WEAPON', weapon: step.weapon })
      const target = value.targets[step.id]
      apply({ type: 'ATTACK', weapon: step.weapon, ...(Array.isArray(target) ? { targets: target, ...target.at(-1) } : { x: target.x, y: target.y }) })
    }
    const fake = storage()
    saveNavalTutorial(value, fake)
    value = loadNavalTutorial(fake)
  }
  assert.deepEqual(visited, NAVAL_TUTORIAL_LESSONS.map(step => step.id))
  assert.equal(value.completed, true)
  assert.equal(value.room.players[0].coins, 45)
  const effects = value.room.events
  assert(effects.some(event => event.cells.some(cell => cell.result === 'hit')))
  assert(effects.some(event => event.cells.some(cell => cell.result === 'mine')))
  assert(effects.some(event => event.weapon === 'radar'))
  assert(effects.some(event => event.weapon === 'fighter' && event.row === 4 && /击落/.test(event.text)))
  assert.equal(value.room.turn, 0)
  assert.equal(value.room._aiNextAt, null)
  assert.equal(advanceNavalTutorial(value, 'CONTINUE'), value)
  assert.throws(() => prepareTutorialAction(value, { type: 'ATTACK', weapon: 'shot', x: 0, y: 0 }), /完成/)
})

test('mobile and desktop training have independent progress and backward-compatible desktop saves', () => {
  const fake = storage(), desktop = advanceNavalTutorial(createNavalTutorial(1000), 'CONTINUE')
  let mobile = createNavalTutorial(2000, { device: 'mobile' })
  mobile = advanceNavalTutorial(mobile, 'CONTINUE')
  mobile = advanceNavalTutorial(mobile, 'CONTINUE')
  assert.equal(getNavalTutorialStep(mobile).id, 'move')
  assert.equal(saveNavalTutorial(desktop, fake), true)
  assert.equal(saveNavalTutorial(mobile, fake), true)
  assert.deepEqual(loadNavalTutorial(fake), desktop)
  assert.deepEqual(loadNavalTutorial(fake, { device: 'mobile' }), mobile)
  assert.equal(navalTutorialStorageKey('mobile'), NAVAL_MOBILE_TUTORIAL_STORAGE_KEY)
  assert.equal(navalTutorialStorageKey('desktop'), NAVAL_TUTORIAL_STORAGE_KEY)
  assert.notEqual(NAVAL_MOBILE_TUTORIAL_STORAGE_KEY, NAVAL_TUTORIAL_STORAGE_KEY)
  assert.equal(fake.data.get('naval-guest-session'), 'formal-session')
  assert.equal(fake.data.get('naval-guest-history'), 'formal-history')
  const legacy = copy(desktop); delete legacy.device; delete legacy.flags.openPanel
  fake.setItem(NAVAL_TUTORIAL_STORAGE_KEY, JSON.stringify(legacy))
  assert.deepEqual(loadNavalTutorial(fake), legacy)
  fake.setItem(NAVAL_MOBILE_TUTORIAL_STORAGE_KEY, JSON.stringify(desktop))
  assert.equal(loadNavalTutorial(fake, { device: 'mobile' }).device, 'mobile')
  assert.equal(loadNavalTutorial(fake, { device: 'mobile' }).step, 0)
})

test('mobile preparation keeps the left board available while right-side tabs stay open', () => {
  let session = advanceNavalTutorial(createNavalTutorial(1000, { device: 'mobile' }), 'CONTINUE')
  assert.equal(getNavalTutorialStep(session).id, 'fleet-open')
  assert.equal(getNavalTutorialStep(session).mode, 'read')
  assert.equal(getNavalTutorialStep(session).target, 'naval-fleet-roster')
  assert.equal(session.flags.openPanel, 'fleet')
  session = advanceNavalTutorial(session, 'CONTINUE')
  assert.equal(getNavalTutorialStep(session).id, 'move')
  assert.equal(getNavalTutorialStep(session).subTarget, 'button')
  session = advanceNavalTutorial(session, { type: 'SELECT_SHIP', shipId: session.room.players[0].ships[0].id })
  assert.equal(getNavalTutorialStep(session).target, 'naval-own-board', 'persistent roster never covers or blocks the board')
  assert.equal(session.flags.openPanel, 'fleet', 'ship selection does not close the persistent list')
  const tools = advanceNavalTutorial(session, { type: 'PANEL', panel: 'tools' })
  assert.equal(getNavalTutorialStep(tools).target, 'naval-own-board', 'even the tools tab does not obscure a cell')
  const legacy = copy(session); legacy.flags.openPanel = null
  assert.equal(getNavalTutorialStep(legacy).target, 'naval-own-board')
  legacy.flags.shipSelected = false
  assert.equal(getNavalTutorialStep(legacy).target, 'naval-fleet-roster', 'legacy collapsed progress uses the persistent fleet')
  const shifted = copy(legacy); shifted.flags.openPanel = 'tools'
  assert.equal(getNavalTutorialStep(shifted).target, 'naval-mobile-fleet-toggle')
  assert.equal(getNavalTutorialStep(shifted).mode, 'action')
  const tab = copy(session); tab.step = NAVAL_MOBILE_TUTORIAL_LESSONS.findIndex(step => step.id === 'tools-open')
  assert.equal(getNavalTutorialStep(tab).target, 'naval-mobile-tools-toggle')
  const switched = advanceNavalTutorial(tab, { type: 'PANEL', panel: 'tools' })
  assert.equal(getNavalTutorialStep(switched).id, 'clear')
  assert.equal(getNavalTutorialStep(switched).target, 'naval-clear-layout')
})

test('mobile preparation explanations and combat overlay interactions are distinct', () => {
  const session = createNavalTutorial(1000, { device: 'mobile' })
  session.flags.openPanel = 'tools'
  assert.equal(getNavalTutorialStep(session).mode, 'read', 'no pointless close before reading the visible sea')
  assert.equal(getNavalTutorialStep(session).target, 'naval-own-board')
  const supply = copy(session)
  supply.step = NAVAL_MOBILE_TUTORIAL_LESSONS.findIndex(step => step.id === 'supply-open')
  supply.room.players[0].stage = 'SUPPLY'
  supply.flags.openPanel = null
  assert.equal(getNavalTutorialStep(supply).mode, 'read')
  assert.equal(getNavalTutorialStep(supply).target, 'naval-buy-fighter')
  const radar = { ...copy(session), step: NAVAL_MOBILE_TUTORIAL_LESSONS.findIndex(step => step.id === 'radar') }
  radar.room.phase = 'BATTLE'; radar.flags.openPanel = null
  assert.equal(getNavalTutorialStep(radar).target, 'naval-mobile-weapons-toggle')
  const open = advanceNavalTutorial(radar, { type: 'PANEL', panel: 'weapons' })
  assert.equal(getNavalTutorialStep(open).target, 'naval-weapon-radar')
  const selected = advanceNavalTutorial(open, { type: 'WEAPON', weapon: 'radar' })
  assert.equal(getNavalTutorialStep(selected).target, 'naval-mobile-weapons-toggle', 'combat attack menu still needs closing before aiming')
  const aiming = advanceNavalTutorial(selected, { type: 'PANEL', panel: null })
  assert.equal(getNavalTutorialStep(aiming).target, 'naval-enemy-board')
  assert.deepEqual(getNavalTutorialStep(aiming).cell, aiming.targets.radar)
})

test('all mobile lessons finish with persistent preparation tabs and collapsed combat weapons', () => {
  let session = createNavalTutorial(1000, { device: 'mobile' }), now = 5000
  const fake = storage(), visited = [], opened = []
  function panel(value) { session = advanceNavalTutorial(session, { type: 'PANEL', panel: value }) }
  function apply(action) {
    now += 1000
    const previousStage = session.room.players[0].stage, previousPhase = session.room.phase
    const prepared = prepareTutorialAction(session, action, now)
    session.room = localAction(prepared.room, prepared.action, now)
    if (session.room.players[0].stage !== previousStage || session.room.phase !== previousPhase) panel(session.room.phase === 'BATTLE' ? null : session.room.players[0].stage === 'SUPPLY' ? 'supply' : 'fleet')
    session = advanceNavalTutorial(session, { type: 'ACTION', action })
  }
  function deploy() { apply({ type: 'DEPLOY', ships: session.room.players[0].ships, inventory: session.room.players[0].inventory, mines: session.room.players[0].mines, aa: session.room.players[0].aa, coatedShips: session.room.players[0].ships.filter(ship => ship.stealth).map(ship => ship.id) }) }
  for (let guard = 0; !session.completed && guard < 100; guard++) {
    const step = getNavalTutorialStep(session)
    if (visited.at(-1) !== step.id) visited.push(step.id)
    if (step.target.startsWith('naval-mobile-')) {
      const next = Object.hasOwn(step, 'pendingPanel') ? step.pendingPanel : step.panel
      assert(['fleet', 'tools', 'supply', 'weapons', null].includes(next))
      if (next) opened.push(next)
      panel(next)
    } else if (step.mode === 'read') session = advanceNavalTutorial(session, 'CONTINUE')
    else if (step.id === 'select-shot') { session = advanceNavalTutorial(session, { type: 'WEAPON', weapon: 'shot' }); panel(null) }
    else if (step.id === 'move') {
      if (!session.flags.shipSelected) session = advanceNavalTutorial(session, { type: 'SELECT_SHIP', shipId: session.room.players[0].ships[0].id })
      else {
        const { x, y } = step.cell
        session.room.players[0].ships = session.room.players[0].ships.map(ship => ship.id === session.flags.selectedShip ? { ...ship, x, y } : ship)
        deploy(); session = advanceNavalTutorial(session, { type: 'MOVE_SHIP' })
      }
    } else if (step.id === 'clear') { session.room.players[0].ships = []; session = advanceNavalTutorial(session, { type: 'CLEAR_FLEET' }) }
    else if (step.id === 'randomize') { session.room.players[0].ships = generateFleet(); deploy(); session = advanceNavalTutorial(session, { type: 'RANDOMIZE' }) }
    else if (step.id === 'rotate') session = advanceNavalTutorial(session, { type: 'ROTATE_SHIP' })
    else if (step.id === 'layout') apply({ type: 'LAYOUT_READY', ships: session.room.players[0].ships })
    else if (step.id.startsWith('buy-')) {
      const key = step.id === 'buy-aa' ? 'antiAir' : step.id.slice(4)
      session.room.players[0].inventory[key]++; deploy(); session = advanceNavalTutorial(session, { type: 'PURCHASE', key })
      assert.equal(session.flags.openPanel, 'supply', 'purchases retain the fixed shop')
    } else if (step.id === 'place-mine') { session.room.players[0].mines = [{ x: step.cell.x, y: step.cell.y }]; deploy(); session = advanceNavalTutorial(session, { type: 'PLACE_MINE' }) }
    else if (step.id === 'place-aa') { session.room.players[0].aa = [step.cell.y]; deploy(); session = advanceNavalTutorial(session, { type: 'PLACE_AA' }) }
    else if (step.id === 'coat') { session.room.players[0].ships[0].stealth = true; deploy(); session = advanceNavalTutorial(session, { type: 'COAT_SHIP' }) }
    else if (step.id === 'ready') apply({ type: 'READY' })
    else if (step.target.startsWith('naval-weapon-')) { session = advanceNavalTutorial(session, { type: 'WEAPON', weapon: step.weapon }); panel(null) }
    else { const target = session.targets[step.id]; apply({ type: 'ATTACK', weapon: step.weapon, ...(Array.isArray(target) ? { targets: target, ...target.at(-1) } : { x: target.x, y: target.y }) }) }
    saveNavalTutorial(session, fake); session = loadNavalTutorial(fake, { device: 'mobile' })
  }
  assert.equal(session.completed, true)
  assert.deepEqual(visited, NAVAL_MOBILE_TUTORIAL_LESSONS.map(step => step.id))
  assert(opened.includes('tools') && opened.includes('weapons'))
  assert.equal(session.room.players[0].coins, 45)
  assert.equal(session.room.turn, 0)
  assert.equal(fake.getItem(NAVAL_TUTORIAL_STORAGE_KEY), null)
})
