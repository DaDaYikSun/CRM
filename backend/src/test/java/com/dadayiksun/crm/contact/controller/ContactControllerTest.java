package com.dadayiksun.crm.contact.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

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
import com.dadayiksun.crm.contact.dto.ContactPanelResponse;
import com.dadayiksun.crm.contact.service.ContactFollowUpService;
import com.dadayiksun.crm.contact.service.ContactSmsDispatcher;
import com.dadayiksun.crm.contact.sms.SmsSender;
import com.dadayiksun.crm.contact.service.ContactPanelService;
import com.dadayiksun.crm.contact.service.ContactSaveService;

import jakarta.servlet.http.Cookie;

/** 주소 · 응답 모양 (API 설계 3-6), 미용사 403 (T3 · T10), 저장 두 번 막기 (K15), page 규칙 (K16 ⑥). */
@WebMvcTest(controllers = ContactController.class)
@Import({SecurityConfig.class, JsonSecurityHandlers.class, GlobalExceptionHandler.class, ContactSmsDispatcher.class})
class ContactControllerTest {

    private static final String BODY = """
            { "guardianId": 7, "method": "PHONE", "talkedWith": "SELF", "memo": null, "nextContactDate": null,
              "items": [ { "scheduleId": 101, "result": "BOOKED" } ], "applyToHousehold": false }
            """;
    private static final String FORBIDDEN = "이 작업을 할 권한이 없습니다. 필요하면 관리자에게 문의해 주세요.";

    @Autowired
    private MockMvc mvc;
    @MockitoBean
    private ContactPanelService panelService;
    @MockitoBean
    private ContactSaveService saveService;
    @MockitoBean
    private SmsSender smsSender;

    private Cookie csrf;

    @BeforeEach
    void setUp() throws Exception {
        csrf = mvc.perform(get("/api/csrf")).andReturn().getResponse().getCookie("XSRF-TOKEN");
    }

    private ResultActions save(MockHttpSession session, String key) throws Exception {
        return mvc.perform(post("/api/contacts").session(session).cookie(csrf).header("X-XSRF-TOKEN", csrf.getValue())
                .header(IdempotencyFilter.HEADER, key).contentType(MediaType.APPLICATION_JSON).content(BODY));
    }

    @Test
    void 패널은_전체_번호와_아이별_예정_항목을_준다() throws Exception {
        when(panelService.panel(7)).thenReturn(new ContactPanelResponse(
                new ContactPanelResponse.Guardian(7, "김보호", "01000000000", null, "PHONE", List.of(), null, true, "OK"),
                null, List.of()));

        mvc.perform(get("/api/guardians/7/contact-panel").session(AuthTestSupport.session(Role.DESK)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.guardian.phone").value("01000000000"))
                .andExpect(jsonPath("$.data.todayContact").doesNotExist());
    }

    @Test
    void 미용사는_패널_지난_연락_저장_모두_403() throws Exception {
        MockHttpSession groomer = AuthTestSupport.session(Role.GROOMER);

        mvc.perform(get("/api/guardians/7/contact-panel").session(groomer))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error.message").value(FORBIDDEN));
        mvc.perform(get("/api/guardians/7/contacts").session(groomer)).andExpect(status().isForbidden());
        save(groomer, "g-1").andExpect(status().isForbidden());
        verify(saveService, times(0)).save(any(), anyLong());
    }

    @Test
    void 저장은_201이고_같은_요청을_두_번_보내도_한_번만_저장한다() throws Exception {
        MockHttpSession desk = AuthTestSupport.session(5L, Role.DESK);
        when(saveService.save(any(), eq(5L))).thenReturn(new ContactSaveService.Saved("g-uuid",
                new ContactFollowUpService.Outcome(List.of(), null)));

        save(desk, "same-key").andExpect(status().isCreated()).andExpect(jsonPath("$.data.contactGroupId").value("g-uuid"));
        save(desk, "same-key").andExpect(status().isCreated()).andExpect(jsonPath("$.data.contactGroupId").value("g-uuid"));

        verify(saveService, times(1)).save(any(), eq(5L));
    }

    @Test
    void 지난_연락_page가_1보다_작으면_400() throws Exception {
        mvc.perform(get("/api/guardians/7/contacts").param("page", "0").session(AuthTestSupport.session(Role.DESK)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
    }
}
