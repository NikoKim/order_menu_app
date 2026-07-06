package com.order.menu.module.upload.controller;

import com.order.menu.common.response.ApiResponse;
import com.order.menu.module.upload.dto.UploadDtos;
import com.order.menu.module.upload.service.UploadService;
import com.order.menu.security.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/upload")
@RequiredArgsConstructor
public class UploadController {
    private final UploadService uploadService;

    @PostMapping("/avatar")
    public ApiResponse<UploadDtos.UploadResponse> avatar(@RequestParam("file") MultipartFile file) {
        return ApiResponse.success(uploadService.avatar(UserContext.get().userId(), file));
    }

    @PostMapping("/shop")
    public ApiResponse<UploadDtos.UploadResponse> shop(@RequestParam("file") MultipartFile file) {
        return ApiResponse.success(uploadService.shop(UserContext.get().userId(), file));
    }

    @PostMapping("/menu")
    public ApiResponse<UploadDtos.UploadResponse> menu(@RequestParam Long shopId, @RequestParam("file") MultipartFile file) {
        return ApiResponse.success(uploadService.menu(UserContext.get().userId(), shopId, file));
    }

    @PostMapping("/family")
    public ApiResponse<UploadDtos.UploadResponse> family(@RequestParam("file") MultipartFile file) {
        return ApiResponse.success(uploadService.family(UserContext.get().userId(), file));
    }
}
