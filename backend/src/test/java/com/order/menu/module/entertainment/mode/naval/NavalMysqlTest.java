package com.order.menu.module.entertainment.mode.naval;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.order.menu.common.exception.BusinessException;
import com.order.menu.module.entertainment.core.service.PlayerIdentity;
import com.order.menu.module.entertainment.mode.naval.NavalService.CreateRequest;
import com.order.menu.module.entertainment.mode.naval.NavalState.Action;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.transaction.support.TransactionTemplate;
import org.yaml.snakeyaml.Yaml;

@EnabledIfSystemProperty(named="naval.mysql",matches="true")
class NavalMysqlTest {
    @Test void realMysqlMatchingInvitesPersistencePrivateViewsAndProfiles() throws Exception {
        Map<?,?> config=new Yaml().load(Files.readString(Path.of("src/main/resources/application-dev.yml")));
        Map<?,?> db=(Map<?,?>)((Map<?,?>)config.get("spring")).get("datasource");
        var ds=new UnpooledDataSource("com.mysql.cj.jdbc.Driver",db.get("url").toString(),db.get("username").toString(),db.get("password").toString());
        String prefix="qa_naval_"+UUID.randomUUID().toString().replace("-", "").substring(0,12)+"_";
        JdbcTemplate jdbc=new PrefixedJdbc(ds,prefix);
        var manager=new DataSourceTransactionManager(ds); var tx=new TransactionTemplate(manager);
        NavalService service=new NavalService(jdbc,new ObjectMapper(),manager);
        try (var connection=ds.getConnection()) {
            String ddl=replaceTables(Files.readString(Path.of("src/main/resources/db/migration/V53__naval_battle.sql"))+"\n"+Files.readString(Path.of("src/main/resources/db/migration/V54__naval_page_presence.sql"))+"\n"+Files.readString(Path.of("src/main/resources/db/migration/V55__naval_round_results.sql"))+"\n"+Files.readString(Path.of("src/main/resources/db/migration/V59__naval_medals.sql")),prefix);
            ScriptUtils.executeSqlScript(connection,new ByteArrayResource(ddl.getBytes(StandardCharsets.UTF_8)));
            try {
                jdbc.update("DELETE FROM naval_matchmaking_lock WHERE id=1");
                assertThrows(org.springframework.dao.EmptyResultDataAccessException.class,() -> jdbc.queryForObject("SELECT id FROM naval_matchmaking_lock WHERE id=1 FOR UPDATE",Integer.class),"the old lock query reproduces the deployment 500 when a restore omits the seed");
                PlayerIdentity a=guest("a"),b=guest("b"),stranger=guest("stranger");
                var pool=Executors.newFixedThreadPool(2);
                Map<String,Object> first,second;
                try {
                    Callable<Map<String,Object>> callA=() -> tx.execute(status -> service.create(a,new CreateRequest("MULTIPLAYER",true)));
                    Callable<Map<String,Object>> callB=() -> tx.execute(status -> service.create(b,new CreateRequest("MULTIPLAYER",true)));
                    var results=pool.invokeAll(List.of(callA,callB)); first=results.get(0).get(); second=results.get(1).get();
                } finally { pool.shutdownNow(); }
                String id=first.get("id").toString(); assertEquals(id,second.get("id"),"concurrent empty-queue creates must meet");
                assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM naval_matchmaking_lock WHERE id=1",Integer.class),"concurrent first requests repair exactly one missing mutex row");
                Map<String,Object> view=tx.execute(status -> service.get(id,a)); assertEquals("PLACEMENT",view.get("phase"));
                assertEquals(2,((List<?>)view.get("players")).size());
                Action clear=new Action(); clear.type="DEPLOY"; clear.ships=List.of();
                tx.execute(status -> service.action(id,a,clear));
                assertTrue(new ObjectMapper().valueToTree(tx.execute(status -> service.get(id,a))).at("/players/"+view.get("you")+"/ships").isEmpty());
                Action incomplete=new Action(); incomplete.type="LAYOUT_READY";
                assertThrows(BusinessException.class,() -> tx.execute(status -> service.action(id,a,incomplete)));
                assertThrows(BusinessException.class,() -> tx.execute(status -> service.get(id,stranger)));
                assertThrows(BusinessException.class,() -> tx.execute(status -> service.join(stranger,view.get("inviteCode").toString())));
                Action deploy=new Action(); deploy.type="LAYOUT_READY"; deploy.ships=NavalEngine.randomFleet();
                tx.execute(status -> service.action(id,a,deploy));
                Action deployB=new Action(); deployB.type="LAYOUT_READY"; deployB.ships=NavalEngine.randomFleet();
                tx.execute(status -> service.action(id,b,deployB));
                assertEquals("SUPPLY",tx.execute(status -> service.get(id,a)).get("phase"));
                Action ready=new Action(); ready.type="READY";
                tx.execute(status -> service.action(id,a,ready)); tx.execute(status -> service.action(id,b,ready));
                var after=tx.execute(status -> service.get(id,a)); assertEquals("BATTLE",after.get("phase"));
                var tree=new ObjectMapper().valueToTree(after); int opponent=1-(int)after.get("you");
                assertEquals(0,tree.at("/players/"+opponent+"/ships").size());
                var directory=service.rooms();
                assertTrue(directory.stream().anyMatch(row -> id.equals(row.get("id"))));
                String publicDirectory=new ObjectMapper().writeValueAsString(directory);
                assertFalse(publicDirectory.contains("inviteCode")); assertFalse(publicDirectory.contains("ships")); assertFalse(publicDirectory.contains(a.key()));
                assertEquals("SPECTATOR",tx.execute(status -> service.enter(id,stranger,new NavalService.EnterRequest(null))).get("role"));
                Action privacy=new Action(); privacy.type="SPECTATORS"; privacy.value=false;
                assertEquals(false,tx.execute(status -> service.action(id,a,privacy)).get("allowSpectators"));
                assertThrows(BusinessException.class,() -> tx.execute(status -> service.get(id,stranger)));
                assertThrows(BusinessException.class,() -> tx.execute(status -> service.enter(id,stranger,new NavalService.EnterRequest(null))));
                privacy.value=true; tx.execute(status -> service.action(id,a,privacy));
                assertEquals("SPECTATOR",tx.execute(status -> service.enter(id,stranger,new NavalService.EnterRequest(null))).get("role"));
                NavalService restarted=new NavalService(jdbc,new ObjectMapper(),manager);
                assertEquals("BATTLE",tx.execute(status -> restarted.get(id,b)).get("phase"));
                Action leave=new Action(); leave.type="LEAVE"; tx.execute(status -> service.action(id,a,leave));
                assertThrows(BusinessException.class,() -> service.profile(a));
                PlayerIdentity user=new PlayerIdentity("USER",991L,null,"U:991","测试用户");
                var formation=new NavalService.Formation("f1","阵型一",NavalEngine.randomFleet());
                tx.execute(status -> service.saveProfile(user,new NavalService.ProfileRequest(List.of(formation))));
                assertEquals(1,((List<?>)service.profile(user).get("formations")).size());
                assertTrue(((List<?>)service.profile(new PlayerIdentity("USER",992L,null,"U:992","其他用户")).get("formations")).isEmpty());
                var signedAi=tx.execute(status -> service.create(user,new CreateRequest("AI",false)));
                String aiId=signedAi.get("id").toString();
                assertEquals("PLACEMENT",signedAi.get("phase"));
                assertEquals(10,new ObjectMapper().valueToTree(signedAi).at("/players/0/ships").size());
                assertEquals(aiId,tx.execute(status -> restarted.get(aiId,user)).get("id"));
                assertEquals("SPECTATOR",tx.execute(status -> service.enter(aiId,stranger,new NavalService.EnterRequest(null))).get("role"));
                privacy.value=false; tx.execute(status -> service.action(aiId,user,privacy));
                assertThrows(BusinessException.class,() -> tx.execute(status -> service.get(aiId,stranger)));
                tx.execute(status -> service.action(aiId,user,leave));
                assertTrue(((List<?>)service.profile(user).get("history")).isEmpty(),"AI placement cancellation must not be archived as a defeat");
                var aiSupply=tx.execute(status -> service.create(user,new CreateRequest("AI",false)));
                String supplyId=aiSupply.get("id").toString();
                tx.execute(status -> service.action(supplyId,user,deploy));
                ObjectMapper mapper=new ObjectMapper();
                NavalState paused=mapper.readValue(jdbc.queryForObject("SELECT state_json FROM naval_rooms WHERE id=?",String.class,supplyId),NavalState.class);
                paused.deadline=System.currentTimeMillis()-60_000; paused.players.get(0).disconnectedAt=System.currentTimeMillis()-120_000;
                jdbc.update("UPDATE naval_rooms SET state_json=? WHERE id=?",mapper.writeValueAsString(paused),supplyId);
                var restored=tx.execute(status -> service.get(supplyId,user));
                assertEquals("SUPPLY",restored.get("phase"),"offline AI preparation must not start a game without its owner");
                assertTrue(((Number)restored.get("deadline")).longValue()>System.currentTimeMillis());
                tx.execute(status -> service.action(supplyId,user,leave));
                assertTrue(((List<?>)service.profile(user).get("history")).isEmpty(),"AI supply cancellation must not be archived either");
                var aiBattle=tx.execute(status -> service.create(user,new CreateRequest("AI",false)));
                String battleId=aiBattle.get("id").toString();
                tx.execute(status -> service.action(battleId,user,deploy));
                tx.execute(status -> service.action(battleId,user,ready));
                tx.execute(status -> service.action(battleId,user,leave));
                var userHistory=(List<?>)service.profile(user).get("history");
                assertEquals(1,userHistory.size());
                assertEquals(battleId,((Map<?,?>)userHistory.get(0)).get("id"));
                assertEquals(false,((Map<?,?>)userHistory.get(0)).get("won"));
                var privateRoom=tx.execute(status -> service.create(stranger,new CreateRequest("MULTIPLAYER",false)));
                assertTrue(service.rooms().stream().anyMatch(row -> privateRoom.get("id").equals(row.get("id")) && Boolean.TRUE.equals(row.get("requiresInvite"))));
                assertThrows(BusinessException.class,() -> tx.execute(status -> service.enter(privateRoom.get("id").toString(),a,new NavalService.EnterRequest("WRONG1"))));
                assertThrows(BusinessException.class,() -> tx.execute(status -> service.enter(privateRoom.get("id").toString(),a,new NavalService.EnterRequest(null))));
                jdbc.update("DELETE FROM naval_matchmaking_lock WHERE id=1");
                var joining=tx.execute(status -> service.enter(privateRoom.get("id").toString(),a,new NavalService.EnterRequest(privateRoom.get("inviteCode").toString())));
                assertEquals("PLACEMENT",joining.get("phase")); assertEquals(privateRoom.get("id"),joining.get("id"));
                assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM naval_matchmaking_lock WHERE id=1",Integer.class),"invite join also repairs the mutex after an import");
                PlayerIdentity mirrorOwner=guest("mirror"),mirrorViewer=guest("mirror-viewer");
                var localPlayers=List.of(NavalEngine.player("forged-key","Forged"),NavalEngine.player("AI","AI"));
                localPlayers.forEach(p -> p.ships=NavalEngine.randomFleet());
                String localId="local-"+System.currentTimeMillis()+"-qa";
                var local=new NavalService.LocalRoomRequest(localId,1,"BATTLE",System.currentTimeMillis()+30000,0,null,true,localPlayers,List.of(),0);
                tx.execute(status -> service.publishLocal(mirrorOwner,local));
                assertTrue(service.rooms().stream().anyMatch(row -> localId.equals(row.get("id"))));
                var mirrored=tx.execute(status -> service.enter(localId,mirrorViewer,new NavalService.EnterRequest(null)));
                assertEquals("SPECTATOR",mirrored.get("role"));
                assertEquals(10,new ObjectMapper().valueToTree(mirrored).at("/players/1/ships").size());
                assertThrows(BusinessException.class,() -> tx.execute(status -> service.publishLocal(mirrorViewer,local)));
                var hiddenLocal=new NavalService.LocalRoomRequest(localId,1,"BATTLE",System.currentTimeMillis()+30000,0,null,false,localPlayers,List.of(),0);
                tx.execute(status -> service.publishLocal(mirrorOwner,hiddenLocal));
                assertThrows(BusinessException.class,() -> tx.execute(status -> service.get(localId,mirrorViewer)));
                int archives=jdbc.queryForObject("SELECT COUNT(*) FROM naval_round_results",Integer.class);
                var endedLocal=new NavalService.LocalRoomRequest(localId,1,"FINISHED",0,0,0,false,localPlayers,List.of(),0);
                tx.execute(status -> service.publishLocal(mirrorOwner,endedLocal));
                assertEquals(archives,jdbc.queryForObject("SELECT COUNT(*) FROM naval_round_results",Integer.class),"local mirrors never upload guest history or rankings");
                NavalState stale=new ObjectMapper().readValue(jdbc.queryForObject("SELECT state_json FROM naval_rooms WHERE id=?",String.class,localId),NavalState.class);
                stale.phase="BATTLE"; NavalEngine.advance(stale,stale.lastSeenAt+30001); assertEquals("FINISHED",stale.phase); assertNull(stale.winner);
                PlayerIdentity busy=guest("busy"),partner=guest("partner"),newHost=guest("new-host");
                var busyRoom=tx.execute(status -> service.create(busy,new CreateRequest("MULTIPLAYER",false)));
                tx.execute(status -> service.join(partner,busyRoom.get("inviteCode").toString()));
                var nextRoom=tx.execute(status -> service.create(newHost,new CreateRequest("MULTIPLAYER",false)));
                String busyId=busyRoom.get("id").toString();
                NavalState expired=new ObjectMapper().readValue(jdbc.queryForObject("SELECT state_json FROM naval_rooms WHERE id=?",String.class,busyId),NavalState.class);
                for (String phase:List.of("PLACEMENT","SUPPLY","BATTLE")) {
                    expired.phase=phase; expired.deadline=System.currentTimeMillis()-1;
                    jdbc.update("UPDATE naval_rooms SET phase=?,deadline=?,state_json=? WHERE id=?",phase,expired.deadline,new ObjectMapper().writeValueAsString(expired),busyId);
                    assertThrows(BusinessException.class,() -> tx.execute(status -> service.join(busy,nextRoom.get("inviteCode").toString())),"expired turn/preparation timer must not allow a second active room");
                }
                var offline=guest("offline");
                var offlineRoom=tx.execute(status -> service.create(offline,new CreateRequest("MULTIPLAYER",true)));
                String offlineId=offlineRoom.get("id").toString();
                jdbc.update("UPDATE naval_rooms SET created_at=CURRENT_TIMESTAMP - INTERVAL 2 MINUTE,updated_at=CURRENT_TIMESTAMP - INTERVAL 1 MINUTE WHERE id=?",offlineId);
                var liveRoom=tx.execute(status -> service.create(guest("live"),new CreateRequest("MULTIPLAYER",true)));
                assertNotEquals(offlineId,liveRoom.get("id"),"offline browser must not remain in the automatic matching queue");
                tx.execute(status -> service.get(offlineId,offline));
                assertEquals(Boolean.TRUE,jdbc.queryForObject("SELECT updated_at > CURRENT_TIMESTAMP - INTERVAL 20 SECOND FROM naval_rooms WHERE id=?",Boolean.class,offlineId));
                var renewed=tx.execute(status -> service.create(guest("renewed-peer"),new CreateRequest("MULTIPLAYER",true)));
                assertEquals(offlineId,renewed.get("id"),"active room polling renews matchmaking presence");
                NavalPresenceService presence=new NavalPresenceService(jdbc);
                String tabA=UUID.randomUUID().toString(),tabB=UUID.randomUUID().toString(),tabC=UUID.randomUUID().toString();
                assertEquals(1L,tx.execute(status -> presence.heartbeat(a,new NavalPresenceService.PresenceRequest(tabA))).get("onlineCount"));
                assertEquals(1L,tx.execute(status -> presence.heartbeat(a,new NavalPresenceService.PresenceRequest(tabB))).get("onlineCount"),"same player across tabs counts once");
                assertEquals(2L,tx.execute(status -> presence.heartbeat(b,new NavalPresenceService.PresenceRequest(tabC))).get("onlineCount"));
                assertThrows(BusinessException.class,() -> tx.execute(status -> presence.heartbeat(b,new NavalPresenceService.PresenceRequest(tabA))));
                assertThrows(BusinessException.class,() -> tx.execute(status -> presence.heartbeat(a,new NavalPresenceService.PresenceRequest("not-a-uuid"))));
                assertEquals(2L,tx.execute(status -> presence.leave(b,tabA)).get("onlineCount"),"another player cannot remove someone else's page presence");
                assertEquals(2L,tx.execute(status -> presence.leave(a,tabA)).get("onlineCount"),"second live tab retains presence");
                assertEquals(1L,tx.execute(status -> presence.leave(a,tabB)).get("onlineCount"));
                assertEquals(0L,presence.count(System.currentTimeMillis()+NavalPresenceService.TTL_MS+1).get("onlineCount"),"closed browsers expire without explicit leave");
                spectatorSeatsRematchesAndImmutableHistory(jdbc,tx,service);
                reconnectAndWaitingCancellation(jdbc,tx,service);
                preventRematchAcrossOtherActiveRoom(jdbc,tx,service);
                int rooms = jdbc.queryForObject("SELECT COUNT(*) FROM naval_rooms",Integer.class);
                int profiles = jdbc.queryForObject("SELECT COUNT(*) FROM naval_profiles",Integer.class);
                int results = jdbc.queryForObject("SELECT COUNT(*) FROM naval_round_results",Integer.class);
                assertTrue(prefix.matches("qa_naval_[a-f0-9]{12}_"));
                try (var statement=connection.createStatement()) { statement.execute("DROP TABLE `"+prefix+"naval_page_presence`"); }
                String repair = replaceTables(Files.readString(Path.of("docs/db/migration/repair/naval_battle_bootstrap.sql")),prefix);
                for (int attempt=0;attempt<2;attempt++) ScriptUtils.executeSqlScript(connection,new ByteArrayResource(repair.getBytes(StandardCharsets.UTF_8)));
                assertEquals(rooms,jdbc.queryForObject("SELECT COUNT(*) FROM naval_rooms",Integer.class));
                assertEquals(profiles,jdbc.queryForObject("SELECT COUNT(*) FROM naval_profiles",Integer.class));
                assertEquals(results,jdbc.queryForObject("SELECT COUNT(*) FROM naval_round_results",Integer.class));
                assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM naval_page_presence",Integer.class),"repair recreates only the missing fixture table");
            } finally {
                if (!prefix.matches("qa_naval_[a-f0-9]{12}_")) throw new IllegalStateException("Invalid fixture prefix");
                try (var statement=connection.createStatement()) {
                    for (String table:List.of("naval_lobby_messages","naval_reviews","naval_medal_profiles","naval_medal_rounds","naval_round_results","naval_page_presence","naval_profiles","naval_rooms","naval_matchmaking_lock")) statement.execute("DROP TABLE IF EXISTS `"+prefix+table+"`");
                }
            }
        }
    }

