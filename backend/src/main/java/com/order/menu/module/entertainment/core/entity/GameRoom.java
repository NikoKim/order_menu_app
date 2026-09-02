package com.order.menu.module.entertainment.core.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("game_rooms")
public class GameRoom {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long gameId;
    private Long gameVersionId;
    private Long sessionId;
    private String inviteCode;
    private String hostIdentityKey;
    private String status;
    private Integer maxPlayers;
    private LocalDateTime createdAt;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private LocalDateTime updatedAt;
}
