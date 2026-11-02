package com.order.menu.module.entertainment.mode.naval;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.order.menu.common.exception.BusinessException;
import com.order.menu.common.exception.ErrorCode;
import com.order.menu.module.entertainment.core.service.PlayerIdentity;
import com.order.menu.module.entertainment.mode.naval.NavalState.Action;
import com.order.menu.module.entertainment.mode.naval.NavalState.Ship;
import com.order.menu.module.entertainment.mode.naval.NavalState.Player;
import com.order.menu.module.entertainment.mode.naval.NavalState.Spectator;
import java.security.SecureRandom;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
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
@RequiredArgsConstructor
public class NavalService {
    private static final Logger LOG = LoggerFactory.getLogger(NavalService.class);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final PlatformTransactionManager transactions;

    public Map<String,Object> militaryTemplate() {
        NavalMilitaryTemplate.Template template=pickMilitaryTemplate();
        return template==null?Map.of():Map.of("ships",template.ships(),"actions",template.actions());
    }
    private NavalMilitaryTemplate.Template pickMilitaryTemplate() {
        List<NavalMilitaryTemplate.Template> candidates=new ArrayList<>();
        for(String source:jdbc.queryForList("SELECT result_json FROM naval_round_results WHERE JSON_UNQUOTE(JSON_EXTRACT(result_json,'$.ruleSet'))='MILITARY' ORDER BY finished_at DESC LIMIT 500",String.class)){
            NavalMilitaryTemplate.Template template=NavalMilitaryTemplate.extract(readMap(source),json);if(template!=null)candidates.add(template);
        }
        return candidates.isEmpty()?null:candidates.get(RANDOM.nextInt(candidates.size()));
    }
    private void seedMilitaryAi(NavalState state){if("AI".equals(state.mode)&&"MILITARY".equals(state.ruleSet))NavalMilitaryTemplate.install(state,pickMilitaryTemplate());}

    @Transactional public Map<String,Object> medalWall(PlayerIdentity identity,String id){NavalMedals medals=new NavalMedals(jdbc,json);Map<String,Object> out=new LinkedHashMap<>(id==null||id.isBlank()?medals.wall(identity.key()):medals.publicWall(id));out.put("mine",Objects.equals(out.get("medalId"),medals.wall(identity.key()).get("medalId")));return out;}
    @Transactional public Map<String,Object> equipMedal(PlayerIdentity identity,String id){return new NavalMedals(jdbc,json).equip(identity.key(),id);}
    public record CreateRequest(String mode, boolean autoMatch, String ruleSet) {
        public CreateRequest(String mode, boolean autoMatch) { this(mode,autoMatch,"CLASSIC"); }
    }
    public record JoinRequest(String inviteCode) { }
    public record SpectateRequest(String inviteCode, String viewMode) { }
    public record Formation(String id, String name, List<Ship> ships) { }
    public record ProfileRequest(List<Formation> formations) { }
    public record ConnectionRequest(String pageToken, boolean connected) { }
    public record EnterRequest(String inviteCode) { }
    public record LocalRoomRequest(String id, int round, String phase, long deadline, int turn, Integer winner,
            boolean allowSpectators, List<Player> players, List<NavalState.Event> events, long presentationUntil, String ruleSet) {
        public LocalRoomRequest(String id,int round,String phase,long deadline,int turn,Integer winner,boolean allowSpectators,List<Player> players,List<NavalState.Event> events,long presentationUntil) {this(id,round,phase,deadline,turn,winner,allowSpectators,players,events,presentationUntil,"CLASSIC");}
    }

