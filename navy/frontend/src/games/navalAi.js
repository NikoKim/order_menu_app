const SIZE = 12
const key = p => `${p.x},${p.y}`
const cells = ship => Array.from({ length: ship.w * ship.h }, (_, i) => ({ x: ship.x + i % ship.w, y: ship.y + Math.floor(i / ship.w) }))
const inside = p => p.x >= 0 && p.y >= 0 && p.x < SIZE && p.y < SIZE
const pick = (items, random) => items[Math.min(items.length - 1, Math.floor(random() * items.length))]

// Deliberately consume only public shot results and sunk hulls, never hidden positions.
export function navalAiKnowledge(defender) {
  const sunk = defender.ships.filter(s => s.hits?.length === s.w * s.h)
  const excluded = new Set()
  for (const ship of sunk) for (const p of cells(ship)) for (let dy = -1; dy <= 1; dy++) for (let dx = -1; dx <= 1; dx++) excluded.add(`${p.x + dx},${p.y + dy}`)
  const attacked = new Set(defender.shots.map(key))
  const blockers = new Set(defender.shots.filter(p => p.result === 'hit').map(key))
  const valid = p => inside(p) && !attacked.has(key(p)) && !excluded.has(key(p))
  const unknown = Array.from({ length: 144 }, (_, i) => ({ x: i % SIZE, y: Math.floor(i / SIZE) })).filter(valid)
  const hitCells = defender.shots.filter(p => p.result === 'hit' && !excluded.has(key(p)))
  const hits = new Set(hitCells.map(key)), ends = []
  for (const p of hitCells) for (const [dx, dy] of [[1, 0], [0, 1]]) if (hits.has(`${p.x + dx},${p.y + dy}`)) {
    for (const sign of [-1, 1]) { let q = p; do { q = { x: q.x + dx * sign, y: q.y + dy * sign } } while (hits.has(key(q))); if (valid(q)) ends.push(q) }
  }
  const adjacent = hitCells.flatMap(p => [[1, 0], [-1, 0], [0, 1], [0, -1]].map(([dx, dy]) => ({ x: p.x + dx, y: p.y + dy }))).filter(valid)
  const remaining = [4, 3, 3, 2, 2, 2, 1, 1, 1, 1]
  for (const ship of sunk) { const i = remaining.indexOf(ship.w * ship.h); if (i >= 0) remaining.splice(i, 1) }
  return { valid, unknown, ends, adjacent, remaining, excluded, blockers }
}

export function navalAiHunt(knowledge, random = Math.random) {
  const { valid, unknown, remaining } = knowledge
  const edgeHunt = random() < .18
  for (const length of [...new Set(remaining)]) {
    const segments = []
    for (let y = 0; y < SIZE; y++) for (let x = 0; x < SIZE; x++) for (const [dx, dy] of length === 1 ? [[1, 0]] : [[1, 0], [0, 1]]) {
      const line = Array.from({ length }, (_, i) => ({ x: x + i * dx, y: y + i * dy }))
      if (line.every(valid)) segments.push(line)
    }
    if (!segments.length) continue
    const edges = edgeHunt ? segments.filter(line => line.some(p => p.x === 0 || p.y === 0 || p.x === 11 || p.y === 11)) : []
    const line = pick(edges.length ? edges : segments, random)
    const middle = length % 2 ? [line[Math.floor(length / 2)]] : [line[length / 2 - 1], line[length / 2]]
    return pick(random() < .9 ? middle : line, random)
  }
  return pick(unknown, random)
}

export function navalAiWeaponOptions(attacker, knowledge) {
  const options = []
  for (const weapon of ['fighter', 'bomber', 'torpedo', 'radar', 'submarine']) {
    if (!(attacker.inventory[weapon] > 0)) continue
    const width = weapon === 'fighter' ? 8 : ['bomber', 'radar', 'submarine'].includes(weapon) ? 3 : 1
    const height = weapon === 'fighter' ? 2 : ['bomber', 'radar'].includes(weapon) ? 3 : 1
    let best = 0, choices = []
    for (let y = 0; y <= SIZE - height; y++) for (let x = 0; x <= SIZE - width; x++) {
      const area = []
      if (weapon === 'torpedo') { for (let col = x; col < SIZE; col++) { area.push({ x: col, y }); if (knowledge.blockers.has(`${col},${y}`)) break } }
      else if (weapon === 'submarine') { for (let row = 0; row < SIZE; row++) if (row !== y) for (let col = x; col < x + 3; col++) area.push({ x: col, y: row }) }
      else for (let row = y; row < y + height; row++) for (let col = x; col < x + width; col++) area.push({ x: col, y: row })
      const score = area.filter(knowledge.valid).length / (weapon === 'fighter' ? 2 : 1)
      if (!score || score < best) continue
      const option = { weapon, x, y, score, ...(weapon === 'submarine' ? { targets: Array.from({ length: 3 }, (_, i) => ({ x: x + i, y })) } : {}) }
      if (score > best) { best = score; choices = [] }
      choices.push(option)
    }
    options.push(...choices)
  }
  return options
}

export function chooseNavalAiAction(room, random = Math.random) {
  const attacker = room.players[1], defender = room.players[0], knowledge = navalAiKnowledge(defender)
  if (knowledge.ends.length) return { weapon: 'shot', ...pick(knowledge.ends, random) }
  const radar = (attacker.radar || []).filter(p => p.occupied && knowledge.valid(p))
  const targets = [...radar, ...knowledge.adjacent]
  const target = targets.length ? pick(targets, random) : navalAiHunt(knowledge, random)
  if (!target) return null
  const options = navalAiWeaponOptions(attacker, knowledge)
  const prior = [...room.events].reverse().find(e => e.player === 1 && ['fighter', 'bomber', 'torpedo'].includes(e.weapon))
  if (!targets.length && options.length && random() < .42) {
    const fighters = options.filter(o => o.weapon === 'fighter')
    const follow = prior && ['bomber', 'torpedo'].includes(prior.weapon) ? fighters.filter(o => (prior.row ?? prior.y) >= o.y && (prior.row ?? prior.y) < o.y + 2) : []
    if (follow.length && random() < .7) return pick(follow, random)
    const bait = options.filter(o => ['bomber', 'torpedo'].includes(o.weapon))
    const pool = fighters.length && bait.length && random() < .55 ? bait : options
    return pick(pool, random)
  }
  return { weapon: 'shot', ...target }
}

export function navalAiMines(ships, count, random = Math.random) {
  const occupied = new Set(ships.flatMap(cells).map(key))
  const candidates = Array.from({ length: 144 }, (_, i) => ({ x: i % SIZE, y: Math.floor(i / SIZE) })).filter(p => !occupied.has(key(p)))
  // Incoming torpedoes travel left to right on the AI board. Mix forward screens and hull shields.
  const ranked = candidates.map(p => {
    const shields = ships.some(s => p.y >= s.y && p.y < s.y + s.h && s.x - p.x >= 1 && s.x - p.x <= 5)
    const weight = 1 + (p.x < 3 ? 4 : 0) + (shields ? 8 : 0)
    return { p, rank: -Math.log(Math.max(.000001, random())) / weight }
  }).sort((a, b) => a.rank - b.rank)
  return ranked.slice(0, count).map(item => item.p)
}
