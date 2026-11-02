import {MILITARY_SHIPS,validateMilitaryFleet,arrangeMilitaryFleet,militaryPlacement} from './navalMilitary.js'
const copy=value=>JSON.parse(JSON.stringify(value))
export function militaryRecordTemplate(result){
  if(result?.ruleSet!=='MILITARY'||!result.battleStarted||![0,1].includes(result.winner)||!['AI','MULTIPLAYER'].includes(result.mode)||result.mode==='AI'&&result.winner!==0)return null
  const ships=copy(result.players?.[result.winner]?.ships||[]).map(s=>({...s,hits:[],lostPlanes:[],minesUsed:0}))
  if(validateMilitaryFleet(ships))return null
  const actions=(result.events||[]).filter(e=>e.player===result.winner&&['shot','fighter','bomber','torpedo','radar','submarine','mine','depthCharge'].includes(e.weapon)).map(({shipId,weapon,plane,x,y})=>({shipId,weapon,plane:plane||0,x,y}))
  return actions.length?{ships,actions}:null
}
export function installMilitaryTemplate(room,template,random=Math.random){
  if(!template?.ships?.length||!template.actions?.length)return false
  let ships=copy(template.ships).map(s=>({...s,hits:[],lostPlanes:[],minesUsed:0}))
  if(validateMilitaryFleet(ships))return false
  if(random()<.4)ships=arrangeMilitaryFleet(ships)
  else for(const s of ships){const before={...s};for(let i=0;i<5;i++){
    const rotation=(before.rotation+(random()<.3?90:0))%360,m=MILITARY_SHIPS[s.kind],next={...s,x:before.x+Math.floor(random()*3)-1,y:before.y+Math.floor(random()*3)-1,rotation,w:rotation%180?m.h:m.w,h:rotation%180?m.w:m.h}
    if(militaryPlacement(ships,next)){Object.assign(s,next);break}
  }}
  room.players[1].ships=ships;room.players[1].coins=300-ships.reduce((n,s)=>n+MILITARY_SHIPS[s.kind].price,0)
  const mirror=random()<.35
  room.aiTemplate=template.actions.map(a=>({...a,x:Math.max(0,Math.min(11,(mirror?11-a.x:a.x)+(random()<.3?Math.floor(random()*3)-1:0))),y:Math.max(0,Math.min(11,a.y+(random()<.3?Math.floor(random()*3)-1:0)))}))
  return true
}
