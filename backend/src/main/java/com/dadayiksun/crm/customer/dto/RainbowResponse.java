package com.dadayiksun.crm.customer.dto;

import java.time.LocalDate;
import java.util.List;

/** {@code POST /api/pets/{id}/rainbow} 출력 (API 설계 3-3, C21). 이미 무지개다리였으면 heldPetIds는 빈 목록. */
public record RainbowResponse(LocalDate holdUntil, List<Long> heldPetIds) {
}
