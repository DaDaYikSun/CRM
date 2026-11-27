package com.dadayiksun.crm.view;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.dadayiksun.crm.auth.access.AuthTestSupport;
import com.dadayiksun.crm.auth.domain.Role;
import com.dadayiksun.crm.common.error.ApiException;
import com.dadayiksun.crm.common.error.ErrorCode;
import com.dadayiksun.crm.common.error.GlobalExceptionHandler;
import com.dadayiksun.crm.common.security.JsonSecurityHandlers;
import com.dadayiksun.crm.common.security.SecurityConfig;
import com.dadayiksun.crm.customer.dto.PetDetail;
import com.dadayiksun.crm.customer.service.PetDetailReader;
import com.dadayiksun.crm.view.TimelineLine.Kind;

/**
 * C9 아이 상세 · 타임라인 (API 설계 3-3 · 3-5). 타임라인은 진짜 조립 클래스를 쓰고 주인 도메인 Reader만 가짜로 둔다.
 * 미용사 제한(POL-ACC-011): 규칙(#143)이 VACCINE · CONTACT를 403으로 막고, ALL · 없음은 조립 자리가 미용 줄만 준다.
 */
@WebMvcTest(controllers = PetDetailController.class)
@Import({SecurityConfig.class, JsonSecurityHandlers.class, GlobalExceptionHandler.class, PetTimelineAssembler.class})
class PetDetailControllerTest {

    @Autowired
    private MockMvc mvc;
    @MockitoBean
    private PetDetailAssembler detail;
    @MockitoBean
    private PetDetailReader customer;
    @MockitoBean
    private VisitTimelineReader visits;
    @MockitoBean
    private ContactTimelineReader contacts;

    private static TimelineLine line(Kind kind, long id, String date, String time) {
        return new TimelineLine(kind, id, LocalDate.parse(date), kind.name(), null, "데스크 직원",
                OffsetDateTime.parse(date + "T" + time + "+09:00"), false, false,
                kind == Kind.CONTACT ? Boolean.TRUE : null, kind == Kind.CONTACT ? Boolean.FALSE : null);
    }

    private void stubLines() {
        when(visits.lines(anyLong(), any())).thenAnswer(inv -> {
            Set<Kind> kinds = inv.getArgument(1);
            List<TimelineLine> all = List.of(line(Kind.VACCINE, 1, "2026-11-10", "10:00:00"),
                    line(Kind.GROOMING, 2, "2026-11-20", "10:00:00"));
            return all.stream().filter(l -> kinds.contains(l.kind())).toList();
        });
        when(contacts.lines(anyLong())).thenReturn(List.of(line(Kind.CONTACT, 3, "2026-11-15", "09:00:00")));
    }

