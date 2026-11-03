package com.trama.mani.rules.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Candidatos en el orden en que se deben ofrecer. Los nombres posicion, puntaje y razones son los de EligibleAlly en
 * MANI-API/docs/openapi/dispatch.yaml, y criterio el de RankingInfo.
 */
public record AllyRankingResponse(
        String criterio,
        String tenantId,
        List<RankedAlly> aliados,
        String correlationId) {

    /**
     * @param id identificador del aliado
     * @param posicion puesto, desde 1
     * @param puntaje de 0 a 1, informativo; null si el aliado no tiene dato para el criterio
     * @param razones explicación legible del puesto
     */
    public record RankedAlly(String id, int posicion, BigDecimal puntaje, List<String> razones) {
    }
}
