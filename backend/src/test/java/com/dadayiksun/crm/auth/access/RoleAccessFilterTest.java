package com.dadayiksun.crm.auth.access;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import com.dadayiksun.crm.auth.domain.Role;
import com.dadayiksun.crm.common.error.GlobalExceptionHandler;
import com.dadayiksun.crm.common.security.JsonSecurityHandlers;
import com.dadayiksun.crm.common.security.SecurityConfig;

import jakarta.servlet.http.Cookie;

/**
 * 서버가 같은 요청을 직접 받아도 거부한다 (NFR-SEC-002). 연락 · 보호자 · 관리자 주소는 다른 이슈의 Controller 자리에
 * 가짜 Controller를 두고, 코드 수정 없이 보호되는지 본다.
 */
@WebMvcTest(controllers = ProbeController.class)
@Import({SecurityConfig.class, JsonSecurityHandlers.class, GlobalExceptionHandler.class, RoleAccessFilter.class,
        AuthWebConfig.class, ProbeController.class})
class RoleAccessFilterTest {

    private static final String FORBIDDEN_MESSAGE = "이 작업을 할 권한이 없습니다. 필요하면 관리자에게 문의해 주세요.";

    @Autowired
    private MockMvc mvc;

    @Test
    void 로그인_없이_API를_부르면_401() throws Exception {
        mvc.perform(get("/api/pets/1"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("SESSION_EXPIRED"))
                .andExpect(jsonPath("$.error.message").value("다시 로그인해 주세요."));
    }

    @Test
    void 미용사는_보호자_전체_번호_API가_403() throws Exception {
        mvc.perform(get("/api/guardians/1").session(AuthTestSupport.session(Role.GROOMER)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.error.message").value(FORBIDDEN_MESSAGE));
    }

    @Test
    void 미용사는_아이_상세는_볼_수_있다() throws Exception {
        mvc.perform(get("/api/pets/1").session(AuthTestSupport.session(Role.GROOMER)))
                .andExpect(status().isOk());
    }

    @Test
    void 미용사가_연락_기록_저장을_직접_보내도_403() throws Exception {
        Cookie token = mvc.perform(get("/api/csrf")).andReturn().getResponse().getCookie("XSRF-TOKEN");
        mvc.perform(post("/api/contacts").cookie(token).header("X-XSRF-TOKEN", token.getValue())
                        .session(AuthTestSupport.session(Role.GROOMER)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    void 데스크는_보호자_상세와_연락_기록_저장이_된다() throws Exception {
        mvc.perform(get("/api/guardians/1").session(AuthTestSupport.session(Role.DESK))).andExpect(status().isOk());
        Cookie token = mvc.perform(get("/api/csrf")).andReturn().getResponse().getCookie("XSRF-TOKEN");
        mvc.perform(post("/api/contacts").cookie(token).header("X-XSRF-TOKEN", token.getValue())
                        .session(AuthTestSupport.session(Role.DESK)))
                .andExpect(status().isOk());
    }

    @Test
    void 데스크는_관리자_주소가_403_관리자는_된다() throws Exception {
        mvc.perform(get("/api/admin/ping").session(AuthTestSupport.session(Role.DESK)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.message").value(FORBIDDEN_MESSAGE));
        mvc.perform(get("/api/admin/ping").session(AuthTestSupport.session(Role.ADMIN))).andExpect(status().isOk());
    }

    @Test
    void 인코딩한_주소로_관리자_규칙을_피할_수_없다() throws Exception {
        mvc.perform(get("/api/%61dmin/ping").session(AuthTestSupport.session(Role.DESK)))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void AdminOnly_표시만_있는_주소도_관리자가_아니면_403() throws Exception {
        mvc.perform(get("/api/only-admin-mark").session(AuthTestSupport.session(Role.DESK)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/only-admin-mark").session(AuthTestSupport.session(Role.ADMIN)))
                .andExpect(status().isOk());
    }

    @Test
    void CurrentStaff를_Controller_인자로_받는다() throws Exception {
        mvc.perform(get("/api/who").session(AuthTestSupport.session(42L, Role.ADMIN)))
                .andExpect(jsonPath("$.data").value(42));
    }

    @Test
    void CSRF_토큰_없는_저장_요청은_로그인했어도_403() throws Exception {
        mvc.perform(post("/api/contacts").session(AuthTestSupport.session(Role.DESK)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }
}
