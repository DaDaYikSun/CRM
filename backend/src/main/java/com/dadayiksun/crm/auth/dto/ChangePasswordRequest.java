package com.dadayiksun.crm.auth.dto;

import jakarta.validation.constraints.NotBlank;

/** POST /api/auth/password 입력 (API 설계 3-1). 빈 칸은 "새 비밀번호을(를) 입력해 주세요." (정책 4-2). */
public record ChangePasswordRequest(@NotBlank(message = "새 비밀번호") String newPassword) {
}
