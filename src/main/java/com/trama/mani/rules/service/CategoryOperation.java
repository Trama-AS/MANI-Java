package com.trama.mani.rules.service;

import java.util.Locale;
import java.util.Optional;

/** Momento del ciclo de vida de la categoría que se evalúa. */
public enum CategoryOperation {
    /** Backoffice crea la categoría: nombre y flujo obligatorios. */
    CREATE,
    /** Backoffice edita: solo se evalúan los campos enviados (actualización parcial). */
    UPDATE,
    /** La categoría se usa en la operación (p. ej. un aliado la declara): debe estar activa. */
    OPERATE;

    public boolean requiresAdmin() {
        return this != OPERATE;
    }

    public static Optional<CategoryOperation> parse(String raw) {
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
