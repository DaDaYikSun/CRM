package com.dadayiksun.crm.customer.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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
import com.dadayiksun.crm.customer.dto.AddPetResponse;
import com.dadayiksun.crm.customer.dto.PhoneCheckResponse;
import com.dadayiksun.crm.customer.dto.RegisterGuardianResponse;
import com.dadayiksun.crm.customer.service.PetRegistrationService;
import com.dadayiksun.crm.customer.service.PhoneCheckService;

import jakarta.servlet.http.Cookie;

/**
 * C6 번호 확인 · 등록 API (API 설계 3-3, 1-2 · 1-5).
 * 로그인한 데스크 직원 세션은 #143의 AuthTestSupport로 만든다 (역할 검사 필터가 모든 /api/**에 로그인을 요구한다).
 */
@WebMvcTest(controllers = GuardianRegistrationController.class)
@Import({SecurityConfig.class, JsonSecurityHandlers.class, GlobalExceptionHandler.class})
class GuardianRegistrationControllerTest {

    /** 시드 데스크 직원 ID 대신 (C3 Fake). */
    private static final long SEED_STAFF_ID = 2L;

    private static final String VALID_BODY = """
            {
              "guardian": { "name": "김보호", "phone": "010-0000-0000", "privacyAgreed": true, "smsAgreed": false },
              "pet": { "name": "보리", "birthDate": "2023-05-02", "approxAge": null, "breedId": 1, "coatTypeId": 3 }
            }
            """;

    @Autowired
    private MockMvc mvc;
    @MockitoBean
    private PhoneCheckService phoneCheck;
    @MockitoBean
    private PetRegistrationService registration;

    private MockHttpSession session;
    private Cookie csrf;

    @BeforeEach
    void setUp() throws Exception {
        session = AuthTestSupport.session(SEED_STAFF_ID, Role.DESK);
        csrf = mvc.perform(get("/api/csrf")).andReturn().getResponse().getCookie("XSRF-TOKEN");
    }

    private ResultActions save(String uri, String body, String idempotencyKey) throws Exception {
        var request = post(uri).session(session).cookie(csrf).header("X-XSRF-TOKEN", csrf.getValue())
                .contentType(MediaType.APPLICATION_JSON).content(body);
        if (idempotencyKey != null) {
            request.header(IdempotencyFilter.HEADER, idempotencyKey);
        }
        return mvc.perform(request);
    }

    @Test
    void 등록_성공은_201과_guardianId_petId() throws Exception {
        when(registration.registerGuardian(any())).thenReturn(new RegisterGuardianResponse(7L, 12L));

        save("/api/guardians", VALID_BODY, "reg-1")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.guardianId").value(7))
                .andExpect(jsonPath("$.data.petId").value(12));
    }

    @Test
    void 같은_등록_요청을_두_번_보내도_한_번만_저장하고_같은_응답() throws Exception {
        when(registration.registerGuardian(any()))
                .thenReturn(new RegisterGuardianResponse(7L, 12L), new RegisterGuardianResponse(8L, 13L));

        String first = save("/api/guardians", VALID_BODY, "reg-twice").andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        save("/api/guardians", VALID_BODY, "reg-twice")
                .andExpect(status().isCreated())
                .andExpect(content().string(first));

        verify(registration, times(1)).registerGuardian(any());
    }

    @Test
    void 기존_보호자에_아이_추가를_두_번_보내도_한_번만_저장() throws Exception {
        when(registration.addPet(eq(7L), any())).thenReturn(new AddPetResponse(13L));
        String body = """
                { "pet": { "name": "콩이", "approxAge": { "months": 3 }, "breedId": 1, "coatTypeId": 3 } }
                """;

        save("/api/guardians/7/pets", body, "add-twice").andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.petId").value(13));
        save("/api/guardians/7/pets", body, "add-twice").andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.petId").value(13));

        verify(registration, times(1)).addPet(eq(7L), any());
    }

    @ParameterizedTest(name = "{0} 빠짐")
    @CsvSource(delimiter = '|', value = {
        "guardian.name  | 보호자 이름     | \"name\": \"김보호\",",
        "guardian.phone | 보호자 전화번호 | \"phone\": \"010-0000-0000\",",
        "pet.name       | 아이 이름       | \"name\": \"보리\",",
        "pet.breedId    | 품종            | \"breedId\": 1,",
        "pet.coatTypeId | 털 유형         | , \"coatTypeId\": 3",
    })
    void 필수_칸이_비면_400_VALIDATION_FAILED와_fields(String field, String label, String removed) throws Exception {
        String body = VALID_BODY.replaceFirst(java.util.regex.Pattern.quote(removed), "");

        save("/api/guardians", body, null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.error.message").value(label + "을(를) 입력해 주세요."))
                .andExpect(jsonPath("$.error.fields[0].field").value(field))
                .andExpect(jsonPath("$.error.fields[0].message").value(label + "을(를) 입력해 주세요."));
        verify(registration, never()).registerGuardian(any());
    }

    @Test
    void 보호자_이름이_빈칸이면_400() throws Exception {
        save("/api/guardians", VALID_BODY.replace("\"김보호\"", "\"  \""), null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields[0].field").value("guardian.name"))
                .andExpect(jsonPath("$.error.message").value("보호자 이름을(를) 입력해 주세요."));
    }

    @Test
    void 입력_오류는_기억하지_않아_고쳐서_같은_키로_다시_보내면_저장한다() throws Exception {
        when(registration.registerGuardian(any())).thenReturn(new RegisterGuardianResponse(7L, 12L));

        save("/api/guardians", VALID_BODY.replace("\"보리\"", "\"\""), "reg-fix").andExpect(status().isBadRequest());
        save("/api/guardians", VALID_BODY, "reg-fix").andExpect(status().isCreated());

        verify(registration, times(1)).registerGuardian(any());
    }

    @Test
    void 번호_확인은_같은_번호_보호자_배열을_준다() throws Exception {
        when(phoneCheck.check("010-0000-0000")).thenReturn(
                PhoneCheckResponse.of(List.of(new PhoneCheckResponse.Match(7L, "김보호", 2))));

        mvc.perform(get("/api/guardians/phone-check").param("phone", "010-0000-0000").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.exists").value(true))
                .andExpect(jsonPath("$.data.guardians[0].guardianId").value(7))
                .andExpect(jsonPath("$.data.guardians[0].name").value("김보호"))
                .andExpect(jsonPath("$.data.guardians[0].petCount").value(2));
    }

    @Test
    void 번호_없이_번호_확인을_부르면_400() throws Exception {
        mvc.perform(get("/api/guardians/phone-check").session(session))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
    }
}
