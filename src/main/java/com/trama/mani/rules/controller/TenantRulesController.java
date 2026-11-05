package com.trama.mani.rules.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.trama.mani.rules.service.TenantRulesValidator;
import com.trama.mani.rules.web.ApiException;
import com.trama.mani.rules.web.CorrelationIdFilter;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Validación de las reglas operativas propias del tenant (SCRUM-1366 / HU-M01-03). Core la llama por la red interna
 * antes de guardar una versión nueva. Unas reglas incoherentes son un 200 con {@code valid=false} y la lista de
 * violaciones: el error es de las reglas, no de la petición. Tenant y rol llegan por encabezado, como en el resto de
 * Rules.
 */
@RestController
@RequestMapping({"/api/v1/rules", ""})
public class TenantRulesController {

    private static final Logger log = LoggerFactory.getLogger(TenantRulesController.class);

    private final TenantRulesValidator validator;

    public TenantRulesController(TenantRulesValidator validator) {
        this.validator = validator;
    }

    public record Response(boolean valid, String version, List<String> violations, String tenantId,
            String correlationId) {
    }

    @PostMapping("/tenant-rules/validate")
    public Response validate(
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantHeader,
            @RequestHeader(value = "X-User-Role", required = false) String roleHeader,
            @RequestBody JsonNode body) {

        if (!StringUtils.hasText(roleHeader)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Encabezado X-User-Role requerido");
        }
        if (!StringUtils.hasText(tenantHeader)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "TENANT_REQUIRED", "Encabezado X-Tenant-Id requerido");
        }
        if (body == null || !body.has("rules")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "rules es obligatorio");
        }
        String tenantId = tenantHeader.trim();
        List<String> violations = validator.violations(body.get("rules"));
        log.info("Validación de reglas de tenant tenant={} version={} valid={} violaciones={}", tenantId,
                validator.version(), violations.isEmpty(), violations.size());
        return new Response(violations.isEmpty(), validator.version(), violations, tenantId,
                CorrelationIdFilter.current());
    }
}
