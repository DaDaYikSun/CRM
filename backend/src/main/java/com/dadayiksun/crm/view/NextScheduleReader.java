package com.dadayiksun.crm.view;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Map;

/**
 * 계약: cycle이 주는 '아이별 다음 예정' Reader (API 설계 3-3 {@code pets[].nextSchedule}, R3).
 * 조립 자리(view)는 이 값을 붙이기만 하고 다시 계산하지 않는다 (K17 ②, 누락은 저장된 {@code missed_at}만).
 *
 * <p>실제 구현은 {@link CycleNextSchedules}다({@link PetScheduleReader}의 가장 이른 진행 중 일정). #148 방문 저장 응답도 이 계약을 쓴다.
 */
public interface NextScheduleReader {

    /** 진행 중(OPEN) 예정 중 가장 가까운 것. 없는 아이는 키가 없다. */
    Map<Long, NextSchedule> nextSchedules(Collection<Long> petIds);

    record NextSchedule(String serviceType, String vaccineTypeName, LocalDate dueDate, boolean missed) {
    }
}
