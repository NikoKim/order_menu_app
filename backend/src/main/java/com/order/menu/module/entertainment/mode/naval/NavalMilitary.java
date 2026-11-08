package com.order.menu.module.entertainment.mode.naval;

import com.order.menu.module.entertainment.mode.naval.NavalState.*;
import java.util.*;

/** Server-authoritative military rules. Damage is stored on hull cells, not inventory. */
public final class NavalMilitary {
    private NavalMilitary() { }
    private static final Random RANDOM = new java.security.SecureRandom();
    // width, height, price, purchase cap
    static final Map<String,int[]> MODELS = Map.ofEntries(Map.entry("torpedoCarrier",new int[]{1,4,60,2}),Map.entry("carrier",new int[]{1,4,50,2}),Map.entry("bomberCarrier",new int[]{1,4,50,2}),Map.entry("battleship",new int[]{1,3,35,4}),Map.entry("cruiser",new int[]{1,3,25,4}),Map.entry("repairTug",new int[]{2,2,35,2}),Map.entry("submarine",new int[]{1,3,50,4}),Map.entry("antiAir",new int[]{1,2,30,6}),Map.entry("torpedo",new int[]{1,2,30,6}),Map.entry("radar",new int[]{1,2,20,6}),Map.entry("mineLayer",new int[]{1,2,30,6}),Map.entry("jammer",new int[]{1,2,20,6}),Map.entry("depthCharge",new int[]{1,2,25,6}),Map.entry("decoy",new int[]{1,2,15,3}));
    record Part(Cell cell, String role, int index, boolean active) { }
    static String group(String kind){return "repairTug".equals(kind)?"support":"decoy".equals(kind)?"decoy":List.of("carrier","bomberCarrier","torpedoCarrier").contains(kind)?"carrier":List.of("battleship","cruiser","submarine").contains(kind)?"medium":"destroyer";}
    static int cap(String kind){return switch(group(kind)){case "support","carrier"->2;case "destroyer"->6;case "medium"->4;default->3;};}
    static Set<Cell> jamming(Player p){Set<Cell> result=new HashSet<>();for(Ship s:p.ships)if("jammer".equals(s.kind)&&parts(s).stream().anyMatch(t->t.active))for(int forward=1;forward<=4;forward++)for(int side=-1;side<=1;side++){Cell c=cell(s,side,-forward);if(inside(c))result.add(c);}return result;}
    static boolean inside(Cell c) { return c.x()>=0 && c.y()>=0 && c.x()<12 && c.y()<12; }
    static String key(Cell c) { return c.x()+","+c.y(); }
    static Cell cell(Ship s,int x,int y) {
        int[] m=MODELS.get(s.kind);
        return switch(s.rotation) {
            case 90 -> new Cell(s.x+m[1]-1-y,s.y+x);
            case 180 -> new Cell(s.x+m[0]-1-x,s.y+m[1]-1-y);
            case 270 -> new Cell(s.x+y,s.y+m[0]-1-x);
            default -> new Cell(s.x+x,s.y+y);
        };
    }
    static Part part(Ship s,int x,int y,String role,int index) {
        Cell c=cell(s,x,y);
        return new Part(c,role,index,!NavalEngine.sunk(s) && ("submarine".equals(s.kind)||!s.hits.contains(key(c))) && (!List.of("bomberCarrier","torpedoCarrier").contains(s.kind)||!s.hits.contains(key(cell(s,0,2)))) && (!"mineLayer".equals(s.kind)||s.minesUsed<10) && !s.lostPlanes.contains(index));
    }
    static List<Part> parts(Ship s) {
        return switch(s.kind) {
            case "repairTug" -> List.of(part(s,0,0,"repair",0),part(s,1,1,"tow",1));
            case "depthCharge" -> List.of(part(s,0,0,"depthCharge",0),part(s,0,1,"depthCharge",1));
            case "carrier" -> List.of(part(s,0,1,"fighter",0),part(s,0,2,"fighter",1));
            case "bomberCarrier" -> List.of(part(s,0,1,"bomber",0));
            case "torpedoCarrier" -> List.of(part(s,0,1,"torpedo",0));
            case "battleship" -> List.of(part(s,0,0,"shot",0),part(s,0,2,"shot",1));
            case "cruiser" -> List.of(part(s,0,0,"shot",0));
            case "decoy" -> List.of();
            default -> List.of(part(s,0,"torpedo".equals(s.kind)?1:0,"mineLayer".equals(s.kind)?"mine":s.kind,0));
        };
    }
    static boolean offensive(Player p) { return p.ships.stream().flatMap(s->parts(s).stream()).anyMatch(a->a.active && List.of("shot","fighter","bomber","torpedo","submarine","depthCharge").contains(a.role)); }
    static boolean defeated(Player p) { return p.ships.stream().filter(s->!"decoy".equals(s.kind)).allMatch(NavalEngine::sunk) || !offensive(p); }
    static boolean checkDefeat(NavalState s,long now){if(!"BATTLE".equals(s.phase)||s.players.size()!=2)return false;if(!defeated(s.players.get(0))&&!defeated(s.players.get(1)))return false;s.phase="FINISHED";s.winner=defeated(s.players.get(0))?1:0;s.deadline=0;s.finishedAt=now;s.finishReason="MILITARY_DEFEAT";return true;}
    static boolean legal(List<Ship> ships,Ship a) {
        return a.x>=0 && a.y>=0 && a.x+a.w<=12 && a.y+a.h<=12 && ships.stream().filter(b->b!=a && !Objects.equals(a.id,b.id) && b.x>=0).noneMatch(b->a.x<=b.x+b.w && b.x<=a.x+a.w && a.y<=b.y+b.h && b.y<=a.y+a.h);
    }
    static int validate(List<Ship> ships,boolean ready) {
        if(ships==null || ships.size()>18) NavalEngine.bad("舰队数据无效");
        int cost=0; Map<String,Integer> counts=new HashMap<>(); Set<String> ids=new HashSet<>();
        for(Ship s:ships) {
            if(s==null || !MODELS.containsKey(s.kind) || s.id==null || s.id.isBlank() || s.id.length()>80 || !ids.add(s.id) || !List.of(0,90,180,270).contains(s.rotation)) NavalEngine.bad("舰船型号或编号无效");
            int[] m=MODELS.get(s.kind); cost+=m[2]; counts.merge(group(s.kind),1,Integer::sum);
            if(counts.get(group(s.kind))>cap(s.kind) || s.w!=(s.rotation%180==0?m[0]:m[1]) || s.h!=(s.rotation%180==0?m[1]:m[0])) NavalEngine.bad("舰船尺寸或数量无效");
            if((ready || s.x!=-1 || s.y!=-1) && !legal(ships,s)) NavalEngine.bad("舰船不能越界或相邻，含斜角");
        }
        if(cost>300) NavalEngine.bad("金币不足");
        // Never trust client-supplied damage or aircraft status.
        for(Ship s:ships) { s.hits=new ArrayList<>(); s.lostPlanes=new ArrayList<>(); s.stealth=false; s.minesUsed=0; }
        Player p=new Player(); p.ships=ships;
        if(ready && !offensive(p)) NavalEngine.bad("至少部署一艘具有进攻能力的舰船");
        return cost;
    }
    static List<Ship> randomFleet() { return NavalMilitaryAi.fleet(); }
    static List<Ship> legacyFleet() {
        List<String> kinds=List.of("carrier","battleship","cruiser","submarine","antiAir","jammer","radar","mineLayer","decoy");
        for(int attempt=0;attempt<100;attempt++) {
            List<Ship> result=new ArrayList<>();
            for(String kind:kinds) {
                int[] m=MODELS.get(kind); Ship s=new Ship(kind+"-"+result.size(),0,0,m[0],m[1]); s.kind=kind;
                List<Integer> positions=new ArrayList<>(); for(int i=0;i<144;i++) positions.add(i); Collections.shuffle(positions,RANDOM);
                boolean found=false;
                for(int pos:positions) { s.x=pos%12; s.y=pos/12; if(legal(result,s)) { found=true; result.add(s); break; } }
                if(!found) break;
            }
            if(result.size()==kinds.size()) return result;
        }
        throw new IllegalStateException("Cannot arrange military fleet");
    }
    static void begin(NavalState s,long now) {
        s.rulesVersion=2; s.phase="PLACEMENT"; s.deadline=now+480_000;
        s.winner=null; s.finishedAt=0; s.finishReason=null; s.settlement=null; s.presentationUntil=0; s.presentationReady.clear(); s.battleStarted=false; s.mineActions=0;s.supportActions=0;s.supportShipId=null; s.aiTemplate=new ArrayList<>();
        for(Player p:s.players) {
            p.ships=new ArrayList<>(); p.shots=new ArrayList<>(); p.radar=new ArrayList<>(); p.inventory=NavalEngine.emptyInventory();
            p.scrapMines=new ArrayList<>();p.mines=new ArrayList<>(); p.aa=new ArrayList<>(); p.coins=300; p.ready=false; p.stage="PLACEMENT"; p.continueRequested=false; p.left=false;
        }
        if("AI".equals(s.mode)) { Player ai=s.players.get(1); ai.ships=randomFleet(); ai.coins=300-ai.ships.stream().mapToInt(t->MODELS.get(t.kind)[2]).sum(); ai.ready=true; ai.stage="READY"; }
    }
    static void start(NavalState s,long now) {
        if(s.players.size()==2 && s.players.stream().allMatch(p->p.ready)) {
            s.phase="BATTLE"; s.battleStarted=true; s.turn=RANDOM.nextInt(2); s.deadline=now+30000; s.nextAiAt=now+4500; NavalEngine.waitForPresentation(s,now);
        }
    }
    static void deploy(NavalState s,int actor,Action a,long now) {
        Player p=s.players.get(actor);
        if(!"PLACEMENT".equals(s.phase) || p.ready) NavalEngine.bad("当前不能调整舰队");
        if(a.inventory!=null && a.inventory.values().stream().anyMatch(n->n==null || n!=0) || a.mines!=null && !a.mines.isEmpty() || a.aa!=null && !a.aa.isEmpty()) NavalEngine.bad("军事模式不支持道具");
        List<Ship> ships=a.ships==null?p.ships:a.ships;
        if(ships.stream().anyMatch(t->"torpedo".equals(t.kind)))NavalEngine.bad("鱼雷驱逐舰已取消，请改购鱼雷航母");
        int cost=validate(ships,a.ready); p.ships=ships; p.coins=300-cost;
        if(a.ready) { p.ready=true; p.stage="READY"; start(s,now); }
    }
    static void expirePreparation(NavalState s,long now) {
        for(Player p:s.players) if(!p.ready) {
            // An empty or unfinished purchase is not silently replaced by a different fleet.
            s.phase="FINISHED"; s.winner=s.players.size()==2?1-s.players.indexOf(p):null; s.finishedAt=now; s.deadline=0; s.finishReason="PREPARATION_TIMEOUT"; return;
        }
        start(s,now);
    }
    static Set<Cell> coverage(Player p) {
        Set<Cell> cells=new HashSet<>();
        for(Ship ship:p.ships) if("antiAir".equals(ship.kind) && parts(ship).stream().anyMatch(a->a.active))
            for(int y=ship.y-1;y<=ship.y+ship.h;y++) for(int x=ship.x-1;x<=ship.x+ship.w;x++) { Cell c=new Cell(x,y); if(inside(c)) cells.add(c); }
        return cells;
    }
    static List<Cell> targets(Action a,Ship ship) {
        List<Cell> result=new ArrayList<>();
        if("mine".equals(a.weapon)&&a.targets!=null&&!a.targets.isEmpty())return new ArrayList<>(a.targets);
        if("fighter".equals(a.weapon)) for(int i=0;i<6;i++) result.add(new Cell(a.x+i,a.y));
        else if("depthCharge".equals(a.weapon)){result.add(new Cell(a.x,a.y));result.add(new Cell(a.x+1,a.y));}
        else if("submarine".equals(a.weapon)) for(int i=0;i<3;i++) result.add(new Cell(a.x+i,a.y));
        else if("bomber".equals(a.weapon)) for(int y=0;y<3;y++)for(int x=0;x<3;x++)result.add(new Cell(a.x+x,a.y+y));
        else if("radar".equals(a.weapon)) for(int y=0;y<2;y++) for(int x=0;x<2;x++) result.add(new Cell(a.x+x,a.y+y));
        else if("battleship".equals(ship.kind)) for(int i=0;i<3;i++) result.add(new Cell(a.x+(ship.rotation%180!=0?i:0),a.y+(ship.rotation%180==0?i:0)));
        else result.add(new Cell(a.x,a.y));
        return result;
    }
    static void attack(NavalState s,int actor,Action a,long now) {
        NavalRepairSupport.normalizeShots(s);
        if(!"BATTLE".equals(s.phase) || s.turn!=actor || s.presentationUntil>now) NavalEngine.bad("尚未轮到你行动");
        if("repairSupport".equals(a.weapon)){NavalRepairSupport.attack(s,actor,a,now);return;}
        if(s.supportActions>0)NavalEngine.bad("请完成废雷修理或跳过");
        Player p=s.players.get(actor), enemy=s.players.get(1-actor);
        Ship ship=p.ships.stream().filter(t->Objects.equals(t.id,a.shipId)).findFirst().orElse(null);
        if(ship==null || parts(ship).stream().noneMatch(t->t.active && t.role.equals(a.weapon) && (!List.of("fighter","bomber","torpedo").contains(a.weapon)||t.index==a.plane)) || List.of("antiAir","jammer","repair","tow").contains(a.weapon)) NavalEngine.bad("该舰船部件已损毁或飞机已损失");
        if(s.mineActions>0&&!"mine".equals(a.weapon))NavalEngine.bad("请放置本回合第二颗水雷");
        List<Cell> target=targets(a,ship), damage=new ArrayList<>(target), route=new ArrayList<>(); List<Shot> hits=new ArrayList<>();
        if(target.stream().anyMatch(t->!inside(t))) NavalEngine.bad("目标区域不能超出海域");
        boolean destroyed=false; List<List<Cell>> torpedoes=new ArrayList<>();List<Shot> backfire=new ArrayList<>();
        if("mine".equals(a.weapon)){
            if(target.size()>2-s.mineActions||target.size()>10-ship.minesUsed||new HashSet<>(target).size()!=target.size()||target.stream().anyMatch(c->!NavalMilitaryAi.mineLegal(p,c)))NavalEngine.bad("每回合最多两颗水雷，位置不能重复或重叠");p.mines.addAll(target);ship.minesUsed+=target.size();damage.clear();
        } else if("depthCharge".equals(a.weapon)){
            NavalDepthCharge.Outcome e=NavalDepthCharge.attack(p,enemy,ship,a,RANDOM);hits.addAll(e.cells());route.addAll(e.route());backfire.addAll(e.backfire());torpedoes.addAll(e.paths());destroyed=e.destroyed();damage.clear();
        } else if("submarine".equals(a.weapon)){
            int before=p.inventory.getOrDefault("submarine",0);p.inventory.put("submarine",1);enemy.inventory.put("mine",enemy.mines.size());NavalState sim=new NavalState();sim.mode="MULTIPLAYER";sim.phase="BATTLE";sim.turn=actor;sim.players=s.players;a.targets=target;
            NavalEngine.attack(sim,actor,a,now);p.inventory.put("submarine",before);Event e=sim.events.stream().filter(t->"submarine".equals(t.weapon())).findFirst().orElseThrow();hits.addAll(e.cells());route.addAll(e.route());torpedoes=e.torpedoes();destroyed=e.destroyed();damage.clear();
            if(destroyed)for(int y=ship.y;y<ship.y+ship.h;y++)for(int x=ship.x;x<ship.x+ship.w;x++){
                if(!ship.hits.contains(x+","+y))ship.hits.add(x+","+y);
                final int cx=x,cy=y;
                if(p.shots.stream().noneMatch(t->t.x()==cx&&t.y()==cy)){Shot lost=new Shot(x,y,"hit");p.shots.add(lost);backfire.add(lost);}
            }
        }
        if("radar".equals(a.weapon)) {
            damage.clear();
            for(Cell c:target) { p.radar.removeIf(r->r.x()==c.x() && r.y()==c.y()); p.radar.add(new Radar(c.x(),c.y(),shipAt(enemy,c)!=null && !jamming(enemy).contains(c))); }
        } else if(List.of("fighter","bomber","torpedo").contains(a.weapon)) {
            boolean launched=false;damage.clear(); Set<Cell> aa=coverage(enemy); int end=a.x+("fighter".equals(a.weapon)?6:"bomber".equals(a.weapon)?3:1);
            for(int x=0;x<=end;x++) { Cell c=new Cell(x,a.y); route.add(c); if(aa.contains(c)) { destroyed=true; break; } if("torpedo".equals(a.weapon)&&x==a.x)launched=true;for(Cell t:target)if(t.x()==c.x())damage.add(t); }
            if("torpedo".equals(a.weapon)){damage.clear();if(launched)for(int x=a.x;x<12;x++){Cell c=new Cell(x,a.y);damage.add(c);if(shipAt(enemy,c)!=null||enemy.mines.contains(c)||enemy.scrapMines.contains(c))break;}}
            if("fighter".equals(a.weapon))damage.retainAll(sampleAir(target,enemy.shots.stream().filter(shot->!Boolean.TRUE.equals(shot.uncertain())).toList(),4+RANDOM.nextInt(2)));
            if("bomber".equals(a.weapon))damage.retainAll(sampleAir(target,enemy.shots.stream().filter(shot->!Boolean.TRUE.equals(shot.uncertain())).toList(),5+RANDOM.nextInt(2)));
            if(destroyed) { if(!ship.lostPlanes.contains(a.plane)) ship.lostPlanes.add(a.plane); }
            else for(int i=route.size()-2;i>=0;i--) route.add(route.get(i));
        } else if("battleship".equals(ship.kind)) {
            List<Cell> pool=new ArrayList<>(target);damage.clear();long known=target.stream().filter(c->enemy.shots.stream().anyMatch(t->t.x()==c.x()&&t.y()==c.y()&&"hit".equals(t.result())&&!Boolean.TRUE.equals(t.uncertain()))).count();
            for(int n=0;n<parts(ship).stream().filter(t->t.active).count();n++){List<Double> weights=new ArrayList<>();for(Cell c:pool){boolean hit=enemy.shots.stream().anyMatch(t->t.x()==c.x()&&t.y()==c.y()&&"hit".equals(t.result())&&!Boolean.TRUE.equals(t.uncertain()));double fresh=known==1?.35:.45;weights.add(known==0||known==3?1.0/3:hit?(1-(3-known)*fresh)/known:fresh);}double r=RANDOM.nextDouble()*weights.stream().mapToDouble(v->v).sum();int index=weights.size()-1;for(int i=0;i<weights.size();i++){r-=weights.get(i);if(r<=0){index=i;break;}}damage.add(pool.remove(index));}
        }
        for(Cell c:damage) {
            if(enemy.scrapMines.remove(c)){hits.add(new Shot(c.x(),c.y(),"mine","scrap"));continue;}
            Shot previous=enemy.shots.stream().filter(t->t.x()==c.x() && t.y()==c.y()).findFirst().orElse(null);
            if(previous!=null&&!Boolean.TRUE.equals(previous.uncertain())&&(shipAt(enemy,c)==null||shipAt(enemy,c).hits.contains(key(c)))){if("shot".equals(a.weapon))hits.add(new Shot(c.x(),c.y(),"miss"));else if("torpedo".equals(a.weapon)&&shipAt(enemy,c)!=null)hits.add(previous);continue;}
            if(enemy.mines.remove(c)){Shot mine=new Shot(c.x(),c.y(),"mine");enemy.shots.add(mine);hits.add(mine);if(List.of("shot","fighter","bomber").contains(a.weapon)&&p.shots.stream().noneMatch(t->t.x()==c.x()&&t.y()==c.y()&&!Boolean.TRUE.equals(t.uncertain()))){Ship own=shipAt(p,c);if(own!=null&&!own.hits.contains(key(c)))own.hits.add(key(c));Shot rebound=new Shot(c.x(),c.y(),own==null?"miss":"hit");NavalRepairSupport.recordShot(p,rebound);backfire.add(rebound);}continue;}
            Ship victim=shipAt(enemy,c); if(victim!=null&&!victim.hits.contains(key(c))) victim.hits.add(key(c));
            Shot hit=new Shot(c.x(),c.y(),victim==null?"miss":"hit"); NavalRepairSupport.recordShot(enemy,hit); hits.add(hit);
        }
        collapseSubmarines(enemy,hits);collapseSubmarines(p,backfire);
        s.events.add(new Event(++s.sequence,actor,a.weapon,hits,p.name+" · "+shipName(ship.kind)+(destroyed?("depthCharge".equals(a.weapon)?"：突击舰沉没":"：飞机被击落"):"：行动完成"),a.x,a.y,null,target,route,torpedoes,destroyed,backfire,ship.id,a.plane));
        if(defeated(enemy) || defeated(p)) { s.phase="FINISHED"; s.winner=defeated(enemy)?actor:1-actor; s.finishedAt=now; s.deadline=0; s.finishReason="MILITARY_DEFEAT"; }
        else {int count="mine".equals(a.weapon)?s.mineActions+target.size():0;boolean again=count==1&&p.ships.stream().flatMap(t->parts(t).stream()).anyMatch(t->t.active&&"mine".equals(t.role));s.mineActions=again?count:0;s.turn=again?actor:1-actor;if(!again)s.deadline=now+30000;s.nextAiAt=now+4500;if(!again)NavalEngine.waitForPresentation(s,now);}
    }
    static List<Cell> sampleAir(List<Cell> targets,List<Shot> shots,int count){Set<Cell> hit=new HashSet<>();for(Shot s:shots)if("hit".equals(s.result()))hit.add(new Cell(s.x(),s.y()));List<Cell> old=new ArrayList<>(),fresh=new ArrayList<>();for(Cell c:targets)(hit.contains(c)?old:fresh).add(c);int n=Math.min(count,targets.size());double expected=Math.min(n,fresh.size()*Math.min(1,n/(double)targets.size()+old.size()*.02));int base=(int)Math.floor(expected),take=Math.max(n-old.size(),Math.min(fresh.size(),base+(RANDOM.nextDouble()<expected-base?1:0)));Collections.shuffle(fresh,RANDOM);Collections.shuffle(old,RANDOM);List<Cell> out=new ArrayList<>(fresh.subList(0,take));out.addAll(old.subList(0,n-take));return out;}
    static Ship shipAt(Player p,Cell c) { return p.ships.stream().filter(s->c.x()>=s.x && c.x()<s.x+s.w && c.y()>=s.y && c.y()<s.y+s.h).findFirst().orElse(null); }
    static String shipName(String kind) { return switch(kind) { case "repairTug" -> "修理拖船";case "carrier" -> "航母（战斗机）"; case "bomberCarrier" -> "航母（轰炸机）";case "torpedoCarrier" -> "航母（鱼雷机）";case "depthCharge" -> "深水炸弹驱逐舰";case "submarine" -> "潜艇";case "mineLayer" -> "布雷驱逐舰";case "jammer" -> "干扰驱逐舰"; case "battleship" -> "战列舰"; case "cruiser" -> "巡洋舰"; case "torpedo" -> "鱼雷驱逐舰"; default -> "雷达驱逐舰"; }; }
    static void collapseSubmarines(Player p,List<Shot> effects){for(Ship ship:p.ships)if("submarine".equals(ship.kind)&&ship.hits.contains(key(cell(ship,0,1))))for(Cell c:NavalMilitaryAi.cells(ship))if(!ship.hits.contains(key(c))){ship.hits.add(key(c));Shot shot=new Shot(c.x(),c.y(),"hit");p.shots.add(shot);effects.add(shot);}}
    static void ai(NavalState s,long now) { aiStrategy(s,now); }
    static void aiStrategy(NavalState s,long now){Action a=NavalMilitaryAi.choose(s);if(a!=null)attack(s,1,a,now);else{s.turn=0;s.deadline=now+30000;}}
}
