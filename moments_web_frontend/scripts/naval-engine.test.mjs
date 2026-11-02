import test from 'node:test'
import assert from 'node:assert/strict'
import { FLEET, ITEMS, createLocalGame, generateFleet, legalPlacement, localAction, migrateLocalGame, normalizeSavedFleet, shipCells, tickLocalGame, validateFleet, validatePartialFleet, completeFleet } from '../src/games/navalEngine.js'

const NOW = 1_000_000
const clone = value => JSON.parse(JSON.stringify(value))
const key = ({ x, y }) => `${x},${y}`
const cells = Array.from({ length: 144 }, (_, i) => ({ x: i % 12, y: Math.floor(i / 12) }))
const emptyInventory = () => Object.fromEntries(Object.keys(ITEMS).map(name => [name, 0]))

function battle() {
  const room = localAction(supply(), { type: 'READY' }, NOW)
  room.players[1].aa = []
  return room
}

function supply(room = createLocalGame(NOW)) {
  return localAction(room, { type: 'LAYOUT_READY' }, NOW)
}

function water(player) {
  const occupied = new Set(player.ships.flatMap(shipCells).map(key))
  const mines = new Set(player.mines.map(key))
  return cells.find(cell => !occupied.has(key(cell)) && !mines.has(key(cell)))
}

function deploy(room, inventory = {}, overrides = {}) {
  if (room.players[0].stage === 'PLACEMENT') room = supply(room)
  return localAction(room, { type: 'DEPLOY', ships: room.players[0].ships, inventory: { ...emptyInventory(), ...inventory }, mines: [], aa: [], ...overrides }, NOW)
}

test('fleet contains all 10 ships and exactly 20 occupied cells, without 2 by 3 ships', () => {
  assert.equal(FLEET.length, 10)
  assert.equal(FLEET.reduce((sum, ship) => sum + ship.w * ship.h, 0), 20)
  assert.deepEqual(Object.values(ITEMS).map(item => [item.price, item.max]), [[30, 3], [30, 3], [25, 3], [25, 3], [40, 2], [30, 3], [10, 5], [5, 12]])
})

test('random formations always fit and keep an empty cell between every pair', () => {
  for (let i = 0; i < 150; i++) {
    const fleet = generateFleet()
    assert.equal(validateFleet(fleet), '')
    assert.equal(new Set(fleet.flatMap(shipCells).map(key)).size, 20)
    assert.ok(fleet.every(ship => shipCells(ship).every(cell => cell.x >= 0 && cell.x < 12 && cell.y >= 0 && cell.y < 12)))
  }
  const serverFleet = generateFleet().map((ship, i) => ({ ...ship, id: `ship-${i}` }))
  assert.equal(validateFleet(serverFleet), '', 'server-generated formations may use different unique ids')
})

test('placement rejects adjacent, diagonal, overlapping, out-of-bounds and malformed ships', () => {
  const ships = [{ id: 'a', x: 2, y: 2, w: 1, h: 1 }]
  for (const [x, y] of [[1, 1], [2, 2], [2, 3], [3, 3], [3, 2]]) assert.equal(legalPlacement(ships, { id: 'b', x, y, w: 1, h: 1 }), false)
  assert.equal(legalPlacement(ships, { id: 'b', x: 4, y: 2, w: 1, h: 1 }), true)
  assert.equal(legalPlacement(ships, { id: 'a', x: 2, y: 2, w: 1, h: 1 }), true)
  assert.equal(legalPlacement([], { id: 'a', x: 11, y: 11, w: 2, h: 3 }), false)
  assert.equal(legalPlacement([], { id: 'a', x: .5, y: 0, w: 1, h: 1 }), false)
  assert.equal(legalPlacement([], { id: 'a', x: 0, y: 0, w: -1, h: 1 }), false)
  assert.notEqual(validateFleet([]), '')
  const duplicate = generateFleet()
  duplicate[1] = duplicate[0]
  assert.notEqual(validateFleet(duplicate), '')
  const badSize = generateFleet()
  badSize[0].w = 2
  assert.notEqual(validateFleet(badSize), '')
})

test('new local games have a five-minute deployment phase and reset coins', () => {
  const room = createLocalGame(NOW)
  assert.equal(room.phase, 'PLACEMENT')
  assert.equal(room.deadline, NOW + 300_000)
  assert.equal(room.players[0].coins, 300)
  assert.equal(room.players[0].ready, false)
  assert.equal(room.players[1].ready, true)
  assert.equal(validateFleet(room.players[1].ships), '')
})

