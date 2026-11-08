package com.navy.game;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.navy.game.NavalState.Player;
import com.navy.game.NavalState.Ship;
import java.security.SecureRandom;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.function.Supplier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Account wallet and rank rewards are committed in the room's settlement transaction. */
public final class NavalProgression {
    private static final ZoneOffset ZONE = ZoneOffset.ofHours(8);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final List<String> CLASSIC = List.of("antiAir", "fighter", "bomber", "torpedo");
    private static final List<String> MILITARY = List.of("carrier", "destroyer", "repairTug", "submarine");
    private static final List<String> AI_CLASSIC = List.of("fighter", "torpedo", "antiAir");
    private static final List<String> SURVIVORS = List.of("patrol", "destroyer", "cruiser");
    private static final int FIXED_TASKS = 4 | 8;
    private static final int AI_POOL = 16 | 32 | 64 | 128;
    private static final int REGULAR_POOL = 1 | 2 | 256 | 512 | 1024 | 2048;
    private static final int[] MAX = {300, 600, 1000, 1500, 2000, 2800, 3800, 4800, 6000, 7500, 9000, 12000};
    private static final List<String> RANKS = List.of("下士", "中士", "上士", "少尉", "中尉", "上尉", "少校", "中校", "上校", "少将", "中将", "上将");
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final Clock clock;
    private final Random random;

    NavalProgression(JdbcTemplate jdbc, ObjectMapper json) { this(jdbc, json, Clock.systemUTC()); }
    NavalProgression(JdbcTemplate jdbc, ObjectMapper json, Clock clock) {
        this(jdbc, json, clock, RANDOM);
    }
    NavalProgression(JdbcTemplate jdbc, ObjectMapper json, Clock clock, Random random) {
        this.jdbc = jdbc;
        this.json = json;
        this.clock = clock;
        this.random = random;
    }

    private <T> T transaction(Supplier<T> work) {
        if (jdbc.getDataSource() == null) return work.get();
        return new TransactionTemplate(new DataSourceTransactionManager(jdbc.getDataSource())).execute(status -> work.get());
    }

    Map<String, Object> profile(String key) {
        if (!account(key)) NavalEngine.bad("请先登录账户");
        return transaction(() -> {
            LocalDate today = day(clock.millis());
            Row row = lock(key);
            Daily daily = daily(key, today);
            Map<String, Object> result = summary(row.score, row.promotionWins);
            result.put("coins", BigDecimal.valueOf(row.coins).add(BigDecimal.valueOf(row.coinFraction,2)));
            result.put("coinsWhole", row.coins);
            result.put("defaultWager", NavalWagers.defaultForLevel(level(row.score)));
            result.put("day", today.toString());
            result.put("resetsAt", today.plusDays(1).atStartOfDay().toInstant(ZONE).toEpochMilli());
            result.put("tasks", taskViews(daily,today));
            List<Map<String,Object>> pending=new ArrayList<>();
            for(Map<String,Object> old:jdbc.queryForList("SELECT * FROM naval_daily_tasks WHERE player_key=? AND task_day<? AND (completed_tasks & ~claimed_tasks)<>0 ORDER BY task_day",key,today.toString())){
                LocalDate date=LocalDate.parse(old.get("task_day").toString());
                taskViews(readDaily(old),date).stream().filter(task->Boolean.TRUE.equals(task.get("canClaim"))).forEach(pending::add);
            }
            for(var reward:jdbc.queryForList("SELECT id,task_day,reward_json FROM naval_task_reset_rewards WHERE player_key=? AND claimed_at IS NULL ORDER BY task_day,id",key))pending.add(archivedView(reward));
            result.put("pendingTasks",pending);
            result.put("claimableCount",pending.size()+Integer.bitCount(daily.completed & ~daily.claimed & daily.selected));
            return result;
        });
    }

