package com.trama.mani.rules.service;

import static com.trama.mani.rules.service.CategoryRuleEvaluator.FLUJO_INVALID;
import static com.trama.mani.rules.service.CategoryRuleEvaluator.NAME_INVALID;
import static com.trama.mani.rules.service.CategoryRuleEvaluator.NOT_AVAILABLE;
import static org.assertj.core.api.Assertions.assertThat;

import com.trama.mani.rules.config.EffectiveCategoryRules;
import com.trama.mani.rules.dto.RuleViolation;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/** Reglas de crear_categoria_servicio() (migración 003, SCRUM-857) portadas a Rules. */
class CategoryRuleEvaluatorTest {

    private static final EffectiveCategoryRules DEFAULTS =
            new EffectiveCategoryRules("v1", 3, 60, true, List.of("COTIZACION_PREVIA", "TARIFA_ESTANDAR"));

    private final CategoryRuleEvaluator evaluator = new CategoryRuleEvaluator();

    private CategoryRuleEvaluator.Result create(String name, String flujo) {
        return evaluator.evaluate(DEFAULTS, CategoryOperation.CREATE, name, flujo, null);
    }

    private static List<String> codes(CategoryRuleEvaluator.Result result) {
        return result.violations().stream().map(RuleViolation::code).toList();
    }

    @Nested
    @DisplayName("CREATE")
    class Create {

        @Test
        void aceptaUnaCategoriaValidaYNormalizaNombreYFlujo() {
            CategoryRuleEvaluator.Result result = create("  Plomería   Express \t", " tarifa_estandar ");

            assertThat(result.allowed()).isTrue();
            assertThat(result.violations()).isEmpty();
            assertThat(result.normalized().name()).isEqualTo("Plomería Express");
            assertThat(result.normalized().flujoOperativo()).isEqualTo("TARIFA_ESTANDAR");
        }

        @ParameterizedTest(name = "longitud {0} => allowed={1}")
        @CsvSource({"2, false", "3, true", "60, true", "61, false"})
        void respetaLosLimitesDeLongitudDelNombre(int length, boolean allowed) {
            CategoryRuleEvaluator.Result result = create("a".repeat(length), "TARIFA_ESTANDAR");

            assertThat(result.allowed()).isEqualTo(allowed);
            if (!allowed) {
                assertThat(result.violations()).singleElement()
                        .satisfies(v -> {
                            assertThat(v.code()).isEqualTo(NAME_INVALID);
                            assertThat(v.field()).isEqualTo("name");
                            assertThat(v.message()).contains("entre 3 y 60");
                        });
            }
        }

        @Test
        void cuentaCaracteresComoPostgresYNoUnidadesUtf16() {
            // 59 letras + un emoji: 60 caracteres (char_length) aunque sean 61 unidades UTF-16.
            String name = "a".repeat(59) + "😀";
            assertThat(name.length()).isEqualTo(61);

            assertThat(create(name, "TARIFA_ESTANDAR").allowed()).isTrue();
        }

        @Test
        void colapsaEspaciosAntesDeMedirLaLongitud() {
            assertThat(create("a     b", "TARIFA_ESTANDAR").allowed()).isTrue();
            assertThat(create("  a  ", "TARIFA_ESTANDAR").allowed()).isFalse();
        }

        @ParameterizedTest
        @ValueSource(strings = {"123", "!!!-", "2026 99"})
        void exigeAlMenosUnaLetra(String name) {
            CategoryRuleEvaluator.Result result = create(name, "TARIFA_ESTANDAR");

            assertThat(result.allowed()).isFalse();
            assertThat(result.violations()).extracting(RuleViolation::message)
                    .contains("el nombre debe contener al menos una letra");
        }

