package com.dadayiksun.crm.view;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.dadayiksun.crm.customer.dto.OptionResponses.VaccineTypeOption;
import com.dadayiksun.crm.customer.service.OptionQueryService;
import com.dadayiksun.crm.cycle.domain.Schedule;
import com.dadayiksun.crm.cycle.domain.ServiceType;
import com.dadayiksun.crm.cycle.service.CycleQueryService;

/** 아이 상세 schedules (C4, API 설계 3-3): 예정일 순, 접종 종류 이름, 저장된 누락만 */
class PetSchedulesTest {

    private static Schedule open(long id, ServiceType type, Long typeId, LocalDate due, LocalDate missedAt) {
        return new Schedule(id, 12, type, typeId, due, Schedule.Status.OPEN, 14, null, null, null, missedAt, "NONE", 0, null);
    }

    @Test
    void 예정일_순으로_접종_종류_이름과_누락을_붙인다() {
        CycleQueryService cycle = mock(CycleQueryService.class);
        OptionQueryService options = mock(OptionQueryService.class);
        when(options.vaccineTypes()).thenReturn(List.of(new VaccineTypeOption(1L, "DHPPL", "종합백신", true)));
        when(cycle.findOpenSchedules(12)).thenReturn(List.of(
                open(102, ServiceType.GROOMING, null, LocalDate.of(2026, 12, 26), null),
                open(101, ServiceType.VACCINE, 1L, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 16)),
                open(103, ServiceType.VACCINE, 9L, LocalDate.of(2027, 1, 1), null)));

        List<PetScheduleReader.ScheduleItem> items = new PetSchedules(cycle, options).openSchedules(12);

        assertThat(items).extracting(PetScheduleReader.ScheduleItem::scheduleId).containsExactly(101L, 102L, 103L);
        assertThat(items.get(0).vaccineType()).isEqualTo(new PetScheduleReader.VaccineTypeItem(1L, "종합백신", true));
        assertThat(items.get(0).missed()).isTrue();
        assertThat(items.get(1).vaccineType()).isNull();
        assertThat(items.get(2).vaccineType()).isNull(); // 선택지에 없는 종류
    }
}