    private void reconnectAndWaitingCancellation(JdbcTemplate jdbc, TransactionTemplate tx, NavalService service) throws Exception {
        PlayerIdentity host=guest("reconnect-host"),peer=guest("reconnect-peer");
        var waiting=tx.execute(status -> service.create(host,new CreateRequest("MULTIPLAYER",false)));
        String waitingId=waiting.get("id").toString(),oldTab=UUID.randomUUID().toString(),newTab=UUID.randomUUID().toString();
        tx.executeWithoutResult(status -> service.connection(waitingId,host,new NavalService.ConnectionRequest(oldTab,true)));
        tx.executeWithoutResult(status -> service.connection(waitingId,host,new NavalService.ConnectionRequest(oldTab,false)));
        assertEquals("FINISHED",tx.execute(status -> service.get(waitingId,host)).get("phase"));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM naval_round_results WHERE room_id=?",Integer.class,waitingId));
        assertThrows(BusinessException.class,() -> tx.execute(status -> service.join(peer,waiting.get("inviteCode").toString())),"cancelled invitation cannot silently recreate its room");
        assertThrows(BusinessException.class,() -> tx.execute(status -> service.spectate(peer,new NavalService.SpectateRequest(waiting.get("inviteCode").toString(),"BOTH"))));
        var room=tx.execute(status -> service.create(host,new CreateRequest("MULTIPLAYER",false)));
        String id=room.get("id").toString(); tx.execute(status -> service.join(peer,room.get("inviteCode").toString()));
        tx.executeWithoutResult(status -> service.connection(id,host,new NavalService.ConnectionRequest(oldTab,true)));
        tx.executeWithoutResult(status -> service.connection(id,host,new NavalService.ConnectionRequest(oldTab,false)));
        assertEquals("PLACEMENT",tx.execute(status -> service.get(id,peer)).get("phase"));
        assertEquals("PLACEMENT",tx.execute(status -> service.get(id,host)).get("phase"),"resume within grace keeps the original room");
        tx.executeWithoutResult(status -> service.connection(id,host,new NavalService.ConnectionRequest(newTab,true)));
        tx.executeWithoutResult(status -> service.connection(id,host,new NavalService.ConnectionRequest(oldTab,false)));
        ObjectMapper json=new ObjectMapper();
        NavalState state=json.readValue(jdbc.queryForObject("SELECT state_json FROM naval_rooms WHERE id=?",String.class,id),NavalState.class);
        assertEquals(0,state.players.get(0).disconnectedAt,"late pagehide from an old tab cannot disconnect the new tab");
        state.players.get(0).disconnectedAt=System.currentTimeMillis()-30_001;
        jdbc.update("UPDATE naval_rooms SET state_json=? WHERE id=?",json.writeValueAsString(state),id);
        var expired=tx.execute(status -> service.get(id,host));
        assertEquals("FINISHED",expired.get("phase")); assertEquals(1,expired.get("winner"));
        assertEquals("DISCONNECTED",expired.get("finishReason"));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM naval_round_results WHERE room_id=?",Integer.class,id));
    }

