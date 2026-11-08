import {markSupportShots,recordNavalShot} from './navalShotMarkers.js'
import {militaryJamming} from './navalMilitary.js'
const copy = value => JSON.parse(JSON.stringify(value))
const weapons = new Set(['shot', 'fighter', 'bomber', 'torpedo', 'radar', 'submarine','mine','depthCharge','repairSupport'])

export function replayAttacks(detail) {
  return (detail?.events || []).filter(event => weapons.has(event.weapon) && [0, 1].includes(event.player))
}

export function replaySnapshot(detail, count) {
  const attacks = replayAttacks(detail)
  const players = (detail.players || []).map((person, index) => {
    const mines = new Map((person.minesRemaining || []).map(cell => [`${cell.x},${cell.y}`, copy(cell)]))
    for (const event of attacks) for (const cell of (event.player !== index ? event.cells : event.backfire) || []) if (cell.result === 'mine') mines.set(`${cell.x},${cell.y}`, { x: cell.x, y: cell.y })
    if(detail.ruleSet==='MILITARY')mines.clear()
    const aa = new Set(person.antiAirRemaining || [])
    for (const event of attacks) if (event.player !== index && event.row != null) aa.add(event.row)
    return { ...copy(person), ships: (person.ships || []).map(ship => ({ ...copy(ship), hits: [], ...(detail.ruleSet === 'MILITARY' ? {lostPlanes:[],minesUsed:0} : {}) })), shots: [], scrapMines:[], mines: [...mines.values()], aa: [...aa], inventory: copy(person.itemsPurchased || {}), radar: [] }
  })
  // Archives store final poses; reverse towing before reconstructing the timeline.
  for(const event of [...attacks].reverse())if(event.support?.mode==='tow'&&event.support.before){
    const person=players[event.player],index=person.ships.findIndex(s=>s.id===event.support.targetShipId)
    if(index>=0)person.ships[index]={...copy(event.support.before),hits:[],lostPlanes:[],minesUsed:0}
  }
  for (const event of attacks.slice(0, Math.max(0, count))) {
    const target = players[1 - event.player], attacker = players[event.player]
    if (!target || !attacker) continue
    if(event.weapon==='repairSupport'){
      const support=event.support||{}
      markSupportShots(attacker,support)
      if(support.after){const i=attacker.ships.findIndex(s=>s.id===support.targetShipId);if(i>=0)attacker.ships[i]=copy(support.after)}
      if(support.mode==='scrapMine')attacker.scrapMines=[...(attacker.scrapMines||[]),...copy(support.cells||[])]
      continue
    }
    if (detail.ruleSet==='MILITARY' && event.destroyed) {const source=attacker.ships.find(s=>s.id===event.shipId);if(source){if(event.weapon==='submarine'||event.weapon==='depthCharge'){for(let i=0;i<source.w*source.h;i++){const c={x:source.x+i%source.w,y:source.y+Math.floor(i/source.w)},key=`${c.x},${c.y}`;if(!source.hits.includes(key))source.hits.push(key);if(!attacker.shots.some(s=>s.x===c.x&&s.y===c.y))attacker.shots.push({...c,result:'hit'})}}else source.lostPlanes.push(event.plane||0)}}
    if (detail.ruleSet!=='MILITARY' && event.weapon !== 'shot') attacker.inventory[event.weapon] = Math.max(0, (attacker.inventory[event.weapon] || 0) - 1)
    if(event.weapon==='mine'){const mines=event.targets?.length?event.targets:[{x:event.x,y:event.y}];attacker.mines.push(...copy(mines));const source=attacker.ships.find(s=>s.id===event.shipId);if(source)source.minesUsed+=mines.length;continue}
    if (event.row != null) target.aa = target.aa.filter(row => row !== event.row)
    if (event.weapon === 'radar') { attacker.radar = detail.ruleSet==='MILITARY' ? (event.targets||[]).map(c=>({...c,occupied:!militaryJamming(target).some(p=>p.x===c.x&&p.y===c.y)&&target.ships.some(s=>c.x>=s.x&&c.x<s.x+s.w&&c.y>=s.y&&c.y<s.y+s.h)})) : copy(event.radar || []); continue }
    for (const [person,cells] of [[target,event.cells],[attacker,event.backfire]]) for (const cell of cells || []) {
      if (!['hit', 'miss', 'mine'].includes(cell.result)) continue
      recordNavalShot(person,copy(cell))
      person.scrapMines=(person.scrapMines||[]).filter(m=>m.x!==cell.x||m.y!==cell.y)
      person.mines = person.mines.filter(mine => mine.x !== cell.x || mine.y !== cell.y)
      if (cell.result === 'hit') {
        const ship = person.ships.find(ship => cell.x >= ship.x && cell.x < ship.x + ship.w && cell.y >= ship.y && cell.y < ship.y + ship.h)
        if (ship&&!ship.hits.includes(`${cell.x},${cell.y}`)) ship.hits.push(`${cell.x},${cell.y}`)
      }
    }
  }
  return { players, attacks, count: Math.min(Math.max(0, count), attacks.length) }
}
