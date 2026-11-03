package com.trama.mani.rules.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;

/**
 * Cuerpo de POST /api/v1/rules/rank-allies (SCRUM-1462). Rules no tiene base de datos: quien es dueño de los datos
 * envía las métricas reales de cada candidato. El criterio se valida en el controlador para responder con el mensaje
 * propio de criterio, sin caer en el error genérico de JSON. El tenant llega por X-Tenant-Id, no por el cuerpo.
 */
public record AllyRankingRequest(
        String criterio,
        @NotEmpty(message = "debe traer al menos un candidato")
        @Size(max = 500, message = "admite máximo 500 candidatos por petición")
        List<@Valid @NotNull(message = "no admite elementos nulos") Candidato> candidatos) {

    /**
     * @param id identificador del aliado
     * @param calificacionPromedio promedio de 1.0 a 5.0; null si no tiene calificaciones
     * @param totalCalificaciones calificaciones recibidas; 0 significa sin calificaciones
     * @param zonasCobertura cantidad de zonas que cubre; obligatorio solo con el criterio COBERTURA
     */
    public record Candidato(
            @NotBlank(message = "es obligatorio") String id,
            @DecimalMin(value = "1.0", message = "debe estar entre 1.0 y 5.0")
            @DecimalMax(value = "5.0", message = "debe estar entre 1.0 y 5.0") BigDecimal calificacionPromedio,
            @NotNull(message = "es obligatorio") @Min(value = 0, message = "no puede ser negativo") Integer totalCalificaciones,
            @Min(value = 0, message = "no puede ser negativo") Integer zonasCobertura) {
    }
}
