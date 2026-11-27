package com.dadayiksun.crm.auth.domain;

import java.time.LocalDateTime;

/** 직원 관리(B-01) 목록 한 줄 (DB 설계 4-1). 비밀번호 해시는 담지 않는다. */
public record StaffAccount(
        Long staffId,
        String loginId,
        String name,
        Role role,
        boolean active,
        LocalDateTime lastLoginAt,
        LocalDateTime deactivatedAt) {
}