    private void preventRematchAcrossOtherActiveRoom(JdbcTemplate jdbc, TransactionTemplate tx, NavalService service) {
        PlayerIdentity a = guest("rematch-busy-a"), b = guest("rematch-busy-b"), c = guest("rematch-busy-c"), d = guest("rematch-busy-d");
        var old = tx.execute(status -> service.create(a,new CreateRequest("MULTIPLAYER",false)));
        String oldId = old.get("id").toString(), code = old.get("inviteCode").toString();
        tx.execute(status -> service.join(b,code));
        Action leave = new Action(); leave.type = "LEAVE";
        Action again = new Action(); again.type = "CONTINUE";
        Action claim = new Action(); claim.type = "TAKE_SEAT"; claim.seat = 1;
        tx.execute(status -> service.action(oldId,b,leave));
        var next = tx.execute(status -> service.create(a,new CreateRequest("MULTIPLAYER",false)));
        String nextId = next.get("id").toString();
        tx.execute(status -> service.spectate(c,new NavalService.SpectateRequest(code,"BOTH")));
        tx.execute(status -> service.action(oldId,c,claim));
        BusinessException exception = assertThrows(BusinessException.class,() -> tx.execute(status -> service.action(oldId,a,again)));
        assertEquals(409,exception.getCode(),"continuing must not occupy two active rooms");
        assertEquals("FINISHED",tx.execute(status -> service.get(oldId,a)).get("phase"));
        tx.execute(status -> service.action(nextId,a,leave));
        jdbc.update("DELETE FROM naval_matchmaking_lock WHERE id=1");
        assertEquals("PLACEMENT",tx.execute(status -> service.action(oldId,a,again)).get("phase"));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM naval_matchmaking_lock WHERE id=1",Integer.class),"continuing a round repairs the same mutex");

        // A previously accepted vote must also be rechecked when a spectator joins.
        tx.execute(status -> service.action(oldId,c,leave));
        tx.execute(status -> service.action(oldId,a,again));
        var latest = tx.execute(status -> service.create(a,new CreateRequest("MULTIPLAYER",false)));
        String latestId = latest.get("id").toString();
        tx.execute(status -> service.spectate(d,new NavalService.SpectateRequest(code,"BOTH")));
        assertThrows(BusinessException.class,() -> tx.execute(status -> service.action(oldId,d,claim)),"seat-claim auto-start must recheck the existing player's old consent");
        assertEquals("SPECTATOR",tx.execute(status -> service.get(oldId,d)).get("role"),"failed claim must roll back membership changes");
        tx.execute(status -> service.action(latestId,a,leave));
        var joined = tx.execute(status -> service.action(oldId,d,claim));
        assertEquals("PLACEMENT",joined.get("phase")); assertEquals(3,joined.get("round"));
    }