    @Test
    void 아이_상세는_모든_역할이_읽고_전체_번호가_없다() throws Exception {
        var pet = new PetDetail.PetBlock(12L, "보리", "DOG", "ACTIVE", new PetDetail.Named(1L, "말티즈"),
                new PetDetail.Named(3L, "장모"), LocalDate.of(2023, 5, 2), true, null, null,
                new PetDetail.GroomInterval(34, "MEDIAN"));
        var guardian = new PetDetail.GuardianBlock(7L, "김보호", "0000", List.of("SENSITIVE"));
        var schedule = new PetScheduleReader.ScheduleItem(101L, "VACCINE",
                new PetScheduleReader.VaccineTypeItem(1L, "종합백신", true), LocalDate.of(2026, 11, 14), true);
        when(detail.assemble(12L)).thenReturn(new PetDetailAssembler.Response(pet, guardian,
                List.of(new PetDetail.Sibling(13L, "콩이", "ACTIVE")), List.of(schedule)));

        for (Role role : Role.values()) {
            mvc.perform(get("/api/pets/12").session(AuthTestSupport.session(role)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.guardian.phoneLast4").value("0000"))
                    .andExpect(jsonPath("$.data.guardian.phone").doesNotExist())
                    .andExpect(jsonPath("$.data.pet.birthDateEstimated").value(true))
                    .andExpect(jsonPath("$.data.siblings[0].petId").value(13))
                    .andExpect(jsonPath("$.data.schedules[0].missed").value(true))
                    .andExpect(jsonPath("$.data.schedules[0].vaccineType.name").value("종합백신"));
        }
    }

    @Test
    void 없는_아이는_404_공통_응답() throws Exception {
        when(detail.assemble(99L)).thenThrow(new ApiException(ErrorCode.NOT_FOUND));

        mvc.perform(get("/api/pets/99").session(AuthTestSupport.session(Role.DESK)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.error.message").value("찾는 정보가 없습니다. 목록에서 다시 찾아 주세요."));
    }

    @Test
    void 타임라인_전체는_날짜_최근_순으로_세_종류가_섞인다() throws Exception {
        stubLines();

        mvc.perform(get("/api/pets/12/timeline?type=ALL").session(AuthTestSupport.session(Role.DESK)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(3))
                .andExpect(jsonPath("$.data.size").value(20))
                .andExpect(jsonPath("$.data.items[0].kind").value("GROOMING"))
                .andExpect(jsonPath("$.data.items[1].kind").value("CONTACT"))
                .andExpect(jsonPath("$.data.items[1].converted").value(true))
                .andExpect(jsonPath("$.data.items[2].kind").value("VACCINE"))
                .andExpect(jsonPath("$.data.items[2].converted").doesNotExist())
                .andExpect(jsonPath("$.data.items[2].staffName").value("데스크 직원"));
    }

    @Test
    void 필터는_그_종류만_준다() throws Exception {
        stubLines();

        mvc.perform(get("/api/pets/12/timeline?type=CONTACT").session(AuthTestSupport.session(Role.DESK)))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].kind").value("CONTACT"));
        mvc.perform(get("/api/pets/12/timeline?type=VACCINE").session(AuthTestSupport.session(Role.ADMIN)))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].kind").value("VACCINE"));
        verify(contacts).lines(12L); // CONTACT 필터 때만 부른다
    }

    @Test
    void 미용사는_type_VACCINE_CONTACT가_403() throws Exception {
        stubLines();

        for (String type : List.of("VACCINE", "CONTACT", "contact")) {
            mvc.perform(get("/api/pets/12/timeline?type=" + type).session(AuthTestSupport.session(Role.GROOMER)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error.code").value("FORBIDDEN"))
                    .andExpect(jsonPath("$.error.message").value("이 작업을 할 권한이 없습니다. 필요하면 관리자에게 문의해 주세요."));
        }
        verifyNoInteractions(contacts);
    }

    @Test
    void 미용사는_type_ALL과_type_없음에서_미용_줄만_받는다() throws Exception {
        stubLines();

        for (String url : List.of("/api/pets/12/timeline?type=ALL", "/api/pets/12/timeline",
                "/api/pets/12/timeline?type=GROOMING")) {
            mvc.perform(get(url).session(AuthTestSupport.session(Role.GROOMER)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.total").value(1))
                    .andExpect(jsonPath("$.data.items[0].kind").value("GROOMING"));
        }
        verify(contacts, never()).lines(anyLong());
        verify(visits, never()).lines(anyLong(), org.mockito.ArgumentMatchers.argThat(k -> k.contains(Kind.VACCINE)));
    }

    @Test
    void 쪽은_20개씩이고_마지막_뒤는_빈_목록과_total() throws Exception {
        List<TimelineLine> many = new ArrayList<>();
        for (int i = 1; i <= 21; i++) {
            many.add(line(Kind.GROOMING, i, "2026-10-" + String.format("%02d", i), "10:00:00"));
        }
        when(visits.lines(anyLong(), any())).thenReturn(many);
        when(contacts.lines(anyLong())).thenReturn(List.of());

        mvc.perform(get("/api/pets/12/timeline?page=1").session(AuthTestSupport.session(Role.DESK)))
                .andExpect(jsonPath("$.data.items.length()").value(20))
                .andExpect(jsonPath("$.data.items[0].id").value(21));
        mvc.perform(get("/api/pets/12/timeline?page=2").session(AuthTestSupport.session(Role.DESK)))
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.total").value(21));
        mvc.perform(get("/api/pets/12/timeline?page=3").session(AuthTestSupport.session(Role.DESK)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(0))
                .andExpect(jsonPath("$.data.total").value(21));
    }

    @Test
    void 잘못된_page와_type은_400() throws Exception {
        for (String q : List.of("page=0", "page=-1", "page=abc", "type=XYZ")) {
            mvc.perform(get("/api/pets/12/timeline?" + q).session(AuthTestSupport.session(Role.DESK)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
        }
    }

    @Test
    void 없는_아이의_타임라인은_404() throws Exception {
        doThrow(new ApiException(ErrorCode.NOT_FOUND)).when(customer).requireExists(99L);

        mvc.perform(get("/api/pets/99/timeline").session(AuthTestSupport.session(Role.DESK)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    @Test
    void 기록이_없으면_빈_목록() throws Exception {
        when(visits.lines(anyLong(), any())).thenReturn(List.of());
        when(contacts.lines(anyLong())).thenReturn(List.of());

        mvc.perform(get("/api/pets/12/timeline").session(AuthTestSupport.session(Role.DESK)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(0))
                .andExpect(jsonPath("$.data.total").value(0));
    }
}
