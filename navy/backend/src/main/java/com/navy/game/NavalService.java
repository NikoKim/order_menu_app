package com.navy.game;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.navy.common.exception.BusinessException;
import com.navy.common.exception.ErrorCode;
import com.navy.identity.PlayerIdentity;
import com.navy.game.NavalState.Action;
import com.navy.game.NavalState.Ship;
import com.navy.game.NavalState.Player;
import com.navy.game.NavalState.Spectator;
import java.security.SecureRandom;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Comparator;
import java.util.UUID;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@RequiredArgsConstructor(onConstructor_=@org.springframework.beans.factory.annotation.Autowired)
public class NavalService {
    public NavalService(JdbcTemplate jdbc,ObjectMapper json,PlatformTransactionManager transactions){this(jdbc,json,transactions,event->{});}
    private static final Logger LOG = LoggerFactory.getLogger(NavalService.class);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final PlatformTransactionManager transactions;
    private final org.springframework.context.ApplicationEventPublisher events;
    private final ShortCache cache=new ShortCache();
    private final ShortCache medalCache=new ShortCache();
    private final ShortCache roomCache=new ShortCache(64);
    private record Committed(long version,String source){}
    private final java.util.concurrent.ConcurrentHashMap<String,Long> heartbeats = new java.util.concurrent.ConcurrentHashMap<>();
    public Map<String,Object> heartbeat(String id,PlayerIdentity identity) {
        NavalState state=readRoom(id);var result=heartbeatSnapshot(state,identity);
        requestMatch(state);
        return result;
    }
    // Only the socket's already-authorized mirror subscription may use this path.
    void mirrorHeartbeat(String id,PlayerIdentity identity){heartbeats.put(id+":"+identity.key(),System.currentTimeMillis());}
    Map<String,Object> heartbeatSnapshot(NavalState state,PlayerIdentity identity){
        String id=state.id;requireViewer(state,identity);
        long now=System.currentTimeMillis();String key=id+":"+identity.key();
        int seat=activeIndex(state,identity.key());
        if(seat>=0&&!state.localSimulation&&!"FINISHED".equals(state.phase)){
            Player player=state.players.get(seat);long previous=heartbeats.getOrDefault(key,player.lastSeenAt),since=player.disconnectedAt>0?player.disconnectedAt:previous;
            boolean expired=("MULTIPLAYER".equals(state.mode)||"BATTLE".equals(state.phase))&&since>0&&now-since>=("BATTLE".equals(state.phase)?NavalReconnect.GRACE_MS:30000);
            boolean resumed=player.disconnectedAt>previous;
            boolean paused="AI".equals(state.mode)&&seat==0&&List.of("PLACEMENT","SUPPLY").contains(state.phase)&&previous>0&&now-previous>15000;
            boolean stale="BATTLE".equals(state.phase)&&previous>0&&now-previous>=NavalReconnect.STALE_MS;
            if(expired||resumed&&!player.reconnectRequired||paused||stale&&!player.reconnectRequired){
                state=new TransactionTemplate(transactions).execute(status->{NavalState current=loadForUpdate(id);NavalEngine.advance(current,now);int currentSeat=activeIndex(current,identity.key());if(currentSeat>=0&&!"FINISHED".equals(current.phase)&&!current.players.get(currentSeat).reconnectRequired){current.players.get(currentSeat).lastSeenAt=Math.max(current.players.get(currentSeat).lastSeenAt,previous);touch(current,identity,now);}save(current);return current;});
            }
            int currentSeat=activeIndex(state,identity.key());if(currentSeat>=0&&state.players.get(currentSeat).reconnectRequired)return Map.of("connected",false);
        }
        if("FINISHED".equals(state.phase)&&!state.localSimulation)return Map.of("connected",false);
        heartbeats.put(key,now);
        return state.localSimulation?Map.of("spectators",NavalEngine.view(state,0,System.currentTimeMillis()).get("spectators")):Map.of("connected",true);
    }
    private NavalState readRoom(String id){
        try{
            boolean writing=org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()&&!org.springframework.transaction.support.TransactionSynchronizationManager.isCurrentTransactionReadOnly();
            if(writing)return loadCommitted(id);
            long version=jdbc.queryForObject("SELECT version FROM naval_rooms WHERE id=?",Long.class,id);String key=id+":"+version;
            Committed cached=roomCache.get(key,30000,()->{var row=jdbc.queryForMap("SELECT version,state_json FROM naval_rooms WHERE id=?",id);return new Committed(((Number)row.get("version")).longValue(),row.get("state_json").toString());});
            if(cached.source().length()>1048576)roomCache.remove(key);
            NavalState state=parseState(cached.source());state.version=cached.version();return state;
        }catch(org.springframework.dao.EmptyResultDataAccessException missing){throw new BusinessException(404,"海战房间不存在");}
    }
    private NavalState loadCommitted(String id){var row=jdbc.queryForMap("SELECT version,state_json FROM naval_rooms WHERE id=?",id);var state=parseState(row.get("state_json").toString());state.version=((Number)row.get("version")).longValue();return state;}
    NavalState committedRoom(String id){NavalState state=readRoom(id);applyHeartbeats(state);decorate(state);return state;}
    private void decorate(NavalState state){
        java.util.Set<String> keys=new java.util.LinkedHashSet<>();state.players.forEach(p->{if(p.key!=null)keys.add(p.key);});state.spectators.forEach(p->{if(p.key!=null)keys.add(p.key);});
        Map<String,Map<String,Object>> walls=medalCache.getAll(keys,5000,missing->NavalMedals.readWalls(jdbc,json,missing));
        state.players.forEach(p->p.medals=walls.getOrDefault(p.key,Map.of()));state.spectators.forEach(p->p.medals=walls.getOrDefault(p.key,Map.of()));
        List<String> accounts=state.players.stream().filter(p->p.registered&&p.key!=null&&p.key.matches("U:[0-9]+")).map(p->p.key).distinct().toList();
        Map<String,Map<String,Object>> ranks=new LinkedHashMap<>();
        if(!accounts.isEmpty())for(var row:jdbc.queryForList("SELECT player_key,score,promotion_wins FROM naval_progression_profiles WHERE player_key IN ("+String.join(",",java.util.Collections.nCopies(accounts.size(),"?"))+")",accounts.toArray()))ranks.put(row.get("player_key").toString(),NavalProgression.summary(((Number)row.get("score")).intValue(),((Number)row.get("promotion_wins")).intValue()));
        state.players.forEach(p->p.progression=p.registered?ranks.getOrDefault(p.key,NavalProgression.summary(0,0)):null);
    }
    private void applyHeartbeats(NavalState state){
        if(state.localSimulation&&!state.players.isEmpty())state.lastSeenAt=Math.max(state.lastSeenAt,heartbeats.getOrDefault(state.id+":"+state.players.get(0).key,0L));
        for(Player p:state.players){Long seen=heartbeats.get(state.id+":"+p.key);if(!p.reconnectRequired&&seen!=null&&seen>p.lastSeenAt){p.lastSeenAt=seen;p.disconnectedAt=0;}}
        for(Spectator p:state.spectators){Long seen=heartbeats.get(state.id+":"+p.key);if(seen!=null)p.lastSeenAt=Math.max(p.lastSeenAt,seen);}
    }

    public Map<String,Object> militaryTemplate() {
        return cache.get("military-template",60000,()->{NavalMilitaryTemplate.Template template=pickMilitaryTemplate();return template==null?Map.of():Map.of("ships",template.ships(),"actions",template.actions());});
    }
    private NavalMilitaryTemplate.Template pickMilitaryTemplate() {
        List<NavalMilitaryTemplate.Template> candidates=new ArrayList<>();
        for(String source:jdbc.queryForList("SELECT result_json FROM naval_round_results WHERE JSON_UNQUOTE(JSON_EXTRACT(result_json,'$.ruleSet'))='MILITARY' ORDER BY finished_at DESC LIMIT 500",String.class)){
            NavalMilitaryTemplate.Template template=NavalMilitaryTemplate.extract(readMap(source),json);if(template!=null)candidates.add(template);
        }
        return candidates.isEmpty()?null:candidates.get(RANDOM.nextInt(candidates.size()));
    }
    private void seedMilitaryAi(NavalState state){if("AI".equals(state.mode)&&"MILITARY".equals(state.ruleSet))NavalMilitaryTemplate.install(state,pickMilitaryTemplate());}

