package com.order.menu.common.exception;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class GlobalExceptionHandlerTest {
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.standaloneSetup(new MissingRouteController())
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    void missingApiResourceIsNotReportedAsAServerFailure() throws Exception {
        mvc.perform(get("/missing-resource"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ErrorCode.NOT_FOUND))
                .andExpect(jsonPath("$.message").value("请求的接口或资源不存在"))
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void missingHandlerHasTheSameSafeNotFoundResponse() throws Exception {
        mvc.perform(get("/missing-handler"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ErrorCode.NOT_FOUND))
                .andExpect(jsonPath("$.message").value("请求的接口或资源不存在"));
    }

    @RestController
    static class MissingRouteController {
        @GetMapping("/missing-resource")
        void resource() throws NoResourceFoundException {
            throw new NoResourceFoundException(HttpMethod.POST, "api/entertainment/ai-connection/test");
        }

        @GetMapping("/missing-handler")
        void handler() throws NoHandlerFoundException {
            throw new NoHandlerFoundException("POST", "/private-path", HttpHeaders.EMPTY);
        }
    }
}
