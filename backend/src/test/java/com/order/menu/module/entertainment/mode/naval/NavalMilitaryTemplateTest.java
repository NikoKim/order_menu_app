package com.order.menu.module.entertainment.mode.naval;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.order.menu.module.entertainment.mode.naval.NavalState.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class NavalMilitaryTemplateTest {
    final ObjectMapper json=new ObjectMapper();
    Map<String,Object> record(String mode,int winner){Map<String,Object> ship=new HashMap<>(Map.of("id","private-id","kind","cruiser","x",3,"y",3,"w",1,"h",3,"rotation",0));ship.put("hits",List.of("3,3"));return new HashMap<>(Map.of("ruleSet","MILITARY","mode",mode,"winner",winner,"battleStarted",true,"players",List.of(Map.of("key","U:secret","ships",List.of(ship)),Map.of("key",mode.equals("AI")?"AI":"U:other","ships",List.of(ship))),"events",List.of(Map.of("player",winner,"weapon","shot","shipId","private-id","x",0,"y",0))));}
    @Test void filtersWinnersAndSanitizesPrivateMetadata(){assertNull(NavalMilitaryTemplate.extract(record("AI",1),json));for(int winner:List.of(0,1)){var t=NavalMilitaryTemplate.extract(record("MULTIPLAYER",winner),json);assertNotNull(t);assertEquals("template-0",t.ships().get(0).id);assertEquals("template-0",t.actions().get(0).shipId);assertTrue(t.ships().get(0).hits.isEmpty());}var classic=record("AI",0);classic.put("ruleSet","CLASSIC");assertNull(NavalMilitaryTemplate.extract(classic,json));assertNotNull(NavalMilitaryTemplate.extract(record("AI",0),json));}
    @Test void variationsStayLegalAndRetainAttackFlow(){for(int i=0;i<20;i++){NavalState s=new NavalState();s.players.add(new Player());s.players.add(new Player());NavalMilitaryTemplate.install(s,NavalMilitaryTemplate.extract(record("AI",0),json));assertEquals(30,NavalMilitary.validate(s.players.get(1).ships,true));assertEquals(270,s.players.get(1).coins);assertEquals(1,s.aiTemplate.size());assertEquals("shot",s.aiTemplate.get(0).weapon);}}
}
