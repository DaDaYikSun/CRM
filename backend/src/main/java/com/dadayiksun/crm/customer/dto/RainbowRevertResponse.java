package com.dadayiksun.crm.customer.dto;

import java.util.List;

/** {@code POST /api/pets/{id}/rainbow-revert} 출력: 이 되돌리기로 보류가 풀린 아이 (API 설계 3-3). */
public record RainbowRevertResponse(List<Long> releasedPetIds) {
}
