package com.dadayiksun.crm.auth.access;

import java.time.Duration;
import java.time.Instant;
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
 * 로그인 시각도 둔다: 세션 저장소의 만료는 '마지막 요청 뒤'라서, '로그인 뒤 8시간'(POL-ACC-005)은 요청마다 여기서 따로 본다 (API 설계 1-6, #167).
 */
public final class AuthSession {

    public static final String ROLE = "CRM_STAFF_ROLE";
    /** 로그인한 시각 (epoch 밀리초) */
    public static final String LOGIN_AT = "CRM_LOGIN_AT";
    /** 로그인 뒤 이만큼 지나면 요청이 있어도 늘리지 않고 끝낸다 (POL-ACC-005) */
    public static final Duration MAX_AGE = Duration.ofHours(8);

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
        session.setAttribute(LOGIN_AT, Instant.now().toEpochMilli());
    }

    /** 로그인한 직원. 로그인이 없거나 로그인 뒤 8시간이 지났으면 비어 있다 (지난 세션은 이때 지운다). */
    public static Optional<CurrentStaff> current(HttpServletRequest request) {
        return current(request, Instant.now());
    }

    static Optional<CurrentStaff> current(HttpServletRequest request, Instant now) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return Optional.empty();
        }
        Object id = session.getAttribute(SessionKeys.STAFF_ID);
        Object role = session.getAttribute(ROLE);
        if (!(id instanceof Number n && role instanceof String r)) {
            return Optional.empty();
        }
        // 로그인 시각이 없는 세션(#167 전에 만든 세션)도 지난 것으로 본다
        if (!(session.getAttribute(LOGIN_AT) instanceof Number loginAt)
                || !now.isBefore(Instant.ofEpochMilli(loginAt.longValue()).plus(MAX_AGE))) {
            session.invalidate();
            return Optional.empty();
        }
        return Optional.of(new CurrentStaff(n.longValue(), Role.valueOf(r)));
    }

    /** 로그아웃 (POL-ACC-008). 세션이 없어도 그대로 끝난다. */
    public static void end(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
    }
}
