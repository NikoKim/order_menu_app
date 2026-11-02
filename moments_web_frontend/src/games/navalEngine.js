import { chooseNavalAiAction, navalAiMines } from './navalAi.js'

const BOARD_SIZE = 12
const PREPARATION_MS = 5 * 60 * 1000
const SUPPLY_MS = 3 * 60 * 1000
const TURN_MS = 30 * 1000
const AI_DELAY_MS = 1200
const clone = value => JSON.parse(JSON.stringify(value))
const key = ({ x, y }) => `${x},${y}`
const integer = value => Number.isInteger(value)
const inside = (x, y) => integer(x) && integer(y) && x >= 0 && y >= 0 && x < BOARD_SIZE && y < BOARD_SIZE

export const FLEET = [
  ...Array.from({ length: 4 }, (_, i) => ({ id: `scout-${i + 1}`, w: 1, h: 1, name: '巡逻艇' })),
  ...Array.from({ length: 3 }, (_, i) => ({ id: `destroyer-${i + 1}`, w: 1, h: 2, name: '驱逐舰' })),
  ...Array.from({ length: 2 }, (_, i) => ({ id: `cruiser-${i + 1}`, w: 1, h: 3, name: '巡洋舰' })),
  { id: 'battleship-1', w: 1, h: 4, name: '战列舰' }
]

export const ITEMS = {
  antiAir: { name: '防空炮', price: 30, max: 3 },
  fighter: { name: '战斗机', price: 30, max: 3 },
  bomber: { name: '轰炸机', price: 25, max: 3 },
  torpedo: { name: '鱼雷机', price: 25, max: 3 },
  radar: { name: '雷达', price: 40, max: 2 },
  submarine: { name: '潜艇', price: 30, max: 3 },
  stealth: { name: '隐身图层', price: 10, max: 5 },
  mine: { name: '水雷', price: 5, max: 12 }
}

export function shipCells(ship) {
  if (!ship || !integer(ship.w) || !integer(ship.h) || ship.w < 1 || ship.h < 1 || ship.w > BOARD_SIZE || ship.h > BOARD_SIZE) return []
  return Array.from({ length: ship.w * ship.h }, (_, i) => ({ x: ship.x + i % ship.w, y: ship.y + Math.floor(i / ship.w) }))
}

export function legalPlacement(ships, ship) {
  if (!ship || !inside(ship.x, ship.y) || !integer(ship.w) || !integer(ship.h) || ship.w < 1 || ship.h < 1 || ship.x + ship.w > BOARD_SIZE || ship.y + ship.h > BOARD_SIZE) return false
  return ships.filter(other => other.id !== ship.id).every(other =>
    ship.x + ship.w < other.x || other.x + other.w < ship.x ||
    ship.y + ship.h < other.y || other.y + other.h < ship.y)
}

export function validateFleet(ships) {
  if (!Array.isArray(ships) || ships.length !== FLEET.length) return '请布置全部 10 艘舰船'
  return validatePartialFleet(ships)
}

export function validatePartialFleet(ships) {
  if (!Array.isArray(ships) || ships.length > FLEET.length) return '最多布置 10 艘舰船'
  if (ships.some(ship => typeof ship?.id !== 'string' || !ship.id.trim() || ship.id.length > 80) || new Set(ships.map(ship => ship.id)).size !== ships.length) return '舰船编号无效或重复'
  const expected = { '1x1': 4, '1x2': 3, '1x3': 2, '1x4': 1 }
  const counts = {}
  for (const ship of ships) {
    const size = `${Math.min(ship.w, ship.h)}x${Math.max(ship.w, ship.h)}`
    if (!expected[size]) return '舰船尺寸不正确'
    counts[size] = (counts[size] || 0) + 1
    if (!legalPlacement(ships, ship)) return '舰船不能越界，彼此须至少隔开一格（包括对角线）'
  }
  if (Object.entries(counts).some(([size, count]) => count > expected[size])) return '舰船尺寸或数量不符合规则'
  return ''
}

export function normalizeSavedFleet(ships) {
  const normalized = clone((ships || []).filter(ship => !(Math.min(ship.w, ship.h) === 2 && Math.max(ship.w, ship.h) === 3)))
  const error = validateFleet(normalized)
  if (error) throw new Error(error)
  return normalized
}

