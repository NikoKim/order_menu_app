package com.navy.game;

import com.navy.common.exception.BusinessException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.jdbc.core.JdbcTemplate;

/** Round defaults are private account preferences; manual changes belong to the room. */
final class NavalSpectators {
    private static final long COOLDOWN_MS = 60_000;
    private NavalSpectators() {}

    static void applyDefaults(JdbcTemplate jdbc, NavalState state) {
        List<Long> accounts=state.players.stream().filter(player->!player.left && player.registered && player.key!=null && player.key.matches("U:[0-9]+"))
            .map(player->Long.parseLong(player.key.substring(2))).distinct().toList();
        boolean allowed=state.allowSpectators;
        if(!accounts.isEmpty()) {
            String placeholders=String.join(",",java.util.Collections.nCopies(accounts.size(),"?"));
            // A current read also sees changes committed after an earlier wallet snapshot.
            for(var row:jdbc.queryForList("SELECT id,default_allow_spectators FROM users WHERE id IN ("+placeholders+") ORDER BY id FOR SHARE",accounts.toArray())) {
                Object value=row.get("default_allow_spectators");
                if(Boolean.FALSE.equals(value) || value instanceof Number number && number.intValue()==0)allowed=false;
            }
        }
        NavalEngine.allowSpectators(state,allowed);
    }

    static void change(NavalState state,int actor,Boolean allowed,long now) {
        if(actor<0 || actor>=state.players.size() || state.players.get(actor).left || "AI".equals(state.players.get(actor).key))
            throw new BusinessException(403,"只有对局玩家可以修改观战设置");
        if(allowed==null)throw new BusinessException(400,"请选择是否允许观战");
        if(state.allowSpectators==allowed)throw new BusinessException(409,"观战设置未发生变化");
        if(now<state.spectatorCooldownUntil)throw new BusinessException(409,"观战设置每分钟只能修改一次");
        NavalEngine.allowSpectators(state,allowed);
        state.spectatorChangedAt=now;
        state.spectatorChangedBy=actor;
        state.spectatorChangedByName=Objects.toString(state.players.get(actor).name,"");
        state.spectatorChangedRound=state.round;
        state.spectatorCooldownUntil=now+COOLDOWN_MS;
        state.spectatorSettingsRevision++;
    }

    static Map<String,Object> metadata(NavalState state) {
        Map<String,Object> result=new LinkedHashMap<>();
        result.put("changedAt",state.spectatorChangedAt);
        result.put("changedBy",state.spectatorChangedBy<0?null:state.spectatorChangedBy);
        result.put("changedByName",Objects.toString(state.spectatorChangedByName,""));
        result.put("cooldownUntil",state.spectatorCooldownUntil);
        result.put("round",state.spectatorChangedRound);
        result.put("revision",state.spectatorSettingsRevision);
        return result;
    }
}
