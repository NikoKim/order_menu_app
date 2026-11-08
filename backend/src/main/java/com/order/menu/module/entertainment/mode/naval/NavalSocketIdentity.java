package com.order.menu.module.entertainment.mode.naval;
import com.order.menu.module.entertainment.core.service.EntertainmentGuestService;
import com.order.menu.module.entertainment.core.service.PlayerIdentity;
import com.order.menu.security.JwtTokenProvider;
import com.order.menu.security.LoginUser;
import com.order.menu.module.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
@Component @RequiredArgsConstructor
public class NavalSocketIdentity {
    private final EntertainmentGuestService guests;private final JwtTokenProvider tokens;private final UserMapper users;
    public PlayerIdentity resolve(String token,String guest){
        LoginUser login=null;
        if(token!=null&&!token.isBlank()){
            var claims=tokens.parse(token);var user=users.selectById(claims.userId());
            if(user==null||user.getStatus()!=1)throw new IllegalArgumentException("Inactive user");
            login=new LoginUser(user.getId(),user.getNickname(),user.getRole());
        }
        return guests.resolveReadonly(login,guest,true);
    }
}
