const key=c=>`${c.x},${c.y}`
const inside=c=>c.x>=0&&c.y>=0&&c.x<12&&c.y<12
const hull=s=>Array.from({length:s.w*s.h},(_,i)=>({x:s.x+i%s.w,y:s.y+Math.floor(i/s.w)}))
const shipAt=(p,c)=>p.ships.find(s=>hull(s).some(h=>key(h)===key(c)))
function hit(p,c,effects){
 const victim=shipAt(p,c),mine=p.mines.findIndex(m=>key(m)===key(c));if(mine>=0)p.mines.splice(mine,1)
 if(victim&&!victim.hits.includes(key(c)))victim.hits.push(key(c))
 const shot={...c,result:victim?'hit':mine>=0?'mine':'miss'},old=p.shots.find(s=>key(s)===key(c))
 if(!old)p.shots.push(shot)
 else if(victim)old.result='hit'
 if(!effects.some(s=>key(s)===key(c)))effects.push(shot)
}
function sink(p,s,effects){for(const c of hull(s))hit(p,c,effects)}
export function depthChargeAttack(attacker,defender,ship,action,activeSides,random=Math.random){
 const route=[],cells=[],backfire=[],torpedoes=[];let destroyed=false
 for(let x=-1;x<=action.x;x++){
  const anchor={x,y:action.y};route.push(anchor)
  const footprint=[anchor,{x:x+1,y:action.y}].filter(inside)
  const mine=footprint.some(c=>defender.mines.some(m=>key(m)===key(c))),collision=footprint.map(c=>shipAt(defender,c)).find(Boolean)
  if(mine||collision){
   destroyed=true;sink(attacker,ship,backfire)
   if(collision)sink(defender,collision,cells)
   for(const c of footprint)hit(defender,c,cells)
   break
  }
 }
 if(!destroyed)for(const side of activeSides){
  const start={x:action.x+Math.floor(random()*2),y:action.y},target={x:start.x,y:start.y+(side===0?-1:1)}
  if(inside(target)){torpedoes.push([start,target]);hit(defender,target,cells)}
 }
 return {route,cells,backfire,torpedoes,destroyed}
}
