package com.navy.game;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.navy.identity.PlayerIdentity;
import jakarta.annotation.PreDestroy;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.*;

/** One physical connection per page; private game frames keep their existing permission projection. */
@Component @RequiredArgsConstructor
public class PageSocket extends TextWebSocketHandler {
 private final RoomSocket games;private final NavalSocketIdentity identities;private final NavalService service;
 private final NavalCommunityService community;private final NavalPresenceService presence;private final ObjectMapper json;
 private final ConcurrentHashMap<String,Page> pages=new ConcurrentHashMap<>(),lobby=new ConcurrentHashMap<>(),directory=new ConcurrentHashMap<>(),online=new ConcurrentHashMap<>();
 private final ConcurrentHashMap<String,ConcurrentHashMap<String,Page>> mirrors=new ConcurrentHashMap<>();
 private final Set<String> legacy=ConcurrentHashMap.newKeySet(),dirtyRooms=ConcurrentHashMap.newKeySet(),cardRooms=ConcurrentHashMap.newKeySet();
 private final Map<String,JsonNode> directoryState=new LinkedHashMap<>();private long directoryVersion;
 private final AtomicBoolean pending=new AtomicBoolean(),lobbyDirty=new AtomicBoolean(),presenceDirty=new AtomicBoolean(),reloadDirectory=new AtomicBoolean();
 private final AtomicLong queuedBytes=new AtomicLong();
 private final ThreadPoolExecutor feeds=pool("naval-feeds",1,64),senders=pool("naval-page-send",4,128);
 private final ScheduledThreadPoolExecutor timers=timers();
 private static ThreadFactory factory(String name){return task->{Thread thread=new Thread(task,name);thread.setDaemon(true);return thread;};}
 private static ThreadPoolExecutor pool(String name,int workers,int capacity){return new ThreadPoolExecutor(workers,workers,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(capacity),factory(name),new ThreadPoolExecutor.AbortPolicy());}
 private static ScheduledThreadPoolExecutor timers(){var timer=new ScheduledThreadPoolExecutor(2,factory("naval-page-timer"));timer.setRemoveOnCancelPolicy(true);return timer;}
 private record Out(WebSocketMessage<?> message,int bytes,Scoped probeRoom,String probePerson,byte[] nonce,boolean networkFrame){}
 private final class Page {
  final WebSocketSession socket;final PlayerIdentity player;final String token,guest;
  final ArrayDeque<Out> queue=new ArrayDeque<>();final AtomicBoolean sending=new AtomicBoolean(),closed=new AtomicBoolean();
  long bytes;volatile long lastSeen=System.currentTimeMillis(),authenticatedAt=lastSeen,afterId;volatile String cardVersion="",mirrorId;
  volatile Scoped room;boolean lobbyInitial=true;long presenceCount=-1;volatile long dirRevision=-1;Map<String,JsonNode> lastDirectory=Map.of();
  final NavalNetworkProbe network=new NavalNetworkProbe();volatile String networkPerson;
  volatile boolean networkQuality;
  Page(WebSocketSession socket,PlayerIdentity player,String token,String guest){this.socket=socket;this.player=player;this.token=token;this.guest=guest;}
 }
 private final class Scoped extends WebSocketSessionDecorator {
  final Page page;final String roomId;final long scope;
  Scoped(Page page,String room,long scope){super(page.socket);this.page=page;roomId=room;this.scope=scope;}
  @Override public String getId(){return page.socket.getId()+"#"+scope;}
  @Override public void sendMessage(WebSocketMessage<?> message)throws java.io.IOException{
   if(page.closed.get()||page.room!=this)return;
   ObjectNode payload=(ObjectNode)json.readTree(((TextMessage)message).getPayload());if("network".equals(payload.path("type").asText())&&!page.networkQuality)return;payload.put("topic","room");payload.put("scope",scope);payload.put("roomId",roomId);enqueue(page,payload);
   if("snapshot".equals(payload.path("type").asText()))changedRoom(roomId);
  }
  @Override public void close(CloseStatus status){if(page.room==this){page.room=null;page.network.reset();page.networkPerson=null;enqueue(page,Map.of("type","roomError","roomId",roomId,"scope",scope,"code",status.getCode()==1008?403:503));changedRoom(roomId);}}
  @Override public void close(){close(CloseStatus.NORMAL);}
 }
 @Override protected void handleTextMessage(WebSocketSession socket,TextMessage message)throws Exception{
  if(legacy.contains(socket.getId())){games.handleTextMessage(socket,message);return;}
  try{
   if(message.getPayloadLength()>8192)throw new IllegalArgumentException("Oversized subscription");
   JsonNode body=json.readTree(message.getPayload());Page page=pages.get(socket.getId());
   if(page==null){
    if(!body.path("multiplex").asBoolean()){legacy.add(socket.getId());games.handleTextMessage(socket,message);return;}
    if(pages.size()>=3000)throw new IllegalArgumentException("Capacity");
    String token=body.path("token").asText(),guest=body.path("guest").asText();page=new Page(socket,identities.resolve(token,guest),token,guest);pages.put(socket.getId(),page);presenceDirty.set(true);
   }
   if(System.currentTimeMillis()-page.authenticatedAt>30000){if(!identities.resolve(page.token,page.guest).key().equals(page.player.key()))throw new IllegalArgumentException("Identity changed");page.authenticatedAt=System.currentTimeMillis();}
   page.lastSeen=System.currentTimeMillis();presence.socketSeen(page.player,socket.getId());
   if(body.has("subscriptions"))subscribe(page,body);
   else{if(page.room!=null)games.handleTextMessage(page.room,new TextMessage("{}"));if(page.mirrorId!=null)service.mirrorHeartbeat(page.mirrorId,page.player);}
   schedule();
  }catch(Exception failure){Page page=pages.get(socket.getId());if(page==null)socket.close(CloseStatus.POLICY_VIOLATION);else close(page,CloseStatus.POLICY_VIOLATION);}
 }
 @Override protected void handlePongMessage(WebSocketSession socket,PongMessage message){
  Page page=pages.get(socket.getId());if(page==null||page.closed.get()||!page.networkQuality||page.room==null)return;
  Scoped room=page.room;String person=page.networkPerson;
  var sample=page.network.acknowledge(message.getPayload(),System.nanoTime());
  if(sample!=null&&page.networkQuality&&page.room==room&&Objects.equals(person,page.networkPerson))games.networkSample(room,person,sample);
 }
 private void probe(Page page,long now){
  Scoped room=page.room;String person=!page.networkQuality||room==null?null:games.networkPerson(room);
  if(person==null){page.network.reset();page.networkPerson=null;return;}
  if(!person.equals(page.networkPerson)){page.network.reset();page.networkPerson=person;}
  var timeout=page.network.timeout(now);if(timeout!=null)games.networkSample(room,person,timeout);
  byte[] nonce=page.network.prepare(now);
  if(nonce!=null)queue(page,new Out(new PingMessage(ByteBuffer.wrap(nonce)),nonce.length,room,person,nonce,false));
 }
 private void subscribe(Page page,JsonNode body)throws Exception{
  if(body.has("networkQuality")){page.networkQuality=body.path("networkQuality").isBoolean()&&body.path("networkQuality").asBoolean();if(!page.networkQuality){page.network.reset();page.networkPerson=null;}}
  String id=page.socket.getId();JsonNode topics=body.path("subscriptions");
  if(topics.path("lobby").asBoolean()){lobby.put(id,page);page.afterId=Math.max(0,body.path("afterId").asLong());page.cardVersion=body.path("cardVersion").asText();page.lobbyInitial=true;lobbyDirty.set(true);}else lobby.remove(id);
  if(topics.path("directory").asBoolean()){boolean first=directory.isEmpty();if(directory.put(id,page)==null||body.path("forceDirectory").asBoolean()){page.dirRevision=-1;if(first||body.path("forceDirectory").asBoolean())reloadDirectory.set(true);}}else directory.remove(id);
  if(topics.path("presence").asBoolean()){online.put(id,page);page.presenceCount=-1;presenceDirty.set(true);}else online.remove(id);
  String room=body.path("roomId").asText("");long scope=body.path("scope").asLong();
  if(room.length()>80||body.path("mirrorId").asText().length()>80)throw new IllegalArgumentException("Invalid room");
  if(page.room!=null&&(!page.room.roomId.equals(room)||page.room.scope!=scope||body.path("forceSnapshot").asBoolean())){Scoped old=page.room;page.room=null;page.network.reset();page.networkPerson=null;games.afterConnectionClosed(old,CloseStatus.NORMAL);changedRoom(old.roomId);}
  if(!room.isBlank()&&page.room==null){Scoped scoped=new Scoped(page,room,scope);page.room=scoped;games.handleTextMessage(scoped,new TextMessage(json.writeValueAsString(Map.of("roomId",room,"version",body.path("forceSnapshot").asBoolean()?-1:body.path("version").asLong(-1),"token",page.token,"guest",page.guest))));}
  if(page.room!=null)games.networkQuality(page.room,page.networkQuality);
  String mirror=body.path("mirrorId").asText("");String target=mirror.isBlank()?null:mirror;
  if(!Objects.equals(target,page.mirrorId)){removeMirror(page);if(target!=null){page.mirrorId=target;mirrors.compute(target,(key,subscribers)->{if(subscribers==null)subscribers=new ConcurrentHashMap<>();subscribers.put(id,page);return subscribers;});}}
  if(page.mirrorId!=null){if(!room.isBlank())throw new IllegalArgumentException("Conflicting subscriptions");try{enqueue(page,Map.of("type","mirror","state",service.mirrorAudience(page.mirrorId,page.player)));}catch(RuntimeException gone){mirrorMissing(page);}}
 }
 @EventListener public void lobbyChanged(LobbyChanged event){lobbyDirty.set(true);schedule();}
 @TransactionalEventListener(fallbackExecution=true) public void roomChanged(RoomChanged event){changedRoom(event.id());}
 private void changedRoom(String id){if(dirtyRooms.size()<512)dirtyRooms.add(id);else reloadDirectory.set(true);if(cardRooms.contains(id)){community.invalidateCards();lobbyDirty.set(true);}schedule();}
 private void schedule(){if(!pending.compareAndSet(false,true))return;timers.schedule(()->{try{feeds.execute(this::flush);}catch(RejectedExecutionException busy){pending.set(false);}},100,TimeUnit.MILLISECONDS);}
 private void flush(){
  try{
   Set<String> changed=new LinkedHashSet<>(dirtyRooms);dirtyRooms.removeAll(changed);
   if(!directory.isEmpty()){
    Map<String,JsonNode> before=new LinkedHashMap<>(directoryState);
    if(reloadDirectory.getAndSet(false)){directoryState.clear();for(var row:service.rooms())directoryState.put(row.get("id").toString(),directoryRow(row));}
    for(String id:changed){var row=service.directoryCard(id);if(row==null)directoryState.remove(id);else directoryState.put(id,directoryRow(row));}
    if(!before.equals(directoryState))directoryVersion++;
    for(Page page:directory.values())if(page.dirRevision!=directoryVersion){
     if(page.dirRevision<0)enqueue(page,Map.of("type","directory","snapshot",true,"revision",directoryVersion,"rooms",directoryState.values()));
     else{var upsert=directoryState.entrySet().stream().filter(e->!Objects.equals(page.lastDirectory.get(e.getKey()),e.getValue())).map(Map.Entry::getValue).toList();var removed=page.lastDirectory.keySet().stream().filter(key->!directoryState.containsKey(key)).toList();enqueue(page,Map.of("type","directory","snapshot",false,"baseRevision",page.dirRevision,"revision",directoryVersion,"upsert",upsert,"removed",removed));}
     page.lastDirectory=new LinkedHashMap<>(directoryState);page.dirRevision=directoryVersion;
    }
   }else{directoryState.clear();reloadDirectory.set(true);}
   if(lobbyDirty.getAndSet(false))for(Page page:lobby.values()){
    var data=community.syncMessages(page.player,page.afterId,page.cardVersion);var records=(List<?>)data.get("records");var cards=(List<?>)data.get("cards");
    if(page.lobbyInitial||!records.isEmpty()||!page.cardVersion.equals(data.get("cardVersion"))){enqueue(page,Map.of("type","lobby","data",data));page.afterId=((Number)data.get("afterId")).longValue();page.cardVersion=data.get("cardVersion").toString();page.lobbyInitial=false;}
    for(Object value:cards)if(value instanceof Map<?,?> card&&card.get("room") instanceof Map<?,?> room&&room.get("roomId")!=null){if(cardRooms.size()>512)cardRooms.clear();cardRooms.add(room.get("roomId").toString());}
    if(Boolean.TRUE.equals(data.get("hasMore")))lobbyDirty.set(true);
   }
   if(presenceDirty.getAndSet(false)){long count=((Number)presence.count(System.currentTimeMillis()).get("onlineCount")).longValue();for(Page page:online.values())if(page.presenceCount!=count){enqueue(page,Map.of("type","presence","onlineCount",count));page.presenceCount=count;}}
   for(String id:changed){var subscribers=mirrors.get(id);if(subscribers==null)continue;Map<String,Map<String,Object>> views=new HashMap<>();for(Page page:subscribers.values())if(id.equals(page.mirrorId))try{enqueue(page,Map.of("type","mirror","state",views.computeIfAbsent(page.player.key(),key->service.mirrorAudience(id,page.player))));}catch(RuntimeException gone){mirrorMissing(page);}}
  }catch(RuntimeException failure){reloadDirectory.set(true);timers.schedule(()->{lobbyDirty.set(true);presenceDirty.set(true);schedule();},2,TimeUnit.SECONDS);}
  finally{pending.set(false);if(lobbyDirty.get()||presenceDirty.get()||!dirtyRooms.isEmpty())schedule();}
 }
 private void removeMirror(Page page){String previous=page.mirrorId;page.mirrorId=null;if(previous!=null)mirrors.computeIfPresent(previous,(key,subscribers)->{subscribers.remove(page.socket.getId(),page);return subscribers.isEmpty()?null:subscribers;});}
 private void mirrorMissing(Page page){String id=page.mirrorId;removeMirror(page);if(id!=null)enqueue(page,Map.of("type","mirrorError","id",id));}
 private JsonNode directoryRow(Map<String,Object> source){var row=new LinkedHashMap<>(source);row.put("spectators",games.viewerCount(source.get("id").toString()));return json.valueToTree(row);}
 private void enqueue(Page page,Object value){
    if(page.closed.get())return;
  try{String encoded=json.writeValueAsString(value);int bytes=encoded.getBytes(StandardCharsets.UTF_8).length;
   boolean network=value instanceof JsonNode node?"network".equals(node.path("type").asText()):value instanceof Map<?,?> map&&"network".equals(map.get("type"));
   queue(page,new Out(new TextMessage(encoded),bytes,null,null,null,network));
  }catch(Exception failed){close(page,CloseStatus.SERVER_ERROR);}
 }
 private void queue(Page page,Out out){
  synchronized(page.queue){if(page.closed.get())return;
   // Measurements are expendable: never fill the business queue or wait behind game frames.
   boolean optional=out.probeRoom()!=null||out.networkFrame();
   if(optional&&(page.sending.get()||!page.queue.isEmpty()||senders.getQueue().remainingCapacity()<=64)){defer(page,out);return;}
   if(page.queue.size()>=16||page.bytes+out.bytes()>1048576||queuedBytes.get()+out.bytes()>67108864){if(optional)defer(page,out);else close(page,new CloseStatus(1013,"Resynchronize"));return;}page.queue.add(out);page.bytes+=out.bytes();queuedBytes.addAndGet(out.bytes());}
  startSend(page);
 }
 private void defer(Page page,Out out){if(out.probeRoom()!=null)page.network.defer(out.nonce());}
 private void startSend(Page page){if(!page.sending.compareAndSet(false,true))return;try{senders.execute(()->send(page));}catch(RejectedExecutionException full){synchronized(page.queue){for(var items=page.queue.iterator();items.hasNext();){Out out=items.next();if(out.probeRoom()!=null||out.networkFrame()){items.remove();page.bytes-=out.bytes();queuedBytes.addAndGet(-out.bytes());defer(page,out);}}page.sending.set(false);if(!page.queue.isEmpty())close(page,new CloseStatus(1013,"Resynchronize"));}}}
 private void send(Page page){try{for(int n=0;n<8&&!page.closed.get();n++){Out out;synchronized(page.queue){out=page.queue.poll();if(out==null)break;page.bytes-=out.bytes();queuedBytes.addAndGet(-out.bytes());}
  if(out.networkFrame()&&!page.networkQuality)continue;
  if(out.probeRoom()!=null){if(page.room!=out.probeRoom()||!Objects.equals(page.networkPerson,out.probePerson())||!Objects.equals(games.networkPerson(out.probeRoom()),out.probePerson())||!page.network.sent(out.nonce(),System.nanoTime())){defer(page,out);continue;}}
  var timeout=timers.schedule(()->close(page,new CloseStatus(1013,"Slow connection")),5,TimeUnit.SECONDS);try{page.socket.sendMessage(out.message());}finally{timeout.cancel(false);}}}catch(Exception failed){close(page,CloseStatus.SERVER_ERROR);}finally{page.sending.set(false);synchronized(page.queue){if(!page.queue.isEmpty()&&!page.closed.get())startSend(page);}}}
private void close(Page page,CloseStatus status){if(!page.closed.compareAndSet(false,true))return;pages.remove(page.socket.getId(),page);lobby.remove(page.socket.getId(),page);directory.remove(page.socket.getId(),page);online.remove(page.socket.getId(),page);removeMirror(page);presence.socketGone(page.socket.getId());presenceDirty.set(true);if(page.room!=null){Scoped old=page.room;page.room=null;games.afterConnectionClosed(old,status);changedRoom(old.roomId);}synchronized(page.queue){queuedBytes.addAndGet(-page.bytes);page.bytes=0;page.queue.clear();}try{page.socket.close(status);}catch(Exception ignored){}schedule();}
 @Override public void afterConnectionClosed(WebSocketSession socket,CloseStatus status){if(legacy.remove(socket.getId()))games.afterConnectionClosed(socket,status);Page page=pages.get(socket.getId());if(page!=null)close(page,status);}
 @Scheduled(fixedDelay=5000) public void collect(){long now=System.currentTimeMillis(),monotonic=System.nanoTime();for(Page page:pages.values())if(now-page.lastSeen>60000)close(page,CloseStatus.SESSION_NOT_RELIABLE);else probe(page,monotonic);presenceDirty.set(true);schedule();}
 @PreDestroy public void shutdown(){feeds.shutdownNow();senders.shutdownNow();timers.shutdownNow();}
}
