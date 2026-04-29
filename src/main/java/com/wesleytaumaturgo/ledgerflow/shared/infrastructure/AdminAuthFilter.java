package com.wesleytaumaturgo.ledgerflow.shared.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Security filter that validates the X-Admin-Key header for all /api/v1/admin/** requests.
 *
 * Security rules enforced:
 *   - NEVER logs the value of X-Admin-Key (CLAUDE.md §4.3 security baseline)
 *   - Returns 401 ProblemDetail directly from filter — request never reaches the controller
 *   - Intercepts ONLY /api/v1/admin/** paths (shouldNotFilter for all other paths)
 *   - ADMIN_API_KEY read from environment via @Value — no hardcoded default
 *
 * The filter writes a RFC 7807 ProblemDetail response directly when auth fails,
 * consistent with the GlobalExceptionHandler contract.
 */
@Component
public class AdminAuthFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(AdminAuthFilter.class);
    private static final String ADMIN_PATH_PREFIX = "/api/v1/admin";
    private static final String AUTH_HEADER = "X-Admin-Key";

    @Value("${ADMIN_API_KEY}")
    private String adminApiKey;

    private final ObjectMapper objectMapper;

    public AdminAuthFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Skip this filter for all requests that are NOT under /api/v1/admin.
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(ADMIN_PATH_PREFIX);
    }

    /**
     * Validates X-Admin-Key. If missing or incorrect, writes 401 ProblemDetail directly.
     * NEVER logs the key value — only logs that auth failed (not why or what was provided).
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String providedKey = request.getHeader(AUTH_HEADER);

        if (!keysMatch(providedKey, adminApiKey)) {
            log.warn("Admin auth failed — missing or invalid X-Admin-Key header for {}",
                request.getRequestURI());

            AdminAuthException ex = new AdminAuthException();
            ProblemDetail problem = ProblemDetail.forStatus(ex.httpStatus());
            problem.setTitle("Authentication required");
            problem.setProperty("errorCode", ex.errorCode());
            problem.setDetail(ex.getMessage());

            String traceId = MDC.get("traceId");
            if (traceId != null) {
                problem.setProperty("traceId", traceId);
            }

            response.setStatus(ex.httpStatus());
            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            objectMapper.writeValue(response.getWriter(), problem);
            return;
        }

        filterChain.doFilter(request, response);
    }

    // Constant-time comparison prevents timing side-channel attacks on the admin key.
    static boolean keysMatch(String provided, String expected) {
        if (provided == null) return false;
        return MessageDigest.isEqual(
            provided.getBytes(StandardCharsets.UTF_8),
            expected.getBytes(StandardCharsets.UTF_8));
    }
}
