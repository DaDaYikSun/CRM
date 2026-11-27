package com.dadayiksun.crm.customer.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 보호자 상세의 customer 몫 (API 설계 3-3 {@code GET /api/guardians/{id}}). {@code pets[].nextSchedule}은 cycle 값이라
 * 여기 없다. 조립 자리(view)가 붙인다 (R3, 4-1).
 */
public record GuardianDetail(GuardianInfo guardian, List<PetCard> pets) {

    /** phone은 숫자만 저장된 전체 번호다 (POL-DATA-002). */
    public record GuardianInfo(Long guardianId, String name, String phone, String subPhone, String preferredContact,
            String phoneStatus, LocalDate phoneCheckedAt, boolean privacyAgreed, boolean smsAgreed, List<String> tags,
            String contactTimeNote, OffsetDateTime createdAt, String createdByName) {
    }

    public record PetCard(Long petId, String name, String status, String breedName, String coatTypeName,
            LocalDate birthDate, boolean birthDateEstimated, LocalDate contactHoldUntil) {
    }
}
