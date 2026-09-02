package com.order.menu.module.entertainment.mode.guessstock.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.order.menu.common.exception.BusinessException;
import com.order.menu.common.exception.ErrorCode;
import com.order.menu.common.response.PageResponse;
import com.order.menu.module.entertainment.core.dto.EntertainmentDtos;
import com.order.menu.module.entertainment.core.entity.EntertainmentGame;
import com.order.menu.module.entertainment.core.entity.GameLeaderboardEntry;
import com.order.menu.module.entertainment.core.entity.GameRoom;
import com.order.menu.module.entertainment.core.entity.GameRoomMember;
import com.order.menu.module.entertainment.core.entity.GameSession;
import com.order.menu.module.entertainment.core.entity.GameSessionAction;
import com.order.menu.module.entertainment.core.entity.GameVersion;
import com.order.menu.module.entertainment.core.mapper.EntertainmentGameMapper;
import com.order.menu.module.entertainment.core.mapper.GameLeaderboardEntryMapper;
import com.order.menu.module.entertainment.core.mapper.GameRoomMapper;
import com.order.menu.module.entertainment.core.mapper.GameRoomMemberMapper;
import com.order.menu.module.entertainment.core.mapper.GameSessionActionMapper;
import com.order.menu.module.entertainment.core.mapper.GameSessionMapper;
import com.order.menu.module.entertainment.core.service.EntertainmentGameService;
import com.order.menu.module.entertainment.core.service.GameAiCredentialService;
import com.order.menu.module.entertainment.core.service.GamePlayKeyService;
import com.order.menu.module.entertainment.core.service.PlayerIdentity;
import com.order.menu.module.entertainment.mode.guessstock.agent.AiDecisionValidator;
import com.order.menu.module.entertainment.mode.guessstock.agent.AiEvidenceDetector;
import com.order.menu.module.entertainment.mode.guessstock.agent.AiMarketSnapshot;
import com.order.menu.module.entertainment.mode.guessstock.agent.AiMarketSnapshotService;
import com.order.menu.module.entertainment.mode.guessstock.agent.AiTargetPositionExecutor;
import com.order.menu.module.entertainment.mode.guessstock.agent.AiThesisConditionEvaluator;
import com.order.menu.module.entertainment.mode.guessstock.agent.AiThesisService;
import com.order.menu.module.entertainment.mode.guessstock.agent.AiTradingFallbackPolicy;
import com.order.menu.module.entertainment.mode.guessstock.agent.PublicEventDigest;
import com.order.menu.module.entertainment.mode.guessstock.agent.AiDifficultyPolicy;
import com.order.menu.module.entertainment.mode.guessstock.config.GuessStockConfig;
import com.order.menu.module.entertainment.mode.guessstock.credit.service.GuessStockCreditService;
import com.order.menu.module.entertainment.mode.guessstock.credit.entity.LoanContract;
import com.order.menu.module.entertainment.mode.guessstock.credit.entity.LoanLedger;
import com.order.menu.module.entertainment.mode.guessstock.engine.GuessStockEventImpactEngine;
import com.order.menu.module.entertainment.mode.guessstock.engine.GuessStockRules;
import com.order.menu.module.entertainment.mode.guessstock.engine.GuessStockEventSelector;
import com.order.menu.module.entertainment.mode.guessstock.engine.AiTradingPolicy;
import com.order.menu.module.entertainment.mode.guessstock.engine.AuctionClearingEngine;
import com.order.menu.module.entertainment.mode.guessstock.engine.MarketStateCalculator;
import com.order.menu.module.entertainment.mode.guessstock.engine.MarketPriceEngine;
import com.order.menu.module.entertainment.mode.guessstock.fundamental.FundamentalEngine;
import com.order.menu.module.entertainment.mode.guessstock.fundamental.ResearchEngine;
import com.order.menu.module.entertainment.mode.guessstock.engine.EventImpactSchedule;
import com.order.menu.module.entertainment.mode.guessstock.engine.EventChainEngine;
import com.order.menu.module.entertainment.mode.guessstock.entity.GuessStockAiTask;
import com.order.menu.module.entertainment.mode.guessstock.entity.GuessStockOrder;
import com.order.menu.module.entertainment.mode.guessstock.entity.GuessStockParticipant;
import com.order.menu.module.entertainment.mode.guessstock.entity.GuessStockPosition;
import com.order.menu.module.entertainment.mode.guessstock.entity.GuessStockRound;
import com.order.menu.module.entertainment.mode.guessstock.entity.GuessStockTrade;
import com.order.menu.module.entertainment.mode.guessstock.entity.GuessStockEventImpactSlice;
import com.order.menu.module.entertainment.mode.guessstock.entity.GuessStockEventChainState;
import com.order.menu.module.entertainment.mode.guessstock.entity.GuessStockResearchAccount;
import com.order.menu.module.entertainment.mode.guessstock.entity.GuessStockResearchAction;
import com.order.menu.module.entertainment.mode.guessstock.entity.GuessStockFinancialReport;
import com.order.menu.module.entertainment.mode.guessstock.entity.AiInvestmentDecision;
import com.order.menu.module.entertainment.mode.guessstock.entity.AiInvestmentThesis;
import com.order.menu.module.entertainment.mode.guessstock.mapper.AiInvestmentDecisionMapper;
import com.order.menu.module.entertainment.mode.guessstock.mapper.GuessStockAiTaskMapper;
import com.order.menu.module.entertainment.mode.guessstock.mapper.GuessStockOrderMapper;
import com.order.menu.module.entertainment.mode.guessstock.mapper.GuessStockParticipantMapper;
import com.order.menu.module.entertainment.mode.guessstock.mapper.GuessStockPositionMapper;
import com.order.menu.module.entertainment.mode.guessstock.mapper.GuessStockRoundMapper;
import com.order.menu.module.entertainment.mode.guessstock.mapper.GuessStockTradeMapper;
import com.order.menu.module.entertainment.mode.guessstock.mapper.GuessStockEventImpactSliceMapper;
import com.order.menu.module.entertainment.mode.guessstock.mapper.GuessStockEventChainStateMapper;
import com.order.menu.module.entertainment.mode.guessstock.mapper.GuessStockResearchAccountMapper;
import com.order.menu.module.entertainment.mode.guessstock.mapper.GuessStockResearchActionMapper;
import com.order.menu.module.entertainment.mode.guessstock.mapper.GuessStockFinancialReportMapper;
import java.math.BigDecimal;
import java.net.URI;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@RequiredArgsConstructor
public class GuessStockSessionService {
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(4);
    private static final long LEASE_SECONDS = 4;
    private static final long ORDER_DRAIN_GRACE_MILLIS = 800;
    private static final long AUTO_GATE_MILLIS = 3_000;
    private static final long ROUND_START_FALLBACK_MILLIS = 6_000;
    private static final Set<BigDecimal> PLAYBACK_SPEEDS = Set.of(
            new BigDecimal("1.0"), new BigDecimal("1.5"), new BigDecimal("2.0"),
            new BigDecimal("3.0"), new BigDecimal("5.0"));
    private static final SecureRandom SESSION_RANDOM = new SecureRandom();
    private static final String OPENING = "OPENING";
    private static final String CLOSING = "CLOSING";
    private static final String AI_PROMPT_VERSION = "agent-v8-public-information";
    private final ConcurrentHashMap<Long, AtomicInteger> inFlightOrders = new ConcurrentHashMap<>();

    private final EntertainmentGameService gameService;
    private final EntertainmentGameMapper gameMapper;
    private final GameSessionMapper sessionMapper;
    private final GameSessionActionMapper actionMapper;
    private final GameLeaderboardEntryMapper leaderboardMapper;
    private final GameRoomMapper roomMapper;
    private final GameRoomMemberMapper roomMemberMapper;
    private final GuessStockRoundMapper roundMapper;
    private final GuessStockParticipantMapper participantMapper;
    private final GuessStockPositionMapper positionMapper;
    private final GuessStockOrderMapper orderMapper;
    private final GuessStockTradeMapper tradeMapper;
    private final GuessStockAiTaskMapper aiTaskMapper;
    private final GuessStockEventImpactSliceMapper eventImpactSliceMapper;
    private final GuessStockEventChainStateMapper eventChainStateMapper;
    private final GuessStockResearchAccountMapper researchAccountMapper;
    private final GuessStockResearchActionMapper researchActionMapper;
    private final GuessStockFinancialReportMapper financialReportMapper;
    private final AiInvestmentDecisionMapper aiDecisionMapper;
    private final GameAiCredentialService credentialService;
    private final GamePlayKeyService playKeyService;
    private final NonTransactionalAiModelGateway aiModelGateway;
    private final AiMarketSnapshotService aiMarketSnapshotService;
    private final AiThesisConditionEvaluator aiConditionEvaluator;
    private final AiEvidenceDetector aiEvidenceDetector;
    private final AiDecisionValidator aiDecisionValidator;
    private final AiThesisService aiThesisService;
    private final AiTargetPositionExecutor aiTargetPositionExecutor;
    private final AiTradingFallbackPolicy aiTradingFallbackPolicy;
    private final GuessStockCreditService creditService;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;
    @Qualifier("entertainmentAiExecutor")
    private final Executor entertainmentAiExecutor;

    @Transactional
    public EntertainmentDtos.SessionResponse start(Long gameId, PlayerIdentity player) {
        return start(gameId, player, null);
    }

    @Transactional
    public EntertainmentDtos.SessionResponse start(Long gameId, PlayerIdentity player,
                                                     EntertainmentDtos.StartSessionRequest request) {
        return startInternal(gameId, player, request, null);
    }

    @Transactional
    public EntertainmentDtos.SessionResponse startChallenge(Long gameId, PlayerIdentity player,
                                                              EntertainmentDtos.StartSessionRequest request,
                                                              long scenarioSeed) {
        return startInternal(gameId, player, request, scenarioSeed);
    }

    private EntertainmentDtos.SessionResponse startInternal(Long gameId, PlayerIdentity player,
                                                              EntertainmentDtos.StartSessionRequest request,
                                                              Long scenarioSeedOverride) {
        EntertainmentGame game = gameService.requirePlayableForUpdate(gameId);
        GameSession existing = sessionMapper.selectOne(new LambdaQueryWrapper<GameSession>()
                .eq(GameSession::getGameId, gameId)
                .eq(GameSession::getGameVersionId, game.getCurrentVersionId())
                .eq(GameSession::getPlayerIdentityKey, player.key())
                .isNull(GameSession::getRoomId)
                .eq(scenarioSeedOverride != null, GameSession::getScenarioSeed, scenarioSeedOverride)
                .in(GameSession::getStatus, "ACTIVE", "PAUSED")
                .orderByDesc(GameSession::getId).last("LIMIT 1"));
        if (existing != null) return snapshotLocked(existing.getId(), player, true);

        playKeyService.consumeIfRequired(gameId, Integer.valueOf(1).equals(game.getPlayKeyRequired()),
                request == null ? null : request.playKey(), "SOLO", player);

        GameVersion version = gameService.requireVersion(game.getCurrentVersionId());
        GuessStockConfig config = gameService.readConfig(version.getConfigJson());
        LocalDateTime now = LocalDateTime.now();
        GameSession session = new GameSession();
        session.setGameId(gameId);
        session.setGameVersionId(version.getId());
        session.setPlayerType(player.type());
        session.setPlayerUserId(player.userId());
        session.setGuestId(player.guestId());
        session.setPlayerIdentityKey(player.key());
        session.setStatus("ACTIVE");
        session.setCurrentRound(1);
        session.setPhase("OPENING_TRADING");
        session.setProgressionMode(config.progressionMode());
        boolean automaticSolo = !"MANUAL".equals(config.progressionMode());
        session.setPlaybackSpeed(automaticSolo
                ? normalizePlaybackSpeed(request == null ? null : request.playbackSpeed())
                : BigDecimal.ONE.setScale(1));
        session.setAutoAdvance(automaticSolo
                && Boolean.TRUE.equals(request == null ? null : request.autoAdvance()) ? 1 : 0);
        session.setActivityLeaseUntil(now.plusSeconds(LEASE_SECONDS));
        session.setRowVersion(0L);
        session.setStartedAt(now);
        sessionMapper.insert(session);
        if (scenarioSeedOverride == null) assignSessionSeed(session, version);
        else {
            session.setScenarioSeed(scenarioSeedOverride);
            sessionMapper.updateById(session);
        }

        createParticipant(session, "PLAYER", player.displayName(), null, player.userId(), player.guestId(),
                player.key(), null, config.initialCash());
        boolean useAi = Boolean.TRUE.equals(config.aiEnabled()) && (request == null || !Boolean.FALSE.equals(request.useAi()));
        if (useAi) {
            for (int i = 0; i < config.aiPlayers().size(); i++) {
                GuessStockConfig.AiPlayerConfig ai = config.aiPlayers().get(i);
                createParticipant(session, "AI", ai.name(), ai.style(), null, null,
                        null, null, ai.initialCash());
            }
            credentialService.storeForSession(session.getId(), gameId, player, request);
        }
        GuessStockRound round = createRound(session, version, config, 1, config.initialPrice());
        prepareRoundPhase(session, round, config, now);
        game.setPlayCount((game.getPlayCount() == null ? 0 : game.getPlayCount()) + 1);
        gameMapper.updateById(game);
        return buildSnapshot(session, player);
    }

