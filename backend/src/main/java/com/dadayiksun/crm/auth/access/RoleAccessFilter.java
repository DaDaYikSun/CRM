package com.dadayiksun.crm.auth.access;

import java.io.IOException;

import org.springframework.core.annotation.Order;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.UrlPathHelper;

import com.dadayiksun.crm.auth.domain.CurrentStaff;
import com.dadayiksun.crm.common.security.JsonSecurityHandlers;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 모든 /api/** 요청의 로그인 · 역할 검사 (NFR-SEC-002). 규칙은 {@link RoleAccessRules} 한 곳.
 * 로그인이 없으면 401 SESSION_EXPIRED, 역할이 안 맞으면 403 FORBIDDEN (C1 형식, JsonSecurityHandlers).
 * 순서 -50: 보안 필터(-100, CSRF 검사)와 세션 필터 뒤, 저장 두 번 막기 필터 앞.
 * 컨트롤러가 아직 없는 주소도 같은 규칙으로 막힌다.
 */
@Component
@Order(-50)
public class RoleAccessFilter extends OncePerRequestFilter {

    private final JsonSecurityHandlers handlers;

    public RoleAccessFilter(JsonSecurityHandlers handlers) {
        this.handlers = handlers;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        // 디코딩한 경로로 본다. 인코딩한 주소(%61dmin)로 규칙을 피하지 못하게 한다.
        String path = UrlPathHelper.defaultInstance.getPathWithinApplication(request);
        String method = request.getMethod();
        if (!(path.equals("/api") || path.startsWith("/api/")) || RoleAccessRules.isPublic(method, path)) {
            chain.doFilter(request, response);
            return;
        }
        CurrentStaff staff = AuthSession.current(request).orElse(null);
        if (staff == null) {
            handlers.commence(request, response, null);
        } else if (!RoleAccessRules.allows(staff.role(), method, path, request.getParameterMap())) {
            handlers.handle(request, response, new AccessDeniedException("role"));
        } else {
            chain.doFilter(request, response);
        }
    }
}
