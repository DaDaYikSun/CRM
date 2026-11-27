package com.dadayiksun.crm.customer.dto;

import java.time.LocalDate;

import com.dadayiksun.crm.common.error.ErrorCode;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 등록 입력의 아이 칸 (API 설계 3-3 C6). 필수: 아이 이름 · 생년월일(또는 대략 나이) · 품종 · 털 유형 (POL-REG-001).
 * {@code birthDate}와 {@code approxAge}는 둘 중 하나만 받는다. 이 검사는 서버 서비스가 한다.
 * 동물 종류는 받지 않는다 (강아지 고정, POL-REG-003).
 */
public record PetInput(
        @NotBlank(message = "아이 이름") @Size(max = 30, message = ErrorCode.VALIDATION_FALLBACK_MESSAGE) String name,
        LocalDate birthDate,
        ApproxAge approxAge,
        @NotNull(message = "품종") Long breedId,
        @NotNull(message = "털 유형") Long coatTypeId) {

    /** 대략 나이 (POL-REG-004): {@code { "years": 3 }} 또는 1살 미만이면 {@code { "months": 5 }}. */
    public record ApproxAge(Integer years, Integer months) {

        public boolean isEmpty() {
            return years == null && months == null;
        }
    }
}