    Map<String,Object> claim(String key,String taskId,String requestedDay){
        if(!account(key))NavalEngine.bad("请先登录账户");
        return transaction(()->{
            if(taskId!=null&&taskId.matches("reset-[0-9]+"))return claimArchived(key,taskId);
            LocalDate date;
            try{date=requestedDay==null||requestedDay.isBlank()?day(clock.millis()):LocalDate.parse(requestedDay);}catch(Exception e){throw new com.navy.common.exception.BusinessException(400,"任务日期无效");}
            if(date.isAfter(day(clock.millis())))NavalEngine.bad("任务日期无效");
            Row row=lock(key);
            Daily daily=daily(key,date);
            String[] parts=Objects.toString(taskId,"").split("~",-1);int requestedCycle;
            try{if(parts.length>2)throw new IllegalArgumentException();requestedCycle=parts.length==1?0:Integer.parseInt(parts[1]);}catch(Exception e){throw new com.navy.common.exception.BusinessException(400,"任务标识无效");}
            if(requestedCycle!=daily.cycle)throw new com.navy.common.exception.BusinessException(409,"任务已经重置，请刷新后领取");
            Task task=tasks(daily).stream().filter(item->item.id.equals(parts[0])).findFirst().orElse(null);
            if(task==null){
                var archived=jdbc.queryForList("SELECT id FROM naval_task_reset_rewards WHERE player_key=? AND task_day=? AND task_cycle=? AND task_id=?",key,date.toString(),daily.cycle,taskId);
                if(!archived.isEmpty())return claimArchived(key,"reset-"+archived.get(0).get("id"));
                throw new com.navy.common.exception.BusinessException(404,"任务不存在");
            }
            if((daily.completed & task.bit)==0)NavalEngine.bad("任务尚未完成");
            if((daily.claimed & task.bit)==0){
                var advance=advance(row.score,row.promotionWins,false,false,0,List.of(task.score),false);
                row.score=advance.score;row.promotionWins=advance.promotionWins;row.coins=Math.addExact(row.coins,task.coins);
                save(key,row);
                jdbc.update("UPDATE naval_daily_tasks SET claimed_tasks=claimed_tasks|? WHERE player_key=? AND task_day=?",task.bit,key,date.toString());
            }
            return profile(key);
        });
    }

    private static List<Map<String,Object>> taskViews(Daily daily,LocalDate day){
        return tasks(daily).stream().map(task->{
            boolean completed=(daily.completed&task.bit)!=0,claimed=(daily.claimed&task.bit)!=0;
            Map<String,Object> view=task.view(completed);view.put("id",task.id+(daily.cycle==0?"":"~"+daily.cycle));view.put("cycle",daily.cycle);view.put("claimed",claimed);view.put("canClaim",completed&&!claimed);view.put("day",day.toString());return view;
        }).toList();
    }

    private Map<String,Object> archivedView(Map<String,Object> row){
        var view=decode(row.get("reward_json").toString());view.put("id","reset-"+row.get("id"));view.put("day",row.get("task_day").toString());view.put("claimed",false);view.put("canClaim",true);return view;
    }

    private Map<String,Object> claimArchived(String key,String taskId){
        long id;
        try{id=Long.parseLong(taskId.substring(6));}catch(Exception e){throw new com.navy.common.exception.BusinessException(400,"任务标识无效");}
        Row wallet=lock(key);
        var rows=jdbc.queryForList("SELECT * FROM naval_task_reset_rewards WHERE id=? AND player_key=? FOR UPDATE",id,key);
        if(rows.isEmpty())throw new com.navy.common.exception.BusinessException(404,"任务不存在");
        var receipt=rows.get(0);
        if(receipt.get("claimed_at")==null){
            var reward=decode(receipt.get("reward_json").toString());
            int score=((Number)reward.get("score")).intValue(),coins=((Number)reward.get("coins")).intValue();
            var next=advance(wallet.score,wallet.promotionWins,false,false,0,List.of(score),false);
            wallet.score=next.score;wallet.promotionWins=next.promotionWins;wallet.coins=Math.addExact(wallet.coins,coins);save(key,wallet);
            jdbc.update("UPDATE naval_task_reset_rewards SET claimed_at=? WHERE id=? AND player_key=?",clock.millis(),id,key);
        }
        return profile(key);
    }

