package com.trama.mani.rules.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.trama.mani.rules.controller.HealthController;
import com.trama.mani.rules.controller.RulesController;
import com.trama.mani.rules.service.AllyRanker;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest({HealthController.class, RulesController.class})
@Import(AllyRanker.class)
class WebSkeletonTest {

    private static final String RANKING = "{\"criterio\":\"CALIFICACION\",\"candidatos\":"
            + "[{\"id\":\"a\",\"totalCalificaciones\":0}]}";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void echoesCorrelationIdInResponseHeader() throws Exception {
        mockMvc.perform(get("/health").header("X-Correlation-ID", "corr-abc"))
                .andExpect(header().string("X-Correlation-ID", "corr-abc"));
    }

    @Test
    void doesNotAddCorsHeadersBecauseTheGatewayOwnsCors() throws Exception {
        mockMvc.perform(get("/health").header("Origin", "http://evil.test"))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    @Test
    void answersTheSameRouteWithAndWithoutTheGatewayPrefix() throws Exception {
        mockMvc.perform(post("/rank-allies").header("X-Tenant-Id", "t1").header("X-User-Role", "SERVICE")
                        .contentType(MediaType.APPLICATION_JSON).content(RANKING))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/rules/rank-allies").header("X-Tenant-Id", "t1").header("X-User-Role", "SERVICE")
                        .contentType(MediaType.APPLICATION_JSON).content(RANKING))
                .andExpect(status().isOk());
    }

    @Test
    void malformedJsonReturnsUniformValidationError() throws Exception {
        mockMvc.perform(post("/rank-allies").header("X-Correlation-ID", "corr-json")
                        .contentType(MediaType.APPLICATION_JSON).content("{no-json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.correlationId").value("corr-json"));
    }

    @Test
    void unknownRouteReturnsUniformNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/rules/no-existe"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.correlationId").value("none"));
    }

    @Test
    void wrongMethodReturnsUniformMethodNotAllowed() throws Exception {
        mockMvc.perform(get("/rank-allies"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
    }
}
