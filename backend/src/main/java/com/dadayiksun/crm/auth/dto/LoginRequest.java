package com.dadayiksun.crm.auth.dto;

import jakarta.validation.constraints.NotBlank;

/** POST /api/auth/login 입력 (API 설계 3-1). 빈 칸은 "{항목}을(를) 입력해 주세요." (정책 4-2). */
public record LoginRequest(
        @NotBlank(message = "아이디") String loginId,
        @NotBlank(message = "비밀번호") String password) {
}
