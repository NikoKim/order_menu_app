package com.order.menu.module.family.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.order.menu.common.exception.BusinessException;
import com.order.menu.common.exception.ErrorCode;
import com.order.menu.module.family.dto.FamilyDtos;
import com.order.menu.module.family.entity.FamilyMember;
import com.order.menu.module.family.mapper.FamilyMemberMapper;
import com.order.menu.module.message.service.MessageService;
import com.order.menu.module.shop.entity.Shop;
import com.order.menu.module.shop.mapper.ShopMapper;
import com.order.menu.module.shop.service.ShopService;
import com.order.menu.module.user.entity.User;
import com.order.menu.module.user.mapper.UserMapper;
import java.util.List;

import com.order.menu.websocket.WsPushService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class FamilyService {
    private final FamilyMemberMapper familyMemberMapper;
    private final ShopMapper shopMapper;
    private final ShopService shopService;
    private final UserMapper userMapper;
    private final WsPushService wsPushService;
    private final MessageService messageService;

    public FamilyDtos.MyFamiliesResponse myFamilies(Long userId) {
        List<FamilyDtos.FamilyResponse> families = familyMemberMapper.selectList(new LambdaQueryWrapper<FamilyMember>()
                        .eq(FamilyMember::getUserId, userId)
                        .orderByDesc(FamilyMember::getCreatedAt))
                .stream()
                .map(this::toResponse)
                .toList();
        return new FamilyDtos.MyFamiliesResponse(families);
    }

    public FamilyDtos.FamilyResponse join(Long userId, String familyCode) {
        Shop shop = shopMapper.selectOne(new LambdaQueryWrapper<Shop>()
                .eq(Shop::getFamilyCode, familyCode.trim().toUpperCase())
                .eq(Shop::getVisibilityType, "FAMILY")
                .eq(Shop::getStatus, 1)
                .last("limit 1"));
        if (shop == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "家庭不存在");
        }
        String joinMode = shop.getFamilyJoinMode() == null ? "OPEN" : shop.getFamilyJoinMode();
        if ("CLOSED".equals(joinMode)) {
            throw new BusinessException(ErrorCode.CONFLICT, "当前家庭不支持新用户加入");
        }
        FamilyMember member = familyMemberMapper.selectOne(new LambdaQueryWrapper<FamilyMember>()
                .eq(FamilyMember::getShopId, shop.getId())
                .eq(FamilyMember::getUserId, userId)
                .last("limit 1"));
        if (member == null) {
            member = new FamilyMember();
            member.setShopId(shop.getId());
            member.setUserId(userId);
        }
        member.setStatus("APPROVAL".equals(joinMode) ? "PENDING" : "APPROVED");
        if (member.getId() == null) {
            familyMemberMapper.insert(member);
        } else {
            familyMemberMapper.updateById(member);
        }

        if ("PENDING".equals(member.getStatus())) {
            wsPushService.pushFamilyMemberApplied(member);
            messageService.createShopNotification(member.getShopId(), "FAMILY", "家庭新成员加入申请",
                    "一位顾客请求加入你的家庭，请前去家庭管理页面查看", familyPayload(member));
        } else if ("APPROVED".equals(member.getStatus())) {
            wsPushService.pushFamilyMemberApproved(member);
            messageService.createNotification(member.getUserId(), "FAMILY", "已加入家庭",
                    "你已加入该家庭", familyPayload(member));
            messageService.onFamilyMemberApproved(member);
        }

        return toResponse(member);
    }

    public void leave(Long userId, Long shopId) {
        int deleted = familyMemberMapper.delete(new LambdaQueryWrapper<FamilyMember>()
                .eq(FamilyMember::getUserId, userId)
                .eq(FamilyMember::getShopId, shopId));
        if (deleted > 0) {
            messageService.onFamilyMemberLeft(shopId, userId);
        }
    }

    public boolean isApprovedMember(Long userId, Long shopId) {
        return familyMemberMapper.selectCount(new LambdaQueryWrapper<FamilyMember>()
                .eq(FamilyMember::getUserId, userId)
                .eq(FamilyMember::getShopId, shopId)
                .eq(FamilyMember::getStatus, "APPROVED")) > 0;
    }

    public List<FamilyDtos.FamilyMemberResponse> shopMembers(Long ownerUserId, String status) {
        Shop shop = shopService.requireOwnedShop(ownerUserId);
        String safeStatus = status == null || status.isBlank() ? null : status.trim();
        return familyMemberMapper.selectList(new LambdaQueryWrapper<FamilyMember>()
                        .eq(FamilyMember::getShopId, shop.getId())
                        .eq(safeStatus != null, FamilyMember::getStatus, safeStatus)
                        .orderByDesc(FamilyMember::getCreatedAt))
                .stream()
                .map(this::toMemberResponse)
                .toList();
    }

    public FamilyDtos.FamilyMemberResponse approve(Long ownerUserId, Long memberId) {
        FamilyMember member = requireOwnedMember(ownerUserId, memberId);
        member.setStatus("APPROVED");
        familyMemberMapper.updateById(member);
        wsPushService.pushFamilyMemberApproved(member);
        messageService.createNotification(member.getUserId(), "FAMILY", "家庭申请已通过",
                "你的家庭申请已通过，已加入家庭", familyPayload(member));
        messageService.onFamilyMemberApproved(member);
        return toMemberResponse(member);
    }

    public void reject(Long ownerUserId, Long memberId) {
        FamilyMember member = requireOwnedMember(ownerUserId, memberId);
        Long deletedMemberId = member.getId();
        Long shopId = member.getShopId();
        Long userId = member.getUserId();
        familyMemberMapper.deleteById(member.getId());
        messageService.onFamilyMemberLeft(shopId, userId);
        wsPushService.pushFamilyMemberRejected(deletedMemberId, shopId, userId);
        messageService.createNotification(userId, "FAMILY", "家庭申请被拒绝",
                "你的加入家庭申请被拒绝", "{\"memberId\":" + deletedMemberId + ",\"shopId\":" + shopId + "}");
    }

    private FamilyMember requireOwnedMember(Long ownerUserId, Long memberId) {
        Shop shop = shopService.requireOwnedShop(ownerUserId);
        FamilyMember member = familyMemberMapper.selectById(memberId);
        if (member == null || !shop.getId().equals(member.getShopId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "家庭申请不存在");
        }
        return member;
    }

    private FamilyDtos.FamilyResponse toResponse(FamilyMember member) {
        Shop shop = shopMapper.selectById(member.getShopId());
        return new FamilyDtos.FamilyResponse(
                member.getShopId(),
                shop == null ? null : shop.getName(),
                shop == null ? null : shop.getFamilyName(),
                shop == null ? null : shop.getFamilyCode(),
                shop == null ? null : shop.getFamilyImageUrl(),
                shop == null ? null : shop.getFamilyJoinMode(),
                member.getStatus());
    }

    private FamilyDtos.FamilyMemberResponse toMemberResponse(FamilyMember member) {
        User user = userMapper.selectById(member.getUserId());
        return new FamilyDtos.FamilyMemberResponse(
                member.getId(),
                member.getUserId(),
                user == null ? null : user.getNickname(),
                user == null ? null : user.getPhone(),
                user == null ? null : user.getAvatarUrl(),
                member.getStatus());
    }

    private String familyPayload(FamilyMember member) {
        return "{\"memberId\":" + member.getId() + ",\"shopId\":" + member.getShopId() + "}";
    }
}
