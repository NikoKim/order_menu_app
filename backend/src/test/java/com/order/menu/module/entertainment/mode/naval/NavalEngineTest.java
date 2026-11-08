package com.order.menu.module.entertainment.mode.naval;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.order.menu.common.exception.BusinessException;
import com.order.menu.module.entertainment.mode.naval.NavalState.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class NavalEngineTest {
    @Test void preBattleExitsAreCancelledInAllModes() {
        NavalState waiting = new NavalState(); waiting.mode="MULTIPLAYER"; waiting.players.add(NavalEngine.player("G:1","甲"));
        NavalEngine.leave(waiting,0,1_000);
        assertNull(waiting.winner); assertEquals("CANCELLED",waiting.finishReason);
        for (String phase : List.of("PLACEMENT","SUPPLY")) {
            NavalState ai = room(); ai.mode="AI"; ai.phase=phase;
            NavalEngine.leave(ai,0,1_000);
            assertNull(ai.winner); assertEquals("CANCELLED",ai.finishReason);
        }
        NavalState online = room(); NavalEngine.leave(online,0,1_000);
        assertNull(online.winner); assertEquals("CANCELLED",online.finishReason);
        NavalState ai = battle(); ai.mode="AI"; NavalEngine.leave(ai,0,3_000);
        assertEquals(1,ai.winner);
    }

    @Test void multiplayerDisconnectHasThirtySecondGraceAndRunsBeforeTurnTimers() {
        NavalState online = room(); online.connectionTracking=true;
        online.players.get(0).lastSeenAt=1_000; online.players.get(0).disconnectedAt=2_000;
        online.players.get(1).lastSeenAt=31_999;
        NavalEngine.advance(online,31_999); assertEquals("PLACEMENT",online.phase);
        NavalEngine.advance(online,32_000);
        assertEquals("FINISHED",online.phase); assertNull(online.winner); assertTrue(online.players.get(0).left);
        assertEquals("DISCONNECTED",online.finishReason);
        NavalState lostBrowser = battle(); lostBrowser.connectionTracking=true;
        lostBrowser.players.get(0).lastSeenAt=1_000; lostBrowser.players.get(1).lastSeenAt=31_000;
        NavalEngine.advance(lostBrowser,31_000); assertEquals(1,lostBrowser.winner);
        NavalState local = room(); local.mode="AI"; local.connectionTracking=true;
        local.players.forEach(player -> player.lastSeenAt=1_000);
        NavalEngine.advance(local,31_000); assertEquals("PLACEMENT",local.phase);
    }

    private NavalState room() {
        NavalState state = new NavalState(); state.id="test"; state.mode="MULTIPLAYER";
        state.players.add(NavalEngine.player("U:1","甲")); state.players.add(NavalEngine.player("G:2","乙"));
        NavalEngine.beginPlacement(state,1_000);
        state.players.forEach(player -> player.ships = NavalEngine.randomFleet());
        return state;
    }

    private NavalState battle() {
        NavalState state=room(); NavalEngine.layoutReady(state,0,null,2_000); NavalEngine.layoutReady(state,1,null,2_000); NavalEngine.ready(state,0,2_000); NavalEngine.ready(state,1,2_000); state.turn=0; return state;
    }

    @Test void generatedFleetsAlwaysContainTenLegalShipsAndTwentyCells() {
        for (int i=0;i<100;i++) {
            List<Ship> fleet=NavalEngine.randomFleet(); NavalEngine.validateFleet(fleet);
            assertEquals(10,fleet.size());
            assertEquals(20,fleet.stream().mapToInt(s -> s.w*s.h).sum());
            assertTrue(fleet.stream().allMatch(s -> s.w==1 || s.h==1));
        }
    }

    @Test void validatesDimensionsBoundsAdjacencyAndDuplicateIds() {
        List<Ship> fleet=NavalEngine.randomFleet(); fleet.get(0).x=-1;
        assertThrows(BusinessException.class,() -> NavalEngine.validateFleet(fleet));
        List<Ship> duplicate=NavalEngine.randomFleet(); duplicate.get(1).id=duplicate.get(0).id;
        assertThrows(BusinessException.class,() -> NavalEngine.validateFleet(duplicate));
        List<Ship> touching=NavalEngine.randomFleet(); touching.get(1).x=touching.get(0).x; touching.get(1).y=touching.get(0).y;
        assertThrows(BusinessException.class,() -> NavalEngine.validateFleet(touching));
        assertThrows(BusinessException.class,() -> NavalEngine.validateFleet(List.of()));
    }

    @Test void deploymentEnforcesBudgetCapsMinesAndCompleteDefensesBeforeReady() {
        NavalState state=room(); Action action=new Action(); action.ships=state.players.get(0).ships;
        NavalEngine.layoutReady(state,0,null,2_000);
        NavalEngine.layoutReady(state,1,null,2_000);
        action.inventory=Map.of("fighter",4);
        assertThrows(BusinessException.class,() -> NavalEngine.deploy(state,0,action,2_000));
        action.inventory=Map.of("fighter",3,"bomber",3,"radar",2,"torpedo",3);
        assertThrows(BusinessException.class,() -> NavalEngine.deploy(state,0,action,2_000));
        action.inventory=Map.of("mine",1); Ship ship=action.ships.get(0); action.mines=List.of(new Cell(ship.x,ship.y));
        assertThrows(BusinessException.class,() -> NavalEngine.deploy(state,0,action,2_000));
        action.mines=List.of(); NavalEngine.deploy(state,0,action,2_000);
        assertEquals(295,state.players.get(0).coins);
        assertThrows(BusinessException.class,() -> NavalEngine.ready(state,0,2_000));
        NavalEngine.advance(state,182_001); assertEquals("BATTLE",state.phase); assertEquals(1,state.players.get(0).mines.size());
    }

    @Test void readyStartsEarlyAndHitGrantsExtraThirtySecondsButMissChangesTurn() {
        NavalState state=battle(); Player target=state.players.get(1); Ship ship=target.ships.get(0);
        NavalEngine.attack(state,0,"shot",ship.x,ship.y,5_000);
        assertEquals(0,state.turn); assertEquals(35_000,state.deadline); assertEquals("hit",target.shots.get(0).result());
        assertThrows(BusinessException.class,() -> NavalEngine.attack(state,0,"shot",ship.x,ship.y,6_000));
        Cell water=water(target); NavalEngine.attack(state,0,"shot",water.x(),water.y(),6_000);
        assertEquals(1,state.turn); assertEquals(36_000,state.deadline);
        assertThrows(BusinessException.class,() -> NavalEngine.attack(state,0,"shot",0,0,7_000));
        NavalEngine.advance(state,36_001); assertEquals(0,state.turn); assertEquals(66_001,state.deadline);
    }

    @Test void airDefenseInterceptsOnlyOnceAndAircraftAreConsumed() {
        NavalState state=battle(); Player attacker=state.players.get(0), target=state.players.get(1);
        attacker.inventory.put("fighter",2); target.inventory.put("antiAir",1); target.aa.add(1);
        NavalEngine.attack(state,0,"fighter",0,0,5_000);
        assertTrue(target.shots.isEmpty()); assertTrue(target.aa.isEmpty()); assertEquals(0,target.inventory.get("antiAir"));
        assertEquals(1,attacker.inventory.get("fighter")); assertEquals(1,state.turn);
        state.turn=0; NavalEngine.attack(state,0,"fighter",0,0,6_000); assertEquals(8,target.shots.size());
    }

    @Test void bomberAttacksFiveToSevenDistinctCellsInThreeByThreeArea() {
        for (int i=0;i<20;i++) {
            NavalState state=battle(); state.players.get(0).inventory.put("bomber",1);
            NavalEngine.attack(state,0,"bomber",3,4,5_000);
            List<Shot> shots=state.players.get(1).shots;
            assertTrue(shots.size()>=5 && shots.size()<=7);
            assertEquals(shots.size(),shots.stream().map(s -> s.x()+","+s.y()).distinct().count());
            assertTrue(shots.stream().allMatch(s -> s.x()>=3 && s.x()<6 && s.y()>=4 && s.y()<7));
        }
    }

    @Test void torpedoStopsAtFirstShipOrMine() {
        NavalState state=battle(); Player target=state.players.get(1);
        target.ships=List.of(new Ship("s",5,3,1,1)); target.mines.add(new Cell(2,3)); target.inventory.put("mine",1);
        state.players.get(0).inventory.put("torpedo",1);
        NavalEngine.attack(state,0,"torpedo",0,3,5_000);
        assertEquals(3,target.shots.size()); assertEquals("mine",target.shots.get(2).result());
        assertTrue(target.ships.get(0).hits.isEmpty()); assertEquals(0,target.inventory.get("mine"));
    }

    @Test void radarIsPrivateAndDoesNotDamageOrGrantExtraTurn() {
        NavalState state=battle(); state.players.get(0).inventory.put("radar",1);
        NavalEngine.attack(state,0,"radar",1,2,5_000);
        assertEquals(9,state.players.get(0).radar.size()); assertEquals(1,state.turn); assertTrue(state.players.get(1).shots.isEmpty());
        assertEquals(List.of(),NavalEngine.view(state,1,5_000).get("radar"));
    }

    @Test void torpedoCanDropAfterAnEarlierMineAndOnlyTravelsFromTheSelectedCell() {
        NavalState state=battle(); Player target=state.players.get(1);
        target.ships=List.of(new Ship("s",8,3,1,2)); target.mines.add(new Cell(2,3)); target.inventory.put("mine",1);
        state.players.get(0).inventory.put("torpedo",1);
        NavalEngine.attack(state,0,"torpedo",6,3,5000);
        assertEquals(List.of(6,7,8),target.shots.stream().map(Shot::x).toList());
        assertEquals("hit",target.shots.get(2).result()); assertEquals(1,target.mines.size());
        assertEquals(6,state.events.get(state.events.size()-1).x());
    }

    @Test void attackEffectsRetainNoncentralOriginsWithoutPublishingRadarResults() throws Exception {
        NavalState state=battle(); state.players.get(0).inventory.put("radar",1);
        NavalEngine.attack(state,0,"radar",1,7,5_000);
        Event radar=state.events.get(state.events.size()-1);
        assertEquals(1,radar.x()); assertEquals(7,radar.y()); assertTrue(radar.cells().isEmpty());
        var publicEvents=new ObjectMapper().valueToTree(NavalEngine.view(state,1,5_000)).get("events");
        assertFalse(publicEvents.toString().contains("occupied"));
        state.turn=0; state.players.get(0).inventory.put("fighter",1);
        state.players.get(1).aa.add(9); state.players.get(1).inventory.put("antiAir",1);
        NavalEngine.attack(state,0,"fighter",3,8,6_000);
        Event intercept=state.events.get(state.events.size()-1);
        assertEquals(3,intercept.x()); assertTrue(intercept.y()==8 || intercept.y()==9); assertEquals(9,intercept.row());
        assertTrue(intercept.cells().isEmpty());
        var mapper=new ObjectMapper();
        Event legacy=mapper.readValue("{\"id\":1,\"player\":0,\"weapon\":\"radar\",\"cells\":[],\"text\":\"scan\"}",Event.class);
        assertNull(legacy.x()); assertNull(legacy.row());
    }

    @Test void viewsNeverExposeOpponentPrivateIdentityFleetInventoryOrDefenses() throws Exception {
        NavalState state=room(); state.players.get(1).mines.add(new Cell(0,0)); state.players.get(1).aa.add(3); state.players.get(1).inventory.put("radar",2);
        var mapper=new ObjectMapper(); var view=mapper.valueToTree(NavalEngine.view(state,0,2_000));
        assertFalse(view.toString().contains("G:2")); assertEquals(0,view.at("/players/1/ships").size());
        assertEquals(0,view.at("/players/1/mines").size()); assertEquals(0,view.at("/players/1/aa").size());
        assertEquals(0,view.at("/players/1/inventory/radar").asInt()); assertEquals(10,view.at("/players/0/ships").size());
        state.phase="FINISHED"; assertEquals(10,mapper.valueToTree(NavalEngine.view(state,0,2_000)).at("/players/1/ships").size());
    }

    @Test void allSunkEndsGameAndRevealsFleet() {
        NavalState state=battle(); Player target=state.players.get(1); target.ships=List.of(new Ship("last",2,2,1,1));
        NavalEngine.attack(state,0,"shot",2,2,5_000);
        assertEquals("FINISHED",state.phase); assertEquals(0,state.winner); assertEquals(0,state.deadline); assertEquals(5_000,state.finishedAt);
    }

    @Test void persistedStateRoundTripsWithPrivateRadarAndCells() throws Exception {
        NavalState state=battle(); state.players.get(0).radar.add(new Radar(1,2,true));
        state.players.get(1).mines.add(new Cell(3,4));
        var mapper=new ObjectMapper(); NavalState restored=mapper.readValue(mapper.writeValueAsString(state),NavalState.class);
        assertEquals(state.players.get(0).radar,restored.players.get(0).radar); assertEquals(state.players.get(1).mines,restored.players.get(1).mines);
    }

    @Test void chatSnapshotsPublicSenderAndMigratesFourFieldMessages() throws Exception {
        NavalState state = room(); String originalId = state.players.get(0).id;
        Message legacy = new ObjectMapper().readValue("{\"id\":7,\"player\":0,\"text\":\"old\",\"at\":2000}",Message.class);
        state.messages.add(legacy); NavalEngine.normalize(state,2_000);
        assertEquals("甲",state.messages.get(0).name()); assertEquals(originalId,state.messages.get(0).senderId());
        state.players.set(0,NavalEngine.player("G:replacement","新指挥官"));
        NavalEngine.chat(state,0,"new",2_001);
        assertEquals("甲",state.messages.get(0).name()); assertEquals(originalId,state.messages.get(0).senderId());
        assertEquals("新指挥官",state.messages.get(1).name()); assertEquals(state.players.get(0).id,state.messages.get(1).senderId());
        assertNotEquals(originalId,state.messages.get(1).senderId());
        assertThrows(BusinessException.class,() -> NavalEngine.chat(state,0,"too fast",2_002));
        var payload = new ObjectMapper().writeValueAsString(NavalEngine.view(state,0,2_003));
        assertFalse(payload.contains("G:replacement")); assertFalse(payload.contains("U:1"));
    }

    @Test void preSettlementLegacyChatUsesOriginalParticipantAfterSeatReplacement() {
        NavalState state = room(); String originalId = state.players.get(1).id;
        state.messages.add(new Message(4,1,"legacy",2_000));
        state.finishedAt = 3_000;
        state.settlement = Map.of("players",List.of(Map.of("id",state.players.get(0).id,"name","甲"),Map.of("id",originalId,"name","乙")));
        state.players.set(1,NavalEngine.player("G:new","新玩家"));
        NavalEngine.normalize(state,4_000);
        assertEquals("乙",state.messages.get(0).name()); assertEquals(originalId,state.messages.get(0).senderId());
    }

    @Test void preparationHasDistinctPerPlayerLayoutAndSupplyStages() {
        NavalState state=room();
        assertThrows(BusinessException.class,() -> NavalEngine.ready(state,0,2_000));
        Action earlyPurchase=new Action(); earlyPurchase.inventory=Map.of("fighter",1);
        assertThrows(BusinessException.class,() -> NavalEngine.deploy(state,0,earlyPurchase,2_000));
        NavalEngine.layoutReady(state,0,null,2_000);
        assertEquals("PLACEMENT",state.phase); assertEquals("SUPPLY",state.players.get(0).stage);
        assertEquals("PLACEMENT",state.players.get(1).stage);
        Action supply=new Action(); supply.inventory=Map.of("fighter",1);
        NavalEngine.deploy(state,0,supply,3_000); assertEquals(270,state.players.get(0).coins);
        Action illegalMove=new Action(); illegalMove.ships=NavalEngine.randomFleet();
        assertThrows(BusinessException.class,() -> NavalEngine.deploy(state,0,illegalMove,3_000));
        NavalEngine.ready(state,0,4_000); assertEquals("PLACEMENT",state.phase);
        NavalEngine.layoutReady(state,1,null,5_000);
        assertEquals("SUPPLY",state.phase); assertEquals(185_000,state.deadline);
        NavalEngine.ready(state,1,6_000); assertEquals("BATTLE",state.phase);
    }

    @Test void timeoutLocksLayoutThenGivesASeparateSupplyWindow() {
        NavalState state=room(); NavalEngine.advance(state,301_000);
        assertEquals("SUPPLY",state.phase); assertEquals(481_000,state.deadline);
        assertTrue(state.players.stream().allMatch(player -> !player.ready && "SUPPLY".equals(player.stage)));
        NavalEngine.advance(state,480_999); assertEquals("SUPPLY",state.phase);
        NavalEngine.advance(state,481_000); assertEquals("BATTLE",state.phase);
    }

    @Test void legacyStateAndSavedFormationsRemoveCarriersAndRetainRemainingGeometry() {
        NavalState state=room(); state.rulesVersion=0;
        for (Player player:state.players) {
            player.ships.add(new Ship("old-carrier-1",0,0,2,3));
            player.ships.add(new Ship("old-carrier-2",4,0,3,2));
        }
        state.players.get(0).inventory.put("fighter",1);
        state.players.get(1).ready=true;
        NavalEngine.normalize(state,2_000);
        assertEquals(2,state.rulesVersion); assertEquals("SUPPLY",state.phase);
        assertEquals("SUPPLY",state.players.get(0).stage); assertEquals("READY",state.players.get(1).stage);
        assertEquals(10,state.players.get(0).ships.size());
        NavalEngine.validateFleet(state.players.get(0).ships);
        List<Ship> saved=new ArrayList<>(state.players.get(0).ships); saved.add(new Ship("old-carrier",0,0,2,3));
        assertEquals(10,NavalEngine.normalizeSavedFleet(saved).size());
    }

    private Cell water(Player player) {
        for (int y=0;y<12;y++) for (int x=0;x<12;x++) {
            final int col=x,row=y;
            if (player.ships.stream().noneMatch(s -> col>=s.x && col<s.x+s.w && row>=s.y && row<s.y+s.h)) return new Cell(x,y);
        }
        throw new AssertionError("No water");
    }

    @Test void clearAndPartialLayoutsPersistAndTimeoutFillsAroundExistingShips() {
        NavalState state=room(); Action clear=new Action(); clear.ships=List.of();
        NavalEngine.deploy(state,0,clear,2_000);
        assertTrue(state.players.get(0).ships.isEmpty());
        assertThrows(BusinessException.class,() -> NavalEngine.layoutReady(state,0,null,2_000));
        Ship existing=new Ship("custom-boat",5,5,1,1);
        Action partial=new Action(); partial.ships=List.of(existing);
        NavalEngine.deploy(state,0,partial,3_000);
        NavalEngine.advance(state,301_001);
        assertEquals("SUPPLY",state.phase);
        NavalEngine.validateFleet(state.players.get(0).ships);
        Ship preserved=state.players.get(0).ships.stream().filter(ship -> ship.id.equals(existing.id)).findFirst().orElseThrow();
        assertEquals(5,preserved.x); assertEquals(5,preserved.y);
        List<Ship> tooMany=new ArrayList<>();
        for(int i=0;i<5;i++) tooMany.add(new Ship("single-"+i,i*2,0,1,1));
        assertThrows(BusinessException.class,() -> NavalEngine.validatePartialFleet(tooMany));
    }

    @Test void aiNeverAttacksTheHaloAroundPublicSunkShipsEvenWithAreaWeapons() {
        for(int iteration=0;iteration<150;iteration++) {
            NavalState state=battle(); state.mode="AI"; state.turn=1; state.nextAiAt=4_000;
            Player target=state.players.get(0), ai=state.players.get(1);
            ai.inventory.putAll(Map.of("fighter",3,"bomber",3,"torpedo",3));
            Ship sunk=target.ships.stream().filter(ship -> ship.w*ship.h==1).findFirst().orElseThrow();
            sunk.hits.add(sunk.x+","+sunk.y); target.shots.add(new Shot(sunk.x,sunk.y,"hit"));
            NavalEngine.advance(state,5_000);
            Event action=state.events.get(state.events.size()-1);
            assertEquals(1,action.player());
            assertTrue(action.cells().stream().allMatch(cell -> cell.x()<sunk.x-1 || cell.x()>sunk.x+1 || cell.y()<sunk.y-1 || cell.y()>sunk.y+1),"AI attacked a known empty halo");
        }
    }
}
