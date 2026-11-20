package com.trama.mani.rules.config;

import com.trama.mani.rules.web.ApiException;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.function.Supplier;
import org.springframework.http.HttpStatus;

/**
 * Disyuntor (Circuit Breaker, SCRUM-1663 / QAS-40) compatible con la semántica de Resilience4j
 * para proteger ejecuciones y dependencias del motor de reglas ante fallas repetidas.
 */
public class RulesCircuitBreaker {

    public enum State {
        CLOSED,
        OPEN,
        HALF_OPEN
    }

    private final String name;
    private final int failureThreshold;
    private final Duration waitDurationInOpenState;
    private final Supplier<Instant> clock;

    private State state = State.CLOSED;
    private int consecutiveFailures = 0;
    private Instant openedAt = null;

    public RulesCircuitBreaker(
            String name,
            int failureThreshold,
            Duration waitDurationInOpenState,
            Supplier<Instant> clock) {
        if (failureThreshold < 1) {
            throw new IllegalArgumentException("failureThreshold debe ser >= 1");
        }
        this.name = Objects.requireNonNull(name, "name es requerido");
        this.failureThreshold = failureThreshold;
        this.waitDurationInOpenState = Objects.requireNonNull(waitDurationInOpenState, "waitDurationInOpenState es requerido");
        this.clock = Objects.requireNonNull(clock, "clock es requerido");
    }

    public RulesCircuitBreaker(String name, int failureThreshold, Duration waitDurationInOpenState) {
        this(name, failureThreshold, waitDurationInOpenState, Instant::now);
    }

    public String getName() {
        return name;
    }

    public synchronized State getState() {
        refreshState();
        return state;
    }

    public synchronized int getConsecutiveFailures() {
        return consecutiveFailures;
    }

    public <T> T executeSupplier(Supplier<T> supplier) {
        ensurePermitted();
        try {
            T result = supplier.get();
            onSuccess();
            return result;
        } catch (ApiException ex) {
            if (ex.getStatus().is5xxServerError()) {
                onError();
            }
            throw ex;
        } catch (RuntimeException ex) {
            onError();
            throw ex;
        }
    }

    public synchronized void reset() {
        state = State.CLOSED;
        consecutiveFailures = 0;
        openedAt = null;
    }

    private synchronized void ensurePermitted() {
        refreshState();
        if (state == State.OPEN) {
            throw new ApiException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "RULES_CIRCUIT_OPEN",
                    "Circuito abierto en Rules (" + name + "): fallas consecutivas detectadas");
        }
    }

    private synchronized void onSuccess() {
        consecutiveFailures = 0;
        openedAt = null;
        state = State.CLOSED;
    }

    private synchronized void onError() {
        consecutiveFailures++;
        if (state == State.HALF_OPEN || consecutiveFailures >= failureThreshold) {
            state = State.OPEN;
            openedAt = clock.get();
        }
    }

    private void refreshState() {
        if (state == State.OPEN
                && openedAt != null
                && !Duration.between(openedAt, clock.get()).minus(waitDurationInOpenState).isNegative()) {
            state = State.HALF_OPEN;
        }
    }
}
