package com.order.menu.module.family.controller;

import com.order.menu.common.response.ApiResponse;
import com.order.menu.module.family.dto.FamilyDtos;
import com.order.menu.module.family.service.FamilyService;
import com.order.menu.security.UserContext;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/shop/family")
@RequiredArgsConstructor
public class ShopFamilyController {
    private final FamilyService familyService;

    @GetMapping("/members")
    public ApiResponse<List<FamilyDtos.FamilyMemberResponse>> members(@RequestParam(required = false) String status) {
        return ApiResponse.success(familyService.shopMembers(UserContext.get().userId(), status));
    }

    @PutMapping("/members/{memberId}/approve")
    public ApiResponse<FamilyDtos.FamilyMemberResponse> approve(@PathVariable Long memberId) {
        return ApiResponse.success(familyService.approve(UserContext.get().userId(), memberId));
    }

    @DeleteMapping("/members/{memberId}")
    public ApiResponse<Void> reject(@PathVariable Long memberId) {
        familyService.reject(UserContext.get().userId(), memberId);
        return ApiResponse.success();
    }
}
