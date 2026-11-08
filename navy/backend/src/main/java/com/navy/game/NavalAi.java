package com.navy.game;

import com.navy.game.NavalState.*;
import java.security.SecureRandom;
import java.util.*;
import java.util.random.RandomGenerator;

/** Public-information targeting shared by all server AI rooms. */
final class NavalAi {
    private static final RandomGenerator RANDOM = new SecureRandom();
    private static String key(int x,int y) { return x+","+y; }
    private static <T> T pick(List<T> values,RandomGenerator random) { return values.get(random.nextInt(values.size())); }
    record Choice(String weapon,int x,int y,double score) {}
    static final class Knowledge {
        final Set<String> attacked=new HashSet<>(), excluded=new HashSet<>(), hits=new HashSet<>(), blockers=new HashSet<>();
        final List<Cell> unknown=new ArrayList<>(), ends=new ArrayList<>(), adjacent=new ArrayList<>();
        final List<Integer> remaining=new ArrayList<>(List.of(4,3,3,2,2,2,1,1,1,1));
        boolean valid(int x,int y) { return x>=0 && y>=0 && x<12 && y<12 && !attacked.contains(key(x,y)) && !excluded.contains(key(x,y)); }
        Knowledge(Player target) {
            for(Ship ship:target.ships) if(NavalEngine.sunk(ship)) {
                remaining.remove(Integer.valueOf(ship.w*ship.h));
                for(int y=ship.y-1;y<=ship.y+ship.h;y++) for(int x=ship.x-1;x<=ship.x+ship.w;x++) excluded.add(key(x,y));
            }
            for(Shot shot:target.shots) { attacked.add(key(shot.x(),shot.y())); if("hit".equals(shot.result())) { blockers.add(key(shot.x(),shot.y())); if(!excluded.contains(key(shot.x(),shot.y()))) hits.add(key(shot.x(),shot.y())); } }
            for(int y=0;y<12;y++) for(int x=0;x<12;x++) if(valid(x,y)) unknown.add(new Cell(x,y));
            for(Shot shot:target.shots) if(hits.contains(key(shot.x(),shot.y()))) {
                for(int[] axis:new int[][]{{1,0},{0,1}}) if(hits.contains(key(shot.x()+axis[0],shot.y()+axis[1]))) for(int sign:new int[]{-1,1}) {
                    int x=shot.x(),y=shot.y(); do { x+=axis[0]*sign; y+=axis[1]*sign; } while(hits.contains(key(x,y)));
                    if(valid(x,y)) ends.add(new Cell(x,y));
                }
                for(int[] d:new int[][]{{1,0},{-1,0},{0,1},{0,-1}}) if(valid(shot.x()+d[0],shot.y()+d[1])) adjacent.add(new Cell(shot.x()+d[0],shot.y()+d[1]));
            }
        }
    }
    static Cell hunt(Knowledge knowledge,RandomGenerator random) {
        boolean edges=random.nextDouble()<.18;
        for(int length:new LinkedHashSet<>(knowledge.remaining)) {
            List<List<Cell>> lines=new ArrayList<>(), border=new ArrayList<>();
            for(int y=0;y<12;y++) for(int x=0;x<12;x++) for(int[] d:length==1?new int[][]{{1,0}}:new int[][]{{1,0},{0,1}}) {
                List<Cell> line=new ArrayList<>(); boolean valid=true;
                for(int i=0;i<length;i++) { int cx=x+i*d[0],cy=y+i*d[1]; if(!knowledge.valid(cx,cy)) { valid=false; break; } line.add(new Cell(cx,cy)); }
                if(valid) { lines.add(line); if(line.stream().anyMatch(p->p.x()==0 || p.y()==0 || p.x()==11 || p.y()==11)) border.add(line); }
            }
            if(lines.isEmpty()) continue;
            List<Cell> line=pick(edges && !border.isEmpty()?border:lines,random);
            if(random.nextDouble()>=.9) return pick(line,random);
            return line.get(length%2==1?length/2:length/2-1+random.nextInt(2));
        }
        return knowledge.unknown.isEmpty()?null:pick(knowledge.unknown,random);
    }
    static List<Choice> weapons(Player ai,Knowledge knowledge) {
        List<Choice> options=new ArrayList<>();
        for(String weapon:List.of("fighter","bomber","torpedo","radar")) {
            if(ai.inventory.getOrDefault(weapon,0)<=0) continue;
            int w="fighter".equals(weapon)?8:"torpedo".equals(weapon)?1:3,h="fighter".equals(weapon)?2:"torpedo".equals(weapon)?1:3,best=0;
            List<Choice> choices=new ArrayList<>();
            for(int y=0;y<=12-h;y++) for(int x=0;x<=12-w;x++) {
                int score=0;
                for(int row=y;row<y+h;row++) for(int col=x;col<("torpedo".equals(weapon)?12:x+w);col++) { if(knowledge.valid(col,row)) score++; if("torpedo".equals(weapon)&&knowledge.blockers.contains(key(col,row)))break; }
                if(score==0 || score<best) continue;
                if(score>best) { best=score; choices.clear(); } choices.add(new Choice(weapon,x,y,"fighter".equals(weapon) ? score/2.0 : score));
            }
            options.addAll(choices);
        }
        return options;
    }
    static Choice choose(NavalState state,RandomGenerator random) {
        Player ai=state.players.get(1); Knowledge knowledge=new Knowledge(state.players.get(0));
        if(!knowledge.ends.isEmpty()) { Cell p=pick(knowledge.ends,random); return new Choice("shot",p.x(),p.y(),1); }
        List<Cell> targets=new ArrayList<>(knowledge.adjacent);
        for(Radar radar:ai.radar) if(radar.occupied() && knowledge.valid(radar.x(),radar.y())) targets.add(new Cell(radar.x(),radar.y()));
        Cell target=targets.isEmpty()?hunt(knowledge,random):pick(targets,random);
        if(target==null) return null;
        List<Choice> options=weapons(ai,knowledge);
        if(targets.isEmpty() && !options.isEmpty() && random.nextDouble()<.42) {
            Event prior=null; for(int i=state.events.size()-1;i>=0;i--) { Event event=state.events.get(i); if(event.player()==1 && List.of("fighter","bomber","torpedo").contains(event.weapon())) { prior=event; break; } }
            if(prior!=null && List.of("bomber","torpedo").contains(prior.weapon())) {
                int row=prior.row()!=null?prior.row():prior.y()==null?-1:prior.y();
                List<Choice> follow=options.stream().filter(o->"fighter".equals(o.weapon()) && row>=o.y() && row<o.y()+2).toList();
                if(!follow.isEmpty() && random.nextDouble()<.7) return pick(follow,random);
            }
            List<Choice> bait=options.stream().filter(o->List.of("bomber","torpedo").contains(o.weapon())).toList();
            boolean fighter=options.stream().anyMatch(o->"fighter".equals(o.weapon()));
            return pick(fighter && !bait.isEmpty() && random.nextDouble()<.55?bait:options,random);
        }
        return new Choice("shot",target.x(),target.y(),1);
    }
    static void attack(NavalState state,long now) { Choice choice=choose(state,RANDOM); if(choice!=null) NavalEngine.attack(state,1,choice.weapon(),choice.x(),choice.y(),now); }
    static List<Cell> mines(List<Ship> ships,int count,RandomGenerator random) {
        record Weighted(Cell cell,double rank) {}
        List<Weighted> options=new ArrayList<>();
        for(int y=0;y<12;y++) for(int x=0;x<12;x++) {
            boolean occupied=false,shield=false;
            for(Ship ship:ships) { if(y>=ship.y && y<ship.y+ship.h) { if(x>=ship.x && x<ship.x+ship.w) occupied=true; if(ship.x-x>=1 && ship.x-x<=5) shield=true; } }
            if(!occupied) options.add(new Weighted(new Cell(x,y),-Math.log(Math.max(.000001,random.nextDouble()))/(1+(x<3?4:0)+(shield?8:0))));
        }
        options.sort(Comparator.comparingDouble(Weighted::rank)); return options.stream().limit(count).map(Weighted::cell).toList();
    }
    static List<Cell> mines(List<Ship> ships,int count) { return mines(ships,count,RANDOM); }
}
