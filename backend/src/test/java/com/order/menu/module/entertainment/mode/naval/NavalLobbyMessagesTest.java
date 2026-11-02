package com.order.menu.module.entertainment.mode.naval;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.order.menu.module.entertainment.core.service.PlayerIdentity;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class NavalLobbyMessagesTest {
    @Test void marksOwnMessagesWithoutPublishingPrivateSenderKeys() throws Exception {
        JdbcTemplate jdbc=mock(JdbcTemplate.class);
        when(jdbc.queryForList(anyString())).thenAnswer(call -> {
            List<Map<String,Object>> rows=new ArrayList<>();
            for (String sender : List.of("G:guest-private","U:12")) {
                Map<String,Object> row=new LinkedHashMap<>();
                row.put("id",rows.size()+1); row.put("senderKey",sender);
                row.put("name","同名指挥官"); row.put("text","你好"); rows.add(row);
            }
            return rows;
        });
        NavalCommunityService community=new NavalCommunityService(jdbc,new ObjectMapper());
        PlayerIdentity guest=new PlayerIdentity("GUEST",null,1L,"G:guest-private","同名指挥官");
        PlayerIdentity user=new PlayerIdentity("USER",12L,null,"U:12","同名指挥官");
        List<Map<String,Object>> guestView=community.messages(guest),userView=community.messages(user);
        assertEquals(List.of(true,false),userView.stream().map(row -> row.get("mine")).toList());
        assertEquals(List.of(false,true),guestView.stream().map(row -> row.get("mine")).toList());
        assertTrue(community.messages().stream().allMatch(row -> Boolean.FALSE.equals(row.get("mine"))));
        String encoded=new ObjectMapper().writeValueAsString(guestView);
        assertFalse(encoded.contains("guest-private")); assertFalse(encoded.contains("senderKey")); assertFalse(encoded.contains("U:12"));
    }
}
