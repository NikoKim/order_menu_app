package com.order.menu.module.entertainment.core.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.order.menu.common.exception.BusinessException;
import com.order.menu.common.exception.ErrorCode;
import com.order.menu.module.entertainment.core.dto.EntertainmentDtos;
import com.order.menu.module.entertainment.core.entity.EntertainmentGame;
import com.order.menu.module.entertainment.core.entity.GameRoom;
import com.order.menu.module.entertainment.core.entity.GameRoomMember;
import com.order.menu.module.entertainment.core.entity.GameVersion;
import com.order.menu.module.entertainment.core.mapper.GameRoomMapper;
import com.order.menu.module.entertainment.core.mapper.GameRoomMemberMapper;
import com.order.menu.module.entertainment.mode.guessstock.config.GuessStockConfig;
import com.order.menu.module.entertainment.mode.guessstock.service.GuessStockSessionService;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GameRoomService {
    private static final String CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();
    private final GameRoomMapper roomMapper;
    private final GameRoomMemberMapper memberMapper;
    private final EntertainmentGameService gameService;
    private final GameAiCredentialService credentialService;
    private final GuessStockSessionService sessionService;
    private final GamePlayKeyService playKeyService;

    @Transactional
    public EntertainmentDtos.RoomResponse create(Long gameId, PlayerIdentity player,
                                                   EntertainmentDtos.CreateRoomRequest request) {
        GameRoom existing = roomMapper.selectActiveForPlayer(gameId, player.key());
        if (existing != null) return response(existing, player, config(existing));
        EntertainmentGame game = gameService.requirePlayableForUpdate(gameId);
        playKeyService.consumeIfRequired(gameId, Integer.valueOf(1).equals(game.getPlayKeyRequired()),
                request == null ? null : request.playKey(), "MULTI", player);
        GameVersion version = gameService.requireVersion(game.getCurrentVersionId());
        GuessStockConfig config = gameService.readConfig(version.getConfigJson());
        if (!Boolean.TRUE.equals(config.multiplayerEnabled())) bad("该游戏未开启多人模式");
        GameRoom room = new GameRoom();
        room.setGameId(gameId);
        room.setGameVersionId(version.getId());
        room.setHostIdentityKey(player.key());
        room.setStatus("LOBBY");
        room.setMaxPlayers(config.maxHumanPlayers());
        boolean inserted = false;
        for (int attempt = 0; attempt < 8 && !inserted; attempt++) {
            room.setInviteCode(inviteCode());
            try { roomMapper.insert(room); inserted = true; }
            catch (DuplicateKeyException ignored) { room.setId(null); }
        }
        if (!inserted) throw new BusinessException(ErrorCode.SERVER_ERROR, "邀请码生成失败，请重试");
        addMember(room.getId(), player, "HOST");
        return response(room, player, config);
    }

    @Transactional
    public EntertainmentDtos.RoomResponse join(String inviteCode, String playKey, PlayerIdentity player) {
        GameRoom room = roomMapper.selectByInviteCodeForUpdate(inviteCode.trim().toUpperCase());
        if (room == null) throw new BusinessException(ErrorCode.NOT_FOUND, "邀请码不存在");
        if (!"LOBBY".equals(room.getStatus())) throw new BusinessException(ErrorCode.CONFLICT, "房间已经开始，不能中途加入");
        GameRoomMember existing = memberMapper.selectForUpdate(room.getId(), player.key());
        if (existing != null) {
            existing.setStatus("ACTIVE");
            existing.setLeftAt(null);
            existing.setLastSeenAt(LocalDateTime.now());
            memberMapper.updateById(existing);
        } else {
            EntertainmentGame game = gameService.requirePlayable(room.getGameId());
            playKeyService.consumeIfRequired(game.getId(), Integer.valueOf(1).equals(game.getPlayKeyRequired()),
                    playKey, "MULTI", player);
            long count = memberMapper.selectCount(new LambdaQueryWrapper<GameRoomMember>()
                    .eq(GameRoomMember::getRoomId, room.getId()).eq(GameRoomMember::getStatus, "ACTIVE"));
            if (count >= room.getMaxPlayers()) throw new BusinessException(ErrorCode.CONFLICT, "房间人数已满");
            addMember(room.getId(), player, "MEMBER");
        }
        return response(room, player, config(room));
    }

    @Transactional
    public EntertainmentDtos.RoomResponse get(Long roomId, PlayerIdentity player) {
        GameRoom room = requireRoom(roomId);
        GameRoomMember member = requireMember(roomId, player);
        member.setLastSeenAt(LocalDateTime.now());
        memberMapper.updateById(member);
        return response(room, player, config(room));
    }

    @Transactional
    public EntertainmentDtos.RoomResponse configureAi(Long roomId, PlayerIdentity player,
                                                         EntertainmentDtos.AiCredentialSelectionRequest request) {
        GameRoom room = roomMapper.selectByIdForUpdate(roomId);
        if (room == null) throw new BusinessException(ErrorCode.NOT_FOUND, "多人房间不存在");
        if (!"LOBBY".equals(room.getStatus())) throw new BusinessException(ErrorCode.CONFLICT, "房间开始后不能更换 AI Key");
        if (!player.key().equals(room.getHostIdentityKey())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "只有房主可以配置多人游戏 AI");
        }
        if (!Boolean.TRUE.equals(config(room).aiEnabled())) bad("该游戏未开启 AI 对手");
        requireMember(roomId, player);
        credentialService.storeForRoom(roomId, player, request);
        return response(room, player, config(room));
    }

    @Transactional
    public EntertainmentDtos.SessionResponse start(Long roomId, PlayerIdentity player,
                                                     EntertainmentDtos.StartRoomRequest request) {
        GameRoom room = roomMapper.selectByIdForUpdate(roomId);
        if (room == null) throw new BusinessException(ErrorCode.NOT_FOUND, "多人房间不存在");
        if (!player.key().equals(room.getHostIdentityKey())) throw new BusinessException(ErrorCode.FORBIDDEN, "只有房主可以开始游戏");
        List<GameRoomMember> members = activeMembers(roomId);
        GuessStockConfig config = config(room);
        boolean useAi = Boolean.TRUE.equals(request == null ? null : request.useAi());
        if (useAi && Boolean.TRUE.equals(request.useDefaultAi())) {
            credentialService.storeForRoom(roomId, player,
                    new EntertainmentDtos.AiCredentialSelectionRequest(true, null));
        }
        if (useAi && !Boolean.TRUE.equals(config.aiEnabled())) bad("该游戏未开启 AI 对手");
        return sessionService.startRoom(room, members, player, useAi);
    }

    @Transactional
    public void leave(Long roomId, PlayerIdentity player) {
        GameRoom room = roomMapper.selectByIdForUpdate(roomId);
        if (room == null) return;
        GameRoomMember member = requireMember(roomId, player);
        member.setStatus("LEFT");
        member.setLeftAt(LocalDateTime.now());
        memberMapper.updateById(member);
        credentialService.purgeRoomPlayer(roomId, player.key());
        transferHostIfNeeded(room, player.key());
        sessionService.reevaluateRoomGate(room.getSessionId());
    }

    @Transactional
    public void close(Long roomId, PlayerIdentity player) {
        GameRoom room = roomMapper.selectByIdForUpdate(roomId);
        if (room == null) return;
        requireMember(roomId, player);
        if (!player.key().equals(room.getHostIdentityKey())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "只有房主可以关闭多人房间");
        }
        if ("CLOSED".equals(room.getStatus())) return;
        sessionService.closeRoomSession(room.getSessionId(), room.getId());
        room.setStatus("CLOSED");
        room.setCompletedAt(LocalDateTime.now());
        roomMapper.updateById(room);
        credentialService.purgeRoom(roomId);
    }

    @Transactional
    public EntertainmentDtos.RoomResponse remove(Long roomId, Long memberId, PlayerIdentity player) {
        GameRoom room = roomMapper.selectByIdForUpdate(roomId);
        if (room == null) throw new BusinessException(ErrorCode.NOT_FOUND, "多人房间不存在");
        if (!player.key().equals(room.getHostIdentityKey())) throw new BusinessException(ErrorCode.FORBIDDEN, "只有房主可以移除成员");
        GameRoomMember member = memberMapper.selectById(memberId);
        if (member == null || !roomId.equals(member.getRoomId()) || !"ACTIVE".equals(member.getStatus())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "房间成员不存在");
        }
        if (player.key().equals(member.getPlayerIdentityKey())) bad("房主请使用离开房间");
        if (member.getLastSeenAt().isAfter(LocalDateTime.now().minusSeconds(60))) {
            throw new BusinessException(ErrorCode.CONFLICT, "成员断线满 60 秒后才能移除");
        }
        member.setStatus("KICKED");
        member.setLeftAt(LocalDateTime.now());
        memberMapper.updateById(member);
        credentialService.purgeRoomPlayer(roomId, member.getPlayerIdentityKey());
        sessionService.reevaluateRoomGate(room.getSessionId());
        return response(room, player, config(room));
    }

    private EntertainmentDtos.RoomResponse response(GameRoom room, PlayerIdentity player, GuessStockConfig config) {
        List<EntertainmentDtos.RoomMemberResponse> members = activeMembers(room.getId()).stream().map(member ->
                new EntertainmentDtos.RoomMemberResponse(member.getId(), member.getDisplayName(), member.getPlayerType(),
                        member.getMemberRole(), member.getStatus(), player.key().equals(member.getPlayerIdentityKey()),
                        credentialService.readyForRoom(room.getId(), member.getPlayerIdentityKey()), false, false, false,
                        null, null, member.getLastSeenAt())).toList();
        EntertainmentGame game = gameService.requirePlayable(room.getGameId());
        return new EntertainmentDtos.RoomResponse(room.getId(), room.getInviteCode(), room.getStatus(), room.getMaxPlayers(),
                room.getGameId(), room.getGameVersionId(), room.getSessionId(), player.key().equals(room.getHostIdentityKey()),
                Boolean.TRUE.equals(config.aiEnabled()), credentialService.readyForGame(room.getGameId()),
                members, gameService.toSummary(game, player));
    }

    private void addMember(Long roomId, PlayerIdentity player, String role) {
        GameRoomMember member = new GameRoomMember();
        member.setRoomId(roomId);
        member.setPlayerType(player.type());
        member.setPlayerUserId(player.userId());
        member.setGuestId(player.guestId());
        member.setPlayerIdentityKey(player.key());
        member.setDisplayName(player.displayName());
        member.setMemberRole(role);
        member.setStatus("ACTIVE");
        member.setLastSeenAt(LocalDateTime.now());
        memberMapper.insert(member);
    }

    private void transferHostIfNeeded(GameRoom room, String leavingIdentity) {
        if (!leavingIdentity.equals(room.getHostIdentityKey())) return;
        List<GameRoomMember> remaining = activeMembers(room.getId());
        if (remaining.isEmpty()) {
            if ("LOBBY".equals(room.getStatus())) room.setStatus("CLOSED");
            credentialService.purgeRoom(room.getId());
        } else {
            GameRoomMember next = remaining.get(0);
            next.setMemberRole("HOST");
            memberMapper.updateById(next);
            room.setHostIdentityKey(next.getPlayerIdentityKey());
        }
        roomMapper.updateById(room);
    }

    private GameRoom requireRoom(Long id) {
        GameRoom room = roomMapper.selectById(id);
        if (room == null) throw new BusinessException(ErrorCode.NOT_FOUND, "多人房间不存在");
        return room;
    }
    private GameRoomMember requireMember(Long roomId, PlayerIdentity player) {
        GameRoomMember member = memberMapper.selectForUpdate(roomId, player.key());
        if (member == null || !"ACTIVE".equals(member.getStatus())) throw new BusinessException(ErrorCode.FORBIDDEN, "你不在该多人房间中");
        return member;
    }
    private List<GameRoomMember> activeMembers(Long roomId) {
        return memberMapper.selectList(new LambdaQueryWrapper<GameRoomMember>()
                .eq(GameRoomMember::getRoomId, roomId).eq(GameRoomMember::getStatus, "ACTIVE")
                .orderByAsc(GameRoomMember::getJoinedAt));
    }
    private GuessStockConfig config(GameRoom room) {
        return gameService.readConfig(gameService.requireVersion(room.getGameVersionId()).getConfigJson());
    }
    private static String inviteCode() {
        StringBuilder result = new StringBuilder(6);
        for (int i = 0; i < 6; i++) result.append(CODE_CHARS.charAt(RANDOM.nextInt(CODE_CHARS.length())));
        return result.toString();
    }
    private static void bad(String message) { throw new BusinessException(ErrorCode.BAD_REQUEST, message); }
}
