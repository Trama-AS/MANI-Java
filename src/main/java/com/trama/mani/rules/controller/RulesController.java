package com.trama.mani.rules.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;

@RestController
@CrossOrigin(origins = "*")
public class RulesController {

    // 1. Healthcheck
    @GetMapping({"/health", "/api/v1/rules/health"})
    public ResponseEntity<Map<String, Object>> healthCheck(
            @RequestHeader(value = "X-Correlation-ID", required = false, defaultValue = "none") String correlationId) {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "UP");
        response.put("service", "MANI-Rules-Java");
        response.put("timestamp", Instant.now().toString());
        response.put("correlationId", correlationId);
        return ResponseEntity.ok(response);
    }

    // 2. Evaluación de tarifas (RF-16 / RF-22)
    @PostMapping("/api/v1/rules/evaluate-rate")
    public ResponseEntity<Map<String, Object>> evaluateRate(
            @RequestBody(required = false) Map<String, Object> request,
            @RequestHeader(value = "X-Correlation-ID", required = false, defaultValue = "none") String correlationId) {
        
        double basePrice = 35000.0;
        if (request != null && request.containsKey("basePrice")) {
            basePrice = Double.parseDouble(request.get("basePrice").toString());
        }

        boolean isWeekend = false;
        if (request != null && request.containsKey("isWeekend")) {
            isWeekend = Boolean.parseBoolean(request.get("isWeekend").toString());
        }

        double surcharge = isWeekend ? 5000.0 : 0.0;
        double finalPrice = basePrice + surcharge;

        Map<String, Object> response = new HashMap<>();
        response.put("service", "MANI-Rules-Java");
        response.put("correlationId", correlationId);
        response.put("basePrice", basePrice);
        response.put("surcharge", surcharge);
        response.put("finalPrice", finalPrice);
        response.put("ruleApplied", isWeekend ? "REGLA-FIN-DE-SEMANA (+5000)" : "TARIFA-ESTANDAR");
        response.put("status", "APPLIED");

        return ResponseEntity.ok(response);
    }

    // 3. Ranking de aliados para asignación (RF-13)
    @PostMapping("/api/v1/rules/rank-allies")
    public ResponseEntity<Map<String, Object>> rankAllies(
            @RequestBody(required = false) Map<String, Object> request,
            @RequestHeader(value = "X-Correlation-ID", required = false, defaultValue = "none") String correlationId) {
        
        List<Map<String, Object>> mockAllies = new ArrayList<>();
        mockAllies.add(Map.of("id", "aliado-1", "name", "Carolina Gómez", "rating", 4.9, "score", 98));
        mockAllies.add(Map.of("id", "aliado-2", "name", "Paola Morales", "rating", 4.7, "score", 92));
        mockAllies.add(Map.of("id", "aliado-3", "name", "Sandra Rivas", "rating", 4.5, "score", 85));

        Map<String, Object> response = new HashMap<>();
        response.put("correlationId", correlationId);
        response.put("rankedAllies", mockAllies);
        response.put("criteria", "CALIFICACION_Y_EXPERIENCIA");

        return ResponseEntity.ok(response);
    }
}