    @Transactional public Map<String,Object> medalWall(PlayerIdentity identity,String id){
        NavalMedals medals=new NavalMedals(jdbc,json);
        Map<String,Object> out=new LinkedHashMap<>(id==null||id.isBlank()
            ?cache.get("own-wall:"+identity.key(),5000,()->medals.wall(identity.key()))
            :cache.get("public-wall:"+id.toLowerCase(java.util.Locale.ROOT),5000,()->medals.publicWall(id)));
        var own=cache.get("own-wall:"+identity.key(),5000,()->medals.wall(identity.key()));
        out.put("mine",Objects.equals(out.get("medalId"),own.get("medalId")));
        return out;
    }
    @Transactional public Map<String,Object> equipMedal(PlayerIdentity identity,String id){var result=new NavalMedals(jdbc,json).equip(identity.key(),id);cache.clear();return result;}
    public record CreateRequest(String mode, boolean autoMatch, String ruleSet,Integer wager) {
        public CreateRequest(String mode,boolean autoMatch,String ruleSet){this(mode,autoMatch,ruleSet,null);}
        public CreateRequest(String mode, boolean autoMatch) { this(mode,autoMatch,"CLASSIC",null); }
    }
    public record JoinRequest(String inviteCode,Integer wager) { public JoinRequest(String inviteCode){this(inviteCode,null);} }
    public record SpectateRequest(String inviteCode, String viewMode) { }
    public record Formation(String id, String name, List<Ship> ships) { }
    public record ProfileRequest(List<Formation> formations) { }
    public record ConnectionRequest(String pageToken, boolean connected) { }
    public record EnterRequest(String inviteCode,Integer wager) { public EnterRequest(String inviteCode){this(inviteCode,null);} }
    @Transactional(readOnly=true)
    public Map<String,Object> inviteTerms(String code,PlayerIdentity identity){return inviteTerms(code,identity,null);}
    @Transactional(readOnly=true)
    public Map<String,Object> inviteTerms(String code,PlayerIdentity identity,Integer seat){
        if(seat!=null&&(seat<0||seat>1))NavalEngine.bad("席位无效");
        NavalState state=loadCode(code,false);requireOpenRoom(state);
        int destination=seat==null?vacant(state):seat;
        boolean replacingHost="FINISHED".equals(state.phase)&&(destination==0||occupied(state)==0);
        int own=entryWager(state,identity,destination);
        return Map.of("inviteCode",state.inviteCode,"wager",own,"hostWager",state.wager,"canSkipWager",own<=10,"registered",identity.loggedIn(),"hostName",replacingHost?identity.displayName():state.players.isEmpty()?"指挥官":state.players.get(0).name,"phase",state.phase);
    }
    private int entryWager(NavalState state,PlayerIdentity identity,int seat){
        return new NavalWagers(jdbc,json).defaultAmount(identity.key());
    }
    public record LocalRoomRequest(String id, int round, String phase, long deadline, int turn, Integer winner,
            boolean allowSpectators, List<Player> players, List<NavalState.Event> events, long presentationUntil, String ruleSet,Map<String,Object> pause) {
        public LocalRoomRequest(String id,int round,String phase,long deadline,int turn,Integer winner,boolean allowSpectators,List<Player> players,List<NavalState.Event> events,long presentationUntil,String ruleSet) {this(id,round,phase,deadline,turn,winner,allowSpectators,players,events,presentationUntil,ruleSet,null);}
        public LocalRoomRequest(String id,int round,String phase,long deadline,int turn,Integer winner,boolean allowSpectators,List<Player> players,List<NavalState.Event> events,long presentationUntil) {this(id,round,phase,deadline,turn,winner,allowSpectators,players,events,presentationUntil,"CLASSIC");}
    }

    public List<Map<String,Object>> rooms() {
        long now=System.currentTimeMillis();
        var states=jdbc.query("SELECT state_json FROM naval_rooms WHERE phase IN ('WAITING','PLACEMENT','SUPPLY','BATTLE','LOCAL') ORDER BY updated_at DESC LIMIT 200",this::readState).stream().peek(this::applyHeartbeats)
            .filter(state -> !"FINISHED".equals(state.phase))
            .filter(state -> state.players.stream().anyMatch(p -> !p.left && !"AI".equals(p.key) && now-(p.disconnectedAt>0?p.disconnectedAt:p.lastSeenAt)<("BATTLE".equals(state.phase)?NavalReconnect.GRACE_MS:30_000))).toList();
        Set<String> keys=new LinkedHashSet<>();states.forEach(state->state.players.forEach(p->{if(p.key!=null)keys.add(p.key);}));
        var walls=medalCache.getAll(keys,5000,missing->NavalMedals.readWalls(jdbc,json,missing));
        states.forEach(state->state.players.forEach(p->p.medals=walls.getOrDefault(p.key,Map.of())));
        return states.stream().map(this::directoryCard).filter(Objects::nonNull).toList();
    }

    @Transactional
    public Map<String,Object> enter(String id,PlayerIdentity identity,EnterRequest request) {
        lockMatchmaking();
        NavalState state=loadForUpdate(id); NavalEngine.advance(state,System.currentTimeMillis()); requireOpenRoom(state);
        if(activeIndex(state,identity.key())>=0) return get(id,identity);
        if("MULTIPLAYER".equals(state.mode) && !state.autoMatch && (request==null || request.inviteCode()==null || !state.inviteCode.equalsIgnoreCase(request.inviteCode().trim())))
            throw new BusinessException(ErrorCode.FORBIDDEN,"此房间需要正确的邀请码");
        if("MULTIPLAYER".equals(state.mode) && "WAITING".equals(state.phase) && vacant(state)>=0) return join(identity,state.inviteCode,request==null?null:request.wager());
        return spectate(identity,new SpectateRequest(state.inviteCode,"BOTH"));
    }

    // Guest AI stays local. This disposable mirror serves spectators only and never produces server results.
    @Transactional
    public Map<String,Object> publishLocal(PlayerIdentity identity,LocalRoomRequest request) {
        if(identity.loggedIn()) throw new BusinessException(ErrorCode.FORBIDDEN,"仅游客本地人机局可发布观战镜像");
        if(request==null || request.id()==null || !request.id().matches("local-[0-9]{10,16}-[a-z0-9]{1,12}") || request.round()<1 || request.round()>10000 || request.turn()<0 || request.turn()>1 || request.phase()==null || !List.of("PLACEMENT","SUPPLY","BATTLE","FINISHED").contains(request.phase()) || request.players()==null || request.players().size()!=2 || request.events()==null || request.events().size()>1000 || request.events().stream().anyMatch(java.util.Objects::isNull) || write(request).length()>200_000) NavalEngine.bad("本地观战数据无效");
        String mirrorRules=request.ruleSet()==null?"CLASSIC":request.ruleSet();
        if(!List.of("CLASSIC","MILITARY").contains(mirrorRules)) NavalEngine.bad("本地观战规则无效");
        for(Player p:request.players()) {
            if(p==null) NavalEngine.bad("本地观战数据无效");
            if("MILITARY".equals(mirrorRules)) NavalMilitary.validate(json.convertValue(p.ships,new com.fasterxml.jackson.core.type.TypeReference<List<Ship>>() {}),false);
            else NavalEngine.validatePartialFleet(p.ships);
            if(p.shots==null || p.shots.size()>144 || p.mines==null || p.mines.size()>12 || p.aa==null || p.aa.size()>3 || p.radar==null || p.radar.size()>("MILITARY".equals(mirrorRules)?144:18) || p.inventory==null || p.reconnectCount<0 || p.reconnectCount>NavalReconnect.MAX_RECONNECTS+1) NavalEngine.bad("本地观战数据无效");
        }
        lockMatchmaking(); long now=System.currentTimeMillis();
        List<NavalState> rows=jdbc.query("SELECT state_json FROM naval_rooms WHERE id=? FOR UPDATE",this::readState,request.id());
        NavalState state;
        if(rows.isEmpty()) {
            // At most one disposable AI mirror per guest.
            var previousMirrors=jdbc.queryForList("SELECT id FROM naval_rooms WHERE host_key=? AND mode='AI' AND id<>? AND phase<>'FINISHED' FOR UPDATE",String.class,identity.key(),request.id());
            for(String previous:previousMirrors){jdbc.update("UPDATE naval_rooms SET phase='FINISHED',state_json=JSON_SET(state_json,'$.phase','FINISHED','$.allowSpectators',FALSE,'$.spectators',JSON_ARRAY(),'$.version',version+1),version=version+1 WHERE id=?",previous);events.publishEvent(new RoomChanged(previous));}
            state=new NavalState(); state.id=request.id(); state.inviteCode=newCode(); state.mode="AI"; state.rulesVersion=2; state.localSimulation=true; state.connectionTracking=true; state.presentationSync=true;
        } else {
            state=rows.get(0);
            if(!state.localSimulation || !identity.key().equals(state.players.get(0).key)) throw new BusinessException(ErrorCode.FORBIDDEN,"无权更新此观战房间");
        }
        List<Player> prior=state.players; state.players=request.players();
        for(int i=0;i<2;i++) {
            Player p=state.players.get(i); p.key=i==0?identity.key():"AI"; p.id=prior.size()>i?prior.get(i).id:UUID.randomUUID().toString();
            p.medals=new NavalMedals(jdbc,json).wall(p.key);p.name=i==0?identity.displayName():"海军 AI"; p.registered=false; p.avatarUrl=null; p.lastSeenAt=now; p.disconnectedAt=0;p.reconnectRequired=false;p.connectionToken=null;p.retiredConnectionTokens=new ArrayList<>();
            if(i==1)p.reconnectCount=0;
        }
        state.ruleSet=mirrorRules;
        state.phase=request.phase(); state.round=request.round(); state.deadline=request.deadline(); state.turn=request.turn(); state.winner=request.winner(); state.events=request.events(); state.lastSeenAt=now;
        heartbeats.put(state.id+":"+identity.key(),now);
        state.finishReason=null;
        state.allowSpectators=request.allowSpectators(); state.presentationUntil=request.presentationUntil(); state.battleStarted=List.of("BATTLE","FINISHED").contains(state.phase);
        state.localPause=localPause(state,request.pause());
        if(!state.allowSpectators) state.spectators.clear(); pruneSpectators(state,now);
        if(rows.isEmpty()) { jdbc.update("INSERT INTO naval_rooms(id,invite_code,mode,auto_match,phase,host_key,peer_key,deadline,state_json) VALUES(?,?,?,FALSE,?,?,?,?,?)",state.id,state.inviteCode,"AI","FINISHED".equals(state.phase)?"FINISHED":"LOCAL",identity.key(),"AI",state.deadline,write(state));events.publishEvent(new RoomChanged(state.id)); }
        else save(state);
        return Map.of("spectators",NavalEngine.view(state,0,now).get("spectators"));
    }

