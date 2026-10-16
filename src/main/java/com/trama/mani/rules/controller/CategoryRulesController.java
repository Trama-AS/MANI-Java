package com.trama.mani.rules.controller;

import com.trama.mani.rules.config.CategoryRulesProperties;
import com.trama.mani.rules.config.EffectiveCategoryRules;
import com.trama.mani.rules.dto.CategoryEvaluationRequest;
import com.trama.mani.rules.dto.CategoryEvaluationResponse;
import com.trama.mani.rules.dto.RuleViolation;
import com.trama.mani.rules.service.CategoryOperation;
import com.trama.mani.rules.service.CategoryRuleEvaluator;
import com.trama.mani.rules.web.ApiException;
import com.trama.mani.rules.web.CorrelationIdFilter;
import jakarta.validation.Valid;
import java.util.Locale;
import java.util.Set;
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
 * Evaluación de reglas de categoría (US-03.1.1-M6 / SCRUM-1226).
 *
 * <p>Tenant y rol llegan como encabezados que pone quien ya validó el JWT: el Gateway
 * (auth_request, nginx.conf) o Core al llamar por la red interna. Rules no es alcanzable desde
 * fuera sin pasar por el Gateway. Una evaluación que no se cumple es un 200 con allowed=false:
 * el error es de la categoría, no de la petición.
 */
@RestController
@RequestMapping({"/api/v1/rules", ""})
public class CategoryRulesController {

    private static final Logger log = LoggerFactory.getLogger(CategoryRulesController.class);
    private static final Set<String> ADMIN_ROLES = Set.of("ADMIN", "ADMIN_TENANT");

    private final CategoryRulesProperties properties;
    private final CategoryRuleEvaluator evaluator;

    public CategoryRulesController(CategoryRulesProperties properties, CategoryRuleEvaluator evaluator) {
        this.properties = properties;
        this.evaluator = evaluator;
    }

    @PostMapping("/categories/evaluate")
    public CategoryEvaluationResponse evaluate(
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantHeader,
            @RequestHeader(value = "X-User-Role", required = false) String roleHeader,
            @Valid @RequestBody CategoryEvaluationRequest request) {

        if (!StringUtils.hasText(roleHeader)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Encabezado X-User-Role requerido");
        }
        if (!StringUtils.hasText(tenantHeader)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "TENANT_REQUIRED", "Encabezado X-Tenant-Id requerido");
        }
        String tenantId = tenantHeader.trim();
        if (request.tenantId() != null && !request.tenantId().trim().equals(tenantId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "TENANT_MISMATCH",
                    "El tenantId del cuerpo no coincide con el tenant de la sesión");
        }

        CategoryOperation operation = CategoryOperation.parse(request.context().operation())
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                        "context.operation debe ser CREATE, UPDATE u OPERATE"));
        if (operation == CategoryOperation.OPERATE && request.context().active() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                    "context.active es obligatorio para OPERATE");
        }
        if (operation.requiresAdmin() && !ADMIN_ROLES.contains(roleHeader.trim().toUpperCase(Locale.ROOT))) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN",
                    "Solo el administrador del tenant gestiona categorías");
        }

        EffectiveCategoryRules rules = properties.forTenant(tenantId);
        CategoryRuleEvaluator.Result result = evaluator.evaluate(rules, operation, request.context().name(),
                request.context().flujoOperativo(), request.context().active());

        log.info("Reglas de categoría tenant={} categoria={} operacion={} version={} allowed={} violaciones={}",
                tenantId, request.categoryId(), operation, rules.version(), result.allowed(),
                result.violations().stream().map(RuleViolation::code).toList());

        return new CategoryEvaluationResponse(result.allowed(), rules.version(), result.violations(),
                result.normalized(), tenantId, request.categoryId(), operation.name(), CorrelationIdFilter.current());
    }
}
