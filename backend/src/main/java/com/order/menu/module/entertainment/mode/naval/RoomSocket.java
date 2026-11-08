package com.order.menu.module.entertainment.mode.naval;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.order.menu.module.entertainment.core.service.PlayerIdentity;
import jakarta.annotation.PreDestroy;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Component @RequiredArgsConstructor
public class RoomSocket extends TextWebSocketHandler {
    private static final int MAX_CLIENTS=3000,MAX_ROOMS=256,HISTORY_BYTES=1048576;
    private static final CloseStatus RESYNC=new CloseStatus(1013,"Reconnect to resynchronize");
    private final NavalService service;
    private final NavalSocketIdentity identities;
    private final ObjectMapper json;
    private final ConcurrentHashMap<String,Channel> rooms=new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String,Client> sessions=new ConcurrentHashMap<>();
    private final ThreadPoolExecutor loaders=pool("naval-room-load",2,64),senders=pool("naval-room-send",4,128);
    private final ScheduledExecutorService watchdog=watchdog();
    private static ScheduledExecutorService watchdog(){var executor=new ScheduledThreadPoolExecutor(2,threads("naval-send-watch"));executor.setRemoveOnCancelPolicy(true);return executor;}
    private static ThreadFactory threads(String name){AtomicInteger n=new AtomicInteger();return task->{Thread t=new Thread(task,name+"-"+n.incrementAndGet());t.setDaemon(true);return t;};}
    private static ThreadPoolExecutor pool(String name,int size,int capacity){return new ThreadPoolExecutor(size,size,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(capacity),threads(name),new ThreadPoolExecutor.AbortPolicy());}
    private static final class Channel {
        final String id;final ConcurrentHashMap<String,Client> clients=new ConcurrentHashMap<>();
        final AtomicBoolean dirty=new AtomicBoolean(),loading=new AtomicBoolean();
        final LinkedHashMap<String,Frame> history=new LinkedHashMap<>();
        final LinkedHashMap<String,Long> delivered=new LinkedHashMap<>();
        long bytes,lastAccess=System.currentTimeMillis();
        volatile NavalState latest;
        Channel(String id){this.id=id;}
        synchronized Frame previous(String group,long version){Frame frame=history.get(group+":"+version);return frame!=null&&System.currentTimeMillis()-frame.at<60000?frame:null;}
        synchronized Frame resume(String player,String group,long version){Long at=delivered.get(player+":"+group+":"+version);return at!=null&&System.currentTimeMillis()-at<60000?previous(group,version):null;}
        synchronized void delivered(String player,Frame frame){delivered.put(player+":"+frame.group+":"+frame.version,System.currentTimeMillis());while(delivered.size()>1024){var i=delivered.entrySet().iterator();i.next();i.remove();}}
        synchronized void remember(Frame frame){String key=frame.group+":"+frame.version;Frame old=history.put(key,frame);bytes+=frame.bytes-(old==null?0:old.bytes);while(bytes>HISTORY_BYTES||history.size()>48){var i=history.entrySet().iterator();if(!i.hasNext())break;bytes-=i.next().getValue().bytes;i.remove();}}
    }
    private static final class Frame {
        final String group;final long version,at=System.currentTimeMillis();final ObjectNode state;final int bytes;
        final ShortCache deltas=new ShortCache(4);
        Frame(String group,ObjectNode state,int bytes){this.group=group;this.state=state;this.bytes=bytes;version=state.path("version").asLong();}
    }
    private record Delivery(Frame frame,String spectatorId){}
    private static final class Client {
        final WebSocketSession socket;final Channel channel;final PlayerIdentity player;final long requestedVersion;final String token,guest;
        final AtomicReference<Delivery> pending=new AtomicReference<>();final AtomicBoolean sending=new AtomicBoolean(),closed=new AtomicBoolean();
        volatile Frame last;volatile String spectatorId;volatile boolean forceSnapshot;
        volatile long authenticatedAt=System.currentTimeMillis();
        Client(WebSocketSession socket,Channel channel,PlayerIdentity player,long version,String token,String guest){this.socket=socket;this.channel=channel;this.player=player;requestedVersion=version;this.token=token;this.guest=guest;}
    }
    @Override protected void handleTextMessage(WebSocketSession socket,TextMessage message)throws Exception {
        try{
            if(message.getPayloadLength()>4096)throw new IllegalArgumentException("Oversized request");
            Client client=sessions.get(socket.getId());
            if(client==null){
                JsonNode input=json.readTree(message.getPayload());String room=input.path("roomId").asText();
                if(room.length()>80||room.isBlank()||sessions.size()>=MAX_CLIENTS||rooms.size()>=MAX_ROOMS&&!rooms.containsKey(room))throw new IllegalArgumentException("Capacity");
                String token=input.path("token").asText(),guest=input.path("guest").asText();
                PlayerIdentity player=identities.resolve(token,guest);
                service.heartbeat(room,player);
                rooms.compute(room,(id,existing)->{Channel channel=existing==null?new Channel(id):existing;Client registered=new Client(socket,channel,player,input.path("version").asLong(-1),token,guest);channel.clients.put(socket.getId(),registered);channel.lastAccess=System.currentTimeMillis();sessions.put(socket.getId(),registered);return channel;});
                client=sessions.get(socket.getId());
            }else{
                if(System.currentTimeMillis()-client.authenticatedAt>=30000){PlayerIdentity renewed=identities.resolve(client.token,client.guest);if(!renewed.key().equals(client.player.key()))throw new IllegalArgumentException("Identity changed");client.authenticatedAt=System.currentTimeMillis();}
                if(client.channel.latest!=null)service.heartbeatSnapshot(client.channel.latest,client.player);else service.heartbeat(client.channel.id,client.player);
            }
            client.channel.lastAccess=System.currentTimeMillis();
            if(client.channel.latest!=null&&client.last!=null&&!client.channel.dirty.get()&&!client.channel.loading.get()){
                var audience=RoomViews.audience(client.channel.latest,client.player);Frame latest=client.channel.previous(audience.group(),client.channel.latest.version);
                if(latest!=null)offer(client,new Delivery(latest,audience.spectatorId()));else request(client.channel);
            }else request(client.channel);
        }catch(Exception failure){Client client=sessions.get(socket.getId());if(client!=null)close(client,CloseStatus.POLICY_VIOLATION);else socket.close(CloseStatus.POLICY_VIOLATION);}
    }
    @TransactionalEventListener(fallbackExecution=true)
    public void changed(RoomChanged event){Channel channel=rooms.get(event.id());if(channel!=null&&!channel.clients.isEmpty())request(channel);}
    private void request(Channel channel){
        channel.dirty.set(true);
        if(!channel.loading.compareAndSet(false,true))return;
        try{loaders.execute(()->drainRoom(channel));}
        catch(RejectedExecutionException overloaded){channel.loading.set(false);channel.clients.values().forEach(c->c.forceSnapshot=true);}
    }
    private void drainRoom(Channel channel){
        try{
            if(channel.dirty.getAndSet(false)&&!channel.clients.isEmpty()){
                // This read runs after commit, once for the whole room, never per receiver.
                NavalState state=service.committedRoom(channel.id);channel.latest=state;long now=System.currentTimeMillis();
                Map<String,Frame> views=new HashMap<>();
                for(Client client:channel.clients.values()){
                    try{
                        RoomViews.Audience audience=RoomViews.audience(state,client.player);
                        Frame frame=views.computeIfAbsent(audience.group(),group->{
                            Frame prior=channel.previous(group,state.version);
                            if(prior!=null)return prior;
                            ObjectNode view=json.valueToTree(RoomViews.view(state,audience,now));
                            Frame next=new Frame(group,view,view.toString().length()*2);channel.remember(next);return next;
                        });
                        if(client.last==null&&client.requestedVersion>=0&&!client.forceSnapshot)client.last=channel.resume(client.player.key(),frame.group,client.requestedVersion);
                        offer(client,new Delivery(frame,audience.spectatorId()));
                    }catch(RuntimeException forbidden){close(client,CloseStatus.POLICY_VIOLATION);}
                }
            }
        }catch(RuntimeException unavailable){channel.clients.values().forEach(c->close(c,RESYNC));}
        finally{channel.loading.set(false);if(channel.dirty.get()&&!channel.clients.isEmpty())request(channel);}
    }
    private void offer(Client client,Delivery delivery){
        if(client.closed.get())return;
        // A single pending slot coalesces bursts. The baseline advances only after a successful send.
        if(client.last!=null&&delivery.frame().version<client.last.version)return;
        client.pending.accumulateAndGet(delivery,(prior,next)->prior!=null&&prior.frame().version>next.frame().version?prior:next);schedule(client);
    }
    private void schedule(Client client){
        if(!client.sending.compareAndSet(false,true))return;
        try{senders.execute(()->drainClient(client));}
        catch(RejectedExecutionException overloaded){client.sending.set(false);close(client,RESYNC);}
    }
    private void drainClient(Client client){
        try{
            Delivery delivery;
            while(!client.closed.get()&&(delivery=client.pending.getAndSet(null))!=null){
                Frame next=delivery.frame(),old=client.last;
                boolean snapshot=client.forceSnapshot||old==null||!old.group.equals(next.group)||!Objects.equals(client.spectatorId,delivery.spectatorId());
                Map<String,Object> payload=new LinkedHashMap<>();payload.put("version",next.version);
                if(snapshot){
                    ObjectNode personal=json.createObjectNode();personal.setAll(next.state);personal.put("ownSpectatorId",delivery.spectatorId());
                    payload.put("type","snapshot");payload.put("state",personal);
                }else if(old.version==next.version){payload.put("type","unchanged");}
                else{
                    Frame baseline=old;payload.put("type","delta");payload.put("baseVersion",old.version);
                    payload.put("changes",next.deltas.get(Long.toString(old.version),60000,()->{List<Map<String,Object>> changes=new ArrayList<>();diff(baseline.state,next.state,new ArrayList<>(),changes);return changes;}));
                }
                ScheduledFuture<?> timeout=watchdog.schedule(()->close(client,RESYNC),5,TimeUnit.SECONDS);
                try{payload.put("serverTime",System.currentTimeMillis());client.socket.sendMessage(new TextMessage(json.writeValueAsString(payload)));}
                finally{timeout.cancel(false);}
                client.last=next;client.spectatorId=delivery.spectatorId();client.forceSnapshot=false;client.channel.delivered(client.player.key(),next);
            }
        }catch(Exception failed){close(client,RESYNC);}
        finally{client.sending.set(false);if(client.pending.get()!=null&&!client.closed.get())schedule(client);}
    }
    static void diff(JsonNode old,JsonNode next,List<Object> path,List<Map<String,Object>> changes){
        if(Objects.equals(old,next))return;
        if(old!=null&&old.isObject()&&next.isObject()){
            Set<String> keys=new LinkedHashSet<>();old.fieldNames().forEachRemaining(keys::add);next.fieldNames().forEachRemaining(keys::add);
            for(String key:keys){List<Object> child=new ArrayList<>(path);child.add(key);if(!next.has(key))changes.add(Map.of("path",child,"remove",true));else diff(old.get(key),next.get(key),child,changes);}return;
        }
        if(old!=null&&old.isArray()&&next.isArray()&&old.size()<=next.size()){
            for(int i=0;i<next.size();i++){List<Object> child=new ArrayList<>(path);child.add(i);diff(i<old.size()?old.get(i):null,next.get(i),child,changes);}return;
        }
        changes.add(Map.of("path",path,"value",next));
    }
    private void remove(Client client){client.closed.set(true);client.pending.set(null);sessions.remove(client.socket.getId(),client);client.channel.clients.remove(client.socket.getId(),client);client.channel.lastAccess=System.currentTimeMillis();}
    int viewerCount(String id){Channel channel=rooms.get(id);return channel==null?0:(int)channel.clients.values().stream().filter(c->!c.closed.get()&&c.last!=null&&"SPECTATOR".equals(c.last.state.path("role").asText())).map(c->c.player.key()).distinct().count();}
    private void close(Client client,CloseStatus status){if(!client.closed.compareAndSet(false,true))return;remove(client);try{client.socket.close(status);}catch(Exception ignored){}}
    @Override public void afterConnectionClosed(WebSocketSession session,CloseStatus status){Client client=sessions.get(session.getId());if(client!=null)remove(client);}
    @Scheduled(fixedDelay=60000) public void cleanup(){long now=System.currentTimeMillis();for(String id:rooms.keySet())rooms.computeIfPresent(id,(key,channel)->channel.clients.isEmpty()&&now-channel.lastAccess>60000?null:channel);}
    @PreDestroy public void shutdown(){loaders.shutdownNow();senders.shutdownNow();watchdog.shutdownNow();}
}
