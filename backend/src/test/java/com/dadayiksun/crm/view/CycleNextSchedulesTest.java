package com.dadayiksun.crm.view;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.dadayiksun.crm.view.PetScheduleReader.ScheduleItem;
import com.dadayiksun.crm.view.PetScheduleReader.VaccineTypeItem;

/** #211: 다음 예정은 PetScheduleReader의 첫 줄(예정일 순)을 그대로 옮긴다: 일정 없음 · 가장 이른 것 · 누락은 저장된 값 그대로. */
class CycleNextSchedulesTest {

    private final PetScheduleReader schedules = mock(PetScheduleReader.class);
    private final CycleNextSchedules next = new CycleNextSchedules(schedules);

    @Test
    void 일정이_없는_아이는_키가_없다() {
        when(schedules.openSchedules(12L)).thenReturn(List.of());

        assertThat(next.nextSchedules(List.of(12L))).isEmpty();
    }

    @Test
    void 예정일_순_첫_줄이_다음_예정이고_접종_종류_이름이_붙는다() {
        when(schedules.openSchedules(12L)).thenReturn(List.of(
                new ScheduleItem(1L, "VACCINE", new VaccineTypeItem(2L, "광견병", true), LocalDate.parse("2026-12-01"), false),
                new ScheduleItem(2L, "VACCINE", new VaccineTypeItem(1L, "종합백신", true), LocalDate.parse("2026-12-10"), false),
                new ScheduleItem(3L, "GROOMING", null, LocalDate.parse("2026-12-20"), false)));

        var n = next.nextSchedules(List.of(12L)).get(12L);

        assertThat(n.serviceType()).isEqualTo("VACCINE");
        assertThat(n.vaccineTypeName()).isEqualTo("광견병");
        assertThat(n.dueDate()).isEqualTo(LocalDate.parse("2026-12-01"));
    }

    @Test
    void 미용은_접종_종류_이름이_없고_누락은_저장된_값_그대로() {
        when(schedules.openSchedules(12L)).thenReturn(List.of(
                new ScheduleItem(1L, "GROOMING", null, LocalDate.parse("2026-11-01"), true)));

        var n = next.nextSchedules(List.of(12L)).get(12L);

        assertThat(n.vaccineTypeName()).isNull();
        assertThat(n.missed()).isTrue();
    }

    @Test
    void 여러_아이를_한_번에_묻는다() {
        when(schedules.openSchedules(1L)).thenReturn(List.of(
                new ScheduleItem(1L, "GROOMING", null, LocalDate.parse("2026-12-01"), false)));
        when(schedules.openSchedules(2L)).thenReturn(List.of());

        var result = next.nextSchedules(List.of(1L, 2L));

        assertThat(result).containsOnlyKeys(1L);
    }
}
