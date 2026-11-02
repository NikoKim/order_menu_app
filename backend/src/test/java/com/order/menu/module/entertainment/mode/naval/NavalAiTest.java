package com.order.menu.module.entertainment.mode.naval;

import static org.junit.jupiter.api.Assertions.*;
import com.order.menu.module.entertainment.mode.naval.NavalState.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class NavalAiTest {
    private Player corridor(int length) {
        Player player=NavalEngine.player("target","Target");
        for(int y=0;y<12;y++) for(int x=0;x<12;x++) if(y!=6 || x<4 || x>=4+length) player.shots.add(new Shot(x,y,"miss"));
        return player;
    }
    private Ship sunk(int length,int y) {
        Ship ship=new Ship("s"+y,0,y,length,1); for(int i=0;i<length;i++) ship.hits.add(i+","+y); return ship;
    }
    @Test void largestHullHuntUsesMiddleAndReplansAfterMiss() {
        Player target=corridor(4); var knowledge=new NavalAi.Knowledge(target);
        for(int seed=0;seed<50;seed++) { Cell p=NavalAi.hunt(knowledge,new Random(seed)); assertEquals(6,p.y()); assert(p.x()>=4 && p.x()<=7); }
        target.ships.add(sunk(4,0)); target=withCorridor(target,3);
        for(int seed=0;seed<50;seed++) { Cell p=NavalAi.hunt(new NavalAi.Knowledge(target),new Random(seed)); assertEquals(6,p.y()); assert(p.x()>=4 && p.x()<=6); }
        target.shots.add(new Shot(5,6,"miss"));
        assertNotEquals(new Cell(5,6),NavalAi.hunt(new NavalAi.Knowledge(target),new Random(1)));
    }
    private Player withCorridor(Player previous,int length) { Player next=corridor(length); next.ships=previous.ships; return next; }
    @Test void weaponsMaximizeEffectiveCoverageWithoutReadingHiddenHulls() {
        Player target=NavalEngine.player("p","P"),ai=NavalEngine.player("AI","AI"); target.ships.add(sunk(4,0)); target.shots.add(new Shot(8,8,"miss"));
        ai.inventory.putAll(Map.of("fighter",1,"bomber",1,"torpedo",1,"radar",1));
        var options=NavalAi.weapons(ai,new NavalAi.Knowledge(target));
        for(var entry:Map.of("fighter",8,"bomber",9,"torpedo",12,"radar",9).entrySet()) {
            var choices=options.stream().filter(o -> entry.getKey().equals(o.weapon())).toList();
            assertFalse(choices.isEmpty()); assertTrue(choices.stream().allMatch(o -> o.score()==entry.getValue()));
        }
        NavalState state=new NavalState(); state.players.add(target); state.players.add(ai);
        var first=NavalAi.choose(state,new Random(9)); target.ships.add(new Ship("hidden",7,6,3,1));
        assertEquals(first,NavalAi.choose(state,new Random(9)));
    }
    @Test void mineScreensAreLegalAndFavorFrontOrHullShields() {
        List<Ship> ships=List.of(new Ship("s",6,6,4,1)); Random random=new Random(17); int favored=0;
        for(int i=0;i<100;i++) {
            var mines=NavalAi.mines(ships,6,random); assertEquals(6,new HashSet<>(mines).size());
            for(Cell p:mines) { assertFalse(p.y()==6 && p.x()>=6 && p.x()<10); if(p.x()<3 || p.y()==6 && p.x()>=1 && p.x()<=5) favored++; }
        }
        assertTrue(favored>330);
    }
}
