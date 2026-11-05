package com.trama.mani.rules.service;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Política de las reglas operativas propias de un tenant (SCRUM-1366 / HU-M01-03). Core guarda las reglas y antes de
 * versionarlas pregunta aquí si son coherentes; Rules no guarda nada. Función pura: las mismas reglas con la misma
 * versión de política dan siempre el mismo resultado.
 *
 * <p>Límites: orden de listado CALIFICACION o COBERTURA (los criterios de /rank-allies); tolerancia de la alerta de
 * tarifa de 0 a 50 %; comisión de 0 a 30 %; ventana de cancelación de 0 a 72 horas y penalización de 0 a 100 %, con
 * penalización mayor que 0 solo si hay ventana; tiempo para aceptar la oferta de 1 a 1440 minutos (entero).
 */
@Service
public class TenantRulesValidator {

    private final String version;

    public TenantRulesValidator(@Value("${mani.rules.tenant.version:tenant-rules-v1}") String version) {
        this.version = version;
    }

    public String version() {
        return version;
    }

    /** Lista de reglas incumplidas; vacía si el conjunto es válido. */
    public List<String> violations(JsonNode reglas) {
        List<String> v = new ArrayList<>();
        if (reglas == null || !reglas.isObject()) {
            v.add("rules debe ser un objeto");
            return v;
        }
        String criterio = texto(reglas.path("ordenListado").path("criterio"));
        if (criterio == null || CriterioRanking.parse(criterio).isEmpty()) {
            v.add("ordenListado.criterio debe ser CALIFICACION o COBERTURA");
        }
        rango(v, reglas.path("alertaTarifa").path("toleranciaPorcentaje"), 0, 50, "alertaTarifa.toleranciaPorcentaje");
        rango(v, reglas.path("comision").path("porcentaje"), 0, 30, "comision.porcentaje");
        Double ventana = rango(v, reglas.path("cancelacion").path("ventanaHoras"), 0, 72, "cancelacion.ventanaHoras");
        Double penalizacion = rango(v, reglas.path("cancelacion").path("penalizacionPorcentaje"), 0, 100,
                "cancelacion.penalizacionPorcentaje");
        if (ventana != null && penalizacion != null && penalizacion > 0 && ventana == 0) {
            v.add("cancelacion: una penalización mayor que 0 exige una ventana mayor que 0 horas");
        }
        JsonNode minutos = reglas.path("oferta").path("minutosParaAceptar");
        if (!minutos.isIntegralNumber() || minutos.asLong() < 1 || minutos.asLong() > 1440) {
            v.add("oferta.minutosParaAceptar debe ser un entero entre 1 y 1440");
        }
        return v;
    }

    private static String texto(JsonNode nodo) {
        return nodo.isTextual() ? nodo.asText() : null;
    }

    private static Double rango(List<String> v, JsonNode nodo, double min, double max, String campo) {
        if (!nodo.isNumber() || nodo.asDouble() < min || nodo.asDouble() > max) {
            v.add(campo + " debe ser un número entre " + (long) min + " y " + (long) max);
            return null;
        }
        return nodo.asDouble();
    }
}
