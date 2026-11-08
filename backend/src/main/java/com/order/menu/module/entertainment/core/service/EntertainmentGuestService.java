package com.order.menu.module.entertainment.core.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.order.menu.common.exception.BusinessException;
import com.order.menu.common.exception.ErrorCode;
import com.order.menu.config.JwtProperties;
import com.order.menu.module.entertainment.core.dto.EntertainmentDtos;
import com.order.menu.module.entertainment.core.entity.EntertainmentGuest;
import com.order.menu.module.entertainment.core.mapper.EntertainmentGuestMapper;
import com.order.menu.security.LoginUser;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EntertainmentGuestService {
    private static final long TOKEN_SECONDS = 365L * 24 * 60 * 60;
    private final EntertainmentGuestMapper guestMapper;
    private final JwtProperties jwtProperties;

    public EntertainmentDtos.GuestResponse create() {
        String publicId = UUID.randomUUID().toString();
        String hash = sha256(publicId);
        String suffix = publicId.replace("-", "").substring(28).toUpperCase();
        EntertainmentGuest guest = new EntertainmentGuest();
        guest.setPublicIdHash(hash);
        guest.setDisplaySuffix(suffix);
        guest.setStatus("ACTIVE");
        guestMapper.insert(guest);
        long expiresAt = Instant.now().plusSeconds(TOKEN_SECONDS).getEpochSecond();
        String payload = publicId + "." + expiresAt;
        String token = base64(payload) + "." + sign(payload);
        return new EntertainmentDtos.GuestResponse(token, "游客-" + suffix);
    }

    public PlayerIdentity resolve(LoginUser loginUser, String guestToken, boolean required) {
        return resolve(loginUser,guestToken,required,false);
    }
    public PlayerIdentity resolveReadonly(LoginUser loginUser,String guestToken,boolean required){return resolve(loginUser,guestToken,required,true);}
    private PlayerIdentity resolve(LoginUser loginUser,String guestToken,boolean required,boolean readOnly){
        if (loginUser != null) {
            return new PlayerIdentity("USER", loginUser.userId(), null, "U:" + loginUser.userId(), loginUser.nickname());
        }
        if (guestToken == null || guestToken.isBlank()) {
            if (required) throw unauthorized();
            return null;
        }
        try {
            String[] tokenParts = guestToken.split("\\.", 2);
            if (tokenParts.length != 2) throw unauthorized();
            String payload = new String(Base64.getUrlDecoder().decode(tokenParts[0]), StandardCharsets.UTF_8);
            if (!MessageDigest.isEqual(sign(payload).getBytes(StandardCharsets.UTF_8), tokenParts[1].getBytes(StandardCharsets.UTF_8))) {
                throw unauthorized();
            }
            int separator = payload.lastIndexOf('.');
            String publicId = payload.substring(0, separator);
            long expiresAt = Long.parseLong(payload.substring(separator + 1));
            if (Instant.now().getEpochSecond() > expiresAt) throw unauthorized();
            EntertainmentGuest guest = guestMapper.selectOne(new LambdaQueryWrapper<EntertainmentGuest>()
                    .eq(EntertainmentGuest::getPublicIdHash, sha256(publicId)).last("LIMIT 1"));
            if (guest == null || !"ACTIVE".equals(guest.getStatus())) throw unauthorized();
            if(!readOnly){guest.setLastSeenAt(LocalDateTime.now());guestMapper.updateById(guest);}
            return new PlayerIdentity("GUEST", null, guest.getId(), "G:" + guest.getPublicIdHash(),
                    "游客-" + guest.getDisplaySuffix());
        } catch (BusinessException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw unauthorized();
        }
    }

    private String sign(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to sign entertainment guest token", exception);
        }
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static String base64(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static BusinessException unauthorized() {
        return new BusinessException(ErrorCode.UNAUTHORIZED, "游客身份已失效，请重新进入游戏");
    }
}
