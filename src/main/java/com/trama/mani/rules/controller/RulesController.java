package com.trama.mani.rules.controller;

import com.trama.mani.rules.dto.AllyRankingRequest;
import com.trama.mani.rules.dto.AllyRankingResponse;
import com.trama.mani.rules.dto.AllyRankingResponse.RankedAlly;
import com.trama.mani.rules.service.AllyRanker;
import com.trama.mani.rules.service.CriterioRanking;
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
 * Ranking de aliados (RF-13 / SCRUM-1462). La tarifa vive en {@link RatesController}. Como en las reglas de categoría,
 * tenant y rol llegan como encabezados de quien ya validó el JWT (Gateway o Dispatch por la red interna). El Gateway
 * recorta "/api/v1/rules" antes de reenviar (nginx.conf), así que la ruta responde con y sin ese prefijo. CORS lo
 * resuelve únicamente el Gateway.
 */
@RestController
@RequestMapping({"/api/v1/rules", ""})
public class RulesController {

    private static final Logger log = LoggerFactory.getLogger(RulesController.class);

    private final AllyRanker ranker;

    public RulesController(AllyRanker ranker) {
        this.ranker = ranker;
    }

    @PostMapping("/rank-allies")
    public AllyRankingResponse rankAllies(
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantHeader,
            @RequestHeader(value = "X-User-Role", required = false) String roleHeader,
            @Valid @RequestBody AllyRankingRequest request) {

        if (!StringUtils.hasText(roleHeader)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Encabezado X-User-Role requerido");
        }
        if (!StringUtils.hasText(tenantHeader)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "TENANT_REQUIRED", "Encabezado X-Tenant-Id requerido");
        }
        CriterioRanking criterio = CriterioRanking.parse(request.criterio())
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                        "criterio debe ser CALIFICACION o COBERTURA"));
        ranker.datosInvalidos(criterio, request.candidatos()).ifPresent(problema -> {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", problema);
        });

        String tenantId = tenantHeader.trim();
        List<RankedAlly> aliados = ranker.rank(criterio, request.candidatos());
        log.info("Ranking de aliados tenant={} criterio={} candidatos={}", tenantId, criterio, aliados.size());

        return new AllyRankingResponse(criterio.name(), tenantId, aliados, CorrelationIdFilter.current());
    }
}
