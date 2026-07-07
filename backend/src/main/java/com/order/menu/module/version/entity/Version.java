package com.order.menu.module.version.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("version")
public class Version {
    private Integer versionCode;

    /**
     * 展示版本号 (例如: "1.0.0", "1.0.1")
     */
    private String versionName;

    /**
     * 安装包下载链接或应用商店链接
     */
    private String downloadUrl;

    /**
     * 是否强制更新 (0-否, 1-是)
     */
    private Integer isForce;

    /**
     * 创建时间（版本发布时间）
     */
    private LocalDateTime createTime;

    /**
     * 应用标识 (如: admin, shop, customer)
     */
    private String appCode;

    /**
     * 更新内容说明
     */
    private String updateContent;
}
