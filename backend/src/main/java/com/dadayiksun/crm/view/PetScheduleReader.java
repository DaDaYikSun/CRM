package com.dadayiksun.crm.view;

import java.time.LocalDate;
import java.util.List;

/**
 * 계약 C4 Reader: cycle이 주는 '아이의 진행 중(OPEN) 주기' (API 설계 3-3 {@code schedules}).
 * 조립 자리는 붙이기만 한다. 누락은 저장된 {@code missed_at}이 있으면 true고 다시 계산하지 않는다 (K17 ②, T13).
 * 방문으로 완료된 주기는 OPEN이 아니라 여기 없다 (POL-MISS-002).
 * 실제 구현: {@link PetSchedules} (#148).
 */
public interface PetScheduleReader {

    /** 예정일 순. */
    List<ScheduleItem> openSchedules(long petId);

    record ScheduleItem(Long scheduleId, String serviceType, VaccineTypeItem vaccineType, LocalDate dueDate,
            boolean missed) {
    }

    record VaccineTypeItem(Long vaccineTypeId, String name, boolean isMajor) {
    }
}
