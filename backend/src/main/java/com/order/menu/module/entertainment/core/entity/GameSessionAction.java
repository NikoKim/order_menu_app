package com.order.menu.module.entertainment.core.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("game_session_actions")
public class GameSessionAction {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long sessionId;
    private Integer roundNo;
    private String actionType;
    private String payloadJson;
    private String clientRequestId;
    private Integer accepted;
    private String rejectReason;
    private LocalDateTime createdAt;
}
