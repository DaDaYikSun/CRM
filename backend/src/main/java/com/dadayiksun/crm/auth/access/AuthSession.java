package com.dadayiksun.crm.auth.access;

import java.util.Optional;

import org.springframework.session.FindByIndexNameSessionRepository;

import com.dadayiksun.crm.auth.domain.CurrentStaff;
import com.dadayiksun.crm.auth.domain.Role;
import com.dadayiksun.crm.auth.domain.Staff;
import com.dadayiksun.crm.common.audit.SessionKeys;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

/**
 * 로그인 세션에 넣고 꺼내는 한 곳 (K2 · K19). 세션에는 직원 ID({@link SessionKeys#STAFF_ID}, 감사 컬럼이 읽음)와 역할만 둔다.
 * 비밀번호 · 토큰은 두지 않는다.
 * 직원 ID는 Spring Session의 주인 이름 칸에도 넣는다. 비활성화 · 역할 변경 · 비밀번호 초기화 때 그 직원의 세션을 모두 지우기 위해서다
 * (POL-ACC-004 · 010, 11/25 회의 R5, #150 {@code StaffSessionTerminator}).
 */
public final class AuthSession {

    public static final String ROLE = "CRM_STAFF_ROLE";
    /** 임시 비밀번호로 로그인해 새 비밀번호를 정해야 하는 동안 true (POL-ACC-010, #150). */
    public static final String MUST_CHANGE_PASSWORD = "CRM_MUST_CHANGE_PASSWORD";

    private AuthSession() {
    }

    /** 로그인 성공. 이미 세션이 있으면 번호를 바꿔 세션 고정 공격을 막는다. */
    public static void start(HttpServletRequest request, Staff staff) {
        if (request.getSession(false) != null) {
            request.changeSessionId();
        }
        HttpSession session = request.getSession(true);
        session.setAttribute(SessionKeys.STAFF_ID, staff.staffId());
        session.setAttribute(ROLE, staff.role().name());
        session.setAttribute(FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME, principalName(staff.staffId()));
        session.setAttribute(MUST_CHANGE_PASSWORD, staff.mustChangePassword());
    }

    /** 세션 주인 이름 = 직원 ID 문자열. 세션을 직원 단위로 찾을 때 쓴다. */
    public static String principalName(Long staffId) {
        return String.valueOf(staffId);
    }

    /** 새 비밀번호를 정해야 하는 세션인가. 값이 없으면 false. */
    public static boolean mustChangePassword(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        return session != null && Boolean.TRUE.equals(session.getAttribute(MUST_CHANGE_PASSWORD));
    }

    /** 새 비밀번호를 정했다: 다른 API를 다시 쓸 수 있다. */
    public static void passwordChanged(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.setAttribute(MUST_CHANGE_PASSWORD, false);
        }
    }

    public static Optional<CurrentStaff> current(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return Optional.empty();
        }
        Object id = session.getAttribute(SessionKeys.STAFF_ID);
        Object role = session.getAttribute(ROLE);
        if (id instanceof Number n && role instanceof String r) {
            return Optional.of(new CurrentStaff(n.longValue(), Role.valueOf(r)));
        }
        return Optional.empty();
    }
}
