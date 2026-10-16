package com.trama.mani.rules.dto;

import java.util.List;

/**
 * allowed/ruleSetVersion/violations es exactamente la forma de RuleEvaluationResult en Core
 * (US-03.1.1-M2.2). normalized trae los valores tal como deben persistirse (nombre sin espacios
 * repetidos, flujo en mayúsculas), para que Core no reimplemente la normalización.
 */
public record CategoryEvaluationResponse(
        boolean allowed,
        String ruleSetVersion,
        List<RuleViolation> violations,
        Normalized normalized,
        String tenantId,
        String categoryId,
        String operation,
        String correlationId) {

    public record Normalized(String name, String flujoOperativo) {
    }
}
