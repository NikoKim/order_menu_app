import test from 'node:test'
import {militaryRecordTemplate,installMilitaryTemplate} from '../src/games/navalMilitaryTemplate.js'
import assert from 'node:assert/strict'
import {militaryKnowledge,chooseMilitaryAction,salvoInclusion} from '../src/games/navalMilitaryAi.js'
import {militarySalvoWeights} from '../src/games/navalMilitary.js'
import {sampleMilitaryAir} from '../src/games/navalMilitary.js'
import {createMilitaryTutorial,militaryTutorialStep,advanceMilitaryTutorial,tickMilitaryTutorial} from '../src/games/navalMilitaryTutorial.js'
import {MILITARY_SHIPS as M,militaryComponents as parts,militaryActions as actions,militaryAttack as attack,militaryTargetCells as targets,militaryJamming as jam,militaryCoverage,validateMilitaryFleet as validate,createMilitaryGame,defaultMilitaryFleet,militaryLocalAction,tickMilitaryGame} from '../src/games/navalMilitary.js'
const ship=(kind,id,x,y,rotation=0)=>({kind,id,x,y,rotation,w:rotation%180?M[kind].h:M[kind].w,h:rotation%180?M[kind].w:M[kind].h,hits:[],lostPlanes:[],minesUsed:0})
const player=ships=>({ships,name:'fleet',shots:[],radar:[],mines:[],inventory:{}})
const battle=(a,b)=>({phase:'BATTLE',turn:0,players:[player(a),player(b)],events:[],sequence:0})

test('depth-charge pricing, two independent launchers and safe route only mark the two attack cells',()=>{
 assert.equal(M.cruiser.price,25);assert.equal(M.submarine.price,50);assert.equal(M.radar.price,20);assert.equal(M.jammer.price,20);assert.equal(M.depthCharge.price,25)
 const d=ship('depthCharge','d',0,0),s=battle([d,ship('cruiser','a',3,0)],[ship('cruiser','e',9,9)])
 attack(s,0,{shipId:'d',weapon:'depthCharge',x:4,y:6},0,()=>0)
 assert.equal(s.events[0].destroyed,false);assert.deepEqual(s.players[1].shots.map(c=>[c.x,c.y]),[[4,5],[4,7]]);assert.equal(s.events[0].route.at(-1).x,4);assert.equal(s.events[0].torpedoes.length,2)
 for(const rotation of [0,90,180,270])for(const lost of [0,1]){
  const d=ship('depthCharge','d',0,0,rotation),s=battle([d,ship('cruiser','a',4,0)],[ship('cruiser','e',9,9)]),part=parts(d)[lost];d.hits=[`${part.x},${part.y}`]
  assert.equal(actions(s.players[0]).filter(a=>a.weapon==='depthCharge').length,1)
  attack(s,0,{shipId:'d',weapon:'depthCharge',x:4,y:6},0,()=>0);assert.equal(s.events[0].torpedoes.length,1);assert.equal(s.events[0].cells[0].y,lost===0?7:5)
 }
})
test('depth-charge collision sinks both whole ships and affects both cells at the collision, never the entire route',()=>{
 const s=battle([ship('depthCharge','d',0,0),ship('cruiser','a',3,0)],[ship('cruiser','target',4,5),ship('cruiser','reserve',9,9)])
 attack(s,0,{shipId:'d',weapon:'depthCharge',x:8,y:6},0,()=>0)
 assert.equal(s.players[0].ships[0].hits.length,2);assert.equal(s.players[1].ships[0].hits.length,3);assert.equal(s.events[0].destroyed,true);assert.deepEqual(s.events[0].route.at(-1),{x:3,y:6});assert.equal(s.events[0].torpedoes.length,0)
 assert(s.players[1].shots.some(c=>c.x===3&&c.y===6&&c.result==='miss'));assert(!s.players[1].shots.some(c=>c.x<3&&c.y===6));assert.equal(s.events[0].backfire.length,2)
})
test('depth-charge mine collision affects its current two cells, consumes the mine and loses the ship',()=>{
 const s=battle([ship('depthCharge','d',0,0),ship('cruiser','a',3,0)],[ship('cruiser','e',9,9)]);s.players[1].mines=[{x:2,y:6}]
 attack(s,0,{shipId:'d',weapon:'depthCharge',x:8,y:6},0,()=>0);assert.equal(s.players[1].mines.length,0);assert.equal(s.players[0].ships[0].hits.length,2);assert.deepEqual(s.events[0].cells.map(c=>c.x),[1,2]);assert(!s.players[1].shots.some(c=>c.x===0&&c.y===6));assert(!actions(s.players[0]).some(a=>a.weapon==='depthCharge'))
})

