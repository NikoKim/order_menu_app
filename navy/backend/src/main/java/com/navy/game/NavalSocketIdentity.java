package com.navy.game;
import com.navy.identity.GuestService;
import com.navy.identity.PlayerIdentity;
import com.navy.security.LoginUser;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
@Component @RequiredArgsConstructor
public class NavalSocketIdentity {
    private final GuestService guests;private final JdbcTemplate jdbc;
    public PlayerIdentity resolve(String token,String guest){
        LoginUser user=null;
        if(token!=null&&!token.isBlank()){
            var rows=jdbc.queryForList("SELECT u.id,u.nickname FROM account_sessions s JOIN users u ON s.user_id=u.id WHERE s.token_hash=? AND s.expires_at>?",GuestService.hash(token),System.currentTimeMillis());
            if(rows.isEmpty())throw new IllegalArgumentException("Expired identity");var row=rows.get(0);user=new LoginUser(((Number)row.get("id")).longValue(),(String)row.get("nickname"));
        }
        return guests.resolve(user,guest,true);
    }
}