export function migrateLocalGame(currentRoom, now = Date.now()) {
  if (!currentRoom || currentRoom.rulesVersion === 2) return currentRoom
  const room = clone(currentRoom)
  room.rulesVersion = 2
  for (const player of room.players) {
    player.ships = normalizeSavedFleet(player.ships)
    const occupied = new Set(player.ships.flatMap(shipCells).map(key))
    player.shots = (player.shots || []).map(shot => shot.result === 'hit' && !occupied.has(key(shot)) ? { ...shot, result: 'miss' } : shot)
    player.stage = player.ready || ['BATTLE', 'FINISHED'].includes(room.phase) ? 'READY' : Object.values(player.inventory || {}).some(count => count > 0) ? 'SUPPLY' : 'PLACEMENT'
  }
  room.players.forEach((player, i) => {
    const occupied = new Set(room.players[1 - i].ships.flatMap(shipCells).map(key))
    player.radar = (player.radar || []).map(cell => ({ ...cell, occupied: occupied.has(key(cell)) }))
  })
  if (room.phase === 'PLACEMENT' && room.players.every(player => player.stage !== 'PLACEMENT')) {
    room.phase = 'SUPPLY'
    room.deadline = now + SUPPLY_MS
  }
  room.radar = room.players[0].radar || []
  if (room.phase === 'BATTLE') {
    const defeated = room.players.map(player => player.ships.every(ship => ship.hits?.length === ship.w * ship.h))
    if (defeated.some(Boolean)) {
      room.phase = 'FINISHED'; room.winner = defeated[1] ? 0 : 1; room.deadline = 0; room._aiNextAt = null
    }
  }
  return room
}

function shuffled(values) {
  const result = [...values]
  for (let i = result.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1))
    ;[result[i], result[j]] = [result[j], result[i]]
  }
  return result
}

export function generateFleet() {
  return completeFleet([])
}

export function completeFleet(partial) {
  const error = validatePartialFleet(partial)
  if (error) throw new Error(error)
  const placed = clone(partial)
  const counts = {}
  const ids = new Set(placed.map(ship => ship.id))
  for (const ship of placed) counts[ship.w * ship.h] = (counts[ship.w * ship.h] || 0) + 1
  const templates = FLEET.filter(template => {
    const size = template.w * template.h
    if (counts[size]) { counts[size]--; return false }
    return true
  }).map(template => {
    let id = template.id
    while (ids.has(id)) id += '-new'
    ids.add(id)
    return { ...template, id }
  }).sort((a, b) => b.w * b.h - a.w * a.h)
  let nodes = 0
  function place(index) {
    if (index === templates.length) return true
    if (++nodes > 30000) return false
    const template = templates[index]
    const sizes = template.w === template.h ? [[template.w, template.h]] : [[template.w, template.h], [template.h, template.w]]
    const candidates = sizes.flatMap(([w, h]) => Array.from({ length: (BOARD_SIZE - w + 1) * (BOARD_SIZE - h + 1) }, (_, cell) => ({
      ...template, x: cell % (BOARD_SIZE - w + 1), y: Math.floor(cell / (BOARD_SIZE - w + 1)), w, h, hits: []
    })))
    for (const candidate of shuffled(candidates)) {
      if (!legalPlacement(placed, candidate)) continue
      placed.push(candidate)
      if (place(index + 1)) return true
      placed.pop()
      if (nodes > 30000) break
    }
    return false
  }
  if (place(0)) return placed
  if (partial.length) return completeFleet([])

  // A bounded fallback prevents an unlucky random search from blocking the UI.
  const positions = [[0, 10, 1, 1], [3, 10, 1, 1], [9, 10, 1, 1], [11, 2, 1, 1],
    [6, 5, 1, 2], [9, 6, 1, 2], [6, 9, 2, 1], [0, 5, 1, 3], [3, 5, 1, 3],
    [8, 0, 1, 4]]
  return FLEET.map((template, i) => ({ ...template, x: positions[i][0], y: positions[i][1], w: positions[i][2], h: positions[i][3], hits: [] }))
}

