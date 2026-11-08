package com.order.menu.module.entertainment.mode.naval;

import java.util.*;
import com.order.menu.module.entertainment.mode.naval.NavalState.*;

/** Derived from public battle evidence; live hull positions must not enter targeting. */
final class NavalMilitaryIntel {
    static final int[][] DIR={{1,0},{-1,0},{0,1},{0,-1}};
    final List<Ship> resolved=new ArrayList<>(),located=new ArrayList<>();
    final Set<Cell> excluded=new HashSet<>();
    final Map<Cell,Double> vital=new HashMap<>();
    final List<List<List<Cell>>> knownAa=new ArrayList<>();
    final Set<String> neutralized=new HashSet<>();
    final Set<Cell> aaHeads=new HashSet<>();
    final List<Aa> aa=new ArrayList<>();
    final List<Cell> threats=new ArrayList<>();
    boolean torpedoThreat,mineThreat;
    record Aa(Cell head,Set<Cell> coverage) { }
    record Risk(boolean unsafe,boolean blocked,boolean confirmed,double exposure,double density,Set<Cell> heads) { }
    static boolean air(String weapon){return List.of("fighter","bomber","torpedo").contains(weapon);}
    static Set<Cell> halo(List<Cell> shape){Set<Cell> out=new HashSet<>();for(Cell c:shape)for(int dy=-1;dy<=1;dy++)for(int dx=-1;dx<=1;dx++)out.add(new Cell(c.x()+dx,c.y()+dy));return out;}
    static List<List<Cell>> hypotheses(String kind,List<String> hits,Map<Cell,String> shots,Set<Cell> excluded,Set<Cell> other){
        int length=NavalMilitary.MODELS.get(kind)[1];List<List<Cell>> out=new ArrayList<>();
        for(int y=0;y<12;y++)for(int x=0;x<12;x++)for(int[] d:DIR){List<Cell> shape=new ArrayList<>();if("repairTug".equals(kind)){shape.addAll(List.of(new Cell(x,y),new Cell(x+1,y),new Cell(x,y+1),new Cell(x+1,y+1)));}else for(int i=0;i<length;i++)shape.add(new Cell(x+d[0]*i,y+d[1]*i));if(shape.stream().allMatch(c->NavalMilitary.inside(c)&&!excluded.contains(c)&&(!shots.containsKey(c)||"hit".equals(shots.get(c)))&&!other.contains(c))&&hits.stream().allMatch(h->shape.stream().anyMatch(c->NavalMilitary.key(c).equals(h))))out.add(shape);}
        return out;
    }
    static boolean disabled(String kind,List<Cell> shape,List<String> hits,Set<Integer> lost){
        boolean first=hits.contains(NavalMilitary.key(shape.get(0))),middle=hits.contains(NavalMilitary.key(shape.get(1))),last=hits.contains(NavalMilitary.key(shape.get(shape.size()-1)));
        if(hits.size()>=shape.size())return true;
        return switch(kind){
            case "battleship" -> first&&last;
            case "carrier" -> (middle||lost.contains(0))&&(hits.contains(NavalMilitary.key(shape.get(2)))||lost.contains(1));
            case "bomberCarrier","torpedoCarrier" -> middle||hits.contains(NavalMilitary.key(shape.get(2)))||lost.contains(0);
            case "cruiser","antiAir","radar","jammer" -> first;
            case "torpedo" -> last||lost.contains(0);
            case "submarine" -> middle;
            default -> false;
        };
    }
    NavalMilitaryIntel(Player own,Player enemy,List<Event> events){
        Map<Cell,String> shots=new HashMap<>();Set<Cell> allHits=new HashSet<>();Set<String> revealed=new HashSet<>();
        for(Shot s:enemy.shots){if(Boolean.TRUE.equals(s.uncertain()))continue;Cell c=new Cell(s.x(),s.y());shots.put(c,s.result());if("hit".equals(s.result()))allHits.add(c);}
        List<Event> observed=events.stream().filter(e->e.player()==0&&e.shipId()!=null).toList();
        for(Ship s:enemy.ships){if(!s.hits.isEmpty()||observed.stream().anyMatch(e->s.id.equals(e.shipId())))revealed.add(s.id);if(NavalEngine.sunk(s)){neutralized.add(s.id);excluded.addAll(halo(NavalMilitaryAi.cells(s)));}Set<Integer> lost=new HashSet<>();for(Event e:observed)if(s.id.equals(e.shipId())&&e.destroyed()&&air(e.weapon()))lost.add(e.plane());if((List.of("torpedoCarrier","bomberCarrier","torpedo").contains(s.kind)&&lost.contains(0))||("carrier".equals(s.kind)&&lost.containsAll(List.of(0,1))))neutralized.add(s.id);}
        for(Ship s:enemy.ships)if(!s.hits.isEmpty()&&!NavalEngine.sunk(s)){
            Set<Cell> other=new HashSet<>(allHits);other.removeIf(c->s.hits.contains(NavalMilitary.key(c)));List<List<Cell>> possible=hypotheses(s.kind,s.hits,shots,excluded,other);Set<Integer> lost=new HashSet<>();for(Event e:observed)if(s.id.equals(e.shipId())&&e.destroyed()&&air(e.weapon()))lost.add(e.plane());
            if(!possible.isEmpty()&&possible.stream().allMatch(p->disabled(s.kind,p,s.hits,lost)))neutralized.add(s.id);
            if(!possible.isEmpty()){
                Set<Cell> union=new HashSet<>(),common=new HashSet<>(halo(possible.get(0)));for(List<Cell> shape:possible){union.addAll(shape);common.retainAll(halo(shape));}common.removeAll(union);excluded.addAll(common);
                int[] indices=switch(s.kind){case "depthCharge"->new int[]{0,1};case "battleship"->new int[]{0,2};case "carrier","bomberCarrier","torpedoCarrier"->new int[]{1,2};case "cruiser","antiAir","radar","jammer"->new int[]{0};default->new int[]{};};
                if(!neutralized.contains(s.id))for(List<Cell> shape:possible)for(int index:indices){Cell c=shape.get(index);if(!shots.containsKey(c))vital.merge(c,3.0/possible.size(),Double::sum);}
                if(possible.stream().map(HashSet::new).distinct().count()!=1)continue;List<Cell> shape=possible.get(0);int minX=shape.stream().mapToInt(Cell::x).min().orElseThrow(),minY=shape.stream().mapToInt(Cell::y).min().orElseThrow();Ship inferred=new Ship(s.id,minX,minY,shape.stream().mapToInt(Cell::x).max().orElseThrow()-minX+1,shape.stream().mapToInt(Cell::y).max().orElseThrow()-minY+1);inferred.kind=s.kind;inferred.hits=s.hits;located.add(inferred);if(neutralized.contains(s.id))resolved.add(inferred);Set<Cell> ring=halo(shape);ring.removeAll(union);excluded.addAll(ring);
            }
        }
        Set<Cell> safe=new HashSet<>();List<Cell> intercepts=new ArrayList<>();
        for(Event e:events)if(e.player()==1&&air(e.weapon())&&e.route()!=null&&!e.route().isEmpty()){int end=e.route().size()-(e.destroyed()?1:0);safe.addAll(e.route().subList(0,end));if(e.destroyed())intercepts.add(e.route().get(e.route().size()-1));}
        boolean unknown=enemy.ships.stream().anyMatch(s->!revealed.contains(s.id));Set<Cell> nonAa=new HashSet<>();for(Ship s:enemy.ships)if(revealed.contains(s.id)&&!"antiAir".equals(s.kind))for(Cell h:allHits)if(s.hits.contains(NavalMilitary.key(h)))nonAa.add(h);
        for(Ship s:located)if(!"antiAir".equals(s.kind))nonAa.addAll(NavalMilitaryAi.cells(s));
        List<List<Cell>> candidates=unknown?hypotheses("antiAir",List.of(),shots,excluded,nonAa):new ArrayList<>();
        for(Ship s:enemy.ships)if("antiAir".equals(s.kind)&&revealed.contains(s.id)&&!neutralized.contains(s.id)){Set<Cell> other=new HashSet<>(allHits);other.removeIf(c->s.hits.contains(NavalMilitary.key(c)));List<List<Cell>> identified=hypotheses(s.kind,s.hits,shots,excluded,other).stream().filter(p->!"hit".equals(shots.get(p.get(0)))&&Collections.disjoint(halo(p),safe)).toList();knownAa.add(identified);candidates.addAll(identified);}
        for(List<Cell> shape:candidates){Set<Cell> coverage=halo(shape);if(!"hit".equals(shots.get(shape.get(0)))&&Collections.disjoint(coverage,safe))aa.add(new Aa(shape.get(0),coverage));}
        for(Cell c:intercepts)if(aa.stream().anyMatch(a->a.coverage.contains(c)))threats.add(c);
        for(Aa a:aa)if(threats.stream().anyMatch(a.coverage::contains))aaHeads.add(a.head);
        mineThreat=unknown||enemy.ships.stream().anyMatch(s->revealed.contains(s.id)&&"mineLayer".equals(s.kind)&&!neutralized.contains(s.id));
        torpedoThreat=unknown||enemy.ships.stream().anyMatch(s->revealed.contains(s.id)&&List.of("submarine","torpedo","torpedoCarrier","depthCharge").contains(s.kind)&&!neutralized.contains(s.id)&&observed.stream().noneMatch(e->s.id.equals(e.shipId())&&e.destroyed()&&air(e.weapon())));
    }
    double exposure(List<Cell> route){double value=0;for(Cell threat:threats){List<Aa> suspects=aa.stream().filter(a->a.coverage.contains(threat)).toList();value=Math.max(value,suspects.stream().filter(a->!Collections.disjoint(a.coverage,route)).count()/(double)Math.max(1,suspects.size()));}return value;}
    Risk flightRisk(List<Cell> route){return boundedFlightRisk(route.stream().filter(NavalMilitary::inside).toList());}
    Risk boundedFlightRisk(List<Cell> route){Set<Cell> heads=new HashSet<>();int danger=0;for(Aa a:aa)if(!Collections.disjoint(a.coverage,route)){danger++;heads.add(a.head);}return new Risk(danger>0,threats.stream().anyMatch(route::contains),knownAa.stream().anyMatch(p->!p.isEmpty()&&p.stream().allMatch(shape->!Collections.disjoint(halo(shape),route))),exposure(route),danger/(double)Math.max(1,aa.size()),heads);}
}
