package com.trama.mani.rules.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.trama.mani.rules.config.RulesConfiguration;
import com.trama.mani.rules.controller.CategoryRulesController;
import com.trama.mani.rules.service.CategoryRuleEvaluator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** Una falla interna de Rules es un 500 uniforme que no filtra el detalle (Core lo traduce a 503). */
@WebMvcTest(CategoryRulesController.class)
@Import(RulesConfiguration.class)
class UnexpectedErrorTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CategoryRuleEvaluator evaluator;

    @Test
    void unaExcepcionNoControladaRespondeInternalErrorSinDetalle() throws Exception {
        given(evaluator.evaluate(any(), any(), any(), any(), any())).willThrow(new IllegalStateException("secreto interno"));

        mockMvc.perform(post("/api/v1/rules/categories/evaluate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Tenant-Id", "tenant-a")
                        .header("X-User-Role", "ADMIN")
                        .header("X-Correlation-ID", "corr-500")
                        .content("{\"context\":{\"operation\":\"OPERATE\",\"active\":true}}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.error").value("Error interno del motor de reglas"))
                .andExpect(jsonPath("$.correlationId").value("corr-500"));
    }
}
