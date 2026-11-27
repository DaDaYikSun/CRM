package com.dadayiksun.crm.customer.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/**
 * {@code POST /api/guardians/{id}/pets} 입력 (API 설계 3-3 C6, 이슈 #145 '입력 pet{…}').
 * 보호자 정보는 받지 않는다. 기존 보호자 아래에 아이만 붙인다 (SCR-S08-07).
 */
public record AddPetRequest(@Valid @NotNull(message = "아이 이름") PetInput pet) {
}
