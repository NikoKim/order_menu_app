package com.order.menu.module.entertainment.mode.guessstock.challenge;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("guess_stock_challenges")
public class GuessStockChallenge {
    @TableId(type = IdType.AUTO)
    private Long id;
    private LocalDate challengeDate;
    private Long gameVersionId;
    private String rulesVersion;
    private Long scenarioSeed;
    private String immutableKey;
    private String configJson;
    private LocalDateTime createdAt;
}
