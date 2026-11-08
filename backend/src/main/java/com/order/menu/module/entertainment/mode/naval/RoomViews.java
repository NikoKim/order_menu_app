package com.order.menu.module.entertainment.mode.naval;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.order.menu.module.entertainment.core.service.PlayerIdentity;
import com.order.menu.common.exception.BusinessException;
import java.util.*;

final class RoomViews {
    record Audience(String group,int seat,String role,String mode,String spectatorId){}
    static Audience audience(NavalState state,PlayerIdentity identity){
        int index=-1;
        for(int i=0;i<state.players.size();i++)if(Objects.equals(state.players.get(i).key,identity.key())){index=i;if(!state.players.get(i).left)return new Audience("P:"+i,i,"PLAYER",null,null);}
        if(!state.allowSpectators)throw new BusinessException(403,"房间已关闭观战");
        for(var spectator:state.spectators)if(Objects.equals(spectator.key,identity.key()))return new Audience("S:"+spectator.viewMode,-1,"SPECTATOR",spectator.viewMode,spectator.id);
        if(index>=0)return new Audience("P:"+index,index,"PLAYER",null,null);
        throw new BusinessException(403,"无权查看房间");
    }
    static Map<String,Object> view(NavalState state,Audience audience,long now){
        if(state.matchedRoomId!=null)return Map.of("id",state.id,"version",state.version,"matchedRoomId",state.matchedRoomId,"ruleSet",state.ruleSet);
        var view=NavalEngine.view(state,audience.seat(),audience.role(),audience.mode(),null,now);view.put("version",state.version);return view;
    }
}