test('purchases enforce exact budget, item limits, integer counts and known item keys', () => {
  const room = createLocalGame(NOW)
  const next = deploy(room, { fighter: 3, bomber: 2, radar: 2, mine: 12, antiAir: 0, torpedo: 0 })
  assert.equal(next.players[0].coins, 20)
  assert.equal(room.players[0].coins, 300, 'actions must not mutate the input')
  assert.throws(() => deploy(room, { antiAir: 3, fighter: 3, bomber: 3, torpedo: 3, radar: 2, mine: 12 }), /金币不足/)
  assert.throws(() => deploy(room, { mine: 13 }), /数量/)
  assert.throws(() => deploy(room, { radar: -1 }), /数量/)
  assert.throws(() => deploy(room, { fighter: .5 }), /数量/)
  assert.throws(() => deploy(room, { radar: '1' }), /数量/)
  assert.throws(() => deploy(room, { unknown: 1 }), /未知/)
})

test('mines cannot overlap a ship, repeat, exceed the purchase or leave the board', () => {
  const room = createLocalGame(NOW)
  const target = water(room.players[0])
  const valid = deploy(room, { mine: 1 }, { mines: [target] })
  assert.deepEqual(valid.players[0].mines, [target])
  assert.throws(() => deploy(room, { mine: 1 }, { mines: [shipCells(room.players[0].ships[0])[0]] }), /水雷/)
  assert.throws(() => deploy(room, { mine: 2 }, { mines: [target, target] }), /水雷/)
  assert.throws(() => deploy(room, {}, { mines: [target] }), /数量/)
  assert.throws(() => deploy(room, { mine: 1 }, { mines: [{ x: -1, y: 0 }] }), /水雷/)
})

test('AA rows are bounded, distinct and limited by the purchase', () => {
  const room = createLocalGame(NOW)
  assert.deepEqual(deploy(room, { antiAir: 2 }, { aa: [0, 11] }).players[0].aa, [0, 11])
  assert.throws(() => deploy(room, {}, { aa: [0] }), /数量/)
  assert.throws(() => deploy(room, { antiAir: 2 }, { aa: [0, 0] }), /防空炮/)
  assert.throws(() => deploy(room, { antiAir: 1 }, { aa: [12] }), /防空炮/)
})

test('layout, supply and battle are separate stages and both ready starts battle early', () => {
  const room = createLocalGame(NOW)
  assert.throws(() => localAction(room, { type: 'READY' }, NOW), /完成布阵/)
  assert.throws(() => localAction(room, { type: 'DEPLOY', ships: room.players[0].ships, inventory: { fighter: 1 } }, NOW), /完成布阵/)
  assert.throws(() => localAction(room, { type: 'DEPLOY', ships: room.players[0].ships, ready: true }, NOW), /完成布阵/)
  const shopping = localAction(room, { type: 'LAYOUT_READY' }, NOW + 100)
  assert.equal(shopping.phase, 'SUPPLY')
  assert.equal(shopping.players[0].stage, 'SUPPLY')
  assert.equal(shopping.deadline, NOW + 180_100)
  const started = localAction(shopping, { type: 'READY' }, NOW + 300)
  assert.equal(started.phase, 'BATTLE')
  assert.equal(started.deadline, NOW + 30_300)
  assert.equal(started.turn, 0)
  const deployedReady = deploy(room, {}, { ready: true })
  assert.equal(deployedReady.phase, 'BATTLE')
  assert.throws(() => deploy(started), /不能调整/)
  assert.throws(() => localAction(started, { type: 'READY' }, NOW), /完成布阵/)
  assert.equal(tickLocalGame(room, NOW + 299_999), room)
  const expired = tickLocalGame(room, NOW + 300_000)
  assert.equal(expired.phase, 'SUPPLY')
  assert.equal(expired.deadline, NOW + 480_000)
  assert.equal(expired.players[0].ready, false)
  const battleTimeout = tickLocalGame(expired, NOW + 480_000)
  assert.equal(battleTimeout.phase, 'BATTLE')
  assert.ok(battleTimeout.players.every(player => player.ready && player.stage === 'READY'))
})

