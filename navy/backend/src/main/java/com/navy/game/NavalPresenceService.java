package com.navy.game;

import com.navy.common.exception.BusinessException;
import com.navy.common.exception.ErrorCode;
import com.navy.identity.PlayerIdentity;
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
    private record Page(String owner,long expiry){}
    private final java.util.concurrent.ConcurrentHashMap<String,Page> pages=new java.util.concurrent.ConcurrentHashMap<>();

    public record PresenceRequest(String pageToken) { }
    public void socketSeen(PlayerIdentity identity,String socketId){pages.put("ws:"+socketId,new Page(identity.key(),System.currentTimeMillis()+TTL_MS));}
    public void socketGone(String socketId){pages.remove("ws:"+socketId);}

    @Transactional
    public Map<String,Object> heartbeat(PlayerIdentity identity, PresenceRequest request) {
        String token=validate(request == null ? null : request.pageToken());
        long now=System.currentTimeMillis();
        pages.compute(token,(key,old)->{if(old!=null&&old.expiry()>now&&!identity.key().equals(old.owner()))throw new BusinessException(ErrorCode.FORBIDDEN,"页面标识不属于当前玩家");return new Page(identity.key(),now+TTL_MS);});
        return count(now);
    }

    @Transactional
    public Map<String,Object> leave(PlayerIdentity identity,String pageToken) {
        String token=validate(pageToken);
        pages.computeIfPresent(token,(key,old)->identity.key().equals(old.owner())?null:old);
        return count(System.currentTimeMillis());
    }

    public Map<String,Object> count(long now) {
        long value=pages.values().stream().filter(p->p.expiry()>now).map(Page::owner).distinct().count();
        return Map.of("onlineCount",value);
    }

    @Scheduled(fixedDelay=60_000,initialDelay=30_000)
    public void purge() {
        pages.values().removeIf(p->p.expiry()<=System.currentTimeMillis());
    }

    private static String validate(String value) {
        if(value==null || !value.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"))
            throw new BusinessException(ErrorCode.BAD_REQUEST,"页面标识无效");
        return UUID.fromString(value).toString();
    }
}
