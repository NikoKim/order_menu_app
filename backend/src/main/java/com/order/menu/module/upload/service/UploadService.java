package com.order.menu.module.upload.service;

import com.order.menu.common.exception.BusinessException;
import com.order.menu.common.exception.ErrorCode;
import com.order.menu.config.UploadProperties;
import com.order.menu.module.shop.entity.Shop;
import com.order.menu.module.shop.service.ShopService;
import com.order.menu.module.upload.dto.UploadDtos;
import com.order.menu.module.upload.entity.UploadFile;
import com.order.menu.module.upload.mapper.UploadFileMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class UploadService {
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final UploadProperties uploadProperties;
    private final UploadFileMapper uploadFileMapper;
    private final ShopService shopService;

    public UploadDtos.UploadResponse avatar(Long userId, MultipartFile file) {
        return store("AVATAR", userId, "avatar", file);
    }

    public UploadDtos.UploadResponse shop(Long ownerUserId, MultipartFile file) {
        Shop shop = shopService.requireOwnedShop(ownerUserId);
        return store("SHOP", shop.getId(), "shop_pic", file);
    }

    public UploadDtos.UploadResponse menu(Long ownerUserId, Long shopId, MultipartFile file) {
        Shop shop = shopService.requireOwnedShop(ownerUserId);
        if (!shop.getId().equals(shopId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权上传该店铺菜品图片");
        }
        return store("DISH", shopId, "menu_pic/" + shopId, file);
    }

    public UploadDtos.UploadResponse family(Long ownerUserId, MultipartFile file) {
        Shop shop = shopService.requireOwnedShop(ownerUserId);
        return store("SHOP", shop.getId(), "home_pic", file);
    }

    private UploadDtos.UploadResponse store(String ownerType, Long ownerId, String relativeDir, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请选择上传文件");
        }
        String extension = extension(file.getOriginalFilename());
        if (!uploadProperties.getAllowedExtensions().contains(extension)) {
            throw new BusinessException(ErrorCode.UNSUPPORTED_MEDIA_TYPE, "上传文件类型不支持");
        }
        String filename = ownerId + "_" + FORMATTER.format(LocalDateTime.now()) + "_" + UUID.randomUUID().toString().replace("-", "") + "." + extension;
        Path dir = Path.of(uploadProperties.getBasePath(), relativeDir);
        Path target = dir.resolve(filename).normalize();
        if (!target.startsWith(dir.normalize())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "文件路径非法");
        }
        try {
            Files.createDirectories(dir);
            file.transferTo(target);
        } catch (IOException exception) {
            throw new BusinessException(ErrorCode.SERVER_ERROR, "文件保存失败");
        }
        String url = publicUrl(uploadProperties.getImageUrlPrefix() + "/" + relativeDir.replace("\\", "/") + "/" + filename);
        UploadFile record = new UploadFile();
        record.setOwnerType(ownerType);
        record.setOwnerId(ownerId);
        record.setFilePath(target.toString());
        record.setUrl(url);
        record.setOriginalName(file.getOriginalFilename());
        uploadFileMapper.insert(record);
        return new UploadDtos.UploadResponse(url);
    }

    private String extension(String originalName) {
        if (originalName == null || !originalName.contains(".")) {
            throw new BusinessException(ErrorCode.UNSUPPORTED_MEDIA_TYPE, "上传文件类型不支持");
        }
        return originalName.substring(originalName.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
    }

    private String publicUrl(String path) {
        String baseUrl = uploadProperties.getPublicBaseUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            return path;
        }
        String normalizedBase = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        return normalizedBase + path;
    }
}
