package com.dadayiksun.crm.dashboard.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.Map;

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
import com.dadayiksun.crm.common.security.JsonSecurityHandlers;
import com.dadayiksun.crm.common.security.SecurityConfig;
import com.dadayiksun.crm.dashboard.dto.MetricsResponse;
import com.dadayiksun.crm.dashboard.repository.DashboardMetricsQuery;
import com.dadayiksun.crm.dashboard.service.DashboardMetricsService;
import com.dadayiksun.crm.dashboard.service.DashboardTrendService;

/**
 * 대시보드 지표는 관리자 · 데스크만 (#157 완료 조건, NFR-SEC-002, POL-ACC-011, T10 · T11), 오류는 공통 응답 (K16 ②).
 */
@WebMvcTest(controllers = DashboardController.class)
@Import({SecurityConfig.class, JsonSecurityHandlers.class, GlobalExceptionHandler.class, RoleAccessFilter.class,
        AuthWebConfig.class, DashboardMetricsService.class, DashboardTrendService.class})
class DashboardAccessTest {

    private static final String FORBIDDEN_MESSAGE = "이 작업을 할 권한이 없습니다. 필요하면 관리자에게 문의해 주세요.";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private DashboardMetricsQuery query;

    @MockitoBean
    private com.dadayiksun.crm.dashboard.repository.DashboardTrendQuery trendQuery;

    @MockitoBean
    private com.dadayiksun.crm.common.time.KstClock clock;

    @Test
    void 미용사가_지표를_부르면_403과_공통_문구() throws Exception {
        mvc.perform(get("/api/dashboard/metrics").session(AuthTestSupport.session(Role.GROOMER)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.error.message").value(FORBIDDEN_MESSAGE));
    }

    @Test
    void 데스크와_관리자는_볼_수_있고_빈_기간은_rate가_null() throws Exception {
        when(clock.today()).thenReturn(LocalDate.of(2026, 11, 27));
        for (Role role : new Role[] {Role.DESK, Role.ADMIN}) {
            mvc.perform(get("/api/dashboard/metrics").session(AuthTestSupport.session(role)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.from").value("2026-11-01"))
                    .andExpect(jsonPath("$.data.to").value("2026-11-27"))
                    .andExpect(jsonPath("$.data.revisitRate.vaccine.rate").doesNotExist())
                    .andExpect(jsonPath("$.data.revisitRate.vaccine.denominator").value(0))
                    .andExpect(jsonPath("$.data.conversionByResult.WRONG_NUMBER.total").value(0));
        }
    }

    @Test
    void 기간_형식이_틀리면_400_공통_응답() throws Exception {
        when(clock.today()).thenReturn(LocalDate.of(2026, 11, 27));
        mvc.perform(get("/api/dashboard/metrics").param("from", "2026-11-30").param("to", "2026-11-01")
                        .session(AuthTestSupport.session(Role.DESK)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
    }

    @Test
    void 로그인하지_않으면_401() throws Exception {
        mvc.perform(get("/api/dashboard/metrics")).andExpect(status().isUnauthorized());
    }

    @Test
    void 추이도_미용사는_403_데스크는_빈_주가_rate_null() throws Exception {
        when(clock.today()).thenReturn(LocalDate.of(2026, 11, 27));
        mvc.perform(get("/api/dashboard/trends").session(AuthTestSupport.session(Role.GROOMER)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/dashboard/trends").session(AuthTestSupport.session(Role.DESK)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.weeks[0].weekStart").value("2026-10-26"))
                .andExpect(jsonPath("$.data.weeks[0].vaccineRevisit.rate").doesNotExist())
                .andExpect(jsonPath("$.data.distribution.grooming.missed").value(0));
    }
}
