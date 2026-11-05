package com.trama.mani.rules.service;

import java.util.Locale;
import java.util.Optional;

/** Criterio con el que se ordenan los aliados. No hay criterio por omisión: quien llama elige uno. */
public enum CriterioRanking {
    /** Mayor calificación promedio primero. */
    CALIFICACION,
    /** Más zonas de cobertura primero. */
    COBERTURA;

    public static Optional<CriterioRanking> parse(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(valueOf(raw.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }
}
