package com.order.menu.module.entertainment.core.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.order.menu.common.exception.BusinessException;
import com.order.menu.common.exception.ErrorCode;
import com.order.menu.common.response.PageResponse;
import com.order.menu.module.entertainment.core.dto.EntertainmentDtos;
import com.order.menu.module.entertainment.core.entity.EntertainmentGame;
import com.order.menu.module.entertainment.core.entity.GameSession;
import com.order.menu.module.entertainment.core.entity.GameRoom;
import com.order.menu.module.entertainment.core.entity.GameVersion;
import com.order.menu.module.entertainment.core.mapper.EntertainmentGameMapper;
import com.order.menu.module.entertainment.core.mapper.GameSessionMapper;
import com.order.menu.module.entertainment.core.mapper.GameRoomMapper;
import com.order.menu.module.entertainment.core.mapper.GameVersionMapper;
import com.order.menu.module.entertainment.core.mapper.GamePlayKeyMapper;
import com.order.menu.module.entertainment.core.entity.GamePlayKey;
import com.order.menu.module.entertainment.core.mode.GameModeHandler;
import com.order.menu.module.entertainment.core.mode.GameModeRegistry;
import com.order.menu.module.entertainment.mode.guessstock.config.GuessStockConfig;
import com.order.menu.module.user.entity.User;
import com.order.menu.module.user.mapper.UserMapper;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class EntertainmentGameService {
    private final EntertainmentGameMapper gameMapper;
    private final GameVersionMapper versionMapper;
    private final GamePlayKeyMapper playKeyMapper;
    private final GameSessionMapper sessionMapper;
    private final GameRoomMapper roomMapper;
    private final UserMapper userMapper;
    private final ObjectMapper objectMapper;
    private final GameModeRegistry modeRegistry;
    private final SecureRandom random = new SecureRandom();

    // Kept for focused service tests and older integrations that do not publish protected games.
    public EntertainmentGameService(EntertainmentGameMapper gameMapper, GameVersionMapper versionMapper,
                                    GameSessionMapper sessionMapper, GameRoomMapper roomMapper,
                                    UserMapper userMapper, ObjectMapper objectMapper, GameModeRegistry modeRegistry) {
        this(gameMapper, versionMapper, null, sessionMapper, roomMapper, userMapper, objectMapper, modeRegistry);
    }

    public List<EntertainmentDtos.ModeResponse> modes() {
        return modeRegistry.all().stream().map(handler -> new EntertainmentDtos.ModeResponse(
                handler.code(), handler.displayName(), handler.configSchemaVersion(), true)).toList();
    }

    public PageResponse<EntertainmentDtos.GameSummaryResponse> list(int page, int pageSize, String keyword,
                                                                     String stockType, String sort,
                                                                     PlayerIdentity viewer) {
        int safePage = Math.max(1, page);
        int safeSize = Math.min(50, Math.max(1, pageSize));
        LambdaQueryWrapper<EntertainmentGame> query = new LambdaQueryWrapper<EntertainmentGame>()
                .eq(EntertainmentGame::getStatus, "PUBLISHED")
                .eq(EntertainmentGame::getVisibility, "PUBLIC")
                .and(StringUtils.hasText(keyword), wrapper -> wrapper.like(EntertainmentGame::getTitle, keyword.trim())
                        .or().like(EntertainmentGame::getDescription, keyword.trim()));
        if ("POPULAR".equalsIgnoreCase(sort)) query.orderByDesc(EntertainmentGame::getPlayCount).orderByDesc(EntertainmentGame::getId);
        else query.orderByDesc(EntertainmentGame::getPublishedAt).orderByDesc(EntertainmentGame::getId);
        Page<EntertainmentGame> result = gameMapper.selectPage(new Page<>(safePage, safeSize), query);
        List<EntertainmentDtos.GameSummaryResponse> records = result.getRecords().stream()
                .map(game -> toSummary(game, viewer)).filter(summary -> !StringUtils.hasText(stockType)
                        || summary.stockType().equalsIgnoreCase(stockType.trim())).toList();
        return new PageResponse<>(records, safePage, safeSize, result.getTotal());
    }

    public PageResponse<EntertainmentDtos.GameSummaryResponse> mine(Long userId, int page, int pageSize) {
        int safePage = Math.max(1, page);
        int safeSize = Math.min(50, Math.max(1, pageSize));
        Page<EntertainmentGame> result = gameMapper.selectPage(new Page<>(safePage, safeSize),
                new LambdaQueryWrapper<EntertainmentGame>().eq(EntertainmentGame::getCreatorUserId, userId)
                        .ne(EntertainmentGame::getStatus, "DELETED")
                        .orderByDesc(EntertainmentGame::getUpdatedAt));
        PlayerIdentity viewer = new PlayerIdentity("USER", userId, null, "U:" + userId, "");
        return new PageResponse<>(result.getRecords().stream().map(game -> toSummary(game, viewer)).toList(),
                safePage, safeSize, result.getTotal());
    }

    public PageResponse<EntertainmentDtos.GameSummaryResponse> adminList(int page, int pageSize, String status,
                                                                          String keyword) {
        int safePage = Math.max(1, page);
        int safeSize = Math.min(50, Math.max(1, pageSize));
        LambdaQueryWrapper<EntertainmentGame> query = new LambdaQueryWrapper<EntertainmentGame>()
                .eq(StringUtils.hasText(status), EntertainmentGame::getStatus, status == null ? null : status.toUpperCase())
                .and(StringUtils.hasText(keyword), wrapper -> wrapper.like(EntertainmentGame::getTitle, keyword.trim())
                        .or().like(EntertainmentGame::getDescription, keyword.trim()))
                .orderByDesc(EntertainmentGame::getUpdatedAt);
        Page<EntertainmentGame> result = gameMapper.selectPage(new Page<>(safePage, safeSize), query);
        return new PageResponse<>(result.getRecords().stream().map(game -> toSummary(game, null)).toList(),
                safePage, safeSize, result.getTotal());
    }

    public EntertainmentDtos.GameDetailResponse detail(Long gameId, PlayerIdentity viewer) {
        EntertainmentGame game = requireGame(gameId);
        boolean owner = viewer != null && viewer.userId() != null && viewer.userId().equals(game.getCreatorUserId());
        if (!"PUBLISHED".equals(game.getStatus()) && !owner) throw notFound();
        JsonNode config = configFor(game, owner);
        GameSession resumable = viewer == null ? null : sessionMapper.selectOne(new LambdaQueryWrapper<GameSession>()
                .eq(GameSession::getGameId, gameId).eq(GameSession::getPlayerIdentityKey, viewer.key())
                .isNull(GameSession::getRoomId)
                .in(GameSession::getStatus, "ACTIVE", "PAUSED").orderByDesc(GameSession::getId).last("LIMIT 1"));
        GameRoom activeRoom = viewer == null ? null : roomMapper.selectActiveForPlayer(gameId, viewer.key());
        return new EntertainmentDtos.GameDetailResponse(toSummary(game, viewer), config,
                resumable != null, resumable == null ? null : resumable.getId(),
                activeRoom == null ? null : activeRoom.getId(), activeRoom == null ? null : activeRoom.getStatus());
    }

    @Transactional
    public EntertainmentDtos.GameDetailResponse create(Long userId, EntertainmentDtos.GameUpsertRequest request) {
        requireCustomer(userId);
        String modeCode = StringUtils.hasText(request.modeCode()) ? request.modeCode().trim().toUpperCase() : "GUESS_STOCK";
        GameModeHandler handler = modeRegistry.require(modeCode);
        JsonNode config = handler.validateAndNormalize(request.config());
        EntertainmentGame game = new EntertainmentGame();
        game.setCreatorUserId(userId);
        game.setModeCode(handler.code());
        apply(game, request, config);
        game.setStatus("DRAFT");
        game.setDraftVersionLabel(normalizeVersionLabel(request.versionLabel(), "1.0"));
        game.setPlayKeyRequired(Boolean.TRUE.equals(request.playKeyRequired()) ? 1 : 0);
        game.setLatestVersionNo(0);
        game.setPlayCount(0L);
        game.setCompletedCount(0L);
        gameMapper.insert(game);
        return detail(game.getId(), new PlayerIdentity("USER", userId, null, "U:" + userId, ""));
    }

    @Transactional
    public EntertainmentDtos.GameDetailResponse update(Long userId, Long gameId,
                                                         EntertainmentDtos.GameUpsertRequest request) {
        EntertainmentGame game = requireOwnedForUpdate(userId, gameId);
        if (StringUtils.hasText(request.modeCode()) && !game.getModeCode().equalsIgnoreCase(request.modeCode().trim())) {
            bad("已创建的游戏不能更换玩法模式");
        }
        JsonNode config = modeRegistry.require(game.getModeCode()).validateAndNormalize(request.config());
        apply(game, request, config);
        gameMapper.updateById(game);
        return detail(gameId, new PlayerIdentity("USER", userId, null, "U:" + userId, ""));
    }

    @Transactional
    public EntertainmentDtos.GameDetailResponse publish(Long userId, Long gameId) {
        EntertainmentGame game = requireOwnedForUpdate(userId, gameId);
        GameModeHandler handler = modeRegistry.require(game.getModeCode());
        JsonNode config = handler.validateAndNormalize(readTree(game.getDraftConfigJson()));
        if (Integer.valueOf(1).equals(game.getPlayKeyRequired()) && playKeyMapper.selectCount(
                new LambdaQueryWrapper<GamePlayKey>().eq(GamePlayKey::getGameId, gameId)
                        .eq(GamePlayKey::getStatus, "ACTIVE")) == 0) {
            bad("开启密钥访问后，至少需要一个启用中的游玩密钥");
        }
        int versionNo = (game.getLatestVersionNo() == null ? 0 : game.getLatestVersionNo()) + 1;
        GameVersion version = new GameVersion();
        version.setGameId(gameId);
        version.setVersionNo(versionNo);
        version.setVersionLabel(normalizeVersionLabel(game.getDraftVersionLabel(), "1.0"));
        version.setConfigSchemaVersion(handler.configSchemaVersion());
        version.setConfigJson(write(config));
        version.setScenarioSeed(random.nextLong());
        version.setPublishedAt(LocalDateTime.now());
        versionMapper.insert(version);
        game.setLatestVersionNo(versionNo);
        game.setCurrentVersionId(version.getId());
        game.setStatus("PUBLISHED");
        game.setPublishedAt(LocalDateTime.now());
        gameMapper.updateById(game);
        return detail(gameId, new PlayerIdentity("USER", userId, null, "U:" + userId, ""));
    }

    @Transactional
    public void changeStatus(Long userId, Long gameId, String status) {
        EntertainmentGame game = requireOwnedForUpdate(userId, gameId);
        String normalized = status == null ? "" : status.toUpperCase();
        if (!List.of("PUBLISHED", "OFFLINE").contains(normalized)) bad("不支持的游戏状态");
        if ("PUBLISHED".equals(normalized) && game.getCurrentVersionId() == null) bad("请先发布游戏版本");
        game.setStatus(normalized);
        gameMapper.updateById(game);
    }

    @Transactional
    public void delete(Long userId, Long gameId) {
        EntertainmentGame game = requireOwnedForUpdate(userId, gameId);
        if (!"OFFLINE".equals(game.getStatus())) bad("只有已下架游戏可以删除");
        game.setStatus("DELETED");
        gameMapper.updateById(game);
    }

    @Transactional
    public void adminChangeStatus(Long gameId, String status) {
        EntertainmentGame game = gameMapper.selectByIdForUpdate(gameId);
        if (game == null) throw notFound();
        String normalized = status == null ? "" : status.toUpperCase();
        if (!List.of("PUBLISHED", "OFFLINE").contains(normalized)) bad("不支持的游戏状态");
        if ("PUBLISHED".equals(normalized) && game.getCurrentVersionId() == null) bad("未发布过的游戏不能恢复公开");
        game.setStatus(normalized);
        gameMapper.updateById(game);
    }

    public EntertainmentGame requirePlayable(Long gameId) {
        EntertainmentGame game = requireGame(gameId);
        if (!"PUBLISHED".equals(game.getStatus()) || game.getCurrentVersionId() == null) throw notFound();
        return game;
    }

    public EntertainmentGame requireOwned(Long userId, Long gameId) {
        requireCustomer(userId);
        EntertainmentGame game = requireGame(gameId);
        if (!userId.equals(game.getCreatorUserId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "只能管理自己创建的游戏");
        }
        return game;
    }

    public EntertainmentGame requirePlayableForUpdate(Long gameId) {
        EntertainmentGame game = gameMapper.selectByIdForUpdate(gameId);
        if (game == null || !"PUBLISHED".equals(game.getStatus()) || game.getCurrentVersionId() == null) throw notFound();
        return game;
    }

    public GameVersion requireVersion(Long versionId) {
        GameVersion version = versionMapper.selectById(versionId);
        if (version == null) throw new BusinessException(ErrorCode.NOT_FOUND, "游戏版本不存在");
        return version;
    }

    public List<GameVersion> versionsForLabel(Long gameId, String versionLabel) {
        return versionMapper.selectList(new LambdaQueryWrapper<GameVersion>()
                .eq(GameVersion::getGameId, gameId).eq(GameVersion::getVersionLabel, versionLabel)
                .orderByDesc(GameVersion::getVersionNo));
    }

    public GuessStockConfig readConfig(String json) {
        try {
            JsonNode normalized = modeRegistry.require("GUESS_STOCK").validateAndNormalize(readTree(json));
            return objectMapper.treeToValue(normalized, GuessStockConfig.class);
        }
        catch (JsonProcessingException exception) { throw new BusinessException(ErrorCode.SERVER_ERROR, "游戏配置无法读取"); }
    }

    public EntertainmentDtos.GameSummaryResponse toSummary(EntertainmentGame game, PlayerIdentity viewer) {
        JsonNode config = configFor(game, viewer != null && viewer.userId() != null
                && viewer.userId().equals(game.getCreatorUserId()));
        Map<String, Object> modeData = modeRegistry.require(game.getModeCode()).summary(config);
        User creator = userMapper.selectById(game.getCreatorUserId());
        EntertainmentDtos.CreatorResponse author = creator == null
                ? new EntertainmentDtos.CreatorResponse(game.getCreatorUserId(), "已停用用户", null)
                : new EntertainmentDtos.CreatorResponse(creator.getId(), creator.getNickname(), creator.getAvatarUrl());
        return new EntertainmentDtos.GameSummaryResponse(game.getId(), game.getModeCode(), game.getTitle(),
                game.getDescription(), game.getCoverUrl(), game.getVisibility(), game.getStatus(), author,
                text(modeData, "stockCode"), text(modeData, "stockType"), bool(modeData, "aiEnabled"),
                integer(modeData, "aiCount"), integer(modeData, "roundCount"),
                game.getLatestVersionNo() == null ? 0 : game.getLatestVersionNo(),
                game.getPlayCount() == null ? 0 : game.getPlayCount(),
                game.getCompletedCount() == null ? 0 : game.getCompletedCount(), game.getPublishedAt(),
                viewer != null && viewer.userId() != null && viewer.userId().equals(game.getCreatorUserId()), modeData,
                versionLabelFor(game, viewer), Integer.valueOf(1).equals(game.getPlayKeyRequired()));
    }

    private JsonNode configFor(EntertainmentGame game, boolean owner) {
        if (game.getCurrentVersionId() != null && !owner) {
            GameVersion version = requireVersion(game.getCurrentVersionId());
            return readTree(version.getConfigJson());
        }
        return readTree(game.getDraftConfigJson());
    }

    private void apply(EntertainmentGame game, EntertainmentDtos.GameUpsertRequest request, JsonNode config) {
        game.setTitle(request.title().trim());
        game.setDescription(trim(request.description()));
        game.setCoverUrl(trim(request.coverUrl()));
        String visibility = request.visibility() == null ? "PUBLIC" : request.visibility().toUpperCase();
        if (!List.of("PUBLIC", "UNLISTED").contains(visibility)) bad("不支持的可见范围");
        game.setVisibility(visibility);
        game.setDraftVersionLabel(normalizeVersionLabel(request.versionLabel(), game.getDraftVersionLabel()));
        game.setPlayKeyRequired(Boolean.TRUE.equals(request.playKeyRequired()) ? 1 : 0);
        game.setDraftConfigJson(write(config));
    }

    private String versionLabelFor(EntertainmentGame game, PlayerIdentity viewer) {
        boolean owner = viewer != null && viewer.userId() != null && viewer.userId().equals(game.getCreatorUserId());
        if (owner || game.getCurrentVersionId() == null) return normalizeVersionLabel(game.getDraftVersionLabel(), "1.0");
        return normalizeVersionLabel(requireVersion(game.getCurrentVersionId()).getVersionLabel(), "v" + game.getLatestVersionNo());
    }

    private static String normalizeVersionLabel(String value, String fallback) {
        String normalized = StringUtils.hasText(value) ? value.trim() : fallback;
        if (!StringUtils.hasText(normalized)) normalized = "1.0";
        if (normalized.length() > 40) bad("版本标识最多 40 个字符");
        return normalized;
    }

    private EntertainmentGame requireOwnedForUpdate(Long userId, Long gameId) {
        requireCustomer(userId);
        EntertainmentGame game = gameMapper.selectByIdForUpdate(gameId);
        if (game == null || "DELETED".equals(game.getStatus())) throw notFound();
        if (!userId.equals(game.getCreatorUserId())) throw new BusinessException(ErrorCode.FORBIDDEN, "只能管理自己创建的游戏");
        return game;
    }

    private EntertainmentGame requireGame(Long id) {
        EntertainmentGame game = gameMapper.selectById(id);
        if (game == null || "DELETED".equals(game.getStatus())) throw notFound();
        return game;
    }

    private void requireCustomer(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null || user.getStatus() == null || user.getStatus() != 1) throw notFound();
        if (!"CUSTOMER".equals(user.getRole())) throw new BusinessException(ErrorCode.FORBIDDEN, "只有已登录顾客可以创建游戏");
    }

    private String write(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (JsonProcessingException exception) { throw new BusinessException(ErrorCode.SERVER_ERROR, "游戏配置无法保存"); }
    }
    private JsonNode readTree(String value) {
        try { return objectMapper.readTree(value); }
        catch (JsonProcessingException exception) { throw new BusinessException(ErrorCode.SERVER_ERROR, "游戏配置无法读取"); }
    }
    private static String text(Map<String, Object> data, String key) { return String.valueOf(data.getOrDefault(key, "")); }
    private static boolean bool(Map<String, Object> data, String key) { return Boolean.TRUE.equals(data.get(key)); }
    private static int integer(Map<String, Object> data, String key) {
        Object value = data.get(key);
        return value instanceof Number number ? number.intValue() : 0;
    }
    private static String trim(String value) { return StringUtils.hasText(value) ? value.trim() : null; }
    private static void bad(String message) { throw new BusinessException(ErrorCode.BAD_REQUEST, message); }
    private static BusinessException notFound() { return new BusinessException(ErrorCode.NOT_FOUND, "游戏不存在"); }
}
