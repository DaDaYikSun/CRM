package com.dadayiksun.crm.target.domain;

import java.time.LocalDate;

/**
 * 연락 대상 후보 한 행: 진행 중(OPEN) 예정 일정 하나와 판단에 필요한 저장된 값.
 * 조회 전용 쿼리가 schedule(cycle) · pet · guardian(customer) · vaccine_type · contact_log(contact)를 읽어 만든다 (K17 ②).
 * 값을 새로 계산하지 않는다. 누락은 저장된 {@code missedAt}만 본다 (T13).
 *
 * @param vaccineTypeName 접종이면 접종 종류 이름, 미용이면 null
 * @param vaccineSortOrder 접종 종류 순서 (같은 예정일일 때 가까운 접종을 고르는 순서)
 * @param processedToday   오늘(한국 시간) 이 예정 항목으로 연락 결과를 남겼는지 (POL-TGT-010, POL-MISS-009)
 */
public record TargetCandidate(
        long scheduleId,
        long petId,
        String petName,
        long guardianId,
        String guardianName,
        String phoneLast4,
        ServiceType serviceType,
        String vaccineTypeName,
        int vaccineSortOrder,
        LocalDate dueDate,
        LocalDate missedAt,
        ContactState contactState,
        LocalDate nextContactDate,
        boolean petActive,
        LocalDate contactHoldUntil,
        boolean tagNoContact,
        boolean processedToday) {

    public enum ServiceType { VACCINE, GROOMING }

    /** schedule.contact_state (DB 설계 4-10) */
    public enum ContactState { NONE, NO_ANSWER, BOOKED, REFUSED, OTHER_HOSPITAL, WRONG_NUMBER, CLOSED }

    public boolean missed() {
        return missedAt != null;
    }
}
