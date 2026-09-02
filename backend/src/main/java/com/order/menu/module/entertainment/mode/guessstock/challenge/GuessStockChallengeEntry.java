package com.order.menu.module.entertainment.mode.guessstock.challenge;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("guess_stock_challenge_entries")
public class GuessStockChallengeEntry {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long challengeId;
    private String participantIdentityKey;
    private Long sessionId;
    private BigDecimal returnPercent;
    private BigDecimal maxDrawdownPercent;
    private Integer liquidationCount;
    private BigDecimal interestCost;
    private Integer accurateResearchCount;
    private Integer researchAttemptCount;
    private LocalDateTime submittedAt;
}
