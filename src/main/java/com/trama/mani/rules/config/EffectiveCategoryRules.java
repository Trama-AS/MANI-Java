package com.trama.mani.rules.config;

import java.util.List;

/** Reglas de categoría ya resueltas para un tenant (defaults + su override). */
public record EffectiveCategoryRules(
        String version,
        int nameMinLength,
        int nameMaxLength,
        boolean nameRequiresLetter,
        List<String> allowedFlujos) {
}
