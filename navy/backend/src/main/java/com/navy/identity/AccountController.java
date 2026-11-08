package com.navy.identity;

import com.navy.common.exception.BusinessException;
import com.navy.common.response.ApiResponse;
import com.navy.security.UserContext;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class AccountController {
    private final JdbcTemplate jdbc;
    private final GuestService guests;
    private final VerificationService verification;
    private final BCryptPasswordEncoder passwords=new BCryptPasswordEncoder();
    public record Credentials(String nickname,String password,String confirmPassword,String email,String emailCode,String captchaId,String captcha) {}
    public record EmailRequest(String email,String captchaId,String captcha) {}
    public record BindEmailRequest(String email,String emailCode) {}
    public record SettingsRequest(Boolean defaultAllowSpectators) {}
    @GetMapping("/auth/captcha") public ApiResponse<?> captcha(jakarta.servlet.http.HttpServletRequest request){return ApiResponse.success(verification.captcha(request.getRemoteAddr()));}
    @PostMapping("/auth/email-code") public ApiResponse<?> email(@RequestBody EmailRequest r,jakarta.servlet.http.HttpServletRequest request){verification.send(r.email(),r.captchaId(),r.captcha(),request.getRemoteAddr());return ApiResponse.success();}
    @PostMapping("/guests") public ApiResponse<?> guest(){return ApiResponse.success(guests.create());}
    @GetMapping("/health") public ApiResponse<?> health(){jdbc.queryForObject("SELECT 1",Integer.class);return ApiResponse.success(Map.of("status","UP"));}
    @PostMapping("/auth/register") @Transactional
    public ApiResponse<?> register(@RequestBody Credentials request,jakarta.servlet.http.HttpServletRequest http) {
        validate(request);
        if(request.nickname().trim().length()>32||request.nickname().contains("@"))throw new BusinessException(400,"指挥官昵称为1至32字，不能包含 @");
        if("NikoKim".equalsIgnoreCase(request.nickname().trim()))throw new BusinessException(409,"该昵称已被保留");
        verification.captcha(request.captchaId(),request.captcha(),http.getRemoteAddr());
        if(!request.password().equals(request.confirmPassword()))throw new BusinessException(400,"两次输入的密码不一致");
        String email=VerificationService.email(request.email());verification.verifyEmail(email,request.emailCode());
        try { jdbc.update("INSERT INTO users(nickname,password_hash,email) VALUES(?,?,?)",request.nickname().trim(),passwords.encode(request.password()),email); }
        catch(org.springframework.dao.DuplicateKeyException e){throw new BusinessException(409,"昵称或邮箱已被注册");}
        return authenticate(request);
    }
    @PostMapping("/auth/login") public ApiResponse<?> login(@RequestBody Credentials request,jakarta.servlet.http.HttpServletRequest http) {
        verification.captcha(request.captchaId(),request.captcha(),http.getRemoteAddr());
        return authenticate(request);
    }
    private ApiResponse<?> authenticate(Credentials request) {
        validate(request);
        String identifier=request.nickname().trim();
        var rows=identifier.contains("@")?jdbc.queryForList("SELECT id,password_hash FROM users WHERE email=?",identifier.toLowerCase(java.util.Locale.ROOT)):jdbc.queryForList("SELECT id,password_hash FROM users WHERE nickname=?",identifier);
        if(rows.isEmpty()||!passwords.matches(request.password(),(String)rows.get(0).get("password_hash"))) throw new BusinessException(401,"邮箱、指挥官昵称或密码错误");
        long id=((Number)rows.get(0).get("id")).longValue();
        String token=UUID.randomUUID()+"-"+UUID.randomUUID();
        jdbc.update("INSERT INTO account_sessions(token_hash,user_id,expires_at) VALUES(?,?,?)",GuestService.hash(token),id,System.currentTimeMillis()+90L*86400000);
        return ApiResponse.success(Map.of("token",token,"user",profile(id)));
    }
    @GetMapping("/account") public ApiResponse<?> me(){return ApiResponse.success(profile(UserContext.get().userId()));}
    @GetMapping("/account/settings") public ApiResponse<?> settings(){return ApiResponse.success(settings(UserContext.get().userId()));}
    @PatchMapping("/account/settings") @Transactional
    public ApiResponse<?> settings(@RequestBody SettingsRequest request){
        long id=UserContext.get().userId();
        if(request==null || request.defaultAllowSpectators()==null)throw new BusinessException(400,"请选择默认观战设置");
        jdbc.update("UPDATE users SET default_allow_spectators=? WHERE id=?",request.defaultAllowSpectators(),id);
        return ApiResponse.success(settings(id));
    }
    @PostMapping("/account/email-code")
    public ApiResponse<?> bindingCode(@RequestBody EmailRequest request,jakarta.servlet.http.HttpServletRequest http) {
        long id=UserContext.get().userId();requireUnbound(id);
        String email=VerificationService.email(request.email());
        if(jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE email=?",Integer.class,email)>0)throw new BusinessException(409,"该邮箱已被其他账号使用");
        verification.send(email,request.captchaId(),request.captcha(),http.getRemoteAddr(),"bind:"+id);
        return ApiResponse.success();
    }
    @PutMapping("/account/email") @Transactional
    public ApiResponse<?> bindEmail(@RequestBody BindEmailRequest request) {
        long id=UserContext.get().userId();requireUnbound(id);
        String email=VerificationService.email(request.email());
        verification.verifyEmail(email,request.emailCode(),"bind:"+id);
        try {
            if(jdbc.update("UPDATE users SET email=? WHERE id=? AND (email IS NULL OR email='')",email,id)!=1)throw new BusinessException(409,"该账号已经绑定邮箱，请刷新个人主页");
        }catch(org.springframework.dao.DuplicateKeyException e){throw new BusinessException(409,"该邮箱已被其他账号使用");}
        return ApiResponse.success(profile(id));
    }
    @PostMapping("/auth/logout") public ApiResponse<?> logout(@RequestHeader("Authorization") String token){jdbc.update("DELETE FROM account_sessions WHERE token_hash=?",GuestService.hash(token.substring(7)));return ApiResponse.success();}
    private void requireUnbound(long id){Object email=profile(id).get("email");if(email!=null&&!email.toString().isBlank())throw new BusinessException(409,"该账号已经绑定邮箱");}
    Map<String,Object> profile(long id){
        var result=new LinkedHashMap<>(jdbc.queryForMap("SELECT id,nickname,email,avatar_url AS avatarUrl,is_admin AS isAdmin,default_allow_spectators AS defaultAllowSpectators FROM users WHERE id=?",id));
        result.put("defaultAllowSpectators",enabled(result.get("defaultAllowSpectators")));
        return result;
    }
    private Map<String,Object> settings(long id){return Map.of("defaultAllowSpectators",enabled(jdbc.queryForObject("SELECT default_allow_spectators FROM users WHERE id=?",Boolean.class,id)));}
    private static boolean enabled(Object value){return !Boolean.FALSE.equals(value) && !(value instanceof Number number && number.intValue()==0);}
    private void validate(Credentials r){if(r.nickname()==null||r.nickname().trim().isEmpty()||r.nickname().trim().length()>254||r.password()==null||r.password().length()<8||r.password().length()>72)throw new BusinessException(400,"请输入邮箱或指挥官昵称，密码为8至72位");}
}
