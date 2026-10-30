package com.trama.mani.rules.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;

/**
 * Cuerpo de POST /api/v1/rules/rates/evaluate (SCRUM-887 / SCRUM-1462). Rules no tiene base de datos: quien es dueño
 * de los datos (Core) envía los hechos, cada importe con la tarifa de referencia que regía cuando se emitió. El tenant
 * llega por X-Tenant-Id, no por el cuerpo.
 */
public record RateEvaluationRequest(
        @NotEmpty(message = "debe traer al menos un importe")
        @Size(max = 500, message = "admite máximo 500 importes por petición")
        List<@Valid @NotNull(message = "no admite elementos nulos") Item> items) {

    /**
     * @param id identificador del importe para quien llama (p. ej. la cotización)
     * @param total importe a evaluar
     * @param valorMin mínimo de la tarifa de referencia
     * @param valorTipico típico de la tarifa de referencia
     * @param valorMax máximo de la tarifa de referencia
     */
    public record Item(
            @NotBlank(message = "es obligatorio") String id,
            @NotNull(message = "es obligatorio") @DecimalMin(value = "0", message = "no puede ser negativo") BigDecimal total,
            @NotNull(message = "es obligatorio") @DecimalMin(value = "0", message = "no puede ser negativo") BigDecimal valorMin,
            @NotNull(message = "es obligatorio") @DecimalMin(value = "0", message = "no puede ser negativo") BigDecimal valorTipico,
            @NotNull(message = "es obligatorio") @DecimalMin(value = "0", message = "no puede ser negativo") BigDecimal valorMax) {
    }
}