    @Transactional(readOnly=true)
    public List<Map<String,Object>> rooms() {
        long now=System.currentTimeMillis();
        return jdbc.query("SELECT state_json FROM naval_rooms WHERE phase IN ('WAITING','PLACEMENT','SUPPLY','BATTLE','LOCAL') AND updated_at > CURRENT_TIMESTAMP - INTERVAL 35 SECOND ORDER BY updated_at DESC LIMIT 200",this::readState).stream()
            .filter(state -> !"FINISHED".equals(state.phase))
            .filter(state -> state.players.stream().anyMatch(p -> !p.left && !"AI".equals(p.key) && now-(p.disconnectedAt>0?p.disconnectedAt:p.lastSeenAt)<30_000))
            .map(state -> {
                Map<String,Object> row=new LinkedHashMap<>();
                row.put("id",state.id); row.put("mode",state.mode); row.put("phase",state.phase); row.put("ruleSet",state.ruleSet);
                row.put("requiresInvite","MULTIPLAYER".equals(state.mode) && !state.autoMatch);
                row.put("allowSpectators",state.allowSpectators); row.put("spectators",state.spectators.stream().filter(p -> now-p.lastSeenAt<45_000).count());
                row.put("players",state.players.stream().filter(p -> !p.left).map(p -> { Map<String,Object> person=new LinkedHashMap<>(); person.put("medals",new NavalMedals(jdbc,json).wall(p.key));person.put("name",p.name); person.put("registered",p.registered); person.put("avatarUrl",p.registered?p.avatarUrl:null); return person; }).toList());
                return row;
            }).toList();
    }

