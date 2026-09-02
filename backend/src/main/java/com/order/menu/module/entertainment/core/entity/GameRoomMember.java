package com.order.menu.module.entertainment.core.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("game_room_members")
public class GameRoomMember {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long roomId;
    private Long participantId;
    private String playerType;
    private Long playerUserId;
    private Long guestId;
    private String playerIdentityKey;
    private String displayName;
    private String memberRole;
    private String status;
    private Integer eventAckRound;
    private String eventAckStage;
    private Integer readyRound;
    private String readyStage;
    private Long restartSessionId;
    private LocalDateTime joinedAt;
    private LocalDateTime lastSeenAt;
    private LocalDateTime leftAt;
}