function emptyInventory() {
  return Object.fromEntries(Object.keys(ITEMS).map(item => [item, 0]))
}

function randomMines(ships, count) {
  const occupied = new Set(ships.flatMap(shipCells).map(key))
  return shuffled(Array.from({ length: BOARD_SIZE * BOARD_SIZE }, (_, i) => ({ x: i % BOARD_SIZE, y: Math.floor(i / BOARD_SIZE) })))
    .filter(cell => !occupied.has(key(cell))).slice(0, count)
}

export function createLocalGame(now = Date.now()) {
  const enemyShips = generateFleet()
  return {
    id: `local-${now}-${Math.random().toString(36).slice(2, 8)}`,
    rulesVersion: 2, inviteCode: '', mode: 'AI', autoMatch: false, phase: 'PLACEMENT', deadline: now + PREPARATION_MS,
    turn: 0, you: 0, winner: null,
    players: [
      { name: '我方舰队', stage: 'PLACEMENT', ready: false, ships: generateFleet(), shots: [], mines: [], aa: [], inventory: emptyInventory(), coins: 300, radar: [] },
      { name: 'AI 舰队', stage: 'READY', ready: true, ships: enemyShips, shots: [], mines: navalAiMines(enemyShips, 4), aa: shuffled(Array.from({ length: 12 }, (_, i) => i)).slice(0, 2), inventory: { antiAir: 2, fighter: 2, bomber: 2, torpedo: 1, radar: 2, mine: 4 }, coins: 0, radar: [] }
    ], messages: [], events: [], radar: [], _sequence: 0, _aiNextAt: null
  }
}

function event(room, details) {
  room._sequence = (room._sequence || 0) + 1
  room.events.push({ id: `event-${room._sequence}`, cells: [], ...details })
}

function beginBattle(room, now) {
  room.phase = 'BATTLE'
  room.turn = 0
  room.deadline = now + TURN_MS
  room._aiNextAt = null
  event(room, { player: -1, weapon: 'start', text: '舰队已就位，战斗开始' })
  waitForPresentation(room, now)
}
function waitForPresentation(room, now) {
  if (!room.presentationSync) return
  room.presentationId = room._sequence; room.presentationUntil = now+15000
  room.deadline = room.presentationUntil+TURN_MS; room._aiNextAt = room.presentationUntil+AI_DELAY_MS
}

function beginSupplyIfArranged(room, now) {
  if (room.players.every(player => player.stage !== 'PLACEMENT') && room.phase === 'PLACEMENT') {
    room.phase = 'SUPPLY'
    room.deadline = now + SUPPLY_MS
    event(room, { player: -1, weapon: 'supply', text: '阵型已锁定，进入补给阶段' })
  }
}

function layoutReady(room, action, now) {
  const player = room.players[0]
  if (room.phase !== 'PLACEMENT' || player.stage !== 'PLACEMENT') throw new Error('当前不能修改或提交阵型')
  if (action.ships) applyDeployment(player, { ships: action.ships })
  const error = validateFleet(player.ships)
  if (error) throw new Error(error)
  player.stage = 'SUPPLY'
  beginSupplyIfArranged(room, now)
}

function sameFleet(a, b) {
  return a.length === b.length && a.every(ship => b.some(other => ship.id === other.id && ['x', 'y', 'w', 'h'].every(field => ship[field] === other[field])))
}

