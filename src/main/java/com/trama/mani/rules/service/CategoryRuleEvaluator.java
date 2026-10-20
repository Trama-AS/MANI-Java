package com.trama.mani.rules.service;

import com.trama.mani.rules.config.EffectiveCategoryRules;
import com.trama.mani.rules.dto.CategoryEvaluationResponse.Normalized;
import com.trama.mani.rules.dto.RuleViolation;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

/**
 * Reglas operativas de la categoría de servicio, migradas de crear_categoria_servicio()
 * (MANI-APIGateway/database/migrations/003_categorias_servicio.sql, SCRUM-857) y de las
 * validaciones equivalentes que tenía Core Node. Función pura: mismas entradas y mismas reglas
 * producen siempre el mismo resultado; no consulta BD.
 *
 * <p>Fuera de alcance a propósito: el nombre único por tenant (MANI-CAT-409) necesita estado y
 * lo sigue decidiendo el índice ux_categoria_tenant_nombre.
 */
@Service
public class CategoryRuleEvaluator {

    public static final String NAME_INVALID = "MANI-CAT-422N";
    public static final String FLUJO_INVALID = "MANI-CAT-422F";
    public static final String NOT_AVAILABLE = "MANI-CAT-422C";

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final Pattern HAS_LETTER = Pattern.compile("\\p{L}");

    public Result evaluate(EffectiveCategoryRules rules, CategoryOperation operation, String name, String flujo,
            Boolean active) {
        List<RuleViolation> violations = new ArrayList<>();
        String normalizedName = normalizeName(name);
        String normalizedFlujo = normalizeFlujo(flujo);

        switch (operation) {
            case CREATE -> {
                checkName(rules, normalizedName, violations);
                checkFlujo(rules, normalizedFlujo, violations);
            }
            case UPDATE -> {
                if (name != null) {
                    checkName(rules, normalizedName, violations);
                }
                if (flujo != null) {
                    checkFlujo(rules, normalizedFlujo, violations);
                }
            }
            case OPERATE -> {
                if (!Boolean.TRUE.equals(active)) {
                    violations.add(new RuleViolation(NOT_AVAILABLE, "active", "la categoría está inactiva"));
                }
            }
        }

        return new Result(violations.isEmpty(), List.copyOf(violations), new Normalized(normalizedName, normalizedFlujo));
    }

    private static void checkName(EffectiveCategoryRules rules, String name, List<RuleViolation> violations) {
        if (name == null || name.isEmpty()) {
            violations.add(new RuleViolation(NAME_INVALID, "name", "el nombre es obligatorio"));
            return;
        }
        // char_length() de Postgres cuenta caracteres, no unidades UTF-16.
        int length = name.codePointCount(0, name.length());
        if (length < rules.nameMinLength() || length > rules.nameMaxLength()) {
            violations.add(new RuleViolation(NAME_INVALID, "name", "el nombre debe tener entre "
                    + rules.nameMinLength() + " y " + rules.nameMaxLength() + " caracteres"));
        }
        if (rules.nameRequiresLetter() && !HAS_LETTER.matcher(name).find()) {
            violations.add(new RuleViolation(NAME_INVALID, "name", "el nombre debe contener al menos una letra"));
        }
    }

    private static void checkFlujo(EffectiveCategoryRules rules, String flujo, List<RuleViolation> violations) {
        if (flujo == null || flujo.isEmpty()) {
            violations.add(new RuleViolation(FLUJO_INVALID, "flujoOperativo", "el flujo operativo es obligatorio"));
            return;
        }
        if (!rules.allowedFlujos().contains(flujo)) {
            violations.add(new RuleViolation(FLUJO_INVALID, "flujoOperativo", "flujo operativo inválido (" + flujo
                    + "); permitidos: " + String.join(", ", rules.allowedFlujos())));
        }
    }

    static String normalizeName(String name) {
        return name == null ? null : WHITESPACE.matcher(name.strip()).replaceAll(" ");
    }

    static String normalizeFlujo(String flujo) {
        return flujo == null ? null : flujo.strip().toUpperCase(Locale.ROOT);
    }

    public record Result(boolean allowed, List<RuleViolation> violations, Normalized normalized) {
    }
}