test('six destroyers and three decoys have independent shared caps',()=>{
 const fleet=['antiAir','radar','mineLayer','jammer','antiAir','radar','decoy','decoy','decoy'].map((k,i)=>ship(k,String(i),-1,-1))
 assert.equal(validate(fleet,false),'');assert(validate([...fleet,ship('jammer','extra',-1,-1)],false));assert(validate([...fleet,ship('decoy','extra',-1,-1)],false));assert.equal(M.cruiser.max,4);assert.equal(M.submarine.max,4)
})

test('located live hulls exclude only their surrounding ring, retaining intact functional cells',()=>{
 const own=player([ship('cruiser','gun',0,0)]),enemy=player([ship('carrier','live',4,2)])
 enemy.ships[0].hits=['4,2','4,5'];enemy.shots=enemy.ships[0].hits.map(h=>{const[x,y]=h.split(',').map(Number);return{x,y,result:'hit'}})
 own.radar=[{x:4,y:3,occupied:false}];const k=militaryKnowledge(own,enemy);assert(!k.neutralized.has('live'));assert.equal(k.located.length,1);assert(!k.fresh({x:5,y:3}));assert(k.fresh({x:4,y:3}));assert(k.score({x:4,y:3})>k.score({x:8,y:8}))
 const a=chooseMilitaryAction({players:[enemy,own],events:[],aiTemplate:[{weapon:'shot',shipId:'gun',x:5,y:3}]},actions(own),targets,()=>0);assert(k.fresh(a));assert.notEqual(a.x,5)
})
test('radar empty cells have zero normal damage value and are revisited only after normal targets exhaust',()=>{
 const own=player([ship('cruiser','c',0,0)]),enemy=player([ship('cruiser','e',9,9)]);own.radar=[{x:5,y:5,occupied:false}]
 const k=militaryKnowledge(own,enemy);assert.equal(k.score({x:5,y:5}),0);assert(k.score({x:5,y:5},true)>0)
 assert.notDeepEqual([chooseMilitaryAction({players:[enemy,own],events:[]},actions(own),targets,()=>0).x,chooseMilitaryAction({players:[enemy,own],events:[]},actions(own),targets,()=>0).y],[5,5])
 for(let y=0;y<12;y++)for(let x=0;x<12;x++)if(x!==5||y!==5)enemy.shots.push({x,y,result:'miss'})
 const a=chooseMilitaryAction({players:[enemy,own],events:[]},actions(own),targets,()=>0);assert.deepEqual([a.x,a.y],[5,5])
})
test('salvo expectation includes wasted old-cell draws, not a flat guns divided by three',()=>{
 const p=salvoInclusion([.3,.35,.35],2);assert(Math.abs(p.reduce((n,v)=>n+v,0)-2)<1e-9);assert(Math.abs(p[1]-(.35+.3*.35/.7+.35*.35/.65))<1e-9)
})
test('unknown anti-air is a single finite risk cost and does not lock the AI into battleships',()=>{
 const own=player([ship('carrier','c',0,0),ship('battleship','b',3,0)]),enemy=player([ship('cruiser','e',9,9)])
 const a=chooseMilitaryAction({players:[enemy,own],events:[]},actions(own),targets,()=>0);assert.equal(a.weapon,'fighter');assert(a.cost>0);assert(a.cost<a.gain);assert(Math.abs(a.score-(a.gain-a.cost))<1e-9)
})
test('submarine route history reduces the single risk cost to zero when the entire entry path is charted',()=>{
 const own=player([ship('submarine','s',0,0)]),enemy=player([ship('cruiser','e',9,9)]),room={players:[enemy,own],events:[]}
 const fixed=(a,s)=>a.x===3&&a.y===6?targets(a,s):[{x:-1,y:-1}]
 const unknown=chooseMilitaryAction(room,actions(own),fixed,()=>0);assert(unknown.cost>0)
 for(let x=0;x<=5;x++)enemy.shots.push({x,y:6,result:'miss'})
 const safe=chooseMilitaryAction(room,actions(own),fixed,()=>0);assert.equal(safe.cost,0);assert(safe.score>unknown.score)
})
test('destroyed AA stops contributing interception risk and aircraft sorties resume',()=>{
 const own=player([ship('carrier','c',0,0),ship('battleship','b',3,0)]),aa=ship('antiAir','aa',2,4),enemy=player([aa,ship('cruiser','e',9,9)])
 aa.hits=['2,4','2,5'];enemy.shots=aa.hits.map(h=>{const[x,y]=h.split(',').map(Number);return{x,y,result:'hit'}})
 const events=[{player:1,weapon:'fighter',destroyed:true,route:[{x:0,y:5},{x:1,y:5}]}],k=militaryKnowledge(own,enemy,events)
 assert.equal(k.aaHeads.size,0);const a=chooseMilitaryAction({players:[enemy,own],events},actions(own),targets,()=>0);assert.equal(a.weapon,'fighter')
})