test('supply locks geometry and only allows purchases and defensive placement', () => {
  const room = supply()
  const changed = clone(room.players[0].ships)
  changed[0].x = (changed[0].x + 1) % 12
  assert.throws(() => localAction(room, { type: 'DEPLOY', ships: changed }, NOW), /阵型已锁定/)
  assert.throws(() => localAction(room, { type: 'LAYOUT_READY', ships: generateFleet() }, NOW), /不能修改/)
  assert.equal(deploy(room, { fighter: 1 }).players[0].inventory.fighter, 1)
})

test('voluntary ready requires deploying every purchased defense; timeout completes them', () => {
  const room = deploy(createLocalGame(NOW), { mine: 12, antiAir: 3 })
  assert.throws(() => localAction(room, { type: 'READY' }, NOW), /部署全部/)
  assert.throws(() => deploy(room, { mine: 1 }, { ready: true }), /部署全部/)
  const expired = tickLocalGame(room, NOW + 300_000)
  assert.equal(expired.phase, 'BATTLE')
  assert.equal(expired.players[0].mines.length, 12)
  assert.equal(expired.players[0].aa.length, 3)
  assert.equal(new Set(expired.players[0].mines.map(key)).size, 12)
  assert.equal(new Set(expired.players[0].aa).size, 3)
  const occupied = new Set(expired.players[0].ships.flatMap(shipCells).map(key))
  assert.ok(expired.players[0].mines.every(mine => !occupied.has(key(mine))))
})

test('a hit grants another 30 seconds; a miss hands over the turn', () => {
  const room = battle()
  const target = shipCells(room.players[1].ships[0])[0]
  const hit = localAction(room, { type: 'ATTACK', weapon: 'shot', ...target }, NOW + 500)
  assert.equal(hit.turn, 0)
  assert.equal(hit.deadline, NOW + 30_500)
  assert.equal(hit.players[1].shots[0].result, 'hit')
  assert.deepEqual(hit.players[1].ships[0].hits, [key(target)])
  assert.equal(room.players[1].shots.length, 0)
  assert.throws(() => localAction(hit, { type: 'ATTACK', weapon: 'shot', ...target }, NOW + 700), /攻击过/)
  const miss = localAction(hit, { type: 'ATTACK', weapon: 'shot', ...water(hit.players[1]) }, NOW + 1000)
  assert.equal(miss.turn, 1)
  assert.equal(miss._aiNextAt, NOW + 2200)
  assert.throws(() => localAction(miss, { type: 'ATTACK', weapon: 'shot', x: 0, y: 0 }, NOW + 1200), /轮到/)
})

test('expired turns swap and the AI attacks only after its delay', () => {
  const room = battle()
  const next = tickLocalGame(room, NOW + 30_000)
  assert.equal(next.turn, 1)
  assert.equal(next.players[0].shots.length, 0)
  assert.equal(next.deadline, NOW + 60_000)
  assert.equal(tickLocalGame(next, NOW + 31_199), next)
  const afterAI = tickLocalGame(next, NOW + 31_200)
  assert.ok(afterAI.events.length > next.events.length)
  assert.ok(afterAI.players[0].shots.length || afterAI.players[1].inventory.radar < next.players[1].inventory.radar || afterAI.players[0].aa.length < next.players[0].aa.length)
})

test('fighter selects two rows but strafes exactly one random row of eight cells', () => {
  const room = battle()
  room.players[0].inventory.fighter = 1
  const next = localAction(room, { type: 'ATTACK', weapon: 'fighter', x: 4, y: 10 }, NOW)
  assert.equal(next.players[1].shots.length, 8)
  assert.equal(new Set(next.players[1].shots.map(cell=>cell.y)).size,1)
  assert.ok(next.players[1].shots.every(cell => cell.x >= 4 && cell.x <= 11 && cell.y >= 10 && cell.y <= 11))
  assert.equal(next.players[0].inventory.fighter, 0)
  assert.throws(() => localAction(room, { type: 'ATTACK', weapon: 'fighter', x: 5, y: 0 }, NOW), /超出/)
  assert.throws(() => localAction(battle(), { type: 'ATTACK', weapon: 'fighter', x: 0, y: 0 }, NOW), /用完/)
})

