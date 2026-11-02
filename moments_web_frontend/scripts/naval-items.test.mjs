import test from 'node:test'
import assert from 'node:assert/strict'
import { createLocalGame, localAction, tickLocalGame, submarineFootprint, shipCells, localBattleResult } from '../src/games/navalEngine.js'
import { navalViewportState } from '../src/games/navalViewport.js'
const now = 1000
function battle() { let r = localAction(createLocalGame(now), { type: 'LAYOUT_READY' }, now); return localAction(r, { type: 'READY' }, now) }
const hull = [{ x: 4,y: 5 }, { x: 5,y: 5 }, { x: 6,y: 5 }]

test('classic torpedo is blocked by destroyed hull without damaging it twice',()=>{
  const r=battle();r.players[0].inventory.torpedo=1;r.players[1].aa=[];r.players[1].mines=[];
  r.players[1].ships=[{id:'old',x:3,y:4,w:1,h:3,hits:['3,4']},{id:'next',x:7,y:4,w:1,h:3,hits:[]}];r.players[1].shots=[{x:3,y:4,result:'hit'}];
  const result=localAction(r,{type:'ATTACK',weapon:'torpedo',x:1,y:4},now+1),e=result.events.findLast(e=>e.weapon==='torpedo');assert.equal(e.cells.at(-1).x,3);assert.equal(result.players[1].ships[0].hits.length,1);assert.equal(result.players[1].ships[1].hits.length,0);
})

test('submarine entry mines damage only one, two or three cells actually inside the sea', () => {
  for (const mineX of [0,1,2,5]) {
    const room=battle(); room.players[0].inventory.submarine=1; room.players[1].mines=[{x:mineX,y:5}]; room.players[1].inventory.mine=1
    const result=localAction(room,{type:'ATTACK',weapon:'submarine',targets:hull},now+1), attack=result.events.findLast(e=>e.weapon==='submarine')
    assert(attack.destroyed); assert.equal(attack.cells.length,Math.min(3,mineX+1)); assert.equal(attack.route.at(-1).x,mineX-2)
    assert(attack.cells.every(cell=>cell.x>=0&&cell.x<=mineX)); assert.deepEqual(attack.torpedoes,[]); assert.equal(result.players[0].shots.length,0)
  }
})

test('fighter single-row random strafing and single/double-row AA obey the chosen row', () => {
  for(const random of [.1,.9]) for(const aa of [[],[0],[1],[0,1],[1,0]]) {
    const room=battle(); room.players[0].inventory.fighter=1; room.players[1].mines=[]; room.players[1].aa=[...aa]; room.players[1].inventory.antiAir=aa.length
    const saved=Math.random; let result
    try { Math.random=()=>random; result=localAction(room,{type:'ATTACK',weapon:'fighter',x:0,y:0},now+1) } finally { Math.random=saved }
    const attack=result.events.findLast(e=>e.weapon==='fighter'), selected=random<.5?0:1
    assert.equal(attack.y,selected)
    if(!aa.length) { assert.equal(attack.cells.length,8); assert(attack.cells.every(cell=>cell.y===selected)) }
    else { assert.equal(attack.cells.length,0); assert.equal(attack.row,aa.includes(selected)?selected:aa[0]); assert.deepEqual(result.players[1].aa,aa.filter(row=>row!==attack.row)); assert.equal(result.players[1].inventory.antiAir,aa.length-1) }
  }
})

test('mines reflect surface attacks at the same logical coordinate, but not torpedoes or submarines', () => {
  for (const weapon of ['shot','fighter','bomber','torpedo']) {
    const room=battle(); room.players[0].inventory[weapon]=1; room.players[1].aa=[]
    room.players[0].ships=[{id:'own',x:0,y:0,w:1,h:1,hits:[]},{id:'reserve',x:11,y:11,w:1,h:1,hits:[]}]
    room.players[1].ships=[{id:'enemy',x:11,y:10,w:1,h:1,hits:[]}]
    room.players[1].mines=Array.from({length:9},(_,i)=>({x:i%3,y:Math.floor(i/3)})); room.players[1].inventory.mine=9
    const result=localAction(room,{type:'ATTACK',weapon,x:0,y:0},now+1), event=result.events.findLast(e=>e.weapon===weapon)
    if (weapon==='torpedo') { assert.equal(event.backfire.length,0); assert.equal(result.players[0].shots.length,0) }
    else { assert(event.backfire.length>0); assert.equal(event.backfire.length,event.cells.filter(cell=>cell.result==='mine').length); for(const cell of event.backfire) assert(result.players[0].shots.some(shot=>shot.x===cell.x&&shot.y===cell.y)); assert(result.players[0].ships[0].hits.length<=1) }
  }
  const room=battle(); room.players[0].inventory.submarine=1; room.players[1].aa=[]; room.players[1].mines=[{x:0,y:5}]
  const result=localAction(room,{type:'ATTACK',weapon:'submarine',targets:hull},now+1)
  assert.equal(result.players[0].shots.length,0)
})

