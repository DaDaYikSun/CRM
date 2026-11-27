package com.dadayiksun.crm.view;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.dadayiksun.crm.auth.domain.CurrentStaff;
import com.dadayiksun.crm.auth.domain.Role;
import com.dadayiksun.crm.customer.dto.PetDetail;
import com.dadayiksun.crm.customer.service.PetDetailReader;
import com.dadayiksun.crm.cycle.domain.Schedule;
import com.dadayiksun.crm.cycle.domain.ServiceType;
import com.dadayiksun.crm.cycle.service.CycleQueryService;
import com.dadayiksun.crm.visit.dto.RecordVisitRequest;
import com.dadayiksun.crm.visit.service.VisitService;

/** 방문 저장 출력 (API 설계 3-5): 닫힌 주기 · 같은 서비스(종류)의 다음 주기 · 미용이면 개인 미용 주기 */
class VisitSaveAssemblerTest {

    private static final CurrentStaff DESK = new CurrentStaff(3L, Role.DESK);

    private VisitService visits;
    private CycleQueryService cycle;
    private PetDetailReader pets;
    private VisitSaveAssembler assembler;

    @BeforeEach
    void setUp() {
        visits = mock(VisitService.class);
        cycle = mock(CycleQueryService.class);
        pets = mock(PetDetailReader.class);
        assembler = new VisitSaveAssembler(visits, cycle, pets);
    }

    private static Schedule schedule(long id, ServiceType type, Long typeId, LocalDate due, Schedule.Status status,
                                     Long doneVisitId) {
        return new Schedule(id, 12, type, typeId, due, status, 14, doneVisitId, null, null, null, "NONE", 0, null);
    }

    @Test
    void 접종이면_닫힌_주기와_그_종류의_다음_주기를_주고_미용_주기는_없다() {
        RecordVisitRequest req = new RecordVisitRequest("VACCINE", 1L, LocalDate.of(2026, 11, 24), null);
        when(visits.record(eq(12L), any(), any())).thenReturn(501L);
        when(cycle.findSchedules(12)).thenReturn(List.of(
                schedule(101, ServiceType.VACCINE, 1L, LocalDate.of(2026, 11, 20), Schedule.Status.DONE, 501L),
                schedule(102, ServiceType.VACCINE, 2L, LocalDate.of(2026, 12, 1), Schedule.Status.OPEN, null),
                schedule(140, ServiceType.VACCINE, 1L, LocalDate.of(2027, 11, 24), Schedule.Status.OPEN, null),
                schedule(103, ServiceType.GROOMING, null, LocalDate.of(2026, 12, 5), Schedule.Status.OPEN, null)));

        VisitSaveAssembler.Response res = assembler.record(12, req, DESK);

        assertThat(res.visitId()).isEqualTo(501L);
        assertThat(res.completedScheduleId()).isEqualTo(101L);
        assertThat(res.nextSchedule()).isEqualTo(
                new VisitSaveAssembler.NextSchedule(140, "VACCINE", 1L, LocalDate.of(2027, 11, 24)));
        assertThat(res.groomInterval()).isNull();
        verify(pets, never()).read(any());
    }

    @Test
    void 미용이면_다시_계산한_개인_미용_주기를_준다() {
        RecordVisitRequest req = new RecordVisitRequest("GROOMING", null, null, null);
        when(visits.record(eq(12L), any(), any())).thenReturn(502L);
        when(cycle.findSchedules(12)).thenReturn(List.of(
                schedule(103, ServiceType.GROOMING, null, LocalDate.of(2026, 11, 20), Schedule.Status.DONE, 502L),
                schedule(150, ServiceType.GROOMING, null, LocalDate.of(2026, 12, 29), Schedule.Status.OPEN, null)));
        PetDetail.GroomInterval interval = new PetDetail.GroomInterval(34, "MEDIAN");
        PetDetail.PetBlock pet = mock(PetDetail.PetBlock.class);
        when(pet.groomInterval()).thenReturn(interval);
        when(pets.read(12L)).thenReturn(new PetDetail(pet, null, List.of()));

        VisitSaveAssembler.Response res = assembler.record(12, req, DESK);

        assertThat(res.completedScheduleId()).isEqualTo(103L);
        assertThat(res.nextSchedule().scheduleId()).isEqualTo(150L);
        assertThat(res.groomInterval()).isEqualTo(interval);
    }

    @Test
    void 열린_주기가_없으면_다음_예정은_null() {
        when(visits.record(eq(12L), any(), any())).thenReturn(503L);
        when(cycle.findSchedules(12)).thenReturn(List.of());

        VisitSaveAssembler.Response res = assembler.record(12,
                new RecordVisitRequest("VACCINE", 9L, null, null), DESK);

        assertThat(res.completedScheduleId()).isNull();
        assertThat(res.nextSchedule()).isNull();
    }
}
