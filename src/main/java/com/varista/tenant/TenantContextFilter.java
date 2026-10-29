package com.varista.tenant;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

public class TenantContextFilter extends OncePerRequestFilter {
    private static final String TENANT_HEADER = "X-Company-ID";

    private final TenantRoutingDataSource dataSource;

    public TenantContextFilter(TenantRoutingDataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        Long tenantId;
        if (isPublicTenantEndpoint(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        if (isTenantSelectionEndpoint(request)) {
            tenantId = parseTenantId(request.getHeader(TENANT_HEADER));
            if (tenantId == null) {
                response.sendError(HttpStatus.BAD_REQUEST.value(), TENANT_HEADER + " header is required");
                return;
            }
        } else {
            Authentication authentication = org.springframework.security.core.context.SecurityContextHolder
                    .getContext().getAuthentication();
            if (!(authentication instanceof JwtAuthenticationToken jwtAuthentication)) {
                filterChain.doFilter(request, response);
                return;
            }
            tenantId = parseTenantId(jwtAuthentication.getToken().getClaimAsString("company_id"));
            if (tenantId == null) {
                response.sendError(HttpStatus.UNAUTHORIZED.value(), "Token has no valid company_id claim");
                return;
            }
        }

        if (!dataSource.containsTenant(tenantId)) {
            response.sendError(HttpStatus.UNAUTHORIZED.value(), "Invalid company");
            return;
        }

        TenantContext.setTenantId(tenantId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }

    private boolean isPublicTenantEndpoint(HttpServletRequest request) {
        return "/tenant/all".equals(request.getServletPath());
    }

    private boolean isTenantSelectionEndpoint(HttpServletRequest request) {
        String path = request.getServletPath();
        return "/user/login".equals(path)
                || "/user/register".equals(path)
                || "/user/verify-otp".equals(path)
                || path.startsWith("/user/resetPassword/");
    }

    private Long parseTenantId(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            long tenantId = Long.parseLong(value);
            return tenantId > 0 ? tenantId : null;
        } catch (NumberFormatException exception) {
            return null;
        }
    }
}
