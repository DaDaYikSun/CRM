package com.dadayiksun.crm.auth.access;

import java.util.Optional;

import com.dadayiksun.crm.auth.domain.CurrentStaff;
import com.dadayiksun.crm.auth.domain.Role;
import com.dadayiksun.crm.auth.domain.Staff;
import com.dadayiksun.crm.common.audit.SessionKeys;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

/**
 * 로그인 세션에 넣고 꺼내는 한 곳 (K2 · K19). 세션에는 직원 ID({@link SessionKeys#STAFF_ID}, 감사 컬럼이 읽음)와 역할만 둔다.
 * 비밀번호 · 토큰은 두지 않는다.
 */
public final class AuthSession {

    public static final String ROLE = "CRM_STAFF_ROLE";

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
