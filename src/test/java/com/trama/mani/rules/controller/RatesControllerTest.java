package com.trama.mani.rules.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.trama.mani.rules.service.RateEvaluator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** Contrato HTTP de POST /api/v1/rules/rates/evaluate (docs/openapi/rules.yaml del Gateway, SCRUM-887). */
@WebMvcTest(RatesController.class)
@Import(RateEvaluator.class)
class RatesControllerTest {

    private static final String URL = "/api/v1/rules/rates/evaluate";
    private static final String TARIFA = "\"valorMin\":100000,\"valorTipico\":150000,\"valorMax\":200000";

    @Autowired
    private MockMvc mockMvc;

    private ResultActions evaluate(String url, String tenant, String role, String body) throws Exception {
        var request = post(url).contentType(MediaType.APPLICATION_JSON).content(body)
                .header("X-Correlation-ID", "corr-tarifas");
        if (tenant != null) {
            request.header("X-Tenant-Id", tenant);
        }
        if (role != null) {
            request.header("X-User-Role", role);
        }
        return mockMvc.perform(request);
    }

    private static String items(String... items) {
        return "{\"items\":[" + String.join(",", items) + "]}";
    }

    private static String item(String id, String total) {
        return "{\"id\":\"" + id + "\",\"total\":" + total + "," + TARIFA + "}";
    }

    @Test
    void evaluaCadaImporteEnOrdenYDevuelveElContratoCompleto() throws Exception {
        evaluate(URL, "tenant-a", "ADMIN", items(item("c1", "90000"), item("c2", "150000"), item("c3", "230000"),
                item("c4", "100000"), item("c5", "200000")))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Correlation-ID", "corr-tarifas"))
                .andExpect(jsonPath("$.version").value("rates-v1"))
                .andExpect(jsonPath("$.toleranciaPorcentaje").value(0))
                .andExpect(jsonPath("$.tenantId").value("tenant-a"))
                .andExpect(jsonPath("$.correlationId").value("corr-tarifas"))
                .andExpect(jsonPath("$.resultados", hasSize(5)))
                .andExpect(jsonPath("$.resultados[0].id").value("c1"))
                .andExpect(jsonPath("$.resultados[0].resultado").value("POR_DEBAJO"))
                .andExpect(jsonPath("$.resultados[0].desviacionPorcentaje").value(10.0))
                .andExpect(jsonPath("$.resultados[1].resultado").value("DENTRO_DE_RANGO"))
                .andExpect(jsonPath("$.resultados[1].desviacionPorcentaje").value(0.0))
                .andExpect(jsonPath("$.resultados[2].resultado").value("POR_ENCIMA"))
                .andExpect(jsonPath("$.resultados[2].desviacionPorcentaje").value(15.0))
                // Los límites son inclusivos.
                .andExpect(jsonPath("$.resultados[3].resultado").value("DENTRO_DE_RANGO"))
                .andExpect(jsonPath("$.resultados[4].resultado").value("DENTRO_DE_RANGO"));
    }

    @Test
    void respondeTambienSinElPrefijoQueRecortaElGateway() throws Exception {
        evaluate("/rates/evaluate", "tenant-a", "ADMIN", items(item("c1", "150000")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultados[0].resultado").value("DENTRO_DE_RANGO"));
    }

    @Test
    void sinRolEs401YSinTenantEs400() throws Exception {
        evaluate(URL, "tenant-a", null, items(item("c1", "1")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        evaluate(URL, null, "ADMIN", items(item("c1", "1")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TENANT_REQUIRED"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "{\"items\":[]}",
        "{}",
        "{\"items\":[{\"id\":\"c1\",\"total\":-1,\"valorMin\":1,\"valorTipico\":2,\"valorMax\":3}]}",
        "{\"items\":[{\"id\":\"\",\"total\":1,\"valorMin\":1,\"valorTipico\":2,\"valorMax\":3}]}",
        "{\"items\":[{\"id\":\"c1\",\"valorMin\":1,\"valorTipico\":2,\"valorMax\":3}]}",
        "{\"items\":[null]}"
    })
    void cuerpoInvalidoEs400ConElFormatoUnicoDeError(String body) throws Exception {
        evaluate(URL, "tenant-a", "ADMIN", body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error").isNotEmpty())
                .andExpect(jsonPath("$.correlationId").value("corr-tarifas"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "\"valorMin\":300,\"valorTipico\":200,\"valorMax\":400",
        "\"valorMin\":100,\"valorTipico\":500,\"valorMax\":400",
        "\"valorMin\":100,\"valorTipico\":100,\"valorMax\":100"
    })
    void unaTarifaInvertidaOSinRangoSeRechazaSinEvaluar(String tarifa) throws Exception {
        evaluate(URL, "tenant-a", "ADMIN", "{\"items\":[{\"id\":\"c9\",\"total\":150," + tarifa + "}]}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("c9")));
    }

    @Test
    void rechazaMasDeQuinientosImportes() throws Exception {
        String[] muchos = new String[501];
        for (int i = 0; i < muchos.length; i++) {
            muchos[i] = item("c" + i, "150000");
        }
        evaluate(URL, "tenant-a", "ADMIN", items(muchos))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }
}
