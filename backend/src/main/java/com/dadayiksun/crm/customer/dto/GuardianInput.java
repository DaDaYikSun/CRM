package com.dadayiksun.crm.customer.dto;

import com.dadayiksun.crm.common.error.ErrorCode;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 등록 입력의 보호자 칸 (API 설계 3-3 C6). 필수: 보호자 이름 · 보호자 전화번호 (POL-REG-001, T9).
 * 동의 칸은 체크 칸만 둔다 (POL-REG-010). 비어 있으면 동의하지 않은 것으로 저장한다.
 * 글자 수 상한은 DB 칸 길이(VARCHAR(30))다.
 */
public record GuardianInput(
        @NotBlank(message = "보호자 이름") @Size(max = 30, message = ErrorCode.VALIDATION_FALLBACK_MESSAGE) String name,
        @NotBlank(message = "보호자 전화번호") String phone,
        Boolean privacyAgreed,
        Boolean smsAgreed) {
}
