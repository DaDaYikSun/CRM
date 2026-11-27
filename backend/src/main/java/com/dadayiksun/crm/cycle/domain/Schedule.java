package com.dadayiksun.crm.cycle.domain;

import java.time.LocalDate;

/**
 * 예정 일정 한 행 = 아이 × 서비스(× 접종 종류)의 한 주기 (DB 설계 4-10).
 * 같은 아이 · 서비스 · 종류에 진행 중(OPEN) 행은 하나뿐이다 (active_key).
 * openedByVisitId: 방문 뒤 다음 주기로 연 행에만 그 방문 (V3). 같은 방문을 다시 처리한 것을 알아보는 데 쓴다.
 */
public record Schedule(
        long scheduleId,
        long petId,
        ServiceType serviceType,
        Long vaccineTypeId,
        LocalDate dueDate,
        Status status,
        int missThresholdDays,
        Long doneVisitId,
        Long openedByVisitId,
        LocalDate doneDate,
        Integer gapDays,
        LocalDate missedAt,
        String contactState,
        int noAnswerCount,
        LocalDate nextContactDate) {

    /** 방문으로 열지 않은 주기 (등록 · 다시 계산 · 테스트 준비용). opened_by_visit_id = NULL */
    public Schedule(long scheduleId, long petId, ServiceType serviceType, Long vaccineTypeId, LocalDate dueDate,
                    Status status, int missThresholdDays, Long doneVisitId, LocalDate doneDate, Integer gapDays,
                    LocalDate missedAt, String contactState, int noAnswerCount, LocalDate nextContactDate) {
        this(scheduleId, petId, serviceType, vaccineTypeId, dueDate, status, missThresholdDays, doneVisitId, null,
                doneDate, gapDays, missedAt, contactState, noAnswerCount, nextContactDate);
    }

    public enum Status { OPEN, DONE, VOID }
}
