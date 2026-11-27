package com.dadayiksun.crm.target.controller;

import static com.dadayiksun.crm.target.domain.TargetFixtures.row;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.ZonedDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.dadayiksun.crm.common.error.GlobalExceptionHandler;
import com.dadayiksun.crm.common.time.KstClock;
import com.dadayiksun.crm.target.service.TargetQueryService;

/** 주소 · 응답 모양 (API 설계 3-6), page · tab 오류는 공통 응답 (K16 ② · ⑥). */
class TargetControllerTest {

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        KstClock clock = new KstClock(Clock.fixed(
                ZonedDateTime.of(2026, 11, 25, 10, 0, 0, 0, KstClock.ZONE).toInstant(), KstClock.ZONE));
        TargetQueryService service = new TargetQueryService((today, weekEnd) -> List.of(row(101).build()), clock);
        mvc = MockMvcBuilders.standaloneSetup(new TargetController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void 목록은_보호자_줄과_페이지_정보를_준다() throws Exception {
        mvc.perform(get("/api/targets").param("tab", "today").param("page", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].guardianName").value("김보호"))
                .andExpect(jsonPath("$.data.items[0].phoneLast4").value("0000"))
                .andExpect(jsonPath("$.data.items[0].pets[0].items[0].scheduleId").value(101))
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.size").value(20))
                .andExpect(jsonPath("$.data.total").value(1));
    }

    @Test
    void 탭이_없으면_오늘_탭() throws Exception {
        mvc.perform(get("/api/targets")).andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(1));
    }

    @Test
    void page가_0이거나_숫자가_아니면_400() throws Exception {
        mvc.perform(get("/api/targets").param("page", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
        mvc.perform(get("/api/targets").param("page", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
    }

    @Test
    void 정해진_탭이_아니면_400() throws Exception {
        mvc.perform(get("/api/targets").param("tab", "all"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
    }

    @Test
    void 요약은_탭별_아이_수() throws Exception {
        mvc.perform(get("/api/targets/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.today.total").value(1))
                .andExpect(jsonPath("$.data.today.done").value(0))
                .andExpect(jsonPath("$.data.week.total").value(1))
                .andExpect(jsonPath("$.data.missed").value(0));
    }
}
