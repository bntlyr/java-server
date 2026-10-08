package com.castem.javaserver.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestLoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);

    public static final String REQUEST_ID_HEADER = "X-Request-Id";
    public static final String MDC_TRACE_ID_KEY = "traceId";

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        // Skip noisy actuator health polling from verbose logs if desired, or keep light logging
        return false;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        long startTime = System.currentTimeMillis();

        String traceId = request.getHeader(REQUEST_ID_HEADER);
        if (traceId == null || traceId.isBlank()) {
            traceId = UUID.randomUUID().toString().substring(0, 8);
        }

        // Add traceId to MDC and response header
        MDC.put(MDC_TRACE_ID_KEY, "[" + traceId + "] ");
        response.setHeader(REQUEST_ID_HEADER, traceId);

        String method = request.getMethod();
        String uri = request.getRequestURI();
        String queryString = request.getQueryString();
        String fullPath = queryString != null ? uri + "?" + queryString : uri;
        String clientIp = getClientIp(request);

        // Don't log health check at INFO level if it floods logs, log as DEBUG
        boolean isActuator = uri.startsWith("/actuator");
        if (isActuator) {
            log.debug("--> {} {} from {}", method, fullPath, clientIp);
        } else {
            log.info("--> {} {} from {}", method, fullPath, clientIp);
        }

        try {
            filterChain.doFilter(request, response);
        } catch (Exception ex) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("<-- {} {} ERROR after {}ms: {}", method, fullPath, duration, ex.getMessage(), ex);
            throw ex;
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            int status = response.getStatus();

            if (isActuator) {
                log.debug("<-- {} {} {} ({}ms)", method, fullPath, status, duration);
            } else if (status >= 500) {
                log.error("<-- {} {} {} ({}ms)", method, fullPath, status, duration);
            } else if (status >= 400) {
                log.warn("<-- {} {} {} ({}ms)", method, fullPath, status, duration);
            } else {
                log.info("<-- {} {} {} ({}ms)", method, fullPath, status, duration);
            }

            MDC.remove(MDC_TRACE_ID_KEY);
        }
    }

    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
