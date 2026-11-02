package com.order.menu.module.entertainment.mode.naval;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.order.menu.common.exception.BusinessException;
import com.order.menu.common.exception.GlobalExceptionHandler;
import com.order.menu.module.entertainment.core.service.EntertainmentGuestService;
import com.order.menu.module.entertainment.core.service.PlayerIdentity;
import java.sql.SQLException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class NavalControllerTest {
    private final NavalService service = mock(NavalService.class);
    private MockMvc mvc;

    @BeforeEach void setup() {
        EntertainmentGuestService guests = mock(EntertainmentGuestService.class);
        PlayerIdentity player = new PlayerIdentity("GUEST",null,1L,"G:controller-test","测试指挥官");
        when(guests.resolve(any(),eq("test-token"),eq(true))).thenReturn(player);
        mvc = MockMvcBuilders.standaloneSetup(new NavalController(service,guests,mock(NavalPresenceService.class),mock(NavalCommunityService.class)))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test void missingTableHasAnActionable503InsteadOfGeneric500() throws Exception {
        when(service.create(any(),any())).thenThrow(new BadSqlGrammarException("create","SELECT secret_internal_column",new SQLException("Table private_database.naval_matchmaking_lock does not exist","42S02",1146)));
        request().andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value(503))
                .andExpect(jsonPath("$.message").value(containsString("V53")))
                .andExpect(jsonPath("$.message").value(containsString("V55")))
                .andExpect(content().string(not(containsString("private_database"))))
                .andExpect(content().string(not(containsString("secret_internal_column"))));
    }

    @Test void missingColumnAlsoIdentifiesRequiredMigrations() throws Exception {
        when(service.create(any(),any())).thenThrow(new BadSqlGrammarException("read","SELECT bad_column",new SQLException("Unknown column","42S22",1054)));
        request().andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.message").value(containsString("数据库迁移")));
    }

    @Test void permissionsHaveSafeConfigurationGuidance() throws Exception {
        when(service.create(any(),any())).thenThrow(new DataAccessResourceFailureException("private-host",new SQLException("Access denied password=secret","42000",1142)));
        request().andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.message").value(containsString("连接和权限")))
                .andExpect(content().string(not(containsString("secret"))))
                .andExpect(content().string(not(containsString("private-host"))));
    }

    @Test void transientDatabaseFailuresDoNotClaimTablesAreMissing() throws Exception {
        when(service.create(any(),any())).thenThrow(new DataAccessResourceFailureException("connection refused"));
        request().andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.message").value("海战服务暂时不可用，请稍后重试"));
    }

    @Test void ordinaryBusinessConflictsKeepTheirOriginalStatus() throws Exception {
        when(service.create(any(),any())).thenThrow(new BusinessException(409,"房间已满"));
        request().andExpect(status().isConflict()).andExpect(jsonPath("$.code").value(409)).andExpect(jsonPath("$.message").value("房间已满"));
    }

    private org.springframework.test.web.servlet.ResultActions request() throws Exception {
        return mvc.perform(post("/api/entertainment/naval/rooms").header("X-Guest-Token","test-token")
                .contentType("application/json").content("{\"mode\":\"MULTIPLAYER\",\"autoMatch\":true}"));
    }
}