    void resetTasks(String key){
        transaction(()->{
            lock(key);LocalDate date=day(clock.millis());Daily daily=daily(key,date);
            for(var view:taskViews(daily,date))if(Boolean.TRUE.equals(view.get("canClaim")))archiveTask(key,date,daily.cycle,view);
            String classic=choose(CLASSIC.stream().filter(value->!value.equals(daily.classic)).toList()),military=choose(MILITARY.stream().filter(value->!value.equals(daily.military)).toList());
            jdbc.update("UPDATE naval_daily_tasks SET classic_restriction=?,military_restriction=?,selected_tasks=?,ai_classic_restriction=?,ai_survival=?,assignment_version=1,completed_tasks=0,claimed_tasks=0,ai_classic_streak=0,task_cycle=task_cycle+1 WHERE player_key=? AND task_day=?",classic,military,selectedTaskMask(random),choose(AI_CLASSIC),choose(SURVIVORS),key,date.toString());
            return true;
        });
    }

    void useScoreCard(String key){
        transaction(()->{
            Row row=lock(key);var next=advance(row.score,row.promotionWins,false,false,0,List.of(100),false);
            if(next.taskScores.get(0)!=100)throw new com.navy.common.exception.BusinessException(409,row.score>=12000?"已达到最高等级分":"当前无法完整增加100分，请先完成晋升考核后再使用");
            row.score=next.score;row.promotionWins=next.promotionWins;save(key,row);return true;
        });
    }

    Map<String, Object> credit(NavalState state) {
        if (!settleable(state)) return Map.of("players", List.of(), "reason", "INELIGIBLE");
        return transaction(() -> creditLocked(state));
    }

    private Map<String, Object> creditLocked(NavalState state) {
        boolean natural = naturalVictory(state);
        LocalDate finishedDay = day(state.finishedAt);
        String gameId = state.id + ":" + state.round;
        Map<String, Row> rows = new LinkedHashMap<>();
        // Two concurrent rooms involving the same accounts must acquire locks in one order.
        state.players.stream().filter(p -> p.registered && account(p.key)).map(p -> p.key).distinct()
            .sorted(Comparator.naturalOrder()).forEach(key -> rows.put(key, lock(key)));
        List<Map<String, Object>> rewards = new ArrayList<>();
        for (int seat = 0; seat < 2; seat++) {
            Player player = state.players.get(seat);
            Row row = rows.get(player.key);
            if (row == null) {
                rewards.add(Map.of("seat", seat, "eligible", false, "scoreAwarded", 0, "coinsAwarded", 0));
                continue;
            }
            var receipts = jdbc.queryForList("SELECT reward_json FROM naval_progression_rounds WHERE game_id=? AND player_key=?", String.class, gameId, player.key);
            if (!receipts.isEmpty()) {
                Map<String, Object> reward = decode(receipts.get(0));
                reward.put("seat", seat);
                rewards.add(reward);
                continue;
            }
            boolean won = state.winner == seat;
            Daily daily = daily(player.key, finishedDay);
            boolean tasksEligible = tasksEligible(state);
            if (aiClassic(state)) daily.aiClassicStreak = nextAiClassicStreak(daily.aiClassicStreak, natural && tasksEligible, won);
            List<Task> completed = new ArrayList<>();
            if (natural && won && tasksEligible) {
                for (Task task : tasks(daily)) {
                    if ((daily.completed & task.bit) == 0 && taskCompleted(task, state, seat, daily)) {
                        daily.completed |= task.bit;
                        completed.add(task);
                    }
                }
            }
            int coins = 0;
            var advance = advance(row.score, row.promotionWins, natural, won,
                natural ? matchScore(state, seat) : 0, List.of(), "MULTIPLAYER".equals(state.mode));
            row.score = advance.score;
            row.promotionWins = advance.promotionWins;
            row.coins = Math.addExact(row.coins, coins);
            save(player.key, row);
            jdbc.update("UPDATE naval_daily_tasks SET completed_tasks=?,ai_classic_streak=? WHERE player_key=? AND task_day=?", daily.completed, daily.aiClassicStreak, player.key, finishedDay.toString());
            Map<String, Object> reward = new LinkedHashMap<>();
            reward.put("seat", seat);
            reward.put("eligible", natural);
            reward.put("matchScore", advance.matchScore);
            reward.put("taskScore", advance.taskScores.stream().mapToInt(Integer::intValue).sum());
            reward.put("scoreAwarded", advance.matchScore + advance.taskScores.stream().mapToInt(Integer::intValue).sum());
            reward.put("coinsAwarded", coins);
            reward.put("score", row.score);
            reward.put("level", level(row.score));
            reward.put("rank", RANKS.get(level(row.score) - 1));
            reward.put("promotion", promotion(row.score, row.promotionWins));
            reward.put("promoted", advance.promoted);
            List<Map<String, Object>> completedViews = new ArrayList<>();
            for (int i = 0; i < completed.size(); i++) {
                Map<String, Object> view = completed.get(i).view(true);
                view.put("id",completed.get(i).id+(daily.cycle==0?"":"~"+daily.cycle));view.put("cycle",daily.cycle);
                view.put("claimed",false);view.put("canClaim",true);view.put("day",finishedDay.toString());
                completedViews.add(view);
            }
            reward.put("completedTasks", completedViews);
            jdbc.update("INSERT INTO naval_progression_rounds(game_id,player_key,reward_json,credited_at) VALUES(?,?,?,?)", gameId, player.key, encode(reward), clock.millis());
            rewards.add(reward);
        }
        return Map.of("players", rewards, "reason", natural ? "NORMAL" : "EXIT_NO_REWARDS");
    }

