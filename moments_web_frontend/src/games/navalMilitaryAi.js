import {militaryIntel,militaryAir} from './navalMilitaryIntel.js'
const key=c=>`${c.x},${c.y}`
const inside=c=>c.x>=0&&c.y>=0&&c.x<12&&c.y<12
const cells=s=>Array.from({length:s.w*s.h},(_,i)=>({x:s.x+i%s.w,y:s.y+Math.floor(i/s.w)}))
export function militaryKnowledge(own,enemy,events=[]){
  const intel=militaryIntel(own,enemy,events)
  const sunk=[...enemy.ships.filter(s=>s.hits.length>=s.w*s.h),...intel.resolved],excluded=new Set(intel.excluded),shot=new Map(enemy.shots.map(c=>[key(c),c.result])),scanned=new Set(own.radar.map(key)),signals=new Set(own.radar.filter(c=>c.occupied).map(key))
  const negative=new Set(own.radar.filter(c=>!c.occupied).map(key))
  for(const s of intel.located)for(const c of cells(s))negative.delete(key(c))
  const fresh=c=>inside(c)&&!shot.has(key(c))&&!excluded.has(key(c)),valid=c=>fresh(c)&&!negative.has(key(c)),weights=new Map()
  const hits=enemy.shots.filter(c=>c.result==='hit'&&!enemy.ships.some(s=>intel.neutralized.has(s.id)&&s.hits.includes(key(c))))
  // Only hit cells and fully revealed wrecks are public; never inspect intact hidden hulls.
  let length=sunk.filter(s=>Math.max(s.w,s.h)===4).length>=2?3:4
  if(length===3&&sunk.filter(s=>Math.max(s.w,s.h)===3).length>=4)length=2
  while(length>1&&Array.from({length:144},(_,i)=>({x:i%12,y:Math.floor(i/12)})).every(c=>![[1,0],[0,1]].some(([dx,dy])=>Array.from({length},(_,j)=>({x:c.x+dx*j,y:c.y+dy*j})).every(valid))))length--
  for(let y=0;y<12;y++)for(let x=0;x<12;x++)for(const[dx,dy]of[[1,0],[0,1]]){
    const run=Array.from({length},(_,i)=>({x:x+dx*i,y:y+dy*i}));if(run.every(valid))for(const i of [...new Set([Math.floor((length-1)/2),Math.floor(length/2)])])weights.set(key(run[i]),(weights.get(key(run[i]))||0)+1)
  }
  for(const h of hits){
    const identified=enemy.ships.find(s=>s.kind==='battleship'&&s.hits.includes(key(h)))
    for(const[dx,dy]of[[1,0],[-1,0],[0,1],[0,-1]]){
      const aligned=hits.some(p=>p.x===h.x+dx&&p.y===h.y+dy),otherAxis=hits.some(p=>p.x===h.x+dy&&p.y===h.y+dx)
      if(otherAxis&&!aligned)continue
      const c={x:h.x+dx,y:h.y+dy};if(fresh(c))weights.set(key(c),Math.max(weights.get(key(c))||0,aligned?32:16))
      if(identified){const vital={x:h.x+dx*2,y:h.y+dy*2};if(fresh(vital))weights.set(key(vital),Math.max(weights.get(key(vital))||0,25))}
    }
  }
  const score=(c,revisit=false)=>fresh(c)&&(revisit||!negative.has(key(c)))?.5+Math.min(1,(weights.get(key(c))||0)*.1)+(signals.has(key(c))?2:0)+(intel.vital.get(key(c))||0)+(intel.aaHeads.has(key(c))?4/Math.max(1,intel.aaHeads.size):0):0
  return {...intel,fresh,valid,score,shot,scanned,excluded}
}
export function salvoInclusion(weights,guns){
  return weights.map((w,i)=>guns===1?w:w+weights.reduce((sum,v,j)=>sum+(i===j?0:v*w/(1-v)),0))
}
export function chooseMilitaryAction(room,actions,targetCells,random=Math.random){
  const own=room.players[1],enemy=room.players[0],events=room.events||[],k=militaryKnowledge(own,enemy,events)
  const step=events.filter(e=>e.player===1&&['shot','fighter','bomber','torpedo','radar','mine','submarine','depthCharge'].includes(e.weapon)).length,planned=room.aiTemplate?.[step]
  const evaluate=revisit=>{
    const options=[]
    for(const a of actions.filter(a=>(!room.mineActions||a.weapon==='mine')&&(a.weapon!=='mine'||room.mineActions||k.torpedoThreat)))for(let y=0;y<12;y++)for(let x=0;x<12;x++){
      const ship=own.ships.find(s=>s.id===a.shipId),action={...a,x,y},area=targetCells(action,ship)
      if(!area.every(inside))continue
      const value=c=>k.score(c,revisit)
      let gain=0,cost=0,targets
      if(a.weapon==='mine'){
        if(own.shots.some(c=>key(c)===key({x,y}))||own.mines.some(c=>key(c)===key({x,y}))||own.ships.some(s=>cells(s).some(c=>key(c)===key({x,y}))))continue
        gain=.05+own.ships.reduce((n,s)=>n+cells(s).reduce((v,p)=>v+(x<p.x&&Math.abs(y-p.y)<=1?.12/(1+p.x-x):0),0),0)+events.filter(e=>e.player===0&&['torpedo','submarine','depthCharge'].includes(e.weapon)&&Math.abs(e.y-y)<=1).length*.15
        targets=[{x,y}]
      }else if(a.weapon==='radar'){
        gain=area.reduce((n,c)=>n+(k.valid(c)&&!k.scanned.has(key(c))?.2:0)+(k.aaHeads.has(key(c))&&!k.scanned.has(key(c))?.4:0),0)
      }else if(a.weapon==='depthCharge'){
        const route=Array.from({length:x+2},(_,col)=>({x:col,y})),collision=k.located.find(s=>route.some(c=>cells(s).some(h=>key(h)===key(c))))
        if(collision){gain=cells(collision).reduce((n,c)=>n+value(c),0)+3;cost=2.5}
        else{const active=ship.hits.length?1:2;for(const direction of [-1,1]){const index=direction<0?0:1;const local=ship.rotation===90?{x:ship.x+1-index,y:ship.y}:ship.rotation===180?{x:ship.x,y:ship.y+1-index}:ship.rotation===270?{x:ship.x+index,y:ship.y}:{x:ship.x,y:ship.y+index};if(!ship.hits.includes(key(local)))for(let col=x;col<x+2;col++)gain+=value({x:col,y:y+direction})/2}
          const safe=route.filter(c=>k.shot.has(key(c))).length/route.length;cost=(1-safe)*.45*(2.5+active*.25)
        }
      }else if(a.weapon==='submarine'){
        const route=Array.from({length:x+3},(_,col)=>({x:col,y})),safe=route.filter(c=>k.shot.has(key(c))).length/route.length
        for(let col=x;col<x+3;col++)for(const d of [-1,1]){let reach=1;for(let row=y+d;row>=0&&row<12;row+=d){const c={x:col,y:row};if(k.shot.get(key(c))==='hit')break;gain+=reach*value(c)/3;if(k.fresh(c))reach*=.85}}
        cost=k.mineThreat?(1-safe)*.55*3:0
      }else if(a.weapon==='torpedo'){
        let reach=1;for(let col=x;col<12;col++){const c={x:col,y};if(k.shot.get(key(c))==='hit')break;gain+=reach*value(c);if(k.fresh(c))reach*=.85}
      }else{
        let probabilities=area.map(()=>1)
        if(['fighter','bomber'].includes(a.weapon)){
          const n=a.weapon==='fighter'?4.5:5.5,known=area.filter(c=>k.shot.get(key(c))==='hit').length,fresh=area.length-known
          probabilities=area.map(c=>k.shot.get(key(c))==='hit'?0:Math.min(n/Math.max(1,fresh),n/area.length+known*.02))
        }else if(ship.kind==='battleship'){
          const known=area.filter(c=>k.shot.get(key(c))==='hit').length
          const weights=area.map(c=>known===0||known===3?1/3:k.shot.get(key(c))==='hit'?(1-(3-known)*(known===1?.35:.45))/known:(known===1?.35:.45))
          const guns=2-Number(ship.hits.includes(`${ship.x},${ship.y}`))-Number(ship.hits.includes(`${ship.x+ship.w-1},${ship.y+ship.h-1}`))
          probabilities=salvoInclusion(weights,guns)
        }
        gain=area.reduce((n,c,i)=>n+probabilities[i]*value(c),0)
      }
      if(militaryAir(a.weapon)){
        const end=x+(a.weapon==='fighter'?6:a.weapon==='bomber'?3:1),risk=k.flightRisk(Array.from({length:end+1},(_,col)=>({x:col,y})))
        const probability=risk.confirmed?.98:Math.max(.85*risk.exposure,risk.unsafe?.12+.12*risk.density:0)
        const remaining=actions.filter(o=>militaryAir(o.weapon)).length
        cost=probability*(1.5+2/Math.max(1,remaining)+gain)
      }
      // Reconnaissance has value only when it removes an unresolved danger hypothesis.
      if(a.weapon==='shot')gain+=area.filter(c=>k.valid(c)&&k.aaHeads.has(key(c))).length*.35
      if(gain<=0)continue
      const distance=planned&&a.weapon===planned.weapon&&a.shipId===planned.shipId?Math.abs(x-planned.x)+Math.abs(y-planned.y):Infinity
      const bonus=distance<=2?Math.min(.15,gain*.03)/(1+distance):0
      options.push({...action,...(targets?{targets}:{}),gain,cost,score:gain+bonus-cost})
    }
    return options
  }
  let options=evaluate(false)
  if(!options.some(o=>!['mine','radar'].includes(o.weapon)&&o.gain>0))options=evaluate(true)
  options.sort((a,b)=>b.score-a.score);if(!options.length)return null
  const best=options[0].score,top=options.filter(o=>o.score>=best-Math.max(.001,Math.abs(best)*.02))
  return top[Math.floor(random()*top.length)]
}
