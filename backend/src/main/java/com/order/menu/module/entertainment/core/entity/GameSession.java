package com.order.menu.module.entertainment.core.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("game_sessions")
public class GameSession {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long gameId;
    private Long gameVersionId;
    private Long roomId;
    private String playerType;
    private Long playerUserId;
    private Long guestId;
    private String playerIdentityKey;
    private String status;
    private Integer currentRound;
    private String phase;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime phaseEndsAt;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long phaseRemainingMs;
    private String progressionMode;
    private BigDecimal playbackSpeed;
    private Integer autoAdvance;
    private Long scenarioSeed;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime activityLeaseUntil;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime pausedAt;
    private String resultJson;
    private Long rowVersion;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
