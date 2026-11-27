package com.dadayiksun.crm.customer.dto;

/** {@code POST /api/guardians} 출력 201 (API 설계 3-3). */
public record RegisterGuardianResponse(Long guardianId, Long petId) {
}
