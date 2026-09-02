package com.order.menu.module.entertainment.mode.guessstock.entity;
import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;
import lombok.Data;
@Data @TableName("guess_stock_research_actions")
public class GuessStockResearchAction {
    @TableId(type = IdType.AUTO) private Long id;
    private Long sessionId; private Long participantId; private Integer roundNo; private String stage;
    private String actionType; private String resultJson; private String requestId; private LocalDateTime createdAt;
}
