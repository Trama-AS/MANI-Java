package com.trama.mani.rules.dto;

/** Forma de error común con Core Node: { error, code, correlationId }. */
public record ApiError(String error, String code, String correlationId) {
}
