package com.dadayiksun.crm.auth.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.dadayiksun.crm.auth.access.AuthWebConfig;
import com.dadayiksun.crm.auth.access.RoleAccessFilter;
import com.dadayiksun.crm.auth.domain.Role;
import com.dadayiksun.crm.auth.domain.Staff;
import com.dadayiksun.crm.auth.service.LoginService;
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
}
