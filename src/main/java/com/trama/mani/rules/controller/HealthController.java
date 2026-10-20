package com.trama.mani.rules.controller;

import com.trama.mani.rules.web.CorrelationIdFilter;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

    @GetMapping({"/health", "/api/v1/rules/health"})
    public Map<String, Object> health() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "UP");
        body.put("service", "MANI-Rules-Java");
        body.put("timestamp", Instant.now().toString());
        body.put("correlationId", CorrelationIdFilter.current());
        return body;
    }
}