test('winner templates select human wins, clear damage and retain a legal varied fleet and attack sequence',()=>{
 const record={ruleSet:'MILITARY',battleStarted:true,mode:'AI',winner:0,players:[player([ship('cruiser','source',3,3)]),player([ship('cruiser','other',8,8)])],events:[{player:0,shipId:'source',weapon:'shot',x:0,y:0}]}
 assert.equal(militaryRecordTemplate({...record,winner:1}),null);assert.equal(militaryRecordTemplate({...record,ruleSet:'CLASSIC'}),null)
 const t=militaryRecordTemplate(record);assert(t);const s=createMilitaryGame();assert(installMilitaryTemplate(s,t));assert.equal(validate(s.players[1].ships),'');assert.equal(s.players[1].coins,275);assert.equal(s.aiTemplate.length,1);assert.equal(record.players[0].ships[0].x,3)
 const enemy=player([ship('cruiser','e',8,8)]),own=player([ship('cruiser','source',3,3)]),room={players:[enemy,own],events:[],aiTemplate:t.actions};let a=chooseMilitaryAction(room,actions(own),targets,()=>0);assert.notDeepEqual([a.x,a.y],[0,0]);enemy.shots=[{x:9,y:9,result:'hit'}];a=chooseMilitaryAction(room,actions(own),targets,()=>0);assert.notDeepEqual([a.x,a.y],[0,0])
 assert.match(actions(player([ship('battleship','h',0,0,90)]))[0].name,/1×3/);assert.match(actions(player([ship('battleship','v',0,0)]))[0].name,/3×1/)
})

