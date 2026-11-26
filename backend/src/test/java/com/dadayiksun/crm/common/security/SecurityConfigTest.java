package com.dadayiksun.crm.common.security;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
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

    @Test
    void CSRF_토큰이_있으면_통과하고_없는_API는_NOT_FOUND() throws Exception {
        mvc.perform(post("/api/anything").with(csrf()))
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
