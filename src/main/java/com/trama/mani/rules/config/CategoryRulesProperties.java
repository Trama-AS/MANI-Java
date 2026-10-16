package com.trama.mani.rules.config;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Parámetros de las reglas de categoría (US-03.1.1 / SCRUM-857), externalizados para cambiarlos
 * redesplegando o reconfigurando solo el Rules Service, nunca Core (RF-02).
 *
 * <pre>
 * mani.rules.category.defaults.version=category-rules-v1
 * mani.rules.category.defaults.name-max-length=60
 * mani.rules.category.tenants[&lt;tenant-uuid&gt;].name-max-length=80
 * </pre>
 *
 * Un tenant solo sobrescribe los campos que declara; el resto hereda de {@code defaults}.
 */
@ConfigurationProperties(prefix = "mani.rules.category")
public class CategoryRulesProperties implements InitializingBean {

    private RuleSet defaults = new RuleSet();
    private Map<String, RuleSet> tenants = new HashMap<>();

    public EffectiveCategoryRules forTenant(String tenantId) {
        RuleSet override = tenants.get(tenantId);
        if (override == null) {
            return toEffective(defaults, defaults.getVersion());
        }
        String version = override.getVersion() != null ? override.getVersion() : defaults.getVersion() + "/" + tenantId;
        RuleSet merged = new RuleSet();
        merged.setNameMinLength(pick(override.getNameMinLength(), defaults.getNameMinLength()));
        merged.setNameMaxLength(pick(override.getNameMaxLength(), defaults.getNameMaxLength()));
        merged.setNameRequiresLetter(pick(override.getNameRequiresLetter(), defaults.getNameRequiresLetter()));
        merged.setAllowedFlujos(override.getAllowedFlujos() != null ? override.getAllowedFlujos() : defaults.getAllowedFlujos());
        return toEffective(merged, version);
    }

    /** Falla el arranque ante una configuración incoherente en vez de evaluar con ella. */
    @Override
    public void afterPropertiesSet() {
        if (defaults.getVersion() == null || defaults.getVersion().isBlank()) {
            throw new IllegalStateException("mani.rules.category.defaults.version es obligatorio");
        }
        forTenant(null);
        tenants.keySet().forEach(this::forTenant);
    }

    private static EffectiveCategoryRules toEffective(RuleSet set, String version) {
        Integer min = set.getNameMinLength();
        Integer max = set.getNameMaxLength();
        if (min == null || max == null || min < 1 || max < min) {
            throw new IllegalStateException(
                    "Longitudes de nombre inválidas en reglas de categoría (" + version + "): min=" + min + ", max=" + max);
        }
        List<String> flujos = set.getAllowedFlujos() == null ? List.of() : set.getAllowedFlujos().stream()
                .map(f -> f.trim().toUpperCase(Locale.ROOT))
                .filter(f -> !f.isEmpty())
                .toList();
        if (flujos.isEmpty()) {
            throw new IllegalStateException("allowed-flujos no puede estar vacío en reglas de categoría (" + version + ")");
        }
        boolean requiresLetter = set.getNameRequiresLetter() == null || set.getNameRequiresLetter();
        return new EffectiveCategoryRules(version, min, max, requiresLetter, flujos);
    }

    private static <T> T pick(T override, T fallback) {
        return override != null ? override : fallback;
    }

    public RuleSet getDefaults() {
        return defaults;
    }

    public void setDefaults(RuleSet defaults) {
        this.defaults = defaults;
    }

    public Map<String, RuleSet> getTenants() {
        return tenants;
    }

    public void setTenants(Map<String, RuleSet> tenants) {
        this.tenants = tenants;
    }

    public static class RuleSet {
        private String version;
        private Integer nameMinLength;
        private Integer nameMaxLength;
        private Boolean nameRequiresLetter;
        // null (no lista vacía) para que un tenant que no lo declara herede el de defaults.
        private List<String> allowedFlujos;

        public String getVersion() {
            return version;
        }

        public void setVersion(String version) {
            this.version = version;
        }

        public Integer getNameMinLength() {
            return nameMinLength;
        }

        public void setNameMinLength(Integer nameMinLength) {
            this.nameMinLength = nameMinLength;
        }

        public Integer getNameMaxLength() {
            return nameMaxLength;
        }

        public void setNameMaxLength(Integer nameMaxLength) {
            this.nameMaxLength = nameMaxLength;
        }

        public Boolean getNameRequiresLetter() {
            return nameRequiresLetter;
        }

        public void setNameRequiresLetter(Boolean nameRequiresLetter) {
            this.nameRequiresLetter = nameRequiresLetter;
        }

        public List<String> getAllowedFlujos() {
            return allowedFlujos;
        }

        public void setAllowedFlujos(List<String> allowedFlujos) {
            this.allowedFlujos = allowedFlujos;
        }
    }
}