test('AI infers a disabled battleship footprint from both gun hits without reading hidden geometry',()=>{
  const own=player([ship('cruiser','own',0,0)]),enemy=player([ship('battleship','b',2,2)])
  enemy.ships[0].hits=['2,2','2,4'];enemy.shots=enemy.ships[0].hits.map(p=>{const[x,y]=p.split(',').map(Number);return {x,y,result:'hit'}})
  let k=militaryKnowledge(own,enemy);assert(k.neutralized.has('b'));assert(k.fresh({x:2,y:3}));assert(!k.fresh({x:3,y:5}));assert(k.fresh({x:4,y:5}))
  enemy.ships[0].x=9;enemy.ships[0].y=8;enemy.ships[0].rotation=90;k=militaryKnowledge(own,enemy);assert(k.fresh({x:2,y:3}));assert(k.fresh({x:9,y:8}))
  enemy.ships[0].hits=['2,2'];enemy.shots=enemy.shots.slice(0,1);assert(!militaryKnowledge(own,enemy).neutralized.has('b'))
})
test('AI investigates intercepted aircraft with a gun, and does not sacrifice another aircraft',()=>{
  const own=player([ship('carrier','c',0,0),ship('cruiser','gun',3,0)]),enemy=player([ship('antiAir','aa',8,8),ship('cruiser','enemy',10,5)])
  const events=[{player:1,weapon:'fighter',destroyed:true,route:[{x:0,y:5},{x:1,y:5},{x:2,y:5}]}],k=militaryKnowledge(own,enemy,events)
  assert(k.aaHeads.size>0);const a=chooseMilitaryAction({players:[enemy,own],events},actions(own),targets,()=>0)
  if(a.weapon==='shot')assert(k.aaHeads.has(`${a.x},${a.y}`));else assert(k.flightRisk(Array.from({length:a.x+7},(_,x)=>({x,y:a.y}))).exposure<.25)
  enemy.ships[0].x=0;enemy.ships[0].y=10;const b=chooseMilitaryAction({players:[enemy,own],events},actions(own),targets,()=>0);assert.deepEqual(b,a)
})
test('AI uses a broad aircraft attack once the flight corridor is verified safe',()=>{
  const own=player([ship('carrier','c',0,0),ship('cruiser','gun',3,0)]),enemy=player([ship('cruiser','enemy',10,5)]),events=[{player:1,weapon:'fighter',destroyed:false,route:Array.from({length:13},(_,x)=>({x,y:5}))}]
  const a=chooseMilitaryAction({players:[enemy,own],events},actions(own),targets,()=>0);assert.equal(a.weapon,'fighter');assert.equal(a.y,5)
})
test('AI stops mines only after public evidence rules out enemy torpedoes and submarines',()=>{
  const own=player([ship('mineLayer','m',6,6),ship('cruiser','gun',3,0)]),enemy=player([ship('cruiser','enemy',10,5)])
  assert(militaryKnowledge(own,enemy).torpedoThreat)
  enemy.ships[0].hits=['10,6'];enemy.shots=[{x:10,y:6,result:'hit'}]
  assert(!militaryKnowledge(own,enemy).torpedoThreat)
  const a=chooseMilitaryAction({players:[enemy,own],events:[]},actions(own).filter(a=>a.weapon==='mine'),targets,()=>0);assert.equal(a,null)
  assert.equal(chooseMilitaryAction({players:[enemy,own],events:[],mineActions:1},actions(own),targets,()=>0).weapon,'mine')
})
test('AI distinguishes certain lost capability from an ambiguous hull footprint',()=>{
  const own=player([ship('cruiser','c',0,0)]),enemy=player([ship('torpedoCarrier','t',3,3)])
  enemy.ships[0].hits=['3,4','3,5'];enemy.shots=[{x:3,y:4,result:'hit'},{x:3,y:5,result:'hit'}]
  let k=militaryKnowledge(own,enemy);assert(k.neutralized.has('t'));assert.equal(k.resolved.length,0);assert(!k.torpedoThreat);assert(k.fresh({x:3,y:3}))
  enemy.ships[0].hits=[];enemy.shots=[];k=militaryKnowledge(own,enemy,[{player:0,shipId:'t',weapon:'torpedo',destroyed:true,plane:0}]);assert(k.neutralized.has('t'));assert(!k.torpedoThreat);assert.equal(k.resolved.length,0)
})
test('AI strategy fleets retain coins and correct remaining balance, with expanded hull caps',()=>{
  const costs=new Set();for(let i=0;i<30;i++){const s=createMilitaryGame(),p=s.players[1],cost=p.ships.reduce((n,s)=>n+M[s.kind].price,0);assert.equal(p.coins,300-cost);assert(p.ships.some(s=>s.kind==='battleship'||s.kind==='cruiser'));assert(p.ships.some(s=>s.kind==='antiAir'||s.kind==='radar'));costs.add(cost)}assert([...costs].some(c=>c<300));assert.equal(M.carrier.max,2);assert.equal(M.radar.max,6)
})

test('military has eight-minute preparation and a sixty-coin torpedo carrier sharing carrier cap',()=>{const s=createMilitaryGame(1000);assert.equal(s.deadline,481000);assert.equal(M.torpedoCarrier.price,60);assert.equal(M.torpedoCarrier.h,4);assert.equal(parts(ship('torpedoCarrier','t',0,0))[0].role,'torpedo');assert.equal(validate([ship('carrier','c',-1,-1),ship('torpedoCarrier','t',-1,-1)],false),'');assert(validate([ship('carrier','c',-1,-1),ship('bomberCarrier','b',-1,-1),ship('torpedoCarrier','t',-1,-1)],false));for(let i=0;i<20;i++)assert(!defaultMilitaryFleet().some(s=>s.kind==='torpedo'))})
test('first mine keeps turn, second switches; timeout clears partial mine action',()=>{const s=battle([ship('mineLayer','m',0,0),ship('cruiser','c',3,3)],[ship('cruiser','e',9,9)]);attack(s,0,{shipId:'m',weapon:'mine',x:5,y:5},1000);assert.equal(s.turn,0);assert.equal(s.mineActions,1);assert.throws(()=>attack(s,0,{shipId:'c',weapon:'shot',x:9,y:9}));attack(s,0,{shipId:'m',weapon:'mine',x:6,y:5},2000);assert.equal(s.turn,1);assert.equal(s.mineActions,0);assert.equal(s.players[0].mines.length,2);s.turn=0;attack(s,0,{shipId:'m',weapon:'mine',x:7,y:5},3000);const next=tickMilitaryGame(s,34000);assert.equal(next.turn,1);assert.equal(next.mineActions,0)})
test('aircraft hit history adds two-percent weight per hit and samples distinct cells',()=>{for(const size of [6,9]){const area=Array.from({length:size},(_,x)=>({x,y:0})),shots=[{x:0,y:0,result:'hit'},{x:1,y:0,result:'hit'}],n=4000,counts=Array(size).fill(0);let seed=12345;const random=()=>((seed=(seed*1664525+1013904223)>>>0)/4294967296);for(let i=0;i<n;i++)counts[sampleMilitaryAir(area,shots,1,random)[0].x]++;const empty=counts.slice(2).reduce((a,b)=>a+b,0)/(size-2),old=(counts[0]+counts[1])/2;assert(empty>old*.98);assert.equal(new Set(sampleMilitaryAir(area,shots,5,random).map(c=>c.x)).size,5)}})

