package com.order.menu.module.entertainment.mode.naval;

import java.util.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.order.menu.module.entertainment.mode.naval.NavalState.*;

final class NavalMilitaryTemplate {
    record Template(List<Ship> ships,List<Action> actions) { }
    static Template extract(Map<String,Object> result,ObjectMapper json){
        try {
            if(!"MILITARY".equals(result.get("ruleSet"))||!Boolean.TRUE.equals(result.get("battleStarted"))||!(result.get("winner") instanceof Number n))return null;
            int winner=n.intValue();if(winner<0||winner>1)return null;
            String mode=String.valueOf(result.get("mode"));if(!List.of("AI","MULTIPLAYER").contains(mode)||("AI".equals(mode)&&winner!=0))return null;
            List<?> players=(List<?>)result.get("players");Map<?,?> person=(Map<?,?>)players.get(winner);if("AI".equals(person.get("key")))return null;
            List<Ship> ships=new ArrayList<>();for(Object item:(List<?>)person.get("ships")){Ship s=json.convertValue(item,Ship.class);if("torpedo".equals(s.kind))return null;ships.add(s);}NavalMilitary.validate(ships,true);
            List<Action> actions=new ArrayList<>();for(Object item:(List<?>)result.get("events")){Map<?,?> e=(Map<?,?>)item;if(!(e.get("player") instanceof Number p)||p.intValue()!=winner||!List.of("shot","fighter","bomber","torpedo","radar","mine","submarine","depthCharge").contains(e.get("weapon")))continue;Action a=new Action();a.weapon=(String)e.get("weapon");a.shipId=(String)e.get("shipId");a.x=((Number)e.get("x")).intValue();a.y=((Number)e.get("y")).intValue();a.plane=e.get("plane") instanceof Number plane?plane.intValue():0;actions.add(a);}
            Map<String,String> ids=new HashMap<>();for(int i=0;i<ships.size();i++){Ship s=ships.get(i);String id="template-"+i;ids.put(s.id,id);s.id=id;}for(Action a:actions)a.shipId=ids.get(a.shipId);actions.removeIf(a->a.shipId==null);
            return actions.isEmpty()?null:new Template(ships,actions);
        } catch(RuntimeException invalid){return null;}
    }
    static void install(NavalState state,Template template){
        if(template==null)return;Random random=new java.security.SecureRandom();List<Ship> fleet=template.ships;
        // Sometimes rebuild the full formation; otherwise make only legal local perturbations.
        boolean broad=random.nextDouble()<.4;
        for(Ship ship:fleet){int x=ship.x,y=ship.y,w=ship.w,h=ship.h,r=ship.rotation;
            for(int attempt=0;attempt<(broad?50:5);attempt++){ship.x=broad?random.nextInt(12):Math.max(0,Math.min(11,x+random.nextInt(3)-1));ship.y=broad?random.nextInt(12):Math.max(0,Math.min(11,y+random.nextInt(3)-1));ship.rotation=(r+(random.nextDouble()<.3?90:0))%360;int[] m=NavalMilitary.MODELS.get(ship.kind);ship.w=ship.rotation%180==0?m[0]:m[1];ship.h=ship.rotation%180==0?m[1]:m[0];if(NavalMilitary.legal(fleet,ship))break;ship.x=x;ship.y=y;ship.w=w;ship.h=h;ship.rotation=r;}
        }
        state.players.get(1).ships=fleet;state.players.get(1).coins=300-fleet.stream().mapToInt(s->NavalMilitary.MODELS.get(s.kind)[2]).sum();state.aiTemplate=new ArrayList<>();
        boolean mirror=random.nextDouble()<.35;for(Action source:template.actions){Action a=new Action();a.shipId=source.shipId;a.weapon=source.weapon;a.plane=source.plane;a.x=Math.max(0,Math.min(11,(mirror?11-source.x:source.x)+(random.nextDouble()<.3?random.nextInt(3)-1:0)));a.y=Math.max(0,Math.min(11,source.y+(random.nextDouble()<.3?random.nextInt(3)-1:0)));state.aiTemplate.add(a);}
    }
}
