package com.navy.game;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.navy.common.exception.BusinessException;
import com.navy.identity.GuestService;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

public final class NavalShop {
    record Item(String id,String name,String category,int price,int durationHours,int dailyLimit,String description) {}
    record Bubble(String style,long expiresAt) {}
    static final List<Item> ITEMS=List.of(
        new Item("task-reset","任务重置卡","CARD",50,24,1,"重置今日任务，未领取的已完成奖励保留"),
        new Item("score-100","100等级分卡","CARD",50,24,1,"增加100等级分，不能跳过晋升考核"),
        new Item("bubble-captain","船长聊天气泡","DECORATION",50,168,0,"船长徽章与航海装饰"),
        new Item("bubble-pirate","海盗聊天气泡","DECORATION",50,168,0,"海盗徽记与航海宝藏"),
        new Item("bubble-tabby","橘猫聊天气泡","DECORATION",100,168,0,"橘猫与猫爪装饰")
    );
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final Clock clock;
    NavalShop(JdbcTemplate jdbc,ObjectMapper json){this(jdbc,json,Clock.systemUTC());}
    NavalShop(JdbcTemplate jdbc,ObjectMapper json,Clock clock){this.jdbc=jdbc;this.json=json;this.clock=clock;}
    private <T>T transaction(Supplier<T> work){
        if(jdbc.getDataSource()==null)return work.get();
        return new TransactionTemplate(new DataSourceTransactionManager(jdbc.getDataSource())).execute(status->work.get());
    }
    static Item item(String id){return ITEMS.stream().filter(item->item.id.equals(id)).findFirst().orElseThrow(()->new BusinessException(404,"物品不存在"));}
    public static List<Map<String,Object>> giftCatalog(){return ITEMS.stream().map(item->Map.<String,Object>of("id",item.id,"name",item.name,"category",item.category,"price",item.price,"durationHours",item.durationHours)).toList();}
    private static void requireAccount(String key){if(key==null||!key.matches("U:[0-9]+"))throw new BusinessException(401,"请先登录账号");}
    private Map<String,Object> lockWallet(String key){
        requireAccount(key);
        jdbc.update("INSERT INTO naval_progression_profiles(player_key) VALUES(?) ON DUPLICATE KEY UPDATE player_key=VALUES(player_key)",key);
        return jdbc.queryForMap("SELECT * FROM naval_progression_profiles WHERE player_key=? FOR UPDATE",key);
    }
    Map<String,Object> profile(String key){return transaction(()->{lockWallet(key);return profileLocked(key);});}
    private Map<String,Object> profileLocked(String key){
        long now=clock.millis();
        var wallet=jdbc.queryForMap("SELECT * FROM naval_progression_profiles WHERE player_key=?",key);
        long whole=((Number)wallet.get("coins")).longValue();
        int fraction=wallet.get("coin_fraction") instanceof Number n?n.intValue():0;
        var inventory=jdbc.queryForList("SELECT i.*,e.inventory_id AS equipped_id FROM naval_shop_inventory i LEFT JOIN naval_shop_equipment e ON e.player_key=i.player_key WHERE i.player_key=? ORDER BY i.id DESC",key);
        Map<String,Long> counts=new LinkedHashMap<>();
        for(var row:jdbc.queryForList("SELECT item_id,COUNT(*) AS amount FROM naval_shop_inventory WHERE player_key=? AND purchase_day=? GROUP BY item_id",key,NavalProgression.day(now).toString()))counts.put(row.get("item_id").toString(),((Number)row.get("amount")).longValue());
        var result=new LinkedHashMap<String,Object>();
        result.put("coins",BigDecimal.valueOf(whole).add(BigDecimal.valueOf(fraction,2)));result.put("serverTime",now);
        result.put("items",ITEMS.stream().map(item->{
            long today=counts.getOrDefault(item.id,0L);var view=new LinkedHashMap<String,Object>();
            view.put("id",item.id);view.put("name",item.name);view.put("category",item.category);view.put("price",item.price);view.put("durationHours",item.durationHours);view.put("dailyLimit",item.dailyLimit);view.put("description",item.description);
            view.put("purchasedToday",today);view.put("canPurchase",whole>=item.price&&(item.dailyLimit==0||today<item.dailyLimit));return view;
        }).toList());
        String equipped=null;
        var views=new java.util.ArrayList<Map<String,Object>>();
        for(var row:inventory){
            String itemId=row.get("item_id").toString();Item item=item(itemId);long id=((Number)row.get("id")).longValue(),expiry=((Number)row.get("expires_at")).longValue();
            String status=row.get("used_at")!=null?"USED":expiry<=now?"EXPIRED":"ACTIVE";
            boolean worn="ACTIVE".equals(status)&&row.get("equipped_id") instanceof Number n&&n.longValue()==id;
            if(worn)equipped=itemId;
            var view=new LinkedHashMap<String,Object>();view.put("id",id);view.put("itemId",itemId);view.put("purchaseRequestId",row.get("purchase_request_id"));view.put("purchasedAt",row.get("purchased_at"));view.put("expiresAt",expiry);view.put("status",status);view.put("equipped",worn);view.put("acquisitionSource",Objects.toString(row.get("acquisition_source"),"PURCHASE"));view.put("sourceNotificationId",row.get("source_notification_id"));
            boolean usable="ACTIVE".equals(status);String unavailable=null;
            if(usable&&"score-100".equals(itemId)){int score=((Number)wallet.get("score")).intValue();if(scoreCardAward(score)!=100){usable=false;unavailable=score>=12000?"已达到最高等级分":"当前等级分不足以完整领取100分，请先完成晋升考核";}}
            view.put("canUse",usable);view.put("unavailableReason",unavailable);view.put("category",item.category);views.add(view);
        }
        result.put("inventory",views);result.put("equippedBubble",equipped);return result;
    }
    Map<String,Object> purchase(String key,String itemId,String requestId){
        Item selected=item(itemId);
        return mutation(key,requestId,List.of("purchase",itemId),()->{
            long now=clock.millis();String today=NavalProgression.day(now).toString();
            if(selected.dailyLimit>0&&jdbc.queryForObject("SELECT COUNT(*) FROM naval_shop_inventory WHERE player_key=? AND item_id=? AND purchase_day=?",Long.class,key,itemId,today)>=selected.dailyLimit)throw new BusinessException(409,"该物品今日购买次数已用完");
            if(jdbc.update("UPDATE naval_progression_profiles SET coins=coins-? WHERE player_key=? AND coins>=?",selected.price,key,selected.price)!=1)throw new BusinessException(400,"金币不足");
            jdbc.update("INSERT INTO naval_shop_inventory(player_key,item_id,purchase_request_id,purchased_at,purchase_day,expires_at) VALUES(?,?,?,?,?,?)",key,itemId,UUID.fromString(requestId).toString(),now,today,Math.addExact(now,selected.durationHours*3600000L));
        });
    }
    Map<String,Object> use(String key,long inventoryId,String requestId){
        return mutation(key,requestId,List.of("use",inventoryId),()->{
            var row=owned(key,inventoryId);Item selected=item(row.get("item_id").toString());
            if(!"CARD".equals(selected.category))throw new BusinessException(400,"装饰请使用装备操作");
            requireActive(row);
            NavalProgression progression=new NavalProgression(jdbc,json,clock);
            if("score-100".equals(selected.id))progression.useScoreCard(key);else progression.resetTasks(key);
            jdbc.update("UPDATE naval_shop_inventory SET used_at=? WHERE id=? AND player_key=?",clock.millis(),inventoryId,key);
        });
    }
    Map<String,Object> equip(String key,Long inventoryId,String requestId){
        return mutation(key,requestId,List.of("equip",inventoryId==null?0L:inventoryId),()->{
            if(inventoryId!=null){var row=owned(key,inventoryId);requireActive(row);if(!"DECORATION".equals(item(row.get("item_id").toString()).category))throw new BusinessException(400,"只有装饰可以装备");}
            jdbc.update("INSERT INTO naval_shop_equipment(player_key,inventory_id) VALUES(?,?) ON DUPLICATE KEY UPDATE inventory_id=VALUES(inventory_id)",key,inventoryId);
        });
    }
    private Map<String,Object> mutation(String key,String requestId,Object payload,Runnable work){
        requireAccount(key);String normalized;
        try{normalized=UUID.fromString(requestId).toString();if(!normalized.equalsIgnoreCase(requestId))throw new IllegalArgumentException();}catch(Exception e){throw new BusinessException(400,"请求标识无效，请刷新后重试");}
        String id=normalized,hash;
        try{hash=GuestService.hash(json.writeValueAsString(payload));}catch(Exception e){throw new IllegalStateException(e);}
        String payloadHash=hash;
        return transaction(()->{
            lockWallet(key);
            jdbc.update("INSERT INTO naval_shop_requests(player_key,request_id,payload_hash) VALUES(?,?,?) ON DUPLICATE KEY UPDATE request_id=VALUES(request_id)",key,id,payloadHash);
            var receipt=jdbc.queryForMap("SELECT payload_hash,completed_at FROM naval_shop_requests WHERE player_key=? AND request_id=? FOR UPDATE",key,id);
            if(!Objects.equals(payloadHash,receipt.get("payload_hash")))throw new BusinessException(409,"请求内容已变更，请重新操作");
            if(receipt.get("completed_at")==null){work.run();jdbc.update("UPDATE naval_shop_requests SET completed_at=? WHERE player_key=? AND request_id=?",clock.millis(),key,id);}
            return profileLocked(key);
        });
    }
    private Map<String,Object> owned(String key,long id){var rows=jdbc.queryForList("SELECT * FROM naval_shop_inventory WHERE id=? AND player_key=? FOR UPDATE",id,key);if(rows.isEmpty())throw new BusinessException(404,"物品不存在");return rows.get(0);}
    private void requireActive(Map<String,Object> row){if(row.get("used_at")!=null)throw new BusinessException(409,"该物品已经使用");if(((Number)row.get("expires_at")).longValue()<=clock.millis())throw new BusinessException(409,"该物品已经过期");}
    static int scoreCardAward(int score){return NavalProgression.advance(score,0,false,false,0,List.of(100),false).taskScores().get(0);}
    Bubble bubble(String key){
        if(key==null||!key.matches("U:[0-9]+"))return new Bubble(null,0);
        var rows=jdbc.queryForList("SELECT i.item_id,i.expires_at FROM naval_shop_equipment e JOIN naval_shop_inventory i ON i.id=e.inventory_id AND i.player_key=e.player_key WHERE e.player_key=? AND i.used_at IS NULL AND i.expires_at>?",key,clock.millis());
        if(rows.isEmpty())return new Bubble(null,0);
        String id=rows.get(0).get("item_id").toString();
        if(!List.of("bubble-captain","bubble-pirate","bubble-tabby").contains(id))return new Bubble(null,0);
        return new Bubble(id.substring("bubble-".length()),((Number)rows.get(0).get("expires_at")).longValue());
    }
}