test('AI attacks previously radar-scanned cells rather than repeating useless radar or submarine runs',()=>{const own=player([ship('submarine','s',0,0),ship('cruiser','c',3,0),ship('radar','r',6,0)]),enemy=player([ship('cruiser','e',9,9)]);for(let y=0;y<12;y++)for(let x=0;x<12;x++){own.radar.push({x,y,occupied:false});if(x!==5||y!==5)enemy.shots.push({x,y,result:'miss'})}const a=chooseMilitaryAction({players:[enemy,own],events:[]},actions(own),targets,()=>0);assert.equal(a.weapon,'shot');assert.deepEqual([a.x,a.y],[5,5])})

test('AI fleets vary kinds and counts while retaining budget, shared caps and legal deployment',()=>{const compositions=new Set(),sizes=new Set(),kinds=new Set();for(let i=0;i<80;i++){const f=defaultMilitaryFleet();assert.equal(validate(f),'');assert(f.reduce((n,s)=>n+M[s.kind].price,0)<=300);compositions.add(f.map(s=>s.kind).sort().join(','));sizes.add(f.length);f.forEach(s=>kinds.add(s.kind))}assert(compositions.size>10);assert(sizes.size>1);assert(kinds.has('bomberCarrier'));assert(kinds.has('jammer'))})
test('two mines are placed atomically and replay target count is preserved',()=>{const s=battle([ship('mineLayer','m',0,0),ship('cruiser','c',3,3)],[ship('cruiser','e',9,9)]);attack(s,0,{shipId:'m',weapon:'mine',x:5,y:5,targets:[{x:5,y:5},{x:6,y:5}]});assert.equal(s.turn,1);assert.equal(s.players[0].mines.length,2);assert.equal(s.players[0].ships[0].minesUsed,2);s.turn=0;const old=JSON.stringify(s);assert.throws(()=>attack(s,0,{shipId:'m',weapon:'mine',x:7,y:5,targets:[{x:7,y:5},{x:5,y:5}]}));assert.equal(JSON.stringify(s),old)})
test('submarine center kills the hull, end hits do not; old gun targets only splash',()=>{for(const y of [3,4]){const s=battle([ship('cruiser','a',0,0)],[ship('submarine','sub',5,3),ship('cruiser','b',9,9)]);attack(s,0,{shipId:'a',weapon:'shot',x:5,y});assert.equal(s.players[1].ships[0].hits.length,y===4?3:1);s.turn=0;const n=s.players[1].shots.length;attack(s,0,{shipId:'a',weapon:'shot',x:5,y});assert.equal(s.events.at(-1).cells[0].result,'miss');assert.equal(s.players[1].shots.length,n)}})
test('salvo calibration sets remaining cells to thirty-five or forty-five percent',()=>{const t=[{x:0,y:0},{x:1,y:0},{x:2,y:0}];let w=militarySalvoWeights(t,[{x:0,y:0,result:'hit'}]);assert(Math.abs(w[1]-.35)<1e-9);w=militarySalvoWeights(t,[{x:0,y:0,result:'hit'},{x:1,y:0,result:'hit'}]);assert(Math.abs(w[2]-.45)<1e-9);assert(Math.abs(w.reduce((a,b)=>a+b)-1)<1e-9)})
test('AI excludes wreck halos, remembers radar, follows hit axes and is blind to intact hull coordinates',()=>{const own=player([ship('cruiser','own',0,0)]),enemy=player([ship('cruiser','hidden',8,8)]);enemy.shots=[{x:5,y:5,result:'hit'},{x:6,y:5,result:'hit'}];own.radar=[{x:0,y:0,occupied:false}];const k=militaryKnowledge(own,enemy);assert(!k.valid({x:0,y:0}));assert(k.fresh({x:0,y:0}));assert(k.score({x:7,y:5})>k.score({x:5,y:6}));const room={players:[enemy,own],events:[]},a=chooseMilitaryAction(room,actions(own),targets,()=>0);enemy.ships[0].x=1;enemy.ships[0].y=9;assert.deepEqual(chooseMilitaryAction(room,actions(own),targets,()=>0),a)})

