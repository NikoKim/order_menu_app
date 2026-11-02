package com.order.menu.module.entertainment.mode.naval;

import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.order.menu.common.exception.BusinessException;
import com.order.menu.module.entertainment.core.service.PlayerIdentity;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.jdbc.core.*;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.yaml.snakeyaml.Yaml;

@EnabledIfSystemProperty(named="naval.mysql",matches="true")
class NavalCommunityMysqlTest {
    @org.junit.jupiter.api.condition.EnabledIfSystemProperty(named="naval.install.v58",matches="true")
    @Test void installSharedResultMigrationOnExplicitLocalDevelopmentRequest() throws Exception {
        Map<?,?> config=new org.yaml.snakeyaml.Yaml().load(Files.readString(Path.of("src/main/resources/application-dev.yml")));
        Map<?,?> db=(Map<?,?>)((Map<?,?>)config.get("spring")).get("datasource");
        String url=db.get("url").toString(); assertTrue(url.startsWith("jdbc:mysql://127.0.0.1:") || url.startsWith("jdbc:mysql://localhost:"));
        var ds=new org.apache.ibatis.datasource.unpooled.UnpooledDataSource("com.mysql.cj.jdbc.Driver",url,db.get("username").toString(),db.get("password").toString());
        try(var connection=ds.getConnection()) {
            assertEquals("order_menu",connection.getCatalog());
            try(var columns=connection.getMetaData().getColumns(connection.getCatalog(),null,"naval_lobby_messages","battle_json")) { if(columns.next()) return; }
            org.springframework.jdbc.datasource.init.ScriptUtils.executeSqlScript(connection,new org.springframework.core.io.ByteArrayResource(Files.readAllBytes(Path.of("src/main/resources/db/migration/V58__naval_shared_results.sql")))) ;
        }
    }
    private static final List<String> TABLES=List.of("naval_medal_profiles","naval_medal_rounds","naval_lobby_messages","naval_reviews","naval_round_results","naval_rooms","naval_profiles","naval_matchmaking_lock","entertainment_guests","users");
    private static String sql(String value,String prefix) {
        for(String table:TABLES) value=value.replaceAll("\\b"+table+"\\b",prefix+table);
        return value;
    }
    @Test void realCommunityRankingsHistoryMessagesAndReviewPermissions() throws Exception {
        Map<?,?> config=new Yaml().load(Files.readString(Path.of("src/main/resources/application-dev.yml")));
        Map<?,?> db=(Map<?,?>)((Map<?,?>)config.get("spring")).get("datasource");
        assertTrue(db.get("url").toString().startsWith("jdbc:mysql://127.0.0.1:3307/order_menu?"));
        var ds=new UnpooledDataSource("com.mysql.cj.jdbc.Driver",db.get("url").toString(),db.get("username").toString(),db.get("password").toString());
        String prefix="qa_community_"+UUID.randomUUID().toString().replace("-","").substring(0,12)+"_";
        var jdbc=new JdbcTemplate(ds) {
            @Override public Map<String,Object> queryForMap(String q,Object...args){return super.queryForMap(sql(q,prefix),args);}
            @Override public int update(String q) { return super.update(sql(q,prefix)); }
            @Override public int update(String q,Object...args) { return super.update(sql(q,prefix),args); }
            @Override public <T> List<T> queryForList(String q,Class<T> type,Object...args) { return super.queryForList(sql(q,prefix),type,args); }
            @Override public List<Map<String,Object>> queryForList(String q,Object...args) { return super.queryForList(sql(q,prefix),args); }
            @Override public List<Map<String,Object>> queryForList(String q) { return super.queryForList(sql(q,prefix)); }
            @Override public <T> T queryForObject(String q,Class<T> type,Object...args) { return super.queryForObject(sql(q,prefix),type,args); }
            @Override public <T> T queryForObject(String q,Class<T> type) { return super.queryForObject(sql(q,prefix),type); }
            @Override public void query(String q,RowCallbackHandler callback) { super.query(sql(q,prefix),callback); }
        };
        var json=new ObjectMapper(); var community=new NavalCommunityService(jdbc,json);
        var user=new PlayerIdentity("USER",1L,null,"U:1","Admiral"); var guest=new PlayerIdentity("GUEST",null,1L,"G:one","Visitor");
        try(var connection=ds.getConnection()) {
            try {
                String ddl="CREATE TABLE users(id BIGINT PRIMARY KEY,nickname VARCHAR(50),avatar_url VARCHAR(200)); INSERT INTO users VALUES(1,'Admiral',NULL); CREATE TABLE entertainment_guests(id BIGINT PRIMARY KEY); INSERT INTO entertainment_guests VALUES(1);";
                for(String migration:List.of("V53__naval_battle.sql","V55__naval_round_results.sql","V56__naval_community.sql","V57__naval_lobby_cards.sql","V58__naval_shared_results.sql","V59__naval_medals.sql")) ddl+="\n"+Files.readString(Path.of("src/main/resources/db/migration/"+migration));
                ScriptUtils.executeSqlScript(connection,new ByteArrayResource(sql(ddl,prefix).getBytes(StandardCharsets.UTF_8)));
                assertThrows(BusinessException.class,()->community.review(user,new NavalCommunityService.ReviewRequest(4,"Before playing")));
                for(int i=0;i<3;i++) {
                    String id="round-"+i,mode=i==2?"MULTIPLAYER":"AI";
                    Map<String,Object> archive=Map.of("gameId",id,"mode",mode,"winner",i==1?1:0,"finishedAt",1000+i,"players",List.of(Map.of("key","U:1","name","Admiral","coinsSpent",80),Map.of("key",i==2?"G:two":"AI","name","Opponent","coinsSpent",250)),"events",List.of());
                    archive=new LinkedHashMap<>(archive); archive.put("battleStarted",true);
                    jdbc.update("INSERT INTO naval_round_results(game_id,room_id,round_no,player0_key,player1_key,finished_at,result_json) VALUES(?,?,?,?,?,?,?)",id,"room-"+i,1,"U:1",i==2?"G:two":"AI",1000+i,json.writeValueAsString(archive));
                }
                assertEquals(1L,community.leaderboard("AI","wins").get(0).get("wins"));
                assertEquals(50.0,community.leaderboard("AI","rate").get(0).get("rate"));
                assertEquals(1,community.leaderboard("AI","economy").size());
                assertEquals("round-0",community.leaderboard("AI","economy").get(0).get("gameId"));
                assertEquals(170L,community.leaderboard("MULTIPLAYER","economy").get(0).get("difference"));
                var better=Map.of("gameId","best-ai","mode","AI","winner",0,"finishedAt",2000,"battleStarted",true,"players",List.of(Map.of("key","U:1","name","Admiral","coinsSpent",40),Map.of("key","AI","name","Opponent","coinsSpent",280)),"events",List.of());
                jdbc.update("INSERT INTO naval_round_results(game_id,room_id,round_no,player0_key,player1_key,finished_at,result_json) VALUES(?,?,?,?,?,?,?)","best-ai","best-room",1,"U:1","AI",2000,json.writeValueAsString(better));
                var best=community.leaderboard("AI","economy");
                assertEquals(1,best.size(),"only the best winning record per player appears");
                assertEquals("best-ai",best.get(0).get("gameId")); assertEquals(240L,best.get(0).get("difference"));
                assertTrue(community.leaderboard("AI","wins","MILITARY").isEmpty());
                var military=new LinkedHashMap<String,Object>(better); military.put("ruleSet","MILITARY");military.put("gameId","military-rank");
                jdbc.update("INSERT INTO naval_round_results(game_id,room_id,round_no,player0_key,player1_key,finished_at,result_json) VALUES(?,?,?,?,?,?,?)","military-rank","military-room",1,"U:1","AI",3000,json.writeValueAsString(military));
                assertEquals(1L,community.leaderboard("AI","wins","MILITARY").get(0).get("wins"));
                assertEquals(2L,community.leaderboard("AI","wins","CLASSIC").get(0).get("wins"));
                assertEquals(100.0,community.leaderboard("AI","rate","MILITARY").get(0).get("rate"));
                assertEquals("military-rank",community.leaderboard("AI","economy","MILITARY").get(0).get("gameId"));
                jdbc.update("DELETE FROM naval_round_results WHERE game_id=?","military-rank");
                var detail=community.detail("round-0",guest); assertEquals(-1,detail.get("you")); assertFalse(json.writeValueAsString(detail).contains("\"key\""));
                assertThrows(BusinessException.class,()->community.detail("missing",user));
                assertThrows(BusinessException.class,()->community.review(guest,new NavalCommunityService.ReviewRequest(5,"Guest")));
                community.review(user,new NavalCommunityService.ReviewRequest(5,"Great match"));
                var reviews=community.reviews(user,1); assertEquals(1L,reviews.get("total")); assertEquals(true,reviews.get("canReview"));
                assertTrue(json.valueToTree(reviews).at("/records/0/mine").asBoolean());
                community.review(user,new NavalCommunityService.ReviewRequest(4,"Updated")); assertEquals(1L,community.reviews(user,1).get("total"));
                community.deleteReview(user); assertEquals(0L,community.reviews(user,1).get("total"));
                community.send(guest,new NavalCommunityService.TextRequest("Hello 😀")); assertEquals(1,community.messages().size());
                assertEquals(true,community.messages(guest).get(0).get("mine"));
                assertEquals(false,community.messages(user).get(0).get("mine"));
                assertThrows(BusinessException.class,()->community.send(guest,new NavalCommunityService.TextRequest("Too soon")));
                assertThrows(BusinessException.class,()->community.send(guest,new NavalCommunityService.TextRequest(" ")));
                jdbc.update("DELETE FROM naval_lobby_messages");
                community.send(guest,new NavalCommunityService.TextRequest(null,"salute",null));
                assertEquals("salute",community.messages().get(0).get("stickerId"));
                assertEquals("",community.messages().get(0).get("text"));
                assertThrows(BusinessException.class,()->community.send(user,new NavalCommunityService.TextRequest("hidden text","heart",null)));
                assertThrows(BusinessException.class,()->community.send(user,new NavalCommunityService.TextRequest(null,"unknown",null)));
                assertThrows(BusinessException.class,()->community.send(user,new NavalCommunityService.TextRequest(null,null,"missing")));
                jdbc.update("DELETE FROM naval_lobby_messages");
                assertThrows(BusinessException.class,()->community.send(guest,new NavalCommunityService.TextRequest(null,null,null,"round-0",null)),"cannot share another person's archive");
                community.send(user,new NavalCommunityService.TextRequest(null,null,null,"round-0",null));
                var battleCard=(Map<?,?>)community.messages().get(0).get("battle");
                assertEquals("round-0",battleCard.get("gameId")); assertEquals(true,battleCard.get("shared"));
                assertFalse(json.writeValueAsString(battleCard).contains("\"key\""));
                jdbc.update("DELETE FROM naval_lobby_messages");
                Map<String,Object> local=new LinkedHashMap<>(); local.put("gameId","local-test:1"); local.put("mode","AI"); local.put("battleStarted",true); local.put("winner",0); local.put("events",List.of());
                local.put("players",List.of(Map.of("name","forged","key","secret","ships",NavalEngine.randomFleet()),Map.of("name","AI","ships",NavalEngine.randomFleet())));
                assertThrows(BusinessException.class,()->community.send(user,new NavalCommunityService.TextRequest(null,null,null,null,local)));
                local.put("events","malformed");
                assertThrows(BusinessException.class,()->community.send(guest,new NavalCommunityService.TextRequest(null,null,null,null,local)),"malformed public replays cannot enter lobby");
                local.put("events",List.of());
                local.put("events",List.of(Map.of("id",1,"player",0,"weapon","submarine","route",List.of(Map.of("x",-2,"y",5),Map.of("x",-1,"y",5)),"cells",List.of(Map.of("x",0,"y",5,"result","miss"),Map.of("x",1,"y",5,"result","mine")),"destroyed",true)));
                community.send(guest,new NavalCommunityService.TextRequest(null,null,null,null,local));
                var localCard=(Map<?,?>)community.messages().get(0).get("battle");
                assertEquals(true,localCard.get("local")); assertFalse(json.writeValueAsString(localCard).contains("secret"));
                assertEquals("Visitor",((Map<?,?>)((List<?>)localCard.get("players")).get(0)).get("name"));
                assertEquals(1,community.leaderboard("AI","economy").size(),"shared local results never enter rankings");
                jdbc.update("DELETE FROM naval_lobby_messages");
                local.put("ruleSet","MILITARY");
                List<NavalState.Ship> militaryFleet=NavalMilitary.randomFleet();
                NavalState.Ship damaged=militaryFleet.get(0);damaged.hits.add(damaged.x+","+damaged.y);
                local.put("players",List.of(Map.of("name","forged","ships",militaryFleet),Map.of("name","AI","ships",NavalMilitary.randomFleet())));
                local.put("events",List.of(Map.of("id",1,"player",0,"weapon","fighter","shipId",damaged.id,"plane",1,"route",List.of(Map.of("x",12,"y",5)),"cells",List.of(Map.of("x",3,"y",5,"result","miss")))));
                community.send(guest,new NavalCommunityService.TextRequest(null,null,null,null,local));
                var militaryCard=(Map<?,?>)community.messages().get(0).get("battle");
                assertEquals("MILITARY",militaryCard.get("ruleSet"));
                var savedEvent=(Map<?,?>)((List<?>)militaryCard.get("events")).get(0);
                assertEquals(damaged.id,savedEvent.get("shipId"));assertEquals(1,savedEvent.get("plane"));
                var savedHull=(Map<?,?>)((List<?>)((Map<?,?>)((List<?>)militaryCard.get("players")).get(0)).get("ships")).get(0);
                assertEquals(List.of(damaged.x+","+damaged.y),savedHull.get("hits"),"validating shared military fleets must preserve damage");
                jdbc.update("DELETE FROM naval_lobby_messages");
                Map<String,Object> shared=new LinkedHashMap<>(); shared.put("ruleSet","MILITARY"); shared.put("phase","WAITING"); shared.put("mode","MULTIPLAYER"); shared.put("deadline",System.currentTimeMillis()+60000); shared.put("round",1); shared.put("inviteCode","QA1234"); shared.put("players",List.of(Map.of("key","G:one","name","Visitor","left",false)));
                jdbc.update("INSERT INTO naval_rooms(id,invite_code,mode,phase,host_key,deadline,state_json) VALUES(?,?,?,?,?,?,?)","shared","QA1234","MULTIPLAYER","WAITING","G:one",shared.get("deadline"),json.writeValueAsString(shared));
                community.send(guest,new NavalCommunityService.TextRequest(null,null,"shared"));
                assertThrows(BusinessException.class,()->community.send(guest,new NavalCommunityService.TextRequest(null,null,"shared")),"one invitation per round even if retried");
                assertEquals(1,community.messages().size());
                assertEquals("WAITING",((Map<?,?>)community.messages().get(0).get("room")).get("status"));
                assertEquals("MILITARY",((Map<?,?>)community.messages().get(0).get("room")).get("ruleSet"));
                assertEquals(true,((Map<?,?>)community.messages().get(0).get("room")).get("requiresInvite"));
                assertFalse(((Map<?,?>)community.messages().get(0).get("room")).containsKey("inviteCode"));
                assertFalse(json.writeValueAsString(community.messages()).contains("G:one"));
                shared.put("phase","BATTLE");
                jdbc.update("UPDATE naval_rooms SET phase='BATTLE',state_json=? WHERE id='shared'",json.writeValueAsString(shared));
                assertEquals("STARTED",((Map<?,?>)community.messages().get(0).get("room")).get("status"));
                shared.put("allowSpectators",false); jdbc.update("UPDATE naval_rooms SET state_json=? WHERE id='shared'",json.writeValueAsString(shared));
                assertEquals(false,((Map<?,?>)community.messages().get(0).get("room")).get("allowSpectators"));
                assertThrows(BusinessException.class,()->community.send(guest,new NavalCommunityService.TextRequest("Not during battle")));
                shared.put("phase","FINISHED"); jdbc.update("UPDATE naval_rooms SET phase='FINISHED',state_json=? WHERE id='shared'",json.writeValueAsString(shared));
                assertEquals("FINISHED",((Map<?,?>)community.messages().get(0).get("room")).get("status"));
                shared.put("phase","WAITING"); shared.put("players",List.of(Map.of("key","G:one","name","Visitor","left",true)));
                jdbc.update("UPDATE naval_rooms SET phase='WAITING',state_json=? WHERE id='shared'",json.writeValueAsString(shared));
                assertEquals("CLOSED",((Map<?,?>)community.messages().get(0).get("room")).get("status"));
            } finally {
                assertTrue(prefix.matches("qa_community_[a-f0-9]{12}_"));
                try(var statement=connection.createStatement()) { for(String table:TABLES) statement.execute("DROP TABLE IF EXISTS `"+prefix+table+"`"); }
            }
        }
    }
}
