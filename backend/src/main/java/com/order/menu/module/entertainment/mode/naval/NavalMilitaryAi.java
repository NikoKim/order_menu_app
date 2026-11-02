package com.order.menu.module.entertainment.mode.naval;

import java.util.*;
import com.order.menu.module.entertainment.mode.naval.NavalState.*;

final class NavalMilitaryAi {
    private static final Random R=new java.security.SecureRandom();
    private static final int[][] DIR={{1,0},{-1,0},{0,1},{0,-1}};
    static List<Cell> cells(Ship s){List<Cell> out=new ArrayList<>();for(int y=s.y;y<s.y+s.h;y++)for(int x=s.x;x<s.x+s.w;x++)out.add(new Cell(x,y));return out;}
    static double purchaseWeight(String kind,List<String> list){double base=List.of("carrier","bomberCarrier","torpedoCarrier","battleship","cruiser","submarine","depthCharge").contains(kind)?3:"antiAir".equals(kind)?4:"radar".equals(kind)?3:"jammer".equals(kind)?2:1;return base/(1+Collections.frequency(list,kind)*3);}
    static List<String> composition(){
        List<List<String>> cores=List.of(List.of("carrier","battleship","antiAir","radar"),List.of("bomberCarrier","battleship","battleship","antiAir","jammer"),List.of("torpedoCarrier","cruiser","submarine","radar","antiAir"),List.of("submarine","battleship","cruiser","mineLayer","radar"),List.of("carrier","bomberCarrier","cruiser","antiAir"),List.of("battleship","battleship","cruiser","antiAir","jammer"));
        List<String> list=new ArrayList<>(cores.get(R.nextInt(cores.size())));int budget=new int[]{250,270,285,300}[R.nextInt(4)];
        while(true){int cost=list.stream().mapToInt(k->NavalMilitary.MODELS.get(k)[2]).sum();List<String> options=NavalMilitary.MODELS.keySet().stream().filter(k->!k.equals("torpedo")&&cost+NavalMilitary.MODELS.get(k)[2]<=budget&&list.stream().filter(t->NavalMilitary.group(t).equals(NavalMilitary.group(k))).count()<NavalMilitary.cap(k)).sorted().toList();if(options.isEmpty())return list;double roll=R.nextDouble()*options.stream().mapToDouble(k->purchaseWeight(k,list)).sum();String chosen=options.get(options.size()-1);for(String k:options){roll-=purchaseWeight(k,list);if(roll<=0){chosen=k;break;}}list.add(chosen);}
    }
    static List<Ship> fleet(){
        List<String> kinds=composition();double rowWeight=R.nextDouble()<.15?3:20;
        List<Ship> best=null;double bestScore=-1;
        for(int attempt=0;attempt<40;attempt++){
            List<Ship> ships=new ArrayList<>();
            for(String kind:kinds){int[] m=NavalMilitary.MODELS.get(kind);int rotation=R.nextInt(4)*90;Ship s=new Ship(kind+"-"+ships.size(),0,0,rotation%180==0?m[0]:m[1],rotation%180==0?m[1]:m[0]);s.kind=kind;s.rotation=rotation;List<Integer> positions=new ArrayList<>();for(int i=0;i<144;i++)positions.add(i);Collections.shuffle(positions,R);for(int pos:positions){s.x=pos%12;s.y=pos/12;if(NavalMilitary.legal(ships,s)){ships.add(s);break;}}}
            if(ships.size()!=kinds.size())continue;
            Player p=new Player();p.ships=ships;Set<Cell> aa=NavalMilitary.coverage(p),jam=NavalMilitary.jamming(p);double score=aa.stream().map(Cell::y).distinct().count()*rowWeight+aa.stream().mapToDouble(c->(12-c.x())/12.0).sum();for(Ship s:ships)if(!"jammer".equals(s.kind))score+=cells(s).stream().filter(jam::contains).count()*4;
            if(score>bestScore){bestScore=score;best=ships;}
        }
        return best==null?NavalMilitary.legacyFleet():best;
    }
    static class Knowledge {
        NavalMilitaryIntel intel;
        Set<Cell> excluded=new HashSet<>(),scanned=new HashSet<>(),negative=new HashSet<>(),signals=new HashSet<>();Map<Cell,String> shots=new HashMap<>();Map<Cell,Double> weights=new HashMap<>();
        boolean fresh(Cell c){return NavalMilitary.inside(c)&&!excluded.contains(c)&&!shots.containsKey(c);}
        boolean valid(Cell c){return fresh(c)&&!negative.contains(c);}
        double score(Cell c){return score(c,false);}
        double score(Cell c,boolean revisit){return fresh(c)&&(revisit||!negative.contains(c))?.5+Math.min(1,weights.getOrDefault(c,0.0)*.1)+(signals.contains(c)?2:0)+intel.vital.getOrDefault(c,0.0)+(intel.aaHeads.contains(c)?4.0/Math.max(1,intel.aaHeads.size()):0):0;}
        Knowledge(Player own,Player enemy){this(own,enemy,List.of());}
        Knowledge(Player own,Player enemy,List<Event> events){
            intel=new NavalMilitaryIntel(own,enemy,events);List<Ship> sunk=new ArrayList<>(enemy.ships.stream().filter(NavalEngine::sunk).toList());sunk.addAll(intel.resolved);excluded.addAll(intel.excluded);
            for(Shot c:enemy.shots)shots.put(new Cell(c.x(),c.y()),c.result());for(Radar c:own.radar){Cell p=new Cell(c.x(),c.y());scanned.add(p);if(c.occupied())signals.add(p);else negative.add(p);}
            for(Ship s:intel.located)negative.removeAll(cells(s));
            int length=sunk.stream().filter(s->Math.max(s.w,s.h)==4).count()>=2?3:4;
            if(length==3&&sunk.stream().filter(s->Math.max(s.w,s.h)==3).count()>=4)length=2;
            for(;length>=1;length--){boolean found=false;for(int y=0;y<12;y++)for(int x=0;x<12;x++)for(int[] d:new int[][]{{1,0},{0,1}}){List<Cell> run=new ArrayList<>();for(int i=0;i<length;i++)run.add(new Cell(x+d[0]*i,y+d[1]*i));if(run.stream().allMatch(this::valid)){found=true;weights.merge(run.get((length-1)/2),1.0,Double::sum);if(length%2==0)weights.merge(run.get(length/2),1.0,Double::sum);}}if(found)break;}
            List<Cell> hits=enemy.shots.stream().filter(s->"hit".equals(s.result())).map(s->new Cell(s.x(),s.y())).filter(c->enemy.ships.stream().noneMatch(s->intel.neutralized.contains(s.id)&&s.hits.contains(NavalMilitary.key(c)))).toList();
            for(Cell h:hits)for(int[] d:DIR){boolean aligned=hits.contains(new Cell(h.x()+d[0],h.y()+d[1])),other=hits.contains(new Cell(h.x()+d[1],h.y()+d[0]));if(other&&!aligned)continue;Cell c=new Cell(h.x()+d[0],h.y()+d[1]);if(fresh(c))weights.merge(c,aligned?32.0:16.0,Math::max);if(enemy.ships.stream().anyMatch(s->"battleship".equals(s.kind)&&s.hits.contains(NavalMilitary.key(h)))){c=new Cell(h.x()+d[0]*2,h.y()+d[1]*2);if(fresh(c))weights.merge(c,25.0,Math::max);}}
        }
    }
    static double mineRisk(NavalState s,Player p,Cell c){double n=0;for(Ship ship:p.ships)for(Cell h:cells(ship))if(c.x()<h.x()&&Math.abs(c.y()-h.y())<=1)n+=3.0/(1+h.x()-c.x());for(Event e:s.events)if(e.player()==0&&List.of("torpedo","submarine","depthCharge").contains(e.weapon())&&e.y()!=null&&Math.abs(e.y()-c.y())<=1)n+=2;return n;}
    static boolean mineLegal(Player p,Cell c){return NavalMilitary.inside(c)&&NavalMilitary.shipAt(p,c)==null&&!p.mines.contains(c)&&p.shots.stream().noneMatch(t->t.x()==c.x()&&t.y()==c.y());}
    record Option(Action action,double gain,double cost,double score) { }
    static double[] inclusion(double[] weights,int guns){double[] out=weights.clone();if(guns>1)for(int i=0;i<3;i++)for(int j=0;j<3;j++)if(i!=j)out[i]+=weights[j]*weights[i]/(1-weights[j]);return out;}
    static List<Option> evaluate(NavalState s,Knowledge k,boolean revisit){
        Player own=s.players.get(1);List<Option> options=new ArrayList<>();int step=(int)s.events.stream().filter(e->e.player()==1&&List.of("shot","fighter","bomber","torpedo","radar","mine","submarine","depthCharge").contains(e.weapon())).count();
        Action planned=s.aiTemplate!=null&&step<s.aiTemplate.size()?s.aiTemplate.get(step):null;
        long remaining=own.ships.stream().flatMap(t->NavalMilitary.parts(t).stream()).filter(p->p.active()&&NavalMilitaryIntel.air(p.role())).count();
        for(Ship ship:own.ships)for(NavalMilitary.Part part:NavalMilitary.parts(ship))if(part.active()&&(s.mineActions==0||part.role().equals("mine"))&&(!part.role().equals("mine")||s.mineActions>0||k.intel.torpedoThreat)&&!List.of("antiAir","jammer").contains(part.role()))for(int y=0;y<12;y++)for(int x=0;x<12;x++){
            Action a=new Action();a.shipId=ship.id;a.plane=part.index();a.weapon=part.role();a.x=x;a.y=y;List<Cell> area=NavalMilitary.targets(a,ship);if(area.stream().anyMatch(c->!NavalMilitary.inside(c)))continue;
            double gain=0,cost=0;
            if("mine".equals(a.weapon)){
                Cell c=new Cell(x,y);if(!mineLegal(own,c))continue;gain=.05;for(Ship t:own.ships)for(Cell p:cells(t))if(x<p.x()&&Math.abs(y-p.y())<=1)gain+=.12/(1+p.x()-x);
                for(Event e:s.events)if(e.player()==0&&List.of("submarine","torpedo").contains(e.weapon())&&e.y()!=null&&Math.abs(e.y()-y)<=1)gain+=.15;
                a.targets=new ArrayList<>(List.of(c));
            }else if("radar".equals(a.weapon)){
                for(Cell c:area)gain+=(k.valid(c)&&!k.scanned.contains(c)?.2:0)+(k.intel.aaHeads.contains(c)&&!k.scanned.contains(c)?.4:0);
            }else if("depthCharge".equals(a.weapon)){
                List<Cell> route=new ArrayList<>();for(int col=0;col<x+2;col++)route.add(new Cell(col,y));Ship collision=k.intel.located.stream().filter(t->!Collections.disjoint(cells(t),route)).findFirst().orElse(null);
                if(collision!=null){gain=cells(collision).stream().mapToDouble(c->k.score(c,revisit)).sum()+3;cost=2.5;}
                else{for(NavalMilitary.Part launcher:NavalMilitary.parts(ship))if(launcher.active())for(int col=x;col<x+2;col++)gain+=k.score(new Cell(col,y+(launcher.index()==0?-1:1)),revisit)/2;double safe=route.stream().filter(k.shots::containsKey).count()/(double)route.size();cost=(1-safe)*.45*(2.5+NavalMilitary.parts(ship).stream().filter(p->p.active()).count()*.25);}
            }else if("submarine".equals(a.weapon)){
                double safe=0;for(int col=0;col<x+3;col++)if(k.shots.containsKey(new Cell(col,y)))safe++;safe/=x+3;
                for(int col=x;col<x+3;col++)for(int d:new int[]{-1,1}){double reach=1;for(int row=y+d;row>=0&&row<12;row+=d){Cell c=new Cell(col,row);if("hit".equals(k.shots.get(c)))break;gain+=reach*k.score(c,revisit)/3;if(k.fresh(c))reach*=.85;}}
                cost=k.intel.mineThreat?(1-safe)*.55*3:0;
            }else if("torpedo".equals(a.weapon)){
                double reach=1;for(int col=x;col<12;col++){Cell c=new Cell(col,y);if("hit".equals(k.shots.get(c)))break;gain+=reach*k.score(c,revisit);if(k.fresh(c))reach*=.85;}
            }else{
                double[] probabilities=new double[area.size()];Arrays.fill(probabilities,1);long known=area.stream().filter(c->"hit".equals(k.shots.get(c))).count();
                if(List.of("fighter","bomber").contains(a.weapon)){double n="fighter".equals(a.weapon)?4.5:5.5;for(int i=0;i<area.size();i++)probabilities[i]="hit".equals(k.shots.get(area.get(i)))?0:Math.min(n/Math.max(1,area.size()-known),n/area.size()+known*.02);}
                else if("battleship".equals(ship.kind)){double fresh=known==1?.35:.45;double[] weights=new double[3];for(int i=0;i<3;i++)weights[i]=known==0||known==3?1.0/3:"hit".equals(k.shots.get(area.get(i)))?(1-(3-known)*fresh)/known:fresh;probabilities=inclusion(weights,(int)NavalMilitary.parts(ship).stream().filter(p->p.active()).count());}
                for(int i=0;i<area.size();i++)gain+=probabilities[i]*k.score(area.get(i),revisit);
            }
            if(NavalMilitaryIntel.air(a.weapon)){int end=x+("fighter".equals(a.weapon)?6:"bomber".equals(a.weapon)?3:1);List<Cell> route=new ArrayList<>();for(int col=0;col<=end;col++)route.add(new Cell(col,y));NavalMilitaryIntel.Risk risk=k.intel.flightRisk(route);double probability=risk.confirmed()?.98:Math.max(.85*risk.exposure(),risk.unsafe()?.12+.12*risk.density():0);cost=probability*(1.5+2.0/Math.max(1,remaining)+gain);}
            if("shot".equals(a.weapon))gain+=area.stream().filter(c->k.valid(c)&&k.intel.aaHeads.contains(c)).count()*.35;
            if(gain<=0)continue;
            double distance=planned!=null&&a.weapon.equals(planned.weapon)&&a.shipId.equals(planned.shipId)?Math.abs(x-planned.x)+Math.abs(y-planned.y):Double.POSITIVE_INFINITY;
            double bonus=distance<=2?Math.min(.15,gain*.03)/(1+distance):0;
            options.add(new Option(a,gain,cost,gain+bonus-cost));
        }
        return options;
    }
    static Action choose(NavalState s){
        Knowledge k=new Knowledge(s.players.get(1),s.players.get(0),s.events);List<Option> options=evaluate(s,k,false);
        if(options.stream().noneMatch(o->!List.of("mine","radar").contains(o.action.weapon)&&o.gain>0))options=evaluate(s,k,true);
        options.sort(Comparator.comparingDouble(Option::score).reversed());if(options.isEmpty())return null;
        double best=options.get(0).score;List<Option> top=options.stream().filter(o->o.score>=best-Math.max(.001,Math.abs(best)*.02)).toList();return top.get(R.nextInt(top.size())).action;
    }
}
