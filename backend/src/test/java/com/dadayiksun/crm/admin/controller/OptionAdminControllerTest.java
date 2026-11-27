package com.dadayiksun.crm.admin.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
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
import com.dadayiksun.crm.common.audit.RuleHistoryQuery;
import com.dadayiksun.crm.common.error.ApiException;
import com.dadayiksun.crm.common.error.ErrorCode;
import com.dadayiksun.crm.common.error.GlobalExceptionHandler;
import com.dadayiksun.crm.common.response.PageResponse;
import com.dadayiksun.crm.common.security.JsonSecurityHandlers;
import com.dadayiksun.crm.common.security.SecurityConfig;
import com.dadayiksun.crm.common.web.PageQuery;
import com.dadayiksun.crm.customer.dto.OptionAdminDtos.BreedItem;
import com.dadayiksun.crm.customer.service.OptionAdminService;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.Cookie;

/** B-03 선택지 · B-06 변경 이력 API (FR-ADM-005 · 006). 관리자만 (POL-ACC-002, NFR-SEC-002). */
@WebMvcTest(controllers = OptionAdminController.class)
@Import({SecurityConfig.class, JsonSecurityHandlers.class, GlobalExceptionHandler.class, RoleAccessFilter.class,
        AuthWebConfig.class})
class OptionAdminControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private OptionAdminService options;

    @MockitoBean
    private RuleHistoryQuery history;

    private MockHttpServletRequestBuilder withCsrf(MockHttpServletRequestBuilder request) throws Exception {
        Cookie token = mvc.perform(get("/api/csrf")).andReturn().getResponse().getCookie("XSRF-TOKEN");
        return request.cookie(token).header("X-XSRF-TOKEN", token.getValue());
    }

    @Test
    void 품종_목록은_숨긴_것도_준다() throws Exception {
        when(options.breeds()).thenReturn(List.of(new BreedItem(1L, "말티즈", 3L, 1, true),
                new BreedItem(2L, "옛 품종", null, 2, false)));
        mvc.perform(get("/api/admin/breeds").session(AuthTestSupport.session(Role.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[1].isActive").value(false))
                .andExpect(jsonPath("$.data[0].defaultCoatTypeId").value(3));
    }

    @Test
    void 같은_이름이면_409_정책_문구() throws Exception {
        when(options.createBreed(any())).thenThrow(new ApiException(ErrorCode.OPTION_DUPLICATE));
        mvc.perform(withCsrf(post("/api/admin/breeds")).session(AuthTestSupport.session(Role.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"말티즈\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("OPTION_DUPLICATE"))
                .andExpect(jsonPath("$.error.message").value("이미 있는 선택지입니다. 다른 이름을 입력해 주세요."));
    }

    @Test
    void 변경_이력은_페이지로_시간은_09_00() throws Exception {
        ObjectMapper json = new ObjectMapper();
        RuleHistoryQuery.Item item = new RuleHistoryQuery.Item(77L, "SETTING", "MISS_THRESHOLD_DAYS", null,
                json.readTree("{\"value\":14}"), json.readTree("{\"value\":21}"), "관리자",
                OffsetDateTime.of(2026, 11, 27, 15, 0, 0, 0, ZoneOffset.ofHours(9)));
        when(history.page(any())).thenReturn(PageResponse.of(List.of(item), new PageQuery(1), 1));
        mvc.perform(get("/api/admin/rule-history").param("page", "1").session(AuthTestSupport.session(Role.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].after.value").value(21))
                .andExpect(jsonPath("$.data.items[0].changedAt").value("2026-11-27T15:00:00+09:00"))
                .andExpect(jsonPath("$.data.total").value(1));
    }

    @Test
    void 관리자가_아니면_403() throws Exception {
        for (Role role : new Role[] {Role.DESK, Role.GROOMER}) {
            mvc.perform(get("/api/admin/rule-history").session(AuthTestSupport.session(role)))
                    .andExpect(status().isForbidden());
            mvc.perform(withCsrf(post("/api/admin/coat-types")).session(AuthTestSupport.session(role))
                            .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"x\",\"defaultGroomWeeks\":8}"))
                    .andExpect(status().isForbidden());
        }
        verify(options, never()).createCoatType(any());
    }
}
