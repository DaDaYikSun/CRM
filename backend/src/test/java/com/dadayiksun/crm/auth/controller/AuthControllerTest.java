package com.dadayiksun.crm.auth.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.dadayiksun.crm.auth.access.AuthSession;
import com.dadayiksun.crm.auth.access.AuthWebConfig;
import com.dadayiksun.crm.auth.access.RoleAccessFilter;
import com.dadayiksun.crm.auth.domain.Role;
import com.dadayiksun.crm.auth.domain.Staff;
import com.dadayiksun.crm.auth.service.LoginService;
import com.dadayiksun.crm.auth.service.PasswordService;
import com.dadayiksun.crm.common.audit.SessionKeys;
import com.dadayiksun.crm.common.error.ApiException;
import com.dadayiksun.crm.common.error.ErrorCode;
import com.dadayiksun.crm.common.error.GlobalExceptionHandler;
import com.dadayiksun.crm.common.security.JsonSecurityHandlers;
import com.dadayiksun.crm.common.security.SecurityConfig;

import jakarta.servlet.http.Cookie;

@WebMvcTest(controllers = AuthController.class)
@Import({SecurityConfig.class, JsonSecurityHandlers.class, GlobalExceptionHandler.class, RoleAccessFilter.class,
        AuthWebConfig.class})
class AuthControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private LoginService loginService;

    @MockitoBean
    private PasswordService passwordService;

    private static final Staff GROOMER = new Staff(3L, "groomer", "x", "미용사", Role.GROOMER, true, false);

    /** 화면과 같은 흐름: GET /api/csrf로 쿠키를 받고 헤더에 싣는다 */
    private MockHttpServletRequestBuilder withCsrf(MockHttpServletRequestBuilder request) throws Exception {
        Cookie token = mvc.perform(get("/api/csrf")).andReturn().getResponse().getCookie("XSRF-TOKEN");
        return request.cookie(token).header("X-XSRF-TOKEN", token.getValue());
    }

    private MockHttpServletRequestBuilder login(String body) throws Exception {
        return withCsrf(post("/api/auth/login")).contentType(MediaType.APPLICATION_JSON).content(body);
    }

    @Test
    void 로그인하면_직원_정보를_주고_세션으로_me를_부를_수_있다() throws Exception {
        when(loginService.login("groomer", "pw")).thenReturn(GROOMER);
        when(loginService.findActive(3L)).thenReturn(GROOMER);

        MockHttpSession session = (MockHttpSession) mvc.perform(login("{\"loginId\":\"groomer\",\"password\":\"pw\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.staffId").value(3))
                .andExpect(jsonPath("$.data.name").value("미용사"))
                .andExpect(jsonPath("$.data.role").value("GROOMER"))
                .andExpect(jsonPath("$.data.mustChangePassword").value(false))
                .andReturn().getRequest().getSession();

        mvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("GROOMER"));
    }

    @Test
    void 비밀번호가_틀리면_401_정책_문구() throws Exception {
        when(loginService.login("desk", "bad")).thenThrow(new ApiException(ErrorCode.LOGIN_FAILED));
        mvc.perform(login("{\"loginId\":\"desk\",\"password\":\"bad\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("LOGIN_FAILED"))
                .andExpect(jsonPath("$.error.message").value("아이디 또는 비밀번호가 맞지 않습니다."));
    }

    @Test
    void 빈_아이디_비밀번호는_400() throws Exception {
        mvc.perform(login("{\"loginId\":\"\",\"password\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
    }

    @Test
    void CSRF_토큰_없는_로그인은_403() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginId\":\"desk\",\"password\":\"pw\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    void 로그인_없이_me를_부르면_401() throws Exception {
        mvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("SESSION_EXPIRED"));
    }

    // ---- 임시 비밀번호 → 새 비밀번호 (#150, POL-ACC-010, API 설계 1-6) ----

    private static final Staff TEMP_DESK = new Staff(5L, "desk02", "x", "새 데스크", Role.DESK, true, true);

    private MockHttpSession loginWithTemporaryPassword() throws Exception {
        when(loginService.login("desk02", "Temp1234")).thenReturn(TEMP_DESK);
        return (MockHttpSession) mvc.perform(login("{\"loginId\":\"desk02\",\"password\":\"Temp1234\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mustChangePassword").value(true))
                .andReturn().getRequest().getSession();
    }

    @Test
    void 새_비밀번호를_정하기_전에는_다른_API가_403_PASSWORD_CHANGE_REQUIRED() throws Exception {
        MockHttpSession session = loginWithTemporaryPassword();
        mvc.perform(get("/api/search").session(session))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("PASSWORD_CHANGE_REQUIRED"))
                .andExpect(jsonPath("$.error.message").value("임시 비밀번호로 로그인했습니다. 새 비밀번호를 정해 주세요."));
        when(loginService.findActive(5L)).thenReturn(TEMP_DESK);
        mvc.perform(get("/api/auth/me").session(session)) // 새로고침해도 새 비밀번호 화면을 지킨다
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mustChangePassword").value(true));
    }

    @Test
    void 새_비밀번호를_정하면_다른_API를_다시_쓸_수_있다() throws Exception {
        MockHttpSession session = loginWithTemporaryPassword();
        mvc.perform(withCsrf(post("/api/auth/password")).session(session)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"newPassword\":\"newPass99\"}"))
                .andExpect(status().isOk());
        verify(passwordService).change(eq(5L), eq("newPass99"), any());
        mvc.perform(get("/api/search").session(session)) // 막히지 않고 지나감 (이 시험에는 검색 Controller가 없어 404)
                .andExpect(status().isNotFound());
    }

    @Test
    void 조건에_안_맞는_새_비밀번호는_400_PASSWORD_RULE() throws Exception {
        MockHttpSession session = loginWithTemporaryPassword();
        doThrow(new ApiException(ErrorCode.PASSWORD_RULE)).when(passwordService).change(eq(5L), eq("short1"), any());
        mvc.perform(withCsrf(post("/api/auth/password")).session(session)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"newPassword\":\"short1\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("PASSWORD_RULE"))
                .andExpect(jsonPath("$.error.message").value("비밀번호는 8자 이상, 영문과 숫자를 섞어 주세요."));
    }

    @Test
    void 로그아웃하면_세션이_지워지고_me는_401() throws Exception {
        when(loginService.login("groomer", "pw")).thenReturn(GROOMER);
        MockHttpSession session = (MockHttpSession) mvc.perform(login("{\"loginId\":\"groomer\",\"password\":\"pw\"}"))
                .andReturn().getRequest().getSession();

        mvc.perform(withCsrf(post("/api/auth/logout")).session(session))
                .andExpect(status().isOk());
        assertThat(session.isInvalid()).isTrue();

        mvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("SESSION_EXPIRED"));
    }

    @Test
    void 로그인이_없어도_로그아웃은_200() throws Exception {
        mvc.perform(withCsrf(post("/api/auth/logout")))
                .andExpect(status().isOk());
    }

    @Test
    void CSRF_토큰_없는_로그아웃은_403() throws Exception {
        mvc.perform(post("/api/auth/logout"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    void 로그인_뒤_8시간이_지난_세션은_401_다시_로그인() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(SessionKeys.STAFF_ID, 3L);
        session.setAttribute(AuthSession.ROLE, "GROOMER");
        session.setAttribute(AuthSession.LOGIN_AT,
                Instant.now().minus(AuthSession.MAX_AGE).minusSeconds(1).toEpochMilli());

        mvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("SESSION_EXPIRED"))
                .andExpect(jsonPath("$.error.message").value("다시 로그인해 주세요."));
        assertThat(session.isInvalid()).isTrue();
    }
}
