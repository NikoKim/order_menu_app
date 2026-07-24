package com.order.menu.module.moment.controller;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

import com.order.menu.module.moment.dto.MomentDtos;
import com.order.menu.module.moment.service.MomentMediaService;
import com.order.menu.module.moment.service.MomentService;
import java.util.List;
import org.junit.jupiter.api.Test;

class MomentControllerTest {
  @Test
  void hotEndpointCapsItsDefaultRequestAtTen() {
    MomentService service = mock(MomentService.class);
    when(service.hot(-1L, 10L)).thenReturn(List.of());

    var response = new MomentController(service, mock(MomentMediaService.class)).hot(10L);

    assertTrue(response.getData().isEmpty());
    verify(service).hot(-1L, 10L);
  }

  @Test
  void shareMetadataKeepsItsOwnUrlAndOpensTheMomentWithoutALandingPage() {
    String target = "https://moment.zsj-unknown.top/customer/moments/7";
    String cardUrl = "https://order-menu-backend.zsj-unknown.top/api/moments/7/share-card?mode=isolated&v=q9-test&commentId=2";

    String html = MomentController.shareHtml("Moment title", "Moment content", "https://example.com/cover.jpg", target, cardUrl);

    assertTrue(html.contains("<title>Moment title</title>"));
    assertTrue(html.contains("property=\"og:title\" content=\"Moment title\""));
    assertTrue(html.contains("property=\"og:url\" content=\"" + cardUrl.replace("&", "&amp;") + "\""));
    assertTrue(html.contains("rel=\"canonical\" href=\"" + cardUrl.replace("&", "&amp;") + "\""));
    assertTrue(html.contains("<iframe src=\"" + target + "\""));
    assertFalse(html.contains("location.replace"));
    assertFalse(html.contains("history.replaceState"));
    assertFalse(html.contains("class=\"card\""));
    assertFalse(html.contains("http-equiv=\"refresh\""));
  }

  @Test
  void commentShareUsesMomentTitleCommentTextAndImageFallbackOrder() {
    MomentService service = mock(MomentService.class);
    MomentDtos.MomentResponse moment = mock(MomentDtos.MomentResponse.class);
    MomentDtos.CommentResponse comment = mock(MomentDtos.CommentResponse.class);
    when(service.detail(-1L, 7L)).thenReturn(moment);
    when(moment.title()).thenReturn("Moment title");
    when(moment.content()).thenReturn("Moment content");
    when(moment.imageUrls()).thenReturn(List.of("/images/moment.jpg"));
    when(service.commentForShare(-1L, 7L, 2L)).thenReturn(comment);
    when(comment.content()).thenReturn("Comment content");
    when(comment.imageUrls()).thenReturn(List.of("/images/comment.jpg"));

    String html = new MomentController(service, mock(MomentMediaService.class))
        .shareCard(7L, 2L, "browse", "q9-test")
        .getBody();

    assertTrue(html.contains("<title>Moment title</title>"));
    assertTrue(html.contains("content=\"Comment content\""));
    assertTrue(html.contains("content=\"https://order-menu-backend.zsj-unknown.top/images/comment.jpg\""));
    assertFalse(html.contains("content=\"Moment content\""));

    when(comment.imageUrls()).thenReturn(List.of());
    html = new MomentController(service, mock(MomentMediaService.class))
        .shareCard(7L, 2L, "browse", "q9-test")
        .getBody();
    assertTrue(html.contains("content=\"https://order-menu-backend.zsj-unknown.top/images/moment.jpg\""));

    when(moment.imageUrls()).thenReturn(List.of());
    html = new MomentController(service, mock(MomentMediaService.class))
        .shareCard(7L, 2L, "browse", "q9-test")
        .getBody();
    assertTrue(html.contains("content=\"https://moment.zsj-unknown.top/app_icon.png\""));
  }
}