    static boolean settleable(NavalState state) {
        boolean multiplayer = "MULTIPLAYER".equals(state.mode) && state.players.stream().noneMatch(p -> "AI".equals(p.key));
        boolean ai = "AI".equals(state.mode) && state.players.stream().filter(p -> "AI".equals(p.key)).count() == 1
            && state.players.stream().filter(p -> p.registered && account(p.key)).count() == 1;
        return !state.localSimulation && (multiplayer || ai) && "FINISHED".equals(state.phase)
            && NavalEngine.battleStarted(state) && state.finishedAt > 0 && state.winner != null
            && (state.winner == 0 || state.winner == 1) && state.players.size() == 2
            && !Objects.equals(state.players.get(0).key, state.players.get(1).key);
    }

    static boolean naturalVictory(NavalState state) {
        return settleable(state) && List.of("DESTROYED", "MILITARY_DEFEAT").contains(Objects.toString(state.finishReason, ""))
            && state.players.stream().noneMatch(p -> p.left);
    }

    static int matchScore(NavalState state, int seat) {
        if (!"MULTIPLAYER".equals(state.mode) || !naturalVictory(state)) return 0;
        if (state.winner == seat) return 5;
        Player winner = state.players.get(state.winner);
        if ("MILITARY".equals(state.ruleSet)) {
            long remaining = winner.ships.stream().filter(NavalProgression::canCounterattack).count();
            return remaining == 1 ? 1 : 0;
        }
        long remaining = winner.ships.stream().filter(ship -> !NavalEngine.sunk(ship)).count();
        return remaining == 1 ? 2 : remaining == 2 ? 1 : 0;
    }

    static boolean canCounterattack(Ship ship) {
        return NavalMilitary.MODELS.containsKey(Objects.toString(ship.kind, "")) && NavalMilitary.parts(ship).stream()
            .anyMatch(part -> part.active() && List.of("shot", "fighter", "bomber", "torpedo", "submarine", "depthCharge").contains(part.role()));
    }

    static boolean unusedClassic(NavalState state, int seat, String item) {
        if ("antiAir".equals(item)) return state.events.stream().noneMatch(event -> event.player() == 1 - seat && event.row() != null
            && List.of("fighter", "bomber", "torpedo").contains(event.weapon()));
        return state.events.stream().noneMatch(event -> event.player() == seat && item.equals(event.weapon()));
    }

    static boolean absentMilitary(NavalState state, int seat, String category) {
        return state.players.get(seat).ships.stream().noneMatch(ship -> switch (category) {
            case "carrier" -> List.of("carrier", "bomberCarrier", "torpedoCarrier").contains(ship.kind);
            case "destroyer" -> List.of("antiAir", "torpedo", "radar", "mineLayer", "jammer", "depthCharge").contains(ship.kind);
            default -> category.equals(ship.kind);
        });
    }

