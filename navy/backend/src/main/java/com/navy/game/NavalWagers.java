package com.navy.game;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.navy.common.exception.BusinessException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.math.BigDecimal;
import org.springframework.jdbc.core.JdbcTemplate;

/** Invoked while the room is locked, inside the same room mutation transaction. */
final class NavalWagers {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    NavalWagers(JdbcTemplate jdbc,ObjectMapper json){this.jdbc=jdbc;this.json=json;}
    private static final List<Integer> AMOUNTS=List.of(5,10,15,20,25,30,50,70,100,150,200,250,320,400,500);
    private static final int[] DEFAULTS={10,20,30,50,70,100,150,200,250,320,400,500};
    static int amount(Integer amount){if(amount==null||!AMOUNTS.contains(amount))throw new BusinessException(400,"请选择有效的正数下注金额");return amount;}
    static int defaultForLevel(int level){return DEFAULTS[Math.max(0,Math.min(11,level-1))];}
    static List<Integer> allowedAmounts(){return AMOUNTS;}
    int defaultAmount(String key){if(!account(key))return 0;var scores=jdbc.queryForList("SELECT score FROM naval_progression_profiles WHERE player_key=?",Integer.class,key);return defaultForLevel(NavalProgression.level(scores.isEmpty()?0:scores.get(0)));}
    long available(String key){if(!account(key))return 0;var coins=jdbc.queryForList("SELECT coins FROM naval_progression_profiles WHERE player_key=?",Long.class,key);return coins.isEmpty()?100:coins.get(0);}
    int selection(String key,Integer selected){int required=defaultAmount(key);if(selected==null)return required;if(selected==required||(required<=10&&selected==0))return selected;throw new BusinessException(409,"本等级下注为"+required+"金币"+(required==10?"，也可选择不下注":""));}
    static void consent(NavalState state,Integer accepted){consent(state.wager,accepted);}
    static void consent(int required,Integer accepted){if(accepted==null)return;if(required==0&&accepted==0)return;if(!accepted.equals(required))throw new BusinessException(409,"此房间下注为"+required+"金币，请刷新后加入");amount(accepted);}
    private static boolean account(String key){return key!=null&&key.matches("U:[0-9]+");}
    private String game(NavalState state){
        if(!"FINISHED".equals(state.phase))state.wagerRound="WAITING".equals(state.phase)&&state.settlement!=null?state.round+1:state.round;
        if(state.wagerRound==0)state.wagerRound=state.round;
        return state.id+":"+state.wagerRound;
    }
    private void lockWallets(Set<String> keys){
        keys.stream().sorted().forEach(key->{
            if(!account(key))return;
            jdbc.update("INSERT INTO naval_progression_profiles(player_key) VALUES(?) ON DUPLICATE KEY UPDATE player_key=VALUES(player_key)",key);
            jdbc.queryForObject("SELECT coins FROM naval_progression_profiles WHERE player_key=? FOR UPDATE",Long.class,key);
        });
    }