        @ParameterizedTest
        @ValueSource(strings = {"Ñu 24h", "Uñas 3D", "电工服务", "Αβγ"})
        void aceptaLetrasDeCualquierIdioma(String name) {
            assertThat(create(name, "TARIFA_ESTANDAR").allowed()).isTrue();
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"   ", "\t\n"})
        void exigeElNombre(String name) {
            CategoryRuleEvaluator.Result result = create(name, "TARIFA_ESTANDAR");

            assertThat(codes(result)).containsExactly(NAME_INVALID);
            assertThat(result.violations().get(0).message()).isEqualTo("el nombre es obligatorio");
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"  "})
        void exigeElFlujoOperativo(String flujo) {
            CategoryRuleEvaluator.Result result = create("Plomería", flujo);

            assertThat(codes(result)).containsExactly(FLUJO_INVALID);
            assertThat(result.violations().get(0).message()).isEqualTo("el flujo operativo es obligatorio");
        }

        @ParameterizedTest
        @ValueSource(strings = {"OTRO", "COTIZACION", "TARIFA ESTANDAR"})
        void rechazaFlujosNoPermitidos(String flujo) {
            CategoryRuleEvaluator.Result result = create("Plomería", flujo);

            assertThat(result.violations()).singleElement().satisfies(v -> {
                assertThat(v.code()).isEqualTo(FLUJO_INVALID);
                assertThat(v.field()).isEqualTo("flujoOperativo");
                assertThat(v.message()).contains("COTIZACION_PREVIA, TARIFA_ESTANDAR");
            });
        }

        @ParameterizedTest
        @ValueSource(strings = {"cotizacion_previa", "Tarifa_Estandar", " COTIZACION_PREVIA "})
        void aceptaElFlujoSinDistinguirMayusculasNiEspaciosExtremos(String flujo) {
            assertThat(create("Plomería", flujo).allowed()).isTrue();
        }

        @Test
        void acumulaTodasLasViolacionesEnUnaSolaEvaluacion() {
            assertThat(codes(create("12", "OTRO"))).containsExactly(NAME_INVALID, NAME_INVALID, FLUJO_INVALID);
        }
    }

    @Nested
    @DisplayName("UPDATE")
    class Update {

        @Test
        void soloEvaluaElNombreSiEsLoUnicoQueCambia() {
            CategoryRuleEvaluator.Result result =
                    evaluator.evaluate(DEFAULTS, CategoryOperation.UPDATE, " Plomería  Pro ", null, null);

            assertThat(result.allowed()).isTrue();
            assertThat(result.normalized().name()).isEqualTo("Plomería Pro");
            assertThat(result.normalized().flujoOperativo()).isNull();
        }

        @Test
        void soloEvaluaElFlujoSiEsLoUnicoQueCambia() {
            assertThat(codes(evaluator.evaluate(DEFAULTS, CategoryOperation.UPDATE, null, "OTRO", null)))
                    .containsExactly(FLUJO_INVALID);
        }

        @Test
        void sinCamposDeReglaNoHayNadaQueRechazar() {
            assertThat(evaluator.evaluate(DEFAULTS, CategoryOperation.UPDATE, null, null, null).allowed()).isTrue();
        }

        @Test
        void unNombreEnviadoEnBlancoSeRechaza() {
            assertThat(codes(evaluator.evaluate(DEFAULTS, CategoryOperation.UPDATE, "   ", null, null)))
                    .containsExactly(NAME_INVALID);
        }
    }

    @Nested
    @DisplayName("OPERATE")
    class Operate {

        @Test
        void unaCategoriaActivaSePuedeOperar() {
            assertThat(evaluator.evaluate(DEFAULTS, CategoryOperation.OPERATE, null, null, true).allowed()).isTrue();
        }

        @Test
        void unaCategoriaInactivaNoEstaDisponible() {
            CategoryRuleEvaluator.Result result = evaluator.evaluate(DEFAULTS, CategoryOperation.OPERATE, null, null, false);

            assertThat(result.allowed()).isFalse();
            assertThat(result.violations()).singleElement()
                    .isEqualTo(new RuleViolation(NOT_AVAILABLE, "active", "la categoría está inactiva"));
        }

        @Test
        void noEvaluaNombreNiFlujoAlOperar() {
            // Filas históricas pueden tener flujo_operativo NULL: operar no las vuelve a juzgar.
            assertThat(evaluator.evaluate(DEFAULTS, CategoryOperation.OPERATE, "x", null, true).allowed()).isTrue();
        }
    }

    @Nested
    @DisplayName("Reglas configurables")
    class Configurable {

        @Test
        void usaLosLimitesYFlujosDelTenant() {
            EffectiveCategoryRules custom = new EffectiveCategoryRules("t-v2", 2, 80, true, List.of("VISITA_TECNICA"));

            assertThat(evaluator.evaluate(custom, CategoryOperation.CREATE, "AB", "visita_tecnica", null).allowed()).isTrue();
            assertThat(evaluator.evaluate(custom, CategoryOperation.CREATE, "a".repeat(80), "VISITA_TECNICA", null).allowed())
                    .isTrue();
            assertThat(codes(evaluator.evaluate(custom, CategoryOperation.CREATE, "AB", "TARIFA_ESTANDAR", null)))
                    .containsExactly(FLUJO_INVALID);
        }

        @Test
        void puedeDesactivarLaExigenciaDeLetra() {
            EffectiveCategoryRules noLetter = new EffectiveCategoryRules("v", 3, 60, false, List.of("TARIFA_ESTANDAR"));

            assertThat(evaluator.evaluate(noLetter, CategoryOperation.CREATE, "123", "TARIFA_ESTANDAR", null).allowed())
                    .isTrue();
        }

        @Test
        void esDeterministaParaLaMismaEntrada() {
            assertThat(create("12", "OTRO")).isEqualTo(create("12", "OTRO"));
        }
    }

    @Test
    void parseDeOperacionIgnoraMayusculasYRechazaDesconocidas() {
        assertThat(CategoryOperation.parse(" create ")).contains(CategoryOperation.CREATE);
        assertThat(CategoryOperation.parse("DELETE")).isEmpty();
        assertThat(CategoryOperation.parse(null)).isEmpty();
        assertThat(CategoryOperation.CREATE.requiresAdmin()).isTrue();
        assertThat(CategoryOperation.UPDATE.requiresAdmin()).isTrue();
        assertThat(CategoryOperation.OPERATE.requiresAdmin()).isFalse();
    }
}
