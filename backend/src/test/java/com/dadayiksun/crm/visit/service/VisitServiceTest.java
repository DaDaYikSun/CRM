package com.dadayiksun.crm.visit.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.dadayiksun.crm.auth.domain.CurrentStaff;
import com.dadayiksun.crm.auth.domain.Role;
import com.dadayiksun.crm.common.error.ApiException;
import com.dadayiksun.crm.common.error.ErrorCode;
import com.dadayiksun.crm.common.time.KstClock;
import com.dadayiksun.crm.visit.domain.VisitRecordedEvent;
import com.dadayiksun.crm.visit.dto.RecordVisitRequest;
import com.dadayiksun.crm.visit.repository.VisitRepository;

/** 방문 기록 (FR-VIS-001 · 002, T3, POL-END-002, #148 결정). 2026-11-25 10시(한국 시간) 기준. */
class VisitServiceTest {

    private static final long PET = 12L;
    private static final long RAINBOW_PET = 13L;
    private static final long COMBO = 1L; // 종합백신
    private static final long HEARTWORM = 2L;
    private static final CurrentStaff DESK = new CurrentStaff(3L, Role.DESK);
    private static final CurrentStaff GROOMER = new CurrentStaff(4L, Role.GROOMER);
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 25);

    private FakeVisits visits;
    private List<Object> events;
    private VisitService service;

    @BeforeEach
    void setUp() {
        visits = new FakeVisits();
        events = new ArrayList<>();
        KstClock clock = new KstClock(Clock.fixed(
                ZonedDateTime.of(2026, 11, 25, 10, 0, 0, 0, KstClock.ZONE).toInstant(), KstClock.ZONE));
        service = new VisitService(visits, events::add, clock);
    }

    private static RecordVisitRequest vaccine(Long typeId, LocalDate date) {
        return new RecordVisitRequest("VACCINE", typeId, date, null);
    }

    private static RecordVisitRequest grooming(LocalDate date, String memo) {
        return new RecordVisitRequest("GROOMING", null, date, memo);
    }

    private void assertValidation(RecordVisitRequest req, CurrentStaff staff, String field) {
        assertThatThrownBy(() -> service.record(PET, req, staff)).isInstanceOfSatisfying(ApiException.class, e -> {
            assertThat(e.code()).isEqualTo(ErrorCode.VALIDATION_FAILED);
            assertThat(e.fields()).extracting("field").contains(field);
        });
        assertThat(visits.rows).isEmpty();
        assertThat(events).isEmpty();
    }

    @Test
    void 접종을_저장하고_직원_시간을_서버가_붙이고_방문_기록됨_이벤트만_낸다() {
        long id = service.record(PET, vaccine(COMBO, LocalDate.of(2026, 11, 24)), DESK);

        VisitRepository.Row row = visits.rows.get(0);
        assertThat(row.serviceType()).isEqualTo("VACCINE");
        assertThat(row.vaccineTypeId()).isEqualTo(COMBO);
        assertThat(row.visitDate()).isEqualTo(LocalDate.of(2026, 11, 24));
        assertThat(row.createdBy()).isEqualTo(3L);
        assertThat(row.createdAt()).isEqualTo(LocalDateTime.of(2026, 11, 25, 10, 0));
        assertThat(events).containsExactly(new VisitRecordedEvent(id));
    }

    @Test
    void 날짜가_없으면_오늘_한국_시간() {
        service.record(PET, grooming(null, "  전체 미용  "), GROOMER);

        assertThat(visits.rows.get(0).visitDate()).isEqualTo(TODAY);
        assertThat(visits.rows.get(0).memo()).isEqualTo("전체 미용");
    }

    @Test
    void 같은_날_다른_접종_종류는_둘_다_저장된다() {
        service.record(PET, vaccine(COMBO, TODAY), DESK);
        service.record(PET, vaccine(HEARTWORM, TODAY), DESK);

        assertThat(visits.rows).hasSize(2);
    }

    @Test
    void 같은_아이_같은_날_같은_종류를_또_넣으면_400() {
        service.record(PET, vaccine(COMBO, TODAY), DESK);

        assertThatThrownBy(() -> service.record(PET, vaccine(COMBO, TODAY), DESK))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.fields()).extracting("field").containsExactly("visitDate"));
        assertThat(visits.rows).hasSize(1);
        assertThat(events).hasSize(1);
    }

    @Test
    void 미래_날짜는_400() {
        assertValidation(grooming(TODAY.plusDays(1), null), DESK, "visitDate");
    }

    @Test
    void 접종_종류가_없거나_고를_수_없는_종류면_400() {
        assertValidation(vaccine(null, TODAY), DESK, "vaccineTypeId");
        assertValidation(vaccine(99L, TODAY), DESK, "vaccineTypeId");
    }

    @Test
    void 미용에_접종_종류를_보내면_400() {
        assertValidation(new RecordVisitRequest("GROOMING", COMBO, TODAY, null), DESK, "vaccineTypeId");
    }

    @Test
    void 메모는_200자까지() {
        assertValidation(grooming(TODAY, "가".repeat(201)), DESK, "memo");
        service.record(PET, grooming(TODAY, "가".repeat(200)), DESK);
        assertThat(visits.rows).hasSize(1);
    }

    @Test
    void 구분이_없거나_정해진_값이_아니면_400() {
        assertValidation(new RecordVisitRequest(null, null, TODAY, null), DESK, "serviceType");
        assertValidation(new RecordVisitRequest("BATH", null, TODAY, null), DESK, "serviceType");
    }

    @Test
    void 미용사가_접종을_보내면_403이고_미용은_저장된다() {
        assertThatThrownBy(() -> service.record(PET, vaccine(COMBO, TODAY), GROOMER))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.FORBIDDEN));
        assertThat(visits.rows).isEmpty();

        service.record(PET, grooming(TODAY, null), GROOMER);
        assertThat(visits.rows).hasSize(1);
    }

    @Test
    void 무지개다리_아이는_409() {
        assertThatThrownBy(() -> service.record(RAINBOW_PET, grooming(TODAY, null), DESK))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.PET_RAINBOW));
        assertThat(visits.rows).isEmpty();
        assertThat(events).isEmpty();
    }

    @Test
    void 없는_아이는_404() {
        assertThatThrownBy(() -> service.record(999L, grooming(TODAY, null), DESK))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.NOT_FOUND));
    }

    @Test
    void 타임라인_줄은_접종이면_종류_이름_미용이면_미용() {
        visits.lines.add(new VisitRepository.LineRow(1, "VACCINE", "종합백신", TODAY, null, "데스크",
                LocalDateTime.of(2026, 11, 25, 9, 0), false));
        visits.lines.add(new VisitRepository.LineRow(2, "GROOMING", null, TODAY, "전체", "미용사",
                LocalDateTime.of(2026, 11, 25, 9, 30), true));

        assertThat(service.lines(PET, Set.of("VACCINE", "GROOMING")))
                .extracting(l -> l.summary()).containsExactly("종합백신", "미용");
    }

    /** 아이 12(ACTIVE) · 13(RAINBOW), 고를 수 있는 접종 종류 1 · 2 */
    static class FakeVisits implements VisitRepository {
        final List<Row> rows = new ArrayList<>();
        final List<LineRow> lines = new ArrayList<>();
        private final Map<Long, String> pets = new HashMap<>(Map.of(PET, "ACTIVE", RAINBOW_PET, "RAINBOW"));

        @Override
        public Optional<String> petStatus(long petId) {
            return Optional.ofNullable(pets.get(petId));
        }

        @Override
        public boolean isSelectableVaccineType(long vaccineTypeId) {
            return vaccineTypeId == COMBO || vaccineTypeId == HEARTWORM;
        }

        @Override
        public boolean existsSameDay(long petId, String serviceType, Long vaccineTypeId, LocalDate visitDate) {
            return rows.stream().anyMatch(r -> r.petId() == petId && r.serviceType().equals(serviceType)
                    && Objects.equals(r.vaccineTypeId(), vaccineTypeId) && r.visitDate().equals(visitDate));
        }

        @Override
        public long insert(Row row) {
            rows.add(row);
            return 500 + rows.size();
        }

        @Override
        public List<LineRow> lines(long petId, Collection<String> serviceTypes) {
            return lines.stream().filter(l -> serviceTypes.contains(l.serviceType())).toList();
        }
    }
}
