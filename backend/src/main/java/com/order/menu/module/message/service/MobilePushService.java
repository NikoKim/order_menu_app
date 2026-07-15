package com.order.menu.module.message.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.order.menu.module.message.dto.MessageDtos;
import com.order.menu.module.message.entity.PushDevice;
import com.order.menu.module.message.mapper.PushDeviceMapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Async;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Service
@RequiredArgsConstructor
public class MobilePushService {
    private final PushDeviceMapper pushDeviceMapper;
    private final RestClient.Builder restClientBuilder;

    @Value("${app.push.gateway-url:}")
    private String gatewayUrl;
    @Value("${app.push.gateway-token:}")
    private String gatewayToken;

    @Transactional
    public void register(Long userId, MessageDtos.PushDeviceRequest request) {
        PushDevice device = pushDeviceMapper.selectOne(new LambdaQueryWrapper<PushDevice>()
                .eq(PushDevice::getToken, request.token()).last("limit 1"));
        LocalDateTime now = LocalDateTime.now();
        if (device == null) {
            device = new PushDevice();
            device.setToken(request.token());
            device.setCreatedAt(now);
        }
        device.setUserId(userId);
        device.setPlatform(request.platform().toUpperCase());
        device.setUpdatedAt(now);
        if (device.getId() == null) pushDeviceMapper.insert(device); else pushDeviceMapper.updateById(device);
    }

    @Transactional
    public void unregister(Long userId, String token) {
        pushDeviceMapper.delete(new LambdaQueryWrapper<PushDevice>()
                .eq(PushDevice::getUserId, userId).eq(PushDevice::getToken, token));
    }

    @Async
    public void dispatch(Long userId, String title, String content, String payload) {
        if (gatewayUrl == null || gatewayUrl.isBlank() || gatewayToken == null || gatewayToken.isBlank()) return;
        List<String> tokens = pushDeviceMapper.selectList(new LambdaQueryWrapper<PushDevice>()
                        .eq(PushDevice::getUserId, userId))
                .stream().map(PushDevice::getToken).toList();
        if (tokens.isEmpty()) return;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(5_000);
        requestFactory.setReadTimeout(10_000);
        RestClient client = restClientBuilder.clone().requestFactory(requestFactory).build();
        String deliveryId = UUID.randomUUID().toString();
        Map<String, Object> body = Map.of("deliveryId", deliveryId,
                "tokens", tokens, "title", title == null ? "" : title,
                "content", content == null ? "" : content,
                "payload", payload == null ? "" : payload);
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                client.post().uri(gatewayUrl)
                        .header("Authorization", "Bearer " + gatewayToken)
                        .header("Idempotency-Key", deliveryId)
                        .body(body).retrieve().toBodilessEntity();
                return;
            } catch (RuntimeException ignored) {
                // The notification remains persisted even when the external gateway is unavailable.
            }
        }
    }
}
