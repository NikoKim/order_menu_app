package com.order.menu.module.version.dto;


import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

public class VersionDtos {
    // 私有化构造器，防止实例化这个工具类
    private VersionDtos() {
    }

    /**
     * 基础的版本信息响应体 (通常用于后台管理列表页展示)
     */
    public record VersionResponse(
            Integer id,
            Integer versionCode,
            String versionName,
            String downloadUrl,
            Integer isForce,
            String updateContent,
            LocalDateTime createTime
    ) {
    }

    /**
     * 客户端检查更新时的响应体 (专门提供给 Flutter 前端)
     */
    public record VersionCheckResponse(
            boolean hasUpdate,
            String versionName,
            String downloadUrl,
            boolean isForce,
            String updateContent
    ) {
    }

    /**
     * 新增或更新版本时的请求体 (用于后台管理系统发布新版本)
     */
    public record VersionSaveRequest(
            @NotBlank(message = "应用标识不能为空")
            String appCode,
            @NotNull(message = "构建版本号不能为空")
            Integer versionCode,

            @NotBlank(message = "展示版本号不能为空")
            @Size(max = 50, message = "展示版本号不能超过50个字符")
            String versionName,

            @NotBlank(message = "下载链接不能为空")
            @Size(max = 255, message = "下载链接不能超过255个字符")
            String downloadUrl,

            @NotNull(message = "是否强制更新标记不能为空")
            Integer isForce,

            String updateContent
    ) {
    }
}