test('mine reflection destroys the final attacking ship and ends the game', () => {
  const room=battle(); room.players[1].aa=[]; room.players[0].ships=[{id:'last',x:0,y:0,w:1,h:1,hits:[]}]; room.players[1].ships=[{id:'enemy',x:11,y:11,w:1,h:1,hits:[]}]; room.players[1].mines=[{x:0,y:0}]
  const result=localAction(room,{type:'ATTACK',weapon:'shot',x:0,y:0},now+1)
  assert.equal(result.phase,'FINISHED'); assert.equal(result.winner,1)
})

test('battle banner and turn handoff wait for presentation acknowledgement before clock or AI starts', () => {
  let room=createLocalGame(now); room.presentationSync=true
  room=localAction(room,{type:'LAYOUT_READY'},now); room=localAction(room,{type:'READY'},now)
  assert(room.presentationUntil); const id=room.presentationId
  assert.deepEqual(tickLocalGame(room,now+6000),room)
  assert.throws(()=>localAction(room,{type:'ATTACK',x:0,y:0},now+1000))
  assert(localAction(room,{type:'PRESENTED',presentationId:id+1},now+6000).presentationUntil)
  room=localAction(room,{type:'PRESENTED',presentationId:id},now+6000)
  assert.equal(room.deadline,now+6000+30000)
  room.players[1].mines=[]
  const occupied=new Set(room.players[1].ships.flatMap(shipCells).map(cell=>`${cell.x},${cell.y}`))
  let aim; for(let y=0;y<12&&!aim;y++) for(let x=0;x<12&&!aim;x++) if(!occupied.has(`${x},${y}`)) aim={x,y}
  room=localAction(room,{type:'ATTACK',...aim},now+7000)
  assert.equal(room.turn,1); assert(room.presentationUntil)
  assert.equal(tickLocalGame(room,now+12000).events.length,room.events.length)
  room=localAction(room,{type:'PRESENTED',presentationId:room.presentationId},now+12000)
  assert.equal(tickLocalGame(room,now+12500).events.length,room.events.length)
  assert(tickLocalGame(room,now+14000).events.length>room.events.length)
})

test('human hits allow immediate chained attacks but a miss gates the next AI turn', () => {
  let room = battle(); room.presentationSync = true; room.players[1].aa = []; room.players[1].mines = []
  const target = room.players[1].ships.find(ship => ship.w * ship.h > 1), cells = shipCells(target)
  room = localAction(room, { type: 'ATTACK', weapon: 'shot', ...cells[0] }, now + 10)
  assert.equal(room.turn, 0); assert.equal(room.presentationUntil || 0, 0); assert.equal(room.deadline, now + 10 + 30000)
  room = localAction(room, { type: 'ATTACK', weapon: 'shot', ...cells[1] }, now + 11)
  assert.equal(room.turn, 0); assert.equal(room.presentationUntil || 0, 0)
  const occupied = new Set(room.players[1].ships.flatMap(shipCells).map(cell => `${cell.x},${cell.y}`))
  const water = Array.from({ length: 144 }, (_, i) => ({ x: i % 12, y: Math.floor(i / 12) })).find(cell => !occupied.has(`${cell.x},${cell.y}`))
  room = localAction(room, { type: 'ATTACK', weapon: 'shot', ...water }, now + 12)
  assert.equal(room.turn, 1); assert(room.presentationUntil)
  assert.deepEqual(tickLocalGame(room, now + 500), room)
})

test('history keeps complete attack records beyond the live event window', () => {
  const room=battle(); room.events=Array.from({length:130},(_,i)=>({id:i,player:0,weapon:'radar',cells:[]}))
  room.players[0].coins=220
  const result=localBattleResult(room)
  assert.equal(result.events.length,130); assert.equal(result.players[0].itemsUsed.radar,130); assert.equal(result.players[0].coinsSpent,80)
})