    @Transactional
    public EntertainmentDtos.SessionResponse startRoom(GameRoom room, List<GameRoomMember> members,
                                                         PlayerIdentity host, boolean useAi) {
        GameRoom lockedRoom = roomMapper.selectByIdForUpdate(room.getId());
        if (lockedRoom == null) throw new BusinessException(ErrorCode.NOT_FOUND, "多人房间不存在");
        if (lockedRoom.getSessionId() != null) return sync(lockedRoom.getSessionId(), host);
        if (!"LOBBY".equals(lockedRoom.getStatus())) throw new BusinessException(ErrorCode.CONFLICT, "房间已经开始");
        EntertainmentGame game = gameService.requirePlayableForUpdate(lockedRoom.getGameId());
        GameVersion version = gameService.requireVersion(lockedRoom.getGameVersionId());
        GuessStockConfig config = gameService.readConfig(version.getConfigJson());
        if (!Boolean.TRUE.equals(config.multiplayerEnabled())) bad("该游戏未开启多人模式");
        if (members.size() < 2) bad("至少需要 2 名真人才能开始多人游戏");
        if (useAi && !Boolean.TRUE.equals(config.aiEnabled())) bad("该游戏未开启 AI 对手");
        if (useAi && !credentialService.readyForRoomOrGame(lockedRoom.getId(), game.getId(), host.key())) {
            bad("使用 AI 对手前，请由房主或游戏创建者配置 API Key");
        }
        GameRoomMember hostMember = members.stream().filter(member -> host.key().equals(member.getPlayerIdentityKey()))
                .findFirst().orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN, "只有房主可以开始游戏"));
        LocalDateTime now = LocalDateTime.now();
        GameSession session = new GameSession();
        session.setGameId(game.getId());
        session.setGameVersionId(version.getId());
        session.setRoomId(lockedRoom.getId());
        session.setPlayerType(hostMember.getPlayerType());
        session.setPlayerUserId(hostMember.getPlayerUserId());
        session.setGuestId(hostMember.getGuestId());
        session.setPlayerIdentityKey(host.key());
        session.setStatus("ACTIVE");
        session.setCurrentRound(1);
        session.setPhase("OPENING_TRADING");
        session.setProgressionMode(config.progressionMode());
        session.setPlaybackSpeed(BigDecimal.ONE.setScale(1));
        session.setAutoAdvance(0);
        session.setActivityLeaseUntil(now.plusSeconds(LEASE_SECONDS));
        session.setRowVersion(0L);
        session.setStartedAt(now);
        sessionMapper.insert(session);
        assignSessionSeed(session, version);
        for (GameRoomMember member : members) {
            GuessStockParticipant participant = createParticipant(session, "PLAYER", member.getDisplayName(), null,
                    member.getPlayerUserId(), member.getGuestId(), member.getPlayerIdentityKey(), member.getId(), config.initialCash());
            member.setParticipantId(participant.getId());
            roomMemberMapper.updateById(member);
        }
        if (useAi) {
            for (int i = 0; i < config.aiPlayers().size(); i++) {
                GuessStockConfig.AiPlayerConfig ai = config.aiPlayers().get(i);
                createParticipant(session, "AI", ai.name(), ai.style(), null, null, null, null, ai.initialCash());
            }
            credentialService.attachRoomOrGameCredential(lockedRoom.getId(), session.getId(), game.getId(), host.key());
        } else {
            credentialService.purgeRoom(lockedRoom.getId());
        }
        GuessStockRound round = createRound(session, version, config, 1, config.initialPrice());
        prepareRoundPhase(session, round, config, now);
        lockedRoom.setSessionId(session.getId());
        lockedRoom.setStatus("PLAYING");
        lockedRoom.setStartedAt(now);
        roomMapper.updateById(lockedRoom);
        game.setPlayCount((game.getPlayCount() == null ? 0 : game.getPlayCount()) + 1);
        gameMapper.updateById(game);
        return buildSnapshot(session, host);
    }

    @Transactional
    public EntertainmentDtos.SessionResponse sync(Long sessionId, PlayerIdentity player) {
        return snapshotLocked(sessionId, player, true);
    }

    @Transactional
    public void closeRoomSession(Long sessionId, Long roomId) {
        if (sessionId == null) return;
        GameSession session = sessionMapper.selectByIdForUpdate(sessionId);
        if (session == null || !roomId.equals(session.getRoomId())) return;
        if (List.of("ACTIVE", "PAUSED").contains(session.getStatus())) {
            session.setStatus("ABANDONED");
            session.setPhase("ABANDONED");
            session.setPhaseEndsAt(null);
            session.setActivityLeaseUntil(null);
            session.setCompletedAt(LocalDateTime.now());
            sessionMapper.updateById(session);
        }
        credentialService.purgeSession(sessionId);
    }

    @Transactional
    public EntertainmentDtos.SessionResponse act(Long sessionId, PlayerIdentity player,
                                                   EntertainmentDtos.ActionRequest request) {
        boolean orderMutation = "PLACE_ORDER".equalsIgnoreCase(request.actionType())
                || "CANCEL_ORDER".equalsIgnoreCase(request.actionType());
        if (orderMutation) beginOrderMutation(sessionId);
        try {
            return actInternal(sessionId, player, request);
        } finally {
            if (orderMutation) endOrderMutation(sessionId);
        }
    }

    @Transactional
    public EntertainmentDtos.CreditStatusResponse creditStatus(Long sessionId, PlayerIdentity player) {
        GameSession session = requireOwnedLocked(sessionId, player);
        GuessStockParticipant participant = playerParticipant(sessionId, player);
        GuessStockPosition position = positionMapper.selectByParticipant(participant.getId());
        GuessStockConfig config = gameService.readConfig(gameService.requireVersion(session.getGameVersionId()).getConfigJson());
        return creditResponse(creditService.status(sessionId, participant, position,
                referencePrice(currentRound(session), stageForPhase(session.getPhase())), config), session.getCurrentRound());
    }

    @Transactional
    public EntertainmentDtos.SessionResponse borrow(Long sessionId, PlayerIdentity player,
                                                     EntertainmentDtos.CreditActionRequest request) {
        GameSession session = requireOwnedLocked(sessionId, player);
        progressLocked(session);
        session = sessionMapper.selectByIdForUpdate(sessionId);
        requireBorrowPhase(session);
        requireOrderEditable(session, player);
        GuessStockParticipant participant = playerParticipant(sessionId, player);
        participant = participantMapper.selectByIdForUpdate(participant.getId());
        GuessStockPosition position = positionMapper.selectByParticipantForUpdate(participant.getId());
        GuessStockConfig config = gameService.readConfig(gameService.requireVersion(session.getGameVersionId()).getConfigJson());
        creditService.borrow(sessionId, session.getCurrentRound(), session.getPhase(), participant, position,
                referencePrice(currentRound(session), stageForPhase(session.getPhase())), config,
                request.productCode(), request.amount(), request.maturityRounds(), request.requestId());
        touchLease(session);
        sessionMapper.updateById(session);
        return buildSnapshot(session, player);
    }

    @Transactional
    public EntertainmentDtos.SessionResponse repay(Long sessionId, PlayerIdentity player,
                                                    EntertainmentDtos.CreditActionRequest request) {
        GameSession session = requireOwnedLocked(sessionId, player);
        progressLocked(session);
        session = sessionMapper.selectByIdForUpdate(sessionId);
        requireActiveSession(session);
        GuessStockParticipant participant = playerParticipant(sessionId, player);
        participant = participantMapper.selectByIdForUpdate(participant.getId());
        GuessStockPosition position = positionMapper.selectByParticipantForUpdate(participant.getId());
        GuessStockConfig config = gameService.readConfig(gameService.requireVersion(session.getGameVersionId()).getConfigJson());
        creditService.repay(sessionId, session.getCurrentRound(), session.getPhase(), participant, position,
                latestMarkPrice(currentRound(session)), config,
                request.productCode(), request.amount(), request.requestId());
        touchLease(session);
        sessionMapper.updateById(session);
        return buildSnapshot(session, player);
    }

    @Transactional
    public EntertainmentDtos.ResearchResponse research(Long sessionId, PlayerIdentity player,
                                                        EntertainmentDtos.ResearchRequest request) {
        GameSession session = requireOwnedLocked(sessionId, player);
        requireActiveSession(session);
        GuessStockConfig config = gameService.readConfig(
                gameService.requireVersion(session.getGameVersionId()).getConfigJson());
        if (!Boolean.TRUE.equals(config.fundamentalEnabled()) || config.researchPoints() <= 0) {
            bad("当前游戏未启用研究行动");
        }
        GuessStockParticipant participant = playerParticipant(sessionId, player);
        ResearchEngine.Action action;
        try { action = ResearchEngine.Action.valueOf(request.actionType().toUpperCase()); }
        catch (IllegalArgumentException exception) { bad("不支持的研究行动"); return null; }
        if (action == ResearchEngine.Action.NONE) bad("请选择研究行动");
        return performResearch(session, participant, config, action, request.requestId());
    }

    private EntertainmentDtos.ResearchResponse performResearch(
            GameSession session,
            GuessStockParticipant participant,
            GuessStockConfig config,
            ResearchEngine.Action action,
            String requestId) {
        Long sessionId = session.getId();
        GuessStockResearchAction duplicate = researchActionMapper.selectOne(
                new LambdaQueryWrapper<GuessStockResearchAction>()
                        .eq(GuessStockResearchAction::getSessionId, sessionId)
                        .eq(GuessStockResearchAction::getParticipantId, participant.getId())
                        .eq(GuessStockResearchAction::getRequestId, requestId).last("LIMIT 1"));
        if (duplicate != null) return readResearchResponse(duplicate.getResultJson());
        GuessStockResearchAccount account = researchAccountMapper.selectOne(
                new LambdaQueryWrapper<GuessStockResearchAccount>()
                        .eq(GuessStockResearchAccount::getSessionId, sessionId)
                        .eq(GuessStockResearchAccount::getParticipantId, participant.getId()).last("LIMIT 1"));
        if (account == null) {
            account = new GuessStockResearchAccount(); account.setSessionId(sessionId);
            account.setParticipantId(participant.getId()); account.setTotalPoints(config.researchPoints());
            account.setRemainingPoints(config.researchPoints()); researchAccountMapper.insert(account);
        }
        GuessStockRound round = currentRound(session);
        double previousFlow = decimal(round.getOpeningOrderFlowChangePercent(), 0)
                + decimal(round.getOrderFlowChangePercent(), 0);
        ResearchEngine.Result result;
        try {
            result = ResearchEngine.perform(new ResearchEngine.Account(account.getTotalPoints(),
                            account.getRemainingPoints()), action, fundamentalState(round),
                    round.getEventCredibility(), previousFlow,
                    mix(session.getScenarioSeed() == null ? sessionId : session.getScenarioSeed(),
                            session.getCurrentRound(), action.ordinal() * 97 + participant.getId().intValue()));
        } catch (IllegalArgumentException exception) { bad("研究点已经用完"); return null; }
        account.setRemainingPoints(result.account().remainingPoints()); researchAccountMapper.updateById(account);
        EntertainmentDtos.ResearchResponse response = new EntertainmentDtos.ResearchResponse(action.name(),
                result.direction(), result.rangeLow(), result.rangeHigh(), result.account().remainingPoints());
        GuessStockResearchAction audit = new GuessStockResearchAction(); audit.setSessionId(sessionId);
        audit.setParticipantId(participant.getId()); audit.setRoundNo(session.getCurrentRound());
        audit.setStage(stageForPhase(session.getPhase())); audit.setActionType(action.name());
        audit.setResultJson(write(response)); audit.setRequestId(requestId); researchActionMapper.insert(audit);
        return response;
    }

    private EntertainmentDtos.ResearchResponse readResearchResponse(String json) {
        try { return objectMapper.readValue(json, EntertainmentDtos.ResearchResponse.class); }
        catch (JsonProcessingException exception) { throw new BusinessException(ErrorCode.SERVER_ERROR, "研究记录读取失败"); }
    }

    @Transactional
    public EntertainmentDtos.SessionResponse extendLoan(Long sessionId, PlayerIdentity player,
                                                         EntertainmentDtos.LoanExtensionRequest request) {
        GameSession session = requireOwnedLocked(sessionId, player);
        progressLocked(session); session = sessionMapper.selectByIdForUpdate(sessionId); requireActiveSession(session);
        GuessStockParticipant participant = participantMapper.selectByIdForUpdate(playerParticipant(sessionId, player).getId());
        GuessStockPosition position = positionMapper.selectByParticipantForUpdate(participant.getId());
        GuessStockConfig config = gameService.readConfig(gameService.requireVersion(session.getGameVersionId()).getConfigJson());
        creditService.extend(sessionId, session.getCurrentRound(), participant, position,
                latestMarkPrice(currentRound(session)), config, request.contractId(), request.extensionRounds(), request.requestId());
        touchLease(session); sessionMapper.updateById(session); return buildSnapshot(session, player);
    }

    private EntertainmentDtos.SessionResponse actInternal(Long sessionId, PlayerIdentity player,
                                                            EntertainmentDtos.ActionRequest request) {
        GameSession session = requireOwnedLocked(sessionId, player);
        progressLocked(session);
        session = sessionMapper.selectByIdForUpdate(sessionId);
        if ("PAUSED".equals(session.getStatus())) bad("请先继续游戏");
        GameSessionAction existingAction = actionMapper.selectOne(new LambdaQueryWrapper<GameSessionAction>()
                .eq(GameSessionAction::getSessionId, sessionId)
                .eq(GameSessionAction::getClientRequestId, request.clientRequestId()).last("LIMIT 1"));
        if (existingAction != null) return buildSnapshot(session, player);

        String actionType = request.actionType().toUpperCase();
        if ("ACK_EVENT".equals(actionType)) {
            acknowledgeEvent(session, player);
        } else if ("READY_NEXT_ROUND".equals(actionType)) {
            readyNextRound(session, player);
        } else if ("SET_PLAYBACK_SETTINGS".equals(actionType)) {
            updatePlaybackSettings(session, request);
        } else if ("REQUEST_RESTART".equals(actionType)) {
            requestRestart(session, player, true);
        } else if ("CANCEL_RESTART".equals(actionType)) {
            requestRestart(session, player, false);
        } else if ("CANCEL_ORDER".equals(actionType)) {
            requireTrading(session);
            requireOrderEditable(session, player);
            GuessStockParticipant participant = playerParticipant(sessionId, player);
            cancelPendingOrder(session, participant);
        } else if ("PLACE_ORDER".equals(actionType)) {
            requireTrading(session);
            requireOrderEditable(session, player);
            GuessStockParticipant participant = playerParticipant(sessionId, player);
            GuessStockConfig config = gameService.readConfig(
                    gameService.requireVersion(session.getGameVersionId()).getConfigJson());
            if ("TARGET_AUCTION".equals(config.tradingMode())) {
                placeTargetOrder(session, participant, request.targetExposurePercent(),
                        request.clientRequestId());
            } else {
                placeOrder(session, participant, request.side(), request.quantity(), Boolean.TRUE.equals(request.allIn()),
                        request.clientRequestId(), "PLAYER");
            }
        } else {
            bad("不支持的订单操作");
        }
        recordAction(session, request);
        session = sessionMapper.selectByIdForUpdate(sessionId);
        if ("ACTIVE".equals(session.getStatus())) touchLease(session);
        sessionMapper.updateById(session);
        return buildSnapshot(session, player);
    }

    private void updatePlaybackSettings(GameSession session, EntertainmentDtos.ActionRequest request) {
        if (session.getRoomId() != null) bad("多人游戏不能调整单人倍速");
        if ("MANUAL".equals(session.getProgressionMode())) bad("仅单人自动模式支持倍速与自动进入下一阶段");
        if (!"ACTIVE".equals(session.getStatus())) bad("当前场次不能调整倍速");
        BigDecimal oldSpeed = playbackSpeed(session);
        BigDecimal newSpeed = request.playbackSpeed() == null ? oldSpeed : normalizePlaybackSpeed(request.playbackSpeed());
        boolean oldAutoAdvance = autoAdvance(session);
        boolean newAutoAdvance = request.autoAdvance() == null ? oldAutoAdvance : Boolean.TRUE.equals(request.autoAdvance());
        LocalDateTime now = LocalDateTime.now();

        if ((isTradingPhase(session.getPhase()) || isCreditPhase(session.getPhase()))
                && session.getPhaseEndsAt() != null) {
            scalePhaseDeadline(session, now, oldSpeed, newSpeed);
            if (isTradingPhase(session.getPhase())) scalePendingAiTasks(session, now, oldSpeed, newSpeed);
        } else if (isEventPhase(session.getPhase()) || isResultPhase(session.getPhase())) {
            if (!newAutoAdvance) session.setPhaseEndsAt(null);
            else if (oldAutoAdvance && session.getPhaseEndsAt() != null) scalePhaseDeadline(session, now, oldSpeed, newSpeed);
            else session.setPhaseEndsAt(now.plusNanos(scaledMillis(AUTO_GATE_MILLIS, newSpeed) * 1_000_000));
        }
        session.setPlaybackSpeed(newSpeed);
        session.setAutoAdvance(newAutoAdvance ? 1 : 0);
        sessionMapper.updateById(session);
    }

    private void requireTrading(GameSession session) {
        if (!"ACTIVE".equals(session.getStatus()) || !isTradingPhase(session.getPhase())) {
            throw new BusinessException(ErrorCode.CONFLICT, "当前阶段不能操作订单");
        }
    }

    private void requireBorrowPhase(GameSession session) {
        if (!"ACTIVE".equals(session.getStatus())
                || !(isCreditPhase(session.getPhase()) || "OPENING_TRADING".equals(session.getPhase()))) {
            throw new BusinessException(ErrorCode.CONFLICT, "当前阶段不能申请贷款");
        }
    }

    private void requireActiveSession(GameSession session) {
        if (!"ACTIVE".equals(session.getStatus()) || "COMPLETED".equals(session.getPhase())) {
            throw new BusinessException(ErrorCode.CONFLICT, "当前场次不能还款");
        }
    }

    private void requireOrderEditable(GameSession session, PlayerIdentity player) {
        if (session.getRoomId() == null || !"MANUAL".equals(session.getProgressionMode())) return;
        GameRoomMember member = requireActiveMember(session.getRoomId(), player);
        if (session.getCurrentRound().equals(member.getReadyRound())
                && session.getPhase().equals(member.getReadyStage())) {
            throw new BusinessException(ErrorCode.CONFLICT, "已确认进入结算，不能再修改订单");
        }
    }

    private void acknowledgeEvent(GameSession session, PlayerIdentity player) {
        if (!"ACTIVE".equals(session.getStatus()) || !isEventPhase(session.getPhase())) {
            throw new BusinessException(ErrorCode.CONFLICT, "当前没有需要确认的事件");
        }
        if (session.getRoomId() == null) {
            continueAfterEvent(session, LocalDateTime.now());
            return;
        }
        GameRoomMember member = requireActiveMember(session.getRoomId(), player);
        member.setEventAckRound(session.getCurrentRound());
        member.setEventAckStage(session.getPhase());
        member.setLastSeenAt(LocalDateTime.now());
        roomMemberMapper.updateById(member);
        if (allActiveMembers(session.getRoomId()).stream()
                .allMatch(item -> session.getCurrentRound().equals(item.getEventAckRound())
                        && session.getPhase().equals(item.getEventAckStage()))) {
            continueAfterEvent(session, LocalDateTime.now());
        }
    }

    private void continueAfterEvent(GameSession session, LocalDateTime now) {
        String stage = stageForPhase(session.getPhase());
        if (OPENING.equals(stage) && creditEnabled(session)) startCreditPhase(session, now);
        else startTrading(session, stage, now);
    }

    private void readyNextRound(GameSession session, PlayerIdentity player) {
        if (!"ACTIVE".equals(session.getStatus())
                || !(isTradingPhase(session.getPhase()) || isCreditPhase(session.getPhase())
                || isRoundStartPhase(session.getPhase())
                || isResultPhase(session.getPhase()))) {
            throw new BusinessException(ErrorCode.CONFLICT, "当前不需要确认下一回合");
        }
        if (isTradingPhase(session.getPhase()) && !"MANUAL".equals(session.getProgressionMode())) {
            throw new BusinessException(ErrorCode.CONFLICT, "自动模式会在倒计时结束后结算");
        }
        if (session.getRoomId() != null) {
            GameRoomMember member = requireActiveMember(session.getRoomId(), player);
            member.setReadyRound(session.getCurrentRound());
            member.setReadyStage(session.getPhase());
            member.setLastSeenAt(LocalDateTime.now());
            roomMemberMapper.updateById(member);
            if (!allActiveMembers(session.getRoomId()).stream()
                    .allMatch(item -> session.getCurrentRound().equals(item.getReadyRound())
                            && session.getPhase().equals(item.getReadyStage()))) return;
        }
        progressFromGate(session, LocalDateTime.now());
    }

    private void requestRestart(GameSession session, PlayerIdentity player, boolean requested) {
        if (!"ACTIVE".equals(session.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "当前场次不能重新开始");
        }
        if (session.getRoomId() == null) {
            if (!requested) return;
            abandonForRestart(session, LocalDateTime.now());
            return;
        }
        GameRoomMember member = requireActiveMember(session.getRoomId(), player);
        member.setRestartSessionId(requested ? session.getId() : null);
        member.setLastSeenAt(LocalDateTime.now());
        roomMemberMapper.updateById(member);
        if (requested && allActiveMembers(session.getRoomId()).stream()
                .allMatch(item -> session.getId().equals(item.getRestartSessionId()))) {
            abandonForRestart(session, LocalDateTime.now());
        }
    }

    @Transactional
    public EntertainmentDtos.SessionResponse pause(Long sessionId, PlayerIdentity player) {
        GameSession session = requireOwnedLocked(sessionId, player);
        progressLocked(session);
        session = sessionMapper.selectByIdForUpdate(sessionId);
        if ("ACTIVE".equals(session.getStatus()) && isTradingPhase(session.getPhase())) {
            List<GuessStockAiTask> dueTasks = aiTaskMapper.selectList(new LambdaQueryWrapper<GuessStockAiTask>()
                .eq(GuessStockAiTask::getSessionId, sessionId)
                .eq(GuessStockAiTask::getRoundNo, session.getCurrentRound())
                    .eq(GuessStockAiTask::getStage, stageForPhase(session.getPhase()))
                    .eq(GuessStockAiTask::getDecisionStatus, "PENDING")
                    .le(GuessStockAiTask::getExecuteAt, LocalDateTime.now()));
            for (GuessStockAiTask task : dueTasks) executeAiTaskLocked(task.getId(), LocalDateTime.now());
        }
        pauseLocked(session, LocalDateTime.now());
        return buildSnapshot(session, player);
    }

    @Transactional
    public EntertainmentDtos.SessionResponse resume(Long sessionId, PlayerIdentity player) {
        GameSession session = requireOwnedLocked(sessionId, player);
        if ("COMPLETED".equals(session.getStatus())) return buildSnapshot(session, player);
        if (!"PAUSED".equals(session.getStatus())) {
            touchLease(session);
            sessionMapper.updateById(session);
            return buildSnapshot(session, player);
        }
        LocalDateTime now = LocalDateTime.now();
        long pausedMillis = Math.max(0, Duration.between(session.getPausedAt(), now).toMillis());
        List<GuessStockAiTask> tasks = aiTaskMapper.selectList(new LambdaQueryWrapper<GuessStockAiTask>()
                .eq(GuessStockAiTask::getSessionId, sessionId).eq(GuessStockAiTask::getDecisionStatus, "PENDING"));
        for (GuessStockAiTask task : tasks) {
            task.setExecuteAt(task.getExecuteAt().plusNanos(pausedMillis * 1_000_000));
            aiTaskMapper.updateById(task);
        }
        session.setStatus("ACTIVE");
        if (("MANUAL".equals(session.getProgressionMode()) && isTradingPhase(session.getPhase()))
                || (isCreditPhase(session.getPhase()) && !creditPhaseTimed(session))
                || ((isEventPhase(session.getPhase()) || isResultPhase(session.getPhase())) && !autoAdvance(session))) {
            session.setPhaseEndsAt(null);
        } else {
            session.setPhaseEndsAt(now.plusNanos(Math.max(0, session.getPhaseRemainingMs()) * 1_000_000));
        }
        session.setPhaseRemainingMs(null);
        session.setPausedAt(null);
        touchLease(session);
        sessionMapper.updateById(session);
        progressLocked(session);
        return buildSnapshot(sessionMapper.selectById(sessionId), player);
    }

    @Transactional
    public void abandon(Long sessionId, PlayerIdentity player) {
        GameSession session = requireOwnedLocked(sessionId, player);
        if ("COMPLETED".equals(session.getStatus())) return;
        if (session.getRoomId() != null) bad("多人游戏不能由单个玩家结束，请离开房间");
        session.setStatus("ABANDONED");
        session.setPhase("COMPLETED");
        session.setPhaseEndsAt(null);
        session.setActivityLeaseUntil(null);
        sessionMapper.updateById(session);
        cancelAiTasks(sessionId);
        releasePendingOrders(sessionId, session.getCurrentRound());
        credentialService.purgeSession(sessionId);
    }

    @Transactional
    public void deleteRecord(Long sessionId, PlayerIdentity player) {
        GameSession session = sessionMapper.selectByIdForUpdate(sessionId);
        if (session == null || player == null) throw new BusinessException(ErrorCode.NOT_FOUND, "游玩记录不存在");
        GuessStockParticipant participant = participantMapper.selectOne(new LambdaQueryWrapper<GuessStockParticipant>()
                .eq(GuessStockParticipant::getSessionId, sessionId)
                .eq(GuessStockParticipant::getParticipantType, "PLAYER")
                .eq(GuessStockParticipant::getPlayerIdentityKey, player.key()).last("LIMIT 1"));
        if (participant == null || Integer.valueOf(1).equals(participant.getRecordDeleted())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "游玩记录不存在");
        }
        if (session.getRoomId() != null && List.of("ACTIVE", "PAUSED").contains(session.getStatus())) {
            bad("进行中的多人游戏不能删除记录，请先完成或关闭房间");
        }
        if (session.getRoomId() == null && !List.of("COMPLETED", "ABANDONED").contains(session.getStatus())) {
            session.setStatus("ABANDONED");
            session.setPhase("COMPLETED");
            session.setPhaseEndsAt(null);
            session.setActivityLeaseUntil(null);
            sessionMapper.updateById(session);
            cancelAiTasks(sessionId);
            releasePendingOrders(sessionId, session.getCurrentRound());
            credentialService.purgeSession(sessionId);
        }
        participant.setRecordDeleted(1);
        participantMapper.updateById(participant);
    }

    @Transactional
    public void reevaluateRoomGate(Long sessionId) {
        if (sessionId == null) return;
        GameSession session = sessionMapper.selectByIdForUpdate(sessionId);
        if (session == null || session.getRoomId() == null || !"ACTIVE".equals(session.getStatus())) return;
        List<GameRoomMember> members = allActiveMembers(session.getRoomId());
        if (members.isEmpty()) return;
        if (members.stream().allMatch(item -> session.getId().equals(item.getRestartSessionId()))) {
            abandonForRestart(session, LocalDateTime.now());
        } else if (isEventPhase(session.getPhase()) && members.stream()
                .allMatch(item -> session.getCurrentRound().equals(item.getEventAckRound())
                        && session.getPhase().equals(item.getEventAckStage()))) {
            continueAfterEvent(session, LocalDateTime.now());
        } else if ((isResultPhase(session.getPhase()) || isCreditPhase(session.getPhase())
                || isRoundStartPhase(session.getPhase())
                || (isTradingPhase(session.getPhase()) && "MANUAL".equals(session.getProgressionMode())))
                && members.stream().allMatch(item -> session.getCurrentRound().equals(item.getReadyRound())
                && session.getPhase().equals(item.getReadyStage()))) {
            progressFromGate(session, LocalDateTime.now());
        }
    }

    private void progressFromGate(GameSession session, LocalDateTime now) {
        if (isRoundStartPhase(session.getPhase())) continueAfterRoundStart(session, now);
        else if ("CREDIT_TRADING".equals(session.getPhase())) startTrading(session, OPENING, now);
        else if ("CREDIT_REPAYMENT".equals(session.getPhase())) finishRound(session, now);
        else if ("OPENING_TRADING".equals(session.getPhase())) clearOpening(session, now);
        else if ("OPENING_RESULT_ACK".equals(session.getPhase())) prepareClosingPhase(session, now);
        else if ("CLOSING_TRADING".equals(session.getPhase())) clearClosing(session, now);
        else if ("ROUND_RESULT_ACK".equals(session.getPhase())) {
            if (creditEnabled(session)) startRepaymentPhase(session, now);
            else finishRound(session, now);
        }
    }

    private void resetSession(GameSession session, LocalDateTime now) {
        GameVersion version = gameService.requireVersion(session.getGameVersionId());
        GuessStockConfig config = gameService.readConfig(version.getConfigJson());
        tradeMapper.delete(new LambdaQueryWrapper<GuessStockTrade>().eq(GuessStockTrade::getSessionId, session.getId()));
        aiTaskMapper.delete(new LambdaQueryWrapper<GuessStockAiTask>().eq(GuessStockAiTask::getSessionId, session.getId()));
        orderMapper.delete(new LambdaQueryWrapper<GuessStockOrder>().eq(GuessStockOrder::getSessionId, session.getId()));
        eventImpactSliceMapper.delete(new LambdaQueryWrapper<GuessStockEventImpactSlice>()
                .eq(GuessStockEventImpactSlice::getSessionId, session.getId()));
        roundMapper.delete(new LambdaQueryWrapper<GuessStockRound>().eq(GuessStockRound::getSessionId, session.getId()));
        eventChainStateMapper.delete(new LambdaQueryWrapper<GuessStockEventChainState>()
                .eq(GuessStockEventChainState::getSessionId, session.getId()));
        researchActionMapper.delete(new LambdaQueryWrapper<GuessStockResearchAction>()
                .eq(GuessStockResearchAction::getSessionId, session.getId()));
        researchAccountMapper.delete(new LambdaQueryWrapper<GuessStockResearchAccount>()
                .eq(GuessStockResearchAccount::getSessionId, session.getId()));
        financialReportMapper.delete(new LambdaQueryWrapper<GuessStockFinancialReport>()
                .eq(GuessStockFinancialReport::getSessionId, session.getId()));
        actionMapper.delete(new LambdaQueryWrapper<GameSessionAction>().eq(GameSessionAction::getSessionId, session.getId()));
        creditService.reset(session.getId());
        for (GuessStockParticipant participant : participants(session.getId())) {
            BigDecimal initialCash = participant.getInitialCash() == null ? config.initialCash() : participant.getInitialCash();
            participant.setCashBalance(GuessStockRules.money(initialCash));
            participant.setReservedCash(ZERO);
            participant.setFinalAsset(null);
            participant.setFinalRank(null);
            participantMapper.updateById(participant);
            GuessStockPosition position = positionMapper.selectByParticipantForUpdate(participant.getId());
            position.setQuantity(0L);
            position.setReservedQuantity(0L);
            position.setAverageCost(ZERO);
            positionMapper.updateById(position);
        }
        if (session.getRoomId() != null) {
            for (GameRoomMember member : allActiveMembers(session.getRoomId())) {
                member.setEventAckRound(null);
                member.setEventAckStage(null);
                member.setReadyRound(null);
                member.setReadyStage(null);
                member.setRestartSessionId(null);
                roomMemberMapper.updateById(member);
            }
            GameRoom room = roomMapper.selectByIdForUpdate(session.getRoomId());
            if (room != null) {
                room.setStatus("PLAYING");
                room.setCompletedAt(null);
                room.setStartedAt(now);
                roomMapper.updateById(room);
            }
        }
        session.setStatus("ACTIVE");
        session.setCurrentRound(1);
        session.setPhase("OPENING_TRADING");
        session.setPhaseEndsAt(null);
        session.setPhaseRemainingMs(null);
        session.setPausedAt(null);
        session.setResultJson(null);
        session.setCompletedAt(null);
        session.setStartedAt(now);
        session.setScenarioSeed(mixedSessionSeed(version.getScenarioSeed(), session.getId()));
        touchLease(session);
        sessionMapper.updateById(session);
        GuessStockRound round = createRound(session, version, config, 1, config.initialPrice());
        prepareRoundPhase(session, round, config, now);
        EntertainmentGame game = gameMapper.selectByIdForUpdate(session.getGameId());
        game.setPlayCount((game.getPlayCount() == null ? 0 : game.getPlayCount()) + 1);
        gameMapper.updateById(game);
    }

    private void abandonForRestart(GameSession session, LocalDateTime now) {
        session.setStatus("ABANDONED");
        session.setPhase("COMPLETED");
        session.setPhaseEndsAt(null);
        session.setActivityLeaseUntil(null);
        session.setResultJson(write(Map.of("restartRequested", true)));
        session.setCompletedAt(now);
        sessionMapper.updateById(session);
        cancelAiTasks(session.getId());
        releasePendingOrders(session.getId(), session.getCurrentRound());
        credentialService.purgeSession(session.getId());
        if (session.getRoomId() != null) {
            GameRoom room = roomMapper.selectByIdForUpdate(session.getRoomId());
            if (room != null) {
                room.setStatus("COMPLETED");
                room.setCompletedAt(now);
                roomMapper.updateById(room);
            }
        }
    }

    public EntertainmentDtos.LeaderboardResponse leaderboard(Long gameId, PlayerIdentity viewer) {
        EntertainmentGame game = gameService.requirePlayable(gameId);
        GameVersion version = gameService.requireVersion(game.getCurrentVersionId());
        String versionLabel = version.getVersionLabel() == null ? "v" + version.getVersionNo() : version.getVersionLabel();
        List<Long> versionIds = gameService.versionsForLabel(gameId, versionLabel).stream().map(GameVersion::getId).toList();
        List<GameLeaderboardEntry> rows = leaderboardMapper.selectList(new LambdaQueryWrapper<GameLeaderboardEntry>()
                .in(GameLeaderboardEntry::getGameVersionId, versionIds)
                .orderByDesc(GameLeaderboardEntry::getScore)
                .orderByDesc(GameLeaderboardEntry::getFinalAsset)
                .orderByAsc(GameLeaderboardEntry::getCompletedAt));
        Map<String, GameLeaderboardEntry> bestByPlayer = new LinkedHashMap<>();
        for (GameLeaderboardEntry row : rows) bestByPlayer.putIfAbsent(row.getPlayerIdentityKey(), row);
        rows = bestByPlayer.values().stream().limit(100).toList();
        List<EntertainmentDtos.LeaderboardEntryResponse> entries = new ArrayList<>();
        EntertainmentDtos.LeaderboardEntryResponse current = null;
        for (int i = 0; i < rows.size(); i++) {
            GameLeaderboardEntry row = rows.get(i);
            boolean mine = viewer != null && viewer.key().equals(row.getPlayerIdentityKey());
            EntertainmentDtos.LeaderboardEntryResponse response = leaderboardEntry(i + 1L, row, mine);
            entries.add(response);
            if (mine) current = response;
        }
        if (current == null && viewer != null) {
            GameLeaderboardEntry mine = leaderboardMapper.selectList(new LambdaQueryWrapper<GameLeaderboardEntry>()
                    .in(GameLeaderboardEntry::getGameVersionId, versionIds)
                    .eq(GameLeaderboardEntry::getPlayerIdentityKey, viewer.key())
                    .orderByDesc(GameLeaderboardEntry::getScore).orderByDesc(GameLeaderboardEntry::getFinalAsset))
                    .stream().findFirst().orElse(null);
            if (mine != null) {
                long rank = bestByPlayer.values().stream().takeWhile(row -> !row.getPlayerIdentityKey().equals(viewer.key())).count() + 1;
                current = leaderboardEntry(rank, mine, true);
            }
        }
        return new EntertainmentDtos.LeaderboardResponse(gameId, version.getId(), version.getVersionNo(), versionLabel, entries, current);
    }

    public PageResponse<EntertainmentDtos.SessionResponse> history(PlayerIdentity player, int page, int pageSize) {
        int safePage = Math.max(1, page);
        int safeSize = Math.min(20, Math.max(1, pageSize));
        List<Long> roomIds = roomMemberMapper.selectList(new LambdaQueryWrapper<GameRoomMember>()
                        .eq(GameRoomMember::getPlayerIdentityKey, player.key()))
                .stream().map(GameRoomMember::getRoomId).distinct().toList();
        List<Long> deletedSessionIds = participantMapper.selectList(new LambdaQueryWrapper<GuessStockParticipant>()
                        .eq(GuessStockParticipant::getParticipantType, "PLAYER")
                        .eq(GuessStockParticipant::getPlayerIdentityKey, player.key())
                        .eq(GuessStockParticipant::getRecordDeleted, 1))
                .stream().map(GuessStockParticipant::getSessionId).distinct().toList();
        LambdaQueryWrapper<GameSession> query = new LambdaQueryWrapper<>();
        query.and(wrapper -> {
            wrapper.eq(GameSession::getPlayerIdentityKey, player.key());
            if (!roomIds.isEmpty()) wrapper.or().in(GameSession::getRoomId, roomIds);
        }).notIn(!deletedSessionIds.isEmpty(), GameSession::getId, deletedSessionIds)
                .orderByDesc(GameSession::getUpdatedAt);
        Page<GameSession> result = sessionMapper.selectPage(new Page<>(safePage, safeSize),
                query);
        return new PageResponse<>(result.getRecords().stream().map(session -> buildSnapshot(session, player)).toList(),
                safePage, safeSize, result.getTotal());
    }

    @Scheduled(fixedDelay = 500)
    public void tick() {
        try {
            LocalDateTime now = LocalDateTime.now();
            aiTaskMapper.recoverStale(now);
            for (Long id : sessionMapper.selectExpiredLeaseIds(now, 50)) {
                transactionTemplate.executeWithoutResult(status -> {
                    GameSession session = sessionMapper.selectByIdForUpdate(id);
                    if (session != null && "ACTIVE".equals(session.getStatus())
                            && session.getActivityLeaseUntil() != null && !session.getActivityLeaseUntil().isAfter(now)) {
                        pauseLocked(session, now);
                    }
                });
            }
            List<CompletableFuture<Void>> aiFutures = aiTaskMapper.selectDue(now, 100).stream()
                    .map(task -> CompletableFuture.runAsync(() -> {
                        Boolean claimed = transactionTemplate.execute(status -> aiTaskMapper.claim(task.getId()) == 1);
                        if (Boolean.TRUE.equals(claimed)) transactionTemplate.executeWithoutResult(
                                status -> executeAiTaskLocked(task.getId(), LocalDateTime.now()));
                    }, entertainmentAiExecutor))
                    .toList();
            CompletableFuture.allOf(aiFutures.toArray(CompletableFuture[]::new)).join();
            for (Long id : sessionMapper.selectDueIds(now, 50)) {
                transactionTemplate.executeWithoutResult(status -> {
                    GameSession session = sessionMapper.selectByIdForUpdate(id);
                    if (session != null) progressLocked(session);
                });
            }
        } catch (BadSqlGrammarException exception) {
            if (!isMissingEntertainmentSchema(exception)) throw exception;
        }
    }

    private static boolean isMissingEntertainmentSchema(Throwable exception) {
        Throwable current = exception;
        while (current != null) {
            String message = current.getMessage();
            if (message != null && message.contains("game_sessions")
                    && (message.contains("doesn't exist") || message.contains("does not exist"))) return true;
            current = current.getCause();
        }
        return false;
    }

    private EntertainmentDtos.SessionResponse snapshotLocked(Long sessionId, PlayerIdentity player, boolean touch) {
        GameSession session = requireOwnedLocked(sessionId, player);
        progressLocked(session);
        session = sessionMapper.selectByIdForUpdate(sessionId);
        if (touch && "ACTIVE".equals(session.getStatus())) {
            touchLease(session);
            sessionMapper.updateById(session);
        }
        return buildSnapshot(session, player);
    }

    private void progressLocked(GameSession session) {
        if (session == null || !"ACTIVE".equals(session.getStatus())) return;
        if (isCreditPhase(session.getPhase()) && !creditPhaseTimed(session)
                && session.getPhaseEndsAt() != null) {
            // Unlimited bank phases must remain gates even for sessions paused by older builds.
            session.setPhaseEndsAt(null);
            session.setPhaseRemainingMs(null);
            sessionMapper.updateById(session);
        }
        LocalDateTime now = LocalDateTime.now();
        int guard = 0;
        while (session.getPhaseEndsAt() != null && !session.getPhaseEndsAt().isAfter(now) && guard++ < 4) {
            if (isTradingPhase(session.getPhase()) && (orderMutationsInFlight(session.getId()) > 0
                    || now.isBefore(session.getPhaseEndsAt().plusNanos(ORDER_DRAIN_GRACE_MILLIS * 1_000_000)))) {
                return;
            }
            if ("OPENING_TRADING".equals(session.getPhase())) clearOpening(session, now);
            else if ("CLOSING_TRADING".equals(session.getPhase())) clearClosing(session, now);
            else if (isRoundStartPhase(session.getPhase())) continueAfterRoundStart(session, now);
            else if (isCreditPhase(session.getPhase())) progressFromGate(session, now);
            else if (isEventPhase(session.getPhase()) && session.getRoomId() == null && autoAdvance(session)) {
                continueAfterEvent(session, now);
            } else if (isResultPhase(session.getPhase()) && session.getRoomId() == null && autoAdvance(session)) {
                progressFromGate(session, now);
            }
            else return;
            session = sessionMapper.selectByIdForUpdate(session.getId());
        }
    }

    private void setGatePhase(GameSession session, String phase, LocalDateTime now) {
        session.setPhase(phase);
        session.setPhaseEndsAt(session.getRoomId() == null && autoAdvance(session)
                ? now.plusNanos(scaledMillis(AUTO_GATE_MILLIS, playbackSpeed(session)) * 1_000_000)
                : null);
        sessionMapper.updateById(session);
    }

    private static BigDecimal normalizePlaybackSpeed(BigDecimal speed) {
        BigDecimal normalized = speed == null ? BigDecimal.ONE.setScale(1) : speed.setScale(1, RoundingMode.HALF_UP);
        if (!PLAYBACK_SPEEDS.contains(normalized)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "倍速只支持 x1、x1.5、x2、x3、x5");
        }
        return normalized;
    }

    private static BigDecimal playbackSpeed(GameSession session) {
        return session.getPlaybackSpeed() == null ? BigDecimal.ONE.setScale(1) : session.getPlaybackSpeed();
    }

    private static boolean autoAdvance(GameSession session) {
        return Integer.valueOf(1).equals(session.getAutoAdvance());
    }

    private static long scaledMillis(long baseMillis, BigDecimal speed) {
        return Math.max(100, BigDecimal.valueOf(baseMillis).divide(speed, 0, RoundingMode.HALF_UP).longValue());
    }

    private static void scalePhaseDeadline(GameSession session, LocalDateTime now,
                                           BigDecimal oldSpeed, BigDecimal newSpeed) {
        long remaining = Math.max(0, Duration.between(now, session.getPhaseEndsAt()).toMillis());
        long adjusted = BigDecimal.valueOf(remaining).multiply(oldSpeed)
                .divide(newSpeed, 0, RoundingMode.HALF_UP).longValue();
        session.setPhaseEndsAt(now.plusNanos(Math.max(100, adjusted) * 1_000_000));
    }

    private void scalePendingAiTasks(GameSession session, LocalDateTime now,
                                     BigDecimal oldSpeed, BigDecimal newSpeed) {
        if (!isTradingPhase(session.getPhase())) return;
        long phaseRemaining = Math.max(100, Duration.between(now, session.getPhaseEndsAt()).toMillis());
        List<GuessStockAiTask> tasks = aiTaskMapper.selectList(new LambdaQueryWrapper<GuessStockAiTask>()
                .eq(GuessStockAiTask::getSessionId, session.getId())
                .eq(GuessStockAiTask::getRoundNo, session.getCurrentRound())
                .eq(GuessStockAiTask::getStage, stageForPhase(session.getPhase()))
                .eq(GuessStockAiTask::getDecisionStatus, "PENDING"));
        for (GuessStockAiTask task : tasks) {
            long remaining = Math.max(0, Duration.between(now, task.getExecuteAt()).toMillis());
            long adjusted = BigDecimal.valueOf(remaining).multiply(oldSpeed)
                    .divide(newSpeed, 0, RoundingMode.HALF_UP).longValue();
            task.setExecuteAt(now.plusNanos(Math.min(Math.max(50, adjusted), Math.max(50, phaseRemaining - 50)) * 1_000_000));
            aiTaskMapper.updateById(task);
        }
    }

    private void startTrading(GameSession session, String stage, LocalDateTime now) {
        GameVersion version = gameService.requireVersion(session.getGameVersionId());
        GuessStockConfig config = gameService.readConfig(version.getConfigJson());
        session.setPhase(stage + "_TRADING");
        long tradingMillis = scaledMillis(config.tradingSeconds() * 1_000L, playbackSpeed(session));
        session.setPhaseEndsAt("MANUAL".equals(session.getProgressionMode())
                ? null : now.plusNanos(tradingMillis * 1_000_000));
        sessionMapper.updateById(session);
        String frozenSnapshotJson = write(createFrozenAiMarketSnapshot(session, stage, config));
        String snapshotVersion = contentVersion(frozenSnapshotJson);
        List<GuessStockParticipant> ais = participantMapper.selectList(new LambdaQueryWrapper<GuessStockParticipant>()
                .eq(GuessStockParticipant::getSessionId, session.getId())
                .eq(GuessStockParticipant::getParticipantType, "AI").orderByAsc(GuessStockParticipant::getId));
        for (int i = 0; i < ais.size(); i++) {
            long seed = mix(sessionSeed(session, version), session.getCurrentRound(), i + (OPENING.equals(stage) ? 0 : 100));
            long firstOffset = rangedOffset(tradingMillis, .18, .48, seed);
            insertAiTask(session, ais.get(i), stage, now, firstOffset, false, frozenSnapshotJson, snapshotVersion);
        }
    }

    private void startCreditPhase(GameSession session, LocalDateTime now) {
        GuessStockConfig config = gameService.readConfig(
                gameService.requireVersion(session.getGameVersionId()).getConfigJson());
        session.setPhase("CREDIT_TRADING");
        session.setPhaseEndsAt(Boolean.TRUE.equals(config.creditPhaseTimed())
                ? phaseDeadline(now, config.creditPhaseSeconds(), session) : null);
        sessionMapper.updateById(session);
    }

    private void startRepaymentPhase(GameSession session, LocalDateTime now) {
        LambdaQueryWrapper<GuessStockParticipant> humanQuery = new LambdaQueryWrapper<GuessStockParticipant>()
                .eq(GuessStockParticipant::getSessionId, session.getId())
                .eq(GuessStockParticipant::getParticipantType, "PLAYER")
                .and(wrapper -> wrapper.isNull(GuessStockParticipant::getRecordDeleted)
                        .or().eq(GuessStockParticipant::getRecordDeleted, 0));
        if (session.getRoomId() != null) {
            List<Long> activeMemberIds = allActiveMembers(session.getRoomId()).stream()
                    .map(GameRoomMember::getId).toList();
            if (activeMemberIds.isEmpty()) {
                finishRound(session, now);
                return;
            }
            humanQuery.in(GuessStockParticipant::getRoomMemberId, activeMemberIds);
        }
        List<GuessStockParticipant> humanParticipants = participantMapper.selectList(humanQuery);
        List<Long> humanParticipantIds = humanParticipants.stream().map(GuessStockParticipant::getId).toList();
        if (!creditService.hasOutstandingDebt(session.getId(), humanParticipantIds)) {
            finishRound(session, now);
            return;
        }
        GuessStockConfig config = gameService.readConfig(
                gameService.requireVersion(session.getGameVersionId()).getConfigJson());
        if ("INLINE".equalsIgnoreCase(config.creditInteractionMode())) {
            boolean finalRound = session.getCurrentRound() >= config.roundCount();
            BigDecimal price = currentRound(session).getClosePrice();
            boolean needsAttention = humanParticipants.stream().anyMatch(participant -> {
                GuessStockPosition position = positionMapper.selectByParticipant(participant.getId());
                GuessStockCreditService.CreditStatus status = creditService.status(
                        session.getId(), participant, position, price, config);
                return status.principal().add(status.accruedInterest()).signum() > 0
                        && needsInlineRepaymentAttention(finalRound, session.getCurrentRound(),
                        status.nearestMaturityRound(), status.risk().riskState());
            });
            if (!needsAttention) {
                finishRound(session, now);
                return;
            }
        }
        session.setPhase("CREDIT_REPAYMENT");
        session.setPhaseEndsAt(Boolean.TRUE.equals(config.repaymentPhaseTimed())
                ? phaseDeadline(now, config.repaymentPhaseSeconds(), session) : null);
        sessionMapper.updateById(session);
    }

    static boolean needsInlineRepaymentAttention(boolean finalRound, int currentRound,
                                                  int nearestMaturityRound, String riskState) {
        if (finalRound || (nearestMaturityRound > 0 && nearestMaturityRound <= currentRound)) return true;
        return riskState != null && !"SAFE".equalsIgnoreCase(riskState);
    }

    private static LocalDateTime phaseDeadline(LocalDateTime now, Integer seconds, GameSession session) {
        long phaseMillis = scaledMillis((seconds == null ? 30 : seconds) * 1_000L, playbackSpeed(session));
        return now.plusNanos(phaseMillis * 1_000_000);
    }

    private void startRoundIntro(GameSession session, LocalDateTime now) {
        session.setPhase("ROUND_START");
        session.setPhaseEndsAt(session.getRoomId() == null
                ? now.plusNanos(ROUND_START_FALLBACK_MILLIS * 1_000_000) : null);
        sessionMapper.updateById(session);
    }

    private void continueAfterRoundStart(GameSession session, LocalDateTime now) {
        GameVersion version = gameService.requireVersion(session.getGameVersionId());
        GuessStockConfig config = gameService.readConfig(version.getConfigJson());
        prepareOpeningPrelude(session, currentRound(session), config, now);
    }

    private boolean creditEnabled(GameSession session) {
        GuessStockConfig config = gameService.readConfig(
                gameService.requireVersion(session.getGameVersionId()).getConfigJson());
        return shouldOpenCreditPhase(config.loanEnabled());
    }

    private boolean creditPhaseTimed(GameSession session) {
        GuessStockConfig config = gameService.readConfig(
                gameService.requireVersion(session.getGameVersionId()).getConfigJson());
        return "CREDIT_TRADING".equals(session.getPhase())
                ? Boolean.TRUE.equals(config.creditPhaseTimed())
                : "CREDIT_REPAYMENT".equals(session.getPhase())
                && Boolean.TRUE.equals(config.repaymentPhaseTimed());
    }

    static boolean shouldOpenCreditPhase(Boolean loanEnabled) {
        return Boolean.TRUE.equals(loanEnabled);
    }

    static boolean shouldOpenCreditPhase(Boolean loanEnabled, String interactionMode) {
        return Boolean.TRUE.equals(loanEnabled) && !"INLINE".equalsIgnoreCase(interactionMode);
    }

    private static long rangedOffset(long durationMillis, double minRatio, double maxRatio, long seed) {
        long min = Math.max(50, Math.round(durationMillis * minRatio));
        long max = Math.max(min, Math.min(durationMillis - 80, Math.round(durationMillis * maxRatio)));
        return min + Math.floorMod(seed, Math.max(1, max - min + 1));
    }

    private void insertAiTask(GameSession session, GuessStockParticipant ai, String stage,
                              LocalDateTime now, long offset, boolean revision, String frozenSnapshotJson,
                              String snapshotVersion) {
        GuessStockAiTask task = new GuessStockAiTask();
        task.setSessionId(session.getId());
        task.setParticipantId(ai.getId());
        task.setRoundNo(session.getCurrentRound());
        task.setStage(stage);
        task.setExecuteAt(now.plusNanos(Math.max(50, offset) * 1_000_000));
        task.setDecisionStatus("PENDING");
        task.setMarketSnapshotJson(frozenSnapshotJson);
        task.setSnapshotVersion(snapshotVersion);
        task.setPromptVersion(AI_PROMPT_VERSION);
        task.setIdempotencyKey("AI:" + session.getId() + ":" + session.getCurrentRound() + ":" + stage + ":" + ai.getId()
                + (revision ? ":REVISION" : ""));
        try { aiTaskMapper.insert(task); } catch (DuplicateKeyException ignored) { }
    }

    private static String contentVersion(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(16);
            for (int index = 0; index < 8; index++) result.append("%02x".formatted(digest[index]));
            return result.toString();
        } catch (Exception ignored) {
            return Integer.toHexString(value.hashCode());
        }
    }

    private void executeAiTaskLocked(Long taskId, LocalDateTime now) {
        long startedAt = System.nanoTime();
        GuessStockAiTask task = aiTaskMapper.selectById(taskId);
        if (task == null || !("PENDING".equals(task.getDecisionStatus())
                || "PROCESSING".equals(task.getDecisionStatus()))) return;
        if ("PENDING".equals(task.getDecisionStatus())) {
            if (aiTaskMapper.claim(taskId) != 1) return;
            task = aiTaskMapper.selectById(taskId);
        }
        GameSession session = sessionMapper.selectById(task.getSessionId());
        if (session == null || !"ACTIVE".equals(session.getStatus()) || !isTradingPhase(session.getPhase())
                || !task.getRoundNo().equals(session.getCurrentRound())
                || !task.getStage().equals(stageForPhase(session.getPhase()))) {
            task.setDecisionStatus("CANCELLED");
            task.setDecisionReason("场次或交易阶段已经结束");
            task.setExecutedAt(now);
            aiTaskMapper.updateById(task);
            return;
        }
        try {
            task.setDecisionReason(decideAiOrder(session, task));
            task.setDecisionStatus("EXECUTED");
        } catch (AiTaskCancelledException exception) {
            task.setDecisionStatus("CANCELLED");
            task.setDecisionReason("场次或交易阶段已经结束");
            task.setErrorMessage(exception.getMessage());
        } catch (AiDecisionUnresolvedException exception) {
            task.setDecisionStatus("UNRESOLVED");
            task.setDecisionReason("观点已失效，本阶段不新增仓位");
            task.setErrorMessage(exception.getMessage());
        } catch (ModelAiDecisionService.ModelDecisionException exception) {
            task.setDecisionStatus("FAILED");
            task.setDecisionReason("本回合持有");
            task.setErrorMessage(exception.getMessage());
        }
        task.setExecutedAt(now);
        if (task.getLatencyMs() == null) {
            task.setLatencyMs(Math.max(0, (System.nanoTime() - startedAt) / 1_000_000));
        }
        task.setAttemptCount(task.getAttemptCount() == null ? 0 : task.getAttemptCount());
        aiTaskMapper.updateById(task);
    }

    private void executeRemainingAiTasks(GameSession session) {
        List<GuessStockAiTask> tasks = aiTaskMapper.selectList(new LambdaQueryWrapper<GuessStockAiTask>()
                .eq(GuessStockAiTask::getSessionId, session.getId())
                .eq(GuessStockAiTask::getRoundNo, session.getCurrentRound())
                .eq(GuessStockAiTask::getStage, stageForPhase(session.getPhase()))
                .eq(GuessStockAiTask::getDecisionStatus, "PENDING").orderByAsc(GuessStockAiTask::getExecuteAt));
        for (GuessStockAiTask task : tasks) executeAiTaskLocked(task.getId(), LocalDateTime.now());
    }

    private String decideAiOrder(GameSession session, GuessStockAiTask task) {
        GameVersion version = gameService.requireVersion(session.getGameVersionId());
        GuessStockConfig config = gameService.readConfig(version.getConfigJson());
        GuessStockParticipant ai = participantMapper.selectById(task.getParticipantId());
        GuessStockPosition position = positionMapper.selectByParticipant(ai.getId());
        GuessStockRound round = currentRound(session);
        List<GuessStockParticipant> ais = participantMapper.selectList(new LambdaQueryWrapper<GuessStockParticipant>()
                .eq(GuessStockParticipant::getSessionId, session.getId())
                .eq(GuessStockParticipant::getParticipantType, "AI").orderByAsc(GuessStockParticipant::getId));
        int aiIndex = Math.max(0, ais.stream().map(GuessStockParticipant::getId).toList().indexOf(ai.getId()));
        GuessStockConfig.AiPlayerConfig aiConfig = config.aiPlayers().get(Math.min(aiIndex, config.aiPlayers().size() - 1));
        boolean revision = task.getIdempotencyKey().endsWith(":REVISION");
        BigDecimal markPrice = referencePrice(round, task.getStage());
        BigDecimal totalAsset = ai.getCashBalance().add(markPrice.multiply(BigDecimal.valueOf(position.getQuantity())))
                .subtract(creditService.debt(session.getId(), ai.getId()));
        double currentPositionPercent = positionPercent(markPrice, position.getQuantity(), totalAsset);
        double maxPositionPercent = clamp(aiConfig.riskPercent().doubleValue(), 0,
                Boolean.TRUE.equals(config.loanEnabled()) ? 300 : 100);
        List<GuessStockRound> history = roundMapper.selectList(new LambdaQueryWrapper<GuessStockRound>()
                .eq(GuessStockRound::getSessionId, session.getId()).isNotNull(GuessStockRound::getClosePrice)
                .orderByDesc(GuessStockRound::getRoundNo).last("LIMIT 50"));
        Collections.reverse(history);
        AiMarketSnapshot snapshot = readFrozenAiMarketSnapshot(task, session, config);
        GuessStockCreditService.CreditStatus decisionCredit = creditService.status(session.getId(), ai, position,
                markPrice, config);
        long marketBuyQuantity = snapshot.publicBuyQuantity();
        long marketSellQuantity = snapshot.publicSellQuantity();
        GuessStockOrder existingOrder = orderMapper.selectOne(new LambdaQueryWrapper<GuessStockOrder>()
                .eq(GuessStockOrder::getSessionId, session.getId()).eq(GuessStockOrder::getParticipantId, ai.getId())
                .eq(GuessStockOrder::getRoundNo, session.getCurrentRound()).eq(GuessStockOrder::getStage, task.getStage())
                .eq(GuessStockOrder::getStatus, "PENDING").last("LIMIT 1"));
        String pendingOrder = existingOrder == null ? "无" : "%s %d股".formatted(existingOrder.getSide(), existingOrder.getQuantity());
        BigDecimal adjustableCash = ai.getCashBalance().subtract(ai.getReservedCash())
                .add(existingOrder != null && "BUY".equals(existingOrder.getSide()) ? existingOrder.getReservedAmount() : ZERO);
        Map<Long, GuessStockPosition> positions = positionMap(session.getId());
        BigDecimal rankingPrice = markPrice;
        Long rankingSessionId = session.getId();
        List<GuessStockParticipant> ranked = participants(session.getId()).stream()
                .sorted(Comparator.comparing((GuessStockParticipant item) -> {
                    BigDecimal asset = item.getCashBalance().add(rankingPrice.multiply(
                            BigDecimal.valueOf(positions.get(item.getId()).getQuantity())))
                            .subtract(creditService.debt(rankingSessionId, item.getId()));
                    BigDecimal initialCash = item.getInitialCash() == null ? config.initialCash() : item.getInitialCash();
                    return returnPercent(asset.subtract(initialCash), initialCash);
                }).reversed())
                .toList();
        int assetRank = Math.max(1, ranked.stream().map(GuessStockParticipant::getId).toList().indexOf(ai.getId()) + 1);
        AiTradingPolicy.Memory memory = aiMemory(session.getId(), ai.getId());
        AiInvestmentThesis previousThesis = aiThesisService.findActive(session.getId(), ai.getId());
        AiThesisConditionEvaluator.ValidationResult validation = aiConditionEvaluator.evaluate(previousThesis, snapshot);
        AiEvidenceDetector.EvidenceResult evidence = aiEvidenceDetector.detect(previousThesis, snapshot, validation);
        List<GuessStockTrade> roundTrades = tradesForRound(session.getId(), ai.getId(), session.getCurrentRound());
        BigDecimal usedTurnover = roundTrades.stream().map(GuessStockTrade::getAmount).reduce(ZERO, BigDecimal::add);
        BigDecimal roundStartAsset = roundStartAsset(ai, position, round.getPreviousClosePrice(), roundTrades)
                .subtract(creditService.debt(session.getId(), ai.getId()));
        double roundTurnoverPct = roundStartAsset.signum() == 0 ? 0 : usedTurnover.divide(roundStartAsset, 10,
                RoundingMode.HALF_UP).multiply(new BigDecimal("100")).doubleValue();
        double estimatedImpact = estimatedImpactPercent(aiConfig.maxOrderQuantity(), config, ranked.size());
        String publicEventName = snapshot.eventName();
        String publicEventDescription = snapshot.eventDescription();
        String modelEventDescription = snapshot.hasPublicEvent() ? publicEventDescription
                + "；类型=" + snapshot.eventType() + "，相关度=" + snapshot.eventRelevance()
                + "，可信度=" + snapshot.eventCredibility() + "，意外程度=" + snapshot.eventSurprise()
                + "，事件强度=" + snapshot.eventStrength() + "，发布时间=" + snapshot.eventPublishedStage()
                : publicEventDescription;
        ModelAiDecisionService.Decision decision;
        if (canKeepWithoutModel(previousThesis, validation, evidence)) {
            task.setModelRoute("KEEP");
            decision = keepDecision(previousThesis, "观点有效且没有事件、价格、趋势或时间方面的重要新证据");
        } else {
            AiTradingPolicy.Context fallbackContext = new AiTradingPolicy.Context(
                    ai.getAiProfile(), config.aiDifficulty(), session.getCurrentRound(), config.roundCount(), task.getStage(),
                    markPrice, position.getAverageCost(), adjustableCash, position.getQuantity(),
                    history.stream().map(item -> new AiTradingPolicy.MarketPoint(item.getOpenPrice(), item.getClosePrice())).toList(),
                    marketBuyQuantity, marketSellQuantity, assetRank, ranked.size(), publicEventName, publicEventDescription,
                    round.getEventType(), round.getEventRelevance(), round.getEventCredibility(), round.getEventSurprise(),
                    round.getEventStrength(), config.baseVolatilityPercent().doubleValue(),
                    config.marketLiquidityShares().doubleValue(), memory,
                    mix(sessionSeed(session, version), aiIndex + 1, 4701), revision);
            List<GameAiCredentialService.DecryptedCredential> credentials = credentialService.credentialsFor(session.getId(), session.getRoomId());
            try {
                if (credentials.isEmpty()) throw new ModelAiDecisionService.ModelDecisionException("未配置模型");
                GameAiCredentialService.DecryptedCredential credential = credentials.get(aiIndex % credentials.size());
                task.setProvider(URI.create(credential.baseUrl()).getHost());
                task.setModel(credential.model());
                String modelRoute = modelRoute(snapshot, validation, evidence);
                task.setModelRoute(modelRoute);
                task.setDecisionCacheKey("GS:%d:AI:%d:R:%d:%s:SNAP:%s:PROMPT:%s:THESIS:%d:%s".formatted(
                        session.getId(), ai.getId(), session.getCurrentRound(), task.getStage(), task.getSnapshotVersion(),
                        AI_PROMPT_VERSION, previousThesis == null ? 0 : previousThesis.getVersion(), modelRoute));
                ModelAiDecisionService.Context modelContext =
                        new ModelAiDecisionService.Context(ai.getAiProfile(), config.aiDifficulty(), session.getCurrentRound(), config.roundCount(),
                                task.getStage(), markPrice.toPlainString(), position.getAverageCost().toPlainString(),
                                adjustableCash.toPlainString(), position.getQuantity(), visibleHistory(snapshot, config.aiDifficulty()),
                                marketBuyQuantity, marketSellQuantity, assetRank, ranked.size(), publicEventName,
                                modelEventDescription, snapshot.eventType(), snapshot.eventDirection(), snapshot.eventStrength(),
                                snapshot.hasPublicEvent() && task.getStage().equals(snapshot.eventPublishedStage()),
                                aiConfig.initialCash().toPlainString(), aiConfig.activityPercent().toPlainString(),
                                aiConfig.riskPercent().toPlainString(), aiConfig.strategyPrompt(), currentPositionPercent,
                                snapshot.return3Pct(), snapshot.return8Pct(), snapshot.return20Pct(), snapshot.volatility8Pct(),
                                snapshot.volatility20Pct(), snapshot.slope3Pct(), snapshot.slope8Pct(), snapshot.slope20Pct(),
                                snapshot.trendAcceleration3Pct(), snapshot.orderImbalancePct(), snapshot.recentHigh().toPlainString(),
                                snapshot.recentLow().toPlainString(), snapshot.marketRegime(), snapshot.previousMarketRegime(),
                                snapshot.regimeChanged(), memory.recentBuyCount(), memory.losingBuyCount(), memory.recentSellCount(), pendingOrder,
                                compactThesis(previousThesis, session.getCurrentRound()),
                                write(validation.triggeredConditions()), aiEvidencePacket(evidence, session.getId(), ai.getId()),
                                aiConfig.rebalanceBandPctPoints().toPlainString(), aiConfig.minHoldRounds(),
                                aiConfig.minTradeAssetPct().toPlainString(), aiConfig.maxRoundTurnoverPct().toPlainString(),
                                roundTurnoverPct, estimatedImpact, estimatedImpact,
                                snapshot.drawdownFromHighPct(), snapshot.maxDrawdownPct(), snapshot.trendState(),
                                snapshot.volatilityState(), snapshot.trendAge(), snapshot.regimeAge(),
                                snapshot.consecutiveDirectionRounds(), snapshot.publicEventDigest().residualSignal().name(),
                                snapshot.publicEventDigest().visibleRemainingStages(), revision,
                                decisionCredit.product() == null ? "" : decisionCredit.product().code(),
                                decisionCredit.principal().add(decisionCredit.accruedInterest()).toPlainString(),
                                decisionCredit.accruedInterest().toPlainString(), decisionCredit.risk().ltvPercent().doubleValue(),
                                decisionCredit.risk().riskState(), decisionCredit.availableCredit().toPlainString(),
                                decisionCredit.nearestMaturityRound());
                decision = cachedAgentDecision(task);
                if (decision != null) {
                    task.setModelRoute("CACHE");
                    task.setCacheHit(1);
                } else {
                    task.setCacheHit(0);
                    decision = requestValidatedModelDecision(task, credential, modelContext, modelRoute,
                            maxPositionPercent, previousThesis, validation, evidence,
                            config.roundCount(), ais.size());
                }
                if ("CACHE".equals(task.getModelRoute())) {
                    decision = withClampedTarget(decision, maxPositionPercent);
                    decision = aiDecisionValidator.validate(decision, previousThesis, validation, evidence);
                }
            } catch (ModelAiDecisionService.ModelDecisionException exception) {
                task.setFallbackReason(exception.getMessage());
                if (previousThesis != null && validation.invalidated()) {
                    aiThesisService.invalidate(previousThesis, session.getCurrentRound());
                    if (existingOrder != null) cancelPendingOrder(session, ai);
                    throw new AiDecisionUnresolvedException("活动观点已经失效，模型未能生成有效的新观点：" + exception.getMessage());
                }
                decision = aiTradingFallbackPolicy.decide(previousThesis, validation.invalidated(), fallbackContext,
                        currentPositionPercent, maxPositionPercent, "模型决策降级：" + exception.getMessage());
                decision = aiDecisionValidator.validate(decision, previousThesis, validation, evidence);
            }
        }

        GameSession executableSession = sessionMapper.selectByIdForUpdate(session.getId());
        if (executableSession == null || !"ACTIVE".equals(executableSession.getStatus())
                || !task.getRoundNo().equals(executableSession.getCurrentRound())
                || !isTradingPhase(executableSession.getPhase())
                || !task.getStage().equals(stageForPhase(executableSession.getPhase()))) {
            throw new AiTaskCancelledException("AI 决策完成时场次已离开原交易阶段");
        }
        session = executableSession;
        ai = participantMapper.selectByIdForUpdate(task.getParticipantId());
        position = positionMapper.selectByParticipantForUpdate(ai.getId());
        round = currentRound(session);
        markPrice = referencePrice(round, task.getStage());
        existingOrder = orderMapper.selectOne(new LambdaQueryWrapper<GuessStockOrder>()
                .eq(GuessStockOrder::getSessionId, session.getId()).eq(GuessStockOrder::getParticipantId, ai.getId())
                .eq(GuessStockOrder::getRoundNo, session.getCurrentRound()).eq(GuessStockOrder::getStage, task.getStage())
                .eq(GuessStockOrder::getStatus, "PENDING").last("LIMIT 1"));
        adjustableCash = ai.getCashBalance().subtract(ai.getReservedCash())
                .add(existingOrder != null && "BUY".equals(existingOrder.getSide()) ? existingOrder.getReservedAmount() : ZERO);
        totalAsset = ai.getCashBalance().add(markPrice.multiply(BigDecimal.valueOf(position.getQuantity())))
                .subtract(creditService.debt(session.getId(), ai.getId()));
        currentPositionPercent = positionPercent(markPrice, position.getQuantity(), totalAsset);
        roundTrades = tradesForRound(session.getId(), ai.getId(), session.getCurrentRound());
        usedTurnover = roundTrades.stream().map(GuessStockTrade::getAmount).reduce(ZERO, BigDecimal::add);
        roundStartAsset = roundStartAsset(ai, position, round.getPreviousClosePrice(), roundTrades)
                .subtract(creditService.debt(session.getId(), ai.getId()));

        maybePerformAiResearch(session, ai, config, decision, task);

        GuessStockCreditService.AiCreditCapacity creditCapacity = creditService.aiCapacity(session.getId(),
                session.getCurrentRound(), session.getPhase(), ai, position, markPrice, config,
                BigDecimal.valueOf(decision.targetPositionPct()));
        decision = withClampedTarget(decision, Math.min(maxPositionPercent,
                creditCapacity.executableExposure().doubleValue()));

        double previousTarget = previousThesis == null ? currentPositionPercent
                : previousThesis.getTargetPositionPct().doubleValue();
        AiInvestmentThesis appliedThesis = aiThesisService.apply(session.getId(), ai.getId(), session.getCurrentRound(),
                task.getStage(), markPrice, previousThesis, decision, validation, evidence);
        AiTargetPositionExecutor.Guard guard = new AiTargetPositionExecutor.Guard(aiConfig.riskPercent(),
                aiConfig.rebalanceBandPctPoints(), aiConfig.minHoldRounds(), aiConfig.minTradeAssetPct(),
                aiConfig.maxRoundTurnoverPct(), aiConfig.maxOrderQuantity(), aiConfig.maxEstimatedImpactPct(),
                aiConfig.maxPositionStepPctPoints(), aiConfig.highUrgencyPositionStepPctPoints(),
                aiConfig.reverseCooldownRounds());
        GuessStockTrade lastTrade = lastTrade(session.getId(), ai.getId());
        AiTargetPositionExecutor.ExecutionPlan plan = aiTargetPositionExecutor.plan(guard,
                new AiTargetPositionExecutor.Context(task.getStage(), session.getCurrentRound(),
                        decision.targetPositionPct(), currentPositionPercent, position.getQuantity(),
                        creditCapacity.availableCredit().add(adjustableCash), totalAsset,
                        roundStartAsset, usedTurnover, markPrice, decision.urgency(),
                        lastBuyRound(session.getId(), ai.getId()), roundTradeDirection(roundTrades),
                        validation.invalidated(), evidence.criticalRisk(), config.marketLiquidityShares(), ranked.size(),
                        config.maxOrderImpactPercent(), lastTrade == null ? null : lastTrade.getRoundNo(),
                        lastTrade == null ? null : lastTrade.getSide(), snapshot.regimeChanged()));
        storeAgentDecision(task, decision);

        if ("HOLD".equals(plan.direction()) && existingOrder != null) {
            cancelPendingOrder(session, ai);
        } else if (!"HOLD".equals(plan.direction())) {
            if ("BUY".equals(plan.direction())) {
                BigDecimal requiredCredit = plan.estimatedValue().subtract(adjustableCash).max(ZERO);
                creditService.autoBorrow(session.getId(), session.getCurrentRound(), session.getPhase(), ai, position,
                        markPrice, config, creditCapacity, requiredCredit,
                        "AI-CREDIT:" + task.getId());
            }
            if ("TARGET_AUCTION".equals(config.tradingMode())) {
                placeTargetOrder(session, ai, BigDecimal.valueOf(plan.targetPositionPct()), task.getIdempotencyKey());
            } else {
                placeOrder(session, ai, plan.direction(), plan.shares(), false, task.getIdempotencyKey(), "AI");
            }
        }
        auditAiDecision(session, ai, task, appliedThesis, previousTarget, currentPositionPercent,
                decision, evidence, validation, plan, totalAsset);
        return (revision ? "Agent 二次评估：" : "Agent 决策：") + decision.decisionType()
                + "，目标敞口 " + BigDecimal.valueOf(plan.targetPositionPct()).setScale(1, RoundingMode.HALF_UP).toPlainString()
                + "%；执行 " + plan.direction() + (plan.shares() > 0 ? " " + plan.shares() + " 股" : "")
                + "；" + plan.reason();
    }

    private void maybePerformAiResearch(GameSession session, GuessStockParticipant ai, GuessStockConfig config,
                                        ModelAiDecisionService.Decision decision, GuessStockAiTask task) {
        if (!Boolean.TRUE.equals(config.fundamentalEnabled()) || config.researchPoints() <= 0
                || decision.researchAction() == null || "NONE".equals(decision.researchAction())) return;
        try {
            ResearchEngine.Action action = ResearchEngine.Action.valueOf(decision.researchAction());
            performResearch(session, ai, config, action, "AI-RESEARCH:" + task.getId());
        } catch (IllegalArgumentException | BusinessException ignored) {
            // Research is optional; an exhausted or invalid budget must not block the market stage.
        }
    }

    private String aiEvidencePacket(AiEvidenceDetector.EvidenceResult evidence, Long sessionId, Long participantId) {
        GuessStockResearchAction latest = researchActionMapper.selectOne(
                new LambdaQueryWrapper<GuessStockResearchAction>()
                        .eq(GuessStockResearchAction::getSessionId, sessionId)
                        .eq(GuessStockResearchAction::getParticipantId, participantId)
                        .orderByDesc(GuessStockResearchAction::getId).last("LIMIT 1"));
        Map<String, Object> packet = new LinkedHashMap<>();
        packet.put("marketEvidence", evidence.evidences());
        if (latest != null) packet.put("lastResearch", readResearchResponse(latest.getResultJson()));
        return write(packet);
    }

    private static boolean canKeepWithoutModel(AiInvestmentThesis thesis,
                                                AiThesisConditionEvaluator.ValidationResult validation,
                                                AiEvidenceDetector.EvidenceResult evidence) {
        return thesis != null && !validation.invalidated() && !evidence.materialChange();
    }

    private ModelAiDecisionService.Decision requestValidatedModelDecision(
            GuessStockAiTask task,
            GameAiCredentialService.DecryptedCredential credential,
            ModelAiDecisionService.Context context,
            String modelRoute,
            double maxPositionPercent,
            AiInvestmentThesis previousThesis,
            AiThesisConditionEvaluator.ValidationResult validation,
            AiEvidenceDetector.EvidenceResult evidence,
            int roundCount,
            int aiPlayerCount) {
        if (!"FAST".equals(modelRoute)) {
            return validateModelDecision(invokeModel(task, credential, context, modelRoute, roundCount, aiPlayerCount), maxPositionPercent,
                    previousThesis, validation, evidence);
        }

        ModelAiDecisionService.Decision fastDecision;
        try {
            fastDecision = validateModelDecision(invokeModel(task, credential, context, "FAST", roundCount, aiPlayerCount), maxPositionPercent,
                    previousThesis, validation, evidence);
        } catch (ModelAiDecisionService.ModelDecisionException fastFailure) {
            switchTaskToStrong(task);
            try {
                return validateModelDecision(invokeModel(task, credential, context, "STRONG", roundCount, aiPlayerCount), maxPositionPercent,
                        previousThesis, validation, evidence);
            } catch (ModelAiDecisionService.ModelDecisionException strongFailure) {
                throw new ModelAiDecisionService.ModelDecisionException(
                        "快速模型和强模型均未生成有效决策：" + strongFailure.getMessage());
            }
        }
        if (fastDecision.confidence() >= .55) return fastDecision;

        switchTaskToStrong(task);
        try {
            return validateModelDecision(invokeModel(task, credential, context, "STRONG", roundCount, aiPlayerCount), maxPositionPercent,
                    previousThesis, validation, evidence);
        } catch (ModelAiDecisionService.ModelDecisionException ignored) {
            task.setModelRoute("FAST");
            if (task.getDecisionCacheKey() != null) {
                task.setDecisionCacheKey(task.getDecisionCacheKey().replace(":STRONG", ":FAST"));
            }
            return fastDecision;
        }
    }

    private ModelAiDecisionService.Decision invokeModel(
            GuessStockAiTask task,
            GameAiCredentialService.DecryptedCredential credential,
            ModelAiDecisionService.Context context,
            String modelRoute,
            int roundCount,
            int aiPlayerCount) {
        ensureAiModelBudget(task.getSessionId(), roundCount, aiPlayerCount);
        try {
            return aiModelGateway.decide(credential, context, modelRoute);
        } finally {
            ModelAiDecisionService.InvocationTelemetry telemetry = aiModelGateway.consumeLastTelemetry();
            if (telemetry != null) {
                task.setLatencyMs((task.getLatencyMs() == null ? 0 : task.getLatencyMs()) + telemetry.latencyMs());
                task.setPromptTokens((task.getPromptTokens() == null ? 0 : task.getPromptTokens()) + telemetry.promptTokens());
                task.setCompletionTokens((task.getCompletionTokens() == null ? 0 : task.getCompletionTokens())
                        + telemetry.completionTokens());
                task.setHttpStatus(telemetry.httpStatus() == 0 ? task.getHttpStatus() : telemetry.httpStatus());
                task.setAttemptCount((task.getAttemptCount() == null ? 0 : task.getAttemptCount())
                        + telemetry.attemptCount());
            }
        }
    }

    private void ensureAiModelBudget(Long sessionId, int roundCount, int aiPlayerCount) {
        List<GuessStockAiTask> tasks = aiTaskMapper.selectList(new LambdaQueryWrapper<GuessStockAiTask>()
                .eq(GuessStockAiTask::getSessionId, sessionId));
        int attempts = tasks.stream().map(GuessStockAiTask::getAttemptCount).filter(Objects::nonNull)
                .mapToInt(Integer::intValue).sum();
        long tokens = tasks.stream().mapToLong(item -> (long) (item.getPromptTokens() == null ? 0 : item.getPromptTokens())
                + (item.getCompletionTokens() == null ? 0 : item.getCompletionTokens())).sum();
        long failures = tasks.stream().filter(item -> item.getFallbackReason() != null && !item.getFallbackReason().isBlank()).count();
        int maxAttempts = Math.max(16, roundCount * Math.max(1, aiPlayerCount) * 3);
        long maxTokens = Math.max(40_000L, maxAttempts * 3_000L);
        int maxFailures = Math.max(4, roundCount * Math.max(1, aiPlayerCount) / 3);
        if (attempts >= maxAttempts || tokens >= maxTokens || failures >= maxFailures) {
            throw new ModelAiDecisionService.ModelDecisionException("本局 AI 模型预算已用尽，改用连续观点或本地策略");
        }
    }

    private ModelAiDecisionService.Decision validateModelDecision(
            ModelAiDecisionService.Decision decision,
            double maxPositionPercent,
            AiInvestmentThesis previousThesis,
            AiThesisConditionEvaluator.ValidationResult validation,
            AiEvidenceDetector.EvidenceResult evidence) {
        ModelAiDecisionService.Decision clamped = withClampedTarget(decision, maxPositionPercent);
        return aiDecisionValidator.validate(clamped, previousThesis, validation, evidence);
    }

    private static void switchTaskToStrong(GuessStockAiTask task) {
        task.setModelRoute("STRONG");
        if (task.getDecisionCacheKey() != null) {
            task.setDecisionCacheKey(task.getDecisionCacheKey().replace(":FAST", ":STRONG"));
        }
    }

    private ModelAiDecisionService.Decision cachedAgentDecision(GuessStockAiTask task) {
        if (task.getDecisionCacheKey() == null) return null;
        GuessStockAiTask cached = aiTaskMapper.selectOne(new LambdaQueryWrapper<GuessStockAiTask>()
                .eq(GuessStockAiTask::getDecisionCacheKey, task.getDecisionCacheKey())
                .eq(GuessStockAiTask::getDecisionStatus, "EXECUTED")
                .isNotNull(GuessStockAiTask::getDecisionJson)
                .ne(task.getId() != null, GuessStockAiTask::getId, task.getId())
                .orderByDesc(GuessStockAiTask::getId).last("LIMIT 1"));
        return cached == null ? null : readAgentDecision(cached.getDecisionJson());
    }

    private static String modelRoute(AiMarketSnapshot snapshot,
                                     AiThesisConditionEvaluator.ValidationResult validation,
                                     AiEvidenceDetector.EvidenceResult evidence) {
        double normalizedShock = Math.abs(snapshot.priceDeviationPct()) / Math.max(.5, snapshot.baseVolatilityPct());
        return validation.invalidated() || evidence.criticalRisk() || snapshot.regimeChanged() || normalizedShock >= 2
                ? "STRONG" : "FAST";
    }

    private String compactThesis(AiInvestmentThesis thesis, int roundNo) {
        if (thesis == null) return "null";
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("id", thesis.getThesisCode());
        value.put("version", thesis.getVersion());
        value.put("direction", thesis.getDirection());
        value.put("target", thesis.getTargetPositionPct());
        value.put("confidence", thesis.getConfidence());
        value.put("horizon", thesis.getTimeHorizon());
        value.put("age", Math.max(0, roundNo - thesis.getCreatedRound()));
        value.put("review", thesis.getReviewAfterRounds());
        value.put("expire", thesis.getExpireAfterRounds());
        try { value.put("invalid", objectMapper.readTree(thesis.getInvalidConditionsJson())); }
        catch (Exception ignored) { value.put("invalid", List.of()); }
        return write(value);
    }

    private static final class AiDecisionUnresolvedException extends RuntimeException {
        private AiDecisionUnresolvedException(String message) { super(message); }
    }

    private static final class AiTaskCancelledException extends RuntimeException {
        private AiTaskCancelledException(String message) { super(message); }
    }

    private AiMarketSnapshot createFrozenAiMarketSnapshot(GameSession session, String stage, GuessStockConfig config) {
        GuessStockRound round = currentRound(session);
        List<GuessStockRound> history = roundMapper.selectList(new LambdaQueryWrapper<GuessStockRound>()
                .eq(GuessStockRound::getSessionId, session.getId()).isNotNull(GuessStockRound::getClosePrice)
                .orderByDesc(GuessStockRound::getRoundNo).last("LIMIT 20"));
        Collections.reverse(history);
        GuessStockRound lastSettled = history.isEmpty() ? null : history.get(history.size() - 1);
        long buys = lastSettled == null || lastSettled.getOpeningBuyQuantity() == null
                ? 0 : lastSettled.getOpeningBuyQuantity();
        long sells = lastSettled == null || lastSettled.getClosingSellQuantity() == null
                ? 0 : lastSettled.getClosingSellQuantity();
        PublicEventDigest eventDigest = publicEventDigest(round, stage);
        return aiMarketSnapshotService.create(session.getCurrentRound(), config.roundCount(), stage,
                referencePrice(round, stage), round.getPreviousClosePrice(), history, round, buys, sells,
                config.baseVolatilityPercent().doubleValue(), eventDigest);
    }

    private static PublicEventDigest publicEventDigest(GuessStockRound round, String stage) {
        boolean eventIsPublic = OPENING.equals(stage)
                ? Integer.valueOf(1).equals(round.getOpeningEventOccurred())
                : Integer.valueOf(1).equals(round.getClosingEventOccurred());
        if (!eventIsPublic) return PublicEventDigest.none();
        PublicEventDigest.ResidualSignal signal = switch (round.getEventStrength() == null
                ? "" : round.getEventStrength().toUpperCase()) {
            case "STRONG" -> PublicEventDigest.ResidualSignal.HIGH;
            case "MEDIUM" -> PublicEventDigest.ResidualSignal.MEDIUM;
            default -> PublicEventDigest.ResidualSignal.LOW;
        };
        // Stage count is intentionally not inferred from private impact slices.
        return new PublicEventDigest(signal, 0);
    }

    private AiMarketSnapshot readFrozenAiMarketSnapshot(GuessStockAiTask task, GameSession session,
                                                         GuessStockConfig config) {
        if (task.getMarketSnapshotJson() != null) {
            try { return objectMapper.readValue(task.getMarketSnapshotJson(), AiMarketSnapshot.class); }
            catch (JsonProcessingException ignored) { }
        }
        return createFrozenAiMarketSnapshot(session, task.getStage(), config);
    }

    private static ModelAiDecisionService.Decision keepDecision(AiInvestmentThesis thesis, String reason) {
        return new ModelAiDecisionService.Decision("KEEP", thesis.getTargetPositionPct().doubleValue(),
                thesis.getConfidence().doubleValue(), "LOW", thesis.getMarketRegime(), thesis.getDirection(),
                thesis.getTimeHorizon(), List.of("NO_MATERIAL_NEW_EVIDENCE"), List.of(),
                thesis.getReviewAfterRounds(), thesis.getExpireAfterRounds(), reason);
    }

    private static ModelAiDecisionService.Decision withClampedTarget(ModelAiDecisionService.Decision decision,
                                                                      double maxPositionPercent) {
        return new ModelAiDecisionService.Decision(decision.decisionType(),
                clamp(decision.targetPositionPct(), 0, maxPositionPercent), decision.confidence(), decision.urgency(),
                decision.marketRegime(), decision.direction(), decision.timeHorizon(), decision.reasonCodes(),
                decision.invalidConditions(), decision.reviewAfterRounds(), decision.expireAfterRounds(), decision.reason(),
                decision.researchAction());
    }

    private List<GuessStockTrade> tradesForRound(Long sessionId, Long participantId, int roundNo) {
        return tradeMapper.selectList(new LambdaQueryWrapper<GuessStockTrade>()
                .eq(GuessStockTrade::getSessionId, sessionId).eq(GuessStockTrade::getParticipantId, participantId)
                .eq(GuessStockTrade::getRoundNo, roundNo).orderByAsc(GuessStockTrade::getId));
    }

    private static BigDecimal roundStartAsset(GuessStockParticipant participant, GuessStockPosition position,
                                              BigDecimal previousClose, List<GuessStockTrade> trades) {
        BigDecimal cash = participant.getCashBalance();
        long shares = position.getQuantity();
        for (GuessStockTrade trade : trades) {
            if ("BUY".equals(trade.getSide())) {
                cash = cash.add(trade.getAmount());
                shares -= trade.getQuantity();
            } else {
                cash = cash.subtract(trade.getAmount());
                shares += trade.getQuantity();
            }
        }
        return GuessStockRules.money(cash.add(previousClose.multiply(BigDecimal.valueOf(Math.max(0, shares)))));
    }

    private static double estimatedImpactPercent(long maxOrderQuantity, GuessStockConfig config, int participantCount) {
        double denominator = config.marketLiquidityShares().doubleValue() * Math.max(1, participantCount);
        if (denominator <= 0) return 0;
        return Math.min(config.maxOrderImpactPercent().doubleValue(),
                maxOrderQuantity / denominator * config.maxOrderImpactPercent().doubleValue());
    }

    private Integer lastBuyRound(Long sessionId, Long participantId) {
        GuessStockTrade trade = tradeMapper.selectOne(new LambdaQueryWrapper<GuessStockTrade>()
                .eq(GuessStockTrade::getSessionId, sessionId).eq(GuessStockTrade::getParticipantId, participantId)
                .eq(GuessStockTrade::getSide, "BUY").orderByDesc(GuessStockTrade::getId).last("LIMIT 1"));
        return trade == null ? null : trade.getRoundNo();
    }

    private GuessStockTrade lastTrade(Long sessionId, Long participantId) {
        return tradeMapper.selectOne(new LambdaQueryWrapper<GuessStockTrade>()
                .eq(GuessStockTrade::getSessionId, sessionId)
                .eq(GuessStockTrade::getParticipantId, participantId)
                .orderByDesc(GuessStockTrade::getId).last("LIMIT 1"));
    }

    private static String roundTradeDirection(List<GuessStockTrade> trades) {
        boolean bought = trades.stream().anyMatch(trade -> "BUY".equals(trade.getSide()));
        boolean sold = trades.stream().anyMatch(trade -> "SELL".equals(trade.getSide()));
        if (bought && sold) return "MIXED";
        if (bought) return "BUY";
        if (sold) return "SELL";
        return "NONE";
    }

    private void auditAiDecision(GameSession session, GuessStockParticipant ai, GuessStockAiTask task,
                                 AiInvestmentThesis thesis, double previousTarget, double currentPosition,
                                 ModelAiDecisionService.Decision decision,
                                 AiEvidenceDetector.EvidenceResult evidence,
                                 AiThesisConditionEvaluator.ValidationResult validation,
                                 AiTargetPositionExecutor.ExecutionPlan plan, BigDecimal totalAsset) {
        AiInvestmentDecision audit = new AiInvestmentDecision();
        audit.setSessionId(session.getId());
        audit.setParticipantId(ai.getId());
        audit.setThesisId(thesis == null ? null : thesis.getId());
        audit.setRoundNo(session.getCurrentRound());
        audit.setPhase(task.getStage());
        audit.setDecisionType(decision.decisionType());
        audit.setPreviousTargetPositionPct(BigDecimal.valueOf(previousTarget).setScale(4, RoundingMode.HALF_UP));
        audit.setTargetPositionPct(BigDecimal.valueOf(plan.targetPositionPct()).setScale(4, RoundingMode.HALF_UP));
        audit.setRequestedTargetPositionPct(BigDecimal.valueOf(plan.requestedTargetPositionPct())
                .setScale(4, RoundingMode.HALF_UP));
        audit.setStepTargetPositionPct(BigDecimal.valueOf(plan.targetPositionPct()).setScale(4, RoundingMode.HALF_UP));
        double executedDeltaPct = totalAsset.signum() <= 0 ? 0 : plan.estimatedValue()
                .divide(totalAsset, 10, RoundingMode.HALF_UP).multiply(new BigDecimal("100")).doubleValue();
        if ("SELL".equals(plan.direction())) executedDeltaPct = -executedDeltaPct;
        audit.setResultingPositionPct(BigDecimal.valueOf(clamp(currentPosition + executedDeltaPct, 0, 300))
                .setScale(4, RoundingMode.HALF_UP));
        audit.setCurrentPositionPct(BigDecimal.valueOf(currentPosition).setScale(4, RoundingMode.HALF_UP));
        audit.setConfidence(BigDecimal.valueOf(decision.confidence()).setScale(4, RoundingMode.HALF_UP));
        audit.setUrgency(decision.urgency());
        audit.setMaterialChange(evidence.materialChange() ? 1 : 0);
        audit.setNewEvidenceJson(write(evidence.evidences()));
        audit.setTriggeredConditionsJson(write(validation.triggeredConditions()));
        audit.setReasonCodesJson(write(decision.reasonCodes()));
        audit.setExecutedDirection(plan.direction());
        audit.setExecutedShares(plan.shares());
        audit.setExecutedValue(GuessStockRules.money(plan.estimatedValue()));
        audit.setExecutionReason(plan.reason());
        aiDecisionMapper.insert(audit);
    }

    private void storeAgentDecision(GuessStockAiTask task, ModelAiDecisionService.Decision decision) {
        try { task.setDecisionJson(objectMapper.writeValueAsString(decision)); }
        catch (JsonProcessingException ignored) { task.setDecisionJson(null); }
    }

    private String aiDecisionMemory(Long sessionId, Long participantId) {
        List<GuessStockAiTask> tasks = aiTaskMapper.selectList(new LambdaQueryWrapper<GuessStockAiTask>()
                .eq(GuessStockAiTask::getSessionId, sessionId).eq(GuessStockAiTask::getParticipantId, participantId)
                .eq(GuessStockAiTask::getDecisionStatus, "EXECUTED").orderByDesc(GuessStockAiTask::getId).last("LIMIT 8"));
        Collections.reverse(tasks);
        if (tasks.isEmpty()) return "暂无历史决策";
        return tasks.stream().map(item -> "R%d %s %s".formatted(item.getRoundNo(), item.getStage(),
                        truncate(item.getDecisionJson() == null ? item.getDecisionReason() : item.getDecisionJson(), 700)))
                .collect(Collectors.joining("；"));
    }

    private String aiTradeMemory(Long sessionId, Long participantId) {
        List<GuessStockTrade> trades = tradeMapper.selectList(new LambdaQueryWrapper<GuessStockTrade>()
                .eq(GuessStockTrade::getSessionId, sessionId).eq(GuessStockTrade::getParticipantId, participantId)
                .orderByDesc(GuessStockTrade::getId).last("LIMIT 10"));
        Collections.reverse(trades);
        if (trades.isEmpty()) return "暂无实际成交";
        return trades.stream().map(item -> "R%d %s %d股@%s".formatted(item.getRoundNo(), item.getSide(),
                item.getQuantity(), item.getExecutionPrice().toPlainString())).collect(Collectors.joining("；"));
    }

    private static MarketFacts marketFacts(List<GuessStockRound> history, BigDecimal currentPrice) {
        List<BigDecimal> prices = history.stream().map(GuessStockRound::getClosePrice)
                .collect(Collectors.toCollection(ArrayList::new));
        if (prices.isEmpty() || prices.get(prices.size() - 1).compareTo(currentPrice) != 0) prices.add(currentPrice);
        BigDecimal high = prices.stream().max(BigDecimal::compareTo).orElse(currentPrice);
        BigDecimal low = prices.stream().min(BigDecimal::compareTo).orElse(currentPrice);
        List<Double> returns = new ArrayList<>();
        for (int index = 1; index < prices.size(); index++) {
            if (prices.get(index - 1).signum() > 0) {
                returns.add(prices.get(index).divide(prices.get(index - 1), 10, RoundingMode.HALF_UP)
                        .subtract(BigDecimal.ONE).multiply(new BigDecimal("100")).doubleValue());
            }
        }
        double mean = returns.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double volatility = Math.sqrt(returns.stream().mapToDouble(value -> Math.pow(value - mean, 2)).average().orElse(0));
        return new MarketFacts(periodReturn(prices, 3), periodReturn(prices, 8), periodReturn(prices, 20),
                volatility, high, low);
    }

    private static double periodReturn(List<BigDecimal> prices, int period) {
        if (prices.size() < 2) return 0;
        int start = Math.max(0, prices.size() - 1 - period);
        BigDecimal baseline = prices.get(start);
        if (baseline.signum() == 0) return 0;
        return prices.get(prices.size() - 1).divide(baseline, 10, RoundingMode.HALF_UP)
                .subtract(BigDecimal.ONE).multiply(new BigDecimal("100")).doubleValue();
    }

    static long targetShares(BigDecimal totalAsset, BigDecimal referencePrice, double targetPositionPercent) {
        if (totalAsset.signum() <= 0 || referencePrice.signum() <= 0 || targetPositionPercent <= 0) return 0;
        return totalAsset.multiply(BigDecimal.valueOf(clamp(targetPositionPercent, 0, 300)))
                .divide(new BigDecimal("100"), 10, RoundingMode.HALF_UP)
                .divide(referencePrice, 0, RoundingMode.FLOOR).longValue();
    }

    private static double positionPercent(BigDecimal price, long quantity, BigDecimal totalAsset) {
        if (totalAsset.signum() <= 0) return 0;
        return price.multiply(BigDecimal.valueOf(quantity)).divide(totalAsset, 8, RoundingMode.HALF_UP)
                .multiply(new BigDecimal("100")).doubleValue();
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private MarketStateCalculator.MarketState marketState(Long sessionId, BigDecimal currentPrice,
                                                           double baseVolatilityPct) {
        List<BigDecimal> prices = roundMapper.selectList(new LambdaQueryWrapper<GuessStockRound>()
                        .eq(GuessStockRound::getSessionId, sessionId)
                        .isNotNull(GuessStockRound::getClosePrice)
                        .orderByDesc(GuessStockRound::getRoundNo).last("LIMIT 50"))
                .stream().map(GuessStockRound::getClosePrice)
                .filter(value -> value != null && value.signum() > 0)
                .collect(Collectors.toCollection(ArrayList::new));
        Collections.reverse(prices);
        if (prices.isEmpty() || prices.get(prices.size() - 1).compareTo(currentPrice) != 0) prices.add(currentPrice);
        MarketStateCalculator.MarketState state = null;
        for (int size = 1; size <= prices.size(); size++) {
            state = MarketStateCalculator.calculate(prices.subList(0, size), baseVolatilityPct, state);
        }
        return state;
    }

    private static void applyMarketState(GuessStockRound round, MarketStateCalculator.MarketState state) {
        round.setTrendState(state.trend().name());
        round.setVolatilityState(state.volatility().name());
        round.setTrendScore(BigDecimal.valueOf(state.trendScore()).setScale(6, RoundingMode.HALF_UP));
        round.setTrendAge(state.trendAge());
        round.setRegimeAge(state.regimeAge());
        round.setConsecutiveDirectionRounds(state.consecutiveDirectionRounds());
        round.setCurrentVolatilityPercent(GuessStockRules.rate(BigDecimal.valueOf(state.currentVolatilityPct())));
    }

    private static String truncate(String value, int maxLength) {
        if (value == null) return "";
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private record MarketFacts(double return3, double return8, double return20, double volatility,
                               BigDecimal high, BigDecimal low) {}

    private AiTradingPolicy.Memory aiMemory(Long sessionId, Long participantId) {
        List<GuessStockTrade> trades = tradeMapper.selectList(new LambdaQueryWrapper<GuessStockTrade>()
                .eq(GuessStockTrade::getSessionId, sessionId).eq(GuessStockTrade::getParticipantId, participantId)
                .orderByDesc(GuessStockTrade::getId).last("LIMIT 10"));
        if (trades.isEmpty()) return new AiTradingPolicy.Memory(0, 0, 0);
        Map<Integer, GuessStockRound> rounds = roundMapper.selectList(new LambdaQueryWrapper<GuessStockRound>()
                        .eq(GuessStockRound::getSessionId, sessionId).isNotNull(GuessStockRound::getClosePrice))
                .stream().collect(Collectors.toMap(GuessStockRound::getRoundNo, item -> item));
        int buys = 0;
        int losingBuys = 0;
        int sells = 0;
        for (GuessStockTrade trade : trades) {
            if ("BUY".equals(trade.getSide())) {
                buys++;
                GuessStockRound tradeRound = rounds.get(trade.getRoundNo());
                if (tradeRound != null && tradeRound.getClosePrice().compareTo(trade.getExecutionPrice()) < 0) losingBuys++;
            } else if ("SELL".equals(trade.getSide())) sells++;
        }
        return new AiTradingPolicy.Memory(buys, losingBuys, sells);
    }

    private void clearOpening(GameSession session, LocalDateTime now) {
        executeRemainingAiTasks(session);
        GameVersion version = gameService.requireVersion(session.getGameVersionId());
        GuessStockConfig config = gameService.readConfig(version.getConfigJson());
        if ("TARGET_AUCTION".equals(config.tradingMode())) {
            clearTargetAuction(session, config, true, now);
            return;
        }
        GuessStockRound round = currentRound(session);
        int participantCount = Math.toIntExact(participantMapper.selectCount(new LambdaQueryWrapper<GuessStockParticipant>()
                .eq(GuessStockParticipant::getSessionId, session.getId())));
        OpeningClearing clearing = fitOpeningOrders(session, round, config, participantCount);
        List<GuessStockOrder> orders = clearing.orders();
        long quantity = orders.stream().mapToLong(GuessStockOrder::getQuantity).sum();
        GuessStockRules.StagePricingResult pricing = clearing.pricing();
        round.setOpenPrice(pricing.price());
        round.setOpeningOrderFlowChangePercent(pricing.orderFlowChangePercent());
        round.setOpeningBuyQuantity(quantity);
        if (usesModernPricing(config)) {
            MarketPriceEngine.Result breakdown = v2Pricing(session, round, config, participantCount,
                    quantity, true);
            applyPriceBreakdown(round, breakdown, true);
            markEventSlicesApplied(session.getId(), round.getRoundNo(), OPENING, now);
        }
        roundMapper.updateById(round);
        for (GuessStockOrder order : orders) fillOrder(order, pricing.price());
        setGatePhase(session, "OPENING_RESULT_ACK", now);
    }

    private void prepareClosingPhase(GameSession session, LocalDateTime now) {
        GuessStockRound round = currentRound(session);
        GameVersion version = gameService.requireVersion(session.getGameVersionId());
        GuessStockConfig config = gameService.readConfig(version.getConfigJson());
        boolean openingEventOccurred = Integer.valueOf(1).equals(round.getOpeningEventOccurred())
                || (round.getOpeningEventOccurred() == null && OPENING.equals(round.getEventStage())
                && round.getEventName() != null);
        if (shouldDrawClosingEvent(openingEventOccurred, round.getClosingEventOccurred())) {
            assignEvent(session, round, config, version, CLOSING, shouldForceEvent(session.getId(), round.getRoundNo()));
        } else if (openingEventOccurred && usesModernPricing(config)) {
            round.setClosingEventOccurred(0);
            round.setClosingEventEffective(0);
            round.setClosingEventChangePercent(ZERO);
            roundMapper.updateById(round);
        } else if (openingEventOccurred && round.getClosingEventEffective() == null) {
            GuessStockConfig.EventConfig selected = config.events().get(round.getEventConfigIndex());
            round.setClosingEventOccurred(1);
            applyEventEffect(session, round, version, config, selected, CLOSING);
            roundMapper.updateById(round);
        }
        if (Integer.valueOf(1).equals(round.getClosingEventOccurred()) && CLOSING.equals(round.getEventStage())) {
            setGatePhase(session, "CLOSING_EVENT_ACK", now);
        } else {
            startTrading(session, CLOSING, now);
        }
    }

    static boolean shouldDrawClosingEvent(boolean openingEventOccurred, Integer closingEventOccurred) {
        return !openingEventOccurred && closingEventOccurred == null;
    }

    private void clearClosing(GameSession session, LocalDateTime now) {
        executeRemainingAiTasks(session);
        GuessStockConfig config = gameService.readConfig(gameService.requireVersion(session.getGameVersionId()).getConfigJson());
        if ("TARGET_AUCTION".equals(config.tradingMode())) {
            clearTargetAuction(session, config, false, now);
            return;
        }
        GuessStockRound round = currentRound(session);
        List<GuessStockOrder> orders = pendingOrders(session.getId(), session.getCurrentRound(), CLOSING);
        long ordinaryQuantity = orders.stream().mapToLong(GuessStockOrder::getQuantity).sum();
        boolean finalRound = session.getCurrentRound() >= config.roundCount();
        long plannedForcedQuantity = 0;
        for (GuessStockParticipant participant : participants(session.getId())) {
            long submitted = orders.stream().filter(order -> order.getParticipantId().equals(participant.getId()))
                    .mapToLong(GuessStockOrder::getQuantity).sum();
            GuessStockPosition position = positionMapper.selectByParticipantForUpdate(participant.getId());
            long projectedShares = Math.max(0, position.getQuantity() - submitted);
            BigDecimal projectedCash = participant.getCashBalance().add(round.getOpenPrice()
                    .multiply(BigDecimal.valueOf(submitted)));
            plannedForcedQuantity += creditService.planForcedShares(session.getId(), session.getCurrentRound(),
                    participantMapper.selectByIdForUpdate(participant.getId()), projectedShares, projectedCash,
                    round.getOpenPrice(), config, finalRound);
        }
        long quantity = ordinaryQuantity + plannedForcedQuantity;
        int participantCount = Math.toIntExact(participantMapper.selectCount(new LambdaQueryWrapper<GuessStockParticipant>()
                .eq(GuessStockParticipant::getSessionId, session.getId())));
        boolean v2 = usesModernPricing(config);
        BigDecimal meanReversion = v2 ? ZERO : GuessStockRules.meanReversionPercent(round.getOpenPrice(),
                round.getPreviousClosePrice(), config.baseVolatilityPercent().multiply(new BigDecimal("0.50")));
        MarketPriceEngine.Result v2Breakdown = v2
                ? v2Pricing(session, round, config, participantCount, quantity, false) : null;
        GuessStockRules.StagePricingResult pricing = v2
                ? new GuessStockRules.StagePricingResult(v2Breakdown.price(), v2Breakdown.orderFlowImpactPct(),
                    v2Breakdown.totalImpactPct())
                : GuessStockRules.closingPrice(round.getOpenPrice(), quantity,
                    round.getBaseChangePercent(), round.getClosingEventChangePercent(), meanReversion,
                    config.marketLiquidityShares(), participantCount, config.maxOrderImpactPercent());
        for (GuessStockOrder order : orders) fillOrder(order, pricing.price());
        for (GuessStockParticipant participant : participants(session.getId())) {
            if (!"AI".equals(participant.getParticipantType())) continue;
            AiInvestmentThesis thesis = aiThesisService.findActive(session.getId(), participant.getId());
            if (thesis != null && thesis.getTargetPositionPct().compareTo(new BigDecimal("100")) <= 0) {
                creditService.autoRepayAll(session.getId(), session.getCurrentRound(),
                        participantMapper.selectByIdForUpdate(participant.getId()));
            }
        }
        long forcedQuantity = 0;
        for (GuessStockParticipant participant : participants(session.getId())) {
            GuessStockPosition position = positionMapper.selectByParticipantForUpdate(participant.getId());
            forcedQuantity += creditService.settleParticipant(session.getId(), session.getCurrentRound(), CLOSING,
                    participantMapper.selectByIdForUpdate(participant.getId()), position, pricing.price(), config, finalRound);
        }
        round.setClosePrice(pricing.price());
        round.setExecutionPrice(pricing.price());
        round.setOrderFlowChangePercent(pricing.orderFlowChangePercent());
        round.setMeanReversionChangePercent(meanReversion);
        round.setClosingSellQuantity(ordinaryQuantity);
        round.setForcedSellQuantity(forcedQuantity);
        round.setNetOrderAmount(GuessStockRules.money(BigDecimal.valueOf(ordinaryQuantity + forcedQuantity).negate()));
        applyMarketState(round, marketState(session.getId(), pricing.price(),
                config.baseVolatilityPercent().doubleValue()));
        if (v2) {
            applyPriceBreakdown(round, v2Breakdown, false);
            markEventSlicesApplied(session.getId(), round.getRoundNo(), CLOSING, now);
        }
        round.setFinancialReportPublished("V3".equals(config.pricingEngineVersion())
                && round.getRoundNo() % config.reportIntervalRounds() == 0 ? 1 : 0);
        round.setClearedAt(now);
        roundMapper.updateById(round);
        if (Integer.valueOf(1).equals(round.getFinancialReportPublished())) publishFinancialReport(round);
        setGatePhase(session, "ROUND_RESULT_ACK", now);
    }

    private void clearTargetAuction(GameSession session, GuessStockConfig config, boolean opening,
                                    LocalDateTime now) {
        GuessStockRound round = currentRound(session);
        String stage = opening ? OPENING : CLOSING;
        BigDecimal reference = opening ? round.getPreviousClosePrice() : round.getOpenPrice();
        int participantCount = Math.toIntExact(participantMapper.selectCount(
                new LambdaQueryWrapper<GuessStockParticipant>()
                        .eq(GuessStockParticipant::getSessionId, session.getId())));
        MarketPriceEngine.Result external = v2Pricing(session, round, config, participantCount, 0, opening);
        List<GuessStockOrder> orders = pendingOrders(session.getId(), session.getCurrentRound(), stage);
        List<AuctionClearingEngine.TargetOrder> targets = auctionTargets(session, orders, external.price(), config);
        long liquidity = Math.max(1, config.marketLiquidityShares().longValue() * Math.max(1, participantCount));
        AuctionClearingEngine.Result auction = AuctionClearingEngine.clear(external.price(), targets, liquidity,
                config.maxOrderImpactPercent(), config.feePercent(), config.slippagePercent());
        long plannedForced = 0;
        boolean finalRound = !opening && session.getCurrentRound() >= config.roundCount();
        if (!opening) {
            Map<Long, AuctionClearingEngine.Fill> fillsByParticipant = auction.fills().stream()
                    .collect(Collectors.toMap(fill -> Long.valueOf(fill.participantKey()), fill -> fill));
            for (GuessStockParticipant participant : participants(session.getId())) {
                GuessStockPosition position = positionMapper.selectByParticipantForUpdate(participant.getId());
                AuctionClearingEngine.Fill fill = fillsByParticipant.get(participant.getId());
                long shares = position.getQuantity();
                BigDecimal cash = participant.getCashBalance();
                if (fill != null) {
                    BigDecimal cashChange = fill.grossAmount().subtract(fill.feeAmount());
                    if ("BUY".equals(fill.side())) { shares += fill.filledShares(); cash = cash.subtract(fill.grossAmount()).subtract(fill.feeAmount()); }
                    else { shares -= fill.filledShares(); cash = cash.add(cashChange); }
                }
                plannedForced += creditService.planForcedShares(session.getId(), session.getCurrentRound(),
                        participantMapper.selectByIdForUpdate(participant.getId()), shares, cash,
                        auction.clearingPrice(), config, finalRound);
            }
            if (plannedForced > 0) {
                auction = AuctionClearingEngine.clear(external.price(), targets, liquidity,
                        config.maxOrderImpactPercent(), config.feePercent(), config.slippagePercent(), plannedForced);
            }
        }
        Map<String, GuessStockOrder> byParticipant = orders.stream()
                .collect(Collectors.toMap(order -> order.getParticipantId().toString(), order -> order));
        for (AuctionClearingEngine.Fill fill : auction.fills()) {
            GuessStockOrder order = byParticipant.get(fill.participantKey());
            if (order != null) fillAuctionOrder(order, fill);
        }
        long bought = auction.fills().stream().filter(fill -> "BUY".equals(fill.side()))
                .mapToLong(AuctionClearingEngine.Fill::filledShares).sum();
        long sold = auction.fills().stream().filter(fill -> "SELL".equals(fill.side()))
                .mapToLong(AuctionClearingEngine.Fill::filledShares).sum();
        MarketPriceEngine.Result combined = combineAuctionBreakdown(reference, external, auction);
        if (opening) {
            round.setOpenPrice(auction.clearingPrice());
            round.setOpeningBuyQuantity(bought);
            round.setOpeningOrderFlowChangePercent(combined.orderFlowImpactPct());
            applyPriceBreakdown(round, combined, true);
            markEventSlicesApplied(session.getId(), round.getRoundNo(), OPENING, now);
            roundMapper.updateById(round);
            setGatePhase(session, "OPENING_RESULT_ACK", now);
            return;
        }
        for (GuessStockParticipant participant : participants(session.getId())) {
            if (!"AI".equals(participant.getParticipantType())) continue;
            AiInvestmentThesis thesis = aiThesisService.findActive(session.getId(), participant.getId());
            if (thesis != null && thesis.getTargetPositionPct().compareTo(new BigDecimal("100")) <= 0) {
                creditService.autoRepayAll(session.getId(), session.getCurrentRound(),
                        participantMapper.selectByIdForUpdate(participant.getId()));
            }
        }
        long forced = 0;
        for (GuessStockParticipant participant : participants(session.getId())) {
            GuessStockPosition position = positionMapper.selectByParticipantForUpdate(participant.getId());
            forced += creditService.settleParticipant(session.getId(), session.getCurrentRound(), CLOSING,
                    participantMapper.selectByIdForUpdate(participant.getId()), position,
                    auction.clearingPrice(), config, finalRound);
        }
        round.setClosePrice(auction.clearingPrice());
        round.setExecutionPrice(auction.clearingPrice());
        round.setOrderFlowChangePercent(combined.orderFlowImpactPct());
        round.setClosingSellQuantity(sold);
        round.setForcedSellQuantity(forced);
        round.setNetOrderAmount(GuessStockRules.money(BigDecimal.valueOf(bought - sold - forced)));
        applyPriceBreakdown(round, combined, false);
        applyMarketState(round, marketState(session.getId(), auction.clearingPrice(),
                config.baseVolatilityPercent().doubleValue()));
        markEventSlicesApplied(session.getId(), round.getRoundNo(), CLOSING, now);
        round.setFinancialReportPublished("V3".equals(config.pricingEngineVersion())
                && round.getRoundNo() % config.reportIntervalRounds() == 0 ? 1 : 0);
        round.setClearedAt(now);
        roundMapper.updateById(round);
        if (Integer.valueOf(1).equals(round.getFinancialReportPublished())) publishFinancialReport(round);
        setGatePhase(session, "ROUND_RESULT_ACK", now);
    }

    private List<AuctionClearingEngine.TargetOrder> auctionTargets(GameSession session,
                                                                   List<GuessStockOrder> orders,
                                                                   BigDecimal price,
                                                                   GuessStockConfig config) {
        List<AuctionClearingEngine.TargetOrder> targets = new ArrayList<>();
        for (GuessStockOrder order : orders) {
            GuessStockParticipant participant = participantMapper.selectByIdForUpdate(order.getParticipantId());
            GuessStockPosition position = positionMapper.selectByParticipantForUpdate(order.getParticipantId());
            targets.add(new AuctionClearingEngine.TargetOrder(order.getParticipantId().toString(),
                    participant.getCashBalance(), position.getQuantity(), order.getTargetExposurePercent().doubleValue(),
                    creditService.debt(session.getId(), participant.getId())));
        }
        return targets;
    }

    private static MarketPriceEngine.Result combineAuctionBreakdown(BigDecimal reference,
                                                                     MarketPriceEngine.Result external,
                                                                     AuctionClearingEngine.Result auction) {
        BigDecimal auctionMove = GuessStockRules.rate(BigDecimal.valueOf(
                Math.log(auction.clearingPrice().doubleValue() / external.price().doubleValue()) * 100));
        BigDecimal flow = external.orderFlowImpactPct().add(auctionMove).setScale(4, RoundingMode.HALF_UP);
        BigDecimal raw = external.rawTotalImpactPct().add(auctionMove).setScale(4, RoundingMode.HALF_UP);
        BigDecimal total = GuessStockRules.rate(BigDecimal.valueOf(
                Math.log(auction.clearingPrice().doubleValue() / reference.doubleValue()) * 100));
        return new MarketPriceEngine.Result(auction.clearingPrice(), external.baseDriftPct(),
                external.regimeImpactPct(), external.momentumImpactPct(), external.eventImpactPct(),
                external.meanReversionImpactPct(), external.noiseImpactPct(), flow,
                external.fundamentalImpactPct(), external.reportSurprisePct(), raw,
                total.subtract(raw).setScale(4, RoundingMode.HALF_UP), total);
    }

    private void publishFinancialReport(GuessStockRound round) {
        GuessStockFinancialReport existing = financialReportMapper.selectOne(
                new LambdaQueryWrapper<GuessStockFinancialReport>()
                        .eq(GuessStockFinancialReport::getSessionId, round.getSessionId())
                        .eq(GuessStockFinancialReport::getRoundNo, round.getRoundNo()).last("LIMIT 1"));
        if (existing != null) return;
        GuessStockFinancialReport previous = financialReportMapper.selectOne(
                new LambdaQueryWrapper<GuessStockFinancialReport>()
                        .eq(GuessStockFinancialReport::getSessionId, round.getSessionId())
                        .lt(GuessStockFinancialReport::getRoundNo, round.getRoundNo())
                        .orderByDesc(GuessStockFinancialReport::getRoundNo).last("LIMIT 1"));
        BigDecimal expected = previous == null ? new BigDecimal("100.0000") : previous.getFundamentalScore();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("demandIndex", round.getDemandIndex());
        body.put("orderIndex", round.getOrderIndex());
        body.put("inventoryIndex", round.getInventoryIndex());
        body.put("marginIndex", round.getMarginIndex());
        body.put("cashFlowIndex", round.getCashFlowIndex());
        body.put("expectedRange", List.of(expected.subtract(new BigDecimal("3.0000")),
                expected.add(new BigDecimal("3.0000"))));
        GuessStockFinancialReport report = new GuessStockFinancialReport();
        report.setSessionId(round.getSessionId());
        report.setRoundNo(round.getRoundNo());
        report.setFundamentalScore(round.getFundamentalScore());
        report.setExpectedScore(expected);
        report.setSurprisePercent(round.getReportSurprisePercent() == null ? ZERO : round.getReportSurprisePercent());
        report.setReportJson(write(body));
        financialReportMapper.insert(report);
    }

    private void finishRound(GameSession session, LocalDateTime now) {
        GameVersion version = gameService.requireVersion(session.getGameVersionId());
        GuessStockConfig config = gameService.readConfig(version.getConfigJson());
        GuessStockRound round = currentRound(session);
        if (session.getCurrentRound() >= config.roundCount()) settle(session, config, round.getClosePrice(), now);
        else advanceNextRound(session, version, config, round.getClosePrice(), now);
    }

    private void advanceNextRound(GameSession session, GameVersion version, GuessStockConfig config,
                                  BigDecimal previousClose, LocalDateTime now) {
        int nextRound = session.getCurrentRound() + 1;
        session.setCurrentRound(nextRound);
        sessionMapper.updateById(session);
        GuessStockRound next = createRound(session, version, config, nextRound, previousClose);
        prepareRoundPhase(session, next, config, now);
    }

    private OpeningClearing fitOpeningOrders(GameSession session, GuessStockRound round,
                                             GuessStockConfig config, int participantCount) {
        List<GuessStockOrder> initial = pendingOrders(session.getId(), session.getCurrentRound(), OPENING);
        GuessStockRules.StagePricingResult pricing = openingPricing(session, round, initial, config, participantCount);
        for (int iteration = 0; iteration < 24; iteration++) {
            boolean changed = false;
            for (GuessStockOrder order : new ArrayList<>(initial)) {
                GuessStockParticipant participant = participantMapper.selectByIdForUpdate(order.getParticipantId());
                GuessStockPosition position = positionMapper.selectByParticipantForUpdate(order.getParticipantId());
                long affordable = GuessStockRules.maxBuyQuantity(participant.getCashBalance(), pricing.price());
                boolean allIn = Integer.valueOf(1).equals(order.getAllIn());
                long adjusted = allIn ? affordable
                        : Math.min(order.getQuantity(), Math.min(config.maxBuyQuantity(), affordable));
                if (adjusted == order.getQuantity()) continue;
                changed = true;
                if (adjusted <= 0) {
                    releaseOrder(order, participant, position, "REJECTED");
                    initial.remove(order);
                } else {
                    order.setQuantity(adjusted);
                    order.setReservedAmount(GuessStockRules.money(pricing.price().multiply(BigDecimal.valueOf(adjusted))));
                    participant.setReservedCash(order.getReservedAmount());
                    participantMapper.updateById(participant);
                    orderMapper.updateById(order);
                }
            }
            GuessStockRules.StagePricingResult next = openingPricing(session, round, initial, config, participantCount);
            pricing = next;
            if (!changed) break;
        }
        // If the integer fixed point alternates by one share, keep the lower side so cash can never go negative.
        for (GuessStockOrder order : new ArrayList<>(initial)) {
            if (!Integer.valueOf(1).equals(order.getAllIn())) continue;
            GuessStockParticipant participant = participantMapper.selectByIdForUpdate(order.getParticipantId());
            long affordable = GuessStockRules.maxBuyQuantity(participant.getCashBalance(), pricing.price());
            if (order.getQuantity() > affordable) {
                if (affordable <= 0) {
                    GuessStockPosition position = positionMapper.selectByParticipantForUpdate(order.getParticipantId());
                    releaseOrder(order, participant, position, "REJECTED");
                    initial.remove(order);
                    continue;
                }
                order.setQuantity(affordable);
                order.setReservedAmount(GuessStockRules.money(pricing.price().multiply(BigDecimal.valueOf(affordable))));
                participant.setReservedCash(order.getReservedAmount());
                participantMapper.updateById(participant);
                orderMapper.updateById(order);
            }
        }
        pricing = openingPricing(session, round, initial, config, participantCount);
        return new OpeningClearing(initial, pricing);
    }

    private GuessStockRules.StagePricingResult openingPricing(GameSession session, GuessStockRound round,
                                                               List<GuessStockOrder> orders,
                                                               GuessStockConfig config, int participantCount) {
        long quantity = orders.stream().mapToLong(GuessStockOrder::getQuantity).sum();
        if (usesModernPricing(config)) {
            MarketPriceEngine.Result result = v2Pricing(session, round, config, participantCount, quantity, true);
            return new GuessStockRules.StagePricingResult(result.price(), result.orderFlowImpactPct(),
                    result.totalImpactPct());
        }
        return GuessStockRules.openingPrice(round.getPreviousClosePrice(), quantity,
                round.getOpeningBaseChangePercent(), round.getOpeningEventChangePercent(),
                config.marketLiquidityShares(), participantCount, config.maxOrderImpactPercent());
    }

    private record OpeningClearing(List<GuessStockOrder> orders,
                                   GuessStockRules.StagePricingResult pricing) {}

    private MarketPriceEngine.Result v2Pricing(GameSession session, GuessStockRound round,
                                                GuessStockConfig config, int participantCount,
                                                long quantity, boolean opening) {
        BigDecimal reference = opening ? round.getPreviousClosePrice() : round.getOpenPrice();
        BigDecimal flow = GuessStockRules.orderFlowImpactPercent(quantity, config.marketLiquidityShares(),
                participantCount, config.maxOrderImpactPercent());
        if (!opening) flow = flow.negate();
        BigDecimal event = scheduledEventImpact(session.getId(), round.getRoundNo(), opening ? OPENING : CLOSING);
        List<BigDecimal> history = historyPrices(session.getId());
        MarketStateCalculator.MarketState state = marketState(session.getId(), reference,
                config.baseVolatilityPercent().doubleValue());
        long scenarioSeed = session.getScenarioSeed() == null ? session.getId() : session.getScenarioSeed();
        double maxMove = Math.min(12, Math.max(3, config.baseVolatilityPercent().doubleValue() * 2.5));
        FundamentalEngine.PriceContribution fundamentals = "V3".equals(config.pricingEngineVersion())
                ? FundamentalEngine.priceContribution(fundamentalState(round), config.initialPrice(), reference,
                    config.fundamentalSensitivity(), !opening && round.getRoundNo() % config.reportIntervalRounds() == 0)
                : new FundamentalEngine.PriceContribution(ZERO, ZERO);
        return MarketPriceEngine.price(new MarketPriceEngine.Context(reference, history, state,
                config.baseVolatilityPercent().doubleValue(), 0, event.doubleValue(), flow.doubleValue(),
                maxMove, opening, mix(scenarioSeed, round.getRoundNo(), opening ? 7007 : 17007),
                fundamentals.fundamentalImpactPct().doubleValue(), fundamentals.reportSurprisePct().doubleValue()));
    }

    private List<BigDecimal> historyPrices(Long sessionId) {
        List<BigDecimal> values = roundMapper.selectList(new LambdaQueryWrapper<GuessStockRound>()
                        .eq(GuessStockRound::getSessionId, sessionId)
                        .isNotNull(GuessStockRound::getClosePrice)
                        .orderByDesc(GuessStockRound::getRoundNo).last("LIMIT 50"))
                .stream().map(GuessStockRound::getClosePrice).collect(Collectors.toCollection(ArrayList::new));
        Collections.reverse(values);
        return values;
    }

    private BigDecimal scheduledEventImpact(Long sessionId, int roundNo, String stage) {
        return eventImpactSliceMapper.selectList(new LambdaQueryWrapper<GuessStockEventImpactSlice>()
                        .eq(GuessStockEventImpactSlice::getSessionId, sessionId)
                        .eq(GuessStockEventImpactSlice::getTargetRoundNo, roundNo)
                        .eq(GuessStockEventImpactSlice::getTargetStage, stage)
                        .isNull(GuessStockEventImpactSlice::getAppliedAt))
                .stream().map(GuessStockEventImpactSlice::getSliceImpactPercent).reduce(ZERO, BigDecimal::add);
    }

    private void markEventSlicesApplied(Long sessionId, int roundNo, String stage, LocalDateTime now) {
        List<GuessStockEventImpactSlice> slices = eventImpactSliceMapper.selectList(
                new LambdaQueryWrapper<GuessStockEventImpactSlice>()
                        .eq(GuessStockEventImpactSlice::getSessionId, sessionId)
                        .eq(GuessStockEventImpactSlice::getTargetRoundNo, roundNo)
                        .eq(GuessStockEventImpactSlice::getTargetStage, stage)
                        .isNull(GuessStockEventImpactSlice::getAppliedAt));
        for (GuessStockEventImpactSlice slice : slices) {
            slice.setAppliedAt(now);
            eventImpactSliceMapper.updateById(slice);
        }
    }

    private static void applyPriceBreakdown(GuessStockRound round, MarketPriceEngine.Result result,
                                            boolean opening) {
        if (opening) {
            round.setOpeningBaseChangePercent(result.noiseImpactPct());
            round.setMomentumChangePercent(result.momentumImpactPct());
            round.setRegimeChangePercent(result.regimeImpactPct());
            round.setScheduledEventChangePercent(result.eventImpactPct());
            round.setMediumMeanReversionPercent(result.meanReversionImpactPct());
            round.setNoiseChangePercent(result.noiseImpactPct());
            round.setRawTotalChangePercent(result.rawTotalImpactPct());
            round.setClampAdjustmentPercent(result.clampAdjustmentPct());
            round.setFundamentalChangePercent(result.fundamentalImpactPct());
            round.setReportSurprisePercent(result.reportSurprisePct());
            round.setTotalChangePercent(result.totalImpactPct());
        } else {
            round.setBaseChangePercent(result.noiseImpactPct());
            round.setMomentumChangePercent(sum(round.getMomentumChangePercent(), result.momentumImpactPct()));
            round.setRegimeChangePercent(sum(round.getRegimeChangePercent(), result.regimeImpactPct()));
            round.setScheduledEventChangePercent(sum(round.getScheduledEventChangePercent(), result.eventImpactPct()));
            round.setMediumMeanReversionPercent(sum(round.getMediumMeanReversionPercent(), result.meanReversionImpactPct()));
            round.setNoiseChangePercent(sum(round.getNoiseChangePercent(), result.noiseImpactPct()));
            round.setRawTotalChangePercent(sum(round.getRawTotalChangePercent(), result.rawTotalImpactPct()));
            round.setClampAdjustmentPercent(sum(round.getClampAdjustmentPercent(), result.clampAdjustmentPct()));
            round.setFundamentalChangePercent(sum(round.getFundamentalChangePercent(), result.fundamentalImpactPct()));
            round.setReportSurprisePercent(sum(round.getReportSurprisePercent(), result.reportSurprisePct()));
            round.setTotalChangePercent(sum(round.getTotalChangePercent(), result.totalImpactPct()));
        }
        round.setMeanReversionChangePercent(round.getMediumMeanReversionPercent());
    }

    private static BigDecimal sum(BigDecimal left, BigDecimal right) {
        return (left == null ? ZERO : left).add(right == null ? ZERO : right).setScale(4, RoundingMode.HALF_UP);
    }

    private void fillOrder(GuessStockOrder order, BigDecimal executionPrice) {
        GuessStockParticipant participant = participantMapper.selectByIdForUpdate(order.getParticipantId());
        GuessStockPosition position = positionMapper.selectByParticipantForUpdate(participant.getId());
        BigDecimal amount = GuessStockRules.money(executionPrice.multiply(BigDecimal.valueOf(order.getQuantity())));
        if ("BUY".equals(order.getSide())) {
            long oldQuantity = position.getQuantity();
            BigDecimal oldCost = position.getAverageCost().multiply(BigDecimal.valueOf(oldQuantity));
            long newQuantity = oldQuantity + order.getQuantity();
            participant.setCashBalance(GuessStockRules.money(participant.getCashBalance().subtract(amount)));
            participant.setReservedCash(ZERO);
            position.setQuantity(newQuantity);
            position.setAverageCost(GuessStockRules.price(oldCost.add(amount)
                    .divide(BigDecimal.valueOf(newQuantity), 10, RoundingMode.HALF_UP)));
        } else {
            if (position.getQuantity() < order.getQuantity()) {
                releaseOrder(order, participant, position, "REJECTED");
                return;
            }
            participant.setCashBalance(GuessStockRules.money(participant.getCashBalance().add(amount)));
            position.setQuantity(position.getQuantity() - order.getQuantity());
            position.setReservedQuantity(0L);
            if (position.getQuantity() == 0) position.setAverageCost(ZERO);
        }
        participantMapper.updateById(participant);
        positionMapper.updateById(position);
        order.setStatus("FILLED");
        orderMapper.updateById(order);
        GuessStockTrade trade = new GuessStockTrade();
        trade.setSessionId(order.getSessionId());
        trade.setOrderId(order.getId());
        trade.setParticipantId(order.getParticipantId());
        trade.setRoundNo(order.getRoundNo());
        trade.setSide(order.getSide());
        trade.setQuantity(order.getQuantity());
        trade.setExecutionPrice(executionPrice);
        trade.setAmount(amount);
        trade.setSource(participant.getParticipantType());
        trade.setTradedAt(LocalDateTime.now());
        tradeMapper.insert(trade);
    }

    private void fillAuctionOrder(GuessStockOrder order, AuctionClearingEngine.Fill fill) {
        GuessStockParticipant participant = participantMapper.selectByIdForUpdate(order.getParticipantId());
        GuessStockPosition position = positionMapper.selectByParticipantForUpdate(participant.getId());
        order.setSide(fill.side());
        order.setQuantity(fill.requestedShares());
        order.setStatus(fill.filledShares() == 0 ? "UNFILLED"
                : fill.filledShares() < fill.requestedShares() ? "PARTIALLY_FILLED" : "FILLED");
        order.setReservedAmount(ZERO);
        orderMapper.updateById(order);
        if (fill.filledShares() == 0) return;
        if ("BUY".equals(fill.side())) {
            long oldQuantity = position.getQuantity();
            BigDecimal oldCost = position.getAverageCost().multiply(BigDecimal.valueOf(oldQuantity));
            long newQuantity = oldQuantity + fill.filledShares();
            participant.setCashBalance(GuessStockRules.money(participant.getCashBalance()
                    .subtract(fill.grossAmount()).subtract(fill.feeAmount())));
            position.setQuantity(newQuantity);
            position.setAverageCost(GuessStockRules.price(oldCost.add(fill.grossAmount()).add(fill.feeAmount())
                    .divide(BigDecimal.valueOf(newQuantity), 10, RoundingMode.HALF_UP)));
        } else {
            long filled = Math.min(position.getQuantity(), fill.filledShares());
            participant.setCashBalance(GuessStockRules.money(participant.getCashBalance()
                    .add(fill.executionPrice().multiply(BigDecimal.valueOf(filled))).subtract(fill.feeAmount())));
            position.setQuantity(position.getQuantity() - filled);
            if (position.getQuantity() == 0) position.setAverageCost(ZERO);
        }
        participant.setReservedCash(ZERO);
        position.setReservedQuantity(0L);
        participantMapper.updateById(participant);
        positionMapper.updateById(position);
        GuessStockTrade trade = new GuessStockTrade();
        trade.setSessionId(order.getSessionId());
        trade.setOrderId(order.getId());
        trade.setParticipantId(order.getParticipantId());
        trade.setRoundNo(order.getRoundNo());
        trade.setSide(fill.side());
        trade.setQuantity(fill.filledShares());
        trade.setExecutionPrice(fill.executionPrice());
        trade.setAmount(fill.grossAmount());
        trade.setFeeAmount(fill.feeAmount());
        trade.setSlippagePercent(fill.slippagePercent());
        trade.setSource(participant.getParticipantType());
        trade.setTradedAt(LocalDateTime.now());
        tradeMapper.insert(trade);
    }

    private void settle(GameSession session, GuessStockConfig config, BigDecimal finalPrice, LocalDateTime now) {
        List<GuessStockParticipant> participants = participants(session.getId());
        Map<Long, GuessStockPosition> positions = positionMap(session.getId());
        for (GuessStockParticipant participant : participants) {
            GuessStockPosition position = positions.get(participant.getId());
            BigDecimal liquidationValue = finalPrice.multiply(BigDecimal.valueOf(position.getQuantity()));
            BigDecimal remainingDebt = creditService.debt(session.getId(), participant.getId());
            participant.setFinalAsset(GuessStockRules.money(participant.getCashBalance().add(liquidationValue)
                    .subtract(remainingDebt)));
            participant.setCashBalance(remainingDebt.signum() > 0 ? ZERO : participant.getFinalAsset());
            participant.setReservedCash(ZERO);
            position.setQuantity(0L);
            position.setReservedQuantity(0L);
            position.setAverageCost(ZERO);
            positionMapper.updateById(position);
            participantMapper.updateById(participant);
        }
        participants.sort(Comparator
                .comparing((GuessStockParticipant participant) -> participantReturnPercent(participant, config)).reversed()
                .thenComparing(GuessStockParticipant::getFinalAsset, Comparator.reverseOrder()));
        for (int i = 0; i < participants.size(); i++) {
            participants.get(i).setFinalRank(i + 1);
            participantMapper.updateById(participants.get(i));
        }
        GuessStockParticipant ownerPlayer = participants.stream().filter(p -> "PLAYER".equals(p.getParticipantType())
                && session.getPlayerIdentityKey().equals(p.getPlayerIdentityKey())).findFirst()
                .orElseGet(() -> participants.stream().filter(p -> "PLAYER".equals(p.getParticipantType())).findFirst().orElseThrow());
        BigDecimal ownerInitialCash = ownerPlayer.getInitialCash() == null ? config.initialCash() : ownerPlayer.getInitialCash();
        BigDecimal profit = GuessStockRules.money(ownerPlayer.getFinalAsset().subtract(ownerInitialCash));
        BigDecimal returnPercent = returnPercent(profit, ownerInitialCash);
        Map<String, Object> result = new HashMap<>();
        result.put("finalAsset", ownerPlayer.getFinalAsset());
        result.put("profitAmount", profit);
        result.put("returnPercent", returnPercent);
        result.put("inSessionRank", ownerPlayer.getFinalRank());
        session.setResultJson(write(result));
        session.setStatus("COMPLETED");
        session.setPhase("COMPLETED");
        session.setPhaseEndsAt(null);
        session.setActivityLeaseUntil(null);
        session.setCompletedAt(now);
        sessionMapper.updateById(session);
        for (GuessStockParticipant human : participants.stream().filter(p -> "PLAYER".equals(p.getParticipantType())).toList()) {
            BigDecimal initialCash = human.getInitialCash() == null ? config.initialCash() : human.getInitialCash();
            BigDecimal humanProfit = GuessStockRules.money(human.getFinalAsset().subtract(initialCash));
            upsertLeaderboard(session, human, humanProfit, now);
        }
        credentialService.purgeSession(session.getId());
        if (session.getRoomId() != null) {
            GameRoom room = roomMapper.selectByIdForUpdate(session.getRoomId());
            if (room != null) {
                room.setStatus("COMPLETED");
                room.setCompletedAt(now);
                roomMapper.updateById(room);
            }
        }
        EntertainmentGame game = gameMapper.selectByIdForUpdate(session.getGameId());
        game.setCompletedCount((game.getCompletedCount() == null ? 0 : game.getCompletedCount()) + 1);
        gameMapper.updateById(game);
    }

    private void upsertLeaderboard(GameSession session, GuessStockParticipant player,
                                   BigDecimal profit, LocalDateTime now) {
        String identityKey = player.getPlayerIdentityKey() == null ? session.getPlayerIdentityKey() : player.getPlayerIdentityKey();
        GameLeaderboardEntry entry = leaderboardMapper.selectOne(new LambdaQueryWrapper<GameLeaderboardEntry>()
                .eq(GameLeaderboardEntry::getGameVersionId, session.getGameVersionId())
                .eq(GameLeaderboardEntry::getPlayerIdentityKey, identityKey).last("LIMIT 1"));
        BigDecimal initialCash = player.getInitialCash() == null
                ? gameService.readConfig(gameService.requireVersion(session.getGameVersionId()).getConfigJson()).initialCash()
                : player.getInitialCash();
        BigDecimal score = returnPercent(profit, initialCash);
        if (entry != null && entry.getScore() != null && entry.getScore().compareTo(score) >= 0) return;
        if (entry == null) entry = new GameLeaderboardEntry();
        entry.setGameId(session.getGameId());
        entry.setGameVersionId(session.getGameVersionId());
        entry.setSessionId(session.getId());
        entry.setPlayerIdentityKey(identityKey);
        entry.setPlayerType(player.getPlayerUserId() != null ? "USER" : "GUEST");
        entry.setPlayerUserId(player.getPlayerUserId());
        entry.setGuestId(player.getGuestId());
        entry.setDisplayName(player.getDisplayName());
        entry.setScore(score);
        entry.setProfitAmount(profit);
        entry.setFinalAsset(player.getFinalAsset());
        entry.setCompletedAt(now);
        if (entry.getId() == null) leaderboardMapper.insert(entry); else leaderboardMapper.updateById(entry);
    }

    private static BigDecimal returnPercent(BigDecimal profit, BigDecimal initialCash) {
        return profit.divide(initialCash, 10, RoundingMode.HALF_UP)
                .multiply(new BigDecimal("100")).setScale(6, RoundingMode.HALF_UP);
    }

    private static BigDecimal participantReturnPercent(GuessStockParticipant participant, GuessStockConfig config) {
        BigDecimal initialCash = participant.getInitialCash() == null ? config.initialCash() : participant.getInitialCash();
        return returnPercent(participant.getFinalAsset().subtract(initialCash), initialCash);
    }

    private void placeTargetOrder(GameSession session, GuessStockParticipant participant,
                                  BigDecimal targetExposurePercent, String requestId) {
        if (targetExposurePercent == null || targetExposurePercent.signum() < 0
                || targetExposurePercent.compareTo(new BigDecimal("300")) > 0) {
            bad("目标股票敞口需在 0%～300% 之间");
        }
        GuessStockOrder processed = orderMapper.selectOne(new LambdaQueryWrapper<GuessStockOrder>()
                .eq(GuessStockOrder::getSessionId, session.getId())
                .eq(GuessStockOrder::getClientRequestId, requestId).last("LIMIT 1"));
        if (processed != null) return;
        GuessStockParticipant locked = participantMapper.selectByIdForUpdate(participant.getId());
        GuessStockPosition position = positionMapper.selectByParticipantForUpdate(participant.getId());
        GuessStockRound round = currentRound(session);
        GuessStockConfig config = gameService.readConfig(
                gameService.requireVersion(session.getGameVersionId()).getConfigJson());
        BigDecimal mark = referencePrice(round, stageForPhase(session.getPhase()));
        GuessStockCreditService.CreditStatus credit = creditService.status(session.getId(), locked, position, mark, config);
        BigDecimal current = credit.risk().exposurePercent();
        if (targetExposurePercent.compareTo(current) > 0
                && List.of("BLOCKED", "LIQUIDATION", "DEFAULTED").contains(credit.risk().riskState())) {
            bad("当前授信风险状态禁止增加股票敞口");
        }
        String stage = stageForPhase(session.getPhase());
        GuessStockOrder order = orderMapper.selectOne(new LambdaQueryWrapper<GuessStockOrder>()
                .eq(GuessStockOrder::getSessionId, session.getId())
                .eq(GuessStockOrder::getParticipantId, participant.getId())
                .eq(GuessStockOrder::getRoundNo, session.getCurrentRound())
                .eq(GuessStockOrder::getStage, stage).last("LIMIT 1"));
        if (order == null) {
            order = new GuessStockOrder();
            order.setSessionId(session.getId());
            order.setParticipantId(participant.getId());
            order.setRoundNo(session.getCurrentRound());
            order.setStage(stage);
            order.setSubmittedAt(LocalDateTime.now());
        } else if ("PENDING".equals(order.getStatus())) {
            releaseReservation(order, locked, position);
        }
        order.setSide("TARGET");
        order.setQuantity(0L);
        order.setTargetExposurePercent(targetExposurePercent.setScale(4, RoundingMode.HALF_UP));
        order.setStatus("PENDING");
        order.setReservedAmount(ZERO);
        order.setAllIn(0);
        order.setClientRequestId(requestId);
        participantMapper.updateById(locked);
        positionMapper.updateById(position);
        if (order.getId() == null) orderMapper.insert(order); else orderMapper.updateById(order);
    }

    private void placeOrder(GameSession session, GuessStockParticipant participant, String sideValue, Long quantityValue,
                            boolean allIn, String requestId, String source) {
        String side = sideValue == null ? "" : sideValue.toUpperCase();
        String stage = stageForPhase(session.getPhase());
        long quantity = quantityValue == null ? 0 : quantityValue;
        if (!List.of("BUY", "SELL").contains(side)) bad("订单方向必须为 BUY 或 SELL");
        if (OPENING.equals(stage) && !"BUY".equals(side)) bad("开盘前只能提交买入订单");
        if (CLOSING.equals(stage) && !"SELL".equals(side)) bad("收盘前只能提交卖出订单");
        if (allIn && !OPENING.equals(stage)) bad("全部资金买入只支持开盘前阶段");
        if (!allIn && (quantity <= 0 || quantity > 10_000_000L)) bad("订单股数需在 1～10000000 之间");
        GuessStockOrder processedRequest = orderMapper.selectOne(new LambdaQueryWrapper<GuessStockOrder>()
                .eq(GuessStockOrder::getSessionId, session.getId())
                .eq(GuessStockOrder::getClientRequestId, requestId).last("LIMIT 1"));
        if (processedRequest != null) return;
        GuessStockParticipant lockedParticipant = participantMapper.selectByIdForUpdate(participant.getId());
        GuessStockPosition position = positionMapper.selectByParticipantForUpdate(participant.getId());
        GuessStockOrder order = orderMapper.selectOne(new LambdaQueryWrapper<GuessStockOrder>()
                .eq(GuessStockOrder::getSessionId, session.getId())
                .eq(GuessStockOrder::getParticipantId, participant.getId())
                .eq(GuessStockOrder::getRoundNo, session.getCurrentRound())
                .eq(GuessStockOrder::getStage, stage).last("LIMIT 1"));
        if (order != null && "PENDING".equals(order.getStatus())) releaseReservation(order, lockedParticipant, position);
        GameVersion version = gameService.requireVersion(session.getGameVersionId());
        GuessStockConfig config = gameService.readConfig(version.getConfigJson());
        GuessStockRound round = currentRound(session);
        BigDecimal reserved = ZERO;
        if ("BUY".equals(side)) {
            GuessStockCreditService.CreditStatus credit = creditService.status(session.getId(), lockedParticipant,
                    position, round.getPreviousClosePrice(), config);
            if (List.of("BLOCKED", "LIQUIDATION", "DEFAULTED").contains(credit.risk().riskState())) {
                bad("当前授信风险状态禁止增加股票敞口");
            }
            BigDecimal availableCash = lockedParticipant.getCashBalance().subtract(lockedParticipant.getReservedCash());
            if (availableCash.signum() < 0) bad("可用现金为负数，当前不能继续买入");
            long affordable = GuessStockRules.maxBuyQuantity(availableCash, round.getPreviousClosePrice());
            long profileLimit = "AI".equals(source) ? 10_000_000L : config.maxBuyQuantity();
            quantity = allIn ? affordable : Math.min(quantity, Math.min(profileLimit, affordable));
            if (quantity <= 0) bad("当前可用现金不足，无法买入 1 股");
            reserved = GuessStockRules.money(round.getPreviousClosePrice().multiply(BigDecimal.valueOf(quantity)));
            lockedParticipant.setReservedCash(reserved);
        } else {
            long available = position.getQuantity() - position.getReservedQuantity();
            if (available < quantity) bad("可卖持仓不足");
            position.setReservedQuantity(quantity);
        }
        participantMapper.updateById(lockedParticipant);
        positionMapper.updateById(position);
        if (order == null) {
            order = new GuessStockOrder();
            order.setSessionId(session.getId());
            order.setParticipantId(participant.getId());
            order.setRoundNo(session.getCurrentRound());
            order.setStage(stage);
            order.setSubmittedAt(LocalDateTime.now());
        }
        order.setSide(side);
        order.setQuantity(quantity);
        order.setStatus("PENDING");
        order.setReservedAmount(reserved);
        order.setAllIn(allIn ? 1 : 0);
        order.setClientRequestId(requestId);
        if (order.getId() == null) orderMapper.insert(order); else orderMapper.updateById(order);
    }

    private void cancelPendingOrder(GameSession session, GuessStockParticipant participant) {
        GuessStockOrder order = orderMapper.selectOne(new LambdaQueryWrapper<GuessStockOrder>()
                .eq(GuessStockOrder::getSessionId, session.getId())
                .eq(GuessStockOrder::getParticipantId, participant.getId())
                .eq(GuessStockOrder::getRoundNo, session.getCurrentRound())
                .eq(GuessStockOrder::getStage, stageForPhase(session.getPhase()))
                .eq(GuessStockOrder::getStatus, "PENDING").last("LIMIT 1"));
        if (order == null) return;
        GuessStockParticipant locked = participantMapper.selectByIdForUpdate(participant.getId());
        GuessStockPosition position = positionMapper.selectByParticipantForUpdate(participant.getId());
        releaseOrder(order, locked, position, "CANCELLED");
    }

    private void releaseOrder(GuessStockOrder order, GuessStockParticipant participant,
                              GuessStockPosition position, String status) {
        releaseReservation(order, participant, position);
        participantMapper.updateById(participant);
        positionMapper.updateById(position);
        order.setStatus(status);
        orderMapper.updateById(order);
    }

    private void releaseReservation(GuessStockOrder order, GuessStockParticipant participant,
                                    GuessStockPosition position) {
        if ("BUY".equals(order.getSide())) participant.setReservedCash(ZERO);
        else position.setReservedQuantity(0L);
    }

    private void releasePendingOrders(Long sessionId, int roundNo) {
        for (GuessStockOrder order : pendingOrders(sessionId, roundNo)) {
            GuessStockParticipant participant = participantMapper.selectByIdForUpdate(order.getParticipantId());
            GuessStockPosition position = positionMapper.selectByParticipantForUpdate(order.getParticipantId());
            releaseOrder(order, participant, position, "CANCELLED");
        }
    }

    private void pauseLocked(GameSession session, LocalDateTime now) {
        if (session == null || !"ACTIVE".equals(session.getStatus())) return;
        Long remaining = session.getPhaseEndsAt() == null ? null
                : Math.max(0, Duration.between(now, session.getPhaseEndsAt()).toMillis());
        session.setStatus("PAUSED");
        session.setPhaseRemainingMs(remaining);
        session.setPhaseEndsAt(null);
        session.setActivityLeaseUntil(null);
        session.setPausedAt(now);
        sessionMapper.updateById(session);
    }

    private GuessStockParticipant createParticipant(GameSession session, String type, String name, String aiProfile,
                                   Long userId, Long guestId, String identityKey, Long roomMemberId,
                                   BigDecimal initialCash) {
        GuessStockParticipant participant = new GuessStockParticipant();
        participant.setSessionId(session.getId());
        participant.setParticipantType(type);
        participant.setPlayerUserId(userId);
        participant.setGuestId(guestId);
        participant.setPlayerIdentityKey(identityKey);
        participant.setRoomMemberId(roomMemberId);
        participant.setAiProfile(aiProfile);
        participant.setDisplayName(name);
        participant.setCashBalance(GuessStockRules.money(initialCash));
        participant.setInitialCash(GuessStockRules.money(initialCash));
        participant.setReservedCash(ZERO);
        participant.setRowVersion(0L);
        participantMapper.insert(participant);
        GuessStockPosition position = new GuessStockPosition();
        position.setSessionId(session.getId());
        position.setParticipantId(participant.getId());
        position.setQuantity(0L);
        position.setReservedQuantity(0L);
        position.setAverageCost(ZERO);
        positionMapper.insert(position);
        return participant;
    }

    private GuessStockRound createRound(GameSession session, GameVersion version, GuessStockConfig config,
                             int roundNo, BigDecimal previousClose) {
        long scenarioSeed = sessionSeed(session, version);
        boolean v2 = usesModernPricing(config);
        BigDecimal openingBase = v2 ? ZERO : boundedNormalPercent(new SplittableRandom(mix(scenarioSeed, roundNo, 7)),
                config.baseVolatilityPercent().multiply(new BigDecimal("0.35")));
        BigDecimal closingBase = v2 ? ZERO : boundedNormalPercent(new SplittableRandom(mix(scenarioSeed, roundNo, 17)),
                config.baseVolatilityPercent().multiply(new BigDecimal("0.65")));
        GuessStockRound round = new GuessStockRound();
        round.setSessionId(session.getId());
        round.setRoundNo(roundNo);
        round.setPreviousClosePrice(GuessStockRules.price(previousClose));
        round.setBaseChangePercent(closingBase);
        round.setOpeningBaseChangePercent(openingBase);
        round.setEventChangePercent(ZERO);
        round.setOpeningEventOccurred(0);
        round.setOpeningEventChangePercent(ZERO);
        round.setClosingEventChangePercent(ZERO);
        round.setMeanReversionChangePercent(ZERO);
        round.setTrendAge(0);
        round.setRegimeAge(0);
        round.setConsecutiveDirectionRounds(0);
        round.setMomentumChangePercent(ZERO);
        round.setRegimeChangePercent(ZERO);
        round.setScheduledEventChangePercent(ZERO);
        round.setMediumMeanReversionPercent(ZERO);
        round.setNoiseChangePercent(ZERO);
        round.setRawTotalChangePercent(ZERO);
        round.setClampAdjustmentPercent(ZERO);
        round.setFundamentalChangePercent(ZERO);
        round.setReportSurprisePercent(ZERO);
        round.setFinancialReportPublished(0);
        round.setTotalChangePercent(ZERO);
        initializeFundamentals(session.getId(), round, config);
        round.setPricingEngineVersion(config.pricingEngineVersion() == null ? "V1" : config.pricingEngineVersion());
        round.setForcedSellQuantity(0L);
        roundMapper.insert(round);
        assignEvent(session, round, config, version, OPENING, shouldForceEvent(session.getId(), roundNo));
        return round;
    }

    private void assignEvent(GameSession session, GuessStockRound round, GuessStockConfig config,
                             GameVersion version, String stage) {
        assignEvent(session, round, config, version, stage, false);
    }

    private void assignEvent(GameSession session, GuessStockRound round, GuessStockConfig config,
                             GameVersion version, String stage, boolean forceOccurrence) {
        SelectedEvent selectedEvent = selectEvent(session.getId(), config, round.getRoundNo(),
                sessionSeed(session, version), stage, forceOccurrence);
        if (selectedEvent == null) {
            if (OPENING.equals(stage)) round.setOpeningEventOccurred(0); else round.setClosingEventOccurred(0);
            roundMapper.updateById(round);
            return;
        }
        GuessStockConfig.EventConfig selected = selectedEvent.event();
        round.setEventConfigIndex(selectedEvent.index());
        round.setEventStage(stage);
        round.setEventName(selected.name());
        round.setEventDescription(selected.description());
        round.setEventType(selected.eventType());
        round.setEventRelevance(selected.relevance());
        round.setEventCredibility(selected.credibility());
        round.setEventSurprise(selected.surprise());
        round.setEventStrength(selected.strength());
        if (OPENING.equals(stage)) round.setOpeningEventOccurred(1); else round.setClosingEventOccurred(1);
        applyEventEffect(session, round, version, config, selected, stage);
        roundMapper.updateById(round);
    }

    private void applyEventEffect(GameSession session, GuessStockRound round, GameVersion version,
                                  GuessStockConfig config, GuessStockConfig.EventConfig selected, String stage) {
        String fixedDirection = CLOSING.equals(stage) && OPENING.equals(round.getEventStage())
                ? round.getOpeningEventDirection() : null;
        List<BigDecimal> recentPrices = roundMapper.selectList(new LambdaQueryWrapper<GuessStockRound>()
                        .eq(GuessStockRound::getSessionId, session.getId())
                        .lt(GuessStockRound::getRoundNo, round.getRoundNo())
                        .isNotNull(GuessStockRound::getClosePrice)
                        .orderByDesc(GuessStockRound::getRoundNo).last("LIMIT 6"))
                .stream().map(GuessStockRound::getClosePrice).collect(Collectors.toCollection(ArrayList::new));
        Collections.reverse(recentPrices);
        BigDecimal baseChange = OPENING.equals(stage) ? round.getOpeningBaseChangePercent() : round.getBaseChangePercent();
        GuessStockEventImpactEngine.Result result = GuessStockEventImpactEngine.evaluate(
                new GuessStockEventImpactEngine.Context(selected, fixedDirection, baseChange,
                        config.baseVolatilityPercent(), referencePrice(round, stage), recentPrices,
                        mix(sessionSeed(session, version), round.getRoundNo(), OPENING.equals(stage) ? 701 : 1701),
                        marketState(session.getId(), referencePrice(round, stage),
                                config.baseVolatilityPercent().doubleValue())));
        BigDecimal appliedEventChange = result.finalChangePercent();
        if (usesModernPricing(config) && result.effective()) {
            appliedEventChange = scheduleEventImpact(session, round, selected, stage, result,
                    config.baseVolatilityPercent().doubleValue());
        }
        if ("V3".equals(config.pricingEngineVersion()) && result.effective()) {
            setFundamentals(round, FundamentalEngine.applyEvent(fundamentalState(round), selected.eventType(),
                    result.direction(), selected.strength()));
        }
        if (stage.equals(round.getEventStage())) {
            round.setEventDirection(result.direction());
            round.setEventEffective(result.effective() ? 1 : 0);
            round.setEventChangePercent(appliedEventChange);
        }
        if (OPENING.equals(stage)) {
            round.setOpeningEventDirection(result.direction());
            round.setOpeningEventEffective(result.effective() ? 1 : 0);
            round.setOpeningEventChangePercent(appliedEventChange);
            round.setOpeningEventTier(result.tier());
            round.setOpeningEventRegime(result.regime());
            round.setOpeningEventRawChangePercent(result.rawChangePercent());
            round.setOpeningEventAdaptationPercent(result.adaptationPercent());
            round.setOpeningEventReason(result.reason());
        } else {
            round.setClosingEventDirection(result.direction());
            round.setClosingEventEffective(result.effective() ? 1 : 0);
            round.setClosingEventChangePercent(appliedEventChange);
            round.setClosingEventTier(result.tier());
            round.setClosingEventRegime(result.regime());
            round.setClosingEventRawChangePercent(result.rawChangePercent());
            round.setClosingEventAdaptationPercent(result.adaptationPercent());
            round.setClosingEventReason(result.reason());
        }
    }

    private BigDecimal scheduleEventImpact(GameSession session, GuessStockRound round,
                                             GuessStockConfig.EventConfig event, String stage,
                                             GuessStockEventImpactEngine.Result result,
                                             double baseVolatilityPct) {
        List<GuessStockEventImpactSlice> existing = eventImpactSliceMapper.selectList(
                new LambdaQueryWrapper<GuessStockEventImpactSlice>()
                        .eq(GuessStockEventImpactSlice::getSessionId, session.getId())
                        .eq(GuessStockEventImpactSlice::getSourceRoundNo, round.getRoundNo())
                        .eq(GuessStockEventImpactSlice::getSourceStage, stage)
                        .eq(GuessStockEventImpactSlice::getEventConfigIndex, round.getEventConfigIndex()));
        if (!existing.isEmpty()) {
            return existing.stream().filter(item -> item.getTargetRoundNo().equals(round.getRoundNo())
                            && stage.equals(item.getTargetStage()))
                    .map(GuessStockEventImpactSlice::getSliceImpactPercent).reduce(ZERO, BigDecimal::add);
        }
        long similarCount = roundMapper.selectCount(new LambdaQueryWrapper<GuessStockRound>()
                .eq(GuessStockRound::getSessionId, session.getId())
                .lt(GuessStockRound::getRoundNo, round.getRoundNo())
                .ge(GuessStockRound::getRoundNo, Math.max(1, round.getRoundNo() - 5))
                .eq(GuessStockRound::getEventType, event.eventType())
                .eq(GuessStockRound::getEventDirection, result.direction()));
        double saturation = 1d / (1d + similarCount * .35d);
        MarketStateCalculator.MarketState state = marketState(session.getId(), round.getPreviousClosePrice(),
                baseVolatilityPct);
        boolean upTrend = state.trend() == MarketStateCalculator.TrendState.UP
                || state.trend() == MarketStateCalculator.TrendState.STRONG_UP;
        boolean opposite = ("UP".equals(result.direction()) && !upTrend
                && state.trend() != MarketStateCalculator.TrendState.SIDEWAYS)
                || ("DOWN".equals(result.direction()) && upTrend);
        double contrarian = opposite && state.trendAge() >= 4 && "HIGH".equals(event.relevance())
                && "CONFIRMED".equals(event.credibility()) && "MAJOR".equals(event.surprise()) ? 1.35 : 1d;
        List<EventImpactSchedule.Slice> schedule = EventImpactSchedule.create(event.eventType(),
                round.getRoundNo(), stage, result.finalChangePercent(), saturation, contrarian);
        for (EventImpactSchedule.Slice value : schedule) {
            GuessStockEventImpactSlice slice = new GuessStockEventImpactSlice();
            slice.setSessionId(session.getId());
            slice.setSourceRoundNo(round.getRoundNo());
            slice.setSourceStage(stage);
            slice.setEventConfigIndex(round.getEventConfigIndex());
            slice.setTargetRoundNo(value.targetRound());
            slice.setTargetStage(value.targetStage());
            slice.setDirection(result.direction());
            slice.setEventType(event.eventType());
            slice.setTotalImpactPercent(result.finalChangePercent());
            slice.setSliceWeight(value.weight());
            slice.setSliceImpactPercent(value.impactPct());
            slice.setSaturationFactor(BigDecimal.valueOf(saturation).setScale(4, RoundingMode.HALF_UP));
            slice.setContrarianFactor(BigDecimal.valueOf(contrarian).setScale(4, RoundingMode.HALF_UP));
            eventImpactSliceMapper.insert(slice);
        }
        return schedule.get(0).impactPct();
    }

    private SelectedEvent selectEvent(Long sessionId, GuessStockConfig config, int roundNo, long scenarioSeed,
                                      String stage, boolean forceOccurrence) {
        GuessStockEventChainState activeChain = eventChainStateMapper.selectOne(
                new LambdaQueryWrapper<GuessStockEventChainState>()
                        .eq(GuessStockEventChainState::getSessionId, sessionId)
                        .le(GuessStockEventChainState::getCooldownUntilRound, roundNo)
                        .orderByDesc(GuessStockEventChainState::getUpdatedAt).last("LIMIT 1"));
        if (activeChain != null) {
            GuessStockConfig.EventConfig source = config.events().stream()
                    .filter(item -> activeChain.getChainKey().equalsIgnoreCase(item.chainKey()))
                    .filter(item -> item.chainStep() != null && item.chainStep().equals(activeChain.getLastStep()))
                    .findFirst().orElse(null);
            if (source != null) {
                EventChainEngine.Selection followUp = EventChainEngine.next(source, config.events(),
                        new EventChainEngine.ChainState(activeChain.getChainKey(), activeChain.getLastStep(),
                                activeChain.getDepth(), activeChain.getCooldownUntilRound()),
                        mix(scenarioSeed, roundNo, OPENING.equals(stage) ? 449 : 1449));
                if (followUp != null) {
                    int selectedIndex = config.events().indexOf(followUp.event());
                    updateChainState(activeChain, followUp.nextState());
                    return new SelectedEvent(selectedIndex, followUp.event());
                }
            }
        }
        List<GuessStockRound> eventHistory = roundMapper.selectList(new LambdaQueryWrapper<GuessStockRound>()
                .eq(GuessStockRound::getSessionId, sessionId).isNotNull(GuessStockRound::getEventConfigIndex)
                .orderByDesc(GuessStockRound::getRoundNo));
        Set<Integer> used = new HashSet<>();
        if (!usesModernPricing(config)) {
            used.addAll(eventHistory.stream().map(GuessStockRound::getEventConfigIndex).toList());
        } else {
            for (int index = 0; index < config.events().size(); index++) {
                GuessStockConfig.EventConfig candidate = config.events().get(index);
                int exactCooldown = "STRONG".equals(candidate.strength()) ? 8 : 4;
                int typeCooldown = "STRONG".equals(candidate.strength()) ? 5 : 3;
                int candidateIndex = index;
                boolean cooling = eventHistory.stream().anyMatch(previous -> {
                    int age = roundNo - previous.getRoundNo();
                    return (previous.getEventConfigIndex().equals(candidateIndex) && age <= exactCooldown)
                            || (candidate.eventType().equals(previous.getEventType()) && age <= typeCooldown);
                });
                if (cooling) used.add(index);
            }
        }
        int salt = OPENING.equals(stage) ? 997 : 1997;
        Integer index = GuessStockEventSelector.select(config.events(), used, mix(scenarioSeed, roundNo, salt), forceOccurrence);
        if (index == null) return null;
        GuessStockConfig.EventConfig selected = config.events().get(index);
        if (selected.chainKey() != null) startChainState(sessionId, selected, roundNo);
        return new SelectedEvent(index, selected);
    }

    private void startChainState(Long sessionId, GuessStockConfig.EventConfig event, int roundNo) {
        GuessStockEventChainState state = eventChainStateMapper.selectOne(
                new LambdaQueryWrapper<GuessStockEventChainState>()
                        .eq(GuessStockEventChainState::getSessionId, sessionId)
                        .eq(GuessStockEventChainState::getChainKey, event.chainKey()).last("LIMIT 1"));
        if (state == null) {
            state = new GuessStockEventChainState();
            state.setSessionId(sessionId);
            state.setChainKey(event.chainKey());
            state.setLastStep(event.chainStep());
            state.setDepth(1);
            state.setCooldownUntilRound(roundNo + 1);
            eventChainStateMapper.insert(state);
        } else {
            state.setLastStep(event.chainStep());
            state.setDepth(1);
            state.setCooldownUntilRound(roundNo + 1);
            eventChainStateMapper.updateById(state);
        }
    }

    private void updateChainState(GuessStockEventChainState entity, EventChainEngine.ChainState state) {
        entity.setLastStep(state.lastStep());
        entity.setDepth(state.depth());
        entity.setCooldownUntilRound(state.cooldownUntilRound());
        eventChainStateMapper.updateById(entity);
    }

    private boolean shouldForceEvent(Long sessionId, int roundNo) {
        if (roundNo <= 5) return false;
        long eventRounds = roundMapper.selectCount(new LambdaQueryWrapper<GuessStockRound>()
                .eq(GuessStockRound::getSessionId, sessionId)
                .between(GuessStockRound::getRoundNo, roundNo - 5, roundNo - 1)
                .isNotNull(GuessStockRound::getEventConfigIndex));
        return eventRounds == 0;
    }

    private record SelectedEvent(int index, GuessStockConfig.EventConfig event) {}

    private void prepareRoundPhase(GameSession session, GuessStockRound round, GuessStockConfig config, LocalDateTime now) {
        startRoundIntro(session, now);
    }

    private void prepareOpeningPrelude(GameSession session, GuessStockRound round,
                                       GuessStockConfig config, LocalDateTime now) {
        if (round.getEventName() != null) {
            setGatePhase(session, "OPENING_EVENT_ACK", now);
            return;
        }
        if (shouldOpenCreditPhase(config.loanEnabled(), config.creditInteractionMode())) startCreditPhase(session, now);
        else startTrading(session, OPENING, now);
    }

    private EntertainmentDtos.SessionResponse buildSnapshot(GameSession session, PlayerIdentity viewer) {
        EntertainmentGame game = gameMapper.selectById(session.getGameId());
        GameVersion version = gameService.requireVersion(session.getGameVersionId());
        GuessStockConfig config = gameService.readConfig(version.getConfigJson());
        GuessStockRound currentRound = currentRound(session);
        List<GuessStockRound> rounds = roundMapper.selectList(new LambdaQueryWrapper<GuessStockRound>()
                .eq(GuessStockRound::getSessionId, session.getId()).orderByAsc(GuessStockRound::getRoundNo));
        Map<Long, GuessStockPosition> positions = positionMap(session.getId());
        BigDecimal markPrice = currentRound.getClosePrice() != null ? currentRound.getClosePrice()
                : currentRound.getOpenPrice() != null ? currentRound.getOpenPrice() : currentRound.getPreviousClosePrice();
        List<GuessStockParticipant> participants = participants(session.getId());
        List<EntertainmentDtos.ParticipantResponse> participantResponses = participants.stream().map(participant -> {
            GuessStockPosition position = positions.get(participant.getId());
            GuessStockCreditService.CreditStatus credit = creditService.status(session.getId(), participant, position, markPrice, config);
            BigDecimal asset = participant.getFinalAsset() != null ? participant.getFinalAsset()
                    : credit.risk().netAsset();
            BigDecimal initialCash = participant.getInitialCash() == null ? config.initialCash() : participant.getInitialCash();
            BigDecimal participantReturn = returnPercent(GuessStockRules.money(asset.subtract(initialCash)), initialCash);
            return new EntertainmentDtos.ParticipantResponse(participant.getId(), participant.getParticipantType(),
                    participant.getDisplayName(), participant.getAiProfile(), participant.getInitialCash(), participant.getCashBalance(),
                    participant.getReservedCash(), position.getQuantity(), position.getReservedQuantity(),
                    position.getAverageCost(), asset, participantReturn, participant.getFinalRank(), viewer != null
                    && "PLAYER".equals(participant.getParticipantType())
                    && (viewer.key().equals(participant.getPlayerIdentityKey())
                    || (participant.getPlayerIdentityKey() == null && session.getRoomId() == null
                    && viewer.key().equals(session.getPlayerIdentityKey()))), credit.risk().debt(),
                    credit.risk().exposurePercent(), credit.risk().ltvPercent(), credit.risk().riskState());
        }).sorted(Comparator.comparing(EntertainmentDtos.ParticipantResponse::returnPercent).reversed()
                .thenComparing(EntertainmentDtos.ParticipantResponse::asset, Comparator.reverseOrder())).toList();
        GuessStockParticipant player = participants.stream().filter(p -> "PLAYER".equals(p.getParticipantType())
                && viewer != null && (viewer.key().equals(p.getPlayerIdentityKey())
                || (p.getPlayerIdentityKey() == null && session.getRoomId() == null
                && viewer.key().equals(session.getPlayerIdentityKey())))).findFirst()
                .orElseGet(() -> participants.stream().filter(p -> "PLAYER".equals(p.getParticipantType())).findFirst().orElseThrow());
        GuessStockOrder order = orderMapper.selectOne(new LambdaQueryWrapper<GuessStockOrder>()
                .eq(GuessStockOrder::getSessionId, session.getId()).eq(GuessStockOrder::getParticipantId, player.getId())
                .eq(GuessStockOrder::getRoundNo, session.getCurrentRound())
                .eq(GuessStockOrder::getStage, stageForPhase(session.getPhase()))
                .eq(GuessStockOrder::getStatus, "PENDING").last("LIMIT 1"));
        List<GuessStockOrder> marketOrders = isTradingPhase(session.getPhase())
                && !"TARGET_AUCTION".equals(config.tradingMode())
                ? pendingOrders(session.getId(), session.getCurrentRound(), stageForPhase(session.getPhase())) : List.of();
        List<GuessStockTrade> trades = tradeMapper.selectList(new LambdaQueryWrapper<GuessStockTrade>()
                .eq(GuessStockTrade::getSessionId, session.getId()).orderByDesc(GuessStockTrade::getId));
        List<GuessStockTrade> playerTrades = tradeMapper.selectList(new LambdaQueryWrapper<GuessStockTrade>()
                .eq(GuessStockTrade::getSessionId, session.getId())
                .eq(GuessStockTrade::getParticipantId, player.getId())
                .orderByDesc(GuessStockTrade::getId));
        Map<Long, String> names = new HashMap<>();
        participants.forEach(participant -> names.put(participant.getId(), participant.getDisplayName()));
        Map<String, Object> result = readMap(session.getResultJson());
        if ("COMPLETED".equals(session.getStatus()) && player.getFinalAsset() != null) {
            BigDecimal initialCash = player.getInitialCash() == null ? config.initialCash() : player.getInitialCash();
            BigDecimal playerProfit = GuessStockRules.money(player.getFinalAsset().subtract(initialCash));
            result = new HashMap<>();
            result.put("finalAsset", player.getFinalAsset());
            result.put("profitAmount", playerProfit);
            result.put("returnPercent", returnPercent(playerProfit, initialCash));
            result.put("inSessionRank", player.getFinalRank());
        }
        Long leaderboardRank = null;
        if ("COMPLETED".equals(session.getStatus())) {
            GameLeaderboardEntry entry = leaderboardMapper.selectOne(new LambdaQueryWrapper<GameLeaderboardEntry>()
                    .eq(GameLeaderboardEntry::getGameVersionId, session.getGameVersionId())
                    .eq(GameLeaderboardEntry::getPlayerIdentityKey, player.getPlayerIdentityKey() == null
                            ? session.getPlayerIdentityKey() : player.getPlayerIdentityKey()).last("LIMIT 1"));
            if (entry != null) leaderboardRank = leaderboardMapper.rankFor(
                    entry.getGameVersionId(), entry.getScore(), entry.getFinalAsset());
        }
        EntertainmentDtos.RoomResponse roomResponse = session.getRoomId() == null ? null : roomResponse(session.getRoomId(), viewer, session);
        List<EntertainmentDtos.AiDecisionResponse> aiDecisions = aiDecisionResponses(session, names);
        List<EntertainmentDtos.AiReviewResponse> aiReviews = "COMPLETED".equals(session.getStatus())
                ? aiReviewResponses(session, participants, positions, rounds, config) : List.of();
        EntertainmentDtos.CreditStatusResponse credit = creditResponse(creditService.status(session.getId(), player,
                positions.get(player.getId()), markPrice, config), session.getCurrentRound());
        return new EntertainmentDtos.SessionResponse(session.getId(), session.getGameId(), session.getGameVersionId(),
                version.getVersionNo(), session.getStatus(), session.getCurrentRound(), session.getPhase(), session.getStartedAt(), LocalDateTime.now(),
                session.getPhaseEndsAt(), session.getPhaseRemainingMs(), playbackSpeed(session), autoAdvance(session),
                config, gameService.toSummary(game, viewer),
                roundResponse(currentRound), rounds.stream().map(this::roundResponse).toList(), participantResponses,
                order == null ? null : orderResponse(order), marketOrders.stream().map(GuessStockSessionService::orderResponse).toList(),
                trades.stream().map(trade -> tradeResponse(trade, names)).toList(),
                playerTrades.stream().map(trade -> tradeResponse(trade, names)).toList(),
                decimal(result.get("finalAsset")), decimal(result.get("profitAmount")), decimal(result.get("returnPercent")),
                integer(result.get("inSessionRank")), leaderboardRank, roomResponse, aiDecisions, credit, aiReviews);
    }

    private List<EntertainmentDtos.AiReviewResponse> aiReviewResponses(
            GameSession session, List<GuessStockParticipant> participants, Map<Long, GuessStockPosition> positions,
            List<GuessStockRound> rounds, GuessStockConfig config) {
        Map<Integer, GuessStockRound> roundMap = rounds.stream().collect(Collectors.toMap(
                GuessStockRound::getRoundNo, value -> value));
        List<AiInvestmentDecision> decisions = aiDecisionMapper.selectList(
                new LambdaQueryWrapper<AiInvestmentDecision>().eq(AiInvestmentDecision::getSessionId, session.getId())
                        .orderByAsc(AiInvestmentDecision::getId));
        List<GuessStockAiTask> tasks = aiTaskMapper.selectList(new LambdaQueryWrapper<GuessStockAiTask>()
                .eq(GuessStockAiTask::getSessionId, session.getId()).orderByAsc(GuessStockAiTask::getId));
        List<GuessStockTrade> trades = tradeMapper.selectList(new LambdaQueryWrapper<GuessStockTrade>()
                .eq(GuessStockTrade::getSessionId, session.getId()).orderByAsc(GuessStockTrade::getId));
        BigDecimal finalMark = rounds.stream().map(GuessStockRound::getClosePrice).filter(Objects::nonNull)
                .reduce((left, right) -> right).orElse(config.initialPrice());
        List<EntertainmentDtos.AiReviewResponse> result = new ArrayList<>();
        for (GuessStockParticipant ai : participants.stream().filter(value -> "AI".equals(value.getParticipantType())).toList()) {
            List<AiInvestmentDecision> aiDecisions = decisions.stream()
                    .filter(value -> ai.getId().equals(value.getParticipantId())).toList();
            List<EntertainmentDtos.AiDecisionHighlightResponse> highlights = aiDecisions.stream()
                    .map(value -> decisionHighlight(value, roundMap)).filter(Objects::nonNull).toList();
            EntertainmentDtos.AiDecisionHighlightResponse best = highlights.stream()
                    .max(Comparator.comparing(EntertainmentDtos.AiDecisionHighlightResponse::outcomePercent)).orElse(null);
            EntertainmentDtos.AiDecisionHighlightResponse mistake = highlights.stream()
                    .min(Comparator.comparing(EntertainmentDtos.AiDecisionHighlightResponse::outcomePercent))
                    .filter(value -> value.outcomePercent().signum() < 0).orElse(null);
            List<GuessStockTrade> aiTrades = trades.stream()
                    .filter(value -> ai.getId().equals(value.getParticipantId())).toList();
            int reversals = 0;
            String previousSide = null;
            for (GuessStockTrade trade : aiTrades) {
                if (previousSide != null && !previousSide.equals(trade.getSide())) reversals++;
                previousSide = trade.getSide();
            }
            List<GuessStockAiTask> aiTasks = tasks.stream()
                    .filter(value -> ai.getId().equals(value.getParticipantId())).toList();
            int modelCalls = (int) aiTasks.stream().filter(value -> value.getProvider() != null
                    && !Integer.valueOf(1).equals(value.getCacheHit())).count();
            int fallbackCount = (int) aiTasks.stream().filter(value -> value.getFallbackReason() != null
                    && !value.getFallbackReason().isBlank()).count();
            GuessStockCreditService.CreditStatus credit = creditService.status(session.getId(), ai,
                    positions.get(ai.getId()), finalMark, config);
            BigDecimal initial = ai.getInitialCash() == null ? config.initialCash() : ai.getInitialCash();
            BigDecimal finalAsset = ai.getFinalAsset() == null ? initial : ai.getFinalAsset();
            result.add(new EntertainmentDtos.AiReviewResponse(ai.getId(), ai.getDisplayName(), ai.getAiProfile(),
                    returnPercent(finalAsset.subtract(initial), initial), ai.getFinalRank(), best, mistake,
                    (int) aiDecisions.stream().filter(value -> "REPLACE_THESIS".equals(value.getDecisionType())).count(),
                    reversals, modelCalls, fallbackCount, credit.totalInterestCharged(),
                    credit.totalForcedLiquidationShares()));
        }
        return result.stream().sorted(Comparator.comparing(EntertainmentDtos.AiReviewResponse::finalRank,
                Comparator.nullsLast(Comparator.naturalOrder()))).toList();
    }

    private static EntertainmentDtos.AiDecisionHighlightResponse decisionHighlight(
            AiInvestmentDecision decision, Map<Integer, GuessStockRound> rounds) {
        if (decision.getExecutedShares() == null || decision.getExecutedShares() <= 0
                || decision.getExecutedDirection() == null) return null;
        GuessStockRound round = rounds.get(decision.getRoundNo());
        if (round == null) return null;
        BigDecimal executionPrice;
        BigDecimal outcomePrice;
        if (OPENING.equals(decision.getPhase())) {
            executionPrice = round.getOpenPrice();
            outcomePrice = round.getClosePrice();
        } else {
            executionPrice = round.getClosePrice();
            GuessStockRound next = rounds.get(decision.getRoundNo() + 1);
            outcomePrice = next == null ? null : next.getClosePrice();
        }
        if (executionPrice == null || outcomePrice == null || executionPrice.signum() <= 0) return null;
        BigDecimal outcome = outcomePrice.subtract(executionPrice).divide(executionPrice, 10, RoundingMode.HALF_UP)
                .multiply(new BigDecimal("100"));
        if ("SELL".equals(decision.getExecutedDirection())) outcome = outcome.negate();
        return new EntertainmentDtos.AiDecisionHighlightResponse(decision.getRoundNo(), decision.getPhase(),
                decision.getExecutedDirection(), decision.getExecutedShares(), GuessStockRules.rate(outcome),
                decision.getExecutionReason());
    }

    private static EntertainmentDtos.CreditStatusResponse creditResponse(GuessStockCreditService.CreditStatus status,
                                                                           int currentRound) {
        List<EntertainmentDtos.LoanLedgerResponse> ledger = ledgerResponses(status.ledgers());
        List<EntertainmentDtos.LoanContractResponse> contracts = contractResponses(status.contracts(), currentRound);
        List<EntertainmentDtos.CreditBankResponse> banks = status.banks().stream().map(bank ->
                new EntertainmentDtos.CreditBankResponse(bank.product().code(), bank.product().name(),
                        bank.product().riskLevel(), bank.facility() == null ? null : bank.facility().getStatus(),
                        bank.principal(), bank.accruedInterest(), bank.principal().add(bank.accruedInterest()),
                        bank.availableCredit(), bank.nearestMaturityRound(), bank.product().warningLtv(),
                        bank.product().blockLtv(), bank.product().liquidationLtv(), bank.product().recoveryLtv(),
                        ledgerResponses(bank.ledgers()), contractResponses(bank.contracts(), currentRound))).toList();
        return new EntertainmentDtos.CreditStatusResponse(status.enabled(),
                status.product() == null ? null : status.product().code(),
                status.product() == null ? null : status.product().name(),
                status.product() == null ? null : status.product().riskLevel(),
                status.facility() == null ? null : status.facility().getStatus(), status.principal(),
                status.accruedInterest(), status.risk().debt(), status.availableCredit(),
                status.productAvailableCredit(),
                status.nearestMaturityRound(), status.risk().stockValue(), status.risk().netAsset(),
                status.risk().collateralValue(), status.risk().exposurePercent(), status.risk().ltvPercent(),
                status.risk().riskState(), status.product() == null ? null : status.product().warningLtv(),
                status.product() == null ? null : status.product().blockLtv(),
                status.product() == null ? null : status.product().liquidationLtv(),
                status.product() == null ? null : status.product().recoveryLtv(),
                status.totalInterestCharged(), status.totalForcedLiquidationShares(), ledger, contracts, banks);
    }

    private static List<EntertainmentDtos.LoanLedgerResponse> ledgerResponses(List<LoanLedger> rows) {
        return rows.stream().map(row -> new EntertainmentDtos.LoanLedgerResponse(row.getId(), row.getRoundNo(),
                row.getEntryType(), row.getPrincipalDelta(), row.getInterestDelta(), row.getCashDelta(),
                row.getReason(), row.getCreatedAt())).toList();
    }

    private static List<EntertainmentDtos.LoanContractResponse> contractResponses(List<LoanContract> rows,
                                                                                    int currentRound) {
        return rows.stream().map(row -> new EntertainmentDtos.LoanContractResponse(row.getId(),
                row.getPrincipalOriginal(), row.getPrincipalOutstanding(), row.getAccruedInterest(),
                row.getInterestRate(), row.getOpenedRound(),
                row.getOriginalMaturityRound() == null ? row.getMaturityRound() : row.getOriginalMaturityRound(),
                row.getMaturityRound(), Math.max(0, row.getMaturityRound() - currentRound + 1), row.getStatus(),
                row.getExtensionCount() == null ? 0 : row.getExtensionCount(),
                row.getTotalExtendedRounds() == null ? 0 : row.getTotalExtendedRounds(),
                List.of("ACTIVE", "PARTIAL_REPAID").contains(row.getStatus())
                        && (row.getExtensionCount() == null || row.getExtensionCount() < 2)
                        && row.getMaturityRound() >= currentRound)).toList();
    }

    private GameSession requireOwnedLocked(Long sessionId, PlayerIdentity player) {
        GameSession session = sessionMapper.selectByIdForUpdate(sessionId);
        if (session == null) throw new BusinessException(ErrorCode.NOT_FOUND, "游戏场次不存在");
        if (player == null) throw new BusinessException(ErrorCode.FORBIDDEN, "无权访问该游戏场次");
        long visibleRecords = participantMapper.selectCount(new LambdaQueryWrapper<GuessStockParticipant>()
                .eq(GuessStockParticipant::getSessionId, sessionId)
                .eq(GuessStockParticipant::getParticipantType, "PLAYER")
                .eq(GuessStockParticipant::getPlayerIdentityKey, player.key())
                .and(wrapper -> wrapper.isNull(GuessStockParticipant::getRecordDeleted)
                        .or().eq(GuessStockParticipant::getRecordDeleted, 0)));
        if (visibleRecords == 0) throw new BusinessException(ErrorCode.NOT_FOUND, "游戏场次不存在");
        if (session.getRoomId() != null) {
            GameRoomMember member = requireActiveMember(session.getRoomId(), player);
            member.setLastSeenAt(LocalDateTime.now());
            roomMemberMapper.updateById(member);
        } else if (!player.key().equals(session.getPlayerIdentityKey())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权访问该游戏场次");
        }
        return session;
    }

    private GuessStockRound currentRound(GameSession session) {
        GuessStockRound round = roundMapper.selectOne(new LambdaQueryWrapper<GuessStockRound>()
                .eq(GuessStockRound::getSessionId, session.getId())
                .eq(GuessStockRound::getRoundNo, session.getCurrentRound()).last("LIMIT 1"));
        if (round == null) throw new BusinessException(ErrorCode.SERVER_ERROR, "游戏回合数据缺失");
        return round;
    }

    private GuessStockParticipant playerParticipant(Long sessionId, PlayerIdentity player) {
        GuessStockParticipant participant = participantMapper.selectOne(new LambdaQueryWrapper<GuessStockParticipant>()
                .eq(GuessStockParticipant::getSessionId, sessionId)
                .eq(GuessStockParticipant::getParticipantType, "PLAYER")
                .eq(GuessStockParticipant::getPlayerIdentityKey, player.key())
                .and(wrapper -> wrapper.isNull(GuessStockParticipant::getRecordDeleted)
                        .or().eq(GuessStockParticipant::getRecordDeleted, 0)).last("LIMIT 1"));
        if (participant == null) throw new BusinessException(ErrorCode.SERVER_ERROR, "玩家账户数据缺失");
        return participant;
    }

    private List<GuessStockParticipant> participants(Long sessionId) {
        return participantMapper.selectList(new LambdaQueryWrapper<GuessStockParticipant>()
                .eq(GuessStockParticipant::getSessionId, sessionId).orderByAsc(GuessStockParticipant::getId));
    }

    private GameRoomMember requireActiveMember(Long roomId, PlayerIdentity player) {
        GameRoomMember member = roomMemberMapper.selectForUpdate(roomId, player.key());
        if (member == null || !"ACTIVE".equals(member.getStatus())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "你不在该多人房间中");
        }
        return member;
    }

    private List<GameRoomMember> allActiveMembers(Long roomId) {
        return roomMemberMapper.selectList(new LambdaQueryWrapper<GameRoomMember>()
                .eq(GameRoomMember::getRoomId, roomId).eq(GameRoomMember::getStatus, "ACTIVE")
                .orderByAsc(GameRoomMember::getJoinedAt));
    }

    private EntertainmentDtos.RoomResponse roomResponse(Long roomId, PlayerIdentity viewer, GameSession session) {
        GameRoom room = roomMapper.selectById(roomId);
        if (room == null) return null;
        List<EntertainmentDtos.RoomMemberResponse> members = allActiveMembers(roomId).stream().map(member ->
                new EntertainmentDtos.RoomMemberResponse(member.getId(), member.getDisplayName(), member.getPlayerType(),
                        member.getMemberRole(), member.getStatus(), viewer != null && viewer.key().equals(member.getPlayerIdentityKey()),
                        credentialService.readyForRoom(roomId, member.getPlayerIdentityKey()),
                        session.getCurrentRound().equals(member.getEventAckRound()),
                        session.getCurrentRound().equals(member.getReadyRound()),
                        session.getId().equals(member.getRestartSessionId()), member.getEventAckStage(),
                        member.getReadyStage(), member.getLastSeenAt())).toList();
        EntertainmentGame game = gameMapper.selectById(room.getGameId());
        GuessStockConfig config = gameService.readConfig(gameService.requireVersion(room.getGameVersionId()).getConfigJson());
        return new EntertainmentDtos.RoomResponse(room.getId(), room.getInviteCode(), room.getStatus(), room.getMaxPlayers(),
                room.getGameId(), room.getGameVersionId(), room.getSessionId(),
                viewer != null && viewer.key().equals(room.getHostIdentityKey()), Boolean.TRUE.equals(config.aiEnabled()),
                credentialService.readyForGame(room.getGameId()), members, gameService.toSummary(game, viewer));
    }

    private List<EntertainmentDtos.AiDecisionResponse> aiDecisionResponses(GameSession session, Map<Long, String> names) {
        Long sessionId = session.getId();
        Map<String, AiInvestmentDecision> audits = aiDecisionMapper.selectList(
                        new LambdaQueryWrapper<AiInvestmentDecision>().eq(AiInvestmentDecision::getSessionId, sessionId)
                                .orderByDesc(AiInvestmentDecision::getId).last("LIMIT 60"))
                .stream().collect(Collectors.toMap(item -> decisionAuditKey(item.getParticipantId(), item.getRoundNo(),
                        item.getPhase()), item -> item, (first, ignored) -> first));
        return aiTaskMapper.selectList(new LambdaQueryWrapper<GuessStockAiTask>()
                        .eq(GuessStockAiTask::getSessionId, sessionId).orderByDesc(GuessStockAiTask::getId).last("LIMIT 30"))
                .stream().filter(task -> aiDecisionRevealed(task, session)).map(task -> {
                    ModelAiDecisionService.Decision decision = readAgentDecision(task.getDecisionJson());
                    AiInvestmentDecision audit = audits.get(decisionAuditKey(task.getParticipantId(), task.getRoundNo(), task.getStage()));
                    return new EntertainmentDtos.AiDecisionResponse(task.getParticipantId(),
                            names.getOrDefault(task.getParticipantId(), "AI"), task.getRoundNo(), task.getStage(), task.getDecisionStatus(),
                            task.getDecisionReason(), task.getErrorMessage(), decision == null ? null : decision.marketRegime(),
                            audit == null ? null : audit.getExecutedDirection(), decision == null ? null : decision.targetPositionPct(),
                            decision == null ? null : decision.confidence(), decision == null ? null : decision.timeHorizon(),
                            decision == null ? List.of() : decision.reasonCodes(),
                            decision == null ? List.of() : decision.invalidConditions(),
                            decision == null ? null : decision.decisionType(), decision == null ? null : decision.direction(),
                            decision == null ? null : decision.urgency(), audit == null ? null : Integer.valueOf(1).equals(audit.getMaterialChange()),
                            audit == null ? List.of() : readJsonList(audit.getNewEvidenceJson()),
                            audit == null ? List.of() : readJsonList(audit.getTriggeredConditionsJson()),
                            audit == null ? null : audit.getExecutedShares(), audit == null ? null : audit.getExecutionReason());
                }).toList();
    }

    private static boolean aiDecisionRevealed(GuessStockAiTask task, GameSession session) {
        if (task.getRoundNo() < session.getCurrentRound()) return true;
        if (task.getRoundNo() > session.getCurrentRound()) return false;
        if (OPENING.equals(task.getStage())) {
            return !List.of("ROUND_START", "OPENING_EVENT_ACK", "CREDIT_TRADING", "OPENING_TRADING")
                    .contains(session.getPhase());
        }
        return List.of("ROUND_RESULT_ACK", "CREDIT_REPAYMENT", "COMPLETED")
                .contains(session.getPhase());
    }

    private static String decisionAuditKey(Long participantId, int roundNo, String stage) {
        return participantId + ":" + roundNo + ":" + stage;
    }

    private List<?> readJsonList(String value) {
        if (value == null || value.isBlank()) return List.of();
        try { return objectMapper.readValue(value, List.class); }
        catch (JsonProcessingException ignored) { return List.of(); }
    }

    private ModelAiDecisionService.Decision readAgentDecision(String value) {
        if (value == null || value.isBlank()) return null;
        try { return objectMapper.readValue(value, ModelAiDecisionService.Decision.class); }
        catch (JsonProcessingException ignored) { return null; }
    }

    private Map<Long, GuessStockPosition> positionMap(Long sessionId) {
        Map<Long, GuessStockPosition> result = new HashMap<>();
        positionMapper.selectList(new LambdaQueryWrapper<GuessStockPosition>()
                .eq(GuessStockPosition::getSessionId, sessionId)).forEach(position -> result.put(position.getParticipantId(), position));
        return result;
    }

    private List<GuessStockOrder> pendingOrders(Long sessionId, int roundNo) {
        return orderMapper.selectList(new LambdaQueryWrapper<GuessStockOrder>()
                .eq(GuessStockOrder::getSessionId, sessionId).eq(GuessStockOrder::getRoundNo, roundNo)
                .eq(GuessStockOrder::getStatus, "PENDING").orderByAsc(GuessStockOrder::getId));
    }

    private List<GuessStockOrder> pendingOrders(Long sessionId, int roundNo, String stage) {
        return orderMapper.selectList(new LambdaQueryWrapper<GuessStockOrder>()
                .eq(GuessStockOrder::getSessionId, sessionId).eq(GuessStockOrder::getRoundNo, roundNo)
                .eq(GuessStockOrder::getStage, stage).eq(GuessStockOrder::getStatus, "PENDING")
                .orderByAsc(GuessStockOrder::getId));
    }

    private void recordAction(GameSession session, EntertainmentDtos.ActionRequest request) {
        GameSessionAction action = new GameSessionAction();
        action.setSessionId(session.getId());
        action.setRoundNo(session.getCurrentRound());
        action.setActionType(request.actionType().toUpperCase());
        Map<String, Object> payload = new HashMap<>();
        payload.put("side", request.side() == null ? "" : request.side());
        payload.put("quantity", request.quantity() == null ? 0 : request.quantity());
        payload.put("allIn", Boolean.TRUE.equals(request.allIn()));
        if (request.targetExposurePercent() != null) payload.put("targetExposurePercent", request.targetExposurePercent());
        if (request.playbackSpeed() != null) payload.put("playbackSpeed", request.playbackSpeed());
        if (request.autoAdvance() != null) payload.put("autoAdvance", request.autoAdvance());
        action.setPayloadJson(write(payload));
        action.setClientRequestId(request.clientRequestId());
        action.setAccepted(1);
        actionMapper.insert(action);
    }

    private void touchLease(GameSession session) { session.setActivityLeaseUntil(LocalDateTime.now().plusSeconds(LEASE_SECONDS)); }
    private void cancelAiTasks(Long sessionId) {
        List<GuessStockAiTask> tasks = aiTaskMapper.selectList(new LambdaQueryWrapper<GuessStockAiTask>()
                .eq(GuessStockAiTask::getSessionId, sessionId).eq(GuessStockAiTask::getDecisionStatus, "PENDING"));
        for (GuessStockAiTask task : tasks) { task.setDecisionStatus("CANCELLED"); aiTaskMapper.updateById(task); }
    }

    private EntertainmentDtos.RoundResponse roundResponse(GuessStockRound round) {
        boolean openingRevealed = round.getOpenPrice() != null;
        boolean closingRevealed = round.getClearedAt() != null;
        boolean openingEvent = OPENING.equals(round.getEventStage());
        boolean eventRevealed = openingEvent ? openingRevealed : closingRevealed;
        Integer stageEffective = !eventRevealed ? null
                : openingEvent ? round.getOpeningEventEffective() : round.getClosingEventEffective();
        BigDecimal stageEventChange = !eventRevealed ? null
                : openingEvent ? round.getOpeningEventChangePercent() : round.getClosingEventChangePercent();
        String stageDirection = openingEvent ? round.getOpeningEventDirection() : round.getClosingEventDirection();
        return new EntertainmentDtos.RoundResponse(round.getRoundNo(), round.getEventStage(), round.getEventName(),
                round.getEventDescription(), eventRevealed ? stageDirection : null,
                round.getEventType(), round.getEventRelevance(), round.getEventCredibility(),
                round.getEventSurprise(), round.getEventStrength(),
                stageEffective == null ? null : Integer.valueOf(1).equals(stageEffective), round.getOpenPrice(),
                round.getClosePrice(), round.getPreviousClosePrice(), round.getExecutionPrice(),
                round.getBaseChangePercent(), openingRevealed ? round.getOpeningBaseChangePercent() : null,
                stageEffective == null ? null : stageEventChange,
                round.getOpeningEventOccurred() == null ? null : Integer.valueOf(1).equals(round.getOpeningEventOccurred()),
                openingRevealed ? round.getOpeningEventDirection() : null,
                openingRevealed && round.getOpeningEventEffective() != null
                        ? Integer.valueOf(1).equals(round.getOpeningEventEffective()) : null,
                openingRevealed ? round.getOpeningEventChangePercent() : null,
                openingRevealed ? round.getOpeningEventTier() : null,
                openingRevealed ? round.getOpeningEventRegime() : null,
                openingRevealed ? round.getOpeningEventRawChangePercent() : null,
                openingRevealed ? round.getOpeningEventAdaptationPercent() : null,
                openingRevealed ? round.getOpeningEventReason() : null,
                round.getClosingEventOccurred() == null ? null : Integer.valueOf(1).equals(round.getClosingEventOccurred()),
                closingRevealed ? round.getClosingEventDirection() : null,
                closingRevealed && round.getClosingEventEffective() != null
                        ? Integer.valueOf(1).equals(round.getClosingEventEffective()) : null,
                closingRevealed ? round.getClosingEventChangePercent() : null,
                closingRevealed ? round.getClosingEventTier() : null,
                closingRevealed ? round.getClosingEventRegime() : null,
                closingRevealed ? round.getClosingEventRawChangePercent() : null,
                closingRevealed ? round.getClosingEventAdaptationPercent() : null,
                closingRevealed ? round.getClosingEventReason() : null,
                closingRevealed ? round.getMeanReversionChangePercent() : null,
                round.getOrderFlowChangePercent(), round.getOpeningOrderFlowChangePercent(),
                round.getOpeningBuyQuantity(), round.getClosingSellQuantity(), round.getForcedSellQuantity(),
                round.getNetOrderAmount(), closingRevealed ? round.getTrendState() : null,
                closingRevealed ? round.getVolatilityState() : null,
                closingRevealed ? round.getTrendScore() : null,
                closingRevealed ? round.getTrendAge() : null,
                closingRevealed ? round.getRegimeAge() : null,
                closingRevealed ? round.getConsecutiveDirectionRounds() : null,
                closingRevealed ? round.getCurrentVolatilityPercent() : null,
                closingRevealed ? round.getMomentumChangePercent() : null,
                closingRevealed ? round.getScheduledEventChangePercent() : null,
                closingRevealed ? round.getMediumMeanReversionPercent() : null,
                closingRevealed ? round.getNoiseChangePercent() : null,
                closingRevealed ? round.getRawTotalChangePercent() : null,
                closingRevealed ? round.getClampAdjustmentPercent() : null,
                closingRevealed ? round.getFundamentalChangePercent() : null,
                closingRevealed ? round.getReportSurprisePercent() : null,
                closingRevealed && Integer.valueOf(1).equals(round.getFinancialReportPublished()),
                closingRevealed && Integer.valueOf(1).equals(round.getFinancialReportPublished()) ? round.getDemandIndex() : null,
                closingRevealed && Integer.valueOf(1).equals(round.getFinancialReportPublished()) ? round.getOrderIndex() : null,
                closingRevealed && Integer.valueOf(1).equals(round.getFinancialReportPublished()) ? round.getInventoryIndex() : null,
                closingRevealed && Integer.valueOf(1).equals(round.getFinancialReportPublished()) ? round.getMarginIndex() : null,
                closingRevealed && Integer.valueOf(1).equals(round.getFinancialReportPublished()) ? round.getCashFlowIndex() : null,
                closingRevealed && Integer.valueOf(1).equals(round.getFinancialReportPublished()) ? round.getFundamentalScore() : null,
                closingRevealed && Integer.valueOf(1).equals(round.getFinancialReportPublished()) ? round.getFairValueIndex() : null,
                closingRevealed && Integer.valueOf(1).equals(round.getFinancialReportPublished()) ? round.getFinancialRisk() : null,
                closingRevealed ? round.getTotalChangePercent() : null,
                round.getPricingEngineVersion(), round.getClearedAt());
    }

    private static EntertainmentDtos.OrderResponse orderResponse(GuessStockOrder order) {
        return new EntertainmentDtos.OrderResponse(order.getId(), order.getParticipantId(), order.getRoundNo(), order.getStage(), order.getSide(),
                order.getQuantity(), order.getTargetExposurePercent(), order.getStatus(), order.getReservedAmount(),
                Integer.valueOf(1).equals(order.getAllIn()), order.getSubmittedAt());
    }

    private static EntertainmentDtos.TradeResponse tradeResponse(GuessStockTrade trade, Map<Long, String> names) {
        return new EntertainmentDtos.TradeResponse(trade.getId(), trade.getParticipantId(), names.get(trade.getParticipantId()),
                trade.getRoundNo(), trade.getSide(), trade.getQuantity(), trade.getExecutionPrice(), trade.getAmount(),
                trade.getFeeAmount(), trade.getSlippagePercent(), trade.getTradedAt());
    }

    private static EntertainmentDtos.LeaderboardEntryResponse leaderboardEntry(long rank, GameLeaderboardEntry entry, boolean mine) {
        BigDecimal initialCash = entry.getFinalAsset().subtract(entry.getProfitAmount());
        BigDecimal returnPercent = initialCash.signum() == 0 ? ZERO : returnPercent(entry.getProfitAmount(), initialCash);
        return new EntertainmentDtos.LeaderboardEntryResponse(rank, entry.getSessionId(), entry.getPlayerType(), entry.getDisplayName(),
                returnPercent, entry.getProfitAmount(), entry.getFinalAsset(), entry.getCompletedAt(), mine);
    }

    private String write(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (JsonProcessingException exception) { throw new BusinessException(ErrorCode.SERVER_ERROR, "游戏数据无法保存"); }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readMap(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try { return objectMapper.readValue(json, Map.class); }
        catch (JsonProcessingException exception) { return Map.of(); }
    }

    private static BigDecimal decimal(Object value) {
        if (value == null) return null;
        return new BigDecimal(value.toString());
    }
    private static Integer integer(Object value) { return value == null ? null : Integer.valueOf(value.toString()); }
    private static boolean isTradingPhase(String phase) {
        return "OPENING_TRADING".equals(phase) || "CLOSING_TRADING".equals(phase);
    }
    private static boolean isCreditPhase(String phase) {
        return "CREDIT_TRADING".equals(phase) || "CREDIT_REPAYMENT".equals(phase);
    }
    private static boolean isRoundStartPhase(String phase) {
        return "ROUND_START".equals(phase);
    }
    private static boolean isEventPhase(String phase) {
        return "OPENING_EVENT_ACK".equals(phase) || "CLOSING_EVENT_ACK".equals(phase);
    }
    private static boolean isResultPhase(String phase) {
        return "OPENING_RESULT_ACK".equals(phase) || "ROUND_RESULT_ACK".equals(phase);
    }
    private static String stageForPhase(String phase) {
        return phase != null && phase.startsWith("CLOSING") ? CLOSING : OPENING;
    }
    private static BigDecimal referencePrice(GuessStockRound round, String stage) {
        return CLOSING.equals(stage) && round.getOpenPrice() != null
                ? round.getOpenPrice() : round.getPreviousClosePrice();
    }
    private static BigDecimal latestMarkPrice(GuessStockRound round) {
        if (round.getClosePrice() != null) return round.getClosePrice();
        if (round.getOpenPrice() != null) return round.getOpenPrice();
        return round.getPreviousClosePrice();
    }
    private void initializeFundamentals(Long sessionId, GuessStockRound round, GuessStockConfig config) {
        GuessStockRound previous = roundMapper.selectOne(new LambdaQueryWrapper<GuessStockRound>()
                .eq(GuessStockRound::getSessionId, sessionId).lt(GuessStockRound::getRoundNo, round.getRoundNo())
                .orderByDesc(GuessStockRound::getRoundNo).last("LIMIT 1"));
        GuessStockConfig.FundamentalConfig initial = config.initialFundamentals();
        FundamentalEngine.State state = previous != null && previous.getDemandIndex() != null
                ? fundamentalState(previous)
                : initial == null ? FundamentalEngine.neutral()
                : new FundamentalEngine.State(initial.demandIndex().doubleValue(), initial.orderIndex().doubleValue(),
                    initial.inventoryIndex().doubleValue(), initial.marginIndex().doubleValue(),
                    initial.cashFlowIndex().doubleValue());
        setFundamentals(round, state);
    }
    private static FundamentalEngine.State fundamentalState(GuessStockRound round) {
        return new FundamentalEngine.State(decimal(round.getDemandIndex(), 100), decimal(round.getOrderIndex(), 100),
                decimal(round.getInventoryIndex(), 100), decimal(round.getMarginIndex(), 100),
                decimal(round.getCashFlowIndex(), 100));
    }
    private static void setFundamentals(GuessStockRound round, FundamentalEngine.State state) {
        round.setDemandIndex(GuessStockRules.rate(BigDecimal.valueOf(state.demandIndex())));
        round.setOrderIndex(GuessStockRules.rate(BigDecimal.valueOf(state.orderIndex())));
        round.setInventoryIndex(GuessStockRules.rate(BigDecimal.valueOf(state.inventoryIndex())));
        round.setMarginIndex(GuessStockRules.rate(BigDecimal.valueOf(state.marginIndex())));
        round.setCashFlowIndex(GuessStockRules.rate(BigDecimal.valueOf(state.cashFlowIndex())));
        FundamentalEngine.Metrics metrics = FundamentalEngine.metrics(state);
        round.setFundamentalScore(metrics.fundamentalScore());
        round.setFairValueIndex(metrics.fairValueIndex());
        round.setFinancialRisk(metrics.financialRisk());
    }
    private static double decimal(BigDecimal value, double fallback) {
        return value == null ? fallback : value.doubleValue();
    }
    private static boolean usesModernPricing(GuessStockConfig config) {
        return "V2".equals(config.pricingEngineVersion()) || "V3".equals(config.pricingEngineVersion());
    }
    private static List<BigDecimal> visibleHistory(AiMarketSnapshot snapshot, String difficulty) {
        int limit = AiDifficultyPolicy.forName(difficulty).publicHistoryRounds();
        List<BigDecimal> values = snapshot.recentPrices();
        return List.copyOf(values.subList(Math.max(0, values.size() - limit), values.size()));
    }
    private static BigDecimal boundedNormalPercent(SplittableRandom random, BigDecimal maximumAbsolute) {
        if (maximumAbsolute == null || maximumAbsolute.signum() == 0) return ZERO;
        double u1 = Math.max(1.0e-12, random.nextDouble());
        double gaussian = Math.sqrt(-2.0 * Math.log(u1)) * Math.cos(2.0 * Math.PI * random.nextDouble());
        double limit = maximumAbsolute.doubleValue();
        return GuessStockRules.rate(BigDecimal.valueOf(Math.max(-limit, Math.min(limit, gaussian * limit / 2.0))));
    }
    private void assignSessionSeed(GameSession session, GameVersion version) {
        session.setScenarioSeed(mixedSessionSeed(version.getScenarioSeed(), session.getId()));
        sessionMapper.updateById(session);
    }
    private static long sessionSeed(GameSession session, GameVersion version) {
        return session.getScenarioSeed() == null
                ? mix(version.getScenarioSeed(), (int) (session.getId() ^ (session.getId() >>> 32)), 4241)
                : session.getScenarioSeed();
    }
    private static long mixedSessionSeed(long versionSeed, long sessionId) {
        long value = versionSeed ^ Long.rotateLeft(sessionId * 0x9E3779B97F4A7C15L, 23)
                ^ SESSION_RANDOM.nextLong() ^ System.nanoTime();
        value = (value ^ (value >>> 30)) * 0xbf58476d1ce4e5b9L;
        value = (value ^ (value >>> 27)) * 0x94d049bb133111ebL;
        return value ^ (value >>> 31);
    }
    private void beginOrderMutation(Long sessionId) {
        inFlightOrders.computeIfAbsent(sessionId, ignored -> new AtomicInteger()).incrementAndGet();
    }
    private void endOrderMutation(Long sessionId) {
        AtomicInteger count = inFlightOrders.get(sessionId);
        if (count != null && count.decrementAndGet() <= 0) inFlightOrders.remove(sessionId, count);
    }
    private int orderMutationsInFlight(Long sessionId) {
        AtomicInteger count = inFlightOrders.get(sessionId);
        return count == null ? 0 : Math.max(0, count.get());
    }
    private static long mix(long seed, int round, int salt) {
        long value = seed ^ (0x9E3779B97F4A7C15L * round) ^ (0xC2B2AE3D27D4EB4FL * salt);
        value ^= value >>> 33; value *= 0xff51afd7ed558ccdL; value ^= value >>> 33;
        return value;
    }
    private static String aiName(String style, int index) {
        String label = switch (style) {
            case "CONSERVATIVE" -> "稳健"; case "TREND" -> "趋势"; case "CONTRARIAN" -> "逆势";
            case "AGGRESSIVE" -> "激进"; default -> "随机";
        };
        return label + " AI " + (index + 1);
    }
    private static void bad(String message) { throw new BusinessException(ErrorCode.BAD_REQUEST, message); }
}