    Map<String,Object> sync(NavalState state){
        if(state.localSimulation||!"MULTIPLAYER".equals(state.mode)){if(state.wager>0)throw new BusinessException(400,"只有人人对战可以下注");state.wagerPot=0;return Map.of("amount",0,"pot",0,"status","NONE","players",List.of());}
        if("MATCHED".equals(state.finishReason)&&state.matchedRoomId!=null){state.wagerPot=0;return null;}
        String game=game(state);
        // Materialize the unique round before locking its receipt: locking a missing
        // receipt gap would deadlock simultaneous settlements of unrelated rounds.
        jdbc.update("INSERT INTO naval_wager_settlements(game_id,outcome_json) VALUES(?,NULL) ON DUPLICATE KEY UPDATE game_id=VALUES(game_id)",game);
        var existing=jdbc.queryForList("SELECT outcome_json FROM naval_wager_settlements WHERE game_id=? AND outcome_json IS NOT NULL",String.class,game);
        if(!existing.isEmpty()){state.wagerPot=0;return read(existing.get(0));}
        var stakes=jdbc.queryForList("SELECT player_key,amount,status FROM naval_wager_stakes WHERE game_id=?",game);
        Set<String> keys=new LinkedHashSet<>();stakes.forEach(row->keys.add(row.get("player_key").toString()));
        state.players.stream().filter(p->p.registered&&account(p.key)).forEach(p->keys.add(p.key));
        if(!"FINISHED".equals(state.phase))for(var player:state.players)if(!player.left){
            keys.add(player.key);
        }
        lockWallets(keys);
        if(!"FINISHED".equals(state.phase))for(var player:state.players)if(!player.left)
            jdbc.update("INSERT INTO naval_wager_stakes(game_id,player_key,amount,status) VALUES(?,?,0,'PENDING') ON DUPLICATE KEY UPDATE player_key=VALUES(player_key)",game,player.key);
        existing=jdbc.queryForList("SELECT outcome_json FROM naval_wager_settlements WHERE game_id=? AND outcome_json IS NOT NULL FOR UPDATE",String.class,game);
        if(!existing.isEmpty()){state.wagerPot=0;return read(existing.get(0));}
        stakes=jdbc.queryForList("SELECT player_key,amount,status FROM naval_wager_stakes WHERE game_id=? FOR UPDATE",game);
        if(!"FINISHED".equals(state.phase)){
            for(var player:state.players)if(!player.left){
                var own=stakes.stream().filter(row->player.key.equals(row.get("player_key"))).findFirst().orElse(null);
                if(own!=null&&"HELD".equals(own.get("status"))){player.wager=((Number)own.get("amount")).intValue();continue;}
                if(own!=null&&!"PENDING".equals(own.get("status")))throw new BusinessException(409,"本局下注已结算，请重新进入下一局");
                int selected=player.registered&&account(player.key)?(player.selectedWager==null?state.wager:player.selectedWager):0;
                if(selected>0){
                    long coins=jdbc.queryForObject("SELECT coins FROM naval_progression_profiles WHERE player_key=? FOR UPDATE",Long.class,player.key);
                    if(coins<selected)throw new BusinessException(400,player.name+"的账户金币不足，无法支付本局下注");
                    jdbc.update("UPDATE naval_progression_profiles SET coins=coins-? WHERE player_key=?",selected,player.key);
                }
                jdbc.update("UPDATE naval_wager_stakes SET amount=?,status='HELD' WHERE game_id=? AND player_key=?",selected,game,player.key);
                player.wager=selected;
            }
            state.wagerPot=state.players.stream().filter(p->!p.left).mapToInt(p->p.wager).sum();
            return null;
        }
        int pot=stakes.stream().filter(row->"HELD".equals(row.get("status"))).mapToInt(row->((Number)row.get("amount")).intValue()).sum();
        boolean decided=NavalEngine.battleStarted(state)&&state.winner!=null&&state.winner>=0&&state.winner<state.players.size()&&stakes.size()==2
            &&stakes.stream().allMatch(row->"HELD".equals(row.get("status"))&&((Number)row.get("amount")).intValue()>=0)
            &&state.players.stream().allMatch(p->keys.contains(p.key));
        boolean natural=decided&&List.of("DESTROYED","MILITARY_DEFEAT").contains(state.finishReason==null?"":state.finishReason);
        boolean partial=decided&&!natural;
        String status=natural?"PAID":partial?"EXIT_PARTIAL":"REFUNDED";
        List<Map<String,Object>> players=new ArrayList<>();
        long totalPayout=0;
        for(int seat=0;seat<state.players.size();seat++){
            String key=state.players.get(seat).key;int stake=stakes.stream().filter(row->key.equals(row.get("player_key"))).mapToInt(row->((Number)row.get("amount")).intValue()).sum();
            int winnerStake=natural?stakes.stream().filter(row->state.players.get(state.winner).key.equals(row.get("player_key"))).mapToInt(row->((Number)row.get("amount")).intValue()).sum():0;
            long payout=natural?naturalPayout(stake,pot-stake,seat==state.winner,winnerStake)*100L:partial?(seat==state.winner?stake*100L+Math.min(stake*100L,(pot-stake)*10L):stake*50L):stake*100L;
            if(decided&&payout>0)deposit(key,payout);
            totalPayout+=payout;
            players.add(Map.of("seat",seat,"stake",stake,"payout",money(payout),"net",money(payout-stake*100L)));
        }
        if(!decided){totalPayout=0;for(var stake:stakes)if("HELD".equals(stake.get("status"))){long payout=((Number)stake.get("amount")).longValue()*100;deposit(stake.get("player_key").toString(),payout);totalPayout+=payout;}}
        Map<String,Object> result=new LinkedHashMap<>();result.put("amount",state.wager);result.put("pot",pot);result.put("status",status);result.put("reason",status);result.put("burnedCoins",money(pot*100L-totalPayout));result.put("winner",decided?state.winner:null);result.put("players",players);
        jdbc.update("UPDATE naval_wager_stakes SET status=? WHERE game_id=?",status,game);
        jdbc.update("UPDATE naval_wager_settlements SET outcome_json=? WHERE game_id=?",write(result),game);
        state.wagerPot=0;return result;
    }
    private static Number money(long cents){return cents%100==0?Integer.valueOf(Math.toIntExact(cents/100)):BigDecimal.valueOf(cents,2);}
    static int naturalPayout(int own,int opponent,boolean won,int winnerStake){return won?own+Math.min(own,opponent):own-Math.min(own,winnerStake);}
    private void deposit(String key,long cents){if(account(key))jdbc.update("UPDATE naval_progression_profiles SET coins=coins+FLOOR((coin_fraction+?)/100),coin_fraction=MOD(coin_fraction+?,100) WHERE player_key=?",cents,cents,key);}

