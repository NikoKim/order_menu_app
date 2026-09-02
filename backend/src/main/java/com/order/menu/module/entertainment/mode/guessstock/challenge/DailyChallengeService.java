package com.order.menu.module.entertainment.mode.guessstock.challenge;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.order.menu.common.exception.BusinessException;
import com.order.menu.common.exception.ErrorCode;
import com.order.menu.module.entertainment.core.dto.EntertainmentDtos;
import com.order.menu.module.entertainment.core.entity.EntertainmentGame;
import com.order.menu.module.entertainment.core.entity.GameSession;
import com.order.menu.module.entertainment.core.entity.GameVersion;
import com.order.menu.module.entertainment.core.mapper.GameSessionMapper;
import com.order.menu.module.entertainment.core.service.EntertainmentGameService;
import com.order.menu.module.entertainment.core.service.PlayerIdentity;
import com.order.menu.module.entertainment.mode.guessstock.config.GuessStockConfig;
import com.order.menu.module.entertainment.mode.guessstock.credit.entity.CreditSnapshot;
import com.order.menu.module.entertainment.mode.guessstock.credit.entity.LoanLedger;
import com.order.menu.module.entertainment.mode.guessstock.credit.mapper.CreditSnapshotMapper;
import com.order.menu.module.entertainment.mode.guessstock.credit.mapper.LoanLedgerMapper;
import com.order.menu.module.entertainment.mode.guessstock.entity.GuessStockParticipant;
import com.order.menu.module.entertainment.mode.guessstock.entity.GuessStockRound;
import com.order.menu.module.entertainment.mode.guessstock.entity.GuessStockResearchAction;
import com.order.menu.module.entertainment.mode.guessstock.entity.GuessStockTrade;
import com.order.menu.module.entertainment.mode.guessstock.mapper.GuessStockParticipantMapper;
import com.order.menu.module.entertainment.mode.guessstock.mapper.GuessStockRoundMapper;
import com.order.menu.module.entertainment.mode.guessstock.mapper.GuessStockResearchActionMapper;
import com.order.menu.module.entertainment.mode.guessstock.mapper.GuessStockTradeMapper;
import com.order.menu.module.entertainment.mode.guessstock.service.GuessStockSessionService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DailyChallengeService {
    private final GuessStockChallengeMapper challengeMapper;
    private final GuessStockChallengeEntryMapper entryMapper;
    private final EntertainmentGameService gameService;
    private final GuessStockSessionService sessionService;
    private final GameSessionMapper sessionMapper;
    private final GuessStockParticipantMapper participantMapper;
    private final GuessStockRoundMapper roundMapper;
    private final GuessStockTradeMapper tradeMapper;
    private final CreditSnapshotMapper snapshotMapper;
    private final LoanLedgerMapper ledgerMapper;
    private final GuessStockResearchActionMapper researchActionMapper;
    private final ObjectMapper objectMapper;

    @Transactional
    public ChallengeResponse daily(Long gameId) {
        EntertainmentGame game = gameService.requirePlayable(gameId);
        GameVersion version = gameService.requireVersion(game.getCurrentVersionId());
        GuessStockConfig config = gameService.readConfig(version.getConfigJson());
        if (!Boolean.TRUE.equals(config.dailyChallengeEnabled())) bad("该游戏未开启每日挑战");
        LocalDate today = LocalDate.now();
        GuessStockChallenge challenge = challengeMapper.selectOne(new LambdaQueryWrapper<GuessStockChallenge>()
                .eq(GuessStockChallenge::getChallengeDate, today)
                .eq(GuessStockChallenge::getGameVersionId, version.getId()).last("LIMIT 1"));
        if (challenge == null) {
            DailyChallengeEngine.Challenge generated = DailyChallengeEngine.create(today, version.getId(), "DAILY_V1");
            challenge = new GuessStockChallenge();
            challenge.setChallengeDate(today);
            challenge.setGameVersionId(version.getId());
            challenge.setRulesVersion(generated.rulesVersion());
            challenge.setScenarioSeed(generated.scenarioSeed());
            challenge.setImmutableKey(generated.immutableKey());
            challenge.setConfigJson(version.getConfigJson());
            try { challengeMapper.insert(challenge); }
            catch (DuplicateKeyException ignored) {
                challenge = challengeMapper.selectOne(new LambdaQueryWrapper<GuessStockChallenge>()
                        .eq(GuessStockChallenge::getChallengeDate, today)
                        .eq(GuessStockChallenge::getGameVersionId, version.getId()).last("LIMIT 1"));
            }
        }
        GameVersion challengeVersion = gameService.requireVersion(challenge.getGameVersionId());
        if (!gameId.equals(challengeVersion.getGameId())) bad("今日挑战已经绑定其他游戏");
        return response(challenge, challengeVersion.getGameId());
    }

    @Transactional
    public EntertainmentDtos.SessionResponse start(Long challengeId, PlayerIdentity player,
                                                     EntertainmentDtos.StartSessionRequest request) {
        GuessStockChallenge challenge = requireChallenge(challengeId);
        if (!LocalDate.now().equals(challenge.getChallengeDate())) bad("该每日挑战已经结束");
        GameVersion version = gameService.requireVersion(challenge.getGameVersionId());
        EntertainmentDtos.StartSessionRequest fixedRules = new EntertainmentDtos.StartSessionRequest(
                false, false, null, request == null ? null : request.playbackSpeed(),
                request == null ? null : request.autoAdvance(), request == null ? null : request.playKey());
        return sessionService.startChallenge(version.getGameId(), player, fixedRules, challenge.getScenarioSeed());
    }

    @Transactional
    public EntryResponse submit(Long challengeId, Long sessionId, PlayerIdentity player) {
        GuessStockChallenge challenge = requireChallenge(challengeId);
        GuessStockChallengeEntry existing = entryMapper.selectOne(new LambdaQueryWrapper<GuessStockChallengeEntry>()
                .eq(GuessStockChallengeEntry::getChallengeId, challengeId)
                .eq(GuessStockChallengeEntry::getParticipantIdentityKey, player.key()).last("LIMIT 1"));
        if (existing != null) return entryResponse(existing, player, rank(existing));
        GameSession session = sessionMapper.selectById(sessionId);
        if (session == null || !"COMPLETED".equals(session.getStatus())) bad("只能提交已经完成的挑战场次");
        if (!challenge.getGameVersionId().equals(session.getGameVersionId())
                || !challenge.getScenarioSeed().equals(session.getScenarioSeed())) bad("场次不属于该每日挑战");
        GuessStockParticipant participant = participantMapper.selectOne(
                new LambdaQueryWrapper<GuessStockParticipant>()
                        .eq(GuessStockParticipant::getSessionId, sessionId)
                        .eq(GuessStockParticipant::getPlayerIdentityKey, player.key()).last("LIMIT 1"));
        if (participant == null) bad("只能提交自己的挑战成绩");
        Metrics metrics = metrics(sessionId, participant);
        GuessStockChallengeEntry entry = new GuessStockChallengeEntry();
        entry.setChallengeId(challengeId);
        entry.setParticipantIdentityKey(player.key());
        entry.setSessionId(sessionId);
        entry.setReturnPercent(rate(metrics.returnPct()));
        entry.setMaxDrawdownPercent(rate(metrics.maxDrawdownPct()));
        entry.setLiquidationCount(metrics.liquidations());
        entry.setInterestCost(rate(metrics.interestCost()));
        ResearchScore research = researchScore(sessionId, participant.getId());
        entry.setAccurateResearchCount(research.accurate());
        entry.setResearchAttemptCount(research.attempts());
        try { entryMapper.insert(entry); }
        catch (DuplicateKeyException ignored) {
            entry = entryMapper.selectOne(new LambdaQueryWrapper<GuessStockChallengeEntry>()
                    .eq(GuessStockChallengeEntry::getChallengeId, challengeId)
                    .eq(GuessStockChallengeEntry::getParticipantIdentityKey, player.key()).last("LIMIT 1"));
        }
        return entryResponse(entry, player, rank(entry));
    }

    public ChallengeResponse get(Long challengeId) {
        GuessStockChallenge challenge = requireChallenge(challengeId);
        GameVersion version = gameService.requireVersion(challenge.getGameVersionId());
        return response(challenge, version.getGameId());
    }

    public List<EntryResponse> leaderboard(Long challengeId, PlayerIdentity player) {
        requireChallenge(challengeId);
        List<GuessStockChallengeEntry> entries = entryMapper.selectList(new LambdaQueryWrapper<GuessStockChallengeEntry>()
                        .eq(GuessStockChallengeEntry::getChallengeId, challengeId)
                        .orderByDesc(GuessStockChallengeEntry::getReturnPercent)
                        .orderByDesc(GuessStockChallengeEntry::getMaxDrawdownPercent)
                        .orderByAsc(GuessStockChallengeEntry::getSubmittedAt).last("LIMIT 100"));
        List<EntryResponse> result = new ArrayList<>(entries.size());
        for (int index = 0; index < entries.size(); index++) {
            result.add(entryResponse(entries.get(index), player, (long) index + 1));
        }
        return result;
    }

    private long rank(GuessStockChallengeEntry entry) {
        List<GuessStockChallengeEntry> entries = entryMapper.selectList(new LambdaQueryWrapper<GuessStockChallengeEntry>()
                .eq(GuessStockChallengeEntry::getChallengeId, entry.getChallengeId())
                .orderByDesc(GuessStockChallengeEntry::getReturnPercent)
                .orderByDesc(GuessStockChallengeEntry::getMaxDrawdownPercent)
                .orderByAsc(GuessStockChallengeEntry::getSubmittedAt));
        for (int index = 0; index < entries.size(); index++) {
            if (entries.get(index).getId().equals(entry.getId())) return index + 1L;
        }
        return entries.size();
    }

    private Metrics metrics(Long sessionId, GuessStockParticipant participant) {
        BigDecimal initial = participant.getInitialCash();
        BigDecimal finalAsset = participant.getFinalAsset();
        double returnPct = finalAsset.subtract(initial).divide(initial, 10, RoundingMode.HALF_UP)
                .multiply(new BigDecimal("100")).doubleValue();
        List<CreditSnapshot> snapshots = snapshotMapper.selectList(new LambdaQueryWrapper<CreditSnapshot>()
                .eq(CreditSnapshot::getSessionId, sessionId)
                .eq(CreditSnapshot::getParticipantId, participant.getId())
                .orderByAsc(CreditSnapshot::getRoundNo).orderByAsc(CreditSnapshot::getId));
        List<Double> assets = snapshots.stream().filter(item -> "CLOSING".equals(item.getStage()))
                .map(item -> item.getNetAsset().doubleValue()).toList();
        if (assets.isEmpty()) assets = reconstructAssets(sessionId, participant);
        double peak = initial.doubleValue();
        double maxDrawdown = 0;
        for (double asset : assets) {
            peak = Math.max(peak, asset);
            maxDrawdown = Math.min(maxDrawdown, asset / Math.max(.01, peak) * 100 - 100);
        }
        int liquidations = (int) snapshots.stream().filter(item -> item.getForcedLiquidationShares() != null
                && item.getForcedLiquidationShares() > 0).map(CreditSnapshot::getRoundNo).distinct().count();
        double interest = ledgerMapper.selectList(new LambdaQueryWrapper<LoanLedger>()
                        .eq(LoanLedger::getSessionId, sessionId)
                        .eq(LoanLedger::getParticipantId, participant.getId()))
                .stream().map(LoanLedger::getInterestDelta).filter(value -> value != null && value.signum() > 0)
                .reduce(BigDecimal.ZERO, BigDecimal::add).doubleValue();
        return new Metrics(returnPct, maxDrawdown, liquidations, interest);
    }

    private List<Double> reconstructAssets(Long sessionId, GuessStockParticipant participant) {
        List<GuessStockRound> rounds = roundMapper.selectList(new LambdaQueryWrapper<GuessStockRound>()
                .eq(GuessStockRound::getSessionId, sessionId).orderByAsc(GuessStockRound::getRoundNo));
        List<GuessStockTrade> trades = tradeMapper.selectList(new LambdaQueryWrapper<GuessStockTrade>()
                .eq(GuessStockTrade::getSessionId, sessionId)
                .eq(GuessStockTrade::getParticipantId, participant.getId()).orderByAsc(GuessStockTrade::getId));
        Map<Integer, List<GuessStockTrade>> byRound = new LinkedHashMap<>();
        for (GuessStockTrade trade : trades) byRound.computeIfAbsent(trade.getRoundNo(), ignored -> new ArrayList<>()).add(trade);
        BigDecimal cash = participant.getInitialCash();
        long shares = 0;
        List<Double> assets = new ArrayList<>();
        for (GuessStockRound round : rounds) {
            for (GuessStockTrade trade : byRound.getOrDefault(round.getRoundNo(), List.of())) {
                BigDecimal fee = trade.getFeeAmount() == null ? BigDecimal.ZERO : trade.getFeeAmount();
                if ("BUY".equals(trade.getSide())) { cash = cash.subtract(trade.getAmount()).subtract(fee); shares += trade.getQuantity(); }
                else { cash = cash.add(trade.getAmount()).subtract(fee); shares -= trade.getQuantity(); }
            }
            if (round.getClosePrice() != null) assets.add(cash.add(round.getClosePrice()
                    .multiply(BigDecimal.valueOf(shares))).doubleValue());
        }
        return assets;
    }

    private ResearchScore researchScore(Long sessionId, Long participantId) {
        Map<Integer, GuessStockRound> rounds = roundMapper.selectList(new LambdaQueryWrapper<GuessStockRound>()
                        .eq(GuessStockRound::getSessionId, sessionId))
                .stream().collect(java.util.stream.Collectors.toMap(GuessStockRound::getRoundNo, value -> value));
        List<GuessStockResearchAction> actions = researchActionMapper.selectList(
                new LambdaQueryWrapper<GuessStockResearchAction>()
                        .eq(GuessStockResearchAction::getSessionId, sessionId)
                        .eq(GuessStockResearchAction::getParticipantId, participantId));
        int accurate = 0;
        for (GuessStockResearchAction action : actions) {
            GuessStockRound round = rounds.get(action.getRoundNo());
            if (round != null && researchDirection(action).equals(actualDirection(action, round))) accurate++;
        }
        return new ResearchScore(actions.size(), accurate);
    }

    private String researchDirection(GuessStockResearchAction action) {
        try {
            JsonNode node = objectMapper.readTree(action.getResultJson());
            return node.path("direction").asText("NONE").toUpperCase();
        } catch (Exception ignored) { return "NONE"; }
    }

    static String actualDirection(GuessStockResearchAction action, GuessStockRound round) {
        double value;
        double up;
        double down;
        switch (action.getActionType()) {
            case "VERIFY_EVENT" -> {
                value = switch (round.getEventCredibility() == null ? "" : round.getEventCredibility().toUpperCase()) {
                    case "CONFIRMED" -> 3; case "PRELIMINARY" -> 2; default -> 1;
                };
                up = down = 1.5;
            }
            case "SURVEY_DEMAND" -> { value = decimal(round.getDemandIndex(), 100); up = 103; down = 97; }
            case "PREVIEW_REPORT" -> { value = decimal(round.getFundamentalScore(), 100); up = 103; down = 97; }
            case "CHECK_LIQUIDITY" -> {
                value = "CLOSING".equals(action.getStage())
                        ? decimal(round.getOpeningOrderFlowChangePercent(), 0) : 0;
                up = 8; down = -8;
            }
            default -> { return "NONE"; }
        }
        return value > up ? "UP" : value < down ? "DOWN" : "FLAT";
    }

    private EntryResponse entryResponse(GuessStockChallengeEntry entry, PlayerIdentity viewer, Long rank) {
        DailyChallengeEngine.Score score = DailyChallengeEngine.score(entry.getReturnPercent().doubleValue(),
                entry.getMaxDrawdownPercent().doubleValue(), entry.getLiquidationCount(),
                entry.getInterestCost().doubleValue(), entry.getAccurateResearchCount());
        GuessStockParticipant participant = participantMapper.selectOne(new LambdaQueryWrapper<GuessStockParticipant>()
                .eq(GuessStockParticipant::getSessionId, entry.getSessionId())
                .eq(GuessStockParticipant::getPlayerIdentityKey, entry.getParticipantIdentityKey()).last("LIMIT 1"));
        int attempts = entry.getResearchAttemptCount() == null ? 0 : entry.getResearchAttemptCount();
        BigDecimal accuracy = attempts == 0 ? BigDecimal.ZERO : BigDecimal.valueOf(entry.getAccurateResearchCount())
                .multiply(BigDecimal.valueOf(100)).divide(BigDecimal.valueOf(attempts), 2, RoundingMode.HALF_UP);
        return new EntryResponse(entry.getId(), rank, entry.getSessionId(),
                participant == null ? "挑战者" : participant.getDisplayName(),
                viewer != null && viewer.key().equals(entry.getParticipantIdentityKey()), entry.getReturnPercent(),
                entry.getMaxDrawdownPercent(), entry.getLiquidationCount(), entry.getInterestCost(),
                entry.getAccurateResearchCount(), attempts, accuracy,
                score.lowDrawdownBadge(), score.noLiquidationBadge(),
                score.noDebtCostBadge(), entry.getSubmittedAt());
    }

    private ChallengeResponse response(GuessStockChallenge challenge, Long gameId) {
        return new ChallengeResponse(challenge.getId(), challenge.getChallengeDate(), gameId,
                challenge.getGameVersionId(), challenge.getRulesVersion(), challenge.getScenarioSeed());
    }
    private GuessStockChallenge requireChallenge(Long id) {
        GuessStockChallenge value = challengeMapper.selectById(id);
        if (value == null) throw new BusinessException(ErrorCode.NOT_FOUND, "每日挑战不存在");
        return value;
    }
    private static BigDecimal rate(double value) { return BigDecimal.valueOf(value).setScale(4, RoundingMode.HALF_UP); }
    private static double decimal(BigDecimal value, double fallback) {
        return value == null ? fallback : value.doubleValue();
    }
    private static void bad(String message) { throw new BusinessException(ErrorCode.BAD_REQUEST, message); }

    private record Metrics(double returnPct, double maxDrawdownPct, int liquidations, double interestCost) {}
    private record ResearchScore(int attempts, int accurate) {}
    public record ChallengeResponse(Long id, LocalDate challengeDate, Long gameId, Long gameVersionId,
                                    String rulesVersion, Long scenarioSeed) {}
    public record EntryResponse(Long id, Long rank, Long sessionId, String displayName, boolean currentPlayer,
                                BigDecimal returnPercent, BigDecimal maxDrawdownPercent,
                                int liquidationCount, BigDecimal interestCost, int accurateResearchCount,
                                int researchAttemptCount, BigDecimal researchAccuracyPercent,
                                boolean lowDrawdownBadge, boolean noLiquidationBadge, boolean noDebtCostBadge,
                                java.time.LocalDateTime submittedAt) {}
}
