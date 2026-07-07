package com.order.menu.module.version.service;

// 1. MyBatis-Plus 相关的包
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

// 2. 你自己项目里的类 (注意核对你的包名路径是否一致)
import com.order.menu.module.version.dto.VersionDtos;
import com.order.menu.module.version.entity.Version;
import com.order.menu.module.version.mapper.VersionMapper;

// 3. Spring 相关的包
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 4. Java 基础包
import java.time.LocalDateTime;

@Service
// 【关键修复点】：这里必须继承 ServiceImpl，并传入 Mapper 和 实体类
public class VersionService extends ServiceImpl<VersionMapper, Version> {

    /**
     * 检查 App 是否有新版本 (供 Flutter 前端调用)
     *
     * @param currentVersionCode 当前客户端的构建版本号
     * @return 包含更新信息的 DTO
     */
    public VersionDtos.VersionCheckResponse checkUpdate(String appCode, Integer currentVersionCode) {
        System.out.println("检查更新 appCode=" + appCode + ", currentVersionCode=" + currentVersionCode);

        Version latestVersion = this.getBaseMapper().selectOne(
                new LambdaQueryWrapper<Version>()
                        .eq(Version::getAppCode, appCode)
                        .orderByDesc(Version::getVersionCode)
                        .last("LIMIT 1")
        );

        System.out.println("数据库查到的最新版本 latestVersion=" + latestVersion);

        if (latestVersion != null && latestVersion.getVersionCode() > currentVersionCode) {
            return new VersionDtos.VersionCheckResponse(
                    true,
                    latestVersion.getVersionName(),
                    latestVersion.getDownloadUrl(),
                    latestVersion.getIsForce() == 1,
                    latestVersion.getUpdateContent()
            );
        }

        return new VersionDtos.VersionCheckResponse(false, null, null, false, null);
    }

    /**
     * 发布新版本 (供后台管理系统调用)
     *
     * @param request 保存新版本的请求参数
     */
    @Transactional(rollbackFor = Exception.class)
    public void publishNewVersion(VersionDtos.VersionSaveRequest request) {
        System.out.println("准备发布版本：" + request);

        Version version = new Version();
        version.setAppCode(request.appCode());
        version.setVersionCode(request.versionCode());
        version.setVersionName(request.versionName());
        version.setDownloadUrl(request.downloadUrl());
        version.setIsForce(request.isForce());
        version.setCreateTime(LocalDateTime.now());
        version.setUpdateContent(request.updateContent());

        boolean saved = this.save(version);
        System.out.println("版本插入结果 saved=" + saved);

        if (!saved) {
            throw new RuntimeException("版本发布失败：数据库插入失败");
        }
    }
}