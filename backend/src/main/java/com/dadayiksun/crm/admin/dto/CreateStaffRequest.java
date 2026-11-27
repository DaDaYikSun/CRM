package com.dadayiksun.crm.admin.dto;

import com.dadayiksun.crm.auth.domain.Role;
import com.dadayiksun.crm.common.error.ErrorCode;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * POST /api/admin/staff 입력 (API 설계 3-10). 비밀번호는 받지 않는다: 서버가 임시 비밀번호를 만든다 (정책 4-4 B-01).
 * 길이는 DB 칸 길이(login_id · name VARCHAR(30), DB 설계 4-1). 역할은 정해진 3가지만 (POL-ACC-001).
 */
public record CreateStaffRequest(
        @NotBlank(message = "아이디") @Size(max = 30, message = ErrorCode.VALIDATION_FALLBACK_MESSAGE) String loginId,
        @NotBlank(message = "이름") @Size(max = 30, message = ErrorCode.VALIDATION_FALLBACK_MESSAGE) String name,
        @NotNull(message = "역할") Role role) {
}
