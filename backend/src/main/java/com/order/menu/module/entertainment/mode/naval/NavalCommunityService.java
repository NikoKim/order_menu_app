package com.order.menu.module.entertainment.mode.naval;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.order.menu.common.exception.BusinessException;
import com.order.menu.common.exception.ErrorCode;
import com.order.menu.module.entertainment.core.service.PlayerIdentity;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor(onConstructor_=@org.springframework.beans.factory.annotation.Autowired)
public class NavalCommunityService {
    public NavalCommunityService(JdbcTemplate jdbc,ObjectMapper json){this(jdbc,json,event->{});}
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    public record TextRequest(String text,String stickerId,String roomId,String gameId,Map<String,Object> localResult) {
        public TextRequest(String text,String stickerId,String roomId) { this(text,stickerId,roomId,null,null); }
        public TextRequest(String text) { this(text,null,null); }
    }
    private static final Set<String> STICKERS=Set.of("salute","surprised","heart","surrender","sleepy","applause","challenge","reconnect");
    public record ReviewRequest(int rating,String content) {}
    private Map<String,Object> decode(String source) {
        try { return json.readValue(source,new TypeReference<LinkedHashMap<String,Object>>() {}); }
        catch (Exception e) { throw new IllegalStateException("Invalid naval archive",e); }
    }
    @SuppressWarnings("unchecked")
    private List<Map<String,Object>> players(Map<String,Object> result) { return (List<Map<String,Object>>)result.get("players"); }
    private long number(Object value) { return value instanceof Number n ? n.longValue() : 0; }
    private final ShortCache publicCache=new ShortCache(),medalCache=new ShortCache();
    private final org.springframework.context.ApplicationEventPublisher events;
    private record LobbyItem(Map<String,Object> payload,String sender){}
    public List<Map<String,Object>> messages(){return messages(null);}
    public List<Map<String,Object>> messages(PlayerIdentity viewer){return messages(viewer,0);}
    public List<Map<String,Object>> messages(PlayerIdentity viewer,long afterId){
        List<LobbyItem> shared=publicCache.get("messages:"+afterId,1500,()->publicMessages(afterId));
        return viewerMessages(shared,viewer);
    }
    private List<Map<String,Object>> viewerMessages(List<LobbyItem> shared,PlayerIdentity viewer){
        return shared.stream().map(item->{Map<String,Object> copy=new LinkedHashMap<>(item.payload());copy.put("mine",viewer!=null&&viewer.key().equals(item.sender()));return copy;}).toList();
    }
    public Map<String,Object> syncMessages(PlayerIdentity viewer,long afterId,String cardVersion){
        if(afterId<0)throw new BusinessException(400,"消息游标无效");
        var records=messages(viewer,afterId);
        List<Map<String,Object>> cards=publicCache.get("cards",3000,()->{
            var rows=jdbc.queryForList("SELECT m.id,m.room_id AS roomId,m.room_round AS roomRound,m.player_key AS senderKey,r.state_json AS roomState FROM (SELECT id,room_id,room_round,player_key FROM naval_lobby_messages ORDER BY id DESC LIMIT 100) m LEFT JOIN naval_rooms r ON r.id=m.room_id WHERE m.room_id IS NOT NULL");
            List<Map<String,Object>> result=new ArrayList<>();
            for(var row:rows)result.add(Map.of("id",row.get("id"),"room",roomCard(row,row.get("roomState"),row.get("senderKey"))));
            return result;
        });
        String revision;
        try{revision=java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(json.writeValueAsBytes(cards)));}
        catch(Exception e){throw new IllegalStateException(e);}
        long cursor=records.stream().mapToLong(row->number(row.get("id"))).max().orElse(afterId);
        return Map.of("records",records,"afterId",Math.max(cursor,afterId),"hasMore",afterId>0&&records.size()==100,"cards",revision.equals(cardVersion)?List.of():cards,"cardVersion",revision);
    }
    private List<LobbyItem> publicMessages(long afterId){
        String columns="SELECT m.id,m.name,m.text,m.created_at AS at,m.sticker_id AS stickerId,m.room_id AS roomId,m.room_round AS roomRound,m.battle_json AS battleJson,m.player_key AS senderKey,r.state_json AS roomState FROM ";
        List<Map<String,Object>> rows=afterId==0
            ?jdbc.queryForList(columns+"(SELECT * FROM naval_lobby_messages ORDER BY id DESC LIMIT 100) m LEFT JOIN naval_rooms r ON r.id=m.room_id ORDER BY m.id DESC")
            :jdbc.queryForList(columns+"(SELECT * FROM naval_lobby_messages WHERE id>? ORDER BY id ASC LIMIT 100) m LEFT JOIN naval_rooms r ON r.id=m.room_id ORDER BY m.id ASC",afterId);
        Set<String> senders=new LinkedHashSet<>();for(var row:rows)if(row.get("senderKey")!=null)senders.add(row.get("senderKey").toString());
        Map<String,Map<String,Object>> walls=medalCache.getAll(senders,10000,missing->NavalMedals.readWalls(jdbc,missing));
        List<LobbyItem> result=new ArrayList<>();
        for(var original:rows){
            Map<String,Object> row=new LinkedHashMap<>(original);Object source=row.remove("roomState"),sender=row.remove("senderKey");
            row.put("medals",walls.getOrDefault(Objects.toString(sender,""),Map.of("unlocked",List.of(),"equipped","")));
            Object battle=row.remove("battleJson");
            if(battle!=null){var shared=decode(battle.toString());shared.put("shared",true);row.put("battle",shared);}
            if(row.get("roomId")!=null)row.put("room",roomCard(row,source,sender));
            result.add(new LobbyItem(row,Objects.toString(sender,null)));
        }
        if(afterId==0)Collections.reverse(result);return result;
    }
    private Map<String,Object> roomCard(Map<String,Object> row,Object source,Object sender){
            Map<String,Object> card=new LinkedHashMap<>(); card.put("status","CLOSED");
            if(source!=null) {
                Map<String,Object> state=decode(source.toString()); String phase=String.valueOf(state.get("phase"));
                List<Map<String,Object>> people=players(state);
                // The original sharing player must still occupy a seat, even after a room is reused.
                boolean anyPresent=people.stream().anyMatch(person -> !Boolean.TRUE.equals(person.get("left")) && Objects.equals(person.get("key"),sender));
                String status="CLOSED";
                if(number(state.get("round"))!=number(row.get("roomRound"))) status="FINISHED";
                else if("FINISHED".equals(phase)) status="CANCELLED".equals(state.get("finishReason")) || "EXPIRED".equals(state.get("finishReason")) || !anyPresent && state.get("winner")==null ? "CLOSED" : "FINISHED";
                else if(anyPresent && "WAITING".equals(phase) && number(state.get("deadline"))>System.currentTimeMillis()) status="WAITING";
                else if(anyPresent && List.of("PLACEMENT","SUPPLY","BATTLE").contains(phase)) status="STARTED";
                boolean requiresInvite="MULTIPLAYER".equals(state.get("mode")) && !Boolean.TRUE.equals(state.get("autoMatch"));
                boolean allowSpectators=!Boolean.FALSE.equals(state.get("allowSpectators"));
                card.put("status",status); card.put("roomId",row.get("roomId")); card.put("ruleSet",state.getOrDefault("ruleSet","CLASSIC"));
                card.put("requiresInvite",requiresInvite); card.put("allowSpectators",allowSpectators);
                card.put("names",people.stream().filter(person -> !Boolean.TRUE.equals(person.get("left"))).map(person -> person.get("name")).toList());
            }

        return card;
    }
    @Transactional
    public List<Map<String,Object>> send(PlayerIdentity identity,TextRequest request) {return send(identity,request,0);}
    @Transactional
    public List<Map<String,Object>> send(PlayerIdentity identity,TextRequest request,long afterId) {
        String text=request==null || request.text()==null ? "" : request.text().trim();
        String sticker=request==null ? null : request.stickerId(),roomId=request==null ? null : request.roomId();
        String battleJson=null;
        boolean battle=request!=null && (request.gameId()!=null || request.localResult()!=null);
        if(battle) {
            if(sticker!=null || roomId!=null || !text.isEmpty()) NavalEngine.bad("战绩分享不能携带其他消息");
            battleJson=sharedResult(identity,request);
        }
        Integer roomRound=null;
        if(sticker!=null) {
            if(!STICKERS.contains(sticker) || !text.isEmpty() || roomId!=null) NavalEngine.bad("表情包消息无效");
        } else if(roomId!=null) {
            if(!text.isEmpty()) NavalEngine.bad("房间分享不能携带文本");
            List<String> rooms=jdbc.queryForList("SELECT state_json FROM naval_rooms WHERE id=? FOR UPDATE",String.class,roomId);
            if(rooms.isEmpty()) NavalEngine.bad("房间不存在");
            Map<String,Object> state=decode(rooms.get(0));
            if(!"MULTIPLAYER".equals(state.get("mode")) || !"WAITING".equals(state.get("phase")) || number(state.get("deadline"))<=System.currentTimeMillis() || players(state).stream().noneMatch(person -> identity.key().equals(person.get("key")) && !Boolean.TRUE.equals(person.get("left")))) NavalEngine.bad("只能分享自己正在等待的双人房间");
            roomRound=(int)number(state.get("round"));
            if(number(state.get("lobbySharedRound"))==roomRound || Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM naval_lobby_messages WHERE room_id=? AND room_round=?)",Boolean.class,roomId,roomRound))) NavalEngine.bad("本局房间已经分享到大厅");
            state.put("lobbySharedRound",roomRound);
            try { jdbc.update("UPDATE naval_rooms SET state_json=JSON_SET(?,\'$.version\',version+1),version=version+1 WHERE id=?",json.writeValueAsString(state),roomId);events.publishEvent(new RoomChanged(roomId)); }
            catch(com.fasterxml.jackson.core.JsonProcessingException e) { throw new IllegalStateException("Cannot save lobby share",e); }
        } else if(!battle && (text.isEmpty() || text.length()>300)) NavalEngine.bad("消息应为 1 至 300 字");
        if(Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM naval_rooms WHERE (host_key=? OR peer_key=?) AND phase IN ('PLACEMENT','SUPPLY','BATTLE'))",Boolean.class,identity.key(),identity.key()))) NavalEngine.bad("对局进行中无法在大厅发送消息");
        // Serialize per-identity posting across backend instances.
        jdbc.queryForList("SELECT id FROM entertainment_guests WHERE id=? FOR UPDATE",identity.guestId()==null ? -1 : identity.guestId());
        if(identity.loggedIn()) jdbc.queryForList("SELECT id FROM users WHERE id=? FOR UPDATE",identity.userId());
        Long latest=jdbc.queryForObject("SELECT MAX(created_at) FROM naval_lobby_messages WHERE player_key=?",Long.class,identity.key());
        long now=System.currentTimeMillis();
        if(latest!=null && now-latest<1500) NavalEngine.bad("消息发送过快，请稍后再试");
        jdbc.update("INSERT INTO naval_lobby_messages(player_key,name,text,created_at,sticker_id,room_id,room_round,battle_json) VALUES(?,?,?,?,?,?,?,?)",identity.key(),identity.displayName(),text,now,sticker,roomId,roomRound,battleJson);
        if(org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive()){
            org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(new org.springframework.transaction.support.TransactionSynchronization(){@Override public void afterCommit(){publicCache.clear();events.publishEvent(new LobbyChanged());}});
        }else{publicCache.clear();events.publishEvent(new LobbyChanged());}
        return viewerMessages(publicMessages(afterId),identity);
    }
    private String sharedResult(PlayerIdentity identity,TextRequest request) {
        Map<String,Object> result;
        if(request.localResult()!=null) {
            if(identity.loggedIn() || request.gameId()!=null) NavalEngine.bad("登录用户请分享服务器战绩");
            result=new LinkedHashMap<>(request.localResult());
            if(!"AI".equals(result.get("mode")) || !Boolean.TRUE.equals(result.get("battleStarted")) || !(result.get("winner") instanceof Number winner) || winner.intValue()<0 || winner.intValue()>1) NavalEngine.bad("只能分享已结束的本地人机战绩");
            result.put("you",0); result.put("local",true);
        } else {
            result=detail(request.gameId(),identity);
            if(number(result.get("you"))<0) throw new BusinessException(ErrorCode.FORBIDDEN,"只能分享自己的战绩");
            result.put("local",false);
        }
        try {
            if(!List.of("CLASSIC","MILITARY").contains(result.getOrDefault("ruleSet","CLASSIC"))) NavalEngine.bad("战绩规则模式无效");
            if(json.writeValueAsString(result).length()>200_000) NavalEngine.bad("战绩数据过大");
            List<Map<String,Object>> people=players(result);
            if(people==null || people.size()!=2) NavalEngine.bad("战绩玩家信息无效");
            Set<String> personFields=Set.of("name","avatarUrl","registered","coinsSpent","itemsUsed","itemsPurchased","inventoryRemaining","ships","shotsReceived","minesRemaining","scrapMinesRemaining","antiAirRemaining","hits","shots","attacks");
            List<Map<String,Object>> safePeople=new ArrayList<>();
            for(Map<String,Object> person:people) {
                Map<String,Object> safe=new LinkedHashMap<>(person); safe.keySet().retainAll(personFields);
                if(request.localResult()!=null) {
                    List<NavalState.Ship> ships=json.convertValue(safe.get("ships"),new TypeReference<List<NavalState.Ship>>() {});
                    if("MILITARY".equals(result.get("ruleSet"))) NavalMilitary.validate(json.convertValue(safe.get("ships"),new TypeReference<List<NavalState.Ship>>() {}),true);
                    else NavalEngine.validateFleet(ships);
                    for(NavalState.Ship ship:ships) {
                        if(ship.hits==null || ship.hits.size()>ship.w*ship.h || new HashSet<>(ship.hits).size()!=ship.hits.size()) NavalEngine.bad("舰船受损数据无效");
                        for(String hit:ship.hits) {
                            if(hit==null || !hit.matches("(?:[0-9]|1[01]),(?:[0-9]|1[01])")) NavalEngine.bad("舰船受损坐标无效");
                            String[] xy=hit.split(","); int x=Integer.parseInt(xy[0]),y=Integer.parseInt(xy[1]);
                            if(x<ship.x || x>=ship.x+ship.w || y<ship.y || y>=ship.y+ship.h) NavalEngine.bad("舰船受损坐标无效");
                        }
                    }
                    for(String field:List.of("itemsUsed","itemsPurchased","inventoryRemaining")) {
                        Object value=safe.get(field);
                        if(value!=null && (!(value instanceof Map<?,?> items) || items.size()>8 || items.values().stream().anyMatch(count->!(count instanceof Number n) || n.intValue()<0 || n.intValue()>144))) NavalEngine.bad("道具数据无效");
                    }
                    for(String field:List.of("shotsReceived","minesRemaining","scrapMinesRemaining")) checkPublicCells(safe.get(field));
                    Object aa=safe.get("antiAirRemaining");
                    if(aa!=null && (!(aa instanceof List<?> rows) || rows.size()>12 || rows.stream().anyMatch(row->!(row instanceof Number n) || n.intValue()<0 || n.intValue()>11))) NavalEngine.bad("防空行数据无效");
                    safe.put("ships",ships); safe.remove("avatarUrl"); safe.put("registered",false);
                }
                safePeople.add(safe);
            }
            if(request.localResult()!=null) {
                safePeople.get(0).put("medals",new NavalMedals(jdbc,json).wall(identity.key()));safePeople.get(1).put("medals",Map.of("unlocked",List.of(),"equipped",""));safePeople.get(0).put("name",identity.displayName()); safePeople.get(1).put("name","AI 舰队");
                Object value=result.get("events");
                if(!(value instanceof List<?>)) NavalEngine.bad("攻击记录无效");
                List<?> events=(List<?>)value;
                if(events.size()>1000) NavalEngine.bad("攻击记录过多");
                List<Map<String,Object>> safeEvents=new ArrayList<>();
                for(Object item:events) {
                    if(!(item instanceof Map<?,?>)) NavalEngine.bad("攻击记录无效");
                    Map<String,Object> entry=new LinkedHashMap<>((Map<String,Object>)item);
                    entry.keySet().retainAll(Set.of("id","player","weapon","cells","backfire","text","x","y","row","targets","route","torpedoes","destroyed","radar","shipId","plane","origin","support"));
                    if(!(entry.get("weapon") instanceof String) || !(entry.get("player") instanceof Number actor) || actor.intValue()<-1 || actor.intValue()>1) NavalEngine.bad("攻击记录无效");
                    entry.put("id",safeEvents.size()+1);
                    if(entry.get("text")!=null && (!(entry.get("text") instanceof String text) || text.length()>300)) NavalEngine.bad("攻击说明无效");
                    for(String field:List.of("cells","backfire","targets","radar")) checkPublicCells(entry.get(field));
                    checkPublicCells(entry.get("route"),-2,"MILITARY".equals(result.get("ruleSet"))?12:11);
                    Object paths=entry.get("torpedoes");
                    if(paths!=null) { if(!(paths instanceof List<?>) || ((List<?>)paths).size()>2) NavalEngine.bad("鱼雷轨迹无效"); for(Object path:(List<?>)paths) checkPublicCells(path); }
                    for(String field:List.of("x","y","row")) if(entry.get(field)!=null && (!(entry.get(field) instanceof Number coordinate) || coordinate.doubleValue()!=coordinate.intValue() || coordinate.intValue()<0 || coordinate.intValue()>11)) NavalEngine.bad("攻击位置无效");
                    if(entry.get("support") instanceof Map<?,?> raw){
                        Map<String,Object> support=new LinkedHashMap<>();
                        if(!List.of("tow","repair","scrapMine","skip").contains(raw.get("mode")))NavalEngine.bad("支援记录无效");
                        support.put("mode",raw.get("mode"));
                        if(raw.get("targetShipId") instanceof String shipId&&shipId.length()<=80)support.put("targetShipId",shipId);
                        for(String pose:List.of("before","after"))if(raw.get(pose) instanceof Map<?,?> data){
                            Map<String,Object> safePose=new LinkedHashMap<>();
                            for(String field:List.of("id","kind","x","y","w","h","rotation","hits","lostPlanes","minesUsed"))if(data.containsKey(field))safePose.put(field,data.get(field));
                            NavalState.Ship ship=json.convertValue(safePose,NavalState.Ship.class);
                            if(ship.x<0||ship.y<0||ship.w<1||ship.h<1||ship.w>4||ship.h>4||ship.x+ship.w>12||ship.y+ship.h>12||ship.hits==null||ship.hits.size()>16)NavalEngine.bad("支援舰体记录无效");
                            support.put(pose,safePose);
                        }
                        checkPublicCells(raw.get("cells"));if(raw.get("cells")!=null)support.put("cells",raw.get("cells"));
                        entry.put("support",support);
                    }
                    safeEvents.add(entry);
                }
                result.put("events",safeEvents);
            }
            result.put("players",safePeople);
            result.keySet().retainAll(Set.of("gameId","mode","ruleSet","winner","finishedAt","battleStarted","finishReason","events","players","you","local"));
            return json.writeValueAsString(result);
        } catch(com.fasterxml.jackson.core.JsonProcessingException | IllegalArgumentException | ClassCastException e) { NavalEngine.bad("战绩数据无效"); return null; }
    }
    private void checkPublicCells(Object value) {
        checkPublicCells(value,0);
    }
    private void checkPublicCells(Object value,int minX) {
        checkPublicCells(value,minX,11);
    }
    private void checkPublicCells(Object value,int minX,int maxX) {
        if(value==null) return;
        if(!(value instanceof List<?>) || ((List<?>)value).size()>144) NavalEngine.bad("海域数据无效");
        for(Object cell:(List<?>)value) {
            if(!(cell instanceof Map<?,?>)) NavalEngine.bad("海域数据无效");
            Map<?,?> point=(Map<?,?>)cell;
            for(String axis:List.of("x","y")) if(!(point.get(axis) instanceof Number n) || n.doubleValue()!=n.intValue() || n.intValue()<(axis.equals("x") ? minX : 0) || n.intValue()>(axis.equals("x") ? maxX : 11)) NavalEngine.bad("海域坐标无效");
            if(point.get("result")!=null && !List.of("hit","miss","mine","detected").contains(point.get("result"))) NavalEngine.bad("攻击结果无效");
            if(point.get("uncertain")!=null && !(point.get("uncertain") instanceof Boolean)) NavalEngine.bad("命中标记无效");
            if(point.get("mineKind")!=null && (!"scrap".equals(point.get("mineKind")) || !"mine".equals(point.get("result")))) NavalEngine.bad("水雷类型无效");
        }
    }
    public Map<String,Object> detail(String gameId,PlayerIdentity identity) {
        List<String> rows=jdbc.queryForList("SELECT result_json FROM naval_round_results WHERE game_id=?",String.class,gameId);
        if(rows.isEmpty()) throw new BusinessException(ErrorCode.NOT_FOUND,"战绩不存在或已过期");
        Map<String,Object> result=decode(rows.get(0)); int you=-1;
        List<Map<String,Object>> people=players(result);
        for(int i=0;i<people.size();i++) { if(identity.key().equals(people.get(i).get("key"))) you=i; people.get(i).put("medals",new NavalMedals(jdbc,json).wall(String.valueOf(people.get(i).get("key"))));people.get(i).remove("key"); }
        result.put("you",you); return result;
    }
    public List<Map<String,Object>> leaderboard(String mode,String metric) { return leaderboard(mode,metric,"CLASSIC"); }
    public List<Map<String,Object>> leaderboard(String mode,String metric,String ruleSet){return leaderboard(mode,metric,ruleSet,1,100);}
    public List<Map<String,Object>> leaderboard(String mode,String metric,String ruleSet,int page,int size){return NavalRankings.page(jdbc,mode,metric,ruleSet,page,size);}
    public Map<String,Object> leaderboardPage(String mode,String metric,String ruleSet,int page,int size){
        var records=leaderboard(mode,metric,ruleSet,page,size);
        long total=jdbc.queryForObject("SELECT COUNT(*) FROM naval_ranking_totals WHERE mode=? AND rule_set=?"+("economy".equals(metric)?" AND best_economy IS NOT NULL":""),Long.class,mode,ruleSet);
        return Map.of("records",records,"total",total,"page",page,"pageSize",size);
    }
    void invalidateCards(){publicCache.remove("cards");}
    private boolean canReview(PlayerIdentity identity) {
        if(!identity.loggedIn()) return false;
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM naval_round_results WHERE (player0_key=? OR player1_key=?) AND (JSON_EXTRACT(result_json,'$.battleStarted')=TRUE OR JSON_EXTRACT(result_json,'$.players[0].attacks')>0 OR JSON_EXTRACT(result_json,'$.players[1].attacks')>0)) OR EXISTS(SELECT 1 FROM naval_rooms WHERE (host_key=? OR peer_key=?) AND phase='BATTLE')",Boolean.class,identity.key(),identity.key(),identity.key(),identity.key()));
    }
    public Map<String,Object> reviews(PlayerIdentity identity,int page) {
        int offset=(Math.max(1,Math.min(10000,page))-1)*5;
        List<Map<String,Object>> rows=jdbc.queryForList("SELECT r.user_id AS id,r.user_id,r.rating,r.content,r.updated_at AS updatedAt,u.nickname,u.avatar_url FROM naval_reviews r JOIN users u ON u.id=r.user_id ORDER BY r.updated_at DESC LIMIT 5 OFFSET ?",offset);
        rows.forEach(row -> { Map<String,Object> author=new LinkedHashMap<>(); author.put("medals",new NavalMedals(jdbc,json).wall("U:"+row.get("user_id")));author.put("nickname",row.remove("nickname")); author.put("avatarUrl",row.remove("avatar_url")); row.put("author",author); row.put("mine",Objects.equals(number(row.remove("user_id")),identity.userId())); });
        Map<String,Object> result=new LinkedHashMap<>(); result.put("records",rows);
        result.put("total",jdbc.queryForObject("SELECT COUNT(*) FROM naval_reviews",Long.class)); result.put("averageRating",jdbc.queryForObject("SELECT COALESCE(AVG(rating),0) FROM naval_reviews",Double.class));
        result.put("canReview",canReview(identity));
        List<Map<String,Object>> mine=identity.loggedIn() ? jdbc.queryForList("SELECT rating,content FROM naval_reviews WHERE user_id=?",identity.userId()) : List.of();
        result.put("myReview",mine.isEmpty() ? null : mine.get(0)); return result;
    }
    public Map<String,Object> review(PlayerIdentity identity,ReviewRequest request) {
        if(!canReview(identity)) throw new BusinessException(ErrorCode.FORBIDDEN,"登录并游玩海战棋后才可点评");
        if(request==null || request.rating()<1 || request.rating()>5 || request.content()==null || request.content().isBlank() || request.content().length()>1000) NavalEngine.bad("请填写 1 至 5 星评分和 1 至 1000 字点评");
        jdbc.update("INSERT INTO naval_reviews(user_id,rating,content,updated_at) VALUES(?,?,?,?) ON DUPLICATE KEY UPDATE rating=VALUES(rating),content=VALUES(content),updated_at=VALUES(updated_at)",identity.userId(),request.rating(),request.content().trim(),System.currentTimeMillis());
        return Map.of("rating",request.rating(),"content",request.content().trim());
    }
    public void deleteReview(PlayerIdentity identity) {
        if(!identity.loggedIn()) throw new BusinessException(ErrorCode.FORBIDDEN,"请先登录");
        jdbc.update("DELETE FROM naval_reviews WHERE user_id=?",identity.userId());
    }
}
