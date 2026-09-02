package com.order.menu.module.entertainment.core.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("game_ai_credentials")
public class GameAiCredential {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long roomId;
    private Long sessionId;
    private Long defaultOwnerUserId;
    private Long gameId;
    private String playerIdentityKey;
    private String baseUrl;
    private String modelName;
    private String apiKeyCiphertext;
    private String apiKeyIv;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
