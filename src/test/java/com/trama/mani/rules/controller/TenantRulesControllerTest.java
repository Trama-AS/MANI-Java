package com.trama.mani.rules.controller;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.trama.mani.rules.service.TenantRulesValidator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** Contrato HTTP de POST /api/v1/rules/tenant-rules/validate (SCRUM-1366). */
@WebMvcTest(TenantRulesController.class)
@Import(TenantRulesValidator.class)
class TenantRulesControllerTest {

    private static final String URL = "/api/v1/rules/tenant-rules/validate";

    @Autowired
    private MockMvc mockMvc;

    private static String reglas(String criterio, String tolerancia, String comision, String ventana,
            String penalizacion, String minutos) {
        return "{\"rules\":{\"ordenListado\":{\"criterio\":" + criterio + "},"
                + "\"alertaTarifa\":{\"toleranciaPorcentaje\":" + tolerancia + "},"
                + "\"comision\":{\"porcentaje\":" + comision + "},"
                + "\"cancelacion\":{\"ventanaHoras\":" + ventana + ",\"penalizacionPorcentaje\":" + penalizacion + "},"
                + "\"oferta\":{\"minutosParaAceptar\":" + minutos + "}}}";
    }

    private static final String VALIDAS = reglas("\"COBERTURA\"", "15", "10", "4", "25", "30");

    private ResultActions validar(String tenant, String role, String body) throws Exception {
        var request = post(URL).contentType(MediaType.APPLICATION_JSON).content(body)
                .header("X-Correlation-ID", "corr-1366");
        if (tenant != null) {
            request.header("X-Tenant-Id", tenant);
        }
        if (role != null) {
            request.header("X-User-Role", role);
        }
        return mockMvc.perform(request);
    }

    @Test
    void reglasCoherentesSonValidas() throws Exception {
        validar("tenant-a", "ADMIN", VALIDAS)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.version").value("tenant-rules-v1"))
                .andExpect(jsonPath("$.violations", hasSize(0)))
                .andExpect(jsonPath("$.tenantId").value("tenant-a"))
                .andExpect(jsonPath("$.correlationId").value("corr-1366"));
    }

    @Test
    void losLimitesSonInclusivos() throws Exception {
        validar("tenant-a", "ADMIN", reglas("\"calificacion\"", "50", "30", "72", "100", "1440"))
                .andExpect(jsonPath("$.valid").value(true));
        validar("tenant-a", "ADMIN", reglas("\"CALIFICACION\"", "0", "0", "0", "0", "1"))
                .andExpect(jsonPath("$.valid").value(true));
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "\"PRECIO\"|15|10|4|25|30|ordenListado.criterio debe ser CALIFICACION o COBERTURA",
        "\"COBERTURA\"|51|10|4|25|30|alertaTarifa.toleranciaPorcentaje debe ser un número entre 0 y 50",
        "\"COBERTURA\"|15|31|4|25|30|comision.porcentaje debe ser un número entre 0 y 30",
        "\"COBERTURA\"|15|10|73|25|30|cancelacion.ventanaHoras debe ser un número entre 0 y 72",
        "\"COBERTURA\"|15|10|4|101|30|cancelacion.penalizacionPorcentaje debe ser un número entre 0 y 100",
        "\"COBERTURA\"|15|10|0|25|30|cancelacion: una penalización mayor que 0 exige una ventana mayor que 0 horas",
        "\"COBERTURA\"|15|10|4|25|0|oferta.minutosParaAceptar debe ser un entero entre 1 y 1440",
        "\"COBERTURA\"|15|10|4|25|1.5|oferta.minutosParaAceptar debe ser un entero entre 1 y 1440",
        "\"COBERTURA\"|\"15\"|10|4|25|30|alertaTarifa.toleranciaPorcentaje debe ser un número entre 0 y 50"
    })
    void reglasIncoherentesSonUn200ConSusViolaciones(String criterio, String tolerancia, String comision,
            String ventana, String penalizacion, String minutos, String violacion) throws Exception {
        validar("tenant-a", "ADMIN", reglas(criterio, tolerancia, comision, ventana, penalizacion, minutos))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(false))
                .andExpect(jsonPath("$.violations", hasItem(violacion)));
    }

    @Test
    void sinReglasEs400YSinEncabezadosEs401O400() throws Exception {
        validar("tenant-a", "ADMIN", "{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        validar("tenant-a", null, VALIDAS).andExpect(status().isUnauthorized());
        validar(null, "ADMIN", VALIDAS)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TENANT_REQUIRED"));
    }

    @Test
    void reglasQueNoSonObjetoSonInvalidas() throws Exception {
        validar("tenant-a", "ADMIN", "{\"rules\":[]}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(false))
                .andExpect(jsonPath("$.violations", hasItem("rules debe ser un objeto")));
    }

    @Test
    void respondeSinElPrefijoQueRecortaElGateway() throws Exception {
        mockMvc.perform(post("/tenant-rules/validate").contentType(MediaType.APPLICATION_JSON).content(VALIDAS)
                        .header("X-Tenant-Id", "tenant-a").header("X-User-Role", "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true));
    }
}
