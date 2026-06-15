package com.ecren.billing.web;

import com.ecren.billing.common.TenantContext;
import com.ecren.billing.security.JwtPrincipal;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class TenantContextFilter implements Filter {

    private final ObjectMapper objectMapper;

    public TenantContextFilter(ObjectMapper objectMapper) {
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

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof JwtPrincipal principal)) {
            writeProblem(response, HttpStatus.UNAUTHORIZED, "Authentication required");
            return;
        }

        // ADMIN role doesn't need a tenant context — their endpoints don't scope by tenant
        if ("ADMIN".equals(principal.role())) {
            chain.doFilter(req, res);
            return;
        }

        TenantContext.set(principal.tenantId());
        try {
            chain.doFilter(req, res);
        } finally {
            TenantContext.clear();
        }
    }

    private boolean isExcluded(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/api/v1/auth/")
                || path.equals("/api/v1/plans")
                || path.startsWith("/api/v1/plans/")
                || path.equals("/swagger-ui.html")
                || path.startsWith("/swagger-ui/")
                || path.equals("/api-docs")
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
