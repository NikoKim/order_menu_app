import assert from 'node:assert/strict'
import test from 'node:test'
import { chooseNavalAiAction, navalAiHunt, navalAiKnowledge, navalAiMines, navalAiWeaponOptions } from '../src/games/navalAi.js'

const ship = (length, x = 0, y = 0, sunk = false) => ({ x, y, w: length, h: 1, hits: sunk ? Array.from({ length }, (_, i) => `${x + i},${y}`) : [] })
const target = (unknown, ships = []) => {
  const keys = new Set(unknown.map(p => `${p.x},${p.y}`))
  return { ships, shots: Array.from({ length: 144 }, (_, i) => ({ x: i % 12, y: Math.floor(i / 12), result: 'miss' })).filter(p => !keys.has(`${p.x},${p.y}`)) }
}
test('hunt uses largest remaining length and central cells, replanning after misses', () => {
  const defender = target([{ x: 2, y: 5 }, { x: 3, y: 5 }, { x: 4, y: 5 }, { x: 5, y: 5 }, { x: 9, y: 9 }])
  assert.deepEqual(navalAiHunt(navalAiKnowledge(defender), () => .5), { x: 4, y: 5 })
  defender.shots.push({ x: 4, y: 5, result: 'miss' })
  assert.notDeepEqual(navalAiHunt(navalAiKnowledge(defender), () => .5), { x: 4, y: 5 })
  defender.ships.push(ship(4, 0, 0, true))
  const three = target([{ x: 6, y: 6 }, { x: 6, y: 7 }, { x: 6, y: 8 }], defender.ships)
  assert.deepEqual(navalAiHunt(navalAiKnowledge(three), () => .5), { x: 6, y: 7 })
})
test('2-cell and 1-cell searches follow only the public sunk fleet, never hidden hulls', () => {
  const sunk = [ship(4, 0, 0, true), ship(3, 0, 2, true), ship(3, 0, 4, true)]
  const defender = target([{ x: 9, y: 8 }, { x: 10, y: 8 }], sunk)
  assert.deepEqual(navalAiKnowledge(defender).remaining, [2, 2, 2, 1, 1, 1, 1])
  const hunt = navalAiHunt(navalAiKnowledge(defender), () => .5)
  assert.equal(hunt.y, 8)
  defender.ships.push(ship(2, 0, 6, true), ship(2, 0, 8, true), ship(2, 0, 10, true))
  assert.deepEqual(navalAiKnowledge(defender).remaining, [1, 1, 1, 1])
})
test('confirmed hit axis wins over hunting, and hidden positions do not influence a decision', () => {
  const room = { players: [{ ships: [ship(4, 2, 4)], shots: [{ x: 2, y: 4, result: 'hit' }, { x: 3, y: 4, result: 'hit' }] }, { inventory: {}, radar: [] }], events: [] }
  const action = chooseNavalAiAction(room, () => .5)
  assert.equal(action.weapon, 'shot'); assert.equal(action.y, 4); assert([1, 4].includes(action.x))
  room.players[0].ships = [ship(4, 6, 8)]
  assert.deepEqual(chooseNavalAiAction(room, () => .5), action)
})
test('each area weapon chooses a maximum effective coverage, excluding sunk halos and visited cells', () => {
  const defender = { ships: [ship(4, 0, 0, true)], shots: [{ x: 8, y: 8, result: 'miss' }] }
  const knowledge = navalAiKnowledge(defender)
  const options = navalAiWeaponOptions({ inventory: { fighter: 1, bomber: 1, torpedo: 1, radar: 1 } }, knowledge)
  for (const [weapon, score] of [['fighter', 8], ['bomber', 9], ['torpedo', 12], ['radar', 9]]) {
    const selected = options.filter(o => o.weapon === weapon)
    assert(selected.length); assert(selected.every(o => o.score === score))
  }
})
test('AI mine screens are legal, varied, and statistically favor front lines or 1-5 cells ahead of hulls', () => {
  const ships = [ship(4, 6, 6)]
  let front = 0, total = 0, seed = 17
  const random = () => ((seed = (seed * 1664525 + 1013904223) >>> 0) / 4294967296)
  for (let i = 0; i < 100; i++) {
    const mines = navalAiMines(ships, 6, random)
    assert.equal(new Set(mines.map(p => `${p.x},${p.y}`)).size, 6)
    for (const p of mines) { assert(!(p.y === 6 && p.x >= 6 && p.x < 10)); total++; if (p.x < 3 || p.y === 6 && p.x >= 1 && p.x <= 5) front++ }
  }
  assert(front / total > .55)
})
