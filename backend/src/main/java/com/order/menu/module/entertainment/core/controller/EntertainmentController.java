package com.order.menu.module.entertainment.core.controller;

import com.order.menu.common.response.ApiResponse;
import com.order.menu.common.response.PageResponse;
import com.order.menu.module.entertainment.core.dto.EntertainmentDtos;
import com.order.menu.module.entertainment.core.dto.AiConnectionDtos;
import com.order.menu.module.entertainment.core.service.GameAiConnectionService;
import com.order.menu.module.entertainment.core.dto.GameReviewDtos;
import com.order.menu.module.entertainment.core.service.GameReviewService;
import com.order.menu.module.entertainment.core.service.EntertainmentGameService;
import com.order.menu.module.entertainment.core.service.EntertainmentGuestService;
import com.order.menu.module.entertainment.core.service.GameRoomService;
import com.order.menu.module.entertainment.core.service.GameAiCredentialService;
import com.order.menu.module.entertainment.core.service.GamePlayKeyService;
import com.order.menu.module.entertainment.core.service.PlayerIdentity;
import com.order.menu.module.entertainment.mode.guessstock.service.GuessStockSessionService;
import com.order.menu.module.entertainment.mode.guessstock.challenge.DailyChallengeService;
import com.order.menu.security.LoginUser;
import com.order.menu.security.UserContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/entertainment")
@RequiredArgsConstructor
public class EntertainmentController {
    private final EntertainmentGameService gameService;
    private final EntertainmentGuestService guestService;
    private final GuessStockSessionService sessionService;
    private final GameRoomService roomService;
    private final GameAiCredentialService credentialService;
    private final GameAiConnectionService connectionService;
    private final GamePlayKeyService playKeyService;
    private final DailyChallengeService dailyChallengeService;
    private final GameReviewService reviewService;

    @GetMapping("/games/{gameId}/reviews")
    public ApiResponse<GameReviewDtos.ReviewPageResponse> reviews(
            @PathVariable Long gameId, @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        LoginUser viewer = UserContext.getOrNull();
        return ApiResponse.success(reviewService.list(gameId, viewer == null ? null : viewer.userId(), page, pageSize));
    }

    @PutMapping("/games/{gameId}/reviews/me")
    public ApiResponse<GameReviewDtos.ReviewResponse> saveReview(
            @PathVariable Long gameId, @Valid @RequestBody GameReviewDtos.SaveRequest request) {
        return ApiResponse.success(reviewService.save(gameId, UserContext.get().userId(), request));
    }

    @DeleteMapping("/games/{gameId}/reviews/me")
    public ApiResponse<Void> deleteReview(@PathVariable Long gameId) {
        reviewService.deleteMine(gameId, customer().userId());
        return ApiResponse.success();
    }

    @GetMapping("/modes")
    public ApiResponse<List<EntertainmentDtos.ModeResponse>> modes() {
        return ApiResponse.success(gameService.modes());
    }

