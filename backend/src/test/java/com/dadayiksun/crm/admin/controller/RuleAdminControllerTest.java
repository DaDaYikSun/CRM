package com.dadayiksun.crm.admin.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import com.dadayiksun.crm.common.error.ApiException;
import com.dadayiksun.crm.common.error.ErrorCode;
import com.dadayiksun.crm.common.error.GlobalExceptionHandler;
import com.dadayiksun.crm.common.security.JsonSecurityHandlers;
import com.dadayiksun.crm.common.security.SecurityConfig;
import com.dadayiksun.crm.customer.service.RainbowHoldSettingService;
import com.dadayiksun.crm.cycle.domain.RuleSettings.VaccineRuleGroup;
import com.dadayiksun.crm.cycle.domain.RuleSettings.VaccineRuleRow;
import com.dadayiksun.crm.cycle.domain.RuleSettings.VaccineTypeRow;
import com.dadayiksun.crm.cycle.service.CycleRuleService;

import jakarta.servlet.http.Cookie;

/** B-02 · B-03 API (FR-ADM-002 · 003 · 004, 계약 C17). 관리자만 (POL-ACC-002, NFR-SEC-002). */
@WebMvcTest(controllers = RuleAdminController.class)
@Import({SecurityConfig.class, JsonSecurityHandlers.class, GlobalExceptionHandler.class, RoleAccessFilter.class,
        AuthWebConfig.class})
class RuleAdminControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private CycleRuleService cycleRules;

    @MockitoBean
    private RainbowHoldSettingService rainbowHold;

    private MockHttpServletRequestBuilder withCsrf(MockHttpServletRequestBuilder request) throws Exception {
        Cookie token = mvc.perform(get("/api/csrf")).andReturn().getResponse().getCookie("XSRF-TOKEN");
        return request.cookie(token).header("X-XSRF-TOKEN", token.getValue());
    }

    @Test
    void 접종_규칙은_종류마다_규칙_행을_묶어_준다() throws Exception {
        when(cycleRules.vaccineRules()).thenReturn(List.of(new VaccineRuleGroup(
                new VaccineTypeRow(1L, "DHPPL", "종합백신", true, true),
                List.of(new VaccineRuleRow(10L, "BOOSTER", 365, null, 365, null, "매년")))));
        mvc.perform(get("/api/admin/vaccine-rules").session(AuthTestSupport.session(Role.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].vaccineType.isMajor").value(true))
                .andExpect(jsonPath("$.data[0].rules[0].intervalDays").value(365))
                .andExpect(jsonPath("$.data[0].rules[0].ageToDays").doesNotExist());
    }

    @Test
    void 규칙을_저장하면_다시_계산한_아이_수() throws Exception {
        when(cycleRules.saveVaccineRules(any())).thenReturn(118);
        mvc.perform(withCsrf(put("/api/admin/vaccine-rules")).session(AuthTestSupport.session(Role.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[{\"vaccineType\":{\"vaccineTypeId\":1,\"isMajor\":true},"
                                + "\"rules\":[{\"vaccineRuleId\":10,\"ageFromDays\":365,\"intervalDays\":300}]}]"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.recalculatedPetCount").value(118));
    }

    @Test
    void 기준값이_0이면_400_정책_문구() throws Exception {
        doThrow(new ApiException(ErrorCode.RULE_VALUE_INVALID)).when(cycleRules).saveMissThresholdDays(0);
        mvc.perform(withCsrf(put("/api/admin/settings")).session(AuthTestSupport.session(Role.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"missThresholdDays\":0,\"rainbowHoldDays\":30}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("RULE_VALUE_INVALID"))
                .andExpect(jsonPath("$.error.message").value("값이 비었거나 0 이하라 저장하지 못했습니다. 1 이상의 숫자를 입력해 주세요."));
        verify(rainbowHold, never()).save(any());
    }

    @Test
    void 관리자가_아니면_조회도_저장도_403() throws Exception {
        for (Role role : new Role[] {Role.DESK, Role.GROOMER}) {
            mvc.perform(get("/api/admin/settings").session(AuthTestSupport.session(role)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
            mvc.perform(withCsrf(put("/api/admin/coat-types/intervals")).session(AuthTestSupport.session(role))
                            .contentType(MediaType.APPLICATION_JSON).content("[]"))
                    .andExpect(status().isForbidden());
        }
        verify(cycleRules, never()).saveCoatIntervals(any());
    }
}
