package com.trama.mani.rules.controller;

import com.trama.mani.rules.web.CorrelationIdFilter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints de tarifa (RF-16/RF-22) y ranking (RF-13) aún simulados; se implementan en sus
 * propias historias. El Gateway recorta "/api/v1/rules" antes de reenviar (nginx.conf), así que
 * cada ruta responde con y sin ese prefijo; Core la llama completa por la red interna.
 * CORS lo resuelve únicamente el Gateway.
 */
@RestController
@RequestMapping({"/api/v1/rules", ""})
public class RulesController {

    @PostMapping("/evaluate-rate")
    public ResponseEntity<Map<String, Object>> evaluateRate(@RequestBody(required = false) Map<String, Object> request) {
        double basePrice = 35000.0;
        if (request != null && request.containsKey("basePrice")) {
            basePrice = Double.parseDouble(request.get("basePrice").toString());
        }

        boolean isWeekend = false;
        if (request != null && request.containsKey("isWeekend")) {
            isWeekend = Boolean.parseBoolean(request.get("isWeekend").toString());
        }

        double surcharge = isWeekend ? 5000.0 : 0.0;

        Map<String, Object> response = new HashMap<>();
        response.put("service", "MANI-Rules-Java");
        response.put("correlationId", CorrelationIdFilter.current());
        response.put("basePrice", basePrice);
        response.put("surcharge", surcharge);
        response.put("finalPrice", basePrice + surcharge);
        response.put("ruleApplied", isWeekend ? "REGLA-FIN-DE-SEMANA (+5000)" : "TARIFA-ESTANDAR");
        response.put("status", "APPLIED");
        return ResponseEntity.ok(response);
    }

    @PostMapping("/rank-allies")
    public ResponseEntity<Map<String, Object>> rankAllies(@RequestBody(required = false) Map<String, Object> request) {
        List<Map<String, Object>> mockAllies = new ArrayList<>();
        mockAllies.add(Map.of("id", "aliado-1", "name", "Carolina Gómez", "rating", 4.9, "score", 98));
        mockAllies.add(Map.of("id", "aliado-2", "name", "Paola Morales", "rating", 4.7, "score", 92));
        mockAllies.add(Map.of("id", "aliado-3", "name", "Sandra Rivas", "rating", 4.5, "score", 85));

        Map<String, Object> response = new HashMap<>();
        response.put("correlationId", CorrelationIdFilter.current());
        response.put("rankedAllies", mockAllies);
        response.put("criteria", "CALIFICACION_Y_EXPERIENCIA");
        return ResponseEntity.ok(response);
    }
}
