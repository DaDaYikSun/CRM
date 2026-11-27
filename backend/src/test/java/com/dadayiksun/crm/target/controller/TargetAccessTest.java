package com.dadayiksun.crm.target.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.dadayiksun.crm.auth.access.AuthTestSupport;
import com.dadayiksun.crm.auth.access.AuthWebConfig;
import com.dadayiksun.crm.auth.access.RoleAccessFilter;
import com.dadayiksun.crm.auth.domain.Role;
import com.dadayiksun.crm.common.error.GlobalExceptionHandler;
import com.dadayiksun.crm.common.response.PageResponse;
import com.dadayiksun.crm.common.security.JsonSecurityHandlers;
import com.dadayiksun.crm.common.security.SecurityConfig;
import com.dadayiksun.crm.common.web.PageQuery;
import com.dadayiksun.crm.target.dto.TargetSummaryResponse;
import com.dadayiksun.crm.target.service.TargetQueryService;

/**
 * 연락 대상 목록 · 요약은 관리자 · 데스크만 (#152 완료 조건, NFR-SEC-002, POL-ACC-011, T10 · T11).
 * 역할 검사(#143 RoleAccessFilter)의 기본 규칙(목록에 없는 /api/**는 미용사 거부)이 진짜 연락 대상 주소에 걸리는지 본다.
 */
@WebMvcTest(controllers = TargetController.class)
@Import({SecurityConfig.class, JsonSecurityHandlers.class, GlobalExceptionHandler.class, RoleAccessFilter.class,
        AuthWebConfig.class})
class TargetAccessTest {

    private static final String FORBIDDEN_MESSAGE = "이 작업을 할 권한이 없습니다. 필요하면 관리자에게 문의해 주세요.";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private TargetQueryService service;

    @Test
    void 미용사가_연락_대상_목록을_부르면_403과_공통_문구() throws Exception {
        mvc.perform(get("/api/targets").param("tab", "today").session(AuthTestSupport.session(Role.GROOMER)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.error.message").value(FORBIDDEN_MESSAGE));
        mvc.perform(get("/api/targets/summary").session(AuthTestSupport.session(Role.GROOMER)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    void 데스크와_관리자는_볼_수_있다() throws Exception {
        when(service.list(any(), any())).thenReturn(PageResponse.of(List.of(), PageQuery.parse("1"), 0));
        when(service.summary()).thenReturn(new TargetSummaryResponse(
                new TargetSummaryResponse.Count(0, 0), new TargetSummaryResponse.Count(0, 0), 0));
        for (Role role : new Role[] {Role.DESK, Role.ADMIN}) {
            mvc.perform(get("/api/targets").session(AuthTestSupport.session(role))).andExpect(status().isOk());
            mvc.perform(get("/api/targets/summary").session(AuthTestSupport.session(role))).andExpect(status().isOk());
        }
    }

    @Test
    void 로그인하지_않으면_401() throws Exception {
        mvc.perform(get("/api/targets")).andExpect(status().isUnauthorized());
    }
}
