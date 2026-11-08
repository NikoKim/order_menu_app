package com.order.menu.module.entertainment.mode.naval;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
final class NavalRankings {
 @SuppressWarnings("unchecked") static void credit(JdbcTemplate jdbc,Map<String,Object> result){
  if(jdbc.update("INSERT IGNORE INTO naval_ranking_rounds(game_id,credited_at) VALUES(?,?)",result.get("gameId"),result.get("finishedAt"))==0)return;
  String mode=Objects.toString(result.get("mode")),rule=Objects.toString(result.getOrDefault("ruleSet","CLASSIC"));
  if("AI".equals(mode)&&Boolean.FALSE.equals(result.get("battleStarted")))return;
  var people=(List<Map<String,Object>>)result.get("players");
  for(int i=0;i<people.size();i++){
   var person=people.get(i);String key=Objects.toString(person.get("key"));if(!key.startsWith("U:"))continue;
   var other=people.get(1-i);boolean won=result.get("winner") instanceof Number n&&n.intValue()==i;
   jdbc.update("INSERT INTO naval_ranking_totals(player_key,mode,rule_set,name,avatar_url,games,wins) VALUES(?,?,?,?,?,1,?) ON DUPLICATE KEY UPDATE name=VALUES(name),avatar_url=VALUES(avatar_url),games=games+1,wins=wins+VALUES(wins)",key,mode,rule,person.get("name"),person.get("avatarUrl"),won?1:0);
   if(won&&person.get("coinsSpent") instanceof Number spent&&other.get("coinsSpent") instanceof Number opponent){
    long difference=opponent.longValue()-spent.longValue(),at=((Number)result.get("finishedAt")).longValue();
    jdbc.update("UPDATE naval_ranking_totals SET best_economy=?,best_game_id=?,best_finished_at=?,spent=?,opponent_spent=? WHERE player_key=? AND mode=? AND rule_set=? AND (best_economy IS NULL OR best_economy<? OR (best_economy=? AND best_finished_at<?))",difference,result.get("gameId"),at,spent.longValue(),opponent.longValue(),key,mode,rule,difference,difference,at);
   }
  }
 }
 static List<Map<String,Object>> page(JdbcTemplate jdbc,String mode,String metric,String rule,int page,int size){
  if(!List.of("AI","MULTIPLAYER").contains(mode)||!List.of("CLASSIC","MILITARY").contains(rule)||!List.of("wins","rate","economy").contains(metric))NavalEngine.bad("排行榜类型无效");
  if(page<1||page>10000||size<1||size>100)NavalEngine.bad("排行榜分页无效");
  String field=switch(metric){case "rate"->"win_rate";case "economy"->"best_economy";default->"wins";};
  var rows=jdbc.queryForList("SELECT player_key,name,avatar_url AS avatarUrl,games,wins,win_rate AS rate,best_economy AS difference,best_game_id AS gameId,best_finished_at AS finishedAt,spent,opponent_spent AS opponentSpent FROM naval_ranking_totals WHERE mode=? AND rule_set=?"+(metric.equals("economy")?" AND best_economy IS NOT NULL":"")+" ORDER BY "+field+" DESC,name,player_key LIMIT ? OFFSET ?",mode,rule,size,(page-1)*size);
  Set<String> keys=new LinkedHashSet<>();rows.forEach(r->keys.add(r.get("player_key").toString()));var medals=NavalMedals.readWalls(jdbc,keys);
  rows.forEach(r->{r.put("medals",medals.get(r.remove("player_key").toString()));if(!metric.equals("economy"))for(String key:List.of("gameId","difference","finishedAt","spent","opponentSpent"))r.remove(key);});return rows;
 }
}