    boolean transfer(NavalState source,NavalState target,String player){
        Set<String> keys=new LinkedHashSet<>();source.players.stream().filter(p->!p.left).forEach(p->keys.add(p.key));target.players.stream().filter(p->!p.left).forEach(p->keys.add(p.key));lockWallets(keys);
        String from=game(source),to=game(target);
        if(target.players.stream().anyMatch(p->!p.left&&player.equals(p.key))){
            var held=jdbc.queryForList("SELECT amount FROM naval_wager_stakes WHERE game_id=? AND player_key=? AND status='HELD' FOR UPDATE",Integer.class,from,player);
            if(!held.isEmpty()&&jdbc.update("UPDATE naval_wager_stakes SET status='REFUNDED' WHERE game_id=? AND player_key=? AND status='HELD'",from,player)==1)jdbc.update("UPDATE naval_progression_profiles SET coins=coins+? WHERE player_key=?",held.get(0),player);
            source.wagerPot=0;return true;
        }
        var held=jdbc.queryForList("SELECT amount FROM naval_wager_stakes WHERE game_id=? AND player_key=? AND status='HELD' FOR UPDATE",Integer.class,from,player);
        if(held.isEmpty())throw new BusinessException(409,"匹配下注状态已改变，请重新匹配");
        int moved=jdbc.update("UPDATE naval_wager_stakes SET game_id=? WHERE game_id=? AND player_key=? AND status='HELD'",to,from,player);
        if(moved!=1)throw new BusinessException(409,"匹配下注状态已改变，请重新匹配");
        source.wagerPot=0;
        return true;
    }
    private String write(Object value){try{return json.writeValueAsString(value);}catch(Exception e){throw new IllegalStateException(e);}}
    private Map<String,Object> read(String value){try{return json.readValue(value,new TypeReference<LinkedHashMap<String,Object>>(){});}catch(Exception e){throw new IllegalStateException(e);}}
}
