package com.trama.mani.rules.controller;

import com.trama.mani.rules.dto.RateEvaluationRequest;
import com.trama.mani.rules.dto.RateEvaluationResponse;
import com.trama.mani.rules.dto.RateEvaluationResponse.Result;
import com.trama.mani.rules.service.RateEvaluator;
import com.trama.mani.rules.web.ApiException;
import com.trama.mani.rules.web.CorrelationIdFilter;
import jakarta.validation.Valid;
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
 * Evaluación de importes contra la tarifa de referencia (SCRUM-887 / SCRUM-1462). Como en las reglas de categoría,
 * tenant y rol llegan como encabezados de quien ya validó el JWT (Gateway o Core por la red interna). Un importe fuera
 * de rango es un 200 con su resultado: el dato es del importe, no un error de la petición.
 */
@RestController
@RequestMapping({"/api/v1/rules", ""})
public class RatesController {

    private static final Logger log = LoggerFactory.getLogger(RatesController.class);

    private final RateEvaluator evaluator;

    public RatesController(RateEvaluator evaluator) {
        this.evaluator = evaluator;
    }

    @PostMapping("/rates/evaluate")
    public RateEvaluationResponse evaluate(
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantHeader,
            @RequestHeader(value = "X-User-Role", required = false) String roleHeader,
            @Valid @RequestBody RateEvaluationRequest request) {

        if (!StringUtils.hasText(roleHeader)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Encabezado X-User-Role requerido");
        }
        if (!StringUtils.hasText(tenantHeader)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "TENANT_REQUIRED", "Encabezado X-Tenant-Id requerido");
        }
        evaluator.tarifaInvalida(request.items()).ifPresent(id -> {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "La tarifa del importe " + id
                    + " debe cumplir valorMin <= valorTipico <= valorMax y valorMin < valorMax");
        });

        String tenantId = tenantHeader.trim();
        List<Result> resultados = request.items().stream().map(evaluator::evaluate).toList();
        long fuera = resultados.stream().filter(r -> !RateEvaluator.DENTRO.equals(r.resultado())).count();
        log.info("Evaluacion de tarifas tenant={} version={} importes={} fueraDeRango={}",
                tenantId, evaluator.version(), resultados.size(), fuera);

        return new RateEvaluationResponse(evaluator.version(), evaluator.toleranciaPorcentaje(), tenantId, resultados,
                CorrelationIdFilter.current());
    }
}
