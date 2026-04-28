package com.wesleytaumaturgo.ledgerflow.shared.infrastructure;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

/**
 * Injects a traceId into MDC at the start of every HTTP request.
 * Reads X-Request-ID header if present; generates a short UUID otherwise.
 * MDC is cleared in finally block — required to prevent stale traceId on Virtual Thread reuse.
 *
 * Registered as highest-precedence filter so all downstream log lines carry the traceId.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    private static final String TRACE_HEADER = "X-Request-ID";
    private static final String MDC_TRACE_KEY = "traceId";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain)
            throws ServletException, IOException {

        String traceId = Optional
                .ofNullable(request.getHeader(TRACE_HEADER))
                .filter(h -> !h.isBlank())
                .orElseGet(() ->
                        UUID.randomUUID().toString().replace("-", "").substring(0, 16));

        MDC.put(MDC_TRACE_KEY, traceId);
        response.setHeader(TRACE_HEADER, traceId);

        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_TRACE_KEY);   // REQUIRED — prevents MDC leak on Virtual Thread reuse
        }
    }
}
