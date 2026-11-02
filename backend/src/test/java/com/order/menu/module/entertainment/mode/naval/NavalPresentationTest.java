package com.order.menu.module.entertainment.mode.naval;

import static org.junit.jupiter.api.Assertions.*;
import com.order.menu.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

class NavalPresentationTest {
    private NavalState battle(String mode) {
        NavalState state=new NavalState(); state.id="presentation"; state.mode=mode; state.presentationSync=true;
        state.players.add(NavalEngine.player("U:1","A")); state.players.add(NavalEngine.player("U:2","B"));
        NavalEngine.beginPlacement(state,1000);
        NavalEngine.layoutReady(state,0,null,1000);
        if (!"AI".equals(mode)) NavalEngine.layoutReady(state,1,null,1000);
        NavalEngine.ready(state,0,1000);
        if (!"AI".equals(mode)) NavalEngine.ready(state,1,1000);
        return state;
    }
    @Test void bothPlayersMustFinishPresentingAndStaleAcknowledgementsDoNotReleaseNextGate() {
        NavalState state=battle("MULTIPLAYER"); long id=state.presentationId;
        assertTrue(state.presentationUntil>0);
        NavalEngine.presented(state,0,id,2000); assertTrue(state.presentationUntil>0);
        NavalEngine.presented(state,1,id-1,3000); assertTrue(state.presentationUntil>0);
        NavalEngine.presented(state,1,id,4000); assertEquals(0,state.presentationUntil); assertEquals(34000,state.deadline);
        int actor=state.turn;
        state.players.get(1-actor).ships=java.util.List.of(new NavalState.Ship("target",4,4,1,3));
        NavalEngine.attack(state,actor,"shot",0,0,5000); long next=state.presentationId;
        assertNotEquals(id,next); assertTrue(state.presentationUntil>0);
        NavalEngine.presented(state,0,id,6000); NavalEngine.presented(state,1,id,6000); assertTrue(state.presentationUntil>0);
        assertThrows(BusinessException.class,()->NavalEngine.attack(state,state.turn,"shot",1,0,6000));
    }
    @Test void aiCannotAttackDuringBannerAndOnlyStartsAfterFreshDelay() {
        NavalState state=battle("AI"); state.turn=1; int count=state.events.size();
        NavalEngine.advance(state,7000); assertEquals(count,state.events.size());
        NavalEngine.presented(state,0,state.presentationId,7000); assertEquals(37000,state.deadline);
        NavalEngine.advance(state,7500); assertEquals(count,state.events.size());
        NavalEngine.advance(state,8300); assertTrue(state.events.size()>count); assertTrue(state.presentationUntil>0);
    }
    @Test void disconnectedPresentationHasBoundedFallbackWithFreshClock() {
        NavalState state=battle("MULTIPLAYER"); long release=state.presentationUntil;
        NavalEngine.advance(state,release+1); assertEquals(0,state.presentationUntil); assertEquals(release+1+30000,state.deadline);
    }

    @Test void humanCanChainHitsImmediatelyButHandoffStillWaitsForQueuedAnimations() {
        for (String mode : java.util.List.of("AI","MULTIPLAYER")) {
            NavalState state=battle(mode); state.turn=0;
            NavalEngine.presented(state,0,state.presentationId,2000);
            if (!"AI".equals(mode)) NavalEngine.presented(state,1,state.presentationId,2000);
            state.players.get(1).ships=java.util.List.of(new NavalState.Ship("target",4,4,1,3));
            state.players.get(1).mines.clear();
            NavalEngine.attack(state,0,"shot",4,4,2001);
            assertEquals(0,state.presentationUntil); assertEquals(32001,state.deadline);
            NavalEngine.attack(state,0,"shot",4,5,2002);
            assertEquals(0,state.presentationUntil); assertEquals(0,state.turn);
            NavalEngine.attack(state,0,"shot",0,0,2003);
            assertTrue(state.presentationUntil>0); assertEquals(1,state.turn);
            assertThrows(BusinessException.class,()->NavalEngine.attack(state,1,"shot",1,1,2004));
        }
    }
}
