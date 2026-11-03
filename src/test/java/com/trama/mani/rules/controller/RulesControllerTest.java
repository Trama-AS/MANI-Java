package com.trama.mani.rules.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.trama.mani.rules.service.AllyRanker;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** Contrato HTTP de POST /api/v1/rules/rank-allies (SCRUM-1462). */
@WebMvcTest(RulesController.class)
@Import(AllyRanker.class)
class RulesControllerTest {

    private static final String URL = "/api/v1/rules/rank-allies";
    private static final String TENANT = "10000000-0000-4000-8000-000000000011";
    private static final String CANDIDATOS = "\"candidatos\":["
            + "{\"id\":\"a\",\"calificacionPromedio\":4.5,\"totalCalificaciones\":12,\"zonasCobertura\":2},"
            + "{\"id\":\"b\",\"calificacionPromedio\":null,\"totalCalificaciones\":0,\"zonasCobertura\":5}]";
    private static final String VALIDO = "{\"criterio\":\"CALIFICACION\"," + CANDIDATOS + "}";

    @Autowired
    private MockMvc mockMvc;

    private ResultActions rank(String url, String tenant, String role, String body) throws Exception {
        var request = post(url).contentType(MediaType.APPLICATION_JSON).content(body)
                .header("X-Correlation-ID", "corr-ranking");
        if (tenant != null) {
            request.header("X-Tenant-Id", tenant);
        }
        if (role != null) {
            request.header("X-User-Role", role);
        }
        return mockMvc.perform(request);
    }

    private ResultActions rank(String body) throws Exception {
        return rank(URL, TENANT, "SERVICE", body);
    }

    private void expectValidationError(String body, String mensaje) throws Exception {
        rank(body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error", containsString(mensaje)))
                .andExpect(jsonPath("$.correlationId").value("corr-ranking"));
    }

    @Test
    void devuelveElContratoCompleto() throws Exception {
        rank(VALIDO)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.criterio").value("CALIFICACION"))
                .andExpect(jsonPath("$.tenantId").value(TENANT))
                .andExpect(jsonPath("$.correlationId").value("corr-ranking"))
                .andExpect(jsonPath("$.aliados", hasSize(2)))
                .andExpect(jsonPath("$.aliados[0].id").value("a"))
                .andExpect(jsonPath("$.aliados[0].posicion").value(1))
                .andExpect(jsonPath("$.aliados[0].puntaje").value(0.9))
                .andExpect(jsonPath("$.aliados[0].razones[0]", containsString("4.5")))
                .andExpect(jsonPath("$.aliados[1].id").value("b"))
                .andExpect(jsonPath("$.aliados[1].posicion").value(2))
                .andExpect(jsonPath("$.aliados[1].puntaje").value(nullValue()));
    }

    @Test
    void ordenaPorCoberturaSinPermitirQueElNoValoradoPaseAlValorado() throws Exception {
        rank("{\"criterio\":\"COBERTURA\"," + CANDIDATOS + "}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.criterio").value("COBERTURA"))
                .andExpect(jsonPath("$.aliados[0].id").value("a"))
                .andExpect(jsonPath("$.aliados[0].puntaje").value(0.4))
                .andExpect(jsonPath("$.aliados[1].id").value("b"))
                .andExpect(jsonPath("$.aliados[1].puntaje").value(1.0));
    }

    @Test
    void respondeSinElPrefijoDelGateway() throws Exception {
        rank("/rank-allies", TENANT, "SERVICE", VALIDO).andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"criterio\":\"OTRO\",%s}", "{\"criterio\":\"\",%s}", "{\"criterio\":null,%s}", "{%s}"})
    void criterioInvalidoOFaltanteDa400SinOrdenPorOmision(String plantilla) throws Exception {
        expectValidationError(plantilla.formatted(CANDIDATOS), "criterio debe ser CALIFICACION o COBERTURA");
    }

    @Test
    void sinTenantDa400() throws Exception {
        rank(URL, null, "SERVICE", VALIDO)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TENANT_REQUIRED"))
                .andExpect(jsonPath("$.correlationId").value("corr-ranking"));
        rank(URL, "  ", "SERVICE", VALIDO)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TENANT_REQUIRED"));
    }

    @Test
    void sinRolDa401() throws Exception {
        rank(URL, TENANT, null, VALIDO)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        rank(URL, TENANT, "  ", VALIDO)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void rechazaListaVaciaOFaltante() throws Exception {
        expectValidationError("{\"criterio\":\"CALIFICACION\",\"candidatos\":[]}", "candidatos");
        expectValidationError("{\"criterio\":\"CALIFICACION\"}", "candidatos");
    }

    @Test
    void rechazaMetricasFueraDeRango() throws Exception {
        expectValidationError("{\"criterio\":\"CALIFICACION\",\"candidatos\":[{\"id\":\"a\",\"calificacionPromedio\":5.1,"
                + "\"totalCalificaciones\":1}]}", "calificacionPromedio");
        expectValidationError("{\"criterio\":\"CALIFICACION\",\"candidatos\":[{\"id\":\"a\",\"calificacionPromedio\":0,"
                + "\"totalCalificaciones\":0}]}", "calificacionPromedio");
        expectValidationError("{\"criterio\":\"CALIFICACION\",\"candidatos\":[{\"id\":\"a\"}]}", "totalCalificaciones");
        expectValidationError("{\"criterio\":\"COBERTURA\",\"candidatos\":[{\"id\":\" \",\"totalCalificaciones\":0,"
                + "\"zonasCobertura\":1}]}", "id");
        expectValidationError("{\"criterio\":\"COBERTURA\",\"candidatos\":[{\"id\":\"a\",\"totalCalificaciones\":0,"
                + "\"zonasCobertura\":-1}]}", "zonasCobertura");
    }

    @Test
    void rechazaDatosIncoherentes() throws Exception {
        expectValidationError("{\"criterio\":\"COBERTURA\",\"candidatos\":[{\"id\":\"a\",\"totalCalificaciones\":0}]}",
                "zonasCobertura");
        expectValidationError("{\"criterio\":\"CALIFICACION\",\"candidatos\":[{\"id\":\"a\",\"totalCalificaciones\":3}]}",
                "calificacionPromedio");
        expectValidationError("{\"criterio\":\"CALIFICACION\",\"candidatos\":["
                + "{\"id\":\"a\",\"totalCalificaciones\":0},{\"id\":\"a\",\"totalCalificaciones\":0}]}", "repetido");
    }
}
