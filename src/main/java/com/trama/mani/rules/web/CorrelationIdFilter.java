package com.trama.mani.rules.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Propaga X-Correlation-ID (lo genera el Gateway o lo reenvía Core/Dispatch) al MDC de los logs JSON
 * y a la respuesta HTTP (SCRUM-1658 / QAS-22). Sin encabezado se usa "none".
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Correlation-ID";
    public static final String MDC_KEY = HEADER;
    public static final String MDC_CORRELATION_ID = "correlationId";
    static final String MISSING = "none";

    private static final Logger log = LoggerFactory.getLogger(CorrelationIdFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(HEADER);
        String correlationId = StringUtils.hasText(header) ? header.trim() : MISSING;

        MDC.put(MDC_KEY, correlationId);
        MDC.put(MDC_CORRELATION_ID, correlationId);
        response.setHeader(HEADER, correlationId);
        try {
            chain.doFilter(request, response);
            log.info("HTTP {} {} status={} correlationId={}",
                    request.getMethod(), request.getRequestURI(), response.getStatus(), correlationId);
        } finally {
            MDC.remove(MDC_KEY);
            MDC.remove(MDC_CORRELATION_ID);
        }
    }

    public static String current() {
        String value = MDC.get(MDC_KEY);
        return value != null ? value : MISSING;
    }
}
