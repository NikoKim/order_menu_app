package com.navy.identity;

import com.navy.common.response.ApiResponse;
import com.navy.common.exception.BusinessException;
import java.math.BigDecimal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class NotificationController {
    private final NotificationService notifications;
    public record ItemRequest(String itemId,BigDecimal quantity,BigDecimal durationHours) {}
    public record SendRequest(List<Long> userIds, String title, String content, BigDecimal coins, String requestId,List<ItemRequest> items) {}

    @GetMapping("/notifications")
    public ApiResponse<?> list(@RequestParam(required=false) Long beforeId,@RequestParam(defaultValue="50") int limit) {
        return ApiResponse.success(notifications.list(beforeId,limit));
    }
    @GetMapping("/notifications/summary")
    public ApiResponse<?> summary() { return ApiResponse.success(notifications.summary()); }
    @PostMapping("/notifications/{id}/read")
    public ApiResponse<?> read(@PathVariable long id) { notifications.read(id);return ApiResponse.success(); }
    @PostMapping("/notifications/{id}/claim")
    public ApiResponse<?> claim(@PathVariable long id) { return ApiResponse.success(notifications.claim(id)); }
    @GetMapping("/admin/users")
    public ApiResponse<?> users(@RequestParam(required=false) String query) { return ApiResponse.success(notifications.users(query)); }
    @GetMapping("/admin/shop-items")
    public ApiResponse<?> giftItems() { return ApiResponse.success(notifications.giftItems()); }
    @PostMapping("/admin/notifications")
    public ApiResponse<?> send(@RequestBody SendRequest request) {
        int coins;
        try { coins=request.coins()==null ? 0 : request.coins().intValueExact(); }
        catch (ArithmeticException e) { throw new BusinessException(400,"赠送金币必须为0至1000000的整数"); }
        List<NotificationService.ItemGift> items;
        try { items=request.items()==null?List.of():request.items().stream().map(item->{if(item==null||item.quantity()==null||item.durationHours()==null)throw new ArithmeticException();return new NotificationService.ItemGift(item.itemId(),item.quantity().intValueExact(),item.durationHours().intValueExact());}).toList(); }
        catch (ArithmeticException e) { throw new BusinessException(400,"赠送物品数量和有效期须为整数"); }
        return ApiResponse.success(notifications.send(request.userIds(),request.title(),request.content(),coins,request.requestId(),items));
    }
}
