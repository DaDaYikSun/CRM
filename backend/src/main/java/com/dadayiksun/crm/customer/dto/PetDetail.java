package com.dadayiksun.crm.customer.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 아이 상세의 customer 몫 (API 설계 3-3 {@code GET /api/pets/{id}}). {@code schedules}는 cycle 값이라 여기 없다.
 * 조립 자리(view)가 붙인다 (R3, 4-1). 전체 번호는 없다 (NFR-SEC-003).
 * 번호 확인 칸(phoneStatus · phoneCheckedAt · phoneCheckNeeded)과 선택 항목(등록번호 · 사진 · 펫보험)은 이 이슈 범위 밖이라 뺐다.
 */
public record PetDetail(PetBlock pet, GuardianBlock guardian, List<Sibling> siblings) {

    public record PetBlock(Long petId, String name, String species, String status, Named breed, Named coatType,
            LocalDate birthDate, boolean birthDateEstimated, BigDecimal weightKg, LocalDate contactHoldUntil,
            GroomInterval groomInterval) {
    }

    public record Named(Long id, String name) {
    }

    /** pet에 저장된 개인 미용 주기 캐시. 아직 없으면 null (cycle이 채운다). */
    public record GroomInterval(int days, String basis) {
    }

    public record GuardianBlock(Long guardianId, String name, String phoneLast4, List<String> tags) {
    }

    public record Sibling(Long petId, String name, String status) {
    }
}
