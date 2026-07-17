package com.order.menu.module.moment.controller;
import com.order.menu.common.response.ApiResponse; import com.order.menu.module.message.dto.MessageDtos; import com.order.menu.module.moment.dto.MomentDtos; import com.order.menu.module.moment.service.MomentService; import com.order.menu.security.UserContext; import jakarta.validation.Valid; import jakarta.validation.constraints.NotNull; import lombok.RequiredArgsConstructor; import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/messages") @RequiredArgsConstructor public class MomentShareController {
  private final MomentService service;
  public record AliasRequest(@NotNull Long conversationId,@NotNull Long momentId,Long commentId){}
  @PostMapping("/moment-shares") public ApiResponse<MessageDtos.ChatMessageResponse> share(@Valid @RequestBody AliasRequest r){return ApiResponse.success(service.share(UserContext.get().userId(),r.momentId(),new MomentDtos.ShareRequest(r.conversationId(),r.commentId())));}
}
