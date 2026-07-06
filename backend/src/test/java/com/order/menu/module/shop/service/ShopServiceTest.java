package com.order.menu.module.shop.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.order.menu.module.category.mapper.MenuCategoryMapper;
import com.order.menu.module.dish.mapper.DishMapper;
import com.order.menu.module.dish.service.DishOptionService;
import com.order.menu.module.shop.entity.Shop;
import com.order.menu.module.shop.mapper.ShopMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ShopServiceTest {
    @Mock
    private ShopMapper shopMapper;

    @Mock
    private MenuCategoryMapper categoryMapper;

    @Mock
    private DishMapper dishMapper;

    @Mock
    private DishOptionService dishOptionService;

    @Test
    void listAllowsMissingKeyword() {
        Page<Shop> pageResult = new Page<>(1, 10);
        pageResult.setRecords(List.of());
        when(shopMapper.selectPage(any(Page.class), any(Wrapper.class))).thenReturn(pageResult);

        ShopService service = new ShopService(shopMapper, categoryMapper, dishMapper, dishOptionService);

        assertDoesNotThrow(() -> service.list(1, 10, null, null));
    }
}
