package com.trama.mani.rules.dto;

/**
 * Regla incumplida. Los códigos son los mismos que emitía crear_categoria_servicio()
 * (migración 003): MANI-CAT-422N nombre, MANI-CAT-422F flujo operativo, MANI-CAT-422C no disponible.
 */
public record RuleViolation(String code, String field, String message) {
}
