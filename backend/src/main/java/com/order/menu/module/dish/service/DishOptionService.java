package com.order.menu.module.dish.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.order.menu.module.dish.dto.DishDtos;
import com.order.menu.module.dish.entity.DishOptionGroup;
import com.order.menu.module.dish.entity.DishOptionItem;
import com.order.menu.module.dish.mapper.DishOptionGroupMapper;
import com.order.menu.module.dish.mapper.DishOptionItemMapper;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DishOptionService {
    private final DishOptionGroupMapper groupMapper;
    private final DishOptionItemMapper itemMapper;

    public List<DishDtos.OptionGroupResponse> groups(Long dishId) {
        return groupsByDishIds(List.of(dishId)).getOrDefault(dishId, List.of());
    }

    public Map<Long, List<DishDtos.OptionGroupResponse>> groupsByDishIds(Collection<Long> dishIds) {
        if (dishIds == null || dishIds.isEmpty()) {
            return Collections.emptyMap();
        }

        List<Long> ids = dishIds.stream().distinct().toList();
        List<DishOptionGroup> groups = groupMapper.selectList(new LambdaQueryWrapper<DishOptionGroup>()
                        .in(DishOptionGroup::getDishId, ids)
                        .orderByAsc(DishOptionGroup::getDishId)
                        .orderByAsc(DishOptionGroup::getSortOrder)
                        .orderByAsc(DishOptionGroup::getId));
        if (groups.isEmpty()) {
            return Collections.emptyMap();
        }

        List<Long> groupIds = groups.stream().map(DishOptionGroup::getId).toList();
        Map<Long, List<DishOptionItem>> itemsByGroup = itemMapper.selectList(
                        new LambdaQueryWrapper<DishOptionItem>()
                                .in(DishOptionItem::getGroupId, groupIds)
                                .orderByAsc(DishOptionItem::getGroupId)
                                .orderByAsc(DishOptionItem::getSortOrder)
                                .orderByAsc(DishOptionItem::getId))
                .stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        DishOptionItem::getGroupId,
                        LinkedHashMap::new,
                        java.util.stream.Collectors.toList()));

        Map<Long, List<DishDtos.OptionGroupResponse>> result = new HashMap<>();
        for (DishOptionGroup group : groups) {
            List<DishDtos.OptionItemResponse> items = itemsByGroup
                    .getOrDefault(group.getId(), List.of())
                    .stream()
                    .map(item -> new DishDtos.OptionItemResponse(
                            item.getId(), item.getName(), item.getPriceDelta(), item.getSortOrder()))
                    .toList();
            result.computeIfAbsent(group.getDishId(), ignored -> new java.util.ArrayList<>())
                    .add(new DishDtos.OptionGroupResponse(
                        group.getId(),
                        group.getName(),
                        group.getRequired() != null && group.getRequired() == 1,
                        group.getSelectionType() == null ? "SINGLE" : group.getSelectionType(),
                        group.getMinSelect() == null ? (group.getRequired() != null && group.getRequired() == 1 ? 1 : 0) : group.getMinSelect(),
                        group.getMaxSelect(),
                        group.getSortOrder(),
                        items));
        }
        return result;
    }

    public void replace(Long dishId, List<DishDtos.OptionGroupRequest> groups) {
        List<DishOptionGroup> oldGroups = groupMapper.selectList(new LambdaQueryWrapper<DishOptionGroup>()
                .eq(DishOptionGroup::getDishId, dishId));
        for (DishOptionGroup oldGroup : oldGroups) {
            itemMapper.delete(new LambdaQueryWrapper<DishOptionItem>().eq(DishOptionItem::getGroupId, oldGroup.getId()));
        }
        groupMapper.delete(new LambdaQueryWrapper<DishOptionGroup>().eq(DishOptionGroup::getDishId, dishId));
        if (groups == null) {
            return;
        }
        int groupIndex = 0;
        for (DishDtos.OptionGroupRequest groupRequest : groups) {
            if (groupRequest.options() == null || groupRequest.options().isEmpty()) {
                continue;
            }
            DishOptionGroup group = new DishOptionGroup();
            group.setDishId(dishId);
            group.setName(groupRequest.name().trim());
            String selectionType = "MULTIPLE".equals(groupRequest.selectionType()) ? "MULTIPLE" : "SINGLE";
            int minSelect = groupRequest.minSelect() == null ? (groupRequest.required() == Boolean.TRUE ? 1 : 0) : Math.max(0, groupRequest.minSelect());
            Integer maxSelect = groupRequest.maxSelect();
            if ("SINGLE".equals(selectionType)) {
                maxSelect = 1;
                minSelect = Math.min(minSelect, 1);
            } else if (maxSelect != null && maxSelect < minSelect) {
                maxSelect = minSelect;
            }
            group.setRequired(minSelect > 0 ? 1 : 0);
            group.setSelectionType(selectionType);
            group.setMinSelect(minSelect);
            group.setMaxSelect(maxSelect);
            group.setSortOrder(groupRequest.sortOrder() == null ? groupIndex : groupRequest.sortOrder());
            groupMapper.insert(group);
            int itemIndex = 0;
            for (DishDtos.OptionItemRequest itemRequest : groupRequest.options()) {
                DishOptionItem item = new DishOptionItem();
                item.setGroupId(group.getId());
                item.setName(itemRequest.name().trim());
                item.setPriceDelta(itemRequest.priceDelta());
                item.setSortOrder(itemRequest.sortOrder() == null ? itemIndex : itemRequest.sortOrder());
                itemMapper.insert(item);
                itemIndex++;
            }
            groupIndex++;
        }
    }
}
