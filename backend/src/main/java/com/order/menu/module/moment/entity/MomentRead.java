package com.order.menu.module.moment.entity; import com.baomidou.mybatisplus.annotation.*; import java.time.LocalDateTime; import lombok.Data;
@Data @TableName("moment_reads") public class MomentRead { @TableId(type=IdType.AUTO) private Long id; private Long userId; private Long momentId; private LocalDateTime readAt; }
