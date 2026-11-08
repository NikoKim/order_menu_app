package com.order.menu.module.entertainment.mode.naval;

import java.util.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;

final class NavalMedals {
    private final JdbcTemplate jdbc;private final ObjectMapper json;
    NavalMedals(JdbcTemplate jdbc,ObjectMapper json){this.jdbc=jdbc;this.json=json;}
    static final List<String> IDS=List.of("classic-3","classic-5","classic-10","military-3","military-5","military-10");
    static int next(int current,boolean won){return won?current+1:0;}
    static List<String> unlocked(int classic,int military){return IDS.stream().filter(id->(id.startsWith("classic")?classic:military)>=Integer.parseInt(id.substring(id.indexOf('-')+1))).toList();}
    private Map<String,Object> decode(String source){try{return json.readValue(source,new TypeReference<LinkedHashMap<String,Object>>(){});}catch(Exception e){throw new IllegalStateException("Invalid naval medal archive",e);}}
    @SuppressWarnings("unchecked")
    private <T>T transaction(java.util.function.Supplier<T> action,boolean independent){if(jdbc.getDataSource()==null)return action.get();var tx=new org.springframework.transaction.support.TransactionTemplate(new org.springframework.jdbc.datasource.DataSourceTransactionManager(jdbc.getDataSource()));if(independent)tx.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);return tx.execute(status->action.get());}
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
        for(String source:jdbc.queryForList("SELECT result_json FROM naval_round_results WHERE player0_key=? OR player1_key=? ORDER BY finished_at,game_id",String.class,key,key))credit(decode(source),key);
    }
    Map<String,Object> wall(String key){if(key==null||key.equals("AI"))return Map.of("unlocked",List.of(),"equipped","");return transaction(()->wallData(key),true);}
    Map<String,Object> readWall(String key){
        if(key==null||key.equals("AI"))return Map.of("unlocked",List.of(),"equipped","");
        var rows=jdbc.queryForList("SELECT * FROM naval_medal_profiles WHERE player_key=?",key);
        if(rows.isEmpty())return Map.of("unlocked",List.of(),"equipped","");
        var row=rows.get(0);int classic=((Number)row.get("classic_best")).intValue(),military=((Number)row.get("military_best")).intValue();
        Map<String,Object> out=new LinkedHashMap<>();out.put("medalId",row.get("public_id"));out.put("unlocked",unlocked(classic,military));out.put("equipped",row.get("equipped")==null?"":row.get("equipped"));return out;
    }
    static Map<String,Map<String,Object>> readWalls(JdbcTemplate jdbc,Collection<String> keys){
        Map<String,Map<String,Object>> result=new LinkedHashMap<>();
        for(String key:keys)result.put(key,Map.of("unlocked",List.of(),"equipped",""));
        List<String> actual=keys.stream().filter(Objects::nonNull).filter(key->!key.equals("AI")).distinct().toList();
        if(actual.isEmpty())return result;
        String placeholders=String.join(",",Collections.nCopies(actual.size(),"?"));
        for(var row:jdbc.queryForList("SELECT player_key,public_id,classic_best,military_best,equipped FROM naval_medal_profiles WHERE player_key IN ("+placeholders+")",actual.toArray())){
            int classic=row.get("classic_best") instanceof Number n?n.intValue():0,military=row.get("military_best") instanceof Number n?n.intValue():0;
            result.put(row.get("player_key").toString(),Map.of("medalId",row.get("public_id"),"unlocked",unlocked(classic,military),"equipped",Objects.toString(row.get("equipped"),"")));
        }
        return result;
    }
    private Map<String,Object> wallData(String key){
        if(key==null||key.equals("AI"))return Map.of("unlocked",List.of(),"equipped","");
        backfill(key);Map<String,Object> row=jdbc.queryForMap("SELECT * FROM naval_medal_profiles WHERE player_key=?",key);
        int classic=row.get("classic_best") instanceof Number n?n.intValue():0,military=row.get("military_best") instanceof Number n?n.intValue():0;
        Map<String,Object> out=new LinkedHashMap<>();out.put("medalId",row.get("public_id"));out.put("unlocked",unlocked(classic,military));out.put("equipped",row.get("equipped")==null?"":row.get("equipped"));out.put("classicCurrent",row.get("classic_current"));out.put("militaryCurrent",row.get("military_current"));out.put("classicBest",classic);out.put("militaryBest",military);return out;
    }
    Map<String,Object> publicWall(String id){List<String> keys=jdbc.queryForList("SELECT player_key FROM naval_medal_profiles WHERE public_id=?",String.class,id);if(keys.isEmpty())NavalEngine.bad("勋章墙不存在");return wall(keys.get(0));}
    Map<String,Object> equip(String key,String id){return transaction(()->equipData(key,id),true);}
    private Map<String,Object> equipData(String key,String id){Map<String,Object> wall=wallData(key);if(id!=null&&!id.isEmpty()&&!((List<?>)wall.get("unlocked")).contains(id))NavalEngine.bad("尚未解锁该勋章");jdbc.update("UPDATE naval_medal_profiles SET equipped=? WHERE player_key=?",id==null?"":id,key);return wallData(key);}

}
