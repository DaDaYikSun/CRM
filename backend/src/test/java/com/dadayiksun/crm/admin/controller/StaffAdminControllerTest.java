package com.dadayiksun.crm.admin.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.dadayiksun.crm.auth.access.AuthTestSupport;
import com.dadayiksun.crm.auth.access.AuthWebConfig;
import com.dadayiksun.crm.auth.access.RoleAccessFilter;
import com.dadayiksun.crm.auth.domain.Role;
import com.dadayiksun.crm.auth.domain.StaffAccount;
import com.dadayiksun.crm.auth.service.StaffAccountService;
import com.dadayiksun.crm.common.error.ApiException;
import com.dadayiksun.crm.common.error.ErrorCode;
import com.dadayiksun.crm.common.error.GlobalExceptionHandler;
import com.dadayiksun.crm.common.security.JsonSecurityHandlers;
import com.dadayiksun.crm.common.security.SecurityConfig;

import jakarta.servlet.http.Cookie;

/** B-01 직원 관리 API (FR-ADM-001, 계약 C16). 관리자만 (NFR-SEC-002). */
@WebMvcTest(controllers = StaffAdminController.class)
@Import({SecurityConfig.class, JsonSecurityHandlers.class, GlobalExceptionHandler.class, RoleAccessFilter.class,
        AuthWebConfig.class})
class StaffAdminControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private StaffAccountService service;

    private MockHttpServletRequestBuilder withCsrf(MockHttpServletRequestBuilder request) throws Exception {
        Cookie token = mvc.perform(get("/api/csrf")).andReturn().getResponse().getCookie("XSRF-TOKEN");
        return request.cookie(token).header("X-XSRF-TOKEN", token.getValue());
    }

    @Test
    void 목록은_해시_없이_시간은_09_00으로() throws Exception {
        when(service.list()).thenReturn(List.of(new StaffAccount(1L, "admin", "관리자", Role.ADMIN, true,
                LocalDateTime.of(2026, 11, 27, 9, 1), null)));
        mvc.perform(get("/api/admin/staff").session(AuthTestSupport.session(Role.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].loginId").value("admin"))
                .andExpect(jsonPath("$.data[0].isActive").value(true))
                .andExpect(jsonPath("$.data[0].lastLoginAt").value("2026-11-27T09:01:00+09:00"))
                .andExpect(jsonPath("$.data[0].passwordHash").doesNotExist());
    }

    @Test
    void 추가하면_201과_임시_비밀번호() throws Exception {
        when(service.create("desk02", "새 데스크", Role.DESK)).thenReturn(new StaffAccountService.Created(7L, "abcd2345ef"));
        mvc.perform(withCsrf(post("/api/admin/staff")).session(AuthTestSupport.session(Role.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginId\":\"desk02\",\"name\":\"새 데스크\",\"role\":\"DESK\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.staffId").value(7))
                .andExpect(jsonPath("$.data.tempPassword").value("abcd2345ef"));
    }

    @Test
    void 같은_아이디는_409_정책_문구() throws Exception {
        when(service.create("desk02", "새 데스크", Role.DESK)).thenThrow(new ApiException(ErrorCode.LOGIN_ID_TAKEN));
        mvc.perform(withCsrf(post("/api/admin/staff")).session(AuthTestSupport.session(Role.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginId\":\"desk02\",\"name\":\"새 데스크\",\"role\":\"DESK\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("LOGIN_ID_TAKEN"))
                .andExpect(jsonPath("$.error.message").value("이미 쓰는 아이디입니다. 다른 아이디를 입력해 주세요."));
    }

    @Test
    void 빈_칸과_정해진_값_밖의_역할은_400() throws Exception {
        mvc.perform(withCsrf(post("/api/admin/staff")).session(AuthTestSupport.session(Role.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"loginId\":\"\",\"name\":\"x\",\"role\":\"DESK\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.message").value("아이디을(를) 입력해 주세요."));
        mvc.perform(withCsrf(post("/api/admin/staff")).session(AuthTestSupport.session(Role.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"loginId\":\"a\",\"name\":\"x\",\"role\":\"OWNER\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
    }

    @Test
    void 비활성화는_바꿀_칸만_넘긴다() throws Exception {
        mvc.perform(withCsrf(patch("/api/admin/staff/7")).session(AuthTestSupport.session(Role.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"isActive\":false}"))
                .andExpect(status().isOk());
        verify(service).update(7L, null, false);
    }

    @Test
    void 데스크와_미용사는_403() throws Exception {
        for (Role role : new Role[] {Role.DESK, Role.GROOMER}) {
            mvc.perform(get("/api/admin/staff").session(AuthTestSupport.session(role)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
            mvc.perform(withCsrf(post("/api/admin/staff")).session(AuthTestSupport.session(role))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"loginId\":\"x1\",\"name\":\"x\",\"role\":\"ADMIN\"}"))
                    .andExpect(status().isForbidden());
        }
    }
}
