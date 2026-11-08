package com.navy.identity;

import com.navy.common.exception.BusinessException;
import com.navy.security.LoginUser;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GuestService {
    private final JdbcTemplate jdbc;
    public Map<String,String> create() {
        String token=UUID.randomUUID()+"-"+UUID.randomUUID();
        String suffix=token.substring(0,6).toUpperCase();
        jdbc.update("INSERT INTO naval_guests(token_hash,display_suffix,expires_at) VALUES(?,?,?)",hash(token),suffix,System.currentTimeMillis()+365L*86400000);
        return Map.of("token",token,"displayName","游客-"+suffix);
    }
    public PlayerIdentity resolve(LoginUser user,String token,boolean required) {
        if(user!=null) return new PlayerIdentity("USER",user.userId(),null,"U:"+user.userId(),user.nickname());
        if(token==null||token.isBlank()) { if(required) throw unauthorized(); return null; }
        var rows=jdbc.queryForList("SELECT id,display_suffix FROM naval_guests WHERE token_hash=? AND expires_at>?",hash(token),System.currentTimeMillis());
        if(rows.isEmpty()) throw unauthorized();
        var row=rows.get(0);
        return new PlayerIdentity("GUEST",null,((Number)row.get("id")).longValue(),"G:"+hash(token),"游客-"+row.get("display_suffix"));
    }
    public static String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch(Exception e) { throw new IllegalStateException(e); }
    }
    private BusinessException unauthorized() { return new BusinessException(401,"游客身份已失效，请重新进入游戏"); }
}
