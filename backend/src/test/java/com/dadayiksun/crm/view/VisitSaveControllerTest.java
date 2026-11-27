package com.dadayiksun.crm.view;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.dadayiksun.crm.auth.access.AuthTestSupport;
import com.dadayiksun.crm.auth.domain.Role;
import com.dadayiksun.crm.common.error.GlobalExceptionHandler;
import com.dadayiksun.crm.common.security.JsonSecurityHandlers;
import com.dadayiksun.crm.common.security.SecurityConfig;
import com.dadayiksun.crm.common.web.IdempotencyFilter;

import jakarta.servlet.http.Cookie;

/** {@code POST /api/pets/{petId}/visits} (API 설계 3-5): 201 응답 모양, 두 번 저장 막기(K15), 로그인 없음 401 */
@WebMvcTest(controllers = VisitSaveController.class)
@Import({SecurityConfig.class, JsonSecurityHandlers.class, GlobalExceptionHandler.class})
class VisitSaveControllerTest {

    private static final String BODY = """
            { "serviceType": "VACCINE", "vaccineTypeId": 1, "visitDate": "2026-11-24", "memo": null }
            """;

    @Autowired
    private MockMvc mvc;
    @MockitoBean
    private VisitSaveAssembler assembler;

    private Cookie csrf;

    @BeforeEach
    void setUp() throws Exception {
        csrf = mvc.perform(get("/api/csrf")).andReturn().getResponse().getCookie("XSRF-TOKEN");
    }

    private ResultActions save(MockHttpSession session, String key) throws Exception {
        var req = post("/api/pets/12/visits").cookie(csrf).header("X-XSRF-TOKEN", csrf.getValue())
                .header(IdempotencyFilter.HEADER, key).contentType(MediaType.APPLICATION_JSON).content(BODY);
        return mvc.perform(session == null ? req : req.session(session));
    }

    @Test
    void 저장은_201이고_같은_요청을_두_번_보내도_한_번만_저장한다() throws Exception {
        MockHttpSession desk = AuthTestSupport.session(5L, Role.DESK);
        when(assembler.record(eq(12L), any(), any())).thenReturn(new VisitSaveAssembler.Response(501, 101L,
                new VisitSaveAssembler.NextSchedule(140, "VACCINE", 1L, LocalDate.of(2027, 11, 24)), null));

        save(desk, "same-key").andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.visitId").value(501))
                .andExpect(jsonPath("$.data.completedScheduleId").value(101))
                .andExpect(jsonPath("$.data.nextSchedule.dueDate").value("2027-11-24"));
        save(desk, "same-key").andExpect(status().isCreated());

        verify(assembler, times(1)).record(eq(12L), any(), any());
    }

    @Test
    void 로그인하지_않으면_401() throws Exception {
        save(null, "k1").andExpect(status().isUnauthorized());
        verify(assembler, never()).record(eq(12L), any(), any());
    }
}