    static boolean taskCompleted(Task task, NavalState state, int seat, Daily daily) {
        boolean military = "MILITARY".equals(state.ruleSet);
        boolean multiplayer = "MULTIPLAYER".equals(state.mode);
        return switch (task.id) {
            case "classic-win" -> multiplayer && !military;
            case "military-win" -> multiplayer && military;
            case "classic-restriction" -> multiplayer && !military && unusedClassic(state, seat, task.restriction);
            case "military-restriction" -> multiplayer && military && absentMilitary(state, seat, task.restriction);
            case "ai-classic-win" -> aiClassic(state);
            case "ai-classic-streak" -> aiClassic(state) && daily.aiClassicStreak >= 3;
            case "ai-classic-restriction" -> aiClassic(state) && unusedClassic(state, seat, task.restriction);
            case "ai-classic-survival" -> aiClassic(state) && survivingClassic(state, seat, task.restriction);
            case "classic-economy" -> multiplayer && !military && economicalVictory(state, seat);
            case "military-economy" -> multiplayer && military && economicalVictory(state, seat);
            case "ai-military-economy" -> "AI".equals(state.mode) && military && economicalVictory(state, seat);
            case "ai-classic-economy" -> aiClassic(state) && economicalVictory(state, seat);
            default -> false;
        };
    }

    static boolean tasksEligible(NavalState state) { return state.taskEligibilityVersion == 1 && !state.taskSpectatorsOpened; }

    static boolean economicalVictory(NavalState state, int seat) {
        // Both rulesets keep the authoritative unspent round budget on the player.
        return (long) state.players.get(seat).coins - state.players.get(1 - seat).coins >= 30;
    }

    static boolean survivingClassic(NavalState state, int seat, String category) {
        int size = switch (category) { case "patrol" -> 1; case "destroyer" -> 2; case "cruiser" -> 3; default -> 0; };
        if (size == 0) return false;
        return state.players.get(seat).ships.stream().filter(ship -> ship.w * ship.h == size && !NavalEngine.sunk(ship)).count() >= 4 - size;
    }

    static boolean aiClassic(NavalState state) { return "AI".equals(state.mode) && "CLASSIC".equals(state.ruleSet); }
    static int nextAiClassicStreak(int streak, boolean natural, boolean won) { return natural && won ? Math.min(3, streak + 1) : 0; }

    record Advance(int score, int promotionWins, int matchScore, List<Integer> taskScores, boolean promoted) { }

    static Advance advance(int score, int streak, boolean natural, boolean won, int match, List<Integer> taskScores) {
        return advance(score, streak, natural, won, match, taskScores, true);
    }

    static Advance advance(int score, int streak, boolean natural, boolean won, int match, List<Integer> taskScores, boolean countsForPromotion) {
        int required = requiredWins(score);
        if (required > 0) {
            int wins = !countsForPromotion ? streak : !won ? 0 : natural ? streak + 1 : streak;
            boolean promoted = countsForPromotion && wins >= required;
            return new Advance(promoted ? score + 1 : score, promoted ? 0 : wins, 0,
                taskScores.stream().map(value -> 0).toList(), promoted);
        }
        int ceiling = score < 1000 ? 1000 : score < 2800 ? 2800 : score < 6000 ? 6000 : 12000;
        int matchAward = Math.max(0, Math.min(match, ceiling - score));
        int next = score + matchAward;
        List<Integer> awards = new ArrayList<>();
        for (int taskScore : taskScores) {
            int award = Math.max(0, Math.min(taskScore, ceiling - next));
            awards.add(award);
            next += award;
        }
        return new Advance(next, 0, matchAward, awards, false);
    }

    public static int level(int score) {
        for (int i = 0; i < MAX.length; i++) if (score <= MAX[i]) return i + 1;
        return MAX.length;
    }

    static int requiredWins(int score) {
        return switch (score) { case 1000 -> 5; case 2800 -> 10; case 6000 -> 16; default -> 0; };
    }

    static Map<String, Object> promotion(int score, int streak) {
        int required = requiredWins(score);
        return required == 0 ? null : Map.of("requiredWins", required, "currentWins", streak, "targetRank", RANKS.get(level(score)));
    }

    static Map<String, Object> summary(int score, int streak) {
        int level = level(score), min = level == 1 ? 0 : MAX[level - 2] + 1, max = MAX[level - 1];
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("score", score);
        result.put("level", level);
        result.put("rank", RANKS.get(level - 1));
        result.put("minScore", min);
        result.put("maxScore", max);
        result.put("nextScore", max);
        result.put("progress", Math.max(0, Math.min(1, (score - min) / (double) (max - min))));
        result.put("promotion", promotion(score, streak));
        return result;
    }