test('bomber attacks 5-7 distinct cells within the selected 3 by 3 area', () => {
  for (let i = 0; i < 30; i++) {
    const room = battle()
    room.players[0].inventory.bomber = 1
    const next = localAction(room, { type: 'ATTACK', weapon: 'bomber', x: 9, y: 9 }, NOW)
    const shots = next.players[1].shots
    assert.ok(shots.length >= 5 && shots.length <= 7)
    assert.equal(new Set(shots.map(key)).size, shots.length)
    assert.ok(shots.every(cell => cell.x >= 9 && cell.y >= 9))
  }
})

test('one AA gun consumes an aircraft, protects the whole area, and is expended', () => {
  for (const weapon of ['fighter', 'bomber', 'torpedo']) {
    const room = battle()
    room.players[0].inventory[weapon] = 1
    room.players[1].aa = [1]
    room.players[1].inventory.antiAir = 1
    const next = localAction(room, { type: 'ATTACK', weapon, x: 0, y: weapon === 'torpedo' ? 1 : 0 }, NOW)
    assert.equal(next.players[1].shots.length, 0)
    assert.equal(next.players[1].aa.length, 0)
    assert.equal(next.players[1].inventory.antiAir, 0)
    assert.equal(next.players[0].inventory[weapon], 0)
    assert.equal(next.turn, 1)
    assert.match(next.events.at(-1).text, /击落/)
  }
  const room = battle()
  room.players[1].aa = [1]
  const target = shipCells(room.players[1].ships[0])[0]
  const next = localAction(room, { type: 'ATTACK', weapon: 'shot', ...target }, NOW)
  assert.deepEqual(next.players[1].aa, [1], 'ordinary shells cannot be intercepted')
})

test('torpedo starts at the selected drop cell and stops on the first ship or mine', () => {
  const room = battle()
  room.players[0].inventory.torpedo = 1
  room.players[1].mines = []
  const y = room.players[1].ships[0].y
  const firstShipX = Math.min(...room.players[1].ships.flatMap(shipCells).filter(cell => cell.y === y).map(cell => cell.x))
  const next = localAction(room, { type: 'ATTACK', weapon: 'torpedo', x: firstShipX, y }, NOW)
  assert.equal(next.players[1].shots.length, 1)
  assert.equal(next.players[1].shots[0].x, firstShipX)
  assert.equal(next.players[1].shots.at(-1).result, 'hit')
  const mineRoom = battle()
  mineRoom.players[0].inventory.torpedo = 1
  const empty = water(mineRoom.players[1])
  mineRoom.players[1].mines = [empty]
  const result = localAction(mineRoom, { type: 'ATTACK', weapon: 'torpedo', x: 0, y: empty.y }, NOW)
  const encountered = result.players[1].shots.at(-1)
  const earliestShip = Math.min(...mineRoom.players[1].ships.flatMap(shipCells).filter(cell => cell.y === empty.y).map(cell => cell.x), Infinity)
  assert.equal(encountered.x, Math.min(empty.x, earliestShip))
  assert.equal(encountered.result, empty.x < earliestShip ? 'mine' : 'hit')
  const edgeRoom = battle(); edgeRoom.players[0].inventory.torpedo = 1
  edgeRoom.players[1].aa = []; edgeRoom.players[1].mines = []
  const edge = localAction(edgeRoom, { type: 'ATTACK', weapon: 'torpedo', x: 11, y: 11 }, NOW)
  assert.deepEqual(edge.players[1].shots.map(({ x,y }) => ({ x,y })), [{ x: 11,y: 11 }])
})

test('a mine explosion never grants a hit bonus or remains on the board', () => {
  const room = battle()
  const target = water(room.players[1])
  room.players[1].mines = [target]
  room.players[1].inventory.mine = 1
  const next = localAction(room, { type: 'ATTACK', weapon: 'shot', ...target }, NOW)
  assert.equal(next.turn, 1)
  assert.equal(next.players[1].shots[0].result, 'mine')
  assert.equal(next.players[1].mines.length, 0)
  assert.equal(next.players[1].inventory.mine, 0)
})

test('radar reveals presence without doing damage and consumes the turn', () => {
  const room = battle()
  room.players[0].inventory.radar = 2
  const target = shipCells(room.players[1].ships[0])[0]
  const next = localAction(room, { type: 'ATTACK', weapon: 'radar', x: Math.min(9, target.x), y: Math.min(9, target.y) }, NOW)
  assert.equal(next.radar.length, 9)
  assert.ok(next.radar.some(cell => cell.occupied))
  assert.equal(next.players[1].shots.length, 0)
  assert.equal(next.players[0].inventory.radar, 1)
  assert.equal(next.turn, 1)
  assert.deepEqual(next.events.at(-1).cells, [], 'scan results are not published in battle events')
})

