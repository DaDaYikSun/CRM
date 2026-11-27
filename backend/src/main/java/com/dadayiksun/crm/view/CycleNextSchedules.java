package com.dadayiksun.crm.view;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

/**
 * {@link NextScheduleReader}의 실제 구현 (#211): {@link PetScheduleReader}(예정일 순 OPEN 주기)의 첫 줄을 고른다.
 * 가장 이른 예정일 하나만 고르고 누락 표시는 cycle 값을 그대로 쓴다 (K17 ②). 방문 저장 응답(`VisitSaveAssembler`)은 이 계약을 쓰지 않고 방금 저장한 종류의 다음 주기를 따로 고른다.
 */
@Component
public class CycleNextSchedules implements NextScheduleReader {

    private final PetScheduleReader schedules;

    public CycleNextSchedules(PetScheduleReader schedules) {
        this.schedules = schedules;
    }

    @Override
    public Map<Long, NextSchedule> nextSchedules(Collection<Long> petIds) {
        Map<Long, NextSchedule> result = new HashMap<>();
        for (Long petId : petIds) {
            schedules.openSchedules(petId).stream().findFirst().ifPresent(s -> result.put(petId,
                    new NextSchedule(s.serviceType(), s.vaccineType() == null ? null : s.vaccineType().name(),
                            s.dueDate(), s.missed())));
        }
        return result;
    }
}