function applyDeployment(player, action) {
  const error = player.stage === 'PLACEMENT' ? validatePartialFleet(action.ships) : validateFleet(action.ships)
  if (error) throw new Error(error)
  const inventory = emptyInventory()
  for (const name of Object.keys(action.inventory || {})) {
    if (!ITEMS[name]) throw new Error('未知的道具')
  }
  let total = 0
  for (const [name, item] of Object.entries(ITEMS)) {
    const count = action.inventory?.[name] ?? 0
    if (!integer(count) || count < 0 || count > item.max) throw new Error(`${item.name}数量不正确`)
    inventory[name] = count
    total += count * item.price
  }
  if (total > 300) throw new Error('金币不足，每局最多使用 300 金币')
  const mines = action.mines || []
  const aa = action.aa || []
  if (!Array.isArray(mines) || mines.length > inventory.mine || !Array.isArray(aa) || aa.length > inventory.antiAir) throw new Error('部署的道具数量超过已购买数量')
  const occupied = new Set(action.ships.flatMap(shipCells).map(key))
  const mineKeys = new Set()
  for (const mine of mines) {
    if (!mine || !inside(mine.x, mine.y) || occupied.has(key(mine)) || mineKeys.has(key(mine))) throw new Error('水雷不能越界、重复或与舰船重叠')
    mineKeys.add(key(mine))
  }
  if (new Set(aa).size !== aa.length || aa.some(row => !integer(row) || row < 0 || row >= BOARD_SIZE)) throw new Error('防空炮须部署在不同的有效行')
  const coated = action.coatedShips || []
  if (!Array.isArray(coated) || new Set(coated).size !== coated.length || coated.length > inventory.stealth || coated.some(id => !action.ships.some(ship => ship.id === id))) throw new Error('隐身涂层数量或舰船无效')
  player.ships = action.ships.map(ship => ({ id: ship.id, x: ship.x, y: ship.y, w: ship.w, h: ship.h, hits: [], stealth: coated.includes(ship.id) }))
  player.inventory = inventory
  player.coins = 300 - total
  player.mines = mines.map(({ x, y }) => ({ x, y }))
  player.aa = [...aa]
}

function validateReady(player) {
  const error = validateFleet(player.ships)
  if (error) throw new Error(error)
  if (player.mines.length !== player.inventory.mine || player.aa.length !== player.inventory.antiAir || player.ships.filter(ship => ship.stealth).length !== (player.inventory.stealth || 0)) throw new Error('请先部署全部水雷、防空炮，并涂好隐身图层')
}

export function submarineFootprint(targets) {
  if (!Array.isArray(targets) || targets.length !== 3 || targets.some(cell => !cell || !inside(cell.x, cell.y)) || new Set(targets.map(key)).size !== 3) throw new Error('请选择三个不同的有效格子')
  const cells = [...targets].sort((a, b) => a.y - b.y || a.x - b.x)
  const horizontal = cells.every(cell => cell.y === cells[0].y) && cells[2].x - cells[0].x === 2
  const vertical = cells.every(cell => cell.x === cells[0].x) && cells[2].y - cells[0].y === 2
  if (!horizontal && !vertical) throw new Error('潜艇占三格，请选择连续一行或一列的三个格子')
  return cells
}