test('destroying every ship ends the battle and no further attacks are allowed', () => {
  const room = battle()
  const targets = room.players[1].ships.flatMap(shipCells)
  let next = room
  for (let i = 0; i < targets.length; i++) next = localAction(next, { type: 'ATTACK', weapon: 'shot', ...targets[i] }, NOW + i * 100)
  assert.equal(next.phase, 'FINISHED')
  assert.equal(next.winner, 0)
  assert.equal(next.deadline, 0)
  assert.equal(next.players[1].shots.length, 20)
  assert.throws(() => localAction(next, { type: 'ATTACK', weapon: 'shot', x: 0, y: 0 }, NOW + 4000), /轮到/)
  assert.equal(tickLocalGame(next, NOW + 4000), next)
})

test('AI decisions do not depend on unrevealed enemy ship positions', () => {
  const first = battle()
  first.turn = 1
  first._aiNextAt = NOW
  first.players[1].inventory = emptyInventory()
  const second = clone(first)
  second.players[0].ships = generateFleet()
  const originalRandom = Math.random
  let a, b
  try {
    Math.random = () => .45
    a = tickLocalGame(first, NOW)
    b = tickLocalGame(second, NOW)
  } finally { Math.random = originalRandom }
  assert.deepEqual(a.players[0].shots.map(({ x, y }) => ({ x, y })), b.players[0].shots.map(({ x, y }) => ({ x, y })))
})

test('chat is trimmed, bounded, local, and survives JSON save/restore', () => {
  const room = createLocalGame(NOW)
  const next = localAction(room, { type: 'CHAT', text: '  准备出击  ' }, NOW)
  assert.equal(next.messages[0].text, '准备出击')
  assert.equal(next.messages[0].player, 0)
  assert.equal(next.messages[0].at, NOW)
  assert.equal(room.messages.length, 0)
  assert.deepEqual(JSON.parse(JSON.stringify(next)), next)
  assert.throws(() => localAction(room, { type: 'CHAT', text: ' ' }, NOW), /消息长度/)
  assert.throws(() => localAction(room, { type: 'CHAT', text: 'a'.repeat(301) }, NOW), /消息长度/)
  const left = localAction(next, { type: 'LEAVE' }, NOW)
  assert.equal(left.phase, 'FINISHED')
  assert.equal(left.winner, null)
})

test('full games finish, survive JSON reloads, and never attack the same cell twice', () => {
  for (let game = 0; game < 12; game++) {
    let room = battle()
    let now = NOW
    let actions = 0
    while (room.phase !== 'FINISHED' && actions++ < 400) {
      now += 4500
      if (room.turn === 0) {
        const attacked = new Set(room.players[1].shots.map(key))
        const target = cells.find(cell => !attacked.has(key(cell)))
        assert.ok(target, 'a live game always has an attackable target')
        room = localAction(room, { type: 'ATTACK', weapon: 'shot', ...target }, now)
      } else room = tickLocalGame(room, now)
      room = clone(room)
    }
    assert.equal(room.phase, 'FINISHED')
    assert.ok(room.winner === 0 || room.winner === 1)
    for (const player of room.players) assert.equal(new Set(player.shots.map(key)).size, player.shots.length)
    const loser = room.players[1 - room.winner]
    assert.ok(loser.ships.every(ship => ship.hits.length === ship.w * ship.h))
  }
})

