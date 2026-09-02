package com.order.menu.module.entertainment.core.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("game_versions")
public class GameVersion {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long gameId;
    private Integer versionNo;
    private String versionLabel;
    private Integer configSchemaVersion;
    private String configJson;
    private Long scenarioSeed;
    private LocalDateTime publishedAt;
}
