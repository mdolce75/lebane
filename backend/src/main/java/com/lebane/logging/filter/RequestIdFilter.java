package com.lebane.logging.filter;

import static net.logstash.logback.argument.StructuredArguments.kv;
import static net.logstash.logback.argument.StructuredArguments.v;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.lebane.logging.RequestContext;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Correlation ID + access log estructurado.
 *
 * <ul>
 *   <li>Lee {@code X-Request-Id}; si falta o es inválido genera uno nuevo.</li>
 *   <li>Lo agrega a la respuesta, al MDC (todos los logs del request lo incluyen) y como atributo del request.</li>
 *   <li>Al finalizar registra una línea de access log con method, path, status, durationMs, remoteAddress y
 *       responseSize como campos estructurados. Nunca registra headers, cookies, query strings ni bodies.</li>
 *   <li>Limpia el MDC siempre (finally), aun ante excepciones.</li>
 * </ul>
 *
 * Orden: después del filtro de observación de Micrometer (HIGHEST_PRECEDENCE + 1), de modo que traceId/spanId
 * ya estén en el MDC cuando se escribe el access log, y antes de Spring Security (-100), de modo que las
 * respuestas 401/403 también lleven requestId.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RequestIdFilter extends OncePerRequestFilter {

    static final String ACCESS_LOGGER_NAME = "com.lebane.access";
    private static final Logger ACCESS_LOG = LoggerFactory.getLogger(ACCESS_LOGGER_NAME);
    private static final int MAX_LOGGED_PATH_LENGTH = 512;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String requestId = RequestContext.resolve(request.getHeader(RequestContext.REQUEST_ID_HEADER));
        request.setAttribute(RequestContext.REQUEST_ID_ATTRIBUTE, requestId);
        response.setHeader(RequestContext.REQUEST_ID_HEADER, requestId);
        MDC.put(RequestContext.REQUEST_ID_MDC_KEY, requestId);

        long start = System.nanoTime();
        try {
            chain.doFilter(request, response);
        } finally {
            long durationMs = (System.nanoTime() - start) / 1_000_000;
            logAccess(request, response, durationMs);
            MDC.remove(RequestContext.REQUEST_ID_MDC_KEY);
        }
    }

    private void logAccess(HttpServletRequest request, HttpServletResponse response, long durationMs) {
        String path = truncate(request.getRequestURI());
        int status = response.getStatus();
        Object[] args = {
                v("method", request.getMethod()),
                v("path", path),
                v("status", status),
                v("durationMs", durationMs),
                kv("remoteAddress", request.getRemoteAddr()),
                kv("responseSize", responseSize(response))
        };
        String message = "HTTP {} {} -> {} in {} ms";
        if (isProbe(path)) {
            ACCESS_LOG.debug(message, args);
        } else if (status >= 500) {
            ACCESS_LOG.warn(message, args);
        } else {
            ACCESS_LOG.info(message, args);
        }
    }

    private static Long responseSize(HttpServletResponse response) {
        String contentLength = response.getHeader(HttpHeaders.CONTENT_LENGTH);
        if (contentLength == null) {
            return null;
        }
        try {
            return Long.parseLong(contentLength);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Healthchecks y scraping se registran en DEBUG para no inundar los logs. */
    private static boolean isProbe(String path) {
        return path.startsWith("/actuator/health") || path.startsWith("/actuator/prometheus");
    }

    private static String truncate(String value) {
        if (value == null) {
            return "";
        }
        return value.length() <= MAX_LOGGED_PATH_LENGTH ? value : value.substring(0, MAX_LOGGED_PATH_LENGTH);
    }
}
