package com.order.menu.module.entertainment.core.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.order.menu.common.exception.BusinessException;
import com.order.menu.common.exception.ErrorCode;
import com.order.menu.module.entertainment.core.dto.EntertainmentDtos;
import com.order.menu.module.entertainment.core.entity.GamePlayKey;
import com.order.menu.module.entertainment.core.entity.GamePlayKeyUse;
import com.order.menu.module.entertainment.core.mapper.GamePlayKeyMapper;
import com.order.menu.module.entertainment.core.mapper.GamePlayKeyUseMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class GamePlayKeyService {
    private final GamePlayKeyMapper keyMapper;
    private final GamePlayKeyUseMapper useMapper;

    public List<EntertainmentDtos.PlayKeyResponse> list(Long gameId) {
        return keyMapper.selectList(new LambdaQueryWrapper<GamePlayKey>()
                .eq(GamePlayKey::getGameId, gameId).ne(GamePlayKey::getStatus, "DELETED")
                .orderByDesc(GamePlayKey::getId)).stream().map(this::response).toList();
    }

    @Transactional
    public EntertainmentDtos.PlayKeyResponse create(Long gameId, EntertainmentDtos.PlayKeyCreateRequest request) {
        String raw = request.keyValue() == null ? "" : request.keyValue().trim();
        if (raw.length() < 4 || raw.length() > 80) bad("游玩密钥长度需为 4 到 80 个字符");
        String scope = normalize(request.playScope(), List.of("SOLO", "MULTI", "BOTH"), "BOTH", "密钥适用模式无效");
        String mode = normalize(request.usageMode(), List.of("ONCE", "LIMITED", "UNLIMITED"), "ONCE", "密钥使用规则无效");
        Integer maxUses = "ONCE".equals(mode) ? 1 : "UNLIMITED".equals(mode) ? null : request.maxUses();
        if ("LIMITED".equals(mode) && (maxUses == null || maxUses < 1 || maxUses > 100000)) bad("有限次密钥次数需为 1 到 100000");
        GamePlayKey key = new GamePlayKey();
        key.setGameId(gameId); key.setKeyHash(hash(raw)); key.setKeyHint(hint(raw)); key.setPlayScope(scope);
        key.setUsageMode(mode); key.setMaxUses(maxUses); key.setUsedCount(0); key.setStatus("ACTIVE");
        try { keyMapper.insert(key); }
        catch (RuntimeException exception) { bad("该密钥已存在或无法保存"); }
        return response(key);
    }

    @Transactional
    public EntertainmentDtos.PlayKeyResponse setStatus(Long gameId, Long keyId, String status) {
        GamePlayKey key = require(gameId, keyId);
        String normalized = normalize(status, List.of("ACTIVE", "DISABLED"), null, "密钥状态无效");
        if ("DELETED".equals(key.getStatus())) bad("已删除的密钥不能恢复");
        key.setStatus(normalized); keyMapper.updateById(key); return response(key);
    }

    @Transactional
    public void delete(Long gameId, Long keyId) {
        GamePlayKey key = require(gameId, keyId);
        key.setStatus("DELETED"); keyMapper.updateById(key);
    }

    @Transactional
    public void consumeIfRequired(Long gameId, boolean required, String rawKey, String scope, PlayerIdentity player) {
        if (!required) return;
        if (!StringUtils.hasText(rawKey)) bad("该游戏需要游玩密钥");
        GamePlayKey key = keyMapper.selectForUse(gameId, hash(rawKey.trim()));
        if (key == null || !"ACTIVE".equals(key.getStatus())) bad("游玩密钥无效或已停用");
        if (!("BOTH".equals(key.getPlayScope()) || scope.equals(key.getPlayScope()))) bad("该密钥不能用于当前游戏模式");
        int used = key.getUsedCount() == null ? 0 : key.getUsedCount();
        if (key.getMaxUses() != null && used >= key.getMaxUses()) bad("游玩密钥使用次数已用完");
        key.setUsedCount(used + 1); key.setLastUsedAt(LocalDateTime.now()); keyMapper.updateById(key);
        GamePlayKeyUse use = new GamePlayKeyUse();
        use.setPlayKeyId(key.getId()); use.setGameId(gameId); use.setPlayerIdentityKey(player.key()); use.setPlayScope(scope);
        useMapper.insert(use);
    }

    private GamePlayKey require(Long gameId, Long keyId) {
        GamePlayKey key = keyMapper.selectById(keyId);
        if (key == null || !gameId.equals(key.getGameId())) throw new BusinessException(ErrorCode.NOT_FOUND, "游玩密钥不存在");
        return key;
    }
    private EntertainmentDtos.PlayKeyResponse response(GamePlayKey key) {
        return new EntertainmentDtos.PlayKeyResponse(key.getId(), key.getKeyHint(), key.getPlayScope(), key.getUsageMode(),
                key.getMaxUses(), key.getUsedCount() == null ? 0 : key.getUsedCount(), key.getStatus(),
                key.getLastUsedAt(), key.getCreatedAt());
    }
    private static String normalize(String value, List<String> allowed, String fallback, String message) {
        String normalized = StringUtils.hasText(value) ? value.trim().toUpperCase() : fallback;
        if (normalized == null || !allowed.contains(normalized)) bad(message);
        return normalized;
    }
    private static String hint(String raw) { return raw.length() <= 8 ? raw : raw.substring(0, 4) + "..." + raw.substring(raw.length() - 3); }
    private static String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception exception) { throw new IllegalStateException(exception); }
    }
    private static void bad(String message) { throw new BusinessException(ErrorCode.BAD_REQUEST, message); }
}
