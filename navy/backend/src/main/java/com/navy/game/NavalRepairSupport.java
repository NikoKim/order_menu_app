package com.navy.game;
import com.navy.game.NavalState.*;
import java.util.*;

final class NavalRepairSupport {
 static boolean contains(Ship tug,Ship s){return s.x>=Math.max(0,tug.x-5)&&s.y>=Math.max(0,tug.y-5)&&s.x+s.w<=Math.min(12,tug.x+7)&&s.y+s.h<=Math.min(12,tug.y+7);}
 private static final com.fasterxml.jackson.databind.ObjectMapper JSON=new com.fasterxml.jackson.databind.ObjectMapper();
 static Ship copy(Ship s){return JSON.convertValue(s,Ship.class);}
 static void markShots(Player player,Map<String,Object> support){
  Set<Cell> cells=new HashSet<>();
  if("tow".equals(support.get("mode"))&&support.get("before")!=null){
   Object before=support.get("before");List<?> hits=before instanceof Ship ship?ship.hits:before instanceof Map<?,?> map&&map.get("hits") instanceof List<?> list?list:List.of();
   for(Object value:hits)if(value instanceof String key&&key.matches("(?:[0-9]|1[01]),(?:[0-9]|1[01])")){String[] xy=key.split(",");cells.add(new Cell(Integer.parseInt(xy[0]),Integer.parseInt(xy[1])));}
  }else if("repair".equals(support.get("mode"))&&support.get("cells") instanceof List<?> list)for(Object cell:list)cells.add(JSON.convertValue(cell,Cell.class));
  if(cells.isEmpty())return;
  player.shots=player.shots.stream().map(shot->"hit".equals(shot.result())&&cells.contains(new Cell(shot.x(),shot.y()))?new Shot(shot.x(),shot.y(),shot.result(),shot.mineKind(),true):shot).collect(java.util.stream.Collectors.toCollection(ArrayList::new));
 }
 static void recordShot(Player player,Shot shot){
  for(int i=0;i<player.shots.size();i++){Shot old=player.shots.get(i);if(old.x()==shot.x()&&old.y()==shot.y()){if(Boolean.TRUE.equals(old.uncertain()))player.shots.set(i,shot);return;}}
  player.shots.add(shot);
 }
 // Old persisted games are upgraded once, without querying or rewriting on each read.
 static void normalizeShots(NavalState state){
  if(state.shotMarkersVersion>=1)return;
  for(Event event:state.events){
   if(event.player()<0||event.player()>=state.players.size()||state.players.size()!=2)continue;
   Player own=state.players.get(event.player()),enemy=state.players.get(1-event.player());
   if("repairSupport".equals(event.weapon())&&event.support()!=null)markShots(own,event.support());
   else for(int side=0;side<2;side++)for(Shot shot:side==0?(event.cells()==null?List.<Shot>of():event.cells()):(event.backfire()==null?List.<Shot>of():event.backfire())){
    Player player=side==0?enemy:own;
    if(player.shots.stream().anyMatch(old->old.x()==shot.x()&&old.y()==shot.y()&&Boolean.TRUE.equals(old.uncertain())))recordShot(player,shot);
   }
  }
  state.shotMarkersVersion=1;
 }
 static void attack(NavalState state,int actor,Action a,long now){
  Player p=state.players.get(actor);Ship tug=p.ships.stream().filter(s->Objects.equals(s.id,a.shipId)).findFirst().orElse(null);String mode=a.supportMode;
  if(tug==null||!"repairTug".equals(tug.kind)||NavalEngine.sunk(tug))NavalEngine.bad("修理拖船不可用");
  String tool="tow".equals(mode)?"tow":"repair";
  if(NavalMilitary.parts(tug).stream().noneMatch(part->part.active()&&tool.equals(part.role())))NavalEngine.bad("对应工具已被击毁");
  if(state.mineActions>0)NavalEngine.bad("请先完成本回合布雷");
  if(state.supportActions>0&&(!List.of("scrapMine","skip").contains(mode)||!Objects.equals(state.supportShipId,tug.id)))NavalEngine.bad("请完成废雷修理或跳过");
  Map<String,Object> support=new LinkedHashMap<>();support.put("mode",mode);support.put("targetShipId",a.targetShipId);List<Cell> cells=new ArrayList<>();
  if("skip".equals(mode)){if(state.supportActions==0)NavalEngine.bad("当前没有待完成的废雷修理");}
  else if("scrapMine".equals(mode)){
   Cell c=new Cell(a.x,a.y);Ship square=new Ship("",a.x,a.y,1,1);
   if(!NavalMilitary.inside(c)||!contains(tug,square)||p.shots.stream().noneMatch(s->s.x()==a.x&&s.y()==a.y&&"mine".equals(s.result()))||p.scrapMines.contains(c)||p.mines.contains(c)||NavalMilitary.shipAt(p,c)!=null)NavalEngine.bad("只能修理范围内已引爆且未修复的水雷");
   p.scrapMines.add(c);cells.add(c);
  }else{
   Ship target=p.ships.stream().filter(s->Objects.equals(s.id,a.targetShipId)).findFirst().orElse(null);
   if(target==null||"submarine".equals(target.kind)||NavalEngine.sunk(target)||!contains(tug,target))NavalEngine.bad("目标舰船不在支援范围或无法修复");
   support.put("before",copy(target));
   if("repair".equals(mode)){
    Cell c=new Cell(a.x,a.y);
    if(!target.hits.contains(NavalMilitary.key(c))||NavalMilitary.parts(target).stream().noneMatch(part->part.cell().equals(c)&&!List.of("fighter","bomber","torpedo").contains(part.role())))NavalEngine.bad("只能修理已击毁的非飞机功能格");
    target.hits.remove(NavalMilitary.key(c));cells.add(c);
   }else if("tow".equals(mode)){
    Ship next=copy(target);int r=a.rotation==null?target.rotation:a.rotation;int[] m=NavalMilitary.MODELS.get(target.kind);
    if(!List.of(0,90,180,270).contains(r))NavalEngine.bad("旋转方向无效");
    next.x=a.x;next.y=a.y;next.rotation=r;next.w=r%180==0?m[0]:m[1];next.h=r%180==0?m[1]:m[0];
    if(!contains(tug,next)||!NavalMilitary.legal(p.ships,next))NavalEngine.bad("拖移位置超出范围或与舰船相邻");
    for(int y=next.y;y<next.y+next.h;y++)for(int x=next.x;x<next.x+next.w;x++){Cell c=new Cell(x,y);if(p.shots.stream().anyMatch(s->s.x()==c.x()&&s.y()==c.y()&&!Boolean.TRUE.equals(s.uncertain()))||p.mines.contains(c)||p.scrapMines.contains(c))NavalEngine.bad("只能拖往未受击的空海域");}
    List<String> hits=new ArrayList<>();for(int y=0;y<m[1];y++)for(int x=0;x<m[0];x++)if(target.hits.contains(NavalMilitary.key(NavalMilitary.cell(target,x,y))))hits.add(NavalMilitary.key(NavalMilitary.cell(next,x,y)));
    target.x=next.x;target.y=next.y;target.w=next.w;target.h=next.h;target.rotation=r;target.hits=hits;
   }else NavalEngine.bad("支援操作无效");
   support.put("after",copy(target));
  }
  support.put("cells",cells);markShots(p,support);boolean remainingMine="scrapMine".equals(mode)&&p.shots.stream().anyMatch(s->"mine".equals(s.result())&&contains(tug,new Ship("",s.x(),s.y(),1,1))&&!p.scrapMines.contains(new Cell(s.x(),s.y()))&&!p.mines.contains(new Cell(s.x(),s.y()))&&NavalMilitary.shipAt(p,new Cell(s.x(),s.y()))==null);boolean again="scrapMine".equals(mode)&&state.supportActions==0&&remainingMine;state.supportActions=again?1:0;state.supportShipId=again?tug.id:null;
  state.turn=again?actor:1-actor;if(!again)state.deadline=now+30000;state.nextAiAt=now+4500;
  state.events.add(new Event(++state.sequence,actor,"repairSupport",List.of(),"tow".equals(mode)?"拖移舰船":"repair".equals(mode)?"修复舰船功能":"skip".equals(mode)?"结束本次修理":"修复废水雷",a.x,a.y,null,cells,List.of(),List.of(),false,List.of(),tug.id,0,support));if(!again)NavalEngine.waitForPresentation(state,now);
 }
}
