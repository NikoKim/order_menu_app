package com.order.menu.module.family.controller;

import com.order.menu.common.response.ApiResponse;
import com.order.menu.module.family.dto.FamilyDtos;
import com.order.menu.module.family.service.FamilyService;
import com.order.menu.security.UserContext;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/families")
@RequiredArgsConstructor
public class FamilyController {
    private final FamilyService familyService;

    @GetMapping("/my")
    public ApiResponse<FamilyDtos.MyFamiliesResponse> myFamilies() {
        return ApiResponse.success(familyService.myFamilies(UserContext.get().userId()));
    }

    @PostMapping("/join")
    public ApiResponse<FamilyDtos.FamilyResponse> join(@Valid @RequestBody FamilyDtos.JoinFamilyRequest request) {
        return ApiResponse.success(familyService.join(UserContext.get().userId(), request.familyCode()));
    }

    @DeleteMapping("/{shopId}")
    public ApiResponse<Void> leave(@PathVariable Long shopId) {
        familyService.leave(UserContext.get().userId(), shopId);
        return ApiResponse.success();
    }
}