export function submarineAttack(room, action, attacker, now) {
  const footprint = submarineFootprint(action.targets), player = room.players[attacker], opponent = room.players[1 - attacker]
  if (!(player.inventory.submarine > 0)) throw new Error('潜艇已经用完')
  player.inventory.submarine--
  const route = [], torpedoes = [], outcomes = [], firstX = footprint[0].x
  let destroyed = false, hit = false
  // The whole three-cell hull travels horizontally from the attacker's sea edge.
  for (let shift = -firstX - (footprint[2].x - firstX); shift <= 0 && !destroyed; shift++) {
    const occupied = footprint.map(cell => ({ x: cell.x + shift, y: cell.y })).filter(cell => inside(cell.x, cell.y))
    route.push({ x: footprint[0].x + shift, y: footprint[0].y })
    for (const cell of occupied) {
      const mine = opponent.mines.findIndex(point => key(point) === key(cell))
      if (mine < 0) continue
      opponent.mines.splice(mine, 1); opponent.inventory.mine = Math.max(0, opponent.inventory.mine - 1)
      for (const part of occupied) {
        if (opponent.shots.some(point => key(point) === key(part))) continue
        const extraMine = opponent.mines.findIndex(point => key(point) === key(part))
        const isMine = key(part) === key(cell) || extraMine >= 0
        if (extraMine >= 0) { opponent.mines.splice(extraMine, 1); opponent.inventory.mine = Math.max(0, opponent.inventory.mine - 1) }
        const ship = opponent.ships.find(ship => shipCells(ship).some(point => key(point) === key(part)))
        if (ship) { ship.hits = [...new Set([...(ship.hits || []), key(part)])]; hit = true }
        const shot = { ...part, result: isMine ? 'mine' : ship ? 'hit' : 'miss' }
        outcomes.push(shot); opponent.shots.push(shot)
      }
      destroyed = true; break
    }
  }
  if (!destroyed) for (const direction of [-1, 1]) {
    const origin = footprint[Math.floor(Math.random() * 3)], path = [{ ...origin }]
    for (let y = origin.y + direction; inside(origin.x, y); y += direction) {
      const cell = { x: origin.x, y }, ship = opponent.ships.find(ship => shipCells(ship).some(point => key(point) === key(cell)))
      path.push(cell)
      const mine = opponent.mines.findIndex(point => key(point) === key(cell))
      if (!opponent.shots.some(point => key(point) === key(cell))) {
        const result = mine >= 0 ? 'mine' : ship ? 'hit' : 'miss', shot = { ...cell, result }
        if (ship) { ship.hits.push(key(cell)); hit = true }
        if (mine >= 0) { opponent.mines.splice(mine, 1); opponent.inventory.mine = Math.max(0, opponent.inventory.mine - 1) }
        outcomes.push(shot); opponent.shots.push(shot)
      }
      if (ship || mine >= 0) break
    }
    torpedoes.push(path)
  }
  event(room, { player: attacker, weapon: 'submarine', x: footprint[0].x, y: footprint[0].y, targets: footprint, route, torpedoes, destroyed, cells: outcomes, text: destroyed ? '潜艇触雷，被炸毁，未能发射鱼雷' : hit ? '潜艇鱼雷命中，获得一次追加攻击' : '潜艇完成上下双向鱼雷攻击' })
  if (opponent.ships.every(ship => ship.hits.length === ship.w * ship.h)) {
    room.phase = 'FINISHED'; room.winner = attacker; room.deadline = 0; room._aiNextAt = null
    event(room, { player: attacker, weapon: 'victory', text: '对方舰队已全部击毁' })
  } else scheduleTurn(room, hit ? attacker : 1 - attacker, now, hit && attacker === 0)
}

function attackCells(weapon, x, y) {
  const sizes = { shot: [1, 1], fighter: [8, 2], bomber: [3, 3], radar: [3, 3], torpedo: [12, 1] }
  const size = sizes[weapon]
  if (!size) throw new Error('未知的攻击类型')
  if (weapon === 'torpedo') size[0] = BOARD_SIZE - x
  if (!inside(x, y) || x + size[0] > BOARD_SIZE || y + size[1] > BOARD_SIZE) throw new Error('目标区域超出海域')
  const cells = Array.from({ length: size[0] * size[1] }, (_, i) => ({ x: x + i % size[0], y: y + Math.floor(i / size[0]) }))
  return weapon === 'bomber' ? shuffled(cells).slice(0, 5 + Math.floor(Math.random() * 3)) : cells
}

function scheduleTurn(room, player, now, immediate = false) {
  room.turn = player
  room.deadline = now + TURN_MS
  room._aiNextAt = player === 1 ? now + AI_DELAY_MS : null
  if (immediate) room.presentationUntil = 0
  else waitForPresentation(room, now)
}

