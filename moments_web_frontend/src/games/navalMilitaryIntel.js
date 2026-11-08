const key=c=>`${c.x},${c.y}`
const inside=c=>c.x>=0&&c.y>=0&&c.x<12&&c.y<12
const dirs=[[1,0],[-1,0],[0,1],[0,-1]]
const lengths={carrier:4,bomberCarrier:4,torpedoCarrier:4,battleship:3,cruiser:3,submarine:3,antiAir:2,radar:2,jammer:2,mineLayer:2,decoy:2,torpedo:2,depthCharge:2}
export const militaryAir=weapon=>['fighter','bomber','torpedo'].includes(weapon)
const halo=shape=>shape.flatMap(c=>Array.from({length:9},(_,i)=>({x:c.x+i%3-1,y:c.y+Math.floor(i/3)-1})))
const cells=s=>Array.from({length:s.w*s.h},(_,i)=>({x:s.x+i%s.w,y:s.y+Math.floor(i/s.w)}))

export function chooseMilitaryFleet(models,random=Math.random){
  // Cores pair sustainable weapons with complementary reconnaissance and protection.
  const cores=[['carrier','battleship','antiAir','radar'],['bomberCarrier','battleship','battleship','antiAir','jammer'],['torpedoCarrier','cruiser','submarine','radar','antiAir'],['submarine','battleship','cruiser','mineLayer','radar'],['carrier','bomberCarrier','cruiser','antiAir'],['battleship','battleship','cruiser','antiAir','jammer']]
  const list=[...cores[Math.floor(random()*cores.length)]],budget=[250,270,285,300][Math.floor(random()*4)],cost=()=>list.reduce((n,k)=>n+models[k].price,0)
  while(true){
    const options=Object.keys(models).filter(k=>!models[k].legacy&&cost()+models[k].price<=budget&&list.filter(t=>models[t].group===models[k].group).length<({carrier:2,medium:4,destroyer:6,decoy:3,support:2})[models[k].group])
    if(!options.length)break
    const weight=k=>(['carrier','bomberCarrier','torpedoCarrier','battleship','cruiser','submarine','depthCharge'].includes(k)?3:k==='antiAir'?4:k==='radar'?3:k==='jammer'?2:1)/(1+list.filter(t=>t===k).length*3)
    let roll=random()*options.reduce((n,k)=>n+weight(k),0),chosen=options.at(-1)
    for(const k of options){roll-=weight(k);if(roll<=0){chosen=k;break}}
    list.push(chosen)
  }
  return list
}

function hypotheses(kind,hits,shots,excluded,otherHits){
  const out=[]
  for(let y=0;y<12;y++)for(let x=0;x<12;x++)for(const[dx,dy]of dirs){
    const shape=kind==='repairTug'?[{x,y},{x:x+1,y},{x,y:y+1},{x:x+1,y:y+1}]:Array.from({length:lengths[kind]},(_,i)=>({x:x+dx*i,y:y+dy*i})),keys=new Set(shape.map(key))
    if(shape.every(c=>inside(c)&&!excluded.has(key(c))&&(!shots.has(key(c))||shots.get(key(c))==='hit')&&!otherHits.has(key(c)))&&hits.every(h=>keys.has(h)))out.push(shape)
  }
  return out
}
function disabled(kind,shape,hits,lost){
  const hit=i=>hits.includes(key(shape[i]))
  if(hits.length>=shape.length)return true
  if(kind==='battleship')return hit(0)&&hit(2)
  if(kind==='carrier')return (hit(1)||lost.has(0))&&(hit(2)||lost.has(1))
  if(['bomberCarrier','torpedoCarrier'].includes(kind))return hit(1)||hit(2)||lost.has(0)
  if(['cruiser','antiAir','radar','jammer'].includes(kind))return hit(0)
  if(kind==='torpedo')return hit(1)||lost.has(0)
  return kind==='submarine'&&hit(1)
}

