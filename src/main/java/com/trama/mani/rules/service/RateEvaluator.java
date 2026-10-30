package com.trama.mani.rules.service;

import com.trama.mani.rules.dto.RateEvaluationRequest.Item;
import com.trama.mani.rules.dto.RateEvaluationResponse.Result;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Evaluación de importes contra su tarifa de referencia (RF-22 / RF-23, SCRUM-887 y SCRUM-1462). Función pura: no
 * consulta datos, así que el mismo importe y la misma tarifa dan siempre el mismo resultado.
 *
 * <p>Un importe está dentro del rango si cumple mínimo × (1 − tolerancia) ≤ total ≤ máximo × (1 + tolerancia). La
 * tolerancia es configuración de Rules (mani.rules.rates.tolerance-percent): cambiarla no exige desplegar Core.
 */
@Service
public class RateEvaluator {

    public static final String DENTRO = "DENTRO_DE_RANGO";
    public static final String POR_DEBAJO = "POR_DEBAJO";
    public static final String POR_ENCIMA = "POR_ENCIMA";

    private static final BigDecimal CIEN = BigDecimal.valueOf(100);

    private final String version;
    private final BigDecimal tolerancia;

    public RateEvaluator(
            @Value("${mani.rules.rates.version:rates-v1}") String version,
            @Value("${mani.rules.rates.tolerance-percent:0}") BigDecimal toleranciaPorcentaje) {
        if (toleranciaPorcentaje.signum() < 0 || toleranciaPorcentaje.compareTo(CIEN) >= 0) {
            throw new IllegalArgumentException("mani.rules.rates.tolerance-percent debe estar entre 0 y 100");
        }
        this.version = version;
        this.tolerancia = toleranciaPorcentaje;
    }

    public String version() {
        return version;
    }

    public BigDecimal toleranciaPorcentaje() {
        return tolerancia;
    }

    /** Primer importe cuya tarifa no cumple mínimo ≤ típico ≤ máximo con mínimo < máximo, si lo hay. */
    public Optional<String> tarifaInvalida(List<Item> items) {
        return items.stream()
                .filter(i -> i.valorMin().compareTo(i.valorTipico()) > 0
                        || i.valorTipico().compareTo(i.valorMax()) > 0
                        || i.valorMin().compareTo(i.valorMax()) >= 0)
                .map(Item::id)
                .findFirst();
    }

    public Result evaluate(Item item) {
        BigDecimal factor = tolerancia.divide(CIEN, 6, RoundingMode.HALF_UP);
        BigDecimal piso = item.valorMin().multiply(BigDecimal.ONE.subtract(factor));
        BigDecimal techo = item.valorMax().multiply(BigDecimal.ONE.add(factor));
        BigDecimal total = item.total();

        if (total.compareTo(piso) < 0) {
            // Aquí valorMin > 0: un total no negativo no puede quedar por debajo de un piso de 0.
            return new Result(item.id(), POR_DEBAJO, porcentaje(item.valorMin().subtract(total), item.valorMin()));
        }
        if (total.compareTo(techo) > 0) {
            return new Result(item.id(), POR_ENCIMA, porcentaje(total.subtract(item.valorMax()), item.valorMax()));
        }
        return new Result(item.id(), DENTRO, BigDecimal.ZERO.setScale(2));
    }

    private static BigDecimal porcentaje(BigDecimal diferencia, BigDecimal base) {
        return diferencia.multiply(CIEN).divide(base, 2, RoundingMode.HALF_UP);
    }
}
