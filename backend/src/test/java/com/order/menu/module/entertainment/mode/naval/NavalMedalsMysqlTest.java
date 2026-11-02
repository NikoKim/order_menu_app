package com.order.menu.module.entertainment.mode.naval;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.yaml.snakeyaml.Yaml;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
@EnabledIfSystemProperty(named="naval.mysql",matches="true")
class NavalMedalsMysqlTest {
 @Test void persistentAwardsBackfillIdempotencyEquipAndIndependentModes() throws Exception {
  Map<?,?> config=new Yaml().load(Files.readString(Path.of("src/main/resources/application-dev.yml"))),db=(Map<?,?>)((Map<?,?>)config.get("spring")).get("datasource");String url=db.get("url").toString();assertTrue(url.startsWith("jdbc:mysql://127.0.0.1:")||url.startsWith("jdbc:mysql://localhost:"));
  var ds=new UnpooledDataSource("com.mysql.cj.jdbc.Driver",url,db.get("username").toString(),db.get("password").toString());String prefix="qa_medals_"+UUID.randomUUID().toString().replace("-","").substring(0,10)+"_";
  var jdbc=new NavalMysqlTest.PrefixedJdbc(ds,prefix);var json=new ObjectMapper();var medals=new NavalMedals(jdbc,json);
  String ddl=Files.readString(Path.of("src/main/resources/db/migration/V59__naval_medals.sql"));
  try(var c=ds.getConnection()){
   if(Boolean.getBoolean("naval.install.v59")){assertEquals("order_menu",c.getCatalog());ScriptUtils.executeSqlScript(c,new ByteArrayResource(ddl.getBytes(StandardCharsets.UTF_8)));}
   String testDdl=Files.readString(Path.of("src/main/resources/db/migration/V55__naval_round_results.sql"))+"\n"+ddl;for(String table:List.of("naval_round_results","naval_medal_profiles","naval_medal_rounds"))testDdl=testDdl.replace(table,prefix+table);ScriptUtils.executeSqlScript(c,new ByteArrayResource(testDdl.getBytes(StandardCharsets.UTF_8)));
   try{
    for(int i=1;i<=10;i++){Map<String,Object> r=result("c"+i,"CLASSIC",0,"MULTIPLAYER");medals.credit(r);medals.credit(r);}
    assertEquals(10,medals.wall("G:a").get("classicCurrent"));assertEquals(3,((List<?>)medals.wall("G:a").get("unlocked")).size());
    medals.credit(result("ai","CLASSIC",1,"AI"));assertEquals(10,medals.wall("G:a").get("classicCurrent"));
    for(int i=1;i<=5;i++)medals.credit(result("m"+i,"MILITARY",0,"MULTIPLAYER"));medals.credit(result("loss","CLASSIC",1,"MULTIPLAYER"));
    var wall=medals.wall("G:a");assertEquals(0,wall.get("classicCurrent"));assertEquals(5,wall.get("militaryCurrent"));assertEquals(5,((List<?>)wall.get("unlocked")).size());
    assertEquals("classic-10",medals.equip("G:a","classic-10").get("equipped"));assertThrows(RuntimeException.class,()->medals.equip("G:b","classic-10"));assertEquals("classic-10",medals.publicWall(wall.get("medalId").toString()).get("equipped"));assertFalse(wall.containsKey("player_key"));
    for(int i=0;i<3;i++){var r=result("old"+i,"MILITARY",0,"MULTIPLAYER");r.put("players",List.of(Map.of("key","G:old"),Map.of("key","G:old-other")));jdbc.update("INSERT INTO naval_round_results(game_id,room_id,round_no,player0_key,player1_key,finished_at,result_json) VALUES(?,?,?,?,?,?,?)","old"+i,"old"+i,1,"G:old","G:old-other",100+i,json.writeValueAsString(r));}
    assertEquals(List.of("military-3"),medals.wall("G:old").get("unlocked"));assertEquals(3,medals.wall("G:old").get("militaryCurrent"));
   }finally{for(String table:List.of("naval_medal_rounds","naval_medal_profiles","naval_round_results"))jdbc.execute("DROP TABLE IF EXISTS "+prefix+table);}
  }
 }
 private Map<String,Object> result(String id,String rule,int winner,String mode){return new LinkedHashMap<>(Map.of("gameId",id,"ruleSet",rule,"mode",mode,"winner",winner,"battleStarted",true,"players",List.of(Map.of("key","G:a"),Map.of("key","G:b"))));}
}
