package com.trama.mani.rules.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/**
 * Cuerpo de POST /api/v1/rules/categories/evaluate. Refleja el puerto que Core declaró en
 * US-03.1.1-M2.2: evaluateCategoryRules({ tenantId, categoryId, context }). El tenant NO se toma
 * de aquí sino de X-Tenant-Id (claim validado por el Gateway o reenviado por Core); si el cuerpo
 * trae tenantId y no coincide, la petición se rechaza.
 */
public record CategoryEvaluationRequest(
        String tenantId,
        String categoryId,
        @NotNull(message = "es obligatorio") @Valid Context context) {

    /**
     * @param operation CREATE | UPDATE | OPERATE
     * @param name nombre propuesto (CREATE obligatorio; UPDATE solo si cambia)
     * @param flujoOperativo flujo propuesto (CREATE obligatorio; UPDATE solo si cambia)
     * @param active estado actual de la categoría (obligatorio en OPERATE)
     */
    public record Context(
            @NotNull(message = "es obligatorio") String operation,
            String name,
            String flujoOperativo,
            Boolean active) {
    }
}
