package com.trama.mani.rules;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * SCRUM-1714: Rules expone sus métricas en formato Prometheus para que Grafana muestre
 * tráfico, errores y latencia medidos dentro del servicio.
 */
// Spring Boot apaga los exportadores de métricas en pruebas; aquí se prueban justamente esos.
@AutoConfigureObservability
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MetricsEndpointTest {

    @Autowired
    private TestRestTemplate rest;

    @Test
    void prometheusEndpointExposesHttpServerRequests() {
        rest.getForEntity("/api/v1/rules/health", String.class);

        ResponseEntity<String> respuesta = rest.getForEntity("/actuator/prometheus", String.class);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(respuesta.getBody())
                .contains("http_server_requests_seconds_count")
                .contains("uri=\"/api/v1/rules/health\"")
                // Histograma activo: permite calcular el p95 en Grafana.
                .contains("http_server_requests_seconds_bucket");
    }

    @Test
    void onlyHealthAndPrometheusAreExposed() {
        assertThat(rest.getForEntity("/actuator/health", String.class).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(rest.getForEntity("/actuator/env", String.class).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(rest.getForEntity("/actuator/beans", String.class).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }
}
