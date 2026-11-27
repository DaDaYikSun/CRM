package com.dadayiksun.crm.cycle.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dadayiksun.crm.cycle.domain.Schedule;
import com.dadayiksun.crm.cycle.repository.ScheduleRepository;

/**
 * C4 Reader: 아이의 예정 일정 (아이 상세 · 보호자 상세 · 리포트 조립 자리, 방문 저장 출력이 읽는다).
 * 저장된 값만 준다. 누락은 missedAt으로만 본다 (T13).
 */
@Service
@Transactional(readOnly = true)
public class CycleQueryService {

    private final ScheduleRepository schedules;

    public CycleQueryService(ScheduleRepository schedules) {
        this.schedules = schedules;
    }

    /** 아이의 모든 주기, 예정일 순 */
    public List<Schedule> findSchedules(long petId) {
        return schedules.findByPet(petId);
    }

    /** 진행 중(OPEN) 주기만 */
    public List<Schedule> findOpenSchedules(long petId) {
        return schedules.findOpenByPet(petId);
    }
}