test('AI extends a confirmed hit axis rather than shooting perpendicular neighbors', () => {
  for(let n=0;n<30;n++) {
    const r=battle(), ship=r.players[0].ships.find(s=>s.w*s.h===4), cells=shipCells(ship)
    ship.hits=cells.slice(1,3).map(c=>`${c.x},${c.y}`)
    r.players[0].shots=cells.slice(1,3).map(c=>({...c,result:'hit'})); r.turn=1; r._aiNextAt=now
    const result=tickLocalGame(r,now+1), attack=result.events.findLast(e=>e.player===1&&e.weapon==='shot')
    assert(attack); assert(attack.cells.every(c=>[cells[0],cells[3]].some(p=>p.x===c.x&&p.y===c.y)))
  }
})
test('submarine validates hull before spending inventory, including forged targets', () => {
  const r = battle(); r.players[0].inventory.submarine = 3
  for (const targets of [null, [], [{ x: 0,y: 0 }], [hull[0],hull[0],hull[1]], [{ x: 4,y: 5 },{ x: 5,y: 6 },{ x: 6,y: 5 }], [{ x: 10,y: 5 },{ x: 11,y: 5 },{ x: 12,y: 5 }]]) assert.throws(() => localAction(r,{ type: 'ATTACK',weapon: 'submarine',targets },now))
  assert.equal(r.players[0].inventory.submarine,3)
  assert.deepEqual(submarineFootprint([...hull].reverse()),hull)
})
test('submarine launches independently upward and downward from its hull and spends exactly one', () => {
  const origins = new Set()
  for (let n=0;n<80;n++) {
    const r = battle(); r.players[0].inventory.submarine=3; r.players[1].mines=[]; r.players[1].aa=[5]
    const result=localAction(r,{ type: 'ATTACK',weapon: 'submarine',targets:hull },now)
    const e=result.events.findLast(e=>e.weapon==='submarine')
    assert.equal(e.destroyed,false); assert.equal(e.torpedoes.length,2); assert.equal(result.players[0].inventory.submarine,2)
    const [up,down]=e.torpedoes
    assert(up.slice(1).every(c=>c.y<up[0].y && c.x===up[0].x)); assert(down.slice(1).every(c=>c.y>down[0].y && c.x===down[0].x))
    assert(hull.some(c=>c.x===up[0].x)); assert(hull.some(c=>c.x===down[0].x)); origins.add(up[0].x+','+down[0].x)
    assert.equal(result.players[1].aa.length,1,'AA does not stop submarines')
    assert.equal(new Set(result.players[1].shots.map(c=>c.x+','+c.y)).size,result.players[1].shots.length)
    assert.equal(result.deadline,now+30000)
  }
  assert([...origins].some(pair=>pair[0]===pair[2])); assert([...origins].some(pair=>pair[0]!==pair[2]))
})
test('whole moving hull detects mines en route, explodes once and never launches', () => {
  const r=battle(); r.players[0].inventory.submarine=1; r.players[1].ships=[]; r.players[1].mines=[{ x: 1,y: 5 }]; r.players[1].inventory.mine=1
  const result=localAction(r,{ type:'ATTACK',weapon:'submarine',targets:hull },now), e=result.events.at(-1)
  const impact=result.events.findLast(e=>e.weapon==='submarine')
  assert(impact.destroyed); assert.deepEqual(impact.torpedoes,[]); assert.equal(result.players[1].mines.length,0); assert.equal(impact.cells.length,2); assert.equal(impact.cells.filter(cell=>cell.result==='mine').length,1)
})
test('coating purchase is validated, required before ready, refunded safely and auto-deployed at timeout', () => {
  let r=localAction(createLocalGame(now),{type:'LAYOUT_READY'},now), ships=r.players[0].ships
  const action={type:'DEPLOY',ships,inventory:{stealth:1},coatedShips:[ships[0].id]}
  r=localAction(r,action,now); assert.equal(r.players[0].coins,290); assert(r.players[0].ships[0].stealth)
  assert.throws(()=>localAction(r,{...action,coatedShips:['forged']},now)); assert.throws(()=>localAction(r,{...action,coatedShips:[ships[0].id,ships[0].id]},now))
  assert.throws(()=>localAction(r,{...action,inventory:{stealth:6}},now))
  const bare=localAction(r,{...action,coatedShips:[]},now); assert.throws(()=>localAction(bare,{type:'READY'},now))
  const automatic=tickLocalGame(bare,bare.deadline+1); assert.equal(automatic.players[0].ships.filter(s=>s.stealth).length,1)
  const refunded=localAction(r,{...action,inventory:{stealth:0},coatedShips:[]},now); assert.equal(refunded.players[0].coins,300); assert(!refunded.players[0].ships.some(s=>s.stealth))
})
test('radar excludes coated ships while cannon still damages them and JSON preserves coating', () => {
  const r=battle(), defender=r.players[1], ship=defender.ships[0]; ship.stealth=true
  r.players[0].inventory.radar=1; const x=Math.min(9,ship.x),y=Math.min(9,ship.y)
  const result=localAction(JSON.parse(JSON.stringify(r)),{type:'ATTACK',weapon:'radar',x,y},now)
  for(const c of shipCells(ship).filter(c=>c.x>=x&&c.x<x+3&&c.y>=y&&c.y<y+3)) assert.equal(result.players[0].radar.find(p=>p.x===c.x&&p.y===c.y).occupied,false)
  result.turn=0; const hit=localAction(result,{type:'ATTACK',weapon:'shot',x:ship.x,y:ship.y},now)
  assert.equal(hit.players[1].shots.at(-1).result,'hit')
})
test('iPhone fallback, installed standalone, native fullscreen and visual viewport remain distinct',()=>{
  const win={navigator:{userAgent:'iPhone',maxTouchPoints:1},matchMedia:()=>({matches:false}),innerWidth:844,innerHeight:390,visualViewport:{width:800,height:350,offsetTop:12,offsetLeft:0}}
  const doc={fullscreenEnabled:false,documentElement:{}}
  const result=navalViewportState(win,doc); assert(result.iosFallback); assert(!result.native); assert(result.landscape); assert.equal(result.height,350)
  win.navigator.standalone=true; assert(navalViewportState(win,doc).standalone); assert(!navalViewportState(win,doc).iosFallback)
})
