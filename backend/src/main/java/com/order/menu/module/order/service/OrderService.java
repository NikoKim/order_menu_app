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
import java.util.concurrent.atomic.AtomicReference;

import com.order.menu.module.shop.service.cache.MenuCacheInvalidator;
import com.order.menu.websocket.WsPushService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
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
    private final MenuCacheInvalidator menuCacheInvalidator;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private IdempotencyService idempotencyService;

    @Transactional
    public OrderDtos.OrderSubmitResponse create(Long customerUserId, OrderDtos.CreateOrderRequest request) {
        Shop shop = shopService.requireById(request.shopId());
        if (!StatusConstants.BUSINESS_OPEN.equals(shop.getBusinessStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "Shop is closed");
        }
        BigDecimal total = BigDecimal.ZERO;
        List<OrderLine> lines = new ArrayList<>();
        // Sort by dishId to reduce deadlock risk under concurrent orders.
        List<OrderDtos.CreateOrderItemRequest> sortedItems = new ArrayList<>(request.items());
        sortedItems.sort(java.util.Comparator.comparing(OrderDtos.CreateOrderItemRequest::dishId));
        for (OrderDtos.CreateOrderItemRequest item : sortedItems) {
            Dish dish = requireOnSaleDish(request.shopId(), item.dishId());
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
        order.setClientRequestId(request.clientRequestId());

        // Redis idempotency check before DB insert.
        IdempotencyService.AcquireResult acquireResult = null;
        AtomicReference<Long> idempotencyOrderId = new AtomicReference<>();
        boolean idempotencyCompletionRegistered = false;
        if (idempotencyService != null) {
            acquireResult = idempotencyService.acquire(customerUserId, request.clientRequestId());
            if (acquireResult.type() == IdempotencyService.AcquireResult.Type.EXISTING) {
                Order existing = orderMapper.selectById(acquireResult.existingOrderId());
                if (existing != null) {
                    return new OrderDtos.OrderSubmitResponse(existing.getId(), existing.getOrderNo(),
                            existing.getStatus(), existing.getTotalAmount());
                }
            } else if (acquireResult.type() == IdempotencyService.AcquireResult.Type.PENDING) {
                Order existing = orderMapper.selectByCustomerAndClientRequestId(
                        customerUserId, request.clientRequestId());
                if (existing != null) {
                    idempotencyService.markSuccess(customerUserId, request.clientRequestId(), existing.getId());
                    return new OrderDtos.OrderSubmitResponse(existing.getId(), existing.getOrderNo(),
                            existing.getStatus(), existing.getTotalAmount());
                }
                throw new BusinessException(ErrorCode.CONFLICT, "ORDER_CREATING");
            } else if (acquireResult.type() == IdempotencyService.AcquireResult.Type.ACQUIRED) {
                idempotencyCompletionRegistered = registerIdempotencyCompletion(
                        customerUserId, request.clientRequestId(), acquireResult.pendingToken(), idempotencyOrderId);
            }
        }

        try {
            orderMapper.insert(order);
        } catch (org.springframework.dao.DuplicateKeyException exception) {
            Order existing = orderMapper.selectByCustomerAndClientRequestId(
                    customerUserId, request.clientRequestId());
            if (existing != null) {
                if (acquireResult != null && acquireResult.type() == IdempotencyService.AcquireResult.Type.ACQUIRED) {
                    idempotencyOrderId.set(existing.getId());
                } else if (idempotencyService != null) {
                    idempotencyService.markSuccess(customerUserId, request.clientRequestId(), existing.getId());
                }
                return new OrderDtos.OrderSubmitResponse(existing.getId(), existing.getOrderNo(),
                        existing.getStatus(), existing.getTotalAmount());
            }
            if (idempotencyService != null && acquireResult != null
                    && acquireResult.type() == IdempotencyService.AcquireResult.Type.ACQUIRED
                    && !idempotencyCompletionRegistered) {
                idempotencyService.releasePending(
                        customerUserId, request.clientRequestId(), acquireResult.pendingToken());
            }
            throw exception;
        }

        for (OrderLine line : lines) {
            Dish dish = line.dish();
            int affected = dishMapper.deductStock(dish.getId(), line.quantity());
            if (affected == 0 && dish.getStock() != null) {
                throw new BusinessException(ErrorCode.CONFLICT,
                        "Insufficient stock for dish: " + dish.getName());
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
        messageService.createShopNotification(order.getShopId(), "ORDER", "收到新订单",
                "订单 " + order.getOrderNo() + " 已提交", orderPayload(order));

        menuCacheInvalidator.evictMenuAfterCommit(order.getShopId());

        if (idempotencyService != null && acquireResult != null
                && acquireResult.type() == IdempotencyService.AcquireResult.Type.ACQUIRED) {
            idempotencyOrderId.set(order.getId());
            if (!idempotencyCompletionRegistered) {
                idempotencyService.markSuccess(customerUserId, request.clientRequestId(), order.getId(),
                        acquireResult.pendingToken());
            }
        }

        return new OrderDtos.OrderSubmitResponse(order.getId(), order.getOrderNo(), order.getStatus(), order.getTotalAmount());
    }

    private boolean registerIdempotencyCompletion(Long userId, String clientRequestId, String pendingToken,
                                                   AtomicReference<Long> orderId) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) return false;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                Long committedOrderId = orderId.get();
                if (committedOrderId != null) {
                    idempotencyService.markSuccess(userId, clientRequestId, committedOrderId, pendingToken);
                }
            }

            @Override
            public void afterCompletion(int status) {
                if (status != TransactionSynchronization.STATUS_COMMITTED) {
                    idempotencyService.releasePending(userId, clientRequestId, pendingToken);
                }
            }
        });
        return true;
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

        int changed = orderMapper.changeToCancelledIfAllowed(orderId, StatusConstants.ORDER_CANCELLED);
        if (changed == 0) {
            return;
        }

        restoreStock(order);

        menuCacheInvalidator.evictMenuAfterCommit(order.getShopId());

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
        notifyCustomerOrderStatus(order, "订单已接单");
    }

    @Transactional
    public void reject(Long ownerUserId, Long orderId) {
        Order order = requireOwnedOrder(ownerUserId, orderId);

        int changed = orderMapper.changeToCancelledIfAllowed(orderId, StatusConstants.ORDER_REJECTED);
        if (changed == 0) {
            return;
        }

        restoreStock(order);

        menuCacheInvalidator.evictMenuAfterCommit(order.getShopId());

        wsPushService.pushOrderRejected(order);
        notifyCustomerOrderStatus(order, "订单已拒绝");
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
        notifyCustomerOrderStatus(order, "订单已完成");
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
        items.stream()
                .sorted(java.util.Comparator.comparing(OrderItem::getDishId))
                .forEach(item -> {
                    int restored = dishMapper.restoreStock(item.getDishId(), item.getQuantity());
                    if (restored == 0) {
                        throw new BusinessException(ErrorCode.NOT_FOUND, "恢复库存失败，菜品不存在");
                    }
                });
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
                "订单 " + order.getOrderNo() + " 状态已更新为：" + orderStatusLabel(order.getStatus()), orderPayload(order));
    }

    private String orderStatusLabel(String status) {
        return switch (status) {
            case "PENDING" -> "待处理";
            case "ACCEPTED" -> "已接单";
            case "REJECTED" -> "已拒绝";
            case "COMPLETED" -> "已完成";
            case "CANCELED" -> "已取消";
            default -> status;
        };
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
