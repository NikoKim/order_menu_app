package com.order.menu.module.entertainment.mode.naval;

import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.order.menu.common.exception.BusinessException;
import com.order.menu.module.entertainment.mode.naval.NavalState.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class NavalItemsTest {
    @Test void partialSubmarineEntryOnlyExplodesCellsAlreadyInside() {
        for(int mineX:List.of(0,1,2,5)) {
            NavalState state=battle(); state.players.get(0).inventory.put("submarine",1); state.players.get(1).mines.add(new Cell(mineX,5)); state.players.get(1).inventory.put("mine",1);
            NavalEngine.attack(state,0,submarine(),2000);
            Event attack=state.events.stream().filter(event->event.weapon().equals("submarine")).findFirst().orElseThrow();
            assertTrue(attack.destroyed()); assertEquals(Math.min(3,mineX+1),attack.cells().size()); assertEquals(mineX-2,attack.route().get(attack.route().size()-1).x());
            assertTrue(attack.cells().stream().allMatch(cell->cell.x()>=0 && cell.x()<=mineX)); assertTrue(attack.torpedoes().isEmpty());
        }
    }
    @Test void fighterChoosesOneRowAndOnlySpendsTheCorrectDefensiveGun() {
        var seen=new java.util.HashSet<Integer>();
        for(int trial=0;trial<40;trial++) for(List<Integer> rows:List.of(List.<Integer>of(),List.of(0),List.of(1),List.of(0,1),List.of(1,0))) {
            NavalState state=battle(); state.players.get(0).inventory.put("fighter",1); Player target=state.players.get(1); target.aa.addAll(rows); target.inventory.put("antiAir",rows.size());
            NavalEngine.attack(state,0,"fighter",0,0,2000);
            Event attack=state.events.stream().filter(event->event.weapon().equals("fighter")).findFirst().orElseThrow();
            seen.add(attack.y()); assertTrue(attack.y()==0 || attack.y()==1);
            if(rows.isEmpty()) { assertEquals(8,attack.cells().size()); assertTrue(attack.cells().stream().allMatch(cell->cell.y()==attack.y())); }
            else { assertTrue(attack.cells().isEmpty()); assertEquals(rows.contains(attack.y())?attack.y():rows.get(0),attack.row()); assertEquals(rows.size()-1,target.aa.size()); assertEquals(rows.size()-1,target.inventory.get("antiAir")); }
        }
        assertEquals(java.util.Set.of(0,1),seen);
        assertEquals(10,NavalEngine.PRICES.get("stealth")); assertEquals(25,NavalEngine.PRICES.get("torpedo"));
    }
    @Test void surfaceWeaponsReboundMinesWithoutRecursiveChainsOrDuplicateDamage() throws Exception {
        for(String weapon:List.of("shot","fighter","bomber")) {
            NavalState state=battle(); Player own=state.players.get(0),enemy=state.players.get(1);
            own.inventory.put(weapon,1); own.ships=List.of(new Ship("own",0,0,1,1),new Ship("reserve",11,11,1,1)); enemy.ships=List.of(new Ship("enemy",11,10,1,1));
            for(int y=0;y<3;y++) for(int x=0;x<3;x++) enemy.mines.add(new Cell(x,y)); enemy.inventory.put("mine",9);
            own.mines.add(new Cell(1,0)); own.inventory.put("mine",1);
            NavalEngine.attack(state,0,weapon,0,0,2000); Event event=state.events.get(state.events.size()-1);
            assertFalse(event.backfire().isEmpty()); assertEquals(event.cells().stream().filter(cell->"mine".equals(cell.result())).count(),event.backfire().size());
            for(Shot cell:event.backfire()) assertTrue(own.shots.contains(cell));
            assertTrue(own.ships.get(0).hits.size()<=1);
            ObjectMapper mapper=new ObjectMapper(); assertEquals(event,mapper.readValue(mapper.writeValueAsBytes(event),Event.class));
        }
    }
    @Test void torpedoesAndSubmarinesNeverReboundMines() {
        NavalState state=battle(); state.players.get(0).inventory.put("torpedo",1);
        state.players.get(1).mines.add(new Cell(0,0));
        NavalEngine.attack(state,0,"torpedo",0,0,2000);
        assertTrue(state.events.get(state.events.size()-1).backfire().isEmpty()); assertTrue(state.players.get(0).shots.isEmpty());
        state=battle(); state.players.get(0).inventory.put("submarine",1); state.players.get(1).mines.add(new Cell(0,5));
        NavalEngine.attack(state,0,submarine(),2000);
        assertTrue(state.events.get(state.events.size()-1).backfire().isEmpty()); assertTrue(state.players.get(0).shots.isEmpty());
    }
    @Test void mineReboundCanEndBattleAndDoesNotCountPreviouslyHitHullAgain() {
        NavalState state=battle(); Player own=state.players.get(0),enemy=state.players.get(1);
        own.ships=List.of(new Ship("last",0,0,1,1)); enemy.ships=List.of(new Ship("enemy",11,11,1,1)); enemy.mines.add(new Cell(0,0));
        NavalEngine.attack(state,0,"shot",0,0,2000); assertEquals(1,state.winner); assertEquals("FINISHED",state.phase);
    }
    @Test void aiContinuesTheAxisOfTwoAdjacentHits() {
        for(int trial=0;trial<30;trial++) {
            NavalState state=battle(); state.mode="AI"; state.turn=1; state.nextAiAt=0;
            Ship ship=state.players.get(0).ships.stream().filter(s->s.w*s.h==4).findFirst().orElseThrow();
            int dx=ship.w>1?1:0,dy=ship.h>1?1:0;
            for(int i=1;i<=2;i++) { int x=ship.x+i*dx,y=ship.y+i*dy; ship.hits.add(x+","+y); state.players.get(0).shots.add(new Shot(x,y,"hit")); }
            state.players.get(1).inventory.put("fighter",3);
            NavalEngine.advance(state,1001); Event attack=state.events.get(state.events.size()-1);
            assertEquals("shot",attack.weapon());
            for(Shot cell:attack.cells()) assertTrue((cell.x()==ship.x&&cell.y()==ship.y)||(cell.x()==ship.x+3*dx&&cell.y()==ship.y+3*dy));
        }
    }
    private NavalState battle() {
        NavalState state=new NavalState(); state.id="items"; state.mode="MULTIPLAYER";
        state.players.add(NavalEngine.player("U:1","A")); state.players.add(NavalEngine.player("U:2","B"));
        NavalEngine.beginPlacement(state,1000);
        for (int i=0;i<2;i++) NavalEngine.layoutReady(state,i,null,1000);
        for (int i=0;i<2;i++) NavalEngine.ready(state,i,1000);
        state.turn=0; return state;
    }
    private Action submarine() {
        Action action=new Action(); action.type="ATTACK"; action.weapon="submarine";
        action.targets=List.of(new Cell(4,5),new Cell(5,5),new Cell(6,5)); return action;
    }
    @Test void submarineLaunchesBothDirectionsAndIgnoresAntiAir() throws Exception {
        NavalState state=battle(); state.players.get(0).inventory.put("submarine",3); state.players.get(1).aa.add(5);
        NavalEngine.attack(state,0,submarine(),2000);
        Event event=state.events.get(state.events.size()-1);
        assertEquals("submarine",event.weapon()); assertFalse(event.destroyed()); assertEquals(2,event.torpedoes().size());
        assertEquals(2,state.players.get(0).inventory.get("submarine")); assertEquals(1,state.players.get(1).aa.size());
        for (int i=0;i<2;i++) {
            List<Cell> path=event.torpedoes().get(i); Cell start=path.get(0); int direction=i==0?-1:1;
            assertTrue(submarine().targets.contains(start));
            for (int j=1;j<path.size();j++) { assertEquals(start.x(),path.get(j).x()); assertEquals(direction,Integer.signum(path.get(j).y()-start.y())); }
        }
        ObjectMapper mapper=new ObjectMapper(); NavalState restored=mapper.readValue(mapper.writeValueAsBytes(state),NavalState.class);
        assertEquals(event,restored.events.get(restored.events.size()-1));
    }
    @Test void transitMineDestroysHullBeforeEitherTorpedoLaunches() {
        NavalState state=battle(); state.players.get(0).inventory.put("submarine",1);
        state.players.get(1).mines.add(new Cell(1,5)); state.players.get(1).inventory.put("mine",1);
        NavalEngine.attack(state,0,submarine(),2000); Event event=state.events.get(state.events.size()-1);
        assertTrue(event.destroyed()); assertTrue(event.torpedoes().isEmpty()); assertEquals(2,event.cells().size());
        assertEquals(1,event.cells().stream().filter(cell -> "mine".equals(cell.result())).count());
        assertTrue(state.players.get(1).mines.isEmpty()); assertEquals(0,state.players.get(1).inventory.get("mine"));
    }
    @Test void invalidSubmarineDoesNotConsumeAnything() {
        NavalState state=battle(); state.players.get(0).inventory.put("submarine",2); Action action=submarine();
        action.targets=List.of(new Cell(4,5),new Cell(4,5),new Cell(5,5));
        assertThrows(BusinessException.class,()->NavalEngine.attack(state,0,action,2000));
        action.targets=List.of(new Cell(4,5),new Cell(5,6),new Cell(6,5));
        assertThrows(BusinessException.class,()->NavalEngine.attack(state,0,action,2000)); assertEquals(2,state.players.get(0).inventory.get("submarine"));
    }
    @Test void coatingHasAuthoritativeBudgetDeploymentAndRadarRules() throws Exception {
        NavalState state=battle(); state.phase="SUPPLY"; Player player=state.players.get(0); player.ready=false; player.stage="SUPPLY";
        Ship ship=player.ships.get(0); Action deploy=new Action(); deploy.inventory=Map.of("stealth",1); deploy.coatedShips=List.of(ship.id);
        NavalEngine.deploy(state,0,deploy,2000); assertEquals(290,player.coins); assertTrue(player.ships.get(0).stealth);
        deploy.coatedShips=List.of("forged"); assertThrows(BusinessException.class,()->NavalEngine.deploy(state,0,deploy,2000));
        deploy.coatedShips=List.of(); NavalEngine.deploy(state,0,deploy,2000);
        assertThrows(BusinessException.class,()->NavalEngine.ready(state,0,2000));
        NavalEngine.advance(state,state.deadline+NavalEngine.SUPPLY_MS+1); assertEquals(1,player.ships.stream().filter(s->s.stealth).count());
        state.phase="BATTLE"; state.turn=1; state.players.get(1).inventory.put("radar",1);
        Ship coated=player.ships.stream().filter(s->s.stealth).findFirst().orElseThrow();
        int x=Math.min(9,coated.x), y=Math.min(9,coated.y);
        NavalEngine.attack(state,1,"radar",x,y,3000);
        assertFalse(state.players.get(1).radar.stream().filter(c->c.x()==coated.x && c.y()==coated.y).findFirst().orElseThrow().occupied());
        state.turn=1; NavalEngine.attack(state,1,"shot",coated.x,coated.y,4000); assertEquals("hit",player.shots.get(0).result());
        ObjectMapper mapper=new ObjectMapper(); assertTrue(mapper.readValue(mapper.writeValueAsBytes(state),NavalState.class).players.get(0).ships.stream().anyMatch(s->s.stealth));
    }
}
