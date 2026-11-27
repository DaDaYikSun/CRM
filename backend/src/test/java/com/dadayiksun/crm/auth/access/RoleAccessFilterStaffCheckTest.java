package com.dadayiksun.crm.auth.access;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;

import com.dadayiksun.crm.auth.domain.Role;
import com.dadayiksun.crm.auth.domain.Staff;
import com.dadayiksun.crm.auth.repository.StaffRepository;
import com.dadayiksun.crm.common.security.JsonSecurityHandlers;
import com.fasterxml.jackson.databind.ObjectMapper;

/** 요청마다 직원 행을 다시 읽는다 (POL-ACC-004 · POL-ACC-010, #150 리뷰) */
class RoleAccessFilterStaffCheckTest {

    private final StaffRepository staffs = mock(StaffRepository.class);
    private RoleAccessFilter filter;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        ObjectMapper om = new ObjectMapper();
        ObjectProvider<StaffRepository> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(staffs);
        filter = new RoleAccessFilter(new JsonSecurityHandlers(om), om, provider);
    }

    private static Staff row(Role role, boolean active, boolean mustChange) {
        return new Staff(2L, "desk", "x", "데스크", role, active, mustChange);
    }

    private MockHttpServletResponse call(MockHttpSession session, String method, String path) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setSession(session);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }

    @Test
    void 활성이고_역할이_같으면_통과() throws Exception {
        when(staffs.findById(2L)).thenReturn(Optional.of(row(Role.DESK, true, false)));
        assertThat(call(AuthTestSupport.session(2L, Role.DESK), "GET", "/api/targets").getStatus()).isEqualTo(200);
    }

    @Test
    void 비활성이_된_직원의_남은_세션은_지우고_401() throws Exception {
        when(staffs.findById(2L)).thenReturn(Optional.of(row(Role.DESK, false, false)));
        MockHttpSession session = AuthTestSupport.session(2L, Role.DESK);
        MockHttpServletResponse response = call(session, "GET", "/api/targets");
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("SESSION_EXPIRED");
        assertThat(session.isInvalid()).isTrue();
    }

    @Test
    void 역할이_바뀐_뒤_옛_역할_세션은_지우고_401() throws Exception {
        when(staffs.findById(2L)).thenReturn(Optional.of(row(Role.GROOMER, true, false)));
        MockHttpSession session = AuthTestSupport.session(2L, Role.DESK);
        assertThat(call(session, "GET", "/api/targets").getStatus()).isEqualTo(401);
        assertThat(session.isInvalid()).isTrue();
    }

    @Test
    void 없어진_직원은_401() throws Exception {
        when(staffs.findById(2L)).thenReturn(Optional.empty());
        assertThat(call(AuthTestSupport.session(2L, Role.DESK), "GET", "/api/targets").getStatus()).isEqualTo(401);
    }

    @Test
    void 새_비밀번호는_직원_행으로_본다_다른_세션도_바로_풀린다() throws Exception {
        MockHttpSession session = AuthTestSupport.session(2L, Role.DESK);
        session.setAttribute(AuthSession.MUST_CHANGE_PASSWORD, true); // 다른 세션에서 이미 바꿈
        when(staffs.findById(2L)).thenReturn(Optional.of(row(Role.DESK, true, false)));
        assertThat(call(session, "GET", "/api/targets").getStatus()).isEqualTo(200);

        when(staffs.findById(2L)).thenReturn(Optional.of(row(Role.DESK, true, true)));
        MockHttpServletResponse blocked = call(AuthTestSupport.session(2L, Role.DESK), "GET", "/api/targets");
        assertThat(blocked.getStatus()).isEqualTo(403);
        assertThat(blocked.getContentAsString()).contains("PASSWORD_CHANGE_REQUIRED");
    }

    @Test
    void 로그인_뒤_비밀번호가_바뀌면_남은_세션은_지우고_401() throws Exception {
        // 비밀번호 확인(옛 해시)과 세션 저장 사이에 초기화가 끼어들어 세션 지우기가 놓친 세션 (#183 리뷰)
        MockHttpServletRequest login = new MockHttpServletRequest();
        AuthSession.start(login, new Staff(2L, "desk", "old-hash", "데스크", Role.DESK, true, false));
        MockHttpSession session = (MockHttpSession) login.getSession(false);

        when(staffs.findById(2L)).thenReturn(Optional.of(new Staff(2L, "desk", "old-hash", "데스크", Role.DESK, true, false)));
        assertThat(call(session, "GET", "/api/targets").getStatus()).isEqualTo(200);

        when(staffs.findById(2L)).thenReturn(Optional.of(new Staff(2L, "desk", "new-hash", "데스크", Role.DESK, true, true)));
        MockHttpServletResponse response = call(session, "POST", "/api/auth/password");
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("SESSION_EXPIRED");
        assertThat(session.isInvalid()).isTrue();
    }
}
