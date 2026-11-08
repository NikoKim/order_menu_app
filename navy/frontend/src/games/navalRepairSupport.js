import {militaryCell,militaryComponents,militaryInside,militaryPlacement,militarySunk,MILITARY_SHIPS,cellKey} from './navalMilitary.js'
import {markSupportShots} from './navalShotMarkers.js'
const clone=value=>JSON.parse(JSON.stringify(value))
export const militarySupportRange=tug=>({x:tug.x-5,y:tug.y-5,w:12,h:12})
export const militarySupportContains=(tug,ship)=>ship.x>=Math.max(0,tug.x-5)&&ship.y>=Math.max(0,tug.y-5)&&ship.x+ship.w<=Math.min(12,tug.x+7)&&ship.y+ship.h<=Math.min(12,tug.y+7)
export const militarySupportTargets=(player,tug)=>player.ships.filter(s=>s.kind!=='submarine'&&!militarySunk(s)&&militarySupportContains(tug,s))
export function repairSupport(room,actor,action,now){
 const player=room.players[actor],tug=player.ships.find(s=>s.id===action.shipId),mode=action.supportMode
 if(!tug||tug.kind!=='repairTug'||militarySunk(tug))throw Error('修理拖船不可用')
 const parts=militaryComponents(tug),tool=mode==='tow'?'tow':'repair'
 if(!parts.some(p=>p.role===tool&&p.active))throw Error('对应工具已被击毁')
 if(room.mineActions)throw Error('请先完成本回合布雷')
 if(room.supportActions&&(mode!=='scrapMine'&&mode!=='skip'||room.supportShipId!==tug.id))throw Error('请完成废雷修理或跳过')
 const support={mode,targetShipId:action.targetShipId,cells:[]}
 if(mode==='skip'){
  if(!room.supportActions)throw Error('当前没有待完成的废雷修理')
 }else if(mode==='scrapMine'){
  const c={x:action.x,y:action.y},key=cellKey(c)
  player.scrapMines||=[]
  if(!militaryInside(c.x,c.y)||!militarySupportContains(tug,{...c,w:1,h:1})||!player.shots.some(s=>cellKey(s)===key&&s.result==='mine')||player.scrapMines.some(s=>cellKey(s)===key)||player.mines.some(s=>cellKey(s)===key)||player.ships.some(s=>c.x>=s.x&&c.x<s.x+s.w&&c.y>=s.y&&c.y<s.y+s.h))throw Error('只能修理范围内已引爆且未修复的水雷')
  player.scrapMines.push(c);support.cells=[c]
 }else{
  const target=player.ships.find(s=>s.id===action.targetShipId)
  if(!target||target.kind==='submarine'||militarySunk(target)||!militarySupportContains(tug,target))throw Error('目标舰船不在支援范围或无法修复')
  support.before=clone(target)
  if(mode==='repair'){
   const c={x:action.x,y:action.y},key=cellKey(c)
   const part=militaryComponents(target).find(p=>p.x===c.x&&p.y===c.y&&!['fighter','bomber','torpedo'].includes(p.role))
   if(!part||!target.hits.includes(key))throw Error('只能修理已击毁的非飞机功能格')
   target.hits=target.hits.filter(k=>k!==key);support.cells=[c]
  }else if(mode==='tow'){
   const rotation=action.rotation??target.rotation??0,m=MILITARY_SHIPS[target.kind]
   if(![0,90,180,270].includes(rotation))throw Error('旋转方向无效')
   const next={...target,x:action.x,y:action.y,rotation,w:rotation%180?m.h:m.w,h:rotation%180?m.w:m.h}
   if(!militarySupportContains(tug,next)||!militaryPlacement(player.ships,next))throw Error('拖移位置超出范围或与舰船相邻')
   for(let y=next.y;y<next.y+next.h;y++)for(let x=next.x;x<next.x+next.w;x++)if(player.shots.some(s=>!s.uncertain&&s.x===x&&s.y===y)||player.mines.some(s=>s.x===x&&s.y===y)||player.scrapMines?.some(s=>s.x===x&&s.y===y))throw Error('只能拖往未受击的空海域')
   const damaged=[]
   for(let y=0;y<m.h;y++)for(let x=0;x<m.w;x++)if(target.hits.includes(cellKey(militaryCell(target,x,y))))damaged.push(cellKey(militaryCell(next,x,y)))
   Object.assign(target,next,{hits:damaged})
  }else throw Error('支援操作无效')
  support.after=clone(target)
 }
 markSupportShots(player,support)
 const remainingMine=mode==='scrapMine'&&player.shots.some(s=>s.result==='mine'&&militarySupportContains(tug,{...s,w:1,h:1})&&!player.scrapMines.some(m=>cellKey(m)===cellKey(s))&&!player.mines.some(m=>cellKey(m)===cellKey(s))&&!player.ships.some(ship=>s.x>=ship.x&&s.x<ship.x+ship.w&&s.y>=ship.y&&s.y<ship.y+ship.h))
 const again=mode==='scrapMine'&&!room.supportActions&&remainingMine
 room.supportActions=again?1:0;room.supportShipId=again?tug.id:null;room.turn=again?actor:1-actor
 if(!again)room.deadline=now+30000;room.nextAiAt=now+4500
 room.events.push({id:++room.sequence,player:actor,weapon:'repairSupport',shipId:tug.id,x:action.x,y:action.y,cells:[],targets:support.cells,route:[],backfire:[],support,text:mode==='tow'?'拖移舰船':mode==='repair'?'修复舰船功能':mode==='skip'?'结束本次修理':'修复废水雷'})
 if(room.presentationSync&&!again){room.presentationId=room.sequence;room.presentationUntil=now+15000;room.deadline=room.presentationUntil+30000}
 return room
}