    @Transactional
    public Map<String,Object> enter(String id,PlayerIdentity identity,EnterRequest request) {
        lockMatchmaking();
        NavalState state=loadForUpdate(id); NavalEngine.advance(state,System.currentTimeMillis()); requireOpenRoom(state);
        if(activeIndex(state,identity.key())>=0) return get(id,identity);
        if("MULTIPLAYER".equals(state.mode) && !state.autoMatch && (request==null || request.inviteCode()==null || !state.inviteCode.equalsIgnoreCase(request.inviteCode().trim())))
            throw new BusinessException(ErrorCode.FORBIDDEN,"此房间需要正确的邀请码");
        if("MULTIPLAYER".equals(state.mode) && "WAITING".equals(state.phase) && vacant(state)>=0) return join(identity,state.inviteCode);
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
            if(p.shots==null || p.shots.size()>144 || p.mines==null || p.mines.size()>12 || p.aa==null || p.aa.size()>3 || p.radar==null || p.radar.size()>("MILITARY".equals(mirrorRules)?144:18) || p.inventory==null) NavalEngine.bad("本地观战数据无效");
        }
        lockMatchmaking(); long now=System.currentTimeMillis();
        List<NavalState> rows=jdbc.query("SELECT state_json FROM naval_rooms WHERE id=? FOR UPDATE",this::readState,request.id());
        NavalState state;
        if(rows.isEmpty()) {
            // At most one disposable AI mirror per guest.
            jdbc.update("UPDATE naval_rooms SET phase='FINISHED' WHERE host_key=? AND mode='AI' AND id<>?",identity.key(),request.id());
            state=new NavalState(); state.id=request.id(); state.inviteCode=newCode(); state.mode="AI"; state.rulesVersion=2; state.localSimulation=true; state.connectionTracking=true; state.presentationSync=true;
        } else {
            state=rows.get(0);
            if(!state.localSimulation || !identity.key().equals(state.players.get(0).key)) throw new BusinessException(ErrorCode.FORBIDDEN,"无权更新此观战房间");
        }
        List<Player> prior=state.players; state.players=request.players();
        for(int i=0;i<2;i++) {
            Player p=state.players.get(i); p.key=i==0?identity.key():"AI"; p.id=prior.size()>i?prior.get(i).id:UUID.randomUUID().toString();
            p.medals=new NavalMedals(jdbc,json).wall(p.key);p.name=i==0?identity.displayName():"海军 AI"; p.registered=false; p.avatarUrl=null; p.lastSeenAt=now; p.disconnectedAt=0;
        }
        state.ruleSet=mirrorRules;
        state.phase=request.phase(); state.round=request.round(); state.deadline=request.deadline(); state.turn=request.turn(); state.winner=request.winner(); state.events=request.events(); state.lastSeenAt=now;
        state.finishReason=null;
        state.allowSpectators=request.allowSpectators(); state.presentationUntil=request.presentationUntil(); state.battleStarted=List.of("BATTLE","FINISHED").contains(state.phase);
        if(!state.allowSpectators) state.spectators.clear(); pruneSpectators(state,now);
        if(rows.isEmpty()) jdbc.update("INSERT INTO naval_rooms(id,invite_code,mode,auto_match,phase,host_key,peer_key,deadline,state_json) VALUES(?,?,?,FALSE,?,?,?,?,?)",state.id,state.inviteCode,"AI","FINISHED".equals(state.phase)?"FINISHED":"LOCAL",identity.key(),"AI",state.deadline,write(state));
        else save(state);
        return Map.of("spectators",NavalEngine.view(state,0,now).get("spectators"));
    }

    @Transactional
    public Map<String,Object> create(PlayerIdentity identity, CreateRequest request) {
        if (request != null && request.ruleSet() != null && !List.of("CLASSIC","MILITARY").contains(request.ruleSet())) NavalEngine.bad("未知规则模式");
        if (request == null || !List.of("AI","MULTIPLAYER").contains(request.mode())) NavalEngine.bad("请选择单人或双人模式");
        if ("AI".equals(request.mode()) && !identity.loggedIn()) NavalEngine.bad("游客单人对战请使用本地模式");
        // One database mutex makes empty-queue create and invite joins atomic across instances.
        lockMatchmaking();
        long now = System.currentTimeMillis();
        NavalState existing = activeRoom(identity,request.mode(),null,now);
        if (existing != null) {
            touch(existing,identity,now);
            if ("WAITING".equals(existing.phase)) existing.lastSeenAt = now;
            save(existing);
            return view(existing,identity,now);
        }
        if (request.autoMatch() && "MULTIPLAYER".equals(request.mode())) {
            List<NavalState> candidates = jdbc.query("SELECT state_json FROM naval_rooms WHERE phase='WAITING' AND mode='MULTIPLAYER' AND auto_match=TRUE AND host_key<>? AND deadline>? AND updated_at > CURRENT_TIMESTAMP - INTERVAL 20 SECOND ORDER BY created_at LIMIT 50 FOR UPDATE",
                    this::readState,identity.key(),now);
            for (NavalState room : candidates) {
                if (!java.util.Objects.equals(room.ruleSet,request.ruleSet()==null?"CLASSIC":request.ruleSet())) continue;
                NavalEngine.advance(room,now);
                if (!"WAITING".equals(room.phase)) { save(room); continue; }
                if (occupied(room) != 1 || activeIndex(room,identity.key()) >= 0) continue;
                putPlayer(room,vacant(room),player(identity));
                NavalEngine.beginPlacement(room,now); save(room); return view(room,identity,now);
            }
        }
        NavalState state = new NavalState(); state.rulesVersion = 2; state.id = UUID.randomUUID().toString(); state.inviteCode = newCode();
        state.presentationSync = true;
        state.connectionTracking = true;
        state.mode = request.mode(); state.autoMatch = request.autoMatch() && "MULTIPLAYER".equals(request.mode());
        state.ruleSet = request.ruleSet()==null ? "CLASSIC" : request.ruleSet();
        state.players.add(player(identity)); state.deadline = now + 30 * 60_000;
        if ("AI".equals(state.mode)) {
            state.players.add(NavalEngine.player("AI", "海军 AI")); NavalEngine.beginPlacement(state,now); seedMilitaryAi(state);
        }
        jdbc.update("INSERT INTO naval_rooms (id,invite_code,mode,auto_match,phase,host_key,peer_key,deadline,state_json) VALUES (?,?,?,?,?,?,?,?,?)",
                state.id,state.inviteCode,state.mode,state.autoMatch,state.phase,identity.key(),state.players.size()==2 ? state.players.get(1).key : null,state.deadline,write(state));
        return view(state,identity,now);
    }

    @Transactional
    public Map<String,Object> join(PlayerIdentity identity, String code) {
        lockMatchmaking();
        NavalState state = loadCode(code,true); long now = System.currentTimeMillis();
        NavalEngine.advance(state,now);
        requireOpenRoom(state);
        if (activeIndex(state,identity.key()) >= 0) { touch(state,identity,now); save(state); return view(state,identity,now); }
        if (!"MULTIPLAYER".equals(state.mode) || vacant(state) < 0 || !List.of("WAITING","FINISHED").contains(state.phase))
            throw new BusinessException(ErrorCode.CONFLICT,"房间已满");
        requireNoOtherRoom(identity,state.id,now);
        claimSeat(state,identity,vacant(state),now); save(state);
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

    @Transactional
    public Map<String,Object> get(String id, PlayerIdentity identity) {
        NavalState state = loadForUpdate(id); long now = System.currentTimeMillis(); pruneSpectators(state,now); requireViewer(state,identity);
        NavalEngine.advance(state,now);
        touch(state,identity,now);
        Spectator person = spectator(state,identity.key()); if (person != null) person.lastSeenAt = now;
        if ("WAITING".equals(state.phase) && activeIndex(state,identity.key()) >= 0) state.lastSeenAt = now;
        save(state); return view(state,identity,now);
    }

    @Transactional
    public Map<String,Object> action(String id, PlayerIdentity identity, Action action) {
        if (action == null || action.type == null) NavalEngine.bad("缺少操作类型");
        if (List.of("TAKE_SEAT","CONTINUE").contains(action.type)) lockMatchmaking();
        NavalState state = loadForUpdate(id); long now = System.currentTimeMillis(); pruneSpectators(state,now); requireViewer(state,identity);
        if (!("LEAVE".equals(action.type) && "AI".equals(state.mode) && !NavalEngine.battleStarted(state))) NavalEngine.advance(state,now);
        int you = activeIndex(state,identity.key());
        touch(state,identity,now);
        if (you < 0) {
            Spectator person = spectator(state,identity.key());
            if ("LEAVE".equals(action.type)) {
                state.spectators.removeIf(item -> identity.key().equals(item.key)); save(state);
                return NavalEngine.view(state,-1,"SPECTATOR","BOTH",null,now);
            }
            if (person == null) throw new BusinessException(ErrorCode.FORBIDDEN,"你已离开玩家席位，请先进入观众席");
            if ("SPECTATE_VIEW".equals(action.type)) { person.viewMode = viewMode(action.viewMode); person.lastSeenAt = now; }
            else if ("TAKE_SEAT".equals(action.type)) {
                requireOpenRoom(state);
                requireNoOtherRoom(identity,state.id,now);
                if (action.seat == null || action.seat < 0 || action.seat > 1 || !"MULTIPLAYER".equals(state.mode) ||
                        !List.of("WAITING","FINISHED").contains(state.phase) || (action.seat < state.players.size() && !state.players.get(action.seat).left))
                    throw new BusinessException(ErrorCode.CONFLICT,"该玩家席位不可加入");
                claimSeat(state,identity,action.seat,now);
            } else throw new BusinessException(ErrorCode.FORBIDDEN,"观众不能操作对局或发送消息");
            save(state); return view(state,identity,now);
        }
        switch (action.type) {
            case "SPECTATORS" -> { state.allowSpectators=Boolean.TRUE.equals(action.value); if(!state.allowSpectators) state.spectators.clear(); }
            case "DEPLOY" -> NavalEngine.deploy(state,you,action,now);
            case "LAYOUT_READY" -> NavalEngine.layoutReady(state,you,action,now);
            case "READY" -> NavalEngine.ready(state,you,now);
            case "ATTACK" -> NavalEngine.attack(state,you,action,now);
            case "PRESENTED" -> NavalEngine.presented(state,you,action.presentationId,now);
            case "CHAT" -> NavalEngine.chat(state,you,action.text,now);
            case "LEAVE" -> NavalEngine.leave(state,you,now);
            case "CONTINUE" -> continueRound(state,you,action.value == null || action.value,now);
            default -> NavalEngine.bad("未知的战局操作");
        }
        save(state); return view(state,identity,now);
    }

    @Transactional
    public Map<String,Object> connection(String id, PlayerIdentity identity, ConnectionRequest request) {
        if (request == null || request.pageToken() == null || !request.pageToken().matches("[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}")) NavalEngine.bad("页面标识无效");
        NavalState state = loadForUpdate(id); long now = System.currentTimeMillis(); requireViewer(state,identity);
        NavalEngine.advance(state,now);
        int you = activeIndex(state,identity.key());
        if (you >= 0 && !"FINISHED".equals(state.phase)) {
            Player player = state.players.get(you);
            if (request.connected()) {
                touch(state,identity,now); player.connectionToken = request.pageToken();
            } else if (player.connectionToken == null || request.pageToken().equals(player.connectionToken)) {
                if ("WAITING".equals(state.phase)) NavalEngine.leave(state,you,now);
                else if (player.disconnectedAt == 0) player.disconnectedAt = now;
            }
        }
        save(state);
        return Map.of("phase",state.phase,"cancelled","CANCELLED".equals(state.finishReason));
    }

    private static void touch(NavalState state, PlayerIdentity identity, long now) {
        int you = activeIndex(state,identity.key());
        if (you >= 0 && !"FINISHED".equals(state.phase)) {
            Player person = state.players.get(you);
            long pausedAt = person.disconnectedAt > 0 ? person.disconnectedAt : person.lastSeenAt;
            if ("AI".equals(state.mode) && List.of("PLACEMENT","SUPPLY").contains(state.phase) && pausedAt > 0 && (person.disconnectedAt > 0 || now-pausedAt>=5_000)) state.deadline += Math.max(0,now-pausedAt);
            person.lastSeenAt = now; person.disconnectedAt = 0;
        }
    }

    private static void requireOpenRoom(NavalState state) {
        if ("FINISHED".equals(state.phase) && state.winner == null && occupied(state) == 0)
            throw new BusinessException(ErrorCode.NOT_FOUND,"房间已关闭，请创建新房间");
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
        metadata.put("ruleSet",state.ruleSet); metadata.put("playerNames",names); metadata.put("title","海战棋 · "+rules+" · "+(names.isEmpty() ? "指挥官" : names.get(0))+"的房间");
        metadata.put("avatarUrls",state.players.stream().filter(person -> !person.left && person.registered && person.avatarUrl != null).map(person -> person.avatarUrl).toList());
        metadata.put("description",String.join(" / ",names)+" 邀你加入海战棋。玩家席位 "+occupied(state)+"/2，满员可进入观众席。");
        return metadata;
    }

    private void claimSeat(NavalState state, PlayerIdentity identity, int seat, long now) {
        archiveRound(state);
        boolean hadActive = occupied(state) > 0;
        // A departed identity may return in the other vacant seat, never in both.
        for (int i=0;i<state.players.size();i++) if (i != seat && identity.key().equals(state.players.get(i).key)) {
            Player empty = NavalEngine.player("VACANT:"+UUID.randomUUID(),"空席"); empty.left = true; state.players.set(i,empty);
        }
        Player person = player(identity); person.continueRequested = true;
        putPlayer(state,seat,person); state.spectators.removeIf(item -> identity.key().equals(item.key));
        if ("WAITING".equals(state.phase) && occupied(state) == 2) {
            if (state.settlement == null) NavalEngine.beginPlacement(state,now); else newRound(state,now);
        } else if ("FINISHED".equals(state.phase) && occupied(state) == 2) maybeContinue(state,now);
        else if ("FINISHED".equals(state.phase) && !hadActive) {
            state.phase = "WAITING"; state.deadline = now+30*60_000; state.lastSeenAt = now;
        }
    }

    private void continueRound(NavalState state, int you, boolean value, long now) {
        if (!"FINISHED".equals(state.phase)) NavalEngine.bad("本局尚未结束");
        if (value && "MULTIPLAYER".equals(state.mode)) requireNoOtherRoom(state.players.get(you).key,state.id,now);
        archiveRound(state); state.players.get(you).continueRequested = value;
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
        archiveRound(state); state.round++; state.messages = new ArrayList<>(); state.events = new ArrayList<>();
        NavalEngine.beginPlacement(state,now); seedMilitaryAi(state);
        state.players.forEach(person -> { person.lastSeenAt = now; person.disconnectedAt = 0; });
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
        return rows.get(0);
    }

    private Player player(PlayerIdentity identity) {
        Player person = NavalEngine.player(identity.key(),identity.displayName());
        person.registered = identity.loggedIn(); person.avatarUrl = avatar(identity); person.lastSeenAt = System.currentTimeMillis(); return person;
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
        for(Player p:state.players)p.medals=new NavalMedals(jdbc,json).wall(p.key);
        for(Spectator p:state.spectators)p.medals=new NavalMedals(jdbc,json).wall(p.key);
        int active = activeIndex(state,identity.key());
        if (active >= 0) return NavalEngine.view(state,active,now);
        Spectator person = spectator(state,identity.key());
        if (person != null) return NavalEngine.view(state,-1,"SPECTATOR",person.viewMode,person.id,now);
        return NavalEngine.view(state,indexOf(state,identity.key()),now);
    }

    @Scheduled(fixedDelay = 1000, initialDelay = 15_000)
    public void tick() {
        try {
            List<String> rooms = jdbc.queryForList("SELECT id FROM naval_rooms WHERE phase IN ('WAITING','PLACEMENT','SUPPLY','BATTLE','LOCAL') ORDER BY updated_at LIMIT 100",String.class);
            TransactionTemplate tx = new TransactionTemplate(transactions);
            for (String id : rooms) {
                try { tx.executeWithoutResult(status -> { NavalState state = loadForUpdate(id); NavalEngine.advance(state,System.currentTimeMillis()); save(state); }); }
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
        List<NavalState> rows = jdbc.query("SELECT state_json FROM naval_rooms WHERE id=? FOR UPDATE",this::readState,id);
        if (rows.isEmpty()) throw new BusinessException(ErrorCode.NOT_FOUND,"海战房间不存在");
        return rows.get(0);
    }

    private void archiveRound(NavalState state) {
        if(state.localSimulation) return;
        if (!"FINISHED".equals(state.phase) || state.winner == null || state.players.size() != 2 || "AI".equals(state.mode) && !NavalEngine.battleStarted(state)) return;
        String gameId = state.id+":"+state.round;
        if (state.settlement != null) return;
        Map<String,Object> result = result(state);
        jdbc.update("INSERT IGNORE INTO naval_round_results (game_id,room_id,round_no,player0_key,player1_key,finished_at,result_json) VALUES (?,?,?,?,?,?,?)",
                gameId,state.id,state.round,state.players.get(0).key,state.players.get(1).key,state.finishedAt,write(result));
        new NavalMedals(jdbc,json).credit(result);
        Map<String,Object> publicResult = new LinkedHashMap<>(result);
        @SuppressWarnings("unchecked") List<Map<String,Object>> participants = (List<Map<String,Object>>)result.get("players");
        publicResult.put("players",participants.stream().map(person -> { Map<String,Object> copy = new LinkedHashMap<>(person); copy.remove("key"); return copy; }).toList());
        state.settlement = publicResult;
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
            stats.put("minesRemaining",person.mines); stats.put("antiAirRemaining",person.aa);
            stats.put("shots",target.shots.size()); stats.put("accuracy",target.shots.isEmpty() ? 0 : Math.round(100.0*hits/target.shots.size()));
            stats.put("shipsSunk",target.ships.stream().filter(NavalEngine::sunk).count());
            stats.put("shipsRemaining",person.ships.stream().filter(ship -> !NavalEngine.sunk(ship)).count());
            participants.add(stats);
        }
        result.put("players",participants); result.put("events",state.events); return result;
    }

    @SuppressWarnings("unchecked")
    private Map<String,Object> historySummary(Map<String,Object> result, String key) {
        if ("AI".equals(result.get("mode")) && Boolean.FALSE.equals(result.get("battleStarted"))) return null;
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
        jdbc.update("UPDATE naval_rooms SET phase=?,host_key=?,peer_key=?,deadline=?,state_json=?,updated_at=CURRENT_TIMESTAMP WHERE id=?",state.localSimulation && !"FINISHED".equals(state.phase)?"LOCAL":state.phase,state.players.get(0).key,
                state.players.size()==2 ? state.players.get(1).key : null,state.deadline,write(state),state.id);
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
        try {
            NavalState state=json.readValue(rs.getString("state_json"),NavalState.class);
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
