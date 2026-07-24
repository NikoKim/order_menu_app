package com.order.menu.module.order.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.order.menu.common.constant.StatusConstants;
import com.order.menu.module.dish.entity.Dish;
import com.order.menu.module.dish.mapper.DishOptionGroupMapper;
import com.order.menu.module.dish.mapper.DishOptionItemMapper;
import com.order.menu.module.dish.mapper.DishMapper;
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
import com.order.menu.module.shop.service.cache.MenuCacheInvalidator;
import com.order.menu.module.user.entity.User;
import com.order.menu.module.user.mapper.UserMapper;
import java.math.BigDecimal;
import java.util.List;

import com.order.menu.websocket.WsPushService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {
    @Mock
    private OrderMapper orderMapper;
    @Mock
    private OrderItemMapper orderItemMapper;
    @Mock
    private DishMapper dishMapper;
    @Mock
    private ShopMapper shopMapper;
    @Mock
    private ShopService shopService;
    @Mock
    private UserMapper userMapper;
    @Mock
    private DishOptionGroupMapper dishOptionGroupMapper;
    @Mock
    private DishOptionItemMapper dishOptionItemMapper;
    @Mock
    private FamilyService familyService;
    @Mock
    private WsPushService wsPushService;
    @Mock
    private MessageService messageService;
    @Mock
    private MenuCacheInvalidator menuCacheInvalidator;

    @Test
    void completeAddsItemQuantitiesToDishSalesCount() {
        Order order = acceptedOrder();
        OrderItem item = item(9L, 3);
        Dish dish = new Dish();
        dish.setId(9L);
        dish.setSalesCount(4);

        when(shopService.requireOwnedShop(99L)).thenReturn(shop());
        when(orderMapper.selectById(1L)).thenReturn(order);
        when(orderItemMapper.selectList(any(Wrapper.class))).thenReturn(List.of(item));
        when(dishMapper.selectById(9L)).thenReturn(dish);

        service().complete(99L, 1L);

        ArgumentCaptor<Dish> captor = ArgumentCaptor.forClass(Dish.class);
        verify(dishMapper).updateById(captor.capture());
        assertEquals(7, captor.getValue().getSalesCount());
    }

    @Test
    void shopDetailIncludesCustomerProfile() {
        Order order = acceptedOrder();
        User customer = new User();
        customer.setId(7L);
        customer.setNickname("Niko");
        customer.setPhone("13800000000");
        customer.setAvatarUrl("https://example.test/avatar.png");

        when(shopService.requireOwnedShop(99L)).thenReturn(shop());
        when(orderMapper.selectById(1L)).thenReturn(order);
        when(shopMapper.selectById(2L)).thenReturn(shop());
        when(userMapper.selectById(7L)).thenReturn(customer);
        when(orderItemMapper.selectList(any(Wrapper.class))).thenReturn(List.of());
        when(familyService.isApprovedMember(7L, 2L)).thenReturn(true);

        OrderDtos.OrderDetailResponse detail = service().shopDetail(99L, 1L);

        assertEquals("Niko", detail.customerNickname());
        assertEquals("13800000000", detail.customerPhone());
        assertEquals("https://example.test/avatar.png", detail.customerAvatarUrl());
        assertEquals(true, detail.familyCustomer());
    }

    private OrderService service() {
        return new OrderService(orderMapper, orderItemMapper, dishMapper, shopMapper, shopService, userMapper,
                dishOptionGroupMapper, dishOptionItemMapper, familyService, wsPushService, messageService,
                menuCacheInvalidator);
    }

    private Order acceptedOrder() {
        Order order = new Order();
        order.setId(1L);
        order.setOrderNo("NO1");
        order.setCustomerUserId(7L);
        order.setShopId(2L);
        order.setTotalAmount(BigDecimal.TEN);
        order.setStatus(StatusConstants.ORDER_ACCEPTED);
        return order;
    }

    private OrderItem item(Long dishId, int quantity) {
        OrderItem item = new OrderItem();
        item.setDishId(dishId);
        item.setQuantity(quantity);
        return item;
    }

    private Shop shop() {
        Shop shop = new Shop();
        shop.setId(2L);
        shop.setName("Shop");
        return shop;
    }
}
