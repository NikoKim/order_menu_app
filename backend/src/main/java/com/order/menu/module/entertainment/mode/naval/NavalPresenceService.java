package com.order.menu.module.entertainment.mode.naval;

import com.order.menu.common.exception.BusinessException;
import com.order.menu.common.exception.ErrorCode;
import com.order.menu.module.entertainment.core.service.PlayerIdentity;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NavalPresenceService {
    public static final long TTL_MS = 25_000;
    private final JdbcTemplate jdbc;

    public record PresenceRequest(String pageToken) { }

    @Transactional
    public Map<String,Object> heartbeat(PlayerIdentity identity, PresenceRequest request) {
        String token=validate(request == null ? null : request.pageToken());
        long now=System.currentTimeMillis();
        jdbc.update("INSERT INTO naval_page_presence (page_token,player_key,expires_at) VALUES (?,?,?) ON DUPLICATE KEY UPDATE expires_at=IF(player_key=VALUES(player_key),VALUES(expires_at),expires_at)",
                token,identity.key(),now+TTL_MS);
        String owner=jdbc.queryForObject("SELECT player_key FROM naval_page_presence WHERE page_token=?",String.class,token);
        if(!identity.key().equals(owner)) throw new BusinessException(ErrorCode.FORBIDDEN,"页面标识不属于当前玩家");
        return count(now);
    }

    @Transactional
    public Map<String,Object> leave(PlayerIdentity identity,String pageToken) {
        String token=validate(pageToken);
        jdbc.update("DELETE FROM naval_page_presence WHERE page_token=? AND player_key=?",token,identity.key());
        return count(System.currentTimeMillis());
    }

    public Map<String,Object> count(long now) {
        Long value=jdbc.queryForObject("SELECT COUNT(DISTINCT player_key) FROM naval_page_presence WHERE expires_at>?",Long.class,now);
        return Map.of("onlineCount",value == null ? 0L : value);
    }

    @Scheduled(fixedDelay=60_000,initialDelay=30_000)
    public void purge() {
        try { jdbc.update("DELETE FROM naval_page_presence WHERE expires_at<=?",System.currentTimeMillis()); }
        catch(org.springframework.jdbc.BadSqlGrammarException ignored) { /* Deployments apply migrations explicitly. */ }
    }

    private static String validate(String value) {
        if(value==null || !value.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"))
            throw new BusinessException(ErrorCode.BAD_REQUEST,"页面标识无效");
        return UUID.fromString(value).toString();
    }
}
