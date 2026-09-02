package com.order.menu.module.entertainment.mode.guessstock.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("guess_stock_event_chain_states")
public class GuessStockEventChainState {
    @TableId(type = IdType.AUTO) private Long id;
    private Long sessionId;
    private String chainKey;
    private Integer lastStep;
    private Integer depth;
    private Integer cooldownUntilRound;
    private LocalDateTime updatedAt;
}
