package com.dadayiksun.crm.cycle.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import com.dadayiksun.crm.cycle.domain.GroomInterval;
import com.dadayiksun.crm.cycle.domain.Schedule;
import com.dadayiksun.crm.cycle.domain.ServiceType;

/**
 * 예정 일정(schedule) 저장소. cycle이 쓰기 주인이다 (K17 ①, 묶음 대표 '예정 일정' K16 ④).
 * pet.groom_interval_days · groom_interval_basis(개인 미용 주기 캐시)도 cycle이 채운다 (DB 설계 5-1).
 */
public interface ScheduleRepository {

    Optional<Schedule> findById(long scheduleId);

    /** 같은 아이 · 서비스 · 종류의 진행 중(OPEN) 행 (active_key로 하나뿐) */
    Optional<Schedule> findOpen(long petId, ServiceType serviceType, Long vaccineTypeId);

    List<Schedule> findOpenByPet(long petId);

    /** 아이의 모든 주기, 예정일 순 */
    List<Schedule> findByPet(long petId);

    /** openedByVisitId: 방문 뒤 다음 주기로 열 때 그 방문, 아니면 null */
    long insertOpen(long petId, ServiceType serviceType, Long vaccineTypeId, LocalDate dueDate, int missThresholdDays,
                    Long openedByVisitId);

    void complete(long scheduleId, long visitId, LocalDate doneDate, int gapDays, LocalDate missedAt);

    /** 늦게 입력한 기준 안 방문으로 저장한 판정을 지운다 (T16) */
    void clearMissedAt(long scheduleId);

    void updateDueDate(long scheduleId, LocalDate dueDate);

    void voidSchedule(long scheduleId);

    /** 판정 작업 (K20): OPEN · missed_at 없음 · 예정일 + 기준 < 오늘인 행에 원래 판정일을 넣는다. 넣은 행 수 */
    int markMissed(LocalDate today);

    /** C12: 연락 상태 칸만 고친다 */
    void updateContact(long scheduleId, String contactState, int noAnswerCount, LocalDate nextContactDate);

    void saveGroomInterval(long petId, GroomInterval interval);
}
