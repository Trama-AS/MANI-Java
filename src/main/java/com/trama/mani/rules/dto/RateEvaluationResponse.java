package com.trama.mani.rules.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Resultado por importe, en el mismo orden de la petición. desviacionPorcentaje es cuánto se sale del rango respecto al
 * límite cruzado (0 si está dentro), redondeado a dos decimales.
 */
public record RateEvaluationResponse(
        String version,
        BigDecimal toleranciaPorcentaje,
        String tenantId,
        List<Result> resultados,
        String correlationId) {

    public record Result(String id, String resultado, BigDecimal desviacionPorcentaje) {
    }
}