    private Map<String,Object> localPause(NavalState state,Map<String,Object> source){
        if(source==null||!Boolean.TRUE.equals(source.get("active")))return null;
        if(!"BATTLE".equals(state.phase))NavalEngine.bad("本地暂停状态无效");
        Map<String,Object> out=new LinkedHashMap<>();out.put("active",true);
        for(String field:List.of("startedAt","until","remainingMs","presentationRemainingMs")){
            Object value=source.get(field);if(!(value instanceof Number number)||number.doubleValue()!=number.longValue()||number.longValue()<0)NavalEngine.bad("本地暂停状态无效");
            out.put(field,((Number)value).longValue());
        }
        long started=((Number)out.get("startedAt")).longValue(),until=((Number)out.get("until")).longValue();
        Player human=state.players.get(0);
        if(started<=0||until<=started||until-started>NavalReconnect.GRACE_MS||((Number)out.get("remainingMs")).longValue()>60_000||((Number)out.get("presentationRemainingMs")).longValue()>15_000||human.reconnectCount>NavalReconnect.MAX_RECONNECTS)NavalEngine.bad("本地暂停状态无效");
        human.disconnectedAt=started;human.reconnectRequired=true;human.reconnectCount=Math.max(1,human.reconnectCount);
        out.put("graceMs",NavalReconnect.GRACE_MS);out.put("maxReconnects",NavalReconnect.MAX_RECONNECTS);
        out.put("players",List.of(Map.of("seat",0,"personId",human.id,"until",until,"reconnectCount",NavalReconnect.used(human),"reconnectRemaining",NavalReconnect.remaining(human))));
        return out;
    }

    @Transactional
    public Map<String,Object> create(PlayerIdentity identity, CreateRequest request) {
        if (request != null && request.ruleSet() != null && !List.of("CLASSIC","MILITARY").contains(request.ruleSet())) NavalEngine.bad("未知规则模式");
        if (request == null || !List.of("AI","MULTIPLAYER").contains(request.mode())) NavalEngine.bad("请选择单人或双人模式");
        if ("AI".equals(request.mode()) && !identity.loggedIn()) NavalEngine.bad("游客单人对战请使用本地模式");
        int wager="MULTIPLAYER".equals(request.mode())?new NavalWagers(jdbc,json).selection(identity.key(),request.wager()):0;
        if("AI".equals(request.mode())&&request.wager()!=null&&request.wager()!=0)NavalEngine.bad("人机对战不能下注");
        // One database mutex makes empty-queue create and invite joins atomic across instances.
        lockMatchmaking();
        long now = System.currentTimeMillis();
        NavalState existing = activeRoom(identity,request.mode(),null,now);
        if (existing != null) {
            touch(existing,identity,now);
            if ("WAITING".equals(existing.phase)) existing.lastSeenAt = now;
            save(existing);
            requestMatch(existing);
            return view(existing,identity,now);
        }
        if (request.autoMatch() && "MULTIPLAYER".equals(request.mode())) {
            List<NavalState> candidates = jdbc.query("SELECT state_json FROM naval_rooms FORCE INDEX (idx_naval_waiting_match) WHERE phase='WAITING' AND mode='MULTIPLAYER' AND auto_match=TRUE AND match_rule_set=? AND host_key<>? ORDER BY created_at,id LIMIT 1 FOR UPDATE",
                    this::readState,request.ruleSet()==null?"CLASSIC":request.ruleSet(),identity.key());
            for (NavalState room : candidates) {
                applyHeartbeats(room);
                if (!java.util.Objects.equals(room.ruleSet,request.ruleSet()==null?"CLASSIC":request.ruleSet())) continue;
                NavalEngine.advance(room,now);
                if (!"WAITING".equals(room.phase)) { save(room); continue; }
                if (occupied(room) != 1 || activeIndex(room,identity.key()) >= 0) continue;
                claimSeat(room,identity,vacant(room),wager,now);
                save(room); return view(room,identity,now);
            }
        }
        NavalState state = new NavalState(); state.rulesVersion = 2; state.id = UUID.randomUUID().toString(); state.inviteCode = newCode();
        state.presentationSync = true;
        state.connectionTracking = true;
        state.mode = request.mode(); state.autoMatch = request.autoMatch() && "MULTIPLAYER".equals(request.mode());
        state.ruleSet = request.ruleSet()==null ? "CLASSIC" : request.ruleSet();
        state.wager=wager;state.nextWager=wager;
        Player host=player(identity);host.selectedWager=wager;host.wager=wager;state.players.add(host); state.deadline = now + 30 * 60_000;
        if ("AI".equals(state.mode)) {
            state.players.add(NavalEngine.player("AI", "海军 AI")); beginPlacement(state,now); seedMilitaryAi(state);
        } else NavalSpectators.applyDefaults(jdbc,state);
        new NavalWagers(jdbc,json).sync(state);
        jdbc.update("INSERT INTO naval_rooms (id,invite_code,mode,auto_match,phase,host_key,peer_key,deadline,state_json,wager) VALUES (?,?,?,?,?,?,?,?,?,?)",
                state.id,state.inviteCode,state.mode,state.autoMatch,state.phase,identity.key(),state.players.size()==2 ? state.players.get(1).key : null,state.deadline,write(state),state.wager);
        events.publishEvent(new RoomChanged(state.id));
        requestMatch(state);
        return view(state,identity,now);
    }

    @Transactional
    public Map<String,Object> join(PlayerIdentity identity, String code) {
        return join(identity,code,null);
    }
    @Transactional
    public Map<String,Object> join(PlayerIdentity identity,String code,Integer wager){
        lockMatchmaking();
        NavalState state = loadCode(code,true); long now = System.currentTimeMillis();
        NavalEngine.advance(state,now);
        requireOpenRoom(state);
        if (activeIndex(state,identity.key()) >= 0) { touch(state,identity,now); save(state); return view(state,identity,now); }
        int selected=new NavalWagers(jdbc,json).selection(identity.key(),wager);
        if (!"MULTIPLAYER".equals(state.mode) || vacant(state) < 0 || !List.of("WAITING","FINISHED").contains(state.phase))
            throw new BusinessException(ErrorCode.CONFLICT,"房间已满");
        requireNoOtherRoom(identity,state.id,now);
        claimSeat(state,identity,vacant(state),selected,now); save(state);
        return view(state,identity,now);
    }

    @Transactional
    public Map<String,Object> spectate(PlayerIdentity identity, SpectateRequest request) {
        if (request == null) NavalEngine.bad("请输入邀请码");
        NavalState state = loadCode(request.inviteCode(),true); long now = System.currentTimeMillis();
        NavalEngine.advance(state,now); pruneSpectators(state,now);
        requireOpenRoom(state);
        if (activeIndex(state,identity.key()) >= 0) { touch(state,identity,now); save(state); return view(state,identity,now); }
        if (!state.allowSpectators) throw new BusinessException(ErrorCode.FORBIDDEN,"房间已关闭观战");
        if ("FINISHED".equals(state.phase) && state.winner==null) throw new BusinessException(ErrorCode.NOT_FOUND,"此房间已关闭");
        Spectator person = spectator(state,identity.key());
        if (person == null) {
            if (state.spectators.size() >= 100) throw new BusinessException(ErrorCode.CONFLICT,"观众席已满");
            person = new Spectator(); person.id = UUID.randomUUID().toString(); person.key = identity.key();
            person.name = identity.displayName(); person.registered = identity.loggedIn(); person.avatarUrl = avatar(identity);
            state.spectators.add(person);
        }
        person.viewMode = viewMode(request.viewMode()); person.lastSeenAt = now;
        save(state); return view(state,identity,now);
    }