    static LocalDate day(long at) { return Instant.ofEpochMilli(at).atOffset(ZONE).toLocalDate(); }
    private static boolean account(String key) { return key != null && key.matches("U:[0-9]+"); }

    private Row lock(String key) {
        jdbc.update("INSERT INTO naval_progression_profiles(player_key) VALUES(?) ON DUPLICATE KEY UPDATE player_key=VALUES(player_key)", key);
        return Row.read(jdbc.queryForMap("SELECT * FROM naval_progression_profiles WHERE player_key=? FOR UPDATE", key));
    }

    private Daily daily(String key, LocalDate day) {
        jdbc.update("INSERT INTO naval_daily_tasks(player_key,task_day,classic_restriction,military_restriction,selected_tasks,ai_classic_restriction,ai_survival,assignment_version) VALUES(?,?,?,?,?,?,?,1) ON DUPLICATE KEY UPDATE player_key=VALUES(player_key)", key, day.toString(), choose(CLASSIC), choose(MILITARY), selectedTaskMask(random), choose(AI_CLASSIC), choose(SURVIVORS));
        Map<String, Object> data = jdbc.queryForMap("SELECT * FROM naval_daily_tasks WHERE player_key=? AND task_day=? FOR UPDATE", key, day.toString());
        Daily daily = readDaily(data);
        if (daily.assignmentVersion == 0 && day.equals(day(clock.millis()))) {
            int selected = selectedTaskMask(random);
            // Keep selected legacy progress; archive only rewards removed by the new draw.
            for (Task task : tasks(daily)) if ((selected & task.bit) == 0 && (daily.completed & ~daily.claimed & task.bit) != 0) {
                Map<String,Object> view = taskViews(daily,day).stream().filter(item -> task.id.equals(item.get("id")) || (task.id+"~"+daily.cycle).equals(item.get("id"))).findFirst().orElseThrow();
                archiveTask(key,day,daily.cycle,view);
                daily.claimed |= task.bit;
            }
            daily.selected = selected;
            daily.aiRestriction = choose(AI_CLASSIC);
            daily.survival = choose(SURVIVORS);
            daily.assignmentVersion = 1;
            jdbc.update("UPDATE naval_daily_tasks SET selected_tasks=?,ai_classic_restriction=?,ai_survival=?,assignment_version=1,claimed_tasks=? WHERE player_key=? AND task_day=?",daily.selected,daily.aiRestriction,daily.survival,daily.claimed,key,day.toString());
        }
        return daily;
    }

    private void archiveTask(String key,LocalDate day,int cycle,Map<String,Object> view) {
        jdbc.update("INSERT INTO naval_task_reset_rewards(player_key,task_day,task_cycle,task_id,reward_json) VALUES(?,?,?,?,?)",key,day.toString(),cycle,view.get("id"),encode(view));
    }

    private static Daily readDaily(Map<String,Object> data){
        Daily daily=new Daily(data.get("classic_restriction").toString(),data.get("military_restriction").toString(),((Number)data.get("completed_tasks")).intValue(),((Number)data.get("ai_classic_streak")).intValue());
        daily.claimed=data.get("claimed_tasks") instanceof Number value?value.intValue():0;
        daily.cycle=data.get("task_cycle") instanceof Number value?value.intValue():0;
        daily.selected=data.get("selected_tasks") instanceof Number value?value.intValue():63;
        daily.assignmentVersion=data.get("assignment_version") instanceof Number value?value.intValue():0;
        daily.aiRestriction=Objects.toString(data.get("ai_classic_restriction"),"fighter");
        daily.survival=Objects.toString(data.get("ai_survival"),"patrol");
        return daily;
    }

    private void save(String key, Row row) {
        jdbc.update("UPDATE naval_progression_profiles SET score=?,coins=?,promotion_wins=? WHERE player_key=?", row.score, row.coins, row.promotionWins, key);
    }

    private String choose(List<String> choices) { return choices.get(random.nextInt(choices.size())); }

