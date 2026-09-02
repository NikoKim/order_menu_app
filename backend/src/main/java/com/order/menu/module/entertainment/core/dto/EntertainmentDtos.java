package com.order.menu.module.entertainment.core.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.order.menu.module.entertainment.mode.guessstock.config.GuessStockConfig;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public final class EntertainmentDtos {
    private EntertainmentDtos() {}

    public record ModeResponse(String code, String name, int configSchemaVersion, boolean enabled) {}

    public record GameUpsertRequest(
            String modeCode,
            @NotBlank @Size(min = 2, max = 50) String title,
            @Size(max = 500) String description,
            @Size(max = 500) String coverUrl,
            String visibility,
            @Size(max = 40) String versionLabel,
            Boolean playKeyRequired,
            @NotNull JsonNode config
    ) {}

    public record CreatorResponse(Long id, String nickname, String avatarUrl) {}

    public record GameSummaryResponse(
            Long id, String modeCode, String title, String description, String coverUrl,
            String visibility, String status, CreatorResponse creator, String stockCode,
            String stockType, boolean aiEnabled, int aiCount, int roundCount,
            int currentVersionNo, long playCount, long completedCount, LocalDateTime publishedAt,
            boolean owner, Map<String, Object> modeData, String versionLabel, boolean playKeyRequired
    ) {}

    public record GameDetailResponse(GameSummaryResponse game, JsonNode config,
                                     boolean hasResumableSession, Long resumableSessionId,
                                     Long activeRoomId, String activeRoomStatus) {}

    public record PlayKeyCreateRequest(@NotBlank @Size(min = 4, max = 80) String keyValue,
                                       String playScope, String usageMode, Integer maxUses) {}
    public record PlayKeyStatusRequest(@NotBlank String status) {}
    public record PlayKeyResponse(Long id, String keyHint, String playScope, String usageMode,
                                  Integer maxUses, int usedCount, String status,
                                  LocalDateTime lastUsedAt, LocalDateTime createdAt) {}

    public record GuestResponse(String token, String displayName) {}

    public record ActionRequest(
            @NotBlank @Size(max = 80) String clientRequestId,
            @NotBlank String actionType,
            String side,
            Long quantity,
            Boolean allIn,
            BigDecimal targetExposurePercent,
            BigDecimal playbackSpeed,
            Boolean autoAdvance
    ) {}

    public record CreditActionRequest(
            @NotBlank @Size(max = 80) String requestId,
            String productCode,
            @NotNull BigDecimal amount,
            Integer maturityRounds
    ) {}

    public record ResearchRequest(@NotBlank @Size(max = 80) String requestId,
                                  @NotBlank String actionType) {}
    public record ResearchResponse(String actionType, String direction, double rangeLow,
                                   double rangeHigh, int remainingPoints) {}

    public record LoanExtensionRequest(@NotBlank @Size(max = 80) String requestId,
                                       @NotNull Long contractId, @NotNull Integer extensionRounds) {}

    public record LoanLedgerResponse(Long id, int roundNo, String type,
                                     BigDecimal principalDelta, BigDecimal interestDelta,
                                     BigDecimal cashDelta, String reason, LocalDateTime createdAt) {}

    public record LoanContractResponse(Long id, BigDecimal principalOriginal, BigDecimal principalOutstanding,
                                       BigDecimal accruedInterest, BigDecimal interestRate, int openedRound,
                                       int originalMaturityRound, int maturityRound, int remainingRounds,
                                       String status, int extensionCount, int totalExtendedRounds,
                                       boolean canExtend) {}

    public record CreditBankResponse(
            String productCode, String productName, String riskLevel, String facilityStatus,
            BigDecimal principal, BigDecimal accruedInterest, BigDecimal effectiveDebt,
            BigDecimal availableCredit, int nearestMaturityRound,
            BigDecimal warningLtv, BigDecimal blockLtv, BigDecimal liquidationLtv, BigDecimal recoveryLtv,
            List<LoanLedgerResponse> recentLedger, List<LoanContractResponse> contracts
    ) {}

    public record CreditStatusResponse(
            boolean enabled, String productCode, String productName, String riskLevel, String facilityStatus,
            BigDecimal principal, BigDecimal accruedInterest, BigDecimal effectiveDebt,
            BigDecimal availableCredit, Map<String, BigDecimal> productAvailableCredit, int nearestMaturityRound,
            BigDecimal stockValue, BigDecimal netAsset, BigDecimal collateralValue,
            BigDecimal exposurePercent, BigDecimal ltvPercent, String riskState,
            BigDecimal warningLtv, BigDecimal blockLtv, BigDecimal liquidationLtv, BigDecimal recoveryLtv,
            BigDecimal totalInterestCharged, long totalForcedLiquidationShares,
            List<LoanLedgerResponse> recentLedger, List<LoanContractResponse> contracts,
            List<CreditBankResponse> banks
    ) {}

    public record AiCredentialRequest(
            @NotBlank @Size(max = 500) String baseUrl,
            @NotBlank @Size(max = 120) String model,
            @NotBlank @Size(max = 500) String apiKey
    ) {}

    public record StartSessionRequest(Boolean useAi, Boolean useDefaultAi, @Valid AiCredentialRequest aiCredential,
                                      BigDecimal playbackSpeed, Boolean autoAdvance, String playKey) {}

    public record AiCredentialSelectionRequest(Boolean useDefault, @Valid AiCredentialRequest aiCredential) {}

    public record AiDefaultResponse(boolean configured, String baseUrl, String model,
                                    LocalDateTime updatedAt) {}

    public record AiGameCredentialResponse(boolean configured, String baseUrl, String model,
                                            LocalDateTime updatedAt) {}

    public record StartRoomRequest(Boolean useAi, Boolean useDefaultAi) {}
    public record CreateRoomRequest(String playKey) {}

    public record RoomMemberResponse(Long id, String displayName, String playerType, String role,
                                     String status, boolean currentPlayer, boolean credentialReady,
                                     boolean eventAcknowledged, boolean roundReady, boolean restartReady,
                                     String eventAckStage, String readyStage,
                                     LocalDateTime lastSeenAt) {}

    public record RoomResponse(Long id, String inviteCode, String status, int maxPlayers,
                               Long gameId, Long gameVersionId, Long sessionId, boolean host,
                               boolean aiEnabled, boolean hostedAiConfigured, List<RoomMemberResponse> members,
                               GameSummaryResponse game) {}

    public record JoinRoomRequest(@NotBlank @Size(min = 6, max = 6) String inviteCode, String playKey) {}

    public record AiDecisionResponse(Long participantId, String participantName, int roundNo,
                                     String stage, String status, String reason, String errorMessage,
                                     String marketRegime, String action, Double targetPositionPercent,
                                     Double confidence, String timeHorizon, List<String> reasonCodes,
                                     List<?> invalidConditions, String decisionType, String direction,
                                     String urgency, Boolean materialChange, List<?> newEvidence,
                                     List<?> triggeredConditions, Long executedShares, String executionReason) {}

    public record AiDecisionHighlightResponse(int roundNo, String stage, String direction, long shares,
                                              BigDecimal outcomePercent, String reason) {}

    public record AiReviewResponse(Long participantId, String participantName, String aiProfile,
                                   BigDecimal returnPercent, Integer finalRank,
                                   AiDecisionHighlightResponse bestDecision,
                                   AiDecisionHighlightResponse biggestMistake,
                                   int thesisReplacementCount, int reverseTradeCount,
                                   int modelCallCount, int fallbackCount,
                                   BigDecimal totalInterestCost, long forcedLiquidationShares) {}

    public record ParticipantResponse(
            Long id, String type, String displayName, String aiProfile,
            BigDecimal initialCash, BigDecimal cash, BigDecimal reservedCash, long quantity, long reservedQuantity,
            BigDecimal averageCost, BigDecimal asset, BigDecimal returnPercent, Integer finalRank, boolean currentPlayer,
            BigDecimal debt, BigDecimal exposurePercent, BigDecimal ltvPercent, String creditRiskState
    ) {}

    public record RoundResponse(
            int roundNo, String eventStage, String eventName, String eventDescription, String eventDirection,
            String eventType, String eventRelevance, String eventCredibility, String eventSurprise, String eventStrength,
            Boolean eventEffective, BigDecimal openPrice, BigDecimal closePrice,
            BigDecimal previousClosePrice, BigDecimal executionPrice, BigDecimal baseChangePercent,
            BigDecimal openingBaseChangePercent, BigDecimal eventChangePercent,
            Boolean openingEventOccurred, String openingEventDirection,
            Boolean openingEventEffective, BigDecimal openingEventChangePercent, String openingEventTier,
            String openingEventRegime, BigDecimal openingEventRawChangePercent,
            BigDecimal openingEventAdaptationPercent, String openingEventReason,
            Boolean closingEventOccurred, String closingEventDirection,
            Boolean closingEventEffective, BigDecimal closingEventChangePercent, String closingEventTier,
            String closingEventRegime, BigDecimal closingEventRawChangePercent,
            BigDecimal closingEventAdaptationPercent, String closingEventReason,
            BigDecimal meanReversionChangePercent, BigDecimal orderFlowChangePercent,
            BigDecimal openingOrderFlowChangePercent, Long openingBuyQuantity,
            Long closingSellQuantity, Long forcedSellQuantity, BigDecimal netOrderAmount,
            String trendState, String volatilityState, BigDecimal trendScore, Integer trendAge, Integer regimeAge,
            Integer consecutiveDirectionRounds, BigDecimal currentVolatilityPercent,
            BigDecimal momentumChangePercent, BigDecimal scheduledEventChangePercent,
            BigDecimal mediumMeanReversionPercent, BigDecimal noiseChangePercent,
            BigDecimal rawTotalChangePercent, BigDecimal clampAdjustmentPercent,
            BigDecimal fundamentalChangePercent, BigDecimal reportSurprisePercent,
            Boolean financialReportPublished, BigDecimal demandIndex, BigDecimal orderIndex,
            BigDecimal inventoryIndex, BigDecimal marginIndex, BigDecimal cashFlowIndex,
            BigDecimal fundamentalScore, BigDecimal fairValueIndex, BigDecimal financialRisk,
            BigDecimal totalChangePercent, String pricingEngineVersion, LocalDateTime clearedAt
    ) {}

    public record OrderResponse(Long id, Long participantId, int roundNo, String stage, String side,
                                long quantity, BigDecimal targetExposurePercent, String status,
                                BigDecimal reservedAmount, boolean allIn,
                                LocalDateTime submittedAt) {}

    public record TradeResponse(Long id, Long participantId, String participantName, int roundNo,
                                String side, long quantity, BigDecimal executionPrice,
                                BigDecimal amount, BigDecimal feeAmount, BigDecimal slippagePercent,
                                LocalDateTime tradedAt) {}

    public record SessionResponse(
            Long id, Long gameId, Long gameVersionId, int gameVersionNo,
            String status, int currentRound, String phase, LocalDateTime startedAt, LocalDateTime serverTime,
            LocalDateTime phaseEndsAt, Long phaseRemainingMs, BigDecimal playbackSpeed, boolean autoAdvance,
            GuessStockConfig config,
            GameSummaryResponse game, RoundResponse currentRoundState,
            List<RoundResponse> rounds, List<ParticipantResponse> participants,
            OrderResponse currentOrder, List<OrderResponse> marketOrders, List<TradeResponse> recentTrades,
            List<TradeResponse> playerTrades,
            BigDecimal finalAsset, BigDecimal profitAmount, BigDecimal returnPercent,
            Integer inSessionRank, Long leaderboardRank, RoomResponse room,
            List<AiDecisionResponse> aiDecisions, CreditStatusResponse credit,
            List<AiReviewResponse> aiReviews
    ) {}

    public record LeaderboardEntryResponse(long rank, Long sessionId, String playerType,
                                           String displayName, BigDecimal returnPercent,
                                           BigDecimal profitAmount, BigDecimal finalAsset,
                                           LocalDateTime completedAt, boolean currentPlayer) {}

    public record LeaderboardResponse(Long gameId, Long gameVersionId, int versionNo, String versionLabel,
                                      List<LeaderboardEntryResponse> entries,
                                      LeaderboardEntryResponse currentPlayer) {}
}
