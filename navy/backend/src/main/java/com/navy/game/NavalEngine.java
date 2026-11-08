package com.navy.game;

import com.navy.common.exception.BusinessException;
import com.navy.common.exception.ErrorCode;
import com.navy.game.NavalState.*;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class NavalEngine {
    public static final int SIZE = 12;
    public static final long PLACEMENT_MS = 300_000;
    public static final long SUPPLY_MS = 180_000;
    public static final long TURN_MS = 30_000;
    public static final long AI_DELAY_MS = 1_200;
    public static final Map<String, Integer> PRICES = Map.of("antiAir", 30, "fighter", 30,
            "bomber", 25, "torpedo", 25, "radar", 40, "mine", 5, "submarine",30,"stealth",10);
    public static final Map<String, Integer> CAPS = Map.of("antiAir", 3, "fighter", 3,
            "bomber", 3, "torpedo", 3, "radar", 2, "mine", 12, "submarine",3,"stealth",5);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int[][] FLEET = {{1,4}, {1,3}, {1,3}, {1,2}, {1,2}, {1,2}, {1,1}, {1,1}, {1,1}, {1,1}};

    private NavalEngine() { }

    public static Map<String, Integer> emptyInventory() {
        Map<String, Integer> result = new LinkedHashMap<>();
        for (String key : List.of("antiAir", "fighter", "bomber", "torpedo", "radar", "mine", "submarine", "stealth")) result.put(key, 0);
        return result;
    }

    public static Player player(String key, String name) {
        Player player = new Player(); player.key = key; player.name = name; return player;
    }

    public static void beginPlacement(NavalState state, long now) {
        NavalReconnect.reset(state);
        if ("MILITARY".equals(state.ruleSet)) { NavalMilitary.begin(state,now); return; }
        state.rulesVersion = 2;
        state.phase = "PLACEMENT";
        state.deadline = now + PLACEMENT_MS;
        state.winner = null; state.finishedAt = 0; state.finishReason = null; state.settlement = null;
        state.presentationUntil = 0; state.presentationReady.clear();
        state.battleStarted = false;
        state.taskSpectatorsOpened = false;
        state.taskEligibilityVersion = 1;
        state.players.forEach(player -> {
            player.ships = randomFleet(); player.stage = "PLACEMENT"; player.ready = false;
            player.shots = new ArrayList<>(); player.mines = new ArrayList<>(); player.aa = new ArrayList<>();
            player.radar = new ArrayList<>(); player.inventory = emptyInventory(); player.coins = 300;
            player.continueRequested = false; player.left = false;
        });
        if ("AI".equals(state.mode)) {
            Player ai = state.players.get(1);
            ai.inventory.putAll(Map.of("fighter", 2, "bomber", 2, "torpedo", 2, "radar", 1, "antiAir", 2, "mine", 6));
            ai.coins = 300 - ai.inventory.entrySet().stream().mapToInt(item -> PRICES.get(item.getKey()) * item.getValue()).sum();
            ai.mines.addAll(NavalAi.mines(ai.ships,6));
            while (ai.aa.size() < 2) { int row = RANDOM.nextInt(SIZE); if (!ai.aa.contains(row)) ai.aa.add(row); }
            ai.ready = true;
            ai.stage = "READY";
        }
    }

    public static List<Ship> randomFleet() {
        for (int attempt = 0; attempt < 300; attempt++) {
            List<Ship> ships = new ArrayList<>();
            boolean complete = true;
            for (int i = 0; i < FLEET.length; i++) {
                boolean placed = false;
                List<int[]> positions = new ArrayList<>();
                for (int rotated = 0; rotated < 2; rotated++) {
                    int w = FLEET[i][rotated], h = FLEET[i][1 - rotated];
                    for (int y = 0; y <= SIZE - h; y++) for (int x = 0; x <= SIZE - w; x++) positions.add(new int[]{x,y,w,h});
                }
                Collections.shuffle(positions, RANDOM);
                for (int[] p : positions) {
                    Ship ship = new Ship("ship-" + i, p[0], p[1], p[2], p[3]);
                    if (ships.stream().noneMatch(other -> touching(ship, other))) { ships.add(ship); placed = true; break; }
                }
                if (!placed) { complete = false; break; }
            }
            if (complete) return ships;
        }
        throw new IllegalStateException("Could not generate a legal naval fleet");
    }

    public static void validateFleet(List<Ship> ships) {
        if (ships == null || ships.size() != 10) bad("需要布置完整的 10 艘舰船");
        validatePartialFleet(ships);
    }

    public static void validatePartialFleet(List<Ship> ships) {
        if (ships == null || ships.size() > 10) bad("最多布置 10 艘舰船");
        Map<String, Integer> counts = new HashMap<>();
        Set<String> ids = new HashSet<>();
        for (Ship ship : ships) {
            if (ship == null || ship.id == null || ship.id.isBlank() || ship.id.length() > 80 || !ids.add(ship.id)) bad("舰船编号无效或重复");
            if (ship.w < 1 || ship.h < 1 || ship.w > 4 || ship.h > 4 || ship.x < 0 || ship.y < 0 || ship.x > SIZE - ship.w || ship.y > SIZE - ship.h) bad("舰船不能超出海域");
            counts.merge(Math.min(ship.w, ship.h) + "x" + Math.max(ship.w, ship.h), 1, Integer::sum);
        }
        Map<String,Integer> expected = Map.of("1x1",4,"1x2",3,"1x3",2,"1x4",1);
        if (counts.entrySet().stream().anyMatch(entry -> entry.getValue() > expected.getOrDefault(entry.getKey(),0))) bad("舰船尺寸或数量不符合规则");
        for (int i = 0; i < ships.size(); i++) for (int j = i + 1; j < ships.size(); j++)
            if (touching(ships.get(i), ships.get(j))) bad("舰船之间必须至少空出一格，斜角也不能相邻");
    }

    public static List<Ship> completeFleet(List<Ship> partial) {
        validatePartialFleet(partial);
        List<Ship> placed = new ArrayList<>(partial);
        Map<Integer,Integer> used = new HashMap<>();
        Set<String> ids = new HashSet<>();
        for (Ship ship : partial) { used.merge(ship.w*ship.h,1,Integer::sum); ids.add(ship.id); }
        List<int[]> missing = new ArrayList<>();
        for (int[] size : FLEET) {
            int area = size[0]*size[1];
            if (used.getOrDefault(area,0)>0) used.put(area,used.get(area)-1);
            else missing.add(size);
        }
        if (fillFleet(placed,missing,0,ids,new int[]{0})) return placed;
        return randomFleet();
    }

    private static boolean fillFleet(List<Ship> placed, List<int[]> missing, int index, Set<String> ids, int[] nodes) {
        if (index == missing.size()) return true;
        if (++nodes[0] > 30_000) return false;
        int[] size = missing.get(index);
        List<int[]> candidates = new ArrayList<>();
        for (int rotated=0;rotated<2;rotated++) {
            int w=size[rotated],h=size[1-rotated];
            for(int y=0;y<=SIZE-h;y++) for(int x=0;x<=SIZE-w;x++) candidates.add(new int[]{x,y,w,h});
        }
        Collections.shuffle(candidates,RANDOM);
        String id="auto-"+index;
        while(ids.contains(id)) id+="-new";
        ids.add(id);
        for(int[] position:candidates) {
            Ship ship=new Ship(id,position[0],position[1],position[2],position[3]);
            if(placed.stream().anyMatch(other -> touching(ship,other))) continue;
            placed.add(ship);
            if(fillFleet(placed,missing,index+1,ids,nodes)) return true;
            placed.remove(placed.size()-1);
            if(nodes[0]>30_000) break;
        }
        ids.remove(id);
        return false;
    }

    private static boolean touching(Ship a, Ship b) {
        return a.x <= b.x + b.w && b.x <= a.x + a.w && a.y <= b.y + b.h && b.y <= a.y + a.h;
    }

    public static void deploy(NavalState state, int index, Action action, long now) {
        if ("MILITARY".equals(state.ruleSet)) { NavalMilitary.deploy(state,index,action,now); return; }
        if (!List.of("PLACEMENT","SUPPLY").contains(state.phase)) bad("当前不能调整部署");
        Player player = state.players.get(index);
        if (player.ready) bad("已准备，不能再修改阵型");
        List<Ship> ships = action.ships == null ? player.ships : action.ships;
        if ("PLACEMENT".equals(player.stage)) validatePartialFleet(ships); else validateFleet(ships);
        if ("PLACEMENT".equals(player.stage) && (action.ready || (action.inventory != null && action.inventory.values().stream().anyMatch(count -> count == null || count != 0)) || (action.mines != null && !action.mines.isEmpty()) || (action.aa != null && !action.aa.isEmpty())))
            bad("请先完成布阵，再购买和部署道具");
        if ("SUPPLY".equals(player.stage) && !sameFleet(player.ships,ships)) bad("补给阶段阵型已锁定，不能移动舰船");
        Map<String, Integer> inventory = emptyInventory();
        if (action.inventory != null) {
            for (Map.Entry<String, Integer> item : action.inventory.entrySet()) {
                if (!CAPS.containsKey(item.getKey()) || item.getValue() == null || item.getValue() < 0 || item.getValue() > CAPS.get(item.getKey())) bad("道具购买数量无效");
                inventory.put(item.getKey(), item.getValue());
            }
        }
        int cost = inventory.entrySet().stream().mapToInt(e -> PRICES.get(e.getKey()) * e.getValue()).sum();
        if (cost > 300) bad("金币不足，每局只有 300 金币");
        List<Cell> mines = action.mines == null ? List.of() : action.mines;
        if (mines.size() > inventory.get("mine") || new HashSet<>(mines).size() != mines.size()) bad("水雷数量无效或位置重复");
        Player staged = new Player(); staged.ships = ships;
        for (Cell cell : mines) if (cell == null || !inside(cell.x(), cell.y()) || shipAt(staged, cell.x(), cell.y()) != null) bad("水雷不能超出海域或与舰船重叠");
        List<Integer> aa = action.aa == null ? List.of() : action.aa;
        if (aa.size() > inventory.get("antiAir") || new HashSet<>(aa).size() != aa.size() || aa.stream().anyMatch(row -> row == null || row < 0 || row >= SIZE)) bad("防空炮数量或行号无效");
        List<String> coated = action.coatedShips == null ? List.of() : action.coatedShips;
        if (coated.size() > inventory.get("stealth") || new HashSet<>(coated).size() != coated.size() || coated.stream().anyMatch(id -> id == null || ships.stream().noneMatch(ship -> id.equals(ship.id)))) bad("隐身涂层数量或舰船无效");
        player.ships = ships;
        player.ships.forEach(ship -> { ship.hits = new ArrayList<>(); ship.stealth = coated.contains(ship.id); });
        player.mines = new ArrayList<>(mines); player.aa = new ArrayList<>(aa);
        player.inventory = inventory; player.coins = 300 - cost;
        if (action.ready) ready(state, index, now);
    }

    public static void ready(NavalState state, int index, long now) {
        if ("MILITARY".equals(state.ruleSet)) { Action a = new Action(); a.ships=state.players.get(index).ships; a.ready=true; NavalMilitary.deploy(state,index,a,now); return; }
        if (!List.of("PLACEMENT","SUPPLY").contains(state.phase) || !"SUPPLY".equals(state.players.get(index).stage)) bad("请先完成布阵，再确认补给");
        Player player = state.players.get(index);
        validateFleet(player.ships);
        if (player.mines.size() != player.inventory.getOrDefault("mine",0) || player.aa.size() != player.inventory.getOrDefault("antiAir",0))
            bad("请先布置所有已购买的水雷和防空炮");
        if (player.ships.stream().filter(ship -> ship.stealth).count() != player.inventory.getOrDefault("stealth",0)) bad("请先涂好所有已购买的隐身图层");
        player.ready = true;
        player.stage = "READY";
        if (state.players.size() == 2 && state.players.stream().allMatch(p -> p.ready)) startBattle(state, now);
    }

    public static void layoutReady(NavalState state, int index, Action action, long now) {
        if ("MILITARY".equals(state.ruleSet)) { if(action==null)action=new Action(); action.ready=true; NavalMilitary.deploy(state,index,action,now); return; }
        Player player = state.players.get(index);
        if (!"PLACEMENT".equals(state.phase) || !"PLACEMENT".equals(player.stage)) bad("当前不能修改或提交阵型");
        if (action != null && action.ships != null) {
            Action layout = new Action(); layout.ships = action.ships;
            deploy(state,index,layout,now);
        }
        validateFleet(player.ships);
        player.stage = "SUPPLY";
        beginSupplyIfArranged(state,now);
    }

    private static void beginSupplyIfArranged(NavalState state, long now) {
        if ("PLACEMENT".equals(state.phase) && state.players.size() == 2 && state.players.stream().noneMatch(player -> "PLACEMENT".equals(player.stage))) {
            state.phase = "SUPPLY"; state.deadline = now + SUPPLY_MS;
            event(state,-1,"supply",List.of(),"阵型已锁定，进入补给阶段");
        }
    }

    private static boolean sameFleet(List<Ship> a, List<Ship> b) {
        return a.size() == b.size() && a.stream().allMatch(ship -> b.stream().anyMatch(other -> ship.id.equals(other.id) && ship.x == other.x && ship.y == other.y && ship.w == other.w && ship.h == other.h));
    }

    public static List<Ship> normalizeSavedFleet(List<Ship> ships) {
        if (ships == null) bad("缺少阵型");
        List<Ship> normalized = ships.stream().filter(ship -> ship == null || !(Math.min(ship.w,ship.h) == 2 && Math.max(ship.w,ship.h) == 3)).toList();
        validateFleet(normalized);
        return new ArrayList<>(normalized);
    }

    public static void normalize(NavalState state, long now) {
        normalizeMessages(state);
        if ("BATTLE".equals(state.phase) && state.allowSpectators) state.taskSpectatorsOpened = true;
        if (state.taskEligibilityVersion == 0 && "AI".equals(state.mode) && "CLASSIC".equals(state.ruleSet)
                && List.of("PLACEMENT","SUPPLY").contains(state.phase) && !battleStarted(state)) {
            for (Player player : state.players) if ("AI".equals(player.key))
                player.coins = 300 - player.inventory.entrySet().stream().mapToInt(item -> PRICES.getOrDefault(item.getKey(),0) * item.getValue()).sum();
        }
        if("MILITARY".equals(state.ruleSet)){
            NavalRepairSupport.normalizeShots(state);
            if("PLACEMENT".equals(state.phase))for(Player p:state.players)if(p.ships.removeIf(ship->"torpedo".equals(ship.kind)))p.coins=300-p.ships.stream().mapToInt(ship->NavalMilitary.MODELS.get(ship.kind)[2]).sum();
            for(Player p:state.players)for(Ship ship:p.ships)if("submarine".equals(ship.kind)&&(sunk(ship)||ship.hits.contains(NavalMilitary.key(NavalMilitary.cell(ship,0,1)))))for(int y=ship.y;y<ship.y+ship.h;y++)for(int x=ship.x;x<ship.x+ship.w;x++){if(!ship.hits.contains(x+","+y))ship.hits.add(x+","+y);if(shotAt(p,x,y)==null)p.shots.add(new Shot(x,y,"hit"));}
            return;
        }
        if (state.rulesVersion >= 2) return;
        state.rulesVersion = 2;
        for (Player player : state.players) {
            if (!player.ships.isEmpty()) player.ships = normalizeSavedFleet(player.ships);
            player.stage = player.ready || List.of("BATTLE","FINISHED").contains(state.phase) ? "READY"
                    : player.inventory.values().stream().anyMatch(count -> count > 0) ? "SUPPLY" : "PLACEMENT";
            player.shots = player.shots.stream().map(shot -> "hit".equals(shot.result()) && shipAt(player,shot.x(),shot.y()) == null ? new Shot(shot.x(),shot.y(),"miss") : shot).collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        }
        for (int i = 0; i < state.players.size(); i++) {
            Player opponent = state.players.get(state.players.size() == 2 ? 1-i : i);
            Player player = state.players.get(i);
            player.radar = player.radar.stream().map(cell -> new Radar(cell.x(),cell.y(),shipAt(opponent,cell.x(),cell.y()) != null)).collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        }
        beginSupplyIfArranged(state,now);
        if ("BATTLE".equals(state.phase) && state.players.size() == 2) {
            boolean firstLost = state.players.get(0).ships.stream().allMatch(NavalEngine::sunk);
            boolean secondLost = state.players.get(1).ships.stream().allMatch(NavalEngine::sunk);
            if (firstLost || secondLost) { state.phase = "FINISHED"; state.winner = secondLost ? 0 : 1; state.deadline = 0; state.finishedAt = now; }
        }
    }

    private static void startBattle(NavalState state, long now) {
        state.battleStarted = true;
        state.taskSpectatorsOpened = state.allowSpectators;
        state.taskEligibilityVersion = 1;
        state.phase = "BATTLE"; state.turn = RANDOM.nextInt(2); state.deadline = now + TURN_MS;
        state.nextAiAt = now + AI_DELAY_MS;
        event(state, -1, "start", List.of(), "舰队就位，战斗开始");
        waitForPresentation(state,now);
    }

    public static void waitForPresentation(NavalState state,long now) {
        if (!state.presentationSync || !"BATTLE".equals(state.phase)) return;
        state.presentationId = state.sequence; state.presentationUntil = now+15_000;
        state.presentationReady.clear();
        if ("AI".equals(state.mode)) state.presentationReady.add(1);
        state.deadline=state.presentationUntil+TURN_MS; state.nextAiAt=state.presentationUntil+AI_DELAY_MS;
    }
    public static void presented(NavalState state,int actor,long id,long now) {
        NavalReconnect.requirePlaying(state);
        if (state.presentationUntil==0 || id!=state.presentationId) return;
        if (!state.presentationReady.contains(actor)) state.presentationReady.add(actor);
        if (state.presentationReady.size()>=2) releasePresentation(state,now);
    }
    private static void releasePresentation(NavalState state,long now) {
        state.presentationUntil=0; state.deadline=now+TURN_MS; state.nextAiAt=now+AI_DELAY_MS;
    }

    public static void advance(NavalState state, long now) {
        if (state.localSimulation) {
            boolean paused=state.localPause!=null&&Boolean.TRUE.equals(state.localPause.get("active"))&&state.localPause.get("until") instanceof Number;
            long expires=paused?((Number)state.localPause.get("until")).longValue():state.lastSeenAt+30_000;
            if (!"FINISHED".equals(state.phase) && (paused?now>=expires:now>expires)) {
                state.phase="FINISHED"; state.finishReason="CANCELLED"; state.winner=null; state.spectators.clear();
            }
            return;
        }
        normalize(state,now);
        if(NavalReconnect.advance(state,now))return;
        if("MILITARY".equals(state.ruleSet)&&NavalMilitary.checkDefeat(state,now))return;
        if (state.connectionTracking && "AI".equals(state.mode) && List.of("PLACEMENT","SUPPLY").contains(state.phase)) {
            Player human = state.players.get(0);
            if (human.disconnectedAt > 0 || human.lastSeenAt > 0 && now-human.lastSeenAt >= 15_000) return;
        }
        if (state.connectionTracking && "MULTIPLAYER".equals(state.mode) && !List.of("BATTLE","FINISHED").contains(state.phase)) {
            int disconnected = -1;
            long firstExpiry = Long.MAX_VALUE;
            for (int i=0;i<state.players.size();i++) {
                Player player = state.players.get(i);
                long since = player.disconnectedAt > 0 ? player.disconnectedAt : player.lastSeenAt;
                if (!player.left && since > 0 && now >= since+30_000 && since < firstExpiry) {
                    disconnected = i; firstExpiry = since;
                }
            }
            if (disconnected >= 0) {
                finishLeave(state,disconnected,now);
                state.finishReason = "DISCONNECTED";
                event(state,disconnected,"disconnected",List.of(),"玩家离开页面超过 30 秒，未及时重连，判负");
                return;
            }
        }
        if ("WAITING".equals(state.phase) && now >= state.deadline) {
            state.players.forEach(player -> player.left = true);
            state.phase = "FINISHED"; state.finishedAt = now; state.deadline = 0;
            state.finishReason = "EXPIRED";
            event(state, -1, "expired", List.of(), "等待房间已过期，请重新创建");
        }
        if ("PLACEMENT".equals(state.phase) && now >= state.deadline) {
            if ("MILITARY".equals(state.ruleSet)) { NavalMilitary.expirePreparation(state,now); return; }
            for (Player player : state.players) {
                if (!"PLACEMENT".equals(player.stage)) continue;
                if (player.ships.size() != 10) {
                    List<Ship> previous = player.ships;
                    player.ships = completeFleet(previous);
                    if (previous.stream().anyMatch(ship -> player.ships.stream().noneMatch(next -> next.id.equals(ship.id) && next.x==ship.x && next.y==ship.y)))
                        event(state,-1,"formation",List.of(),"剩余舰船无法排入，已自动重排合法阵型");
                }
                player.stage = "SUPPLY";
            }
            beginSupplyIfArranged(state,now);
        } else if ("SUPPLY".equals(state.phase) && now >= state.deadline) {
            for (Player player : state.players) {
                while (player.mines.size() < player.inventory.getOrDefault("mine",0)) {
                    Cell cell = new Cell(RANDOM.nextInt(SIZE),RANDOM.nextInt(SIZE));
                    if (shipAt(player,cell.x(),cell.y()) == null && !player.mines.contains(cell)) player.mines.add(cell);
                }
                while (player.aa.size() < player.inventory.getOrDefault("antiAir",0)) {
                    int row = RANDOM.nextInt(SIZE); if (!player.aa.contains(row)) player.aa.add(row);
                }
                long coats = player.inventory.getOrDefault("stealth",0) - player.ships.stream().filter(ship -> ship.stealth).count();
                for (Ship ship : player.ships) if (!ship.stealth && coats > 0) { ship.stealth = true; coats--; }
                player.ready = true;
                player.stage = "READY";
            }
            startBattle(state, now);
        }
        if (!"BATTLE".equals(state.phase)) return;
        if (state.presentationUntil>0) {
            if (now<state.presentationUntil) return;
            releasePresentation(state,now);
        }
        if (now >= state.deadline) {
            event(state, state.turn, "timeout", List.of(), "攻击超时，回合交给对手");
            state.mineActions=0;state.supportActions=0;state.supportShipId=null; state.turn = 1 - state.turn; state.deadline = now + TURN_MS; state.nextAiAt = now + AI_DELAY_MS;
        }
        if ("AI".equals(state.mode) && state.turn == 1 && now >= state.nextAiAt) {
            if ("MILITARY".equals(state.ruleSet)) NavalMilitary.ai(state,now); else aiAttack(state, now);
        }
    }

    public static void attack(NavalState state, int actor, String weapon, int x, int y, long now) {
        NavalReconnect.requirePlaying(state);
        if (state.presentationUntil>0) bad("请等待战斗动画播放完毕");
        if (!"BATTLE".equals(state.phase) || state.turn != actor) bad("尚未轮到你攻击");
        if (weapon == null || !List.of("shot", "fighter", "bomber", "torpedo", "radar").contains(weapon)) bad("未知的攻击道具");
        Player from = state.players.get(actor), target = state.players.get(1 - actor);
        if (!inside(x, y)) bad("攻击位置超出海域");
        if ("fighter".equals(weapon) && (x > 4 || y > 10)) bad("战斗机需要完整的 2 行 8 列区域");
        if (List.of("bomber", "radar").contains(weapon) && (x > 9 || y > 9)) bad("需要完整的 3 行 3 列区域");
        if (!"shot".equals(weapon) && from.inventory.getOrDefault(weapon, 0) < 1) bad("没有可使用的该道具");
        if ("shot".equals(weapon) && shotAt(target, x, y) != null) bad("该格子已经攻击过");
        if (!"shot".equals(weapon)) from.inventory.compute(weapon, (key, count) -> count - 1);
        List<Shot> cells = new ArrayList<>(), backfire = new ArrayList<>();
        boolean hit = false;
        if ("radar".equals(weapon)) {
            from.radar.removeIf(cell -> cell.x() >= x && cell.x() < x + 3 && cell.y() >= y && cell.y() < y + 3);
            for (int row = y; row < y + 3; row++) for (int col = x; col < x + 3; col++) {
                Ship ship = shipAt(target,col,row); from.radar.add(new Radar(col,row,ship != null && !ship.stealth));
            }
            event(state, actor, weapon, cells, "雷达扫描完成", x, y, null);
        } else {
            int attackRow = "fighter".equals(weapon) ? y+RANDOM.nextInt(2) : y;
            int endRow = "fighter".equals(weapon) ? y + 1 : "bomber".equals(weapon) ? y + 2 : y;
            Integer intercept = List.of("fighter", "bomber", "torpedo").contains(weapon)
                    ? target.aa.stream().filter(row -> row >= y && row <= endRow).findFirst().orElse(null) : null;
            if ("fighter".equals(weapon) && target.aa.contains(attackRow)) intercept=attackRow;
            if (intercept != null) {
                target.aa.remove(intercept);
                target.inventory.compute("antiAir", (key,count) -> Math.max(0,count-1));
                event(state, actor, weapon, cells, "防空炮击落了来袭飞机", x, attackRow, intercept);
            } else {
                List<Cell> area = new ArrayList<>();
                if ("shot".equals(weapon)) area.add(new Cell(x,y));
                if ("fighter".equals(weapon)) for (int col = x; col < x + 8; col++) area.add(new Cell(col,attackRow));
                if ("bomber".equals(weapon)) {
                    for (int row = y; row < y + 3; row++) for (int col = x; col < x + 3; col++) area.add(new Cell(col,row));
                    Collections.shuffle(area, RANDOM); area = new ArrayList<>(area.subList(0, 5 + RANDOM.nextInt(3)));
                }
                if ("torpedo".equals(weapon)) {
                    for (int col = x; col < SIZE; col++) {
                        area.add(new Cell(col,y));
                        if (shipAt(target,col,y) != null || target.mines.contains(new Cell(col,y))) break;
                    }
                }
                for (Cell cell : area) {
                    if (shotAt(target,cell.x(),cell.y()) != null) {
                        if ("torpedo".equals(weapon) && shipAt(target,cell.x(),cell.y()) != null) cells.add(shotAt(target,cell.x(),cell.y()));
                        continue;
                    }
                    Ship ship = shipAt(target,cell.x(),cell.y());
                    String result = ship != null ? "hit" : target.mines.remove(cell) ? "mine" : "miss";
                    if ("mine".equals(result)) {
                        target.inventory.compute("mine", (key,count) -> Math.max(0,count-1));
                        if (!"torpedo".equals(weapon)) {
                            Ship own = shipAt(from,cell.x(),cell.y());
                            boolean ownMine = from.mines.remove(cell);
                            Shot reflection = new Shot(cell.x(),cell.y(),own != null ? "hit" : ownMine ? "mine" : "miss");
                            if (ownMine) from.inventory.compute("mine",(key,count)->Math.max(0,count-1));
                            if (own != null && !own.hits.contains(cell.x()+","+cell.y())) own.hits.add(cell.x()+","+cell.y());
                            if (shotAt(from,cell.x(),cell.y())==null) from.shots.add(reflection);
                            backfire.add(reflection);
                        }
                    }
                    if (ship != null) { ship.hits.add(cell.x() + "," + cell.y()); hit = true; }
                    Shot shot = new Shot(cell.x(),cell.y(),result); cells.add(shot); target.shots.add(shot);
                }
                state.events.add(new Event(++state.sequence,actor,weapon,cells,!backfire.isEmpty() ? "引爆水雷，己方同坐标海域受到反击" : hit ? "击中舰船，获得一次追加攻击" : "未击中舰船",x,attackRow,null,List.of(),List.of(),List.of(),false,backfire));
            }
        }
        boolean selfDestroyed = from.ships.stream().allMatch(NavalEngine::sunk);
        if (target.ships.stream().allMatch(NavalEngine::sunk) || selfDestroyed) {
            state.phase = "FINISHED"; state.winner = selfDestroyed ? 1-actor : actor; state.finishedAt = now; state.deadline = 0; state.finishReason = "DESTROYED";
            event(state, state.winner, "victory", List.of(), selfDestroyed ? "水雷反击击毁了攻击方全部舰船" : "对方舰队已全部击毁");
        } else {
            if (!hit) state.turn = 1 - actor;
            state.deadline = now + TURN_MS; state.nextAiAt = now + AI_DELAY_MS;
            if (hit && !("AI".equals(state.mode) && actor == 1)) state.presentationUntil = 0;
            else waitForPresentation(state,now);
        }
    }

    public static void attack(NavalState state, int actor, Action action, long now) {
        NavalReconnect.requirePlaying(state);
        if ("MILITARY".equals(state.ruleSet)) { NavalMilitary.attack(state,actor,action,now); return; }
        if (state.presentationUntil>0) bad("请等待战斗动画播放完毕");
        if (!"submarine".equals(action.weapon)) { attack(state,actor,action.weapon,action.x,action.y,now); return; }
        if (!"BATTLE".equals(state.phase) || state.turn != actor) bad("尚未轮到你攻击");
        List<Cell> hull = action.targets;
        if (hull == null || hull.size() != 3 || hull.stream().anyMatch(cell -> cell == null || !inside(cell.x(),cell.y())) || new HashSet<>(hull).size() != 3) bad("请选择三个不同的有效格子");
        hull = new ArrayList<>(hull); hull.sort(java.util.Comparator.comparingInt(Cell::y).thenComparingInt(Cell::x));
        Cell first = hull.get(0), last = hull.get(2);
        if (!(hull.stream().allMatch(cell -> cell.y() == first.y()) && last.x()-first.x() == 2) &&
                !(hull.stream().allMatch(cell -> cell.x() == first.x()) && last.y()-first.y() == 2)) bad("潜艇占三格，请选择连续一行或一列的三个格子");
        Player from = state.players.get(actor), target = state.players.get(1-actor);
        if (from.inventory.getOrDefault("submarine",0) < 1) bad("潜艇已经用完");
        from.inventory.compute("submarine",(key,count) -> count-1);
        List<Cell> route = new ArrayList<>(); List<List<Cell>> torpedoes = new ArrayList<>(); List<Shot> outcomes = new ArrayList<>();
        boolean destroyed = false, hit = false;
        for (int shift=-last.x();shift<=0 && !destroyed;shift++) {
            route.add(new Cell(first.x()+shift,first.y()));
            for (Cell cell : hull) {
                Cell position = new Cell(cell.x()+shift,cell.y());
                if (!inside(position.x(),position.y()))continue;
                boolean scrap=target.scrapMines.remove(position);if(!scrap&&!target.mines.remove(position))continue;
                if(!scrap)target.inventory.compute("mine",(key,count) -> Math.max(0,(count == null ? 0 : count)-1));
                for (Cell part : hull) {
                    int x=part.x()+shift, y=part.y();
                    if (!inside(x,y))continue;
                    boolean extraScrap=target.scrapMines.remove(new Cell(x,y));Ship current=shipAt(target,x,y);if(shotAt(target,x,y)!=null&&!Boolean.TRUE.equals(shotAt(target,x,y).uncertain())&&(current==null||current.hits.contains(x+","+y))&&!position.equals(new Cell(x,y))&&!extraScrap)continue;
                    boolean mine=position.equals(new Cell(x,y)) || extraScrap || target.mines.remove(new Cell(x,y));
                    if (mine && !extraScrap && !position.equals(new Cell(x,y))) target.inventory.compute("mine",(key,count) -> Math.max(0,count-1));
                    Ship ship=shipAt(target,x,y);
                    if (ship!=null) { if(!ship.hits.contains(x+","+y))ship.hits.add(x+","+y); hit=true; }
                    Shot shot=new Shot(x,y,mine ? "mine" : ship!=null ? "hit" : "miss",extraScrap||scrap&&position.equals(new Cell(x,y))?"scrap":null);
                    outcomes.add(shot); NavalRepairSupport.recordShot(target,shot);
                }
                destroyed = true; break;
            }
        }
        if (!destroyed) for (int direction : new int[]{-1,1}) {
            Cell origin = hull.get(RANDOM.nextInt(3)); List<Cell> path = new ArrayList<>(); path.add(origin);
            for (int row=origin.y()+direction;inside(origin.x(),row);row+=direction) {
                Cell cell = new Cell(origin.x(),row); path.add(cell);
                Ship ship = shipAt(target,cell.x(),cell.y()); boolean scrap=target.scrapMines.contains(cell),mine = target.mines.contains(cell)||scrap;
                if (shotAt(target,cell.x(),cell.y()) == null||Boolean.TRUE.equals(shotAt(target,cell.x(),cell.y()).uncertain())||mine||ship!=null&&!ship.hits.contains(cell.x()+","+cell.y())) {
                    String result = mine ? "mine" : ship != null ? "hit" : "miss";
                    if (ship != null) { if(!ship.hits.contains(cell.x()+","+cell.y()))ship.hits.add(cell.x()+","+cell.y()); hit = true; }
                    if (mine) { target.scrapMines.remove(cell);target.mines.remove(cell); if(!scrap)target.inventory.compute("mine",(key,count) -> Math.max(0,(count == null ? 0 : count)-1)); }
                    Shot shot = new Shot(cell.x(),cell.y(),result,scrap?"scrap":null); outcomes.add(shot); NavalRepairSupport.recordShot(target,shot);
                }
                if (ship != null || mine) break;
            }
            torpedoes.add(path);
        }
        state.events.add(new Event(++state.sequence,actor,"submarine",outcomes,destroyed ? "潜艇触雷，被炸毁，未能发射鱼雷" : hit ? "潜艇鱼雷命中，获得一次追加攻击" : "潜艇完成上下双向鱼雷攻击",first.x(),first.y(),null,hull,route,torpedoes,destroyed));
        if (target.ships.stream().allMatch(NavalEngine::sunk)) {
            state.phase="FINISHED"; state.winner=actor; state.finishedAt=now; state.deadline=0; state.finishReason="DESTROYED";
            event(state,actor,"victory",List.of(),"对方舰队已全部击毁");
        } else {
            state.turn=hit ? actor : 1-actor; state.deadline=now+TURN_MS; state.nextAiAt=now+AI_DELAY_MS;
            if (hit && !("AI".equals(state.mode) && actor == 1)) state.presentationUntil = 0;
            else waitForPresentation(state,now);
        }
    }

    private static void aiAttack(NavalState state, long now) {
        NavalAi.attack(state,now);
    }

    public static void chat(NavalState state, int actor, String text, long now) {
        if ("AI".equals(state.mode)) bad("人机对局无需战局聊天");
        Player sender = state.players.get(actor);
        chatMessage(state,actor,sender.name,sender.id,text,now);
    }

    static void chatMessage(NavalState state,int actor,String name,String senderId,String text,long now){
        if (text == null || text.isBlank() || text.length() > 300) bad("聊天消息需要 1 到 300 个字符");
        normalizeMessages(state);
        Message previous = state.messages.isEmpty() ? null : state.messages.get(state.messages.size()-1);
        if (previous != null && senderId.equals(previous.senderId()) && now - previous.at() < 750) bad("消息发送太快，请稍后再试");
        state.messages.add(new Message(++state.sequence, actor, text.trim(), now,name,senderId));
        if (state.messages.size() > 100) state.messages.remove(0);
    }

    private static void normalizeMessages(NavalState state) {
        for (int index=0;index<state.messages.size();index++) {
            Message message = state.messages.get(index);
            if (message.name() != null && message.senderId() != null) continue;
            Player player = message.player() >= 0 && message.player() < state.players.size() ? state.players.get(message.player()) : null;
            String name = player == null ? "指挥官" : player.name, senderId = player == null ? null : player.id;
            // Old pre-settlement chat may outlive a replaced seat; prefer its original participant.
            if (state.settlement != null && message.at() <= state.finishedAt && state.settlement.get("players") instanceof List<?> participants &&
                    message.player() >= 0 && message.player() < participants.size() && participants.get(message.player()) instanceof Map<?,?> participant) {
                if (participant.get("name") instanceof String originalName) name = originalName;
                if (participant.get("id") instanceof String originalId) senderId = originalId;
            }
            state.messages.set(index,new Message(message.id(),message.player(),message.text(),message.at(),
                    message.name() == null ? name : message.name(),message.senderId() == null ? senderId : message.senderId(),message.bubbleStyle(),message.bubbleExpiresAt()));
        }
    }

    public static void leave(NavalState state, int actor, long now) {
        if(!state.localSimulation&&"BATTLE".equals(state.phase)){
            if(NavalReconnect.disconnect(state,actor,now)&&"BATTLE".equals(state.phase))event(state,actor,"reconnecting",List.of(),"玩家暂时离开，等待 60 秒重连");
            return;
        }
        finishLeave(state,actor,now);
    }

    static void finishLeave(NavalState state,int actor,long now){
        Player departing = state.players.get(actor); departing.left = true; departing.continueRequested = false;
        if ("FINISHED".equals(state.phase)) return;
        boolean cancelled = !battleStarted(state);
        state.phase = "FINISHED"; state.winner = !cancelled && state.players.size() == 2 ? 1 - actor : null;
        state.deadline = 0; state.finishedAt = now; state.finishReason = cancelled ? "CANCELLED" : "PLAYER_LEFT";
        state.battlePausedAt=0;
        event(state, actor, "leave", List.of(), "玩家已离开战局");
    }

    static void finishDisconnected(NavalState state,int actor,long now,String text){
        finishLeave(state,actor,now);state.finishReason="DISCONNECTED";
        event(state,actor,"disconnected",List.of(),text);
    }

    public static boolean battleStarted(NavalState state) {
        return state.battleStarted || state.events.stream().anyMatch(event -> "start".equals(event.weapon()));
    }

    static void allowSpectators(NavalState state, boolean allowed) {
        if ("BATTLE".equals(state.phase) && (state.allowSpectators || allowed)) state.taskSpectatorsOpened = true;
        state.allowSpectators = allowed;
        if (!allowed) state.spectators.clear();
    }

    public static Map<String, Object> view(NavalState state, int you, long now) {
        return view(state,you,"PLAYER",null,null,now);
    }

    public static Map<String, Object> view(NavalState state, int you, String role, String viewMode, String spectatorId, long now) {
        boolean spectator = "SPECTATOR".equals(role), both = spectator && "BOTH".equals(viewMode);
        int viewAs = spectator ? "PLAYER_1".equals(viewMode) ? 1 : 0 : you;
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("rulesVersion",state.rulesVersion);
        view.put("ruleSet",state.ruleSet);
        view.put("id",state.id); view.put("inviteCode",state.inviteCode); view.put("mode",state.mode);
        view.put("autoMatch",state.autoMatch); view.put("phase",state.phase); view.put("deadline",state.deadline);
        view.put("allowSpectators",state.allowSpectators); view.put("lobbyShared",state.lobbySharedRound==state.round);
        view.put("spectatorSettings",NavalSpectators.metadata(state));
        view.put("pause",NavalReconnect.metadata(state));
        view.put("presentationUntil",state.presentationUntil); view.put("presentationId",state.presentationId);
        view.put("supportActions",state.supportActions);view.put("supportShipId",both||viewAs==state.turn?state.supportShipId:null);view.put("mineActions",state.mineActions);view.put("turn",state.turn); view.put("you",you); view.put("winner",state.winner); view.put("serverTime",now); view.put("finishedAt",state.finishedAt);
        view.put("role",role); view.put("viewMode",spectator ? viewMode : "PLAYER_"+you); view.put("viewAs",viewAs);
        view.put("ownSpectatorId",spectatorId); view.put("round",state.round); view.put("gameId",state.id+":"+state.round); view.put("finishReason",state.finishReason);
        view.put("battleStarted",battleStarted(state));view.put("settlement",state.settlement);view.put("wager",state.wager);view.put("wagerPot",state.wagerPot);view.put("nextWager",you>=0&&you<state.players.size()?state.players.get(you).nextWager:state.nextWager);
        view.put("lastResult",state.settlement);
        int resultYou = -1;
        if (!spectator && you >= 0 && state.settlement != null && state.settlement.get("players") instanceof List<?> participants) {
            for (int i=0;i<participants.size();i++) if (participants.get(i) instanceof Map<?,?> participant && state.players.get(you).id.equals(participant.get("id"))) resultYou = i;
        }
        view.put("resultYou",resultYou);
        List<Map<String,Object>> players = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            Player player = i < state.players.size() ? state.players.get(i) : player(null,"空席");
            boolean occupied = player.key != null && !player.left;
            boolean visible = both || i == viewAs || "FINISHED".equals(state.phase);
            Map<String,Object> member = new LinkedHashMap<>();
            member.put("id",player.id); member.put("name",player.name); member.put("ready",player.ready); member.put("stage",player.stage);
            member.put("occupied",occupied); member.put("left",!occupied); member.put("connected",occupied && player.disconnectedAt == 0 && (!state.connectionTracking || now-player.lastSeenAt<15_000)); member.put("continueRequested",player.continueRequested);
            member.put("reconnectUntil",occupied && player.disconnectedAt > 0 ? player.disconnectedAt+NavalReconnect.GRACE_MS : 0);
            member.put("reconnectCount",NavalReconnect.used(state,player));member.put("reconnectRemaining",NavalReconnect.remaining(state,player));
            member.put("medals",player.medals); member.put("registered",player.registered);member.put("wager",player.wager); member.put("avatarUrl",player.registered ? player.avatarUrl : null);
            member.put("progression",player.progression);
            member.put("nextWager",player.nextWager);member.put("canSkipWager",player.canSkipWager);
            member.put("totalShips",player.ships.size());
            member.put("ships",visible ? player.ships : player.ships.stream().filter(NavalEngine::sunk).toList());
            member.put("scrapMines",visible?player.scrapMines:List.of());member.put("shots",player.shots); member.put("mines",visible ? player.mines : List.of());
            member.put("aa",visible ? player.aa : List.of()); member.put("inventory",visible ? player.inventory : emptyInventory());
            member.put("coins",visible ? player.coins : 0); member.put("radar",visible ? player.radar : List.of()); players.add(member);
        }
        view.put("players",players); view.put("messages",state.messages.stream().map(message->message.visible(now)).toList()); view.put("events",state.events.subList(Math.max(0,state.events.size()-100),state.events.size()).stream().map(e->{if(e.support()==null||both||e.player()==viewAs||"FINISHED".equals(state.phase))return e;return new Event(e.id(),e.player(),e.weapon(),List.of(),e.text(),null,null,null,List.of(),List.of(),List.of(),false,List.of(),null,0,Map.of("mode",e.support().get("mode")));}).toList());
        view.put("radar",viewAs < state.players.size() ? state.players.get(viewAs).radar : List.of());
        view.put("spectators",state.spectators.stream().map(person -> {
            Map<String,Object> personView = new LinkedHashMap<>(); personView.put("id",person.id); personView.put("name",person.name);
            personView.put("medals",person.medals);personView.put("avatarUrl",person.registered ? person.avatarUrl : null); personView.put("registered",person.registered); personView.put("viewMode",person.viewMode); return personView;
        }).toList());
        return view;
    }

    private static void event(NavalState state, int actor, String weapon, List<Shot> cells, String text) {
        event(state, actor, weapon, cells, text, null, null, null);
    }

    private static void event(NavalState state, int actor, String weapon, List<Shot> cells, String text,
                              Integer x, Integer y, Integer row) {
        state.events.add(new Event(++state.sequence,actor,weapon,cells,text,x,y,row));
    }

    private static Shot shotAt(Player player, int x, int y) { return player.shots.stream().filter(s -> s.x() == x && s.y() == y).findFirst().orElse(null); }
    private static Ship shipAt(Player player, int x, int y) { return player.ships.stream().filter(s -> x >= s.x && x < s.x+s.w && y >= s.y && y < s.y+s.h).findFirst().orElse(null); }
    public static boolean sunk(Ship ship) { return ship.hits != null && ship.hits.size() == ship.w * ship.h; }
    private static boolean inside(int x,int y) { return x >= 0 && x < SIZE && y >= 0 && y < SIZE; }
    static void bad(String message) { throw new BusinessException(ErrorCode.BAD_REQUEST,message); }
}
