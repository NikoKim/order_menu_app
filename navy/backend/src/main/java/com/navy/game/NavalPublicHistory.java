package com.navy.game;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.jdbc.core.JdbcTemplate;

/** Public summaries deliberately cannot identify or retrieve a private battle replay. */
final class NavalPublicHistory {
    static final int RECENT_LIMIT=10;
    private final JdbcTemplate jdbc;
    NavalPublicHistory(JdbcTemplate jdbc){this.jdbc=jdbc;}

    private static String text(String path){return "JSON_UNQUOTE(JSON_EXTRACT(result_json,'$."+path+"'))";}
    private static String value(String path){return "JSON_EXTRACT(result_json,'$."+path+"')";}
    private static String eligibility(){
        String mode=text("mode"),winner=value("winner"),started=value("battleStarted");
        // Missing legacy flags need positive battle evidence, never just an archived row.
        String legacyEvidence="("+text("finishReason")+" IN ('DESTROYED','MILITARY_DEFEAT')"
            +" OR JSON_SEARCH(result_json,'one','start',NULL,'$.events[*].weapon') IS NOT NULL"
            +" OR COALESCE("+value("players[0].attacks")+",0)>0 OR COALESCE("+value("players[1].attacks")+",0)>0"
            +" OR COALESCE("+value("players[0].shots")+",0)>0 OR COALESCE("+value("players[1].shots")+",0)>0)";
        return "finished_at>0 AND player0_key<>player1_key"
            +" AND JSON_TYPE("+value("players")+")='ARRAY' AND JSON_LENGTH(result_json,'$.players')=2"
            +" AND "+text("players[0].key")+"=player0_key AND "+text("players[1].key")+"=player1_key"
            +" AND JSON_TYPE("+winner+")='INTEGER' AND "+winner+" IN (0,1)"
            +" AND COALESCE("+text("phase")+",'FINISHED')='FINISHED'"
            +" AND COALESCE("+text("ruleSet")+",'CLASSIC') IN ('CLASSIC','MILITARY')"
            +" AND (("+mode+"='MULTIPLAYER' AND player0_key<>'AI' AND player1_key<>'AI')"
            +" OR ("+mode+"='AI' AND ((player0_key='AI')<>(player1_key='AI'))))"
            +" AND COALESCE("+text("local")+",'false')<>'true' AND COALESCE("+text("localSimulation")+",'false')<>'true'"
            +" AND room_id NOT LIKE 'local-%' AND game_id NOT LIKE 'local-%'"
            +" AND COALESCE("+text("finishReason")+",'') NOT IN ('CANCELLED','EXPIRED','MATCHED','PREPARATION_TIMEOUT','TUTORIAL')"
            +" AND ("+started+"=TRUE OR (("+started+" IS NULL OR JSON_TYPE("+started+")='NULL') AND "+legacyEvidence+"))";
    }
    private static String branch(int seat){return " FROM naval_round_results FORCE INDEX (idx_naval_result_player"+seat+") WHERE player"+seat+"_key=? AND "+eligibility();}
    private static String won(int seat){return "IF("+value("winner")+"="+seat+",1,0)";}
    static String statisticsSql(){
        return "SELECT COUNT(*) AS games,COALESCE(SUM(won),0) AS wins FROM (SELECT "+won(0)+" AS won"+branch(0)
            +" UNION ALL SELECT "+won(1)+" AS won"+branch(1)+") public_matches";
    }
    static String recentSql(){
        List<String> branches=new ArrayList<>();
        for(int seat=0;seat<2;seat++)branches.add("(SELECT game_id,finished_at AS finishedAt,"+text("mode")+" AS mode,COALESCE("+text("ruleSet")+",'CLASSIC') AS ruleSet,"
            +won(seat)+" AS won,NULLIF("+text("players["+(1-seat)+"].name")+",'null') AS opponentName,NULLIF("+text("players["+seat+"].name")+",'null') AS playerName"
            +branch(seat)+" ORDER BY finished_at DESC,game_id DESC LIMIT "+RECENT_LIMIT+")");
        return "SELECT finishedAt,mode,ruleSet,won,opponentName,playerName FROM ("+String.join(" UNION ALL ",branches)+") public_recent ORDER BY finishedAt DESC,game_id DESC LIMIT "+RECENT_LIMIT;
    }
    record History(Map<String,Object> stats,List<Map<String,Object>> recentMatches,String latestName){}
    History read(String key){
        var totals=jdbc.queryForMap(statisticsSql(),key,key);
        Map<String,Object> stats=Map.of("games",number(totals.get("games")),"wins",number(totals.get("wins")));
        var records=jdbc.queryForList(recentSql(),key,key);
        List<Map<String,Object>> recent=new ArrayList<>();
        for(var row:records.stream().limit(RECENT_LIMIT).toList()){
            Map<String,Object> summary=new LinkedHashMap<>();
            summary.put("finishedAt",number(row.get("finishedAt")));
            summary.put("mode",row.get("mode"));summary.put("ruleSet",row.get("ruleSet"));
            summary.put("won",Boolean.TRUE.equals(row.get("won")) || row.get("won") instanceof Number won && won.intValue()==1);
            summary.put("opponentName",Objects.toString(row.get("opponentName"),"指挥官"));
            recent.add(summary);
        }
        return new History(stats,recent,records.isEmpty()?null:Objects.toString(records.get(0).get("playerName"),null));
    }
    private static long number(Object value){return value instanceof Number number?number.longValue():0;}
}
