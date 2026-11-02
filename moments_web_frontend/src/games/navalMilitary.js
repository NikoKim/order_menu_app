import {depthChargeAttack} from './navalDepthCharge.js'
import { submarineAttack } from './navalEngine.js'
import {chooseMilitaryFleet} from './navalMilitaryIntel.js'
import {chooseMilitaryAction} from './navalMilitaryAi.js'
export const MILITARY_SHIPS = {
  carrier: { name: '航母（战斗机）', w: 1, h: 4, price: 50, max: 2, group:'carrier', detail: '两架战斗机 · 1 × 6 内随机攻击 4–5 格' },
  bomberCarrier: {name:'航母（轰炸机）',w:1,h:4,price:50,max:2,group:'carrier',detail:'中间两格共用一架轰炸机 · 3 × 3 随机攻击 5–6 格'},
  torpedoCarrier:{name:'航母（鱼雷机）',w:1,h:4,price:60,max:2,group:'carrier',detail:'中间甲板共用一架鱼雷机，沿行投放鱼雷'},
  battleship: { name: '战列舰', w: 1, h: 3, price: 35, max: 4, group:'medium', detail: '首尾主炮 · 三格内随机命中 1 / 2 格，一格已命中时其余各 35%，两格时剩余 45%' },
  cruiser: { name: '巡洋舰', w: 1, h: 3, price: 30, max: 4, group:'medium', detail: '舰首主炮 · 单格炮击' },
  antiAir: { name: '防空驱逐舰', w: 1, h: 2, price: 30, max: 6, group:'destroyer', detail: '舰首防空炮 · 舰体及周围一格' },
  torpedo: { legacy:true, name: '鱼雷驱逐舰（旧版）', w: 1, h: 2, price: 30, max: 6, group:'destroyer', detail: '舰尾鱼雷机 · 鱼雷沿行前进，遇舰体（含残骸）或水雷停止' },
  radar: { name: '雷达驱逐舰', w: 1, h: 2, price: 20, max: 6, group:'destroyer', detail: '舰首雷达 · 2 × 2 扫描' },
  mineLayer:{name:'布雷驱逐舰',w:1,h:2,price:30,max:6,group:'destroyer',detail:'全舰损毁前可布置 10 枚水雷，每回合最多两枚，确认后占一次行动'},
  jammer:{name:'干扰驱逐舰',w:1,h:2,price:20,max:6,group:'destroyer',detail:'舰首前方 4 格、横向 3 格矩形提供雷达隐蔽'},
  submarine:{name:'潜艇',w:1,h:3,price:50,max:4,group:'medium',detail:'复用经典三格潜航与上下双鱼雷，触雷或中部命中直接击沉，两端受击不直接击沉'},
  depthCharge:{name:'深水炸弹驱逐舰',w:1,h:2,price:25,max:6,group:'destroyer',detail:'两格各一门深弹发射器，首格投上侧、尾格投下侧；碰撞双方整舰沉没并波及当时两格水域，触雷自身沉没；航路不留弹坑'},
  decoy: { name: '佯攻舰', w: 1, h: 2, price: 15, max: 3, group:'decoy', detail: '雷达诱饵 · 不计入存活舰艇' }
}
export const militaryInside = (x, y) => Number.isInteger(x) && Number.isInteger(y) && x >= 0 && y >= 0 && x < 12 && y < 12
const copy = value => JSON.parse(JSON.stringify(value))
export const cellKey = p => `${p.x},${p.y}`
export const militaryGroupCount=(ships,kind)=>ships.filter(s=>MILITARY_SHIPS[s.kind]?.group===MILITARY_SHIPS[kind]?.group).length
export function shuffleMilitary(values,random=Math.random){const a=[...values];for(let i=a.length-1;i>0;i--){const j=Math.floor(random()*(i+1));[a[i],a[j]]=[a[j],a[i]]}return a}
export function sampleMilitaryAir(targets,shots,count,random=Math.random){
  const hit=new Set(shots.filter(s=>s.result==='hit').map(cellKey)),old=targets.filter(c=>hit.has(cellKey(c))),fresh=targets.filter(c=>!hit.has(cellKey(c))),n=Math.min(count,targets.length)
  // Calibrate inclusion probabilities while retaining exactly n distinct attack cells.
  const expected=Math.min(n,fresh.length*Math.min(1,n/targets.length+old.length*.02)),base=Math.floor(expected)
  const take=Math.max(n-old.length,Math.min(fresh.length,base+(random()<expected-base?1:0)))
  return [...shuffleMilitary(fresh,random).slice(0,take),...shuffleMilitary(old,random).slice(0,n-take)]
}
export function militaryJamming(player){const out=new Map();for(const s of player.ships||[])if(s.kind==='jammer'&&militaryComponents(s).some(p=>p.active)&&s.x>=0)for(let forward=1;forward<=4;forward++)for(let side=-1;side<=1;side++){const p=militaryCell(s,side,-forward);if(militaryInside(p.x,p.y))out.set(cellKey(p),p)}return [...out.values()]}
export function militaryCell(ship, x, y) {
  const model = MILITARY_SHIPS[ship.kind], r = ship.rotation || 0
  if (r === 90) return { x: ship.x + model.h - 1 - y, y: ship.y + x }
  if (r === 180) return { x: ship.x + model.w - 1 - x, y: ship.y + model.h - 1 - y }
  if (r === 270) return { x: ship.x + y, y: ship.y + model.w - 1 - x }
  return { x: ship.x + x, y: ship.y + y }
}
export const militarySunk = s => s.hits?.length >= s.w * s.h
export function normalizeMilitaryDamage(room) {
  if(room.phase==='PLACEMENT')for(const p of room.players||[])if(p.ships.some(s=>MILITARY_SHIPS[s.kind]?.legacy)){p.ships=p.ships.filter(s=>!MILITARY_SHIPS[s.kind]?.legacy);p.coins=300-p.ships.reduce((n,s)=>n+(MILITARY_SHIPS[s.kind]?.price||0),0)}
  for(const player of room.players || [])for(const ship of player.ships || [])if(ship.kind==='submarine'&&(militarySunk(ship)||ship.hits.includes(cellKey(militaryCell(ship,0,1)))))for(let i=0;i<ship.w*ship.h;i++){
    const c={x:ship.x+i%ship.w,y:ship.y+Math.floor(i/ship.w)}
    if(!ship.hits.includes(cellKey(c)))ship.hits.push(cellKey(c))
    if(!player.shots.some(s=>cellKey(s)===cellKey(c)))player.shots.push({...c,result:'hit'})
  }
  return room
}
export function militaryComponents(ship) {
  const type = ship.kind, model = MILITARY_SHIPS[type]
  if (!model) return []
  const parts = type==='depthCharge'?[[0,0,'depthCharge',0],[0,1,'depthCharge',1]]: type === 'carrier' ? [[0, 1, 'fighter', 0], [0, 2, 'fighter', 1]]
    : type === 'bomberCarrier' ? [[0,1,'bomber',0]] : type==='torpedoCarrier'?[[0,1,'torpedo',0]]
    : type === 'battleship' ? [[0, 0, 'gun', 0], [0, 2, 'gun', 1]]
      : type === 'cruiser' ? [[0, 0, 'gun', 0]] : type === 'decoy' ? []
        : [[0, type === 'torpedo' ? 1 : 0, type==='mineLayer'?'mine':type, 0]]
  return parts.map(([x, y, role, index]) => {
    const cell = militaryCell(ship, x, y)
    return { ...cell, role, index, active: !militarySunk(ship) && (!['mineLayer','submarine'].includes(type) && ship.hits?.includes(cellKey(cell)) ? false : true) && (!['bomberCarrier','torpedoCarrier'].includes(type)||!ship.hits?.includes(cellKey(militaryCell(ship,0,2)))) && (type!=='mineLayer'||(ship.minesUsed||0)<10) && !(ship.lostPlanes || []).includes(index) }
  })
}
export function militaryActions(player) {
  return (player?.ships || []).flatMap((ship, index) => militaryComponents(ship).filter(p => p.active && ['gun', 'fighter', 'bomber', 'torpedo', 'radar','mine','submarine','depthCharge'].includes(p.role))
    .filter((p, index) => !['gun','depthCharge'].includes(p.role) || index === 0).map(part => ({ shipId: ship.id, plane: part.index, weapon: part.role === 'gun' ? 'shot' : part.role, kind: ship.kind, name: `${MILITARY_SHIPS[ship.kind].name} ${player.ships.slice(0, index + 1).filter(s => s.kind === ship.kind).length} · ${part.role === 'gun' ? (ship.kind==='battleship'?`主炮 ${ship.w>1?'1×3':'3×1'}`:'主炮') : part.role === 'fighter' ? `战斗机 ${part.index + 1}` : part.role === 'radar' ? '雷达' : ({bomber:'轰炸机',mine:'布雷',submarine:'潜航',depthCharge:'深弹突击'})[part.role] || '鱼雷机'}` })))
}
export function militaryCoverage(player) {
  const cells = new Map()
  for (const s of player?.ships || []) if (s.x >= 0 && s.kind === 'antiAir' && militaryComponents(s).some(p => p.active)) {
    for (let y = s.y - 1; y <= s.y + s.h; y++) for (let x = s.x - 1; x <= s.x + s.w; x++) if (militaryInside(x, y)) cells.set(`${x},${y}`, { x, y })
  }
  return [...cells.values()]
}
export function militaryPlacement(ships, candidate) {
  return militaryInside(candidate.x, candidate.y) && candidate.x + candidate.w <= 12 && candidate.y + candidate.h <= 12 && ships.filter(s => s.id !== candidate.id && s.x >= 0).every(s => candidate.x + candidate.w < s.x || s.x + s.w < candidate.x || candidate.y + candidate.h < s.y || s.y + s.h < candidate.y)
}
export function validateMilitaryFleet(ships, ready = true) {
  if (!Array.isArray(ships) || ships.length > 16) return '舰队数据无效'
  let cost = 0
  const counts = {}, ids = new Set()
  for (const s of ships) {
    const m = MILITARY_SHIPS[s.kind], r = s.rotation || 0
    if (!m || m.legacy || ![0, 90, 180, 270].includes(r) || typeof s.id !== 'string' || !s.id.trim() || s.id.length > 80 || ids.has(s.id)) return '舰船型号或编号无效'
    ids.add(s.id); counts[s.kind] = (counts[s.kind] || 0) + 1; cost += m.price
    if (Object.entries(counts).filter(([kind])=>MILITARY_SHIPS[kind].group===m.group).reduce((n,[,count])=>n+count,0) > ({carrier:2,medium:4,destroyer:6,decoy:3})[m.group]) return '超过该型号购买上限'
    if (s.w !== (r % 180 ? m.h : m.w) || s.h !== (r % 180 ? m.w : m.h)) return '舰船尺寸无效'
    if ((ready || s.x !== -1 || s.y !== -1) && !militaryPlacement(ships, s)) return '舰船须在海域内，彼此隔开一格（含斜角）'
  }
  if (cost > 300) return '金币不足'
  if (ready && !militaryActions({ ships }).some(a => !['radar','mine'].includes(a.weapon))) return '至少部署一艘具有进攻能力的舰船'
  return ''
}
export function arrangeMilitaryFleet(ships) {
  for (let attempt = 0; attempt < 100; attempt++) {
    const placed = []
    for (const original of [...ships].sort((a, b) => b.w * b.h - a.w * a.h)) {
      let found = false
      const cells = Array.from({ length: 144 }, (_, i) => i).sort(() => Math.random() - .5)
      for (const i of cells) {
        const s = { ...copy(original), x: i % 12, y: Math.floor(i / 12) }
        if (militaryPlacement(placed, s)) { placed.push(s); found = true; break }
      }
      if (!found) break
    }
    if (placed.length === ships.length) return ships.map(s => placed.find(p => p.id === s.id))
  }
  throw new Error('当前舰队无法排入海域，请减少舰船或调整方向')
}
export function defaultMilitaryFleet() {
  const kinds=chooseMilitaryFleet(MILITARY_SHIPS),rowWeight=Math.random()<.15?3:20
  let best,bestScore=-Infinity
  for(let i=0;i<25;i++){
    const ships=arrangeMilitaryFleet(kinds.map((kind,j)=>{const rotation=Math.floor(Math.random()*4)*90,m=MILITARY_SHIPS[kind];return {...m,kind,id:`${kind}-${j}`,x:-1,y:-1,rotation,w:rotation%180?m.h:m.w,h:rotation%180?m.w:m.h,hits:[],lostPlanes:[],minesUsed:0}})),p={ships}
    const aa=militaryCoverage(p),jam=new Set(militaryJamming(p).map(cellKey))
    const score=new Set(aa.map(c=>c.y)).size*rowWeight+aa.reduce((n,c)=>n+(12-c.x)/12,0)+ships.filter(s=>s.kind!=='jammer').reduce((n,s)=>n+Array.from({length:s.w*s.h},(_,j)=>jam.has(`${s.x+j%s.w},${s.y+Math.floor(j/s.w)}`)?4:0).reduce((a,b)=>a+b,0),0)
    if(score>bestScore){bestScore=score;best=ships}
  }
  return best
}
export function createMilitaryGame(now = Date.now()) {
  const player = (name, ai) => {const ships=ai?defaultMilitaryFleet():[];return { name, ships, shots: [], radar: [], inventory: {}, mines: [], aa: [], coins: 300-ships.reduce((n,s)=>n+MILITARY_SHIPS[s.kind].price,0), ready: ai, stage: ai ? 'READY' : 'PLACEMENT' }}
  return { id: `local-${now}-${Math.random().toString(36).slice(2,8)}`, ruleSet: 'MILITARY', mode: 'AI', phase: 'PLACEMENT', turn: 0, you: 0, players: [player('我方舰队', false), player('海军 AI', true)], events: [], deadline: now + 480000, winner: null, sequence: 0 }
}
export function militaryTargetCells(action, ship) {
  const { x, y, weapon } = action
  if(weapon==='mine'&&Array.isArray(action.targets)&&action.targets.length)return action.targets.map(c=>({x:c.x,y:c.y}))
  if (weapon === 'fighter') return Array.from({ length: 6 }, (_, i) => ({ x:x+i, y }))
  if (weapon === 'depthCharge') return [{x,y},{x:x+1,y}]
  if (weapon === 'submarine') return Array.from({length:3},(_,i)=>({x:x+i,y}))
  if (weapon === 'bomber') return Array.from({length:9},(_,i)=>({x:x+i%3,y:y+Math.floor(i/3)}))
  if (weapon === 'radar') return [{ x, y }, { x: x + 1, y }, { x, y: y + 1 }, { x: x + 1, y: y + 1 }]
  if (ship?.kind === 'battleship') return Array.from({ length: 3 }, (_, i) => ({ x: x + (ship.rotation % 180 ? i : 0), y: y + (ship.rotation % 180 ? 0 : i) }))
  return [{ x, y }]
}
export function militaryHasAttack(p){return militaryActions(p).some(a=>['shot','fighter','bomber','torpedo','submarine','depthCharge'].includes(a.weapon))}
export function militarySalvoWeights(targets,shots){const hits=new Set(shots.filter(s=>s.result==='hit').map(cellKey)),known=targets.filter(c=>hits.has(cellKey(c))).length;return targets.map(c=>known===0||known===3?1/3:hits.has(cellKey(c))?(1-(3-known)*((known===1?.35:.45)))/known:(known===1?.35:.45))}
function defeated(p) { return p.ships.filter(s => s.kind !== 'decoy').every(militarySunk) || !militaryHasAttack(p) }
export function militaryAttack(room, actor, action, now = Date.now(), random = Math.random) {
  normalizeMilitaryDamage(room)
  if (room.phase !== 'BATTLE' || room.turn !== actor) throw new Error('尚未轮到你行动')
  const attacker = room.players[actor], defender = room.players[1 - actor], ship = attacker.ships.find(s => s.id === action.shipId)
  if(room.mineActions&&action.weapon!=='mine')throw Error('请放置本回合第二颗水雷')
  const allowed = militaryActions(attacker).find(a => a.shipId === action.shipId && a.weapon === action.weapon && (!['fighter','bomber','torpedo'].includes(a.weapon) || a.plane === (action.plane || 0)))
  if (!allowed || !ship) throw new Error('该舰船部件已损毁或飞机已损失')
  const targets = militaryTargetCells(action, ship)
  if (!targets.every(c => militaryInside(c.x, c.y))) throw new Error('目标区域不能超出海域')
  const origin = militaryComponents(ship).find(p => p.active && (action.weapon !== 'fighter' || p.index === action.plane)) || { x: ship.x, y: ship.y }
  const cells = [], route = [], coverage = new Set(militaryCoverage(defender).map(cellKey))
  let destroyed = false, damageTargets = targets, torpedoes=[], backfire=[], torpedoLaunched=false
  if(action.weapon==='mine') {
    if(targets.length>2-(room.mineActions||0)||targets.length>10-(ship.minesUsed||0)||new Set(targets.map(cellKey)).size!==targets.length)throw Error('每回合最多布置两颗水雷，且不能超过剩余数量')
    if(attacker.ships.some(s=>targets.some(c=>c.x>=s.x&&c.x<s.x+s.w&&c.y>=s.y&&c.y<s.y+s.h))||attacker.mines.some(c=>cellKey(c)===cellKey(targets[0]))||attacker.shots.some(c=>cellKey(c)===cellKey(targets[0]))) throw Error('水雷不能与舰船、已有水雷或已受击格重叠')
    if(targets.some(c=>attacker.mines.some(m=>cellKey(m)===cellKey(c))||attacker.shots.some(s=>cellKey(s)===cellKey(c))))throw Error('水雷位置无效')
    attacker.mines.push(...targets.map(c=>({...c})));ship.minesUsed=(ship.minesUsed||0)+targets.length;damageTargets=[]
  } else if(action.weapon==='depthCharge') {
    const e=depthChargeAttack(attacker,defender,ship,action,militaryComponents(ship).filter(p=>p.active).map(p=>p.index),random);cells.push(...e.cells);route.push(...e.route);backfire.push(...e.backfire);torpedoes=e.torpedoes;destroyed=e.destroyed;damageTargets=[]
  } else if(action.weapon==='submarine') {
    const previous=attacker.inventory.submarine;attacker.inventory.submarine=1;defender.inventory.mine=defender.mines.length
    const sim={phase:'BATTLE',turn:actor,players:room.players,events:[],_sequence:0};submarineAttack(sim,{...action,targets},actor,now);attacker.inventory.submarine=previous||0
    const e=sim.events.find(e=>e.weapon==='submarine');cells.push(...e.cells);route.push(...e.route);torpedoes=e.torpedoes;destroyed=e.destroyed;damageTargets=[]
    if(destroyed)for(let i=0;i<ship.w*ship.h;i++){
      const c={x:ship.x+i%ship.w,y:ship.y+Math.floor(i/ship.w)}
      if(!ship.hits.includes(cellKey(c)))ship.hits.push(cellKey(c))
      if(!attacker.shots.some(s=>cellKey(s)===cellKey(c))){const shot={...c,result:'hit'};attacker.shots.push(shot);backfire.push(shot)}
    }
  }
  if (action.weapon === 'radar') {
    for (const c of targets) {
      const occupied = !militaryJamming(defender).some(p=>cellKey(p)===cellKey(c)) && defender.ships.some(s => c.x >= s.x && c.x < s.x + s.w && c.y >= s.y && c.y < s.y + s.h)
      attacker.radar = attacker.radar.filter(p => cellKey(p) !== cellKey(c)); attacker.radar.push({ ...c, occupied })
    }
    damageTargets = []
  } else if (['fighter', 'bomber', 'torpedo'].includes(action.weapon)) {
    damageTargets = []
    const end = action.x + (action.weapon === 'fighter' ? 6 : action.weapon==='bomber'?3:1)
    for (let x = 0; x <= end; x++) {
      const c = { x, y:action.y }; route.push(c)
      if (coverage.has(cellKey(c))) { destroyed = true; break }
      if(action.weapon==='torpedo' && x===action.x)torpedoLaunched=true
      if (targets.some(t => t.x===c.x)) damageTargets.push(...targets.filter(t=>t.x===c.x))
    }
    if(action.weapon==='torpedo') {damageTargets=[];if(torpedoLaunched)for(let x=action.x;x<12;x++){const c={x,y:action.y};damageTargets.push(c);if(defender.mines.some(m=>cellKey(m)===cellKey(c))||defender.ships.some(s=>x>=s.x&&x<s.x+s.w&&c.y>=s.y&&c.y<s.y+s.h))break}}
    if(action.weapon==='fighter') damageTargets=sampleMilitaryAir(targets,defender.shots,4+Math.floor(random()*2),random).filter(c=>damageTargets.some(p=>cellKey(p)===cellKey(c)))
    if(action.weapon==='bomber') damageTargets=sampleMilitaryAir(targets,defender.shots,5+Math.floor(random()*2),random).filter(c=>damageTargets.some(p=>cellKey(p)===cellKey(c)))
    if (destroyed) ship.lostPlanes = [...new Set([...(ship.lostPlanes || []), action.plane || 0])]
    else route.push(...route.slice(0, -1).reverse())
  } else if (ship.kind === 'battleship') {
    const n = militaryComponents(ship).filter(p => p.active).length
    const pool=[...targets],calibration=new Map(targets.map((c,i)=>[cellKey(c),militarySalvoWeights(targets,defender.shots)[i]]));damageTargets=[]
    while(damageTargets.length<n){const weights=pool.map(c=>calibration.get(cellKey(c)));let r=random()*weights.reduce((a,b)=>a+b,0),index=weights.length-1;for(let i=0;i<weights.length;i++){r-=weights[i];if(r<=0){index=i;break}}damageTargets.push(pool.splice(index,1)[0])}
  }
  for (const c of damageTargets) {
    const previous=defender.shots.find(s => cellKey(s) === cellKey(c))
    if (previous) {if(action.weapon==='shot')cells.push({...c,result:'miss'});else if(action.weapon==='torpedo'&&defender.ships.some(s=>c.x>=s.x&&c.x<s.x+s.w&&c.y>=s.y&&c.y<s.y+s.h))cells.push({...previous});continue}
    const mine=defender.mines.findIndex(m=>cellKey(m)===cellKey(c))
    if(mine>=0){defender.mines.splice(mine,1);const shot={...c,result:'mine'};defender.shots.push(shot);cells.push(shot);if(['shot','fighter','bomber'].includes(action.weapon)){const s=attacker.ships.find(s=>c.x>=s.x&&c.x<s.x+s.w&&c.y>=s.y&&c.y<s.y+s.h);if(!attacker.shots.some(p=>cellKey(p)===cellKey(c))){if(s)s.hits.push(cellKey(c));const rebound={...c,result:s?'hit':'miss'};attacker.shots.push(rebound);backfire.push(rebound)}}continue}
    const target = defender.ships.find(s => c.x >= s.x && c.x < s.x + s.w && c.y >= s.y && c.y < s.y + s.h)
    if (target) target.hits.push(cellKey(c))
    const shot = { ...c, result: target ? 'hit' : 'miss' }; defender.shots.push(shot); cells.push(shot)
  }
  for(const p of [attacker,defender])for(const s of p.ships)if(s.kind==='submarine'&&s.hits.includes(cellKey(militaryCell(s,0,1))))for(let i=0;i<s.w*s.h;i++){const c={x:s.x+i%s.w,y:s.y+Math.floor(i/s.w)};if(!s.hits.includes(cellKey(c))){s.hits.push(cellKey(c));const shot={...c,result:'hit'};p.shots.push(shot);(p===defender?cells:backfire).push(shot)}}
  const event = { id: ++room.sequence, player: actor, weapon: action.weapon, shipId: ship.id, plane: action.plane || 0, origin, cells, targets, route, torpedoes, backfire, destroyed, x: action.x, y: action.y, text: `${attacker.name} · ${allowed.name}${destroyed ? (action.weapon==='depthCharge'?'：突击舰沉没':'：飞机被击落') : action.weapon === 'radar' ? '：扫描完成' : `：${cells.filter(c => c.result === 'hit').length} 格命中`}` }
  room.events.push(event)
  if (defeated(defender) || defeated(attacker)) { room.phase = 'FINISHED'; room.winner = defeated(defender) ? actor : 1 - actor; room.deadline = 0; room.finishReason = 'MILITARY_DEFEAT' }
  else {const count=action.weapon==='mine'?(room.mineActions||0)+targets.length:0,again=count===1&&militaryActions(attacker).some(a=>a.weapon==='mine');room.mineActions=again?count:0;room.turn=again?actor:1-actor;room.deadline=now+30000;room.nextAiAt=now+4500}
  if(room.presentationSync&&room.phase==='BATTLE'){room.presentationId=room.sequence;room.presentationUntil=now+15000;room.deadline=room.presentationUntil+30000}
  return room
}
export function militaryLocalAction(current, action, now = Date.now()) {
  const room = copy(current), p = room.players[0]
  if (action.type === 'DEPLOY') {
    if (room.phase !== 'PLACEMENT' || p.ready) throw new Error('当前不能调整舰队')
    const error = validateMilitaryFleet(action.ships, action.ready)
    if (error) throw new Error(error)
    p.ships = copy(action.ships).map(s => ({ ...s, hits: [], lostPlanes: [], minesUsed:0 })); p.coins = 300 - p.ships.reduce((n, s) => n + MILITARY_SHIPS[s.kind].price, 0)
    if (action.ready) { p.ready = true; p.stage = 'READY'; room.phase = 'BATTLE'; room.battleStarted = true; room.deadline = now + 30000; if(room.presentationSync){room.presentationId=room.sequence;room.presentationUntil=now+15000;room.deadline=room.presentationUntil+30000} }
  } else if(action.type==='PRESENTED'){if(action.presentationId===room.presentationId){room.presentationUntil=0;room.deadline=now+30000;room.nextAiAt=now+1200}}
  else if (action.type === 'ATTACK') militaryAttack(room, 0, action, now)
  else if (action.type === 'LEAVE') { room.phase = 'FINISHED'; room.winner = 1 }
  return room
}
export function tickMilitaryGame(current, now = Date.now()) {
  const normalized=normalizeMilitaryDamage(copy(current))
  if(JSON.stringify(normalized)!==JSON.stringify(current))current=normalized
  if (current.phase === 'PLACEMENT' && now >= current.deadline) return { ...copy(current), phase: 'FINISHED', winner: 1, deadline: 0, finishReason: 'PREPARATION_TIMEOUT' }
  if (current.phase !== 'BATTLE') return current
  if(current.players.some(defeated))return {...copy(current),phase:'FINISHED',winner:defeated(current.players[0])?1:0,deadline:0,finishReason:'MILITARY_DEFEAT'}
  const room = copy(current)
  if(room.presentationUntil>now)return current
  if(room.presentationUntil){room.presentationUntil=0;room.deadline=now+30000;room.nextAiAt=now+1200}
  if (room.turn === 1 && now >= (room.nextAiAt || 0)) {
    const actions = militaryActions(room.players[1])
    if (!actions.length) { room.phase = 'FINISHED'; room.winner = 0; return room }
    const a = chooseMilitaryAction(room,actions,militaryTargetCells)
    if(!a)return room
    militaryAttack(room,1,a,now)

  } else if (now >= room.deadline) { room.mineActions=0; room.turn = 1 - room.turn; room.deadline = now + 30000; room.nextAiAt = now + 2200 }
  return room
}
