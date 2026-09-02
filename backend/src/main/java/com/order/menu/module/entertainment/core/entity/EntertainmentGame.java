package com.order.menu.module.entertainment.core.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("games")
public class EntertainmentGame {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long creatorUserId;
    private String modeCode;
    private String title;
    private String description;
    private String coverUrl;
    private String visibility;
    private Integer playKeyRequired;
    private String status;
    private String draftConfigJson;
    private String draftVersionLabel;
    private Integer latestVersionNo;
    private Long currentVersionId;
    private Long playCount;
    private Long completedCount;
    private LocalDateTime publishedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