    @SuppressWarnings("unchecked")
    private void spectatorSeatsRematchesAndImmutableHistory(JdbcTemplate jdbc, TransactionTemplate tx, NavalService service) throws Exception {
        ObjectMapper json = new ObjectMapper();
        PlayerIdentity host = new PlayerIdentity("USER",993L,null,"U:993","主方"), peer = new PlayerIdentity("USER",994L,null,"U:994","对方");
        PlayerIdentity viewer = guest("audience-v4"), challenger = guest("audience-challenger-v4");
        Map<String,Object> created = tx.execute(status -> service.create(host,new CreateRequest("MULTIPLAYER",false)));
        String id = created.get("id").toString(), code = created.get("inviteCode").toString();
        assertEquals(2,((List<?>)created.get("players")).size());
        assertEquals(false,((Map<?,?>)((List<?>)created.get("players")).get(1)).get("occupied"));
        tx.execute(status -> service.join(peer,code));
        NavalState privateState = json.readValue(jdbc.queryForObject("SELECT state_json FROM naval_rooms WHERE id=?",String.class,id),NavalState.class);
        for (int i=0;i<2;i++) {
            privateState.players.get(i).mines.add(new NavalState.Cell(11,i));
            privateState.players.get(i).inventory.put("mine",1);
            privateState.players.get(i).aa.add(i);
            privateState.players.get(i).radar.add(new NavalState.Radar(i,i,true));
        }
        jdbc.update("UPDATE naval_rooms SET state_json=? WHERE id=?",json.writeValueAsString(privateState),id);
        var both = tx.execute(status -> service.spectate(viewer,new NavalService.SpectateRequest(code,"BOTH")));
        assertEquals("SPECTATOR",both.get("role")); assertEquals(-1,both.get("you"));
        assertNotNull(both.get("ownSpectatorId"));
        assertEquals(10,json.valueToTree(both).at("/players/0/ships").size());
        assertEquals(10,json.valueToTree(both).at("/players/1/ships").size());
        assertFalse(json.writeValueAsString(both).contains("U:993"));
        assertFalse(json.writeValueAsString(both).contains("G:audience"));
        assertEquals(true,json.valueToTree(both).at("/players/0/registered").asBoolean());
        assertFalse(service.getShareMetadata(code).containsKey("players"));
        Action mode = new Action(); mode.type = "SPECTATE_VIEW"; mode.viewMode = "PLAYER_0";
        var pov = tx.execute(status -> service.action(id,viewer,mode));
        assertEquals(0,pov.get("viewAs"));
        assertEquals(10,json.valueToTree(pov).at("/players/0/ships").size());
        assertEquals(0,json.valueToTree(pov).at("/players/1/ships").size());
        assertEquals(1,json.valueToTree(pov).at("/players/0/mines").size());
        assertEquals(1,json.valueToTree(pov).at("/players/0/radar").size());
        assertEquals(0,json.valueToTree(pov).at("/players/1/mines").size());
        assertEquals(0,json.valueToTree(pov).at("/players/1/aa").size());
        assertEquals(0,json.valueToTree(pov).at("/players/1/radar").size());
        assertEquals(0,json.valueToTree(pov).at("/players/1/inventory/mine").asInt());
        mode.viewMode = "PLAYER_1"; pov = tx.execute(status -> service.action(id,viewer,mode));
        assertEquals(1,pov.get("viewAs"));
        assertEquals(0,json.valueToTree(pov).at("/players/0/ships").size());
        assertEquals(10,json.valueToTree(pov).at("/players/1/ships").size());
        for (String type : List.of("CHAT","ATTACK","DEPLOY","READY","CONTINUE")) {
            Action forbidden = new Action(); forbidden.type = type; forbidden.text = "不能发送";
            BusinessException exception = assertThrows(BusinessException.class,() -> tx.execute(status -> service.action(id,viewer,forbidden)));
            assertEquals(403,exception.getCode());
        }
        NavalState finished = json.readValue(jdbc.queryForObject("SELECT state_json FROM naval_rooms WHERE id=?",String.class,id),NavalState.class);
        finished.phase = "FINISHED"; finished.winner = 1; finished.finishedAt = System.currentTimeMillis(); finished.finishReason = "DESTROYED";
        jdbc.update("UPDATE naval_rooms SET phase='FINISHED',state_json=? WHERE id=?",json.writeValueAsString(finished),id);
        Map<String,Object> settled = tx.execute(status -> service.get(id,host));
        assertNotNull(settled.get("lastResult"));
        assertFalse(json.writeValueAsString(settled.get("lastResult")).contains("U:993"));
        Action again = new Action(); again.type = "CONTINUE";
        var consent = tx.execute(status -> service.action(id,host,again)); assertEquals("FINISHED",consent.get("phase"));
        assertEquals(true,json.valueToTree(consent).at("/players/0/continueRequested").asBoolean());
        var round2 = tx.execute(status -> service.action(id,peer,again));
        assertEquals("PLACEMENT",round2.get("phase")); assertEquals(2,round2.get("round"));
        assertEquals(id+":2",round2.get("gameId")); assertEquals(code,round2.get("inviteCode"));
        assertEquals(300,json.valueToTree(round2).at("/players/1/coins").asInt());
        assertEquals(0,json.valueToTree(round2).at("/players/1/mines").size());
        assertEquals(0,json.valueToTree(round2).at("/players/1/radar").size());
        assertNull(round2.get("lastResult"));
        Action originalChat = new Action(); originalChat.type = "CHAT"; originalChat.text = "原玩家的消息";
        var originalChatView = tx.execute(status -> service.action(id,peer,originalChat));
        String originalSenderId = json.valueToTree(originalChatView).at("/messages/0/senderId").asText();
        assertEquals("对方",json.valueToTree(originalChatView).at("/messages/0/name").asText());
        Action leave = new Action(); leave.type = "LEAVE";
        var departed = tx.execute(status -> service.action(id,peer,leave));
        assertEquals("FINISHED",departed.get("phase")); assertEquals(0,departed.get("winner"));
        assertEquals("PLAYER",departed.get("role")); assertEquals(1,departed.get("you"));
        assertThrows(BusinessException.class,() -> tx.execute(status -> service.action(id,peer,again)));
        assertEquals(2,((List<?>)service.profile(peer).get("history")).size());
        assertEquals(2,((List<?>)service.profile(host).get("history")).size());
        // Simulate old persisted JSON without sender snapshots before claiming the seat.
        var oldPayload = (com.fasterxml.jackson.databind.node.ObjectNode)json.readTree(jdbc.queryForObject("SELECT state_json FROM naval_rooms WHERE id=?",String.class,id));
        var oldMessage = (com.fasterxml.jackson.databind.node.ObjectNode)oldPayload.at("/messages/0");
        oldMessage.remove(List.of("name","senderId"));
        jdbc.update("UPDATE naval_rooms SET state_json=? WHERE id=?",json.writeValueAsString(oldPayload),id);
        tx.execute(status -> service.spectate(challenger,new NavalService.SpectateRequest(code,"BOTH")));
        Action take = new Action(); take.type = "TAKE_SEAT"; take.seat = 1;
        var pool = Executors.newFixedThreadPool(2);
        List<Map<String,Object>> claims;
        try {
            Callable<Map<String,Object>> first = () -> { try { return tx.execute(status -> service.action(id,viewer,take)); } catch (BusinessException exception) { return null; } };
            Callable<Map<String,Object>> second = () -> { try { return tx.execute(status -> service.action(id,challenger,take)); } catch (BusinessException exception) { return null; } };
            claims = new java.util.ArrayList<>(); for (var future : pool.invokeAll(List.of(first,second))) { var result = future.get(); if (result != null) claims.add(result); }
        } finally { pool.shutdownNow(); }
        assertEquals(1,claims.size(),"exactly one spectator may atomically claim the empty seat");
        var replacement = claims.get(0);
        assertEquals("FINISHED",replacement.get("phase"));
        assertEquals("对方",json.valueToTree(replacement).at("/lastResult/players/1/name").asText(),"settlement keeps the actual previous opponent");
        assertEquals(true,json.valueToTree(replacement).at("/players/1/continueRequested").asBoolean());
        assertEquals("对方",json.valueToTree(replacement).at("/messages/0/name").asText());
        assertEquals(originalSenderId,json.valueToTree(replacement).at("/messages/0/senderId").asText());
        String replacementName = json.valueToTree(replacement).at("/players/1/name").asText();
        PlayerIdentity seated = viewer.displayName().equals(replacementName) ? viewer : challenger;
        Action newChat = new Action(); newChat.type = "CHAT"; newChat.text = "新玩家的消息";
        var afterChat = tx.execute(status -> service.action(id,seated,newChat));
        assertEquals("对方",json.valueToTree(afterChat).at("/messages/0/name").asText());
        assertEquals(replacementName,json.valueToTree(afterChat).at("/messages/1/name").asText());
        assertEquals(json.valueToTree(afterChat).at("/players/1/id").asText(),json.valueToTree(afterChat).at("/messages/1/senderId").asText());
        assertNotEquals(originalSenderId,json.valueToTree(afterChat).at("/messages/1/senderId").asText());
        var round3 = tx.execute(status -> service.action(id,host,again));
        assertEquals("PLACEMENT",round3.get("phase")); assertEquals(3,round3.get("round"));
        assertEquals(2,((List<?>)service.profile(peer).get("history")).size(),"replacing the seat must not rewrite earlier participants");
        assertThrows(BusinessException.class,() -> tx.execute(status -> service.get(id,peer)));
    }

