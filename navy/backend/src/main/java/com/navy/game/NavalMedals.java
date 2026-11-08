package com.navy.game;

import java.util.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;

final class NavalMedals {
    private static final int PROFILE_BATCH_LIMIT=512;
    private final JdbcTemplate jdbc;private final ObjectMapper json;
    NavalMedals(JdbcTemplate jdbc,ObjectMapper json){this.jdbc=jdbc;this.json=json;}
    static final List<String> IDS=List.of("classic-3","classic-5","classic-10","military-3","military-5","military-10");
    static int next(int current,boolean won){return won?current+1:0;}
    static List<String> unlocked(int classic,int military){return IDS.stream().filter(id->(id.startsWith("classic")?classic:military)>=Integer.parseInt(id.substring(id.indexOf('-')+1))).toList();}
    private Map<String,Object> decode(String source){try{return json.readValue(source,new TypeReference<LinkedHashMap<String,Object>>(){});}catch(Exception e){throw new IllegalStateException("Invalid naval medal archive",e);}}
    @SuppressWarnings("unchecked")
    private <T>T transaction(java.util.function.Supplier<T> action,boolean independent){
        boolean writable=org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()
            && !org.springframework.transaction.support.TransactionSynchronizationManager.isCurrentTransactionReadOnly();
        if(jdbc.getDataSource()==null || writable)return action.get();
        var tx=new org.springframework.transaction.support.TransactionTemplate(new org.springframework.jdbc.datasource.DataSourceTransactionManager(jdbc.getDataSource()));
        if(independent)tx.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return tx.execute(status->action.get());
    }
    void credit(Map<String,Object> result){transaction(()->{credit(result,null);return true;},false);}
    @SuppressWarnings("unchecked")
    private void credit(Map<String,Object> result,String only){
        if(!"MULTIPLAYER".equals(result.get("mode"))||!Boolean.TRUE.equals(result.get("battleStarted"))||!(result.get("winner") instanceof Number winner)||!List.of(0,1).contains(winner.intValue()))return;
        List<Map<String,Object>> players=(List<Map<String,Object>>)result.get("players");String rule="MILITARY".equals(result.get("ruleSet"))?"military":"classic";
        for(int seat=0;seat<players.size();seat++){
            String key=String.valueOf(players.get(seat).get("key"));if(key.equals("null")||key.equals("AI"))continue;
            if(only!=null&&!only.equals(key))continue;if(only==null)backfill(key);ensure(key);jdbc.queryForList("SELECT player_key FROM naval_medal_profiles WHERE player_key=? FOR UPDATE",key);
            if(jdbc.update("INSERT IGNORE INTO naval_medal_rounds(game_id,player_key) VALUES(?,?)",result.get("gameId"),key)==0)continue;
            if(winner.intValue()==seat)jdbc.update("UPDATE naval_medal_profiles SET "+rule+"_best=GREATEST("+rule+"_best,"+rule+"_current+1),"+rule+"_current="+rule+"_current+1 WHERE player_key=?",key);
            else jdbc.update("UPDATE naval_medal_profiles SET "+rule+"_current=0 WHERE player_key=?",key);
        }
    }
    private void ensure(String key){jdbc.update("INSERT IGNORE INTO naval_medal_profiles(player_key,public_id) VALUES(?,?)",key,UUID.randomUUID().toString());}
    void backfill(String key){
        boolean absent=jdbc.queryForList("SELECT public_id FROM naval_medal_profiles WHERE player_key=?",String.class,key).isEmpty();
        if(!absent)return;ensure(key);
        for(String source:jdbc.queryForList("SELECT result_json FROM naval_round_results WHERE (player0_key=? OR player1_key=?) AND JSON_UNQUOTE(JSON_EXTRACT(result_json,'$.mode'))='MULTIPLAYER' ORDER BY finished_at,game_id",String.class,key,key))credit(decode(source),key);
    }
    Map<String,Object> wall(String key){if(key==null||key.equals("AI"))return Map.of("unlocked",List.of(),"equipped","");return transaction(()->wallData(key),true);}
    Map<String,Object> readWall(String key){
        if(key==null||key.equals("AI"))return Map.of("unlocked",List.of(),"equipped","");
        var rows=jdbc.queryForList("SELECT * FROM naval_medal_profiles WHERE player_key=?",key);
        if(rows.isEmpty())return Map.of("unlocked",List.of(),"equipped","");
        var row=rows.get(0);int classic=((Number)row.get("classic_best")).intValue(),military=((Number)row.get("military_best")).intValue();
        Map<String,Object> out=new LinkedHashMap<>();out.put("medalId",row.get("public_id"));out.put("unlocked",unlocked(classic,military));out.put("equipped",row.get("equipped")==null?"":row.get("equipped"));return out;
    }
    static Map<String,Map<String,Object>> readWalls(JdbcTemplate jdbc,ObjectMapper json,Collection<String> keys){
        Map<String,Map<String,Object>> result=new LinkedHashMap<>();
        for(String key:keys)result.put(key,Map.of("unlocked",List.of(),"equipped",""));
        List<String> actual=keys.stream().filter(Objects::nonNull).filter(key->!key.equals("AI")).distinct().sorted().toList();
        if(actual.isEmpty())return result;
        String placeholders=String.join(",",Collections.nCopies(actual.size(),"?"));
        for(var row:jdbc.queryForList("SELECT player_key,public_id,classic_best,military_best,equipped FROM naval_medal_profiles WHERE player_key IN ("+placeholders+")",actual.toArray())){
            int classic=row.get("classic_best") instanceof Number n?n.intValue():0,military=row.get("military_best") instanceof Number n?n.intValue():0;
            result.put(row.get("player_key").toString(),Map.of("medalId",row.get("public_id"),"unlocked",unlocked(classic,military),"equipped",Objects.toString(row.get("equipped"),"")));
        }
        // Reuse transactional backfill; inserting an empty row here
        // would incorrectly mark legacy medals as already migrated.
        NavalMedals medals=new NavalMedals(jdbc,json);
        int initialized=0;
        for(String key:actual)if(!result.get(key).containsKey("medalId") && key.matches("U:[0-9]+|G:[a-f0-9]{64}")){
            if(initialized++>=PROFILE_BATCH_LIMIT)break;
            Map<String,Object> wall=medals.wall(key);
            result.put(key,Map.of("medalId",wall.get("medalId"),"unlocked",wall.get("unlocked"),"equipped",wall.get("equipped")));
        }
        return result;
    }
    private Map<String,Object> wallData(String key){
        if(key==null||key.equals("AI"))return Map.of("unlocked",List.of(),"equipped","");
        backfill(key);Map<String,Object> row=jdbc.queryForMap("SELECT * FROM naval_medal_profiles WHERE player_key=?",key);
        int classic=row.get("classic_best") instanceof Number n?n.intValue():0,military=row.get("military_best") instanceof Number n?n.intValue():0;
        Map<String,Object> out=new LinkedHashMap<>();out.put("medalId",row.get("public_id"));out.put("unlocked",unlocked(classic,military));out.put("equipped",row.get("equipped")==null?"":row.get("equipped"));out.put("classicCurrent",row.get("classic_current"));out.put("militaryCurrent",row.get("military_current"));out.put("classicBest",classic);out.put("militaryBest",military);return out;
    }
    Map<String,Object> publicWall(String id){
        if(id==null||!id.matches("[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}"))throw new com.navy.common.exception.BusinessException(404,"玩家主页不存在");
        List<String> keys=jdbc.queryForList("SELECT player_key FROM naval_medal_profiles WHERE public_id=?",String.class,id);
        if(keys.isEmpty())throw new com.navy.common.exception.BusinessException(404,"玩家主页不存在");
        String key=keys.get(0);
        Map<String,Object> result=new LinkedHashMap<>(wall(key));
        var history=new NavalPublicHistory(jdbc).read(key);
        result.put("stats",history.stats());result.put("recentMatches",history.recentMatches());
        result.put("registered",false);result.put("progression",null);
        result.put("name",Objects.toString(history.latestName(),key.startsWith("G:")?"游客":"指挥官"));result.put("avatarUrl",null);
        if(key.matches("G:[a-f0-9]{64}")){
            var guests=jdbc.queryForList("SELECT display_suffix FROM naval_guests WHERE token_hash=?",String.class,key.substring(2));
            if(!guests.isEmpty()&&guests.get(0)!=null&&!guests.get(0).isBlank())result.put("name","游客-"+guests.get(0));
        }
        if(key.matches("U:[0-9]+")){
            var people=jdbc.queryForList("SELECT nickname,avatar_url FROM users WHERE id=?",Long.parseLong(key.substring(2)));
            if(!people.isEmpty()){
                result.put("name",people.get(0).get("nickname"));
                result.put("avatarUrl",people.get(0).get("avatar_url"));
                result.put("registered",true);
                var rows=jdbc.queryForList("SELECT score,promotion_wins FROM naval_progression_profiles WHERE player_key=?",key);
                int score=rows.isEmpty()?0:((Number)rows.get(0).get("score")).intValue();
                int streak=rows.isEmpty()?0:((Number)rows.get(0).get("promotion_wins")).intValue();
                result.put("progression",NavalProgression.summary(score,streak));
            }
        }
        return result;
    }
    Map<String,Object> equip(String key,String id){return transaction(()->equipData(key,id),true);}
    private Map<String,Object> equipData(String key,String id){Map<String,Object> wall=wallData(key);if(id!=null&&!id.isEmpty()&&!((List<?>)wall.get("unlocked")).contains(id))NavalEngine.bad("尚未解锁该勋章");jdbc.update("UPDATE naval_medal_profiles SET equipped=? WHERE player_key=?",id==null?"":id,key);return wallData(key);}

}
