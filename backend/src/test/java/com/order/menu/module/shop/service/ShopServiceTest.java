package com.order.menu.module.shop.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.order.menu.module.category.mapper.MenuCategoryMapper;
import com.order.menu.module.dish.mapper.DishMapper;
import com.order.menu.module.dish.service.DishOptionService;
import com.order.menu.module.shop.entity.Shop;
import com.order.menu.module.shop.mapper.ShopMapper;
import java.util.List;

import com.order.menu.websocket.WsPushService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.mockito.ArgumentCaptor;
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
    @Mock
    private WsPushService wsPushService;

    private ShopService service;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                Shop.class
        );
        service = new ShopService(shopMapper, categoryMapper, dishMapper, dishOptionService, wsPushService);
    }

    @Test
    void listAllowsMissingKeyword() {
        Page<Shop> pageResult = new Page<>(1, 10);
        pageResult.setRecords(List.of());
        when(shopMapper.selectPage(any(Page.class), any(Wrapper.class))).thenReturn(pageResult);

        assertDoesNotThrow(() -> service.list(1, 10, null, null));
    }

    @Test
    void listWithoutFamilyCodeIncludesShopsWithoutFamilyMembership() {
        Page<Shop> pageResult = new Page<>(1, 10);
        pageResult.setRecords(List.of());
        when(shopMapper.selectPage(any(Page.class), any(Wrapper.class))).thenReturn(pageResult);

        service.list(1, 10, null, null);

        ArgumentCaptor<Wrapper<Shop>> wrapperCaptor = ArgumentCaptor.forClass(Wrapper.class);
        org.mockito.Mockito.verify(shopMapper).selectPage(any(Page.class), wrapperCaptor.capture());

        assertTrue(wrapperCaptor.getValue().getSqlSegment().contains("family_code IS NULL"));
    }
}
