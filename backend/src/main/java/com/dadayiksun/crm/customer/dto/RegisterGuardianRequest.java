package com.dadayiksun.crm.customer.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/** {@code POST /api/guardians} 입력 (API 설계 3-3 C6). 보호자와 첫 아이를 한 요청으로 함께 저장한다 (SCR-S08-06). */
public record RegisterGuardianRequest(
        @Valid @NotNull(message = "보호자 이름") GuardianInput guardian,
        @Valid @NotNull(message = "아이 이름") PetInput pet) {
}
