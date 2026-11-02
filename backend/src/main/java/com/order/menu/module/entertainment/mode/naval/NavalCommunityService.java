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
@RequiredArgsConstructor
public class NavalCommunityService {
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
    public List<Map<String,Object>> messages() {
        return messages(null);
    }
    public List<Map<String,Object>> messages(PlayerIdentity viewer) {
        List<Map<String,Object>> rows=jdbc.queryForList("SELECT m.id,m.name,m.text,m.created_at AS at,m.sticker_id AS stickerId,m.room_id AS roomId,m.room_round AS roomRound,m.battle_json AS battleJson,m.player_key AS senderKey,r.state_json AS roomState FROM (SELECT * FROM naval_lobby_messages ORDER BY id DESC LIMIT 100) m LEFT JOIN naval_rooms r ON r.id=m.room_id ORDER BY m.id DESC");
        for(Map<String,Object> row:rows) {
            Object source=row.remove("roomState"),sender=row.remove("senderKey");
            row.put("mine",viewer!=null && viewer.key().equals(sender));
            row.put("medals",new NavalMedals(jdbc,json).wall(sender==null?null:sender.toString()));
            Object battle=row.remove("battleJson");
            if (battle!=null) { Map<String,Object> shared=decode(battle.toString()); shared.put("shared",true); row.put("battle",shared); }
            if(row.get("roomId")==null) continue;
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
            row.put("room",card);
        }
        Collections.reverse(rows); return rows;
    }
    @Transactional
    public List<Map<String,Object>> send(PlayerIdentity identity,TextRequest request) {
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
            try { jdbc.update("UPDATE naval_rooms SET state_json=? WHERE id=?",json.writeValueAsString(state),roomId); }
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
        return messages(identity);
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
            Set<String> personFields=Set.of("name","avatarUrl","registered","coinsSpent","itemsUsed","itemsPurchased","inventoryRemaining","ships","shotsReceived","minesRemaining","antiAirRemaining","hits","shots","attacks");
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
                    for(String field:List.of("shotsReceived","minesRemaining")) checkPublicCells(safe.get(field));
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
                    entry.keySet().retainAll(Set.of("id","player","weapon","cells","backfire","text","x","y","row","targets","route","torpedoes","destroyed","radar","shipId","plane","origin"));
                    if(!(entry.get("weapon") instanceof String) || !(entry.get("player") instanceof Number actor) || actor.intValue()<-1 || actor.intValue()>1) NavalEngine.bad("攻击记录无效");
                    entry.put("id",safeEvents.size()+1);
                    if(entry.get("text")!=null && (!(entry.get("text") instanceof String text) || text.length()>300)) NavalEngine.bad("攻击说明无效");
                    for(String field:List.of("cells","backfire","targets","radar")) checkPublicCells(entry.get(field));
                    checkPublicCells(entry.get("route"),-2,"MILITARY".equals(result.get("ruleSet"))?12:11);
                    Object paths=entry.get("torpedoes");
                    if(paths!=null) { if(!(paths instanceof List<?>) || ((List<?>)paths).size()>2) NavalEngine.bad("鱼雷轨迹无效"); for(Object path:(List<?>)paths) checkPublicCells(path); }
                    for(String field:List.of("x","y","row")) if(entry.get(field)!=null && (!(entry.get(field) instanceof Number coordinate) || coordinate.doubleValue()!=coordinate.intValue() || coordinate.intValue()<0 || coordinate.intValue()>11)) NavalEngine.bad("攻击位置无效");
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
    public List<Map<String,Object>> leaderboard(String mode,String metric,String ruleSet) {
        if(!List.of("CLASSIC","MILITARY").contains(ruleSet)) NavalEngine.bad("排行榜规则无效");
        if(!List.of("AI","MULTIPLAYER").contains(mode) || !List.of("wins","rate","economy").contains(metric)) NavalEngine.bad("排行榜类型无效");
        Map<String,Map<String,Object>> users=new HashMap<>(), economy=new HashMap<>();
        jdbc.query("SELECT result_json FROM naval_round_results",(org.springframework.jdbc.core.RowCallbackHandler) row -> {
            Map<String,Object> result=decode(row.getString(1)); if(!ruleSet.equals(result.getOrDefault("ruleSet","CLASSIC"))) return; if(!mode.equals(result.get("mode")) || "AI".equals(mode) && Boolean.FALSE.equals(result.get("battleStarted"))) return;
            List<Map<String,Object>> people=players(result);
            for(int i=0;i<people.size();i++) {
                Map<String,Object> person=people.get(i),other=people.get(1-i);
                String key=String.valueOf(person.get("key")); if(!key.startsWith("U:")) continue;
                Map<String,Object> stats=users.computeIfAbsent(key,k -> { Map<String,Object> value=new LinkedHashMap<>(); value.put("medals",new NavalMedals(jdbc,json).wall(key));value.put("name",person.get("name")); value.put("avatarUrl",person.get("avatarUrl")); value.put("games",0L); value.put("wins",0L); return value; });
                stats.put("games",number(stats.get("games"))+1);
                if(number(result.get("winner"))==i) stats.put("wins",number(stats.get("wins"))+1);
                stats.put("rate",100.0*number(stats.get("wins"))/number(stats.get("games")));
                if(number(result.get("winner"))==i && person.containsKey("coinsSpent") && other.containsKey("coinsSpent")) {
                    Map<String,Object> entry=new LinkedHashMap<>(stats);
                    entry.put("difference",number(other.get("coinsSpent"))-number(person.get("coinsSpent")));
                    entry.put("spent",person.get("coinsSpent")); entry.put("opponentSpent",other.get("coinsSpent"));
                    entry.put("gameId",result.get("gameId")); entry.put("finishedAt",result.get("finishedAt"));
                    Map<String,Object> best=economy.get(key);
                    if(best==null || number(entry.get("difference"))>number(best.get("difference")) || number(entry.get("difference"))==number(best.get("difference")) && number(entry.get("finishedAt"))>number(best.get("finishedAt"))) economy.put(key,entry);
                }
            }
        });
        List<Map<String,Object>> result=new ArrayList<>(metric.equals("economy") ? economy.values() : users.values());
        String field=metric.equals("economy") ? "difference" : metric;
        result.sort(Comparator.<Map<String,Object>>comparingDouble(item -> -((Number)item.get(field)).doubleValue()).thenComparing(item -> String.valueOf(item.get("name"))));
        return result.stream().limit(100).toList();
    }
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
