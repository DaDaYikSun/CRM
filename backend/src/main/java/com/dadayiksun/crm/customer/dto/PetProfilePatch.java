package com.dadayiksun.crm.customer.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** {@code PATCH /api/pets/{id}} 입력 (바꿀 칸만, 빠진 칸은 그대로). 체중은 지울 수 없다 [?]. */
public record PetProfilePatch(String name, LocalDate birthDate, Long breedId, Long coatTypeId, BigDecimal weightKg) {
}
