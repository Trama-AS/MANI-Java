package com.trama.mani.rules.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Prueba de humo (QA-06 / SCRUM-1123) de la capa web. Cuando lleguen las reglas reales de
 * tarifa y el ranking, sus pruebas se suman en este paquete.
 */
@WebMvcTest(RulesController.class)
class RulesControllerSmokeTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void healthRespondsUpAndEchoesCorrelationId() throws Exception {
        mockMvc.perform(get("/health").header("X-Correlation-ID", "corr-123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.service").value("MANI-Rules-Java"))
                .andExpect(jsonPath("$.correlationId").value("corr-123"));
    }

    @Test
    void gatewayHealthPathRespondsUp() throws Exception {
        mockMvc.perform(get("/api/v1/rules/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.correlationId").value("none"));
    }
}
