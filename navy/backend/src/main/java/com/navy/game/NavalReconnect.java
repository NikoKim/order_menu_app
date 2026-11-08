package com.navy.game;

import com.navy.common.exception.BusinessException;
import com.navy.game.NavalState.Player;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Room-locked reconnect grace; a paused round retains its original absolute clocks until resumed. */
final class NavalReconnect {
    static final long GRACE_MS=60_000,STALE_MS=15_000;
    static final int MAX_RECONNECTS=3;
    static boolean active(NavalState state){return !state.localSimulation&&"BATTLE".equals(state.phase)&&state.battlePausedAt>0;}
    static void reset(NavalState state){state.battlePausedAt=0;state.localPause=null;for(Player player:state.players){player.disconnectedAt=0;player.reconnectRequired=false;player.reconnectCount=0;}}
    static int used(Player player){return "AI".equals(player.key)?0:Math.max(0,player.reconnectCount);}
    static int remaining(Player player){return "AI".equals(player.key)?0:Math.max(0,MAX_RECONNECTS-used(player));}
    static int used(NavalState state,Player player){return !"AI".equals(player.key)&&"BATTLE".equals(state.phase)&&player.disconnectedAt>0?Math.max(1,used(player)):used(player);}
    static int remaining(NavalState state,Player player){return "AI".equals(player.key)?0:Math.max(0,MAX_RECONNECTS-used(state,player));}
    static boolean disconnect(NavalState state,int seat,long now){
        if(state.localSimulation||!"BATTLE".equals(state.phase)||seat<0||seat>=state.players.size())return false;
        Player player=state.players.get(seat);if(player.left||"AI".equals(player.key))return false;
        boolean first=player.disconnectedAt==0;
        if(first){
            player.reconnectCount=Math.max(0,player.reconnectCount)+1;
            player.disconnectedAt=now;
        }else if(player.reconnectCount==0)player.reconnectCount=1;
        player.reconnectRequired=true;player.continueRequested=false;
        if(state.battlePausedAt==0)state.battlePausedAt=now;
        if(player.reconnectCount>MAX_RECONNECTS)NavalEngine.finishDisconnected(state,seat,now,"本局第4次断线，直接判负");
        return first;
    }
    static boolean advance(NavalState state,long now){
        if(state.localSimulation||!"BATTLE".equals(state.phase))return false;
        if(state.connectionTracking)for(int seat=0;seat<state.players.size();seat++){
            Player player=state.players.get(seat);
            if(!player.left&&player.key!=null&&!"AI".equals(player.key)&&player.disconnectedAt==0&&player.lastSeenAt>0&&now-player.lastSeenAt>=STALE_MS){
                long pausedAt=player.lastSeenAt+STALE_MS;
                disconnect(state,seat,now);
                if("FINISHED".equals(state.phase))return true;
                if(pausedAt<state.battlePausedAt)state.battlePausedAt=pausedAt;
            }
        }
        int expired=-1;long earliest=Long.MAX_VALUE;
        for(int seat=0;seat<state.players.size();seat++){
            Player player=state.players.get(seat);if(player.left||"AI".equals(player.key)||player.disconnectedAt<=0)continue;
            if(player.reconnectCount==0){
                if(player.disconnectedAt<state.battlePausedAt)player.disconnectedAt=now;
                player.reconnectCount=1;
            }
            if(state.battlePausedAt==0)state.battlePausedAt=Math.min(now,player.disconnectedAt);
            if(now-player.disconnectedAt>=GRACE_MS&&player.disconnectedAt<earliest){earliest=player.disconnectedAt;expired=seat;}
        }
        if(expired>=0){NavalEngine.finishDisconnected(state,expired,now,"玩家离开超过60秒，未及时重连，判负");return true;}
        return active(state);
    }
    static void connect(NavalState state,int seat,String token,long now){
        Player player=state.players.get(seat);
        if(player.disconnectedAt>0&&player.reconnectCount==0)player.reconnectCount=1;
        if(player.retiredConnectionTokens==null)player.retiredConnectionTokens=new ArrayList<>();
        if(player.retiredConnectionTokens.contains(token)||player.reconnectRequired&&Objects.equals(token,player.connectionToken))throw new BusinessException(409,"请重新进入对局以恢复连接");
        if(player.connectionToken!=null&&!Objects.equals(token,player.connectionToken))retire(player,player.connectionToken);
        player.connectionToken=token;player.disconnectedAt=0;player.reconnectRequired=false;player.lastSeenAt=now;
        if(active(state)&&state.players.stream().noneMatch(person->!person.left&&!"AI".equals(person.key)&&person.disconnectedAt>0)){
            long elapsed=Math.max(0,now-state.battlePausedAt);
            if(state.deadline>0)state.deadline+=elapsed;if(state.presentationUntil>0)state.presentationUntil+=elapsed;if(state.nextAiAt>0)state.nextAiAt+=elapsed;
            state.battlePausedAt=0;
        }
    }
    static void leaveIntent(NavalState state,int seat,String pendingToken){
        if(pendingToken!=null&&!pendingToken.matches("[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}"))NavalEngine.bad("页面标识无效");
        if(!state.localSimulation&&"BATTLE".equals(state.phase)){
            Player player=state.players.get(seat);retire(player,player.connectionToken);retire(player,pendingToken);
        }
    }
    static void inheritRetired(Player player,Player previous){if(previous==null)return;if(previous.retiredConnectionTokens!=null)for(String token:previous.retiredConnectionTokens)retire(player,token);retire(player,previous.connectionToken);}
    static void cancelPending(Player player,String token){retire(player,token);}
    private static void retire(Player player,String token){if(token==null)return;if(player.retiredConnectionTokens==null)player.retiredConnectionTokens=new ArrayList<>();if(!player.retiredConnectionTokens.contains(token))player.retiredConnectionTokens.add(token);while(player.retiredConnectionTokens.size()>8)player.retiredConnectionTokens.remove(0);}
    static void requirePlaying(NavalState state){if(active(state))throw new BusinessException(409,"对局已暂停，请等待玩家重新连接");}
    static Map<String,Object> metadata(NavalState state){
        if(state.localSimulation&&"BATTLE".equals(state.phase)&&state.localPause!=null)return state.localPause;
        List<Map<String,Object>> players=new ArrayList<>();long until=Long.MAX_VALUE;
        if(active(state))for(int seat=0;seat<state.players.size();seat++){
            Player player=state.players.get(seat);if(player.left||"AI".equals(player.key)||player.disconnectedAt<=0)continue;
            long end=player.disconnectedAt+GRACE_MS;until=Math.min(until,end);players.add(Map.of("seat",seat,"personId",player.id,"until",end,"reconnectCount",used(state,player),"reconnectRemaining",remaining(state,player)));
        }
        boolean paused=active(state);Map<String,Object> out=new LinkedHashMap<>();out.put("active",paused);out.put("startedAt",paused?state.battlePausedAt:0L);out.put("until",paused&&until<Long.MAX_VALUE?until:0L);
        out.put("remainingMs",paused?Math.max(0,state.deadline-state.battlePausedAt):0L);out.put("presentationRemainingMs",paused&&state.presentationUntil>0?Math.max(0,state.presentationUntil-state.battlePausedAt):0L);out.put("graceMs",GRACE_MS);out.put("maxReconnects",MAX_RECONNECTS);out.put("players",players);return out;
    }
    private NavalReconnect(){}
}