function applyAttack(room, action, attacker, now) {
  if (room.presentationUntil) throw new Error('请等待战斗动画播放完毕')
  if (room.phase !== 'BATTLE' || room.turn !== attacker) throw new Error('还没有轮到你行动')
  const weapon = action.weapon || 'shot'
  if (weapon === 'submarine') { submarineAttack(room, action, attacker, now); return }
  const player = room.players[attacker]
  const opponent = room.players[1 - attacker]
  if (weapon !== 'shot' && (!ITEMS[weapon] || !['fighter', 'bomber', 'torpedo', 'radar'].includes(weapon) || !(player.inventory[weapon] > 0))) throw new Error('该道具已经用完')
  const cells = attackCells(weapon, action.x, action.y)
  const shotKeys = new Set(opponent.shots.map(key))
  const targetArea = weapon === 'bomber' ? attackCells('radar', action.x, action.y) : cells
  if (weapon !== 'radar' && targetArea.every(cell => shotKeys.has(key(cell)))) throw new Error('这个区域已经全部攻击过了')
  if (weapon !== 'shot') player.inventory[weapon]--
  const attackRow = weapon === 'fighter' ? action.y + Math.floor(Math.random()*2) : action.y

  const intercepted = ['fighter', 'bomber', 'torpedo'].includes(weapon)
    ? weapon === 'fighter' && opponent.aa.includes(attackRow) ? opponent.aa.indexOf(attackRow) : opponent.aa.findIndex(row => (weapon === 'bomber'
      ? row >= action.y && row < action.y + 3
      : cells.some(cell => cell.y === row)))
    : -1
  if (intercepted >= 0) {
    const [row] = opponent.aa.splice(intercepted, 1)
    opponent.inventory.antiAir = Math.max(0, opponent.inventory.antiAir - 1)
    event(room, { player: attacker, weapon, x: action.x, y: attackRow, row, text: `${ITEMS[weapon].name}被第 ${row + 1} 行的防空炮击落` })
    scheduleTurn(room, 1 - attacker, now)
    return
  }

  if (weapon === 'radar') {
    const occupied = new Set(opponent.ships.filter(ship => !ship.stealth).flatMap(shipCells).map(key))
    const found = cells.map(cell => ({ ...cell, occupied: occupied.has(key(cell)) }))
    player.radar = [...(player.radar || []).filter(existing => !cells.some(cell => key(cell) === key(existing))), ...found]
    room.radar = room.players[0].radar || []
    event(room, { player: attacker, weapon, x: action.x, y: action.y, text: '雷达完成区域扫描' })
    scheduleTurn(room, 1 - attacker, now)
    return
  }

  let hit = false
  const outcomes = [], backfire = []
  for (const cell of weapon === 'fighter' ? cells.filter(cell=>cell.y===attackRow) : cells) {
    const cellKey = key(cell)
    if (shotKeys.has(cellKey)) {
      if (weapon === 'torpedo' && opponent.ships.some(ship => shipCells(ship).some(point => key(point) === cellKey))) {
        outcomes.push({ ...opponent.shots.find(shot => key(shot) === cellKey) }); break
      }
      continue
    }
    const mineIndex = opponent.mines.findIndex(mine => key(mine) === cellKey)
    const ship = opponent.ships.find(item => shipCells(item).some(occupied => key(occupied) === cellKey))
    const result = mineIndex >= 0 ? 'mine' : ship ? 'hit' : 'miss'
    if (mineIndex >= 0) {
      opponent.mines.splice(mineIndex, 1)
      opponent.inventory.mine = Math.max(0, opponent.inventory.mine - 1)
      if (weapon !== 'torpedo') {
        const ownShip = player.ships.find(ship => shipCells(ship).some(point => key(point) === cellKey))
        const ownMine = player.mines.findIndex(point => key(point) === cellKey)
        const reflected = { ...cell, result: ownShip ? 'hit' : ownMine >= 0 ? 'mine' : 'miss' }
        if (ownMine >= 0) { player.mines.splice(ownMine,1); player.inventory.mine = Math.max(0,player.inventory.mine-1) }
        if (ownShip) ownShip.hits = [...new Set([...(ownShip.hits || []),cellKey])]
        if (!player.shots.some(shot=>key(shot)===cellKey)) player.shots.push(reflected)
        backfire.push(reflected)
      }
    }
    if (ship) {
      ship.hits = [...new Set([...(ship.hits || []), cellKey])]
      hit = true
    }
    const outcome = { ...cell, result }
    opponent.shots.push(outcome)
    outcomes.push(outcome)
    shotKeys.add(cellKey)
    if (weapon === 'torpedo' && (ship || mineIndex >= 0)) break
  }
  const sunk = opponent.ships.filter(ship => (ship.hits || []).length === ship.w * ship.h)
  event(room, { player: attacker, weapon, x: action.x, y: attackRow, cells: outcomes, backfire, text: backfire.length ? '引爆水雷，己方同坐标海域受到反击' : hit ? '命中舰船，获得一次额外攻击' : outcomes.some(cell => cell.result === 'mine') ? '引爆水雷' : '攻击落水', sunk: sunk.map(ship => ship.id) })
  const selfDestroyed = player.ships.every(ship => (ship.hits || []).length === ship.w*ship.h)
  if (sunk.length === FLEET.length || selfDestroyed) {
    room.phase = 'FINISHED'
    room.winner = selfDestroyed ? 1-attacker : attacker
    room.deadline = 0
    room._aiNextAt = null
    event(room, { player: room.winner, weapon: 'victory', text: selfDestroyed ? '水雷反击击毁了攻击方全部舰船' : '对方舰队全部被击毁' })
  } else scheduleTurn(room, hit ? attacker : 1 - attacker, now, hit && attacker === 0)
}

