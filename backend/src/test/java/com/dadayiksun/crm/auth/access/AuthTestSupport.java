package com.dadayiksun.crm.auth.access;

import org.springframework.mock.web.MockHttpSession;

import com.dadayiksun.crm.auth.domain.Role;
import com.dadayiksun.crm.common.audit.SessionKeys;

/**
 * 다른 이슈의 Controller 테스트용: 로그인한 직원의 세션을 만든다.
 * 역할 검사 필터(#143)가 모든 /api/** 요청에 로그인을 요구하므로 {@code mvc.perform(get(...).session(AuthTestSupport.session(Role.DESK)))}로 쓴다.
 */
public final class AuthTestSupport {

    private AuthTestSupport() {
    }

    public static MockHttpSession session(Role role) {
        return session(1L, role);
    }

    public static MockHttpSession session(long staffId, Role role) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(SessionKeys.STAFF_ID, staffId);
        session.setAttribute(AuthSession.ROLE, role.name());
        return session;
    }
}