    @Test
    @EnabledIfSystemProperty(named="naval.install.v55",matches="true")
    void installRoundResultMigrationOnExplicitLocalDevelopmentRequest() throws Exception {
        Map<?,?> config = new Yaml().load(Files.readString(Path.of("src/main/resources/application-dev.yml")));
        Map<?,?> db = (Map<?,?>)((Map<?,?>)config.get("spring")).get("datasource");
        String url = db.get("url").toString();
        assertTrue(url.startsWith("jdbc:mysql://127.0.0.1:") || url.startsWith("jdbc:mysql://localhost:"),"migration helper is restricted to the local development database");
        var ds = new UnpooledDataSource("com.mysql.cj.jdbc.Driver",url,db.get("username").toString(),db.get("password").toString());
        try (var connection = ds.getConnection()) {
            assertEquals("order_menu",connection.getCatalog());
            try (var tables = connection.getMetaData().getTables(connection.getCatalog(),null,"naval_round_results",new String[]{"TABLE"})) {
                if (tables.next()) return;
            }
            ScriptUtils.executeSqlScript(connection,new ByteArrayResource(Files.readAllBytes(Path.of("src/main/resources/db/migration/V55__naval_round_results.sql"))));
        }
    }

    @Test void militaryRoomsMatchSeparatelyPersistAndContinueWithMilitaryRules() throws Exception {
        Map<?,?> config=new Yaml().load(Files.readString(Path.of("src/main/resources/application-dev.yml")));
        Map<?,?> db=(Map<?,?>)((Map<?,?>)config.get("spring")).get("datasource");
        var ds=new UnpooledDataSource("com.mysql.cj.jdbc.Driver",db.get("url").toString(),db.get("username").toString(),db.get("password").toString());
        String prefix="qa_military_"+UUID.randomUUID().toString().replace("-", "").substring(0,12)+"_";
        JdbcTemplate jdbc=new PrefixedJdbc(ds,prefix);
        var manager=new DataSourceTransactionManager(ds); var tx=new TransactionTemplate(manager); var json=new ObjectMapper();
        NavalService service=new NavalService(jdbc,json,manager);
        try(var connection=ds.getConnection()) {
            String ddl=""; for(String migration:List.of("V53__naval_battle.sql","V54__naval_page_presence.sql","V55__naval_round_results.sql","V59__naval_medals.sql")) ddl+=Files.readString(Path.of("src/main/resources/db/migration/"+migration))+"\n";
            ScriptUtils.executeSqlScript(connection,new ByteArrayResource(replaceTables(ddl,prefix).getBytes(StandardCharsets.UTF_8)));
            try {
                var a=guest("military-a");var b=guest("classic-b");var c=guest("military-c");
                var first=tx.execute(status->service.create(a,new CreateRequest("MULTIPLAYER",true,"MILITARY")));
                var classic=tx.execute(status->service.create(b,new CreateRequest("MULTIPLAYER",true,"CLASSIC")));
                assertNotEquals(first.get("id"),classic.get("id"));
                var peer=tx.execute(status->service.create(c,new CreateRequest("MULTIPLAYER",true,"MILITARY")));
                assertEquals(first.get("id"),peer.get("id")); assertEquals("MILITARY",peer.get("ruleSet"));
                String id=first.get("id").toString();
                Action da=new Action();da.type="DEPLOY";da.ready=true;
                var ca=new NavalState.Ship("a",0,0,1,3);ca.kind="cruiser";da.ships=List.of(ca);
                Action dc=new Action();dc.type="DEPLOY";dc.ready=true;
                var cc=new NavalState.Ship("c",5,5,1,3);cc.kind="cruiser";var decoy=new NavalState.Ship("d",8,8,1,2);decoy.kind="decoy";dc.ships=List.of(cc,decoy);
                tx.execute(status->service.action(id,a,da));var deployed=tx.execute(status->service.action(id,c,dc));
                assertEquals("BATTLE",deployed.get("phase"));
                NavalState.Action presented=new NavalState.Action();presented.type="PRESENTED";presented.presentationId=((Number)deployed.get("presentationId")).longValue();tx.execute(status->service.action(id,a,presented));tx.execute(status->service.action(id,c,presented));
                NavalService restarted=new NavalService(jdbc,json,manager);
                var view=tx.execute(status->restarted.get(id,a)); int you=(int)view.get("you");
                assertEquals(265,json.valueToTree(view).at("/players/"+you+"/coins").asInt());
                assertTrue(json.valueToTree(view).at("/players/"+(1-you)+"/ships").isEmpty());
                Action attack=new Action();attack.type="ATTACK";attack.weapon="shot";
                boolean aTurn=(int)view.get("turn")==you; attack.shipId=aTurn?"a":"c";attack.x=aTurn?5:0;attack.y=aTurn?5:0;
                var result=tx.execute(status->restarted.action(id,aTurn?a:c,attack));assertEquals("FINISHED",result.get("phase"));
                Action again=new Action();again.type="CONTINUE";again.value=true;
                tx.execute(status->restarted.action(id,a,again));var next=tx.execute(status->restarted.action(id,c,again));
                assertEquals("MILITARY",next.get("ruleSet"));assertEquals("PLACEMENT",next.get("phase"));
                assertTrue(json.valueToTree(next).at("/players/0/ships").isEmpty());
                var mirrorHuman=NavalEngine.player("","");mirrorHuman.ships=NavalMilitary.randomFleet();mirrorHuman.ships.get(0).hits.add("0,0");
                var mirrorAi=NavalEngine.player("AI","");mirrorAi.ships=NavalMilitary.randomFleet();
                String mirrorId="local-"+System.currentTimeMillis()+"-military";
                var mirror=new NavalService.LocalRoomRequest(mirrorId,1,"BATTLE",System.currentTimeMillis()+30000,0,null,true,List.of(mirrorHuman,mirrorAi),List.of(),0,"MILITARY");
                tx.execute(status->service.publishLocal(a,mirror));
                var spectator=tx.execute(status->service.enter(mirrorId,guest("military-spectator"),new NavalService.EnterRequest(null)));
                assertEquals("MILITARY",spectator.get("ruleSet"));assertEquals("SPECTATOR",spectator.get("role"));
                assertEquals(1,json.valueToTree(spectator).at("/players/0/ships/0/hits").size(),"mirror validation preserves component damage");
            } finally {
                for(String table:List.of("naval_medal_profiles","naval_medal_rounds","naval_round_results","naval_page_presence","naval_profiles","naval_rooms","naval_matchmaking_lock")) jdbc.execute("DROP TABLE IF EXISTS "+prefix+table);
            }
        }
    }