function advanceTime(room, now) {
  if (room.phase === 'PLACEMENT' && now >= room.deadline) {
    for (const player of room.players) {
      if (player.stage !== 'PLACEMENT') continue
      if (validateFleet(player.ships)) {
        const before = player.ships
        player.ships = completeFleet(before)
        if (before.some(ship => !player.ships.some(next => next.id === ship.id && next.x === ship.x && next.y === ship.y))) event(room, { player: -1, weapon: 'formation', text: '剩余舰船无法排入，已自动重排合法阵型' })
        player.mines = []
      }
      player.stage = 'SUPPLY'
    }
    beginSupplyIfArranged(room, now)
  } else if (room.phase === 'SUPPLY' && now >= room.deadline) {
    for (const player of room.players) {
      const remainingMines = randomMines(player.ships, 144).filter(cell => !player.mines.some(mine => key(mine) === key(cell)))
      player.mines.push(...remainingMines.slice(0, Math.max(0, player.inventory.mine - player.mines.length)))
      const remainingRows = shuffled(Array.from({ length: 12 }, (_, i) => i)).filter(row => !player.aa.includes(row))
      player.aa.push(...remainingRows.slice(0, Math.max(0, player.inventory.antiAir - player.aa.length)))
      let coats = (player.inventory.stealth || 0) - player.ships.filter(ship => ship.stealth).length
      for (const ship of player.ships) if (!ship.stealth && coats > 0) { ship.stealth = true; coats-- }
      player.ready = true
      player.stage = 'READY'
    }
    beginBattle(room, now)
  } else if (room.phase === 'BATTLE' && now >= room.deadline) {
    const expiredPlayer = room.turn
    scheduleTurn(room, 1 - expiredPlayer, now)
    event(room, { player: expiredPlayer, weapon: 'timeout', text: '行动超时，轮到对方' })
  }
}

function aiAction(room) {
  return chooseNavalAiAction(room)
}

export function localAction(currentRoom, action, now = Date.now()) {
  if (!currentRoom || !action || !action.type) throw new Error('无效的游戏操作')
  const room = clone(migrateLocalGame(currentRoom, now))
  if (action.type !== 'LEAVE') advanceTime(room, now)
  if (action.type === 'CHAT') {
    const text = typeof action.text === 'string' ? action.text.trim() : ''
    if (!text || text.length > 300) throw new Error('消息长度须为 1 至 300 个字符')
    room._sequence = (room._sequence || 0) + 1
    room.messages.push({ id: `message-${room._sequence}`, player: 0, name: room.players[0].name, text, at: now })
    room.messages = room.messages.slice(-100)
  } else if (action.type === 'DEPLOY') {
    const player = room.players[0]
    if (!['PLACEMENT', 'SUPPLY'].includes(room.phase) || player.ready) throw new Error('当前不能调整部署')
    if (player.stage === 'PLACEMENT' && (Object.values(action.inventory || {}).some(value => value !== 0) || action.mines?.length || action.aa?.length || action.ready)) throw new Error('请先完成布阵，再购买和部署道具')
    if (player.stage === 'SUPPLY' && action.ships && !sameFleet(player.ships, action.ships)) throw new Error('补给阶段阵型已锁定，不能移动舰船')
    applyDeployment(player, { ...action, ships: action.ships || player.ships })
    if (action.ready) {
      validateReady(player)
      player.ready = true; player.stage = 'READY'
      if (room.players.every(member => member.ready)) beginBattle(room, now)
    }
  } else if (action.type === 'LAYOUT_READY') {
    layoutReady(room, action, now)
  } else if (action.type === 'READY') {
    if (!['PLACEMENT', 'SUPPLY'].includes(room.phase) || room.players[0].stage !== 'SUPPLY') throw new Error('请先完成布阵，再确认补给')
    validateReady(room.players[0])
    room.players[0].ready = true
    room.players[0].stage = 'READY'
    if (room.players.every(member => member.ready)) beginBattle(room, now)
  } else if (action.type === 'PRESENTED') {
    if (room.presentationUntil && action.presentationId === room.presentationId) {
      room.presentationUntil = 0; room.deadline = now+TURN_MS; room._aiNextAt = room.turn === 1 ? now+AI_DELAY_MS : null
    }
  } else if (action.type === 'ATTACK') {
    applyAttack(room, action, 0, now)
  } else if (action.type === 'LEAVE') {
    if (room.phase === 'FINISHED') return room
    const started = room.events.some(event => event.weapon === 'start')
    room.phase = 'FINISHED'
    room.winner = started ? 1 : null
    room.finishReason = started ? 'PLAYER_LEFT' : 'CANCELLED'
    room.deadline = 0
    room._aiNextAt = null
    event(room, { player: 0, weapon: 'leave', text: '我方退出战斗' })
  } else throw new Error('未知的游戏操作')
  return room
}

