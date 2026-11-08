package com.navy.identity;

import com.navy.common.exception.BusinessException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.navy.game.NavalShop;
import com.navy.security.UserContext;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationService {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final JdbcTemplate jdbc;
    private final Clock clock;
    public record ItemGift(String itemId,int quantity,int durationHours) {}

    @org.springframework.beans.factory.annotation.Autowired
    public NotificationService(JdbcTemplate jdbc){this(jdbc,Clock.systemUTC());}
    NotificationService(JdbcTemplate jdbc,Clock clock){this.jdbc=jdbc;this.clock=clock;}

    public Map<String,Object> summary() {
        long user = UserContext.get().userId();
        return Map.of("unreadCount", unread(user));
    }

    public Map<String,Object> list(Long beforeId, int limit) {
        long user = UserContext.get().userId();
        if (beforeId != null && beforeId < 1 || limit < 1 || limit > 100) bad("消息分页参数不正确");
        String sql = "SELECT * FROM account_notifications WHERE user_id=?";
        List<Object> args = new ArrayList<>();
        args.add(user);
        if (beforeId != null) { sql += " AND id<?"; args.add(beforeId); }
        args.add(limit + 1);
        var rows = jdbc.queryForList(sql + " ORDER BY id DESC LIMIT ?", args.toArray());
        boolean more = rows.size() > limit;
        long now=clock.millis();
        var items = rows.stream().limit(limit).map(row->view(row,now)).toList();
        var result = new LinkedHashMap<String,Object>();
        result.put("items", items);
        result.put("unreadCount", unread(user));
        result.put("hasMore", more);
        result.put("nextBeforeId", items.isEmpty() ? null : items.get(items.size()-1).get("id"));
        return result;
    }

    public void read(long id) {
        long user = UserContext.get().userId();
        if (jdbc.queryForObject("SELECT COUNT(*) FROM account_notifications WHERE id=? AND user_id=?",Integer.class,id,user) != 1)
            throw new BusinessException(404,"消息不存在");
        jdbc.update("UPDATE account_notifications SET read_at=COALESCE(read_at,?) WHERE id=? AND user_id=?",clock.millis(),id,user);
    }

    @Transactional
    public Map<String,Object> claim(long id) {
        long user = UserContext.get().userId();
        var rows = jdbc.queryForList("SELECT * FROM account_notifications WHERE id=? AND user_id=? FOR UPDATE",id,user);
        if (rows.isEmpty()) throw new BusinessException(404,"消息不存在");
        var notification = rows.get(0);
        int amount = ((Number)notification.get("coins")).intValue();
        var attachments=attachments(notification);
        if (amount <= 0 && attachments.isEmpty()) bad("这条消息没有可领取的附件");
        boolean alreadyClaimed = notification.get("claimed_at") != null;
        checkClaimDeadline(notification,alreadyClaimed,clock.millis());
        String key = "U:" + user;
        jdbc.update("INSERT INTO naval_progression_profiles(player_key) VALUES(?) ON DUPLICATE KEY UPDATE player_key=VALUES(player_key)",key);
        var wallet = jdbc.queryForMap("SELECT coins,coin_fraction FROM naval_progression_profiles WHERE player_key=? FOR UPDATE",key);
        long coins = ((Number)wallet.get("coins")).longValue();
        if (!alreadyClaimed) {
            long now=clock.millis();
            checkClaimDeadline(notification,false,now);
            try { coins = Math.addExact(coins,amount); }
            catch (ArithmeticException e) { throw new BusinessException(409,"金币余额已达上限，暂时无法领取"); }
            jdbc.update("UPDATE naval_progression_profiles SET coins=? WHERE player_key=?",coins,key);
            int index=0;
            for(var item:attachments){
                int quantity=((Number)item.get("quantity")).intValue(),hours=((Number)item.get("durationHours")).intValue();
                long expiry=Math.addExact(now,hours*3600000L);
                for(int count=0;count<quantity;count++)jdbc.update("INSERT INTO naval_shop_inventory(player_key,item_id,purchased_at,purchase_day,expires_at,acquisition_source,source_notification_id,source_item_index) VALUES(?,?,?,NULL,?,'GIFT',?,?)",key,item.get("itemId"),now,expiry,id,index++);
            }
            jdbc.update("UPDATE account_notifications SET claimed_at=?,read_at=COALESCE(read_at,?) WHERE id=? AND user_id=?",now,now,id,user);
        }
        int fraction = ((Number)wallet.get("coin_fraction")).intValue();
        return Map.of("id",id,"claimed",true,"alreadyClaimed",alreadyClaimed,"coinsAwarded",alreadyClaimed ? 0 : amount,
            "coins",BigDecimal.valueOf(coins).add(BigDecimal.valueOf(fraction,2)),"itemsGranted",alreadyClaimed?List.of():attachments,
            "itemCountAwarded",alreadyClaimed?0:attachments.stream().mapToInt(item->((Number)item.get("quantity")).intValue()).sum());
    }

    public List<Map<String,Object>> users(String query) {
        requireAdmin();
        String term = query == null ? "" : query.trim();
        if (term.length() > 254) bad("搜索内容不能超过254字");
        if (term.isEmpty()) return jdbc.queryForList("SELECT id,nickname,email,avatar_url AS avatarUrl FROM users ORDER BY id DESC LIMIT 100");
        String like = "%" + term.replace("!","!!").replace("%","!%").replace("_","!_") + "%";
        return jdbc.queryForList("SELECT id,nickname,email,avatar_url AS avatarUrl FROM users WHERE nickname LIKE ? ESCAPE '!' OR email LIKE ? ESCAPE '!' OR CAST(id AS CHAR)=? ORDER BY id DESC LIMIT 100",like,like,term);
    }

    public List<Map<String,Object>> giftItems(){requireAdmin();return NavalShop.giftCatalog();}

    @Transactional
    public Map<String,Object> send(List<Long> userIds, String title, String content, Integer coins, String requestId) {
        return send(userIds,title,content,coins,requestId,List.of());
    }

    @Transactional
    public Map<String,Object> send(List<Long> userIds, String title, String content, Integer coins, String requestId,List<ItemGift> items) {
        long admin = requireAdmin();
        try {
            if (requestId == null || !UUID.fromString(requestId).toString().equalsIgnoreCase(requestId)) bad("发送请求标识不正确，请刷新页面");
        } catch (IllegalArgumentException e) { bad("发送请求标识不正确，请刷新页面"); }
        requestId = requestId.toLowerCase(java.util.Locale.ROOT);
        if (userIds == null || userIds.isEmpty() || userIds.size() > 100 || userIds.stream().anyMatch(id -> id == null || id < 1)) bad("请选择1至100个接收账号");
        title = title == null ? "" : title.trim();
        content = content == null ? "" : content.trim();
        if (title.isEmpty() || title.length() > 120 || content.isEmpty() || content.length() > 4000) bad("标题为1至120字，内容为1至4000字");
        int gift = coins == null ? 0 : coins;
        if (gift < 0 || gift > 1_000_000) bad("赠送金币必须为0至1000000的整数");
        List<Map<String,Object>> gifts=normalizeItems(items);
        var recipients = new TreeSet<>(userIds);
        String placeholders = String.join(",", java.util.Collections.nCopies(recipients.size(),"?"));
        int existing = jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE id IN (" + placeholders + ")",Integer.class,recipients.toArray());
        if (existing != recipients.size()) bad("部分接收账号不存在，请重新选择");
        long now = clock.millis();
        String hash;
        try { hash = GuestService.hash(JSON.writeValueAsString(gifts.isEmpty()?List.of(recipients,title,content,gift):List.of(recipients,title,content,gift,gifts.stream().map(item->List.of(item.get("itemId"),item.get("quantity"),item.get("durationHours"))).toList()))); }
        catch (JsonProcessingException e) { throw new IllegalStateException("Cannot encode notification request",e); }
        jdbc.update("INSERT INTO admin_notification_requests(admin_id,request_id,payload_hash,coins,created_at) VALUES(?,?,?,?,?) ON DUPLICATE KEY UPDATE request_id=VALUES(request_id)",admin,requestId,hash,gift,now);
        var receipt = jdbc.queryForMap("SELECT payload_hash,sent_count,coins FROM admin_notification_requests WHERE admin_id=? AND request_id=? FOR UPDATE",admin,requestId);
        if (!hash.equals(receipt.get("payload_hash"))) throw new BusinessException(409,"发送请求已经变更，请使用新的请求标识");
        if (receipt.get("sent_count") != null) return Map.of("sent",receipt.get("sent_count"),"coinsPerUser",receipt.get("coins"));
        String itemJson;
        try { itemJson=gifts.isEmpty()?null:JSON.writeValueAsString(gifts); }
        catch(JsonProcessingException e){throw new IllegalStateException("Cannot encode item gifts",e);}
        Long deadline=gift>0||!gifts.isEmpty()?Math.addExact(now,86400000L):null;
        for (long recipient : recipients) {
            jdbc.update("INSERT INTO account_notifications(user_id,sender_id,title,content,coins,created_at,items_json,claim_expires_at) VALUES(?,?,?,?,?,?,?,?)",recipient,admin,title,content,gift,now,itemJson,deadline);
        }
        jdbc.update("UPDATE admin_notification_requests SET sent_count=? WHERE admin_id=? AND request_id=?",recipients.size(),admin,requestId);
        return Map.of("sent",recipients.size(),"coinsPerUser",gift);
    }

    private long requireAdmin() {
        long user = UserContext.get().userId();
        if (!Integer.valueOf(1).equals(jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE id=? AND is_admin=TRUE",Integer.class,user)))
            throw new BusinessException(403,"仅管理员可以执行此操作");
        return user;
    }

    private long unread(long user) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM account_notifications WHERE user_id=? AND read_at IS NULL",Long.class,user);
    }

    private static Map<String,Object> view(Map<String,Object> row,long now) {
        var item = new LinkedHashMap<String,Object>();
        for (String key : List.of("id","title","content","coins")) item.put(key,row.get(key));
        item.put("createdAt",row.get("created_at"));
        item.put("read",row.get("read_at") != null);
        item.put("claimed",row.get("claimed_at") != null);
        var attachments=attachments(row);
        item.put("items",attachments);
        Long deadline=row.get("claim_expires_at") instanceof Number value?value.longValue():null;
        boolean claimed=row.get("claimed_at")!=null,expired=!claimed&&deadline!=null&&deadline<=now;
        item.put("claimExpiresAt",deadline);
        item.put("expired",expired);
        item.put("canClaim",!claimed&&!expired&&(((Number)row.get("coins")).intValue()>0||!attachments.isEmpty()));
        return item;
    }

    private static List<Map<String,Object>> normalizeItems(List<ItemGift> items){
        if(items==null||items.isEmpty())return List.of();
        if(items.size()>5)bad("一次最多赠送5种物品");
        Map<String,Map<String,Object>> sorted=new java.util.TreeMap<>();int total=0;
        for(var item:items){
            if(item==null||item.quantity()<1||item.quantity()>99||item.durationHours()<1||item.durationHours()>8760)bad("物品数量为1至99，有效期为1至8760小时");
            var catalog=NavalShop.giftCatalog().stream().filter(row->row.get("id").equals(item.itemId())).findFirst().orElseThrow(()->new BusinessException(400,"不支持赠送该物品"));
            if(sorted.containsKey(item.itemId()))bad("同一种物品请合并后赠送");
            total+=item.quantity();if(total>100)bad("每位玩家一次最多收到100件物品");
            sorted.put(item.itemId(),Map.of("itemId",item.itemId(),"name",catalog.get("name"),"category",catalog.get("category"),"quantity",item.quantity(),"durationHours",item.durationHours()));
        }
        return List.copyOf(sorted.values());
    }

    private static List<Map<String,Object>> attachments(Map<String,Object> row){
        Object value=row.get("items_json");if(value==null)return List.of();
        try{return JSON.readValue(value.toString(),new TypeReference<List<Map<String,Object>>>(){});}
        catch(JsonProcessingException e){throw new IllegalStateException("Invalid notification item gifts",e);}
    }

    private static void checkClaimDeadline(Map<String,Object> notification,boolean claimed,long now){
        if(!claimed&&notification.get("claim_expires_at") instanceof Number value&&value.longValue()<=now)throw new BusinessException(409,"赠送邮件已过期，请在24小时内领取");
    }

    private static void bad(String message) { throw new BusinessException(400,message); }
}
