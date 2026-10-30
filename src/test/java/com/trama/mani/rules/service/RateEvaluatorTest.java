package com.trama.mani.rules.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.trama.mani.rules.dto.RateEvaluationRequest.Item;
import com.trama.mani.rules.dto.RateEvaluationResponse.Result;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class RateEvaluatorTest {

    private static Item item(String total) {
        return new Item("c1", new BigDecimal(total), new BigDecimal("100000"), new BigDecimal("150000"),
                new BigDecimal("200000"));
    }

    @Test
    void sinToleranciaLosLimitesSonInclusivos() {
        RateEvaluator evaluator = new RateEvaluator("rates-v1", BigDecimal.ZERO);

        assertThat(evaluator.evaluate(item("100000")).resultado()).isEqualTo(RateEvaluator.DENTRO);
        assertThat(evaluator.evaluate(item("200000")).resultado()).isEqualTo(RateEvaluator.DENTRO);
        assertThat(evaluator.evaluate(item("99999.99")).resultado()).isEqualTo(RateEvaluator.POR_DEBAJO);
        assertThat(evaluator.evaluate(item("200000.01")).resultado()).isEqualTo(RateEvaluator.POR_ENCIMA);
    }

    @Test
    void laDesviacionSeMideContraElLimiteCruzadoConDosDecimales() {
        RateEvaluator evaluator = new RateEvaluator("rates-v1", BigDecimal.ZERO);

        Result debajo = evaluator.evaluate(item("66666"));
        Result encima = evaluator.evaluate(item("250000"));

        assertThat(debajo.desviacionPorcentaje()).isEqualByComparingTo("33.33");
        assertThat(encima.desviacionPorcentaje()).isEqualByComparingTo("25.00");
        assertThat(evaluator.evaluate(item("150000")).desviacionPorcentaje()).isEqualByComparingTo("0");
    }

    @Test
    void laToleranciaAmpliaElRangoAmbosLados() {
        RateEvaluator evaluator = new RateEvaluator("rates-v2", new BigDecimal("10"));

        assertThat(evaluator.evaluate(item("90000")).resultado()).isEqualTo(RateEvaluator.DENTRO);
        assertThat(evaluator.evaluate(item("220000")).resultado()).isEqualTo(RateEvaluator.DENTRO);
        assertThat(evaluator.evaluate(item("89999")).resultado()).isEqualTo(RateEvaluator.POR_DEBAJO);
        assertThat(evaluator.evaluate(item("220001")).resultado()).isEqualTo(RateEvaluator.POR_ENCIMA);
        assertThat(evaluator.version()).isEqualTo("rates-v2");
    }

    @Test
    void unMinimoDeCeroNuncaDejaUnImportePorDebajo() {
        RateEvaluator evaluator = new RateEvaluator("rates-v1", BigDecimal.ZERO);
        Item gratis = new Item("c0", BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ONE, BigDecimal.TEN);

        assertThat(evaluator.evaluate(gratis).resultado()).isEqualTo(RateEvaluator.DENTRO);
    }

    @Test
    void detectaLaPrimeraTarifaInvalida() {
        RateEvaluator evaluator = new RateEvaluator("rates-v1", BigDecimal.ZERO);
        Item invertida = new Item("mala", BigDecimal.ONE, new BigDecimal("5"), new BigDecimal("3"), new BigDecimal("9"));
        Item plana = new Item("plana", BigDecimal.ONE, BigDecimal.TEN, BigDecimal.TEN, BigDecimal.TEN);

        assertThat(evaluator.tarifaInvalida(List.of(item("1"), invertida, plana))).contains("mala");
        assertThat(evaluator.tarifaInvalida(List.of(plana))).contains("plana");
        assertThat(evaluator.tarifaInvalida(List.of(item("1")))).isEmpty();
    }

    @Test
    void unaToleranciaFueraDeRangoImpideArrancar() {
        BigDecimal negativa = new BigDecimal("-1");
        BigDecimal cien = new BigDecimal("100");
        assertThatThrownBy(() -> new RateEvaluator("v", negativa)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RateEvaluator("v", cien)).isInstanceOf(IllegalArgumentException.class);
    }
}
