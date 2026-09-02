package com.order.menu.module.entertainment.mode.guessstock.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("guess_stock_participants")
public class GuessStockParticipant {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long sessionId;
    private String participantType;
    private Long playerUserId;
    private Long guestId;
    private String playerIdentityKey;
    private Long roomMemberId;
    private String aiProfile;
    private String displayName;
    private BigDecimal cashBalance;
    private BigDecimal initialCash;
    private BigDecimal reservedCash;
    private BigDecimal finalAsset;
    private Integer finalRank;
    private Integer recordDeleted;
    private Long rowVersion;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
