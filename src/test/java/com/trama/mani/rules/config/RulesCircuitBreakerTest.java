package com.trama.mani.rules.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.trama.mani.rules.web.ApiException;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class RulesCircuitBreakerTest {

    @Test
    void opensAfterReachingFailureThresholdAndRecoversViaHalfOpen() {
        AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-11-16T20:00:00Z"));
        RulesCircuitBreaker cb = new RulesCircuitBreaker(
                "rules-test", 3, Duration.ofSeconds(10), now::get);

        assertEquals("rules-test", cb.getName());
        assertEquals(RulesCircuitBreaker.State.CLOSED, cb.getState());

        // Los errores 4xx de validación no abren el circuito
        assertThrows(ApiException.class, () -> cb.executeSupplier(() -> {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "invalido");
        }));
        assertEquals(0, cb.getConsecutiveFailures());

        // Tres fallas 5xx/runtime abren el circuito
        assertThrows(ApiException.class, () -> cb.executeSupplier(() -> {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "UPSTREAM_503", "caido");
        }));
        for (int i = 0; i < 2; i++) {
            assertThrows(IllegalStateException.class, () -> cb.executeSupplier(() -> {
                throw new IllegalStateException("fallo interno");
            }));
        }

        assertEquals(RulesCircuitBreaker.State.OPEN, cb.getState());
        assertEquals(3, cb.getConsecutiveFailures());

        // Con el circuito abierto rechaza de inmediato con 503 RULES_CIRCUIT_OPEN
        ApiException openEx = assertThrows(ApiException.class, () -> cb.executeSupplier(() -> "ok"));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, openEx.getStatus());
        assertEquals("RULES_CIRCUIT_OPEN", openEx.getCode());

        // Avanza el reloj -> HALF_OPEN; si falla vuelve a OPEN
        now.set(now.get().plusSeconds(10));
        assertEquals(RulesCircuitBreaker.State.HALF_OPEN, cb.getState());
        assertThrows(RuntimeException.class, () -> cb.executeSupplier(() -> {
            throw new RuntimeException("sigue caido");
        }));
        assertEquals(RulesCircuitBreaker.State.OPEN, cb.getState());

        // Avanza el reloj -> HALF_OPEN; ahora tiene éxito -> CLOSED
        now.set(now.get().plusSeconds(10));
        String ok = cb.executeSupplier(() -> "recuperado");
        assertEquals("recuperado", ok);
        assertEquals(RulesCircuitBreaker.State.CLOSED, cb.getState());
        assertEquals(0, cb.getConsecutiveFailures());

        cb.reset();
        assertEquals(RulesCircuitBreaker.State.CLOSED, cb.getState());
    }

    @Test
    void validatesConstructorArgumentsAndDefaultClock() {
        assertThrows(IllegalArgumentException.class,
                () -> new RulesCircuitBreaker("x", 0, Duration.ofSeconds(1)));
        RulesCircuitBreaker cb = new RulesCircuitBreaker("default-clock", 2, Duration.ofSeconds(5));
        assertEquals("ok", cb.executeSupplier(() -> "ok"));
    }
}
