package com.dadayiksun.crm.search.dto;

import java.time.LocalDate;
import java.util.List;

/** C7 검색 항목 (API 설계 3-4). 전체 번호는 담지 않고 뒤 4자리만 (NFR-SEC-003, POL-DATA-002). */
public record SearchItem(
        long petId, String petName,
        long guardianId, String guardianName, String phoneLast4,
        String breedName, LocalDate birthDate, boolean birthDateEstimated,
        String status, List<String> siblingNames) {
}
