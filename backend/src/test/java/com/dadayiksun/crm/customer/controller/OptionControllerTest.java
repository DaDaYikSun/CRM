package com.dadayiksun.crm.customer.controller;

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
import com.dadayiksun.crm.auth.domain.Role;
import com.dadayiksun.crm.common.error.GlobalExceptionHandler;
import com.dadayiksun.crm.common.security.JsonSecurityHandlers;
import com.dadayiksun.crm.common.security.SecurityConfig;
import com.dadayiksun.crm.customer.dto.OptionResponses.BreedOption;
import com.dadayiksun.crm.customer.dto.OptionResponses.CoatTypeOption;
import com.dadayiksun.crm.customer.dto.OptionResponses.VaccineTypeOption;
import com.dadayiksun.crm.customer.service.OptionQueryService;

/** C5 선택지 응답 모양 (API 설계 3-2). */
@WebMvcTest(controllers = OptionController.class)
@Import({SecurityConfig.class, JsonSecurityHandlers.class, GlobalExceptionHandler.class})
class OptionControllerTest {

    @Autowired
    private MockMvc mvc;
    @MockitoBean
    private OptionQueryService options;

    @Test
    void 품종은_기본_털_유형을_함께_주고_없으면_null() throws Exception {
        when(options.breeds()).thenReturn(List.of(new BreedOption(1L, "말티즈", 3L), new BreedOption(2L, "푸들", null)));

        mvc.perform(get("/api/options/breeds").session(AuthTestSupport.session(Role.DESK)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].breedId").value(1))
                .andExpect(jsonPath("$.data[0].name").value("말티즈"))
                .andExpect(jsonPath("$.data[0].defaultCoatTypeId").value(3))
                .andExpect(jsonPath("$.data[1].defaultCoatTypeId").doesNotExist());
    }

    @Test
    void 털_유형() throws Exception {
        when(options.coatTypes()).thenReturn(List.of(new CoatTypeOption(1L, "SHORT", "단모")));

        mvc.perform(get("/api/options/coat-types").session(AuthTestSupport.session(Role.DESK)))
                .andExpect(jsonPath("$.data[0].coatTypeId").value(1))
                .andExpect(jsonPath("$.data[0].code").value("SHORT"))
                .andExpect(jsonPath("$.data[0].name").value("단모"));
    }

    @Test
    void 기준값은_미용사도_읽는다() throws Exception {
        when(options.settings()).thenReturn(new com.dadayiksun.crm.customer.dto.OptionResponses.SettingsResponse(14, 30));

        mvc.perform(get("/api/options/settings").session(AuthTestSupport.session(Role.GROOMER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.missThresholdDays").value(14))
                .andExpect(jsonPath("$.data.rainbowHoldDays").value(30));
    }

    @Test
    void 접종_종류는_isMajor_이름으로() throws Exception {
        when(options.vaccineTypes()).thenReturn(List.of(new VaccineTypeOption(1L, "DHPPL", "종합백신", true)));

        mvc.perform(get("/api/options/vaccine-types").session(AuthTestSupport.session(Role.DESK)))
                .andExpect(jsonPath("$.data[0].vaccineTypeId").value(1))
                .andExpect(jsonPath("$.data[0].code").value("DHPPL"))
                .andExpect(jsonPath("$.data[0].name").value("종합백신"))
                .andExpect(jsonPath("$.data[0].isMajor").value(true))
                .andExpect(jsonPath("$.data[0].major").doesNotExist());
    }
}
