package com.order.menu.module.moment.controller;

import com.order.menu.common.response.ApiResponse;
import com.order.menu.module.message.dto.MessageDtos;
import com.order.menu.module.moment.dto.MomentDtos;
import com.order.menu.module.moment.service.*;
import com.order.menu.security.UserContext;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.util.HtmlUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController @RequestMapping("/api/moments") @RequiredArgsConstructor
public class MomentController {
  private final MomentService service; private final MomentMediaService media;
  @GetMapping public ApiResponse<MomentDtos.PageResponse<MomentDtos.MomentResponse>> list(
      @RequestParam(defaultValue="RECOMMENDED") String feed,@RequestParam(defaultValue="1") long page,
      @RequestParam(required=false) Long pageSize,@RequestParam(required=false) Long size,
      @RequestParam(defaultValue="true") boolean consumeExposure,
      @RequestParam(required=false) String keyword,@RequestParam(required=false) Long authorId,@RequestParam(required=false) Long topicId){
    long s=pageSize!=null?pageSize:(size!=null?size:10);long normalized=page<1?1:page;Long uid=uid();
    if(keyword!=null)return ApiResponse.success(service.search(uid,keyword,normalized,s));
    if(authorId!=null)return ApiResponse.success(service.momentsForProfile(uid,authorId,normalized,s));
    if(topicId!=null)return ApiResponse.success(service.byTopic(uid,topicId,normalized,s));
    return ApiResponse.success(service.feed(uid,feed,normalized,s,consumeExposure));
  }
  @GetMapping(value="/{id}/share-card",produces=MediaType.TEXT_HTML_VALUE)
  public ResponseEntity<String> shareCard(@PathVariable Long id,@RequestParam(required=false)Long commentId){
    String title="动态分享",description="点击查看动态详情",image="https://moment.zsj-unknown.top/app_icon.png";
    try{
      MomentDtos.MomentResponse moment=service.detail(-1L,id);
      title=moment.title()==null||moment.title().isBlank()?title:moment.title();
      description=moment.content()==null||moment.content().isBlank()?description:moment.content();
      if(moment.imageUrls()!=null&&!moment.imageUrls().isEmpty())image=absoluteImage(moment.imageUrls().get(0));
    }catch(RuntimeException ignored){}
    String target="https://moment.zsj-unknown.top/customer/moments/"+id+(commentId==null?"":"?commentId="+commentId);
    String html=shareHtml(title,description,image,target);
    return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL,"public, max-age=300").contentType(MediaType.TEXT_HTML).body(html);
  }
  @GetMapping("/{id}") public ApiResponse<MomentDtos.MomentResponse> detail(@PathVariable Long id){return ApiResponse.success(service.detail(uid(),id));}
  @GetMapping("/{id}/availability") public ApiResponse<MomentDtos.AvailabilityResponse> availability(@PathVariable Long id){return ApiResponse.success(service.availability(uid(),id));}
  @PostMapping public ApiResponse<MomentDtos.MomentResponse> create(@Valid @RequestBody MomentDtos.UpsertRequest r){return ApiResponse.success(service.create(uid(),r));}
  @PutMapping("/{id}") public ApiResponse<MomentDtos.MomentResponse> update(@PathVariable Long id,@Valid @RequestBody MomentDtos.UpsertRequest r){return ApiResponse.success(service.update(uid(),id,r));}
  @DeleteMapping("/{id}") public ApiResponse<Void> delete(@PathVariable Long id){service.delete(uid(),id);return ApiResponse.success();}
  @GetMapping("/drafts") public ApiResponse<MomentDtos.PageResponse<MomentDtos.MomentResponse>> drafts(@RequestParam(defaultValue="1")long page,@RequestParam(defaultValue="10")long pageSize){return ApiResponse.success(service.drafts(uid(),page,pageSize));}
  @PutMapping("/{id}/like") public ApiResponse<MomentDtos.MomentResponse> like(@PathVariable Long id,@RequestBody(required=false) MomentDtos.ToggleRequest r){return ApiResponse.success(service.like(uid(),id,r==null||!Boolean.FALSE.equals(r.active())));}
  @PostMapping("/{id}/likes") public ApiResponse<MomentDtos.MomentResponse> addLike(@PathVariable Long id){return ApiResponse.success(service.like(uid(),id,true));}
  @DeleteMapping("/{id}/likes") public ApiResponse<MomentDtos.MomentResponse> removeLike(@PathVariable Long id){return ApiResponse.success(service.like(uid(),id,false));}
  @PutMapping("/{id}/reactions") public ApiResponse<MomentDtos.MomentResponse> reaction(@PathVariable Long id,@Valid @RequestBody MomentDtos.ReactionRequest r){return ApiResponse.success(service.react(uid(),id,r.emoji(),r.active()==null||r.active()));}
  @PostMapping("/{id}/reactions") public ApiResponse<MomentDtos.MomentResponse> addReaction(@PathVariable Long id,@Valid @RequestBody MomentDtos.ReactionRequest r){return ApiResponse.success(service.react(uid(),id,r.emoji(),true));}
  @DeleteMapping("/{id}/reactions") public ApiResponse<MomentDtos.MomentResponse> removeReaction(@PathVariable Long id,@RequestParam String emoji){return ApiResponse.success(service.react(uid(),id,emoji,false));}
  @GetMapping("/{id}/comments") public ApiResponse<MomentDtos.PageResponse<MomentDtos.CommentResponse>> comments(@PathVariable Long id,@RequestParam(defaultValue="1")long page,@RequestParam(defaultValue="10")long pageSize){return ApiResponse.success(service.comments(uid(),id,page,pageSize));}
  @PostMapping("/{id}/comments") public ApiResponse<MomentDtos.CommentResponse> comment(@PathVariable Long id,@Valid @RequestBody MomentDtos.CommentRequest r){return ApiResponse.success(service.addComment(uid(),id,r));}
  @GetMapping({"/{momentId}/comments/{commentId}/replies","/comments/{commentId}/replies"}) public ApiResponse<MomentDtos.PageResponse<MomentDtos.CommentResponse>> replies(@PathVariable(required=false) Long momentId,@PathVariable Long commentId,@RequestParam(defaultValue="1")long page,@RequestParam(defaultValue="10")long pageSize){return ApiResponse.success(service.replies(uid(),commentId,page,pageSize));}
  @DeleteMapping({"/{momentId}/comments/{commentId}","/comments/{commentId}"}) public ApiResponse<Void> deleteComment(@PathVariable(required=false)Long momentId,@PathVariable Long commentId){service.deleteComment(uid(),commentId);return ApiResponse.success();}
  @PutMapping({"/{momentId}/comments/{commentId}/like","/comments/{commentId}/like"}) public ApiResponse<MomentDtos.CommentResponse> likeComment(@PathVariable(required=false)Long momentId,@PathVariable Long commentId,@RequestBody(required=false)MomentDtos.ToggleRequest r){return ApiResponse.success(service.likeComment(uid(),commentId,r==null||!Boolean.FALSE.equals(r.active())));}
  @GetMapping("/topics/suggestions") public ApiResponse<List<MomentDtos.TopicResponse>> topics(@RequestParam(required=false)String query){return ApiResponse.success(service.suggestTopics(query));}
  @GetMapping("/topics") public ApiResponse<MomentDtos.PageResponse<MomentDtos.TopicResponse>> topicPage(@RequestParam(required=false)String query,@RequestParam(defaultValue="1")long page,@RequestParam(defaultValue="10")long pageSize){return ApiResponse.success(service.topicsPage(query,page,pageSize));}
  @PostMapping("/topics") public ApiResponse<MomentDtos.TopicResponse> createTopic(@Valid @RequestBody MomentDtos.TopicCreateRequest r){return ApiResponse.success(service.createTopic(uid(),r.name()));}
  @GetMapping("/topics/{id}/detail") public ApiResponse<MomentDtos.TopicResponse> topicDetail(@PathVariable Long id){return ApiResponse.success(service.topicDetail(id));}
  @GetMapping("/topics/{id}") public ApiResponse<MomentDtos.PageResponse<MomentDtos.MomentResponse>> topic(@PathVariable Long id,@RequestParam(defaultValue="1")long page,@RequestParam(defaultValue="10")long pageSize){return ApiResponse.success(service.byTopic(uid(),id,page,pageSize));}
  @GetMapping("/users/{id}") public ApiResponse<MomentDtos.PageResponse<MomentDtos.MomentResponse>> userMoments(@PathVariable Long id,@RequestParam(defaultValue="1")long page,@RequestParam(defaultValue="10")long pageSize){return ApiResponse.success(service.momentsForProfile(uid(),id,page,pageSize));}
  @GetMapping("/users/{id}/summary") public ApiResponse<MomentDtos.ProfileSummary> summary(@PathVariable Long id){return ApiResponse.success(service.profile(uid(),id));}
  @GetMapping("/users/{id}/privacy") public ApiResponse<MomentDtos.PreferenceResponse> privacy(@PathVariable Long id){return ApiResponse.success(service.preference(uid(),id));}
  @PutMapping("/users/{id}/privacy") public ApiResponse<MomentDtos.PreferenceResponse> privacy(@PathVariable Long id,@RequestBody MomentDtos.PreferenceRequest r){return ApiResponse.success(service.updatePreference(uid(),id,r));}
  @GetMapping("/privacy") public ApiResponse<MomentDtos.PreferenceResponse> privacyAlias(@RequestParam Long targetUserId){return ApiResponse.success(service.preference(uid(),targetUserId));}
  @PutMapping("/privacy") public ApiResponse<MomentDtos.PreferenceResponse> privacyAlias(@RequestParam Long targetUserId,@RequestBody MomentDtos.PreferenceRequest r){return ApiResponse.success(service.updatePreference(uid(),targetUserId,r));}
  @PutMapping("/privacy/profile") public ApiResponse<String> profileVisibility(@Valid @RequestBody MomentDtos.ProfileVisibilityRequest r){return ApiResponse.success(service.updateProfileVisibility(uid(),r.visibility()));}
  @GetMapping("/privacy/profile") public ApiResponse<MomentDtos.ProfileSummary> profileVisibility(){return ApiResponse.success(service.profile(uid(),uid()));}
  @PostMapping(value="/images",consumes="multipart/form-data") public ApiResponse<MomentDtos.UploadResponse> image(@RequestPart("image")MultipartFile f){return ApiResponse.success(media.store(uid(),f,false));}
  @PostMapping(value="/comment-images",consumes="multipart/form-data") public ApiResponse<MomentDtos.UploadResponse> commentImage(@RequestPart("image")MultipartFile f){return ApiResponse.success(media.store(uid(),f,true));}
  @PostMapping("/{id}/share") public ApiResponse<MessageDtos.ChatMessageResponse> share(@PathVariable Long id,@Valid @RequestBody MomentDtos.ShareRequest r){return ApiResponse.success(service.share(uid(),id,r));}
  @GetMapping("/unread-count") public ApiResponse<MomentDtos.UnreadResponse> unread(){return ApiResponse.success(service.unread(uid()));}
  @PutMapping("/read") public ApiResponse<MomentDtos.UnreadResponse> read(@Valid @RequestBody MomentDtos.MarkReadRequest r){return ApiResponse.success(service.markRead(uid(),r.momentIds()));}
  private String absoluteImage(String url){if(url==null||url.isBlank())return "https://moment.zsj-unknown.top/app_icon.png";if(url.startsWith("http://")||url.startsWith("https://"))return url;return "https://order-menu-backend.zsj-unknown.top"+(url.startsWith("/")?url:"/"+url);}
  private String shareHtml(String title,String description,String image,String target){
    String safeTitle=HtmlUtils.htmlEscape(title);String safeDescription=HtmlUtils.htmlEscape(description.length()>180?description.substring(0,180)+"...":description);String safeImage=HtmlUtils.htmlEscape(image);String safeTarget=HtmlUtils.htmlEscape(target);
    return "<!doctype html><html lang=\"zh-CN\"><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"+
      "<title>"+safeTitle+"</title><meta name=\"title\" content=\""+safeTitle+"\"><meta name=\"description\" content=\""+safeDescription+"\">"+
      "<meta itemprop=\"name\" content=\""+safeTitle+"\"><meta itemprop=\"description\" content=\""+safeDescription+"\"><meta itemprop=\"image\" content=\""+safeImage+"\">"+
      "<meta property=\"og:type\" content=\"article\"><meta property=\"og:site_name\" content=\""+safeTitle+"\">"+
      "<meta property=\"og:title\" content=\""+safeTitle+"\"><meta property=\"og:description\" content=\""+safeDescription+"\">"+
      "<meta property=\"og:image\" content=\""+safeImage+"\"><meta property=\"og:url\" content=\""+safeTarget+"\">"+
      "<meta name=\"twitter:card\" content=\"summary_large_image\"><meta name=\"twitter:title\" content=\""+safeTitle+"\">"+
      "<meta name=\"twitter:description\" content=\""+safeDescription+"\"><meta name=\"twitter:image\" content=\""+safeImage+"\">"+
      "<meta http-equiv=\"refresh\" content=\"0;url="+safeTarget+"\"><link rel=\"canonical\" href=\""+safeTarget+"\"></head>"+
      "<body><p><a href=\""+safeTarget+"\">查看动态详情</a></p><script>location.replace("+jsString(target)+")</script></body></html>";
  }
  private String jsString(String value){return "\""+value.replace("\\","\\\\").replace("\"","\\\"").replace("<","\\u003c").replace(">","\\u003e").replace("&","\\u0026")+"\"";}
  private Long uid(){return UserContext.getOrNull()==null?-1L:UserContext.getOrNull().userId();}
}
