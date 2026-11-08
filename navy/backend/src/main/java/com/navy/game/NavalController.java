package com.navy.game;

import com.navy.common.response.ApiResponse;
import com.navy.identity.GuestService;
import com.navy.identity.PlayerIdentity;
import com.navy.security.UserContext;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import java.sql.SQLException;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;
import java.util.List;

@RestController
@RequiredArgsConstructor
@Slf4j
@RequestMapping("/api/naval")
public class NavalController {
    private final NavalService service;
    private final GuestService guests;
    private final NavalPresenceService presence;
    private final NavalCommunityService community;
    private final ShortCache cache=new ShortCache();
    @PutMapping("/rooms/local/access") public ApiResponse<Map<String,Object>> hideLocal(@RequestBody Map<String,String> request,@RequestHeader(value="X-Guest-Token",required=false) String guest){return ApiResponse.success(service.hideLocal(request.get("id"),identity(guest)));}

    @GetMapping("/leaderboard")
    public ApiResponse<List<Map<String,Object>>> leaderboard(@RequestParam(defaultValue="AI") String mode,@RequestParam(defaultValue="wins") String metric,@RequestParam(defaultValue="CLASSIC") String ruleSet,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="100") int pageSize) { return ApiResponse.success(cache.get("rank:"+mode+":"+metric+":"+ruleSet+":"+page+":"+pageSize,15000,()->community.leaderboard(mode,metric,ruleSet,page,pageSize))); }
    @GetMapping("/history/{gameId}")
    public ApiResponse<Map<String,Object>> detail(@PathVariable String gameId,@RequestHeader(value="X-Guest-Token",required=false) String guest) { return ApiResponse.success(community.detail(gameId,identity(guest))); }
    @GetMapping("/leaderboard/page")
    public ApiResponse<Map<String,Object>> leaderboardPage(@RequestParam(defaultValue="AI") String mode,@RequestParam(defaultValue="wins") String metric,@RequestParam(defaultValue="CLASSIC") String ruleSet,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="8") int pageSize){return ApiResponse.success(cache.get("rank-page:"+mode+":"+metric+":"+ruleSet+":"+page+":"+pageSize,15000,()->community.leaderboardPage(mode,metric,ruleSet,page,pageSize)));}
    @GetMapping("/lobby/messages")
    public ApiResponse<List<Map<String,Object>>> messages(@RequestHeader(value="X-Guest-Token",required=false) String guest) { return ApiResponse.success(community.messages(guests.resolve(UserContext.getOrNull(),guest,false))); }
    @GetMapping("/lobby/sync")
    public ApiResponse<Map<String,Object>> syncMessages(@RequestParam(defaultValue="0") long afterId,@RequestParam(defaultValue="") String cardVersion,@RequestHeader(value="X-Guest-Token",required=false) String guest){return ApiResponse.success(community.syncMessages(guests.resolve(UserContext.getOrNull(),guest,false),afterId,cardVersion));}
    @PostMapping("/lobby/messages")
    public ApiResponse<List<Map<String,Object>>> message(@RequestBody NavalCommunityService.TextRequest request,@RequestHeader(value="X-Guest-Token",required=false) String guest,@RequestParam(defaultValue="0") long afterId) { return ApiResponse.success(community.send(identity(guest),request,afterId)); }
    @GetMapping("/reviews")
    public ApiResponse<Map<String,Object>> reviews(@RequestParam(defaultValue="1") int page,@RequestHeader(value="X-Guest-Token",required=false) String guest) { return ApiResponse.success(community.reviews(identity(guest),page)); }
    @PutMapping("/reviews/me")
    public ApiResponse<Map<String,Object>> review(@RequestBody NavalCommunityService.ReviewRequest request,@RequestHeader(value="X-Guest-Token",required=false) String guest) { return ApiResponse.success(community.review(identity(guest),request)); }
    @DeleteMapping("/reviews/me")
    public ApiResponse<Void> deleteReview(@RequestHeader(value="X-Guest-Token",required=false) String guest) { community.deleteReview(identity(guest)); return ApiResponse.success(); }

    @PostMapping("/presence")
    public ApiResponse<Map<String,Object>> presence(@RequestBody NavalPresenceService.PresenceRequest request,
            @RequestHeader(value="X-Guest-Token",required=false) String guest) {
        return ApiResponse.success(presence.heartbeat(identity(guest),request));
    }

    @DeleteMapping("/presence/{pageToken}")
    public ApiResponse<Map<String,Object>> leavePresence(@PathVariable String pageToken,
            @RequestHeader(value="X-Guest-Token",required=false) String guest) {
        return ApiResponse.success(presence.leave(identity(guest),pageToken));
    }

    public record MedalRequest(String medalId) {}
    @GetMapping("/medals") public ApiResponse<Map<String,Object>> medals(@RequestParam(required=false) String player,@RequestHeader(value="X-Guest-Token",required=false) String guest){return ApiResponse.success(service.medalWall(identity(guest),player));}
    @PutMapping("/medals") public ApiResponse<Map<String,Object>> equipMedal(@RequestBody MedalRequest request,@RequestHeader(value="X-Guest-Token",required=false) String guest){return ApiResponse.success(service.equipMedal(identity(guest),request.medalId()));}

    @GetMapping("/military-template")
    public ApiResponse<Map<String,Object>> militaryTemplate(){return ApiResponse.success(service.militaryTemplate());}

    @GetMapping("/rooms")
    public ApiResponse<List<Map<String,Object>>> rooms() { return ApiResponse.success(cache.get("rooms",3000,service::rooms)); }

    @PostMapping("/rooms/{id}/enter")
    public ApiResponse<Map<String,Object>> enter(@PathVariable String id,@RequestBody NavalService.EnterRequest request,@RequestHeader(value="X-Guest-Token",required=false) String guest) { return ApiResponse.success(service.enter(id,identity(guest),request)); }

    @PutMapping("/rooms/local")
    public ApiResponse<Map<String,Object>> publishLocal(@RequestBody NavalService.LocalRoomRequest request,@RequestHeader(value="X-Guest-Token",required=false) String guest) { return ApiResponse.success(service.publishLocal(identity(guest),request)); }

    @PostMapping("/rooms")
    public ApiResponse<Map<String,Object>> create(@RequestBody NavalService.CreateRequest request,
            @RequestHeader(value="X-Guest-Token",required=false) String guest) {
        return ApiResponse.success(service.create(identity(guest),request));
    }

    @PostMapping("/rooms/join")
    public ApiResponse<Map<String,Object>> join(@RequestBody NavalService.JoinRequest request,
            @RequestHeader(value="X-Guest-Token",required=false) String guest) {
        var player=identity(guest);return ApiResponse.success(request.wager()==null?service.join(player,request.inviteCode()):service.join(player,request.inviteCode(),request.wager()));
    }
    @GetMapping("/rooms/invite/{code}/terms")
    public ApiResponse<Map<String,Object>> inviteTerms(@PathVariable String code,@RequestParam(required=false) Integer seat,@RequestHeader(value="X-Guest-Token",required=false) String guest){return ApiResponse.success(service.inviteTerms(code,identity(guest),seat));}

    @GetMapping("/rooms/{id}")
    public ApiResponse<Map<String,Object>> room(@PathVariable String id,
            @RequestHeader(value="X-Guest-Token",required=false) String guest,@RequestParam(defaultValue="-1") long version) {
        return ApiResponse.success(service.sync(id,identity(guest),version));
    }

    @PostMapping("/rooms/spectate")
    public ApiResponse<Map<String,Object>> spectate(@RequestBody NavalService.SpectateRequest request,
            @RequestHeader(value="X-Guest-Token",required=false) String guest) {
        return ApiResponse.success(service.spectate(identity(guest),request));
    }

    @PostMapping("/rooms/{id}/actions")
    public ApiResponse<Map<String,Object>> action(@PathVariable String id,@RequestBody NavalState.Action request,
            @RequestHeader(value="X-Guest-Token",required=false) String guest,@RequestParam(defaultValue="false") boolean compact) {
        return ApiResponse.success(service.action(id,identity(guest),request,compact));
    }

    @PostMapping("/rooms/{id}/connection")
    public ApiResponse<Map<String,Object>> connection(@PathVariable String id,@RequestBody NavalService.ConnectionRequest request,
            @RequestHeader(value="X-Guest-Token",required=false) String guest) {
        return ApiResponse.success(service.connection(id,identity(guest),request));
    }

    @GetMapping("/profile")
    public ApiResponse<Map<String,Object>> profile(@RequestHeader(value="X-Guest-Token",required=false) String guest) {
        return ApiResponse.success(service.profile(identity(guest)));
    }

    @GetMapping("/progression")
    public ApiResponse<Map<String,Object>> progression(@RequestHeader(value="X-Guest-Token",required=false) String guest) {
        return ApiResponse.success(service.progression(identity(guest)));
    }

    public record TaskClaim(String day){}
    @PostMapping("/progression/tasks/{taskId}/claim")
    public ApiResponse<Map<String,Object>> claimTask(@PathVariable String taskId,@RequestBody(required=false) TaskClaim request,@RequestHeader(value="X-Guest-Token",required=false) String guest){
        return ApiResponse.success(service.claimTask(identity(guest),taskId,request==null?null:request.day()));
    }

    @PutMapping("/profile")
    public ApiResponse<Map<String,Object>> profile(@RequestBody NavalService.ProfileRequest request,
            @RequestHeader(value="X-Guest-Token",required=false) String guest) {
        return ApiResponse.success(service.saveProfile(identity(guest),request));
    }

    private PlayerIdentity identity(String guest) { return guests.resolve(UserContext.getOrNull(),guest,true); }

    @PostMapping("/rooms/{id}/heartbeat") public ApiResponse<Map<String,Object>> heartbeat(@PathVariable String id,@RequestHeader(value="X-Guest-Token",required=false) String guest){return ApiResponse.success(service.heartbeat(id,identity(guest)));}

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ApiResponse<Void>> databaseUnavailable(DataAccessException exception) {
        log.error("Naval database request failed; check Navy Flyway migrations and datasource permissions",exception);
        String message = "海战服务暂时不可用，请稍后重试";
        for (Throwable cause=exception;cause!=null;cause=cause.getCause()) {
            if (!(cause instanceof SQLException sql)) continue;
            if (sql.getErrorCode() == 1146 || sql.getErrorCode() == 1054)
                message = "海战数据库尚未初始化完成，请管理员检查 Navy 数据库迁移";
            else if (sql.getErrorCode() == 1044 || sql.getErrorCode() == 1045 || sql.getErrorCode() == 1142)
                message = "海战服务暂不可用，请管理员检查数据库连接和权限";
        }
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(ApiResponse.fail(503,message));
    }
}
