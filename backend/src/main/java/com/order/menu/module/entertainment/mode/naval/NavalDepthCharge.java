package com.order.menu.module.entertainment.mode.naval;
import java.util.*;
import com.order.menu.module.entertainment.mode.naval.NavalState.*;
final class NavalDepthCharge {
 record Outcome(List<Cell> route,List<Shot> cells,List<Shot> backfire,List<List<Cell>> paths,boolean destroyed){}
 static void hit(Player p,Cell c,List<Shot> effects){Ship victim=NavalMilitary.shipAt(p,c);boolean scrap=p.scrapMines.remove(c),mine=p.mines.remove(c)||scrap;if(victim!=null&&!victim.hits.contains(NavalMilitary.key(c)))victim.hits.add(NavalMilitary.key(c));Shot shot=new Shot(c.x(),c.y(),victim!=null?"hit":mine?"mine":"miss",scrap?"scrap":null);NavalRepairSupport.recordShot(p,shot);if(effects.stream().noneMatch(s->s.x()==c.x()&&s.y()==c.y()))effects.add(shot);}
 static void sink(Player p,Ship s,List<Shot> effects){for(Cell c:NavalMilitaryAi.cells(s))hit(p,c,effects);}
 static Outcome attack(Player own,Player enemy,Ship ship,Action a,Random random){
  List<Cell> route=new ArrayList<>();List<Shot> cells=new ArrayList<>(),backfire=new ArrayList<>();List<List<Cell>> paths=new ArrayList<>();boolean destroyed=false;
  for(int x=-1;x<=a.x;x++){Cell anchor=new Cell(x,a.y);route.add(anchor);List<Cell> footprint=List.of(anchor,new Cell(x+1,a.y)).stream().filter(NavalMilitary::inside).toList();Ship collision=footprint.stream().map(c->NavalMilitary.shipAt(enemy,c)).filter(Objects::nonNull).findFirst().orElse(null);boolean mine=footprint.stream().anyMatch(c->enemy.mines.contains(c)||enemy.scrapMines.contains(c));if(mine||collision!=null){destroyed=true;sink(own,ship,backfire);if(collision!=null)sink(enemy,collision,cells);for(Cell c:footprint)hit(enemy,c,cells);break;}}
  if(!destroyed)for(NavalMilitary.Part part:NavalMilitary.parts(ship))if(part.active()){Cell start=new Cell(a.x+random.nextInt(2),a.y),target=new Cell(start.x(),start.y()+(part.index()==0?-1:1));if(NavalMilitary.inside(target)){paths.add(List.of(start,target));hit(enemy,target,cells);}}
  return new Outcome(route,cells,backfire,paths,destroyed);
 }
}
