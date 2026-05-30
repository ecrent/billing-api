package com.ecren.billing.web;

import com.ecren.billing.common.TenantContext;
import com.ecren.billing.repository.TenantRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@Component
public class TenantContextFilter implements Filter {

    private static final List<String> EXCLUDED_PREFIXES = List.of(
            "/api/v1/tenants",
            "/api/v1/plans/",
            "/swagger-ui/",
            "/api-docs/"
    );

    private final TenantRepository tenantRepository;
    private final ObjectMapper objectMapper;

    public TenantContextFilter(TenantRepository tenantRepository, ObjectMapper objectMapper) {
        this.tenantRepository = tenantRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) req;
        HttpServletResponse response = (HttpServletResponse) res;

        if (isExcluded(request)) {
            chain.doFilter(req, res);
            return;
        }

        String header = request.getHeader("X-Tenant-ID");
        if (header == null || header.isBlank()) {
            writeProblem(response, HttpStatus.BAD_REQUEST, "X-Tenant-ID header is required");
            return;
        }

        UUID tenantId;
        try {
            tenantId = UUID.fromString(header);
        } catch (IllegalArgumentException e) {
            writeProblem(response, HttpStatus.BAD_REQUEST, "X-Tenant-ID is not a valid UUID");
            return;
        }

        if (!tenantRepository.existsById(tenantId)) {
            writeProblem(response, HttpStatus.NOT_FOUND, "Tenant not found: " + tenantId);
            return;
        }

        TenantContext.set(tenantId);
        try {
            chain.doFilter(req, res);
        } finally {
            TenantContext.clear();
        }
    }

    private boolean isExcluded(HttpServletRequest request) {
        String method = request.getMethod();
        String path = request.getRequestURI();

        // POST /api/v1/tenants is excluded; other methods on /api/v1/tenants/* are not
        if ("POST".equalsIgnoreCase(method) && "/api/v1/tenants".equals(path)) {
            return true;
        }
        // prefix-based exclusions (GET /api/v1/plans/**, /swagger-ui/**, /api-docs/**)
        return path.equals("/api/v1/plans")
                || path.startsWith("/api/v1/plans/")
                || path.startsWith("/swagger-ui/")
                || path.startsWith("/api-docs/");
    }

    private void writeProblem(HttpServletResponse response, HttpStatus status, String detail)
            throws IOException {
        ProblemDetail problem = ProblemDetail.forStatus(status);
        problem.setDetail(detail);
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), problem);
    }
}