test('military tutorial completes full desktop and mobile flows with every active weapon and battle outcome',()=>{
  for(const device of ['desktop','mobile']){
    let s=createMilitaryTutorial(1000,{device});const covered=new Set()
    for(let i=0;i<150&&!s.completed;i++){
      let step=militaryTutorialStep(s),e
      covered.add(step.id)
      if(step.mode==='read')e={type:'CONTINUE'}
      else if(step.target==='naval-type-destroyer')e={type:'TYPE',kind:step.buy}
      else if(step.buy){s.room.players[0].ships.push(ship(step.buy,step.buy,-1,-1));e={type:'PURCHASE',key:step.buy}}
      else if(['shop','fleet','tools'].includes(step.id))e={type:'PANEL',panel:step.id==='shop'?'supply':step.id}
      else if(step.id==='rotate')e={type:'ROTATE_SHIP'}
      else if(step.id==='manual')e={type:'MOVE_SHIP'}
      else if(step.id==='clear')e={type:'CLEAR_FLEET'}
      else if(step.id==='layout'){s.room.players[0].ships=defaultMilitaryFleet();e={type:'RANDOMIZE'}}
      else if(step.id==='ready'){s.room=militaryLocalAction(s.room,{type:'DEPLOY',ships:s.room.players[0].ships,ready:true},1000);e={type:'ACTION',action:{type:'DEPLOY'}}}
      else if(step.weapon){
        s=advanceMilitaryTutorial(s,{type:'WEAPON',weapon:step.weapon,kind:step.kind});step=militaryTutorialStep(s)
        s.room.presentationSync=false;s.room.presentationUntil=0
        s.room=tickMilitaryTutorial(s.room,Date.now()+100000)
        const a=actions(s.room.players[0]).find(a=>a.weapon===step.weapon&&a.kind===step.kind)
        assert(a,step.id)
        const action={type:'ATTACK',...a,x:step.cell.x,y:step.cell.y}
        attack(s.room,0,action,Date.now())
        if(step.id==='jamming')assert(s.room.players[0].radar.every(p=>!p.occupied))
        if(step.id==='intercept')assert(s.room.events.at(-1).destroyed)
        if(step.id==='mine-collision')assert.equal(s.room.players[0].shots.filter(p=>p.result==='hit').length,3)
        if(step.id==='victory')assert.equal(s.room.winner,0)
        e={type:'ACTION',action}
      }else assert.fail(step.id)
      s=advanceMilitaryTutorial(s,e)
    }
    assert(s.completed)
    for(const id of ['broadside','bomber','jamming','lay-mine','submarine','intercept','damage','mine-collision','backfire','victory'])assert(covered.has(id),id)
  }
})

test('fighters sample exactly four or five distinct cells inside six-cell target',()=>{
  for(const random of [()=>0,()=>.99]){
    const s=battle([ship('carrier','a',0,0)],[ship('cruiser','b',8,8)])
    attack(s,0,{shipId:'a',weapon:'fighter',plane:0,x:2,y:5},0,random)
    const cells=s.events[0].cells
    assert.equal(cells.length,random()===0?4:5)
    assert.equal(new Set(cells.map(c=>c.x)).size,cells.length)
    assert(cells.every(c=>c.x>=2&&c.x<8&&c.y===5))
  }
})