    public Map<String,Object> get(String id, PlayerIdentity identity) {
        NavalState state=currentRoom(id,identity);
        Map<String,Object> result=view(state,identity,System.currentTimeMillis());result.put("version",state.version);return result;
    }
    public Map<String,Object> sync(String id,PlayerIdentity identity,long version){NavalState state=currentRoom(id,identity);if(id.equals(state.id)&&state.version==version)return Map.of("unchanged",true,"version",version);return view(state,identity,System.currentTimeMillis());}

    @Transactional
    public Map<String,Object> action(String id, PlayerIdentity identity, Action action) {
        return action(id,identity,action,false);
    }
    @Transactional
    public Map<String,Object> action(String id,PlayerIdentity identity,Action action,boolean compact){
        if (action == null || action.type == null) NavalEngine.bad("缺少操作类型");
        if (List.of("TAKE_SEAT","CONTINUE","LEAVE").contains(action.type)) lockMatchmaking();
        NavalState state=loadForUpdate(id);id=state.id;String hash=null;
        if(action.requestId!=null){
            try{if(action.requestId.length()!=36)throw new IllegalArgumentException();action.requestId=UUID.fromString(action.requestId).toString();}catch(Exception bad){NavalEngine.bad("操作标识无效");}
            hash=requestHash(action);
            var receipts=jdbc.queryForList("SELECT payload_hash,version,reply_json FROM naval_action_receipts WHERE room_id=? AND player_key=? AND request_id=?",id,identity.key(),action.requestId);
            if(!receipts.isEmpty()){
                var receipt=receipts.get(0);if(!hash.equals(receipt.get("payload_hash")))throw new BusinessException(409,"操作标识已用于另一项操作");
                if(receipt.get("reply_json")!=null)return readMap(receipt.get("reply_json").toString());
                return Map.of("accepted",true,"replayed",true,"id",id,"version",receipt.get("version"));
            }
        }
        Map<String,Object> reply=performAction(state,identity,action,compact);
        if(action.requestId!=null)jdbc.update("INSERT INTO naval_action_receipts(room_id,player_key,request_id,payload_hash,version,reply_json) VALUES(?,?,?,?,?,?)",id,identity.key(),action.requestId,hash,state.version,List.of("LEAVE","TAKE_SEAT","CONTINUE").contains(action.type)?write(reply):null);
        return reply;
    }
    private static final ObjectMapper REQUEST_JSON=com.fasterxml.jackson.databind.json.JsonMapper.builder().enable(com.fasterxml.jackson.databind.SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS).build();
    private String requestHash(Action action){try{Map<String,Object> fields=json.convertValue(action,new TypeReference<LinkedHashMap<String,Object>>(){});fields.remove("requestId");if(action.wager==null)fields.remove("wager");byte[] bytes=REQUEST_JSON.writeValueAsBytes(fields);if(bytes.length>200000)NavalEngine.bad("操作数据过大");return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));}catch(BusinessException e){throw e;}catch(Exception e){throw new IllegalArgumentException(e);}}
    private Map<String,Object> performAction(NavalState state,PlayerIdentity identity,Action action,boolean compact){
        String id=state.id;long now=System.currentTimeMillis();pruneSpectators(state,now);requireViewer(state,identity);
        if (!("LEAVE".equals(action.type) && !NavalEngine.battleStarted(state))) NavalEngine.advance(state,now);
        int you = activeIndex(state,identity.key());
        if(you>=0&&NavalReconnect.active(state)&&!List.of("LEAVE","CHAT","SPECTATORS").contains(action.type))NavalReconnect.requirePlaying(state);
        touch(state,identity,now);
        if(!"FINISHED".equals(state.phase))heartbeats.put(id+":"+identity.key(),now);
        if (you < 0) {
            Spectator person = spectator(state,identity.key());
            if ("LEAVE".equals(action.type)) {
                state.spectators.removeIf(item -> identity.key().equals(item.key)); save(state);
                return NavalEngine.view(state,-1,"SPECTATOR","BOTH",null,now);
            }
            if (person == null) throw new BusinessException(ErrorCode.FORBIDDEN,"你已离开玩家席位，请先进入观众席");
            if ("SPECTATE_VIEW".equals(action.type)) { person.viewMode = viewMode(action.viewMode); person.lastSeenAt = now; }
            else if("CHAT".equals(action.type)){NavalEngine.chatMessage(state,-1,person.name,person.id,action.text,now);decorateMessage(state,identity.key());}
            else if ("TAKE_SEAT".equals(action.type)) {
                requireOpenRoom(state);
                requireNoOtherRoom(identity,state.id,now);
                if (action.seat == null || action.seat < 0 || action.seat > 1 || !"MULTIPLAYER".equals(state.mode) ||
                        !List.of("WAITING","FINISHED").contains(state.phase) || (action.seat < state.players.size() && !state.players.get(action.seat).left))
                    throw new BusinessException(ErrorCode.CONFLICT,"该玩家席位不可加入");
                claimSeat(state,identity,action.seat,new NavalWagers(jdbc,json).selection(identity.key(),action.wager),now);
            } else throw new BusinessException(ErrorCode.FORBIDDEN,"观众不能操作对局");
            save(state); return view(state,identity,now);
        }
        switch (action.type) {
            case "SPECTATORS" -> NavalSpectators.change(state,you,action.value,now);
            case "DEPLOY" -> NavalEngine.deploy(state,you,action,now);
            case "LAYOUT_READY" -> NavalEngine.layoutReady(state,you,action,now);
            case "READY" -> NavalEngine.ready(state,you,now);
            case "ATTACK" -> NavalEngine.attack(state,you,action,now);
            case "PRESENTED" -> NavalEngine.presented(state,you,action.presentationId,now);
            case "CHAT" -> {NavalEngine.chat(state,you,action.text,now);decorateMessage(state,identity.key());}
            case "LEAVE" -> {NavalReconnect.leaveIntent(state,you,action.pageToken);NavalEngine.leave(state,you,now);heartbeats.remove(id+":"+identity.key());}
            case "CONTINUE" -> continueRound(state,you,action.value == null || action.value,action.wager,now);
            default -> NavalEngine.bad("未知的战局操作");
        }
        save(state); return actionReply(state,identity,action,now,compact);
    }

    private Map<String,Object> actionReply(NavalState state,PlayerIdentity identity,Action action,long now,boolean compact){
        if(compact&&List.of("ATTACK","DEPLOY","LAYOUT_READY","READY","CHAT","PRESENTED","SPECTATORS").contains(action.type))return Map.of("accepted",true,"id",state.id,"version",state.version);
        return view(state,identity,now);
    }

    private void decorateMessage(NavalState state,String key){
        int last=state.messages.size()-1;var message=state.messages.get(last);var bubble=new NavalShop(jdbc,json).bubble(key);
        state.messages.set(last,new NavalState.Message(message.id(),message.player(),message.text(),message.at(),message.name(),message.senderId(),bubble.style(),bubble.expiresAt()));
    }
    @Transactional public Map<String,Object> hideLocal(String id,PlayerIdentity identity){NavalState state=loadForUpdate(id);if(!state.localSimulation||!Objects.equals(state.players.get(0).key,identity.key()))throw new BusinessException(403,"无权修改镜像");state.allowSpectators=false;state.spectators.clear();save(state);return Map.of("spectators",List.of());}
    Map<String,Object> mirrorAudience(String id,PlayerIdentity identity){NavalState state=committedRoom(id);if(!state.localSimulation||!Objects.equals(state.players.get(0).key,identity.key()))throw new BusinessException(403,"无权读取镜像");return Map.of("id",id,"version",state.version,"spectators",state.allowSpectators?state.spectators.stream().map(p->{Map<String,Object> item=new LinkedHashMap<>();item.put("id",p.id);item.put("name",p.name);item.put("avatarUrl",p.avatarUrl);item.put("registered",p.registered);item.put("medals",p.medals);item.put("viewMode",p.viewMode);return item;}).toList():List.of());}
    Map<String,Object> directoryCard(String id){try{return directoryCard(committedRoom(id));}catch(BusinessException missing){if(missing.getCode()==404)return null;throw missing;}}
    private Map<String,Object> directoryCard(NavalState state){long now=System.currentTimeMillis();applyHeartbeats(state);if("FINISHED".equals(state.phase)||state.players.stream().noneMatch(p->!p.left&&!"AI".equals(p.key)&&now-(p.disconnectedAt>0?p.disconnectedAt:p.lastSeenAt)<("BATTLE".equals(state.phase)?NavalReconnect.GRACE_MS:30000)))return null;Map<String,Object> row=new LinkedHashMap<>();row.put("id",state.id);row.put("mode",state.mode);row.put("phase",state.phase);row.put("ruleSet",state.ruleSet);row.put("wager",state.wager);row.put("wagerPot",state.wagerPot);row.put("allowSpectators",state.allowSpectators);row.put("requiresInvite","MULTIPLAYER".equals(state.mode)&&!state.autoMatch);row.put("spectators",state.spectators.size());row.put("players",state.players.stream().filter(p->!p.left).map(p->{Map<String,Object> person=new LinkedHashMap<>();person.put("name",p.name);person.put("registered",p.registered);person.put("avatarUrl",p.registered?p.avatarUrl:null);person.put("medals",p.medals);return person;}).toList());return row;}

    @Transactional
    public Map<String,Object> connection(String id, PlayerIdentity identity, ConnectionRequest request) {
        if (request == null || request.pageToken() == null || !request.pageToken().matches("[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}")) NavalEngine.bad("页面标识无效");
        NavalState state = loadForUpdate(id); long now = System.currentTimeMillis(); requireViewer(state,identity);
        NavalEngine.advance(state,now);
        int you = activeIndex(state,identity.key());
        if (you >= 0 && !"FINISHED".equals(state.phase)) {
            Player player = state.players.get(you);
            if (request.connected()) {
                if("AI".equals(state.mode)&&List.of("PLACEMENT","SUPPLY").contains(state.phase))touch(state,identity,now);
                NavalReconnect.connect(state,you,request.pageToken(),now);
                heartbeats.put(id+":"+identity.key(),now);
            } else if (player.connectionToken != null && request.pageToken().equals(player.connectionToken)) {
                NavalEngine.leave(state,you,now);heartbeats.remove(id+":"+identity.key());
            } else NavalReconnect.cancelPending(player,request.pageToken());
        }
        save(state);
        return Map.of("phase",state.phase,"cancelled","CANCELLED".equals(state.finishReason),"pause",NavalReconnect.metadata(state));
    }

    private static void touch(NavalState state, PlayerIdentity identity, long now) {
        int you = activeIndex(state,identity.key());
        if (you >= 0 && !"FINISHED".equals(state.phase)) {
            Player person = state.players.get(you);
            if(person.reconnectRequired)return;
            long pausedAt = person.disconnectedAt > 0 ? person.disconnectedAt : person.lastSeenAt;
            if ("AI".equals(state.mode) && List.of("PLACEMENT","SUPPLY").contains(state.phase) && pausedAt > 0 && (person.disconnectedAt > 0 || now-pausedAt>=15_000)) state.deadline += Math.max(0,now-pausedAt);
            person.lastSeenAt = now; person.disconnectedAt = 0;
        }
    }

    private static void requireOpenRoom(NavalState state) {
        if ("FINISHED".equals(state.phase) && state.winner == null && occupied(state) == 0)
            throw new BusinessException(ErrorCode.NOT_FOUND,"房间已关闭，请创建新房间");
    }

    @Transactional
    public Map<String,Object> progression(PlayerIdentity identity) {
        requireLogin(identity);
        return new NavalProgression(jdbc,json).profile(identity.key());
    }

    @Transactional
    public Map<String,Object> claimTask(PlayerIdentity identity,String taskId,String day){
        requireLogin(identity);return new NavalProgression(jdbc,json).claim(identity.key(),taskId,day);
    }

    @Transactional(readOnly = true)
    public Map<String,Object> profile(PlayerIdentity identity) {
        requireLogin(identity);
        List<String> rows = jdbc.queryForList("SELECT formations_json FROM naval_profiles WHERE user_id=?",String.class,identity.userId());
        List<Formation> formations = rows.isEmpty() ? List.of() : readFormations(rows.get(0));
        Map<String,Map<String,Object>> summaries = new LinkedHashMap<>();
        for (String source : jdbc.queryForList("SELECT result_json FROM naval_round_results WHERE player0_key=? OR player1_key=? ORDER BY finished_at DESC LIMIT 50",String.class,identity.key(),identity.key())) {
            Map<String,Object> result = readMap(source), summary = historySummary(result,identity.key());
            if (summary != null) summaries.put(result.get("gameId").toString(),summary);
        }
        // Keep pre-migration finished rooms visible until their first archival save.
        for (NavalState state : jdbc.query("SELECT state_json FROM naval_rooms WHERE (host_key=? OR peer_key=?) AND phase='FINISHED' ORDER BY updated_at DESC LIMIT 50",this::readState,identity.key(),identity.key())) {
            if (state.winner == null || state.settlement != null) continue;
            Map<String,Object> result = result(state), summary = historySummary(result,identity.key());
            if (summary != null) summaries.putIfAbsent(result.get("gameId").toString(),summary);
        }
        return Map.of("formations",formations,"history",summaries.values().stream().sorted(Comparator.comparingLong(item -> -((Number)item.get("finishedAt")).longValue())).limit(50).toList());
    }

    @Transactional
    public Map<String,Object> saveProfile(PlayerIdentity identity, ProfileRequest request) {
        requireLogin(identity);
        if (request == null || request.formations() == null || request.formations().size() > 20) NavalEngine.bad("最多保存 20 个阵型");
        java.util.Set<String> ids = new java.util.HashSet<>();
        List<Formation> normalized = new ArrayList<>();
        for (Formation formation : request.formations()) {
            if (formation == null || formation.id() == null || formation.id().isBlank() || formation.id().length()>80 || !ids.add(formation.id()) || formation.name()==null || formation.name().isBlank() || formation.name().length()>40) NavalEngine.bad("阵型名称或编号无效");
            List<Ship> ships = NavalEngine.normalizeSavedFleet(formation.ships());
            ships.forEach(ship -> ship.hits = new ArrayList<>());
            normalized.add(new Formation(formation.id(),formation.name(),ships));
        }
        jdbc.update("INSERT INTO naval_profiles (user_id,formations_json) VALUES (?,?) ON DUPLICATE KEY UPDATE formations_json=VALUES(formations_json)",
                identity.userId(),write(normalized));
        return profile(identity);
    }

    @Transactional(readOnly = true)
    public Map<String,Object> getShareMetadata(String inviteCode) {
        NavalState state = loadCode(inviteCode,false);
        List<String> names = state.players.stream().filter(person -> !person.left).map(person -> person.name).toList();
        Map<String,Object> metadata = new LinkedHashMap<>();
        metadata.put("inviteCode",state.inviteCode); metadata.put("phase",state.phase); metadata.put("occupied",occupied(state));
        String rules="MILITARY".equals(state.ruleSet)?"军事模式":"经典模式";
        metadata.put("ruleSet",state.ruleSet);metadata.put("wager",state.wager); metadata.put("playerNames",names); metadata.put("title","海战棋 · "+rules+" · "+(names.isEmpty() ? "指挥官" : names.get(0))+"的房间");
        metadata.put("avatarUrls",state.players.stream().filter(person -> !person.left && person.registered && person.avatarUrl != null).map(person -> person.avatarUrl).toList());
        metadata.put("description",String.join(" / ",names)+" 邀你加入海战棋。玩家席位 "+occupied(state)+"/2，满员可进入观众席。");
        return metadata;
    }

    private void claimSeat(NavalState state, PlayerIdentity identity, int seat, int selectedWager, long now) {
        archiveRound(state);
        boolean hadActive = occupied(state) > 0;
        if(!hadActive&&"FINISHED".equals(state.phase)){
            seat=0;state.round++;state.wagerRound=state.round;state.settlement=null;
            state.allowSpectators=true;
            state.wager=selectedWager;
            state.nextWager=state.wager;
        }
        if("FINISHED".equals(state.phase)&&seat==0)state.players.forEach(p->p.continueRequested=false);
        Player previousConnection=state.players.stream().filter(p->identity.key().equals(p.key)).findFirst().orElse(null);
        // A departed identity may return in the other vacant seat, never in both.
        for (int i=0;i<state.players.size();i++) if (i != seat && identity.key().equals(state.players.get(i).key)) {
            Player empty = NavalEngine.player("VACANT:"+UUID.randomUUID(),"空席"); empty.left = true; state.players.set(i,empty);
        }
        Player person = player(identity); person.continueRequested = true;person.selectedWager=selectedWager;person.wager=selectedWager;person.nextWager=selectedWager;
        NavalReconnect.inheritRetired(person,previousConnection);
        putPlayer(state,seat,person); state.spectators.removeIf(item -> identity.key().equals(item.key));
        if ("WAITING".equals(state.phase) && occupied(state) == 2) {
            if (state.settlement == null) beginPlacement(state,now); else newRound(state,now);
        } else if ("FINISHED".equals(state.phase) && occupied(state) == 2) maybeContinue(state,now);
        else if ("FINISHED".equals(state.phase) && !hadActive) {
            state.phase = "WAITING"; state.deadline = now+30*60_000; state.lastSeenAt = now;state.battleStarted=false;state.events=new ArrayList<>();state.winner=null;state.finishedAt=0;state.finishReason=null;
            NavalSpectators.applyDefaults(jdbc,state);
        }
    }

    private void continueRound(NavalState state, int you, boolean value,Integer quotedWager, long now) {
        if (!"FINISHED".equals(state.phase)) NavalEngine.bad("本局尚未结束");
        if (value && "MULTIPLAYER".equals(state.mode)) requireNoOtherRoom(state.players.get(you).key,state.id,now);
        archiveRound(state);
        if(value&&"MULTIPLAYER".equals(state.mode))state.players.get(you).selectedWager=new NavalWagers(jdbc,json).selection(state.players.get(you).key,quotedWager);
        state.players.get(you).continueRequested = value;
        if ("AI".equals(state.mode) && state.players.size() == 2) state.players.get(1).continueRequested = true;
        maybeContinue(state,now);
    }

    private void maybeContinue(NavalState state, long now) {
        if (occupied(state) == 2 && state.players.stream().allMatch(person -> person.continueRequested)) newRound(state,now);
    }

    private void newRound(NavalState state, long now) {
        if ("MULTIPLAYER".equals(state.mode)) {
            // Prior consent must not start a second game after its owner joined another room.
            for (Player person : state.players) requireNoOtherRoom(person.key,state.id,now);
        }
        boolean recalculate="FINISHED".equals(state.phase);
        archiveRound(state);
        if(recalculate&&"MULTIPLAYER".equals(state.mode)){
            NavalWagers wagers=new NavalWagers(jdbc,json);
            for(Player person:state.players){person.selectedWager=wagers.selection(person.key,person.selectedWager);person.wager=person.selectedWager;}
            state.wager=state.players.get(0).wager;
        }
        state.nextWager=state.wager;
        state.round++;state.wagerRound=state.round; state.messages = new ArrayList<>(); state.events = new ArrayList<>();
        if(recalculate)state.allowSpectators=true;
        beginPlacement(state,now); seedMilitaryAi(state);
        state.players.forEach(person -> { person.lastSeenAt = now; person.disconnectedAt = 0; });
    }

    private void beginPlacement(NavalState state,long now) {
        NavalSpectators.applyDefaults(jdbc,state);
        NavalEngine.beginPlacement(state,now);
    }

    private NavalState activeRoom(PlayerIdentity identity, String mode, String except, long now) {
        return activeRoom(identity.key(),mode,except,now);
    }

    private NavalState activeRoom(String key, String mode, String except, long now) {
        List<NavalState> rooms = jdbc.query("SELECT state_json FROM naval_rooms WHERE mode=? AND (host_key=? OR peer_key=?) AND phase IN ('WAITING','PLACEMENT','SUPPLY','BATTLE') ORDER BY created_at DESC FOR UPDATE",this::readState,mode,key,key);
        for (NavalState room : rooms) {
            if (room.id.equals(except) || activeIndex(room,key) < 0) continue;
            NavalEngine.advance(room,now); save(room);
            if (!"FINISHED".equals(room.phase)) return room;
        }
        return null;
    }

    private void requireNoOtherRoom(PlayerIdentity identity, String except, long now) {
        requireNoOtherRoom(identity.key(),except,now);
    }

    private void requireNoOtherRoom(String key, String except, long now) {
        if (activeRoom(key,"MULTIPLAYER",except,now) != null)
            throw new BusinessException(ErrorCode.CONFLICT,"请先退出当前多人房间再加入新房间");
    }

    private NavalState loadCode(String code, boolean lock) {
        if (code == null || !code.trim().toUpperCase().matches("[A-Z2-9]{6}")) NavalEngine.bad("请输入有效的 6 位邀请码");
        List<NavalState> rows = jdbc.query("SELECT state_json FROM naval_rooms WHERE invite_code=?"+(lock ? " FOR UPDATE" : ""),this::readState,code.trim().toUpperCase());
        if (rows.isEmpty()) throw new BusinessException(ErrorCode.NOT_FOUND,"邀请码不存在");
        NavalState state=rows.get(0);
        if(state.matchedRoomId!=null)return lock?loadForUpdate(state.matchedRoomId):readRoom(state.matchedRoomId);
        return state;
    }

    private Player player(PlayerIdentity identity) {
        Player person = NavalEngine.player(identity.key(),identity.displayName());
        person.registered = identity.loggedIn(); person.avatarUrl = avatar(identity); person.lastSeenAt = System.currentTimeMillis();person.nextWager=new NavalWagers(jdbc,json).defaultAmount(identity.key());person.canSkipWager=person.nextWager<=10;return person;
    }

    private String avatar(PlayerIdentity identity) {
        if (!identity.loggedIn()) return null;
        List<String> values = jdbc.queryForList("SELECT avatar_url FROM users WHERE id=?",String.class,identity.userId());
        return values.isEmpty() ? null : values.get(0);
    }

    private static int activeIndex(NavalState state, String key) {
        int index = indexOf(state,key); return index >= 0 && !state.players.get(index).left ? index : -1;
    }

    private static int occupied(NavalState state) {
        return (int)state.players.stream().filter(person -> person.key != null && !person.left).count();
    }

    private static int vacant(NavalState state) {
        for (int seat=0;seat<2;seat++) if (seat >= state.players.size() || state.players.get(seat).left) return seat;
        return -1;
    }

    private static void putPlayer(NavalState state, int seat, Player player) {
        while (state.players.size() <= seat) { Player empty = NavalEngine.player(null,"空席"); empty.left = true; state.players.add(empty); }
        state.players.set(seat,player);
    }

    private static Spectator spectator(NavalState state, String key) {
        return state.spectators.stream().filter(person -> key.equals(person.key)).findFirst().orElse(null);
    }

    private static void pruneSpectators(NavalState state, long now) {
        state.spectators.removeIf(person -> person.lastSeenAt < now-45_000);
    }

    private static String viewMode(String mode) {
        if (mode == null) return "BOTH";
        if (!List.of("BOTH","PLAYER_0","PLAYER_1").contains(mode)) NavalEngine.bad("无效的观战视角");
        return mode;
    }

    private Map<String,Object> view(NavalState state, PlayerIdentity identity, long now) {
        decorate(state);
        int active = activeIndex(state,identity.key());
        if (active >= 0) {var result=NavalEngine.view(state,active,now);result.put("version",state.version);return result;}
        Spectator person = spectator(state,identity.key());
        var result=person!=null?NavalEngine.view(state,-1,"SPECTATOR",person.viewMode,person.id,now):NavalEngine.view(state,indexOf(state,identity.key()),now);result.put("version",state.version);return result;
    }

    @Scheduled(fixedDelay = 1000, initialDelay = 15_000)
    public void tick() {
        try {
            long now=System.currentTimeMillis();
            heartbeats.entrySet().removeIf(e->e.getValue()<now-120000);
            List<String> rooms = jdbc.queryForList("SELECT id FROM naval_rooms WHERE phase IN ('WAITING','PLACEMENT','SUPPLY','BATTLE','LOCAL') AND due_at<=? ORDER BY due_at LIMIT 100",String.class,now);
            TransactionTemplate tx = new TransactionTemplate(transactions);
            for (String id : rooms) {
                try { tx.executeWithoutResult(status -> { NavalState state = loadForUpdate(id); String before=write(state); applyHeartbeats(state); NavalEngine.advance(state,System.currentTimeMillis()); if(!before.equals(write(state)))save(state);else jdbc.update("UPDATE naval_rooms SET due_at=? WHERE id=?",nextDue(state),id); }); }
                catch (RuntimeException exception) { LOG.warn("Naval room tick failed for {}",id,exception); }
            }
        } catch (org.springframework.jdbc.BadSqlGrammarException exception) {
            // Existing deployments apply SQL migrations separately from application startup.
            LOG.debug("Naval schema is not installed yet");
        } catch (RuntimeException exception) { LOG.warn("Naval scheduled update failed",exception); }
    }

    @Scheduled(fixedDelay = 3_600_000, initialDelay = 60_000)
    public void purgeGuestSessions() {
        try {
            jdbc.update("DELETE FROM naval_round_results WHERE player0_key LIKE 'G:%' AND player1_key LIKE 'G:%' AND finished_at<?",System.currentTimeMillis()-86_400_000L);
            jdbc.update("DELETE FROM naval_rooms WHERE phase='FINISHED' AND host_key LIKE 'G:%' AND (peer_key LIKE 'G:%' OR peer_key IS NULL) AND updated_at < CURRENT_TIMESTAMP - INTERVAL 1 DAY");
            jdbc.update("DELETE FROM naval_rooms WHERE phase='FINISHED' AND host_key LIKE 'G:%' AND peer_key='AI' AND JSON_EXTRACT(state_json,'$.localSimulation')=TRUE AND updated_at < CURRENT_TIMESTAMP - INTERVAL 1 HOUR");
        } catch (RuntimeException exception) { LOG.debug("Naval guest cleanup unavailable",exception); }
    }

    private NavalState loadForUpdate(String id) {
        for(int hop=0;hop<8;hop++){
            List<NavalState> rows = jdbc.query("SELECT state_json FROM naval_rooms WHERE id=? FOR UPDATE",this::readState,id);
            if (rows.isEmpty()) throw new BusinessException(ErrorCode.NOT_FOUND,"海战房间不存在");
            NavalState state=rows.get(0);
            if(state.matchedRoomId!=null){id=state.matchedRoomId;continue;}
            applyHeartbeats(state);return state;
        }
        throw new BusinessException(409,"匹配房间跳转异常，请重新进入");
    }

    private NavalState currentRoom(String id,PlayerIdentity identity){
        for(int hop=0;hop<8;hop++){NavalState state=readRoom(id);requireViewer(state,identity);if(state.matchedRoomId==null)return state;id=state.matchedRoomId;}
        throw new BusinessException(409,"匹配房间跳转异常，请重新进入");
    }

    private void requestMatch(NavalState state){
        if(state.autoMatch&&"MULTIPLAYER".equals(state.mode)&&"WAITING".equals(state.phase)&&state.matchedRoomId==null)events.publishEvent(new MatchRequested(state.ruleSet));
    }
    public List<String> waitingMatchRules(){
        return jdbc.queryForList("SELECT DISTINCT match_rule_set FROM naval_rooms FORCE INDEX (idx_naval_waiting_match) WHERE phase='WAITING' AND auto_match=TRUE AND mode='MULTIPLAYER'",String.class);
    }
    @Transactional
    public boolean matchWaiting(String rule){
        if(!List.of("CLASSIC","MILITARY").contains(rule))return false;
        lockMatchmaking();long now=System.currentTimeMillis();
        var candidates=jdbc.query("SELECT state_json FROM naval_rooms FORCE INDEX (idx_naval_waiting_match) WHERE phase='WAITING' AND auto_match=TRUE AND mode='MULTIPLAYER' AND match_rule_set=? ORDER BY created_at,id LIMIT 32 FOR UPDATE",this::readState,rule);
        if(candidates.size()<2)return false;
        List<NavalState> ready=new ArrayList<>();
        for(NavalState state:candidates){
            applyHeartbeats(state);NavalEngine.advance(state,now);
            if(!"WAITING".equals(state.phase)){save(state);continue;}
            if(occupied(state)!=1){
                if(occupied(state)==2)beginPlacement(state,now);
                else{state.phase="FINISHED";state.finishReason="CANCELLED";state.finishedAt=now;state.deadline=0;}
                save(state);continue;
            }
            ready.add(state);
        }
        if(ready.size()<2)return true;
        NavalState chosenTarget=null,chosenSource=null;
        outer:for(int i=0;i<ready.size();i++)for(int j=i+1;j<ready.size();j++){
            NavalState possibleTarget=ready.get(i),possibleSource=ready.get(j);
            Player incoming=possibleSource.players.stream().filter(p->!p.left).findFirst().orElseThrow();
            if(new NavalWagers(jdbc,json).transfer(possibleSource,possibleTarget,incoming.key)){chosenTarget=possibleTarget;chosenSource=possibleSource;break outer;}
        }
        if(chosenTarget==null)return false;
        final NavalState target=chosenTarget,source=chosenSource;
        Player incoming=source.players.stream().filter(p->!p.left).findFirst().orElseThrow();
        source.matchedRoomId=target.id;source.phase="FINISHED";source.finishReason="MATCHED";source.winner=null;source.finishedAt=now;source.deadline=0;save(source);
        if(activeIndex(target,incoming.key)<0){
            putPlayer(target,vacant(target),json.convertValue(incoming,Player.class));
            target.allowSpectators=target.allowSpectators&&source.allowSpectators;
            target.spectatorCooldownUntil=Math.max(target.spectatorCooldownUntil,source.spectatorCooldownUntil);
            for(Spectator spectator:source.spectators)if(target.spectators.size()<100&&activeIndex(target,spectator.key)<0&&spectator(target,spectator.key)==null)target.spectators.add(spectator);
            target.spectators.removeIf(p->activeIndex(target,p.key)>=0);
            if(!target.allowSpectators)target.spectators.clear();
            if(target.settlement!=null)newRound(target,now);else beginPlacement(target,now);
            save(target);
        }
        return true;
    }

    private void archiveRound(NavalState state) {
        Map<String,Object> wager=new NavalWagers(jdbc,json).sync(state);
        if(state.localSimulation) return;
        if (!"FINISHED".equals(state.phase) || state.winner == null || state.players.size() != 2 || !NavalEngine.battleStarted(state)) return;
        String gameId = state.id+":"+state.round;
        if (state.settlement != null) {refreshNextWagers(state);return;}
        // Wallet locks precede cold medal initialization in both AI and PVP settlement.
        Map<String,Object> progression=new NavalProgression(jdbc,json).credit(state);
        Map<String,Object> result = result(state);
        result.put("wager",wager);
        result.put("progression",progression);
        refreshNextWagers(state);
        jdbc.update("INSERT IGNORE INTO naval_round_results (game_id,room_id,round_no,player0_key,player1_key,finished_at,result_json) VALUES (?,?,?,?,?,?,?)",
                gameId,state.id,state.round,state.players.get(0).key,state.players.get(1).key,state.finishedAt,write(result));
        new NavalMedals(jdbc,json).credit(result);
        NavalRankings.credit(jdbc,result);
        cache.clear();
        Map<String,Object> publicResult = new LinkedHashMap<>(result);
        @SuppressWarnings("unchecked") List<Map<String,Object>> participants = (List<Map<String,Object>>)result.get("players");
        publicResult.put("players",participants.stream().map(person -> { Map<String,Object> copy = new LinkedHashMap<>(person); copy.remove("key"); return copy; }).toList());
        state.settlement = publicResult;
    }

    private void refreshNextWagers(NavalState state){
        if(!"MULTIPLAYER".equals(state.mode))return;
        NavalWagers wagers=new NavalWagers(jdbc,json);
        for(Player player:state.players){player.nextWager=wagers.defaultAmount(player.key);player.canSkipWager=player.nextWager<=10;}
        state.nextWager=state.players.isEmpty()?0:state.players.get(0).nextWager;
    }

    private Map<String,Object> result(NavalState state) {
        Map<String,Object> result = new LinkedHashMap<>();
        result.put("gameId",state.id+":"+state.round); result.put("roomId",state.id); result.put("round",state.round);
        result.put("mode",state.mode); result.put("ruleSet",state.ruleSet); result.put("winner",state.winner); result.put("finishedAt",state.finishedAt);
        result.put("battleStarted",state.battleStarted || state.events.stream().anyMatch(event -> "start".equals(event.weapon())));
        result.put("finishReason",state.finishReason); result.put("reason",state.finishReason);
        List<Map<String,Object>> participants = new ArrayList<>();
        for (int seat=0;seat<state.players.size();seat++) {
            Player person = state.players.get(seat), target = state.players.get(1-seat);
            long hits = target.shots.stream().filter(shot -> "hit".equals(shot.result())).count();
            Map<String,Object> stats = new LinkedHashMap<>();
            stats.put("id",person.id); stats.put("key",person.key); stats.put("name",person.name); stats.put("avatarUrl",person.registered ? person.avatarUrl : null);
            stats.put("medals",new NavalMedals(jdbc,json).wall(person.key));stats.put("registered",person.registered); stats.put("left",person.left); stats.put("hits",hits);
            stats.put("reconnectCount",NavalReconnect.used(person));stats.put("reconnectRemaining",NavalReconnect.remaining(person));
            final int actor = seat;
            stats.put("attacks",state.events.stream().filter(event -> event.player() == actor && List.of("shot","fighter","bomber","torpedo","radar","submarine","depthCharge").contains(event.weapon())).count());
            stats.put("coinsSpent",300-person.coins);
            Map<String,Long> used=state.events.stream().filter(event -> event.player()==actor && NavalEngine.PRICES.containsKey(event.weapon())).collect(java.util.stream.Collectors.groupingBy(NavalState.Event::weapon,java.util.stream.Collectors.counting()));
            used.put("mine",person.shots.stream().filter(shot -> "mine".equals(shot.result())).count());
            used.put("antiAir",state.events.stream().filter(event -> event.player()!=actor && event.row()!=null).count());
            used.put("stealth",person.ships.stream().filter(ship -> ship.stealth).count());
            stats.put("itemsUsed",used);
            Map<String,Integer> remaining=new LinkedHashMap<>(person.inventory);
            remaining.put("stealth",Math.max(0,remaining.getOrDefault("stealth",0)-(int)(long)used.get("stealth")));
            stats.put("inventoryRemaining",remaining);
            Map<String,Long> purchased=new LinkedHashMap<>();
            NavalEngine.PRICES.keySet().forEach(key -> purchased.put(key,remaining.getOrDefault(key,0)+used.getOrDefault(key,0L)));
            stats.put("itemsPurchased",purchased);
            if("MILITARY".equals(state.ruleSet)){used.clear();remaining.clear();purchased.clear();}
            stats.put("coatedShips",person.ships.stream().filter(ship -> ship.stealth).count());
            stats.put("ships",person.ships); stats.put("shotsReceived",person.shots);
            stats.put("minesRemaining",person.mines);stats.put("scrapMinesRemaining",person.scrapMines); stats.put("antiAirRemaining",person.aa);
            stats.put("shots",target.shots.size()); stats.put("accuracy",target.shots.isEmpty() ? 0 : Math.round(100.0*hits/target.shots.size()));
            stats.put("shipsSunk",target.ships.stream().filter(NavalEngine::sunk).count());
            stats.put("shipsRemaining",person.ships.stream().filter(ship -> !NavalEngine.sunk(ship)).count());
            participants.add(stats);
        }
        result.put("players",participants); result.put("events",state.events); return result;
    }

    @SuppressWarnings("unchecked")
    private Map<String,Object> historySummary(Map<String,Object> result, String key) {
        if (Boolean.FALSE.equals(result.get("battleStarted"))) return null;
        List<Map<String,Object>> players = (List<Map<String,Object>>)result.get("players"); int you = -1;
        for (int i=0;i<players.size();i++) if (key.equals(players.get(i).get("key"))) you = i;
        if (you < 0) return null;
        Map<String,Object> summary = new LinkedHashMap<>(result); summary.remove("players"); summary.remove("events");
        summary.put("id",((Number)result.get("round")).intValue() == 1 ? result.get("roomId") : result.get("gameId"));
        summary.put("you",you); summary.put("won",((Number)result.get("winner")).intValue() == you);
        summary.put("opponent",players.get(1-you).get("name")); return summary;
    }

    private void save(NavalState state) {
        archiveRound(state);
        if(state.persistedJson!=null&&durableState(json,state.persistedJson).equals(durableState(json,write(state)))){
            return;
        }
        state.version++;
        jdbc.update("UPDATE naval_rooms SET phase=?,host_key=?,peer_key=?,deadline=?,state_json=?,version=?,due_at=?,wager=?,updated_at=CURRENT_TIMESTAMP WHERE id=?",state.localSimulation && !"FINISHED".equals(state.phase)?"LOCAL":state.phase,state.players.get(0).key,
                state.players.size()==2 ? state.players.get(1).key : null,state.deadline,write(state),state.version,nextDue(state),state.wager,state.id);
        events.publishEvent(new RoomChanged(state.id));
        state.persistedJson=write(state);
        requestMatch(state);
        String committed=state.persistedJson,key=state.id+":"+state.version;long version=state.version;
        Runnable publish=()->{if(committed.length()<=1048576)roomCache.put(key,new Committed(version,committed),30000);};
        if(org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive())org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(new org.springframework.transaction.support.TransactionSynchronization(){@Override public void afterCommit(){publish.run();}});else publish.run();
    }
    static com.fasterxml.jackson.databind.JsonNode durableState(ObjectMapper json,String source){
        try{
            var tree=(com.fasterxml.jackson.databind.node.ObjectNode)json.readTree(source);tree.remove(java.util.List.of("version","lastSeenAt"));
            for(String group:java.util.List.of("players","spectators"))if(tree.path(group).isArray())for(var person:tree.path(group))if(person.isObject())((com.fasterxml.jackson.databind.node.ObjectNode)person).remove("lastSeenAt");
            return tree;
        }catch(Exception e){throw new IllegalStateException("Invalid room snapshot",e);}
    }
    private long nextDue(NavalState s){
        long now=System.currentTimeMillis(),due=s.deadline>now?s.deadline:now+30000;
        if(s.localSimulation)return s.localPause!=null&&Boolean.TRUE.equals(s.localPause.get("active"))&&s.localPause.get("until") instanceof Number until?Math.max(now+1000,until.longValue()):now+30000;
        if(NavalReconnect.active(s))return s.players.stream().filter(p->!p.left&&!"AI".equals(p.key)&&p.disconnectedAt>0).mapToLong(p->p.disconnectedAt+NavalReconnect.GRACE_MS).min().orElse(now+1000);
        if(s.presentationUntil>0)due=Math.min(due,s.presentationUntil);
        if("BATTLE".equals(s.phase)&&"AI".equals(s.mode)&&s.turn==1&&s.presentationUntil==0)due=Math.min(due,Math.max(now,s.nextAiAt));
        if(s.connectionTracking)for(Player p:s.players)if(!p.left&&!"AI".equals(p.key))due=Math.min(due,Math.max(now+1000,(p.disconnectedAt>0?p.disconnectedAt:p.lastSeenAt)+("BATTLE".equals(s.phase)?p.disconnectedAt==0?NavalReconnect.STALE_MS:NavalReconnect.GRACE_MS:30_000)));
        return due;
    }

    private void lockMatchmaking() {
        // Restores a missing seed after data-only imports, taking an exclusive lock even on duplicates.
        jdbc.update("INSERT INTO naval_matchmaking_lock (id) VALUES (1) ON DUPLICATE KEY UPDATE id=1");
        jdbc.queryForObject("SELECT id FROM naval_matchmaking_lock WHERE id=1 FOR UPDATE",Integer.class);
    }

    private String newCode() {
        for (int attempt = 0; attempt < 20; attempt++) {
            StringBuilder code = new StringBuilder(); for (int i = 0; i < 6; i++) code.append(CODE_CHARS.charAt(RANDOM.nextInt(CODE_CHARS.length())));
            if (jdbc.queryForObject("SELECT COUNT(*) FROM naval_rooms WHERE invite_code=?",Integer.class,code.toString()) == 0) return code.toString();
        }
        throw new BusinessException(ErrorCode.SERVER_ERROR,"邀请码生成失败，请重试");
    }

    private void requireViewer(NavalState state, PlayerIdentity identity) {
        if(activeIndex(state,identity.key())<0 && !state.allowSpectators) throw new BusinessException(ErrorCode.FORBIDDEN,"房间已关闭观战");
        if (indexOf(state,identity.key()) < 0 && spectator(state,identity.key()) == null)
            throw new BusinessException(ErrorCode.FORBIDDEN,"你不是该海战房间的成员");
    }

    private static int indexOf(NavalState state, String key) {
        for (int i=0;i<state.players.size();i++) if (key.equals(state.players.get(i).key)) return i;
        return -1;
    }

    private NavalState readState(ResultSet rs, int index) throws SQLException {
        return parseState(rs.getString("state_json"));
    }
    private NavalState parseState(String persisted){
        try {
            NavalState state=json.readValue(persisted,NavalState.class);state.persistedJson=persisted;
            NavalEngine.normalize(state,System.currentTimeMillis());
            if (!state.connectionTracking) {
                state.connectionTracking = true;
                state.players.forEach(person -> person.lastSeenAt = System.currentTimeMillis());
            }
            if (state.round<1) state.round=1;
            if (!state.presentationSync) { state.presentationSync=true; NavalEngine.waitForPresentation(state,System.currentTimeMillis()); }
            return state;
        }
        catch (Exception exception) { throw new IllegalStateException("Invalid persisted naval room",exception); }
    }

    private List<Formation> readFormations(String source) {
        try { return json.readValue(source,new TypeReference<List<Formation>>() { }).stream().map(formation -> new Formation(formation.id(),formation.name(),NavalEngine.normalizeSavedFleet(formation.ships()))).toList(); }
        catch (Exception exception) { throw new IllegalStateException("Invalid persisted naval formations",exception); }
    }

    private Map<String,Object> readMap(String source) {
        try { return json.readValue(source,new TypeReference<Map<String,Object>>() { }); }
        catch (Exception exception) { throw new IllegalStateException("Invalid naval round result",exception); }
    }

    private String write(Object value) {
        try { return json.writeValueAsString(value); }
        catch (Exception exception) { throw new IllegalStateException("Cannot persist naval game",exception); }
    }

    private static void requireLogin(PlayerIdentity identity) {
        if (!identity.loggedIn()) throw new BusinessException(ErrorCode.UNAUTHORIZED,"游客阵型和战绩仅保存在本地");
    }
}
