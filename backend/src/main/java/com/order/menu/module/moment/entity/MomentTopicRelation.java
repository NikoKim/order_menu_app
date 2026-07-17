package com.order.menu.module.moment.entity;
import com.baomidou.mybatisplus.annotation.*; import java.time.LocalDateTime; import lombok.Data;
@Data @TableName("moment_topic_relations") public class MomentTopicRelation { private Long momentId; private Long topicId; private LocalDateTime createdAt; }
