package com.trama.mani.rules.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.trama.mani.rules.config.RulesConfiguration;
import com.trama.mani.rules.service.CategoryRuleEvaluator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** Contrato HTTP de POST /api/v1/rules/categories/evaluate (docs/openapi/rules.yaml del Gateway). */
@WebMvcTest(CategoryRulesController.class)
@Import({RulesConfiguration.class, CategoryRuleEvaluator.class})
@TestPropertySource(properties = {
        "mani.rules.category.tenants[tenant-override].name-min-length=2",
        "mani.rules.category.tenants[tenant-override].version=category-rules-v2-override"
})
class CategoryRulesControllerTest {

    private static final String URL = "/api/v1/rules/categories/evaluate";
    private static final String VALID_CREATE =
            "{\"context\":{\"operation\":\"CREATE\",\"name\":\"  Plomería  Express \",\"flujoOperativo\":\"tarifa_estandar\"}}";

    @Autowired
    private MockMvc mockMvc;

    private ResultActions evaluate(String url, String tenant, String role, String body) throws Exception {
        var request = post(url).contentType(MediaType.APPLICATION_JSON).content(body)
                .header("X-Correlation-ID", "corr-test");
        if (tenant != null) {
            request.header("X-Tenant-Id", tenant);
        }
        if (role != null) {
            request.header("X-User-Role", role);
        }
        return mockMvc.perform(request);
    }

    @Test
    void createValidaRespondeElContratoCompleto() throws Exception {
        evaluate(URL, "tenant-a", "ADMIN", VALID_CREATE)
                .andExpect(status().isOk())
                .andExpect(header().string("X-Correlation-ID", "corr-test"))
                .andExpect(jsonPath("$.allowed").value(true))
                .andExpect(jsonPath("$.ruleSetVersion").value("category-rules-v1"))
                .andExpect(jsonPath("$.violations", hasSize(0)))
                .andExpect(jsonPath("$.normalized.name").value("Plomería Express"))
                .andExpect(jsonPath("$.normalized.flujoOperativo").value("TARIFA_ESTANDAR"))
                .andExpect(jsonPath("$.tenantId").value("tenant-a"))
                .andExpect(jsonPath("$.categoryId").doesNotExist())
                .andExpect(jsonPath("$.operation").value("CREATE"))
                .andExpect(jsonPath("$.correlationId").value("corr-test"));
    }

    @Test
    void respondeTambienSinElPrefijoQueRecortaElGateway() throws Exception {
        evaluate("/categories/evaluate", "tenant-a", "ADMIN", VALID_CREATE)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowed").value(true));
    }

    @Test
    void unaReglaIncumplidaEsUn200ConAllowedFalseYSusViolaciones() throws Exception {
        evaluate(URL, "tenant-a", "ADMIN", "{\"context\":{\"operation\":\"CREATE\",\"name\":\"12\",\"flujoOperativo\":\"OTRO\"}}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowed").value(false))
                .andExpect(jsonPath("$.violations", hasSize(3)))
                .andExpect(jsonPath("$.violations[0].code").value("MANI-CAT-422N"))
                .andExpect(jsonPath("$.violations[0].field").value("name"))
                .andExpect(jsonPath("$.violations[2].code").value("MANI-CAT-422F"))
                .andExpect(jsonPath("$.violations[2].field").value("flujoOperativo"));
    }

    @Test
    void operateConCategoriaInactivaDevuelve422C() throws Exception {
        evaluate(URL, "tenant-a", "aliado", "{\"categoryId\":\"c-1\",\"context\":{\"operation\":\"OPERATE\",\"active\":false}}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowed").value(false))
                .andExpect(jsonPath("$.categoryId").value("c-1"))
                .andExpect(jsonPath("$.violations[0].code").value("MANI-CAT-422C"));
    }

    @Test
    void aplicaElOverrideDelTenantYReportaSuVersion() throws Exception {
        String twoChars = "{\"context\":{\"operation\":\"CREATE\",\"name\":\"AB\",\"flujoOperativo\":\"TARIFA_ESTANDAR\"}}";

        evaluate(URL, "tenant-override", "ADMIN", twoChars)
                .andExpect(jsonPath("$.allowed").value(true))
                .andExpect(jsonPath("$.ruleSetVersion").value("category-rules-v2-override"));
        evaluate(URL, "tenant-normal", "ADMIN", twoChars)
                .andExpect(jsonPath("$.allowed").value(false))
                .andExpect(jsonPath("$.ruleSetVersion").value("category-rules-v1"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "admin", "ADMIN_TENANT", "admin_tenant"})
    void aceptaLosRolesDeAdministradorQueEmitenCoreYElGateway(String role) throws Exception {
        evaluate(URL, "tenant-a", role, VALID_CREATE).andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = {"ALLY", "aliado", "CLIENT", "authenticated"})
    void createYUpdateExigenAdministrador(String role) throws Exception {
        evaluate(URL, "tenant-a", role, VALID_CREATE)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.correlationId").value("corr-test"));
        evaluate(URL, "tenant-a", role, "{\"context\":{\"operation\":\"UPDATE\",\"name\":\"Nuevo\"}}")
                .andExpect(status().isForbidden());
    }

    @Test
    void operateLoPuedeEvaluarCualquierRolAutenticado() throws Exception {
        evaluate(URL, "tenant-a", "aliado", "{\"context\":{\"operation\":\"OPERATE\",\"active\":true}}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowed").value(true));
    }

    @Test
    void sinTenantRespondeTenantRequired() throws Exception {
        evaluate(URL, null, "ADMIN", VALID_CREATE)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TENANT_REQUIRED"));
        evaluate(URL, "   ", "ADMIN", VALID_CREATE)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TENANT_REQUIRED"));
    }

    @Test
    void sinRolRespondeUnauthorized() throws Exception {
        evaluate(URL, "tenant-a", null, VALID_CREATE)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void unTenantAjenoEnElCuerpoSeRechaza() throws Exception {
        evaluate(URL, "tenant-a", "ADMIN",
                "{\"tenantId\":\"tenant-b\",\"context\":{\"operation\":\"CREATE\",\"name\":\"Plomería\",\"flujoOperativo\":\"TARIFA_ESTANDAR\"}}")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("TENANT_MISMATCH"));
    }

    @Test
    void elTenantDelCuerpoIgualAlDeLaSesionSeAcepta() throws Exception {
        evaluate(URL, "tenant-a", "ADMIN",
                "{\"tenantId\":\"tenant-a\",\"context\":{\"operation\":\"CREATE\",\"name\":\"Plomería\",\"flujoOperativo\":\"TARIFA_ESTANDAR\"}}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenantId").value("tenant-a"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{}",
            "{\"context\":{}}",
            "{\"context\":{\"operation\":\"DELETE\"}}",
            "{\"context\":{\"operation\":\"OPERATE\"}}",
            "{no-json"
    })
    void peticionesMalFormadasRespondenValidationError(String body) throws Exception {
        evaluate(URL, "tenant-a", "ADMIN", body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.correlationId").value("corr-test"));
    }

    @Test
    void contentTypeDistintoDeJsonResponde415() throws Exception {
        mockMvc.perform(post(URL).contentType(MediaType.TEXT_PLAIN).content("x")
                        .header("X-Tenant-Id", "tenant-a").header("X-User-Role", "ADMIN"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }
}
