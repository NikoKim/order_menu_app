package com.order.menu.module.entertainment.mode.guessstock.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("guess_stock_ai_tasks")
public class GuessStockAiTask {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long sessionId;
    private Long participantId;
    private Integer roundNo;
    private String stage;
    private LocalDateTime executeAt;
    private String decisionStatus;
    private String decisionReason;
    private String decisionJson;
    private String marketSnapshotJson;
    private String snapshotVersion;
    private String promptVersion;
    private String modelRoute;
    private String provider;
    private String model;
    private Long latencyMs;
    private Integer promptTokens;
    private Integer completionTokens;
    private Integer cacheHit;
    private String fallbackReason;
    private Integer httpStatus;
    private Integer attemptCount;
    private String decisionCacheKey;
    private String errorMessage;
    private String idempotencyKey;
    private LocalDateTime createdAt;
    private LocalDateTime executedAt;
}