    @GetMapping("/games")
    public ApiResponse<PageResponse<EntertainmentDtos.GameSummaryResponse>> games(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "12") int pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String stockType,
            @RequestParam(defaultValue = "LATEST") String sort,
            @RequestHeader(value = "X-Guest-Token", required = false) String guestToken) {
        return ApiResponse.success(gameService.list(page, pageSize, keyword, stockType, sort,
                guestService.resolve(UserContext.getOrNull(), guestToken, false)));
    }

    @GetMapping("/games/{gameId}")
    public ApiResponse<EntertainmentDtos.GameDetailResponse> game(
            @PathVariable Long gameId,
            @RequestHeader(value = "X-Guest-Token", required = false) String guestToken) {
        return ApiResponse.success(gameService.detail(gameId,
                guestService.resolve(UserContext.getOrNull(), guestToken, false)));
    }

    @PostMapping("/guests")
    public ApiResponse<EntertainmentDtos.GuestResponse> createGuest() {
        return ApiResponse.success(guestService.create());
    }

    @GetMapping("/my/games")
    public ApiResponse<PageResponse<EntertainmentDtos.GameSummaryResponse>> myGames(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return ApiResponse.success(gameService.mine(customer().userId(), page, pageSize));
    }

    @PostMapping("/games")
    public ApiResponse<EntertainmentDtos.GameDetailResponse> createGame(
            @Valid @RequestBody EntertainmentDtos.GameUpsertRequest request) {
        return ApiResponse.success(gameService.create(customer().userId(), request));
    }

    @PutMapping("/games/{gameId}")
    public ApiResponse<EntertainmentDtos.GameDetailResponse> updateGame(
            @PathVariable Long gameId, @Valid @RequestBody EntertainmentDtos.GameUpsertRequest request) {
        return ApiResponse.success(gameService.update(customer().userId(), gameId, request));
    }

    @PostMapping("/games/{gameId}/publish")
    public ApiResponse<EntertainmentDtos.GameDetailResponse> publishGame(@PathVariable Long gameId) {
        return ApiResponse.success(gameService.publish(customer().userId(), gameId));
    }

    @PutMapping("/games/{gameId}/status")
    public ApiResponse<Void> changeStatus(@PathVariable Long gameId, @Valid @RequestBody StatusRequest request) {
        gameService.changeStatus(customer().userId(), gameId, request.status());
        return ApiResponse.success();
    }

    @DeleteMapping("/games/{gameId}")
    public ApiResponse<Void> deleteGame(@PathVariable Long gameId) {
        gameService.delete(customer().userId(), gameId);
        return ApiResponse.success();
    }

    @PostMapping("/games/{gameId}/sessions")
    public ApiResponse<EntertainmentDtos.SessionResponse> start(
            @PathVariable Long gameId,
            @Valid @RequestBody(required = false) EntertainmentDtos.StartSessionRequest request,
            @RequestHeader(value = "X-Guest-Token", required = false) String guestToken) {
        return ApiResponse.success(sessionService.start(gameId, player(guestToken), request));
    }

    @GetMapping("/games/{gameId}/daily-challenge")
    public ApiResponse<DailyChallengeService.ChallengeResponse> dailyChallenge(@PathVariable Long gameId) {
        return ApiResponse.success(dailyChallengeService.daily(gameId));
    }

    @PostMapping("/challenges/{challengeId}/sessions")
    public ApiResponse<EntertainmentDtos.SessionResponse> startDailyChallenge(
            @PathVariable Long challengeId,
            @Valid @RequestBody(required = false) EntertainmentDtos.StartSessionRequest request,
            @RequestHeader(value = "X-Guest-Token", required = false) String guestToken) {
        return ApiResponse.success(dailyChallengeService.start(challengeId, player(guestToken), request));
    }

    @GetMapping("/challenges/{challengeId}")
    public ApiResponse<DailyChallengeService.ChallengeResponse> dailyChallengeById(@PathVariable Long challengeId) {
        return ApiResponse.success(dailyChallengeService.get(challengeId));
    }

    @PostMapping("/challenges/{challengeId}/entries/{sessionId}")
    public ApiResponse<DailyChallengeService.EntryResponse> submitDailyChallenge(
            @PathVariable Long challengeId, @PathVariable Long sessionId,
            @RequestHeader(value = "X-Guest-Token", required = false) String guestToken) {
        return ApiResponse.success(dailyChallengeService.submit(challengeId, sessionId, player(guestToken)));
    }

    @GetMapping("/challenges/{challengeId}/leaderboard")
    public ApiResponse<List<DailyChallengeService.EntryResponse>> dailyChallengeLeaderboard(
            @PathVariable Long challengeId,
            @RequestHeader(value = "X-Guest-Token", required = false) String guestToken) {
        return ApiResponse.success(dailyChallengeService.leaderboard(challengeId, player(guestToken)));
    }

    @PostMapping("/games/{gameId}/rooms")
    public ApiResponse<EntertainmentDtos.RoomResponse> createRoom(
            @PathVariable Long gameId,
            @RequestBody(required = false) EntertainmentDtos.CreateRoomRequest request,
            @RequestHeader(value = "X-Guest-Token", required = false) String guestToken) {
        return ApiResponse.success(roomService.create(gameId, player(guestToken), request));
    }

    @GetMapping("/games/{gameId}/play-keys")
    public ApiResponse<List<EntertainmentDtos.PlayKeyResponse>> playKeys(@PathVariable Long gameId) {
        LoginUser user = customer(); gameService.requireOwned(user.userId(), gameId);
        return ApiResponse.success(playKeyService.list(gameId));
    }

    @PostMapping("/games/{gameId}/play-keys")
    public ApiResponse<EntertainmentDtos.PlayKeyResponse> createPlayKey(
            @PathVariable Long gameId, @Valid @RequestBody EntertainmentDtos.PlayKeyCreateRequest request) {
        LoginUser user = customer(); gameService.requireOwned(user.userId(), gameId);
        return ApiResponse.success(playKeyService.create(gameId, request));
    }

    @PutMapping("/games/{gameId}/play-keys/{keyId}/status")
    public ApiResponse<EntertainmentDtos.PlayKeyResponse> setPlayKeyStatus(
            @PathVariable Long gameId, @PathVariable Long keyId,
            @Valid @RequestBody EntertainmentDtos.PlayKeyStatusRequest request) {
        LoginUser user = customer(); gameService.requireOwned(user.userId(), gameId);
        return ApiResponse.success(playKeyService.setStatus(gameId, keyId, request.status()));
    }

    @DeleteMapping("/games/{gameId}/play-keys/{keyId}")
    public ApiResponse<Void> deletePlayKey(@PathVariable Long gameId, @PathVariable Long keyId) {
        LoginUser user = customer(); gameService.requireOwned(user.userId(), gameId);
        playKeyService.delete(gameId, keyId); return ApiResponse.success();
    }

    @PostMapping("/rooms/join")
    public ApiResponse<EntertainmentDtos.RoomResponse> joinRoom(
            @Valid @RequestBody EntertainmentDtos.JoinRoomRequest request,
            @RequestHeader(value = "X-Guest-Token", required = false) String guestToken) {
        return ApiResponse.success(roomService.join(request.inviteCode(), request.playKey(), player(guestToken)));
    }

    @GetMapping("/rooms/{roomId}")
    public ApiResponse<EntertainmentDtos.RoomResponse> room(
            @PathVariable Long roomId,
            @RequestHeader(value = "X-Guest-Token", required = false) String guestToken) {
        return ApiResponse.success(roomService.get(roomId, player(guestToken)));
    }

    @PostMapping("/rooms/{roomId}/ai-credential")
    public ApiResponse<EntertainmentDtos.RoomResponse> configureRoomAi(
            @PathVariable Long roomId, @Valid @RequestBody EntertainmentDtos.AiCredentialSelectionRequest request,
            @RequestHeader(value = "X-Guest-Token", required = false) String guestToken) {
        return ApiResponse.success(roomService.configureAi(roomId, player(guestToken), request));
    }

    @PostMapping("/rooms/{roomId}/start")
    public ApiResponse<EntertainmentDtos.SessionResponse> startRoom(
            @PathVariable Long roomId,
            @RequestBody(required = false) EntertainmentDtos.StartRoomRequest request,
            @RequestHeader(value = "X-Guest-Token", required = false) String guestToken) {
        return ApiResponse.success(roomService.start(roomId, player(guestToken), request));
    }

    @GetMapping("/ai-default")
    public ApiResponse<EntertainmentDtos.AiDefaultResponse> aiDefault(
            @RequestHeader(value = "X-Guest-Token", required = false) String guestToken) {
        return ApiResponse.success(credentialService.defaultCredential(player(guestToken)));
    }

    @PostMapping("/ai-connection/test")
    public ApiResponse<AiConnectionDtos.Result> testAiConnection(
            @Valid @RequestBody AiConnectionDtos.Request request,
            @RequestHeader(value = "X-Guest-Token", required = false) String guestToken) {
        return ApiResponse.success(connectionService.test(player(guestToken), request));
    }

    @PutMapping("/ai-default")
    public ApiResponse<EntertainmentDtos.AiDefaultResponse> saveAiDefault(
            @Valid @RequestBody EntertainmentDtos.AiCredentialRequest request,
            @RequestHeader(value = "X-Guest-Token", required = false) String guestToken) {
        return ApiResponse.success(credentialService.saveDefault(player(guestToken), request));
    }

    @DeleteMapping("/ai-default")
    public ApiResponse<Void> deleteAiDefault(
            @RequestHeader(value = "X-Guest-Token", required = false) String guestToken) {
        credentialService.deleteDefault(player(guestToken));
        return ApiResponse.success();
    }

    @GetMapping("/games/{gameId}/ai-credential")
    public ApiResponse<EntertainmentDtos.AiGameCredentialResponse> gameAiCredential(@PathVariable Long gameId) {
        gameService.requirePlayable(gameId);
        return ApiResponse.success(credentialService.gameCredential(gameId));
    }

    @PutMapping("/games/{gameId}/ai-credential")
    public ApiResponse<EntertainmentDtos.AiGameCredentialResponse> saveGameAiCredential(
            @PathVariable Long gameId, @Valid @RequestBody EntertainmentDtos.AiCredentialRequest request) {
        LoginUser user = customer();
        gameService.requireOwned(user.userId(), gameId);
        return ApiResponse.success(credentialService.saveGameCredential(gameId,
                new PlayerIdentity("USER", user.userId(), null, "U:" + user.userId(), user.nickname()), request));
    }

    @DeleteMapping("/games/{gameId}/ai-credential")
    public ApiResponse<Void> deleteGameAiCredential(@PathVariable Long gameId) {
        LoginUser user = customer();
        gameService.requireOwned(user.userId(), gameId);
        credentialService.deleteGameCredential(gameId);
        return ApiResponse.success();
    }

    @DeleteMapping("/rooms/{roomId}/members/{memberId}")
    public ApiResponse<EntertainmentDtos.RoomResponse> removeRoomMember(
            @PathVariable Long roomId, @PathVariable Long memberId,
            @RequestHeader(value = "X-Guest-Token", required = false) String guestToken) {
        return ApiResponse.success(roomService.remove(roomId, memberId, player(guestToken)));
    }

    @PostMapping("/rooms/{roomId}/leave")
    public ApiResponse<Void> leaveRoom(
            @PathVariable Long roomId,
            @RequestHeader(value = "X-Guest-Token", required = false) String guestToken) {
        roomService.leave(roomId, player(guestToken));
        return ApiResponse.success();
    }

    @DeleteMapping("/rooms/{roomId}")
    public ApiResponse<Void> closeRoom(
            @PathVariable Long roomId,
            @RequestHeader(value = "X-Guest-Token", required = false) String guestToken) {
        roomService.close(roomId, player(guestToken));
        return ApiResponse.success();
    }

    @GetMapping("/sessions/{sessionId}")
    public ApiResponse<EntertainmentDtos.SessionResponse> session(
            @PathVariable Long sessionId,
            @RequestHeader(value = "X-Guest-Token", required = false) String guestToken) {
        return ApiResponse.success(sessionService.sync(sessionId, player(guestToken)));
    }

    @PostMapping("/sessions/{sessionId}/sync")
    public ApiResponse<EntertainmentDtos.SessionResponse> sync(
            @PathVariable Long sessionId,
            @RequestHeader(value = "X-Guest-Token", required = false) String guestToken) {
        return ApiResponse.success(sessionService.sync(sessionId, player(guestToken)));
    }

    @PostMapping("/sessions/{sessionId}/actions")
    public ApiResponse<EntertainmentDtos.SessionResponse> action(
            @PathVariable Long sessionId,
            @RequestHeader(value = "X-Guest-Token", required = false) String guestToken,
            @Valid @RequestBody EntertainmentDtos.ActionRequest request) {
        return ApiResponse.success(sessionService.act(sessionId, player(guestToken), request));
    }

    @GetMapping("/sessions/{sessionId}/credit/status")
    public ApiResponse<EntertainmentDtos.CreditStatusResponse> creditStatus(
            @PathVariable Long sessionId,
            @RequestHeader(value = "X-Guest-Token", required = false) String guestToken) {
        return ApiResponse.success(sessionService.creditStatus(sessionId, player(guestToken)));
    }

    @PostMapping("/sessions/{sessionId}/credit/borrow")
    public ApiResponse<EntertainmentDtos.SessionResponse> borrow(
            @PathVariable Long sessionId,
            @RequestHeader(value = "X-Guest-Token", required = false) String guestToken,
            @Valid @RequestBody EntertainmentDtos.CreditActionRequest request) {
        return ApiResponse.success(sessionService.borrow(sessionId, player(guestToken), request));
    }

    @PostMapping("/sessions/{sessionId}/credit/repay")
    public ApiResponse<EntertainmentDtos.SessionResponse> repay(
            @PathVariable Long sessionId,
            @RequestHeader(value = "X-Guest-Token", required = false) String guestToken,
            @Valid @RequestBody EntertainmentDtos.CreditActionRequest request) {
        return ApiResponse.success(sessionService.repay(sessionId, player(guestToken), request));
    }

    @PostMapping("/sessions/{sessionId}/research")
    public ApiResponse<EntertainmentDtos.ResearchResponse> research(
            @PathVariable Long sessionId,
            @RequestHeader(value = "X-Guest-Token", required = false) String guestToken,
            @Valid @RequestBody EntertainmentDtos.ResearchRequest request) {
        return ApiResponse.success(sessionService.research(sessionId, player(guestToken), request));
    }

    @PostMapping("/sessions/{sessionId}/credit/extend")
    public ApiResponse<EntertainmentDtos.SessionResponse> extendLoan(
            @PathVariable Long sessionId,
            @RequestHeader(value = "X-Guest-Token", required = false) String guestToken,
            @Valid @RequestBody EntertainmentDtos.LoanExtensionRequest request) {
        return ApiResponse.success(sessionService.extendLoan(sessionId, player(guestToken), request));
    }

    @PostMapping("/sessions/{sessionId}/pause")
    public ApiResponse<EntertainmentDtos.SessionResponse> pause(
            @PathVariable Long sessionId,
            @RequestHeader(value = "X-Guest-Token", required = false) String guestToken) {
        return ApiResponse.success(sessionService.pause(sessionId, player(guestToken)));
    }

    @PostMapping("/sessions/{sessionId}/resume")
    public ApiResponse<EntertainmentDtos.SessionResponse> resume(
            @PathVariable Long sessionId,
            @RequestHeader(value = "X-Guest-Token", required = false) String guestToken) {
        return ApiResponse.success(sessionService.resume(sessionId, player(guestToken)));
    }

    @DeleteMapping("/sessions/{sessionId}")
    public ApiResponse<Void> abandon(
            @PathVariable Long sessionId,
            @RequestHeader(value = "X-Guest-Token", required = false) String guestToken) {
        sessionService.abandon(sessionId, player(guestToken));
        return ApiResponse.success();
    }

    @DeleteMapping("/sessions/{sessionId}/record")
    public ApiResponse<Void> deleteRecord(
            @PathVariable Long sessionId,
            @RequestHeader(value = "X-Guest-Token", required = false) String guestToken) {
        sessionService.deleteRecord(sessionId, player(guestToken));
        return ApiResponse.success();
    }

    @GetMapping("/games/{gameId}/leaderboard")
    public ApiResponse<EntertainmentDtos.LeaderboardResponse> leaderboard(
            @PathVariable Long gameId,
            @RequestHeader(value = "X-Guest-Token", required = false) String guestToken) {
        PlayerIdentity viewer = guestService.resolve(UserContext.getOrNull(), guestToken, false);
        return ApiResponse.success(sessionService.leaderboard(gameId, viewer));
    }

    @GetMapping("/my/sessions")
    public ApiResponse<PageResponse<EntertainmentDtos.SessionResponse>> history(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestHeader(value = "X-Guest-Token", required = false) String guestToken) {
        return ApiResponse.success(sessionService.history(player(guestToken), page, pageSize));
    }

    private PlayerIdentity player(String guestToken) {
        return guestService.resolve(UserContext.getOrNull(), guestToken, true);
    }

    private static LoginUser customer() {
        LoginUser user = UserContext.get();
        if (!user.isCustomer()) throw new com.order.menu.common.exception.BusinessException(
                com.order.menu.common.exception.ErrorCode.FORBIDDEN, "只有已登录顾客可以创建游戏");
        return user;
    }

    public record StatusRequest(@NotBlank String status) {}
}
