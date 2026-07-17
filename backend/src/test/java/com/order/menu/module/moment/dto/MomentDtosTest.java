package com.order.menu.module.moment.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class MomentDtosTest {
  @Test
  void momentCommentTimestampsCarryTheServerOffset() throws Exception {
    MomentDtos.CommentResponse response = new MomentDtos.CommentResponse(
        1L, 2L, null, "内容", List.of(), null, null, null, false,
        0L, false, 0L, List.of(), LocalDateTime.of(2026, 7, 16, 12, 34, 56));

    String json = new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .writeValueAsString(response);

    assertTrue(json.contains("\"createdAt\":\"2026-07-16T12:34:56.000+08:00\""));
  }
}