test('resuming an older mine-destroyed submarine fills its missing hit markers',()=>{
  const s=battle([ship('submarine','a',0,0),ship('cruiser','c',4,4)],[ship('cruiser','e',9,9)])
  s.players[0].ships[0].hits=['0,0','0,1','0,2'];s.deadline=30000
  const next=tickMilitaryGame(s,1000)
  assert.deepEqual(next.players[0].shots,[{x:0,y:0,result:'hit'},{x:0,y:1,result:'hit'},{x:0,y:2,result:'hit'}])
  assert.equal(s.players[0].shots.length,0)
})
test('catalog, budget and shared footprint caps',()=>{assert.deepEqual([M.carrier.w,M.carrier.h,M.carrier.price],[1,4,50]);assert.equal(M.battleship.h,3);assert.equal(M.submarine.price,50);assert.equal(M.decoy.price,15);for(let i=0;i<20;i++)assert.equal(validate(defaultMilitaryFleet()),'');assert.equal(validate([ship('carrier','a',-1,-1),ship('bomberCarrier','b',-1,-1)],false),'');assert.equal(validate(['cruiser','battleship','submarine','cruiser'].map((k,i)=>ship(k,String(i),-1,-1)),false),'');assert.ok(validate(['cruiser','battleship','submarine','cruiser','battleship'].map((k,i)=>ship(k,String(i),-1,-1)),false));assert.equal(validate(['antiAir','radar','mineLayer','jammer','antiAir'].map((k,i)=>ship(k,String(i),-1,-1)),false),'');assert.equal(validate(['antiAir','radar','mineLayer','jammer','antiAir','radar'].map((k,i)=>ship(k,String(i),-1,-1)),false),'');assert.ok(validate(['antiAir','radar','mineLayer','jammer','antiAir','radar','jammer'].map((k,i)=>ship(k,String(i),-1,-1)),false))})
test('carrier middle decks and bomber shared bay rotate exactly',()=>{let s=ship('carrier','a',2,3,90);assert.deepEqual(parts(s).map(p=>[p.x,p.y]),[[4,3],[3,3]]);s=ship('bomberCarrier','b',0,0);s.hits=['0,2'];assert.equal(parts(s)[0].active,false);s=ship('battleship','b',2,3,90);assert.deepEqual(parts(s).map(p=>[p.x,p.y]),[[4,3],[2,3]])})
test('fighter six targets and horizontal route independent of carrier orientation',()=>{for(const r of [0,90,180,270]){const s=battle([ship('carrier','a',0,0,r)],[ship('cruiser','b',8,8)]);attack(s,0,{shipId:'a',weapon:'fighter',plane:0,x:2,y:5});assert.ok([4,5].includes(s.events[0].cells.length));assert.deepEqual(s.events[0].route[0],{x:0,y:5});assert.deepEqual(s.events[0].route[8],{x:8,y:5});assert.equal(s.turn,1)}})
test('bomber hits five or six distinct cells inside 3x3 and safe aircraft reusable',()=>{for(const random of [()=>0,()=>.99]){const s=battle([ship('bomberCarrier','a',0,0)],[ship('carrier','b',8,8)]);attack(s,0,{shipId:'a',weapon:'bomber',plane:0,x:3,y:5},0,random);const cells=s.events[0].cells;assert.equal(cells.length,random()===0?5:6);assert.equal(new Set(cells.map(c=>`${c.x},${c.y}`)).size,cells.length);assert(cells.every(c=>c.x>=3&&c.x<6&&c.y>=5&&c.y<8));assert.equal(parts(s.players[0].ships[0])[0].active,true)}})
test('AA intercepts before covered-cell damage and permanently loses correct plane',()=>{const s=battle([ship('carrier','a',0,0)],[ship('antiAir','aa',2,4),ship('cruiser','b',8,8)]);attack(s,0,{shipId:'a',weapon:'fighter',plane:0,x:2,y:5});assert(s.events[0].destroyed);assert.equal(s.events[0].cells.length,0);assert.deepEqual(s.players[0].ships[0].lostPlanes,[0]);assert.equal(actions(s.players[0]).filter(a=>a.weapon==='fighter').length,1);s.turn=0;assert.throws(()=>attack(s,0,{shipId:'a',weapon:'fighter',plane:0,x:2,y:5}))})
test('torpedo flight reaches logical column13 and forged plane is rejected',()=>{const s=battle([ship('torpedo','a',0,0)],[ship('cruiser','b',8,8)]);assert.throws(()=>attack(s,0,{shipId:'a',weapon:'torpedo',plane:1,x:11,y:5}));attack(s,0,{shipId:'a',weapon:'torpedo',plane:0,x:11,y:5});assert.deepEqual(s.events[0].route[12],{x:12,y:5})})
test('battleship one/two surviving guns sample distinct aligned target cells',()=>{const b=ship('battleship','a',0,0,90),s=battle([b],[ship('carrier','b',8,8)]);b.hits=['2,0'];attack(s,0,{shipId:'a',weapon:'shot',x:3,y:4});assert.equal(s.events[0].cells.length,1);assert(s.events[0].cells.every(c=>c.y===4));assert.equal(s.turn,1)})
test('jammer matches screenshot: I7 bow facing H7 covers E6:H8, never its own hull',()=>{const j=ship('jammer','j',8,6,270),area=jam(player([j]));assert.equal(area.length,12);assert(area.every(c=>c.x>=4&&c.x<=7&&c.y>=5&&c.y<=7));const s=battle([ship('radar','r',0,0),ship('cruiser','c',0,8)],[j,ship('decoy','d',5,6),ship('cruiser','c2',0,8)]);attack(s,0,{shipId:'r',weapon:'radar',x:5,y:5});assert(s.players[0].radar.every(c=>!c.occupied));j.hits=['8,6'];assert.equal(jam(player([j])).length,0)})
test('minelayer survives partial damage, lays one mine per turn, max ten',()=>{const m=ship('mineLayer','m',0,0),s=battle([m,ship('cruiser','c',3,3)],[ship('cruiser','b',8,8)]);m.hits=['0,1'];for(let i=0;i<10;i++){s.turn=0;attack(s,0,{shipId:'m',weapon:'mine',x:2+i%10,y:0});assert.equal(s.turn,i%2===0?0:1)}assert.equal(s.players[0].mines.length,10);assert(!actions(s.players[0]).some(a=>a.weapon==='mine'));s.turn=0;assert.throws(()=>attack(s,0,{shipId:'m',weapon:'mine',x:5,y:1}))})
test('mine reflects surface attacks once at same logical coordinate',()=>{const s=battle([ship('cruiser','a',0,0)],[ship('cruiser','b',8,8)]);s.players[1].mines=[{x:0,y:0}];attack(s,0,{shipId:'a',weapon:'shot',x:0,y:0});assert.equal(s.events[0].cells[0].result,'mine');assert.equal(s.events[0].backfire[0].result,'hit');assert.equal(s.winner,1)})
test('submarine reuses classic partial-hull mine collision and is then unavailable',()=>{const s=battle([ship('submarine','a',0,0),ship('cruiser','c',4,4)],[ship('cruiser','b',8,8)]);s.players[1].mines=[{x:0,y:5}];attack(s,0,{shipId:'a',weapon:'submarine',x:4,y:5});assert(s.events[0].destroyed);assert.equal(s.events[0].torpedoes.length,0);assert.equal(s.events[0].cells.length,1);assert.equal(s.players[0].ships[0].hits.length,3);assert.equal(s.players[0].shots.length,3);assert.equal(s.events[0].backfire.length,3);assert(!actions(s.players[0]).some(a=>a.weapon==='submarine'))})
test('gun destruction wins with radar and decoy alive',()=>{const s=battle([ship('cruiser','a',0,0)],[ship('cruiser','b',5,5),ship('radar','r',8,8),ship('decoy','d',10,0)]);attack(s,0,{shipId:'a',weapon:'shot',x:5,y:5});assert.equal(s.winner,0);assert.equal(s.phase,'FINISHED')})
test('shared presentation gate pauses local AI until acknowledgement',()=>{let s=createMilitaryGame();s.presentationSync=true;s=militaryLocalAction(s,{type:'DEPLOY',ships:[ship('cruiser','c',0,0)],ready:true},1000);assert(s.presentationUntil>1000);const old=s;assert.equal(tickMilitaryGame(s,2000),old);s=militaryLocalAction(s,{type:'PRESENTED',presentationId:s.presentationId},3000);assert.equal(s.presentationUntil,0);assert.equal(s.deadline,33000)})
test('military torpedo stops at destroyed hull without duplicate damage or backfire',()=>{const s=battle([ship('torpedo','a',0,0)],[ship('cruiser','wreck',3,4),ship('cruiser','b',7,4),ship('cruiser','c',10,8)]);s.players[1].ships[0].hits=['3,4'];s.players[1].shots=[{x:3,y:4,result:'hit'}];attack(s,0,{shipId:'a',weapon:'torpedo',x:1,y:4});assert.deepEqual(s.events[0].cells.map(c=>c.x),[1,2,3]);assert.equal(s.events[0].cells.at(-1).result,'hit');assert.equal(s.events[0].backfire.length,0)})
test('last lost aircraft ends combat even with radar, minelayer, AA, jammer and decoy',()=>{const carrier=ship('carrier','a',0,0),s=battle([carrier,...['radar','mineLayer','jammer','antiAir','decoy'].map((k,i)=>ship(k,k,2+i*2,6))],[ship('cruiser','enemy',8,8)]);carrier.lostPlanes=[0,1];const next=tickMilitaryGame(s,0);assert.equal(next.phase,'FINISHED');assert.equal(next.winner,1)})
