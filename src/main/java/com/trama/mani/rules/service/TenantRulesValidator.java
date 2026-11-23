package com.trama.mani.rules.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Política de las reglas operativas propias de un tenant (SCRUM-1366 / HU-M01-03). Core guarda las reglas y antes de
 * versionarlas pregunta aquí si son coherentes; Rules no guarda nada. Función pura: las mismas reglas con la misma
 * versión de política dan siempre el mismo resultado.
 *
 * <p>Es la única copia de esta política (SCRUM-1722 / QAS-21): Core ya no valida por su cuenta y guarda las reglas
 * normalizadas que devuelve {@link #evaluar}.
 *
 * <p>Límites: orden de listado CALIFICACION o COBERTURA (los criterios de /rank-allies); tolerancia de la alerta de
 * tarifa de 0 a 50 %; comisión de 0 a 30 % con máximo 2 decimales (la liquidación guarda 2); ventana de cancelación de
 * 0 a 72 horas y penalización de 0 a 100 %, con penalización mayor que 0 solo si hay ventana; tiempo para aceptar la
 * oferta de 1 a 1440 minutos (entero).
 */
@Service
public class TenantRulesValidator {

    /** Comisión máxima del tenant, en porcentaje. */
    public static final int COMISION_MAXIMA = 30;

    private final String version;

    public TenantRulesValidator(@Value("${mani.rules.tenant.version:tenant-rules-v1}") String version) {
        this.version = version;
    }

    public String version() {
        return version;
    }

    /**
     * Resultado de evaluar unas reglas: las violaciones y, si no hay ninguna, las reglas normalizadas (criterio en
     * mayúsculas y sin espacios, y solo los campos de la política).
     */
    public record Resultado(List<String> violaciones, ObjectNode normalizadas) {
        public boolean valido() {
            return violaciones.isEmpty();
        }
    }

    public Resultado evaluar(JsonNode reglas) {
        List<String> v = new ArrayList<>();
        if (reglas == null || !reglas.isObject()) {
            v.add("rules debe ser un objeto");
            return new Resultado(v, null);
        }
        String criterio = criterio(reglas.path("ordenListado").path("criterio")).map(Enum::name).orElse(null);
        if (criterio == null) {
            v.add("ordenListado.criterio debe ser CALIFICACION o COBERTURA");
        }
        JsonNode tolerancia = rango(v, reglas.path("alertaTarifa").path("toleranciaPorcentaje"), 0, 50,
                "alertaTarifa.toleranciaPorcentaje");
        JsonNode comision = comision(v, reglas.path("comision").path("porcentaje"));
        JsonNode ventana = rango(v, reglas.path("cancelacion").path("ventanaHoras"), 0, 72, "cancelacion.ventanaHoras");
        JsonNode penalizacion = rango(v, reglas.path("cancelacion").path("penalizacionPorcentaje"), 0, 100,
                "cancelacion.penalizacionPorcentaje");
        if (penalizacionSinVentana(ventana, penalizacion)) {
            v.add("cancelacion: una penalización mayor que 0 exige una ventana mayor que 0 horas");
        }
        JsonNode minutos = reglas.path("oferta").path("minutosParaAceptar");
        if (!minutosValidos(minutos)) {
            v.add("oferta.minutosParaAceptar debe ser un entero entre 1 y 1440");
        }
        if (!v.isEmpty()) {
            return new Resultado(v, null);
        }
        return new Resultado(v, normalizadas(criterio, tolerancia, comision, ventana, penalizacion, minutos.asLong()));
    }

    private static Optional<CriterioRanking> criterio(JsonNode nodo) {
        return nodo.isTextual() ? CriterioRanking.parse(nodo.asText()) : Optional.empty();
    }

    private static boolean penalizacionSinVentana(JsonNode ventana, JsonNode penalizacion) {
        return ventana != null && penalizacion != null && penalizacion.asDouble() > 0 && ventana.asDouble() == 0;
    }

    private static boolean minutosValidos(JsonNode minutos) {
        return minutos.isIntegralNumber() && minutos.asLong() >= 1 && minutos.asLong() <= 1440;
    }

    /** Solo los campos de la política, con el criterio ya en mayúsculas: es lo que Core guarda. */
    private static ObjectNode normalizadas(String criterio, JsonNode tolerancia, JsonNode comision, JsonNode ventana,
            JsonNode penalizacion, long minutos) {
        ObjectNode n = JsonNodeFactory.instance.objectNode();
        n.putObject("ordenListado").put("criterio", criterio);
        n.putObject("alertaTarifa").set("toleranciaPorcentaje", tolerancia);
        n.putObject("comision").set("porcentaje", comision);
        ObjectNode cancelacion = n.putObject("cancelacion");
        cancelacion.set("ventanaHoras", ventana);
        cancelacion.set("penalizacionPorcentaje", penalizacion);
        n.putObject("oferta").put("minutosParaAceptar", minutos);
        return n;
    }

    private static JsonNode rango(List<String> v, JsonNode nodo, double min, double max, String campo) {
        if (!nodo.isNumber() || nodo.asDouble() < min || nodo.asDouble() > max) {
            v.add(campo + " debe ser un número entre " + (long) min + " y " + (long) max);
            return null;
        }
        return nodo;
    }

    /** Comisión de 0 a 30 % con máximo 2 decimales: la columna de la liquidación guarda 2. */
    private static JsonNode comision(List<String> v, JsonNode nodo) {
        boolean valida = nodo.isNumber()
                && nodo.asDouble() >= 0
                && nodo.asDouble() <= COMISION_MAXIMA
                && nodo.decimalValue().stripTrailingZeros().scale() <= 2;
        if (!valida) {
            v.add("comision.porcentaje debe ser un número entre 0 y " + COMISION_MAXIMA + ", con máximo 2 decimales");
            return null;
        }
        return nodo;
    }
}
