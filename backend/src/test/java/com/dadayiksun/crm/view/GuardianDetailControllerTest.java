package com.dadayiksun.crm.view;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.dadayiksun.crm.auth.access.AuthTestSupport;
import com.dadayiksun.crm.auth.domain.Role;
import com.dadayiksun.crm.common.error.ApiException;
import com.dadayiksun.crm.common.error.ErrorCode;
import com.dadayiksun.crm.common.error.GlobalExceptionHandler;
import com.dadayiksun.crm.common.security.JsonSecurityHandlers;
import com.dadayiksun.crm.common.security.SecurityConfig;
import com.dadayiksun.crm.customer.dto.GuardianDetail;
import com.dadayiksun.crm.customer.service.GuardianUpdateService;

import jakarta.servlet.http.Cookie;

/** C10 보호자 상세 · 수정 응답 모양 (API 설계 3-3). 로그인 · 역할은 #143의 AuthTestSupport 세션으로 만든다. */
@WebMvcTest(controllers = GuardianDetailController.class)
@Import({SecurityConfig.class, JsonSecurityHandlers.class, GlobalExceptionHandler.class})
class GuardianDetailControllerTest {

    @Autowired
    private MockMvc mvc;
    @MockitoBean
    private GuardianDetailAssembler assembler;
    @MockitoBean
    private GuardianUpdateService update;

    private GuardianDetailAssembler.Response sample() {
        var g = new GuardianDetail.GuardianInfo(7L, "김보호", "01000000000", null, "PHONE", "OK",
                LocalDate.of(2026, 11, 26), true, true, List.of("SENSITIVE"), null,
                OffsetDateTime.parse("2023-05-20T10:00:00+09:00"), "데스크 직원");
        var p = new GuardianDetailAssembler.PetCard(12L, "보리", "ACTIVE", "말티즈", "장모", LocalDate.of(2023, 5, 2),
                false, null, null);
        return new GuardianDetailAssembler.Response(g, List.of(p));
    }

    @Test
    void 상세는_전체_번호와_같은_집_아이를_준다() throws Exception {
        when(assembler.assemble(7L)).thenReturn(sample());

        mvc.perform(get("/api/guardians/7").session(AuthTestSupport.session(Role.DESK)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.guardian.phone").value("01000000000"))
                .andExpect(jsonPath("$.data.pets[0].name").value("보리"))
                .andExpect(jsonPath("$.data.pets[0].nextSchedule").doesNotExist());
    }

    @Test
    void 없는_보호자는_404_공통_응답() throws Exception {
        when(assembler.assemble(99L)).thenThrow(new ApiException(ErrorCode.NOT_FOUND));

        mvc.perform(get("/api/guardians/99").session(AuthTestSupport.session(Role.DESK)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    @Test
    void 수정은_바뀐_칸만_넘기고_상세를_돌려준다() throws Exception {
        when(assembler.assemble(7L)).thenReturn(sample());
        var csrf = mvc.perform(get("/api/csrf")).andReturn().getResponse().getCookie("XSRF-TOKEN");

        mvc.perform(patch("/api/guardians/7").session(AuthTestSupport.session(Role.DESK)).cookie(csrf).header("X-XSRF-TOKEN", csrf.getValue())
                .contentType(MediaType.APPLICATION_JSON).content("{\"contactTimeNote\":null}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.guardian.guardianId").value(7));

        verify(update).update(eq(7L), any());
    }

    @Test
    void 미용사는_상세와_수정이_403() throws Exception {
        var csrf = mvc.perform(get("/api/csrf")).andReturn().getResponse().getCookie("XSRF-TOKEN");

        mvc.perform(get("/api/guardians/7").session(AuthTestSupport.session(Role.GROOMER)))
                .andExpect(status().isForbidden());
        mvc.perform(patch("/api/guardians/7").session(AuthTestSupport.session(Role.GROOMER)).cookie(csrf)
                .header("X-XSRF-TOKEN", csrf.getValue()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
    }
}
