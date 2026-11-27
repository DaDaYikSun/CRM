package com.dadayiksun.crm.customer.controller;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.dadayiksun.crm.auth.access.AuthTestSupport;
import com.dadayiksun.crm.auth.domain.Role;
import com.dadayiksun.crm.common.error.GlobalExceptionHandler;
import com.dadayiksun.crm.common.security.JsonSecurityHandlers;
import com.dadayiksun.crm.common.security.SecurityConfig;
import com.dadayiksun.crm.customer.dto.RainbowResponse;
import com.dadayiksun.crm.customer.service.PetProfileService;
import com.dadayiksun.crm.customer.service.RainbowMarkService;
import com.dadayiksun.crm.customer.service.RainbowRevertService;

import jakarta.servlet.http.Cookie;

/** C21 무지개다리 표시의 역할 검사와 응답 모양 (API 설계 2장 · 3-3, POL-ACC-011). 미용사는 서버가 403으로 막는다. */
@WebMvcTest(controllers = PetProfileController.class)
@Import({SecurityConfig.class, JsonSecurityHandlers.class, GlobalExceptionHandler.class})
class PetRainbowControllerTest {

    @Autowired
    private MockMvc mvc;
    @MockitoBean
    private PetProfileService profile;
    @MockitoBean
    private RainbowRevertService revert;
    @MockitoBean
    private RainbowMarkService mark;

    private Cookie csrf;

    @BeforeEach
    void setUp() throws Exception {
        csrf = mvc.perform(get("/api/csrf")).andReturn().getResponse().getCookie("XSRF-TOKEN");
    }

    private ResultActions rainbow(MockHttpSession session) throws Exception {
        return mvc.perform(post("/api/pets/5/rainbow").session(session).cookie(csrf)
                .header("X-XSRF-TOKEN", csrf.getValue()));
    }

    @Test
    void 데스크와_관리자는_표시할_수_있다() throws Exception {
        when(mark.mark(5L)).thenReturn(new RainbowResponse(LocalDate.of(2026, 12, 24), List.of(13L)));

        for (Role role : new Role[] {Role.DESK, Role.ADMIN}) {
            rainbow(AuthTestSupport.session(role))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.holdUntil").value("2026-12-24"))
                    .andExpect(jsonPath("$.data.heldPetIds[0]").value(13));
        }
    }

    @Test
    void 미용사는_403이고_서비스를_부르지_않는다() throws Exception {
        rainbow(AuthTestSupport.session(Role.GROOMER)).andExpect(status().isForbidden());

        verify(mark, never()).mark(5L);
    }

    @Test
    void 되돌리기는_관리자만() throws Exception {
        for (Role role : new Role[] {Role.DESK, Role.GROOMER}) {
            mvc.perform(post("/api/pets/5/rainbow-revert").session(AuthTestSupport.session(role)).cookie(csrf)
                    .header("X-XSRF-TOKEN", csrf.getValue())).andExpect(status().isForbidden());
        }
    }
}
