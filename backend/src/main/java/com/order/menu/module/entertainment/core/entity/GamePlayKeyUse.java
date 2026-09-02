package com.order.menu.module.entertainment.core.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("game_play_key_uses")
public class GamePlayKeyUse {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long playKeyId;
    private Long gameId;
    private String playerIdentityKey;
    private String playScope;
    private LocalDateTime usedAt;
}
