package com.dadayiksun.crm.customer.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * {@code PATCH /api/pets/{id}} 출력. API 설계 3-3은 '{@code GET /api/pets/{id}}와 같은 모양'이라 했지만
 * 그 응답의 schedules · siblings는 조립 자리(#149)가 만든다. #149 전에는 {@code pet} 블록만 준다 (보고에 적음).
 */
public record PetProfileResponse(PetBlock pet) {

    public record PetBlock(Long petId, String name, String species, String status, Named breed, Named coatType,
            LocalDate birthDate, boolean birthDateEstimated, BigDecimal weightKg, LocalDate contactHoldUntil) {
    }

    public record Named(Long id, String name) {
    }
}