    private static PlayerIdentity guest(String id) { return new PlayerIdentity("GUEST",null,1L,"G:"+id,"游客"+id); }
    private static String replaceTables(String sql,String prefix) {
        for (String table:List.of("naval_lobby_messages","naval_reviews","naval_medal_profiles","naval_medal_rounds","naval_round_results","naval_page_presence","naval_profiles","naval_rooms","naval_matchmaking_lock")) sql=sql.replaceAll("\\b"+table+"\\b",prefix+table);
        return sql;
    }

    static class PrefixedJdbc extends JdbcTemplate {
        private final String prefix;
        PrefixedJdbc(javax.sql.DataSource ds,String prefix) { super(ds); this.prefix=prefix; }
        @Override public Map<String,Object> queryForMap(String sql,Object...args){return super.queryForMap(replaceTables(sql,prefix),args);}
        @Override public List<Map<String,Object>> queryForList(String sql,Object...args){return super.queryForList(replaceTables(sql,prefix),args);}
        @Override public int update(String sql) { return super.update(replaceTables(sql,prefix)); }
        @Override public int update(String sql,Object...args) { return super.update(replaceTables(sql,prefix),args); }
        @Override public <T> List<T> query(String sql,RowMapper<T> mapper,Object...args) { return super.query(replaceTables(sql,prefix),mapper,args); }
        @Override public <T> List<T> query(String sql,RowMapper<T> mapper) { return super.query(replaceTables(sql,prefix),mapper); }
        @Override public <T> List<T> queryForList(String sql,Class<T> type,Object...args) { return super.queryForList(replaceTables(sql,prefix),type,args); }
        @Override public <T> List<T> queryForList(String sql,Class<T> type) { return super.queryForList(replaceTables(sql,prefix),type); }
        @Override public <T> T queryForObject(String sql,Class<T> type,Object...args) { return super.queryForObject(replaceTables(sql,prefix),type,args); }
        @Override public <T> T queryForObject(String sql,Class<T> type) { return super.queryForObject(replaceTables(sql,prefix),type); }
    }
}
