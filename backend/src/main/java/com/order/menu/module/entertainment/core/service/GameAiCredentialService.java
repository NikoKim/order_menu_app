package com.order.menu.module.entertainment.core.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.order.menu.common.exception.BusinessException;
import com.order.menu.common.exception.ErrorCode;
import com.order.menu.config.JwtProperties;
import com.order.menu.module.entertainment.core.dto.EntertainmentDtos;
import com.order.menu.module.entertainment.core.entity.GameAiCredential;
import com.order.menu.module.entertainment.core.mapper.GameAiCredentialMapper;
import java.net.InetAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GameAiCredentialService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private final GameAiCredentialMapper credentialMapper;
    private final JwtProperties jwtProperties;

    @Transactional
    public void storeForRoom(Long roomId, PlayerIdentity player, EntertainmentDtos.AiCredentialRequest request) {
        store(roomId, null, player.key(), request);
    }

    @Transactional
    public void storeForRoom(Long roomId, PlayerIdentity player,
                             EntertainmentDtos.AiCredentialSelectionRequest selection) {
        storeForRoom(roomId, player, selectedRequest(player, selection));
    }

    @Transactional
    public void storeForSession(Long sessionId, PlayerIdentity player, EntertainmentDtos.AiCredentialRequest request) {
        store(null, sessionId, player.key(), request);
    }

    @Transactional
    public void storeForSession(Long sessionId, Long gameId, PlayerIdentity player,
                                EntertainmentDtos.StartSessionRequest selection) {
        if (Boolean.TRUE.equals(selection == null ? null : selection.useDefaultAi())) {
            storeForSession(sessionId, player, defaultRequest(player));
        } else if (selection != null && selection.aiCredential() != null) {
            storeForSession(sessionId, player, selection.aiCredential());
        } else {
            copyHostedCredential(gameId, sessionId, player.key());
        }
    }

    @Transactional
    public void attachRoomCredential(Long roomId, Long sessionId, String identityKey) {
        GameAiCredential row = credentialMapper.selectOne(new LambdaQueryWrapper<GameAiCredential>()
                .eq(GameAiCredential::getRoomId, roomId)
                .eq(GameAiCredential::getPlayerIdentityKey, identityKey)
                .eq(GameAiCredential::getStatus, "ACTIVE").last("LIMIT 1"));
        if (row == null) throw new BusinessException(ErrorCode.BAD_REQUEST, "请先配置房主的 AI API Key");
        row.setSessionId(sessionId);
        credentialMapper.updateById(row);
    }

    public boolean readyForRoom(Long roomId, String identityKey) {
        return credentialMapper.selectCount(new LambdaQueryWrapper<GameAiCredential>()
                .eq(GameAiCredential::getRoomId, roomId).eq(GameAiCredential::getPlayerIdentityKey, identityKey)
                .eq(GameAiCredential::getStatus, "ACTIVE")) > 0;
    }

    public boolean readyForGame(Long gameId) {
        return gameId != null && credentialMapper.selectCount(new LambdaQueryWrapper<GameAiCredential>()
                .eq(GameAiCredential::getGameId, gameId).eq(GameAiCredential::getStatus, "ACTIVE")) > 0;
    }

    public boolean readyForRoomOrGame(Long roomId, Long gameId, String identityKey) {
        return readyForRoom(roomId, identityKey) || readyForGame(gameId);
    }

    @Transactional
    public void attachRoomOrGameCredential(Long roomId, Long sessionId, Long gameId, String identityKey) {
        if (readyForRoom(roomId, identityKey)) attachRoomCredential(roomId, sessionId, identityKey);
        else copyHostedCredential(gameId, sessionId, identityKey);
    }

    public List<DecryptedCredential> credentialsFor(Long sessionId, Long roomId) {
        LambdaQueryWrapper<GameAiCredential> query = new LambdaQueryWrapper<GameAiCredential>()
                .eq(GameAiCredential::getStatus, "ACTIVE");
        query.eq(GameAiCredential::getSessionId, sessionId);
        return credentialMapper.selectList(query.orderByAsc(GameAiCredential::getId)).stream()
                .map(row -> new DecryptedCredential(row.getBaseUrl(), row.getModelName(), decrypt(row), row.getPlayerIdentityKey()))
                .toList();
    }

    @Transactional
    public void purgeSession(Long sessionId) {
        credentialMapper.delete(new LambdaQueryWrapper<GameAiCredential>().eq(GameAiCredential::getSessionId, sessionId));
    }

    @Transactional
    public void purgeRoomPlayer(Long roomId, String identityKey) {
        credentialMapper.delete(new LambdaQueryWrapper<GameAiCredential>()
                .eq(GameAiCredential::getRoomId, roomId)
                .eq(GameAiCredential::getPlayerIdentityKey, identityKey));
    }

    @Transactional
    public void purgeRoom(Long roomId) {
        credentialMapper.delete(new LambdaQueryWrapper<GameAiCredential>().eq(GameAiCredential::getRoomId, roomId));
    }

    public EntertainmentDtos.AiDefaultResponse defaultCredential(PlayerIdentity player) {
        requireLoggedIn(player);
        GameAiCredential row = defaultRow(player.userId());
        return row == null
                ? new EntertainmentDtos.AiDefaultResponse(false, null, null, null)
                : new EntertainmentDtos.AiDefaultResponse(true, row.getBaseUrl(), row.getModelName(), row.getUpdatedAt());
    }

    public EntertainmentDtos.AiGameCredentialResponse gameCredential(Long gameId) {
        GameAiCredential row = hostedRow(gameId);
        return row == null
                ? new EntertainmentDtos.AiGameCredentialResponse(false, null, null, null)
                : new EntertainmentDtos.AiGameCredentialResponse(true, row.getBaseUrl(), row.getModelName(), row.getUpdatedAt());
    }

    @Transactional
    public EntertainmentDtos.AiGameCredentialResponse saveGameCredential(Long gameId, PlayerIdentity owner,
                                                                          EntertainmentDtos.AiCredentialRequest request) {
        requireLoggedIn(owner);
        String baseUrl = validateBaseUrl(request.baseUrl());
        String model = request.model().trim();
        String apiKey = request.apiKey().trim();
        if (apiKey.length() < 8) throw new BusinessException(ErrorCode.BAD_REQUEST, "API Key 格式无效");
        GameAiCredential credential = hostedRow(gameId);
        if (credential == null) credential = new GameAiCredential();
        byte[] iv = new byte[12];
        RANDOM.nextBytes(iv);
        credential.setRoomId(null);
        credential.setSessionId(null);
        credential.setDefaultOwnerUserId(null);
        credential.setGameId(gameId);
        credential.setPlayerIdentityKey(owner.key());
        credential.setBaseUrl(baseUrl);
        credential.setModelName(model);
        credential.setApiKeyIv(Base64.getEncoder().encodeToString(iv));
        credential.setApiKeyCiphertext(encrypt(apiKey, iv));
        credential.setStatus("ACTIVE");
        if (credential.getId() == null) credentialMapper.insert(credential); else credentialMapper.updateById(credential);
        return gameCredential(gameId);
    }

    @Transactional
    public void deleteGameCredential(Long gameId) {
        credentialMapper.delete(new LambdaQueryWrapper<GameAiCredential>().eq(GameAiCredential::getGameId, gameId));
    }

    @Transactional
    public EntertainmentDtos.AiDefaultResponse saveDefault(PlayerIdentity player,
                                                            EntertainmentDtos.AiCredentialRequest request) {
        requireLoggedIn(player);
        String baseUrl = validateBaseUrl(request.baseUrl());
        String model = request.model().trim();
        String apiKey = request.apiKey().trim();
        if (apiKey.length() < 8) throw new BusinessException(ErrorCode.BAD_REQUEST, "API Key 格式无效");
        GameAiCredential credential = defaultRow(player.userId());
        if (credential == null) credential = new GameAiCredential();
        byte[] iv = new byte[12];
        RANDOM.nextBytes(iv);
        credential.setRoomId(null);
        credential.setSessionId(null);
        credential.setDefaultOwnerUserId(player.userId());
        credential.setGameId(null);
        credential.setPlayerIdentityKey(player.key());
        credential.setBaseUrl(baseUrl);
        credential.setModelName(model);
        credential.setApiKeyIv(Base64.getEncoder().encodeToString(iv));
        credential.setApiKeyCiphertext(encrypt(apiKey, iv));
        credential.setStatus("ACTIVE");
        if (credential.getId() == null) credentialMapper.insert(credential); else credentialMapper.updateById(credential);
        return defaultCredential(player);
    }

    @Transactional
    public void deleteDefault(PlayerIdentity player) {
        requireLoggedIn(player);
        credentialMapper.delete(new LambdaQueryWrapper<GameAiCredential>()
                .eq(GameAiCredential::getDefaultOwnerUserId, player.userId()));
    }

    private void store(Long roomId, Long sessionId, String identityKey,
                       EntertainmentDtos.AiCredentialRequest request) {
        if (request == null) throw new BusinessException(ErrorCode.BAD_REQUEST, "启用模型 AI 时必须提供 API Key");
        String baseUrl = validateBaseUrl(request.baseUrl());
        String model = request.model().trim();
        String apiKey = request.apiKey().trim();
        if (apiKey.length() < 8) throw new BusinessException(ErrorCode.BAD_REQUEST, "API Key 格式无效");
        LambdaQueryWrapper<GameAiCredential> query = new LambdaQueryWrapper<GameAiCredential>()
                .eq(GameAiCredential::getPlayerIdentityKey, identityKey);
        if (roomId != null) query.eq(GameAiCredential::getRoomId, roomId); else query.eq(GameAiCredential::getSessionId, sessionId);
        GameAiCredential credential = credentialMapper.selectOne(query.last("LIMIT 1"));
        if (credential == null) credential = new GameAiCredential();
        byte[] iv = new byte[12];
        RANDOM.nextBytes(iv);
        credential.setRoomId(roomId);
        credential.setSessionId(sessionId);
        credential.setDefaultOwnerUserId(null);
        credential.setGameId(null);
        credential.setPlayerIdentityKey(identityKey);
        credential.setBaseUrl(baseUrl);
        credential.setModelName(model);
        credential.setApiKeyIv(Base64.getEncoder().encodeToString(iv));
        credential.setApiKeyCiphertext(encrypt(apiKey, iv));
        credential.setStatus("ACTIVE");
        if (credential.getId() == null) credentialMapper.insert(credential); else credentialMapper.updateById(credential);
    }

    private EntertainmentDtos.AiCredentialRequest selectedRequest(
            PlayerIdentity player, EntertainmentDtos.AiCredentialSelectionRequest selection) {
        if (Boolean.TRUE.equals(selection == null ? null : selection.useDefault())) return defaultRequest(player);
        return selection == null ? null : selection.aiCredential();
    }

    private EntertainmentDtos.AiCredentialRequest defaultRequest(PlayerIdentity player) {
        requireLoggedIn(player);
        GameAiCredential row = defaultRow(player.userId());
        if (row == null) throw new BusinessException(ErrorCode.BAD_REQUEST, "请先保存默认 AI 配置");
        return new EntertainmentDtos.AiCredentialRequest(row.getBaseUrl(), row.getModelName(), decrypt(row));
    }

    private GameAiCredential defaultRow(Long userId) {
        return credentialMapper.selectOne(new LambdaQueryWrapper<GameAiCredential>()
                .eq(GameAiCredential::getDefaultOwnerUserId, userId)
                .eq(GameAiCredential::getStatus, "ACTIVE").last("LIMIT 1"));
    }

    private GameAiCredential hostedRow(Long gameId) {
        return credentialMapper.selectOne(new LambdaQueryWrapper<GameAiCredential>()
                .eq(GameAiCredential::getGameId, gameId)
                .eq(GameAiCredential::getStatus, "ACTIVE").last("LIMIT 1"));
    }

    private void copyHostedCredential(Long gameId, Long sessionId, String identityKey) {
        GameAiCredential source = hostedRow(gameId);
        if (source == null) throw new BusinessException(ErrorCode.BAD_REQUEST, "启用模型 AI 时必须提供 API Key");
        GameAiCredential credential = new GameAiCredential();
        credential.setSessionId(sessionId);
        credential.setPlayerIdentityKey(identityKey);
        credential.setBaseUrl(source.getBaseUrl());
        credential.setModelName(source.getModelName());
        credential.setApiKeyIv(source.getApiKeyIv());
        credential.setApiKeyCiphertext(source.getApiKeyCiphertext());
        credential.setStatus("ACTIVE");
        credentialMapper.insert(credential);
    }

    private static void requireLoggedIn(PlayerIdentity player) {
        if (player == null || !player.loggedIn()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "登录后才能使用默认 AI 配置");
        }
    }

    private String validateBaseUrl(String value) {
        try {
            URI uri = URI.create(value.trim());
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null) {
                throw new IllegalArgumentException();
            }
            for (InetAddress address : InetAddress.getAllByName(uri.getHost())) {
                if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                        || address.isSiteLocalAddress() || address.isMulticastAddress()) throw new IllegalArgumentException();
            }
            String normalized = value.trim();
            return normalized.endsWith("/") ? normalized.substring(0, normalized.length() - 1) : normalized;
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "AI Base URL 必须是可公开访问的 HTTPS 地址");
        }
    }

    private String encrypt(String value, byte[] iv) {
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key(), new GCMParameterSpec(128, iv));
            return Base64.getEncoder().encodeToString(cipher.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to encrypt AI credential", exception);
        }
    }

    private String decrypt(GameAiCredential row) {
        try {
            byte[] iv = Base64.getDecoder().decode(row.getApiKeyIv());
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, iv));
            return new String(cipher.doFinal(Base64.getDecoder().decode(row.getApiKeyCiphertext())), StandardCharsets.UTF_8);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to decrypt AI credential", exception);
        }
    }

    private SecretKeySpec key() throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest((jwtProperties.getSecret() + ":entertainment-ai").getBytes(StandardCharsets.UTF_8));
        return new SecretKeySpec(digest, "AES");
    }

    public record DecryptedCredential(String baseUrl, String model, String apiKey, String ownerIdentityKey) {}
}