    static int selectedTaskMask(Random random) {
        int selected = FIXED_TASKS;
        for (int pool : List.of(AI_POOL,REGULAR_POOL)) {
            List<Integer> choices = new ArrayList<>();
            for (int bit=1;bit<=2048;bit<<=1) if ((pool & bit) != 0) choices.add(bit);
            Collections.shuffle(choices,random);
            selected |= choices.get(0) | choices.get(1);
        }
        return selected;
    }
    private String encode(Object value) { try { return json.writeValueAsString(value); } catch (Exception e) { throw new IllegalStateException("Cannot encode naval rewards", e); } }
    private Map<String, Object> decode(String value) {
        try { return json.readValue(value, new TypeReference<LinkedHashMap<String, Object>>() { }); }
        catch (Exception e) { throw new IllegalStateException("Invalid naval reward receipt", e); }
    }

    private static final class Row {
        int score, promotionWins, coinFraction;
        long coins;
        static Row read(Map<String, Object> data) {
            Row row = new Row();
            row.score = ((Number) data.get("score")).intValue();
            row.coins = ((Number) data.get("coins")).longValue();
            row.coinFraction = data.get("coin_fraction") instanceof Number fraction ? fraction.intValue() : 0;
            row.promotionWins = ((Number) data.get("promotion_wins")).intValue();
            return row;
        }
    }

    static final class Daily {
        final String classic, military;
        String aiRestriction = "fighter", survival = "patrol";
        int completed, claimed, aiClassicStreak, cycle, selected = 63, assignmentVersion;
        Daily(String classic, String military, int completed, int aiClassicStreak) { this.classic = classic; this.military = military; this.completed = completed; this.aiClassicStreak = aiClassicStreak; }
    }

    record Task(String id, int bit, String title, int coins, int score, String restriction, int progress, int target) {
        Task(String id, int bit, String title, int coins, int score, String restriction) { this(id, bit, title, coins, score, restriction, 0, 1); }
        Map<String, Object> view(boolean completed) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("id", id);
            result.put("title", title);
            result.put("coins", coins);
            result.put("score", score);
            result.put("completed", completed);
            result.put("progress", completed ? target : progress);
            result.put("target", target);
            return result;
        }
    }

    static List<Task> tasks(Daily row) {
        String classicName = switch (row.classic) { case "antiAir" -> "防空炮"; case "fighter" -> "战斗机"; case "bomber" -> "轰炸机"; default -> "鱼雷机"; };
        String militaryName = switch (row.military) { case "carrier" -> "航母"; case "destroyer" -> "驱逐舰"; case "repairTug" -> "修理拖船"; default -> "潜艇"; };
        String aiName = switch (row.aiRestriction) { case "antiAir" -> "防空炮"; case "fighter" -> "战斗机"; default -> "鱼雷机"; };
        String survivorName = switch (row.survival) { case "destroyer" -> "2艘驱逐舰"; case "cruiser" -> "1艘巡洋舰"; default -> "3艘巡逻艇"; };
        int survivorReward = switch (row.survival) { case "destroyer" -> 30; case "cruiser" -> 40; default -> 20; };
        return List.of(new Task("classic-win", 1, "获胜一场人人经典模式", 10, 10, ""),
            new Task("military-win", 2, "获胜一场人人军事模式", 10, 10, ""),
            new Task("classic-restriction", 4, "不使用" + classicName + "获胜一场人人经典模式", 20, 20, row.classic),
            new Task("military-restriction", 8, "不部署" + militaryName + "获胜一场人人军事模式", 30, 30, row.military),
            new Task("ai-classic-win", 16, "获胜一场人机经典模式", 5, 5, ""),
            new Task("ai-classic-streak", 32, "人机经典模式连胜三场", 30, 30, "", row.aiClassicStreak, 3),
            new Task("ai-classic-restriction", 64, "不使用" + aiName + "获胜一场人机经典模式", 10, 10, row.aiRestriction),
            new Task("ai-classic-survival", 128, "至少存活" + survivorName + "获胜一场人机经典模式", survivorReward, survivorReward, row.survival),
            new Task("classic-economy", 256, "比对手少花至少30金币获胜一场人人经典模式", 20, 20, ""),
            new Task("military-economy", 512, "比对手少花至少30金币获胜一场人人军事模式", 20, 20, ""),
            new Task("ai-military-economy", 1024, "比对手少花至少30金币获胜一场人机军事模式", 10, 10, ""),
            new Task("ai-classic-economy", 2048, "比对手少花至少30金币获胜一场人机经典模式", 10, 10, ""))
            .stream().filter(task -> (row.selected & task.bit) != 0).toList();
    }
}
