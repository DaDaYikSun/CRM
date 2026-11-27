package com.dadayiksun.crm.auth.access;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;

import com.dadayiksun.crm.auth.domain.Role;
import com.dadayiksun.crm.auth.domain.Staff;
import com.dadayiksun.crm.common.audit.SessionKeys;

/** 로그인 뒤 8시간 (POL-ACC-005)과 로그아웃 (POL-ACC-008), #167 */
class AuthSessionTest {

    private static final Staff DESK = new Staff(2L, "desk", "x", "데스크", Role.DESK, true, false);
    private static final Instant LOGIN = Instant.parse("2026-11-27T00:00:00Z");

    private static MockHttpServletRequest requestWith(MockHttpSession session) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setSession(session);
        return request;
    }

    private static MockHttpSession loggedInAt(Instant at) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(SessionKeys.STAFF_ID, 2L);
        session.setAttribute(AuthSession.ROLE, "DESK");
        session.setAttribute(AuthSession.LOGIN_AT, at.toEpochMilli());
        return session;
    }

    @Test
    void 로그인하면_로그인_시각을_세션에_둔다() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        long before = Instant.now().toEpochMilli();
        AuthSession.start(request, DESK);
        Object at = request.getSession(false).getAttribute(AuthSession.LOGIN_AT);
        assertThat(at).isInstanceOf(Long.class);
        assertThat((Long) at).isGreaterThanOrEqualTo(before);
        assertThat(AuthSession.current(request)).isPresent();
    }

    @Test
    void 로그인_뒤_8시간_전에는_로그인_상태() {
        MockHttpSession session = loggedInAt(LOGIN);
        assertThat(AuthSession.current(requestWith(session), LOGIN.plusSeconds(8 * 3600 - 1)))
                .hasValueSatisfying(s -> assertThat(s.role()).isEqualTo(Role.DESK));
        assertThat(session.isInvalid()).isFalse();
    }

    @Test
    void 로그인_뒤_8시간이_되면_세션을_지우고_비어_있다() {
        MockHttpSession session = loggedInAt(LOGIN);
        assertThat(AuthSession.current(requestWith(session), LOGIN.plusSeconds(8 * 3600))).isEmpty();
        assertThat(session.isInvalid()).isTrue();
    }

    @Test
    void 로그인_시각이_없는_세션은_지난_것으로_본다() {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(SessionKeys.STAFF_ID, 2L);
        session.setAttribute(AuthSession.ROLE, "DESK");
        assertThat(AuthSession.current(requestWith(session), LOGIN)).isEmpty();
        assertThat(session.isInvalid()).isTrue();
    }

    @Test
    void 로그아웃은_세션을_지우고_세션이_없어도_된다() {
        MockHttpSession session = loggedInAt(Instant.now());
        AuthSession.end(requestWith(session));
        assertThat(session.isInvalid()).isTrue();

        AuthSession.end(new MockHttpServletRequest()); // 예외 없음
    }

    @Test
    void 로그인하면_비밀번호_해시_대신_표식을_두고_새_비밀번호를_정하면_바꾼다() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        AuthSession.start(request, DESK);
        Object stamp = request.getSession(false).getAttribute(AuthSession.PASSWORD_STAMP);
        assertThat(stamp).isInstanceOf(String.class).isNotEqualTo("x");
        assertThat(AuthSession.passwordStampMatches(request, "x")).isTrue();
        assertThat(AuthSession.passwordStampMatches(request, "y")).isFalse();

        AuthSession.passwordChanged(request, "y");
        assertThat(AuthSession.passwordStampMatches(request, "y")).isTrue();
        assertThat(AuthSession.passwordStampMatches(request, "x")).isFalse();
    }

    @Test
    void 표식이_없는_세션은_비밀번호가_다르다고_본다() {
        // 표식 기능 전에 만든 세션: 배포 때 한 번 다시 로그인 (#183 리뷰)
        assertThat(AuthSession.passwordStampMatches(requestWith(loggedInAt(Instant.now())), "x")).isFalse();
    }
}
