package com.order.menu.module.entertainment.core.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("game_play_keys")
public class GamePlayKey {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long gameId;
    private String keyHash;
    private String keyHint;
    private String playScope;
    private String usageMode;
    private Integer maxUses;
    private Integer usedCount;
    private String status;
    private LocalDateTime lastUsedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