export function militaryIntel(own,enemy,events=[]){
  enemy={...enemy,shots:enemy.shots.filter(c=>!c.uncertain)}
  const shots=new Map(enemy.shots.map(c=>[key(c),c.result])),allHits=new Set(enemy.shots.filter(c=>c.result==='hit').map(key)),excluded=new Set(),resolved=[],neutralized=new Set(),revealed=new Set(),located=[],vital=new Map()
  const observed=events.filter(e=>e.player===0&&e.shipId)
  for(const s of enemy.ships){
    if(s.hits.length||observed.some(e=>e.shipId===s.id))revealed.add(s.id)
    if(s.hits.length>=s.w*s.h){neutralized.add(s.id);for(const c of halo(cells(s)))excluded.add(key(c))}
    const lost=new Set(observed.filter(e=>e.shipId===s.id&&e.destroyed&&militaryAir(e.weapon)).map(e=>e.plane||0))
    if((['torpedoCarrier','bomberCarrier','torpedo'].includes(s.kind)&&lost.has(0))||(s.kind==='carrier'&&lost.has(0)&&lost.has(1)))neutralized.add(s.id)
  }
  // Infer footprints from public hits and misses, never from a live hull's hidden coordinates.
  for(const s of enemy.ships.filter(s=>s.hits.length&&s.hits.length<s.w*s.h)){
    const other=new Set([...allHits].filter(h=>!s.hits.includes(h))),possibilities=hypotheses(s.kind,s.hits,shots,excluded,other),lost=new Set(observed.filter(e=>e.shipId===s.id&&e.destroyed&&militaryAir(e.weapon)).map(e=>e.plane||0))
    if(possibilities.length&&possibilities.every(p=>disabled(s.kind,p,s.hits,lost)))neutralized.add(s.id)
    if(possibilities.length){
      const union=new Set(possibilities.flatMap(p=>p.map(key))),common=halo(possibilities[0]).map(key).filter(k=>possibilities.every(p=>halo(p).some(c=>key(c)===k)))
      for(const k of common)if(!union.has(k))excluded.add(k)
      const indices=s.kind==='battleship'?[0,2]:s.kind==='carrier'?[1,2]:['bomberCarrier','torpedoCarrier'].includes(s.kind)?[1,2]:s.kind==='depthCharge'?[0,1]:['cruiser','antiAir','radar','jammer'].includes(s.kind)?[0]:[]
      if(!neutralized.has(s.id))for(const shape of possibilities)for(const i of indices){const k=key(shape[i]);if(!shots.has(k))vital.set(k,(vital.get(k)||0)+3/possibilities.length)}
      if(new Set(possibilities.map(p=>p.map(key).sort().join(';'))).size!==1)continue
      const shape=possibilities[0],xs=shape.map(c=>c.x),ys=shape.map(c=>c.y)
      located.push({id:s.id,kind:s.kind,x:Math.min(...xs),y:Math.min(...ys),w:Math.max(...xs)-Math.min(...xs)+1,h:Math.max(...ys)-Math.min(...ys)+1,hits:s.hits})
      if(neutralized.has(s.id))resolved.push(located.at(-1))
      for(const c of halo(shape))if(!union.has(key(c)))excluded.add(key(c))
    }
  }
  const safe=new Set(),intercepts=[]
  for(const e of events.filter(e=>e.player===1&&militaryAir(e.weapon)&&e.route?.length)){
    if(e.destroyed)intercepts.push(e.route.at(-1))
    for(const c of e.destroyed?e.route.slice(0,-1):e.route)safe.add(key(c))
  }
  const unknown=enemy.ships.some(s=>!revealed.has(s.id)),nonAaHits=new Set(enemy.ships.filter(s=>revealed.has(s.id)&&s.kind!=='antiAir').flatMap(s=>s.hits)),aa=[]
  for(const s of located.filter(s=>s.kind!=='antiAir'))for(const c of cells(s))nonAaHits.add(key(c))
  const candidates=unknown?hypotheses('antiAir',[],shots,excluded,nonAaHits):[]
  for(const s of enemy.ships.filter(s=>s.kind==='antiAir'&&revealed.has(s.id)&&!neutralized.has(s.id)))candidates.push(...hypotheses('antiAir',s.hits,shots,excluded,new Set([...allHits].filter(h=>!s.hits.includes(h)))))
  for(const shape of candidates){const coverage=halo(shape);if(shots.get(key(shape[0]))!=='hit'&&!coverage.some(c=>safe.has(key(c))))aa.push({head:shape[0],coverage:new Set(coverage.map(key))})}
  const threats=intercepts.filter(c=>aa.some(a=>a.coverage.has(key(c)))),aaHeads=new Set()
  for(const a of aa)if(threats.some(c=>a.coverage.has(key(c))))aaHeads.add(key(a.head))
  const knownAa=enemy.ships.filter(s=>s.kind==='antiAir'&&revealed.has(s.id)&&!neutralized.has(s.id)).map(s=>hypotheses('antiAir',s.hits,shots,excluded,new Set([...allHits].filter(h=>!s.hits.includes(h)))).filter(p=>shots.get(key(p[0]))!=='hit'&&!halo(p).some(c=>safe.has(key(c)))))
  const flightRisk=route=>{const path=new Set(route.filter(inside).map(key)),danger=aa.filter(a=>[...path].some(k=>a.coverage.has(k)));const confirmed=knownAa.some(possible=>possible.length&&possible.every(p=>halo(p).some(c=>path.has(key(c)))))
    const exposure=threats.reduce((n,c)=>{const suspects=aa.filter(a=>a.coverage.has(key(c)));return Math.max(n,suspects.filter(a=>[...path].some(k=>a.coverage.has(k))).length/Math.max(1,suspects.length))},0)
    return {exposure,unsafe:danger.length>0,blocked:threats.some(c=>path.has(key(c))),confirmed,density:danger.length/Math.max(1,aa.length),heads:new Set(danger.map(a=>key(a.head)))}}
  const torpedoThreat=unknown||enemy.ships.some(s=>revealed.has(s.id)&&['submarine','torpedo','torpedoCarrier','depthCharge'].includes(s.kind)&&!neutralized.has(s.id)&&!observed.some(e=>e.shipId===s.id&&e.destroyed&&militaryAir(e.weapon)))
  const mineThreat=unknown||enemy.ships.some(s=>revealed.has(s.id)&&s.kind==='mineLayer'&&!neutralized.has(s.id))
  return {resolved,located,excluded,vital,neutralized,aaHeads,flightRisk,torpedoThreat,mineThreat}
}
