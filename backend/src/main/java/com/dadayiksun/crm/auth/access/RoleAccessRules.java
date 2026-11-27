package com.dadayiksun.crm.auth.access;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.util.AntPathMatcher;

import com.dadayiksun.crm.auth.domain.Role;

/**
 * API 설계 2장 '역할별 허용 표'를 서버에 옮긴 한 곳 (NFR-SEC-002, POL-ACC-011, T3 · T10).
 * 위에서부터 처음 맞는 규칙을 쓴다. 아무 규칙에도 안 맞는 /api/** 는 관리자 · 데스크만 허용(미용사 거부)이다.
 * 그래서 아직 없는 API도, 새로 생기는 API도 기본이 미용사 403이다. 미용사에게 열어 줄 때만 아래 목록에 더한다.
 *
 * <p>요청 본문 · 값에 따라 갈리는 검사는 여기서 못 한다. 해당 API가 {@code CurrentStaff.require(...)}로 한다:
 * 방문 {@code serviceType=VACCINE}(미용사 거부, #148), 접종 방문의 수정 이력(#149 · 초안 28).
 * 쿼리 값(타임라인 {@code type}, 수정 이력 {@code targetType})은 여기서 막는다.
 */
public final class RoleAccessRules {

    private static final Set<Role> ALL = Set.of(Role.ADMIN, Role.DESK, Role.GROOMER);
    private static final Set<Role> STAFF_ONLY = Set.of(Role.ADMIN, Role.DESK);
    private static final Set<Role> ADMIN_ONLY = Set.of(Role.ADMIN);

    private static final AntPathMatcher MATCHER = new AntPathMatcher();

    /** method가 null이면 모든 메서드. queryParam이 있으면 그 값 중 하나가 쿼리에 있을 때만 맞는다. */
    private record Rule(String method, String pattern, String queryParam, Set<String> queryValues, Set<Role> roles) {
        boolean matches(String requestMethod, String path, Map<String, String[]> query) {
            if (method != null && !method.equals(requestMethod)) {
                return false;
            }
            if (!MATCHER.match(pattern, path)) {
                return false;
            }
            if (queryParam == null) {
                return true;
            }
            return Arrays.stream(query.getOrDefault(queryParam, new String[0]))
                    .anyMatch(v -> v != null && queryValues.contains(v.trim().toUpperCase()));
        }
    }

    private static Rule any(String pattern, Set<Role> roles) {
        return new Rule(null, pattern, null, Set.of(), roles);
    }

    private static Rule on(String method, String pattern, Set<Role> roles) {
        return new Rule(method, pattern, null, Set.of(), roles);
    }

    private static Rule onQuery(String method, String pattern, String param, Set<String> values, Set<Role> roles) {
        return new Rule(method, pattern, param, values, roles);
    }

    private static final List<Rule> RULES = List.of(
            // 미용사도 되는 것 (표의 '미용사 O')
            any("/api/auth/**", ALL),
            on("GET", "/api/options/**", ALL),
            on("GET", "/api/search/**", ALL),
            on("GET", "/api/pets/{id}", ALL),
            // 타임라인: 접종 · 연락 필터는 미용사 거부 (POL-ACC-011). ALL · GROOMING은 허용
            onQuery("GET", "/api/pets/{id}/timeline", "type", Set.of("VACCINE", "CONTACT"), STAFF_ONLY),
            on("GET", "/api/pets/{id}/timeline", ALL),
            on("POST", "/api/pets/{id}/visits", ALL), // serviceType=VACCINE 거부는 방문 API가 한다
            on("GET", "/api/grooming", ALL),
            on("PATCH", "/api/visits/{id}", ALL), // 본인 미용 기록만은 방문 API가 한다 (POL-DATA-006)
            onQuery("GET", "/api/edit-history", "targetType", Set.of("CONTACT_LOG"), STAFF_ONLY),
            on("GET", "/api/edit-history", ALL), // 접종 방문 이력 거부는 수정 이력 API가 한다
            // 관리자만
            on("POST", "/api/pets/{id}/rainbow-revert", ADMIN_ONLY),
            any("/api/admin/**", ADMIN_ONLY));

    private RoleAccessRules() {
    }

    /** 로그인 없이 부를 수 있는 것: 로그인, CSRF 쿠키 받기. */
    public static boolean isPublic(String method, String path) {
        return ("POST".equals(method) && "/api/auth/login".equals(path))
                || ("GET".equals(method) && "/api/csrf".equals(path));
    }

    /** 이 역할이 이 요청을 해도 되는가. HEAD는 GET과 같게 본다 (스프링이 GET 핸들러로 보냄). */
    public static boolean allows(Role role, String method, String path, Map<String, String[]> query) {
        String m = "HEAD".equals(method) ? "GET" : method;
        return RULES.stream()
                .filter(r -> r.matches(m, path, query))
                .findFirst()
                .map(Rule::roles)
                .orElse(STAFF_ONLY)
                .contains(role);
    }
}
