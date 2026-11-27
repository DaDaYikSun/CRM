package com.dadayiksun.crm.cycle.domain;

import java.time.LocalDate;

/**
 * 예정 일정 한 행 = 아이 × 서비스(× 접종 종류)의 한 주기 (DB 설계 4-10).
 * 같은 아이 · 서비스 · 종류에 진행 중(OPEN) 행은 하나뿐이다 (active_key).
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
        LocalDate doneDate,
        Integer gapDays,
        LocalDate missedAt,
        String contactState,
        int noAnswerCount,
        LocalDate nextContactDate) {

    public enum Status { OPEN, DONE, VOID }
}
