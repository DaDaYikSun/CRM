package com.dadayiksun.crm.view;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.dadayiksun.crm.common.error.GlobalExceptionHandler;
import com.dadayiksun.crm.common.time.KstClock;

/** 주소 · 응답 모양 (API 설계 3장 GET /api/grooming), page · tab 오류는 공통 응답 (K16 ② · ⑥). */
class GroomingControllerTest {

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        KstClock clock = new KstClock(Clock.fixed(
                ZonedDateTime.of(2026, 11, 25, 10, 0, 0, 0, KstClock.ZONE).toInstant(), KstClock.ZONE));
        GroomingRow row = new GroomingRow(12L, "보리", "말티즈", "장모", LocalDate.of(2023, 5, 2), "김보호", "0000",
                List.of("SENSITIVE"), 102L, LocalDate.of(2026, 11, 20), false, LocalDate.of(2026, 10, 17), 34, "MEDIAN",
                false);
        GroomingListAssembler assembler = new GroomingListAssembler((dueUntil, today) -> List.of(row), clock);
        mvc = MockMvcBuilders.standaloneSetup(new GroomingController(assembler))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void 목록은_아이_줄과_페이지_정보를_준다_번호는_뒤_4자리만() throws Exception {
        mvc.perform(get("/api/grooming").param("tab", "week").param("page", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].petId").value(12))
                .andExpect(jsonPath("$.data.items[0].petName").value("보리"))
                .andExpect(jsonPath("$.data.items[0].breedName").value("말티즈"))
                .andExpect(jsonPath("$.data.items[0].coatTypeName").value("장모"))
                .andExpect(jsonPath("$.data.items[0].guardianName").value("김보호"))
                .andExpect(jsonPath("$.data.items[0].phoneLast4").value("0000"))
                .andExpect(jsonPath("$.data.items[0].phone").doesNotExist())
                .andExpect(jsonPath("$.data.items[0].guardianTags[0]").value("SENSITIVE"))
                .andExpect(jsonPath("$.data.items[0].scheduleId").value(102))
                .andExpect(jsonPath("$.data.items[0].dueDate").exists())
                .andExpect(jsonPath("$.data.items[0].lastGroomDate").exists())
                .andExpect(jsonPath("$.data.items[0].groomInterval.days").value(34))
                .andExpect(jsonPath("$.data.items[0].groomInterval.basis").value("MEDIAN"))
                .andExpect(jsonPath("$.data.items[0].groomedToday").value(false))
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.size").value(20))
                .andExpect(jsonPath("$.data.total").value(1));
    }

    @Test
    void 탭이_없으면_오늘_탭() throws Exception {
        mvc.perform(get("/api/grooming")).andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(1));
    }

    @Test
    void 정해진_탭이_아니거나_page가_잘못되면_400() throws Exception {
        mvc.perform(get("/api/grooming").param("tab", "missed"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
        mvc.perform(get("/api/grooming").param("page", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
    }
}
