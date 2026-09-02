package com.order.menu.module.entertainment.core.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("game_leaderboard_entries")
public class GameLeaderboardEntry {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long gameId;
    private Long gameVersionId;
    private Long sessionId;
    private String playerIdentityKey;
    private String playerType;
    private Long playerUserId;
    private Long guestId;
    private String displayName;
    private BigDecimal score;
    private BigDecimal profitAmount;
    private BigDecimal finalAsset;
    private LocalDateTime completedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
