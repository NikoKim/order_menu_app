package com.order.menu.module.version.controller;


import com.order.menu.common.response.ApiResponse;
import com.order.menu.module.version.dto.VersionDtos;
import com.order.menu.module.version.service.VersionService;
import jakarta.validation.Valid;
import org.hibernate.validator.internal.util.Version;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/version")
public class VersionController {

    // 直接注入 Service 实体类
    @Autowired
    private VersionService versionService;

    @GetMapping("/check")
    public ApiResponse<VersionDtos.VersionCheckResponse> checkUpdate(@RequestParam String appCode, @RequestParam Integer currentVersionCode) {
        return ApiResponse.success(versionService.checkUpdate(appCode, currentVersionCode));
    }

    @PostMapping("/publish")
    public ApiResponse<Void> publishNewVersion(@RequestBody @Valid VersionDtos.VersionSaveRequest request) {
        versionService.publishNewVersion(request);
        return ApiResponse.success(null);
    }
}
