package com.trama.mani.rules.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * Las reglas se cambian por configuración de Rules (no se despliega Core) y una configuración
 * incoherente debe impedir el arranque en vez de evaluar con ella.
 */
class CategoryRulesPropertiesTest {

    private static final String TENANT = "10000000-0000-4000-8000-000000000011";

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(RulesConfiguration.class)
            .withPropertyValues(
                    "mani.rules.category.defaults.version=category-rules-v1",
                    "mani.rules.category.defaults.name-min-length=3",
                    "mani.rules.category.defaults.name-max-length=60",
                    "mani.rules.category.defaults.name-requires-letter=true",
                    "mani.rules.category.defaults.allowed-flujos=COTIZACION_PREVIA,TARIFA_ESTANDAR");

    @Test
    void sinOverrideUsaLosDefaults() {
        runner.run(context -> {
            EffectiveCategoryRules rules = context.getBean(CategoryRulesProperties.class).forTenant("cualquiera");

            assertThat(rules).isEqualTo(new EffectiveCategoryRules(
                    "category-rules-v1", 3, 60, true, List.of("COTIZACION_PREVIA", "TARIFA_ESTANDAR")));
        });
    }

    @Test
    void unTenantSobrescribeSoloLoQueDeclaraYHeredaElResto() {
        runner.withPropertyValues(
                        "mani.rules.category.tenants[" + TENANT + "].name-max-length=80",
                        "mani.rules.category.tenants[" + TENANT + "].version=category-rules-v2-t11")
                .run(context -> {
                    CategoryRulesProperties properties = context.getBean(CategoryRulesProperties.class);
                    EffectiveCategoryRules tenant = properties.forTenant(TENANT);

                    assertThat(tenant.version()).isEqualTo("category-rules-v2-t11");
                    assertThat(tenant.nameMaxLength()).isEqualTo(80);
                    assertThat(tenant.nameMinLength()).isEqualTo(3);
                    assertThat(tenant.allowedFlujos()).containsExactly("COTIZACION_PREVIA", "TARIFA_ESTANDAR");
                    assertThat(properties.forTenant("otro-tenant").nameMaxLength()).isEqualTo(60);
                });
    }

    @Test
    void unOverrideSinVersionPropiaQuedaTrazableConElTenant() {
        runner.withPropertyValues("mani.rules.category.tenants[" + TENANT + "].name-min-length=2").run(context -> {
            assertThat(context.getBean(CategoryRulesProperties.class).forTenant(TENANT).version())
                    .isEqualTo("category-rules-v1/" + TENANT);
        });
    }

    @Test
    void normalizaLosFlujosConfiguradosYPermiteDesactivarLaLetra() {
        runner.withPropertyValues(
                        "mani.rules.category.tenants[" + TENANT + "].allowed-flujos= visita_tecnica , ,TARIFA_ESTANDAR",
                        "mani.rules.category.tenants[" + TENANT + "].name-requires-letter=false")
                .run(context -> {
                    EffectiveCategoryRules tenant = context.getBean(CategoryRulesProperties.class).forTenant(TENANT);

                    assertThat(tenant.allowedFlujos()).containsExactly("VISITA_TECNICA", "TARIFA_ESTANDAR");
                    assertThat(tenant.nameRequiresLetter()).isFalse();
                });
    }

    @Test
    void noArrancaSiElMinimoSuperaAlMaximo() {
        runner.withPropertyValues("mani.rules.category.defaults.name-min-length=70").run(context ->
                assertThat(context).hasFailed().getFailure().hasRootCauseMessage(
                        "Longitudes de nombre inválidas en reglas de categoría (category-rules-v1): min=70, max=60"));
    }

    @Test
    void noArrancaSiElMinimoEsMenorQueUno() {
        runner.withPropertyValues("mani.rules.category.defaults.name-min-length=0")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void noArrancaSiNoHayFlujosPermitidos() {
        runner.withPropertyValues("mani.rules.category.defaults.allowed-flujos=")
                .run(context -> assertThat(context).hasFailed().getFailure()
                        .hasRootCauseMessage("allowed-flujos no puede estar vacío en reglas de categoría (category-rules-v1)"));
    }

    @Test
    void noArrancaSinVersion() {
        runner.withPropertyValues("mani.rules.category.defaults.version=")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void noArrancaSiElOverrideDeUnTenantEsIncoherente() {
        runner.withPropertyValues("mani.rules.category.tenants[" + TENANT + "].name-max-length=2")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void sinLongitudesConfiguradasNoArranca() {
        new ApplicationContextRunner()
                .withUserConfiguration(RulesConfiguration.class)
                .withPropertyValues("mani.rules.category.defaults.version=v1",
                        "mani.rules.category.defaults.allowed-flujos=TARIFA_ESTANDAR")
                .run(context -> assertThat(context).hasFailed());
    }
}
