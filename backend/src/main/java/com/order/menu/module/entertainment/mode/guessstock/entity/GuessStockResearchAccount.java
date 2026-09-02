package com.order.menu.module.entertainment.mode.guessstock.entity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
@Data @TableName("guess_stock_research_accounts")
public class GuessStockResearchAccount {
    @TableId(type = IdType.AUTO) private Long id;
    private Long sessionId; private Long participantId; private Integer totalPoints; private Integer remainingPoints;
}