test('legacy saved games and formations lose carriers without losing valid ship placement', () => {
  const positions = [[0,10,1,1],[3,10,1,1],[9,10,1,1],[11,2,1,1],[6,5,1,2],[9,6,1,2],[6,9,2,1],[0,5,1,3],[3,5,1,3],[8,0,1,4],[0,0,2,3],[4,0,2,3]]
  const legacy = createLocalGame(NOW)
  delete legacy.rulesVersion
  legacy.players.forEach(player => {
    delete player.stage
    player.ships = positions.map(([x,y,w,h],i) => ({ id: `old-${i}`, x,y,w,h,hits:[] }))
  })
  legacy.players[0].inventory.fighter = 1
  legacy.players[0].coins = 270
  legacy.players[1].radar = [{ x: 0, y: 0, occupied: true }]
  legacy.players[0].shots = [{ x: 0, y: 0, result: 'hit' }]
  assert.equal(normalizeSavedFleet(legacy.players[0].ships).length, 10)
  const migrated = migrateLocalGame(legacy, NOW + 100)
  assert.equal(migrated.rulesVersion, 2)
  assert.equal(migrated.phase, 'SUPPLY')
  assert.equal(migrated.players[0].stage, 'SUPPLY')
  assert.equal(migrated.players[0].ships.length, 10)
  assert.equal(migrated.players[0].inventory.fighter, 1)
  assert.equal(migrated.players[0].shots[0].result, 'miss')
  assert.equal(migrated.players[1].radar[0].occupied, false)
  assert.equal(legacy.players[0].ships.length, 12, 'migration must not mutate old saves')
  assert.equal(tickLocalGame(legacy, NOW + 100).rulesVersion, 2)
})

test('clearing, partial placement and reload preserve legal ships without allowing early ready', () => {
  const initial = createLocalGame(NOW)
  const empty = localAction(initial, { type: 'DEPLOY', ships: [] }, NOW)
  assert.equal(empty.players[0].ships.length, 0)
  assert.equal(validatePartialFleet([]), '')
  assert.throws(() => localAction(empty, { type: 'LAYOUT_READY' }, NOW), /全部 10/)
  const placed = [{ id: 'custom-boat', x: 5, y: 5, w: 1, h: 1, hits: [], stealth: false }]
  const partial = localAction(empty, { type: 'DEPLOY', ships: placed }, NOW)
  assert.deepEqual(clone(partial).players[0].ships, placed)
  const complete = completeFleet(placed)
  assert.equal(validateFleet(complete), '')
  assert.deepEqual(complete.find(ship => ship.id === 'custom-boat'), placed[0])
  const automatic = tickLocalGame(partial, NOW + 300_000)
  assert.equal(automatic.phase, 'SUPPLY')
  assert.equal(validateFleet(automatic.players[0].ships), '')
  assert.deepEqual(automatic.players[0].ships.find(ship => ship.id === 'custom-boat'), placed[0])
  const excessive = Array.from({ length: 5 }, (_, i) => ({ id: `single-${i}`, x: i * 2, y: 0, w: 1, h: 1 }))
  assert.match(validatePartialFleet(excessive), /数量/)
})

test('AI avoids the public one-cell halo of sunk ships for shots and all area weapons', () => {
  for (let i = 0; i < 120; i++) {
    const room = battle()
    const target = room.players[0]
    const ship = target.ships.find(s => s.w * s.h === 1)
    ship.hits = shipCells(ship).map(key)
    target.shots = shipCells(ship).map(cell => ({ ...cell, result: 'hit' }))
    const excluded = new Set()
    for (let y = ship.y - 1; y <= ship.y + ship.h; y++) for (let x = ship.x - 1; x <= ship.x + ship.w; x++) excluded.add(`${x},${y}`)
    room.turn = 1; room._aiNextAt = NOW
    const after = tickLocalGame(room, NOW)
    const action = after.events.filter(event => event.player === 1).at(-1)
    assert(action, 'AI should have a legal non-halo action')
    assert(action.cells.every(cell => !excluded.has(key(cell))), `unsafe ${action.weapon}`)
    if (action.weapon === 'radar') {
      for (let dy = 0; dy < 3; dy++) for (let dx = 0; dx < 3; dx++) assert(!excluded.has(`${action.x + dx},${action.y + dy}`))
    }
  }
})

test('leaving AI preparation does not create a defeat, leaving battle does', () => {
  const initial = createLocalGame(NOW)
  const placed = localAction(initial, { type: 'LAYOUT_READY' }, NOW)
  for (const room of [initial, placed]) {
    const cancelled = localAction(room, { type: 'LEAVE' }, NOW)
    assert.equal(cancelled.winner, null)
    assert.equal(cancelled.finishReason, 'CANCELLED')
    assert.equal(cancelled.phase, 'FINISHED')
  }
  const finished = localAction(battle(), { type: 'LEAVE' }, NOW)
  assert.equal(finished.winner, 1)
  assert.equal(finished.finishReason, 'PLAYER_LEFT')
})
