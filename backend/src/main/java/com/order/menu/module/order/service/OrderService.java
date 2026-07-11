package com.order.menu.module.order.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.order.menu.common.constant.StatusConstants;
import com.order.menu.common.exception.BusinessException;
import com.order.menu.common.exception.ErrorCode;
import com.order.menu.common.response.PageResponse;
import com.order.menu.common.util.OrderNoUtils;
import com.order.menu.module.dish.entity.Dish;
import com.order.menu.module.dish.entity.DishOptionGroup;
import com.order.menu.module.dish.entity.DishOptionItem;
import com.order.menu.module.dish.mapper.DishMapper;
import com.order.menu.module.dish.mapper.DishOptionGroupMapper;
import com.order.menu.module.dish.mapper.DishOptionItemMapper;
import com.order.menu.module.family.service.FamilyService;
import com.order.menu.module.message.service.MessageService;
import com.order.menu.module.order.dto.OrderDtos;
import com.order.menu.module.order.entity.Order;
import com.order.menu.module.order.entity.OrderItem;
import com.order.menu.module.order.mapper.OrderItemMapper;
import com.order.menu.module.order.mapper.OrderMapper;
import com.order.menu.module.shop.entity.Shop;
import com.order.menu.module.shop.mapper.ShopMapper;
import com.order.menu.module.shop.service.ShopService;
import com.order.menu.module.user.entity.User;
import com.order.menu.module.user.mapper.UserMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.order.menu.websocket.WsPushService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class OrderService {
    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final DishMapper dishMapper;
    private final ShopMapper shopMapper;
    private final ShopService shopService;
    private final UserMapper userMapper;
    private final DishOptionGroupMapper dishOptionGroupMapper;
    private final DishOptionItemMapper dishOptionItemMapper;
    private final FamilyService familyService;
    private final WsPushService wsPushService;
    private final MessageService messageService;

    @Transactional
    public OrderDtos.OrderSubmitResponse create(Long customerUserId, OrderDtos.CreateOrderRequest request) {
        Shop shop = shopService.requireById(request.shopId());
        if (!StatusConstants.BUSINESS_OPEN.equals(shop.getBusinessStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "Shop is closed");
        }
        BigDecimal total = BigDecimal.ZERO;
        List<OrderLine> lines = new ArrayList<>();
        for (OrderDtos.CreateOrderItemRequest item : request.items()) {
            Dish dish = requireOnSaleDish(request.shopId(), item.dishId());
            if (dish.getStock() != null && dish.getStock() < item.quantity()) {
                throw new BusinessException(ErrorCode.CONFLICT, "Insufficient dish stock");
            }
            OptionSelection optionSelection = resolveOptions(dish.getId(), item.optionItemIds());
            BigDecimal unitPrice = dish.getPrice().add(optionSelection.amount());
            BigDecimal subtotal = unitPrice.multiply(BigDecimal.valueOf(item.quantity()));
            total = total.add(subtotal);
            lines.add(new OrderLine(dish, item.quantity(), optionSelection, unitPrice, subtotal));
        }
        Order order = new Order();
        order.setOrderNo(OrderNoUtils.next());
        order.setCustomerUserId(customerUserId);
        order.setShopId(request.shopId());
        order.setTotalAmount(total);
        order.setRemark(trimToNull(request.remark()));
        order.setStatus(StatusConstants.ORDER_SUBMITTED);
        orderMapper.insert(order);

        for (OrderLine line : lines) {
            Dish dish = line.dish();
            if (dish.getStock() != null) {
                dish.setStock(dish.getStock() - line.quantity());
                dishMapper.updateById(dish);
            }
            OrderItem orderItem = new OrderItem();
            orderItem.setOrderId(order.getId());
            orderItem.setDishId(dish.getId());
            orderItem.setDishName(dish.getName());
            orderItem.setDishImageUrl(dish.getImageUrl());
            orderItem.setUnitPrice(line.unitPrice());
            orderItem.setQuantity(line.quantity());
            orderItem.setSubtotalAmount(line.subtotal());
            orderItem.setSelectedOptions(line.optionSelection().label());
            orderItem.setOptionAmount(line.optionSelection().amount());
            orderItemMapper.insert(orderItem);
        }

        wsPushService.pushOrderCreated(order);
        messageService.createShopNotification(order.getShopId(), "ORDER", "New order",
                "Order " + order.getOrderNo() + " was submitted", orderPayload(order));

        return new OrderDtos.OrderSubmitResponse(order.getId(), order.getOrderNo(), order.getStatus(), order.getTotalAmount());
    }

    public PageResponse<OrderDtos.OrderSummaryResponse> myOrders(Long customerUserId, int page, int pageSize, String status) {
        String normalizedStatus = normalizeOrderStatus(status);
        Page<Order> result = orderMapper.selectPage(new Page<>(page, pageSize), new LambdaQueryWrapper<Order>()
                .eq(Order::getCustomerUserId, customerUserId)
                .eq(StringUtils.hasText(normalizedStatus), Order::getStatus, normalizedStatus)
                .orderByDesc(Order::getCreatedAt));
        return new PageResponse<>(result.getRecords().stream().map(this::toSummary).toList(), page, pageSize, result.getTotal());
    }

    public OrderDtos.OrderDetailResponse customerDetail(Long customerUserId, Long orderId) {
        Order order = requireOrder(orderId);
        if (!customerUserId.equals(order.getCustomerUserId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "No permission to access this order");
        }
        return toDetail(order);
    }

    @Transactional
    public void cancel(Long customerUserId, Long orderId) {
        Order order = requireOrder(orderId);

        if (!customerUserId.equals(order.getCustomerUserId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权限取消该订单，请反馈商家。");
        }

        if (!StatusConstants.ORDER_SUBMITTED.equals(order.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "当前订单订单状态无法被取消。");
        }

        order.setStatus(StatusConstants.ORDER_CANCELLED);
        orderMapper.updateById(order);

        restoreStock(order);

        wsPushService.pushOrderCancelled(order);
    }

    public PageResponse<OrderDtos.OrderSummaryResponse> shopOrders(Long ownerUserId, int page, int pageSize, String status, LocalDate date) {
        Shop shop = shopService.requireOwnedShop(ownerUserId);
        String normalizedStatus = normalizeOrderStatus(status);
        Page<Order> result = orderMapper.selectPage(new Page<>(page, pageSize), new LambdaQueryWrapper<Order>()
                .eq(Order::getShopId, shop.getId())
                .eq(StringUtils.hasText(normalizedStatus), Order::getStatus, normalizedStatus)
                .ge(date != null, Order::getCreatedAt, date == null ? null : date.atStartOfDay())
                .lt(date != null, Order::getCreatedAt, date == null ? null : date.plusDays(1).atStartOfDay())
                .orderByDesc(Order::getCreatedAt));
        return new PageResponse<>(result.getRecords().stream().map(this::toSummary).toList(), page, pageSize, result.getTotal());
    }

    public OrderDtos.OrderDetailResponse shopDetail(Long ownerUserId, Long orderId) {
        Order order = requireOwnedOrder(ownerUserId, orderId);
        return toDetail(order);
    }

    public void accept(Long ownerUserId, Long orderId) {
        Order order = requireOwnedOrder(ownerUserId, orderId);

        if (!StatusConstants.ORDER_SUBMITTED.equals(order.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "当前订单状态无法被处理。");
        }

        order.setStatus(StatusConstants.ORDER_ACCEPTED);
        orderMapper.updateById(order);

        wsPushService.pushOrderAccepted(order);
        notifyCustomerOrderStatus(order, "Order accepted");
    }

    @Transactional
    public void reject(Long ownerUserId, Long orderId) {
        Order order = requireOwnedOrder(ownerUserId, orderId);

        if (!StatusConstants.ORDER_SUBMITTED.equals(order.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "当前订单状态无法被拒绝。");
        }

        order.setStatus(StatusConstants.ORDER_REJECTED);
        orderMapper.updateById(order);

        restoreStock(order);

        wsPushService.pushOrderRejected(order);
        notifyCustomerOrderStatus(order, "Order rejected");
    }

    public void complete(Long ownerUserId, Long orderId) {
        Order order = requireOwnedOrder(ownerUserId, orderId);
        if (!StatusConstants.ORDER_ACCEPTED.equals(order.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "当前订单状态无法被完成。");
        }
        order.setStatus(StatusConstants.ORDER_COMPLETED);
        orderMapper.updateById(order);
        List<OrderItem> items = orderItemMapper.selectList(new LambdaQueryWrapper<OrderItem>()
                .eq(OrderItem::getOrderId, order.getId()));
        for (OrderItem item : items) {
            Dish dish = dishMapper.selectById(item.getDishId());
            if (dish != null) {
                int currentSales = dish.getSalesCount() == null ? 0 : dish.getSalesCount();
                int quantity = item.getQuantity() == null ? 0 : item.getQuantity();
                dish.setSalesCount(currentSales + quantity);
                dishMapper.updateById(dish);
            }
        }

        wsPushService.pushOrderCompleted(order);
        notifyCustomerOrderStatus(order, "Order completed");
    }

    private Dish requireOnSaleDish(Long shopId, Long dishId) {
        Dish dish = dishMapper.selectById(dishId);
        if (dish == null || !shopId.equals(dish.getShopId()) || !StatusConstants.DISH_ON_SALE.equals(dish.getStatus())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Dish not found or off sale");
        }
        return dish;
    }

    private OptionSelection resolveOptions(Long dishId, List<Long> optionItemIds) {
        List<Long> safeIds = optionItemIds == null ? List.of() : optionItemIds.stream().distinct().toList();
        List<DishOptionGroup> groups = dishOptionGroupMapper.selectList(new LambdaQueryWrapper<DishOptionGroup>()
                .eq(DishOptionGroup::getDishId, dishId)
                .orderByAsc(DishOptionGroup::getSortOrder)
                .orderByAsc(DishOptionGroup::getId));
        if (groups.isEmpty() && safeIds.isEmpty()) {
            return new OptionSelection(BigDecimal.ZERO, null);
        }

        BigDecimal amount = BigDecimal.ZERO;
        List<String> labels = new ArrayList<>();
        List<DishOptionItem> selectedItems = new ArrayList<>();
        for (Long optionItemId : safeIds) {
            DishOptionItem item = dishOptionItemMapper.selectById(optionItemId);
            if (item == null) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "Invalid dish option");
            }
            DishOptionGroup group = dishOptionGroupMapper.selectById(item.getGroupId());
            if (group == null || !dishId.equals(group.getDishId())) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "Invalid dish option");
            }
            selectedItems.add(item);
        }

        Map<Long, List<DishOptionItem>> selectedByGroup = selectedItems.stream()
                .collect(Collectors.groupingBy(DishOptionItem::getGroupId));
        for (DishOptionGroup group : groups) {
            List<DishOptionItem> groupItems = selectedByGroup.getOrDefault(group.getId(), List.of());
            int count = groupItems.size();
            int minSelect = group.getMinSelect() == null ? (group.getRequired() != null && group.getRequired() == 1 ? 1 : 0) : group.getMinSelect();
            Integer maxSelect = group.getMaxSelect();
            String selectionType = group.getSelectionType() == null ? "SINGLE" : group.getSelectionType();
            if ("SINGLE".equals(selectionType)) {
                maxSelect = 1;
            }
            if (count < minSelect) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "Missing required dish option");
            }
            if (maxSelect != null && count > maxSelect) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "Too many dish options selected");
            }
            for (DishOptionItem item : groupItems) {
                amount = amount.add(item.getPriceDelta());
                labels.add(group.getName() + ":" + item.getName());
            }
        }
        return new OptionSelection(amount, labels.isEmpty() ? null : String.join("; ", labels));
    }

    private void restoreStock(Order order) {
        List<OrderItem> items = orderItemMapper.selectList(new LambdaQueryWrapper<OrderItem>()
                .eq(OrderItem::getOrderId, order.getId()));
        for (OrderItem item : items) {
            Dish dish = dishMapper.selectById(item.getDishId());
            if (dish != null && dish.getStock() != null) {
                int quantity = item.getQuantity() == null ? 0 : item.getQuantity();
                dish.setStock(dish.getStock() + quantity);
                dishMapper.updateById(dish);
            }
        }
    }

    private Order requireOrder(Long orderId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Order not found");
        }
        return order;
    }

    private Order requireOwnedOrder(Long ownerUserId, Long orderId) {
        Shop shop = shopService.requireOwnedShop(ownerUserId);
        Order order = requireOrder(orderId);
        if (!shop.getId().equals(order.getShopId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "No permission to access this order");
        }
        return order;
    }

    private String normalizeOrderStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return null;
        }
        String normalized = status.trim();
        if (!StatusConstants.isOrderStatus(normalized)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Invalid order status");
        }
        return normalized;
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private void notifyCustomerOrderStatus(Order order, String title) {
        messageService.createNotification(order.getCustomerUserId(), "ORDER", title,
                "Order " + order.getOrderNo() + " status: " + order.getStatus(), orderPayload(order));
    }

    private String orderPayload(Order order) {
        return "{\"orderId\":" + order.getId() + ",\"shopId\":" + order.getShopId() + "}";
    }

    private OrderDtos.OrderSummaryResponse toSummary(Order order) {
        Shop shop = shopMapper.selectById(order.getShopId());
        return new OrderDtos.OrderSummaryResponse(order.getId(), order.getOrderNo(), order.getShopId(),
                shop == null ? null : shop.getName(), order.getStatus(), order.getTotalAmount(), order.getCreatedAt());
    }

    private OrderDtos.OrderDetailResponse toDetail(Order order) {
        Shop shop = shopMapper.selectById(order.getShopId());
        User customer = userMapper.selectById(order.getCustomerUserId());
        List<OrderDtos.OrderItemResponse> items = orderItemMapper.selectList(new LambdaQueryWrapper<OrderItem>()
                        .eq(OrderItem::getOrderId, order.getId()))
                .stream()
                .map(item -> new OrderDtos.OrderItemResponse(item.getDishId(), item.getDishName(), item.getDishImageUrl(),
                        item.getUnitPrice(), item.getQuantity(), item.getSubtotalAmount(), item.getSelectedOptions(),
                        item.getOptionAmount()))
                .toList();
        boolean familyCustomer = familyService.isApprovedMember(order.getCustomerUserId(), order.getShopId());
        return new OrderDtos.OrderDetailResponse(order.getId(), order.getOrderNo(), order.getShopId(),
                shop == null ? null : shop.getName(), order.getCustomerUserId(),
                customer == null ? null : customer.getNickname(),
                customer == null ? null : customer.getPhone(),
                customer == null ? null : customer.getAvatarUrl(),
                familyCustomer,
                order.getStatus(), order.getTotalAmount(),
                order.getRemark(), order.getCreatedAt(), items);
    }

    private record OptionSelection(BigDecimal amount, String label) {
    }

    private record OrderLine(Dish dish, Integer quantity, OptionSelection optionSelection,
                             BigDecimal unitPrice, BigDecimal subtotal) {
    }
}
