package com.dadayiksun.crm.common.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import jakarta.servlet.http.Cookie;

import com.dadayiksun.crm.auth.access.AuthTestSupport;
import com.dadayiksun.crm.auth.domain.Role;
import com.dadayiksun.crm.common.error.GlobalExceptionHandler;

/** CSRF 토큰이 없는 저장 요청은 거부된다 (K10). 없는 API는 C1 오류 형식 (K16 ②). */
@WebMvcTest(controllers = CsrfController.class)
@Import({SecurityConfig.class, JsonSecurityHandlers.class, GlobalExceptionHandler.class})
class SecurityConfigTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void CSRF_토큰_없는_저장_요청은_403() throws Exception {
        mvc.perform(post("/api/anything"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.error.message").value("이 작업을 할 권한이 없습니다. 필요하면 관리자에게 문의해 주세요."));
    }

    /**
     * 화면과 같은 흐름: GET /api/csrf로 쿠키를 받고, 그 값을 헤더 X-XSRF-TOKEN에 실어 저장 요청을 보낸다.
     * spring-security-test의 csrf()는 쓰지 않는다. csrf()는 공유 CsrfFilter의 토큰 저장소를 세션 저장소로 바꿔
     * 같은 컨텍스트의 다른 테스트(쿠키 확인)를 깨뜨린다.
     */
    @Test
    void CSRF_토큰이_있으면_통과하고_없는_API는_NOT_FOUND() throws Exception {
        Cookie token = mvc.perform(get("/api/csrf")).andReturn().getResponse().getCookie("XSRF-TOKEN");
        // #143: 모든 /api/**에 로그인이 필요해 직원 세션을 함께 보낸다
        mvc.perform(post("/api/anything").cookie(token).header("X-XSRF-TOKEN", token.getValue())
                        .session(AuthTestSupport.session(Role.ADMIN)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    @Test
    void api_csrf는_XSRF_TOKEN_쿠키를_준다() throws Exception {
        mvc.perform(get("/api/csrf"))
                .andExpect(status().isOk())
                .andExpect(cookie().exists("XSRF-TOKEN"))
                .andExpect(jsonPath("$.data.headerName").value("X-XSRF-TOKEN"));
    }
}