export function localBattleResult(room) {
  return {
    ruleSet: room.ruleSet || 'CLASSIC', battleStarted: Boolean(room.battleStarted || room.events.some(event => event.weapon === 'start')),
    gameId: `${room.id}:${room.round || 1}`, mode: room.mode, winner: room.winner, finishedAt: Date.now(), events: clone(room.events || []),
    players: room.players.map((player, index) => {
      const target = room.players[1-index], events = (room.events || []).filter(event => event.player === index), used = {}
      for (const event of events) if (ITEMS[event.weapon]) used[event.weapon] = (used[event.weapon] || 0)+1
      used.mine = player.shots.filter(cell => cell.result === 'mine').length
      used.antiAir = (room.events || []).filter(event => event.player !== index && event.row != null).length
      used.stealth = player.ships.filter(ship => ship.stealth).length
      const remaining = { ...player.inventory, stealth: Math.max(0,(player.inventory.stealth || 0)-used.stealth) }
      const purchased = Object.fromEntries(Object.keys(ITEMS).map(key => [key,(remaining[key] || 0)+(used[key] || 0)]))
      if(room.ruleSet==='MILITARY')for(const values of [used,purchased,remaining])for(const key of Object.keys(values))delete values[key]
      return { medals: player.medals, name: player.name, coinsSpent: 300-player.coins, itemsUsed: used, itemsPurchased: purchased, inventoryRemaining: remaining, ships: clone(player.ships), shotsReceived: clone(player.shots), minesRemaining: clone(player.mines), antiAirRemaining: clone(player.aa), hits: target.shots.filter(shot => shot.result === 'hit').length, shots: target.shots.length, attacks: events.filter(event => ['shot','fighter','bomber','torpedo','radar','submarine','depthCharge'].includes(event.weapon)).length }
    })
  }
}

export function tickLocalGame(currentRoom, now = Date.now()) {
  currentRoom = migrateLocalGame(currentRoom, now)
  if (currentRoom?.presentationUntil) {
    if (now < currentRoom.presentationUntil) return currentRoom
    const released = clone(currentRoom); released.presentationUntil=0; released.deadline=now+TURN_MS; released._aiNextAt=released.turn===1 ? now+AI_DELAY_MS : null
    return released
  }
  if (!currentRoom || currentRoom.phase === 'FINISHED' ||
      (now < currentRoom.deadline && !(currentRoom.phase === 'BATTLE' && currentRoom.turn === 1 && now >= currentRoom._aiNextAt))) return currentRoom
  const room = clone(currentRoom)
  advanceTime(room, now)
  if (room.phase === 'BATTLE' && room.turn === 1 && now >= room._aiNextAt) {
    const action = aiAction(room)
    if (action) applyAttack(room, action, 1, now)
  }
  return room
}
