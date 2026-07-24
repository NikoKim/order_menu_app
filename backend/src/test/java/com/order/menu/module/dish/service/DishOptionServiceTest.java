package com.order.menu.module.dish.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.order.menu.module.dish.entity.DishOptionGroup;
import com.order.menu.module.dish.entity.DishOptionItem;
import com.order.menu.module.dish.mapper.DishOptionGroupMapper;
import com.order.menu.module.dish.mapper.DishOptionItemMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DishOptionServiceTest {

    @Mock private DishOptionGroupMapper groupMapper;
    @Mock private DishOptionItemMapper itemMapper;
    private DishOptionService service;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                DishOptionGroup.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                DishOptionItem.class);
        service = new DishOptionService(groupMapper, itemMapper);
    }

    @Test
    void loadsGroupsAndItemsInTwoQueriesForAnEntireMenu() {
        DishOptionGroup size = group(11L, 101L, "Size", 0);
        DishOptionGroup sugar = group(12L, 102L, "Sugar", 0);
        when(groupMapper.selectList(any())).thenReturn(List.of(size, sugar));
        when(itemMapper.selectList(any())).thenReturn(List.of(
                item(21L, 11L, "Large"), item(22L, 12L, "Less")));

        var result = service.groupsByDishIds(List.of(101L, 102L));

        assertThat(result).containsOnlyKeys(101L, 102L);
        assertThat(result.get(101L).get(0).options()).extracting("name").containsExactly("Large");
        assertThat(result.get(102L).get(0).options()).extracting("name").containsExactly("Less");
        verify(groupMapper, times(1)).selectList(any());
        verify(itemMapper, times(1)).selectList(any());
    }

    private static DishOptionGroup group(Long id, Long dishId, String name, int sortOrder) {
        DishOptionGroup group = new DishOptionGroup();
        group.setId(id);
        group.setDishId(dishId);
        group.setName(name);
        group.setRequired(0);
        group.setSelectionType("SINGLE");
        group.setMinSelect(0);
        group.setMaxSelect(1);
        group.setSortOrder(sortOrder);
        return group;
    }

    private static DishOptionItem item(Long id, Long groupId, String name) {
        DishOptionItem item = new DishOptionItem();
        item.setId(id);
        item.setGroupId(groupId);
        item.setName(name);
        item.setPriceDelta(BigDecimal.ZERO);
        item.setSortOrder(0);
        return item;
    }
}
